package io.constellation.lang.compiler

import cats.effect.IO
import io.constellation.*
import io.constellation.lang.semantic.SemanticType

import java.util.UUID

/** Compilation result containing the DagSpec and synthetic modules */
final case class CompileResult(
  dagSpec: DagSpec,
  syntheticModules: Map[UUID, Module.Uninitialized]
)

/** Compiles IR to Constellation DagSpec */
object DagCompiler {

  /** Compile an IR program to a DagSpec and synthetic modules */
  def compile(program: IRProgram, dagName: String, registeredModules: Map[String, Module.Uninitialized]): CompileResult = {
    val compiler = new DagCompilerState(dagName, registeredModules)
    compiler.compile(program)
  }

  /** Internal compiler state */
  private class DagCompilerState(
    dagName: String,
    registeredModules: Map[String, Module.Uninitialized]
  ) {
    // Maps IR node ID -> data node UUID (output of that IR node)
    private var dataNodes: Map[UUID, DataNodeSpec] = Map.empty
    private var moduleNodes: Map[UUID, ModuleNodeSpec] = Map.empty
    private var syntheticModules: Map[UUID, Module.Uninitialized] = Map.empty
    private var inEdges: Set[(UUID, UUID)] = Set.empty   // data -> module
    private var outEdges: Set[(UUID, UUID)] = Set.empty  // module -> data

    // Maps IR node ID -> its output data node UUID
    private var nodeOutputs: Map[UUID, UUID] = Map.empty

    def compile(program: IRProgram): CompileResult = {
      // Process nodes in topological order
      program.topologicalOrder.foreach { nodeId =>
        program.nodes.get(nodeId).foreach(processNode)
      }

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

      CompileResult(dagSpec, syntheticModules)
    }

    private def processNode(node: IRNode): Unit = node match {
      case IRNode.Input(id, name, outputType, _) =>
        // Input nodes become top-level data nodes
        val dataId = UUID.randomUUID()
        val cType = SemanticType.toCType(outputType)
        dataNodes = dataNodes + (dataId -> DataNodeSpec(name, Map(dataId -> name), cType))
        nodeOutputs = nodeOutputs + (id -> dataId)

      case IRNode.ModuleCall(id, moduleName, languageName, inputs, outputType, _) =>
        processModuleCall(id, moduleName, languageName, inputs, outputType)

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
    }

    private def processModuleCall(
      id: UUID,
      moduleName: String,
      languageName: String,
      inputs: Map[String, UUID],
      outputType: SemanticType
    ): Unit = {
      val moduleId = UUID.randomUUID()
      val outputDataId = UUID.randomUUID()

      // Look up the registered module and get output field name
      val outputFieldName = registeredModules.get(moduleName) match {
        case Some(uninitModule) =>
          // Use the module's spec
          val spec = uninitModule.spec.copy(
            metadata = uninitModule.spec.metadata.copy(name = s"$dagName.$languageName")
          )
          moduleNodes = moduleNodes + (moduleId -> spec)
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
      inputs.foreach { case (paramName, inputNodeId) =>
        val inputDataId = nodeOutputs.getOrElse(inputNodeId,
          throw new IllegalStateException(s"Input node $inputNodeId not found")
        )
        inEdges = inEdges + ((inputDataId, moduleId))
        // Update the data node's nicknames to include this module's parameter name
        dataNodes = dataNodes.updatedWith(inputDataId) {
          case Some(spec) => Some(spec.copy(nicknames = spec.nicknames + (moduleId -> paramName)))
          case None => None
        }
      }

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

    private def processMergeNode(
      id: UUID,
      left: UUID,
      right: UUID,
      outputType: SemanticType
    ): Unit = {
      val outputDataId = UUID.randomUUID()

      val leftDataId = nodeOutputs.getOrElse(left,
        throw new IllegalStateException(s"Left node $left not found")
      )
      val rightDataId = nodeOutputs.getOrElse(right,
        throw new IllegalStateException(s"Right node $right not found")
      )

      val leftCType = dataNodes(leftDataId).cType
      val rightCType = dataNodes(rightDataId).cType
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

    private def processProjectNode(
      id: UUID,
      source: UUID,
      fields: List[String],
      outputType: SemanticType
    ): Unit = {
      val outputDataId = UUID.randomUUID()

      val sourceDataId = nodeOutputs.getOrElse(source,
        throw new IllegalStateException(s"Source node $source not found")
      )

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

    private def processFieldAccessNode(
      id: UUID,
      source: UUID,
      field: String,
      outputType: SemanticType
    ): Unit = {
      val outputDataId = UUID.randomUUID()

      val sourceDataId = nodeOutputs.getOrElse(source,
        throw new IllegalStateException(s"Source node $source not found")
      )

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

    private def processConditionalNode(
      id: UUID,
      condition: UUID,
      thenBranch: UUID,
      elseBranch: UUID,
      outputType: SemanticType
    ): Unit = {
      val outputDataId = UUID.randomUUID()

      val condDataId = nodeOutputs.getOrElse(condition,
        throw new IllegalStateException(s"Condition node $condition not found")
      )
      val thenDataId = nodeOutputs.getOrElse(thenBranch,
        throw new IllegalStateException(s"Then branch node $thenBranch not found")
      )
      val elseDataId = nodeOutputs.getOrElse(elseBranch,
        throw new IllegalStateException(s"Else branch node $elseBranch not found")
      )

      val outputCType = SemanticType.toCType(outputType)

      // Create data node with inline transform (no synthetic module needed)
      dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
        name = s"conditional_${id.toString.take(8)}_output",
        nicknames = Map.empty,
        cType = outputCType,
        inlineTransform = Some(InlineTransform.ConditionalTransform),
        transformInputs = Map("cond" -> condDataId, "thenBr" -> thenDataId, "elseBr" -> elseDataId)
      ))
      nodeOutputs = nodeOutputs + (id -> outputDataId)
    }

    private def processLiteralNode(
      id: UUID,
      value: Any,
      outputType: SemanticType
    ): Unit = {
      // Literals become data nodes with inline literal transform
      val dataId = UUID.randomUUID()
      val cType = SemanticType.toCType(outputType)
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
    ): Unit = {
      val outputDataId = UUID.randomUUID()

      val leftDataId = nodeOutputs.getOrElse(left,
        throw new IllegalStateException(s"Left node $left not found")
      )
      val rightDataId = nodeOutputs.getOrElse(right,
        throw new IllegalStateException(s"Right node $right not found")
      )

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

    private def processOrNode(
      id: UUID,
      left: UUID,
      right: UUID
    ): Unit = {
      val outputDataId = UUID.randomUUID()

      val leftDataId = nodeOutputs.getOrElse(left,
        throw new IllegalStateException(s"Left node $left not found")
      )
      val rightDataId = nodeOutputs.getOrElse(right,
        throw new IllegalStateException(s"Right node $right not found")
      )

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

    private def processNotNode(
      id: UUID,
      operand: UUID
    ): Unit = {
      val outputDataId = UUID.randomUUID()

      val operandDataId = nodeOutputs.getOrElse(operand,
        throw new IllegalStateException(s"Operand node $operand not found")
      )

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

    private def processGuardNode(
      id: UUID,
      expr: UUID,
      condition: UUID,
      innerType: SemanticType
    ): Unit = {
      val outputDataId = UUID.randomUUID()

      val exprDataId = nodeOutputs.getOrElse(expr,
        throw new IllegalStateException(s"Expression node $expr not found")
      )
      val condDataId = nodeOutputs.getOrElse(condition,
        throw new IllegalStateException(s"Condition node $condition not found")
      )

      val exprCType = dataNodes(exprDataId).cType
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

    private def processCoalesceNode(
      id: UUID,
      left: UUID,
      right: UUID,
      resultType: SemanticType
    ): Unit = {
      val outputDataId = UUID.randomUUID()

      val leftDataId = nodeOutputs.getOrElse(left,
        throw new IllegalStateException(s"Left node $left not found")
      )
      val rightDataId = nodeOutputs.getOrElse(right,
        throw new IllegalStateException(s"Right node $right not found")
      )

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
}
