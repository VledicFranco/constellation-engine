package io.constellation.execution

import cats.effect.{IO, Ref}
import cats.effect.std.Queue
import cats.implicits.*

import java.util.concurrent.atomic.AtomicLong

/** Priority levels for task scheduling.
  *
  * Higher values indicate higher priority:
  *   - Critical (100): System-critical tasks
  *   - High (75): User-facing, latency-sensitive
  *   - Normal (50): Default priority
  *   - Low (25): Background processing
  *   - Background (0): Best-effort, lowest priority
  *   - Custom(n): User-defined priority value
  */
sealed trait PriorityLevel {
  def value: Int
}

object PriorityLevel {
  case object Critical          extends PriorityLevel { val value = 100 }
  case object High              extends PriorityLevel { val value = 75  }
  case object Normal            extends PriorityLevel { val value = 50  }
  case object Low               extends PriorityLevel { val value = 25  }
  case object Background        extends PriorityLevel { val value = 0   }
  case class Custom(value: Int) extends PriorityLevel

  /** Parse priority level from string. */
  def fromString(s: String): Option[PriorityLevel] = s.toLowerCase match {
    case "critical"   => Some(Critical)
    case "high"       => Some(High)
    case "normal"     => Some(Normal)
    case "low"        => Some(Low)
    case "background" => Some(Background)
    case _ =>
      s.toIntOption.map(Custom(_))
  }

  /** Compare two priority levels. */
  implicit val ordering: Ordering[PriorityLevel] =
    Ordering.by[PriorityLevel, Int](_.value).reverse // Higher value = higher priority
}

/** A prioritized task for scheduling.
  *
  * @param id
  *   Unique task identifier
  * @param priority
  *   Task priority level
  * @param submittedAt
  *   Submission timestamp (for FIFO within same priority)
  * @param task
  *   The task to execute
  */
final case class PrioritizedTask[A](
    id: Long,
    priority: Int,
    submittedAt: Long,
    task: IO[A]
)

object PrioritizedTask {

  /** Ordering: higher priority first, then earlier submission. */
  implicit def ordering[A]: Ordering[PrioritizedTask[A]] =
    Ordering.by[PrioritizedTask[A], (Int, Long)](t => (-t.priority, t.submittedAt))
}

/** Priority-based task scheduler.
  *
  * Provides priority hints for task execution. Higher priority tasks are preferred over lower
  * priority ones.
  *
  * ==Current Implementation==
  *
  * Currently executes tasks immediately with priority recorded for metrics. Future versions may
  * implement actual priority queue scheduling.
  *
  * ==Usage==
  *
  * {{{
  * val scheduler = PriorityScheduler.create.unsafeRunSync()
  *
  * // Submit tasks with different priorities
  * val critical = scheduler.submit(criticalTask, PriorityLevel.Critical)
  * val background = scheduler.submit(backgroundTask, PriorityLevel.Background)
  *
  * // Both run, but critical is prioritized
  * (critical, background).parTupled.unsafeRunSync()
  * }}}
  */
class PriorityScheduler private (
    taskIdCounter: AtomicLong,
    statsRef: Ref[IO, PrioritySchedulerStats]
) {

  /** Submit a task with the given priority.
    *
    * @param task
    *   The task to execute
    * @param priority
    *   The priority level
    * @return
    *   The task result
    */
  def submit[A](task: IO[A], priority: PriorityLevel): IO[A] = {
    val taskId      = taskIdCounter.incrementAndGet()
    val submittedAt = System.currentTimeMillis()

    for {
      // Record submission
      _         <- statsRef.update(_.recordSubmission(priority))
      startTime <- IO.realTime
      // Execute the task
      result <- task.guarantee {
        for {
          endTime <- IO.realTime
          duration = endTime - startTime
          _ <- statsRef.update(_.recordCompletion(priority, duration.toMillis))
        } yield ()
      }
    } yield result
  }

  /** Submit a task with Normal priority. */
  def submit[A](task: IO[A]): IO[A] = submit(task, PriorityLevel.Normal)

  /** Get scheduler statistics. */
  def stats: IO[PrioritySchedulerStats] = statsRef.get

  /** Reset statistics. */
  def resetStats: IO[Unit] = statsRef.set(PrioritySchedulerStats.empty)
}

object PriorityScheduler {

  /** Create a new priority scheduler. */
  def create: IO[PriorityScheduler] =
    for {
      statsRef <- Ref.of[IO, PrioritySchedulerStats](PrioritySchedulerStats.empty)
    } yield new PriorityScheduler(new AtomicLong(0), statsRef)
}

/** Statistics for priority scheduler.
  *
  * @param totalSubmitted
  *   Total tasks submitted
  * @param totalCompleted
  *   Total tasks completed
  * @param byPriority
  *   Per-priority statistics
  */
final case class PrioritySchedulerStats(
    totalSubmitted: Long,
    totalCompleted: Long,
    byPriority: Map[Int, PriorityStats]
) {

  def recordSubmission(priority: PriorityLevel): PrioritySchedulerStats = {
    val priorityStats = byPriority.getOrElse(priority.value, PriorityStats.empty)
    copy(
      totalSubmitted = totalSubmitted + 1,
      byPriority = byPriority + (priority.value -> priorityStats.recordSubmission)
    )
  }

  def recordCompletion(priority: PriorityLevel, durationMs: Long): PrioritySchedulerStats = {
    val priorityStats = byPriority.getOrElse(priority.value, PriorityStats.empty)
    copy(
      totalCompleted = totalCompleted + 1,
      byPriority = byPriority + (priority.value -> priorityStats.recordCompletion(durationMs))
    )
  }

  /** Get stats for a specific priority level. */
  def forPriority(priority: PriorityLevel): Option[PriorityStats] =
    byPriority.get(priority.value)

  /** Get completion rate (0.0 to 1.0). */
  def completionRate: Double =
    if totalSubmitted == 0 then 1.0
    else totalCompleted.toDouble / totalSubmitted

  override def toString: String = {
    val priorities = byPriority.toSeq
      .sortBy(-_._1)
      .map { case (p, s) =>
        s"  priority=$p: ${s.submitted} submitted, ${s.completed} completed, avg=${s.avgDurationMs}ms"
      }
      .mkString("\n")
    s"PrioritySchedulerStats(submitted=$totalSubmitted, completed=$totalCompleted)\n$priorities"
  }
}

object PrioritySchedulerStats {
  val empty: PrioritySchedulerStats = PrioritySchedulerStats(0, 0, Map.empty)
}

/** Per-priority statistics. */
final case class PriorityStats(
    submitted: Long,
    completed: Long,
    totalDurationMs: Long
) {
  def recordSubmission: PriorityStats = copy(submitted = submitted + 1)

  def recordCompletion(durationMs: Long): PriorityStats =
    copy(completed = completed + 1, totalDurationMs = totalDurationMs + durationMs)

  def avgDurationMs: Long =
    if completed == 0 then 0
    else totalDurationMs / completed

  def pendingCount: Long = submitted - completed
}

object PriorityStats {
  val empty: PriorityStats = PriorityStats(0, 0, 0)
}
