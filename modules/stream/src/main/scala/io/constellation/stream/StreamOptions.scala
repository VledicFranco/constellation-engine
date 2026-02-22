package io.constellation.stream

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** Configuration options for stream compilation and execution.
  *
  * @param defaultParallelism
  *   Default number of parallel evalMap fibers per module node
  * @param defaultBufferSize
  *   Default bounded queue buffer size for source/sink connectors
  * @param shutdownTimeout
  *   Maximum time to wait for graceful drain on shutdown
  * @param metricsEnabled
  *   Whether to collect per-element stream metrics
  * @param maxBatchSize
  *   RFC-034: Maximum batch size for batch function invocations (0 = unlimited)
  * @param adaptiveBatching
  *   RFC-034 Phase 1B: Enable adaptive batch sizing based on throughput
  * @param batchRouting
  *   RFC-034 Phase 1B: Enable conditional batch routing (use batch if beneficial)
  */
final case class StreamOptions(
    defaultParallelism: Int = 1,
    defaultBufferSize: Int = 256,
    shutdownTimeout: FiniteDuration = 30.seconds,
    metricsEnabled: Boolean = true,
    maxBatchSize: Int = 0, // RFC-034: 0 = unlimited
    adaptiveBatching: Boolean = false, // RFC-034 Phase 1B
    batchRouting: Boolean = false // RFC-034 Phase 1B
)
