package io.constellation.stream

import cats.effect.IO

import fs2.Stream

/** A compiled stream graph ready for execution.
  *
  * @param stream
  *   The composed fs2 Stream graph (runs all sources → transforms → sinks)
  * @param metrics
  *   Live metrics tracker for monitoring
  * @param shutdown
  *   Graceful shutdown trigger — signals all sources to drain
  */
final case class StreamGraph(
    stream: Stream[IO, Unit],
    metrics: StreamMetrics,
    shutdown: IO[Unit]
)
