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
      sig <- constellation.run(compiled.program, inputs)
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
    val large = Long.MaxValue / 2
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

  "Math divide" should "return 0 for division by zero (safe default)" in {
    val source = """
      in a: Int
      in b: Int
      result = divide(a, b)
      out result
    """
    val result = runProgram(
      source,
      Map("a" -> CValue.CInt(100), "b" -> CValue.CInt(0)),
      "result"
    )
    result shouldBe CValue.CInt(0)
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

  "String upper" should "handle empty string" in {
    val source = """
      in value: String
      result = upper(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("")),
      "result"
    )
    result shouldBe CValue.CString("")
  }

  it should "handle already uppercase" in {
    val source = """
      in value: String
      result = upper(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("HELLO")),
      "result"
    )
    result shouldBe CValue.CString("HELLO")
  }

  it should "handle mixed case" in {
    val source = """
      in value: String
      result = upper(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("HeLLo WoRLd")),
      "result"
    )
    result shouldBe CValue.CString("HELLO WORLD")
  }

  it should "handle unicode characters" in {
    val source = """
      in value: String
      result = upper(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("café")),
      "result"
    )
    result shouldBe CValue.CString("CAFÉ")
  }

  it should "handle numbers and special chars (unchanged)" in {
    val source = """
      in value: String
      result = upper(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("abc123!@#")),
      "result"
    )
    result shouldBe CValue.CString("ABC123!@#")
  }

  "String lower" should "handle empty string" in {
    val source = """
      in value: String
      result = lower(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("")),
      "result"
    )
    result shouldBe CValue.CString("")
  }

  it should "handle already lowercase" in {
    val source = """
      in value: String
      result = lower(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("hello")),
      "result"
    )
    result shouldBe CValue.CString("hello")
  }

  it should "handle mixed case" in {
    val source = """
      in value: String
      result = lower(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("HeLLo WoRLd")),
      "result"
    )
    result shouldBe CValue.CString("hello world")
  }

  it should "handle unicode characters" in {
    val source = """
      in value: String
      result = lower(value)
      out result
    """
    val result = runProgram(
      source,
      Map("value" -> CValue.CString("CAFÉ")),
      "result"
    )
    result shouldBe CValue.CString("café")
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

  "List first" should "return 0 for empty list (safe default)" in {
    val source = """
      in list: List<Int>
      result = list-first(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector.empty, CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(0)
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

  "List last" should "return 0 for empty list (safe default)" in {
    val source = """
      in list: List<Int>
      result = list-last(list)
      out result
    """
    val result = runProgram(
      source,
      Map("list" -> CValue.CList(Vector.empty, CType.CInt)),
      "result"
    )
    result shouldBe CValue.CInt(0)
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
}
