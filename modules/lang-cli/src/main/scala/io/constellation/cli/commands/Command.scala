package io.constellation.cli.commands

import cats.effect.{ExitCode, IO}

import io.constellation.cli.OutputFormat

import org.http4s.Uri
import org.http4s.client.Client

/** Base trait for CLI commands. */
trait CliCommand
