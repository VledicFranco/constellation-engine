package io.constellation

import io.circe.Json
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JsonCValueConverterEdgeCaseTest extends AnyFlatSpec with Matchers {

  // ============================================================
  // jsonToRawValue - Specialized List Paths (RStringList, RBoolList, RFloatList, RIntList)
  // ============================================================

  "jsonToRawValue specialized lists" should "produce RStringList for empty string array" in {
    val json   = Json.arr()
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CString))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RStringList(arr) => arr.length shouldBe 0
      case other                     => fail(s"Expected RStringList, got $other")
    }
  }

  it should "produce RBoolList for empty boolean array" in {
    val json   = Json.arr()
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CBoolean))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RBoolList(arr) => arr.length shouldBe 0
      case other                   => fail(s"Expected RBoolList, got $other")
    }
  }

  it should "produce RFloatList for empty float array" in {
    val json   = Json.arr()
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CFloat))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RFloatList(arr) => arr.length shouldBe 0
      case other                    => fail(s"Expected RFloatList, got $other")
    }
  }

  it should "handle RIntList with Long.MaxValue" in {
    val json   = Json.arr(Json.fromLong(Long.MaxValue), Json.fromLong(Long.MinValue))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CInt))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RIntList(arr) =>
        arr(0) shouldBe Long.MaxValue
        arr(1) shouldBe Long.MinValue
      case other => fail(s"Expected RIntList, got $other")
    }
  }

  it should "handle RFloatList with precision edge values" in {
    val json = Json.arr(
      Json.fromDouble(Double.MinPositiveValue).get,
      Json.fromDouble(1.7976931348623157e308).get, // near Double.MaxValue
      Json.fromDouble(0.0).get
    )
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CFloat))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RFloatList(arr) =>
        arr(0) shouldBe Double.MinPositiveValue
        arr(2) shouldBe 0.0
      case other => fail(s"Expected RFloatList, got $other")
    }
  }

  it should "handle RStringList with unicode and special characters" in {
    val json   = Json.arr(Json.fromString(""), Json.fromString("hello\nworld"), Json.fromString("\u00e9\u00e0\u00fc"))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CString))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RStringList(arr) =>
        arr(0) shouldBe ""
        arr(1) shouldBe "hello\nworld"
        arr(2) shouldBe "\u00e9\u00e0\u00fc"
      case other => fail(s"Expected RStringList, got $other")
    }
  }

  it should "handle large RIntList" in {
    val elements = (1 to 1000).map(i => Json.fromLong(i.toLong)).toVector
    val json     = Json.fromValues(elements)
    val result   = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CInt))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RIntList(arr) =>
        arr.length shouldBe 1000
        arr(0) shouldBe 1L
        arr(999) shouldBe 1000L
      case other => fail(s"Expected RIntList, got $other")
    }
  }

  it should "report correct index in error for RStringList with non-string element" in {
    val json   = Json.arr(Json.fromString("ok"), Json.fromString("fine"), Json.fromInt(42))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CString))
    result.isLeft shouldBe true
    result.left.exists(_.contains("[2]")) shouldBe true
    result.left.exists(_.contains("expected String")) shouldBe true
  }

  it should "report correct index in error for RBoolList with non-boolean element" in {
    val json   = Json.arr(Json.fromBoolean(true), Json.fromString("nope"))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CBoolean))
    result.isLeft shouldBe true
    result.left.exists(_.contains("[1]")) shouldBe true
    result.left.exists(_.contains("expected Boolean")) shouldBe true
  }

  it should "report correct index in error for RIntList with non-int element" in {
    val json   = Json.arr(Json.fromInt(1), Json.fromInt(2), Json.fromBoolean(false))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CInt))
    result.isLeft shouldBe true
    result.left.exists(_.contains("[2]")) shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
  }

  it should "report correct index in error for RFloatList with non-number element" in {
    val json   = Json.arr(Json.fromDouble(1.0).get, Json.fromString("bad"))
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CList(CType.CFloat))
    result.isLeft shouldBe true
    result.left.exists(_.contains("[1]")) shouldBe true
    result.left.exists(_.contains("expected Float")) shouldBe true
  }

  // ============================================================
  // jsonToRawValue - Nested Product/Record Values
  // ============================================================

  "jsonToRawValue nested structures" should "convert nested product (record within record)" in {
    val innerProduct = CType.CProduct(Map("street" -> CType.CString, "city" -> CType.CString))
    val outerProduct = CType.CProduct(Map("name" -> CType.CString, "address" -> innerProduct))
    val json = Json.obj(
      "name" -> Json.fromString("Alice"),
      "address" -> Json.obj(
        "street" -> Json.fromString("123 Main St"),
        "city"   -> Json.fromString("Springfield")
      )
    )
    val result = JsonCValueConverter.jsonToRawValue(json, outerProduct)
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RProduct(fields) =>
        // Sorted by key: "address" (index 0), "name" (index 1)
        fields.length shouldBe 2
        fields(0) match {
          case RawValue.RProduct(inner) =>
            inner.length shouldBe 2
            // Sorted: "city" (index 0), "street" (index 1)
            inner(0) shouldBe RawValue.RString("Springfield")
            inner(1) shouldBe RawValue.RString("123 Main St")
          case other => fail(s"Expected inner RProduct, got $other")
        }
        fields(1) shouldBe RawValue.RString("Alice")
      case other => fail(s"Expected RProduct, got $other")
    }
  }

  it should "convert deeply nested structure (3 levels)" in {
    val level3 = CType.CProduct(Map("value" -> CType.CInt))
    val level2 = CType.CProduct(Map("inner" -> level3))
    val level1 = CType.CProduct(Map("middle" -> level2))
    val json = Json.obj(
      "middle" -> Json.obj(
        "inner" -> Json.obj(
          "value" -> Json.fromInt(42)
        )
      )
    )
    val result = JsonCValueConverter.jsonToRawValue(json, level1)
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RProduct(Array(RawValue.RProduct(Array(RawValue.RProduct(Array(RawValue.RInt(v))))))) =>
        v shouldBe 42L
      case other => fail(s"Expected nested RProduct, got $other")
    }
  }

  it should "report nested error path for product field type mismatch" in {
    val innerProduct = CType.CProduct(Map("x" -> CType.CInt))
    val outerProduct = CType.CProduct(Map("nested" -> innerProduct))
    val json = Json.obj(
      "nested" -> Json.obj(
        "x" -> Json.fromString("not an int")
      )
    )
    val result = JsonCValueConverter.jsonToRawValue(json, outerProduct)
    result.isLeft shouldBe true
    result.left.exists(_.contains("nested")) shouldBe true
    result.left.exists(_.contains("x")) shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
  }

  it should "handle product with multiple field type errors" in {
    val structure = Map("a" -> CType.CInt, "b" -> CType.CInt, "c" -> CType.CInt)
    val json = Json.obj(
      "a" -> Json.fromString("bad"),
      "b" -> Json.fromString("also bad"),
      "c" -> Json.fromInt(1) // only this is correct
    )
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CProduct(structure))
    result.isLeft shouldBe true
    // Multiple errors should be joined
    val err = result.left.toOption.get
    err.contains("a") shouldBe true
    err.contains("b") shouldBe true
  }

  it should "convert list of products via generic RList path" in {
    val productType = CType.CProduct(Map("id" -> CType.CInt, "label" -> CType.CString))
    val listType    = CType.CList(productType)
    val json = Json.arr(
      Json.obj("id" -> Json.fromInt(1), "label" -> Json.fromString("first")),
      Json.obj("id" -> Json.fromInt(2), "label" -> Json.fromString("second"))
    )
    val result = JsonCValueConverter.jsonToRawValue(json, listType)
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RList(arr) =>
        arr.length shouldBe 2
        arr(0) match {
          case RawValue.RProduct(fields) => fields.length shouldBe 2
          case other                     => fail(s"Expected RProduct, got $other")
        }
      case other => fail(s"Expected RList, got $other")
    }
  }

  it should "fail on generic list when inner element fails" in {
    val productType = CType.CProduct(Map("x" -> CType.CInt))
    val listType    = CType.CList(productType)
    val json = Json.arr(
      Json.obj("x" -> Json.fromInt(1)),
      Json.obj("x" -> Json.fromString("bad"))
    )
    val result = JsonCValueConverter.jsonToRawValue(json, listType)
    result.isLeft shouldBe true
    result.left.exists(_.contains("[1]")) shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
  }

  // ============================================================
  // jsonToRawValue - Map with Non-String Keys (Array of Pairs)
  // ============================================================

  "jsonToRawValue maps" should "convert array of pairs with int keys to RMap" in {
    val json = Json.arr(
      Json.arr(Json.fromInt(10), Json.fromString("ten")),
      Json.arr(Json.fromInt(20), Json.fromString("twenty"))
    )
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CMap(CType.CInt, CType.CString))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RMap(entries) =>
        entries.length shouldBe 2
        entries(0) shouldBe (RawValue.RInt(10), RawValue.RString("ten"))
        entries(1) shouldBe (RawValue.RInt(20), RawValue.RString("twenty"))
      case other => fail(s"Expected RMap, got $other")
    }
  }

  it should "convert empty object to RMap with string keys" in {
    val json   = Json.obj()
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CMap(CType.CString, CType.CInt))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RMap(entries) => entries.length shouldBe 0
      case other                  => fail(s"Expected RMap, got $other")
    }
  }

  it should "convert empty array to RMap with non-string keys" in {
    val json   = Json.arr()
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CMap(CType.CInt, CType.CString))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RMap(entries) => entries.length shouldBe 0
      case other                  => fail(s"Expected RMap, got $other")
    }
  }

  it should "fail when non-string key map pair has wrong key type" in {
    val json = Json.arr(
      Json.arr(Json.fromString("not-an-int"), Json.fromString("value"))
    )
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CMap(CType.CInt, CType.CString))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
  }

  it should "fail when non-string key map pair has wrong value type" in {
    val json = Json.arr(
      Json.arr(Json.fromInt(1), Json.fromInt(42))
    )
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CMap(CType.CInt, CType.CString))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected String")) shouldBe true
  }

  it should "handle map with product values" in {
    val valueType = CType.CProduct(Map("score" -> CType.CFloat))
    val json      = Json.obj("alice" -> Json.obj("score" -> Json.fromDouble(9.5).get))
    val result    = JsonCValueConverter.jsonToRawValue(json, CType.CMap(CType.CString, valueType))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RMap(entries) =>
        entries.length shouldBe 1
        entries(0)._1 shouldBe RawValue.RString("alice")
        entries(0)._2 match {
          case RawValue.RProduct(fields) =>
            fields.length shouldBe 1
            fields(0) shouldBe RawValue.RFloat(9.5)
          case other => fail(s"Expected RProduct, got $other")
        }
      case other => fail(s"Expected RMap, got $other")
    }
  }

  // ============================================================
  // jsonToRawValue - Union Paths
  // ============================================================

  "jsonToRawValue union" should "convert tagged union with product value" in {
    val structure = Map(
      "success" -> CType.CProduct(Map("data" -> CType.CString)),
      "error"   -> CType.CProduct(Map("code" -> CType.CInt))
    )
    val json = Json.obj(
      "tag"   -> Json.fromString("success"),
      "value" -> Json.obj("data" -> Json.fromString("ok"))
    )
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CUnion(structure))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RUnion(tag, value) =>
        tag shouldBe "success"
        value match {
          case RawValue.RProduct(fields) =>
            fields.length shouldBe 1
            fields(0) shouldBe RawValue.RString("ok")
          case other => fail(s"Expected RProduct, got $other")
        }
      case other => fail(s"Expected RUnion, got $other")
    }
  }

  it should "fail when union value type does not match tagged variant" in {
    val structure = Map("num" -> CType.CInt)
    val json = Json.obj(
      "tag"   -> Json.fromString("num"),
      "value" -> Json.fromString("not a number")
    )
    val result = JsonCValueConverter.jsonToRawValue(json, CType.CUnion(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
  }

  // ============================================================
  // jsonToRawValue - Optional Paths
  // ============================================================

  "jsonToRawValue optional" should "convert optional product from JSON" in {
    val innerType = CType.CProduct(Map("x" -> CType.CInt))
    val json      = Json.obj("x" -> Json.fromInt(99))
    val result    = JsonCValueConverter.jsonToRawValue(json, CType.COptional(innerType))
    result.isRight shouldBe true
    result.toOption.get match {
      case RawValue.RSome(RawValue.RProduct(fields)) =>
        fields(0) shouldBe RawValue.RInt(99)
      case other => fail(s"Expected RSome(RProduct(...)), got $other")
    }
  }

  it should "fail when optional inner type mismatches" in {
    val json   = Json.fromString("not a number")
    val result = JsonCValueConverter.jsonToRawValue(json, CType.COptional(CType.CInt))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
  }

  // ============================================================
  // jsonToRawValue - Null Handling
  // ============================================================

  "jsonToRawValue null handling" should "fail when null for non-optional String" in {
    val result = JsonCValueConverter.jsonToRawValue(Json.Null, CType.CString)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected String")) shouldBe true
    result.left.exists(_.contains("null")) shouldBe true
  }

  it should "fail when null for non-optional Int" in {
    val result = JsonCValueConverter.jsonToRawValue(Json.Null, CType.CInt)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
  }

  it should "fail when null for non-optional Boolean" in {
    val result = JsonCValueConverter.jsonToRawValue(Json.Null, CType.CBoolean)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Boolean")) shouldBe true
  }

  it should "fail when null for non-optional Float" in {
    val result = JsonCValueConverter.jsonToRawValue(Json.Null, CType.CFloat)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Float")) shouldBe true
  }

  // ============================================================
  // jsonToCValue - CProduct with Nested CValues
  // ============================================================

  "jsonToCValue nested products" should "convert product containing a list" in {
    val structure = Map("name" -> CType.CString, "scores" -> CType.CList(CType.CInt))
    val json = Json.obj(
      "name"   -> Json.fromString("Bob"),
      "scores" -> Json.arr(Json.fromInt(10), Json.fromInt(20))
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))
    result.isRight shouldBe true
    val prod = result.toOption.get.asInstanceOf[CValue.CProduct]
    prod.value("name") shouldBe CValue.CString("Bob")
    prod.value("scores") shouldBe CValue.CList(
      Vector(CValue.CInt(10), CValue.CInt(20)),
      CType.CInt
    )
  }

  it should "convert product containing a map" in {
    val structure = Map("data" -> CType.CMap(CType.CString, CType.CInt))
    val json = Json.obj(
      "data" -> Json.obj("x" -> Json.fromInt(1), "y" -> Json.fromInt(2))
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))
    result.isRight shouldBe true
    val prod = result.toOption.get.asInstanceOf[CValue.CProduct]
    val cmap = prod.value("data").asInstanceOf[CValue.CMap]
    cmap.value.size shouldBe 2
  }

  it should "convert product containing an optional field (present)" in {
    val structure = Map("maybe" -> CType.COptional(CType.CString))
    val json      = Json.obj("maybe" -> Json.fromString("here"))
    val result    = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))
    result.isRight shouldBe true
    val prod = result.toOption.get.asInstanceOf[CValue.CProduct]
    prod.value("maybe") shouldBe CValue.CSome(CValue.CString("here"), CType.CString)
  }

  it should "convert product containing an optional field (null)" in {
    val structure = Map("maybe" -> CType.COptional(CType.CString))
    val json      = Json.obj("maybe" -> Json.Null)
    val result    = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))
    result.isRight shouldBe true
    val prod = result.toOption.get.asInstanceOf[CValue.CProduct]
    prod.value("maybe") shouldBe CValue.CNone(CType.CString)
  }

  it should "convert product containing a nested product" in {
    val inner = CType.CProduct(Map("a" -> CType.CInt, "b" -> CType.CInt))
    val outer = CType.CProduct(Map("nested" -> inner, "label" -> CType.CString))
    val json = Json.obj(
      "label"  -> Json.fromString("test"),
      "nested" -> Json.obj("a" -> Json.fromInt(1), "b" -> Json.fromInt(2))
    )
    val result = JsonCValueConverter.jsonToCValue(json, outer)
    result.isRight shouldBe true
    val prod  = result.toOption.get.asInstanceOf[CValue.CProduct]
    val inner2 = prod.value("nested").asInstanceOf[CValue.CProduct]
    inner2.value("a") shouldBe CValue.CInt(1)
    inner2.value("b") shouldBe CValue.CInt(2)
  }

  // ============================================================
  // jsonToCValue - CUnion Conversion from JSON
  // ============================================================

  "jsonToCValue union" should "convert union with nested product value" in {
    val structure = Map(
      "person" -> CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt)),
      "code"   -> CType.CInt
    )
    val json = Json.obj(
      "tag"   -> Json.fromString("person"),
      "value" -> Json.obj("name" -> Json.fromString("Eve"), "age" -> Json.fromInt(25))
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    result.isRight shouldBe true
    val union = result.toOption.get.asInstanceOf[CValue.CUnion]
    union.tag shouldBe "person"
    val prod = union.value.asInstanceOf[CValue.CProduct]
    prod.value("name") shouldBe CValue.CString("Eve")
    prod.value("age") shouldBe CValue.CInt(25)
  }

  it should "fail when union value type mismatches tagged variant" in {
    val structure = Map("num" -> CType.CInt)
    val json = Json.obj(
      "tag"   -> Json.fromString("num"),
      "value" -> Json.fromString("not a number")
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
  }

  it should "fail when expecting Union but got array" in {
    val structure = Map("str" -> CType.CString)
    val json      = Json.arr(Json.fromString("hello"))
    val result    = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Object with 'tag' and 'value'")) shouldBe true
  }

  it should "fail when expecting Union but got primitive" in {
    val structure = Map("str" -> CType.CString)
    val json      = Json.fromInt(42)
    val result    = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Object with 'tag' and 'value'")) shouldBe true
  }

  // ============================================================
  // jsonToCValue - CMap with Non-String Keys (Array of Pairs)
  // ============================================================

  "jsonToCValue maps" should "fail when non-string key map receives non-object non-array" in {
    val json   = Json.fromString("not valid")
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CInt, CType.CString))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Array or Object")) shouldBe true
  }

  it should "convert empty array to CMap with non-string keys" in {
    val json   = Json.arr()
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CInt, CType.CString))
    result shouldBe Right(CValue.CMap(Vector.empty, CType.CInt, CType.CString))
  }

  it should "fail when pair has wrong key type in CMap" in {
    val json = Json.arr(
      Json.arr(Json.fromString("not-an-int"), Json.fromString("value"))
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CInt, CType.CString))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
  }

  it should "fail when pair has wrong value type in CMap" in {
    val json = Json.arr(
      Json.arr(Json.fromInt(1), Json.fromInt(42))
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CInt, CType.CString))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected String")) shouldBe true
  }

  it should "fail when object given for CMap with non-string keys" in {
    // An object with non-CString keysType falls through to the array-of-pairs branch
    val json   = Json.obj("a" -> Json.fromString("value"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CInt, CType.CString))
    // Falls through to the `case _` branch after `json.asObject`, then tries asArray which fails
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Array or Object")) shouldBe true
  }

  it should "handle multiple CMap value errors" in {
    val json = Json.obj(
      "a" -> Json.fromString("bad1"),
      "b" -> Json.fromString("bad2")
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CString, CType.CInt))
    result.isLeft shouldBe true
    val err = result.left.toOption.get
    // Should contain errors for both keys
    err.contains("a") shouldBe true
    err.contains("b") shouldBe true
  }

  // ============================================================
  // jsonToCValue - CSome/CNone from JSON
  // ============================================================

  "jsonToCValue optional" should "convert optional list (present)" in {
    val optionalList = CType.COptional(CType.CList(CType.CInt))
    val json         = Json.arr(Json.fromInt(1), Json.fromInt(2))
    val result       = JsonCValueConverter.jsonToCValue(json, optionalList)
    result.isRight shouldBe true
    val some = result.toOption.get.asInstanceOf[CValue.CSome]
    some.innerType shouldBe CType.CList(CType.CInt)
    some.value shouldBe CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2)), CType.CInt)
  }

  it should "convert optional list (null)" in {
    val optionalList = CType.COptional(CType.CList(CType.CInt))
    val json         = Json.Null
    val result       = JsonCValueConverter.jsonToCValue(json, optionalList)
    result shouldBe Right(CValue.CNone(CType.CList(CType.CInt)))
  }

  it should "convert optional product (present)" in {
    val optionalProd = CType.COptional(CType.CProduct(Map("x" -> CType.CInt)))
    val json         = Json.obj("x" -> Json.fromInt(5))
    val result       = JsonCValueConverter.jsonToCValue(json, optionalProd)
    result.isRight shouldBe true
    result.toOption.get match {
      case CValue.CSome(CValue.CProduct(fields, _), _) =>
        fields("x") shouldBe CValue.CInt(5)
      case other => fail(s"Expected CSome(CProduct(...)), got $other")
    }
  }

  it should "convert optional product (null)" in {
    val optionalProd = CType.COptional(CType.CProduct(Map("x" -> CType.CInt)))
    val json         = Json.Null
    val result       = JsonCValueConverter.jsonToCValue(json, optionalProd)
    result shouldBe Right(CValue.CNone(CType.CProduct(Map("x" -> CType.CInt))))
  }

  // ============================================================
  // jsonToCValue - Error Cases: Wrong JSON Type
  // ============================================================

  "jsonToCValue error cases" should "fail when expecting Int but got null" in {
    val result = JsonCValueConverter.jsonToCValue(Json.Null, CType.CInt)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
    result.left.exists(_.contains("null")) shouldBe true
  }

  it should "fail when expecting Float but got null" in {
    val result = JsonCValueConverter.jsonToCValue(Json.Null, CType.CFloat)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Float")) shouldBe true
  }

  it should "fail when expecting Boolean but got null" in {
    val result = JsonCValueConverter.jsonToCValue(Json.Null, CType.CBoolean)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Boolean")) shouldBe true
  }

  it should "fail when expecting Array but got null" in {
    val result = JsonCValueConverter.jsonToCValue(Json.Null, CType.CList(CType.CInt))
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Array")) shouldBe true
  }

  it should "fail when expecting Boolean but got array" in {
    val result = JsonCValueConverter.jsonToCValue(Json.arr(), CType.CBoolean)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Boolean")) shouldBe true
  }

  it should "fail when expecting Int but got array" in {
    val result = JsonCValueConverter.jsonToCValue(Json.arr(), CType.CInt)
    result.isLeft shouldBe true
    result.left.exists(_.contains("expected Int")) shouldBe true
  }

  it should "report multiple errors in list with multiple bad elements" in {
    val json   = Json.arr(Json.fromString("a"), Json.fromString("b"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CList(CType.CInt))
    result.isLeft shouldBe true
    val err = result.left.toOption.get
    err.contains("[0]") shouldBe true
    err.contains("[1]") shouldBe true
  }

  // ============================================================
  // tryAutoDetectUnionVariant - JSON Object with Tag Field Matching
  // ============================================================

  "tryAutoDetectUnionVariant" should "auto-detect variant when JSON has extra fields beyond required" in {
    val structure = Map(
      "point" -> CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CInt))
    )
    // JSON has the required fields plus an extra one
    val json   = Json.obj("x" -> Json.fromInt(1), "y" -> Json.fromInt(2), "z" -> Json.fromInt(3))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    // Since z is extra but x and y are present, it should still match "point"
    result.isRight shouldBe true
    val union = result.toOption.get.asInstanceOf[CValue.CUnion]
    union.tag shouldBe "point"
  }

  it should "fail auto-detect when no variant fields match" in {
    val structure = Map(
      "point" -> CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CInt))
    )
    val json   = Json.obj("a" -> Json.fromInt(1), "b" -> Json.fromInt(2))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("could not match fields to any union variant")) shouldBe true
  }

  it should "fail auto-detect when variant is not a product type" in {
    // Union with primitive variant types - auto-detect only works with CProduct variants
    val structure = Map(
      "str" -> CType.CString,
      "num" -> CType.CInt
    )
    val json   = Json.obj("something" -> Json.fromString("value"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    result.isLeft shouldBe true
    result.left.exists(_.contains("could not match fields to any union variant")) shouldBe true
  }

  it should "auto-detect when fields match but value types are wrong returns no match" in {
    val structure = Map(
      "typed" -> CType.CProduct(Map("count" -> CType.CInt))
    )
    // count field is present but wrong type
    val json   = Json.obj("count" -> Json.fromString("not a number"))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    // Auto-detect finds the variant by field names, attempts conversion which fails,
    // so it ends up with no matches
    result.isLeft shouldBe true
    result.left.exists(_.contains("could not match fields to any union variant")) shouldBe true
  }

  it should "prefer most specific when all variants match and auto-detect handles 3+ variants" in {
    val structure = Map(
      "minimal"  -> CType.CProduct(Map("id" -> CType.CInt)),
      "standard" -> CType.CProduct(Map("id" -> CType.CInt, "name" -> CType.CString)),
      "full"     -> CType.CProduct(Map("id" -> CType.CInt, "name" -> CType.CString, "age" -> CType.CInt))
    )
    val json   = Json.obj("id" -> Json.fromInt(1), "name" -> Json.fromString("Alice"), "age" -> Json.fromInt(30))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))
    result.isRight shouldBe true
    val union = result.toOption.get.asInstanceOf[CValue.CUnion]
    union.tag shouldBe "full"
  }

  // ============================================================
  // rawValueToJson - RProduct to JSON
  // ============================================================

  "rawValueToJson product" should "convert single-field RProduct to JSON" in {
    val structure = Map("name" -> CType.CString)
    val value     = RawValue.RProduct(Array(RawValue.RString("Alice")))
    val result    = JsonCValueConverter.rawValueToJson(value, CType.CProduct(structure))
    result.hcursor.downField("name").as[String].toOption shouldBe Some("Alice")
  }

  it should "convert RProduct with nested product to JSON" in {
    val innerStruct = Map("x" -> CType.CInt)
    val outerStruct = Map("inner" -> CType.CProduct(innerStruct), "label" -> CType.CString)
    val value = RawValue.RProduct(Array(
      RawValue.RProduct(Array(RawValue.RInt(42))), // "inner" is first alphabetically
      RawValue.RString("test")                     // "label" is second
    ))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CProduct(outerStruct))
    result.hcursor.downField("label").as[String].toOption shouldBe Some("test")
    result.hcursor.downField("inner").downField("x").as[Int].toOption shouldBe Some(42)
  }

  it should "convert empty RProduct to empty JSON object" in {
    val structure = Map.empty[String, CType]
    val value     = RawValue.RProduct(Array.empty[RawValue])
    val result    = JsonCValueConverter.rawValueToJson(value, CType.CProduct(structure))
    result shouldBe Json.obj()
  }

  // ============================================================
  // rawValueToJson - RUnion to JSON
  // ============================================================

  "rawValueToJson union" should "convert RUnion with product value to JSON" in {
    val structure = Map(
      "success" -> CType.CProduct(Map("data" -> CType.CString)),
      "error"   -> CType.CInt
    )
    val value  = RawValue.RUnion("success", RawValue.RProduct(Array(RawValue.RString("ok"))))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CUnion(structure))
    result.hcursor.downField("tag").as[String].toOption shouldBe Some("success")
    result.hcursor.downField("value").downField("data").as[String].toOption shouldBe Some("ok")
  }

  it should "convert RUnion with primitive value to JSON" in {
    val structure = Map("num" -> CType.CInt)
    val value     = RawValue.RUnion("num", RawValue.RInt(99))
    val result    = JsonCValueConverter.rawValueToJson(value, CType.CUnion(structure))
    result.hcursor.downField("tag").as[String].toOption shouldBe Some("num")
    result.hcursor.downField("value").as[Int].toOption shouldBe Some(99)
  }

  it should "throw on unknown union tag" in {
    val structure = Map("known" -> CType.CInt)
    val value     = RawValue.RUnion("unknown", RawValue.RInt(1))
    assertThrows[RuntimeException] {
      JsonCValueConverter.rawValueToJson(value, CType.CUnion(structure))
    }
  }

  // ============================================================
  // rawValueToJson - RSome/RNone to JSON
  // ============================================================

  "rawValueToJson optional" should "convert RSome with nested product to JSON" in {
    val innerType = CType.CProduct(Map("x" -> CType.CInt))
    val value     = RawValue.RSome(RawValue.RProduct(Array(RawValue.RInt(7))))
    val result    = JsonCValueConverter.rawValueToJson(value, CType.COptional(innerType))
    result.hcursor.downField("x").as[Int].toOption shouldBe Some(7)
  }

  it should "convert RSome with primitive to inner JSON" in {
    val value  = RawValue.RSome(RawValue.RString("present"))
    val result = JsonCValueConverter.rawValueToJson(value, CType.COptional(CType.CString))
    result shouldBe Json.fromString("present")
  }

  it should "convert RNone to null regardless of inner type" in {
    val result1 = JsonCValueConverter.rawValueToJson(RawValue.RNone, CType.COptional(CType.CString))
    result1 shouldBe Json.Null

    val result2 = JsonCValueConverter.rawValueToJson(RawValue.RNone, CType.COptional(CType.CInt))
    result2 shouldBe Json.Null

    val innerType = CType.CProduct(Map("x" -> CType.CInt))
    val result3   = JsonCValueConverter.rawValueToJson(RawValue.RNone, CType.COptional(innerType))
    result3 shouldBe Json.Null
  }

  // ============================================================
  // rawValueToJson - RMap to JSON
  // ============================================================

  "rawValueToJson map" should "convert RMap with string keys and product values" in {
    val valueType = CType.CProduct(Map("v" -> CType.CInt))
    val value = RawValue.RMap(Array(
      (RawValue.RString("a"), RawValue.RProduct(Array(RawValue.RInt(1)))),
      (RawValue.RString("b"), RawValue.RProduct(Array(RawValue.RInt(2))))
    ))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CMap(CType.CString, valueType))
    result.hcursor.downField("a").downField("v").as[Int].toOption shouldBe Some(1)
    result.hcursor.downField("b").downField("v").as[Int].toOption shouldBe Some(2)
  }

  it should "convert RMap with non-string keys to array of pairs" in {
    val value = RawValue.RMap(Array(
      (RawValue.RInt(1), RawValue.RFloat(1.1)),
      (RawValue.RInt(2), RawValue.RFloat(2.2))
    ))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CMap(CType.CInt, CType.CFloat))
    val arr    = result.asArray.get
    arr.length shouldBe 2
    arr(0).asArray.get(0).asNumber.flatMap(_.toLong) shouldBe Some(1L)
    arr(0).asArray.get(1).asNumber.map(_.toDouble) shouldBe Some(1.1)
  }

  it should "convert empty RMap to empty JSON object (string keys)" in {
    val value  = RawValue.RMap(Array.empty[(RawValue, RawValue)])
    val result = JsonCValueConverter.rawValueToJson(value, CType.CMap(CType.CString, CType.CInt))
    result shouldBe Json.obj()
  }

  it should "convert empty RMap to empty JSON array (non-string keys)" in {
    val value  = RawValue.RMap(Array.empty[(RawValue, RawValue)])
    val result = JsonCValueConverter.rawValueToJson(value, CType.CMap(CType.CInt, CType.CString))
    result shouldBe Json.arr()
  }

  it should "throw when RMap with CString key type has non-RString key" in {
    val value = RawValue.RMap(Array(
      (RawValue.RInt(1), RawValue.RString("oops"))
    ))
    assertThrows[RuntimeException] {
      JsonCValueConverter.rawValueToJson(value, CType.CMap(CType.CString, CType.CString))
    }
  }

  // ============================================================
  // rawValueToJson - Specialized Lists
  // ============================================================

  "rawValueToJson specialized lists" should "convert empty RIntList" in {
    val value  = RawValue.RIntList(Array.empty[Long])
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CInt))
    result shouldBe Json.arr()
  }

  it should "convert empty RFloatList" in {
    val value  = RawValue.RFloatList(Array.empty[Double])
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CFloat))
    result shouldBe Json.arr()
  }

  it should "convert empty RStringList" in {
    val value  = RawValue.RStringList(Array.empty[String])
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CString))
    result shouldBe Json.arr()
  }

  it should "convert empty RBoolList" in {
    val value  = RawValue.RBoolList(Array.empty[Boolean])
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CBoolean))
    result shouldBe Json.arr()
  }

  it should "convert RFloatList with NaN to string fallback" in {
    val value  = RawValue.RFloatList(Array(Double.NaN, 1.0))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CFloat))
    val arr    = result.asArray.get
    arr(0) shouldBe Json.fromString("NaN")
    arr(1) shouldBe Json.fromDouble(1.0).get
  }

  it should "convert RFloatList with Infinity to string fallback" in {
    val value  = RawValue.RFloatList(Array(Double.PositiveInfinity, Double.NegativeInfinity))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CFloat))
    val arr    = result.asArray.get
    arr(0) shouldBe Json.fromString("Infinity")
    arr(1) shouldBe Json.fromString("-Infinity")
  }

  it should "convert RIntList with boundary values" in {
    val value  = RawValue.RIntList(Array(Long.MaxValue, Long.MinValue, 0L))
    val result = JsonCValueConverter.rawValueToJson(value, CType.CList(CType.CInt))
    val arr    = result.asArray.get
    arr(0) shouldBe Json.fromLong(Long.MaxValue)
    arr(1) shouldBe Json.fromLong(Long.MinValue)
    arr(2) shouldBe Json.fromLong(0)
  }

  // ============================================================
  // rawValueToJson - RFloat NaN/Infinity Fallback
  // ============================================================

  "rawValueToJson float fallback" should "convert RFloat NaN to string" in {
    val value  = RawValue.RFloat(Double.NaN)
    val result = JsonCValueConverter.rawValueToJson(value, CType.CFloat)
    result shouldBe Json.fromString("NaN")
  }

  it should "convert RFloat Infinity to string" in {
    val value  = RawValue.RFloat(Double.PositiveInfinity)
    val result = JsonCValueConverter.rawValueToJson(value, CType.CFloat)
    result shouldBe Json.fromString("Infinity")
  }

  it should "convert RFloat NegativeInfinity to string" in {
    val value  = RawValue.RFloat(Double.NegativeInfinity)
    val result = JsonCValueConverter.rawValueToJson(value, CType.CFloat)
    result shouldBe Json.fromString("-Infinity")
  }

  // ============================================================
  // rawValueToJson - Error Case (Mismatched Types)
  // ============================================================

  "rawValueToJson error cases" should "throw on mismatched raw value and type" in {
    // RList with non-list CType
    assertThrows[RuntimeException] {
      JsonCValueConverter.rawValueToJson(
        RawValue.RList(Array(RawValue.RInt(1))),
        CType.CString
      )
    }
  }

  it should "throw when RMap given non-map type" in {
    assertThrows[RuntimeException] {
      JsonCValueConverter.rawValueToJson(
        RawValue.RMap(Array((RawValue.RString("k"), RawValue.RInt(1)))),
        CType.CString
      )
    }
  }

  // ============================================================
  // Round-Trip Tests: jsonToRawValue -> rawValueToJson
  // ============================================================

  "round-trip rawValue" should "preserve nested product through conversion" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val cType     = CType.CProduct(structure)
    val json      = Json.obj("name" -> Json.fromString("Alice"), "age" -> Json.fromInt(30))

    val rawResult = JsonCValueConverter.jsonToRawValue(json, cType)
    rawResult.isRight shouldBe true
    val backToJson = JsonCValueConverter.rawValueToJson(rawResult.toOption.get, cType)
    backToJson.hcursor.downField("name").as[String].toOption shouldBe Some("Alice")
    backToJson.hcursor.downField("age").as[Int].toOption shouldBe Some(30)
  }

  it should "preserve tagged union through conversion" in {
    val structure = Map("str" -> CType.CString, "num" -> CType.CInt)
    val cType     = CType.CUnion(structure)
    val json      = Json.obj("tag" -> Json.fromString("num"), "value" -> Json.fromInt(42))

    val rawResult = JsonCValueConverter.jsonToRawValue(json, cType)
    rawResult.isRight shouldBe true
    val backToJson = JsonCValueConverter.rawValueToJson(rawResult.toOption.get, cType)
    backToJson.hcursor.downField("tag").as[String].toOption shouldBe Some("num")
    backToJson.hcursor.downField("value").as[Int].toOption shouldBe Some(42)
  }

  it should "preserve optional Some through conversion" in {
    val cType = CType.COptional(CType.CString)
    val json  = Json.fromString("hello")

    val rawResult = JsonCValueConverter.jsonToRawValue(json, cType)
    rawResult.isRight shouldBe true
    val backToJson = JsonCValueConverter.rawValueToJson(rawResult.toOption.get, cType)
    backToJson shouldBe Json.fromString("hello")
  }

  it should "preserve optional None through conversion" in {
    val cType = CType.COptional(CType.CInt)
    val json  = Json.Null

    val rawResult = JsonCValueConverter.jsonToRawValue(json, cType)
    rawResult.isRight shouldBe true
    val backToJson = JsonCValueConverter.rawValueToJson(rawResult.toOption.get, cType)
    backToJson shouldBe Json.Null
  }

  it should "preserve RIntList through conversion" in {
    val cType = CType.CList(CType.CInt)
    val json  = Json.arr(Json.fromInt(10), Json.fromInt(20), Json.fromInt(30))

    val rawResult = JsonCValueConverter.jsonToRawValue(json, cType)
    rawResult.isRight shouldBe true
    val backToJson = JsonCValueConverter.rawValueToJson(rawResult.toOption.get, cType)
    backToJson shouldBe Json.arr(Json.fromLong(10), Json.fromLong(20), Json.fromLong(30))
  }

  it should "preserve RMap with string keys through conversion" in {
    val cType = CType.CMap(CType.CString, CType.CInt)
    val json  = Json.obj("x" -> Json.fromInt(1), "y" -> Json.fromInt(2))

    val rawResult = JsonCValueConverter.jsonToRawValue(json, cType)
    rawResult.isRight shouldBe true
    val backToJson = JsonCValueConverter.rawValueToJson(rawResult.toOption.get, cType)
    backToJson.hcursor.downField("x").as[Int].toOption shouldBe Some(1)
    backToJson.hcursor.downField("y").as[Int].toOption shouldBe Some(2)
  }

  it should "preserve complex nested structure through conversion" in {
    val innerProduct = CType.CProduct(Map("id" -> CType.CInt, "name" -> CType.CString))
    val listType     = CType.CList(innerProduct)
    val outerType    = CType.CProduct(Map("items" -> listType, "count" -> CType.CInt))

    val json = Json.obj(
      "items" -> Json.arr(
        Json.obj("id" -> Json.fromInt(1), "name" -> Json.fromString("first")),
        Json.obj("id" -> Json.fromInt(2), "name" -> Json.fromString("second"))
      ),
      "count" -> Json.fromInt(2)
    )

    val rawResult = JsonCValueConverter.jsonToRawValue(json, outerType)
    rawResult.isRight shouldBe true
    val backToJson = JsonCValueConverter.rawValueToJson(rawResult.toOption.get, outerType)
    backToJson.hcursor.downField("count").as[Int].toOption shouldBe Some(2)
    val items = backToJson.hcursor.downField("items").as[Vector[Json]].toOption.get
    items.length shouldBe 2
    items(0).hcursor.downField("id").as[Int].toOption shouldBe Some(1)
    items(1).hcursor.downField("name").as[String].toOption shouldBe Some("second")
  }

  // ============================================================
  // Round-Trip Tests: jsonToCValue -> cValueToJson
  // ============================================================

  "round-trip cValue" should "preserve CMap with non-string keys" in {
    val cType = CType.CMap(CType.CInt, CType.CString)
    val json = Json.arr(
      Json.arr(Json.fromInt(1), Json.fromString("one")),
      Json.arr(Json.fromInt(2), Json.fromString("two"))
    )
    val cResult = JsonCValueConverter.jsonToCValue(json, cType)
    cResult.isRight shouldBe true
    val backToJson = JsonCValueConverter.cValueToJson(cResult.toOption.get)
    val arr        = backToJson.asArray.get
    arr.length shouldBe 2
  }

  it should "preserve CProduct with optional fields" in {
    val cType = CType.CProduct(Map(
      "name"  -> CType.CString,
      "email" -> CType.COptional(CType.CString)
    ))
    val json = Json.obj(
      "name"  -> Json.fromString("Test"),
      "email" -> Json.Null
    )
    val cResult = JsonCValueConverter.jsonToCValue(json, cType)
    cResult.isRight shouldBe true
    val backToJson = JsonCValueConverter.cValueToJson(cResult.toOption.get)
    backToJson.hcursor.downField("name").as[String].toOption shouldBe Some("Test")
    backToJson.hcursor.downField("email").focus shouldBe Some(Json.Null)
  }

  it should "preserve CUnion with product variant" in {
    val cType = CType.CUnion(Map(
      "success" -> CType.CProduct(Map("data" -> CType.CString)),
      "failure" -> CType.CProduct(Map("error" -> CType.CString))
    ))
    val json = Json.obj(
      "tag"   -> Json.fromString("failure"),
      "value" -> Json.obj("error" -> Json.fromString("not found"))
    )
    val cResult = JsonCValueConverter.jsonToCValue(json, cType)
    cResult.isRight shouldBe true
    val backToJson = JsonCValueConverter.cValueToJson(cResult.toOption.get)
    backToJson.hcursor.downField("tag").as[String].toOption shouldBe Some("failure")
    backToJson.hcursor.downField("value").downField("error").as[String].toOption shouldBe Some("not found")
  }

  // ============================================================
  // cValueToJson - Additional Edge Cases
  // ============================================================

  "cValueToJson edge cases" should "convert CList of CProducts to JSON" in {
    val structure = Map("x" -> CType.CInt)
    val list = CValue.CList(
      Vector(
        CValue.CProduct(Map("x" -> CValue.CInt(1)), structure),
        CValue.CProduct(Map("x" -> CValue.CInt(2)), structure)
      ),
      CType.CProduct(structure)
    )
    val result = JsonCValueConverter.cValueToJson(list)
    val arr    = result.asArray.get
    arr.length shouldBe 2
    arr(0).hcursor.downField("x").as[Int].toOption shouldBe Some(1)
    arr(1).hcursor.downField("x").as[Int].toOption shouldBe Some(2)
  }

  it should "convert CMap with string keys containing nested products" in {
    val structure = Map("v" -> CType.CInt)
    val cmap = CValue.CMap(
      Vector(
        (CValue.CString("a"), CValue.CProduct(Map("v" -> CValue.CInt(1)), structure)),
        (CValue.CString("b"), CValue.CProduct(Map("v" -> CValue.CInt(2)), structure))
      ),
      CType.CString,
      CType.CProduct(structure)
    )
    val result = JsonCValueConverter.cValueToJson(cmap)
    result.hcursor.downField("a").downField("v").as[Int].toOption shouldBe Some(1)
    result.hcursor.downField("b").downField("v").as[Int].toOption shouldBe Some(2)
  }

  it should "convert CSome wrapping a product" in {
    val structure = Map("x" -> CType.CInt)
    val value     = CValue.CSome(CValue.CProduct(Map("x" -> CValue.CInt(5)), structure), CType.CProduct(structure))
    val result    = JsonCValueConverter.cValueToJson(value)
    result.hcursor.downField("x").as[Int].toOption shouldBe Some(5)
  }

  it should "convert CSome wrapping a list" in {
    val value  = CValue.CSome(CValue.CList(Vector(CValue.CInt(1)), CType.CInt), CType.CList(CType.CInt))
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.arr(Json.fromLong(1))
  }

  it should "convert CUnion with list value" in {
    val structure = Map("items" -> CType.CList(CType.CInt))
    val value = CValue.CUnion(
      CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2)), CType.CInt),
      structure,
      "items"
    )
    val result = JsonCValueConverter.cValueToJson(value)
    result.hcursor.downField("tag").as[String].toOption shouldBe Some("items")
    result.hcursor.downField("value").as[Vector[Json]].toOption.get.length shouldBe 2
  }

  it should "handle CFloat zero correctly" in {
    val value  = CValue.CFloat(0.0)
    val result = JsonCValueConverter.cValueToJson(value)
    result.asNumber.map(_.toDouble) shouldBe Some(0.0)
  }

  it should "handle CInt zero correctly" in {
    val value  = CValue.CInt(0)
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.fromLong(0)
  }

  it should "handle CInt Long.MaxValue correctly" in {
    val value  = CValue.CInt(Long.MaxValue)
    val result = JsonCValueConverter.cValueToJson(value)
    result shouldBe Json.fromLong(Long.MaxValue)
  }
}
