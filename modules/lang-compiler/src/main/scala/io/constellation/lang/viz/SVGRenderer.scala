package io.constellation.lang.viz

import scala.collection.mutable.ListBuffer

/** Renders a DagVizIR as SVG markup.
  *
  * This renderer produces standalone SVG that can be:
  *   - Embedded directly in HTML
  *   - Saved as .svg files
  *   - Used for server-side rendering
  *
  * For VSCode integration, client-side rendering is preferred for better theme integration. This
  * renderer is useful for export, documentation, and headless rendering.
  */
object SVGRenderer extends DagRenderer {

  /** Default configuration for SVG rendering */
  case class SVGConfig(
      nodeWidth: Double = 160,
      nodeHeight: Double = 50,
      inputHeight: Double = 36,
      outputHeight: Double = 44,
      moduleHeight: Double = 60,
      padding: Double = 40,
      fontFamily: String = "system-ui, -apple-system, sans-serif",
      fontSize: Int = 12,
      background: Option[String] = None // None = transparent
  )

  /** Node colors by kind */
  private object Colors {
    val inputFill     = "#dcfce7"
    val inputStroke   = "#22c55e"
    val outputFill    = "#dbeafe"
    val outputStroke  = "#3b82f6"
    val opFill        = "#f3f4f6"
    val opStroke      = "#6b7280"
    val opHeader      = "#6b7280"
    val literalFill   = "#fef3c7"
    val literalStroke = "#f59e0b"
    val mergeFill     = "#f3e8ff"
    val mergeStroke   = "#a855f7"
    val guardFill     = "#fef9c3"
    val guardStroke   = "#eab308"
    val condFill      = "#fee2e2"
    val condStroke    = "#ef4444"
    val hofFill       = "#cffafe"
    val hofStroke     = "#06b6d4"
    val edgeColor     = "#64748b"
    val textColor     = "#1f2937"
    val textLight     = "#6b7280"

    // Execution state colors
    val pendingStroke   = "#9ca3af"
    val runningStroke   = "#3b82f6"
    val completedStroke = "#22c55e"
    val failedStroke    = "#ef4444"
    val failedFill      = "#fef2f2"
  }

  def render(dag: DagVizIR): String =
    render(dag, SVGConfig())

  def render(dag: DagVizIR, config: SVGConfig): String = {
    if dag.nodes.isEmpty then {
      return s"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 100">
        <text x="100" y="50" text-anchor="middle" fill="${Colors.textLight}" font-family="${config.fontFamily}">(empty DAG)</text>
      </svg>"""
    }

    val sb = new StringBuilder

    // Calculate bounds from node positions or use metadata
    val bounds  = dag.metadata.bounds.getOrElse(calculateBounds(dag, config))
    val width   = bounds.maxX - bounds.minX + config.padding * 2
    val height  = bounds.maxY - bounds.minY + config.padding * 2
    val viewBox = s"${bounds.minX - config.padding} ${bounds.minY - config.padding} $width $height"

    // SVG header
    sb.append(
      s"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="$viewBox" width="$width" height="$height">\n"""
    )

    // Embedded styles
    sb.append(renderStyles(config))

    // Background if specified
    config.background.foreach { bg =>
      sb.append(
        s"""  <rect x="${bounds.minX - config.padding}" y="${bounds.minY - config.padding}" """
      )
      sb.append(s"""width="$width" height="$height" fill="$bg"/>\n""")
    }

    // Arrow marker definition
    sb.append(renderDefs())

    // Title if present
    dag.metadata.title.foreach { title =>
      sb.append(s"""  <title>$title</title>\n""")
    }

    // Render edges first (behind nodes)
    sb.append("  <g class=\"edges\">\n")
    dag.edges.foreach { edge =>
      sb.append(renderEdge(edge, dag, config))
    }
    sb.append("  </g>\n")

    // Render nodes
    sb.append("  <g class=\"nodes\">\n")
    dag.nodes.foreach { node =>
      sb.append(renderNode(node, config))
    }
    sb.append("  </g>\n")

    sb.append("</svg>")
    sb.toString
  }

  private def calculateBounds(dag: DagVizIR, config: SVGConfig): Bounds = {
    val positions = dag.nodes.flatMap(_.position)
    if positions.isEmpty then {
      Bounds(0, 0, 400, 300)
    } else {
      val xs = positions.map(_.x)
      val ys = positions.map(_.y)
      Bounds(
        minX = xs.min - config.nodeWidth / 2,
        minY = ys.min - config.moduleHeight / 2,
        maxX = xs.max + config.nodeWidth / 2,
        maxY = ys.max + config.moduleHeight / 2
      )
    }
  }

  private def renderStyles(config: SVGConfig): String =
    s"""  <style>
    .node rect, .node ellipse, .node polygon { stroke-width: 2; }
    .node text { font-family: ${config.fontFamily}; font-size: ${config.fontSize}px; fill: ${Colors.textColor}; }
    .node .label { font-weight: 500; }
    .node .type-sig { font-size: ${config.fontSize - 2}px; fill: ${Colors.textLight}; }
    .edge path { fill: none; stroke: ${Colors.edgeColor}; stroke-width: 1.5; }
    .edge.optional path { stroke-dasharray: 5,5; }
    .edge.control path { stroke-width: 2.5; stroke: ${Colors.outputStroke}; }
    .edge text { font-size: ${config.fontSize - 3}px; fill: ${Colors.textLight}; }
    .node.state-running rect, .node.state-running ellipse, .node.state-running polygon { stroke: ${Colors.runningStroke}; stroke-width: 3; }
    .node.state-completed rect, .node.state-completed ellipse, .node.state-completed polygon { stroke: ${Colors.completedStroke}; }
    .node.state-failed rect, .node.state-failed ellipse, .node.state-failed polygon { stroke: ${Colors.failedStroke}; fill: ${Colors.failedFill}; }
    .node.state-pending { opacity: 0.7; }
  </style>
"""

  private def renderDefs(): String =
    s"""  <defs>
    <marker id="arrowhead" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
      <polygon points="0 0, 10 3.5, 0 7" fill="${Colors.edgeColor}"/>
    </marker>
    <marker id="arrowhead-control" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
      <polygon points="0 0, 10 3.5, 0 7" fill="${Colors.outputStroke}"/>
    </marker>
  </defs>
"""

  private def renderNode(node: VizNode, config: SVGConfig): String = {
    val pos = node.position.getOrElse(Position(0, 0))
    val sb  = new StringBuilder

    // Execution state class
    val stateClass =
      node.executionState.map(s => s" state-${s.status.toString.toLowerCase}").getOrElse("")

    // Get dimensions and colors based on node kind
    val (width, height, fillColor, strokeColor) = node.kind match {
      case NodeKind.Input =>
        (config.nodeWidth, config.inputHeight, Colors.inputFill, Colors.inputStroke)
      case NodeKind.Output =>
        (config.nodeWidth, config.outputHeight, Colors.outputFill, Colors.outputStroke)
      case NodeKind.Operation =>
        (config.nodeWidth, config.moduleHeight, Colors.opFill, Colors.opStroke)
      case NodeKind.Literal =>
        (config.nodeWidth, config.nodeHeight, Colors.literalFill, Colors.literalStroke)
      case NodeKind.Merge =>
        (config.nodeWidth, config.nodeHeight, Colors.mergeFill, Colors.mergeStroke)
      case NodeKind.Guard =>
        (config.nodeWidth, config.nodeHeight, Colors.guardFill, Colors.guardStroke)
      case NodeKind.Conditional =>
        (config.nodeWidth, config.nodeHeight, Colors.condFill, Colors.condStroke)
      case NodeKind.Branch =>
        (config.nodeWidth, config.nodeHeight, Colors.condFill, Colors.condStroke)
      case NodeKind.HigherOrder =>
        (config.nodeWidth, config.nodeHeight, Colors.hofFill, Colors.hofStroke)
      case _ => (config.nodeWidth, config.nodeHeight, Colors.opFill, Colors.opStroke)
    }

    val x = pos.x - width / 2
    val y = pos.y - height / 2

    sb.append(s"""    <g class="node$stateClass" data-id="${escapeXml(node.id)}">\n""")

    // Shape based on kind
    node.kind match {
      case NodeKind.Input =>
        // Ellipse for input
        sb.append(
          s"""      <ellipse cx="${pos.x}" cy="${pos.y}" rx="${width / 2}" ry="${height / 2}" """
        )
        sb.append(s"""fill="$fillColor" stroke="$strokeColor"/>\n""")

      case NodeKind.Output =>
        // Hexagon for output
        val points = hexagonPoints(x, y, width, height)
        sb.append(
          s"""      <polygon points="$points" fill="$fillColor" stroke="$strokeColor"/>\n"""
        )

      case NodeKind.Operation =>
        // Rectangle with header bar
        sb.append(s"""      <rect x="$x" y="$y" width="$width" height="$height" rx="4" ry="4" """)
        sb.append(s"""fill="$fillColor" stroke="$strokeColor"/>\n""")
        sb.append(s"""      <rect x="$x" y="$y" width="$width" height="8" rx="4" ry="4" """)
        sb.append(s"""fill="$strokeColor"/>\n""")

      case NodeKind.Merge =>
        // Circle for merge
        val radius = Math.min(width, height) / 2 - 5
        sb.append(s"""      <circle cx="${pos.x}" cy="${pos.y}" r="$radius" """)
        sb.append(s"""fill="$fillColor" stroke="$strokeColor"/>\n""")

      case NodeKind.Conditional | NodeKind.Branch =>
        // Diamond for conditional
        val points = diamondPoints(pos.x, pos.y, width * 0.7, height)
        sb.append(
          s"""      <polygon points="$points" fill="$fillColor" stroke="$strokeColor"/>\n"""
        )

      case _ =>
        // Default rounded rectangle
        sb.append(s"""      <rect x="$x" y="$y" width="$width" height="$height" rx="8" ry="8" """)
        sb.append(s"""fill="$fillColor" stroke="$strokeColor"/>\n""")
    }

    // Icon based on kind
    val icon = node.kind match {
      case NodeKind.Input       => "▶"
      case NodeKind.Output      => "▶"
      case NodeKind.Merge       => "⊕"
      case NodeKind.Literal     => "#"
      case NodeKind.Guard       => "?"
      case NodeKind.Conditional => "⑂"
      case NodeKind.HigherOrder => "ƒ"
      case _                    => ""
    }

    if icon.nonEmpty then {
      val iconX = if node.kind == NodeKind.Output then pos.x + width / 2 - 16 else x + 12
      sb.append(s"""      <text x="$iconX" y="${pos.y + 4}" class="icon">${escapeXml(
          icon
        )}</text>\n""")
    }

    // Label
    val labelY = node.kind match {
      case NodeKind.Operation => pos.y
      case _                  => pos.y + 4
    }
    sb.append(
      s"""      <text x="${pos.x}" y="$labelY" text-anchor="middle" class="label">${escapeXml(
          truncate(node.label, 18)
        )}</text>\n"""
    )

    // Type signature (abbreviated)
    if node.typeSignature.nonEmpty && node.typeSignature != "Unit" then {
      val typeY      = labelY + 14
      val abbrevType = abbreviateType(node.typeSignature)
      sb.append(
        s"""      <text x="${pos.x}" y="$typeY" text-anchor="middle" class="type-sig">${escapeXml(
            abbrevType
          )}</text>\n"""
      )
    }

    // Execution state indicator
    node.executionState.foreach { state =>
      val stateIcon = state.status match {
        case ExecutionStatus.Completed => "✓"
        case ExecutionStatus.Failed    => "✗"
        case ExecutionStatus.Running   => "⟳"
        case _                         => ""
      }
      if stateIcon.nonEmpty then {
        sb.append(
          s"""      <text x="${x + 10}" y="${y + 14}" class="state-icon">$stateIcon</text>\n"""
        )
      }
    }

    sb.append("    </g>\n")
    sb.toString
  }

  private def renderEdge(edge: VizEdge, dag: DagVizIR, config: SVGConfig): String = {
    val sourceNode = dag.nodes.find(_.id == edge.source)
    val targetNode = dag.nodes.find(_.id == edge.target)

    (sourceNode, targetNode) match {
      case (Some(src), Some(tgt)) =>
        val srcPos = src.position.getOrElse(Position(0, 0))
        val tgtPos = tgt.position.getOrElse(Position(0, 0))

        val sb = new StringBuilder

        val edgeClass = edge.kind match {
          case EdgeKind.Optional => " optional"
          case EdgeKind.Control  => " control"
          case _                 => ""
        }
        val marker =
          if edge.kind == EdgeKind.Control then "url(#arrowhead-control)" else "url(#arrowhead)"

        sb.append(
          s"""    <g class="edge$edgeClass" data-source="${edge.source}" data-target="${edge.target}">\n"""
        )

        // Calculate path based on layout direction
        val isVertical = dag.metadata.layoutDirection == "TB"
        val (startX, startY, endX, endY) = if isVertical then {
          val sy = srcPos.y + getNodeHeight(src.kind, config) / 2
          val ey = tgtPos.y - getNodeHeight(tgt.kind, config) / 2
          (srcPos.x, sy, tgtPos.x, ey)
        } else {
          val sx = srcPos.x + config.nodeWidth / 2
          val ex = tgtPos.x - config.nodeWidth / 2
          (sx, srcPos.y, ex, tgtPos.y)
        }

        // Bezier curve
        val (midX, midY) = if isVertical then {
          (startX, (startY + endY) / 2)
        } else {
          ((startX + endX) / 2, startY)
        }

        val path = if isVertical then {
          s"M $startX $startY C $startX $midY, $endX $midY, $endX $endY"
        } else {
          s"M $startX $startY C $midX $startY, $midX $endY, $endX $endY"
        }

        sb.append(s"""      <path d="$path" marker-end="$marker"/>\n""")

        // Edge label if present
        edge.label.foreach { label =>
          val labelX = (startX + endX) / 2
          val labelY = (startY + endY) / 2 - 5
          sb.append(s"""      <text x="$labelX" y="$labelY" text-anchor="middle">${escapeXml(
              label
            )}</text>\n""")
        }

        sb.append("    </g>\n")
        sb.toString

      case _ => ""
    }
  }

  private def getNodeHeight(kind: NodeKind, config: SVGConfig): Double = kind match {
    case NodeKind.Input     => config.inputHeight
    case NodeKind.Output    => config.outputHeight
    case NodeKind.Operation => config.moduleHeight
    case _                  => config.nodeHeight
  }

  private def hexagonPoints(x: Double, y: Double, width: Double, height: Double): String = {
    val inset = 12
    List(
      (x + inset, y),
      (x + width - inset, y),
      (x + width, y + height / 2),
      (x + width - inset, y + height),
      (x + inset, y + height),
      (x, y + height / 2)
    ).map { case (px, py) => s"$px,$py" }.mkString(" ")
  }

  private def diamondPoints(cx: Double, cy: Double, width: Double, height: Double): String =
    List(
      (cx, cy - height / 2),
      (cx + width / 2, cy),
      (cx, cy + height / 2),
      (cx - width / 2, cy)
    ).map { case (px, py) => s"$px,$py" }.mkString(" ")

  private def truncate(s: String, maxLen: Int): String =
    if s.length <= maxLen then s else s.take(maxLen - 1) + "…"

  private def abbreviateType(typeSignature: String): String =
    if typeSignature.length <= 20 then typeSignature
    else typeSignature.take(17) + "..."

  private def escapeXml(s: String): String =
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")

  def fileExtension: String = "svg"
  def mimeType: String      = "image/svg+xml"
}
