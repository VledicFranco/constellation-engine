package io.constellation.lang.viz

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DOTRendererTest extends AnyFunSuite with Matchers {

  private def makeNode(
      id: String,
      kind: NodeKind = NodeKind.Operation,
      label: String = ""
  ): VizNode =
    VizNode(id, kind, if label.isEmpty then s"Node$id" else label, "String", None, None)

  private def makeEdge(source: String, target: String, kind: EdgeKind = EdgeKind.Data): VizEdge =
    VizEdge(s"e-$source-$target", source, target, None, kind)

  test("render valid DOT syntax") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input, "input")),
      edges = List.empty
    )

    val result = DOTRenderer.render(dag)

    result should startWith("digraph DAG {")
    result should endWith("}\n")
    result should include("rankdir=")
  }

  test("render empty dag") {
    val dag    = DagVizIR(nodes = List.empty, edges = List.empty)
    val result = DOTRenderer.render(dag)

    result should startWith("digraph DAG {")
    result should endWith("}\n")
  }

  test("render nodes with shapes") {
    val nodes = List(
      makeNode("input", NodeKind.Input, "data"),
      makeNode("output", NodeKind.Output, "result"),
      makeNode("op", NodeKind.Operation, "Process"),
      makeNode("merge", NodeKind.Merge, "join"),
      makeNode("cond", NodeKind.Conditional, "check")
    )

    val dag    = DagVizIR(nodes = nodes, edges = List.empty)
    val result = DOTRenderer.render(dag)

    result should include("shape=ellipse")       // Input
    result should include("shape=doubleoctagon") // Output
    result should include("shape=box")           // Operation
    result should include("shape=circle")        // Merge
    result should include("shape=diamond")       // Conditional
  }

  test("render node colors") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("a", NodeKind.Input),
        makeNode("b", NodeKind.Output)
      ),
      edges = List.empty
    )

    val result = DOTRenderer.render(dag)

    result should include("fillcolor=")
    result should include("color=")
    result should include("#22c55e") // Green for input
    result should include("#3b82f6") // Blue for output
  }

  test("render edges") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("a", NodeKind.Input),
        makeNode("b", NodeKind.Output)
      ),
      edges = List(makeEdge("a", "b"))
    )

    val result = DOTRenderer.render(dag)

    result should include("\"a\" -> \"b\"")
  }

  test("render edge labels") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("a", NodeKind.Input),
        makeNode("b", NodeKind.Operation)
      ),
      edges = List(
        VizEdge("e1", "a", "b", Some("param"), EdgeKind.Data)
      )
    )

    val result = DOTRenderer.render(dag)

    result should include("label=\"param\"")
  }

  test("render different edge styles") {
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

    val result = DOTRenderer.render(dag)

    result should include("style=dashed") // Optional edge
    result should include("style=bold")   // Control edge
  }

  test("render with title") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input)),
      edges = List.empty,
      metadata = VizMetadata(title = Some("My Pipeline"))
    )

    val result = DOTRenderer.render(dag)

    result should include("label=\"My Pipeline\"")
    result should include("labelloc=t")
  }

  test("render LR direction") {
    val dag = DagVizIR(
      nodes = List(makeNode("a", NodeKind.Input)),
      edges = List.empty,
      metadata = VizMetadata(layoutDirection = "LR")
    )

    val result = DOTRenderer.render(dag)

    result should include("rankdir=LR")
  }

  test("render groups as clusters") {
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

    val result = DOTRenderer.render(dag)

    result should include("subgraph cluster_group1")
    result should include("label=\"Processing\"")
  }

  test("escape quotes in labels") {
    val dag = DagVizIR(
      nodes = List(
        VizNode("a", NodeKind.Input, "data \"quoted\"", "String", None, None)
      ),
      edges = List.empty
    )

    val result = DOTRenderer.render(dag)

    result should include("\\\"quoted\\\"")
  }

  test("render execution state styling") {
    val dag = DagVizIR(
      nodes = List(
        VizNode(
          "a",
          NodeKind.Operation,
          "running",
          "String",
          None,
          Some(ExecutionState(ExecutionStatus.Running))
        ),
        VizNode(
          "b",
          NodeKind.Operation,
          "failed",
          "String",
          None,
          Some(ExecutionState(ExecutionStatus.Failed, error = Some("Error!")))
        )
      ),
      edges = List.empty
    )

    val result = DOTRenderer.render(dag)

    result should include("penwidth=3") // Running and Failed have thick borders
  }

  test("node labels include type signature") {
    val dag = DagVizIR(
      nodes = List(
        VizNode("a", NodeKind.Input, "data", "List<String>", None, None)
      ),
      edges = List.empty
    )

    val result = DOTRenderer.render(dag)

    // DOT uses \\n for newlines within labels
    result should include("data\\\\nList<String>")
  }

  test("abbreviate long type signatures") {
    val longType = "{ field1: String, field2: Int, field3: Boolean, field4: Float }"
    val dag = DagVizIR(
      nodes = List(
        VizNode("a", NodeKind.Input, "data", longType, None, None)
      ),
      edges = List.empty
    )

    val result = DOTRenderer.render(dag)

    result should include("...")
  }

  test("file extension and mime type") {
    DOTRenderer.fileExtension shouldBe "dot"
    DOTRenderer.mimeType shouldBe "text/vnd.graphviz"
  }
}
