package io.constellation.stream.testing

import scala.concurrent.duration.FiniteDuration

import cats.effect.IO
import cats.effect.std.Queue
import cats.implicits.*

import io.constellation.CValue
import io.constellation.stream.*
import io.constellation.stream.connector.*

/** Test harness for stream pipelines.
  *
  * Provides ergonomic methods for pushing data into memory sources, collecting from memory sinks,
  * and inspecting metrics.
  */
final class StreamTestKit private (
    val sourceQueues: Map[String, Queue[IO, Option[CValue]]],
    val sinkQueues: Map[String, Queue[IO, CValue]],
    val registry: ConnectorRegistry,
    val graph: Option[StreamGraph]
) {

  /** Push a value into a named source. */
  def emit(sourceName: String, value: CValue): IO[Unit] =
    sourceQueues
      .get(sourceName)
      .map(_.offer(Some(value)))
      .getOrElse(IO.raiseError(new NoSuchElementException(s"Source '$sourceName' not found")))

  /** Push multiple values into a named source. */
  def emitAll(sourceName: String, values: List[CValue]): IO[Unit] =
    sourceQueues
      .get(sourceName)
      .map(q => values.traverse_(v => q.offer(Some(v))))
      .getOrElse(IO.raiseError(new NoSuchElementException(s"Source '$sourceName' not found")))

  /** Signal end-of-stream for a named source. */
  def complete(sourceName: String): IO[Unit] =
    sourceQueues
      .get(sourceName)
      .map(_.offer(None))
      .getOrElse(IO.raiseError(new NoSuchElementException(s"Source '$sourceName' not found")))

  /** Collect all values that have arrived at a named sink. */
  def collectSink(sinkName: String): IO[List[CValue]] =
    sinkQueues
      .get(sinkName)
      .map(_.tryTakeN(None).map(_.toList))
      .getOrElse(IO.raiseError(new NoSuchElementException(s"Sink '$sinkName' not found")))

  /** Get a metrics snapshot (if graph is available). */
  def metricsSnapshot: IO[StreamMetricsSnapshot] =
    graph.map(_.metrics.snapshot).getOrElse(IO.pure(StreamMetricsSnapshot(0, 0, 0, Map.empty)))

}

object StreamTestKit {

  /** Create a test kit with named memory sources and sinks.
    *
    * @param sourceNames
    *   Names of memory source connectors to create
    * @param sinkNames
    *   Names of memory sink connectors to create
    * @param bufferSize
    *   Buffer size for the underlying queues
    */
  def create(
      sourceNames: List[String],
      sinkNames: List[String],
      bufferSize: Int = 256
  ): IO[StreamTestKit] =
    for {
      srcQueues <- sourceNames.traverse(name =>
        Queue.bounded[IO, Option[CValue]](bufferSize).map(name -> _)
      )
      snkQueues <- sinkNames.traverse(name =>
        Queue.bounded[IO, CValue](bufferSize).map(name -> _)
      )
    } yield {
      val srcMap = srcQueues.toMap
      val snkMap = snkQueues.toMap

      val registryBuilder = srcMap.foldLeft(ConnectorRegistry.builder) { case (b, (name, q)) =>
        b.source(name, MemoryConnector.source(name, q))
      }
      val registry = snkMap.foldLeft(registryBuilder) { case (b, (name, q)) =>
        b.sink(name, MemoryConnector.sink(name, q))
      }.build

      new StreamTestKit(srcMap, snkMap, registry, None)
    }

}
