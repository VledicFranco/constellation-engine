# RFC-034: Module Provider Performance Optimizations

**Status:** Accepted
**Priority:** P1 (Streaming Performance)
**Author:** Francisco + Claude
**Created:** 2026-02-22
**Depends on:** RFC-024 (Module Provider Protocol v4), RFC-025 (Streaming Pipelines)

---

## Summary

The current Module Provider Protocol (RFC-024) executes one gRPC call per module invocation per event. In streaming pipelines, this produces `M × N` network round-trips per second (M modules × N events/sec), which is a fundamental performance bottleneck. A 5-module pipeline processing 1,000 events/sec generates 5,000 gRPC calls/sec — each carrying serialization, network, and deserialization overhead.

This RFC defines three incremental optimization phases that progressively reduce this overhead without changing the core protocol's semantics:

1. **Batch Invocation** — Amortize N events into a single gRPC call. Reduces calls by ~100x for typical batch sizes.
2. **Persistent Streaming Connections** — Replace per-call unary gRPC with bidirectional streaming. Amortizes HTTP/2 framing overhead and enables flow-control-based backpressure.
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

The constellation-lang parser, AST, IR, and core type system already support `with batch:` and `with window:` syntax. The full compilation pipeline produces `ModuleCallOptions` with `batchSize`, `batchTimeoutMs`, and `window` fields (defined in `ModuleCallOptions.scala`, lines 48-50). However:

1. **`StreamRoutes.doDeploy` omits `moduleOptions` when calling `StreamCompiler.wireWithConfig()`** (line 216), defaulting to `Map.empty`. All compiled module options — batch size, timeout, window — are discarded for the streaming path. (The batch execution path in `ConstellationRoutes` correctly propagates `image.moduleOptions`.)
2. **`StreamCompiler.buildGraph` only applies `moduleOptions` to the `collect` pseudo-module** (lines 202-206). Regular module nodes ignore `batchSize` even when it's present — no `groupWithin` is applied.
3. **`ExternalModule.callExecutor()` sends a single `ExecuteRequest`** per invocation (lines 202-218) — the protobuf has no batch variant.
4. **The gRPC `ModuleExecutor` service defines only unary `Execute()`** (provider.proto, lines 31-33) — no streaming or batch RPC exists.

The language already promises batching. The runtime doesn't deliver it. This RFC closes that gap.

### Why Not "Just Use Scala"?

Module providers exist for a reason: polyglot teams, existing service infrastructure, independent deployment. Telling users "for performance, don't use providers" defeats the purpose. The goal is to make providers viable for streaming workloads — not as fast as in-process Scala, but fast enough that the tradeoff is acceptable.

**Target:** Provider-backed streaming pipelines should achieve ≥80% of the throughput of equivalent in-process pipelines for I/O-bound workloads (where module execution time dominates serialization overhead).

---

## Phase 1: Batch Invocation Protocol

**Complexity:** Low
**Impact:** ~100x call reduction for typical batch sizes
**Prerequisite:** Phase 0 (wire `moduleOptions` through to `StreamCompiler`)

### Design

Accumulate N events into a batch, send as a single gRPC call, receive N results. The provider processes all events in one invocation — amortizing connection overhead, enabling provider-side vectorization, and reducing serialization round-trips.

```
Before (N=100):  100 × [serialize → gRPC → deserialize] = 100 round-trips
After  (N=100):    1 × [serialize batch → gRPC → deserialize batch] = 1 round-trip
```

### Batch Error Semantics

Batching is a **transport optimization**, not a semantic grouping. Each element in a batch is independent:

- The provider returns per-event results via `ExecuteBatchResult` (success or error for each input).
- On the Constellation side, `buildGraph` zips batch results back to their source elements.
- Each result follows the pipeline's existing `StreamErrorStrategy` (Skip, Log, DLQ, Propagate) — identical to unbatched behavior.
- A batch never fails atomically. If 3 of 100 events fail, the 97 successes are emitted and the 3 failures are handled per-strategy.

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

#### 1. Wire `moduleOptions` to StreamCompiler (Phase 0)

`StreamRoutes.doDeploy` currently omits `moduleOptions` when calling `wireWithConfig()`. Fix:

```scala
// In StreamRoutes.doDeploy (line 216):
// Before — moduleOptions defaults to Map.empty:
graph <- StreamCompiler.wireWithConfig(
  dagSpec, config, registry, moduleFns,
  streamOptions, errorStrategy, joinStrategy
)

// After — pass compiled module options:
graph <- StreamCompiler.wireWithConfig(
  dagSpec, config, registry, moduleFns,
  streamOptions, errorStrategy, joinStrategy,
  image.moduleOptions
)
```

Note: `StreamCompiler.wire()` and `wireWithConfig()` already accept a `moduleOptions: Map[UUID, ModuleCallOptions]` parameter (defaulting to `Map.empty`). No signature changes needed. Phase 0 is just this 1-line fix — it threads the options through so they're available in `buildGraph`. The `groupWithin` logic is part of Phase 1.

#### 2. Batch Module Function Type

`StreamCompiler` currently receives `Map[UUID, CValue => IO[CValue]]` — per-element functions. Batch execution requires a different function signature that operates on chunks. Rather than changing the existing map type, add a parallel parameter:

```scala
def wire(
    dagSpec: DagSpec,
    registry: ConnectorRegistry,
    modules: Map[UUID, CValue => IO[CValue]],
    // New: batch-capable module functions (Phase 1)
    batchModules: Map[UUID, List[CValue] => IO[List[Either[Throwable, CValue]]]] = Map.empty,
    options: StreamOptions = StreamOptions(),
    // ...existing params...
    moduleOptions: Map[UUID, ModuleCallOptions] = Map.empty
): IO[StreamGraph]
```

`StreamRoutes.extractModuleFunctions` populates `batchModules` for external modules whose provider declares `supports_batch = true`. In-process modules are never added to `batchModules` — batching only benefits external calls where it amortizes network overhead.

#### 3. Batch Accumulation in StreamCompiler

In `buildGraph`, when a module has both a batch function and `batchSize > 1` in its options, use `groupWithin` + batch dispatch:

```scala
// Inside buildGraph, standard module processing:
val opts         = moduleOptions.get(moduleId)
val batchSize    = opts.flatMap(_.batchSize).getOrElse(1)
val batchTimeout = opts.flatMap(_.batchTimeoutMs)
  .map(ms => FiniteDuration(ms, TimeUnit.MILLISECONDS))
  .getOrElse(1.second)
val batchFn      = batchModules.get(moduleId)

val processed = (batchFn, batchSize > 1) match {
  case (Some(bf), true) =>
    // Batch path: group elements, call batch function, flatten results
    inputStream
      .groupWithin(batchSize, batchTimeout)
      .evalMap { chunk =>
        bf(chunk.toList).flatMap { results =>
          // Record metrics per-element within the batch
          results.traverse {
            case Right(v)  => metrics.recordElement(moduleName).as(v)
            case Left(err) => metrics.recordError(moduleName).as(
              CValue.CString(s"error: ${safeMessage(err)}")  // or skip/dlq per strategy
            )
          }
        }
      }
      .flatMap(results => Stream.emits(results))
  case _ =>
    // Per-element path: existing evalMap (unchanged)
    inputStream.evalMap(fn)
}
```

Important: `groupWithin` is only applied when a batch function exists. In-process modules always use the per-element path — batching adds latency without improving throughput when there's no network overhead to amortize.

#### 4. ExternalModule Batch Dispatch

`ExternalModule` checks whether the provider supports batching. If so, it exposes a batch-aware function that `StreamCompiler` can call with a chunk of elements:

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

When the provider declares `supports_batch = false`, the module is not added to `batchModules` and follows the standard per-element `Execute()` path — no `groupWithin` is applied. Batching is only meaningful when the provider can process a batch in a single RPC call.

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

If `executeBatch` is provided, the SDK advertises `supports_batch = true` in `ModuleCapabilities`. If only `execute` is provided, the SDK auto-generates a batch handler that loops over the single-event handler (still benefits from amortized network overhead, though not from provider-side vectorization).

### Constellation-Lang Integration

The existing `with batch:` syntax already compiles through to `ModuleCallOptions.batchSize`. Once Phase 0 wires `moduleOptions` through, this works end-to-end:

```constellation
use ml.sentiment

in events: Stream<String>

results = analyze(events) with {
  batch: 100,
  timeout: 1s
}

out results
```

### Batch Size Resolution

When both the user and provider declare batch sizes, the effective size is `min(provider_max, user_declared)`. If neither is set, no batching occurs (batch size defaults to 1) — batching is always explicit opt-in.

---

## Phase 2: Persistent Streaming Connections

**Complexity:** Medium
**Impact:** Amortizes per-RPC HTTP/2 framing overhead, enables flow-control backpressure
**Prerequisite:** Phase 1 (batch infrastructure)

### Design

Replace unary `Execute` / `ExecuteBatch` RPCs with a long-lived bidirectional gRPC stream. Events flow continuously without per-call RPC setup:

```
Before: [RPC setup → request → response → RPC teardown] × N
After:  [stream open] → [request → response] × N → [stream close]
```

Note: `GrpcChannelCache` already reuses `ManagedChannel` instances per `executorUrl`, so TCP connections are not created per call. The overhead Phase 2 eliminates is per-RPC HTTP/2 stream framing, header compression, and provider-side request handler allocation — not TCP connections.

### Protocol Changes

```protobuf
service ModuleExecutor {
  rpc Execute(ExecuteRequest) returns (ExecuteResponse);         // unchanged
  rpc ExecuteBatch(ExecuteBatchRequest) returns (ExecuteBatchResponse);  // Phase 1
  // New — persistent bidirectional stream
  rpc ExecuteStream(stream StreamExecuteRequest) returns (stream StreamExecuteResponse);
}

// Wraps ExecuteRequest with a correlation ID for multiplexed matching
message StreamExecuteRequest {
  string correlation_id = 1;   // Unique per-request, echoed in response
  ExecuteRequest request = 2;
}

message StreamExecuteResponse {
  string correlation_id = 1;   // Matches the request's correlation_id
  ExecuteResponse response = 2;
}
```

The `correlation_id` is a UUID generated per-call by Constellation. The provider echoes it back in the response. This enables multiplexing multiple module calls over a single stream with correct request-response matching. (The existing `execution_id` is a pipeline-level ID, not suitable for per-call correlation.)

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
- Sends `StreamExecuteRequest` messages with unique `correlation_id`
- Matches responses by `correlation_id` using a `ConcurrentHashMap[String, Deferred[IO, ExecuteResponse]]`
- Handles backpressure via gRPC flow control
- Reconnects on stream failure (with exponential backoff)
- Closes when the streaming pipeline shuts down (via `Resource` lifecycle)

### Benefits

- Amortizes per-RPC HTTP/2 framing and header compression overhead
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

Chain detection happens in `StreamRoutes` (not `StreamCompiler`) because it requires provider metadata that `StreamCompiler` doesn't have. `StreamCompiler` receives `Map[UUID, CValue => IO[CValue]]` — opaque functions with no provider affinity information.

The detection algorithm:

1. Walk the topologically-sorted module IDs from the `DagSpec`
2. For each module, check if it's an `ExternalModule` and which `executor_url` serves it
3. Find maximal linear runs of consecutive modules sharing the same `executor_url`
4. For each run of length ≥ 2 where all modules declare `supports_chaining = true`, replace the individual module functions with a single chained function that sends `ExecuteChainRequest`
5. Pass the modified `modules` map to `StreamCompiler.wire()`

This keeps `StreamCompiler` provider-agnostic — it sees a single function where it previously saw three.

### Protocol Changes

```protobuf
service ModuleExecutor {
  // ...existing RPCs from Phases 1-2...
  rpc ExecuteChain(ExecuteChainRequest) returns (ExecuteChainResponse);  // New
}
```

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

### Phase 0: Fix the Wiring Gap (~30 LOC)

**Scope:** Minimal — 1 production line + 1 test file. No protocol or signature changes.

1. Pass `image.moduleOptions` in `StreamRoutes.doDeploy` call to `wireWithConfig()` (1-line fix)
2. Unit test: verify `moduleOptions` propagates from `PipelineImage` through to `buildGraph`

This threads compiled module options into the streaming path so they're available for Phase 1. A standalone deliverable — no behavioral change until `buildGraph` uses the options.

### Phase 1: Batch Invocation (~400 LOC)

**Scope:** Runtime batch accumulation + provider batch protocol + SDK support.

Runtime (Constellation-side):
1. Add `batchModules: Map[UUID, List[CValue] => IO[List[Either[Throwable, CValue]]]]` parameter to `StreamCompiler.wire()` / `wireWithConfig()`
2. In `buildGraph`, apply `groupWithin(batchSize, batchTimeout)` when a module has a batch function and `batchSize > 1` (the logic from Runtime Changes §3)
3. Handle per-element errors within batched chunks (zip results back, apply `StreamErrorStrategy`)
4. Populate `batchModules` in `StreamRoutes.extractModuleFunctions` for external modules with `supports_batch = true`

Provider protocol:
5. Add `ExecuteBatch` RPC and messages to `provider.proto`
6. Add `ModuleCapabilities` to `ModuleDeclaration`
7. Implement `callExecutorBatch` in `ExternalModule`
8. Implement `ExecuteBatch` handler in SDK (TypeScript + Scala)

Testing:
9. Unit tests: batch grouping, timeout flush, error handling within batch
10. Integration tests: batch vs. per-event throughput comparison with real provider

### Phase 2: Persistent Streaming Connections (~550 LOC)

1. Add `ExecuteStream` RPC with `StreamExecuteRequest`/`StreamExecuteResponse` (includes `correlation_id`) to `provider.proto`
2. Implement `StreamExecutor` in `module-provider` — connection lifecycle via `Resource`, correlation via `ConcurrentHashMap[String, Deferred]`, reconnection with exponential backoff
3. Add `supports_streaming` capability negotiation
4. SDK: implement stream handler registration
5. Integration tests: connection reuse, reconnection after failure, backpressure propagation, correlation correctness under concurrent load

### Phase 3: Provider-Side Chaining (~350 LOC)

1. Add `ExecuteChainRequest` / `ExecuteChainResponse` / `ChainError` to `provider.proto`
2. Implement chain detection in `StreamRoutes` — walk topological order, find maximal same-provider linear runs, replace with chained functions
3. Add `supports_chaining` capability negotiation
4. SDK: implement chain handler registration
5. Integration tests: chain execution, `ChainError` attribution, mixed chain/non-chain pipelines, chain detection correctness

---

## Performance Targets

| Metric | Current (v4) | Phase 0+1 | Phase 2 | Phase 3 |
|--------|-------------|-----------|---------|---------|
| Calls/sec (5 modules, 1K events/sec) | 5,000 | 50 | 50 | 10 |
| Overhead per event (5 modules) | ~10ms | ~0.1ms | ~0.05ms | ~0.02ms |
| Provider throughput ceiling | ~500 events/sec | ~50K events/sec | ~100K events/sec | ~200K events/sec |
| Latency per event (p50) | ~10ms | ~5ms (batch wait) | ~3ms | ~2ms |

Targets assume 2ms base gRPC round-trip, batch size 100, 5-module linear pipeline. "Current (v4)" numbers are estimates — Phase 1 should include a benchmark measuring actual baseline overhead before batch optimizations begin. The existing `StreamingModuleBenchmark` (from issue #235) provides per-element overhead baselines for in-process modules.

---

## Backwards Compatibility

All phases are **additive and backwards-compatible**:

- Existing providers that don't declare capabilities continue to use unary `Execute()` — no changes needed
- Capability negotiation uses proto3 default values (`false` / `0`) — old providers automatically declare no advanced capabilities
- The `Execute()` RPC is never removed — it remains the fallback for providers that don't support batch, streaming, or chaining
- `moduleOptions` defaults to `Map.empty` — existing `wire()` call sites continue to work
- Batch size defaults to 1 (no batching) — explicit opt-in required via `with batch:` syntax

---

## Design Decisions

These were open questions in earlier drafts, now resolved:

1. **Batch error semantics:** Per-event. A batch is a transport optimization, not a semantic unit. Each `ExecuteBatchResult` maps 1:1 back to its source element and follows the pipeline's `StreamErrorStrategy`. A batch never fails atomically.

2. **Chain type validation:** Compilation-time inference. Constellation detects chainable runs from the DAG topology and provider affinity at deploy time. Registration-time declaration would over-constrain — providers shouldn't need to enumerate all valid chains upfront.

3. **Batch size resolution:** `min(provider_max, user_declared)`. If neither is set, batch size is 1 (no batching). Explicit opt-in prevents surprising behavior changes.

4. **Chain detection location:** `StreamRoutes`, not `StreamCompiler`. `StreamCompiler` receives opaque `CValue => IO[CValue]` functions — it has no provider metadata. Chain detection requires knowing which `executor_url` serves each module, which is available in `StreamRoutes` when building the `modules` map.

5. **Streaming connection correlation:** Dedicated `correlation_id` field in `StreamExecuteRequest`/`StreamExecuteResponse`. The existing `execution_id` is pipeline-level, not per-call. `correlation_id` is a UUID generated per request, echoed by the provider for multiplexed matching.

---

## References

- RFC-024: Module Provider Protocol v4 — current protocol, gRPC architecture, executor pool
- RFC-025: Streaming Pipelines — streaming compilation, `StreamCompiler`, connector framework
- Issue #230: Collect pseudo-module for streaming (related — batch semantics)
- Issue #231: Circuit breaker for streaming (related — error handling in batch context)
- Issue #235: DAG cache for streaming (related — compilation performance)
