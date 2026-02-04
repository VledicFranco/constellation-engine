# Core Concepts

This page introduces the key concepts behind Constellation Engine. Read this before diving into the tutorial or language reference.

## What Constellation Engine Is

Constellation Engine is a **type-safe pipeline orchestration framework** for Scala 3. It lets you define data-processing pipelines in a declarative DSL (constellation-lang), implement the underlying functions in Scala, and execute them with automatic parallelization, type checking, and resilience.

## Two Layers

Constellation separates **what** your pipeline does from **how** it's implemented:

| Layer | Role | Format |
|-------|------|--------|
| **constellation-lang** | Declarative pipeline definitions | `.cst` files (hot-reloadable) |
| **Scala runtime** | Module implementations, execution engine | Scala 3 + Cats Effect |

Pipeline authors write `.cst` files that reference named modules. Module developers implement those modules in Scala with full access to the JVM ecosystem (HTTP clients, databases, ML libraries).

## Core Concepts

### Module

A **Module** is the basic unit of computation. Each module has:

- A **name** (PascalCase, e.g., `FetchCustomer`)
- **Input parameters** with typed fields
- **Output fields** with known types
- An **implementation** (pure function or IO effect)

```scala
val fetchCustomer = ModuleBuilder
  .metadata("FetchCustomer", "Fetch customer data", 1, 0)
  .implementation[CustomerInput, CustomerOutput] { input =>
    IO { httpClient.get(s"/customers/${input.customerId}") }
  }
  .build
```

### Pipeline

A **Pipeline** is a directed acyclic graph (DAG) of module calls, declared in constellation-lang:

```constellation
in order: { id: String, customerId: String }

customer = FetchCustomer(order.customerId)
shipping = EstimateShipping(order.id)

out order + customer + shipping
```

The compiler resolves dependencies between module calls and the runtime executes independent branches in parallel.

### DagSpec

A **DagSpec** is the compiled representation of a pipeline. It contains:

- **Module nodes** (`Map[UUID, ModuleNodeSpec]`) -- each module call in the pipeline
- **Data nodes** (`Map[UUID, DataNodeSpec]`) -- each value flowing between modules
- **Edges** -- connections between data nodes and module nodes (inputs/outputs)
- **Declared outputs** -- the pipeline's output variables

### Type System (CType / CValue)

The runtime type system ensures type safety across the entire pipeline:

**CType** (types):
- Primitives: `CString`, `CInt`, `CFloat`, `CBoolean`
- Collections: `CList`, `CMap`
- Structured: `CProduct` (records), `CUnion` (tagged unions)
- Optional: `COptional`

**CValue** (values):
- Every runtime value carries its `CType`, enabling type checking at DAG boundaries
- Automatic derivation maps Scala case classes to/from CType and CValue

## Pipeline Lifecycle

A constellation-lang source file goes through several stages before execution:

```
Source (.cst)
    |
    v
  Parse          -- text -> AST
    |
    v
  Type Check     -- validate types, infer missing types
    |
    v
  IR Generate    -- typed AST -> intermediate representation
    |
    v
  Optimize       -- dead code elimination, constant folding, CSE
    |
    v
  DAG Compile    -- IR -> DagSpec + synthetic modules
    |
    v
  PipelineImage  -- immutable snapshot (structural hash, DagSpec, options)
    |
    v
  LoadedPipeline -- image + runtime module instances
    |
    v
  Execute        -- parallel DAG traversal -> DataSignature (results)
```

Each stage catches different classes of errors: the parser catches syntax errors, the type checker catches field typos and type mismatches, and the DAG compiler validates module availability.

## Content-Addressed Storage

Compiled pipelines are stored using **content addressing**:

- **Structural hash** -- SHA-256 of the canonicalized DagSpec (independent of UUIDs). Two pipelines with identical logic produce the same hash.
- **Syntactic hash** -- SHA-256 of the source text. Used for cache-hit detection: if the source and module registry haven't changed, the existing compiled pipeline is reused.
- **Aliases** -- Human-readable names that point to a structural hash. You can repoint an alias to a different version without changing client code.

The `PipelineStore` trait provides:
- `store(image)` -- save a compiled pipeline, returns structural hash
- `get(hash)` -- retrieve by structural hash
- `alias(name, hash)` -- create or update a named alias
- `resolve(name)` -- look up alias to structural hash

## Execution Modes

### Hot Pipeline (compile + run)

Send source code to the `/run` endpoint. The server compiles and executes in one step:

```bash
curl -X POST http://localhost:8080/run \
  -d '{"source": "in x: Int\nout x", "inputs": {"x": 42}}'
```

### Cold Pipeline (store + execute by reference)

Compile once via `/compile`, then execute by name or hash via `/execute`:

```bash
# Compile and store
curl -X POST http://localhost:8080/compile \
  -d '{"source": "...", "name": "my-pipeline"}'

# Execute by name
curl -X POST http://localhost:8080/execute \
  -d '{"ref": "my-pipeline", "inputs": {"x": 42}}'

# Execute by structural hash
curl -X POST http://localhost:8080/execute \
  -d '{"ref": "sha256:abc123...", "inputs": {"x": 42}}'
```

## Suspend and Resume

Pipelines support **partial execution**. If some inputs are missing, the pipeline suspends instead of failing:

1. The runtime executes all modules whose inputs are available
2. Modules waiting on missing inputs are left in `Unfired` state
3. A `SuspendedExecution` snapshot is saved with the execution ID
4. The caller can later resume with the missing inputs via `POST /executions/{id}/resume`

This enables **incremental execution** -- provide inputs as they become available, and the pipeline picks up where it left off.

## Resilience

Modules support declarative resilience options via the `with` clause in constellation-lang:

```constellation
result = SlowService(data) with {
  timeout: 5000,
  retry: 3,
  backoff: "exponential",
  fallback: DefaultValue(data),
  cache: 60000
}
```

Available options:

| Option | Purpose |
|--------|---------|
| `retry` | Max retry count on failure |
| `timeout` | Execution timeout (ms) |
| `delay` | Delay before execution (ms) |
| `backoff` | Retry strategy: `fixed`, `linear`, `exponential` |
| `fallback` | Alternative module call on failure |
| `cache` | Cache TTL (ms) |
| `cache_backend` | Named cache backend (e.g., `redis`) |
| `throttle` | Rate limiting (count per time window) |
| `concurrency` | Max concurrent instances |
| `on_error` | Error strategy: `propagate`, `skip`, `log`, `wrap` |
| `lazy` | Defer execution until result is needed |
| `priority` | Execution priority (0-100) |

## HTTP API

The server exposes several endpoint groups:

| Group | Endpoints | Purpose |
|-------|-----------|---------|
| **Health** | `/health`, `/health/live`, `/health/ready` | Liveness and readiness probes |
| **Compile & Run** | `/compile`, `/run` | Compile and/or execute pipelines |
| **Pipeline Management** | `/pipelines`, `/pipelines/{ref}`, `/execute` | Store, list, and execute pipelines |
| **Suspension** | `/executions`, `/executions/{id}/resume` | Manage suspended executions |
| **Versioning** | `/pipelines/{name}/reload`, `/pipelines/{name}/versions` | Hot-reload and version history |
| **Canary** | `/pipelines/{name}/canary` | Canary deployments with traffic splitting |
| **Modules** | `/modules` | List registered modules |
| **Metrics** | `/metrics` | Cache stats and execution counts |
| **LSP** | `/lsp` (WebSocket) | Language Server Protocol for IDEs |

See `docs/README.md` for full endpoint documentation links.

## Tooling

- **VSCode Extension** -- Syntax highlighting, autocomplete, inline errors, hover types, DAG visualization, and one-click execution via the Language Server Protocol.
- **Web Dashboard** -- Browser-based UI for browsing files, running pipelines, viewing DAG graphs, and inspecting execution history. Served by the HTTP server.
- **LSP** -- Standard Language Server Protocol support. Works with any LSP-compatible editor.

## Next Steps

- Follow the [Getting Started Tutorial](getting-started.md) to build your first pipeline
- Read the [constellation-lang Guide](constellation-lang/README.md) to learn the DSL syntax
- Browse the [Pipeline Examples](examples/README.md) for common patterns
- Check the [API Guide](api-guide.md) for programmatic usage
- See the [Embedding Guide](embedding-guide.md) to integrate Constellation into your JVM application
