package io.constellation.stdlib.categories

import io.constellation.lang.semantic.*
import io.constellation.stdlib.StdLib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ComparisonFunctionsTest extends AnyFlatSpec with Matchers {

  // Module spec tests

  "eqIntModule" should "have correct spec name" in {
    StdLib.eqIntModule.spec.name shouldBe "stdlib.eq-int"
  }

  it should "have correct description" in {
    StdLib.eqIntModule.spec.description shouldBe "Check if two integers are equal"
  }

  it should "have stdlib and comparison tags" in {
    StdLib.eqIntModule.spec.tags should contain allOf ("stdlib", "comparison")
  }

  "eqStringModule" should "have correct spec name" in {
    StdLib.eqStringModule.spec.name shouldBe "stdlib.eq-string"
  }

  it should "have correct description" in {
    StdLib.eqStringModule.spec.description shouldBe "Check if two strings are equal"
  }

  "gtModule" should "have correct spec name" in {
    StdLib.gtModule.spec.name shouldBe "stdlib.gt"
  }

  it should "have correct description" in {
    StdLib.gtModule.spec.description shouldBe "Check if a > b"
  }

  "ltModule" should "have correct spec name" in {
    StdLib.ltModule.spec.name shouldBe "stdlib.lt"
  }

  "gteModule" should "have correct spec name" in {
    StdLib.gteModule.spec.name shouldBe "stdlib.gte"
  }

  "lteModule" should "have correct spec name" in {
    StdLib.lteModule.spec.name shouldBe "stdlib.lte"
  }

  // Signature tests

  "eqIntSignature" should "have two Int params and return Boolean" in {
    StdLib.eqIntSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.eqIntSignature.returns shouldBe SemanticType.SBoolean
  }

  it should "have stdlib.compare namespace" in {
    StdLib.eqIntSignature.namespace shouldBe Some("stdlib.compare")
  }

  "eqStringSignature" should "have two String params and return Boolean" in {
    StdLib.eqStringSignature.params shouldBe List(
      "a" -> SemanticType.SString,
      "b" -> SemanticType.SString
    )
    StdLib.eqStringSignature.returns shouldBe SemanticType.SBoolean
    StdLib.eqStringSignature.namespace shouldBe Some("stdlib.compare")
  }

  "gtSignature" should "have two Int params and return Boolean" in {
    StdLib.gtSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.gtSignature.returns shouldBe SemanticType.SBoolean
  }

  "ltSignature" should "have two Int params and return Boolean" in {
    StdLib.ltSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.ltSignature.returns shouldBe SemanticType.SBoolean
  }

  "gteSignature" should "have two Int params and return Boolean" in {
    StdLib.gteSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.gteSignature.returns shouldBe SemanticType.SBoolean
  }

  "lteSignature" should "have two Int params and return Boolean" in {
    StdLib.lteSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.lteSignature.returns shouldBe SemanticType.SBoolean
  }

  // Collection tests

  "comparisonSignatures" should "contain exactly 6 signatures" in {
    StdLib.comparisonSignatures should have size 6
  }

  it should "contain all comparison function signatures" in {
    val names = StdLib.comparisonSignatures.map(_.name)
    names should contain allOf (
      "eq-int", "eq-string", "gt", "lt", "gte", "lte"
    )
  }

  "comparisonModules" should "contain exactly 6 modules" in {
    StdLib.comparisonModules should have size 6
  }

  it should "be keyed by module spec name" in {
    StdLib.comparisonModules.keys should contain allOf (
      "stdlib.eq-int", "stdlib.eq-string", "stdlib.gt",
      "stdlib.lt", "stdlib.gte", "stdlib.lte"
    )
  }

  it should "have modules matching their keys" in {
    StdLib.comparisonModules.foreach { case (key, module) =>
      module.spec.name shouldBe key
    }
  }

  "all comparison signatures" should "have stdlib.compare namespace" in {
    StdLib.comparisonSignatures.foreach { sig =>
      sig.namespace shouldBe Some("stdlib.compare")
    }
  }

  "all comparison signatures" should "return Boolean" in {
    StdLib.comparisonSignatures.foreach { sig =>
      sig.returns shouldBe SemanticType.SBoolean
    }
  }
}
