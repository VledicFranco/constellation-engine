---
title: "Resilient Pipeline"
sidebar_position: 23
description: "A realistic pipeline combining all module call options"
---

# Resilient Pipeline

A realistic data processing pipeline that combines caching, retry, timeout, fallback, priority, lazy evaluation, rate limiting, and error handling.

## Use Case

You're building a product recommendation service that fetches user data from a flaky API, product data from a slow API, runs an expensive computation, calls a rate-limited enrichment service, and performs lazy deep analysis.

## The Pipeline

```constellation
# resilient-pipeline-demo.cst

@example("user-123")
in userId: String

@example("product-456")
in productId: String

@example("default-user-data")
in defaultUserData: String

@example("default-product-data")
in defaultProductData: String

# Step 1: Fetch user data (flaky API)
# Cache 5min + retry 3x + exponential backoff + timeout 3s + fallback
userData = FlakyService(userId) with
    cache: 5min,
    retry: 3,
    delay: 100ms,
    backoff: exponential,
    timeout: 3s,
    fallback: defaultUserData,
    priority: high

# Step 2: Fetch product data (slow API)
# Cache 1min + retry 2x + linear backoff + timeout 2s + fallback
productData = SlowApiCall(productId) with
    cache: 1min,
    retry: 2,
    delay: 50ms,
    backoff: linear,
    timeout: 2s,
    fallback: defaultProductData,
    priority: high

# Step 3: Expensive computation (CPU-bound)
# Cache 10min + lazy + concurrency limit
recommendation = ExpensiveCompute(userData) with
    cache: 10min,
    lazy: true,
    priority: normal,
    concurrency: 2

# Step 4: Rate-limited enrichment (external API)
# Throttle + concurrency + timeout + log errors
@example("https://enrichment-api.example.com")
in enrichmentEndpoint: String

enrichedData = RateLimitedApi(enrichmentEndpoint) with
    throttle: 10/1s,
    concurrency: 3,
    timeout: 5s,
    on_error: log,
    priority: low

# Step 5: Deep analysis (very expensive, deferred)
# Lazy + long cache + background priority
deepInsights = DeepAnalysis(productData) with
    lazy: true,
    cache: 1h,
    priority: background,
    timeout: 10s

out userData
out productData
out recommendation
out enrichedData
out deepInsights
```

## Explanation

| Step | Pattern | Rationale |
|---|---|---|
| User data | `cache + retry + backoff + fallback` | Flaky service — retry with backoff, cache successes, fallback on total failure |
| Product data | `cache + retry + fallback` | Slow service — shorter cache (data changes), linear backoff |
| Recommendation | `cache + lazy + concurrency` | CPU-bound — defer until needed, limit parallelism, cache result |
| Enrichment | `throttle + concurrency + on_error: log` | Rate-limited — respect API limits, log failures, don't block pipeline |
| Deep analysis | `lazy + cache + background` | Expensive — defer, cache for a long time, lowest priority |

### Why these combinations work

- **Cache + retry**: Cache stores the successful result. On cache miss, retries handle transient failures
- **Lazy + cache**: Deferred execution means the computation only happens if the output is consumed. Once computed, the cache prevents re-computation
- **Throttle + concurrency**: Throttle limits rate (calls/second), concurrency limits parallelism (simultaneous calls). Together they prevent both rate limit violations and resource exhaustion
- **on_error: log**: For non-critical enrichment, logging the error and continuing is better than failing the entire pipeline

## Running the Example

### Input
```json
{
  "userId": "user-123",
  "productId": "product-456",
  "defaultUserData": "default-user-data",
  "defaultProductData": "default-product-data",
  "enrichmentEndpoint": "https://enrichment-api.example.com"
}
```

### Output
```json
{
  "userData": "flaky service result: user-123",
  "productData": "api response from: product-456",
  "recommendation": "computed: flaky service result: user-123",
  "enrichedData": "rate limited response: https://enrichment-api.example.com",
  "deepInsights": "deep analysis: api response from: product-456"
}
```

:::note
When combining multiple resilience options, they are evaluated in this order: (1) timeout wraps the call, (2) retry handles failures, (3) fallback provides a default if all retries fail, (4) on_error applies if no fallback is set, (5) cache stores successful results.
:::

:::tip
Start with minimal resilience and add options as you discover failure modes. Over-engineering with every option can mask bugs and make debugging harder. A simple `retry: 2, timeout: 3s` is often enough.
:::

## Best Practices

1. **Layer resilience by criticality** — critical data gets retry + fallback; optional enrichment gets `on_error: log`
2. **Cache at appropriate TTLs** — frequently changing data gets short TTLs; expensive computations get longer ones
3. **Use lazy for conditional execution** — don't compute what you might not need
4. **Set priorities based on latency impact** — the user is waiting for high-priority data; background tasks can wait

## Related Examples

- [Retry and Fallback](retry-and-fallback.md) — retry patterns in detail
- [Caching](caching.md) — cache configuration
- [Rate Limiting](rate-limiting.md) — throttle and concurrency
- [Priority and Lazy](priority-and-lazy.md) — execution control
- [Error Handling](error-handling.md) — on_error strategies
