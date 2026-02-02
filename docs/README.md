<p align="center">
  <img src="../brand/logo-icon.svg" alt="Constellation Engine" width="64" height="64">
</p>

# Constellation Engine Documentation

Constellation Engine is a Scala 3 framework for building and executing ML pipeline DAGs (Directed Acyclic Graphs). It provides a type-safe, functional approach to orchestrating machine learning models and data transformations.

## Table of Contents

- [Overview](#overview)
- [Getting Started](#getting-started)
- [Documentation](#documentation)
- [Quick Example](#quick-example)

> **New to Constellation?** Start with the [Getting Started Tutorial](getting-started.md) - a comprehensive 2-hour guide covering installation, basic pipelines, custom modules, and real-world examples.

## Overview

Constellation Engine consists of two main components:

1. **Core Runtime** - A typed DAG execution engine that manages module execution, data flow, and error handling
2. **constellation-lang** - A domain-specific language for declaratively defining ML pipelines

### Key Features

- **Type-safe pipelines**: Full compile-time type checking for data flow
- **Declarative DSL**: Define complex pipelines in a readable, maintainable syntax
- **Modular architecture**: Compose reusable modules into larger pipelines
- **Parallel execution**: Automatic parallelization of independent operations
- **Batched ML operations**: First-class support for `Candidates<T>` batching

## Getting Started

### Prerequisites

- Scala 3.3.x
- SBT 1.9.x
- JDK 17+

### Installation

Add to your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "org.typelevel" %% "cats-core" % "2.10.0",
  "org.typelevel" %% "cats-effect" % "3.5.2",
  "org.typelevel" %% "cats-parse" % "1.0.0",
  "io.circe" %% "circe-core" % "0.14.6"
)
```

### Build and Test

```bash
make compile   # Compile the project
make test      # Run all tests
make dev       # Start development server
```

## Documentation

### Getting Started

| Document | Description |
|----------|-------------|
| [Getting Started Tutorial](getting-started.md) | Step-by-step tutorial for new users |
| [Embedding Guide](embedding-guide.md) | Embed Constellation in your JVM application |
| [Pipeline Examples](examples/README.md) | Real-world pipeline examples with explanations |

### Language & API

| Document | Description |
|----------|-------------|
| [constellation-lang Guide](constellation-lang/README.md) | Complete language reference for the DSL |
| [Standard Library](stdlib.md) | Built-in functions for common operations |
| [API Guide](api-guide.md) | Programmatic API usage and module creation |
| [Error Reference](error-reference.md) | Structured error catalog with codes and resolutions |

### Architecture & Security

| Document | Description |
|----------|-------------|
| [Architecture](architecture.md) | Technical architecture and design decisions |
| [Security Model](security.md) | Trust boundaries, sandboxing, and HTTP hardening |
| [Performance Tuning](performance-tuning.md) | Scheduler, circuit breakers, caching, JVM tuning |

### Tooling

| Document | Description |
|----------|-------------|
| [Dashboard & Tooling](tooling.md) | Web dashboard, VSCode extension, Playwright dev loop |
| [LSP Integration](LSP_INTEGRATION.md) | Language Server Protocol setup and features |
| [Troubleshooting](troubleshooting.md) | Common issues and solutions |

### SPI Integration Guides

Plug in your own metrics, tracing, event publishing, caching, and storage:

| Document | Description |
|----------|-------------|
| [MetricsProvider](integrations/spi/metrics-provider.md) | Prometheus, Datadog integration |
| [TracerProvider](integrations/spi/tracer-provider.md) | OpenTelemetry, Jaeger integration |
| [ExecutionListener](integrations/spi/execution-listener.md) | Kafka events, database audit logs |
| [CacheBackend](integrations/spi/cache-backend.md) | Redis, Caffeine integration |
| [ExecutionStorage](integrations/spi/execution-storage.md) | PostgreSQL, SQLite storage |

### HTTP API

| Document | Description |
|----------|-------------|
| [OpenAPI Specification](api/openapi.yaml) | REST API specification (OpenAPI 3.0) |
| [LSP WebSocket Protocol](api/lsp-websocket.md) | WebSocket protocol for IDE integration |

### Migration

| Document | Description |
|----------|-------------|
| [v0.3.0 Migration Guide](migration/v0.3.0.md) | Upgrading from v0.2.x to v0.3.0 |

### Development

| Document | Description |
|----------|-------------|
| [Global Priority Scheduler](dev/global-scheduler.md) | Bounded concurrency with priority-based scheduling |
| [MCP Server](dev/constellation-repo-dev-mcp.md) | MCP server for multi-agent development workflows |

## Quick Example

### Using constellation-lang

```
# Define a type for your data
type Communication = {
  id: String,
  content: String,
  channel: String
}

# Declare inputs
in communications: Candidates<Communication>
in userId: Int

# Process through ML models
embeddings = embed-model(communications)
scores = ranking-model(embeddings + communications, userId)

# Select fields and merge results
result = communications[id, channel] + scores[score]

# Declare output
out result
```

### Compiling and Running

```scala
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.*

// Create compiler with registered functions
val compiler = LangCompiler.builder
  .withFunction(FunctionSignature(
    name = "embed-model",
    params = List("input" -> SemanticType.SCandidates(inputType)),
    returns = embeddingsType,
    moduleName = "embed-model"
  ))
  .build

// Compile the program
val result = compiler.compile(source, "my-pipeline")

result match {
  case Right(compiled) =>
    // compiled.dagSpec contains the DAG specification
    // compiled.syntheticModules contains generated modules
  case Left(errors) =>
    errors.foreach(e => println(e.format))
}
```

## Project Structure

```
modules/
├── core/                   # Core type system
│   └── src/.../TypeSystem.scala
├── runtime/                # Runtime API
│   └── src/.../ModuleBuilder.scala, Runtime.scala, Spec.scala
├── lang-ast/               # AST definitions
├── lang-parser/            # cats-parse based parser
│   └── src/.../ConstellationParser.scala
├── lang-compiler/          # Type checking, IR, DAG compilation
│   └── src/.../TypeChecker.scala, IRGenerator.scala, DagCompiler.scala
├── lang-stdlib/            # Standard library functions
│   └── src/.../StdLib.scala
├── lang-lsp/               # Language Server Protocol
│   └── src/.../ConstellationLanguageServer.scala
├── http-api/               # HTTP server and routes
│   └── src/.../ConstellationServer.scala
└── example-app/            # Example application
    └── src/.../ExampleLib.scala
```
