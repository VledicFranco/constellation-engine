/**
 * Transport abstractions for the Module Provider Protocol.
 *
 * Mirrors the Scala `transport.scala` traits:
 * - ProviderTransport: client-side gRPC for registration/control
 * - ControlPlaneHandler: callbacks for server-originated messages
 * - ControlPlaneStream: client-side stream handle
 * - ExecutorServerFactory: hosts the ModuleExecutor service
 *
 * Production: wraps @grpc/grpc-js. Test: in-memory fakes.
 */

// ===== Proto message shapes (plain objects) =====
// These mirror the proto definitions but are plain TS interfaces so the
// transport layer doesn't depend on proto codegen.

export interface RegisterRequest {
  namespace: string;
  modules: ModuleDeclaration[];
  protocolVersion: number;
  executorUrl: string;
  groupId: string;
}

export interface ModuleDeclaration {
  name: string;
  inputSchema?: unknown;
  outputSchema?: unknown;
  version: string;
  description: string;
}

export interface RegisterResponse {
  success: boolean;
  results: ModuleRegistrationResult[];
  protocolVersion: number;
  connectionId: string;
}

export interface ModuleRegistrationResult {
  moduleName: string;
  accepted: boolean;
  rejectionReason: string;
}

export interface DeregisterRequest {
  namespace: string;
  moduleNames: string[];
  connectionId: string;
}

export interface DeregisterResponse {
  success: boolean;
  results: ModuleDeregistrationResult[];
}

export interface ModuleDeregistrationResult {
  moduleName: string;
  removed: boolean;
  error: string;
}

export interface ControlMessage {
  protocolVersion: number;
  connectionId: string;
  payload:
    | { case: "heartbeat"; value: Heartbeat }
    | { case: "heartbeatAck"; value: HeartbeatAck }
    | { case: "activeModulesReport"; value: ActiveModulesReport }
    | { case: "drainRequest"; value: DrainRequest }
    | { case: "drainAck"; value: DrainAck }
    | { case: undefined; value: undefined };
}

export interface Heartbeat {
  namespace: string;
  timestamp: number;
}

export interface HeartbeatAck {
  timestamp: number;
}

export interface ActiveModulesReport {
  activeModules: string[];
}

export interface DrainRequest {
  reason: string;
  deadlineMs: number;
}

export interface DrainAck {
  accepted: boolean;
  inFlightCount: number;
}

export interface ExecuteRequest {
  moduleName: string;
  inputData: Uint8Array;
  executionId: string;
  metadata: Record<string, string>;
}

export interface ExecuteResponse {
  result:
    | { case: "outputData"; value: Uint8Array }
    | { case: "error"; value: ExecutionError }
    | { case: undefined; value: undefined };
  metrics?: ExecutionMetrics;
}

export interface ExecutionError {
  code: string;
  message: string;
  stackTrace: string;
}

export interface ExecutionMetrics {
  durationMs: number;
  memoryBytes: number;
}

// ===== Transport interfaces =====

/** Abstracts the gRPC client-side transport for registration and control plane. */
export interface ProviderTransport {
  register(request: RegisterRequest): Promise<RegisterResponse>;
  deregister(request: DeregisterRequest): Promise<DeregisterResponse>;
  openControlPlane(handler: ControlPlaneHandler): Promise<ControlPlaneStream>;
  /** Close the underlying connection. Optional — transports that don't need cleanup can omit. */
  close?(): void;
}

/** Callbacks for server-originated control plane messages. */
export interface ControlPlaneHandler {
  onHeartbeatAck(ack: HeartbeatAck): Promise<void>;
  onActiveModulesReport(report: ActiveModulesReport): Promise<void>;
  onDrainRequest(drain: DrainRequest): Promise<void>;
  onStreamError(error: Error): Promise<void>;
  onStreamCompleted(): Promise<void>;
}

/** Client-side control plane stream handle for sending messages. */
export interface ControlPlaneStream {
  sendHeartbeat(hb: Heartbeat, connectionId?: string): Promise<void>;
  sendDrainAck(ack: DrainAck): Promise<void>;
  close(): Promise<void>;
}

/** Abstracts hosting the ModuleExecutor gRPC service. */
export interface ExecutorServerFactory {
  /** Create and start an executor server. Returns the actual bound port. */
  create(
    handler: (request: ExecuteRequest) => Promise<ExecuteResponse>,
    port: number,
  ): Promise<ExecutorServer>;
}

/** Handle to a running executor server. */
export interface ExecutorServer {
  port: number;
  stop(): Promise<void>;
}
