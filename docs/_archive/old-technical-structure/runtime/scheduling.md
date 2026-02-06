# Scheduling

> **Path**: `docs/runtime/scheduling.md`
> **Parent**: [runtime/](./README.md)

Priority-based task scheduling with bounded concurrency and starvation prevention.

## Overview

By default, Constellation uses an **unbounded scheduler** that executes all tasks immediately. When enabled, the **bounded scheduler** limits concurrency and uses priority ordering.

| Scheduler | Behavior |
|-----------|----------|
| Unbounded (default) | All tasks run immediately via `parTraverse` |
| Bounded | Limited slots, priority queue determines order |

## Configuration

Environment variables control scheduler behavior:

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_SCHEDULER_ENABLED` | `false` | Enable bounded scheduler |
| `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY` | `16` | Maximum concurrent tasks |
| `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` | `30s` | Time before priority boost |

### Example

```bash
CONSTELLATION_SCHEDULER_ENABLED=true \
CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=8 \
sbt "exampleApp/run"
```

## Priority Levels

Priority values range from 0 to 100:

| Level | Value | Use Case |
|-------|-------|----------|
| Critical | 100 | Time-sensitive operations |
| High | 80 | Important user-facing tasks |
| Normal | 50 | Default (implicit) |
| Low | 20 | Background processing |
| Background | 0 | Non-urgent work |

### In constellation-lang

```constellation
# High priority
result = MyModule(input) with priority: high

# Normal priority (default)
result = MyModule(input)

# Explicit numeric
result = MyModule(input) with priority: 80
```

## How It Works

### Bounded Scheduler Components

```
┌─────────────────────────────────────────┐
│           GlobalScheduler               │
│  ┌─────────────┐  ┌─────────────────┐   │
│  │  Semaphore  │  │  Priority Queue │   │
│  │(concurrency)│  │    (TreeSet)    │   │
│  └─────────────┘  └─────────────────┘   │
│  ┌─────────────────────────────────────┐│
│  │       Aging Fiber (starvation)      ││
│  └─────────────────────────────────────┘│
└─────────────────────────────────────────┘
```

### Submission Flow

1. Task submitted with priority
2. Entry added to priority queue
3. If slots available, highest priority task starts
4. Task completes, slot released
5. Next highest priority task starts

### Priority Ordering

Tasks ordered by:
1. **Effective priority** (higher first)
2. **Submission time** (earlier first for FIFO within same priority)

## Starvation Prevention

The scheduler uses an **aging mechanism** to prevent low-priority task starvation:

- Every 5 seconds, waiting tasks get +10 effective priority
- After 30 seconds, a Background(0) task reaches Normal(60) priority

```
Time 0s:  Background task with priority 0
Time 5s:  Effective priority = 10
Time 10s: Effective priority = 20
...
Time 30s: Effective priority = 60 (now higher than Normal)
```

## Programmatic Usage

### Creating a Scheduler

```scala
import io.constellation.execution.GlobalScheduler
import scala.concurrent.duration._

// Unbounded (default behavior)
val unbounded = GlobalScheduler.unbounded

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

### Task Submission

```scala
// Submit with priority
scheduler.submit(80, IO.println("High priority"))
scheduler.submit(20, IO.println("Low priority"))

// Submit with default normal priority (50)
scheduler.submitNormal(IO.println("Normal priority"))

// Get stats
scheduler.stats.flatMap(s => IO.println(s"Active: ${s.activeCount}"))
```

## Monitoring

The `/metrics` endpoint includes scheduler statistics:

```json
{
  "scheduler": {
    "enabled": true,
    "activeCount": 4,
    "queuedCount": 12,
    "totalSubmitted": 1523,
    "totalCompleted": 1507,
    "starvationPromotions": 23
  }
}
```

| Metric | Description |
|--------|-------------|
| `activeCount` | Tasks currently executing |
| `queuedCount` | Tasks waiting in priority queue |
| `totalSubmitted` | Total tasks submitted since start |
| `totalCompleted` | Total tasks completed since start |
| `starvationPromotions` | Times aging boosted a task's priority |

## Behavior Comparison

### Without Scheduler (Default)

```
Execution A: [task1(low), task2(low), task3(low)]
Execution B: [task4(high), task5(high)]

All 5 tasks start immediately
Completion order depends on individual task duration
```

### With Bounded Scheduler (maxConcurrency=2)

```
Execution A: [task1(low), task2(low), task3(low)]
Execution B: [task4(high), task5(high)]

task4, task5 start first (high priority)
When slot available, task1/2/3 start in order
```

Both produce the same **results**, but with different **timing**.

## Best Practices

1. **Default to unbounded** - Only enable when you need priority control
2. **Use priority sparingly** - Most tasks should use default `normal`
3. **Match concurrency to capacity** - Set `maxConcurrency` based on downstream limits
4. **Monitor starvation** - High promotions may indicate starved low-priority tasks

## Related Files

| File | Purpose |
|------|---------|
| `modules/runtime/.../execution/GlobalScheduler.scala` | Core implementation |
| `modules/runtime/.../Runtime.scala` | DAG execution with scheduler |
| [../dev/global-scheduler.md](../dev/global-scheduler.md) | Full developer reference |
