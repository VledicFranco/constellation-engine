package io.constellation.stream

import cats.effect.{IO, Ref}
import cats.implicits.*

/** Live streaming metrics collected during stream execution. */
trait StreamMetrics {

  /** Get a snapshot of all current metrics. */
  def snapshot: IO[StreamMetricsSnapshot]

  /** Record an element processed by a module. */
  def recordElement(moduleName: String): IO[Unit]

  /** Record an error in a module. */
  def recordError(moduleName: String): IO[Unit]

  /** Record an element sent to the dead-letter queue. */
  def recordDlq(moduleName: String): IO[Unit]
}

/** Immutable snapshot of stream metrics at a point in time. */
final case class StreamMetricsSnapshot(
    totalElements: Long,
    totalErrors: Long,
    totalDlq: Long,
    perModule: Map[String, ModuleStreamMetrics]
)

/** Per-module streaming metrics. */
final case class ModuleStreamMetrics(
    elementsProcessed: Long,
    errors: Long,
    dlqCount: Long
)

object StreamMetrics {

  /** Create a new metrics tracker backed by atomic Refs. */
  def create: IO[StreamMetrics] =
    for {
      totalElems <- Ref.of[IO, Long](0L)
      totalErrs  <- Ref.of[IO, Long](0L)
      totalDlqs  <- Ref.of[IO, Long](0L)
      perModule  <- Ref.of[IO, Map[String, (Long, Long, Long)]](Map.empty)
    } yield new StreamMetrics {

      override def snapshot: IO[StreamMetricsSnapshot] =
        for {
          te  <- totalElems.get
          ter <- totalErrs.get
          td  <- totalDlqs.get
          pm  <- perModule.get
        } yield StreamMetricsSnapshot(
          totalElements = te,
          totalErrors = ter,
          totalDlq = td,
          perModule = pm.map { case (name, (e, er, d)) =>
            name -> ModuleStreamMetrics(e, er, d)
          }
        )

      override def recordElement(moduleName: String): IO[Unit] =
        totalElems.update(_ + 1) *>
          perModule.update { m =>
            val (e, er, d) = m.getOrElse(moduleName, (0L, 0L, 0L))
            m.updated(moduleName, (e + 1, er, d))
          }

      override def recordError(moduleName: String): IO[Unit] =
        totalErrs.update(_ + 1) *>
          perModule.update { m =>
            val (e, er, d) = m.getOrElse(moduleName, (0L, 0L, 0L))
            m.updated(moduleName, (e, er + 1, d))
          }

      override def recordDlq(moduleName: String): IO[Unit] =
        totalDlqs.update(_ + 1) *>
          perModule.update { m =>
            val (e, er, d) = m.getOrElse(moduleName, (0L, 0L, 0L))
            m.updated(moduleName, (e, er, d + 1))
          }
    }

  /** No-op metrics (for when metrics are disabled). */
  val noop: StreamMetrics = new StreamMetrics {
    override def snapshot: IO[StreamMetricsSnapshot] =
      IO.pure(StreamMetricsSnapshot(0, 0, 0, Map.empty))
    override def recordElement(moduleName: String): IO[Unit] = IO.unit
    override def recordError(moduleName: String): IO[Unit]   = IO.unit
    override def recordDlq(moduleName: String): IO[Unit]     = IO.unit
  }
}
