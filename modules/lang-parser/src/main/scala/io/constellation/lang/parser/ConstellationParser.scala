package io.constellation.lang.parser

import cats.parse.{Parser as P, Parser0}
import cats.parse.Numbers
import cats.parse.Rfc5234.{alpha, digit}
import cats.syntax.all.*
import io.constellation.lang.ast.*
import io.constellation.lang.ast.CompareOp
import io.constellation.lang.ast.ArithOp
import io.constellation.lang.ast.BoolOp

object ConstellationParser {

  // Whitespace and comments
  private val whitespaceChar: P[Unit] = P.charIn(" \t\r\n").void
  private val lineComment: P[Unit]    = (P.string("#") ~ P.until0(P.char('\n'))).void
  private val ws: Parser0[Unit]       = (whitespaceChar | lineComment).rep0.void

  private def token[A](p: P[A]): P[A] = p <* ws

  // Keywords (must not be followed by identifier chars)
  private def keyword(s: String): P[Unit] =
    token(P.string(s) <* P.not(alpha | digit | P.charIn("-_")))

  private val typeKw: P[Unit]      = keyword("type")
  private val inKw: P[Unit]        = keyword("in")
  private val outKw: P[Unit]       = keyword("out")
  private val ifKw: P[Unit]        = keyword("if")
  private val elseKw: P[Unit]      = keyword("else")
  private val trueKw: P[Unit]      = keyword("true")
  private val falseKw: P[Unit]     = keyword("false")
  private val useKw: P[Unit]       = keyword("use")
  private val asKw: P[Unit]        = keyword("as")
  private val andKw: P[Unit]       = keyword("and")
  private val orKw: P[Unit]        = keyword("or")
  private val notKw: P[Unit]       = keyword("not")
  private val whenKw: P[Unit]      = keyword("when")
  private val branchKw: P[Unit]    = keyword("branch")
  private val otherwiseKw: P[Unit] = keyword("otherwise")

  // Reserved words
  private val reserved: Set[String] = Set(
    "type",
    "in",
    "out",
    "if",
    "else",
    "true",
    "false",
    "use",
    "as",
    "and",
    "or",
    "not",
    "when",
    "branch",
    "otherwise"
  )

  // Identifiers: allow hyphens for function names like "ide-ranker-v2"
  private val identifierStart: P[Char] = alpha | P.charIn("_")
  private val identifierCont: P[Char]  = alpha | digit | P.charIn("-_")

  private val rawIdentifier: P[String] =
    (identifierStart ~ identifierCont.rep0).map { case (h, t) => (h :: t).mkString }

  val identifier: P[String] = token(
    rawIdentifier.flatMap { name =>
      if reserved.contains(name) then P.fail
      else P.pure(name)
    }
  )

  // Type identifiers (PascalCase, but we'll accept any identifier for flexibility)
  val typeIdentifier: P[String] = identifier

  // Symbols
  private val equals: P[Unit]       = token(P.char('='))
  private val colon: P[Unit]        = token(P.char(':'))
  private val comma: P[Unit]        = token(P.char(','))
  private val plus: P[Unit]         = token(P.char('+'))
  private val openBrace: P[Unit]    = token(P.char('{'))
  private val closeBrace: P[Unit]   = token(P.char('}'))
  private val openParen: P[Unit]    = token(P.char('('))
  private val closeParen: P[Unit]   = token(P.char(')'))
  private val openBracket: P[Unit]  = token(P.char('['))
  private val closeBracket: P[Unit] = token(P.char(']'))
  private val openAngle: P[Unit]    = token(P.char('<'))
  private val closeAngle: P[Unit]   = token(P.char('>'))
  private val dot: P[Unit]          = P.char('.')
  private val arrow: P[Unit]        = token(P.string("->"))

  // Comparison operators (order matters: longer operators first)
  private val eqOp: P[CompareOp]    = P.string("==").as(CompareOp.Eq)
  private val notEqOp: P[CompareOp] = P.string("!=").as(CompareOp.NotEq)
  private val lteOp: P[CompareOp]   = P.string("<=").as(CompareOp.LtEq)
  private val gteOp: P[CompareOp]   = P.string(">=").as(CompareOp.GtEq)
  private val ltOp: P[CompareOp]    = P.string("<").as(CompareOp.Lt)
  private val gtOp: P[CompareOp]    = P.string(">").as(CompareOp.Gt)

  private val compareOp: P[CompareOp] =
    token(eqOp | notEqOp | lteOp | gteOp | ltOp | gtOp)

  // Arithmetic operators
  // Note: subOp must not match '-' when followed by '>' (which is the arrow ->)
  private val addOp: P[ArithOp] = P.char('+').as(ArithOp.Add)
  private val subOp: P[ArithOp] = (P.char('-') <* P.not(P.char('>'))).backtrack.as(ArithOp.Sub)
  private val mulOp: P[ArithOp] = P.char('*').as(ArithOp.Mul)
  private val divOp: P[ArithOp] = P.char('/').as(ArithOp.Div)

  private val addSubOp: P[ArithOp] = token(addOp | subOp)
  private val mulDivOp: P[ArithOp] = token(mulOp | divOp)

  // Qualified names: stdlib.math.add (note: no whitespace around dots)
  private val qualifiedName: P[QualifiedName] =
    (rawIdentifier ~ (dot *> rawIdentifier).rep0)
      .map { case (first, rest) =>
        QualifiedName((first :: rest.toList).filterNot(reserved.contains))
      }
      .flatMap { qn =>
        if qn.parts.isEmpty || qn.parts.exists(reserved.contains) then P.fail
        else P.pure(qn)
      }

  private val locatedQualifiedName: P[Located[QualifiedName]] =
    withSpan(qualifiedName <* ws)

  // Span tracking - captures full range (start and end offsets)
  private def withSpan[A](p: P[A]): P[Located[A]] =
    (P.caret.with1 ~ p ~ P.caret).map { case ((start, value), end) =>
      Located(value, Span(start.offset, end.offset))
    }

  // Type expressions
  lazy val typeExpr: P[TypeExpr] = P.defer(typeExprMerge)

  private lazy val typeExprMerge: P[TypeExpr] =
    (typeExprPrimary ~ (plus *> typeExprPrimary).rep0).map { case (first, rest) =>
      rest.foldLeft(first)(TypeExpr.TypeMerge(_, _))
    }

  private lazy val typeExprPrimary: P[TypeExpr] =
    parameterizedType.backtrack | recordType | primitiveOrRef

  private val primitiveOrRef: P[TypeExpr] = typeIdentifier.map { name =>
    name match {
      case "String" | "Int" | "Float" | "Boolean" => TypeExpr.Primitive(name)
      case other                                  => TypeExpr.TypeRef(other)
    }
  }

  private lazy val recordType: P[TypeExpr.Record] =
    (openBrace *> recordField.repSep0(comma) <* closeBrace)
      .map(fields => TypeExpr.Record(fields.toList))

  private val recordField: P[(String, TypeExpr)] =
    identifier ~ (colon *> typeExpr)

  private lazy val parameterizedType: P[TypeExpr.Parameterized] =
    (typeIdentifier ~ (openAngle *> typeExpr.repSep(comma) <* closeAngle))
      .map { case (name, params) => TypeExpr.Parameterized(name, params.toList) }

  // Expressions
  // Precedence (low to high): coalesce (??) -> when (guard) -> or -> and -> not -> compare -> addSub -> mulDiv -> postfix -> primary
  lazy val expression: P[Expression] = P.defer(exprCoalesce)

  // Coalesce operator: a ?? b
  // Returns unwrapped value if a is Some, otherwise b
  // Right-associative: a ?? b ?? c = a ?? (b ?? c)
  private lazy val coalesceOp: P[Unit] = token(P.string("??").void)

  private lazy val exprCoalesce: P[Expression] =
    (withSpan(exprGuard) ~ (coalesceOp *> withSpan(P.defer(exprCoalesce))).?).map {
      case (left, None)        => left.value
      case (left, Some(right)) => Expression.Coalesce(left, right)
    }

  // Guard expression: expr when condition
  // Returns Optional<T> where T is the type of expr
  private lazy val exprGuard: P[Expression] =
    (withSpan(exprOr) ~ (whenKw *> withSpan(exprOr)).?).map {
      case (expr, None)            => expr.value
      case (expr, Some(condition)) => Expression.Guard(expr, condition)
    }

  // Boolean OR expressions: a or b
  // Left-associative for chaining: a or b or c = (a or b) or c
  private lazy val exprOr: P[Expression] =
    (withSpan(exprAnd) ~ (orKw *> withSpan(exprAnd)).rep0).map {
      case (first, Nil) => first.value
      case (first, rest) =>
        rest
          .foldLeft(first) { (left, right) =>
            Located(
              Expression.BoolBinary(left, BoolOp.Or, right),
              Span(left.span.start, right.span.end)
            )
          }
          .value
    }

  // Boolean AND expressions: a and b
  // Left-associative for chaining: a and b and c = (a and b) and c
  private lazy val exprAnd: P[Expression] =
    (withSpan(exprNot) ~ (andKw *> withSpan(exprNot)).rep0).map {
      case (first, Nil) => first.value
      case (first, rest) =>
        rest
          .foldLeft(first) { (left, right) =>
            Located(
              Expression.BoolBinary(left, BoolOp.And, right),
              Span(left.span.start, right.span.end)
            )
          }
          .value
    }

  // Boolean NOT expression: not a (unary prefix)
  // Note: P.defer is required to break the recursive reference cycle and avoid
  // cats-parse "infinite loop in function body" warning
  private lazy val exprNot: P[Expression] =
    (notKw *> withSpan(P.defer(exprNot))).map { operand =>
      Expression.Not(operand)
    }.backtrack | exprCompare

  // Comparison expressions: a == b, x < y, etc.
  // Note: we don't allow chaining (a < b < c is invalid)
  private lazy val exprCompare: P[Expression] =
    (withSpan(exprAddSub) ~ (compareOp ~ withSpan(exprAddSub)).?).map {
      case (left, None) => left.value
      case (left, Some((op, right))) =>
        Expression.Compare(left, op, right)
    }

  // Addition and subtraction (lower precedence than multiplication/division)
  private lazy val exprAddSub: P[Expression] =
    (withSpan(exprMulDiv) ~ (addSubOp ~ withSpan(exprMulDiv)).rep0).map {
      case (first, Nil) => first.value
      case (first, rest) =>
        rest
          .foldLeft((first.value, first.span)) { case ((left, leftSpan), (op, right)) =>
            val newSpan = Span(leftSpan.start, right.span.end)
            (Expression.Arithmetic(Located(left, leftSpan), op, right), newSpan)
          }
          ._1
    }

  // Multiplication and division (higher precedence than addition/subtraction)
  private lazy val exprMulDiv: P[Expression] =
    (withSpan(exprPostfix) ~ (mulDivOp ~ withSpan(exprPostfix)).rep0).map {
      case (first, Nil) => first.value
      case (first, rest) =>
        rest
          .foldLeft((first.value, first.span)) { case ((left, leftSpan), (op, right)) =>
            val newSpan = Span(leftSpan.start, right.span.end)
            (Expression.Arithmetic(Located(left, leftSpan), op, right), newSpan)
          }
          ._1
    }

  /** Postfix operations: projection [...] or {...} and field access .field */
  private lazy val exprPostfix: P[Expression] =
    (withSpan(exprPrimary) ~ postfixOp.rep0).map { case (locExpr, ops) =>
      ops
        .foldLeft((locExpr.value, locExpr.span)) { case ((expr, span), op) =>
          op match {
            case Left(fields) =>
              (Expression.Projection(Located(expr, span), fields), span)
            case Right(field) =>
              val newSpan = Span(span.start, field.span.end)
              (Expression.FieldAccess(Located(expr, span), field), newSpan)
          }
        }
        ._1
    }

  // Either[List[String], Located[String]] - Left is projection, Right is field access
  private lazy val postfixOp: P[Either[List[String], Located[String]]] =
    projection.map(Left(_)) | fieldAccess.map(Right(_))

  // Projection supports both [...] and {...} syntax
  private lazy val projection: P[List[String]] =
    bracketProjection | braceProjection

  private lazy val bracketProjection: P[List[String]] =
    openBracket *> identifier.repSep(comma).map(_.toList) <* closeBracket

  private lazy val braceProjection: P[List[String]] =
    openBrace *> identifier.repSep(comma).map(_.toList) <* closeBrace

  private lazy val fieldAccess: P[Located[String]] =
    token(dot) *> withSpan(rawIdentifier <* ws).flatMap { loc =>
      if reserved.contains(loc.value) then P.fail
      else P.pure(loc)
    }

  private lazy val exprPrimary: P[Expression] =
    conditional.backtrack | branchExpr | functionCall.backtrack | literal | varRef.backtrack | parenExpr

  private lazy val parenExpr: P[Expression] =
    openParen *> expression <* closeParen

  private val varRef: P[Expression.VarRef] =
    identifier.map(Expression.VarRef(_))

  private lazy val functionCall: P[Expression.FunctionCall] =
    ((qualifiedName <* ws) ~ (openParen *> withSpan(expression).repSep0(comma) <* closeParen))
      .map { case (name, args) =>
        Expression.FunctionCall(name, args.toList)
      }

  private lazy val conditional: P[Expression.Conditional] =
    ((ifKw *> openParen *> withSpan(expression) <* closeParen) ~ withSpan(
      expression
    ) ~ (elseKw *> withSpan(expression)))
      .map { case ((cond, thenBr), elseBr) =>
        Expression.Conditional(cond, thenBr, elseBr)
      }

  /** Branch expression: multi-way conditional branch { condition1 -> expression1, condition2 ->
    * expression2, otherwise -> defaultExpression }
    */
  private lazy val branchCase: P[(Located[Expression], Located[Expression])] =
    withSpan(P.defer(expression)) ~ (arrow *> withSpan(P.defer(expression)))

  private lazy val otherwiseCase: P[Located[Expression]] =
    otherwiseKw *> arrow *> withSpan(P.defer(expression))

  // Branch item: either a condition -> value case or the otherwise clause
  // Try branchCase first (with backtrack), then otherwiseCase
  private lazy val branchItem
      : P[Either[(Located[Expression], Located[Expression]), Located[Expression]]] =
    branchCase.map(Left(_)).backtrack | otherwiseCase.map(Right(_))

  // Manual comma-separated parsing: first item, then zero or more (comma, item)
  private lazy val branchItems
      : P[List[Either[(Located[Expression], Located[Expression]), Located[Expression]]]] =
    branchItem.flatMap { first =>
      (comma *> branchItem).backtrack.rep0.map { rest =>
        first :: rest.toList
      }
    }

  private lazy val branchExpr: P[Expression.Branch] =
    (branchKw *> openBrace *> branchItems <* closeBrace).flatMap { items =>
      // Validate: all items except the last must be Left (cases), last must be Right (otherwise)
      items.lastOption match {
        case Some(Right(otherwise)) =>
          val cases = items.init.collect { case Left(c) => c }
          if cases.length == items.length - 1 then P.pure(Expression.Branch(cases, otherwise))
          else P.failWith("Branch cases must come before the otherwise clause")
        case _ =>
          P.failWith("Branch must have an otherwise clause as the last item")
      }
    }

  // Literals

  // Escape sequences in strings: \n, \t, \r, \\, \", \$
  private val escapeSequence: P[Char] = P.char('\\') *> P.oneOf(
    List(
      P.char('n').as('\n'),
      P.char('t').as('\t'),
      P.char('r').as('\r'),
      P.char('\\').as('\\'),
      P.char('"').as('"'),
      P.char('$').as('$')
    )
  )

  // Plain string character: anything except ", \, or $ followed by {
  // Note: .backtrack is required so that when we see '$' followed by '{',
  // we backtrack and let the interpolation parser handle it
  private val plainStringChar: P[Char] =
    P.charWhere(c => c != '"' && c != '\\' && c != '$') |
      (P.char('$') <* P.not(P.char('{'))).as('$').backtrack

  // String content character: either escape sequence or plain char
  private val stringContentChar: P[Char] = escapeSequence | plainStringChar

  // Interpolation: ${expression}
  private lazy val interpolation: P[Located[Expression]] =
    P.string("${") *> withSpan(P.defer(expression)) <* P.char('}')

  // String part: sequence of non-interpolation characters
  private val stringPart: Parser0[String] = stringContentChar.rep0.map(_.mkString)

  // Interpolated string content: alternating parts and expressions
  private lazy val interpolatedStringContent
      : Parser0[(List[String], List[Located[Expression]])] = {
    // Start with a string part
    stringPart.flatMap { firstPart =>
      // Then zero or more (interpolation, stringPart) pairs
      (interpolation ~ stringPart).rep0.map { pairs =>
        if pairs.isEmpty then (List(firstPart), List.empty)
        else {
          val parts       = firstPart :: pairs.map(_._2).toList
          val expressions = pairs.map(_._1).toList
          (parts, expressions)
        }
      }
    }
  }

  // Interpolated string: returns StringLit if no interpolations, StringInterpolation otherwise
  private lazy val interpolatedString: P[Expression] =
    token(
      P.char('"') *> interpolatedStringContent <* P.char('"')
    ).map { case (parts, expressions) =>
      if expressions.isEmpty then Expression.StringLit(parts.head)
      else Expression.StringInterpolation(parts, expressions)
    }

  private val floatLit: P[Expression.FloatLit] =
    token(
      (Numbers.digits ~ P.char('.') ~ Numbers.digits).string
    ).map(s => Expression.FloatLit(s.toDouble))

  private val intLit: P[Expression.IntLit] =
    token(Numbers.signedIntString).map(s => Expression.IntLit(s.toLong))

  private val boolLit: P[Expression.BoolLit] =
    (trueKw.as(true) | falseKw.as(false)).map(Expression.BoolLit(_))

  private val literal: P[Expression] =
    floatLit.backtrack | intLit | interpolatedString | boolLit

  // Declarations
  private val typeDef: P[Declaration.TypeDef] =
    (typeKw *> withSpan(typeIdentifier) ~ (equals *> withSpan(typeExpr)))
      .map { case (name, defn) => Declaration.TypeDef(name, defn) }

  private val inputDecl: P[Declaration.InputDecl] =
    (inKw *> withSpan(identifier) ~ (colon *> withSpan(typeExpr)))
      .map { case (name, typ) => Declaration.InputDecl(name, typ) }

  private val assignment: P[Declaration.Assignment] =
    (withSpan(identifier) ~ (equals *> withSpan(expression)))
      .map { case (name, expr) => Declaration.Assignment(name, expr) }

  private val outputDecl: P[Declaration.OutputDecl] =
    (outKw *> withSpan(identifier))
      .map(name => Declaration.OutputDecl(name))

  private val useDecl: P[Declaration.UseDecl] =
    (useKw *> locatedQualifiedName ~ (asKw *> withSpan(identifier)).?)
      .map { case (path, alias) => Declaration.UseDecl(path, alias) }

  private val declaration: P[Declaration] =
    typeDef.backtrack | inputDecl.backtrack | outputDecl.backtrack | useDecl.backtrack | assignment

  // Full program: sequence of declarations with at least one output declaration
  val program: Parser0[Program] =
    (ws *> declaration.repSep0(ws) <* ws).flatMap { declarations =>
      // Collect output declarations
      val outputs = declarations.collect { case Declaration.OutputDecl(name) =>
        name
      }
      if outputs.isEmpty then {
        P.fail // At least one output required
      } else {
        P.pure(Program(declarations, outputs))
      }
    }

  /** Parse a constellation-lang program */
  def parse(source: String): Either[CompileError.ParseError, Program] =
    program.parseAll(source).left.map { err =>
      val offset = err.failedAtOffset
      CompileError.ParseError(
        s"Parse error: ${err.expected.toList.map(_.toString).mkString(", ")}",
        Some(Span.point(offset))
      )
    }
}
