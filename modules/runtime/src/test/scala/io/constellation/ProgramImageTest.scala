package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID

class ProgramImageTest extends AnyFlatSpec with Matchers {

  private def simpleDagSpec: DagSpec = {
    val moduleId = UUID.randomUUID()
    val inputId = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> ModuleNodeSpec(
        metadata = ComponentMetadata.empty("TestModule"),
        consumes = Map("input" -> CType.CString),
        produces = Map("out" -> CType.CString)
      )),
      data = Map(
        inputId -> DataNodeSpec("input", Map(moduleId -> "input"), CType.CString),
        outputId -> DataNodeSpec("output", Map(moduleId -> "out"), CType.CString)
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, outputId)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputId)
    )
  }

  "ProgramImage.computeStructuralHash" should "delegate to ContentHash" in {
    val dag = simpleDagSpec
    val hash = ProgramImage.computeStructuralHash(dag)
    hash shouldBe ContentHash.computeStructuralHash(dag)
  }

  "ProgramImage.rehydrate" should "produce a LoadedProgram" in {
    val dag = simpleDagSpec
    val image = ProgramImage(
      structuralHash = ProgramImage.computeStructuralHash(dag),
      syntacticHash = "",
      dagSpec = dag,
      moduleOptions = Map.empty,
      compiledAt = Instant.now()
    )

    val loaded = ProgramImage.rehydrate(image)
    loaded.image shouldBe image
    loaded.structuralHash shouldBe image.structuralHash
    // No branch modules in this simple DAG, so synthetic should be empty
    loaded.syntheticModules shouldBe empty
  }

  "structural hash" should "round-trip through ProgramImage" in {
    val dag = simpleDagSpec
    val hash = ProgramImage.computeStructuralHash(dag)
    val image = ProgramImage(
      structuralHash = hash,
      syntacticHash = "",
      dagSpec = dag,
      moduleOptions = Map.empty,
      compiledAt = Instant.now()
    )
    image.structuralHash shouldBe hash
    ProgramImage.computeStructuralHash(image.dagSpec) shouldBe hash
  }
}
