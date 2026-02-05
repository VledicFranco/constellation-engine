package io.constellation.lang.optimizer

import java.util.UUID

import scala.collection.mutable

import io.constellation.lang.compiler.{IRNode, IRPipeline, TypedLambda}

/** Common Subexpression Elimination optimization pass.
  *
  * Deduplicates identical computations by detecting nodes with the same semantic signature and
  * replacing duplicates with references to a single canonical node.
  */
object CommonSubexpressionElimination extends OptimizationPass {

  val name: String = "common-subexpression-elimination"

  def run(ir: IRPipeline): IRPipeline = {
    // Compute signature for each node
    val signatures = ir.nodes.map { case (id, node) =>
      id -> computeSignature(node)
    }

    // Group nodes by signature (only consider nodes with non-empty signatures)
    val groups = signatures
      .filter(_._2.nonEmpty)
      .groupBy(_._2)
      .filter(_._2.size > 1) // Only keep groups with duplicates

    // For each group, keep the first (canonical) node and mark others for replacement
    val replacements = mutable.Map[UUID, UUID]()
    for (_, nodeIds) <- groups do {
      val sorted    = nodeIds.toList.sortBy(_._1.toString) // Deterministic ordering
      val canonical = sorted.head._1
      for (id, _) <- sorted.tail do replacements(id) = canonical
    }

    if replacements.isEmpty then {
      // No duplicates found
      return ir
    }

    // Apply replacements
    val updatedIR = replaceReferences(ir, replacements.toMap)

    // Remove replaced nodes
    val filteredNodes = updatedIR.nodes.filterNot { case (id, _) =>
      replacements.contains(id)
    }

    updatedIR.copy(nodes = filteredNodes)
  }

  /** Compute a semantic signature for a node.
    *
    * Two nodes with the same signature compute the same value. Returns empty string for nodes that
    * should not be deduplicated (inputs, literals with unique values, etc.)
    */
  private def computeSignature(node: IRNode): String = node match {
    // Inputs are unique by name
    case IRNode.Input(_, _, _, _) => ""

    // Literals are unique by value
    case IRNode.LiteralNode(_, _, _, _) => ""

    // Module calls are identified by module name and input mapping
    case IRNode.ModuleCall(_, moduleName, languageName, inputs, outputType, _, _) =>
      val inputSig = inputs.toSeq.sortBy(_._1).map { case (k, v) => s"$k=$v" }.mkString(",")
      s"call:$moduleName:$languageName:$inputSig:${outputType.prettyPrint}"

    // Merge nodes
    case IRNode.MergeNode(_, left, right, outputType, _) =>
      s"merge:$left:$right:${outputType.prettyPrint}"

    // Project nodes
    case IRNode.ProjectNode(_, source, fields, outputType, _) =>
      s"project:$source:${fields.mkString(",")}:${outputType.prettyPrint}"

    // Field access nodes
    case IRNode.FieldAccessNode(_, source, field, outputType, _) =>
      s"field:$source:$field:${outputType.prettyPrint}"

    // Conditional nodes
    case IRNode.ConditionalNode(_, condition, thenBranch, elseBranch, outputType, _) =>
      s"cond:$condition:$thenBranch:$elseBranch:${outputType.prettyPrint}"

    // Boolean operations
    case IRNode.AndNode(_, left, right, _) =>
      s"and:$left:$right"

    case IRNode.OrNode(_, left, right, _) =>
      s"or:$left:$right"

    case IRNode.NotNode(_, operand, _) =>
      s"not:$operand"

    // Guard nodes
    case IRNode.GuardNode(_, expr, condition, innerType, _) =>
      s"guard:$expr:$condition:${innerType.prettyPrint}"

    // Coalesce nodes
    case IRNode.CoalesceNode(_, left, right, resultType, _) =>
      s"coalesce:$left:$right:${resultType.prettyPrint}"

    // Branch nodes
    case IRNode.BranchNode(_, cases, otherwise, resultType, _) =>
      val casesSig = cases.map { case (c, e) => s"$c->$e" }.mkString(",")
      s"branch:$casesSig:$otherwise:${resultType.prettyPrint}"

    // String interpolation
    case IRNode.StringInterpolationNode(_, parts, expressions, _) =>
      val exprSig = expressions.mkString(",")
      s"interp:${parts.mkString("|")}:$exprSig"

    // Higher-order nodes - don't deduplicate (lambda bodies are complex)
    case IRNode.HigherOrderNode(_, _, _, _, _, _) => ""

    // List literals
    case IRNode.ListLiteralNode(_, elements, elementType, _) =>
      s"list:${elements.mkString(",")}:${elementType.prettyPrint}"
  }
}
