package io.constellation.lang.compiler

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SuggestionsTest extends AnyFlatSpec with Matchers {

  // ========== Levenshtein Distance Tests ==========

  "Suggestions.levenshteinDistance" should "return 0 for identical strings" in {
    Suggestions.levenshteinDistance("cat", "cat") shouldBe 0
    Suggestions.levenshteinDistance("hello", "hello") shouldBe 0
    Suggestions.levenshteinDistance("", "") shouldBe 0
  }

  it should "return correct distance for single character changes" in {
    Suggestions.levenshteinDistance("cat", "bat") shouldBe 1  // substitution
    Suggestions.levenshteinDistance("cat", "cart") shouldBe 1 // insertion
    Suggestions.levenshteinDistance("cart", "cat") shouldBe 1 // deletion
    Suggestions.levenshteinDistance("cat", "ca") shouldBe 1   // deletion
  }

  it should "return correct distance for multiple changes" in {
    Suggestions.levenshteinDistance("cat", "dog") shouldBe 3
    Suggestions.levenshteinDistance("kitten", "sitting") shouldBe 3
    Suggestions.levenshteinDistance("abc", "xyz") shouldBe 3
  }

  it should "handle empty strings" in {
    Suggestions.levenshteinDistance("", "abc") shouldBe 3
    Suggestions.levenshteinDistance("abc", "") shouldBe 3
  }

  it should "be case-sensitive" in {
    Suggestions.levenshteinDistance("Cat", "cat") shouldBe 1
    Suggestions.levenshteinDistance("ABC", "abc") shouldBe 3
  }

  // ========== findSimilar Tests ==========

  "Suggestions.findSimilar" should "find similar strings within max distance" in {
    val candidates = List("text", "test", "next", "context", "unrelated")

    Suggestions.findSimilar("textt", candidates) should contain("text")
    Suggestions.findSimilar("txt", candidates) should contain("text")
  }

  it should "return empty for no similar matches" in {
    val candidates = List("text", "test", "next")

    Suggestions.findSimilar("xyz", candidates) shouldBe empty
    Suggestions.findSimilar("completely_different", candidates) shouldBe empty
  }

  it should "perform case-insensitive similarity matching" in {
    // Case-insensitive: "Uppercas" is similar to "Uppercase" (distance 1)
    val candidates = List("Uppercase", "Lowercase", "MixedCase")

    // Typo "Uppercas" should suggest "Uppercase"
    Suggestions.findSimilar("Uppercas", candidates) should contain("Uppercase")

    // Typo with different case should still find match
    Suggestions.findSimilar("uppercas", candidates) should contain("Uppercase")
    Suggestions.findSimilar("LOWERCAS", candidates) should contain("Lowercase")
  }

  it should "respect maxSuggestions limit" in {
    val candidates = List("text1", "text2", "text3", "text4", "text5")

    Suggestions.findSimilar("text", candidates, maxSuggestions = 2).length should be <= 2
    Suggestions.findSimilar("text", candidates, maxSuggestions = 1).length should be <= 1
  }

  it should "respect maxDistance parameter" in {
    val candidates = List("abc", "abcd", "abcde", "abcdef")

    // With maxDistance = 1, only "abc" (exact match excluded) and "abcd" should match
    val result1 = Suggestions.findSimilar("abc", candidates, maxDistance = 1)
    result1 should contain("abcd")
    result1 should not contain "abcdef"

    // With maxDistance = 3, more should match
    val result3 = Suggestions.findSimilar("abc", candidates, maxDistance = 3)
    result3 should contain("abcdef")
  }

  it should "exclude exact matches (case-insensitive)" in {
    val candidates = List("text", "Text", "TEXT", "test", "next")

    // Should not contain any case variation of "text"
    val result = Suggestions.findSimilar("text", candidates)
    result should not contain "text"
    result should not contain "Text"
    result should not contain "TEXT"
  }

  it should "sort by similarity (closest first)" in {
    val candidates = List("abcdef", "abcd", "abcde", "abc")

    val result = Suggestions.findSimilar("abcx", candidates, maxDistance = 3)
    // "abcd" should come before "abcdef" because it's closer
    if result.contains("abcd") && result.contains("abcdef") then {
      result.indexOf("abcd") should be < result.indexOf("abcdef")
    }
  }

  // ========== forError Tests ==========

  "Suggestions.forError" should "generate suggestions for UndefinedVariable" in {
    val error = io.constellation.lang.ast.CompileError.UndefinedVariable("textt", None)
    val context = SuggestionContext(
      definedVariables = List("text", "count", "result")
    )

    val suggestions = Suggestions.forError(error, context)
    suggestions should not be empty
    suggestions.exists(_.contains("text")) shouldBe true
  }

  it should "generate suggestions for UndefinedFunction" in {
    val error = io.constellation.lang.ast.CompileError.UndefinedFunction("Uppercas", None)
    val context = SuggestionContext(
      availableFunctions = List("Uppercase", "Lowercase", "Trim")
    )

    val suggestions = Suggestions.forError(error, context)
    suggestions should not be empty
    suggestions.exists(_.contains("Uppercase")) shouldBe true
  }

  it should "generate suggestions for InvalidFieldAccess" in {
    val error = io.constellation.lang.ast.CompileError.InvalidFieldAccess(
      "nam",
      List("name", "age", "email"),
      None
    )
    val context = SuggestionContext.empty

    val suggestions = Suggestions.forError(error, context)
    suggestions should not be empty
    suggestions.exists(_.contains("name")) shouldBe true
  }

  it should "generate suggestions for InvalidProjection" in {
    val error = io.constellation.lang.ast.CompileError.InvalidProjection(
      "emial",
      List("name", "email", "phone"),
      None
    )
    val context = SuggestionContext.empty

    val suggestions = Suggestions.forError(error, context)
    suggestions should not be empty
    suggestions.exists(_.contains("email")) shouldBe true
  }

  it should "return empty for errors without applicable suggestions" in {
    val error   = io.constellation.lang.ast.CompileError.InternalError("Something went wrong")
    val context = SuggestionContext.empty

    val suggestions = Suggestions.forError(error, context)
    suggestions shouldBe empty
  }

  // ========== SuggestionContext Tests ==========

  "SuggestionContext.empty" should "have empty lists" in {
    val ctx = SuggestionContext.empty
    ctx.definedVariables shouldBe empty
    ctx.definedTypes shouldBe empty
    ctx.availableFunctions shouldBe empty
    ctx.availableNamespaces shouldBe empty
    ctx.functionsByNamespace shouldBe empty
  }
}
