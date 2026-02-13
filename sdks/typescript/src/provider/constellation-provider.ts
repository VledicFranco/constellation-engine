/**
 * Top-level entry point for the provider SDK.
 *
 * Manages module registration, instance connections, executor server, and canary rollouts.
 * Mirrors the Scala ConstellationProvider class with a Promise-based API.
 */

import type { ModuleDefinition } from "../types/module-definition.js";
import type { SdkConfig } from "../types/config.js";
import { DEFAULT_SDK_CONFIG } from "../types/config.js";
import { ConnectionState } from "../types/connection.js";
import type { CanaryResult } from "../types/canary.js";
import type { CValueSerializer } from "../serialization/cvalue-serializer.js";
import { JsonCValueSerializer } from "../serialization/cvalue-serializer.js";
import type {
  ProviderTransport,
  ExecutorServerFactory,
  ExecutorServer,
} from "../transport/transport.js";
import type { DiscoveryStrategy } from "../discovery/discovery-strategy.js";
import { StaticDiscovery } from "../discovery/static-discovery.js";
import { InstanceConnection } from "./instance-connection.js";
import { ModuleExecutorServer } from "./module-executor-server.js";
import { CanaryCoordinator } from "./canary-coordinator.js";

export interface ProviderOptions {
  /** constellation-lang namespace (dot-separated). */
  namespace: string;
  /** Static list of Constellation instance addresses. Mutually exclusive with `discovery`. */
  instances?: string[];
  /** Custom discovery strategy. Mutually exclusive with `instances`. */
  discovery?: DiscoveryStrategy;
  /** SDK configuration. Defaults are applied for any missing fields. */
  config?: Partial<SdkConfig>;
  /** Factory for creating ProviderTransport instances, given a server address. */
  transportFactory: (address: string) => ProviderTransport;
  /** Factory for creating the executor gRPC server. */
  executorServerFactory: ExecutorServerFactory;
  /** CValue serializer. Defaults to JsonCValueSerializer. */
  serializer?: CValueSerializer;
}

export class ConstellationProvider {
  readonly namespace: string;
  private readonly config: SdkConfig;
  private readonly discovery: DiscoveryStrategy;
  private readonly transportFactory: (address: string) => ProviderTransport;
  private readonly executorServerFactory: ExecutorServerFactory;
  private readonly serializer: CValueSerializer;

  private modules: ModuleDefinition[] = [];
  private connections: Map<string, InstanceConnection> = new Map();
  private transports: ProviderTransport[] = [];
  private executorServer: ExecutorServer | null = null;
  private moduleExecutorServer: ModuleExecutorServer | null = null;
  private started = false;

  private constructor(options: ProviderOptions) {
    this.namespace = options.namespace;
    this.config = {
      ...DEFAULT_SDK_CONFIG,
      ...options.config,
      canary: { ...DEFAULT_SDK_CONFIG.canary, ...options.config?.canary },
    };
    this.discovery = options.discovery ?? new StaticDiscovery(options.instances ?? []);
    this.transportFactory = options.transportFactory;
    this.executorServerFactory = options.executorServerFactory;
    this.serializer = options.serializer ?? JsonCValueSerializer;
  }

  /** Create a new ConstellationProvider. */
  static async create(options: ProviderOptions): Promise<ConstellationProvider> {
    return new ConstellationProvider(options);
  }

  /** Register a module to be provided. Must be called before start. */
  register(module: ModuleDefinition): void {
    if (this.started) {
      throw new Error("Cannot register modules after provider has started");
    }
    this.modules.push(module);
  }

  /** Get all registered modules. */
  get registeredModules(): ModuleDefinition[] {
    return [...this.modules];
  }

  /**
   * Start the provider: launch executor server, discover instances, connect to all.
   */
  async start(): Promise<void> {
    if (this.started) return;

    // Start executor server
    this.moduleExecutorServer = new ModuleExecutorServer([...this.modules], this.serializer);
    this.executorServer = await this.executorServerFactory.create(
      this.moduleExecutorServer.toHandler(),
      this.config.executorPort,
    );

    try {
      // Discover instances and create connections
      const executorUrl = `${this.config.executorHost}:${this.config.executorPort}`;
      const instanceAddresses = await this.discovery.instances();
      for (const addr of instanceAddresses) {
        const transport = this.transportFactory(addr);
        this.transports.push(transport);
        const conn = new InstanceConnection(
          addr,
          this.namespace,
          transport,
          this.config,
          [...this.modules],
          executorUrl,
        );
        this.connections.set(addr, conn);
      }

      // Connect all instances
      await Promise.all([...this.connections.values()].map((conn) => conn.connect()));
    } catch (error) {
      // Clean up on partial failure: disconnect any successful connections and stop executor
      await Promise.all(
        [...this.connections.values()].map((conn) => conn.disconnect().catch(() => {})),
      );
      this.connections.clear();
      for (const t of this.transports) {
        try {
          t.close?.();
        } catch {
          /* best-effort */
        }
      }
      this.transports = [];
      if (this.executorServer) {
        await this.executorServer.stop().catch(() => {});
        this.executorServer = null;
      }
      this.moduleExecutorServer = null;
      throw error;
    }

    this.started = true;
  }

  /**
   * Stop the provider: disconnect all instances, close transports, and stop the executor server.
   */
  async stop(): Promise<void> {
    if (!this.started) return;

    // Disconnect all instances
    await Promise.all([...this.connections.values()].map((conn) => conn.disconnect()));
    this.connections.clear();

    // Close transports
    for (const t of this.transports) {
      try {
        t.close?.();
      } catch {
        /* best-effort */
      }
    }
    this.transports = [];

    // Stop executor server
    if (this.executorServer) {
      await this.executorServer.stop();
      this.executorServer = null;
    }

    this.moduleExecutorServer = null;
    this.started = false;
  }

  /** Perform a canary rollout of new modules across all instances. */
  async canaryRollout(newModules: ModuleDefinition[]): Promise<CanaryResult> {
    const oldModules = [...this.modules];
    const coordinator = new CanaryCoordinator(this.connections, this.config.canary);
    const result = await coordinator.rollout(oldModules, newModules);

    if (result.status === "Promoted") {
      this.modules = [...newModules];
      this.moduleExecutorServer?.setModules(newModules);
    }

    return result;
  }

  /** Get status of all instance connections. */
  async status(): Promise<Array<{ address: string; state: ConnectionState }>> {
    return [...this.connections.entries()].map(([address, conn]) => ({
      address,
      state: conn.currentState,
    }));
  }
}
