package io.constellation.http

import io.constellation.http.StreamApiModels.*

import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StreamApiModelsTest extends AnyFlatSpec with Matchers {

  // ===== StreamDeployRequest =====

  "StreamDeployRequest" should "round-trip through JSON" in {
    val req = StreamDeployRequest(
      name = "my-stream",
      pipelineRef = "sha256:abc123",
      sourceBindings = Map(
        "input" -> SourceBindingRequest("memory", Map("bufferSize" -> "256"))
      ),
      sinkBindings = Map(
        "output" -> SinkBindingRequest("memory", Map.empty)
      ),
      options = Some(
        StreamOptionsRequest(
          errorStrategy = Some("log"),
          parallelism = Some(4)
        )
      )
    )

    val json    = req.asJson
    val decoded = json.as[StreamDeployRequest]
    decoded shouldBe Right(req)
  }

  it should "deserialize with minimal fields" in {
    val json = parse(
      """{"name":"test","pipelineRef":"ref1","sourceBindings":{},"sinkBindings":{}}"""
    ).toOption.get
    val decoded = json.as[StreamDeployRequest]
    decoded shouldBe a[Right[_, _]]
    decoded.toOption.get.name shouldBe "test"
    decoded.toOption.get.sourceBindings shouldBe empty
  }

  // ===== StreamInfoResponse =====

  "StreamInfoResponse" should "round-trip through JSON" in {
    val info = StreamInfoResponse(
      id = "s1",
      name = "my-stream",
      status = "running",
      startedAt = "2026-01-01T00:00:00Z",
      metrics = Some(
        StreamMetricsSummary(
          totalElements = 100,
          totalErrors = 2,
          totalDlq = 1,
          perModule = Map("Uppercase" -> ModuleMetrics(100, 2, 1))
        )
      )
    )

    val json    = info.asJson
    val decoded = json.as[StreamInfoResponse]
    decoded shouldBe Right(info)
  }

  it should "serialize without metrics" in {
    val info = StreamInfoResponse(
      id = "s1",
      name = "my-stream",
      status = "stopped",
      startedAt = "2026-01-01T00:00:00Z"
    )

    val json = info.asJson.noSpaces
    json should include("\"status\":\"stopped\"")
    json should include("\"metrics\":null")
  }

  // ===== StreamMetricsSummary =====

  "StreamMetricsSummary" should "round-trip through JSON" in {
    val summary = StreamMetricsSummary(
      totalElements = 500,
      totalErrors = 3,
      totalDlq = 0,
      perModule = Map(
        "Transform" -> ModuleMetrics(250, 1, 0),
        "Filter"    -> ModuleMetrics(250, 2, 0)
      )
    )

    val json    = summary.asJson
    val decoded = json.as[StreamMetricsSummary]
    decoded shouldBe Right(summary)
  }

  // ===== ConnectorInfoResponse =====

  "ConnectorInfoResponse" should "round-trip through JSON" in {
    val info = ConnectorInfoResponse(
      name = "kafka-source",
      typeName = "kafka",
      kind = "source",
      schema = Some(
        ConnectorSchemaResponse(
          required = Map("topic" -> "StringProp"),
          optional = Map("groupId" -> "StringProp")
        )
      )
    )

    val json    = info.asJson
    val decoded = json.as[ConnectorInfoResponse]
    decoded shouldBe Right(info)
  }

  // ===== ConnectorListResponse =====

  "ConnectorListResponse" should "round-trip through JSON" in {
    val list = ConnectorListResponse(
      connectors = List(
        ConnectorInfoResponse("src", "memory", "source", None),
        ConnectorInfoResponse("snk", "memory", "sink", None)
      )
    )

    val json    = list.asJson
    val decoded = json.as[ConnectorListResponse]
    decoded shouldBe Right(list)
  }

  // ===== StreamListResponse =====

  "StreamListResponse" should "round-trip through JSON" in {
    val list = StreamListResponse(
      streams = List(
        StreamInfoResponse("s1", "stream-1", "running", "2026-01-01T00:00:00Z"),
        StreamInfoResponse("s2", "stream-2", "stopped", "2026-01-01T00:00:00Z")
      )
    )

    val json    = list.asJson
    val decoded = json.as[StreamListResponse]
    decoded shouldBe Right(list)
  }
}
