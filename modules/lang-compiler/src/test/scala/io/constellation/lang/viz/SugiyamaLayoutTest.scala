package io.constellation.lang.viz

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SugiyamaLayoutTest extends AnyFunSuite with Matchers {

  private def makeNode(id: String, kind: NodeKind = NodeKind.Operation): VizNode =
    VizNode(id, kind, s"Node$id", "String", None, None)

  private def makeEdge(source: String, target: String): VizEdge =
    VizEdge(s"e-$source-$target", source, target, None, EdgeKind.Data)

  test("layout single node") {
    val dag = DagVizIR(
      nodes = List(makeNode("A", NodeKind.Input)),
      edges = List.empty
    )

    val result = SugiyamaLayout.layout(dag)

    result.nodes should have length 1
    result.nodes.head.position shouldBe defined
    result.metadata.bounds shouldBe defined
  }

  test("layout linear pipeline A -> B -> C") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("A", NodeKind.Input),
        makeNode("B"),
        makeNode("C", NodeKind.Output)
      ),
      edges = List(
        makeEdge("A", "B"),
        makeEdge("B", "C")
      )
    )

    val result = SugiyamaLayout.layout(dag)

    // All nodes should have positions
    result.nodes.foreach { node =>
      node.position shouldBe defined
    }

    // Nodes should be in different layers (different y coordinates for TB layout)
    val positions = result.nodes.map(n => n.id -> n.position.get).toMap
    positions("A").y should be < positions("B").y
    positions("B").y should be < positions("C").y
  }

  test("layout diamond pattern") {
    // A -> B, A -> C, B -> D, C -> D
    val dag = DagVizIR(
      nodes = List(
        makeNode("A", NodeKind.Input),
        makeNode("B"),
        makeNode("C"),
        makeNode("D", NodeKind.Output)
      ),
      edges = List(
        makeEdge("A", "B"),
        makeEdge("A", "C"),
        makeEdge("B", "D"),
        makeEdge("C", "D")
      )
    )

    val result = SugiyamaLayout.layout(dag)

    val positions = result.nodes.map(n => n.id -> n.position.get).toMap

    // A should be at top (layer 0)
    // B and C should be at same layer (layer 1)
    // D should be at bottom (layer 2)
    positions("A").y should be < positions("B").y
    positions("A").y should be < positions("C").y
    positions("B").y shouldBe positions("C").y
    positions("B").y should be < positions("D").y
    positions("C").y should be < positions("D").y
  }

  test("layout respects left-right direction") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("A", NodeKind.Input),
        makeNode("B", NodeKind.Output)
      ),
      edges = List(makeEdge("A", "B"))
    )

    val config = LayoutConfig(direction = "LR")
    val result = SugiyamaLayout.layout(dag, config)

    val positions = result.nodes.map(n => n.id -> n.position.get).toMap

    // For LR, x increases instead of y
    positions("A").x should be < positions("B").x

    result.metadata.layoutDirection shouldBe "LR"
  }

  test("layout multiple inputs at same layer") {
    // A1, A2 -> B
    val dag = DagVizIR(
      nodes = List(
        makeNode("A1", NodeKind.Input),
        makeNode("A2", NodeKind.Input),
        makeNode("B", NodeKind.Output)
      ),
      edges = List(
        makeEdge("A1", "B"),
        makeEdge("A2", "B")
      )
    )

    val result = SugiyamaLayout.layout(dag)

    val positions = result.nodes.map(n => n.id -> n.position.get).toMap

    // A1 and A2 should be on same layer (same y)
    positions("A1").y shouldBe positions("A2").y
    // Both should be above B
    positions("A1").y should be < positions("B").y
    // A1 and A2 should have different x positions
    positions("A1").x should not be positions("A2").x
  }

  test("layout calculates correct bounds") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("A", NodeKind.Input),
        makeNode("B"),
        makeNode("C", NodeKind.Output)
      ),
      edges = List(
        makeEdge("A", "B"),
        makeEdge("B", "C")
      )
    )

    val result = SugiyamaLayout.layout(dag)

    result.metadata.bounds shouldBe defined
    val bounds = result.metadata.bounds.get

    // Bounds should encompass all nodes
    result.nodes.foreach { node =>
      val pos = node.position.get
      pos.x should be >= bounds.minX
      pos.x should be <= bounds.maxX
      pos.y should be >= bounds.minY
      pos.y should be <= bounds.maxY
    }
  }

  test("layout handles empty dag") {
    val dag = DagVizIR(nodes = List.empty, edges = List.empty)
    val result = SugiyamaLayout.layout(dag)
    result.nodes shouldBe empty
  }

  test("layout with custom config") {
    val dag = DagVizIR(
      nodes = List(
        makeNode("A", NodeKind.Input),
        makeNode("B", NodeKind.Output)
      ),
      edges = List(makeEdge("A", "B"))
    )

    val config = LayoutConfig(
      direction = "TB",
      nodeWidth = 200,
      nodeHeight = 80,
      nodeSpacing = 60,
      layerSpacing = 120
    )

    val result = SugiyamaLayout.layout(dag, config)

    val positions = result.nodes.map(n => n.id -> n.position.get).toMap

    // Layer spacing should affect y distance
    val yDiff = positions("B").y - positions("A").y
    yDiff should be >= config.layerSpacing
  }

  test("crossing minimization improves layout") {
    // Create a dag where naive ordering would have more crossings
    // A1 -> B2, A2 -> B1 (crossing if A1,A2 and B1,B2 in that order)
    val dag = DagVizIR(
      nodes = List(
        makeNode("A1", NodeKind.Input),
        makeNode("A2", NodeKind.Input),
        makeNode("B1"),
        makeNode("B2")
      ),
      edges = List(
        makeEdge("A1", "B2"),
        makeEdge("A2", "B1")
      )
    )

    val result = SugiyamaLayout.layout(dag)

    val positions = result.nodes.map(n => n.id -> n.position.get).toMap

    // After crossing minimization, if A1 is left of A2,
    // then B2 should be left of B1 (to minimize crossing)
    // Or vice versa
    val a1Left = positions("A1").x < positions("A2").x
    val b2Left = positions("B2").x < positions("B1").x

    // They should match (both left or both right) to avoid crossing
    a1Left shouldBe b2Left
  }
}
