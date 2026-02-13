package io.constellation.provider.sdk

import scala.concurrent.duration.*

/** Configuration for the provider SDK.
  *
  * @param executorPort
  *   Port to host the ModuleExecutor gRPC service on
  * @param executorHost
  *   Hostname that the Constellation server should use to reach this provider's executor. In Docker
  *   or Kubernetes, this is typically the service/container name (e.g., "provider-scala"). Defaults
  *   to "localhost" for local development.
  * @param heartbeatInterval
  *   Interval between heartbeat messages on the control plane
  * @param reconnectBackoff
  *   Initial backoff duration for reconnection attempts
  * @param maxReconnectBackoff
  *   Maximum backoff duration for reconnection attempts
  * @param maxReconnectAttempts
  *   Maximum number of consecutive reconnection attempts before giving up
  * @param groupId
  *   Optional provider group ID for horizontal scaling. When set, multiple provider replicas with
  *   the same groupId + namespace are treated as a single logical provider. Constellation
  *   load-balances Execute calls across healthy group members.
  * @param canary
  *   Configuration for canary rollout behavior
  */
final case class SdkConfig(
    executorPort: Int = 9091,
    executorHost: String = "localhost",
    heartbeatInterval: FiniteDuration = 5.seconds,
    reconnectBackoff: FiniteDuration = 1.second,
    maxReconnectBackoff: FiniteDuration = 60.seconds,
    maxReconnectAttempts: Int = 10,
    groupId: Option[String] = None,
    canary: CanaryConfig = CanaryConfig()
)

/** Configuration for canary rollout behavior.
  *
  * @param observationWindow
  *   How long to observe a canary instance before promoting
  * @param healthThreshold
  *   Minimum health ratio (0.0-1.0) to consider canary healthy
  * @param maxLatencyMs
  *   Maximum acceptable latency in milliseconds
  * @param rollbackOnFailure
  *   Whether to automatically rollback on canary failure
  */
final case class CanaryConfig(
    observationWindow: FiniteDuration = 30.seconds,
    healthThreshold: Double = 0.95,
    maxLatencyMs: Long = 5000L,
    rollbackOnFailure: Boolean = true
)
