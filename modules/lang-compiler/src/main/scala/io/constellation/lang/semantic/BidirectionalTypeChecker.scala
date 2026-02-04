package io.constellation.lang.semantic

import cats.data.{Validated, ValidatedNel}
import cats.syntax.all.*
import io.constellation.lang.ast.*

import java.util.concurrent.atomic.AtomicInteger

/** Bidirectional type checker for constellation-lang.
  *
  * Implements the bidirectional typing algorithm where types flow both:
  *   - Bottom-up (inference/synthesis mode ⇑): type derived from expression structure
  *   - Top-down (checking mode ⇓): expected type pushed into expression
  *
  * Key benefits:
  *   - Lambda parameters can be inferred from function context
  *   - Empty lists can be typed from expected context
  *   - Better error messages with contextual information
  *
  * The core rules are:
  * {{{
  * [Sub]        Γ ⊢ e ⇒ A    A <: B
  *              ─────────────────────
  *              Γ ⊢ e ⇐ B
  *
  * [Lam-Check]  Γ, x:A ⊢ e ⇐ B
  *              ─────────────────────
  *              Γ ⊢ (λx. e) ⇐ A → B
  *
  * [App]        Γ ⊢ f ⇒ A → B    Γ ⊢ arg ⇐ A
  *              ─────────────────────────────
  *              Γ ⊢ f(arg) ⇒ B
  * }}}
  */
class BidirectionalTypeChecker(functions: FunctionRegistry) {

  import Mode.*
  import TypeContext.*
  import SemanticType.*

  type TypeResult[A] = ValidatedNel[CompileError, A]

  // Thread-safe counter for generating fresh row variables
  // AtomicInteger ensures concurrent type checks don't produce duplicate IDs
  private val rowVarCounter = new AtomicInteger(0)

  // Thread-local warnings collection
  // Each thread gets its own mutable buffer, avoiding concurrent modification issues
  private val collectedWarnings =
    new ThreadLocal[scala.collection.mutable.ListBuffer[CompileWarning]] {
      override def initialValue(): scala.collection.mutable.ListBuffer[CompileWarning] =
        scala.collection.mutable.ListBuffer.empty
    }

  /** Generate a fresh row variable for row polymorphism instantiation */
  private def freshRowVar(): RowVar =
    RowVar(rowVarCounter.incrementAndGet())

  /** Add a warning to the collection */
  private def addWarning(warning: CompileWarning): Unit =
    collectedWarnings.get() += warning

  /** Type check a pipeline */
  def check(program: Pipeline): Either[List[CompileError], TypedPipeline] = {
    // Reset warnings for this thread's type check invocation
    collectedWarnings.get().clear()
    val initialEnv = TypeEnvironment(functions = functions)

    val result = program.declarations
      .foldLeft((initialEnv, List.empty[TypedDeclaration]).validNel[CompileError]) {
        case (Validated.Valid((env, decls)), decl) =>
          checkDeclaration(decl, env).map { case (newEnv, typedDecl) =>
            (newEnv, decls :+ typedDecl)
          }
        case (invalid, _) => invalid
      }
      .map { case (_, typedDecls) =>
        val outputs = typedDecls.collect {
          case TypedDeclaration.OutputDecl(name, semanticType, span) =>
            (name, semanticType, span)
        }
        TypedPipeline(typedDecls, outputs, collectedWarnings.get().toList)
      }

    result.toEither.left.map(_.toList)
  }

  // ============================================================================
  // Declaration Checking
  // ============================================================================

  private def checkDeclaration(
      decl: Declaration,
      env: TypeEnvironment
  ): TypeResult[(TypeEnvironment, TypedDeclaration)] = decl match {

    case Declaration.TypeDef(name, defn) =>
      resolveTypeExpr(defn.value, defn.span, env).map { semType =>
        val newEnv = env.addType(name.value, semType)
        val span   = Span(name.span.start, defn.span.end)
        (newEnv, TypedDeclaration.TypeDef(name.value, semType, span))
      }

    case Declaration.InputDecl(name, typeExpr, annotations) =>
      resolveTypeExpr(typeExpr.value, typeExpr.span, env).andThen { semType =>
        // Validate @example annotations
        val annotationValidation: TypeResult[Unit] = annotations.traverse {
          case Annotation.Example(exprLoc) =>
            checkExpr(exprLoc.value, exprLoc.span, env, Check(semType), TopLevel).andThen {
              typedExpr =>
                if Subtyping.isSubtype(typedExpr.semanticType, semType) then ().validNel
                else
                  CompileError
                    .TypeMismatch(
                      semType.prettyPrint,
                      typedExpr.semanticType.prettyPrint,
                      Some(exprLoc.span)
                    )
                    .invalidNel
            }
        }.void

        annotationValidation.map { _ =>
          val newEnv = env.addVariable(name.value, semType)
          val span   = Span(name.span.start, typeExpr.span.end)
          (newEnv, TypedDeclaration.InputDecl(name.value, semType, span))
        }
      }

    case Declaration.Assignment(target, value) =>
      // Assignments use inference mode (no expected type from declaration)
      inferExpr(value.value, value.span, env, TopLevel).map { typedExpr =>
        val newEnv = env.addVariable(target.value, typedExpr.semanticType)
        val span   = Span(target.span.start, value.span.end)
        (newEnv, TypedDeclaration.Assignment(target.value, typedExpr, span))
      }

    case Declaration.OutputDecl(name) =>
      env.lookupVariable(name.value) match {
        case Some(semanticType) =>
          (env, TypedDeclaration.OutputDecl(name.value, semanticType, name.span)).validNel
        case None =>
          CompileError.UndefinedVariable(name.value, Some(name.span)).invalidNel
      }

    case Declaration.UseDecl(path, aliasOpt) =>
      val namespace = path.value.fullName
      val namespaceExists = functions.namespaces.exists { ns =>
        ns == namespace || ns.startsWith(namespace + ".")
      }
      val hasPrefix = functions.all.exists { sig =>
        sig.qualifiedName.startsWith(namespace + ".")
      }

      if !namespaceExists && !hasPrefix then {
        CompileError.UndefinedNamespace(namespace, Some(path.span)).invalidNel
      } else {
        val newEnv = aliasOpt match {
          case Some(alias) => env.addAliasedImport(alias.value, namespace)
          case None        => env.addWildcardImport(namespace)
        }
        val span = aliasOpt.map(a => Span(path.span.start, a.span.end)).getOrElse(path.span)
        (newEnv, TypedDeclaration.UseDecl(namespace, aliasOpt.map(_.value), span)).validNel
      }
  }

  // ============================================================================
  // Expression Type Checking (Bidirectional)
  // ============================================================================

  /** Main entry point: check expression in given mode */
  def checkExpr(
      expr: Expression,
      span: Span,
      env: TypeEnvironment,
      mode: Mode,
      context: TypeContext
  ): TypeResult[TypedExpression] = mode match {
    case Infer           => inferExpr(expr, span, env, context)
    case Check(expected) => checkAgainst(expr, span, env, expected, context)
  }

  /** Inference mode (⇑): Synthesize type from expression structure */
  def inferExpr(
      expr: Expression,
      span: Span,
      env: TypeEnvironment,
      context: TypeContext
  ): TypeResult[TypedExpression] = expr match {

    case Expression.VarRef(name) =>
      env
        .lookupVariable(name)
        .toValidNel(CompileError.UndefinedVariable(name, Some(span)))
        .map(TypedExpression.VarRef(name, _, span))

    case Expression.FunctionCall(name, args, options) =>
      inferFunctionCall(name, args, options, span, env, context)

    case Expression.Merge(left, right) =>
      (
        inferExpr(left.value, left.span, env, context),
        inferExpr(right.value, right.span, env, context)
      )
        .mapN { (l, r) =>
          mergeTypes(l.semanticType, r.semanticType, span).map { merged =>
            TypedExpression.Merge(l, r, merged, span)
          }
        }
        .andThen(identity)

    case Expression.Projection(source, fields) =>
      inferExpr(source.value, source.span, env, context).andThen { typedSource =>
        checkProjection(typedSource, fields, span)
      }

    case Expression.FieldAccess(source, field) =>
      inferExpr(source.value, source.span, env, context).andThen { typedSource =>
        checkFieldAccess(typedSource, field.value, field.span, span)
      }

    case Expression.Conditional(cond, thenBr, elseBr) =>
      (
        inferExpr(cond.value, cond.span, env, context),
        inferExpr(thenBr.value, thenBr.span, env, ConditionalBranch("then")),
        inferExpr(elseBr.value, elseBr.span, env, ConditionalBranch("else"))
      ).mapN { (c, t, e) =>
        if c.semanticType != SBoolean then
          CompileError
            .TypeMismatch("Boolean", c.semanticType.prettyPrint, Some(cond.span))
            .invalidNel
        else {
          val resultType = Subtyping.lub(t.semanticType, e.semanticType)
          TypedExpression.Conditional(c, t, e, resultType, span).validNel
        }
      }.andThen(identity)

    case Expression.StringLit(v) =>
      TypedExpression.Literal(v, SString, span).validNel

    case Expression.StringInterpolation(parts, expressions) =>
      expressions
        .traverse { locExpr =>
          inferExpr(locExpr.value, locExpr.span, env, context)
        }
        .map { typedExprs =>
          TypedExpression.StringInterpolation(parts, typedExprs, span)
        }

    case Expression.IntLit(v) =>
      TypedExpression.Literal(v, SInt, span).validNel

    case Expression.FloatLit(v) =>
      TypedExpression.Literal(v, SFloat, span).validNel

    case Expression.BoolLit(v) =>
      TypedExpression.Literal(v, SBoolean, span).validNel

    case Expression.ListLit(elements) =>
      if elements.isEmpty then {
        // Empty list in inference mode - use SNothing as element type
        // This is compatible with any List<T> via subtyping
        TypedExpression.ListLiteral(Nil, SNothing, span).validNel
      } else {
        elements.traverse(elem => inferExpr(elem.value, elem.span, env, context)).map {
          typedElements =>
            val elementType = Subtyping.commonType(typedElements.map(_.semanticType))
            TypedExpression.ListLiteral(typedElements, elementType, span)
        }
      }

    case Expression.Compare(left, op, right) =>
      (
        inferExpr(left.value, left.span, env, context),
        inferExpr(right.value, right.span, env, context)
      )
        .mapN((l, r) => desugarComparison(l, op, r, span, env))
        .andThen(identity)

    case Expression.Arithmetic(left, op, right) =>
      (
        inferExpr(left.value, left.span, env, context),
        inferExpr(right.value, right.span, env, context)
      )
        .mapN((l, r) => desugarArithmetic(l, op, r, span, env))
        .andThen(identity)

    case Expression.BoolBinary(left, op, right) =>
      (
        inferExpr(left.value, left.span, env, context),
        inferExpr(right.value, right.span, env, context)
      )
        .mapN { (l, r) =>
          val errors = List(
            if l.semanticType != SBoolean then
              Some(
                CompileError.TypeMismatch("Boolean", l.semanticType.prettyPrint, Some(left.span))
              )
            else None,
            if r.semanticType != SBoolean then
              Some(
                CompileError.TypeMismatch("Boolean", r.semanticType.prettyPrint, Some(right.span))
              )
            else None
          ).flatten
          if errors.nonEmpty then errors.head.invalidNel
          else TypedExpression.BoolBinary(l, op, r, span).validNel
        }
        .andThen(identity)

    case Expression.Not(operand) =>
      inferExpr(operand.value, operand.span, env, context).andThen { typedOperand =>
        if typedOperand.semanticType != SBoolean then
          CompileError
            .TypeMismatch("Boolean", typedOperand.semanticType.prettyPrint, Some(operand.span))
            .invalidNel
        else TypedExpression.Not(typedOperand, span).validNel
      }

    case Expression.Guard(expr, condition) =>
      (
        inferExpr(expr.value, expr.span, env, context),
        inferExpr(condition.value, condition.span, env, context)
      ).mapN { (typedExpr, typedCondition) =>
        if typedCondition.semanticType != SBoolean then
          CompileError
            .TypeMismatch("Boolean", typedCondition.semanticType.prettyPrint, Some(condition.span))
            .invalidNel
        else TypedExpression.Guard(typedExpr, typedCondition, span).validNel
      }.andThen(identity)

    case Expression.Coalesce(left, right) =>
      (
        inferExpr(left.value, left.span, env, context),
        inferExpr(right.value, right.span, env, context)
      )
        .mapN { (typedLeft, typedRight) =>
          checkCoalesce(typedLeft, typedRight, left.span, right.span, span)
        }
        .andThen(identity)

    case Expression.Branch(cases, otherwise) =>
      val casesResult = cases.traverse { case (cond, expr) =>
        (
          inferExpr(cond.value, cond.span, env, context),
          inferExpr(expr.value, expr.span, env, context)
        ).mapN { (typedCond, typedExpr) =>
          if typedCond.semanticType != SBoolean then
            CompileError
              .TypeMismatch("Boolean", typedCond.semanticType.prettyPrint, Some(cond.span))
              .invalidNel
          else (typedCond, typedExpr).validNel
        }.andThen(identity)
      }

      val otherwiseResult = inferExpr(otherwise.value, otherwise.span, env, context)

      (casesResult, otherwiseResult)
        .mapN { (typedCases, typedOtherwise) =>
          val allExprs   = typedCases.map(_._2) :+ typedOtherwise
          val resultType = Subtyping.commonType(allExprs.map(_.semanticType))
          TypedExpression.Branch(typedCases, typedOtherwise, resultType, span).validNel
        }
        .andThen(identity)

    case Expression.Lambda(params, body) =>
      // In inference mode, all parameters must have type annotations
      inferLambda(params, body, span, env)
  }

  /** Checking mode (⇓): Check expression against expected type */
  def checkAgainst(
      expr: Expression,
      span: Span,
      env: TypeEnvironment,
      expected: SemanticType,
      context: TypeContext
  ): TypeResult[TypedExpression] = (expr, expected) match {

    // Lambda without annotations: infer params from expected function type
    case (Expression.Lambda(params, body), SFunction(expectedParams, expectedReturn)) =>
      checkLambdaAgainst(params, body, expectedParams, expectedReturn, span, env, context)

    // Empty list: use expected element type
    case (Expression.ListLit(Nil), SList(expectedElem)) =>
      TypedExpression.ListLiteral(Nil, expectedElem, span).validNel

    // Non-empty list: infer element types then check list subtyping
    // This preserves backwards-compatible error messages like "expected List<Int>, got List<String>"
    case (Expression.ListLit(elements), expected @ SList(expectedElem)) =>
      elements.traverse(elem => inferExpr(elem.value, elem.span, env, context)).andThen {
        typedElements =>
          val inferredElemType = Subtyping.commonType(typedElements.map(_.semanticType))
          val inferredListType = SList(inferredElemType)
          if Subtyping.isSubtype(inferredListType, expected) then {
            // Use expected element type for the result (bidirectional propagation)
            TypedExpression.ListLiteral(typedElements, expectedElem, span).validNel
          } else {
            CompileError
              .TypeMismatch(
                expected.prettyPrint,
                inferredListType.prettyPrint,
                Some(span)
              )
              .invalidNel
          }
      }

    // Default: infer then check subtyping (subsumption rule)
    case _ =>
      inferExpr(expr, span, env, context).andThen { typed =>
        if Subtyping.isSubtype(typed.semanticType, expected) then {
          typed.validNel
        } else {
          // Enhanced error message with context
          val contextMsg = context match {
            case TopLevel => ""
            case ctx      => s" ${ctx.describe}"
          }
          CompileError
            .TypeMismatch(
              expected.prettyPrint,
              typed.semanticType.prettyPrint,
              Some(span)
            )
            .invalidNel
        }
      }
  }

  // ============================================================================
  // Lambda Type Checking
  // ============================================================================

  /** Infer lambda type when no expected type is available */
  private def inferLambda(
      params: List[Expression.LambdaParam],
      body: Located[Expression],
      span: Span,
      env: TypeEnvironment
  ): TypeResult[TypedExpression.Lambda] = {
    // All parameters must have type annotations in inference mode
    val paramTypesResult = params.traverse { param =>
      param.typeAnnotation match {
        case Some(typeExpr) =>
          resolveTypeExpr(typeExpr.value, typeExpr.span, env).map(t => param.name.value -> t)
        case None =>
          CompileError
            .TypeError(
              s"Lambda parameter '${param.name.value}' requires a type annotation. " +
                s"Hint: Either add a type annotation like (${param.name.value}: SomeType) => ..., " +
                s"or use this lambda in a context where the type can be inferred (e.g., as an argument to Filter or Map).",
              Some(param.name.span)
            )
            .invalidNel
      }
    }

    paramTypesResult.andThen { paramTypes =>
      val lambdaEnv = paramTypes.foldLeft(env) { case (e, (name, typ)) =>
        e.addVariable(name, typ)
      }
      inferExpr(body.value, body.span, lambdaEnv, TopLevel).map { typedBody =>
        val funcType = SFunction(paramTypes.map(_._2), typedBody.semanticType)
        TypedExpression.Lambda(paramTypes, typedBody, funcType, span)
      }
    }
  }

  /** Check lambda against expected function type (bidirectional!) */
  private def checkLambdaAgainst(
      params: List[Expression.LambdaParam],
      body: Located[Expression],
      expectedParams: List[SemanticType],
      expectedReturn: SemanticType,
      span: Span,
      env: TypeEnvironment,
      context: TypeContext
  ): TypeResult[TypedExpression.Lambda] = {
    // Validate parameter count
    if params.length != expectedParams.length then {
      return CompileError
        .TypeError(
          s"Lambda has ${params.length} parameter(s) but expected ${expectedParams.length}",
          Some(span)
        )
        .invalidNel
    }

    // Infer parameter types from expected type, or use explicit annotations
    val paramTypesResult = params.zip(expectedParams).traverse { case (param, expectedParamType) =>
      param.typeAnnotation match {
        case Some(typeExpr) =>
          // Explicit annotation - validate compatibility (contravariance for params)
          resolveTypeExpr(typeExpr.value, typeExpr.span, env).andThen { annotatedType =>
            if Subtyping.isSubtype(expectedParamType, annotatedType) then {
              (param.name.value -> annotatedType).validNel
            } else {
              CompileError
                .TypeMismatch(
                  expectedParamType.prettyPrint,
                  annotatedType.prettyPrint,
                  Some(typeExpr.span)
                )
                .invalidNel
            }
          }
        case None =>
          // Infer from expected type (the key bidirectional feature!)
          (param.name.value -> expectedParamType).validNel
      }
    }

    paramTypesResult.andThen { paramTypes =>
      val lambdaEnv = paramTypes.foldLeft(env) { case (e, (name, typ)) =>
        e.addVariable(name, typ)
      }
      // Check body against expected return type
      val bodyContext = LambdaBody(expectedReturn)
      checkAgainst(body.value, body.span, lambdaEnv, expectedReturn, bodyContext).map { typedBody =>
        val funcType = SFunction(paramTypes.map(_._2), typedBody.semanticType)
        TypedExpression.Lambda(paramTypes, typedBody, funcType, span)
      }
    }
  }

  // ============================================================================
  // Function Call Type Checking
  // ============================================================================

  /** Infer function call type, using checking mode for arguments */
  private def inferFunctionCall(
      name: QualifiedName,
      args: List[Located[Expression]],
      options: ModuleCallOptions,
      span: Span,
      env: TypeEnvironment,
      context: TypeContext
  ): TypeResult[TypedExpression] =
    functions.lookupInScope(name, env.namespaceScope, Some(span)) match {
      case Right(sig) =>
        if args.size != sig.params.size then {
          CompileError
            .TypeError(
              s"Function ${name.fullName} expects ${sig.params.size} argument(s), got ${args.size}",
              Some(span)
            )
            .invalidNel
        } else if sig.isRowPolymorphic then {
          // Row-polymorphic function: instantiate fresh row variables and use unification
          inferRowPolymorphicCall(name, sig, args, options, span, env, context)
        } else {
          // Standard function: check each argument against expected parameter type
          val argsResult = args.zip(sig.params).zipWithIndex.traverse {
            case ((argExpr, (paramName, paramType)), idx) =>
              val argContext = FunctionArgument(name.fullName, idx, paramName)
              checkExpr(argExpr.value, argExpr.span, env, Check(paramType), argContext)
          }

          // Validate module call options if present and get typed fallback
          val optionsValidation: TypeResult[Option[TypedExpression]] =
            if options.isEmpty then None.validNel
            else validateModuleCallOptions(options, sig.returns, span, env, context)

          // Combine args and options validation
          (argsResult, optionsValidation).mapN { (typedArgs, typedFallback) =>
            TypedExpression.FunctionCall(
              name.fullName,
              sig,
              typedArgs,
              options,
              typedFallback,
              span
            )
          }
        }
      case Left(error) =>
        error.invalidNel
    }

  /** Handle row-polymorphic function call with row unification */
  private def inferRowPolymorphicCall(
      name: QualifiedName,
      originalSig: FunctionSignature,
      args: List[Located[Expression]],
      options: ModuleCallOptions,
      span: Span,
      env: TypeEnvironment,
      context: TypeContext
  ): TypeResult[TypedExpression] = {
    // Instantiate fresh row variables for this call site
    val instantiatedSig = originalSig.instantiate(() => freshRowVar())

    // Type check arguments, collecting row substitutions along the way
    var subst = RowUnification.Substitution.empty

    val typedArgsResult = args.zip(instantiatedSig.params).zipWithIndex.traverse {
      case ((argExpr, (paramName, paramType)), idx) =>
        val argContext = FunctionArgument(name.fullName, idx, paramName)

        // First, infer the argument type
        inferExpr(argExpr.value, argExpr.span, env, context).andThen { typedArg =>
          // Then check compatibility, potentially using row unification
          checkArgumentWithRowUnification(
            typedArg,
            paramType,
            argExpr.span,
            idx,
            name.fullName,
            subst
          ) match {
            case Right((typed, newSubst)) =>
              subst = newSubst
              typed.validNel
            case Left(error) =>
              error.invalidNel
          }
        }
    }

    // Validate module call options if present and get typed fallback
    val optionsValidation: TypeResult[Option[TypedExpression]] =
      if options.isEmpty then None.validNel
      else validateModuleCallOptions(options, originalSig.returns, span, env, context)

    (typedArgsResult, optionsValidation).mapN { (typedArgs, typedFallback) =>
      // Apply substitution to the return type
      val resultType = RowUnification.applySubstitution(instantiatedSig.returns, subst)
      TypedExpression.FunctionCall(
        name.fullName,
        originalSig,
        typedArgs,
        options,
        typedFallback,
        span
      )
    }
  }

  /** Check argument against expected type with row unification support */
  private def checkArgumentWithRowUnification(
      typedArg: TypedExpression,
      expectedType: SemanticType,
      span: Span,
      argIndex: Int,
      funcName: String,
      subst: RowUnification.Substitution
  ): Either[CompileError, (TypedExpression, RowUnification.Substitution)] =
    (typedArg.semanticType, expectedType) match {
      // Closed record passed to open record parameter - use row unification
      case (actual: SRecord, expected: SOpenRecord) =>
        RowUnification.unifyClosedWithOpen(actual, expected) match {
          case Right(newSubst) =>
            Right((typedArg, subst.merge(newSubst)))
          case Left(err) =>
            Left(
              CompileError.TypeError(
                s"Argument ${argIndex + 1} to $funcName: ${err.message}",
                Some(span)
              )
            )
        }

      // Open record passed to open record parameter
      case (actual: SOpenRecord, expected: SOpenRecord) =>
        RowUnification.unifyOpenWithOpen(actual, expected, subst) match {
          case Right(newSubst) =>
            Right((typedArg, newSubst))
          case Left(err) =>
            Left(
              CompileError.TypeError(
                s"Argument ${argIndex + 1} to $funcName: ${err.message}",
                Some(span)
              )
            )
        }

      // Standard subtyping check
      case (actual, expected) =>
        if Subtyping.isSubtype(actual, expected) then {
          Right((typedArg, subst))
        } else {
          Left(
            CompileError.TypeMismatch(
              expected.prettyPrint,
              actual.prettyPrint,
              Some(span)
            )
          )
        }
    }

  // ============================================================================
  // Module Call Options Validation
  // ============================================================================

  /** Validate module call options and return any errors. Warnings are collected via addWarning().
    *
    * Validates:
    *   - Fallback type compatibility with module return type
    *   - Value ranges (retry >= 0, concurrency > 0, etc.)
    *   - Option dependencies (warns if delay without retry, etc.)
    */
  /** Validates module call options and returns the typed fallback expression if present.
    *
    * @return
    *   The typed fallback expression (if present and valid), or None
    */
  private def validateModuleCallOptions(
      options: ModuleCallOptions,
      moduleReturnType: SemanticType,
      span: Span,
      env: TypeEnvironment,
      context: TypeContext
  ): TypeResult[Option[TypedExpression]] = {
    import cats.syntax.all.*

    val errors = scala.collection.mutable.ListBuffer[CompileError]()
    var typedFallbackOpt: Option[TypedExpression] = None

    // 1. Validate fallback type and capture typed expression
    options.fallback.foreach { fallbackExpr =>
      // Type check the fallback expression
      inferExpr(fallbackExpr.value, fallbackExpr.span, env, context) match {
        case Validated.Valid(typedFallback) =>
          if !Subtyping.isSubtype(typedFallback.semanticType, moduleReturnType) then {
            errors += CompileError.FallbackTypeMismatch(
              moduleReturnType.prettyPrint,
              typedFallback.semanticType.prettyPrint,
              Some(fallbackExpr.span)
            )
          } else {
            // Capture the typed fallback for use in FunctionCall
            typedFallbackOpt = Some(typedFallback)
          }
        case Validated.Invalid(errs) =>
          // Propagate fallback type check errors
          errors ++= errs.toList
      }
    }

    // 2. Validate value ranges
    options.retry.foreach { value =>
      if value < 0 then {
        errors += CompileError.InvalidOptionValue(
          "retry",
          value.toString,
          "must be >= 0",
          Some(span)
        )
      }
    }

    options.concurrency.foreach { value =>
      if value <= 0 then {
        errors += CompileError.InvalidOptionValue(
          "concurrency",
          value.toString,
          "must be > 0",
          Some(span)
        )
      }
    }

    options.throttle.foreach { rate =>
      if rate.count <= 0 then {
        errors += CompileError.InvalidOptionValue(
          "throttle",
          s"${rate.count}/${rate.per.value}${rate.per.unit}",
          "count must be > 0",
          Some(span)
        )
      }
    }

    options.timeout.foreach { duration =>
      if duration.value <= 0 then {
        errors += CompileError.InvalidOptionValue(
          "timeout",
          s"${duration.value}${durationUnitString(duration.unit)}",
          "must be > 0",
          Some(span)
        )
      }
    }

    options.delay.foreach { duration =>
      if duration.value <= 0 then {
        errors += CompileError.InvalidOptionValue(
          "delay",
          s"${duration.value}${durationUnitString(duration.unit)}",
          "must be > 0",
          Some(span)
        )
      }
    }

    options.cache.foreach { duration =>
      if duration.value <= 0 then {
        errors += CompileError.InvalidOptionValue(
          "cache",
          s"${duration.value}${durationUnitString(duration.unit)}",
          "must be > 0",
          Some(span)
        )
      }
    }

    // 3. Validate option dependencies (warnings, not errors)
    if options.delay.isDefined && options.retry.isEmpty then {
      addWarning(CompileWarning.OptionDependency("delay", "retry", Some(span)))
    }

    if options.backoff.isDefined && options.delay.isEmpty then {
      addWarning(CompileWarning.OptionDependency("backoff", "delay", Some(span)))
    }

    if options.backoff.isDefined && options.retry.isEmpty then {
      addWarning(CompileWarning.OptionDependency("backoff", "retry", Some(span)))
    }

    if options.cacheBackend.isDefined && options.cache.isEmpty then {
      addWarning(CompileWarning.OptionDependency("cache_backend", "cache", Some(span)))
    }

    // Warn about high retry count
    options.retry.foreach { retryCount =>
      if retryCount > 10 then {
        addWarning(CompileWarning.HighRetryCount(retryCount, Some(span)))
      }
    }

    // Return errors or typed fallback
    if errors.nonEmpty then {
      errors.head.invalidNel
    } else {
      typedFallbackOpt.validNel
    }
  }

  /** Convert DurationUnit to string for error messages */
  private def durationUnitString(unit: DurationUnit): String = unit match {
    case DurationUnit.Milliseconds => "ms"
    case DurationUnit.Seconds      => "s"
    case DurationUnit.Minutes      => "min"
    case DurationUnit.Hours        => "h"
    case DurationUnit.Days         => "d"
  }

  // ============================================================================
  // Helper Methods
  // ============================================================================

  private def resolveTypeExpr(
      typeExpr: TypeExpr,
      span: Span,
      env: TypeEnvironment
  ): TypeResult[SemanticType] = typeExpr match {
    case TypeExpr.Primitive(name) =>
      name match {
        case "String"  => SString.validNel
        case "Int"     => SInt.validNel
        case "Float"   => SFloat.validNel
        case "Boolean" => SBoolean.validNel
        case other     => CompileError.UndefinedType(other, Some(span)).invalidNel
      }

    case TypeExpr.TypeRef(name) =>
      env.lookupType(name).toValidNel(CompileError.UndefinedType(name, Some(span)))

    case TypeExpr.Record(fields) =>
      fields
        .traverse { case (name, typ) =>
          resolveTypeExpr(typ, span, env).map(name -> _)
        }
        .map(fs => SRecord(fs.toMap))

    case TypeExpr.Parameterized(name, params) =>
      name match {
        // "Candidates" is a legacy alias for "List"
        case "Candidates" if params.size == 1 =>
          resolveTypeExpr(params.head, span, env).map(SList(_))
        case "List" if params.size == 1 =>
          resolveTypeExpr(params.head, span, env).map(SList(_))
        case "Map" if params.size == 2 =>
          (resolveTypeExpr(params(0), span, env), resolveTypeExpr(params(1), span, env))
            .mapN(SMap(_, _))
        case "Optional" if params.size == 1 =>
          resolveTypeExpr(params.head, span, env).map(SOptional(_))
        case _ =>
          CompileError.UndefinedType(s"$name<...>", Some(span)).invalidNel
      }

    case TypeExpr.TypeMerge(left, right) =>
      (resolveTypeExpr(left, span, env), resolveTypeExpr(right, span, env))
        .mapN((l, r) => mergeTypes(l, r, span))
        .andThen(identity)

    case TypeExpr.Union(members) =>
      members.traverse(m => resolveTypeExpr(m, span, env)).map { resolvedMembers =>
        val flattened = resolvedMembers.flatMap {
          case SUnion(innerMembers) => innerMembers.toList
          case other                => List(other)
        }.toSet
        if flattened.size == 1 then flattened.head
        else SUnion(flattened)
      }
  }

  private def mergeTypes(
      left: SemanticType,
      right: SemanticType,
      span: Span
  ): TypeResult[SemanticType] =
    (left, right) match {
      case (SRecord(lFields), SRecord(rFields)) =>
        SRecord(lFields ++ rFields).validNel
      // List<Record> + List<Record> = merge records element-wise
      case (SList(SRecord(lFields)), SList(SRecord(rFields))) =>
        SList(SRecord(lFields ++ rFields)).validNel
      // List<Record> + Record = add fields to each element
      case (SList(lElem), rRec: SRecord) =>
        mergeTypes(lElem, rRec, span).map(SList(_))
      // Record + List<Record> = add fields to each element
      case (lRec: SRecord, SList(rElem)) =>
        mergeTypes(lRec, rElem, span).map(SList(_))
      case _ =>
        CompileError.IncompatibleMerge(left.prettyPrint, right.prettyPrint, Some(span)).invalidNel
    }

  private def checkProjection(
      typedSource: TypedExpression,
      fields: List[String],
      span: Span
  ): TypeResult[TypedExpression] =
    typedSource.semanticType match {
      case SRecord(availableFields) =>
        validateProjection(fields, availableFields, span).map { projectedFields =>
          TypedExpression.Projection(typedSource, fields, SRecord(projectedFields), span)
        }
      // List<Record> projection: select fields from each element
      case SList(SRecord(availableFields)) =>
        validateProjection(fields, availableFields, span).map { projectedFields =>
          TypedExpression.Projection(typedSource, fields, SList(SRecord(projectedFields)), span)
        }
      case other =>
        CompileError
          .TypeError(
            s"Projection requires a record type, got ${other.prettyPrint}",
            Some(span)
          )
          .invalidNel
    }

  private def validateProjection(
      requested: List[String],
      available: Map[String, SemanticType],
      span: Span
  ): TypeResult[Map[String, SemanticType]] =
    requested
      .traverse { field =>
        available
          .get(field)
          .toValidNel(CompileError.InvalidProjection(field, available.keys.toList, Some(span)))
          .map(field -> _)
      }
      .map(_.toMap)

  private def checkFieldAccess(
      typedSource: TypedExpression,
      field: String,
      fieldSpan: Span,
      span: Span
  ): TypeResult[TypedExpression] =
    typedSource.semanticType match {
      case SRecord(availableFields) =>
        availableFields.get(field) match {
          case Some(fieldType) =>
            TypedExpression.FieldAccess(typedSource, field, fieldType, span).validNel
          case None =>
            CompileError
              .InvalidFieldAccess(field, availableFields.keys.toList, Some(fieldSpan))
              .invalidNel
        }
      // List<Record> field access: extract field from each element
      case SList(SRecord(availableFields)) =>
        availableFields.get(field) match {
          case Some(fieldType) =>
            TypedExpression.FieldAccess(typedSource, field, SList(fieldType), span).validNel
          case None =>
            CompileError
              .InvalidFieldAccess(field, availableFields.keys.toList, Some(fieldSpan))
              .invalidNel
        }
      case other =>
        CompileError
          .TypeError(
            s"Field access requires a record type, got ${other.prettyPrint}",
            Some(span)
          )
          .invalidNel
    }

  private def checkCoalesce(
      typedLeft: TypedExpression,
      typedRight: TypedExpression,
      leftSpan: Span,
      rightSpan: Span,
      span: Span
  ): TypeResult[TypedExpression] =
    typedLeft.semanticType match {
      case SOptional(innerType) =>
        val rightType = typedRight.semanticType
        if innerType == rightType then {
          TypedExpression.Coalesce(typedLeft, typedRight, span, rightType).validNel
        } else {
          rightType match {
            case SOptional(rightInner) if innerType == rightInner =>
              TypedExpression.Coalesce(typedLeft, typedRight, span, SOptional(innerType)).validNel
            case _ =>
              CompileError
                .TypeMismatch(innerType.prettyPrint, rightType.prettyPrint, Some(rightSpan))
                .invalidNel
          }
        }
      case other =>
        CompileError
          .TypeError(
            s"Left side of ?? must be Optional, got ${other.prettyPrint}",
            Some(leftSpan)
          )
          .invalidNel
    }

  // ============================================================================
  // Operator Desugaring
  // ============================================================================

  private def desugarComparison(
      left: TypedExpression,
      op: CompareOp,
      right: TypedExpression,
      span: Span,
      env: TypeEnvironment
  ): TypeResult[TypedExpression] = {
    def opString(op: CompareOp): String = op match {
      case CompareOp.Eq    => "=="
      case CompareOp.NotEq => "!="
      case CompareOp.Lt    => "<"
      case CompareOp.Gt    => ">"
      case CompareOp.LtEq  => "<="
      case CompareOp.GtEq  => ">="
    }

    if left.semanticType != right.semanticType then {
      return CompileError
        .TypeMismatch(
          left.semanticType.prettyPrint,
          right.semanticType.prettyPrint,
          Some(span)
        )
        .invalidNel
    }

    val funcNameResult: TypeResult[String] = (op, left.semanticType) match {
      case (CompareOp.Eq, SInt)       => "eq-int".validNel
      case (CompareOp.Eq, SString)    => "eq-string".validNel
      case (CompareOp.Lt, SInt)       => "lt".validNel
      case (CompareOp.Gt, SInt)       => "gt".validNel
      case (CompareOp.LtEq, SInt)     => "lte".validNel
      case (CompareOp.GtEq, SInt)     => "gte".validNel
      case (CompareOp.NotEq, SInt)    => "eq-int".validNel
      case (CompareOp.NotEq, SString) => "eq-string".validNel
      case _ =>
        CompileError
          .UnsupportedComparison(
            opString(op),
            left.semanticType.prettyPrint,
            right.semanticType.prettyPrint,
            Some(span)
          )
          .invalidNel
    }

    funcNameResult.andThen { funcName =>
      functions.lookupInScope(
        QualifiedName.simple(funcName),
        env.namespaceScope,
        Some(span)
      ) match {
        case Right(sig) =>
          val funcCall = TypedExpression.FunctionCall(
            funcName,
            sig,
            List(left, right),
            ModuleCallOptions.empty,
            None,
            span
          )
          if op == CompareOp.NotEq then {
            functions.lookupInScope(
              QualifiedName.simple("not"),
              env.namespaceScope,
              Some(span)
            ) match {
              case Right(notSig) =>
                TypedExpression
                  .FunctionCall("not", notSig, List(funcCall), ModuleCallOptions.empty, None, span)
                  .validNel
              case Left(err) => err.invalidNel
            }
          } else {
            funcCall.validNel
          }
        case Left(err) => err.invalidNel
      }
    }
  }

  private def desugarArithmetic(
      left: TypedExpression,
      op: ArithOp,
      right: TypedExpression,
      span: Span,
      env: TypeEnvironment
  ): TypeResult[TypedExpression] = {
    def opString(op: ArithOp): String = op match {
      case ArithOp.Add => "+"
      case ArithOp.Sub => "-"
      case ArithOp.Mul => "*"
      case ArithOp.Div => "/"
    }

    def isNumeric(t: SemanticType): Boolean = t match {
      case SInt | SFloat => true
      case _             => false
    }

    def isMergeable(t: SemanticType): Boolean = t match {
      case _: SRecord        => true
      case SList(_: SRecord) => true
      case _                 => false
    }

    if op == ArithOp.Add && isMergeable(left.semanticType) && isMergeable(right.semanticType) then {
      return mergeTypes(left.semanticType, right.semanticType, span).map { merged =>
        TypedExpression.Merge(left, right, merged, span)
      }
    }

    if !isNumeric(left.semanticType) || !isNumeric(right.semanticType) then {
      return CompileError
        .UnsupportedArithmetic(
          opString(op),
          left.semanticType.prettyPrint,
          right.semanticType.prettyPrint,
          Some(span)
        )
        .invalidNel
    }

    val funcName = op match {
      case ArithOp.Add => "add"
      case ArithOp.Sub => "subtract"
      case ArithOp.Mul => "multiply"
      case ArithOp.Div => "divide"
    }

    functions.lookupInScope(QualifiedName.simple(funcName), env.namespaceScope, Some(span)) match {
      case Right(sig) =>
        TypedExpression
          .FunctionCall(funcName, sig, List(left, right), ModuleCallOptions.empty, None, span)
          .validNel
      case Left(err) => err.invalidNel
    }
  }
}

object BidirectionalTypeChecker {

  /** Create a new bidirectional type checker */
  def apply(functions: FunctionRegistry): BidirectionalTypeChecker =
    new BidirectionalTypeChecker(functions)
}
