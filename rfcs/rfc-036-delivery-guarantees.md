# RFC-036: Streaming Delivery Guarantees

**Status:** Draft
**Priority:** P1 (Streaming Reliability)
**Author:** Francisco + Claude
**Created:** 2026-02-22
**Depends on:** RFC-025 (Streaming Pipelines), RFC-035 (Stream Circuit Breaker)
**Splits from:** RFC-025 Phase 5, Issue #231

---

## Summary

Formalize and complete the **delivery guarantee** system for streaming pipelines. RFC-025 defined three guarantee levels (at-most-once, at-least-once, exactly-once) and partially implemented the first two. This RFC:

1. **Hardens at-least-once** — closes gaps in the current offset commit protocol (per-element vs batched commits, fan-out acknowledgment, failure replay)
2. **Introduces exactly-once** — defines a transactional connector SPI and a two-phase commit protocol for source-to-sink atomic delivery
3. **Adds deployment-time validation** — the system rejects pipeline configurations that claim a guarantee level their connectors can't support

---

## Motivation

### Current State

| Guarantee | Defined | Implemented | Gaps |
|-----------|---------|-------------|------|
| **At-most-once** | Yes | Yes | None — fire-and-forget is the default, works correctly |
| **At-least-once** | Yes | Partial | Offset commit exists (`OffsetCommitter`) but is not wired into `StreamCompiler`. No connector implements it. No fan-out coordination. |
| **Exactly-once** | Yes (spec) | No | Not started. Requires transactional SPI. |

The `DeliveryGuarantee` enum, `OffsetCommitter` trait, and `Offset` type exist in `modules/stream/src/main/scala/io/constellation/stream/delivery/`. But the `StreamCompiler` never calls `offsetCommitter.commit()` — offsets are defined but not committed.

### What This Enables

- **At-least-once:** On pipeline restart, the source replays from the last committed offset. No data loss. Elements may be reprocessed (modules should be idempotent or duplicate-tolerant).
- **Exactly-once:** For compatible connector pairs (e.g., Kafka → Kafka), each element is processed and delivered exactly once, even across restarts. This is the gold standard for financial transactions, inventory updates, and other non-idempotent workloads.
- **Deployment validation:** `exactly_once: true` on a pipeline with a memory source fails at deploy time with a clear error, rather than silently degrading.

---

## Design

### Delivery Guarantee Levels

```
At-Most-Once          At-Least-Once              Exactly-Once
─────────────         ─────────────              ────────────
source.pull()         source.pull()              txn.begin()
  │                     │  (no commit yet)         source.pull()
  ▼                     ▼                            │
process(elem)         process(elem)              process(elem)
  │                     │                            │
  ▼                     ▼                            ▼
sink.write(result)    sink.write(result)          sink.write(result)  ← transactional
  │                     │                            │
  done                  ▼                            ▼
                      source.commit(offset)       txn.commit()        ← atomic
                        │                            │
                        done                         done
```

### Part 1: Hardening At-Least-Once

#### Offset Embedding

The current `OffsetCommitter` has `commit(offset: Offset)` but no mechanism to associate an offset with a specific element. The source connector must embed offset metadata:

```scala
// Extend SourceConnector with offset-aware streaming
trait OffsetAwareSource extends SourceConnector {
  def streamWithOffsets(config: ValidatedConnectorConfig): Stream[IO, (CValue, Offset)]
  override def deliveryGuarantee: DeliveryGuarantee = DeliveryGuarantee.AtLeastOnce
}
```

For sources that don't support offsets, the default `stream()` method continues to work with `AtMostOnce` semantics.

#### Commit Strategy

Not every element should trigger an offset commit — that would be prohibitively expensive for high-throughput sources. Three strategies:

| Strategy | Behavior | Use Case |
|----------|----------|----------|
| `PerElement` | Commit after every element | Low-throughput, maximum safety |
| `Interval(duration)` | Commit on a timer (e.g., every 5s) | Default — good balance |
| `Count(n)` | Commit every N elements | Batch-oriented workloads |

```scala
sealed trait CommitStrategy
object CommitStrategy {
  case object PerElement                       extends CommitStrategy
  case class Interval(every: FiniteDuration)   extends CommitStrategy
  case class Count(every: Int)                 extends CommitStrategy
}
```

Default: `Interval(5.seconds)`.

#### Fan-Out Coordination

RFC-025 states: "Write to ALL sinks (fan-out: wait for every branch)." For at-least-once, the offset must only be committed after **all** sink branches have confirmed the element. This requires:

1. A `Deferred` per element that completes when all sink branches have written
2. A background fiber that commits offsets as their corresponding Deferreds complete
3. Ordering guarantee: offsets commit in sequence (no gaps)

```
source.pull() → elem(offset=42)
  ├─► branch A: sink_a.write(result_a) → ack_a
  └─► branch B: sink_b.write(result_b) → ack_b
                                           │
  (ack_a AND ack_b) ──────────────────────►commit(offset=42)
```

#### Wiring into StreamCompiler

`StreamCompiler.wire()` gains a `commitStrategy` parameter. When the source is `OffsetAwareSource` and delivery is `AtLeastOnce`:

1. Use `streamWithOffsets()` instead of `stream()`
2. After the module pipeline and all sink branches complete per element, commit the offset
3. On pipeline restart, the source resumes from `offsetCommitter.currentOffset()`

### Part 2: Exactly-Once via Transactional SPI

#### Transactional Connector Traits

```scala
trait TransactionalSource extends SourceConnector {
  def deliveryGuarantee: DeliveryGuarantee = DeliveryGuarantee.ExactlyOnce
  def beginTransaction: IO[SourceTransaction]
}

trait SourceTransaction {
  def pull: Stream[IO, (CValue, Offset)]
  def commit: IO[Unit]
  def abort: IO[Unit]
}

trait TransactionalSink extends SinkConnector {
  def beginTransaction: IO[SinkTransaction]
}

trait SinkTransaction {
  def write(value: CValue): IO[Unit]
  def commit: IO[Unit]
  def abort: IO[Unit]
}
```

#### Two-Phase Commit Protocol

For `ExactlyOnce` pipelines:

```
1. source_txn = source.beginTransaction()
2. sink_txn   = sink.beginTransaction()       // one per sink branch
3. element    = source_txn.pull()
4. result     = process(element)
5. sink_txn.write(result)                      // for each sink
6. sink_txn.commit()                           // Phase 1: sinks commit
7. source_txn.commit()                         // Phase 2: source commits
```

If step 4 or 5 fails:
```
sink_txn.abort()
source_txn.abort()
→ element will be re-pulled on next transaction
```

If step 6 fails (sink commit):
```
source_txn.abort()
→ element will be re-pulled; sink's failed commit means nothing was written
```

If step 7 fails (source commit after sink committed):
```
→ element was written to sink but source offset not committed
→ on restart, element is re-pulled and re-processed
→ sink must be IDEMPOTENT or use deduplication (e.g., Kafka producer idempotence)
```

This is the standard "at-least-once with idempotent sinks" pattern that systems like Kafka Streams use to achieve effectively-exactly-once semantics.

#### Deployment Validation

When a pipeline is deployed with `exactly_once: true`:

```scala
def validateDeliveryGuarantee(
    sources: Map[String, SourceConnector],
    sinks: Map[String, SinkConnector],
    requested: DeliveryGuarantee
): Either[String, Unit] = requested match {
  case DeliveryGuarantee.ExactlyOnce =>
    val nonTransactionalSources = sources.filterNot(_._2.isInstanceOf[TransactionalSource])
    val nonTransactionalSinks   = sinks.filterNot(_._2.isInstanceOf[TransactionalSink])
    if (nonTransactionalSources.nonEmpty || nonTransactionalSinks.nonEmpty)
      Left(s"Exactly-once requires transactional connectors. " +
           s"Non-transactional sources: ${nonTransactionalSources.keys.mkString(", ")}. " +
           s"Non-transactional sinks: ${nonTransactionalSinks.keys.mkString(", ")}.")
    else Right(())
  case _ => Right(())
}
```

This runs at deploy time (`StreamRoutes.deployStream`) and returns a `400 Bad Request` if the connectors don't support the requested guarantee.

### Part 3: Extend `DeliveryGuarantee` Enum

```scala
sealed trait DeliveryGuarantee
object DeliveryGuarantee {
  case object AtMostOnce  extends DeliveryGuarantee
  case object AtLeastOnce extends DeliveryGuarantee
  case object ExactlyOnce extends DeliveryGuarantee
}
```

### Configuration Surface

Delivery guarantees are set per pipeline at deployment time:

```json
POST /api/v1/streams
{
  "pipelineRef": "my-pipeline",
  "sourceBindings": { ... },
  "sinkBindings": { ... },
  "options": {
    "deliveryGuarantee": "at-least-once",
    "commitStrategy": "interval",
    "commitInterval": "5s"
  }
}
```

---

## Implementation Plan

### Phase 1: Harden At-Least-Once (~300 LOC)

- [ ] Add `OffsetAwareSource` trait extending `SourceConnector`
- [ ] Add `CommitStrategy` enum to `StreamOptions`
- [ ] Wire offset commit into `StreamCompiler` — commit after sink confirmation
- [ ] Implement batched commit (interval-based, default 5s)
- [ ] Add `deliveryGuarantee` field to `StreamDeployRequest`
- [ ] Unit tests: offset commit after processing, replay on restart

### Phase 2: Exactly-Once SPI (~250 LOC)

- [ ] Add `ExactlyOnce` to `DeliveryGuarantee` enum
- [ ] Define `TransactionalSource` and `TransactionalSink` traits
- [ ] Define `SourceTransaction` and `SinkTransaction` traits
- [ ] Implement two-phase commit orchestration in `StreamCompiler`
- [ ] Add deployment-time validation (reject incompatible connectors)
- [ ] Unit tests with mock transactional connectors

### Phase 3: Fan-Out Acknowledgment (~200 LOC)

- [ ] Implement per-element `Deferred`-based acknowledgment for multi-sink pipelines
- [ ] Ensure offset commits are ordered (no gaps)
- [ ] Integration test: fan-out with at-least-once, verify all branches ack before commit

### Phase 4: Memory Connector Reference Implementation (~100 LOC)

- [ ] Implement `OffsetAwareMemorySource` (for testing at-least-once)
- [ ] Implement `TransactionalMemorySource` / `TransactionalMemorySink` (for testing exactly-once)
- [ ] These are test-only implementations — real connectors (Kafka) come in separate RFCs

---

## Alternatives Considered

### Exactly-once via distributed transactions (2PC with coordinator)

Rejected for initial implementation. A full two-phase commit with a separate transaction coordinator adds significant complexity (coordinator availability, timeout handling, participant recovery). The Kafka-style approach (at-least-once + idempotent sinks) achieves effectively-exactly-once with much simpler machinery. A future RFC could add coordinator-based 2PC for heterogeneous source/sink pairs.

### Offset tracking inside CValue

One approach is to embed offset metadata inside each `CValue` as it flows through the pipeline. Rejected — this leaks transport concerns into the domain model. Offsets are tracked in a parallel channel alongside the main data stream.

### Library-level exactly-once (e.g., fs2-kafka transactions)

For Kafka-to-Kafka pipelines, fs2-kafka provides built-in transactional semantics. The connector SPI proposed here is intentionally more abstract — it supports Kafka but also other transactional systems. The Kafka connector RFC will use fs2-kafka's transactional APIs internally while conforming to the `TransactionalSource`/`TransactionalSink` SPI.

---

## Open Questions

1. **Should `AtLeastOnce` be the default for persistent connectors?** Currently `AtMostOnce` is the universal default. Changing the default for persistent sources would be safer but might surprise users with unexpected replay behavior. Proposed: keep `AtMostOnce` as default everywhere, require explicit opt-in.

2. **How should exactly-once interact with the circuit breaker (RFC-035)?** When the circuit opens, in-flight transactions should be aborted. The circuit breaker's `Open` state should trigger `txn.abort()` for any pending transactions. Proposed: document this interaction but implement in the circuit breaker RFC.

3. **What happens to exactly-once during hot-reload?** Pipeline schema changes during a transaction could corrupt state. Proposed: drain all in-flight transactions before applying a hot-reload, same as the existing graceful drain mechanism.
