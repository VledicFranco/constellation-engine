package io.constellation

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.impl.ModuleRegistryImpl

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModuleRegistryTest extends AnyFlatSpec with Matchers {

  case class TestInput(x: Long)
  case class TestOutput(result: Long)

  private def createTestModule(name: String): Module.Uninitialized =
    ModuleBuilder
      .metadata(name, s"Test module $name", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x * 2))
      .build

  "ModuleRegistry" should "initialize empty" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val modules  = registry.listModules.unsafeRunSync()
    modules shouldBe empty
  }

  it should "register and retrieve a module" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module   = createTestModule("TestModule")

    registry.register("TestModule", module).unsafeRunSync()

    val retrieved = registry.get("TestModule").unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.spec.name shouldBe "TestModule"
  }

  it should "return None for non-existent module" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()

    val retrieved = registry.get("NonExistent").unsafeRunSync()
    retrieved shouldBe None
  }

  it should "list all registered modules" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module1  = createTestModule("Module1")
    val module2  = createTestModule("Module2")
    val module3  = createTestModule("Module3")

    registry.register("Module1", module1).unsafeRunSync()
    registry.register("Module2", module2).unsafeRunSync()
    registry.register("Module3", module3).unsafeRunSync()

    val modules = registry.listModules.unsafeRunSync()
    modules.map(_.name) should contain theSameElementsAs List("Module1", "Module2", "Module3")
  }

  it should "overwrite existing module on duplicate registration" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module1  = createTestModule("SameName")
    val module2 = ModuleBuilder
      .metadata("SameName", "Updated description", 2, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x * 3))
      .build

    registry.register("SameName", module1).unsafeRunSync()
    registry.register("SameName", module2).unsafeRunSync()

    val modules = registry.listModules.unsafeRunSync()
    modules.length shouldBe 1
    modules.head.description shouldBe "Updated description"
    modules.head.majorVersion shouldBe 2
  }

  it should "get module by stripped name prefix" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module   = createTestModule("Uppercase")

    registry.register("Uppercase", module).unsafeRunSync()

    // Should find module when queried with dag prefix
    val retrieved = registry.get("test.Uppercase").unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.spec.name shouldBe "Uppercase"
  }

  it should "prefer exact match over stripped name" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module1  = createTestModule("Uppercase")
    val module2 = ModuleBuilder
      .metadata("test.Uppercase", "Exact match module", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x))
      .build

    registry.register("Uppercase", module1).unsafeRunSync()
    registry.register("test.Uppercase", module2).unsafeRunSync()

    // Should return the exact match
    val retrieved = registry.get("test.Uppercase").unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.spec.description shouldBe "Exact match module"
  }

  "initModules" should "initialize modules for a DAG" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module   = createTestModule("TestModule")
    registry.register("TestModule", module).unsafeRunSync()

    val moduleId  = UUID.randomUUID()
    val dataId    = UUID.randomUUID()
    val outDataId = UUID.randomUUID()

    val moduleSpec = ModuleNodeSpec(
      metadata = ComponentMetadata("TestModule", "Test", List.empty, 1, 0),
      consumes = Map("x" -> CType.CInt),
      produces = Map("result" -> CType.CInt)
    )

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(moduleId -> moduleSpec),
      data = Map(
        dataId    -> DataNodeSpec("input", Map(moduleId -> "x"), CType.CInt),
        outDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CInt)
      ),
      inEdges = Set((dataId, moduleId)),
      outEdges = Set((moduleId, outDataId))
    )

    val initializedModules = registry.initModules(dag).unsafeRunSync()
    initializedModules.size shouldBe 1
    initializedModules.keys should contain(moduleId)
  }

  it should "initialize modules using stripped name matching" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module   = createTestModule("Uppercase")
    registry.register("Uppercase", module).unsafeRunSync()

    val moduleId  = UUID.randomUUID()
    val dataId    = UUID.randomUUID()
    val outDataId = UUID.randomUUID()

    // DAG uses prefixed name
    val moduleSpec = ModuleNodeSpec(
      metadata = ComponentMetadata("mydag.Uppercase", "Test", List.empty, 1, 0),
      consumes = Map("x" -> CType.CInt),
      produces = Map("result" -> CType.CInt)
    )

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(moduleId -> moduleSpec),
      data = Map(
        dataId    -> DataNodeSpec("input", Map(moduleId -> "x"), CType.CInt),
        outDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CInt)
      ),
      inEdges = Set((dataId, moduleId)),
      outEdges = Set((moduleId, outDataId))
    )

    val initializedModules = registry.initModules(dag).unsafeRunSync()
    initializedModules.size shouldBe 1
    initializedModules.keys should contain(moduleId)
  }

  it should "return empty map for DAG with no registered modules" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()

    val moduleId  = UUID.randomUUID()
    val dataId    = UUID.randomUUID()
    val outDataId = UUID.randomUUID()

    val moduleSpec = ModuleNodeSpec(
      metadata = ComponentMetadata("UnregisteredModule", "Test", List.empty, 1, 0)
    )

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(moduleId -> moduleSpec),
      data = Map(
        dataId    -> DataNodeSpec("input", Map.empty, CType.CInt),
        outDataId -> DataNodeSpec("output", Map.empty, CType.CInt)
      ),
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val initializedModules = registry.initModules(dag).unsafeRunSync()
    initializedModules shouldBe empty
  }

  it should "initialize only registered modules from DAG" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module1  = createTestModule("RegisteredModule")
    registry.register("RegisteredModule", module1).unsafeRunSync()

    val moduleId1 = UUID.randomUUID()
    val moduleId2 = UUID.randomUUID()

    val moduleSpec1 = ModuleNodeSpec(
      metadata = ComponentMetadata("RegisteredModule", "Test", List.empty, 1, 0)
    )
    val moduleSpec2 = ModuleNodeSpec(
      metadata = ComponentMetadata("UnregisteredModule", "Test", List.empty, 1, 0)
    )

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(moduleId1 -> moduleSpec1, moduleId2 -> moduleSpec2),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val initializedModules = registry.initModules(dag).unsafeRunSync()
    initializedModules.size shouldBe 1
    initializedModules.keys should contain(moduleId1)
    initializedModules.keys should not contain moduleId2
  }

  // ===== registerAll =====

  "registerAll" should "register multiple modules at once" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    val m1       = createTestModule("Module1")
    val m2       = createTestModule("Module2")
    val m3       = createTestModule("Module3")

    registry.registerAll(List("Module1" -> m1, "Module2" -> m2, "Module3" -> m3)).unsafeRunSync()

    val modules = registry.listModules.unsafeRunSync()
    modules.map(_.name) should contain theSameElementsAs List("Module1", "Module2", "Module3")
  }

  it should "register empty list without error" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.registerAll(List.empty).unsafeRunSync()
    registry.size.unsafeRunSync() shouldBe 0
  }

  // ===== contains =====

  "contains" should "return true for registered module name" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.register("TestModule", createTestModule("TestModule")).unsafeRunSync()
    registry.contains("TestModule").unsafeRunSync() shouldBe true
  }

  it should "return false for unregistered name" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.contains("NonExistent").unsafeRunSync() shouldBe false
  }

  it should "return true for short name of prefixed module" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.register("mydag.Uppercase", createTestModule("mydag.Uppercase")).unsafeRunSync()
    registry.contains("Uppercase").unsafeRunSync() shouldBe true
    registry.contains("mydag.Uppercase").unsafeRunSync() shouldBe true
  }

  // ===== size =====

  "size" should "return 0 for empty registry" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.size.unsafeRunSync() shouldBe 0
  }

  it should "return correct count after registrations" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.register("A", createTestModule("A")).unsafeRunSync()
    registry.register("B", createTestModule("B")).unsafeRunSync()
    registry.size.unsafeRunSync() shouldBe 2
  }

  // ===== indexSize =====

  "indexSize" should "return 0 for empty registry" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.indexSize.unsafeRunSync() shouldBe 0
  }

  it should "be greater than size when prefixed modules are registered" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.register("mydag.Uppercase", createTestModule("mydag.Uppercase")).unsafeRunSync()
    // Index has both "mydag.Uppercase" and "Uppercase"
    registry.size.unsafeRunSync() shouldBe 1
    registry.indexSize.unsafeRunSync() shouldBe 2
  }

  it should "equal size for non-prefixed modules" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.register("Uppercase", createTestModule("Uppercase")).unsafeRunSync()
    registry.size.unsafeRunSync() shouldBe 1
    registry.indexSize.unsafeRunSync() shouldBe 1
  }

  // ===== clear =====

  "clear" should "remove all modules and index entries" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.register("A", createTestModule("A")).unsafeRunSync()
    registry.register("mydag.B", createTestModule("mydag.B")).unsafeRunSync()
    registry.size.unsafeRunSync() shouldBe 2

    registry.clear.unsafeRunSync()

    registry.size.unsafeRunSync() shouldBe 0
    registry.indexSize.unsafeRunSync() shouldBe 0
    registry.listModules.unsafeRunSync() shouldBe empty
    registry.get("A").unsafeRunSync() shouldBe None
  }

  // ===== withModules factory =====

  "ModuleRegistryImpl.withModules" should "create pre-populated registry" in {
    val m1 = createTestModule("Mod1")
    val m2 = createTestModule("Mod2")

    val registry = ModuleRegistryImpl.withModules(List("Mod1" -> m1, "Mod2" -> m2)).unsafeRunSync()

    registry.size.unsafeRunSync() shouldBe 2
    registry.get("Mod1").unsafeRunSync() shouldBe defined
    registry.get("Mod2").unsafeRunSync() shouldBe defined
  }

  it should "index short names for prefixed modules" in {
    val m1 = createTestModule("dag1.Transform")

    val registry = ModuleRegistryImpl.withModules(List("dag1.Transform" -> m1)).unsafeRunSync()

    registry.get("Transform").unsafeRunSync() shouldBe defined
    registry.indexSize.unsafeRunSync() shouldBe 2 // "dag1.Transform" + "Transform"
  }

  // ===== Name conflict resolution in updateNameIndex =====

  "name index conflict resolution" should "let first registration win for short name" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    val m1       = createTestModule("dag1.Transform")
    val m2       = createTestModule("dag2.Transform")

    registry.register("dag1.Transform", m1).unsafeRunSync()
    registry.register("dag2.Transform", m2).unsafeRunSync()

    // Short name "Transform" should resolve to first registered (dag1.Transform)
    val retrieved = registry.get("Transform").unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.spec.name shouldBe "dag1.Transform"

    // Both full names should still work
    registry.get("dag1.Transform").unsafeRunSync() shouldBe defined
    registry.get("dag2.Transform").unsafeRunSync() shouldBe defined
  }

  it should "not increment index when re-registering same module" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    val m1       = createTestModule("dag1.Transform")

    registry.register("dag1.Transform", m1).unsafeRunSync()
    val indexSizeBefore = registry.indexSize.unsafeRunSync()

    // Re-register same name
    registry.register("dag1.Transform", m1).unsafeRunSync()
    val indexSizeAfter = registry.indexSize.unsafeRunSync()

    indexSizeAfter shouldBe indexSizeBefore
  }
}
