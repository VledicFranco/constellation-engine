# Architecture

> **Path**: `docs/overview/architecture.md`
> **Parent**: [overview/](./README.md)

## Module Dependency Graph

```
constellation-core
       │
       ▼
constellation-runtime ◄─── constellation-lang-ast
                                    │
                                    ▼
                          constellation-lang-parser
                                    │
                                    ▼
                          constellation-lang-compiler
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
          constellation-lang-stdlib  constellation-lang-lsp
                    │               │
                    └───────┬───────┘
                            ▼
                   constellation-http-api
                            │
                            ▼
                      example-app
```

## Module Responsibilities

| Module | Responsibility |
|--------|----------------|
| `core` | Type system (CType, CValue), DAG specifications |
| `runtime` | Execution engine, ModuleBuilder, scheduling, pooling |
| `lang-ast` | AST node definitions for parser output |
| `lang-parser` | cats-parse based DSL parser |
| `lang-compiler` | Type checking, IR generation, DAG compilation |
| `lang-stdlib` | Standard library functions (math, string, list, etc.) |
| `lang-lsp` | Language Server Protocol for IDE integration |
| `http-api` | REST endpoints, WebSocket LSP, dashboard |

## Data Flow

```
.cst source
    │
    ▼ (Parser)
   AST
    │
    ▼ (TypeChecker)
   Typed AST
    │
    ▼ (IRGenerator)
   IR (intermediate representation)
    │
    ▼ (DagCompiler)
   DagSpec
    │
    ▼ (Runtime)
   Execution with registered Modules
    │
    ▼
   DataSignature (outputs)
```

## Key Abstractions

| Abstraction | Location | Purpose |
|-------------|----------|---------|
| `CType` | core | Runtime type representation |
| `CValue` | core | Runtime value with type tag |
| `DagSpec` | core | DAG structure specification |
| `Module` | runtime | Executable unit (Scala function) |
| `ModuleBuilder` | runtime | Fluent API for module creation |
| `ConstellationServer` | http-api | HTTP server builder |

## Storage Architecture

```
PipelineStore (SPI)
    │
    ├─► InMemoryPipelineStore (default)
    │
    └─► Custom implementations (PostgreSQL, Redis, etc.)

Storage model:
    PipelineImage {
      structuralHash: String   # content-addressed (deterministic)
      syntacticHash: String    # source text hash
      dagSpec: DagSpec
      source: String
      createdAt: Instant
    }
```

## Execution Architecture

```
Request → ConstellationRoutes
              │
              ▼
         Compiler (cached by source hash)
              │
              ▼
         Runtime.execute(dagSpec, inputs, modules)
              │
              ▼
         ┌────────────────────────────────┐
         │  Layer-by-layer execution      │
         │  (parallel within each layer)  │
         │                                │
         │  Layer 0: [input nodes]        │
         │  Layer 1: [module A, B, C]     │  ← concurrent
         │  Layer 2: [module D]           │
         │  Layer 3: [output nodes]       │
         └────────────────────────────────┘
              │
              ▼
         DataSignature { outputs, computedNodes, status }
```
