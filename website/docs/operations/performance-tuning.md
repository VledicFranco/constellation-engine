---
title: "Performance Tuning"
sidebar_position: 4
description: "Scheduler, circuit breakers, caching, and memory tuning"
---

# Performance Tuning Guide

This guide covers production tuning for Constellation Engine: scheduler configuration, timeouts, circuit breakers, caching, object pooling, JVM settings, and monitoring.

## Scheduler Configuration

### Unbounded vs Bounded

| Mode | Factory | Behavior | Use Case |
|------|---------|----------|----------|
| Unbounded | `GlobalScheduler.unbounded` | Every task runs immediately on the Cats Effect thread pool | Development, low-concurrency |
| Bounded | `GlobalScheduler.bounded(...)` | Priority queue with concurrency limit | Production |

The default is unbounded. For production deployments, use bounded:

```scala
import io.constellation.execution.GlobalScheduler
import scala.concurrent.duration._

GlobalScheduler.bounded(
  maxConcurrency    = 16,
  maxQueueSize      = 1000,
  starvationTimeout = 30.seconds
).use { scheduler =>
  val constellation = ConstellationImpl.builder()
    .withScheduler(scheduler)
    .build()
  // ...
}
```

### maxConcurrency Sizing

| Workload Type | Recommended | Rationale |
|---------------|-------------|-----------|
| CPU-bound modules (ML inference, computation) | cores x 1-2 | Avoid context-switch overhead |
| IO-bound modules (HTTP calls, DB queries) | cores x 4-10 | Threads block on IO, need more |
| Mixed | cores x 2-4 | Balance between CPU and IO |

To find the right value: start with `cores x 2`, monitor queue depth, and adjust.

### maxQueueSize

Controls how many tasks can wait when all concurrency slots are busy.

| Setting | Trade-off |
|---------|-----------|
| Too small (e.g., 10) | Rejects requests quickly under load — good for fail-fast |
| Too large (e.g., 100000) | Absorbs bursts but increases memory and latency — requests wait longer |
| Recommended: 100-1000 | Balances burst absorption with bounded memory |

When the queue is full, new submissions throw `QueueFullException`.

### starvationTimeout

Low-priority tasks get promoted after waiting this long, preventing indefinite starvation:

| Setting | Behavior |
|---------|----------|
| Short (5-10s) | Low-priority tasks run sooner, less priority differentiation |
| Long (60s+) | Strong priority ordering, risk of low-priority starvation under sustained load |
| Default (30s) | Balanced |

Monitor `SchedulerStats.starvationPromotions` — if this grows steadily, your system is under sustained high-priority load.

### Environment Variables

For the HTTP server, scheduler configuration is available via environment variables:

```bash
CONSTELLATION_SCHEDULER_ENABLED=true
CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=16
CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT=30s
```

## Timeout Strategy

### Global DAG Timeout

Set a default timeout for all DAG executions:

```scala
ConstellationImpl.builder()
  .withDefaultTimeout(60.seconds)
  .build()
```

Any pipeline exceeding this duration is cancelled and modules receive `Timed` status.

### Per-Module Timeout

Individual modules can have their own timeouts via `ModuleConfig`:

```scala
ModuleBuilder
  .metadata("SlowModule", "May take a while", 1, 0)
  .config(ModuleConfig(timeout = Some(30.seconds)))
  .implementation[In, Out] { ... }
  .build
```

### Interaction Rules

| Global Timeout | Module Timeout | Effective |
|----------------|----------------|-----------|
| 60s | None | Module has 60s (inherits global) |
| 60s | 30s | Module has 30s (module wins) |
| None | 30s | Module has 30s |
| None | None | No timeout |

The global timeout applies to the entire DAG execution. If a single module's timeout fires first, that module fails but the DAG continues (other modules may still complete).

## Circuit Breaker Tuning

### Configuration

```scala
import io.constellation.execution.CircuitBreakerConfig

CircuitBreakerConfig(
  failureThreshold  = 5,          // Consecutive failures to trip
  resetDuration     = 30.seconds, // Time in Open before trying HalfOpen
  halfOpenMaxProbes = 1           // Probes allowed in HalfOpen
)
```

### State Machine

```
     success
  ┌───────────┐
  │           │
  ▼           │
Closed ──(threshold failures)──> Open ──(resetDuration)──> HalfOpen
  ▲                                                          │
  │                                                          │
  └──────────────────(probe success)─────────────────────────┘
                     (probe failure) ──> Open
```

### When to Adjust

| Symptom | Action |
|---------|--------|
| Circuit opens too easily | Increase `failureThreshold` (e.g., 5 → 10) |
| Slow recovery after outage | Decrease `resetDuration` (e.g., 30s → 10s) |
| Flapping (open/close/open) | Increase `halfOpenMaxProbes` to allow more test requests |
| External service has cold start | Increase `resetDuration` to give it time to warm up |

### Monitoring

```scala
val stats: IO[CircuitStats] = circuitBreaker.stats

// CircuitStats fields:
// state: CircuitState (Closed, Open, HalfOpen)
// consecutiveFailures: Int
// totalSuccesses: Long
// totalFailures: Long
// totalRejected: Long
```

## Cache Configuration

### Compilation Caching

`CachingLangCompiler` wraps any `LangCompiler` and caches compilation results:

```scala
import io.constellation.lang.CachingLangCompiler

val compiler = CachingLangCompiler.withDefaults(baseCompiler)
```

Default settings: LRU eviction with reasonable max entries and TTL.

### CacheBackend (SPI)

For runtime caching (module results, intermediate data), implement the `CacheBackend` trait:

```scala
trait CacheBackend {
  def get[A](key: String): IO[Option[CacheEntry[A]]]
  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit]
  def delete(key: String): IO[Boolean]
  def clear: IO[Unit]
  def stats: IO[CacheStats]
}
```

See [CacheBackend Integration Guide](../integrations/cache-backend.md) for Redis and Caffeine implementations.

### Monitoring Cache Performance

```bash
curl http://localhost:8080/metrics | jq .cache
```

Key metric: `hitRatio` — should be >0.8 for stable workloads with unchanged sources.

```scala
val stats: IO[CacheStats] = cache.stats
// CacheStats fields:
// hits: Long
// misses: Long
// evictions: Long
// size: Int
// maxSize: Option[Int]
// hitRatio: Double (0.0 to 1.0)
```

If `hitRatio` is low:
- Increase max cache size
- Increase TTL if data changes infrequently
- Check if cache keys are too specific (include timestamps, etc.)

## Object Pool Tuning

Constellation uses object pooling internally to reduce allocation overhead:

- **DeferredPool** — pools `Deferred[IO, _]` instances used for inter-module data flow
- **RuntimeStatePool** — pools `Runtime.State` objects

Pooling reduces allocation by approximately 90% in high-throughput scenarios. These pools are managed automatically and do not require tuning in most deployments.

## JVM Tuning

### Garbage Collector

| GC | Flag | Best For |
|----|------|----------|
| G1GC | `-XX:+UseG1GC` | General-purpose, balanced latency/throughput (default on JDK 17+) |
| ZGC | `-XX:+UseZGC` | Low-latency, sub-millisecond pauses |

**Recommendation:** Use G1GC (default) unless you need sub-millisecond pause times, in which case use ZGC.

### Heap Sizing

Base formula: `512MB + ~1MB per concurrent DAG execution`

| Concurrent DAGs | Recommended Heap |
|-----------------|-----------------|
| 10 | 512MB - 1GB |
| 100 | 1GB - 2GB |
| 1000 | 2GB - 4GB |

```bash
java -Xms512m -Xmx2g -jar constellation.jar
```

### Recommended JVM Flags

```bash
java \
  -Xms512m \
  -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/constellation/ \
  -jar constellation.jar
```

For ZGC (JDK 17+):

```bash
java \
  -Xms512m \
  -Xmx2g \
  -XX:+UseZGC \
  -XX:+HeapDumpOnOutOfMemoryError \
  -jar constellation.jar
```

## Monitoring

### MetricsProvider SPI

Instrument Constellation with your metrics system by implementing `MetricsProvider`:

```scala
trait MetricsProvider {
  def counter(name: String, tags: Map[String, String]): IO[Unit]
  def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit]
  def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit]
}
```

See [MetricsProvider Integration Guide](../integrations/metrics-provider.md) for Prometheus and Datadog examples.

### Built-in Instrumentation Points

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `execution.started` | counter | `dag_name` | DAG execution started |
| `execution.completed` | counter | `dag_name`, `success` | DAG execution completed |
| `execution.duration_ms` | histogram | `dag_name` | End-to-end execution time |
| `module.duration_ms` | histogram | `module_name` | Per-module execution time |
| `module.failed` | counter | `module_name` | Module execution failures |
| `scheduler.active` | gauge | — | Currently running tasks |
| `scheduler.queued` | gauge | — | Tasks waiting in queue |

### Health Endpoints

| Endpoint | Purpose | Access |
|----------|---------|--------|
| `GET /health/live` | Liveness probe — is the process alive? | Public |
| `GET /health/ready` | Readiness probe — can it serve traffic? | Public |
| `GET /health/detail` | Component diagnostics | Opt-in, auth-gated |

`/health/detail` returns scheduler stats, lifecycle state, custom check results, and compiler status.

### SchedulerStats

```scala
val stats: IO[SchedulerStats] = scheduler.stats

// SchedulerStats fields:
// activeCount: Int         — currently executing tasks
// queuedCount: Int         — tasks waiting in queue
// totalSubmitted: Long     — lifetime submissions
// totalCompleted: Long     — lifetime completions
// highPriorityCompleted: Long
// lowPriorityCompleted: Long
// starvationPromotions: Long — low-priority tasks promoted due to starvation timeout
```

## Diagnostic Checklist

### Pipelines are slow

1. Check per-module execution times via `MetricsProvider` histograms
2. Identify the bottleneck module — is it CPU-bound or IO-bound?
3. For IO-bound: increase `maxConcurrency`, optimize the external call
4. For CPU-bound: verify `maxConcurrency` is not too high (context switching)
5. Check if compilation caching is active (`hitRatio` > 0.8)

### Queue is growing

1. Monitor `SchedulerStats.queuedCount` over time
2. If steadily increasing: your submission rate exceeds processing capacity
3. Increase `maxConcurrency` (if resources allow)
4. Implement upstream backpressure (reject or queue at the API layer)
5. Check for a single slow module blocking the pipeline

### High rejection rate

1. Check `CircuitStats.totalRejected` for circuit breaker rejections
2. If circuit is Open: the downstream module is failing — investigate root cause
3. Check for `QueueFullException` — the scheduler queue is saturated
4. Increase `maxQueueSize` for burst absorption, or increase `maxConcurrency`

### Cache misses

1. Monitor `CacheStats.hitRatio`
2. If low: check if scripts change frequently (cache is invalidated on source change)
3. Increase max cache size if evictions are high
4. Increase TTL if compiled results are stable

### Memory growing

1. Check execution storage: `ExecutionStorage` retains execution history in memory by default
2. Reduce `maxExecutions` in storage config (default: 1000)
3. Reduce `maxValueSizeBytes` to truncate large output values
4. Check for leaked `CancellableExecution` handles — ensure they are deregistered
5. Verify GC is functioning: enable GC logging with `-Xlog:gc`

## Performance Targets

Reference numbers from the benchmark suite (see `docs/dev/performance-benchmarks.md`):

| Operation | Target | Notes |
|-----------|--------|-------|
| Parse (small program) | <5ms | 3-5 line scripts |
| Full pipeline (medium) | <100ms | Parse + type-check + compile + execute |
| Cache hit | <5ms | Cached compilation result lookup |
| Cache speedup | >5x | Cached vs uncached compilation |
| Autocomplete response | <50ms | LSP completion request |
| Orchestration overhead per node | ~0.15ms | Runtime scheduling overhead |
| p99 sustained throughput | <0.5ms/node | Under continuous load |
| Object pool allocation reduction | ~90% | Pooled vs unpooled Deferred allocations |
