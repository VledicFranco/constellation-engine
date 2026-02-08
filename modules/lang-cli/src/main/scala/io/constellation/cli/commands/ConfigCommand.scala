package io.constellation.cli.commands

import cats.effect.{ExitCode, IO}
import cats.implicits.*

import com.monovore.decline.*

import io.constellation.cli.{CliApp, CliConfig, Output, OutputFormat}

/** Config subcommands. */
sealed trait ConfigSubcommand extends CliCommand

object ConfigCommand:

  /** Show all configuration. */
  case class ConfigShow() extends ConfigSubcommand

  /** Get a specific config value. */
  case class ConfigGet(key: String) extends ConfigSubcommand

  /** Set a config value. */
  case class ConfigSet(key: String, value: String) extends ConfigSubcommand

  private val showCmd = Opts.subcommand(
    name = "show",
    help = "Show all configuration"
  )(Opts(ConfigShow()))

  private val getCmd = Opts.subcommand(
    name = "get",
    help = "Get a config value"
  ) {
    Opts.argument[String](metavar = "key").map(ConfigGet.apply)
  }

  private val setCmd = Opts.subcommand(
    name = "set",
    help = "Set a config value"
  ) {
    (Opts.argument[String](metavar = "key"), Opts.argument[String](metavar = "value"))
      .mapN(ConfigSet.apply)
  }

  val command: Opts[CliCommand] = Opts.subcommand(
    name = "config",
    help = "Manage CLI configuration"
  ) {
    showCmd orElse getCmd orElse setCmd
  }
