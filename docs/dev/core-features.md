# Core Features

Constellation Engine is designed with one overarching philosophy: **minimize time to experiment**. The faster you can iterate on pipeline configurations and service combinations, the faster you can discover what works. Constellation provides the tooling to make this experimentation loop as tight as possible.

## Table of Contents

- [Philosophy: Time to Experiment](#philosophy-time-to-experiment)
- [Type System](#type-system)
- [Module System](#module-system)
- [constellation-lang DSL](#constellation-lang-dsl)
- [Type Algebra](#type-algebra)
- [Higher-Order Functions](#higher-order-functions)
- [Execution Model](#execution-model)
- [Standard Library](#standard-library)
- [Reliability Features](#reliability-features)
- [Extension Points](#extension-points)

---

## Philosophy: Time to Experiment

Traditional pipeline development suffers from long iteration cycles:

1. **Code changes** require rebuilding and redeploying
2. **Type errors** surface at runtime, often in production
3. **Integration testing** requires full pipeline execution
4. **Debugging** requires tracing through imperative code

Constellation addresses these pain points:

| Traditional Approach | Constellation Approach |
|---------------------|----------------------|
| Runtime type errors | Compile-time type checking |
| Manual dependency management | Automatic DAG resolution |
| Sequential execution by default | Parallel execution by default |
| Imperative pipeline code | Declarative pipeline specification |
| Rebuild to change pipelines | Hot-reload pipeline definitions |

---

## Type System

Constellation's type system provides compile-time safety for data flowing through pipelines.

### Runtime Types (`CType`)

The core runtime uses a sealed type hierarchy:

| Type | Description | Example |
|------|-------------|---------|
| `CString` | String values | `"hello"` |
| `CInt` | Integer values (Long) | `42` |
| `CFloat` | Floating-point values | `3.14` |
| `CBoolean` | Boolean values | `true` |
| `CList(T)` | Homogeneous lists | `List<Int>` |
| `CMap(K,V)` | Key-value maps | `Map<String, Int>` |
| `CProduct` | Records with named fields | `{ name: String, age: Int }` |
| `COptional(T)` | Optional values | `Option<String>` |

### Semantic Types (`SemanticType`)

The compiler uses a richer type representation:

| Type | Purpose |
|------|---------|
| `SRecord(fields)` | Typed record with field names and types |
| `SList(element)` | Typed list with element-wise operations for records |
| `SMap(key, value)` | Typed map |
| `SFunction(params, returns)` | Function types for HOFs |
| `SOptional(element)` | Optional wrapper |

### Automatic Type Derivation

Scala 3 Mirrors enable automatic `CType` derivation from case classes:

```scala
case class User(name: String, age: Long)
val userType: CType = deriveType[User]
// CType.CProduct(Map("name" -> CString, "age" -> CInt))
```

See [Type Derivation](type-derivation.md) for details.

---

## Module System

Modules are the building blocks of Constellation pipelines.

### ModuleBuilder API

Create modules declaratively:

```scala
case class TextInput(text: String)
case class TextOutput(result: String, wordCount: Long)

val textProcessor = ModuleBuilder
  .metadata("TextProcessor", "Processes text input", 1, 0)
  .tags("text", "nlp")
  .implementationPure[TextInput, TextOutput] { input =>
    val words = input.text.split("\\s+")
    TextOutput(input.text.toUpperCase, words.length)
  }
  .build
```

### Module Lifecycle

1. **Definition**: Modules are defined as `Module.Uninitialized` templates
2. **Initialization**: Modules receive their position in the DAG and create runnable instances
3. **Execution**: Modules run in parallel, waiting on their inputs
4. **Completion**: Outputs propagate to downstream modules

### Side Effects

For modules with side effects (API calls, database access), use IO-based implementations:

```scala
.implementation[Input, Output] { input =>
  IO {
    // Side-effectful operations
    callExternalApi(input)
  }
}
```

---

## constellation-lang DSL

The domain-specific language provides a clean syntax for pipeline definition.

### Basic Structure

```
# Type definitions
type User = { id: Int, name: String, email: String }

# Inputs
in users: List<User>
in context: { threshold: Int }

# Processing steps
enriched = users + context
filtered = filter(enriched, (u) => gt(u.score, context.threshold))
result = filtered[id, name]

# Outputs
out result
```

### Key Constructs

| Construct | Syntax | Purpose |
|-----------|--------|---------|
| Type definition | `type Name = { ... }` | Define record types |
| Input declaration | `in name: Type` | Declare pipeline inputs |
| Output declaration | `out name` | Declare pipeline outputs |
| Function call | `f(a, b)` | Call a module/function |
| Field access | `record.field` | Access record fields |
| Merge | `a + b` | Combine records |
| Projection | `a[f1, f2]` | Select fields |
| Conditional | `if (c) a else b` | Branching |
| Guard | `x when c` | Optional wrapping |
| Coalesce | `a ?? b` | Fallback values |

---

## Type Algebra

Type algebra enables powerful data transformation without explicit mapping code.

### Merge Semantics (`+`)

Merge combines records, with right-side values winning on conflicts:

```
{ a: Int, b: String } + { b: Int, c: Float }
= { a: Int, b: Int, c: Float }
```

When applied to `List<Record>`:

```
List<{ id: String }> + { userId: Int }
= List<{ id: String, userId: Int }>
```

Context fields are added to **each** list item.

### Projection Semantics (`[fields]`)

Projection selects specific fields:

```
{ a: Int, b: String, c: Float }[a, c]
= { a: Int, c: Float }
```

When applied to `List<Record>`:

```
List<{ id: String, score: Float, extra: String }>[id, score]
= List<{ id: String, score: Float }>
```

---

## Higher-Order Functions

Constellation supports functional programming patterns for list processing.

### Available Functions

| Function | Signature | Description |
|----------|-----------|-------------|
| `filter` | `(List<T>, (T) => Bool) => List<T>` | Keep matching items |
| `map` | `(List<T>, (T) => U) => List<U>` | Transform items |
| `all` | `(List<T>, (T) => Bool) => Bool` | All match predicate |
| `any` | `(List<T>, (T) => Bool) => Bool` | Any matches predicate |

### Lambda Syntax

```
filter(numbers, (x) => gt(x, 0))
map(items, (item) => multiply(item.score, 2))
```

### Implementation

HOFs use `InlineTransform` at runtime rather than traditional modules, enabling efficient in-place transformations.

---

## Execution Model

### Parallel by Default

DAG structure enables automatic parallelization:

```
        A
       / \
      B   C     <- B and C run in parallel
       \ /
        D
```

Any modules without data dependencies run concurrently.

### Data Flow

1. **Inputs** are provided to the runtime
2. **Modules** fire when all inputs are available
3. **Data** propagates through edges as modules complete
4. **Outputs** are collected from terminal data nodes

### Element-wise List Operations

`List<Record>` supports element-wise operations:

- Operations apply to all items in the list
- Merge adds context to each item
- Projection selects fields from each item
- Field access extracts a field from each item

---

## Standard Library

The `lang-stdlib` module provides common operations.

### Math Functions
`add`, `subtract`, `multiply`, `divide`, `max`, `min`

### String Functions
`concat`, `upper`, `lower`, `string-length`

### List Functions
`list-length`, `list-first`, `list-last`, `list-is-empty`

### Boolean Functions
`and`, `or`, `not`

### Comparison Functions
`eq-int`, `eq-string`, `gt`, `lt`, `gte`, `lte`

### Utility Functions
`identity`, `const-*`, `log`

See [Standard Library Reference](../stdlib.md) for complete documentation.

---

## Reliability Features

### Compile-Time Safety

Type checking catches errors before runtime:

- Undefined variable references
- Type mismatches in function calls
- Invalid projections (non-existent fields)
- Incompatible merge operations

### Structured Error Messages

All errors include position information:

```
Error at line 5, column 12: Type mismatch
  Expected: Int
  Found: String
```

### Module Status Tracking

Runtime tracks module execution status:

- `Unfired`: Waiting for inputs
- `Fired`: Successfully completed
- `Timed`: Execution timeout
- `Failed`: Execution error

---

## Lifecycle Management

Constellation provides execution lifecycle control for production deployments.

### Graceful Shutdown

`ConstellationLifecycle` manages draining of in-flight executions:

```scala
import io.constellation.execution.ConstellationLifecycle

for {
  lifecycle <- ConstellationLifecycle.create
  constellation <- ConstellationImpl.builder()
    .withLifecycle(lifecycle)
    .build()
  // On shutdown signal:
  _ <- lifecycle.shutdown(drainTimeout = 30.seconds)
} yield ()
```

State machine: `Running` → `Draining` → `Stopped`

### Cancellable Execution

Pipelines can be cancelled mid-execution:

```scala
// Use IO.timeout for cancellation:
constellation.run(compiled.program, inputs)
  .timeout(30.seconds)
```

Status: `Running` | `Completed` | `Cancelled` | `TimedOut` | `Failed`

### Circuit Breakers

Per-module circuit breakers prevent cascading failures:

```scala
CircuitBreakerConfig(
  failureThreshold  = 5,
  resetDuration     = 30.seconds,
  halfOpenMaxProbes = 1
)
```

State machine: `Closed` → `Open` → `HalfOpen` → `Closed`

### Bounded Scheduler

Priority-based task scheduling with configurable concurrency:

```scala
GlobalScheduler.bounded(
  maxConcurrency    = 16,
  maxQueueSize      = 1000,
  starvationTimeout = 30.seconds
)
```

Priority levels: Critical (100), High (80), Normal (50), Low (20), Background (0).

---

## SPI Hook Points

The Service Provider Interface allows plugging in external infrastructure:

| Trait | Hook Points | Purpose |
|-------|-------------|---------|
| `MetricsProvider` | counter, histogram, gauge | Emit runtime metrics |
| `TracerProvider` | span wrapping | Distributed tracing |
| `ExecutionListener` | 6 lifecycle callbacks | Event publishing, audit |
| `CacheBackend` | get/set/delete | Compilation and result caching |

All configured via `ConstellationBackends`:

```scala
val backends = ConstellationBackends(
  metrics  = myPrometheus,
  tracer   = myOtel,
  listener = myKafka,
  cache    = Some(myRedis)
)
```

Default: all no-op (zero overhead). See [SPI Integration Guides](../integrations/spi/) for implementations.

---

## Extension Points

### Adding Functions

Register custom functions with `FunctionRegistry`:

```scala
registry.register(FunctionSignature(
  name = "my-function",
  params = List("input" -> SemanticType.SString),
  returns = SemanticType.SInt,
  moduleName = "my-function-module"
))
```

### Creating Modules

Use `ModuleBuilder` for type-safe module creation:

```scala
val module = ModuleBuilder
  .metadata("Name", "Description", 1, 0)
  .implementation[Input, Output] { ... }
  .build
```

### Extending the Compiler

New IR node types can be added by:

1. Adding to the `IRNode` sealed trait
2. Handling in `IRGenerator`
3. Handling in `DagCompiler`
4. Creating synthetic modules if needed

---

## Related Documentation

- [Architecture](../architecture.md) - Technical architecture deep dive
- [Getting Started](../getting-started.md) - Tutorial for new users
- [constellation-lang Reference](../constellation-lang/README.md) - Complete language syntax
- [API Guide](../api-guide.md) - Programmatic usage
- [Type Derivation](type-derivation.md) - Scala 3 Mirrors for type derivation
