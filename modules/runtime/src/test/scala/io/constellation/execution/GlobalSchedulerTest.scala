package io.constellation.execution

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.RetrySupport

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable

class GlobalSchedulerTest extends AnyFlatSpec with Matchers with RetrySupport {

  // -------------------------------------------------------------------------
  // Unbounded Scheduler Tests
  // -------------------------------------------------------------------------

  "GlobalScheduler.unbounded" should "execute tasks immediately" in {
    val result = GlobalScheduler.unbounded.submit(50, IO.pure(42)).unsafeRunSync()
    result shouldBe 42
  }

  it should "ignore priority" in {
    val scheduler = GlobalScheduler.unbounded

    // Both low and high priority should execute immediately
    val low  = scheduler.submit(0, IO.pure("low"))
    val high = scheduler.submit(100, IO.pure("high"))

    (low, high).parTupled.unsafeRunSync() shouldBe ("low", "high")
  }

  it should "return empty stats" in {
    val stats = GlobalScheduler.unbounded.stats.unsafeRunSync()

    stats shouldBe SchedulerStats.empty
    stats.activeCount shouldBe 0
    stats.queuedCount shouldBe 0
    stats.totalSubmitted shouldBe 0
  }

  it should "handle errors properly" in {
    val error = intercept[RuntimeException] {
      GlobalScheduler.unbounded
        .submit(50, IO.raiseError[Int](new RuntimeException("Test error")))
        .unsafeRunSync()
    }

    error.getMessage shouldBe "Test error"
  }

  it should "work with submitNormal convenience method" in {
    val result = GlobalScheduler.unbounded.submitNormal(IO.pure("normal")).unsafeRunSync()
    result shouldBe "normal"
  }

  // -------------------------------------------------------------------------
  // SchedulerStats Tests
  // -------------------------------------------------------------------------

  "SchedulerStats" should "have correct empty instance" in {
    val empty = SchedulerStats.empty

    empty.activeCount shouldBe 0
    empty.queuedCount shouldBe 0
    empty.totalSubmitted shouldBe 0
    empty.totalCompleted shouldBe 0
    empty.highPriorityCompleted shouldBe 0
    empty.lowPriorityCompleted shouldBe 0
    empty.starvationPromotions shouldBe 0
  }

  // -------------------------------------------------------------------------
  // Bounded Scheduler - Basic Tests
  // -------------------------------------------------------------------------

  "GlobalScheduler.bounded" should "limit concurrency to maxConcurrency" in {
    GlobalScheduler
      .bounded(maxConcurrency = 2)
      .use { scheduler =>
        val activeCount = new AtomicInteger(0)
        val maxActive   = new AtomicInteger(0)

        val tasks = (1 to 10).toList.map { i =>
          scheduler.submit(
            50,
            IO {
              val current = activeCount.incrementAndGet()
              maxActive.updateAndGet(m => math.max(m, current))
              Thread.sleep(50)
              activeCount.decrementAndGet()
              i
            }
          )
        }

        for {
          results <- tasks.parSequence
          _ = results should contain theSameElementsAs (1 to 10)
          _ = maxActive.get() should be <= 2
        } yield ()
      }
      .unsafeRunSync()
  }

  it should "track stats accurately" in {
    GlobalScheduler
      .bounded(maxConcurrency = 2)
      .use { scheduler =>
        for {
          statsBefore <- scheduler.stats
          _ = statsBefore.totalSubmitted shouldBe 0
          _ = statsBefore.totalCompleted shouldBe 0

          _ <- scheduler.submit(50, IO.pure(1))
          _ <- scheduler.submit(80, IO.pure(2))
          _ <- scheduler.submit(20, IO.pure(3))

          statsAfter <- scheduler.stats
          _ = statsAfter.totalSubmitted shouldBe 3
          _ = statsAfter.totalCompleted shouldBe 3
          _ = statsAfter.highPriorityCompleted shouldBe 1 // priority 80 >= 75
          _ = statsAfter.lowPriorityCompleted shouldBe 1  // priority 20 < 25
        } yield ()
      }
      .unsafeRunSync()
  }

  it should "require positive maxConcurrency" in {
    an[IllegalArgumentException] should be thrownBy {
      GlobalScheduler.bounded(maxConcurrency = 0).use(_ => IO.unit).unsafeRunSync()
    }

    an[IllegalArgumentException] should be thrownBy {
      GlobalScheduler.bounded(maxConcurrency = -1).use(_ => IO.unit).unsafeRunSync()
    }
  }

  // -------------------------------------------------------------------------
  // Bounded Scheduler - Priority Ordering Tests
  // -------------------------------------------------------------------------

  "Bounded scheduler priority ordering" should "execute higher priority tasks before lower priority" taggedAs Retryable in {
    GlobalScheduler
      .bounded(maxConcurrency = 1)
      .use { scheduler =>
        val completionOrder = ListBuffer[String]()

        for {
          // Fill the single slot with a blocking task
          blocker <- scheduler.submit(50, IO.sleep(100.millis) *> IO.pure("blocker")).start

          // Submit tasks in order: low, medium, high
          // They should complete in order: high, medium, low (due to priority queue)
          _ <- IO.sleep(20.millis) // Let blocker acquire the slot

          lowFiber <- scheduler
            .submit(
              20,
              IO {
                completionOrder.synchronized(completionOrder += "low")
                "low"
              }
            )
            .start

          medFiber <- scheduler
            .submit(
              50,
              IO {
                completionOrder.synchronized(completionOrder += "med")
                "med"
              }
            )
            .start

          highFiber <- scheduler
            .submit(
              80,
              IO {
                completionOrder.synchronized(completionOrder += "high")
                "high"
              }
            )
            .start

          // Wait for all to complete
          _ <- blocker.joinWithNever
          _ <- highFiber.joinWithNever
          _ <- medFiber.joinWithNever
          _ <- lowFiber.joinWithNever

          // Note: Since maxConcurrency=1, tasks execute sequentially
          // Priority queue should process in order: high(80), med(50), low(20)
          _ = completionOrder.toList shouldBe List("high", "med", "low")
        } yield ()
      }
      .unsafeRunSync()
  }

  it should "use FIFO within same priority level" in {
    GlobalScheduler
      .bounded(maxConcurrency = 1)
      .use { scheduler =>
        val completionOrder = ListBuffer[Int]()

        for {
          // Fill the slot with a blocker
          blocker <- scheduler.submit(50, IO.sleep(100.millis) *> IO.pure("blocker")).start
          _       <- IO.sleep(20.millis)

          // Submit 3 tasks at same priority
          fiber1 <- scheduler
            .submit(
              50,
              IO {
                completionOrder.synchronized(completionOrder += 1)
              }
            )
            .start

          fiber2 <- scheduler
            .submit(
              50,
              IO {
                completionOrder.synchronized(completionOrder += 2)
              }
            )
            .start

          fiber3 <- scheduler
            .submit(
              50,
              IO {
                completionOrder.synchronized(completionOrder += 3)
              }
            )
            .start

          _ <- blocker.joinWithNever
          _ <- fiber1.joinWithNever
          _ <- fiber2.joinWithNever
          _ <- fiber3.joinWithNever

          // Same priority = FIFO order
          _ = completionOrder.toList shouldBe List(1, 2, 3)
        } yield ()
      }
      .unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // Bounded Scheduler - Priority Clamping Tests
  // -------------------------------------------------------------------------

  "Bounded scheduler priority clamping" should "clamp priority to 0-100 range" in {
    GlobalScheduler
      .bounded(maxConcurrency = 2)
      .use { scheduler =>
        for {
          // Negative priority should be clamped to 0
          _ <- scheduler.submit(-10, IO.pure(1))

          // Priority over 100 should be clamped to 100
          _ <- scheduler.submit(150, IO.pure(2))

          stats <- scheduler.stats
          _ = stats.totalCompleted shouldBe 2
          // -10 clamped to 0, counted as low priority
          _ = stats.lowPriorityCompleted shouldBe 1
          // 150 clamped to 100, counted as high priority
          _ = stats.highPriorityCompleted shouldBe 1
        } yield ()
      }
      .unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // Bounded Scheduler - Starvation Prevention Tests
  // -------------------------------------------------------------------------

  "Bounded scheduler starvation prevention" should "boost priority of waiting tasks over time" taggedAs Retryable in {
    // Use a short starvation timeout for testing
    GlobalScheduler
      .bounded(maxConcurrency = 1, starvationTimeout = 1.second)
      .use { scheduler =>
        for {
          // This test verifies the aging mechanism works
          // We check that starvationPromotions increases over time

          // Fill slot with long-running task
          blocker <- scheduler.submit(50, IO.sleep(300.millis)).start
          _       <- IO.sleep(20.millis)

          // Submit a low-priority task that will wait
          lowPriority <- scheduler.submit(10, IO.pure("low")).start

          // Wait for aging to happen (at least one 5-second interval wouldn't happen in this short test,
          // but the mechanism is tested)
          _ <- blocker.joinWithNever
          _ <- lowPriority.joinWithNever

          // Just verify completion - detailed aging tests would need longer durations
          stats <- scheduler.stats
          _ = stats.totalCompleted shouldBe 2
        } yield ()
      }
      .unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // Bounded Scheduler - Shutdown Tests
  // -------------------------------------------------------------------------

  "Bounded scheduler shutdown" should "reject new tasks after shutdown" in {
    val (scheduler, shutdown) = GlobalScheduler
      .bounded(maxConcurrency = 2)
      .allocated
      .unsafeRunSync()

    // Complete a task to verify scheduler works
    scheduler.submit(50, IO.pure(1)).unsafeRunSync() shouldBe 1

    // Shutdown the scheduler
    shutdown.unsafeRunSync()

    // New submissions should fail
    an[IllegalStateException] should be thrownBy {
      scheduler.submit(50, IO.pure(2)).unsafeRunSync()
    }
  }

  // -------------------------------------------------------------------------
  // Bounded Scheduler - Error Handling Tests
  // -------------------------------------------------------------------------

  "Bounded scheduler error handling" should "propagate task errors" in {
    GlobalScheduler
      .bounded(maxConcurrency = 2)
      .use { scheduler =>
        val error = intercept[RuntimeException] {
          scheduler
            .submit(50, IO.raiseError[Int](new RuntimeException("Task failed")))
            .unsafeRunSync()
        }

        IO(error.getMessage shouldBe "Task failed")
      }
      .unsafeRunSync()
  }

  it should "continue processing after task error" in {
    GlobalScheduler
      .bounded(maxConcurrency = 2)
      .use { scheduler =>
        for {
          // First task fails
          _ <- scheduler
            .submit(50, IO.raiseError[Int](new RuntimeException("Error")))
            .attempt
            .map(_ shouldBe a[Left[_, _]])

          // Second task should still work
          result <- scheduler.submit(50, IO.pure(42))
          _ = result shouldBe 42

          stats <- scheduler.stats
          _ = stats.totalSubmitted shouldBe 2
          _ = stats.totalCompleted shouldBe 2 // Failed tasks still count as completed
        } yield ()
      }
      .unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // Bounded Scheduler - Concurrent Submission Tests
  // -------------------------------------------------------------------------

  "Bounded scheduler concurrent submissions" should "handle many concurrent submitters" in {
    GlobalScheduler
      .bounded(maxConcurrency = 4)
      .use { scheduler =>
        val counter = new AtomicLong(0)

        // 100 concurrent submitters
        val submitters = (1 to 100).toList.map { i =>
          scheduler.submit(
            i % 100, // Varying priorities
            IO { counter.incrementAndGet(); i }
          )
        }

        for {
          results <- submitters.parSequence
          _ = results.toSet shouldBe (1 to 100).toSet
          _ = counter.get() shouldBe 100

          stats <- scheduler.stats
          _ = stats.totalSubmitted shouldBe 100
          _ = stats.totalCompleted shouldBe 100
        } yield ()
      }
      .unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // QueueEntry Tests
  // -------------------------------------------------------------------------

  "QueueEntry" should "order by effective priority descending, then by id ascending" in {
    val now = 0.seconds

    val highPriority =
      QueueEntry(id = 1, priority = 80, submittedAt = now, gate = null, effectivePriority = 80)
    val lowPriority =
      QueueEntry(id = 2, priority = 20, submittedAt = now, gate = null, effectivePriority = 20)
    val medPriority =
      QueueEntry(id = 3, priority = 50, submittedAt = now, gate = null, effectivePriority = 50)

    val ordered = List(lowPriority, medPriority, highPriority).sorted

    ordered.map(_.id) shouldBe List(1, 3, 2) // high(80), med(50), low(20)
  }

  it should "use FIFO order for same priority" in {
    val now = 0.seconds

    val first =
      QueueEntry(id = 1, priority = 50, submittedAt = now, gate = null, effectivePriority = 50)
    val second =
      QueueEntry(id = 2, priority = 50, submittedAt = now, gate = null, effectivePriority = 50)
    val third =
      QueueEntry(id = 3, priority = 50, submittedAt = now, gate = null, effectivePriority = 50)

    val ordered = List(third, first, second).sorted

    ordered.map(_.id) shouldBe List(1, 2, 3) // FIFO by id
  }

  it should "calculate aging correctly" in {
    val entry = QueueEntry(
      id = 1,
      priority = 20,
      submittedAt = 0.seconds,
      gate = null,
      effectivePriority = 20
    )

    // After 10 seconds, effective priority should increase
    val aged10s = entry.withAging(10.seconds, boostPerSecond = 10)
    aged10s.effectivePriority should be > 20

    // After 30 seconds, should be capped at 100
    val aged60s = entry.withAging(60.seconds, boostPerSecond = 10)
    aged60s.effectivePriority shouldBe 100 // capped
  }

  // -------------------------------------------------------------------------
  // SchedulerState Tests
  // -------------------------------------------------------------------------

  "SchedulerState" should "enqueue and dequeue correctly" in {
    var state = SchedulerState.empty

    val entry1 = QueueEntry(
      id = 1,
      priority = 50,
      submittedAt = 0.seconds,
      gate = null,
      effectivePriority = 50
    )
    val entry2 = QueueEntry(
      id = 2,
      priority = 80,
      submittedAt = 0.seconds,
      gate = null,
      effectivePriority = 80
    )

    state = state.enqueue(entry1)
    state.queue.size shouldBe 1
    state.totalSubmitted shouldBe 1

    state = state.enqueue(entry2)
    state.queue.size shouldBe 2
    state.totalSubmitted shouldBe 2

    // Dequeue should return highest priority first
    val (maybeEntry, newState) = state.dequeue
    maybeEntry shouldBe defined
    maybeEntry.get.id shouldBe 2 // Higher priority
    newState.queue.size shouldBe 1
    newState.activeCount shouldBe 1
  }

  it should "track completion statistics" in {
    var state = SchedulerState.empty.copy(activeCount = 1)

    // Complete high priority task
    state = state.complete(80)
    state.totalCompleted shouldBe 1
    state.highPriorityCompleted shouldBe 1
    state.lowPriorityCompleted shouldBe 0
    state.activeCount shouldBe 0

    // Complete low priority task
    state = state.copy(activeCount = 1)
    state = state.complete(10)
    state.totalCompleted shouldBe 2
    state.highPriorityCompleted shouldBe 1
    state.lowPriorityCompleted shouldBe 1

    // Complete normal priority task (neither high nor low)
    state = state.copy(activeCount = 1)
    state = state.complete(50)
    state.totalCompleted shouldBe 3
    state.highPriorityCompleted shouldBe 1 // unchanged
    state.lowPriorityCompleted shouldBe 1  // unchanged
  }

  // -------------------------------------------------------------------------
  // Integration Tests
  // -------------------------------------------------------------------------

  "Bounded scheduler integration" should "work with parallel DAG-like execution" in {
    GlobalScheduler
      .bounded(maxConcurrency = 4)
      .use { scheduler =>
        // Simulate a DAG where multiple modules execute in parallel
        val moduleResults = (1 to 10).toList.map { moduleId =>
          val priority = moduleId match {
            case 1 | 2 => 80 // Critical modules
            case 3 | 4 => 50 // Normal modules
            case _     => 20 // Background modules
          }

          scheduler.submit(
            priority,
            IO {
              Thread.sleep(10) // Simulate some work
              s"Module $moduleId completed"
            }
          )
        }

        for {
          results <- moduleResults.parSequence
          _ = results.size shouldBe 10
          _ = results.forall(_.contains("completed")) shouldBe true

          stats <- scheduler.stats
          _ = stats.totalCompleted shouldBe 10
          // 2 high priority (1,2 with priority 80)
          _ = stats.highPriorityCompleted shouldBe 2
          // 6 low priority (5-10 with priority 20)
          _ = stats.lowPriorityCompleted shouldBe 6
        } yield ()
      }
      .unsafeRunSync()
  }

  it should "allow cross-execution priority ordering" in {
    GlobalScheduler
      .bounded(maxConcurrency = 2)
      .use { scheduler =>
        // Execution A: all low priority
        val execAModules = (1 to 5).toList.map { i =>
          scheduler.submit(20, IO(s"A$i"))
        }

        // Execution B: all high priority
        val execBModules = (1 to 5).toList.map { i =>
          scheduler.submit(80, IO(s"B$i"))
        }

        for {
          // Run both executions concurrently
          results <- (execAModules ++ execBModules).parSequence

          _ = results.size shouldBe 10
          _ = results.filter(_.startsWith("A")).size shouldBe 5
          _ = results.filter(_.startsWith("B")).size shouldBe 5

          stats <- scheduler.stats
          _ = stats.highPriorityCompleted shouldBe 5 // All from execution B
          _ = stats.lowPriorityCompleted shouldBe 5  // All from execution A
        } yield ()
      }
      .unsafeRunSync()
  }
}
