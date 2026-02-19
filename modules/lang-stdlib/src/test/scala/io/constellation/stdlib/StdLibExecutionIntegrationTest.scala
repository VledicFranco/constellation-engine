package io.constellation.stdlib

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests for stdlib functions that verify actual runtime execution.
  *
  * These tests compile constellation-lang source code and execute the resulting DAG to verify that
  * stdlib modules produce correct outputs at runtime. This complements the existing compilation
  * tests by validating runtime behavior.
  *
  * Coverage: All 41+ stdlib functions across categories:
  *   - Math: add, subtract, multiply, divide, max, min, abs, modulo, round, negate
  *   - String: concat, string-length, join, split, contains, trim, replace
  *   - List: list-length, list-first, list-last, list-is-empty, list-sum, list-concat,
  *     list-contains, list-reverse
  *   - Boolean: and, or, not (built-in operators)
  *   - Comparison: eq-int, eq-string, gt, lt, gte, lte
  *   - Utility: identity, log
  *   - Higher-order: filter, map, all, any (lambda-based)
  *   - Type conversion: to-string, to-int, to-float
  */
class StdLibExecutionIntegrationTest extends AnyFlatSpec with Matchers {

  /** Helper to compile and execute constellation-lang source, returning the output value.
    *
    * @param source
    *   constellation-lang source code with input declarations and output
    * @param dagName
    *   name for the compiled DAG
    * @param inputs
    *   map of input variable names to CValue inputs
    * @param outputName
    *   name of the output variable to retrieve
    * @return
    *   the CValue result from the output variable
    */
  private def compileAndRun(
      source: String,
      dagName: String,
      inputs: Map[String, CValue],
      outputName: String
  ): CValue = {
    val compiler       = StdLib.compiler
    val compileResult  = compiler.compile(source, dagName)
    val compiledOutput = compileResult.toOption.get

    val dag     = compiledOutput.pipeline.image.dagSpec
    val modules = compiledOutput.pipeline.syntheticModules

    val state = Runtime.run(dag, inputs, modules).unsafeRunSync()

    // Use outputBindings to find the data node UUID for the named output
    val outputNodeId = dag.outputBindings.getOrElse(
      outputName,
      throw new RuntimeException(
        s"Output '$outputName' not found in outputBindings. Available: ${dag.outputBindings.keys.mkString(", ")}"
      )
    )

    state.data(outputNodeId).value
  }

  /** Helper for programs that may fail with an error. */
  private def compileAndRunAttempt(
      source: String,
      dagName: String,
      inputs: Map[String, CValue],
      outputName: String
  ): Either[Throwable, CValue] = {
    val compiler       = StdLib.compiler
    val compileResult  = compiler.compile(source, dagName)
    val compiledOutput = compileResult.toOption.get

    val dag     = compiledOutput.pipeline.image.dagSpec
    val modules = compiledOutput.pipeline.syntheticModules

    Runtime
      .run(dag, inputs, modules)
      .attempt
      .unsafeRunSync()
      .map { state =>
        val outputNodeId = dag.outputBindings.getOrElse(
          outputName,
          throw new RuntimeException(
            s"Output '$outputName' not found in outputBindings. Available: ${dag.outputBindings.keys
                .mkString(", ")}"
          )
        )

        state.data(outputNodeId).value
      }
  }

  // ===== Math Functions (10 functions) =====

  "Math: add" should "add two integers" in {
    val source = """
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(10L), "b" -> CValue.CInt(32L))
    val output = compileAndRun(source, "add-test", inputs, "result")
    output shouldBe CValue.CInt(42L)
  }

  "Math: subtract" should "subtract two integers" in {
    val source = """
      in a: Int
      in b: Int
      result = subtract(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(50L), "b" -> CValue.CInt(8L))
    val output = compileAndRun(source, "subtract-test", inputs, "result")
    output shouldBe CValue.CInt(42L)
  }

  "Math: multiply" should "multiply two integers" in {
    val source = """
      in a: Int
      in b: Int
      result = multiply(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(6L), "b" -> CValue.CInt(7L))
    val output = compileAndRun(source, "multiply-test", inputs, "result")
    output shouldBe CValue.CInt(42L)
  }

  "Math: divide" should "divide two integers" in {
    val source = """
      in a: Int
      in b: Int
      result = divide(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(84L), "b" -> CValue.CInt(2L))
    val output = compileAndRun(source, "divide-test", inputs, "result")
    output shouldBe CValue.CInt(42L)
  }

  it should "fail when dividing by zero" in {
    val source = """
      in a: Int
      in b: Int
      result = divide(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(42L), "b" -> CValue.CInt(0L))

    // When a module fails, the module status should be Failed
    val compiler       = StdLib.compiler
    val compileResult  = compiler.compile(source, "divide-zero-test")
    val compiledOutput = compileResult.toOption.get
    val dag            = compiledOutput.pipeline.image.dagSpec
    val modules        = compiledOutput.pipeline.syntheticModules

    val state = Runtime.run(dag, inputs, modules).unsafeRunSync()

    // Find the divide module and check its status
    val divideModuleId = dag.modules.keys.head
    val moduleStatus   = state.moduleStatus(divideModuleId).value

    moduleStatus shouldBe a[Module.Status.Failed]
  }

  "Math: max" should "return maximum of two integers" in {
    val source = """
      in a: Int
      in b: Int
      result = max(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(42L), "b" -> CValue.CInt(10L))
    val output = compileAndRun(source, "max-test", inputs, "result")
    output shouldBe CValue.CInt(42L)
  }

  "Math: min" should "return minimum of two integers" in {
    val source = """
      in a: Int
      in b: Int
      result = min(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(10L), "b" -> CValue.CInt(42L))
    val output = compileAndRun(source, "min-test", inputs, "result")
    output shouldBe CValue.CInt(10L)
  }

  "Math: abs" should "return absolute value" in {
    val source = """
      in value: Int
      result = abs(value)
      out result
    """
    val inputs = Map("value" -> CValue.CInt(-42L))
    val output = compileAndRun(source, "abs-test", inputs, "result")
    output shouldBe CValue.CInt(42L)
  }

  "Math: modulo" should "return remainder after division" in {
    val source = """
      in a: Int
      in b: Int
      result = modulo(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(42L), "b" -> CValue.CInt(10L))
    val output = compileAndRun(source, "modulo-test", inputs, "result")
    output shouldBe CValue.CInt(2L)
  }

  it should "fail when modulo by zero" in {
    val source = """
      in a: Int
      in b: Int
      result = modulo(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(42L), "b" -> CValue.CInt(0L))

    // When a module fails, the module status should be Failed
    val compiler       = StdLib.compiler
    val compileResult  = compiler.compile(source, "modulo-zero-test")
    val compiledOutput = compileResult.toOption.get
    val dag            = compiledOutput.pipeline.image.dagSpec
    val modules        = compiledOutput.pipeline.syntheticModules

    val state = Runtime.run(dag, inputs, modules).unsafeRunSync()

    // Find the modulo module and check its status
    val moduloModuleId = dag.modules.keys.head
    val moduleStatus   = state.moduleStatus(moduloModuleId).value

    moduleStatus shouldBe a[Module.Status.Failed]
  }

  "Math: round" should "round float to nearest integer" in {
    val source = """
      in value: Float
      result = round(value)
      out result
    """
    val inputs = Map("value" -> CValue.CFloat(42.7))
    val output = compileAndRun(source, "round-test", inputs, "result")
    output shouldBe CValue.CInt(43L)
  }

  "Math: negate" should "negate an integer" in {
    val source = """
      in value: Int
      result = negate(value)
      out result
    """
    val inputs = Map("value" -> CValue.CInt(42L))
    val output = compileAndRun(source, "negate-test", inputs, "result")
    output shouldBe CValue.CInt(-42L)
  }

  // ===== String Functions (7 functions) =====

  "String: concat" should "concatenate two strings" in {
    val source = """
      in a: String
      in b: String
      result = concat(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CString("Hello"), "b" -> CValue.CString(" World"))
    val output = compileAndRun(source, "concat-test", inputs, "result")
    output shouldBe CValue.CString("Hello World")
  }

  "String: string-length" should "return length of string" in {
    val source = """
      in text: String
      result = string-length(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("Hello"))
    val output = compileAndRun(source, "string-length-test", inputs, "result")
    output shouldBe CValue.CInt(5L)
  }

  "String: join" should "join list of strings with separator" in {
    val source = """
      in items: List<String>
      in sep: String
      result = join(items, sep)
      out result
    """
    val inputs = Map(
      "items" -> CValue.CList(
        Vector(CValue.CString("a"), CValue.CString("b"), CValue.CString("c")),
        CType.CString
      ),
      "sep" -> CValue.CString(",")
    )
    val output = compileAndRun(source, "join-test", inputs, "result")
    output shouldBe CValue.CString("a,b,c")
  }

  "String: split" should "split string by separator" in {
    val source = """
      in text: String
      in sep: String
      result = split(text, sep)
      out result
    """
    val inputs = Map("text" -> CValue.CString("a,b,c"), "sep" -> CValue.CString(","))
    val output = compileAndRun(source, "split-test", inputs, "result")
    output shouldBe CValue.CList(
      Vector(CValue.CString("a"), CValue.CString("b"), CValue.CString("c")),
      CType.CString
    )
  }

  "String: contains" should "check if string contains substring" in {
    val source = """
      in text: String
      in sub: String
      result = contains(text, sub)
      out result
    """
    val inputs = Map("text" -> CValue.CString("Hello World"), "sub" -> CValue.CString("World"))
    val output = compileAndRun(source, "contains-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  "String: trim" should "remove leading/trailing whitespace" in {
    val source = """
      in text: String
      result = trim(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("  Hello  "))
    val output = compileAndRun(source, "trim-test", inputs, "result")
    output shouldBe CValue.CString("Hello")
  }

  "String: replace" should "replace all occurrences of substring" in {
    val source = """
      in text: String
      in old: String
      in new: String
      result = replace(text, old, new)
      out result
    """
    val inputs = Map(
      "text" -> CValue.CString("Hello World"),
      "old"  -> CValue.CString("World"),
      "new"  -> CValue.CString("Scala")
    )
    val output = compileAndRun(source, "replace-test", inputs, "result")
    output shouldBe CValue.CString("Hello Scala")
  }

  // ===== List Functions (8 functions) =====

  "List: list-length" should "return length of list" in {
    val source = """
      in items: List<Int>
      result = list-length(items)
      out result
    """
    val inputs =
      Map("items" -> CValue.CList(Vector(CValue.CInt(1L), CValue.CInt(2L)), CType.CInt))
    val output = compileAndRun(source, "list-length-test", inputs, "result")
    output shouldBe CValue.CInt(2L)
  }

  "List: list-first" should "return first element of list" in {
    val source = """
      in items: List<Int>
      result = list-first(items)
      out result
    """
    val inputs =
      Map("items" -> CValue.CList(Vector(CValue.CInt(42L), CValue.CInt(10L)), CType.CInt))
    val output = compileAndRun(source, "list-first-test", inputs, "result")
    output shouldBe CValue.CInt(42L)
  }

  "List: list-last" should "return last element of list" in {
    val source = """
      in items: List<Int>
      result = list-last(items)
      out result
    """
    val inputs =
      Map("items" -> CValue.CList(Vector(CValue.CInt(10L), CValue.CInt(42L)), CType.CInt))
    val output = compileAndRun(source, "list-last-test", inputs, "result")
    output shouldBe CValue.CInt(42L)
  }

  "List: list-is-empty" should "return true for empty list" in {
    val source = """
      in items: List<Int>
      result = list-is-empty(items)
      out result
    """
    val inputs = Map("items" -> CValue.CList(Vector.empty, CType.CInt))
    val output = compileAndRun(source, "list-is-empty-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  it should "return false for non-empty list" in {
    val source = """
      in items: List<Int>
      result = list-is-empty(items)
      out result
    """
    val inputs = Map("items" -> CValue.CList(Vector(CValue.CInt(1L)), CType.CInt))
    val output = compileAndRun(source, "list-is-empty-false-test", inputs, "result")
    output shouldBe CValue.CBoolean(false)
  }

  "List: list-sum" should "return sum of integer list" in {
    val source = """
      in items: List<Int>
      result = list-sum(items)
      out result
    """
    val inputs = Map(
      "items" -> CValue.CList(
        Vector(CValue.CInt(10L), CValue.CInt(20L), CValue.CInt(12L)),
        CType.CInt
      )
    )
    val output = compileAndRun(source, "list-sum-test", inputs, "result")
    output shouldBe CValue.CInt(42L)
  }

  "List: list-concat" should "concatenate two lists" in {
    val source = """
      in a: List<Int>
      in b: List<Int>
      result = list-concat(a, b)
      out result
    """
    val inputs = Map(
      "a" -> CValue.CList(Vector(CValue.CInt(1L), CValue.CInt(2L)), CType.CInt),
      "b" -> CValue.CList(Vector(CValue.CInt(3L), CValue.CInt(4L)), CType.CInt)
    )
    val output = compileAndRun(source, "list-concat-test", inputs, "result")
    output shouldBe CValue.CList(
      Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L), CValue.CInt(4L)),
      CType.CInt
    )
  }

  "List: list-contains" should "check if list contains element" in {
    val source = """
      in items: List<Int>
      in value: Int
      result = list-contains(items, value)
      out result
    """
    val inputs = Map(
      "items" -> CValue.CList(Vector(CValue.CInt(10L), CValue.CInt(42L)), CType.CInt),
      "value" -> CValue.CInt(42L)
    )
    val output = compileAndRun(source, "list-contains-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  "List: list-reverse" should "reverse a list" in {
    val source = """
      in items: List<Int>
      result = list-reverse(items)
      out result
    """
    val inputs = Map(
      "items" -> CValue.CList(
        Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
        CType.CInt
      )
    )
    val output = compileAndRun(source, "list-reverse-test", inputs, "result")
    output shouldBe CValue.CList(
      Vector(CValue.CInt(3L), CValue.CInt(2L), CValue.CInt(1L)),
      CType.CInt
    )
  }

  // ===== Boolean Functions (built-in operators) =====

  "Boolean: and" should "perform logical AND" in {
    val source = """
      in a: Boolean
      in b: Boolean
      result = a and b
      out result
    """
    val inputs = Map("a" -> CValue.CBoolean(true), "b" -> CValue.CBoolean(true))
    val output = compileAndRun(source, "and-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  "Boolean: or" should "perform logical OR" in {
    val source = """
      in a: Boolean
      in b: Boolean
      result = a or b
      out result
    """
    val inputs = Map("a" -> CValue.CBoolean(false), "b" -> CValue.CBoolean(true))
    val output = compileAndRun(source, "or-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  "Boolean: not" should "perform logical NOT" in {
    val source = """
      in a: Boolean
      result = not a
      out result
    """
    val inputs = Map("a" -> CValue.CBoolean(false))
    val output = compileAndRun(source, "not-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  // ===== Comparison Functions (6 functions) =====

  "Comparison: eq-int" should "check integer equality" in {
    val source = """
      in a: Int
      in b: Int
      result = eq-int(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(42L), "b" -> CValue.CInt(42L))
    val output = compileAndRun(source, "eq-int-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  "Comparison: eq-string" should "check string equality" in {
    val source = """
      in a: String
      in b: String
      result = eq-string(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CString("hello"), "b" -> CValue.CString("hello"))
    val output = compileAndRun(source, "eq-string-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  "Comparison: gt" should "check if first integer is greater than second" in {
    val source = """
      in a: Int
      in b: Int
      result = gt(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(42L), "b" -> CValue.CInt(10L))
    val output = compileAndRun(source, "gt-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  "Comparison: lt" should "check if first integer is less than second" in {
    val source = """
      in a: Int
      in b: Int
      result = lt(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(10L), "b" -> CValue.CInt(42L))
    val output = compileAndRun(source, "lt-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  "Comparison: gte" should "check if first integer is greater than or equal to second" in {
    val source = """
      in a: Int
      in b: Int
      result = gte(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(42L), "b" -> CValue.CInt(42L))
    val output = compileAndRun(source, "gte-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  "Comparison: lte" should "check if first integer is less than or equal to second" in {
    val source = """
      in a: Int
      in b: Int
      result = lte(a, b)
      out result
    """
    val inputs = Map("a" -> CValue.CInt(42L), "b" -> CValue.CInt(42L))
    val output = compileAndRun(source, "lte-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  // ===== Utility Functions (2 functions) =====

  "Utility: identity" should "return input unchanged" in {
    val source = """
      in value: String
      result = identity(value)
      out result
    """
    val inputs = Map("value" -> CValue.CString("hello"))
    val output = compileAndRun(source, "identity-test", inputs, "result")
    output shouldBe CValue.CString("hello")
  }

  "Utility: log" should "execute without error" in {
    val source = """
      in message: String
      result = log(message)
      out result
    """
    val inputs = Map("message" -> CValue.CString("test"))
    val output = compileAndRun(source, "log-test", inputs, "result")
    output shouldBe CValue.CString("test")
  }

  // ===== Higher-Order Functions (4 functions) =====

  "HOF: filter" should "filter list elements by predicate" in {
    val source = """
      in numbers: Seq<Int>
      result = filter(numbers, (x) => x > 10)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CSeq(
        Vector(CValue.CInt(5L), CValue.CInt(15L), CValue.CInt(8L), CValue.CInt(42L)),
        CType.CInt
      )
    )
    val output = compileAndRun(source, "filter-test", inputs, "result")
    output shouldBe CValue.CSeq(Vector(CValue.CInt(15L), CValue.CInt(42L)), CType.CInt)
  }

  "HOF: map" should "transform list elements" in {
    val source = """
      in numbers: Seq<Int>
      result = map(numbers, (x) => x * 2)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CSeq(
        Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
        CType.CInt
      )
    )
    val output = compileAndRun(source, "map-test", inputs, "result")
    output shouldBe CValue.CSeq(
      Vector(CValue.CInt(2L), CValue.CInt(4L), CValue.CInt(6L)),
      CType.CInt
    )
  }

  "HOF: all" should "return true if all elements satisfy predicate" in {
    val source = """
      in numbers: Seq<Int>
      result = all(numbers, (x) => x > 0)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CSeq(
        Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
        CType.CInt
      )
    )
    val output = compileAndRun(source, "all-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  it should "return false if any element fails predicate" in {
    val source = """
      in numbers: Seq<Int>
      result = all(numbers, (x) => x > 1)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CSeq(
        Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
        CType.CInt
      )
    )
    val output = compileAndRun(source, "all-false-test", inputs, "result")
    output shouldBe CValue.CBoolean(false)
  }

  "HOF: any" should "return true if any element satisfies predicate" in {
    val source = """
      in numbers: Seq<Int>
      result = any(numbers, (x) => x > 2)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CSeq(
        Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
        CType.CInt
      )
    )
    val output = compileAndRun(source, "any-test", inputs, "result")
    output shouldBe CValue.CBoolean(true)
  }

  it should "return false if no element satisfies predicate" in {
    val source = """
      in numbers: Seq<Int>
      result = any(numbers, (x) => x > 10)
      out result
    """
    val inputs = Map(
      "numbers" -> CValue.CSeq(
        Vector(CValue.CInt(1L), CValue.CInt(2L), CValue.CInt(3L)),
        CType.CInt
      )
    )
    val output = compileAndRun(source, "any-false-test", inputs, "result")
    output shouldBe CValue.CBoolean(false)
  }

  // ===== Type Conversion Functions (3 functions) =====

  "TypeConversion: to-string" should "convert integer to string" in {
    val source = """
      in value: Int
      result = to-string(value)
      out result
    """
    val inputs = Map("value" -> CValue.CInt(42L))
    val output = compileAndRun(source, "to-string-test", inputs, "result")
    output shouldBe CValue.CString("42")
  }

  "TypeConversion: to-int" should "truncate float to integer" in {
    val source = """
      in value: Float
      result = to-int(value)
      out result
    """
    val inputs = Map("value" -> CValue.CFloat(42.7))
    val output = compileAndRun(source, "to-int-test", inputs, "result")
    output shouldBe CValue.CInt(42L)
  }

  "TypeConversion: to-float" should "convert integer to float" in {
    val source = """
      in value: Int
      result = to-float(value)
      out result
    """
    val inputs = Map("value" -> CValue.CInt(42L))
    val output = compileAndRun(source, "to-float-test", inputs, "result")
    output shouldBe CValue.CFloat(42.0)
  }

  // ===== Complex Integration Tests =====

  "Integration: complex pipeline" should "execute multiple stdlib functions in sequence" in {
    val source = """
      in a: Int
      in b: Int
      sum = add(a, b)
      doubled = multiply(sum, 2)
      isLarge = gt(doubled, 50)
      out isLarge
    """
    val inputs = Map("a" -> CValue.CInt(10L), "b" -> CValue.CInt(20L))
    val output = compileAndRun(source, "complex-test", inputs, "isLarge")
    output shouldBe CValue.CBoolean(true) // (10+20)*2 = 60 > 50
  }

  "Integration: string and list operations" should "combine string and list functions" in {
    val source = """
      in words: List<String>
      joined = join(words, " ")
      length = string-length(joined)
      out length
    """
    val inputs = Map(
      "words" -> CValue.CList(
        Vector(CValue.CString("Hello"), CValue.CString("World")),
        CType.CString
      )
    )
    val output = compileAndRun(source, "string-list-test", inputs, "length")
    output shouldBe CValue.CInt(11L) // "Hello World" has 11 characters
  }

  "Integration: filter and sum" should "filter seq and return positives" in {
    val source = """
      in numbers: Seq<Int>
      positives = filter(numbers, (x) => x > 0)
      out positives
    """
    val inputs = Map(
      "numbers" -> CValue.CSeq(
        Vector(CValue.CInt(-5L), CValue.CInt(10L), CValue.CInt(-3L), CValue.CInt(20L)),
        CType.CInt
      )
    )
    val output = compileAndRun(source, "filter-seq-test", inputs, "positives")
    output shouldBe CValue.CSeq(
      Vector(CValue.CInt(10L), CValue.CInt(20L)),
      CType.CInt
    )
  }
}
