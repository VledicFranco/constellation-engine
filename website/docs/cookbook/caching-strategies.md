---
title: "Caching Strategies"
sidebar_position: 24
description: "Short TTL, long TTL, and distributed caching patterns"
---

# Caching Strategies

Choose the right cache TTL and backend for different data freshness requirements.

## Use Case

Your pipeline calls multiple services with different data freshness needs: user sessions change every few seconds, API responses update every few minutes, and configuration changes rarely.

## The Pipeline

```constellation
# caching-strategies.cst

@example("user-42")
in userId: String

@example("SELECT config FROM app_settings")
in configQuery: String

@example("https://exchange-rates.example.com/latest")
in ratesEndpoint: String

# Short TTL (30 seconds) - frequently changing data
userSession = QuickCheck(userId) with cache: 30s

# Medium TTL (5 minutes) - moderately changing data
userData = SlowQuery(userId) with cache: 5min

# Long TTL (1 hour) - rarely changing data
appConfig = SlowQuery(configQuery) with cache: 1h

# Distributed cache - for multi-instance deployments
exchangeRates = SlowApiCall(ratesEndpoint) with
    cache: 10min,
    cache_backend: "redis"

# Cache with resilience - retry on miss, cache on success
resilientCached = FlakyService(userId) with
    cache: 5min,
    retry: 3,
    delay: 100ms,
    backoff: exponential,
    timeout: 2s

out userSession
out userData
out appConfig
out exchangeRates
out resilientCached
```

## Explanation

### TTL Selection Guide

| TTL | Data type | Examples |
|---|---|---|
| `30s` | Frequently changing | User sessions, real-time status, live counters |
| `5min` | Moderately changing | User profiles, API responses, dashboard data |
| `1h` | Rarely changing | Configuration, reference data, computed aggregates |

### In-Memory vs Distributed

| Backend | Scope | Use case |
|---|---|---|
| Default (in-memory) | Per-instance | Single instance or data that can differ between instances |
| `cache_backend: "redis"` | Shared across instances | Multi-instance deployments where all instances should see the same cached data |

### Cache + Resilience Interaction

```
Request arrives
  → Check cache
  → HIT: return cached value (instant)
  → MISS: call module
    → Success: store in cache, return value
    → Failure: retry (if configured)
      → Retry success: store in cache, return value
      → All retries failed: use fallback (if configured)
```

## Running the Example

### Input
```json
{
  "userId": "user-42",
  "configQuery": "SELECT config FROM app_settings",
  "ratesEndpoint": "https://exchange-rates.example.com/latest"
}
```

### Output
```json
{
  "userSession": "session: user-42",
  "userData": "query result for: user-42",
  "appConfig": "query result for: SELECT config FROM app_settings",
  "exchangeRates": "api response from: https://exchange-rates.example.com/latest",
  "resilientCached": "flaky service result: user-42"
}
```

## Variations

### Write-through pattern

```constellation
in query: String
in fallbackData: String

# Cache with long TTL and fallback
# On first call: slow query, result cached
# On subsequent calls: instant cache hit
# On cache miss + failure: use fallback
result = SlowQuery(query) with
    cache: 1h,
    retry: 2,
    fallback: fallbackData
```

### Different backends per call

```constellation
in userId: String
in configKey: String

# User data in Redis (shared across instances)
user = SlowQuery(userId) with cache: 5min, cache_backend: "redis"

# Config in local memory (instance-specific, faster)
config = SlowQuery(configKey) with cache: 1h

out user
out config
```

## Best Practices

1. **Start with the shortest acceptable TTL** — you can always increase it if the data changes less often than expected
2. **Use distributed cache for data consistency** — when all instances must see the same cached values
3. **Monitor cache hit rates** — `GET /metrics` shows per-module cache statistics; aim for >80% hit rate
4. **Combine cache with fallback** — if both the cache miss and the service call fail, a fallback prevents pipeline failure
5. **Don't cache non-deterministic calls** — if a module returns different results for the same input, caching will serve stale data

## Related Examples

- [Caching](caching.md) — basic cache usage
- [Retry and Fallback](retry-and-fallback.md) — combining cache with retry
- [Resilient Pipeline](resilient-pipeline.md) — cache in a full resilience setup
