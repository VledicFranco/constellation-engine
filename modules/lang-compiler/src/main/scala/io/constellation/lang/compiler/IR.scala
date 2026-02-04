package io.constellation.lang.compiler

import io.constellation.ModuleCallOptions
import io.constellation.lang.ast.{BackoffStrategy, ErrorStrategy, PriorityLevel, Span}
import io.constellation.lang.semantic.SemanticType

import java.util.UUID

/** Module call options at IR level.
  *
  * These are the resolved options from the AST, with fallback expressions compiled into IR nodes.
  * Options like retry, timeout, etc. are passed through to the runtime for execution.
  */
final case class IRModuleCallOptions(
    retry: Option[Int] = None,
    timeoutMs: Option[Long] = None,
    delayMs: Option[Long] = None,
    backoff: Option[BackoffStrategy] = None,
    fallback: Option[UUID] = None, // IR node ID for fallback expression
    cacheMs: Option[Long] = None,
    cacheBackend: Option[String] = None,
    throttleCount: Option[Int] = None,
    throttlePerMs: Option[Long] = None,
    concurrency: Option[Int] = None,
    onError: Option[ErrorStrategy] = None,
    lazyEval: Option[Boolean] = None,
    priority: Option[Int] = None // Normalized to Int (critical=100, high=80, etc.)
) {
  def isEmpty: Boolean =
    retry.isEmpty && timeoutMs.isEmpty && delayMs.isEmpty && backoff.isEmpty &&
      fallback.isEmpty && cacheMs.isEmpty && cacheBackend.isEmpty &&
      throttleCount.isEmpty && throttlePerMs.isEmpty && concurrency.isEmpty &&
      onError.isEmpty && lazyEval.isEmpty && priority.isEmpty

  /** Convert to runtime-level [[ModuleCallOptions]] (drops IR-specific `fallback`). */
  def toModuleCallOptions: ModuleCallOptions = ModuleCallOptions(
    retry = retry,
    timeoutMs = timeoutMs,
    delayMs = delayMs,
    backoff = backoff.map {
      case BackoffStrategy.Fixed       => "fixed"
      case BackoffStrategy.Linear      => "linear"
      case BackoffStrategy.Exponential => "exponential"
    },
    cacheMs = cacheMs,
    cacheBackend = cacheBackend,
    throttleCount = throttleCount,
    throttlePerMs = throttlePerMs,
    concurrency = concurrency,
    onError = onError.map {
      case ErrorStrategy.Propagate => "propagate"
      case ErrorStrategy.Skip      => "skip"
      case ErrorStrategy.Log       => "log"
      case ErrorStrategy.Wrap      => "wrap"
    },
    lazyEval = lazyEval,
    priority = priority
  )
}

object IRModuleCallOptions {
  val empty: IRModuleCallOptions = IRModuleCallOptions()
}

/** Intermediate representation for constellation-lang pipelines.
  *
  * The IR represents a data-flow graph where:
  *   - Each node produces a value with a known type
  *   - Dependencies between nodes are explicit via UUID references
  *   - This IR is designed to be easily compiled to Constellation DagSpec
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
      moduleName: String,        // The registered module name in Constellation
      languageName: String,      // The name used in constellation-lang
      inputs: Map[String, UUID], // Parameter name -> input node ID
      outputType: SemanticType,
      options: IRModuleCallOptions = IRModuleCallOptions.empty,
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

  /** Boolean AND node with short-circuit evaluation. If left is false, right is not evaluated.
    */
  final case class AndNode(
      id: UUID,
      left: UUID,
      right: UUID,
      debugSpan: Option[Span] = None
  ) extends IRNode {
    def outputType: SemanticType = SemanticType.SBoolean
  }

  /** Boolean OR node with short-circuit evaluation. If left is true, right is not evaluated.
    */
  final case class OrNode(
      id: UUID,
      left: UUID,
      right: UUID,
      debugSpan: Option[Span] = None
  ) extends IRNode {
    def outputType: SemanticType = SemanticType.SBoolean
  }

  /** Boolean NOT node */
  final case class NotNode(
      id: UUID,
      operand: UUID,
      debugSpan: Option[Span] = None
  ) extends IRNode {
    def outputType: SemanticType = SemanticType.SBoolean
  }

  /** Guard node for conditional execution. Returns Optional<T> where T is the inner expression
    * type. If condition is true, returns Some(expr), else returns None.
    */
  final case class GuardNode(
      id: UUID,
      expr: UUID,
      condition: UUID,
      innerType: SemanticType,
      debugSpan: Option[Span] = None
  ) extends IRNode {
    def outputType: SemanticType = SemanticType.SOptional(innerType)
  }

  /** Coalesce node for null-coalescing operation. If left (Optional) is Some(v), returns v. If
    * None, returns right. Short-circuits: right is not evaluated if left is Some.
    */
  final case class CoalesceNode(
      id: UUID,
      left: UUID,
      right: UUID,
      resultType: SemanticType,
      debugSpan: Option[Span] = None
  ) extends IRNode {
    def outputType: SemanticType = resultType
  }

  /** Branch node for multi-way conditional. Evaluates conditions in order, returns first matching
    * expression. If no condition matches, returns the otherwise expression.
    */
  final case class BranchNode(
      id: UUID,
      cases: List[(UUID, UUID)], // (condition ID, expression ID) pairs
      otherwise: UUID,
      resultType: SemanticType,
      debugSpan: Option[Span] = None
  ) extends IRNode {
    def outputType: SemanticType = resultType
  }

  /** String interpolation node. Combines static string parts with evaluated expressions.
    * parts.length == expressions.length + 1 Example: "Hello, ${name}!" has parts ["Hello, ", "!"]
    * and one expression
    */
  final case class StringInterpolationNode(
      id: UUID,
      parts: List[String],
      expressions: List[UUID],
      debugSpan: Option[Span] = None
  ) extends IRNode {
    def outputType: SemanticType = SemanticType.SString
  }

  /** Higher-order function operation (filter, map, all, any, etc.) Applies a lambda expression to
    * each element of a collection.
    */
  final case class HigherOrderNode(
      id: UUID,
      operation: HigherOrderOp,
      source: UUID,        // Source collection node ID
      lambda: TypedLambda, // The lambda to apply
      outputType: SemanticType,
      debugSpan: Option[Span] = None
  ) extends IRNode

  /** A list literal node containing multiple element expressions */
  final case class ListLiteralNode(
      id: UUID,
      elements: List[UUID],
      elementType: SemanticType,
      debugSpan: Option[Span] = None
  ) extends IRNode {
    def outputType: SemanticType = SemanticType.SList(elementType)
  }
}

/** Higher-order operation types */
enum HigherOrderOp:
  case Filter // Filter elements by predicate
  case Map    // Transform each element
  case All    // Check if all elements satisfy predicate
  case Any    // Check if any element satisfies predicate
  case SortBy // Sort by key extractor

/** Typed lambda representation for IR (independent of TypedExpression) Contains IR nodes
  * representing the lambda body that can be evaluated per element during collection operations.
  */
final case class TypedLambda(
    paramNames: List[String],
    paramTypes: List[SemanticType],
    bodyNodes: Map[UUID, IRNode], // IR nodes for lambda body
    bodyOutputId: UUID,           // Output node ID of the body
    returnType: SemanticType
)

/** The complete IR representation of a constellation-lang pipeline */
final case class IRPipeline(
    nodes: Map[UUID, IRNode],
    inputs: List[UUID],                 // IDs of input nodes (for top-level data)
    declaredOutputs: List[String],      // Names of declared output variables
    variableBindings: Map[String, UUID] // Variable name -> IR node ID (for resolving outputs)
) {

  /** Get all dependencies for a given node */
  def dependencies(nodeId: UUID): Set[UUID] = nodes.get(nodeId) match {
    case Some(IRNode.Input(_, _, _, _)) => Set.empty
    case Some(IRNode.ModuleCall(_, _, _, inputs, _, options, _)) =>
      inputs.values.toSet ++ options.fallback.toSet
    case Some(IRNode.MergeNode(_, left, right, _, _))                => Set(left, right)
    case Some(IRNode.ProjectNode(_, source, _, _, _))                => Set(source)
    case Some(IRNode.FieldAccessNode(_, source, _, _, _))            => Set(source)
    case Some(IRNode.ConditionalNode(_, cond, thenBr, elseBr, _, _)) => Set(cond, thenBr, elseBr)
    case Some(IRNode.LiteralNode(_, _, _, _))                        => Set.empty
    case Some(IRNode.AndNode(_, left, right, _))                     => Set(left, right)
    case Some(IRNode.OrNode(_, left, right, _))                      => Set(left, right)
    case Some(IRNode.NotNode(_, operand, _))                         => Set(operand)
    case Some(IRNode.GuardNode(_, expr, condition, _, _))            => Set(expr, condition)
    case Some(IRNode.CoalesceNode(_, left, right, _, _))             => Set(left, right)
    case Some(IRNode.BranchNode(_, cases, otherwise, _, _)) =>
      cases.flatMap { case (cond, expr) => Set(cond, expr) }.toSet + otherwise
    case Some(IRNode.StringInterpolationNode(_, _, expressions, _)) =>
      expressions.toSet
    case Some(IRNode.HigherOrderNode(_, _, source, _, _, _)) =>
      Set(source) // Lambda body nodes are evaluated separately per element
    case Some(IRNode.ListLiteralNode(_, elements, _, _)) => elements.toSet
    case None                                            => Set.empty
  }

  /** Topologically sort nodes for execution order */
  def topologicalOrder: List[UUID] = {
    var visited = Set.empty[UUID]
    var result  = List.empty[UUID]

    def visit(nodeId: UUID): Unit =
      if !visited.contains(nodeId) then {
        visited = visited + nodeId
        dependencies(nodeId).foreach(visit)
        result = nodeId :: result
      }

    nodes.keys.foreach(visit)
    result.reverse
  }

  /** Compute topological layers for parallel processing.
    *
    * Nodes in the same layer have no dependencies on each other and can be processed in parallel.
    * Layer 0 contains nodes with no dependencies, Layer N contains nodes whose dependencies are all
    * in layers 0..N-1.
    *
    * @return
    *   List of layers, where each layer is a set of node IDs
    */
  def topologicalLayers: List[Set[UUID]] = {
    import scala.collection.mutable

    // Build reverse dependency map (node -> nodes that depend on it)
    val dependents: mutable.Map[UUID, mutable.Set[UUID]] = mutable.Map.empty
    nodes.keys.foreach(id => dependents(id) = mutable.Set.empty[UUID])

    // Compute in-degree for each node
    val inDegree: mutable.Map[UUID, Int] = mutable.Map.empty.withDefaultValue(0)
    nodes.keys.foreach { nodeId =>
      val deps = dependencies(nodeId)
      inDegree(nodeId) = deps.size
      deps.foreach { depId =>
        if !dependents.contains(depId) then {
          dependents(depId) = mutable.Set.empty[UUID]
        }
        dependents(depId) += nodeId
      }
    }

    val layers    = mutable.ListBuffer[Set[UUID]]()
    var remaining = nodes.keySet

    while remaining.nonEmpty do {
      // Current layer: all nodes with in-degree 0
      val currentLayer = remaining.filter(n => inDegree(n) == 0)

      if currentLayer.isEmpty && remaining.nonEmpty then {
        // Cycle detected - this should not happen if earlier validation is correct
        throw new IllegalStateException(
          s"Cycle detected in DAG during topological sort. Remaining node count: ${remaining.size}"
        )
      } else {
        layers += currentLayer

        // Decrease in-degree for dependents
        currentLayer.foreach { nodeId =>
          dependents.get(nodeId).foreach { deps =>
            deps.foreach { dependent =>
              inDegree(dependent) = inDegree(dependent) - 1
            }
          }
        }

        remaining = remaining -- currentLayer
      }
    }

    layers.toList
  }

  /** Get the maximum parallelism potential (size of largest layer) */
  def maxParallelism: Int = {
    val layers = topologicalLayers
    if layers.isEmpty then 0 else layers.map(_.size).max
  }

  /** Get the critical path length (number of layers) */
  def criticalPathLength: Int = topologicalLayers.size
}
