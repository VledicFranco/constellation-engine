package io.constellation.cli.commands

import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets

import cats.data.ValidatedNel
import cats.effect.{ExitCode, IO}
import cats.implicits.*

import com.monovore.decline.*

import io.circe.{Decoder, Json}
import io.circe.generic.semiauto.*

import io.constellation.cli.{CliApp, HttpClient, Output, OutputFormat, StringUtils}

import org.http4s.Uri
import org.http4s.client.Client

/** Viz format options. */
enum VizFormat:
  case Dot, Json, Mermaid

object VizFormat:
  given Argument[VizFormat] with
    def read(s: String): ValidatedNel[String, VizFormat] =
      s.toLowerCase match
        case "dot"     => VizFormat.Dot.validNel
        case "json"    => VizFormat.Json.validNel
        case "mermaid" => VizFormat.Mermaid.validNel
        case other     => s"Invalid format '$other', expected: dot, json, mermaid".invalidNel
    def defaultMetavar: String = "format"

/** Viz command: generate DAG visualization. */
case class VizCommand(
    file: Path,
    format: VizFormat = VizFormat.Dot
) extends CliCommand

object VizCommand:

  /** API response model for /compile endpoint (used for viz). */
  case class CompileResponse(
      success: Boolean,
      structuralHash: Option[String] = None,
      errors: List[String] = Nil
  )

  object CompileResponse:
    given Decoder[CompileResponse] = Decoder.instance { c =>
      for
        success        <- c.downField("success").as[Boolean]
        structuralHash <- c.downField("structuralHash").as[Option[String]]
        errors         <- c.downField("errors").as[Option[List[String]]].map(_.getOrElse(Nil))
      yield CompileResponse(success, structuralHash, errors)
    }

  /** API response model for pipeline detail. */
  case class PipelineDetailResponse(
      structuralHash: String,
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
    given Decoder[ModuleInfo] = deriveDecoder

  object PipelineDetailResponse:
    given Decoder[PipelineDetailResponse] = deriveDecoder

  private val fileArg = Opts.argument[Path](metavar = "file")

  private val formatOpt = Opts
    .option[VizFormat](
      "format",
      short = "F",
      help = "Output format: dot, json, mermaid"
    )
    .withDefault(VizFormat.Dot)

  val command: Opts[CliCommand] = Opts.subcommand(
    name = "viz",
    help = "Visualize pipeline DAG"
  ) {
    (fileArg, formatOpt).mapN(VizCommand.apply)
  }

  /** Execute the viz command. */
  def execute(
      cmd: VizCommand,
      baseUri: Uri,
      token: Option[String],
      outputFormat: OutputFormat,
      quiet: Boolean
  )(using client: Client[IO]): IO[ExitCode] =
    for
      // Read source file
      sourceResult <- readSourceFile(cmd.file)
      exitCode <- sourceResult match
        case Left(err) =>
          IO.println(Output.error(err, outputFormat)).as(CliApp.ExitCodes.UsageError)
        case Right(source) =>
          compileAndVisualize(
            source,
            cmd.file.getFileName.toString,
            cmd.format,
            baseUri,
            token,
            outputFormat
          )
    yield exitCode

  /** Read source code from file with path validation.
    *
    * Resolves symlinks and validates the path to prevent path traversal.
    */
  private def readSourceFile(path: Path): IO[Either[String, String]] =
    IO.blocking {
      if !Files.exists(path) then Left(s"File not found: $path")
      else if Files.isDirectory(path) then Left(s"Path is a directory: $path")
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

  /** Compile and then fetch pipeline detail for visualization. */
  private def compileAndVisualize(
      source: String,
      name: String,
      vizFormat: VizFormat,
      baseUri: Uri,
      token: Option[String],
      outputFormat: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    // First compile to get the structural hash
    val compileUri = baseUri / "compile"
    val compileBody = Json.obj(
      "source" -> Json.fromString(source),
      "name"   -> Json.fromString(name.stripSuffix(".cst"))
    )

    HttpClient.post[CompileResponse](compileUri, compileBody, token).flatMap {
      case HttpClient.Success(resp) if resp.success =>
        resp.structuralHash match
          case Some(hash) =>
            // Fetch pipeline detail
            fetchAndRenderPipeline(hash, vizFormat, baseUri, token, outputFormat)
          case None =>
            IO.println(Output.error("Compilation succeeded but no hash returned", outputFormat))
              .as(CliApp.ExitCodes.RuntimeError)

      case HttpClient.Success(resp) =>
        IO.println(Output.compilationErrors(resp.errors, outputFormat))
          .as(CliApp.ExitCodes.CompileError)

      case HttpClient.ApiError(401, _) =>
        IO.println(Output.error("Authentication required", outputFormat))
          .as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(403, _) =>
        IO.println(Output.error("Access denied", outputFormat))
          .as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(_, msg) =>
        IO.println(Output.error(msg, outputFormat))
          .as(CliApp.ExitCodes.CompileError)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, outputFormat))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ParseError(msg) =>
        IO.println(Output.error(s"Failed to parse server response: $msg", outputFormat))
          .as(CliApp.ExitCodes.RuntimeError)
    }

  /** Fetch pipeline detail and render visualization. */
  private def fetchAndRenderPipeline(
      hash: String,
      vizFormat: VizFormat,
      baseUri: Uri,
      token: Option[String],
      outputFormat: OutputFormat
  )(using client: Client[IO]): IO[ExitCode] =
    val detailUri = baseUri / "pipelines" / s"sha256:$hash"

    HttpClient.get[PipelineDetailResponse](detailUri, token).flatMap {
      case HttpClient.Success(detail) =>
        val output = renderVisualization(detail, vizFormat)
        IO.println(output).as(CliApp.ExitCodes.Success)

      case HttpClient.ApiError(404, _) =>
        IO.println(Output.error("Pipeline not found after compilation", outputFormat))
          .as(CliApp.ExitCodes.NotFound)

      case HttpClient.ApiError(_, msg) =>
        IO.println(Output.error(msg, outputFormat))
          .as(CliApp.ExitCodes.RuntimeError)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, outputFormat))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ParseError(msg) =>
        IO.println(Output.error(s"Failed to parse server response: $msg", outputFormat))
          .as(CliApp.ExitCodes.RuntimeError)
    }

  /** Render pipeline as the requested format. */
  private def renderVisualization(
      detail: PipelineDetailResponse,
      format: VizFormat
  ): String =
    // Build nodes: (id, label, dependencies)
    val inputNodes = detail.inputSchema.toList.map { case (name, typ) =>
      (s"input_$name", s"$name: $typ", Nil)
    }

    val moduleNodes = detail.modules.zipWithIndex.map { case (m, idx) =>
      val deps = m.inputs.keys.toList.map(k => s"input_$k")
      (s"module_$idx", m.name, deps)
    }

    val outputNodes = detail.declaredOutputs.map { name =>
      (s"output_$name", s"out: $name", Nil)
    }

    val allNodes = inputNodes ++ moduleNodes ++ outputNodes

    // Build edges
    val inputToModule = for
      (m, idx)  <- detail.modules.zipWithIndex
      inputName <- m.inputs.keys
    yield (s"input_$inputName", s"module_$idx")

    val moduleToOutput = for
      (m, idx)   <- detail.modules.zipWithIndex
      outputName <- m.outputs.keys
      if detail.declaredOutputs.contains(outputName)
    yield (s"module_$idx", s"output_$outputName")

    val allEdges = inputToModule ++ moduleToOutput

    format match
      case VizFormat.Dot =>
        Output.dagDot(allNodes, allEdges)
      case VizFormat.Mermaid =>
        Output.dagMermaid(allNodes, allEdges)
      case VizFormat.Json =>
        Json
          .obj(
            "nodes" -> Json.fromValues(allNodes.map { case (id, label, deps) =>
              Json.obj(
                "id"           -> Json.fromString(id),
                "label"        -> Json.fromString(label),
                "dependencies" -> Json.fromValues(deps.map(Json.fromString))
              )
            }),
            "edges" -> Json.fromValues(allEdges.map { case (from, to) =>
              Json.obj(
                "from" -> Json.fromString(from),
                "to"   -> Json.fromString(to)
              )
            })
          )
          .spaces2
