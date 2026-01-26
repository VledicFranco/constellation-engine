package io.constellation.http

import java.nio.file.{Path, Paths}

/** Configuration for the Constellation Dashboard.
  *
  * Environment variables:
  * - CONSTELLATION_CST_DIR: Directory containing .cst files to browse
  * - CONSTELLATION_SAMPLE_RATE: Default sampling rate (0.0-1.0)
  * - CONSTELLATION_MAX_EXECUTIONS: Maximum stored executions
  * - CONSTELLATION_DASHBOARD_ENABLED: Enable/disable dashboard
  *
  * @param cstDirectory
  *   Root directory for browsing .cst files
  * @param defaultSampleRate
  *   Default sampling rate for execution storage (1.0 = store all)
  * @param maxStoredExecutions
  *   Maximum number of executions to store in memory
  * @param enableDashboard
  *   Whether the dashboard endpoints are enabled
  */
case class DashboardConfig(
    cstDirectory: Option[Path] = None,
    defaultSampleRate: Double = 1.0,
    maxStoredExecutions: Int = 1000,
    enableDashboard: Boolean = true
) {

  /** Get the CST directory or default to current working directory. */
  def getCstDirectory: Path = cstDirectory.getOrElse(Paths.get("."))

  /** Validate configuration values. */
  def validate: Either[String, DashboardConfig] = {
    if defaultSampleRate < 0.0 || defaultSampleRate > 1.0 then
      Left(s"Sample rate must be between 0.0 and 1.0, got: $defaultSampleRate")
    else if maxStoredExecutions <= 0 then
      Left(s"Max stored executions must be positive, got: $maxStoredExecutions")
    else Right(this)
  }
}

object DashboardConfig {

  /** Create configuration from environment variables. */
  def fromEnv: DashboardConfig = {
    val cstDir = sys.env.get("CONSTELLATION_CST_DIR").map(Paths.get(_))

    val sampleRate = sys.env
      .get("CONSTELLATION_SAMPLE_RATE")
      .flatMap(_.toDoubleOption)
      .getOrElse(1.0)

    val maxExecs = sys.env
      .get("CONSTELLATION_MAX_EXECUTIONS")
      .flatMap(_.toIntOption)
      .getOrElse(1000)

    val enabled = sys.env
      .get("CONSTELLATION_DASHBOARD_ENABLED")
      .map(_.toLowerCase)
      .forall(v => v == "true" || v == "1" || v == "yes")

    DashboardConfig(
      cstDirectory = cstDir,
      defaultSampleRate = sampleRate,
      maxStoredExecutions = maxExecs,
      enableDashboard = enabled
    )
  }

  /** Default configuration. */
  val default: DashboardConfig = DashboardConfig()

  /** Builder for fluent configuration. */
  class Builder private[DashboardConfig] (config: DashboardConfig) {

    def withCstDirectory(path: Path): Builder =
      new Builder(config.copy(cstDirectory = Some(path)))

    def withCstDirectory(path: String): Builder =
      withCstDirectory(Paths.get(path))

    def withSampleRate(rate: Double): Builder =
      new Builder(config.copy(defaultSampleRate = rate))

    def withMaxExecutions(max: Int): Builder =
      new Builder(config.copy(maxStoredExecutions = max))

    def withDashboardEnabled(enabled: Boolean): Builder =
      new Builder(config.copy(enableDashboard = enabled))

    def build: Either[String, DashboardConfig] = config.validate
  }

  /** Create a builder starting from environment configuration. */
  def builder: Builder = new Builder(fromEnv)

  /** Create a builder starting from default configuration. */
  def defaultBuilder: Builder = new Builder(default)
}
