---
title: "Resilience Patterns"
sidebar_position: 1
description: "Comprehensive guide to retry, timeout, cache, and fallback patterns for building resilient LLM pipelines"
---

# Resilience Patterns

**Goal:** Master the four resilience primitives (retry, timeout, cache, fallback) and their combinations to build production-grade LLM pipelines.

## Overview

Resilience in Constellation is built on four declarative primitives:
1. **Retry** - Automatic retry on transient failures
2. **Timeout** - Time limits to prevent hanging operations
3. **Cache** - Result storage to avoid redundant work
4. **Fallback** - Graceful degradation when all else fails

These primitives compose cleanly and are specified via the `with` clause on module calls. No custom error handling code required.

**Why this matters for LLMs:**
- LLM APIs are inherently unreliable (rate limits, timeouts, transient errors)
- Token costs make redundant calls expensive
- User experience demands fast responses and graceful degradation
- Production systems need predictable latency and failure modes

---

## Decision Matrix

**Start here: Choose your resilience strategy based on the operation characteristics.**

| Operation Type | Recommended Pattern | Rationale |
|----------------|-------------------|-----------|
| **LLM API call** | `retry: 3, timeout: 30s, cache: 1h, fallback: simpler_model` | High latency, rate limits, expensive tokens |
| **Embedding generation** | `cache: 7d, timeout: 10s, retry: 2` | Deterministic, expensive, stable over time |
| **Vector search** | `timeout: 5s, retry: 2, fallback: []` | Fast expected, degradable to empty results |
| **Prompt validation** | `timeout: 1s, cache: 5min` | Fast, deterministic, frequently repeated |
| **Token counting** | `cache: 30min, timeout: 500ms` | Pure function, cacheable, must be fast |
| **Model metadata fetch** | `cache: 1d, timeout: 3s, retry: 2` | Static data, infrequent changes |
| **User context lookup** | `cache: 15min, timeout: 2s, fallback: default_context` | Session data, degradable |
| **Safety check** | `timeout: 5s, retry: 2, fallback: reject` | Critical, must complete, fail-safe |
| **RAG retrieval** | `timeout: 3s, cache: 10min, retry: 2, fallback: []` | Latency-sensitive, degradable |
| **Response streaming** | `timeout: 60s, retry: 0, fallback: cached_response` | Long-running, non-retriable |

### Decision Flowchart

```
Start: What kind of operation?
│
├─ Network call?
│  ├─ External API? → timeout + retry + fallback
│  └─ Internal service? → timeout + retry
│
├─ Expensive (tokens/compute)?
│  ├─ Deterministic? → cache (long TTL)
│  └─ Non-deterministic? → cache (short TTL) + retry
│
├─ Critical for correctness?
│  ├─ Must not fail? → retry + fallback (conservative default)
│  └─ Can degrade? → retry + fallback (empty/degraded)
│
└─ Latency-sensitive?
   ├─ User-facing? → timeout (short) + cache + fallback
   └─ Background? → timeout (long) + retry + cache
```

---

## Retry Patterns

### 1. Basic Retry

**Use when:** Transient failures are common (network glitches, rate limits).

```constellation
in prompt: String

response = GenerateText(prompt) with retry: 3
out response
```

**Behavior:**
- 1 initial attempt + 3 retries = 4 total attempts
- No delay between retries (immediate retry)
- Fails if all 4 attempts fail

**When to use:**
- Quick operations where immediate retry makes sense
- Internal services with fast recovery

**When to avoid:**
- Rate-limited APIs (immediate retry will hit rate limit again)
- Expensive operations (wastes resources on rapid retries)

---

### 2. Retry with Fixed Delay

**Use when:** Service needs brief recovery time between attempts.

```constellation
in prompt: String

response = GenerateText(prompt) with
    retry: 3,
    delay: 1s,
    backoff: fixed

out response
```

**Behavior:**
- Waits 1s between each retry
- Attempt timing: 0s, 1s, 2s, 3s
- Total time: 3s delay + 4 × execution time

**When to use:**
- Services with brief recovery periods
- Internal APIs with predictable failure patterns

**Best practices:**
- Use 200ms-1s delay for internal services
- Use 1s-3s delay for external APIs

---

### 3. Retry with Exponential Backoff

**Use when:** Service needs increasing recovery time (rate limits, overload).

```constellation
in prompt: String

response = GenerateText(prompt) with
    retry: 5,
    delay: 1s,
    backoff: exponential

out response
```

**Behavior:**
- Delay doubles each retry: 1s, 2s, 4s, 8s, 16s
- Capped at 30s per attempt
- Attempt timing: 0s, 1s (wait) → 1s, 2s (wait) → 3s, 4s (wait) → 7s, 8s (wait) → 15s

**Why exponential:**
- Gives recovering services progressively more time
- Avoids overwhelming a rate-limited API
- Standard practice for external APIs (AWS, OpenAI, Anthropic)

**When to use:**
- Rate-limited LLM APIs
- Cloud services with backoff recommendations
- Any external API with transient overload

**Configuration guide:**

| Initial Delay | Max Retries | Total Wait Time | Use Case |
|---------------|-------------|-----------------|----------|
| 100ms | 3 | ~700ms | Fast internal services |
| 500ms | 4 | ~7.5s | Standard APIs |
| 1s | 5 | ~31s | Rate-limited LLM APIs |
| 2s | 5 | ~62s | Slow external services |

---

### 4. Retry with Linear Backoff

**Use when:** Service needs predictable, increasing recovery time.

```constellation
in prompt: String

response = GenerateText(prompt) with
    retry: 5,
    delay: 1s,
    backoff: linear

out response
```

**Behavior:**
- Delay increases by base amount: 1s, 2s, 3s, 4s, 5s
- Attempt timing: 0s, 1s → 1s, 2s → 3s, 3s → 6s, 4s → 10s

**When to use:**
- Internal services with linear recovery characteristics
- When you want more aggressive retry than exponential but not immediate

**Comparison:**

| Retry | Exponential | Linear | Fixed |
|-------|-------------|--------|-------|
| 1 → 2 | 1s | 1s | 1s |
| 2 → 3 | 2s | 2s | 1s |
| 3 → 4 | 4s | 3s | 1s |
| 4 → 5 | 8s | 4s | 1s |
| Total | 15s | 10s | 4s |

---

### 5. Retry Budget Pattern

**Use when:** You want to limit total retry time, not just attempts.

```constellation
in prompt: String

response = GenerateText(prompt) with
    retry: 10,
    delay: 1s,
    backoff: exponential,
    timeout: 5s  # Timeout per attempt

out response
```

**Effective behavior:**
- Each attempt has 5s timeout
- Exponential backoff between attempts
- Total possible time: 10 attempts × 5s = 50s + backoff delays
- But: if timeouts are consistent, you'll exhaust retries quickly

**Why this works:**
- Timeout bounds per-attempt time
- Retry count bounds total attempts
- Backoff strategy bounds retry frequency

**Total time formula:**
```
Total = (retries + 1) × timeout + sum(backoff_delays)
```

**Example calculation:**
- retry: 5, timeout: 10s, delay: 1s, backoff: exponential
- Attempts: 6 × 10s = 60s
- Backoff: 1 + 2 + 4 + 8 + 16 = 31s
- Total: ~91s maximum

---

### 6. Idempotency-Safe Retry

**Critical:** Only retry idempotent operations.

**Idempotent operations (safe to retry):**
```constellation
# Reading - safe
profile = FetchUserProfile(userId) with retry: 3

# Pure computation - safe
embeddings = GenerateEmbeddings(text) with retry: 3

# GET requests - safe
data = HttpGet(url) with retry: 3
```

**Non-idempotent operations (NEVER retry without safeguards):**
```constellation
# Writing - NOT SAFE
# Don't do this:
result = CreateUser(userData) with retry: 3  # May create duplicates!

# Payment - NOT SAFE
payment = ChargeCard(amount) with retry: 3  # May double-charge!

# Incrementing - NOT SAFE
count = IncrementCounter() with retry: 3  # May over-count!
```

**How to handle non-idempotent operations:**

1. **Use idempotency keys** (if the service supports it):
```constellation
in paymentData: Record
in idempotencyKey: String

payment = ChargeCard(paymentData, idempotencyKey) with retry: 3
```

2. **Check before retry**:
```constellation
in userData: Record

# No retry on creation
userId = CreateUser(userData)

# Retry-safe lookup
profile = FetchUserProfile(userId) with retry: 3
```

3. **Use fallback instead of retry**:
```constellation
in userData: Record

userId = CreateUser(userData) with
    timeout: 10s,
    fallback: LookupExistingUser(userData)
```

---

## Timeout Patterns

### 1. Basic Timeout

**Use when:** Operation has a known maximum reasonable duration.

```constellation
in prompt: String

response = GenerateText(prompt) with timeout: 30s
out response
```

**Behavior:**
- If execution exceeds 30s, operation is cancelled
- Raises `ModuleTimeoutException`
- No retry (unless explicitly added)

**When to use:**
- Any network operation
- Database queries
- External API calls
- File I/O operations

**When to avoid:**
- Pure computations (unless you want to bound CPU time)
- Operations with unpredictable duration (use timeout + fallback instead)

---

### 2. Timeout Per Attempt (with Retry)

**Use when:** Retry is needed, but each attempt should fail fast.

```constellation
in prompt: String

response = GenerateText(prompt) with
    timeout: 10s,
    retry: 3

out response
```

**Behavior:**
- Each of 4 attempts has a 10s timeout
- Total possible time: 4 × 10s = 40s
- If attempt 1 times out, retry immediately (or with delay if configured)

**Key insight:**
- Timeout is **per attempt**, not total
- Without timeout, a hanging first attempt blocks forever
- With timeout, hanging attempts fail fast and allow retry

**Example timeline:**
```
0s    : Start attempt 1
10s   : Attempt 1 times out
10s   : Start attempt 2
15s   : Attempt 2 succeeds
Total : 15s
```

---

### 3. Deadline Propagation Pattern

**Use when:** Multiple operations must complete within a total time budget.

```constellation
in query: String
in maxLatency: Duration

@example(30s)
in deadline: Duration

# Fast retrieval
docs = VectorSearch(query) with
    timeout: when deadline < 10s then 2s else 5s

# Main LLM call
response = GenerateWithContext(query, docs) with
    timeout: when deadline < 10s then 5s else 20s

out response
```

**Behavior:**
- Adjust timeouts based on total deadline
- Earlier operations get shorter timeouts to leave budget for later ones
- Ensures total latency stays under deadline

**Total budget allocation example:**
```
Total deadline: 30s
├─ VectorSearch: 5s (17%)
├─ GenerateWithContext: 20s (67%)
└─ Buffer: 5s (16%)
```

**Best practices:**
- Allocate 10-20% buffer for overhead
- Earlier operations get smaller share
- Critical operations get larger share

---

### 4. Adaptive Timeout Pattern

**Use when:** Timeout should adjust based on recent performance.

```constellation
in prompt: String
in recentAvgLatency: Duration

# Use 2× recent average, capped at 60s
adaptiveTimeout = Min(recentAvgLatency * 2, 60s)

response = GenerateText(prompt) with
    timeout: adaptiveTimeout,
    retry: 2

out response
```

**Why adaptive:**
- Accounts for model performance variations
- Prevents false timeouts during slow periods
- Prevents excessive waits during fast periods

**Implementation note:**
This requires tracking metrics outside the pipeline. Use this pattern when:
- You have latency monitoring
- Model performance varies significantly
- False timeouts are costly

---

### 5. Timeout with Graceful Degradation

**Use when:** Timeout is expected, and you have a degraded alternative.

```constellation
in query: String

# Try full LLM generation
fullResponse = GenerateDetailed(query) with
    timeout: 10s,
    fallback: GenerateSimple(query) with timeout: 3s

out fullResponse
```

**Behavior:**
- Try detailed response (10s timeout)
- If timeout, fall back to simple response (3s timeout)
- If simple also times out, raise error (or add another fallback)

**Latency guarantee:**
- Best case: detailed response in <10s
- Degraded case: simple response in <13s (10s + 3s)
- Worst case: error at 13s

---

## Cache Patterns

### 1. Basic Result Caching

**Use when:** Same inputs produce same outputs (pure functions).

```constellation
in text: String

embeddings = GenerateEmbeddings(text) with cache: 1h
out embeddings
```

**Behavior:**
- First call: execute module, store result
- Subsequent calls (within 1h): return cached result
- After 1h: cache expires, re-execute module

**Cache key:**
- Computed from: module name + input values
- `GenerateEmbeddings("hello")` and `GenerateEmbeddings("world")` have different keys
- `GenerateEmbeddings("hello")` always has the same key

**When to use:**
- Deterministic operations (same input → same output)
- Expensive operations (LLM calls, embeddings, expensive compute)
- Frequently repeated inputs

**When to avoid:**
- Non-deterministic operations (e.g., `GetCurrentTime`)
- Operations with side effects (writes, increments)
- Rapidly changing data

---

### 2. TTL Selection Guide

**Choose cache TTL based on data freshness requirements and change frequency.**

| Data Type | Recommended TTL | Rationale |
|-----------|----------------|-----------|
| **Embeddings** | 7d - 30d | Text embeddings are deterministic and stable |
| **Model responses** | 1h - 24h | Balance freshness vs. cost; depends on prompt stability |
| **User context** | 5min - 30min | Session data changes moderately |
| **Prompt validation** | 1h - 6h | Validation rules change infrequently |
| **Token counts** | 1h | Deterministic but rules may change |
| **Model metadata** | 1d - 7d | Model configs change rarely |
| **Safety checks** | 1h | Balance safety vs. performance |
| **RAG retrieval** | 10min - 1h | Documents change, but not constantly |

**Formula for choosing TTL:**
```
TTL = min(
  Max_acceptable_staleness,
  Typical_reuse_window,
  Cost_saving_target / Request_cost
)
```

**Example:**
- Max staleness: 6h (data updates every 6h)
- Reuse window: 2h (users repeat queries within 2h)
- Cost: $0.01/call, target $100/day savings → need 10k cached calls/day → 1h TTL sufficient

**Chosen TTL: 1h** (limited by reuse window)

---

### 3. Cache-Aside Pattern

**Use when:** Cache is optional, and you want explicit control over cache hits/misses.

```constellation
in userId: String
in useCache: Boolean

# Conditional caching
profile = when useCache
  then LoadUserProfile(userId) with cache: 15min
  else LoadUserProfile(userId)

out profile
```

**Behavior:**
- If `useCache = true`: use cache (15min TTL)
- If `useCache = false`: bypass cache, always fetch fresh

**When to use:**
- Admin operations need fresh data
- User has option to "refresh"
- Testing cache behavior

---

### 4. Write-Through Cache Pattern

**Use when:** Writes should update cache immediately.

```constellation
in userId: String
in newData: Record

# Update storage
result = UpdateUser(userId, newData)

# Invalidate cache by re-fetching with cache
fresh = LoadUserProfile(userId) with cache: 15min

out fresh
```

**Behavior:**
- Write operation (no cache)
- Re-fetch with cache (stores fresh value in cache)
- Subsequent reads get updated cached value

**Limitation in Constellation:**
- No explicit cache invalidation API
- Must re-fetch to update cache
- Alternative: use `cache_backend` with external invalidation

---

### 5. Multi-Level Cache Pattern

**Use when:** Different cache durations for different operations.

```constellation
in query: String

# Short TTL for retrieval (data changes)
docs = VectorSearch(query) with cache: 10min

# Long TTL for embeddings (deterministic)
queryEmbedding = GenerateEmbedding(query) with cache: 7d

# Medium TTL for generation (balance cost vs. freshness)
response = GenerateWithContext(query, docs) with cache: 1h

out response
```

**Rationale:**
- Embeddings are pure → cache long
- Retrieval results change → cache short
- LLM responses are expensive → cache medium

---

### 6. Distributed Cache Pattern

**Use when:** Multiple instances need to share cached results.

```constellation
in text: String

embeddings = GenerateEmbeddings(text) with
    cache: 1d,
    cache_backend: "redis"

out embeddings
```

**Behavior:**
- Results stored in Redis (or Memcached)
- All instances share the same cache
- Cache persists across restarts

**When to use:**
- Multi-instance deployments
- Horizontal scaling
- Cache needs to survive restarts

**Setup required:**
```scala
// Configure cache backend
ConstellationBuilder[IO]
  .withCacheBackend("redis", RedisCacheBackend(redisClient))
  .build
```

**See also:** [cache_backend option](../../language/options/cache-backend.md)

---

### 7. Cache Hit Rate Monitoring

**Monitor cache effectiveness to tune TTLs.**

```bash
# Get cache statistics
curl http://localhost:8080/metrics | jq .cache

# Expected output:
# {
#   "hits": 8543,
#   "misses": 1234,
#   "hitRate": 0.87,
#   "size": 456
# }
```

**Target hit rates:**
- **>80%** - Excellent (most requests cached)
- **60-80%** - Good (decent cache utilization)
- **40-60%** - Fair (consider increasing TTL)
- **<40%** - Poor (cache not effective, shorten TTL or drop caching)

**Tuning based on hit rate:**
- Low hit rate + high misses → TTL too short, increase TTL
- High hit rate + stale data → TTL too long, decrease TTL
- Low hit rate + unique inputs → caching won't help, remove cache

---

## Fallback Patterns

### 1. Static Default Fallback

**Use when:** You have a sensible default value.

```constellation
in stockSymbol: String

price = GetStockPrice(stockSymbol) with
    retry: 2,
    timeout: 5s,
    fallback: 0.0

out price
```

**Behavior:**
- Try to fetch price (with retry)
- If all attempts fail, return 0.0
- No error raised

**When to use:**
- Non-critical data with obvious defaults
- Degraded mode is acceptable
- Errors would block the pipeline unnecessarily

**When to avoid:**
- Critical data (where 0.0 would be misleading)
- Operations where failure should be visible
- Cases where caller needs to distinguish success from failure

---

### 2. Alternative Service Fallback

**Use when:** You have a backup service.

```constellation
in prompt: String

response = PrimaryLLM(prompt) with
    timeout: 10s,
    retry: 2,
    fallback: BackupLLM(prompt) with timeout: 10s

out response
```

**Behavior:**
- Try primary LLM (with retry)
- If exhausted, try backup LLM
- If backup fails, raise error (or add another fallback)

**Use cases:**
- Primary: expensive, high-quality model
- Backup: cheaper, lower-quality model
- Ensures response even if primary is down

**Example: Model cascade**
```constellation
in prompt: String

response = GPT4(prompt) with
    timeout: 30s,
    retry: 2,
    fallback: GPT35(prompt) with
        timeout: 15s,
        retry: 2,
        fallback: Claude(prompt) with timeout: 15s

out response
```

**Latency worst-case:**
- GPT-4: 2 retries × 30s = 60s
- GPT-3.5: 2 retries × 15s = 30s
- Claude: 1 attempt × 15s = 15s
- Total: ~105s maximum

**Best practice:**
- Limit cascade depth to 2-3 levels
- Use progressively shorter timeouts
- Consider total latency budget

---

### 3. Cached Fallback Pattern

**Use when:** Stale data is better than no data.

```constellation
in endpoint: String

# Try fresh fetch
data = FetchData(endpoint) with
    timeout: 5s,
    retry: 2,
    fallback: GetCachedData(endpoint)

out data
```

**Behavior:**
- Try to fetch fresh data
- If failed, return last known cached value (even if expired)
- Requires `GetCachedData` module that returns last cached value

**Implementation note:**
This requires a module that explicitly reads from cache without TTL checks.

---

### 4. Degraded Mode Fallback

**Use when:** Partial results are better than complete failure.

```constellation
in query: String

# Try full context generation
fullContext = GenerateContext(query) with
    timeout: 10s,
    retry: 2,
    fallback: { summary: "", relevance: 0.0 }

# Try document retrieval
docs = VectorSearch(query) with
    timeout: 5s,
    retry: 2,
    fallback: []

# Generate with whatever context is available
response = GenerateWithContext(query, docs, fullContext)
out response
```

**Behavior:**
- Context generation fails → use empty context
- Vector search fails → use empty docs
- Generation still proceeds with degraded inputs

**When to use:**
- Non-critical features (enrichment, metadata)
- Operations where partial data is useful
- User experience prioritizes response over completeness

---

### 5. Fallback with Logging

**Use when:** Fallback is used, but you want visibility into failures.

```constellation
in userId: String
in defaultData: Record

profile = LoadUserProfile(userId) with
    retry: 3,
    timeout: 5s,
    fallback: defaultData,
    on_error: log

out profile
```

**Behavior:**
- Try to load profile (with retry)
- If failed, return `defaultData`
- Log error before using fallback

**Why this matters:**
- Fallback masks errors from users
- Logging ensures engineers see failures
- Allows monitoring of fallback usage rate

**Monitoring:**
```bash
# Count fallback usage
grep "LoadUserProfile fallback" logs/*.log | wc -l

# If >5% of requests use fallback, investigate
```

---

### 6. Conditional Fallback

**Use when:** Fallback should vary based on context.

```constellation
in query: String
in userTier: String

response = GeneratePremium(query) with
    timeout: 30s,
    retry: 2,
    fallback: when userTier == "free"
        then GenerateBasic(query)
        else RaiseError("Premium service unavailable")

out response
```

**Behavior:**
- Free users: fallback to basic generation
- Premium users: error raised (no degraded experience)

**Use cases:**
- Tiered service levels
- A/B testing (fallback for control group only)
- Conditional degradation based on load

---

## Combined Patterns

### 1. Full Resilience Stack

**Use when:** Operation is critical, expensive, and unreliable.

```constellation
in prompt: String
in defaultResponse: String

response = GenerateText(prompt) with
    cache: 1h,
    retry: 3,
    delay: 1s,
    backoff: exponential,
    timeout: 30s,
    fallback: defaultResponse,
    on_error: log

out response
```

**Execution order:**
1. Check cache (if hit, return immediately)
2. On cache miss, execute module
3. If times out, retry (with exponential backoff)
4. Retry up to 3 times
5. If all retries fail, log error and return fallback
6. If any attempt succeeds, cache result

**Latency analysis:**
- Best case: <5ms (cache hit)
- Typical case: 10-30s (first attempt succeeds, cached)
- Degraded case: ~75s (3 retries with backoff + timeouts) → fallback

**Cost analysis:**
- Cache hit: $0 (no LLM call)
- Cache miss: $0.01/call × 1-4 attempts = $0.01-$0.04
- With 80% hit rate: $0.002-$0.008 average per request

---

### 2. Read-Through Cache with Retry

**Use when:** Cache is primary optimization, retry is backup.

```constellation
in text: String

embeddings = GenerateEmbeddings(text) with
    cache: 7d,
    timeout: 10s,
    retry: 2,
    delay: 500ms,
    backoff: exponential

out embeddings
```

**Behavior:**
- First call: execute (with retry), cache result
- Subsequent calls: return cached (no retry needed)
- Retry only happens on cache miss

**Why this ordering:**
- Cache eliminates most retry needs (80%+ hit rate)
- Retry provides resilience for the 20% cache misses
- No wasted retries on cache hits

---

### 3. Lazy Evaluation with Cache

**Use when:** Result may not be needed, but if needed, should be cached.

```constellation
in query: String
in includeMetadata: Boolean

# Only compute if needed
metadata = when includeMetadata
    then GenerateMetadata(query) with
        cache: 1h,
        lazy: true
    else {}

response = GenerateResponse(query, metadata)
out response
```

**Behavior:**
- If `includeMetadata = false`: metadata never computed
- If `includeMetadata = true`: compute once, cache for 1h
- Subsequent requests with same query: use cached metadata

**Use cases:**
- Optional expensive operations
- Conditional features based on user tier
- Operations with variable necessity

---

### 4. Circuit Breaker Pattern

**Use when:** Repeated failures should skip attempts entirely.

```constellation
in endpoint: String
in circuitOpen: Boolean

data = when circuitOpen
    then FallbackData(endpoint)
    else FetchData(endpoint) with
        retry: 2,
        timeout: 5s,
        fallback: FallbackData(endpoint)

out data
```

**Behavior:**
- If circuit open: immediately use fallback (no attempt)
- If circuit closed: try fetch (with retry/timeout)
- Circuit state managed externally

**Implementation note:**
This requires external circuit breaker logic tracking failure rates. The pattern shows how to integrate it into pipelines.

**Typical circuit breaker logic:**
```
if failure_rate > 50% in last 1min:
    circuitOpen = true
    wait 30s
    try one request (half-open)
    if success: circuitOpen = false
    if failure: circuitOpen = true, wait 30s again
```

---

### 5. Priority + Timeout + Fallback

**Use when:** Resource-constrained environment with varying criticality.

```constellation
in criticalQuery: String
in optionalQuery: String

# High priority, longer timeout, no fallback (must succeed)
critical = ProcessCritical(criticalQuery) with
    priority: high,
    timeout: 30s,
    retry: 3

# Low priority, short timeout, immediate fallback
optional = ProcessOptional(optionalQuery) with
    priority: low,
    timeout: 5s,
    retry: 1,
    fallback: {}

out critical
out optional
```

**Behavior (under load):**
- Critical requests get priority scheduling
- Critical requests get more retry budget
- Optional requests fail fast (5s timeout, 1 retry)
- Optional requests don't block critical ones

**Use cases:**
- Multi-tenant systems (paid tier = high priority)
- Background vs. real-time workloads
- Critical path vs. enrichment

---

### 6. Retry with Timeout and Dynamic Backoff

**Use when:** Backoff should adapt to failure types.

```constellation
in prompt: String
in recentFailureRate: Float

delay = when recentFailureRate > 0.5 then 5s else 1s
backoff = when recentFailureRate > 0.5 then exponential else linear

response = GenerateText(prompt) with
    retry: 3,
    delay: delay,
    backoff: backoff,
    timeout: 30s

out response
```

**Behavior:**
- High failure rate → longer delay, exponential backoff (back off aggressively)
- Low failure rate → shorter delay, linear backoff (retry faster)

**Use cases:**
- Adaptive resilience during incidents
- Multi-region deployments with varying health
- APIs with variable rate limits

---

## Anti-Patterns and Pitfalls

### Anti-Pattern 1: Retry Without Timeout

**Problem:** Hanging calls never fail, retry never kicks in.

**Bad:**
```constellation
response = SlowAPI(input) with retry: 3
# If SlowAPI hangs, retry never happens (still waiting on attempt 1)
```

**Good:**
```constellation
response = SlowAPI(input) with
    timeout: 10s,
    retry: 3
# Timeout ensures hanging calls fail, allowing retry
```

**Rule:** Always pair `retry` with `timeout`.

---

### Anti-Pattern 2: Cache Non-Deterministic Operations

**Problem:** Caching random/time-dependent results gives stale/incorrect data.

**Bad:**
```constellation
time = GetCurrentTime() with cache: 1h
# Returns same time for 1 hour!
```

**Good:**
```constellation
time = GetCurrentTime()
# No cache - always fresh
```

**Rule:** Only cache pure functions (same input → same output).

---

### Anti-Pattern 3: Aggressive Retry on Non-Transient Errors

**Problem:** Retrying validation errors, auth errors, or malformed requests wastes resources.

**Bad:**
```constellation
result = ValidateInput(input) with retry: 5
# If input is malformed, retrying won't help (will fail 5 times)
```

**Good:**
```constellation
result = ValidateInput(input) with timeout: 1s
# Validation should be fast, no retry needed
```

**Rule:** Only retry transient errors (network, rate limits, timeouts). Don't retry permanent errors (validation, auth, not-found).

**Implementation note:**
Constellation retries all errors. To avoid retrying permanent errors, ensure modules distinguish error types and raise non-retriable exceptions.

---

### Anti-Pattern 4: Long Timeout Without Fallback

**Problem:** Users wait too long for failed operations.

**Bad:**
```constellation
response = SlowAPI(input) with timeout: 120s, retry: 3
# User waits up to 8 minutes for a response!
```

**Good:**
```constellation
response = SlowAPI(input) with
    timeout: 30s,
    retry: 2,
    fallback: CachedResponse(input)
# User waits max 90s, gets cached response if failed
```

**Rule:** If timeout × retries > 60s, add fallback for graceful degradation.

---

### Anti-Pattern 5: Caching Errors

**Problem:** Errors don't get cached, but you might expect them to.

**Behavior:**
```constellation
result = FlakyAPI(input) with cache: 1h, retry: 0
# If FlakyAPI fails, error is raised (not cached)
# Next call with same input will try again (no cache)
```

**Clarification:**
- Only successful results are cached
- Errors are never cached
- This is correct behavior (caching errors would propagate failures)

**If you want error caching:**
```constellation
result = FlakyAPI(input) with
    cache: 1h,
    fallback: { error: "Service unavailable" }
# Fallback value gets cached
```

**Rule:** Understand that `cache` only caches successful results.

---

### Anti-Pattern 6: Insufficient Cache TTL

**Problem:** Cache expires too soon, defeating the purpose.

**Bad:**
```constellation
embeddings = GenerateEmbeddings(text) with cache: 5min
# Embeddings are deterministic, why only 5min cache?
```

**Good:**
```constellation
embeddings = GenerateEmbeddings(text) with cache: 7d
# Embeddings don't change, cache longer
```

**Rule:** Match TTL to data stability. Deterministic operations can cache for days.

---

### Anti-Pattern 7: Fallback Without Logging

**Problem:** Fallback masks errors, making debugging impossible.

**Bad:**
```constellation
result = ImportantAPI(input) with fallback: default
# If API is failing 100% of the time, you'll never know
```

**Good:**
```constellation
result = ImportantAPI(input) with
    fallback: default,
    on_error: log
# Errors are logged, you can monitor failure rate
```

**Rule:** Always log when using fallback, or you'll be blind to failures.

---

### Pitfall 1: Misunderstanding Timeout Scope

**Misconception:** timeout applies to total execution (including retries).

**Reality:** timeout applies per attempt.

**Example:**
```constellation
result = API(input) with timeout: 10s, retry: 3
# Total time: 4 attempts × 10s = 40s (not 10s!)
```

**Correct interpretation:**
- Each attempt has 10s timeout
- 4 total attempts (1 initial + 3 retries)
- Maximum total time: 40s + backoff delays

---

### Pitfall 2: Cache Key Collisions

**Misconception:** Module name is sufficient for cache key.

**Reality:** Cache key includes module name AND input values.

**Example:**
```constellation
# These have DIFFERENT cache keys:
emb1 = Embed("hello") with cache: 1h  # Key: hash(Embed, "hello")
emb2 = Embed("world") with cache: 1h  # Key: hash(Embed, "world")

# These have the SAME cache key:
emb3 = Embed("hello") with cache: 1h  # Key: hash(Embed, "hello")
emb4 = Embed("hello") with cache: 1h  # Key: hash(Embed, "hello") - cache hit!
```

---

### Pitfall 3: Forgetting Exponential Backoff Cap

**Misconception:** Exponential backoff grows forever.

**Reality:** Exponential backoff is capped at 30s per delay.

**Example:**
```constellation
result = API(input) with
    retry: 10,
    delay: 1s,
    backoff: exponential
```

**Actual delays:**
```
Retry 1 → 2: 1s
Retry 2 → 3: 2s
Retry 3 → 4: 4s
Retry 4 → 5: 8s
Retry 5 → 6: 16s
Retry 6 → 7: 30s (capped, would be 32s)
Retry 7 → 8: 30s (capped)
Retry 8 → 9: 30s (capped)
Retry 9 → 10: 30s (capped)
Total delay: ~151s
```

---

## Testing Resilience

### 1. Unit Testing Retry Logic

**Test that retry actually retries:**

```scala
// Mock module that fails N times then succeeds
val flakyModule = new FlakyModule(failureCount = 2)

// Execute with retry
val pipeline = """
  in input: String
  result = FlakyModule(input) with retry: 3
  out result
"""

val result = execute(pipeline, Map("input" -> "test"))
// Assert: succeeded after 3 attempts
assert(flakyModule.attemptCount == 3)
```

---

### 2. Unit Testing Timeout

**Test that timeout actually fires:**

```scala
// Mock module that hangs
val slowModule = new SlowModule(delay = 60.seconds)

val pipeline = """
  in input: String
  result = SlowModule(input) with timeout: 1s
  out result
"""

val result = execute(pipeline, Map("input" -> "test"))
// Assert: timed out after 1s
assert(result.isFailure)
assert(result.error.contains("timeout"))
```

---

### 3. Unit Testing Cache

**Test that cache hits work:**

```scala
var callCount = 0
val expensiveModule = new ExpensiveModule {
  override def execute(input: Input): Output = {
    callCount += 1
    Output(input.value)
  }
}

val pipeline = """
  in input: String
  result = ExpensiveModule(input) with cache: 1h
  out result
"""

// First call
execute(pipeline, Map("input" -> "test"))
assert(callCount == 1)

// Second call (same input)
execute(pipeline, Map("input" -> "test"))
assert(callCount == 1) // Still 1, cache hit!

// Third call (different input)
execute(pipeline, Map("input" -> "other"))
assert(callCount == 2) // Cache miss
```

---

### 4. Integration Testing Fallback

**Test that fallback is used on failure:**

```scala
val pipeline = """
  in input: String
  result = UnreliableAPI(input) with
    retry: 2,
    fallback: "default"
  out result
"""

// Simulate API down
setAPIStatus(down = true)

val result = execute(pipeline, Map("input" -> "test"))
assert(result("result") == "default")
```

---

### 5. Load Testing Resilience

**Test behavior under load:**

```bash
# Simulate high load
for i in {1..1000}; do
  curl -X POST http://localhost:8080/execute \
    -H "Content-Type: application/json" \
    -d '{"source": "...", "inputs": {...}}' &
done
wait

# Check metrics
curl http://localhost:8080/metrics | jq

# Verify:
# - Cache hit rate >80%
# - Retry rate <10%
# - Timeout rate <5%
# - Fallback rate <5%
```

---

### 6. Chaos Testing

**Test resilience under failure conditions:**

```bash
# Simulate network failures
# (using toxiproxy or similar)

# 1. Test timeout under latency
add_latency 5s
# Verify: requests timeout correctly

# 2. Test retry under packet loss
add_packet_loss 50%
# Verify: retry succeeds after multiple attempts

# 3. Test fallback under total outage
block_traffic
# Verify: fallback is used

# 4. Test cache under intermittent failures
add_intermittent_failures 20%
# Verify: cache rate increases (reduces failures)
```

---

## Configuration Recipes

### Recipe 1: Standard LLM API Call

**Use case:** Call OpenAI, Anthropic, Cohere, etc.

```constellation
in prompt: String

response = LLMGenerate(prompt) with
    timeout: 30s,
    retry: 3,
    delay: 1s,
    backoff: exponential,
    cache: 1h,
    fallback: SimplerModel(prompt)

out response
```

**Why:**
- 30s timeout: generous for LLM calls
- 3 retries: handles transient rate limits
- Exponential backoff: respects rate limits
- 1h cache: balances cost vs. freshness
- Fallback: degrades to simpler model

---

### Recipe 2: Embedding Generation

**Use case:** Generate text embeddings.

```constellation
in text: String

embeddings = GenerateEmbeddings(text) with
    timeout: 10s,
    retry: 2,
    delay: 500ms,
    backoff: exponential,
    cache: 7d

out embeddings
```

**Why:**
- 10s timeout: embeddings are fast
- 2 retries: sufficient for transient errors
- 7d cache: embeddings are deterministic
- No fallback: embeddings are critical, errors should surface

---

### Recipe 3: Vector Search

**Use case:** Search vector database (Pinecone, Weaviate, etc.)

```constellation
in query: String

results = VectorSearch(query) with
    timeout: 5s,
    retry: 2,
    delay: 200ms,
    fallback: []

out results
```

**Why:**
- 5s timeout: vector search should be fast
- 2 retries: brief retry for transient errors
- 200ms delay: fast retry (internal service)
- Empty fallback: degradable to no results
- No cache: results change as data updates

---

### Recipe 4: Prompt Validation

**Use case:** Check prompt for safety, policy violations.

```constellation
in prompt: String

validation = ValidatePrompt(prompt) with
    timeout: 2s,
    cache: 1h

out validation
```

**Why:**
- 2s timeout: validation should be fast
- 1h cache: same prompts repeat
- No retry: validation errors are deterministic
- No fallback: validation must complete

---

### Recipe 5: RAG Pipeline

**Use case:** Retrieval-augmented generation.

```constellation
in query: String

# Fast retrieval with short cache
docs = VectorSearch(query) with
    timeout: 3s,
    retry: 2,
    cache: 10min,
    fallback: []

# Expensive generation with long cache
response = GenerateWithContext(query, docs) with
    timeout: 30s,
    retry: 3,
    delay: 1s,
    backoff: exponential,
    cache: 1h,
    fallback: GenerateWithoutContext(query)

out response
```

**Why:**
- Retrieval: fast timeout, short cache (data changes)
- Generation: long timeout, long cache (expensive)
- Fallback chain: retrieval fails → empty docs, generation fails → no-context generation

---

### Recipe 6: Multi-Model Cascade

**Use case:** Try expensive model, fall back to cheaper.

```constellation
in prompt: String

response = GPT4(prompt) with
    timeout: 60s,
    retry: 2,
    delay: 2s,
    backoff: exponential,
    cache: 2h,
    fallback: GPT35(prompt) with
        timeout: 30s,
        retry: 2,
        cache: 1h

out response
```

**Why:**
- GPT-4: long timeout (slow), long cache (expensive)
- GPT-3.5: shorter timeout (faster), shorter cache (cheaper)
- Two-level fallback: best effort quality, guaranteed response

---

## Best Practices Summary

### General Principles

1. **Always pair retry with timeout** - Retry without timeout is useless (hanging calls never fail)
2. **Only cache pure functions** - Caching non-deterministic operations gives stale data
3. **Log when using fallback** - Fallback masks errors, logging provides visibility
4. **Match TTL to data stability** - Deterministic data can cache longer
5. **Use exponential backoff for external APIs** - Respects rate limits and recovering services
6. **Test resilience under failure** - Unit test each resilience primitive
7. **Monitor cache hit rates** - Tune TTLs based on actual hit rates

---

### Resilience Checklist

Use this checklist for every external module call:

- [ ] **Timeout?** - Set timeout for any network/IO operation
- [ ] **Retry?** - Add retry if operation is idempotent and has transient failures
- [ ] **Backoff?** - Use exponential backoff for external APIs, linear for internal
- [ ] **Cache?** - Cache if deterministic and expensive
- [ ] **TTL?** - Match TTL to data freshness requirements
- [ ] **Fallback?** - Add fallback if degraded mode is acceptable
- [ ] **Logging?** - Log errors when using fallback
- [ ] **Total latency?** - Calculate worst-case latency (timeout × retries + backoff)
- [ ] **Idempotent?** - Verify operation is safe to retry
- [ ] **Monitoring?** - Add metrics for retry rate, cache hit rate, fallback rate

---

### Quick Reference Table

| Operation | Timeout | Retry | Backoff | Cache | Fallback |
|-----------|---------|-------|---------|-------|----------|
| LLM API call | 30s | 3 | exponential | 1h | simpler model |
| Embeddings | 10s | 2 | exponential | 7d | - |
| Vector search | 5s | 2 | linear | 10min | [] |
| Validation | 2s | 0 | - | 1h | - |
| Token count | 1s | 0 | - | 30min | - |
| Metadata fetch | 3s | 2 | linear | 1d | - |
| User context | 2s | 2 | linear | 15min | default |
| Safety check | 5s | 2 | linear | 1h | reject |

---

## Related Documentation

- **[Module Options Reference](../../language/module-options.md)** - Complete option syntax
- **[retry option](../../language/options/retry.md)** - Detailed retry documentation
- **[timeout option](../../language/options/timeout.md)** - Detailed timeout documentation
- **[cache option](../../language/options/cache.md)** - Detailed cache documentation
- **[fallback option](../../language/options/fallback.md)** - Detailed fallback documentation
- **[Retry and Fallback Cookbook](../../../cookbook/retry-and-fallback.md)** - Working examples
- **[Caching Cookbook](../../../cookbook/caching.md)** - Caching examples
- **[Resilient Pipeline Cookbook](../../../cookbook/resilient-pipeline.md)** - Full pipeline example

---

**Next:** [Module Development Patterns](./module-development.md)
