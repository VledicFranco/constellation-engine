package io.constellation.cli.commands

import java.nio.file.{Files, Path, Paths}

import cats.effect.{ExitCode, IO}
import cats.implicits.*

import com.monovore.decline.*

import io.circe.{Decoder, Json}
import io.circe.generic.semiauto.*

import io.constellation.cli.{CliApp, HttpClient, Output, OutputFormat}

import org.http4s.Uri
import org.http4s.client.Client

/** Compile command: type-check a pipeline file. */
case class CompileCommand(
    file: Path,
    watch: Boolean = false
) extends CliCommand

object CompileCommand:

  /** API response model for /compile endpoint. */
  case class CompileResponse(
      success: Boolean,
      structuralHash: Option[String] = None,
      syntacticHash: Option[String] = None,
      name: Option[String] = None,
      errors: List[String] = Nil
  )

  object CompileResponse:
    given Decoder[CompileResponse] = Decoder.instance { c =>
      for
        success        <- c.downField("success").as[Boolean]
        structuralHash <- c.downField("structuralHash").as[Option[String]]
        syntacticHash  <- c.downField("syntacticHash").as[Option[String]]
        name           <- c.downField("name").as[Option[String]]
        errors         <- c.downField("errors").as[Option[List[String]]].map(_.getOrElse(Nil))
      yield CompileResponse(success, structuralHash, syntacticHash, name, errors)
    }

  private val fileArg = Opts.argument[Path](metavar = "file")

  private val watchFlag = Opts.flag(
    "watch",
    short = "w",
    help = "Watch mode: recompile on changes"
  ).orFalse

  val command: Opts[CliCommand] = Opts.subcommand(
    name = "compile",
    help = "Compile and type-check a pipeline file"
  ) {
    (fileArg, watchFlag).mapN(CompileCommand.apply)
  }

  /** Execute the compile command. */
  def execute(
      cmd: CompileCommand,
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
          compileSource(source, cmd.file.getFileName.toString, baseUri, token, format, quiet)
    yield exitCode

  /** Read source code from file. */
  private def readSourceFile(path: Path): IO[Either[String, String]] =
    IO.blocking {
      if !Files.exists(path) then
        Left(s"File not found: $path")
      else if !Files.isRegularFile(path) then
        Left(s"Not a regular file: $path")
      else
        Right(Files.readString(path))
    }.handleError { e =>
      Left(s"Failed to read file: ${e.getMessage}")
    }

  /** Compile source code via API. */
  private def compileSource(
      source: String,
      name: String,
      baseUri: Uri,
      token: Option[String],
      format: OutputFormat,
      quiet: Boolean
  )(using client: Client[IO]): IO[ExitCode] =
    val uri = baseUri / "compile"
    val body = Json.obj(
      "source" -> Json.fromString(source),
      "name"   -> Json.fromString(name.stripSuffix(".cst"))
    )

    HttpClient.post[CompileResponse](uri, body, token).flatMap {
      case HttpClient.Success(resp) =>
        if resp.success then
          val hashInfo = resp.structuralHash.map(h => s" (hash: ${h.take(12)}...)").getOrElse("")
          val msg = s"Compilation successful$hashInfo"
          IO.println(Output.success(msg, format)).as(CliApp.ExitCodes.Success)
        else
          IO.println(Output.compilationErrors(resp.errors, format)).as(CliApp.ExitCodes.CompileError)

      case HttpClient.ApiError(401, _) =>
        IO.println(Output.error("Authentication required", format)).as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(403, _) =>
        IO.println(Output.error("Access denied", format)).as(CliApp.ExitCodes.AuthError)

      case HttpClient.ApiError(_, msg) =>
        IO.println(Output.error(msg, format)).as(CliApp.ExitCodes.CompileError)

      case HttpClient.ConnectionError(msg) =>
        IO.println(Output.connectionError(baseUri.renderString, msg, format))
          .as(CliApp.ExitCodes.ConnectionError)

      case HttpClient.ParseError(msg) =>
        IO.println(Output.error(s"Failed to parse server response: $msg", format))
          .as(CliApp.ExitCodes.RuntimeError)
    }
