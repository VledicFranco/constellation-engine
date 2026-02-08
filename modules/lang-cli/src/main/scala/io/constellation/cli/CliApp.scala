package io.constellation.cli

import java.nio.file.{Files, Path, Paths}

import cats.effect.{ExitCode, IO}
import cats.implicits.*

import com.monovore.decline.*
import com.monovore.decline.effect.*

import io.circe.Json
import io.circe.syntax.*

import io.constellation.cli.commands.*

import org.http4s.Uri
import org.http4s.client.Client

/** Main CLI application logic. */
object CliApp:

  /** Exit codes following RFC-021 specification. */
  object ExitCodes:
    val Success         = ExitCode(0)
    val CompileError    = ExitCode(1)
    val RuntimeError    = ExitCode(2)
    val ConnectionError = ExitCode(3)
    val AuthError       = ExitCode(4)
    val NotFound        = ExitCode(5)
    val UsageError      = ExitCode(10)

  // Global options
  private val serverOpt = Opts.option[String](
    "server",
    short = "s",
    help = "Constellation server URL"
  ).orNone

  private val tokenOpt = Opts.option[String](
    "token",
    short = "t",
    help = "API authentication token"
  ).orNone

  private val jsonFlag = Opts.flag(
    "json",
    short = "j",
    help = "Output as JSON"
  ).orFalse

  private val quietFlag = Opts.flag(
    "quiet",
    short = "q",
    help = "Suppress non-essential output"
  ).orFalse

  private val verboseFlag = Opts.flag(
    "verbose",
    short = "v",
    help = "Verbose output for debugging"
  ).orFalse

  // Global options tuple
  case class GlobalOpts(
      server: Option[String],
      token: Option[String],
      json: Boolean,
      quiet: Boolean,
      verbose: Boolean
  )

  private val globalOpts: Opts[GlobalOpts] =
    (serverOpt, tokenOpt, jsonFlag, quietFlag, verboseFlag).mapN(GlobalOpts.apply)

  // All subcommands combined
  private val allCommands: Opts[CliCommand] =
    CompileCommand.command orElse
      RunCommand.command orElse
      VizCommand.command orElse
      ServerCommand.command orElse
      ConfigCommand.command

  // Main command
  private val mainCmd: Command[(GlobalOpts, CliCommand)] = Command(
    name = "constellation",
    header = "Constellation Engine CLI - Pipeline orchestration from the command line"
  ) {
    (globalOpts, allCommands).tupled
  }

  /** Run the CLI application. */
  def run(args: List[String]): IO[ExitCode] =
    mainCmd.parse(args, sys.env) match
      case Left(help) =>
        // Check if it's --version
        if args.contains("--version") || args.contains("-V") then
          IO.println("constellation 0.5.0").as(ExitCodes.Success)
        else
          IO.println(help.toString).as(
            if help.errors.isEmpty then ExitCodes.Success else ExitCodes.UsageError
          )
      case Right((global, cmd)) =>
        executeCommand(global, cmd)

  /** Execute a CLI command with global options. */
  private def executeCommand(global: GlobalOpts, cmd: CliCommand): IO[ExitCode] =
    for
      // Load config with CLI overrides
      config <- CliConfig.load(global.server, global.token, global.json)
      format  = config.effectiveOutput(global.json)

      // Execute the command
      result <- cmd match
        case _: ConfigCommand.ConfigShow =>
          val output = Output.configShow(config, format)
          IO.println(output).as(ExitCodes.Success)

        case c: ConfigCommand.ConfigGet =>
          CliConfig.getValue(c.key).flatMap {
            case Some(value) =>
              IO.println(Output.configValue(c.key, Some(value), format)).as(ExitCodes.Success)
            case None =>
              IO.println(Output.configValue(c.key, None, format)).as(ExitCodes.NotFound)
          }

        case c: ConfigCommand.ConfigSet =>
          CliConfig.setValue(c.key, c.value).flatMap { _ =>
            val msg = s"Set ${c.key} = ${c.value}"
            IO.println(Output.success(msg, format)).as(ExitCodes.Success)
          }.handleErrorWith { e =>
            IO.println(Output.error(e.getMessage, format)).as(ExitCodes.UsageError)
          }

        case _ =>
          // Commands that need HTTP client
          config.serverUri match
            case Left(err) =>
              IO.println(Output.error(s"Invalid server URL: $err", format)).as(ExitCodes.UsageError)
            case Right(baseUri) =>
              HttpClient.client.use { implicit client =>
                executeHttpCommand(cmd, baseUri, config, format, global.quiet)
              }
    yield result

  /** Execute commands that require HTTP. */
  private def executeHttpCommand(
      cmd: CliCommand,
      baseUri: Uri,
      config: CliConfig,
      format: OutputFormat,
      quiet: Boolean
  )(using client: Client[IO]): IO[ExitCode] =
    cmd match
      case c: CompileCommand =>
        CompileCommand.execute(c, baseUri, config.server.token, format, quiet)

      case c: RunCommand =>
        RunCommand.execute(c, baseUri, config.server.token, format, quiet)

      case c: VizCommand =>
        VizCommand.execute(c, baseUri, config.server.token, format, quiet)

      case c: ServerCommand.ServerSubcommand =>
        ServerCommand.execute(c, baseUri, config.server.token, format, quiet)

      case _ =>
        IO.println(Output.error("Unknown command", format)).as(ExitCodes.UsageError)
