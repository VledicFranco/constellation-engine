package io.constellation.lang.viz

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ASCIIRendererTest extends AnyFunSuite with Matchers {

  private def makeNode(
      id: String,
      kind: NodeKind = NodeKind.Operation,
      label: String = "",
      yPos: Option[Double] = None
  ): VizNode =
    VizNode(
      id,
      kind,
      if (label.isEmpty) s"Node$id" else label,
      "String",
      yPos.map(y => Position(0, y)),
      None
    )

  private def makeEdge(source: String, target: String): VizEdge =
    VizEdge(s"e-$source-$target", source, target, None, EdgeKind.Data)

  test("render empty dag") {
    val dag = DagVizIR(nodes = List.empty, edges = List.empty)
    val result = ASCIIRenderer.render(dag)

    result shouldBe "(empty DAG)"
  }

  test("render single node") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input, "data")),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    // Should have box characters
    result should include("┌")
    result should include("┐")
    result should include("└")
    result should include("┘")
    result should include("─")
    result should include("│")
    result should include("data")
  }

  test("render input node with indicator") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input, "data")),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    result should include("▶") // Input indicator
  }

  test("render output node with indicator") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Output, "result")),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    result should include("▶") // Output indicator (at end)
  }

  test("render merge node with indicator") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Merge, "join")),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    result should include("+") // Merge indicator
  }

  test("render conditional node with indicator") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Conditional, "check")),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    result should include("?") // Conditional indicator
  }

  test("render linear pipeline with layers") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("a", NodeKind.Input, "input", Some(0)),
        makeNode("b", NodeKind.Operation, "Process", Some(100)),
        makeNode("c", NodeKind.Output, "output", Some(200))
      ),
      edges = List(
        makeEdge("a", "b"),
        makeEdge("b", "c")
      )
    )

    val result = ASCIIRenderer.render(dag)

    // Should contain all node labels
    result should include("input")
    result should include("Process")
    result should include("output")

    // Should have connectors
    result should include("│")
    result should include("▼")
  }

  test("render nodes side by side in same layer") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("a", NodeKind.Input, "input1", Some(0)),
        makeNode("b", NodeKind.Input, "input2", Some(0))
      ),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    // Both nodes should be in the output
    result should include("input1")
    result should include("input2")

    // Should have legend
    result should include("Legend:")
  }

  test("render with title") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input, "data")),
      edges = List.empty,
      metadata = VizMetadata(title = Some("My Pipeline"))
    )

    val result = ASCIIRenderer.render(dag)

    result should include("=== My Pipeline ===")
  }

  test("truncate long labels") {
    val dag = DagVizIR(
      nodes = List(
        VizNode(
          "a",
          NodeKind.Operation,
          "ThisIsAVeryLongLabelThatShouldBeTruncated",
          "String",
          None,
          None
        )
      ),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    result should include("...")
  }

  test("render type signature") {
    val dag = DagVizIR(
      nodes = List(
        VizNode("a", NodeKind.Input, "data", "List<Int>", None, None)
      ),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    result should include("List<Int>")
  }

  test("render execution state") {
    val dag = DagVizIR(
      nodes = List(
        VizNode(
          "a",
          NodeKind.Operation,
          "running",
          "String",
          None,
          Some(ExecutionState(ExecutionStatus.Running))
        )
      ),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    result should include("[RUNNING]")
  }

  test("render completed execution state") {
    val dag = DagVizIR(
      nodes = List(
        VizNode(
          "a",
          NodeKind.Operation,
          "done",
          "String",
          None,
          Some(ExecutionState(ExecutionStatus.Completed))
        )
      ),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    result should include("[done]")
  }

  test("render failed execution state") {
    val dag = DagVizIR(
      nodes = List(
        VizNode(
          "a",
          NodeKind.Operation,
          "failed",
          "String",
          None,
          Some(ExecutionState(ExecutionStatus.Failed, error = Some("Error!")))
        )
      ),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    result should include("[FAILED]")
  }

  test("renders legend") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input)),
      edges = List.empty
    )

    val result = ASCIIRenderer.render(dag)

    result should include("Legend:")
    result should include("Input")
    result should include("Output")
    result should include("Merge")
    result should include("Conditional")
  }

  test("compute layers from topological sort when no positions") {
    // Nodes without positions should be organized by dependency order
    val dag = DagVizIR(
      nodes = List(
        VizNode("a", NodeKind.Input, "input", "String", None, None),
        VizNode("b", NodeKind.Operation, "middle", "String", None, None),
        VizNode("c", NodeKind.Output, "output", "String", None, None)
      ),
      edges = List(
        makeEdge("a", "b"),
        makeEdge("b", "c")
      )
    )

    val result = ASCIIRenderer.render(dag)

    // All nodes should appear
    result should include("input")
    result should include("middle")
    result should include("output")
  }

  test("file extension and mime type") {
    ASCIIRenderer.fileExtension shouldBe "txt"
    ASCIIRenderer.mimeType shouldBe "text/plain"
  }
}
