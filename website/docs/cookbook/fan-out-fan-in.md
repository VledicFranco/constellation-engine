---
title: "Fan-Out / Fan-In"
sidebar_position: 16
description: "Parallel service calls merged into a single result"
---

# Fan-Out / Fan-In

Call multiple independent services in parallel, merge their results, and project the fields you need.

## Use Case

You're building a Backend-for-Frontend (BFF) endpoint that aggregates data from a profile service, an activity service, and a preferences service. Each service returns a different record shape. You need a combined response with selected fields.

## The Pipeline

```constellation
# fan-out-fan-in.cst

@example("user-42")
in userId: String

# Fan-out: three independent calls execute in parallel (no data dependencies)
profile = ProfileService(userId)
activity = ActivityService(userId)
prefs = PrefsService(userId)

# Fan-in: merge all results into a single record
# Right side wins on field name conflicts
combined = profile + activity + prefs

# Project specific fields from the merged record
summary = combined[userName, activityScore, theme]

out combined
out summary
```

## Explanation

| Step | Expression | Purpose |
|---|---|---|
| 1 | Three `Service(userId)` calls | Fan-out — each call is independent, so they run in parallel |
| 2 | `profile + activity + prefs` | Fan-in — merge all results into one record |
| 3 | `combined[userName, activityScore, theme]` | Project — select only the fields the client needs |

The DAG compiler detects that `profile`, `activity`, and `prefs` have no data dependencies between them (they all depend only on `userId`), so it schedules them concurrently. No explicit `parMapN` or `parTupled` is needed.

## Running the Example

### Input
```json
{
  "userId": "user-42"
}
```

### Expected Output (assuming service responses)
```json
{
  "combined": {
    "userName": "Alice",
    "email": "alice@example.com",
    "activityScore": 85,
    "lastLogin": "2026-02-01",
    "theme": "dark"
  },
  "summary": {
    "userName": "Alice",
    "activityScore": 85,
    "theme": "dark"
  }
}
```

## Variations

### With resilience

```constellation
in userId: String

profile = ProfileService(userId) with
    cache: 5min,
    retry: 2,
    timeout: 2s

activity = ActivityService(userId) with
    retry: 3,
    timeout: 3s,
    fallback: { activityScore: 0 }

combined = profile + activity
summary = combined[userName, activityScore]

out summary
```

### Four-way fan-out

```constellation
in id: String

a = ServiceA(id)
b = ServiceB(id)
c = ServiceC(id)
d = ServiceD(id)

full = a + b + c + d
result = full[fieldA, fieldB, fieldC, fieldD]

out result
```

## Best Practices

1. **No explicit parallelism needed** — independent calls run in parallel automatically
2. **Add resilience per-call** — each service can have its own retry/timeout/fallback settings
3. **Project at the end** — merge everything, then select what you need rather than carefully picking fields from each source

## Related Examples

- [Type Algebra](type-algebra.md) — merge and projection operators
- [Resilient Pipeline](resilient-pipeline.md) — full resilience patterns on service calls
- [Comparison Guide](/docs/getting-started/comparison) — Constellation vs manual Scala
