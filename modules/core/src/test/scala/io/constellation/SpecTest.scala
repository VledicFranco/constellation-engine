package io.constellation

import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SpecTest extends AnyFlatSpec with Matchers {

  "DagSpec" should "create empty dag with a name" in {
    val dag = DagSpec.empty("TestDag")
    dag.name shouldBe "TestDag"
    dag.modules shouldBe Map.empty
    dag.data shouldBe Map.empty
    dag.inEdges shouldBe Set.empty
    dag.outEdges shouldBe Set.empty
  }

  it should "identify top level data nodes" in {
    val dataId1  = UUID.randomUUID()
    val dataId2  = UUID.randomUUID()
    val dataId3  = UUID.randomUUID()
    val moduleId = UUID.randomUUID()

    val dataSpec1 = DataNodeSpec(name = "data1", nicknames = Map.empty, cType = CType.CInt)
    val dataSpec2 = DataNodeSpec(name = "data2", nicknames = Map.empty, cType = CType.CString)
    val dataSpec3 = DataNodeSpec(name = "data3", nicknames = Map.empty, cType = CType.CBoolean)

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map.empty,
      data = Map(dataId1 -> dataSpec1, dataId2 -> dataSpec2, dataId3 -> dataSpec3),
      inEdges = Set((dataId1, moduleId)), // dataId1 feeds into module
      outEdges = Set((moduleId, dataId3)) // module produces dataId3
    )

    // dataId1 is consumed but not produced, so it's top-level
    // dataId2 has no edges at all - the current implementation only considers nodes in inEdges
    // dataId3 is produced by a module, so it's not top-level
    dag.topLevelDataNodes.keys should contain(dataId1)
    dag.topLevelDataNodes.keys should not contain dataId3
  }

  "DataNodeSpec" should "store nicknames per module" in {
    val moduleId1 = UUID.randomUUID()
    val moduleId2 = UUID.randomUUID()

    val spec = DataNodeSpec(
      name = "testData",
      nicknames = Map(moduleId1 -> "input_x", moduleId2 -> "output_result"),
      cType = CType.CInt
    )

    spec.name shouldBe "testData"
    spec.nicknames(moduleId1) shouldBe "input_x"
    spec.nicknames(moduleId2) shouldBe "output_result"
    spec.cType shouldBe CType.CInt
  }

  "ModuleNodeSpec" should "have default config values" in {
    val spec = ModuleNodeSpec(
      metadata = ComponentMetadata(
        name = "Test",
        description = "Test module",
        tags = List.empty,
        majorVersion = 1,
        minorVersion = 0
      ),
      config = ModuleConfig.default,
      definitionContext = None,
      consumes = Map.empty,
      produces = Map.empty
    )

    spec.config shouldBe ModuleConfig.default
    spec.definitionContext shouldBe None
  }

  it should "provide convenience accessors" in {
    val spec = ModuleNodeSpec(
      metadata = ComponentMetadata(
        name = "Accessor",
        description = "Test accessors",
        tags = List("test"),
        majorVersion = 2,
        minorVersion = 3
      )
    )

    spec.name shouldBe "Accessor"
    spec.description shouldBe "Test accessors"
    spec.tags shouldBe List("test")
    spec.majorVersion shouldBe 2
    spec.minorVersion shouldBe 3
    spec.inputsTimeout shouldBe ModuleConfig.default.inputsTimeout
    spec.moduleTimeout shouldBe ModuleConfig.default.moduleTimeout
  }

  "ComponentMetadata" should "store all metadata fields" in {
    val meta = ComponentMetadata(
      name = "MyModule",
      description = "Does something useful",
      tags = List("math", "utility"),
      majorVersion = 2,
      minorVersion = 3
    )

    meta.name shouldBe "MyModule"
    meta.description shouldBe "Does something useful"
    meta.tags shouldBe List("math", "utility")
    meta.majorVersion shouldBe 2
    meta.minorVersion shouldBe 3
  }

  it should "create empty metadata with just a name" in {
    val meta = ComponentMetadata.empty("EmptyMeta")
    meta.name shouldBe "EmptyMeta"
    meta.description shouldBe ""
    meta.tags shouldBe List.empty
    meta.majorVersion shouldBe 0
    meta.minorVersion shouldBe 1
  }

  "ModuleConfig" should "have sensible defaults" in {
    val config = ModuleConfig.default
    config.inputsTimeout.toSeconds should be > 0L
    config.moduleTimeout.toSeconds should be > 0L
  }

  "ModuleNodeSpec.empty" should "create an empty module spec" in {
    val spec = ModuleNodeSpec.empty
    spec.name shouldBe "EmptyModule"
    spec.consumes shouldBe Map.empty
    spec.produces shouldBe Map.empty
  }
}
