package io.constellation.http

import cats.effect.{IO, Resource}
import cats.implicits.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import io.constellation.Constellation
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.FunctionRegistry
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

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

  /** Configuration for the HTTP server */
  case class Config(
      host: String = "0.0.0.0",
      port: Int = DefaultPort
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

    /** Build the HTTP server resource */
    def build: Resource[IO, Server] = {
      val httpRoutes = ConstellationRoutes(constellation, compiler, functionRegistry).routes
      val lspHandler = LspWebSocketHandler(constellation, compiler)

      val host = Host.fromString(config.host).getOrElse(host"0.0.0.0")
      val port = Port.fromInt(config.port).getOrElse(port"8080")

      EmberServerBuilder
        .default[IO]
        .withHost(host)
        .withPort(port)
        .withIdleTimeout(scala.concurrent.duration.Duration.Inf) // Disable WebSocket idle timeout
        .withHttpWebSocketApp { wsb =>
          // Combine HTTP routes and WebSocket routes
          val allRoutes = httpRoutes <+> lspHandler.routes(wsb)
          allRoutes.orNotFound
        }
        .build
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
