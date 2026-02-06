# Runtime

> **Path**: `docs/runtime/`
> **Parent**: [docs/](../README.md)

Execution engine, scheduling, and Scala API.

## Contents

| File | Description |
|------|-------------|
| [execution-modes.md](./execution-modes.md) | Hot, cold, and suspended execution |
| [scheduling.md](./scheduling.md) | Priority scheduler, concurrency control |
| [module-builder.md](./module-builder.md) | Scala API for creating modules |
| [type-system.md](./type-system.md) | CType, CValue, type derivation |
| [embedding.md](./embedding.md) | Embedding Constellation in JVM applications |
| [performance.md](./performance.md) | Performance tuning and optimization |

## Overview

The runtime executes compiled DAGs:

1. **Layer-based execution** — Nodes grouped by dependency depth
2. **Parallel within layer** — Independent nodes execute concurrently
3. **Type-safe data flow** — CValue carries type tags for validation

## Execution Flow

```
DagSpec + Inputs + Modules
         │
         ▼
    ┌─────────────────────┐
    │   Layer 0: Inputs   │
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │ Layer 1: [A, B, C]  │ ← parallel
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │   Layer 2: [D]      │
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │  Layer 3: Outputs   │
    └─────────────────────┘
         │
         ▼
    DataSignature { outputs, status }
```

## Key Components

| Component | Purpose |
|-----------|---------|
| `Runtime` | DAG execution engine |
| `Module` | Executable unit (wraps Scala function) |
| `ModuleBuilder` | Fluent API for module creation |
| `GlobalScheduler` | Priority-based task ordering |
| `DeferredPool` | Object pooling for performance |

## Quick Start

```scala
import io.constellation.runtime._
import io.constellation.core._

// Create a module
val uppercase = ModuleBuilder
  .metadata("Uppercase", "Converts to uppercase", 1, 0)
  .implementationPure[TextInput, TextOutput] { input =>
    TextOutput(input.text.toUpperCase)
  }
  .build

// Register with constellation
constellation.setModule(uppercase)
```

See [module-builder.md](./module-builder.md) for details.
