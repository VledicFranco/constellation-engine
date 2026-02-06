---
title: "Technical Architecture"
sidebar_position: 1
description: "How Constellation Engine processes your pipelines"
---

# Architecture

This document explains how Constellation Engine works at a high level, helping you understand what happens when you run your pipelines.

## Overview

Constellation Engine processes your pipelines through four stages:

```
Source (.cst) → Parse → Type Check → Compile to DAG → Execute
```

Each stage catches different errors, so problems are found early before execution.

## How Your Pipeline Runs

### 1. Parsing

Your `.cst` source code is parsed into an abstract syntax tree (AST). **Syntax errors are caught here** - things like missing parentheses, invalid characters, or malformed statements.

Example syntax error:
```
Error at line 3, column 15: Expected ')' but found end of line
```

### 2. Type Checking

The compiler validates all field accesses and type operations. **Type errors appear here** - mismatched types, undefined variables, or invalid field access.

Example type error:
```
Error at line 5: Cannot merge String with Int
```

### 3. DAG Compilation

Your pipeline is converted to a **directed acyclic graph (DAG)** where each node is a module call. The compiler analyzes dependencies and identifies which operations can run in parallel.

```
          ┌─────────┐
          │ Input A │
          └────┬────┘
               │
          ┌────▼────┐
          │ Module1 │
          └────┬────┘
               │
    ┌──────────┼──────────┐
    │                     │
┌───▼───┐           ┌─────▼─────┐
│Module2│           │  Module3  │  ← These run in parallel
└───┬───┘           └─────┬─────┘
    │                     │
    └──────────┬──────────┘
               │
          ┌────▼────┐
          │ Output  │
          └─────────┘
```

### 4. Execution

The runtime executes your DAG on Cats Effect fibers. **Independent branches run in parallel automatically** - you don't need to manage concurrency.

## Key Concepts

| Concept | What It Means |
|---------|---------------|
| DAG | Directed graph of module calls, enabling automatic parallelism |
| Fiber | Lightweight thread for concurrent execution (much cheaper than OS threads) |
| Hot reload | Change `.cst` files without restarting the server |
| Content addressing | Compiled DAGs are cached by source hash for instant reuse |
| Synthetic modules | Language constructs (merge, project, conditional) become real DAG nodes |

## Pipeline Stages at a Glance

| Stage | Input | Output | Errors Caught |
|-------|-------|--------|---------------|
| Parse | Source code | AST | Syntax errors |
| Type Check | AST | Typed AST | Type mismatches, undefined variables |
| IR Generation | Typed AST | Intermediate representation | Dependency analysis |
| DAG Compile | IR | Executable DAG | Invalid module references |
| Execute | DAG + inputs | Results | Runtime failures |

## Extension Points

### Custom Modules

You can add your own processing modules using the ModuleBuilder API:

```scala
val myModule = ModuleBuilder
  .metadata("MyModule", "Description", 1, 0)
  .implementationPure[MyInput, MyOutput] { input =>
    MyOutput(transform(input))
  }
  .build
```

Modules are automatically available in your `.cst` pipelines once registered.

### Backend Integrations

Constellation Engine supports pluggable backends for observability and resilience:

| Backend | Purpose |
|---------|---------|
| MetricsProvider | Export metrics to Prometheus, StatsD, etc. |
| TracerProvider | Distributed tracing with OpenTelemetry |
| ExecutionListener | Event streaming to Kafka, webhooks, etc. |
| CacheBackend | Cache compiled pipelines in Redis, Memcached |
| CircuitBreakerRegistry | Per-module circuit breakers for resilience |

All backends default to no-op implementations with zero overhead.

## Error Messages

Constellation Engine provides detailed error messages with source positions:

```
TypeError at line 12, column 5:
  Cannot access field 'email' on type Record(name: String, age: Int)
  Available fields: name, age
```

## Execution Features

### Cancellation and Timeouts

Pipelines can be cancelled or timed out:

```scala
constellation.run(pipeline, inputs)
  .timeout(30.seconds)
```

### Graceful Shutdown

The lifecycle manager ensures in-flight executions complete before shutdown:

```
Running → Draining → Stopped
```

### Circuit Breakers

Per-module circuit breakers prevent cascading failures when external services are down.

## For Contributors

For implementation details including code structure and internal APIs, see the LLM documentation in the repository's `docs/` directory:

- **Compiler Internals** (`docs/components/compiler/`) - Parser, type checker, IR generation, DAG compilation
- **Runtime Execution** (`docs/components/runtime/`) - Module system, parallel execution, data flow
- **Type System** (`docs/components/core/`) - CType, CValue, type algebra
- **HTTP API** (`docs/components/http-api/`) - Server configuration, middleware, routes
- **SPI Integration** - See [Integrations](/docs/integrations/metrics-provider) for implementing custom backends

The source code is organized by module:

| Module | Path | Purpose |
|--------|------|---------|
| core | `modules/core/` | Type system and specifications |
| runtime | `modules/runtime/` | Module execution and DAG runtime |
| lang-parser | `modules/lang-parser/` | constellation-lang parser |
| lang-compiler | `modules/lang-compiler/` | Type checking and DAG compilation |
| http-api | `modules/http-api/` | HTTP server and WebSocket LSP |

## Next Steps

- [Programmatic API](../api-reference/programmatic-api.md) — Embed Constellation in your Scala application
- [HTTP API Overview](../api-reference/http-api-overview.md) — REST endpoints for pipeline execution
- [Security Model](./security-model.md) — Trust boundaries and HTTP hardening
- [Metrics Provider](../integrations/metrics-provider.md) — Add observability to your pipelines
- [API Stability](./api-stability.md) — Versioning policy and stability guarantees
