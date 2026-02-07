package io.constellation.lang.compiler

import java.util.UUID

import cats.effect.IO
import cats.syntax.all.*

import io.constellation.*
import io.constellation.lang.ast.CompileWarning
import io.constellation.lang.compiler.CompilerError as DagCompilerError
import io.constellation.lang.semantic.SemanticType

/** Internal compilation output from DagCompiler. */
private[compiler] final case class DagCompileOutput(
    dagSpec: DagSpec,
    syntheticModules: Map[UUID, Module.Uninitialized],
    moduleOptions: Map[UUID, IRModuleCallOptions] = Map.empty,
    warnings: List[CompileWarning] = Nil
)

/** Compiles IR to Constellation DagSpec.
  *
  * ==Type Safety Note==
  *
  * The `asInstanceOf` casts in this file are **safe by construction**:
  *
  *   - '''Lambda casts''': HOF lambda arguments are always TypedExpression.Lambda because the IR
  *     generator only produces lambda arguments for higher-order functions
  *
  *   - '''Boolean casts''' (in evaluateLambdaBody): Logical operations (And, Or, Not) and
  *     conditionals receive Boolean operands because the type checker verifies all operand types
  *     during semantic analysis before IR generation
  *
  *   - '''Numeric casts''' (in evaluateLambdaBody): Arithmetic operations receive numeric operands
  *     because the type checker verifies operand types
  *
  *   - '''Condition result cast''' (in ConditionalModule): WhenNode conditions are type-checked to
  *     be Boolean during semantic analysis
  *
  * Runtime type validation can be enabled by setting `CONSTELLATION_DEBUG=true` for development and
  * debugging purposes. See [[io.constellation.DebugMode]].
  *
  * @see
  *   [[io.constellation.DebugMode]] for optional runtime type validation
  */
object DagCompiler {

  /** Compile an IR pipeline to a DagSpec and synthetic modules */
  def compile(
      program: IRPipeline,
      dagName: String,
      registeredModules: Map[String, Module.Uninitialized]
  ): Either[DagCompilerError, DagCompileOutput] = {
    val compiler = new DagCompilerState(dagName, registeredModules)
    compiler.compile(program)
  }

  /** Internal compiler state */
  private class DagCompilerState(
      dagName: String,
      registeredModules: Map[String, Module.Uninitialized]
  ) {
    // Maps IR node ID -> data node UUID (output of that IR node)
    private var dataNodes: Map[UUID, DataNodeSpec]                = Map.empty
    private var moduleNodes: Map[UUID, ModuleNodeSpec]            = Map.empty
    private var syntheticModules: Map[UUID, Module.Uninitialized] = Map.empty
    private var inEdges: Set[(UUID, UUID)]                        = Set.empty // data -> module
    private var outEdges: Set[(UUID, UUID)]                       = Set.empty // module -> data

    // Maps module UUID -> module call options (for runtime execution)
    private var moduleOptions: Map[UUID, IRModuleCallOptions] = Map.empty

    // Maps IR node ID -> its output data node UUID
    private var nodeOutputs: Map[UUID, UUID] = Map.empty

    /** Helper to look up a node output with proper error handling */
    private def getNodeOutput(nodeId: UUID, context: String): Either[DagCompilerError, UUID] =
      nodeOutputs.get(nodeId).toRight(DagCompilerError.NodeNotFound(nodeId, context))

    def compile(program: IRPipeline): Either[DagCompilerError, DagCompileOutput] = {
      // Process nodes in topological order
      val processResult =
        program.topologicalOrder.foldLeft[Either[DagCompilerError, Unit]](Right(())) {
          case (Right(_), nodeId) =>
            program.nodes.get(nodeId) match {
              case Some(node) => processNode(node)
              case None       => Right(()) // Skip missing nodes
            }
          case (left @ Left(_), _) => left
        }

      processResult.map { _ =>
        // Build output bindings: map output variable names to data node UUIDs
        val outputBindings: Map[String, UUID] = program.declaredOutputs.flatMap { outputName =>
          // Look up the IR node ID for this variable
          program.variableBindings.get(outputName).flatMap { irNodeId =>
            // Look up the data node UUID for this IR node
            nodeOutputs.get(irNodeId).map { dataNodeId =>
              outputName -> dataNodeId
            }
          }
        }.toMap

        val dagSpec = DagSpec(
          metadata = ComponentMetadata.empty(dagName),
          modules = moduleNodes,
          data = dataNodes,
          inEdges = inEdges,
          outEdges = outEdges,
          declaredOutputs = program.declaredOutputs,
          outputBindings = outputBindings
        )

        DagCompileOutput(dagSpec, syntheticModules, moduleOptions)
      }
    }

    private def processNode(node: IRNode): Either[DagCompilerError, Unit] = node match {
      case IRNode.Input(id, name, outputType, _) =>
        // Input nodes become top-level data nodes
        val dataId = UUID.randomUUID()
        val cType  = SemanticType.toCType(outputType)
        dataNodes = dataNodes + (dataId -> DataNodeSpec(name, Map(dataId -> name), cType))
        nodeOutputs = nodeOutputs + (id -> dataId)
        Right(())

      case IRNode.ModuleCall(id, moduleName, languageName, inputs, outputType, options, _) =>
        processModuleCall(id, moduleName, languageName, inputs, outputType, options)

      case IRNode.MergeNode(id, left, right, outputType, _) =>
        processMergeNode(id, left, right, outputType)

      case IRNode.ProjectNode(id, source, fields, outputType, _) =>
        processProjectNode(id, source, fields, outputType)

      case IRNode.FieldAccessNode(id, source, field, outputType, _) =>
        processFieldAccessNode(id, source, field, outputType)

      case IRNode.ConditionalNode(id, condition, thenBranch, elseBranch, outputType, _) =>
        processConditionalNode(id, condition, thenBranch, elseBranch, outputType)

      case IRNode.LiteralNode(id, value, outputType, _) =>
        processLiteralNode(id, value, outputType)
        Right(())

      case IRNode.AndNode(id, left, right, _) =>
        processAndNode(id, left, right)

      case IRNode.OrNode(id, left, right, _) =>
        processOrNode(id, left, right)

      case IRNode.NotNode(id, operand, _) =>
        processNotNode(id, operand)

      case IRNode.GuardNode(id, expr, condition, innerType, _) =>
        processGuardNode(id, expr, condition, innerType)

      case IRNode.CoalesceNode(id, left, right, resultType, _) =>
        processCoalesceNode(id, left, right, resultType)

      case IRNode.BranchNode(id, cases, otherwise, resultType, _) =>
        processBranchNode(id, cases, otherwise, resultType)

      case IRNode.StringInterpolationNode(id, parts, expressions, _) =>
        processStringInterpolationNode(id, parts, expressions)

      case IRNode.HigherOrderNode(id, operation, source, lambda, outputType, _) =>
        processHigherOrderNode(id, operation, source, lambda, outputType)

      case IRNode.ListLiteralNode(id, elements, elementType, _) =>
        processListLiteralNode(id, elements, elementType)

      case IRNode.RecordLitNode(id, fields, outputType, _) =>
        processRecordLitNode(id, fields, outputType)

      case IRNode.MatchNode(id, scrutinee, cases, resultType, _) =>
        processMatchNode(id, scrutinee, cases, resultType)
    }

    private def processModuleCall(
        id: UUID,
        moduleName: String,
        languageName: String,
        inputs: Map[String, UUID],
        outputType: SemanticType,
        options: IRModuleCallOptions
    ): Either[DagCompilerError, Unit] = {
      val moduleId     = UUID.randomUUID()
      val outputDataId = UUID.randomUUID()

      // Store module call options if not empty
      if !options.isEmpty then {
        moduleOptions = moduleOptions + (moduleId -> options)
      }

      // Look up the registered module and get output field name
      val outputFieldName = registeredModules.get(moduleName) match {
        case Some(uninitModule) =>
          // Use the module's spec
          val spec = uninitModule.spec.copy(
            metadata = uninitModule.spec.metadata.copy(name = s"$dagName.$languageName")
          )
          moduleNodes = moduleNodes + (moduleId           -> spec)
          syntheticModules = syntheticModules + (moduleId -> uninitModule)
          // Get the output field name from the module's produces map
          uninitModule.spec.produces.keys.headOption.getOrElse("out")

        case None =>
          // Create a placeholder module spec (for testing or when module is provided at runtime)
          val cType = SemanticType.toCType(outputType)
          val spec = ModuleNodeSpec(
            metadata = ComponentMetadata.empty(s"$dagName.$languageName"),
            consumes = inputs.map { case (name, _) => name -> CType.CString }, // Placeholder
            produces = Map("out" -> cType)
          )
          moduleNodes = moduleNodes + (moduleId -> spec)
          "out"
      }

      // Connect input data nodes to the module and update nicknames
      val inputResults = inputs.toList.traverse { case (paramName, inputNodeId) =>
        getNodeOutput(inputNodeId, s"input to module $languageName").map { inputDataId =>
          inEdges = inEdges + ((inputDataId, moduleId))
          // Update the data node's nicknames to include this module's parameter name
          dataNodes = dataNodes.updatedWith(inputDataId) {
            case Some(spec) => Some(spec.copy(nicknames = spec.nicknames + (moduleId -> paramName)))
            case None       => None
          }
        }
      }

      inputResults.map { _ =>
        // Create output data node using the module's actual output field name
        val cType = SemanticType.toCType(outputType)
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = languageName + "_output",
          nicknames = Map(moduleId -> outputFieldName),
          cType = cType
        ))
        outEdges = outEdges + ((moduleId, outputDataId))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processMergeNode(
        id: UUID,
        left: UUID,
        right: UUID,
        outputType: SemanticType
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      for {
        leftDataId  <- getNodeOutput(left, "left operand of merge")
        rightDataId <- getNodeOutput(right, "right operand of merge")
      } yield {
        val leftCType   = dataNodes(leftDataId).cType
        val rightCType  = dataNodes(rightDataId).cType
        val outputCType = SemanticType.toCType(outputType)

        // Create data node with inline transform (no synthetic module needed)
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"merge_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = outputCType,
          inlineTransform = Some(InlineTransform.MergeTransform(leftCType, rightCType)),
          transformInputs = Map("left" -> leftDataId, "right" -> rightDataId)
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processProjectNode(
        id: UUID,
        source: UUID,
        fields: List[String],
        outputType: SemanticType
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      getNodeOutput(source, "source of projection").map { sourceDataId =>
        val sourceCType = dataNodes(sourceDataId).cType
        val outputCType = SemanticType.toCType(outputType)

        // Create data node with inline transform (no synthetic module needed)
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"project_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = outputCType,
          inlineTransform = Some(InlineTransform.ProjectTransform(fields, sourceCType)),
          transformInputs = Map("source" -> sourceDataId)
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processFieldAccessNode(
        id: UUID,
        source: UUID,
        field: String,
        outputType: SemanticType
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      getNodeOutput(source, s"source of field access '.$field'").map { sourceDataId =>
        val sourceCType = dataNodes(sourceDataId).cType
        val outputCType = SemanticType.toCType(outputType)

        // Create data node with inline transform (no synthetic module needed)
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"field_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = outputCType,
          inlineTransform = Some(InlineTransform.FieldAccessTransform(field, sourceCType)),
          transformInputs = Map("source" -> sourceDataId)
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processConditionalNode(
        id: UUID,
        condition: UUID,
        thenBranch: UUID,
        elseBranch: UUID,
        outputType: SemanticType
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      for {
        condDataId <- getNodeOutput(condition, "condition of if-then-else")
        thenDataId <- getNodeOutput(thenBranch, "then branch of if-then-else")
        elseDataId <- getNodeOutput(elseBranch, "else branch of if-then-else")
      } yield {
        val outputCType = SemanticType.toCType(outputType)

        // Create data node with inline transform (no synthetic module needed)
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"conditional_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = outputCType,
          inlineTransform = Some(InlineTransform.ConditionalTransform),
          transformInputs =
            Map("cond" -> condDataId, "thenBr" -> thenDataId, "elseBr" -> elseDataId)
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processLiteralNode(
        id: UUID,
        value: Any,
        outputType: SemanticType
    ): Unit = {
      // Literals become data nodes with inline literal transform
      val dataId = UUID.randomUUID()
      val cType  = SemanticType.toCType(outputType)
      dataNodes = dataNodes + (dataId -> DataNodeSpec(
        name = s"literal_${id.toString.take(8)}",
        nicknames = Map(dataId -> s"literal_${id.toString.take(8)}"),
        cType = cType,
        inlineTransform = Some(InlineTransform.LiteralTransform(value)),
        transformInputs = Map.empty // No inputs needed for literals
      ))
      nodeOutputs = nodeOutputs + (id -> dataId)
    }

    private def processAndNode(
        id: UUID,
        left: UUID,
        right: UUID
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      for {
        leftDataId  <- getNodeOutput(left, "left operand of 'and'")
        rightDataId <- getNodeOutput(right, "right operand of 'and'")
      } yield {
        // Create data node with inline transform (no synthetic module needed)
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"and_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = CType.CBoolean,
          inlineTransform = Some(InlineTransform.AndTransform),
          transformInputs = Map("left" -> leftDataId, "right" -> rightDataId)
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processOrNode(
        id: UUID,
        left: UUID,
        right: UUID
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      for {
        leftDataId  <- getNodeOutput(left, "left operand of 'or'")
        rightDataId <- getNodeOutput(right, "right operand of 'or'")
      } yield {
        // Create data node with inline transform (no synthetic module needed)
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"or_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = CType.CBoolean,
          inlineTransform = Some(InlineTransform.OrTransform),
          transformInputs = Map("left" -> leftDataId, "right" -> rightDataId)
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processNotNode(
        id: UUID,
        operand: UUID
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      getNodeOutput(operand, "operand of 'not'").map { operandDataId =>
        // Create data node with inline transform (no synthetic module needed)
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"not_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = CType.CBoolean,
          inlineTransform = Some(InlineTransform.NotTransform),
          transformInputs = Map("operand" -> operandDataId)
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processGuardNode(
        id: UUID,
        expr: UUID,
        condition: UUID,
        innerType: SemanticType
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      for {
        exprDataId <- getNodeOutput(expr, "expression of guard (when)")
        condDataId <- getNodeOutput(condition, "condition of guard (when)")
      } yield {
        val exprCType   = dataNodes(exprDataId).cType
        val outputCType = CType.COptional(exprCType)

        // Create data node with inline transform (no synthetic module needed)
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"guard_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = outputCType,
          inlineTransform = Some(InlineTransform.GuardTransform),
          transformInputs = Map("expr" -> exprDataId, "cond" -> condDataId)
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processCoalesceNode(
        id: UUID,
        left: UUID,
        right: UUID,
        resultType: SemanticType
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      for {
        leftDataId  <- getNodeOutput(left, "left operand of coalesce (??)")
        rightDataId <- getNodeOutput(right, "right operand of coalesce (??)")
      } yield {
        val outputCType = SemanticType.toCType(resultType)

        // Create data node with inline transform (no synthetic module needed)
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"coalesce_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = outputCType,
          inlineTransform = Some(InlineTransform.CoalesceTransform),
          transformInputs = Map("left" -> leftDataId, "right" -> rightDataId)
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processBranchNode(
        id: UUID,
        cases: List[(UUID, UUID)],
        otherwise: UUID,
        resultType: SemanticType
    ): Either[DagCompilerError, Unit] = {
      val moduleId     = UUID.randomUUID()
      val outputDataId = UUID.randomUUID()

      // Get all data IDs for conditions and expressions
      val caseDataIdsResult = cases.zipWithIndex.traverse { case ((condId, exprId), idx) =>
        for {
          condDataId <- getNodeOutput(condId, s"condition of branch case $idx")
          exprDataId <- getNodeOutput(exprId, s"expression of branch case $idx")
        } yield (condDataId, exprDataId)
      }

      for {
        caseDataIds     <- caseDataIdsResult
        otherwiseDataId <- getNodeOutput(otherwise, "otherwise branch")
      } yield {
        // Build consumes map with indexed names
        val consumesMap = caseDataIds.zipWithIndex.flatMap { case ((condDataId, exprDataId), idx) =>
          List(
            s"cond$idx" -> dataNodes(condDataId).cType,
            s"expr$idx" -> dataNodes(exprDataId).cType
          )
        }.toMap + ("otherwise" -> dataNodes(otherwiseDataId).cType)

        val outputCType = SemanticType.toCType(resultType)

        // Create synthetic branch module
        val spec = ModuleNodeSpec(
          metadata = ComponentMetadata.empty(s"$dagName.branch-${id.toString.take(8)}"),
          consumes = consumesMap,
          produces = Map("out" -> outputCType)
        )
        moduleNodes = moduleNodes + (moduleId -> spec)

        // Create synthetic module implementation
        val syntheticModule = createBranchModule(spec, cases.size, outputCType)
        syntheticModules = syntheticModules + (moduleId -> syntheticModule)

        // Update nicknames for input data nodes
        caseDataIds.zipWithIndex.foreach { case ((condDataId, exprDataId), idx) =>
          dataNodes = dataNodes.updatedWith(condDataId) {
            case Some(spec) =>
              Some(spec.copy(nicknames = spec.nicknames + (moduleId -> s"cond$idx")))
            case None => None
          }
          dataNodes = dataNodes.updatedWith(exprDataId) {
            case Some(spec) =>
              Some(spec.copy(nicknames = spec.nicknames + (moduleId -> s"expr$idx")))
            case None => None
          }
        }
        dataNodes = dataNodes.updatedWith(otherwiseDataId) {
          case Some(spec) => Some(spec.copy(nicknames = spec.nicknames + (moduleId -> "otherwise")))
          case None       => None
        }

        // Connect edges
        caseDataIds.foreach { case (condDataId, exprDataId) =>
          inEdges = inEdges + ((condDataId, moduleId)) + ((exprDataId, moduleId))
        }
        inEdges = inEdges + ((otherwiseDataId, moduleId))

        // Create output data node
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"branch_${id.toString.take(8)}_output",
          nicknames = Map(moduleId -> "out"),
          cType = outputCType
        ))

        // Connect module output to data node
        outEdges = outEdges + ((moduleId, outputDataId))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processStringInterpolationNode(
        id: UUID,
        parts: List[String],
        expressions: List[UUID]
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      // Get data IDs for all interpolated expressions
      val exprDataIdsResult = expressions.zipWithIndex.traverse { case (exprNodeId, idx) =>
        getNodeOutput(exprNodeId, s"expression $idx in string interpolation").map { dataId =>
          (s"expr$idx", dataId)
        }
      }

      exprDataIdsResult.map { exprDataIds =>
        // Create data node with inline transform (no synthetic module needed)
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"interpolate_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = CType.CString,
          inlineTransform = Some(InlineTransform.StringInterpolationTransform(parts)),
          transformInputs = exprDataIds.toMap
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    /** Create a branch module that evaluates conditions in order. Returns the first expression
      * whose condition is true, or the otherwise expression.
      */
    private def createBranchModule(
        spec: ModuleNodeSpec,
        numCases: Int,
        outputCType: CType
    ): Module.Uninitialized =
      Module.Uninitialized(
        spec = spec,
        init = (moduleId, dagSpec) =>
          for {
            consumesNs <- Module.Namespace.consumes(moduleId, dagSpec)
            producesNs <- Module.Namespace.produces(moduleId, dagSpec)
            // Create deferreds for all conditions and expressions
            condDeferreds <- (0 until numCases).toList.traverse(_ => cats.effect.Deferred[IO, Any])
            exprDeferreds <- (0 until numCases).toList.traverse(_ => cats.effect.Deferred[IO, Any])
            otherwiseDeferred <- cats.effect.Deferred[IO, Any]
            outDeferred       <- cats.effect.Deferred[IO, Any]
            // Get name IDs
            condIds     <- (0 until numCases).toList.traverse(i => consumesNs.nameId(s"cond$i"))
            exprIds     <- (0 until numCases).toList.traverse(i => consumesNs.nameId(s"expr$i"))
            otherwiseId <- consumesNs.nameId("otherwise")
            outId       <- producesNs.nameId("out")
          } yield {
            val dataMap = (condIds.zip(condDeferreds) ++ exprIds.zip(exprDeferreds) ++
              List((otherwiseId, otherwiseDeferred), (outId, outDeferred))).toMap
            Module.Runnable(
              id = moduleId,
              data = dataMap,
              run = runtime => {
                // Evaluate conditions in order, return first true branch or otherwise
                def evaluateBranches(idx: Int): IO[Any] =
                  if idx >= numCases then {
                    // All conditions were false, return otherwise
                    runtime.getTableData(otherwiseId)
                  } else {
                    for {
                      condValue <- runtime.getTableData(condIds(idx))
                      result <-
                        if condValue.asInstanceOf[Boolean] then {
                          runtime.getTableData(exprIds(idx))
                        } else {
                          evaluateBranches(idx + 1)
                        }
                    } yield result
                  }
                for {
                  result <- evaluateBranches(0)
                  _      <- runtime.setTableData(outId, result)
                  // Also store in state for output extraction
                  cValue = Runtime.anyToCValue(result, outputCType)
                  _ <- runtime.setStateData(outId, cValue)
                } yield ()
              }
            )
          }
      )

    private def processMatchNode(
        id: UUID,
        scrutinee: UUID,
        cases: List[MatchCaseIR],
        resultType: SemanticType
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      // Get the scrutinee data ID
      getNodeOutput(scrutinee, "scrutinee of match expression").flatMap { scrutineeDataId =>
        val scrutineeCType = dataNodes(scrutineeDataId).cType
        val outputCType    = SemanticType.toCType(resultType)

        // Build body evaluators - functions that extract bindings and compute body value
        val bodyEvaluatorsResult = cases.zipWithIndex.traverse { case (matchCase, idx) =>
          getNodeOutput(matchCase.bodyId, s"body of match case $idx").map { bodyDataId =>
            val bodySpec = dataNodes(bodyDataId)
            // Create evaluator that extracts bindings from scrutinee and computes body
            createMatchBodyEvaluator(
              matchCase.pattern,
              matchCase.bindings,
              bodyDataId,
              bodySpec,
              scrutineeCType
            )
          }
        }

        bodyEvaluatorsResult.map { bodyEvaluators =>
          // Create pattern matcher functions
          val patternMatchers = cases.map { c =>
            createPatternMatcher(c.pattern)
          }

          // Create inline transform that handles pattern matching
          val matchTransform = InlineTransform.MatchTransform(
            patternMatchers,
            bodyEvaluators,
            scrutineeCType
          )

          // Create output data node with inline transform
          dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
            name = s"match_${id.toString.take(8)}_output",
            nicknames = Map.empty,
            cType = outputCType,
            inlineTransform = Some(matchTransform),
            transformInputs = Map("scrutinee" -> scrutineeDataId)
          ))

          nodeOutputs = nodeOutputs + (id -> outputDataId)
        }
      }
    }

    /** Create an evaluator function for a match case body.
      * This function extracts bindings from the scrutinee and evaluates the body expression.
      */
    private def createMatchBodyEvaluator(
        pattern: PatternIR,
        bindings: Map[String, SemanticType],
        bodyDataId: UUID,
        bodySpec: DataNodeSpec,
        scrutineeCType: CType
    ): Any => Any = { (scrutineeValue: Any) =>
      // Unwrap union value if necessary
      val recordValue = scrutineeValue match {
        case (tag: String, inner) => inner
        case other                => other
      }

      // Extract field bindings from the record
      val bindingValues = recordValue match {
        case m: Map[String, Any] @unchecked =>
          bindings.keys.map(name => name -> m.getOrElse(name, null)).toMap
        case _ =>
          Map.empty[String, Any]
      }

      // If body has an inline transform, evaluate it with bindings as inputs
      bodySpec.inlineTransform match {
        case Some(transform) =>
          // Merge bindings with any other transform inputs
          // For string interpolation, the bindings are the expression values
          transform.apply(bindingValues)
        case None =>
          // No inline transform - body is a literal or direct reference
          // This case handles simple patterns like `_ -> 42`
          bindingValues.values.headOption.getOrElse(null)
      }
    }

    /** Create a pattern matcher function from a PatternIR.
      * The returned function checks if a value matches the pattern at runtime.
      * For union types, the value is already unwrapped before matching.
      */
    private def createPatternMatcher(pattern: PatternIR): Any => Boolean = { (value: Any) =>
      pattern match {
        case PatternIR.Record(fields, _) =>
          value match {
            case m: Map[String, ?] @unchecked => fields.forall(m.contains)
            case _                            => false
          }

        case PatternIR.TypeTest(typeName, _) =>
          typeName match {
            case "String"  => value.isInstanceOf[String]
            case "Int"     => value.isInstanceOf[Long] || value.isInstanceOf[Int]
            case "Float"   => value.isInstanceOf[Double] || value.isInstanceOf[Float]
            case "Boolean" => value.isInstanceOf[Boolean]
            case _         => false // Unknown type
          }

        case PatternIR.Wildcard() =>
          true // Wildcard matches anything
      }
    }

    /** Create a match module that evaluates patterns in order.
      * Returns the expression for the first matching pattern.
      * @deprecated Use inline MatchTransform instead
      */
    private def createMatchModule(
        spec: ModuleNodeSpec,
        cases: List[MatchCaseIR],
        outputCType: CType
    ): Module.Uninitialized =
      Module.Uninitialized(
        spec = spec,
        init = (moduleId, dagSpec) =>
          for {
            consumesNs <- Module.Namespace.consumes(moduleId, dagSpec)
            producesNs <- Module.Namespace.produces(moduleId, dagSpec)
            // Create deferreds for scrutinee and all case bodies
            scrutineeDeferred <- cats.effect.Deferred[IO, Any]
            bodyDeferreds     <- cases.indices.toList.traverse(_ => cats.effect.Deferred[IO, Any])
            outDeferred       <- cats.effect.Deferred[IO, Any]
            // Get name IDs
            scrutineeId <- consumesNs.nameId("scrutinee")
            bodyIds     <- cases.indices.toList.traverse(i => consumesNs.nameId(s"body$i"))
            outId       <- producesNs.nameId("out")
          } yield {
            val dataMap = List((scrutineeId, scrutineeDeferred)) ++
              bodyIds.zip(bodyDeferreds) ++
              List((outId, outDeferred))
            Module.Runnable(
              id = moduleId,
              data = dataMap.toMap,
              run = runtime => {
                for {
                  scrutineeValue <- runtime.getTableData(scrutineeId)
                  // Find the first matching pattern
                  matchingIdx = cases.zipWithIndex.find { case (matchCase, _) =>
                    patternMatches(matchCase.pattern, scrutineeValue)
                  }.map(_._2)
                  result <- matchingIdx match {
                    case Some(idx) => runtime.getTableData(bodyIds(idx))
                    case None =>
                      // No pattern matched - this shouldn't happen if exhaustiveness was checked
                      IO.raiseError(
                        new MatchError(s"No pattern matched value: $scrutineeValue")
                      )
                  }
                  _ <- runtime.setTableData(outId, result)
                  // Also store in state for output extraction
                  cValue = Runtime.anyToCValue(result, outputCType)
                  _ <- runtime.setStateData(outId, cValue)
                } yield ()
              }
            )
          }
      )

    /** Check if a pattern matches a value at runtime.
      * Handles union values which are represented as (tag, innerValue) tuples.
      */
    private def patternMatches(pattern: PatternIR, value: Any): Boolean = {
      // Unwrap union values: (tag: String, innerValue: Any)
      val unwrapped = value match {
        case (tag: String, inner) => inner
        case other                => other
      }

      pattern match {
        case PatternIR.Record(fields, _) =>
          unwrapped match {
            case m: Map[String, ?] @unchecked =>
              fields.forall(m.contains)
            case _ => false
          }

        case PatternIR.TypeTest(typeName, _) =>
          typeName match {
            case "String"  => unwrapped.isInstanceOf[String]
            case "Int"     => unwrapped.isInstanceOf[Long] || unwrapped.isInstanceOf[Int]
            case "Float"   => unwrapped.isInstanceOf[Double] || unwrapped.isInstanceOf[Float]
            case "Boolean" => unwrapped.isInstanceOf[Boolean]
            case _         => false // Unknown type
          }

        case PatternIR.Wildcard() =>
          true // Wildcard matches anything
      }
    }

    private def processHigherOrderNode(
        id: UUID,
        operation: HigherOrderOp,
        source: UUID,
        lambda: TypedLambda,
        outputType: SemanticType
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      for {
        sourceDataId <- getNodeOutput(source, s"source of ${operation.toString.toLowerCase}")
        transform    <- createHigherOrderTransform(operation, lambda)
      } yield {
        val outputCType = SemanticType.toCType(outputType)

        // Create data node with inline transform
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"hof_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = outputCType,
          inlineTransform = Some(transform),
          transformInputs = Map("source" -> sourceDataId)
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processListLiteralNode(
        id: UUID,
        elements: List[UUID],
        elementType: SemanticType
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      // Get data IDs for all element expressions
      val elemDataIdsResult = elements.zipWithIndex.traverse { case (elemNodeId, idx) =>
        getNodeOutput(elemNodeId, s"element $idx in list literal").map { dataId =>
          (s"elem$idx", dataId)
        }
      }

      elemDataIdsResult.map { elemDataIds =>
        val outputCType = CType.CList(SemanticType.toCType(elementType))

        // Create data node with inline transform
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"list_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = outputCType,
          inlineTransform = Some(InlineTransform.ListLiteralTransform(elements.size)),
          transformInputs = elemDataIds.toMap
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    private def processRecordLitNode(
        id: UUID,
        fields: List[(String, UUID)],
        outputType: SemanticType
    ): Either[DagCompilerError, Unit] = {
      val outputDataId = UUID.randomUUID()

      // Get data IDs for all field value expressions
      val fieldDataIdsResult = fields.traverse { case (fieldName, fieldNodeId) =>
        getNodeOutput(fieldNodeId, s"field '$fieldName' in record literal").map { dataId =>
          (fieldName, dataId)
        }
      }

      fieldDataIdsResult.map { fieldDataIds =>
        val outputCType = SemanticType.toCType(outputType)
        val fieldNames  = fields.map(_._1)

        // Create data node with inline transform
        dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
          name = s"record_${id.toString.take(8)}_output",
          nicknames = Map.empty,
          cType = outputCType,
          inlineTransform = Some(InlineTransform.RecordBuildTransform(fieldNames)),
          transformInputs = fieldDataIds.toMap
        ))
        nodeOutputs = nodeOutputs + (id -> outputDataId)
      }
    }

    /** Create the appropriate inline transform for a higher-order operation */
    private def createHigherOrderTransform(
        operation: HigherOrderOp,
        lambda: TypedLambda
    ): Either[DagCompilerError, InlineTransform] =
      // Create the lambda evaluator function - this can fail
      createLambdaEvaluator(lambda).flatMap { lambdaEvaluator =>
        operation match {
          case HigherOrderOp.Filter =>
            Right(InlineTransform.FilterTransform(lambdaEvaluator.asInstanceOf[Any => Boolean]))
          case HigherOrderOp.Map =>
            Right(InlineTransform.MapTransform(lambdaEvaluator))
          case HigherOrderOp.All =>
            Right(InlineTransform.AllTransform(lambdaEvaluator.asInstanceOf[Any => Boolean]))
          case HigherOrderOp.Any =>
            Right(InlineTransform.AnyTransform(lambdaEvaluator.asInstanceOf[Any => Boolean]))
          case HigherOrderOp.SortBy =>
            Left(DagCompilerError.UnsupportedOperation("SortBy"))
        }
      }

    /** Create a lambda evaluator function from a TypedLambda. Returns Either to handle potential
      * errors in lambda body structure.
      */
    private def createLambdaEvaluator(lambda: TypedLambda): Either[DagCompilerError, Any => Any] =
      // Validate the lambda body structure upfront
      validateLambdaBody(lambda.bodyNodes, lambda.bodyOutputId).map { _ => (element: Any) =>
        {
          // Bind parameter to element value
          val paramBindings: Map[String, Any] = lambda.paramNames.zip(List(element)).toMap

          // Evaluate the lambda body - errors here become runtime exceptions
          // since the structure was validated at compile time
          evaluateLambdaBodyUnsafe(lambda.bodyNodes, lambda.bodyOutputId, paramBindings)
        }
      }

    /** Validate lambda body structure at compile time */
    private def validateLambdaBody(
        nodes: Map[UUID, IRNode],
        outputId: UUID
    ): Either[DagCompilerError, Unit] = {
      def validateNode(nodeId: UUID): Either[DagCompilerError, Unit] =
        nodes.get(nodeId) match {
          case None => Left(DagCompilerError.NodeNotFound(nodeId, "lambda body"))
          case Some(node) =>
            node match {
              case IRNode.Input(_, _, _, _)                   => Right(())
              case IRNode.LiteralNode(_, _, _, _)             => Right(())
              case IRNode.FieldAccessNode(_, source, _, _, _) => validateNode(source)
              case IRNode.AndNode(_, left, right, _) =>
                validateNode(left).flatMap(_ => validateNode(right))
              case IRNode.OrNode(_, left, right, _) =>
                validateNode(left).flatMap(_ => validateNode(right))
              case IRNode.NotNode(_, operand, _) => validateNode(operand)
              case IRNode.ModuleCall(_, moduleName, _, inputs, _, _, _) =>
                validateBuiltinFunction(moduleName).flatMap { _ =>
                  inputs.values.toList.traverse(validateNode).map(_ => ())
                }
              case IRNode.ConditionalNode(_, cond, thenBr, elseBr, _, _) =>
                for {
                  _ <- validateNode(cond)
                  _ <- validateNode(thenBr)
                  _ <- validateNode(elseBr)
                } yield ()
              case IRNode.ListLiteralNode(_, elems, _, _) =>
                elems.traverse(validateNode).map(_ => ())
              case other =>
                Left(
                  DagCompilerError.UnsupportedNodeType(other.getClass.getSimpleName, "lambda body")
                )
            }
        }
      validateNode(outputId)
    }

    /** Validate that a function is supported in lambda bodies */
    private def validateBuiltinFunction(moduleName: String): Either[DagCompilerError, Unit] = {
      val funcName = moduleName.split('.').last
      val supported = Set(
        "add",
        "add-int",
        "subtract",
        "sub-int",
        "multiply",
        "mul-int",
        "divide",
        "div-int",
        "gt",
        "lt",
        "gte",
        "lte",
        "eq-int",
        "eq-string"
      )
      if supported.contains(funcName) then Right(())
      else Left(DagCompilerError.UnsupportedFunction(moduleName, funcName))
    }

    /** Evaluate lambda body nodes with given parameter bindings. This is called at runtime after
      * validation, so errors become exceptions.
      */
    private def evaluateLambdaBodyUnsafe(
        nodes: Map[UUID, IRNode],
        outputId: UUID,
        paramBindings: Map[String, Any]
    ): Any = {
      // Simple interpreter for lambda body nodes
      var evaluatedNodes: Map[UUID, Any] = Map.empty

      def evaluateNode(nodeId: UUID): Any =
        evaluatedNodes.get(nodeId) match {
          case Some(value) => value
          case None =>
            val node  = nodes(nodeId)
            val value = evaluateLambdaNodeUnsafe(node, nodes, paramBindings, evaluateNode)
            evaluatedNodes = evaluatedNodes + (nodeId -> value)
            value
        }

      evaluateNode(outputId)
    }

    /** Evaluate a single IR node in lambda context. Called at runtime after compile-time
      * validation.
      */
    private def evaluateLambdaNodeUnsafe(
        node: IRNode,
        nodes: Map[UUID, IRNode],
        paramBindings: Map[String, Any],
        evaluateNode: UUID => Any
    ): Any = node match {
      case IRNode.Input(_, name, _, _) =>
        // Lambda parameter - look up in bindings
        paramBindings.getOrElse(
          name,
          throw DagCompilerError.toException(DagCompilerError.LambdaParameterNotBound(name))
        )

      case IRNode.LiteralNode(_, value, _, _) =>
        value

      case IRNode.FieldAccessNode(_, source, field, _, _) =>
        val sourceValue = evaluateNode(source)
        sourceValue match {
          case m: Map[String, ?] @unchecked => m(field)
          case _ =>
            throw DagCompilerError.toException(
              DagCompilerError.InvalidFieldAccess(field, sourceValue.getClass.getSimpleName)
            )
        }

      case IRNode.AndNode(_, left, right, _) =>
        val leftVal = evaluateNode(left).asInstanceOf[Boolean]
        if !leftVal then false else evaluateNode(right).asInstanceOf[Boolean]

      case IRNode.OrNode(_, left, right, _) =>
        val leftVal = evaluateNode(left).asInstanceOf[Boolean]
        if leftVal then true else evaluateNode(right).asInstanceOf[Boolean]

      case IRNode.NotNode(_, operand, _) =>
        !evaluateNode(operand).asInstanceOf[Boolean]

      case IRNode.ModuleCall(_, moduleName, _, inputs, _, _, _) =>
        // For simple comparison/arithmetic operations in lambda bodies
        val inputValues = inputs.map { case (name, nodeId) => name -> evaluateNode(nodeId) }
        evaluateBuiltinFunctionUnsafe(moduleName, inputValues)

      case IRNode.ConditionalNode(_, cond, thenBr, elseBr, _, _) =>
        val condVal = evaluateNode(cond).asInstanceOf[Boolean]
        if condVal then evaluateNode(thenBr) else evaluateNode(elseBr)

      case IRNode.ListLiteralNode(_, elems, _, _) =>
        elems.map(evaluateNode).toList

      case other =>
        throw DagCompilerError.toException(
          DagCompilerError.UnsupportedNodeType(other.getClass.getSimpleName, "lambda body")
        )
    }

    /** Evaluate a built-in function for use in lambda bodies. Called at runtime after compile-time
      * validation.
      */
    private def evaluateBuiltinFunctionUnsafe(moduleName: String, inputs: Map[String, Any]): Any = {
      // Extract function name from module name (e.g., "stdlib.multiply" -> "multiply")
      val funcName = moduleName.split('.').last

      funcName match {
        // Arithmetic operations
        case "add" | "add-int" =>
          inputs("a").asInstanceOf[Long] + inputs("b").asInstanceOf[Long]
        case "subtract" | "sub-int" =>
          inputs("a").asInstanceOf[Long] - inputs("b").asInstanceOf[Long]
        case "multiply" | "mul-int" =>
          inputs("a").asInstanceOf[Long] * inputs("b").asInstanceOf[Long]
        case "divide" | "div-int" =>
          val b = inputs("b").asInstanceOf[Long]
          if b != 0 then inputs("a").asInstanceOf[Long] / b else 0L

        // Comparison operations
        case "gt" =>
          inputs("a").asInstanceOf[Long] > inputs("b").asInstanceOf[Long]
        case "lt" =>
          inputs("a").asInstanceOf[Long] < inputs("b").asInstanceOf[Long]
        case "gte" =>
          inputs("a").asInstanceOf[Long] >= inputs("b").asInstanceOf[Long]
        case "lte" =>
          inputs("a").asInstanceOf[Long] <= inputs("b").asInstanceOf[Long]
        case "eq-int" =>
          inputs("a").asInstanceOf[Long] == inputs("b").asInstanceOf[Long]
        case "eq-string" =>
          inputs("a").asInstanceOf[String] == inputs("b").asInstanceOf[String]

        case _ =>
          throw DagCompilerError.toException(
            DagCompilerError.UnsupportedFunction(moduleName, funcName)
          )
      }
    }
  }
}
