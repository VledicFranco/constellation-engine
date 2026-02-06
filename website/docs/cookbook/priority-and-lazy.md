---
title: "Priority and Lazy"
sidebar_position: 22
description: "Execution control with priority levels and lazy evaluation"
---

# Priority and Lazy Evaluation

Control execution order with `priority` levels and defer computation with `lazy` evaluation.

## Use Case

Your pipeline has both fast validation checks and slow analysis tasks. You want validation to run first, and expensive analysis to be deferred until the results are actually needed.

## The Pipeline

```constellation
# priority-lazy-demo.cst

@example("simple data")
in simpleData: String

@example("complex data requiring analysis")
in complexData: String

# Priority levels: critical > high > normal > low > background
quickResult = QuickCheck(simpleData) with priority: high
expensiveResult = DeepAnalysis(simpleData) with priority: low
backgroundResult = ExpensiveCompute(simpleData) with priority: background

# Lazy: defer execution until the result is consumed
lazyComputed = ExpensiveCompute(complexData) with lazy: true

# Lazy + cache: deferred AND cached when finally executed
lazyAndCached = SlowQuery(simpleData) with lazy: true, cache: 5min

# High priority with full resilience
@example("critical-query")
in criticalQuery: String

criticalResult = SlowQuery(criticalQuery) with
    priority: critical,
    cache: 10min,
    retry: 3,
    timeout: 5s

# Background job with everything
@example("background-job")
in backgroundJob: String

@example("background-fallback")
in bgFallback: String

backgroundJob = SlowApiCall(backgroundJob) with
    priority: background,
    lazy: true,
    timeout: 30s,
    retry: 5,
    delay: 1s,
    backoff: linear,
    fallback: bgFallback,
    cache: 1h

out quickResult
out expensiveResult
out backgroundResult
out lazyComputed
out lazyAndCached
out criticalResult
out backgroundJob
```

## Explanation

### Priority Levels

| Level | Use case |
|---|---|
| `critical` | Must execute first — authentication, validation |
| `high` | Important — primary data fetching |
| `normal` | Default — standard processing |
| `low` | Can wait — enrichment, analytics |
| `background` | Lowest priority — pre-warming, batch jobs |

Priority ordering is enforced by the global scheduler (when enabled). Without the scheduler, priority serves as a hint to the runtime.

### Lazy Evaluation

| Option | Behavior |
|---|---|
| `lazy: true` | Execution is deferred until the output is actually consumed |
| (default) | Execution starts as soon as inputs are available |

Lazy evaluation is useful when a pipeline has conditional branches — if the result of an expensive computation is only needed in one branch, lazy evaluation avoids computing it when that branch isn't taken.

## Running the Example

### Input
```json
{
  "simpleData": "simple data",
  "complexData": "complex data requiring analysis",
  "criticalQuery": "critical-query",
  "backgroundJob": "background-job",
  "bgFallback": "background-fallback"
}
```

### Output
```json
{
  "quickResult": "quick check: simple data",
  "expensiveResult": "deep analysis: simple data",
  "backgroundResult": "computed: simple data",
  "lazyComputed": "computed: complex data requiring analysis",
  "lazyAndCached": "query result for: simple data",
  "criticalResult": "query result for: critical-query",
  "backgroundJob": "api response from: background-job"
}
```

## Variations

### Priority-based pipeline ordering

```constellation
in userId: String

# Validate first (critical)
auth = AuthCheck(userId) with priority: critical

# Fetch data (high)
userData = UserService(userId) with priority: high

# Enrich (low, lazy)
social = SocialService(userId) with priority: low, lazy: true

out auth
out userData
out social
```

## Best Practices

1. **Use `critical` sparingly** — only for authentication, authorization, and validation
2. **Combine `lazy` with `cache`** — if the lazy value is eventually needed, cache it to avoid recomputation
3. **Enable the global scheduler** — set `CONSTELLATION_SCHEDULER_ENABLED=true` for priority ordering to take effect
4. **Background for pre-warming** — use `priority: background` with `cache` to pre-warm cached values during idle time

## Related Examples

- [Rate Limiting](rate-limiting.md) — controlling concurrency
- [Resilient Pipeline](resilient-pipeline.md) — priority in a full resilience setup
- [Caching](caching.md) — combining lazy with cache
