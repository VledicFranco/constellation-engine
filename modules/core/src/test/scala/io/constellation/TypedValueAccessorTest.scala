package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TypedValueAccessorTest extends AnyFlatSpec with Matchers {

  // ========== getField Tests ==========

  "TypedValueAccessor.getField" should "extract field from RProduct by name" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val accessor = TypedValueAccessor(CType.CProduct(structure))
    // Fields sorted: age (0), name (1)
    val raw = RawValue.RProduct(Array(RawValue.RInt(30), RawValue.RString("Alice")))

    accessor.getField(raw, "name") shouldBe RawValue.RString("Alice")
    accessor.getField(raw, "age") shouldBe RawValue.RInt(30)
  }

  it should "throw TypeMismatchException for non-existent field" in {
    val structure = Map("name" -> CType.CString)
    val accessor = TypedValueAccessor(CType.CProduct(structure))
    val raw = RawValue.RProduct(Array(RawValue.RString("Alice")))

    val exception = intercept[TypeMismatchException] {
      accessor.getField(raw, "nonexistent")
    }
    exception.getMessage should include("Field 'nonexistent' not found")
  }

  it should "throw TypeMismatchException when called on non-product type" in {
    val accessor = TypedValueAccessor(CType.CInt)
    val raw = RawValue.RInt(42)

    val exception = intercept[TypeMismatchException] {
      accessor.getField(raw, "field")
    }
    exception.getMessage should include("Cannot get field from")
  }

  it should "throw TypeMismatchException when raw value is not RProduct" in {
    val structure = Map("name" -> CType.CString)
    val accessor = TypedValueAccessor(CType.CProduct(structure))
    val raw = RawValue.RString("not a product")

    val exception = intercept[TypeMismatchException] {
      accessor.getField(raw, "name")
    }
    exception.getMessage should include("Expected RProduct")
  }

  it should "throw TypeMismatchException when field index out of bounds" in {
    val structure = Map("a" -> CType.CInt, "b" -> CType.CInt, "c" -> CType.CInt)
    val accessor = TypedValueAccessor(CType.CProduct(structure))
    // Only provide 2 values, but structure expects 3
    val raw = RawValue.RProduct(Array(RawValue.RInt(1), RawValue.RInt(2)))

    val exception = intercept[TypeMismatchException] {
      accessor.getField(raw, "c") // "c" is at index 2
    }
    exception.getMessage should include("out of bounds")
  }

  // ========== getFieldType Tests ==========

  "TypedValueAccessor.getFieldType" should "return type for existing field" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val accessor = TypedValueAccessor(CType.CProduct(structure))

    accessor.getFieldType("name") shouldBe CType.CString
    accessor.getFieldType("age") shouldBe CType.CInt
  }

  it should "throw TypeMismatchException for non-existent field" in {
    val structure = Map("name" -> CType.CString)
    val accessor = TypedValueAccessor(CType.CProduct(structure))

    val exception = intercept[TypeMismatchException] {
      accessor.getFieldType("nonexistent")
    }
    exception.getMessage should include("Field 'nonexistent' not found")
  }

  it should "throw TypeMismatchException for non-product type" in {
    val accessor = TypedValueAccessor(CType.CInt)

    val exception = intercept[TypeMismatchException] {
      accessor.getFieldType("field")
    }
    exception.getMessage should include("Cannot get field type from")
  }

  // ========== fieldAccessor Tests ==========

  "TypedValueAccessor.fieldAccessor" should "return accessor for field type" in {
    val structure = Map("name" -> CType.CString, "score" -> CType.CFloat)
    val accessor = TypedValueAccessor(CType.CProduct(structure))

    val nameAccessor = accessor.fieldAccessor("name")
    nameAccessor.cType shouldBe CType.CString

    val scoreAccessor = accessor.fieldAccessor("score")
    scoreAccessor.cType shouldBe CType.CFloat
  }

  it should "throw for non-existent field" in {
    val structure = Map("name" -> CType.CString)
    val accessor = TypedValueAccessor(CType.CProduct(structure))

    val exception = intercept[TypeMismatchException] {
      accessor.fieldAccessor("missing")
    }
    exception.getMessage should include("Field 'missing' not found")
  }

  // ========== elementAccessor Tests ==========

  "TypedValueAccessor.elementAccessor" should "return accessor for list element type" in {
    val accessor = TypedValueAccessor(CType.CList(CType.CInt))
    val elemAccessor = accessor.elementAccessor

    elemAccessor.cType shouldBe CType.CInt
  }

  it should "handle nested list types" in {
    val accessor = TypedValueAccessor(CType.CList(CType.CList(CType.CString)))
    val elemAccessor = accessor.elementAccessor

    elemAccessor.cType shouldBe CType.CList(CType.CString)
  }

  it should "throw TypeMismatchException for non-list type" in {
    val accessor = TypedValueAccessor(CType.CInt)

    val exception = intercept[TypeMismatchException] {
      accessor.elementAccessor
    }
    exception.getMessage should include("Cannot get element type from")
  }

  // ========== innerAccessor Tests ==========

  "TypedValueAccessor.innerAccessor" should "return accessor for optional inner type" in {
    val accessor = TypedValueAccessor(CType.COptional(CType.CString))
    val innerAcc = accessor.innerAccessor

    innerAcc.cType shouldBe CType.CString
  }

  it should "handle nested optional types" in {
    val accessor = TypedValueAccessor(CType.COptional(CType.COptional(CType.CInt)))
    val innerAcc = accessor.innerAccessor

    innerAcc.cType shouldBe CType.COptional(CType.CInt)
  }

  it should "throw TypeMismatchException for non-optional type" in {
    val accessor = TypedValueAccessor(CType.CString)

    val exception = intercept[TypeMismatchException] {
      accessor.innerAccessor
    }
    exception.getMessage should include("Cannot get inner type from")
  }

  // ========== getInt Tests ==========

  "TypedValueAccessor.getInt" should "extract Long from RInt" in {
    val accessor = TypedValueAccessor(CType.CInt)
    accessor.getInt(RawValue.RInt(42)) shouldBe 42L
    accessor.getInt(RawValue.RInt(-100)) shouldBe -100L
    accessor.getInt(RawValue.RInt(Long.MaxValue)) shouldBe Long.MaxValue
    accessor.getInt(RawValue.RInt(0)) shouldBe 0L
  }

  it should "throw TypeMismatchException for non-int types" in {
    val accessor = TypedValueAccessor(CType.CInt)

    val exception = intercept[TypeMismatchException] {
      accessor.getInt(RawValue.RString("not an int"))
    }
    exception.getMessage should include("Expected RInt")
  }

  // ========== getFloat Tests ==========

  "TypedValueAccessor.getFloat" should "extract Double from RFloat" in {
    val accessor = TypedValueAccessor(CType.CFloat)
    accessor.getFloat(RawValue.RFloat(3.14)) shouldBe 3.14
    accessor.getFloat(RawValue.RFloat(-2.5)) shouldBe -2.5
    accessor.getFloat(RawValue.RFloat(0.0)) shouldBe 0.0
  }

  it should "throw TypeMismatchException for non-float types" in {
    val accessor = TypedValueAccessor(CType.CFloat)

    val exception = intercept[TypeMismatchException] {
      accessor.getFloat(RawValue.RInt(42))
    }
    exception.getMessage should include("Expected RFloat")
  }

  // ========== getString Tests ==========

  "TypedValueAccessor.getString" should "extract String from RString" in {
    val accessor = TypedValueAccessor(CType.CString)
    accessor.getString(RawValue.RString("hello")) shouldBe "hello"
    accessor.getString(RawValue.RString("")) shouldBe ""
    accessor.getString(RawValue.RString("with\nnewline")) shouldBe "with\nnewline"
  }

  it should "throw TypeMismatchException for non-string types" in {
    val accessor = TypedValueAccessor(CType.CString)

    val exception = intercept[TypeMismatchException] {
      accessor.getString(RawValue.RBool(true))
    }
    exception.getMessage should include("Expected RString")
  }

  // ========== getBool Tests ==========

  "TypedValueAccessor.getBool" should "extract Boolean from RBool" in {
    val accessor = TypedValueAccessor(CType.CBoolean)
    accessor.getBool(RawValue.RBool(true)) shouldBe true
    accessor.getBool(RawValue.RBool(false)) shouldBe false
  }

  it should "throw TypeMismatchException for non-bool types" in {
    val accessor = TypedValueAccessor(CType.CBoolean)

    val exception = intercept[TypeMismatchException] {
      accessor.getBool(RawValue.RFloat(1.0))
    }
    exception.getMessage should include("Expected RBool")
  }

  // ========== toCValue Tests - Primitives ==========

  "TypedValueAccessor.toCValue" should "convert primitive RawValues" in {
    TypedValueAccessor(CType.CString).toCValue(RawValue.RString("test")) shouldBe CValue.CString("test")
    TypedValueAccessor(CType.CInt).toCValue(RawValue.RInt(42)) shouldBe CValue.CInt(42)
    TypedValueAccessor(CType.CFloat).toCValue(RawValue.RFloat(3.14)) shouldBe CValue.CFloat(3.14)
    TypedValueAccessor(CType.CBoolean).toCValue(RawValue.RBool(true)) shouldBe CValue.CBoolean(true)
  }

  // ========== toCValue Tests - Optionals ==========

  it should "convert RSome to CSome" in {
    val accessor = TypedValueAccessor(CType.COptional(CType.CInt))
    val raw = RawValue.RSome(RawValue.RInt(42))
    val expected = CValue.CSome(CValue.CInt(42), CType.CInt)

    accessor.toCValue(raw) shouldBe expected
  }

  it should "convert RNone to CNone" in {
    val accessor = TypedValueAccessor(CType.COptional(CType.CString))
    accessor.toCValue(RawValue.RNone) shouldBe CValue.CNone(CType.CString)
  }

  it should "convert nested optionals" in {
    val accessor = TypedValueAccessor(CType.COptional(CType.COptional(CType.CInt)))
    val raw = RawValue.RSome(RawValue.RSome(RawValue.RInt(10)))
    val expected = CValue.CSome(
      CValue.CSome(CValue.CInt(10), CType.CInt),
      CType.COptional(CType.CInt)
    )

    accessor.toCValue(raw) shouldBe expected
  }

  // ========== toCValue Tests - Specialized Lists ==========

  it should "convert RIntList to CList" in {
    val accessor = TypedValueAccessor(CType.CList(CType.CInt))
    val raw = RawValue.RIntList(Array(1L, 2L, 3L))
    val expected = CValue.CList(
      Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
      CType.CInt
    )

    accessor.toCValue(raw) shouldBe expected
  }

  it should "convert RFloatList to CList" in {
    val accessor = TypedValueAccessor(CType.CList(CType.CFloat))
    val raw = RawValue.RFloatList(Array(1.1, 2.2))
    val expected = CValue.CList(
      Vector(CValue.CFloat(1.1), CValue.CFloat(2.2)),
      CType.CFloat
    )

    accessor.toCValue(raw) shouldBe expected
  }

  it should "convert RStringList to CList" in {
    val accessor = TypedValueAccessor(CType.CList(CType.CString))
    val raw = RawValue.RStringList(Array("a", "b", "c"))
    val expected = CValue.CList(
      Vector(CValue.CString("a"), CValue.CString("b"), CValue.CString("c")),
      CType.CString
    )

    accessor.toCValue(raw) shouldBe expected
  }

  it should "convert RBoolList to CList" in {
    val accessor = TypedValueAccessor(CType.CList(CType.CBoolean))
    val raw = RawValue.RBoolList(Array(true, false, true))
    val expected = CValue.CList(
      Vector(CValue.CBoolean(true), CValue.CBoolean(false), CValue.CBoolean(true)),
      CType.CBoolean
    )

    accessor.toCValue(raw) shouldBe expected
  }

  // ========== toCValue Tests - Generic Lists ==========

  it should "convert generic RList to CList" in {
    val productType = CType.CProduct(Map("x" -> CType.CInt))
    val accessor = TypedValueAccessor(CType.CList(productType))
    val raw = RawValue.RList(Array(
      RawValue.RProduct(Array(RawValue.RInt(1))),
      RawValue.RProduct(Array(RawValue.RInt(2)))
    ))

    val result = accessor.toCValue(raw)
    result shouldBe a[CValue.CList]
    val list = result.asInstanceOf[CValue.CList]
    list.value.length shouldBe 2
  }

  // ========== toCValue Tests - Maps ==========

  it should "convert RMap to CMap" in {
    val accessor = TypedValueAccessor(CType.CMap(CType.CString, CType.CInt))
    val raw = RawValue.RMap(Array(
      (RawValue.RString("x"), RawValue.RInt(1)),
      (RawValue.RString("y"), RawValue.RInt(2))
    ))

    val result = accessor.toCValue(raw)
    result shouldBe a[CValue.CMap]
    val map = result.asInstanceOf[CValue.CMap]
    map.value.toSet shouldBe Set(
      (CValue.CString("x"), CValue.CInt(1)),
      (CValue.CString("y"), CValue.CInt(2))
    )
  }

  it should "convert empty RMap to empty CMap" in {
    val accessor = TypedValueAccessor(CType.CMap(CType.CString, CType.CFloat))
    val raw = RawValue.RMap(Array.empty)

    val result = accessor.toCValue(raw)
    result shouldBe a[CValue.CMap]
    result.asInstanceOf[CValue.CMap].value shouldBe empty
  }

  // ========== toCValue Tests - Products ==========

  it should "convert RProduct to CProduct with sorted fields" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val accessor = TypedValueAccessor(CType.CProduct(structure))
    // Sorted: age (0), name (1)
    val raw = RawValue.RProduct(Array(RawValue.RInt(30), RawValue.RString("Alice")))

    val result = accessor.toCValue(raw)
    result shouldBe a[CValue.CProduct]
    val product = result.asInstanceOf[CValue.CProduct]
    product.value("name") shouldBe CValue.CString("Alice")
    product.value("age") shouldBe CValue.CInt(30)
  }

  it should "convert nested product" in {
    val innerStructure = Map("x" -> CType.CInt)
    val outerStructure = Map("inner" -> CType.CProduct(innerStructure))
    val accessor = TypedValueAccessor(CType.CProduct(outerStructure))
    val raw = RawValue.RProduct(Array(
      RawValue.RProduct(Array(RawValue.RInt(42)))
    ))

    val result = accessor.toCValue(raw)
    result shouldBe a[CValue.CProduct]
    val outer = result.asInstanceOf[CValue.CProduct]
    outer.value("inner") shouldBe a[CValue.CProduct]
  }

  // ========== toCValue Tests - Unions ==========

  it should "convert RUnion to CUnion" in {
    val structure = Map("Int" -> CType.CInt, "String" -> CType.CString)
    val accessor = TypedValueAccessor(CType.CUnion(structure))
    val raw = RawValue.RUnion("Int", RawValue.RInt(42))

    val result = accessor.toCValue(raw)
    result shouldBe a[CValue.CUnion]
    val union = result.asInstanceOf[CValue.CUnion]
    union.tag shouldBe "Int"
    union.value shouldBe CValue.CInt(42)
  }

  it should "throw for unknown union variant" in {
    val structure = Map("Int" -> CType.CInt)
    val accessor = TypedValueAccessor(CType.CUnion(structure))
    val raw = RawValue.RUnion("Unknown", RawValue.RString("test"))

    val exception = intercept[TypeMismatchException] {
      accessor.toCValue(raw)
    }
    exception.getMessage should include("Unknown union variant 'Unknown'")
  }

  // ========== toCValue Tests - Type Mismatches ==========

  it should "throw TypeMismatchException for incompatible raw/type combinations" in {
    val accessor = TypedValueAccessor(CType.CString)

    val exception = intercept[TypeMismatchException] {
      accessor.toCValue(RawValue.RInt(42))
    }
    exception.getMessage should include("Cannot convert")
  }

  // ========== Companion Object Tests ==========

  "TypedValueAccessor companion" should "create accessor via apply" in {
    val accessor = TypedValueAccessor(CType.CInt)
    accessor.cType shouldBe CType.CInt
  }

  // ========== Complex Integration Tests ==========

  "TypedValueAccessor" should "handle deeply nested structures" in {
    // List[Optional[Product{name: String, values: List[Int]}]]
    val productType = CType.CProduct(Map(
      "name" -> CType.CString,
      "values" -> CType.CList(CType.CInt)
    ))
    val listType = CType.CList(CType.COptional(productType))
    val accessor = TypedValueAccessor(listType)

    // Create a complex nested value
    val innerProduct = RawValue.RProduct(Array(
      RawValue.RString("test"),
      RawValue.RIntList(Array(1, 2, 3))
    ))
    val raw = RawValue.RList(Array(
      RawValue.RSome(innerProduct),
      RawValue.RNone
    ))

    val result = accessor.toCValue(raw)
    result shouldBe a[CValue.CList]
  }

  it should "support chained accessor navigation" in {
    val innerType = CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CFloat))
    val listType = CType.CList(innerType)
    val outerType = CType.CProduct(Map("items" -> listType))

    val accessor = TypedValueAccessor(outerType)
    val itemsAccessor = accessor.fieldAccessor("items")
    val elementAccessor = itemsAccessor.elementAccessor
    val xAccessor = elementAccessor.fieldAccessor("x")

    accessor.cType shouldBe outerType
    itemsAccessor.cType shouldBe listType
    elementAccessor.cType shouldBe innerType
    xAccessor.cType shouldBe CType.CInt
  }
}
