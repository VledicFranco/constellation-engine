# Constellation Engine - LLM Code Assistant Guide

This document provides comprehensive context for AI code assistants working with the Constellation Engine codebase.

## Project Overview

**Constellation Engine** is a type-safe, composable ML orchestration framework for Scala 3. It allows users to:
1. Define custom processing modules using a fluent `ModuleBuilder` API
2. Compose pipelines using the `constellation-lang` DSL
3. Execute pipelines with automatic dependency resolution and type checking
4. Expose pipelines via HTTP API

**Target Users:** Data scientists, ML engineers, and backend developers building data processing and ML pipelines.

**Key Value Proposition:** Type-safe orchestration with declarative DSL that eliminates boilerplate while maintaining compile-time safety for module definitions.

## Architecture

### Module Structure (SBT Multi-Project Build)

```
constellation-engine/
├── modules/
│   ├── core/              # Type system, foundational types (no dependencies)
│   ├── runtime/           # Execution engine, ModuleBuilder, registries
│   ├── lang-ast/          # AST definitions for constellation-lang
│   ├── lang-parser/       # Parser for constellation-lang (uses cats-parse)
│   ├── lang-compiler/     # Semantic analysis, type checking, DAG compilation
│   ├── lang-stdlib/       # Standard library modules and examples
│   ├── lang-lsp/          # Language Server Protocol implementation
│   ├── http-api/          # HTTP server with http4s + WebSocket LSP
│   └── example-app/       # Example application (demonstrates library usage)
├── docs/                  # Documentation
├── vscode-extension/      # VSCode extension for constellation-lang
├── build.sbt              # SBT multi-project configuration
└── llm.md                 # This file
```

### Dependency Graph

```
core (circe, cats-core, cats-effect)
  ↑
  ├────────────┬──────────┐
  │            │          │
runtime     lang-ast  (independent)
  ↑            ↑
  │            │
  │       lang-parser (cats-parse)
  │            │
  └─────┬──────┘
        │
   lang-compiler
        ↑
        ├──────┬─────────┬─────────┐
        │      │         │         │
   lang-stdlib │     lang-lsp  example-app
               │         │
               └────┬────┘
                    │
                http-api (http4s + WebSockets)
```

**Critical Rule:** Modules can only depend on modules above them in the graph. Never create circular dependencies.

### Tech Stack

- **Scala:** 3.3.1
- **Effect System:** Cats Effect 3.5.2 (IO monad for side effects)
- **JSON:** Circe 0.14.6 (with generic derivation)
- **Parser:** cats-parse 1.0.0 (for constellation-lang)
- **HTTP:** http4s 0.23.25 with ember-server
- **Testing:** ScalaTest 3.2.17

## Key Concepts

### 1. Type System (`core` module)

**File:** `modules/core/src/main/scala/io/constellation/TypeSystem.scala`

Core types representing values and their types at runtime:

```scala
// Type representation
enum CType:
  case TString
  case TLong
  case TDouble
  case TBoolean
  case TList(elementType: CType)
  case TStruct(fields: Map[String, CType])
  case TUnit

// Value representation
enum CValue:
  case VString(value: String)
  case VLong(value: Long)
  case VDouble(value: Double)
  case VBoolean(value: Boolean)
  case VList(values: List[CValue], elementType: CType)
  case VStruct(fields: Map[String, CValue], structType: Map[String, CType])
  case VUnit
```

**Type Tags:** Use `CTypeTag[A]` for compile-time type mapping:
```scala
given CTypeTag[String] with
  def cType: CType = CType.TString
```

**Injectors/Extractors:** Convert between Scala types and `CValue`:
```scala
// Inject: String => CValue
CValueInjector[String].inject("hello") // VString("hello")

// Extract: CValue => String
CValueExtractor[String].extract(VString("hello")) // Right("hello")
```

### 2. Modules (`runtime` module)

**Files:**
- `modules/runtime/src/main/scala/io/constellation/ModuleBuilder.scala`
- `modules/runtime/src/main/scala/io/constellation/Spec.scala`

**Module Definition Pattern:**

```scala
// 1. Define input/output case classes
case class MyInput(text: String, count: Long)
case class MyOutput(result: String)

// 2. Build module with fluent API
val myModule: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "MyModule",
    description = "Does something useful",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("domain", "transform")  // Optional tags
  .implementationPure[MyInput, MyOutput] { input =>
    MyOutput(input.text * input.count.toInt)
  }
  .build
```

**Pure vs IO implementations:**
- `.implementationPure[I, O](f: I => O)` - Pure functions, no side effects
- `.implementation[I, O](f: I => IO[O])` - Effectful operations (HTTP calls, DB queries, etc.)

**Module Types:**
- `Module.Uninitialized` - Module definition, not yet registered
- `Module.Initialized` - Module with runtime context, ready for execution

### 3. Constellation-Lang DSL

**Files:**
- `modules/lang-ast/src/main/scala/io/constellation/lang/ast/AST.scala` (AST definitions)
- `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala` (Parser)
- `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala` (Compiler)

**Syntax:**

```constellation
# Declare inputs with types
in text: String
in threshold: Int

# Call modules (case-sensitive, must match registered module names)
cleaned = Trim(text)
uppercased = Uppercase(cleaned)
wordCount = WordCount(uppercased)

# Conditional output
out uppercased
out wordCount
```

**Type System:**
- Basic types: `String`, `Int`, `Long`, `Double`, `Boolean`
- Collections: `List<T>` (e.g., `List<String>`, `List<Int>`)
- Structs: Not directly expressible in DSL, but created from case classes

**Important:** Module names in constellation-lang must exactly match the `name` parameter in `ModuleBuilder.metadata()`.

### 4. DAG Compilation

**File:** `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala`

**Process:**
1. **Parse:** Text → AST (`ConstellationParser`)
2. **Type Check:** AST → Typed AST (`TypeChecker`)
3. **Generate IR:** Typed AST → Intermediate Representation (`IRGenerator`)
4. **Compile DAG:** IR → `DagSpec` (`DagCompiler`)

**Result:** `DagSpec` containing:
- `components: Map[String, Component]` - All computation steps
- `outputs: Map[String, ComponentId]` - Output mapping
- `inputs: Map[String, CType]` - Required inputs with types

### 5. Execution (`runtime` module)

**File:** `modules/runtime/src/main/scala/io/constellation/Runtime.scala`

**Execution Model:**
1. Initialize modules with runtime context (`Module.Initialized`)
2. Build execution plan from DAG
3. Execute components in dependency order
4. Return outputs as `Map[String, CValue]`

```scala
// Register module
constellation.setModule(myModule)

// Register compiled DAG
constellation.setDag("my-pipeline", dagSpec)

// Execute
val result: IO[Map[String, CValue]] =
  constellation.execute("my-pipeline", inputs = Map("text" -> VString("hello")))
```

## Common Patterns

### Pattern 1: Creating Custom Modules

**Location:** See `modules/example-app/src/main/scala/io/constellation/examples/app/modules/`

```scala
object MyModules {
  // Always use case classes for inputs/outputs (no tuples!)
  case class TextInput(text: String)
  case class TextOutput(result: String)

  val uppercase: Module.Uninitialized = ModuleBuilder
    .metadata("Uppercase", "Converts text to uppercase", 1, 0)
    .tags("text", "transform")
    .implementationPure[TextInput, TextOutput] { input =>
      TextOutput(input.text.toUpperCase)
    }
    .build

  // Collect all modules
  val all: List[Module.Uninitialized] = List(uppercase)
}
```

**Rules:**
- Use case classes, not tuples (Scala 3 doesn't support single-element tuples)
- Field names in case classes map to variable names in constellation-lang
- Module names must be unique within a Constellation instance

### Pattern 2: Registering Modules

```scala
import cats.implicits._  // Required for .traverse

for {
  constellation <- ConstellationImpl.init
  _ <- MyModules.all.traverse(constellation.setModule)
} yield ()
```

**Alternative (avoiding cats.implicits):**
```scala
constellation.setModules(MyModules.all)  // If this method exists
```

### Pattern 3: Starting HTTP Server

**File:** `modules/http-api/src/main/scala/io/constellation/http/ConstellationServer.scala`

```scala
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.http.ConstellationServer

for {
  constellation <- ConstellationImpl.init
  _ <- myModules.traverse(constellation.setModule)
  compiler = LangCompiler.empty  // Or StdLib.compiler for stdlib
  _ <- ConstellationServer
    .builder(constellation, compiler)
    .withHost("0.0.0.0")
    .withPort(8080)
    .run  // Runs forever
} yield ()
```

### Pattern 4: Compiling Constellation-Lang Programs

```scala
import io.constellation.lang.LangCompiler

val source = """
  in text: String
  result = Uppercase(text)
  out result
"""

val compiler = LangCompiler.empty
compiler.compile(source, "my-dag") match {
  case Right(compiled) =>
    // compiled.dagSpec: DagSpec
    constellation.setDag("my-dag", compiled.dagSpec)
  case Left(errors) =>
    // errors: List[CompilationError]
    errors.foreach(e => println(e.message))
}
```

### Pattern 5: Testing Modules

**Location:** See `modules/runtime/src/test/scala/io/constellation/ModuleBuilderTest.scala`

```scala
class MyModuleTest extends AnyFlatSpec with should.Matchers {
  "MyModule" should "transform input correctly" in {
    val input = MyInput("hello", 3)
    val result = myModule.implementation(input)  // For pure modules

    result shouldBe MyOutput("hellohellohello")
  }
}
```

**For IO-based modules:**
```scala
"AsyncModule" should "fetch data" in {
  val input = MyInput("key")
  val result = myModule.implementation(input).unsafeRunSync()  // Unsafe for tests only

  result shouldBe MyOutput("data")
}
```

## File Organization

### Package Structure

All code uses package `io.constellation.*`:

```
io.constellation
├── TypeSystem.scala           (core module)
├── Spec.scala                 (core module)
├── Runtime.scala              (runtime module)
├── ModuleBuilder.scala        (runtime module)
├── Constellation.scala        (runtime module)
├── impl/
│   ├── ConstellationImpl.scala
│   ├── DagRegistryImpl.scala
│   └── ModuleRegistryImpl.scala
├── lang/
│   ├── ast/AST.scala          (lang-ast module)
│   ├── parser/ConstellationParser.scala  (lang-parser)
│   ├── semantic/TypeChecker.scala        (lang-compiler)
│   ├── compiler/DagCompiler.scala        (lang-compiler)
│   └── LangCompiler.scala     (lang-compiler, NOTE: not in lang.runtime)
├── stdlib/StdLib.scala        (lang-stdlib)
├── examples/                  (lang-stdlib)
├── http/                      (http-api module)
│   ├── ConstellationServer.scala
│   ├── ConstellationRoutes.scala
│   └── models/ApiModels.scala
└── examples.app/              (example-app module)
    ├── TextProcessingApp.scala
    └── modules/
```

### Important File Locations

| Component | File Path |
|-----------|-----------|
| Type system | `modules/core/src/main/scala/io/constellation/TypeSystem.scala` |
| Module specs | `modules/core/src/main/scala/io/constellation/Spec.scala` |
| ModuleBuilder | `modules/runtime/src/main/scala/io/constellation/ModuleBuilder.scala` |
| Runtime engine | `modules/runtime/src/main/scala/io/constellation/Runtime.scala` |
| Constellation API | `modules/runtime/src/main/scala/io/constellation/Constellation.scala` |
| AST | `modules/lang-ast/src/main/scala/io/constellation/lang/ast/AST.scala` |
| Parser | `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala` |
| Type checker | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala` |
| DAG compiler | `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala` |
| LangCompiler | `modules/lang-compiler/src/main/scala/io/constellation/lang/LangCompiler.scala` |
| Standard library | `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/StdLib.scala` |
| HTTP server | `modules/http-api/src/main/scala/io/constellation/http/ConstellationServer.scala` |
| Example app | `modules/example-app/src/main/scala/io/constellation/examples/app/TextProcessingApp.scala` |

## Development Workflows

### Quick Start (Recommended)

The fastest way to start developing:

```bash
# One command to start everything
make dev

# Or with hot-reload (server auto-restarts on code changes)
make server-rerun &
make ext-watch
```

### Makefile Commands

| Command | Description |
|---------|-------------|
| `make dev` | Start full dev environment (server + extension watch) |
| `make server` | Start HTTP/LSP server with ExampleLib |
| `make server-rerun` | Start server with hot-reload (auto-restart) |
| `make watch` | Watch Scala sources and recompile |
| `make ext-watch` | Watch TypeScript and recompile |
| `make test` | Run all tests |
| `make compile` | Compile all Scala modules |
| `make extension` | Compile VSCode extension |
| `make all` | Compile everything (Scala + TypeScript) |
| `make clean` | Clean all build artifacts |
| `make install` | Install all dependencies |

**Module-specific tests:**
```bash
make test-core      # Test core module
make test-compiler  # Test compiler
make test-lsp       # Test LSP server
make test-parser    # Test parser
make test-http      # Test HTTP API
```

### VSCode Tasks

Press `Ctrl+Shift+P` → "Tasks: Run Task" to access:

| Task | Description |
|------|-------------|
| **Compile All** | Compile Scala modules (default build) |
| **Run Tests** | Run all tests |
| **Start Server (ExampleLib)** | Start HTTP/LSP server |
| **Watch Compile** | Watch Scala and recompile on changes |
| **Watch Extension** | Watch TypeScript and recompile |
| **Full Dev Setup** | Start server + extension watch in parallel |
| **Test Core/Compiler/LSP/Parser** | Module-specific tests |

### Development Scripts

Platform-specific startup scripts in `scripts/`:

**Windows (PowerShell):**
```powershell
.\scripts\dev.ps1                    # Full dev environment
.\scripts\dev.ps1 -HotReload         # With auto-restart
.\scripts\dev.ps1 -ServerOnly        # Server only
.\scripts\dev.ps1 -WatchOnly         # TypeScript watch only
```

**Unix/Mac (Bash):**
```bash
./scripts/dev.sh                     # Full dev environment
./scripts/dev.sh --hot-reload        # With auto-restart
./scripts/dev.sh --server-only       # Server only
./scripts/dev.sh --watch-only        # TypeScript watch only
```

### Hot Reload (Server Auto-Restart)

For automatic server restart when Scala code changes:

```bash
# Using Make
make server-rerun

# Using SBT directly
sbt "~exampleApp/reStart"
```

This uses the sbt-revolver plugin configured in `project/plugins.sbt`.

### Building the Project

```bash
# Preferred: Use make commands
make clean && make compile     # Clean build
make test                      # Run all tests
make test-core                 # Run core module tests
make test-compiler             # Run compiler module tests
make server                    # Run example application/server
make dev                       # Development mode with hot reload

# Advanced: Direct sbt for specific needs
sbt runtime/test               # Test specific module not covered by make
sbt ~compile                   # Continuous compilation (watch mode)
```

### Adding a New Module to the Build

1. Create module directory: `modules/my-module/`
2. Create `modules/my-module/build.sbt` (optional, or configure in root build.sbt)
3. Update root `build.sbt`:

```scala
lazy val myModule = (project in file("modules/my-module"))
  .dependsOn(core, runtime)  // Add dependencies
  .settings(
    name := "constellation-my-module",
    libraryDependencies ++= Seq(
      // Add specific dependencies
    )
  )

lazy val root = (project in file("."))
  .aggregate(
    core,
    runtime,
    // ... other modules ...
    myModule  // Add here
  )
```

### Working with HTTP API

**Start server:**
```bash
sbt "exampleApp/runMain io.constellation.examples.app.TextProcessingApp"
```

**Test endpoints:**
```bash
# Health check
curl http://localhost:8080/health

# List modules
curl http://localhost:8080/modules | jq

# Compile pipeline
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in text: String\nresult = Uppercase(text)\nout result",
    "dagName": "test-pipeline"
  }' | jq

# List compiled DAGs
curl http://localhost:8080/dags | jq

# Execute pipeline
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{
    "dagName": "test-pipeline",
    "inputs": {"text": "hello world"}
  }' | jq
```

## Common Tasks

### Task: Add a New Text Processing Module

**Location:** `modules/example-app/src/main/scala/io/constellation/examples/app/modules/TextModules.scala`

```scala
// 1. Define input/output types
case class ReverseInput(text: String)
case class ReverseOutput(result: String)

// 2. Create module
val reverse: Module.Uninitialized = ModuleBuilder
  .metadata(
    name = "Reverse",
    description = "Reverses the text",
    majorVersion = 1,
    minorVersion = 0
  )
  .tags("text", "transform")
  .implementationPure[ReverseInput, ReverseOutput] { input =>
    ReverseOutput(input.text.reverse)
  }
  .build

// 3. Add to `all` list
val all: List[Module.Uninitialized] = List(
  uppercase,
  lowercase,
  // ...
  reverse  // Add here
)
```

### Task: Add a Module with Multiple Inputs

```scala
case class ConcatInput(first: String, second: String, separator: String)
case class ConcatOutput(result: String)

val concat: Module.Uninitialized = ModuleBuilder
  .metadata("Concat", "Concatenates strings with separator", 1, 0)
  .implementationPure[ConcatInput, ConcatOutput] { input =>
    ConcatOutput(s"${input.first}${input.separator}${input.second}")
  }
  .build
```

**Usage in constellation-lang:**
```constellation
in firstName: String
in lastName: String
fullName = Concat(firstName, lastName, " ")
out fullName
```

### Task: Add an Async Module (with IO)

```scala
import cats.effect.IO
import scala.concurrent.duration._

case class FetchInput(url: String)
case class FetchOutput(content: String)

val fetchUrl: Module.Uninitialized = ModuleBuilder
  .metadata("FetchUrl", "Fetches content from URL", 1, 0)
  .tags("io", "network")
  .implementation[FetchInput, FetchOutput] { input =>
    IO {
      // Perform HTTP request (simplified)
      val content = scala.io.Source.fromURL(input.url).mkString
      FetchOutput(content)
    }
  }
  .build
```

### Task: Using ExampleLib (All Functions)

The `ExampleLib` provides a compiler with all standard library and example app functions pre-registered:

**Location:** `modules/example-app/src/main/scala/io/constellation/examples/app/ExampleLib.scala`

```scala
import io.constellation.examples.app.ExampleLib

// Get compiler with all functions (StdLib + DataModules + TextModules)
val compiler = ExampleLib.compiler

// Available functions:
// Data: SumList, Average, Max, Min, FilterGreaterThan, MultiplyEach, Range, FormatNumber
// Text: Uppercase, Lowercase, Trim, Replace, WordCount, TextLength, Contains, SplitLines, Split
// StdLib: add, subtract, multiply, divide, concat, upper, lower, etc.
```

**Running ExampleServer (full function library):**
```bash
sbt "exampleApp/runMain io.constellation.examples.app.server.ExampleServer"
```

### Task: Add Standard Library Function

**Location:** `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/StdLib.scala`

```scala
object StdLib {
  // Define module
  val myNewFunction: Module.Uninitialized = ModuleBuilder
    .metadata("MyFunction", "Description", 1, 0)
    .implementationPure[Input, Output] { ... }
    .build

  // Add to allModules
  private val allModules: List[Module.Uninitialized] = List(
    // ... existing modules ...
    myNewFunction
  )
}
```

### Task: Create a New Constellation-Lang Example

**Location:** `modules/example-app/examples/my-pipeline.cst`

```constellation
# my-pipeline.cst
# Purpose: Describe what this pipeline does

# Inputs
in inputText: String
in threshold: Int

# Processing steps
cleaned = Trim(inputText)
uppercased = Uppercase(cleaned)
wordCount = WordCount(uppercased)

# Outputs
out uppercased
out wordCount
```

**Test with curl:**
```bash
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d @<(cat <<EOF
{
  "source": "$(cat modules/example-app/examples/my-pipeline.cst)",
  "dagName": "my-pipeline"
}
EOF
)
```

## Common Pitfalls and Solutions

### Pitfall 1: Using Tuples Instead of Case Classes

**Problem:**
```scala
// This doesn't work in Scala 3
val myModule = ModuleBuilder
  .implementationPure[(String,), (String,)] { input =>
    (input._1.toUpperCase,)
  }
```

**Solution:**
```scala
case class Input(text: String)
case class Output(result: String)

val myModule = ModuleBuilder
  .implementationPure[Input, Output] { input =>
    Output(input.text.toUpperCase)
  }
```

### Pitfall 2: Missing cats.implicits Import

**Problem:**
```scala
// Compilation error: value traverse is not a member of List
modules.traverse(constellation.setModule)
```

**Solution:**
```scala
import cats.implicits._

modules.traverse(constellation.setModule)
```

### Pitfall 3: Wrong Package After Moving Files

**Problem:** After using `git mv`, files still have old package declarations.

**Solution:** Always update package declarations after moving files:
```scala
// OLD (before move)
package io.constellation.api

// NEW (after move to runtime)
package io.constellation
```

### Pitfall 4: Module Name Mismatch

**Problem:** Module name in ModuleBuilder doesn't match constellation-lang usage.

```scala
// Scala
ModuleBuilder.metadata("UpperCase", ...) // Wrong capitalization

// constellation-lang
result = Uppercase(text)  // Won't find module
```

**Solution:** Ensure exact match (case-sensitive):
```scala
ModuleBuilder.metadata("Uppercase", ...)
```

### Pitfall 5: Wrong Method Name for Constellation Init

**Problem:**
```scala
constellation <- ConstellationImpl.create  // Wrong - method doesn't exist
```

**Solution:**
```scala
constellation <- ConstellationImpl.init  // Correct
```

### Pitfall 6: Using createDag Instead of getDag

**Problem:**
```scala
// This creates a new empty DAG, not what you want!
dag <- constellation.createDag(dagName)
```

**Solution:**
```scala
// This retrieves an existing compiled DAG
dag <- constellation.getDag(dagName)
```

### Pitfall 7: Field Name Mismatch

**Problem:**
```scala
case class MyInput(inputText: String)  // Field: inputText

// constellation-lang
result = MyModule(text)  // Field name doesn't match!
```

**Solution:** Field names must match exactly:
```scala
case class MyInput(text: String)  // Field: text

// constellation-lang
result = MyModule(text)  // Now matches
```

### Pitfall 8: Circular Module Dependencies

**Problem:** Trying to make `core` depend on `runtime` or `runtime` depend on `lang-compiler`.

**Solution:** Follow the dependency graph strictly:
```
core → runtime → lang-compiler → http-api
       ↓
    lang-ast → lang-parser
```

## JSON Encoding/Decoding

**Files:**
- `modules/runtime/src/main/scala/io/constellation/CustomJsonCodecs.scala`
- `modules/http-api/src/main/scala/io/constellation/http/models/ApiModels.scala`

### Circe Encoders/Decoders

**Automatic derivation (most case classes):**
```scala
import io.circe.generic.semiauto._

case class MyData(name: String, count: Int)

given Encoder[MyData] = deriveEncoder
given Decoder[MyData] = deriveDecoder
```

**Custom encoding (for enums or special cases):**
```scala
import io.circe.{Encoder, Decoder, Json}

given Encoder[CType] = {
  case CType.TString => Json.fromString("String")
  case CType.TLong => Json.fromString("Long")
  // ...
}

given Decoder[CType] = Decoder[String].emap {
  case "String" => Right(CType.TString)
  case "Long" => Right(CType.TLong)
  // ...
}
```

## Testing Patterns

### Unit Testing Modules

**Location:** `modules/runtime/src/test/scala/io/constellation/ModuleBuilderTest.scala`

```scala
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MyModuleTest extends AnyFlatSpec with Matchers {
  "MyModule" should "process input correctly" in {
    case class Input(value: String)
    case class Output(result: String)

    val module = ModuleBuilder
      .metadata("Test", "Test module", 1, 0)
      .implementationPure[Input, Output] { input =>
        Output(input.value.toUpperCase)
      }
      .build

    // For pure modules, can test implementation directly
    // (This is simplified - actual implementation is private)
    val input = Input("hello")
    // Test via execution instead
  }
}
```

### Integration Testing with Constellation

```scala
import cats.effect.unsafe.implicits.global

"Full pipeline" should "execute correctly" in {
  val result = (for {
    constellation <- ConstellationImpl.init
    _ <- constellation.setModule(myModule)
    _ <- constellation.setDag("test", dagSpec)
    outputs <- constellation.execute("test", Map("input" -> VString("hello")))
  } yield outputs).unsafeRunSync()

  result("output") shouldBe VString("HELLO")
}
```

### Testing HTTP Endpoints

**Location:** `modules/http-api/src/test/scala/io/constellation/http/ConstellationRoutesTest.scala`

```scala
import org.http4s._
import org.http4s.implicits._
import org.http4s.circe._

"POST /compile" should "compile valid program" in {
  val request = Request[IO](Method.POST, uri"/compile")
    .withEntity(CompileRequest(
      source = "in x: String\nout x",
      dagName = "test"
    ))

  val response = routes.orNotFound.run(request).unsafeRunSync()

  response.status shouldBe Status.Ok
}
```

## Code Style and Conventions

### Naming

- **Modules:** PascalCase (e.g., `Uppercase`, `WordCount`)
- **Module variables:** camelCase (e.g., `val uppercase`, `val wordCount`)
- **Case classes:** PascalCase (e.g., `TextInput`, `WordCountOutput`)
- **Fields:** camelCase (e.g., `text`, `wordCount`)
- **Packages:** lowercase (e.g., `io.constellation.lang.compiler`)

### Module Organization

```scala
object MyModules {
  // 1. Group by functionality
  // ========== Text Transformers ==========

  // 2. Define input/output types close to usage
  case class UppercaseInput(text: String)
  case class UppercaseOutput(result: String)

  // 3. Define module
  val uppercase: Module.Uninitialized = ...

  // 4. Collect all modules at bottom
  val all: List[Module.Uninitialized] = List(...)
}
```

### Error Handling

**In pure modules:**
```scala
.implementationPure[Input, Output] { input =>
  if (input.value.isEmpty)
    throw new IllegalArgumentException("Value cannot be empty")
  else
    Output(input.value.toUpperCase)
}
```

**In IO modules:**
```scala
.implementation[Input, Output] { input =>
  if (input.value.isEmpty)
    IO.raiseError(new IllegalArgumentException("Value cannot be empty"))
  else
    IO.pure(Output(input.value.toUpperCase))
}
```

### Comments

- Use ScalaDoc for public APIs
- Use inline comments for complex logic
- Keep constellation-lang examples commented

```scala
/** Converts text to uppercase.
  *
  * Example usage in constellation-lang:
  * {{{
  * in text: String
  * result = Uppercase(text)
  * out result
  * }}}
  *
  * @param input TextInput with text field
  * @return TextOutput with uppercased text
  */
val uppercase: Module.Uninitialized = ...
```

## Quick Reference

### Most Common Imports

```scala
// Core types
import io.constellation._
import io.constellation.TypeSystem._

// Module building
import io.constellation.ModuleBuilder

// Effects
import cats.effect.IO
import cats.implicits._

// Constellation instance
import io.constellation.impl.ConstellationImpl

// Compiler
import io.constellation.lang.LangCompiler

// HTTP (if using http-api)
import io.constellation.http.ConstellationServer

// Testing
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
```

### Common Commands

**Prefer `make` commands over raw `sbt`:**

```bash
make compile                   # Compile all modules
make test                      # Run all tests
make test-core                 # Test specific module
make test-compiler             # Test compiler module
make dev                       # Start dev environment
make server                    # Start server only
make clean                     # Clean build artifacts
```

**Advanced SBT commands (when `make` targets don't exist):**

```bash
sbt core/test                  # Test specific module directly
sbt ~compile                   # Watch mode (continuous compilation)
sbt "exampleApp/run"           # Run example app
sbt ";clean;compile;test"      # Full rebuild and test
```

### Module Template

```scala
package io.constellation.mypackage

import io.constellation._
import cats.effect.IO

object MyModules {

  case class MyInput(/* fields */)
  case class MyOutput(/* fields */)

  val myModule: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "MyModule",
      description = "What it does",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("category")
    .implementationPure[MyInput, MyOutput] { input =>
      MyOutput(/* transformation */)
    }
    .build

  val all: List[Module.Uninitialized] = List(myModule)
}
```

### Constellation-Lang Template

```constellation
# Pipeline name and description

# Inputs (required)
in inputName: Type

# Processing (module calls)
intermediate = ModuleName(inputName)
result = AnotherModule(intermediate)

# Outputs (required)
out result
```

## VSCode Extension Features

The VSCode extension provides a rich editing experience for constellation-lang files with multiple interactive features.

### Keyboard Shortcuts

| Shortcut | Command | Description |
|----------|---------|-------------|
| `Ctrl+Shift+R` | Run Script | Execute the current .cst file and show results |
| `Ctrl+Shift+D` | Show DAG Visualization | Visualize the pipeline as an interactive graph |
| `Ctrl+Space` | Autocomplete | Show available modules and keywords |

### Script Runner Panel

The Script Runner provides an interactive way to execute constellation-lang scripts:

- **Input Form**: Automatically generates input fields based on declared inputs
- **Type-aware UI**: Different controls for String, Int, List<T>, Boolean types
- **Live Results**: Shows execution output with formatted JSON
- **Error Display**: Compilation and runtime errors with source locations

**Trigger:** `Ctrl+Shift+R` or click the play icon in the editor title bar.

### DAG Visualizer Panel

The DAG Visualizer renders your pipeline as an interactive directed graph:

- **Node Types**:
  - Data nodes (blue, rounded): Inputs and intermediate values
  - Module nodes (orange, rectangular): Processing steps
- **Edges**: Curved arrows showing data flow direction
- **Interaction**:
  - Pan by clicking and dragging
  - Zoom with mouse wheel
  - Click nodes for details (future feature)
- **Auto-refresh**: Updates when the source file changes

**Trigger:** `Ctrl+Shift+D` or click the graph icon in the editor title bar.

### LSP Features

| Feature | Description | Trigger |
|---------|-------------|---------|
| **Autocomplete** | Module names, keywords, types | `Ctrl+Space` |
| **Diagnostics** | Real-time compilation errors | Automatic |
| **Hover Info** | Module documentation and types | Mouse hover |
| **Go to Definition** | Navigate to module source (planned) | `F12` |

## Language Server Protocol (LSP) Integration

Constellation Engine includes a full LSP implementation for constellation-lang, enabling IDE features in VSCode and other editors.

### Quick Start with LSP

**1. Start server with LSP (using ExampleLib with all functions):**
```bash
sbt "exampleApp/runMain io.constellation.examples.app.server.ExampleServer"
```

**2. Install VSCode extension:**
```bash
cd vscode-extension
npm install && npm run compile
```

**3. Open VSCode and create a `.cst` file:**
```constellation
in text: String
result = Uppercase(text)  # Autocomplete works here!
out result
```

### LSP Features

| Feature | Description | Trigger |
|---------|-------------|---------|
| **Autocomplete** | Module names and keywords | `Ctrl+Space` while typing |
| **Diagnostics** | Real-time compilation errors | Automatic as you type |
| **Hover Info** | Module documentation | Hover mouse over module name |
| **Execute Pipeline** | Run pipeline from editor | Command Palette → "Constellation: Execute" |

### LSP Architecture

```
VSCode Extension (TypeScript)
    ↓ WebSocket at ws://localhost:8080/lsp
ConstellationLanguageServer (Scala)
    ↓
DocumentManager + LangCompiler + Constellation
```

**Key Files:**
- `modules/lang-lsp/src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala` - Main LSP server
- `modules/http-api/src/main/scala/io/constellation/http/LspWebSocketHandler.scala` - WebSocket endpoint
- `vscode-extension/src/extension.ts` - VSCode language client

### LSP Protocol Messages

**Supported Requests:**
- `initialize` - Start LSP session
- `textDocument/completion` - Get autocomplete items
- `textDocument/hover` - Get hover information
- `constellation/executePipeline` - Execute pipeline (custom command)

**Notifications:**
- `textDocument/didOpen` - File opened
- `textDocument/didChange` - File changed
- `textDocument/publishDiagnostics` - Server sends errors to client

### Adding LSP to Your App

LSP support is automatic when using `ConstellationServer`:

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withPort(8080)  // LSP available at ws://localhost:8080/lsp
  .run
```

### VSCode Extension Configuration

**Settings:**
```json
{
  "constellation.server.url": "ws://localhost:8080/lsp"
}
```

**File Association:**
- Extension: `.cst`
- Language ID: `constellation`

### Testing LSP

**Manual test with websocat:**
```bash
websocat ws://localhost:8080/lsp

# Send initialize request
{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,"rootUri":null,"capabilities":{}}}
```

**Unit test:**
```scala
// Test autocomplete
val request = Request(
  id = RequestId.NumberId(1),
  method = "textDocument/completion",
  params = Some(completionParams.asJson)
)
server.handleRequest(request) // Returns completions
```

### Documentation

For complete LSP documentation, see:
- **LSP Integration Guide:** `docs/LSP_INTEGRATION.md`
- **VSCode Extension README:** `vscode-extension/README.md`

## Resources

- **Main README:** `/README.md` (if exists)
- **Example Application:** `modules/example-app/README.md`
- **User Learnings:** `modules/example-app/LEARNINGS.md`
- **ML Orchestration Guide:** `docs/ML_ORCHESTRATION_CHALLENGES.md`
- **LSP Integration:** `docs/LSP_INTEGRATION.md`
- **Test Files:** Best source of usage examples

## When to Use Which Module

| Task | Module | Key Files |
|------|--------|-----------|
| Define new types | `core` | `TypeSystem.scala` |
| Create custom modules | `runtime` | `ModuleBuilder.scala` |
| Parse constellation-lang | `lang-parser` | `ConstellationParser.scala` |
| Compile to DAG | `lang-compiler` | `DagCompiler.scala` |
| Add stdlib functions | `lang-stdlib` | `StdLib.scala` |
| Expose HTTP API | `http-api` | `ConstellationServer.scala` |
| Example implementation | `example-app` | `TextProcessingApp.scala` |

## Version Information

- **Scala:** 3.3.1
- **SBT:** 1.9.x (check `project/build.properties`)
- **Cats Effect:** 3.5.2
- **http4s:** 0.23.25
- **Circe:** 0.14.6

---

**Last Updated:** 2026-01-17

## Recent Additions (January 2026)

- **DAG Visualizer**: Interactive graph visualization of pipelines (`Ctrl+Shift+D`)
- **Script Runner**: Execute scripts directly from VSCode with input forms (`Ctrl+Shift+R`)
- **ExampleLib**: Combined compiler with StdLib + DataModules + TextModules
- **ExampleServer**: HTTP server with full function library at `modules/example-app/src/main/scala/.../server/`
- **LSP getDagStructure**: New endpoint for retrieving compiled DAG structure as JSON

**For Questions:** See test files for usage examples, or check `modules/example-app/` for complete working examples.
