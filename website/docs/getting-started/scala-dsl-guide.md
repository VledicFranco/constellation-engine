---
title: "Scala DSL Guide"
sidebar_position: 4
description: "Compose type-safe pipelines in Scala without writing .cst files"
---

# Scala DSL Guide

The Scala DSL lets you compose pipelines entirely in Scala — no `.cst` files needed. Wiring errors are caught at **compile time** rather than at runtime.

```scala
import io.constellation.dsl.*

val pipeline: TypedPipeline = Pipeline.define("textProcessing") { p =>
  val rawRef     = p.input[TextInput]("raw")
  val trimmedRef = p.step(rawRef)(trimBuilder).retry(2).timeout(5.seconds)
  val upperRef   = p.step(trimmedRef)(uppercaseBuilder).cache(10.minutes)
  p.output("result", upperRef)
}
```

## When to Use the Scala DSL vs. `.cst`

| | Scala DSL | `.cst` text DSL |
|---|---|---|
| **Wiring errors caught at** | Compile time | Runtime |
| **Pipelines defined in** | Scala source | External files |
| **Hot-reload support** | No | Yes |
| **IDE autocomplete on wiring** | Yes (full type inference) | Via LSP extension |
| **Multi-field fan-in** | `p.assemble` | Natural syntax |
| **Best for** | In-process pipelines, library code, testing | Runtime-configurable, external, hot-swappable pipelines |

Use the Scala DSL when your pipeline topology is known at compile time and you want the compiler to catch wiring errors. Use `.cst` when you need hot-reload or external pipeline configuration.

## Add the Dependency

The DSL lives in `constellation-runtime` — no additional dependency needed if you already include it:

```scala
val constellationVersion = "0.7.0"

libraryDependencies ++= Seq(
  "io.github.vledicfranco" %% "constellation-core"    % constellationVersion,
  "io.github.vledicfranco" %% "constellation-runtime" % constellationVersion
)
```

No `constellation-lang-compiler` is needed for the Scala DSL.

## Define I/O Types

Each step's input and output must be a **single-field case class**. The field name becomes the internal node nickname used for wiring.

```scala
case class RawInput(text: String)
case class TrimOutput(text: String)
case class UpperOutput(result: String)
```

For multi-field fan-in modules, use any number of fields — but the output must still be single-field:

```scala
case class SummaryInput(text: String, result: String)  // multi-field — for p.assemble only
case class SummaryOutput(combined: String)              // single-field output
```

:::note
Using a multi-field type with `p.step` or `p.adapt` is a **compile error**, not a runtime error.
:::

## Define Module Builders

Pass `ModuleBuilder[I, O]` values directly to the DSL — do not call `.build()`. The DSL calls it internally.

```scala
val trimBuilder: ModuleBuilder[RawInput, TrimOutput] =
  ModuleBuilder
    .metadata("Trim", "Remove leading and trailing whitespace", 1, 0)
    .implementationPure[RawInput, TrimOutput](in => TrimOutput(in.text.trim))

val uppercaseBuilder: ModuleBuilder[TrimOutput, UpperOutput] =
  ModuleBuilder
    .metadata("Uppercase", "Convert text to uppercase", 1, 0)
    .implementationPure[TrimOutput, UpperOutput](in => UpperOutput(in.text.toUpperCase))
```

## Build a Pipeline

Use `Pipeline.define` to open a builder scope:

```scala
import scala.concurrent.duration.*
import io.constellation.dsl.*

val pipeline: TypedPipeline = Pipeline.define("myPipeline") { p =>

  // 1. Declare an input
  val rawRef = p.input[RawInput]("raw")

  // 2. Wire steps (with optional call options)
  val trimmedRef = p.step(rawRef)(trimBuilder).retry(2).timeout(5.seconds)
  val upperRef   = p.step(trimmedRef)(uppercaseBuilder).cache(10.minutes)

  // 3. Declare outputs
  p.output("result", upperRef)
}
```

`Pipeline.define` is pure — no `IO`. The returned `TypedPipeline` is an immutable value.

## Execute the Pipeline

```scala
import cats.effect.IO
import cats.implicits.*
import io.constellation.*
import io.constellation.impl.ConstellationImpl

def run(input: String): IO[String] =
  for {
    constellation <- ConstellationImpl.init

    // Register all modules defined in the pipeline
    _ <- pipeline.registerModules(constellation)

    // Run with named inputs
    result <- constellation.run(
      pipeline.load,
      Map("raw" -> CValue.CString(input))
    )
  } yield result.outputs.get("result") match {
    case Some(CValue.CString(v)) => v
    case other                   => sys.error(s"Unexpected output: $other")
  }
```

`pipeline.registerModules` replaces manual `constellation.setModule(...)` calls — it registers all modules declared via `p.step` and `p.assemble` in one call.

`pipeline.load` wraps the compiled `PipelineImage` into a `LoadedPipeline` ready for `Constellation.run`.

## p.adapt — Bridging Type Boundaries

Use `p.adapt` when two steps have structurally compatible data but different Scala wrapper types:

```scala
case class RawInput(text: String)
case class TrimInput(text: String)   // same field "text", different wrapper

val rawRef      = p.input[RawInput]("raw")
val trimInputRef = p.adapt(rawRef)(r => TrimInput(r.text))
val trimmedRef  = p.step(trimInputRef)(trimBuilder)
```

`p.adapt` creates an anonymous **synthetic module** that runs inline without being registered in the Constellation module registry. You don't need to call `registerModules` for it.

Both the input and output of `p.adapt` must be single-field case classes.

## p.assemble — Fan-In from Multiple Nodes

Use `p.assemble` to merge outputs from multiple upstream steps into a single multi-field input:

```scala
case class SummaryInput(text: String, result: String)
case class SummaryOutput(combined: String)

val summarizeBuilder: ModuleBuilder[SummaryInput, SummaryOutput] =
  ModuleBuilder
    .metadata("Summarize", "Combine trimmed and uppercased text", 1, 0)
    .implementationPure[SummaryInput, SummaryOutput](in =>
      SummaryOutput(s"'${in.text}' → '${in.result}'")
    )

// Fan-in: trimmedRef provides .text, upperRef provides .result
val summaryRef = p.assemble(summarizeBuilder) { ctx =>
  SummaryInput(
    text   = ctx.field(trimmedRef, _.text),
    result = ctx.field(upperRef, _.result)
  )
}
p.output("summary", summaryRef)
```

`ctx.field(ref, _.fieldName)` is a **compile-time macro** that:
1. Extracts the field name `"fieldName"` from the selector at compile time
2. Records the dependency (`ref` → `"fieldName"`) for edge wiring at pipeline-definition time
3. Returns a null placeholder (immediately discarded — only the side effect matters)

The field name in the selector (e.g. `_.text`) must match the upstream ref's type field name. The corresponding field name in `SummaryInput` is what the module runtime receives.

## Call Options on TypedRef

Every `TypedRef[A]` returned by `p.step`, `p.assemble`, and `p.adapt` supports 11 call option methods. Options are immutable and chain-able:

```scala
val ref = p.step(inputRef)(myBuilder)
  .retry(3)
  .timeout(10.seconds)
  .backoff("exponential")
  .cache(5.minutes)
  .priority(80)
```

| Method | Description |
|--------|-------------|
| `.retry(n)` | Retry the module up to `n` times on failure |
| `.timeout(d)` | Abort if not complete within duration `d` |
| `.delay(d)` | Delay execution by `d` |
| `.backoff(strategy)` | Retry backoff: `"fixed"`, `"linear"`, or `"exponential"` |
| `.cache(d)` | Cache output for duration `d` |
| `.cacheBackend(name)` | Use a named cache backend |
| `.throttle(count, per)` | At most `count` calls per `per` window |
| `.concurrency(n)` | Maximum concurrent executions |
| `.onError(strategy)` | `"propagate"`, `"skip"`, `"log"`, or `"wrap"` |
| `.lazyEval` | Only evaluate when output is consumed |
| `.priority(p)` | Scheduling priority (higher = runs sooner) |

Options are flushed into `pipeline.callOptions` (keyed by module UUID) when the ref is consumed by the next `p.step`, `p.assemble`, or `p.output` call.

## What TypedPipeline Contains

`Pipeline.define` returns a `TypedPipeline` with:

| Field | Description |
|-------|-------------|
| `spec` | The compiled `DagSpec` (modules, data nodes, edges, outputs) |
| `callOptions` | `Map[UUID, ModuleCallOptions]` — keyed by module node UUID |
| `syntheticModules` | Anonymous adapt modules (from `p.adapt`) — never registered |
| `moduleBuilders` | Named modules (from `p.step` and `p.assemble`) — registered by `registerModules` |
| `image` | `PipelineImage` with structural hash, computed once at construction |

```scala
// Inspect the compiled structure
println(pipeline.spec.modules.size)         // number of module nodes
println(pipeline.spec.data.size)            // number of data nodes
println(pipeline.image.structuralHash)      // deterministic hash of topology
```

## Complete Example

The following example matches the topology from `DslExamples.scala` in the example-app module:

```scala
import scala.concurrent.duration.*
import cats.effect.IO
import cats.implicits.*
import io.constellation.*
import io.constellation.dsl.*
import io.constellation.impl.ConstellationImpl

object TextPipelineApp {

  case class RawInput(text: String)
  case class TrimInput(text: String)
  case class TrimOutput(text: String)
  case class UpperOutput(result: String)
  case class SummaryInput(text: String, result: String)
  case class SummaryOutput(combined: String)

  val trimBuilder: ModuleBuilder[TrimInput, TrimOutput] =
    ModuleBuilder
      .metadata("Trim", "Remove whitespace", 1, 0)
      .implementationPure[TrimInput, TrimOutput](in => TrimOutput(in.text.trim))

  val uppercaseBuilder: ModuleBuilder[TrimOutput, UpperOutput] =
    ModuleBuilder
      .metadata("Uppercase", "Convert to uppercase", 1, 0)
      .implementationPure[TrimOutput, UpperOutput](in => UpperOutput(in.text.toUpperCase))

  val summarizeBuilder: ModuleBuilder[SummaryInput, SummaryOutput] =
    ModuleBuilder
      .metadata("Summarize", "Combine trimmed and uppercased text", 1, 0)
      .implementationPure[SummaryInput, SummaryOutput](in =>
        SummaryOutput(s"'${in.text}' → '${in.result}'")
      )

  val pipeline: TypedPipeline = Pipeline.define("textProcessing") { p =>
    val rawRef       = p.input[RawInput]("raw")
    val trimInputRef = p.adapt(rawRef)(r => TrimInput(r.text))
    val trimmedRef   = p.step(trimInputRef)(trimBuilder).retry(2).timeout(5.seconds)
    val upperRef     = p.step(trimmedRef)(uppercaseBuilder).cache(10.minutes)
    val summaryRef   = p.assemble(summarizeBuilder) { ctx =>
      SummaryInput(
        text   = ctx.field(trimmedRef, _.text),
        result = ctx.field(upperRef, _.result)
      )
    }
    p.output("summary", summaryRef)
  }

  def run(input: String): IO[String] =
    for {
      c      <- ConstellationImpl.init
      _      <- pipeline.registerModules(c)
      result <- c.run(pipeline.load, Map("raw" -> CValue.CString(input)))
    } yield result.outputs.get("summary") match {
      case Some(CValue.CString(v)) => v
      case other                   => sys.error(s"Unexpected: $other")
    }
}
```

## Equivalent `.cst` Program

The example above is equivalent to this constellation-lang program:

```constellation
in raw: String
trimmed  = Trim(raw)
upper    = Uppercase(trimmed)
summary  = Summarize(trimmed, upper)
out summary
```

The key difference: the DSL catches field name mismatches at compile time; the `.cst` program catches them at runtime during type-checking.

## Next Steps

- [Embedding Guide](./embedding-guide.md) — Using Constellation in a JVM application with `.cst`
- [Tutorial](./tutorial.md) — Learning constellation-lang syntax
- RFC-028 (`rfcs/rfc-028-scala-dsl.md` in the repository) — Design specification for the Scala DSL
