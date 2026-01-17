package io.constellation.lang.ast

/** Source position for error messages */
final case class Position(line: Int, column: Int, offset: Int) {
  override def toString: String = s"$line:$column"
}

object Position {
  val zero: Position = Position(1, 1, 0)
}

/** A value with its source position */
final case class Located[+A](value: A, pos: Position) {
  def map[B](f: A => B): Located[B] = Located(f(value), pos)
}

object Located {
  def apply[A](value: A): Located[A] = Located(value, Position.zero)
}

/** A complete constellation-lang program */
final case class Program(
  declarations: List[Declaration],
  output: Located[Expression]
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

/** Compile errors with position information */
sealed trait CompileError {
  def message: String
  def position: Option[Position]

  def format: String = position match {
    case Some(pos) => s"Error at $pos: $message"
    case None => s"Error: $message"
  }
}

object CompileError {
  final case class ParseError(message: String, position: Option[Position]) extends CompileError
  final case class TypeError(message: String, position: Option[Position]) extends CompileError
  final case class UndefinedVariable(name: String, position: Option[Position]) extends CompileError {
    def message: String = s"Undefined variable: $name"
  }
  final case class UndefinedType(name: String, position: Option[Position]) extends CompileError {
    def message: String = s"Undefined type: $name"
  }
  final case class UndefinedFunction(name: String, position: Option[Position]) extends CompileError {
    def message: String = s"Undefined function: $name"
  }
  final case class TypeMismatch(expected: String, actual: String, position: Option[Position]) extends CompileError {
    def message: String = s"Type mismatch: expected $expected, got $actual"
  }
  final case class InvalidProjection(field: String, availableFields: List[String], position: Option[Position]) extends CompileError {
    def message: String = s"Invalid projection: field '$field' not found. Available: ${availableFields.mkString(", ")}"
  }
  final case class IncompatibleMerge(leftType: String, rightType: String, position: Option[Position]) extends CompileError {
    def message: String = s"Cannot merge types: $leftType + $rightType"
  }
}
