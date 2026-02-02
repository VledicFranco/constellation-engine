package io.constellation.lang.viz

import scala.collection.mutable

/** Renders a DagVizIR as ASCII art.
  *
  * This renderer produces text-based visualizations suitable for terminal output, logs, and
  * environments without graphics support.
  *
  * The output uses Unicode box-drawing characters for better visual appearance. If the DAG has been
  * laid out (nodes have positions), the renderer will organize nodes by layer. Otherwise, it will
  * perform a simple topological sort.
  */
object ASCIIRenderer extends DagRenderer {

  private val BoxWidth = 24
  private val BoxChars = BoxCharacters.Unicode

  case class BoxCharacters(
      topLeft: Char,
      topRight: Char,
      bottomLeft: Char,
      bottomRight: Char,
      horizontal: Char,
      vertical: Char,
      downArrow: Char,
      rightArrow: Char,
      teeDown: Char,
      teeUp: Char,
      teeRight: Char,
      teeLeft: Char,
      cross: Char
  )

  object BoxCharacters {
    val Unicode = BoxCharacters(
      topLeft = '\u250c',     // ┌
      topRight = '\u2510',    // ┐
      bottomLeft = '\u2514',  // └
      bottomRight = '\u2518', // ┘
      horizontal = '\u2500',  // ─
      vertical = '\u2502',    // │
      downArrow = '\u25bc',   // ▼
      rightArrow = '\u25b6',  // ▶
      teeDown = '\u252c',     // ┬
      teeUp = '\u2534',       // ┴
      teeRight = '\u251c',    // ├
      teeLeft = '\u2524',     // ┤
      cross = '\u253c'        // ┼
    )

    val ASCII = BoxCharacters(
      topLeft = '+',
      topRight = '+',
      bottomLeft = '+',
      bottomRight = '+',
      horizontal = '-',
      vertical = '|',
      downArrow = 'v',
      rightArrow = '>',
      teeDown = '+',
      teeUp = '+',
      teeRight = '+',
      teeLeft = '+',
      cross = '+'
    )
  }

  def render(dag: DagVizIR): String = {
    if dag.nodes.isEmpty then return "(empty DAG)"

    val sb = new StringBuilder

    // Add title if present
    dag.metadata.title.foreach { title =>
      sb.append(s"=== $title ===\n\n")
    }

    // Group nodes by layer (y position) or compute layers
    val layers = computeLayers(dag)

    // Render each layer
    layers.zipWithIndex.foreach { case (layerNodes, layerIdx) =>
      // Render the nodes in this layer
      renderLayer(sb, layerNodes, dag)

      // Draw connections to next layer (if not last)
      if layerIdx < layers.length - 1 then {
        renderConnections(sb, layerNodes, layers(layerIdx + 1), dag)
      }
    }

    // Add legend
    sb.append("\n")
    renderLegend(sb)

    sb.toString
  }

  /** Group nodes into layers based on position or topological order */
  private def computeLayers(dag: DagVizIR): List[List[VizNode]] = {
    // Check if nodes have positions (from layout)
    val hasPositions = dag.nodes.exists(_.position.isDefined)

    if hasPositions then {
      // Group by Y position (for TB layout) or X position (for LR layout)
      val isLR = dag.metadata.layoutDirection == "LR"
      val groups = dag.nodes.groupBy { node =>
        node.position.map(p => if isLR then p.x else p.y).getOrElse(0.0)
      }
      groups.toList
        .sortBy(_._1)
        .map(_._2.sortBy { node =>
          node.position.map(p => if isLR then p.y else p.x).getOrElse(0.0)
        })
    } else {
      // Compute layers using topological sort
      topologicalLayers(dag)
    }
  }

  /** Compute layers using Kahn's algorithm (topological sort) */
  private def topologicalLayers(dag: DagVizIR): List[List[VizNode]] = {
    val nodeMap  = dag.nodes.map(n => n.id -> n).toMap
    val outEdges = dag.edges.groupBy(_.source).map { case (k, v) => k -> v.map(_.target) }
    val inDegree = mutable.Map[String, Int]().withDefaultValue(0)

    dag.edges.foreach(e => inDegree(e.target) += 1)

    val layers  = mutable.ListBuffer[List[VizNode]]()
    var current = dag.nodes.filter(n => inDegree(n.id) == 0)

    while current.nonEmpty do {
      layers += current
      val next = mutable.ListBuffer[VizNode]()
      current.foreach { node =>
        outEdges.getOrElse(node.id, List.empty).foreach { targetId =>
          inDegree(targetId) -= 1
          if inDegree(targetId) == 0 then {
            nodeMap.get(targetId).foreach(next += _)
          }
        }
      }
      current = next.toList
    }

    layers.toList
  }

  /** Render a layer of nodes */
  private def renderLayer(sb: StringBuilder, nodes: List[VizNode], dag: DagVizIR): Unit = {
    val boxes    = nodes.map(renderNodeBox)
    val maxLines = boxes.map(_.length).maxOption.getOrElse(0)

    // Pad boxes to same height
    val paddedBoxes = boxes.map { box =>
      val padding = List.fill(maxLines - box.length)(emptyLine)
      box ++ padding
    }

    // Render line by line across all boxes
    for lineIdx <- 0 until maxLines do {
      val line = paddedBoxes.map(_(lineIdx)).mkString("  ")
      sb.append(line).append("\n")
    }
  }

  /** Render a single node as a box */
  private def renderNodeBox(node: VizNode): List[String] = {
    val lines = mutable.ListBuffer[String]()
    val bc    = BoxChars
    val width = BoxWidth
    val inner = width - 2

    // Kind indicator
    val kindChar = node.kind match {
      case NodeKind.Input       => s"${bc.rightArrow} "
      case NodeKind.Output      => s" ${bc.rightArrow}"
      case NodeKind.Merge       => "+ "
      case NodeKind.Conditional => "? "
      case NodeKind.Guard       => "! "
      case NodeKind.Literal     => "# "
      case NodeKind.HigherOrder => "f "
      case _                    => "  "
    }

    // Format label
    val label = truncate(node.label, inner - kindChar.length)

    // Format type (abbreviated)
    val typeStr = truncate(abbreviateType(node.typeSignature), inner)

    // Build the box
    lines += s"${bc.topLeft}${bc.horizontal.toString * inner}${bc.topRight}"
    lines += s"${bc.vertical}${kindChar}${padRight(label, inner - kindChar.length)}${bc.vertical}"
    if typeStr.nonEmpty && typeStr != "Unit" then {
      lines += s"${bc.vertical}${padRight(typeStr, inner)}${bc.vertical}"
    }

    // Execution state if present
    node.executionState.foreach { state =>
      val stateStr = state.status match {
        case ExecutionStatus.Pending   => "[pending]"
        case ExecutionStatus.Running   => "[RUNNING]"
        case ExecutionStatus.Completed => "[done]"
        case ExecutionStatus.Failed    => "[FAILED]"
      }
      lines += s"${bc.vertical}${padRight(stateStr, inner)}${bc.vertical}"
    }

    lines += s"${bc.bottomLeft}${bc.horizontal.toString * inner}${bc.bottomRight}"
    lines.toList
  }

  /** Render connections between layers */
  private def renderConnections(
      sb: StringBuilder,
      currentLayer: List[VizNode],
      nextLayer: List[VizNode],
      dag: DagVizIR
  ): Unit = {
    val boxCenter = BoxWidth / 2

    // Find edges from current layer to next layer
    val currentIds = currentLayer.map(_.id).toSet
    val nextIds    = nextLayer.map(_.id).toSet
    val relevantEdges = dag.edges.filter { e =>
      currentIds.contains(e.source) && nextIds.contains(e.target)
    }

    if relevantEdges.nonEmpty then {
      // Simple connector line
      val spacing = "  " // Space between boxes
      val connectorLine = currentLayer.indices
        .map { idx =>
          val nodeId  = currentLayer(idx).id
          val hasEdge = relevantEdges.exists(_.source == nodeId)
          if hasEdge then {
            " " * (boxCenter - 1) + BoxChars.vertical.toString + " " * (BoxWidth - boxCenter)
          } else {
            " " * BoxWidth
          }
        }
        .mkString(spacing)

      sb.append(connectorLine).append("\n")

      // Arrow line
      val arrowLine = currentLayer.indices
        .map { idx =>
          val nodeId  = currentLayer(idx).id
          val hasEdge = relevantEdges.exists(_.source == nodeId)
          if hasEdge then {
            " " * (boxCenter - 1) + BoxChars.downArrow.toString + " " * (BoxWidth - boxCenter)
          } else {
            " " * BoxWidth
          }
        }
        .mkString(spacing)

      sb.append(arrowLine).append("\n")
    }
  }

  /** Render a legend explaining the symbols */
  private def renderLegend(sb: StringBuilder): Unit = {
    sb.append("Legend:\n")
    sb.append(s"  ${BoxChars.rightArrow}  Input    ")
    sb.append(s"  ${BoxChars.rightArrow} Output   ")
    sb.append(s"  + Merge    ")
    sb.append(s"  ? Conditional\n")
    sb.append(s"  ! Guard    ")
    sb.append(s"  # Literal  ")
    sb.append(s"  f Higher-order\n")
  }

  /** Empty line for padding */
  private def emptyLine: String = " " * BoxWidth

  /** Truncate text to fit width */
  private def truncate(text: String, maxLen: Int): String =
    if text.length <= maxLen then text
    else text.take(maxLen - 3) + "..."

  /** Pad string to the right */
  private def padRight(text: String, width: Int): String =
    if text.length >= width then text.take(width)
    else text + " " * (width - text.length)

  /** Abbreviate type signatures */
  private def abbreviateType(typeSignature: String): String =
    if typeSignature.length <= 20 then typeSignature
    else typeSignature.take(17) + "..."

  def fileExtension: String = "txt"
  def mimeType: String      = "text/plain"
}
