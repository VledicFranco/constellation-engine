package io.constellation.lang.ast

/** Source span representing a range in the source code (byte offsets) */
final case class Span(start: Int, end: Int) {
  def point: Int = start  // Where to place caret in error message
  def length: Int = end - start
  def contains(offset: Int): Boolean = offset >= start && offset < end
  def isEmpty: Boolean = start == end

  override def toString: String = s"[$start..$end)"
}

object Span {
  val zero: Span = Span(0, 0)

  /** Helper to create span from single offset */
  def point(offset: Int): Span = Span(offset, offset + 1)
}

/** Line and column position (1-based, for display) */
final case class LineCol(line: Int, col: Int) {
  override def toString: String = s"$line:$col"
}

/** Efficient mapping from byte offsets to line/column positions */
final case class LineMap(lineStarts: Array[Int]) {
  def offsetToLineCol(offset: Int): LineCol = {
    // Binary search for line containing offset
    val lineIdx = java.util.Arrays.binarySearch(lineStarts, offset)
    val actualLine = if (lineIdx >= 0) lineIdx else -lineIdx - 2
    val col = offset - lineStarts(actualLine)
    LineCol(actualLine + 1, col + 1)  // 1-based for display
  }

  def lineCount: Int = lineStarts.length
}

object LineMap {
  def fromSource(content: String): LineMap = {
    val starts = content.zipWithIndex.collect {
      case ('\n', idx) => idx + 1
    }
    LineMap((0 +: starts).toArray)
  }
}

/** Source file with efficient span→line/col conversion */
final case class SourceFile(name: String, content: String) {
  private lazy val lineMap: LineMap = LineMap.fromSource(content)

  def spanToLineCol(span: Span): (LineCol, LineCol) = (
    lineMap.offsetToLineCol(span.start),
    lineMap.offsetToLineCol(span.end)
  )

  def extractLine(line: Int): String = {
    val start = lineMap.lineStarts(line - 1)
    val end = if (line < lineMap.lineCount)
                lineMap.lineStarts(line) - 1
              else
                content.length
    content.substring(start, end)
  }

  def extractSnippet(span: Span): String = {
    val (startLC, endLC) = spanToLineCol(span)
    val line = extractLine(startLC.line)
    val lineNum = f"${startLC.line}%3d"
    val pointer = " " * (startLC.col - 1) + "^" * ((span.length max 1) min (line.length - startLC.col + 1))
    s"""|
        | $lineNum | $line
        |     | $pointer""".stripMargin
  }
}

/** A value with its source span */
final case class Located[+A](value: A, span: Span) {
  def map[B](f: A => B): Located[B] = Located(f(value), span)
}

object Located {
  // Note: No convenience constructor - force explicit span passing
}

/** A complete constellation-lang program */
final case class Program(
  declarations: List[Declaration],
  outputs: List[Located[String]]  // List of declared output variable names
)

/** Top-level declarations */
sealed trait Declaration

object Declaration {
  /** Type definition: type Communication = { id: String, channel: String } */
  final case class TypeDef(
    name: Located[String],
    definition: Located[TypeExpr]
  ) extends Declaration

  /** Input declaration: in communications: Candidates<Communication> */
  final case class InputDecl(
    name: Located[String],
    typeExpr: Located[TypeExpr]
  ) extends Declaration

  /** Variable assignment: embeddings = embed-model(communications) */
  final case class Assignment(
    target: Located[String],
    value: Located[Expression]
  ) extends Declaration

  /** Output declaration: out varName */
  final case class OutputDecl(
    name: Located[String]
  ) extends Declaration
}

/** Type expressions */
sealed trait TypeExpr

object TypeExpr {
  /** Primitive types: String, Int, Float, Boolean */
  final case class Primitive(name: String) extends TypeExpr

  /** Record types: { field1: Type1, field2: Type2 } */
  final case class Record(fields: List[(String, TypeExpr)]) extends TypeExpr

  /** Parameterized types: Candidates<T>, List<T> */
  final case class Parameterized(name: String, params: List[TypeExpr]) extends TypeExpr

  /** Type reference: Communication (refers to type defined earlier) */
  final case class TypeRef(name: String) extends TypeExpr

  /** Type algebra: A + B (merges record fields) */
  final case class TypeMerge(left: TypeExpr, right: TypeExpr) extends TypeExpr
}

/** Expressions */
sealed trait Expression

object Expression {
  /** Variable reference: communications, embeddings */
  final case class VarRef(name: String) extends Expression

  /** Function call: ide-ranker-v2-candidate-embed(communications) */
  final case class FunctionCall(
    name: String,
    args: List[Located[Expression]]
  ) extends Expression

  /** Type algebra on expressions: embeddings + communications */
  final case class Merge(
    left: Located[Expression],
    right: Located[Expression]
  ) extends Expression

  /** Projection: communications[communicationId, channel] */
  final case class Projection(
    source: Located[Expression],
    fields: List[String]
  ) extends Expression

  /** Conditional: if (cond) expr else expr */
  final case class Conditional(
    condition: Located[Expression],
    thenBranch: Located[Expression],
    elseBranch: Located[Expression]
  ) extends Expression

  /** String literal: "hello" */
  final case class StringLit(value: String) extends Expression

  /** Integer literal: 42 */
  final case class IntLit(value: Long) extends Expression

  /** Float literal: 3.14 */
  final case class FloatLit(value: Double) extends Expression

  /** Boolean literal: true, false */
  final case class BoolLit(value: Boolean) extends Expression
}

/** Compile errors with span information */
sealed trait CompileError {
  def message: String
  def span: Option[Span]

  def format: String = span match {
    case Some(s) => s"Error at $s: $message"
    case None => s"Error: $message"
  }

  def formatWithSource(source: SourceFile): String = span match {
    case Some(s) =>
      val (start, _) = source.spanToLineCol(s)
      val snippet = source.extractSnippet(s)
      s"Error at ${source.name}:$start\n$message\n$snippet"
    case None =>
      s"Error: $message"
  }
}

object CompileError {
  final case class ParseError(message: String, span: Option[Span]) extends CompileError
  final case class TypeError(message: String, span: Option[Span]) extends CompileError
  final case class UndefinedVariable(name: String, span: Option[Span]) extends CompileError {
    def message: String = s"Undefined variable: $name"
  }
  final case class UndefinedType(name: String, span: Option[Span]) extends CompileError {
    def message: String = s"Undefined type: $name"
  }
  final case class UndefinedFunction(name: String, span: Option[Span]) extends CompileError {
    def message: String = s"Undefined function: $name"
  }
  final case class TypeMismatch(expected: String, actual: String, span: Option[Span]) extends CompileError {
    def message: String = s"Type mismatch: expected $expected, got $actual"
  }
  final case class InvalidProjection(field: String, availableFields: List[String], span: Option[Span]) extends CompileError {
    def message: String = s"Invalid projection: field '$field' not found. Available: ${availableFields.mkString(", ")}"
  }
  final case class IncompatibleMerge(leftType: String, rightType: String, span: Option[Span]) extends CompileError {
    def message: String = s"Cannot merge types: $leftType + $rightType"
  }
}
