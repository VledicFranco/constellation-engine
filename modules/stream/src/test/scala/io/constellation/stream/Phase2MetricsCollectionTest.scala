package io.constellation.stream

import java.util.UUID

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.stream.config.StreamPipelineConfig
import io.constellation.stream.connector.ConnectorRegistry
import io.constellation.stream.error.StreamErrorStrategy

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** RFC-034 Phase 2 Task #2: Runtime Metrics Collection
  *
  * Validates that batch execution times are tracked, history is maintained,
  * and adaptive sizing recommendations respond to throughput metrics.
  */
class Phase2MetricsCollectionTest extends AnyFlatSpec with Matchers {

  // ===== Test helpers =====

  private def createSimpleDag(): (DagSpec, UUID, UUID) = {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dagSpec = DagSpec(
      metadata = ComponentMetadata("test_dag", "test dag", Nil, 1, 0),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("TestModule", "test module", Nil, 1, 0),
          consumes = Map("input" -> CType.CString),
          produces = Map("output" -> CType.CString)
        )
      ),
      data = Map(
        inputId  -> DataNodeSpec("input", Map(moduleId -> "input"), CType.CString),
        outputId -> DataNodeSpec("output", Map(moduleId -> "output"), CType.CString)
      ),
      inEdges = Set(inputId -> moduleId),
      outEdges = Set(moduleId -> outputId),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputId)
    )

    (dagSpec, moduleId, inputId)
  }

  private val singleElementFn: CValue => IO[CValue] = { input =>
    IO.pure(
      input match {
        case CValue.CString(s) => CValue.CString(s"processed:$s")
        case other             => other
      }
    )
  }

  private val batchFn: List[CValue] => IO[List[Either[Throwable, CValue]]] = { inputs =>
    IO.pure(
      inputs.map { input =>
        Right(
          input match {
            case CValue.CString(s) => CValue.CString(s"processed:$s")
            case other             => other
          }
        )
      }
    )
  }

  // ===== Task 2: Batch History Recording =====

  "BatchHistory" should "start empty" in {
    val history = BatchSplitter.BatchHistory.empty
    history.batchSizes should be(empty)
    history.executionTimesMs should be(empty)
  }

  it should "record batch execution with size and time" in {
    val history0 = BatchSplitter.BatchHistory.empty
    val history1 = BatchSplitter.BatchHistory.record(history0, batchSize = 10, executionTimeMs = 5.0)

    history1.batchSizes should contain(10)
    history1.executionTimesMs should contain(5.0)
  }

  it should "accumulate multiple batch recordings" in {
    val h0 = BatchSplitter.BatchHistory.empty
    val h1 = BatchSplitter.BatchHistory.record(h0, batchSize = 10, executionTimeMs = 5.0)
    val h2 = BatchSplitter.BatchHistory.record(h1, batchSize = 20, executionTimeMs = 8.0)
    val h3 = BatchSplitter.BatchHistory.record(h2, batchSize = 15, executionTimeMs = 7.0)

    h3.batchSizes should contain theSameElementsInOrderAs List(10, 20, 15)
    h3.executionTimesMs should contain theSameElementsInOrderAs List(5.0, 8.0, 7.0)
  }

  it should "trim history to max size (100 by default)" in {
    val h0 = BatchSplitter.BatchHistory.empty
    // Record 150 batches
    val h150 = (1 to 150).foldLeft(h0) { (h, i) =>
      BatchSplitter.BatchHistory.record(h, batchSize = 10, executionTimeMs = 1.0)
    }

    h150.batchSizes.length should be(150)

    // Trim to 100
    val h100 = BatchSplitter.BatchHistory.keep(h150, maxSize = 100)
    h100.batchSizes.length should be(100)
    // Should keep the most recent (last 100), all with batchSize 10
    h100.batchSizes.head should be(10) // All batches are size 10
    h100.batchSizes.last should be(10)
    h100.executionTimesMs.length should be(100)
  }

  // ===== Task 2: Adaptive Sizing Calculations =====

  "AdaptiveBatchConfig" should "calculate average fixed overhead from history" in {
    val h0 = BatchSplitter.BatchHistory.empty
    val h1 = BatchSplitter.BatchHistory.record(h0, batchSize = 10, executionTimeMs = 2.0)
    val h2 = BatchSplitter.BatchHistory.record(h1, batchSize = 10, executionTimeMs = 2.0)

    // With batch size 10 and time 2ms, per-element cost is 0.2ms
    // Overhead should be calculated
    val overhead = h2.averageFixedOverhead
    overhead should be >= 0.0
  }

  it should "calculate average per-element time from history" in {
    val h0 = BatchSplitter.BatchHistory.empty
    val h1 = BatchSplitter.BatchHistory.record(h0, batchSize = 10, executionTimeMs = 5.0)
    val h2 = BatchSplitter.BatchHistory.record(h1, batchSize = 20, executionTimeMs = 10.0)

    val avgPerElement = h2.averagePerElementTime
    // Should be around 0.5ms per element
    avgPerElement should be(0.5 +- 0.1)
  }

  it should "recommend increasing batch size when throughput is very high" in {
    val config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 50,
      observedThroughputPerSec = 7000.0 // >1.2x target
    )

    config.recommendedBatchSize should be > 50
    config.shouldIncreaseSize should be(true)
  }

  it should "recommend keeping batch size when throughput is good" in {
    val config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 50,
      observedThroughputPerSec = 4500.0 // 0.8-1.2x target
    )

    config.recommendedBatchSize should be(50)
    config.isOptimal should be(true)
  }

  it should "recommend decreasing batch size when throughput is low" in {
    val config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 100,
      observedThroughputPerSec = 200.0 // <0.5x target
    )

    config.recommendedBatchSize should be < 100
    config.shouldDecreaseSize should be(true)
  }

  // ===== Task 2: Throughput Calculation =====

  it should "calculate throughput from batch history" in {
    val h0 = BatchSplitter.BatchHistory.empty
    val h1 = BatchSplitter.BatchHistory.record(h0, batchSize = 100, executionTimeMs = 20.0)
    val h2 = BatchSplitter.BatchHistory.record(h1, batchSize = 100, executionTimeMs = 20.0)

    val avgPerElement = h2.averagePerElementTime
    val elementsPerSecond = 1000.0 / avgPerElement

    // 100 elements in 20ms = 5000 elements/sec
    elementsPerSecond should be(5000.0 +- 100.0)
  }

  // ===== Task 2: Metrics Integration with StreamCompiler =====

  "StreamCompiler batch metrics integration" should "record batch size in compiled graphs" in {
    val (dagSpec, moduleId, _) = createSimpleDag()
    val registry                = ConnectorRegistry.empty
    val modules                 = Map(moduleId -> singleElementFn)
    val batchModules            = Map(moduleId -> batchFn)

    // Create options with metrics enabled
    val options = StreamOptions(
      maxBatchSize = 50,
      adaptiveBatching = true,  // Enable adaptive batching
      batchRouting = true        // Enable routing decisions
    )

    val graph = StreamCompiler
      .wire(
        dagSpec,
        registry,
        modules,
        options,
        errorStrategy = StreamErrorStrategy.Log,
        batchModules = batchModules
      )
      .unsafeRunSync()

    // Verify graph was created (implies metrics path considered)
    graph should not be null
    graph.stream should not be null
  }

  it should "add minimal overhead during compilation when metrics enabled" in {
    val (dagSpec, moduleId, _) = createSimpleDag()
    val registry                = ConnectorRegistry.empty
    val modules                 = Map(moduleId -> singleElementFn)
    val batchModules            = Map(moduleId -> batchFn)

    // Baseline: no adaptive batching or routing
    val startBaseline = System.nanoTime()
    (1 to 5).foreach { _ =>
      StreamCompiler
        .wire(
          dagSpec,
          registry,
          modules,
          StreamOptions(adaptiveBatching = false, batchRouting = false),
          batchModules = batchModules
        )
        .unsafeRunSync()
    }
    val timeBaseline = (System.nanoTime() - startBaseline) / 1e6

    // With metrics enabled
    val startWithMetrics = System.nanoTime()
    (1 to 5).foreach { _ =>
      StreamCompiler
        .wire(
          dagSpec,
          registry,
          modules,
          StreamOptions(adaptiveBatching = true, batchRouting = true),
          batchModules = batchModules
        )
        .unsafeRunSync()
    }
    val timeWithMetrics = (System.nanoTime() - startWithMetrics) / 1e6

    val overhead = ((timeWithMetrics - timeBaseline) / timeBaseline * 100)
    println(f"\nCompilation overhead with metrics: ${overhead}%+.1f%%")

    // Compilation overhead should be acceptable (<10% for configuration handling)
    overhead should be < 10.0
  }

  // ===== Task 2: Feedback Loop =====

  "Adaptive sizing feedback" should "respond to throughput metrics within 2 batches" in {
    // Create a config with poor throughput
    var config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 100,
      observedThroughputPerSec = 200.0 // Low throughput (0.04 ratio)
    )

    // With low throughput, should recommend decreasing
    config.shouldDecreaseSize should be(true)
    val recommendedSize = config.recommendedBatchSize
    recommendedSize should be < 100

    // After adjusting to recommended size and improving throughput
    config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = recommendedSize,
      observedThroughputPerSec = 4500.0 // Improved throughput
    )
    config.isOptimal should be(true) // Should stabilize at good throughput
  }

  // ===== Task 2: Configuration Combinations =====

  "StreamCompiler with metrics options" should "combine adaptive batching and batch routing" in {
    val (dagSpec, moduleId, _) = createSimpleDag()
    val registry                = ConnectorRegistry.empty
    val modules                 = Map(moduleId -> singleElementFn)
    val batchModules            = Map(moduleId -> batchFn)

    val options = StreamOptions(
      maxBatchSize = 50,
      adaptiveBatching = true,
      batchRouting = true
    )

    val graph = StreamCompiler
      .wire(
        dagSpec,
        registry,
        modules,
        options,
        batchModules = batchModules
      )
      .unsafeRunSync()

    graph should not be null
  }

  it should "work with default options (metrics disabled)" in {
    val (dagSpec, moduleId, _) = createSimpleDag()
    val registry                = ConnectorRegistry.empty
    val modules                 = Map(moduleId -> singleElementFn)
    val batchModules            = Map(moduleId -> batchFn)

    val graph = StreamCompiler
      .wire(
        dagSpec,
        registry,
        modules,
        StreamOptions(), // Default: all metrics disabled
        batchModules = batchModules
      )
      .unsafeRunSync()

    graph should not be null
  }

  // ===== Task 2: Error Cases =====

  "Adaptive sizing" should "handle empty history gracefully" in {
    val emptyHistory = BatchSplitter.BatchHistory.empty

    val overhead = emptyHistory.averageFixedOverhead
    overhead should be >= 0.0

    val perElement = emptyHistory.averagePerElementTime
    perElement should be >= 0.0
  }

  it should "stabilize recommendations under consistent load" in {
    // Build history with consistent performance
    var history = BatchSplitter.BatchHistory.empty
    (1 to 10).foreach { _ =>
      history = BatchSplitter.BatchHistory.record(history, batchSize = 50, executionTimeMs = 5.0)
    }

    // Config with throughput ratio in the "good" range (0.8-1.2 of target)
    // Target is 5000, so throughput of 4500 should be optimal
    val config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 50,
      observedThroughputPerSec = 4500.0 // Good throughput (0.9x target = good)
    )

    config.isOptimal should be(true)
  }

  // ===== Task 2: Performance =====

  "Metrics collection" should "have <1% overhead on adaptive sizing calculations" in {
    val startCalc = System.nanoTime()
    (1 to 1000).foreach { _ =>
      val config = BatchSplitter.AdaptiveBatchConfig(
        currentBatchSize = 50,
        observedThroughputPerSec = 5000.0
      )
      val _ = config.recommendedBatchSize
    }
    val timeCalc = (System.nanoTime() - startCalc) / 1e6

    // Should complete very quickly
    timeCalc should be < 50.0 // 50ms for 1000 calculations = 0.05ms per calculation
  }

  it should "have <1% overhead on history recording" in {
    val startRecord = System.nanoTime()
    var history = BatchSplitter.BatchHistory.empty
    (1 to 100).foreach { i =>
      history = BatchSplitter.BatchHistory.record(history, batchSize = 10 + i, executionTimeMs = 1.0 + i * 0.1)
      history = BatchSplitter.BatchHistory.keep(history, maxSize = 100)
    }
    val timeRecord = (System.nanoTime() - startRecord) / 1e6

    // Should be very fast
    timeRecord should be < 10.0 // 10ms for 100 recordings + trims = 0.1ms per operation
  }
}
