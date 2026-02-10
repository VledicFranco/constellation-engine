---
title: "Embedded API Reference"
sidebar_position: 1
description: "Comprehensive guide to programmatic usage of Constellation in Scala applications"
---

# Embedded API Reference

This guide covers programmatic integration of Constellation Engine into Scala applications. Use this when you need maximum control, type safety, and performance for pipeline orchestration within your JVM application.

## Table of Contents

- [Overview](#overview)
- [Core Components](#core-components)
- [Basic Usage Patterns](#basic-usage-patterns)
- [Module Registration](#module-registration)
- [Error Handling](#error-handling)
- [Resource Management](#resource-management)
- [Advanced Patterns](#advanced-patterns)
- [Testing Embedded Applications](#testing-embedded-applications)
- [Performance Optimization](#performance-optimization)
- [Complete Examples](#complete-examples)

## Overview

### When to Use the Embedded API

| Scenario | Embedded API | HTTP API |
|----------|--------------|----------|
| **Tight Scala integration** | ✅ Best choice | ⚠️ Serialization overhead |
| **Type safety at compile time** | ✅ Full type safety | ⚠️ JSON only |
| **Low-latency requirements** | ✅ No network overhead | ❌ Network latency |
| **Polyglot architecture** | ⚠️ JVM only | ✅ Language agnostic |
| **Independent scaling** | ⚠️ Coupled with app | ✅ Scale separately |
| **Maximum performance** | ✅ Direct API calls | ⚠️ HTTP overhead |

### Architecture Overview

```
Your Application
  │
  ├─ Constellation[IO]        ← Main API (register modules, execute pipelines)
  │   └─ ConstellationImpl    ← Default implementation
  │
  ├─ LangCompiler             ← Compile constellation-lang to LoadedPipeline
  │   ├─ Parser               ← Parse .cst source
  │   ├─ TypeChecker          ← Semantic analysis
  │   ├─ IRGenerator          ← Generate intermediate representation
  │   └─ DagCompiler          ← Compile to executable DAG
  │
  ├─ Runtime                  ← Execute compiled DAGs
  │   ├─ Module execution     ← Parallel task execution
  │   ├─ Data flow            ← Dependency-driven scheduling
  │   └─ State management     ← Track execution state
  │
  └─ SPI Backends (optional)  ← Pluggable integrations
      ├─ MetricsProvider      ← Prometheus, etc.
      ├─ TracerProvider       ← OpenTelemetry, etc.
      ├─ CacheBackend         ← Redis, etc.
      └─ ExecutionListener    ← Event streaming
```

### Dependency Setup

Add to your `build.sbt`:

```scala
val constellationVersion = "0.3.1"

libraryDependencies ++= Seq(
  "io.github.vledicfranco" %% "constellation-core"          % constellationVersion,
  "io.github.vledicfranco" %% "constellation-runtime"       % constellationVersion,
  "io.github.vledicfranco" %% "constellation-lang-compiler" % constellationVersion,
  "io.github.vledicfranco" %% "constellation-lang-stdlib"   % constellationVersion
)
```

For HTTP server integration:

```scala
libraryDependencies += "io.github.vledicfranco" %% "constellation-http-api" % constellationVersion
```

## Core Components

### Constellation[IO]

The primary interface for all runtime operations.

```scala
trait Constellation {
  /** Register a module for use in DAG execution */
  def setModule(module: Module.Uninitialized): IO[Unit]

  /** Retrieve a registered module by name */
  def getModuleByName(name: String): IO[Option[Module.Uninitialized]]

  /** List all registered modules */
  def getModules: IO[List[ModuleNodeSpec]]

  /** Access pipeline storage */
  def PipelineStore: PipelineStore

  /** Execute a loaded pipeline */
  def run(
      loaded: LoadedPipeline,
      inputs: Map[String, CValue],
      options: ExecutionOptions = ExecutionOptions()
  ): IO[DataSignature]

  /** Execute a pipeline by reference (name or hash) */
  def run(
      ref: String,
      inputs: Map[String, CValue],
      options: ExecutionOptions
  ): IO[DataSignature]

  /** Resume a suspended execution */
  def resumeFromStore(
      handle: SuspensionHandle,
      additionalInputs: Map[String, CValue] = Map.empty,
      resolvedNodes: Map[String, CValue] = Map.empty,
      options: ExecutionOptions = ExecutionOptions()
  ): IO[DataSignature]
}
```

**Usage:**

```scala
import cats.effect.IO
import io.constellation.impl.ConstellationImpl

for {
  constellation <- ConstellationImpl.init
  _             <- constellation.setModule(myModule)
  sig           <- constellation.run(loadedPipeline, inputs)
} yield sig
```

### LangCompiler

Compiles constellation-lang source code into executable pipelines.

```scala
trait LangCompiler {
  /** Compile source to a LoadedPipeline */
  def compile(
      source: String,
      dagName: String
  ): Either[List[CompileError], CompilationOutput]

  /** Async variant for IO-based caching */
  def compileIO(
      source: String,
      dagName: String
  ): IO[Either[List[CompileError], CompilationOutput]]

  /** Compile to IR only (for visualization) */
  def compileToIR(
      source: String,
      dagName: String
  ): Either[List[CompileError], IRPipeline]

  /** Get function registry for introspection */
  def functionRegistry: FunctionRegistry
}
```

**Builder API:**

```scala
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic._

val compiler = LangCompiler.builder
  .withFunction(FunctionSignature(
    name = "Transform",
    params = List("input" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "transform-module"
  ))
  .withOptimization()  // Enable IR optimization
  .withCaching()       // Enable compilation cache
  .build
```

### LoadedPipeline

A compiled pipeline ready for execution.

```scala
final case class LoadedPipeline(
    image: PipelineImage,
    syntheticModules: Map[UUID, Module.Uninitialized]
)
```

**Fields:**

- `image: PipelineImage` - Immutable pipeline snapshot containing:
  - `dagSpec: DagSpec` - Executable DAG specification
  - `structuralHash: String` - Content-based identifier
  - `moduleOptions: Map[UUID, ModuleCallOptions]` - Per-module configuration
- `syntheticModules` - Auto-generated modules for language features (merge, projection, branches)

### DataSignature

Describes the outcome of pipeline execution.

```scala
final case class DataSignature(
    executionId: UUID,
    structuralHash: String,
    resumptionCount: Int,
    status: PipelineStatus,
    inputs: Map[String, CValue],
    computedNodes: Map[String, CValue],
    outputs: Map[String, CValue],
    missingInputs: List[String],
    pendingOutputs: List[String],
    suspendedState: Option[SuspendedExecution],
    metadata: SignatureMetadata
)
```

**Status Values:**

```scala
sealed trait PipelineStatus
object PipelineStatus {
  case object Completed extends PipelineStatus
  case object Suspended extends PipelineStatus
  final case class Failed(errors: List[ExecutionError]) extends PipelineStatus
}
```

**Usage:**

```scala
sig.status match {
  case PipelineStatus.Completed =>
    println(s"Success: ${sig.outputs}")

  case PipelineStatus.Failed(errors) =>
    errors.foreach(e => println(s"${e.nodeName} failed: ${e.message}"))

  case PipelineStatus.Suspended =>
    println(s"Missing inputs: ${sig.missingInputs}")
    // Can resume later via constellation.resumeFromStore(...)
}
```

## Basic Usage Patterns

### Minimal Example

The simplest possible embedded usage:

```scala
import cats.effect.{IO, IOApp}
import cats.implicits._
import io.constellation._
import io.constellation.TypeSystem._
import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib

object MinimalExample extends IOApp.Simple {

  val source = """
    in text: String
    result = Uppercase(text)
    out result
  """

  def run: IO[Unit] =
    for {
      // 1. Create constellation instance
      constellation <- ConstellationImpl.init

      // 2. Register standard library
      _ <- StdLib.allModules.values.toList.traverse(constellation.setModule)

      // 3. Create compiler with StdLib signatures
      compiler = StdLib.compiler

      // 4. Compile the pipeline
      compiled <- IO.fromEither(
        compiler.compile(source, "uppercase-pipeline")
          .left.map(errs => new RuntimeException(errs.map(_.message).mkString("\n")))
      )

      // 5. Execute
      sig <- constellation.run(
        compiled.pipeline,
        inputs = Map("text" -> CValue.VString("hello world"))
      )

      // 6. Extract results
      _ <- IO.println(s"Result: ${sig.outputs.get("result")}")
    } yield ()
}
```

**Output:**

```
Result: Some(VString(HELLO WORLD))
```

### Compile Once, Execute Many Times

Store compiled pipelines for reuse:

```scala
import io.constellation._
import io.constellation.impl.ConstellationImpl

for {
  constellation <- ConstellationImpl.init
  _ <- StdLib.allModules.values.toList.traverse(constellation.setModule)

  compiler = StdLib.compiler
  compiled <- IO.fromEither(compiler.compile(source, "text-pipeline"))

  // Store in pipeline registry
  hash <- IO.pure(constellation.PipelineStore.store(compiled.pipeline.image))
  _ <- constellation.PipelineStore.alias("text-pipeline", hash)

  // Execute by name multiple times
  sig1 <- constellation.run("text-pipeline", Map("text" -> CValue.VString("first")))
  sig2 <- constellation.run("text-pipeline", Map("text" -> CValue.VString("second")))
  sig3 <- constellation.run("text-pipeline", Map("text" -> CValue.VString("third")))

  _ <- IO.println(s"Results: ${List(sig1, sig2, sig3).map(_.outputs)}")
} yield ()
```

### Working with Execution Options

Control metadata collection and execution behavior:

```scala
import io.constellation.ExecutionOptions

val options = ExecutionOptions(
  collectModuleMetadata = true,   // Include per-module timing
  collectInputs = true,            // Include inputs in signature
  collectComputedNodes = true      // Include all intermediate values
)

for {
  sig <- constellation.run(compiled.pipeline, inputs, options)

  // Access metadata
  _ <- IO.println(s"Execution time: ${sig.metadata.totalLatency}")
  _ <- IO.println(s"Module count: ${sig.metadata.moduleMetadata.size}")

  // Per-module timing
  _ <- sig.metadata.moduleMetadata.toList.traverse { case (moduleName, meta) =>
    IO.println(s"$moduleName: ${meta.latency.toMillis}ms")
  }
} yield ()
```

## Module Registration

### Creating Modules with ModuleBuilder

Use case classes for type-safe module definitions:

```scala
import io.constellation.ModuleBuilder

// 1. Define input/output types
case class SentimentInput(text: String)
case class SentimentOutput(score: Double, label: String)

// 2. Build the module
val sentimentModule = ModuleBuilder
  .metadata(
    name = "Sentiment",
    description = "Analyzes text sentiment",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("ml", "nlp")
  .implementationPure[SentimentInput, SentimentOutput] { input =>
    // Your sentiment analysis logic here
    val score = analyzeSentiment(input.text)
    val label = if (score > 0.5) "positive" else "negative"
    SentimentOutput(score, label)
  }
  .build

// 3. Register with constellation
constellation.setModule(sentimentModule)
```

**Field naming rules:**

- Case class field names become variable names in constellation-lang
- Names must match exactly (case-sensitive)

```scala
case class MyInput(userName: String, userId: Long)

// In constellation-lang:
result = MyModule(userName, userId)  // Field names must match
```

### Pure vs IO Implementations

**Pure (side-effect-free):**

```scala
.implementationPure[Input, Output] { input =>
  // Synchronous, deterministic transformation
  Output(input.text.toUpperCase)
}
```

**IO (effectful operations):**

```scala
.implementation[Input, Output] { input =>
  IO {
    // HTTP calls, database queries, file I/O
    val response = httpClient.get(input.url)
    Output(response.body)
  }
}
```

**Use IO for:**

- HTTP requests
- Database queries
- File system operations
- External API calls
- Non-deterministic operations

### Adding Context Metadata

Return execution context alongside results:

```scala
import cats.Eval
import io.circe.Json

.implementationWithContext[Input, Output] { input =>
  IO {
    val startTime = System.currentTimeMillis()
    val result = processData(input)
    val endTime = System.currentTimeMillis()

    Module.Produces(
      data = Output(result),
      implementationContext = Eval.later(Map(
        "processingTime" -> Json.fromLong(endTime - startTime),
        "algorithm" -> Json.fromString("v2"),
        "confidence" -> Json.fromDoubleOrNull(0.95)
      ))
    )
  }
}
```

**Context is available in execution metadata:**

```scala
val options = ExecutionOptions(collectModuleMetadata = true)
val sig = constellation.run(pipeline, inputs, options)

sig.metadata.moduleMetadata.get("MyModule").foreach { meta =>
  meta.context.foreach { ctx =>
    println(s"Processing time: ${ctx("processingTime")}")
  }
}
```

### Registering Functions for Compilation

Register function signatures so the compiler recognizes your modules:

```scala
import io.constellation.lang.semantic._

val sentimentSig = FunctionSignature(
  name = "Sentiment",           // Name in constellation-lang
  params = List(
    "text" -> SemanticType.SString
  ),
  returns = SemanticType.SRecord(Map(
    "score" -> SemanticType.SFloat,
    "label" -> SemanticType.SString
  )),
  moduleName = "Sentiment"      // Runtime module name
)

val compiler = LangCompiler.builder
  .withFunctions(StdLib.allSignatures :+ sentimentSig)
  .build
```

**Now use in constellation-lang:**

```constellation
in review: String
analysis = Sentiment(review)
out analysis
```

### Module Configuration

Set timeouts and other options:

```scala
import scala.concurrent.duration._

val module = ModuleBuilder
  .metadata("LongRunning", "Takes time", 1, 0)
  .inputsTimeout(30.seconds)   // Wait for inputs
  .moduleTimeout(60.seconds)   // Execution timeout
  .implementation[Input, Output](processFunction)
  .build
```

**Timeout behavior:**

- `inputsTimeout` - How long to wait for all inputs to arrive
- `moduleTimeout` - Maximum execution time for the module logic
- If timeout expires: Module status set to `Module.Status.Timed`

## Error Handling

### Compile-Time Errors

Handle compilation failures gracefully:

```scala
import io.constellation.lang.ast.CompileError

compiler.compile(source, "my-pipeline") match {
  case Right(compiled) =>
    // Success - use compiled.pipeline
    compiled.pipeline

  case Left(errors) =>
    // Compilation failed - analyze errors
    errors.foreach {
      case e: CompileError.ParseError =>
        println(s"Syntax error at ${e.position}: ${e.message}")

      case e: CompileError.UndefinedFunction =>
        println(s"Unknown function '${e.name}' at ${e.position}")
        println(s"Did you mean: ${e.suggestions.mkString(", ")}")

      case e: CompileError.TypeMismatch =>
        println(s"Type error at ${e.position}:")
        println(s"  Expected: ${e.expected}")
        println(s"  Got: ${e.actual}")

      case e: CompileError.UndefinedVariable =>
        println(s"Unknown variable '${e.name}' at ${e.position}")

      case e: CompileError.InvalidProjection =>
        println(s"Field '${e.field}' not found")
        println(s"Available fields: ${e.availableFields.mkString(", ")}")

      case e =>
        println(e.format)
    }
}
```

### Runtime Errors

Handle execution failures:

```scala
constellation.run(pipeline, inputs).attempt.flatMap {
  case Right(sig) =>
    sig.status match {
      case PipelineStatus.Completed =>
        IO.println(s"Success: ${sig.outputs}")

      case PipelineStatus.Failed(errors) =>
        errors.traverse { error =>
          IO.println(s"Module ${error.moduleName} failed:")
          IO.println(s"  Node: ${error.nodeName}")
          IO.println(s"  Message: ${error.message}")
          error.cause.foreach { ex =>
            IO.println(s"  Cause: ${ex.getClass.getName}")
            ex.printStackTrace()
          }
        }.void

      case PipelineStatus.Suspended =>
        IO.println(s"Suspended - missing inputs: ${sig.missingInputs}")
        // Could resume later
        sig.suspendedState.foreach { suspended =>
          IO.println(s"Execution ID: ${suspended.executionId}")
          IO.println(s"Computed so far: ${suspended.computedValues.size} nodes")
        }
    }

  case Left(exception) =>
    // Global execution failure (before/after runtime)
    IO.println(s"Execution failed: ${exception.getMessage}")
    exception.printStackTrace()
    IO.raiseError(exception)
}
```

### Validation Errors

Input validation failures:

```scala
val inputs = Map("text" -> CValue.VString("hello"))

constellation.run(pipeline, inputs).attempt.flatMap {
  case Left(ex: RuntimeException) if ex.getMessage.contains("unexpected") =>
    IO.println("Input validation failed - check input names")

  case Left(ex: RuntimeException) if ex.getMessage.contains("different type") =>
    IO.println("Input type mismatch - check input types")

  case Left(ex) =>
    IO.println(s"Other error: ${ex.getMessage}")

  case Right(sig) =>
    IO.println("Success")
}
```

### Error Recovery Strategies

Retry failed executions:

```scala
import cats.effect.syntax.all._

def runWithRetry(
    constellation: Constellation,
    pipeline: LoadedPipeline,
    inputs: Map[String, CValue],
    maxRetries: Int = 3
): IO[DataSignature] = {

  def attempt(retriesLeft: Int): IO[DataSignature] =
    constellation.run(pipeline, inputs).flatMap { sig =>
      sig.status match {
        case PipelineStatus.Completed =>
          IO.pure(sig)

        case PipelineStatus.Failed(errors) if retriesLeft > 0 =>
          IO.println(s"Execution failed, retrying... ($retriesLeft left)") *>
            IO.sleep(1.second) *>
            attempt(retriesLeft - 1)

        case _ =>
          IO.pure(sig)
      }
    }

  attempt(maxRetries)
}
```

## Resource Management

### Lifecycle Management

Use `ConstellationLifecycle` for graceful shutdown:

```scala
import io.constellation.execution.ConstellationLifecycle
import scala.concurrent.duration._

for {
  lifecycle <- ConstellationLifecycle.create

  constellation <- ConstellationImpl.builder()
    .withLifecycle(lifecycle)
    .build()

  // Register modules
  _ <- modules.traverse(constellation.setModule)

  // Run your application
  _ <- runApplication(constellation)

  // Graceful shutdown - wait up to 30s for in-flight executions
  _ <- lifecycle.shutdown(drainTimeout = 30.seconds)
} yield ()
```

**Lifecycle states:**

1. **Running** - Accepting new executions
2. **Draining** - Rejecting new, completing in-flight
3. **Stopped** - All executions complete

**Check lifecycle state:**

```scala
lifecycle.isRunning.flatMap {
  case true => constellation.run(pipeline, inputs)
  case false => IO.raiseError(new Exception("System is shutting down"))
}
```

### Resource-Based Initialization

Use Cats Effect `Resource` for automatic cleanup:

```scala
import cats.effect.{IO, Resource}
import io.constellation.execution.GlobalScheduler

val app: Resource[IO, Unit] = for {
  // Scheduler with automatic shutdown
  scheduler <- GlobalScheduler.bounded(
    maxConcurrency = 16,
    maxQueueSize = 1000,
    starvationTimeout = 30.seconds
  )

  // Lifecycle manager
  lifecycle <- Resource.eval(ConstellationLifecycle.create)

  // Constellation instance
  constellation <- Resource.eval(
    ConstellationImpl.builder()
      .withScheduler(scheduler)
      .withLifecycle(lifecycle)
      .build()
  )

  // Cleanup on Resource release
  _ <- Resource.onFinalize(lifecycle.shutdown(30.seconds))

} yield {
  // Use constellation here
  // Resources automatically cleaned up on exit
}

// Run application
app.use { _ =>
  // Application logic
  IO.unit
}
```

### Managing Module State

For stateful modules, use Ref for thread-safe state:

```scala
import cats.effect.Ref

case class CounterInput(increment: Long)
case class CounterOutput(current: Long)

def createCounterModule: IO[Module.Uninitialized] =
  Ref.of[IO, Long](0L).map { counterRef =>
    ModuleBuilder
      .metadata("Counter", "Stateful counter", 1, 0)
      .implementation[CounterInput, CounterOutput] { input =>
        counterRef.updateAndGet(_ + input.increment).map(CounterOutput(_))
      }
      .build
  }

// Usage
for {
  counterModule <- createCounterModule
  constellation <- ConstellationImpl.init
  _ <- constellation.setModule(counterModule)

  // Each execution modifies shared state
  sig1 <- constellation.run(pipeline, Map("increment" -> CValue.VLong(5)))
  sig2 <- constellation.run(pipeline, Map("increment" -> CValue.VLong(3)))
  sig3 <- constellation.run(pipeline, Map("increment" -> CValue.VLong(2)))

  // Results: 5, 8, 10
} yield ()
```

## Advanced Patterns

### Custom Schedulers

Control concurrency and priority:

```scala
import io.constellation.execution.GlobalScheduler

// Bounded scheduler with priority
GlobalScheduler.bounded(
  maxConcurrency = 16,      // Max parallel tasks
  maxQueueSize = 1000,      // Max queued tasks
  starvationTimeout = 30.seconds  // Boost low-priority tasks after delay
).use { scheduler =>
  ConstellationImpl.builder()
    .withScheduler(scheduler)
    .build()
    .flatMap { constellation =>
      // Your application logic
    }
}
```

**Priority levels:**

```scala
object Priority {
  val Critical   = 100  // Time-sensitive operations
  val High       = 80   // Important user-facing
  val Normal     = 50   // Default
  val Low        = 20   // Background processing
  val Background = 0    // Non-urgent work
}
```

### Circuit Breakers

Protect modules from cascading failures:

```scala
import io.constellation.execution.CircuitBreakerConfig

val constellation = ConstellationImpl.builder()
  .withCircuitBreaker(CircuitBreakerConfig(
    failureThreshold = 5,          // Open after 5 consecutive failures
    resetDuration = 30.seconds,    // Try again after 30s
    halfOpenMaxProbes = 1          // Allow 1 probe in half-open state
  ))
  .build()
```

**Circuit breaker states:**

1. **Closed** - Normal operation
2. **Open** - Fail fast after threshold
3. **Half-Open** - Test if service recovered

### SPI Backend Integration

Plug in observability and caching:

```scala
import io.constellation.spi._

// Define custom backends
val myMetrics = new MetricsProvider {
  def counter(name: String, tags: Map[String, String]): IO[Unit] =
    IO.println(s"Counter: $name $tags")

  def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit] =
    IO.println(s"Histogram: $name=$value $tags")

  def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit] =
    IO.println(s"Gauge: $name=$value $tags")
}

val myTracer = new TracerProvider {
  def span[A](name: String, attributes: Map[String, String])(fa: IO[A]): IO[A] = {
    IO.println(s"Span start: $name") *>
      fa <*
      IO.println(s"Span end: $name")
  }
}

val backends = ConstellationBackends(
  metrics = myMetrics,
  tracer = myTracer,
  listener = ExecutionListener.noop,
  cache = None
)

val constellation = ConstellationImpl.builder()
  .withBackends(backends)
  .build()
```

### Pipeline Caching

Cache compiled pipelines:

```scala
import io.constellation.lang.CompilationCache

// Enable caching on compiler
val compiler = LangCompiler.builder
  .withFunctions(StdLib.allSignatures)
  .withCaching(CompilationCache.Config(
    maxSize = 100,
    ttl = 1.hour
  ))
  .build

// Compilations are cached by source hash
val compiled1 = compiler.compileIO(source, "pipeline1")  // Cache miss
val compiled2 = compiler.compileIO(source, "pipeline1")  // Cache hit (fast)
```

**Cache metrics:**

```scala
compiler match {
  case caching: CachingLangCompiler =>
    for {
      stats <- caching.cacheStats
      _ <- IO.println(s"Hit rate: ${stats.hitRate}")
      _ <- IO.println(s"Entries: ${stats.size}/${stats.maxSize}")
    } yield ()
  case _ =>
    IO.println("Caching not enabled")
}
```

### Execution Cancellation

Cancel long-running executions:

```scala
import io.constellation.execution.CancellableExecution
import scala.concurrent.duration._

for {
  constellation <- ConstellationImpl.init

  // Start execution (returns immediately)
  exec <- constellation.runCancellable(pipeline, inputs)

  // Do other work
  _ <- IO.sleep(5.seconds)

  // Check status
  status <- exec.status
  _ <- status match {
    case ExecutionStatus.Running =>
      // Cancel if still running
      exec.cancel *> IO.println("Execution cancelled")
    case ExecutionStatus.Completed =>
      IO.println("Execution completed")
    case ExecutionStatus.Failed(error) =>
      IO.println(s"Execution failed: ${error.getMessage}")
    case ExecutionStatus.Cancelled =>
      IO.println("Already cancelled")
  }

  // Get result (may be partial if cancelled)
  sig <- exec.result
} yield sig
```

### Execution Timeouts

Set global timeout for executions:

```scala
val constellation = ConstellationImpl.builder()
  .withDefaultTimeout(30.seconds)
  .build()

// All executions timeout after 30 seconds
constellation.run(pipeline, inputs)
```

**Per-execution timeout:**

```scala
constellation.run(pipeline, inputs)
  .timeout(60.seconds)
  .handleErrorWith {
    case _: TimeoutException =>
      IO.println("Execution timed out") *> IO.raiseError(...)
    case ex =>
      IO.raiseError(ex)
  }
```

### Suspension and Resumption

Handle incomplete executions:

```scala
import io.constellation.{SuspensionStore, InMemorySuspensionStore}

for {
  // Create suspension store
  store <- InMemorySuspensionStore.create

  constellation <- ConstellationImpl.builder()
    .withSuspensionStore(store)
    .build()

  // Execute with partial inputs
  sig1 <- constellation.run(pipeline, Map("input1" -> value1))

  // If suspended, save state
  handle <- sig1.status match {
    case PipelineStatus.Suspended =>
      sig1.suspendedState match {
        case Some(suspended) =>
          store.save(suspended)
        case None =>
          IO.raiseError(new Exception("No suspended state"))
      }
    case _ =>
      IO.raiseError(new Exception("Not suspended"))
  }

  // Later: resume with additional inputs
  sig2 <- constellation.resumeFromStore(
    handle,
    additionalInputs = Map("input2" -> value2)
  )

  _ <- IO.println(s"Resumed execution completed: ${sig2.status}")
} yield ()
```

## Testing Embedded Applications

### Unit Testing Modules

Test modules in isolation:

```scala
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.implicits.global

class SentimentModuleTest extends AnyFlatSpec with Matchers {

  "Sentiment module" should "classify positive text" in {
    val module = sentimentModule
    val input = SentimentInput("This is great!")

    // Extract the implementation function (for testing)
    // In production code, use constellation.run()
    val result = testModule(module, input).unsafeRunSync()

    result.label shouldBe "positive"
    result.score should be > 0.5
  }

  it should "classify negative text" in {
    val input = SentimentInput("This is terrible!")
    val result = testModule(sentimentModule, input).unsafeRunSync()

    result.label shouldBe "negative"
    result.score should be < 0.5
  }
}
```

### Integration Testing

Test complete pipelines:

```scala
import cats.effect.IO
import cats.effect.unsafe.implicits.global

class PipelineIntegrationTest extends AnyFlatSpec with Matchers {

  val source = """
    in text: String
    sentiment = Sentiment(text)
    out sentiment
  """

  "Sentiment pipeline" should "process text end-to-end" in {
    val test = for {
      constellation <- ConstellationImpl.init
      _ <- constellation.setModule(sentimentModule)

      compiler = LangCompiler.builder
        .withFunction(sentimentSignature)
        .build

      compiled <- IO.fromEither(
        compiler.compile(source, "test-pipeline")
          .left.map(errs => new Exception(errs.head.format))
      )

      sig <- constellation.run(
        compiled.pipeline,
        Map("text" -> CValue.VString("I love this product!"))
      )

    } yield sig

    val sig = test.unsafeRunSync()

    sig.status shouldBe PipelineStatus.Completed
    sig.outputs should contain key "sentiment"
  }
}
```

### Testing with TestContainers

Test with external dependencies:

```scala
import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

class ExternalServiceTest extends AnyFlatSpec with Matchers {

  "Pipeline with HTTP module" should "call external service" in {
    val container = GenericContainer(
      dockerImage = "my-service:latest",
      exposedPorts = Seq(8080),
      waitStrategy = Wait.forHttp("/health")
    )

    container.start()

    val serviceUrl = s"http://${container.host}:${container.mappedPort(8080)}"

    // Create module that calls container
    val httpModule = createHttpModule(serviceUrl)

    val test = for {
      constellation <- ConstellationImpl.init
      _ <- constellation.setModule(httpModule)
      sig <- constellation.run(pipeline, inputs)
    } yield sig

    val sig = test.unsafeRunSync()
    sig.status shouldBe PipelineStatus.Completed

    container.stop()
  }
}
```

### Mocking Modules

Replace modules with test doubles:

```scala
case class MockInput(data: String)
case class MockOutput(result: String)

val mockModule = ModuleBuilder
  .metadata("ExternalService", "Mock for testing", 1, 0)
  .implementationPure[MockInput, MockOutput] { input =>
    // Return deterministic test data
    MockOutput(s"MOCK: ${input.data}")
  }
  .build

// Use in tests
val constellation = ConstellationImpl.init.unsafeRunSync()
constellation.setModule(mockModule).unsafeRunSync()
```

## Performance Optimization

### Object Pooling

Reduce allocation overhead:

```scala
import io.constellation.pool.RuntimePool

for {
  // Create pool (reusable Deferreds and state containers)
  pool <- RuntimePool.create(
    initialSize = 50,
    maxSize = 200
  )

  constellation <- ConstellationImpl.init

  // Use pooled execution
  sig <- constellation.runPooled(
    pipeline.image.dagSpec,
    inputs,
    modules,
    pool
  )
} yield sig
```

**Performance characteristics:**

- 90% reduction in per-request allocations
- More stable p99 latency (fewer GC pauses)
- 15-30% throughput improvement for small DAGs

### Compilation Caching

Cache compiled pipelines to avoid repeated parsing/type-checking:

```scala
val compiler = LangCompiler.builder
  .withFunctions(signatures)
  .withCaching(CompilationCache.Config(
    maxSize = 100,        // Cache 100 pipelines
    ttl = 1.hour          // Evict after 1 hour
  ))
  .build

// First compilation: ~50ms (parse + typecheck + compile)
val compiled1 = compiler.compileIO(source, "pipeline")

// Second compilation: ~2ms (cache hit)
val compiled2 = compiler.compileIO(source, "pipeline")
```

### Pipeline Store Optimization

Store pipelines for fast lookup:

```scala
// Store once
val hash = constellation.PipelineStore.store(compiled.pipeline.image)
constellation.PipelineStore.alias("my-pipeline", hash)

// Execute by name (no compilation)
for {
  sig1 <- constellation.run("my-pipeline", inputs1)
  sig2 <- constellation.run("my-pipeline", inputs2)
  sig3 <- constellation.run("my-pipeline", inputs3)
} yield List(sig1, sig2, sig3)
```

### Parallel Module Registration

Register modules in parallel:

```scala
import cats.implicits._

val modules: List[Module.Uninitialized] = List(
  module1, module2, module3, module4, module5
)

// Sequential (slow)
modules.traverse(constellation.setModule)

// Parallel (fast)
modules.parTraverse(constellation.setModule)
```

### Batch Execution

Execute multiple pipelines in parallel:

```scala
import cats.syntax.parallel._

val executions = List(
  (pipeline1, inputs1),
  (pipeline2, inputs2),
  (pipeline3, inputs3)
)

executions.parTraverse { case (pipeline, inputs) =>
  constellation.run(pipeline, inputs)
}
```

### Memory-Efficient Inputs

Use `RawValue` for large datasets:

```scala
import io.constellation.RawValue

// Instead of CValue (heap overhead)
val largeList = CValue.CList(
  (1 to 1000000).map(i => CValue.VLong(i)).toVector,
  CType.CInt
)

// Use RawValue (primitive arrays)
val efficientList = RawValue.RIntList(
  (1 to 1000000).toArray
)

// Execute with RawValue inputs
Runtime.runWithRawInputs(
  dag = dagSpec,
  initData = Map("data" -> efficientList),
  inputTypes = Map("data" -> CType.CList(CType.CInt)),
  modules = modules
)
```

## Complete Examples

### Example 1: Text Processing Pipeline

```scala
import cats.effect.{IO, IOApp}
import cats.implicits._
import io.constellation._
import io.constellation.TypeSystem._
import io.constellation.impl.ConstellationImpl
import io.constellation.stdlib.StdLib

object TextProcessingApp extends IOApp.Simple {

  val pipeline = """
    in rawText: String

    # Clean and normalize
    trimmed = Trim(rawText)
    lower = Lowercase(trimmed)

    # Extract features
    words = WordCount(lower)
    chars = Length(lower)

    # Compute metrics
    avgWordLength = Divide(chars, words)

    out lower
    out words
    out avgWordLength
  """

  def run: IO[Unit] =
    for {
      constellation <- ConstellationImpl.init
      _ <- StdLib.allModules.values.toList.traverse(constellation.setModule)

      compiler = StdLib.compiler
      compiled <- IO.fromEither(
        compiler.compile(pipeline, "text-processing")
          .left.map(errs => new RuntimeException(errs.map(_.message).mkString("\n")))
      )

      // Process sample text
      sig <- constellation.run(
        compiled.pipeline,
        Map("rawText" -> CValue.VString("  The Quick Brown Fox Jumps  "))
      )

      _ <- IO.println("=== Text Processing Results ===")
      _ <- IO.println(s"Normalized: ${sig.output("lower")}")
      _ <- IO.println(s"Word count: ${sig.output("words")}")
      _ <- IO.println(s"Avg word length: ${sig.output("avgWordLength")}")

    } yield ()
}
```

### Example 2: ML Inference Pipeline

```scala
import cats.effect.{IO, IOApp, Resource}
import cats.implicits._
import io.constellation._
import io.constellation.TypeSystem._
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic._

object MLInferenceApp extends IOApp.Simple {

  // Custom modules
  case class EmbedInput(text: String)
  case class EmbedOutput(embedding: Vector[Double])

  case class ClassifyInput(embedding: Vector[Double])
  case class ClassifyOutput(label: String, confidence: Double)

  val embedModule = ModuleBuilder
    .metadata("Embed", "Text to embedding", 1, 0)
    .implementation[EmbedInput, EmbedOutput] { input =>
      IO {
        // Call your embedding model
        val embedding = callEmbeddingModel(input.text)
        EmbedOutput(embedding)
      }
    }
    .build

  val classifyModule = ModuleBuilder
    .metadata("Classify", "Classify embedding", 1, 0)
    .implementation[ClassifyInput, ClassifyOutput] { input =>
      IO {
        // Call your classifier
        val (label, confidence) = callClassifier(input.embedding)
        ClassifyOutput(label, confidence)
      }
    }
    .build

  val pipeline = """
    in text: String

    embedding = Embed(text)
    prediction = Classify(embedding)

    out prediction
  """

  val signatures = List(
    FunctionSignature(
      name = "Embed",
      params = List("text" -> SemanticType.SString),
      returns = SemanticType.SRecord(Map(
        "embedding" -> SemanticType.SList(SemanticType.SFloat)
      )),
      moduleName = "Embed"
    ),
    FunctionSignature(
      name = "Classify",
      params = List("embedding" -> SemanticType.SList(SemanticType.SFloat)),
      returns = SemanticType.SRecord(Map(
        "label" -> SemanticType.SString,
        "confidence" -> SemanticType.SFloat
      )),
      moduleName = "Classify"
    )
  )

  def run: IO[Unit] =
    for {
      constellation <- ConstellationImpl.init
      _ <- constellation.setModule(embedModule)
      _ <- constellation.setModule(classifyModule)

      compiler = LangCompiler.builder
        .withFunctions(signatures)
        .build

      compiled <- IO.fromEither(
        compiler.compile(pipeline, "ml-inference")
          .left.map(errs => new RuntimeException(errs.map(_.message).mkString("\n")))
      )

      // Run inference
      sig <- constellation.run(
        compiled.pipeline,
        Map("text" -> CValue.VString("This product is amazing!"))
      )

      _ <- sig.output("prediction") match {
        case Some(CValue.CProduct(fields, _)) =>
          IO.println(s"Label: ${fields("label")}") *>
            IO.println(s"Confidence: ${fields("confidence")}")
        case _ =>
          IO.println("Unexpected output format")
      }

    } yield ()

  // Stub implementations
  def callEmbeddingModel(text: String): Vector[Double] =
    Vector.fill(768)(scala.util.Random.nextDouble())

  def callClassifier(embedding: Vector[Double]): (String, Double) =
    ("positive", 0.92)
}
```

### Example 3: Production Application with All Features

```scala
import cats.effect.{IO, IOApp, Resource}
import cats.implicits._
import io.constellation._
import io.constellation.impl.ConstellationImpl
import io.constellation.execution._
import io.constellation.spi._
import scala.concurrent.duration._

object ProductionApp extends IOApp.Simple {

  // Application resources
  val resources: Resource[IO, (Constellation, LangCompiler)] = for {
    // Bounded scheduler
    scheduler <- GlobalScheduler.bounded(
      maxConcurrency = 16,
      maxQueueSize = 1000,
      starvationTimeout = 30.seconds
    )

    // Lifecycle manager
    lifecycle <- Resource.eval(ConstellationLifecycle.create)

    // SPI backends
    backends <- Resource.eval(IO.pure(ConstellationBackends(
      metrics = PrometheusMetrics.create,
      tracer = OpenTelemetryTracer.create,
      listener = ExecutionListener.noop,
      cache = None
    )))

    // Constellation instance
    constellation <- Resource.eval(
      ConstellationImpl.builder()
        .withScheduler(scheduler)
        .withBackends(backends)
        .withDefaultTimeout(60.seconds)
        .withLifecycle(lifecycle)
        .withCircuitBreaker(CircuitBreakerConfig(
          failureThreshold = 5,
          resetDuration = 30.seconds,
          halfOpenMaxProbes = 1
        ))
        .build()
    )

    // Register modules
    _ <- Resource.eval(
      StdLib.allModules.values.toList.parTraverse(constellation.setModule)
    )

    // Compiler with caching
    compiler = LangCompiler.builder
      .withFunctions(StdLib.allSignatures)
      .withCaching(CompilationCache.Config(maxSize = 100, ttl = 1.hour))
      .withOptimization()
      .build

    // Cleanup on shutdown
    _ <- Resource.onFinalize(
      lifecycle.shutdown(30.seconds) *>
        IO.println("Constellation shutdown complete")
    )

  } yield (constellation, compiler)

  def run: IO[Unit] = resources.use { case (constellation, compiler) =>
    for {
      _ <- IO.println("=== Constellation Production App ===")

      // Compile pipelines
      pipeline1 <- compileOrFail(compiler, source1, "pipeline1")
      pipeline2 <- compileOrFail(compiler, source2, "pipeline2")

      // Store for reuse
      _ <- storePipeline(constellation, pipeline1, "text-processing")
      _ <- storePipeline(constellation, pipeline2, "data-analysis")

      // Execute workloads
      _ <- processWorkload(constellation, "text-processing")
      _ <- processWorkload(constellation, "data-analysis")

      _ <- IO.println("=== Processing Complete ===")

    } yield ()
  }

  def compileOrFail(
      compiler: LangCompiler,
      source: String,
      name: String
  ): IO[LoadedPipeline] =
    IO.fromEither(
      compiler.compile(source, name)
        .left.map(errs => new RuntimeException(errs.map(_.format).mkString("\n")))
    ).map(_.pipeline)

  def storePipeline(
      constellation: Constellation,
      pipeline: LoadedPipeline,
      name: String
  ): IO[Unit] =
    for {
      hash <- IO.pure(constellation.PipelineStore.store(pipeline.image))
      _ <- constellation.PipelineStore.alias(name, hash)
      _ <- IO.println(s"Stored pipeline '$name' with hash $hash")
    } yield ()

  def processWorkload(
      constellation: Constellation,
      pipelineName: String
  ): IO[Unit] = {
    val inputs = List(
      Map("input" -> CValue.VString("sample1")),
      Map("input" -> CValue.VString("sample2")),
      Map("input" -> CValue.VString("sample3"))
    )

    inputs.parTraverse { input =>
      constellation.run(pipelineName, input, ExecutionOptions())
        .timeout(30.seconds)
        .handleErrorWith { ex =>
          IO.println(s"Execution failed: ${ex.getMessage}") *>
            IO.pure(DataSignature.failed("error", ex))
        }
    }.void
  }

  val source1 = """
    in input: String
    result = Uppercase(input)
    out result
  """

  val source2 = """
    in input: String
    length = Length(input)
    out length
  """
}
```

### Example 4: Stream Processing

```scala
import cats.effect.{IO, IOApp}
import cats.effect.std.Queue
import fs2.Stream

object StreamProcessingApp extends IOApp.Simple {

  def run: IO[Unit] =
    for {
      constellation <- ConstellationImpl.init
      _ <- StdLib.allModules.values.toList.traverse(constellation.setModule)

      compiler = StdLib.compiler
      compiled <- IO.fromEither(
        compiler.compile(source, "stream-processor")
          .left.map(errs => new RuntimeException(errs.head.format))
      )

      // Process stream of inputs
      results <- Stream
        .emits(generateInputs(1000))
        .parEvalMapUnordered(16) { input =>
          constellation.run(compiled.pipeline, input)
        }
        .filter(_.status == PipelineStatus.Completed)
        .compile
        .toList

      _ <- IO.println(s"Processed ${results.size} items successfully")

    } yield ()

  def generateInputs(count: Int): List[Map[String, CValue]] =
    (1 to count).map { i =>
      Map("text" -> CValue.VString(s"item-$i"))
    }.toList

  val source = """
    in text: String
    upper = Uppercase(text)
    out upper
  """
}
```

## Best Practices

### 1. Use Resource for Lifecycle

Always use `Resource` or explicit lifecycle management:

```scala
// Good
GlobalScheduler.bounded(...).use { scheduler =>
  // Use scheduler
}

// Bad
val scheduler = GlobalScheduler.bounded(...).unsafeRunSync()
// No cleanup!
```

### 2. Register Modules Once

Register modules at application startup, not per-execution:

```scala
// Good
for {
  constellation <- ConstellationImpl.init
  _ <- modules.traverse(constellation.setModule)  // Once
  _ <- (1 to 1000).toList.traverse { _ =>
    constellation.run(pipeline, inputs)           // Many times
  }
} yield ()

// Bad
for {
  _ <- (1 to 1000).toList.traverse { _ =>
    constellation <- ConstellationImpl.init
    _ <- modules.traverse(constellation.setModule)  // 1000 times!
    constellation.run(pipeline, inputs)
  }
} yield ()
```

### 3. Cache Compiled Pipelines

Store compiled pipelines for reuse:

```scala
// Good
val compiled = compiler.compile(source, "pipeline")
(1 to 1000).traverse { _ =>
  constellation.run(compiled.pipeline, inputs)
}

// Bad
(1 to 1000).traverse { _ =>
  val compiled = compiler.compile(source, "pipeline")  // Slow!
  constellation.run(compiled.pipeline, inputs)
}
```

### 4. Handle Errors Explicitly

Don't use `unsafeRunSync` in production:

```scala
// Good
constellation.run(pipeline, inputs).attempt.flatMap {
  case Right(sig) => handleSuccess(sig)
  case Left(ex) => handleError(ex)
}

// Bad
val sig = constellation.run(pipeline, inputs).unsafeRunSync()
// Blocks thread, no error handling!
```

### 5. Use Bounded Scheduler in Production

Prevent resource exhaustion:

```scala
// Good (production)
GlobalScheduler.bounded(maxConcurrency = 16, ...).use { scheduler =>
  ConstellationImpl.builder()
    .withScheduler(scheduler)
    .build()
}

// Okay (development)
ConstellationImpl.init  // Uses unbounded scheduler
```

## See Also

- [Embedding Guide](../../getting-started/embedding-guide.md) - Quick start tutorial
- [Programmatic API](../../api-reference/programmatic-api.md) - API overview
- [HTTP API Reference](../reference/http-api.md) - REST API for remote access
- [Security Model](../../architecture/security-model.md) - Trust boundaries
- [SPI Integration](../../spi/overview.md) - Custom backend providers
