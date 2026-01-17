package io.constellation.examples.app

import cats.effect.{IO, IOApp}
import cats.implicits._
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.http.ConstellationServer
import io.constellation.examples.app.modules.{TextModules, DataModules}

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

  def run: IO[Unit] = {
    for {
      // Step 1: Initialize the Constellation engine
      _ <- IO.println("🚀 Initializing Constellation Engine...")
      constellation <- ConstellationImpl.init

      // Step 2: Register custom modules
      _ <- IO.println("📦 Registering custom modules...")
      _ <- registerCustomModules(constellation)

      // Step 3: Create compiler (no stdlib functions needed for this example)
      _ <- IO.println("🔧 Creating compiler...")
      compiler = LangCompiler.empty

      // Step 4: Print registered modules
      _ <- IO.println("\n✅ Available custom modules:")
      modules <- constellation.getModules
      _ <- modules.traverse { module =>
        IO.println(s"   • ${module.name} (v${module.majorVersion}.${module.minorVersion})")
      }

      // Step 5: Start HTTP server
      _ <- IO.println(s"\n🌐 Starting HTTP API server on port 8080...")
      _ <- IO.println("   Available endpoints:")
      _ <- IO.println("     GET  /health          - Health check")
      _ <- IO.println("     POST /compile         - Compile constellation-lang program")
      _ <- IO.println("     GET  /modules         - List available modules")
      _ <- IO.println("     GET  /dags            - List compiled DAGs")
      _ <- IO.println("")

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
}
