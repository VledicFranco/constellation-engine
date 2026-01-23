package io.constellation

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExecutionTrackerTest extends AnyFlatSpec with Matchers {

  // ===========================================================================
  // Basic Tracking Tests
  // ===========================================================================

  "ExecutionTracker" should "start and finish an execution" in {
    val result = (for {
      tracker     <- ExecutionTracker.create
      executionId <- tracker.startExecution("test-dag")
      _           <- tracker.finishExecution(executionId)
      trace       <- tracker.getTrace(executionId)
    } yield trace).unsafeRunSync()

    result shouldBe defined
    val trace = result.get
    trace.dagName shouldBe "test-dag"
    trace.isComplete shouldBe true
    trace.endTime shouldBe defined
    trace.totalDurationMs shouldBe defined
  }

  it should "record node start as Running" in {
    val result = (for {
      tracker     <- ExecutionTracker.create
      executionId <- tracker.startExecution("test-dag")
      _           <- tracker.recordNodeStart(executionId, "node-1")
      trace       <- tracker.getTrace(executionId)
    } yield trace).unsafeRunSync()

    result shouldBe defined
    val trace  = result.get
    val nodeResult = trace.nodeResults.get("node-1")
    nodeResult shouldBe defined
    nodeResult.get.status shouldBe NodeStatus.Running
    nodeResult.get.value shouldBe None
    nodeResult.get.durationMs shouldBe None
  }

  it should "record node completion with value and duration" in {
    val testValue = Json.obj("name" -> Json.fromString("test"), "count" -> Json.fromInt(42))

    val result = (for {
      tracker     <- ExecutionTracker.create
      executionId <- tracker.startExecution("test-dag")
      _           <- tracker.recordNodeStart(executionId, "node-1")
      _           <- tracker.recordNodeComplete(executionId, "node-1", testValue, 150)
      trace       <- tracker.getTrace(executionId)
    } yield trace).unsafeRunSync()

    result shouldBe defined
    val trace      = result.get
    val nodeResult = trace.nodeResults.get("node-1")
    nodeResult shouldBe defined
    nodeResult.get.status shouldBe NodeStatus.Completed
    nodeResult.get.value shouldBe Some(testValue)
    nodeResult.get.durationMs shouldBe Some(150)
    nodeResult.get.error shouldBe None
  }

  it should "record node failure with error" in {
    val result = (for {
      tracker     <- ExecutionTracker.create
      executionId <- tracker.startExecution("test-dag")
      _           <- tracker.recordNodeStart(executionId, "node-1")
      _           <- tracker.recordNodeFailed(executionId, "node-1", "Something went wrong", 50)
      trace       <- tracker.getTrace(executionId)
    } yield trace).unsafeRunSync()

    result shouldBe defined
    val trace      = result.get
    val nodeResult = trace.nodeResults.get("node-1")
    nodeResult shouldBe defined
    nodeResult.get.status shouldBe NodeStatus.Failed
    nodeResult.get.error shouldBe Some("Something went wrong")
    nodeResult.get.durationMs shouldBe Some(50)
    nodeResult.get.value shouldBe None
  }

  // ===========================================================================
  // Multiple Nodes Tests
  // ===========================================================================

  it should "track multiple nodes independently" in {
    val value1 = Json.fromString("result-1")
    val value2 = Json.fromInt(100)

    val result = (for {
      tracker     <- ExecutionTracker.create
      executionId <- tracker.startExecution("test-dag")
      _           <- tracker.recordNodeStart(executionId, "node-1")
      _           <- tracker.recordNodeStart(executionId, "node-2")
      _           <- tracker.recordNodeComplete(executionId, "node-1", value1, 100)
      _           <- tracker.recordNodeFailed(executionId, "node-2", "Failed", 50)
      trace       <- tracker.getTrace(executionId)
    } yield trace).unsafeRunSync()

    result shouldBe defined
    val trace = result.get
    trace.nodeResults.size shouldBe 2

    val node1 = trace.nodeResults.get("node-1").get
    node1.status shouldBe NodeStatus.Completed
    node1.value shouldBe Some(value1)

    val node2 = trace.nodeResults.get("node-2").get
    node2.status shouldBe NodeStatus.Failed
    node2.error shouldBe Some("Failed")
  }

  // ===========================================================================
  // Multiple Executions Tests
  // ===========================================================================

  it should "track multiple executions independently" in {
    val result = (for {
      tracker <- ExecutionTracker.create
      exec1   <- tracker.startExecution("dag-1")
      exec2   <- tracker.startExecution("dag-2")
      _       <- tracker.recordNodeComplete(exec1, "node-a", Json.fromInt(1), 10)
      _       <- tracker.recordNodeComplete(exec2, "node-b", Json.fromInt(2), 20)
      trace1  <- tracker.getTrace(exec1)
      trace2  <- tracker.getTrace(exec2)
    } yield (trace1, trace2)).unsafeRunSync()

    val (trace1, trace2) = result
    trace1.get.dagName shouldBe "dag-1"
    trace1.get.nodeResults.keys.toSet shouldBe Set("node-a")

    trace2.get.dagName shouldBe "dag-2"
    trace2.get.nodeResults.keys.toSet shouldBe Set("node-b")
  }

  it should "list all traces" in {
    val result = (for {
      tracker <- ExecutionTracker.create
      _       <- tracker.startExecution("dag-1")
      _       <- tracker.startExecution("dag-2")
      _       <- tracker.startExecution("dag-3")
      traces  <- tracker.getAllTraces
    } yield traces).unsafeRunSync()

    result.size shouldBe 3
    result.map(_.dagName).toSet shouldBe Set("dag-1", "dag-2", "dag-3")
  }

  // ===========================================================================
  // LRU Eviction Tests
  // ===========================================================================

  it should "evict old traces when maxTraces is exceeded" in {
    val config = ExecutionTracker.Config(maxTraces = 3)

    val result = (for {
      tracker <- ExecutionTracker.create(config)
      exec1   <- tracker.startExecution("dag-1")
      exec2   <- tracker.startExecution("dag-2")
      exec3   <- tracker.startExecution("dag-3")
      exec4   <- tracker.startExecution("dag-4") // Should evict dag-1
      traces  <- tracker.getAllTraces
      trace1  <- tracker.getTrace(exec1)
    } yield (traces, trace1)).unsafeRunSync()

    val (traces, trace1) = result
    traces.size shouldBe 3
    trace1 shouldBe None // dag-1 should have been evicted
    traces.map(_.dagName).toSet shouldBe Set("dag-2", "dag-3", "dag-4")
  }

  it should "evict multiple traces if needed" in {
    val config = ExecutionTracker.Config(maxTraces = 2)

    val result = (for {
      tracker <- ExecutionTracker.create(config)
      _       <- tracker.startExecution("dag-1")
      _       <- tracker.startExecution("dag-2")
      _       <- tracker.startExecution("dag-3")
      _       <- tracker.startExecution("dag-4")
      _       <- tracker.startExecution("dag-5")
      traces  <- tracker.getAllTraces
    } yield traces).unsafeRunSync()

    result.size shouldBe 2
    result.map(_.dagName).toSet shouldBe Set("dag-4", "dag-5")
  }

  // ===========================================================================
  // Value Truncation Tests
  // ===========================================================================

  it should "truncate large values" in {
    val config    = ExecutionTracker.Config(maxValueSizeBytes = 100)
    val largeJson = Json.fromString("x" * 200) // > 100 bytes

    val result = (for {
      tracker     <- ExecutionTracker.create(config)
      executionId <- tracker.startExecution("test-dag")
      _           <- tracker.recordNodeComplete(executionId, "node-1", largeJson, 10)
      trace       <- tracker.getTrace(executionId)
    } yield trace).unsafeRunSync()

    result shouldBe defined
    val nodeResult = result.get.nodeResults.get("node-1").get
    nodeResult.value.get.asString.get should startWith("<truncated:")
  }

  it should "not truncate small values" in {
    val config    = ExecutionTracker.Config(maxValueSizeBytes = 1000)
    val smallJson = Json.fromString("small value")

    val result = (for {
      tracker     <- ExecutionTracker.create(config)
      executionId <- tracker.startExecution("test-dag")
      _           <- tracker.recordNodeComplete(executionId, "node-1", smallJson, 10)
      trace       <- tracker.getTrace(executionId)
    } yield trace).unsafeRunSync()

    result shouldBe defined
    val nodeResult = result.get.nodeResults.get("node-1").get
    nodeResult.value shouldBe Some(smallJson)
  }

  // ===========================================================================
  // Clear Tests
  // ===========================================================================

  it should "clear all traces" in {
    val result = (for {
      tracker <- ExecutionTracker.create
      _       <- tracker.startExecution("dag-1")
      _       <- tracker.startExecution("dag-2")
      before  <- tracker.getAllTraces
      _       <- tracker.clear
      after   <- tracker.getAllTraces
    } yield (before, after)).unsafeRunSync()

    val (before, after) = result
    before.size shouldBe 2
    after.size shouldBe 0
  }

  // ===========================================================================
  // Thread Safety Tests
  // ===========================================================================

  it should "handle concurrent operations safely" in {
    import cats.implicits._

    val result = (for {
      tracker     <- ExecutionTracker.create
      executionId <- tracker.startExecution("concurrent-dag")
      // Simulate concurrent node completions
      _ <- List.range(0, 100).parTraverse { i =>
        tracker.recordNodeComplete(executionId, s"node-$i", Json.fromInt(i), i.toLong)
      }
      trace <- tracker.getTrace(executionId)
    } yield trace).unsafeRunSync()

    result shouldBe defined
    result.get.nodeResults.size shouldBe 100
  }

  // ===========================================================================
  // fromRuntimeState Tests
  // ===========================================================================

  "ExecutionTracker.fromRuntimeState" should "convert completed module status" in {
    import cats.Eval
    import scala.concurrent.duration._
    import java.util.UUID

    val moduleId = UUID.randomUUID()
    val dataId   = UUID.randomUUID()

    val runtimeState = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = DagSpec.empty("test"),
      moduleStatus = Map(
        moduleId -> Eval.now(Module.Status.Fired(100.millis, None))
      ),
      data = Map(
        dataId -> Eval.now(CValue.CString("test-value"))
      ),
      latency = Some(200.millis)
    )

    val trace = ExecutionTracker.fromRuntimeState(
      executionId = "exec-1",
      dagName = "test-dag",
      runtimeState = runtimeState,
      startTime = System.currentTimeMillis() - 200
    )

    trace.executionId shouldBe "exec-1"
    trace.dagName shouldBe "test-dag"
    trace.isComplete shouldBe true

    // Check module result
    val moduleResult = trace.nodeResults.get(moduleId.toString)
    moduleResult shouldBe defined
    moduleResult.get.status shouldBe NodeStatus.Completed
    moduleResult.get.durationMs shouldBe Some(100)

    // Check data result
    val dataResult = trace.nodeResults.get(dataId.toString)
    dataResult shouldBe defined
    dataResult.get.status shouldBe NodeStatus.Completed
    dataResult.get.value shouldBe Some(Json.fromString("test-value"))
  }

  it should "convert failed module status" in {
    import cats.Eval
    import java.util.UUID

    val moduleId = UUID.randomUUID()

    val runtimeState = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = DagSpec.empty("test"),
      moduleStatus = Map(
        moduleId -> Eval.now(Module.Status.Failed(new RuntimeException("Test error")))
      ),
      data = Map.empty,
      latency = None
    )

    val trace = ExecutionTracker.fromRuntimeState(
      executionId = "exec-1",
      dagName = "test-dag",
      runtimeState = runtimeState,
      startTime = System.currentTimeMillis()
    )

    val moduleResult = trace.nodeResults.get(moduleId.toString)
    moduleResult shouldBe defined
    moduleResult.get.status shouldBe NodeStatus.Failed
    moduleResult.get.error shouldBe Some("Test error")
  }

  it should "convert timed out module status" in {
    import cats.Eval
    import scala.concurrent.duration._
    import java.util.UUID

    val moduleId = UUID.randomUUID()

    val runtimeState = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = DagSpec.empty("test"),
      moduleStatus = Map(
        moduleId -> Eval.now(Module.Status.Timed(5000.millis))
      ),
      data = Map.empty,
      latency = None
    )

    val trace = ExecutionTracker.fromRuntimeState(
      executionId = "exec-1",
      dagName = "test-dag",
      runtimeState = runtimeState,
      startTime = System.currentTimeMillis()
    )

    val moduleResult = trace.nodeResults.get(moduleId.toString)
    moduleResult shouldBe defined
    moduleResult.get.status shouldBe NodeStatus.Failed
    moduleResult.get.error.get should include("Timed out")
    moduleResult.get.durationMs shouldBe Some(5000)
  }

  // ===========================================================================
  // NodeStatus Tests
  // ===========================================================================

  "NodeStatus" should "have correct string representation" in {
    NodeStatus.Pending.toString shouldBe "Pending"
    NodeStatus.Running.toString shouldBe "Running"
    NodeStatus.Completed.toString shouldBe "Completed"
    NodeStatus.Failed.toString shouldBe "Failed"
  }

  // ===========================================================================
  // ExecutionTrace Tests
  // ===========================================================================

  "ExecutionTrace" should "calculate total duration when complete" in {
    val trace = ExecutionTrace(
      executionId = "exec-1",
      dagName = "test",
      startTime = 1000,
      endTime = Some(1500)
    )

    trace.totalDurationMs shouldBe Some(500)
  }

  it should "return None for duration when incomplete" in {
    val trace = ExecutionTrace(
      executionId = "exec-1",
      dagName = "test",
      startTime = 1000,
      endTime = None
    )

    trace.totalDurationMs shouldBe None
    trace.isComplete shouldBe false
  }
}
