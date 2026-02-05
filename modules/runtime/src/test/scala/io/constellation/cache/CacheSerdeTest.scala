package io.constellation.cache

import io.constellation.{CType, CValue}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CacheSerdeTest extends AnyFlatSpec with Matchers {

  // -------------------------------------------------------------------------
  // CValue JSON Serde
  // -------------------------------------------------------------------------

  "CacheSerde.cvalueSerde" should "roundtrip CString" in {
    val original = CValue.CString("hello world")
    val bytes    = CacheSerde.cvalueSerde.serialize(original)
    val decoded  = CacheSerde.cvalueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "roundtrip CInt" in {
    val original = CValue.CInt(42L)
    val bytes    = CacheSerde.cvalueSerde.serialize(original)
    val decoded  = CacheSerde.cvalueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "roundtrip CFloat" in {
    val original = CValue.CFloat(3.14)
    val bytes    = CacheSerde.cvalueSerde.serialize(original)
    val decoded  = CacheSerde.cvalueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "roundtrip CBoolean" in {
    val original = CValue.CBoolean(true)
    val bytes    = CacheSerde.cvalueSerde.serialize(original)
    val decoded  = CacheSerde.cvalueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "roundtrip CList" in {
    val original = CValue.CList(
      Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
      CType.CInt
    )
    val bytes   = CacheSerde.cvalueSerde.serialize(original)
    val decoded = CacheSerde.cvalueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "roundtrip CMap" in {
    val original = CValue.CMap(
      Vector(
        (CValue.CString("a"), CValue.CInt(1)),
        (CValue.CString("b"), CValue.CInt(2))
      ),
      CType.CString,
      CType.CInt
    )
    val bytes   = CacheSerde.cvalueSerde.serialize(original)
    val decoded = CacheSerde.cvalueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "roundtrip CProduct" in {
    val original = CValue.CProduct(
      Map("name" -> CValue.CString("test"), "count" -> CValue.CInt(5)),
      Map("name" -> CType.CString, "count"          -> CType.CInt)
    )
    val bytes   = CacheSerde.cvalueSerde.serialize(original)
    val decoded = CacheSerde.cvalueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "roundtrip CUnion" in {
    val structure = Map("Int" -> CType.CInt, "String" -> CType.CString)
    val original  = CValue.CUnion(CValue.CInt(42), structure, "Int")
    val bytes     = CacheSerde.cvalueSerde.serialize(original)
    val decoded   = CacheSerde.cvalueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "roundtrip CSome" in {
    val original = CValue.CSome(CValue.CInt(42), CType.CInt)
    val bytes    = CacheSerde.cvalueSerde.serialize(original)
    val decoded  = CacheSerde.cvalueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "roundtrip CNone" in {
    val original = CValue.CNone(CType.CString)
    val bytes    = CacheSerde.cvalueSerde.serialize(original)
    val decoded  = CacheSerde.cvalueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "throw CacheSerdeException on invalid JSON" in {
    val invalidBytes = "not valid json".getBytes("UTF-8")
    an[CacheSerdeException] should be thrownBy {
      CacheSerde.cvalueSerde.deserialize(invalidBytes)
    }
  }

  // -------------------------------------------------------------------------
  // Map[String, CValue] JSON Serde
  // -------------------------------------------------------------------------

  "CacheSerde.mapCValueSerde" should "roundtrip a map of CValues" in {
    val original = Map(
      "text"  -> CValue.CString("hello"),
      "count" -> CValue.CInt(42),
      "flag"  -> CValue.CBoolean(true)
    )
    val bytes   = CacheSerde.mapCValueSerde.serialize(original)
    val decoded = CacheSerde.mapCValueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "roundtrip an empty map" in {
    val original = Map.empty[String, CValue]
    val bytes    = CacheSerde.mapCValueSerde.serialize(original)
    val decoded  = CacheSerde.mapCValueSerde.deserialize(bytes)
    decoded shouldBe original
  }

  // -------------------------------------------------------------------------
  // Java Serde
  // -------------------------------------------------------------------------

  "CacheSerde.javaSerde" should "roundtrip a String" in {
    val serde    = CacheSerde.javaSerde[String]
    val original = "test string"
    val bytes    = serde.serialize(original)
    val decoded  = serde.deserialize(bytes)
    decoded shouldBe original
  }

  it should "roundtrip an Integer" in {
    val serde    = CacheSerde.javaSerde[java.lang.Integer]
    val original = java.lang.Integer.valueOf(42)
    val bytes    = serde.serialize(original)
    val decoded  = serde.deserialize(bytes)
    decoded shouldBe original
  }

  // -------------------------------------------------------------------------
  // Any Serde
  // -------------------------------------------------------------------------

  "CacheSerde.anySerde" should "serialize CValue as JSON" in {
    val cvalue = CValue.CString("hello")
    val bytes  = CacheSerde.anySerde.serialize(cvalue)
    // Should be JSON (starts with '{')
    bytes(0) shouldBe '{'.toByte
    val decoded = CacheSerde.anySerde.deserialize(bytes)
    decoded shouldBe cvalue
  }

  it should "serialize Serializable values with Java serialization" in {
    val value: Any = "a plain string"
    val bytes      = CacheSerde.anySerde.serialize(value)
    // Should be prefixed with 0x01
    bytes(0) shouldBe 0x01.toByte
    val decoded = CacheSerde.anySerde.deserialize(bytes)
    decoded shouldBe value
  }

  it should "throw on non-serializable values" in {
    val nonSerializable = new Object() {}
    an[CacheSerdeException] should be thrownBy {
      CacheSerde.anySerde.serialize(nonSerializable)
    }
  }

  it should "throw on empty byte array" in {
    an[CacheSerdeException] should be thrownBy {
      CacheSerde.anySerde.deserialize(Array.emptyByteArray)
    }
  }
}
