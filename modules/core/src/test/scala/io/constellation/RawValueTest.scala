package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RawValueTest extends AnyFlatSpec with Matchers {

  "RawValueConverter" should "convert primitive CValues to RawValues" in {
    val intCV    = CValue.CInt(42)
    val stringCV = CValue.CString("hello")
    val floatCV  = CValue.CFloat(3.14)
    val boolCV   = CValue.CBoolean(true)

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
    product.values(0) shouldBe RawValue.RInt(30)         // age
    product.values(1) shouldBe RawValue.RString("Alice") // name
  }

  it should "convert optional values" in {
    val someCV = CValue.CSome(CValue.CInt(42), CType.CInt)
    val noneCV = CValue.CNone(CType.CInt)

    RawValueConverter.fromCValue(someCV) shouldBe RawValue.RSome(RawValue.RInt(42))
    RawValueConverter.fromCValue(noneCV) shouldBe RawValue.RNone
  }

  "TypedValueAccessor" should "convert RawValue back to CValue" in {
    val accessor = TypedValueAccessor(CType.CInt)
    val raw      = RawValue.RInt(42)
    accessor.toCValue(raw) shouldBe CValue.CInt(42)
  }

  it should "convert RIntList back to CList" in {
    val accessor = TypedValueAccessor(CType.CList(CType.CInt))
    val raw      = RawValue.RIntList(Array(1, 2, 3))
    val cValue   = accessor.toCValue(raw)

    cValue shouldBe a[CValue.CList]
    val list = cValue.asInstanceOf[CValue.CList]
    list.value shouldBe Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3))
    list.subtype shouldBe CType.CInt
  }

  it should "convert RProduct back to CProduct" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val accessor  = TypedValueAccessor(CType.CProduct(structure))
    val raw       = RawValue.RProduct(Array(RawValue.RInt(30), RawValue.RString("Alice")))

    val cValue = accessor.toCValue(raw)
    cValue shouldBe a[CValue.CProduct]
    val product = cValue.asInstanceOf[CValue.CProduct]
    product.value shouldBe Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30))
  }

  it should "access fields from RProduct" in {
    val structure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val accessor  = TypedValueAccessor(CType.CProduct(structure))
    val raw       = RawValue.RProduct(Array(RawValue.RInt(30), RawValue.RString("Alice")))

    accessor.getField(raw, "age") shouldBe RawValue.RInt(30)
    accessor.getField(raw, "name") shouldBe RawValue.RString("Alice")
  }

  it should "get extractors for primitives" in {
    val intAccessor    = TypedValueAccessor(CType.CInt)
    val floatAccessor  = TypedValueAccessor(CType.CFloat)
    val stringAccessor = TypedValueAccessor(CType.CString)
    val boolAccessor   = TypedValueAccessor(CType.CBoolean)

    intAccessor.getInt(RawValue.RInt(42)) shouldBe 42L
    floatAccessor.getFloat(RawValue.RFloat(3.14)) shouldBe 3.14
    stringAccessor.getString(RawValue.RString("hello")) shouldBe "hello"
    boolAccessor.getBool(RawValue.RBool(true)) shouldBe true
  }

  "RawValue roundtrip" should "preserve values through conversion" in {
    val structure = Map("scores" -> CType.CList(CType.CFloat), "name" -> CType.CString)
    val original = CValue.CProduct(
      Map(
        "name"   -> CValue.CString("Test"),
        "scores" -> CValue.CList(Vector(CValue.CFloat(1.0), CValue.CFloat(2.0)), CType.CFloat)
      ),
      structure
    )

    val raw      = RawValueConverter.fromCValue(original)
    val restored = RawValueConverter.toCValue(raw, CType.CProduct(structure))

    restored shouldBe original
  }

  it should "preserve large int arrays through conversion" in {
    val size     = 10000
    val values   = (0 until size).map(i => CValue.CInt(i.toLong)).toVector
    val original = CValue.CList(values, CType.CInt)

    val raw = RawValueConverter.fromCValue(original)
    raw shouldBe a[RawValue.RIntList]
    raw.asInstanceOf[RawValue.RIntList].length shouldBe size

    val restored = RawValueConverter.toCValue(raw, CType.CList(CType.CInt))
    restored shouldBe original
  }

  it should "preserve large float arrays through conversion" in {
    val size     = 10000
    val values   = (0 until size).map(i => CValue.CFloat(i.toDouble)).toVector
    val original = CValue.CList(values, CType.CFloat)

    val raw = RawValueConverter.fromCValue(original)
    raw shouldBe a[RawValue.RFloatList]
    raw.asInstanceOf[RawValue.RFloatList].length shouldBe size

    val restored = RawValueConverter.toCValue(raw, CType.CList(CType.CFloat))
    restored shouldBe original
  }

  // Union type tests

  "RawValueConverter" should "convert CUnion with Int value to RUnion" in {
    val structure = Map("Int" -> CType.CInt, "String" -> CType.CString)
    val unionCV   = CValue.CUnion(CValue.CInt(42), structure, "Int")

    val raw = RawValueConverter.fromCValue(unionCV)
    raw shouldBe a[RawValue.RUnion]

    val rUnion = raw.asInstanceOf[RawValue.RUnion]
    rUnion.tag shouldBe "Int"
    rUnion.value shouldBe RawValue.RInt(42)
  }

  it should "convert CUnion with String value to RUnion" in {
    val structure = Map("Int" -> CType.CInt, "String" -> CType.CString)
    val unionCV   = CValue.CUnion(CValue.CString("hello"), structure, "String")

    val raw = RawValueConverter.fromCValue(unionCV)
    raw shouldBe a[RawValue.RUnion]

    val rUnion = raw.asInstanceOf[RawValue.RUnion]
    rUnion.tag shouldBe "String"
    rUnion.value shouldBe RawValue.RString("hello")
  }

  it should "convert CUnion with Boolean value to RUnion" in {
    val structure = Map("Boolean" -> CType.CBoolean, "Int" -> CType.CInt)
    val unionCV   = CValue.CUnion(CValue.CBoolean(true), structure, "Boolean")

    val raw = RawValueConverter.fromCValue(unionCV)
    raw shouldBe a[RawValue.RUnion]

    val rUnion = raw.asInstanceOf[RawValue.RUnion]
    rUnion.tag shouldBe "Boolean"
    rUnion.value shouldBe RawValue.RBool(true)
  }

  it should "convert CUnion with Float value to RUnion" in {
    val structure = Map("Float" -> CType.CFloat, "Int" -> CType.CInt)
    val unionCV   = CValue.CUnion(CValue.CFloat(3.14), structure, "Float")

    val raw = RawValueConverter.fromCValue(unionCV)
    raw shouldBe a[RawValue.RUnion]

    val rUnion = raw.asInstanceOf[RawValue.RUnion]
    rUnion.tag shouldBe "Float"
    rUnion.value shouldBe RawValue.RFloat(3.14)
  }

  it should "convert CUnion with record value to RUnion" in {
    val recordStructure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val recordType      = CType.CProduct(recordStructure)
    val structure       = Map("Person" -> recordType, "Error" -> CType.CString)

    val recordValue = CValue.CProduct(
      Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30)),
      recordStructure
    )
    val unionCV = CValue.CUnion(recordValue, structure, "Person")

    val raw = RawValueConverter.fromCValue(unionCV)
    raw shouldBe a[RawValue.RUnion]

    val rUnion = raw.asInstanceOf[RawValue.RUnion]
    rUnion.tag shouldBe "Person"
    rUnion.value shouldBe a[RawValue.RProduct]
  }

  it should "convert CUnion with list value to RUnion" in {
    val listType  = CType.CList(CType.CInt)
    val structure = Map("Numbers" -> listType, "Text" -> CType.CString)

    val listValue = CValue.CList(
      Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
      CType.CInt
    )
    val unionCV = CValue.CUnion(listValue, structure, "Numbers")

    val raw = RawValueConverter.fromCValue(unionCV)
    raw shouldBe a[RawValue.RUnion]

    val rUnion = raw.asInstanceOf[RawValue.RUnion]
    rUnion.tag shouldBe "Numbers"
    rUnion.value shouldBe a[RawValue.RIntList]
  }

  it should "convert CUnion with Optional value to RUnion" in {
    val optType   = CType.COptional(CType.CInt)
    val structure = Map("MaybeInt" -> optType, "String" -> CType.CString)

    val someValue = CValue.CSome(CValue.CInt(42), CType.CInt)
    val unionCV   = CValue.CUnion(someValue, structure, "MaybeInt")

    val raw = RawValueConverter.fromCValue(unionCV)
    raw shouldBe a[RawValue.RUnion]

    val rUnion = raw.asInstanceOf[RawValue.RUnion]
    rUnion.tag shouldBe "MaybeInt"
    rUnion.value shouldBe RawValue.RSome(RawValue.RInt(42))
  }

  "TypedValueAccessor" should "convert RUnion back to CUnion" in {
    val structure = Map("Int" -> CType.CInt, "String" -> CType.CString)
    val accessor  = TypedValueAccessor(CType.CUnion(structure))

    val rUnion = RawValue.RUnion("Int", RawValue.RInt(42))
    val cValue = accessor.toCValue(rUnion)

    cValue shouldBe a[CValue.CUnion]
    val union = cValue.asInstanceOf[CValue.CUnion]
    union.tag shouldBe "Int"
    union.value shouldBe CValue.CInt(42)
    union.structure shouldBe structure
  }

  it should "convert RUnion with String back to CUnion" in {
    val structure = Map("Int" -> CType.CInt, "String" -> CType.CString)
    val accessor  = TypedValueAccessor(CType.CUnion(structure))

    val rUnion = RawValue.RUnion("String", RawValue.RString("hello"))
    val cValue = accessor.toCValue(rUnion)

    cValue shouldBe a[CValue.CUnion]
    val union = cValue.asInstanceOf[CValue.CUnion]
    union.tag shouldBe "String"
    union.value shouldBe CValue.CString("hello")
  }

  it should "convert RUnion with record back to CUnion" in {
    val recordStructure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val recordType      = CType.CProduct(recordStructure)
    val unionStructure  = Map("Person" -> recordType, "Error" -> CType.CString)

    val accessor = TypedValueAccessor(CType.CUnion(unionStructure))

    // Fields sorted alphabetically: age, name
    val rUnion = RawValue.RUnion(
      "Person",
      RawValue.RProduct(
        Array(
          RawValue.RInt(30),
          RawValue.RString("Alice")
        )
      )
    )
    val cValue = accessor.toCValue(rUnion)

    cValue shouldBe a[CValue.CUnion]
    val union = cValue.asInstanceOf[CValue.CUnion]
    union.tag shouldBe "Person"
    union.value shouldBe a[CValue.CProduct]

    val record = union.value.asInstanceOf[CValue.CProduct]
    record.value("name") shouldBe CValue.CString("Alice")
    record.value("age") shouldBe CValue.CInt(30)
  }

  "RUnion roundtrip" should "preserve union values through conversion" in {
    val structure = Map("Int" -> CType.CInt, "String" -> CType.CString)
    val original  = CValue.CUnion(CValue.CInt(42), structure, "Int")

    val raw      = RawValueConverter.fromCValue(original)
    val restored = RawValueConverter.toCValue(raw, CType.CUnion(structure))

    restored shouldBe original
  }

  it should "preserve union with String value through conversion" in {
    val structure = Map("Int" -> CType.CInt, "String" -> CType.CString)
    val original  = CValue.CUnion(CValue.CString("hello"), structure, "String")

    val raw      = RawValueConverter.fromCValue(original)
    val restored = RawValueConverter.toCValue(raw, CType.CUnion(structure))

    restored shouldBe original
  }

  it should "preserve union with record value through conversion" in {
    val recordStructure = Map("name" -> CType.CString, "age" -> CType.CInt)
    val recordType      = CType.CProduct(recordStructure)
    val unionStructure  = Map("Person" -> recordType, "Error" -> CType.CString)

    val recordValue = CValue.CProduct(
      Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30)),
      recordStructure
    )
    val original = CValue.CUnion(recordValue, unionStructure, "Person")

    val raw      = RawValueConverter.fromCValue(original)
    val restored = RawValueConverter.toCValue(raw, CType.CUnion(unionStructure))

    restored shouldBe original
  }

  it should "preserve union with list value through conversion" in {
    val listType  = CType.CList(CType.CInt)
    val structure = Map("Numbers" -> listType, "Text" -> CType.CString)

    val listValue = CValue.CList(
      Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
      CType.CInt
    )
    val original = CValue.CUnion(listValue, structure, "Numbers")

    val raw      = RawValueConverter.fromCValue(original)
    val restored = RawValueConverter.toCValue(raw, CType.CUnion(structure))

    restored shouldBe original
  }

  it should "preserve multi-type union through conversion" in {
    val structure = Map(
      "Int"     -> CType.CInt,
      "String"  -> CType.CString,
      "Boolean" -> CType.CBoolean,
      "Float"   -> CType.CFloat
    )

    // Test each variant
    val intOriginal    = CValue.CUnion(CValue.CInt(42), structure, "Int")
    val stringOriginal = CValue.CUnion(CValue.CString("test"), structure, "String")
    val boolOriginal   = CValue.CUnion(CValue.CBoolean(true), structure, "Boolean")
    val floatOriginal  = CValue.CUnion(CValue.CFloat(3.14), structure, "Float")

    val cType = CType.CUnion(structure)

    RawValueConverter.toCValue(
      RawValueConverter.fromCValue(intOriginal),
      cType
    ) shouldBe intOriginal
    RawValueConverter.toCValue(
      RawValueConverter.fromCValue(stringOriginal),
      cType
    ) shouldBe stringOriginal
    RawValueConverter.toCValue(
      RawValueConverter.fromCValue(boolOriginal),
      cType
    ) shouldBe boolOriginal
    RawValueConverter.toCValue(
      RawValueConverter.fromCValue(floatOriginal),
      cType
    ) shouldBe floatOriginal
  }

  // ============= RawValue Type Tests =============

  // --- RString ---
  "RString" should "have correct toDebugString" in {
    RawValue.RString("hello").toDebugString shouldBe "RString(hello)"
  }

  it should "store and expose value" in {
    RawValue.RString("test").value shouldBe "test"
  }

  it should "support equality" in {
    RawValue.RString("a") shouldBe RawValue.RString("a")
    RawValue.RString("a") should not be RawValue.RString("b")
  }

  // --- RBool ---
  "RBool" should "have correct toDebugString" in {
    RawValue.RBool(true).toDebugString shouldBe "RBool(true)"
    RawValue.RBool(false).toDebugString shouldBe "RBool(false)"
  }

  it should "store and expose value" in {
    RawValue.RBool(true).value shouldBe true
    RawValue.RBool(false).value shouldBe false
  }

  // --- RFloat ---
  "RFloat" should "have correct toDebugString" in {
    RawValue.RFloat(3.14).toDebugString shouldBe "RFloat(3.14)"
  }

  it should "store and expose value" in {
    RawValue.RFloat(2.71).value shouldBe 2.71
  }

  // --- RIntList ---
  "RIntList" should "have correct toDebugString" in {
    RawValue.RIntList(Array(1L, 2L, 3L)).toDebugString shouldBe "RIntList(3 elements)"
  }

  it should "report correct length" in {
    RawValue.RIntList(Array(1L, 2L, 3L)).length shouldBe 3
    RawValue.RIntList(Array.empty[Long]).length shouldBe 0
  }

  it should "support indexed access" in {
    val list = RawValue.RIntList(Array(10L, 20L, 30L))
    list(0) shouldBe 10L
    list(1) shouldBe 20L
    list(2) shouldBe 30L
  }

  it should "support equality for same content arrays" in {
    val a = RawValue.RIntList(Array(1L, 2L, 3L))
    val b = RawValue.RIntList(Array(1L, 2L, 3L))
    a shouldBe b
    a.hashCode() shouldBe b.hashCode()
  }

  it should "not equal different content arrays" in {
    val a = RawValue.RIntList(Array(1L, 2L, 3L))
    val b = RawValue.RIntList(Array(1L, 2L, 4L))
    a should not be b
  }

  it should "not equal non-RIntList" in {
    val a = RawValue.RIntList(Array(1L))
    a.equals("not an RIntList") shouldBe false
  }

  // --- RFloatList ---
  "RFloatList" should "have correct toDebugString" in {
    RawValue.RFloatList(Array(1.1, 2.2)).toDebugString shouldBe "RFloatList(2 elements)"
  }

  it should "report correct length" in {
    RawValue.RFloatList(Array(1.0, 2.0, 3.0)).length shouldBe 3
    RawValue.RFloatList(Array.empty[Double]).length shouldBe 0
  }

  it should "support indexed access" in {
    val list = RawValue.RFloatList(Array(1.1, 2.2, 3.3))
    list(0) shouldBe 1.1
    list(1) shouldBe 2.2
    list(2) shouldBe 3.3
  }

  it should "support equality for same content arrays" in {
    val a = RawValue.RFloatList(Array(1.0, 2.0))
    val b = RawValue.RFloatList(Array(1.0, 2.0))
    a shouldBe b
    a.hashCode() shouldBe b.hashCode()
  }

  it should "not equal different content arrays" in {
    val a = RawValue.RFloatList(Array(1.0, 2.0))
    val b = RawValue.RFloatList(Array(1.0, 3.0))
    a should not be b
  }

  it should "not equal non-RFloatList" in {
    RawValue.RFloatList(Array(1.0)).equals(42) shouldBe false
  }

  // --- RStringList ---
  "RStringList" should "have correct toDebugString" in {
    RawValue.RStringList(Array("a", "b")).toDebugString shouldBe "RStringList(2 elements)"
  }

  it should "report correct length" in {
    RawValue.RStringList(Array("x", "y", "z")).length shouldBe 3
    RawValue.RStringList(Array.empty[String]).length shouldBe 0
  }

  it should "support indexed access" in {
    val list = RawValue.RStringList(Array("hello", "world"))
    list(0) shouldBe "hello"
    list(1) shouldBe "world"
  }

  it should "support equality for same content arrays" in {
    val a = RawValue.RStringList(Array("a", "b"))
    val b = RawValue.RStringList(Array("a", "b"))
    a shouldBe b
    a.hashCode() shouldBe b.hashCode()
  }

  it should "not equal different content arrays" in {
    val a = RawValue.RStringList(Array("a", "b"))
    val b = RawValue.RStringList(Array("a", "c"))
    a should not be b
  }

  it should "not equal non-RStringList" in {
    RawValue.RStringList(Array("a")).equals(List("a")) shouldBe false
  }

  // --- RBoolList ---
  "RBoolList" should "have correct toDebugString" in {
    RawValue.RBoolList(Array(true, false)).toDebugString shouldBe "RBoolList(2 elements)"
  }

  it should "report correct length" in {
    RawValue.RBoolList(Array(true, false, true)).length shouldBe 3
    RawValue.RBoolList(Array.empty[Boolean]).length shouldBe 0
  }

  it should "support indexed access" in {
    val list = RawValue.RBoolList(Array(true, false, true))
    list(0) shouldBe true
    list(1) shouldBe false
    list(2) shouldBe true
  }

  it should "support equality for same content arrays" in {
    val a = RawValue.RBoolList(Array(true, false))
    val b = RawValue.RBoolList(Array(true, false))
    a shouldBe b
    a.hashCode() shouldBe b.hashCode()
  }

  it should "not equal different content arrays" in {
    val a = RawValue.RBoolList(Array(true, false))
    val b = RawValue.RBoolList(Array(true, true))
    a should not be b
  }

  it should "not equal non-RBoolList" in {
    RawValue.RBoolList(Array(true)).equals("true") shouldBe false
  }

  // --- RList ---
  "RList" should "have correct toDebugString" in {
    RawValue.RList(Array(RawValue.RInt(1), RawValue.RInt(2))).toDebugString shouldBe "RList(2 elements)"
  }

  it should "report correct length" in {
    RawValue.RList(Array(RawValue.RInt(1), RawValue.RString("a"))).length shouldBe 2
    RawValue.RList(Array.empty[RawValue]).length shouldBe 0
  }

  it should "support indexed access" in {
    val list = RawValue.RList(Array(RawValue.RInt(10), RawValue.RString("hello")))
    list(0) shouldBe RawValue.RInt(10)
    list(1) shouldBe RawValue.RString("hello")
  }

  it should "support equality for same content" in {
    val a = RawValue.RList(Array(RawValue.RInt(1), RawValue.RInt(2)))
    val b = RawValue.RList(Array(RawValue.RInt(1), RawValue.RInt(2)))
    a shouldBe b
    a.hashCode() shouldBe b.hashCode()
  }

  it should "not equal different content" in {
    val a = RawValue.RList(Array(RawValue.RInt(1)))
    val b = RawValue.RList(Array(RawValue.RInt(2)))
    a should not be b
  }

  it should "not equal non-RList" in {
    RawValue.RList(Array(RawValue.RInt(1))).equals("not a list") shouldBe false
  }

  // --- RMap ---
  "RMap" should "have correct toDebugString" in {
    val map = RawValue.RMap(Array((RawValue.RString("k"), RawValue.RInt(1))))
    map.toDebugString shouldBe "RMap(1 entries)"
  }

  it should "find existing key via get" in {
    val map = RawValue.RMap(Array(
      (RawValue.RString("a"), RawValue.RInt(1)),
      (RawValue.RString("b"), RawValue.RInt(2))
    ))
    map.get(RawValue.RString("a")) shouldBe Some(RawValue.RInt(1))
    map.get(RawValue.RString("b")) shouldBe Some(RawValue.RInt(2))
  }

  it should "return None for missing key" in {
    val map = RawValue.RMap(Array((RawValue.RString("a"), RawValue.RInt(1))))
    map.get(RawValue.RString("missing")) shouldBe None
  }

  it should "return None for empty map" in {
    val map = RawValue.RMap(Array.empty[(RawValue, RawValue)])
    map.get(RawValue.RString("any")) shouldBe None
  }

  it should "support equality" in {
    val a = RawValue.RMap(Array((RawValue.RString("k"), RawValue.RInt(1))))
    val b = RawValue.RMap(Array((RawValue.RString("k"), RawValue.RInt(1))))
    a shouldBe b
    a.hashCode() shouldBe b.hashCode()
  }

  it should "not equal different content" in {
    val a = RawValue.RMap(Array((RawValue.RString("k"), RawValue.RInt(1))))
    val b = RawValue.RMap(Array((RawValue.RString("k"), RawValue.RInt(2))))
    a should not be b
  }

  it should "not equal non-RMap" in {
    RawValue.RMap(Array.empty[(RawValue, RawValue)]).equals(Map.empty) shouldBe false
  }

  // --- RProduct ---
  "RProduct" should "have correct toDebugString" in {
    RawValue.RProduct(Array(RawValue.RInt(1), RawValue.RString("a"))).toDebugString shouldBe "RProduct(2 fields)"
  }

  it should "support indexed access" in {
    val product = RawValue.RProduct(Array(RawValue.RInt(42), RawValue.RString("hello")))
    product(0) shouldBe RawValue.RInt(42)
    product(1) shouldBe RawValue.RString("hello")
  }

  it should "support equality" in {
    val a = RawValue.RProduct(Array(RawValue.RInt(1), RawValue.RString("x")))
    val b = RawValue.RProduct(Array(RawValue.RInt(1), RawValue.RString("x")))
    a shouldBe b
    a.hashCode() shouldBe b.hashCode()
  }

  it should "not equal different content" in {
    val a = RawValue.RProduct(Array(RawValue.RInt(1)))
    val b = RawValue.RProduct(Array(RawValue.RInt(2)))
    a should not be b
  }

  it should "not equal non-RProduct" in {
    RawValue.RProduct(Array(RawValue.RInt(1))).equals("not a product") shouldBe false
  }

  // --- RUnion ---
  "RUnion" should "have correct toDebugString" in {
    RawValue.RUnion("Int", RawValue.RInt(42)).toDebugString shouldBe "RUnion(Int, RInt(42))"
  }

  // --- RSome / RNone ---
  "RSome" should "have correct toDebugString" in {
    RawValue.RSome(RawValue.RInt(42)).toDebugString shouldBe "RSome(RInt(42))"
  }

  "RNone" should "have correct toDebugString" in {
    RawValue.RNone.toDebugString shouldBe "RNone"
  }
}
