package io.constellation.stream.connector

import cats.effect.IO
import cats.effect.std.Queue

import io.constellation.CValue

import fs2.{Pipe, Stream}

/** Queue-based in-memory source and sink connectors for testing and local use.
  *
  * MemorySource wraps a `Queue[IO, Option[CValue]]` — emit `None` to signal end-of-stream.
  * MemorySink wraps a `Queue[IO, CValue]` — collect from it after stream completes.
  */
object MemoryConnector {

  /** Create a memory source backed by a bounded queue.
    *
    * Push values with `queue.offer(Some(value))` and signal completion with `queue.offer(None)`.
    */
  def source(
      sourceName: String,
      queue: Queue[IO, Option[CValue]]
  ): SourceConnector =
    new SourceConnector {
      override def name: String     = sourceName
      override def typeName: String = "memory"
      override def stream(config: ValidatedConnectorConfig): Stream[IO, CValue] =
        Stream.fromQueueNoneTerminated(queue)
    }

  /** Create a memory sink backed by a bounded queue.
    *
    * Collect results with `queue.tryTakeN(None)` after stream completes.
    */
  def sink(
      sinkName: String,
      queue: Queue[IO, CValue]
  ): SinkConnector =
    new SinkConnector {
      override def name: String     = sinkName
      override def typeName: String = "memory"
      override def pipe(config: ValidatedConnectorConfig): Pipe[IO, CValue, Unit] =
        _.evalMap(queue.offer)
    }

  /** Create a source/sink pair for testing round-trips. Returns (sourceQueue, sinkQueue,
    * sourceConnector, sinkConnector).
    */
  def pair(
      name: String,
      bufferSize: Int = 256
  ): IO[(Queue[IO, Option[CValue]], Queue[IO, CValue], SourceConnector, SinkConnector)] =
    for {
      srcQ  <- Queue.bounded[IO, Option[CValue]](bufferSize)
      sinkQ <- Queue.bounded[IO, CValue](bufferSize)
    } yield (srcQ, sinkQ, source(name, srcQ), sink(name, sinkQ))
}
