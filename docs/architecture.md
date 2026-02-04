# Architecture

This document describes the technical architecture of Constellation Engine, covering both the core runtime and the constellation-lang compiler.

## System Overview

Constellation Engine is organized into two main subsystems:

```
┌─────────────────────────────────────────────────────────────────┐
│                    constellation-lang                            │
│  ┌─────────┐   ┌─────────────┐   ┌─────────┐   ┌─────────────┐  │
│  │ Parser  │ → │ TypeChecker │ → │   IR    │ → │ DagCompiler │  │
│  └─────────┘   └─────────────┘   └─────────┘   └─────────────┘  │
│       ↓              ↓                ↓              ↓          │
│      AST         TypedAST        IRPipeline   CompilationOutput  │
└─────────────────────────────────────────────────────────────────┘
                                                      │
                                                      ↓
┌─────────────────────────────────────────────────────────────────┐
│                      Core Runtime                                │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────────────┐    │
│  │   DagSpec   │ → │   Runtime   │ → │   Module Execution  │    │
│  └─────────────┘   └─────────────┘   └─────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## Core Runtime

### Type System (`api/TypeSystem.scala`)

The runtime type system provides value representation for DAG execution:

```scala
// Types
sealed trait CType
object CType {
  case object CString extends CType
  case object CInt extends CType
  case object CFloat extends CType
  case object CBoolean extends CType
  case class CList(element: CType) extends CType
  case class CMap(key: CType, value: CType) extends CType
  case class CProduct(fields: Map[String, CType]) extends CType
  case class CUnion(fields: Map[String, CType]) extends CType
}

// Values
sealed trait CValue {
  def ctype: CType
}
```

### DAG Specification (`api/Spec.scala`)

DAGs are specified as a collection of modules and data nodes with edges:

```scala
case class DagSpec(
  metadata: ComponentMetadata,
  modules: Map[UUID, ModuleNodeSpec],    // Processing nodes
  data: Map[UUID, DataNodeSpec],         // Data flow nodes
  inEdges: Set[(UUID, UUID)],            // data → module
  outEdges: Set[(UUID, UUID)]            // module → data
)

case class ModuleNodeSpec(
  metadata: ComponentMetadata,
  consumes: Map[String, CType],          // Input parameter types
  produces: Map[String, CType],          // Output parameter types
  config: ModuleConfig
)

case class DataNodeSpec(
  name: String,
  nicknames: Map[UUID, String],          // moduleId → parameterName
  cType: CType
)
```

### Module System (`api/Runtime.scala`)

Modules are the processing units in the DAG:

```scala
object Module {
  // Uninitialized module (template)
  case class Uninitialized(
    spec: ModuleNodeSpec,
    init: (UUID, DagSpec) => IO[Runnable]
  )

  // Initialized module ready to run
  case class Runnable(
    id: UUID,
    data: MutableDataTable,
    run: Runtime => IO[Unit]
  )
}
```

### Execution Flow

1. **Initialization**: Modules are initialized with their position in the DAG
2. **Data Registration**: Each module registers its input/output data slots
3. **Parallel Execution**: All modules run in parallel, waiting on inputs
4. **Completion**: Data flows through the DAG as modules complete

```scala
// High-level API:
constellation.run(compiled.pipeline, inputs): IO[DataSignature]
// Low-level (internal):
Runtime.run(dagSpec, initData, modules): IO[Runtime.State]
```

## constellation-lang Compiler

### Compilation Pipeline

```
Source → Parser → AST → TypeChecker → TypedAST → IRGenerator → IR → DagCompiler → Result
```

### Phase 1: Parsing (`lang/parser/`)

The parser uses [cats-parse](https://github.com/typelevel/cats-parse) for functional parser combinators.

**Key Design Decisions:**
- Position tracking on all AST nodes for precise error messages
- Backtracking for ambiguous constructs (`outputDecl.backtrack | declaration`)
- Hyphenated identifiers supported for service and module names

```scala
// Position tracking
case class Position(line: Int, column: Int, offset: Int)
case class Located[+A](value: A, pos: Position)

// Parser entry point
def parse(source: String): Either[CompileError.ParseError, Program]
```

### Phase 2: Type Checking (`lang/semantic/`)

Type checking validates the program and produces a typed AST.

**SemanticType** - Internal type representation:

```scala
sealed trait SemanticType
object SemanticType {
  case object SString, SInt, SFloat, SBoolean
  case class SRecord(fields: Map[String, SemanticType])
  case class SCandidates(element: SemanticType)
  case class SList(element: SemanticType)
  case class SMap(key: SemanticType, value: SemanticType)
}
```

**TypeEnvironment** - Tracks types and variables during checking:

```scala
case class TypeEnvironment(
  types: Map[String, SemanticType],      // Type definitions
  variables: Map[String, SemanticType],  // Variable bindings
  functions: FunctionRegistry            // Available functions
)
```

**Type Algebra** - Merge semantics:

| Left Type | Right Type | Result |
|-----------|------------|--------|
| `SRecord(a)` | `SRecord(b)` | `SRecord(a ++ b)` |
| `SCandidates(SRecord(a))` | `SCandidates(SRecord(b))` | `SCandidates(SRecord(a ++ b))` |
| `SCandidates(SRecord(a))` | `SRecord(b)` | `SCandidates(SRecord(a ++ b))` |
| Other | Other | Error |

### Phase 3: IR Generation (`lang/compiler/IR.scala`, `IRGenerator.scala`)

The IR is a flat graph representation suitable for DAG compilation.

```scala
sealed trait IRNode {
  def id: UUID
  def outputType: SemanticType
}

object IRNode {
  case class Input(id: UUID, name: String, outputType: SemanticType)
  case class ModuleCall(id: UUID, moduleName: String, languageName: String,
                        inputs: Map[String, UUID], outputType: SemanticType)
  case class MergeNode(id: UUID, left: UUID, right: UUID, outputType: SemanticType)
  case class ProjectNode(id: UUID, source: UUID, fields: List[String], outputType: SemanticType)
  case class ConditionalNode(id: UUID, condition: UUID, thenBranch: UUID,
                             elseBranch: UUID, outputType: SemanticType)
  case class LiteralNode(id: UUID, value: Any, outputType: SemanticType)
}
```

**IRPipeline** provides dependency analysis and topological sorting:

```scala
case class IRPipeline(
  nodes: Map[UUID, IRNode],
  inputs: List[UUID],
  output: UUID,
  outputType: SemanticType
) {
  def dependencies(nodeId: UUID): Set[UUID]
  def topologicalOrder: List[UUID]
}
```

### Phase 4: DAG Compilation (`lang/compiler/DagCompiler.scala`)

Converts IR to DagSpec with synthetic modules.

**Synthetic Modules** are generated for language constructs:

| Construct | Synthetic Module |
|-----------|-----------------|
| `a + b` | MergeModule - combines records |
| `x[f1, f2]` | ProjectionModule - selects fields |
| `if (c) a else b` | ConditionalModule - branching |

```scala
// Public API (returned by LangCompiler.compile):
final case class CompilationOutput(
  pipeline: LoadedPipeline,
  warnings: List[CompileWarning]
)

// Internal (used by DagCompiler):
case class DagCompileOutput(
  dagSpec: DagSpec,
  syntheticModules: Map[UUID, Module.Uninitialized]
)
```

**Compilation Strategy:**

1. Process IR nodes in topological order
2. Create DataNodeSpec for each node's output
3. Create ModuleNodeSpec for function calls
4. Create synthetic modules for merge/project/conditional
5. Build edges from data dependencies

## Data Flow

### DAG Structure

```
                    ┌──────────────┐
                    │   Input A    │  DataNodeSpec
                    └──────┬───────┘
                           │
                           ↓ inEdge
                    ┌──────────────┐
                    │   Module 1   │  ModuleNodeSpec
                    └──────┬───────┘
                           │
                           ↓ outEdge
                    ┌──────────────┐
                    │   Data X     │  DataNodeSpec
                    └──────┬───────┘
                           │
          ┌────────────────┼────────────────┐
          ↓                                 ↓
   ┌──────────────┐                  ┌──────────────┐
   │   Module 2   │                  │   Module 3   │
   └──────┬───────┘                  └──────┬───────┘
          │                                 │
          ↓                                 ↓
   ┌──────────────┐                  ┌──────────────┐
   │   Data Y     │                  │   Data Z     │
   └──────────────┘                  └──────────────┘
```

### Nicknames

DataNodeSpecs use "nicknames" to map parameter names for different modules:

```scala
DataNodeSpec(
  name = "user_data",
  nicknames = Map(
    module1Id -> "input",    // Module 1 sees this as "input"
    module2Id -> "userData"  // Module 2 sees this as "userData"
  ),
  cType = CType.CProduct(...)
)
```

## Error Handling

### Compile Errors

All errors include position information:

```scala
sealed trait CompileError {
  def message: String
  def position: Option[Position]
  def format: String = position match {
    case Some(pos) => s"Error at $pos: $message"
    case None => s"Error: $message"
  }
}
```

Error types:
- `ParseError` - Syntax errors
- `TypeError` - General type errors
- `UndefinedVariable` - Unknown variable reference
- `UndefinedType` - Unknown type reference
- `UndefinedFunction` - Unknown function call
- `TypeMismatch` - Expected vs actual type mismatch
- `InvalidProjection` - Projecting non-existent field
- `IncompatibleMerge` - Cannot merge these types

### Runtime Errors

Runtime uses Cats Effect's IO for error handling:

```scala
Module.Status {
  case object Unfired
  case class Fired(latency: FiniteDuration, context: Option[Map[String, Json]])
  case class Timed(latency: FiniteDuration)  // Timeout
  case class Failed(error: Throwable)
}
```

## Backend SPI Layer

Constellation Engine provides a Service Provider Interface (SPI) for plugging in external infrastructure. All backends are configured through `ConstellationBackends`:

```scala
final case class ConstellationBackends(
  metrics:         MetricsProvider    = MetricsProvider.noop,
  tracer:          TracerProvider     = TracerProvider.noop,
  listener:        ExecutionListener  = ExecutionListener.noop,
  cache:           Option[CacheBackend] = None,
  circuitBreakers: Option[CircuitBreakerRegistry] = None
)
```

### SPI Traits

```
ConstellationBackends
  ├── MetricsProvider    — counter(), histogram(), gauge()
  ├── TracerProvider     — span[A](name, attrs)(body: IO[A])
  ├── ExecutionListener  — onExecutionStart/Complete, onModuleStart/Complete/Failed
  ├── CacheBackend       — get/set/delete with TTL and stats
  └── CircuitBreakerRegistry — per-module circuit breakers
```

All default to no-op implementations with zero overhead. See the [SPI Integration Guides](integrations/spi/) for implementation examples.

### Wiring

```scala
val backends = ConstellationBackends(
  metrics  = myPrometheusMetrics,
  tracer   = myOtelTracer,
  listener = myKafkaListener,
  cache    = Some(myRedisCache)
)

val constellation = ConstellationImpl.builder()
  .withBackends(backends)
  .build()
```

## Execution Lifecycle

### Cancellable Execution

Pipelines can be cancelled or timed out using `IO.timeout`:

```scala
constellation.run(compiled.pipeline, inputs)
  .timeout(30.seconds)
```

### Lifecycle State Machine

`ConstellationLifecycle` manages graceful shutdown:

```
Running ──(shutdown called)──> Draining ──(all executions complete or timeout)──> Stopped
```

- **Running:** Accepts new executions
- **Draining:** Rejects new executions, waits for in-flight to complete
- **Stopped:** All executions finished

### Circuit Breaker

Per-module circuit breakers prevent cascading failures:

```
Closed ──(threshold failures)──> Open ──(resetDuration)──> HalfOpen
  ▲                                                          │
  └──────────────(probe success)─────────────────────────────┘
```

### Bounded Scheduler

`GlobalScheduler.bounded()` provides priority-based task scheduling with configurable concurrency limits and starvation prevention.

## HTTP Hardening

The HTTP server supports opt-in security middleware:

```
Client Request
  → CORS Middleware      (cross-origin handling)
    → Rate Limit         (per-IP token bucket)
      → Auth Middleware  (API key + role validation)
        → Routes
```

| Feature | Config | Default |
|---------|--------|---------|
| Authentication | `AuthConfig` | Disabled |
| CORS | `CorsConfig` | Disabled |
| Rate Limiting | `RateLimitConfig` | Disabled |
| Health Checks | `HealthCheckConfig` | Basic only |

All features are opt-in via `ConstellationServer.builder()` methods. When not configured, they add zero overhead.

## Extension Points

### Adding New Functions

Register with FunctionRegistry:

```scala
val registry = FunctionRegistry.empty
registry.register(FunctionSignature(
  name = "my-function",
  params = List("input" -> SemanticType.SString),
  returns = SemanticType.SInt,
  moduleName = "my-function-module"
))
```

### Creating Custom Modules

Use ModuleBuilder:

```scala
val module = ModuleBuilder
  .metadata("my-module", "Description", 1, 0)
  .implementation[MyInput, MyOutput] { input =>
    IO.pure(MyOutput(result))
  }
  .build
```

### Adding New IR Node Types

1. Add case class to `IRNode` sealed trait
2. Handle in `IRGenerator.generateExpression`
3. Handle in `DagCompiler.processNode`
4. Create synthetic module if needed
