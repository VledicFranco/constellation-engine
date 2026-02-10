package io.constellation.impl

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModuleRegistryImplExtendedTest extends AnyFlatSpec with Matchers {

  case class TestInput(x: Long)
  case class TestOutput(result: Long)

  private def createTestModule(name: String): Module.Uninitialized =
    ModuleBuilder
      .metadata(name, s"Test module $name", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x * 2))
      .build

  private def createTestModuleWithVersion(
      name: String,
      major: Int,
      minor: Int
  ): Module.Uninitialized =
    ModuleBuilder
      .metadata(name, s"Test module $name v$major.$minor", major, minor)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.x * 2))
      .build

  // ===== initModules with various DagSpec configurations =====

  "initModules" should "return empty map for empty DagSpec" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    registry.register("Uppercase", createTestModule("Uppercase")).unsafeRunSync()

    val dag = DagSpec.empty("EmptyDag")

    val result = registry.initModules(dag).unsafeRunSync()
    result shouldBe empty
  }

  it should "map multiple DagSpec modules to their registered implementations" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val m1       = createTestModule("Uppercase")
    val m2       = createTestModule("WordCount")
    val m3       = createTestModule("Trim")

    registry.register("Uppercase", m1).unsafeRunSync()
    registry.register("WordCount", m2).unsafeRunSync()
    registry.register("Trim", m3).unsafeRunSync()

    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    val id3 = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        id1 -> ModuleNodeSpec(metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0)),
        id2 -> ModuleNodeSpec(metadata = ComponentMetadata("WordCount", "Test", List.empty, 1, 0)),
        id3 -> ModuleNodeSpec(metadata = ComponentMetadata("Trim", "Test", List.empty, 1, 0))
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = registry.initModules(dag).unsafeRunSync()
    result should have size 3
    result should contain key id1
    result should contain key id2
    result should contain key id3

    result(id1).spec.name shouldBe "Uppercase"
    result(id2).spec.name shouldBe "WordCount"
    result(id3).spec.name shouldBe "Trim"
  }

  it should "return partial map when some DagSpec modules are not registered" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    registry.register("Uppercase", createTestModule("Uppercase")).unsafeRunSync()

    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    val id3 = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        id1 -> ModuleNodeSpec(metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0)),
        id2 -> ModuleNodeSpec(metadata =
          ComponentMetadata("MissingModule1", "Test", List.empty, 1, 0)
        ),
        id3 -> ModuleNodeSpec(metadata =
          ComponentMetadata("MissingModule2", "Test", List.empty, 1, 0)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = registry.initModules(dag).unsafeRunSync()
    result should have size 1
    result should contain key id1
    result should not contain key(id2)
    result should not contain key(id3)
  }

  it should "resolve DagSpec modules using dot-prefixed names via index" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    registry.register("dag1.Transform", createTestModule("dag1.Transform")).unsafeRunSync()

    val moduleId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("dag1.Transform", "Test", List.empty, 1, 0)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = registry.initModules(dag).unsafeRunSync()
    result should have size 1
    result should contain key moduleId
  }

  it should "resolve DagSpec modules by stripped name when registered without prefix" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    registry.register("Transform", createTestModule("Transform")).unsafeRunSync()

    val moduleId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("mydag.Transform", "Test", List.empty, 1, 0)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = registry.initModules(dag).unsafeRunSync()
    result should have size 1
    result should contain key moduleId
  }

  it should "handle DagSpec with same module name used for different UUIDs" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    registry.register("Uppercase", createTestModule("Uppercase")).unsafeRunSync()

    // Two different DAG nodes reference the same module name
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        id1 -> ModuleNodeSpec(metadata =
          ComponentMetadata("Uppercase", "First use", List.empty, 1, 0)
        ),
        id2 -> ModuleNodeSpec(metadata =
          ComponentMetadata("Uppercase", "Second use", List.empty, 1, 0)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = registry.initModules(dag).unsafeRunSync()
    result should have size 2
    result should contain key id1
    result should contain key id2
    // Both should resolve to the same registered module
    result(id1).spec.name shouldBe "Uppercase"
    result(id2).spec.name shouldBe "Uppercase"
  }

  // ===== Concurrent registration =====

  "concurrent registration" should "handle parallel register calls safely" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]

    val modules = (1 to 50).map(i => s"Module$i" -> createTestModule(s"Module$i")).toList

    // Register all modules in parallel
    modules
      .parTraverse { case (name, module) =>
        registry.register(name, module)
      }
      .unsafeRunSync()

    val size = registry.size.unsafeRunSync()
    size shouldBe 50

    // All modules should be retrievable
    modules.foreach { case (name, _) =>
      val retrieved = registry.get(name).unsafeRunSync()
      retrieved shouldBe defined
      retrieved.get.spec.name shouldBe name
    }
  }

  it should "handle parallel register and get calls safely" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]

    // Pre-register some modules
    (1 to 10).foreach { i =>
      registry.register(s"PreModule$i", createTestModule(s"PreModule$i")).unsafeRunSync()
    }

    // Concurrently register new modules and read existing ones
    val registerFiber = (11 to 30).toList.parTraverse { i =>
      registry.register(s"NewModule$i", createTestModule(s"NewModule$i"))
    }

    val getFiber = (1 to 10).toList.parTraverse { i =>
      registry.get(s"PreModule$i")
    }

    val (_, getResults) = (registerFiber, getFiber).parTupled.unsafeRunSync()

    // All pre-registered modules should still be found
    getResults.foreach { result =>
      result shouldBe defined
    }

    // All newly registered modules should exist
    (11 to 30).foreach { i =>
      registry.get(s"NewModule$i").unsafeRunSync() shouldBe defined
    }
  }

  it should "handle concurrent registerAll calls safely" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]

    val batch1 = (1 to 10).map(i => s"Batch1_$i" -> createTestModule(s"Batch1_$i")).toList
    val batch2 = (1 to 10).map(i => s"Batch2_$i" -> createTestModule(s"Batch2_$i")).toList
    val batch3 = (1 to 10).map(i => s"Batch3_$i" -> createTestModule(s"Batch3_$i")).toList

    // Register three batches concurrently
    (
      registry.registerAll(batch1),
      registry.registerAll(batch2),
      registry.registerAll(batch3)
    ).parTupled.unsafeRunSync()

    registry.size.unsafeRunSync() shouldBe 30
  }

  // ===== get with various name patterns =====

  "get" should "return None for empty string name" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    registry.get("").unsafeRunSync() shouldBe None
  }

  it should "handle lookup of deeply nested dot-separated names" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val module   = createTestModule("org.example.Transform")
    registry.register("org.example.Transform", module).unsafeRunSync()

    // Exact match should work
    registry.get("org.example.Transform").unsafeRunSync() shouldBe defined

    // Short name should also work (takes last segment after split on '.')
    registry.get("Transform").unsafeRunSync() shouldBe defined
  }

  it should "handle lookup where query has dot prefix but registered name does not" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    registry.register("SimpleModule", createTestModule("SimpleModule")).unsafeRunSync()

    // Querying with a prefix should still find it via stripped name fallback
    val result = registry.get("mydag.SimpleModule").unsafeRunSync()
    result shouldBe defined
    result.get.spec.name shouldBe "SimpleModule"
  }

  it should "resolve short name to first registered when multiple prefixed modules share it" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    val m1       = createTestModuleWithVersion("dag1.Compute", 1, 0)
    val m2       = createTestModuleWithVersion("dag2.Compute", 2, 0)

    registry.register("dag1.Compute", m1).unsafeRunSync()
    registry.register("dag2.Compute", m2).unsafeRunSync()

    // Short name "Compute" should resolve to first registered (dag1.Compute)
    val shortResult = registry.get("Compute").unsafeRunSync()
    shortResult shouldBe defined
    shortResult.get.spec.majorVersion shouldBe 1

    // Full names should still resolve correctly
    val fullResult1 = registry.get("dag1.Compute").unsafeRunSync()
    fullResult1 shouldBe defined
    fullResult1.get.spec.majorVersion shouldBe 1

    val fullResult2 = registry.get("dag2.Compute").unsafeRunSync()
    fullResult2 shouldBe defined
    fullResult2.get.spec.majorVersion shouldBe 2
  }

  // ===== register overwrite behavior =====

  "register" should "overwrite module data but preserve index entries" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]

    val m1 = createTestModuleWithVersion("dag1.Process", 1, 0)
    registry.register("dag1.Process", m1).unsafeRunSync()

    val indexBefore = registry.indexSize.unsafeRunSync()

    // Overwrite with updated version
    val m2 = createTestModuleWithVersion("dag1.Process", 2, 0)
    registry.register("dag1.Process", m2).unsafeRunSync()

    registry.size.unsafeRunSync() shouldBe 1
    registry.indexSize.unsafeRunSync() shouldBe indexBefore

    val retrieved = registry.get("dag1.Process").unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.spec.majorVersion shouldBe 2
  }

  // ===== listModules =====

  "listModules" should "return empty list for empty registry" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    registry.listModules.unsafeRunSync() shouldBe empty
  }

  it should "return specs for all registered modules" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    registry.register("A", createTestModule("A")).unsafeRunSync()
    registry.register("B", createTestModule("B")).unsafeRunSync()
    registry.register("C", createTestModule("C")).unsafeRunSync()

    val specs = registry.listModules.unsafeRunSync()
    specs should have size 3
    specs.map(_.name) should contain theSameElementsAs List("A", "B", "C")
  }

  it should "reflect overwrites (no duplicates)" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    registry.register("Module", createTestModuleWithVersion("Module", 1, 0)).unsafeRunSync()
    registry.register("Module", createTestModuleWithVersion("Module", 2, 0)).unsafeRunSync()

    val specs = registry.listModules.unsafeRunSync()
    specs should have size 1
    specs.head.majorVersion shouldBe 2
  }

  // ===== clear =====

  "clear" should "allow re-registration after clearing" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.register("A", createTestModule("A")).unsafeRunSync()
    registry.register("dag.B", createTestModule("dag.B")).unsafeRunSync()

    registry.clear.unsafeRunSync()
    registry.size.unsafeRunSync() shouldBe 0
    registry.indexSize.unsafeRunSync() shouldBe 0

    // Re-register
    registry.register("C", createTestModule("C")).unsafeRunSync()
    registry.size.unsafeRunSync() shouldBe 1
    registry.get("C").unsafeRunSync() shouldBe defined

    // Old modules should not be findable
    registry.get("A").unsafeRunSync() shouldBe None
    registry.get("dag.B").unsafeRunSync() shouldBe None
    registry.get("B").unsafeRunSync() shouldBe None
  }

  // ===== contains edge cases =====

  "contains" should "return false for empty string" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.contains("").unsafeRunSync() shouldBe false
  }

  it should "return false after clear" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync().asInstanceOf[ModuleRegistryImpl]
    registry.register("X", createTestModule("X")).unsafeRunSync()
    registry.contains("X").unsafeRunSync() shouldBe true

    registry.clear.unsafeRunSync()
    registry.contains("X").unsafeRunSync() shouldBe false
  }

  // ===== initModules with branch modules (synthetic modules not in registry) =====

  "initModules" should "return empty for branch modules not in registry" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()

    val branchId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("BranchDag"),
      modules = Map(
        branchId -> ModuleNodeSpec(
          metadata = ComponentMetadata("branch-0", "Branch module", List.empty, 1, 0),
          consumes =
            Map("cond0" -> CType.CBoolean, "expr0" -> CType.CString, "otherwise" -> CType.CString),
          produces = Map("out" -> CType.CString)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    // Branch modules are synthetic and not in the registry
    val result = registry.initModules(dag).unsafeRunSync()
    result shouldBe empty
  }

  // ===== withModules factory edge cases =====

  "ModuleRegistryImpl.withModules" should "create empty registry from empty list" in {
    val registry = ModuleRegistryImpl.withModules(List.empty).unsafeRunSync()
    registry.size.unsafeRunSync() shouldBe 0
    registry.indexSize.unsafeRunSync() shouldBe 0
    registry.listModules.unsafeRunSync() shouldBe empty
  }

  it should "handle prefixed module names with short name conflicts" in {
    val m1 = createTestModule("dag1.Parser")
    val m2 = createTestModule("dag2.Parser")

    val registry = ModuleRegistryImpl
      .withModules(
        List(
          "dag1.Parser" -> m1,
          "dag2.Parser" -> m2
        )
      )
      .unsafeRunSync()

    registry.size.unsafeRunSync() shouldBe 2

    // Both full names should work
    registry.get("dag1.Parser").unsafeRunSync() shouldBe defined
    registry.get("dag2.Parser").unsafeRunSync() shouldBe defined

    // Short name resolves to one of them (first registration wins)
    val shortResult = registry.get("Parser").unsafeRunSync()
    shortResult shouldBe defined
  }

  // ===== initModules with DagSpec containing modules with empty name =====

  it should "not find modules with empty name if none registered" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    registry.register("Uppercase", createTestModule("Uppercase")).unsafeRunSync()

    val moduleId = UUID.randomUUID()
    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("", "Empty name", List.empty, 1, 0)
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = registry.initModules(dag).unsafeRunSync()
    result shouldBe empty
  }

  // ===== Concurrent initModules =====

  "concurrent initModules" should "handle parallel initModules calls on same registry" in {
    val registry = ModuleRegistryImpl.init.unsafeRunSync()
    (1 to 5).foreach { i =>
      registry.register(s"Module$i", createTestModule(s"Module$i")).unsafeRunSync()
    }

    val dags = (1 to 5).map { i =>
      val moduleId = UUID.randomUUID()
      DagSpec(
        metadata = ComponentMetadata.empty(s"Dag$i"),
        modules = Map(
          moduleId -> ModuleNodeSpec(
            metadata = ComponentMetadata(s"Module$i", "Test", List.empty, 1, 0)
          )
        ),
        data = Map.empty,
        inEdges = Set.empty,
        outEdges = Set.empty
      )
    }.toList

    // Run initModules in parallel for different DAGs
    val results = dags.parTraverse(dag => registry.initModules(dag)).unsafeRunSync()

    results should have size 5
    results.foreach { result =>
      result should have size 1
    }
  }
}
