# RFC-013: Production Readiness Roadmap

**Status:** Draft
**Priority:** P0 (Product Foundation)
**Author:** Human + Claude
**Created:** 2026-01-29

---

## Summary

This RFC defines the gaps between Constellation Engine's current state and a production-ready **embeddable library** capable of running pipelines at scale. It introduces a unified Backend SPI for extensibility, covers execution lifecycle hardening, observability hooks, and robustness testing. The HTTP server and dashboard are treated as an optional module, not the core product.

---

## Product Model

**Constellation Engine is an embeddable library.** Users add it as a dependency to their JVM application and use the programmatic API to compile and execute pipelines.

| Layer | Role | Dependency model |
|-------|------|-----------------|
| `constellation-core` | Type system, values, DAG spec | Required |
| `constellation-runtime` | Execution engine, scheduler, pooling | Required |
| `constellation-lang` | Parser, compiler, stdlib, LSP | Required for `.cst` support |
| `constellation-http` | HTTP server, dashboard, REST API | **Optional module** |

The HTTP server + dashboard is an opt-in module that demonstrates how to embed the library. Users who embed Constellation into their own app will use the library API directly and provide their own HTTP layer, auth, deployment, etc.

This distinction shapes every decision in this RFC: the library provides **primitives and extension points**, not baked-in infrastructure.

---

## Motivation

The runtime core is mature: parallel DAG execution, priority scheduling, retry/backoff/timeout, error strategies, rate limiting, concurrency limiting, object pooling, compilation caching. The compiler, parser, type system, and LSP are well-tested (90+ test files, CI with coverage enforcement, performance regression baselines).

**The engine works. What's missing is:**

1. **Execution lifecycle** — can't cancel DAGs, no global timeout, no circuit breaker, no graceful shutdown
2. **Extensibility** — no unified way for embedders to plug in their own metrics, storage, tracing, or event handling
3. **Observability hooks** — no standard interfaces for metrics, tracing, or structured event reporting
4. **Robustness proof** — no stress tests, no property-based testing, no fuzzing

### What This RFC Does NOT Cover

- Dashboard UI features
- VSCode extension improvements
- New constellation-lang syntax or modules
- Performance optimizations (already have benchmarks and regression tests)
- Multi-instance coordination / distributed scheduling
- Streaming / incremental execution (fundamental architecture change, separate RFC)

---

## Current State Assessment

### What's Production-Grade Today

| Component | Status | Evidence |
|-----------|--------|----------|
| DAG execution engine | Strong | Parallel modules, deferred sync, inline transforms, 4 execution variants |
| Global scheduler | Strong | Bounded concurrency, priority queue, starvation prevention, metrics |
| Error handling | Strong | 4 strategies (propagate/skip/log/wrap), retry with backoff, per-module timeout, fallback values |
| Rate & concurrency limiting | Strong | Token bucket + semaphore, per-module configuration |
| Object pooling | Strong | DeferredPool + RuntimeStatePool, 90% allocation reduction |
| Compilation caching | Strong | LRU eviction, hit rate tracking, cache stats endpoint |
| Type system & compiler | Strong | 75-80% test coverage, regression suite, error codes |
| Test infrastructure | Strong | 90 test files, 4 CI workflows, Playwright E2E, cross-OS extension tests |

### What's Missing

| Category | Gap | Impact |
|----------|-----|--------|
| Execution lifecycle | No cancellation, no global timeout, no circuit breaker, no graceful shutdown | Can't stop runaway pipelines |
| Extensibility | No unified SPI for backends | Embedders can't plug in their own infrastructure |
| Observability | No standard hooks for metrics, tracing, events | Embedders can't monitor the library |
| Robustness | No large DAG stress tests, no property tests, no fuzzing | Scalability unknowns |

---

## Phase 1: Unified Backend SPI

**Goal:** Define the extension surface that embedders use to plug Constellation into their infrastructure.

This phase comes first because it establishes the interfaces that all other phases implement against.

### 1.1 ConstellationBackends

The library defines a single configuration point that bundles all extension points. Embedders provide implementations; the library ships noop/in-memory defaults.

```scala
case class ConstellationBackends(
  metrics:    MetricsProvider    = MetricsProvider.noop,
  tracer:     TracerProvider     = TracerProvider.noop,
  storage:    ExecutionStorage   = InMemoryExecutionStorage(),
  cache:      CacheBackend       = InMemoryCacheBackend(),
  listener:   ExecutionListener  = ExecutionListener.noop
)
```

**Usage by embedders:**

```scala
val backends = ConstellationBackends(
  metrics  = MyPrometheusMetrics(...),
  storage  = MyPostgresStorage(dataSource),
  cache    = MyRedisCache(redisClient),
  listener = MyEventPipeline(kafka)
)

val constellation = Constellation.builder()
  .withBackends(backends)
  .withModules(allModules)
  .build()
```

**Design principles:**
- Every backend has a noop/in-memory default — zero mandatory external dependencies
- Backends are traits with pure IO signatures — testable, composable
- The builder validates configuration at construction time, not at first use

### 1.2 MetricsProvider Trait

The library calls this at key instrumentation points. Embedders implement it to feed their monitoring stack (Prometheus, Datadog, Micrometer, etc.).

```scala
trait MetricsProvider {
  def counter(name: String, tags: Map[String, String] = Map.empty): IO[Unit]
  def histogram(name: String, value: Double, tags: Map[String, String] = Map.empty): IO[Unit]
  def gauge(name: String, value: Double, tags: Map[String, String] = Map.empty): IO[Unit]
}

object MetricsProvider {
  val noop: MetricsProvider = new MetricsProvider {
    def counter(name: String, tags: Map[String, String]): IO[Unit] = IO.unit
    def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO.unit
    def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit] = IO.unit
  }
}
```

**Instrumentation points in the library:**

| Metric | Type | Tags | Where |
|--------|------|------|-------|
| `constellation.execution.total` | Counter | `status` | Runtime |
| `constellation.execution.duration_ms` | Histogram | `dag_name` | Runtime |
| `constellation.module.duration_ms` | Histogram | `module_name` | ModuleExecutor |
| `constellation.compilation.total` | Counter | `cache_hit` | LangCompiler |
| `constellation.compilation.duration_ms` | Histogram | — | LangCompiler |
| `constellation.scheduler.active` | Gauge | — | GlobalScheduler |
| `constellation.scheduler.queued` | Gauge | — | GlobalScheduler |
| `constellation.circuit_breaker.state` | Gauge | `module_name` | CircuitBreaker |

### 1.3 TracerProvider Trait

For distributed tracing. Embedders implement this to create spans (OpenTelemetry, Jaeger, Datadog APM, etc.).

```scala
trait TracerProvider {
  def span[A](name: String, attributes: Map[String, String] = Map.empty)(body: IO[A]): IO[A]
}

object TracerProvider {
  val noop: TracerProvider = new TracerProvider {
    def span[A](name: String, attributes: Map[String, String])(body: IO[A]): IO[A] = body
  }
}
```

**Span tree created by the library:**

```
span: "execute(text-analysis)"
  ├─ span: "module(Trim)"         {module_name=Trim, kind=Operation}
  ├─ span: "module(Lowercase)"    {module_name=Lowercase}
  ├─ span: "module(WordCount)"    {module_name=WordCount}
  └─ span: "module(SplitLines)"   {module_name=SplitLines}
```

### 1.4 ExecutionListener Trait

Lightweight event hooks for custom logging, alerting, or data pipelines. Simpler than tracing — no span management, just callbacks.

```scala
trait ExecutionListener {
  def onExecutionStart(executionId: String, dagName: String): IO[Unit]
  def onModuleStart(executionId: String, moduleId: String, moduleName: String): IO[Unit]
  def onModuleComplete(executionId: String, moduleId: String, moduleName: String, durationMs: Long): IO[Unit]
  def onModuleFailed(executionId: String, moduleId: String, moduleName: String, error: Throwable): IO[Unit]
  def onExecutionComplete(executionId: String, durationMs: Long, status: ExecutionStatus): IO[Unit]
}

object ExecutionListener {
  val noop: ExecutionListener = ...

  /** Combine multiple listeners */
  def composite(listeners: ExecutionListener*): ExecutionListener = ...
}
```

**Execution model:** Fire-and-forget. The runtime forks listener callbacks to background fibers — zero latency impact on pipeline execution. Events may be lost on crash, which is acceptable for logging/alerting/analytics. Audit/checkpoint use cases requiring guaranteed delivery should be addressed with a dedicated middleware API in a future RFC.

**Use cases:**
- Feed execution events to Kafka/event bus
- Build custom dashboards
- Trigger alerts on failure patterns
- Record audit trails

### 1.5 ExecutionStorage & CacheBackend

These traits already exist in the codebase. This phase formalizes them as part of the SPI:

- `ExecutionStorage` — the core library ships only `InMemoryExecutionStorage` as default. Persistent backends (SQLite, Postgres) are the embedder's responsibility.
- `CacheBackend` — already pluggable. Keep `InMemoryCacheBackend` as default.

**Acceptance criteria for Phase 1:**
- [ ] `ConstellationBackends` case class accepted by `Constellation.builder()`
- [ ] All five traits defined with noop defaults
- [ ] Existing code wired to call `MetricsProvider` at instrumentation points
- [ ] Existing code wired to call `ExecutionListener` at lifecycle events
- [ ] `TracerProvider.span()` wraps compilation and module execution
- [ ] Zero overhead when using noop implementations (verified by benchmark)
- [ ] Builder API documented with examples in scaladoc

---

## Phase 2: Execution Lifecycle

**Goal:** Make pipelines stoppable, safe, and resilient to failures.

These are the gaps that will cause incidents. A DAG that can't be cancelled, a process that can't shut down gracefully, and a downstream service that fails repeatedly without tripping a breaker — these are operational hazards.

### 2.1 DAG Execution Cancellation

**Problem:** Once `Runtime.runWithScheduler()` starts, the DAG runs to completion or failure. There is no way to cancel an in-progress execution. `parTraverse` has no early termination.

**Impact:** Cannot implement request cancellation, cannot enforce global timeouts, cannot implement fair resource sharing.

**Proposed Design:**

```scala
trait CancellableExecution {
  def cancel: IO[Unit]
  def result: IO[Runtime.State]
  def status: IO[ExecutionStatus] // Running, Completed, Cancelled, Failed
}

object Runtime {
  def runCancellable(
    dag: DagSpec,
    inputs: Map[String, CValue],
    modules: List[Module.Runnable],
    scheduler: GlobalScheduler,
    backends: ConstellationBackends = ConstellationBackends()
  ): IO[CancellableExecution]
}
```

**Implementation approach:**
- Wrap execution in `Deferred`-based cancellation token
- Pass token to each module's run fiber
- On cancel: set token, modules check before/after execution
- Cancelled modules complete output deferreds with `CancelledException`
- Resource cleanup hook for modules that acquire resources
- `ExecutionListener.onExecutionComplete` called with `Cancelled` status

**Acceptance criteria:**
- [ ] `CancellableExecution.cancel` stops all in-flight modules within 1 second
- [ ] Cancelled execution returns `Cancelled` status with partial results
- [ ] Resources acquired by cancelled modules are released
- [ ] `ExecutionListener` and `MetricsProvider` called on cancellation

### 2.2 Global DAG Timeout

**Problem:** Only per-module timeouts exist. No way to set a deadline for the entire DAG. A DAG with 50 modules could run indefinitely.

**Proposed Design:**

```scala
// Programmatic API
Runtime.runCancellable(dag, inputs, modules, scheduler, backends)
  .flatMap(_.result.timeout(30.seconds))

// Or built into the builder
Constellation.builder()
  .withDefaultTimeout(5.minutes)
  .build()
```

In constellation-lang:
```constellation
@timeout(30s)
in text: String
result = SlowPipeline(text)
out result
```

**Implementation approach:**
- Wrap `runCancellable` with `IO.timeout(duration)`
- On timeout: invoke cancellation, return `TimedOut` status
- Default timeout configurable in builder (default: 5 minutes)

**Acceptance criteria:**
- [ ] DAG execution respects global timeout regardless of per-module timeouts
- [ ] Timed-out execution returns partial results with `TimedOut` status
- [ ] Default timeout configurable via builder API
- [ ] Timeout overridable per-execution call

### 2.3 Circuit Breaker

**Problem:** Retry logic doesn't detect repeated failure patterns. No fail-fast after consecutive failures.

**Proposed Design:**

Library API only — circuit breaker is an infrastructure concern configured by embedders, not script authors. This keeps the constellation-lang surface area small.

Three states: Closed (normal) → Open (fail-fast) → Half-Open (probe)

```scala
trait CircuitBreaker {
  def protect[A](operation: IO[A]): IO[A]
  def state: IO[CircuitState]
  def stats: IO[CircuitStats]
}
```

**Implementation approach:**
- `Ref[IO, CircuitState]` per module name (shared across DAG executions)
- Track consecutive failures; open circuit when threshold exceeded
- Open state: immediately fail with `CircuitOpenException`
- After reset duration: half-open, allow single probe
- State reported via `MetricsProvider` gauge
- In-memory only (resets on restart, which is acceptable — avoids hammering on cold start by warming up naturally)

**Acceptance criteria:**
- [ ] Circuit opens after N consecutive failures (configurable)
- [ ] Open circuit fails immediately without calling downstream
- [ ] Half-open state allows single probe after reset duration
- [ ] Circuit state observable via `MetricsProvider`
- [ ] Per-module circuit breaker instances shared across executions

### 2.4 Graceful Shutdown

**Problem:** No way to drain in-flight work. On process kill, executions are lost and resources leaked.

**Proposed Design:**

The library provides a `Lifecycle` API. In the optional http-api module, this integrates with SIGTERM. For embedders, they call it from their own shutdown hooks.

```scala
trait ConstellationLifecycle {
  /** Stop accepting new work, wait for in-flight to complete */
  def shutdown(drainTimeout: FiniteDuration = 30.seconds): IO[Unit]

  /** Current state */
  def state: IO[LifecycleState] // Running, Draining, Stopped

  /** Number of in-flight executions */
  def inflightCount: IO[Int]
}
```

**Implementation approach:**
- Track in-flight executions via `Ref[IO, Set[ExecutionId]]`
- On `shutdown()`: set draining flag, reject new submissions, wait for set to empty (with timeout)
- On drain timeout: cancel remaining executions via cancellation API
- Scheduler shutdown called at end

**Acceptance criteria:**
- [ ] `shutdown()` drains in-flight executions (up to timeout)
- [ ] New submissions rejected during drain with clear error
- [ ] Remaining executions cancelled after drain timeout
- [ ] `inflightCount` observable by embedder for their own health checks
- [ ] http-api module wires this to SIGTERM

### 2.5 Backpressure & Queue Rejection

**Problem:** Unbounded queue growth in the scheduler. No rejection policy.

**Proposed Design:**

```scala
// Builder configuration
Constellation.builder()
  .withScheduler(SchedulerConfig(
    maxConcurrency = 16,
    maxQueueSize = 1000,
    starvationTimeout = 30.seconds
  ))
  .build()
```

**Implementation approach:**
- Add `maxQueueSize` to `BoundedGlobalScheduler`
- On `submit()`: check queue size; reject with `QueueFullException` if at capacity
- Rejection count tracked via `MetricsProvider` counter
- http-api module maps `QueueFullException` to `429 Too Many Requests`

**Acceptance criteria:**
- [ ] Scheduler rejects submissions when queue reaches max
- [ ] `MetricsProvider` counter incremented on rejection
- [ ] Queue size observable via `MetricsProvider` gauge
- [ ] Default max queue size: 1000 (configurable via builder)

---

## Phase 3: HTTP Module Hardening

**Goal:** Make the optional http-api module safe to expose to a network.

These items are scoped to the `constellation-http` optional module, not the core library. Embedders who build their own HTTP layer handle these concerns themselves.

### 3.1 API Authentication

Static API keys with role-based access. Simplest path to "not wide open."

```scala
// http-api module configuration
ConstellationServer.builder(constellation, compiler)
  .withAuth(AuthConfig(
    enabled = true,
    apiKeys = Map(
      "key1" -> Role.Admin,
      "key2" -> Role.Execute,
      "key3" -> Role.ReadOnly
    )
  ))
  .run
```

| Role | Permissions |
|------|------------|
| `Admin` | All operations |
| `Execute` | Compile, execute, read |
| `ReadOnly` | Read modules, DAGs, metrics |

- `/health` and `/metrics` exempt from auth (for monitoring)
- Future: swap to JWT/OAuth without changing middleware interface

### 3.2 CORS Middleware

```scala
ConstellationServer.builder(constellation, compiler)
  .withCors(CorsConfig(
    allowedOrigins = Set("https://dashboard.example.com", "http://localhost:3000")
  ))
  .run
```

Uses http4s built-in CORS middleware. Disabled by default.

### 3.3 HTTP Rate Limiting

Token bucket per client IP (or per API key when auth enabled).

```scala
ConstellationServer.builder(constellation, compiler)
  .withRateLimit(RateLimitConfig(
    requestsPerMinute = 100,
    burst = 20
  ))
  .run
```

Returns `429 Too Many Requests` with `Retry-After` header. Disabled by default. `/health` and `/metrics` exempt.

### 3.4 Deep Health Checks

```
GET /health         → Liveness (200 if alive, 503 during shutdown)
GET /health/ready   → Readiness (checks scheduler, modules, compiler)
GET /health/detail  → Component-level diagnostics with metrics
```

Readiness probe uses `ConstellationLifecycle.state` from Phase 2.4.

**Acceptance criteria for Phase 3:**
- [x] Auth middleware rejects unauthenticated requests when enabled
- [x] CORS headers present when configured
- [x] Rate limiting returns 429 when exceeded
- [x] Health endpoints distinguish liveness, readiness, and detail
- [x] All features disabled by default (zero behavior change for existing users)

---

## Phase 4: Deployment Examples

**Goal:** Provide reference deployment artifacts for the optional http-api module.

These are **examples**, not primary deliverables. The core product is the library; deployment is the embedder's concern. But good examples lower the barrier to adoption.

### 4.1 Fat JAR (sbt-assembly)

- `sbt "exampleApp/assembly"` produces a runnable JAR
- `java -jar constellation.jar` starts the example server
- GitHub Release artifacts include the fat JAR

### 4.2 Dockerfile

Multi-stage build: sbt builder → JRE-only runtime. Health check built in. Configurable via environment variables.

### 4.3 Docker Compose

Development stack with optional Prometheus + Grafana for monitoring the example server.

### 4.4 Kubernetes Manifests

Reference deployment with liveness/readiness probes, resource limits, ConfigMap. Located in `deploy/k8s/`.

**Acceptance criteria for Phase 4:**
- [ ] `sbt "exampleApp/assembly"` produces runnable JAR
- [ ] `docker build` + `docker run` works
- [ ] `docker compose up` starts example server with optional monitoring
- [ ] K8s manifests deploy a working instance with probes

---

## Phase 5: Testing for Scale

**Goal:** Prove the library works under pressure.

### 5.1 Large DAG Stress Tests

Generate DAGs programmatically with configurable width (parallelism) and depth (sequential chains).

```scala
class LargeDagStressTest extends AnyFlatSpec {
  "Runtime" should "execute a 100-node DAG within 5 seconds" in { ... }
  "Runtime" should "execute a 500-node DAG within 30 seconds" in { ... }
  "Compiler" should "compile a 1000-node program within 10 seconds" in { ... }
  "Scheduler" should "handle 1000 concurrent submissions" in { ... }
}
```

- [ ] 100-node and 500-node DAGs compile and execute within time bounds
- [ ] Memory usage stays bounded (no leaks)
- [ ] Results are deterministic

### 5.2 Property-Based Testing (ScalaCheck)

Add generators for random valid constellation-lang programs, type expressions, DAG topologies, and CValue instances.

**Properties to verify:**
- Parsing a printed AST roundtrips to the same AST
- Type checking is deterministic
- Compilation is deterministic
- Execution is deterministic

- [ ] ScalaCheck dependency added
- [ ] Generators for core types (CType, CValue, AST nodes)
- [ ] Roundtrip and determinism properties verified
- [ ] Integrated into CI

### 5.3 Sustained Load Testing

Run 10K+ executions, measure heap growth, GC pauses, and latency degradation.

- [ ] 10K executions complete without OOM
- [ ] Heap growth < 50MB
- [ ] p99 latency doesn't degrade over time
- [ ] Object pools maintain stable size

### 5.4 Adversarial Input Fuzzing

Random byte sequences, deeply nested expressions, extremely long programs.

- [ ] Parser handles 10K random inputs without crashing
- [ ] Deeply nested expressions (1000+ levels) don't stack overflow
- [ ] All failures produce structured errors, not unhandled exceptions

---

## Phase 6: Documentation

**Goal:** Documentation sufficient for an external developer to embed, configure, extend, operate, and troubleshoot Constellation without reading source code.

The existing docs cover constellation-lang syntax, architecture internals, and development workflow well. What's missing is the **embedder-facing surface**: how to use the library, implement the SPI, tune performance, and operate in production.

### 6.1 Embedding Quick Start

A single guide that takes a developer from zero to running pipeline in their own JVM app.

- [ ] Add Constellation as an sbt/Maven/Gradle dependency
- [ ] Minimal `Constellation.builder()` setup with default backends
- [ ] Compile and execute a `.cst` script programmatically
- [ ] Read outputs from execution result
- [ ] Complete runnable example (copy-paste-run)

**Location:** `docs/embedding-guide.md`

### 6.2 SPI Integration Guides

One guide per backend trait showing how to implement it with a real-world library.

- [ ] **MetricsProvider guide** — example with Prometheus (micrometer) and Datadog
- [ ] **TracerProvider guide** — example with OpenTelemetry (otel4s) and Jaeger
- [ ] **ExecutionListener guide** — example writing events to Kafka and to a database
- [ ] **ExecutionStorage guide** — example with PostgreSQL (Doobie/Skunk) and SQLite
- [ ] **CacheBackend guide** — example with Redis (redis4cats) and Caffeine
- [ ] Each guide includes: trait implementation, wiring into `ConstellationBackends`, testing the integration, gotchas

**Location:** `docs/integrations/spi/` (one file per trait)

### 6.3 API Reference (Scaladoc)

Scaladoc coverage for the entire public API surface.

- [ ] `Constellation` builder — all methods documented with examples
- [ ] `ConstellationBackends` — each field documented
- [ ] All five SPI traits — every method documented with contract (pre/postconditions, threading)
- [ ] `CancellableExecution` — lifecycle states, cancel semantics
- [ ] `ConstellationLifecycle` — shutdown/drain behavior
- [ ] `CircuitBreaker` — state machine documented
- [ ] `SchedulerConfig` — each parameter documented with valid ranges
- [ ] `Runtime.State`, `ExecutionStatus`, all public ADTs
- [ ] Ensure `sbt doc` generates clean output with no warnings

### 6.4 HTTP API Reference

Formal endpoint documentation for the optional http-api module.

- [ ] OpenAPI 3.0 spec (`docs/api/openapi.yaml`) generated or hand-written
- [ ] Every endpoint: method, path, request/response schemas, status codes, auth requirements
- [ ] WebSocket protocol for LSP (`docs/api/lsp-websocket.md` — already exists, update if needed)
- [ ] Error response format documented (error codes, messages)
- [ ] Example `curl` commands for every endpoint

### 6.5 Architecture Update

Update existing architecture docs to reflect the new SPI and lifecycle model.

- [ ] Update `docs/architecture.md` — add SPI layer, lifecycle management, backend wiring
- [ ] Update `llm.md` — add ConstellationBackends, builder API, new module dependency edges
- [ ] Add architecture diagram showing: core → runtime → SPI traits → embedder implementations
- [ ] Document module dependency graph with the new SPI module placement
- [ ] Update `docs/dev/core-features.md` with lifecycle and SPI features

### 6.6 Performance Tuning Guide

Practical guide for embedders configuring Constellation for their workload.

- [ ] Scheduler tuning: `maxConcurrency` sizing based on workload type (CPU-bound vs IO-bound)
- [ ] Queue sizing: `maxQueueSize` trade-offs (memory vs rejection rate)
- [ ] Timeout strategy: global vs per-module timeout interactions
- [ ] Circuit breaker tuning: threshold and reset duration selection
- [ ] Object pool sizing: when to adjust pool parameters
- [ ] Cache configuration: LRU size, hit rate monitoring, cache invalidation
- [ ] JVM flags: recommended GC settings, heap sizing for sustained load
- [ ] Diagnostic checklist: what to check when pipelines are slow

**Location:** `docs/performance-tuning.md`

### 6.7 Error Reference

Structured catalog of all errors the library can produce.

- [ ] Compilation errors — already partially covered in `docs/constellation-lang/error-messages.md`, ensure completeness
- [ ] Runtime errors — `ModuleExecutionException`, `TimeoutException`, `CircuitOpenException`, `QueueFullException`, `CancelledException`
- [ ] Builder validation errors — invalid configuration messages
- [ ] Each error: code, message template, cause, resolution steps
- [ ] Distinguish recoverable vs fatal errors

**Location:** `docs/error-reference.md`

### 6.8 Migration Guide

For existing users upgrading to the new API.

- [ ] Breaking changes summary (if any API changed)
- [ ] Before/after code examples for each change
- [ ] New optional features and how to opt in
- [ ] Deprecation notices with timelines

**Location:** `docs/migration/` (one file per major version)

### 6.9 Security Model

Document the security boundaries and the embedder's responsibilities.

- [ ] What the library trusts (module code, compiled DAGs)
- [ ] What the library validates (inputs, types, DAG structure)
- [ ] Embedder's responsibilities (auth, network isolation, secret management)
- [ ] http-api module security features (auth, CORS, rate limiting — from Phase 3)
- [ ] Script sandboxing limitations (constellation-lang cannot call arbitrary code, but modules can)
- [ ] Dependency audit process

**Location:** `docs/security.md`

### 6.10 Dashboard & Tooling

Document the dashboard and VSCode extension for users who opt into the http-api module.

- [ ] Dashboard feature overview with screenshots
- [ ] Dashboard architecture (TypeScript components, Cytoscape.js, API integration)
- [ ] VSCode extension setup and features (already partially in `vscode-extension/README.md`, consolidate)
- [ ] MCP server setup for LLM-assisted development

**Location:** `docs/tooling.md`

**Acceptance criteria for Phase 6:**
- [ ] An external developer can embed and run Constellation using only the docs (no source reading)
- [ ] Every SPI trait has a working integration example
- [ ] `sbt doc` produces clean Scaladoc for all public types
- [ ] HTTP API has a machine-readable OpenAPI spec
- [ ] Architecture docs reflect the current state of the system (not stale)
- [ ] Performance tuning guide validated against benchmark results
- [ ] All new documentation reviewed for accuracy by running the examples

---

## Implementation Order & Dependencies

```
Phase 1 (Backend SPI)                Phase 2 (Execution Lifecycle)
├── 1.1 ConstellationBackends       ├── 2.1 DAG Cancellation
├── 1.2 MetricsProvider             │   └── 2.2 Global DAG Timeout
├── 1.3 TracerProvider              ├── 2.3 Circuit Breaker
├── 1.4 ExecutionListener           ├── 2.4 Graceful Shutdown
└── 1.5 Storage & Cache formalize   └── 2.5 Backpressure
    ▲                                   ▲
    │ (Phase 2 uses Phase 1 traits)     │
    └───────────────────────────────────┘

Phase 3 (HTTP Module)               Phase 4 (Deployment Examples)
├── 3.1 API Auth                    ├── 4.1 Fat JAR
├── 3.2 CORS                       ├── 4.2 Dockerfile
├── 3.3 HTTP Rate Limiting          ├── 4.3 Docker Compose
└── 3.4 Deep Health Checks          └── 4.4 K8s Manifests

Phase 5 (Testing)                   Phase 6 (Documentation)
├── 5.1 Large DAG Stress Tests      ├── 6.1 Embedding Quick Start
├── 5.2 Property-Based Testing      ├── 6.2 SPI Integration Guides  ← After Phase 1
├── 5.3 Sustained Load Testing      ├── 6.3 API Reference (Scaladoc)
└── 5.4 Adversarial Fuzzing         ├── 6.4 HTTP API Reference       ← After Phase 3
                                    ├── 6.5 Architecture Update
                                    ├── 6.6 Performance Tuning Guide  ← After Phase 5
                                    ├── 6.7 Error Reference
                                    ├── 6.8 Migration Guide
                                    ├── 6.9 Security Model
                                    └── 6.10 Dashboard & Tooling
```

**Recommended start order:**
1. Phase 1 (SPI) — establishes the extension surface everything else builds on
2. Phase 2 (Lifecycle) — hardest, highest impact
3. Phase 5 (Testing) — can run in parallel with Phases 3-4
4. Phase 3 (HTTP) — straightforward http4s middleware
5. Phase 4 (Deployment) — lowest priority, reference artifacts
6. Phase 6 (Documentation) — written incrementally as each phase lands; 6.1 and 6.5 can start early, others after the features they document

---

## Configuration Model

**Programmatic builder API only.** No config files in the core library. The embedding application owns its own configuration system (Typesafe Config, env vars, YAML, etc.) and passes values to the builder.

```scala
val constellation = Constellation.builder()
  .withBackends(backends)
  .withModules(allModules)
  .withScheduler(SchedulerConfig(
    maxConcurrency = 16,
    maxQueueSize = 1000,
    starvationTimeout = 30.seconds
  ))
  .withDefaultTimeout(5.minutes)
  .build()
```

The optional http-api module may read environment variables for convenience (as it does today), but this is a feature of that module, not the core library.

**Validation:** The builder validates all configuration at `build()` time — fail-fast. Invalid values (e.g., `maxConcurrency: -1`, `maxQueueSize: 0`) cause `build()` to return an error immediately, not at first execution.

---

## Non-Goals (Explicit)

- **Config files in core library** — embedder's concern
- **Baked-in Prometheus/Jaeger/etc.** — provide traits, not implementations
- **Persistent storage in core** — in-memory defaults; persistence via SPI
- **Multi-instance coordination** — premature; separate RFC when needed
- **Streaming / incremental execution** — architecture change; separate RFC
- **Multi-tenancy isolation** — requires auth + quotas + namespaces; too large
- **Dashboard UI features** — out of scope

---

## Resolved Design Decisions

| Question | Decision | Rationale |
|----------|----------|-----------|
| MetricsProvider API richness | Simple fire-and-forget (`counter(name, tags): IO[Unit]`) | Minimal surface area; embedders who need pre-registration handle it in their implementation |
| Circuit breaker scope | Library API only, not in constellation-lang | Infrastructure concern; keeps language surface area small |
| ExecutionListener semantics | Fire-and-forget (background fibers) | Zero latency impact; audit/checkpoint needs can be a future middleware API |
| Builder validation | Fail-fast at `build()` time | Standard builder pattern; catch misconfiguration at startup, not at first use |

---

## References

- [Cats Effect 3 — Resource & Lifecycle](https://typelevel.org/cats-effect/docs/std/resource)
- [http4s Middleware — CORS, Metrics](https://http4s.org/v0.23/middleware/)
- [otel4s — OpenTelemetry for Scala](https://github.com/typelevel/otel4s)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [sbt-assembly Plugin](https://github.com/sbt/sbt-assembly)
