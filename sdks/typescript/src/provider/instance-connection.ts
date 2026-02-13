/**
 * Manages a single connection to a Constellation instance.
 *
 * Handles registration, deregistration, and state tracking.
 * Mirrors the Scala InstanceConnection class.
 */

import { ConnectionState } from "../types/connection.js";
import type { ModuleDefinition } from "../types/module-definition.js";
import { toDeclaration } from "../types/module-definition.js";
import type { SdkConfig } from "../types/config.js";
import type {
  ProviderTransport,
  ControlPlaneStream,
} from "../transport/transport.js";

export class InstanceConnection {
  readonly instanceAddress: string;
  readonly namespace: string;

  private _state: ConnectionState = ConnectionState.Disconnected;
  private _connectionId: string | undefined;
  private _modules: ModuleDefinition[];
  private readonly transport: ProviderTransport;
  private readonly config: SdkConfig;
  private readonly executorUrl: string;
  private _controlPlane: ControlPlaneStream | undefined;
  private _heartbeatTimer: ReturnType<typeof setInterval> | undefined;

  constructor(
    instanceAddress: string,
    namespace: string,
    transport: ProviderTransport,
    config: SdkConfig,
    modules: ModuleDefinition[],
    executorUrl: string,
  ) {
    this.instanceAddress = instanceAddress;
    this.namespace = namespace;
    this.transport = transport;
    this.config = config;
    this._modules = [...modules];
    this.executorUrl = executorUrl;
  }

  get currentState(): ConnectionState {
    return this._state;
  }

  get connectionId(): string | undefined {
    return this._connectionId;
  }

  get isHealthy(): boolean {
    return this._state === ConnectionState.Active;
  }

  /**
   * Connect to the Constellation instance by registering modules.
   * Idempotent: if already Active, this is a no-op.
   */
  async connect(): Promise<void> {
    if (this._state === ConnectionState.Active) return;

    this._state = ConnectionState.Registering;
    try {
      const response = await this.transport.register({
        namespace: this.namespace,
        modules: this._modules.map(toDeclaration),
        protocolVersion: 1,
        executorUrl: this.executorUrl,
        groupId: this.config.groupId ?? "",
      });

      if (response.success) {
        this._connectionId = response.connectionId;
        // Open control plane stream and start heartbeats
        await this.startControlPlane(response.connectionId);
        this._state = ConnectionState.Active;
      } else {
        this._state = ConnectionState.Disconnected;
        const rejected = response.results
          .filter((r) => !r.accepted)
          .map((r) => `${r.moduleName}: ${r.rejectionReason}`)
          .join(", ");
        throw new Error(`Registration failed: ${rejected}`);
      }
    } catch (error) {
      if (this._state === ConnectionState.Registering) {
        this._state = ConnectionState.Disconnected;
      }
      throw error;
    }
  }

  /**
   * Gracefully disconnect from the Constellation instance.
   * No-op if already disconnected.
   */
  async disconnect(): Promise<void> {
    if (this._state === ConnectionState.Disconnected) return;

    // Stop heartbeats and close control plane
    this.stopControlPlane();

    if (this._connectionId) {
      try {
        await this.transport.deregister({
          namespace: this.namespace,
          moduleNames: this._modules.map((m) => m.name),
          connectionId: this._connectionId,
        });
      } catch {
        // Best-effort deregistration
      }
    }

    this._connectionId = undefined;
    this._state = ConnectionState.Disconnected;
  }

  /** Replace the module list (for canary rollout). */
  replaceModules(newModules: ModuleDefinition[]): void {
    this._modules = [...newModules];
  }

  /** Get the current modules. */
  get modules(): ModuleDefinition[] {
    return [...this._modules];
  }

  // ===== Control Plane =====

  private async startControlPlane(connectionId: string): Promise<void> {
    const stream = await this.transport.openControlPlane({
      onHeartbeatAck: async () => {},
      onActiveModulesReport: async () => {},
      onDrainRequest: async () => {
        this._state = ConnectionState.Draining;
      },
      onStreamError: async () => {},
      onStreamCompleted: async () => {},
    });
    this._controlPlane = stream;

    // Send initial heartbeat to activate control plane on server
    await stream.sendHeartbeat(
      { namespace: this.namespace, timestamp: Date.now() },
      connectionId,
    );

    // Start periodic heartbeats
    const intervalMs = this.config.heartbeatIntervalMs ?? 5000;
    this._heartbeatTimer = setInterval(() => {
      stream
        .sendHeartbeat(
          { namespace: this.namespace, timestamp: Date.now() },
          connectionId,
        )
        .catch(() => {});
    }, intervalMs);
  }

  private stopControlPlane(): void {
    if (this._heartbeatTimer) {
      clearInterval(this._heartbeatTimer);
      this._heartbeatTimer = undefined;
    }
    if (this._controlPlane) {
      this._controlPlane.close().catch(() => {});
      this._controlPlane = undefined;
    }
  }
}
