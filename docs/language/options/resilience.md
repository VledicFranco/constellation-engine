# Resilience Options

> **Path**: `docs/language/options/resilience.md`
> **Parent**: [options/](./README.md)

Options for handling failures: retry, timeout, fallback, delay, backoff.

## retry

Retry failed module calls.

```constellation
data = FetchData(id) with retry: 3
```

| Value | Meaning |
|-------|---------|
| `0` | No retries (fail immediately) |
| `N` | Up to N retries after initial failure |

Total attempts = 1 (initial) + N (retries)

## timeout

Maximum execution time per attempt.

```constellation
result = SlowService(req) with timeout: 10s
```

| Unit | Example |
|------|---------|
| `ms` | `timeout: 500ms` |
| `s` | `timeout: 5s` |
| `min` | `timeout: 2min` |

With retry: timeout applies to each attempt separately.

```constellation
# Each attempt has 5s, up to 4 attempts total
data = Fetch(id) with retry: 3, timeout: 5s
# Max total time: 4 × 5s = 20s (plus delays)
```

## fallback

Default value when module fails (after all retries exhausted).

```constellation
# Literal value
user = GetUser(id) with fallback: { name: "Guest", tier: "free" }

# Another module call
data = Primary(id) with fallback: Secondary(id)
```

With fallback, the pipeline continues instead of failing.

## delay

Wait time between retry attempts.

```constellation
data = Fetch(id) with retry: 3, delay: 500ms
```

**Warning**: `delay` without `retry` has no effect.

## backoff

Strategy for increasing delay between retries.

| Strategy | Behavior |
|----------|----------|
| `fixed` | Same delay every retry |
| `linear` | delay × attempt number |
| `exponential` | delay × 2^attempt (capped at 30s) |

```constellation
# Exponential: 500ms, 1s, 2s, 4s...
data = Fetch(id) with retry: 5, delay: 500ms, backoff: exponential

# Linear: 1s, 2s, 3s, 4s...
data = Fetch(id) with retry: 4, delay: 1s, backoff: linear
```

**Requires**: `delay` must be set.

## Combining Options

### Full Resilience Pattern
```constellation
data = ExternalAPI(request)
  with retry: 3,
       timeout: 10s,
       delay: 500ms,
       backoff: exponential,
       fallback: { status: "unavailable" }
```

Execution flow:
1. Attempt 1 (timeout: 10s)
2. If fails: wait 500ms
3. Attempt 2 (timeout: 10s)
4. If fails: wait 1s (exponential)
5. Attempt 3 (timeout: 10s)
6. If fails: wait 2s (exponential)
7. Attempt 4 (timeout: 10s)
8. If still fails: return fallback

### Timeout-Only
```constellation
result = Slow(data) with timeout: 30s
# Fails immediately if exceeds 30s
```

### Retry Without Delay
```constellation
result = Flaky(data) with retry: 2
# Immediate retries (no delay)
```
