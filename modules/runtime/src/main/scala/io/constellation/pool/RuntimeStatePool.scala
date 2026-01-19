package io.constellation.pool

import cats.Eval
import cats.effect.{IO, Ref}
import io.constellation.{CValue, DagSpec, Module}

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

/**
 * A pool of reusable runtime state containers.
 *
 * Each execution of a DAG requires mutable state to track module status and
 * data values. This pool maintains pre-allocated containers that can be
 * acquired, used for execution, and then released back to the pool.
 *
 * == Thread Safety ==
 *
 * Pool access is thread-safe via Cats Effect Ref. Each acquired PooledState
 * is exclusively owned by its execution and should not be shared.
 *
 * == Lifecycle ==
 *
 * 1. `acquire` - Get a state container from the pool (or create new if empty)
 * 2. Use the container during DAG execution
 * 3. `release` - Clear and return the container to the pool
 *
 * @param maxSize Maximum number of state containers to keep in the pool
 */
final class RuntimeStatePool private (
  pool: Ref[IO, List[RuntimeStatePool.PooledState]],
  val maxSize: Int,
  metrics: RuntimeStatePool.Metrics
) {

  /**
   * Acquire a state container from the pool.
   * Creates a new one if the pool is empty.
   */
  def acquire: IO[RuntimeStatePool.PooledState] = {
    pool.modify {
      case head :: tail =>
        metrics.recordPoolHit()
        (tail, IO.pure(head))
      case Nil =>
        metrics.recordPoolMiss()
        (Nil, IO.pure(RuntimeStatePool.PooledState.create()))
    }.flatten.flatMap { state =>
      IO.delay {
        state.markInUse()
        state
      }
    }
  }

  /**
   * Release a state container back to the pool.
   * The container is cleared before being added to the pool.
   */
  def release(state: RuntimeStatePool.PooledState): IO[Unit] = {
    IO.delay(state.reset()) >> pool.update { current =>
      if (current.size < maxSize) {
        state :: current
      } else {
        // Pool is full, discard the state
        current
      }
    }
  }

  /**
   * Bracket-style acquire/release for safe resource management.
   */
  def use[A](f: RuntimeStatePool.PooledState => IO[A]): IO[A] = {
    acquire.flatMap { state =>
      f(state).guarantee(release(state))
    }
  }

  /**
   * Get current pool size (for metrics/debugging).
   */
  def size: IO[Int] = pool.get.map(_.size)

  /**
   * Get pool metrics snapshot.
   */
  def getMetrics: RuntimeStatePool.MetricsSnapshot = metrics.snapshot
}

object RuntimeStatePool {

  /**
   * Create and initialize a RuntimeStatePool.
   */
  def create(initialSize: Int = 10, maxSize: Int = 50): IO[RuntimeStatePool] = {
    for {
      initial <- IO.delay(List.fill(initialSize)(PooledState.create()))
      pool <- Ref.of[IO, List[PooledState]](initial)
      metrics = new Metrics()
    } yield new RuntimeStatePool(pool, maxSize, metrics)
  }

  /**
   * Create an empty pool (for testing or when pooling is disabled).
   */
  def empty: IO[RuntimeStatePool] = {
    for {
      pool <- Ref.of[IO, List[PooledState]](Nil)
      metrics = new Metrics()
    } yield new RuntimeStatePool(pool, 0, metrics)
  }

  /**
   * Mutable state container that can be reused between executions.
   *
   * Uses mutable collections internally for performance, but access
   * is controlled through the pool's acquire/release mechanism.
   */
  final class PooledState private (
    val moduleStatus: mutable.HashMap[UUID, Eval[Module.Status]],
    val data: mutable.HashMap[UUID, Eval[CValue]],
    @volatile private var _inUse: Boolean,
    @volatile private var _processUuid: UUID,
    @volatile private var _dag: DagSpec,
    @volatile private var _latency: Option[FiniteDuration]
  ) {

    def processUuid: UUID = _processUuid
    def dag: DagSpec = _dag
    def latency: Option[FiniteDuration] = _latency
    def inUse: Boolean = _inUse

    def setProcessUuid(uuid: UUID): Unit = _processUuid = uuid
    def setDag(dag: DagSpec): Unit = _dag = dag
    def setLatency(latency: Option[FiniteDuration]): Unit = _latency = latency

    def markInUse(): Unit = _inUse = true

    /**
     * Reset the state for reuse.
     * Called when releasing back to the pool.
     */
    def reset(): Unit = {
      moduleStatus.clear()
      data.clear()
      _inUse = false
      _processUuid = null
      _dag = null
      _latency = None
    }

    /**
     * Initialize for a new execution.
     */
    def initialize(dag: DagSpec): Unit = {
      _processUuid = UUID.randomUUID()
      _dag = dag
      _latency = None
      // Pre-populate module status as Unfired
      dag.modules.keys.foreach { moduleId =>
        moduleStatus.put(moduleId, Eval.later(Module.Status.Unfired))
      }
    }

    /**
     * Convert to immutable Runtime.State for final result.
     */
    def toImmutableState: RuntimeStateSnapshot = RuntimeStateSnapshot(
      processUuid = _processUuid,
      dag = _dag,
      moduleStatus = moduleStatus.toMap,
      data = data.toMap,
      latency = _latency
    )
  }

  object PooledState {
    def create(): PooledState = new PooledState(
      moduleStatus = mutable.HashMap.empty,
      data = mutable.HashMap.empty,
      _inUse = false,
      _processUuid = null,
      _dag = null,
      _latency = None
    )
  }

  /**
   * Immutable snapshot of runtime state (for returning to callers).
   */
  final case class RuntimeStateSnapshot(
    processUuid: UUID,
    dag: DagSpec,
    moduleStatus: Map[UUID, Eval[Module.Status]],
    data: Map[UUID, Eval[CValue]],
    latency: Option[FiniteDuration]
  )

  /**
   * Pool metrics for monitoring.
   */
  final class Metrics {
    private val hits = new AtomicLong(0)
    private val misses = new AtomicLong(0)
    private val totalAcquires = new AtomicLong(0)

    def recordPoolHit(): Unit = {
      hits.incrementAndGet()
      totalAcquires.incrementAndGet()
    }

    def recordPoolMiss(): Unit = {
      misses.incrementAndGet()
      totalAcquires.incrementAndGet()
    }

    def snapshot: MetricsSnapshot = MetricsSnapshot(
      hits = hits.get(),
      misses = misses.get(),
      totalAcquires = totalAcquires.get()
    )
  }

  /**
   * Immutable snapshot of pool metrics.
   */
  final case class MetricsSnapshot(
    hits: Long,
    misses: Long,
    totalAcquires: Long
  ) {
    def hitRate: Double = if (totalAcquires > 0) hits.toDouble / totalAcquires else 0.0
  }
}
