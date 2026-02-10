---
title: "Getting Started"
sidebar_position: 2
description: "Quick onboarding for LLMs learning Constellation Engine"
---

# Getting Started with Constellation Engine

**Goal:** Get you productive with Constellation in 10 minutes.

## What is Constellation Engine?

**In 5 Lines:**
- A **type-safe pipeline orchestration framework** for Scala 3
- Define processing **modules** in Scala using `ModuleBuilder`
- Compose **pipelines** in `constellation-lang` (`.cst` files)
- Automatic **parallel execution** via DAG compilation
- **HTTP API** for remote execution or **embedded API** for programmatic use

## Why Constellation Exists

**Problem:** Building data pipelines is repetitive and error-prone:
- Manual dependency tracking
- Type mismatches at runtime
- Difficult parallelization
- Boilerplate for HTTP APIs

**Solution:** Constellation provides:
- **Declarative pipelines** - Focus on logic, not orchestration
- **Type safety** - Catch errors at compile time
- **Automatic parallelization** - DAG-based execution
- **Built-in HTTP API** - Instant REST endpoints

## Key Capabilities for LLMs

### 1. Type-Safe Module Composition
```scala
// Define a module in Scala
case class TextInput(text: String)
case class TextOutput(result: String)

val uppercase = ModuleBuilder
  .metadata("Uppercase", "Converts text to uppercase", 1, 0)
  .implementationPure[TextInput, TextOutput] { input =>
    TextOutput(input.text.toUpperCase)
  }
  .build
```

```constellation
# Compose in constellation-lang
in text: String
result = Uppercase(text)
out result
```

### 2. Automatic Parallelization
```constellation
# These run in parallel (no dependencies)
a = ProcessA(input)
b = ProcessB(input)
c = ProcessC(input)

# This waits for all three
combined = Merge(a, b, c)
out combined
```

### 3. Built-In Resilience
```constellation
# Add retry, timeout, caching declaratively
result = ExpensiveAPI(input) with {
  retry: 3,
  timeout: 10s,
  cache: 1h
}
```

### 4. HTTP API Out-of-the-Box
```bash
# Start server
make server

# Execute pipeline
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{"source": "in x: Int\nresult = Double(x)\nout result", "inputs": {"x": 42}}'
```

### 5. Embedded in Scala
```scala
// Compile and execute programmatically
for {
  constellation <- Constellation.create[IO]
  compiler      <- DagCompiler.create[IO](constellation)
  _             <- registerModules(constellation)
  dag           <- compiler.compile(source)
  result        <- dag.execute(inputs)
} yield result
```

## Core Concepts (Quick Version)

See [Key Concepts](./key-concepts.md) for full glossary.

### Module
A **reusable processing unit** with typed inputs/outputs.
- Defined in Scala using `ModuleBuilder`
- Registered in `Constellation` instance
- Called from `.cst` files

### Pipeline
A **DAG of modules** defined in constellation-lang.
- Input declarations: `in x: String`
- Module calls: `result = Uppercase(x)`
- Output declarations: `out result`

### CType / CValue
**Type system primitives:**
- `CType` - Type at compile time (e.g., `CString`, `CInt`)
- `CValue` - Value at runtime (e.g., `CString("text")`, `CInt(42)`)

### DAG (Directed Acyclic Graph)
**Execution plan** compiled from pipeline:
- Nodes = module invocations
- Edges = data dependencies
- Layers = parallel execution batches

### Hot vs Cold Execution
- **Hot:** Precompiled DAG, fast execution (HTTP API)
- **Cold:** Compile on-demand (embedded API, development)

## First Topics to Read

### Essential Reading (30 minutes)
1. **[Key Concepts](./key-concepts.md)** - Terminology glossary (10 min)
2. **[Type System](./foundations/type-system.md)** - Core mental model (15 min)
3. **[Module Development](./patterns/module-development.md)** - Create your first module (20 min)

### Task-Oriented Reading
Pick based on your immediate goal:

| Goal | Read This | Time |
|------|-----------|------|
| **Use the CLI** | [CLI Reference](./cli-reference.md) | 15 min |
| **Write pipelines** | [Pipeline Lifecycle](./foundations/pipeline-lifecycle.md) | 20 min |
| **Understand execution** | [DAG Execution](./foundations/dag-execution.md) | 15 min |
| **Add resilience** | [Resilience Patterns](./patterns/resilience.md) | 25 min |
| **Deploy HTTP API** | [HTTP API Reference](./reference/http-api.md) | 20 min |
| **Embed in Scala** | [Embedded API](./integration/embedded-api.md) | 30 min |

## Quick Example Walkthrough

### Step 1: Define Modules (Scala)
```scala
// modules/example-app/src/main/scala/io/constellation/example/modules/TextModules.scala
package io.constellation.example.modules

import cats.effect.IO
import io.constellation.ModuleBuilder
import io.constellation.TypeSystem._

object TextModules {
  // Uppercase module
  case class TextInput(text: String)
  case class TextOutput(result: String)

  val uppercase = ModuleBuilder
    .metadata("Uppercase", "Converts text to uppercase", 1, 0)
    .implementationPure[TextInput, TextOutput] { input =>
      TextOutput(input.text.toUpperCase)
    }
    .build

  // Trim module
  val trim = ModuleBuilder
    .metadata("Trim", "Removes leading/trailing whitespace", 1, 0)
    .implementationPure[TextInput, TextOutput] { input =>
      TextOutput(input.text.trim)
    }
    .build

  // Register all
  def register(constellation: Constellation[IO]): IO[Unit] =
    List(uppercase, trim).traverse(constellation.setModule).void
}
```

### Step 2: Register Modules
```scala
// modules/example-app/src/main/scala/io/constellation/example/ExampleLib.scala
import io.constellation.example.modules.TextModules

object ExampleLib {
  def register(constellation: Constellation[IO]): IO[Unit] =
    TextModules.register(constellation)
}
```

### Step 3: Write Pipeline (.cst)
```constellation
# example.cst
in text: String

# Process text
trimmed = Trim(text)
result = Uppercase(trimmed)

out result
```

### Step 4: Execute via CLI
```bash
# Compile and validate
cst-cli compile example.cst

# Execute with inputs
cst-cli execute example.cst --input text="  hello world  "
# Output: {"result": "HELLO WORLD"}
```

### Step 5: Execute via HTTP
```bash
# Start server
make server

# Execute pipeline
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in text: String\ntrimmed = Trim(text)\nresult = Uppercase(trimmed)\nout result",
    "inputs": {"text": "  hello world  "}
  }'
```

## Development Workflow

### Local Development
```bash
# Start full dev environment (server + dashboard)
make dev

# Or just server with hot-reload
make server-rerun

# Run tests
make test

# Compile everything
make compile
```

### CLI Development
```bash
# Test CLI locally
make test-cli

# Build standalone JAR
make cli-assembly

# Run standalone CLI
java -jar target/cst-cli.jar --help
```

### Docker Deployment
```bash
# Build Docker image
make docker-build

# Run in Docker
make docker-run

# Or with Docker Compose
docker-compose up
```

## Common Patterns

### Pure Computation
```scala
.implementationPure[Input, Output] { input =>
  Output(compute(input))
}
```

### Side Effects
```scala
.implementation[Input, Output] { input =>
  IO {
    // Perform side effect
    Output(result)
  }
}
```

### Error Handling
```scala
.implementation[Input, Output] { input =>
  IO.fromEither(
    validateInput(input).map(compute)
  )
}
```

### Async Operations
```scala
.implementation[Input, Output] { input =>
  for {
    data <- fetchFromAPI(input.url)
    processed <- processData(data)
  } yield Output(processed)
}
```

## Where to Go Next

### By Role

#### **Module Developer**
1. [Module Development Patterns](./patterns/module-development.md)
2. [Type System Foundations](./foundations/type-system.md)
3. [Module Options Reference](./reference/module-options.md)

#### **Pipeline Author**
1. [Pipeline Lifecycle](./foundations/pipeline-lifecycle.md)
2. [DAG Composition Patterns](./patterns/dag-composition.md)
3. [Type Syntax Reference](./reference/type-syntax.md)

#### **Platform Engineer**
1. [Execution Modes](./foundations/execution-modes.md)
2. [HTTP API Reference](./reference/http-api.md)
3. [Resilience Patterns](./patterns/resilience.md)

### By Learning Style

#### **Top-Down (Concepts First)**
1. [Pipeline Lifecycle](./foundations/pipeline-lifecycle.md)
2. [DAG Execution](./foundations/dag-execution.md)
3. [Execution Modes](./foundations/execution-modes.md)

#### **Bottom-Up (Code First)**
1. [Module Development](./patterns/module-development.md)
2. [DAG Composition](./patterns/dag-composition.md)
3. [Resilience Patterns](./patterns/resilience.md)

#### **Reference-Driven**
1. [Type Syntax](./reference/type-syntax.md)
2. [Module Options](./reference/module-options.md)
3. [Error Codes](./reference/error-codes.md)

## Key Files in Codebase

See [Project Structure](./project-structure.md) for complete navigation guide.

**Most Important:**
- `modules/core/.../TypeSystem.scala` - Type system primitives
- `modules/runtime/.../ModuleBuilder.scala` - Module definition API
- `modules/lang-compiler/.../DagCompiler.scala` - Pipeline compilation
- `modules/http-api/.../ConstellationServer.scala` - HTTP server

## Troubleshooting

### Common Issues

#### Module Not Found
```
Error: Module 'Uppercase' not found
```
**Fix:** Ensure module is registered in `ExampleLib.scala` and name matches exactly (case-sensitive).

#### Type Mismatch
```
Error: Expected CString, got CInt
```
**Fix:** Check input types in `.cst` file match module signature. See [Type System](./foundations/type-system.md).

#### Circular Dependency
```
Error: Circular dependency detected
```
**Fix:** Pipeline must be a DAG (no cycles). Check variable dependencies.

#### Server Won't Start
```
Error: Address already in use
```
**Fix:** Port 8080 is taken. Set `CONSTELLATION_PORT=8081` or kill existing process.

### Getting Help
1. Check [Error Handling](./patterns/error-handling.md)
2. Search [GitHub Issues](https://github.com/VledicFranco/constellation-engine/issues)
3. Read [CLAUDE.md](https://github.com/VledicFranco/constellation-engine/blob/master/CLAUDE.md)

## Example Projects

### Minimal Example
```scala
// MinimalExample.scala
import cats.effect.{IO, IOApp}
import io.constellation._

object MinimalExample extends IOApp.Simple {
  def run: IO[Unit] = for {
    constellation <- Constellation.create[IO]
    compiler      <- DagCompiler.create[IO](constellation)

    // Register simple module
    _ <- {
      val double = ModuleBuilder
        .metadata("Double", "Doubles a number", 1, 0)
        .implementationPure[Map[String, CValue], Map[String, CValue]] { input =>
          val x = input("x").asInstanceOf[CInt].value
          Map("result" -> CInt(x * 2))
        }
        .build
      constellation.setModule(double)
    }

    // Compile pipeline
    source = "in x: Int\nresult = Double(x)\nout result"
    dag <- compiler.compile(source)

    // Execute
    inputs = Map("x" -> CInt(21))
    result <- dag.execute(inputs)

    _ <- IO.println(s"Result: ${result("result")}")
  } yield ()
}
```

### HTTP Server Example
```scala
// ServerExample.scala
import cats.effect.{IO, IOApp}
import io.constellation._
import io.constellation.http._

object ServerExample extends IOApp.Simple {
  def run: IO[Unit] = for {
    constellation <- Constellation.create[IO]
    compiler      <- DagCompiler.create[IO](constellation)

    // Register modules
    _ <- ExampleLib.register(constellation)

    // Start HTTP server
    _ <- ConstellationServer.builder(constellation, compiler)
      .withPort(8080)
      .run
  } yield ()
}
```

## Summary

You now know:
- ✅ What Constellation is and why it exists
- ✅ Core concepts (Module, Pipeline, DAG, CType/CValue)
- ✅ How to define modules in Scala
- ✅ How to write pipelines in constellation-lang
- ✅ How to execute via CLI, HTTP, or embedded API
- ✅ Where to go next based on your role

**Next steps:**
1. Read [Key Concepts](./key-concepts.md) for terminology
2. Explore [Module Development](./patterns/module-development.md) for hands-on guide
3. Check [Cookbook](../cookbook/index.md) for working examples

---

**Ready for concepts?** → [Key Concepts](./key-concepts.md)
