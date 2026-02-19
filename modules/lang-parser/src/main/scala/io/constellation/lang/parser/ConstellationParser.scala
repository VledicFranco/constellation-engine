package io.constellation.lang.parser

import cats.parse.Rfc5234.{alpha, digit}
import cats.parse.{Numbers, Parser as P, Parser0}
import cats.syntax.all.*

import io.constellation.lang.ast.{
  ArithOp,
  BackoffStrategy,
  BoolOp,
  CompareOp,
  CustomPriority,
  Duration,
  DurationUnit,
  ErrorStrategy,
  ModuleCallOptions,
  PriorityLevel,
  Rate,
  *
}

object ConstellationParser extends MemoizationSupport {

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
  private val withKw: P[Unit]      = keyword("with")
  private val lazyKw: P[Unit]      = keyword("lazy")
  private val matchKw: P[Unit]     = keyword("match")
  private val isKw: P[Unit]        = keyword("is")

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
    "otherwise",
    "with",
    "lazy",
    "match",
    "is"
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
  private val pipe: P[Unit]         = token(P.char('|'))
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
  private val fatArrow: P[Unit]     = token(P.string("=>"))
  private val slash: P[Unit]        = token(P.char('/'))

  // ============================================================================
  // Duration and Rate parsing (for module call options)
  // ============================================================================

  // Duration units: ms, s, min, h, d
  // Note: 'min' must be checked before 'm' to avoid ambiguity
  private val durationUnit: P[DurationUnit] = P.oneOf(
    List(
      P.string("ms").as(DurationUnit.Milliseconds),
      P.string("min").as(DurationUnit.Minutes),
      P.string("s").as(DurationUnit.Seconds),
      P.string("h").as(DurationUnit.Hours),
      P.string("d").as(DurationUnit.Days)
    )
  )

  // Duration: integer followed by unit (e.g., 30s, 5min, 100ms)
  private val duration: P[Duration] =
    (Numbers.nonNegativeIntString ~ durationUnit).map { case (valueStr, unit) =>
      Duration(valueStr.toLong, unit)
    }

  // Rate: count/duration (e.g., 100/1min, 10/1s)
  private val rate: P[Rate] =
    (Numbers.nonNegativeIntString ~ (slash *> duration)).map { case (countStr, per) =>
      Rate(countStr.toInt, per)
    }

  // Backoff strategy identifiers
  private val backoffStrategy: P[BackoffStrategy] = P.oneOf(
    List(
      P.string("exponential").as(BackoffStrategy.Exponential),
      P.string("linear").as(BackoffStrategy.Linear),
      P.string("fixed").as(BackoffStrategy.Fixed)
    )
  )

  // Error handling strategy identifiers
  private val errorStrategy: P[ErrorStrategy] = P.oneOf(
    List(
      P.string("propagate").as(ErrorStrategy.Propagate),
      P.string("skip").as(ErrorStrategy.Skip),
      P.string("log").as(ErrorStrategy.Log),
      P.string("wrap").as(ErrorStrategy.Wrap)
    )
  )

  // Priority level identifiers
  private val priorityLevel: P[PriorityLevel] = P.oneOf(
    List(
      P.string("critical").as(PriorityLevel.Critical),
      P.string("high").as(PriorityLevel.High),
      P.string("normal").as(PriorityLevel.Normal),
      P.string("low").as(PriorityLevel.Low),
      P.string("background").as(PriorityLevel.Background)
    )
  )

  // ============================================================================
  // Window and Join Strategy specs (RFC-025 Phase 3)
  // ============================================================================

  // WindowSpec: tumbling(5s), sliding(5s, 1s), count(100)
  private val windowSpec: P[WindowSpec] = P.oneOf(
    List(
      (P.string("tumbling") *> openParen *> token(duration) <* closeParen)
        .map(d => WindowSpec.Tumbling(d)),
      (P.string("sliding") *> openParen *> token(duration) ~ (comma *> token(duration)) <* closeParen)
        .map { case (size, slide) => WindowSpec.Sliding(size, slide) },
      (P.string("count") *> openParen *> token(Numbers.nonNegativeIntString) <* closeParen)
        .map(n => WindowSpec.Count(n.toInt))
    )
  )

  // JoinStrategySpec: combine_latest, zip, buffer(5s)
  private val joinStrategySpec: P[JoinStrategySpec] = P.oneOf(
    List(
      P.string("combine_latest").as(JoinStrategySpec.CombineLatest),
      (P.string("buffer") *> openParen *> token(duration) <* closeParen)
        .map(d => JoinStrategySpec.Buffer(d)),
      P.string("zip").as(JoinStrategySpec.Zip)
    )
  )

  // ============================================================================
  // Module call options: with retry: 3, timeout: 30s, cache: 5min
  // ============================================================================

  // Option names (used for parsing individual options)
  private val optionName: P[String] = token(rawIdentifier)

  // Single module call option: name: value
  // Returns a function that updates ModuleCallOptions
  private lazy val moduleOption: P[ModuleCallOptions => ModuleCallOptions] = P.defer {
    // retry: 3
    val retryOpt = (token(P.string("retry")) *> colon *> token(Numbers.nonNegativeIntString))
      .map(v => (opts: ModuleCallOptions) => opts.copy(retry = Some(v.toInt)))

    // timeout: 30s
    val timeoutOpt = (token(P.string("timeout")) *> colon *> token(duration))
      .map(d => (opts: ModuleCallOptions) => opts.copy(timeout = Some(d)))

    // delay: 1s
    val delayOpt = (token(P.string("delay")) *> colon *> token(duration))
      .map(d => (opts: ModuleCallOptions) => opts.copy(delay = Some(d)))

    // backoff: exponential
    val backoffOpt = (token(P.string("backoff")) *> colon *> token(backoffStrategy))
      .map(s => (opts: ModuleCallOptions) => opts.copy(backoff = Some(s)))

    // fallback: expression
    val fallbackOpt = (token(P.string("fallback")) *> colon *> withSpan(expression))
      .map(e => (opts: ModuleCallOptions) => opts.copy(fallback = Some(e)))

    // cache: 5min
    val cacheOpt = (token(P.string("cache")) *> colon *> token(duration))
      .map(d => (opts: ModuleCallOptions) => opts.copy(cache = Some(d)))

    // cache_backend: "redis"
    val cacheBackendOpt = (token(P.string("cache_backend")) *> colon *> token(
      P.char('"') *> P.until0(P.char('"')) <* P.char('"')
    ))
      .map(s => (opts: ModuleCallOptions) => opts.copy(cacheBackend = Some(s)))

    // throttle: 100/1min
    val throttleOpt = (token(P.string("throttle")) *> colon *> token(rate))
      .map(r => (opts: ModuleCallOptions) => opts.copy(throttle = Some(r)))

    // concurrency: 5
    val concurrencyOpt =
      (token(P.string("concurrency")) *> colon *> token(Numbers.nonNegativeIntString))
        .map(v => (opts: ModuleCallOptions) => opts.copy(concurrency = Some(v.toInt)))

    // on_error: skip
    val onErrorOpt = (token(P.string("on_error")) *> colon *> token(errorStrategy))
      .map(s => (opts: ModuleCallOptions) => opts.copy(onError = Some(s)))

    // lazy (flag without value) or lazy: true/false
    val lazyFlagOnly = lazyKw.as((opts: ModuleCallOptions) => opts.copy(lazyEval = Some(true)))
    val lazyWithValue = (lazyKw *> colon *> (trueKw.as(true) | falseKw.as(false)))
      .map(v => (opts: ModuleCallOptions) => opts.copy(lazyEval = Some(v)))
    val lazyOpt = lazyWithValue.backtrack | lazyFlagOnly

    // priority: high or priority: 10
    val priorityLevelOpt = token(priorityLevel)
      .map(p => (opts: ModuleCallOptions) => opts.copy(priority = Some(Left(p))))
    val priorityNumericOpt = token(Numbers.signedIntString)
      .map(v =>
        (opts: ModuleCallOptions) => opts.copy(priority = Some(Right(CustomPriority(v.toInt))))
      )
    val priorityOpt =
      (token(P.string("priority")) *> colon *> (priorityLevelOpt.backtrack | priorityNumericOpt))
        .map(f => f) // f is already the right type

    // Streaming options (RFC-025 Phase 3)

    // batch: 100
    val batchOpt =
      (token(P.string("batch")) *> colon *> token(Numbers.nonNegativeIntString))
        .map(v => (opts: ModuleCallOptions) => opts.copy(batch = Some(v.toInt)))

    // batch_timeout: 5s
    val batchTimeoutOpt = (token(P.string("batch_timeout")) *> colon *> token(duration))
      .map(d => (opts: ModuleCallOptions) => opts.copy(batchTimeout = Some(d)))

    // window: tumbling(5s) | sliding(5s, 1s) | count(100)
    val windowOpt = (token(P.string("window")) *> colon *> token(windowSpec))
      .map(w => (opts: ModuleCallOptions) => opts.copy(window = Some(w)))

    // checkpoint: 30s
    val checkpointOpt = (token(P.string("checkpoint")) *> colon *> token(duration))
      .map(d => (opts: ModuleCallOptions) => opts.copy(checkpoint = Some(d)))

    // join: combine_latest | zip | buffer(5s)
    val joinOpt = (token(P.string("join")) *> colon *> token(joinStrategySpec))
      .map(j => (opts: ModuleCallOptions) => opts.copy(join = Some(j)))

    P.oneOf(
      List(
        retryOpt.backtrack,
        timeoutOpt.backtrack,
        delayOpt.backtrack,
        backoffOpt.backtrack,
        fallbackOpt.backtrack,
        cacheBackendOpt.backtrack, // must come before cache
        cacheOpt.backtrack,
        throttleOpt.backtrack,
        concurrencyOpt.backtrack,
        onErrorOpt.backtrack,
        lazyOpt.backtrack,
        priorityOpt.backtrack,
        batchTimeoutOpt.backtrack, // must come before batch
        batchOpt.backtrack,
        windowOpt.backtrack,
        checkpointOpt.backtrack,
        joinOpt.backtrack
      )
    )
  }

  // With clause: with option1: value1, option2: value2, ...
  private lazy val withClause: P[ModuleCallOptions] =
    (withKw *> moduleOption.repSep(comma)).map { options =>
      options.foldLeft(ModuleCallOptions.empty)((acc, f) => f(acc))
    }

  // Optional with clause
  private lazy val optionalWithClause: Parser0[ModuleCallOptions] =
    withClause.?.map(_.getOrElse(ModuleCallOptions.empty))

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
  // Precedence (low to high): union (|) -> merge (+) -> primary
  lazy val typeExpr: P[TypeExpr] = P.defer(typeExprUnion)

  // Union types: A | B | C
  private lazy val typeExprUnion: P[TypeExpr] =
    (typeExprMerge ~ (pipe *> typeExprMerge).rep0).map { case (first, rest) =>
      if rest.isEmpty then first
      else TypeExpr.Union(first :: rest.toList)
    }

  // Type merge: A + B (record field merge)
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

  // Lambda parameter: x or x: Int
  private lazy val lambdaParam: P[Expression.LambdaParam] =
    (withSpan(identifier) ~ (colon *> withSpan(typeExpr)).?).map { case (name, typeAnnotation) =>
      Expression.LambdaParam(name, typeAnnotation)
    }

  // Lambda parameters: (x) or (a, b) or (x: Int, y: String)
  private lazy val lambdaParams: P[List[Expression.LambdaParam]] =
    openParen *> lambdaParam.repSep0(comma).map(_.toList) <* closeParen

  // Lambda expression: (x) => x + 1 or (a, b) => a + b
  private lazy val lambdaExpr: P[Expression.Lambda] =
    (lambdaParams ~ (fatArrow *> withSpan(P.defer(expression)))).map { case (params, body) =>
      Expression.Lambda(params, body)
    }

  // Expressions
  // Precedence (low to high): lambda -> coalesce (??) -> when (guard) -> or -> and -> not -> compare -> addSub -> mulDiv -> postfix -> primary
  lazy val expression: P[Expression] = P.defer(lambdaExpr.backtrack | exprCoalesce)

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

  // Optimized exprPrimary using P.oneOf for efficient alternative matching
  // Order: distinctive keywords first (if, branch, match), then structures, then fallbacks
  private lazy val exprPrimary: P[Expression] =
    P.oneOf(
      List(
        conditional.backtrack,  // 'if' keyword is distinctive
        branchExpr.backtrack,   // 'branch' keyword is distinctive
        matchExpr.backtrack,    // 'match' keyword is distinctive
        parenExpr.backtrack,    // '(' is distinctive
        functionCall.backtrack, // qualified name followed by '('
        literal,                // literals have distinctive prefixes
        varRef                  // fallback - simple identifier
      )
    )

  private lazy val parenExpr: P[Expression] =
    openParen *> expression <* closeParen

  private val varRef: P[Expression.VarRef] =
    identifier.map(Expression.VarRef(_))

  private lazy val functionCall: P[Expression.FunctionCall] =
    ((qualifiedName <* ws) ~ (openParen *> withSpan(expression).repSep0(
      comma
    ) <* closeParen) ~ optionalWithClause)
      .map { case ((name, args), options) =>
        Expression.FunctionCall(name, args.toList, options)
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

  // ============================================================================
  // Match Expression (Pattern Matching)
  // ============================================================================

  /** Record pattern: { field1, field2 } Matches records that have the specified fields.
    */
  private lazy val recordPattern: P[Pattern.Record] =
    (openBrace *> identifier.repSep(comma) <* closeBrace)
      .map(fields => Pattern.Record(fields.toList))

  /** Wildcard pattern: _ Matches any value.
    */
  private lazy val wildcardPattern: P[Pattern.Wildcard] =
    token(P.char('_') <* P.not(identifierCont)).as(Pattern.Wildcard())

  /** Type test pattern: is String Matches values of the specified type.
    */
  private lazy val typeTestPattern: P[Pattern.TypeTest] =
    (isKw *> typeIdentifier).map(Pattern.TypeTest(_))

  /** Pattern: one of the pattern variants */
  private lazy val pattern: P[Pattern] =
    recordPattern.backtrack | wildcardPattern.backtrack | typeTestPattern

  /** Match case: pattern -> expression */
  private lazy val matchCase: P[MatchCase] =
    (withSpan(pattern) ~ (arrow *> withSpan(P.defer(expression))))
      .map { case (pat, body) => MatchCase(pat, body) }

  /** Match expression: match expr { pattern1 -> expr1, pattern2 -> expr2 } Provides structural
    * pattern matching over union types.
    */
  private lazy val matchExpr: P[Expression.Match] =
    (matchKw *> withSpan(P.defer(exprPrimary)) ~ (openBrace *> matchCase.repSep(
      comma
    ) <* closeBrace))
      .map { case (scrutinee, cases) => Expression.Match(scrutinee, cases.toList) }

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
  private lazy val interpolatedStringContent: Parser0[(List[String], List[Located[Expression]])] =
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

  // List literal: [1, 2, 3] or ["a", "b", "c"]
  private lazy val listLit: P[Expression.ListLit] =
    (openBracket *> withSpan(P.defer(expression)).repSep0(comma) <* closeBracket)
      .map(elements => Expression.ListLit(elements.toList))

  // Record literal field: fieldName: expression
  private lazy val recordLitField: P[(String, Located[Expression])] =
    (identifier ~ (colon *> withSpan(P.defer(expression))))
      .map { case (id, expr) => (id, expr) }

  // Record literal: { name: "Alice", age: 30 }
  // Distinguished from record patterns by the colon after field name
  private lazy val recordLit: P[Expression.RecordLit] =
    (openBrace *> recordLitField.repSep0(comma) <* closeBrace)
      .map(fields => Expression.RecordLit(fields.toList))

  // Optimized literal parsing using P.oneOf for efficient matching
  // Each literal type has distinctive starting characters
  private val literal: P[Expression] =
    P.oneOf(
      List(
        interpolatedString, // starts with '"'
        listLit,            // starts with '['
        recordLit.backtrack, // starts with '{' - need backtrack to distinguish from other brace uses
        boolLit.backtrack,  // 'true' or 'false' - need backtrack for keyword prefix
        floatLit.backtrack, // digits with '.' - try before intLit
        intLit              // digits only
      )
    )

  // Declarations
  private val typeDef: P[Declaration.TypeDef] =
    (typeKw *> withSpan(typeIdentifier) ~ (equals *> withSpan(typeExpr)))
      .map { case (name, defn) => Declaration.TypeDef(name, defn) }

  // Annotation: @example(expression)
  private lazy val exampleAnnotation: P[Annotation.Example] =
    (token(P.char('@')) *> P.string("example") *> openParen *> withSpan(expression) <* closeParen)
      .map(expr => Annotation.Example(expr))

  // Simple quoted string (no interpolation): "value" -> value
  private val quotedString: P[String] =
    P.char('"') *> P.until0(P.char('"')) <* P.char('"')

  // Connector property: key: expression
  private lazy val connectorProperty: P[(String, Located[Expression])] =
    (token(rawIdentifier) ~ (colon *> withSpan(expression)))
      .map { case (key, value) => (key, value) }

  // @source("connector_type", key: value, ...)
  private lazy val sourceAnnotation: P[Annotation.Source] = P.defer {
    (token(P.char('@')) *> P.string("source") *> openParen *>
      token(quotedString) ~
      (comma *> connectorProperty).rep0.map(_.toMap) <*
      closeParen)
      .map { case (connector, props) => Annotation.Source(connector, props) }
  }

  // @sink("connector_type", key: value, ...)
  private lazy val sinkAnnotation: P[Annotation.Sink] = P.defer {
    (token(P.char('@')) *> P.string("sink") *> openParen *>
      token(quotedString) ~
      (comma *> connectorProperty).rep0.map(_.toMap) <*
      closeParen)
      .map { case (connector, props) => Annotation.Sink(connector, props) }
  }

  // Any input annotation: @example, @source
  private lazy val inputAnnotation: P[Annotation] =
    sourceAnnotation.backtrack | exampleAnnotation

  // Input declaration with one or more annotations
  private val inputDeclWithAnnotations: P[Declaration.InputDecl] =
    (inputAnnotation.rep.map(_.toList) ~ (inKw *> withSpan(identifier)) ~ (colon *> withSpan(
      typeExpr
    )))
      .map { case ((annots, name), typ) => Declaration.InputDecl(name, typ, annots) }

  // Input declaration without annotations
  private val inputDeclWithoutAnnotations: P[Declaration.InputDecl] =
    ((inKw *> withSpan(identifier)) ~ (colon *> withSpan(typeExpr)))
      .map { case (name, typ) => Declaration.InputDecl(name, typ, Nil) }

  private val inputDecl: P[Declaration.InputDecl] =
    inputDeclWithAnnotations.backtrack | inputDeclWithoutAnnotations

  private val assignment: P[Declaration.Assignment] =
    (withSpan(identifier) ~ (equals *> withSpan(expression)))
      .map { case (name, expr) => Declaration.Assignment(name, expr) }

  // Output declaration with optional @sink annotations
  private val outputDeclWithAnnotations: P[Declaration.OutputDecl] =
    (sinkAnnotation.rep.map(_.toList) ~ (outKw *> withSpan(identifier)))
      .map { case (annots, name) => Declaration.OutputDecl(name, annots) }

  private val outputDeclWithoutAnnotations: P[Declaration.OutputDecl] =
    (outKw *> withSpan(identifier))
      .map(name => Declaration.OutputDecl(name))

  private val outputDecl: P[Declaration.OutputDecl] =
    outputDeclWithAnnotations.backtrack | outputDeclWithoutAnnotations

  private val useDecl: P[Declaration.UseDecl] =
    (useKw *> locatedQualifiedName ~ (asKw *> withSpan(identifier)).?)
      .map { case (path, alias) => Declaration.UseDecl(path, alias) }

  // Declaration parsing using P.oneOf for efficient matching
  // Backtrack needed for declarations that can partially match identifiers:
  // - inputDecl: 'in' can match start of identifier like 'inner'
  // - outputDecl: 'out' can match start of identifier like 'output_var'
  // - useDecl: 'use' can match start of identifier like 'user'
  // - typeDef: 'type' can match start of identifier like 'typeInfo'
  private val declaration: P[Declaration] =
    P.oneOf(
      List(
        typeDef.backtrack,    // 'type' keyword, needs backtrack
        inputDecl.backtrack,  // 'in' or '@', 'in' needs backtrack
        outputDecl.backtrack, // 'out' keyword, needs backtrack
        useDecl.backtrack,    // 'use' keyword, needs backtrack
        assignment            // fallback - identifier = expr
      )
    )

  // Full pipeline: sequence of declarations with at least one output declaration
  val pipeline: Parser0[Pipeline] =
    (ws *> declaration.repSep0(ws) <* ws).flatMap { declarations =>
      // Collect output declarations
      val outputs = declarations.collect { case Declaration.OutputDecl(name, _) =>
        name
      }
      if outputs.isEmpty then {
        P.fail // At least one output required
      } else {
        P.pure(Pipeline(declarations, outputs))
      }
    }

  /** Parse a constellation-lang pipeline. Clears the memoization cache before parsing to ensure
    * fresh state.
    */
  def parse(source: String): Either[CompileError.ParseError, Pipeline] = {
    clearMemoCache() // Clear cache for fresh parse
    pipeline.parseAll(source).left.map { err =>
      val offset = err.failedAtOffset
      CompileError.ParseError(
        s"Parse error: ${err.expected.toList.map(_.toString).mkString(", ")}",
        Some(Span.point(offset))
      )
    }
  }

  /** Parse with cache statistics for benchmarking.
    * @return
    *   Either parse error or (Pipeline, (cache hits, cache misses))
    */
  def parseWithStats(source: String): Either[CompileError.ParseError, (Pipeline, (Int, Int))] = {
    clearMemoCache()
    pipeline.parseAll(source) match {
      case Right(prog) =>
        val stats = getCacheStats
        Right((prog, stats))
      case Left(err) =>
        val offset = err.failedAtOffset
        Left(
          CompileError.ParseError(
            s"Parse error: ${err.expected.toList.map(_.toString).mkString(", ")}",
            Some(Span.point(offset))
          )
        )
    }
  }
}
