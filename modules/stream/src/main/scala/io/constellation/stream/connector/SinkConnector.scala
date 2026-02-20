package io.constellation.stream.connector

import java.time.Instant

import cats.effect.IO

import io.constellation.CValue

import fs2.Pipe

/** A sink connector consumes a stream of CValues and writes them to an external system.
  *
  * Implementations wrap external data sinks (queues, files, databases, etc.) and expose them as fs2
  * Pipes. Each connector declares its type name and configuration schema for validation.
  */
trait SinkConnector extends HealthCheckable {

  /** The instance name of this sink connector. */
  def name: String

  /** The connector type identifier (e.g., "memory", "websocket", "kafka"). */
  def typeName: String

  /** The configuration schema for this connector type. */
  def configSchema: ConnectorSchema = ConnectorSchema.empty

  /** The pipe that consumes values and writes them to the sink.
    *
    * @param config
    *   Validated configuration for this connector instance
    */
  def pipe(config: ValidatedConnectorConfig): Pipe[IO, CValue, Unit]

  /** Default health report returns Healthy. */
  override def healthReport: IO[ConnectorHealthReport] =
    IO.realTimeInstant.map { now =>
      ConnectorHealthReport(name, typeName, ConnectorHealthStatus.Healthy, now)
    }
}
