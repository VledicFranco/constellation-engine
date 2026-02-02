package io.constellation

import cats.Eval
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.execution.GlobalScheduler
import io.constellation.spi.ConstellationBackends
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

/** Integration tests for metadata instrumentation (Phase 3).
  *
  * Exercises MetadataBuilder through actual execution paths:
  *   - ConstellationImpl.run (for complete pipelines with timings/provenance)
  *   - SuspendableExecution.resume (for resolution source tracking)
  *   - MetadataBuilder directly (for blocked graph on synthetic suspended state)
  */
class MetadataInstrumentationTest extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // Shared fixture: text -> Uppercase -> output
  // (using same name "text" for both input name and module parameter)
  // ---------------------------------------------------------------------------

  case class TextIn(text: String)
  case class TextOut(result: String)

  private val uppercaseModule: Module.Uninitialized = ModuleBuilder
    .metadata("Uppercase", "Uppercase", 1, 0)
    .implementationPure[TextIn, TextOut](in => TextOut(in.text.toUpperCase))
    .build

  private val moduleId     = UUID.randomUUID()
  private val inputDataId  = UUID.randomUUID()
  private val outputDataId = UUID.randomUUID()

  private val simpleDag = DagSpec(
    metadata = ComponentMetadata.empty("TestDag"),
    modules = Map(
      moduleId -> ModuleNodeSpec(
        metadata = ComponentMetadata("Uppercase", "Uppercase", List.empty, 1, 0),
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
      outputDataId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CString)
    ),
    inEdges = Set((inputDataId, moduleId)),
    outEdges = Set((moduleId, outputDataId)),
    declaredOutputs = List("result"),
    outputBindings = Map("result" -> outputDataId)
  )

  private val modules: Map[UUID, Module.Uninitialized] = Map(moduleId -> uppercaseModule)
  private val fullInputs                               = Map("text" -> CValue.CString("hello"))

  // ---------------------------------------------------------------------------
  // Two-module DAG: text -> Uppercase -> mid -> Exclaim -> result
  // ---------------------------------------------------------------------------

  private val exclaimModule: Module.Uninitialized = ModuleBuilder
    .metadata("Exclaim", "Add exclamation", 1, 0)
    .implementationPure[TextIn, TextOut](in => TextOut(in.text + "!"))
    .build

  private val moduleIdA     = UUID.randomUUID()
  private val moduleIdB     = UUID.randomUUID()
  private val inputDataId2  = UUID.randomUUID()
  private val midDataId     = UUID.randomUUID()
  private val outputDataId2 = UUID.randomUUID()

  private val chainDag = DagSpec(
    metadata = ComponentMetadata.empty("ChainDag"),
    modules = Map(
      moduleIdA -> ModuleNodeSpec(
        metadata = ComponentMetadata("Uppercase", "Uppercase", List.empty, 1, 0),
        consumes = Map("text" -> CType.CString),
        produces = Map("result" -> CType.CString)
      ),
      moduleIdB -> ModuleNodeSpec(
        metadata = ComponentMetadata("Exclaim", "Exclaim", List.empty, 1, 0),
        consumes = Map("text" -> CType.CString),
        produces = Map("result" -> CType.CString)
      )
    ),
    data = Map(
      inputDataId2 -> DataNodeSpec(
        "text",
        Map(inputDataId2 -> "text", moduleIdA -> "text"),
        CType.CString
      ),
      midDataId -> DataNodeSpec(
        "mid",
        Map(moduleIdA -> "result", moduleIdB -> "text"),
        CType.CString
      ),
      outputDataId2 -> DataNodeSpec("result", Map(moduleIdB -> "result"), CType.CString)
    ),
    inEdges = Set((inputDataId2, moduleIdA), (midDataId, moduleIdB)),
    outEdges = Set((moduleIdA, midDataId), (moduleIdB, outputDataId2)),
    declaredOutputs = List("result"),
    outputBindings = Map("result" -> outputDataId2)
  )

  private val chainModules: Map[UUID, Module.Uninitialized] = Map(
    moduleIdA -> uppercaseModule,
    moduleIdB -> exclaimModule
  )

  /** Run a complete pipeline via ConstellationImpl.run with the given options. */
  private def runComplete(options: ExecutionOptions): DataSignature = {
    val image = ProgramImage(
      structuralHash = "test-hash",
      syntacticHash = "test-syntactic",
      dagSpec = simpleDag,
      moduleOptions = Map.empty,
      compiledAt = java.time.Instant.now()
    )
    val loaded = LoadedProgram(image, Map.empty)

    (for {
      constellation <- impl.ConstellationImpl.init
      _             <- constellation.setModule(uppercaseModule)
      result        <- constellation.run(loaded, fullInputs, options)
    } yield result).unsafeRunSync()
  }

  // ---------------------------------------------------------------------------
  // Complete pipeline + includeTimings
  // ---------------------------------------------------------------------------

  "Metadata instrumentation" should "populate nodeTimings on completed pipeline" in {
    val sig = runComplete(ExecutionOptions(includeTimings = true))

    sig.status shouldBe PipelineStatus.Completed
    sig.metadata.nodeTimings shouldBe defined
    val timings = sig.metadata.nodeTimings.get
    timings should contain key "Uppercase"
    timings("Uppercase").toNanos should be > 0L
  }

  // ---------------------------------------------------------------------------
  // Complete pipeline + includeProvenance
  // ---------------------------------------------------------------------------

  it should "populate provenance on completed pipeline" in {
    val sig = runComplete(ExecutionOptions(includeProvenance = true))

    sig.status shouldBe PipelineStatus.Completed
    sig.metadata.provenance shouldBe defined
    val prov = sig.metadata.provenance.get
    prov("text") shouldBe "<input>"
    prov("result") shouldBe "Uppercase"
  }

  // ---------------------------------------------------------------------------
  // Blocked graph via MetadataBuilder on synthetic state
  // ---------------------------------------------------------------------------

  it should "populate blockedGraph for a suspended state" in {
    val suspendedState = Runtime.State(
      processUuid = UUID.randomUUID(),
      dag = simpleDag,
      moduleStatus = Map(moduleId -> Eval.now(Module.Status.Unfired)),
      data = Map.empty
    )

    val metadata = MetadataBuilder.build(
      suspendedState,
      simpleDag,
      ExecutionOptions(includeBlockedGraph = true),
      java.time.Instant.now(),
      java.time.Instant.now(),
      inputNodeNames = Set.empty
    )

    metadata.blockedGraph shouldBe defined
    val blocked = metadata.blockedGraph.get
    blocked should contain key "text"
    blocked("text") should contain("result")
  }

  // ---------------------------------------------------------------------------
  // Resume with resolvedNodes + includeResolutionSources (two-module chain)
  // ---------------------------------------------------------------------------

  it should "classify resolution sources correctly after resume with resolvedNodes" in {
    val suspended = SuspendedExecution(
      executionId = UUID.randomUUID(),
      structuralHash = "test-hash",
      resumptionCount = 0,
      dagSpec = chainDag,
      moduleOptions = Map.empty,
      providedInputs = Map("text" -> CValue.CString("hello")),
      computedValues = Map.empty,
      moduleStatuses = Map.empty
    )

    val sig = SuspendableExecution
      .resume(
        suspended = suspended,
        resolvedNodes = Map("mid" -> CValue.CString("MANUAL")),
        modules = chainModules,
        options = ExecutionOptions(includeResolutionSources = true)
      )
      .unsafeRunSync()

    sig.metadata.resolutionSources shouldBe defined
    val sources = sig.metadata.resolutionSources.get
    sources("text") shouldBe ResolutionSource.FromInput
    sources("mid") shouldBe ResolutionSource.FromManualResolution
    sources("result") shouldBe ResolutionSource.FromModuleExecution
  }

  // ---------------------------------------------------------------------------
  // All flags off -> only baseline metadata
  // ---------------------------------------------------------------------------

  it should "include only baseline metadata when all flags are off" in {
    val sig = runComplete(ExecutionOptions())

    sig.metadata.startedAt shouldBe defined
    sig.metadata.completedAt shouldBe defined
    sig.metadata.totalDuration shouldBe defined
    sig.metadata.nodeTimings shouldBe None
    sig.metadata.provenance shouldBe None
    sig.metadata.blockedGraph shouldBe None
    sig.metadata.resolutionSources shouldBe None
  }
}
