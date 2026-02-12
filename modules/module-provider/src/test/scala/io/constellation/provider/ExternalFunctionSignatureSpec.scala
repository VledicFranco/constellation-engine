package io.constellation.provider

import io.constellation.CType
import io.constellation.lang.semantic.SemanticType

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExternalFunctionSignatureSpec extends AnyFlatSpec with Matchers {

  "ExternalFunctionSignature.create" should "create signature with record input" in {
    val sig = ExternalFunctionSignature.create(
      name = "analyze",
      namespace = "ml.sentiment",
      inputType = CType.CProduct(Map("text" -> CType.CString)),
      outputType = CType.CProduct(Map("score" -> CType.CFloat))
    )

    sig.name shouldBe "analyze"
    sig.namespace shouldBe Some("ml.sentiment")
    sig.moduleName shouldBe "ml.sentiment.analyze"
    sig.qualifiedName shouldBe "ml.sentiment.analyze"
    sig.params should contain(("text", SemanticType.SString))
    sig.returns shouldBe SemanticType.SRecord(Map("score" -> SemanticType.SFloat))
  }

  it should "create signature with primitive input" in {
    val sig = ExternalFunctionSignature.create(
      name = "uppercase",
      namespace = "text",
      inputType = CType.CString,
      outputType = CType.CString
    )

    sig.name shouldBe "uppercase"
    sig.namespace shouldBe Some("text")
    sig.params shouldBe List("input" -> SemanticType.SString)
    sig.returns shouldBe SemanticType.SString
  }

  it should "handle multi-field record" in {
    val sig = ExternalFunctionSignature.create(
      name = "transform",
      namespace = "data",
      inputType = CType.CProduct(
        Map(
          "name"  -> CType.CString,
          "age"   -> CType.CInt,
          "score" -> CType.CFloat
        )
      ),
      outputType = CType.CProduct(Map("result" -> CType.CBoolean))
    )

    sig.params.map(_._1).toSet shouldBe Set("name", "age", "score")
    sig.returns shouldBe SemanticType.SRecord(Map("result" -> SemanticType.SBoolean))
  }
}
