package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StreamWebSocketTest extends AnyFlatSpec with Matchers {

  // ===== Instantiation =====

  "StreamWebSocket" should "create instance without error" in {
    val ws = StreamWebSocket()
    ws should not be null
  }

  it should "start with zero subscriptions" in {
    val ws    = StreamWebSocket()
    val count = ws.subscriptionCount.unsafeRunSync()
    count shouldBe 0
  }

  // ===== Event Publishing =====

  it should "publish events without error when no subscribers" in {
    val ws = StreamWebSocket()
    val event = StreamEvent.StreamDeployed("s1", "test-stream")

    noException should be thrownBy {
      ws.publish(event).unsafeRunSync()
    }
  }

  // ===== StreamEvent Serialization =====

  "StreamEvent.StreamDeployed" should "serialize correctly" in {
    val event = StreamEvent.StreamDeployed(
      streamId = "s1",
      streamName = "test-stream",
      timestamp = 1234567890L
    )

    val json = (event: StreamEvent).asJson.noSpaces

    json should include("\"type\":\"stream:deployed\"")
    json should include("\"streamId\":\"s1\"")
    json should include("\"streamName\":\"test-stream\"")
  }

  "StreamEvent.StreamStopped" should "serialize correctly" in {
    val event = StreamEvent.StreamStopped(
      streamId = "s1",
      streamName = "test-stream",
      timestamp = 1234567890L
    )

    val json = (event: StreamEvent).asJson.noSpaces

    json should include("\"type\":\"stream:stopped\"")
    json should include("\"streamId\":\"s1\"")
  }

  "StreamEvent.StreamFailed" should "serialize correctly" in {
    val event = StreamEvent.StreamFailed(
      streamId = "s1",
      streamName = "test-stream",
      error = "connection lost",
      timestamp = 1234567890L
    )

    val json = (event: StreamEvent).asJson.noSpaces

    json should include("\"type\":\"stream:failed\"")
    json should include("\"error\":\"connection lost\"")
  }

  "StreamEvent.StreamMetricsUpdate" should "serialize correctly" in {
    val event = StreamEvent.StreamMetricsUpdate(
      streamId = "s1",
      totalElements = 100,
      totalErrors = 2,
      totalDlq = 0,
      timestamp = 1234567890L
    )

    val json = (event: StreamEvent).asJson.noSpaces

    json should include("\"type\":\"stream:metrics\"")
    json should include("\"totalElements\":100")
    json should include("\"totalErrors\":2")
  }

  // ===== Event Type Uniqueness =====

  "StreamEvent types" should "have unique event type strings" in {
    val types = List(
      StreamEvent.StreamDeployed("", "", 0L).eventType,
      StreamEvent.StreamStopped("", "", 0L).eventType,
      StreamEvent.StreamFailed("", "", "", 0L).eventType,
      StreamEvent.StreamMetricsUpdate("", 0, 0, 0, 0L).eventType
    )

    types.distinct.size shouldBe types.size
  }

  // ===== JSON Parsing =====

  "Serialized stream events" should "be valid JSON" in {
    val events: List[StreamEvent] = List(
      StreamEvent.StreamDeployed("s1", "Stream1", 1000L),
      StreamEvent.StreamStopped("s1", "Stream1", 2000L),
      StreamEvent.StreamFailed("s2", "Stream2", "error", 3000L),
      StreamEvent.StreamMetricsUpdate("s1", 100, 2, 0, 4000L)
    )

    events.foreach { event =>
      val json = event.asJson.noSpaces
      parse(json) match {
        case Right(parsed) =>
          parsed.hcursor.downField("type").as[String] shouldBe a[Right[_, _]]
          parsed.hcursor.downField("streamId").as[String] shouldBe a[Right[_, _]]
          parsed.hcursor.downField("timestamp").as[Long] shouldBe a[Right[_, _]]
        case Left(error) =>
          fail(s"Failed to parse event JSON: ${error.getMessage}")
      }
    }
  }
}
