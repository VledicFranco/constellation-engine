package io.constellation.lsp

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.constellation.lsp.SemanticTokenTypes.*

class SemanticTokenProviderTest extends AnyFlatSpec with Matchers {

  private val provider = SemanticTokenProvider()

  // Helper to decode tokens for debugging
  private def decodeTokens(tokens: List[Int]): List[(Int, Int, Int, Int, Int)] =
    tokens.grouped(5).toList.map {
      case List(deltaLine, deltaStart, length, tokenType, modifiers) =>
        (deltaLine, deltaStart, length, tokenType, modifiers)
      case other =>
        fail(s"Invalid token group: $other")
    }

  // ========== Basic Token Extraction ==========

  "SemanticTokenProvider" should "return empty list for empty source" in {
    val tokens = provider.computeTokens("")
    tokens shouldBe empty
  }

  it should "return empty list for parse errors (graceful degradation)" in {
    val invalidSource = "this is not valid { syntax }"
    val tokens        = provider.computeTokens(invalidSource)
    tokens shouldBe empty
  }

  // ========== Input Declarations ==========

  it should "extract parameter token from input declaration" in {
    val source = """
      |in text: String
      |out text
    """.stripMargin.trim

    val tokens = provider.computeTokens(source)
    tokens.nonEmpty shouldBe true

    // Find parameter token (tokenType = 4 = Parameter)
    val tokenGroups = decodeTokens(tokens)
    val paramTokens = tokenGroups.filter(_._4 == TokenType.Parameter.index)
    paramTokens.nonEmpty shouldBe true
  }

  it should "extract parameter with declaration modifier" in {
    val source = """
      |in myInput: Int
      |out myInput
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)
    val paramTokens = tokenGroups.filter(_._4 == TokenType.Parameter.index)

    paramTokens.nonEmpty shouldBe true
    // Check declaration modifier is set
    paramTokens.head._5 shouldBe TokenModifier.Declaration
  }

  // ========== Variable Assignments ==========

  it should "extract variable token from assignment" in {
    val source = """
      |in text: String
      |result = Uppercase(text)
      |out result
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have variable tokens for 'result' (assignment) and 'result' (output)
    val varTokens = tokenGroups.filter(_._4 == TokenType.Variable.index)
    varTokens.nonEmpty shouldBe true
  }

  it should "extract function token from function call" in {
    val source = """
      |in text: String
      |result = Uppercase(text)
      |out result
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have function token for Uppercase
    val funcTokens = tokenGroups.filter(_._4 == TokenType.Function.index)
    funcTokens.nonEmpty shouldBe true
    // Function should have DefaultLibrary modifier
    funcTokens.head._5 shouldBe TokenModifier.DefaultLibrary
  }

  // ========== Type Definitions ==========

  it should "extract type token from type definition" in {
    val source = """
      |type MyType = { name: String }
      |in x: MyType
      |out x
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have type token for MyType
    val typeTokens = tokenGroups.filter(_._4 == TokenType.Type.index)
    typeTokens.nonEmpty shouldBe true
    // Type should have Declaration and Definition modifiers
    val expectedMods = TokenModifier.Declaration | TokenModifier.Definition
    typeTokens.head._5 shouldBe expectedMods
  }

  // ========== Use Declarations ==========

  it should "extract namespace token from use declaration" in {
    val source = """
      |use stdlib.math
      |in x: Int
      |out x
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have namespace token
    val nsTokens = tokenGroups.filter(_._4 == TokenType.Namespace.index)
    nsTokens.nonEmpty shouldBe true
  }

  it should "extract namespace tokens from use with alias" in {
    val source = """
      |use stdlib.math as m
      |in x: Int
      |out x
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have two namespace tokens: one for path, one for alias
    val nsTokens = tokenGroups.filter(_._4 == TokenType.Namespace.index)
    nsTokens.length shouldBe 2
  }

  // ========== Literals ==========

  it should "extract string token from string literal" in {
    val source = """
      |in dummy: String
      |result = Concat("hello", dummy)
      |out result
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have string token
    val strTokens = tokenGroups.filter(_._4 == TokenType.String.index)
    strTokens.nonEmpty shouldBe true
  }

  it should "extract number token from integer literal" in {
    val source = """
      |in dummy: Int
      |result = Add(dummy, 42)
      |out result
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have number token
    val numTokens = tokenGroups.filter(_._4 == TokenType.Number.index)
    numTokens.nonEmpty shouldBe true
  }

  it should "extract keyword token from boolean literal" in {
    val source = """
      |in dummy: Boolean
      |result = if (dummy) true else false
      |out result
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Verify tokens are extracted
    tokens.nonEmpty shouldBe true
    // Keyword tokens should exist for true/false
    val kwTokens = tokenGroups.filter(_._4 == TokenType.Keyword.index)
    kwTokens.length should be >= 2
  }

  // ========== Field Access ==========

  it should "extract property token from field access" in {
    val source = """
      |in user: { name: String }
      |result = user.name
      |out result
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have property token for 'name'
    val propTokens = tokenGroups.filter(_._4 == TokenType.Property.index)
    propTokens.nonEmpty shouldBe true
  }

  // ========== Lambda Expressions ==========

  it should "extract parameter tokens from lambda parameters" in {
    val source = """
      |in numbers: List<Int>
      |result = Map(numbers, (x) => Add(x, 1))
      |out result
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have parameter tokens
    val paramTokens = tokenGroups.filter(_._4 == TokenType.Parameter.index)
    // One for 'numbers' (input), one for 'x' (lambda param)
    paramTokens.length should be >= 2
  }

  // ========== Delta Encoding ==========

  it should "produce correctly delta-encoded tokens" in {
    val source = """
      |in a: Int
      |out a
    """.stripMargin.trim

    val tokens = provider.computeTokens(source)

    // Tokens come in groups of 5
    tokens.length % 5 shouldBe 0
    tokens.nonEmpty shouldBe true

    val tokenGroups = decodeTokens(tokens)

    // First token should have deltaLine = 0 (first line)
    tokenGroups.head._1 shouldBe 0

    // All lengths should be positive
    tokenGroups.foreach { t =>
      t._3 should be > 0 // length
    }
  }

  it should "handle multi-line sources" in {
    val source = """
      |in a: Int
      |in b: Int
      |out a
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have tokens on multiple lines
    // At least some tokens should have deltaLine > 0
    val hasMultipleLines = tokenGroups.exists(_._1 > 0)
    hasMultipleLines shouldBe true
  }

  // ========== Complex Expressions ==========

  it should "handle nested expressions" in {
    val source = """
      |in x: Int
      |in y: Int
      |result = Add(Multiply(x, y), Subtract(x, y))
      |out result
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have multiple function tokens
    val funcTokens = tokenGroups.filter(_._4 == TokenType.Function.index)
    funcTokens.length should be >= 3 // Add, Multiply, Subtract
  }

  it should "handle conditional expressions" in {
    val source = """
      |in x: Int
      |in y: Int
      |in flag: Boolean
      |result = if (flag) x else y
      |out result
    """.stripMargin.trim

    val tokens      = provider.computeTokens(source)
    val tokenGroups = decodeTokens(tokens)

    // Should have tokens for the conditional expression
    // Variables x and y should be captured
    val varTokens = tokenGroups.filter(_._4 == TokenType.Variable.index)
    varTokens.nonEmpty shouldBe true
  }

  // ========== Real World Examples ==========

  it should "handle a complete pipeline" in {
    val source = """
      |# Text processing pipeline
      |type Input = { text: String }
      |
      |in input: Input
      |
      |cleaned = Trim(input.text)
      |upper = Uppercase(cleaned)
      |
      |out upper
    """.stripMargin.trim

    val tokens = provider.computeTokens(source)

    tokens.nonEmpty shouldBe true
    // Verify token count is valid (multiple of 5)
    tokens.length % 5 shouldBe 0

    val tokenGroups = decodeTokens(tokens)

    // Should have type, parameter, variable, function, property tokens
    tokenGroups.exists(_._4 == TokenType.Type.index) shouldBe true
    tokenGroups.exists(_._4 == TokenType.Parameter.index) shouldBe true
    tokenGroups.exists(_._4 == TokenType.Variable.index) shouldBe true
    tokenGroups.exists(_._4 == TokenType.Function.index) shouldBe true
    tokenGroups.exists(_._4 == TokenType.Property.index) shouldBe true
  }

  // ========== Token Count Verification ==========

  it should "produce exactly 5 integers per token" in {
    val source = """
      |in x: Int
      |y = Add(x, 1)
      |out y
    """.stripMargin.trim

    val tokens = provider.computeTokens(source)
    tokens.length % 5 shouldBe 0
    tokens.length should be > 0
  }
}
