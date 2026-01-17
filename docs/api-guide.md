# API Guide

This guide covers programmatic usage of Constellation Engine, including compiling constellation-lang programs and creating custom modules.

## Table of Contents

- [Compiling Programs](#compiling-programs)
- [Registering Functions](#registering-functions)
- [Creating Modules](#creating-modules)
- [Running DAGs](#running-dags)
- [Working with Types](#working-with-types)
- [Error Handling](#error-handling)

## Compiling Programs

### Basic Compilation

```scala
import io.constellation.lang.runtime.LangCompiler

val compiler = LangCompiler.empty

val source = """
  in x: Int
  in y: Int
  out x
"""

val result = compiler.compile(source, "my-dag")

result match {
  case Right(compiled) =>
    println(s"DAG name: ${compiled.dagSpec.name}")
    println(s"Modules: ${compiled.dagSpec.modules.size}")
    println(s"Data nodes: ${compiled.dagSpec.data.size}")

  case Left(errors) =>
    errors.foreach(e => println(e.format))
}
```

### Using the Builder

```scala
import io.constellation.lang.runtime.LangCompiler
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
case class CompileResult(
  dagSpec: DagSpec,                              // The compiled DAG
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
import io.constellation.lang.runtime.ModuleBridge

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
import io.constellation.api.ModuleBuilder
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

## Running DAGs

### Basic Execution

```scala
import io.constellation.api.Runtime
import cats.effect.unsafe.implicits.global

val result = for {
  // Compile the program
  compiled <- IO.fromEither(
    compiler.compile(source, "my-dag").left.map(e => new Exception(e.head.format))
  )

  // Prepare initial data
  initData = Map(
    "x" -> CValue.CInt(42),
    "y" -> CValue.CString("hello")
  )

  // Combine registered modules with synthetic modules
  allModules = registeredModules ++ compiled.syntheticModules

  // Run the DAG
  state <- Runtime.run(compiled.dagSpec, initData, allModules)
} yield state

val finalState = result.unsafeRunSync()
```

### Accessing Results

```scala
val state: Runtime.State = result.unsafeRunSync()

// Get execution latency
state.latency.foreach(l => println(s"Total time: $l"))

// Get module statuses
state.moduleStatus.foreach { case (moduleId, status) =>
  status.value match {
    case Module.Status.Fired(latency, context) =>
      println(s"Module $moduleId completed in $latency")
    case Module.Status.Failed(error) =>
      println(s"Module $moduleId failed: ${error.getMessage}")
    case Module.Status.Timed(timeout) =>
      println(s"Module $moduleId timed out after $timeout")
    case Module.Status.Unfired =>
      println(s"Module $moduleId never ran")
  }
}

// Get data values
state.data.foreach { case (dataId, value) =>
  println(s"Data $dataId: ${value.value}")
}
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
import io.constellation.api.CType

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
import io.constellation.api.CValue

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

val result: IO[Runtime.State] = Runtime.run(dag, data, modules)

result.attempt.map {
  case Right(state) =>
    // Check individual module failures
    val failures = state.moduleStatus.collect {
      case (id, status) if status.value.isInstanceOf[Module.Status.Failed] =>
        id -> status.value.asInstanceOf[Module.Status.Failed].error
    }

  case Left(error) =>
    // Global execution failure
    println(s"DAG execution failed: ${error.getMessage}")
}
```

## Complete Example

```scala
import cats.effect.{IO, IOApp}
import io.constellation.api._
import io.constellation.lang.runtime._
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
    // Compile
    compiled <- IO.fromEither(
      compiler.compile(source, "my-pipeline")
        .left.map(e => new Exception(e.map(_.format).mkString("\n")))
    )

    // Prepare
    initData = Map("data" -> CValue.CString("hello world"))
    modules = Map(processModule.spec.name -> processModule) ++ compiled.syntheticModules

    // Run
    state <- Runtime.run(compiled.dagSpec, initData, modules)

    // Output
    _ <- IO.println(s"Completed in ${state.latency}")
    _ <- IO.println(s"Results: ${state.data}")
  } yield ()
}
```
