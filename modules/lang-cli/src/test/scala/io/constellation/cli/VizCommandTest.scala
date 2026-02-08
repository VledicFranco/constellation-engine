package io.constellation.cli

import java.nio.file.{Files, Path}

import io.constellation.cli.commands.{VizCommand, VizFormat}

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class VizCommandTest extends AnyFunSuite with Matchers with BeforeAndAfterEach:

  private var tempDir: Path = _

  override def beforeEach(): Unit =
    tempDir = Files.createTempDirectory("cli-viz-test")

  override def afterEach(): Unit =
    if tempDir != null && Files.exists(tempDir) then
      Files
        .walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.deleteIfExists)

  // ============= Command Construction Tests =============

  test("VizCommand: stores file path"):
    val file = tempDir.resolve("pipeline.cst")
    val cmd  = VizCommand(file)
    cmd.file shouldBe file
    cmd.format shouldBe VizFormat.Dot

  test("VizCommand: stores format"):
    val file = tempDir.resolve("pipeline.cst")
    val cmd  = VizCommand(file, format = VizFormat.Json)
    cmd.format shouldBe VizFormat.Json

  // ============= VizFormat Tests =============

  test("VizFormat: all values"):
    VizFormat.values should contain allOf (VizFormat.Dot, VizFormat.Json, VizFormat.Mermaid)

  // ============= DOT Output Tests =============

  test("dagDot: empty graph"):
    val result = Output.dagDot(Nil, Nil)
    result should include("digraph pipeline")
    result should include("rankdir=LR")

  test("dagDot: single node"):
    val nodes  = List(("n1", "Node1", Nil))
    val result = Output.dagDot(nodes, Nil)
    result should include("\"n1\"")
    result should include("Node1")

  test("dagDot: edge between nodes"):
    val nodes = List(
      ("n1", "Source", Nil),
      ("n2", "Sink", Nil)
    )
    val edges  = List(("n1", "n2"))
    val result = Output.dagDot(nodes, edges)
    result should include("\"n1\" -> \"n2\"")

  test("dagDot: multiple edges"):
    val nodes = List(
      ("a", "A", Nil),
      ("b", "B", Nil),
      ("c", "C", Nil)
    )
    val edges  = List(("a", "b"), ("a", "c"), ("b", "c"))
    val result = Output.dagDot(nodes, edges)
    result should include("\"a\" -> \"b\"")
    result should include("\"a\" -> \"c\"")
    result should include("\"b\" -> \"c\"")

  // ============= Mermaid Output Tests =============

  test("dagMermaid: empty graph"):
    val result = Output.dagMermaid(Nil, Nil)
    result should startWith("graph LR")

  test("dagMermaid: single node"):
    val nodes  = List(("n1", "Node1", Nil))
    val result = Output.dagMermaid(nodes, Nil)
    result should include("n1[Node1]")

  test("dagMermaid: edge between nodes"):
    val nodes = List(
      ("n1", "Source", Nil),
      ("n2", "Sink", Nil)
    )
    val edges  = List(("n1", "n2"))
    val result = Output.dagMermaid(nodes, edges)
    result should include("n1 --> n2")

  // ============= Response Parsing Tests =============

  test("CompileResponse: decode for viz"):
    val json = Json.obj(
      "success"        -> Json.True,
      "structuralHash" -> Json.fromString("hash123")
    )
    val result = json.as[VizCommand.CompileResponse]
    result shouldBe a[Right[?, ?]]
    result.toOption.get.structuralHash shouldBe Some("hash123")

  test("PipelineDetailResponse: decode"):
    val json = Json.obj(
      "structuralHash" -> Json.fromString("hash456"),
      "modules" -> Json.arr(
        Json.obj(
          "name"        -> Json.fromString("Uppercase"),
          "description" -> Json.fromString("Converts to uppercase"),
          "version"     -> Json.fromString("1.0"),
          "inputs"      -> Json.obj("text" -> Json.fromString("String")),
          "outputs"     -> Json.obj("result" -> Json.fromString("String"))
        )
      ),
      "declaredOutputs" -> Json.arr(Json.fromString("result")),
      "inputSchema"     -> Json.obj("text" -> Json.fromString("String")),
      "outputSchema"    -> Json.obj("result" -> Json.fromString("String"))
    )
    val result = json.as[VizCommand.PipelineDetailResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.structuralHash shouldBe "hash456"
    resp.modules should have size 1
    resp.modules.head.name shouldBe "Uppercase"
    resp.declaredOutputs shouldBe List("result")

  test("ModuleInfo: decode"):
    val json = Json.obj(
      "name"        -> Json.fromString("Transform"),
      "description" -> Json.fromString("Transforms data"),
      "version"     -> Json.fromString("2.0"),
      "inputs"      -> Json.obj("a" -> Json.fromString("Int"), "b" -> Json.fromString("Int")),
      "outputs"     -> Json.obj("sum" -> Json.fromString("Int"))
    )
    val result = json.as[VizCommand.ModuleInfo]
    result shouldBe a[Right[?, ?]]
    val info = result.toOption.get
    info.name shouldBe "Transform"
    info.inputs should have size 2
    info.outputs should have size 1
