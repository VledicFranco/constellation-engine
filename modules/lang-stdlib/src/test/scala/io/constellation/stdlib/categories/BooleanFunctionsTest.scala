package io.constellation.stdlib.categories

import io.constellation.lang.semantic.*
import io.constellation.stdlib.StdLib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class BooleanFunctionsTest extends AnyFlatSpec with Matchers {

  // Module spec tests

  "andModule" should "have correct spec name" in {
    StdLib.andModule.spec.name shouldBe "stdlib.and"
  }

  it should "have correct description" in {
    StdLib.andModule.spec.description shouldBe "Logical AND"
  }

  it should "have stdlib and boolean tags" in {
    StdLib.andModule.spec.tags should contain allOf ("stdlib", "boolean")
  }

  "orModule" should "have correct spec name" in {
    StdLib.orModule.spec.name shouldBe "stdlib.or"
  }

  it should "have correct description" in {
    StdLib.orModule.spec.description shouldBe "Logical OR"
  }

  it should "have stdlib and boolean tags" in {
    StdLib.orModule.spec.tags should contain allOf ("stdlib", "boolean")
  }

  "notModule" should "have correct spec name" in {
    StdLib.notModule.spec.name shouldBe "stdlib.not"
  }

  it should "have correct description" in {
    StdLib.notModule.spec.description shouldBe "Logical NOT"
  }

  it should "have stdlib and boolean tags" in {
    StdLib.notModule.spec.tags should contain allOf ("stdlib", "boolean")
  }

  // Signature tests

  "andSignature" should "have correct params" in {
    StdLib.andSignature.params shouldBe List(
      "a" -> SemanticType.SBoolean,
      "b" -> SemanticType.SBoolean
    )
  }

  it should "return Boolean" in {
    StdLib.andSignature.returns shouldBe SemanticType.SBoolean
  }

  it should "have stdlib.bool namespace" in {
    StdLib.andSignature.namespace shouldBe Some("stdlib.bool")
  }

  "orSignature" should "have correct params and return type" in {
    StdLib.orSignature.params shouldBe List(
      "a" -> SemanticType.SBoolean,
      "b" -> SemanticType.SBoolean
    )
    StdLib.orSignature.returns shouldBe SemanticType.SBoolean
    StdLib.orSignature.namespace shouldBe Some("stdlib.bool")
  }

  "notSignature" should "take single Boolean param" in {
    StdLib.notSignature.params shouldBe List("value" -> SemanticType.SBoolean)
    StdLib.notSignature.returns shouldBe SemanticType.SBoolean
    StdLib.notSignature.namespace shouldBe Some("stdlib.bool")
  }

  // Collection tests

  "booleanSignatures" should "contain exactly 3 signatures" in {
    StdLib.booleanSignatures should have size 3
  }

  it should "contain all boolean function signatures" in {
    val names = StdLib.booleanSignatures.map(_.name)
    names should contain allOf ("and", "or", "not")
  }

  "booleanModules" should "contain exactly 3 modules" in {
    StdLib.booleanModules should have size 3
  }

  it should "be keyed by module spec name" in {
    StdLib.booleanModules.keys should contain allOf (
      "stdlib.and", "stdlib.or", "stdlib.not"
    )
  }

  it should "have modules matching their keys" in {
    StdLib.booleanModules.foreach { case (key, module) =>
      module.spec.name shouldBe key
    }
  }

  "all boolean signatures" should "have stdlib.bool namespace" in {
    StdLib.booleanSignatures.foreach { sig =>
      sig.namespace shouldBe Some("stdlib.bool")
    }
  }
}
