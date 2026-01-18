package io.constellation.http

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.Json
import io.circe.syntax._
import io.constellation.{CType, CValue, JsonCValueConverter}

class JsonCValueConverterTest extends AnyFlatSpec with Matchers {

  "JsonCValueConverter" should "convert JSON string to CValue.CString" in {
    val json = Json.fromString("hello")
    val result = JsonCValueConverter.jsonToCValue(json, CType.CString)

    result shouldBe Right(CValue.CString("hello"))
  }

  it should "convert JSON number to CValue.CInt" in {
    val json = Json.fromLong(42)
    val result = JsonCValueConverter.jsonToCValue(json, CType.CInt)

    result shouldBe Right(CValue.CInt(42))
  }

  it should "convert JSON number to CValue.CFloat" in {
    val json = Json.fromDouble(3.14).get
    val result = JsonCValueConverter.jsonToCValue(json, CType.CFloat)

    result shouldBe Right(CValue.CFloat(3.14))
  }

  it should "convert JSON boolean to CValue.CBoolean" in {
    val json = Json.fromBoolean(true)
    val result = JsonCValueConverter.jsonToCValue(json, CType.CBoolean)

    result shouldBe Right(CValue.CBoolean(true))
  }

  it should "convert JSON array to CValue.CList" in {
    val json = Json.fromValues(List(
      Json.fromString("a"),
      Json.fromString("b"),
      Json.fromString("c")
    ))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CList(CType.CString))

    result shouldBe Right(CValue.CList(
      Vector(
        CValue.CString("a"),
        CValue.CString("b"),
        CValue.CString("c")
      ),
      CType.CString
    ))
  }

  it should "convert nested JSON arrays to CValue.CList" in {
    val json = Json.fromValues(List(
      Json.fromValues(List(Json.fromLong(1), Json.fromLong(2))),
      Json.fromValues(List(Json.fromLong(3), Json.fromLong(4)))
    ))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CList(CType.CList(CType.CInt)))

    result shouldBe Right(CValue.CList(
      Vector(
        CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2)), CType.CInt),
        CValue.CList(Vector(CValue.CInt(3), CValue.CInt(4)), CType.CInt)
      ),
      CType.CList(CType.CInt)
    ))
  }

  it should "convert JSON object to CValue.CMap with string keys" in {
    val json = Json.obj(
      "key1" -> Json.fromString("value1"),
      "key2" -> Json.fromString("value2")
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CString, CType.CString))

    result.isRight shouldBe true
    val cValue = result.toOption.get.asInstanceOf[CValue.CMap]
    cValue.value should contain allOf (
      (CValue.CString("key1"), CValue.CString("value1")),
      (CValue.CString("key2"), CValue.CString("value2"))
    )
  }

  it should "convert JSON array of pairs to CValue.CMap with non-string keys" in {
    val json = Json.fromValues(List(
      Json.fromValues(List(Json.fromLong(1), Json.fromString("one"))),
      Json.fromValues(List(Json.fromLong(2), Json.fromString("two")))
    ))
    val result = JsonCValueConverter.jsonToCValue(json, CType.CMap(CType.CInt, CType.CString))

    result shouldBe Right(CValue.CMap(
      Vector(
        (CValue.CInt(1), CValue.CString("one")),
        (CValue.CInt(2), CValue.CString("two"))
      ),
      CType.CInt,
      CType.CString
    ))
  }

  it should "convert JSON object to CValue.CProduct" in {
    val json = Json.obj(
      "name" -> Json.fromString("Alice"),
      "age" -> Json.fromLong(30),
      "active" -> Json.fromBoolean(true)
    )
    val structure = Map(
      "name" -> CType.CString,
      "age" -> CType.CInt,
      "active" -> CType.CBoolean
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))

    result shouldBe Right(CValue.CProduct(
      Map(
        "name" -> CValue.CString("Alice"),
        "age" -> CValue.CInt(30),
        "active" -> CValue.CBoolean(true)
      ),
      structure
    ))
  }

  it should "convert JSON object to CValue.CUnion" in {
    val json = Json.obj(
      "tag" -> Json.fromString("Success"),
      "value" -> Json.fromString("Operation completed")
    )
    val structure = Map(
      "Success" -> CType.CString,
      "Error" -> CType.CString
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))

    result shouldBe Right(CValue.CUnion(
      CValue.CString("Operation completed"),
      structure,
      "Success"
    ))
  }

  it should "return error for type mismatch on primitives" in {
    val json = Json.fromString("not a number")
    val result = JsonCValueConverter.jsonToCValue(json, CType.CInt)

    result.isLeft shouldBe true
    result.left.toOption.get should include("expected Int")
  }

  it should "return error for missing required field in CProduct" in {
    val json = Json.obj(
      "name" -> Json.fromString("Alice")
      // missing "age" field
    )
    val structure = Map(
      "name" -> CType.CString,
      "age" -> CType.CInt
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))

    result.isLeft shouldBe true
    result.left.toOption.get should include("missing required field 'age'")
  }

  it should "return error for invalid union tag" in {
    val json = Json.obj(
      "tag" -> Json.fromString("InvalidTag"),
      "value" -> Json.fromString("test")
    )
    val structure = Map(
      "Success" -> CType.CString,
      "Error" -> CType.CString
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CUnion(structure))

    result.isLeft shouldBe true
    result.left.toOption.get should include("invalid union tag 'InvalidTag'")
  }

  it should "return error with field path for nested type mismatch" in {
    val json = Json.obj(
      "user" -> Json.obj(
        "name" -> Json.fromLong(123) // should be string
      )
    )
    val structure = Map(
      "user" -> CType.CProduct(Map("name" -> CType.CString))
    )
    val result = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))

    result.isLeft shouldBe true
    result.left.toOption.get should include("user.name")
  }

  // CValue → JSON conversion tests

  it should "convert CValue.CString to JSON string" in {
    val cValue = CValue.CString("hello")
    val json = JsonCValueConverter.cValueToJson(cValue)

    json shouldBe Json.fromString("hello")
  }

  it should "convert CValue.CInt to JSON number" in {
    val cValue = CValue.CInt(42)
    val json = JsonCValueConverter.cValueToJson(cValue)

    json shouldBe Json.fromLong(42)
  }

  it should "convert CValue.CFloat to JSON number" in {
    val cValue = CValue.CFloat(3.14)
    val json = JsonCValueConverter.cValueToJson(cValue)

    json.asNumber.map(_.toDouble).get shouldBe 3.14
  }

  it should "convert CValue.CBoolean to JSON boolean" in {
    val cValue = CValue.CBoolean(true)
    val json = JsonCValueConverter.cValueToJson(cValue)

    json shouldBe Json.fromBoolean(true)
  }

  it should "convert CValue.CList to JSON array" in {
    val cValue = CValue.CList(
      Vector(CValue.CString("a"), CValue.CString("b")),
      CType.CString
    )
    val json = JsonCValueConverter.cValueToJson(cValue)

    json.asArray.get shouldBe Vector(
      Json.fromString("a"),
      Json.fromString("b")
    )
  }

  it should "convert CValue.CMap with string keys to JSON object" in {
    val cValue = CValue.CMap(
      Vector(
        (CValue.CString("key1"), CValue.CString("value1")),
        (CValue.CString("key2"), CValue.CString("value2"))
      ),
      CType.CString,
      CType.CString
    )
    val json = JsonCValueConverter.cValueToJson(cValue)

    json.asObject.get.toMap shouldBe Map(
      "key1" -> Json.fromString("value1"),
      "key2" -> Json.fromString("value2")
    )
  }

  it should "convert CValue.CMap with non-string keys to JSON array of pairs" in {
    val cValue = CValue.CMap(
      Vector(
        (CValue.CInt(1), CValue.CString("one")),
        (CValue.CInt(2), CValue.CString("two"))
      ),
      CType.CInt,
      CType.CString
    )
    val json = JsonCValueConverter.cValueToJson(cValue)

    json.asArray.get shouldBe Vector(
      Json.fromValues(Vector(Json.fromLong(1), Json.fromString("one"))),
      Json.fromValues(Vector(Json.fromLong(2), Json.fromString("two")))
    )
  }

  it should "convert CValue.CProduct to JSON object" in {
    val structure = Map(
      "name" -> CType.CString,
      "age" -> CType.CInt
    )
    val cValue = CValue.CProduct(
      Map(
        "name" -> CValue.CString("Alice"),
        "age" -> CValue.CInt(30)
      ),
      structure
    )
    val json = JsonCValueConverter.cValueToJson(cValue)

    json.asObject.get.toMap shouldBe Map(
      "name" -> Json.fromString("Alice"),
      "age" -> Json.fromLong(30)
    )
  }

  it should "convert CValue.CUnion to JSON object with tag and value" in {
    val structure = Map(
      "Success" -> CType.CString,
      "Error" -> CType.CString
    )
    val cValue = CValue.CUnion(
      CValue.CString("Operation completed"),
      structure,
      "Success"
    )
    val json = JsonCValueConverter.cValueToJson(cValue)

    json.asObject.get.toMap shouldBe Map(
      "tag" -> Json.fromString("Success"),
      "value" -> Json.fromString("Operation completed")
    )
  }

  it should "round-trip CValue through JSON and back" in {
    val structure = Map(
      "name" -> CType.CString,
      "age" -> CType.CInt,
      "tags" -> CType.CList(CType.CString)
    )
    val original = CValue.CProduct(
      Map(
        "name" -> CValue.CString("Alice"),
        "age" -> CValue.CInt(30),
        "tags" -> CValue.CList(Vector(CValue.CString("developer"), CValue.CString("scala")), CType.CString)
      ),
      structure
    )

    val json = JsonCValueConverter.cValueToJson(original)
    val roundTripped = JsonCValueConverter.jsonToCValue(json, CType.CProduct(structure))

    roundTripped shouldBe Right(original)
  }

  // Optional type tests

  it should "convert JSON null to CValue.CNone" in {
    val json = Json.Null
    val result = JsonCValueConverter.jsonToCValue(json, CType.COptional(CType.CString))

    result shouldBe Right(CValue.CNone(CType.CString))
  }

  it should "convert JSON value to CValue.CSome" in {
    val json = Json.fromString("hello")
    val result = JsonCValueConverter.jsonToCValue(json, CType.COptional(CType.CString))

    result shouldBe Right(CValue.CSome(CValue.CString("hello"), CType.CString))
  }

  it should "convert nested JSON value to CValue.CSome with complex inner type" in {
    val json = Json.fromValues(List(Json.fromLong(1), Json.fromLong(2), Json.fromLong(3)))
    val result = JsonCValueConverter.jsonToCValue(json, CType.COptional(CType.CList(CType.CInt)))

    result shouldBe Right(CValue.CSome(
      CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)), CType.CInt),
      CType.CList(CType.CInt)
    ))
  }

  it should "convert CValue.CSome to JSON value" in {
    val cValue = CValue.CSome(CValue.CString("hello"), CType.CString)
    val json = JsonCValueConverter.cValueToJson(cValue)

    json shouldBe Json.fromString("hello")
  }

  it should "convert CValue.CNone to JSON null" in {
    val cValue = CValue.CNone(CType.CInt)
    val json = JsonCValueConverter.cValueToJson(cValue)

    json shouldBe Json.Null
  }

  it should "round-trip Optional values through JSON" in {
    val someValue = CValue.CSome(CValue.CInt(42), CType.CInt)
    val noneValue = CValue.CNone(CType.CString)

    val someJson = JsonCValueConverter.cValueToJson(someValue)
    val noneJson = JsonCValueConverter.cValueToJson(noneValue)

    JsonCValueConverter.jsonToCValue(someJson, CType.COptional(CType.CInt)) shouldBe Right(someValue)
    JsonCValueConverter.jsonToCValue(noneJson, CType.COptional(CType.CString)) shouldBe Right(noneValue)
  }
}
