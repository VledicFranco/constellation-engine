package io.constellation.stream.connector

import java.time.Instant

import cats.effect.IO
import cats.implicits.*

/** Health status for a connector. */
sealed trait ConnectorHealthStatus

object ConnectorHealthStatus {
  case object Healthy                  extends ConnectorHealthStatus
  case class Unhealthy(reason: String) extends ConnectorHealthStatus
  case object Unknown                  extends ConnectorHealthStatus
}

/** Health check report for a single connector. */
final case class ConnectorHealthReport(
    connectorName: String,
    typeName: String,
    status: ConnectorHealthStatus,
    checkedAt: Instant
)

/** Trait for connectors that support health checking. */
trait HealthCheckable {

  /** Quick boolean health check. */
  def isHealthy: IO[Boolean] = IO.pure(true)

  /** Full health report with status and timestamp. */
  def healthReport: IO[ConnectorHealthReport]
}
