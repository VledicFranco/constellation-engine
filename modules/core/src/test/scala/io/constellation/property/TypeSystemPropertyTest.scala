package io.constellation.property

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import io.constellation.{CType, CValue}
import io.constellation.property.ConstellationGenerators._

/** Property-based tests for the Constellation type system (RFC-013 Phase 5.2)
  *
  * Verifies structural invariants of CType and CValue using ScalaCheck generators.
  *
  * Run with: sbt "core/testOnly *TypeSystemPropertyTest"
  */
class TypeSystemPropertyTest extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  // -------------------------------------------------------------------------
  // CValue.ctype consistency
  // -------------------------------------------------------------------------

  "CValue" should "always have a ctype matching its construction type" in {
    forAll(genTypedValue) { case (expectedType, value) =>
      value.ctype shouldBe expectedType
    }
  }

  // -------------------------------------------------------------------------
  // CType generation coverage
  // -------------------------------------------------------------------------

  "CType generator" should "produce all primitive types" in {
    val primitiveTypes = Set[CType](CType.CString, CType.CInt, CType.CFloat, CType.CBoolean)
    val generated = (1 to 200).flatMap(_ => genPrimitiveCType.sample).toSet

    primitiveTypes.foreach { pt =>
      generated should contain(pt)
    }
  }

  it should "produce composite types" in {
    val generated = (1 to 500).flatMap(_ => genCType(3).sample)

    // Should produce at least some composite types
    generated.exists(_.isInstanceOf[CType.CList]) shouldBe true
    generated.exists(_.isInstanceOf[CType.CProduct]) shouldBe true
    generated.exists(_.isInstanceOf[CType.COptional]) shouldBe true
  }

  // -------------------------------------------------------------------------
  // CValue for given CType is structurally valid
  // -------------------------------------------------------------------------

  "CValue generator" should "produce valid CString values" in {
    forAll(genCValueForType(CType.CString)) { value =>
      value shouldBe a[CValue.CString]
      value.ctype shouldBe CType.CString
    }
  }

  it should "produce valid CInt values" in {
    forAll(genCValueForType(CType.CInt)) { value =>
      value shouldBe a[CValue.CInt]
      value.ctype shouldBe CType.CInt
    }
  }

  it should "produce valid CFloat values" in {
    forAll(genCValueForType(CType.CFloat)) { value =>
      value shouldBe a[CValue.CFloat]
      value.ctype shouldBe CType.CFloat
    }
  }

  it should "produce valid CBoolean values" in {
    forAll(genCValueForType(CType.CBoolean)) { value =>
      value shouldBe a[CValue.CBoolean]
      value.ctype shouldBe CType.CBoolean
    }
  }

  it should "produce valid CList values" in {
    val listType = CType.CList(CType.CInt)
    forAll(genCValueForType(listType)) { value =>
      value shouldBe a[CValue.CList]
      value.ctype shouldBe listType
      val list = value.asInstanceOf[CValue.CList]
      list.value.foreach(_.ctype shouldBe CType.CInt)
    }
  }

  it should "produce valid CProduct values" in {
    val productType = CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))
    forAll(genCValueForType(productType)) { value =>
      value shouldBe a[CValue.CProduct]
      value.ctype shouldBe productType
      val product = value.asInstanceOf[CValue.CProduct]
      product.value.keys shouldBe Set("name", "age")
      product.value("name").ctype shouldBe CType.CString
      product.value("age").ctype shouldBe CType.CInt
    }
  }

  it should "produce valid COptional values" in {
    val optionalType = CType.COptional(CType.CString)
    forAll(genCValueForType(optionalType)) { value =>
      value.ctype shouldBe optionalType
      value match {
        case CValue.CSome(inner, innerType) =>
          inner.ctype shouldBe CType.CString
          innerType shouldBe CType.CString
        case CValue.CNone(innerType) =>
          innerType shouldBe CType.CString
        case _ => fail(s"Expected CSome or CNone, got $value")
      }
    }
  }

  // -------------------------------------------------------------------------
  // CType equality is reflexive, symmetric, transitive
  // -------------------------------------------------------------------------

  "CType equality" should "be reflexive" in {
    forAll(genCType()) { ctype =>
      ctype shouldBe ctype
    }
  }

  it should "be deterministic for the same structure" in {
    // Generating two CProducts with the same fields should be equal
    val t1 = CType.CProduct(Map("a" -> CType.CString, "b" -> CType.CInt))
    val t2 = CType.CProduct(Map("a" -> CType.CString, "b" -> CType.CInt))
    t1 shouldBe t2
  }

  // -------------------------------------------------------------------------
  // CValue structural invariants
  // -------------------------------------------------------------------------

  "CValue structural invariant" should "hold: list elements match declared subtype" in {
    forAll(genCType(1)) { elemType =>
      forAll(genCValueForType(CType.CList(elemType), 1)) { value =>
        val list = value.asInstanceOf[CValue.CList]
        list.subtype shouldBe elemType
        list.value.foreach(_.ctype shouldBe elemType)
      }
    }
  }

  it should "hold: product fields match declared structure" in {
    val structure = Map("x" -> CType.CInt, "y" -> CType.CString)
    forAll(genCValueForType(CType.CProduct(structure))) { value =>
      val product = value.asInstanceOf[CValue.CProduct]
      product.structure shouldBe structure
      structure.foreach { case (name, expectedType) =>
        product.value(name).ctype shouldBe expectedType
      }
    }
  }

  it should "hold: map keys and values match declared types" in {
    val mapType = CType.CMap(CType.CString, CType.CInt)
    forAll(genCValueForType(mapType, 1)) { value =>
      val map = value.asInstanceOf[CValue.CMap]
      map.keysType shouldBe CType.CString
      map.valuesType shouldBe CType.CInt
      map.value.foreach { case (k, v) =>
        k.ctype shouldBe CType.CString
        v.ctype shouldBe CType.CInt
      }
    }
  }
}
