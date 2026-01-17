package io.constellation.lang.parser

import cats.parse.{Parser => P, Parser0}
import cats.parse.Numbers
import cats.parse.Rfc5234.{alpha, digit}
import cats.syntax.all.*
import io.constellation.lang.ast.*

object ConstellationParser {

  // Whitespace and comments
  private val whitespaceChar: P[Unit] = P.charIn(" \t\r\n").void
  private val lineComment: P[Unit] = (P.string("#") ~ P.until0(P.char('\n'))).void
  private val ws: Parser0[Unit] = (whitespaceChar | lineComment).rep0.void

  private def token[A](p: P[A]): P[A] = p <* ws

  // Keywords (must not be followed by identifier chars)
  private def keyword(s: String): P[Unit] =
    token(P.string(s) <* P.not(alpha | digit | P.charIn("-_")))

  private val typeKw: P[Unit] = keyword("type")
  private val inKw: P[Unit] = keyword("in")
  private val outKw: P[Unit] = keyword("out")
  private val ifKw: P[Unit] = keyword("if")
  private val elseKw: P[Unit] = keyword("else")
  private val trueKw: P[Unit] = keyword("true")
  private val falseKw: P[Unit] = keyword("false")

  // Reserved words
  private val reserved: Set[String] = Set("type", "in", "out", "if", "else", "true", "false")

  // Identifiers: allow hyphens for function names like "ide-ranker-v2"
  private val identifierStart: P[Char] = alpha | P.charIn("_")
  private val identifierCont: P[Char] = alpha | digit | P.charIn("-_")

  private val rawIdentifier: P[String] =
    (identifierStart ~ identifierCont.rep0).map { case (h, t) => (h :: t).mkString }

  val identifier: P[String] = token(
    rawIdentifier.flatMap { name =>
      if (reserved.contains(name)) P.fail
      else P.pure(name)
    }
  )

  // Type identifiers (PascalCase, but we'll accept any identifier for flexibility)
  val typeIdentifier: P[String] = identifier

  // Symbols
  private val equals: P[Unit] = token(P.char('='))
  private val colon: P[Unit] = token(P.char(':'))
  private val comma: P[Unit] = token(P.char(','))
  private val plus: P[Unit] = token(P.char('+'))
  private val openBrace: P[Unit] = token(P.char('{'))
  private val closeBrace: P[Unit] = token(P.char('}'))
  private val openParen: P[Unit] = token(P.char('('))
  private val closeParen: P[Unit] = token(P.char(')'))
  private val openBracket: P[Unit] = token(P.char('['))
  private val closeBracket: P[Unit] = token(P.char(']'))
  private val openAngle: P[Unit] = token(P.char('<'))
  private val closeAngle: P[Unit] = token(P.char('>'))

  // Position tracking
  private def withPos[A](p: P[A]): P[Located[A]] =
    (P.caret.with1 ~ p).map { case (caret, value) =>
      Located(value, Position(caret.line, caret.col, caret.offset))
    }

  // Type expressions
  lazy val typeExpr: P[TypeExpr] = P.defer(typeExprMerge)

  private lazy val typeExprMerge: P[TypeExpr] =
    (typeExprPrimary ~ (plus *> typeExprPrimary).rep0).map {
      case (first, rest) =>
        rest.foldLeft(first)(TypeExpr.TypeMerge(_, _))
    }

  private lazy val typeExprPrimary: P[TypeExpr] =
    parameterizedType.backtrack | recordType | primitiveOrRef

  private val primitiveOrRef: P[TypeExpr] = typeIdentifier.map { name =>
    name match {
      case "String" | "Int" | "Float" | "Boolean" => TypeExpr.Primitive(name)
      case other => TypeExpr.TypeRef(other)
    }
  }

  private lazy val recordType: P[TypeExpr.Record] =
    (openBrace *> recordField.repSep0(comma) <* closeBrace)
      .map(fields => TypeExpr.Record(fields.toList))

  private val recordField: P[(String, TypeExpr)] =
    (identifier ~ (colon *> typeExpr))

  private lazy val parameterizedType: P[TypeExpr.Parameterized] =
    (typeIdentifier ~ (openAngle *> typeExpr.repSep(comma) <* closeAngle))
      .map { case (name, params) => TypeExpr.Parameterized(name, params.toList) }

  // Expressions
  lazy val expression: P[Expression] = P.defer(exprMerge)

  private lazy val exprMerge: P[Expression] =
    (exprProjection ~ (plus *> withPos(exprProjection)).rep0).map {
      case (first, Nil) => first
      case (first, rest) =>
        rest.foldLeft(first) { (left, right) =>
          Expression.Merge(Located(left, Position.zero), right)
        }
    }

  private lazy val exprProjection: P[Expression] =
    (exprPrimary ~ projection.?).map {
      case (expr, None) => expr
      case (expr, Some(fields)) =>
        Expression.Projection(Located(expr, Position.zero), fields)
    }

  private val projection: P[List[String]] =
    openBracket *> identifier.repSep(comma).map(_.toList) <* closeBracket

  private lazy val exprPrimary: P[Expression] =
    conditional.backtrack | functionCall.backtrack | literal | varRef | parenExpr

  private lazy val parenExpr: P[Expression] =
    openParen *> expression <* closeParen

  private val varRef: P[Expression.VarRef] =
    identifier.map(Expression.VarRef(_))

  private lazy val functionCall: P[Expression.FunctionCall] =
    (identifier ~ (openParen *> expression.repSep0(comma) <* closeParen))
      .map { case (name, args) =>
        Expression.FunctionCall(name, args.toList.map(e => Located(e, Position.zero)))
      }

  private lazy val conditional: P[Expression.Conditional] =
    ((ifKw *> openParen *> expression <* closeParen) ~ expression ~ (elseKw *> expression))
      .map { case ((cond, thenBr), elseBr) =>
        Expression.Conditional(
          Located(cond, Position.zero),
          Located(thenBr, Position.zero),
          Located(elseBr, Position.zero)
        )
      }

  // Literals
  private val stringLit: P[Expression.StringLit] =
    token(P.char('"') *> P.charsWhile0(_ != '"') <* P.char('"'))
      .map(Expression.StringLit(_))

  private val floatLit: P[Expression.FloatLit] =
    token(
      (Numbers.digits ~ P.char('.') ~ Numbers.digits).string
    ).map(s => Expression.FloatLit(s.toDouble))

  private val intLit: P[Expression.IntLit] =
    token(Numbers.signedIntString).map(s => Expression.IntLit(s.toLong))

  private val boolLit: P[Expression.BoolLit] =
    (trueKw.as(true) | falseKw.as(false)).map(Expression.BoolLit(_))

  private val literal: P[Expression] =
    floatLit.backtrack | intLit | stringLit | boolLit

  // Declarations
  private val typeDef: P[Declaration.TypeDef] =
    (typeKw *> withPos(typeIdentifier) ~ (equals *> withPos(typeExpr)))
      .map { case (name, defn) => Declaration.TypeDef(name, defn) }

  private val inputDecl: P[Declaration.InputDecl] =
    (inKw *> withPos(identifier) ~ (colon *> withPos(typeExpr)))
      .map { case (name, typ) => Declaration.InputDecl(name, typ) }

  private val assignment: P[Declaration.Assignment] =
    (withPos(identifier) ~ (equals *> withPos(expression)))
      .map { case (name, expr) => Declaration.Assignment(name, expr) }

  private val declaration: P[Declaration] =
    typeDef.backtrack | inputDecl.backtrack | assignment

  private val outputDecl: P[Located[Expression]] =
    outKw *> withPos(expression)

  // A statement is either a declaration or an output (at the end)
  private val statement: P[Either[Declaration, Located[Expression]]] =
    outputDecl.map(Right(_)).backtrack | declaration.map(Left(_))

  // Full program: sequence of statements ending with exactly one output
  val program: Parser0[Program] =
    (ws *> statement.repSep0(ws) <* ws).flatMap { statements =>
      val decls = statements.collect { case Left(d) => d }
      val outputs = statements.collect { case Right(o) => o }
      outputs match {
        case List(out) => P.pure(Program(decls, out))
        case Nil => P.fail
        case _ => P.fail // Multiple outputs not allowed
      }
    }

  /** Parse a constellation-lang program */
  def parse(source: String): Either[CompileError.ParseError, Program] =
    program.parseAll(source).left.map { err =>
      val pos = err.failedAtOffset
      // Calculate line and column from offset
      val lines = source.take(pos).split("\n", -1)
      val line = lines.length
      val col = lines.lastOption.map(_.length + 1).getOrElse(1)
      CompileError.ParseError(
        s"Parse error: ${err.expected.toList.map(_.toString).mkString(", ")}",
        Some(Position(line, col, pos))
      )
    }
}
