package io.constellation.provider

import io.constellation.{CType, CValue}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CValueSerializerSpec extends AnyFlatSpec with Matchers {

  val serializer: CValueSerializer = JsonCValueSerializer

  "JsonCValueSerializer" should "round-trip CString" in {
    roundTrip(CValue.CString("hello world"))
  }

  it should "round-trip CInt" in {
    roundTrip(CValue.CInt(42L))
  }

  it should "round-trip CFloat" in {
    roundTrip(CValue.CFloat(3.14))
  }

  it should "round-trip CBoolean" in {
    roundTrip(CValue.CBoolean(true))
  }

  it should "round-trip CList" in {
    roundTrip(CValue.CList(
      Vector(CValue.CString("a"), CValue.CString("b")),
      CType.CString
    ))
  }

  it should "round-trip CProduct" in {
    roundTrip(CValue.CProduct(
      Map("name" -> CValue.CString("test"), "count" -> CValue.CInt(5)),
      Map("name" -> CType.CString, "count" -> CType.CInt)
    ))
  }

  it should "round-trip CMap" in {
    roundTrip(CValue.CMap(
      Vector((CValue.CString("key1"), CValue.CInt(1)), (CValue.CString("key2"), CValue.CInt(2))),
      CType.CString,
      CType.CInt
    ))
  }

  it should "round-trip empty CList" in {
    roundTrip(CValue.CList(Vector.empty, CType.CString))
  }

  it should "round-trip empty CProduct" in {
    roundTrip(CValue.CProduct(Map.empty, Map.empty))
  }

  it should "return error for invalid bytes" in {
    val result = serializer.deserialize("not valid json".getBytes)
    result.isLeft shouldBe true
  }

  private def roundTrip(value: CValue): Unit = {
    val bytes  = serializer.serialize(value)
    bytes.isRight shouldBe true
    val result = serializer.deserialize(bytes.toOption.get)
    result shouldBe Right(value)
  }
}
