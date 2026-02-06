---
title: "Retry and Fallback"
sidebar_position: 18
description: "Resilience patterns with retry, delay, backoff, and fallback"
---

# Retry and Fallback

Handle transient failures with retry, delay, exponential backoff, timeout, and fallback options.

## Use Case

You call external services that may fail transiently (network errors, rate limits, timeouts). You need automatic retries with backoff, and a fallback value if all attempts fail.

## The Pipeline

```constellation
# retry-resilience-demo.cst

@example("process this request")
in request: String

@example("default fallback response")
in fallbackValue: String

# Retry with exponential backoff
# FlakyService fails 2/3 attempts — succeeds on 3rd retry
reliableResult = FlakyService(request) with
    retry: 3,
    delay: 100ms,
    backoff: exponential

# Timeout + retry
# TimeoutProneService is slow on first call, fast on retry
@example("timeout test request")
in timeoutRequest: String

timeoutResult = TimeoutProneService(timeoutRequest) with
    timeout: 500ms,
    retry: 2,
    delay: 50ms

# Fallback for always-failing services
@example("this will fail")
in failingRequest: String

safeResult = AlwaysFailsService(failingRequest) with
    retry: 2,
    fallback: fallbackValue

# Combined: timeout + retry + backoff + fallback
@example("https://flaky-api.example.com/data")
in apiEndpoint: String

@example("cached default data")
in defaultApiResponse: String

apiResult = SlowApiCall(apiEndpoint) with
    timeout: 2s,
    retry: 3,
    delay: 200ms,
    backoff: exponential,
    fallback: defaultApiResponse

out reliableResult
out timeoutResult
out safeResult
out apiResult
```

## Explanation

| Option | Syntax | Purpose |
|---|---|---|
| `retry` | `retry: 3` | Maximum number of retry attempts |
| `delay` | `delay: 100ms` | Wait between retries |
| `backoff` | `backoff: exponential` | `exponential` (2x) or `linear` (+delay) backoff |
| `timeout` | `timeout: 2s` | Cancel if the call takes longer |
| `fallback` | `fallback: value` | Use this value if all attempts fail |

Units: `ms` (milliseconds), `s` (seconds), `min` (minutes), `h` (hours).

### Execution flow

1. Call the module
2. If it fails or times out, wait `delay` (with backoff)
3. Retry up to `retry` times
4. If all attempts fail, use `fallback` (or propagate the error if no fallback)

## Running the Example

### Input
```json
{
  "request": "process this request",
  "fallbackValue": "default fallback response",
  "timeoutRequest": "timeout test request",
  "failingRequest": "this will fail",
  "apiEndpoint": "https://flaky-api.example.com/data",
  "defaultApiResponse": "cached default data"
}
```

### Output
```json
{
  "reliableResult": "flaky service result: process this request",
  "timeoutResult": "timeout prone result: timeout test request",
  "safeResult": "default fallback response",
  "apiResult": "api response from: https://flaky-api.example.com/data"
}
```

## Variations

### Linear backoff

```constellation
in request: String

result = FlakyService(request) with
    retry: 5,
    delay: 200ms,
    backoff: linear
```

With linear backoff and 200ms delay: attempt 1 at 0ms, attempt 2 at 200ms, attempt 3 at 400ms, attempt 4 at 600ms, attempt 5 at 800ms.

### Timeout only (no retry)

```constellation
in request: String
in default: String

result = SlowApiCall(request) with
    timeout: 1s,
    fallback: default
```

:::warning
Only retry idempotent operations. Retrying non-idempotent calls (like payments or order submissions) can cause duplicates or inconsistent state.
:::

:::tip
Combine retry with timeout. A retry without a timeout can hang indefinitely if the service is slow. Use `timeout: 2s, retry: 3` to bound total latency.
:::

:::note
Exponential backoff (2x multiplier) is preferred for external APIs because it gives recovering services time to stabilize. Linear backoff is better for internal services where you want faster recovery.
:::

## Best Practices

1. **Always set a timeout** — prevent hanging calls from blocking the pipeline
2. **Use fallback for non-critical calls** — if the call isn't essential, provide a default
3. **Exponential backoff for external APIs** — avoids overwhelming a recovering service
4. **Keep retry counts low** — 2-3 retries is usually sufficient; more retries just increase latency

## Related Examples

- [Error Handling](error-handling.md) — `on_error` strategies
- [Resilient Pipeline](resilient-pipeline.md) — all options combined
- [Caching](caching.md) — cache results to avoid re-calling
