package io.constellation.stdlib.categories

import io.constellation.lang.semantic.*
import io.constellation.stdlib.StdLib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StringFunctionsTest extends AnyFlatSpec with Matchers {

  // Module spec tests

  "concatModule" should "have correct spec name" in {
    StdLib.concatModule.spec.name shouldBe "stdlib.concat"
  }

  it should "have correct description" in {
    StdLib.concatModule.spec.description shouldBe "Concatenate two strings"
  }

  it should "have stdlib and string tags" in {
    StdLib.concatModule.spec.tags should contain allOf ("stdlib", "string")
  }

  "stringLengthModule" should "have correct spec name" in {
    StdLib.stringLengthModule.spec.name shouldBe "stdlib.string-length"
  }

  "joinModule" should "have correct spec name" in {
    StdLib.joinModule.spec.name shouldBe "stdlib.join"
  }

  it should "have correct description" in {
    StdLib.joinModule.spec.description shouldBe "Join strings with delimiter"
  }

  "splitModule" should "have correct spec name" in {
    StdLib.splitModule.spec.name shouldBe "stdlib.split"
  }

  "containsModule" should "have correct spec name" in {
    StdLib.containsModule.spec.name shouldBe "stdlib.contains"
  }

  "trimModule" should "have correct spec name" in {
    StdLib.trimModule.spec.name shouldBe "stdlib.trim"
  }

  "replaceModule" should "have correct spec name" in {
    StdLib.replaceModule.spec.name shouldBe "stdlib.replace"
  }

  // Signature tests

  "concatSignature" should "have correct params" in {
    StdLib.concatSignature.params shouldBe List(
      "a" -> SemanticType.SString,
      "b" -> SemanticType.SString
    )
  }

  it should "return String" in {
    StdLib.concatSignature.returns shouldBe SemanticType.SString
  }

  it should "have stdlib.string namespace" in {
    StdLib.concatSignature.namespace shouldBe Some("stdlib.string")
  }

  "stringLengthSignature" should "take String and return Int" in {
    StdLib.stringLengthSignature.params shouldBe List("value" -> SemanticType.SString)
    StdLib.stringLengthSignature.returns shouldBe SemanticType.SInt
  }

  "joinSignature" should "take List<String> and separator" in {
    StdLib.joinSignature.params shouldBe List(
      "list"      -> SemanticType.SList(SemanticType.SString),
      "separator" -> SemanticType.SString
    )
    StdLib.joinSignature.returns shouldBe SemanticType.SString
  }

  "splitSignature" should "take String and delimiter, return List<String>" in {
    StdLib.splitSignature.params shouldBe List(
      "value"     -> SemanticType.SString,
      "substring" -> SemanticType.SString
    )
    StdLib.splitSignature.returns shouldBe SemanticType.SList(SemanticType.SString)
  }

  "containsSignature" should "return Boolean" in {
    StdLib.containsSignature.params shouldBe List(
      "value"     -> SemanticType.SString,
      "substring" -> SemanticType.SString
    )
    StdLib.containsSignature.returns shouldBe SemanticType.SBoolean
  }

  "trimSignature" should "take String and return String" in {
    StdLib.trimSignature.params shouldBe List("value" -> SemanticType.SString)
    StdLib.trimSignature.returns shouldBe SemanticType.SString
  }

  "replaceSignature" should "take three String params" in {
    StdLib.replaceSignature.params shouldBe List(
      "value"       -> SemanticType.SString,
      "target"      -> SemanticType.SString,
      "replacement" -> SemanticType.SString
    )
    StdLib.replaceSignature.returns shouldBe SemanticType.SString
  }

  // Collection tests

  "stringSignatures" should "contain exactly 7 signatures" in {
    StdLib.stringSignatures should have size 7
  }

  it should "contain all string function signatures" in {
    val names = StdLib.stringSignatures.map(_.name)
    names should contain allOf (
      "concat", "string-length", "join", "split", "contains", "trim", "replace"
    )
  }

  "stringModules" should "contain exactly 7 modules" in {
    StdLib.stringModules should have size 7
  }

  it should "be keyed by module spec name" in {
    StdLib.stringModules.keys should contain allOf (
      "stdlib.concat", "stdlib.string-length", "stdlib.join",
      "stdlib.split", "stdlib.contains", "stdlib.trim", "stdlib.replace"
    )
  }

  it should "have modules matching their keys" in {
    StdLib.stringModules.foreach { case (key, module) =>
      module.spec.name shouldBe key
    }
  }

  "all string signatures" should "have stdlib.string namespace" in {
    StdLib.stringSignatures.foreach { sig =>
      sig.namespace shouldBe Some("stdlib.string")
    }
  }
}
