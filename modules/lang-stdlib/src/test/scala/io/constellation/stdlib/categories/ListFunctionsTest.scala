package io.constellation.stdlib.categories

import io.constellation.lang.semantic.*
import io.constellation.stdlib.StdLib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ListFunctionsTest extends AnyFlatSpec with Matchers {

  // Module spec tests

  "listLengthModule" should "have correct spec name" in {
    StdLib.listLengthModule.spec.name shouldBe "stdlib.list-length"
  }

  it should "have correct description" in {
    StdLib.listLengthModule.spec.description shouldBe "Get the length of a list"
  }

  it should "have stdlib and list tags" in {
    StdLib.listLengthModule.spec.tags should contain allOf ("stdlib", "list")
  }

  "listFirstModule" should "have correct spec name" in {
    StdLib.listFirstModule.spec.name shouldBe "stdlib.list-first"
  }

  "listLastModule" should "have correct spec name" in {
    StdLib.listLastModule.spec.name shouldBe "stdlib.list-last"
  }

  "listIsEmptyModule" should "have correct spec name" in {
    StdLib.listIsEmptyModule.spec.name shouldBe "stdlib.list-is-empty"
  }

  "listSumModule" should "have correct spec name" in {
    StdLib.listSumModule.spec.name shouldBe "stdlib.list-sum"
  }

  it should "have correct description" in {
    StdLib.listSumModule.spec.description shouldBe "Sum all elements in a list"
  }

  "listConcatModule" should "have correct spec name" in {
    StdLib.listConcatModule.spec.name shouldBe "stdlib.list-concat"
  }

  "listContainsModule" should "have correct spec name" in {
    StdLib.listContainsModule.spec.name shouldBe "stdlib.list-contains"
  }

  "listReverseModule" should "have correct spec name" in {
    StdLib.listReverseModule.spec.name shouldBe "stdlib.list-reverse"
  }

  // Signature tests

  "listLengthSignature" should "take List<Int> and return Int" in {
    StdLib.listLengthSignature.params shouldBe List(
      "list" -> SemanticType.SList(SemanticType.SInt)
    )
    StdLib.listLengthSignature.returns shouldBe SemanticType.SInt
  }

  it should "have stdlib.list namespace" in {
    StdLib.listLengthSignature.namespace shouldBe Some("stdlib.list")
  }

  "listFirstSignature" should "take List<Int> and return Int" in {
    StdLib.listFirstSignature.params shouldBe List(
      "list" -> SemanticType.SList(SemanticType.SInt)
    )
    StdLib.listFirstSignature.returns shouldBe SemanticType.SInt
  }

  "listLastSignature" should "take List<Int> and return Int" in {
    StdLib.listLastSignature.params shouldBe List(
      "list" -> SemanticType.SList(SemanticType.SInt)
    )
    StdLib.listLastSignature.returns shouldBe SemanticType.SInt
  }

  "listIsEmptySignature" should "return Boolean" in {
    StdLib.listIsEmptySignature.params shouldBe List(
      "list" -> SemanticType.SList(SemanticType.SInt)
    )
    StdLib.listIsEmptySignature.returns shouldBe SemanticType.SBoolean
  }

  "listSumSignature" should "take List<Int> and return Int" in {
    StdLib.listSumSignature.params shouldBe List(
      "list" -> SemanticType.SList(SemanticType.SInt)
    )
    StdLib.listSumSignature.returns shouldBe SemanticType.SInt
  }

  "listConcatSignature" should "take two List<Int> and return List<Int>" in {
    StdLib.listConcatSignature.params shouldBe List(
      "a" -> SemanticType.SList(SemanticType.SInt),
      "b" -> SemanticType.SList(SemanticType.SInt)
    )
    StdLib.listConcatSignature.returns shouldBe SemanticType.SList(SemanticType.SInt)
  }

  "listContainsSignature" should "take List<Int> and Int, return Boolean" in {
    StdLib.listContainsSignature.params shouldBe List(
      "list"  -> SemanticType.SList(SemanticType.SInt),
      "value" -> SemanticType.SInt
    )
    StdLib.listContainsSignature.returns shouldBe SemanticType.SBoolean
  }

  "listReverseSignature" should "take List<Int> and return List<Int>" in {
    StdLib.listReverseSignature.params shouldBe List(
      "list" -> SemanticType.SList(SemanticType.SInt)
    )
    StdLib.listReverseSignature.returns shouldBe SemanticType.SList(SemanticType.SInt)
  }

  // Collection tests

  "listSignatures" should "contain exactly 8 signatures" in {
    StdLib.listSignatures should have size 8
  }

  it should "contain all list function signatures" in {
    val names = StdLib.listSignatures.map(_.name)
    names should contain allOf (
      "list-length", "list-first", "list-last", "list-is-empty",
      "list-sum", "list-concat", "list-contains", "list-reverse"
    )
  }

  "listModules" should "contain exactly 8 modules" in {
    StdLib.listModules should have size 8
  }

  it should "be keyed by module spec name" in {
    StdLib.listModules.keys should contain allOf (
      "stdlib.list-length", "stdlib.list-first", "stdlib.list-last",
      "stdlib.list-is-empty", "stdlib.list-sum", "stdlib.list-concat",
      "stdlib.list-contains", "stdlib.list-reverse"
    )
  }

  it should "have modules matching their keys" in {
    StdLib.listModules.foreach { case (key, module) =>
      module.spec.name shouldBe key
    }
  }

  "all list signatures" should "have stdlib.list namespace" in {
    StdLib.listSignatures.foreach { sig =>
      sig.namespace shouldBe Some("stdlib.list")
    }
  }
}
