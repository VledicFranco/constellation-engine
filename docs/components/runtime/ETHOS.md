# Runtime Ethos

> Normative constraints for the execution engine.

---

## Identity

- **IS:** Execution engine for compiled DAGs with layer-based parallelism
- **IS NOT:** A compiler, parser, HTTP server, or storage system

---

## Semantic Mapping

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `Constellation` | Entry point for registering modules and executing pipelines |
| `ConstellationImpl` | Default implementation of the Constellation trait |
| `ModuleBuilder` | Fluent API for creating typed modules from Scala functions |
| `Module.Uninitialized` | Module definition before type instantiation |
| `Module.Initialized` | Module ready for execution with concrete input/output types |
| `Runtime` | DAG execution engine that resolves dependencies and runs layers |
| `CancellableExecution` | Handle for an in-flight execution that can be cancelled |
| `ExecutionStatus` | State machine: Pending, Running, Suspended, Completed, Cancelled, Failed |
| `GlobalScheduler` | Priority-based task scheduler with concurrency control |
| `BoundedGlobalScheduler` | Scheduler with maximum concurrency and starvation prevention |
| `CircuitBreaker` | Failure isolation: tracks errors and blocks calls when threshold exceeded |
| `BackoffStrategy` | Retry delay calculation: Constant, Linear, Exponential, Fibonacci |
| `ConstellationLifecycle` | Graceful shutdown: drain in-flight executions before stopping |
| `ModuleRegistry` | Thread-safe registry mapping module names to implementations |
| `PipelineStore` | Cache for compiled pipelines (source hash to DagSpec) |
| `SuspensionStore` | Persistence for suspended executions awaiting additional inputs |
| `CacheBackend` | SPI for pluggable cache storage (in-memory, Redis, Memcached) |

For complete type signatures, see:
- [io.constellation](/docs/generated/io.constellation.md)
- [io.constellation.execution](/docs/generated/io.constellation.execution.md)
- [io.constellation.impl](/docs/generated/io.constellation.impl.md)

---

## Invariants

### 1. Modules are pure functions

Module implementations must not have side effects beyond their declared IO type. Side effects (logging, metrics, external calls) are wrapped in `IO`.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/runtime/src/main/scala/io/constellation/ModuleBuilder.scala#implementationPure` |
| Test | `modules/runtime/src/test/scala/io/constellation/ModuleBuilderTest.scala#implementationPure` |

### 2. Layer execution is parallel within layers

Modules in the same DAG layer (no dependencies between them) execute concurrently. The runtime never serializes independent work.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/runtime/src/main/scala/io/constellation/Runtime.scala#parTraverse` |
| Test | `modules/runtime/src/test/scala/io/constellation/SteppedExecutionTest.scala#group parallel modules in same batch` |

### 3. Cancellation is cooperative

`CancellableExecution.cancel` requests cancellation but does not forcibly terminate fibers. Modules must check for cancellation or use `IO.cancelable`.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/runtime/src/main/scala/io/constellation/execution/CancellableExecution.scala#def cancel` |
| Test | `modules/runtime/src/test/scala/io/constellation/execution/CancellableExecutionTest.scala#cancel a slow execution` |

### 4. Graceful shutdown drains before stopping

`ConstellationLifecycle.shutdown` transitions to Draining, waits for in-flight executions (up to timeout), then transitions to Stopped.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/runtime/src/main/scala/io/constellation/execution/ConstellationLifecycle.scala#def shutdown` |
| Test | `modules/runtime/src/test/scala/io/constellation/execution/ConstellationLifecycleTest.scala#drain in-flight executions` |

### 5. Circuit breaker state transitions are atomic

Circuit breaker state (Closed, Open, HalfOpen) transitions use atomic references. Concurrent failures cannot corrupt state.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/runtime/src/main/scala/io/constellation/execution/CircuitBreaker.scala#stateRef` |
| Test | `modules/runtime/src/test/scala/io/constellation/execution/CircuitBreakerTest.scala#concurrent getOrCreate` |

---

## Principles (Prioritized)

1. **Correctness over performance** — Never sacrifice type safety or execution semantics for speed
2. **Explicit over implicit** — Module signatures declare all inputs/outputs; no hidden state
3. **Composable over monolithic** — Small executors (retry, cache, timeout) compose into pipelines
4. **Observable by default** — Execution state is inspectable at every layer

---

## Decision Heuristics

- When adding a new resilience option, implement as a composable executor that wraps module invocation
- When uncertain about concurrency, prefer `Ref` and `Deferred` over locks
- When a module needs external state, require it as an input rather than closing over it
- When optimizing, benchmark first and preserve semantics

---

## Out of Scope

- Compilation and parsing (see [compiler/](../compiler/))
- HTTP request handling (see [http-api/](../http-api/))
- Type system definitions (see [core/](../core/))
- Standard library functions (see [stdlib/](../stdlib/))

---

## Implements Features

| Feature | Artifacts |
|---------|-----------|
| [Resilience](../../features/resilience/) | RetryExecutor, CacheExecutor, TimeoutExecutor, BackoffStrategy, CircuitBreaker |
| [Parallelization](../../features/parallelization/) | Runtime, LayerExecutor, GlobalScheduler, BoundedGlobalScheduler |
| [Execution](../../features/execution/) | CancellableExecution, ExecutionStatus, ConstellationLifecycle, SuspensionStore |
| [Extensibility](../../features/extensibility/) | CacheBackend, MetricsProvider, ExecutionListener, TracerProvider |
