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
  */
final case class StreamOptions(
    defaultParallelism: Int = 1,
    defaultBufferSize: Int = 256,
    shutdownTimeout: FiniteDuration = 30.seconds,
    metricsEnabled: Boolean = true
)
