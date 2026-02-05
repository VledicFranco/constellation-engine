package io.constellation

import java.time.Instant
import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PipelineImageTest extends AnyFlatSpec with Matchers {

  private def simpleDagSpec: DagSpec = {
    val moduleId = UUID.randomUUID()
    val inputId  = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata.empty("TestModule"),
          consumes = Map("input" -> CType.CString),
          produces = Map("out" -> CType.CString)
        )
      ),
      data = Map(
        inputId  -> DataNodeSpec("input", Map(moduleId -> "input"), CType.CString),
        outputId -> DataNodeSpec("output", Map(moduleId -> "out"), CType.CString)
      ),
      inEdges = Set((inputId, moduleId)),
      outEdges = Set((moduleId, outputId)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputId)
    )
  }

  "PipelineImage.computeStructuralHash" should "delegate to ContentHash" in {
    val dag  = simpleDagSpec
    val hash = PipelineImage.computeStructuralHash(dag)
    hash shouldBe ContentHash.computeStructuralHash(dag)
  }

  "PipelineImage.rehydrate" should "produce a LoadedPipeline" in {
    val dag = simpleDagSpec
    val image = PipelineImage(
      structuralHash = PipelineImage.computeStructuralHash(dag),
      syntacticHash = "",
      dagSpec = dag,
      moduleOptions = Map.empty,
      compiledAt = Instant.now()
    )

    val loaded = PipelineImage.rehydrate(image)
    loaded.image shouldBe image
    loaded.structuralHash shouldBe image.structuralHash
    // No branch modules in this simple DAG, so synthetic should be empty
    loaded.syntheticModules shouldBe empty
  }

  "structural hash" should "round-trip through PipelineImage" in {
    val dag  = simpleDagSpec
    val hash = PipelineImage.computeStructuralHash(dag)
    val image = PipelineImage(
      structuralHash = hash,
      syntacticHash = "",
      dagSpec = dag,
      moduleOptions = Map.empty,
      compiledAt = Instant.now()
    )
    image.structuralHash shouldBe hash
    PipelineImage.computeStructuralHash(image.dagSpec) shouldBe hash
  }
}
