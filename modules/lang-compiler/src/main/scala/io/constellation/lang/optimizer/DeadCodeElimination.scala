package io.constellation.lang.optimizer

import io.constellation.lang.compiler.{IRNode, IRProgram}

import java.util.UUID
import scala.collection.mutable

/** Dead Code Elimination optimization pass.
  *
  * Removes nodes that are not reachable from any output. Uses backward DFS traversal from output
  * nodes to mark reachable nodes.
  */
object DeadCodeElimination extends OptimizationPass {

  val name: String = "dead-code-elimination"

  def run(ir: IRProgram): IRProgram = {
    // Find all output node IDs from declared outputs
    val outputNodeIds = ir.declaredOutputs.flatMap { outputName =>
      ir.variableBindings.get(outputName)
    }

    // If no outputs declared, nothing to eliminate
    if outputNodeIds.isEmpty then {
      return ir
    }

    // Compute all reachable nodes from outputs via backward traversal
    val reachable = computeReachable(outputNodeIds, ir)

    // Filter nodes to keep only reachable ones
    val filteredNodes = ir.nodes.filter { case (id, _) => reachable.contains(id) }

    // Filter variable bindings to keep only those pointing to reachable nodes
    val filteredBindings = ir.variableBindings.filter { case (_, id) =>
      reachable.contains(id)
    }

    // Filter inputs to keep only reachable ones
    val filteredInputs = ir.inputs.filter(reachable.contains)

    ir.copy(
      nodes = filteredNodes,
      inputs = filteredInputs,
      variableBindings = filteredBindings
    )
  }

  /** Compute set of all node IDs reachable from the given output nodes.
    *
    * Uses backward DFS traversal following dependencies.
    */
  private def computeReachable(outputNodeIds: List[UUID], ir: IRProgram): Set[UUID] = {
    val visited = mutable.Set[UUID]()

    def visit(id: UUID): Unit =
      if !visited.contains(id) then {
        visited += id
        // Get dependencies for this node and visit them
        ir.dependencies(id).foreach(visit)
        // Also visit lambda body nodes for higher-order nodes
        ir.nodes.get(id).foreach {
          case IRNode.HigherOrderNode(_, _, _, lambda, _, _) =>
            lambda.bodyNodes.keys.foreach { lambdaNodeId =>
              // Only mark the lambda nodes themselves as reachable
              // (they're stored separately but we should track them)
              visited += lambdaNodeId
            }
          case _ => ()
        }
      }

    outputNodeIds.foreach(visit)
    visited.toSet
  }
}
