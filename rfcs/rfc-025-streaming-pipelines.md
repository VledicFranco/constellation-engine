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

Every operation on `Seq<T>` (map, filter, concat, fold, collect) has a well-defined mapping in both modes. The existing `List<T>` is retained as an **always-materialized** collection — `Seq<List<T>>` is unambiguous: a sequence of finite chunks. The `collect` stdlib function is the **materialization boundary** — it converts `Seq<T>` to `Seq<List<T>>` (identity-ish in single mode, windowed grouping in streaming mode), and the compiler auto-inserts it when a module expecting `List<T>` receives `Seq<T>`. `Seq<T>` subsumes the legacy `Candidates<T>` alias — all instances of `Candidates` are renamed to `Seq` (clean refactor, no migration needed).

Existing language constructs lift naturally into streaming semantics: **union types** (`A | B`) become event routing via `match`, **guards** (`when`) become element-wise filters, **merge** (`+`) becomes type-driven enrichment, and **structural dot syntax** (`items.field`) acts as an implicit element-wise lambda. A **connector registry** provides pluggable sources and sinks (Kafka, WebSockets, HTTP SSE, files) for streaming mode.

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
| **Existing constructs lift naturally** | Guards become filters, merge becomes enrichment, match becomes routing, structural dot syntax becomes element-wise mapping. No new streaming-specific syntax for common patterns. |
| **Union types are event routing** | `match` on a union-typed input becomes a stream router in streaming mode. `Optional<T>` (conceptually `T \| None`) gives guards natural event semantics: `None` = no emission. |
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

**Runtime value representation:** `CValue.CSeq(value: Vector[CValue])` wraps a `Vector[CValue]` in single mode — structurally identical to `CValue.CList`. The distinction is at the **type level** (`CType.CSeq` vs `CType.CList`), which the interpreter uses to choose execution strategy. In streaming mode, `CSeq` never materializes as a `CValue` — it exists only as a `Stream[IO, CValue]` edge in the stream graph. Pattern matching in module code sees `Vector[CValue]` in both cases; the type tag determines whether the interpreter treats it as a finite collection or an abstract sequence.

**Migration:** `Seq<T>` subsumes the legacy `Candidates<T>` alias. All existing uses of `Candidates` in the codebase and `.cst` files are renamed to `Seq`. This is a clean refactor — no migration path or backward compatibility shims needed. The parser accepts both `Seq<T>` and `Candidates<T>` during a transition period, resolving both to `CSeq`.

Composites:

| Composite | Single Mode | Streaming Mode | Use Case |
|-----------|-------------|----------------|----------|
| `Seq<List<T>>` | `Vector[Vector[T]]` | `Stream[IO, Vector[T]]` | Windowed/batched data |
| `T` (scalar) | Value | Event (pulled from source) | Individual data points |
| `List<Seq<T>>` | **Compiler error** | — | Can't materialize unbounded |
| `Seq<Seq<T>>` | **Compiler error** | — | See below |

**Nested Seq:** `Seq<Seq<T>>` is a compiler error. The `flatMap` operation is defined as `map + flatten`, producing `Seq<U>` directly (never `Seq<Seq<U>>`). If nested structure is needed, use `Seq<List<T>>` (stream of finite chunks). This keeps the type system clean and avoids ambiguous runtime representations.

### The Interpreter Functor

The interpreter is a functor from the category of pipeline operations to the category of runtime operations. It maps:

| Seq Operation | Single (`Vector`) | Streaming (`Stream[IO, _]`) |
|---------------|-------------------|-----------------------------|
| `map(f)` | `vector.map(f)` | `stream.map(f)` |
| `filter(p)` | `vector.filter(p)` | `stream.filter(p)` |
| `flatMap(f)` | `vector.flatMap(f)` | `stream.flatMap(f)` |
| `concat (++)` | `v1 ++ v2` | `s1 ++ s2` (sequential append) |
| `interleave` | Round-robin alternating (truncates to shorter) | `s1.merge(s2)` (non-deterministic) |
| `zip` | `v1.zip(v2)` (truncates to shorter) | `s1.zip(s2)` (terminates on shorter) |
| `fold(z)(f)` | `vector.foldLeft(z)(f)` → scalar `T` | windowed: `stream.fold(z)(f)` per window → scalar events |
| `take(n)` | `vector.take(n)` | `stream.take(n)` |
| `batch(n)` | `vector.grouped(n)` → `Seq<List<T>>` | `stream.chunkN(n)` → `Stream[IO, Vector[T]]` |
| `window(d)` | Identity / compiler info | `stream.groupWithin(n, d)` → `Stream[IO, Vector[T]]` |
| `collect` | `Vector(vector)` — wraps full vector as single List | Windowed grouping → `Stream[IO, Vector[T]]` |
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

If no `window` is specified in streaming mode, the runtime uses a default window from `StreamOptions.defaultMaterializationWindow` (the same default used by implicit `collect` boundaries). The compiler emits an info diagnostic: "fold on Seq<T> in streaming mode uses windowed aggregation."

**DAG IR type:** Fold always produces scalar `T` in the DAG IR — the type system sees `Seq<Int> → Int`, not `Seq<Int> → Seq<Int>`. In single mode, this is literally one value. In streaming mode, the interpreter emits one `T` event per window close — the "scalar" fires repeatedly, once per window. Downstream nodes see individual `T` events, not a collection. This preserves type compatibility: a downstream module expecting `Int` works identically in both modes. The difference is *cardinality* (one value vs. one-per-window), not *type*.

### Materialization Boundaries — `collect`

The `collect` function is the **materialization boundary** between `Seq<T>` (abstract, potentially unbounded) and `List<T>` (finite, in-memory). It lives in `stdlib.collection` alongside `filter`, `map`, `all`, `any` — same namespace, same calling convention. Unlike the HOFs (which take lambdas), `collect` is a **pseudo-module** — compiler-inlined via `InlineTransform` but without lambda extraction, same pattern as `Interleave` and `Zip`.

```constellation
use stdlib.collection

in events: Seq<UserEvent>

# Explicit materialization with window spec
batched = collect(events, tumbling(5min))       # Seq<T> → Seq<List<T>>
rolling = collect(events, sliding(10min, 1min)) # Seq<T> → Seq<List<T>>
chunks = collect(events, count(100))            # Seq<T> → Seq<List<T>>

# Default materialization (uses deployment-level defaultMaterializationWindow)
batched = collect(events)                       # Seq<T> → Seq<List<T>>
```

**Dual-mode semantics:**

| Expression | Single Mode | Streaming Mode |
|---|---|---|
| `collect(seq)` | `Vector(vector)` — wraps full vector as single-element `Seq<List<T>>` | Groups by `StreamOptions.defaultMaterializationWindow` → `Stream[IO, Vector[T]]` |
| `collect(seq, tumbling(5min))` | Same — one chunk (no time dimension) | `stream.groupWithin(_, 5.min)` → `Stream[IO, Vector[T]]` |
| `collect(seq, count(100))` | `vector.grouped(100)` → multiple chunks | `stream.chunkN(100)` → `Stream[IO, Vector[T]]` |

#### Reusable Materialization Points

`collect` is especially useful when multiple downstream modules need the same materialized chunks — one materialization, shared window boundaries:

```constellation
# One materialization, multiple consumers
batch = collect(events, tumbling(5min))
stats = ComputeStats(batch)       # receives List<UserEvent> per window
summary = Summarize(batch)         # same windows, same boundaries
archive = ArchiveBatch(batch)      # same chunks
```

Without `collect`, each `with window:` on a module call creates its own independent window with potentially different boundaries.

#### Auto-Desugaring Rule

The compiler statically detects **materialization mismatches**: when a module parameter expects `List<T>` (or any non-Seq collection) but receives `Seq<T>`.

**Rule:** When the compiler detects `Module(seq_arg)` where the module's `FunctionSignature` declares the parameter as `CList(T)` but the expression type is `CSeq(T)`:

1. The compiler auto-inserts `collect(seq_arg)` before the module call
2. If `with window:` is specified on the module call → uses that window spec
3. If no window → uses `StreamOptions.defaultMaterializationWindow` and emits an **info diagnostic**: *"Module ComputeStats expects List\<Int\> but receives Seq\<Int\>. Implicit materialization boundary inserted (default window)."*
4. In single mode, the materialization is effectively identity — the module receives the full collection as one chunk

This means existing pipelines "just work" without the user needing to think about materialization. The diagnostic tells them exactly what happened, and they can take explicit control with `collect()` or `with window:` when they need to.

**Example of auto-desugaring:**

```constellation
# User writes:
stats = ComputeStats(events)
# ComputeStats.consumes = Map("values" -> CList(CInt))
# events: Seq<Int>

# Compiler desugars to:
__events_collected = collect(events)     # implicit, uses default window
stats = ComputeStats(__events_collected) # module receives List<Int> per chunk
```

#### Module Lifting Strategy — Summary

The module's input signature determines how the compiler handles a `Seq<T>` argument:

| Module expects | Receives `Seq<T>` | Compiler action | Streaming behavior |
|---|---|---|---|
| Scalar `T` | `Seq<T>` | **Element-wise lift** | `stream.evalMap(module.run)` — one call per element |
| `List<T>` | `Seq<T>` | **Auto-insert `collect`** | `stream.groupWithin(...)` then `evalMap` — one call per chunk |
| `Seq<T>` | `Seq<T>` | **Compiler error in streaming mode** | Modules are `I => IO[O]`, not stream processors (Phase 5: `StreamModule[S]`) |

#### Implementation Details

- **Namespace:** `stdlib.collection` (alongside `filter`, `map`, `all`, `any`)
- **Module name:** `stdlib.builtin.collect` (pseudo-module, not HOF — no lambda argument)
- **IR:** Detected by DagCompiler via module name prefix `stdlib.builtin.*` (separate from `stdlib.hof.*` HOF path). Optional `WindowSpec` argument parsed in Phase 3.
- **DagCompiler:** Produces `InlineTransform.CollectTransform(windowSpec: Option[WindowSpec])` — direct generation, no lambda compilation
- **StreamOptions:** New field `defaultMaterializationWindow: FiniteDuration = 5.minutes`

### Element-Wise Operations & Structural Syntax

Existing element-wise operations on collections lift naturally to Seq. These are already implemented for `List<Record>` in the TypeChecker and extend unchanged to `Seq<Record>`:

| Operation | Expression | Type | Meaning |
|-----------|-----------|------|---------|
| Field access | `items.name` | `Seq<Record> → Seq<T>` | Extract field from each element |
| Projection | `items[name, age]` | `Seq<Record> → Seq<Record>` | Project fields from each element |
| Merge | `items + context` | `Seq<Record> + Record → Seq<Record>` | Merge scalar into each element |
| Comparison | `items.score > 10` | `Seq<Int> → Seq<Boolean>` | Element-wise comparison (scalar broadcast) |

**Structural dot syntax as implicit lambda:** The expression `items.field` is semantically equivalent to `map(items, (x) => x.field)`, but requires no explicit lambda. This point-free style covers the vast majority of element-wise operations. Explicit `Lambda` remains in the AST for complex cases, but structural syntax handles field access, projection, comparison, and composition without it.

**Example — filtering without lambdas:**

```constellation
in items: Seq<{ score: Int, name: String }>

# Structural syntax — no lambda
high_scores = items when items.score > 10

# Equivalent explicit form (still valid)
high_scores = filter(items, (x) => x.score > 10)
```

How the structural version works mechanically:

1. `items: Seq<{score: Int, name: String}>`
2. `items.score` → `Seq<Int>` (element-wise field access)
3. `items.score > 10` → `Seq<Boolean>` (element-wise comparison, scalar broadcast)
4. `items when Seq<Boolean>` → `Seq<{score: Int, name: String}>` (element-wise filter)

### Guard (`when`) on Seq — Element-Wise Filter

The `when` operator has dual semantics depending on operand type:

| Expression | Type | Single Mode | Streaming Mode |
|-----------|------|-------------|----------------|
| `x when cond` (scalar) | `T × Boolean → Optional<T>` | `CSome(x)` or `CNone` | Event filter — emit or swallow |
| `seq when cond` (Seq + scalar Boolean) | `Seq<T> × Boolean → Seq<T>` | All or nothing | All or nothing |
| `seq when seq_cond` (Seq + Seq Boolean) | `Seq<T> × Seq<Boolean> → Seq<T>` | Element-wise filter | `stream.filter(p)` |

For **scalar guards**: `Optional<T>` is conceptually `T | None` (a union type). In streaming mode, `None` means "no event emitted this tick" — the element is silently dropped. `Some(v)` means the event passes through. This gives guards natural filter semantics without any special streaming syntax.

**Coalesce (`??`) as default/fallback:**

```constellation
result = risky_event when is_valid ?? fallback_value
```

- **Single mode:** If guard produces None, use fallback
- **Streaming mode:** If event is filtered out, substitute with fallback value (stream always emits)

---

## Union Types as Event Routing

### Match as Stream Router

The existing `match` expression on union types gains natural streaming semantics:

```constellation
in event: Seq<Success | Failure>

result = match event {
  Success { value } -> ProcessValue(value)
  Failure { error } -> HandleError(error)
}
```

| Mode | Behavior |
|------|----------|
| **Single** | Standard pattern match — one branch executes based on value tag |
| **Streaming** | Stream partition — events tagged `Success` flow to `ProcessValue`, events tagged `Failure` flow to `HandleError` |

The `match` expression compiles to a **stream router** in streaming mode. Each arm becomes a separate stream continuation. No special fan-out syntax needed — the type system determines the routing.

### Branch as Predicate Router

The existing `branch` expression routes by predicate (not by type tag):

```constellation
result = branch {
  score > 90 -> HighPriority(item)
  score > 50 -> MediumPriority(item)
  otherwise  -> LowPriority(item)
}
```

| Mode | Behavior |
|------|----------|
| **Single** | First matching condition wins |
| **Streaming** | Route each event to a processing path based on its condition |

### Interpreter Functor Mapping — Control Flow

| Pipeline Construct | Single Mode | Streaming Mode |
|-------------------|-------------|----------------|
| `match` on `T` (union scalar) | Branch, one executes | Partition stream by tag |
| `match` on `Seq<T>` (union elements) | Element-wise branch | Element-wise stream partition |
| `branch` on scalar | First match wins | Route each event by predicate |
| `when` (guard, scalar) | `Optional<T>` | Event filter (emit/swallow) |
| `when` (guard, Seq) | Element-wise `Optional<T>` | `stream.filter(p)` |
| `??` (coalesce) | Fallback on None | Default on filtered event |

### Optional<T> as Union Semantics

`Optional<T>` is conceptually equivalent to `T | None`. Although `COptional`/`SOptional` remain as distinct types in the implementation (for ergonomics and optimization), the **streaming interpreter treats them with union event semantics**:

- `Some(v)` → event emits `v`
- `None` → no emission (element dropped from stream)

This means every construct that produces `Optional<T>` — guards, coalesce, conditional lookups — automatically works in streaming mode as an element filter.

---

## Merge Operator — Type-Driven Enrichment

### Merge Semantics by Operand Type

The existing `+` (merge) operator already implements four strategies in `MergeTransform`. When extended to `Seq`, the operand types **naturally determine** the streaming join strategy:

| Left | Right | Single Mode | Streaming Mode |
|------|-------|-------------|----------------|
| `Record + Record` | → | Field merge (`lMap ++ rMap`) | Field merge — both are scalar events, join fires per configured strategy (`combineLatest` default) |
| `Seq<Record> + Seq<Record>` | → | Element-wise merge (zip, same length) | `stream.zip(stream).map(merge)` |
| `Seq<Record> + Record` | → | Broadcast scalar to every element | `combineLatest` — each Seq element gets latest scalar merged |
| `Record + Seq<Record>` | → | Broadcast (reversed) | Same as above |

**Zip length divergence:** In single mode, `Seq<Record> + Seq<Record>` throws `IllegalArgumentException` if the vectors have different lengths (existing `MergeTransform` behavior). In streaming mode, `stream.zip` terminates when the shorter stream ends — remaining elements from the longer stream are silently dropped. This is an intentional behavioral divergence: single mode enforces strictness (fail-fast on mismatched data), while streaming mode follows fs2 zip semantics (graceful termination). The `StreamMetrics` counter `elementsDroppedZip` tracks how many elements were discarded due to one side completing early, and the stream emits a `StreamEvent.ZipExhausted(side: "left" | "right")` event for monitoring.

### The Enrichment Pattern

The `Seq<Record> + Record` case is the **streaming enrichment pattern**. In streaming mode, the scalar Record acts as a slowly-changing lookup value (via `combineLatest` join):

```constellation
in events: Seq<UserEvent>
in config: AppConfig        # scalar — changes rarely

# Each event gets the latest config merged in
enriched = events + config
```

- **Single mode:** Every element in the vector gets `config` merged in (broadcast)
- **Streaming mode:** Each new event gets the most recent `config` value merged in. When `config` changes, subsequent events use the new value.

No special enrichment syntax needed — the `+` operator and the type system handle it.

### Key-Based Joins — Module Concern

For matching records by key (e.g., join events with profiles by `userId`), this is a **module-level concern**, not a language operator. Key-based joins require maintaining a stateful lookup table, especially in streaming mode.

```constellation
# Key-based join via module — not the + operator
enriched = JoinByKey(events, profiles, "userId")
```

Rationale:
- Key-based join is inherently stateful (lookup table maintenance)
- The `+` operator is a pure structural merge — adding key semantics would overload its meaning
- A `JoinByKey` module can have richer options (join type, window for temporal joins, default for missing keys)

Key-based joins are a Phase 5 feature (stateful streaming).

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

**Choosing the right join strategy:**

- **`combineLatest` (default)** — best when one input changes rarely relative to the other (e.g., events enriched with slowly-changing config). Be aware that if both inputs are high-throughput event streams, `combineLatest` produces `O(N * M)` outputs (every new event from either side triggers a computation with the latest from the other). If this is not desired, use `zip`.
- **`zip`** — best when inputs have a 1:1 correspondence (e.g., request-response pairs, correlated sensor readings). The faster stream waits for the slower one. Elements are never duplicated or reused.
- **`buffer(timeout)`** — best when inputs arrive in loose batches (e.g., data from multiple APIs with different latencies). Waits up to `timeout` for all inputs to have at least one value, then fires.

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
| Fan-out (by type) | `match` — one branch executes | `match` — stream partition by tag |
| Fan-out (broadcast) | Implicit via DAG deps | `stream.broadcastThrough(...)` |
| Fan-in | Record merge (single value) | `concat` / `Interleave` / `Zip` pseudo-modules |
| Merge (`+`) | Structural field merge | Type-driven: zip (Seq+Seq) or combineLatest (Seq+scalar) |
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
    defaultMaterializationWindow = 5.minutes
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

Module receives `List<T>` (finite chunk) in both modes. In single mode, the Runtime applies `grouped(n)` before invoking the module — the module is called once per chunk. The `batch_timeout` option is ignored in single mode (no time dimension). This means batching is implemented by **both** interpreters, not just the `StreamCompiler`.

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

**Relationship to `collect`:** `with window:` on a module call is syntactic sugar for inserting a `collect` before the module. These two are equivalent:

```constellation
# with window: sugar
stats = ComputeStats(events) with window: tumbling(5min)

# Explicit collect
windowed = collect(events, tumbling(5min))
stats = ComputeStats(windowed)
```

Use `with window:` for one-off windowing on a single module call. Use explicit `collect` when the same materialized chunks feed multiple downstream modules.

#### Window vs. Batch: Mutually Exclusive

Both produce `Seq<List<T>>` but serve different purposes:

| Option | Purpose | Grouping strategy |
|--------|---------|-------------------|
| `window` | **Semantic aggregation** — compute over a meaningful boundary | Time-based or count-based |
| `batch` | **Efficiency chunking** — reduce per-element overhead | Size-based with timeout |

The compiler rejects `window` and `batch` on the same module call.

#### Fan-In — Seq Combination via Pseudo-Modules

Fan-in operations are `Seq<T> × Seq<T> → Seq<T>` operations. The `++` operator remains for concat, while `Interleave` and `Zip` are built-in **pseudo-modules** that use existing function call syntax (no parser changes needed):

```constellation
# Sequential append (language-level operator)
combined = eventsA ++ eventsB

# Non-deterministic interleave (pseudo-module)
combined = Interleave(eventsA, eventsB)

# Strict 1:1 pairing (pseudo-module)
paired = Zip(eventsA, eventsB)
```

| Operator | Single | Streaming |
|----------|--------|-----------|
| `++` (concat) | `v1 ++ v2` | `s1 ++ s2` (drain first, then second) |
| `Interleave(a, b)` | Round-robin alternating (truncates to shorter) | `s1.merge(s2)` (non-deterministic) |
| `Zip(a, b)` | `v1.zip(v2)` → `Seq<(A, B)>` (truncates to shorter) | `s1.zip(s2)` (backpressure faster, terminates on shorter) |

**Length mismatch:** Both `Interleave` and `Zip` truncate to the shorter input in single mode (matching Scala's `zip` behavior). In streaming mode, the stream terminates when either side completes. Use `++` (concat) when all elements from both inputs must be preserved.

Pseudo-modules (`Interleave`, `Zip`, `collect`) are compiler-inlined via the `stdlib.builtin.*` module name prefix — they produce `InlineTransform` nodes in the DAG, not actual module invocations. This is a separate code path from HOFs (`stdlib.hof.*`), which require lambda extraction. Pseudo-modules have no lambdas.

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
AnnotationArgs = ConnectorName ( ',' KeyValueArg )*
ConnectorName  = Identifier | StringLiteral
KeyValueArg    = Identifier ':' Literal
```

**Connector name resolution:** The first argument is the connector name. It can be a bare identifier (`kafka`, `websocket`) or a string literal (`"http-sse"`) for names that aren't valid identifiers (e.g., names containing hyphens). The compiler resolves the name against the `ConnectorRegistry` at deployment time — unrecognized names produce a deployment error, not a compile error (since the registry is a runtime concern).

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
trait StreamMetrics {
  def elementsIn: Ref[IO, Long]
  def elementsOut: Ref[IO, Long]
  def elementsFailed: Ref[IO, Long]
  def elementsDlq: Ref[IO, Long]
  def elementsDroppedZip: Ref[IO, Long]
  def startedAt: Instant
  def perModule: Map[UUID, ModuleStreamMetrics]
  def throughputPerSecond: IO[Double]
  def snapshot: IO[StreamMetricsSnapshot]
}

object StreamMetrics {
  def create(modules: Map[UUID, String]): IO[StreamMetrics]
}

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
   - If `collect` transform → `stream.groupWithin(n, d)` (or `chunkN` for count-based)
   - If inline Seq transform → `stream.map/filter/flatMap(transform)`
   - If module output → `stream.evalMap(module.run)` (or `parEvalMap` if `concurrency` set)
   - If `match` on union → partition stream by tag, wire each arm
   - If `+` merge → type-driven: zip (Seq+Seq) or combineLatest (Seq+scalar)
5. **Apply with-clause options** (see dual-mode mapping below)
6. **Wrap each module step in error handler**
7. **Wire join points** — multi-input nodes use configured join strategy (`combineLatest` default)
8. **Connect sink nodes** → pipe to sinks
9. **Compose** — all streams merged into `Stream[IO, Unit]` with resource management

### With-Clause Dual-Mode Mapping

All 12 existing `with`-clause options have per-element semantics in streaming mode. No new streaming-specific options are needed for the existing set — only additive options (`window`, `batch`, `checkpoint`, `join`) are new.

| Option | Single Mode (current) | Streaming Mode | Notes |
|--------|----------------------|----------------|-------|
| `retry: N` | Retry module on failure | Retry **per element** | Same semantics, continuous |
| `timeout: 5s` | Fail module if >5s | Fail **element** if >5s | Per-element, not stream-wide |
| `delay: 100ms` | Delay before execution | Per-element delay = rate limiting | `stream.metered(100.millis)` |
| `backoff: exponential` | Backoff between retries | Same, per-element retries | Works naturally |
| `fallback: { ... }` | Fallback value on failure | Emit **fallback per failed element** | Stream continues |
| `cache: 30s` | Memoize by input hash | **Deduplication** — same input within TTL returns cached result | See below |
| `cache_backend: "memcached"` | External cache store | Same, survives stream restarts | Cross-restart dedup |
| `throttle: 100/1s` | Rate limiting | `stream.metered(rate)` / token bucket | Same intent |
| `concurrency: 8` | Parallel execution | `parEvalMap(8)` | 8 elements concurrently |
| `on_error: dlq` | Wrap error + log | Route to DLQ, continue | Already specified |
| `lazy: true` | Defer computation | **Ignored** (fs2 is pull-based, inherently lazy) | Compiler info diagnostic |
| `priority: high` | Scheduler priority | **Ignored** (no stream-level priority) | Compiler info diagnostic |

**Cache as stream deduplication:** The `cache` option keys by input CValue hash. In streaming mode, if an element with the same input hash arrives within the TTL, the cached output is emitted without re-invoking the module. This gives automatic deduplication:

```constellation
# Same event within 30s → cached result, module not re-invoked
processed = ExpensiveModel(events) with cache: 30s
```

With `cache_backend: "memcached"`, the dedup cache survives stream restarts — at-least-once reprocessing after restart skips already-processed elements within the TTL window.

### Fan-Out

```
source ──┬──▶ ModuleA ──▶ ...
         ├──▶ ModuleB ──▶ ...
         └──▶ ModuleC ──▶ ...
```

Source stream is broadcast via `stream.broadcastThrough(...)`. Each consumer runs in its own fiber.

**Type-driven fan-out via `match`:** When a union-typed stream is matched, the fan-out is by type tag — each arm receives only its matching elements. This is more efficient than broadcast (no wasted processing).

**Backpressure:** Slowest consumer governs source pace (fs2 default, only safe choice). If a branch is consistently slow:

1. **Increase concurrency** — `result = SlowModule(x) with concurrency: 8`
2. **Decouple via connector** — route through a persistent queue for independent consumption

### Fan-In

```
streamA ──┐
           ├──▶ concat / Interleave / Zip ──▶ Module ──▶ ...
streamB ──┘
```

Combination strategy is determined by the operator or pseudo-module used (`++`, `Interleave(a,b)`, `Zip(a,b)`).

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
  defaultMaterializationWindow: FiniteDuration = 5.minutes
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
- [ ] Rename `Candidates` → `Seq` throughout codebase and parser
- [ ] Redefine `CType.CList` as always-materialized collection
- [ ] Type checker: reject `Seq<Seq<T>>` and `List<Seq<T>>` composites
- [ ] `StreamCompiler.wire()` — converts DagSpec to fs2 Stream graph
- [ ] Module lifting — `evalMap` wrapper for existing modules
- [ ] Seq transform streaming — `stream.map/filter/flatMap` per element
- [ ] Element-wise operations on Seq — field access, projection, merge (lift from List)
- [ ] Guard on Seq — element-wise filter interpretation
- [ ] Merge type-driven join — Seq+Seq=zip, Seq+scalar=combineLatest
- [ ] Join point wiring — `combineLatest` default for multi-input nodes
- [ ] Per-element error handling — all `on_error` strategies including `dlq`
- [ ] Circuit breaker — `maxConsecutiveErrors` with pause/resume
- [ ] Existing with-clause options — per-element streaming semantics (cache as dedup)
- [ ] `collect` pseudo-module — `stdlib.builtin.collect`, `InlineTransform.CollectTransform` (no-arg form only; WindowSpec form in Phase 3)
- [ ] Auto-desugaring — compiler inserts implicit `collect` when module expects `List<T>` but receives `Seq<T>`
- [ ] `StreamOptions.defaultMaterializationWindow` — configurable default for implicit collect and fold
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

**Scope:** New `with`-clause options + Seq operators + annotations + union routing

- [ ] AST: `WindowSpec` sealed trait (`Tumbling`, `Sliding`, `Count`)
- [ ] Parser: `window`, `batch`, `batch_timeout`, `checkpoint` options
- [ ] Parser: `@source` / `@sink` annotation syntax
- [ ] Parser: `join` option for input declarations
- [ ] AST: `Annotation.Source`, `Annotation.Sink` (extend existing sealed trait)
- [ ] AST: `OutputDecl.annotations` field
- [ ] AST: Extend `ModuleCallOptions` with streaming fields
- [ ] `Interleave` and `Zip` pseudo-modules (compiler-inlined via `stdlib.builtin.*` path)
- [ ] `collect` WindowSpec argument form — `collect(seq, tumbling(5min))` (requires WindowSpec parser from above)
- [ ] Type checker: Validate `Seq<T> → Seq<List<T>>` for windowed/batched modules
- [ ] Type checker: Enforce `window` / `batch` mutual exclusivity
- [ ] Compiler: `match` on union Seq → stream partition IR node
- [ ] Compiler: `branch` on Seq → predicate routing IR node
- [ ] Compiler: streaming option validation (info in single mode for `lazy`/`priority`)
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

**Scope:** Kafka, file, gRPC + stateful modules + transactional delivery + key-based joins

> **Note:** Phase 5 items are non-trivial and will require their own RFCs before implementation. Specifically:
> - **Stateful streaming** (`StreamModule[S]`, `JoinByKey`, durable state) — needs its own RFC covering state serialization, checkpointing, recovery semantics, and state migration on pipeline updates.
> - **Exactly-once delivery** — needs its own RFC covering transactional protocols, connector requirements, and failure semantics.
> - **External connectors** (Kafka, gRPC) — may be covered by individual connector RFCs or a single connector SPI RFC depending on scope.

- [ ] `constellation-connector-kafka` (fs2-kafka)
- [ ] `constellation-connector-file` (fs2-io)
- [ ] `constellation-connector-grpc` (fs2-grpc)
- [ ] Exactly-once delivery for Kafka → Kafka (separate RFC)
- [ ] `StreamModule[S]` trait for stateful streaming (separate RFC)
- [ ] `JoinByKey` stateful module for key-based stream joins (separate RFC)
- [ ] `checkpoint_backend: "rocksdb"` for durable state (separate RFC)

---

## Example: End-to-End Pipeline (Both Modes)

### Pipeline Definition (`user-enrichment.cst`)

```constellation
use stdlib.collection

type UserEvent = { userId: String, action: String, timestamp: Int }
type UserProfile = { userId: String, name: String, tier: String }
type ProcessedEvent = { userId: String, action: String, tier: String, score: Int }

in events: Seq<UserEvent>
in profiles: Seq<UserProfile>

# Enrichment — module handles joining by key or strategy
enriched = Enrich(events, profiles)
scored = ScoreEvent(enriched) with retry: 2, timeout: 5s, on_error: dlq

# Fan-out via structural guard — no lambda needed
premium = scored when scored.tier == "premium"
alert = AlertTeam(premium) with fallback: { alerted: false }

# Union-based routing
type Result = Success | Failure
validated = Validate(scored)
routed = match validated {
  Success { event } -> Archive(event)
  Failure { error } -> NotifyAdmin(error)
}

# Explicit materialization — shared window for multiple consumers
batch = collect(events, tumbling(5min))
stats = ComputeStats(batch)
summary = Summarize(batch)

# Cache as dedup — same event within 30s returns cached result
deduped = ExpensiveEnrich(events) with cache: 30s

out scored
out alert
out routed
out stats
out summary
out deduped
```

### Single Mode

```scala
val result = runtime.run(compiledPipeline, Map(
  "events"   -> CValue.CSeq(Vector(event1, event2, event3)),
  "profiles" -> CValue.CSeq(Vector(profile1, profile2))
))
// Returns: DataSignature with scored, alert, routed, stats, summary, deduped outputs
// batch = collect wraps full vector as single List (identity in single mode)
// stats + summary both receive the same full List<UserEvent>
// match on validated: one branch per element based on tag
// premium: elements where tier == "premium" wrapped in Optional
```

### Streaming Mode

```scala
val config = StreamPipelineConfig(
  bindings = Map(
    "events"   -> SourceBinding("kafka", Map("topic" -> "user-events")),
    "profiles" -> SourceBinding("kafka", Map("topic" -> "user-profiles")),
    "scored"   -> SinkBinding("kafka", Map("topic" -> "scored-events")),
    "alert"    -> SinkBinding("webhook", Map("url" -> "https://alerts.example.com")),
    "routed"   -> SinkBinding("kafka", Map("topic" -> "routed-results")),
    "stats"    -> SinkBinding("kafka", Map("topic" -> "event-stats")),
    "summary"  -> SinkBinding("kafka", Map("topic" -> "event-summary")),
    "deduped"  -> SinkBinding("kafka", Map("topic" -> "deduped-events"))
  ),
  dlq = Some(SinkBinding("kafka", Map("topic" -> "enrichment-dlq")))
)

val graph = StreamRuntime.deploy(compiledPipeline, config, registry)
graph.stream.compile.drain  // Runs continuously
// match on validated: stream partition — Success events to Archive, Failure events to NotifyAdmin
// premium: element-wise filter — non-premium events dropped from stream
// batch = collect: materializes stream into 5min tumbling windows → Seq<List<UserEvent>>
// stats + summary: both receive the same windowed chunks, shared boundaries
// deduped: cache deduplication — repeated events within 30s return cached result
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

  "union routing" should "partition by type" in {
    val testKit = StreamTestKit.fromPipeline(compiledPipeline)

    testKit.run {
      testKit.source("events").emit(validEvent)
      testKit.source("events").emit(invalidEvent)

      val routed = testKit.sink("routed").take(2, timeout = 5.seconds)
      // One goes through Archive, one through NotifyAdmin
      routed should have size 2
    }
  }

  "collect" should "materialize shared window for multiple consumers" in {
    val testKit = StreamTestKit.fromPipeline(compiledPipeline)

    testKit.run {
      testKit.source("events").emitAll(List(e1, e2, e3))
      testKit.advanceTime(5.minutes)

      // Both stats and summary receive the same windowed batch
      val stats = testKit.sink("stats").take(1, timeout = 1.second)
      val summary = testKit.sink("summary").take(1, timeout = 1.second)
      stats should have size 1
      summary should have size 1
    }
  }
}
```

---

## Interpreter Functor Reference

This section compiles all dual-mode mappings by category — the complete specification of how every pipeline construct is interpreted in single and streaming mode.

### Types

| Pipeline Type | CType | Single Mode | Streaming Mode |
|---------------|-------|-------------|----------------|
| `T` (scalar) | `CString`, `CInt`, etc. | Value | Event (pulled from source) |
| `Seq<T>` | `CSeq(elementType)` | `Vector[T]` | `Stream[IO, T]` |
| `List<T>` | `CList(elementType)` | `Vector[T]` | `Vector[T]` |
| `Optional<T>` | `COptional(innerType)` | `CSome(v)` or `CNone` | `Some` = emit, `None` = no emission |
| `A \| B` (union) | `CUnion(structure)` | Tagged value | Tagged event |
| `Seq<List<T>>` | `CSeq(CList(...))` | `Vector[Vector[T]]` | `Stream[IO, Vector[T]]` |
| `List<Seq<T>>` | — | **Compiler error** | — |
| `Seq<Seq<T>>` | — | **Compiler error** | — |

### Seq Operations (Interpreter Functor)

| Operation | Pipeline Syntax | Single (`Vector`) | Streaming (`Stream[IO, _]`) |
|-----------|----------------|-------------------|-----------------------------|
| Map | `Module(seq)` | `vector.map(module.run)` | `stream.evalMap(module.run)` |
| Filter | `seq when seq_cond` | `vector.filter(p)` | `stream.filter(p)` |
| FlatMap | (internal) | `vector.flatMap(f)` | `stream.flatMap(f)` |
| Concat | `seqA ++ seqB` | `v1 ++ v2` | `s1 ++ s2` (sequential) |
| Interleave | `Interleave(a, b)` | Round-robin alternating (truncates to shorter) | `s1.merge(s2)` (non-deterministic) |
| Zip | `Zip(a, b)` | `v1.zip(v2)` | `s1.zip(s2)` (backpressure) |
| Fold | `Reduce(seq)` | `vector.foldLeft(z)(f)` → scalar | Windowed aggregation → scalar events |
| Take | `take(seq, n)` | `vector.take(n)` | `stream.take(n)` |
| Batch | `with batch: N` | `vector.grouped(n)` → `Seq<List<T>>` | `stream.chunkN(n)` → `Stream[IO, Vector[T]]` |
| Window | `with window: tumbling(d)` | Identity / compiler info | `stream.groupWithin(n, d)` → `Stream[IO, Vector[T]]` |
| Collect | `collect(seq)` / `collect(seq, windowSpec)` | `Vector(vector)` — wraps as single List | Windowed grouping → `Stream[IO, Vector[T]]` |

**Excluded** (no streaming counterpart — `List`-only): `size`, `reverse`, `sort`, `indexOf`, random access.

**Auto-desugaring:** When a module expects `List<T>` but receives `Seq<T>`, the compiler auto-inserts `collect` with the default materialization window. See [Materialization Boundaries](#materialization-boundaries--collect).

### Element-Wise Operations (Structural Syntax)

| Operation | Expression | Input Type | Output Type | Lambda Equivalent |
|-----------|-----------|------------|-------------|-------------------|
| Field access | `items.name` | `Seq<{name: T, ...}>` | `Seq<T>` | `map(items, (x) => x.name)` |
| Projection | `items[name, age]` | `Seq<Record>` | `Seq<Record>` | `map(items, (x) => x[name, age])` |
| Broadcast merge | `items + scalar` | `Seq<Record> + Record` | `Seq<Record>` | `map(items, (x) => x + scalar)` |
| Comparison | `items.score > 10` | `Seq<Int>` | `Seq<Boolean>` | `map(items, (x) => x.score > 10)` |
| Filter | `items when items.score > 10` | `Seq<T> × Seq<Boolean>` | `Seq<T>` | `filter(items, (x) => x.score > 10)` |

All element-wise operations are mode-agnostic — the interpreter handles `Vector.map` vs `Stream.map` transparently.

### Control Flow

| Construct | Expression | Single Mode | Streaming Mode |
|-----------|-----------|-------------|----------------|
| Guard (scalar) | `x when cond` | `Optional<T>` (`CSome` / `CNone`) | Event filter (emit / swallow) |
| Guard (Seq, scalar cond) | `seq when cond` | All or nothing | All or nothing |
| Guard (Seq, Seq cond) | `seq when seq_cond` | Element-wise filter | `stream.filter(p)` |
| Coalesce | `opt ?? fallback` | Fallback on `None` | Default on filtered event |
| Match (scalar union) | `match x { A -> ..., B -> ... }` | Branch, one executes | Partition stream by tag |
| Match (Seq union) | `match seq { A -> ..., B -> ... }` | Element-wise branch | Element-wise stream partition |
| Branch (scalar) | `branch { cond -> expr, ... }` | First match wins | Route event by predicate |
| Conditional | `if cond then a else b` | Evaluate one branch | Evaluate per event |

### Merge & Join

**Merge operator (`+`):**

| Left | Right | Single Mode | Streaming Mode |
|------|-------|-------------|----------------|
| `Record` | `Record` | Field merge (`lMap ++ rMap`) | Field merge — both scalar events, fires per join strategy (`combineLatest` default) |
| `Seq<Record>` | `Seq<Record>` | Element-wise merge (zip, same length — throws on mismatch) | `stream.zip(stream).map(merge)` — terminates on shorter side (see zip length divergence) |
| `Seq<Record>` | `Record` | Broadcast scalar to every element | `combineLatest` — latest scalar merged into each element |
| `Record` | `Seq<Record>` | Broadcast (reversed) | Same as above |

**Join strategies (multi-input synchronization):**

| Strategy | Syntax | Behavior | Use Case |
|----------|--------|----------|----------|
| `combineLatest` | default | Fire on any input, use latest from others | Event + slowly-changing context |
| `zip` | `with join: zip` | Pair 1:1 — wait for one from each | Correlated inputs |
| `buffer(timeout)` | `with join: buffer(5s)` | Wait up to timeout for all inputs | Loose synchronization |

Join strategies are **streaming-only**. In single mode, all inputs are provided upfront.

### Fan-In & Fan-Out

**Fan-in:**

| Operator | Syntax | Single Mode | Streaming Mode |
|----------|--------|-------------|----------------|
| Concat | `seqA ++ seqB` | `v1 ++ v2` (preserves all elements) | `s1 ++ s2` (drain first, then second) |
| Interleave | `Interleave(a, b)` | Round-robin alternating (truncates to shorter) | `s1.merge(s2)` (non-deterministic) |
| Zip | `Zip(a, b)` | `v1.zip(v2)` → `Seq<(A, B)>` (truncates to shorter) | `s1.zip(s2)` (terminates on shorter) |

**Fan-out:**

| Mechanism | Trigger | Single Mode | Streaming Mode |
|-----------|---------|-------------|----------------|
| DAG dependency | Multiple nodes read same data | Computed once, shared | `broadcastThrough(...)` — each branch in own fiber |
| Union `match` | `match` on `A \| B` typed data | One branch per value | Stream partition by tag (efficient, no waste) |
| Predicate `branch` | `branch { cond -> ... }` | First match per value | Route each event by predicate |

### With-Clause Options (Existing — Dual Mode)

| Option | Single Mode | Streaming Mode | Emergent Behavior |
|--------|------------|----------------|-------------------|
| `retry: N` | Retry module on failure | Retry per element | — |
| `timeout: 5s` | Fail module if >5s | Fail element if >5s | — |
| `delay: 100ms` | Delay before execution | Per-element delay | Rate limiting (`stream.metered`) |
| `backoff: exponential` | Backoff between retries | Same, per element | — |
| `fallback: { ... }` | Fallback value on failure | Fallback per failed element | Stream never drops on error |
| `cache: 30s` | Memoize by input hash | Same input → cached result | **Stream deduplication** |
| `cache_backend: "memcached"` | External cache | Same, survives restarts | **Cross-restart dedup** |
| `throttle: 100/1s` | Token-bucket rate limit | Token-bucket rate limit | Bursty rate control |
| `concurrency: 8` | Parallel module execution | `parEvalMap(8)` | N elements concurrently |
| `on_error: dlq` | Wrap error + log | Route to DLQ, continue | Per-element error isolation |
| `lazy: true` | Defer computation | **Ignored** | fs2 is pull-based (inherently lazy) |
| `priority: high` | Scheduler priority | **Ignored** | No stream-level priority |

### With-Clause Options (New — Streaming Additive)

| Option | Single Mode | Streaming Mode | Type Change |
|--------|------------|----------------|-------------|
| `batch: N` | `vector.grouped(n)` | `stream.chunkN(n)` | `Seq<T> → Seq<List<T>>` |
| `batch_timeout: 2s` | Ignored | Flush after timeout | — |
| `window: tumbling(5min)` | Compiler info, identity | `groupWithin(n, d)` | `Seq<T> → Seq<List<T>>` |
| `window: sliding(10min, 1min)` | Compiler info, identity | Sliding window | `Seq<T> → Seq<List<T>>` |
| `window: count(100)` | Same as `batch: 100` | Count-based window | `Seq<T> → Seq<List<T>>` |
| `checkpoint: 30s` | Ignored | Periodic offset commit | — |
| `join: zip` | Ignored | Strict 1:1 input pairing | — |
| `join: buffer(5s)` | Ignored | Wait up to timeout | — |

`window` and `batch` are **mutually exclusive** — compiler rejects both on the same module call.

### Error Handling

| Strategy | Syntax | Single Mode | Streaming Mode |
|----------|--------|------------|----------------|
| Propagate | `on_error: propagate` | Re-throw, fail pipeline | Re-throw, terminate stream (or branch) |
| Skip | `on_error: skip` | Return zero value | Drop element, continue |
| Log | `on_error: log` | Log + zero value | Log + drop element, continue |
| Wrap | `on_error: wrap` | Wrap in ErrorResult | Emit ErrorResult downstream |
| DLQ | `on_error: dlq` | Same as wrap | Route `CMap` to DLQ sink |

**Circuit breaker** (streaming only): After `maxConsecutiveErrors` failures, stream pauses → cooldown → half-open retry → resume or double cooldown.

### Delivery & Lifecycle

**Delivery guarantees:**

| Level | Guarantee | Default For |
|-------|-----------|-------------|
| At-most-once | Elements may be lost | `memory` connector |
| At-least-once | Elements may replay; none lost | Persistent connectors |
| Exactly-once | Each element exactly once | Phase 5, connector-specific |

**Checkpoint frequency:**

| Setting | Behavior | Trade-off |
|---------|----------|-----------|
| `checkpoint: 1s` | Commit every second | Low replay, high overhead |
| `checkpoint: 1min` | Commit every minute | Up to 1 min replay |
| No checkpoint | Commit per-element | Safest, highest overhead |

**Hot-reload compatibility:**

| Schema Change | Allowed? | Rationale |
|---------------|----------|-----------|
| Input unchanged | Yes | Safe |
| Input widened (new optional fields) | Yes | Backwards-compatible |
| Input narrowed (fields removed) | **No** | Connector data may not match |
| Input changed (different fields) | **No** | Deploy as new stream |
| Output changed | Yes (warning) | Downstream may need updating |
| New inputs added | **No** | No connector binding |
| Inputs removed | Yes | Connector disconnected |

---

## Open Questions

No open questions remain. All design decisions have been resolved.

---

## Resolved Design Decisions

### Streaming is NOT a type system concern

The type system does not introduce `Stream<T>`. Instead, `Seq<T>` is the abstract container, and the execution strategy determines its runtime representation.

### Seq<T> subsumes Candidates<T>

Clean rename throughout codebase. No backward compatibility shims. Parser accepts both during transition.

### Guard on Seq is element-wise filter

`seq when condition` filters elements (drops non-matching) rather than wrapping in Optional. For scalar guards, Optional semantics are retained, but `None` means "no emission" in streaming mode.

### Union types are event routing

`match` on a union-typed Seq becomes a stream partition. `Optional<T>` is conceptually `T | None` and follows union event semantics. `branch` is predicate-based routing.

### Merge operator is type-driven enrichment

`Seq<Record> + Record` uses combineLatest in streaming (enrichment pattern). `Seq<Record> + Seq<Record>` uses zip (element-wise merge). No special enrichment syntax needed.

### Key-based joins are a module concern

`JoinByKey` is a stateful module, not a language operator. Deferred to Phase 5.

### Fan-in operators are pseudo-modules

`Interleave(a, b)`, `Zip(a, b)`, and `collect(seq)` use existing function call syntax. `++` remains the language-level concat operator. Pseudo-modules are compiler-inlined via the `stdlib.builtin.*` path — separate from the `stdlib.hof.*` HOF path (which requires lambda extraction). This keeps the HOF path uniform while giving pseudo-modules a clean, lambda-free compilation path.

### Existing with-clause options work per-element in streaming

No new streaming-specific options needed for the existing 12. `cache` becomes deduplication, `delay` becomes rate limiting, `lazy` and `priority` are ignored with compiler info diagnostics.

### Window syntax uses identifier + parenthesized arguments

`window: tumbling(5min)`, `window: sliding(10min, slide: 1min)`, `window: count(100)`. This follows the same parsing patterns as existing with-clause values (`Duration`, `Rate`, `BackoffStrategy`). AST representation:

```scala
sealed trait WindowSpec
object WindowSpec {
  case class Tumbling(size: Duration) extends WindowSpec
  case class Sliding(size: Duration, slide: Duration) extends WindowSpec
  case class Count(n: Int) extends WindowSpec
}
```

Parser grammar: `WindowSpec = 'tumbling' '(' Duration ')' | 'sliding' '(' Duration ',' 'slide' ':' Duration ')' | 'count' '(' Int ')'`. No new parsing primitives needed — reuses existing `Duration` and integer parsers.

### Backpressure uses fs2 default — no per-branch configuration

Slowest consumer governs source pace (fs2 pull-based default). This is the only safe choice — per-branch buffering/dropping would mask real problems and risk silent data loss. The two escape hatches are: (1) `with concurrency: N` to parallelize a slow branch, (2) decouple via connector (route through a persistent queue for independent consumption). A `with buffer: N` option may be added in Phase 5 if demand warrants it, but as opt-in with a compiler warning.

### Nested Seq<Seq<T>> is a compiler error

`flatMap` is defined as `map + flatten`, never producing nested Seq. Use `Seq<List<T>>` for nested structure.

### `collect` is the materialization boundary, auto-desugared by the compiler

`collect` is a **pseudo-module** in `stdlib.collection` (same namespace as `filter`, `map`, `all`, `any`, but compiled via the `stdlib.builtin.*` path — no lambda extraction needed). It converts `Seq<T>` to `Seq<List<T>>` — identity-ish in single mode, windowed grouping in streaming mode. The compiler auto-inserts `collect` when a module expects `List<T>` but receives `Seq<T>`, with an info diagnostic. `with window:` on a module call is sugar for inserting `collect` before the call. The default materialization window is configurable at deployment level via `StreamOptions.defaultMaterializationWindow`.

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

### Key-based joins as language-level syntax

Key-based joins are inherently stateful (lookup table maintenance). Overloading `+` with key semantics would conflate structural merge with stateful matching. Better as a module.

### Optional<T> refactored to CUnion sugar

Considered unifying `COptional`/`SOptional` into `CUnion(T, None)` at the type level. Kept as separate types for ergonomics and optimization — the streaming interpreter applies union event semantics to Optional without requiring a type system change.

### `collect` as new syntax or `with`-clause on bare expression

Considered expressing materialization as `events with window: tumbling(5min)` (bare expression `with`-clause), `events.collect(5min)` (method syntax), `events as List<T>` (type cast), or `events |> collect(...)` (pipe operator). All would require parser changes and break existing language patterns. The stdlib function-call pattern (`collect(events, tumbling(5min))`) requires zero parser changes and matches the existing `filter`/`map` calling convention. Also considered making `collect` an HOF (via `stdlib.hof.*` path), but since it takes no lambda, it's a better fit as a pseudo-module (`stdlib.builtin.*`) — same pattern as `Interleave` and `Zip`, keeping the HOF path uniform.

### Lambda-only element-wise operations

The structural dot syntax (`items.field`, `items.score > 10`) covers the common case without lambdas. Explicit lambdas remain for complex cases but are rarely needed.

---

## References

- **RFC-014:** Suspendable Execution — `SuspendedExecution`, `SuspensionStore`, `SuspensionCodec` (already implemented)
- **RFC-015:** Pipeline Lifecycle Management — hot/cold loading, canary, versioning
- **RFC-024:** Module Provider Protocol — polyglot modules compatible with streaming
- **fs2 documentation:** https://fs2.io
- **fs2-kafka:** https://fd4s.github.io/fs2-kafka/
- **Existing streaming infra:** `modules/http-api/.../ExecutionWebSocket.scala` (fs2 already used)
- **Existing suspension:** `modules/runtime/.../SuspendedExecution.scala`, `SuspendableExecution.scala`
- **Existing element-wise ops:** `modules/lang-compiler/.../TypeChecker.scala` (lines 536-586) — List<Record> field access, projection, merge
- **Existing union types:** `modules/core/.../TypeSystem.scala` — CUnion, COptional; `modules/lang-compiler/.../SemanticType.scala` — SUnion, SOptional
- **Existing match/pattern:** `modules/lang-ast/.../AST.scala` — Match, MatchCase, Pattern.Record, Pattern.TypeTest, Pattern.Wildcard
- **Existing merge transform:** `modules/core/.../InlineTransform.scala` — MergeTransform (4 strategies by type)
- **Existing HOF inlining:** `modules/lang-compiler/.../DagCompiler.scala` — processHigherOrderNode, FilterTransform, MapTransform
- **Existing HOF signatures:** `modules/lang-stdlib/.../categories/HigherOrderFunctions.scala` — filter, map, all, any (HOFs with lambdas)
- **Existing HOF detection:** `modules/lang-compiler/.../IRGenerator.scala` — `isHigherOrderFunction`, `getHigherOrderOp`, `HigherOrderOp` enum
- **Pseudo-module pattern:** `collect`, `Interleave`, `Zip` use `stdlib.builtin.*` prefix — compiler-inlined without lambda extraction, separate from `stdlib.hof.*` path
