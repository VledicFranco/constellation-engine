package io.constellation

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.impl.ConstellationImpl
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

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

  "Constellation" should "initialize successfully" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation shouldBe a[ConstellationImpl]
  }

  // Module operations
  "getModules" should "return empty list initially" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val modules = constellation.getModules.unsafeRunSync()
    modules shouldBe empty
  }

  "setModule" should "register a module" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val module = createUppercaseModule()

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
    val module = createUppercaseModule()
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

  // DAG operations
  "createDag" should "create a new empty DAG" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val result = constellation.createDag("TestDag").unsafeRunSync()
    result shouldBe defined
    result.get.name shouldBe "TestDag"
  }

  it should "return None if DAG already exists" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    constellation.createDag("TestDag").unsafeRunSync()
    val result = constellation.createDag("TestDag").unsafeRunSync()
    result shouldBe None
  }

  "dagExists" should "return false for non-existent DAG" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    constellation.dagExists("NonExistent").unsafeRunSync() shouldBe false
  }

  it should "return true for existing DAG" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    constellation.createDag("ExistingDag").unsafeRunSync()
    constellation.dagExists("ExistingDag").unsafeRunSync() shouldBe true
  }

  "setDag" should "register a DAG" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val dag = DagSpec.empty("TestDag")

    constellation.setDag("TestDag", dag).unsafeRunSync()

    constellation.dagExists("TestDag").unsafeRunSync() shouldBe true
  }

  it should "overwrite existing DAG" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val dag1 = DagSpec(
      metadata = ComponentMetadata("TestDag", "First version", List.empty, 1, 0),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )
    val dag2 = DagSpec(
      metadata = ComponentMetadata("TestDag", "Second version", List.empty, 2, 0),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    constellation.setDag("TestDag", dag1).unsafeRunSync()
    constellation.setDag("TestDag", dag2).unsafeRunSync()

    val retrieved = constellation.getDag("TestDag").unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.description shouldBe "Second version"
  }

  "listDags" should "return empty map initially" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val dags = constellation.listDags.unsafeRunSync()
    dags shouldBe empty
  }

  it should "list all registered DAGs" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    constellation.createDag("Dag1").unsafeRunSync()
    constellation.createDag("Dag2").unsafeRunSync()
    constellation.createDag("Dag3").unsafeRunSync()

    val dags = constellation.listDags.unsafeRunSync()
    dags.size shouldBe 3
    dags.keys should contain allOf ("Dag1", "Dag2", "Dag3")
  }

  "getDag" should "retrieve a registered DAG" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val dag = DagSpec(
      metadata = ComponentMetadata("TestDag", "Test description", List("test"), 1, 0),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    constellation.setDag("TestDag", dag).unsafeRunSync()

    val retrieved = constellation.getDag("TestDag").unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.name shouldBe "TestDag"
    retrieved.get.description shouldBe "Test description"
  }

  it should "return None for non-existent DAG" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val retrieved = constellation.getDag("NonExistent").unsafeRunSync()
    retrieved shouldBe None
  }

  // DAG execution
  "runDag" should "execute a simple DAG" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    // Register the module
    constellation.setModule(createUppercaseModule()).unsafeRunSync()

    // Build a simple DAG
    val moduleId = UUID.randomUUID()
    val inputDataId = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    // Input data nodes need the public input name in their nicknames
    // Using the data node's own UUID as the key for public input name
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
        inputDataId -> DataNodeSpec("input", Map(inputDataId -> "input", moduleId -> "text"), CType.CString),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputDataId)
    )

    constellation.setDag("SimpleDag", dag).unsafeRunSync()

    val inputs = Map("input" -> CValue.CString("hello"))
    val state = constellation.runDag("SimpleDag", inputs).unsafeRunSync()

    state.data.get(outputDataId) shouldBe defined
    state.data(outputDataId).value shouldBe CValue.CString("HELLO")
  }

  it should "fail for non-existent DAG" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val inputs = Map("input" -> CValue.CString("test"))
    val result = constellation.runDag("NonExistent", inputs).attempt.unsafeRunSync()

    result.isLeft shouldBe true
    result.left.exists(_.getMessage.contains("not found")) shouldBe true
  }

  "runDagSpec" should "execute a DAG spec directly" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    // Register the module
    constellation.setModule(createDoubleModule()).unsafeRunSync()

    val moduleId = UUID.randomUUID()
    val inputDataId = UUID.randomUUID()
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
        inputDataId -> DataNodeSpec("x", Map(inputDataId -> "x", moduleId -> "x"), CType.CInt),
        outputDataId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CInt)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> outputDataId)
    )

    val inputs = Map("x" -> CValue.CInt(21))
    val state = constellation.runDagSpec(dag, inputs).unsafeRunSync()

    state.data.get(outputDataId) shouldBe defined
    state.data(outputDataId).value shouldBe CValue.CInt(42)
  }

  "runDagWithModules" should "execute with pre-resolved modules" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val moduleId = UUID.randomUUID()
    val inputDataId = UUID.randomUUID()
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
        inputDataId -> DataNodeSpec("text", Map(inputDataId -> "text", moduleId -> "text"), CType.CString),
        outputDataId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> outputDataId)
    )

    // Pass modules directly
    val modules = Map(moduleId -> createUppercaseModule())

    val inputs = Map("text" -> CValue.CString("world"))
    val state = constellation.runDagWithModules(dag, inputs, modules).unsafeRunSync()

    state.data.get(outputDataId) shouldBe defined
    state.data(outputDataId).value shouldBe CValue.CString("WORLD")
  }

  // Input validation
  "runDag" should "fail on unexpected input name" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createUppercaseModule()).unsafeRunSync()

    val moduleId = UUID.randomUUID()
    val inputDataId = UUID.randomUUID()
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
        inputDataId -> DataNodeSpec("expectedInput", Map(inputDataId -> "expectedInput", moduleId -> "text"), CType.CString),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    constellation.setDag("ValidationDag", dag).unsafeRunSync()

    // Use wrong input name - should fail because "wrongName" is not a valid input
    val inputs = Map("wrongName" -> CValue.CString("test"))
    val result = constellation.runDag("ValidationDag", inputs).attempt.unsafeRunSync()

    result.isLeft shouldBe true
  }

  it should "fail on wrong input type" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createUppercaseModule()).unsafeRunSync()

    val moduleId = UUID.randomUUID()
    val inputDataId = UUID.randomUUID()
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
        inputDataId -> DataNodeSpec("text", Map(inputDataId -> "text", moduleId -> "text"), CType.CString),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    constellation.setDag("TypeDag", dag).unsafeRunSync()

    // Use wrong input type (Int instead of String)
    val inputs = Map("text" -> CValue.CInt(123))
    val result = constellation.runDag("TypeDag", inputs).attempt.unsafeRunSync()

    result.isLeft shouldBe true
    result.left.exists(_.getMessage.contains("different type")) shouldBe true
  }

  // Module status tracking
  "runDag" should "track module status" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createUppercaseModule()).unsafeRunSync()

    val moduleId = UUID.randomUUID()
    val inputDataId = UUID.randomUUID()
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
        inputDataId -> DataNodeSpec("text", Map(inputDataId -> "text", moduleId -> "text"), CType.CString),
        outputDataId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId))
    )

    constellation.setDag("StatusDag", dag).unsafeRunSync()

    val inputs = Map("text" -> CValue.CString("test"))
    val state = constellation.runDag("StatusDag", inputs).unsafeRunSync()

    state.moduleStatus.get(moduleId) shouldBe defined
    state.moduleStatus(moduleId).value shouldBe a[Module.Status.Fired]
  }
}
