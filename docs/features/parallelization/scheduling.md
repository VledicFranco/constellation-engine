# Scheduling

> **Path**: `docs/features/parallelization/scheduling.md`
> **Parent**: [parallelization/](./README.md)

Priority-based task scheduling with bounded concurrency and starvation prevention.

## Quick Example

```constellation
# High-priority: user-facing request
userData = GetUser(id) with priority: high

# Low-priority: analytics
analytics = TrackEvent(id) with priority: low

# Critical: payment processing
payment = ProcessPayment(order) with priority: critical
```

With bounded scheduler enabled:

```bash
CONSTELLATION_SCHEDULER_ENABLED=true
CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=8
```

High and critical tasks execute before low-priority tasks when slots are limited.

## Scheduler Modes

### Unbounded (Default)

All tasks run immediately via `parTraverse`. No queuing, no priority ordering.

```scala
// Unbounded execution
layer.modules.parTraverse(_.run)
```

**Use when:**
- Resources are ample
- All modules are equally important
- Simplicity is preferred

### Bounded

Limited slots with priority queue. Tasks wait for available slots, highest priority first.

```scala
// Bounded execution
layer.modules.parTraverse { module =>
  scheduler.submit(module.priority, module.run)
}
```

**Use when:**
- Downstream resources have limits (DB connections, API rate limits)
- Some modules are more important than others
- Memory usage from unbounded parallelism is a concern

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
CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT=60s \
sbt "exampleApp/run"
```

## Priority Levels

Priority values range from 0 to 100:

| Level | Value | Use Case |
|-------|-------|----------|
| `critical` | 100 | Time-sensitive operations (payments, auth) |
| `high` | 80 | Important user-facing tasks |
| `normal` | 50 | Default (implicit) |
| `low` | 20 | Background processing |
| `background` | 0 | Non-urgent work (analytics, cleanup) |

### In Constellation-Lang

```constellation
# Named priority
result = MyModule(input) with priority: high

# Numeric priority
result = MyModule(input) with priority: 80

# Default (normal = 50)
result = MyModule(input)
```

### Priority Resolution

1. Explicit priority from `with priority:` clause
2. Default priority (50 = normal)

## How the Bounded Scheduler Works

### Components

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

```
1. Task submitted with priority
   ↓
2. Entry added to priority queue
   ↓
3. If slots available → highest priority task starts
   ↓
4. Task completes → slot released
   ↓
5. Next highest priority task starts
```

### Priority Ordering

Tasks are ordered by:

1. **Effective priority** (higher first)
2. **Submission time** (earlier first, for FIFO within same priority)

```scala
// QueueEntry ordering
implicit val ordering: Ordering[QueueEntry] =
  Ordering.by[QueueEntry, (Int, Long)](e => (-e.effectivePriority, e.id))
```

## Starvation Prevention

Low-priority tasks could starve if high-priority tasks keep arriving. The scheduler uses **aging** to prevent this:

- Every 5 seconds, waiting tasks get +10 effective priority
- After 30 seconds, a `background (0)` task reaches `normal (60)` priority

```
Time 0s:  Background task with priority 0
Time 5s:  Effective priority = 10
Time 10s: Effective priority = 20
Time 15s: Effective priority = 30
Time 20s: Effective priority = 40
Time 25s: Effective priority = 50 (equal to Normal)
Time 30s: Effective priority = 60 (now higher than Normal)
```

### Configuring Starvation Timeout

```bash
# Shorter timeout = more aggressive aging
CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT=15s

# Longer timeout = more priority differentiation
CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT=60s
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
  scheduler.submit(80, highPriorityTask) *>
  scheduler.submit(20, lowPriorityTask)
}
```

### Task Submission

```scala
// Submit with explicit priority
scheduler.submit(80, IO.println("High priority"))
scheduler.submit(20, IO.println("Low priority"))

// Submit with default normal priority (50)
scheduler.submitNormal(IO.println("Normal priority"))
```

### Checking Stats

```scala
scheduler.stats.flatMap { s =>
  IO.println(s"Active: ${s.activeCount}, Queued: ${s.queuedCount}")
}
```

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `runtime` | Scheduler trait and unbounded impl | `modules/runtime/src/main/scala/io/constellation/execution/GlobalScheduler.scala:72-108` |
| `runtime` | Bounded scheduler with priority queue | `modules/runtime/src/main/scala/io/constellation/execution/GlobalScheduler.scala:239-334` |
| `runtime` | Priority queue entry | `modules/runtime/src/main/scala/io/constellation/execution/GlobalScheduler.scala:150-165` |
| `runtime` | Starvation prevention (aging) | `modules/runtime/src/main/scala/io/constellation/execution/GlobalScheduler.scala:364-406` |
| `runtime` | DAG execution with scheduler | `modules/runtime/src/main/scala/io/constellation/Runtime.scala:153-189` |

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
    "highPriorityCompleted": 342,
    "lowPriorityCompleted": 891,
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
| `highPriorityCompleted` | Tasks completed with priority >= 75 |
| `lowPriorityCompleted` | Tasks completed with priority < 25 |
| `starvationPromotions` | Times aging boosted a task's priority |

### Health Indicators

| Indicator | Healthy | Concerning |
|-----------|---------|------------|
| `queuedCount` | Close to 0 | Growing over time |
| `starvationPromotions` | Low | High (low-priority tasks waiting too long) |
| `activeCount` | Below or at max | Always at max (contention) |

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

1. **Default to unbounded.** Only enable when you need priority control or resource limits.

2. **Use priority sparingly.** Most tasks should use default `normal`. Reserve `high` and `critical` for truly important paths.

3. **Match concurrency to capacity.** Set `maxConcurrency` based on downstream limits (DB pool size, API rate limits).

4. **Monitor starvation.** High `starvationPromotions` may indicate:
   - Too many high-priority tasks
   - `maxConcurrency` too low
   - Starvation timeout too long

5. **Don't use priority for ordering.** Priority is about importance, not sequence. Use data dependencies for ordering.

## Troubleshooting

### High-priority tasks are slow

**Possible causes:**
- Too many high-priority tasks competing
- `maxConcurrency` too low for workload

**Solutions:**
- Reduce high-priority task count
- Increase `maxConcurrency`
- Check if tasks should really be high priority

### Low-priority tasks never complete

**Possible causes:**
- Starvation (high-priority tasks monopolizing slots)
- Queue backup

**Solutions:**
- Decrease `starvationTimeout` to age tasks faster
- Increase `maxConcurrency`
- Reduce high-priority task rate

### Queue keeps growing

**Possible causes:**
- Submission rate exceeds completion rate
- Tasks are slower than expected

**Solutions:**
- Increase `maxConcurrency`
- Optimize slow modules
- Add backpressure at the API level

## Related

- [layer-execution.md](./layer-execution.md) - How layers determine execution order
- [performance.md](./performance.md) - Tuning parallelization behavior
- [../resilience/throttle.md](../resilience/throttle.md) - Rate limiting individual modules
