package io.constellation.execution

import cats.effect.{IO, Ref}
import cats.effect.std.Semaphore

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.*

/** Concurrency limiter using a semaphore.
  *
  * Limits the number of concurrent executions of an operation. Uses Cats Effect Semaphore for
  * efficient and fair permit management.
  *
  * ==Usage==
  *
  * {{{
  * // Create limiter allowing 5 concurrent executions
  * val limiter = ConcurrencyLimiter(5).unsafeRunSync()
  *
  * // Execute with concurrency limit
  * limiter.withPermit(myOperation)
  *
  * // Or acquire/release manually
  * limiter.acquire >> myOperation.guarantee(limiter.release)
  * }}}
  *
  * ==Difference from Rate Limiting==
  *
  * | Aspect   | Concurrency         | Rate Limiting       |
  * |:---------|:--------------------|:--------------------|
  * | Limits   | Active executions   | Operations per time |
  * | Question | "How many at once?" | "How fast?"         |
  * | Use case | Connection pools    | API quotas          |
  */
class ConcurrencyLimiter private (
    semaphore: Semaphore[IO],
    maxConcurrent: Int,
    activeRef: Ref[IO, Long],
    peakRef: Ref[IO, Long],
    totalRef: Ref[IO, Long],
    waitingRef: Ref[IO, Long]
) {

  /** Execute an operation with concurrency limiting.
    *
    * Acquires a permit before executing and releases it after, even if the operation fails.
    *
    * @param operation
    *   The operation to execute
    * @return
    *   The result of the operation
    */
  def withPermit[A](operation: IO[A]): IO[A] =
    semaphore.permit.use { _ =>
      for {
        _      <- activeRef.update(_ + 1)
        _      <- totalRef.update(_ + 1)
        _      <- updatePeak
        result <- operation.guarantee(activeRef.update(_ - 1))
      } yield result
    }

  /** Acquire a permit, waiting if necessary.
    *
    * Remember to release the permit when done:
    * {{{
    * limiter.acquire >> operation.guarantee(limiter.release)
    * }}}
    */
  def acquire: IO[Unit] =
    for {
      _ <- waitingRef.update(_ + 1)
      _ <- semaphore.acquire
      _ <- waitingRef.update(_ - 1)
      _ <- activeRef.update(_ + 1)
      _ <- totalRef.update(_ + 1)
      _ <- updatePeak
    } yield ()

  /** Release a permit. */
  def release: IO[Unit] =
    for {
      _ <- activeRef.update(_ - 1)
      _ <- semaphore.release
    } yield ()

  /** Try to acquire a permit without waiting.
    *
    * @return
    *   true if permit was acquired, false if limit reached
    */
  def tryAcquire: IO[Boolean] =
    semaphore.tryAcquire.flatMap {
      case true =>
        activeRef.update(_ + 1) >>
          totalRef.update(_ + 1) >>
          updatePeak >>
          IO.pure(true)
      case false =>
        IO.pure(false)
    }

  /** Get the number of currently active executions. */
  def active: IO[Long] = activeRef.get

  /** Get the number of available permits. */
  def available: IO[Long] = semaphore.available

  /** Get concurrency limiter statistics. */
  def stats: IO[ConcurrencyStats] =
    for {
      activeCount    <- activeRef.get
      peakCount      <- peakRef.get
      totalCount     <- totalRef.get
      waitingCount   <- waitingRef.get
      availableCount <- semaphore.available
    } yield ConcurrencyStats(
      maxConcurrent = maxConcurrent,
      currentActive = activeCount,
      peakActive = peakCount,
      totalExecutions = totalCount,
      currentWaiting = waitingCount,
      availablePermits = availableCount
    )

  /** Reset statistics (useful for testing). */
  def resetStats: IO[Unit] =
    for {
      _ <- peakRef.set(0)
      _ <- totalRef.set(0)
    } yield ()

  private def updatePeak: IO[Unit] =
    for {
      active <- activeRef.get
      _      <- peakRef.update(peak => math.max(peak, active))
    } yield ()
}

object ConcurrencyLimiter {

  /** Create a new concurrency limiter.
    *
    * @param maxConcurrent
    *   Maximum number of concurrent executions
    * @return
    *   A new concurrency limiter
    */
  def apply(maxConcurrent: Int): IO[ConcurrencyLimiter] = {
    require(maxConcurrent > 0, "Max concurrent must be positive")
    for {
      semaphore  <- Semaphore[IO](maxConcurrent.toLong)
      activeRef  <- Ref.of[IO, Long](0L)
      peakRef    <- Ref.of[IO, Long](0L)
      totalRef   <- Ref.of[IO, Long](0L)
      waitingRef <- Ref.of[IO, Long](0L)
    } yield new ConcurrencyLimiter(
      semaphore,
      maxConcurrent,
      activeRef,
      peakRef,
      totalRef,
      waitingRef
    )
  }

  /** Create a concurrency limiter with a single permit (mutex). */
  def mutex: IO[ConcurrencyLimiter] = apply(1)
}

/** Statistics for a concurrency limiter. */
final case class ConcurrencyStats(
    maxConcurrent: Int,
    currentActive: Long,
    peakActive: Long,
    totalExecutions: Long,
    currentWaiting: Long,
    availablePermits: Long
) {

  /** Current utilization (0.0 to 1.0). */
  def utilization: Double = currentActive.toDouble / maxConcurrent

  /** Peak utilization (0.0 to 1.0). */
  def peakUtilization: Double = peakActive.toDouble / maxConcurrent

  override def toString: String =
    s"ConcurrencyStats(active=$currentActive/$maxConcurrent, peak=$peakActive, total=$totalExecutions, waiting=$currentWaiting)"
}
