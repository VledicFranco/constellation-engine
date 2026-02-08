package io.constellation.cli

import java.nio.file.Paths

import io.constellation.cli.commands.*

import com.monovore.decline.*
import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class DeployCommandTest extends AnyFunSuite with Matchers:

  // Helper to wrap Opts in a Command for parsing
  private def wrapCommand[A](opts: Opts[A]): Command[A] =
    Command("test", "test command")(opts)

  private def parseDeploy(args: String*): Either[Help, CliCommand] =
    wrapCommand(DeployCommand.command).parse(args.toList)

  // ============= Push Command Tests =============

  test("deploy push: parse with file"):
    val result = parseDeploy("deploy", "push", "pipeline.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployPush]
    cmd.file.toString shouldBe "pipeline.cst"
    cmd.name shouldBe None

  test("deploy push: parse with name option"):
    val result = parseDeploy("deploy", "push", "pipeline.cst", "--name", "my-pipeline")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployPush]
    cmd.file.toString shouldBe "pipeline.cst"
    cmd.name shouldBe Some("my-pipeline")

  test("deploy push: parse with short name flag"):
    val result = parseDeploy("deploy", "push", "pipeline.cst", "-n", "short-name")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployPush]
    cmd.name shouldBe Some("short-name")

  // ============= Canary Command Tests =============

  test("deploy canary: parse with file"):
    val result = parseDeploy("deploy", "canary", "pipeline.cst")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployCanary]
    cmd.file.toString shouldBe "pipeline.cst"
    cmd.percent shouldBe 10 // default

  test("deploy canary: parse with percent option"):
    val result = parseDeploy("deploy", "canary", "pipeline.cst", "--percent", "25")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployCanary]
    cmd.percent shouldBe 25

  test("deploy canary: parse with short percent flag"):
    val result = parseDeploy("deploy", "canary", "pipeline.cst", "-p", "5")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployCanary]
    cmd.percent shouldBe 5

  test("deploy canary: parse with name and percent"):
    val result = parseDeploy("deploy", "canary", "pipeline.cst", "-n", "test", "-p", "15")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployCanary]
    cmd.name shouldBe Some("test")
    cmd.percent shouldBe 15

  // ============= Promote Command Tests =============

  test("deploy promote: parse with pipeline name"):
    val result = parseDeploy("deploy", "promote", "my-pipeline")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployPromote]
    cmd.pipeline shouldBe "my-pipeline"

  // ============= Rollback Command Tests =============

  test("deploy rollback: parse with pipeline name"):
    val result = parseDeploy("deploy", "rollback", "my-pipeline")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployRollback]
    cmd.pipeline shouldBe "my-pipeline"
    cmd.version shouldBe None

  test("deploy rollback: parse with version option"):
    val result = parseDeploy("deploy", "rollback", "my-pipeline", "--version", "3")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployRollback]
    cmd.pipeline shouldBe "my-pipeline"
    cmd.version shouldBe Some(3)

  test("deploy rollback: parse with short version flag"):
    val result = parseDeploy("deploy", "rollback", "my-pipeline", "-v", "2")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployRollback]
    cmd.version shouldBe Some(2)

  // ============= Status Command Tests =============

  test("deploy status: parse with pipeline name"):
    val result = parseDeploy("deploy", "status", "my-pipeline")
    result shouldBe a[Right[?, ?]]
    val cmd = result.toOption.get.asInstanceOf[DeployCommand.DeployStatus]
    cmd.pipeline shouldBe "my-pipeline"

  // ============= Response Decoding Tests =============

  test("ReloadResponse: decode success"):
    val json = Json.obj(
      "success"      -> Json.fromBoolean(true),
      "previousHash" -> Json.fromString("abc123"),
      "newHash"      -> Json.fromString("def456"),
      "name"         -> Json.fromString("my-pipeline"),
      "changed"      -> Json.fromBoolean(true),
      "version"      -> Json.fromInt(2)
    )
    val result = json.as[DeployCommand.ReloadResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.success shouldBe true
    resp.name shouldBe "my-pipeline"
    resp.version shouldBe 2
    resp.changed shouldBe true

  test("ReloadResponse: decode with canary"):
    val json = Json.obj(
      "success"      -> Json.fromBoolean(true),
      "previousHash" -> Json.fromString("abc123"),
      "newHash"      -> Json.fromString("def456"),
      "name"         -> Json.fromString("my-pipeline"),
      "changed"      -> Json.fromBoolean(true),
      "version"      -> Json.fromInt(3),
      "canary" -> Json.obj(
        "pipelineName" -> Json.fromString("my-pipeline"),
        "oldVersion" -> Json.obj(
          "version"        -> Json.fromInt(2),
          "structuralHash" -> Json.fromString("abc123")
        ),
        "newVersion" -> Json.obj(
          "version"        -> Json.fromInt(3),
          "structuralHash" -> Json.fromString("def456")
        ),
        "currentWeight" -> Json.fromDoubleOrNull(0.1),
        "currentStep"   -> Json.fromInt(1),
        "status"        -> Json.fromString("observing"),
        "startedAt"     -> Json.fromString("2026-02-08T00:00:00Z")
      )
    )
    val result = json.as[DeployCommand.ReloadResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.canary shouldBe defined
    resp.canary.get.status shouldBe "observing"
    resp.canary.get.currentWeight shouldBe 0.1

  test("RollbackResponse: decode"):
    val json = Json.obj(
      "success"         -> Json.fromBoolean(true),
      "name"            -> Json.fromString("my-pipeline"),
      "previousVersion" -> Json.fromInt(3),
      "activeVersion"   -> Json.fromInt(2),
      "structuralHash"  -> Json.fromString("abc123")
    )
    val result = json.as[DeployCommand.RollbackResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.success shouldBe true
    resp.previousVersion shouldBe 3
    resp.activeVersion shouldBe 2

  test("PromoteResponse: decode"):
    val json = Json.obj(
      "success"        -> Json.fromBoolean(true),
      "pipelineName"   -> Json.fromString("my-pipeline"),
      "previousWeight" -> Json.fromDoubleOrNull(0.1),
      "newWeight"      -> Json.fromDoubleOrNull(0.25),
      "status"         -> Json.fromString("observing")
    )
    val result = json.as[DeployCommand.PromoteResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.previousWeight shouldBe 0.1
    resp.newWeight shouldBe 0.25

  test("CanaryStatusResponse: decode with metrics"):
    val json = Json.obj(
      "pipelineName" -> Json.fromString("my-pipeline"),
      "oldVersion" -> Json.obj(
        "version"        -> Json.fromInt(2),
        "structuralHash" -> Json.fromString("abc123")
      ),
      "newVersion" -> Json.obj(
        "version"        -> Json.fromInt(3),
        "structuralHash" -> Json.fromString("def456")
      ),
      "currentWeight" -> Json.fromDoubleOrNull(0.25),
      "currentStep"   -> Json.fromInt(2),
      "status"        -> Json.fromString("observing"),
      "startedAt"     -> Json.fromString("2026-02-08T00:00:00Z"),
      "metrics" -> Json.obj(
        "oldVersion" -> Json.obj(
          "requests"     -> Json.fromLong(950),
          "successes"    -> Json.fromLong(948),
          "failures"     -> Json.fromLong(2),
          "avgLatencyMs" -> Json.fromDoubleOrNull(12.3),
          "p99LatencyMs" -> Json.fromDoubleOrNull(45.0)
        ),
        "newVersion" -> Json.obj(
          "requests"     -> Json.fromLong(50),
          "successes"    -> Json.fromLong(49),
          "failures"     -> Json.fromLong(1),
          "avgLatencyMs" -> Json.fromDoubleOrNull(11.8),
          "p99LatencyMs" -> Json.fromDoubleOrNull(42.0)
        )
      )
    )
    val result = json.as[DeployCommand.CanaryStatusResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.currentWeight shouldBe 0.25
    resp.metrics shouldBe defined
    resp.metrics.get.oldVersion.requests shouldBe 950
    resp.metrics.get.newVersion.failures shouldBe 1
