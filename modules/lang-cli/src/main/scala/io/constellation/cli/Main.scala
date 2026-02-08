package io.constellation.cli

import cats.effect.{ExitCode, IO, IOApp}

/** Entry point for the Constellation CLI. */
object Main extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    CliApp.run(args)
