package io.constellation.lang.optimizer

import java.util.UUID

import io.constellation.lang.compiler.{IRNode, IRPipeline}

/** Base trait for IR optimization passes.
  *
  * Each pass transforms an IRPipeline to a potentially optimized version. Passes should be pure
  * functions - they should not modify their input.
  */
trait OptimizationPass {

  /** Unique name identifying this optimization pass */
  def name: String

  /** Apply this optimization pass to the IR program.
    *
    * @param ir
    *   The IR program to optimize
    * @return
    *   The optimized IR program (may be unchanged if no optimizations apply)
    */
  def run(ir: IRPipeline): IRPipeline

  /** Helper to transform all nodes in the IR program.
    *
    * @param ir
    *   The IR program
    * @param f
    *   The transformation function to apply to each node
    * @return
    *   A new IR program with transformed nodes
    */
  protected def transformNodes(ir: IRPipeline)(f: IRNode => IRNode): IRPipeline =
    ir.copy(nodes = ir.nodes.map { case (id, node) => id -> f(node) })

  /** Helper to filter nodes from the IR program.
    *
    * @param ir
    *   The IR program
    * @param p
    *   The predicate - nodes where p returns true are kept
    * @return
    *   A new IR program with only nodes matching the predicate
    */
  protected def filterNodes(ir: IRPipeline)(p: (UUID, IRNode) => Boolean): IRPipeline =
    ir.copy(nodes = ir.nodes.filter { case (id, node) => p(id, node) })

  /** Helper to replace node references throughout the IR program.
    *
    * @param ir
    *   The IR program
    * @param replacements
    *   A map from old node IDs to new node IDs
    * @return
    *   A new IR program with references updated
    */
  protected def replaceReferences(ir: IRPipeline, replacements: Map[UUID, UUID]): IRPipeline = {
    def replace(id: UUID): UUID = replacements.getOrElse(id, id)

    def replaceInNode(node: IRNode): IRNode = node match {
      case n: IRNode.Input       => n
      case n: IRNode.LiteralNode => n
      case n: IRNode.ModuleCall =>
        n.copy(inputs = n.inputs.map { case (k, v) => k -> replace(v) })
      case n: IRNode.MergeNode =>
        n.copy(left = replace(n.left), right = replace(n.right))
      case n: IRNode.ProjectNode =>
        n.copy(source = replace(n.source))
      case n: IRNode.FieldAccessNode =>
        n.copy(source = replace(n.source))
      case n: IRNode.ConditionalNode =>
        n.copy(
          condition = replace(n.condition),
          thenBranch = replace(n.thenBranch),
          elseBranch = replace(n.elseBranch)
        )
      case n: IRNode.AndNode =>
        n.copy(left = replace(n.left), right = replace(n.right))
      case n: IRNode.OrNode =>
        n.copy(left = replace(n.left), right = replace(n.right))
      case n: IRNode.NotNode =>
        n.copy(operand = replace(n.operand))
      case n: IRNode.GuardNode =>
        n.copy(expr = replace(n.expr), condition = replace(n.condition))
      case n: IRNode.CoalesceNode =>
        n.copy(left = replace(n.left), right = replace(n.right))
      case n: IRNode.BranchNode =>
        n.copy(
          cases = n.cases.map { case (cond, expr) => (replace(cond), replace(expr)) },
          otherwise = replace(n.otherwise)
        )
      case n: IRNode.StringInterpolationNode =>
        n.copy(expressions = n.expressions.map(replace))
      case n: IRNode.HigherOrderNode =>
        n.copy(source = replace(n.source))
      case n: IRNode.ListLiteralNode =>
        n.copy(elements = n.elements.map(replace))
    }

    val newNodes    = ir.nodes.map { case (id, node) => id -> replaceInNode(node) }
    val newBindings = ir.variableBindings.map { case (name, id) => name -> replace(id) }

    ir.copy(nodes = newNodes, variableBindings = newBindings)
  }
}
