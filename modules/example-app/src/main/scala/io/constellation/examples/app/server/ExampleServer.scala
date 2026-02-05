package io.constellation.examples.app.server

import java.nio.file.Paths

import cats.effect.{IO, IOApp}
import cats.implicits.*

import io.constellation.examples.app.ExampleLib
import io.constellation.execution.GlobalScheduler
import io.constellation.http.{ConstellationServer, DashboardConfig}
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.CachingLangCompiler
import io.constellation.stdlib.StdLib

import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** Example HTTP server with standard library + example app functions
  *
  * This server demonstrates how to start a Constellation HTTP API server with all available modules
  * including DataModules and TextModules.
  *
  * The server port can be configured via the CONSTELLATION_PORT environment variable. Default port
  * is 8080. For multi-agent setups, use: 8080 + agent_number
  *
  * The dashboard can be configured via environment variables:
  *   - CONSTELLATION_CST_DIR: Directory containing .cst files to browse (default: current
  *     directory)
  *   - CONSTELLATION_SAMPLE_RATE: Execution sampling rate 0.0-1.0 (default: 1.0)
  *   - CONSTELLATION_MAX_EXECUTIONS: Max stored executions (default: 1000)
  *
  * Once started, you can:
  *   - Access the dashboard: http://localhost:{port}/dashboard
  *   - Compile pipelines: POST /compile (returns structuralHash for content-addressed lookup)
  *   - Execute by reference: POST /execute (accepts name or "sha256:<hash>" via `ref` field)
  *   - Compile and run: POST /run (stores image, returns structuralHash)
  *   - List stored pipelines: GET /pipelines
  *   - Pipeline metadata: GET /pipelines/:ref
  *   - Repoint alias: PUT /pipelines/:name/alias
  *   - List available DAGs: GET /dags (legacy)
  *   - List available modules: GET /modules
  *   - Check server health: GET /health
  *   - Connect via WebSocket LSP: ws://localhost:{port}/lsp
  */
object ExampleServer extends IOApp.Simple {
  private val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromName[IO]("io.constellation.examples.app.server.ExampleServer")

  def run: IO[Unit] =
    ConstellationServer.schedulerResource.use { scheduler =>
      for {
        // Log scheduler configuration
        _ <-
          if ConstellationServer.SchedulerConfig.enabled then {
            logger.info(
              s"Bounded scheduler enabled: maxConcurrency=${ConstellationServer.SchedulerConfig.maxConcurrency}, " +
                s"starvationTimeout=${ConstellationServer.SchedulerConfig.starvationTimeout}"
            )
          } else {
            logger.info("Using unbounded scheduler (default)")
          }

        // Create constellation engine instance with scheduler
        constellation <- ConstellationImpl.initWithScheduler(scheduler)

        // Register all modules (StdLib + ExampleLib) for runtime execution and LSP
        allModules = (StdLib.allModules ++ ExampleLib.allModules).values.toList
        _ <- allModules.traverse(constellation.setModule)
        _ <- logger.info(s"Registered ${allModules.size} modules")

        // Create compiler with standard library + example app functions
        // Wrap in caching compiler to avoid redundant compilations on every keystroke
        baseCompiler = ExampleLib.compiler
        compiler     = CachingLangCompiler.withDefaults(baseCompiler)
        _ <- logger.info("Compilation caching enabled")

        // Configure dashboard (reads from environment variables)
        dashboardConfig = DashboardConfig.fromEnv
        cstDir          = dashboardConfig.getCstDirectory.toAbsolutePath.toString

        // Start the HTTP server (port from CONSTELLATION_PORT env var, defaults to 8080)
        port = ConstellationServer.DefaultPort
        _ <- logger.info(s"Constellation HTTP API server starting at http://0.0.0.0:$port")
        _ <- logger.info(s"Dashboard available at http://localhost:$port/dashboard")
        _ <- logger.info(s"Dashboard CST directory: $cstDir")
        _ <- logger.info(s"LSP WebSocket available at ws://localhost:$port/lsp")
        _ <- ConstellationServer
          .builder(constellation, compiler)
          .withHost("0.0.0.0")
          .withDashboard(dashboardConfig)
          .run
      } yield ()
    }
}
