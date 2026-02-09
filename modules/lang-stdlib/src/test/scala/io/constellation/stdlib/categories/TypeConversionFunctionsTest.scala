package io.constellation.stdlib.categories

import io.constellation.lang.semantic.*
import io.constellation.stdlib.StdLib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TypeConversionFunctionsTest extends AnyFlatSpec with Matchers {

  // Module spec tests

  "toStringModule" should "have correct spec name" in {
    StdLib.toStringModule.spec.name shouldBe "stdlib.to-string"
  }

  it should "have correct description" in {
    StdLib.toStringModule.spec.description shouldBe "Convert integer to string"
  }

  it should "have stdlib and convert tags" in {
    StdLib.toStringModule.spec.tags should contain allOf ("stdlib", "convert")
  }

  "toIntModule" should "have correct spec name" in {
    StdLib.toIntModule.spec.name shouldBe "stdlib.to-int"
  }

  it should "have correct description" in {
    StdLib.toIntModule.spec.description shouldBe "Truncate float to integer"
  }

  it should "have stdlib and convert tags" in {
    StdLib.toIntModule.spec.tags should contain allOf ("stdlib", "convert")
  }

  "toFloatModule" should "have correct spec name" in {
    StdLib.toFloatModule.spec.name shouldBe "stdlib.to-float"
  }

  it should "have correct description" in {
    StdLib.toFloatModule.spec.description shouldBe "Convert integer to float"
  }

  it should "have stdlib and convert tags" in {
    StdLib.toFloatModule.spec.tags should contain allOf ("stdlib", "convert")
  }

  // Signature tests

  "toStringSignature" should "take Int and return String" in {
    StdLib.toStringSignature.params shouldBe List("value" -> SemanticType.SInt)
    StdLib.toStringSignature.returns shouldBe SemanticType.SString
  }

  it should "have stdlib.convert namespace" in {
    StdLib.toStringSignature.namespace shouldBe Some("stdlib.convert")
  }

  it should "have correct module name" in {
    StdLib.toStringSignature.moduleName shouldBe "stdlib.to-string"
  }

  "toIntSignature" should "take Float and return Int" in {
    StdLib.toIntSignature.params shouldBe List("value" -> SemanticType.SFloat)
    StdLib.toIntSignature.returns shouldBe SemanticType.SInt
  }

  it should "have stdlib.convert namespace" in {
    StdLib.toIntSignature.namespace shouldBe Some("stdlib.convert")
  }

  "toFloatSignature" should "take Int and return Float" in {
    StdLib.toFloatSignature.params shouldBe List("value" -> SemanticType.SInt)
    StdLib.toFloatSignature.returns shouldBe SemanticType.SFloat
  }

  it should "have stdlib.convert namespace" in {
    StdLib.toFloatSignature.namespace shouldBe Some("stdlib.convert")
  }

  // Collection tests

  "conversionSignatures" should "contain exactly 3 signatures" in {
    StdLib.conversionSignatures should have size 3
  }

  it should "contain all conversion function signatures" in {
    val names = StdLib.conversionSignatures.map(_.name)
    names should contain allOf ("to-string", "to-int", "to-float")
  }

  "conversionModules" should "contain exactly 3 modules" in {
    StdLib.conversionModules should have size 3
  }

  it should "be keyed by module spec name" in {
    StdLib.conversionModules.keys should contain allOf (
      "stdlib.to-string", "stdlib.to-int", "stdlib.to-float"
    )
  }

  it should "have modules matching their keys" in {
    StdLib.conversionModules.foreach { case (key, module) =>
      module.spec.name shouldBe key
    }
  }

  "all conversion signatures" should "have stdlib.convert namespace" in {
    StdLib.conversionSignatures.foreach { sig =>
      sig.namespace shouldBe Some("stdlib.convert")
    }
  }
}
