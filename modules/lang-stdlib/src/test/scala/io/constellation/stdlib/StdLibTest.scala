package io.constellation.stdlib

import io.constellation.lang.semantic._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class StdLibTest extends AnyFlatSpec with Matchers {

  "StdLib.compiler" should "compile programs with math functions" in {
    val compiler = StdLib.compiler

    val source = """
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """

    val result = compiler.compile(source, "math-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules should not be empty
    // Module spec should reference stdlib.add
    compiled.dagSpec.modules.values.exists(_.name.contains("add")) shouldBe true
  }

  it should "compile programs with string functions" in {
    val compiler = StdLib.compiler

    val source = """
      in text: String
      result = upper(text)
      out result
    """

    val result = compiler.compile(source, "string-dag")
    result.isRight shouldBe true
  }

  it should "compile programs with boolean functions" in {
    val compiler = StdLib.compiler

    val source = """
      in a: Boolean
      in b: Boolean
      result = and(a, b)
      out result
    """

    val result = compiler.compile(source, "bool-dag")
    result.isRight shouldBe true
  }

  it should "compile programs with comparison functions" in {
    val compiler = StdLib.compiler

    val source = """
      in a: Int
      in b: Int
      result = gt(a, b)
      out result
    """

    val result = compiler.compile(source, "compare-dag")
    result.isRight shouldBe true
    result.toOption.get.dagSpec.modules should not be empty
  }

  it should "compile programs with list functions" in {
    val compiler = StdLib.compiler

    val source = """
      in nums: List<Int>
      result = list-length(nums)
      out result
    """

    val result = compiler.compile(source, "list-dag")
    result.isRight shouldBe true
  }

  it should "compile complex programs with multiple stdlib functions" in {
    val compiler = StdLib.compiler

    val source = """
      in a: Int
      in b: Int
      sum = add(a, b)
      isGreater = gt(sum, b)
      out isGreater
    """

    val result = compiler.compile(source, "complex-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have modules for add and gt
    compiled.dagSpec.modules.size shouldBe 2
  }

  it should "compile programs with conditionals using comparison" in {
    val compiler = StdLib.compiler

    val source = """
      in a: Int
      in b: Int
      isGreater = gt(a, b)
      result = if (isGreater) a else b
      out result
    """

    val result = compiler.compile(source, "conditional-dag")
    result.isRight shouldBe true
  }

  "StdLib.registerAll" should "register all standard functions" in {
    val builder = StdLib.registerAll(
      io.constellation.lang.LangCompilerBuilder()
    )
    val compiler = builder.build

    // Should be able to use all functions
    val functions = List(
      "add", "subtract", "multiply", "divide", "max", "min",
      "concat", "upper", "lower", "string-length",
      "and", "or", "not",
      "gt", "lt", "gte", "lte", "eq-int", "eq-string",
      "list-length", "list-first", "list-last", "list-is-empty",
      "log", "identity"
    )

    // Verify each function can be used in a minimal program
    functions.foreach { funcName =>
      val testSource = funcName match {
        case "add" | "subtract" | "multiply" | "divide" | "max" | "min" =>
          s"in a: Int\nin b: Int\nresult = $funcName(a, b)\nout result"
        case "concat" =>
          s"in a: String\nin b: String\nresult = $funcName(a, b)\nout result"
        case "upper" | "lower" | "string-length" | "identity" | "log" =>
          s"in s: String\nresult = $funcName(s)\nout result"
        case "and" | "or" =>
          s"in a: Boolean\nin b: Boolean\nresult = $funcName(a, b)\nout result"
        case "not" =>
          s"in b: Boolean\nresult = $funcName(b)\nout result"
        case "gt" | "lt" | "gte" | "lte" | "eq-int" =>
          s"in a: Int\nin b: Int\nresult = $funcName(a, b)\nout result"
        case "eq-string" =>
          s"in a: String\nin b: String\nresult = $funcName(a, b)\nout result"
        case "list-length" | "list-first" | "list-last" | "list-is-empty" =>
          s"in nums: List<Int>\nresult = $funcName(nums)\nout result"
        case _ =>
          s"in x: Int\nout x"
      }

      val result = compiler.compile(testSource, s"$funcName-test")
      result.isRight shouldBe true
    }
  }

  "Math signatures" should "have correct types" in {
    MathOps.addSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    MathOps.addSignature.returns shouldBe SemanticType.SInt
  }

  "String signatures" should "have correct types" in {
    StringOps.concatSignature.params shouldBe List(
      "a" -> SemanticType.SString,
      "b" -> SemanticType.SString
    )
    StringOps.concatSignature.returns shouldBe SemanticType.SString

    StringOps.upperSignature.returns shouldBe SemanticType.SString
  }

  "Boolean signatures" should "have correct types" in {
    BoolOps.andSignature.returns shouldBe SemanticType.SBoolean
    BoolOps.notSignature.params shouldBe List("value" -> SemanticType.SBoolean)
  }

  "Comparison signatures" should "return Boolean" in {
    CompareOps.gtSignature.returns shouldBe SemanticType.SBoolean
    CompareOps.ltSignature.returns shouldBe SemanticType.SBoolean
    CompareOps.eqIntSignature.returns shouldBe SemanticType.SBoolean
  }

  "StdLib.allModules" should "contain all modules" in {
    val modules = StdLib.allModules

    modules.keys should contain ("stdlib.add")
    modules.keys should contain ("stdlib.subtract")
    modules.keys should contain ("stdlib.concat")
    modules.keys should contain ("stdlib.upper")
    modules.keys should contain ("stdlib.and")
    modules.keys should contain ("stdlib.gt")
    modules.keys should contain ("stdlib.list-length")
    modules.keys should contain ("stdlib.log")
  }
}
