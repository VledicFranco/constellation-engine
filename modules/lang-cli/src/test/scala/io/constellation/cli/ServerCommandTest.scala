package io.constellation.cli

import io.constellation.cli.commands.*

import com.monovore.decline.*
import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ServerCommandTest extends AnyFunSuite with Matchers:

  // Helper to wrap Opts in a Command for parsing
  private def wrapCommand[A](opts: Opts[A]): Command[A] =
    Command("test", "test command")(opts)

  private def parseServer(args: String*): Either[Help, CliCommand] =
    wrapCommand(ServerCommand.command).parse(args.toList)

  // ============= Health Command Tests =============

  test("server health: parse"):
    val result = parseServer("server", "health")
    result shouldBe a[Right[?, ?]]
    result.toOption.get shouldBe a[ServerCommand.ServerHealth]

  // ============= Pipelines Command Tests =============

  test("server pipelines: parse list"):
    val result = parseServer("server", "pipelines")
    result shouldBe a[Right[?, ?]]
    result.toOption.get shouldBe ServerCommand.ServerPipelines(None)

  test("server pipelines show: parse with name"):
    val result = parseServer("server", "pipelines", "show", "my-pipeline")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[ServerCommand.ServerPipelines]
    cmd.showDetails shouldBe Some("my-pipeline")

  test("server pipelines show: parse with hash"):
    val result = parseServer("server", "pipelines", "show", "sha256:abc123")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[ServerCommand.ServerPipelines]
    cmd.showDetails shouldBe Some("sha256:abc123")

  // ============= Executions Command Tests =============

  test("server executions list: parse"):
    val result = parseServer("server", "executions", "list")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[ServerCommand.ServerExecutions]
    cmd.subcommand shouldBe a[ServerCommand.ExecutionsList]

  test("server executions list: parse with limit"):
    val result = parseServer("server", "executions", "list", "--limit", "10")
    result shouldBe a[Right[?, ?]]
    val cmd  = result.toOption.get.asInstanceOf[ServerCommand.ServerExecutions]
    val list = cmd.subcommand.asInstanceOf[ServerCommand.ExecutionsList]
    list.limit shouldBe Some(10)

  test("server executions list: parse with short limit flag"):
    val result = parseServer("server", "executions", "list", "-n", "5")
    result shouldBe a[Right[?, ?]]
    val cmd  = result.toOption.get.asInstanceOf[ServerCommand.ServerExecutions]
    val list = cmd.subcommand.asInstanceOf[ServerCommand.ExecutionsList]
    list.limit shouldBe Some(5)

  test("server executions show: parse"):
    val result = parseServer("server", "executions", "show", "exec-123")
    result shouldBe a[Right[?, ?]]
    val cmd  = result.toOption.get.asInstanceOf[ServerCommand.ServerExecutions]
    val show = cmd.subcommand.asInstanceOf[ServerCommand.ExecutionsShow]
    show.id shouldBe "exec-123"

  test("server executions delete: parse"):
    val result = parseServer("server", "executions", "delete", "exec-456")
    result shouldBe a[Right[?, ?]]
    val cmd    = result.toOption.get.asInstanceOf[ServerCommand.ServerExecutions]
    val delete = cmd.subcommand.asInstanceOf[ServerCommand.ExecutionsDelete]
    delete.id shouldBe "exec-456"

  // ============= Metrics Command Tests =============

  test("server metrics: parse"):
    val result = parseServer("server", "metrics")
    result shouldBe a[Right[?, ?]]
    result.toOption.get shouldBe a[ServerCommand.ServerMetrics]

  // ============= Response Parsing Tests =============

  test("HealthResponse: decode"):
    val json   = Json.obj("status" -> Json.fromString("ok"))
    val result = json.as[ServerCommand.HealthResponse]
    result shouldBe a[Right[?, ?]]
    result.toOption.get.status shouldBe "ok"

  test("PipelineSummary: decode"):
    val json = Json.obj(
      "structuralHash"  -> Json.fromString("abc123"),
      "syntacticHash"   -> Json.fromString("def456"),
      "aliases"         -> Json.arr(Json.fromString("my-pipeline")),
      "compiledAt"      -> Json.fromString("2026-02-08T00:00:00Z"),
      "moduleCount"     -> Json.fromInt(3),
      "declaredOutputs" -> Json.arr(Json.fromString("result"))
    )
    val result = json.as[ServerCommand.PipelineSummary]
    result shouldBe a[Right[?, ?]]
    val summary = result.toOption.get
    summary.structuralHash shouldBe "abc123"
    summary.aliases shouldBe List("my-pipeline")
    summary.moduleCount shouldBe 3

  test("PipelineListResponse: decode"):
    val json = Json.obj(
      "pipelines" -> Json.arr(
        Json.obj(
          "structuralHash"  -> Json.fromString("hash1"),
          "syntacticHash"   -> Json.fromString("syn1"),
          "compiledAt"      -> Json.fromString("2026-02-08T00:00:00Z"),
          "moduleCount"     -> Json.fromInt(2),
          "declaredOutputs" -> Json.arr()
        )
      )
    )
    val result = json.as[ServerCommand.PipelineListResponse]
    result shouldBe a[Right[?, ?]]
    result.toOption.get.pipelines should have size 1

  test("ExecutionSummary: decode"):
    val json = Json.obj(
      "executionId"     -> Json.fromString("exec-123"),
      "structuralHash"  -> Json.fromString("hash-456"),
      "resumptionCount" -> Json.fromInt(2),
      "missingInputs"   -> Json.obj("text" -> Json.fromString("String")),
      "createdAt"       -> Json.fromString("2026-02-08T00:00:00Z")
    )
    val result = json.as[ServerCommand.ExecutionSummary]
    result shouldBe a[Right[?, ?]]
    val exec = result.toOption.get
    exec.executionId shouldBe "exec-123"
    exec.resumptionCount shouldBe 2
    exec.missingInputs shouldBe Map("text" -> "String")

  test("ExecutionListResponse: decode"):
    val json = Json.obj(
      "executions" -> Json.arr(
        Json.obj(
          "executionId"     -> Json.fromString("exec-1"),
          "structuralHash"  -> Json.fromString("hash-1"),
          "resumptionCount" -> Json.fromInt(0),
          "missingInputs"   -> Json.obj(),
          "createdAt"       -> Json.fromString("2026-02-08T00:00:00Z")
        )
      )
    )
    val result = json.as[ServerCommand.ExecutionListResponse]
    result shouldBe a[Right[?, ?]]
    result.toOption.get.executions should have size 1

  test("ModuleInfo: decode"):
    val json = Json.obj(
      "name"        -> Json.fromString("Uppercase"),
      "description" -> Json.fromString("Converts text to uppercase"),
      "version"     -> Json.fromString("1.0"),
      "inputs"      -> Json.obj("text" -> Json.fromString("String")),
      "outputs"     -> Json.obj("result" -> Json.fromString("String"))
    )
    val result = json.as[ServerCommand.ModuleInfo]
    result shouldBe a[Right[?, ?]]
    val info = result.toOption.get
    info.name shouldBe "Uppercase"
    info.inputs shouldBe Map("text" -> "String")

  test("PipelineDetailResponse: decode"):
    val json = Json.obj(
      "structuralHash" -> Json.fromString("abc123"),
      "syntacticHash"  -> Json.fromString("def456"),
      "aliases"        -> Json.arr(Json.fromString("test")),
      "compiledAt"     -> Json.fromString("2026-02-08T00:00:00Z"),
      "modules" -> Json.arr(
        Json.obj(
          "name"        -> Json.fromString("Transform"),
          "description" -> Json.fromString("Transforms data"),
          "version"     -> Json.fromString("1.0"),
          "inputs"      -> Json.obj(),
          "outputs"     -> Json.obj()
        )
      ),
      "declaredOutputs" -> Json.arr(Json.fromString("result")),
      "inputSchema"     -> Json.obj("x" -> Json.fromString("Int")),
      "outputSchema"    -> Json.obj("result" -> Json.fromString("String"))
    )
    val result = json.as[ServerCommand.PipelineDetailResponse]
    result shouldBe a[Right[?, ?]]
    val detail = result.toOption.get
    detail.structuralHash shouldBe "abc123"
    detail.modules should have size 1
    detail.inputSchema shouldBe Map("x" -> "Int")
