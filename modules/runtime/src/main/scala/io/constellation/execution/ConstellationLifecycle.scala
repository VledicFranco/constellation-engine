package io.constellation.execution

import cats.effect.{Deferred, IO, Ref}
import cats.implicits.*

import java.util.UUID
import scala.concurrent.duration.*

/** State of the Constellation lifecycle. */
enum LifecycleState:
  case Running, Draining, Stopped

/** Manages graceful shutdown of Constellation.
  *
  * Tracks in-flight executions and provides a shutdown method that:
  *   1. Stops accepting new executions 2. Waits for in-flight executions to drain (up to timeout)
  *      3. Cancels any remaining executions
  */
trait ConstellationLifecycle {

  /** Initiate graceful shutdown.
    *
    * @param drainTimeout
    *   Maximum time to wait for in-flight executions to complete
    */
  def shutdown(drainTimeout: FiniteDuration = 30.seconds): IO[Unit]

  /** Current lifecycle state. */
  def state: IO[LifecycleState]

  /** Number of currently in-flight executions. */
  def inflightCount: IO[Int]

  /** Register a new execution for lifecycle tracking.
    *
    * @return
    *   true if registered (Running state), false if rejected (Draining/Stopped)
    */
  def registerExecution(execId: UUID, handle: CancellableExecution): IO[Boolean]

  /** Deregister a completed execution. */
  def deregisterExecution(execId: UUID): IO[Unit]
}

object ConstellationLifecycle {

  /** Exception thrown when an execution is rejected because the system is shutting down. */
  class ShutdownRejectedException(message: String) extends RuntimeException(message)

  /** Create a new lifecycle manager. */
  def create: IO[ConstellationLifecycle] =
    for {
      stateRef <- Ref.of[IO, (LifecycleState, Map[UUID, CancellableExecution])](
        (LifecycleState.Running, Map.empty)
      )
      drainSignal <- Deferred[IO, Unit]
    } yield new ConstellationLifecycleImpl(stateRef, drainSignal)

  private class ConstellationLifecycleImpl(
      stateRef: Ref[IO, (LifecycleState, Map[UUID, CancellableExecution])],
      drainSignal: Deferred[IO, Unit]
  ) extends ConstellationLifecycle {

    def shutdown(drainTimeout: FiniteDuration): IO[Unit] =
      for {
        // Transition to Draining
        inflight <- stateRef.modify {
          case (LifecycleState.Running, executions) =>
            ((LifecycleState.Draining, executions), executions)
          case other =>
            (other, other._2)
        }

        // If no in-flight executions, complete drain immediately
        _ <-
          if inflight.isEmpty then {
            drainSignal.complete(()).attempt.void
          } else {
            IO.unit
          }

        // Wait for drain (with timeout)
        _ <- drainSignal.get.timeoutTo(
          drainTimeout,
          // Timeout: cancel remaining executions
          stateRef.get.flatMap { case (_, remaining) =>
            remaining.values.toList.traverse_(_.cancel)
          }
        )

        // Transition to Stopped
        _ <- stateRef.update { case (_, executions) =>
          (LifecycleState.Stopped, executions)
        }
      } yield ()

    def state: IO[LifecycleState] = stateRef.get.map(_._1)

    def inflightCount: IO[Int] = stateRef.get.map(_._2.size)

    def registerExecution(execId: UUID, handle: CancellableExecution): IO[Boolean] =
      stateRef.modify {
        case (LifecycleState.Running, executions) =>
          ((LifecycleState.Running, executions + (execId -> handle)), true)
        case other =>
          (other, false)
      }

    def deregisterExecution(execId: UUID): IO[Unit] =
      stateRef
        .modify { case (st, executions) =>
          val updated = executions - execId
          ((st, updated), (st, updated.isEmpty))
        }
        .flatMap {
          case (LifecycleState.Draining, true) =>
            // Last execution drained — signal completion
            drainSignal.complete(()).attempt.void
          case _ =>
            IO.unit
        }
  }
}
