package io.constellation

import io.circe.Json

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LazyValueTest extends AnyFlatSpec with Matchers {

  // ===== LazyJsonValue =====

  "LazyJsonValue" should "defer conversion until materialize is called" in {
    val lazy1 = LazyJsonValue(Json.fromString("hello"), CType.CString)
    lazy1.isMaterialized shouldBe false
    lazy1.materialize shouldBe Right(CValue.CString("hello"))
    lazy1.isMaterialized shouldBe true
  }

  it should "cache materialized result" in {
    val lazy1  = LazyJsonValue(Json.fromInt(42), CType.CInt)
    val first  = lazy1.materialize
    val second = lazy1.materialize
    first shouldBe second
    first shouldBe Right(CValue.CInt(42L))
  }

  it should "report correct cType" in {
    val lazy1 = LazyJsonValue(Json.fromBoolean(true), CType.CBoolean)
    lazy1.cType shouldBe CType.CBoolean
  }

  it should "materialize boolean values" in {
    LazyJsonValue(Json.fromBoolean(false), CType.CBoolean).materialize shouldBe Right(
      CValue.CBoolean(false)
    )
  }

  it should "materialize float values" in {
    LazyJsonValue(Json.fromDoubleOrNull(3.14), CType.CFloat).materialize shouldBe Right(
      CValue.CFloat(3.14)
    )
  }

  // ===== LazyListValue =====

  "LazyListValue" should "convert elements on demand" in {
    val arr   = Vector(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))
    val lazy1 = LazyListValue(arr, CType.CInt)

    lazy1.size shouldBe 3
    lazy1.materializedCount shouldBe 0

    lazy1.get(0) shouldBe Right(CValue.CInt(1L))
    lazy1.materializedCount shouldBe 1

    lazy1.get(2) shouldBe Right(CValue.CInt(3L))
    lazy1.materializedCount shouldBe 2
  }

  it should "return error for out-of-bounds index" in {
    val arr   = Vector(Json.fromInt(1))
    val lazy1 = LazyListValue(arr, CType.CInt)

    lazy1.get(-1).isLeft shouldBe true
    lazy1.get(1).isLeft shouldBe true
  }

  it should "cache accessed elements" in {
    val arr   = Vector(Json.fromString("a"), Json.fromString("b"))
    val lazy1 = LazyListValue(arr, CType.CString)

    lazy1.get(0) shouldBe Right(CValue.CString("a"))
    lazy1.get(0) shouldBe Right(CValue.CString("a"))
    lazy1.materializedCount shouldBe 1
  }

  it should "materialize all elements" in {
    val arr   = Vector(Json.fromInt(10), Json.fromInt(20))
    val lazy1 = LazyListValue(arr, CType.CInt)

    val result = lazy1.materialize
    result shouldBe Right(
      CValue.CList(Vector(CValue.CInt(10L), CValue.CInt(20L)), CType.CInt)
    )
    lazy1.isFullyMaterialized shouldBe true
  }

  it should "report correct cType" in {
    val lazy1 = LazyListValue(Vector.empty, CType.CString)
    lazy1.cType shouldBe CType.CList(CType.CString)
  }

  it should "handle empty array" in {
    val lazy1 = LazyListValue(Vector.empty, CType.CInt)
    lazy1.size shouldBe 0
    lazy1.isFullyMaterialized shouldBe true
    lazy1.materialize shouldBe Right(CValue.CList(Vector.empty, CType.CInt))
  }

  "LazyListValue.fromJson" should "create from JSON array" in {
    val json   = Json.arr(Json.fromInt(1), Json.fromInt(2))
    val result = LazyListValue.fromJson(json, CType.CInt)
    result.isRight shouldBe true
    result.toOption.get.size shouldBe 2
  }

  it should "fail for non-array JSON" in {
    val result = LazyListValue.fromJson(Json.fromString("not array"), CType.CInt)
    result.isLeft shouldBe true
  }

  it should "fail for null JSON" in {
    val result = LazyListValue.fromJson(Json.Null, CType.CInt)
    result.isLeft shouldBe true
  }

  // ===== LazyProductValue =====

  "LazyProductValue" should "convert fields on demand" in {
    val obj = Map(
      "name" -> Json.fromString("Alice"),
      "age"  -> Json.fromInt(30)
    )
    val types = Map("name" -> CType.CString, "age" -> CType.CInt)
    val lazy1 = LazyProductValue(obj, types)

    lazy1.materializedCount shouldBe 0

    lazy1.getField("name") shouldBe Right(Some(CValue.CString("Alice")))
    lazy1.materializedCount shouldBe 1

    lazy1.getField("age") shouldBe Right(Some(CValue.CInt(30L)))
    lazy1.materializedCount shouldBe 2
  }

  it should "return Right(None) for unknown field" in {
    val obj   = Map("name" -> Json.fromString("Alice"))
    val types = Map("name" -> CType.CString)
    val lazy1 = LazyProductValue(obj, types)

    lazy1.getField("unknown") shouldBe Right(None)
  }

  it should "return error for missing required field" in {
    val obj   = Map.empty[String, Json]
    val types = Map("name" -> CType.CString)
    val lazy1 = LazyProductValue(obj, types)

    lazy1.getField("name").isLeft shouldBe true
  }

  it should "get field required" in {
    val obj   = Map("x" -> Json.fromInt(5))
    val types = Map("x" -> CType.CInt)
    val lazy1 = LazyProductValue(obj, types)

    lazy1.getFieldRequired("x") shouldBe Right(CValue.CInt(5L))
  }

  it should "fail getFieldRequired for unknown field" in {
    val obj   = Map("x" -> Json.fromInt(5))
    val types = Map("x" -> CType.CInt)
    val lazy1 = LazyProductValue(obj, types)

    lazy1.getFieldRequired("y").isLeft shouldBe true
  }

  it should "materialize all fields" in {
    val obj   = Map("a" -> Json.fromString("hello"), "b" -> Json.fromInt(42))
    val types = Map("a" -> CType.CString, "b" -> CType.CInt)
    val lazy1 = LazyProductValue(obj, types)

    val result = lazy1.materialize
    result.isRight shouldBe true
    val product = result.toOption.get.asInstanceOf[CValue.CProduct]
    product.value("a") shouldBe CValue.CString("hello")
    product.value("b") shouldBe CValue.CInt(42L)
  }

  it should "report correct cType" in {
    val types = Map("x" -> CType.CInt)
    val lazy1 = LazyProductValue(Map.empty, types)
    lazy1.cType shouldBe CType.CProduct(types)
  }

  it should "report field names" in {
    val types = Map("a" -> CType.CString, "b" -> CType.CInt)
    val lazy1 = LazyProductValue(Map.empty, types)
    lazy1.fieldNames shouldBe Set("a", "b")
  }

  "LazyProductValue.fromJson" should "create from JSON object" in {
    val json   = Json.obj("x" -> Json.fromInt(1))
    val types  = Map("x" -> CType.CInt)
    val result = LazyProductValue.fromJson(json, types)
    result.isRight shouldBe true
  }

  it should "fail for non-object JSON" in {
    val result = LazyProductValue.fromJson(Json.fromInt(42), Map("x" -> CType.CInt))
    result.isLeft shouldBe true
  }

  it should "fail for null JSON" in {
    val result = LazyProductValue.fromJson(Json.Null, Map("x" -> CType.CInt))
    result.isLeft shouldBe true
  }
}
