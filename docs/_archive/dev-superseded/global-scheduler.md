# Global Priority Scheduler

The Global Priority Scheduler provides bounded concurrency with priority ordering across all concurrent DAG executions. High-priority tasks from any execution run before low-priority tasks when the system is under load.

## Overview

By default, Constellation uses an **unbounded scheduler** that executes all tasks immediately without any concurrency limiting. This preserves the current behavior where all parallel modules in a DAG layer run simultaneously.

When enabled, the **bounded scheduler** limits the number of concurrent tasks and uses a priority queue to determine execution order. This is useful for:

- **Resource management**: Prevent overwhelming downstream services
- **Priority-based execution**: Ensure critical tasks complete first
- **Fair scheduling**: Starvation prevention ensures all tasks eventually run

## Configuration

The scheduler is configured via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_SCHEDULER_ENABLED` | `false` | Enable bounded scheduler |
| `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY` | `16` | Maximum concurrent tasks |
| `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` | `30s` | Time before priority boost |

### Examples

```bash
# Enable bounded scheduler with default settings
CONSTELLATION_SCHEDULER_ENABLED=true

# Enable with custom concurrency limit
CONSTELLATION_SCHEDULER_ENABLED=true CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=8

# Enable with longer starvation timeout
CONSTELLATION_SCHEDULER_ENABLED=true CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT=60s
```

## Priority Levels

Priority values range from 0 to 100. The following conventions are recommended:

| Level | Value | Use Case |
|-------|-------|----------|
| Critical | 100 | Time-sensitive operations |
| High | 80 | Important user-facing tasks |
| Normal | 50 | Default (implicit) |
| Low | 20 | Background processing |
| Background | 0 | Non-urgent work |

### Using Priority in constellation-lang

```constellation
# High priority module call
result = MyModule(input) with priority: high

# Normal priority (default)
result = MyModule(input)

# Low priority for background work
result = BackgroundTask(data) with priority: low

# Explicit numeric priority
result = MyModule(input) with priority: 80
```

### Priority Mapping

The language keywords map to numeric values:

- `critical` → 100
- `high` → 80
- `normal` → 50
- `low` → 20
- `background` → 0

## How It Works

### Unbounded Scheduler (Default)

When the scheduler is disabled (default), tasks execute immediately:

```
submit(priority, task) → task executes immediately
```

This preserves the original behavior where all modules in a DAG layer run in parallel via `parTraverse`.

### Bounded Scheduler

When enabled, the bounded scheduler uses:

1. **Semaphore** for concurrency limiting
2. **Priority Queue** (TreeSet) for task ordering
3. **Deferred gates** for task synchronization
4. **Background fiber** for starvation prevention (aging)

#### Submission Flow

```
1. Task submitted with priority
2. Entry added to priority queue
3. If slots available, highest priority task starts
4. Task completes, slot released
5. Next highest priority task starts
```

#### Priority Ordering

Tasks are ordered by:
1. **Effective priority** (higher first)
2. **Submission time** (earlier first, for FIFO within same priority)

### Starvation Prevention

Low-priority tasks are not starved indefinitely. The scheduler uses an **aging mechanism**:

- Every 5 seconds, waiting tasks get +10 effective priority
- After 30 seconds, a Background(0) task reaches Normal(60) priority
- This ensures even low-priority tasks eventually execute

Example aging:
```
Time 0s:  Background task with priority 0
Time 5s:  Effective priority = 10
Time 10s: Effective priority = 20
Time 15s: Effective priority = 30
...
Time 30s: Effective priority = 60 (now higher than Normal)
```

## Architecture

### Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ ExampleApp  │  │ HTTP Server │  │   LSP       │         │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘         │
└─────────┼────────────────┼────────────────┼─────────────────┘
          │                │                │
          ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────┐
│                ConstellationImpl                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                   GlobalScheduler                      │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌───────────────┐  │  │
│  │  │  Semaphore  │  │ PriorityQ   │  │ Aging Fiber   │  │  │
│  │  │(concurrency)│  │  (TreeSet)  │  │ (starvation)  │  │  │
│  │  └─────────────┘  └─────────────┘  └───────────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│                      Runtime.scala                           │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  runnable.parTraverse { module =>                     │  │
│  │    scheduler.submit(priority, module.run(runtime))    │  │
│  │  }                                                    │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `modules/runtime/.../execution/GlobalScheduler.scala` | Core scheduler implementation |
| `modules/runtime/.../Runtime.scala` | DAG execution with scheduler |
| `modules/runtime/.../impl/ConstellationImpl.scala` | Scheduler integration |
| `modules/lang-compiler/.../ModuleOptionsExecutor.scala` | Priority option handling |
| `modules/http-api/.../ConstellationServer.scala` | Server configuration |

## Monitoring

### Metrics Endpoint

When the scheduler is enabled, the `/metrics` endpoint includes scheduler statistics:

```json
{
  "scheduler": {
    "enabled": true,
    "activeCount": 4,
    "queuedCount": 12,
    "totalSubmitted": 1523,
    "totalCompleted": 1507,
    "highPriorityCompleted": 312,
    "lowPriorityCompleted": 89,
    "starvationPromotions": 23
  }
}
```

### Statistics Explained

| Metric | Description |
|--------|-------------|
| `activeCount` | Tasks currently executing |
| `queuedCount` | Tasks waiting in priority queue |
| `totalSubmitted` | Total tasks submitted since start |
| `totalCompleted` | Total tasks completed since start |
| `highPriorityCompleted` | Tasks with priority >= 75 |
| `lowPriorityCompleted` | Tasks with priority < 25 |
| `starvationPromotions` | Times aging boosted a task's priority |

## Programmatic Usage

### Creating a Scheduler

```scala
import io.constellation.execution.GlobalScheduler
import cats.effect.IO

// Unbounded (default behavior)
val unbounded: GlobalScheduler = GlobalScheduler.unbounded

// Bounded with Resource lifecycle
val boundedResource = GlobalScheduler.bounded(
  maxConcurrency = 16,
  starvationTimeout = 30.seconds
)

// Use within Resource
boundedResource.use { scheduler =>
  scheduler.submit(80, myHighPriorityTask) *>
  scheduler.submit(20, myLowPriorityTask)
}
```

### With ConstellationImpl

```scala
import io.constellation.impl.ConstellationImpl
import io.constellation.http.ConstellationServer

// Using schedulerResource helper
ConstellationServer.schedulerResource.use { scheduler =>
  for {
    constellation <- ConstellationImpl.initWithScheduler(scheduler)
    // ... use constellation
  } yield ()
}
```

### Direct Task Submission

```scala
// Submit with priority
scheduler.submit(80, IO.println("High priority"))
scheduler.submit(20, IO.println("Low priority"))

// Submit with default normal priority (50)
scheduler.submitNormal(IO.println("Normal priority"))

// Get stats
scheduler.stats.flatMap(s => IO.println(s"Active: ${s.activeCount}"))
```

## Behavior Comparison

### Without Scheduler (Default)

```
Execution A: [task1(low), task2(low), task3(low)]
Execution B: [task4(high), task5(high)]

→ All 5 tasks start immediately
→ Completion order depends on individual task duration
```

### With Bounded Scheduler (maxConcurrency=2)

```
Execution A: [task1(low), task2(low), task3(low)]
Execution B: [task4(high), task5(high)]

→ task4, task5 start first (high priority)
→ When slot available, task1/2/3 start in order
→ High-priority work completes faster
```

Both produce the same **results**, but with different **timing**. The scheduler affects execution order, not correctness.

## Best Practices

1. **Default to unbounded**: Only enable the scheduler when you need priority control or bounded concurrency.

2. **Use priority sparingly**: Most tasks should use the default `normal` priority. Reserve `high`/`critical` for truly time-sensitive operations.

3. **Set appropriate concurrency**: The `maxConcurrency` should match your downstream capacity (e.g., database connection pool size, external API rate limits).

4. **Monitor starvation promotions**: High `starvationPromotions` may indicate that low-priority tasks are being starved. Consider increasing concurrency or reducing high-priority traffic.

5. **Resource lifecycle**: Use `GlobalScheduler.bounded` with `Resource` for proper cleanup. The scheduler runs a background fiber that should be cancelled on shutdown.

## Troubleshooting

### Tasks Not Running

**Symptom**: Tasks appear stuck, `queuedCount` is high but `activeCount` is 0.

**Possible causes**:
- Scheduler was shutdown (check for `shuttingDown` state)
- Semaphore deadlock (shouldn't happen with current implementation)

**Solution**: Check scheduler stats, restart application if needed.

### Low Priority Tasks Never Run

**Symptom**: `lowPriorityCompleted` stays at 0, `starvationPromotions` keeps increasing.

**Possible causes**:
- Constant stream of high-priority tasks
- `maxConcurrency` too low relative to high-priority load

**Solution**: Increase `maxConcurrency` or reduce starvation timeout.

### Memory Usage Increasing

**Symptom**: Memory grows over time, `queuedCount` keeps increasing.

**Possible causes**:
- Tasks submitted faster than completed
- Task execution is failing silently

**Solution**: Check task completion rate, increase concurrency, or add backpressure at submission.
