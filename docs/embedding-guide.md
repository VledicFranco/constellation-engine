# Embedding Guide

This guide walks you through embedding Constellation Engine in your own JVM application. By the end you will have a standalone program that compiles a constellation-lang script, executes it, and prints the result — no HTTP server required.

## Overview

Constellation Engine is an embeddable library. The HTTP server and VSCode extension are optional layers built on top of the same API you use directly:

```
Your Application
  └─ Constellation API  (compile, execute, manage modules)
       ├─ Runtime        (DAG execution, scheduling, lifecycle)
       ├─ Compiler       (parse, type-check, compile constellation-lang)
       └─ StdLib         (built-in modules: math, string, list, etc.)
```

All interactions go through two main entry points:

| Entry Point | Purpose |
|-------------|---------|
| `Constellation` trait | Register modules, store programs, execute pipelines |
| `LangCompiler` | Compile constellation-lang source into `LoadedProgram` |

## Add Dependencies

### sbt

```scala
val constellationVersion = "0.3.0"

libraryDependencies ++= Seq(
  "io.constellation" %% "constellation-core"          % constellationVersion,
  "io.constellation" %% "constellation-runtime"       % constellationVersion,
  "io.constellation" %% "constellation-lang-compiler" % constellationVersion,
  "io.constellation" %% "constellation-lang-stdlib"   % constellationVersion
)
```

Add the HTTP module only if you need the server:

```scala
libraryDependencies += "io.constellation" %% "constellation-http-api" % constellationVersion
```

### Required Transitive Dependencies

The library pulls in:

- **Cats Effect 3** — `IO` monad for all effectful operations
- **Circe** — JSON encoding/decoding
- **cats-parse** — parser combinators (compiler module)
- **http4s** — only if you include `http-api`

## Minimal Setup

```scala
import cats.effect._
import cats.implicits._
import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib
import io.constellation.lang.LangCompiler

object MinimalExample extends IOApp.Simple {

  val source = """
    in text: String
    result = Uppercase(text)
    out result
  """

  def run: IO[Unit] =
    for {
      // 1. Create a Constellation instance
      constellation <- ConstellationImpl.init

      // 2. Register standard library modules
      _ <- StdLib.allModules.values.toList.traverse(constellation.setModule)

      // 3. Create a compiler with StdLib function signatures
      compiler = StdLib.compiler

      // 4. Compile the source
      compiled <- IO.fromEither(
        compiler.compile(source, "my-pipeline").leftMap(errs =>
          new RuntimeException(errs.map(_.message).mkString("\n"))
        )
      )

      // 5. Execute the pipeline
      sig <- constellation.run(
        compiled.program,
        inputs = Map("text" -> io.constellation.TypeSystem.CValue.VString("hello world"))
      )

      // 6. Read outputs
      _ <- IO.println(s"Outputs: ${sig.outputs}")
    } yield ()
}
```

## Compile and Execute

### Compilation

`LangCompiler.compile` parses, type-checks, and compiles constellation-lang source into a `CompilationOutput`:

```scala
val result: Either[List[CompileError], CompilationOutput] =
  compiler.compile(source, dagName)
```

`CompilationOutput` contains:

| Field | Type | Description |
|-------|------|-------------|
| `program` | `LoadedProgram` | The compiled program (includes `image.dagSpec` and `syntheticModules`) |
| `warnings` | `List[CompileWarning]` | Non-fatal compilation warnings |

### Execution

Pass the compiled program and inputs to the runtime:

```scala
val sig: IO[DataSignature] = constellation.run(
  compiled.program,
  inputs           // Map[String, CValue]
)
```

`DataSignature` provides:

| Field | Description |
|-------|-------------|
| `outputs` | `Map[String, CValue]` — declared pipeline output values |
| `computedNodes` | `Map[String, CValue]` — all intermediate computed values |
| `status` | `PipelineStatus` — Completed, Suspended, or Failed |
| `missingInputs` | `List[String]` — inputs that were not provided |

### Reusing Compiled Programs

Compile once, execute many times:

```scala
// Store the program image for later execution
val hash = constellation.programStore.store(compiled.program.image)

// Optionally give it a human-readable alias
constellation.programStore.alias("my-pipeline", hash)

// Execute by name or hash
val sig = constellation.run("my-pipeline", inputs, ExecutionOptions())
```

## Complete Runnable Example

This self-contained example compiles and runs a two-step text pipeline:

```scala
import cats.effect._
import cats.implicits._
import io.constellation._
import io.constellation.TypeSystem._
import io.constellation.TypeSystem.CValue._
import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib
import io.constellation.lang.LangCompiler

object TextPipeline extends IOApp.Simple {

  val source = """
    in text: String

    trimmed = Trim(text)
    upper   = Uppercase(trimmed)
    words   = WordCount(upper)

    out upper
    out words
  """

  def run: IO[Unit] =
    for {
      constellation <- ConstellationImpl.init
      _             <- StdLib.allModules.values.toList.traverse(constellation.setModule)

      compiler = StdLib.compiler
      compiled <- IO.fromEither(
        compiler.compile(source, "text-pipeline").leftMap(errs =>
          new RuntimeException(errs.map(_.message).mkString("\n"))
        )
      )

      sig <- constellation.run(
        compiled.program,
        Map("text" -> VString("  hello world  "))
      )

      _ <- IO.println(s"upper = ${sig.outputs.get("upper")}")
      _ <- IO.println(s"words = ${sig.outputs.get("words")}")
    } yield ()
}
```

Expected output:

```
upper = Some(VString(HELLO WORLD))
words = Some(VLong(2))
```

## Adding Custom Modules

### Define a Module

Use `ModuleBuilder` with case class inputs and outputs:

```scala
import io.constellation.ModuleBuilder
import io.constellation.lang.semantic.{FunctionSignature, SemanticType}

// Input/output case classes — field names become parameter names in constellation-lang
case class SentimentInput(text: String)
case class SentimentOutput(score: Double, label: String)

val sentimentModule = ModuleBuilder
  .metadata("Sentiment", "Analyzes text sentiment", 1, 0)
  .tags("ml", "nlp")
  .implementationPure[SentimentInput, SentimentOutput] { input =>
    // Replace with your actual ML model call
    val score = if (input.text.contains("good")) 0.9 else 0.1
    val label = if (score > 0.5) "positive" else "negative"
    SentimentOutput(score, label)
  }
  .build
```

For modules that perform IO (HTTP calls, database queries, file reads):

```scala
.implementation[SentimentInput, SentimentOutput] { input =>
  IO {
    // Side-effectful operations here
    callExternalApi(input.text)
  }
}
```

### Register the Module

```scala
// Register the runtime module
constellation.setModule(sentimentModule)

// Register the function signature so the compiler recognizes it
val sentimentSig = FunctionSignature(
  name = "Sentiment",
  params = List("text" -> SemanticType.SString),
  returns = SemanticType.SRecord(Map(
    "score" -> SemanticType.SFloat,
    "label" -> SemanticType.SString
  )),
  moduleName = "Sentiment"
)

val compiler = LangCompiler.builder
  .withFunctions(StdLib.allSignatures :+ sentimentSig)
  .build
```

Now you can use `Sentiment` in constellation-lang scripts:

```
in review: String
analysis = Sentiment(review)
out analysis
```

## Production Configuration

### Builder API

`ConstellationImpl.builder()` provides full control over runtime behavior:

```scala
import io.constellation.impl.ConstellationImpl
import io.constellation.spi.ConstellationBackends
import io.constellation.execution._

val constellation = ConstellationImpl.builder()
  .withScheduler(scheduler)
  .withBackends(backends)
  .withDefaultTimeout(30.seconds)
  .withLifecycle(lifecycle)
  .build()
```

### Bounded Scheduler

The default scheduler is unbounded — every task runs immediately. For production, use a bounded scheduler with priority ordering:

```scala
import io.constellation.execution.GlobalScheduler

// Resource-based (recommended — cleans up on shutdown)
GlobalScheduler.bounded(
  maxConcurrency    = 16,
  maxQueueSize      = 1000,
  starvationTimeout = 30.seconds
).use { scheduler =>
  // Use scheduler here
}
```

Priority levels:

| Priority | Value | Use Case |
|----------|-------|----------|
| Critical | 100 | Health checks, control plane |
| High | 80 | User-facing requests |
| Normal | 50 | Default |
| Low | 20 | Background jobs |
| Background | 0 | Housekeeping |

### SPI Backends

`ConstellationBackends` bundles pluggable integrations. All default to no-op with zero overhead:

```scala
import io.constellation.spi._

val backends = ConstellationBackends(
  metrics  = myPrometheusMetrics,   // MetricsProvider
  tracer   = myOtelTracer,          // TracerProvider
  listener = myKafkaListener,       // ExecutionListener
  cache    = Some(myRedisCache)     // CacheBackend
)
```

See the [SPI Integration Guides](integrations/spi/) for implementation examples with popular libraries.

### Lifecycle Management

`ConstellationLifecycle` enables graceful shutdown with in-flight execution draining:

```scala
import io.constellation.execution.ConstellationLifecycle

for {
  lifecycle <- ConstellationLifecycle.create
  constellation <- ConstellationImpl.builder()
    .withLifecycle(lifecycle)
    .build()

  // ... run your application ...

  // Graceful shutdown: wait up to 30s for in-flight executions
  _ <- lifecycle.shutdown(drainTimeout = 30.seconds)
} yield ()
```

Lifecycle states: `Running` → `Draining` → `Stopped`.

### Circuit Breakers

Protect modules from cascading failures:

```scala
import io.constellation.execution.CircuitBreakerConfig

val constellation = ConstellationImpl.builder()
  .withCircuitBreaker(CircuitBreakerConfig(
    failureThreshold  = 5,          // Open after 5 consecutive failures
    resetDuration     = 30.seconds, // Try again after 30s
    halfOpenMaxProbes = 1           // Allow 1 probe in half-open state
  ))
  .build()
```

### Timeouts

For long-running pipelines, use `IO.timeout`:

```scala
val sig: IO[DataSignature] =
  constellation.run(compiled.program, inputs)
    .timeout(30.seconds)
```

## Optional: HTTP Server

Add the `http-api` dependency and start a server:

```scala
import io.constellation.http._

ConstellationServer
  .builder(constellation, compiler)
  .withHost("0.0.0.0")
  .withPort(8080)
  .withDashboard                  // Enable web dashboard
  .withAuth(AuthConfig(apiKeys = Map(
    "admin-key" -> ApiRole.Admin,
    "app-key"   -> ApiRole.Execute
  )))
  .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  .withRateLimit(RateLimitConfig(requestsPerMinute = 200, burst = 40))
  .withHealthChecks(HealthCheckConfig(enableDetailEndpoint = true))
  .run
```

All hardening features (auth, CORS, rate limiting) are opt-in and disabled by default. See the [Security Model](security.md) for details.

## Next Steps

- [Security Model](security.md) — trust boundaries and HTTP hardening
- [Performance Tuning](performance-tuning.md) — scheduler, circuit breakers, caching
- [Error Reference](error-reference.md) — structured error catalog
- [SPI Integration Guides](integrations/spi/) — plug in metrics, tracing, storage
- [Migration Guide](migration/v0.3.0.md) — upgrading from earlier versions
