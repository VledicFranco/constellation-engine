---
title: "Clustering"
sidebar_position: 6
description: "Running Constellation Engine in a distributed cluster"
---

# Clustering Guide

This guide covers running Constellation Engine as a distributed cluster with shared state, coordinated execution, and cache synchronization.

## Architecture Overview

Constellation Engine instances are stateless at the HTTP layer but maintain in-process state for performance:

```
                    ┌─────────────────┐
                    │  Load Balancer  │
                    └────────┬────────┘
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
         ┌────────┐    ┌────────┐    ┌────────┐
         │ Node 1 │    │ Node 2 │    │ Node 3 │
         │ Cache  │    │ Cache  │    │ Cache  │
         │ Queue  │    │ Queue  │    │ Queue  │
         └────┬───┘    └────┬───┘    └────┬───┘
              │              │              │
              └──────────────┼──────────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
              ▼              ▼              ▼
         ┌────────┐    ┌────────┐    ┌────────┐
         │ Redis  │    │Memcached│   │Postgres│
         │(cache) │    │ (cache) │   │(storage)│
         └────────┘    └────────┘    └────────┘
```

For most deployments, you can run multiple independent instances behind a load balancer without any shared state. Add shared backends when you need:

- **Cross-instance cache hits** — Avoid redundant compilation across nodes
- **Persistent execution history** — Query execution logs from any node
- **Coordinated circuit breakers** — Synchronized failure detection

## Cluster Configuration

### Basic Multi-Node Setup

The simplest cluster is multiple independent instances:

```yaml
# docker-compose.yml
services:
  constellation-1:
    build: .
    environment:
      CONSTELLATION_PORT: "8080"
      CONSTELLATION_SCHEDULER_ENABLED: "true"
      CONSTELLATION_SCHEDULER_MAX_CONCURRENCY: "16"

  constellation-2:
    build: .
    environment:
      CONSTELLATION_PORT: "8080"
      CONSTELLATION_SCHEDULER_ENABLED: "true"
      CONSTELLATION_SCHEDULER_MAX_CONCURRENCY: "16"

  constellation-3:
    build: .
    environment:
      CONSTELLATION_PORT: "8080"
      CONSTELLATION_SCHEDULER_ENABLED: "true"
      CONSTELLATION_SCHEDULER_MAX_CONCURRENCY: "16"
```

Each instance has independent:
- Compilation cache
- Execution history
- Scheduler queue
- Circuit breaker state

This is sufficient for stateless HTTP workloads.

### Shared Cache Cluster

For shared compilation and execution caching, use a distributed cache backend.

#### With Memcached

```scala
import io.constellation.cache.memcached.{MemcachedCacheBackend, MemcachedConfig}

// Connect to Memcached cluster
val cacheConfig = MemcachedConfig.cluster(
  servers = "memcached-1:11211,memcached-2:11211,memcached-3:11211"
)

MemcachedCacheBackend.resource(cacheConfig).use { cache =>
  // Use for compilation cache
  val compiler = LangCompilerBuilder()
    .withCacheBackend(cache)
    .build()

  // Use for runtime cache
  val constellation = ConstellationImpl.builder()
    .withBackends(ConstellationBackends(cache = Some(cache)))
    .build()

  // All instances share the same cache
}
```

#### With Redis

```scala
import io.constellation.cache.{CacheBackend, CacheSerde, DistributedCacheBackend}
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.Stdout._

// Redis cluster connection
Redis[IO].cluster(
  "redis://redis-1:6379,redis://redis-2:6379,redis://redis-3:6379"
).use { redis =>
  val cache = new RedisCacheBackend(redis, CacheSerde.anySerde)

  val compiler = LangCompilerBuilder()
    .withCacheBackend(cache)
    .build()

  // ...
}
```

### Kubernetes Cluster Configuration

```yaml
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: constellation-config
  namespace: constellation
data:
  CONSTELLATION_PORT: "8080"
  CONSTELLATION_SCHEDULER_ENABLED: "true"
  CONSTELLATION_SCHEDULER_MAX_CONCURRENCY: "16"
  # Shared cache configuration
  MEMCACHED_SERVERS: "memcached-0.memcached:11211,memcached-1.memcached:11211"
  # Shared storage configuration
  POSTGRES_URL: "jdbc:postgresql://postgres:5432/constellation"
```

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: constellation-engine
  namespace: constellation
spec:
  replicas: 3
  selector:
    matchLabels:
      app.kubernetes.io/name: constellation-engine
  template:
    metadata:
      labels:
        app.kubernetes.io/name: constellation-engine
    spec:
      containers:
        - name: constellation
          image: constellation-engine:latest
          envFrom:
            - configMapRef:
                name: constellation-config
          # ... probes, resources, etc.
```

## Distributed Execution

### Per-Instance Scheduling

By default, each Constellation instance runs its own scheduler. Tasks submitted to an instance are queued and executed locally:

```
Node 1                    Node 2
┌─────────────────┐      ┌─────────────────┐
│ Scheduler       │      │ Scheduler       │
│ ┌─────────────┐ │      │ ┌─────────────┐ │
│ │ Queue: 5    │ │      │ │ Queue: 3    │ │
│ │ Active: 4   │ │      │ │ Active: 4   │ │
│ └─────────────┘ │      │ └─────────────┘ │
└─────────────────┘      └─────────────────┘
```

This model works well for:
- Stateless HTTP APIs
- Independent request processing
- Simple horizontal scaling

### Work Stealing (Future)

For advanced workloads, a distributed work-stealing scheduler can balance load across nodes. This is not yet implemented but the architecture supports it:

```scala
// Future API (not yet available)
GlobalScheduler.distributed(
  nodes = List("node-1:8080", "node-2:8080", "node-3:8080"),
  maxConcurrencyPerNode = 16,
  workStealingEnabled = true
)
```

### Execution Routing

For pipelines that must execute on a specific node (e.g., GPU access, local files), use execution routing:

```scala
// Route by node ID
val nodeId = sys.env.getOrElse("NODE_ID", "default")

ConstellationImpl.builder()
  .withNodeId(nodeId)
  .build()
```

Clients can then target specific nodes via header:

```bash
curl -X POST http://constellation.example.com/execute \
  -H "X-Constellation-Node: gpu-node-1" \
  -H "Content-Type: application/json" \
  -d '{"source": "...", "inputs": {}}'
```

The load balancer routes based on this header (requires custom configuration).

## State Synchronization

### Cache Synchronization

When using a distributed cache backend (Memcached, Redis), cache synchronization is automatic:

1. Node 1 compiles a program, stores result in shared cache
2. Node 2 receives same program, finds it in shared cache
3. No recompilation needed

#### Cache Key Strategy

Cache keys are based on content hashes:

```
compilation:<sha256(source)>
execution:<sha256(dagSpec + inputs)>
module:<moduleId>:<sha256(input)>
```

This ensures:
- Same source compiles to same cache key on any node
- Same inputs produce same execution cache key
- No node-specific prefixes needed

#### Cache Invalidation

For manual cache invalidation across nodes:

```scala
// Clear specific key (propagates to all nodes via shared backend)
cache.delete("compilation:abc123").unsafeRunSync()

// Clear all keys (use sparingly)
cache.clear.unsafeRunSync()
```

For TTL-based expiration (recommended):

```scala
// Compilation cache: 1 hour TTL
cache.set(key, compiledProgram, ttl = 1.hour)

// Module execution cache: 5 minutes TTL
cache.set(key, result, ttl = 5.minutes)
```

### Execution History Synchronization

For shared execution history across nodes, implement `ExecutionStorage` backed by a database:

```scala
import io.constellation.spi.ExecutionStorage
import doobie._
import doobie.implicits._
import cats.effect.IO

class PostgresExecutionStorage(xa: Transactor[IO]) extends ExecutionStorage {

  def store(trace: ExecutionTrace): IO[Unit] =
    sql"""
      INSERT INTO execution_traces (id, dag_name, started_at, completed_at, status, trace_json)
      VALUES (${trace.id}, ${trace.dagName}, ${trace.startedAt}, ${trace.completedAt}, ${trace.status}, ${trace.toJson})
    """.update.run.transact(xa).void

  def get(id: String): IO[Option[ExecutionTrace]] =
    sql"SELECT trace_json FROM execution_traces WHERE id = $id"
      .query[String]
      .option
      .transact(xa)
      .map(_.map(ExecutionTrace.fromJson))

  def list(limit: Int, offset: Int): IO[List[ExecutionTrace]] =
    sql"SELECT trace_json FROM execution_traces ORDER BY started_at DESC LIMIT $limit OFFSET $offset"
      .query[String]
      .to[List]
      .transact(xa)
      .map(_.map(ExecutionTrace.fromJson))

  // ... other methods
}
```

Wire it into the builder:

```scala
val storage = new PostgresExecutionStorage(xa)

ConstellationImpl.builder()
  .withBackends(ConstellationBackends(storage = Some(storage)))
  .build()
```

### Circuit Breaker Synchronization

By default, circuit breakers are per-instance. For coordinated failure detection, implement a distributed circuit breaker:

```scala
import io.constellation.execution.{CircuitBreaker, CircuitState}

class RedisCircuitBreaker(
  name: String,
  redis: RedisCommands[IO, String, String],
  config: CircuitBreakerConfig
) extends CircuitBreaker {

  private val stateKey = s"circuit:$name:state"
  private val failuresKey = s"circuit:$name:failures"

  def getState: IO[CircuitState] =
    redis.get(stateKey).map {
      case Some("open") => CircuitState.Open
      case Some("half-open") => CircuitState.HalfOpen
      case _ => CircuitState.Closed
    }

  def recordFailure: IO[Unit] =
    for {
      failures <- redis.incr(failuresKey)
      _ <- if (failures >= config.failureThreshold)
             redis.set(stateKey, "open") *>
             redis.expire(stateKey, config.resetDuration)
           else IO.unit
    } yield ()

  def recordSuccess: IO[Unit] =
    redis.set(stateKey, "closed") *>
    redis.del(failuresKey).void

  // ... wrap execution logic
}
```

This ensures all nodes see the same circuit state:
- Node 1 records 3 failures
- Node 2 records 2 failures
- Circuit opens across all nodes (threshold: 5)

## Cache Sharing Between Instances

### Compilation Cache Sharing

Sharing the compilation cache is the highest-value optimization for clusters:

| Scenario | Without Sharing | With Sharing |
|----------|-----------------|--------------|
| Deploy 3 nodes | Each compiles everything | First node compiles, others hit cache |
| Rolling update | New pods recompile | New pods hit existing cache |
| Scale out | New pods cold start | New pods warm from cache |

#### Cache Warming

Pre-warm the cache at startup:

```scala
// In your application startup
def warmCache(compiler: LangCompiler, programs: List[String]): IO[Unit] =
  programs.traverse_ { source =>
    compiler.compile(source).attempt.void  // Ignore errors, just populate cache
  }

// Load frequently-used programs
val commonPrograms = loadProgramsFromDirectory("/app/common-programs")
warmCache(compiler, commonPrograms).unsafeRunSync()
```

#### Cache Metrics

Monitor cross-instance cache effectiveness:

```bash
# Check hit rate on each node
for node in node-1 node-2 node-3; do
  echo "$node:"
  curl -s http://$node:8080/metrics | jq '.cache.hitRate'
done
```

Healthy cluster: All nodes show similar hit rates (>0.8 for stable workloads).

### Module Result Caching

For expensive module computations, enable result caching:

```constellation
# constellation-lang
expensive_result = ExpensiveComputation(input) with cache: 1h, cache_backend: "redis"
```

This caches the module output in the shared backend. Subsequent executions (on any node) skip the computation.

### Cache Consistency

Distributed caches use eventual consistency. Consider:

| Operation | Consistency | Notes |
|-----------|-------------|-------|
| Write | Immediate to backend | Other nodes see update on next read |
| Read | Local view | May be stale if TTL not expired |
| Delete | Propagates via backend | Other nodes see delete on next access |

For compilation caching, eventual consistency is acceptable:
- Worst case: Two nodes compile the same program simultaneously
- Both write to cache; one wins (idempotent)
- Future reads hit cache

For execution caching where freshness matters:
- Use short TTLs (seconds to minutes)
- Or implement cache-aside pattern with explicit invalidation

## Cluster Monitoring

### Aggregated Metrics

Collect metrics from all nodes for cluster-wide visibility:

```yaml
# Prometheus scrape config
scrape_configs:
  - job_name: 'constellation'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names: ['constellation']
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app_kubernetes_io_name]
        regex: constellation-engine
        action: keep
```

### Key Cluster Metrics

| Metric | Aggregation | Alert Threshold |
|--------|-------------|-----------------|
| `cache.hitRate` | Average across nodes | < 0.5 |
| `scheduler.queuedCount` | Sum across nodes | > maxConcurrency * nodeCount |
| `execution.duration_ms` | P99 across nodes | > 30s |
| `http.5xx` | Sum across nodes | > 1% of requests |

### Health Dashboard

Query cluster health:

```bash
# All nodes healthy?
for pod in $(kubectl get pods -n constellation -l app.kubernetes.io/name=constellation-engine -o name); do
  echo "$pod: $(kubectl exec -n constellation $pod -- curl -s localhost:8080/health/ready | jq -r '.ready')"
done
```

## Troubleshooting

### Cache Misses Across Nodes

**Symptom:** Low hit rate despite identical workloads.

**Causes:**
1. Cache key mismatch (different source formatting)
2. TTL too short
3. Cache eviction (size limit reached)
4. Network partition from cache backend

**Diagnosis:**
```bash
# Compare cache keys generated on different nodes
curl http://node-1:8080/debug/cache-keys
curl http://node-2:8080/debug/cache-keys
```

**Solutions:**
- Normalize source code before hashing (trim whitespace, etc.)
- Increase TTL for stable sources
- Increase cache size limit
- Check network connectivity to Memcached/Redis

### Split-Brain Circuit Breaker

**Symptom:** Some nodes have circuit open, others closed.

**Cause:** Circuit breaker state is per-instance by default.

**Solution:** Implement distributed circuit breaker (see above) or accept per-instance behavior.

### Uneven Load Distribution

**Symptom:** Some nodes have high queue depth, others are idle.

**Causes:**
1. Session affinity routing traffic to subset of nodes
2. Load balancer not using all nodes
3. Slow nodes processing requests longer

**Diagnosis:**
```bash
# Compare scheduler stats across nodes
for node in node-1 node-2 node-3; do
  echo "$node:"
  curl -s http://$node:8080/metrics | jq '.scheduler'
done
```

**Solutions:**
- Review load balancer configuration
- Check for long-running pipelines on specific nodes
- Adjust session affinity settings