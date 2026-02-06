---
title: "Fan-Out / Fan-In"
sidebar_position: 16
description: "Parallel service calls merged into a single result"
---

# Fan-Out / Fan-In

Call multiple independent services in parallel. When services return record types, merge the results with `+` and project fields with `[]`.

## Use Case

You need to call several services concurrently. The DAG compiler detects that independent calls have no data dependencies and schedules them in parallel — no manual `parMapN` needed.

## The Pipeline

```constellation
# fan-out-fan-in.cst

@example("user-42")
in userId: String

@example("product-99")
in productId: String

@example("https://api.example.com/prefs")
in prefsEndpoint: String

# Fan-out: three independent calls execute in parallel (no data dependencies)
profile = FlakyService(userId)
activity = SlowApiCall(productId)
prefs = SlowQuery(prefsEndpoint)

# All three outputs are computed concurrently
out profile
out activity
out prefs
```

## Explanation

| Step | Expression | Purpose |
|---|---|---|
| 1 | `FlakyService(userId)` | Calls first service |
| 2 | `SlowApiCall(productId)` | Calls second service — no dependency on step 1 |
| 3 | `SlowQuery(prefsEndpoint)` | Calls third service — no dependency on steps 1 or 2 |

Since none of the three calls depend on each other's output, the runtime executes them concurrently. The total latency is the maximum of the three, not the sum.

### Merge with record-typed modules

When your modules return record types (not primitives), you can merge and project:

```constellation
# Assuming modules that return record types:
# ProfileService returns { userName: String, email: String }
# ActivityService returns { activityScore: Int, lastLogin: String }

in userId: String

profile = ProfileService(userId)
activity = ActivityService(userId)

# Merge records (right side wins on field conflicts)
combined = profile + activity

# Project specific fields
summary = combined[userName, activityScore]

out summary
```

The `+` operator merges the fields from both records. The `[]` operator selects specific fields — the compiler verifies each field exists.

## Running the Example

### Input
```json
{
  "userId": "user-42",
  "productId": "product-99",
  "prefsEndpoint": "https://api.example.com/prefs"
}
```

### Output
```json
{
  "profile": "flaky service result: user-42",
  "activity": "api response from: product-99",
  "prefs": "query result for: https://api.example.com/prefs"
}
```

## Variations

### With resilience

```constellation
in userId: String
in fallbackProfile: String

profile = FlakyService(userId) with
    cache: 5min,
    retry: 2,
    timeout: 2s,
    fallback: fallbackProfile

activity = SlowApiCall(userId) with
    retry: 3,
    timeout: 3s

out profile
out activity
```

### Fan-out with caching

```constellation
in userId: String
in productId: String

# Both calls run in parallel, each with its own cache TTL
user = SlowQuery(userId) with cache: 5min
product = SlowApiCall(productId) with cache: 1h

out user
out product
```

:::tip
Parallelization is automatic. The DAG compiler analyzes data dependencies and runs independent calls concurrently. You never need to write `parMapN` or manage threads — just write sequential-looking code and let the compiler optimize.
:::

:::note
Error handling in parallel branches: by default, if one branch fails, sibling branches are cancelled and the error propagates. Use `fallback` or `on_error: log` on individual calls if you want partial results when some branches fail.
:::

## Best Practices

1. **No explicit parallelism needed** — independent calls run in parallel automatically
2. **Add resilience per-call** — each service can have its own retry/timeout/fallback settings
3. **Use `+` and `[]` for record merging** — when modules return record types, merge and project to build composite responses

## Related Examples

- [Type Algebra](type-algebra.md) — merge and projection operators
- [Resilient Pipeline](resilient-pipeline.md) — full resilience patterns on service calls
- [Comparison Guide](/docs/getting-started/comparison) — Constellation vs manual Scala
