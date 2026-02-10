# RFC-025: Streaming Pipelines

**Status:** Draft
**Priority:** P1 (Core Runtime / Extensibility)
**Author:** Human + Claude
**Created:** 2026-02-10

---

## Summary

Introduce an **fs2-based streaming compilation backend** that compiles `.cst` pipelines into continuous `Stream[IO, _]` graphs, with a **connector registry** for pluggable sources and sinks (Kafka, HTTP streams, WebSockets, files, etc.). Pipelines become deployable as long-running streaming infrastructure — hot or cold loaded — while remaining fully compatible with the existing batch (request-response) execution mode.

The language gains new `with`-clause options for streaming-specific concerns (windowing, batching, merge strategy, checkpointing) that are validated at compile time in streaming mode and safely ignored in batch mode. This preserves the core design principle: **a single pipeline definition, multiple execution strategies**.

**Key Insight:** The existing DagSpec is execution-strategy-agnostic. By adding a second compilation backend (`StreamCompiler`) alongside the existing `Runtime`, the same DAG topology can be wired as either a one-shot Deferred graph or a continuous fs2 Stream graph — without modifying the batch path.

---

## Motivation

### The Gap

Constellation pipelines currently execute in request-response mode: all inputs provided upfront, DAG runs to completion, outputs returned. This works well for API-triggered workloads but cannot model:

- **Event-driven processing** — reacting to continuous streams of events (user actions, IoT telemetry, transaction feeds)
- **Real-time enrichment** — augmenting events with context as they flow through (e.g., enrich click events with user profiles)
- **Continuous aggregation** — computing running statistics, windowed counts, or rolling averages over unbounded data
- **Stream routing** — fan-out events to different processing paths based on content, then fan-in results

Today, teams wanting streaming must leave Constellation entirely and build bespoke fs2/Kafka Streams/Flink pipelines, losing all the benefits of Constellation's type safety, DAG optimization, resilience options, and declarative composition.

### What This Enables

A developer can:

1. Write a pipeline in `.cst` using familiar syntax (`in`, `out`, module calls, type algebra)
2. Deploy it as a **batch endpoint** (existing behavior, unchanged) OR as **streaming infrastructure** (new)
3. Bind inputs/outputs to connectors (Kafka topics, WebSocket channels, HTTP SSE endpoints) via a deployment configuration or inline annotations
4. Use stream-specific options (`window`, `batch`, `checkpoint`) when needed, with the compiler validating them
5. Hot-reload streaming pipelines with zero downtime (extending the existing pipeline lifecycle from RFC-015)

### Why fs2?

- **Already in the dependency tree** — used in `http-api` for WebSocket event broadcasting
- **Native Cats Effect integration** — composes with the existing IO-based module signatures
- **Built-in backpressure** — no manual flow control needed
- **Resource safety** — bracket-based lifecycle ensures connectors are properly closed
- **Pull-based model** — consumers drive the pace, preventing OOM on slow downstream

---

## Design Principles

| Principle | Rationale |
|-----------|-----------|
| **Separate compilation backend** | A new `StreamCompiler` reads the same `DagSpec` and produces an fs2 Stream graph. The existing `Runtime` batch path is untouched — no risk of regression. |
| **Pipeline-mode-agnostic syntax** | The core language (`in`, `out`, module calls, type algebra, guards) works identically in both modes. Stream-specific options are additive. |
| **Connectors are deployment config** | `in`/`out` declarations remain type-only by default. Connector bindings (Kafka topic, WebSocket path) are a deployment concern, not a language concern. Optional inline annotations provide hints. |
| **Transparent module lifting** | Modules written as `I => IO[O]` are automatically lifted to `Stream[IO, I] => Stream[IO, O]` via `stream.evalMap(f)`. No module changes required. |
| **Additive language extensions** | New `with`-clause options (`window`, `batch`, `checkpoint`, `merge`) extend the existing option system. In batch mode they are ignored or produce compiler warnings. |
| **Backpressure by default** | fs2's pull-based model provides backpressure without configuration. Existing `throttle` and `concurrency` options map to `Stream.metered` and `Stream.parEvalMap`. |

---

## Architecture

### Compilation Pipeline

```
                          ┌──────────────────────┐
                          │  Existing Batch Path  │
                          │                       │
                     ┌───▶│  Runtime.run()        │──▶ Map[String, CValue]
                     │    │  (Deferred-based)     │
                     │    └──────────────────────┘
                     │
AST → IR → DagSpec ──┤
                     │    ┌──────────────────────┐
                     │    │  New Streaming Path   │
                     │    │                       │
                     └───▶│  StreamCompiler.wire() │──▶ Stream[IO, CValue]
                          │  (fs2-based)          │
                          └──────────────────────┘
```

The `DagSpec` is the shared intermediate representation. Both backends read the same topology, edges, module specs, and inline transforms. The difference is execution strategy:

| Concern | Batch (`Runtime`) | Streaming (`StreamCompiler`) |
|---------|-------------------|------------------------------|
| Data node | `Deferred[IO, Any]` (single value) | `Stream[IO, CValue]` (continuous) |
| Module execution | `deferred.get` → run → `deferred.complete` | `stream.evalMap(run)` or `stream.parEvalMap(n)(run)` |
| Inline transforms | Compute once, store result | `stream.map(transform)` per element |
| Fan-out | Implicit via DAG deps | `stream.broadcastThrough(...)` |
| Fan-in (merge `+`) | Record merge (single value) | Configurable: `merge` / `zip` / `combineLatest` |
| Completion | All deferreds resolved | Stream terminates or runs indefinitely |

### Module Lifecycle

```
┌──────────────────────────────────────────────────────┐
│                   Module.Uninitialized                │
│  spec: ModuleNodeSpec                                │
│  init: (UUID, DagSpec) => IO[Runnable]               │
└────────────────────┬─────────────────────────────────┘
                     │
          ┌──────────┴──────────┐
          │                     │
    Batch │               Streaming
          │                     │
          ▼                     ▼
  Module.Runnable         Module.Runnable
  run: Runtime => IO[Unit]  (same — StreamCompiler wraps it)
                            evalMap lifts IO[Unit] to
                            Stream[IO, CValue] => Stream[IO, CValue]
```

**No module interface changes.** Existing modules work in both modes. The `StreamCompiler` wraps the module's `run` function:

```scala
// Batch mode (existing)
module.run(runtime)  // IO[Unit], completes deferreds

// Streaming mode (new)
inputStream.evalMap { element =>
  // Create single-element runtime context
  // Run module against it
  // Extract output
}
```

---

## Connector Registry

### Connector Traits

```scala
trait SourceConnector {
  def typeName: String  // e.g. "kafka", "websocket", "http-sse"
  def stream(config: ConnectorConfig): Stream[IO, CValue]
}

trait SinkConnector {
  def typeName: String
  def pipe(config: ConnectorConfig): Pipe[IO, CValue, Unit]
}

// Configuration is connector-specific
case class ConnectorConfig(
  properties: Map[String, String]  // e.g. "topic" -> "user-events"
)
```

### Registry

```scala
val registry = ConnectorRegistry.builder
  .source("kafka", KafkaSourceConnector(bootstrapServers))
  .source("websocket", WebSocketSourceConnector(host, port))
  .source("http-sse", HttpSseSourceConnector())
  .sink("kafka", KafkaSinkConnector(bootstrapServers))
  .sink("webhook", WebhookSinkConnector())
  .build
```

### Built-In Connectors (Phase 1)

| Connector | Source | Sink | Description |
|-----------|--------|------|-------------|
| `memory` | Yes | Yes | In-memory `Queue[IO, CValue]` — testing and embedding |
| `http-sse` | Yes | No | Server-Sent Events HTTP stream |
| `websocket` | Yes | Yes | WebSocket channels (extends existing LSP infra) |

### External Connectors (Phase 2+, separate modules)

| Connector | Source | Sink | Library |
|-----------|--------|------|---------|
| `kafka` | Yes | Yes | fs2-kafka |
| `file` | Yes | Yes | fs2-io |
| `grpc-stream` | Yes | Yes | fs2-grpc |

External connectors ship as separate sbt modules (like `cache-memcached`) to avoid forcing dependencies on users who don't need them.

---

## Deployment Configuration

### Connector Bindings

When loading a pipeline in streaming mode, a **binding configuration** maps `in`/`out` declarations to connectors:

```scala
val streamConfig = StreamPipelineConfig(
  mode = ExecutionMode.Streaming,
  bindings = Map(
    "events"  -> SourceBinding("kafka", Map("topic" -> "user-events", "group" -> "pipeline-1")),
    "context" -> SourceBinding("kafka", Map("topic" -> "user-context")),
    "results" -> SinkBinding("kafka", Map("topic" -> "enriched-events"))
  ),
  options = StreamOptions(
    checkpointInterval = 1.minute,
    maxConcurrency = 16
  )
)

// Load and run as streaming infrastructure
val fiber: IO[Fiber[IO, Throwable, Unit]] =
  streamRuntime.run(compiledPipeline, streamConfig, connectorRegistry)
```

### Hot/Cold Loading Integration

Extends the existing pipeline lifecycle (RFC-015):

| Operation | Batch (existing) | Streaming (new) |
|-----------|------------------|-----------------|
| Cold load | Compile + store in `PipelineStore` | Same + wire stream graph |
| Hot load | Recompile + swap `LoadedPipeline` | Gracefully drain old stream, start new |
| Hot reload | Replace in-memory pipeline | Drain → recompile → reconnect to same connectors |
| Canary | Traffic splitting by request | Traffic splitting by partition/key range |

**Graceful hot-reload for streams:**

```
1. New pipeline version compiled and validated
2. New stream graph wired but not started
3. Old stream signaled to drain (finishes in-flight elements)
4. Old stream completes, resources released
5. New stream starts consuming from last committed offset
6. Health check confirms new stream is processing
```

---

## Language Extensions

### New `with`-Clause Options

All new options are **additive** to the existing 12 options. In batch mode they are ignored (with an optional compiler info diagnostic).

#### Windowing

```constellation
# Tumbling window: non-overlapping, fixed-size
stats = ComputeStats(events) with window: tumbling(5min)

# Sliding window: overlapping, fixed-size with slide interval
rolling = RollingAvg(readings) with window: sliding(10min, slide: 1min)

# Count-based window
batch_result = ProcessBatch(items) with window: count(100)
```

**Semantics:** The module receives a `List<T>` (the window contents) instead of a single `T`. The compiler validates that the module's input type accepts `List<T>` when a window option is present.

#### Batching

```constellation
# Collect elements before passing to module
processed = HeavyModel(stream) with
    batch: 50,           # collect 50 elements
    batch_timeout: 2s    # or flush after 2s, whichever comes first
```

**Dual-mode semantics:** In batch mode, `batch` chunks an existing `List<T>` input into sub-lists. In streaming mode, it buffers elements from the stream. Either way, the module receives `List<T>`.

#### Fan-In Merge Strategy

```constellation
# When merging multiple streams with type algebra
combined = streamA + streamB with merge: interleave

# Options:
#   interleave  — emit elements as they arrive from either stream (default)
#   zip         — pair elements 1:1, backpressure the faster stream
#   concat      — drain streamA fully, then streamB
```

**Batch mode:** Ignored (record merge is always deterministic in batch).

#### Checkpointing

```constellation
result = StatefulModule(events) with
    checkpoint: 1min,               # snapshot state every minute
    checkpoint_backend: "rocksdb"   # state backend for checkpoints
```

**Batch mode:** Ignored (stateless single-pass execution).

### Optional Inline Source/Sink Annotations

For convenience, pipelines can declare connector hints. These are **non-binding** — the deployment config can override them:

```constellation
# Annotation syntax (parsed but not required)
@source(kafka, topic: "user-events", group: "enrichment")
in events: { userId: String, action: String, timestamp: Int }

@sink(kafka, topic: "enriched-events")
out enriched
```

**If no annotation is present**, the pipeline works in both modes:
- Batch: caller provides `Map[String, CValue]` as today
- Streaming: deployment config provides connector bindings

**If annotation is present but deployment config overrides it**, the deployment config wins (annotation is a default/hint).

---

## Stream Compilation

### StreamCompiler

```scala
object StreamCompiler {

  case class StreamGraph(
    stream: Stream[IO, Unit],        // The composed, runnable stream
    metrics: StreamMetrics,          // Live counters (elements processed, errors, latency)
    shutdown: IO[Unit]               // Graceful shutdown signal
  )

  def wire(
    dag: DagSpec,
    modules: Map[UUID, Module.Uninitialized],
    moduleOptions: Map[UUID, ModuleCallOptions],
    sources: Map[String, Stream[IO, CValue]],   // bound inputs
    sinks: Map[String, Pipe[IO, CValue, Unit]], // bound outputs
    config: StreamOptions
  ): IO[StreamGraph]
}
```

### Wiring Algorithm

1. **Identify source nodes** — `DagSpec.userInputDataNodes` → bind to source streams
2. **Topological sort** — same as batch, determines wiring order
3. **For each data node:**
   - If source node → use bound `Stream[IO, CValue]`
   - If inline transform → `inputStreams.map(transform)`
   - If module output → `inputStream.evalMap(module.run)` (or `parEvalMap` if `concurrency` option set)
4. **Apply streaming options:**
   - `window` → `stream.groupWithin(n, d)` or custom windowing
   - `batch` → `stream.chunkN(n).map(_.toList)` with timeout
   - `merge` → `streamA.merge(streamB)` / `.zip` / `.append`
   - `throttle` → `stream.metered(rate)`
   - `retry` → `stream.attempts(schedule).collect { case Right(v) => v }`
   - `timeout` → `stream.timeout(d)` per element
   - `fallback` → `.handleErrorWith(_ => Stream.emit(fallbackValue))`
   - `checkpoint` → periodic state snapshots via `Stream.fixedDelay`
5. **Connect sink nodes** — `DagSpec.outputBindings` → pipe to sinks
6. **Compose** — all streams merged into a single `Stream[IO, Unit]` with resource management

### Fan-Out / Fan-In

```
Fan-out (one source, multiple consumers):

  source ──┬──▶ ModuleA ──▶ ...
           ├──▶ ModuleB ──▶ ...
           └──▶ ModuleC ──▶ ...

Implementation: source stream is broadcast to each consumer fiber.
Each consumer applies backpressure independently.
```

```
Fan-in (multiple sources, one consumer via type algebra +):

  streamA ──┐
             ├──▶ merge/zip ──▶ Module ──▶ ...
  streamB ──┘

Implementation: configurable via `merge` option.
Default: interleave (non-deterministic merge).
```

### Resilience Options in Streaming Mode

Existing resilience options map naturally to fs2 combinators:

| Option | Batch Semantics | Streaming Semantics |
|--------|----------------|---------------------|
| `retry: N` | Retry module call N times | Retry per-element N times, continue stream |
| `timeout: Ts` | Fail if module exceeds T | Per-element timeout, emit error or skip |
| `fallback: expr` | Use fallback on failure | Per-element fallback, stream continues |
| `cache: Tttl` | Cache result for TTL | Cache per-key, deduplicate stream |
| `throttle: N/Ts` | Rate-limit calls | `stream.metered(T/N)` |
| `concurrency: N` | Semaphore(N) | `stream.parEvalMap(N)` |
| `on_error: skip` | Skip failed node | Drop failed element, continue stream |
| `on_error: log` | Log and skip | Log failed element, continue stream |
| `lazy` | Execute only if output used | Ignored in streaming (all paths are active) |
| `priority` | Scheduler hint | Ignored (fs2 manages scheduling) |

---

## Type System Considerations

### Approach: Streaming is a Runtime Concern

The type system does **not** introduce a `Stream<T>` type. Instead:

- Pipelines are typed as they are today (`in events: Record`, `out results`)
- The **execution mode** determines whether `events` is a single value or a continuous stream
- Modules receive the **element type** in both modes — a module typed `Record => IO[Record]` processes one record at a time whether that record came from an HTTP request or a Kafka topic

This keeps the type system simple and pipelines truly dual-mode.

### Windowed Modules Are an Exception

When `window` is specified, the module's input type is `List<T>` instead of `T`. The compiler validates this:

```constellation
# ComputeStats must accept List<Record>, not Record
stats = ComputeStats(events) with window: tumbling(5min)
```

**Compiler rule:** If `window` is present, the input CType must be `CList(elementType)` where `elementType` matches the stream element type. This is checked at compile time in streaming mode and ignored in batch mode.

---

## HTTP API Extensions

### New Endpoints

```
POST /pipelines/{name}/deploy-stream
  Body: StreamPipelineConfig (bindings, options)
  Response: { streamId, status: "running" }

DELETE /streams/{streamId}
  Graceful shutdown of a running stream

GET /streams
  List all running streams with health status

GET /streams/{streamId}
  Stream details: throughput, latency, errors, lag

GET /streams/{streamId}/metrics
  Prometheus-format metrics for the stream
```

### Dashboard Integration

- **Stream status panel** — running streams with live throughput/latency gauges
- **Stream topology view** — DAG visualization with per-edge element counts and flow rates
- **Connector health** — source lag, sink throughput, error rates
- **Hot-reload controls** — deploy new version, drain, rollback

---

## Implementation Phases

### Phase 1: Core Streaming Backend

**Scope:** `StreamCompiler` + memory connectors + basic wiring

- [ ] `StreamCompiler.wire()` — converts DagSpec to fs2 Stream graph
- [ ] Module lifting — `evalMap` wrapper for existing modules
- [ ] Inline transform streaming — `stream.map(transform)` for merges, projections, field access
- [ ] Memory source/sink connectors for testing
- [ ] `StreamGraph` lifecycle — start, graceful shutdown, error handling
- [ ] Integration tests with existing example pipelines running in streaming mode

**Deliverables:** A pipeline that works in batch mode today can also run as a stream with in-memory connectors.

### Phase 2: Connector Registry + Built-In Connectors

**Scope:** Connector SPI + WebSocket + HTTP SSE connectors

- [ ] `SourceConnector` / `SinkConnector` traits
- [ ] `ConnectorRegistry` builder API
- [ ] WebSocket source/sink (extends existing LSP WebSocket infra)
- [ ] HTTP SSE source connector
- [ ] Connector health checks
- [ ] `StreamPipelineConfig` binding configuration

**Deliverables:** Streaming pipelines can consume from WebSockets and emit to SSE endpoints.

### Phase 3: Language Extensions

**Scope:** New `with`-clause options + `@source`/`@sink` annotations

- [ ] Parser: `window`, `batch`, `batch_timeout`, `merge`, `checkpoint`, `checkpoint_backend` options
- [ ] AST: Extend `ModuleCallOptions` with streaming fields
- [ ] Type checker: Validate windowed module input types
- [ ] Compiler: Streaming option validation (warn in batch mode, error if invalid in stream mode)
- [ ] Parser: `@source` / `@sink` annotation syntax
- [ ] AST: `Declaration.InputDecl` / `OutputDecl` annotation fields

**Deliverables:** Pipelines can express windowing, batching, and merge strategy in the language.

### Phase 4: HTTP API + Dashboard

**Scope:** Stream management endpoints + dashboard integration

- [ ] `/pipelines/{name}/deploy-stream` endpoint
- [ ] `/streams` CRUD and monitoring endpoints
- [ ] Prometheus metrics for streams (throughput, latency, errors, lag)
- [ ] Dashboard stream status panel
- [ ] Dashboard stream topology visualization with live metrics
- [ ] Hot-reload for streaming pipelines (drain → recompile → restart)

### Phase 5: External Connectors

**Scope:** Kafka, file, gRPC connectors as separate modules

- [ ] `constellation-connector-kafka` module (fs2-kafka)
- [ ] `constellation-connector-file` module (fs2-io)
- [ ] `constellation-connector-grpc` module (fs2-grpc)
- [ ] Connector documentation and examples

---

## Example: End-to-End Streaming Pipeline

### Pipeline Definition (`user-enrichment.cst`)

```constellation
# Type definitions
type UserEvent = { userId: String, action: String, timestamp: Int }
type UserProfile = { userId: String, name: String, tier: String }
type EnrichedEvent = { userId: String, action: String, name: String, tier: String }

# Inputs and outputs (mode-agnostic)
in events: UserEvent
in profiles: UserProfile

# Processing
enriched = Enrich(events + profiles)
scored = ScoreEvent(enriched) with retry: 2, timeout: 5s

# Fan-out: high-value events get extra processing
premium = scored when scored.tier == "premium"
alert = AlertTeam(premium) with fallback: { alerted: false }

out scored
out alert
```

### Batch Deployment (existing, unchanged)

```scala
val result = runtime.run(compiledPipeline, Map(
  "events"   -> CValue.CMap(Vector(/* single event */)),
  "profiles" -> CValue.CMap(Vector(/* single profile */))
))
// Returns: DataSignature with scored + alert outputs
```

### Streaming Deployment (new)

```scala
val config = StreamPipelineConfig(
  bindings = Map(
    "events"   -> SourceBinding("kafka", Map("topic" -> "user-events")),
    "profiles" -> SourceBinding("kafka", Map("topic" -> "user-profiles")),
    "scored"   -> SinkBinding("kafka", Map("topic" -> "scored-events")),
    "alert"    -> SinkBinding("webhook", Map("url" -> "https://alerts.example.com"))
  )
)

val graph = streamCompiler.wire(compiledPipeline, config, registry)
graph.stream.compile.drain  // Runs until shutdown signal
```

### With Streaming Options

```constellation
in events: UserEvent

# Windowed aggregation
window_stats = ComputeWindowStats(events) with
    window: tumbling(5min),
    checkpoint: 1min

# Batched inference
scored = BatchScorer(events) with
    batch: 100,
    batch_timeout: 2s,
    concurrency: 4

out window_stats
out scored
```

---

## Open Questions

### 1. Should `Stream<T>` be a first-class CType?

**Current leaning:** No — keep streaming as a runtime concern. Pipelines are typed by their element type, and the execution mode determines whether values are singular or continuous.

**Trade-off:** A first-class `Stream<T>` type would let the compiler distinguish between a module that processes one element and one that processes a continuous feed. Without it, this distinction is purely by convention and deployment config.

### 2. How does `Candidates<T>` relate to streams?

**Current leaning:** In streaming mode, `Candidates<T>` could naturally represent a windowed batch — the type algebra (`+`, `[]`) already works element-wise. A stream of `Candidates<T>` is effectively a stream of windows.

**Trade-off:** Overloading `Candidates<T>` to mean "batch of elements" in both modes could be elegant or confusing depending on whether the implicit broadcast semantics (`Candidates<T> + Record`) make sense for streaming.

### 3. Backpressure interaction with `throttle` and `concurrency`

**Current leaning:** Same option names, same semantics. `throttle: 100/min` means 100 elements per minute whether batch (token bucket on module calls) or streaming (`stream.metered`). `concurrency: 4` means 4 parallel executions (`parEvalMap(4)`).

**Trade-off:** Streaming may need finer-grained control (per-partition concurrency, adaptive backpressure). Should these be new options or overloads of existing ones?

### 4. Scope: single RFC or phased RFCs?

**Current leaning:** Single RFC with phased implementation (5 phases defined above). The design is coherent enough to present as one vision, and phased delivery manages risk.

**Trade-off:** Separate RFCs per phase would allow independent review cycles and prevent scope creep, but fragment the design narrative.

### 5. State management for stateful streaming modules

Stateful streaming (running aggregations, session windows) requires per-key state that persists across elements. Should Constellation provide a state abstraction (like Flink's `ValueState`), or should stateful modules manage their own state internally?

### 6. Exactly-once semantics

End-to-end exactly-once processing requires coordination between source commits, processing, and sink writes. Should this be a Constellation guarantee (complex, connector-dependent) or a documented best-effort with at-least-once as the default?

---

## Rejected Alternatives

### Modify the existing Runtime instead of adding a StreamCompiler

Changing `Deferred[IO, Any]` to `Stream[IO, CValue]` throughout the runtime would unify the execution model but risk regressing the well-tested batch path. The separate backend approach is safer and allows streaming to evolve independently.

### Embed streaming semantics in the type system

Making `Stream<T>` a CType would force every pipeline to declare whether it's streaming or batch at the language level. This contradicts the goal of dual-mode pipelines and adds complexity to the type checker for minimal benefit.

### Use Kafka Streams or Flink as the streaming backend

These are powerful but introduce massive dependencies, JVM version constraints, and operational complexity. fs2 is already in the dependency tree, composes with Cats Effect, and provides the right abstraction level for Constellation's nanoservice model.

### Connector configuration in the language only (no external config)

Hardcoding connector details in `.cst` files would make pipelines environment-specific and prevent the same pipeline from running in batch and streaming modes. Deployment config as the primary binding mechanism preserves flexibility.

---

## References

- **RFC-014:** Suspendable Execution — suspension/resumption model that complements streaming
- **RFC-015:** Pipeline Lifecycle Management — hot/cold loading, canary, versioning
- **RFC-024:** Module Provider Protocol — polyglot modules compatible with streaming
- **fs2 documentation:** https://fs2.io
- **fs2-kafka:** https://fd4s.github.io/fs2-kafka/
- **Existing streaming infra:** `modules/http-api/.../ExecutionWebSocket.scala` (fs2 already used for event broadcasting)
