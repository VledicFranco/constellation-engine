package io.constellation

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import cats.Eval

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MetadataBuilderTest extends AnyFlatSpec with Matchers {

  private val startedAt = Instant.parse("2025-01-01T00:00:00Z")
  private val completedAt = Instant.parse("2025-01-01T00:00:05Z")

  // ===== Basic build with no options =====

  "MetadataBuilder.build" should "return metadata with timestamps and no optional fields" in {
    val dag = DagSpec.empty("TestDag")
    val state = Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
    val options = ExecutionOptions()

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set.empty)

    result.startedAt shouldBe Some(startedAt)
    result.completedAt shouldBe Some(completedAt)
    result.totalDuration shouldBe defined
    result.totalDuration.get.getSeconds shouldBe 5L
    result.nodeTimings shouldBe None
    result.provenance shouldBe None
    result.blockedGraph shouldBe None
    result.resolutionSources shouldBe None
  }

  // ===== Node Timings =====

  it should "include node timings when includeTimings is true" in {
    val moduleId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Uppercase module", List.empty, 1, 0)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )
    val state = Runtime.State(
      UUID.randomUUID(),
      dag,
      Map(moduleId -> Eval.now(Module.Status.Fired(100.millis))),
      Map.empty
    )
    val options = ExecutionOptions(includeTimings = true)

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set.empty)

    result.nodeTimings shouldBe defined
    result.nodeTimings.get should contain key "Uppercase"
    result.nodeTimings.get("Uppercase").toMillis shouldBe 100L
  }

  it should "skip unfired modules in timings" in {
    val firedId = UUID.randomUUID()
    val unfiredId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        firedId -> ModuleNodeSpec(metadata = ComponentMetadata("Fired", "Fired", List.empty, 1, 0)),
        unfiredId -> ModuleNodeSpec(metadata = ComponentMetadata("Unfired", "Unfired", List.empty, 1, 0))
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )
    val state = Runtime.State(
      UUID.randomUUID(),
      dag,
      Map(
        firedId -> Eval.now(Module.Status.Fired(50.millis)),
        unfiredId -> Eval.now(Module.Status.Unfired)
      ),
      Map.empty
    )
    val options = ExecutionOptions(includeTimings = true)

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set.empty)

    result.nodeTimings.get should have size 1
    result.nodeTimings.get should contain key "Fired"
  }

  it should "skip failed modules in timings" in {
    val failedId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        failedId -> ModuleNodeSpec(metadata = ComponentMetadata("Failed", "Failed", List.empty, 1, 0))
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )
    val state = Runtime.State(
      UUID.randomUUID(),
      dag,
      Map(failedId -> Eval.now(Module.Status.Failed(new RuntimeException("err")))),
      Map.empty
    )
    val options = ExecutionOptions(includeTimings = true)

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set.empty)

    result.nodeTimings.get shouldBe empty
  }

  // ===== Provenance =====

  it should "include provenance when includeProvenance is true" in {
    val moduleId = UUID.randomUUID()
    val inputId = UUID.randomUUID()
    val outputId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Uppercase", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputId -> DataNodeSpec("text", Map(moduleId -> "text"), CType.CString),
        outputId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, outputId))
    )
    val state = Runtime.State(
      UUID.randomUUID(),
      dag,
      Map(moduleId -> Eval.now(Module.Status.Fired(10.millis))),
      Map(
        inputId -> Eval.now(CValue.CString("hello")),
        outputId -> Eval.now(CValue.CString("HELLO"))
      )
    )
    val options = ExecutionOptions(includeProvenance = true)

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set("text"))

    result.provenance shouldBe defined
    result.provenance.get("text") shouldBe "<input>"
    result.provenance.get("result") shouldBe "Uppercase"
  }

  it should "mark inline transform data nodes in provenance" in {
    val inputId = UUID.randomUUID()
    val transformId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map.empty,
      data = Map(
        inputId -> DataNodeSpec("input", Map.empty, CType.CInt),
        transformId -> DataNodeSpec("doubled", Map.empty, CType.CInt,
          inlineTransform = Some(InlineTransform.MergeTransform(CType.CInt, CType.CInt)),
          transformInputs = Map("left" -> inputId)
        )
      ),
      inEdges = Set.empty,
      outEdges = Set.empty
    )
    val state = Runtime.State(
      UUID.randomUUID(),
      dag,
      Map.empty,
      Map(
        inputId -> Eval.now(CValue.CInt(5L)),
        transformId -> Eval.now(CValue.CInt(10L))
      )
    )
    val options = ExecutionOptions(includeProvenance = true)

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set("input"))

    result.provenance shouldBe defined
    result.provenance.get("input") shouldBe "<input>"
    result.provenance.get("doubled") shouldBe "<inline-transform>"
  }

  // ===== Blocked Graph =====

  it should "include blocked graph when includeBlockedGraph is true" in {
    val moduleId = UUID.randomUUID()
    val inputId = UUID.randomUUID()
    val outputId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Mod", "Mod", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        )
      ),
      data = Map(
        inputId -> DataNodeSpec("x", Map(moduleId -> "x"), CType.CInt),
        outputId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CInt)
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, outputId))
    )
    // State with no data computed (simulating missing input)
    val state = Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
    val options = ExecutionOptions(includeBlockedGraph = true)

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set.empty)

    result.blockedGraph shouldBe defined
    result.blockedGraph.get should contain key "x"
    result.blockedGraph.get("x") should contain("result")
  }

  it should "return empty blocked graph when all inputs are provided" in {
    val moduleId = UUID.randomUUID()
    val inputId = UUID.randomUUID()
    val outputId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Mod", "Mod", List.empty, 1, 0)
        )
      ),
      data = Map(
        inputId -> DataNodeSpec("x", Map(moduleId -> "x"), CType.CInt),
        outputId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CInt)
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, outputId))
    )
    // All data nodes computed
    val state = Runtime.State(
      UUID.randomUUID(), dag, Map.empty,
      Map(
        inputId -> Eval.now(CValue.CInt(1L)),
        outputId -> Eval.now(CValue.CInt(2L))
      )
    )
    val options = ExecutionOptions(includeBlockedGraph = true)

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set("x"))

    result.blockedGraph shouldBe defined
    result.blockedGraph.get shouldBe empty
  }

  it should "compute transitive blocked graph through multiple modules" in {
    val mod1Id = UUID.randomUUID()
    val mod2Id = UUID.randomUUID()
    val inputId = UUID.randomUUID()
    val midId = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ChainDag"),
      modules = Map(
        mod1Id -> ModuleNodeSpec(metadata = ComponentMetadata("Mod1", "Mod1", List.empty, 1, 0)),
        mod2Id -> ModuleNodeSpec(metadata = ComponentMetadata("Mod2", "Mod2", List.empty, 1, 0))
      ),
      data = Map(
        inputId -> DataNodeSpec("input", Map(mod1Id -> "x"), CType.CInt),
        midId -> DataNodeSpec("mid", Map(mod1Id -> "result", mod2Id -> "x"), CType.CInt),
        outputId -> DataNodeSpec("output", Map(mod2Id -> "result"), CType.CInt)
      ),
      inEdges = Set((inputId, mod1Id), (midId, mod2Id)),
      outEdges = Set((mod1Id, midId), (mod2Id, outputId))
    )
    // No data computed - input is missing
    val state = Runtime.State(UUID.randomUUID(), dag, Map.empty, Map.empty)
    val options = ExecutionOptions(includeBlockedGraph = true)

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set.empty)

    result.blockedGraph shouldBe defined
    result.blockedGraph.get should contain key "input"
    // "input" being missing blocks "mid" and "output" transitively
    result.blockedGraph.get("input") should contain("mid")
    result.blockedGraph.get("input") should contain("output")
  }

  // ===== Resolution Sources =====

  it should "include resolution sources when includeResolutionSources is true" in {
    val moduleId = UUID.randomUUID()
    val inputId = UUID.randomUUID()
    val outputId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(metadata = ComponentMetadata("Mod", "Mod", List.empty, 1, 0))
      ),
      data = Map(
        inputId -> DataNodeSpec("x", Map(moduleId -> "x"), CType.CInt),
        outputId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CInt)
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, outputId))
    )
    val state = Runtime.State(
      UUID.randomUUID(), dag, Map.empty,
      Map(
        inputId -> Eval.now(CValue.CInt(1L)),
        outputId -> Eval.now(CValue.CInt(2L))
      )
    )
    val options = ExecutionOptions(includeResolutionSources = true)

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set("x"))

    result.resolutionSources shouldBe defined
    result.resolutionSources.get("x") shouldBe ResolutionSource.FromInput
    result.resolutionSources.get("result") shouldBe ResolutionSource.FromModuleExecution
  }

  it should "classify manually resolved nodes" in {
    val inputId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map.empty,
      data = Map(
        inputId -> DataNodeSpec("x", Map.empty, CType.CInt)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty
    )
    val state = Runtime.State(
      UUID.randomUUID(), dag, Map.empty,
      Map(inputId -> Eval.now(CValue.CInt(1L)))
    )
    val options = ExecutionOptions(includeResolutionSources = true)

    val result = MetadataBuilder.build(
      state, dag, options, startedAt, completedAt,
      inputNodeNames = Set.empty,
      resolvedNodeNames = Set("x")
    )

    result.resolutionSources shouldBe defined
    result.resolutionSources.get("x") shouldBe ResolutionSource.FromManualResolution
  }

  // ===== All options enabled =====

  it should "include all metadata when all options are enabled" in {
    val moduleId = UUID.randomUUID()
    val inputId = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("FullDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Mod", "Mod", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        )
      ),
      data = Map(
        inputId -> DataNodeSpec("x", Map(moduleId -> "x"), CType.CInt),
        outputId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CInt)
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, outputId))
    )
    val state = Runtime.State(
      UUID.randomUUID(), dag,
      Map(moduleId -> Eval.now(Module.Status.Fired(50.millis))),
      Map(
        inputId -> Eval.now(CValue.CInt(5L)),
        outputId -> Eval.now(CValue.CInt(10L))
      )
    )
    val options = ExecutionOptions(
      includeTimings = true,
      includeProvenance = true,
      includeBlockedGraph = true,
      includeResolutionSources = true
    )

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set("x"))

    result.nodeTimings shouldBe defined
    result.provenance shouldBe defined
    result.blockedGraph shouldBe defined
    result.resolutionSources shouldBe defined

    result.nodeTimings.get should have size 1
    result.provenance.get("x") shouldBe "<input>"
    result.provenance.get("result") shouldBe "Mod"
    result.blockedGraph.get shouldBe empty // All inputs provided
    result.resolutionSources.get("x") shouldBe ResolutionSource.FromInput
    result.resolutionSources.get("result") shouldBe ResolutionSource.FromModuleExecution
  }

  // ===== Timed module in timings =====

  it should "skip timed-out modules in timings" in {
    val timedId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        timedId -> ModuleNodeSpec(metadata = ComponentMetadata("Timed", "Timed", List.empty, 1, 0))
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )
    val state = Runtime.State(
      UUID.randomUUID(), dag,
      Map(timedId -> Eval.now(Module.Status.Timed(5.seconds))),
      Map.empty
    )
    val options = ExecutionOptions(includeTimings = true)

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set.empty)

    result.nodeTimings.get shouldBe empty
  }

  // ===== Provenance with unknown source =====

  it should "mark data node as unknown when not input and not produced by module" in {
    val dataId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map.empty,
      data = Map(
        dataId -> DataNodeSpec("orphan", Map.empty, CType.CInt)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty
    )
    // dataId has computed value but is not an input, not an inline transform, and not produced by any module
    val state = Runtime.State(
      UUID.randomUUID(), dag, Map.empty,
      Map(dataId -> Eval.now(CValue.CInt(1L)))
    )
    val options = ExecutionOptions(includeProvenance = true)

    val result = MetadataBuilder.build(state, dag, options, startedAt, completedAt, Set.empty)

    result.provenance shouldBe defined
    // This orphan is a top-level node with no inline transform, so it's a "user input" by definition
    // But the userInputDataNodes set is based on structural analysis, not inputNodeNames param
    // The provenance logic checks: is it in inputDataUuids (userInputDataNodes.keySet)? YES -> "<input>"
    // So it should be "<input>" not "<unknown>"
    result.provenance.get("orphan") shouldBe "<input>"
  }
}
