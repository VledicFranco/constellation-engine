package io.constellation.examples.app.server

import cats.effect.{IO, IOApp}
import cats.implicits.*
import io.constellation.impl.ConstellationImpl
import io.constellation.examples.app.ExampleLib
import io.constellation.stdlib.StdLib
import io.constellation.http.ConstellationServer
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
  * Once started, you can:
  *   - Compile constellation-lang programs: POST /compile
  *   - Execute compiled DAGs: POST /execute
  *   - List available DAGs: GET /dags
  *   - List available modules: GET /modules
  *   - Check server health: GET /health
  *   - Connect via WebSocket LSP: ws://localhost:{port}/lsp
  */
object ExampleServer extends IOApp.Simple {
  private val logger: Logger[IO] =
    Slf4jLogger.getLoggerFromName[IO]("io.constellation.examples.app.server.ExampleServer")

  def run: IO[Unit] =
    for {
      // Create constellation engine instance
      constellation <- ConstellationImpl.init

      // Register all modules (StdLib + ExampleLib) for runtime execution and LSP
      allModules = (StdLib.allModules ++ ExampleLib.allModules).values.toList
      _ <- allModules.traverse(constellation.setModule)
      _ <- logger.info(s"Registered ${allModules.size} modules")

      // Create compiler with standard library + example app functions
      compiler = ExampleLib.compiler

      // Start the HTTP server (port from CONSTELLATION_PORT env var, defaults to 8080)
      port = ConstellationServer.DefaultPort
      _ <- logger.info(s"Constellation HTTP API server starting at http://0.0.0.0:$port")
      _ <- logger.info(s"LSP WebSocket available at ws://localhost:$port/lsp")
      _ <- ConstellationServer
        .builder(constellation, compiler)
        .withHost("0.0.0.0")
        .run
    } yield ()
}
