---
title: "Error Handling"
sidebar_position: 20
description: "Error handling strategies with on_error: skip, log, fail"
---

# Error Handling

Control what happens when a module call fails using `on_error` strategies.

## Use Case

Different module calls in your pipeline have different criticality. Some failures should stop the pipeline; others should be logged and skipped.

## The Pipeline

```constellation
# error-handling-demo.cst

@example("will-fail-request")
in failingRequest: String

# on_error: skip - Silently returns zero/empty value on error
skipped = AlwaysFailsService(failingRequest) with on_error: skip

# on_error: log - Logs the error but continues with zero/empty value
logged = AlwaysFailsService(failingRequest) with on_error: log

# on_error: fail (default) - Propagates the error, stopping execution
# failed = AlwaysFailsService(failingRequest) with on_error: fail

# Combine on_error with retry
@example("flaky-request")
in flakyRequest: String

robustResult = FlakyService(flakyRequest) with
    retry: 3,
    delay: 100ms,
    on_error: skip

# Combine on_error with fallback
# Fallback takes precedence over on_error
@example("critical-request")
in criticalRequest: String

@example("safe-default-value")
in safeDefault: String

safeCritical = AlwaysFailsService(criticalRequest) with
    retry: 2,
    fallback: safeDefault

out skipped
out logged
out robustResult
out safeCritical
```

## Explanation

| Strategy | Behavior | When to use |
|---|---|---|
| `on_error: fail` | Propagates the error, stops pipeline execution | Default. Use for critical calls where failure means the result is invalid |
| `on_error: log` | Logs the error, continues with zero/empty value | Use for optional enrichment where you want visibility into failures |
| `on_error: skip` | Silently returns zero/empty value | Use for truly optional calls where failure is expected and uninteresting |

### Interaction with other options

| Combination | Behavior |
|---|---|
| `retry + on_error` | Retry first; if all retries fail, apply `on_error` strategy |
| `fallback + on_error` | `fallback` takes precedence — the fallback value is used on failure |
| `timeout + on_error` | Timeout counts as a failure — `on_error` applies after timeout |

## Running the Example

### Input
```json
{
  "failingRequest": "will-fail-request",
  "flakyRequest": "flaky-request",
  "criticalRequest": "critical-request",
  "safeDefault": "safe-default-value"
}
```

### Output
```json
{
  "skipped": "",
  "logged": "",
  "robustResult": "flaky service result: flaky-request",
  "safeCritical": "safe-default-value"
}
```

## Variations

### Non-critical enrichment

```constellation
in userId: String

# Core data — must succeed
userData = UserService(userId)

# Enrichment — nice to have
recommendations = RecommendationService(userId) with on_error: log
socialData = SocialService(userId) with on_error: skip

out userData
out recommendations
out socialData
```

:::tip
Choose your error strategy based on criticality: use `fallback` for critical data with a meaningful default, `on_error: log` for optional enrichment you want to monitor, and `on_error: skip` only for truly optional data where failures are expected.
:::

:::warning
In parallel branches, an error with `on_error: fail` (the default) will cancel sibling branches and propagate immediately. Use `on_error: log` or `fallback` for non-critical parallel calls to prevent one failure from stopping the entire pipeline.
:::

## Best Practices

1. **Default to `fail`** — silent failures hide bugs. Only use `skip`/`log` intentionally
2. **Prefer `fallback` over `on_error: skip`** — a meaningful default is better than a zero/empty value
3. **Use `log` during development** — switch to `skip` or `fallback` in production after you understand the failure modes
4. **Combine with retry** — retry transient failures before falling back to error handling

## Related Examples

- [Retry and Fallback](retry-and-fallback.md) — retry and fallback patterns
- [Resilient Pipeline](resilient-pipeline.md) — combining all resilience options
