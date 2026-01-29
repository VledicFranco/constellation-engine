package io.constellation.execution

import cats.effect.{Deferred, IO}
import cats.effect.unsafe.implicits.global
import cats.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import java.util.UUID

import io.constellation._
import io.constellation.spi.ConstellationBackends

class ConstellationLifecycleTest extends AnyFlatSpec with Matchers {

  // -------------------------------------------------------------------------
  // Helper: create a mock CancellableExecution
  // -------------------------------------------------------------------------

  private def mockExecution(
      id: UUID = UUID.randomUUID(),
      cancelDelay: FiniteDuration = 0.millis,
      completionDelay: Option[FiniteDuration] = None
  ): IO[(CancellableExecution, Deferred[IO, Unit])] =
    for {
      cancelledSignal <- Deferred[IO, Unit]
      completionSignal <- Deferred[IO, Unit]
      statusRef <- cats.effect.Ref.of[IO, ExecutionStatus](ExecutionStatus.Running)
    } yield {
      val exec = new CancellableExecution {
        val executionId: UUID = id
        def cancel: IO[Unit] =
          IO.sleep(cancelDelay) *>
          statusRef.set(ExecutionStatus.Cancelled) *>
          cancelledSignal.complete(()).void
        def result: IO[Runtime.State] =
          completionSignal.get.as(Runtime.State(
            processUuid = id,
            dag = DagSpec.empty("mock"),
            moduleStatus = Map.empty,
            data = Map.empty,
            latency = None
          ))
        def status: IO[ExecutionStatus] = statusRef.get
      }

      completionDelay.foreach { delay =>
        (IO.sleep(delay) *> completionSignal.complete(()).void).unsafeRunAndForget()
      }

      (exec, completionSignal)
    }

  // -------------------------------------------------------------------------
  // Initial State
  // -------------------------------------------------------------------------

  "ConstellationLifecycle" should "start in Running state" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    lc.state.unsafeRunSync() shouldBe LifecycleState.Running
    lc.inflightCount.unsafeRunSync() shouldBe 0
  }

  // -------------------------------------------------------------------------
  // Registration
  // -------------------------------------------------------------------------

  it should "allow registration when Running" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()
    val (exec, _) = mockExecution().unsafeRunSync()

    val registered = lc.registerExecution(exec.executionId, exec).unsafeRunSync()
    registered shouldBe true
    lc.inflightCount.unsafeRunSync() shouldBe 1
  }

  it should "reject registration when Draining" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    // Register first to keep it in Draining state
    val (exec1, _) = mockExecution().unsafeRunSync()
    lc.registerExecution(exec1.executionId, exec1).unsafeRunSync()

    // Start shutdown in background (drain timeout long enough for our assertions)
    val shutdownFiber = lc.shutdown(5.seconds).start.unsafeRunSync()
    IO.sleep(200.millis).unsafeRunSync()

    // Should be draining now
    lc.state.unsafeRunSync() shouldBe LifecycleState.Draining

    // New registration should be rejected
    val (exec2, _) = mockExecution().unsafeRunSync()
    val registered = lc.registerExecution(exec2.executionId, exec2).unsafeRunSync()
    registered shouldBe false

    // Clean up: deregister the in-flight execution so shutdown can complete
    lc.deregisterExecution(exec1.executionId).unsafeRunSync()
    shutdownFiber.join.timeout(3.seconds).unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // Deregistration
  // -------------------------------------------------------------------------

  it should "track deregistration" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()
    val (exec, _) = mockExecution().unsafeRunSync()

    lc.registerExecution(exec.executionId, exec).unsafeRunSync()
    lc.inflightCount.unsafeRunSync() shouldBe 1

    lc.deregisterExecution(exec.executionId).unsafeRunSync()
    lc.inflightCount.unsafeRunSync() shouldBe 0
  }

  // -------------------------------------------------------------------------
  // Shutdown - Drain
  // -------------------------------------------------------------------------

  it should "drain in-flight executions during shutdown" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    // Register an execution that completes after 200ms
    val (exec, signal) = mockExecution(completionDelay = Some(200.millis)).unsafeRunSync()
    lc.registerExecution(exec.executionId, exec).unsafeRunSync()
    lc.inflightCount.unsafeRunSync() shouldBe 1

    // Shutdown should wait for completion
    val shutdownFiber = (for {
      _ <- lc.shutdown(5.seconds)
    } yield ()).start.unsafeRunSync()

    IO.sleep(100.millis).unsafeRunSync()
    lc.state.unsafeRunSync() shouldBe LifecycleState.Draining

    // Simulate execution completing
    lc.deregisterExecution(exec.executionId).unsafeRunSync()

    shutdownFiber.join.timeout(3.seconds).unsafeRunSync()
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }

  it should "complete immediately if no in-flight executions" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    lc.shutdown(1.second).timeout(3.seconds).unsafeRunSync()
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }

  // -------------------------------------------------------------------------
  // Shutdown - Cancel on Timeout
  // -------------------------------------------------------------------------

  it should "cancel remaining executions after drain timeout" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    // Register an execution that won't complete on its own
    val (exec, _) = mockExecution().unsafeRunSync()
    lc.registerExecution(exec.executionId, exec).unsafeRunSync()

    // Shutdown with short timeout
    lc.shutdown(200.millis).timeout(3.seconds).unsafeRunSync()

    // Cancel should have been called, and we should be Stopped
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Cancelled
  }

  // -------------------------------------------------------------------------
  // ShutdownRejectedException
  // -------------------------------------------------------------------------

  "ShutdownRejectedException" should "have a descriptive message" in {
    val ex = new ConstellationLifecycle.ShutdownRejectedException("Server is shutting down")
    ex.getMessage shouldBe "Server is shutting down"
  }

  // -------------------------------------------------------------------------
  // Backpressure - QueueFullException
  // -------------------------------------------------------------------------

  "GlobalScheduler with maxQueueSize" should "reject when queue is full" in {
    GlobalScheduler.bounded(maxConcurrency = 1, maxQueueSize = 2).use { scheduler =>
      for {
        // Fill up: 1 active + 2 queued = at capacity
        gate <- Deferred[IO, Unit]

        // Submit a blocking task (takes the active slot)
        f1 <- scheduler.submit(50, gate.get).start

        // Give time for f1 to be dispatched
        _ <- IO.sleep(100.millis)

        // Fill the queue with 2 tasks
        f2 <- scheduler.submit(50, IO.pure(2)).start
        f3 <- scheduler.submit(50, IO.pure(3)).start

        // Give time for tasks to be enqueued
        _ <- IO.sleep(100.millis)

        // Next submission should fail (queue full)
        result <- scheduler.submit(50, IO.pure(4)).attempt

        // Clean up
        _ <- gate.complete(())
        _ <- f1.join
        _ <- f2.join
        _ <- f3.join
      } yield {
        result.isLeft shouldBe true
        result.left.toOption.get shouldBe a[QueueFullException]
        val ex = result.left.toOption.get.asInstanceOf[QueueFullException]
        ex.maxSize shouldBe 2
      }
    }.unsafeRunSync()
  }

  it should "not reject when maxQueueSize is 0 (unlimited)" in {
    GlobalScheduler.bounded(maxConcurrency = 1, maxQueueSize = 0).use { scheduler =>
      for {
        gate <- Deferred[IO, Unit]
        f1 <- scheduler.submit(50, gate.get).start
        _ <- IO.sleep(100.millis)

        // Should succeed even with many queued tasks
        f2 <- scheduler.submit(50, IO.pure(2)).start
        f3 <- scheduler.submit(50, IO.pure(3)).start
        f4 <- scheduler.submit(50, IO.pure(4)).start
        f5 <- scheduler.submit(50, IO.pure(5)).start

        _ <- gate.complete(())
        _ <- f1.join
        _ <- f2.join
        _ <- f3.join
        _ <- f4.join
        _ <- f5.join
      } yield succeed
    }.unsafeRunSync()
  }

  it should "allow submissions after queue drains" in {
    GlobalScheduler.bounded(maxConcurrency = 1, maxQueueSize = 1).use { scheduler =>
      for {
        gate <- Deferred[IO, Unit]
        f1 <- scheduler.submit(50, gate.get).start
        _ <- IO.sleep(100.millis)

        // Queue 1 task (at capacity)
        f2 <- scheduler.submit(50, IO.pure(2)).start
        _ <- IO.sleep(50.millis)

        // Release — queue drains
        _ <- gate.complete(())
        _ <- f1.join
        _ <- f2.join

        // Should work again
        result <- scheduler.submit(50, IO.pure(42))
      } yield {
        result shouldBe 42
      }
    }.unsafeRunSync()
  }
}
