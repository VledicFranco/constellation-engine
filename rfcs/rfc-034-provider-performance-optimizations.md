# RFC-034: Module Provider Performance Optimizations

**Status:** Draft
**Priority:** P1 (Streaming Performance)
**Author:** Francisco + Claude
**Created:** 2026-02-22
**Depends on:** RFC-024 (Module Provider Protocol v4), RFC-025 (Streaming Pipelines)

---

## Summary

The current Module Provider Protocol (RFC-024) executes one gRPC call per module invocation per event. In streaming pipelines, this produces `M × N` network round-trips per second (M modules × N events/sec), which is a fundamental performance bottleneck. A 5-module pipeline processing 1,000 events/sec generates 5,000 gRPC calls/sec — each carrying serialization, network, and deserialization overhead.

This RFC defines three incremental optimization phases that progressively reduce this overhead without changing the core protocol's semantics:

1. **Batch Invocation** — Amortize N events into a single gRPC call. Reduces calls by ~100x for typical batch sizes.
2. **Persistent Streaming Connections** — Replace per-call unary gRPC with bidirectional streaming. Eliminates connection overhead entirely.
3. **Provider-Side Chaining** — Send a linear chain of modules to the provider for local execution. Eliminates intermediate round-trips between consecutive external modules.

Each phase is independently valuable and backwards-compatible with existing providers.

---

## Motivation

### The N-Hops Problem

Module providers are external gRPC services. Every module call crosses a process boundary:

```
Event → [serialize] → [gRPC call] → [deserialize] → [execute] → [serialize] → [gRPC response] → [deserialize] → Next
```

For a single invocation this is ~2ms overhead (gRPC + MessagePack). But streaming pipelines amplify this linearly:

| Pipeline Shape | Events/sec | Calls/sec | Overhead/sec |
|----------------|-----------|-----------|-------------|
| 1 external module | 1,000 | 1,000 | ~2s |
| 3 external modules (linear) | 1,000 | 3,000 | ~6s |
| 5 external modules (linear) | 1,000 | 5,000 | ~10s |

At 5,000 calls/sec, the network overhead alone exceeds the available compute time per second. The pipeline cannot keep up — events queue, backpressure triggers, and throughput collapses.

### Existing Infrastructure Gaps

The constellation-lang parser, AST, IR, and core type system already support `with batch:` and `with window:` syntax. The full compilation pipeline produces `ModuleCallOptions` with `batchSize`, `batchTimeoutMs`, and `window` fields. However:

1. **`ConstellationRoutes` hardcodes `moduleOptions = Map.empty`** (line 1615) when constructing `PipelineImage` for streaming execution, discarding all compiled module options.
2. **`StreamCompiler` never receives `moduleOptions`** — its `wire()` and `wireWithConfig()` signatures don't accept them.
3. **`ExternalModule.callExecutor()` sends a single `ExecuteRequest`** per invocation — the protobuf has no batch variant.
4. **The gRPC `ModuleExecutor` service defines only unary `Execute()`** — no streaming RPC exists.

The language already promises batching. The runtime doesn't deliver it. This RFC closes that gap.

### Why Not "Just Use Scala"?

Module providers exist for a reason: polyglot teams, existing service infrastructure, independent deployment. Telling users "for performance, don't use providers" defeats the purpose. The goal is to make providers viable for streaming workloads — not as fast as in-process Scala, but fast enough that the tradeoff is acceptable.

**Target:** Provider-backed streaming pipelines should achieve ≥80% of the throughput of equivalent in-process pipelines for I/O-bound workloads (where module execution time dominates serialization overhead).

---

## Phase 1: Batch Invocation Protocol

**Complexity:** Low
**Impact:** ~100x call reduction for typical batch sizes
**Prerequisite:** Wire `moduleOptions` through to `StreamCompiler`

### Design

Accumulate N events into a batch, send as a single gRPC call, receive N results. The provider processes all events in one invocation — amortizing connection overhead, enabling provider-side vectorization, and reducing serialization round-trips.

```
Before (N=100):  100 × [serialize → gRPC → deserialize] = 100 round-trips
After  (N=100):    1 × [serialize batch → gRPC → deserialize batch] = 1 round-trip
```

### Protocol Changes

#### New Protobuf RPC

```protobuf
service ModuleExecutor {
  // Existing — unchanged, backwards compatible
  rpc Execute(ExecuteRequest) returns (ExecuteResponse);

  // New — batch variant
  rpc ExecuteBatch(ExecuteBatchRequest) returns (ExecuteBatchResponse);
}

message ExecuteBatchRequest {
  string module_name = 1;
  // Each entry is a serialized CValue (same format as ExecuteRequest.input_data)
  repeated bytes inputs = 2;
  string execution_id = 3;
  map<string, string> metadata = 4;
}

message ExecuteBatchResponse {
  // Results in same order as inputs. Each is either output_data or error.
  repeated ExecuteBatchResult results = 1;
  ExecutionMetrics aggregate_metrics = 2;
}

message ExecuteBatchResult {
  oneof result {
    bytes output_data = 1;
    ExecutionError error = 2;
  }
}
```

#### Capability Negotiation

Providers declare batch support at registration time:

```protobuf
message ModuleDeclaration {
  string name = 1;
  TypeSchema input_schema = 2;
  TypeSchema output_schema = 3;
  string version = 4;
  string description = 5;
  // New: Provider capabilities
  ModuleCapabilities capabilities = 6;
}

message ModuleCapabilities {
  bool supports_batch = 1;
  int32 max_batch_size = 2;  // 0 = no limit
}
```

When `supports_batch = false` (or the field is absent — proto3 default), Constellation falls back to per-event `Execute()` calls. This ensures full backwards compatibility with existing providers.

### Runtime Changes

#### 1. Wire `moduleOptions` to StreamCompiler

`ConstellationRoutes` currently discards module options. Fix:

```scala
// In StreamRoutes streaming endpoint:
// Before:
moduleOptions = Map.empty

// After:
moduleOptions = compiled.pipeline.image.moduleOptions
```

`StreamCompiler.wire()` and `wireWithConfig()` gain a `moduleOptions: Map[UUID, ModuleCallOptions]` parameter (defaulting to `Map.empty` for backwards compatibility).

#### 2. Batch Accumulation in StreamCompiler

When `moduleOptions(moduleId).batchSize` is set, `StreamCompiler.buildGraph` wraps the input stream with fs2 grouping before applying the module function:

```scala
// Pseudocode — inside buildGraph per-module wiring
val batchSize    = moduleOptions.get(moduleId).flatMap(_.batchSize).getOrElse(1)
val batchTimeout = moduleOptions.get(moduleId).flatMap(_.batchTimeoutMs)
  .map(ms => FiniteDuration(ms, MILLISECONDS))
  .getOrElse(1.second)

val processedStream = if (batchSize > 1) {
  inputStream
    .groupWithin(batchSize, batchTimeout)
    .evalMap(chunk => batchModuleFn(chunk.toList))
    .flatMap(Stream.emits)
} else {
  inputStream.evalMap(moduleFn)
}
```

#### 3. ExternalModule Batch Dispatch

`ExternalModule` checks whether the provider supports batching. If so, it uses `ExecuteBatch` instead of N individual `Execute` calls:

```scala
// In ExternalModule — new batch execution path
private def callExecutorBatch(
    channelCache: GrpcChannelCache,
    executorUrl: String,
    moduleName: String,
    inputBatches: List[Array[Byte]],
    executionId: String
): IO[pb.ExecuteBatchResponse] = IO {
  val channel = channelCache.getChannel(executorUrl)
  val stub    = pb.ModuleExecutorGrpc.blockingStub(channel)
  stub.executeBatch(
    pb.ExecuteBatchRequest(
      moduleName = moduleName,
      inputs = inputBatches.map(b => ByteString.copyFrom(b)),
      executionId = executionId
    )
  )
}
```

### SDK Changes

The TypeScript and Scala SDKs gain a batch handler registration API:

```typescript
// TypeScript SDK
provider.registerBatchModule({
  name: "analyze",
  inputSchema: { type: "string" },
  outputSchema: { type: "record", fields: { sentiment: { type: "float" } } },
  // Single-event handler (existing)
  execute: async (input) => ({ sentiment: analyzeSingle(input) }),
  // Batch handler (new, optional)
  executeBatch: async (inputs) => inputs.map(i => ({ sentiment: analyzeSingle(i) })),
  maxBatchSize: 256,
});
```

If `executeBatch` is provided, the SDK advertises `supports_batch = true` in `ModuleCapabilities`. If only `execute` is provided, the SDK can optionally auto-generate a batch handler that loops over the single-event handler (still benefits from amortized network overhead, though not from provider-side vectorization).

### Constellation-Lang Integration

The existing `with batch:` syntax already compiles through to `ModuleCallOptions.batchSize`. Once the runtime wiring is fixed, this works end-to-end:

```constellation
use ml.sentiment

in events: Stream<String>

results = analyze(events) with {
  batch: 100,
  timeout: 1s
}

out results
```

---

## Phase 2: Persistent Streaming Connections

**Complexity:** Medium
**Impact:** Eliminates per-call connection overhead
**Prerequisite:** Phase 1 (batch infrastructure)

### Design

Replace unary `Execute` / `ExecuteBatch` RPCs with a long-lived bidirectional gRPC stream. Events flow continuously without per-call connection setup:

```
Before: [connect → request → response → disconnect] × N
After:  [connect] → [request → response] × N → [disconnect]
```

### Protocol Changes

```protobuf
service ModuleExecutor {
  rpc Execute(ExecuteRequest) returns (ExecuteResponse);         // unchanged
  rpc ExecuteBatch(ExecuteBatchRequest) returns (ExecuteBatchResponse);  // Phase 1
  // New — persistent bidirectional stream
  rpc ExecuteStream(stream ExecuteRequest) returns (stream ExecuteResponse);
}
```

The `ExecuteStream` RPC reuses existing `ExecuteRequest` / `ExecuteResponse` messages. The stream stays open for the lifetime of the streaming pipeline. Constellation multiplexes all module calls for a given provider over a single stream, using `execution_id` for request-response correlation.

### Capability Negotiation

```protobuf
message ModuleCapabilities {
  bool supports_batch = 1;
  int32 max_batch_size = 2;
  bool supports_streaming = 3;  // New
}
```

### Runtime Changes

`ExternalModule` maintains a per-provider `StreamExecutor` that manages the bidirectional stream:

- Opens the stream on first module call in a streaming pipeline
- Sends `ExecuteRequest` messages and matches responses by `execution_id`
- Handles backpressure via gRPC flow control
- Reconnects on stream failure (with exponential backoff)
- Closes when the streaming pipeline shuts down

### Benefits

- Eliminates TCP connection overhead (~0.5ms per call saved)
- Enables gRPC flow control for natural backpressure propagation
- Reduces provider-side thread churn (one stream handler vs. N request handlers)

---

## Phase 3: Provider-Side Chaining

**Complexity:** Medium-High
**Impact:** Eliminates intermediate round-trips for consecutive external modules
**Prerequisite:** Phase 2 (streaming connections)

### Design

When a streaming pipeline has a linear chain of modules served by the same provider, Constellation sends the chain definition to the provider. The provider executes the chain locally, returning only the final output:

```
Before: Event → [gRPC: A] → [gRPC: B] → [gRPC: C] → Result   (3 round-trips)
After:  Event → [gRPC: chain(A→B→C)] → Result                  (1 round-trip)
```

### Detection

`StreamCompiler` identifies chainable segments during `buildGraph`:

1. Find consecutive module nodes in topological order
2. Check if all modules in the run are served by the same provider (same `executor_url`)
3. Check if all modules in the run have compatible types (output of N matches input of N+1)
4. Group into a `ChainedExecution` unit

### Protocol Changes

```protobuf
message ExecuteChainRequest {
  // Ordered list of module names to execute sequentially
  repeated string module_names = 1;
  // Input for the first module in the chain
  bytes input_data = 2;
  string execution_id = 3;
  map<string, string> metadata = 4;
}

message ExecuteChainResponse {
  oneof result {
    bytes output_data = 1;      // Final output from last module in chain
    ChainError error = 2;
  }
  ExecutionMetrics aggregate_metrics = 3;
}

message ChainError {
  string failed_module = 1;     // Which module in the chain failed
  int32 step_index = 2;         // Position in the chain (0-indexed)
  ExecutionError error = 3;
}
```

### Capability Negotiation

```protobuf
message ModuleCapabilities {
  bool supports_batch = 1;
  int32 max_batch_size = 2;
  bool supports_streaming = 3;
  bool supports_chaining = 4;   // New
}
```

All modules in a chain must individually declare `supports_chaining = true`. The provider guarantees it can execute any declared chain of its own modules without external dependencies.

### Constraints

- All modules in a chain must belong to the **same provider** (same `executor_url` or same provider group)
- The chain must be **linear** — no fan-in, fan-out, or branching within the chain
- Types must be **compatible** — output of module N must match input of module N+1
- Chain boundaries are at provider boundaries — a chain cannot span multiple providers

### Composability with Phases 1 and 2

Chaining composes naturally with batching and streaming:

```
[batch 100 events] → [gRPC stream: chain(A→B→C) × 100] → [unbatch]
```

This combines all three optimizations: 100 events batched, sent over a persistent stream, executed as a chain. For a 5-module pipeline processing 1,000 events/sec: from 5,000 calls/sec down to ~10 calls/sec (100-event batches, single chain call per batch).

---

## Rejected Alternative: Full Subgraph Offloading

We considered a more ambitious approach: sending an arbitrary subgraph (not just a linear chain) to the provider for local execution. The provider would receive a sub-DAG specification, execute it locally (including fan-in, fan-out, and parallelism), and return the aggregated results.

**Why it was rejected:**

1. **Type system duplication.** The provider would need a local type checker to validate the sub-DAG — duplicating Constellation's type system across every SDK language.
2. **DAG runtime duplication.** Executing a sub-DAG requires the full `Runtime` engine (topological ordering, data table, module status tracking, error propagation). Reimplementing this in Python/TypeScript SDKs is a massive surface area with divergence risk.
3. **Debugging opacity.** When a sub-DAG fails, attribution becomes difficult — which module failed, at which step, with which inputs? The intermediate state is invisible to Constellation's monitoring.
4. **Incremental complexity.** Provider-side chaining (Phase 3) captures the most common case (linear runs of same-provider modules) with ~20% of the implementation complexity.
5. **Diminishing returns.** The vast majority of provider-hosted pipelines are linear chains or near-linear. Complex DAGs with fan-in/fan-out across provider boundaries are rare and likely indicate a design that should be split across providers anyway.

The provider is an **executor**, not a **runtime**. Keeping the DAG execution responsibility in Constellation preserves the single source of truth for type safety, monitoring, and error attribution.

---

## Implementation Plan

### Phase 0: Fix the Wiring Gap (Prerequisite)

**Estimated scope:** Small — 3-4 files, no protocol changes

1. Pass `moduleOptions` through `ConstellationRoutes` → `StreamRoutes` → `StreamCompiler`
2. Add `moduleOptions` parameter to `StreamCompiler.wire()` and `wireWithConfig()`
3. Apply `batchSize` / `batchTimeoutMs` via `fs2.Stream.groupWithin` in `buildGraph`
4. Test with in-process modules (batching semantics, not provider-specific)

This unblocks the existing `with batch:` syntax for all streaming pipelines, regardless of provider involvement.

### Phase 1: Batch Invocation (Provider-Specific)

1. Add `ExecuteBatch` RPC and messages to `provider.proto`
2. Add `ModuleCapabilities` to `ModuleDeclaration`
3. Implement `ExecuteBatch` handler in SDK (TypeScript + Scala)
4. Add batch dispatch logic to `ExternalModule`
5. Integration tests: batch vs. per-event throughput comparison

### Phase 2: Persistent Streaming Connections

1. Add `ExecuteStream` RPC to `provider.proto`
2. Implement `StreamExecutor` in `module-provider` (connection manager + correlation)
3. Add `supports_streaming` capability
4. SDK: implement stream handler registration
5. Integration tests: connection reuse, reconnection, backpressure

### Phase 3: Provider-Side Chaining

1. Add `ExecuteChainRequest` / `ExecuteChainResponse` to `provider.proto`
2. Implement chain detection in `StreamCompiler.buildGraph`
3. Add `supports_chaining` capability
4. SDK: implement chain handler registration
5. Integration tests: chain execution, error attribution, mixed chain/non-chain pipelines

---

## Performance Targets

| Metric | Current (v4) | Phase 1 | Phase 2 | Phase 3 |
|--------|-------------|---------|---------|---------|
| Calls/sec (5 modules, 1K events/sec) | 5,000 | 50 | 50 | 10 |
| Overhead per event (5 modules) | ~10ms | ~0.1ms | ~0.05ms | ~0.02ms |
| Provider throughput ceiling | ~500 events/sec | ~50K events/sec | ~100K events/sec | ~200K events/sec |
| Latency per event (p50) | ~10ms | ~5ms (batch wait) | ~3ms | ~2ms |

Targets assume 2ms base gRPC round-trip, batch size 100, 5-module linear pipeline.

---

## Backwards Compatibility

All three phases are **additive and backwards-compatible**:

- Existing providers that don't declare capabilities continue to use unary `Execute()` — no changes needed
- Capability negotiation uses proto3 default values (`false` / `0`) — old providers automatically declare no advanced capabilities
- The `Execute()` RPC is never removed — it remains the fallback for providers that don't support batch, streaming, or chaining
- `moduleOptions` defaults to `Map.empty` — existing `wire()` call sites continue to work

---

## Open Questions

1. **Batch error semantics.** If 3 of 100 events in a batch fail, should the entire batch fail or should failures be per-event? Current design uses per-event results (`ExecuteBatchResult`), but the streaming pipeline needs to decide how to handle partial failures (skip, retry, DLQ).

2. **Chain type validation.** Should chain compatibility be validated at registration time (provider declares which chains are valid) or at pipeline compilation time (Constellation infers chains from the DAG)? Current design uses compilation-time inference.

3. **Batch size tuning.** Should batch size be provider-declared (`max_batch_size` in capabilities), user-declared (`with batch: 100`), or auto-tuned based on observed throughput? Current design allows both provider and user declarations, with `min(provider_max, user_declared)` as the effective size.

---

## References

- RFC-024: Module Provider Protocol v4 — current protocol, gRPC architecture, executor pool
- RFC-025: Streaming Pipelines — streaming compilation, `StreamCompiler`, connector framework
- Issue #230: Collect pseudo-module for streaming (related — batch semantics)
- Issue #231: Circuit breaker for streaming (related — error handling in batch context)
- Issue #235: DAG cache for streaming (related — compilation performance)
