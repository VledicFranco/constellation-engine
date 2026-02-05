---
title: "Rate Limiting"
sidebar_position: 21
description: "Control call rates with throttle and concurrency options"
---

# Rate Limiting

Limit the rate and concurrency of module calls to respect API rate limits and protect resources.

## Use Case

You call external APIs with rate limits (e.g., 5 requests/second) or run resource-intensive tasks that should not all execute at once.

## The Pipeline

```constellation
# rate-limiting-demo.cst

@example("request-1")
in req1: String

@example("request-2")
in req2: String

@example("request-3")
in req3: String

@example("request-4")
in req4: String

@example("request-5")
in req5: String

# Throttle: limit to 5 requests per second
resp1 = RateLimitedApi(req1) with throttle: 5/1s
resp2 = RateLimitedApi(req2) with throttle: 5/1s
resp3 = RateLimitedApi(req3) with throttle: 5/1s
resp4 = RateLimitedApi(req4) with throttle: 5/1s
resp5 = RateLimitedApi(req5) with throttle: 5/1s

# Concurrency: at most 2 running at the same time
@example("task-A")
in taskA: String

@example("task-B")
in taskB: String

@example("task-C")
in taskC: String

resultA = ResourceIntensiveTask(taskA) with concurrency: 2
resultB = ResourceIntensiveTask(taskB) with concurrency: 2
resultC = ResourceIntensiveTask(taskC) with concurrency: 2

# Combine throttle and concurrency
@example("heavy-task")
in heavyTask: String

heavyResult = ResourceIntensiveTask(heavyTask) with
    throttle: 10/1s,
    concurrency: 3

out resp1
out resp2
out resp3
out resp4
out resp5
out resultA
out resultB
out resultC
out heavyResult
```

## Explanation

| Option | Syntax | Purpose |
|---|---|---|
| `throttle` | `throttle: 5/1s` | Maximum N calls per time window |
| `concurrency` | `concurrency: 2` | Maximum N calls running simultaneously |

### Throttle vs Concurrency

| Aspect | Throttle | Concurrency |
|---|---|---|
| Controls | Rate (calls per time period) | Parallelism (simultaneous calls) |
| Use case | External API rate limits | CPU/memory-bounded tasks |
| Scope | Per module name | Per module name |

Throttle limits are shared across all calls to the same module. If you have 5 calls to `RateLimitedApi` with `throttle: 5/1s`, at most 5 execute per second regardless of how many are queued.

## Running the Example

### Input
```json
{
  "req1": "request-1",
  "req2": "request-2",
  "req3": "request-3",
  "req4": "request-4",
  "req5": "request-5",
  "taskA": "task-A",
  "taskB": "task-B",
  "taskC": "task-C",
  "heavyTask": "heavy-task"
}
```

## Variations

### Rate-limited with retry

```constellation
in request: String
in default: String

result = ExternalApi(request) with
    throttle: 10/1s,
    retry: 3,
    delay: 200ms,
    fallback: default
```

## Best Practices

1. **Match throttle to the API's rate limit** — check the external service's documentation for its limits
2. **Use concurrency for CPU-bound work** — prevent resource exhaustion from too many parallel computations
3. **Combine both for heavy API calls** — `throttle` for rate, `concurrency` for parallel connections
4. **Same settings per module** — all calls to the same module should use the same throttle/concurrency values for consistent behavior

## Related Examples

- [Retry and Fallback](retry-and-fallback.md) — combining rate limiting with retry
- [Priority and Lazy](priority-and-lazy.md) — execution ordering
- [Resilient Pipeline](resilient-pipeline.md) — all options combined
