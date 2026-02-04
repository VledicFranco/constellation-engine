package io.constellation

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.impl.ConstellationImpl
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID

class ConstellationTest extends AnyFlatSpec with Matchers {

  case class SingleInput(text: String)
  case class SingleOutput(result: String)

  case class IntInput(x: Long)
  case class IntOutput(result: Long)

  private def createUppercaseModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Uppercase", "Converts text to uppercase", 1, 0)
      .implementationPure[SingleInput, SingleOutput](in => SingleOutput(in.text.toUpperCase))
      .build

  private def createDoubleModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Double", "Doubles a number", 1, 0)
      .implementationPure[IntInput, IntOutput](in => IntOutput(in.x * 2))
      .build

  /** Helper to build a LoadedPipeline from a hand-built DagSpec */
  private def loadedFromDag(
      dag: DagSpec,
      syntheticModules: Map[UUID, Module.Uninitialized] = Map.empty
  ): LoadedPipeline = {
    val structuralHash = PipelineImage.computeStructuralHash(dag)
    val image = PipelineImage(
      structuralHash = structuralHash,
      syntacticHash = "",
      dagSpec = dag,
      moduleOptions = Map.empty,
      compiledAt = Instant.now()
    )
    LoadedPipeline(image, syntheticModules)
  }

  "Constellation" should "initialize successfully" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation shouldBe a[ConstellationImpl]
  }

  // Module operations
  "getModules" should "return empty list initially" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val modules       = constellation.getModules.unsafeRunSync()
    modules shouldBe empty
  }

  "setModule" should "register a module" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val module        = createUppercaseModule()

    constellation.setModule(module).unsafeRunSync()

    val modules = constellation.getModules.unsafeRunSync()
    modules.map(_.name) should contain("Uppercase")
  }

  it should "allow registering multiple modules" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    constellation.setModule(createUppercaseModule()).unsafeRunSync()
    constellation.setModule(createDoubleModule()).unsafeRunSync()

    val modules = constellation.getModules.unsafeRunSync()
    modules.map(_.name) should contain allOf ("Uppercase", "Double")
  }

  "getModuleByName" should "retrieve a registered module" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val module        = createUppercaseModule()
    constellation.setModule(module).unsafeRunSync()

    val retrieved = constellation.getModuleByName("Uppercase").unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.spec.name shouldBe "Uppercase"
  }

  it should "return None for non-existent module" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val retrieved = constellation.getModuleByName("NonExistent").unsafeRunSync()
    retrieved shouldBe None
  }

  // DAG execution via LoadedPipeline
  "run" should "execute a simple DAG" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    // Register the module
    constellation.setModule(createUppercaseModule()).unsafeRunSync()

    // Build a simple DAG
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("SimpleDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          "input",
          Map(inputDataId -> "input", moduleId -> "text"),
          CType.CString
        ),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputDataId)
    )

    val loaded = loadedFromDag(dag)
    val inputs = Map("input" -> CValue.CString("hello"))
    val sig    = constellation.run(loaded, inputs).unsafeRunSync()

    sig.outputs.get("output") shouldBe Some(CValue.CString("HELLO"))
  }

  it should "fail for non-existent program ref" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val inputs = Map("input" -> CValue.CString("test"))
    val result =
      constellation.run("NonExistent", inputs, ExecutionOptions()).attempt.unsafeRunSync()

    result.isLeft shouldBe true
    result.left.exists(_.getMessage.contains("not found")) shouldBe true
  }

  it should "execute a DAG spec directly via LoadedPipeline" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    // Register the module
    constellation.setModule(createDoubleModule()).unsafeRunSync()

    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("DirectDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Double", "Test", List.empty, 1, 0),
          consumes = Map("x" -> CType.CInt),
          produces = Map("result" -> CType.CInt)
        )
      ),
      data = Map(
        inputDataId  -> DataNodeSpec("x", Map(inputDataId -> "x", moduleId -> "x"), CType.CInt),
        outputDataId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CInt)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> outputDataId)
    )

    val loaded = loadedFromDag(dag)
    val inputs = Map("x" -> CValue.CInt(21))
    val sig    = constellation.run(loaded, inputs).unsafeRunSync()

    sig.outputs.get("result") shouldBe Some(CValue.CInt(42))
  }

  it should "execute with pre-resolved synthetic modules" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("PreResolvedDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
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

    // Pass modules as synthetic modules in LoadedPipeline
    val syntheticModules = Map(moduleId -> createUppercaseModule())
    val loaded           = loadedFromDag(dag, syntheticModules)

    val inputs = Map("text" -> CValue.CString("world"))
    val sig    = constellation.run(loaded, inputs).unsafeRunSync()

    sig.outputs.get("result") shouldBe Some(CValue.CString("WORLD"))
  }

  // Input validation
  "run" should "fail on unexpected input name" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createUppercaseModule()).unsafeRunSync()

    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    // DAG expects input named "expectedInput"
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ValidationDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          "expectedInput",
          Map(inputDataId -> "expectedInput", moduleId -> "text"),
          CType.CString
        ),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    val loaded = loadedFromDag(dag)

    // Use wrong input name - should fail because "wrongName" is not a valid input
    val inputs = Map("wrongName" -> CValue.CString("test"))
    val result = constellation.run(loaded, inputs).attempt.unsafeRunSync()

    result.isLeft shouldBe true
  }

  it should "fail on wrong input type" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createUppercaseModule()).unsafeRunSync()

    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TypeDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
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
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    val loaded = loadedFromDag(dag)

    // Use wrong input type (Int instead of String)
    val inputs = Map("text" -> CValue.CInt(123))
    val result = constellation.run(loaded, inputs).attempt.unsafeRunSync()

    result.isLeft shouldBe true
    result.left.exists(_.getMessage.contains("different type")) shouldBe true
  }

  // Module status tracking via DataSignature metadata
  "run" should "complete successfully and report status" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createUppercaseModule()).unsafeRunSync()

    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("StatusDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
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

    val loaded = loadedFromDag(dag)
    val inputs = Map("text" -> CValue.CString("test"))
    val sig    = constellation.run(loaded, inputs).unsafeRunSync()

    sig.status shouldBe PipelineStatus.Completed
    sig.outputs.get("result") shouldBe Some(CValue.CString("TEST"))
  }
}
