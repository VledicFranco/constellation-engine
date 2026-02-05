# Advanced Options

> **Path**: `docs/language/options/advanced.md`
> **Parent**: [options/](./README.md)

Error handling, lazy evaluation, and scheduling priority.

## on_error

Strategy for handling module failures.

```constellation
result = RiskyOperation(data) with on_error: skip
```

| Strategy | Behavior |
|----------|----------|
| `propagate` | Re-throw error (default) |
| `skip` | Return zero value for output type |
| `log` | Log error, return zero value |
| `wrap` | Wrap error in result type |

### Zero Values

| Type | Zero Value |
|------|------------|
| String | `""` |
| Int | `0` |
| Float | `0.0` |
| Boolean | `false` |
| List | `[]` |
| Record | `{}` (empty) |
| Optional | absent |

### With Fallback

`fallback` takes precedence over `on_error`:

```constellation
# Uses fallback value, not zero value
result = Fetch(id) with fallback: default, on_error: skip
```

### Use Cases

```constellation
# Log and continue with default
stats = ComputeStats(data) with on_error: log

# Silently skip non-critical enrichment
extra = GetExtra(id) with on_error: skip
```

## lazy

Defer execution until result is needed.

```constellation
expensive = HeavyCompute(data) with lazy: true
```

### Behavior

- Module only executes if its output is declared with `out`
- Or if another module depends on its result
- Useful for conditional execution

### Use Cases

```constellation
# Only compute if explicitly needed
debug = GenerateDebugInfo(data) with lazy: true
# out debug  ← uncomment to trigger execution

# Conditional in pipeline
premium = GetPremiumData(user) with lazy: true
result = premium when user.tier == "premium"
out result
```

## priority

Hint for execution scheduling order.

```constellation
critical = ProcessPayment(order) with priority: critical
background = GenerateReport(data) with priority: background
```

### Priority Levels

| Level | Value | Use Case |
|-------|-------|----------|
| `critical` | 100 | Payment, auth, real-time |
| `high` | 80 | User-facing requests |
| `normal` | 50 | Default |
| `low` | 20 | Batch processing |
| `background` | 0 | Reports, cleanup |

### Numeric Values

Can use integers 0-100:

```constellation
result = Process(data) with priority: 75
```

### Requires Global Scheduler

Priority only affects ordering when scheduler is enabled:

```bash
CONSTELLATION_SCHEDULER_ENABLED=true
```

Without scheduler, all modules execute as soon as dependencies are ready.

### Starvation Prevention

Low-priority tasks are boosted after waiting too long:

```bash
CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT=30s
```

## Combining Advanced Options

```constellation
# Low-priority, lazy, tolerant of failures
analytics = TrackEvent(event)
  with priority: background,
       lazy: true,
       on_error: log

# Critical, fast-fail
payment = ChargeCard(order)
  with priority: critical,
       timeout: 5s
       # on_error defaults to propagate
```
