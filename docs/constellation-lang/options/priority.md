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

## Priority Levels

| Level | Value | Description |
|-------|-------|-------------|
| `critical` | 100 | Highest priority, minimal queuing |
| `high` | 75 | Above normal, preferred scheduling |
| `normal` | 50 | Default priority |
| `low` | 25 | Below normal, can be delayed |
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

## Behavior

1. When a module call with priority is made:
   - Create a `PrioritizedTask` with the priority value
   - Submit to the priority scheduler
2. The scheduler processes tasks by:
   - Sorting by priority (highest first)
   - Breaking ties by submission time (FIFO)
   - Executing in priority order

## Priority Ordering

Higher numeric values = higher priority:

```
critical (100) > high (75) > normal (50) > low (25) > background (0)
```

Tasks with the same priority are processed in submission order.

## Scheduler Statistics

The priority scheduler tracks:
- Tasks submitted per priority level
- Average wait time by priority
- Current queue depth

Access via the `/metrics` endpoint:
```bash
curl http://localhost:8080/metrics | jq .scheduler
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
