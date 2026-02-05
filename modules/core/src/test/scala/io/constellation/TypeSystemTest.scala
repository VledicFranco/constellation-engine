package io.constellation

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TypeSystemTest extends AnyFlatSpec with Matchers {

  "CTypeTag" should "provide correct CType for primitive types" in {
    summon[CTypeTag[String]].cType shouldBe CType.CString
    summon[CTypeTag[Long]].cType shouldBe CType.CInt
    summon[CTypeTag[Double]].cType shouldBe CType.CFloat
    summon[CTypeTag[Boolean]].cType shouldBe CType.CBoolean
  }

  it should "provide correct CType for collections" in {
    summon[CTypeTag[Vector[String]]].cType shouldBe CType.CList(CType.CString)
    summon[CTypeTag[List[Long]]].cType shouldBe CType.CList(CType.CInt)
    summon[CTypeTag[Vector[Vector[Boolean]]]].cType shouldBe CType.CList(
      CType.CList(CType.CBoolean)
    )
  }

  it should "provide correct CType for maps" in {
    summon[CTypeTag[Map[String, Long]]].cType shouldBe CType.CMap(CType.CString, CType.CInt)
    summon[CTypeTag[Map[Long, Vector[String]]]].cType shouldBe CType.CMap(
      CType.CInt,
      CType.CList(CType.CString)
    )
  }

  it should "provide correct CType for optionals" in {
    summon[CTypeTag[Option[String]]].cType shouldBe CType.COptional(CType.CString)
    summon[CTypeTag[Option[Long]]].cType shouldBe CType.COptional(CType.CInt)
    summon[CTypeTag[Option[Vector[Boolean]]]].cType shouldBe CType.COptional(
      CType.CList(CType.CBoolean)
    )
    summon[CTypeTag[Option[Option[String]]]].cType shouldBe CType.COptional(
      CType.COptional(CType.CString)
    )
  }

  "CValueInjector" should "inject primitive values correctly" in {
    summon[CValueInjector[String]].inject("hello") shouldBe CValue.CString("hello")
    summon[CValueInjector[Long]].inject(42L) shouldBe CValue.CInt(42L)
    summon[CValueInjector[Double]].inject(3.14) shouldBe CValue.CFloat(3.14)
    summon[CValueInjector[Boolean]].inject(true) shouldBe CValue.CBoolean(true)
  }

  it should "inject vectors correctly" in {
    val result = summon[CValueInjector[Vector[Long]]].inject(Vector(1L, 2L, 3L))
    result shouldBe CValue.CList(
      Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
      CType.CInt
    )
  }

  it should "inject lists correctly" in {
    val result = summon[CValueInjector[List[String]]].inject(List("a", "b"))
    result shouldBe CValue.CList(
      Vector(CValue.CString("a"), CValue.CString("b")),
      CType.CString
    )
  }

  it should "inject maps correctly" in {
    val result = summon[CValueInjector[Map[String, Long]]].inject(Map("x" -> 1L, "y" -> 2L))
    result.ctype shouldBe CType.CMap(CType.CString, CType.CInt)
    result match {
      case CValue.CMap(values, _, _) =>
        values.toSet shouldBe Set(
          (CValue.CString("x"), CValue.CInt(1L)),
          (CValue.CString("y"), CValue.CInt(2L))
        )
      case _ => fail("Expected CValue.CMap")
    }
  }

  it should "inject Some values correctly" in {
    val result = summon[CValueInjector[Option[String]]].inject(Some("hello"))
    result shouldBe CValue.CSome(CValue.CString("hello"), CType.CString)
    result.ctype shouldBe CType.COptional(CType.CString)
  }

  it should "inject None values correctly" in {
    val result = summon[CValueInjector[Option[Long]]].inject(None)
    result shouldBe CValue.CNone(CType.CInt)
    result.ctype shouldBe CType.COptional(CType.CInt)
  }

  "CValueExtractor" should "extract primitive values correctly" in {
    summon[CValueExtractor[String]]
      .extract(CValue.CString("hello"))
      .unsafeRunSync() shouldBe "hello"
    summon[CValueExtractor[Long]].extract(CValue.CInt(42L)).unsafeRunSync() shouldBe 42L
    summon[CValueExtractor[Double]].extract(CValue.CFloat(3.14)).unsafeRunSync() shouldBe 3.14
    summon[CValueExtractor[Boolean]].extract(CValue.CBoolean(false)).unsafeRunSync() shouldBe false
  }

  it should "extract vectors correctly" in {
    val cValue = CValue.CList(Vector(CValue.CInt(1L), CValue.CInt(2L)), CType.CInt)
    val result = summon[CValueExtractor[Vector[Long]]].extract(cValue).unsafeRunSync()
    result shouldBe Vector(1L, 2L)
  }

  it should "extract lists correctly" in {
    val cValue = CValue.CList(Vector(CValue.CString("a"), CValue.CString("b")), CType.CString)
    val result = summon[CValueExtractor[List[String]]].extract(cValue).unsafeRunSync()
    result shouldBe List("a", "b")
  }

  it should "extract maps correctly" in {
    val cValue = CValue.CMap(
      Vector((CValue.CString("x"), CValue.CInt(1L))),
      CType.CString,
      CType.CInt
    )
    val result = summon[CValueExtractor[Map[String, Long]]].extract(cValue).unsafeRunSync()
    result shouldBe Map("x" -> 1L)
  }

  it should "extract Some values correctly" in {
    val cValue = CValue.CSome(CValue.CString("hello"), CType.CString)
    val result = summon[CValueExtractor[Option[String]]].extract(cValue).unsafeRunSync()
    result shouldBe Some("hello")
  }

  it should "extract None values correctly" in {
    val cValue = CValue.CNone(CType.CInt)
    val result = summon[CValueExtractor[Option[Long]]].extract(cValue).unsafeRunSync()
    result shouldBe None
  }

  it should "fail with descriptive error on type mismatch" in {
    val result = summon[CValueExtractor[String]].extract(CValue.CInt(42L)).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("Expected CValue.CString")
  }

  "CValue" should "report correct ctype" in {
    CValue.CString("test").ctype shouldBe CType.CString
    CValue.CInt(1L).ctype shouldBe CType.CInt
    CValue.CFloat(1.0).ctype shouldBe CType.CFloat
    CValue.CBoolean(true).ctype shouldBe CType.CBoolean
    CValue.CList(Vector.empty, CType.CString).ctype shouldBe CType.CList(CType.CString)
    CValue.CMap(Vector.empty, CType.CString, CType.CInt).ctype shouldBe CType.CMap(
      CType.CString,
      CType.CInt
    )
    CValue.CSome(CValue.CString("test"), CType.CString).ctype shouldBe CType.COptional(
      CType.CString
    )
    CValue.CNone(CType.CInt).ctype shouldBe CType.COptional(CType.CInt)
  }

// Test case classes for type derivation
  case class SimpleRecord(name: String, age: Long)
  case class NestedRecord(id: Long, data: SimpleRecord)
  case class WithCollections(items: List[Long], mapping: Map[String, String])
  case class WithOptional(value: Option[String])
  case class EmptyRecord()
  case class AllPrimitives(s: String, l: Long, d: Double, b: Boolean)

  "deriveType" should "derive CType for simple case class" in {
    val ctype = deriveType[SimpleRecord]
    ctype shouldBe CType.CProduct(
      Map(
        "name" -> CType.CString,
        "age"  -> CType.CInt
      )
    )
  }

  it should "derive CType for nested case class" in {
    val ctype = deriveType[NestedRecord]
    ctype shouldBe CType.CProduct(
      Map(
        "id" -> CType.CInt,
        "data" -> CType.CProduct(
          Map("name" -> CType.CString, "age" -> CType.CInt)
        )
      )
    )
  }

  it should "derive CType with collection fields" in {
    val ctype = deriveType[WithCollections]
    ctype shouldBe CType.CProduct(
      Map(
        "items"   -> CType.CList(CType.CInt),
        "mapping" -> CType.CMap(CType.CString, CType.CString)
      )
    )
  }

  it should "derive CType with Optional field" in {
    val ctype = deriveType[WithOptional]
    ctype shouldBe CType.CProduct(
      Map(
        "value" -> CType.COptional(CType.CString)
      )
    )
  }

  it should "derive CType for empty case class" in {
    val ctype = deriveType[EmptyRecord]
    ctype shouldBe CType.CProduct(Map.empty)
  }

  it should "derive CType with all primitive types" in {
    val ctype = deriveType[AllPrimitives]
    ctype shouldBe CType.CProduct(
      Map(
        "s" -> CType.CString,
        "l" -> CType.CInt,
        "d" -> CType.CFloat,
        "b" -> CType.CBoolean
      )
    )
  }

  "CTypeTag.productTag" should "be summoned for case class" in {
    val tag = summon[CTypeTag[SimpleRecord]]
    tag.cType shouldBe CType.CProduct(
      Map(
        "name" -> CType.CString,
        "age"  -> CType.CInt
      )
    )
  }

  it should "not interfere with primitive type tags" in {
    // Verify primitive types still work correctly (no ambiguity)
    summon[CTypeTag[String]].cType shouldBe CType.CString
    summon[CTypeTag[Long]].cType shouldBe CType.CInt
    summon[CTypeTag[Double]].cType shouldBe CType.CFloat
    summon[CTypeTag[Boolean]].cType shouldBe CType.CBoolean
    summon[CTypeTag[List[String]]].cType shouldBe CType.CList(CType.CString)
    summon[CTypeTag[Map[String, Long]]].cType shouldBe CType.CMap(CType.CString, CType.CInt)
    summon[CTypeTag[Option[String]]].cType shouldBe CType.COptional(CType.CString)
  }

  it should "work with deeply nested structures" in {
    case class DeepNest(inner: NestedRecord)
    val ctype = deriveType[DeepNest]
    ctype shouldBe CType.CProduct(
      Map(
        "inner" -> CType.CProduct(
          Map(
            "id" -> CType.CInt,
            "data" -> CType.CProduct(
              Map("name" -> CType.CString, "age" -> CType.CInt)
            )
          )
        )
      )
    )
  }

  "Roundtrip" should "inject and extract values correctly" in {
    def roundtrip[A](value: A)(using
        injector: CValueInjector[A],
        extractor: CValueExtractor[A]
    ): A =
      extractor.extract(injector.inject(value)).unsafeRunSync()

    roundtrip("hello") shouldBe "hello"
    roundtrip(42L) shouldBe 42L
    roundtrip(3.14) shouldBe 3.14
    roundtrip(true) shouldBe true
    roundtrip(Vector(1L, 2L, 3L)) shouldBe Vector(1L, 2L, 3L)
    roundtrip(List("a", "b", "c")) shouldBe List("a", "b", "c")
    roundtrip(Map("x" -> 1L, "y" -> 2L)) shouldBe Map("x" -> 1L, "y" -> 2L)
    roundtrip(Some("hello"): Option[String]) shouldBe Some("hello")
    roundtrip(None: Option[Long]) shouldBe None
    roundtrip(Some(Vector(1L, 2L)): Option[Vector[Long]]) shouldBe Some(Vector(1L, 2L))
  }
}
