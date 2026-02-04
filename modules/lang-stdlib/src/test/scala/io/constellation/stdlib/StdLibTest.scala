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
    compiled.pipeline.image.dagSpec.modules should not be empty
    // Module spec should reference stdlib.add
    compiled.pipeline.image.dagSpec.modules.values.exists(_.name.contains("add")) shouldBe true
  }

  it should "compile programs with string functions" in {
    val compiler = StdLib.compiler

    val source = """
      in a: String
      in b: String
      result = concat(a, b)
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
    result.toOption.get.pipeline.image.dagSpec.modules should not be empty
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
    compiled.pipeline.image.dagSpec.modules.size shouldBe 2
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
      "abs",
      "modulo",
      "negate",
      "concat",
      "string-length",
      "trim",
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
      "list-sum",
      "log",
      "identity",
      "to-string",
      "to-int"
    )

    // Verify each function can be used in a minimal program
    functions.foreach { funcName =>
      val testSource = funcName match {
        case "add" | "subtract" | "multiply" | "divide" | "max" | "min" | "modulo" =>
          s"in a: Int\nin b: Int\nresult = $funcName(a, b)\nout result"
        case "concat" =>
          s"in a: String\nin b: String\nresult = $funcName(a, b)\nout result"
        case "string-length" | "identity" | "log" | "trim" =>
          s"in s: String\nresult = $funcName(s)\nout result"
        case "abs" | "negate" | "to-string" =>
          s"in x: Int\nresult = $funcName(x)\nout result"
        case "gt" | "lt" | "gte" | "lte" | "eq-int" =>
          s"in a: Int\nin b: Int\nresult = $funcName(a, b)\nout result"
        case "eq-string" =>
          s"in a: String\nin b: String\nresult = $funcName(a, b)\nout result"
        case "list-length" | "list-first" | "list-last" | "list-is-empty" | "list-sum" =>
          s"in nums: List<Int>\nresult = $funcName(nums)\nout result"
        case "to-int" =>
          s"in x: Float\nresult = $funcName(x)\nout result"
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

  it should "have correct types for new math functions" in {
    StdLib.absSignature.params shouldBe List("value" -> SemanticType.SInt)
    StdLib.absSignature.returns shouldBe SemanticType.SInt

    StdLib.moduloSignature.params shouldBe List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt)
    StdLib.moduloSignature.returns shouldBe SemanticType.SInt

    StdLib.roundSignature.params shouldBe List("value" -> SemanticType.SFloat)
    StdLib.roundSignature.returns shouldBe SemanticType.SInt

    StdLib.negateSignature.params shouldBe List("value" -> SemanticType.SInt)
    StdLib.negateSignature.returns shouldBe SemanticType.SInt
  }

  "String signatures" should "have correct types" in {
    StdLib.concatSignature.params shouldBe List(
      "a" -> SemanticType.SString,
      "b" -> SemanticType.SString
    )
    StdLib.concatSignature.returns shouldBe SemanticType.SString
  }

  it should "have correct types for new string functions" in {
    StdLib.joinSignature.params shouldBe List(
      "list"      -> SemanticType.SList(SemanticType.SString),
      "separator" -> SemanticType.SString
    )
    StdLib.joinSignature.returns shouldBe SemanticType.SString

    StdLib.splitSignature.returns shouldBe SemanticType.SList(SemanticType.SString)

    StdLib.containsSignature.returns shouldBe SemanticType.SBoolean

    StdLib.trimSignature.params shouldBe List("value" -> SemanticType.SString)
    StdLib.trimSignature.returns shouldBe SemanticType.SString

    StdLib.replaceSignature.params shouldBe List(
      "value"       -> SemanticType.SString,
      "target"      -> SemanticType.SString,
      "replacement" -> SemanticType.SString
    )
    StdLib.replaceSignature.returns shouldBe SemanticType.SString
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

  "List signatures" should "have correct types for new list functions" in {
    StdLib.listSumSignature.params shouldBe List("list" -> SemanticType.SList(SemanticType.SInt))
    StdLib.listSumSignature.returns shouldBe SemanticType.SInt

    StdLib.listConcatSignature.params shouldBe List(
      "a" -> SemanticType.SList(SemanticType.SInt),
      "b" -> SemanticType.SList(SemanticType.SInt)
    )
    StdLib.listConcatSignature.returns shouldBe SemanticType.SList(SemanticType.SInt)

    StdLib.listContainsSignature.params shouldBe List(
      "list"  -> SemanticType.SList(SemanticType.SInt),
      "value" -> SemanticType.SInt
    )
    StdLib.listContainsSignature.returns shouldBe SemanticType.SBoolean

    StdLib.listReverseSignature.params shouldBe List(
      "list" -> SemanticType.SList(SemanticType.SInt)
    )
    StdLib.listReverseSignature.returns shouldBe SemanticType.SList(SemanticType.SInt)
  }

  "TypeConversion signatures" should "have correct types" in {
    StdLib.toStringSignature.params shouldBe List("value" -> SemanticType.SInt)
    StdLib.toStringSignature.returns shouldBe SemanticType.SString

    StdLib.toIntSignature.params shouldBe List("value" -> SemanticType.SFloat)
    StdLib.toIntSignature.returns shouldBe SemanticType.SInt

    StdLib.toFloatSignature.params shouldBe List("value" -> SemanticType.SInt)
    StdLib.toFloatSignature.returns shouldBe SemanticType.SFloat
  }

  "StdLib.allModules" should "contain all modules" in {
    val modules = StdLib.allModules

    // Math
    modules.keys should contain("stdlib.add")
    modules.keys should contain("stdlib.subtract")
    modules.keys should contain("stdlib.abs")
    modules.keys should contain("stdlib.modulo")
    modules.keys should contain("stdlib.round")
    modules.keys should contain("stdlib.negate")

    // String
    modules.keys should contain("stdlib.concat")
    modules.keys should contain("stdlib.join")
    modules.keys should contain("stdlib.split")
    modules.keys should contain("stdlib.contains")
    modules.keys should contain("stdlib.trim")
    modules.keys should contain("stdlib.replace")

    // Boolean
    modules.keys should contain("stdlib.and")

    // Comparison
    modules.keys should contain("stdlib.gt")

    // List
    modules.keys should contain("stdlib.list-length")
    modules.keys should contain("stdlib.list-sum")
    modules.keys should contain("stdlib.list-concat")
    modules.keys should contain("stdlib.list-contains")
    modules.keys should contain("stdlib.list-reverse")

    // Utility
    modules.keys should contain("stdlib.log")

    // Conversion
    modules.keys should contain("stdlib.to-string")
    modules.keys should contain("stdlib.to-int")
    modules.keys should contain("stdlib.to-float")

    // Removed functions should not be present
    modules.keys should not contain "stdlib.upper"
    modules.keys should not contain "stdlib.lower"
    modules.keys should not contain "stdlib.const-int"
    modules.keys should not contain "stdlib.const-float"
    modules.keys should not contain "stdlib.const-string"
    modules.keys should not contain "stdlib.const-bool"
    modules.keys should not contain "stdlib.record.get-name"
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
    compiled.pipeline.image.dagSpec.modules should not be empty
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
      trimmed_greeting = str.trim(greeting)
      out sum
    """

    val result = compiler.compile(source, "multi-ns-dag")
    result.isRight shouldBe true

    val compiled = result.toOption.get
    // Should have 2 modules: add and trim
    compiled.pipeline.image.dagSpec.modules should have size 2
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
    compiled.pipeline.syntheticModules should not be empty
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
    registry.namespaces should contain("stdlib.convert")

    // Should be able to lookup by qualified name
    registry.lookupQualified("stdlib.math.add").isDefined shouldBe true
    registry.lookupQualified("stdlib.string.concat").isDefined shouldBe true
    registry.lookupQualified("stdlib.convert.to-string").isDefined shouldBe true
  }

  it should "have signatures with correct namespace attributes" in {
    // Math namespace
    StdLib.addSignature.namespace shouldBe Some("stdlib.math")
    StdLib.multiplySignature.namespace shouldBe Some("stdlib.math")
    StdLib.absSignature.namespace shouldBe Some("stdlib.math")

    // String namespace
    StdLib.concatSignature.namespace shouldBe Some("stdlib.string")
    StdLib.trimSignature.namespace shouldBe Some("stdlib.string")

    // Bool namespace
    StdLib.andSignature.namespace shouldBe Some("stdlib.bool")

    // Compare namespace
    StdLib.gtSignature.namespace shouldBe Some("stdlib.compare")

    // Convert namespace
    StdLib.toStringSignature.namespace shouldBe Some("stdlib.convert")
  }

  // Higher-order function tests

  "HOF signatures" should "have correct types for filter" in {
    StdLib.filterIntSignature.params shouldBe List(
      "items"     -> SemanticType.SList(SemanticType.SInt),
      "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    )
    StdLib.filterIntSignature.returns shouldBe SemanticType.SList(SemanticType.SInt)
    StdLib.filterIntSignature.namespace shouldBe Some("stdlib.collection")
  }

  it should "have correct types for map" in {
    StdLib.mapIntIntSignature.params shouldBe List(
      "items"     -> SemanticType.SList(SemanticType.SInt),
      "transform" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SInt)
    )
    StdLib.mapIntIntSignature.returns shouldBe SemanticType.SList(SemanticType.SInt)
    StdLib.mapIntIntSignature.namespace shouldBe Some("stdlib.collection")
  }

  it should "have correct types for all" in {
    StdLib.allIntSignature.params shouldBe List(
      "items"     -> SemanticType.SList(SemanticType.SInt),
      "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    )
    StdLib.allIntSignature.returns shouldBe SemanticType.SBoolean
    StdLib.allIntSignature.namespace shouldBe Some("stdlib.collection")
  }

  it should "have correct types for any" in {
    StdLib.anyIntSignature.params shouldBe List(
      "items"     -> SemanticType.SList(SemanticType.SInt),
      "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
    )
    StdLib.anyIntSignature.returns shouldBe SemanticType.SBoolean
    StdLib.anyIntSignature.namespace shouldBe Some("stdlib.collection")
  }

  "StdLib.compiler" should "compile programs with filter and lambda" in {
    val compiler = StdLib.compiler

    // Lambda bodies need stdlib.compare for comparison operators
    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = filter(numbers, (x) => gt(x, 0))
      out result
    """

    val result = compiler.compile(source, "filter-dag")
    result match {
      case Left(errors) => fail(s"Compilation failed: ${errors.map(_.message).mkString(", ")}")
      case Right(_)     => succeed
    }
  }

  it should "compile programs with map and lambda" in {
    val compiler = StdLib.compiler

    // Lambda bodies need stdlib.math for arithmetic operators
    val source = """
      use stdlib.collection
      use stdlib.math
      in numbers: List<Int>
      result = map(numbers, (x) => multiply(x, 2))
      out result
    """

    val result = compiler.compile(source, "map-dag")
    result match {
      case Left(errors) => fail(s"Compilation failed: ${errors.map(_.message).mkString(", ")}")
      case Right(_)     => succeed
    }
  }

  it should "compile programs with all and lambda" in {
    val compiler = StdLib.compiler

    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = all(numbers, (x) => gt(x, 0))
      out result
    """

    val result = compiler.compile(source, "all-dag")
    result match {
      case Left(errors) => fail(s"Compilation failed: ${errors.map(_.message).mkString(", ")}")
      case Right(_)     => succeed
    }
  }

  it should "compile programs with any and lambda" in {
    val compiler = StdLib.compiler

    val source = """
      use stdlib.collection
      use stdlib.compare
      in numbers: List<Int>
      result = any(numbers, (x) => lt(x, 0))
      out result
    """

    val result = compiler.compile(source, "any-dag")
    result match {
      case Left(errors) => fail(s"Compilation failed: ${errors.map(_.message).mkString(", ")}")
      case Right(_)     => succeed
    }
  }

  it should "expose collection namespace in registry" in {
    val compiler = StdLib.compiler
    val registry = compiler.functionRegistry

    registry.namespaces should contain("stdlib.collection")
    registry.lookupQualified("stdlib.collection.filter").isDefined shouldBe true
    registry.lookupQualified("stdlib.collection.map").isDefined shouldBe true
    registry.lookupQualified("stdlib.collection.all").isDefined shouldBe true
    registry.lookupQualified("stdlib.collection.any").isDefined shouldBe true
  }

  // New function compilation tests

  "StdLib.compiler" should "compile programs with new math functions" in {
    val compiler = StdLib.compiler

    val absSource = """
      in x: Int
      result = abs(x)
      out result
    """
    compiler.compile(absSource, "abs-dag").isRight shouldBe true

    val moduloSource = """
      in a: Int
      in b: Int
      result = modulo(a, b)
      out result
    """
    compiler.compile(moduloSource, "modulo-dag").isRight shouldBe true

    val negateSource = """
      in x: Int
      result = negate(x)
      out result
    """
    compiler.compile(negateSource, "negate-dag").isRight shouldBe true

    val roundSource = """
      in x: Float
      result = round(x)
      out result
    """
    compiler.compile(roundSource, "round-dag").isRight shouldBe true
  }

  it should "compile programs with new string functions" in {
    val compiler = StdLib.compiler

    val trimSource = """
      in text: String
      result = trim(text)
      out result
    """
    compiler.compile(trimSource, "trim-dag").isRight shouldBe true

    val containsSource = """
      in text: String
      in sub: String
      result = contains(text, sub)
      out result
    """
    compiler.compile(containsSource, "contains-dag").isRight shouldBe true

    val replaceSource = """
      in text: String
      in target: String
      in replacement: String
      result = replace(text, target, replacement)
      out result
    """
    compiler.compile(replaceSource, "replace-dag").isRight shouldBe true
  }

  it should "compile programs with new list functions" in {
    val compiler = StdLib.compiler

    val sumSource = """
      in nums: List<Int>
      result = list-sum(nums)
      out result
    """
    compiler.compile(sumSource, "list-sum-dag").isRight shouldBe true

    val containsSource = """
      in nums: List<Int>
      in val: Int
      result = list-contains(nums, val)
      out result
    """
    compiler.compile(containsSource, "list-contains-dag").isRight shouldBe true

    val reverseSource = """
      in nums: List<Int>
      result = list-reverse(nums)
      out result
    """
    compiler.compile(reverseSource, "list-reverse-dag").isRight shouldBe true
  }

  it should "compile programs with type conversion functions" in {
    val compiler = StdLib.compiler

    val toStringSource = """
      in x: Int
      result = to-string(x)
      out result
    """
    compiler.compile(toStringSource, "to-string-dag").isRight shouldBe true

    val toIntSource = """
      in x: Float
      result = to-int(x)
      out result
    """
    compiler.compile(toIntSource, "to-int-dag").isRight shouldBe true

    val toFloatSource = """
      in x: Int
      result = to-float(x)
      out result
    """
    compiler.compile(toFloatSource, "to-float-dag").isRight shouldBe true
  }
}
