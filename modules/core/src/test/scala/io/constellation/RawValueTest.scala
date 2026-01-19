package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RawValueTest extends AnyFlatSpec with Matchers {

  "RawValueConverter" should "convert primitive CValues to RawValues" in {
    val intCV = CValue.CInt(42)
    val stringCV = CValue.CString("hello")
    val floatCV = CValue.CFloat(3.14)
    val boolCV = CValue.CBoolean(true)

    RawValueConverter.fromCValue(intCV) shouldBe RawValue.RInt(42)
    RawValueConverter.fromCValue(stringCV) shouldBe RawValue.RString("hello")
    RawValueConverter.fromCValue(floatCV) shouldBe RawValue.RFloat(3.14)
    RawValueConverter.fromCValue(boolCV) shouldBe RawValue.RBool(true)
  }

  it should "convert int lists to specialized RIntList" in {
    val listCV = CValue.CList(
      Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
      CType.CInt
    )

    val raw = RawValueConverter.fromCValue(listCV)
    raw shouldBe a[RawValue.RIntList]
    raw.asInstanceOf[RawValue.RIntList].values shouldBe Array(1L, 2L, 3L)
  }

  it should "convert float lists to specialized RFloatList" in {
    val listCV = CValue.CList(
      Vector(CValue.CFloat(1.1), CValue.CFloat(2.2), CValue.CFloat(3.3)),
      CType.CFloat
    )

    val raw = RawValueConverter.fromCValue(listCV)
    raw shouldBe a[RawValue.RFloatList]
    raw.asInstanceOf[RawValue.RFloatList].values shouldBe Array(1.1, 2.2, 3.3)
  }

  it should "convert string lists to specialized RStringList" in {
    val listCV = CValue.CList(
      Vector(CValue.CString("a"), CValue.CString("b")),
      CType.CString
    )

    val raw = RawValueConverter.fromCValue(listCV)
    raw shouldBe a[RawValue.RStringList]
    raw.asInstanceOf[RawValue.RStringList].values shouldBe Array("a", "b")
  }

  it should "convert products to RProduct with sorted field order" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val productCV = CValue.CProduct(
      Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30)),
      structure
    )

    val raw = RawValueConverter.fromCValue(productCV)
    raw shouldBe a[RawValue.RProduct]
    // Fields are sorted alphabetically: age, name
    val product = raw.asInstanceOf[RawValue.RProduct]
    product.values.length shouldBe 2
    product.values(0) shouldBe RawValue.RInt(30)    // age
    product.values(1) shouldBe RawValue.RString("Alice")  // name
  }

  it should "convert optional values" in {
    val someCV = CValue.CSome(CValue.CInt(42), CType.CInt)
    val noneCV = CValue.CNone(CType.CInt)

    RawValueConverter.fromCValue(someCV) shouldBe RawValue.RSome(RawValue.RInt(42))
    RawValueConverter.fromCValue(noneCV) shouldBe RawValue.RNone
  }

  "TypedValueAccessor" should "convert RawValue back to CValue" in {
    val accessor = TypedValueAccessor(CType.CInt)
    val raw = RawValue.RInt(42)
    accessor.toCValue(raw) shouldBe CValue.CInt(42)
  }

  it should "convert RIntList back to CList" in {
    val accessor = TypedValueAccessor(CType.CList(CType.CInt))
    val raw = RawValue.RIntList(Array(1, 2, 3))
    val cValue = accessor.toCValue(raw)

    cValue shouldBe a[CValue.CList]
    val list = cValue.asInstanceOf[CValue.CList]
    list.value shouldBe Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3))
    list.subtype shouldBe CType.CInt
  }

  it should "convert RProduct back to CProduct" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val accessor = TypedValueAccessor(CType.CProduct(structure))
    val raw = RawValue.RProduct(Array(RawValue.RInt(30), RawValue.RString("Alice")))

    val cValue = accessor.toCValue(raw)
    cValue shouldBe a[CValue.CProduct]
    val product = cValue.asInstanceOf[CValue.CProduct]
    product.value shouldBe Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30))
  }

  it should "access fields from RProduct" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val accessor = TypedValueAccessor(CType.CProduct(structure))
    val raw = RawValue.RProduct(Array(RawValue.RInt(30), RawValue.RString("Alice")))

    accessor.getField(raw, "age") shouldBe RawValue.RInt(30)
    accessor.getField(raw, "name") shouldBe RawValue.RString("Alice")
  }

  it should "get extractors for primitives" in {
    val intAccessor = TypedValueAccessor(CType.CInt)
    val floatAccessor = TypedValueAccessor(CType.CFloat)
    val stringAccessor = TypedValueAccessor(CType.CString)
    val boolAccessor = TypedValueAccessor(CType.CBoolean)

    intAccessor.getInt(RawValue.RInt(42)) shouldBe 42L
    floatAccessor.getFloat(RawValue.RFloat(3.14)) shouldBe 3.14
    stringAccessor.getString(RawValue.RString("hello")) shouldBe "hello"
    boolAccessor.getBool(RawValue.RBool(true)) shouldBe true
  }

  "RawValue roundtrip" should "preserve values through conversion" in {
    val structure = Map("scores" -> CType.CList(CType.CFloat), "name" -> CType.CString)
    val original = CValue.CProduct(
      Map(
        "name" -> CValue.CString("Test"),
        "scores" -> CValue.CList(Vector(CValue.CFloat(1.0), CValue.CFloat(2.0)), CType.CFloat)
      ),
      structure
    )

    val raw = RawValueConverter.fromCValue(original)
    val restored = RawValueConverter.toCValue(raw, CType.CProduct(structure))

    restored shouldBe original
  }

  it should "preserve large int arrays through conversion" in {
    val size = 10000
    val values = (0 until size).map(i => CValue.CInt(i.toLong)).toVector
    val original = CValue.CList(values, CType.CInt)

    val raw = RawValueConverter.fromCValue(original)
    raw shouldBe a[RawValue.RIntList]
    raw.asInstanceOf[RawValue.RIntList].length shouldBe size

    val restored = RawValueConverter.toCValue(raw, CType.CList(CType.CInt))
    restored shouldBe original
  }

  it should "preserve large float arrays through conversion" in {
    val size = 10000
    val values = (0 until size).map(i => CValue.CFloat(i.toDouble)).toVector
    val original = CValue.CList(values, CType.CFloat)

    val raw = RawValueConverter.fromCValue(original)
    raw shouldBe a[RawValue.RFloatList]
    raw.asInstanceOf[RawValue.RFloatList].length shouldBe size

    val restored = RawValueConverter.toCValue(raw, CType.CList(CType.CFloat))
    restored shouldBe original
  }
}
