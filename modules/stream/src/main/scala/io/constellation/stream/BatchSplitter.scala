package io.constellation.stream

import cats.effect.IO
import cats.implicits.*

import io.constellation.CValue

/** RFC-034 Phase 1B: Batch splitting and adaptive batching strategies.
  *
  * Splits large batches into chunks respecting max_batch_size constraints, and provides adaptive
  * batching based on performance characteristics.
  */
object BatchSplitter {

  /** Split a batch into chunks, respecting max_batch_size constraint.
    *
    * @param inputs
    *   List of input values to process
    * @param maxBatchSize
    *   Maximum batch size (0 = unlimited)
    * @return
    *   List of batches, each with at most maxBatchSize elements
    */
  def splitBatch(inputs: List[CValue], maxBatchSize: Int): List[List[CValue]] =
    if maxBatchSize <= 0 then {
      // No limit - return single batch
      List(inputs)
    } else if inputs.length <= maxBatchSize then {
      // Fits in one batch
      List(inputs)
    } else {
      // Split into chunks
      inputs.grouped(maxBatchSize).toList
    }

  /** Process a batch through a batch function, splitting if necessary.
    *
    * Splits the batch according to maxBatchSize, processes each chunk, and combines results
    * preserving original order.
    *
    * @param inputs
    *   List of input values
    * @param batchFn
    *   Batch function to apply to each chunk
    * @param maxBatchSize
    *   Maximum batch size constraint
    * @return
    *   List of results in original order
    */
  def processWithSplitting(
      inputs: List[CValue],
      batchFn: List[CValue] => IO[List[Either[Throwable, CValue]]],
      maxBatchSize: Int
  ): IO[List[Either[Throwable, CValue]]] = {
    val chunks = splitBatch(inputs, maxBatchSize)

    // Process each chunk and concatenate results
    chunks
      .traverse(chunk => batchFn(chunk))
      .map(_.flatten)
  }

  /** Adaptive batch sizing based on throughput metrics.
    *
    * Adjusts batch size based on observed performance:
    *   - If throughput is high (>1000 elem/sec): keep current size
    *   - If throughput is medium (100-1000): stay with current size
    *   - If throughput is low (<100): consider smaller batch size
    *   - If throughput is very high (>5000): consider increasing batch size
    */
  case class AdaptiveBatchConfig(
      currentBatchSize: Int,
      observedThroughputPerSec: Double,
      targetThroughputPerSec: Double = 5000.0
  ) {
    def recommendedBatchSize: Int = {
      val ratio = observedThroughputPerSec / targetThroughputPerSec

      if ratio > 1.2 then {
        // Throughput is very high - increase batch size to improve amortization
        math.min((currentBatchSize * 1.5).toInt, 1000)
      } else if ratio > 0.8 then {
        // Throughput is good - keep current size
        currentBatchSize
      } else if ratio > 0.5 then {
        // Throughput is moderate - slightly decrease
        math.max((currentBatchSize * 0.9).toInt, 10)
      } else {
        // Throughput is low - decrease batch size significantly
        math.max((currentBatchSize * 0.7).toInt, 5)
      }
    }

    def shouldIncreaseSize: Boolean = recommendedBatchSize > currentBatchSize
    def shouldDecreaseSize: Boolean = recommendedBatchSize < currentBatchSize
    def isOptimal: Boolean          = recommendedBatchSize == currentBatchSize
  }

  /** Conditional batch routing based on batch characteristics.
    *
    * Decides whether to use batch function or fall back to single-element based on batch size and
    * expected overhead amortization.
    */
  case class BatchRoutingDecision(
      useBatchFunction: Boolean,
      reason: String
  )

  /** Determine whether to use batch function for a given batch size.
    *
    * Uses batch function if:
    *   - Batch size is large enough to amortize fixed overhead (typically >5)
    *   - Batch function is available
    *   - Batch function overhead is low relative to throughput gain
    *
    * @param batchSize
    *   Number of elements in batch
    * @param hasBatchFunction
    *   Whether batch function is available
    * @param fixedOverheadMs
    *   Estimated fixed overhead per batch call (connection setup, etc.)
    * @param singleElementTimeMs
    *   Estimated time for single-element execution
    * @return
    *   Routing decision
    */
  def decideBatchRouting(
      batchSize: Int,
      hasBatchFunction: Boolean,
      fixedOverheadMs: Double = 1.0,
      singleElementTimeMs: Double = 0.1
  ): BatchRoutingDecision =
    if !hasBatchFunction then {
      BatchRoutingDecision(false, "No batch function available")
    } else if batchSize < 2 then {
      BatchRoutingDecision(false, "Batch size too small")
    } else {
      // Calculate amortization benefit
      val singleElementTotal = singleElementTimeMs * batchSize
      val batchTotal         = fixedOverheadMs + (singleElementTimeMs * batchSize)
      val amortization       = singleElementTotal / batchTotal

      if amortization > 1.1 then {
        // >10% improvement
        BatchRoutingDecision(true, s"Good amortization: ${amortization}x speedup expected")
      } else if amortization > 1.01 then {
        // >1% improvement, but marginal
        BatchRoutingDecision(true, s"Marginal amortization: ${amortization}x speedup")
      } else {
        BatchRoutingDecision(false, s"Insufficient amortization: ${amortization}x")
      }
    }

  /** Estimate overhead based on batch history.
    *
    * Learns from batch execution history to refine overhead estimates.
    */
  case class BatchHistory(
      batchSizes: List[Int],
      executionTimesMs: List[Double]
  ) {
    def averageFixedOverhead: Double =
      if batchSizes.isEmpty then 1.0
      else {
        val perElementCosts = batchSizes.zip(executionTimesMs).map { case (size, time) =>
          time / size
        }
        val avgPerElement = perElementCosts.sum / perElementCosts.length
        val minPerElement = perElementCosts.min

        // Fixed overhead = total - (per-element cost * batch size)
        // Use min per-element as baseline, difference is overhead
        batchSizes
          .zip(executionTimesMs)
          .map { case (size, time) =>
            time - (minPerElement * size)
          }
          .sum / batchSizes.length
      }

    def averagePerElementTime: Double =
      if batchSizes.isEmpty then 0.1
      else {
        batchSizes
          .zip(executionTimesMs)
          .map { case (size, time) =>
            time / size
          }
          .sum / batchSizes.length
      }
  }

  object BatchHistory {
    def empty: BatchHistory = BatchHistory(List.empty, List.empty)

    def record(
        history: BatchHistory,
        batchSize: Int,
        executionTimeMs: Double
    ): BatchHistory =
      history.copy(
        batchSizes = history.batchSizes :+ batchSize,
        executionTimesMs = history.executionTimesMs :+ executionTimeMs
      )

    def keep(history: BatchHistory, maxSize: Int = 100): BatchHistory =
      if history.batchSizes.length > maxSize then {
        val dropCount = history.batchSizes.length - maxSize
        history.copy(
          batchSizes = history.batchSizes.drop(dropCount),
          executionTimesMs = history.executionTimesMs.drop(dropCount)
        )
      } else {
        history
      }
  }
}
