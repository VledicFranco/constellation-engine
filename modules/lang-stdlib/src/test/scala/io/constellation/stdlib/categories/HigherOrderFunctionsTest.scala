package io.constellation.stdlib.categories

import io.constellation.lang.semantic.*
import io.constellation.stdlib.StdLib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HigherOrderFunctionsTest extends AnyFlatSpec with Matchers {

  // Signature tests

  "filterIntSignature" should "have correct name" in {
    StdLib.filterIntSignature.name shouldBe "filter"
  }

  it should "take List<Int> and predicate function" in {
    StdLib.filterIntSignature.params shouldBe List(
      "items"     -> SemanticType.SSeq(SemanticType.SInt),
      "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    )
  }

  it should "return List<Int>" in {
    StdLib.filterIntSignature.returns shouldBe SemanticType.SSeq(SemanticType.SInt)
  }

  it should "have stdlib.collection namespace" in {
    StdLib.filterIntSignature.namespace shouldBe Some("stdlib.collection")
  }

  it should "have correct module name" in {
    StdLib.filterIntSignature.moduleName shouldBe "stdlib.hof.filter-int"
  }

  "mapIntIntSignature" should "have correct name" in {
    StdLib.mapIntIntSignature.name shouldBe "map"
  }

  it should "take List<Int> and transform function" in {
    StdLib.mapIntIntSignature.params shouldBe List(
      "items"     -> SemanticType.SSeq(SemanticType.SInt),
      "transform" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SInt)
    )
  }

  it should "return List<Int>" in {
    StdLib.mapIntIntSignature.returns shouldBe SemanticType.SSeq(SemanticType.SInt)
  }

  it should "have stdlib.collection namespace" in {
    StdLib.mapIntIntSignature.namespace shouldBe Some("stdlib.collection")
  }

  it should "have correct module name" in {
    StdLib.mapIntIntSignature.moduleName shouldBe "stdlib.hof.map-int-int"
  }

  "allIntSignature" should "have correct name" in {
    StdLib.allIntSignature.name shouldBe "all"
  }

  it should "take List<Int> and predicate function" in {
    StdLib.allIntSignature.params shouldBe List(
      "items"     -> SemanticType.SSeq(SemanticType.SInt),
      "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    )
  }

  it should "return Boolean" in {
    StdLib.allIntSignature.returns shouldBe SemanticType.SBoolean
  }

  it should "have stdlib.collection namespace" in {
    StdLib.allIntSignature.namespace shouldBe Some("stdlib.collection")
  }

  "anyIntSignature" should "have correct name" in {
    StdLib.anyIntSignature.name shouldBe "any"
  }

  it should "take List<Int> and predicate function" in {
    StdLib.anyIntSignature.params shouldBe List(
      "items"     -> SemanticType.SSeq(SemanticType.SInt),
      "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    )
  }

  it should "return Boolean" in {
    StdLib.anyIntSignature.returns shouldBe SemanticType.SBoolean
  }

  it should "have stdlib.collection namespace" in {
    StdLib.anyIntSignature.namespace shouldBe Some("stdlib.collection")
  }

  // Collection tests

  "hofSignatures" should "contain exactly 4 signatures" in {
    StdLib.hofSignatures should have size 4
  }

  it should "contain filter, map, all, any" in {
    val names = StdLib.hofSignatures.map(_.name)
    names should contain allOf ("filter", "map", "all", "any")
  }

  "hofModules" should "be empty" in {
    StdLib.hofModules shouldBe empty
  }

  "all HOF signatures" should "have stdlib.collection namespace" in {
    StdLib.hofSignatures.foreach { sig =>
      sig.namespace shouldBe Some("stdlib.collection")
    }
  }

  "all HOF signatures" should "have module names starting with stdlib.hof" in {
    StdLib.hofSignatures.foreach { sig =>
      sig.moduleName should startWith("stdlib.hof.")
    }
  }
}
