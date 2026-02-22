# RFC-035: Stream-Level Circuit Breaker

**Status:** Draft
**Priority:** P1 (Streaming Reliability)
**Author:** Francisco + Claude
**Created:** 2026-02-22
**Depends on:** RFC-025 (Streaming Pipelines)
**Splits from:** RFC-025 Phase 5, Issue #231

---

## Summary

Add a **stream-level circuit breaker** that pauses source consumption when consecutive element failures exceed a configurable threshold. This protects downstream systems from poisoned streams and prevents DLQ flooding, while allowing automatic recovery through a half-open probe mechanism.

The existing `CircuitBreaker` in `runtime/execution/` protects **individual module calls** (per-invocation). This RFC introduces a **per-stream** circuit breaker that operates at the pipeline level — pausing the entire source when the stream is unhealthy, rather than rejecting individual operations.

---

## Motivation

### The Problem

A streaming pipeline processing events from an external source (Kafka, WebSocket, HTTP SSE) can encounter sustained failure modes:

- **Poisoned partition:** A batch of malformed events causes every module invocation to fail
- **Downstream outage:** A sink's backing service goes down, causing all writes to fail
- **Resource exhaustion:** Memory pressure or connection pool saturation causes cascading failures

Without a circuit breaker, the stream continues pulling events, failing on each one, and routing them to the DLQ. At 1,000 events/sec with a sustained failure, the DLQ receives 1,000 poison records per second — compounding the problem instead of containing it.

### Current State

| Component | Exists | Scope |
|-----------|--------|-------|
| `CircuitBreaker` trait | Yes (`runtime/execution/`) | Per-module-invocation, request-response mode |
| `CircuitBreakerRegistry` | Yes (`runtime/execution/`) | Creates per-module-name breakers for `Runtime.runWithBackends` |
| `StreamEvent.CircuitOpen` | Yes (`stream/StreamEvent.scala`) | Event type defined but never emitted |
| `StreamEvent.CircuitClosed` | Yes (`stream/StreamEvent.scala`) | Event type defined but never emitted |
| `StreamOptions.maxConsecutiveErrors` | No | Referenced in RFC-025 spec but not in current `StreamOptions` |
| Stream-level pause/resume | No | Not implemented |

The event types exist. The runtime circuit breaker exists. What's missing is the **stream-level orchestration** that connects them.

### What This Enables

- Streams **self-heal** — pause on sustained failure, probe periodically, resume when healthy
- DLQ stays manageable — bounded error volume during outages
- Dashboard shows circuit state — operators see which streams are paused and why
- Configurable per-pipeline — different thresholds for different risk profiles

---

## Design

### Stream Circuit Breaker State Machine

```
         success
    ┌────────────────┐
    │                │
    ▼     failure    │
 CLOSED ──────────► OPEN ──────────► HALF_OPEN
    ▲   (threshold)   │  (cooldown)      │
    │                 │                  │
    │                 │     failure      │
    │                 ◄──────────────────┘
    │                   (double cooldown)
    │                 │
    │     success     │
    └─────────────────┘
      (from HALF_OPEN)
```

**States:**

| State | Behavior |
|-------|----------|
| `Closed` | Normal operation. Elements flow through the pipeline. Consecutive failure counter increments on error, resets on success. |
| `Open` | Stream paused — source stops pulling. `StreamEvent.CircuitOpen` emitted. Cooldown timer starts. |
| `HalfOpen` | After cooldown expires, pull exactly one element (probe). If it succeeds → `Closed`. If it fails → `Open` with doubled cooldown (capped at `maxCooldown`). |

### Configuration

Extend `StreamOptions` with circuit breaker settings:

```scala
case class StreamOptions(
  // ... existing fields ...
  defaultParallelism: Int = 1,
  defaultBufferSize: Int = 256,
  shutdownTimeout: FiniteDuration = 30.seconds,
  metricsEnabled: Boolean = true,

  // Circuit breaker (new)
  circuitBreakerEnabled: Boolean = true,
  consecutiveFailureThreshold: Int = 100,
  initialCooldown: FiniteDuration = 30.seconds,
  maxCooldown: FiniteDuration = 5.minutes,
  cooldownMultiplier: Double = 2.0
)
```

### New Type: `StreamCircuitBreaker`

```scala
// modules/stream/src/main/scala/io/constellation/stream/circuit/StreamCircuitBreaker.scala

sealed trait StreamCircuitState
object StreamCircuitState {
  case object Closed                                    extends StreamCircuitState
  case class Open(since: Instant, cooldown: FiniteDuration) extends StreamCircuitState
  case object HalfOpen                                  extends StreamCircuitState
}

trait StreamCircuitBreaker {
  def state: IO[StreamCircuitState]
  def recordSuccess: IO[Unit]
  def recordFailure: IO[Unit]
  def shouldPull: IO[Boolean]   // false when Open (and cooldown not expired)
  def stats: IO[StreamCircuitStats]
}

case class StreamCircuitStats(
  consecutiveFailures: Long,
  totalOpens: Long,
  totalProbes: Long,
  lastOpenedAt: Option[Instant],
  currentCooldown: FiniteDuration
)
```

### Integration Point: `StreamCompiler`

The circuit breaker wraps the **source pull**, not individual module calls. In `StreamCompiler.wire()`:

```
source.stream(config)
  .through(circuitBreakerGate)   // <-- new: gate that pauses when open
  .through(modulePipeline)
  .through(errorHandler)
  .through(sink.pipe(config))
```

The `circuitBreakerGate` is an fs2 `Pipe` that:

1. **Closed:** passes elements through, calls `recordSuccess`/`recordFailure` based on downstream outcome
2. **Open:** emits nothing (blocks the pull), polls `shouldPull` on a timer
3. **HalfOpen:** passes exactly one element, transitions based on result

The feedback loop requires the circuit breaker to observe **downstream** results. This means the breaker wraps the entire `modulePipeline + errorHandler` stage, not just the source:

```scala
def withCircuitBreaker(
    breaker: StreamCircuitBreaker,
    eventSink: StreamEvent => IO[Unit]
)(inner: Pipe[IO, CValue, CValue]): Pipe[IO, CValue, CValue] =
  source => source.through(inner).evalTap { _ =>
    breaker.recordSuccess
  }.handleErrorWith { error =>
    Stream.exec(breaker.recordFailure) ++
      Stream.exec(eventSink(StreamEvent.CircuitOpen(...)))
  }
```

The actual implementation is more nuanced (per-element error observation vs stream-level errors), but the principle is: **observe outcomes after module execution, gate before source pull**.

### Event Emission

The existing `StreamEvent.CircuitOpen` and `StreamEvent.CircuitClosed` types are already defined. The circuit breaker emits these through the stream's event callback (passed via `StreamCompiler.wire`).

### Dashboard Integration

No dashboard changes required in this RFC. The `StreamEvent` types are already part of the metrics pipeline — once emitted, they'll appear in stream metrics and the `/api/v1/streams/:id/metrics` endpoint. A follow-up could add visual indicators to the dashboard.

---

## Implementation Plan

### Phase 1: Core `StreamCircuitBreaker` (~200 LOC)

- [ ] Create `modules/stream/src/main/scala/io/constellation/stream/circuit/StreamCircuitBreaker.scala`
- [ ] Implement `Ref`-based state machine: `Closed → Open → HalfOpen → Closed`
- [ ] Implement cooldown with exponential backoff (capped at `maxCooldown`)
- [ ] Unit tests for state transitions, cooldown doubling, reset on success

### Phase 2: `StreamOptions` Extension + Wiring (~150 LOC)

- [ ] Add circuit breaker fields to `StreamOptions`
- [ ] Create `circuitBreakerGate` pipe in `StreamCompiler`
- [ ] Wire into `StreamCompiler.wire()` / `StreamCompiler.wireWithConfig()`
- [ ] Emit `StreamEvent.CircuitOpen` / `StreamEvent.CircuitClosed` at transitions

### Phase 3: Integration Tests (~200 LOC)

- [ ] Test: stream pauses after N consecutive failures
- [ ] Test: stream resumes after cooldown + successful probe
- [ ] Test: cooldown doubles on probe failure (up to max)
- [ ] Test: success resets consecutive failure counter
- [ ] Test: circuit breaker disabled when `circuitBreakerEnabled = false`
- [ ] Test: metrics reflect circuit state transitions

### Phase 4: HTTP API Surface (~50 LOC)

- [ ] Expose circuit breaker state in `/api/v1/streams/:id` response
- [ ] Add `circuitState` field to `StreamInfoResponse` (open/closed/half-open)

---

## Alternatives Considered

### Reuse the existing `CircuitBreaker` from `runtime/execution/`

Rejected. The runtime circuit breaker protects individual `IO[A]` operations — it throws `CircuitOpenException` when open. Stream-level circuit breaking needs to **pause the source pull**, which is a fundamentally different mechanism (fs2 backpressure vs exception throwing). The state machine logic is similar but the integration surface is different.

### Per-module circuit breakers within the stream

The runtime already supports this via `CircuitBreakerRegistry` in `Runtime.runWithBackends`. Per-module breakers are orthogonal to the stream-level breaker proposed here — they protect individual modules from repeated failures, while the stream-level breaker protects the **entire pipeline** from sustained failure cascades. Both can coexist.

### Kill the stream instead of pausing

Rejected. Killing requires manual restart. Pausing with auto-recovery is operationally superior — the stream self-heals when the underlying issue resolves.

---

## Open Questions

1. **Should the circuit breaker count DLQ-routed elements as failures?** If `on_error: dlq` routes an element to the DLQ, that's "handled" from the stream's perspective. But if 100% of elements go to DLQ, the stream is still unhealthy. Proposed: DLQ counts as failure for circuit breaker purposes.

2. **Per-branch circuit breakers in fan-out?** RFC-025 says "each branch has independent error handling." Should each branch get its own circuit breaker, or should one global breaker cover the entire stream? Proposed: start with one per stream, add per-branch in a follow-up if needed.

3. **Should the circuit breaker be exposed in the constellation-lang `with` clause?** E.g., `with { circuit_breaker: { threshold: 50, cooldown: 10s } }`. Proposed: defer to deployment config for now; language support is a separate enhancement.
