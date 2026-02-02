package io.constellation.lang.compiler

import io.constellation.lang.ast.{CompileError, Span}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ErrorFormatterTest extends AnyFlatSpec with Matchers {

  val sampleSource: String =
    """in text: String
      |in count: Int
      |result = Uppercase(textt)
      |out result""".stripMargin

  // ========== format() Tests ==========

  "ErrorFormatter.format" should "include error code in output" in {
    val formatter = new ErrorFormatter(sampleSource)
    val error     = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))

    val formatted = formatter.format(error)

    formatted.code shouldBe "E001"
    formatted.title shouldBe "Undefined variable"
  }

  it should "include location information" in {
    val formatter = new ErrorFormatter(sampleSource)
    val error     = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))

    val formatted = formatter.format(error)

    formatted.location should include("line")
    formatted.location should include("column")
  }

  it should "include code snippet with underline" in {
    val formatter = new ErrorFormatter(sampleSource)
    val error     = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))

    val formatted = formatter.format(error)

    formatted.snippet should include("Uppercase(textt)")
    formatted.snippet should include("^")
  }

  it should "include explanation" in {
    val formatter = new ErrorFormatter(sampleSource)
    val error     = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))

    val formatted = formatter.format(error)

    formatted.explanation should not be empty
    formatted.explanation should include("declared")
  }

  it should "include suggestions when context is provided" in {
    val formatter = new ErrorFormatter(sampleSource)
    val error     = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))
    val context   = SuggestionContext(definedVariables = List("text", "count"))

    val formatted = formatter.format(error, context)

    formatted.suggestions should not be empty
    formatted.suggestions.exists(_.contains("text")) shouldBe true
  }

  it should "include documentation URL" in {
    val formatter = new ErrorFormatter(sampleSource)
    val error     = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))

    val formatted = formatter.format(error)

    formatted.docUrl shouldBe defined
    formatted.docUrl.get should include("constellation-engine.dev")
  }

  it should "handle errors without span" in {
    val formatter = new ErrorFormatter(sampleSource)
    val error     = CompileError.InternalError("Something went wrong", None)

    val formatted = formatter.format(error)

    formatted.location should include("unknown")
    formatted.snippet shouldBe empty
  }

  // ========== Category Tests ==========

  "ErrorFormatter" should "categorize reference errors correctly" in {
    val formatter = new ErrorFormatter(sampleSource)

    val varError = CompileError.UndefinedVariable("x", None)
    formatter.format(varError).category shouldBe ErrorCategory.Reference

    val funcError = CompileError.UndefinedFunction("Foo", None)
    formatter.format(funcError).category shouldBe ErrorCategory.Reference

    val typeError = CompileError.UndefinedType("Bar", None)
    formatter.format(typeError).category shouldBe ErrorCategory.Reference
  }

  it should "categorize type errors correctly" in {
    val formatter = new ErrorFormatter(sampleSource)

    val mismatchError = CompileError.TypeMismatch("String", "Int", None)
    formatter.format(mismatchError).category shouldBe ErrorCategory.Type

    val mergeError = CompileError.IncompatibleMerge("String", "Int", None)
    formatter.format(mergeError).category shouldBe ErrorCategory.Type
  }

  it should "categorize syntax errors correctly" in {
    val formatter = new ErrorFormatter(sampleSource)

    val parseError = CompileError.ParseError("Unexpected token", None)
    formatter.format(parseError).category shouldBe ErrorCategory.Syntax
  }

  it should "categorize internal errors correctly" in {
    val formatter = new ErrorFormatter(sampleSource)

    val internalError = CompileError.InternalError("Oops", None)
    formatter.format(internalError).category shouldBe ErrorCategory.Internal
  }

  // ========== toPlainText Tests ==========

  "FormattedError.toPlainText" should "produce readable output" in {
    val formatter = new ErrorFormatter(sampleSource)
    val error     = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))
    val context   = SuggestionContext(definedVariables = List("text", "count"))

    val formatted = formatter.format(error, context)
    val plainText = formatted.toPlainText

    plainText should include("Error E001")
    plainText should include("Undefined variable")
    plainText should include("textt")
    plainText should include("→") // Suggestions marker
  }

  // ========== toMarkdown Tests ==========

  "FormattedError.toMarkdown" should "produce markdown formatted output" in {
    val formatter = new ErrorFormatter(sampleSource)
    val error     = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))
    val context   = SuggestionContext(definedVariables = List("text", "count"))

    val formatted = formatter.format(error, context)
    val markdown  = formatted.toMarkdown

    markdown should include("**Error E001")
    markdown should include("```")
    markdown should include("**Suggestions:**")
  }

  // ========== toOneLine Tests ==========

  "FormattedError.toOneLine" should "produce concise output" in {
    val formatter = new ErrorFormatter(sampleSource)
    val error     = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))

    val formatted = formatter.format(error)
    val oneLine   = formatted.toOneLine

    oneLine shouldBe "E001: Undefined variable: textt"
  }

  // ========== formatAll Tests ==========

  "ErrorFormatter.formatAll" should "format multiple errors" in {
    val formatter = new ErrorFormatter(sampleSource)
    val errors = List(
      CompileError.UndefinedVariable("x", None),
      CompileError.UndefinedFunction("Foo", None)
    )

    val formatted = formatter.formatAll(errors)

    formatted should have length 2
    formatted(0).code shouldBe "E001"
    formatted(1).code shouldBe "E002"
  }

  // ========== Edge Cases ==========

  "ErrorFormatter" should "handle empty source" in {
    val formatter = new ErrorFormatter("")
    val error     = CompileError.UndefinedVariable("x", Some(Span(0, 1)))

    val formatted = formatter.format(error)

    formatted.code shouldBe "E001"
    // Should not crash
  }

  it should "handle span at end of file" in {
    val source    = "in x: String"
    val formatter = new ErrorFormatter(source)
    val error     = CompileError.UndefinedVariable("String", Some(Span(6, 12)))

    val formatted = formatter.format(error)

    formatted.snippet should not be empty
  }

  it should "handle multi-line source" in {
    // Source: "line1\nline2\nline3\nline4\nline5"
    // Offsets: line1=0-4, \n=5, line2=6-10, \n=11, line3=12-16, \n=17, line4=18-22, \n=23, line5=24-28
    val source    = "line1\nline2\nline3\nline4\nline5"
    val formatter = new ErrorFormatter(source)
    val error     = CompileError.UndefinedVariable("x", Some(Span(12, 17))) // "line3" at offset 12

    val formatted = formatter.format(error)

    // Should show context around line 3 (where offset 12 is)
    formatted.snippet should include("line2") // Context line before
    formatted.snippet should include("line3") // Error line
    formatted.snippet should include("line4") // Context line after
  }
}
