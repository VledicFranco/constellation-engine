---
title: "priority"
sidebar_position: 12
description: "Set the scheduling priority for module execution to control task ordering when resources are constrained."
---

# priority

Set the scheduling priority for module execution.

## Syntax

```constellation
result = Module(args) with priority: <level>
# or
result = Module(args) with priority: <number>
```

**Type:** Priority level or Integer

## Description

The `priority` option provides a hint to the scheduler about the relative importance of a module call. Higher priority tasks are scheduled before lower priority ones when resources are constrained.

Priority can be specified as a named level or as a numeric value.

> **Note:** Priority only affects scheduling when the bounded scheduler is enabled. See [Scheduler Configuration](#scheduler-configuration) below.

## Priority Levels

| Level | Value | Description |
|-------|-------|-------------|
| `critical` | 100 | Highest priority, minimal queuing |
| `high` | 80 | Above normal, preferred scheduling |
| `normal` | 50 | Default priority |
| `low` | 20 | Below normal, can be delayed |
| `background` | 0 | Lowest priority, runs when idle |

## Examples

### Named Priority Level

```constellation
# Critical alert processing - highest priority
alert = ProcessAlert(event) with priority: critical

# Background cleanup - lowest priority
cleanup = RunCleanup(data) with priority: background
```

### Numeric Priority

```constellation
# Custom priority value (0-100)
important = HighValue(x) with priority: 90
routine = Normal(y) with priority: 50
deferred = LowValue(z) with priority: 10
```

### Combined with Other Options

```constellation
response = CriticalApi(request) with
    priority: critical,
    retry: 5,
    timeout: 30s
```

### Prioritized Pipeline

```constellation
in events: List[Event]

# Process events by priority
processed = events.map(e =>
    when e.severity == "critical" then
        Handle(e) with priority: critical
    else when e.severity == "high" then
        Handle(e) with priority: high
    else
        Handle(e) with priority: normal
)

out processed
```

## Scheduler Configuration

By default, Constellation uses an **unbounded scheduler** that executes all parallel tasks immediately. In this mode, priority values are stored but do not affect execution order.

To enable priority-based scheduling, use the **bounded scheduler** via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_SCHEDULER_ENABLED` | `false` | Enable bounded scheduler |
| `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY` | `16` | Maximum concurrent tasks |
| `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` | `30s` | Time before priority boost |

### Enabling the Scheduler

```bash
# Enable bounded scheduler with default settings
CONSTELLATION_SCHEDULER_ENABLED=true

# Enable with custom concurrency limit
CONSTELLATION_SCHEDULER_ENABLED=true CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=8
```

### Starvation Prevention

The bounded scheduler includes starvation prevention via an **aging mechanism**:
- Waiting tasks receive +10 effective priority every 5 seconds
- After 30 seconds, a `background` (0) task reaches 60 effective priority
- This ensures low-priority tasks eventually execute

For full details, see [Global Scheduler Documentation](#).

## Behavior

When the bounded scheduler is enabled:

1. When a module call with priority is made:
   - Create a `PrioritizedTask` with the priority value
   - Submit to the priority scheduler
2. The scheduler processes tasks by:
   - Sorting by effective priority (highest first)
   - Breaking ties by submission time (FIFO)
   - Executing in priority order within concurrency limits

When the scheduler is disabled (default):
- All parallel tasks execute immediately via `parTraverse`
- Priority values are ignored (no scheduling effect)

## Priority Ordering

Higher numeric values = higher priority:

```
critical (100) > high (80) > normal (50) > low (20) > background (0)
```

Tasks with the same priority are processed in submission order.

## Scheduler Statistics

When the bounded scheduler is enabled, it tracks execution metrics.

Access via the `/metrics` endpoint:
```bash
curl http://localhost:8080/metrics | jq .scheduler
```

Example response:
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

## Related Options

- **[concurrency](./concurrency.md)** - Affects scheduling capacity
- **[throttle](./throttle.md)** - Rate limits after priority scheduling

## Best Practices

- Reserve `critical` for truly urgent operations
- Use `background` for non-time-sensitive tasks
- Default to `normal` unless priority is important
- Monitor queue depths to detect priority inversion
- Avoid having too many `critical` tasks (defeats purpose)
- Consider business impact when assigning priorities
