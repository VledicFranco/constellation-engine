package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RawValueConverterTest extends AnyFlatSpec with Matchers {

  // ============= Specialized List Conversions =============

  "RawValueConverter.fromCValue" should "convert CList of booleans to RBoolList" in {
    val listCV = CValue.CList(
      Vector(CValue.CBoolean(true), CValue.CBoolean(false), CValue.CBoolean(true)),
      CType.CBoolean
    )

    val raw = RawValueConverter.fromCValue(listCV)
    raw shouldBe a[RawValue.RBoolList]
    raw.asInstanceOf[RawValue.RBoolList].values shouldBe Array(true, false, true)
  }

  it should "convert empty CList of ints to empty RIntList" in {
    val listCV = CValue.CList(Vector.empty, CType.CInt)
    val raw    = RawValueConverter.fromCValue(listCV)
    raw shouldBe a[RawValue.RIntList]
    raw.asInstanceOf[RawValue.RIntList].values shouldBe Array.empty[Long]
  }

  it should "convert empty CList of floats to empty RFloatList" in {
    val listCV = CValue.CList(Vector.empty, CType.CFloat)
    val raw    = RawValueConverter.fromCValue(listCV)
    raw shouldBe a[RawValue.RFloatList]
    raw.asInstanceOf[RawValue.RFloatList].values shouldBe Array.empty[Double]
  }

  it should "convert empty CList of strings to empty RStringList" in {
    val listCV = CValue.CList(Vector.empty, CType.CString)
    val raw    = RawValueConverter.fromCValue(listCV)
    raw shouldBe a[RawValue.RStringList]
    raw.asInstanceOf[RawValue.RStringList].values shouldBe Array.empty[String]
  }

  it should "convert empty CList of booleans to empty RBoolList" in {
    val listCV = CValue.CList(Vector.empty, CType.CBoolean)
    val raw    = RawValueConverter.fromCValue(listCV)
    raw shouldBe a[RawValue.RBoolList]
    raw.asInstanceOf[RawValue.RBoolList].values shouldBe Array.empty[Boolean]
  }

  // ============= Generic List Conversion =============

  it should "convert CList of nested records to generic RList" in {
    val recordType = CType.CProduct(Map("x" -> CType.CInt))
    val listCV = CValue.CList(
      Vector(
        CValue.CProduct(Map("x" -> CValue.CInt(1)), Map("x" -> CType.CInt)),
        CValue.CProduct(Map("x" -> CValue.CInt(2)), Map("x" -> CType.CInt))
      ),
      recordType
    )

    val raw = RawValueConverter.fromCValue(listCV)
    raw shouldBe a[RawValue.RList]
    raw.asInstanceOf[RawValue.RList].length shouldBe 2
  }

  it should "convert CList of optionals to generic RList" in {
    val optType = CType.COptional(CType.CInt)
    val listCV = CValue.CList(
      Vector(
        CValue.CSome(CValue.CInt(42), CType.CInt),
        CValue.CNone(CType.CInt)
      ),
      optType
    )

    val raw = RawValueConverter.fromCValue(listCV)
    raw shouldBe a[RawValue.RList]
    val rList = raw.asInstanceOf[RawValue.RList]
    rList.length shouldBe 2
    rList(0) shouldBe RawValue.RSome(RawValue.RInt(42))
    rList(1) shouldBe RawValue.RNone
  }

  // ============= CMap Conversion =============

  it should "convert CMap to RMap" in {
    val mapCV = CValue.CMap(
      Vector(
        CValue.CString("key1") -> CValue.CInt(1),
        CValue.CString("key2") -> CValue.CInt(2)
      ),
      CType.CString,
      CType.CInt
    )

    val raw = RawValueConverter.fromCValue(mapCV)
    raw shouldBe a[RawValue.RMap]
    raw.asInstanceOf[RawValue.RMap].entries.length shouldBe 2
  }

  it should "convert empty CMap to empty RMap" in {
    val mapCV = CValue.CMap(Vector.empty, CType.CString, CType.CInt)
    val raw   = RawValueConverter.fromCValue(mapCV)
    raw shouldBe a[RawValue.RMap]
    raw.asInstanceOf[RawValue.RMap].entries.length shouldBe 0
  }

  // ============= CProduct Conversion =============

  it should "convert CProduct with sorted field order" in {
    val productCV = CValue.CProduct(
      Map("z" -> CValue.CInt(3), "a" -> CValue.CString("first"), "m" -> CValue.CBoolean(true)),
      Map("a" -> CType.CString, "m"  -> CType.CBoolean, "z"          -> CType.CInt)
    )

    val raw = RawValueConverter.fromCValue(productCV)
    raw shouldBe a[RawValue.RProduct]
    val product = raw.asInstanceOf[RawValue.RProduct]
    // Fields are sorted: a, m, z
    product(0) shouldBe RawValue.RString("first")
    product(1) shouldBe RawValue.RBool(true)
    product(2) shouldBe RawValue.RInt(3)
  }

  it should "throw TypeMismatchError for missing field in CProduct" in {
    val productCV = CValue.CProduct(
      Map("a" -> CValue.CInt(1)),
      Map("a" -> CType.CInt, "b" -> CType.CString)
    )

    val error = intercept[TypeMismatchError] {
      RawValueConverter.fromCValue(productCV)
    }
    error.expected should include("field 'b'")
  }

  // ============= Error Paths for Specialized Lists =============

  it should "throw TypeMismatchError for non-CInt in int list" in {
    val listCV = CValue.CList(
      Vector(CValue.CInt(1), CValue.CString("not-an-int")),
      CType.CInt
    )

    val error = intercept[TypeMismatchError] {
      RawValueConverter.fromCValue(listCV)
    }
    error.expected shouldBe "CInt"
  }

  it should "throw TypeMismatchError for non-CFloat in float list" in {
    val listCV = CValue.CList(
      Vector(CValue.CFloat(1.0), CValue.CInt(2)),
      CType.CFloat
    )

    val error = intercept[TypeMismatchError] {
      RawValueConverter.fromCValue(listCV)
    }
    error.expected shouldBe "CFloat"
  }

  it should "throw TypeMismatchError for non-CString in string list" in {
    val listCV = CValue.CList(
      Vector(CValue.CString("ok"), CValue.CInt(42)),
      CType.CString
    )

    val error = intercept[TypeMismatchError] {
      RawValueConverter.fromCValue(listCV)
    }
    error.expected shouldBe "CString"
  }

  it should "throw TypeMismatchError for non-CBoolean in boolean list" in {
    val listCV = CValue.CList(
      Vector(CValue.CBoolean(true), CValue.CInt(1)),
      CType.CBoolean
    )

    val error = intercept[TypeMismatchError] {
      RawValueConverter.fromCValue(listCV)
    }
    error.expected shouldBe "CBoolean"
  }

  // ============= toCValue delegation =============

  "RawValueConverter.toCValue" should "delegate to TypedValueAccessor" in {
    val raw    = RawValue.RString("hello")
    val cValue = RawValueConverter.toCValue(raw, CType.CString)
    cValue shouldBe CValue.CString("hello")
  }

  it should "convert RBoolList back to CList" in {
    val raw    = RawValue.RBoolList(Array(true, false))
    val cValue = RawValueConverter.toCValue(raw, CType.CList(CType.CBoolean))
    cValue shouldBe a[CValue.CList]
    val list = cValue.asInstanceOf[CValue.CList]
    list.value shouldBe Vector(CValue.CBoolean(true), CValue.CBoolean(false))
  }

  it should "convert RStringList back to CList" in {
    val raw    = RawValue.RStringList(Array("a", "b"))
    val cValue = RawValueConverter.toCValue(raw, CType.CList(CType.CString))
    cValue shouldBe a[CValue.CList]
    val list = cValue.asInstanceOf[CValue.CList]
    list.value shouldBe Vector(CValue.CString("a"), CValue.CString("b"))
  }

  it should "convert RFloatList back to CList" in {
    val raw    = RawValue.RFloatList(Array(1.5, 2.5))
    val cValue = RawValueConverter.toCValue(raw, CType.CList(CType.CFloat))
    cValue shouldBe a[CValue.CList]
    val list = cValue.asInstanceOf[CValue.CList]
    list.value shouldBe Vector(CValue.CFloat(1.5), CValue.CFloat(2.5))
  }

  it should "round-trip CMap through RawValue" in {
    val original = CValue.CMap(
      Vector(CValue.CString("a") -> CValue.CInt(1)),
      CType.CString,
      CType.CInt
    )
    val raw      = RawValueConverter.fromCValue(original)
    val restored = RawValueConverter.toCValue(raw, CType.CMap(CType.CString, CType.CInt))
    restored shouldBe a[CValue.CMap]
  }

  it should "round-trip Optional values" in {
    val some    = CValue.CSome(CValue.CString("hello"), CType.CString)
    val none    = CValue.CNone(CType.CString)
    val optType = CType.COptional(CType.CString)

    val rawSome  = RawValueConverter.fromCValue(some)
    val rawNone  = RawValueConverter.fromCValue(none)
    val backSome = RawValueConverter.toCValue(rawSome, optType)
    val backNone = RawValueConverter.toCValue(rawNone, optType)

    backSome shouldBe some
    backNone shouldBe none
  }

  // ============= CSeq Conversions =============

  "RawValueConverter.fromCValue" should "convert CSeq of ints to RIntList" in {
    val seqCV = CValue.CSeq(
      Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
      CType.CInt
    )
    val raw = RawValueConverter.fromCValue(seqCV)
    raw shouldBe a[RawValue.RIntList]
    raw.asInstanceOf[RawValue.RIntList].values shouldBe Array(1L, 2L, 3L)
  }

  it should "convert CSeq of floats to RFloatList" in {
    val seqCV = CValue.CSeq(
      Vector(CValue.CFloat(1.0), CValue.CFloat(2.5)),
      CType.CFloat
    )
    val raw = RawValueConverter.fromCValue(seqCV)
    raw shouldBe a[RawValue.RFloatList]
    raw.asInstanceOf[RawValue.RFloatList].values shouldBe Array(1.0, 2.5)
  }

  it should "convert CSeq of strings to RStringList" in {
    val seqCV = CValue.CSeq(
      Vector(CValue.CString("a"), CValue.CString("b")),
      CType.CString
    )
    val raw = RawValueConverter.fromCValue(seqCV)
    raw shouldBe a[RawValue.RStringList]
    raw.asInstanceOf[RawValue.RStringList].values shouldBe Array("a", "b")
  }

  it should "convert CSeq of booleans to RBoolList" in {
    val seqCV = CValue.CSeq(
      Vector(CValue.CBoolean(true), CValue.CBoolean(false)),
      CType.CBoolean
    )
    val raw = RawValueConverter.fromCValue(seqCV)
    raw shouldBe a[RawValue.RBoolList]
    raw.asInstanceOf[RawValue.RBoolList].values shouldBe Array(true, false)
  }

  it should "convert CSeq of nested types to RList" in {
    val seqCV = CValue.CSeq(
      Vector(
        CValue.CList(Vector(CValue.CInt(1L)), CType.CInt),
        CValue.CList(Vector(CValue.CInt(2L)), CType.CInt)
      ),
      CType.CList(CType.CInt)
    )
    val raw = RawValueConverter.fromCValue(seqCV)
    raw shouldBe a[RawValue.RList]
  }

  it should "convert empty CSeq to empty specialized array" in {
    val seqCV = CValue.CSeq(Vector.empty, CType.CInt)
    val raw   = RawValueConverter.fromCValue(seqCV)
    raw shouldBe a[RawValue.RIntList]
    raw.asInstanceOf[RawValue.RIntList].values shouldBe Array.empty[Long]
  }
}
