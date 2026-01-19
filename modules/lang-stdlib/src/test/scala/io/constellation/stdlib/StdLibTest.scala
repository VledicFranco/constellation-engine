package io.constellation.stdlib

import io.constellation.lang.semantic.*
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

  it should "compile programs with boolean operators" in {
    val compiler = StdLib.compiler

    // Note: and, or, not are now built-in operators, not stdlib functions
    val source = """
      in a: Boolean
      in b: Boolean
      result = a and b
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
    // Note: and, or, not are now built-in operators (keywords), not stdlib functions
    val functions = List(
      "add",
      "subtract",
      "multiply",
      "divide",
      "max",
      "min",
      "concat",
      "upper",
      "lower",
      "string-length",
      "gt",
      "lt",
      "gte",
      "lte",
      "eq-int",
      "eq-string",
      "list-length",
      "list-first",
      "list-last",
      "list-is-empty",
      "log",
      "identity"
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
    StdLib.addSignature.params shouldBe List(
      "a" -> SemanticType.SInt,
      "b" -> SemanticType.SInt
    )
    StdLib.addSignature.returns shouldBe SemanticType.SInt
  }

  "String signatures" should "have correct types" in {
    StdLib.concatSignature.params shouldBe List(
      "a" -> SemanticType.SString,
      "b" -> SemanticType.SString
    )
    StdLib.concatSignature.returns shouldBe SemanticType.SString

    StdLib.upperSignature.returns shouldBe SemanticType.SString
  }

  "Boolean signatures" should "have correct types" in {
    StdLib.andSignature.returns shouldBe SemanticType.SBoolean
    StdLib.notSignature.params shouldBe List("value" -> SemanticType.SBoolean)
  }

  "Comparison signatures" should "return Boolean" in {
    StdLib.gtSignature.returns shouldBe SemanticType.SBoolean
    StdLib.ltSignature.returns shouldBe SemanticType.SBoolean
    StdLib.eqIntSignature.returns shouldBe SemanticType.SBoolean
  }

  "StdLib.allModules" should "contain all modules" in {
    val modules = StdLib.allModules

    modules.keys should contain("stdlib.add")
    modules.keys should contain("stdlib.subtract")
    modules.keys should contain("stdlib.concat")
    modules.keys should contain("stdlib.upper")
    modules.keys should contain("stdlib.and")
    modules.keys should contain("stdlib.gt")
    modules.keys should contain("stdlib.list-length")
    modules.keys should contain("stdlib.log")
  }

  // Namespace tests

  "StdLib namespaces" should "compile programs with fully qualified names" in {
    val compiler = StdLib.compiler

    val source = """
      in a: Int
      in b: Int
      result = stdlib.math.add(a, b)
      out result
    """

    val result = compiler.compile(source, "fqn-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    compiled.dagSpec.modules should not be empty
  }

  it should "compile programs with use declarations" in {
    val compiler = StdLib.compiler

    val source = """
      use stdlib.math
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """

    val result = compiler.compile(source, "use-dag")
    result.isRight shouldBe true
  }

  it should "compile programs with aliased imports" in {
    val compiler = StdLib.compiler

    val source = """
      use stdlib.math as m
      in a: Int
      in b: Int
      result = m.add(a, b)
      out result
    """

    val result = compiler.compile(source, "alias-dag")
    result.isRight shouldBe true
  }

  it should "compile programs with multiple namespace imports" in {
    val compiler = StdLib.compiler

    val source = """
      use stdlib.math
      use stdlib.string as str
      in a: Int
      in b: Int
      in greeting: String
      sum = add(a, b)
      upper_greeting = str.upper(greeting)
      out sum
    """

    val result = compiler.compile(source, "multi-ns-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have 2 modules: add and upper
    compiled.dagSpec.modules should have size 2
  }

  it should "include synthetic modules for execution" in {
    val compiler = StdLib.compiler

    val source = """
      use stdlib.math
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """

    val result = compiler.compile(source, "synth-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // syntheticModules should contain the add module
    compiled.syntheticModules should not be empty
  }

  it should "expose function registry with namespaces" in {
    val compiler = StdLib.compiler
    val registry = compiler.functionRegistry

    // Should have all stdlib namespaces
    registry.namespaces should contain("stdlib.math")
    registry.namespaces should contain("stdlib.string")
    registry.namespaces should contain("stdlib.bool")
    registry.namespaces should contain("stdlib.compare")
    registry.namespaces should contain("stdlib.list")

    // Should be able to lookup by qualified name
    registry.lookupQualified("stdlib.math.add").isDefined shouldBe true
    registry.lookupQualified("stdlib.string.upper").isDefined shouldBe true
  }

  it should "have signatures with correct namespace attributes" in {
    // Math namespace
    StdLib.addSignature.namespace shouldBe Some("stdlib.math")
    StdLib.multiplySignature.namespace shouldBe Some("stdlib.math")

    // String namespace
    StdLib.upperSignature.namespace shouldBe Some("stdlib.string")
    StdLib.concatSignature.namespace shouldBe Some("stdlib.string")

    // Bool namespace
    StdLib.andSignature.namespace shouldBe Some("stdlib.bool")

    // Compare namespace
    StdLib.gtSignature.namespace shouldBe Some("stdlib.compare")
  }
}
