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

      case IRNode.ConditionalNode(id, condition, thenBranch, elseBranch, outputType, _) =>
        processConditionalNode(id, condition, thenBranch, elseBranch, outputType)

      case IRNode.LiteralNode(id, value, outputType, _) =>
        processLiteralNode(id, value, outputType)
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

      // Look up the registered module
      registeredModules.get(moduleName) match {
        case Some(uninitModule) =>
          // Use the module's spec
          val spec = uninitModule.spec.copy(
            metadata = uninitModule.spec.metadata.copy(name = s"$dagName.$languageName")
          )
          moduleNodes = moduleNodes + (moduleId -> spec)
          syntheticModules = syntheticModules + (moduleId -> uninitModule)

        case None =>
          // Create a placeholder module spec (for testing or when module is provided at runtime)
          val cType = SemanticType.toCType(outputType)
          val spec = ModuleNodeSpec(
            metadata = ComponentMetadata.empty(s"$dagName.$languageName"),
            consumes = inputs.map { case (name, _) => name -> CType.CString }, // Placeholder
            produces = Map("out" -> cType)
          )
          moduleNodes = moduleNodes + (moduleId -> spec)
      }

      // Connect input data nodes to the module
      inputs.foreach { case (_, inputNodeId) =>
        val inputDataId = nodeOutputs.getOrElse(inputNodeId,
          throw new IllegalStateException(s"Input node $inputNodeId not found")
        )
        inEdges = inEdges + ((inputDataId, moduleId))
      }

      // Create output data node
      val cType = SemanticType.toCType(outputType)
      dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
        name = languageName + "_output",
        nicknames = Map(moduleId -> "out"),
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
      val moduleId = UUID.randomUUID()
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

      // Create synthetic merge module
      val spec = ModuleNodeSpec(
        metadata = ComponentMetadata.empty(s"$dagName.merge-${id.toString.take(8)}"),
        consumes = Map("left" -> leftCType, "right" -> rightCType),
        produces = Map("out" -> outputCType)
      )
      moduleNodes = moduleNodes + (moduleId -> spec)

      // Create synthetic module implementation
      val syntheticModule = createMergeModule(spec, leftCType, rightCType, outputCType)
      syntheticModules = syntheticModules + (moduleId -> syntheticModule)

      // Update nicknames for input data nodes
      dataNodes = dataNodes.updatedWith(leftDataId) {
        case Some(spec) => Some(spec.copy(nicknames = spec.nicknames + (moduleId -> "left")))
        case None => None
      }
      dataNodes = dataNodes.updatedWith(rightDataId) {
        case Some(spec) => Some(spec.copy(nicknames = spec.nicknames + (moduleId -> "right")))
        case None => None
      }

      // Connect edges
      inEdges = inEdges + ((leftDataId, moduleId)) + ((rightDataId, moduleId))

      // Create output data node
      dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
        name = s"merge_${id.toString.take(8)}_output",
        nicknames = Map(moduleId -> "out"),
        cType = outputCType
      ))
      outEdges = outEdges + ((moduleId, outputDataId))
      nodeOutputs = nodeOutputs + (id -> outputDataId)
    }

    private def processProjectNode(
      id: UUID,
      source: UUID,
      fields: List[String],
      outputType: SemanticType
    ): Unit = {
      val moduleId = UUID.randomUUID()
      val outputDataId = UUID.randomUUID()

      val sourceDataId = nodeOutputs.getOrElse(source,
        throw new IllegalStateException(s"Source node $source not found")
      )

      val sourceCType = dataNodes(sourceDataId).cType
      val outputCType = SemanticType.toCType(outputType)

      // Create synthetic projection module
      val spec = ModuleNodeSpec(
        metadata = ComponentMetadata.empty(s"$dagName.project-${id.toString.take(8)}"),
        consumes = Map("source" -> sourceCType),
        produces = Map("out" -> outputCType)
      )
      moduleNodes = moduleNodes + (moduleId -> spec)

      // Create synthetic module implementation
      val syntheticModule = createProjectionModule(spec, fields, sourceCType, outputCType)
      syntheticModules = syntheticModules + (moduleId -> syntheticModule)

      // Update nicknames for input data node
      dataNodes = dataNodes.updatedWith(sourceDataId) {
        case Some(spec) => Some(spec.copy(nicknames = spec.nicknames + (moduleId -> "source")))
        case None => None
      }

      // Connect edges
      inEdges = inEdges + ((sourceDataId, moduleId))

      // Create output data node
      dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
        name = s"project_${id.toString.take(8)}_output",
        nicknames = Map(moduleId -> "out"),
        cType = outputCType
      ))
      outEdges = outEdges + ((moduleId, outputDataId))
      nodeOutputs = nodeOutputs + (id -> outputDataId)
    }

    private def processConditionalNode(
      id: UUID,
      condition: UUID,
      thenBranch: UUID,
      elseBranch: UUID,
      outputType: SemanticType
    ): Unit = {
      val moduleId = UUID.randomUUID()
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

      val condCType = dataNodes(condDataId).cType
      val thenCType = dataNodes(thenDataId).cType
      val elseCType = dataNodes(elseDataId).cType
      val outputCType = SemanticType.toCType(outputType)

      // Create synthetic conditional module
      val spec = ModuleNodeSpec(
        metadata = ComponentMetadata.empty(s"$dagName.conditional-${id.toString.take(8)}"),
        consumes = Map("cond" -> condCType, "thenBr" -> thenCType, "elseBr" -> elseCType),
        produces = Map("out" -> outputCType)
      )
      moduleNodes = moduleNodes + (moduleId -> spec)

      // Create synthetic module implementation
      val syntheticModule = createConditionalModule(spec, outputCType)
      syntheticModules = syntheticModules + (moduleId -> syntheticModule)

      // Update nicknames
      dataNodes = dataNodes.updatedWith(condDataId) {
        case Some(spec) => Some(spec.copy(nicknames = spec.nicknames + (moduleId -> "cond")))
        case None => None
      }
      dataNodes = dataNodes.updatedWith(thenDataId) {
        case Some(spec) => Some(spec.copy(nicknames = spec.nicknames + (moduleId -> "thenBr")))
        case None => None
      }
      dataNodes = dataNodes.updatedWith(elseDataId) {
        case Some(spec) => Some(spec.copy(nicknames = spec.nicknames + (moduleId -> "elseBr")))
        case None => None
      }

      // Connect edges
      inEdges = inEdges + ((condDataId, moduleId)) + ((thenDataId, moduleId)) + ((elseDataId, moduleId))

      // Create output data node
      dataNodes = dataNodes + (outputDataId -> DataNodeSpec(
        name = s"conditional_${id.toString.take(8)}_output",
        nicknames = Map(moduleId -> "out"),
        cType = outputCType
      ))
      outEdges = outEdges + ((moduleId, outputDataId))
      nodeOutputs = nodeOutputs + (id -> outputDataId)
    }

    private def processLiteralNode(
      id: UUID,
      value: Any,
      outputType: SemanticType
    ): Unit = {
      // Literals become input data nodes with constant values
      val dataId = UUID.randomUUID()
      val cType = SemanticType.toCType(outputType)
      dataNodes = dataNodes + (dataId -> DataNodeSpec(
        name = s"literal_${id.toString.take(8)}",
        nicknames = Map(dataId -> s"literal_${id.toString.take(8)}"),
        cType = cType
      ))
      nodeOutputs = nodeOutputs + (id -> dataId)
    }

    /** Create a merge module that combines two records */
    private def createMergeModule(
      spec: ModuleNodeSpec,
      leftCType: CType,
      rightCType: CType,
      outputCType: CType
    ): Module.Uninitialized = {
      Module.Uninitialized(
        spec = spec,
        init = (moduleId, dagSpec) => {
          for {
            consumesNs <- Module.Namespace.consumes(moduleId, dagSpec)
            producesNs <- Module.Namespace.produces(moduleId, dagSpec)
            leftDeferred <- cats.effect.Deferred[IO, Any]
            rightDeferred <- cats.effect.Deferred[IO, Any]
            outDeferred <- cats.effect.Deferred[IO, Any]
            leftId <- consumesNs.nameId("left")
            rightId <- consumesNs.nameId("right")
            outId <- producesNs.nameId("out")
          } yield Module.Runnable(
            id = moduleId,
            data = Map(leftId -> leftDeferred, rightId -> rightDeferred, outId -> outDeferred),
            run = runtime => {
              for {
                leftValue <- runtime.getTableData(leftId)
                rightValue <- runtime.getTableData(rightId)
                merged = mergeValues(leftValue, rightValue, leftCType, rightCType)
                _ <- runtime.setTableData(outId, merged)
              } yield ()
            }
          )
        }
      )
    }

    /** Merge two values (record merge or list element-wise merge) */
    private def mergeValues(left: Any, right: Any, leftCType: CType, rightCType: CType): Any = {
      (left, right, leftCType, rightCType) match {
        case (lMap: Map[String, ?] @unchecked, rMap: Map[String, ?] @unchecked, _, _) =>
          lMap ++ rMap

        case (lList: List[?] @unchecked, rList: List[?] @unchecked, CType.CList(lElem), CType.CList(rElem)) =>
          lList.zip(rList).map { case (l, r) => mergeValues(l, r, lElem, rElem) }

        case (lList: List[?] @unchecked, rMap: Map[String, ?] @unchecked, CType.CList(_), _) =>
          lList.map {
            case elemMap: Map[String, ?] @unchecked => elemMap ++ rMap
            case other => other
          }

        case (lMap: Map[String, ?] @unchecked, rList: List[?] @unchecked, _, CType.CList(_)) =>
          rList.map {
            case elemMap: Map[String, ?] @unchecked => lMap ++ elemMap
            case other => other
          }

        case _ => right // Fallback: right wins
      }
    }

    /** Create a projection module that selects fields */
    private def createProjectionModule(
      spec: ModuleNodeSpec,
      fields: List[String],
      sourceCType: CType,
      outputCType: CType
    ): Module.Uninitialized = {
      Module.Uninitialized(
        spec = spec,
        init = (moduleId, dagSpec) => {
          for {
            consumesNs <- Module.Namespace.consumes(moduleId, dagSpec)
            producesNs <- Module.Namespace.produces(moduleId, dagSpec)
            sourceDeferred <- cats.effect.Deferred[IO, Any]
            outDeferred <- cats.effect.Deferred[IO, Any]
            sourceId <- consumesNs.nameId("source")
            outId <- producesNs.nameId("out")
          } yield Module.Runnable(
            id = moduleId,
            data = Map(sourceId -> sourceDeferred, outId -> outDeferred),
            run = runtime => {
              for {
                sourceValue <- runtime.getTableData(sourceId)
                projected = projectFields(sourceValue, fields, sourceCType)
                _ <- runtime.setTableData(outId, projected)
              } yield ()
            }
          )
        }
      )
    }

    /** Project fields from a value */
    private def projectFields(value: Any, fields: List[String], cType: CType): Any = {
      (value, cType) match {
        case (map: Map[String, ?] @unchecked, CType.CProduct(_)) =>
          fields.flatMap(f => map.get(f).map(f -> _)).toMap

        case (list: List[?] @unchecked, CType.CList(elemType)) =>
          list.map(elem => projectFields(elem, fields, elemType))

        case _ => value
      }
    }

    /** Create a conditional module */
    private def createConditionalModule(
      spec: ModuleNodeSpec,
      outputCType: CType
    ): Module.Uninitialized = {
      Module.Uninitialized(
        spec = spec,
        init = (moduleId, dagSpec) => {
          for {
            consumesNs <- Module.Namespace.consumes(moduleId, dagSpec)
            producesNs <- Module.Namespace.produces(moduleId, dagSpec)
            condDeferred <- cats.effect.Deferred[IO, Any]
            thenDeferred <- cats.effect.Deferred[IO, Any]
            elseDeferred <- cats.effect.Deferred[IO, Any]
            outDeferred <- cats.effect.Deferred[IO, Any]
            condId <- consumesNs.nameId("cond")
            thenId <- consumesNs.nameId("thenBr")
            elseId <- consumesNs.nameId("elseBr")
            outId <- producesNs.nameId("out")
          } yield Module.Runnable(
            id = moduleId,
            data = Map(condId -> condDeferred, thenId -> thenDeferred, elseId -> elseDeferred, outId -> outDeferred),
            run = runtime => {
              for {
                condValue <- runtime.getTableData(condId)
                thenValue <- runtime.getTableData(thenId)
                elseValue <- runtime.getTableData(elseId)
                result = if (condValue.asInstanceOf[Boolean]) thenValue else elseValue
                _ <- runtime.setTableData(outId, result)
              } yield ()
            }
          )
        }
      )
    }
  }
}
