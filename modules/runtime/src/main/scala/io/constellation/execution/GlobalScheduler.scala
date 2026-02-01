package io.constellation.execution

import cats.effect.{Deferred, IO, Ref, Resource}
import cats.effect.std.{Queue, Semaphore}
import cats.implicits._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.util.concurrent.atomic.AtomicLong
import scala.collection.immutable.TreeSet
import scala.concurrent.duration._

/** Exception thrown when the scheduler queue is full and cannot accept new tasks. */
class QueueFullException(val currentSize: Int, val maxSize: Int)
    extends RuntimeException(s"Scheduler queue is full ($currentSize/$maxSize)")

/** Statistics for the global scheduler.
  *
  * @param activeCount Tasks currently executing
  * @param queuedCount Tasks waiting in the priority queue
  * @param totalSubmitted Total tasks submitted since creation
  * @param totalCompleted Total tasks completed since creation
  * @param highPriorityCompleted Tasks completed with priority >= 75
  * @param lowPriorityCompleted Tasks completed with priority < 25
  * @param starvationPromotions Times a task's effective priority was boosted due to aging
  */
final case class SchedulerStats(
    activeCount: Int,
    queuedCount: Int,
    totalSubmitted: Long,
    totalCompleted: Long,
    highPriorityCompleted: Long,
    lowPriorityCompleted: Long,
    starvationPromotions: Long
)

object SchedulerStats {
  val empty: SchedulerStats = SchedulerStats(0, 0, 0, 0, 0, 0, 0)
}

/** Global priority scheduler for bounded concurrency with priority ordering.
  *
  * Provides a unified interface for task scheduling across all DAG executions.
  * High-priority tasks from any execution run before low-priority tasks.
  *
  * ==Priority Levels==
  *
  * {{{
  * | Level      | Value | Use Case                    |
  * |------------|-------|-----------------------------|
  * | Critical   | 100   | Time-sensitive operations   |
  * | High       | 80    | Important user-facing tasks |
  * | Normal     | 50    | Default                     |
  * | Low        | 20    | Background processing       |
  * | Background | 0     | Non-urgent work             |
  * }}}
  *
  * ==Implementations==
  *
  *   - `GlobalScheduler.unbounded`: Pass-through scheduler (current behavior)
  *   - `GlobalScheduler.bounded`: Bounded concurrency with priority queue
  */
trait GlobalScheduler {

  /** Submit a task with the given priority.
    *
    * The task will be queued and executed when a slot becomes available.
    * Higher priority tasks execute before lower priority tasks.
    *
    * @param priority Task priority (0-100, higher = more important)
    * @param task The IO task to execute
    * @return The task result
    */
  def submit[A](priority: Int, task: IO[A]): IO[A]

  /** Submit a task with default normal priority (50). */
  def submitNormal[A](task: IO[A]): IO[A] = submit(50, task)

  /** Get current scheduler statistics. */
  def stats: IO[SchedulerStats]
}

object GlobalScheduler {

  /** Default priority for tasks without explicit priority. */
  val DefaultPriority: Int = 50

  /** Unbounded scheduler that executes tasks immediately.
    *
    * This is the default scheduler that preserves current behavior.
    * Tasks execute immediately without any concurrency limiting or priority ordering.
    */
  val unbounded: GlobalScheduler = new GlobalScheduler {
    def submit[A](priority: Int, task: IO[A]): IO[A] = task
    def stats: IO[SchedulerStats] = IO.pure(SchedulerStats.empty)
  }

  /** Create a bounded scheduler with priority queue.
    *
    * @param maxConcurrency Maximum number of tasks executing simultaneously
    * @param maxQueueSize Maximum number of tasks waiting in the queue (0 = unlimited)
    * @param starvationTimeout Duration after which low-priority tasks get priority boost
    * @return Resource that manages the scheduler lifecycle
    */
  def bounded(
      maxConcurrency: Int,
      maxQueueSize: Int = 0,
      starvationTimeout: FiniteDuration = 30.seconds
  ): Resource[IO, GlobalScheduler] = {
    require(maxConcurrency > 0, "maxConcurrency must be positive")
    require(maxQueueSize >= 0, "maxQueueSize must be non-negative")

    for {
      scheduler <- Resource.make(
        BoundedGlobalScheduler.create(maxConcurrency, starvationTimeout, maxQueueSize)
      )(_.shutdown)
    } yield scheduler
  }

  /** Create a bounded scheduler for simpler use cases without Resource lifecycle.
    *
    * Note: The scheduler will continue running until the JVM exits.
    * For long-running applications, prefer `bounded` which properly cleans up.
    */
  def boundedUnsafe(
      maxConcurrency: Int,
      maxQueueSize: Int = 0,
      starvationTimeout: FiniteDuration = 30.seconds
  ): IO[GlobalScheduler] = {
    BoundedGlobalScheduler.create(maxConcurrency, starvationTimeout, maxQueueSize)
  }
}

/** Entry in the priority queue waiting for execution. */
private[execution] final case class QueueEntry(
    id: Long,
    priority: Int,
    submittedAt: FiniteDuration,
    gate: Deferred[IO, Unit],
    effectivePriority: Int  // Increases over time to prevent starvation
) {
  /** Calculate current effective priority considering age. */
  def withAging(now: FiniteDuration, boostPerSecond: Int): QueueEntry = {
    val waitTime = now - submittedAt
    val boost = (waitTime.toSeconds * boostPerSecond / 5).toInt  // Boost every 5 seconds
    val newEffective = math.min(priority + boost, 100)
    copy(effectivePriority = newEffective)
  }
}

private[execution] object QueueEntry {
  /** Ordering: higher effective priority first, then earlier submission (FIFO). */
  implicit val ordering: Ordering[QueueEntry] =
    Ordering.by[QueueEntry, (Int, Long)](e => (-e.effectivePriority, e.id))
}

/** Internal state for the bounded scheduler. */
private[execution] final case class SchedulerState(
    queue: TreeSet[QueueEntry],
    activeCount: Int,
    totalSubmitted: Long,
    totalCompleted: Long,
    highPriorityCompleted: Long,
    lowPriorityCompleted: Long,
    starvationPromotions: Long,
    shuttingDown: Boolean
) {
  def enqueue(entry: QueueEntry): SchedulerState =
    copy(queue = queue + entry, totalSubmitted = totalSubmitted + 1)

  def dequeue: (Option[QueueEntry], SchedulerState) =
    queue.headOption match {
      case Some(entry) => (Some(entry), copy(queue = queue - entry, activeCount = activeCount + 1))
      case None        => (None, this)
    }

  def complete(priority: Int): SchedulerState = {
    val isHigh = priority >= 75
    val isLow = priority < 25
    copy(
      activeCount = activeCount - 1,
      totalCompleted = totalCompleted + 1,
      highPriorityCompleted = if (isHigh) highPriorityCompleted + 1 else highPriorityCompleted,
      lowPriorityCompleted = if (isLow) lowPriorityCompleted + 1 else lowPriorityCompleted
    )
  }

  def recordStarvationPromotion: SchedulerState =
    copy(starvationPromotions = starvationPromotions + 1)

  def toStats: SchedulerStats = SchedulerStats(
    activeCount = activeCount,
    queuedCount = queue.size,
    totalSubmitted = totalSubmitted,
    totalCompleted = totalCompleted,
    highPriorityCompleted = highPriorityCompleted,
    lowPriorityCompleted = lowPriorityCompleted,
    starvationPromotions = starvationPromotions
  )
}

private[execution] object SchedulerState {
  val empty: SchedulerState = SchedulerState(
    queue = TreeSet.empty[QueueEntry],
    activeCount = 0,
    totalSubmitted = 0,
    totalCompleted = 0,
    highPriorityCompleted = 0,
    lowPriorityCompleted = 0,
    starvationPromotions = 0,
    shuttingDown = false
  )
}

/** Bounded global scheduler with priority queue and starvation prevention.
  *
  * Uses:
  *   - Semaphore for concurrency limiting
  *   - TreeSet-based priority queue for ordering
  *   - Background fiber for starvation prevention (aging)
  */
private[execution] class BoundedGlobalScheduler private (
    maxConcurrency: Int,
    starvationTimeout: FiniteDuration,
    maxQueueSize: Int,
    stateRef: Ref[IO, SchedulerState],
    semaphore: Semaphore[IO],
    taskIdCounter: AtomicLong,
    agingFiber: cats.effect.Fiber[IO, Throwable, Unit]
) extends GlobalScheduler {

  private val boostPerSecond: Int = 10  // Priority boost per 5 seconds of waiting

  def submit[A](priority: Int, task: IO[A]): IO[A] = {
    for {
      // Check if shutting down
      state <- stateRef.get
      _ <- IO.raiseError(new IllegalStateException("Scheduler is shutting down")).whenA(state.shuttingDown)

      // Check queue capacity
      _ <- IO.raiseError(new QueueFullException(state.queue.size, maxQueueSize))
        .whenA(maxQueueSize > 0 && state.queue.size >= maxQueueSize)

      // Create entry
      now <- IO.realTime
      gate <- Deferred[IO, Unit]
      taskId = taskIdCounter.incrementAndGet()
      clampedPriority = math.max(0, math.min(100, priority))
      entry = QueueEntry(
        id = taskId,
        priority = clampedPriority,
        submittedAt = now,
        gate = gate,
        effectivePriority = clampedPriority
      )

      // Enqueue the entry
      _ <- stateRef.update(_.enqueue(entry))

      // Try to dispatch queued tasks
      _ <- dispatch

      // Wait for our turn
      _ <- gate.get

      // Execute the task with semaphore
      result <- semaphore.permit.use { _ =>
        task.guarantee {
          for {
            _ <- stateRef.update(_.complete(clampedPriority))
            _ <- dispatch  // Try to dispatch next task
          } yield ()
        }
      }
    } yield result
  }

  def stats: IO[SchedulerStats] = stateRef.get.map(_.toStats)

  /** Try to dispatch the highest priority queued task. */
  private def dispatch: IO[Unit] = {
    stateRef.modify { state =>
      if (state.activeCount < maxConcurrency && state.queue.nonEmpty) {
        val (maybeEntry, newState) = state.dequeue
        maybeEntry match {
          case Some(entry) => (newState, Some(entry.gate))
          case None        => (state, None)
        }
      } else {
        (state, None)
      }
    }.flatMap {
      case Some(gate) => gate.complete(()).void
      case None       => IO.unit
    }
  }

  /** Shutdown the scheduler gracefully. */
  def shutdown: IO[Unit] = {
    for {
      _ <- stateRef.update(_.copy(shuttingDown = true))
      _ <- agingFiber.cancel
    } yield ()
  }
}

private[execution] object BoundedGlobalScheduler {
  private val logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("io.constellation.execution.BoundedGlobalScheduler")

  def create(
      maxConcurrency: Int,
      starvationTimeout: FiniteDuration,
      maxQueueSize: Int = 0
  ): IO[BoundedGlobalScheduler] = {
    for {
      stateRef <- Ref.of[IO, SchedulerState](SchedulerState.empty)
      semaphore <- Semaphore[IO](maxConcurrency)
      taskIdCounter = new AtomicLong(0)

      // Start aging fiber for starvation prevention
      agingFiber <- startAgingFiber(stateRef, starvationTimeout).start

    } yield new BoundedGlobalScheduler(
      maxConcurrency = maxConcurrency,
      starvationTimeout = starvationTimeout,
      maxQueueSize = maxQueueSize,
      stateRef = stateRef,
      semaphore = semaphore,
      taskIdCounter = taskIdCounter,
      agingFiber = agingFiber
    )
  }

  /** Background fiber that periodically boosts priority of waiting tasks. */
  private def startAgingFiber(
      stateRef: Ref[IO, SchedulerState],
      starvationTimeout: FiniteDuration
  ): IO[Unit] = {
    val agingInterval = 5.seconds
    val boostPerInterval = 10  // Priority boost per interval

    def loop: IO[Unit] = {
      for {
        _ <- IO.sleep(agingInterval)
        now <- IO.realTime

        // Update effective priorities for waiting tasks
        promotionCount <- stateRef.modify { state =>
          if (state.shuttingDown) {
            (state, 0)
          } else {
            var promotions = 0
            val updatedQueue = state.queue.map { entry =>
              val waitTime = now - entry.submittedAt
              val boost = (waitTime.toSeconds / 5 * boostPerInterval).toInt
              val newEffective = math.min(entry.priority + boost, 100)
              if (newEffective > entry.effectivePriority) {
                promotions += 1
                entry.copy(effectivePriority = newEffective)
              } else {
                entry
              }
            }
            // Rebuild TreeSet to reorder based on new effective priorities
            val reorderedQueue = TreeSet.from(updatedQueue)(QueueEntry.ordering)
            val newState = state.copy(
              queue = reorderedQueue,
              starvationPromotions = state.starvationPromotions + promotions
            )
            (newState, promotions)
          }
        }

        // Continue unless shutting down
        state <- stateRef.get
        _ <- loop.unlessA(state.shuttingDown)
      } yield ()
    }

    loop.handleErrorWith { error =>
      // Log error but don't crash - aging is best-effort
      logger.warn(error)(s"GlobalScheduler aging fiber error: ${error.getMessage}") *>
        IO.sleep(agingInterval) *>
        loop
    }
  }
}
