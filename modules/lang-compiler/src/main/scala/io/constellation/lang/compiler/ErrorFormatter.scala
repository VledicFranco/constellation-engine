package io.constellation.lang.compiler

import io.constellation.lang.ast.{CompileError, SourceFile, Span}

/** Formatted error message with all contextual information.
  *
  * This is the structured output of error formatting, containing
  * all the pieces needed to display a helpful error message.
  */
case class FormattedError(
    code: String,
    title: String,
    category: ErrorCategory,
    location: String,
    snippet: String,
    explanation: String,
    suggestions: List[String],
    docUrl: Option[String],
    rawMessage: String
) {

  /** Format as plain text for terminal output */
  def toPlainText: String = {
    val parts = List(
      s"Error $code: $title",
      location,
      "",
      snippet,
      "",
      s"  $rawMessage",
      "",
      explanation.linesIterator.map(l => s"  $l").mkString("\n"),
      "",
      if (suggestions.nonEmpty)
        suggestions.map(s => s"  → $s").mkString("\n")
      else "",
      docUrl.map(url => s"\n  See: $url").getOrElse("")
    )
    parts.filter(_.nonEmpty).mkString("\n")
  }

  /** Format as markdown for IDE hover/tooltips */
  def toMarkdown: String = {
    val parts = List(
      s"**Error $code: $title**",
      "",
      "```",
      snippet,
      "```",
      "",
      rawMessage,
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

  /** Format as a concise single-line message */
  def toOneLine: String =
    s"$code: $rawMessage"
}

/** Formats compile errors into rich, helpful error messages.
  *
  * Features:
  *   - Error codes with explanations
  *   - Source code snippets with caret markers
  *   - "Did you mean?" suggestions
  *   - Documentation links
  *
  * @param source The source code being compiled
  */
class ErrorFormatter(source: String) {
  private val sourceFile = SourceFile("<input>", source)
  private val lines      = source.split("\n", -1)

  /** Format a compile error with full context.
    *
    * @param error The compile error to format
    * @param context Suggestion context with available symbols
    * @return Formatted error with all contextual information
    */
  def format(error: CompileError, context: SuggestionContext = SuggestionContext.empty): FormattedError = {
    val errorCode = ErrorCodes.fromCompileError(error)

    FormattedError(
      code = errorCode.code,
      title = errorCode.title,
      category = errorCode.category,
      location = formatLocation(error.span),
      snippet = formatSnippet(error.span),
      explanation = errorCode.explanation,
      suggestions = Suggestions.forError(error, context),
      docUrl = errorCode.docUrl,
      rawMessage = error.message
    )
  }

  /** Format multiple errors.
    *
    * @param errors List of compile errors
    * @param context Suggestion context
    * @return List of formatted errors
    */
  def formatAll(
      errors: List[CompileError],
      context: SuggestionContext = SuggestionContext.empty
  ): List[FormattedError] =
    errors.map(format(_, context))

  /** Format location as "line X, column Y" */
  private def formatLocation(span: Option[Span]): String =
    span match {
      case Some(s) =>
        val (startLC, _) = sourceFile.spanToLineCol(s)
        s"  --> line ${startLC.line}, column ${startLC.col}"
      case None =>
        "  --> (unknown location)"
    }

  /** Format source code snippet with caret markers.
    *
    * Shows the error line with context and underlines the error location.
    */
  private def formatSnippet(span: Option[Span]): String =
    span match {
      case Some(s) =>
        val (startLC, endLC) = sourceFile.spanToLineCol(s)

        // Calculate line range to show (error line ± 1 for context)
        val errorLine    = startLC.line
        val fromLine     = Math.max(1, errorLine - 1)
        val toLine       = Math.min(lines.length, errorLine + 1)
        val lineNumWidth = toLine.toString.length.max(3)

        val snippetLines = (fromLine to toLine).flatMap { lineNum =>
          val lineIdx     = lineNum - 1
          val lineContent = if (lineIdx < lines.length) lines(lineIdx) else ""
          val lineNumStr  = lineNum.toString.reverse.padTo(lineNumWidth, ' ').reverse
          val prefix      = s"$lineNumStr │ "

          if (lineNum == errorLine) {
            // This is the error line - add underline
            val startCol    = startLC.col
            val endCol      = if (startLC.line == endLC.line) endLC.col else lineContent.length + 1
            val underlineLen = Math.max(1, endCol - startCol)

            val padding   = " " * lineNumWidth + " │ " + " " * (startCol - 1)
            val underline = "^" * underlineLen

            List(
              s"$prefix$lineContent",
              s"$padding$underline"
            )
          } else {
            List(s"$prefix$lineContent")
          }
        }

        snippetLines.mkString("\n")

      case None => ""
    }
}

object ErrorFormatter {

  /** Create a formatter for the given source code */
  def apply(source: String): ErrorFormatter = new ErrorFormatter(source)

  /** Format a single error with default context */
  def formatError(source: String, error: CompileError): FormattedError =
    new ErrorFormatter(source).format(error)

  /** Format a single error with context */
  def formatError(
      source: String,
      error: CompileError,
      context: SuggestionContext
  ): FormattedError =
    new ErrorFormatter(source).format(error, context)

  /** Format multiple errors */
  def formatErrors(
      source: String,
      errors: List[CompileError],
      context: SuggestionContext = SuggestionContext.empty
  ): List[FormattedError] =
    new ErrorFormatter(source).formatAll(errors, context)

  /** Quick format to plain text */
  def toPlainText(source: String, error: CompileError): String =
    formatError(source, error).toPlainText

  /** Quick format to markdown */
  def toMarkdown(source: String, error: CompileError): String =
    formatError(source, error).toMarkdown
}
