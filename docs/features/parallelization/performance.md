# Performance Tuning

> **Path**: `docs/features/parallelization/performance.md`
> **Parent**: [parallelization/](./README.md)

How to tune parallelization behavior for optimal performance.

## Quick Checklist

| Goal | Action |
|------|--------|
| Maximize throughput | Use unbounded scheduler (default) |
| Protect downstream resources | Enable bounded scheduler with appropriate `maxConcurrency` |
| Prioritize latency-sensitive paths | Add `priority: high` to critical modules |
| Reduce memory pressure | Lower `maxConcurrency` |
| Debug execution order | Enable single-threaded mode |

## When to Use Each Scheduler Mode

### Unbounded (Default)

```bash
# No configuration needed - this is the default
```

**Best for:**
- Development and testing
- Workloads with ample resources
- Pipelines where all modules are equally important
- Maximum throughput when resources aren't constrained

**Characteristics:**
- Zero scheduling overhead
- No priority ordering
- All independent modules run simultaneously

### Bounded with Priorities

```bash
CONSTELLATION_SCHEDULER_ENABLED=true
CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=16
```

**Best for:**
- Production workloads with resource constraints
- Mixed-priority pipelines (user-facing + background)
- Protecting downstream services from overload

**Characteristics:**
- Small scheduling overhead (~0.1ms per task)
- Priority-based execution order
- Bounded memory usage from limited parallelism

## Tuning `maxConcurrency`

### Starting Point

```
maxConcurrency = min(
  available_cpu_cores * 2,
  downstream_connection_pool_size,
  target_concurrent_requests
)
```

### Examples

| Scenario | Recommendation |
|----------|----------------|
| CPU-bound modules | `maxConcurrency = CPU cores` |
| I/O-bound modules (HTTP, DB) | `maxConcurrency = CPU cores * 2-4` |
| Rate-limited external API | `maxConcurrency = API rate limit / avg_request_duration` |
| Database connection pool (size 20) | `maxConcurrency <= 20` |

### Symptoms of Wrong Settings

| Symptom | Cause | Fix |
|---------|-------|-----|
| High latency, low CPU | `maxConcurrency` too low | Increase |
| OOM errors | `maxConcurrency` too high | Decrease |
| Database connection errors | Exceeds pool size | Match to pool |
| Rate limit errors from APIs | Exceeds API limits | Decrease |

## Using Priority Effectively

### Priority Guidelines

| Priority | When to Use |
|----------|-------------|
| `critical` (100) | Payment processing, authentication |
| `high` (80) | User-facing API responses |
| `normal` (50) | Standard processing (default) |
| `low` (20) | Background enrichment |
| `background` (0) | Analytics, logging, cleanup |

### Example: Mixed-Priority Pipeline

```constellation
in orderId: String

# Critical path - payment must complete first
payment = ProcessPayment(orderId) with priority: critical

# High priority - user sees this
orderDetails = GetOrderDetails(orderId) with priority: high

# Background - user doesn't wait for this
analytics = TrackOrderView(orderId) with priority: background
auditLog = LogOrderAccess(orderId) with priority: background

out orderDetails
```

### Anti-Patterns

**Don't:** Make everything high priority

```constellation
# BAD: Everything is high priority = nothing is prioritized
a = ModuleA(x) with priority: high
b = ModuleB(x) with priority: high
c = ModuleC(x) with priority: high
```

**Don't:** Use priority for sequencing

```constellation
# BAD: Using priority to force order
first = ModuleA(x) with priority: 100
second = ModuleB(x) with priority: 50  # Might still run first!
```

**Do:** Use data dependencies for order

```constellation
# GOOD: Data dependency ensures order
first = ModuleA(x)
second = ModuleB(first)  # Guaranteed to run after ModuleA
```

## Starvation Prevention Tuning

### Default Settings

```bash
CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT=30s
# Low-priority tasks get +10 priority every 5 seconds
# After 30s, priority 0 reaches priority 60
```

### Tuning for Different Workloads

| Workload | Recommended Timeout |
|----------|---------------------|
| Interactive (low latency) | 15-20s |
| Batch processing | 60-120s |
| Mixed workloads | 30s (default) |

**Shorter timeout:**
- Low-priority tasks complete faster
- Less priority differentiation
- Use when low-priority work is still time-sensitive

**Longer timeout:**
- More priority differentiation
- Low-priority tasks may wait longer
- Use when high-priority work is critical

## Monitoring Performance

### Key Metrics

```bash
curl http://localhost:8080/metrics | jq .scheduler
```

```json
{
  "enabled": true,
  "activeCount": 4,
  "queuedCount": 12,
  "totalSubmitted": 1523,
  "totalCompleted": 1507,
  "starvationPromotions": 23
}
```

### Health Indicators

| Metric | Healthy | Action if Unhealthy |
|--------|---------|---------------------|
| `queuedCount` | Low, stable | Increase `maxConcurrency` |
| `queuedCount / totalSubmitted` | < 5% | Check for slow modules |
| `starvationPromotions` | Low | Decrease timeout or increase concurrency |
| `activeCount` | Near but not always at max | Optimal |

### Per-Module Latency

The dashboard shows execution time per module:

1. Open dashboard (`http://localhost:8080/dashboard`)
2. Run a pipeline
3. View execution history for per-module timing
4. Identify slow modules for optimization

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `runtime` | Unbounded execution via `parTraverse` | `modules/runtime/src/main/scala/io/constellation/Runtime.scala:178-184` |
| `runtime` | Bounded scheduler | `modules/runtime/src/main/scala/io/constellation/execution/GlobalScheduler.scala` |
| `runtime` | Priority submission | `modules/runtime/src/main/scala/io/constellation/Runtime.scala:180-181` |
| `http-api` | Scheduler metrics endpoint | `modules/http-api/src/main/scala/io/constellation/http/ConstellationRoutes.scala` |

## Advanced: Object Pooling

For high-throughput workloads, the runtime supports object pooling to reduce allocation overhead:

```scala
import io.constellation.pool.RuntimePool

RuntimePool.create(poolSize = 100).use { pool =>
  Runtime.runPooled(dag, inputs, modules, pool)
}
```

**Benefits:**
- 90% reduction in per-request allocations
- More stable p99 latency (fewer GC pauses)
- 15-30% throughput improvement for small DAGs

**When to use:**
- High request rates (>1000 req/s)
- Latency-sensitive workloads
- Observed GC pressure from DAG execution

## Debugging Performance Issues

### Slow Pipeline Execution

1. **Check layer structure.** Deep chains serialize execution.
   - Use dashboard DAG view to visualize
   - Restructure to maximize parallel layers

2. **Check individual module latency.**
   - Use execution history in dashboard
   - Profile slow modules independently

3. **Check scheduler queuing.**
   - High `queuedCount` = not enough slots
   - Increase `maxConcurrency` or reduce task rate

### High Memory Usage

1. **Check concurrent module count.**
   - Lower `maxConcurrency` to reduce parallel allocations

2. **Check for large intermediate values.**
   - Profile memory per-module
   - Consider streaming for large data

3. **Enable pooling.**
   - Use `RuntimePool` for reduced allocation pressure

### Inconsistent Latency

1. **Check for GC pauses.**
   - Monitor GC metrics
   - Consider pooling

2. **Check for priority inversions.**
   - Review priority assignments
   - Ensure critical paths have appropriate priority

3. **Check for starvation.**
   - Monitor `starvationPromotions`
   - Tune starvation timeout

## Best Practices Summary

1. **Start simple.** Use unbounded scheduler until you have a reason not to.

2. **Measure first.** Profile before optimizing. Use dashboard and metrics.

3. **Right-size concurrency.** Match `maxConcurrency` to downstream capacity.

4. **Use priority sparingly.** Most modules should be `normal`. Reserve high/critical for truly important paths.

5. **Structure for parallelism.** Design DAGs with wide layers of independent modules.

6. **Monitor in production.** Watch scheduler metrics for queuing and starvation.

## Related

- [layer-execution.md](./layer-execution.md) - Understanding layer-based execution
- [scheduling.md](./scheduling.md) - Scheduler configuration details
- [PHILOSOPHY.md](./PHILOSOPHY.md) - Why automatic parallelization
