package io.constellation.cli.commands

import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets

import cats.effect.{ExitCode, IO}
import cats.implicits.*

import com.monovore.decline.*

import io.circe.{Decoder, Json}
import io.circe.syntax.*

import io.constellation.cli.{CliApp, HttpClient, Output, OutputFormat, StringUtils}

import org.http4s.Uri
import org.http4s.client.Client

object DeployCommand:

  // ============= Push Command =============

  case class DeployPush(
      file: Path,
      name: Option[String] = None
  ) extends CliCommand

  // ============= Canary Command =============

  case class DeployCanary(
      file: Path,
      name: Option[String] = None,
      percent: Int = 10
  ) extends CliCommand

  // ============= Promote Command =============

  case class DeployPromote(
      pipeline: String
  ) extends CliCommand

  // ============= Rollback Command =============

  case class DeployRollback(
      pipeline: String,
      version: Option[Int] = None
  ) extends CliCommand

  // ============= Status Command =============

  case class DeployStatus(
      pipeline: String
  ) extends CliCommand

  // ============= Response Types =============

  case class ReloadResponse(
      success: Boolean,
      previousHash: Option[String],
      newHash: String,
      name: String,
      changed: Boolean,
      version: Int,
      canary: Option[CanaryStateResponse] = None
  )

  object ReloadResponse:
    given Decoder[ReloadResponse] = Decoder.instance { c =>
      for
        success      <- c.downField("success").as[Boolean]
        previousHash <- c.downField("previousHash").as[Option[String]]
        newHash      <- c.downField("newHash").as[String]
        name         <- c.downField("name").as[String]
        changed      <- c.downField("changed").as[Boolean]
        version      <- c.downField("version").as[Int]
        canary       <- c.downField("canary").as[Option[CanaryStateResponse]]
      yield ReloadResponse(success, previousHash, newHash, name, changed, version, canary)
    }

  case class CanaryStateResponse(
      pipelineName: String,
      oldVersion: CanaryVersionInfo,
      newVersion: CanaryVersionInfo,
      currentWeight: Double,
      currentStep: Int,
      status: String,
      startedAt: String
  )

  object CanaryStateResponse:
    given Decoder[CanaryStateResponse] = Decoder.instance { c =>
      for
        pipelineName  <- c.downField("pipelineName").as[String]
        oldVersion    <- c.downField("oldVersion").as[CanaryVersionInfo]
        newVersion    <- c.downField("newVersion").as[CanaryVersionInfo]
        currentWeight <- c.downField("currentWeight").as[Double]
        currentStep   <- c.downField("currentStep").as[Int]
        status        <- c.downField("status").as[String]
        startedAt     <- c.downField("startedAt").as[String]
      yield CanaryStateResponse(pipelineName, oldVersion, newVersion, currentWeight, currentStep, status, startedAt)
    }

  case class CanaryVersionInfo(
      version: Int,
      structuralHash: String
  )

  object CanaryVersionInfo:
    given Decoder[CanaryVersionInfo] = Decoder.instance { c =>
      for
        version        <- c.downField("version").as[Int]
        structuralHash <- c.downField("structuralHash").as[String]
      yield CanaryVersionInfo(version, structuralHash)
    }

  case class RollbackResponse(
      success: Boolean,
      name: String,
      previousVersion: Int,
      activeVersion: Int,
      structuralHash: String
  )

  object RollbackResponse:
    given Decoder[RollbackResponse] = Decoder.instance { c =>
      for
        success         <- c.downField("success").as[Boolean]
        name            <- c.downField("name").as[String]
        previousVersion <- c.downField("previousVersion").as[Int]
        activeVersion   <- c.downField("activeVersion").as[Int]
        structuralHash  <- c.downField("structuralHash").as[String]
      yield RollbackResponse(success, name, previousVersion, activeVersion, structuralHash)
    }

  case class PromoteResponse(
      success: Boolean,
      pipelineName: String,
      previousWeight: Double,
      newWeight: Double,
      status: String
  )

  object PromoteResponse:
    given Decoder[PromoteResponse] = Decoder.instance { c =>
      for
        success        <- c.downField("success").as[Boolean]
        pipelineName   <- c.downField("pipelineName").as[String]
        previousWeight <- c.downField("previousWeight").as[Double]
        newWeight      <- c.downField("newWeight").as[Double]
        status         <- c.downField("status").as[String]
      yield PromoteResponse(success, pipelineName, previousWeight, newWeight, status)
    }

  // ============= Command Definitions =============

  private val fileArg = Opts.argument[Path](metavar = "file.cst")

  private val nameOpt = Opts.option[String](
    "name",
    short = "n",
    help = "Pipeline name (default: filename without extension)"
  ).orNone

  private val percentOpt = Opts.option[Int](
    "percent",
    short = "p",
    help = "Initial canary traffic percentage (default: 10)"
  ).withDefault(10)

  private val versionOpt = Opts.option[Int](
    "version",
    short = "v",
    help = "Specific version to rollback to"
  ).orNone

  private val pipelineArg = Opts.argument[String](metavar = "pipeline")

  private val pushCmd = Opts.subcommand(
    name = "push",
    help = "Deploy a pipeline to the server"
  ) {
    (fileArg, nameOpt).mapN(DeployPush.apply)
  }

  private val canaryCmd = Opts.subcommand(
    name = "canary",
    help = "Deploy a pipeline as a canary"
  ) {
    (fileArg, nameOpt, percentOpt).mapN(DeployCanary.apply)
  }

  private val promoteCmd = Opts.subcommand(
    name = "promote",
    help = "Promote a canary deployment to stable"
  ) {
    pipelineArg.map(DeployPromote.apply)
  }

  private val rollbackCmd = Opts.subcommand(
    name = "rollback",
    help = "Rollback a pipeline to a previous version"
  ) {
    (pipelineArg, versionOpt).mapN(DeployRollback.apply)
  }

  private val statusCmd = Opts.subcommand(
    name = "status",
    help = "Show canary deployment status"
  ) {
    pipelineArg.map(DeployStatus.apply)
  }

  val command: Opts[CliCommand] = Opts.subcommand(
    name = "deploy",
    help = "Pipeline deployment operations"
  ) {
    pushCmd orElse canaryCmd orElse promoteCmd orElse rollbackCmd orElse statusCmd
  }

  // ============= Execution =============

  def execute(
      cmd: CliCommand,
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat,
      quiet: Boolean
  )(using client: Client[IO]): IO[ExitCode] =
    cmd match
      case DeployPush(file, name) =>
        executePush(file, name, baseUri, token, format, quiet)

      case DeployCanary(file, name, percent) =>
        executeCanary(file, name, percent, baseUri, token, format, quiet)

      case DeployPromote(pipeline) =>
        executePromote(pipeline, baseUri, token, format)

      case DeployRollback(pipeline, version) =>
        executeRollback(pipeline, version, baseUri, token, format)

      case DeployStatus(pipeline) =>
        executeStatus(pipeline, baseUri, token, format)

      case _ =>
        IO.println(Output.error("Unknown deploy command", format)).as(CliApp.ExitCodes.UsageError)

  // ============= Push =============

  private def executePush(
      file: Path,
      nameOpt: Option[String],
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat,
      quiet: Boolean
  )(using client: Client[IO]): IO[ExitCode] =
    readSourceFile(file).flatMap {
      case Left(err) =>
        IO.println(Output.error(err, format)).as(CliApp.ExitCodes.UsageError)

      case Right(source) =>
        val name = nameOpt.getOrElse(deriveName(file))
        val uri  = baseUri / "pipelines" / name / "reload"
        val body = Json.obj("source" -> Json.fromString(source))

        HttpClient.post[ReloadResponse](uri, body, token).flatMap {
          case HttpClient.Success(resp) =>
            val output = formatPushResult(resp, format)
            IO.println(output).as(CliApp.ExitCodes.Success)

          case HttpClient.ConnectionError(msg) =>
            IO.println(Output.connectionError(baseUri.renderString, msg, format))
              .as(CliApp.ExitCodes.ConnectionError)

          case HttpClient.ApiError(400, msg) =>
            IO.println(Output.error(s"Compilation failed: $msg", format))
              .as(CliApp.ExitCodes.CompileError)

          case HttpClient.ApiError(401, _) =>
            IO.println(Output.error("Authentication required", format))
              .as(CliApp.ExitCodes.AuthError)

          case HttpClient.ApiError(_, msg) =>
            IO.println(Output.error(msg, format)).as(CliApp.ExitCodes.RuntimeError)

          case HttpClient.ParseError(msg) =>
            IO.println(Output.error(s"Failed to parse response: $msg", format))
              .as(CliApp.ExitCodes.RuntimeError)
        }
    }

  private def formatPushResult(resp: ReloadResponse, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        if resp.changed then
          val sb = new StringBuilder
          sb.append(s"${fansi.Color.Green("✓")} Deployed ${fansi.Bold.On(resp.name)} v${resp.version}\n")
          sb.append(s"  Hash: ${StringUtils.hashPreview(resp.newHash)}\n")
          resp.previousHash.foreach { prev =>
            sb.append(s"  Previous: ${StringUtils.hashPreview(prev)}\n")
          }
          sb.toString.trim
        else
          s"${fansi.Color.Yellow("○")} No changes to ${fansi.Bold.On(resp.name)} (already at v${resp.version})"

      case OutputFormat.Json =>
        Json.obj(
          "success"      -> Json.fromBoolean(resp.success),
          "name"         -> Json.fromString(resp.name),
          "version"      -> Json.fromInt(resp.version),
          "changed"      -> Json.fromBoolean(resp.changed),
          "hash"         -> Json.fromString(resp.newHash),
          "previousHash" -> resp.previousHash.fold(Json.Null)(Json.fromString)
        ).noSpaces

  // ============= Canary =============

  private def executeCanary(
      file: Path,
      nameOpt: Option[String],
      percent: Int,
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat,
      quiet: Boolean
  )(using client: Client[IO]): IO[ExitCode] =
    readSourceFile(file).flatMap {
      case Left(err) =>
        IO.println(Output.error(err, format)).as(CliApp.ExitCodes.UsageError)

      case Right(source) =>
        val name   = nameOpt.getOrElse(deriveName(file))
        val uri    = baseUri / "pipelines" / name / "reload"
        val weight = percent.toDouble / 100.0
        val body = Json.obj(
          "source" -> Json.fromString(source),
          "canary" -> Json.obj(
            "initialWeight" -> Json.fromDoubleOrNull(weight),
            "autoPromote"   -> Json.fromBoolean(true)
          )
        )

        HttpClient.post[ReloadResponse](uri, body, token).flatMap {
          case HttpClient.Success(resp) =>
            val output = formatCanaryResult(resp, percent, format)
            IO.println(output).as(CliApp.ExitCodes.Success)

          case HttpClient.ConnectionError(msg) =>
            IO.println(Output.connectionError(baseUri.renderString, msg, format))
              .as(CliApp.ExitCodes.ConnectionError)

          case HttpClient.ApiError(400, msg) =>
            IO.println(Output.error(s"Deployment failed: $msg", format))
              .as(CliApp.ExitCodes.CompileError)

          case HttpClient.ApiError(401, _) =>
            IO.println(Output.error("Authentication required", format))
              .as(CliApp.ExitCodes.AuthError)

          case HttpClient.ApiError(409, _) =>
            IO.println(Output.error("A canary deployment is already active for this pipeline", format))
              .as(CliApp.ExitCodes.Conflict)

          case HttpClient.ApiError(_, msg) =>
            IO.println(Output.error(msg, format)).as(CliApp.ExitCodes.RuntimeError)

          case HttpClient.ParseError(msg) =>
            IO.println(Output.error(s"Failed to parse response: $msg", format))
              .as(CliApp.ExitCodes.RuntimeError)
        }
    }

  private def formatCanaryResult(resp: ReloadResponse, percent: Int, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        resp.canary match
          case Some(canary) =>
            val sb = new StringBuilder
            sb.append(s"${fansi.Color.Green("✓")} Canary started for ${fansi.Bold.On(resp.name)}\n")
            sb.append(s"  New version: v${canary.newVersion.version} (${StringUtils.hashPreview(canary.newVersion.structuralHash)})\n")
            sb.append(s"  Old version: v${canary.oldVersion.version} (${StringUtils.hashPreview(canary.oldVersion.structuralHash)})\n")
            sb.append(s"  Traffic: ${percent}% to new version\n")
            sb.append(s"  Status: ${canary.status}")
            sb.toString

          case None =>
            if resp.changed then
              s"${fansi.Color.Yellow("!")} Deployed ${fansi.Bold.On(resp.name)} v${resp.version} (no previous version for canary)"
            else
              s"${fansi.Color.Yellow("○")} No changes to ${fansi.Bold.On(resp.name)}"

      case OutputFormat.Json =>
        val canaryJson = resp.canary.fold(Json.Null) { c =>
          Json.obj(
            "status"      -> Json.fromString(c.status),
            "oldVersion"  -> Json.fromInt(c.oldVersion.version),
            "newVersion"  -> Json.fromInt(c.newVersion.version),
            "weight"      -> Json.fromDoubleOrNull(c.currentWeight)
          )
        }
        Json.obj(
          "success" -> Json.fromBoolean(resp.success),
          "name"    -> Json.fromString(resp.name),
          "version" -> Json.fromInt(resp.version),
          "changed" -> Json.fromBoolean(resp.changed),
          "canary"  -> canaryJson
        ).noSpaces

  // ============= Promote =============

  private def executePromote(
      pipeline: String,
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = baseUri / "pipelines" / pipeline / "canary" / "promote"

    HttpClient.post[PromoteResponse](uri, Json.obj(), token).flatMap {
      case HttpClient.Success(resp) =>
        val output = formatPromoteResult(resp, format)
        IO.println(output).as(CliApp.ExitCodes.Success)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ApiError(404, _) =>
        IO.println(Output.error(s"No active canary deployment for '$pipeline'", format))
          .as(CliApp.ExitCodes.NotFound)

      case HttpClient.ApiError(401, _) =>
        IO.println(Output.error("Authentication required", format))
          .as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(_, msg) =>
        IO.println(Output.error(msg, format)).as(CliApp.ExitCodes.RuntimeError)

      case HttpClient.ParseError(msg) =>
        IO.println(Output.error(s"Failed to parse response: $msg", format))
          .as(CliApp.ExitCodes.RuntimeError)
    }

  private def formatPromoteResult(resp: PromoteResponse, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        val prevPct = (resp.previousWeight * 100).toInt
        val newPct  = (resp.newWeight * 100).toInt
        if resp.status == "complete" then
          s"${fansi.Color.Green("✓")} Canary ${fansi.Bold.On(resp.pipelineName)} promoted to 100% (complete)"
        else
          s"${fansi.Color.Green("✓")} Canary ${fansi.Bold.On(resp.pipelineName)} promoted: $prevPct% → $newPct%"

      case OutputFormat.Json =>
        Json.obj(
          "success"        -> Json.fromBoolean(resp.success),
          "pipelineName"   -> Json.fromString(resp.pipelineName),
          "previousWeight" -> Json.fromDoubleOrNull(resp.previousWeight),
          "newWeight"      -> Json.fromDoubleOrNull(resp.newWeight),
          "status"         -> Json.fromString(resp.status)
        ).noSpaces

  // ============= Rollback =============

  private def executeRollback(
      pipeline: String,
      versionOpt: Option[Int],
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = versionOpt match
      case Some(v) => baseUri / "pipelines" / pipeline / "rollback" / v.toString
      case None    => baseUri / "pipelines" / pipeline / "rollback"

    HttpClient.post[RollbackResponse](uri, Json.obj(), token).flatMap {
      case HttpClient.Success(resp) =>
        val output = formatRollbackResult(resp, format)
        IO.println(output).as(CliApp.ExitCodes.Success)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ApiError(404, msg) =>
        IO.println(Output.error(msg, format))
          .as(CliApp.ExitCodes.NotFound)

      case HttpClient.ApiError(401, _) =>
        IO.println(Output.error("Authentication required", format))
          .as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(_, msg) =>
        IO.println(Output.error(msg, format)).as(CliApp.ExitCodes.RuntimeError)

      case HttpClient.ParseError(msg) =>
        IO.println(Output.error(s"Failed to parse response: $msg", format))
          .as(CliApp.ExitCodes.RuntimeError)
    }

  private def formatRollbackResult(resp: RollbackResponse, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        val sb = new StringBuilder
        sb.append(s"${fansi.Color.Green("✓")} Rolled back ${fansi.Bold.On(resp.name)}\n")
        sb.append(s"  From: v${resp.previousVersion}\n")
        sb.append(s"  To: v${resp.activeVersion}\n")
        sb.append(s"  Hash: ${StringUtils.hashPreview(resp.structuralHash)}")
        sb.toString

      case OutputFormat.Json =>
        Json.obj(
          "success"         -> Json.fromBoolean(resp.success),
          "name"            -> Json.fromString(resp.name),
          "previousVersion" -> Json.fromInt(resp.previousVersion),
          "activeVersion"   -> Json.fromInt(resp.activeVersion),
          "structuralHash"  -> Json.fromString(resp.structuralHash)
        ).noSpaces

  // ============= Status =============

  private def executeStatus(
      pipeline: String,
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = baseUri / "pipelines" / pipeline / "canary"

    HttpClient.get[CanaryStatusResponse](uri, token).flatMap {
      case HttpClient.Success(resp) =>
        val output = formatStatusResult(resp, format)
        IO.println(output).as(CliApp.ExitCodes.Success)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ApiError(404, _) =>
        IO.println(Output.error(s"No active canary deployment for '$pipeline'", format))
          .as(CliApp.ExitCodes.NotFound)

      case HttpClient.ApiError(401, _) =>
        IO.println(Output.error("Authentication required", format))
          .as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(_, msg) =>
        IO.println(Output.error(msg, format)).as(CliApp.ExitCodes.RuntimeError)

      case HttpClient.ParseError(msg) =>
        IO.println(Output.error(s"Failed to parse response: $msg", format))
          .as(CliApp.ExitCodes.RuntimeError)
    }

  case class CanaryStatusResponse(
      pipelineName: String,
      oldVersion: CanaryVersionInfo,
      newVersion: CanaryVersionInfo,
      currentWeight: Double,
      currentStep: Int,
      status: String,
      startedAt: String,
      metrics: Option[CanaryMetrics] = None
  )

  case class CanaryMetrics(
      oldVersion: VersionMetrics,
      newVersion: VersionMetrics
  )

  case class VersionMetrics(
      requests: Long,
      successes: Long,
      failures: Long,
      avgLatencyMs: Double,
      p99LatencyMs: Double
  )

  object VersionMetrics:
    given Decoder[VersionMetrics] = Decoder.instance { c =>
      for
        requests     <- c.downField("requests").as[Long]
        successes    <- c.downField("successes").as[Long]
        failures     <- c.downField("failures").as[Long]
        avgLatencyMs <- c.downField("avgLatencyMs").as[Double]
        p99LatencyMs <- c.downField("p99LatencyMs").as[Double]
      yield VersionMetrics(requests, successes, failures, avgLatencyMs, p99LatencyMs)
    }

  object CanaryMetrics:
    given Decoder[CanaryMetrics] = Decoder.instance { c =>
      for
        oldVersion <- c.downField("oldVersion").as[VersionMetrics]
        newVersion <- c.downField("newVersion").as[VersionMetrics]
      yield CanaryMetrics(oldVersion, newVersion)
    }

  object CanaryStatusResponse:
    given Decoder[CanaryStatusResponse] = Decoder.instance { c =>
      for
        pipelineName  <- c.downField("pipelineName").as[String]
        oldVersion    <- c.downField("oldVersion").as[CanaryVersionInfo]
        newVersion    <- c.downField("newVersion").as[CanaryVersionInfo]
        currentWeight <- c.downField("currentWeight").as[Double]
        currentStep   <- c.downField("currentStep").as[Int]
        status        <- c.downField("status").as[String]
        startedAt     <- c.downField("startedAt").as[String]
        metrics       <- c.downField("metrics").as[Option[CanaryMetrics]]
      yield CanaryStatusResponse(pipelineName, oldVersion, newVersion, currentWeight, currentStep, status, startedAt, metrics)
    }

  private def formatStatusResult(resp: CanaryStatusResponse, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        val sb = new StringBuilder
        val weightPct = (resp.currentWeight * 100).toInt
        sb.append(s"${fansi.Bold.On("Canary Deployment:")} ${resp.pipelineName}\n\n")
        sb.append(s"  ${fansi.Bold.On("Status:")} ${resp.status}\n")
        sb.append(s"  ${fansi.Bold.On("Traffic:")} ${weightPct}% to new version (step ${resp.currentStep})\n")
        sb.append(s"  ${fansi.Bold.On("Started:")} ${resp.startedAt}\n\n")
        sb.append(s"  ${fansi.Bold.On("Old version:")} v${resp.oldVersion.version} (${StringUtils.hashPreview(resp.oldVersion.structuralHash)})\n")
        sb.append(s"  ${fansi.Bold.On("New version:")} v${resp.newVersion.version} (${StringUtils.hashPreview(resp.newVersion.structuralHash)})\n")

        resp.metrics.foreach { m =>
          sb.append(s"\n${fansi.Bold.On("Metrics:")}\n")
          sb.append(s"  Old version: ${m.oldVersion.requests} reqs, ")
          if m.oldVersion.requests > 0 then
            val errorRate = m.oldVersion.failures.toDouble / m.oldVersion.requests * 100
            sb.append(f"$errorRate%.1f%% errors, ${m.oldVersion.p99LatencyMs}%.0fms p99\n")
          else
            sb.append("no traffic\n")

          sb.append(s"  New version: ${m.newVersion.requests} reqs, ")
          if m.newVersion.requests > 0 then
            val errorRate = m.newVersion.failures.toDouble / m.newVersion.requests * 100
            sb.append(f"$errorRate%.1f%% errors, ${m.newVersion.p99LatencyMs}%.0fms p99\n")
          else
            sb.append("no traffic\n")
        }

        sb.toString.trim

      case OutputFormat.Json =>
        import io.circe.generic.auto.*
        Json.obj(
          "pipelineName"  -> Json.fromString(resp.pipelineName),
          "status"        -> Json.fromString(resp.status),
          "currentWeight" -> Json.fromDoubleOrNull(resp.currentWeight),
          "currentStep"   -> Json.fromInt(resp.currentStep),
          "startedAt"     -> Json.fromString(resp.startedAt),
          "oldVersion"    -> Json.obj(
            "version" -> Json.fromInt(resp.oldVersion.version),
            "hash"    -> Json.fromString(resp.oldVersion.structuralHash)
          ),
          "newVersion"    -> Json.obj(
            "version" -> Json.fromInt(resp.newVersion.version),
            "hash"    -> Json.fromString(resp.newVersion.structuralHash)
          )
        ).noSpaces

  // ============= Helpers =============

  /**
   * Read source code from file with path validation.
   *
   * Resolves symlinks and validates the path to prevent path traversal.
   */
  private def readSourceFile(path: Path): IO[Either[String, String]] =
    IO.blocking {
      if !Files.exists(path) then
        Left(s"File not found: $path")
      else if Files.isDirectory(path) then
        Left(s"Path is a directory: $path")
      else
        // Resolve symlinks to canonical path
        val realPath = path.toRealPath()
        Right(new String(Files.readAllBytes(realPath), StandardCharsets.UTF_8))
    }.handleErrorWith {
      case _: java.nio.file.AccessDeniedException =>
        IO.pure(Left(s"Permission denied: $path"))
      case _: java.nio.file.NoSuchFileException =>
        IO.pure(Left(s"File not found: $path"))
      case e: java.io.IOException =>
        IO.pure(Left(s"Failed to read file: ${StringUtils.sanitizeError(e.getMessage)}"))
      case e =>
        IO.pure(Left(s"Unexpected error: ${StringUtils.sanitizeError(e.getMessage)}"))
    }

  private def deriveName(path: Path): String =
    val fileName = path.getFileName.toString
    if fileName.endsWith(".cst") then fileName.dropRight(4)
    else fileName
