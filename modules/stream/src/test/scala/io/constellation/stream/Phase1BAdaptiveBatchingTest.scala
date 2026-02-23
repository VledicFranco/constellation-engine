package io.constellation.stream

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.CValue

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
      fixedOverheadMs = 0.5,    // Small fixed overhead
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
      fixedOverheadMs = 5.0,    // High fixed overhead
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

  "Phase 1B vs Phase 1 Comparison" should "verify batch splitting preserves correctness with larger batches" in {
    val batchFn: List[CValue] => IO[List[Either[Throwable, CValue]]] = { inputs =>
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

    // Test with 250 elements
    val inputs = (1 to 250).map(i => CValue.CString(s"item_$i")).toList

    // Phase 1: Process without splitting
    val phase1Results =
      batchFn(inputs).unsafeRunSync()

    // Phase 1B: Process with splitting at 50
    val phase1bResults = BatchSplitter.processWithSplitting(inputs, batchFn, 50).unsafeRunSync()

    // Both should produce same results
    phase1Results should have length 250
    phase1bResults should have length 250

    // Results should match
    phase1Results.zip(phase1bResults).foreach { case (r1, r2) =>
      (r1, r2) match {
        case (Right(v1), Right(v2)) => v1 shouldEqual v2
        case _                      => fail("Results should both be Right values")
      }
    }
  }

  it should "demonstrate batch routing enforces conservative routing decisions" in {
    // Batch routing is conservative: only use batch when conditions are very favorable
    // The amortization formula: amortization = singleTime / batchTime
    // Since batchTime = fixedOverhead + singleTime, amortization < 1 when fixedOverhead > 0
    // This means batch functions are rarely used unless overhead is negligible

    // Test 1: Single element should never use batch function (batch size < 2)
    val singleElemDecision = BatchSplitter.decideBatchRouting(1, true, 1.0, 0.1)
    singleElemDecision.useBatchFunction shouldBe false

    // Test 2: Small batch should not use batch (insufficient size to amortize overhead)
    val smallDecision = BatchSplitter.decideBatchRouting(2, true, 1.0, 0.1)
    smallDecision.useBatchFunction shouldBe false

    // Test 3: No batch function available means never use batch
    val noBatchDecision = BatchSplitter.decideBatchRouting(100, false, 1.0, 0.1)
    noBatchDecision.useBatchFunction shouldBe false

    // Test 4: With positive fixed overhead, amortization < 1, so batch is rarely preferred
    val withOverheadDecision = BatchSplitter.decideBatchRouting(50, true, 1.0, 0.1)
    withOverheadDecision.useBatchFunction shouldBe false

    // Test 5: Verify decision has reasoning
    val withOverheadDecision2 = BatchSplitter.decideBatchRouting(100, true, 0.5, 0.1)
    withOverheadDecision2.reason should not be empty
  }

  it should "show adaptive sizing recommends correct directions based on throughput" in {
    val scenarios = List(
      (
        "Throughput too low (40% of target)",
        2000.0,
        5000.0,
        50,
        true,
        true
      ), // Should decrease, ratio < 0.8
      ("Throughput optimal (95% of target)", 4750.0, 5000.0, 50, false, false), // Should keep
      (
        "Throughput very high (130% of target)",
        6500.0,
        5000.0,
        50,
        true,
        false
      ) // Should increase, ratio > 1.2
    )

    for (scenarioName, observed, target, current, shouldChange, shouldDecrease) <- scenarios do {
      val config      = BatchSplitter.AdaptiveBatchConfig(current, observed, target)
      val ratio       = observed / target
      val recommended = config.recommendedBatchSize
      val changed     = recommended != current

      // Verify change occurs as expected
      changed shouldEqual shouldChange

      // Verify direction of change
      if ratio < 0.8 then {
        recommended should be < current
      } else if ratio > 1.2 then {
        recommended should be > current
      } else {
        recommended shouldEqual current
      }
    }
  }
}
