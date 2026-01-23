# Task 1.4: Improved Error Messages

**Phase:** 1 - Quick Wins
**Effort:** Medium (3-4 days)
**Impact:** Medium (Better developer experience)
**Dependencies:** None
**Blocks:** None

---

## Objective

Enhance compiler error messages with detailed explanations, suggestions, and documentation links to help developers understand and fix issues faster.

---

## Background

### Current Behavior

Error messages are functional but minimal:

```scala
// Current error output
CompileError.UndefinedVariable(
  message = "Undefined variable: textt",
  span = Some(Span(45, 50))
)

// User sees:
// Error at line 3: Undefined variable: textt
```

**Problems:**
- No explanation of WHY the error occurred
- No suggestions for how to fix it
- No "did you mean?" for typos
- No links to documentation

### Desired Output

```
Error E001: Undefined variable
  --> pipeline.cst:3:10
   |
 3 | result = Uppercase(textt)
   |                    ^^^^^
   |
   = The variable 'textt' is used but was never declared.

   Did you mean: 'text' (declared at line 1)?

   Variables must be declared before use, either as:
   - An input: in textt: String
   - An assignment: textt = SomeModule(...)

   See: https://constellation-engine.dev/docs/variables
```

---

## Technical Design

### Error Catalog

Define error codes and explanations:

```scala
sealed trait ErrorCode {
  def code: String
  def title: String
  def explanation(context: ErrorContext): String
  def suggestions(context: ErrorContext): List[String]
  def docUrl: Option[String]
}

case class ErrorContext(
  source: String,
  span: Span,
  additionalInfo: Map[String, Any]
)

object ErrorCodes {
  case object UndefinedVariable extends ErrorCode {
    def code = "E001"
    def title = "Undefined variable"
    def explanation(ctx: ErrorContext) = {
      val varName = ctx.additionalInfo("variableName")
      s"The variable '$varName' is used but was never declared."
    }
    def suggestions(ctx: ErrorContext) = {
      val varName = ctx.additionalInfo("variableName").asInstanceOf[String]
      val availableVars = ctx.additionalInfo("availableVariables").asInstanceOf[List[String]]

      val similar = findSimilar(varName, availableVars)
      similar.map(s => s"Did you mean: '$s'?")
    }
    def docUrl = Some("https://constellation-engine.dev/docs/variables")
  }

  // ... more error codes
}
```

### Error Formatter

```scala
class ErrorFormatter(source: String) {

  def format(error: CompileError): FormattedError = {
    val code = errorToCode(error)
    val context = extractContext(error)

    FormattedError(
      code = code.code,
      title = code.title,
      location = formatLocation(error.span),
      codeSnippet = formatSnippet(error.span),
      explanation = code.explanation(context),
      suggestions = code.suggestions(context),
      docUrl = code.docUrl
    )
  }

  private def formatSnippet(span: Span): String = {
    // Extract relevant lines and add caret markers
  }

  private def findSimilar(target: String, candidates: List[String]): List[String] = {
    // Levenshtein distance-based suggestions
  }
}
```

---

## Deliverables

### Required

- [ ] **`ErrorCode.scala`** - Error code definitions:
  - All existing error types mapped to codes
  - E001-E099: Syntax errors
  - E100-E199: Type errors
  - E200-E299: Reference errors
  - E300-E399: Semantic errors

- [ ] **`ErrorFormatter.scala`** - Error formatting:
  - Code snippet extraction
  - Caret/underline markers
  - Line number formatting
  - Multi-line error support

- [ ] **`Suggestions.scala`** - Suggestion generation:
  - Levenshtein distance for "did you mean"
  - Context-aware suggestions
  - Common mistake patterns

- [ ] **Integration with CompileError**:
  - Update error creation to include context
  - Wire formatter into error output

- [ ] **Unit Tests**:
  - Each error code has correct message
  - Suggestions are helpful
  - Snippet extraction is correct
  - Multi-line errors handled

### Error Code Catalog

| Code | Title | Category |
|------|-------|----------|
| E001 | Undefined variable | Reference |
| E002 | Undefined function | Reference |
| E003 | Undefined type | Reference |
| E010 | Type mismatch | Type |
| E011 | Incompatible types for operator | Type |
| E012 | Invalid field access | Type |
| E013 | Invalid projection | Type |
| E020 | Parse error: unexpected token | Syntax |
| E021 | Parse error: missing token | Syntax |
| E030 | Duplicate definition | Semantic |
| E031 | Circular dependency | Semantic |

---

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/ErrorCode.scala` | **New** | Error code catalog |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/ErrorFormatter.scala` | **New** | Formatting logic |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/Suggestions.scala` | **New** | Suggestion generation |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/CompilerError.scala` | Modify | Add error codes |
| `modules/lang-lsp/src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala` | Modify | Use formatter for diagnostics |
| `modules/lang-compiler/src/test/scala/io/constellation/lang/compiler/ErrorFormatterTest.scala` | **New** | Tests |

---

## Implementation Guide

> **Overview:** 4 steps | ~5 new files | Estimated 3-4 days

### Step 1: Define Error Codes

```scala
// ErrorCode.scala
package io.constellation.lang.compiler

sealed trait ErrorCode {
  def code: String
  def title: String
  def category: ErrorCategory
  def explanation: String
  def docPath: Option[String]
}

enum ErrorCategory {
  case Syntax, Type, Reference, Semantic, Internal
}

object ErrorCodes {

  // Reference Errors (E001-E009)
  case object UndefinedVariable extends ErrorCode {
    val code = "E001"
    val title = "Undefined variable"
    val category = ErrorCategory.Reference
    val explanation = """
      |The variable you're trying to use has not been declared.
      |
      |Variables must be declared before use:
      |  - As an input: in variableName: Type
      |  - As an assignment: variableName = SomeModule(...)
    """.stripMargin.trim
    val docPath = Some("variables")
  }

  case object UndefinedFunction extends ErrorCode {
    val code = "E002"
    val title = "Undefined function"
    val category = ErrorCategory.Reference
    val explanation = """
      |The function you're trying to call is not registered.
      |
      |Make sure the function is:
      |  - Spelled correctly (function names are case-sensitive)
      |  - Registered with the compiler via StdLib or custom modules
      |  - Imported if it's from a namespace: use stdlib.math
    """.stripMargin.trim
    val docPath = Some("functions")
  }

  case object UndefinedType extends ErrorCode {
    val code = "E003"
    val title = "Undefined type"
    val category = ErrorCategory.Reference
    val explanation = """
      |The type you specified is not defined.
      |
      |Built-in types: String, Int, Float, Boolean
      |Collections: List<T>, Map<K, V>, Optional<T>
      |Custom types must be declared: type MyType = { field: Type }
    """.stripMargin.trim
    val docPath = Some("types")
  }

  // Type Errors (E010-E019)
  case object TypeMismatch extends ErrorCode {
    val code = "E010"
    val title = "Type mismatch"
    val category = ErrorCategory.Type
    val explanation = """
      |The actual type does not match the expected type.
      |
      |This often happens when:
      |  - Passing wrong argument type to a function
      |  - Assigning incompatible value to a variable
      |  - Returning wrong type from a conditional
    """.stripMargin.trim
    val docPath = Some("type-system")
  }

  case object IncompatibleOperator extends ErrorCode {
    val code = "E011"
    val title = "Incompatible types for operator"
    val category = ErrorCategory.Type
    val explanation = """
      |The operator cannot be applied to these types.
      |
      |Operators and supported types:
      |  - Arithmetic (+, -, *, /): Int, Float
      |  - Comparison (==, !=, <, >): Int, Float, String
      |  - Boolean (and, or, not): Boolean
      |  - Merge (+): Records
    """.stripMargin.trim
    val docPath = Some("operators")
  }

  case object InvalidFieldAccess extends ErrorCode {
    val code = "E012"
    val title = "Invalid field access"
    val category = ErrorCategory.Type
    val explanation = """
      |The field you're trying to access doesn't exist on this type.
      |
      |Field access (.field) requires a record type with that field.
      |Check that:
      |  - The field name is spelled correctly
      |  - The source expression is a record type
    """.stripMargin.trim
    val docPath = Some("field-access")
  }

  // Syntax Errors (E020-E029)
  case object UnexpectedToken extends ErrorCode {
    val code = "E020"
    val title = "Unexpected token"
    val category = ErrorCategory.Syntax
    val explanation = """
      |The parser encountered a token it didn't expect.
      |
      |Check for:
      |  - Missing or extra parentheses
      |  - Missing commas between arguments
      |  - Typos in keywords
    """.stripMargin.trim
    val docPath = Some("syntax")
  }

  // ... more error codes

  val all: List[ErrorCode] = List(
    UndefinedVariable, UndefinedFunction, UndefinedType,
    TypeMismatch, IncompatibleOperator, InvalidFieldAccess,
    UnexpectedToken
    // ... etc
  )

  def fromCompileError(error: CompileError): ErrorCode = error match {
    case _: CompileError.UndefinedVariable => UndefinedVariable
    case _: CompileError.UndefinedFunction => UndefinedFunction
    case _: CompileError.UndefinedType => UndefinedType
    case _: CompileError.TypeMismatch => TypeMismatch
    // ... etc
    case _ => UnexpectedToken // fallback
  }
}
```

### Step 2: Implement Suggestion Generation

```scala
// Suggestions.scala
package io.constellation.lang.compiler

object Suggestions {

  /**
   * Find strings similar to the target using Levenshtein distance.
   *
   * @param target The string to match
   * @param candidates Available strings to suggest
   * @param maxDistance Maximum edit distance (default 2)
   * @param maxSuggestions Maximum suggestions to return (default 3)
   * @return Sorted list of similar strings
   */
  def findSimilar(
    target: String,
    candidates: List[String],
    maxDistance: Int = 2,
    maxSuggestions: Int = 3
  ): List[String] = {
    candidates
      .map(c => (c, levenshteinDistance(target.toLowerCase, c.toLowerCase)))
      .filter(_._2 <= maxDistance)
      .sortBy(_._2)
      .take(maxSuggestions)
      .map(_._1)
  }

  /**
   * Calculate Levenshtein edit distance between two strings.
   */
  def levenshteinDistance(s1: String, s2: String): Int = {
    val m = s1.length
    val n = s2.length

    val dp = Array.ofDim[Int](m + 1, n + 1)

    for (i <- 0 to m) dp(i)(0) = i
    for (j <- 0 to n) dp(0)(j) = j

    for (i <- 1 to m; j <- 1 to n) {
      val cost = if (s1(i - 1) == s2(j - 1)) 0 else 1
      dp(i)(j) = Math.min(
        Math.min(dp(i - 1)(j) + 1, dp(i)(j - 1) + 1),
        dp(i - 1)(j - 1) + cost
      )
    }

    dp(m)(n)
  }

  /**
   * Generate contextual suggestions based on error type.
   */
  def forError(error: CompileError, context: CompilationContext): List[String] = {
    error match {
      case CompileError.UndefinedVariable(name, _) =>
        val available = context.definedVariables
        val similar = findSimilar(name, available)
        similar.map(s => s"Did you mean: '$s'?") ++
          List(s"Declare the variable: in $name: String")

      case CompileError.UndefinedFunction(name, _) =>
        val available = context.availableFunctions
        val similar = findSimilar(name, available)
        similar.map(s => s"Did you mean: '$s'?") ++
          suggestImports(name, context)

      case CompileError.InvalidFieldAccess(field, actualType, _) =>
        val availableFields = extractFields(actualType)
        val similar = findSimilar(field, availableFields)
        similar.map(s => s"Did you mean: '.$s'?") ++
          List(s"Available fields: ${availableFields.mkString(", ")}")

      case _ => List.empty
    }
  }

  private def suggestImports(name: String, ctx: CompilationContext): List[String] = {
    // Check if function exists in known namespaces
    ctx.allNamespaceFunctions
      .filter(_._2.exists(_.name.equalsIgnoreCase(name)))
      .map { case (ns, _) => s"Try adding: use $ns" }
      .toList
  }

  private def extractFields(typeStr: String): List[String] = {
    // Parse type string to extract field names
    // e.g., "{ name: String, age: Int }" -> ["name", "age"]
    val fieldPattern = """(\w+)\s*:""".r
    fieldPattern.findAllMatchIn(typeStr).map(_.group(1)).toList
  }
}
```

### Step 3: Implement Error Formatter

```scala
// ErrorFormatter.scala
package io.constellation.lang.compiler

case class FormattedError(
  code: String,
  title: String,
  location: String,
  snippet: String,
  explanation: String,
  suggestions: List[String],
  docUrl: Option[String]
) {
  def toPlainText: String = {
    val parts = List(
      s"Error $code: $title",
      location,
      snippet,
      "",
      s"  = $explanation",
      "",
      suggestions.map(s => s"  > $s").mkString("\n"),
      docUrl.map(url => s"\n  See: $url").getOrElse("")
    )
    parts.filter(_.nonEmpty).mkString("\n")
  }

  def toMarkdown: String = {
    val parts = List(
      s"**Error $code: $title**",
      "",
      "```",
      snippet,
      "```",
      "",
      explanation,
      "",
      if (suggestions.nonEmpty)
        "**Suggestions:**\n" + suggestions.map(s => s"- $s").mkString("\n")
      else "",
      docUrl.map(url => s"\n[Documentation]($url)").getOrElse("")
    )
    parts.filter(_.nonEmpty).mkString("\n")
  }
}

class ErrorFormatter(source: String) {
  private val lines = source.split("\n", -1)
  private val lineMap = computeLineMap(source)

  def format(error: CompileError, context: CompilationContext): FormattedError = {
    val errorCode = ErrorCodes.fromCompileError(error)

    FormattedError(
      code = errorCode.code,
      title = errorCode.title,
      location = formatLocation(error.span),
      snippet = formatSnippet(error.span),
      explanation = errorCode.explanation,
      suggestions = Suggestions.forError(error, context),
      docUrl = errorCode.docPath.map(p => s"https://constellation-engine.dev/docs/$p")
    )
  }

  private def formatLocation(span: Option[Span]): String = {
    span match {
      case Some(s) =>
        val (line, col) = offsetToLineCol(s.start)
        s"  --> line $line, column $col"
      case None =>
        "  --> (unknown location)"
    }
  }

  private def formatSnippet(span: Option[Span]): String = {
    span match {
      case Some(s) =>
        val (startLine, startCol) = offsetToLineCol(s.start)
        val (endLine, endCol) = offsetToLineCol(s.end)

        val contextLines = 1 // Lines before/after to show

        val fromLine = Math.max(0, startLine - contextLines - 1)
        val toLine = Math.min(lines.length - 1, endLine + contextLines - 1)

        val lineNumWidth = (toLine + 1).toString.length

        val snippetLines = (fromLine to toLine).map { lineIdx =>
          val lineNum = lineIdx + 1
          val lineContent = lines(lineIdx)
          val prefix = f"$lineNum%${lineNumWidth}d | "

          if (lineIdx >= startLine - 1 && lineIdx <= endLine - 1) {
            // This line has the error
            val underline = buildUnderline(lineIdx, startLine, endLine, startCol, endCol, lineContent.length, lineNumWidth)
            s"$prefix$lineContent\n$underline"
          } else {
            s"$prefix$lineContent"
          }
        }

        snippetLines.mkString("\n")

      case None => ""
    }
  }

  private def buildUnderline(
    lineIdx: Int,
    startLine: Int,
    endLine: Int,
    startCol: Int,
    endCol: Int,
    lineLength: Int,
    lineNumWidth: Int
  ): String = {
    val prefix = " " * lineNumWidth + " | "

    val from = if (lineIdx == startLine - 1) startCol - 1 else 0
    val to = if (lineIdx == endLine - 1) endCol - 1 else lineLength

    prefix + " " * from + "^" * Math.max(1, to - from)
  }

  private def offsetToLineCol(offset: Int): (Int, Int) = {
    var line = 1
    var col = 1
    var pos = 0

    while (pos < offset && pos < source.length) {
      if (source(pos) == '\n') {
        line += 1
        col = 1
      } else {
        col += 1
      }
      pos += 1
    }

    (line, col)
  }

  private def computeLineMap(source: String): Array[Int] = {
    // Map from line number to starting offset
    val offsets = scala.collection.mutable.ArrayBuffer(0)
    for ((c, i) <- source.zipWithIndex) {
      if (c == '\n') offsets += (i + 1)
    }
    offsets.toArray
  }
}
```

### Step 4: Integration

```scala
// Update CompileError to include context
sealed trait CompileError {
  def message: String
  def span: Option[Span]
  def errorCode: ErrorCode = ErrorCodes.fromCompileError(this)
}

// In ConstellationLanguageServer, use formatter for diagnostics
private def createDiagnostic(error: CompileError, source: String, ctx: CompilationContext): Diagnostic = {
  val formatter = new ErrorFormatter(source)
  val formatted = formatter.format(error, ctx)

  val diagnostic = new Diagnostic()
  diagnostic.setRange(spanToRange(error.span))
  diagnostic.setSeverity(DiagnosticSeverity.Error)
  diagnostic.setCode(formatted.code)
  diagnostic.setMessage(s"${formatted.title}\n\n${formatted.explanation}\n\n${formatted.suggestions.mkString("\n")}")
  diagnostic.setSource("constellation")

  diagnostic
}
```

---

## Testing Strategy

### Unit Tests

```scala
class SuggestionsTest extends AnyFlatSpec with Matchers {

  "Suggestions.levenshteinDistance" should "compute correct distances" in {
    Suggestions.levenshteinDistance("cat", "cat") shouldBe 0
    Suggestions.levenshteinDistance("cat", "bat") shouldBe 1
    Suggestions.levenshteinDistance("cat", "cart") shouldBe 1
    Suggestions.levenshteinDistance("cat", "dog") shouldBe 3
    Suggestions.levenshteinDistance("", "abc") shouldBe 3
  }

  "Suggestions.findSimilar" should "find similar strings" in {
    val candidates = List("text", "test", "next", "context", "unrelated")

    Suggestions.findSimilar("textt", candidates) should contain("text")
    Suggestions.findSimilar("txt", candidates) should contain("text")
    Suggestions.findSimilar("xyz", candidates) shouldBe empty
  }

  it should "be case-insensitive" in {
    val candidates = List("Uppercase", "Lowercase")

    Suggestions.findSimilar("uppercase", candidates) should contain("Uppercase")
    Suggestions.findSimilar("LOWERCASE", candidates) should contain("Lowercase")
  }
}

class ErrorFormatterTest extends AnyFlatSpec with Matchers {

  val source = """in text: String
                 |in count: Int
                 |result = Uppercase(textt)
                 |out result""".stripMargin

  "ErrorFormatter" should "format location correctly" in {
    val formatter = new ErrorFormatter(source)
    val error = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))
    val ctx = CompilationContext(definedVariables = List("text", "count"))

    val formatted = formatter.format(error, ctx)

    formatted.location should include("line 3")
    formatted.location should include("column")
  }

  it should "include code snippet with underline" in {
    val formatter = new ErrorFormatter(source)
    val error = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))
    val ctx = CompilationContext(definedVariables = List("text", "count"))

    val formatted = formatter.format(error, ctx)

    formatted.snippet should include("result = Uppercase(textt)")
    formatted.snippet should include("^^^^^")
  }

  it should "include suggestions" in {
    val formatter = new ErrorFormatter(source)
    val error = CompileError.UndefinedVariable("textt", Some(Span(45, 50)))
    val ctx = CompilationContext(definedVariables = List("text", "count"))

    val formatted = formatter.format(error, ctx)

    formatted.suggestions should contain("Did you mean: 'text'?")
  }
}
```

---

## Web Resources

### Error Message Design
- [Elm Compiler Errors](https://elm-lang.org/news/compiler-errors-for-humans) - Gold standard for helpful errors
- [Rust Error Messages](https://blog.rust-lang.org/2016/08/10/Shape-of-errors-to-come.html) - Evolution of Rust's errors
- [TypeScript Error Messages](https://www.typescriptlang.org/docs/handbook/2/narrowing.html) - Type error explanations

### Levenshtein Distance
- [Wikipedia: Levenshtein Distance](https://en.wikipedia.org/wiki/Levenshtein_distance)
- [Fuzzy String Matching](https://blog.usejournal.com/fuzzy-string-matching-in-scala-5b4e30a2c12f) - Scala implementation
- [Apache Commons Text](https://commons.apache.org/proper/commons-text/javadocs/api-release/org/apache/commons/text/similarity/LevenshteinDistance.html) - Java library

### User Experience
- [How to Write Good Error Messages](https://uxplanet.org/how-to-write-good-error-messages-858e4551cd4) - UX perspective
- [Error Message Guidelines](https://www.nngroup.com/articles/error-message-guidelines/) - Nielsen Norman Group

---

## Acceptance Criteria

1. **Functional Requirements**
   - [ ] All error types have codes and explanations
   - [ ] Code snippets show context around error
   - [ ] "Did you mean" suggestions work for typos
   - [ ] Documentation links are valid

2. **Quality Requirements**
   - [ ] Explanations are clear and actionable
   - [ ] Suggestions are actually helpful
   - [ ] Formatting is consistent across all errors
   - [ ] Unit test coverage > 80%

3. **Integration Requirements**
   - [ ] LSP diagnostics use formatted errors
   - [ ] CLI output uses formatted errors
   - [ ] Markdown rendering works in VSCode hover

---

## Notes for Implementer

1. **Study Elm and Rust** - These compilers have excellent error messages. Emulate their style.

2. **Collect real errors** - Run the compiler on various invalid programs to see what errors actually occur.

3. **Test with users** - If possible, get feedback on whether suggestions are actually helpful.

4. **Keep explanations concise** - Long explanations are ignored. Aim for 3-4 sentences max.

5. **Escape special characters** - When including user input in messages, escape it properly for markdown/terminal.
