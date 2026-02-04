package io.constellation.lang.optimizer

import io.constellation.lang.compiler.{IRNode, IRPipeline}
import io.constellation.lang.semantic.SemanticType

import java.util.UUID
import scala.collection.mutable

/** Constant Folding optimization pass.
  *
  * Evaluates constant expressions at compile time. Supports arithmetic operations, string
  * concatenation, and boolean operations.
  */
object ConstantFolding extends OptimizationPass {

  val name: String = "constant-folding"

  def run(ir: IRPipeline): IRPipeline = {
    // Map of node ID -> folded constant value (if foldable)
    val folded = mutable.Map[UUID, FoldedValue]()

    // Process nodes in topological order to ensure dependencies are processed first
    val sortedNodes = ir.topologicalOrder

    // First pass: identify nodes that can be folded
    for nodeId <- sortedNodes do
      ir.nodes.get(nodeId).foreach { node =>
        tryFold(node, folded.toMap, ir).foreach { value =>
          folded(nodeId) = value
        }
      }

    // Second pass: replace foldable nodes with literals
    val newNodes = ir.nodes.map { case (id, node) =>
      folded.get(id) match {
        case Some(value) =>
          id -> IRNode.LiteralNode(id, value.value, value.semanticType, node.debugSpan)
        case None =>
          id -> node
      }
    }

    ir.copy(nodes = newNodes)
  }

  /** Internal representation of a folded constant value */
  private case class FoldedValue(value: Any, semanticType: SemanticType)

  /** Try to fold a node to a constant value.
    *
    * @return
    *   Some(FoldedValue) if the node can be folded, None otherwise
    */
  private def tryFold(
      node: IRNode,
      folded: Map[UUID, FoldedValue],
      ir: IRPipeline
  ): Option[FoldedValue] = node match {

    // Literals are already constants
    case IRNode.LiteralNode(_, value, outputType, _) =>
      Some(FoldedValue(value, outputType))

    // Module calls can be folded if all inputs are constant and operation is foldable
    case IRNode.ModuleCall(_, moduleName, _, inputs, outputType, _, _) =>
      foldModuleCall(moduleName, inputs, folded, outputType)

    // AND with constant operands
    case IRNode.AndNode(_, left, right, _) =>
      for {
        leftVal  <- folded.get(left)
        rightVal <- folded.get(right)
        if leftVal.value.isInstanceOf[Boolean] && rightVal.value.isInstanceOf[Boolean]
      } yield {
        val result = leftVal.value.asInstanceOf[Boolean] && rightVal.value.asInstanceOf[Boolean]
        FoldedValue(result, SemanticType.SBoolean)
      }

    // OR with constant operands
    case IRNode.OrNode(_, left, right, _) =>
      for {
        leftVal  <- folded.get(left)
        rightVal <- folded.get(right)
        if leftVal.value.isInstanceOf[Boolean] && rightVal.value.isInstanceOf[Boolean]
      } yield {
        val result = leftVal.value.asInstanceOf[Boolean] || rightVal.value.asInstanceOf[Boolean]
        FoldedValue(result, SemanticType.SBoolean)
      }

    // NOT with constant operand
    case IRNode.NotNode(_, operand, _) =>
      for {
        operandVal <- folded.get(operand)
        if operandVal.value.isInstanceOf[Boolean]
      } yield {
        val result = !operandVal.value.asInstanceOf[Boolean]
        FoldedValue(result, SemanticType.SBoolean)
      }

    // Conditional with constant condition
    case IRNode.ConditionalNode(_, condition, thenBranch, elseBranch, outputType, _) =>
      folded.get(condition).flatMap { condVal =>
        if condVal.value.isInstanceOf[Boolean] then {
          val result = if condVal.value.asInstanceOf[Boolean] then thenBranch else elseBranch
          folded.get(result).map(v => FoldedValue(v.value, outputType))
        } else None
      }

    // String interpolation with all constant expressions
    case IRNode.StringInterpolationNode(_, parts, expressions, _) =>
      if expressions.forall(folded.contains) then {
        val exprValues = expressions.map(id => folded(id).value.toString)
        val result     = parts.zipAll(exprValues, "", "").map { case (p, e) => p + e }.mkString
        Some(FoldedValue(result, SemanticType.SString))
      } else None

    // Other nodes cannot be folded
    case _ => None
  }

  /** Try to fold a module call to a constant value */
  private def foldModuleCall(
      moduleName: String,
      inputs: Map[String, UUID],
      folded: Map[UUID, FoldedValue],
      outputType: SemanticType
  ): Option[FoldedValue] = {
    // Check if all inputs are constants
    val allConstant = inputs.values.forall(folded.contains)
    if !allConstant then return None

    // Get input values
    def getInt(name: String): Option[Int] =
      inputs.get(name).flatMap(folded.get).flatMap { v =>
        v.value match {
          case i: Int     => Some(i)
          case l: Long    => Some(l.toInt)
          case d: Double  => Some(d.toInt)
          case f: Float   => Some(f.toInt)
          case bi: BigInt => Some(bi.toInt)
          case _          => None
        }
      }

    def getDouble(name: String): Option[Double] =
      inputs.get(name).flatMap(folded.get).flatMap { v =>
        v.value match {
          case i: Int    => Some(i.toDouble)
          case l: Long   => Some(l.toDouble)
          case d: Double => Some(d)
          case f: Float  => Some(f.toDouble)
          case _         => None
        }
      }

    def getString(name: String): Option[String] =
      inputs.get(name).flatMap(folded.get).map(_.value.toString)

    def getBool(name: String): Option[Boolean] =
      inputs.get(name).flatMap(folded.get).flatMap { v =>
        v.value match {
          case b: Boolean => Some(b)
          case _          => None
        }
      }

    // Match on module name and fold if possible
    moduleName match {
      // Integer arithmetic
      case "stdlib.math.add" | "add" =>
        for {
          a <- getInt("a")
          b <- getInt("b")
        } yield FoldedValue(a + b, SemanticType.SInt)

      case "stdlib.math.subtract" | "subtract" =>
        for {
          a <- getInt("a")
          b <- getInt("b")
        } yield FoldedValue(a - b, SemanticType.SInt)

      case "stdlib.math.multiply" | "multiply" =>
        for {
          a <- getInt("a")
          b <- getInt("b")
        } yield FoldedValue(a * b, SemanticType.SInt)

      case "stdlib.math.divide" | "divide" =>
        for {
          a <- getInt("a")
          b <- getInt("b")
          if b != 0 // Don't fold division by zero
        } yield FoldedValue(a / b, SemanticType.SInt)

      case "stdlib.math.modulo" | "modulo" =>
        for {
          a <- getInt("a")
          b <- getInt("b")
          if b != 0
        } yield FoldedValue(a % b, SemanticType.SInt)

      // Float arithmetic
      case "stdlib.math.addFloat" | "addFloat" =>
        for {
          a <- getDouble("a")
          b <- getDouble("b")
        } yield FoldedValue(a + b, SemanticType.SFloat)

      case "stdlib.math.subtractFloat" | "subtractFloat" =>
        for {
          a <- getDouble("a")
          b <- getDouble("b")
        } yield FoldedValue(a - b, SemanticType.SFloat)

      case "stdlib.math.multiplyFloat" | "multiplyFloat" =>
        for {
          a <- getDouble("a")
          b <- getDouble("b")
        } yield FoldedValue(a * b, SemanticType.SFloat)

      case "stdlib.math.divideFloat" | "divideFloat" =>
        for {
          a <- getDouble("a")
          b <- getDouble("b")
          if b != 0.0
        } yield FoldedValue(a / b, SemanticType.SFloat)

      // String operations
      case "stdlib.string.concat" | "concat" =>
        for {
          a <- getString("a")
          b <- getString("b")
        } yield FoldedValue(a + b, SemanticType.SString)

      case "stdlib.string.length" | "length" =>
        for {
          s <- getString("s").orElse(getString("text"))
        } yield FoldedValue(s.length, SemanticType.SInt)

      case "stdlib.string.toUpperCase" | "toUpperCase" | "uppercase" =>
        for {
          s <- getString("s").orElse(getString("text"))
        } yield FoldedValue(s.toUpperCase, SemanticType.SString)

      case "stdlib.string.toLowerCase" | "toLowerCase" | "lowercase" =>
        for {
          s <- getString("s").orElse(getString("text"))
        } yield FoldedValue(s.toLowerCase, SemanticType.SString)

      // Comparison operations
      case "stdlib.compare.equals" | "equals" | "eq" =>
        for {
          a <- inputs.get("a").flatMap(folded.get)
          b <- inputs.get("b").flatMap(folded.get)
        } yield FoldedValue(a.value == b.value, SemanticType.SBoolean)

      case "stdlib.compare.greaterThan" | "greaterThan" | "gt" =>
        for {
          a <- getInt("a")
          b <- getInt("b")
        } yield FoldedValue(a > b, SemanticType.SBoolean)

      case "stdlib.compare.lessThan" | "lessThan" | "lt" =>
        for {
          a <- getInt("a")
          b <- getInt("b")
        } yield FoldedValue(a < b, SemanticType.SBoolean)

      case "stdlib.compare.greaterThanOrEqual" | "greaterThanOrEqual" | "gte" =>
        for {
          a <- getInt("a")
          b <- getInt("b")
        } yield FoldedValue(a >= b, SemanticType.SBoolean)

      case "stdlib.compare.lessThanOrEqual" | "lessThanOrEqual" | "lte" =>
        for {
          a <- getInt("a")
          b <- getInt("b")
        } yield FoldedValue(a <= b, SemanticType.SBoolean)

      // Boolean operations
      case "stdlib.bool.not" | "not" =>
        for {
          a <- getBool("a").orElse(getBool("value"))
        } yield FoldedValue(!a, SemanticType.SBoolean)

      // Unknown module - cannot fold
      case _ => None
    }
  }
}
