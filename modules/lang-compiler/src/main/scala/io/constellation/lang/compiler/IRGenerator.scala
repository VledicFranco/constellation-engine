package io.constellation.lang.compiler

import java.util.UUID

import io.constellation.lang.ast.{
  BoolOp,
  CompileError,
  JoinStrategySpec,
  ModuleCallOptions,
  PriorityLevel,
  Span,
  WindowSpec
}
import io.constellation.lang.semantic.*

/** Generates IR from a typed AST */
object IRGenerator {

  /** Context for IR generation */
  private case class GenContext(
      nodes: Map[UUID, IRNode],
      bindings: Map[String, UUID] // Variable name -> node ID
  ) {
    def addNode(node: IRNode): GenContext =
      copy(nodes = nodes + (node.id -> node))

    def bind(name: String, nodeId: UUID): GenContext =
      copy(bindings = bindings + (name -> nodeId))

    def lookup(name: String): Option[UUID] =
      bindings.get(name)
  }

  private object GenContext {
    def empty: GenContext = GenContext(Map.empty, Map.empty)
  }

  /** Generate IR from a typed pipeline */
  def generate(program: TypedPipeline): Either[List[CompileError], IRPipeline] =
    generateDeclarations(program.declarations, GenContext.empty).map { case (finalCtx, inputIds) =>
      // Extract declared output variable names
      val declaredOutputs = program.outputs.map(_._1)

      IRPipeline(
        nodes = finalCtx.nodes,
        inputs = inputIds,
        declaredOutputs = declaredOutputs,
        variableBindings = finalCtx.bindings
      )
    }

  private def generateDeclarations(
      decls: List[TypedDeclaration],
      ctx: GenContext
  ): Either[List[CompileError], (GenContext, List[UUID])] =
    decls.foldLeft[Either[List[CompileError], (GenContext, List[UUID])]](
      Right((ctx, List.empty[UUID]))
    ) { case (acc, decl) =>
      acc.flatMap { case (currentCtx, inputIds) =>
        decl match {
          case TypedDeclaration.TypeDef(_, _, _) =>
            // Type definitions don't generate IR nodes
            Right((currentCtx, inputIds))

          case TypedDeclaration.InputDecl(name, semanticType, span) =>
            val id   = UUID.randomUUID()
            val node = IRNode.Input(id, name, semanticType, Some(span))
            Right((currentCtx.addNode(node).bind(name, id), inputIds :+ id))

          case TypedDeclaration.Assignment(name, value, _) =>
            generateExpression(value, currentCtx).map { case (newCtx, valueId) =>
              (newCtx.bind(name, valueId), inputIds)
            }

          case TypedDeclaration.OutputDecl(_, _, _) =>
            // Output declarations don't generate IR nodes, they just mark variables as outputs
            Right((currentCtx, inputIds))

          case TypedDeclaration.UseDecl(_, _, _) =>
            // Use declarations are processed during type checking, no IR generation needed
            Right((currentCtx, inputIds))
        }
      }
    }

  private def generateExpression(
      expr: TypedExpression,
      ctx: GenContext
  ): Either[List[CompileError], (GenContext, UUID)] = expr match {

    case TypedExpression.VarRef(name, _, span) =>
      // Variable reference just looks up the existing node ID
      ctx.lookup(name) match {
        case Some(id) => Right((ctx, id))
        case None =>
          Left(List(CompileError.UndefinedVariable(name, Some(span))))
      }

    case TypedExpression.FunctionCall(name, signature, args, options, typedFallback, span) =>
      // Check if this is a higher-order function call (has lambda argument)
      val lambdaArgIndex = args.indexWhere(_.isInstanceOf[TypedExpression.Lambda])
      if lambdaArgIndex >= 0 && isHigherOrderFunction(signature.moduleName) then {
        // Generate higher-order node
        generateHigherOrderCall(name, signature, args, lambdaArgIndex, span, ctx)
      } else {
        // Regular function call - generate IR for each argument
        val argsResult = args.zipWithIndex
          .foldLeft[Either[List[CompileError], (GenContext, List[(String, UUID)])]](
            Right((ctx, List.empty[(String, UUID)]))
          ) { case (acc, (arg, idx)) =>
            acc.flatMap { case (currentCtx, ids) =>
              generateExpression(arg, currentCtx).map { case (newCtx, argId) =>
                val paramName = signature.params(idx)._1
                (newCtx, ids :+ (paramName -> argId))
              }
            }
          }

        argsResult.flatMap { case (argsCtx, argIds) =>
          // Convert AST options to IR options (using typed fallback)
          convertOptions(options, typedFallback, argsCtx).map { case (finalCtx, irOptions) =>
            val id = UUID.randomUUID()
            val node = IRNode.ModuleCall(
              id = id,
              moduleName = signature.moduleName,
              languageName = name,
              inputs = argIds.toMap,
              outputType = signature.returns,
              options = irOptions,
              debugSpan = Some(span)
            )
            (finalCtx.addNode(node), id)
          }
        }
      }

    case TypedExpression.Merge(left, right, semanticType, span) =>
      generateExpression(left, ctx).flatMap { case (leftCtx, leftId) =>
        generateExpression(right, leftCtx).map { case (rightCtx, rightId) =>
          val id   = UUID.randomUUID()
          val node = IRNode.MergeNode(id, leftId, rightId, semanticType, Some(span))
          (rightCtx.addNode(node), id)
        }
      }

    case TypedExpression.Projection(source, fields, semanticType, span) =>
      generateExpression(source, ctx).map { case (sourceCtx, sourceId) =>
        val id   = UUID.randomUUID()
        val node = IRNode.ProjectNode(id, sourceId, fields, semanticType, Some(span))
        (sourceCtx.addNode(node), id)
      }

    case TypedExpression.FieldAccess(source, field, semanticType, span) =>
      generateExpression(source, ctx).map { case (sourceCtx, sourceId) =>
        val id   = UUID.randomUUID()
        val node = IRNode.FieldAccessNode(id, sourceId, field, semanticType, Some(span))
        (sourceCtx.addNode(node), id)
      }

    case TypedExpression.Conditional(cond, thenBr, elseBr, semanticType, span) =>
      generateExpression(cond, ctx).flatMap { case (condCtx, condId) =>
        generateExpression(thenBr, condCtx).flatMap { case (thenCtx, thenId) =>
          generateExpression(elseBr, thenCtx).map { case (elseCtx, elseId) =>
            val id   = UUID.randomUUID()
            val node = IRNode.ConditionalNode(id, condId, thenId, elseId, semanticType, Some(span))
            (elseCtx.addNode(node), id)
          }
        }
      }

    case TypedExpression.Literal(value, semanticType, span) =>
      val id   = UUID.randomUUID()
      val node = IRNode.LiteralNode(id, value, semanticType, Some(span))
      Right((ctx.addNode(node), id))

    case TypedExpression.BoolBinary(left, op, right, span) =>
      generateExpression(left, ctx).flatMap { case (leftCtx, leftId) =>
        generateExpression(right, leftCtx).map { case (rightCtx, rightId) =>
          val id = UUID.randomUUID()
          val node = op match {
            case BoolOp.And => IRNode.AndNode(id, leftId, rightId, Some(span))
            case BoolOp.Or  => IRNode.OrNode(id, leftId, rightId, Some(span))
          }
          (rightCtx.addNode(node), id)
        }
      }

    case TypedExpression.Not(operand, span) =>
      generateExpression(operand, ctx).map { case (operandCtx, operandId) =>
        val id   = UUID.randomUUID()
        val node = IRNode.NotNode(id, operandId, Some(span))
        (operandCtx.addNode(node), id)
      }

    case TypedExpression.Guard(expr, condition, span) =>
      generateExpression(expr, ctx).flatMap { case (exprCtx, exprId) =>
        generateExpression(condition, exprCtx).map { case (condCtx, condId) =>
          val id   = UUID.randomUUID()
          val node = IRNode.GuardNode(id, exprId, condId, expr.semanticType, Some(span))
          (condCtx.addNode(node), id)
        }
      }

    case TypedExpression.Coalesce(left, right, span, resultType) =>
      generateExpression(left, ctx).flatMap { case (leftCtx, leftId) =>
        generateExpression(right, leftCtx).map { case (rightCtx, rightId) =>
          val id   = UUID.randomUUID()
          val node = IRNode.CoalesceNode(id, leftId, rightId, resultType, Some(span))
          (rightCtx.addNode(node), id)
        }
      }

    case TypedExpression.Branch(cases, otherwise, semanticType, span) =>
      // Generate IR for all conditions and expressions
      val casesResult =
        cases.foldLeft[Either[List[CompileError], (GenContext, List[(UUID, UUID)])]](
          Right((ctx, List.empty[(UUID, UUID)]))
        ) { case (acc, (cond, expr)) =>
          acc.flatMap { case (currentCtx, ids) =>
            generateExpression(cond, currentCtx).flatMap { case (condCtx, condId) =>
              generateExpression(expr, condCtx).map { case (exprCtx, exprId) =>
                (exprCtx, ids :+ (condId, exprId))
              }
            }
          }
        }

      casesResult.flatMap { case (casesCtx, caseIds) =>
        generateExpression(otherwise, casesCtx).map { case (otherwiseCtx, otherwiseId) =>
          val id   = UUID.randomUUID()
          val node = IRNode.BranchNode(id, caseIds, otherwiseId, semanticType, Some(span))
          (otherwiseCtx.addNode(node), id)
        }
      }

    case TypedExpression.StringInterpolation(parts, expressions, span) =>
      // Generate IR for all interpolated expressions
      val exprsResult = expressions.foldLeft[Either[List[CompileError], (GenContext, List[UUID])]](
        Right((ctx, List.empty[UUID]))
      ) { case (acc, expr) =>
        acc.flatMap { case (currentCtx, ids) =>
          generateExpression(expr, currentCtx).map { case (newCtx, exprId) =>
            (newCtx, ids :+ exprId)
          }
        }
      }

      exprsResult.map { case (exprsCtx, exprIds) =>
        val id   = UUID.randomUUID()
        val node = IRNode.StringInterpolationNode(id, parts, exprIds, Some(span))
        (exprsCtx.addNode(node), id)
      }

    case TypedExpression.ListLiteral(elements, elementType, span) =>
      // Generate IR for all element expressions
      val elemsResult = elements.foldLeft[Either[List[CompileError], (GenContext, List[UUID])]](
        Right((ctx, List.empty[UUID]))
      ) { case (acc, elem) =>
        acc.flatMap { case (currentCtx, ids) =>
          generateExpression(elem, currentCtx).map { case (newCtx, elemId) =>
            (newCtx, ids :+ elemId)
          }
        }
      }

      elemsResult.map { case (elemCtx, elemIds) =>
        val id   = UUID.randomUUID()
        val node = IRNode.ListLiteralNode(id, elemIds, elementType, Some(span))
        (elemCtx.addNode(node), id)
      }

    case TypedExpression.RecordLiteral(fields, semanticType, span) =>
      // Generate IR for all field expressions
      val fieldsResult =
        fields.foldLeft[Either[List[CompileError], (GenContext, List[(String, UUID)])]](
          Right((ctx, List.empty[(String, UUID)]))
        ) { case (acc, (fieldName, fieldExpr)) =>
          acc.flatMap { case (currentCtx, ids) =>
            generateExpression(fieldExpr, currentCtx).map { case (newCtx, fieldId) =>
              (newCtx, ids :+ (fieldName -> fieldId))
            }
          }
        }

      fieldsResult.map { case (fieldsCtx, fieldNodeIds) =>
        val id   = UUID.randomUUID()
        val node = IRNode.RecordLitNode(id, fieldNodeIds, semanticType, Some(span))
        (fieldsCtx.addNode(node), id)
      }

    case TypedExpression.Lambda(_, _, _, span) =>
      // Lambdas shouldn't appear standalone - they should only appear as arguments
      // to higher-order functions, which are handled in FunctionCall case.
      // If we reach here, it's an error (lambda used in invalid context).
      Left(List(CompileError.InvalidLambdaContext(Some(span))))

    case TypedExpression.Match(scrutinee, cases, semanticType, span) =>
      generateExpression(scrutinee, ctx).flatMap { case (scrutineeCtx, scrutineeId) =>
        // Generate IR for all case bodies
        val casesResult =
          cases.foldLeft[Either[List[CompileError], (GenContext, List[MatchCaseIR])]](
            Right((scrutineeCtx, List.empty[MatchCaseIR]))
          ) { case (acc, typedCase) =>
            acc.flatMap { case (currentCtx, irCaseList) =>
              // Create field access nodes for pattern bindings
              val bindingCtx = typedCase.bindings.foldLeft(currentCtx) {
                case (ctx, (fieldName, fieldType)) =>
                  val fieldId = UUID.randomUUID()
                  val fieldNode =
                    IRNode.FieldAccessNode(fieldId, scrutineeId, fieldName, fieldType, None)
                  ctx.addNode(fieldNode).bind(fieldName, fieldId)
              }

              // Generate body with bindings in scope
              generateExpression(typedCase.body, bindingCtx).map { case (bodyCtx, bodyId) =>
                val patternIR = toPatternIR(typedCase.pattern)
                val irCase    = MatchCaseIR(patternIR, typedCase.bindings, bodyId)
                // Return original context (don't pollute outer scope with bindings)
                // but keep all generated nodes
                val outCtx = currentCtx.copy(nodes = bodyCtx.nodes)
                (outCtx, irCaseList :+ irCase)
              }
            }
          }

        casesResult.map { case (casesCtx, irCases) =>
          val id   = UUID.randomUUID()
          val node = IRNode.MatchNode(id, scrutineeId, irCases, semanticType, Some(span))
          (casesCtx.addNode(node), id)
        }
      }
  }

  /** Convert a typed pattern to IR pattern representation */
  private def toPatternIR(pattern: TypedPattern): PatternIR = pattern match {
    case TypedPattern.Record(fields, matchedType, _) =>
      PatternIR.Record(fields, matchedType)
    case TypedPattern.TypeTest(typeName, matchedType, _) =>
      PatternIR.TypeTest(typeName, matchedType)
    case TypedPattern.Wildcard(_) =>
      PatternIR.Wildcard()
  }

  /** Check if a module name corresponds to a higher-order function */
  private def isHigherOrderFunction(moduleName: String): Boolean =
    moduleName.startsWith("stdlib.hof.")

  /** Determine the higher-order operation from module name */
  private def getHigherOrderOp(
      moduleName: String,
      span: Span
  ): Either[List[CompileError], HigherOrderOp] =
    moduleName match {
      case n if n.contains("filter") => Right(HigherOrderOp.Filter)
      case n if n.contains("map")    => Right(HigherOrderOp.Map)
      case n if n.contains("all")    => Right(HigherOrderOp.All)
      case n if n.contains("any")    => Right(HigherOrderOp.Any)
      case n if n.contains("sortBy") => Right(HigherOrderOp.SortBy)
      case _ =>
        Left(List(CompileError.UnknownHigherOrderFunction(moduleName, Some(span))))
    }

  /** Generate IR for a higher-order function call with a lambda argument */
  private def generateHigherOrderCall(
      name: String,
      signature: FunctionSignature,
      args: List[TypedExpression],
      lambdaArgIndex: Int,
      span: io.constellation.lang.ast.Span,
      ctx: GenContext
  ): Either[List[CompileError], (GenContext, UUID)] = {
    // Extract the source collection (first non-lambda argument)
    val sourceArg = args.head // Assuming source is always first

    val lambdaArg = args(lambdaArgIndex).asInstanceOf[TypedExpression.Lambda]

    generateExpression(sourceArg, ctx).flatMap { case (sourceCtx, sourceId) =>
      generateLambdaIR(lambdaArg, sourceCtx).flatMap { case (lambdaIR, capturedInputs) =>
        getHigherOrderOp(signature.moduleName, span).map { op =>
          val id = UUID.randomUUID()
          val node = IRNode.HigherOrderNode(
            id = id,
            operation = op,
            source = sourceId,
            lambda = lambdaIR,
            outputType = signature.returns,
            capturedInputs = capturedInputs,
            debugSpan = Some(span)
          )
          (sourceCtx.addNode(node), id)
        }
      }
    }
  }

  /** Generate IR representation of a lambda (body nodes + output).
    *
    * Supports closures: when the lambda body references variables from the enclosing scope, those
    * variables are captured as additional `IRNode.Input` nodes in the lambda's body, making the
    * sub-DAG self-contained. The returned `capturedInputs` maps captured variable names to their
    * node UUIDs in the outer context, so the DagCompiler can wire the data dependencies.
    *
    * @return
    *   (TypedLambda with capturedBindings, Map of varName -> outer node UUID)
    */
  private def generateLambdaIR(
      lambda: TypedExpression.Lambda,
      outerCtx: GenContext
  ): Either[List[CompileError], (TypedLambda, Map[String, UUID])] = {
    val paramNameSet = lambda.params.map(_._1).toSet

    // Find free variables in the lambda body (referenced but not bound as lambda params)
    val freeVarNames = collectFreeVars(lambda.body, paramNameSet)

    // Create input nodes for lambda parameters
    val paramInputs = lambda.params.map { case (name, paramType) =>
      val paramId   = UUID.randomUUID()
      val inputNode = IRNode.Input(paramId, name, paramType, None)
      (name, paramId, inputNode)
    }

    // Create captured variable input nodes for free variables found in outer context
    var capturedBindings = Map.empty[String, UUID] // varName -> inner Input node UUID
    var capturedInputs   = Map.empty[String, UUID] // varName -> outer context node UUID

    val capturedInputNodes = freeVarNames.toList.flatMap { varName =>
      outerCtx.lookup(varName).flatMap { outerNodeId =>
        outerCtx.nodes.get(outerNodeId).map { outerNode =>
          val captureId   = UUID.randomUUID()
          val captureNode = IRNode.Input(captureId, varName, outerNode.outputType, None)
          capturedBindings = capturedBindings + (varName -> captureId)
          capturedInputs = capturedInputs + (varName     -> outerNodeId)
          (varName, captureId, captureNode)
        }
      }
    }

    // Create context with parameter bindings + captured variable bindings
    val lambdaCtx =
      (paramInputs ++ capturedInputNodes).foldLeft(GenContext.empty) {
        case (ctx, (name, nodeId, node)) =>
          ctx.addNode(node).bind(name, nodeId)
      }

    // Generate IR for the lambda body
    generateExpression(lambda.body, lambdaCtx).map { case (bodyCtx, bodyOutputId) =>
      val typedLambda = TypedLambda(
        paramNames = lambda.params.map(_._1),
        paramTypes = lambda.params.map(_._2),
        bodyNodes = bodyCtx.nodes,
        bodyOutputId = bodyOutputId,
        returnType = lambda.semanticType.returnType,
        capturedBindings = capturedBindings
      )

      (typedLambda, capturedInputs)
    }
  }

  /** Collect free variable names in a typed expression (names not in the bound set). Used to
    * determine which outer-scope variables a lambda body captures.
    */
  private def collectFreeVars(expr: TypedExpression, bound: Set[String]): Set[String] = expr match {
    case TypedExpression.VarRef(name, _, _) =>
      if bound.contains(name) then Set.empty else Set(name)

    case TypedExpression.Literal(_, _, _) => Set.empty

    case TypedExpression.FunctionCall(_, _, args, _, typedFallback, _) =>
      args.flatMap(collectFreeVars(_, bound)).toSet ++
        typedFallback.map(collectFreeVars(_, bound)).getOrElse(Set.empty)

    case TypedExpression.Lambda(params, body, _, _) =>
      collectFreeVars(body, bound ++ params.map(_._1))

    case TypedExpression.Merge(l, r, _, _) =>
      collectFreeVars(l, bound) ++ collectFreeVars(r, bound)

    case TypedExpression.Projection(source, _, _, _) =>
      collectFreeVars(source, bound)

    case TypedExpression.FieldAccess(source, _, _, _) =>
      collectFreeVars(source, bound)

    case TypedExpression.Conditional(cond, thenBr, elseBr, _, _) =>
      collectFreeVars(cond, bound) ++ collectFreeVars(thenBr, bound) ++
        collectFreeVars(elseBr, bound)

    case TypedExpression.BoolBinary(l, _, r, _) =>
      collectFreeVars(l, bound) ++ collectFreeVars(r, bound)

    case TypedExpression.Not(operand, _) =>
      collectFreeVars(operand, bound)

    case TypedExpression.Guard(expr, cond, _) =>
      collectFreeVars(expr, bound) ++ collectFreeVars(cond, bound)

    case TypedExpression.Coalesce(l, r, _, _) =>
      collectFreeVars(l, bound) ++ collectFreeVars(r, bound)

    case TypedExpression.Branch(cases, otherwise, _, _) =>
      cases.flatMap { case (cond, expr) =>
        collectFreeVars(cond, bound) ++ collectFreeVars(expr, bound)
      }.toSet ++ collectFreeVars(otherwise, bound)

    case TypedExpression.StringInterpolation(_, exprs, _) =>
      exprs.flatMap(collectFreeVars(_, bound)).toSet

    case TypedExpression.ListLiteral(elems, _, _) =>
      elems.flatMap(collectFreeVars(_, bound)).toSet

    case TypedExpression.RecordLiteral(fields, _, _) =>
      fields.flatMap { case (_, expr) => collectFreeVars(expr, bound) }.toSet

    case TypedExpression.Match(scrutinee, cases, _, _) =>
      collectFreeVars(scrutinee, bound) ++ cases.flatMap { c =>
        collectFreeVars(c.body, bound ++ c.bindings.keys)
      }.toSet
  }

  /** Convert AST ModuleCallOptions to IR options, generating IR nodes for fallback if present.
    *
    * @param options
    *   The AST module call options
    * @param typedFallback
    *   The typed fallback expression (already type-checked)
    * @param ctx
    *   Current generation context
    * @return
    *   Updated context and IR options
    */
  private def convertOptions(
      options: ModuleCallOptions,
      typedFallback: Option[TypedExpression],
      ctx: GenContext
  ): Either[List[CompileError], (GenContext, IRModuleCallOptions)] =
    if options.isEmpty && typedFallback.isEmpty then {
      Right((ctx, IRModuleCallOptions.empty))
    } else {
      // Generate IR for typed fallback expression if present
      val fallbackResult: Either[List[CompileError], (GenContext, Option[UUID])] =
        typedFallback match {
          case Some(fallbackExpr) =>
            generateExpression(fallbackExpr, ctx).map { case (newCtx, id) =>
              (newCtx, Some(id))
            }
          case None =>
            Right((ctx, None))
        }

      fallbackResult.map { case (finalCtx, fallbackId) =>
        // Convert priority to normalized Int value
        val priorityValue: Option[Int] = options.priority.map {
          case Left(level) =>
            level match {
              case PriorityLevel.Critical   => 100
              case PriorityLevel.High       => 80
              case PriorityLevel.Normal     => 50
              case PriorityLevel.Low        => 20
              case PriorityLevel.Background => 0
            }
          case Right(custom) => custom.value
        }

        // Convert WindowSpec to serialized string
        val windowValue: Option[String] = options.window.map {
          case WindowSpec.Tumbling(size)       => s"tumbling:${size.toMillis}"
          case WindowSpec.Sliding(size, slide) => s"sliding:${size.toMillis}:${slide.toMillis}"
          case WindowSpec.Count(n)             => s"count:$n"
        }

        // Convert JoinStrategySpec to serialized string
        val joinValue: Option[String] = options.join.map {
          case JoinStrategySpec.CombineLatest   => "combine-latest"
          case JoinStrategySpec.Zip             => "zip"
          case JoinStrategySpec.Buffer(timeout) => s"buffer:${timeout.toMillis}"
        }

        val irOptions = IRModuleCallOptions(
          retry = options.retry,
          timeoutMs = options.timeout.map(_.toMillis),
          delayMs = options.delay.map(_.toMillis),
          backoff = options.backoff,
          fallback = fallbackId,
          cacheMs = options.cache.map(_.toMillis),
          cacheBackend = options.cacheBackend,
          throttleCount = options.throttle.map(_.count),
          throttlePerMs = options.throttle.map(_.per.toMillis),
          concurrency = options.concurrency,
          onError = options.onError,
          lazyEval = options.lazyEval,
          priority = priorityValue,
          batchSize = options.batch,
          batchTimeoutMs = options.batchTimeout.map(_.toMillis),
          window = windowValue,
          checkpointMs = options.checkpoint.map(_.toMillis),
          joinStrategy = joinValue
        )

        (finalCtx, irOptions)
      }
    }
}
