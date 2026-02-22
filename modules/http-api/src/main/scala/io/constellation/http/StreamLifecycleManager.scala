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
    * Starts the graph on a fiber. Returns `Left` if a stream with the given ID already exists. Uses
    * a two-phase modify pattern to prevent TOCTOU races on the state map.
    */
  def deploy(id: String, name: String, graph: StreamGraph): IO[Either[String, ManagedStream]] =
    // Phase 1: Atomic existence check
    state
      .modify { streams =>
        if streams.contains(id) then (streams, false) else (streams, true)
      }
      .flatMap { canProceed =>
        if !canProceed then IO.pure(Left(s"Stream with id '$id' already exists"))
        else
          for {
            // Phase 2: Launch fiber
            fiber <- graph.stream.compile.drain.handleErrorWith { err =>
              val message = safeMessage(err)
              logger.error(err)(s"Stream '$name' ($id) failed") *>
                state.update(
                  _.updatedWith(id)(_.map(_.copy(status = StreamStatus.Failed(message))))
                ) *>
                publishEvent(StreamEvent.StreamFailed(id, name, message))
            }.start
            now     = Instant.now()
            managed = ManagedStream(id, name, graph, fiber, now, StreamStatus.Running)
            // Phase 3: Atomic insert (second check guards against concurrent deploy races)
            inserted <- state.modify { streams =>
              if streams.contains(id) then (streams, false)
              else (streams + (id -> managed), true)
            }
            result <-
              if inserted then
                logger.info(s"Deployed stream '$name' ($id)") *>
                  publishEvent(StreamEvent.StreamDeployed(id, name)).as(Right(managed))
              else
                // Race lost — cancel the fiber we just started
                fiber.cancel.as(Left(s"Stream with id '$id' already exists"))
          } yield result
      }

  /** Stop and remove a stream by ID. Returns `Left` if the stream is not found.
    *
    * Uses `state.modify` to atomically extract and remove the entry, then performs shutdown effects
    * outside the atomic operation. This prevents TOCTOU races (e.g. double-stop) and ensures
    * stopped streams don't leak in the state map.
    */
  def stop(id: String): IO[Either[String, Unit]] =
    state
      .modify { streams =>
        streams.get(id) match {
          case None          => (streams, None)
          case Some(managed) => (streams - id, Some(managed))
        }
      }
      .flatMap {
        case None =>
          IO.pure(Left(s"Stream '$id' not found"))
        case Some(managed) =>
          managed.graph.shutdown *>
            managed.fiber.cancel *>
            logger.info(s"Stopped stream '${managed.name}' ($id)") *>
            publishEvent(StreamEvent.StreamStopped(id, managed.name)).as(Right(()))
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
          logger.warn(s"Failed to publish stream event: ${safeMessage(err)}")
        )
      case None => IO.unit
    }

  private def safeMessage(e: Throwable): String =
    Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
}

object StreamLifecycleManager {

  /** Create a new StreamLifecycleManager */
  def create: IO[StreamLifecycleManager] =
    for {
      state     <- Ref.of[IO, Map[String, ManagedStream]](Map.empty)
      publisher <- Ref.of[IO, Option[StreamEvent => IO[Unit]]](None)
    } yield new StreamLifecycleManager(state, publisher)
}
