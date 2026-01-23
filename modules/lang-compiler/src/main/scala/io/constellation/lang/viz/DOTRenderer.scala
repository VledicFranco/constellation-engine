package io.constellation.lang.viz

import scala.collection.mutable.ListBuffer

/** Renders a DagVizIR as Graphviz DOT format.
  *
  * DOT is the graph description language used by Graphviz. It produces high-quality
  * vector graphics suitable for academic papers, presentations, and professional
  * documentation.
  *
  * Usage:
  * {{{
  * val dot = DOTRenderer.render(dag)
  * // Then use Graphviz CLI:
  * // dot -Tpng output.dot -o output.png
  * // dot -Tsvg output.dot -o output.svg
  * // dot -Tpdf output.dot -o output.pdf
  * }}}
  *
  * @see https://graphviz.org/doc/info/lang.html
  */
object DOTRenderer extends DagRenderer {

  def render(dag: DagVizIR): String = {
    val sb = new StringBuilder

    // DOT header
    sb.append("digraph DAG {\n")

    // Graph attributes
    val rankdir = dag.metadata.layoutDirection match {
      case "LR" => "LR"
      case _    => "TB"
    }
    sb.append(s"    rankdir=$rankdir;\n")
    sb.append("    splines=ortho;\n")
    sb.append("    nodesep=0.8;\n")
    sb.append("    ranksep=1.0;\n")
    sb.append("\n")

    // Default node attributes
    sb.append("    node [\n")
    sb.append("        fontname=\"Helvetica\"\n")
    sb.append("        fontsize=12\n")
    sb.append("        style=filled\n")
    sb.append("    ];\n")
    sb.append("\n")

    // Default edge attributes
    sb.append("    edge [\n")
    sb.append("        fontname=\"Helvetica\"\n")
    sb.append("        fontsize=10\n")
    sb.append("    ];\n")
    sb.append("\n")

    // Add title as a graph label if present
    dag.metadata.title.foreach { title =>
      sb.append(s"    label=${quote(title)};\n")
      sb.append("    labelloc=t;\n")
      sb.append("    fontsize=16;\n")
      sb.append("\n")
    }

    // Render groups as clusters (subgraphs)
    dag.groups.foreach { group =>
      sb.append(s"    subgraph cluster_${sanitizeId(group.id)} {\n")
      sb.append(s"        label=${quote(group.label)};\n")
      sb.append("        style=rounded;\n")
      sb.append("        color=\"#94a3b8\";\n")
      sb.append("        bgcolor=\"#f8fafc\";\n")
      group.nodeIds.foreach { nodeId =>
        sb.append(s"        ${quote(nodeId)};\n")
      }
      sb.append("    }\n\n")
    }

    // Render nodes
    dag.nodes.foreach { node =>
      val attrs = nodeAttributes(node)
      sb.append(s"    ${quote(node.id)} [$attrs];\n")
    }

    sb.append("\n")

    // Render edges
    dag.edges.foreach { edge =>
      val attrs = edgeAttributes(edge)
      val attrStr = if (attrs.nonEmpty) s" [$attrs]" else ""
      sb.append(s"    ${quote(edge.source)} -> ${quote(edge.target)}$attrStr;\n")
    }

    sb.append("}\n")
    sb.toString
  }

  /** Generate DOT node attributes */
  private def nodeAttributes(node: VizNode): String = {
    val attrs = ListBuffer[String]()

    // Label with name and type
    val typeAbbrev = abbreviateType(node.typeSignature)
    val label = if (typeAbbrev.isEmpty || typeAbbrev == "Unit") {
      node.label
    } else {
      s"${node.label}\\n${typeAbbrev}"
    }
    attrs += s"label=${quote(label)}"

    // Shape based on node kind
    val shape = node.kind match {
      case NodeKind.Input       => "ellipse"
      case NodeKind.Output      => "doubleoctagon"
      case NodeKind.Operation   => "box"
      case NodeKind.Literal     => "note"
      case NodeKind.Merge       => "circle"
      case NodeKind.Project     => "parallelogram"
      case NodeKind.FieldAccess => "box"
      case NodeKind.Conditional => "diamond"
      case NodeKind.Guard       => "hexagon"
      case NodeKind.Branch      => "diamond"
      case NodeKind.Coalesce    => "ellipse"
      case NodeKind.HigherOrder => "component"
      case NodeKind.ListLiteral => "folder"
      case NodeKind.BooleanOp   => "diamond"
      case NodeKind.StringInterp => "box"
    }
    attrs += s"shape=$shape"

    // Colors based on node kind
    val (fillColor, borderColor) = node.kind match {
      case NodeKind.Input       => ("#dcfce7", "#22c55e") // Green
      case NodeKind.Output      => ("#dbeafe", "#3b82f6") // Blue
      case NodeKind.Operation   => ("#f3f4f6", "#6b7280") // Gray
      case NodeKind.Literal     => ("#fef3c7", "#f59e0b") // Amber
      case NodeKind.Merge       => ("#f3e8ff", "#a855f7") // Purple
      case NodeKind.Project     => ("#e0e7ff", "#6366f1") // Indigo
      case NodeKind.FieldAccess => ("#f3f4f6", "#6b7280") // Gray
      case NodeKind.Conditional => ("#fee2e2", "#ef4444") // Red
      case NodeKind.Guard       => ("#fce7f3", "#ec4899") // Pink
      case NodeKind.Branch      => ("#fee2e2", "#ef4444") // Red
      case NodeKind.Coalesce    => ("#ecfeff", "#06b6d4") // Cyan
      case NodeKind.HigherOrder => ("#cffafe", "#06b6d4") // Cyan
      case NodeKind.ListLiteral => ("#fef3c7", "#f59e0b") // Amber
      case NodeKind.BooleanOp   => ("#fef3c7", "#f59e0b") // Amber
      case NodeKind.StringInterp => ("#f3f4f6", "#6b7280") // Gray
    }
    attrs += s"fillcolor=${quote(fillColor)}"
    attrs += s"color=${quote(borderColor)}"

    // Execution state styling
    node.executionState.foreach { state =>
      state.status match {
        case ExecutionStatus.Running =>
          attrs += "penwidth=3"
          attrs += "color=\"#f59e0b\""
        case ExecutionStatus.Completed =>
          attrs += "penwidth=2"
          attrs += "color=\"#22c55e\""
        case ExecutionStatus.Failed =>
          attrs += "penwidth=3"
          attrs += "color=\"#ef4444\""
          attrs += "fillcolor=\"#fee2e2\""
        case ExecutionStatus.Pending => // Default styling
      }
    }

    attrs.mkString(", ")
  }

  /** Generate DOT edge attributes */
  private def edgeAttributes(edge: VizEdge): String = {
    val attrs = ListBuffer[String]()

    // Label for parameter name
    edge.label.foreach { l =>
      attrs += s"label=${quote(l)}"
    }

    // Edge style based on kind
    edge.kind match {
      case EdgeKind.Data =>
        attrs += "color=\"#374151\""
      case EdgeKind.Optional =>
        attrs += "style=dashed"
        attrs += "color=\"#9ca3af\""
      case EdgeKind.Control =>
        attrs += "style=bold"
        attrs += "color=\"#3b82f6\""
        attrs += "penwidth=2"
    }

    attrs.mkString(", ")
  }

  /** Abbreviate long type signatures */
  private def abbreviateType(typeSignature: String): String = {
    if (typeSignature.length <= 25) typeSignature
    else typeSignature.take(22) + "..."
  }

  /** Quote a string for DOT format */
  private def quote(s: String): String = {
    val escaped = s
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
    s"\"$escaped\""
  }

  /** Sanitize an ID for DOT */
  private def sanitizeId(id: String): String = {
    id.replaceAll("[^a-zA-Z0-9_]", "_")
  }

  def fileExtension: String = "dot"
  def mimeType: String      = "text/vnd.graphviz"
}
