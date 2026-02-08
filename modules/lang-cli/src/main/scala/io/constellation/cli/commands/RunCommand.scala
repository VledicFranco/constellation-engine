package io.constellation.cli.commands

import java.nio.file.{Files, Path}
import java.nio.charset.StandardCharsets

import cats.data.ValidatedNel
import cats.effect.{ExitCode, IO}
import cats.implicits.*

import com.monovore.decline.*

import io.circe.{Decoder, Json}
import io.circe.generic.semiauto.*
import io.circe.parser.parse

import io.constellation.cli.{CliApp, HttpClient, Output, OutputFormat, StringUtils}

import org.http4s.Uri
import org.http4s.client.Client

/** Run command: execute a pipeline with inputs. */
object RunConstants:
  /** Maximum allowed input file size (10MB). */
  val MaxInputFileSize: Long = 10 * 1024 * 1024
case class RunCommand(
    file: Path,
    inputs: List[(String, String)] = Nil,
    inputFile: Option[Path] = None
) extends CliCommand

object RunCommand:

  /** API response model for /run endpoint. */
  case class RunResponse(
      success: Boolean,
      outputs: Map[String, Json] = Map.empty,
      structuralHash: Option[String] = None,
      compilationErrors: List[String] = Nil,
      error: Option[String] = None,
      status: Option[String] = None,
      executionId: Option[String] = None,
      missingInputs: Option[Map[String, String]] = None,
      pendingOutputs: Option[List[String]] = None,
      resumptionCount: Option[Int] = None
  )

  object RunResponse:
    given Decoder[RunResponse] = Decoder.instance { c =>
      for
        success <- c.downField("success").as[Boolean]
        outputs <- c.downField("outputs").as[Option[Map[String, Json]]].map(_.getOrElse(Map.empty))
        structuralHash <- c.downField("structuralHash").as[Option[String]]
        compilationErrors <- c
          .downField("compilationErrors")
          .as[Option[List[String]]]
          .map(_.getOrElse(Nil))
        error           <- c.downField("error").as[Option[String]]
        status          <- c.downField("status").as[Option[String]]
        executionId     <- c.downField("executionId").as[Option[String]]
        missingInputs   <- c.downField("missingInputs").as[Option[Map[String, String]]]
        pendingOutputs  <- c.downField("pendingOutputs").as[Option[List[String]]]
        resumptionCount <- c.downField("resumptionCount").as[Option[Int]]
      yield RunResponse(
        success,
        outputs,
        structuralHash,
        compilationErrors,
        error,
        status,
        executionId,
        missingInputs,
        pendingOutputs,
        resumptionCount
      )
    }

  private val fileArg = Opts.argument[Path](metavar = "file")

  private val inputOpt = Opts
    .options[(String, String)](
      "input",
      short = "i",
      help = "Input value in key=value format"
    )(using inputMetavar)
    .orEmpty

  private val inputFileOpt = Opts
    .option[Path](
      "input-file",
      short = "f",
      help = "JSON file containing input values"
    )
    .orNone

  /** Custom metavar for key=value pairs. */
  private given inputMetavar: Argument[(String, String)] with
    def read(s: String): ValidatedNel[String, (String, String)] =
      s.split("=", 2) match
        case Array(key, value) => (key.trim, value.trim).validNel
        case _                 => s"Invalid input format '$s', expected key=value".invalidNel

    def defaultMetavar: String = "key=value"

  val command: Opts[CliCommand] = Opts.subcommand(
    name = "run",
    help = "Execute a pipeline with inputs"
  ) {
    (fileArg, inputOpt, inputFileOpt).mapN(RunCommand.apply)
  }

  /** Execute the run command. */
  def execute(
      cmd: RunCommand,
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat,
      quiet: Boolean
  )(using client: Client[IO]): IO[ExitCode] =
    for
      // Read source file
      sourceResult <- readSourceFile(cmd.file)
      exitCode <- sourceResult match
        case Left(err) =>
          IO.println(Output.error(err, format)).as(CliApp.ExitCodes.UsageError)
        case Right(source) =>
          // Parse inputs
          parseInputs(cmd.inputs, cmd.inputFile).flatMap {
            case Left(err) =>
              IO.println(Output.error(err, format)).as(CliApp.ExitCodes.UsageError)
            case Right(inputs) =>
              runPipeline(source, inputs, baseUri, token, format, quiet)
          }
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

  /** Parse inputs from CLI args and/or input file.
    *
    * Validates file size before reading to prevent OOM on large files.
    */
  private def parseInputs(
      cliInputs: List[(String, String)],
      inputFile: Option[Path]
  ): IO[Either[String, Map[String, Json]]] =
    val cliMap = cliInputs.map { case (k, v) =>
      k -> parseValue(v)
    }.toMap

    inputFile match
      case None => IO.pure(Right(cliMap))
      case Some(path) =>
        IO.blocking {
          if !Files.exists(path) then Left(s"Input file not found: $path")
          else if Files.size(path) > RunConstants.MaxInputFileSize then
            val maxMB = RunConstants.MaxInputFileSize / (1024 * 1024)
            Left(s"Input file too large (max ${maxMB}MB): $path")
          else
            // Resolve symlinks for path traversal mitigation
            val realPath = path.toRealPath()
            val content  = new String(Files.readAllBytes(realPath), StandardCharsets.UTF_8)
            parse(content) match
              case Right(json) =>
                json.as[Map[String, Json]] match
                  case Right(fileInputs) =>
                    // CLI inputs override file inputs
                    Right(fileInputs ++ cliMap)
                  case Left(err) =>
                    Left(s"Invalid JSON in input file: ${err.message}")
              case Left(err) =>
                Left(s"Invalid JSON in input file: ${err.message}")
        }.handleErrorWith {
          case _: java.nio.file.AccessDeniedException =>
            IO.pure(Left(s"Permission denied: $path"))
          case _: java.nio.file.NoSuchFileException =>
            IO.pure(Left(s"Input file not found: $path"))
          case e =>
            IO.pure(Left(s"Failed to read input file: ${StringUtils.sanitizeError(e.getMessage)}"))
        }

  /** Parse a string value to JSON, inferring type. */
  private def parseValue(s: String): Json =
    // Try to parse as JSON first
    parse(s).getOrElse {
      // Check for special values
      s.toLowerCase match
        case "true"  => Json.True
        case "false" => Json.False
        case "null"  => Json.Null
        case _       =>
          // Try as number
          s.toDoubleOption match
            case Some(d) if s.contains(".") => Json.fromDoubleOrNull(d)
            case Some(d)                    => Json.fromLong(d.toLong)
            case None                       => Json.fromString(s)
    }

  /** Run pipeline via API. */
  private def runPipeline(
      source: String,
      inputs: Map[String, Json],
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat,
      quiet: Boolean
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = baseUri / "run"
    val body = Json.obj(
      "source" -> Json.fromString(source),
      "inputs" -> Json.fromFields(inputs)
    )

    HttpClient.post[RunResponse](uri, body, token).flatMap {
      case HttpClient.Success(resp) =>
        // Handle compilation errors
        if resp.compilationErrors.nonEmpty then
          IO.println(Output.compilationErrors(resp.compilationErrors, format))
            .as(CliApp.ExitCodes.CompileError)
        // Handle runtime errors
        else if resp.error.isDefined then
          IO.println(Output.error(resp.error.get, format))
            .as(CliApp.ExitCodes.RuntimeError)
        // Handle suspended execution
        else if resp.status.contains("suspended") then
          val execId        = resp.executionId.getOrElse("unknown")
          val missingInputs = resp.missingInputs.getOrElse(Map.empty)
          IO.println(Output.suspended(execId, missingInputs, format))
            .as(CliApp.ExitCodes.Success)
        // Handle successful execution
        else
          IO.println(Output.outputs(resp.outputs, format))
            .as(CliApp.ExitCodes.Success)

      case HttpClient.ApiError(401, _) =>
        IO.println(Output.error("Authentication required", format))
          .as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(403, _) =>
        IO.println(Output.error("Access denied", format))
          .as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(_, msg) =>
        IO.println(Output.error(msg, format))
          .as(CliApp.ExitCodes.RuntimeError)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ParseError(msg) =>
        IO.println(Output.error(s"Failed to parse server response: $msg", format))
          .as(CliApp.ExitCodes.RuntimeError)
    }
