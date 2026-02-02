package io.constellation.lang.viz

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SVGRendererTest extends AnyFunSuite with Matchers {

  private def makeNode(
      id: String,
      kind: NodeKind = NodeKind.Operation,
      label: String = ""
  ): VizNode =
    VizNode(id, kind, if label.isEmpty then s"Node$id" else label, "String", None, None)

  private def makeNodeWithPosition(
      id: String,
      kind: NodeKind,
      label: String,
      x: Double,
      y: Double
  ): VizNode =
    VizNode(id, kind, label, "String", Some(Position(x, y)), None)

  private def makeEdge(source: String, target: String, kind: EdgeKind = EdgeKind.Data): VizEdge =
    VizEdge(s"e-$source-$target", source, target, None, kind)

  test("render valid SVG structure") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input, "input")),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should startWith("<svg xmlns=\"http://www.w3.org/2000/svg\"")
    result should include("viewBox=")
    result should endWith("</svg>")
  }

  test("render empty dag") {
    val dag    = DagVizIR(nodes = List.empty, edges = List.empty)
    val result = SVGRenderer.render(dag)

    result should include("<svg")
    result should include("(empty DAG)")
  }

  test("render nodes with positions") {
    val dag = DagVizIR(
      nodes = List(
        makeNodeWithPosition("a", NodeKind.Input, "data", 100, 50),
        makeNodeWithPosition("b", NodeKind.Output, "result", 100, 150)
      ),
      edges = List(makeEdge("a", "b"))
    )

    val result = SVGRenderer.render(dag)

    result should include("<g class=\"nodes\">")
    result should include("data-id=\"a\"")
    result should include("data-id=\"b\"")
  }

  test("render input node as ellipse") {
    val dag = DagVizIR(
      nodes = List(makeNodeWithPosition("a", NodeKind.Input, "input", 100, 100)),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("<ellipse")
    result should include("cx=\"100.0\"")
    result should include("cy=\"100.0\"")
  }

  test("render output node as hexagon") {
    val dag = DagVizIR(
      nodes = List(makeNodeWithPosition("a", NodeKind.Output, "output", 100, 100)),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("<polygon")
    result should include("points=")
  }

  test("render operation node as rectangle with header") {
    val dag = DagVizIR(
      nodes = List(makeNodeWithPosition("a", NodeKind.Operation, "Process", 100, 100)),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    // Operation has two rects - main body and header bar
    result should include("<rect")
    result should include("rx=\"4\"")
  }

  test("render merge node as circle") {
    val dag = DagVizIR(
      nodes = List(makeNodeWithPosition("a", NodeKind.Merge, "join", 100, 100)),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("<circle")
    result should include("cx=\"100.0\"")
    result should include("cy=\"100.0\"")
  }

  test("render conditional node as diamond") {
    val dag = DagVizIR(
      nodes = List(makeNodeWithPosition("a", NodeKind.Conditional, "check", 100, 100)),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("<polygon")
    // Diamond shape has 4 points
    result should include("points=")
  }

  test("render edges with paths") {
    val dag = DagVizIR(
      nodes = List(
        makeNodeWithPosition("a", NodeKind.Input, "x", 100, 50),
        makeNodeWithPosition("b", NodeKind.Output, "y", 100, 150)
      ),
      edges = List(makeEdge("a", "b"))
    )

    val result = SVGRenderer.render(dag)

    result should include("<g class=\"edges\">")
    result should include("<path")
    result should include("marker-end=")
    result should include("data-source=\"a\"")
    result should include("data-target=\"b\"")
  }

  test("render optional edge with dashed style") {
    val dag = DagVizIR(
      nodes = List(
        makeNodeWithPosition("a", NodeKind.Input, "x", 100, 50),
        makeNodeWithPosition("b", NodeKind.Operation, "op", 100, 150)
      ),
      edges = List(VizEdge("e1", "a", "b", None, EdgeKind.Optional))
    )

    val result = SVGRenderer.render(dag)

    result should include("class=\"edge optional\"")
  }

  test("render control edge with control style") {
    val dag = DagVizIR(
      nodes = List(
        makeNodeWithPosition("a", NodeKind.Input, "x", 100, 50),
        makeNodeWithPosition("b", NodeKind.Output, "y", 100, 150)
      ),
      edges = List(VizEdge("e1", "a", "b", None, EdgeKind.Control))
    )

    val result = SVGRenderer.render(dag)

    result should include("class=\"edge control\"")
    result should include("url(#arrowhead-control)")
  }

  test("render edge labels") {
    val dag = DagVizIR(
      nodes = List(
        makeNodeWithPosition("a", NodeKind.Input, "x", 100, 50),
        makeNodeWithPosition("b", NodeKind.Operation, "Add", 100, 150)
      ),
      edges = List(VizEdge("e1", "a", "b", Some("param"), EdgeKind.Data))
    )

    val result = SVGRenderer.render(dag)

    result should include("param")
    result should include("text-anchor=\"middle\"")
  }

  test("render with title") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input)),
      edges = List.empty,
      metadata = VizMetadata(title = Some("My Pipeline"))
    )

    val result = SVGRenderer.render(dag)

    result should include("<title>My Pipeline</title>")
  }

  test("render execution state - running") {
    val dag = DagVizIR(
      nodes = List(
        VizNode(
          "a",
          NodeKind.Operation,
          "running",
          "String",
          Some(Position(100, 100)),
          Some(ExecutionState(ExecutionStatus.Running))
        )
      ),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("state-running")
  }

  test("render execution state - completed") {
    val dag = DagVizIR(
      nodes = List(
        VizNode(
          "a",
          NodeKind.Operation,
          "done",
          "String",
          Some(Position(100, 100)),
          Some(ExecutionState(ExecutionStatus.Completed))
        )
      ),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("state-completed")
  }

  test("render execution state - failed") {
    val dag = DagVizIR(
      nodes = List(
        VizNode(
          "a",
          NodeKind.Operation,
          "failed",
          "String",
          Some(Position(100, 100)),
          Some(ExecutionState(ExecutionStatus.Failed, error = Some("Error!")))
        )
      ),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("state-failed")
  }

  test("render type signature") {
    val dag = DagVizIR(
      nodes = List(
        VizNode("a", NodeKind.Input, "data", "List<String>", Some(Position(100, 100)), None)
      ),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("List&lt;String&gt;") // XML escaped
    result should include("class=\"type-sig\"")
  }

  test("abbreviate long type signatures") {
    val longType = "{ field1: String, field2: Int, field3: Boolean, field4: Float }"
    val dag = DagVizIR(
      nodes = List(
        VizNode("a", NodeKind.Input, "data", longType, Some(Position(100, 100)), None)
      ),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("...")
  }

  test("escape XML characters in labels") {
    val dag = DagVizIR(
      nodes = List(
        VizNode("a", NodeKind.Input, "data<T>", "String", Some(Position(100, 100)), None)
      ),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("data&lt;T&gt;")
    result should not include "data<T>"
  }

  test("escape XML characters in node IDs") {
    val dag = DagVizIR(
      nodes = List(
        VizNode("a&b", NodeKind.Input, "test", "String", Some(Position(100, 100)), None)
      ),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("data-id=\"a&amp;b\"")
  }

  test("render with custom config") {
    val dag = DagVizIR(
      nodes = List(makeNodeWithPosition("a", NodeKind.Input, "input", 100, 100)),
      edges = List.empty
    )

    val config = SVGRenderer.SVGConfig(
      nodeWidth = 200,
      fontSize = 14,
      background = Some("#ffffff")
    )

    val result = SVGRenderer.render(dag, config)

    result should include("font-size: 14px")
    result should include("fill=\"#ffffff\"")
  }

  test("include embedded styles") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input)),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("<style>")
    result should include(".node")
    result should include(".edge")
    result should include("</style>")
  }

  test("include arrow marker definitions") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input)),
      edges = List.empty
    )

    val result = SVGRenderer.render(dag)

    result should include("<defs>")
    result should include("<marker id=\"arrowhead\"")
    result should include("<marker id=\"arrowhead-control\"")
    result should include("</defs>")
  }

  test("render different node kinds with correct colors") {
    val nodes = List(
      makeNodeWithPosition("input", NodeKind.Input, "in", 100, 50),
      makeNodeWithPosition("output", NodeKind.Output, "out", 100, 100),
      makeNodeWithPosition("op", NodeKind.Operation, "proc", 100, 150),
      makeNodeWithPosition("lit", NodeKind.Literal, "42", 100, 200),
      makeNodeWithPosition("merge", NodeKind.Merge, "join", 100, 250),
      makeNodeWithPosition("guard", NodeKind.Guard, "check", 100, 300),
      makeNodeWithPosition("hof", NodeKind.HigherOrder, "map", 100, 350)
    )

    val dag    = DagVizIR(nodes = nodes, edges = List.empty)
    val result = SVGRenderer.render(dag)

    // Check that different colors are present
    result should include("#dcfce7") // Input fill (green)
    result should include("#dbeafe") // Output fill (blue)
    result should include("#f3f4f6") // Operation fill (gray)
    result should include("#fef3c7") // Literal fill (amber)
    result should include("#f3e8ff") // Merge fill (purple)
    result should include("#fef9c3") // Guard fill (yellow)
    result should include("#cffafe") // HigherOrder fill (cyan)
  }

  test("use metadata bounds if provided") {
    val dag = DagVizIR(
      nodes = List(makeNodeWithPosition("a", NodeKind.Input, "input", 100, 100)),
      edges = List.empty,
      metadata = VizMetadata(bounds = Some(Bounds(0, 0, 500, 400)))
    )

    val result = SVGRenderer.render(dag)

    // Bounds affect the viewBox
    result should include("viewBox=")
    result should include("width=\"580.0\"")  // 500 - 0 + 40*2 padding
    result should include("height=\"480.0\"") // 400 - 0 + 40*2 padding
  }

  test("file extension and mime type") {
    SVGRenderer.fileExtension shouldBe "svg"
    SVGRenderer.mimeType shouldBe "image/svg+xml"
  }
}
