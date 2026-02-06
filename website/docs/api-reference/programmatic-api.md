---
title: "Programmatic API"
sidebar_position: 2
description: "Scala API for creating modules and building pipelines"
---

# API Guide

This guide covers programmatic usage of Constellation Engine, including compiling constellation-lang pipelines and creating custom modules.

## When to Use the Programmatic API

Choose the programmatic API when you need tight integration with your Scala application and maximum performance. For distributed or polyglot architectures, consider the [HTTP API](./http-api-overview) instead.

| Scenario | Recommendation |
|----------|----------------|
| **Tight Scala integration** | Programmatic - direct type safety, no serialization |
| **Microservice architecture** | HTTP API - language-agnostic, easier scaling |
| **High-throughput, low-latency** | Programmatic - no network overhead |
| **Multiple client languages** | HTTP API - universal REST interface |
| **Dynamic pipeline loading** | Either - both support hot reload |

### Quick Decision Guide

- **Building a Scala application?** → Use programmatic API for best performance
- **Building a polyglot system?** → Use HTTP API for flexibility
- **Need to scale independently?** → Use HTTP API with multiple instances
- **Embedding in existing Scala service?** → Use programmatic API

## Table of Contents

- [Compiling Pipelines](#compiling-pipelines)
- [Registering Functions](#registering-functions)
- [Creating Modules](#creating-modules)
- [Running Pipelines](#running-pipelines)
- [Working with Types](#working-with-types)
- [Error Handling](#error-handling)

## Compiling Pipelines

### Basic Compilation

```scala
import io.constellation.lang.LangCompiler

val compiler = LangCompiler.empty

val source = """
  in x: Int
  in y: Int
  out x
"""

val result = compiler.compile(source, "my-dag")

result match {
  case Right(compiled) =>
    val dagSpec = compiled.pipeline.image.dagSpec
    println(s"DAG name: ${dagSpec.name}")
    println(s"Modules: ${dagSpec.modules.size}")
    println(s"Data nodes: ${dagSpec.data.size}")

  case Left(errors) =>
    errors.foreach(e => println(e.format))
}
```

### Using the Builder

```scala
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic._

val compiler = LangCompiler.builder
  .withFunction(FunctionSignature(
    name = "transform",
    params = List("input" -> SemanticType.SString),
    returns = SemanticType.SString,
    moduleName = "transform-module"
  ))
  .withFunction(FunctionSignature(
    name = "score",
    params = List(
      "data" -> SemanticType.SCandidates(SemanticType.SRecord(Map(
        "id" -> SemanticType.SString
      ))),
      "userId" -> SemanticType.SInt
    ),
    returns = SemanticType.SCandidates(SemanticType.SRecord(Map(
      "score" -> SemanticType.SFloat
    ))),
    moduleName = "score-module"
  ))
  .build
```

### Compilation Result

```scala
final case class CompilationOutput(
  pipeline: LoadedPipeline,        // The compiled pipeline
  warnings: List[CompileWarning]   // Non-fatal warnings
)

final case class LoadedPipeline(
  image: PipelineImage,                            // Contains dagSpec + metadata
  syntheticModules: Map[UUID, Module.Uninitialized]  // Generated modules
)
```

The `syntheticModules` map contains automatically generated modules for:
- Merge operations (`+`)
- Projection operations (`[fields]`)
- Conditional expressions (`if/else`)

## Registering Functions

Functions in constellation-lang map to modules at runtime. Register them with `FunctionSignature`:

```scala
import io.constellation.lang.semantic._

val signature = FunctionSignature(
  name = "embed-model",           // Name used in constellation-lang
  params = List(                  // Parameter names and types
    "input" -> SemanticType.SCandidates(
      SemanticType.SRecord(Map(
        "text" -> SemanticType.SString
      ))
    )
  ),
  returns = SemanticType.SCandidates(  // Return type
    SemanticType.SRecord(Map(
      "embedding" -> SemanticType.SList(SemanticType.SFloat)
    ))
  ),
  moduleName = "embed-model"      // Constellation module name
)

val registry = FunctionRegistry.empty
registry.register(signature)
```

### Using ModuleBridge

For modules with existing specs, use `ModuleBridge`:

```scala
import io.constellation.lang.ModuleBridge

// Extract types from module spec
val params = ModuleBridge.extractParams(myModule)
val returns = ModuleBridge.extractReturns(myModule)

// Create signature
val sig = ModuleBridge.signatureFromModule(
  languageName = "my-function",
  module = myModule,
  params = params,
  returns = returns
)
```

## Creating Modules

### Using ModuleBuilder

```scala
import io.constellation.ModuleBuilder
import cats.effect.IO

// Define input/output case classes
case class MyInput(data: String, count: Int)
case class MyOutput(result: String)

val module = ModuleBuilder
  .metadata(
    name = "my-module",
    description = "Processes data",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("ml", "processing")
  .implementation[MyInput, MyOutput] { input =>
    IO.pure(MyOutput(s"Processed: ${input.data} x ${input.count}"))
  }
  .build
```

### Pure Implementations

For synchronous operations:

```scala
val module = ModuleBuilder
  .metadata("pure-module", "Pure transformation", 1, 0)
  .implementationPure[MyInput, MyOutput] { input =>
    MyOutput(input.data.toUpperCase)
  }
  .build
```

### With Context

Return additional context alongside the result:

```scala
import cats.Eval
import io.circe.Json

val module = ModuleBuilder
  .metadata("context-module", "With context", 1, 0)
  .implementationWithContext[MyInput, MyOutput] { input =>
    IO.pure(Module.Produces(
      data = MyOutput(input.data),
      implementationContext = Eval.later(Map(
        "processingTime" -> Json.fromLong(System.currentTimeMillis)
      ))
    ))
  }
  .build
```

### Configuration

```scala
import scala.concurrent.duration._

val module = ModuleBuilder
  .metadata("configured-module", "With timeouts", 1, 0)
  .inputsTimeout(10.seconds)   // Time to wait for inputs
  .moduleTimeout(5.seconds)    // Time for module execution
  .implementation[MyInput, MyOutput](processFunction)
  .build
```

## Running Pipelines

### Basic Execution

:::tip Resource Management
Always use `Resource` or `use` to ensure proper cleanup. The `Constellation` instance manages thread pools and caches that need graceful shutdown. When using `IOApp`, the runtime handles this automatically. For other contexts, compose with your application's effect lifecycle.
:::

```scala
import io.constellation._
import cats.effect.unsafe.implicits.global

val result = for {
  constellation <- impl.ConstellationImpl.init
  _ <- modules.traverse(constellation.setModule)

  // Compile the pipeline
  compiled <- IO.fromEither(
    compiler.compile(source, "my-dag").left.map(e => new Exception(e.head.format))
  )

  // Prepare inputs
  inputs = Map(
    "x" -> CValue.CInt(42),
    "y" -> CValue.CString("hello")
  )

  // Run the pipeline
  sig <- constellation.run(compiled.pipeline, inputs)
} yield sig

val signature = result.unsafeRunSync()
```

### Accessing Results

```scala
val sig: DataSignature = result.unsafeRunSync()

// Check execution status
println(s"Status: ${sig.status}")
println(s"Execution ID: ${sig.executionId}")

// Get declared outputs
sig.outputs.foreach { case (name, value) =>
  println(s"Output $name: $value")
}

// Get all computed intermediate values
sig.computedNodes.foreach { case (name, value) =>
  println(s"Node $name: $value")
}

// Check for missing inputs
if (sig.missingInputs.nonEmpty)
  println(s"Missing inputs: ${sig.missingInputs}")
```

## Working with Types

### SemanticType (Compiler)

Used during compilation for type checking:

```scala
import io.constellation.lang.semantic.SemanticType

// Primitives
val stringType = SemanticType.SString
val intType = SemanticType.SInt
val floatType = SemanticType.SFloat
val boolType = SemanticType.SBoolean

// Records
val recordType = SemanticType.SRecord(Map(
  "id" -> SemanticType.SString,
  "count" -> SemanticType.SInt
))

// Parameterized
val listType = SemanticType.SList(SemanticType.SString)
val mapType = SemanticType.SMap(SemanticType.SString, SemanticType.SInt)
val candidatesType = SemanticType.SCandidates(recordType)
```

### CType (Runtime)

Used at runtime for data representation:

```scala
import io.constellation.CType

// Primitives
val stringType = CType.CString
val intType = CType.CInt

// Compound types
val productType = CType.CProduct(Map(
  "name" -> CType.CString,
  "age" -> CType.CInt
))

val listType = CType.CList(CType.CString)
```

### Converting Between Types

```scala
import io.constellation.lang.semantic.SemanticType

// SemanticType → CType
val cType = SemanticType.toCType(semanticType)

// CType → SemanticType
val semType = SemanticType.fromCType(cType)
```

### CValue (Runtime Values)

```scala
import io.constellation.CValue

// Create values
val stringVal = CValue.CString("hello")
val intVal = CValue.CInt(42)
val floatVal = CValue.CFloat(3.14)
val boolVal = CValue.CBoolean(true)

val listVal = CValue.CList(
  List(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
  CType.CInt
)

val productVal = CValue.CProduct(Map(
  "name" -> CValue.CString("Alice"),
  "age" -> CValue.CInt(30)
))

// Access type
val typ: CType = stringVal.ctype
```

## Error Handling

:::warning
Avoid using `unsafeRunSync()` in production code. It blocks the current thread and can cause deadlocks. Use `IOApp` or integrate with your application's effect system for proper async execution.
:::

### Compile Errors

```scala
compiler.compile(source, "dag") match {
  case Right(result) =>
    // Success

  case Left(errors) =>
    errors.foreach {
      case e: CompileError.ParseError =>
        println(s"Syntax error: ${e.format}")

      case e: CompileError.UndefinedVariable =>
        println(s"Unknown variable '${e.name}' at ${e.position}")

      case e: CompileError.UndefinedType =>
        println(s"Unknown type '${e.name}' at ${e.position}")

      case e: CompileError.UndefinedFunction =>
        println(s"Unknown function '${e.name}' at ${e.position}")

      case e: CompileError.TypeMismatch =>
        println(s"Expected ${e.expected}, got ${e.actual} at ${e.position}")

      case e: CompileError.InvalidProjection =>
        println(s"Field '${e.field}' not found. Available: ${e.availableFields}")

      case e: CompileError.IncompatibleMerge =>
        println(s"Cannot merge ${e.leftType} + ${e.rightType}")

      case e =>
        println(e.format)
    }
}
```

### Runtime Errors

```scala
import cats.effect.IO

val result: IO[DataSignature] = constellation.run(compiled.pipeline, inputs)

result.attempt.map {
  case Right(sig) =>
    sig.status match {
      case PipelineStatus.Completed =>
        println(s"Success: ${sig.outputs}")
      case PipelineStatus.Failed(errors) =>
        errors.foreach(e => println(s"Node ${e.nodeName} failed: ${e.message}"))
      case PipelineStatus.Suspended =>
        println(s"Suspended, missing: ${sig.missingInputs}")
    }

  case Left(error) =>
    // Global execution failure
    println(s"Execution failed: ${error.getMessage}")
}
```

## Complete Example

```scala
import cats.effect.{IO, IOApp}
import cats.implicits._
import io.constellation._
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic._

object MyPipeline extends IOApp.Simple {

  // Define module
  case class ProcessInput(data: String)
  case class ProcessOutput(result: String)

  val processModule = ModuleBuilder
    .metadata("process", "Process data", 1, 0)
    .implementationPure[ProcessInput, ProcessOutput] { input =>
      ProcessOutput(input.data.toUpperCase)
    }
    .build

  // Create compiler
  val compiler = LangCompiler.builder
    .withFunction(FunctionSignature(
      name = "process",
      params = List("data" -> SemanticType.SString),
      returns = SemanticType.SString,
      moduleName = "process"
    ))
    .build

  val source = """
    in data: String
    result = process(data)
    out result
  """

  def run: IO[Unit] = for {
    // Initialize
    constellation <- ConstellationImpl.init
    _ <- constellation.setModule(processModule)

    // Compile
    compiled <- IO.fromEither(
      compiler.compile(source, "my-pipeline")
        .left.map(e => new Exception(e.map(_.format).mkString("\n")))
    )

    // Run
    sig <- constellation.run(
      compiled.pipeline,
      Map("data" -> TypeSystem.CValue.VString("hello world"))
    )

    // Output
    _ <- IO.println(s"Status: ${sig.status}")
    _ <- IO.println(s"Results: ${sig.outputs}")
  } yield ()
}
```

## Next Steps

- [HTTP API Overview](./http-api-overview.md) — REST API for polyglot architectures
- [Standard Library](./stdlib.md) — Built-in functions to register with your compiler
- [Error Reference](./error-reference.md) — Compile and runtime error handling
- [Technical Architecture](../architecture/technical-architecture.md) — How pipelines are compiled and executed
- [Cache Backend](../integrations/cache-backend.md) — Implement custom caching for module results
