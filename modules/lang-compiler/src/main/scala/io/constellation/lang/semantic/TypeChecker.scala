package io.constellation.lang.semantic

import cats.data.{Validated, ValidatedNel}
import cats.syntax.all.*
import io.constellation.lang.ast.*
import io.constellation.lang.ast.CompareOp
import io.constellation.lang.ast.ArithOp
import io.constellation.lang.ast.BoolOp

/** Type environment for type checking */
final case class TypeEnvironment(
    types: Map[String, SemanticType] = Map.empty,
    variables: Map[String, SemanticType] = Map.empty,
    functions: FunctionRegistry,
    namespaceScope: NamespaceScope = NamespaceScope.empty
) {
  def addType(name: String, typ: SemanticType): TypeEnvironment =
    copy(types = types + (name -> typ))

  def addVariable(name: String, typ: SemanticType): TypeEnvironment =
    copy(variables = variables + (name -> typ))

  def addWildcardImport(namespace: String): TypeEnvironment =
    copy(namespaceScope = namespaceScope.addWildcard(namespace))

  def addAliasedImport(alias: String, namespace: String): TypeEnvironment =
    copy(namespaceScope = namespaceScope.addAlias(alias, namespace))

  def lookupType(name: String): Option[SemanticType]     = types.get(name)
  def lookupVariable(name: String): Option[SemanticType] = variables.get(name)
}

/** Typed pipeline after type checking */
final case class TypedPipeline(
    declarations: List[TypedDeclaration],
    outputs: List[(String, SemanticType, Span)], // (name, type, span) for each declared output
    warnings: List[CompileWarning] = Nil
)

/** Typed declarations */
sealed trait TypedDeclaration {
  def span: Span
}

object TypedDeclaration {
  final case class TypeDef(name: String, definition: SemanticType, span: Span)
      extends TypedDeclaration
  final case class InputDecl(name: String, semanticType: SemanticType, span: Span)
      extends TypedDeclaration
  final case class Assignment(name: String, value: TypedExpression, span: Span)
      extends TypedDeclaration
  final case class OutputDecl(name: String, semanticType: SemanticType, span: Span)
      extends TypedDeclaration
  final case class UseDecl(path: String, alias: Option[String], span: Span) extends TypedDeclaration
}

/** Typed expressions */
sealed trait TypedExpression {
  def semanticType: SemanticType
  def span: Span
}

object TypedExpression {
  final case class VarRef(name: String, semanticType: SemanticType, span: Span)
      extends TypedExpression

  final case class FunctionCall(
      name: String,
      signature: FunctionSignature,
      args: List[TypedExpression],
      options: ModuleCallOptions,
      typedFallback: Option[
        TypedExpression
      ], // Typed fallback expression (if options.fallback is set)
      span: Span
  ) extends TypedExpression {
    def semanticType: SemanticType = signature.returns
  }

  final case class Merge(
      left: TypedExpression,
      right: TypedExpression,
      semanticType: SemanticType,
      span: Span
  ) extends TypedExpression

  final case class Projection(
      source: TypedExpression,
      fields: List[String],
      semanticType: SemanticType,
      span: Span
  ) extends TypedExpression

  final case class FieldAccess(
      source: TypedExpression,
      field: String,
      semanticType: SemanticType,
      span: Span
  ) extends TypedExpression

  final case class Conditional(
      condition: TypedExpression,
      thenBranch: TypedExpression,
      elseBranch: TypedExpression,
      semanticType: SemanticType,
      span: Span
  ) extends TypedExpression

  final case class Literal(value: Any, semanticType: SemanticType, span: Span)
      extends TypedExpression

  /** List literal: [1, 2, 3] or ["a", "b", "c"] Contains typed element expressions.
    */
  final case class ListLiteral(
      elements: List[TypedExpression],
      elementType: SemanticType,
      span: Span
  ) extends TypedExpression {
    def semanticType: SemanticType = SemanticType.SList(elementType)
  }

  /** String interpolation: "Hello, ${name}!" Contains N+1 string parts for N interpolated
    * expressions.
    */
  final case class StringInterpolation(
      parts: List[String],
      expressions: List[TypedExpression],
      span: Span
  ) extends TypedExpression {
    def semanticType: SemanticType = SemanticType.SString
  }

  /** Boolean binary expression: and, or */
  final case class BoolBinary(
      left: TypedExpression,
      op: BoolOp,
      right: TypedExpression,
      span: Span
  ) extends TypedExpression {
    def semanticType: SemanticType = SemanticType.SBoolean
  }

  /** Boolean negation: not */
  final case class Not(
      operand: TypedExpression,
      span: Span
  ) extends TypedExpression {
    def semanticType: SemanticType = SemanticType.SBoolean
  }

  /** Guard expression: expr when condition Returns Optional<T> where T is the type of expr. If
    * condition is true, returns Some(expr), else returns None.
    */
  final case class Guard(
      expr: TypedExpression,
      condition: TypedExpression,
      span: Span
  ) extends TypedExpression {
    def semanticType: SemanticType = SemanticType.SOptional(expr.semanticType)
  }

  /** Coalesce expression: optional ?? fallback If left is Some(v), returns v. If None, returns
    * right. Return type depends on right operand:
    *   - Optional<T> ?? T -> T
    *   - Optional<T> ?? Optional<T> -> Optional<T>
    */
  final case class Coalesce(
      left: TypedExpression,
      right: TypedExpression,
      span: Span,
      resultType: SemanticType
  ) extends TypedExpression {
    def semanticType: SemanticType = resultType
  }

  /** Branch expression: multi-way conditional Evaluates conditions in order, returns first matching
    * expression. 'otherwise' provides the default case.
    */
  final case class Branch(
      cases: List[(TypedExpression, TypedExpression)], // condition -> expression pairs
      otherwise: TypedExpression,
      semanticType: SemanticType,
      span: Span
  ) extends TypedExpression

  /** Lambda expression: (x, y) => x + y A function literal with typed parameters and a body.
    */
  final case class Lambda(
      params: List[(String, SemanticType)], // parameter name -> type
      body: TypedExpression,
      semanticType: SemanticType.SFunction,
      span: Span
  ) extends TypedExpression
}

/** Type checker for constellation-lang.
  *
  * This object provides the main entry point for type checking. It delegates to
  * `BidirectionalTypeChecker` which implements bidirectional type inference.
  *
  * Bidirectional type checking enables:
  *   - Lambda parameter type inference from context
  *   - Empty list typing from expected type
  *   - Better error messages with contextual information
  */
object TypeChecker {

  type TypeResult[A] = ValidatedNel[CompileError, A]

  /** Type check a pipeline using bidirectional type inference.
    *
    * @param program
    *   The parsed pipeline to type check
    * @param functions
    *   Registry of available function signatures
    * @return
    *   Either a list of type errors, or a fully typed pipeline
    */
  def check(
      program: Pipeline,
      functions: FunctionRegistry
  ): Either[List[CompileError], TypedPipeline] =
    // Delegate to bidirectional type checker
    BidirectionalTypeChecker(functions).check(program)

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
        // Validate @example annotations - example type must match input type
        val annotationValidation: TypeResult[Unit] = annotations.traverse {
          case Annotation.Example(exprLoc) =>
            checkExpression(exprLoc.value, exprLoc.span, env).andThen { typedExpr =>
              if isAssignable(typedExpr.semanticType, semType) then ().validNel
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
      checkExpression(value.value, value.span, env).map { typedExpr =>
        val newEnv = env.addVariable(target.value, typedExpr.semanticType)
        val span   = Span(target.span.start, value.span.end)
        (newEnv, TypedDeclaration.Assignment(target.value, typedExpr, span))
      }

    case Declaration.OutputDecl(name) =>
      // Check that the output variable exists in scope
      env.lookupVariable(name.value) match {
        case Some(semanticType) =>
          (env, TypedDeclaration.OutputDecl(name.value, semanticType, name.span)).validNel
        case None =>
          CompileError.UndefinedVariable(name.value, Some(name.span)).invalidNel
      }

    case Declaration.UseDecl(path, aliasOpt) =>
      val namespace = path.value.fullName
      // Verify the namespace exists in the registry
      val namespaceExists = env.functions.namespaces.exists { ns =>
        ns == namespace || ns.startsWith(namespace + ".")
      }
      if !namespaceExists then {
        // Check if any function has this as a prefix
        val hasPrefix = env.functions.all.exists { sig =>
          sig.qualifiedName.startsWith(namespace + ".")
        }
        if !hasPrefix then {
          CompileError.UndefinedNamespace(namespace, Some(path.span)).invalidNel
        } else {
          // Namespace exists via function qualifiedNames
          val newEnv = aliasOpt match {
            case Some(alias) => env.addAliasedImport(alias.value, namespace)
            case None        => env.addWildcardImport(namespace)
          }
          val span = aliasOpt.map(a => Span(path.span.start, a.span.end)).getOrElse(path.span)
          (newEnv, TypedDeclaration.UseDecl(namespace, aliasOpt.map(_.value), span)).validNel
        }
      } else {
        val newEnv = aliasOpt match {
          case Some(alias) => env.addAliasedImport(alias.value, namespace)
          case None        => env.addWildcardImport(namespace)
        }
        val span = aliasOpt.map(a => Span(path.span.start, a.span.end)).getOrElse(path.span)
        (newEnv, TypedDeclaration.UseDecl(namespace, aliasOpt.map(_.value), span)).validNel
      }
  }

  private def resolveTypeExpr(
      typeExpr: TypeExpr,
      span: Span,
      env: TypeEnvironment
  ): TypeResult[SemanticType] = typeExpr match {

    case TypeExpr.Primitive(name) =>
      name match {
        case "String"  => SemanticType.SString.validNel
        case "Int"     => SemanticType.SInt.validNel
        case "Float"   => SemanticType.SFloat.validNel
        case "Boolean" => SemanticType.SBoolean.validNel
        case other     => CompileError.UndefinedType(other, Some(span)).invalidNel
      }

    case TypeExpr.TypeRef(name) =>
      env
        .lookupType(name)
        .toValidNel(
          CompileError.UndefinedType(name, Some(span))
        )

    case TypeExpr.Record(fields) =>
      fields
        .traverse { case (name, typ) =>
          resolveTypeExpr(typ, span, env).map(name -> _)
        }
        .map(fs => SemanticType.SRecord(fs.toMap))

    case TypeExpr.Parameterized(name, params) =>
      name match {
        // "Candidates" is a legacy alias for "List" - both support element-wise operations
        case "Candidates" if params.size == 1 =>
          resolveTypeExpr(params.head, span, env).map(SemanticType.SList(_))
        case "List" if params.size == 1 =>
          resolveTypeExpr(params.head, span, env).map(SemanticType.SList(_))
        case "Map" if params.size == 2 =>
          (resolveTypeExpr(params(0), span, env), resolveTypeExpr(params(1), span, env))
            .mapN(SemanticType.SMap(_, _))
        case "Optional" if params.size == 1 =>
          resolveTypeExpr(params.head, span, env).map(SemanticType.SOptional(_))
        case _ =>
          CompileError.UndefinedType(s"$name<...>", Some(span)).invalidNel
      }

    case TypeExpr.TypeMerge(left, right) =>
      (resolveTypeExpr(left, span, env), resolveTypeExpr(right, span, env))
        .mapN((l, r) => mergeTypes(l, r, span))
        .andThen(identity)

    case TypeExpr.Union(members) =>
      members
        .traverse(m => resolveTypeExpr(m, span, env))
        .map { resolvedMembers =>
          // Flatten nested unions and collect into a set
          val flattened = resolvedMembers.flatMap {
            case SemanticType.SUnion(innerMembers) => innerMembers.toList
            case other                             => List(other)
          }.toSet
          // If only one member after flattening, return it directly
          if flattened.size == 1 then flattened.head
          else SemanticType.SUnion(flattened)
        }
  }

  /** Type algebra: merge two types */
  def mergeTypes(left: SemanticType, right: SemanticType, span: Span): TypeResult[SemanticType] =
    (left, right) match {
      case (SemanticType.SRecord(lFields), SemanticType.SRecord(rFields)) =>
        // Right-hand side wins on conflicts
        SemanticType.SRecord(lFields ++ rFields).validNel

      // List<Record> + List<Record> = merge records element-wise
      case (
            SemanticType.SList(SemanticType.SRecord(lFields)),
            SemanticType.SList(SemanticType.SRecord(rFields))
          ) =>
        SemanticType.SList(SemanticType.SRecord(lFields ++ rFields)).validNel

      // List<Record> + Record = add fields to each element
      case (SemanticType.SList(lElem), rRec: SemanticType.SRecord) =>
        mergeTypes(lElem, rRec, span).map(SemanticType.SList(_))

      // Record + List<Record> = add fields to each element
      case (lRec: SemanticType.SRecord, SemanticType.SList(rElem)) =>
        mergeTypes(lRec, rElem, span).map(SemanticType.SList(_))

      case _ =>
        CompileError.IncompatibleMerge(left.prettyPrint, right.prettyPrint, Some(span)).invalidNel
    }

  private def checkExpression(
      expr: Expression,
      span: Span,
      env: TypeEnvironment
  ): TypeResult[TypedExpression] = expr match {

    case Expression.VarRef(name) =>
      env
        .lookupVariable(name)
        .toValidNel(CompileError.UndefinedVariable(name, Some(span)))
        .map(TypedExpression.VarRef(name, _, span))

    case Expression.FunctionCall(name, args, _) =>
      env.functions.lookupInScope(name, env.namespaceScope, Some(span)) match {
        case Right(sig) =>
          if args.size != sig.params.size then {
            CompileError
              .TypeError(
                s"Function ${name.fullName} expects ${sig.params.size} arguments, got ${args.size}",
                Some(span)
              )
              .invalidNel
          } else {
            args
              .zip(sig.params)
              .traverse { case (argExpr, (paramName, paramType)) =>
                // Check if argument is a lambda and parameter expects a function type
                (argExpr.value, paramType) match {
                  case (lambda: Expression.Lambda, funcType: SemanticType.SFunction) =>
                    // Use type-directed inference for lambda
                    checkLambdaWithExpected(lambda, funcType, argExpr.span, env)
                  case _ =>
                    // Regular expression type checking
                    checkExpression(argExpr.value, argExpr.span, env).andThen { typedArg =>
                      if isAssignable(typedArg.semanticType, paramType) then typedArg.validNel
                      else
                        CompileError
                          .TypeMismatch(
                            paramType.prettyPrint,
                            typedArg.semanticType.prettyPrint,
                            Some(argExpr.span)
                          )
                          .invalidNel
                    }
                }
              }
              .map { typedArgs =>
                TypedExpression.FunctionCall(
                  name.fullName,
                  sig,
                  typedArgs,
                  ModuleCallOptions.empty,
                  None,
                  span
                )
              }
          }
        case Left(error) =>
          error.invalidNel
      }

    case Expression.Merge(left, right) =>
      (checkExpression(left.value, left.span, env), checkExpression(right.value, right.span, env))
        .mapN { (l, r) =>
          mergeTypes(l.semanticType, r.semanticType, span).map { merged =>
            TypedExpression.Merge(l, r, merged, span)
          }
        }
        .andThen(identity)

    case Expression.Projection(source, fields) =>
      checkExpression(source.value, source.span, env).andThen { typedSource =>
        typedSource.semanticType match {
          case SemanticType.SRecord(availableFields) =>
            checkProjection(fields, availableFields, span).map { projectedFields =>
              TypedExpression.Projection(
                typedSource,
                fields,
                SemanticType.SRecord(projectedFields),
                span
              )
            }

          // List<Record> projection: select fields from each element
          case SemanticType.SList(SemanticType.SRecord(availableFields)) =>
            checkProjection(fields, availableFields, span).map { projectedFields =>
              TypedExpression.Projection(
                typedSource,
                fields,
                SemanticType.SList(SemanticType.SRecord(projectedFields)),
                span
              )
            }

          case other =>
            CompileError
              .TypeError(
                s"Projection requires a record type, got ${other.prettyPrint}",
                Some(span)
              )
              .invalidNel
        }
      }

    case Expression.FieldAccess(source, field) =>
      checkExpression(source.value, source.span, env).andThen { typedSource =>
        typedSource.semanticType match {
          case SemanticType.SRecord(availableFields) =>
            availableFields.get(field.value) match {
              case Some(fieldType) =>
                TypedExpression.FieldAccess(typedSource, field.value, fieldType, span).validNel
              case None =>
                CompileError
                  .InvalidFieldAccess(field.value, availableFields.keys.toList, Some(field.span))
                  .invalidNel
            }

          // List<Record> field access: extract field from each element
          case SemanticType.SList(SemanticType.SRecord(availableFields)) =>
            availableFields.get(field.value) match {
              case Some(fieldType) =>
                // Field access on List<Record> returns List of the field type
                TypedExpression
                  .FieldAccess(
                    typedSource,
                    field.value,
                    SemanticType.SList(fieldType),
                    span
                  )
                  .validNel
              case None =>
                CompileError
                  .InvalidFieldAccess(field.value, availableFields.keys.toList, Some(field.span))
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
      }

    case Expression.Conditional(cond, thenBr, elseBr) =>
      (
        checkExpression(cond.value, cond.span, env),
        checkExpression(thenBr.value, thenBr.span, env),
        checkExpression(elseBr.value, elseBr.span, env)
      ).mapN { (c, t, e) =>
        if c.semanticType != SemanticType.SBoolean then
          CompileError
            .TypeMismatch("Boolean", c.semanticType.prettyPrint, Some(cond.span))
            .invalidNel
        else {
          // Use LUB to find common type for branches
          val resultType = Subtyping.lub(t.semanticType, e.semanticType)
          TypedExpression.Conditional(c, t, e, resultType, span).validNel
        }
      }.andThen(identity)

    case Expression.StringLit(v) =>
      TypedExpression.Literal(v, SemanticType.SString, span).validNel

    case Expression.StringInterpolation(parts, expressions) =>
      // Type check all interpolated expressions
      // Any type is allowed in interpolations - they'll be converted to strings at runtime
      expressions
        .traverse { locExpr =>
          checkExpression(locExpr.value, locExpr.span, env)
        }
        .map { typedExprs =>
          TypedExpression.StringInterpolation(parts, typedExprs, span)
        }

    case Expression.IntLit(v) =>
      TypedExpression.Literal(v, SemanticType.SInt, span).validNel

    case Expression.FloatLit(v) =>
      TypedExpression.Literal(v, SemanticType.SFloat, span).validNel

    case Expression.BoolLit(v) =>
      TypedExpression.Literal(v, SemanticType.SBoolean, span).validNel

    case Expression.ListLit(elements) =>
      if elements.isEmpty then
        // Empty list literal - use SNothing as element type (compatible with any List<T>)
        TypedExpression.ListLiteral(Nil, SemanticType.SNothing, span).validNel
      else
        // Type check each element
        elements.traverse(elem => checkExpression(elem.value, elem.span, env)).andThen {
          typedElements =>
            // Find common type for all elements using LUB
            val elementType = Subtyping.commonType(typedElements.map(_.semanticType))
            TypedExpression.ListLiteral(typedElements, elementType, span).validNel
        }

    case Expression.Compare(left, op, right) =>
      (checkExpression(left.value, left.span, env), checkExpression(right.value, right.span, env))
        .mapN { (l, r) =>
          desugarComparison(l, op, r, span, env)
        }
        .andThen(identity)

    case Expression.Arithmetic(left, op, right) =>
      (checkExpression(left.value, left.span, env), checkExpression(right.value, right.span, env))
        .mapN { (l, r) =>
          desugarArithmetic(l, op, r, span, env)
        }
        .andThen(identity)

    case Expression.BoolBinary(left, op, right) =>
      (checkExpression(left.value, left.span, env), checkExpression(right.value, right.span, env))
        .mapN { (l, r) =>
          val errors = List(
            if l.semanticType != SemanticType.SBoolean then
              Some(
                CompileError.TypeMismatch("Boolean", l.semanticType.prettyPrint, Some(left.span))
              )
            else None,
            if r.semanticType != SemanticType.SBoolean then
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
      checkExpression(operand.value, operand.span, env).andThen { typedOperand =>
        if typedOperand.semanticType != SemanticType.SBoolean then
          CompileError
            .TypeMismatch("Boolean", typedOperand.semanticType.prettyPrint, Some(operand.span))
            .invalidNel
        else TypedExpression.Not(typedOperand, span).validNel
      }

    case Expression.Guard(expr, condition) =>
      (
        checkExpression(expr.value, expr.span, env),
        checkExpression(condition.value, condition.span, env)
      )
        .mapN { (typedExpr, typedCondition) =>
          if typedCondition.semanticType != SemanticType.SBoolean then
            CompileError
              .TypeMismatch(
                "Boolean",
                typedCondition.semanticType.prettyPrint,
                Some(condition.span)
              )
              .invalidNel
          else TypedExpression.Guard(typedExpr, typedCondition, span).validNel
        }
        .andThen(identity)

    case Expression.Coalesce(left, right) =>
      (checkExpression(left.value, left.span, env), checkExpression(right.value, right.span, env))
        .mapN { (typedLeft, typedRight) =>
          typedLeft.semanticType match {
            case SemanticType.SOptional(innerType) =>
              val rightType = typedRight.semanticType
              // First check: Optional<T> ?? T -> T (even if T is Optional)
              if innerType == rightType then
                TypedExpression.Coalesce(typedLeft, typedRight, span, rightType).validNel
              // Second check: Optional<T> ?? Optional<T> -> Optional<T> (when inner types match)
              else
                rightType match {
                  case SemanticType.SOptional(rightInner) if innerType == rightInner =>
                    TypedExpression
                      .Coalesce(typedLeft, typedRight, span, SemanticType.SOptional(innerType))
                      .validNel
                  case _ =>
                    CompileError
                      .TypeMismatch(innerType.prettyPrint, rightType.prettyPrint, Some(right.span))
                      .invalidNel
                }
            case other =>
              CompileError
                .TypeError(
                  s"Left side of ?? must be Optional, got ${other.prettyPrint}",
                  Some(left.span)
                )
                .invalidNel
          }
        }
        .andThen(identity)

    case Expression.Branch(cases, otherwise) =>
      // Type check all conditions and expressions
      val casesResult = cases.traverse { case (cond, expr) =>
        (checkExpression(cond.value, cond.span, env), checkExpression(expr.value, expr.span, env))
          .mapN { (typedCond, typedExpr) =>
            // Verify condition is Boolean
            if typedCond.semanticType != SemanticType.SBoolean then
              CompileError
                .TypeMismatch("Boolean", typedCond.semanticType.prettyPrint, Some(cond.span))
                .invalidNel
            else (typedCond, typedExpr).validNel
          }
          .andThen(identity)
      }

      val otherwiseResult = checkExpression(otherwise.value, otherwise.span, env)

      (casesResult, otherwiseResult)
        .mapN { (typedCases, typedOtherwise) =>
          // Find common type for all branch results using LUB
          val allExprs   = typedCases.map(_._2) :+ typedOtherwise
          val resultType = Subtyping.commonType(allExprs.map(_.semanticType))
          TypedExpression.Branch(typedCases, typedOtherwise, resultType, span).validNel
        }
        .andThen(identity)

    case Expression.Lambda(params, body) =>
      // Lambda expressions can only be type-checked in the context of a function call
      // where we know the expected function type. If we get here without context,
      // it means the lambda is used in an unsupported context.
      // For now, require explicit type annotations on all parameters
      val paramTypesResult = params.traverse { param =>
        param.typeAnnotation match {
          case Some(typeExpr) =>
            resolveTypeExpr(typeExpr.value, typeExpr.span, env).map(t => param.name.value -> t)
          case None =>
            CompileError
              .TypeError(
                s"Lambda parameter '${param.name.value}' requires a type annotation in this context",
                Some(param.name.span)
              )
              .invalidNel
        }
      }

      paramTypesResult.andThen { paramTypes =>
        // Create environment with lambda parameters bound
        val lambdaEnv = paramTypes.foldLeft(env) { case (e, (name, typ)) =>
          e.addVariable(name, typ)
        }
        // Type check the body
        checkExpression(body.value, body.span, lambdaEnv).map { typedBody =>
          val funcType = SemanticType.SFunction(paramTypes.map(_._2), typedBody.semanticType)
          TypedExpression.Lambda(paramTypes, typedBody, funcType, span)
        }
      }
  }

  /** Type check a lambda expression with expected type context for type inference */
  private def checkLambdaWithExpected(
      lambda: Expression.Lambda,
      expectedType: SemanticType.SFunction,
      span: Span,
      env: TypeEnvironment
  ): TypeResult[TypedExpression.Lambda] = {
    val params = lambda.params
    val body   = lambda.body

    // Validate parameter count
    if params.size != expectedType.paramTypes.size then {
      return CompileError
        .TypeError(
          s"Lambda has ${params.size} parameters but expected ${expectedType.paramTypes.size}",
          Some(span)
        )
        .invalidNel
    }

    // Infer parameter types from expected type, or use explicit annotations
    val paramTypesResult =
      params.zip(expectedType.paramTypes).traverse { case (param, expectedParamType) =>
        param.typeAnnotation match {
          case Some(typeExpr) =>
            // Explicit type annotation - validate it's compatible with expected
            // For parameters, expected must be subtype of annotated (contravariance)
            resolveTypeExpr(typeExpr.value, typeExpr.span, env).andThen { annotatedType =>
              if Subtyping.isSubtype(expectedParamType, annotatedType) then
                (param.name.value -> annotatedType).validNel
              else
                CompileError
                  .TypeMismatch(
                    expectedParamType.prettyPrint,
                    annotatedType.prettyPrint,
                    Some(typeExpr.span)
                  )
                  .invalidNel
            }
          case None =>
            // Infer from expected type
            (param.name.value -> expectedParamType).validNel
        }
      }

    paramTypesResult.andThen { paramTypes =>
      // Create environment with lambda parameters bound
      val lambdaEnv = paramTypes.foldLeft(env) { case (e, (name, typ)) =>
        e.addVariable(name, typ)
      }
      // Type check the body
      checkExpression(body.value, body.span, lambdaEnv).andThen { typedBody =>
        // Validate return type is subtype of expected
        if Subtyping.isSubtype(typedBody.semanticType, expectedType.returnType) then {
          val funcType = SemanticType.SFunction(paramTypes.map(_._2), typedBody.semanticType)
          TypedExpression.Lambda(paramTypes, typedBody, funcType, span).validNel
        } else {
          CompileError
            .TypeMismatch(
              expectedType.returnType.prettyPrint,
              typedBody.semanticType.prettyPrint,
              Some(body.span)
            )
            .invalidNel
        }
      }
    }
  }

  private def checkProjection(
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

  private def isAssignable(actual: SemanticType, expected: SemanticType): Boolean =
    Subtyping.isAssignable(actual, expected)

  /** Desugar comparison operator to stdlib function call */
  private def desugarComparison(
      left: TypedExpression,
      op: CompareOp,
      right: TypedExpression,
      span: Span,
      env: TypeEnvironment
  ): TypeResult[TypedExpression] = {
    // Helper to format operator for error messages
    def opString(op: CompareOp): String = op match {
      case CompareOp.Eq    => "=="
      case CompareOp.NotEq => "!="
      case CompareOp.Lt    => "<"
      case CompareOp.Gt    => ">"
      case CompareOp.LtEq  => "<="
      case CompareOp.GtEq  => ">="
    }

    // First check that both operands have the same type
    if left.semanticType != right.semanticType then {
      return CompileError
        .TypeMismatch(
          left.semanticType.prettyPrint,
          right.semanticType.prettyPrint,
          Some(span)
        )
        .invalidNel
    }

    // Determine the function name based on operator and type
    val funcNameResult: TypeResult[String] = (op, left.semanticType) match {
      // Equality for Int
      case (CompareOp.Eq, SemanticType.SInt) => "eq-int".validNel
      // Equality for String
      case (CompareOp.Eq, SemanticType.SString) => "eq-string".validNel

      // Ordering comparisons only for Int
      case (CompareOp.Lt, SemanticType.SInt)   => "lt".validNel
      case (CompareOp.Gt, SemanticType.SInt)   => "gt".validNel
      case (CompareOp.LtEq, SemanticType.SInt) => "lte".validNel
      case (CompareOp.GtEq, SemanticType.SInt) => "gte".validNel

      // NotEq is handled specially - we wrap the eq result in not()
      case (CompareOp.NotEq, SemanticType.SInt)    => "eq-int".validNel
      case (CompareOp.NotEq, SemanticType.SString) => "eq-string".validNel

      // Unsupported combinations
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
      // Look up the function signature
      env.functions.lookupInScope(
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

          // For NotEq, wrap in not()
          if op == CompareOp.NotEq then {
            env.functions.lookupInScope(
              QualifiedName.simple("not"),
              env.namespaceScope,
              Some(span)
            ) match {
              case Right(notSig) =>
                TypedExpression
                  .FunctionCall("not", notSig, List(funcCall), ModuleCallOptions.empty, None, span)
                  .validNel
              case Left(err) =>
                err.invalidNel
            }
          } else {
            funcCall.validNel
          }

        case Left(err) =>
          err.invalidNel
      }
    }
  }

  /** Desugar arithmetic operator to stdlib function call or merge */
  private def desugarArithmetic(
      left: TypedExpression,
      op: ArithOp,
      right: TypedExpression,
      span: Span,
      env: TypeEnvironment
  ): TypeResult[TypedExpression] = {
    // Helper to format operator for error messages
    def opString(op: ArithOp): String = op match {
      case ArithOp.Add => "+"
      case ArithOp.Sub => "-"
      case ArithOp.Mul => "*"
      case ArithOp.Div => "/"
    }

    // Check if a type is numeric
    def isNumeric(t: SemanticType): Boolean = t match {
      case SemanticType.SInt | SemanticType.SFloat => true
      case _                                       => false
    }

    // Check if a type is mergeable (record-like: Record, List<Record>)
    def isMergeable(t: SemanticType): Boolean = t match {
      case _: SemanticType.SRecord                     => true
      case SemanticType.SList(_: SemanticType.SRecord) => true
      case SemanticType.SList(_)                       => false // List of non-record not mergeable
      case _                                           => false
    }

    // For Add with mergeable types (records or List<Record>), treat as merge
    if op == ArithOp.Add && isMergeable(left.semanticType) && isMergeable(right.semanticType) then {
      return mergeTypes(left.semanticType, right.semanticType, span).map { merged =>
        TypedExpression.Merge(left, right, merged, span)
      }
    }

    // For arithmetic operations, both operands must be numeric
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

    // Determine the function name based on operator
    val funcName: String = op match {
      case ArithOp.Add => "add"
      case ArithOp.Sub => "subtract"
      case ArithOp.Mul => "multiply"
      case ArithOp.Div => "divide"
    }

    // Look up the function signature
    env.functions.lookupInScope(
      QualifiedName.simple(funcName),
      env.namespaceScope,
      Some(span)
    ) match {
      case Right(sig) =>
        TypedExpression
          .FunctionCall(funcName, sig, List(left, right), ModuleCallOptions.empty, None, span)
          .validNel
      case Left(err) =>
        err.invalidNel
    }
  }
}
