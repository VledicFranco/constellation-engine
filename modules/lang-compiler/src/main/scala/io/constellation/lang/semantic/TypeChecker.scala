package io.constellation.lang.semantic

import cats.data.{ValidatedNel, Validated}
import cats.syntax.all.*
import io.constellation.lang.ast.*

/** Type environment for type checking */
final case class TypeEnvironment(
  types: Map[String, SemanticType] = Map.empty,
  variables: Map[String, SemanticType] = Map.empty,
  functions: FunctionRegistry
) {
  def addType(name: String, typ: SemanticType): TypeEnvironment =
    copy(types = types + (name -> typ))

  def addVariable(name: String, typ: SemanticType): TypeEnvironment =
    copy(variables = variables + (name -> typ))

  def lookupType(name: String): Option[SemanticType] = types.get(name)
  def lookupVariable(name: String): Option[SemanticType] = variables.get(name)
}

/** Typed program after type checking */
final case class TypedProgram(
  declarations: List[TypedDeclaration],
  output: TypedExpression,
  outputType: SemanticType
)

/** Typed declarations */
sealed trait TypedDeclaration

object TypedDeclaration {
  final case class TypeDef(name: String, definition: SemanticType) extends TypedDeclaration
  final case class InputDecl(name: String, semanticType: SemanticType) extends TypedDeclaration
  final case class Assignment(name: String, value: TypedExpression) extends TypedDeclaration
}

/** Typed expressions */
sealed trait TypedExpression {
  def semanticType: SemanticType
}

object TypedExpression {
  final case class VarRef(name: String, semanticType: SemanticType) extends TypedExpression

  final case class FunctionCall(
    name: String,
    signature: FunctionSignature,
    args: List[TypedExpression]
  ) extends TypedExpression {
    def semanticType: SemanticType = signature.returns
  }

  final case class Merge(
    left: TypedExpression,
    right: TypedExpression,
    semanticType: SemanticType
  ) extends TypedExpression

  final case class Projection(
    source: TypedExpression,
    fields: List[String],
    semanticType: SemanticType
  ) extends TypedExpression

  final case class Conditional(
    condition: TypedExpression,
    thenBranch: TypedExpression,
    elseBranch: TypedExpression,
    semanticType: SemanticType
  ) extends TypedExpression

  final case class Literal(value: Any, semanticType: SemanticType) extends TypedExpression
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
      .andThen { case (finalEnv, typedDecls) =>
        checkExpression(program.output.value, program.output.pos, finalEnv).map { typedOutput =>
          TypedProgram(typedDecls, typedOutput, typedOutput.semanticType)
        }
      }

    result.toEither.left.map(_.toList)
  }

  private def checkDeclaration(
    decl: Declaration,
    env: TypeEnvironment
  ): TypeResult[(TypeEnvironment, TypedDeclaration)] = decl match {

    case Declaration.TypeDef(name, defn) =>
      resolveTypeExpr(defn.value, defn.pos, env).map { semType =>
        val newEnv = env.addType(name.value, semType)
        (newEnv, TypedDeclaration.TypeDef(name.value, semType))
      }

    case Declaration.InputDecl(name, typeExpr) =>
      resolveTypeExpr(typeExpr.value, typeExpr.pos, env).map { semType =>
        val newEnv = env.addVariable(name.value, semType)
        (newEnv, TypedDeclaration.InputDecl(name.value, semType))
      }

    case Declaration.Assignment(target, value) =>
      checkExpression(value.value, value.pos, env).map { typedExpr =>
        val newEnv = env.addVariable(target.value, typedExpr.semanticType)
        (newEnv, TypedDeclaration.Assignment(target.value, typedExpr))
      }
  }

  private def resolveTypeExpr(
    typeExpr: TypeExpr,
    pos: Position,
    env: TypeEnvironment
  ): TypeResult[SemanticType] = typeExpr match {

    case TypeExpr.Primitive(name) => name match {
      case "String" => SemanticType.SString.validNel
      case "Int" => SemanticType.SInt.validNel
      case "Float" => SemanticType.SFloat.validNel
      case "Boolean" => SemanticType.SBoolean.validNel
      case other => CompileError.UndefinedType(other, Some(pos)).invalidNel
    }

    case TypeExpr.TypeRef(name) =>
      env.lookupType(name).toValidNel(
        CompileError.UndefinedType(name, Some(pos))
      )

    case TypeExpr.Record(fields) =>
      fields.traverse { case (name, typ) =>
        resolveTypeExpr(typ, pos, env).map(name -> _)
      }.map(fs => SemanticType.SRecord(fs.toMap))

    case TypeExpr.Parameterized(name, params) =>
      name match {
        case "Candidates" if params.size == 1 =>
          resolveTypeExpr(params.head, pos, env).map(SemanticType.SCandidates(_))
        case "List" if params.size == 1 =>
          resolveTypeExpr(params.head, pos, env).map(SemanticType.SList(_))
        case "Map" if params.size == 2 =>
          (resolveTypeExpr(params(0), pos, env), resolveTypeExpr(params(1), pos, env))
            .mapN(SemanticType.SMap(_, _))
        case _ =>
          CompileError.UndefinedType(s"$name<...>", Some(pos)).invalidNel
      }

    case TypeExpr.TypeMerge(left, right) =>
      (resolveTypeExpr(left, pos, env), resolveTypeExpr(right, pos, env))
        .mapN((l, r) => mergeTypes(l, r, pos))
        .andThen(identity)
  }

  /** Type algebra: merge two types */
  def mergeTypes(left: SemanticType, right: SemanticType, pos: Position): TypeResult[SemanticType] =
    (left, right) match {
      case (SemanticType.SRecord(lFields), SemanticType.SRecord(rFields)) =>
        // Right-hand side wins on conflicts
        SemanticType.SRecord(lFields ++ rFields).validNel

      case (SemanticType.SCandidates(SemanticType.SRecord(lFields)),
            SemanticType.SCandidates(SemanticType.SRecord(rFields))) =>
        SemanticType.SCandidates(SemanticType.SRecord(lFields ++ rFields)).validNel

      case (SemanticType.SCandidates(lElem), rRec: SemanticType.SRecord) =>
        mergeTypes(lElem, rRec, pos).map(SemanticType.SCandidates(_))

      case (lRec: SemanticType.SRecord, SemanticType.SCandidates(rElem)) =>
        mergeTypes(lRec, rElem, pos).map(SemanticType.SCandidates(_))

      case _ =>
        CompileError.IncompatibleMerge(left.prettyPrint, right.prettyPrint, Some(pos)).invalidNel
    }

  private def checkExpression(
    expr: Expression,
    pos: Position,
    env: TypeEnvironment
  ): TypeResult[TypedExpression] = expr match {

    case Expression.VarRef(name) =>
      env.lookupVariable(name)
        .toValidNel(CompileError.UndefinedVariable(name, Some(pos)))
        .map(TypedExpression.VarRef(name, _))

    case Expression.FunctionCall(name, args) =>
      env.functions.lookup(name) match {
        case Some(sig) =>
          if (args.size != sig.params.size) {
            CompileError.TypeError(
              s"Function $name expects ${sig.params.size} arguments, got ${args.size}",
              Some(pos)
            ).invalidNel
          } else {
            args.zip(sig.params).traverse { case (argExpr, (paramName, paramType)) =>
              checkExpression(argExpr.value, argExpr.pos, env).andThen { typedArg =>
                if (isAssignable(typedArg.semanticType, paramType))
                  typedArg.validNel
                else
                  CompileError.TypeMismatch(
                    paramType.prettyPrint,
                    typedArg.semanticType.prettyPrint,
                    Some(argExpr.pos)
                  ).invalidNel
              }
            }.map { typedArgs =>
              TypedExpression.FunctionCall(name, sig, typedArgs)
            }
          }
        case None =>
          CompileError.UndefinedFunction(name, Some(pos)).invalidNel
      }

    case Expression.Merge(left, right) =>
      (checkExpression(left.value, left.pos, env), checkExpression(right.value, right.pos, env))
        .mapN { (l, r) =>
          mergeTypes(l.semanticType, r.semanticType, pos).map { merged =>
            TypedExpression.Merge(l, r, merged)
          }
        }
        .andThen(identity)

    case Expression.Projection(source, fields) =>
      checkExpression(source.value, source.pos, env).andThen { typedSource =>
        typedSource.semanticType match {
          case SemanticType.SRecord(availableFields) =>
            checkProjection(fields, availableFields, pos).map { projectedFields =>
              TypedExpression.Projection(typedSource, fields, SemanticType.SRecord(projectedFields))
            }

          case SemanticType.SCandidates(SemanticType.SRecord(availableFields)) =>
            checkProjection(fields, availableFields, pos).map { projectedFields =>
              TypedExpression.Projection(
                typedSource,
                fields,
                SemanticType.SCandidates(SemanticType.SRecord(projectedFields))
              )
            }

          case other =>
            CompileError.TypeError(
              s"Projection requires a record type, got ${other.prettyPrint}",
              Some(pos)
            ).invalidNel
        }
      }

    case Expression.Conditional(cond, thenBr, elseBr) =>
      (
        checkExpression(cond.value, cond.pos, env),
        checkExpression(thenBr.value, thenBr.pos, env),
        checkExpression(elseBr.value, elseBr.pos, env)
      ).mapN { (c, t, e) =>
        if (c.semanticType != SemanticType.SBoolean)
          CompileError.TypeMismatch("Boolean", c.semanticType.prettyPrint, Some(cond.pos)).invalidNel
        else if (t.semanticType != e.semanticType)
          CompileError.TypeMismatch(t.semanticType.prettyPrint, e.semanticType.prettyPrint, Some(elseBr.pos)).invalidNel
        else
          TypedExpression.Conditional(c, t, e, t.semanticType).validNel
      }.andThen(identity)

    case Expression.StringLit(v) =>
      TypedExpression.Literal(v, SemanticType.SString).validNel

    case Expression.IntLit(v) =>
      TypedExpression.Literal(v, SemanticType.SInt).validNel

    case Expression.FloatLit(v) =>
      TypedExpression.Literal(v, SemanticType.SFloat).validNel

    case Expression.BoolLit(v) =>
      TypedExpression.Literal(v, SemanticType.SBoolean).validNel
  }

  private def checkProjection(
    requested: List[String],
    available: Map[String, SemanticType],
    pos: Position
  ): TypeResult[Map[String, SemanticType]] = {
    requested.traverse { field =>
      available.get(field)
        .toValidNel(CompileError.InvalidProjection(field, available.keys.toList, Some(pos)))
        .map(field -> _)
    }.map(_.toMap)
  }

  private def isAssignable(actual: SemanticType, expected: SemanticType): Boolean =
    actual == expected // Strict equality for now; can extend with subtyping
}
