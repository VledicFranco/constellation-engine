package io.constellation.execution

import cats.effect.{Deferred, Fiber, IO, Ref}
import cats.implicits.*
import io.constellation.Runtime

import java.util.UUID
import scala.concurrent.duration.FiniteDuration

/** Status of a cancellable DAG execution. */
enum ExecutionStatus:
  case Running, Completed, Cancelled, TimedOut
  case Failed(error: Throwable)

/** Handle for a running DAG execution that supports cancellation.
  *
  * Returned immediately by `Runtime.runCancellable` before execution completes. The caller can
  * await completion via `result` or cancel via `cancel`.
  */
trait CancellableExecution {

  /** Unique identifier for this execution. */
  def executionId: UUID

  /** Cancel this execution. Idempotent — calling after completion or repeated calls are no-ops. */
  def cancel: IO[Unit]

  /** Block until execution completes (or is cancelled/timed out). Returns the final state. */
  def result: IO[Runtime.State]

  /** Current execution status (non-blocking). */
  def status: IO[ExecutionStatus]
}

object CancellableExecution {

  /** Create a CancellableExecution that is already completed. */
  def completed(execId: UUID, state: Runtime.State): IO[CancellableExecution] =
    for {
      statusRef <- Ref.of[IO, ExecutionStatus](ExecutionStatus.Completed)
      resultDef <- Deferred[IO, Runtime.State]
      _         <- resultDef.complete(state)
    } yield new CancellableExecutionImpl(execId, statusRef, resultDef, Nil, Nil)

  /** Internal implementation backed by Ref + Deferred + Fiber handles. */
  private[constellation] class CancellableExecutionImpl(
      val executionId: UUID,
      statusRef: Ref[IO, ExecutionStatus],
      resultDeferred: Deferred[IO, Runtime.State],
      moduleFibers: List[Fiber[IO, Throwable, Unit]],
      transformFibers: List[Fiber[IO, Throwable, Unit]]
  ) extends CancellableExecution {

    def cancel: IO[Unit] = cancelWith(ExecutionStatus.Cancelled)

    /** Cancel with a specific status (used internally for timeout vs user cancel). */
    private[constellation] def cancelWith(targetStatus: ExecutionStatus): IO[Unit] =
      statusRef
        .modify {
          case ExecutionStatus.Running =>
            (targetStatus, true)
          case other =>
            (other, false)
        }
        .flatMap {
          case true =>
            // Cancel all fibers — Cats Effect 3 fiber.cancel is safe and idempotent
            val cancelAll = (moduleFibers ++ transformFibers).traverse_(_.cancel)
            cancelAll
          case false =>
            IO.unit
        }

    def result: IO[Runtime.State] = resultDeferred.get

    def status: IO[ExecutionStatus] = statusRef.get
  }

  /** Builder used by Runtime.runCancellable to construct the handle. */
  private[constellation] def create(
      execId: UUID,
      statusRef: Ref[IO, ExecutionStatus],
      resultDeferred: Deferred[IO, Runtime.State],
      moduleFibers: List[Fiber[IO, Throwable, Unit]],
      transformFibers: List[Fiber[IO, Throwable, Unit]]
  ): CancellableExecution =
    new CancellableExecutionImpl(execId, statusRef, resultDeferred, moduleFibers, transformFibers)
}
