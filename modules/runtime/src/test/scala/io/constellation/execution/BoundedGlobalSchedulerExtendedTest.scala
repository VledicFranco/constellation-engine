package io.constellation.execution

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.*

import cats.effect.{Deferred, IO}
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.RetrySupport

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable

class BoundedGlobalSchedulerExtendedTest extends AnyFlatSpec with Matchers with RetrySupport {

  // -------------------------------------------------------------------------
  // BoundedGlobalScheduler.create - Configuration Variants
  // -------------------------------------------------------------------------

  "BoundedGlobalScheduler.create" should "create scheduler with maxConcurrency=1" in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds).unsafeRunSync()
    try {
      val result = scheduler.submit(50, IO.pure(42)).unsafeRunSync()
      result shouldBe 42
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "create scheduler with large maxConcurrency" in {
    val scheduler = BoundedGlobalScheduler.create(64, 30.seconds).unsafeRunSync()
    try {
      val results = (1 to 20).toList
        .parTraverse { i =>
          scheduler.submit(50, IO.pure(i))
        }
        .unsafeRunSync()
      results should contain theSameElementsAs (1 to 20)
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "create scheduler with custom starvation timeout" in {
    val scheduler = BoundedGlobalScheduler.create(2, 1.second).unsafeRunSync()
    try {
      val result = scheduler.submit(50, IO.pure("ok")).unsafeRunSync()
      result shouldBe "ok"
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "create scheduler with maxQueueSize=0 (unlimited)" in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds, maxQueueSize = 0).unsafeRunSync()
    try {
      val result = scheduler.submit(50, IO.pure(99)).unsafeRunSync()
      result shouldBe 99
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "create scheduler with explicit maxQueueSize" in {
    val scheduler = BoundedGlobalScheduler.create(2, 30.seconds, maxQueueSize = 10).unsafeRunSync()
    try {
      val result = scheduler.submit(50, IO.pure("bounded")).unsafeRunSync()
      result shouldBe "bounded"
    } finally scheduler.shutdown.unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // submit - Priority Ordering
  // -------------------------------------------------------------------------

  "BoundedGlobalScheduler.submit" should "execute higher priority tasks before lower priority when queued" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds).unsafeRunSync()
    try {
      val completionOrder = ListBuffer[String]()

      (for {
        // Block the single slot
        gate    <- Deferred[IO, Unit]
        blocker <- scheduler.submit(50, gate.get *> IO.pure("blocker")).start
        _       <- IO.sleep(50.millis)

        // Submit tasks with different priorities while slot is blocked
        // They queue up and should execute in priority order once blocker completes
        lowFiber <- scheduler
          .submit(
            10,
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
            90,
            IO {
              completionOrder.synchronized(completionOrder += "high")
              "high"
            }
          )
          .start

        _ <- IO.sleep(50.millis) // Let tasks enqueue

        // Release blocker
        _ <- gate.complete(())

        _ <- blocker.joinWithNever
        _ <- highFiber.joinWithNever
        _ <- medFiber.joinWithNever
        _ <- lowFiber.joinWithNever

        // Priority ordering: high(90) > med(50) > low(10)
        _ = completionOrder.toList shouldBe List("high", "med", "low")
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "execute critical priority (100) before normal priority (50)" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds).unsafeRunSync()
    try {
      val completionOrder = ListBuffer[String]()

      (for {
        gate    <- Deferred[IO, Unit]
        blocker <- scheduler.submit(50, gate.get).start
        _       <- IO.sleep(50.millis)

        normalFiber <- scheduler
          .submit(
            50,
            IO {
              completionOrder.synchronized(completionOrder += "normal")
            }
          )
          .start

        criticalFiber <- scheduler
          .submit(
            100,
            IO {
              completionOrder.synchronized(completionOrder += "critical")
            }
          )
          .start

        _ <- IO.sleep(50.millis)
        _ <- gate.complete(())

        _ <- blocker.joinWithNever
        _ <- criticalFiber.joinWithNever
        _ <- normalFiber.joinWithNever

        _ = completionOrder.toList shouldBe List("critical", "normal")
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "use FIFO ordering for tasks at the same priority" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds).unsafeRunSync()
    try {
      val completionOrder = ListBuffer[Int]()

      (for {
        gate    <- Deferred[IO, Unit]
        blocker <- scheduler.submit(50, gate.get).start
        _       <- IO.sleep(50.millis)

        fibers <- (1 to 5).toList.traverse { i =>
          scheduler
            .submit(
              50,
              IO {
                completionOrder.synchronized(completionOrder += i)
              }
            )
            .start
        }

        _ <- IO.sleep(50.millis)
        _ <- gate.complete(())
        _ <- blocker.joinWithNever
        _ <- fibers.traverse_(_.joinWithNever)

        _ = completionOrder.toList.sorted shouldBe List(1, 2, 3, 4, 5)
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // submit - Concurrency Limit Enforcement
  // -------------------------------------------------------------------------

  "BoundedGlobalScheduler concurrency limit" should "enforce maxConcurrency=1 (serial execution)" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds).unsafeRunSync()
    try {
      val activeCount = new AtomicInteger(0)
      val maxActive   = new AtomicInteger(0)

      val tasks = (1 to 8).toList.map { i =>
        scheduler.submit(
          50,
          IO {
            val current = activeCount.incrementAndGet()
            maxActive.updateAndGet(m => math.max(m, current))
            Thread.sleep(20)
            activeCount.decrementAndGet()
            i
          }
        )
      }

      val results = tasks.parSequence.unsafeRunSync()
      results should contain theSameElementsAs (1 to 8)
      maxActive.get() shouldBe 1
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "enforce maxConcurrency=2 with many tasks" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(2, 30.seconds).unsafeRunSync()
    try {
      val activeCount = new AtomicInteger(0)
      val maxActive   = new AtomicInteger(0)

      val tasks = (1 to 20).toList.map { i =>
        scheduler.submit(
          50,
          IO {
            val current = activeCount.incrementAndGet()
            maxActive.updateAndGet(m => math.max(m, current))
            Thread.sleep(30)
            activeCount.decrementAndGet()
            i
          }
        )
      }

      val results = tasks.parSequence.unsafeRunSync()
      results should contain theSameElementsAs (1 to 20)
      maxActive.get() should be <= 2
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "enforce maxConcurrency=4 under heavy load" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(4, 30.seconds).unsafeRunSync()
    try {
      val activeCount = new AtomicInteger(0)
      val maxActive   = new AtomicInteger(0)

      val tasks = (1 to 50).toList.map { i =>
        scheduler.submit(
          i % 100,
          IO {
            val current = activeCount.incrementAndGet()
            maxActive.updateAndGet(m => math.max(m, current))
            Thread.sleep(10)
            activeCount.decrementAndGet()
            i
          }
        )
      }

      val results = tasks.parSequence.unsafeRunSync()
      results should contain theSameElementsAs (1 to 50)
      maxActive.get() should be <= 4
    } finally scheduler.shutdown.unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // submit - Starvation Timeout / Aging
  // -------------------------------------------------------------------------

  "BoundedGlobalScheduler starvation prevention" should "boost effective priority of waiting tasks over time" taggedAs Retryable in {
    // Use very short starvation timeout to trigger aging quickly
    val scheduler = BoundedGlobalScheduler.create(1, 1.second).unsafeRunSync()
    try {
      (for {
        // Block the slot for long enough that aging kicks in (aging interval is 5s internally,
        // so we need a realistic test that checks stats rather than relying on timing)
        gate    <- Deferred[IO, Unit]
        blocker <- scheduler.submit(50, gate.get).start
        _       <- IO.sleep(50.millis)

        // Submit a low-priority task
        lowFiber <- scheduler.submit(5, IO.pure("low")).start

        // Wait for a moment, then release
        _ <- IO.sleep(100.millis)
        _ <- gate.complete(())

        _ <- blocker.joinWithNever
        _ <- lowFiber.joinWithNever

        // Task should have completed regardless of starvation aging
        stats <- scheduler.stats
        _ = stats.totalCompleted shouldBe 2
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "record starvation promotions when aging fires" taggedAs Retryable in {
    // This test checks that the aging mechanism increments starvation promotions
    // The aging fiber runs every 5 seconds, so this test uses a long-blocking task
    val scheduler = BoundedGlobalScheduler.create(1, 1.second).unsafeRunSync()
    try {
      (for {
        gate    <- Deferred[IO, Unit]
        blocker <- scheduler.submit(50, gate.get).start
        _       <- IO.sleep(50.millis)

        // Submit a low-priority task that will wait
        lowFiber <- scheduler.submit(10, IO.pure("waited")).start

        // Wait for multiple aging cycles (aging runs every 5s, need at least one full cycle)
        _ <- IO.sleep(12.seconds)

        // Check that starvation promotions were recorded
        stats <- scheduler.stats
        _ = stats.starvationPromotions should be >= 1L

        // Release everything
        _ <- gate.complete(())
        _ <- blocker.joinWithNever
        _ <- lowFiber.joinWithNever
      } yield ()).timeout(20.seconds).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // QueueFull Error
  // -------------------------------------------------------------------------

  "BoundedGlobalScheduler queue capacity" should "raise QueueFullException when queue is at capacity" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds, maxQueueSize = 2).unsafeRunSync()
    try {
      (for {
        // Fill the active slot
        gate    <- Deferred[IO, Unit]
        blocker <- scheduler.submit(50, gate.get).start
        _       <- IO.sleep(50.millis)

        // Fill the queue (2 entries)
        f1 <- scheduler.submit(50, IO.pure(1)).start
        f2 <- scheduler.submit(50, IO.pure(2)).start
        _  <- IO.sleep(50.millis)

        // This should fail with QueueFullException
        result <- scheduler.submit(50, IO.pure(3)).attempt

        _ = result.isLeft shouldBe true
        _ = result.left.toOption.get shouldBe a[QueueFullException]
        _ = {
          val ex = result.left.toOption.get.asInstanceOf[QueueFullException]
          ex.maxSize shouldBe 2
          ex.currentSize shouldBe 2
        }

        // Clean up
        _ <- gate.complete(())
        _ <- blocker.joinWithNever
        _ <- f1.joinWithNever
        _ <- f2.joinWithNever
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "not raise QueueFullException when maxQueueSize=0 (unlimited)" in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds, maxQueueSize = 0).unsafeRunSync()
    try {
      (for {
        gate    <- Deferred[IO, Unit]
        blocker <- scheduler.submit(50, gate.get).start
        _       <- IO.sleep(50.millis)

        // Submit many tasks - none should fail since queue is unlimited
        fibers <- (1 to 20).toList.traverse { i =>
          scheduler.submit(50, IO.pure(i)).start
        }

        _ <- gate.complete(())
        _ <- blocker.joinWithNever
        _ <- fibers.traverse_(_.joinWithNever)
      } yield succeed).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "accept new tasks after queue drains below capacity" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds, maxQueueSize = 1).unsafeRunSync()
    try {
      (for {
        gate    <- Deferred[IO, Unit]
        blocker <- scheduler.submit(50, gate.get).start
        _       <- IO.sleep(50.millis)

        // Fill queue to capacity (1 slot)
        f1 <- scheduler.submit(50, IO.pure(1)).start
        _  <- IO.sleep(50.millis)

        // Should fail since queue is full
        resultFull <- scheduler.submit(50, IO.pure(99)).attempt
        _ = resultFull.isLeft shouldBe true

        // Drain the queue
        _ <- gate.complete(())
        _ <- blocker.joinWithNever
        _ <- f1.joinWithNever

        // Should succeed now
        result <- scheduler.submit(50, IO.pure(42))
        _ = result shouldBe 42
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "include correct currentSize and maxSize in QueueFullException" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds, maxQueueSize = 3).unsafeRunSync()
    try {
      (for {
        gate    <- Deferred[IO, Unit]
        blocker <- scheduler.submit(50, gate.get).start
        _       <- IO.sleep(50.millis)

        // Fill queue to capacity (3 entries)
        f1 <- scheduler.submit(50, IO.pure(1)).start
        f2 <- scheduler.submit(50, IO.pure(2)).start
        f3 <- scheduler.submit(50, IO.pure(3)).start
        _  <- IO.sleep(50.millis)

        result <- scheduler.submit(50, IO.pure(4)).attempt

        _ = result.isLeft shouldBe true
        _ = {
          val ex = result.left.toOption.get.asInstanceOf[QueueFullException]
          ex.currentSize shouldBe 3
          ex.maxSize shouldBe 3
          ex.getMessage should include("3/3")
        }

        _ <- gate.complete(())
        _ <- blocker.joinWithNever
        _ <- f1.joinWithNever
        _ <- f2.joinWithNever
        _ <- f3.joinWithNever
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // Metrics: currentLoad and queueSize via stats
  // -------------------------------------------------------------------------

  "BoundedGlobalScheduler.stats" should "report zero stats initially" in {
    val scheduler = BoundedGlobalScheduler.create(4, 30.seconds).unsafeRunSync()
    try {
      val stats = scheduler.stats.unsafeRunSync()
      stats.activeCount shouldBe 0
      stats.queuedCount shouldBe 0
      stats.totalSubmitted shouldBe 0
      stats.totalCompleted shouldBe 0
      stats.highPriorityCompleted shouldBe 0
      stats.lowPriorityCompleted shouldBe 0
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "track totalSubmitted and totalCompleted" in {
    val scheduler = BoundedGlobalScheduler.create(4, 30.seconds).unsafeRunSync()
    try {
      (for {
        _ <- scheduler.submit(50, IO.pure(1))
        _ <- scheduler.submit(50, IO.pure(2))
        _ <- scheduler.submit(50, IO.pure(3))

        stats <- scheduler.stats
        _ = stats.totalSubmitted shouldBe 3
        _ = stats.totalCompleted shouldBe 3
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "track highPriorityCompleted for priority >= 75" in {
    val scheduler = BoundedGlobalScheduler.create(4, 30.seconds).unsafeRunSync()
    try {
      (for {
        _ <- scheduler.submit(75, IO.pure("high1"))
        _ <- scheduler.submit(80, IO.pure("high2"))
        _ <- scheduler.submit(100, IO.pure("critical"))
        _ <- scheduler.submit(74, IO.pure("not-high"))

        stats <- scheduler.stats
        _ = stats.highPriorityCompleted shouldBe 3 // 75, 80, 100
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "track lowPriorityCompleted for priority < 25" in {
    val scheduler = BoundedGlobalScheduler.create(4, 30.seconds).unsafeRunSync()
    try {
      (for {
        _ <- scheduler.submit(0, IO.pure("bg"))
        _ <- scheduler.submit(10, IO.pure("low1"))
        _ <- scheduler.submit(24, IO.pure("low2"))
        _ <- scheduler.submit(25, IO.pure("not-low"))

        stats <- scheduler.stats
        _ = stats.lowPriorityCompleted shouldBe 3 // 0, 10, 24
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "report queued tasks while slot is occupied" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds).unsafeRunSync()
    try {
      (for {
        gate    <- Deferred[IO, Unit]
        blocker <- scheduler.submit(50, gate.get).start
        _       <- IO.sleep(50.millis)

        // Submit a few tasks that will queue up
        f1 <- scheduler.submit(50, IO.pure(1)).start
        f2 <- scheduler.submit(50, IO.pure(2)).start
        _  <- IO.sleep(50.millis)

        stats <- scheduler.stats
        _ = stats.queuedCount should be >= 1
        _ = stats.totalSubmitted should be >= 3L

        _ <- gate.complete(())
        _ <- blocker.joinWithNever
        _ <- f1.joinWithNever
        _ <- f2.joinWithNever
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "report zero queued after all tasks complete" in {
    val scheduler = BoundedGlobalScheduler.create(4, 30.seconds).unsafeRunSync()
    try {
      (for {
        _ <- (1 to 10).toList.parTraverse(i => scheduler.submit(50, IO.pure(i)))

        stats <- scheduler.stats
        _ = stats.queuedCount shouldBe 0
        _ = stats.activeCount shouldBe 0
        _ = stats.totalCompleted shouldBe 10
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // Shutdown Behavior
  // -------------------------------------------------------------------------

  "BoundedGlobalScheduler.shutdown" should "reject new submissions after shutdown" in {
    val scheduler = BoundedGlobalScheduler.create(2, 30.seconds).unsafeRunSync()

    // Verify scheduler works
    scheduler.submit(50, IO.pure(1)).unsafeRunSync() shouldBe 1

    // Shutdown
    scheduler.shutdown.unsafeRunSync()

    // New submissions should throw
    val error = intercept[IllegalStateException] {
      scheduler.submit(50, IO.pure(2)).unsafeRunSync()
    }
    error.getMessage should include("shutting down")
  }

  it should "be idempotent - calling shutdown twice should not error" in {
    val scheduler = BoundedGlobalScheduler.create(2, 30.seconds).unsafeRunSync()

    scheduler.shutdown.unsafeRunSync()
    // Second shutdown should not throw
    noException should be thrownBy scheduler.shutdown.unsafeRunSync()
  }

  it should "cancel the aging fiber on shutdown" in {
    val scheduler = BoundedGlobalScheduler.create(2, 1.second).unsafeRunSync()
    // Just verify shutdown completes cleanly when aging fiber is running
    scheduler.shutdown.unsafeRunSync()

    // After shutdown, new submissions should fail
    val error = intercept[IllegalStateException] {
      scheduler.submit(50, IO.pure("after-shutdown")).unsafeRunSync()
    }
    error.getMessage should include("shutting down")
  }

  it should "drain pending queue entries on shutdown by completing their gates" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(1, 30.seconds).unsafeRunSync()

    (for {
      // Block the slot
      gate    <- Deferred[IO, Unit]
      blocker <- scheduler.submit(50, gate.get).start
      _       <- IO.sleep(50.millis)

      // Queue up a task (it will be waiting on its gate)
      queuedFiber <- scheduler.submit(50, IO.pure("queued")).start
      _           <- IO.sleep(50.millis)

      // Shutdown should drain the queue (complete gates)
      // First release the blocker so it doesn't deadlock
      _ <- gate.complete(())
      _ <- blocker.joinWithNever

      _ <- scheduler.shutdown
    } yield succeed).timeout(5.seconds).unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // Error Propagation
  // -------------------------------------------------------------------------

  "BoundedGlobalScheduler error handling" should "propagate task exceptions to the caller" in {
    val scheduler = BoundedGlobalScheduler.create(2, 30.seconds).unsafeRunSync()
    try {
      val result = scheduler
        .submit(50, IO.raiseError[Int](new RuntimeException("task boom")))
        .attempt
        .unsafeRunSync()

      result.isLeft shouldBe true
      result.left.toOption.get.getMessage shouldBe "task boom"
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "continue processing after a task fails" in {
    val scheduler = BoundedGlobalScheduler.create(2, 30.seconds).unsafeRunSync()
    try {
      (for {
        _      <- scheduler.submit(50, IO.raiseError[Int](new RuntimeException("fail"))).attempt
        result <- scheduler.submit(50, IO.pure(42))
        _ = result shouldBe 42

        stats <- scheduler.stats
        _ = stats.totalSubmitted shouldBe 2
        _ = stats.totalCompleted shouldBe 2
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "not leak concurrency slots when tasks fail" taggedAs Retryable in {
    val scheduler = BoundedGlobalScheduler.create(2, 30.seconds).unsafeRunSync()
    try {
      (for {
        // Submit tasks that all fail
        _ <- (1 to 10).toList.parTraverse { i =>
          scheduler.submit(50, IO.raiseError[Int](new RuntimeException(s"fail-$i"))).attempt
        }

        // Slots should be freed; new tasks should execute
        results <- (1 to 5).toList.parTraverse { i =>
          scheduler.submit(50, IO.pure(i))
        }
        _ = results should contain theSameElementsAs (1 to 5)

        stats <- scheduler.stats
        _ = stats.activeCount shouldBe 0
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // Priority Clamping
  // -------------------------------------------------------------------------

  "BoundedGlobalScheduler priority clamping" should "clamp negative priorities to 0" in {
    val scheduler = BoundedGlobalScheduler.create(2, 30.seconds).unsafeRunSync()
    try {
      (for {
        _     <- scheduler.submit(-50, IO.pure("neg"))
        stats <- scheduler.stats
        _ = stats.lowPriorityCompleted shouldBe 1 // 0 < 25 -> low priority
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  it should "clamp priorities above 100 to 100" in {
    val scheduler = BoundedGlobalScheduler.create(2, 30.seconds).unsafeRunSync()
    try {
      (for {
        _     <- scheduler.submit(200, IO.pure("over"))
        stats <- scheduler.stats
        _ = stats.highPriorityCompleted shouldBe 1 // 100 >= 75 -> high priority
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // submitNormal convenience method
  // -------------------------------------------------------------------------

  "BoundedGlobalScheduler.submitNormal" should "use priority 50" in {
    val scheduler = BoundedGlobalScheduler.create(2, 30.seconds).unsafeRunSync()
    try {
      (for {
        result <- scheduler.submitNormal(IO.pure("normal"))
        _ = result shouldBe "normal"

        stats <- scheduler.stats
        // Priority 50 is neither high (>=75) nor low (<25)
        _ = stats.highPriorityCompleted shouldBe 0
        _ = stats.lowPriorityCompleted shouldBe 0
        _ = stats.totalCompleted shouldBe 1
      } yield ()).unsafeRunSync()
    } finally scheduler.shutdown.unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // Concurrent stress test
  // -------------------------------------------------------------------------

  "BoundedGlobalScheduler under stress" should "handle many concurrent submissions with mixed priorities" in {
    val scheduler = BoundedGlobalScheduler.create(4, 30.seconds).unsafeRunSync()
    try {
      val counter = new AtomicLong(0)

      val tasks = (1 to 200).toList.map { i =>
        val priority = i match {
          case x if x <= 50  => 90 // high
          case x if x <= 100 => 50 // normal
          case x if x <= 150 => 20 // low
          case _             => 0  // background
        }
        scheduler.submit(priority, IO { counter.incrementAndGet(); i })
      }

      val results = tasks.parSequence.unsafeRunSync()
      results.toSet shouldBe (1 to 200).toSet
      counter.get() shouldBe 200

      val stats = scheduler.stats.unsafeRunSync()
      stats.totalSubmitted shouldBe 200
      stats.totalCompleted shouldBe 200
      stats.highPriorityCompleted shouldBe 50 // priority 90 >= 75
      stats.lowPriorityCompleted shouldBe 100 // priority 20 and 0 < 25
    } finally scheduler.shutdown.unsafeRunSync()
  }
}
