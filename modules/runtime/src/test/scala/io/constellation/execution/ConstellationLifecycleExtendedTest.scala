package io.constellation.execution

import java.util.UUID

import scala.concurrent.duration.*

import cats.effect.unsafe.implicits.global
import cats.effect.{Deferred, IO, Ref}
import cats.implicits.*

import io.constellation.{RetrySupport, *}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable

class ConstellationLifecycleExtendedTest extends AnyFlatSpec with Matchers with RetrySupport {

  // -------------------------------------------------------------------------
  // Helper: create a mock CancellableExecution
  // -------------------------------------------------------------------------

  private def mockExecution(
      id: UUID = UUID.randomUUID(),
      cancelDelay: FiniteDuration = 0.millis
  ): IO[(CancellableExecution, Deferred[IO, Unit])] =
    for {
      cancelledSignal  <- Deferred[IO, Unit]
      completionSignal <- Deferred[IO, Unit]
      statusRef        <- Ref.of[IO, ExecutionStatus](ExecutionStatus.Running)
    } yield {
      val exec = new CancellableExecution {
        val executionId: UUID = id
        def cancel: IO[Unit] =
          IO.sleep(cancelDelay) *>
            statusRef.set(ExecutionStatus.Cancelled) *>
            cancelledSignal.complete(()).void
        def result: IO[Runtime.State] =
          completionSignal.get.as(
            Runtime.State(
              processUuid = id,
              dag = DagSpec.empty("mock"),
              moduleStatus = Map.empty,
              data = Map.empty,
              latency = None
            )
          )
        def status: IO[ExecutionStatus] = statusRef.get
      }
      (exec, completionSignal)
    }

  // -------------------------------------------------------------------------
  // init / create
  // -------------------------------------------------------------------------

  "ConstellationLifecycle.create" should "initialize in Running state with zero inflight" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    lc.state.unsafeRunSync() shouldBe LifecycleState.Running
    lc.inflightCount.unsafeRunSync() shouldBe 0
  }

  it should "create independent lifecycle instances" in {
    val lc1 = ConstellationLifecycle.create.unsafeRunSync()
    val lc2 = ConstellationLifecycle.create.unsafeRunSync()

    val (exec, _) = mockExecution().unsafeRunSync()
    lc1.registerExecution(exec.executionId, exec).unsafeRunSync()

    lc1.inflightCount.unsafeRunSync() shouldBe 1
    lc2.inflightCount.unsafeRunSync() shouldBe 0
  }

  // -------------------------------------------------------------------------
  // start / isRunning (via state)
  // -------------------------------------------------------------------------

  "ConstellationLifecycle state" should "be Running after creation" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()
    lc.state.unsafeRunSync() shouldBe LifecycleState.Running
  }

  it should "transition to Draining during shutdown" taggedAs Retryable in {
    val lc        = ConstellationLifecycle.create.unsafeRunSync()
    val (exec, _) = mockExecution().unsafeRunSync()

    lc.registerExecution(exec.executionId, exec).unsafeRunSync()

    // Start shutdown in background (won't complete because execution is still inflight)
    val shutdownFiber = lc.shutdown(5.seconds).start.unsafeRunSync()
    IO.sleep(200.millis).unsafeRunSync()

    lc.state.unsafeRunSync() shouldBe LifecycleState.Draining

    // Clean up
    lc.deregisterExecution(exec.executionId).unsafeRunSync()
    shutdownFiber.join.timeout(3.seconds).unsafeRunSync()
  }

  it should "transition to Stopped after shutdown completes" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    lc.shutdown(1.second).timeout(3.seconds).unsafeRunSync()
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }

  // -------------------------------------------------------------------------
  // Lifecycle State Transitions
  // -------------------------------------------------------------------------

  "ConstellationLifecycle state transitions" should "go Running -> Draining -> Stopped" taggedAs Retryable in {
    val lc        = ConstellationLifecycle.create.unsafeRunSync()
    val (exec, _) = mockExecution().unsafeRunSync()

    // Running
    lc.state.unsafeRunSync() shouldBe LifecycleState.Running

    lc.registerExecution(exec.executionId, exec).unsafeRunSync()

    // Start shutdown -> Draining
    val shutdownFiber = lc.shutdown(5.seconds).start.unsafeRunSync()
    IO.sleep(200.millis).unsafeRunSync()
    lc.state.unsafeRunSync() shouldBe LifecycleState.Draining

    // Deregister -> triggers drain completion -> Stopped
    lc.deregisterExecution(exec.executionId).unsafeRunSync()
    shutdownFiber.join.timeout(3.seconds).unsafeRunSync()
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }

  it should "go directly Running -> Stopped when no inflight executions" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    lc.state.unsafeRunSync() shouldBe LifecycleState.Running

    lc.shutdown(1.second).timeout(3.seconds).unsafeRunSync()

    // Should skip Draining and go straight to Stopped
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }

  it should "not allow registration after reaching Stopped state" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    lc.shutdown(1.second).timeout(3.seconds).unsafeRunSync()
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped

    val (exec, _) = mockExecution().unsafeRunSync()
    val registered = lc.registerExecution(exec.executionId, exec).unsafeRunSync()
    registered shouldBe false
  }

  it should "not allow registration while Draining" taggedAs Retryable in {
    val lc         = ConstellationLifecycle.create.unsafeRunSync()
    val (exec1, _) = mockExecution().unsafeRunSync()

    lc.registerExecution(exec1.executionId, exec1).unsafeRunSync()

    val shutdownFiber = lc.shutdown(5.seconds).start.unsafeRunSync()
    IO.sleep(200.millis).unsafeRunSync()

    lc.state.unsafeRunSync() shouldBe LifecycleState.Draining

    val (exec2, _) = mockExecution().unsafeRunSync()
    val registered  = lc.registerExecution(exec2.executionId, exec2).unsafeRunSync()
    registered shouldBe false

    // Clean up
    lc.deregisterExecution(exec1.executionId).unsafeRunSync()
    shutdownFiber.join.timeout(3.seconds).unsafeRunSync()
  }

  // -------------------------------------------------------------------------
  // registerExecution / deregisterExecution
  // -------------------------------------------------------------------------

  "ConstellationLifecycle registration" should "register multiple executions" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    val execs = (1 to 5).toList.map(_ => mockExecution().unsafeRunSync())

    execs.foreach { case (exec, _) =>
      lc.registerExecution(exec.executionId, exec).unsafeRunSync() shouldBe true
    }

    lc.inflightCount.unsafeRunSync() shouldBe 5
  }

  it should "track inflight count accurately on register and deregister" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    val (exec1, _) = mockExecution().unsafeRunSync()
    val (exec2, _) = mockExecution().unsafeRunSync()
    val (exec3, _) = mockExecution().unsafeRunSync()

    lc.registerExecution(exec1.executionId, exec1).unsafeRunSync()
    lc.inflightCount.unsafeRunSync() shouldBe 1

    lc.registerExecution(exec2.executionId, exec2).unsafeRunSync()
    lc.inflightCount.unsafeRunSync() shouldBe 2

    lc.registerExecution(exec3.executionId, exec3).unsafeRunSync()
    lc.inflightCount.unsafeRunSync() shouldBe 3

    lc.deregisterExecution(exec2.executionId).unsafeRunSync()
    lc.inflightCount.unsafeRunSync() shouldBe 2

    lc.deregisterExecution(exec1.executionId).unsafeRunSync()
    lc.inflightCount.unsafeRunSync() shouldBe 1

    lc.deregisterExecution(exec3.executionId).unsafeRunSync()
    lc.inflightCount.unsafeRunSync() shouldBe 0
  }

  it should "handle deregistering a non-existent execution gracefully" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    // Deregistering an ID that was never registered should not throw
    noException should be thrownBy {
      lc.deregisterExecution(UUID.randomUUID()).unsafeRunSync()
    }
  }

  it should "handle deregistering the same execution twice gracefully" in {
    val lc        = ConstellationLifecycle.create.unsafeRunSync()
    val (exec, _) = mockExecution().unsafeRunSync()

    lc.registerExecution(exec.executionId, exec).unsafeRunSync()
    lc.inflightCount.unsafeRunSync() shouldBe 1

    lc.deregisterExecution(exec.executionId).unsafeRunSync()
    lc.inflightCount.unsafeRunSync() shouldBe 0

    // Second deregister should be a no-op
    noException should be thrownBy {
      lc.deregisterExecution(exec.executionId).unsafeRunSync()
    }
    lc.inflightCount.unsafeRunSync() shouldBe 0
  }

  // -------------------------------------------------------------------------
  // Graceful Shutdown with In-Flight Tasks
  // -------------------------------------------------------------------------

  "ConstellationLifecycle graceful shutdown" should "wait for inflight executions to complete within drain timeout" taggedAs Retryable in {
    val lc        = ConstellationLifecycle.create.unsafeRunSync()
    val (exec, _) = mockExecution().unsafeRunSync()

    lc.registerExecution(exec.executionId, exec).unsafeRunSync()

    // Start shutdown with generous timeout
    val shutdownFiber = lc.shutdown(5.seconds).start.unsafeRunSync()
    IO.sleep(200.millis).unsafeRunSync()

    // State should be Draining
    lc.state.unsafeRunSync() shouldBe LifecycleState.Draining

    // Simulate execution completion
    lc.deregisterExecution(exec.executionId).unsafeRunSync()

    // Shutdown should complete now
    shutdownFiber.join.timeout(3.seconds).unsafeRunSync()
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }

  it should "cancel remaining executions when drain timeout expires" in {
    val lc        = ConstellationLifecycle.create.unsafeRunSync()
    val (exec, _) = mockExecution().unsafeRunSync()

    lc.registerExecution(exec.executionId, exec).unsafeRunSync()

    // Shutdown with very short timeout (execution won't finish in time)
    lc.shutdown(200.millis).timeout(5.seconds).unsafeRunSync()

    // Cancel should have been called
    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Cancelled
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }

  it should "cancel multiple remaining executions on timeout" in {
    val lc         = ConstellationLifecycle.create.unsafeRunSync()
    val (exec1, _) = mockExecution().unsafeRunSync()
    val (exec2, _) = mockExecution().unsafeRunSync()
    val (exec3, _) = mockExecution().unsafeRunSync()

    lc.registerExecution(exec1.executionId, exec1).unsafeRunSync()
    lc.registerExecution(exec2.executionId, exec2).unsafeRunSync()
    lc.registerExecution(exec3.executionId, exec3).unsafeRunSync()

    lc.inflightCount.unsafeRunSync() shouldBe 3

    // Shutdown with short timeout
    lc.shutdown(200.millis).timeout(5.seconds).unsafeRunSync()

    // All executions should have been cancelled
    exec1.status.unsafeRunSync() shouldBe ExecutionStatus.Cancelled
    exec2.status.unsafeRunSync() shouldBe ExecutionStatus.Cancelled
    exec3.status.unsafeRunSync() shouldBe ExecutionStatus.Cancelled
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }

  it should "drain some and cancel the rest on timeout" taggedAs Retryable in {
    val lc         = ConstellationLifecycle.create.unsafeRunSync()
    val (exec1, _) = mockExecution().unsafeRunSync()
    val (exec2, _) = mockExecution().unsafeRunSync()

    lc.registerExecution(exec1.executionId, exec1).unsafeRunSync()
    lc.registerExecution(exec2.executionId, exec2).unsafeRunSync()

    // Start shutdown with moderate timeout
    val shutdownFiber = lc.shutdown(1.second).start.unsafeRunSync()
    IO.sleep(100.millis).unsafeRunSync()

    // exec1 completes during drain period
    lc.deregisterExecution(exec1.executionId).unsafeRunSync()

    // exec2 does not complete -> will be cancelled after timeout
    shutdownFiber.join.timeout(5.seconds).unsafeRunSync()

    // exec1 was deregistered normally (status unchanged from Running since our mock
    // doesn't set status on deregister)
    exec1.status.unsafeRunSync() shouldBe ExecutionStatus.Running

    // exec2 should have been cancelled by the timeout handler
    exec2.status.unsafeRunSync() shouldBe ExecutionStatus.Cancelled

    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }

  it should "handle shutdown with slow cancel gracefully" in {
    // Use a mock that has a delay in cancel
    val lc        = ConstellationLifecycle.create.unsafeRunSync()
    val (exec, _) = mockExecution(cancelDelay = 200.millis).unsafeRunSync()

    lc.registerExecution(exec.executionId, exec).unsafeRunSync()

    // Shutdown with short drain timeout - cancel will be called but takes time
    lc.shutdown(100.millis).timeout(5.seconds).unsafeRunSync()

    // Even with slow cancel, shutdown should complete
    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Cancelled
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }

  // -------------------------------------------------------------------------
  // Concurrent registration + shutdown
  // -------------------------------------------------------------------------

  "ConstellationLifecycle concurrent operations" should "handle concurrent registrations safely" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    val registrations = (1 to 20).toList.parTraverse { _ =>
      for {
        pair <- mockExecution()
        (exec, _) = pair
        registered <- lc.registerExecution(exec.executionId, exec)
      } yield registered
    }.unsafeRunSync()

    registrations.count(_ == true) shouldBe 20
    lc.inflightCount.unsafeRunSync() shouldBe 20
  }

  it should "handle concurrent deregistrations safely" in {
    val lc = ConstellationLifecycle.create.unsafeRunSync()

    // Register 20 executions
    val execs = (1 to 20).toList.map(_ => mockExecution().unsafeRunSync())
    execs.foreach { case (exec, _) =>
      lc.registerExecution(exec.executionId, exec).unsafeRunSync()
    }

    lc.inflightCount.unsafeRunSync() shouldBe 20

    // Deregister all concurrently
    execs.parTraverse { case (exec, _) =>
      lc.deregisterExecution(exec.executionId)
    }.unsafeRunSync()

    lc.inflightCount.unsafeRunSync() shouldBe 0
  }

  // -------------------------------------------------------------------------
  // ShutdownRejectedException
  // -------------------------------------------------------------------------

  "ShutdownRejectedException" should "carry the correct message" in {
    val ex = new ConstellationLifecycle.ShutdownRejectedException("System shutting down")
    ex.getMessage shouldBe "System shutting down"
    ex shouldBe a[RuntimeException]
  }

  it should "be usable as a Throwable" in {
    val ex: Throwable = new ConstellationLifecycle.ShutdownRejectedException("rejected")
    ex.getMessage shouldBe "rejected"
  }

  // -------------------------------------------------------------------------
  // Edge cases
  // -------------------------------------------------------------------------

  "ConstellationLifecycle edge cases" should "handle shutdown with zero drain timeout" in {
    val lc        = ConstellationLifecycle.create.unsafeRunSync()
    val (exec, _) = mockExecution().unsafeRunSync()

    lc.registerExecution(exec.executionId, exec).unsafeRunSync()

    // Zero timeout means cancel immediately
    lc.shutdown(0.millis).timeout(5.seconds).unsafeRunSync()

    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Cancelled
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }

  it should "handle registering with the same UUID twice" in {
    val lc    = ConstellationLifecycle.create.unsafeRunSync()
    val id    = UUID.randomUUID()
    val (exec1, _) = mockExecution(id = id).unsafeRunSync()
    val (exec2, _) = mockExecution(id = id).unsafeRunSync()

    lc.registerExecution(id, exec1).unsafeRunSync() shouldBe true
    // Second registration with same UUID overwrites (Map behavior)
    lc.registerExecution(id, exec2).unsafeRunSync() shouldBe true
    // But inflight count reflects unique keys
    lc.inflightCount.unsafeRunSync() shouldBe 1
  }

  it should "complete shutdown immediately when all executions deregister before shutdown starts" in {
    val lc        = ConstellationLifecycle.create.unsafeRunSync()
    val (exec, _) = mockExecution().unsafeRunSync()

    lc.registerExecution(exec.executionId, exec).unsafeRunSync()
    lc.deregisterExecution(exec.executionId).unsafeRunSync()

    // No inflight -> shutdown should be instant
    lc.shutdown(1.second).timeout(3.seconds).unsafeRunSync()
    lc.state.unsafeRunSync() shouldBe LifecycleState.Stopped
  }
}
