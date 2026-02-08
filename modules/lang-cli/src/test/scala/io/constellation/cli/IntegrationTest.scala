package io.constellation.cli

import java.nio.file.{Files, Path}

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.Json

import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Integration tests for CLI operations.
  *
  * Note: These tests verify CLI logic without requiring a running server.
  * For full end-to-end tests, use the dashboard E2E test infrastructure.
  */
class IntegrationTest extends AnyFunSuite with Matchers with BeforeAndAfterEach:

  private var tempDir: Path = _

  override def beforeEach(): Unit =
    tempDir = Files.createTempDirectory("cli-integration-test")

  override def afterEach(): Unit =
    if tempDir != null && Files.exists(tempDir) then
      Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.deleteIfExists)

  // ============= Config Flow Tests =============

  test("config: default values are sensible"):
    val config = CliConfig()
    config.server.url shouldBe "http://localhost:8080"
    config.server.token shouldBe None
    config.defaults.output shouldBe OutputFormat.Human
    config.defaults.vizFormat shouldBe "dot"

  test("config: JSON round-trip preserves values"):
    val original = CliConfig(
      server = ServerConfig(
        url = "https://api.example.com:9090",
        token = Some("secret-token")
      ),
      defaults = DefaultsConfig(
        output = OutputFormat.Json,
        vizFormat = "mermaid"
      )
    )

    val json = io.circe.syntax.EncoderOps(original).asJson
    val decoded = json.as[CliConfig]

    decoded shouldBe Right(original)

  test("config: load applies precedence correctly"):
    // Config precedence: CLI flags > env vars > config file > defaults
    // This test verifies the load function applies overrides correctly

    val config = CliConfig.load(
      serverUrl = Some("http://cli-override:8080"),
      token = None,
      jsonOutput = true
    ).unsafeRunSync()

    config.server.url shouldBe "http://cli-override:8080"
    config.defaults.output shouldBe OutputFormat.Json

  // ============= File Operation Tests =============

  test("file: read pipeline source"):
    val file = tempDir.resolve("test-pipeline.cst")
    val source = """
      |# Test pipeline
      |in text: String
      |result = Uppercase(text)
      |out result
    """.stripMargin.trim

    Files.writeString(file, source)

    val content = Files.readString(file)
    content shouldBe source
    content should include("Uppercase")

  test("file: read JSON input file"):
    val file = tempDir.resolve("inputs.json")
    val inputJson = """
      |{
      |  "text": "hello world",
      |  "count": 42,
      |  "enabled": true
      |}
    """.stripMargin

    Files.writeString(file, inputJson)

    val content = Files.readString(file)
    val parsed = io.circe.parser.parse(content).flatMap(_.as[Map[String, Json]])

    parsed shouldBe a[Right[?, ?]]
    val inputs = parsed.toOption.get
    inputs("text") shouldBe Json.fromString("hello world")
    inputs("count") shouldBe Json.fromInt(42)
    inputs("enabled") shouldBe Json.True

  // ============= Output Format Tests =============

  test("output: success message in both formats"):
    val humanOutput = Output.success("Operation completed", OutputFormat.Human)
    val jsonOutput = Output.success("Operation completed", OutputFormat.Json)

    humanOutput should include("✓")
    humanOutput should include("Operation completed")

    val parsed = io.circe.parser.parse(jsonOutput)
    parsed shouldBe a[Right[?, ?]]
    parsed.toOption.get.hcursor.downField("success").as[Boolean] shouldBe Right(true)

  test("output: error message in both formats"):
    val humanOutput = Output.error("Something went wrong", OutputFormat.Human)
    val jsonOutput = Output.error("Something went wrong", OutputFormat.Json)

    humanOutput should include("✗")

    val parsed = io.circe.parser.parse(jsonOutput)
    parsed shouldBe a[Right[?, ?]]
    parsed.toOption.get.hcursor.downField("success").as[Boolean] shouldBe Right(false)

  test("output: compilation errors format"):
    val errors = List(
      "Syntax error at line 1: unexpected token",
      "Type error at line 3: expected Int, got String"
    )

    val humanOutput = Output.compilationErrors(errors, OutputFormat.Human)
    val jsonOutput = Output.compilationErrors(errors, OutputFormat.Json)

    humanOutput should include("2 error(s)")
    errors.foreach(e => humanOutput should include(e))

    val parsed = io.circe.parser.parse(jsonOutput)
    parsed.toOption.get.hcursor.downField("errors").as[List[String]] shouldBe Right(errors)

  // ============= DAG Visualization Tests =============

  test("viz: DOT output is valid graphviz"):
    val nodes = List(
      ("input_text", "text: String", Nil),
      ("module_0", "Uppercase", List("input_text")),
      ("output_result", "out: result", Nil)
    )
    val edges = List(
      ("input_text", "module_0"),
      ("module_0", "output_result")
    )

    val dot = Output.dagDot(nodes, edges)

    // Verify DOT structure
    dot should startWith("digraph pipeline {")
    dot should include("rankdir=LR")
    dot should include("\"input_text\"")
    dot should include("\"module_0\"")
    dot should include("\"input_text\" -> \"module_0\"")
    dot should endWith("}\n")

  test("viz: Mermaid output is valid"):
    val nodes = List(
      ("input_text", "text: String", Nil),
      ("module_0", "Transform", Nil)
    )
    val edges = List(("input_text", "module_0"))

    val mermaid = Output.dagMermaid(nodes, edges)

    mermaid should startWith("graph LR")
    mermaid should include("input_text[text: String]")
    mermaid should include("input_text --> module_0")

  // ============= Exit Code Tests =============

  test("exit codes: follow RFC-021 specification"):
    CliApp.ExitCodes.Success.code shouldBe 0
    CliApp.ExitCodes.CompileError.code shouldBe 1
    CliApp.ExitCodes.RuntimeError.code shouldBe 2
    CliApp.ExitCodes.ConnectionError.code shouldBe 3
    CliApp.ExitCodes.AuthError.code shouldBe 4
    CliApp.ExitCodes.NotFound.code shouldBe 5
    CliApp.ExitCodes.UsageError.code shouldBe 10

  // ============= Input Parsing Tests =============

  test("input parsing: various value types"):
    // String
    Output.outputs(Map("str" -> Json.fromString("hello")), OutputFormat.Human) should include("hello")

    // Number
    Output.outputs(Map("num" -> Json.fromInt(42)), OutputFormat.Human) should include("42")

    // Boolean
    Output.outputs(Map("bool" -> Json.True), OutputFormat.Human) should include("true")

    // Array
    val arr = Json.arr(Json.fromInt(1), Json.fromInt(2))
    Output.outputs(Map("arr" -> arr), OutputFormat.Human) should include("2 items")

    // Object
    val obj = Json.obj("key" -> Json.fromString("value"))
    Output.outputs(Map("obj" -> obj), OutputFormat.Human) should include("1 fields")

  // ============= Suspended Execution Tests =============

  test("suspended output: includes all info"):
    val execId = "12345678-1234-1234-1234-123456789abc"
    val missing = Map(
      "input1" -> "String",
      "input2" -> "Int"
    )

    val humanOutput = Output.suspended(execId, missing, OutputFormat.Human)
    val jsonOutput = Output.suspended(execId, missing, OutputFormat.Json)

    humanOutput should include("suspended")
    humanOutput should include("12345678")
    humanOutput should include("input1")
    humanOutput should include("input2")

    val parsed = io.circe.parser.parse(jsonOutput)
    parsed.toOption.get.hcursor.downField("status").as[String] shouldBe Right("suspended")
    parsed.toOption.get.hcursor.downField("executionId").as[String] shouldBe Right(execId)
