package io.constellation.examples.app

import cats.effect.{IO, IOApp}
import cats.implicits._
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.http.ConstellationServer
import io.constellation.examples.app.modules.{TextModules, DataModules}
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** Example Application: Text Processing Pipeline
  *
  * This application demonstrates how to:
  * 1. Create custom domain-specific modules
  * 2. Register them with the Constellation engine
  * 3. Expose them via HTTP API
  * 4. Use constellation-lang to compose pipelines
  *
  * == Custom Modules ==
  * This app provides text processing and data analysis modules:
  *
  * '''Text Transformers:'''
  *  - Uppercase, Lowercase, Trim
  *  - Replace, Split
  *
  * '''Text Analyzers:'''
  *  - WordCount, TextLength
  *  - Contains
  *
  * '''Data Processors:'''
  *  - SumList, Average, Max, Min
  *  - Filter, Map operations
  *
  * == Usage ==
  * Start the server:
  * {{{
  * sbt "exampleApp/runMain io.constellation.examples.app.TextProcessingApp"
  * }}}
  *
  * Then compile and execute pipelines via HTTP:
  * {{{
  * # Example: Text processing pipeline
  * curl -X POST http://localhost:8080/compile \
  *   -H "Content-Type: application/json" \
  *   -d '{
  *     "source": "in text: String\nprocessed = Uppercase(text)\nout processed",
  *     "dagName": "uppercase-pipeline"
  *   }'
  *
  * # List available modules
  * curl http://localhost:8080/modules
  * }}}
  */
object TextProcessingApp extends IOApp.Simple {
  private val logger: Logger[IO] = Slf4jLogger.getLoggerFromName[IO]("io.constellation.examples.app.TextProcessingApp")

  def run: IO[Unit] = {
    for {
      // Step 1: Initialize the Constellation engine
      _ <- logger.info("Initializing Constellation Engine...")
      constellation <- ConstellationImpl.init

      // Step 2: Register custom modules
      _ <- logger.info("Registering custom modules...")
      _ <- registerCustomModules(constellation)

      // Step 3: Create compiler with registered modules
      _ <- logger.info("Creating compiler...")
      compiler <- buildCompiler(constellation)

      // Step 4: Log registered modules
      _ <- logger.info("Available custom modules:")
      modules <- constellation.getModules
      _ <- modules.traverse { module =>
        logger.info(s"  ${module.name} (v${module.majorVersion}.${module.minorVersion})")
      }

      // Step 5: Start HTTP server
      _ <- logger.info("Starting HTTP API server on port 8080...")
      _ <- logger.info("Available endpoints: GET /health, POST /compile, GET /modules, GET /dags")

      _ <- ConstellationServer
        .builder(constellation, compiler)
        .withHost("0.0.0.0")
        .withPort(8080)
        .run
    } yield ()
  }

  /** Register all custom modules with the engine */
  private def registerCustomModules(constellation: io.constellation.Constellation): IO[Unit] = {
    val allModules = TextModules.all ++ DataModules.all

    allModules.traverse { module =>
      constellation.setModule(module)
    }.void
  }

  /** Build a LangCompiler with all registered modules from Constellation */
  private def buildCompiler(constellation: io.constellation.Constellation): IO[LangCompiler] = {
    constellation.getModules.map { modules =>
      val builder = modules.foldLeft(LangCompiler.builder) { (b, moduleSpec) =>
        // Extract params and return type from module spec
        val params = moduleSpec.consumes.map { case (name, ctype) =>
          name -> io.constellation.lang.semantic.SemanticType.fromCType(ctype)
        }.toList

        val returns = moduleSpec.produces.get("out") match {
          case Some(ctype) => io.constellation.lang.semantic.SemanticType.fromCType(ctype)
          case None =>
            // If no "out", create a record from all produces
            io.constellation.lang.semantic.SemanticType.SRecord(
              moduleSpec.produces.map { case (name, ctype) =>
                name -> io.constellation.lang.semantic.SemanticType.fromCType(ctype)
              }
            )
        }

        // Register function signature for type checking
        val sig = io.constellation.lang.semantic.FunctionSignature(
          name = moduleSpec.name,
          params = params,
          returns = returns,
          moduleName = moduleSpec.name
        )

        b.withFunction(sig)
      }
      builder.build
    }
  }
}
