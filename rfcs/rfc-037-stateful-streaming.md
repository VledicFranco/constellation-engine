# RFC-037: Stateful Streaming Modules

**Status:** Draft
**Priority:** P2 (Streaming Extensibility)
**Author:** Francisco + Claude
**Created:** 2026-02-22
**Depends on:** RFC-025 (Streaming Pipelines), RFC-036 (Delivery Guarantees)
**Splits from:** RFC-025 Phase 5, Issue #231

---

## Summary

Introduce **stateful streaming modules** — a new module type that maintains per-key mutable state across elements in a streaming pipeline. This enables running counters, session windows, deduplication, and key-based joins without leaving Constellation.

The core abstraction is `StreamModule[S]`: a module that receives an element and its current state, and produces an output element and updated state. The runtime manages state lifecycle: initialization, threading through the stream, periodic checkpointing to a durable backend, and recovery on restart.

---

## Motivation

### The Gap

All current Constellation modules are **stateless** — they receive an input, produce an output, and retain nothing between invocations. In single mode this is fine (each execution is independent). In streaming mode, many real workloads require state:

| Workload | State Needed |
|----------|-------------|
| Running count/sum/average | Accumulator per key |
| Session windows | Per-session buffer + timeout |
| Deduplication | Seen-set or bloom filter |
| Key-based joins | Buffer for unmatched elements on each side |
| Rate limiting | Token bucket per key |
| Change detection | Previous value per key |

Today, users must implement these as external services (Redis, database) and call them from IO-based modules. This works but loses the benefits of Constellation's type safety, DAG visualization, and lifecycle management.

### What This Enables

```constellation
in events: Seq<{userId: String, action: String, amount: Float}>

# Stateful module: accumulates spend per user
userSpend = RunningSum(events, key: userId, value: amount)

# Stateful module: deduplicates by event ID within 1-hour window
unique = Dedup(events, key: eventId, window: 1h)

# Stateful module: joins two streams by key
enriched = JoinByKey(orders, users, key: userId)

out userSpend
out unique
out enriched
```

Modules like `RunningSum`, `Dedup`, and `JoinByKey` are implemented using the `StreamModule[S]` trait and can be registered in the stdlib or as user-defined modules.

---

## Design

### Core Trait: `StreamModule[S]`

```scala
// modules/stream/src/main/scala/io/constellation/stream/StatefulModule.scala

trait StreamModule[S] {
  /** Initial state for a new key (or on first element if unkeyed). */
  def initialState: S

  /** Process one element with the current state. Returns output + updated state. */
  def process(element: CValue, state: S): IO[(CValue, S)]

  /** Serialize state for checkpointing. */
  def serializeState(state: S): Array[Byte]

  /** Deserialize state on recovery. */
  def deserializeState(bytes: Array[Byte]): S
}
```

### Keyed vs Unkeyed State

| Mode | State Scope | Example |
|------|-------------|---------|
| **Unkeyed** | Single global state instance | Global running count |
| **Keyed** | One state instance per key | Per-user running total |

Keyed state requires a **key extractor** — a function `CValue => String` derived from the pipeline's `key:` parameter. The runtime maintains a `Map[String, S]` of per-key states.

```scala
case class StatefulModuleConfig[S](
  module: StreamModule[S],
  keyExtractor: Option[CValue => String],  // None = unkeyed (global state)
  checkpointBackend: CheckpointBackend,
  checkpointInterval: FiniteDuration = 1.minute,
  stateEvictionPolicy: EvictionPolicy = EvictionPolicy.None
)
```

### State Threading in StreamCompiler

For stateless modules, `StreamCompiler` lifts `I => IO[O]` to `stream.evalMap(f)`. For stateful modules, the lifting is different:

```scala
// Stateless: each element is independent
stream.evalMap(module.execute)

// Stateful (unkeyed): thread state through elements sequentially
stream.evalMapAccumulate(initialState) { (state, element) =>
  module.process(element, state).map { case (output, newState) => (newState, output) }
}

// Stateful (keyed): maintain per-key state
stream.evalMap { element =>
  val key = keyExtractor(element)
  for {
    currentState <- stateStore.get(key).map(_.getOrElse(module.initialState))
    (output, newState) <- module.process(element, currentState)
    _ <- stateStore.put(key, newState)
  } yield output
}
```

The keyed path requires a **state store** — an abstraction over the mutable state map.

### State Store Abstraction

```scala
trait StateStore[S] {
  def get(key: String): IO[Option[S]]
  def put(key: String, state: S): IO[Unit]
  def delete(key: String): IO[Unit]
  def snapshot: IO[Map[String, S]]           // for checkpointing
  def restore(data: Map[String, S]): IO[Unit] // for recovery
}
```

**Implementations:**

| Backend | Persistence | Use Case |
|---------|-------------|----------|
| `InMemoryStateStore` | None (lost on restart) | Development, testing, ephemeral streams |
| `CheckpointedStateStore` | Periodic snapshots to durable storage | Production — wraps in-memory with checkpoint |

The durable storage backend is pluggable:

```scala
trait CheckpointBackend {
  def save(streamId: String, moduleId: UUID, data: Array[Byte]): IO[Unit]
  def load(streamId: String, moduleId: UUID): IO[Option[Array[Byte]]]
  def delete(streamId: String, moduleId: UUID): IO[Unit]
}
```

**Initial backends:**

| Backend | Implementation | Notes |
|---------|---------------|-------|
| `FileCheckpointBackend` | Local filesystem | Simple, no dependencies. Good for single-node. |
| `NoopCheckpointBackend` | No-op | For `InMemoryStateStore` or testing. |

A `RocksDBCheckpointBackend` (referenced in RFC-025) is deferred to a follow-up — it requires a native dependency and careful lifecycle management.

### Checkpointing Protocol

Periodic checkpointing persists state to survive restarts:

```
Every checkpointInterval:
  1. Pause element processing (briefly)
  2. snapshot = stateStore.snapshot()
  3. bytes = module.serializeState(snapshot)  // per key
  4. checkpointBackend.save(streamId, moduleId, bytes)
  5. If at-least-once: commit source offset (state and offset are consistent)
  6. Resume element processing
```

**Interaction with delivery guarantees (RFC-036):**

| Guarantee | Checkpoint Behavior |
|-----------|-------------------|
| At-most-once | Checkpoint state only; no offset commit |
| At-least-once | Checkpoint state + commit offset atomically (same interval) |
| Exactly-once | Checkpoint state + offset within transaction |

Aligning checkpoint and offset commit ensures that on recovery, the replayed elements start from the exact state that was checkpointed. Without this alignment, state and offsets can diverge, causing incorrect results.

### Recovery Protocol

On pipeline restart:

```
1. Load last checkpoint: bytes = checkpointBackend.load(streamId, moduleId)
2. Deserialize: stateMap = module.deserializeState(bytes)
3. Restore: stateStore.restore(stateMap)
4. Resume source from last committed offset (handled by RFC-036)
5. Elements between last checkpoint and crash are re-processed
   → state updates are idempotent (same input + same prior state = same output)
```

### State Eviction

For keyed state, unbounded key growth is a concern. Eviction policies:

```scala
sealed trait EvictionPolicy
object EvictionPolicy {
  case object None                                   extends EvictionPolicy
  case class MaxKeys(limit: Int)                     extends EvictionPolicy  // LRU eviction
  case class TTL(duration: FiniteDuration)            extends EvictionPolicy  // time-based
  case class TTLAndMaxKeys(ttl: FiniteDuration, max: Int) extends EvictionPolicy
}
```

Evicted keys restart with `initialState` on next occurrence.

### Module Registration

Stateful modules are registered like regular modules but with a different builder API:

```scala
val runningSum: Module.Uninitialized = StreamModuleBuilder
  .metadata("RunningSum", "Accumulates sum per key", 1, 0)
  .stateful[Double](
    initialState = 0.0,
    process = (element, state) => {
      val amount = element.asProduct("amount").asFloat
      val newState = state + amount
      IO.pure((CValue.CFloat(newState), newState))
    },
    serialize = state => state.toString.getBytes,
    deserialize = bytes => new String(bytes).toDouble
  )
  .build
```

### JoinByKey — First Stateful Stdlib Module

`JoinByKey` is the canonical use case for stateful streaming, and the primary motivation from RFC-025:

```scala
// State: Map of unmatched elements from each side
case class JoinState(
  leftBuffer: Map[String, CValue],   // key → last left element
  rightBuffer: Map[String, CValue]   // key → last right element
)

// On left element: buffer it, check right buffer for match
// On right element: buffer it, check left buffer for match
// On match: emit joined product, remove from both buffers
```

This is deferred to a follow-up after the core `StreamModule[S]` infrastructure is proven.

---

## Implementation Plan

### Phase 1: Core `StreamModule[S]` Trait + State Store (~400 LOC)

- [ ] Define `StreamModule[S]` trait
- [ ] Define `StateStore[S]` trait
- [ ] Implement `InMemoryStateStore`
- [ ] Implement `CheckpointedStateStore` wrapper
- [ ] Define `CheckpointBackend` trait
- [ ] Implement `FileCheckpointBackend` and `NoopCheckpointBackend`
- [ ] Unit tests for state store operations and checkpoint round-trip

### Phase 2: StreamCompiler Integration (~300 LOC)

- [ ] Extend `StreamCompiler` to detect stateful modules
- [ ] Implement unkeyed state threading (`evalMapAccumulate`)
- [ ] Implement keyed state threading (state store lookup/update)
- [ ] Wire checkpoint timer into stream lifecycle
- [ ] Integration tests: stateful module in streaming pipeline

### Phase 3: Key Extraction + Eviction (~200 LOC)

- [ ] Implement key extractor from `key:` parameter in module options
- [ ] Implement `EvictionPolicy` (LRU, TTL)
- [ ] Add eviction metrics to `StreamMetrics`
- [ ] Tests: eviction under load, TTL expiry

### Phase 4: Recovery (~200 LOC)

- [ ] Implement recovery protocol (load checkpoint → restore state → resume)
- [ ] Align checkpoint interval with offset commit (RFC-036 interaction)
- [ ] Integration test: crash + restart → state recovered, no data loss
- [ ] Integration test: state + offset consistency after recovery

### Phase 5: Stdlib Modules (follow-up)

- [ ] `RunningSum` / `RunningAvg` / `RunningCount`
- [ ] `Dedup` (seen-set with TTL)
- [ ] `JoinByKey` (buffered key-based join)
- [ ] `SessionWindow` (per-key windowed aggregation)

---

## Alternatives Considered

### External state via IO-based modules

This is the current workaround — use `.implementation[I, O]` with IO effects that read/write Redis or a database. It works but:
- State management is the user's responsibility (no automatic checkpointing, recovery, or eviction)
- No integration with Constellation's delivery guarantees
- DAG visualization doesn't show state dependencies
- Hot-reload can't drain state gracefully

`StreamModule[S]` brings state under Constellation's lifecycle management.

### fs2 `evalMapAccumulate` only (no state store)

For unkeyed state, `evalMapAccumulate` is sufficient. But keyed state requires random access by key, which `evalMapAccumulate` doesn't support — it only threads a single accumulator. The state store abstraction is necessary for keyed workloads.

### State serialization via CValue

Instead of user-defined `serializeState`/`deserializeState`, we could require state to be a `CValue` and use the existing CValue serialization. This would simplify the API but limit state to Constellation's type system — no custom data structures, no efficient binary formats. The user-defined approach is more flexible.

### RocksDB from day one

Deferred. RocksDB provides excellent performance for large state stores but adds a native dependency (`librocksdbjni`) and lifecycle complexity (compaction, WAL management). `FileCheckpointBackend` with periodic snapshots is sufficient for initial adoption. RocksDB can be added as a `CheckpointBackend` implementation later without changing the `StreamModule[S]` API.

---

## Open Questions

1. **State schema evolution:** What happens when a stateful module's state type changes (e.g., adding a field to the accumulator)? Options: (a) require explicit migration functions, (b) version the serialization format, (c) discard old state on schema change. Proposed: start with (c) — discard and log a warning. Add migration support in a follow-up if users need it.

2. **Parallelism for keyed state:** Can elements with different keys be processed in parallel? Yes, if the state store supports concurrent access per key. `InMemoryStateStore` with a `ConcurrentHashMap` backend enables this. But ordering within a key must be preserved. Proposed: parallel across keys, sequential within a key.

3. **State size limits:** Should there be a maximum state size per key or per module? Unbounded state growth can OOM the process. Proposed: configurable `maxStateBytes` with a default of 100MB per module, enforced at checkpoint time.

4. **Language syntax for stateful modules:** Should `key:` be a first-class parameter in constellation-lang, or a module option in the `with` clause? Proposed: `with { key: userId }` in the `with` clause, consistent with other module options.
