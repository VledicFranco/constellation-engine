package io.constellation

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.impl.DagRegistryImpl
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class DagRegistryTest extends AnyFlatSpec with Matchers {

  private def createTestDag(name: String): DagSpec =
    DagSpec.empty(name).copy(
      metadata = ComponentMetadata(name, s"Description for $name", List("test"), 1, 0)
    )

  "DagRegistry" should "initialize empty" in {
    val registry = DagRegistryImpl.init.unsafeRunSync()
    val dags = registry.list.unsafeRunSync()
    dags shouldBe empty
  }

  it should "register and retrieve a DAG" in {
    val registry = DagRegistryImpl.init.unsafeRunSync()
    val dag = createTestDag("TestDag")

    registry.register("TestDag", dag).unsafeRunSync()

    val retrieved = registry.retrieve("TestDag", None).unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.name shouldBe "TestDag"
    retrieved.get.description shouldBe "Description for TestDag"
  }

  it should "return None for non-existent DAG" in {
    val registry = DagRegistryImpl.init.unsafeRunSync()

    val retrieved = registry.retrieve("NonExistent", None).unsafeRunSync()
    retrieved shouldBe None
  }

  it should "check DAG existence correctly" in {
    val registry = DagRegistryImpl.init.unsafeRunSync()
    val dag = createTestDag("ExistingDag")

    registry.exists("ExistingDag").unsafeRunSync() shouldBe false

    registry.register("ExistingDag", dag).unsafeRunSync()

    registry.exists("ExistingDag").unsafeRunSync() shouldBe true
    registry.exists("NonExistent").unsafeRunSync() shouldBe false
  }

  it should "list all registered DAGs" in {
    val registry = DagRegistryImpl.init.unsafeRunSync()
    val dag1 = createTestDag("Dag1")
    val dag2 = createTestDag("Dag2")
    val dag3 = createTestDag("Dag3")

    registry.register("Dag1", dag1).unsafeRunSync()
    registry.register("Dag2", dag2).unsafeRunSync()
    registry.register("Dag3", dag3).unsafeRunSync()

    val dags = registry.list.unsafeRunSync()
    dags.size shouldBe 3
    dags.keys should contain theSameElementsAs Set("Dag1", "Dag2", "Dag3")
  }

  it should "return ComponentMetadata in list" in {
    val registry = DagRegistryImpl.init.unsafeRunSync()
    val dag = DagSpec(
      metadata = ComponentMetadata("MetaDag", "Has metadata", List("tag1", "tag2"), 2, 3),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    registry.register("MetaDag", dag).unsafeRunSync()

    val dags = registry.list.unsafeRunSync()
    dags.size shouldBe 1
    val meta = dags("MetaDag")
    meta.name shouldBe "MetaDag"
    meta.description shouldBe "Has metadata"
    meta.tags shouldBe List("tag1", "tag2")
    meta.majorVersion shouldBe 2
    meta.minorVersion shouldBe 3
  }

  it should "overwrite existing DAG on duplicate registration" in {
    val registry = DagRegistryImpl.init.unsafeRunSync()
    val dag1 = createTestDag("SameName")
    val dag2 = DagSpec(
      metadata = ComponentMetadata("SameName", "Updated description", List("updated"), 2, 0),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    registry.register("SameName", dag1).unsafeRunSync()
    registry.register("SameName", dag2).unsafeRunSync()

    val dags = registry.list.unsafeRunSync()
    dags.size shouldBe 1

    val retrieved = registry.retrieve("SameName", None).unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.description shouldBe "Updated description"
    retrieved.get.majorVersion shouldBe 2
    retrieved.get.tags shouldBe List("updated")
  }

  it should "retrieve DAG ignoring version parameter" in {
    val registry = DagRegistryImpl.init.unsafeRunSync()
    val dag = createTestDag("VersionedDag")

    registry.register("VersionedDag", dag).unsafeRunSync()

    // Current implementation ignores version parameter
    val retrieved1 = registry.retrieve("VersionedDag", None).unsafeRunSync()
    val retrieved2 = registry.retrieve("VersionedDag", Some("1.0")).unsafeRunSync()
    val retrieved3 = registry.retrieve("VersionedDag", Some("2.0")).unsafeRunSync()

    retrieved1 shouldBe defined
    retrieved2 shouldBe defined
    retrieved3 shouldBe defined
    retrieved1.get.name shouldBe "VersionedDag"
    retrieved2.get.name shouldBe "VersionedDag"
    retrieved3.get.name shouldBe "VersionedDag"
  }

  it should "register DAG with modules and data nodes" in {
    val registry = DagRegistryImpl.init.unsafeRunSync()
    val moduleId = UUID.randomUUID()
    val dataId1 = UUID.randomUUID()
    val dataId2 = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("ComplexDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("TestModule", "Test", List.empty, 1, 0),
          consumes = Map("input" -> CType.CString),
          produces = Map("output" -> CType.CString)
        )
      ),
      data = Map(
        dataId1 -> DataNodeSpec("input", Map(moduleId -> "input"), CType.CString),
        dataId2 -> DataNodeSpec("output", Map(moduleId -> "output"), CType.CString)
      ),
      inEdges = Set((dataId1, moduleId)),
      outEdges = Set((moduleId, dataId2))
    )

    registry.register("ComplexDag", dag).unsafeRunSync()

    val retrieved = registry.retrieve("ComplexDag", None).unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.modules.size shouldBe 1
    retrieved.get.data.size shouldBe 2
    retrieved.get.inEdges.size shouldBe 1
    retrieved.get.outEdges.size shouldBe 1
  }
}
