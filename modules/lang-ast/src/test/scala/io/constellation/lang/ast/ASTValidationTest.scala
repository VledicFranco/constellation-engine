package io.constellation.lang.ast

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Edge case and validation tests for AST components.
  *
  * This test suite complements ASTTest.scala by focusing on boundary conditions, error handling,
  * and semantic validation that aren't covered by structural tests. Targets Phase 2 of the
  * strategic test coverage improvement plan.
  *
  * Coverage areas:
  *   - QualifiedName edge cases (empty parts, boundary conditions)
  *   - Span validation (negative values, invalid ranges, overlapping)
  *   - LineMap with CRLF, Unicode, and edge cases
  *   - CompileError formatting with complex source snippets
  *   - SourceFile boundary conditions
  *   - Duration/Rate calculations
  *   - ModuleCallOptions validation
  */
class ASTValidationTest extends AnyFlatSpec with Matchers {

  // ===== QualifiedName Edge Cases =====

  "QualifiedName with empty parts list" should "handle localName gracefully" in {
    val qn = QualifiedName(List())
    assertThrows[NoSuchElementException] {
      qn.localName // Should throw because parts.last fails on empty list
    }
  }

  it should "have empty fullName" in {
    val qn = QualifiedName(List())
    qn.fullName shouldBe ""
  }

  it should "have no namespace" in {
    val qn = QualifiedName(List())
    qn.namespace shouldBe None
  }

  it should "not be simple" in {
    val qn = QualifiedName(List())
    qn.isSimple shouldBe false
  }

  "QualifiedName with empty string parts" should "create valid but unusual names" in {
    val qn = QualifiedName(List("", "foo"))
    qn.fullName shouldBe ".foo"
    qn.localName shouldBe "foo"
    qn.namespace shouldBe Some("")
  }

  "QualifiedName.fromString with empty string" should "create single empty part" in {
    val qn = QualifiedName.fromString("")
    qn.parts shouldBe List("")
    qn.isSimple shouldBe true
  }

  it should "handle strings with consecutive dots" in {
    val qn = QualifiedName.fromString("a..b")
    qn.parts shouldBe List("a", "", "b")
    qn.fullName shouldBe "a..b"
  }

  it should "handle leading and trailing dots" in {
    val leadingDot  = QualifiedName.fromString(".foo")
    val trailingDot = QualifiedName.fromString("foo.")

    leadingDot.parts shouldBe List("", "foo")
    // split drops trailing empty strings, so "foo." becomes List("foo")
    trailingDot.parts shouldBe List("foo")
  }

  "QualifiedName with very long namespace" should "handle correctly" in {
    val parts = (1 to 100).map(i => s"part$i").toList
    val qn    = QualifiedName(parts)
    qn.parts.size shouldBe 100
    qn.localName shouldBe "part100"
    qn.namespace.get should startWith("part1.")
  }

  // ===== Span Edge Cases =====

  "Span with negative start" should "still function" in {
    val span = Span(-5, 10)
    span.start shouldBe -5
    span.end shouldBe 10
    span.length shouldBe 15
    span.point shouldBe -5
  }

  "Span with negative end" should "have negative length" in {
    val span = Span(10, -5)
    span.length shouldBe -15
  }

  "Span with start > end" should "have negative length" in {
    val span = Span(20, 10)
    span.length shouldBe -10
    span.isEmpty shouldBe false // Only true when start == end
  }

  "Span with very large offsets" should "handle correctly" in {
    val span = Span(Int.MaxValue - 100, Int.MaxValue)
    span.length shouldBe 100
    span.contains(Int.MaxValue - 50) shouldBe true
    span.contains(Int.MaxValue) shouldBe false // end is exclusive
  }

  "Span.contains with boundary values" should "handle edge correctly" in {
    val span = Span(10, 20)
    span.contains(10) shouldBe true  // inclusive start
    span.contains(20) shouldBe false // exclusive end
    span.contains(19) shouldBe true  // last included
  }

  "Overlapping spans" should "be detectable via contains" in {
    val span1 = Span(10, 20)
    val span2 = Span(15, 25)

    // span1 contains span2.start
    span1.contains(span2.start) shouldBe true
    // span2 contains span1.end - 1
    span2.contains(span1.end - 1) shouldBe true
  }

  "Adjacent spans" should "not overlap" in {
    val span1 = Span(10, 20)
    val span2 = Span(20, 30)

    span1.contains(span2.start) shouldBe false // end is exclusive
    span2.contains(span1.end - 1) shouldBe false
  }

  // ===== LineMap Edge Cases =====

  "LineMap.fromSource with CRLF line endings" should "handle Windows line endings" in {
    val lm = LineMap.fromSource("hello\r\nworld\r\n")
    // \r\n is 2 characters, but only \n starts a new line
    lm.lineCount shouldBe 3                      // "hello\r\n", "world\r\n", ""
    lm.offsetToLineCol(0) shouldBe LineCol(1, 1) // 'h'
    lm.offsetToLineCol(5) shouldBe LineCol(1, 6) // '\r'
    lm.offsetToLineCol(6) shouldBe LineCol(1, 7) // '\n'
    lm.offsetToLineCol(7) shouldBe LineCol(2, 1) // 'w'
  }

  "LineMap.fromSource with mixed line endings" should "handle LF and CRLF" in {
    val lm = LineMap.fromSource("line1\nline2\r\nline3\n")
    lm.lineCount shouldBe 4
    lm.offsetToLineCol(6) shouldBe LineCol(2, 1)  // after first \n
    lm.offsetToLineCol(13) shouldBe LineCol(3, 1) // after \r\n
  }

  "LineMap.fromSource with Unicode characters" should "handle multi-byte correctly" in {
    // "café" is 4 characters in Scala (c, a, f, é), \n is character 5
    val lm = LineMap.fromSource("café\nworld")
    lm.offsetToLineCol(4) shouldBe LineCol(1, 5) // '\n' position
    lm.offsetToLineCol(5) shouldBe LineCol(2, 1) // 'w' in world (after \n)
    lm.offsetToLineCol(6) shouldBe LineCol(2, 2) // 'o' in world
  }

  "LineMap.fromSource with only newlines" should "create multiple empty lines" in {
    val lm = LineMap.fromSource("\n\n\n")
    lm.lineCount shouldBe 4 // "", "", "", ""
    lm.offsetToLineCol(0) shouldBe LineCol(1, 1)
    lm.offsetToLineCol(1) shouldBe LineCol(2, 1)
    lm.offsetToLineCol(2) shouldBe LineCol(3, 1)
    lm.offsetToLineCol(3) shouldBe LineCol(4, 1)
  }

  "LineMap.offsetToLineCol with offset at end of file" should "return last position" in {
    val lm = LineMap.fromSource("hello")
    lm.offsetToLineCol(5) shouldBe LineCol(1, 6) // past last character
  }

  "LineMap.offsetToLineCol with offset beyond end" should "return position past end" in {
    val lm = LineMap.fromSource("hello")
    // Binary search will return a position, though it's out of bounds
    lm.offsetToLineCol(100) shouldBe LineCol(1, 101)
  }

  "LineMap with very long line" should "handle large column numbers" in {
    val longLine = "a" * 10000
    val lm       = LineMap.fromSource(longLine)
    lm.lineCount shouldBe 1
    lm.offsetToLineCol(5000) shouldBe LineCol(1, 5001)
    lm.offsetToLineCol(9999) shouldBe LineCol(1, 10000)
  }

  // ===== SourceFile Edge Cases =====

  "SourceFile with empty content" should "handle extractLine correctly" in {
    val sf = SourceFile("empty.cst", "")
    sf.extractLine(1) shouldBe ""
  }

  "SourceFile.extractLine with out of bounds line number" should "throw exception" in {
    val sf = SourceFile("test.cst", "line1\nline2")
    assertThrows[ArrayIndexOutOfBoundsException] {
      sf.extractLine(0) // lines are 1-based
    }
    assertThrows[ArrayIndexOutOfBoundsException] {
      sf.extractLine(3) // only 2 lines
    }
  }

  "SourceFile.extractSnippet with span at start of file" should "show first line" in {
    val sf      = SourceFile("test.cst", "hello world")
    val snippet = sf.extractSnippet(Span(0, 5))
    snippet should include("1")
    snippet should include("hello world")
    snippet should include("^^^^^")
  }

  "SourceFile.extractSnippet with span at end of file" should "show last line" in {
    val sf      = SourceFile("test.cst", "hello world")
    val snippet = sf.extractSnippet(Span(6, 11))
    snippet should include("1")
    snippet should include("hello world")
    snippet should include("^^^^^")
  }

  "SourceFile.extractSnippet with very long line" should "not truncate pointer" in {
    val longLine = "a" * 1000
    val sf       = SourceFile("test.cst", longLine)
    val snippet  = sf.extractSnippet(Span(500, 510))
    snippet should include("1")
    snippet should include(longLine)
    snippet should include("^^^^^^^^^^")
  }

  "SourceFile.extractSnippet with Unicode" should "handle multi-byte characters" in {
    val sf      = SourceFile("test.cst", "hello 世界 world")
    val snippet = sf.extractSnippet(Span(6, 12)) // "世界" in UTF-8
    snippet should include("1")
    snippet should include("hello 世界 world")
  }

  "SourceFile with CRLF line endings" should "extract lines correctly" in {
    val sf = SourceFile("test.cst", "line1\r\nline2\r\nline3")
    sf.extractLine(1) shouldBe "line1\r"
    sf.extractLine(2) shouldBe "line2\r"
    sf.extractLine(3) shouldBe "line3"
  }

  // ===== CompileError Edge Cases =====

  "CompileError.format with None span" should "show generic error" in {
    val err = CompileError.ParseError("General error", None)
    err.format shouldBe "Error: General error"
  }

  "CompileError.format with zero-length span" should "show position" in {
    val err = CompileError.TypeError("Empty span error", Some(Span(10, 10)))
    err.format shouldBe "Error at [10..10): Empty span error"
  }

  "CompileError.formatWithSource with span at line boundary" should "show correct line" in {
    val sf        = SourceFile("test.cst", "line1\nline2\nline3")
    val err       = CompileError.UndefinedVariable("x", Some(Span(6, 7))) // first char of line2
    val formatted = err.formatWithSource(sf)

    formatted should include("test.cst")
    formatted should include("2:") // line 2
    formatted should include("line2")
  }

  "CompileError.formatWithSource with multiline source" should "show correct line" in {
    // Create source with explicit newlines to avoid stripMargin ambiguity
    val source = "line1\nline2\nline3\nline4"
    val sf     = SourceFile("test.cst", source)
    // Span(12, 17) = "line3" which is line 3
    val err       = CompileError.TypeError("Error on line 3", Some(Span(12, 17)))
    val formatted = err.formatWithSource(sf)

    formatted should include("test.cst")
    formatted should include("3:") // line 3
    formatted should include("line3")
  }

  "CompileError.formatWithSource with very long line" should "include full line" in {
    val longLine  = "a" * 500 + "ERROR" + "b" * 500
    val sf        = SourceFile("test.cst", longLine)
    val err       = CompileError.ParseError("Error in middle", Some(Span(500, 505)))
    val formatted = err.formatWithSource(sf)

    formatted should include(longLine)
    formatted should include("ERROR")
  }

  "CompileError.TypeMismatch" should "format with expected and actual types" in {
    val err = CompileError.TypeMismatch("Int", "String", Some(Span(0, 5)))
    err.message shouldBe "Type mismatch: expected Int, got String"
    err.format should include("Type mismatch")
    err.format should include("[0..5)")
  }

  "CompileError.InvalidProjection" should "list available fields" in {
    val err =
      CompileError.InvalidProjection("missing", List("id", "name", "value"), Some(Span(0, 7)))
    err.message should include("field 'missing' not found")
    err.message should include("Available: id, name, value")
  }

  "CompileError.IncompatibleMerge" should "provide helpful message" in {
    val err = CompileError.IncompatibleMerge("Record{id:Int}", "List<String>", Some(Span(0, 5)))
    err.message should include("Cannot merge types")
    err.message should include("Record{id:Int} + List<String>")
    err.message should include("Two records")
    err.message should include("Candidates")
  }

  "CompileError.AmbiguousFunction" should "list candidates" in {
    val err = CompileError.AmbiguousFunction(
      "add",
      List("stdlib.math.add", "custom.math.add", "legacy.add"),
      Some(Span(0, 3))
    )
    err.message should include("Ambiguous function 'add'")
    err.message should include("stdlib.math.add")
    err.message should include("custom.math.add")
    err.message should include("legacy.add")
  }

  "CompileError.InternalError" should "indicate internal failure" in {
    val err = CompileError.InternalError("Unexpected compiler state", Some(Span(10, 20)))
    err.message shouldBe "Internal compiler error: Unexpected compiler state"
    err.format should include("Internal compiler error")
  }

  // ===== Duration and Rate Validation =====

  "Duration.toMillis" should "convert milliseconds correctly" in {
    Duration(100, DurationUnit.Milliseconds).toMillis shouldBe 100L
  }

  it should "convert seconds correctly" in {
    Duration(30, DurationUnit.Seconds).toMillis shouldBe 30000L
  }

  it should "convert minutes correctly" in {
    Duration(5, DurationUnit.Minutes).toMillis shouldBe 300000L
  }

  it should "convert hours correctly" in {
    Duration(2, DurationUnit.Hours).toMillis shouldBe 7200000L
  }

  it should "convert days correctly" in {
    Duration(1, DurationUnit.Days).toMillis shouldBe 86400000L
  }

  it should "handle zero duration" in {
    Duration(0, DurationUnit.Seconds).toMillis shouldBe 0L
  }

  it should "handle very large durations" in {
    // Don't overflow Long.MaxValue
    Duration(365, DurationUnit.Days).toMillis shouldBe 31536000000L
  }

  "Duration.toString" should "format correctly for each unit" in {
    Duration(100, DurationUnit.Milliseconds).toString shouldBe "100ms"
    Duration(30, DurationUnit.Seconds).toString shouldBe "30s"
    Duration(5, DurationUnit.Minutes).toString shouldBe "5min"
    Duration(2, DurationUnit.Hours).toString shouldBe "2h"
    Duration(1, DurationUnit.Days).toString shouldBe "1d"
  }

  "Rate.toString" should "format as count/duration" in {
    val rate = Rate(100, Duration(1, DurationUnit.Minutes))
    rate.toString shouldBe "100/1min"
  }

  it should "handle large rates" in {
    val rate = Rate(10000, Duration(1, DurationUnit.Seconds))
    rate.toString shouldBe "10000/1s"
  }

  // ===== ModuleCallOptions Validation =====

  "ModuleCallOptions.empty" should "have isEmpty true" in {
    ModuleCallOptions.empty.isEmpty shouldBe true
  }

  "ModuleCallOptions with retry" should "not be empty" in {
    val opts = ModuleCallOptions(retry = Some(3))
    opts.isEmpty shouldBe false
  }

  "ModuleCallOptions with timeout" should "not be empty" in {
    val opts = ModuleCallOptions(timeout = Some(Duration(30, DurationUnit.Seconds)))
    opts.isEmpty shouldBe false
  }

  "ModuleCallOptions with cache" should "not be empty" in {
    val opts = ModuleCallOptions(cache = Some(Duration(5, DurationUnit.Minutes)))
    opts.isEmpty shouldBe false
  }

  "ModuleCallOptions with priority" should "not be empty" in {
    val opts = ModuleCallOptions(priority = Some(Left(PriorityLevel.High)))
    opts.isEmpty shouldBe false
  }

  "ModuleCallOptions with custom priority" should "not be empty" in {
    val opts = ModuleCallOptions(priority = Some(Right(CustomPriority(100))))
    opts.isEmpty shouldBe false
  }

  "ModuleCallOptions with multiple options" should "not be empty" in {
    val opts = ModuleCallOptions(
      retry = Some(3),
      timeout = Some(Duration(30, DurationUnit.Seconds)),
      cache = Some(Duration(5, DurationUnit.Minutes))
    )
    opts.isEmpty shouldBe false
  }

  "ModuleCallOptions with all None" should "be empty" in {
    val opts = ModuleCallOptions(
      retry = None,
      timeout = None,
      delay = None,
      backoff = None,
      fallback = None,
      cache = None,
      cacheBackend = None,
      throttle = None,
      concurrency = None,
      onError = None,
      lazyEval = None,
      priority = None
    )
    opts.isEmpty shouldBe true
  }

  // ===== Located Edge Cases =====

  "Located.map" should "preserve span through transformation" in {
    val loc    = Located(10, Span(5, 10))
    val result = loc.map(_ * 2).map(_ + 5).map(_.toString)
    result.value shouldBe "25"
    result.span shouldBe Span(5, 10)
  }

  it should "work with complex transformations" in {
    val loc = Located(List(1, 2, 3), Span(0, 10))
    val result = loc
      .map(_.sum)
      .map(_ * 10)
    result.value shouldBe 60
    result.span shouldBe Span(0, 10)
  }

  // ===== Enum Edge Cases =====

  "DurationUnit" should "have correct enum values" in {
    DurationUnit.values.toList should contain allOf (
      DurationUnit.Milliseconds,
      DurationUnit.Seconds,
      DurationUnit.Minutes,
      DurationUnit.Hours,
      DurationUnit.Days
    )
  }

  "BackoffStrategy" should "have correct enum values" in {
    BackoffStrategy.values.toList should contain allOf (
      BackoffStrategy.Fixed,
      BackoffStrategy.Linear,
      BackoffStrategy.Exponential
    )
  }

  "ErrorStrategy" should "have correct enum values" in {
    ErrorStrategy.values.toList should contain allOf (
      ErrorStrategy.Propagate,
      ErrorStrategy.Skip,
      ErrorStrategy.Log,
      ErrorStrategy.Wrap
    )
  }

  "PriorityLevel" should "have correct enum values" in {
    PriorityLevel.values.toList should contain allOf (
      PriorityLevel.Critical,
      PriorityLevel.High,
      PriorityLevel.Normal,
      PriorityLevel.Low,
      PriorityLevel.Background
    )
  }

  // ===== TypeExpr.Union Edge Cases =====

  "TypeExpr.Union" should "hold multiple type members" in {
    val union = TypeExpr.Union(
      List(
        TypeExpr.Primitive("String"),
        TypeExpr.Primitive("Int"),
        TypeExpr.Primitive("Float")
      )
    )
    union.members.size shouldBe 3
    union.members should contain(TypeExpr.Primitive("String"))
  }

  it should "allow single member union" in {
    val union = TypeExpr.Union(List(TypeExpr.Primitive("String")))
    union.members.size shouldBe 1
  }

  it should "allow empty union" in {
    val union = TypeExpr.Union(List())
    union.members shouldBe empty
  }

  // ===== Annotation Edge Cases =====

  "Annotation.Example" should "hold expression value" in {
    val example = Annotation.Example(
      Located(Expression.StringLit("default value"), Span(0, 13))
    )
    example.value.value shouldBe Expression.StringLit("default value")
    example.value.span shouldBe Span(0, 13)
  }

  it should "work with complex expressions" in {
    val example = Annotation.Example(
      Located(
        Expression.FunctionCall(
          QualifiedName.simple("generateDefault"),
          List()
        ),
        Span(0, 20)
      )
    )
    example.value.value shouldBe a[Expression.FunctionCall]
  }

  // ===== CompareOp, ArithOp, BoolOp Enums =====

  "CompareOp" should "have all comparison operators" in {
    CompareOp.values.toList should contain allOf (
      CompareOp.Eq,
      CompareOp.NotEq,
      CompareOp.Lt,
      CompareOp.Gt,
      CompareOp.LtEq,
      CompareOp.GtEq
    )
  }

  "ArithOp" should "have all arithmetic operators" in {
    ArithOp.values.toList should contain allOf (
      ArithOp.Add,
      ArithOp.Sub,
      ArithOp.Mul,
      ArithOp.Div
    )
  }

  "BoolOp" should "have all boolean operators" in {
    BoolOp.values.toList should contain allOf (
      BoolOp.And,
      BoolOp.Or
    )
  }

  // ===== CustomPriority Edge Cases =====

  "CustomPriority" should "accept any integer value" in {
    CustomPriority(0).value shouldBe 0
    CustomPriority(100).value shouldBe 100
    CustomPriority(-50).value shouldBe -50
    CustomPriority(Int.MaxValue).value shouldBe Int.MaxValue
    CustomPriority(Int.MinValue).value shouldBe Int.MinValue
  }

  // ===== Rate Edge Cases =====

  "Rate with zero count" should "be constructible" in {
    val rate = Rate(0, Duration(1, DurationUnit.Seconds))
    rate.count shouldBe 0
  }

  "Rate with zero duration" should "be constructible" in {
    val rate = Rate(100, Duration(0, DurationUnit.Seconds))
    rate.per.toMillis shouldBe 0L
  }
}
