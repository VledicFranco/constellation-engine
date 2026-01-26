package io.constellation.http

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import io.constellation.Constellation
import io.constellation.execution.GlobalScheduler
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.FunctionRegistry
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import java.nio.file.Path
import scala.concurrent.duration._

/** HTTP server for the Constellation Engine API
  *
  * Provides a REST API for compiling constellation-lang programs, managing DAGs and modules, and
  * executing computational pipelines.
  *
  * Example usage:
  * {{{
  * import cats.effect.IO
  * import cats.effect.unsafe.implicits.global
  * import io.constellation.impl.ConstellationImpl
  * import io.constellation.lang.LangCompiler
  *
  * val constellation = ConstellationImpl.create.unsafeRunSync()
  * val compiler = LangCompiler.empty
  *
  * ConstellationServer
  *   .builder(constellation, compiler)
  *   .withPort(8080)
  *   .build
  *   .use(_ => IO.never)
  *   .unsafeRunSync()
  * }}}
  */
object ConstellationServer {
  private val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromName[IO]("io.constellation.http.ConstellationServer")

  /** Default port, can be overridden via CONSTELLATION_PORT environment variable */
  val DefaultPort: Int = sys.env.get("CONSTELLATION_PORT").flatMap(_.toIntOption).getOrElse(8080)

  /** Scheduler configuration from environment variables */
  object SchedulerConfig {
    /** Whether bounded scheduler is enabled (CONSTELLATION_SCHEDULER_ENABLED) */
    val enabled: Boolean = sys.env.get("CONSTELLATION_SCHEDULER_ENABLED")
      .map(_.toLowerCase)
      .exists(v => v == "true" || v == "1" || v == "yes")

    /** Maximum concurrency for bounded scheduler (CONSTELLATION_SCHEDULER_MAX_CONCURRENCY) */
    val maxConcurrency: Int = sys.env.get("CONSTELLATION_SCHEDULER_MAX_CONCURRENCY")
      .flatMap(_.toIntOption)
      .getOrElse(16)

    /** Starvation timeout for priority boost (CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT) */
    val starvationTimeout: FiniteDuration = sys.env.get("CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT")
      .flatMap(parseDuration)
      .getOrElse(30.seconds)

    private def parseDuration(s: String): Option[FiniteDuration] = {
      val pattern = """(\d+)(s|ms|m|min|h)""".r
      s.toLowerCase match {
        case pattern(num, "s")   => num.toIntOption.map(_.seconds)
        case pattern(num, "ms")  => num.toIntOption.map(_.milliseconds)
        case pattern(num, "m")   => num.toIntOption.map(_.minutes)
        case pattern(num, "min") => num.toIntOption.map(_.minutes)
        case pattern(num, "h")   => num.toIntOption.map(_.hours)
        case _ => s.toIntOption.map(_.seconds) // Default to seconds
      }
    }
  }

  /** Create a scheduler resource based on environment configuration.
    *
    * @return Resource that manages scheduler lifecycle, or unbounded if disabled
    */
  def schedulerResource: Resource[IO, GlobalScheduler] = {
    if (SchedulerConfig.enabled) {
      Resource.eval(logger.info(
        s"Creating bounded scheduler: maxConcurrency=${SchedulerConfig.maxConcurrency}, " +
        s"starvationTimeout=${SchedulerConfig.starvationTimeout}"
      )) *>
      GlobalScheduler.bounded(
        maxConcurrency = SchedulerConfig.maxConcurrency,
        starvationTimeout = SchedulerConfig.starvationTimeout
      )
    } else {
      Resource.eval(logger.info("Using unbounded scheduler (default)")) *>
      Resource.pure(GlobalScheduler.unbounded)
    }
  }

  /** Configuration for the HTTP server */
  case class Config(
      host: String = "0.0.0.0",
      port: Int = DefaultPort,
      dashboardConfig: Option[DashboardConfig] = None
  )

  /** Builder for creating a Constellation HTTP server */
  class ServerBuilder(
      constellation: Constellation,
      compiler: LangCompiler,
      functionRegistry: FunctionRegistry = FunctionRegistry.empty,
      config: Config = Config()
  ) {

    /** Set the host address */
    def withHost(host: String): ServerBuilder =
      new ServerBuilder(constellation, compiler, functionRegistry, config.copy(host = host))

    /** Set the port number */
    def withPort(port: Int): ServerBuilder =
      new ServerBuilder(constellation, compiler, functionRegistry, config.copy(port = port))

    /** Set the function registry for namespace endpoints */
    def withFunctionRegistry(registry: FunctionRegistry): ServerBuilder =
      new ServerBuilder(constellation, compiler, registry, config)

    /** Enable dashboard with default configuration */
    def withDashboard: ServerBuilder =
      new ServerBuilder(constellation, compiler, functionRegistry,
        config.copy(dashboardConfig = Some(DashboardConfig.fromEnv)))

    /** Enable dashboard with custom configuration */
    def withDashboard(dashboardConfig: DashboardConfig): ServerBuilder =
      new ServerBuilder(constellation, compiler, functionRegistry,
        config.copy(dashboardConfig = Some(dashboardConfig)))

    /** Enable dashboard with CST directory */
    def withDashboard(cstDirectory: Path): ServerBuilder =
      withDashboard(DashboardConfig.fromEnv.copy(cstDirectory = Some(cstDirectory)))

    /** Build the HTTP server resource */
    def build: Resource[IO, Server] = {
      val httpRoutes = ConstellationRoutes(constellation, compiler, functionRegistry).routes
      val lspHandler = LspWebSocketHandler(constellation, compiler)

      val host = Host.fromString(config.host).getOrElse(host"0.0.0.0")
      val port = Port.fromInt(config.port).getOrElse(port"8080")

      // Optionally create dashboard routes
      val dashboardRoutesIO: IO[Option[DashboardRoutes]] = config.dashboardConfig match {
        case Some(dashConfig) if dashConfig.enableDashboard =>
          DashboardRoutes.withDefaultStorage(constellation, compiler, dashConfig).map(Some(_))
        case _ =>
          IO.pure(None)
      }

      Resource.eval(dashboardRoutesIO).flatMap { dashboardRoutesOpt =>
        EmberServerBuilder
          .default[IO]
          .withHost(host)
          .withPort(port)
          .withIdleTimeout(scala.concurrent.duration.Duration.Inf) // Disable WebSocket idle timeout
          .withHttpWebSocketApp { wsb =>
            // Combine HTTP routes, optional dashboard routes, and WebSocket routes
            val baseRoutes = httpRoutes <+> lspHandler.routes(wsb)
            val allRoutes = dashboardRoutesOpt match {
              case Some(dashboardRoutes) => dashboardRoutes.routes <+> baseRoutes
              case None                  => baseRoutes
            }
            allRoutes.orNotFound
          }
          .build
      }
    }

    /** Run the server and return when it completes */
    def run: IO[Unit] =
      build.use { server =>
        logger.info(
          s"Constellation HTTP API server started at http://${config.host}:${config.port}"
        ) *>
          IO.never
      }
  }

  /** Create a new server builder
    *
    * @param constellation
    *   The constellation engine instance
    * @param compiler
    *   The constellation-lang compiler instance
    * @return
    *   A builder for configuring and starting the server
    */
  def builder(constellation: Constellation, compiler: LangCompiler): ServerBuilder =
    new ServerBuilder(constellation, compiler, compiler.functionRegistry)
}
