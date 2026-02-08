package io.constellation.cli

import io.circe.Json
import io.circe.syntax.*

/** Output formatting utilities for CLI. */
object Output:

  // ANSI color codes via fansi
  private val Red    = fansi.Color.Red
  private val Green  = fansi.Color.Green
  private val Yellow = fansi.Color.Yellow
  private val Blue   = fansi.Color.Blue
  private val Cyan   = fansi.Color.Cyan
  private val Bold   = fansi.Bold.On
  private val Reset  = fansi.Attr.Reset

  /** Format a success message. */
  def success(message: String, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        s"${Green(Bold("✓"))} ${message}"
      case OutputFormat.Json =>
        Json.obj(
          "success" -> Json.True,
          "message" -> Json.fromString(message)
        ).noSpaces

  /** Format an error message. */
  def error(message: String, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        s"${Red(Bold("✗"))} ${Red(message)}"
      case OutputFormat.Json =>
        Json.obj(
          "success" -> Json.False,
          "error"   -> Json.fromString(message)
        ).noSpaces

  /** Format a warning message. */
  def warning(message: String, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        s"${Yellow(Bold("⚠"))} ${Yellow(message)}"
      case OutputFormat.Json =>
        Json.obj(
          "warning" -> Json.fromString(message)
        ).noSpaces

  /** Format an info message. */
  def info(message: String, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        s"${Blue("ℹ")} ${message}"
      case OutputFormat.Json =>
        Json.obj(
          "info" -> Json.fromString(message)
        ).noSpaces

  /** Format compilation errors. */
  def compilationErrors(errors: List[String], format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        val header = s"${Red(Bold("✗"))} Compilation failed with ${errors.size} error(s):\n"
        val body = errors.map(e => s"  ${Red("•")} $e").mkString("\n")
        header + body
      case OutputFormat.Json =>
        Json.obj(
          "success" -> Json.False,
          "errors"  -> errors.asJson
        ).noSpaces

  /** Format execution outputs. */
  def outputs(results: Map[String, Json], format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        if results.isEmpty then
          s"${Yellow("No outputs produced")}"
        else
          val header = s"${Green(Bold("✓"))} Execution completed:\n"
          val body = results.map { case (name, value) =>
            s"  ${Cyan(name)}: ${formatJsonValue(value)}"
          }.mkString("\n")
          header + body
      case OutputFormat.Json =>
        Json.obj(
          "success" -> Json.True,
          "outputs" -> Json.fromFields(results)
        ).noSpaces

  /** Format a suspended execution. */
  def suspended(
      executionId: String,
      missingInputs: Map[String, String],
      format: OutputFormat
  ): String =
    format match
      case OutputFormat.Human =>
        val header = s"${Yellow(Bold("⏸"))} Execution suspended (ID: ${executionId.take(8)}...)\n"
        val body = if missingInputs.nonEmpty then
          s"  Missing inputs:\n" + missingInputs.map { case (name, typ) =>
            s"    ${Cyan(name)}: ${Yellow(typ)}"
          }.mkString("\n")
        else ""
        header + body
      case OutputFormat.Json =>
        Json.obj(
          "success"       -> Json.True,
          "status"        -> Json.fromString("suspended"),
          "executionId"   -> Json.fromString(executionId),
          "missingInputs" -> missingInputs.asJson
        ).noSpaces

  /** Format pipeline modules for viz output. */
  def dagDot(
      nodes: List[(String, String, List[String])],
      edges: List[(String, String)]
  ): String =
    val sb = new StringBuilder
    sb.append("digraph pipeline {\n")
    sb.append("  rankdir=LR;\n")
    sb.append("  node [shape=box, style=rounded];\n")
    sb.append("\n")

    // Add nodes
    nodes.foreach { case (id, label, _) =>
      sb.append(s"""  "$id" [label="$label"];\n""")
    }
    sb.append("\n")

    // Add edges
    edges.foreach { case (from, to) =>
      sb.append(s"""  "$from" -> "$to";\n""")
    }

    sb.append("}\n")
    sb.toString

  /** Format pipeline as Mermaid diagram. */
  def dagMermaid(
      nodes: List[(String, String, List[String])],
      edges: List[(String, String)]
  ): String =
    val sb = new StringBuilder
    sb.append("graph LR\n")

    // Add nodes
    nodes.foreach { case (id, label, _) =>
      sb.append(s"  $id[$label]\n")
    }

    // Add edges
    edges.foreach { case (from, to) =>
      sb.append(s"  $from --> $to\n")
    }

    sb.toString

  /** Format health check result. */
  def health(
      status: String,
      version: Option[String],
      uptime: Option[String],
      pipelineCount: Option[Int],
      format: OutputFormat
  ): String =
    format match
      case OutputFormat.Human =>
        val statusIcon = if status == "ok" then Green(Bold("✓")) else Red(Bold("✗"))
        val lines = List[Option[String]](
          Some(s"$statusIcon Server ${if status == "ok" then "healthy" else status}"),
          version.map(v => s"  Version: $v"),
          uptime.map(u => s"  Uptime: $u"),
          pipelineCount.map(c => s"  Pipelines: $c loaded")
        ).flatten
        lines.mkString("\n")
      case OutputFormat.Json =>
        Json.obj(
          "status"        -> Json.fromString(status),
          "version"       -> version.map(Json.fromString).getOrElse(Json.Null),
          "uptime"        -> uptime.map(Json.fromString).getOrElse(Json.Null),
          "pipelineCount" -> pipelineCount.map(Json.fromInt).getOrElse(Json.Null)
        ).noSpaces

  /** Format config show output. */
  def configShow(config: CliConfig, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        val lines = List(
          s"${Bold("Server:")}",
          s"  url: ${Cyan(config.server.url)}",
          s"  token: ${config.server.token.map(_ => Yellow("(set)")).getOrElse("(not set)")}",
          s"${Bold("Defaults:")}",
          s"  output: ${config.defaults.output.toString.toLowerCase}",
          s"  viz_format: ${config.defaults.vizFormat}"
        )
        lines.mkString("\n")
      case OutputFormat.Json =>
        config.asJson.noSpaces

  /** Format a single config value. */
  def configValue(key: String, value: Option[String], format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        value match
          case Some(v) => s"$key = ${Cyan(v)}"
          case None    => s"${Yellow(s"Config key '$key' not found")}"
      case OutputFormat.Json =>
        Json.obj(
          "key"   -> Json.fromString(key),
          "value" -> value.map(Json.fromString).getOrElse(Json.Null)
        ).noSpaces

  /** Format a connection error. */
  def connectionError(url: String, error: String, format: OutputFormat): String =
    format match
      case OutputFormat.Human =>
        s"${Red(Bold("✗"))} Cannot connect to server at ${Cyan(url)}\n" +
          s"  ${Red(error)}\n" +
          s"  ${Yellow("Hint:")} Make sure the Constellation server is running"
      case OutputFormat.Json =>
        Json.obj(
          "success" -> Json.False,
          "error"   -> Json.fromString("connection_error"),
          "message" -> Json.fromString(error),
          "url"     -> Json.fromString(url)
        ).noSpaces

  /** Format a JSON value for human display. */
  private def formatJsonValue(json: Json): String =
    json.fold(
      jsonNull = "null",
      jsonBoolean = b => if b then "true" else "false",
      jsonNumber = n => n.toString,
      jsonString = s => s"\"$s\"",
      jsonArray = arr => s"[${arr.size} items]",
      jsonObject = obj => s"{${obj.size} fields}"
    )
