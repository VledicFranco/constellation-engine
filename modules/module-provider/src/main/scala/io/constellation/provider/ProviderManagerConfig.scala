package io.constellation.provider

import scala.concurrent.duration.*

/** Configuration for ModuleProviderManager.
  *
  * @param grpcPort
  *   Port for the gRPC ModuleProvider service (default: 9090)
  * @param heartbeatInterval
  *   Interval between heartbeat pings on control plane stream
  * @param heartbeatTimeout
  *   Time to wait for heartbeat response before considering provider dead
  * @param controlPlaneRequiredTimeout
  *   Time to wait after registration for control plane connection before auto-deregistering
  * @param reservedNamespaces
  *   Namespace prefixes that providers cannot claim
  */
final case class ProviderManagerConfig(
    grpcPort: Int = 9090,
    heartbeatInterval: FiniteDuration = 5.seconds,
    heartbeatTimeout: FiniteDuration = 15.seconds,
    controlPlaneRequiredTimeout: FiniteDuration = 30.seconds,
    reservedNamespaces: Set[String] = Set("stdlib")
)

object ProviderManagerConfig {

  /** Load configuration from environment variables, falling back to defaults. */
  def fromEnv: ProviderManagerConfig = {
    val port = sys.env.get("CONSTELLATION_PROVIDER_PORT").flatMap(_.toIntOption).getOrElse(9090)
    val heartbeatInterval = sys.env.get("CONSTELLATION_PROVIDER_HEARTBEAT_INTERVAL")
      .flatMap(parseDuration).getOrElse(5.seconds)
    val heartbeatTimeout = sys.env.get("CONSTELLATION_PROVIDER_HEARTBEAT_TIMEOUT")
      .flatMap(parseDuration).getOrElse(15.seconds)

    ProviderManagerConfig(
      grpcPort = port,
      heartbeatInterval = heartbeatInterval,
      heartbeatTimeout = heartbeatTimeout
    )
  }

  private def parseDuration(s: String): Option[FiniteDuration] =
    s.toLongOption.map(_.seconds)
}
