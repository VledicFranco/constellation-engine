package io.constellation.lang.ast

/** Source span representing a range in the source code (byte offsets) */
final case class Span(start: Int, end: Int) {
  def point: Int                     = start // Where to place caret in error message
  def length: Int                    = end - start
  def contains(offset: Int): Boolean = offset >= start && offset < end
  def isEmpty: Boolean               = start == end

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
    val lineIdx    = java.util.Arrays.binarySearch(lineStarts, offset)
    val actualLine = if lineIdx >= 0 then lineIdx else -lineIdx - 2
    val col        = offset - lineStarts(actualLine)
    LineCol(actualLine + 1, col + 1) // 1-based for display
  }

  def lineCount: Int = lineStarts.length
}

object LineMap {
  def fromSource(content: String): LineMap = {
    val starts = content.zipWithIndex.collect { case ('\n', idx) =>
      idx + 1
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
    val end =
      if line < lineMap.lineCount then lineMap.lineStarts(line) - 1
      else content.length
    content.substring(start, end)
  }

  def extractSnippet(span: Span): String = {
    val (startLC, endLC) = spanToLineCol(span)
    val line             = extractLine(startLC.line)
    val lineNum          = f"${startLC.line}%3d"
    val pointer =
      " " * (startLC.col - 1) + "^" * ((span.length max 1) min (line.length - startLC.col + 1))
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

/** Qualified name for namespace support (e.g., "stdlib.math.add") */
final case class QualifiedName(parts: List[String]) {

  /** True if this is a simple name without namespace */
  def isSimple: Boolean = parts.size == 1

  /** The namespace portion (all parts except the last) */
  def namespace: Option[String] =
    if parts.size > 1 then Some(parts.init.mkString(".")) else None

  /** The local name (last part) */
  def localName: String = parts.last

  /** The fully qualified name */
  def fullName: String = parts.mkString(".")

  override def toString: String = fullName
}

object QualifiedName {

  /** Create a simple (non-qualified) name */
  def simple(name: String): QualifiedName = QualifiedName(List(name))

  /** Parse a qualified name from a dotted string */
  def fromString(s: String): QualifiedName = QualifiedName(s.split("\\.").toList)
}

/** A complete constellation-lang program */
final case class Program(
    declarations: List[Declaration],
    outputs: List[Located[String]] // List of declared output variable names
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

  /** Use declaration: use stdlib.math or use stdlib.math as m */
  final case class UseDecl(
      path: Located[QualifiedName],
      alias: Option[Located[String]]
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

/** Comparison operators */
enum CompareOp:
  case Eq    // ==
  case NotEq // !=
  case Lt    // <
  case Gt    // >
  case LtEq  // <=
  case GtEq  // >=

/** Arithmetic operators */
enum ArithOp:
  case Add // +
  case Sub // -
  case Mul // *
  case Div // /

/** Boolean operators */
enum BoolOp:
  case And // and
  case Or  // or

/** Expressions */
sealed trait Expression

object Expression {

  /** Variable reference: communications, embeddings */
  final case class VarRef(name: String) extends Expression

  /** Function call: ide-ranker-v2-candidate-embed(communications) or stdlib.math.add(a, b) */
  final case class FunctionCall(
      name: QualifiedName,
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

  /** Field access: user.name (returns the field value directly, not wrapped in a record) */
  final case class FieldAccess(
      source: Located[Expression],
      field: Located[String]
  ) extends Expression

  /** Conditional: if (cond) expr else expr */
  final case class Conditional(
      condition: Located[Expression],
      thenBranch: Located[Expression],
      elseBranch: Located[Expression]
  ) extends Expression

  /** String literal: "hello" */
  final case class StringLit(value: String) extends Expression

  /** String interpolation: "Hello, ${name}!"
    * Contains N+1 string parts for N interpolated expressions.
    * Example: "Hello, ${name}!" has parts ["Hello, ", "!"] and expressions [VarRef("name")]
    */
  final case class StringInterpolation(
    parts: List[String],                    // Static string parts (always parts.length == expressions.length + 1)
    expressions: List[Located[Expression]]  // Interpolated expressions
  ) extends Expression

  /** Integer literal: 42 */
  final case class IntLit(value: Long) extends Expression

  /** Float literal: 3.14 */
  final case class FloatLit(value: Double) extends Expression

  /** Boolean literal: true, false */
  final case class BoolLit(value: Boolean) extends Expression

  /** Comparison expression: a == b, x < y, etc. */
  final case class Compare(
      left: Located[Expression],
      op: CompareOp,
      right: Located[Expression]
  ) extends Expression

  /** Arithmetic expression: a + b, x * y, etc. */
  final case class Arithmetic(
      left: Located[Expression],
      op: ArithOp,
      right: Located[Expression]
  ) extends Expression

  /** Boolean binary expression: a and b, a or b */
  final case class BoolBinary(
      left: Located[Expression],
      op: BoolOp,
      right: Located[Expression]
  ) extends Expression

  /** Boolean negation: not a */
  final case class Not(
      operand: Located[Expression]
  ) extends Expression

  /** Guard expression: expr when condition Returns Optional<T> where T is the type of expr. If
    * condition is true, returns Some(expr), else returns None.
    */
  final case class Guard(
      expr: Located[Expression],
      condition: Located[Expression]
  ) extends Expression

  /** Coalesce expression: optional ?? fallback If optional is Some(v), returns v. If optional is
    * None, evaluates and returns fallback. Short-circuits: fallback not evaluated if left is Some.
    */
  final case class Coalesce(
      left: Located[Expression],
      right: Located[Expression]
  ) extends Expression

  /** Branch expression: multi-way conditional branch { condition1 -> expression1, condition2 ->
    * expression2, otherwise -> defaultExpression } Conditions evaluated in order; first match wins.
    * 'otherwise' is required for exhaustiveness.
    */
  final case class Branch(
      cases: List[(Located[Expression], Located[Expression])], // condition -> expression pairs
      otherwise: Located[Expression]
  ) extends Expression
}

/** Compile errors with span information */
sealed trait CompileError {
  def message: String
  def span: Option[Span]

  def format: String = span match {
    case Some(s) => s"Error at $s: $message"
    case None    => s"Error: $message"
  }

  def formatWithSource(source: SourceFile): String = span match {
    case Some(s) =>
      val (start, _) = source.spanToLineCol(s)
      val snippet    = source.extractSnippet(s)
      s"Error at ${source.name}:$start\n$message\n$snippet"
    case None =>
      s"Error: $message"
  }
}

object CompileError {
  final case class ParseError(message: String, span: Option[Span]) extends CompileError
  final case class TypeError(message: String, span: Option[Span])  extends CompileError
  final case class UndefinedVariable(name: String, span: Option[Span]) extends CompileError {
    def message: String = s"Undefined variable: $name"
  }
  final case class UndefinedType(name: String, span: Option[Span]) extends CompileError {
    def message: String = s"Undefined type: $name"
  }
  final case class UndefinedFunction(name: String, span: Option[Span]) extends CompileError {
    def message: String = s"Undefined function: $name"
  }
  final case class TypeMismatch(expected: String, actual: String, span: Option[Span])
      extends CompileError {
    def message: String = s"Type mismatch: expected $expected, got $actual"
  }
  final case class InvalidProjection(
      field: String,
      availableFields: List[String],
      span: Option[Span]
  ) extends CompileError {
    def message: String =
      s"Invalid projection: field '$field' not found. Available: ${availableFields.mkString(", ")}"
  }
  final case class InvalidFieldAccess(
      field: String,
      availableFields: List[String],
      span: Option[Span]
  ) extends CompileError {
    def message: String =
      s"Invalid field access: field '$field' not found. Available: ${availableFields.mkString(", ")}"
  }
  final case class IncompatibleMerge(leftType: String, rightType: String, span: Option[Span])
      extends CompileError {
    def message: String =
      s"""Cannot merge types: $leftType + $rightType
         |The '+' operator requires compatible types:
         |  - Two records (fields are merged, right-hand side wins on conflicts)
         |  - Two Candidates (element-wise merge of inner records)
         |  - Candidates + Record (broadcast record to each element)
         |  - Record + Candidates (broadcast record to each element)""".stripMargin
  }
  final case class UndefinedNamespace(namespace: String, span: Option[Span]) extends CompileError {
    def message: String = s"Undefined namespace: $namespace"
  }
  final case class AmbiguousFunction(name: String, candidates: List[String], span: Option[Span])
      extends CompileError {
    def message: String = s"Ambiguous function '$name'. Candidates: ${candidates.mkString(", ")}"
  }

  final case class UnsupportedComparison(
      op: String,
      leftType: String,
      rightType: String,
      span: Option[Span]
  ) extends CompileError {
    def message: String = s"Operator '$op' is not supported for types $leftType and $rightType"
  }

  final case class UnsupportedArithmetic(
      op: String,
      leftType: String,
      rightType: String,
      span: Option[Span]
  ) extends CompileError {
    def message: String =
      s"Arithmetic operator '$op' is not supported for types $leftType and $rightType"
  }
}
