package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExecutionStorageTest extends AnyFlatSpec with Matchers {

  private def createStorage(maxExec: Int = 100): ExecutionStorage[IO] =
    ExecutionStorage.inMemory(ExecutionStorage.Config(maxExecutions = maxExec)).unsafeRunSync()

  private def sampleExecution(
      id: String = java.util.UUID.randomUUID().toString,
      dagName: String = "test-dag",
      status: ExecutionStatus = ExecutionStatus.Completed,
      startTime: Long = System.currentTimeMillis(),
      scriptPath: Option[String] = Some("test.cst")
  ): StoredExecution = StoredExecution(
    executionId = id,
    dagName = dagName,
    scriptPath = scriptPath,
    startTime = startTime,
    endTime = Some(startTime + 100),
    inputs = Map("x" -> Json.fromInt(42)),
    outputs = Some(Map("result" -> Json.fromString("done"))),
    status = status,
    nodeResults = Map.empty,
    dagVizIR = None,
    sampleRate = 1.0,
    source = ExecutionSource.Dashboard
  )

  // ============= Store and Get =============

  "InMemoryExecutionStorage" should "store and retrieve an execution" in {
    val storage = createStorage()
    val exec    = sampleExecution()

    val result = (for {
      id      <- storage.store(exec)
      fetched <- storage.get(id)
    } yield fetched).unsafeRunSync()

    result shouldBe defined
    result.get.dagName shouldBe "test-dag"
  }

  it should "return None for non-existent execution" in {
    val storage = createStorage()
    val result  = storage.get("non-existent").unsafeRunSync()
    result shouldBe None
  }

  // ============= List =============

  it should "list executions sorted by most recent first" in {
    val storage = createStorage()
    val exec1   = sampleExecution(startTime = 1000)
    val exec2   = sampleExecution(startTime = 2000)
    val exec3   = sampleExecution(startTime = 3000)

    val result = (for {
      _ <- storage.store(exec1)
      _ <- storage.store(exec2)
      _ <- storage.store(exec3)
      l <- storage.list(10, 0)
    } yield l).unsafeRunSync()

    result should have size 3
    result.head.startTime shouldBe 3000
    result.last.startTime shouldBe 1000
  }

  it should "support pagination with limit and offset" in {
    val storage = createStorage()

    val result = (for {
      _ <- storage.store(sampleExecution(startTime = 1000))
      _ <- storage.store(sampleExecution(startTime = 2000))
      _ <- storage.store(sampleExecution(startTime = 3000))
      l <- storage.list(1, 1)
    } yield l).unsafeRunSync()

    result should have size 1
    result.head.startTime shouldBe 2000
  }

  // ============= ListByScript =============

  it should "list executions by script path" in {
    val storage = createStorage()

    val result = (for {
      _ <- storage.store(sampleExecution(scriptPath = Some("scripts/a.cst")))
      _ <- storage.store(sampleExecution(scriptPath = Some("scripts/b.cst")))
      _ <- storage.store(sampleExecution(scriptPath = Some("scripts/a.cst")))
      l <- storage.listByScript("a.cst", 10)
    } yield l).unsafeRunSync()

    result should have size 2
  }

  it should "return empty list for unmatched script path" in {
    val storage = createStorage()

    val result = (for {
      _ <- storage.store(sampleExecution(scriptPath = Some("scripts/a.cst")))
      l <- storage.listByScript("nonexistent.cst", 10)
    } yield l).unsafeRunSync()

    result shouldBe empty
  }

  // ============= Stats =============

  it should "return correct stats" in {
    val storage = createStorage()

    val result = (for {
      _ <- storage.store(sampleExecution(status = ExecutionStatus.Running))
      _ <- storage.store(sampleExecution(status = ExecutionStatus.Completed))
      _ <- storage.store(sampleExecution(status = ExecutionStatus.Failed))
      s <- storage.stats
    } yield s).unsafeRunSync()

    result.totalExecutions shouldBe 3
    result.runningExecutions shouldBe 1
    result.completedExecutions shouldBe 1
    result.failedExecutions shouldBe 1
  }

  it should "return empty stats for empty storage" in {
    val storage = createStorage()
    val result  = storage.stats.unsafeRunSync()

    result.totalExecutions shouldBe 0
    result.oldestExecutionTime shouldBe None
    result.newestExecutionTime shouldBe None
  }

  // ============= Update =============

  it should "update an existing execution" in {
    val storage = createStorage()
    val exec    = sampleExecution(status = ExecutionStatus.Running)

    val result = (for {
      id      <- storage.store(exec)
      updated <- storage.update(id)(_.copy(status = ExecutionStatus.Completed))
    } yield updated).unsafeRunSync()

    result shouldBe defined
    result.get.status shouldBe ExecutionStatus.Completed
  }

  it should "return None when updating non-existent execution" in {
    val storage = createStorage()
    val result =
      storage.update("non-existent")(_.copy(status = ExecutionStatus.Failed)).unsafeRunSync()
    result shouldBe None
  }

  // ============= Delete =============

  it should "delete an existing execution" in {
    val storage = createStorage()
    val exec    = sampleExecution()

    val result = (for {
      id      <- storage.store(exec)
      deleted <- storage.delete(id)
      fetched <- storage.get(id)
    } yield (deleted, fetched)).unsafeRunSync()

    result._1 shouldBe true
    result._2 shouldBe None
  }

  it should "return false when deleting non-existent execution" in {
    val storage = createStorage()
    val result  = storage.delete("non-existent").unsafeRunSync()
    result shouldBe false
  }

  // ============= Clear =============

  it should "clear all executions" in {
    val storage = createStorage()

    val result = (for {
      _ <- storage.store(sampleExecution())
      _ <- storage.store(sampleExecution())
      _ <- storage.clear
      s <- storage.stats
    } yield s).unsafeRunSync()

    result.totalExecutions shouldBe 0
  }

  // ============= LRU Eviction =============

  it should "evict oldest executions when max capacity reached" in {
    val storage = createStorage(maxExec = 2)
    val exec1   = sampleExecution(id = "exec-1", startTime = 1000)
    val exec2   = sampleExecution(id = "exec-2", startTime = 2000)
    val exec3   = sampleExecution(id = "exec-3", startTime = 3000)

    val result = (for {
      _      <- storage.store(exec1)
      _      <- storage.store(exec2)
      _      <- storage.store(exec3)
      first  <- storage.get("exec-1")
      second <- storage.get("exec-2")
      third  <- storage.get("exec-3")
      s      <- storage.stats
    } yield (first, second, third, s)).unsafeRunSync()

    result._1 shouldBe None // evicted
    result._2 shouldBe defined
    result._3 shouldBe defined
    result._4.totalExecutions shouldBe 2
  }

  // ============= createExecution Helper =============

  "ExecutionStorage.createExecution" should "create execution with Running status" in {
    val exec = ExecutionStorage.createExecution(
      dagName = "test",
      scriptPath = Some("test.cst"),
      inputs = Map("x" -> Json.fromInt(1)),
      source = ExecutionSource.Dashboard
    )
    exec.status shouldBe ExecutionStatus.Running
    exec.outputs shouldBe None
    exec.endTime shouldBe None
    exec.executionId should not be empty
  }

  it should "create execution with custom sample rate" in {
    val exec = ExecutionStorage.createExecution(
      dagName = "test",
      scriptPath = None,
      inputs = Map.empty,
      source = ExecutionSource.API,
      sampleRate = 0.5
    )
    exec.sampleRate shouldBe 0.5
    exec.source shouldBe ExecutionSource.API
  }

  // ============= shouldSample =============

  "ExecutionStorage.shouldSample" should "always sample VSCode executions" in {
    val result = ExecutionStorage.shouldSample(ExecutionSource.VSCodeExtension, Some(0.0), 0.0)
    result shouldBe true
  }

  it should "use script rate when provided" in {
    // With rate 1.0, should always sample
    val result = ExecutionStorage.shouldSample(ExecutionSource.Dashboard, Some(1.0), 0.0)
    result shouldBe true
  }

  it should "reject with rate 0.0" in {
    val result = ExecutionStorage.shouldSample(ExecutionSource.API, Some(0.0), 1.0)
    result shouldBe false
  }

  // ============= durationMs =============

  "StoredExecution.durationMs" should "compute duration when endTime is set" in {
    val exec = sampleExecution().copy(startTime = 1000, endTime = Some(1500))
    exec.durationMs shouldBe Some(500)
  }

  it should "return None when endTime is not set" in {
    val exec = sampleExecution().copy(endTime = None)
    exec.durationMs shouldBe None
  }

  // ============= ExecutionSummary.fromStored =============

  "ExecutionSummary.fromStored" should "create summary from stored execution" in {
    val exec    = sampleExecution()
    val summary = ExecutionSummary.fromStored(exec)

    summary.executionId shouldBe exec.executionId
    summary.dagName shouldBe exec.dagName
    summary.status shouldBe exec.status
    summary.nodeCount shouldBe exec.nodeResults.size
  }

  it should "truncate long output preview" in {
    val longOutput = "a" * 100
    val exec = sampleExecution().copy(
      outputs = Some(Map("result" -> Json.fromString(longOutput)))
    )
    val summary = ExecutionSummary.fromStored(exec)

    summary.outputPreview shouldBe defined
    // The JSON representation includes quotes, so full string is > 50 chars
    // Preview should contain "..." indicating truncation
    summary.outputPreview.get should include("...")
  }

  it should "handle execution with no outputs" in {
    val exec    = sampleExecution().copy(outputs = None)
    val summary = ExecutionSummary.fromStored(exec)
    summary.outputPreview shouldBe None
  }
}
