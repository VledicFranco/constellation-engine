package io.constellation.http

import java.time.Instant
import java.util.UUID

import cats.effect.{Fiber, IO, Ref}
import cats.implicits.*

import io.constellation.stream.{StreamGraph, StreamMetrics, StreamMetricsSnapshot}

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** Status of a managed stream */
sealed trait StreamStatus

object StreamStatus {
  case object Running                    extends StreamStatus
  final case class Failed(error: String) extends StreamStatus
  case object Stopped                    extends StreamStatus
}

/** A stream graph managed by the lifecycle manager */
final case class ManagedStream(
    id: String,
    name: String,
    graph: StreamGraph,
    fiber: Fiber[IO, Throwable, Unit],
    startedAt: Instant,
    status: StreamStatus
)

/** Manages the lifecycle of running stream graphs.
  *
  * Provides deploy/stop/list/metrics operations backed by a `Ref[IO, Map[String, ManagedStream]]`.
  */
class StreamLifecycleManager private (
    state: Ref[IO, Map[String, ManagedStream]],
    eventPublisher: Ref[IO, Option[StreamEvent => IO[Unit]]]
) {
  private val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromName[IO]("io.constellation.http.StreamLifecycleManager")

  /** Deploy a stream graph with the given ID and name.
    *
    * Starts the graph on a fiber. Returns `Left` if a stream with the given ID already exists.
    */
  def deploy(id: String, name: String, graph: StreamGraph): IO[Either[String, ManagedStream]] =
    state.get.flatMap { streams =>
      if streams.contains(id) then IO.pure(Left(s"Stream with id '$id' already exists"))
      else {
        for {
          fiber <- graph.stream.compile.drain.handleErrorWith { err =>
            for {
              _ <- logger.error(err)(s"Stream '$name' ($id) failed")
              _ <- state.update(
                _.updatedWith(id)(_.map(_.copy(status = StreamStatus.Failed(err.getMessage))))
              )
              _ <- publishEvent(StreamEvent.StreamFailed(id, name, err.getMessage))
            } yield ()
          }.start
          now     = Instant.now()
          managed = ManagedStream(id, name, graph, fiber, now, StreamStatus.Running)
          _ <- state.update(_ + (id -> managed))
          _ <- logger.info(s"Deployed stream '$name' ($id)")
          _ <- publishEvent(StreamEvent.StreamDeployed(id, name))
        } yield Right(managed)
      }
    }

  /** Stop a running stream by ID. Returns `Left` if the stream is not found. */
  def stop(id: String): IO[Either[String, Unit]] =
    state.get.flatMap { streams =>
      streams.get(id) match {
        case None => IO.pure(Left(s"Stream '$id' not found"))
        case Some(managed) =>
          for {
            _ <- managed.graph.shutdown
            _ <- managed.fiber.cancel
            _ <- state.update(_.updatedWith(id)(_.map(_.copy(status = StreamStatus.Stopped))))
            _ <- logger.info(s"Stopped stream '${managed.name}' ($id)")
            _ <- publishEvent(StreamEvent.StreamStopped(id, managed.name))
          } yield Right(())
      }
    }

  /** Get a managed stream by ID */
  def get(id: String): IO[Option[ManagedStream]] =
    state.get.map(_.get(id))

  /** List all managed streams */
  def list: IO[List[ManagedStream]] =
    state.get.map(_.values.toList)

  /** Get a metrics snapshot for a stream. Returns `None` if the stream is not found. */
  def metrics(id: String): IO[Option[StreamMetricsSnapshot]] =
    state.get.flatMap { streams =>
      streams.get(id) match {
        case None          => IO.pure(None)
        case Some(managed) => managed.graph.metrics.snapshot.map(Some(_))
      }
    }

  /** Register an event publisher function for stream lifecycle events */
  def setEventPublisher(f: StreamEvent => IO[Unit]): IO[Unit] =
    eventPublisher.set(Some(f))

  private def publishEvent(event: StreamEvent): IO[Unit] =
    eventPublisher.get.flatMap {
      case Some(f) =>
        f(event).handleErrorWith(err =>
          logger.warn(s"Failed to publish stream event: ${err.getMessage}")
        )
      case None => IO.unit
    }
}

object StreamLifecycleManager {

  /** Create a new StreamLifecycleManager */
  def create: IO[StreamLifecycleManager] =
    for {
      state     <- Ref.of[IO, Map[String, ManagedStream]](Map.empty)
      publisher <- Ref.of[IO, Option[StreamEvent => IO[Unit]]](None)
    } yield new StreamLifecycleManager(state, publisher)
}
