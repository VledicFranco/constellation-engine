package io.constellation.execution

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import io.constellation.RetrySupport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable

import scala.concurrent.duration.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

import io.constellation.*
import io.constellation.spi.{ConstellationBackends, ExecutionListener}

class CancellableExecutionTest extends AnyFlatSpec with Matchers with RetrySupport {

  // -------------------------------------------------------------------------
  // Helper: build a simple DAG for testing
  // -------------------------------------------------------------------------

  private case class TextInput(text: String)
  private case class TextOutput(result: String)

  private def slowModule(name: String, delay: FiniteDuration): Module.Uninitialized =
    ModuleBuilder
      .metadata(name, s"Test module $name", 1, 0)
      .implementation[TextInput, TextOutput] { input =>
        IO.sleep(delay).as(TextOutput(input.text.toUpperCase))
      }
      .build

  private def fastModule(name: String): Module.Uninitialized =
    ModuleBuilder
      .metadata(name, s"Test module $name", 1, 0)
      .implementationPure[TextInput, TextOutput] { input =>
        TextOutput(input.text.toUpperCase)
      }
      .build

  private def buildSimpleDag(
      moduleName: String,
      moduleFactory: String => Module.Uninitialized
  ): (DagSpec, Map[UUID, Module.Uninitialized]) = {
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()
    val moduleId     = UUID.randomUUID()

    val inputSpec = DataNodeSpec(
      name = "text",
      cType = CType.CString,
      nicknames = Map(moduleId -> "text")
    )

    val outputSpec = DataNodeSpec(
      name = "result",
      cType = CType.CString,
      nicknames = Map(moduleId -> "result")
    )

    val moduleSpec = ModuleNodeSpec(
      metadata = ComponentMetadata(moduleName, s"Test module $moduleName", List.empty, 1, 0),
      consumes = Map("text" -> CType.CString),
      produces = Map("result" -> CType.CString)
    )

    val dag = DagSpec(
      metadata = ComponentMetadata(moduleName + "-dag", "", List.empty, 1, 0),
      data = Map(inputDataId -> inputSpec, outputDataId -> outputSpec),
      modules = Map(moduleId -> moduleSpec),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    (dag, Map(moduleId -> moduleFactory(moduleName)))
  }

  // -------------------------------------------------------------------------
  // CancellableExecution.completed
  // -------------------------------------------------------------------------

  "CancellableExecution.completed" should "create a completed execution" in {
    val execId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")
    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = dag,
      moduleStatus = Map.empty,
      data = Map.empty,
      latency = Some(100.millis)
    )

    val exec = CancellableExecution.completed(execId, state).unsafeRunSync()

    exec.executionId shouldBe execId
    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Completed
    exec.result.unsafeRunSync() shouldBe state
  }

  it should "be a no-op when cancelled" in {
    val execId = UUID.randomUUID()
    val dag    = DagSpec.empty("test")
    val state = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = dag,
      moduleStatus = Map.empty,
      data = Map.empty,
      latency = Some(100.millis)
    )

    val exec = CancellableExecution.completed(execId, state).unsafeRunSync()
    exec.cancel.unsafeRunSync()
    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Completed
  }

  // -------------------------------------------------------------------------
  // Runtime.runCancellable - Normal completion
  // -------------------------------------------------------------------------

  "Runtime.runCancellable" should "complete normally with full results" in {
    val (dag, modules) = buildSimpleDag("Fast", fastModule)
    val inputs         = Map("text" -> CValue.CString("hello"))

    val exec = Runtime
      .runCancellable(
        dag,
        inputs,
        modules,
        Map.empty,
        GlobalScheduler.unbounded,
        ConstellationBackends.defaults
      )
      .unsafeRunSync()

    val state = exec.result.timeout(5.seconds).unsafeRunSync()
    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Completed
    state.latency.isDefined shouldBe true
  }

  // -------------------------------------------------------------------------
  // Runtime.runCancellable - Cancellation
  // -------------------------------------------------------------------------

  it should "cancel a slow execution and return Cancelled status" taggedAs Retryable in {
    val (dag, modules) = buildSimpleDag("Slow", name => slowModule(name, 10.seconds))
    val inputs         = Map("text" -> CValue.CString("hello"))

    val exec = Runtime
      .runCancellable(
        dag,
        inputs,
        modules,
        Map.empty,
        GlobalScheduler.unbounded,
        ConstellationBackends.defaults
      )
      .unsafeRunSync()

    // Give it a moment to start
    IO.sleep(200.millis).unsafeRunSync()
    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Running

    // Cancel
    exec.cancel.unsafeRunSync()

    // Result should complete (not hang)
    val state = exec.result.timeout(5.seconds).unsafeRunSync()
    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Cancelled
  }

  it should "be idempotent when cancelled multiple times" taggedAs Retryable in {
    val (dag, modules) = buildSimpleDag("Slow2", name => slowModule(name, 10.seconds))
    val inputs         = Map("text" -> CValue.CString("hello"))

    val exec = Runtime
      .runCancellable(
        dag,
        inputs,
        modules,
        Map.empty,
        GlobalScheduler.unbounded,
        ConstellationBackends.defaults
      )
      .unsafeRunSync()

    IO.sleep(200.millis).unsafeRunSync()

    // Cancel twice — should not throw
    exec.cancel.unsafeRunSync()
    exec.cancel.unsafeRunSync()

    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Cancelled
    exec.result.timeout(5.seconds).unsafeRunSync()
  }

  it should "be a no-op when cancelled after completion" in {
    val (dag, modules) = buildSimpleDag("Fast2", fastModule)
    val inputs         = Map("text" -> CValue.CString("hello"))

    val exec = Runtime
      .runCancellable(
        dag,
        inputs,
        modules,
        Map.empty,
        GlobalScheduler.unbounded,
        ConstellationBackends.defaults
      )
      .unsafeRunSync()

    // Wait for completion
    exec.result.timeout(5.seconds).unsafeRunSync()
    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Completed

    // Cancel after completion — should be no-op
    exec.cancel.unsafeRunSync()
    exec.status.unsafeRunSync() shouldBe ExecutionStatus.Completed
  }

  // -------------------------------------------------------------------------
  // Runtime.runWithTimeout
  // -------------------------------------------------------------------------

  "Runtime.runWithTimeout" should "complete within timeout" in {
    val (dag, modules) = buildSimpleDag("Fast3", fastModule)
    val inputs         = Map("text" -> CValue.CString("hello"))

    val state = Runtime
      .runWithTimeout(
        5.seconds,
        dag,
        inputs,
        modules,
        Map.empty,
        GlobalScheduler.unbounded,
        ConstellationBackends.defaults
      )
      .unsafeRunSync()

    state.latency.isDefined shouldBe true
  }

  it should "cancel execution when timeout elapses" taggedAs Retryable in {
    val (dag, modules) = buildSimpleDag("Slow3", name => slowModule(name, 10.seconds))
    val inputs         = Map("text" -> CValue.CString("hello"))

    val state = Runtime
      .runWithTimeout(
        500.millis,
        dag,
        inputs,
        modules,
        Map.empty,
        GlobalScheduler.unbounded,
        ConstellationBackends.defaults
      )
      .timeout(10.seconds)
      .unsafeRunSync()

    // Execution should have completed (timed out, not hung)
    state should not be null
  }

  // -------------------------------------------------------------------------
  // ExecutionListener.onExecutionCancelled
  // -------------------------------------------------------------------------

  "ExecutionListener" should "receive onExecutionCancelled when execution is cancelled" taggedAs Retryable in {
    val cancelledCalled = new AtomicBoolean(false)

    val listener = new ExecutionListener {
      def onExecutionStart(executionId: UUID, dagName: String): IO[Unit]                 = IO.unit
      def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] = IO.unit
      def onModuleComplete(
          executionId: UUID,
          moduleId: UUID,
          moduleName: String,
          durationMs: Long
      ): IO[Unit] = IO.unit
      def onModuleFailed(
          executionId: UUID,
          moduleId: UUID,
          moduleName: String,
          error: Throwable
      ): IO[Unit] = IO.unit
      def onExecutionComplete(
          executionId: UUID,
          dagName: String,
          succeeded: Boolean,
          durationMs: Long
      ): IO[Unit] = IO.unit
      override def onExecutionCancelled(executionId: UUID, dagName: String): IO[Unit] =
        IO(cancelledCalled.set(true))
    }

    val backends       = ConstellationBackends(listener = listener)
    val (dag, modules) = buildSimpleDag("Slow4", name => slowModule(name, 10.seconds))
    val inputs         = Map("text" -> CValue.CString("hello"))

    val exec = Runtime
      .runCancellable(
        dag,
        inputs,
        modules,
        Map.empty,
        GlobalScheduler.unbounded,
        backends
      )
      .unsafeRunSync()

    IO.sleep(200.millis).unsafeRunSync()
    exec.cancel.unsafeRunSync()
    exec.result.timeout(5.seconds).unsafeRunSync()

    // Give fire-and-forget listener time to execute
    IO.sleep(500.millis).unsafeRunSync()
    cancelledCalled.get() shouldBe true
  }

  // -------------------------------------------------------------------------
  // Bounded scheduler integration
  // -------------------------------------------------------------------------

  "Runtime.runCancellable" should "work with bounded scheduler" in {
    GlobalScheduler
      .bounded(maxConcurrency = 2)
      .use { scheduler =>
        val (dag, modules) = buildSimpleDag("Fast4", fastModule)
        val inputs         = Map("text" -> CValue.CString("hello"))

        Runtime
          .runCancellable(
            dag,
            inputs,
            modules,
            Map.empty,
            scheduler,
            ConstellationBackends.defaults
          )
          .flatMap { exec =>
            exec.result.timeout(5.seconds).map { state =>
              state.latency.isDefined shouldBe true
            }
          }
      }
      .unsafeRunSync()
  }
}
