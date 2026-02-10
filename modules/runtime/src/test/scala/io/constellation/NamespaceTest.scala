package io.constellation

import java.util.UUID

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// ---------------------------------------------------------------------------
// Module.Namespace - construction and nameId
// ---------------------------------------------------------------------------

class NamespaceNameIdTest extends AnyFlatSpec with Matchers {

  "Namespace.nameId" should "return the UUID for a known name" in {
    val id  = UUID.randomUUID()
    val ns  = Module.Namespace(Map("text" -> id))
    val res = ns.nameId("text").unsafeRunSync()

    res shouldBe id
  }

  it should "raise an error for an unknown name" in {
    val ns     = Module.Namespace(Map("text" -> UUID.randomUUID()))
    val result = ns.nameId("missing").attempt.unsafeRunSync()

    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("missing")
    result.left.toOption.get.getMessage should include("not found in namespace")
  }

  it should "work with an empty namespace" in {
    val ns     = Module.Namespace(Map.empty)
    val result = ns.nameId("anything").attempt.unsafeRunSync()

    result.isLeft shouldBe true
  }

  it should "distinguish between different names in the namespace" in {
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    val ns  = Module.Namespace(Map("input" -> id1, "output" -> id2))

    ns.nameId("input").unsafeRunSync() shouldBe id1
    ns.nameId("output").unsafeRunSync() shouldBe id2
  }
}

// ---------------------------------------------------------------------------
// Module.Namespace.consumes
// ---------------------------------------------------------------------------

class NamespaceConsumesTest extends AnyFlatSpec with Matchers {

  "Namespace.consumes" should "build namespace from inEdges for a module" in {
    val moduleId = UUID.randomUUID()
    val dataId   = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> ModuleNodeSpec.empty),
      data = Map(
        dataId -> DataNodeSpec(
          name = "text",
          nicknames = Map(moduleId -> "text"),
          cType = CType.CString
        )
      ),
      inEdges = Set((dataId, moduleId)),
      outEdges = Set.empty
    )

    val ns = Module.Namespace.consumes(moduleId, dag).unsafeRunSync()
    ns.nameToUUID shouldBe Map("text" -> dataId)
  }

  it should "return empty namespace when module has no inEdges" in {
    val moduleId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> ModuleNodeSpec.empty),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val ns = Module.Namespace.consumes(moduleId, dag).unsafeRunSync()
    ns.nameToUUID shouldBe empty
  }

  it should "only include edges targeting the specified module" in {
    val module1 = UUID.randomUUID()
    val module2 = UUID.randomUUID()
    val data1   = UUID.randomUUID()
    val data2   = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(
        module1 -> ModuleNodeSpec.empty,
        module2 -> ModuleNodeSpec.empty
      ),
      data = Map(
        data1 -> DataNodeSpec("input1", Map(module1 -> "a"), CType.CString),
        data2 -> DataNodeSpec("input2", Map(module2 -> "b"), CType.CInt)
      ),
      inEdges = Set((data1, module1), (data2, module2)),
      outEdges = Set.empty
    )

    val ns1 = Module.Namespace.consumes(module1, dag).unsafeRunSync()
    ns1.nameToUUID shouldBe Map("a" -> data1)

    val ns2 = Module.Namespace.consumes(module2, dag).unsafeRunSync()
    ns2.nameToUUID shouldBe Map("b" -> data2)
  }

  it should "fail when data node is missing from dag" in {
    val moduleId = UUID.randomUUID()
    val dataId   = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> ModuleNodeSpec.empty),
      data = Map.empty, // data node not registered
      inEdges = Set((dataId, moduleId)),
      outEdges = Set.empty
    )

    val result = Module.Namespace.consumes(moduleId, dag).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("data node")
  }

  it should "fail when nickname is missing for the module" in {
    val moduleId = UUID.randomUUID()
    val dataId   = UUID.randomUUID()
    val otherId  = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> ModuleNodeSpec.empty),
      data = Map(
        dataId -> DataNodeSpec(
          name = "text",
          nicknames = Map(otherId -> "text"), // nickname for a different module
          cType = CType.CString
        )
      ),
      inEdges = Set((dataId, moduleId)),
      outEdges = Set.empty
    )

    val result = Module.Namespace.consumes(moduleId, dag).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("nickname")
  }
}

// ---------------------------------------------------------------------------
// Module.Namespace.produces
// ---------------------------------------------------------------------------

class NamespaceProducesTest extends AnyFlatSpec with Matchers {

  "Namespace.produces" should "build namespace from outEdges for a module" in {
    val moduleId = UUID.randomUUID()
    val dataId   = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> ModuleNodeSpec.empty),
      data = Map(
        dataId -> DataNodeSpec(
          name = "result",
          nicknames = Map(moduleId -> "result"),
          cType = CType.CString
        )
      ),
      inEdges = Set.empty,
      outEdges = Set((moduleId, dataId))
    )

    val ns = Module.Namespace.produces(moduleId, dag).unsafeRunSync()
    ns.nameToUUID shouldBe Map("result" -> dataId)
  }

  it should "return empty namespace when module has no outEdges" in {
    val moduleId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> ModuleNodeSpec.empty),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )

    val ns = Module.Namespace.produces(moduleId, dag).unsafeRunSync()
    ns.nameToUUID shouldBe empty
  }

  it should "only include edges from the specified module" in {
    val module1 = UUID.randomUUID()
    val module2 = UUID.randomUUID()
    val data1   = UUID.randomUUID()
    val data2   = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(
        module1 -> ModuleNodeSpec.empty,
        module2 -> ModuleNodeSpec.empty
      ),
      data = Map(
        data1 -> DataNodeSpec("out1", Map(module1 -> "x"), CType.CInt),
        data2 -> DataNodeSpec("out2", Map(module2 -> "y"), CType.CFloat)
      ),
      inEdges = Set.empty,
      outEdges = Set((module1, data1), (module2, data2))
    )

    val ns1 = Module.Namespace.produces(module1, dag).unsafeRunSync()
    ns1.nameToUUID shouldBe Map("x" -> data1)

    val ns2 = Module.Namespace.produces(module2, dag).unsafeRunSync()
    ns2.nameToUUID shouldBe Map("y" -> data2)
  }

  it should "fail when data node is missing from dag" in {
    val moduleId = UUID.randomUUID()
    val dataId   = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> ModuleNodeSpec.empty),
      data = Map.empty, // data node not registered
      inEdges = Set.empty,
      outEdges = Set((moduleId, dataId))
    )

    val result = Module.Namespace.produces(moduleId, dag).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("data node")
  }

  it should "fail when nickname is missing for the module" in {
    val moduleId = UUID.randomUUID()
    val dataId   = UUID.randomUUID()
    val otherId  = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> ModuleNodeSpec.empty),
      data = Map(
        dataId -> DataNodeSpec(
          name = "result",
          nicknames = Map(otherId -> "result"), // nickname for different module
          cType = CType.CString
        )
      ),
      inEdges = Set.empty,
      outEdges = Set((moduleId, dataId))
    )

    val result = Module.Namespace.produces(moduleId, dag).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("nickname")
  }

  it should "handle module with multiple output data nodes" in {
    val moduleId = UUID.randomUUID()
    val data1    = UUID.randomUUID()
    val data2    = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("test"),
      modules = Map(moduleId -> ModuleNodeSpec.empty),
      data = Map(
        data1 -> DataNodeSpec("out1", Map(moduleId -> "first"), CType.CString),
        data2 -> DataNodeSpec("out2", Map(moduleId -> "second"), CType.CInt)
      ),
      inEdges = Set.empty,
      outEdges = Set((moduleId, data1), (moduleId, data2))
    )

    val ns = Module.Namespace.produces(moduleId, dag).unsafeRunSync()
    ns.nameToUUID.size shouldBe 2
    ns.nameToUUID("first") shouldBe data1
    ns.nameToUUID("second") shouldBe data2
  }
}

// ---------------------------------------------------------------------------
// LazyProductValue additional coverage
// ---------------------------------------------------------------------------

class LazyProductValueExtendedTest extends AnyFlatSpec with Matchers {

  "LazyProductValue" should "cache field values on repeated access" in {
    val obj   = Map("x" -> Json.fromInt(42))
    val types = Map("x" -> CType.CInt)
    val lp    = LazyProductValue(obj, types)

    lp.getField("x") shouldBe Right(Some(CValue.CInt(42L)))
    lp.getField("x") shouldBe Right(Some(CValue.CInt(42L)))
    lp.materializedCount shouldBe 1 // only converted once
  }

  it should "handle product with multiple fields, materializing selectively" in {
    val obj = Map(
      "a" -> Json.fromString("hello"),
      "b" -> Json.fromInt(10),
      "c" -> Json.fromBoolean(true)
    )
    val types = Map(
      "a" -> CType.CString,
      "b" -> CType.CInt,
      "c" -> CType.CBoolean
    )
    val lp = LazyProductValue(obj, types)

    // Only access "b"
    lp.getField("b") shouldBe Right(Some(CValue.CInt(10L)))
    lp.materializedCount shouldBe 1

    // Now access "a"
    lp.getField("a") shouldBe Right(Some(CValue.CString("hello")))
    lp.materializedCount shouldBe 2
  }

  it should "materialize all fields and produce a CProduct value" in {
    val obj = Map(
      "name"  -> Json.fromString("test"),
      "count" -> Json.fromInt(5)
    )
    val types = Map("name" -> CType.CString, "count" -> CType.CInt)
    val lp    = LazyProductValue(obj, types)

    val result = lp.materialize
    result.isRight shouldBe true
    val product = result.toOption.get.asInstanceOf[CValue.CProduct]
    product.value("name") shouldBe CValue.CString("test")
    product.value("count") shouldBe CValue.CInt(5L)
  }

  it should "return error from materialize when a required field is missing from JSON" in {
    val obj   = Map("a" -> Json.fromString("hello"))
    val types = Map("a" -> CType.CString, "b" -> CType.CInt) // "b" missing from JSON obj
    val lp    = LazyProductValue(obj, types)

    val result = lp.materialize
    result.isLeft shouldBe true
    result.left.toOption.get should include("b")
  }

  it should "report correct cType as CProduct" in {
    val types = Map("x" -> CType.CInt, "y" -> CType.CString)
    val lp    = LazyProductValue(Map.empty, types)

    lp.cType shouldBe CType.CProduct(types)
  }

  it should "report fieldNames from the type map" in {
    val types = Map("alpha" -> CType.CString, "beta" -> CType.CInt, "gamma" -> CType.CBoolean)
    val lp    = LazyProductValue(Map.empty, types)

    lp.fieldNames shouldBe Set("alpha", "beta", "gamma")
  }

  it should "handle empty product" in {
    val lp = LazyProductValue(Map.empty, Map.empty)

    lp.fieldNames shouldBe empty
    lp.materializedCount shouldBe 0
    val result = lp.materialize
    result.isRight shouldBe true
    result.toOption.get shouldBe CValue.CProduct(Map.empty, Map.empty)
  }

  "LazyProductValue.fromJson" should "create from a valid JSON object" in {
    val json   = Json.obj("x" -> Json.fromInt(1), "y" -> Json.fromString("two"))
    val types  = Map("x" -> CType.CInt, "y" -> CType.CString)
    val result = LazyProductValue.fromJson(json, types)

    result.isRight shouldBe true
    val lp = result.toOption.get
    lp.getField("x") shouldBe Right(Some(CValue.CInt(1L)))
    lp.getField("y") shouldBe Right(Some(CValue.CString("two")))
  }

  it should "fail for a JSON array" in {
    val json   = Json.arr(Json.fromInt(1))
    val result = LazyProductValue.fromJson(json, Map("x" -> CType.CInt))

    result.isLeft shouldBe true
  }

  it should "fail for a JSON string" in {
    val result = LazyProductValue.fromJson(Json.fromString("not an object"), Map("x" -> CType.CInt))

    result.isLeft shouldBe true
  }

  it should "fail for JSON null" in {
    val result = LazyProductValue.fromJson(Json.Null, Map("x" -> CType.CInt))

    result.isLeft shouldBe true
    result.left.toOption.get should include("null")
  }

  it should "fail for a JSON number" in {
    val result = LazyProductValue.fromJson(Json.fromInt(42), Map("x" -> CType.CInt))

    result.isLeft shouldBe true
  }
}
