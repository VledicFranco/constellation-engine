package io.constellation.cli

import io.circe.Json
import io.circe.parser.parse
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class OutputTest extends AnyFunSuite with Matchers:

  // ============= Success Message Tests =============

  test("success: human format includes checkmark"):
    val result = Output.success("Operation completed", OutputFormat.Human)
    result should include("✓")
    result should include("Operation completed")

  test("success: JSON format is valid JSON"):
    val result = Output.success("Done", OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("success").as[Boolean] shouldBe Right(true)
    json.toOption.get.hcursor.downField("message").as[String] shouldBe Right("Done")

  // ============= Error Message Tests =============

  test("error: human format includes X mark"):
    val result = Output.error("Something failed", OutputFormat.Human)
    result should include("✗")
    result should include("Something failed")

  test("error: JSON format is valid JSON"):
    val result = Output.error("Failed", OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("success").as[Boolean] shouldBe Right(false)
    json.toOption.get.hcursor.downField("error").as[String] shouldBe Right("Failed")

  // ============= Warning Message Tests =============

  test("warning: human format includes warning symbol"):
    val result = Output.warning("Deprecated feature", OutputFormat.Human)
    result should include("⚠")
    result should include("Deprecated feature")

  test("warning: JSON format is valid JSON"):
    val result = Output.warning("Warning text", OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("warning").as[String] shouldBe Right("Warning text")

  // ============= Compilation Errors Tests =============

  test("compilationErrors: human format shows all errors"):
    val errors = List("Error 1", "Error 2", "Error 3")
    val result = Output.compilationErrors(errors, OutputFormat.Human)
    result should include("✗")
    result should include("3 error(s)")
    errors.foreach(e => result should include(e))

  test("compilationErrors: JSON format is valid JSON array"):
    val errors = List("Type mismatch", "Undefined variable")
    val result = Output.compilationErrors(errors, OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("success").as[Boolean] shouldBe Right(false)
    json.toOption.get.hcursor.downField("errors").as[List[String]] shouldBe Right(errors)

  // ============= Outputs Tests =============

  test("outputs: human format shows key-value pairs"):
    val results = Map(
      "result" -> Json.fromString("hello"),
      "count"  -> Json.fromInt(42)
    )
    val result = Output.outputs(results, OutputFormat.Human)
    result should include("✓")
    result should include("result")
    result should include("count")

  test("outputs: empty results in human format"):
    val result = Output.outputs(Map.empty, OutputFormat.Human)
    result should include("No outputs")

  test("outputs: JSON format is valid JSON"):
    val results = Map(
      "greeting" -> Json.fromString("hello"),
      "number"   -> Json.fromInt(123)
    )
    val result = Output.outputs(results, OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("success").as[Boolean] shouldBe Right(true)
    json.toOption.get.hcursor.downField("outputs").downField("greeting").as[String] shouldBe Right(
      "hello"
    )
    json.toOption.get.hcursor.downField("outputs").downField("number").as[Int] shouldBe Right(123)

  // ============= Suspended Tests =============

  test("suspended: human format shows execution ID"):
    val result =
      Output.suspended("12345678-abcd-efgh-ijkl", Map("input1" -> "String"), OutputFormat.Human)
    result should include("⏸")
    result should include("12345678")
    result should include("input1")

  test("suspended: JSON format is valid JSON"):
    val missing = Map("text" -> "CString", "count" -> "CInt")
    val result  = Output.suspended("exec-id-123", missing, OutputFormat.Json)
    val json    = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("status").as[String] shouldBe Right("suspended")
    json.toOption.get.hcursor.downField("executionId").as[String] shouldBe Right("exec-id-123")

  // ============= DAG Visualization Tests =============

  test("dagDot: generates valid DOT format"):
    val nodes = List(
      ("n1", "Input", Nil),
      ("n2", "Process", List("n1")),
      ("n3", "Output", List("n2"))
    )
    val edges  = List(("n1", "n2"), ("n2", "n3"))
    val result = Output.dagDot(nodes, edges)

    result should startWith("digraph pipeline {")
    result should include("rankdir=LR")
    result should include("\"n1\"")
    result should include("\"n2\"")
    result should include("\"n1\" -> \"n2\"")
    result should endWith("}\n")

  test("dagMermaid: generates valid Mermaid format"):
    val nodes = List(
      ("n1", "Input", Nil),
      ("n2", "Output", List("n1"))
    )
    val edges  = List(("n1", "n2"))
    val result = Output.dagMermaid(nodes, edges)

    result should startWith("graph LR")
    result should include("n1[Input]")
    result should include("n2[Output]")
    result should include("n1 --> n2")

  // ============= DAG Label Escaping Tests =============

  test("dagDot: escapes quotes in labels"):
    val nodes = List(("n1", """say "hello"""", Nil))
    val edges = List.empty[(String, String)]
    val result = Output.dagDot(nodes, edges)
    result should include("""label="say \"hello\""""")
    result should not include """label="say "hello""""

  test("dagDot: escapes backslashes in labels"):
    val nodes = List(("n1", """path\to\file""", Nil))
    val edges = List.empty[(String, String)]
    val result = Output.dagDot(nodes, edges)
    result should include("""path\\to\\file""")

  test("dagMermaid: escapes brackets in labels"):
    val nodes = List(("n1", "List[Int]", Nil))
    val edges = List.empty[(String, String)]
    val result = Output.dagMermaid(nodes, edges)
    // Brackets should be replaced to avoid breaking Mermaid syntax
    result should not include "n1[List[Int]]"
    result should include("n1[List(Int)]")

  test("dagMermaid: escapes quotes in labels"):
    val nodes = List(("n1", """say "hi"""", Nil))
    val edges = List.empty[(String, String)]
    val result = Output.dagMermaid(nodes, edges)
    result should include("say 'hi'")

  test("dagDot: empty nodes and edges"):
    val result = Output.dagDot(Nil, Nil)
    result should startWith("digraph pipeline {")
    result should endWith("}\n")

  test("dagMermaid: empty nodes and edges"):
    val result = Output.dagMermaid(Nil, Nil)
    result should startWith("graph LR")

  // ============= Health Check Tests =============

  test("health: human format for healthy server"):
    val result = Output.health("ok", Some("0.5.0"), Some("1d 2h"), Some(10), OutputFormat.Human)
    result should include("✓")
    result should include("healthy")
    result should include("0.5.0")
    result should include("1d 2h")
    result should include("10 loaded")

  test("health: JSON format is valid JSON"):
    val result = Output.health("ok", Some("0.5.0"), None, Some(5), OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("status").as[String] shouldBe Right("ok")
    json.toOption.get.hcursor.downField("version").as[String] shouldBe Right("0.5.0")

  // ============= Config Show Tests =============

  test("configShow: human format shows all config"):
    val config = CliConfig(
      server = ServerConfig(url = "http://test.com", token = Some("secret")),
      defaults = DefaultsConfig(output = OutputFormat.Json, vizFormat = "mermaid")
    )
    val result = Output.configShow(config, OutputFormat.Human)
    result should include("Server")
    result should include("http://test.com")
    result should include("(set)")
    result should include("Defaults")
    result should include("mermaid")

  test("configShow: JSON format is valid JSON"):
    val config = CliConfig()
    val result = Output.configShow(config, OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("server").downField("url").as[String] shouldBe Right(
      "http://localhost:8080"
    )

  // ============= Connection Error Tests =============

  test("connectionError: human format shows URL and hint"):
    val result =
      Output.connectionError("http://localhost:8080", "Connection refused", OutputFormat.Human)
    result should include("✗")
    result should include("http://localhost:8080")
    result should include("Connection refused")
    result should include("Hint")

  test("connectionError: JSON format is valid JSON"):
    val result = Output.connectionError("http://test.com", "Timeout", OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("success").as[Boolean] shouldBe Right(false)
    json.toOption.get.hcursor.downField("error").as[String] shouldBe Right("connection_error")
    json.toOption.get.hcursor.downField("url").as[String] shouldBe Right("http://test.com")

  // ============= Info Message Tests =============

  test("info: human format includes info symbol"):
    val result = Output.info("Build started", OutputFormat.Human)
    result should include("ℹ")
    result should include("Build started")

  test("info: JSON format is valid JSON"):
    val result = Output.info("Processing", OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("info").as[String] shouldBe Right("Processing")

  // ============= Config Value Tests =============

  test("configValue: human format shows key = value"):
    val result = Output.configValue("server.url", Some("http://localhost:8080"), OutputFormat.Human)
    result should include("server.url")
    result should include("http://localhost:8080")

  test("configValue: human format shows warning for missing key"):
    val result = Output.configValue("server.unknown", None, OutputFormat.Human)
    result should include("not found")

  test("configValue: JSON format for existing value"):
    val result = Output.configValue("server.url", Some("http://test.com"), OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("key").as[String] shouldBe Right("server.url")
    json.toOption.get.hcursor.downField("value").as[String] shouldBe Right("http://test.com")

  test("configValue: JSON format for missing value"):
    val result = Output.configValue("missing.key", None, OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]

  // ============= Health Unhealthy Tests =============

  test("health: human format for unhealthy server"):
    val result = Output.health("error", None, None, None, OutputFormat.Human)
    result should include("✗")
    result should include("error")

  test("health: human format with minimal info"):
    val result = Output.health("ok", None, None, None, OutputFormat.Human)
    result should include("✓")

  // ============= Suspended Edge Cases =============

  test("suspended: human format with empty missing inputs"):
    val result = Output.suspended("exec-123", Map.empty, OutputFormat.Human)
    result should include("⏸")

  test("suspended: JSON format with empty missing inputs"):
    val result = Output.suspended("exec-456", Map.empty, OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("status").as[String] shouldBe Right("suspended")

  // ============= Outputs Edge Cases =============

  test("outputs: JSON format with empty results"):
    val result = Output.outputs(Map.empty, OutputFormat.Json)
    val json   = parse(result)
    json shouldBe a[Right[?, ?]]
    json.toOption.get.hcursor.downField("success").as[Boolean] shouldBe Right(true)

  // ============= Compilation Errors Edge Cases =============

  test("compilationErrors: human format single error"):
    val errors = List("Single error")
    val result = Output.compilationErrors(errors, OutputFormat.Human)
    result should include("1 error(s)")
    result should include("Single error")
