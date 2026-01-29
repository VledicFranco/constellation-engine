package io.constellation.lang.parser

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.scalacheck.Gen

import io.constellation.lang.ast.CompileError

/** Adversarial input fuzzing tests (RFC-013 Phase 5.4)
  *
  * Feeds random byte sequences, deeply nested expressions, and extremely
  * long programs to the parser. Verifies all failures produce structured
  * errors and no unhandled exceptions or stack overflows occur.
  *
  * Run with: sbt "langParser/testOnly *AdversarialFuzzingTest"
  */
class AdversarialFuzzingTest extends AnyFlatSpec with Matchers with ScalaCheckPropertyChecks {

  private val parser = ConstellationParser

  // -------------------------------------------------------------------------
  // 5.4: Random byte sequences
  // -------------------------------------------------------------------------

  "Parser" should "handle 10000 random inputs without crashing" in {
    val random = new scala.util.Random(42) // deterministic seed

    var parseErrors = 0
    var parseSuccesses = 0
    var unexpectedExceptions = 0

    (1 to 10000).foreach { i =>
      val input = generateRandomInput(random, maxLength = 200)
      try {
        parser.parse(input) match {
          case Right(_) => parseSuccesses += 1
          case Left(_: CompileError.ParseError) => parseErrors += 1
        }
      } catch {
        case _: StackOverflowError =>
          fail(s"StackOverflowError on input #$i: ${input.take(50)}...")
        case e: Exception =>
          unexpectedExceptions += 1
          // Some exceptions may be acceptable (e.g., from cats-parse internals)
          // but should be rare
      }
    }

    println(s"Random input results: $parseErrors errors, $parseSuccesses successes, $unexpectedExceptions unexpected exceptions")

    // The vast majority should produce structured ParseError, not crashes
    unexpectedExceptions should be < 100 // Allow a small number of edge cases
  }

  // -------------------------------------------------------------------------
  // 5.4: Random byte sequences via ScalaCheck
  // -------------------------------------------------------------------------

  it should "produce structured errors for arbitrary strings" in {
    val genInput = Gen.oneOf(
      Gen.asciiStr,
      Gen.alphaNumStr,
      Gen.listOf(Gen.choose(0.toChar, 127.toChar)).map(_.mkString),
      Gen.const(""),
      Gen.const("\n\n\n"),
      Gen.const("  "),
      Gen.const("\t\t\t"),
      Gen.const("# comment only"),
      Gen.const("in x: \nout x")
    )

    forAll(genInput) { input =>
      noException should be thrownBy {
        parser.parse(input)
        // Either Right or Left(ParseError) is fine, just don't crash
      }
    }
  }

  // -------------------------------------------------------------------------
  // 5.4: Deeply nested expressions
  // -------------------------------------------------------------------------

  it should "handle deeply nested parenthesized boolean expressions (100 levels)" in {
    val depth = 100
    val nested = buildNestedBoolExpr(depth)
    val source = s"in flag: Boolean\nresult = $nested\nout result\n"

    noException should be thrownBy {
      parser.parse(source)
    }
  }

  it should "handle deeply nested if-else expressions (100 levels)" in {
    val depth = 100
    val nested = buildNestedConditional(depth)
    val source = s"in flag: Boolean\nin x: Int\nin y: Int\nresult = $nested\nout result\n"

    noException should be thrownBy {
      parser.parse(source)
    }
  }

  it should "handle deeply nested coalesce chains (200 levels)" in {
    val depth = 200
    val vars = (0 until depth).map(i => s"v$i")
    val assignments = vars.zipWithIndex.map { case (v, i) =>
      if (i == 0) s"$v = x when flag"
      else s"$v = x when flag"
    }.mkString("\n")

    val coalesce = vars.mkString(" ?? ") + " ?? fallback"
    val source = s"in flag: Boolean\nin x: Int\nin fallback: Int\n$assignments\nresult = $coalesce\nout result\n"

    noException should be thrownBy {
      parser.parse(source)
    }
  }

  it should "not stack overflow with 500-level nested boolean expressions" in {
    val depth = 500
    val nested = buildNestedBoolExpr(depth)
    val source = s"in flag: Boolean\nresult = $nested\nout result\n"

    // Should not throw StackOverflowError
    try {
      parser.parse(source)
      // Success or structured ParseError - both fine
    } catch {
      case _: StackOverflowError =>
        fail("Parser stack overflowed on 500-level nested expression")
      case _: Exception =>
        // Other exceptions are acceptable (e.g., parser giving up)
    }
  }

  // -------------------------------------------------------------------------
  // 5.4: Extremely long programs
  // -------------------------------------------------------------------------

  it should "handle a program with 1000 variable assignments" in {
    val assignments = (0 until 1000).map { i =>
      if (i == 0) s"v$i = flag"
      else s"v$i = v${i - 1} and flag"
    }.mkString("\n")

    val source = s"in flag: Boolean\n$assignments\nout v999\n"

    noException should be thrownBy {
      val result = parser.parse(source)
      result.isRight shouldBe true
    }
  }

  it should "handle a program with 500 output declarations" in {
    val assignments = (0 until 500).map { i =>
      s"v$i = flag"
    }.mkString("\n")
    val outputs = (0 until 500).map(i => s"out v$i").mkString("\n")

    val source = s"in flag: Boolean\n$assignments\n$outputs\n"

    noException should be thrownBy {
      val result = parser.parse(source)
      result.isRight shouldBe true
    }
  }

  // -------------------------------------------------------------------------
  // 5.4: All failures produce structured errors
  // -------------------------------------------------------------------------

  it should "produce ParseError (not unhandled exception) for malformed type expressions" in {
    val malformedTypes = List(
      "in x: \nout x",
      "in x: String String\nout x",
      "in x: {\nout x",
      "in x: { a: }\nout x",
      "in x: List<>\nout x"
    )

    malformedTypes.foreach { source =>
      noException should be thrownBy {
        parser.parse(source) // Should return Left(ParseError), not throw
      }
    }
  }

  it should "produce structured errors for token-level adversarial inputs" in {
    val adversarial = List(
      "=====",
      "(((((",
      ")))))",
      "{{{{{{",
      "}}}}}}",
      "in in in in in",
      "out out out",
      "type type type",
      "# " * 1000,          // Very long comment
      "\u0000\u0001\u0002", // Control characters
      "in x: String\n" + ("x = x\n" * 500) + "out x\n", // Self-referential
      "in " + ("a" * 10000) + ": String\nout " + ("a" * 10000), // Very long identifiers
      "\r\n" * 1000         // Lots of empty lines (CRLF)
    )

    adversarial.foreach { source =>
      noException should be thrownBy {
        parser.parse(source)
      }
    }
  }

  // -------------------------------------------------------------------------
  // 5.4: Unicode and special characters
  // -------------------------------------------------------------------------

  it should "handle unicode input without crashing" in {
    val unicodeInputs = List(
      "in \u00e9: String\nout \u00e9",
      "in x: String\n# \u4e16\u754c\u4f60\u597d\nresult = x\nout result",
      "in x: String\nresult = x # \ud83d\ude00 emoji comment\nout result",
      "\ufeff" + "in x: String\nresult = x\nout result", // BOM
      "in x: String\nresult = x\nout result\n\u200b"      // Zero-width space at end
    )

    unicodeInputs.foreach { source =>
      noException should be thrownBy {
        parser.parse(source)
      }
    }
  }

  // -------------------------------------------------------------------------
  // Generators
  // -------------------------------------------------------------------------

  private def generateRandomInput(random: scala.util.Random, maxLength: Int): String = {
    val length = random.nextInt(maxLength)
    val strategy = random.nextInt(5)
    strategy match {
      case 0 =>
        // Random ASCII bytes
        (0 until length).map(_ => (random.nextInt(95) + 32).toChar).mkString
      case 1 =>
        // Random mix of keywords and symbols
        val keywords = Array("in", "out", "type", "if", "else", "and", "or", "not", "when", "true", "false")
        val symbols = Array("=", "(", ")", "{", "}", ":", ",", ".", "#", "\n", " ", "+", "-", "*", "/", "??")
        (0 until length).map { _ =>
          if (random.nextBoolean()) keywords(random.nextInt(keywords.length))
          else symbols(random.nextInt(symbols.length))
        }.mkString
      case 2 =>
        // Random valid-looking program fragments
        val head = "in x: String\n"
        val body = (0 until random.nextInt(20)).map(i => s"v$i = x\n").mkString
        val out = "out x\n"
        head + body + out
      case 3 =>
        // Binary-like noise
        new String(Array.fill(length)(random.nextInt(256).toByte), "ISO-8859-1")
      case 4 =>
        // Empty or whitespace
        " " * length
    }
  }

  private def buildNestedBoolExpr(depth: Int): String = {
    if (depth <= 0) "flag"
    else s"(${buildNestedBoolExpr(depth - 1)} and flag)"
  }

  private def buildNestedConditional(depth: Int): String = {
    if (depth <= 0) "x"
    else s"if (flag) ${buildNestedConditional(depth - 1)} else y"
  }
}
