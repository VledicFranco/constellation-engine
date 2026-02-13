/**
 * SDK configuration types.
 *
 * Mirrors the Scala SdkConfig and CanaryConfig case classes.
 * Durations are expressed as milliseconds (Ms suffix convention).
 */

export interface SdkConfig {
  /** Port to host the ModuleExecutor gRPC service on. Default: 9091. */
  executorPort: number;
  /** Hostname/IP the Constellation server should use to reach this provider's executor. Default: "localhost". */
  executorHost: string;
  /** Interval between heartbeat messages in milliseconds. Default: 5000. */
  heartbeatIntervalMs: number;
  /** Initial backoff for reconnection in milliseconds. Default: 1000. */
  reconnectBackoffMs: number;
  /** Maximum backoff for reconnection in milliseconds. Default: 60000. */
  maxReconnectBackoffMs: number;
  /** Maximum consecutive reconnection attempts. Default: 10. */
  maxReconnectAttempts: number;
  /** Optional provider group ID for horizontal scaling. */
  groupId?: string;
  /** Canary rollout configuration. */
  canary: CanaryConfig;
}

export interface CanaryConfig {
  /** Observation window in milliseconds before promoting. Default: 30000. */
  observationWindowMs: number;
  /** Minimum health ratio (0.0-1.0). Default: 0.95. */
  healthThreshold: number;
  /** Maximum acceptable latency in milliseconds. Default: 5000. */
  maxLatencyMs: number;
  /** Whether to auto-rollback on failure. Default: true. */
  rollbackOnFailure: boolean;
}

export const DEFAULT_CANARY_CONFIG: CanaryConfig = {
  observationWindowMs: 30_000,
  healthThreshold: 0.95,
  maxLatencyMs: 5_000,
  rollbackOnFailure: true,
};

export const DEFAULT_SDK_CONFIG: SdkConfig = {
  executorPort: 9091,
  executorHost: "localhost",
  heartbeatIntervalMs: 5_000,
  reconnectBackoffMs: 1_000,
  maxReconnectBackoffMs: 60_000,
  maxReconnectAttempts: 10,
  canary: DEFAULT_CANARY_CONFIG,
};
