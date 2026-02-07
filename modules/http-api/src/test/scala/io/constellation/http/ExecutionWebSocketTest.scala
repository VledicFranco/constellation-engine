package io.constellation.http

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExecutionWebSocketTest extends AnyFlatSpec with Matchers {

  // ========== Instantiation Tests ==========

  "ExecutionWebSocket" should "create instance without error" in {
    val ws = ExecutionWebSocket()
    ws should not be null
  }

  it should "provide an ExecutionListener" in {
    val ws = ExecutionWebSocket()
    ws.listener should not be null
  }

  it should "start with zero subscriptions" in {
    val ws    = ExecutionWebSocket()
    val count = ws.subscriptionCount.unsafeRunSync()
    count shouldBe 0
  }

  // ========== ExecutionEvent JSON Serialization Tests ==========

  "ExecutionEvent.ExecutionStarted" should "serialize correctly" in {
    val event = ExecutionEvent.ExecutionStarted(
      executionId = "exec-123",
      dagName = "TestDag",
      timestamp = 1234567890L
    )

    val json = (event: ExecutionEvent).asJson.noSpaces

    json should include("\"type\":\"execution:start\"")
    json should include("\"executionId\":\"exec-123\"")
    json should include("\"dagName\":\"TestDag\"")
    json should include("\"timestamp\":1234567890")
  }

  "ExecutionEvent.ModuleStarted" should "serialize correctly" in {
    val event = ExecutionEvent.ModuleStarted(
      executionId = "exec-123",
      moduleId = "mod-456",
      moduleName = "Uppercase",
      timestamp = 1234567890L
    )

    val json = (event: ExecutionEvent).asJson.noSpaces

    json should include("\"type\":\"module:start\"")
    json should include("\"moduleId\":\"mod-456\"")
    json should include("\"moduleName\":\"Uppercase\"")
  }

  "ExecutionEvent.ModuleCompleted" should "serialize correctly" in {
    val event = ExecutionEvent.ModuleCompleted(
      executionId = "exec-123",
      moduleId = "mod-456",
      moduleName = "Uppercase",
      durationMs = 150L,
      timestamp = 1234567890L
    )

    val json = (event: ExecutionEvent).asJson.noSpaces

    json should include("\"type\":\"module:complete\"")
    json should include("\"durationMs\":150")
  }

  "ExecutionEvent.ModuleFailed" should "serialize correctly" in {
    val event = ExecutionEvent.ModuleFailed(
      executionId = "exec-123",
      moduleId = "mod-456",
      moduleName = "Uppercase",
      error = "Something went wrong",
      timestamp = 1234567890L
    )

    val json = (event: ExecutionEvent).asJson.noSpaces

    json should include("\"type\":\"module:failed\"")
    json should include("\"error\":\"Something went wrong\"")
  }

  "ExecutionEvent.ExecutionCompleted" should "serialize correctly" in {
    val event = ExecutionEvent.ExecutionCompleted(
      executionId = "exec-123",
      dagName = "TestDag",
      succeeded = true,
      durationMs = 500L,
      timestamp = 1234567890L
    )

    val json = (event: ExecutionEvent).asJson.noSpaces

    json should include("\"type\":\"execution:complete\"")
    json should include("\"succeeded\":true")
    json should include("\"durationMs\":500")
  }

  "ExecutionEvent.ExecutionCancelled" should "serialize correctly" in {
    val event = ExecutionEvent.ExecutionCancelled(
      executionId = "exec-123",
      dagName = "TestDag",
      timestamp = 1234567890L
    )

    val json = (event: ExecutionEvent).asJson.noSpaces

    json should include("\"type\":\"execution:cancelled\"")
    json should include("\"dagName\":\"TestDag\"")
  }

  // ========== ExecutionListener Callback Tests ==========

  "ExecutionListener.onExecutionStart" should "not throw" in {
    val ws       = ExecutionWebSocket()
    val execId   = UUID.randomUUID()
    val listener = ws.listener

    noException should be thrownBy {
      listener.onExecutionStart(execId, "TestDag").unsafeRunSync()
    }
  }

  "ExecutionListener.onModuleStart" should "not throw" in {
    val ws       = ExecutionWebSocket()
    val execId   = UUID.randomUUID()
    val moduleId = UUID.randomUUID()
    val listener = ws.listener

    noException should be thrownBy {
      listener.onModuleStart(execId, moduleId, "TestModule").unsafeRunSync()
    }
  }

  "ExecutionListener.onModuleComplete" should "not throw" in {
    val ws       = ExecutionWebSocket()
    val execId   = UUID.randomUUID()
    val moduleId = UUID.randomUUID()
    val listener = ws.listener

    noException should be thrownBy {
      listener.onModuleComplete(execId, moduleId, "TestModule", 100L).unsafeRunSync()
    }
  }

  "ExecutionListener.onModuleFailed" should "not throw" in {
    val ws       = ExecutionWebSocket()
    val execId   = UUID.randomUUID()
    val moduleId = UUID.randomUUID()
    val listener = ws.listener

    noException should be thrownBy {
      listener.onModuleFailed(execId, moduleId, "TestModule", new RuntimeException("Test error")).unsafeRunSync()
    }
  }

  "ExecutionListener.onExecutionComplete" should "not throw" in {
    val ws       = ExecutionWebSocket()
    val execId   = UUID.randomUUID()
    val listener = ws.listener

    noException should be thrownBy {
      listener.onExecutionComplete(execId, "TestDag", succeeded = true, 500L).unsafeRunSync()
    }
  }

  "ExecutionListener.onExecutionCancelled" should "not throw" in {
    val ws       = ExecutionWebSocket()
    val execId   = UUID.randomUUID()
    val listener = ws.listener

    noException should be thrownBy {
      listener.onExecutionCancelled(execId, "TestDag").unsafeRunSync()
    }
  }

  // ========== Error Handling Tests ==========

  "ExecutionListener" should "handle null error message gracefully" in {
    val ws       = ExecutionWebSocket()
    val execId   = UUID.randomUUID()
    val moduleId = UUID.randomUUID()
    val listener = ws.listener

    // Exception with null message
    val nullMsgException = new RuntimeException(null: String)

    noException should be thrownBy {
      listener.onModuleFailed(execId, moduleId, "TestModule", nullMsgException).unsafeRunSync()
    }
  }

  // ========== Event Type Tests ==========

  "ExecutionEvent types" should "have unique event type strings" in {
    val types = List(
      ExecutionEvent.ExecutionStarted("", "", 0L).eventType,
      ExecutionEvent.ModuleStarted("", "", "", 0L).eventType,
      ExecutionEvent.ModuleCompleted("", "", "", 0L, 0L).eventType,
      ExecutionEvent.ModuleFailed("", "", "", "", 0L).eventType,
      ExecutionEvent.ExecutionCompleted("", "", true, 0L, 0L).eventType,
      ExecutionEvent.ExecutionCancelled("", "", 0L).eventType
    )

    types.distinct.size shouldBe types.size
  }

  // ========== JSON Parsing Tests ==========

  "Serialized events" should "be valid JSON" in {
    val events: List[ExecutionEvent] = List(
      ExecutionEvent.ExecutionStarted("exec-1", "Dag1", 1000L),
      ExecutionEvent.ModuleStarted("exec-1", "mod-1", "Module1", 1001L),
      ExecutionEvent.ModuleCompleted("exec-1", "mod-1", "Module1", 50L, 1051L),
      ExecutionEvent.ModuleFailed("exec-2", "mod-2", "Module2", "Error", 2000L),
      ExecutionEvent.ExecutionCompleted("exec-1", "Dag1", true, 100L, 1100L),
      ExecutionEvent.ExecutionCancelled("exec-3", "Dag3", 3000L)
    )

    events.foreach { event =>
      val json = event.asJson.noSpaces
      parse(json) match {
        case Right(parsed) =>
          // Verify type field is present
          parsed.hcursor.downField("type").as[String] shouldBe a[Right[_, _]]
          // Verify executionId field is present
          parsed.hcursor.downField("executionId").as[String] shouldBe a[Right[_, _]]
          // Verify timestamp field is present
          parsed.hcursor.downField("timestamp").as[Long] shouldBe a[Right[_, _]]
        case Left(error) =>
          fail(s"Failed to parse event JSON: ${error.getMessage}")
      }
    }
  }
}
