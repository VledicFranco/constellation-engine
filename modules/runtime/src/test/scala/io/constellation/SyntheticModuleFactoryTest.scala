package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class SyntheticModuleFactoryTest extends AnyFlatSpec with Matchers {

  "fromDagSpec" should "reconstruct branch modules" in {
    val moduleId = UUID.randomUUID()
    val spec = ModuleNodeSpec(
      metadata = ComponentMetadata.empty("test.branch-abc12345"),
      consumes = Map(
        "cond0" -> CType.CBoolean,
        "expr0" -> CType.CString,
        "otherwise" -> CType.CString
      ),
      produces = Map("out" -> CType.CString)
    )

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> spec),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result should contain key moduleId
    result(moduleId).spec shouldBe spec
  }

  it should "not reconstruct non-branch modules" in {
    val moduleId = UUID.randomUUID()
    val spec = ModuleNodeSpec(
      metadata = ComponentMetadata.empty("test.Uppercase"),
      consumes = Map("text" -> CType.CString),
      produces = Map("out" -> CType.CString)
    )

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> spec),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result shouldBe empty
  }

  it should "handle multiple branch modules" in {
    val mod1 = UUID.randomUUID()
    val mod2 = UUID.randomUUID()
    val spec1 = ModuleNodeSpec(
      metadata = ComponentMetadata.empty("test.branch-aaa11111"),
      consumes = Map("cond0" -> CType.CBoolean, "expr0" -> CType.CInt, "otherwise" -> CType.CInt),
      produces = Map("out" -> CType.CInt)
    )
    val spec2 = ModuleNodeSpec(
      metadata = ComponentMetadata.empty("test.branch-bbb22222"),
      consumes = Map(
        "cond0" -> CType.CBoolean, "expr0" -> CType.CString,
        "cond1" -> CType.CBoolean, "expr1" -> CType.CString,
        "otherwise" -> CType.CString
      ),
      produces = Map("out" -> CType.CString)
    )

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(mod1 -> spec1, mod2 -> spec2),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val result = SyntheticModuleFactory.fromDagSpec(dag)
    result.size shouldBe 2
    result should contain key mod1
    result should contain key mod2
  }

  it should "return empty map for DAG with no modules" in {
    val dag = DagSpec.empty("test")
    SyntheticModuleFactory.fromDagSpec(dag) shouldBe empty
  }
}
