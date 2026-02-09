package io.constellation.stdlib.categories

import io.constellation.lang.semantic.*
import io.constellation.stdlib.StdLib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MathFunctionsTest extends AnyFlatSpec with Matchers {

  // Module spec tests

  "addModule" should "have correct spec name" in {
    StdLib.addModule.spec.name shouldBe "stdlib.add"
  }

  it should "have correct description" in {
    StdLib.addModule.spec.description shouldBe "Add two integers"
  }

  it should "have stdlib and math tags" in {
    StdLib.addModule.spec.tags should contain allOf ("stdlib", "math")
  }

  "subtractModule" should "have correct spec name" in {
    StdLib.subtractModule.spec.name shouldBe "stdlib.subtract"
  }

  it should "have correct description" in {
    StdLib.subtractModule.spec.description shouldBe "Subtract two integers"
  }

  it should "have stdlib and math tags" in {
    StdLib.subtractModule.spec.tags should contain allOf ("stdlib", "math")
  }

  "multiplyModule" should "have correct spec name" in {
    StdLib.multiplyModule.spec.name shouldBe "stdlib.multiply"
  }

  "divideModule" should "have correct spec name" in {
    StdLib.divideModule.spec.name shouldBe "stdlib.divide"
  }

  "maxModule" should "have correct spec name" in {
    StdLib.maxModule.spec.name shouldBe "stdlib.max"
  }

  "minModule" should "have correct spec name" in {
    StdLib.minModule.spec.name shouldBe "stdlib.min"
  }

  "absModule" should "have correct spec name" in {
    StdLib.absModule.spec.name shouldBe "stdlib.abs"
  }

  it should "have correct description" in {
    StdLib.absModule.spec.description shouldBe "Absolute value of an integer"
  }

  "moduloModule" should "have correct spec name" in {
    StdLib.moduloModule.spec.name shouldBe "stdlib.modulo"
  }

  "roundModule" should "have correct spec name" in {
    StdLib.roundModule.spec.name shouldBe "stdlib.round"
  }

  "negateModule" should "have correct spec name" in {
    StdLib.negateModule.spec.name shouldBe "stdlib.negate"
  }

  it should "have correct description" in {
    StdLib.negateModule.spec.description shouldBe "Negate a number"
  }

  // Signature tests

  "addSignature" should "have correct name" in {
    StdLib.addSignature.name shouldBe "add"
  }

  it should "have correct params" in {
    StdLib.addSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
  }

  it should "return Int" in {
    StdLib.addSignature.returns shouldBe SemanticType.SInt
  }

  it should "have correct module name" in {
    StdLib.addSignature.moduleName shouldBe "stdlib.add"
  }

  it should "have stdlib.math namespace" in {
    StdLib.addSignature.namespace shouldBe Some("stdlib.math")
  }

  "subtractSignature" should "have correct params and return type" in {
    StdLib.subtractSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.subtractSignature.returns shouldBe SemanticType.SInt
    StdLib.subtractSignature.namespace shouldBe Some("stdlib.math")
  }

  "multiplySignature" should "have correct params and return type" in {
    StdLib.multiplySignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.multiplySignature.returns shouldBe SemanticType.SInt
  }

  "divideSignature" should "have correct params and return type" in {
    StdLib.divideSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.divideSignature.returns shouldBe SemanticType.SInt
  }

  "maxSignature" should "have correct params and return type" in {
    StdLib.maxSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.maxSignature.returns shouldBe SemanticType.SInt
  }

  "minSignature" should "have correct params and return type" in {
    StdLib.minSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.minSignature.returns shouldBe SemanticType.SInt
  }

  "absSignature" should "take single Int param" in {
    StdLib.absSignature.params shouldBe List("value" -> SemanticType.SInt)
    StdLib.absSignature.returns shouldBe SemanticType.SInt
  }

  "moduloSignature" should "have two Int params" in {
    StdLib.moduloSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.moduloSignature.returns shouldBe SemanticType.SInt
  }

  "roundSignature" should "take Float and return Int" in {
    StdLib.roundSignature.params shouldBe List("value" -> SemanticType.SFloat)
    StdLib.roundSignature.returns shouldBe SemanticType.SInt
  }

  "negateSignature" should "take Int and return Int" in {
    StdLib.negateSignature.params shouldBe List("value" -> SemanticType.SInt)
    StdLib.negateSignature.returns shouldBe SemanticType.SInt
  }

  // Collection tests

  "mathSignatures" should "contain exactly 10 signatures" in {
    StdLib.mathSignatures should have size 10
  }

  it should "contain all math function signatures" in {
    val names = StdLib.mathSignatures.map(_.name)
    names should contain allOf (
      "add", "subtract", "multiply", "divide",
      "max", "min", "abs", "modulo", "round", "negate"
    )
  }

  "mathModules" should "contain exactly 10 modules" in {
    StdLib.mathModules should have size 10
  }

  it should "be keyed by module spec name" in {
    StdLib.mathModules.keys should contain allOf (
      "stdlib.add", "stdlib.subtract", "stdlib.multiply", "stdlib.divide",
      "stdlib.max", "stdlib.min", "stdlib.abs", "stdlib.modulo",
      "stdlib.round", "stdlib.negate"
    )
  }

  it should "have modules matching their keys" in {
    StdLib.mathModules.foreach { case (key, module) =>
      module.spec.name shouldBe key
    }
  }

  // All signatures should have stdlib.math namespace
  "all math signatures" should "have stdlib.math namespace" in {
    StdLib.mathSignatures.foreach { sig =>
      sig.namespace shouldBe Some("stdlib.math")
    }
  }
}
