package io.constellation.lang.viz

/** Renders a DagVizIR as Mermaid diagram syntax.
  *
  * Mermaid is a markdown-based diagramming tool that renders in GitHub, GitLab, and many
  * documentation platforms. This is the preferred format for README files and GitHub issues.
  *
  * @see
  *   https://mermaid.js.org/
  */
object MermaidRenderer extends DagRenderer {

  def render(dag: DagVizIR): String = {
    if dag.nodes.isEmpty then return "graph TD\n    %% Empty DAG"

    val sb = new StringBuilder

    // Header with direction
    val direction = dag.metadata.layoutDirection match {
      case "LR" => "LR"
      case _    => "TD"
    }
    sb.append(s"graph $direction\n")

    // Add title as a comment if present
    dag.metadata.title.foreach { title =>
      sb.append(s"    %% $title\n")
    }

    // Render nodes with appropriate shapes
    dag.nodes.foreach { node =>
      val (open, close) = nodeShape(node.kind)
      val label         = escapeLabel(formatNodeLabel(node))
      sb.append(s"    ${sanitizeId(node.id)}$open$label$close\n")
    }

    sb.append("\n")

    // Render edges
    dag.edges.foreach { edge =>
      val arrow = edgeArrow(edge.kind)
      val label = edge.label.map(l => s"|${escapeLabel(l)}|").getOrElse("")
      sb.append(s"    ${sanitizeId(edge.source)} $arrow$label ${sanitizeId(edge.target)}\n")
    }

    // Render groups as subgraphs
    dag.groups.foreach { group =>
      sb.append(s"\n    subgraph ${sanitizeId(group.id)}[${escapeLabel(group.label)}]\n")
      group.nodeIds.foreach { nodeId =>
        sb.append(s"        ${sanitizeId(nodeId)}\n")
      }
      sb.append("    end\n")
    }

    // Add styling classes for node kinds
    sb.append("\n")
    addStyling(sb, dag)

    sb.toString
  }

  /** Get Mermaid shape delimiters for a node kind */
  private def nodeShape(kind: NodeKind): (String, String) = kind match {
    case NodeKind.Input        => ("([", "])") // Stadium shape (rounded ends)
    case NodeKind.Output       => ("[[", "]]") // Subroutine shape
    case NodeKind.Operation    => ("[", "]")   // Rectangle
    case NodeKind.Literal      => ("(", ")")   // Rounded rectangle
    case NodeKind.Merge        => ("((", "))") // Circle
    case NodeKind.Project      => ("[/", "/]") // Parallelogram
    case NodeKind.FieldAccess  => ("[", "]")   // Rectangle
    case NodeKind.Conditional  => ("{", "}")   // Diamond
    case NodeKind.Guard        => ("{{", "}}") // Hexagon
    case NodeKind.Branch       => ("{", "}")   // Diamond
    case NodeKind.Coalesce     => ("([", "])") // Stadium
    case NodeKind.HigherOrder  => ("[[", "]]") // Subroutine
    case NodeKind.ListLiteral  => ("(", ")")   // Rounded rectangle
    case NodeKind.BooleanOp    => ("{", "}")   // Diamond
    case NodeKind.StringInterp => ("[", "]")   // Rectangle
  }

  /** Get Mermaid arrow style for an edge kind */
  private def edgeArrow(kind: EdgeKind): String = kind match {
    case EdgeKind.Data     => "-->"  // Solid arrow
    case EdgeKind.Optional => "-.->" // Dashed arrow
    case EdgeKind.Control  => "==>"  // Thick arrow
  }

  /** Format a node's label for display */
  private def formatNodeLabel(node: VizNode): String = {
    val typeAbbrev = abbreviateType(node.typeSignature)
    if typeAbbrev.isEmpty || typeAbbrev == "Unit" then {
      node.label
    } else {
      s"${node.label}<br/><small>${typeAbbrev}</small>"
    }
  }

  /** Abbreviate long type signatures */
  private def abbreviateType(typeSignature: String): String =
    if typeSignature.length <= 30 then typeSignature
    else typeSignature.take(27) + "..."

  /** Escape special characters for Mermaid labels */
  private def escapeLabel(text: String): String = {
    // Mermaid uses quotes for labels with special chars
    val escaped = text
      .replace("\\", "\\\\")
      .replace("\"", "#quot;")
      .replace("<", "#lt;")
      .replace(">", "#gt;")
      .replace("{", "#123;")
      .replace("}", "#125;")
      .replace("[", "#91;")
      .replace("]", "#93;")
      .replace("|", "#124;")

    s"\"$escaped\""
  }

  /** Sanitize node IDs for Mermaid (must be alphanumeric with underscores) */
  private def sanitizeId(id: String): String = {
    // Replace non-alphanumeric characters with underscores
    // Prefix with 'n' if starts with a digit
    val sanitized = id.replaceAll("[^a-zA-Z0-9_]", "_")
    if sanitized.headOption.exists(_.isDigit) then s"n$sanitized"
    else sanitized
  }

  /** Add CSS-like styling for node types */
  private def addStyling(sb: StringBuilder, dag: DagVizIR): Unit = {
    // Group nodes by kind for styling
    val nodesByKind = dag.nodes.groupBy(_.kind)

    // Define styles for each kind
    val kindStyles: Map[NodeKind, String] = Map(
      NodeKind.Input       -> "fill:#22c55e,stroke:#16a34a,color:#fff",
      NodeKind.Output      -> "fill:#3b82f6,stroke:#2563eb,color:#fff",
      NodeKind.Operation   -> "fill:#6b7280,stroke:#4b5563,color:#fff",
      NodeKind.Literal     -> "fill:#f59e0b,stroke:#d97706,color:#fff",
      NodeKind.Merge       -> "fill:#a855f7,stroke:#9333ea,color:#fff",
      NodeKind.Conditional -> "fill:#ef4444,stroke:#dc2626,color:#fff",
      NodeKind.Guard       -> "fill:#ec4899,stroke:#db2777,color:#fff",
      NodeKind.HigherOrder -> "fill:#06b6d4,stroke:#0891b2,color:#fff"
    )

    // Apply styles to nodes
    kindStyles.foreach { case (kind, style) =>
      nodesByKind.get(kind).foreach { nodes =>
        if nodes.nonEmpty then {
          val nodeIds = nodes.map(n => sanitizeId(n.id)).mkString(",")
          sb.append(s"    style $nodeIds $style\n")
        }
      }
    }
  }

  def fileExtension: String = "mmd"
  def mimeType: String      = "text/x-mermaid"
}
