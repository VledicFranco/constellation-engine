package io.constellation.lang.viz

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DagRendererTest extends AnyFunSuite with Matchers {

  test("DagRenderer.forFormat returns correct renderer for mermaid") {
    DagRenderer.forFormat("mermaid") shouldBe Some(MermaidRenderer)
    DagRenderer.forFormat("mmd") shouldBe Some(MermaidRenderer)
    DagRenderer.forFormat("MERMAID") shouldBe Some(MermaidRenderer)
  }

  test("DagRenderer.forFormat returns correct renderer for dot") {
    DagRenderer.forFormat("dot") shouldBe Some(DOTRenderer)
    DagRenderer.forFormat("graphviz") shouldBe Some(DOTRenderer)
    DagRenderer.forFormat("DOT") shouldBe Some(DOTRenderer)
  }

  test("DagRenderer.forFormat returns correct renderer for ascii") {
    DagRenderer.forFormat("ascii") shouldBe Some(ASCIIRenderer)
    DagRenderer.forFormat("text") shouldBe Some(ASCIIRenderer)
    DagRenderer.forFormat("txt") shouldBe Some(ASCIIRenderer)
    DagRenderer.forFormat("ASCII") shouldBe Some(ASCIIRenderer)
  }

  test("DagRenderer.forFormat returns correct renderer for svg") {
    DagRenderer.forFormat("svg") shouldBe Some(SVGRenderer)
    DagRenderer.forFormat("SVG") shouldBe Some(SVGRenderer)
  }

  test("DagRenderer.forFormat returns None for unknown format") {
    DagRenderer.forFormat("unknown") shouldBe None
    DagRenderer.forFormat("pdf") shouldBe None
    DagRenderer.forFormat("") shouldBe None
  }

  test("DagRenderer companion object provides direct access to renderers") {
    DagRenderer.mermaid shouldBe MermaidRenderer
    DagRenderer.dot shouldBe DOTRenderer
    DagRenderer.ascii shouldBe ASCIIRenderer
    DagRenderer.svg shouldBe SVGRenderer
  }

  test("availableFormats lists all supported formats") {
    DagRenderer.availableFormats should contain("mermaid")
    DagRenderer.availableFormats should contain("dot")
    DagRenderer.availableFormats should contain("ascii")
    DagRenderer.availableFormats should contain("svg")
  }

  test("all renderers implement DagRenderer trait") {
    val renderers: List[DagRenderer] = List(
      MermaidRenderer,
      DOTRenderer,
      ASCIIRenderer,
      SVGRenderer
    )

    renderers.foreach { renderer =>
      renderer.fileExtension should not be empty
      renderer.mimeType should not be empty

      // Should be able to render an empty DAG
      val emptyDag = DagVizIR(List.empty, List.empty)
      val result = renderer.render(emptyDag)
      result should not be empty
    }
  }

  test("all renderers produce different output") {
    val dag = DagVizIR(
      nodes = List(
        VizNode("a", NodeKind.Input, "input", "String", None, None),
        VizNode("b", NodeKind.Output, "output", "String", None, None)
      ),
      edges = List(
        VizEdge("e1", "a", "b", None, EdgeKind.Data)
      )
    )

    val mermaidOutput = MermaidRenderer.render(dag)
    val dotOutput = DOTRenderer.render(dag)
    val asciiOutput = ASCIIRenderer.render(dag)
    val svgOutput = SVGRenderer.render(dag)

    // All outputs should be different
    mermaidOutput should not equal dotOutput
    dotOutput should not equal asciiOutput
    asciiOutput should not equal mermaidOutput
    svgOutput should not equal mermaidOutput
    svgOutput should not equal dotOutput
    svgOutput should not equal asciiOutput

    // Mermaid starts with "graph"
    mermaidOutput should startWith("graph")

    // DOT starts with "digraph"
    dotOutput should startWith("digraph")

    // ASCII has box characters
    asciiOutput should include("┌")

    // SVG starts with "<svg"
    svgOutput should startWith("<svg")
  }
}
