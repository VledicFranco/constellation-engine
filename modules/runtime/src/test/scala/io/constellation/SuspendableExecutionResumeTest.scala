package io.constellation

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.execution.GlobalScheduler
import io.constellation.spi.ConstellationBackends

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for SuspendableExecution.resume() and its validation logic.
  *
  * Covers the full resume lifecycle including:
  *   - Successful resume of a simple suspended execution
  *   - Validation of additional inputs (type mismatch, already provided, unknown)
  *   - Validation of resolved nodes (type mismatch, already computed, unknown)
  *   - DataSignature correctness after resume
  */
class SuspendableExecutionResumeTest extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // Shared fixture: text -> Uppercase -> result
  // ---------------------------------------------------------------------------

  case class StringInput(text: String)
  case class StringOutput(result: String)

  private val uppercaseModule: Module.Uninitialized = ModuleBuilder
    .metadata("Uppercase", "Test uppercase", 1, 0)
    .implementationPure[StringInput, StringOutput](in => StringOutput(in.text.toUpperCase))
    .build

  private val moduleId     = UUID.randomUUID()
  private val inputDataId  = UUID.randomUUID()
  private val outputDataId = UUID.randomUUID()

  /** Builds a simple single-module DAG: text -> Uppercase -> result */
  private def simpleDag: DagSpec = DagSpec(
    metadata = ComponentMetadata.empty("TestDag"),
    modules = Map(
      moduleId -> ModuleNodeSpec(
        metadata = ComponentMetadata("Uppercase", "Test uppercase", List.empty, 1, 0),
        consumes = Map("text" -> CType.CString),
        produces = Map("result" -> CType.CString)
      )
    ),
    data = Map(
      inputDataId -> DataNodeSpec(
        "text",
        Map(inputDataId -> "text", moduleId -> "text"),
        CType.CString
      ),
      outputDataId -> DataNodeSpec(
        "result",
        Map(moduleId -> "result"),
        CType.CString
      )
    ),
    inEdges = Set((inputDataId, moduleId)),
    outEdges = Set((moduleId, outputDataId)),
    declaredOutputs = List("result"),
    outputBindings = Map("result" -> outputDataId)
  )

  /** Creates a suspended execution with no inputs provided and no computed values. */
  private def mkSuspended(
      dag: DagSpec = simpleDag,
      providedInputs: Map[String, CValue] = Map.empty,
      computedValues: Map[UUID, CValue] = Map.empty
  ): SuspendedExecution = SuspendedExecution(
    executionId = UUID.randomUUID(),
    structuralHash = "test-hash",
    resumptionCount = 0,
    dagSpec = dag,
    moduleOptions = Map.empty,
    providedInputs = providedInputs,
    computedValues = computedValues,
    moduleStatuses = Map.empty
  )

  private val scheduler: GlobalScheduler         = GlobalScheduler.unbounded
  private val backends: ConstellationBackends     = ConstellationBackends.defaults

  // ---------------------------------------------------------------------------
  // Successful resume
  // ---------------------------------------------------------------------------

  "resume" should "complete a simple suspended execution with valid inputs" in {
    val suspended = mkSuspended()

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("hello")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs should contain key "result"
    result.outputs("result") shouldBe CValue.CString("HELLO")
    result.resumptionCount shouldBe 1
  }

  it should "include the input in the DataSignature" in {
    val suspended = mkSuspended()

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("world")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.inputs should contain("text" -> CValue.CString("world"))
  }

  it should "merge additional inputs with existing provided inputs" in {
    // Create a DAG with two inputs
    val moduleId2      = UUID.randomUUID()
    val inputDataId2   = UUID.randomUUID()
    val inputDataId3   = UUID.randomUUID()
    val outputDataId2  = UUID.randomUUID()

    case class TwoIn(text: String, suffix: String)
    case class ConcatOut(result: String)

    val concatModule: Module.Uninitialized = ModuleBuilder
      .metadata("Concat", "Concatenation", 1, 0)
      .implementationPure[TwoIn, ConcatOut](in => ConcatOut(in.text + in.suffix))
      .build

    val twoInputDag = DagSpec(
      metadata = ComponentMetadata.empty("TwoInputDag"),
      modules = Map(
        moduleId2 -> ModuleNodeSpec(
          metadata = ComponentMetadata("Concat", "Concatenation", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString, "suffix" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId2 -> DataNodeSpec(
          "text",
          Map(inputDataId2 -> "text", moduleId2 -> "text"),
          CType.CString
        ),
        inputDataId3 -> DataNodeSpec(
          "suffix",
          Map(inputDataId3 -> "suffix", moduleId2 -> "suffix"),
          CType.CString
        ),
        outputDataId2 -> DataNodeSpec(
          "result",
          Map(moduleId2 -> "result"),
          CType.CString
        )
      ),
      inEdges = Set((inputDataId2, moduleId2), (inputDataId3, moduleId2)),
      outEdges = Set((moduleId2, outputDataId2)),
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> outputDataId2)
    )

    // Suspend with "text" already provided, resume with "suffix"
    val suspended = mkSuspended(
      dag = twoInputDag,
      providedInputs = Map("text" -> CValue.CString("hello"))
    )

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("suffix" -> CValue.CString("_world")),
        modules = Map(moduleId2 -> concatModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs("result") shouldBe CValue.CString("hello_world")
    result.inputs should contain("text"   -> CValue.CString("hello"))
    result.inputs should contain("suffix" -> CValue.CString("_world"))
  }

  it should "increment the resumption count" in {
    val suspended = mkSuspended().copy(resumptionCount = 2)

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("test")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.resumptionCount shouldBe 3
  }

  // ---------------------------------------------------------------------------
  // Additional input validation: InputTypeMismatchError
  // ---------------------------------------------------------------------------

  "resume with additional inputs" should "fail with InputTypeMismatchError for wrong type" in {
    val suspended = mkSuspended()

    val error = intercept[InputTypeMismatchError] {
      SuspendableExecution
        .resume(
          suspended = suspended,
          additionalInputs = Map("text" -> CValue.CInt(42)),
          modules = Map(moduleId -> uppercaseModule),
          scheduler = scheduler,
          backends = backends
        )
        .unsafeRunSync()
    }

    error.name shouldBe "text"
    error.expected shouldBe CType.CString
    error.actual shouldBe CType.CInt
  }

  // ---------------------------------------------------------------------------
  // Additional input validation: InputAlreadyProvidedError
  // ---------------------------------------------------------------------------

  it should "fail with InputAlreadyProvidedError when input conflicts with existing" in {
    // Suspend with "text" already provided as "hello"
    val suspended = mkSuspended(providedInputs = Map("text" -> CValue.CString("hello")))

    val error = intercept[InputAlreadyProvidedError] {
      SuspendableExecution
        .resume(
          suspended = suspended,
          additionalInputs = Map("text" -> CValue.CString("different")),
          modules = Map(moduleId -> uppercaseModule),
          scheduler = scheduler,
          backends = backends
        )
        .unsafeRunSync()
    }

    error.name shouldBe "text"
  }

  it should "allow re-providing the same input with the same value" in {
    // Re-providing the exact same value is allowed (not a conflict)
    val suspended = mkSuspended(providedInputs = Map("text" -> CValue.CString("hello")))

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("hello")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs("result") shouldBe CValue.CString("HELLO")
  }

  // ---------------------------------------------------------------------------
  // Additional input validation: UnknownNodeError
  // ---------------------------------------------------------------------------

  it should "fail with UnknownNodeError for unknown additional input name" in {
    val suspended = mkSuspended()

    val error = intercept[UnknownNodeError] {
      SuspendableExecution
        .resume(
          suspended = suspended,
          additionalInputs = Map("nonexistent" -> CValue.CString("value")),
          modules = Map(moduleId -> uppercaseModule),
          scheduler = scheduler,
          backends = backends
        )
        .unsafeRunSync()
    }

    error.name shouldBe "nonexistent"
  }

  // ---------------------------------------------------------------------------
  // Resolved node validation: NodeTypeMismatchError
  // ---------------------------------------------------------------------------

  "resume with resolved nodes" should "fail with NodeTypeMismatchError for wrong type" in {
    val suspended = mkSuspended()

    val error = intercept[NodeTypeMismatchError] {
      SuspendableExecution
        .resume(
          suspended = suspended,
          additionalInputs = Map("text" -> CValue.CString("hello")),
          resolvedNodes = Map("result" -> CValue.CInt(42)),
          modules = Map(moduleId -> uppercaseModule),
          scheduler = scheduler,
          backends = backends
        )
        .unsafeRunSync()
    }

    error.name shouldBe "result"
    error.expected shouldBe CType.CString
    error.actual shouldBe CType.CInt
  }

  // ---------------------------------------------------------------------------
  // Resolved node validation: NodeAlreadyResolvedError
  // ---------------------------------------------------------------------------

  it should "fail with NodeAlreadyResolvedError when node is already computed" in {
    // Mark the output node as already computed
    val suspended = mkSuspended(
      computedValues = Map(outputDataId -> CValue.CString("ALREADY"))
    )

    val error = intercept[NodeAlreadyResolvedError] {
      SuspendableExecution
        .resume(
          suspended = suspended,
          additionalInputs = Map("text" -> CValue.CString("hello")),
          resolvedNodes = Map("result" -> CValue.CString("OVERWRITE")),
          modules = Map(moduleId -> uppercaseModule),
          scheduler = scheduler,
          backends = backends
        )
        .unsafeRunSync()
    }

    error.name shouldBe "result"
  }

  // ---------------------------------------------------------------------------
  // Resolved node validation: UnknownNodeError
  // ---------------------------------------------------------------------------

  it should "fail with UnknownNodeError for unknown resolved node name" in {
    val suspended = mkSuspended()

    val error = intercept[UnknownNodeError] {
      SuspendableExecution
        .resume(
          suspended = suspended,
          additionalInputs = Map("text" -> CValue.CString("hello")),
          resolvedNodes = Map("unknown_node" -> CValue.CString("value")),
          modules = Map(moduleId -> uppercaseModule),
          scheduler = scheduler,
          backends = backends
        )
        .unsafeRunSync()
    }

    error.name shouldBe "unknown_node"
  }

  // ---------------------------------------------------------------------------
  // Concurrent resume prevention
  // ---------------------------------------------------------------------------

  "resume" should "release the in-flight lock after successful resume" in {
    val suspended = mkSuspended()

    // First resume should succeed
    val result1 = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("hello")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result1.status shouldBe PipelineStatus.Completed

    // Second resume with same executionId should also succeed (lock was released)
    val result2 = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("world")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result2.status shouldBe PipelineStatus.Completed
    result2.outputs("result") shouldBe CValue.CString("WORLD")
  }

  it should "release the in-flight lock even when resume fails" in {
    val suspended = mkSuspended()

    // First resume should fail (wrong type)
    intercept[InputTypeMismatchError] {
      SuspendableExecution
        .resume(
          suspended = suspended,
          additionalInputs = Map("text" -> CValue.CInt(42)),
          modules = Map(moduleId -> uppercaseModule),
          scheduler = scheduler,
          backends = backends
        )
        .unsafeRunSync()
    }

    // Second resume with correct inputs should succeed (lock was released)
    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("recovered")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs("result") shouldBe CValue.CString("RECOVERED")
  }

  // ---------------------------------------------------------------------------
  // buildDataSignature correctness
  // ---------------------------------------------------------------------------

  "buildDataSignature" should "produce a DataSignature with correct structural hash" in {
    val suspended = mkSuspended()

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("hello")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.structuralHash shouldBe "test-hash"
  }

  it should "produce a DataSignature with no missing inputs when all are provided" in {
    val suspended = mkSuspended()

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("hello")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.missingInputs shouldBe empty
    result.pendingOutputs shouldBe empty
  }

  it should "produce a DataSignature with no suspendedState when Completed" in {
    val suspended = mkSuspended()

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("hello")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.suspendedState shouldBe None
  }

  it should "include computed nodes in the DataSignature" in {
    val suspended = mkSuspended()

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("test")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.computedNodes should contain key "text"
    result.computedNodes should contain key "result"
    result.computedNodes("text") shouldBe CValue.CString("test")
    result.computedNodes("result") shouldBe CValue.CString("TEST")
  }

  it should "preserve the execution ID from the runtime state" in {
    val suspended = mkSuspended()

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("hello")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    // The execution ID comes from Runtime.State.processUuid (a new UUID per run),
    // not from the suspended execution's ID
    result.executionId should not be null
  }

  it should "include metadata timestamps" in {
    val suspended = mkSuspended()

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("hello")),
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.metadata.startedAt shouldBe defined
    result.metadata.completedAt shouldBe defined
    result.metadata.totalDuration shouldBe defined
  }

  // ---------------------------------------------------------------------------
  // Empty additional inputs and empty resolved nodes
  // ---------------------------------------------------------------------------

  "resume" should "succeed with empty additional inputs when inputs were already provided" in {
    val suspended = mkSuspended(providedInputs = Map("text" -> CValue.CString("pre-filled")))

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map.empty,
        modules = Map(moduleId -> uppercaseModule),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs("result") shouldBe CValue.CString("PRE-FILLED")
  }

  // ---------------------------------------------------------------------------
  // Resolution sources metadata
  // ---------------------------------------------------------------------------

  "resume with resolution sources enabled" should "classify resolved nodes correctly" in {
    // Create a DAG with two outputs so we can manually resolve one
    val modId     = UUID.randomUUID()
    val inId      = UUID.randomUUID()
    val outId1    = UUID.randomUUID()
    val outId2    = UUID.randomUUID()

    case class DualIn(text: String)
    case class DualOut(upper: String, lower: String)

    val dualModule: Module.Uninitialized = ModuleBuilder
      .metadata("DualCase", "Dual case conversion", 1, 0)
      .implementationPure[DualIn, DualOut](in => DualOut(in.text.toUpperCase, in.text.toLowerCase))
      .build

    val dualDag = DagSpec(
      metadata = ComponentMetadata.empty("DualDag"),
      modules = Map(
        modId -> ModuleNodeSpec(
          metadata = ComponentMetadata("DualCase", "Dual case conversion", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("upper" -> CType.CString, "lower" -> CType.CString)
        )
      ),
      data = Map(
        inId -> DataNodeSpec(
          "text",
          Map(inId -> "text", modId -> "text"),
          CType.CString
        ),
        outId1 -> DataNodeSpec("upper", Map(modId -> "upper"), CType.CString),
        outId2 -> DataNodeSpec("lower", Map(modId -> "lower"), CType.CString)
      ),
      inEdges = Set((inId, modId)),
      outEdges = Set((modId, outId1), (modId, outId2)),
      declaredOutputs = List("upper", "lower"),
      outputBindings = Map("upper" -> outId1, "lower" -> outId2)
    )

    val suspended = mkSuspended(dag = dualDag)

    val result = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("text" -> CValue.CString("Hello")),
        modules = Map(modId -> dualModule),
        options = ExecutionOptions(includeResolutionSources = true),
        scheduler = scheduler,
        backends = backends
      )
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.metadata.resolutionSources shouldBe defined

    val sources = result.metadata.resolutionSources.get
    sources("text") shouldBe ResolutionSource.FromInput
    sources("upper") shouldBe ResolutionSource.FromModuleExecution
    sources("lower") shouldBe ResolutionSource.FromModuleExecution
  }
}
