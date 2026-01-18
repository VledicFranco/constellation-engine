package io.constellation.lang.compiler

import io.constellation.lang.ast.Span
import io.constellation.lang.semantic.SemanticType

import java.util.UUID

/** Intermediate representation for constellation-lang programs.
  *
  * The IR represents a data-flow graph where:
  * - Each node produces a value with a known type
  * - Dependencies between nodes are explicit via UUID references
  * - This IR is designed to be easily compiled to Constellation DagSpec
  */
sealed trait IRNode {
  def id: UUID
  def outputType: SemanticType
  def debugSpan: Option[Span]
}

object IRNode {

  /** An input node representing external data entering the DAG */
  final case class Input(
    id: UUID,
    name: String,
    outputType: SemanticType,
    debugSpan: Option[Span] = None
  ) extends IRNode

  /** A module call node representing an ML model or function invocation */
  final case class ModuleCall(
    id: UUID,
    moduleName: String,           // The registered module name in Constellation
    languageName: String,         // The name used in constellation-lang
    inputs: Map[String, UUID],    // Parameter name -> input node ID
    outputType: SemanticType,
    debugSpan: Option[Span] = None
  ) extends IRNode

  /** A merge node combining two record types using type algebra */
  final case class MergeNode(
    id: UUID,
    left: UUID,
    right: UUID,
    outputType: SemanticType,
    debugSpan: Option[Span] = None
  ) extends IRNode

  /** A projection node selecting fields from a record */
  final case class ProjectNode(
    id: UUID,
    source: UUID,
    fields: List[String],
    outputType: SemanticType,
    debugSpan: Option[Span] = None
  ) extends IRNode

  /** A field access node extracting a single field value from a record */
  final case class FieldAccessNode(
    id: UUID,
    source: UUID,
    field: String,
    outputType: SemanticType,
    debugSpan: Option[Span] = None
  ) extends IRNode

  /** A conditional node for if/else expressions */
  final case class ConditionalNode(
    id: UUID,
    condition: UUID,
    thenBranch: UUID,
    elseBranch: UUID,
    outputType: SemanticType,
    debugSpan: Option[Span] = None
  ) extends IRNode

  /** A literal value node */
  final case class LiteralNode(
    id: UUID,
    value: Any,
    outputType: SemanticType,
    debugSpan: Option[Span] = None
  ) extends IRNode
}

/** The complete IR program representing a constellation-lang program */
final case class IRProgram(
  nodes: Map[UUID, IRNode],
  inputs: List[UUID],                   // IDs of input nodes (for top-level data)
  declaredOutputs: List[String],        // Names of declared output variables
  variableBindings: Map[String, UUID]   // Variable name -> IR node ID (for resolving outputs)
) {

  /** Get all dependencies for a given node */
  def dependencies(nodeId: UUID): Set[UUID] = nodes.get(nodeId) match {
    case Some(IRNode.Input(_, _, _, _)) => Set.empty
    case Some(IRNode.ModuleCall(_, _, _, inputs, _, _)) => inputs.values.toSet
    case Some(IRNode.MergeNode(_, left, right, _, _)) => Set(left, right)
    case Some(IRNode.ProjectNode(_, source, _, _, _)) => Set(source)
    case Some(IRNode.FieldAccessNode(_, source, _, _, _)) => Set(source)
    case Some(IRNode.ConditionalNode(_, cond, thenBr, elseBr, _, _)) => Set(cond, thenBr, elseBr)
    case Some(IRNode.LiteralNode(_, _, _, _)) => Set.empty
    case None => Set.empty
  }

  /** Topologically sort nodes for execution order */
  def topologicalOrder: List[UUID] = {
    var visited = Set.empty[UUID]
    var result = List.empty[UUID]

    def visit(nodeId: UUID): Unit = {
      if (!visited.contains(nodeId)) {
        visited = visited + nodeId
        dependencies(nodeId).foreach(visit)
        result = nodeId :: result
      }
    }

    nodes.keys.foreach(visit)
    result.reverse
  }
}
