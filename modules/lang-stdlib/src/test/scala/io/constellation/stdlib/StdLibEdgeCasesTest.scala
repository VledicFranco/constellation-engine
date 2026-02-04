package io.constellation.stdlib

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import io.constellation.*
import io.constellation.impl.ConstellationImpl
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Edge case tests for StdLib functions - verifying execution behavior through the runtime. */
class StdLibEdgeCasesTest extends AnyFlatSpec with Matchers {

  /** Compile a program, run it with inputs, and return the output value */
  private def runProgram(
      source: String,
      inputs: Map[String, CValue],
      outputName: String
  ): CValue = {
    val test = for {
      constellation <- ConstellationImpl.init
      compiled = StdLib.compiler.compile(source, "test-dag").toOption.get
      sig <- constellation.run(compiled.pipeline, inputs)
    } yield sig.outputs(outputName)

    test.unsafeRunSync()
  }

  // ============================================================
  // Math Functions Edge Cases
  // ============================================================

  "Math add" should "handle zero operands" in {
    val source = """
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(0), "b" -> CValue.CInt(0)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  it should "handle negative numbers" in {
    val source = """
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(-5), "b" -> CValue.CInt(-3)),
      "result"
    )
    result shouldBe CValue.CInt(-8)
  }

  it should "handle mixed positive and negative" in {
    val source = """
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(10), "b" -> CValue.CInt(-15)),
      "result"
    )
    result shouldBe CValue.CInt(-5)
  }

  it should "handle large numbers" in {
    val source = """
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """
    val large  = Long.MaxValue / 2
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(large), "b" -> CValue.CInt(1)),
      "result"
    )
    result shouldBe CValue.CInt(large + 1)
  }

  "Math subtract" should "handle zero operands" in {
    val source = """
      in a: Int
      in b: Int
      result = subtract(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(0), "b" -> CValue.CInt(0)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  it should "handle negative numbers" in {
    val source = """
      in a: Int
      in b: Int
      result = subtract(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(-5), "b" -> CValue.CInt(-3)),
      "result"
    )
    result shouldBe CValue.CInt(-2)
  }

  it should "handle subtraction resulting in negative" in {
    val source = """
      in a: Int
      in b: Int
      result = subtract(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(3), "b" -> CValue.CInt(10)),
      "result"
    )
    result shouldBe CValue.CInt(-7)
  }

  "Math multiply" should "handle zero operand" in {
    val source = """
      in a: Int
      in b: Int
      result = multiply(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(100), "b" -> CValue.CInt(0)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  it should "handle negative numbers" in {
    val source = """
      in a: Int
      in b: Int
      result = multiply(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(-5), "b" -> CValue.CInt(3)),
      "result"
    )
    result shouldBe CValue.CInt(-15)
  }

  it should "handle two negative numbers (positive result)" in {
    val source = """
      in a: Int
      in b: Int
      result = multiply(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(-4), "b" -> CValue.CInt(-3)),
      "result"
    )
    result shouldBe CValue.CInt(12)
  }

  it should "handle identity multiplication" in {
    val source = """
      in a: Int
      in b: Int
      result = multiply(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(42), "b" -> CValue.CInt(1)),
      "result"
    )
    result shouldBe CValue.CInt(42)
  }

  "Math divide" should "raise error for division by zero" in {
    val source = """
      in a: Int
      in b: Int
      result = divide(a, b)
      out result
    """
    an[Exception] should be thrownBy {
      runProgram(
        source,
        Map("a" -> CValue.CInt(100), "b" -> CValue.CInt(0)),
        "result"
      )
    }
  }

  it should "handle zero dividend" in {
    val source = """
      in a: Int
      in b: Int
      result = divide(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(0), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  it should "handle integer division truncation" in {
    val source = """
      in a: Int
      in b: Int
      result = divide(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(7), "b" -> CValue.CInt(3)),
      "result"
    )
    result shouldBe CValue.CInt(2)
  }

  it should "handle negative dividend" in {
    val source = """
      in a: Int
      in b: Int
      result = divide(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(-10), "b" -> CValue.CInt(3)),
      "result"
    )
    result shouldBe CValue.CInt(-3)
  }

  it should "handle negative divisor" in {
    val source = """
      in a: Int
      in b: Int
      result = divide(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(10), "b" -> CValue.CInt(-3)),
      "result"
    )
    result shouldBe CValue.CInt(-3)
  }

  it should "handle both negative (positive result)" in {
    val source = """
      in a: Int
      in b: Int
      result = divide(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(-10), "b" -> CValue.CInt(-3)),
      "result"
    )
    result shouldBe CValue.CInt(3)
  }

  "Math max" should "handle equal values" in {
    val source = """
      in a: Int
      in b: Int
      result = max(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CInt(5)
  }

  it should "handle negative numbers" in {
    val source = """
      in a: Int
      in b: Int
      result = max(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(-10), "b" -> CValue.CInt(-5)),
      "result"
    )
    result shouldBe CValue.CInt(-5)
  }

  it should "handle zero" in {
    val source = """
      in a: Int
      in b: Int
      result = max(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(0), "b" -> CValue.CInt(-1)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  it should "handle large numbers" in {
    val source = """
      in a: Int
      in b: Int
      result = max(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(Long.MaxValue), "b" -> CValue.CInt(Long.MinValue)),
      "result"
    )
    result shouldBe CValue.CInt(Long.MaxValue)
  }

  "Math min" should "handle equal values" in {
    val source = """
      in a: Int
      in b: Int
      result = min(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CInt(5)
  }

  it should "handle negative numbers" in {
    val source = """
      in a: Int
      in b: Int
      result = min(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(-10), "b" -> CValue.CInt(-5)),
      "result"
    )
    result shouldBe CValue.CInt(-10)
  }

  it should "handle zero" in {
    val source = """
      in a: Int
      in b: Int
      result = min(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(0), "b" -> CValue.CInt(1)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  it should "handle large numbers" in {
    val source = """
      in a: Int
      in b: Int
      result = min(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(Long.MaxValue), "b" -> CValue.CInt(Long.MinValue)),
      "result"
    )
    result shouldBe CValue.CInt(Long.MinValue)
  }

  // ============================================================
  // String Functions Edge Cases
  // ============================================================

  "String concat" should "handle empty strings" in {
    val source = """
      in a: String
      in b: String
      result = concat(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CString(""), "b" -> CValue.CString("")),
      "result"
    )
    result shouldBe CValue.CString("")
  }

  it should "handle first string empty" in {
    val source = """
      in a: String
      in b: String
      result = concat(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CString(""), "b" -> CValue.CString("hello")),
      "result"
    )
    result shouldBe CValue.CString("hello")
  }

  it should "handle second string empty" in {
    val source = """
      in a: String
      in b: String
      result = concat(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CString("hello"), "b" -> CValue.CString("")),
      "result"
    )
    result shouldBe CValue.CString("hello")
  }

  it should "handle unicode strings" in {
    val source = """
      in a: String
      in b: String
      result = concat(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CString("你好"), "b" -> CValue.CString("世界")),
      "result"
    )
    result shouldBe CValue.CString("你好世界")
  }

  it should "handle special characters" in {
    val source = """
      in a: String
      in b: String
      result = concat(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CString("a\nb"), "b" -> CValue.CString("\tc")),
      "result"
    )
    result shouldBe CValue.CString("a\nb\tc")
  }

  "String trim" should "handle empty string" in {
    val source = """
      in value: String
      result = trim(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("")),
      "result"
    )
    result shouldBe CValue.CString("")
  }

  it should "trim leading and trailing whitespace" in {
    val source = """
      in value: String
      result = trim(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("  hello  ")),
      "result"
    )
    result shouldBe CValue.CString("hello")
  }

  it should "handle string with no whitespace" in {
    val source = """
      in value: String
      result = trim(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("hello")),
      "result"
    )
    result shouldBe CValue.CString("hello")
  }

  it should "handle string that is only whitespace" in {
    val source = """
      in value: String
      result = trim(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("   ")),
      "result"
    )
    result shouldBe CValue.CString("")
  }

  "String contains" should "find existing substring" in {
    val source = """
      in value: String
      in substring: String
      result = contains(value, substring)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("hello world"), "substring" -> CValue.CString("world")),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "return false for missing substring" in {
    val source = """
      in value: String
      in substring: String
      result = contains(value, substring)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("hello world"), "substring" -> CValue.CString("xyz")),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  it should "handle empty substring" in {
    val source = """
      in value: String
      in substring: String
      result = contains(value, substring)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("hello"), "substring" -> CValue.CString("")),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  "String replace" should "replace occurrences" in {
    val source = """
      in value: String
      in target: String
      in replacement: String
      result = replace(value, target, replacement)
      out result
    """
    val result = runProgram(
      source,
      Map(
        "value"       -> CValue.CString("hello world"),
        "target"      -> CValue.CString("world"),
        "replacement" -> CValue.CString("there")
      ),
      "result"
    )
    result shouldBe CValue.CString("hello there")
  }

  it should "replace all occurrences" in {
    val source = """
      in value: String
      in target: String
      in replacement: String
      result = replace(value, target, replacement)
      out result
    """
    val result = runProgram(
      source,
      Map(
        "value"       -> CValue.CString("aaa"),
        "target"      -> CValue.CString("a"),
        "replacement" -> CValue.CString("b")
      ),
      "result"
    )
    result shouldBe CValue.CString("bbb")
  }

  it should "handle no match" in {
    val source = """
      in value: String
      in target: String
      in replacement: String
      result = replace(value, target, replacement)
      out result
    """
    val result = runProgram(
      source,
      Map(
        "value"       -> CValue.CString("hello"),
        "target"      -> CValue.CString("xyz"),
        "replacement" -> CValue.CString("abc")
      ),
      "result"
    )
    result shouldBe CValue.CString("hello")
  }

  "String length" should "handle empty string" in {
    val source = """
      in value: String
      result = string-length(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("")),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  it should "handle regular string" in {
    val source = """
      in value: String
      result = string-length(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("hello")),
      "result"
    )
    result shouldBe CValue.CInt(5)
  }

  it should "handle unicode string" in {
    val source = """
      in value: String
      result = string-length(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("你好")),
      "result"
    )
    result shouldBe CValue.CInt(2)
  }

  it should "handle string with spaces" in {
    val source = """
      in value: String
      result = string-length(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("hello world")),
      "result"
    )
    result shouldBe CValue.CInt(11)
  }

  it should "handle string with newlines" in {
    val source = """
      in value: String
      result = string-length(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("a\nb\nc")),
      "result"
    )
    result shouldBe CValue.CInt(5)
  }

  // ============================================================
  // List Functions Edge Cases
  // ============================================================

  "List length" should "handle empty list" in {
    val source = """
      in list: List<Int>
      result = list-length(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector.empty, CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  it should "handle single element list" in {
    val source = """
      in list: List<Int>
      result = list-length(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(CValue.CInt(42)), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(1)
  }

  it should "handle multiple elements" in {
    val source = """
      in list: List<Int>
      result = list-length(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(1L, 2L, 3L, 4L, 5L).map(CValue.CInt.apply), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(5)
  }

  "List first" should "raise error for empty list" in {
    val source = """
      in list: List<Int>
      result = list-first(list)
      out result
    """
    an[Exception] should be thrownBy {
      runProgram(
        source,
        Map("list" -> CValue.CList(Vector.empty, CType.CInt)),
        "result"
      )
    }
  }

  it should "return first element" in {
    val source = """
      in list: List<Int>
      result = list-first(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(10L, 20L, 30L).map(CValue.CInt.apply), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(10)
  }

  it should "handle single element list" in {
    val source = """
      in list: List<Int>
      result = list-first(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(CValue.CInt(99)), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(99)
  }

  it should "handle negative numbers in list" in {
    val source = """
      in list: List<Int>
      result = list-first(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(-5L, 0L, 5L).map(CValue.CInt.apply), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(-5)
  }

  "List last" should "raise error for empty list" in {
    val source = """
      in list: List<Int>
      result = list-last(list)
      out result
    """
    an[Exception] should be thrownBy {
      runProgram(
        source,
        Map("list" -> CValue.CList(Vector.empty, CType.CInt)),
        "result"
      )
    }
  }

  it should "return last element" in {
    val source = """
      in list: List<Int>
      result = list-last(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(10L, 20L, 30L).map(CValue.CInt.apply), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(30)
  }

  it should "handle single element list" in {
    val source = """
      in list: List<Int>
      result = list-last(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(CValue.CInt(99)), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(99)
  }

  it should "handle negative numbers in list" in {
    val source = """
      in list: List<Int>
      result = list-last(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(-5L, 0L, 5L).map(CValue.CInt.apply), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(5)
  }

  "List isEmpty" should "return true for empty list" in {
    val source = """
      in list: List<Int>
      result = list-is-empty(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector.empty, CType.CInt)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "return false for non-empty list" in {
    val source = """
      in list: List<Int>
      result = list-is-empty(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(CValue.CInt(1)), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  it should "return false for list with multiple elements" in {
    val source = """
      in list: List<Int>
      result = list-is-empty(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(1L, 2L, 3L).map(CValue.CInt.apply), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  // ============================================================
  // Utility Functions Edge Cases
  // ============================================================

  "Identity function" should "pass through empty string" in {
    val source = """
      in value: String
      result = identity(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("")),
      "result"
    )
    result shouldBe CValue.CString("")
  }

  it should "pass through regular string unchanged" in {
    val source = """
      in value: String
      result = identity(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("hello world")),
      "result"
    )
    result shouldBe CValue.CString("hello world")
  }

  it should "pass through unicode string unchanged" in {
    val source = """
      in value: String
      result = identity(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("こんにちは")),
      "result"
    )
    result shouldBe CValue.CString("こんにちは")
  }

  "Log function" should "pass through the message" in {
    val source = """
      in message: String
      result = log(message)
      out result
    """
    val result = runProgram(
      source,
      Map("message" -> CValue.CString("test log")),
      "result"
    )
    result shouldBe CValue.CString("test log")
  }

  it should "handle empty message" in {
    val source = """
      in message: String
      result = log(message)
      out result
    """
    val result = runProgram(
      source,
      Map("message" -> CValue.CString("")),
      "result"
    )
    result shouldBe CValue.CString("")
  }

  // ============================================================
  // Comparison Functions Edge Cases
  // ============================================================

  "Greater than (gt)" should "handle equal values" in {
    val source = """
      in a: Int
      in b: Int
      result = gt(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  it should "handle first greater" in {
    val source = """
      in a: Int
      in b: Int
      result = gt(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(10), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "handle first less" in {
    val source = """
      in a: Int
      in b: Int
      result = gt(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(10)),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  it should "handle negative numbers" in {
    val source = """
      in a: Int
      in b: Int
      result = gt(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(-5), "b" -> CValue.CInt(-10)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "handle zero" in {
    val source = """
      in a: Int
      in b: Int
      result = gt(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(0), "b" -> CValue.CInt(-1)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  "Less than (lt)" should "handle equal values" in {
    val source = """
      in a: Int
      in b: Int
      result = lt(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  it should "handle first less" in {
    val source = """
      in a: Int
      in b: Int
      result = lt(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(10)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "handle first greater" in {
    val source = """
      in a: Int
      in b: Int
      result = lt(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(10), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  it should "handle negative numbers" in {
    val source = """
      in a: Int
      in b: Int
      result = lt(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(-10), "b" -> CValue.CInt(-5)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  "Greater than or equal (gte)" should "handle equal values" in {
    val source = """
      in a: Int
      in b: Int
      result = gte(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "handle first greater" in {
    val source = """
      in a: Int
      in b: Int
      result = gte(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(10), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "handle first less" in {
    val source = """
      in a: Int
      in b: Int
      result = gte(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(10)),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  "Less than or equal (lte)" should "handle equal values" in {
    val source = """
      in a: Int
      in b: Int
      result = lte(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "handle first less" in {
    val source = """
      in a: Int
      in b: Int
      result = lte(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(10)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "handle first greater" in {
    val source = """
      in a: Int
      in b: Int
      result = lte(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(10), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  "Equality (eq-int)" should "handle equal values" in {
    val source = """
      in a: Int
      in b: Int
      result = eq-int(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "handle unequal values" in {
    val source = """
      in a: Int
      in b: Int
      result = eq-int(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(10)),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  it should "handle zero" in {
    val source = """
      in a: Int
      in b: Int
      result = eq-int(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(0), "b" -> CValue.CInt(0)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "handle negative equal values" in {
    val source = """
      in a: Int
      in b: Int
      result = eq-int(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(-5), "b" -> CValue.CInt(-5)),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  "String equality (eq-string)" should "handle equal strings" in {
    val source = """
      in a: String
      in b: String
      result = eq-string(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CString("hello"), "b" -> CValue.CString("hello")),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "handle unequal strings" in {
    val source = """
      in a: String
      in b: String
      result = eq-string(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CString("hello"), "b" -> CValue.CString("world")),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  it should "handle empty strings" in {
    val source = """
      in a: String
      in b: String
      result = eq-string(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CString(""), "b" -> CValue.CString("")),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "handle case sensitivity" in {
    val source = """
      in a: String
      in b: String
      result = eq-string(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CString("Hello"), "b" -> CValue.CString("hello")),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  it should "handle unicode strings" in {
    val source = """
      in a: String
      in b: String
      result = eq-string(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CString("你好"), "b" -> CValue.CString("你好")),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  // Note: Boolean operators (and, or, not) are built-in language keywords,
  // not StdLib functions. They are tested in the orchestration tests.
  // See TypeCheckerTest and LangCompilerTest for coverage.

  // ============================================================
  // New Math Functions Edge Cases
  // ============================================================

  "Math abs" should "handle positive number" in {
    val source = """
      in value: Int
      result = abs(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CInt(5)
  }

  it should "handle negative number" in {
    val source = """
      in value: Int
      result = abs(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(-5)),
      "result"
    )
    result shouldBe CValue.CInt(5)
  }

  it should "handle zero" in {
    val source = """
      in value: Int
      result = abs(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(0)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  "Math modulo" should "handle basic modulo" in {
    val source = """
      in a: Int
      in b: Int
      result = modulo(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(7), "b" -> CValue.CInt(3)),
      "result"
    )
    result shouldBe CValue.CInt(1)
  }

  it should "raise error for division by zero" in {
    val source = """
      in a: Int
      in b: Int
      result = modulo(a, b)
      out result
    """
    an[Exception] should be thrownBy {
      runProgram(
        source,
        Map("a" -> CValue.CInt(5), "b" -> CValue.CInt(0)),
        "result"
      )
    }
  }

  it should "handle exact divisibility" in {
    val source = """
      in a: Int
      in b: Int
      result = modulo(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(6), "b" -> CValue.CInt(3)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  "Math negate" should "negate positive number" in {
    val source = """
      in value: Int
      result = negate(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(5)),
      "result"
    )
    result shouldBe CValue.CInt(-5)
  }

  it should "negate negative number" in {
    val source = """
      in value: Int
      result = negate(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(-5)),
      "result"
    )
    result shouldBe CValue.CInt(5)
  }

  it should "handle zero" in {
    val source = """
      in value: Int
      result = negate(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(0)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  "Math round" should "round down" in {
    val source = """
      in value: Float
      result = round(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CFloat(3.2)),
      "result"
    )
    result shouldBe CValue.CInt(3)
  }

  it should "round up" in {
    val source = """
      in value: Float
      result = round(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CFloat(3.7)),
      "result"
    )
    result shouldBe CValue.CInt(4)
  }

  it should "handle exactly 0.5" in {
    val source = """
      in value: Float
      result = round(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CFloat(3.5)),
      "result"
    )
    result shouldBe CValue.CInt(4)
  }

  it should "handle negative value" in {
    val source = """
      in value: Float
      result = round(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CFloat(-2.7)),
      "result"
    )
    result shouldBe CValue.CInt(-3)
  }

  // ============================================================
  // New List Functions Edge Cases
  // ============================================================

  "List sum" should "handle empty list" in {
    val source = """
      in list: List<Int>
      result = list-sum(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector.empty, CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  it should "sum elements" in {
    val source = """
      in list: List<Int>
      result = list-sum(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(1L, 2L, 3L, 4L, 5L).map(CValue.CInt.apply), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(15)
  }

  it should "handle negative numbers" in {
    val source = """
      in list: List<Int>
      result = list-sum(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(-1L, -2L, 3L).map(CValue.CInt.apply), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  "List contains" should "find existing element" in {
    val source = """
      in list: List<Int>
      in value: Int
      result = list-contains(list, value)
      out result
    """
    val result = runProgram(
      source,
      Map(
        "list"  -> CValue.CList(Vector(1L, 2L, 3L).map(CValue.CInt.apply), CType.CInt),
        "value" -> CValue.CInt(2)
      ),
      "result"
    )
    result shouldBe CValue.CBoolean(true)
  }

  it should "not find missing element" in {
    val source = """
      in list: List<Int>
      in value: Int
      result = list-contains(list, value)
      out result
    """
    val result = runProgram(
      source,
      Map(
        "list"  -> CValue.CList(Vector(1L, 2L, 3L).map(CValue.CInt.apply), CType.CInt),
        "value" -> CValue.CInt(5)
      ),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  it should "handle empty list" in {
    val source = """
      in list: List<Int>
      in value: Int
      result = list-contains(list, value)
      out result
    """
    val result = runProgram(
      source,
      Map(
        "list"  -> CValue.CList(Vector.empty, CType.CInt),
        "value" -> CValue.CInt(1)
      ),
      "result"
    )
    result shouldBe CValue.CBoolean(false)
  }

  "List reverse" should "reverse list" in {
    val source = """
      in list: List<Int>
      result = list-reverse(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(1L, 2L, 3L).map(CValue.CInt.apply), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CList(Vector(3L, 2L, 1L).map(CValue.CInt.apply), CType.CInt)
  }

  it should "handle empty list" in {
    val source = """
      in list: List<Int>
      result = list-reverse(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector.empty, CType.CInt)),
      "result"
    )
    result shouldBe CValue.CList(Vector.empty, CType.CInt)
  }

  it should "handle single element list" in {
    val source = """
      in list: List<Int>
      result = list-reverse(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector(CValue.CInt(42)), CType.CInt)),
      "result"
    )
    result shouldBe CValue.CList(Vector(CValue.CInt(42)), CType.CInt)
  }

  "List concat" should "concatenate two lists" in {
    val source = """
      in a: List<Int>
      in b: List<Int>
      result = list-concat(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map(
        "a" -> CValue.CList(Vector(1L, 2L).map(CValue.CInt.apply), CType.CInt),
        "b" -> CValue.CList(Vector(3L, 4L).map(CValue.CInt.apply), CType.CInt)
      ),
      "result"
    )
    result shouldBe CValue.CList(Vector(1L, 2L, 3L, 4L).map(CValue.CInt.apply), CType.CInt)
  }

  it should "handle first list empty" in {
    val source = """
      in a: List<Int>
      in b: List<Int>
      result = list-concat(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map(
        "a" -> CValue.CList(Vector.empty, CType.CInt),
        "b" -> CValue.CList(Vector(1L, 2L).map(CValue.CInt.apply), CType.CInt)
      ),
      "result"
    )
    result shouldBe CValue.CList(Vector(1L, 2L).map(CValue.CInt.apply), CType.CInt)
  }

  it should "handle both lists empty" in {
    val source = """
      in a: List<Int>
      in b: List<Int>
      result = list-concat(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map(
        "a" -> CValue.CList(Vector.empty, CType.CInt),
        "b" -> CValue.CList(Vector.empty, CType.CInt)
      ),
      "result"
    )
    result shouldBe CValue.CList(Vector.empty, CType.CInt)
  }

  // ============================================================
  // Type Conversion Functions Edge Cases
  // ============================================================

  "to-string" should "convert positive integer" in {
    val source = """
      in value: Int
      result = to-string(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(42)),
      "result"
    )
    result shouldBe CValue.CString("42")
  }

  it should "convert negative integer" in {
    val source = """
      in value: Int
      result = to-string(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(-7)),
      "result"
    )
    result shouldBe CValue.CString("-7")
  }

  it should "convert zero" in {
    val source = """
      in value: Int
      result = to-string(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(0)),
      "result"
    )
    result shouldBe CValue.CString("0")
  }

  "to-int" should "truncate positive float" in {
    val source = """
      in value: Float
      result = to-int(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CFloat(3.7)),
      "result"
    )
    result shouldBe CValue.CInt(3)
  }

  it should "truncate negative float" in {
    val source = """
      in value: Float
      result = to-int(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CFloat(-3.7)),
      "result"
    )
    result shouldBe CValue.CInt(-3)
  }

  it should "handle zero" in {
    val source = """
      in value: Float
      result = to-int(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CFloat(0.0)),
      "result"
    )
    result shouldBe CValue.CInt(0)
  }

  "to-float" should "convert positive integer" in {
    val source = """
      in value: Int
      result = to-float(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(42)),
      "result"
    )
    result shouldBe CValue.CFloat(42.0)
  }

  it should "convert negative integer" in {
    val source = """
      in value: Int
      result = to-float(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(-7)),
      "result"
    )
    result shouldBe CValue.CFloat(-7.0)
  }

  it should "convert zero" in {
    val source = """
      in value: Int
      result = to-float(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CInt(0)),
      "result"
    )
    result shouldBe CValue.CFloat(0.0)
  }
}
