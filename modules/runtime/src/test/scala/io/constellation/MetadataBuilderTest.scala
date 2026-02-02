package io.constellation

import cats.Eval
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.{Duration, Instant}
import java.util.UUID
import scala.concurrent.duration.*

class MetadataBuilderTest extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // Test helpers
  // ---------------------------------------------------------------------------

  private val moduleId1    = UUID.randomUUID()
  private val moduleId2    = UUID.randomUUID()
  private val inputDataId  = UUID.randomUUID()
  private val midDataId    = UUID.randomUUID()
  private val outputDataId = UUID.randomUUID()

  /** A simple linear DAG: input -> module1 -> mid -> module2 -> output */
  private val linearDag = DagSpec(
    metadata = ComponentMetadata.empty("TestDag"),
    modules = Map(
      moduleId1 -> ModuleNodeSpec(
        metadata = ComponentMetadata("ModuleA", "First module", List.empty, 1, 0),
        consumes = Map("text" -> CType.CString),
        produces = Map("result" -> CType.CString)
      ),
      moduleId2 -> ModuleNodeSpec(
        metadata = ComponentMetadata("ModuleB", "Second module", List.empty, 1, 0),
        consumes = Map("text" -> CType.CString),
        produces = Map("result" -> CType.CString)
      )
    ),
    data = Map(
      inputDataId  -> DataNodeSpec("input", Map(moduleId1 -> "text"), CType.CString),
      midDataId    -> DataNodeSpec("mid", Map(moduleId2 -> "text"), CType.CString),
      outputDataId -> DataNodeSpec("output", Map.empty, CType.CString)
    ),
    inEdges = Set((inputDataId, moduleId1), (midDataId, moduleId2)),
    outEdges = Set((moduleId1, midDataId), (moduleId2, outputDataId)),
    declaredOutputs = List("output")
  )

  private val startedAt   = Instant.parse("2026-01-01T00:00:00Z")
  private val completedAt = Instant.parse("2026-01-01T00:00:01Z")

  private def allFlagsOn: ExecutionOptions = ExecutionOptions(
    includeTimings = true,
    includeProvenance = true,
    includeBlockedGraph = true,
    includeResolutionSources = true
  )

  private def allFlagsOff: ExecutionOptions = ExecutionOptions()

  /** State where all modules fired and all data computed. */
  private def completedState: Runtime.State = Runtime.State(
    processUuid = UUID.randomUUID(),
    dag = linearDag,
    moduleStatus = Map(
      moduleId1 -> Eval.now(Module.Status.Fired(100.millis)),
      moduleId2 -> Eval.now(Module.Status.Fired(200.millis))
    ),
    data = Map(
      inputDataId  -> Eval.now(CValue.CString("hello")),
      midDataId    -> Eval.now(CValue.CString("HELLO")),
      outputDataId -> Eval.now(CValue.CString("HELLO!"))
    )
  )

  /** State where only input is provided (suspended). */
  private def suspendedState: Runtime.State = Runtime.State(
    processUuid = UUID.randomUUID(),
    dag = linearDag,
    moduleStatus = Map(
      moduleId1 -> Eval.now(Module.Status.Unfired),
      moduleId2 -> Eval.now(Module.Status.Unfired)
    ),
    data = Map.empty
  )

  // ---------------------------------------------------------------------------
  // Baseline metadata
  // ---------------------------------------------------------------------------

  "MetadataBuilder" should "always include startedAt, completedAt, and totalDuration" in {
    val metadata = MetadataBuilder.build(
      completedState,
      linearDag,
      allFlagsOff,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )

    metadata.startedAt shouldBe Some(startedAt)
    metadata.completedAt shouldBe Some(completedAt)
    metadata.totalDuration shouldBe Some(Duration.between(startedAt, completedAt))
  }

  // ---------------------------------------------------------------------------
  // nodeTimings
  // ---------------------------------------------------------------------------

  it should "return None for nodeTimings when flag is off" in {
    val metadata = MetadataBuilder.build(
      completedState,
      linearDag,
      allFlagsOff,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )

    metadata.nodeTimings shouldBe None
  }

  it should "populate nodeTimings from Fired module statuses" in {
    val metadata = MetadataBuilder.build(
      completedState,
      linearDag,
      allFlagsOn,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )

    val timings = metadata.nodeTimings.get
    timings should have size 2
    timings("ModuleA") shouldBe Duration.ofMillis(100)
    timings("ModuleB") shouldBe Duration.ofMillis(200)
  }

  it should "exclude Unfired modules from nodeTimings" in {
    val partialState = completedState.copy(
      moduleStatus = Map(
        moduleId1 -> Eval.now(Module.Status.Fired(100.millis)),
        moduleId2 -> Eval.now(Module.Status.Unfired)
      )
    )

    val metadata = MetadataBuilder.build(
      partialState,
      linearDag,
      allFlagsOn,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )

    val timings = metadata.nodeTimings.get
    timings should have size 1
    timings should contain key "ModuleA"
    timings should not contain key("ModuleB")
  }

  // ---------------------------------------------------------------------------
  // provenance
  // ---------------------------------------------------------------------------

  it should "return None for provenance when flag is off" in {
    val metadata = MetadataBuilder.build(
      completedState,
      linearDag,
      allFlagsOff,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )

    metadata.provenance shouldBe None
  }

  it should "classify input nodes as '<input>' in provenance" in {
    val metadata = MetadataBuilder.build(
      completedState,
      linearDag,
      allFlagsOn,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )

    val prov = metadata.provenance.get
    prov("input") shouldBe "<input>"
  }

  it should "classify module-produced nodes by module name in provenance" in {
    val metadata = MetadataBuilder.build(
      completedState,
      linearDag,
      allFlagsOn,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )

    val prov = metadata.provenance.get
    prov("mid") shouldBe "ModuleA"
    prov("output") shouldBe "ModuleB"
  }

  it should "classify inline transform nodes as '<inline-transform>' in provenance" in {
    val transformDataId = UUID.randomUUID()
    val dagWithTransform = linearDag.copy(
      data = linearDag.data + (transformDataId -> DataNodeSpec(
        "transformed",
        Map.empty,
        CType.CString,
        inlineTransform = Some(InlineTransform.NotTransform),
        transformInputs = Map("text" -> inputDataId)
      ))
    )

    val stateWithTransform = completedState.copy(
      data = completedState.data + (transformDataId -> Eval.now(CValue.CString("HELLO")))
    )

    val metadata = MetadataBuilder.build(
      stateWithTransform,
      dagWithTransform,
      allFlagsOn,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )

    val prov = metadata.provenance.get
    prov("transformed") shouldBe "<inline-transform>"
  }

  // ---------------------------------------------------------------------------
  // blockedGraph
  // ---------------------------------------------------------------------------

  it should "return None for blockedGraph when flag is off" in {
    val metadata = MetadataBuilder.build(
      suspendedState,
      linearDag,
      allFlagsOff,
      startedAt,
      completedAt,
      inputNodeNames = Set.empty
    )

    metadata.blockedGraph shouldBe None
  }

  it should "build blockedGraph for suspended execution with missing inputs" in {
    val metadata = MetadataBuilder.build(
      suspendedState,
      linearDag,
      allFlagsOn,
      startedAt,
      completedAt,
      inputNodeNames = Set.empty
    )

    val blocked = metadata.blockedGraph.get
    blocked should contain key "input"
    // Missing "input" blocks mid and output transitively
    blocked("input") should contain allOf ("mid", "output")
  }

  it should "return empty blockedGraph for completed execution" in {
    val metadata = MetadataBuilder.build(
      completedState,
      linearDag,
      allFlagsOn,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )

    val blocked = metadata.blockedGraph.get
    blocked shouldBe empty
  }

  // ---------------------------------------------------------------------------
  // resolutionSources
  // ---------------------------------------------------------------------------

  it should "return None for resolutionSources when flag is off" in {
    val metadata = MetadataBuilder.build(
      completedState,
      linearDag,
      allFlagsOff,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )

    metadata.resolutionSources shouldBe None
  }

  it should "classify resolution sources correctly (input, module, manual)" in {
    val metadata = MetadataBuilder.build(
      completedState,
      linearDag,
      allFlagsOn,
      startedAt,
      completedAt,
      inputNodeNames = Set("input"),
      resolvedNodeNames = Set("mid")
    )

    val sources = metadata.resolutionSources.get
    sources("input") shouldBe ResolutionSource.FromInput
    sources("mid") shouldBe ResolutionSource.FromManualResolution
    sources("output") shouldBe ResolutionSource.FromModuleExecution
  }

  // ---------------------------------------------------------------------------
  // Flag independence
  // ---------------------------------------------------------------------------

  it should "populate each field independently based on its own flag" in {
    val timingsOnly = ExecutionOptions(includeTimings = true)
    val m1 = MetadataBuilder.build(
      completedState,
      linearDag,
      timingsOnly,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )
    m1.nodeTimings shouldBe defined
    m1.provenance shouldBe None
    m1.blockedGraph shouldBe None
    m1.resolutionSources shouldBe None

    val provenanceOnly = ExecutionOptions(includeProvenance = true)
    val m2 = MetadataBuilder.build(
      completedState,
      linearDag,
      provenanceOnly,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )
    m2.nodeTimings shouldBe None
    m2.provenance shouldBe defined
    m2.blockedGraph shouldBe None
    m2.resolutionSources shouldBe None

    val blockedOnly = ExecutionOptions(includeBlockedGraph = true)
    val m3 = MetadataBuilder.build(
      completedState,
      linearDag,
      blockedOnly,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )
    m3.nodeTimings shouldBe None
    m3.provenance shouldBe None
    m3.blockedGraph shouldBe defined
    m3.resolutionSources shouldBe None

    val resolutionOnly = ExecutionOptions(includeResolutionSources = true)
    val m4 = MetadataBuilder.build(
      completedState,
      linearDag,
      resolutionOnly,
      startedAt,
      completedAt,
      inputNodeNames = Set("input")
    )
    m4.nodeTimings shouldBe None
    m4.provenance shouldBe None
    m4.blockedGraph shouldBe None
    m4.resolutionSources shouldBe defined
  }
}
