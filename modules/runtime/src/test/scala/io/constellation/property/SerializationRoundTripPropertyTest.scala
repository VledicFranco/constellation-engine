package io.constellation.property

import io.constellation.json.given
import io.constellation.{CType, CValue}

import io.circe.parser.*
import io.circe.syntax.*
import org.scalacheck.Gen
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

/** Property-based tests for CValue serialization round-tripping (RFC-017 Phase 3).
  *
  * Verifies that for all generated (CType, CValue) pairs: decode(encode(value)) == value
  *
  * Run with: sbt "runtime/testOnly *SerializationRoundTripPropertyTest"
  */
class SerializationRoundTripPropertyTest
    extends AnyFlatSpec
    with Matchers
    with ScalaCheckPropertyChecks {

  // -------------------------------------------------------------------------
  // Inline generators (runtime test scope doesn't have access to core test sources)
  // -------------------------------------------------------------------------

  private val genPrimitiveCType: Gen[CType] = Gen.oneOf(
    CType.CString,
    CType.CInt,
    CType.CFloat,
    CType.CBoolean
  )

  private val genFieldName: Gen[String] = for {
    head <- Gen.alphaLowerChar
    tail <- Gen
      .listOf(Gen.frequency((3, Gen.alphaNumChar), (1, Gen.const('_'))))
      .map(_.mkString)
    name <- Gen.const(s"$head$tail")
    if name.length <= 20 && name.length >= 1
  } yield name

  private def genCType(maxDepth: Int = 3): Gen[CType] =
    if maxDepth <= 0 then genPrimitiveCType
    else
      Gen.frequency(
        (4, genPrimitiveCType),
        (1, genCType(maxDepth - 1).map(CType.CList.apply)),
        (
          1,
          for {
            kt <- genPrimitiveCType
            vt <- genCType(maxDepth - 1)
          } yield CType.CMap(kt, vt)
        ),
        (
          1,
          for {
            size <- Gen.choose(1, 4)
            fields <- Gen.listOfN(
              size,
              for {
                name <- genFieldName
                t    <- genCType(maxDepth - 1)
              } yield (name, t)
            )
          } yield CType.CProduct(fields.toMap)
        ),
        (1, genCType(maxDepth - 1).map(CType.COptional.apply))
      )

  private def defaultCValue(ctype: CType): CValue = ctype match {
    case CType.CString      => CValue.CString("")
    case CType.CInt         => CValue.CInt(0)
    case CType.CFloat       => CValue.CFloat(0.0)
    case CType.CBoolean     => CValue.CBoolean(false)
    case CType.CList(et)    => CValue.CList(Vector.empty, et)
    case CType.CMap(kt, vt) => CValue.CMap(Vector.empty, kt, vt)
    case CType.CProduct(s) =>
      CValue.CProduct(s.map { case (k, t) => k -> defaultCValue(t) }, s)
    case CType.CUnion(s) =>
      val (tag, t) = s.head
      CValue.CUnion(defaultCValue(t), s, tag)
    case CType.COptional(t) => CValue.CNone(t)
  }

  private def genCValueForType(ctype: CType, maxDepth: Int = 2): Gen[CValue] =
    ctype match {
      case CType.CString  => Gen.alphaNumStr.map(s => CValue.CString(s.take(100)))
      case CType.CInt     => Gen.choose(-1000000L, 1000000L).map(CValue.CInt.apply)
      case CType.CFloat   => Gen.choose(-1e6, 1e6).map(CValue.CFloat.apply)
      case CType.CBoolean => Gen.oneOf(true, false).map(CValue.CBoolean.apply)
      case CType.CList(elemType) =>
        if maxDepth <= 0 then Gen.const(CValue.CList(Vector.empty, elemType))
        else
          for {
            size  <- Gen.choose(0, 5)
            elems <- Gen.listOfN(size, genCValueForType(elemType, maxDepth - 1))
          } yield CValue.CList(elems.toVector, elemType)
      case CType.CMap(kt, vt) =>
        if maxDepth <= 0 then Gen.const(CValue.CMap(Vector.empty, kt, vt))
        else
          for {
            size <- Gen.choose(0, 3)
            pairs <- Gen.listOfN(
              size,
              for {
                k <- genCValueForType(kt, maxDepth - 1)
                v <- genCValueForType(vt, maxDepth - 1)
              } yield (k, v)
            )
          } yield CValue.CMap(pairs.toVector, kt, vt)
      case CType.CProduct(structure) =>
        if maxDepth <= 0 || structure.isEmpty then
          Gen.const(
            CValue.CProduct(
              structure.map { case (k, t) => k -> defaultCValue(t) },
              structure
            )
          )
        else
          for {
            values <- Gen.sequence[List[CValue], CValue](
              structure.values.toList.map(t => genCValueForType(t, maxDepth - 1))
            )
          } yield CValue.CProduct(structure.keys.zip(values).toMap, structure)
      case CType.CUnion(structure) =>
        for {
          (tag, tagType) <- Gen.oneOf(structure.toList)
          value          <- genCValueForType(tagType, maxDepth - 1)
        } yield CValue.CUnion(value, structure, tag)
      case CType.COptional(innerType) =>
        Gen.oneOf(
          Gen.const(CValue.CNone(innerType)),
          genCValueForType(innerType, maxDepth - 1).map(v => CValue.CSome(v, innerType))
        )
    }

  private val genTypedValue: Gen[(CType, CValue)] = for {
    ctype <- genCType(2)
    value <- genCValueForType(ctype)
  } yield (ctype, value)

  // -------------------------------------------------------------------------
  // CValue round-trip: CValue → JSON → CValue
  // -------------------------------------------------------------------------

  "CValue serialization" should "round-trip for all generated typed values" in {
    forAll(genTypedValue) { case (_, value) =>
      val json    = value.asJson
      val decoded = json.as[CValue]
      decoded shouldBe Right(value)
    }
  }

  it should "round-trip for primitive CString values" in {
    forAll(genCValueForType(CType.CString)) { value =>
      val json    = value.asJson
      val decoded = json.as[CValue]
      decoded shouldBe Right(value)
    }
  }

  it should "round-trip for primitive CInt values" in {
    forAll(genCValueForType(CType.CInt)) { value =>
      val json    = value.asJson
      val decoded = json.as[CValue]
      decoded shouldBe Right(value)
    }
  }

  it should "round-trip for primitive CFloat values" in {
    forAll(genCValueForType(CType.CFloat)) { value =>
      val json    = value.asJson
      val decoded = json.as[CValue]
      decoded shouldBe Right(value)
    }
  }

  it should "round-trip for primitive CBoolean values" in {
    forAll(genCValueForType(CType.CBoolean)) { value =>
      val json    = value.asJson
      val decoded = json.as[CValue]
      decoded shouldBe Right(value)
    }
  }

  // -------------------------------------------------------------------------
  // CValue round-trip for composite types
  // -------------------------------------------------------------------------

  it should "round-trip for CList values" in {
    val genList = for {
      elemType <- genPrimitiveCType
      value    <- genCValueForType(CType.CList(elemType))
    } yield value

    forAll(genList) { value =>
      val json    = value.asJson
      val decoded = json.as[CValue]
      decoded shouldBe Right(value)
    }
  }

  it should "round-trip for CMap values" in {
    val genMap = for {
      keyType <- genPrimitiveCType
      valType <- genPrimitiveCType
      value   <- genCValueForType(CType.CMap(keyType, valType))
    } yield value

    forAll(genMap) { value =>
      val json    = value.asJson
      val decoded = json.as[CValue]
      decoded shouldBe Right(value)
    }
  }

  it should "round-trip for CProduct values" in {
    val genProduct = for {
      size <- Gen.choose(1, 4)
      fields <- Gen.listOfN(
        size,
        for {
          name <- genFieldName
          t    <- genPrimitiveCType
        } yield (name, t)
      )
      value <- genCValueForType(CType.CProduct(fields.toMap))
    } yield value

    forAll(genProduct) { value =>
      val json    = value.asJson
      val decoded = json.as[CValue]
      decoded shouldBe Right(value)
    }
  }

  it should "round-trip for COptional values (CSome and CNone)" in {
    val genOptional = for {
      innerType <- genPrimitiveCType
      value     <- genCValueForType(CType.COptional(innerType))
    } yield value

    forAll(genOptional) { value =>
      val json    = value.asJson
      val decoded = json.as[CValue]
      decoded shouldBe Right(value)
    }
  }

  it should "round-trip for CUnion values" in {
    val genUnion = for {
      size <- Gen.choose(1, 3)
      fields <- Gen.listOfN(
        size,
        for {
          name <- genFieldName
          t    <- genPrimitiveCType
        } yield (name, t)
      )
      value <- genCValueForType(CType.CUnion(fields.toMap))
    } yield value

    forAll(genUnion) { value =>
      val json    = value.asJson
      val decoded = json.as[CValue]
      decoded shouldBe Right(value)
    }
  }

  // -------------------------------------------------------------------------
  // CType round-trip: CType → JSON → CType
  // -------------------------------------------------------------------------

  "CType serialization" should "round-trip for all generated types" in {
    forAll(genCType(3)) { ctype =>
      val json    = ctype.asJson
      val decoded = json.as[CType]
      decoded shouldBe Right(ctype)
    }
  }

  it should "round-trip for primitive types" in {
    forAll(genPrimitiveCType) { ctype =>
      val json    = ctype.asJson
      val decoded = json.as[CType]
      decoded shouldBe Right(ctype)
    }
  }

  // -------------------------------------------------------------------------
  // JSON string round-trip (serialize to string, parse back)
  // -------------------------------------------------------------------------

  "CValue JSON string serialization" should "round-trip through string representation" in {
    forAll(genTypedValue) { case (_, value) =>
      val jsonString = value.asJson.noSpaces
      val parsed     = parse(jsonString)
      parsed.isRight shouldBe true
      val decoded = parsed.flatMap(_.as[CValue])
      decoded shouldBe Right(value)
    }
  }

  // -------------------------------------------------------------------------
  // Nested composite types
  // -------------------------------------------------------------------------

  "Nested CValue serialization" should "round-trip for deeply nested types" in {
    val genDeepValue = for {
      ctype <- genCType(3)
      value <- genCValueForType(ctype, 3)
    } yield value

    forAll(genDeepValue) { value =>
      val json    = value.asJson
      val decoded = json.as[CValue]
      decoded shouldBe Right(value)
    }
  }
}
