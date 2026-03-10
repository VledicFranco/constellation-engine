package io.constellation.examples.app.dsl

import scala.concurrent.duration.*

import cats.effect.IO
import cats.implicits.*

import io.constellation.*
import io.constellation.dsl.*
import io.constellation.impl.ConstellationImpl

/** Demonstrates the Scala DSL for type-safe pipeline composition.
  *
  * Shows how to build a multi-step pipeline entirely in Scala 3 without writing `.cst` files. The
  * same topology can be expressed as the equivalent `.cst` program:
  *
  * {{{
  *   in raw: String
  *   trimmed  = Trim(raw)
  *   upper    = Uppercase(trimmed)
  *   summary  = Summarize(trimmed, upper)
  *   out summary
  * }}}
  *
  * ==DSL features demonstrated==
  *   - `p.input` — declaring a pipeline input
  *   - `p.adapt` — bridging two structurally-equivalent but type-incompatible wrappers
  *   - `p.step` with call options (`.retry`, `.timeout`, `.cache`)
  *   - `p.assemble` — fan-in from two upstream nodes
  *   - `p.output` — declaring a pipeline output
  */
object DslExamples {

  // ---------------------------------------------------------------------------
  // I/O types — single-field wrappers as required by the DSL
  // ---------------------------------------------------------------------------

  case class RawInput(text: String)
  case class TrimInput(text: String)
  case class TrimOutput(text: String)
  case class UpperOutput(result: String)

  // Fan-in type: field names must match the upstream wrapper field names so that
  // `ctx.field(trimmedRef, _.text)` and `ctx.field(upperRef, _.result)` compile.
  case class SummaryInput(text: String, result: String)
  case class SummaryOutput(combined: String)

  // ---------------------------------------------------------------------------
  // Module builders — NOT .build(); the DSL consumes ModuleBuilder[I, O] directly
  // ---------------------------------------------------------------------------

  val trimBuilder: ModuleBuilder[TrimInput, TrimOutput] =
    ModuleBuilder
      .metadata("Trim", "Remove leading and trailing whitespace", 1, 0)
      .implementationPure[TrimInput, TrimOutput](in => TrimOutput(in.text.trim))

  val uppercaseBuilder: ModuleBuilder[TrimOutput, UpperOutput] =
    ModuleBuilder
      .metadata("Uppercase", "Convert text to uppercase", 1, 0)
      .implementationPure[TrimOutput, UpperOutput](in => UpperOutput(in.text.toUpperCase))

  val summarizeBuilder: ModuleBuilder[SummaryInput, SummaryOutput] =
    ModuleBuilder
      .metadata("Summarize", "Combine trimmed and uppercased text", 1, 0)
      .implementationPure[SummaryInput, SummaryOutput](in =>
        SummaryOutput(s"'${in.text}' → '${in.result}'")
      )

  // ---------------------------------------------------------------------------
  // Pipeline definition
  //
  // Topology:
  //   raw: RawInput
  //     → adapt → TrimInput
  //       → trim (retry 2, timeout 5s) → TrimOutput
  //         → uppercase (cache 10m) → UpperOutput
  //   [TrimOutput, UpperOutput] → assemble → SummaryOutput
  //                                           ↓
  //                                        "summary"
  // ---------------------------------------------------------------------------

  val textPipeline: TypedPipeline = Pipeline.define("textProcessing") { p =>

    // 1. Declare pipeline input
    val rawRef = p.input[RawInput]("raw")

    // 2. Adapt: RawInput → TrimInput (same field "text"; bridges a type boundary)
    val trimInputRef = p.adapt(rawRef)(r => TrimInput(r.text))

    // 3. Trim with resilience options
    val trimmedRef = p
      .step(trimInputRef)(trimBuilder)
      .retry(2)
      .timeout(5.seconds)

    // 4. Uppercase with caching
    val upperRef = p
      .step(trimmedRef)(uppercaseBuilder)
      .cache(10.minutes)

    // 5. Assemble: fan-in from trimmed (_.text) and upper (_.result)
    val summaryRef = p.assemble(summarizeBuilder) { ctx =>
      SummaryInput(
        text = ctx.field(trimmedRef, _.text),
        result = ctx.field(upperRef, _.result)
      )
    }

    // 6. Declare output
    p.output("summary", summaryRef)
  }

  // ---------------------------------------------------------------------------
  // Execution helper — registers all named modules, then runs the pipeline
  // ---------------------------------------------------------------------------

  def run(input: String): IO[String] =
    for {
      constellation <- ConstellationImpl.init
      _             <- textPipeline.registerModules(constellation)
      result <- constellation.run(
        textPipeline.load,
        Map("raw" -> CValue.CString(input))
      )
    } yield result.outputs.get("summary") match {
      case Some(CValue.CString(v)) => v
      case other                   => sys.error(s"Unexpected output: $other")
    }
}
