package io.constellation

import io.circe.Json
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JsonCValueConverterTest extends AnyFlatSpec with Matchers {

  // ============================================================
  // JSON → CValue Conversion Tests
  // ============================================================

  "jsonToCValue" should "convert String JSON to CString" in {
    val json = Json.fromString("hello")
    val result = JsonCValueConverter.jsonToCValue(json, CType.CString)
    result shouldBe Right(CValue.CString("hello"))
  }

  it should "convert empty String JSON to CString" in {
    val json = Json.fromString("")
    val result = JsonCValueConverter.jsonToCValue(json, CType.CString)
    result shouldBe Right(CValue.CString(""))
  }

  it should "fail when expecting String but got number" in {
    val json = Json.fromInt(42)
    val result = JsonCValueConverter.jsonToCValue(json, CType.CString)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected String")) shouldBe true
  }

  it should "fail when expecting String but got null" in {
    val json = Json.Null
    val result = JsonCValueConverter.jsonToCValue(json, CType.CString)
    result.isLeft shouldBe true
  }

  it should "convert Int JSON to CInt" in {
    val json = Json.fromInt(42)
    val result = JsonCValueConverter.jsonToCValue(json, CType.CInt)
    result shouldBe Right(CValue.CInt(42))
  }

  it should "convert negative Int JSON to CInt" in {
    val json = Json.fromInt(-100)
    val result = JsonCValueConverter.jsonToCValue(json, CType.CInt)
    result shouldBe Right(CValue.CInt(-100))
  }

  it should "convert Long JSON to CInt" in {
    val json = Json.fromLong(Long.MaxValue)
    val result = JsonCValueConverter.jsonToCValue(json, CType.CInt)
    result shouldBe Right(CValue.CInt(Long.MaxValue))
  }

  it should "fail when expecting Int but got string" in {
    val json = Json.fromString("42")
    val result = JsonCValueConverter.jsonToCValue(json, CType.CInt)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
  }

  it should "fail when expecting Int but got boolean" in {
    val json = Json.fromBoolean(true)
    val result = JsonCValueConverter.jsonToCValue(json, CType.CInt)
    result.isLeft shouldBe true
  }

  it should "convert Float JSON to CFloat" in {
    val json = Json.fromDouble(3.14).get
    val result = JsonCValueConverter.jsonToCValue(json, CType.CFloat)
    result shouldBe Right(CValue.CFloat(3.14))
  }

  it should "convert Int JSON to CFloat" in {
    val json = Json.fromInt(42)
    val result = JsonCValueConverter.jsonToCValue(json, CType.CFloat)
    result shouldBe Right(CValue.CFloat(42.0))
  }

  it should "convert negative Float JSON to CFloat" in {
    val json = Json.fromDouble(-2.5).get
    val result = JsonCValueConverter.jsonToCValue(json, CType.CFloat)
    result shouldBe Right(CValue.CFloat(-2.5))
  }

  it should "fail when expecting Float but got string" in {
    val json = Json.fromString("3.14")
    val result = JsonCValueConverter.jsonToCValue(json, CType.CFloat)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Float")) shouldBe true
  }

  it should "convert Boolean JSON to CBoolean (true)" in {
    val json = Json.fromBoolean(true)
    val result = JsonCValueConverter.jsonToCValue(json, CType.CBoolean)
    result shouldBe Right(CValue.CBoolean(true))
  }

  it should "convert Boolean JSON to CBoolean (false)" in {
    val json = Json.fromBoolean(false)
    val result = JsonCValueConverter.jsonToCValue(json, CType.CBoolean)
    result shouldBe Right(CValue.CBoolean(false))
  }

  it should "fail when expecting Boolean but got number" in {
    val json = Json.fromInt(1)
    val result = JsonCValueConverter.jsonToCValue(json, CType.CBoolean)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Boolean")) shouldBe true
  }

  // List conversions
  it should "convert empty Array JSON to CList" in {
    val json = Json.arr()
    val result = JsonCValueConverter.jsonToCValue(json, CType.CList(CType.CInt))
    result shouldBe Right(CValue.CList(Vector.empty, CType.CInt))
  }

  it should "convert Array of Ints to CList" in {
    val json = Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CList(CType.CInt))
    result shouldBe Right(CValue.CList(
      Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
      CType.CInt
    ))
  }

  it should "convert Array of Strings to CList" in {
    val json = Json.arr(Json.fromString("a"), Json.fromString("b"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CList(CType.CString))
    result shouldBe Right(CValue.CList(
      Vector(CValue.CString("a"), CValue.CString("b")),
      CType.CString
    ))
  }

  it should "fail when Array contains wrong type" in {
    val json = Json.arr(Json.fromInt(1), Json.fromString("oops"), Json.fromInt(3))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CList(CType.CInt))
    result.isLeft shouldBe true
    result.left.exists(_.contains("[1]")) shouldBe true // Error path includes index
  }

  it should "fail when expecting Array but got object" in {
    val json = Json.obj("key" -> Json.fromInt(1))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CList(CType.CInt))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Array")) shouldBe true
  }

  // Map conversions
  it should "convert Object to CMap with string keys" in {
    val json = Json.obj("a" -> Json.fromInt(1), "b" -> Json.fromInt(2))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CString, CType.CInt))
    result.isRight shouldBe true
    val cmap = result.toOption.get.asInstanceOf[CValue.CMap]
    cmap.value.size shouldBe 2
  }

  it should "convert empty Object to CMap" in {
    val json = Json.obj()
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CString, CType.CInt))
    result shouldBe Right(CValue.CMap(Vector.empty, CType.CString, CType.CInt))
  }

  it should "convert Array of pairs to CMap with non-string keys" in {
    val json = Json.arr(
      Json.arr(Json.fromInt(1), Json.fromString("one")),
      Json.arr(Json.fromInt(2), Json.fromString("two"))
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CInt, CType.CString))
    result.isRight shouldBe true
    val cmap = result.toOption.get.asInstanceOf[CValue.CMap]
    cmap.value.size shouldBe 2
  }

  it should "fail when Map pair is not [key, value]" in {
    val json = Json.arr(
      Json.arr(Json.fromInt(1), Json.fromString("one")),
      Json.arr(Json.fromInt(2)) // Only one element
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CInt, CType.CString))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected [key, value] pair")) shouldBe true
  }

  it should "fail when Map value is wrong type" in {
    val json = Json.obj("a" -> Json.fromString("not an int"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CString, CType.CInt))
    result.isLeft shouldBe true
  }

  // Product conversions
  it should "convert Object to CProduct" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val json = Json.obj("name" -> Json.fromString("Alice"), "age" -> Json.fromInt(30))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))
    result.isRight shouldBe true
    val prod = result.toOption.get.asInstanceOf[CValue.CProduct]
    prod.value("name") shouldBe CValue.CString("Alice")
    prod.value("age") shouldBe CValue.CInt(30)
  }

  it should "fail when Product field is missing" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val json = Json.obj("name" -> Json.fromString("Alice")) // Missing age
    val result = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("missing required field 'age'")) shouldBe true
  }

  it should "fail when Product field is wrong type" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val json = Json.obj("name" -> Json.fromString("Alice"), "age" -> Json.fromString("thirty"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("age")) shouldBe true
  }

  it should "fail when expecting Object for Product but got array" in {
    val structure = Map("name" -> CType.CString)
    val json = Json.arr(Json.fromString("Alice"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Object")) shouldBe true
  }

  // Union conversions
  it should "convert tagged Union" in {
    val structure = Map("str" -> CType.CString, "num" -> CType.CInt)
    val json = Json.obj("tag" -> Json.fromString("str"), "value" -> Json.fromString("hello"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    result.isRight shouldBe true
    val union = result.toOption.get.asInstanceOf[CValue.CUnion]
    union.tag shouldBe "str"
    union.value shouldBe CValue.CString("hello")
  }

  it should "fail when Union tag is invalid" in {
    val structure = Map("str" -> CType.CString, "num" -> CType.CInt)
    val json = Json.obj("tag" -> Json.fromString("invalid"), "value" -> Json.fromString("hello"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("invalid union tag 'invalid'")) shouldBe true
  }

  it should "fail when Union is missing tag" in {
    val structure = Map("str" -> CType.CString)
    val json = Json.obj("value" -> Json.fromString("hello"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("union must have 'tag' and 'value' fields")) shouldBe true
  }

  it should "fail when Union tag is not a string" in {
    val structure = Map("str" -> CType.CString)
    val json = Json.obj("tag" -> Json.fromInt(1), "value" -> Json.fromString("hello"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("union tag must be a string")) shouldBe true
  }

  // Optional conversions
  it should "convert null to CNone" in {
    val json = Json.Null
    val result = JsonCValueConverter.jsonToCValue(json, CType.COptional(CType.CInt))
    result shouldBe Right(CValue.CNone(CType.CInt))
  }

  it should "convert value to CSome" in {
    val json = Json.fromInt(42)
    val result = JsonCValueConverter.jsonToCValue(json, CType.COptional(CType.CInt))
    result shouldBe Right(CValue.CSome(CValue.CInt(42), CType.CInt))
  }

  it should "fail when Optional inner type is wrong" in {
    val json = Json.fromString("not an int")
    val result = JsonCValueConverter.jsonToCValue(json, CType.COptional(CType.CInt))
    result.isLeft shouldBe true
  }

  // Nested structures
  it should "convert deeply nested structure" in {
    val innerProduct = CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CInt))
    val listOfProducts = CType.CList(innerProduct)
    val json = Json.arr(
      Json.obj("x" -> Json.fromInt(1), "y" -> Json.fromInt(2)),
      Json.obj("x" -> Json.fromInt(3), "y" -> Json.fromInt(4))
    )
    val result = JsonCValueConverter.jsonToCValue(json, listOfProducts)
    result.isRight shouldBe true
    val list = result.toOption.get.asInstanceOf[CValue.CList]
    list.value.size shouldBe 2
  }

  it should "report error path for deeply nested error" in {
    val innerProduct = CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CInt))
    val listOfProducts = CType.CList(innerProduct)
    val json = Json.arr(
      Json.obj("x" -> Json.fromInt(1), "y" -> Json.fromInt(2)),
      Json.obj("x" -> Json.fromString("wrong"), "y" -> Json.fromInt(4))
    )
    val result = JsonCValueConverter.jsonToCValue(json, listOfProducts)
    result.isLeft shouldBe true
    result.left.exists(_.contains("[1]")) shouldBe true
    result.left.exists(_.contains("x")) shouldBe true
  }

  // ============================================================
  // CValue → JSON Conversion Tests
  // ============================================================

  "cValueToJson" should "convert CString to JSON string" in {
    val value = CValue.CString("hello")
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.fromString("hello")
  }

  it should "convert empty CString to JSON string" in {
    val value = CValue.CString("")
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.fromString("")
  }

  it should "convert CInt to JSON number" in {
    val value = CValue.CInt(42)
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.fromLong(42)
  }

  it should "convert negative CInt to JSON number" in {
    val value = CValue.CInt(-100)
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.fromLong(-100)
  }

  it should "convert CFloat to JSON number" in {
    val value = CValue.CFloat(3.14)
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.fromDouble(3.14).get
  }

  it should "convert CBoolean true to JSON boolean" in {
    val value = CValue.CBoolean(true)
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.fromBoolean(true)
  }

  it should "convert CBoolean false to JSON boolean" in {
    val value = CValue.CBoolean(false)
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.fromBoolean(false)
  }

  it should "convert CList to JSON array" in {
    val value = CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2)), CType.CInt)
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.arr(Json.fromInt(1), Json.fromInt(2))
  }

  it should "convert empty CList to empty JSON array" in {
    val value = CValue.CList(Vector.empty, CType.CInt)
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.arr()
  }

  it should "convert CMap with string keys to JSON object" in {
    val value = CValue.CMap(
      Vector((CValue.CString("a"), CValue.CInt(1))),
      CType.CString, CType.CInt
    )
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.obj("a" -> Json.fromInt(1))
  }

  it should "convert CMap with non-string keys to JSON array of pairs" in {
    val value = CValue.CMap(
      Vector((CValue.CInt(1), CValue.CString("one"))),
      CType.CInt, CType.CString
    )
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.arr(Json.arr(Json.fromInt(1), Json.fromString("one")))
  }

  it should "convert CProduct to JSON object" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val value = CValue.CProduct(
      Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30)),
      structure
    )
    val result = JsonCValueConverter.cValueToJson(value)
    result.hcursor.downField("name").as[String].toOption shouldBe Some("Alice")
    result.hcursor.downField("age").as[Int].toOption shouldBe Some(30)
  }

  it should "convert CUnion to JSON with tag and value" in {
    val structure = Map("str" -> CType.CString, "num" -> CType.CInt)
    val value = CValue.CUnion(CValue.CString("hello"), structure, "str")
    val result = JsonCValueConverter.cValueToJson(value)
    result.hcursor.downField("tag").as[String].toOption shouldBe Some("str")
    result.hcursor.downField("value").as[String].toOption shouldBe Some("hello")
  }

  it should "convert CSome to inner JSON value" in {
    val value = CValue.CSome(CValue.CInt(42), CType.CInt)
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.fromInt(42)
  }

  it should "convert CNone to JSON null" in {
    val value = CValue.CNone(CType.CInt)
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.Null
  }

  // ============================================================
  // JSON → RawValue Conversion Tests
  // ============================================================

  "jsonToRawValue" should "convert String JSON to RString" in {
    val json = Json.fromString("hello")
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CString)
    result shouldBe Right(RawValue.RString("hello"))
  }

  it should "convert Int JSON to RInt" in {
    val json = Json.fromInt(42)
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CInt)
    result shouldBe Right(RawValue.RInt(42))
  }

  it should "convert Float JSON to RFloat" in {
    val json = Json.fromDouble(3.14).get
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CFloat)
    result shouldBe Right(RawValue.RFloat(3.14))
  }

  it should "convert Boolean JSON to RBool" in {
    val json = Json.fromBoolean(true)
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CBoolean)
    result shouldBe Right(RawValue.RBool(true))
  }

  // Fast path tests for specialized arrays
  it should "convert Int array to RIntList (fast path)" in {
    val json = Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromInt(3))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CInt))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RIntList(arr) =>
        arr.toList shouldBe List(1L, 2L, 3L)
      case _ => fail("Expected RIntList")
    }
  }

  it should "convert empty Int array to RIntList" in {
    val json = Json.arr()
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CInt))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RIntList(arr) => arr.length shouldBe 0
      case _ => fail("Expected RIntList")
    }
  }

  it should "fail when Int array contains non-number" in {
    val json = Json.arr(Json.fromInt(1), Json.fromString("oops"), Json.fromInt(3))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CInt))
    result.isLeft shouldBe true
    result.left.exists(_.contains("[1]")) shouldBe true
  }

  it should "convert Float array to RFloatList (fast path)" in {
    val json = Json.arr(Json.fromDouble(1.1).get, Json.fromDouble(2.2).get)
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CFloat))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RFloatList(arr) =>
        arr.toList shouldBe List(1.1, 2.2)
      case _ => fail("Expected RFloatList")
    }
  }

  it should "fail when Float array contains non-number" in {
    val json = Json.arr(Json.fromDouble(1.1).get, Json.fromBoolean(true))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CFloat))
    result.isLeft shouldBe true
  }

  it should "convert String array to RStringList (fast path)" in {
    val json = Json.arr(Json.fromString("a"), Json.fromString("b"))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CString))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RStringList(arr) =>
        arr.toList shouldBe List("a", "b")
      case _ => fail("Expected RStringList")
    }
  }

  it should "fail when String array contains non-string" in {
    val json = Json.arr(Json.fromString("a"), Json.fromInt(1))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CString))
    result.isLeft shouldBe true
  }

  it should "convert Boolean array to RBoolList (fast path)" in {
    val json = Json.arr(Json.fromBoolean(true), Json.fromBoolean(false))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CBoolean))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RBoolList(arr) =>
        arr.toList shouldBe List(true, false)
      case _ => fail("Expected RBoolList")
    }
  }

  it should "fail when Boolean array contains non-boolean" in {
    val json = Json.arr(Json.fromBoolean(true), Json.fromString("oops"))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CBoolean))
    result.isLeft shouldBe true
  }

  // Generic list path for nested types
  it should "convert nested list to RList (generic path)" in {
    val nestedType = CType.CList(CType.CList(CType.CInt))
    val json = Json.arr(
      Json.arr(Json.fromInt(1), Json.fromInt(2)),
      Json.arr(Json.fromInt(3))
    )
    val result = JsonCValueConverter.jsonToRawValue(json, nestedType)
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RList(arr) => arr.length shouldBe 2
      case _ => fail("Expected RList")
    }
  }

  // Map conversions
  it should "convert Object to RMap with string keys" in {
    val json = Json.obj("a" -> Json.fromInt(1))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CMap(CType.CString, CType.CInt))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RMap(pairs) =>
        pairs.length shouldBe 1
      case _ => fail("Expected RMap")
    }
  }

  // Product conversions
  it should "convert Object to RProduct" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val json = Json.obj("name" -> Json.fromString("Alice"), "age" -> Json.fromInt(30))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CProduct(structure))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RProduct(fields) =>
        fields.length shouldBe 2
      case _ => fail("Expected RProduct")
    }
  }

  // Union conversions
  it should "convert tagged Union to RUnion" in {
    val structure = Map("str" -> CType.CString, "num" -> CType.CInt)
    val json = Json.obj("tag" -> Json.fromString("str"), "value" -> Json.fromString("hello"))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CUnion(structure))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RUnion(tag, value) =>
        tag shouldBe "str"
        value shouldBe RawValue.RString("hello")
      case _ => fail("Expected RUnion")
    }
  }

  // Optional conversions
  it should "convert null to RNone" in {
    val json = Json.Null
    val result = JsonCValueConverter.jsonToRawValue(json, CType.COptional(CType.CInt))
    result shouldBe Right(RawValue.RNone)
  }

  it should "convert value to RSome" in {
    val json = Json.fromInt(42)
    val result = JsonCValueConverter.jsonToRawValue(json, CType.COptional(CType.CInt))
    result shouldBe Right(RawValue.RSome(RawValue.RInt(42)))
  }

  // ============================================================
  // RawValue → JSON Conversion Tests
  // ============================================================

  "rawValueToJson" should "convert RString to JSON string" in {
    val value = RawValue.RString("hello")
    val result = JsonCValueConverter.rawValueToJson(value, CType.CString)
    result shouldBe Json.fromString("hello")
  }

  it should "convert RInt to JSON number" in {
    val value = RawValue.RInt(42)
    val result = JsonCValueConverter.rawValueToJson(value, CType.CInt)
    result shouldBe Json.fromLong(42)
  }

  it should "convert RFloat to JSON number" in {
    val value = RawValue.RFloat(3.14)
    val result = JsonCValueConverter.rawValueToJson(value, CType.CFloat)
    result shouldBe Json.fromDouble(3.14).get
  }

  it should "convert RBool to JSON boolean" in {
    val value = RawValue.RBool(true)
    val result = JsonCValueConverter.rawValueToJson(value, CType.CBoolean)
    result shouldBe Json.fromBoolean(true)
  }

  it should "convert RIntList to JSON array" in {
    val value = RawValue.RIntList(Array(1L, 2L, 3L))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CInt))
    result shouldBe Json.arr(Json.fromLong(1), Json.fromLong(2), Json.fromLong(3))
  }

  it should "convert RFloatList to JSON array" in {
    val value = RawValue.RFloatList(Array(1.1, 2.2))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CFloat))
    result shouldBe Json.arr(Json.fromDouble(1.1).get, Json.fromDouble(2.2).get)
  }

  it should "convert RStringList to JSON array" in {
    val value = RawValue.RStringList(Array("a", "b"))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CString))
    result shouldBe Json.arr(Json.fromString("a"), Json.fromString("b"))
  }

  it should "convert RBoolList to JSON array" in {
    val value = RawValue.RBoolList(Array(true, false))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CBoolean))
    result shouldBe Json.arr(Json.fromBoolean(true), Json.fromBoolean(false))
  }

  it should "convert RList to JSON array" in {
    val value = RawValue.RList(Array(RawValue.RInt(1), RawValue.RInt(2)))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CInt))
    result shouldBe Json.arr(Json.fromLong(1), Json.fromLong(2))
  }

  it should "convert RMap with string keys to JSON object" in {
    val value = RawValue.RMap(Array((RawValue.RString("a"), RawValue.RInt(1))))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CMap(CType.CString, CType.CInt))
    result shouldBe Json.obj("a" -> Json.fromInt(1))
  }

  it should "convert RMap with non-string keys to JSON array of pairs" in {
    val value = RawValue.RMap(Array((RawValue.RInt(1), RawValue.RString("one"))))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CMap(CType.CInt, CType.CString))
    result shouldBe Json.arr(Json.arr(Json.fromInt(1), Json.fromString("one")))
  }

  it should "convert RProduct to JSON object" in {
    // RProduct stores values by index according to sorted key order in structure
    // For Map("name" -> ..., "age" -> ...), sorted keys are ["age", "name"]
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val value = RawValue.RProduct(Array(
      RawValue.RInt(30),      // age (index 0, first alphabetically)
      RawValue.RString("Alice") // name (index 1)
    ))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CProduct(structure))
    result.hcursor.downField("name").as[String].toOption shouldBe Some("Alice")
    result.hcursor.downField("age").as[Int].toOption shouldBe Some(30)
  }

  it should "convert RUnion to JSON with tag and value" in {
    val structure = Map("str" -> CType.CString)
    val value = RawValue.RUnion("str", RawValue.RString("hello"))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CUnion(structure))
    result.hcursor.downField("tag").as[String].toOption shouldBe Some("str")
    result.hcursor.downField("value").as[String].toOption shouldBe Some("hello")
  }

  it should "convert RSome to inner JSON value" in {
    val value = RawValue.RSome(RawValue.RInt(42))
    val result = JsonCValueConverter.rawValueToJson(value, CType.COptional(CType.CInt))
    result shouldBe Json.fromInt(42)
  }

  it should "convert RNone to JSON null" in {
    val value = RawValue.RNone
    val result = JsonCValueConverter.rawValueToJson(value, CType.COptional(CType.CInt))
    result shouldBe Json.Null
  }
}
