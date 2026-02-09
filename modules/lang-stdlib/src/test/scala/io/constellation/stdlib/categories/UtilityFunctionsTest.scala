package io.constellation.stdlib.categories

import io.constellation.lang.semantic.*
import io.constellation.stdlib.StdLib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UtilityFunctionsTest extends AnyFlatSpec with Matchers {

  // Module spec tests

  "identityModule" should "have correct spec name" in {
    StdLib.identityModule.spec.name shouldBe "stdlib.identity"
  }

  it should "have correct description" in {
    StdLib.identityModule.spec.description shouldBe "Pass-through identity function"
  }

  it should "have stdlib and utility tags" in {
    StdLib.identityModule.spec.tags should contain allOf ("stdlib", "utility")
  }

  "logModule" should "have correct spec name" in {
    StdLib.logModule.spec.name shouldBe "stdlib.log"
  }

  it should "have correct description" in {
    StdLib.logModule.spec.description shouldBe "Log a message and pass through"
  }

  it should "have stdlib and debug tags" in {
    StdLib.logModule.spec.tags should contain allOf ("stdlib", "debug")
  }

  // Signature tests

  "identitySignature" should "take String and return String" in {
    StdLib.identitySignature.params shouldBe List("value" -> SemanticType.SString)
    StdLib.identitySignature.returns shouldBe SemanticType.SString
  }

  it should "have stdlib namespace" in {
    StdLib.identitySignature.namespace shouldBe Some("stdlib")
  }

  it should "have correct module name" in {
    StdLib.identitySignature.moduleName shouldBe "stdlib.identity"
  }

  "logSignature" should "take String message and return String" in {
    StdLib.logSignature.params shouldBe List("message" -> SemanticType.SString)
    StdLib.logSignature.returns shouldBe SemanticType.SString
  }

  it should "have stdlib.debug namespace" in {
    StdLib.logSignature.namespace shouldBe Some("stdlib.debug")
  }

  it should "have correct module name" in {
    StdLib.logSignature.moduleName shouldBe "stdlib.log"
  }

  // Collection tests

  "utilitySignatures" should "contain exactly 2 signatures" in {
    StdLib.utilitySignatures should have size 2
  }

  it should "contain identity and log" in {
    val names = StdLib.utilitySignatures.map(_.name)
    names should contain allOf ("identity", "log")
  }

  "utilityModules" should "contain exactly 2 modules" in {
    StdLib.utilityModules should have size 2
  }

  it should "be keyed by module spec name" in {
    StdLib.utilityModules.keys should contain allOf (
      "stdlib.identity", "stdlib.log"
    )
  }

  it should "have modules matching their keys" in {
    StdLib.utilityModules.foreach { case (key, module) =>
      module.spec.name shouldBe key
    }
  }
}
