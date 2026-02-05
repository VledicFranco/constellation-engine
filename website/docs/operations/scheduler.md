---
title: "Global Scheduler"
sidebar_position: 3
description: "Priority-based task scheduling with bounded concurrency and starvation prevention"
---

# Global Scheduler

The Global Priority Scheduler provides bounded concurrency with priority ordering across all concurrent DAG executions. High-priority tasks from any execution run before low-priority tasks when the system is under load.

## Overview

By default, Constellation uses an **unbounded scheduler** that executes all tasks immediately without any concurrency limiting. This preserves the behavior where all parallel modules in a DAG layer run simultaneously.

When enabled, the **bounded scheduler** limits the number of concurrent tasks and uses a priority queue to determine execution order. This is useful for:

- **Resource management**: Prevent overwhelming downstream services or exhausting connection pools
- **Priority-based execution**: Ensure critical tasks complete before less important ones
- **Fair scheduling**: Starvation prevention ensures all tasks eventually run, even under heavy load

## Configuration

The scheduler is configured via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_SCHEDULER_ENABLED` | `false` | Enable bounded priority scheduler |
| `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY` | `16` | Maximum concurrent tasks |
| `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` | `30s` | Time before low-priority tasks get priority boost |

### Examples

```bash
# Enable bounded scheduler with default settings
CONSTELLATION_SCHEDULER_ENABLED=true sbt "exampleApp/run"

# Enable with custom concurrency limit
CONSTELLATION_SCHEDULER_ENABLED=true \
CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=8 \
sbt "exampleApp/run"

# Enable with longer starvation timeout
CONSTELLATION_SCHEDULER_ENABLED=true \
CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT=60s \
sbt "exampleApp/run"
```

### Docker / Kubernetes

```yaml
# docker-compose.yml
services:
  constellation:
    environment:
      CONSTELLATION_SCHEDULER_ENABLED: "true"
      CONSTELLATION_SCHEDULER_MAX_CONCURRENCY: "8"
      CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT: "30s"
```

```yaml
# Kubernetes Deployment
spec:
  containers:
    - name: constellation
      env:
        - name: CONSTELLATION_SCHEDULER_ENABLED
          value: "true"
        - name: CONSTELLATION_SCHEDULER_MAX_CONCURRENCY
          value: "8"
```

## Priority Levels

Priority values range from 0 to 100. Higher values indicate higher priority. The following conventions are recommended:

| Level | Value | Use Case |
|-------|-------|----------|
| Critical | 100 | Time-sensitive operations, SLA-bound requests |
| High | 80 | Important user-facing tasks |
| Normal | 50 | Default priority (implicit) |
| Low | 20 | Background processing, batch jobs |
| Background | 0 | Non-urgent work, cleanup tasks |

### Priority Semantics

- **Values are clamped**: Priorities below 0 are treated as 0; priorities above 100 are treated as 100
- **FIFO within same priority**: Tasks with equal priority execute in submission order
- **Cross-execution ordering**: Priority applies globally across all concurrent DAG executions

### Priority Classification for Statistics

The scheduler tracks completion statistics by priority tier:

| Tier | Priority Range | Statistic |
|------|----------------|-----------|
| High | >= 75 | `highPriorityCompleted` |
| Normal | 25-74 | (not separately tracked) |
| Low | < 25 | `lowPriorityCompleted` |

## Starvation Prevention

Low-priority tasks are not starved indefinitely. The scheduler uses an **aging mechanism** that gradually increases the effective priority of waiting tasks.

### Algorithm

1. A background fiber runs every **5 seconds**
2. For each waiting task, it calculates `waitTime = now - submittedAt`
3. Effective priority increases by **+10 per 5 seconds** of waiting
4. Effective priority is capped at **100** (maximum)
5. The priority queue is re-sorted based on new effective priorities

### Aging Timeline Example

A task submitted with priority 0 (Background):

| Wait Time | Effective Priority | Notes |
|-----------|-------------------|-------|
| 0s | 0 | Initial submission |
| 5s | 10 | First aging boost |
| 10s | 20 | Now equals Low priority |
| 15s | 30 | |
| 20s | 40 | |
| 25s | 50 | Now equals Normal priority |
| 30s | 60 | Now higher than Normal |
| 50s | 100 | Maximum (capped) |

After approximately 25 seconds, even the lowest-priority task will have the same effective priority as newly submitted Normal-priority tasks.

### Starvation Timeout Configuration

The `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` controls the aging rate. A shorter timeout means faster priority boosting:

- **30s (default)**: Background tasks reach Normal priority in ~25 seconds
- **60s**: Slower boosting, suitable when high-priority work genuinely should take precedence longer
- **10s**: Aggressive boosting, useful when fairness is more important than priority

## Queue Behavior Under Load

### Bounded Concurrency

When the scheduler is enabled:

1. At most `maxConcurrency` tasks execute simultaneously
2. Additional tasks wait in a priority queue
3. When a task completes, the highest-priority waiting task starts

### Queue Capacity

By default, the queue is unbounded. In programmatic usage, you can set a maximum queue size:

```scala
GlobalScheduler.bounded(
  maxConcurrency = 16,
  maxQueueSize = 1000,  // Reject new tasks when queue exceeds 1000
  starvationTimeout = 30.seconds
)
```

When `maxQueueSize > 0` and the queue is full, new submissions fail with a `QueueFullException`. This provides backpressure to prevent unbounded memory growth.

### Submission Flow

```
1. Client submits task with priority
2. Scheduler checks:
   - Is it shutting down? -> IllegalStateException
   - Is queue full? -> QueueFullException
3. Task added to priority queue, submission count incremented
4. Dispatch loop tries to start highest-priority queued task
5. If concurrency slots available, task gate opens
6. Task acquires semaphore permit and executes
7. On completion (success or failure):
   - Permit released
   - Completion stats updated
   - Dispatch loop runs again for next task
```

## Performance Characteristics

### Time Complexity

| Operation | Complexity | Notes |
|-----------|------------|-------|
| Submit | O(log n) | TreeSet insertion |
| Dispatch | O(log n) | TreeSet removal of head |
| Aging pass | O(n) | Rebuilds TreeSet with new priorities |

Where `n` is the number of queued tasks.

### Memory Usage

Each queued task holds:
- A `QueueEntry` (~100 bytes including Deferred gate)
- Reference to the task's `IO[A]` closure

For typical workloads (queues < 10,000), memory overhead is negligible.

### Overhead

When the scheduler is enabled but not under contention:
- Semaphore acquisition adds ~1 microsecond per task
- Deferred gate synchronization adds ~1 microsecond
- Total overhead: typically < 10 microseconds per task

Under heavy load with full queue, the overhead is dominated by queue operations (log n).

## When to Enable the Scheduler

### Enable when

- You need to limit concurrent calls to external APIs with rate limits
- You want critical requests to preempt background processing
- You need to bound resource usage (connections, memory, threads)
- Multiple concurrent DAG executions compete for shared resources

### Keep disabled when

- Running a single DAG execution at a time
- All tasks have equal priority
- Maximum parallelism is desired without bounds
- Development/testing environments

## Monitoring

### Scheduler Statistics

The scheduler exposes runtime statistics via the `stats` method:

```scala
scheduler.stats.flatMap { stats =>
  IO.println(s"""
    Active: ${stats.activeCount}
    Queued: ${stats.queuedCount}
    Submitted: ${stats.totalSubmitted}
    Completed: ${stats.totalCompleted}
    High-priority completed: ${stats.highPriorityCompleted}
    Low-priority completed: ${stats.lowPriorityCompleted}
    Starvation promotions: ${stats.starvationPromotions}
  """)
}
```

| Metric | Description |
|--------|-------------|
| `activeCount` | Tasks currently executing (0 to maxConcurrency) |
| `queuedCount` | Tasks waiting in priority queue |
| `totalSubmitted` | Cumulative tasks submitted since startup |
| `totalCompleted` | Cumulative tasks completed since startup |
| `highPriorityCompleted` | Tasks with priority >= 75 that completed |
| `lowPriorityCompleted` | Tasks with priority < 25 that completed |
| `starvationPromotions` | Number of times aging boosted a task's priority |

### Metrics Endpoint

When enabled, scheduler metrics are available via the `/metrics` endpoint:

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
  "highPriorityCompleted": 312,
  "lowPriorityCompleted": 89,
  "starvationPromotions": 23
}
```

### Health Indicators

- **Healthy**: `queuedCount` fluctuates but stays bounded; `activeCount` <= `maxConcurrency`
- **Warning**: `queuedCount` growing steadily; `starvationPromotions` increasing rapidly
- **Unhealthy**: `queuedCount` unbounded growth; tasks timing out

## Programmatic Usage

### Creating a Scheduler

```scala
import io.constellation.execution.GlobalScheduler
import scala.concurrent.duration._

// Unbounded (default behavior, no limits)
val unbounded: GlobalScheduler = GlobalScheduler.unbounded

// Bounded with Resource lifecycle (recommended)
val boundedResource = GlobalScheduler.bounded(
  maxConcurrency = 16,
  maxQueueSize = 0,  // 0 = unlimited queue
  starvationTimeout = 30.seconds
)

// Use within Resource for proper cleanup
boundedResource.use { scheduler =>
  for {
    result1 <- scheduler.submit(80, highPriorityTask)
    result2 <- scheduler.submit(20, lowPriorityTask)
  } yield (result1, result2)
}
```

### Direct Task Submission

```scala
// Submit with explicit priority (0-100)
scheduler.submit(80, IO.println("High priority"))
scheduler.submit(20, IO.println("Low priority"))

// Submit with default normal priority (50)
scheduler.submitNormal(IO.println("Normal priority"))

// Get current statistics
scheduler.stats.flatMap { s =>
  IO.println(s"Active: ${s.activeCount}, Queued: ${s.queuedCount}")
}
```

### Shutdown Behavior

The bounded scheduler implements graceful shutdown:

1. Sets `shuttingDown = true` - new submissions are rejected
2. Cancels the aging background fiber
3. Drains the pending queue by completing all gates
4. Allows in-flight tasks to complete

```scala
// Automatic shutdown via Resource
boundedResource.use { scheduler =>
  // ... use scheduler
} // Scheduler shutdown automatically here
```

## Best Practices

1. **Start with defaults**: The default `maxConcurrency=16` and `starvationTimeout=30s` work well for most workloads.

2. **Match concurrency to bottlenecks**: Set `maxConcurrency` based on your slowest downstream dependency (e.g., database connection pool size, external API rate limit).

3. **Use priority sparingly**: Most tasks should use Normal priority. Reserve High/Critical for truly time-sensitive operations.

4. **Monitor starvation promotions**: High `starvationPromotions` indicates low-priority work is being delayed. Consider increasing concurrency or reducing high-priority load.

5. **Enable queue limits in production**: Set `maxQueueSize` to prevent unbounded memory growth under sustained overload.

6. **Use Resource lifecycle**: Always use `GlobalScheduler.bounded` (which returns a `Resource`) rather than `boundedUnsafe` to ensure proper cleanup.

## Troubleshooting

### Tasks Appear Stuck

**Symptoms**: `queuedCount` is high, `activeCount` is 0, tasks are not completing.

**Possible causes**:
- Scheduler was shutdown (check for "shutting down" errors)
- All active tasks are blocked on external resources

**Solutions**:
- Check scheduler stats for shutdown state
- Verify external dependencies are responsive
- Restart the application if scheduler is in inconsistent state

### Low-Priority Tasks Never Run

**Symptoms**: `lowPriorityCompleted` stays at 0, `starvationPromotions` keeps increasing.

**Possible causes**:
- Constant stream of high-priority tasks
- `maxConcurrency` too low for the high-priority load

**Solutions**:
- Increase `maxConcurrency` to allow more parallel execution
- Reduce `starvationTimeout` to boost low-priority tasks faster
- Review if all "high priority" tasks genuinely need that priority

### Queue Growing Unboundedly

**Symptoms**: `queuedCount` keeps increasing, memory usage growing.

**Possible causes**:
- Tasks submitted faster than they complete
- Tasks taking longer than expected
- Downstream services are slow or failing

**Solutions**:
- Enable queue size limits (`maxQueueSize`)
- Add circuit breakers to failing downstream calls
- Increase `maxConcurrency` if resources allow
- Add backpressure at the submission layer

### High Starvation Promotion Count

**Symptoms**: `starvationPromotions` is very high relative to `totalCompleted`.

**Possible causes**:
- System is overloaded with high-priority work
- `maxConcurrency` is too low
- Tasks are taking too long to complete

**Solutions**:
- This is often normal under sustained load
- If problematic, increase concurrency or reduce starvation timeout
- Consider whether priority assignments are appropriate
