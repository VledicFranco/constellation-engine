package io.constellation.stream.connector

import java.time.Instant

import cats.effect.IO

import io.constellation.CValue
import io.constellation.stream.delivery.{DeliveryGuarantee, OffsetCommitter}

import fs2.Stream

/** A source connector provides a stream of CValues from an external system.
  *
  * Implementations wrap external data sources (queues, files, Kafka topics, etc.) and expose them
  * as fs2 Streams. Each connector declares its type name and configuration schema for validation.
  */
trait SourceConnector extends HealthCheckable {

  /** The instance name of this source connector. */
  def name: String

  /** The connector type identifier (e.g., "memory", "websocket", "kafka"). */
  def typeName: String

  /** The configuration schema for this connector type. */
  def configSchema: ConnectorSchema = ConnectorSchema.empty

  /** The delivery guarantee level for this source. */
  def deliveryGuarantee: DeliveryGuarantee = DeliveryGuarantee.AtMostOnce

  /** The offset committer for this source (noop by default). */
  def offsetCommitter: OffsetCommitter = OffsetCommitter.noop

  /** The stream of values produced by this source.
    *
    * @param config
    *   Validated configuration for this connector instance
    */
  def stream(config: ValidatedConnectorConfig): Stream[IO, CValue]

  /** Default health report returns Healthy. */
  override def healthReport: IO[ConnectorHealthReport] =
    IO.realTimeInstant.map { now =>
      ConnectorHealthReport(name, typeName, ConnectorHealthStatus.Healthy, now)
    }
}
