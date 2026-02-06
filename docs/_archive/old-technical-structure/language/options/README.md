# Module Options

> **Path**: `docs/language/options/`
> **Parent**: [language/](../README.md)

Orchestration options for module calls.

## Contents

| File | Description |
|------|-------------|
| [resilience.md](./resilience.md) | retry, timeout, fallback, delay, backoff |
| [caching.md](./caching.md) | cache, cache_backend |
| [rate-control.md](./rate-control.md) | throttle, concurrency |
| [advanced.md](./advanced.md) | on_error, lazy, priority |

## Syntax

```constellation
result = Module(args) with option1: value1, option2: value2
```

## All Options

| Option | Type | Example | Purpose |
|--------|------|---------|---------|
| `retry` | Int | `retry: 3` | Retry on failure |
| `timeout` | Duration | `timeout: 5s` | Max execution time |
| `fallback` | Expression | `fallback: {}` | Default on failure |
| `delay` | Duration | `delay: 500ms` | Wait between retries |
| `backoff` | Strategy | `backoff: exponential` | Delay growth |
| `cache` | Duration | `cache: 15min` | Cache results |
| `cache_backend` | String | `cache_backend: "redis"` | Named cache |
| `throttle` | Rate | `throttle: 100/1min` | Rate limit |
| `concurrency` | Int | `concurrency: 5` | Max parallel |
| `on_error` | Strategy | `on_error: skip` | Error handling |
| `lazy` | Boolean | `lazy: true` | Defer execution |
| `priority` | Level | `priority: high` | Scheduling hint |

## Duration Values

| Unit | Example |
|------|---------|
| Milliseconds | `500ms` |
| Seconds | `5s` |
| Minutes | `15min` |
| Hours | `1h` |
| Days | `1d` |

## Option Interactions

| Combination | Behavior |
|-------------|----------|
| `retry` + `timeout` | Timeout applies per attempt |
| `retry` + `delay` | Delay between each retry |
| `retry` + `backoff` | Delay increases each retry |
| `cache` + `retry` | Cache after successful attempt |
| `fallback` + `on_error` | Fallback takes precedence |

## Common Patterns

### Resilient External Call
```constellation
data = FetchData(id) with retry: 3, timeout: 10s, backoff: exponential
```

### Cached Lookup
```constellation
user = GetUser(id) with cache: 15min, timeout: 5s
```

### Rate-Limited API
```constellation
result = ExternalAPI(req) with throttle: 100/1min, retry: 2
```

### Background Processing
```constellation
report = GenerateReport(data) with priority: low, lazy: true
```
