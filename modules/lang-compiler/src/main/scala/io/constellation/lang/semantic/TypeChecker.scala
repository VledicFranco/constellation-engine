package io.constellation.lang.semantic

import cats.data.{ValidatedNel, Validated}
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

  def lookupType(name: String): Option[SemanticType] = types.get(name)
  def lookupVariable(name: String): Option[SemanticType] = variables.get(name)
}

/** Typed program after type checking */
final case class TypedProgram(
  declarations: List[TypedDeclaration],
  outputs: List[(String, SemanticType, Span)]  // (name, type, span) for each declared output
)

/** Typed declarations */
sealed trait TypedDeclaration {
  def span: Span
}

object TypedDeclaration {
  final case class TypeDef(name: String, definition: SemanticType, span: Span) extends TypedDeclaration
  final case class InputDecl(name: String, semanticType: SemanticType, span: Span) extends TypedDeclaration
  final case class Assignment(name: String, value: TypedExpression, span: Span) extends TypedDeclaration
  final case class OutputDecl(name: String, semanticType: SemanticType, span: Span) extends TypedDeclaration
  final case class UseDecl(path: String, alias: Option[String], span: Span) extends TypedDeclaration
}

/** Typed expressions */
sealed trait TypedExpression {
  def semanticType: SemanticType
  def span: Span
}

object TypedExpression {
  final case class VarRef(name: String, semanticType: SemanticType, span: Span) extends TypedExpression

  final case class FunctionCall(
    name: String,
    signature: FunctionSignature,
    args: List[TypedExpression],
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

  final case class Literal(value: Any, semanticType: SemanticType, span: Span) extends TypedExpression

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
}

/** Type checker for constellation-lang */
object TypeChecker {

  type TypeResult[A] = ValidatedNel[CompileError, A]

  /** Type check a program */
  def check(program: Program, functions: FunctionRegistry): Either[List[CompileError], TypedProgram] = {
    val initialEnv = TypeEnvironment(functions = functions)

    val result = program.declarations
      .foldLeft((initialEnv, List.empty[TypedDeclaration]).validNel[CompileError]) {
        case (Validated.Valid((env, decls)), decl) =>
          checkDeclaration(decl, env).map { case (newEnv, typedDecl) =>
            (newEnv, decls :+ typedDecl)
          }
        case (invalid, _) => invalid
      }
      .map { case (finalEnv, typedDecls) =>
        // Collect output declarations with their types
        val outputs = typedDecls.collect {
          case TypedDeclaration.OutputDecl(name, semanticType, span) =>
            (name, semanticType, span)
        }
        TypedProgram(typedDecls, outputs)
      }

    result.toEither.left.map(_.toList)
  }

  private def checkDeclaration(
    decl: Declaration,
    env: TypeEnvironment
  ): TypeResult[(TypeEnvironment, TypedDeclaration)] = decl match {

    case Declaration.TypeDef(name, defn) =>
      resolveTypeExpr(defn.value, defn.span, env).map { semType =>
        val newEnv = env.addType(name.value, semType)
        val span = Span(name.span.start, defn.span.end)
        (newEnv, TypedDeclaration.TypeDef(name.value, semType, span))
      }

    case Declaration.InputDecl(name, typeExpr) =>
      resolveTypeExpr(typeExpr.value, typeExpr.span, env).map { semType =>
        val newEnv = env.addVariable(name.value, semType)
        val span = Span(name.span.start, typeExpr.span.end)
        (newEnv, TypedDeclaration.InputDecl(name.value, semType, span))
      }

    case Declaration.Assignment(target, value) =>
      checkExpression(value.value, value.span, env).map { typedExpr =>
        val newEnv = env.addVariable(target.value, typedExpr.semanticType)
        val span = Span(target.span.start, value.span.end)
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
      if (!namespaceExists) {
        // Check if any function has this as a prefix
        val hasPrefix = env.functions.all.exists { sig =>
          sig.qualifiedName.startsWith(namespace + ".")
        }
        if (!hasPrefix) {
          CompileError.UndefinedNamespace(namespace, Some(path.span)).invalidNel
        } else {
          // Namespace exists via function qualifiedNames
          val newEnv = aliasOpt match {
            case Some(alias) => env.addAliasedImport(alias.value, namespace)
            case None => env.addWildcardImport(namespace)
          }
          val span = aliasOpt.map(a => Span(path.span.start, a.span.end)).getOrElse(path.span)
          (newEnv, TypedDeclaration.UseDecl(namespace, aliasOpt.map(_.value), span)).validNel
        }
      } else {
        val newEnv = aliasOpt match {
          case Some(alias) => env.addAliasedImport(alias.value, namespace)
          case None => env.addWildcardImport(namespace)
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

    case TypeExpr.Primitive(name) => name match {
      case "String" => SemanticType.SString.validNel
      case "Int" => SemanticType.SInt.validNel
      case "Float" => SemanticType.SFloat.validNel
      case "Boolean" => SemanticType.SBoolean.validNel
      case other => CompileError.UndefinedType(other, Some(span)).invalidNel
    }

    case TypeExpr.TypeRef(name) =>
      env.lookupType(name).toValidNel(
        CompileError.UndefinedType(name, Some(span))
      )

    case TypeExpr.Record(fields) =>
      fields.traverse { case (name, typ) =>
        resolveTypeExpr(typ, span, env).map(name -> _)
      }.map(fs => SemanticType.SRecord(fs.toMap))

    case TypeExpr.Parameterized(name, params) =>
      name match {
        case "Candidates" if params.size == 1 =>
          resolveTypeExpr(params.head, span, env).map(SemanticType.SCandidates(_))
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
  }

  /** Type algebra: merge two types */
  def mergeTypes(left: SemanticType, right: SemanticType, span: Span): TypeResult[SemanticType] =
    (left, right) match {
      case (SemanticType.SRecord(lFields), SemanticType.SRecord(rFields)) =>
        // Right-hand side wins on conflicts
        SemanticType.SRecord(lFields ++ rFields).validNel

      case (SemanticType.SCandidates(SemanticType.SRecord(lFields)),
            SemanticType.SCandidates(SemanticType.SRecord(rFields))) =>
        SemanticType.SCandidates(SemanticType.SRecord(lFields ++ rFields)).validNel

      case (SemanticType.SCandidates(lElem), rRec: SemanticType.SRecord) =>
        mergeTypes(lElem, rRec, span).map(SemanticType.SCandidates(_))

      case (lRec: SemanticType.SRecord, SemanticType.SCandidates(rElem)) =>
        mergeTypes(lRec, rElem, span).map(SemanticType.SCandidates(_))

      case _ =>
        CompileError.IncompatibleMerge(left.prettyPrint, right.prettyPrint, Some(span)).invalidNel
    }

  private def checkExpression(
    expr: Expression,
    span: Span,
    env: TypeEnvironment
  ): TypeResult[TypedExpression] = expr match {

    case Expression.VarRef(name) =>
      env.lookupVariable(name)
        .toValidNel(CompileError.UndefinedVariable(name, Some(span)))
        .map(TypedExpression.VarRef(name, _, span))

    case Expression.FunctionCall(name, args) =>
      env.functions.lookupInScope(name, env.namespaceScope, Some(span)) match {
        case Right(sig) =>
          if (args.size != sig.params.size) {
            CompileError.TypeError(
              s"Function ${name.fullName} expects ${sig.params.size} arguments, got ${args.size}",
              Some(span)
            ).invalidNel
          } else {
            args.zip(sig.params).traverse { case (argExpr, (paramName, paramType)) =>
              checkExpression(argExpr.value, argExpr.span, env).andThen { typedArg =>
                if (isAssignable(typedArg.semanticType, paramType))
                  typedArg.validNel
                else
                  CompileError.TypeMismatch(
                    paramType.prettyPrint,
                    typedArg.semanticType.prettyPrint,
                    Some(argExpr.span)
                  ).invalidNel
              }
            }.map { typedArgs =>
              TypedExpression.FunctionCall(name.fullName, sig, typedArgs, span)
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
              TypedExpression.Projection(typedSource, fields, SemanticType.SRecord(projectedFields), span)
            }

          case SemanticType.SCandidates(SemanticType.SRecord(availableFields)) =>
            checkProjection(fields, availableFields, span).map { projectedFields =>
              TypedExpression.Projection(
                typedSource,
                fields,
                SemanticType.SCandidates(SemanticType.SRecord(projectedFields)),
                span
              )
            }

          case other =>
            CompileError.TypeError(
              s"Projection requires a record type, got ${other.prettyPrint}",
              Some(span)
            ).invalidNel
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
                CompileError.InvalidFieldAccess(field.value, availableFields.keys.toList, Some(field.span)).invalidNel
            }

          case SemanticType.SCandidates(SemanticType.SRecord(availableFields)) =>
            availableFields.get(field.value) match {
              case Some(fieldType) =>
                // Field access on Candidates returns Candidates of the field type
                TypedExpression.FieldAccess(
                  typedSource,
                  field.value,
                  SemanticType.SCandidates(fieldType),
                  span
                ).validNel
              case None =>
                CompileError.InvalidFieldAccess(field.value, availableFields.keys.toList, Some(field.span)).invalidNel
            }

          case other =>
            CompileError.TypeError(
              s"Field access requires a record type, got ${other.prettyPrint}",
              Some(span)
            ).invalidNel
        }
      }

    case Expression.Conditional(cond, thenBr, elseBr) =>
      (
        checkExpression(cond.value, cond.span, env),
        checkExpression(thenBr.value, thenBr.span, env),
        checkExpression(elseBr.value, elseBr.span, env)
      ).mapN { (c, t, e) =>
        if (c.semanticType != SemanticType.SBoolean)
          CompileError.TypeMismatch("Boolean", c.semanticType.prettyPrint, Some(cond.span)).invalidNel
        else if (t.semanticType != e.semanticType)
          CompileError.TypeMismatch(t.semanticType.prettyPrint, e.semanticType.prettyPrint, Some(elseBr.span)).invalidNel
        else
          TypedExpression.Conditional(c, t, e, t.semanticType, span).validNel
      }.andThen(identity)

    case Expression.StringLit(v) =>
      TypedExpression.Literal(v, SemanticType.SString, span).validNel

    case Expression.IntLit(v) =>
      TypedExpression.Literal(v, SemanticType.SInt, span).validNel

    case Expression.FloatLit(v) =>
      TypedExpression.Literal(v, SemanticType.SFloat, span).validNel

    case Expression.BoolLit(v) =>
      TypedExpression.Literal(v, SemanticType.SBoolean, span).validNel

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
            if (l.semanticType != SemanticType.SBoolean)
              Some(CompileError.TypeMismatch("Boolean", l.semanticType.prettyPrint, Some(left.span)))
            else None,
            if (r.semanticType != SemanticType.SBoolean)
              Some(CompileError.TypeMismatch("Boolean", r.semanticType.prettyPrint, Some(right.span)))
            else None
          ).flatten

          if (errors.nonEmpty)
            errors.head.invalidNel
          else
            TypedExpression.BoolBinary(l, op, r, span).validNel
        }
        .andThen(identity)

    case Expression.Not(operand) =>
      checkExpression(operand.value, operand.span, env).andThen { typedOperand =>
        if (typedOperand.semanticType != SemanticType.SBoolean)
          CompileError.TypeMismatch("Boolean", typedOperand.semanticType.prettyPrint, Some(operand.span)).invalidNel
        else
          TypedExpression.Not(typedOperand, span).validNel
      }
  }

  private def checkProjection(
    requested: List[String],
    available: Map[String, SemanticType],
    span: Span
  ): TypeResult[Map[String, SemanticType]] = {
    requested.traverse { field =>
      available.get(field)
        .toValidNel(CompileError.InvalidProjection(field, available.keys.toList, Some(span)))
        .map(field -> _)
    }.map(_.toMap)
  }

  private def isAssignable(actual: SemanticType, expected: SemanticType): Boolean =
    actual == expected // Strict equality for now; can extend with subtyping

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
    if (left.semanticType != right.semanticType) {
      return CompileError.TypeMismatch(
        left.semanticType.prettyPrint,
        right.semanticType.prettyPrint,
        Some(span)
      ).invalidNel
    }

    // Determine the function name based on operator and type
    val funcNameResult: TypeResult[String] = (op, left.semanticType) match {
      // Equality for Int
      case (CompareOp.Eq, SemanticType.SInt) => "eq-int".validNel
      // Equality for String
      case (CompareOp.Eq, SemanticType.SString) => "eq-string".validNel

      // Ordering comparisons only for Int
      case (CompareOp.Lt, SemanticType.SInt)  => "lt".validNel
      case (CompareOp.Gt, SemanticType.SInt)  => "gt".validNel
      case (CompareOp.LtEq, SemanticType.SInt) => "lte".validNel
      case (CompareOp.GtEq, SemanticType.SInt) => "gte".validNel

      // NotEq is handled specially - we wrap the eq result in not()
      case (CompareOp.NotEq, SemanticType.SInt)    => "eq-int".validNel
      case (CompareOp.NotEq, SemanticType.SString) => "eq-string".validNel

      // Unsupported combinations
      case _ =>
        CompileError.UnsupportedComparison(
          opString(op),
          left.semanticType.prettyPrint,
          right.semanticType.prettyPrint,
          Some(span)
        ).invalidNel
    }

    funcNameResult.andThen { funcName =>
      // Look up the function signature
      env.functions.lookupInScope(QualifiedName.simple(funcName), env.namespaceScope, Some(span)) match {
        case Right(sig) =>
          val funcCall = TypedExpression.FunctionCall(funcName, sig, List(left, right), span)

          // For NotEq, wrap in not()
          if (op == CompareOp.NotEq) {
            env.functions.lookupInScope(QualifiedName.simple("not"), env.namespaceScope, Some(span)) match {
              case Right(notSig) =>
                TypedExpression.FunctionCall("not", notSig, List(funcCall), span).validNel
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
      case _ => false
    }

    // Check if a type is mergeable (record-like: Record, Candidates<Record>)
    def isMergeable(t: SemanticType): Boolean = t match {
      case _: SemanticType.SRecord => true
      case SemanticType.SCandidates(_: SemanticType.SRecord) => true
      case SemanticType.SCandidates(_) => false // Candidates of non-record not mergeable
      case _ => false
    }

    // For Add with mergeable types (records or Candidates<Record>), treat as merge
    if (op == ArithOp.Add && isMergeable(left.semanticType) && isMergeable(right.semanticType)) {
      return mergeTypes(left.semanticType, right.semanticType, span).map { merged =>
        TypedExpression.Merge(left, right, merged, span)
      }
    }

    // For arithmetic operations, both operands must be numeric
    if (!isNumeric(left.semanticType) || !isNumeric(right.semanticType)) {
      return CompileError.UnsupportedArithmetic(
        opString(op),
        left.semanticType.prettyPrint,
        right.semanticType.prettyPrint,
        Some(span)
      ).invalidNel
    }

    // Determine the function name based on operator
    val funcName: String = op match {
      case ArithOp.Add => "add"
      case ArithOp.Sub => "subtract"
      case ArithOp.Mul => "multiply"
      case ArithOp.Div => "divide"
    }

    // Look up the function signature
    env.functions.lookupInScope(QualifiedName.simple(funcName), env.namespaceScope, Some(span)) match {
      case Right(sig) =>
        TypedExpression.FunctionCall(funcName, sig, List(left, right), span).validNel
      case Left(err) =>
        err.invalidNel
    }
  }
}
