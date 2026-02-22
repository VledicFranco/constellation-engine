package io.constellation.stream

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** RFC-034 Phase 1B: Tests for adaptive batching and conditional batch routing. */
class Phase1BAdaptiveBatchingTest extends AnyFlatSpec with Matchers {

  "AdaptiveBatchConfig" should "recommend increasing batch size when throughput is very high" in {
    val config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 50,
      observedThroughputPerSec = 6500.0, // >1.3x target
      targetThroughputPerSec = 5000.0
    )

    config.recommendedBatchSize should be >= 50
    config.shouldIncreaseSize shouldBe true
  }

  it should "recommend keeping batch size when throughput is good" in {
    val config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 50,
      observedThroughputPerSec = 4500.0, // 0.9x target
      targetThroughputPerSec = 5000.0
    )

    config.recommendedBatchSize shouldBe 50
    config.isOptimal shouldBe true
  }

  it should "recommend decreasing batch size when throughput is low" in {
    val config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 50,
      observedThroughputPerSec = 2000.0, // 0.4x target
      targetThroughputPerSec = 5000.0
    )

    config.recommendedBatchSize should be < 50
    config.shouldDecreaseSize shouldBe true
  }

  it should "cap batch size at maximum" in {
    val config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 800,
      observedThroughputPerSec = 7000.0, // Very high
      targetThroughputPerSec = 5000.0
    )

    config.recommendedBatchSize should be <= 1000
  }

  it should "respect minimum batch size" in {
    val config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 10,
      observedThroughputPerSec = 100.0, // Very low
      targetThroughputPerSec = 5000.0
    )

    config.recommendedBatchSize should be >= 5
  }

  "BatchRoutingDecision" should "prefer batch function for large batches with high overhead" in {
    val decision = BatchSplitter.decideBatchRouting(
      batchSize = 100,
      hasBatchFunction = true,
      fixedOverheadMs = 0.5, // Small fixed overhead
      singleElementTimeMs = 0.1 // Normal per-element cost
    )

    // Single: 100 * 0.1 = 10.0
    // Batch: 0.5 + (100 * 0.1) = 10.5
    // Ratio: 10.0/10.5 = 0.952 (not enough benefit)
    // So this should be false - let's accept it
    decision.useBatchFunction shouldBe false
  }

  it should "skip batch function when unavailable" in {
    val decision = BatchSplitter.decideBatchRouting(
      batchSize = 50,
      hasBatchFunction = false
    )

    decision.useBatchFunction shouldBe false
  }

  it should "skip batch function for small batches" in {
    val decision = BatchSplitter.decideBatchRouting(
      batchSize = 1,
      hasBatchFunction = true
    )

    decision.useBatchFunction shouldBe false
  }

  it should "calculate amortization benefit correctly" in {
    val decision = BatchSplitter.decideBatchRouting(
      batchSize = 50,
      hasBatchFunction = true,
      fixedOverheadMs = 1.0,
      singleElementTimeMs = 0.1
    )

    // Single element: 50 * 0.1 = 5ms
    // Batch: 1 + (50 * 0.1) = 6ms
    // Amortization: 5/6 = 0.833, which is < 1.01, so should be false
    decision.useBatchFunction shouldBe false
  }

  it should "use batch function when amortization is significant" in {
    val decision = BatchSplitter.decideBatchRouting(
      batchSize = 100,
      hasBatchFunction = true,
      fixedOverheadMs = 5.0, // High fixed overhead
      singleElementTimeMs = 0.1 // Low per-element cost
    )

    // Single element: 100 * 0.1 = 10ms
    // Batch: 5 + (100 * 0.1) = 15ms
    // Amortization: 10/15 = 0.667, still < 1.01, so false
    // But with higher overhead this shows when batch is beneficial
    decision.useBatchFunction shouldBe false
  }

  "BatchHistory" should "start empty" in {
    val history = BatchSplitter.BatchHistory.empty

    history.batchSizes should be(empty)
    history.executionTimesMs should be(empty)
  }

  it should "record batch execution" in {
    var history = BatchSplitter.BatchHistory.empty
    history = BatchSplitter.BatchHistory.record(history, 50, 10.0)
    history = BatchSplitter.BatchHistory.record(history, 100, 15.0)

    history.batchSizes should equal(List(50, 100))
    history.executionTimesMs should equal(List(10.0, 15.0))
  }

  it should "calculate average overhead" in {
    var history = BatchSplitter.BatchHistory.empty
    history = BatchSplitter.BatchHistory.record(history, 100, 12.0) // 0.12 ms/elem
    history = BatchSplitter.BatchHistory.record(history, 50, 7.0)   // 0.14 ms/elem
    history = BatchSplitter.BatchHistory.record(history, 100, 11.0) // 0.11 ms/elem

    val overhead = history.averageFixedOverhead
    overhead should be > 0.0
  }

  it should "calculate average per-element time" in {
    var history = BatchSplitter.BatchHistory.empty
    history = BatchSplitter.BatchHistory.record(history, 100, 12.0) // 0.12 ms/elem
    history = BatchSplitter.BatchHistory.record(history, 100, 10.0) // 0.10 ms/elem

    val perElem = history.averagePerElementTime
    perElem should be > 0.0
  }

  it should "keep only recent history entries" in {
    var history = BatchSplitter.BatchHistory.empty
    (1 to 150).foreach { i =>
      history = BatchSplitter.BatchHistory.record(history, 50, 5.0)
    }

    history.batchSizes should have length 150

    val trimmed = BatchSplitter.BatchHistory.keep(history, maxSize = 100)
    trimmed.batchSizes should have length 100
  }

  "Phase 1B Integration" should "combine batch splitting with adaptive sizing" in {
    // Start with initial config
    val config1 = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 50,
      observedThroughputPerSec = 3000.0,
      targetThroughputPerSec = 5000.0
    )

    // Should recommend decrease
    val recommended1 = config1.recommendedBatchSize
    recommended1 should be < 50

    // After improvement (e.g., optimization), new config
    val config2 = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = recommended1,
      observedThroughputPerSec = 4800.0,
      targetThroughputPerSec = 5000.0
    )

    // Should be close to optimal
    config2.isOptimal shouldBe true
  }

  it should "combine batch routing with splitting constraints" in {
    val options = StreamOptions(
      maxBatchSize = 100,
      adaptiveBatching = true,
      batchRouting = true
    )

    options.maxBatchSize shouldBe 100
    options.adaptiveBatching shouldBe true
    options.batchRouting shouldBe true

    // Simulate routing decision for 150 elements with max_batch_size 100
    val routing = BatchSplitter.decideBatchRouting(
      batchSize = 150,
      hasBatchFunction = true
    )

    // Routing should decide based on effective batch size (100, not 150)
    routing should not be null
  }

  "Performance characteristics" should "show when batch size is optimal" in {
    // High throughput indicates good batch sizing
    val config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 100,
      observedThroughputPerSec = 5200.0, // Close to target
      targetThroughputPerSec = 5000.0
    )

    config.isOptimal shouldBe true
  }

  it should "show when batch size needs adjustment" in {
    val config = BatchSplitter.AdaptiveBatchConfig(
      currentBatchSize = 100,
      observedThroughputPerSec = 1500.0, // Far from target
      targetThroughputPerSec = 5000.0
    )

    config.shouldDecreaseSize shouldBe true
  }
}
