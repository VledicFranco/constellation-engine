package io.constellation.examples.app.modules

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.*
import io.constellation.examples.app.ExampleLib
import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Unit tests for TextModules.
  *
  * Tests all text processing modules with various inputs including edge cases.
  */
class TextModulesTest extends AnyFlatSpec with Matchers {

  private val compiler = ExampleLib.compiler

  /** Create a Constellation instance with all modules registered */
  private def createConstellation: IO[Constellation] =
    for {
      constellation <- ConstellationImpl.init
      allModules = (StdLib.allModules ++ ExampleLib.allModules).values.toList
      _ <- allModules.traverse(constellation.setModule)
    } yield constellation

  /** Helper to compile, run, and extract output value */
  private def runModule[T](
      source: String,
      dagName: String,
      inputs: Map[String, CValue],
      outputName: String
  )(extract: CValue => T): T = {
    val test = for {
      constellation <- createConstellation
      compiled = compiler.compile(source, dagName).toOption.get
      sig <- constellation.run(compiled.pipeline, inputs)
    } yield sig.outputs.get(outputName)

    val result = test.unsafeRunSync()
    result shouldBe defined
    extract(result.get)
  }

  // ========== Uppercase Tests ==========

  "Uppercase" should "convert text to uppercase" in {
    val source = """
      in text: String
      result = Uppercase(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("hello world"))

    val result = runModule[String](source, "upper-test", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "HELLO WORLD"
  }

  it should "handle empty string" in {
    val source = """
      in text: String
      result = Uppercase(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString(""))

    val result = runModule[String](source, "upper-empty", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe ""
  }

  it should "handle already uppercase text" in {
    val source = """
      in text: String
      result = Uppercase(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("ALREADY UPPER"))

    val result = runModule[String](source, "upper-noop", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "ALREADY UPPER"
  }

  it should "handle mixed case and special characters" in {
    val source = """
      in text: String
      result = Uppercase(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("Hello, World! 123"))

    val result = runModule[String](source, "upper-mixed", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "HELLO, WORLD! 123"
  }

  // ========== Lowercase Tests ==========

  "Lowercase" should "convert text to lowercase" in {
    val source = """
      in text: String
      result = Lowercase(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("HELLO WORLD"))

    val result = runModule[String](source, "lower-test", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "hello world"
  }

  it should "handle empty string" in {
    val source = """
      in text: String
      result = Lowercase(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString(""))

    val result = runModule[String](source, "lower-empty", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe ""
  }

  it should "handle mixed case" in {
    val source = """
      in text: String
      result = Lowercase(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("HeLLo WoRLd"))

    val result = runModule[String](source, "lower-mixed", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "hello world"
  }

  // ========== Trim Tests ==========

  "Trim" should "remove leading and trailing whitespace" in {
    val source = """
      in text: String
      result = Trim(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("  hello  "))

    val result = runModule[String](source, "trim-test", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "hello"
  }

  it should "handle empty string" in {
    val source = """
      in text: String
      result = Trim(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString(""))

    val result = runModule[String](source, "trim-empty", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe ""
  }

  it should "handle whitespace-only string" in {
    val source = """
      in text: String
      result = Trim(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("   \t\n  "))

    val result = runModule[String](source, "trim-whitespace", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe ""
  }

  it should "preserve internal whitespace" in {
    val source = """
      in text: String
      result = Trim(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("  hello   world  "))

    val result = runModule[String](source, "trim-internal", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "hello   world"
  }

  // ========== Replace Tests ==========

  "Replace" should "replace all occurrences" in {
    val source = """
      in text: String
      in find: String
      in replace: String
      result = Replace(text, find, replace)
      out result
    """
    val inputs = Map(
      "text"    -> CValue.CString("hello hello hello"),
      "find"    -> CValue.CString("hello"),
      "replace" -> CValue.CString("hi")
    )

    val result = runModule[String](source, "replace-test", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "hi hi hi"
  }

  it should "handle no matches" in {
    val source = """
      in text: String
      in find: String
      in replace: String
      result = Replace(text, find, replace)
      out result
    """
    val inputs = Map(
      "text"    -> CValue.CString("hello world"),
      "find"    -> CValue.CString("xyz"),
      "replace" -> CValue.CString("abc")
    )

    val result = runModule[String](source, "replace-none", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "hello world"
  }

  it should "handle empty replacement" in {
    val source = """
      in text: String
      in find: String
      in replace: String
      result = Replace(text, find, replace)
      out result
    """
    val inputs = Map(
      "text"    -> CValue.CString("hello world"),
      "find"    -> CValue.CString("o"),
      "replace" -> CValue.CString("")
    )

    val result = runModule[String](source, "replace-delete", inputs, "result") {
      case CValue.CString(v) => v
    }
    result shouldBe "hell wrld"
  }

  // ========== WordCount Tests ==========

  "WordCount" should "count words correctly" in {
    val source = """
      in text: String
      result = WordCount(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("hello world foo bar"))

    val result = runModule[Long](source, "wordcount-test", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 4L
  }

  it should "return 0 for empty string" in {
    val source = """
      in text: String
      result = WordCount(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString(""))

    val result = runModule[Long](source, "wordcount-empty", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 0L
  }

  it should "handle single word" in {
    val source = """
      in text: String
      result = WordCount(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("hello"))

    val result = runModule[Long](source, "wordcount-single", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 1L
  }

  it should "handle multiple spaces between words" in {
    val source = """
      in text: String
      result = WordCount(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("hello    world"))

    val result = runModule[Long](source, "wordcount-spaces", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 2L
  }

  it should "return 0 for whitespace-only string" in {
    val source = """
      in text: String
      result = WordCount(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("   \t\n  "))

    val result = runModule[Long](source, "wordcount-whitespace", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 0L
  }

  // ========== TextLength Tests ==========

  "TextLength" should "count characters correctly" in {
    val source = """
      in text: String
      result = TextLength(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("hello"))

    val result = runModule[Long](source, "length-test", inputs, "result") { case CValue.CInt(v) =>
      v
    }
    result shouldBe 5L
  }

  it should "return 0 for empty string" in {
    val source = """
      in text: String
      result = TextLength(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString(""))

    val result = runModule[Long](source, "length-empty", inputs, "result") { case CValue.CInt(v) =>
      v
    }
    result shouldBe 0L
  }

  it should "count spaces and special characters" in {
    val source = """
      in text: String
      result = TextLength(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("a b c!"))

    val result = runModule[Long](source, "length-special", inputs, "result") {
      case CValue.CInt(v) => v
    }
    result shouldBe 6L
  }

  // ========== Contains Tests ==========

  "Contains" should "return true when substring exists" in {
    val source = """
      in text: String
      in substring: String
      result = Contains(text, substring)
      out result
    """
    val inputs = Map(
      "text"      -> CValue.CString("hello world"),
      "substring" -> CValue.CString("world")
    )

    val result = runModule[Boolean](source, "contains-true", inputs, "result") {
      case CValue.CBoolean(v) => v
    }
    result shouldBe true
  }

  it should "return false when substring does not exist" in {
    val source = """
      in text: String
      in substring: String
      result = Contains(text, substring)
      out result
    """
    val inputs = Map(
      "text"      -> CValue.CString("hello world"),
      "substring" -> CValue.CString("xyz")
    )

    val result = runModule[Boolean](source, "contains-false", inputs, "result") {
      case CValue.CBoolean(v) => v
    }
    result shouldBe false
  }

  it should "be case-sensitive" in {
    val source = """
      in text: String
      in substring: String
      result = Contains(text, substring)
      out result
    """
    val inputs = Map(
      "text"      -> CValue.CString("Hello World"),
      "substring" -> CValue.CString("hello")
    )

    val result = runModule[Boolean](source, "contains-case", inputs, "result") {
      case CValue.CBoolean(v) => v
    }
    result shouldBe false
  }

  it should "return true for empty substring" in {
    val source = """
      in text: String
      in substring: String
      result = Contains(text, substring)
      out result
    """
    val inputs = Map(
      "text"      -> CValue.CString("hello"),
      "substring" -> CValue.CString("")
    )

    val result = runModule[Boolean](source, "contains-empty-sub", inputs, "result") {
      case CValue.CBoolean(v) => v
    }
    result shouldBe true
  }

  // ========== SplitLines Tests ==========

  "SplitLines" should "split text by newlines" in {
    val source = """
      in text: String
      result = SplitLines(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("line1\nline2\nline3"))

    val result = runModule[Vector[String]](source, "splitlines-test", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CString(v) => v }
    }
    result shouldBe Vector("line1", "line2", "line3")
  }

  it should "handle single line" in {
    val source = """
      in text: String
      result = SplitLines(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString("single line"))

    val result = runModule[Vector[String]](source, "splitlines-single", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CString(v) => v }
    }
    result shouldBe Vector("single line")
  }

  it should "handle empty string" in {
    val source = """
      in text: String
      result = SplitLines(text)
      out result
    """
    val inputs = Map("text" -> CValue.CString(""))

    val result = runModule[Vector[String]](source, "splitlines-empty", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CString(v) => v }
    }
    result shouldBe Vector("")
  }

  // ========== Split Tests ==========

  "Split" should "split text by delimiter" in {
    val source = """
      in text: String
      in delimiter: String
      result = Split(text, delimiter)
      out result
    """
    val inputs = Map(
      "text"      -> CValue.CString("a,b,c,d"),
      "delimiter" -> CValue.CString(",")
    )

    val result = runModule[Vector[String]](source, "split-test", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CString(v) => v }
    }
    result shouldBe Vector("a", "b", "c", "d")
  }

  it should "handle multi-character delimiter" in {
    val source = """
      in text: String
      in delimiter: String
      result = Split(text, delimiter)
      out result
    """
    val inputs = Map(
      "text"      -> CValue.CString("a::b::c"),
      "delimiter" -> CValue.CString("::")
    )

    val result = runModule[Vector[String]](source, "split-multi", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CString(v) => v }
    }
    result shouldBe Vector("a", "b", "c")
  }

  it should "handle no matches" in {
    val source = """
      in text: String
      in delimiter: String
      result = Split(text, delimiter)
      out result
    """
    val inputs = Map(
      "text"      -> CValue.CString("hello world"),
      "delimiter" -> CValue.CString(",")
    )

    val result = runModule[Vector[String]](source, "split-none", inputs, "result") {
      case CValue.CList(values, _) => values.map { case CValue.CString(v) => v }
    }
    result shouldBe Vector("hello world")
  }

  // ========== Module Metadata Tests ==========

  "TextModules.all" should "contain all text modules" in {
    val moduleNames = TextModules.all.map(_.spec.name)

    moduleNames should contain("Uppercase")
    moduleNames should contain("Lowercase")
    moduleNames should contain("Trim")
    moduleNames should contain("Replace")
    moduleNames should contain("WordCount")
    moduleNames should contain("TextLength")
    moduleNames should contain("Contains")
    moduleNames should contain("SplitLines")
    moduleNames should contain("Split")
  }

  it should "have correct module count" in {
    TextModules.all should have size 9
  }

  "Each text module" should "have valid metadata" in {
    TextModules.all.foreach { module =>
      module.spec.name should not be empty
      module.spec.metadata.description should not be empty
      module.spec.metadata.majorVersion should be >= 0
      module.spec.metadata.minorVersion should be >= 0
    }
  }

  it should "have text-related tags" in {
    TextModules.all.foreach { module =>
      module.spec.metadata.tags should not be empty
      module.spec.metadata.tags.exists(t =>
        t == "text" || t == "transform" || t == "analysis" || t == "split"
      ) shouldBe true
    }
  }
}
