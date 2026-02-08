package io.constellation.cli.commands

import cats.effect.{ExitCode, IO}
import cats.implicits.*

import io.constellation.cli.{CliApp, HttpClient, Output, OutputFormat, StringUtils}

import com.monovore.decline.*
import io.circe.{Decoder, Json}
import org.http4s.Uri
import org.http4s.client.Client

object ServerCommand:

  /** Server subcommands for server operations. */
  sealed trait ServerSubcommand extends CliCommand

  // ============= Health Command =============

  case class ServerHealth() extends ServerSubcommand

  case class HealthResponse(
      status: String
  )

  object HealthResponse:
    given Decoder[HealthResponse] = Decoder.instance { c =>
      c.downField("status").as[String].map(HealthResponse.apply)
    }

  // ============= Pipelines Command =============

  case class ServerPipelines(
      showDetails: Option[String] = None
  ) extends ServerSubcommand

  case class PipelineSummary(
      structuralHash: String,
      syntacticHash: String,
      aliases: List[String],
      compiledAt: String,
      moduleCount: Int,
      declaredOutputs: List[String]
  )

  object PipelineSummary:
    given Decoder[PipelineSummary] = Decoder.instance { c =>
      for
        structuralHash <- c.downField("structuralHash").as[String]
        syntacticHash  <- c.downField("syntacticHash").as[String]
        aliases        <- c.downField("aliases").as[Option[List[String]]].map(_.getOrElse(Nil))
        compiledAt     <- c.downField("compiledAt").as[String]
        moduleCount    <- c.downField("moduleCount").as[Int]
        declaredOutputs <- c
          .downField("declaredOutputs")
          .as[Option[List[String]]]
          .map(_.getOrElse(Nil))
      yield PipelineSummary(
        structuralHash,
        syntacticHash,
        aliases,
        compiledAt,
        moduleCount,
        declaredOutputs
      )
    }

  case class PipelineListResponse(
      pipelines: List[PipelineSummary]
  )

  object PipelineListResponse:
    given Decoder[PipelineListResponse] = Decoder.instance { c =>
      c.downField("pipelines").as[List[PipelineSummary]].map(PipelineListResponse.apply)
    }

  case class PipelineDetailResponse(
      structuralHash: String,
      syntacticHash: String,
      aliases: List[String],
      compiledAt: String,
      modules: List[ModuleInfo],
      declaredOutputs: List[String],
      inputSchema: Map[String, String],
      outputSchema: Map[String, String]
  )

  case class ModuleInfo(
      name: String,
      description: String,
      version: String,
      inputs: Map[String, String],
      outputs: Map[String, String]
  )

  object ModuleInfo:
    given Decoder[ModuleInfo] = Decoder.instance { c =>
      for
        name        <- c.downField("name").as[String]
        description <- c.downField("description").as[String]
        version     <- c.downField("version").as[String]
        inputs      <- c.downField("inputs").as[Map[String, String]]
        outputs     <- c.downField("outputs").as[Map[String, String]]
      yield ModuleInfo(name, description, version, inputs, outputs)
    }

  object PipelineDetailResponse:
    given Decoder[PipelineDetailResponse] = Decoder.instance { c =>
      for
        structuralHash <- c.downField("structuralHash").as[String]
        syntacticHash  <- c.downField("syntacticHash").as[String]
        aliases        <- c.downField("aliases").as[Option[List[String]]].map(_.getOrElse(Nil))
        compiledAt     <- c.downField("compiledAt").as[String]
        modules        <- c.downField("modules").as[List[ModuleInfo]]
        declaredOutputs <- c
          .downField("declaredOutputs")
          .as[Option[List[String]]]
          .map(_.getOrElse(Nil))
        inputSchema  <- c.downField("inputSchema").as[Map[String, String]]
        outputSchema <- c.downField("outputSchema").as[Map[String, String]]
      yield PipelineDetailResponse(
        structuralHash,
        syntacticHash,
        aliases,
        compiledAt,
        modules,
        declaredOutputs,
        inputSchema,
        outputSchema
      )
    }

  // ============= Executions Command =============

  case class ServerExecutions(
      subcommand: ExecutionsSubcommand
  ) extends ServerSubcommand

  sealed trait ExecutionsSubcommand
  case class ExecutionsList(limit: Option[Int] = None) extends ExecutionsSubcommand
  case class ExecutionsShow(id: String)                extends ExecutionsSubcommand
  case class ExecutionsDelete(id: String)              extends ExecutionsSubcommand

  case class ExecutionSummary(
      executionId: String,
      structuralHash: String,
      resumptionCount: Int,
      missingInputs: Map[String, String],
      createdAt: String
  )

  object ExecutionSummary:
    given Decoder[ExecutionSummary] = Decoder.instance { c =>
      for
        executionId     <- c.downField("executionId").as[String]
        structuralHash  <- c.downField("structuralHash").as[String]
        resumptionCount <- c.downField("resumptionCount").as[Int]
        missingInputs   <- c.downField("missingInputs").as[Map[String, String]]
        createdAt       <- c.downField("createdAt").as[String]
      yield ExecutionSummary(executionId, structuralHash, resumptionCount, missingInputs, createdAt)
    }

  case class ExecutionListResponse(
      executions: List[ExecutionSummary]
  )

  object ExecutionListResponse:
    given Decoder[ExecutionListResponse] = Decoder.instance { c =>
      c.downField("executions").as[List[ExecutionSummary]].map(ExecutionListResponse.apply)
    }

  // ============= Metrics Command =============

  case class ServerMetrics() extends ServerSubcommand

  // ============= Command Definitions =============

  private val healthCmd = Opts.subcommand(
    name = "health",
    help = "Check server health status"
  )(Opts(ServerHealth()))

  private val pipelinesListCmd = Opts(ServerPipelines(None))

  private val pipelinesShowCmd = Opts.subcommand(
    name = "show",
    help = "Show pipeline details"
  ) {
    Opts.argument[String](metavar = "name-or-hash").map(name => ServerPipelines(Some(name)))
  }

  private val pipelinesCmd = Opts.subcommand(
    name = "pipelines",
    help = "List or show loaded pipelines"
  ) {
    pipelinesShowCmd orElse pipelinesListCmd
  }

  private val execListCmd = Opts.subcommand(
    name = "list",
    help = "List suspended executions"
  ) {
    Opts
      .option[Int]("limit", short = "n", help = "Maximum number of executions to show")
      .orNone
      .map(limit => ServerExecutions(ExecutionsList(limit)))
  }

  private val execShowCmd = Opts.subcommand(
    name = "show",
    help = "Show execution details"
  ) {
    Opts.argument[String](metavar = "id").map(id => ServerExecutions(ExecutionsShow(id)))
  }

  private val execDeleteCmd = Opts.subcommand(
    name = "delete",
    help = "Delete a suspended execution"
  ) {
    Opts.argument[String](metavar = "id").map(id => ServerExecutions(ExecutionsDelete(id)))
  }

  private val executionsCmd = Opts.subcommand(
    name = "executions",
    help = "Manage suspended executions"
  ) {
    execListCmd orElse execShowCmd orElse execDeleteCmd
  }

  private val metricsCmd = Opts.subcommand(
    name = "metrics",
    help = "Show server metrics"
  )(Opts(ServerMetrics()))

  val command: Opts[CliCommand] = Opts.subcommand(
    name = "server",
    help = "Server operations"
  ) {
    healthCmd orElse pipelinesCmd orElse executionsCmd orElse metricsCmd
  }

  // ============= Execution =============

  def execute(
      cmd: ServerSubcommand,
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat,
      quiet: Boolean
  )(using client: Client[IO]): IO[ExitCode] =
    cmd match
      case ServerHealth() =>
        executeHealth(baseUri, token, format)

      case ServerPipelines(None) =>
        executePipelinesList(baseUri, token, format)

      case ServerPipelines(Some(ref)) =>
        executePipelinesShow(ref, baseUri, token, format)

      case ServerExecutions(ExecutionsList(limit)) =>
        executeExecutionsList(limit, baseUri, token, format)

      case ServerExecutions(ExecutionsShow(id)) =>
        executeExecutionsShow(id, baseUri, token, format)

      case ServerExecutions(ExecutionsDelete(id)) =>
        executeExecutionsDelete(id, baseUri, token, format)

      case ServerMetrics() =>
        executeMetrics(baseUri, token, format)

  // ============= Health =============

  private def executeHealth(
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = baseUri / "health"

    HttpClient.get[HealthResponse](uri, token).flatMap {
      case HttpClient.Success(resp) =>
        val output = Output.health(resp.status, None, None, None, format)
        IO.println(output).as(CliApp.ExitCodes.Success)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ApiError(503, _) =>
        val output = Output.health("unavailable", None, None, None, format)
        IO.println(output).as(CliApp.ExitCodes.RuntimeError)

      case HttpClient.ApiError(_, msg) =>
        IO.println(Output.error(msg, format)).as(CliApp.ExitCodes.RuntimeError)

      case HttpClient.ParseError(msg) =>
        IO.println(Output.error(s"Failed to parse response: $msg", format))
          .as(CliApp.ExitCodes.RuntimeError)
    }

  // ============= Pipelines List =============

  private def executePipelinesList(
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = baseUri / "pipelines"

    HttpClient.get[PipelineListResponse](uri, token).flatMap {
      case HttpClient.Success(resp) =>
        val output = formatPipelineList(resp.pipelines, format)
        IO.println(output).as(CliApp.ExitCodes.Success)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ApiError(401, _) =>
        IO.println(Output.error("Authentication required", format))
          .as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(_, msg) =>
        IO.println(Output.error(msg, format)).as(CliApp.ExitCodes.RuntimeError)

      case HttpClient.ParseError(msg) =>
        IO.println(Output.error(s"Failed to parse response: $msg", format))
          .as(CliApp.ExitCodes.RuntimeError)
    }

  private def formatPipelineList(pipelines: List[PipelineSummary], format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        if pipelines.isEmpty then s"${fansi.Color.Yellow("No pipelines loaded")}"
        else
          val header = s"${fansi.Bold.On(s"${pipelines.size} pipeline(s) loaded:")}\n"
          val rows = pipelines.map { p =>
            val name = p.aliases.headOption.getOrElse(StringUtils.hashPreview(p.structuralHash))
            val hash = StringUtils.truncate(p.structuralHash, StringUtils.Display.HashPreviewLength)
            val modules = s"${p.moduleCount} modules"
            val outputs = p.declaredOutputs.mkString(", ")
            s"  ${fansi.Color.Cyan(name)} (${hash}) - $modules, outputs: [$outputs]"
          }
          header + rows.mkString("\n")

      case OutputFormat.Json =>
        import io.circe.syntax.*
        Json
          .obj(
            "pipelines" -> Json.fromValues(pipelines.map { p =>
              Json.obj(
                "name"            -> Json.fromString(p.aliases.headOption.getOrElse("")),
                "structuralHash"  -> Json.fromString(p.structuralHash),
                "aliases"         -> Json.fromValues(p.aliases.map(Json.fromString)),
                "moduleCount"     -> Json.fromInt(p.moduleCount),
                "declaredOutputs" -> Json.fromValues(p.declaredOutputs.map(Json.fromString))
              )
            })
          )
          .noSpaces

  // ============= Pipelines Show =============

  private def executePipelinesShow(
      ref: String,
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = baseUri / "pipelines" / ref

    HttpClient.get[PipelineDetailResponse](uri, token).flatMap {
      case HttpClient.Success(resp) =>
        val output = formatPipelineDetail(resp, format)
        IO.println(output).as(CliApp.ExitCodes.Success)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ApiError(404, _) =>
        IO.println(Output.error(s"Pipeline '$ref' not found", format))
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

  private def formatPipelineDetail(detail: PipelineDetailResponse, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        val sb = new StringBuilder
        sb.append(s"${fansi.Bold.On("Pipeline Details")}\n\n")
        sb.append(s"  ${fansi.Bold.On("Hash:")} ${detail.structuralHash}\n")
        if detail.aliases.nonEmpty then
          sb.append(s"  ${fansi.Bold.On("Aliases:")} ${detail.aliases.mkString(", ")}\n")
        sb.append(s"  ${fansi.Bold.On("Compiled:")} ${detail.compiledAt}\n")
        sb.append(s"\n${fansi.Bold.On("Inputs:")}\n")
        detail.inputSchema.foreach { case (name, typ) =>
          sb.append(s"  ${fansi.Color.Cyan(name)}: $typ\n")
        }
        sb.append(s"\n${fansi.Bold.On("Outputs:")}\n")
        detail.outputSchema.foreach { case (name, typ) =>
          sb.append(s"  ${fansi.Color.Green(name)}: $typ\n")
        }
        sb.append(s"\n${fansi.Bold.On("Modules:")} (${detail.modules.size})\n")
        detail.modules.foreach { m =>
          sb.append(s"  ${fansi.Color.Yellow(m.name)} v${m.version}\n")
          sb.append(s"    ${m.description}\n")
        }
        sb.toString.trim

      case OutputFormat.Json =>
        import io.circe.syntax.*
        Json
          .obj(
            "structuralHash" -> Json.fromString(detail.structuralHash),
            "syntacticHash"  -> Json.fromString(detail.syntacticHash),
            "aliases"        -> Json.fromValues(detail.aliases.map(Json.fromString)),
            "compiledAt"     -> Json.fromString(detail.compiledAt),
            "inputSchema" -> Json.fromFields(
              detail.inputSchema.map((k, v) => k -> Json.fromString(v))
            ),
            "outputSchema" -> Json.fromFields(
              detail.outputSchema.map((k, v) => k -> Json.fromString(v))
            ),
            "modules" -> Json.fromValues(detail.modules.map { m =>
              Json.obj(
                "name"        -> Json.fromString(m.name),
                "description" -> Json.fromString(m.description),
                "version"     -> Json.fromString(m.version)
              )
            })
          )
          .noSpaces

  // ============= Executions List =============

  private def executeExecutionsList(
      limit: Option[Int],
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = baseUri / "executions"

    HttpClient.get[ExecutionListResponse](uri, token).flatMap {
      case HttpClient.Success(resp) =>
        val executions = limit.fold(resp.executions)(n => resp.executions.take(n))
        val output     = formatExecutionList(executions, format)
        IO.println(output).as(CliApp.ExitCodes.Success)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ApiError(401, _) =>
        IO.println(Output.error("Authentication required", format))
          .as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(_, msg) =>
        IO.println(Output.error(msg, format)).as(CliApp.ExitCodes.RuntimeError)

      case HttpClient.ParseError(msg) =>
        IO.println(Output.error(s"Failed to parse response: $msg", format))
          .as(CliApp.ExitCodes.RuntimeError)
    }

  private def formatExecutionList(
      executions: List[ExecutionSummary],
      format: OutputFormat
  ): String =
    format match
      case OutputFormat.Human =>
        if executions.isEmpty then s"${fansi.Color.Yellow("No suspended executions")}"
        else
          val header =
            s"${fansi.Bold.On("ID")}                                  ${fansi.Bold.On("Pipeline")}      ${fansi.Bold
                .On("Missing")}  ${fansi.Bold.On("Created")}\n"
          val rows = executions.map { e =>
            val id      = e.executionId
            val hash    = StringUtils.hashPreview(e.structuralHash)
            val missing = e.missingInputs.size.toString
            val created = StringUtils.timestampPreview(e.createdAt)
            f"$id  $hash  $missing%7s  $created"
          }
          header + rows.mkString("\n")

      case OutputFormat.Json =>
        import io.circe.syntax.*
        Json
          .obj(
            "executions" -> Json.fromValues(executions.map { e =>
              Json.obj(
                "executionId"     -> Json.fromString(e.executionId),
                "structuralHash"  -> Json.fromString(e.structuralHash),
                "resumptionCount" -> Json.fromInt(e.resumptionCount),
                "missingInputs" -> Json.fromFields(
                  e.missingInputs.map((k, v) => k -> Json.fromString(v))
                ),
                "createdAt" -> Json.fromString(e.createdAt)
              )
            })
          )
          .noSpaces

  // ============= Executions Show =============

  private def executeExecutionsShow(
      id: String,
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = baseUri / "executions" / id

    HttpClient.get[ExecutionSummary](uri, token).flatMap {
      case HttpClient.Success(resp) =>
        val output = formatExecutionDetail(resp, format)
        IO.println(output).as(CliApp.ExitCodes.Success)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ApiError(404, _) =>
        IO.println(Output.error(s"Execution '$id' not found", format))
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

  private def formatExecutionDetail(exec: ExecutionSummary, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        val sb = new StringBuilder
        sb.append(s"${fansi.Bold.On("Execution Details")}\n\n")
        sb.append(s"  ${fansi.Bold.On("ID:")} ${exec.executionId}\n")
        sb.append(s"  ${fansi.Bold.On("Pipeline:")} ${exec.structuralHash}\n")
        sb.append(s"  ${fansi.Bold.On("Resumptions:")} ${exec.resumptionCount}\n")
        sb.append(s"  ${fansi.Bold.On("Created:")} ${exec.createdAt}\n")
        if exec.missingInputs.nonEmpty then
          sb.append(s"\n${fansi.Bold.On("Missing Inputs:")}\n")
          exec.missingInputs.foreach { case (name, typ) =>
            sb.append(s"  ${fansi.Color.Cyan(name)}: ${fansi.Color.Yellow(typ)}\n")
          }
        sb.toString.trim

      case OutputFormat.Json =>
        import io.circe.syntax.*
        Json
          .obj(
            "executionId"     -> Json.fromString(exec.executionId),
            "structuralHash"  -> Json.fromString(exec.structuralHash),
            "resumptionCount" -> Json.fromInt(exec.resumptionCount),
            "missingInputs" -> Json.fromFields(
              exec.missingInputs.map((k, v) => k -> Json.fromString(v))
            ),
            "createdAt" -> Json.fromString(exec.createdAt)
          )
          .noSpaces

  // ============= Executions Delete =============

  private def executeExecutionsDelete(
      id: String,
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = baseUri / "executions" / id

    // Use a simple response decoder for delete
    given Decoder[Json] = Decoder.decodeJson

    HttpClient.delete[Json](uri, token).flatMap {
      case HttpClient.Success(_) =>
        IO.println(Output.success(s"Deleted execution $id", format))
          .as(CliApp.ExitCodes.Success)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ApiError(404, _) =>
        IO.println(Output.error(s"Execution '$id' not found", format))
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

  // ============= Metrics =============

  private def executeMetrics(
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = baseUri / "metrics"

    given Decoder[Json] = Decoder.decodeJson

    HttpClient.get[Json](uri, token).flatMap {
      case HttpClient.Success(resp) =>
        val output = formatMetrics(resp, format)
        IO.println(output).as(CliApp.ExitCodes.Success)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ApiError(401, _) =>
        IO.println(Output.error("Authentication required", format))
          .as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(_, msg) =>
        IO.println(Output.error(msg, format)).as(CliApp.ExitCodes.RuntimeError)

      case HttpClient.ParseError(msg) =>
        IO.println(Output.error(s"Failed to parse response: $msg", format))
          .as(CliApp.ExitCodes.RuntimeError)
    }

  private def formatMetrics(metrics: Json, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        val sb = new StringBuilder
        sb.append(s"${fansi.Bold.On("Server Metrics")}\n\n")

        // Server section
        metrics.hcursor.downField("server").focus.foreach { server =>
          sb.append(s"${fansi.Bold.On("Server:")}\n")
          server.hcursor.downField("uptime_seconds").as[Long].foreach { uptime =>
            val days  = uptime / 86400
            val hours = (uptime % 86400) / 3600
            val mins  = (uptime % 3600) / 60
            val uptimeStr =
              if days > 0 then s"${days}d ${hours}h ${mins}m"
              else if hours > 0 then s"${hours}h ${mins}m"
              else s"${mins}m"
            sb.append(s"  Uptime: $uptimeStr\n")
          }
          server.hcursor.downField("requests_total").as[Long].foreach { reqs =>
            sb.append(s"  Requests: $reqs\n")
          }
        }

        // Cache section
        metrics.hcursor.downField("cache").focus.foreach { cache =>
          if !cache.isNull then
            sb.append(s"\n${fansi.Bold.On("Cache:")}\n")
            cache.hcursor.downField("hits").as[Long].foreach(v => sb.append(s"  Hits: $v\n"))
            cache.hcursor.downField("misses").as[Long].foreach(v => sb.append(s"  Misses: $v\n"))
            cache.hcursor.downField("hitRate").as[Double].foreach { rate =>
              sb.append(f"  Hit Rate: ${rate * 100}%.1f%%\n")
            }
            cache.hcursor.downField("entries").as[Int].foreach(v => sb.append(s"  Entries: $v\n"))
        }

        // Scheduler section
        metrics.hcursor.downField("scheduler").focus.foreach { sched =>
          if !sched.isNull then
            sched.hcursor.downField("enabled").as[Boolean].foreach { enabled =>
              sb.append(
                s"\n${fansi.Bold.On("Scheduler:")} ${if enabled then "enabled" else "disabled"}\n"
              )
              if enabled then
                sched.hcursor
                  .downField("activeCount")
                  .as[Int]
                  .foreach(v => sb.append(s"  Active: $v\n"))
                sched.hcursor
                  .downField("queuedCount")
                  .as[Int]
                  .foreach(v => sb.append(s"  Queued: $v\n"))
                sched.hcursor
                  .downField("totalCompleted")
                  .as[Long]
                  .foreach(v => sb.append(s"  Completed: $v\n"))
            }
        }

        sb.toString.trim

      case OutputFormat.Json =>
        metrics.spaces2
