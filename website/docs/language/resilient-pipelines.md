---
title: "Resilient Pipelines"
sidebar_position: 13
---

# Resilient Pipelines Guide

This guide demonstrates real-world patterns for building fault-tolerant data pipelines using module call options.

## Overview

Resilient pipelines combine multiple options to handle failures gracefully:

- **Retry** for transient failures
- **Timeout** to prevent hanging
- **Fallback** for graceful degradation
- **Cache** for performance and availability
- **Rate control** to respect limits

## Pattern 1: External API Integration

When calling external APIs, handle network issues, rate limits, and service unavailability.

```constellation
in request: ApiRequest

# Resilient API call with full protection
response = ExternalApi(request) with
    retry: 3,
    delay: 1s,
    backoff: exponential,
    timeout: 30s,
    throttle: 100/1min,
    fallback: { success: false, error: "Service unavailable" }

out response
```

### What This Does

1. **Throttle**: Limits to 100 calls/minute (respects API rate limits)
2. **Timeout**: Each attempt limited to 30 seconds
3. **Retry**: Up to 3 retries on failure
4. **Backoff**: Wait 1s, 2s, 4s between retries
5. **Fallback**: Return error object if all retries fail

## Pattern 2: Cached Data Loading

For expensive data that doesn't change frequently, use caching with fallback.

```constellation
in userId: String

# Load with cache and fallback to stale data
userData = LoadUserData(userId) with
    cache: 15min,
    retry: 2,
    timeout: 5s,
    fallback: GetStaleData(userId)

out userData
```

### What This Does

1. **Cache**: Return cached value if available (within 15 min)
2. **Timeout**: Fresh load limited to 5 seconds
3. **Retry**: 2 retry attempts if load fails
4. **Fallback**: Use potentially stale data if all else fails

## Pattern 3: Multi-Stage Pipeline

Break complex pipelines into stages with different resilience needs.

```constellation
in rawData: Record

# Stage 1: Validation (fast, no retry)
validated = Validate(rawData) with
    timeout: 1s,
    on_error: wrap

# Stage 2: Enrichment (external call, needs retry)
enriched = when validated.isRight
    then Enrich(validated.right) with
        retry: 3,
        delay: 500ms,
        timeout: 10s
    else { data: rawData, enriched: false }

# Stage 3: Storage (critical, max retry)
stored = Store(enriched) with
    retry: 5,
    delay: 1s,
    backoff: exponential,
    timeout: 30s,
    priority: high

out stored
```

### What This Does

- **Validation**: Fast fail, wrap errors for handling
- **Enrichment**: Moderate retry for external service
- **Storage**: Maximum effort for critical data persistence

## Pattern 4: Parallel Processing with Limits

Process items in parallel with resource protection.

```constellation
in items: List[DataItem]

# Process with concurrency and rate limiting
results = items.map(item =>
    ProcessItem(item) with
        concurrency: 10,
        throttle: 50/1s,
        retry: 2,
        timeout: 5s,
        on_error: skip
)

# Filter successful results
successful = results.filter(r => r.success)

out successful
```

### What This Does

1. **Concurrency**: Max 10 parallel executions
2. **Throttle**: Max 50 items per second
3. **Retry**: 2 retries per item
4. **On_error**: Skip failed items, continue processing

## Pattern 5: Priority-Based Processing

Handle different priorities of work appropriately.

```constellation
in event: Event

# Route based on priority
result = when event.priority == "critical" then
    ProcessCritical(event) with
        priority: critical,
        retry: 5,
        timeout: 60s
else when event.priority == "high" then
    ProcessHigh(event) with
        priority: high,
        retry: 3,
        timeout: 30s
else
    ProcessNormal(event) with
        priority: normal,
        retry: 2,
        timeout: 15s,
        on_error: log

out result
```

### What This Does

- **Critical**: Maximum retry, longer timeout, highest scheduling priority
- **High**: Moderate settings, elevated priority
- **Normal**: Standard settings, log failures

## Pattern 6: Lazy Evaluation for Conditional Paths

Defer expensive computations until needed.

```constellation
in request: Request

# Define but don't execute yet
fullAnalysis = DeepAnalysis(request) with lazy, cache: 1h
quickCheck = FastCheck(request) with cache: 5min

# Decide which path to take
output = when quickCheck.needsFullAnalysis
    then fullAnalysis  # Only now is DeepAnalysis executed
    else quickCheck.result

out output
```

### What This Does

1. **Lazy**: DeepAnalysis is only executed if needed
2. **Cache**: Both results are cached for reuse
3. **Efficiency**: Expensive computation avoided when possible

## Pattern 7: Circuit Breaker Pattern

Combine options to implement circuit breaker behavior.

```constellation
in request: Request

# Track failures in cache
failureCount = GetFailureCount(request.service) with cache: 1min

# Circuit breaker logic
result = when failureCount > 5 then
    # Circuit open - return fallback immediately
    { status: "circuit_open", data: cachedData }
else
    # Circuit closed - try the service
    CallService(request) with
        retry: 2,
        timeout: 5s,
        on_error: wrap

# Update failure tracking
finalResult = when result.isLeft then
    IncrementFailures(request.service) >> { status: "failed", error: result.left }
else
    ResetFailures(request.service) >> result.right

out finalResult
```

## Pattern 8: Graceful Degradation

Progressively fall back to simpler services.

```constellation
in query: SearchQuery

# Try services in order of quality
result = PremiumSearch(query) with
    timeout: 2s,
    fallback: StandardSearch(query) with
        timeout: 3s,
        fallback: BasicSearch(query) with
            timeout: 5s,
            fallback: { results: [], source: "none" }

out result
```

### What This Does

Tries premium service first, falls back through standard and basic, finally returns empty if all fail.

## Best Practices Summary

| Pattern | Key Options | Use Case |
|---------|-------------|----------|
| API Integration | retry, backoff, throttle, timeout | External services |
| Cached Loading | cache, fallback, retry | Expensive reads |
| Multi-Stage | varying options per stage | Complex pipelines |
| Parallel Processing | concurrency, throttle, on_error | Batch operations |
| Priority-Based | priority, varying retry/timeout | Mixed workloads |
| Lazy Evaluation | lazy, cache | Conditional expensive ops |
| Circuit Breaker | cache, fallback, on_error | Unstable services |
| Graceful Degradation | nested fallback | Service tiers |

## Monitoring

Monitor pipeline health with the `/metrics` endpoint:

```bash
# Check cache effectiveness
curl http://localhost:8080/metrics | jq .cache.hitRate

# Check retry rates
curl http://localhost:8080/metrics | jq .execution.retryRate

# Check throttle queue depth
curl http://localhost:8080/metrics | jq .rateControl.queueDepth
```

## See Also

- [Module Options Reference](./module-options.md) - Complete option documentation
- [Individual Option Pages](./options/retry.md) - Detailed option guides
