package io.constellation.lang.viz

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class MermaidRendererTest extends AnyFunSuite with Matchers {

  private def makeNode(
      id: String,
      kind: NodeKind = NodeKind.Operation,
      label: String = ""
  ): VizNode =
    VizNode(id, kind, if label.isEmpty then s"Node$id" else label, "String", None, None)

  private def makeEdge(source: String, target: String, kind: EdgeKind = EdgeKind.Data): VizEdge =
    VizEdge(s"e-$source-$target", source, target, None, kind)

  test("render empty dag") {
    val dag    = DagVizIR(nodes = List.empty, edges = List.empty)
    val result = MermaidRenderer.render(dag)

    result should include("graph TD")
    result should include("Empty DAG")
  }

  test("render single input node") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input, "data")),
      edges = List.empty
    )

    val result = MermaidRenderer.render(dag)

    result should include("graph TD")
    result should include("([") // Stadium shape for input
    result should include("data")
  }

  test("render linear pipeline") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("a", NodeKind.Input, "input"),
        makeNode("b", NodeKind.Operation, "Process"),
        makeNode("c", NodeKind.Output, "output")
      ),
      edges = List(
        makeEdge("a", "b"),
        makeEdge("b", "c")
      )
    )

    val result = MermaidRenderer.render(dag)

    result should include("graph TD")
    result should include("-->") // Solid arrows
    result should include("input")
    result should include("Process")
    result should include("output")
    result should include("[[") // Output shape
  }

  test("render different node shapes") {
    val nodes = List(
      makeNode("input", NodeKind.Input),
      makeNode("output", NodeKind.Output),
      makeNode("op", NodeKind.Operation),
      makeNode("merge", NodeKind.Merge),
      makeNode("cond", NodeKind.Conditional),
      makeNode("guard", NodeKind.Guard),
      makeNode("literal", NodeKind.Literal)
    )

    val dag    = DagVizIR(nodes = nodes, edges = List.empty)
    val result = MermaidRenderer.render(dag)

    // Check different shape markers are present
    result should include("([")  // Input - stadium
    result should include("[[")  // Output - subroutine
    result should include("[\"") // Operation - rectangle (with quote)
    result should include("((")  // Merge - circle
    result should include("{")   // Conditional - diamond
    result should include("{{")  // Guard - hexagon
    result should include("(\"") // Literal - rounded
  }

  test("render different edge kinds") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("a", NodeKind.Input),
        makeNode("b", NodeKind.Operation),
        makeNode("c", NodeKind.Operation),
        makeNode("d", NodeKind.Output)
      ),
      edges = List(
        VizEdge("e1", "a", "b", None, EdgeKind.Data),
        VizEdge("e2", "a", "c", None, EdgeKind.Optional),
        VizEdge("e3", "b", "d", None, EdgeKind.Control)
      )
    )

    val result = MermaidRenderer.render(dag)

    result should include("-->")  // Data edge - solid
    result should include("-.->") // Optional edge - dashed
    result should include("==>")  // Control edge - thick
  }

  test("render edge labels") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("a", NodeKind.Input, "x"),
        makeNode("b", NodeKind.Input, "y"),
        makeNode("c", NodeKind.Operation, "Add")
      ),
      edges = List(
        VizEdge("e1", "a", "c", Some("left"), EdgeKind.Data),
        VizEdge("e2", "b", "c", Some("right"), EdgeKind.Data)
      )
    )

    val result = MermaidRenderer.render(dag)

    // Mermaid edge labels are escaped with quotes
    result should include("|\"left\"|")
    result should include("|\"right\"|")
  }

  test("render with title") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input)),
      edges = List.empty,
      metadata = VizMetadata(title = Some("My Pipeline"))
    )

    val result = MermaidRenderer.render(dag)

    result should include("%% My Pipeline")
  }

  test("render LR direction") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input)),
      edges = List.empty,
      metadata = VizMetadata(layoutDirection = "LR")
    )

    val result = MermaidRenderer.render(dag)

    result should include("graph LR")
  }

  test("render groups as subgraphs") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("a", NodeKind.Input),
        makeNode("b", NodeKind.Operation),
        makeNode("c", NodeKind.Output)
      ),
      edges = List(makeEdge("a", "b"), makeEdge("b", "c")),
      groups = List(
        NodeGroup("group1", "Processing", List("b"))
      )
    )

    val result = MermaidRenderer.render(dag)

    result should include("subgraph group1")
    result should include("Processing")
    result should include("end")
  }

  test("escape special characters in labels") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input, "data<String>")),
      edges = List.empty
    )

    val result = MermaidRenderer.render(dag)

    // Should escape < and > characters
    result should include("#lt;")
    result should include("#gt;")
  }

  test("sanitize node IDs") {
    val dag = DagVizIR(
      nodes = List(
        VizNode("123-abc", NodeKind.Input, "test", "String", None, None)
      ),
      edges = List.empty
    )

    val result = MermaidRenderer.render(dag)

    // IDs starting with numbers should be prefixed
    result should include("n123")
    // Dashes should be replaced with underscores
    result should include("_abc")
  }

  test("add styling for node kinds") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("a", NodeKind.Input),
        makeNode("b", NodeKind.Output)
      ),
      edges = List.empty
    )

    val result = MermaidRenderer.render(dag)

    // Should have style directives
    result should include("style")
    result should include("fill:")
  }

  test("file extension and mime type") {
    MermaidRenderer.fileExtension shouldBe "mmd"
    MermaidRenderer.mimeType shouldBe "text/x-mermaid"
  }
}
