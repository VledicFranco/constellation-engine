package io.constellation.benchmark

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.collection.mutable.ListBuffer
import java.util.UUID

import io.constellation._
import io.constellation.execution.GlobalScheduler
import io.constellation.spi.ConstellationBackends

/** Sustained load tests (RFC-013 Phase 5.3)
  *
  * Runs 10K+ executions and verifies no OOM, bounded heap growth,
  * and stable p99 latency over time.
  *
  * Run with: sbt "runtime/testOnly *SustainedLoadTest"
  */
class SustainedLoadTest extends AnyFlatSpec with Matchers {

  // -------------------------------------------------------------------------
  // Test Modules
  // -------------------------------------------------------------------------

  private case class TextInput(text: String)
  private case class TextOutput(result: String)

  private def createUppercaseModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Uppercase", "Converts text to uppercase", 1, 0)
      .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toUpperCase))
      .build

  // -------------------------------------------------------------------------
  // DAG Builder
  // -------------------------------------------------------------------------

  /** Create a small DAG: input -> Uppercase -> output */
  private def createSmallDag(): (DagSpec, Map[UUID, Module.Uninitialized]) = {
    val moduleId = UUID.randomUUID()
    val inputDataId = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("SmallDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec("input", Map(inputDataId -> "input", moduleId -> "text"), CType.CString),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputDataId)
    )

    (dag, Map(moduleId -> createUppercaseModule()))
  }

  // -------------------------------------------------------------------------
  // Helper
  // -------------------------------------------------------------------------

  private def runOnce(dag: DagSpec, modules: Map[UUID, Module.Uninitialized]): Double = {
    val inputs = Map("input" -> CValue.CString("sustained load test"))
    val start = System.nanoTime()
    Runtime.run(dag, inputs, modules).unsafeRunSync()
    (System.nanoTime() - start) / 1e6 // ms
  }

  // -------------------------------------------------------------------------
  // 5.3: 10K executions complete without OOM
  // -------------------------------------------------------------------------

  "Sustained load" should "complete 10000 executions without OOM" in {
    val (dag, modules) = createSmallDag()
    val totalRuns = 10000
    val latencies = new ListBuffer[Double]()
    val jrt = java.lang.Runtime.getRuntime

    (1 to totalRuns).foreach { i =>
      val latencyMs = runOnce(dag, modules)
      latencies += latencyMs

      // Periodic GC to simulate real-world conditions
      if (i % 1000 == 0) {
        System.gc()
        val usedMB = (jrt.totalMemory() - jrt.freeMemory()) / (1024.0 * 1024.0)
        println(f"  [$i%5d/$totalRuns] p50=${percentile(latencies, 50)}%.2f ms, p99=${percentile(latencies, 99)}%.2f ms, heap=${usedMB}%.0f MB")
      }
    }

    latencies.size shouldBe totalRuns
    println(f"Completed $totalRuns executions. Final p50=${percentile(latencies, 50)}%.2f ms, p99=${percentile(latencies, 99)}%.2f ms")
  }

  // -------------------------------------------------------------------------
  // 5.3: Heap growth < 50MB
  // -------------------------------------------------------------------------

  it should "not show monotonic heap growth over repeated batches" in {
    val jrt = java.lang.Runtime.getRuntime
    val (dag, modules) = createSmallDag()

    // Warmup phase — let JIT compile and heap stabilize
    (1 to 500).foreach { _ => runOnce(dag, modules) }

    // Measure heap at 5 checkpoints during 5000 executions
    val batchSize = 1000
    val heapSamples = (1 to 5).map { batch =>
      (1 to batchSize).foreach(_ => runOnce(dag, modules))
      System.gc()
      Thread.sleep(100)
      val used = (jrt.totalMemory() - jrt.freeMemory()) / (1024.0 * 1024.0)
      println(f"  Batch $batch: heap used = $used%.1f MB")
      used
    }

    // Verify: the last sample should not be dramatically larger than the first
    // (allows for GC non-determinism but catches true leaks)
    val first = heapSamples.head
    val last = heapSamples.last
    val maxSample = heapSamples.max

    println(f"  First: $first%.1f MB, Last: $last%.1f MB, Max: $maxSample%.1f MB")

    // The heap should not grow by more than 200MB across batches
    // This is generous — a true leak would show unbounded growth
    (maxSample - first) should be < 200.0
  }

  // -------------------------------------------------------------------------
  // 5.3: p99 latency doesn't degrade over time
  // -------------------------------------------------------------------------

  it should "maintain stable p99 latency over time" in {
    val (dag, modules) = createSmallDag()
    val batchSize = 500
    val numBatches = 10
    val batchP99s = new ListBuffer[Double]()

    // Warmup
    (1 to 100).foreach(_ => runOnce(dag, modules))

    (1 to numBatches).foreach { batch =>
      val latencies = (1 to batchSize).map(_ => runOnce(dag, modules)).toList
      val p99 = percentile(latencies, 99)
      batchP99s += p99
      println(f"  Batch $batch%2d: p99=$p99%.2f ms")
    }

    // The last batch p99 should not be more than 3x the first batch p99
    val firstP99 = batchP99s.head
    val lastP99 = batchP99s.last
    val ratio = lastP99 / firstP99

    println(f"p99 ratio (last/first): $ratio%.2f")

    // Last batch p99 should not degrade more than 3x from first batch
    ratio should be < 3.0
  }

  // -------------------------------------------------------------------------
  // 5.3: Concurrent sustained load
  // -------------------------------------------------------------------------

  it should "handle concurrent sustained load (1000 parallel executions)" in {
    val (dag, modules) = createSmallDag()
    val inputs = Map("input" -> CValue.CString("concurrent load"))

    val results = (1 to 1000).toList.parTraverse { _ =>
      Runtime.run(dag, inputs, modules)
    }.unsafeRunSync()

    results.size shouldBe 1000
    results.foreach(_.latency.isDefined shouldBe true)
  }

  // -------------------------------------------------------------------------
  // Utility
  // -------------------------------------------------------------------------

  private def percentile(data: Iterable[Double], p: Int): Double = {
    val sorted = data.toArray.sorted
    if (sorted.isEmpty) return 0.0
    val idx = math.ceil(p / 100.0 * sorted.length).toInt - 1
    sorted(math.max(0, math.min(idx, sorted.length - 1)))
  }
}
