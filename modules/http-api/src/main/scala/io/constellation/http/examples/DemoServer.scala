package io.constellation.http.examples

import cats.effect.{IO, IOApp}
import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib
import io.constellation.http.ConstellationServer

/** Demo HTTP server with standard library functions pre-loaded
  *
  * This example demonstrates how to start a Constellation HTTP API server with the standard library
  * functions available for use in constellation-lang programs.
  *
  * Once started, you can:
  *   - Compile constellation-lang pipelines: POST /compile
  *   - Execute compiled DAGs: POST /execute
  *   - List available DAGs: GET /dags
  *   - List available modules: GET /modules
  *   - Check server health: GET /health
  *
  * Example curl commands:
  * {{{
  * # Health check
  * curl http://localhost:8080/health
  *
  * # Compile a program
  * curl -X POST http://localhost:8080/compile \
  *   -H "Content-Type: application/json" \
  *   -d '{
  *     "source": "in a: Int\nin b: Int\nresult = add(a, b)\nout result",
  *     "dagName": "addition-dag"
  *   }'
  *
  * # List available modules
  * curl http://localhost:8080/modules
  *
  * # List available DAGs
  * curl http://localhost:8080/dags
  * }}}
  */
object DemoServer extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      // Create constellation engine instance
      constellation <- ConstellationImpl.init

      // Create compiler with standard library functions
      compiler = StdLib.compiler

      // Start the HTTP server
      _ <- ConstellationServer
        .builder(constellation, compiler)
        .withHost("0.0.0.0")
        .withPort(8080)
        .run
    } yield ()
}
