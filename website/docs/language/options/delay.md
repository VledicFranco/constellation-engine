---
title: "delay"
sidebar_position: 6
description: "Set the base wait time between retry attempts, optionally modified by a backoff strategy."
---

# delay

Set the base delay between retry attempts.

## Syntax

```constellation
result = Module(args) with retry: N, delay: <duration>
```

**Type:** Duration (`ms`, `s`, `min`, `h`, `d`)

## Description

The `delay` option specifies the wait time between retry attempts. It is typically used with `retry` and optionally with `backoff` to control how the delay changes over time.

Without `backoff` or with `backoff: fixed`, the delay remains constant. With other backoff strategies, the `delay` value serves as the base delay that gets modified.

## Examples

### Fixed Delay

```constellation
result = FlakyService(input) with retry: 3, delay: 1s
```

Wait 1 second between each retry attempt.

### Delay with Backoff

```constellation
response = ApiCall(request) with
    retry: 5,
    delay: 500ms,
    backoff: exponential
```

Delays: 500ms, 1s, 2s, 4s, 8s (exponential increase).

### Short Delay for Fast Retries

```constellation
cached = GetFromCache(key) with retry: 2, delay: 50ms
```

Quick retries for transient cache misses.

### Delay Without Retry

```constellation
# Warning: delay has no effect without retry
result = Operation(data) with delay: 1s
```

This generates a compiler warning since `delay` only applies between retries.

## Behavior

1. First attempt executes immediately
2. If the attempt fails and retries remain:
   - Wait for the computed delay (based on `delay` and `backoff`)
   - Execute the next attempt
3. Continue until success or retries exhausted

## Delay Calculation with Backoff

| Strategy | Formula | Example (base: 1s) |
|----------|---------|-------------------|
| `fixed` | constant | 1s, 1s, 1s, 1s |
| `linear` | N × delay | 1s, 2s, 3s, 4s |
| `exponential` | 2^(N-1) × delay | 1s, 2s, 4s, 8s |

Note: Exponential backoff is capped at 30 seconds maximum.

## Duration Units

| Unit | Suffix | Example |
|------|--------|---------|
| Milliseconds | `ms` | `100ms`, `500ms` |
| Seconds | `s` | `1s`, `5s` |
| Minutes | `min` | `1min` |
| Hours | `h` | `1h` |
| Days | `d` | `1d` |

## Related Options

- **[retry](./retry.md)** - Required for delay to have effect
- **[backoff](./backoff.md)** - How delay changes over retries
- **[timeout](./timeout.md)** - Time limit per attempt

## Diagnostics

| Warning | Cause |
|---------|-------|
| delay without retry | Delay has no effect without retry option |

## Best Practices

- Start with short delays (100ms-1s)
- Use exponential backoff for rate-limited services
- Consider service recovery time when setting delays
- Balance retry speed against server load
