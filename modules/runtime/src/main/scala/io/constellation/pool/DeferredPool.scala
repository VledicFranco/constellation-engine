package io.constellation.pool

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import cats.effect.{Deferred, IO, Ref}
import cats.implicits.*

/** A pool of pre-allocated Deferred instances for reducing allocation overhead.
  *
  * Deferreds in Cats Effect are one-shot: once completed, they cannot be reset. This pool manages a
  * supply of fresh, uncompleted Deferreds that can be acquired for DAG execution. After execution
  * completes, the pool replenishes itself with fresh Deferreds for subsequent requests.
  *
  * ==Thread Safety==
  *
  * All operations are thread-safe and use Cats Effect's Ref for state management.
  *
  * ==Memory Model==
  *
  * The pool maintains between `initialSize` and `maxSize` Deferreds. When depleted, it creates
  * fresh Deferreds on demand. After execution, calling `replenish` restores the pool to its target
  * size.
  *
  * @param initialSize
  *   Number of Deferreds to pre-allocate on initialization
  * @param maxSize
  *   Maximum pool size (to prevent unbounded growth)
  */
final class DeferredPool private (
    pool: Ref[IO, List[Deferred[IO, Any]]],
    val initialSize: Int,
    val maxSize: Int,
    metrics: DeferredPool.Metrics
) {

  /** Acquire a single Deferred from the pool. If the pool is empty, creates a fresh Deferred.
    */
  def acquire: IO[Deferred[IO, Any]] =
    pool.modify {
      case head :: tail =>
        metrics.recordPoolHit()
        (tail, IO.pure(head))
      case Nil =>
        metrics.recordPoolMiss()
        (Nil, Deferred[IO, Any])
    }.flatten

  /** Acquire multiple Deferreds efficiently. Takes as many as available from the pool, creates the
    * rest.
    */
  def acquireN(n: Int): IO[List[Deferred[IO, Any]]] =
    if n <= 0 then IO.pure(Nil)
    else {
      pool
        .modify { available =>
          val (fromPool, remaining) = available.splitAt(n)
          val needed                = n - fromPool.size
          metrics.recordPoolHits(fromPool.size)
          metrics.recordPoolMisses(needed)
          (remaining, (fromPool, needed))
        }
        .flatMap { case (fromPool, needed) =>
          if needed > 0 then {
            List.fill(needed)(Deferred[IO, Any]).sequence.map(fromPool ++ _)
          } else {
            IO.pure(fromPool)
          }
        }
    }

  /** Replenish the pool with fresh Deferreds after execution.
    *
    * Since completed Deferreds cannot be reused, this creates fresh ones up to the target size (min
    * of count and available capacity).
    */
  def replenish(count: Int): IO[Unit] =
    pool.get.flatMap { current =>
      val currentSize = current.size
      val toAdd       = math.min(count, maxSize - currentSize)
      if toAdd > 0 then {
        List.fill(toAdd)(Deferred[IO, Any]).sequence.flatMap { fresh =>
          pool.update(_ ++ fresh)
        }
      } else IO.unit
    }

  /** Get current pool size (for metrics/debugging).
    */
  def size: IO[Int] = pool.get.map(_.size)

  /** Get pool metrics snapshot.
    */
  def getMetrics: DeferredPool.MetricsSnapshot = metrics.snapshot
}

object DeferredPool {

  /** Create and initialize a DeferredPool.
    */
  def create(initialSize: Int = 100, maxSize: Int = 10000): IO[DeferredPool] =
    for {
      // Pre-allocate initial Deferreds
      initial <- List.fill(initialSize)(Deferred[IO, Any]).sequence
      pool    <- Ref.of[IO, List[Deferred[IO, Any]]](initial)
      metrics = new Metrics()
    } yield new DeferredPool(pool, initialSize, maxSize, metrics)

  /** Create an empty pool (for testing or when pooling is disabled).
    */
  def empty: IO[DeferredPool] =
    for {
      pool <- Ref.of[IO, List[Deferred[IO, Any]]](Nil)
      metrics = new Metrics()
    } yield new DeferredPool(pool, 0, 0, metrics)

  /** Pool metrics for monitoring.
    */
  final class Metrics {
    private val hits          = new AtomicLong(0)
    private val misses        = new AtomicLong(0)
    private val totalAcquires = new AtomicLong(0)

    def recordPoolHit(): Unit = {
      hits.incrementAndGet()
      totalAcquires.incrementAndGet()
    }

    def recordPoolHits(count: Int): Unit = {
      hits.addAndGet(count)
      totalAcquires.addAndGet(count)
    }

    def recordPoolMiss(): Unit = {
      misses.incrementAndGet()
      totalAcquires.incrementAndGet()
    }

    def recordPoolMisses(count: Int): Unit =
      if count > 0 then {
        misses.addAndGet(count)
        totalAcquires.addAndGet(count)
      }

    def snapshot: MetricsSnapshot = MetricsSnapshot(
      hits = hits.get(),
      misses = misses.get(),
      totalAcquires = totalAcquires.get()
    )
  }

  /** Immutable snapshot of pool metrics.
    */
  final case class MetricsSnapshot(
      hits: Long,
      misses: Long,
      totalAcquires: Long
  ) {
    def hitRate: Double = if totalAcquires > 0 then hits.toDouble / totalAcquires else 0.0
  }
}
