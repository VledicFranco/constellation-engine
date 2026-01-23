package io.constellation.lang.viz

/** Common interface for DAG renderers.
  *
  * Renderers transform a DagVizIR into a string representation suitable for different output formats
  * (Mermaid, DOT/Graphviz, ASCII art, etc.).
  */
trait DagRenderer {

  /** Render the DAG to a string in the target format */
  def render(dag: DagVizIR): String

  /** File extension for the output format (e.g., "mmd", "dot", "txt") */
  def fileExtension: String

  /** MIME type for the output format */
  def mimeType: String
}

object DagRenderer {

  /** Available renderer formats */
  val mermaid: DagRenderer = MermaidRenderer
  val dot: DagRenderer     = DOTRenderer
  val ascii: DagRenderer   = ASCIIRenderer
  val svg: DagRenderer     = SVGRenderer

  /** Get a renderer by format name */
  def forFormat(format: String): Option[DagRenderer] = format.toLowerCase match {
    case "mermaid" | "mmd" => Some(MermaidRenderer)
    case "dot" | "graphviz" => Some(DOTRenderer)
    case "ascii" | "text" | "txt" => Some(ASCIIRenderer)
    case "svg" => Some(SVGRenderer)
    case _ => None
  }

  /** List of all available formats */
  val availableFormats: List[String] = List("mermaid", "dot", "ascii", "svg")
}
