package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RuntimeAnyConversionTest extends AnyFlatSpec with Matchers {

  // ===== CType.CString =====

  "anyToCValue" should "convert String to CValue.CString" in {
    Runtime.anyToCValue("hello", CType.CString) shouldBe CValue.CString("hello")
  }

  // ===== CType.CInt =====

  it should "convert Long to CValue.CInt" in {
    Runtime.anyToCValue(42L, CType.CInt) shouldBe CValue.CInt(42L)
  }

  it should "convert Int to CValue.CInt" in {
    Runtime.anyToCValue(42: Int, CType.CInt) shouldBe CValue.CInt(42L)
  }

  it should "convert java.lang.Long to CValue.CInt" in {
    val jLong: java.lang.Long = java.lang.Long.valueOf(99L)
    Runtime.anyToCValue(jLong, CType.CInt) shouldBe CValue.CInt(99L)
  }

  it should "convert java.lang.Integer to CValue.CInt" in {
    val jInt: java.lang.Integer = java.lang.Integer.valueOf(7)
    Runtime.anyToCValue(jInt, CType.CInt) shouldBe CValue.CInt(7L)
  }

  it should "convert string to CValue.CInt via fallback" in {
    Runtime.anyToCValue("123", CType.CInt) shouldBe CValue.CInt(123L)
  }

  // ===== CType.CFloat =====

  it should "convert Double to CValue.CFloat" in {
    Runtime.anyToCValue(3.14, CType.CFloat) shouldBe CValue.CFloat(3.14)
  }

  it should "convert Float to CValue.CFloat" in {
    val f: Float = 2.5f
    Runtime.anyToCValue(f, CType.CFloat) shouldBe CValue.CFloat(2.5)
  }

  it should "convert java.lang.Double to CValue.CFloat" in {
    val jDouble: java.lang.Double = java.lang.Double.valueOf(1.5)
    Runtime.anyToCValue(jDouble, CType.CFloat) shouldBe CValue.CFloat(1.5)
  }

  it should "convert java.lang.Float to CValue.CFloat" in {
    val jFloat: java.lang.Float = java.lang.Float.valueOf(0.5f)
    Runtime.anyToCValue(jFloat, CType.CFloat) shouldBe CValue.CFloat(0.5)
  }

  it should "convert string to CValue.CFloat via fallback" in {
    Runtime.anyToCValue("9.99", CType.CFloat) shouldBe CValue.CFloat(9.99)
  }

  // ===== CType.CBoolean =====

  it should "convert Boolean to CValue.CBoolean" in {
    Runtime.anyToCValue(true, CType.CBoolean) shouldBe CValue.CBoolean(true)
  }

  it should "convert java.lang.Boolean to CValue.CBoolean" in {
    val jBool: java.lang.Boolean = java.lang.Boolean.FALSE
    Runtime.anyToCValue(jBool, CType.CBoolean) shouldBe CValue.CBoolean(false)
  }

  it should "convert string to CValue.CBoolean via fallback" in {
    Runtime.anyToCValue("true", CType.CBoolean) shouldBe CValue.CBoolean(true)
  }

  // ===== CType.CList =====

  it should "convert List to CValue.CList" in {
    val result = Runtime.anyToCValue(List(1L, 2L, 3L), CType.CList(CType.CInt))
    result shouldBe CValue.CList(
      Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
      CType.CInt
    )
  }

  it should "convert Vector to CValue.CList" in {
    val result = Runtime.anyToCValue(Vector("a", "b"), CType.CList(CType.CString))
    result shouldBe CValue.CList(
      Vector(CValue.CString("a"), CValue.CString("b")),
      CType.CString
    )
  }

  it should "wrap single value in CList via fallback" in {
    val result = Runtime.anyToCValue(42L, CType.CList(CType.CInt))
    result shouldBe CValue.CList(Vector(CValue.CInt(42L)), CType.CInt)
  }

  // ===== CType.CProduct =====

  it should "convert Map to CValue.CProduct" in {
    val fieldTypes = Map("name" -> CType.CString, "age" -> CType.CInt)
    val value      = Map("name" -> ("Alice": Any), "age" -> (30L: Any))
    val result     = Runtime.anyToCValue(value, CType.CProduct(fieldTypes))
    result shouldBe a[CValue.CProduct]
    val product = result.asInstanceOf[CValue.CProduct]
    product.value("name") shouldBe CValue.CString("Alice")
    product.value("age") shouldBe CValue.CInt(30L)
  }

  // ===== CType.CMap =====

  it should "convert Map to CValue.CMap" in {
    val value  = Map("x" -> 1L, "y" -> 2L)
    val result = Runtime.anyToCValue(value, CType.CMap(CType.CString, CType.CInt))
    result shouldBe a[CValue.CMap]
    val cMap = result.asInstanceOf[CValue.CMap]
    cMap.value.toSet shouldBe Set(
      (CValue.CString("x"), CValue.CInt(1L)),
      (CValue.CString("y"), CValue.CInt(2L))
    )
  }

  // ===== CType.COptional =====

  it should "convert Some to CValue.CSome" in {
    val result = Runtime.anyToCValue(Some("hello"), CType.COptional(CType.CString))
    result shouldBe CValue.CSome(CValue.CString("hello"), CType.CString)
  }

  it should "convert None to CValue.CNone" in {
    val result = Runtime.anyToCValue(None, CType.COptional(CType.CInt))
    result shouldBe CValue.CNone(CType.CInt)
  }

  it should "wrap non-Option value in CSome via fallback" in {
    val result = Runtime.anyToCValue("direct", CType.COptional(CType.CString))
    result shouldBe CValue.CSome(CValue.CString("direct"), CType.CString)
  }

  // ===== CType.CUnion =====

  it should "convert tagged tuple to CValue.CUnion" in {
    val variants = Map("str" -> CType.CString, "num" -> CType.CInt)
    val value    = ("str", "hello")
    val result   = Runtime.anyToCValue(value, CType.CUnion(variants))
    result shouldBe a[CValue.CUnion]
    val union = result.asInstanceOf[CValue.CUnion]
    union.tag shouldBe "str"
    union.value shouldBe CValue.CString("hello")
  }

  // ===== Empty / edge cases =====

  it should "handle empty List" in {
    val result = Runtime.anyToCValue(List.empty[Long], CType.CList(CType.CInt))
    result shouldBe CValue.CList(Vector.empty, CType.CInt)
  }

  it should "handle empty Map for CMap" in {
    val result = Runtime.anyToCValue(Map.empty[String, Long], CType.CMap(CType.CString, CType.CInt))
    result shouldBe a[CValue.CMap]
    result.asInstanceOf[CValue.CMap].value shouldBe empty
  }
}
