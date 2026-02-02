---
title: "backoff"
sidebar_position: 7
---

# backoff

Control how the delay between retries changes over time.

## Syntax

```constellation
result = Module(args) with retry: N, delay: D, backoff: <strategy>
```

**Type:** Strategy enum (`fixed`, `linear`, `exponential`)

## Description

The `backoff` option determines how the delay between retry attempts increases (or stays constant). It works together with `delay` to calculate the actual wait time before each retry.

## Strategies

### fixed

Constant delay between all retries. This is the default if `backoff` is not specified.

```constellation
result = Service(input) with retry: 4, delay: 1s, backoff: fixed
```

Delays: 1s, 1s, 1s, 1s

### linear

Delay increases linearly with each attempt (N × base delay).

```constellation
result = Service(input) with retry: 4, delay: 1s, backoff: linear
```

Delays: 1s, 2s, 3s, 4s

### exponential

Delay doubles with each attempt (2^(N-1) × base delay), capped at 30 seconds.

```constellation
result = Service(input) with retry: 4, delay: 1s, backoff: exponential
```

Delays: 1s, 2s, 4s, 8s

## Delay Table

Given `delay: 1s`:

| Retry # | Fixed | Linear | Exponential |
|---------|-------|--------|-------------|
| 1 → 2 | 1s | 1s | 1s |
| 2 → 3 | 1s | 2s | 2s |
| 3 → 4 | 1s | 3s | 4s |
| 4 → 5 | 1s | 4s | 8s |
| 5 → 6 | 1s | 5s | 16s |
| 6 → 7 | 1s | 6s | 30s (capped) |
| 7 → 8 | 1s | 7s | 30s (capped) |

## Examples

### Exponential Backoff for Rate Limiting

```constellation
response = RateLimitedApi(request) with
    retry: 5,
    delay: 500ms,
    backoff: exponential
```

Respects rate limits by backing off exponentially: 0.5s, 1s, 2s, 4s, 8s.

### Linear Backoff for Gradual Recovery

```constellation
result = RecoveringService(data) with
    retry: 5,
    delay: 2s,
    backoff: linear
```

Gives the service increasing time to recover: 2s, 4s, 6s, 8s, 10s.

### Fixed Backoff for Consistent Behavior

```constellation
cached = RefreshCache(key) with
    retry: 3,
    delay: 1s,
    backoff: fixed
```

Predictable timing: 1s, 1s, 1s.

## Behavior

The backoff strategy affects the `computeDelay` function:

```
delay = base_delay × multiplier

where multiplier for attempt N is:
  fixed:       1
  linear:      N
  exponential: 2^(N-1)  (capped so delay ≤ 30s)
```

## Related Options

- **[delay](./delay.md)** - Required base delay value
- **[retry](./retry.md)** - Required for backoff to have effect
- **[timeout](./timeout.md)** - Time limit per attempt

## Diagnostics

| Warning | Cause |
|---------|-------|
| backoff without delay | Backoff requires a base delay to multiply |
| backoff without retry | Backoff has no effect without retry |

## Best Practices

- Use `exponential` for external APIs with rate limits
- Use `linear` when you expect gradual service recovery
- Use `fixed` for internal services with consistent behavior
- Combine with reasonable `timeout` to avoid long waits
