package io.constellation.lang.viz

import io.constellation.lang.compiler.{IRNode, IRProgram, HigherOrderOp}
import io.constellation.lang.semantic.SemanticType

import java.util.UUID

/** Compiles an IRProgram into a DagVizIR for visualization */
object DagVizCompiler:

  /** Compile an IR program to visualization IR
    *
    * @param ir The compiled IR program
    * @param title Optional title for the visualization
    * @return DagVizIR ready for layout
    */
  def compile(ir: IRProgram, title: Option[String] = None): DagVizIR =
    val nodeMap = ir.nodes

    // Step 1: Convert all IR nodes to VizNodes (keep original kind/label)
    val vizNodes = nodeMap.map { case (id, node) =>
      irNodeToVizNode(id, node)
    }.toList

    // Step 2: Create explicit Output nodes for declared outputs
    val outputNodes = ir.declaredOutputs.flatMap { name =>
      ir.variableBindings.get(name).map { sourceId =>
        val outputId = s"output_$name"
        val sourceNode = nodeMap.get(sourceId)
        val typeSignature = sourceNode.map(n => formatType(getNodeOutputType(n))).getOrElse("Unknown")
        VizNode(
          id = outputId,
          kind = NodeKind.Output,
          label = name,
          typeSignature = typeSignature
        )
      }
    }

    // Step 3: Build edges from IR dependencies
    val vizEdges = buildEdges(ir)

    // Step 4: Create edges from source nodes to output nodes
    var outputEdgeId = vizEdges.length
    val outputEdges = ir.declaredOutputs.flatMap { name =>
      ir.variableBindings.get(name).map { sourceId =>
        outputEdgeId += 1
        VizEdge(
          id = s"e$outputEdgeId",
          source = sourceId.toString,
          target = s"output_$name",
          label = Some("value"),
          kind = EdgeKind.Data
        )
      }
    }

    // Combine everything
    val allNodes = vizNodes ++ outputNodes
    val allEdges = vizEdges ++ outputEdges

    DagVizIR(
      nodes = allNodes,
      edges = allEdges,
      groups = List.empty, // Groups computed later if needed
      metadata = VizMetadata(title = title)
    )

  /** Convert an IR node to a visualization node */
  private def irNodeToVizNode(id: UUID, node: IRNode): VizNode =
    node match {
      case IRNode.Input(_, name, outputType, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.Input,
          label = name,
          typeSignature = formatType(outputType)
        )

      case IRNode.ModuleCall(_, moduleName, languageName, inputs, outputType, _, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.Operation,
          label = languageName,
          typeSignature = formatType(outputType)
        )

      case IRNode.MergeNode(_, _, _, outputType, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.Merge,
          label = "+",
          typeSignature = formatType(outputType)
        )

      case IRNode.ProjectNode(_, _, fields, outputType, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.Project,
          label = s"[${fields.take(3).mkString(", ")}${if fields.length > 3 then ", ..." else ""}]",
          typeSignature = formatType(outputType)
        )

      case IRNode.FieldAccessNode(_, _, field, outputType, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.FieldAccess,
          label = s".$field",
          typeSignature = formatType(outputType)
        )

      case IRNode.ConditionalNode(_, _, _, _, outputType, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.Conditional,
          label = "if/else",
          typeSignature = formatType(outputType)
        )

      case IRNode.LiteralNode(_, value, outputType, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.Literal,
          label = formatLiteralValue(value),
          typeSignature = formatType(outputType)
        )

      case IRNode.AndNode(_, _, _, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.BooleanOp,
          label = "AND",
          typeSignature = "Boolean"
        )

      case IRNode.OrNode(_, _, _, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.BooleanOp,
          label = "OR",
          typeSignature = "Boolean"
        )

      case IRNode.NotNode(_, _, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.BooleanOp,
          label = "NOT",
          typeSignature = "Boolean"
        )

      case IRNode.GuardNode(_, _, _, innerType, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.Guard,
          label = "when",
          typeSignature = s"Optional<${formatType(innerType)}>"
        )

      case IRNode.CoalesceNode(_, _, _, resultType, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.Coalesce,
          label = "??",
          typeSignature = formatType(resultType)
        )

      case IRNode.BranchNode(_, cases, _, resultType, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.Branch,
          label = s"branch (${cases.length} cases)",
          typeSignature = formatType(resultType)
        )

      case IRNode.StringInterpolationNode(_, _, _, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.StringInterp,
          label = "interpolate",
          typeSignature = "String"
        )

      case IRNode.HigherOrderNode(_, operation, _, _, outputType, _) =>
        val opName = operation match {
          case HigherOrderOp.Filter => "filter"
          case HigherOrderOp.Map    => "map"
          case HigherOrderOp.All    => "all"
          case HigherOrderOp.Any    => "any"
          case HigherOrderOp.SortBy => "sortBy"
        }
        VizNode(
          id = id.toString,
          kind = NodeKind.HigherOrder,
          label = opName,
          typeSignature = formatType(outputType)
        )

      case IRNode.ListLiteralNode(_, elements, elementType, _) =>
        VizNode(
          id = id.toString,
          kind = NodeKind.ListLiteral,
          label = s"[${elements.length} items]",
          typeSignature = s"List<${formatType(elementType)}>"
        )
    }

  /** Extract the output type from an IR node */
  private def getNodeOutputType(node: IRNode): SemanticType =
    node match {
      case IRNode.Input(_, _, outputType, _)                   => outputType
      case IRNode.ModuleCall(_, _, _, _, outputType, _, _)      => outputType
      case IRNode.MergeNode(_, _, _, outputType, _)            => outputType
      case IRNode.ProjectNode(_, _, _, outputType, _)          => outputType
      case IRNode.FieldAccessNode(_, _, _, outputType, _)      => outputType
      case IRNode.ConditionalNode(_, _, _, _, outputType, _)   => outputType
      case IRNode.LiteralNode(_, _, outputType, _)             => outputType
      case IRNode.AndNode(_, _, _, _)                          => SemanticType.SBoolean
      case IRNode.OrNode(_, _, _, _)                           => SemanticType.SBoolean
      case IRNode.NotNode(_, _, _)                             => SemanticType.SBoolean
      case IRNode.GuardNode(_, _, _, innerType, _)             => SemanticType.SOptional(innerType)
      case IRNode.CoalesceNode(_, _, _, resultType, _)         => resultType
      case IRNode.BranchNode(_, _, _, resultType, _)           => resultType
      case IRNode.StringInterpolationNode(_, _, _, _)          => SemanticType.SString
      case IRNode.HigherOrderNode(_, _, _, _, outputType, _)   => outputType
      case IRNode.ListLiteralNode(_, _, elementType, _)        => SemanticType.SList(elementType)
    }

  /** Build edges from IR dependencies */
  private def buildEdges(ir: IRProgram): List[VizEdge] =
    var edgeId = 0
    def nextEdgeId(): String =
      edgeId += 1
      s"e$edgeId"

    ir.nodes.flatMap { case (targetId, node) =>
      node match {
        case IRNode.Input(_, _, _, _) =>
          List.empty

        case IRNode.ModuleCall(_, _, _, inputs, _, _, _) =>
          inputs.map { case (paramName, sourceId) =>
            VizEdge(
              id = nextEdgeId(),
              source = sourceId.toString,
              target = targetId.toString,
              label = Some(paramName),
              kind = EdgeKind.Data
            )
          }.toList

        case IRNode.MergeNode(_, left, right, _, _) =>
          List(
            VizEdge(nextEdgeId(), left.toString, targetId.toString, Some("left"), EdgeKind.Data),
            VizEdge(nextEdgeId(), right.toString, targetId.toString, Some("right"), EdgeKind.Data)
          )

        case IRNode.ProjectNode(_, source, _, _, _) =>
          List(VizEdge(nextEdgeId(), source.toString, targetId.toString, None, EdgeKind.Data))

        case IRNode.FieldAccessNode(_, source, _, _, _) =>
          List(VizEdge(nextEdgeId(), source.toString, targetId.toString, None, EdgeKind.Data))

        case IRNode.ConditionalNode(_, cond, thenBr, elseBr, _, _) =>
          List(
            VizEdge(nextEdgeId(), cond.toString, targetId.toString, Some("cond"), EdgeKind.Control),
            VizEdge(nextEdgeId(), thenBr.toString, targetId.toString, Some("then"), EdgeKind.Data),
            VizEdge(nextEdgeId(), elseBr.toString, targetId.toString, Some("else"), EdgeKind.Data)
          )

        case IRNode.LiteralNode(_, _, _, _) =>
          List.empty

        case IRNode.AndNode(_, left, right, _) =>
          List(
            VizEdge(nextEdgeId(), left.toString, targetId.toString, Some("left"), EdgeKind.Control),
            VizEdge(nextEdgeId(), right.toString, targetId.toString, Some("right"), EdgeKind.Control)
          )

        case IRNode.OrNode(_, left, right, _) =>
          List(
            VizEdge(nextEdgeId(), left.toString, targetId.toString, Some("left"), EdgeKind.Control),
            VizEdge(nextEdgeId(), right.toString, targetId.toString, Some("right"), EdgeKind.Control)
          )

        case IRNode.NotNode(_, operand, _) =>
          List(VizEdge(nextEdgeId(), operand.toString, targetId.toString, None, EdgeKind.Control))

        case IRNode.GuardNode(_, expr, condition, _, _) =>
          List(
            VizEdge(nextEdgeId(), expr.toString, targetId.toString, Some("expr"), EdgeKind.Data),
            VizEdge(nextEdgeId(), condition.toString, targetId.toString, Some("cond"), EdgeKind.Control)
          )

        case IRNode.CoalesceNode(_, left, right, _, _) =>
          List(
            VizEdge(nextEdgeId(), left.toString, targetId.toString, Some("value"), EdgeKind.Optional),
            VizEdge(nextEdgeId(), right.toString, targetId.toString, Some("default"), EdgeKind.Data)
          )

        case IRNode.BranchNode(_, cases, otherwise, _, _) =>
          val caseEdges = cases.zipWithIndex.flatMap { case ((condId, exprId), idx) =>
            List(
              VizEdge(nextEdgeId(), condId.toString, targetId.toString, Some(s"cond$idx"), EdgeKind.Control),
              VizEdge(nextEdgeId(), exprId.toString, targetId.toString, Some(s"case$idx"), EdgeKind.Data)
            )
          }
          caseEdges :+ VizEdge(nextEdgeId(), otherwise.toString, targetId.toString, Some("otherwise"), EdgeKind.Data)

        case IRNode.StringInterpolationNode(_, _, expressions, _) =>
          expressions.zipWithIndex.map { case (exprId, idx) =>
            VizEdge(nextEdgeId(), exprId.toString, targetId.toString, Some(s"expr$idx"), EdgeKind.Data)
          }

        case IRNode.HigherOrderNode(_, _, source, _, _, _) =>
          List(VizEdge(nextEdgeId(), source.toString, targetId.toString, Some("source"), EdgeKind.Data))

        case IRNode.ListLiteralNode(_, elements, _, _) =>
          elements.zipWithIndex.map { case (elemId, idx) =>
            VizEdge(nextEdgeId(), elemId.toString, targetId.toString, Some(s"[$idx]"), EdgeKind.Data)
          }
      }
    }.toList

  /** Format a type for display, abbreviating long record types */
  private def formatType(t: SemanticType): String =
    t match {
      case SemanticType.SRecord(fields) if fields.size > 3 =>
        val shown = fields.take(3).map { case (n, t) => s"$n: ${formatType(t)}" }.mkString(", ")
        s"{ $shown, ... +${fields.size - 3} }"
      case other =>
        other.prettyPrint
    }

  /** Format a literal value for display */
  private def formatLiteralValue(value: Any): String =
    value match {
      case s: String if s.length > 20 => s""""${s.take(20)}...""""
      case s: String                  => s""""$s""""
      case n: Number                  => n.toString
      case b: Boolean                 => b.toString
      case null                       => "null"
      case other                      => other.toString.take(20)
    }
