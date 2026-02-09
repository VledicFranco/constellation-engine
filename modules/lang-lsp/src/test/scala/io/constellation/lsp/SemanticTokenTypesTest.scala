package io.constellation.lsp

import io.constellation.lsp.SemanticTokenTypes.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SemanticTokenTypesTest extends AnyFlatSpec with Matchers {

  // ========== TokenType Enum Indices ==========

  "TokenType enum" should "have 11 entries" in {
    TokenType.values.length shouldBe 11
  }

  it should "have unique indices" in {
    val indices = TokenType.values.map(_.index)
    indices.distinct.length shouldBe indices.length
  }

  it should "have sequential indices from 0 to 10" in {
    val indices = TokenType.values.map(_.index).sorted
    indices shouldBe (0 to 10).toArray
  }

  it should "assign Namespace index 0" in {
    TokenType.Namespace.index shouldBe 0
  }

  it should "assign Type index 1" in {
    TokenType.Type.index shouldBe 1
  }

  it should "assign Function index 2" in {
    TokenType.Function.index shouldBe 2
  }

  it should "assign Variable index 3" in {
    TokenType.Variable.index shouldBe 3
  }

  it should "assign Parameter index 4" in {
    TokenType.Parameter.index shouldBe 4
  }

  it should "assign Property index 5" in {
    TokenType.Property.index shouldBe 5
  }

  it should "assign Keyword index 6" in {
    TokenType.Keyword.index shouldBe 6
  }

  it should "assign String index 7" in {
    TokenType.String.index shouldBe 7
  }

  it should "assign Number index 8" in {
    TokenType.Number.index shouldBe 8
  }

  it should "assign Operator index 9" in {
    TokenType.Operator.index shouldBe 9
  }

  it should "assign Comment index 10" in {
    TokenType.Comment.index shouldBe 10
  }

  // ========== TokenModifier Bitmask Values ==========

  "TokenModifier" should "have Declaration equal to 1" in {
    TokenModifier.Declaration shouldBe 1
  }

  it should "have Definition equal to 2" in {
    TokenModifier.Definition shouldBe 2
  }

  it should "have Readonly equal to 4" in {
    TokenModifier.Readonly shouldBe 4
  }

  it should "have DefaultLibrary equal to 8" in {
    TokenModifier.DefaultLibrary shouldBe 8
  }

  it should "have None equal to 0" in {
    TokenModifier.None shouldBe 0
  }

  // ========== TokenModifier Bitwise OR Combinations ==========

  it should "combine Declaration | Definition to 3" in {
    (TokenModifier.Declaration | TokenModifier.Definition) shouldBe 3
  }

  it should "combine Declaration | Readonly to 5" in {
    (TokenModifier.Declaration | TokenModifier.Readonly) shouldBe 5
  }

  it should "combine Declaration | Definition | Readonly to 7" in {
    (TokenModifier.Declaration | TokenModifier.Definition | TokenModifier.Readonly) shouldBe 7
  }

  it should "combine all modifiers to 15" in {
    val all = TokenModifier.Declaration | TokenModifier.Definition |
      TokenModifier.Readonly | TokenModifier.DefaultLibrary
    all shouldBe 15
  }

  it should "leave value unchanged when OR-ed with None" in {
    (TokenModifier.Declaration | TokenModifier.None) shouldBe TokenModifier.Declaration
    (TokenModifier.Definition | TokenModifier.None) shouldBe TokenModifier.Definition
  }

  // ========== tokenTypes List ==========

  "tokenTypes list" should "have 11 entries" in {
    tokenTypes.length shouldBe 11
  }

  it should "match enum order so that tokenTypes(i) corresponds to TokenType with index i" in {
    TokenType.values.foreach { tt =>
      val expectedName = tt.toString.toLowerCase
      tokenTypes(tt.index) shouldBe expectedName
    }
  }

  it should "contain the expected string values in order" in {
    tokenTypes shouldBe List(
      "namespace", "type", "function", "variable", "parameter",
      "property", "keyword", "string", "number", "operator", "comment"
    )
  }

  // ========== tokenModifiers List ==========

  "tokenModifiers list" should "have 4 entries" in {
    tokenModifiers.length shouldBe 4
  }

  it should "contain the expected string values in order" in {
    tokenModifiers shouldBe List(
      "declaration", "definition", "readonly", "defaultLibrary"
    )
  }
}
