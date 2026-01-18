package io.constellation.examples.app.server

import cats.effect.{IO, IOApp}
import cats.implicits._
import io.constellation.impl.ConstellationImpl
import io.constellation.examples.app.ExampleLib
import io.constellation.stdlib.StdLib
import io.constellation.http.ConstellationServer

/** Example HTTP server with standard library + example app functions
  *
  * This server demonstrates how to start a Constellation HTTP API server
  * with all available modules including DataModules and TextModules.
  *
  * Once started, you can:
  * - Compile constellation-lang programs: POST /compile
  * - Execute compiled DAGs: POST /execute
  * - List available DAGs: GET /dags
  * - List available modules: GET /modules
  * - Check server health: GET /health
  * - Connect via WebSocket LSP: ws://localhost:8080/lsp
  */
object ExampleServer extends IOApp.Simple {

  def run: IO[Unit] = {
    for {
      // Create constellation engine instance
      constellation <- ConstellationImpl.init

      // Register all modules (StdLib + ExampleLib) for runtime execution and LSP
      allModules = (StdLib.allModules ++ ExampleLib.allModules).values.toList
      _ <- allModules.traverse(constellation.setModule)
      _ <- IO.println(s"Registered ${allModules.size} modules")

      // Create compiler with standard library + example app functions
      compiler = ExampleLib.compiler

      // Start the HTTP server
      _ <- IO.println("Constellation HTTP API server started at http://0.0.0.0:8080")
      _ <- ConstellationServer
        .builder(constellation, compiler)
        .withHost("0.0.0.0")
        .withPort(8080)
        .run
    } yield ()
  }
}
