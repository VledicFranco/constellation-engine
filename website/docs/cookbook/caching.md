---
title: "Caching"
sidebar_position: 19
description: "Cache module results with configurable TTL and backends"
---

# Caching

Cache module call results to avoid redundant computation or API calls.

## Use Case

You have slow database queries or expensive API calls that return the same results for the same inputs. Caching avoids repeated work.

## The Pipeline

```constellation
# cache-demo.cst

@example("SELECT * FROM users")
in query1: String

@example("SELECT * FROM orders")
in query2: String

# First call - slow (500ms), result cached for 5 minutes
result1 = SlowQuery(query1) with cache: 5min

# Second call with same query - instant (cache hit)
result2 = SlowQuery(query1) with cache: 5min

# Different query - slow (different cache key)
result3 = SlowQuery(query2) with cache: 5min

# Longer TTL for expensive computations
@example("transform this data")
in data: String

computed = ExpensiveCompute(data) with cache: 10min

# Distributed cache backend for multi-instance deployments
@example("https://api.example.com/slow")
in endpoint: String

apiResult = SlowApiCall(endpoint) with cache: 1h, cache_backend: "redis"

out result1
out result2
out result3
out computed
out apiResult
```

## Explanation

| Option | Syntax | Purpose |
|---|---|---|
| `cache` | `cache: 5min` | Cache the result for the specified TTL |
| `cache_backend` | `cache_backend: "redis"` | Use a distributed cache backend |

### Cache keys

The cache key is derived from: **module name + input values**. Two calls with the same module and same input produce the same cache key, regardless of where they appear in the pipeline.

### TTL units

| Unit | Example |
|---|---|
| Seconds | `cache: 30s` |
| Minutes | `cache: 5min` |
| Hours | `cache: 1h` |

## Running the Example

### Input
```json
{
  "query1": "SELECT * FROM users",
  "query2": "SELECT * FROM orders",
  "data": "transform this data",
  "endpoint": "https://api.example.com/slow"
}
```

### Output
```json
{
  "result1": "query result for: SELECT * FROM users",
  "result2": "query result for: SELECT * FROM users",
  "result3": "query result for: SELECT * FROM orders",
  "computed": "computed: transform this data",
  "apiResult": "api response from: https://api.example.com/slow"
}
```

### Performance

| Call | Without cache | With cache |
|---|---|---|
| `result1` (first call) | 500ms | 500ms (miss) |
| `result2` (same query) | 500ms | <5ms (hit) |
| `computed` (first call) | 2000ms | 2000ms (miss) |
| `computed` (subsequent) | 2000ms | <5ms (hit) |

## Variations

### Cache with resilience

```constellation
in request: String
in default: String

result = FlakyService(request) with
    cache: 5min,
    retry: 3,
    fallback: default
```

The cache stores the successful result. Retries and fallback only apply on cache miss.

:::tip
Keep cache TTL under 24 hours to avoid serving stale data. For critical data, prefer shorter TTLs (minutes) over longer ones (hours).
:::

:::warning
Cache keys are derived from module name + input values. If your inputs contain timestamps or random IDs, every call will be a cache miss. Normalize inputs before caching.
:::

:::note
The default in-memory cache is per-instance. For multi-instance deployments, use `cache_backend: "redis"` to share cache across instances.
:::

## Best Practices

1. **Choose TTL based on data freshness** — user sessions: 30s; API responses: 5min; reference data: 1h
2. **Use `cache_backend: "redis"` for multi-instance** — the default in-memory cache is per-instance
3. **Cache after retry** — `cache + retry` caches the successful result after retries succeed
4. **Monitor hit rate** — check `GET /metrics` for cache hit rate (should be >80% for steady workloads)

## Related Examples

- [Caching Strategies](caching-strategies.md) — short, medium, and long TTL patterns
- [Retry and Fallback](retry-and-fallback.md) — combining cache with retry
- [Resilient Pipeline](resilient-pipeline.md) — cache in a full resilience setup
