# RFC-025: Streaming Pipelines

**Status:** Draft
**Priority:** P1 (Core Runtime / Extensibility)
**Author:** Human + Claude
**Created:** 2026-02-10

---

## Summary

Introduce a **dual-mode execution strategy** for Constellation pipelines: **single mode** (existing request-response execution) and **streaming mode** (new continuous fs2-based execution). Both modes compile from the same `.cst` pipeline definition to the same deterministic DAG IR. The difference is the **interpreter** — a runtime functor that maps the abstract DAG to a concrete execution strategy.

The key design insight is the introduction of **`Seq<T>`** — an abstract ordered collection type in the pipeline language that the interpreter maps differently per mode:

- **Single mode:** `Seq<T>` → `Vector[T]` (finite, synchronous, in-memory)
- **Streaming mode:** `Seq<T>` → `Stream[IO, T]` (unbounded, asynchronous, backpressured)

Every operation on `Seq<T>` (map, filter, concat, fold, batch) has a well-defined mapping in both modes. The existing `List<T>` is retained as an **always-materialized** collection — `Seq<List<T>>` is unambiguous: a sequence of finite chunks. A **connector registry** provides pluggable sources and sinks (Kafka, WebSockets, HTTP SSE, files) for streaming mode.

**Core principle:** One pipeline definition, one DAG IR, two interpreters. The DAG is the shared structure; the execution strategy is the functor.

---

## Motivation

### The Gap

Constellation pipelines currently execute in single mode: all inputs provided upfront, DAG runs to completion, outputs returned. This works well for API-triggered workloads but cannot model:

- **Event-driven processing** — reacting to continuous streams of events (user actions, IoT telemetry, transaction feeds)
- **Real-time enrichment** — augmenting events with context as they flow through (e.g., enrich click events with user profiles)
- **Continuous aggregation** — computing running statistics, windowed counts, or rolling averages over unbounded data
- **Stream routing** — fan-out events to different processing paths based on content, then fan-in results

Today, teams wanting streaming must leave Constellation entirely and build bespoke fs2/Kafka Streams/Flink pipelines, losing all the benefits of Constellation's type safety, DAG optimization, resilience options, and declarative composition.

### What This Enables

A developer can:

1. Write a pipeline in `.cst` using familiar syntax (`in`, `out`, module calls, type algebra)
2. Deploy it in **single mode** (existing behavior, unchanged) OR **streaming mode** (new)
3. Bind inputs/outputs to connectors (Kafka topics, WebSocket channels, HTTP SSE endpoints) via deployment configuration or inline annotations
4. Use stream-aware options (`window`, `batch`, `checkpoint`) when needed, with the compiler validating them
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
| **Same DAG, two interpreters** | A new `StreamCompiler` reads the same `DagSpec` and produces an fs2 Stream graph. The existing `Runtime` single-mode path is untouched — no risk of regression. |
| **`Seq<T>` as the abstract container** | The pipeline language uses `Seq<T>` for ordered collections. The interpreter maps it to `Vector[T]` (single) or `Stream[IO, T]` (streaming). All Seq operations have dual interpretations. |
| **`List<T>` as materialization boundary** | `List<T>` is always a finite, in-memory collection in both modes. `Seq<List<T>>` = a stream of finite chunks. |
| **Scalars are events** | In streaming mode, scalar inputs (`in x: T`) are events pulled from sources. The DAG fires when inputs are available — same suspension semantics as single mode. |
| **Transparent module lifting** | Modules written as `I => IO[O]` are automatically lifted: `vector.map(f)` in single mode, `stream.evalMap(f)` in streaming mode. No module changes required. |
| **Connectors are deployment config** | `in`/`out` declarations remain type-only by default. Connector bindings (Kafka topic, WebSocket path) are a deployment concern, not a language concern. |
| **Fail per-element, not per-stream** | A single failing element must never crash the stream. Error handling is per-element with configurable strategies. |
| **Backpressure by default** | fs2's pull-based model provides backpressure without configuration. |

---

## The Seq Type — Abstract Ordered Collection

### Type System Refactor

The current `CType.CList` is refactored into two distinct types:

| Pipeline Type | CType | Single Mode | Streaming Mode | Purpose |
|---------------|-------|-------------|----------------|---------|
| `Seq<T>` | `CSeq(elementType)` | `Vector[T]` | `Stream[IO, T]` | Abstract sequence — interpreter chooses representation |
| `List<T>` | `CList(elementType)` | `Vector[T]` | `Vector[T]` | Materialized collection — always finite, in-memory |

Composites:

| Composite | Single Mode | Streaming Mode | Use Case |
|-----------|-------------|----------------|----------|
| `Seq<List<T>>` | `Vector[Vector[T]]` | `Stream[IO, Vector[T]]` | Windowed/batched data |
| `T` (scalar) | Value | Event (pulled from source) | Individual data points |
| `List<Seq<T>>` | **Disallowed** | — | Can't materialize unbounded |
| `Seq<Seq<T>>` | **Disallowed** | — | Ambiguous — use `Seq<List<T>>` |

### The Interpreter Functor

The interpreter is a functor from the category of pipeline operations to the category of runtime operations. It maps:

| Seq Operation | Single (`Vector`) | Streaming (`Stream[IO, _]`) |
|---------------|-------------------|-----------------------------|
| `map(f)` | `vector.map(f)` | `stream.map(f)` |
| `filter(p)` | `vector.filter(p)` | `stream.filter(p)` |
| `flatMap(f)` | `vector.flatMap(f)` | `stream.flatMap(f)` |
| `concat (++)` | `v1 ++ v2` | `s1 ++ s2` (sequential append) |
| `interleave` | `v1.zip(v2).flatMap(t => Vector(t._1, t._2))` | `s1.merge(s2)` (non-deterministic) |
| `zip` | `v1.zip(v2)` | `s1.zip(s2)` |
| `fold(z)(f)` | `vector.foldLeft(z)(f)` → scalar `T` | windowed: `stream.fold(z)(f)` per window → scalar events |
| `take(n)` | `vector.take(n)` | `stream.take(n)` |
| `batch(n)` | `vector.grouped(n)` → `Seq<List<T>>` | `stream.chunkN(n)` → `Stream[IO, Vector[T]]` |
| `window(d)` | Identity / compiler info | `stream.groupWithin(n, d)` → `Stream[IO, Vector[T]]` |
| Module apply | `vector.map(module.run)` | `stream.evalMap(module.run)` |

**Operations excluded from Seq** (no streaming counterpart): `size`, `reverse`, `sort`, `indexOf`, random access. These are module-level concerns or `List`-only operations.

### Fold Semantics

`fold` on an unbounded `Seq` has no natural termination. In streaming mode, fold is interpreted as **windowed aggregation** producing scalar events:

```constellation
# In single mode: folds the entire Seq<Int> into one Int
total = Sum(values)

# In streaming mode: folds per-window, emitting one Int per window
# Default window is configurable; explicit window overrides:
total = Sum(values) with window: tumbling(5min)
```

If no `window` is specified in streaming mode, the runtime uses a default window from `StreamOptions.defaultFoldWindow`. The compiler emits an info diagnostic: "fold on Seq<T> in streaming mode uses windowed aggregation."

---

## Scalars as Events — Unified Suspension Semantics

### Core Insight

In both modes, a DAG node fires when **all its inputs are available**. The difference is what "available" means:

- **Single mode:** A value has been provided by the caller
- **Streaming mode:** An event has been pulled from a source

This unifies the execution model. Partial availability → suspension. The existing `SuspendedExecution` / `SuspensionStore` / `SuspensionCodec` infrastructure supports this directly.

### Single Mode — Partial Evaluation

```constellation
in text: String
in count: Int

trimmed = Trim(text)
result = Repeat(trimmed, count)
out result
```

If only `text` is provided:

```
text [provided] ──▶ Trim [fires] ──▶ trimmed [resolved]
                                                         ╲
                                                          Repeat [suspended]
                                                         ╱
count [missing] ────────────────────────────────────────
```

The runtime returns a `DataSignature` with `status = Suspended`, `suspendedState = Some(SuspendedExecution(...))`. The caller resumes via `SuspendableExecution.resume(suspended, additionalInputs = Map("count" -> CValue.CInt(3)))`.

This is already implemented.

### Streaming Mode — Synchronization Points

Same DAG, streaming mode. Scalars are events pulled from sources:

```
text source ──▶ Trim ──▶ trimmed ──╲
                                     join ──▶ Repeat ──▶ result
count source ─────────────────────╱
```

When a `text` event arrives, `Trim` fires immediately. But `Repeat` needs both `trimmed` and `count`. The stream **suspends at the join point** until both inputs have a value — the same semantics as single mode, applied continuously.

**Default join strategy:** `combineLatest` — fire on any new input, use the latest value from the other. This is the most natural behavior for scalars-as-events:

- If `count` rarely changes, it acts like a broadcast constant (new `text` events fire with the current `count`)
- If both change frequently, every new value from either side triggers a new computation

The join strategy is configurable per-input:

```constellation
in events: Seq<UserEvent>
in profiles: Seq<UserProfile> with join: zip   # strict 1:1 pairing
```

| Join Strategy | Behavior | Use Case |
|---------------|----------|----------|
| `combineLatest` | Fire on any input, use latest from others (default) | Event + slowly-changing context |
| `zip` | Pair 1:1 — wait for one from each | Correlated inputs |
| `buffer(timeout)` | Wait up to timeout for all inputs | Loose synchronization |

**Streaming-only.** In single mode, `join` is ignored (all inputs are provided upfront).

---

## Architecture

### Compilation Pipeline

```
                          ┌──────────────────────┐
                          │  Single Mode          │
                          │                       │
                     ┌───▶│  Runtime.run()        │──▶ DataSignature
                     │    │  (Deferred-based)     │    (or SuspendedExecution)
                     │    └──────────────────────┘
                     │
AST → IR → DagSpec ──┤
                     │    ┌──────────────────────┐
                     │    │  Streaming Mode       │
                     │    │                       │
                     └───▶│  StreamCompiler.wire() │──▶ StreamGraph
                          │  (fs2-based)          │    (continuous)
                          └──────────────────────┘
```

The `DagSpec` is the shared intermediate representation. Both interpreters read the same topology, edges, module specs, and inline transforms:

| Concern | Single (`Runtime`) | Streaming (`StreamCompiler`) |
|---------|-------------------|------------------------------|
| Data node | `Deferred[IO, Any]` (resolve once) | `Stream[IO, CValue]` (continuous) |
| Module execution | `deferred.get` → run → `deferred.complete` | `stream.evalMap(run)` or `parEvalMap(n)(run)` |
| Seq transforms | Compute once on full Vector | `stream.map/filter/...` per element |
| Fan-out | Implicit via DAG deps | `stream.broadcastThrough(...)` |
| Fan-in | Record merge (single value) | Configurable: `concat` / `interleave` / `zip` |
| Partial inputs | `SuspendedExecution` (return snapshot) | Join strategy (await at sync point) |
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
    Single │              Streaming
          │                     │
          ▼                     ▼
  Module.Runnable         Module.Runnable
  run: Runtime => IO[Unit]  (same — StreamCompiler wraps it)
                            evalMap lifts IO[Unit] to
                            Stream[IO, CValue] => Stream[IO, CValue]
```

**No module interface changes.** Existing modules work in both modes.

---

## Connector Registry

### Connector Traits

```scala
trait SourceConnector {
  def typeName: String               // e.g. "kafka", "websocket", "http-sse"
  def configSchema: ConnectorSchema  // declares required/optional properties
  def stream(config: ValidatedConnectorConfig): Stream[IO, CValue]
}

trait SinkConnector {
  def typeName: String
  def configSchema: ConnectorSchema
  def pipe(config: ValidatedConnectorConfig): Pipe[IO, CValue, Unit]
}
```

### Connector Configuration & Validation

Connector properties use `Map[String, String]` as the wire format, but each connector declares a **schema** that is validated eagerly at deployment time:

```scala
case class ConnectorSchema(
  required: Map[String, PropertyType],
  optional: Map[String, PropertyType],
  description: String
)

enum PropertyType {
  case StringProp(default: Option[String] = None)
  case IntProp(default: Option[Int] = None, min: Int = 0, max: Int = Int.MaxValue)
  case DurationProp(default: Option[FiniteDuration] = None)
  case EnumProp(allowed: Set[String], default: Option[String] = None)
}

case class ConnectorConfig(properties: Map[String, String])

final class ValidatedConnectorConfig private[connector] (
  val properties: Map[String, String]
)

object ConnectorConfig {
  def validate(
    raw: ConnectorConfig,
    schema: ConnectorSchema
  ): Either[List[ConnectorConfigError], ValidatedConnectorConfig]
}
```

Misconfigured connectors fail fast at `StreamCompiler.wire()` time with clear error messages.

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

External connectors ship as separate sbt modules to avoid forcing dependencies on users who don't need them.

---

## Deployment Configuration

### Connector Bindings

When deploying a pipeline in streaming mode, a **binding configuration** maps `in`/`out` declarations to connectors:

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
    maxConcurrency = 16,
    defaultFoldWindow = 5.minutes
  ),
  dlq = Some(SinkBinding("kafka", Map("topic" -> "pipeline-dlq")))
)

val graph: IO[StreamGraph] =
  StreamRuntime.deploy(compiledPipeline, streamConfig, connectorRegistry)
```

### Hot/Cold Loading Integration

Extends the existing pipeline lifecycle (RFC-015):

| Operation | Single (existing) | Streaming (new) |
|-----------|-------------------|-----------------|
| Cold load | Compile + store in `PipelineStore` | Same + wire stream graph |
| Hot load | Recompile + swap `LoadedPipeline` | Gracefully drain old stream, start new |
| Hot reload | Replace in-memory pipeline | Drain → recompile → reconnect to same connectors |
| Canary | Traffic splitting by request | Traffic splitting by partition/key range |

**Graceful hot-reload for streams:**

```
1. New pipeline version compiled and validated
2. Type compatibility check (see below)
3. New stream graph wired but not started
4. Old stream signaled to drain (finishes in-flight elements)
5. Old stream completes, resources released
6. New stream starts consuming from last committed offset
7. Health check confirms new stream is processing
```

### Schema Evolution on Hot-Reload

The compiler checks **type compatibility** between old and new pipeline versions:

| Change | Allowed? | Rationale |
|--------|----------|-----------|
| Input type unchanged | Yes | Safe |
| Input type widened (new optional fields) | Yes | Backwards-compatible |
| Input type narrowed (fields removed) | **No** | Connector data may not match |
| Input type changed (different fields) | **No** | Deploy as new stream instead |
| Output type changed | Yes (with warning) | Downstream consumers may need updating |
| New inputs added | **No** | No connector binding exists |
| Inputs removed | Yes | Unused connector is disconnected |

Incompatible reloads are rejected with a clear error listing the breaking changes.

```scala
sealed trait ReloadCheck
object ReloadCheck {
  case object Compatible extends ReloadCheck
  case class IncompatibleInputs(breaking: Map[String, (CType, CType)]) extends ReloadCheck
  case class MissingBindings(unboundInputs: Set[String]) extends ReloadCheck
}
```

---

## Language Extensions

### Seq Operations as `with`-Clause Options

All new options are **additive** to the existing 12 options. In single mode they are ignored (with an optional compiler info diagnostic) unless they have a sensible single-mode interpretation.

#### Batching — `Seq<T> → Seq<List<T>>`

```constellation
# Group elements into finite chunks
processed = HeavyModel(events) with
    batch: 50,           # collect 50 elements
    batch_timeout: 2s    # or flush after 2s (streaming only)
```

| Mode | Interpretation |
|------|---------------|
| Single | `vector.grouped(50).toVector` → `Vector[Vector[T]]` |
| Streaming | `stream.groupWithin(50, 2.seconds)` → `Stream[IO, Vector[T]]` |

Module receives `List<T>` (finite chunk) in both modes.

#### Windowing — `Seq<T> → Seq<List<T>>`

```constellation
# Time-based grouping
stats = ComputeStats(events) with window: tumbling(5min)

# Sliding window
rolling = RollingAvg(readings) with window: sliding(10min, slide: 1min)

# Count-based window
batch_result = ProcessBatch(items) with window: count(100)
```

| Mode | Interpretation |
|------|---------------|
| Single | `count(N)` works (same as batch). Time-based windows: compiler info, treated as identity. |
| Streaming | `stream.groupWithin(n, d)` or custom windowing → `Stream[IO, Vector[T]]` |

Module receives `List<T>` (the window contents).

#### Window vs. Batch: Mutually Exclusive

Both produce `Seq<List<T>>` but serve different purposes:

| Option | Purpose | Grouping strategy |
|--------|---------|-------------------|
| `window` | **Semantic aggregation** — compute over a meaningful boundary | Time-based or count-based |
| `batch` | **Efficiency chunking** — reduce per-element overhead | Size-based with timeout |

The compiler rejects `window` and `batch` on the same module call.

#### Fan-In — Seq Combination Operators

Fan-in operations are `Seq<T> × Seq<T> → Seq<T>` operations:

```constellation
# Sequential append (default for ++)
combined = eventsA ++ eventsB

# Non-deterministic interleave
combined = Merge(eventsA, eventsB) with merge: interleave

# Strict 1:1 pairing
paired = Zip(eventsA, eventsB)
```

| Operator | Single | Streaming |
|----------|--------|-----------|
| `++` (concat) | `v1 ++ v2` | `s1 ++ s2` (drain first, then second) |
| `interleave` | Alternating elements | `s1.merge(s2)` (non-deterministic) |
| `zip` | `v1.zip(v2)` | `s1.zip(s2)` (backpressure faster) |

#### Checkpointing

```constellation
result = Process(events) with checkpoint: 30s
```

Controls how frequently source offsets are committed. In single mode: ignored.

### Annotation Syntax (`@source` / `@sink`)

Optional connector hints on declarations. Non-binding — deployment config overrides.

```constellation
@source(kafka, topic: "user-events", group: "enrichment")
in events: Seq<UserEvent>

@sink(kafka, topic: "enriched-events")
out enriched
```

#### Parser Grammar

```
Annotation     = '@' Identifier '(' AnnotationArgs ')'
AnnotationArgs = Identifier ( ',' KeyValueArg )*
KeyValueArg    = Identifier ':' Literal
```

#### AST Impact

Extends the existing `sealed trait Annotation` with new cases:

```scala
// New annotation cases (alongside existing Annotation.Example)
final case class Source(
  connector: String,
  properties: Map[String, Located[Expression]]
) extends Annotation

final case class Sink(
  connector: String,
  properties: Map[String, Located[Expression]]
) extends Annotation
```

`OutputDecl` gains an `annotations: List[Annotation] = Nil` field (matching `InputDecl`).

---

## Stream Compilation

### StreamCompiler

```scala
object StreamCompiler {

  case class StreamGraph(
    stream: Stream[IO, Unit],
    metrics: StreamMetrics,
    shutdown: IO[Unit]
  )

  def wire(
    dag: DagSpec,
    modules: Map[UUID, Module.Uninitialized],
    moduleOptions: Map[UUID, ModuleCallOptions],
    sources: Map[String, Stream[IO, CValue]],
    sinks: Map[String, Pipe[IO, CValue, Unit]],
    dlq: Option[Pipe[IO, CValue, Unit]],
    config: StreamOptions
  ): IO[StreamGraph]
}
```

### StreamMetrics

```scala
case class StreamMetrics(
  elementsIn: Ref[IO, Long],
  elementsOut: Ref[IO, Long],
  elementsFailed: Ref[IO, Long],
  elementsDlq: Ref[IO, Long],
  startedAt: Instant,
  perModule: Map[UUID, ModuleStreamMetrics],
  def throughputPerSecond: IO[Double],
  def snapshot: IO[StreamMetricsSnapshot]
)

case class ModuleStreamMetrics(
  moduleName: String,
  processed: Ref[IO, Long],
  failed: Ref[IO, Long],
  latencyPercentilesNanos: Ref[IO, PercentileTracker]
)

// Serializable snapshot
case class StreamMetricsSnapshot(
  streamId: String,
  uptimeSeconds: Long,
  elementsIn: Long,
  elementsOut: Long,
  elementsFailed: Long,
  elementsDlq: Long,
  throughputPerSecond: Double,
  modules: Map[String, ModuleMetricsSnapshot]
)

case class ModuleMetricsSnapshot(
  processed: Long,
  failed: Long,
  p50LatencyMs: Double,
  p99LatencyMs: Double
)
```

**Integration with existing `/metrics`:**

```json
{
  "cache": { ... },
  "scheduler": { ... },
  "server": { ... },
  "streams": {
    "active": 3,
    "totalElementsProcessed": 1542890,
    "streams": {
      "stream-abc123": {
        "pipeline": "user-enrichment",
        "uptimeSeconds": 3600,
        "throughputPerSecond": 428.5,
        "elementsIn": 1542890,
        "elementsFailed": 12,
        "elementsDlq": 8
      }
    }
  }
}
```

### Wiring Algorithm

1. **Validate connector configs** — check all bindings against connector schemas, fail fast
2. **Identify source nodes** — `DagSpec.userInputDataNodes` → bind to source streams
3. **Topological sort** — same as single mode, determines wiring order
4. **For each data node:**
   - If source node → use bound `Stream[IO, CValue]`
   - If inline Seq transform → `stream.map/filter/flatMap(transform)`
   - If module output → `stream.evalMap(module.run)` (or `parEvalMap` if `concurrency` set)
5. **Apply Seq options:**
   - `window` → `stream.groupWithin(n, d)` → `Stream[IO, Vector[T]]`
   - `batch` → `stream.chunkN(n)` with timeout → `Stream[IO, Vector[T]]`
   - `throttle` → `stream.metered(rate)`
   - `retry` → per-element retry with backoff
   - `timeout` → per-element timeout
   - `fallback` → per-element fallback value
   - `checkpoint` → periodic offset commit via `Stream.fixedDelay`
6. **Wrap each module step in error handler**
7. **Wire join points** — multi-input nodes use configured join strategy (`combineLatest` default)
8. **Connect sink nodes** → pipe to sinks
9. **Compose** — all streams merged into `Stream[IO, Unit]` with resource management

### Fan-Out

```
source ──┬──▶ ModuleA ──▶ ...
         ├──▶ ModuleB ──▶ ...
         └──▶ ModuleC ──▶ ...
```

Source stream is broadcast via `stream.broadcastThrough(...)`. Each consumer runs in its own fiber.

**Backpressure:** Slowest consumer governs source pace (fs2 default, only safe choice). If a branch is consistently slow:

1. **Increase concurrency** — `result = SlowModule(x) with concurrency: 8`
2. **Decouple via connector** — route through a persistent queue for independent consumption

### Fan-In

```
streamA ──┐
           ├──▶ concat/interleave/zip ──▶ Module ──▶ ...
streamB ──┘
```

Combination strategy is determined by the Seq operator used (`++`, `interleave`, `zip`).

---

## Error Handling & Fault Tolerance

Core principle: **a failing element must never crash the stream**.

### Per-Element Error Wrapping

```scala
inputStream.evalMap { element =>
  module.run(element).attempt.flatMap {
    case Right(result) => IO.pure(result)
    case Left(error)   => applyErrorStrategy(element, error, options)
  }
}
```

### Error Strategies

| Strategy | Single mode | Streaming mode |
|----------|------------|----------------|
| `on_error: propagate` | Re-throw, fail pipeline | Re-throw, terminate stream |
| `on_error: skip` | Return zero value | Drop element, continue |
| `on_error: log` | Log + return zero value | Log + drop element, continue |
| `on_error: wrap` | Wrap in ErrorResult | Emit ErrorResult downstream, continue |
| `on_error: dlq` | Same as wrap | Route to Dead Letter Queue, continue |

### Dead Letter Queue (DLQ)

Failed elements are serialized as `CValue` (a `CMap` with element, error, module name, timestamp, attempt count) and routed to a DLQ sink configured at deployment level:

```scala
val config = StreamPipelineConfig(
  bindings = Map(...),
  dlq = Some(SinkBinding("kafka", Map("topic" -> "pipeline-dlq")))
)
```

```constellation
enriched = Enrich(events) with retry: 3, on_error: dlq
```

If no DLQ is configured but `on_error: dlq` is used, falls back to `log` behavior with a compiler warning.

### Circuit Breaking

Streams support a circuit breaker to prevent poisoned streams from flooding the DLQ:

```scala
case class StreamOptions(
  checkpointInterval: Option[FiniteDuration] = None,
  maxConcurrency: Int = 16,
  maxConsecutiveErrors: Int = 100,
  defaultFoldWindow: FiniteDuration = 5.minutes
)
```

When consecutive errors reach the threshold:
1. Stream **pauses** — stops pulling from source
2. `StreamEvent.CircuitOpen` emitted to metrics/dashboard
3. After cooldown (`30s` default), retries one element (half-open)
4. Success → circuit closes, resume. Failure → double cooldown (capped at `5min`)

### Error Isolation in Fan-Out

Each branch has independent error handling. A branch crashing (`on_error: propagate`) terminates only that branch's fiber — other branches and the source continue. Dashboard shows which branch is down.

---

## Delivery Guarantees

| Level | Guarantee | When |
|-------|-----------|------|
| **At-most-once** | Elements may be lost on failure | Default for `memory` connector |
| **At-least-once** | Elements may be reprocessed; none lost | Default for persistent connectors |
| **Exactly-once** | Each element processed exactly once | Phase 5, connector-specific |

### At-Least-Once Protocol

```
1. Pull element from source (do NOT commit offset)
2. Process through pipeline
3. Write to ALL sinks (fan-out: wait for every branch)
4. Commit source offset
```

On failure at step 2 or 3, element is not committed. On restart, source replays from last committed offset. Modules should be idempotent or duplicate-tolerant.

### Checkpoint Interaction

| Setting | Behavior | Trade-off |
|---------|----------|-----------|
| `checkpoint: 1s` | Commit every second | Low replay window, higher overhead |
| `checkpoint: 1min` | Commit every minute | Up to 1 min replay on failure |
| No checkpoint | Commit per-element | Safest, highest overhead |

### Exactly-Once (Phase 5)

Requires transactional source + sink coordination. Connector-specific (e.g., Kafka transactions). Constellation will introduce an `exactly_once: true` option that requires compatible connectors and fails at deployment time otherwise.

---

## State Management

### Phase 1-3: Stateless Streaming

All streaming modules are **stateless per invocation**. A module receives one element (or one `List<T>` for windowed/batched calls) and produces one output. No state persists between elements.

This covers: enrichment, transformation, filtering, routing, and windowed aggregation (the window contents arrive as `List<T>`).

### Phase 5: Stateful Streaming

For per-key state across elements (running counters, session windows, deduplication), a future phase introduces `StreamModule[S]`:

```scala
trait StreamModule[S] {
  def initialState: S
  def process(element: CValue, state: S): IO[(CValue, S)]
}
```

Deferred to its own RFC once the stateless streaming foundation is proven.

---

## Testing Strategy

### StreamTestKit

```scala
import io.constellation.stream.testing._

class UserEnrichmentStreamTest extends AnyFlatSpec with Matchers {

  "streaming pipeline" should "enrich events" in {
    val testKit = StreamTestKit.fromPipeline(compiledPipeline)

    testKit.run {
      testKit.source("events").emit(userEvent("alice", "click"))
      testKit.source("profiles").emit(userProfile("alice", "premium"))

      val results = testKit.sink("scored").take(1, timeout = 5.seconds)
      results should have size 1
      results.head.field("tier") shouldBe CValue.CString("premium")
    }
  }
}
```

### Capabilities

| Capability | API | Purpose |
|------------|-----|---------|
| Emit elements | `source.emit(value)` | Feed test data |
| Collect outputs | `sink.take(n, timeout)` | Assert on results |
| Inject errors | `source.emitError(ex)` | Test error paths |
| Assert DLQ | `testKit.dlq.take(n)` | Verify DLQ routing |
| Assert metrics | `testKit.metrics.snapshot` | Verify counts |
| Time control | `testKit.advanceTime(5.minutes)` | Test windowed aggregation |

### Time Control for Windowed Tests

```scala
"windowed aggregation" should "emit results after window closes" in {
  val testKit = StreamTestKit.fromPipeline(windowedPipeline)

  testKit.run {
    testKit.source("events").emitAll(List(e1, e2, e3))

    testKit.sink("stats").take(1, timeout = 100.millis) shouldBe empty

    testKit.advanceTime(5.minutes)

    val results = testKit.sink("stats").take(1, timeout = 1.second)
    results should have size 1
  }
}
```

---

## HTTP API Extensions

### New Endpoints

```
POST /pipelines/{name}/deploy-stream
  Body: StreamPipelineConfig (bindings, options)
  Response: { streamId, status: "running" }

DELETE /streams/{streamId}
  Graceful shutdown

GET /streams
  List running streams with health status

GET /streams/{streamId}
  Details: throughput, latency, errors, circuit breaker state

GET /streams/{streamId}/metrics
  Prometheus-format metrics
```

### Dashboard Integration

- Stream status panel with live throughput/latency gauges
- Stream topology view with per-edge element counts and flow rates
- Connector health — source lag, sink throughput, error rates
- Hot-reload controls — deploy new version, drain, rollback
- Circuit breaker status per module

---

## Implementation Phases

### Phase 1: Core Streaming Backend

**Scope:** `StreamCompiler` + `Seq<T>` type + memory connectors + error handling + testing

- [ ] `CType.CSeq` — new abstract sequence type in core
- [ ] Redefine `CType.CList` as always-materialized collection
- [ ] `StreamCompiler.wire()` — converts DagSpec to fs2 Stream graph
- [ ] Module lifting — `evalMap` wrapper for existing modules
- [ ] Seq transform streaming — `stream.map/filter/flatMap` per element
- [ ] Join point wiring — `combineLatest` default for multi-input nodes
- [ ] Per-element error handling — all `on_error` strategies including `dlq`
- [ ] Circuit breaker — `maxConsecutiveErrors` with pause/resume
- [ ] Memory source/sink connectors
- [ ] `StreamGraph` lifecycle — start, graceful shutdown
- [ ] `StreamMetrics` — atomic counters, per-module breakdown, snapshot API
- [ ] `StreamTestKit` — emit, collect, assert, error injection, time control
- [ ] Integration tests with existing example pipelines in streaming mode

### Phase 2: Connector Registry + Built-In Connectors

**Scope:** Connector SPI + validation + WebSocket + HTTP SSE

- [ ] `SourceConnector` / `SinkConnector` traits with `configSchema`
- [ ] `ConnectorSchema` and `ConnectorConfig.validate()`
- [ ] `ConnectorRegistry` builder API
- [ ] WebSocket source/sink
- [ ] HTTP SSE source connector
- [ ] Connector health checks
- [ ] `StreamPipelineConfig` binding configuration
- [ ] At-least-once delivery (offset commit protocol)

### Phase 3: Language Extensions

**Scope:** New `with`-clause options + Seq operators + annotations

- [ ] Parser: `window`, `batch`, `batch_timeout`, `checkpoint` options
- [ ] Parser: `@source` / `@sink` annotation syntax
- [ ] Parser: `join` option for input declarations
- [ ] AST: `Annotation.Source`, `Annotation.Sink` (extend existing sealed trait)
- [ ] AST: `OutputDecl.annotations` field
- [ ] AST: Extend `ModuleCallOptions` with streaming fields
- [ ] Type checker: Validate `Seq<T> → Seq<List<T>>` for windowed/batched modules
- [ ] Type checker: Enforce `window` / `batch` mutual exclusivity
- [ ] Type checker: Disallow `List<Seq<T>>` and `Seq<Seq<T>>` composites
- [ ] Compiler: Streaming option validation (info in single mode, error if invalid in streaming)
- [ ] Fold default window handling — compiler info diagnostic

### Phase 4: HTTP API + Dashboard + Hot-Reload

**Scope:** Stream management endpoints + dashboard + schema evolution

- [ ] `/pipelines/{name}/deploy-stream` endpoint
- [ ] `/streams` CRUD and monitoring endpoints
- [ ] Prometheus-format stream metrics
- [ ] Dashboard stream status panel
- [ ] Dashboard stream topology with live metrics
- [ ] Schema evolution check on hot-reload (`ReloadCheck`)
- [ ] Graceful drain → recompile → restart

### Phase 5: External Connectors + Stateful Streaming + Exactly-Once

**Scope:** Kafka, file, gRPC + stateful modules + transactional delivery

- [ ] `constellation-connector-kafka` (fs2-kafka)
- [ ] `constellation-connector-file` (fs2-io)
- [ ] `constellation-connector-grpc` (fs2-grpc)
- [ ] Exactly-once delivery for Kafka → Kafka
- [ ] `StreamModule[S]` trait for stateful streaming (separate RFC)
- [ ] `checkpoint_backend: "rocksdb"` for durable state

---

## Example: End-to-End Pipeline (Both Modes)

### Pipeline Definition (`user-enrichment.cst`)

```constellation
type UserEvent = { userId: String, action: String, timestamp: Int }
type UserProfile = { userId: String, name: String, tier: String }

in events: Seq<UserEvent>
in profiles: Seq<UserProfile>

# Processing — identical in both modes
enriched = Enrich(events, profiles)
scored = ScoreEvent(enriched) with retry: 2, timeout: 5s, on_error: dlq

# Fan-out
premium = scored when scored.tier == "premium"
alert = AlertTeam(premium) with fallback: { alerted: false }

# Windowed aggregation
stats = ComputeStats(events) with window: tumbling(5min)

out scored
out alert
out stats
```

### Single Mode

```scala
val result = runtime.run(compiledPipeline, Map(
  "events"   -> CValue.CList(Vector(event1, event2, event3)),
  "profiles" -> CValue.CList(Vector(profile1, profile2))
))
// Returns: DataSignature with scored, alert, stats outputs
// stats = ComputeStats over the full list (window ignored in single mode)
```

### Streaming Mode

```scala
val config = StreamPipelineConfig(
  bindings = Map(
    "events"   -> SourceBinding("kafka", Map("topic" -> "user-events")),
    "profiles" -> SourceBinding("kafka", Map("topic" -> "user-profiles")),
    "scored"   -> SinkBinding("kafka", Map("topic" -> "scored-events")),
    "alert"    -> SinkBinding("webhook", Map("url" -> "https://alerts.example.com")),
    "stats"    -> SinkBinding("kafka", Map("topic" -> "event-stats"))
  ),
  dlq = Some(SinkBinding("kafka", Map("topic" -> "enrichment-dlq")))
)

val graph = StreamRuntime.deploy(compiledPipeline, config, registry)
graph.stream.compile.drain  // Runs continuously
```

### Testing

```scala
class UserEnrichmentTest extends AnyFlatSpec with Matchers {

  "pipeline in streaming mode" should "enrich and score events" in {
    val testKit = StreamTestKit.fromPipeline(compiledPipeline)

    testKit.run {
      testKit.source("events").emit(clickEvent)
      testKit.source("profiles").emit(aliceProfile)

      val scored = testKit.sink("scored").take(1, timeout = 5.seconds)
      scored.head.field("tier") shouldBe CValue.CString("premium")

      testKit.dlq.take(1, timeout = 200.millis) shouldBe empty
    }
  }

  "windowed stats" should "aggregate per window" in {
    val testKit = StreamTestKit.fromPipeline(compiledPipeline)

    testKit.run {
      testKit.source("events").emitAll(List(e1, e2, e3))
      testKit.advanceTime(5.minutes)

      val stats = testKit.sink("stats").take(1, timeout = 1.second)
      stats should have size 1
    }
  }
}
```

---

## Open Questions

### 1. Key-based joins

When joining `Seq<UserEvent>` with `Seq<UserProfile>`, should the pipeline language support key-based matching (`join events and profiles on userId`)? Or is this a module concern (a `Join` module that takes two inputs and matches by key)?

### 2. Seq operators as built-in modules vs. language-level syntax

Should `interleave`, `zip`, `concat` be:
- **(A)** Language-level operators with parser support (new syntax)
- **(B)** Built-in pseudo-modules (`Merge(a, b)`, `Zip(a, b)`) that use existing function call syntax
- **(C)** `with`-clause options on input declarations

### 3. `Seq<T>` backward compatibility migration path

Although backward compatibility is not required, what is the migration path for existing `.cst` files using `List<T>`? Options:
- **(A)** `List<T>` keeps current semantics (always materialized), `Seq<T>` is additive
- **(B)** Existing `List<T>` syntax is automatically interpreted as `Seq<T>`, and `List<T>` requires an explicit `@materialized` annotation or new syntax like `[T]`

### 4. Guard (`when`) streaming semantics

In single mode, `x when condition` returns `Optional<T>`. In streaming mode, should it:
- **(A)** Filter (drop non-matching elements from the Seq) — more natural for streams
- **(B)** Wrap in Optional (consistent with single mode) — preserves element count

### 5. Nested Seq validation

Should `Seq<Seq<T>>` be a compiler error or silently flattened to `Seq<T>`? The RFC currently disallows it, but `flatMap` naturally produces nested sequences.

---

## Resolved Design Decisions

### Streaming is NOT a type system concern

The type system does not introduce `Stream<T>`. Instead, `Seq<T>` is the abstract container, and the execution strategy determines its runtime representation.

### Delivery guarantees are connector-dependent

At-least-once for persistent connectors, at-most-once for memory. Exactly-once is Phase 5.

### Single RFC, phased implementation

5 phases, each independently deliverable.

### State management deferred to Phase 5

Stateless streaming covers the majority of use cases. Stateful streaming (`StreamModule[S]`) gets its own RFC.

---

## Rejected Alternatives

### Modify the existing Runtime instead of adding a StreamCompiler

Risk of regressing the well-tested single-mode path. Separate interpreter is safer.

### Embed streaming semantics in the type system

Making `Stream<T>` a CType would force every pipeline to declare its mode. `Seq<T>` as an abstract container that both modes interpret is cleaner.

### Use Kafka Streams or Flink as the streaming backend

Massive dependencies. fs2 is already in the dependency tree and composes with Cats Effect.

### Connector configuration in the language only

Would make pipelines environment-specific. Deployment config preserves flexibility.

### Implicit buffering on fan-out

Masks backpressure problems. Explicit backpressure is safer.

### Allow `window` and `batch` on same module call

Ambiguous semantics. Mutually exclusive is simpler.

---

## References

- **RFC-014:** Suspendable Execution — `SuspendedExecution`, `SuspensionStore`, `SuspensionCodec` (already implemented)
- **RFC-015:** Pipeline Lifecycle Management — hot/cold loading, canary, versioning
- **RFC-024:** Module Provider Protocol — polyglot modules compatible with streaming
- **fs2 documentation:** https://fs2.io
- **fs2-kafka:** https://fd4s.github.io/fs2-kafka/
- **Existing streaming infra:** `modules/http-api/.../ExecutionWebSocket.scala` (fs2 already used)
- **Existing suspension:** `modules/runtime/.../SuspendedExecution.scala`, `SuspendableExecution.scala`
