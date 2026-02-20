package io.constellation.stream.connector

import java.time.Instant

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import fs2.{Pipe, Stream}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.constellation.CValue

class ConnectorHealthTest extends AnyFlatSpec with Matchers {

  "SourceConnector" should "default to Healthy status" in {
    val src = new SourceConnector {
      def name: String                                                 = "test-source"
      def typeName: String                                             = "test"
      def stream(config: ValidatedConnectorConfig): Stream[IO, CValue] = Stream.empty
    }

    val report = src.healthReport.unsafeRunSync()
    report.connectorName shouldBe "test-source"
    report.typeName shouldBe "test"
    report.status shouldBe ConnectorHealthStatus.Healthy
  }

  it should "default isHealthy to true" in {
    val src = new SourceConnector {
      def name: String                                                 = "test"
      def typeName: String                                             = "test"
      def stream(config: ValidatedConnectorConfig): Stream[IO, CValue] = Stream.empty
    }

    src.isHealthy.unsafeRunSync() shouldBe true
  }

  "SinkConnector" should "default to Healthy status" in {
    val snk = new SinkConnector {
      def name: String                                                   = "test-sink"
      def typeName: String                                               = "test"
      def pipe(config: ValidatedConnectorConfig): Pipe[IO, CValue, Unit] = _.drain
    }

    val report = snk.healthReport.unsafeRunSync()
    report.connectorName shouldBe "test-sink"
    report.typeName shouldBe "test"
    report.status shouldBe ConnectorHealthStatus.Healthy
  }

  "Custom connector" should "report Unhealthy when overridden" in {
    val unhealthySrc = new SourceConnector {
      def name: String                                                 = "bad-source"
      def typeName: String                                             = "broken"
      def stream(config: ValidatedConnectorConfig): Stream[IO, CValue] = Stream.empty

      override def isHealthy: IO[Boolean] = IO.pure(false)

      override def healthReport: IO[ConnectorHealthReport] =
        IO.realTimeInstant.map { now =>
          ConnectorHealthReport(
            name,
            typeName,
            ConnectorHealthStatus.Unhealthy("connection lost"),
            now
          )
        }
    }

    val report = unhealthySrc.healthReport.unsafeRunSync()
    report.status shouldBe a[ConnectorHealthStatus.Unhealthy]
    report.status.asInstanceOf[ConnectorHealthStatus.Unhealthy].reason shouldBe "connection lost"
    unhealthySrc.isHealthy.unsafeRunSync() shouldBe false
  }

  "ConnectorRegistry.healthCheck" should "aggregate reports from all connectors" in {
    val srcQ = cats.effect.std.Queue.bounded[IO, Option[CValue]](10).unsafeRunSync()
    val snkQ = cats.effect.std.Queue.bounded[IO, CValue](10).unsafeRunSync()

    val registry = ConnectorRegistry.builder
      .source("input", MemoryConnector.source("input", srcQ))
      .sink("output", MemoryConnector.sink("output", snkQ))
      .build

    val reports = registry.healthCheck.unsafeRunSync()
    reports should have size 2
    reports("input").status shouldBe ConnectorHealthStatus.Healthy
    reports("output").status shouldBe ConnectorHealthStatus.Healthy
  }

  it should "return empty map for empty registry" in {
    val reports = ConnectorRegistry.empty.healthCheck.unsafeRunSync()
    reports shouldBe empty
  }

  "ConnectorHealthStatus" should "have all expected variants" in {
    ConnectorHealthStatus.Healthy shouldBe a[ConnectorHealthStatus]
    ConnectorHealthStatus.Unhealthy("reason") shouldBe a[ConnectorHealthStatus]
    ConnectorHealthStatus.Unknown shouldBe a[ConnectorHealthStatus]
  }

  "ConnectorHealthReport" should "contain timestamp" in {
    val report = ConnectorHealthReport("test", "type", ConnectorHealthStatus.Healthy, Instant.now())
    report.checkedAt should not be null
  }
}
