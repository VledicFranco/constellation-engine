---
title: "Module Options Reference"
sidebar_position: 2
description: "Complete reference for all module call options including retry, timeout, cache, throttle, and execution control."
---

# Module Options Reference

Complete reference for all options available in the `with` clause of module calls.

:::info Quick Navigation
- **[Quick Reference Table](#quick-reference)** - All options at a glance
- **[Resilience Options](#resilience-options)** - retry, timeout, delay, backoff, fallback
- **[Cache Options](#cache-options)** - cache, cache_backend
- **[Rate Control Options](#rate-control-options)** - throttle, concurrency
- **[Execution Control](#execution-control-options)** - lazy, priority
- **[Error Handling](#error-handling-options)** - on_error
- **[Decision Matrix](#decision-matrix)** - When to use which combinations
- **[Complete Examples](#complete-examples)** - Real-world patterns
:::

## Quick Reference

| Option | Type | Description | Default |
|--------|------|-------------|---------|
| [`retry`](#retry) | Integer | Maximum retry attempts on failure | 0 (no retry) |
| [`timeout`](#timeout) | Duration | Maximum execution time per attempt | None (no timeout) |
| [`delay`](#delay) | Duration | Base delay between retry attempts | None |
| [`backoff`](#backoff) | Strategy | How delay increases between retries | `fixed` |
| [`fallback`](#fallback) | Expression | Value to use if all retries fail | None |
| [`cache`](#cache) | Duration | TTL for caching results | None (no cache) |
| [`cache_backend`](#cache_backend) | String | Named cache backend to use | `"memory"` |
| [`throttle`](#throttle) | Rate | Maximum call rate limit | None |
| [`concurrency`](#concurrency) | Integer | Maximum parallel executions | None |
| [`on_error`](#on_error) | Strategy | Error handling behavior | `propagate` |
| [`lazy`](#lazy) | Boolean | Defer execution until needed | `false` |
| [`priority`](#priority) | Level/Integer | Scheduling priority hint | `normal` (50) |

### Value Types

**Duration:** Time value with unit suffix
- Milliseconds: `100ms`, `500ms`
- Seconds: `1s`, `30s`
- Minutes: `1min`, `5min`
- Hours: `1h`, `2h`
- Days: `1d`, `7d`

**Rate:** Count per duration in format `count/duration`
- `100/1min` - 100 calls per minute
- `10/1s` - 10 calls per second
- `1000/1h` - 1000 calls per hour

**Backoff Strategies:**
- `fixed` - Constant delay between retries
- `linear` - Delay increases linearly (N × base)
- `exponential` - Delay doubles each retry (capped at 30s)

**Error Strategies:**
- `propagate` - Re-throw the error (default)
- `skip` - Return zero value for the type
- `log` - Log error and return zero value
- `wrap` - Wrap error in Either type

**Priority Levels:**
- `critical` (100) - Highest priority
- `high` (80) - Above normal
- `normal` (50) - Default priority
- `low` (20) - Below normal
- `background` (0) - Lowest priority

## Resilience Options

Options for handling failures and ensuring reliable execution.

### retry

Automatically retry module execution on failure up to a specified number of attempts.

**Type:** Integer (non-negative)

**Syntax:**
```constellation
result = Module(args) with retry: 3
```

**Behavior:**
- Total attempts = retry + 1 (initial attempt + retries)
- `retry: 3` means 4 total attempts (1 initial + 3 retries)
- If successful on any attempt, return result immediately
- If all attempts fail, raise `RetryExhaustedException` or use fallback

**Important Notes:**
- ⚠️ Total attempts = retry + 1 (`retry: 3` = 4 attempts)
- 💡 Always pair with `timeout` to prevent hanging
- 💡 Consider `delay` with `backoff: exponential` for rate-limited APIs

**Examples:**

Basic retry:
```constellation
response = HttpGet(url) with retry: 3
```

Retry with delay:
```constellation
result = FlakyService(input) with retry: 5, delay: 1s
```

Retry with exponential backoff:
```constellation
response = ApiCall(request) with
    retry: 5,
    delay: 500ms,
    backoff: exponential
# Delays: 500ms, 1s, 2s, 4s, 8s
```

Retry with timeout:
```constellation
result = SlowOperation(data) with retry: 3, timeout: 10s
# Each attempt: 10s timeout. Total max: 40s
```

Retry with fallback:
```constellation
value = GetConfig(key) with retry: 2, fallback: "default"
```

**Error Details:**

When retries are exhausted, `RetryExhaustedException` contains:
- Module name
- Total number of attempts
- History of all errors from each attempt

Example error:
```
FlakyService failed after 4 attempts:
  Attempt 1: Connection timeout
  Attempt 2: Connection timeout
  Attempt 3: Service unavailable
  Attempt 4: Connection timeout
```

**Diagnostics:**
- Warning: More than 10 retries specified
- Error: Negative retry count

**Best Practices:**
- Use moderate retry counts (2-5) for most operations
- Always pair with `timeout` to prevent hanging
- Consider exponential backoff for rate-limited APIs
- Provide a `fallback` for graceful degradation

---

### timeout

Set a maximum execution time for a module call, cancelling the operation if it exceeds the limit.

**Type:** Duration

**Syntax:**
```constellation
result = Module(args) with timeout: 30s
```

**Behavior:**
- If module completes before timeout: return result
- If timeout expires: cancel execution and raise `ModuleTimeoutException`
- With retry: timeout applies **per attempt**, not total

**Important Notes:**
- ⚠️ Timeout is per attempt, not total (with `timeout: 10s, retry: 3`, max total = 40s)
- 💡 Always set timeouts for network calls (HTTP, database, external APIs)

**Examples:**

Basic timeout:
```constellation
response = HttpRequest(url) with timeout: 30s
```

Timeout with retry:
```constellation
result = RemoteCall(params) with timeout: 10s, retry: 3
# Each attempt: 10s timeout. Max total: 40s
```

Short timeout for fast services:
```constellation
cached = GetFromCache(key) with timeout: 100ms
```

Timeout with fallback:
```constellation
data = SlowOperation(input) with timeout: 5s, fallback: defaultData
```

**Error Details:**

`ModuleTimeoutException` contains:
- Module name
- Timeout duration
- Descriptive message

Example error:
```
Module HttpRequest timed out after 30s
```

**Best Practices:**
- Always set timeout for network operations
- Use shorter timeouts with retry for transient failures
- Consider downstream impact of long timeouts
- Set timeouts based on expected response time plus margin

---

### delay

Set the base wait time between retry attempts, optionally modified by a backoff strategy.

**Type:** Duration

**Syntax:**
```constellation
result = Module(args) with retry: N, delay: 1s
```

**Behavior:**
- First attempt executes immediately (no delay)
- After each failure, wait for computed delay before retrying
- Delay calculation depends on `backoff` strategy

**Important Notes:**
- ⚠️ `delay` requires `retry` to have any effect
- 💡 Use with `backoff: exponential` for rate-limited services

**Examples:**

Fixed delay:
```constellation
result = FlakyService(input) with retry: 3, delay: 1s
# Delays: 1s, 1s, 1s
```

Delay with backoff:
```constellation
response = ApiCall(request) with
    retry: 5,
    delay: 500ms,
    backoff: exponential
# Delays: 500ms, 1s, 2s, 4s, 8s
```

Short delay for fast retries:
```constellation
cached = GetFromCache(key) with retry: 2, delay: 50ms
```

**Delay Calculation with Backoff:**

| Strategy | Formula | Example (base: 1s) |
|----------|---------|-------------------|
| `fixed` | constant | 1s, 1s, 1s, 1s |
| `linear` | N × delay | 1s, 2s, 3s, 4s |
| `exponential` | 2^(N-1) × delay | 1s, 2s, 4s, 8s |

Note: Exponential backoff is capped at 30s maximum.

**Diagnostics:**
- Warning: `delay` without `retry` (has no effect)

**Best Practices:**
- Start with short delays (100ms-1s)
- Use exponential backoff for rate-limited services
- Consider service recovery time when setting delays
- Balance retry speed against server load

---

### backoff

Control how the delay between retry attempts changes over time using fixed, linear, or exponential strategies.

**Type:** Strategy enum (`fixed`, `linear`, `exponential`)

**Syntax:**
```constellation
result = Module(args) with retry: N, delay: D, backoff: exponential
```

**Strategies:**

**`fixed`** - Constant delay between all retries (default):
```constellation
result = Service(input) with retry: 4, delay: 1s, backoff: fixed
# Delays: 1s, 1s, 1s, 1s
```

**`linear`** - Delay increases linearly (N × base delay):
```constellation
result = Service(input) with retry: 4, delay: 1s, backoff: linear
# Delays: 1s, 2s, 3s, 4s
```

**`exponential`** - Delay doubles each attempt (2^(N-1) × base, capped at 30s):
```constellation
result = Service(input) with retry: 4, delay: 1s, backoff: exponential
# Delays: 1s, 2s, 4s, 8s
```

**Delay Table:**

Given `delay: 1s`:

| Retry # | Fixed | Linear | Exponential |
|---------|-------|--------|-------------|
| 1 → 2 | 1s | 1s | 1s |
| 2 → 3 | 1s | 2s | 2s |
| 3 → 4 | 1s | 3s | 4s |
| 4 → 5 | 1s | 4s | 8s |
| 5 → 6 | 1s | 5s | 16s |
| 6 → 7 | 1s | 6s | 30s (capped) |

**Important Notes:**
- 💡 Use `exponential` for external APIs with rate limits
- 💡 Use `linear` when you expect gradual service recovery
- 💡 Use `fixed` for internal services with predictable behavior

**Examples:**

Exponential backoff for rate limiting:
```constellation
response = RateLimitedApi(request) with
    retry: 5,
    delay: 500ms,
    backoff: exponential
# Respects rate limits: 0.5s, 1s, 2s, 4s, 8s
```

Linear backoff for gradual recovery:
```constellation
result = RecoveringService(data) with
    retry: 5,
    delay: 2s,
    backoff: linear
# Gives service time to recover: 2s, 4s, 6s, 8s, 10s
```

**Diagnostics:**
- Warning: `backoff` without `delay` (requires base delay)
- Warning: `backoff` without `retry` (has no effect)

**Best Practices:**
- Use `exponential` for external APIs with rate limits
- Use `linear` when you expect gradual service recovery
- Use `fixed` for internal services with consistent behavior
- Combine with reasonable `timeout` to avoid long waits

---

### fallback

Provide a default value to return when a module call fails instead of propagating the error.

**Type:** Expression (must match module's return type)

**Syntax:**
```constellation
result = Module(args) with fallback: defaultValue
```

**Behavior:**
- Execute module (with retries if configured)
- If successful: return result
- If all attempts fail: evaluate and return fallback expression
- Fallback is only evaluated if needed (lazy evaluation)

**Important Notes:**
- 💡 Use for graceful degradation
- 💡 Combine with `on_error: log` to ensure failures are recorded
- ⚠️ Fallback expression must match module's return type (compile-time check)

**Examples:**

Simple fallback value:
```constellation
config = GetConfig(key) with fallback: "default"
```

Fallback with retry:
```constellation
price = GetPrice(symbol) with retry: 3, fallback: 0.0
# Try 4 times, then return 0.0
```

Record fallback:
```constellation
user = LoadUser(id) with fallback: { name: "Unknown", id: 0 }
```

Fallback with another call:
```constellation
primary = PrimaryService(req) with fallback: BackupService(req)
# Fall back to secondary service on failure
```

Conditional fallback:
```constellation
in useCache: Boolean

data = FetchData(id) with fallback: when useCache then cachedData else emptyData
```

**Type Checking:**

```constellation
# Valid - fallback matches return type (Int)
count = CountItems(list) with fallback: 0

# Invalid - type mismatch (String vs Int)
count = CountItems(list) with fallback: "none"  # Compile error!
```

**Best Practices:**
- Provide meaningful default values
- Consider downstream impact of fallback values
- Use fallback for graceful degradation, not error suppression
- Log errors separately when using fallback (`on_error: log`)

---

## Cache Options

Options for result caching and storage.

### cache

Cache module results for a specified duration to avoid redundant computations.

**Type:** Duration

**Syntax:**
```constellation
result = Module(args) with cache: 15min
```

**Behavior:**
1. Generate cache key from module name and input values
2. Check cache for existing entry
3. If cached and not expired: return cached value (cache hit)
4. If no cache or expired:
   - Execute module (with retry if configured)
   - If successful, store result with TTL
   - Return result

**Cache Key Generation:**

Deterministic hash of:
- Module name
- Input parameter names and values (sorted alphabetically)

Ensures:
- Same inputs → same cache key
- Different inputs → different cache keys
- Parameter order doesn't matter

**Important Notes:**
- ⚠️ Only cache pure functions (same inputs = same outputs)
- 💡 Retry happens before caching (errors are never cached)
- 💡 Avoid caching modules with side effects or time-dependent results

**Examples:**

Basic caching:
```constellation
profile = LoadUserProfile(userId) with cache: 15min
```

Short cache for fast lookups:
```constellation
config = GetConfig(key) with cache: 30s
```

Long cache for static data:
```constellation
schema = LoadSchema(tableName) with cache: 1d
```

Cache with retry:
```constellation
data = FetchData(id) with retry: 3, cache: 10min
# Retries happen before caching. Only success is cached.
```

Cache with distributed backend:
```constellation
session = LoadSession(token) with cache: 1h, cache_backend: "memcached"
```

**Cache Statistics:**

Access via `/metrics` endpoint:
```bash
curl http://localhost:8080/metrics | jq .cache
```

Fields:
- `hits` - Number of cache hits
- `misses` - Number of cache misses
- `evictions` - Number of expired/evicted entries
- `size`/`entries` - Current cached entry count
- `hitRatio`/`hitRate` - Ratio of hits to total accesses (0.0-1.0)

**Best Practices:**
- Cache expensive or slow operations
- Use shorter TTLs for frequently changing data
- Use longer TTLs for stable reference data
- Monitor cache hit rates to tune TTL values
- Only cache pure functions
- Consider cache invalidation strategies for critical data

---

### cache_backend

Select a specific cache storage backend by name (in-memory, Memcached, Redis, etc.).

**Type:** String (quoted backend name)

**Syntax:**
```constellation
result = Module(args) with cache: 1h, cache_backend: "memcached"
```

**Available Backends:**

| Name | Source | Description | Use Case |
|------|--------|-------------|----------|
| `memory` | Built-in | In-memory with TTL + LRU eviction | Development, single instance |
| `memcached` | Optional module | Distributed Memcached via spymemcached | High-throughput distributed |
| `redis` | Custom SPI | Distributed Redis (implement yourself) | Multi-instance with rich data |
| `caffeine` | Custom SPI | High-performance local (implement yourself) | Production single instance |

**Important Notes:**
- 💡 Use distributed caching in production for multi-instance deployments
- 💡 Default `memory` backend is per-instance (causes cache misses across servers)
- ⚠️ If named backend not registered, runtime creates in-memory fallback

**Examples:**

Memcached backend:
```constellation
session = LoadSession(token) with cache: 1h, cache_backend: "memcached"
```

In-memory backend (explicit):
```constellation
config = GetConfig(key) with cache: 5min, cache_backend: "memory"
```

Redis backend:
```constellation
lookup = ExpensiveLookup(id) with cache: 30min, cache_backend: "redis"
```

**Backend Configuration:**

Backends are registered at application startup:

```scala
import io.constellation.cache.{CacheRegistry, InMemoryCacheBackend}
import io.constellation.cache.memcached.{MemcachedCacheBackend, MemcachedConfig}

MemcachedCacheBackend.resource(MemcachedConfig.single()).use { memcached =>
  for {
    registry <- CacheRegistry.withBackends(
      "memory"    -> InMemoryCacheBackend(),
      "memcached" -> memcached
    )
  } yield ()
}
```

Set global default:
```scala
ConstellationImpl.builder()
  .withCache(memcachedBackend)  // All modules use Memcached by default
  .build()
```

**Diagnostics:**
- Warning: `cache_backend` without `cache` (requires cache option)

**Best Practices:**
- Use `memory` for local, single-instance development
- Use `memcached` or Redis for distributed production
- Configure backends at startup before running pipelines
- Match backend to data consistency and latency requirements
- Use key namespacing to isolate tenants sharing a cluster

---

## Rate Control Options

Options for managing execution rate and resources.

### throttle

Limit the rate of module calls using a token bucket algorithm to respect external API rate limits.

**Type:** Rate (count per duration)

**Syntax:**
```constellation
result = Module(args) with throttle: 100/1min
```

**Behavior:**

Token bucket algorithm:
1. Each duration period, count tokens are available
2. Each call consumes one token
3. If tokens available: consume token, execute immediately
4. If no tokens: wait until token becomes available, then execute

Allows bursting up to `count` calls instantly, with sustained rate averaging to `count/duration`.

**Important Notes:**
- 💡 Token bucket allows bursting (up to `count` instant calls)
- 💡 Use `throttle` for rate limiting (calls per time), `concurrency` for parallelism
- 💡 Can be combined: `throttle: 100/1min, concurrency: 10`

**Examples:**

API rate limiting:
```constellation
response = ExternalApi(request) with throttle: 100/1min
# Max 100 calls per minute
```

Per-second limiting:
```constellation
result = FastService(data) with throttle: 10/1s
# Max 10 calls per second
```

Hourly limiting:
```constellation
report = GenerateReport(params) with throttle: 1000/1h
# Max 1000 reports per hour
```

Combined with retry:
```constellation
response = RateLimitedApi(request) with
    throttle: 50/1min,
    retry: 3,
    delay: 1s,
    backoff: exponential
```

**Rate Format:**

```
<count>/<duration>

Examples:
  100/1min   - 100 per minute
  10/1s      - 10 per second
  1000/1h    - 1000 per hour
  50/30s     - 50 per 30 seconds
```

**Per-Module Limiting:**

Rate limits are tracked per module name. Different modules have independent limits:

```constellation
# These have separate rate limits
a = ServiceA(x) with throttle: 10/1s
b = ServiceB(y) with throttle: 10/1s
```

**Best Practices:**
- Match throttle rate to external API limits (with margin)
- Use for third-party APIs with documented rate limits
- Combine with retry for handling rate limit errors
- Consider per-user or per-tenant throttling for fairness
- Monitor throttle wait times to detect capacity issues

---

### concurrency

Limit the number of parallel executions of a module to control resource usage.

**Type:** Integer (positive)

**Syntax:**
```constellation
result = Module(args) with concurrency: 5
```

**Behavior:**

Semaphore-based limiting:
1. When call is made, try to acquire permit
2. If permit available: acquire, execute, release when done
3. If no permit: wait until permit is released, then proceed

**Important Notes:**
- 💡 Use `concurrency: 1` for mutex behavior (only one call at a time)
- ⚠️ Always pair with `timeout` to prevent deadlocks
- 💡 Use `concurrency` for parallelism, `throttle` for rate limiting

**Examples:**

Limit parallel database connections:
```constellation
data = DatabaseQuery(sql) with concurrency: 5
# Max 5 concurrent queries
```

Single-threaded execution:
```constellation
result = NotThreadSafe(input) with concurrency: 1
# Only one call at a time (mutex)
```

Combined with throttle:
```constellation
response = ExternalApi(request) with
    concurrency: 10,
    throttle: 100/1min
# Max 10 concurrent calls AND max 100 per minute
```

Limit resource-intensive operations:
```constellation
processed = HeavyComputation(data) with concurrency: 2
# Limit CPU-intensive ops to 2 parallel
```

**Concurrency vs Throttle:**

| Aspect | concurrency | throttle |
|--------|-------------|----------|
| Limits | Parallel executions | Calls per time window |
| Queueing | When all slots busy | When rate exceeded |
| Use case | Resource protection | Rate limiting |

**Per-Module Limiting:**

Concurrency limits are tracked per module name:

```constellation
# These have separate concurrency limits
a = ServiceA(x) with concurrency: 5
b = ServiceB(y) with concurrency: 5
```

**Diagnostics:**
- Error: Zero concurrency (must be positive >= 1)

**Best Practices:**
- Set concurrency based on downstream service capacity
- Use `concurrency: 1` for non-thread-safe operations
- Combine with timeout to prevent deadlocks
- Monitor queue depth to detect capacity issues
- Consider total concurrency across all modules

---

## Execution Control Options

Options for fine-tuned execution control.

### lazy

Defer module execution until the result is actually needed, with automatic memoization on first access.

**Type:** Boolean (flag or explicit true/false)

**Syntax:**
```constellation
result = Module(args) with lazy
# or
result = Module(args) with lazy: true
```

**Behavior:**
1. When lazy module call encountered: create `LazyValue` wrapper (no execution)
2. When lazy value first accessed: execute computation, memoize result
3. On subsequent accesses: return memoized result (no re-execution)

**Important Notes:**
- ⚠️ Errors occur when forced, not when defined (can make debugging harder)
- 💡 Memoization guarantee: executes exactly once, even with multiple accesses
- 💡 Thread-safe: only one thread executes, others wait for result

**Examples:**

Defer expensive computation:
```constellation
in shouldProcess: Boolean
in data: Record

# Only computed if shouldProcess is true
expensive = HeavyComputation(data) with lazy

output = when shouldProcess then expensive else defaultValue
out output
```

Explicit boolean value:
```constellation
# Enable lazy evaluation
deferred = Compute(x) with lazy: true

# Disable (execute immediately - default)
immediate = Compute(x) with lazy: false
```

Multiple access (memoization):
```constellation
cached = ExpensiveLoad(id) with lazy

# First access triggers execution
first = process(cached)

# Second access uses memoized result
second = transform(cached)

out { first: first, second: second }
```

Conditional branching:
```constellation
in useNewAlgorithm: Boolean

# Both defined but only one runs
oldResult = OldAlgorithm(data) with lazy
newResult = NewAlgorithm(data) with lazy

output = when useNewAlgorithm then newResult else oldResult
out output
```

**When to Use Lazy:**

**Good use cases:**
- Expensive computations that may not be needed
- Conditional branches where only one path executes
- Breaking circular dependencies
- Deferring I/O until necessary

**Avoid when:**
- Value is always needed (no benefit)
- Timing of execution matters (unpredictable)
- Need explicit error handling at definition time

**Best Practices:**
- Use lazy for expensive operations in conditional paths
- Remember errors occur when forced, not when defined
- Combine with timeout to handle slow lazy evaluations
- Consider memoization behavior when side effects matter

---

### priority

Set the scheduling priority for module execution to control task ordering when resources are constrained.

**Type:** Priority level or Integer (0-100)

**Syntax:**
```constellation
result = Module(args) with priority: critical
# or
result = Module(args) with priority: 90
```

**Priority Levels:**

| Level | Value | Description |
|-------|-------|-------------|
| `critical` | 100 | Highest priority, minimal queuing |
| `high` | 80 | Above normal, preferred scheduling |
| `normal` | 50 | Default priority |
| `low` | 20 | Below normal, can be delayed |
| `background` | 0 | Lowest priority, runs when idle |

**Important Notes:**
- ⚠️ Priority has **no effect** unless bounded scheduler is enabled
- 💡 Default scheduler is unbounded (executes all tasks immediately)
- 💡 Enable with `CONSTELLATION_SCHEDULER_ENABLED=true`
- 💡 Starvation prevention: waiting tasks gain +10 priority every 5 seconds

**Examples:**

Named priority level:
```constellation
# Critical alert processing - highest priority
alert = ProcessAlert(event) with priority: critical

# Background cleanup - lowest priority
cleanup = RunCleanup(data) with priority: background
```

Numeric priority:
```constellation
# Custom priority value (0-100)
important = HighValue(x) with priority: 90
routine = Normal(y) with priority: 50
deferred = LowValue(z) with priority: 10
```

Combined with other options:
```constellation
response = CriticalApi(request) with
    priority: critical,
    retry: 5,
    timeout: 30s
```

Prioritized pipeline:
```constellation
in events: List[Event]

# Process events by priority
processed = events.map(e =>
    when e.severity == "critical" then
        Handle(e) with priority: critical
    else when e.severity == "high" then
        Handle(e) with priority: high
    else
        Handle(e) with priority: normal
)

out processed
```

**Scheduler Configuration:**

Enable bounded scheduler via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_SCHEDULER_ENABLED` | `false` | Enable bounded scheduler |
| `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY` | `16` | Maximum concurrent tasks |
| `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` | `30s` | Time before priority boost |

Example:
```bash
# Enable bounded scheduler with default settings
CONSTELLATION_SCHEDULER_ENABLED=true

# Enable with custom concurrency limit
CONSTELLATION_SCHEDULER_ENABLED=true CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=8
```

**Starvation Prevention:**

Aging mechanism:
- Waiting tasks receive +10 effective priority every 5 seconds
- After 30s, `background` (0) task reaches 60 effective priority
- Ensures low-priority tasks eventually execute

**Priority Ordering:**

Higher numeric values = higher priority:
```
critical (100) > high (80) > normal (50) > low (20) > background (0)
```

Tasks with same priority processed in submission order (FIFO).

**Scheduler Statistics:**

Access via `/metrics` endpoint when scheduler enabled:
```bash
curl http://localhost:8080/metrics | jq .scheduler
```

Example response:
```json
{
  "scheduler": {
    "enabled": true,
    "activeCount": 4,
    "queuedCount": 12,
    "totalSubmitted": 1523,
    "totalCompleted": 1507,
    "highPriorityCompleted": 312,
    "lowPriorityCompleted": 89,
    "starvationPromotions": 23
  }
}
```

**Best Practices:**
- Reserve `critical` for truly urgent operations
- Use `background` for non-time-sensitive tasks
- Default to `normal` unless priority is important
- Monitor queue depths to detect priority inversion
- Avoid too many `critical` tasks (defeats purpose)
- Consider business impact when assigning priorities

---

## Error Handling Options

Options for controlling error behavior.

### on_error

Control error handling strategy using propagate, skip, log, or wrap to determine failure behavior.

**Type:** Strategy enum (`propagate`, `skip`, `log`, `wrap`)

**Syntax:**
```constellation
result = Module(args) with on_error: log
```

**Strategies:**

**`propagate` (default)** - Re-throw the error to caller:
```constellation
result = RiskyOperation(data) with on_error: propagate
# Errors bubble up to calling context
```

**`skip`** - Return zero/default value for the type:
```constellation
count = CountItems(list) with on_error: skip
# Returns 0 (zero value for Int) on failure
```

Zero values by type:
- `Int` → `0`
- `Double` → `0.0`
- `String` → `""`
- `Boolean` → `false`
- `List[T]` → `[]`
- `Optional[T]` → `None`

**`log`** - Log the error and return zero value:
```constellation
data = ProcessData(input) with on_error: log
# Logs: "[ProcessData] failed: <error>. Skipping."
```

**`wrap`** - Wrap result in `Either` type for downstream handling:
```constellation
result = UncertainOperation(input) with on_error: wrap
# Returns Either[Error, Value]
```

Downstream handling:
```constellation
processed = when result.isRight
    then process(result.right)
    else handleError(result.left)
```

**Important Notes:**
- ⚠️ `skip` uses type-specific zero values (ensure downstream can handle)
- 💡 Combine `on_error: log` with `fallback` for visibility + specific value
- 💡 `wrap` enables explicit error handling in code

**Examples:**

Skip failing items in pipeline:
```constellation
in items: List[Record]

# Process each item, skipping failures
processed = items.map(item => Transform(item) with on_error: skip)

out processed
```

Log and continue:
```constellation
# Log failures but continue with default values
enriched = EnrichData(data) with on_error: log, retry: 2
```

Wrap for explicit handling:
```constellation
result = ExternalService(request) with on_error: wrap

# Handle the wrapped result
output = when result.isRight
    then result.right
    else { error: result.left.message, fallback: true }

out output
```

**on_error vs fallback:**

| Aspect | on_error | fallback |
|--------|----------|----------|
| Value | Zero/default for type | Specific expression |
| Flexibility | Fixed strategies | Any expression |
| Type | Must match return type | Must match return type |
| Use case | General handling | Specific default value |

Can be combined (fallback takes precedence for value):
```constellation
# fallback used for value, on_error: log adds logging
result = Operation(x) with on_error: log, fallback: defaultValue
```

**Best Practices:**
- Use `propagate` when errors must be handled upstream
- Use `skip` for non-critical operations in pipelines
- Use `log` when you need visibility into failures
- Use `wrap` for explicit error handling in code
- Combine with `retry` to attempt recovery first

---

## Decision Matrix

Guide for choosing option combinations based on use case.

### By Use Case

**External API Calls (rate-limited):**
```constellation
response = ExternalApi(request) with
    retry: 5,
    delay: 1s,
    backoff: exponential,
    timeout: 30s,
    throttle: 100/1min,
    fallback: emptyResponse
```
- Retry with exponential backoff for rate limit respect
- Timeout per attempt to prevent hanging
- Throttle to enforce rate limits proactively
- Fallback for graceful degradation

**Database Queries:**
```constellation
data = DatabaseQuery(sql) with
    retry: 3,
    timeout: 10s,
    concurrency: 10,
    cache: 5min
```
- Moderate retry for transient failures
- Timeout to prevent connection exhaustion
- Concurrency limit to protect database
- Cache for frequently accessed data

**Background Jobs:**
```constellation
job = ProcessJob(data) with
    priority: background,
    retry: 10,
    delay: 5s,
    backoff: linear,
    timeout: 5min,
    on_error: log
```
- Low priority (non-urgent)
- High retry count (can wait for recovery)
- Long timeout (complex processing)
- Log errors for monitoring

**Real-time Critical Operations:**
```constellation
alert = ProcessAlert(event) with
    priority: critical,
    retry: 2,
    timeout: 1s,
    fallback: defaultAlert
```
- Highest priority for immediate scheduling
- Low retry (must respond quickly)
- Short timeout (fail fast)
- Fallback for guaranteed response

**Expensive Computations:**
```constellation
result = HeavyComputation(data) with
    cache: 1h,
    cache_backend: "memcached",
    lazy,
    concurrency: 2,
    timeout: 2min
```
- Cache with distributed backend (share across instances)
- Lazy to defer until needed
- Low concurrency (resource-intensive)
- Long timeout (complex computation)

**Caching Strategies:**
```constellation
# Static reference data
schema = LoadSchema(name) with cache: 1d

# Configuration
config = GetConfig(key) with cache: 5min

# Session data
session = LoadSession(token) with
    cache: 30min,
    cache_backend: "memcached"

# Frequently changing data
price = GetStockPrice(symbol) with cache: 10s
```

### By Resilience Pattern

**Fail Fast:**
```constellation
result = Operation(data) with timeout: 100ms, on_error: propagate
```

**Fail Safe:**
```constellation
result = Operation(data) with
    retry: 3,
    timeout: 5s,
    fallback: safeDefault
```

**Retry with Backoff:**
```constellation
result = Operation(data) with
    retry: 5,
    delay: 1s,
    backoff: exponential,
    timeout: 30s
```

**Graceful Degradation:**
```constellation
primary = PrimaryService(req) with
    retry: 2,
    timeout: 5s,
    fallback: BackupService(req)
```

**Circuit Breaker Pattern:**
```constellation
# Note: Implement via custom module wrapping
result = ResilientCall(service, request) with
    retry: 3,
    timeout: 10s,
    fallback: degradedResponse,
    on_error: log
```

### Option Interactions

**Retry + Timeout:**
- Timeout applies per attempt, not total
- Total max time = (retry + 1) × timeout
- Example: `retry: 3, timeout: 10s` → max 40s total

**Retry + Delay + Backoff:**
- Delay sets base, backoff modifies per retry
- Fixed: constant delay
- Linear: N × delay
- Exponential: 2^(N-1) × delay (capped at 30s)

**Cache + Retry:**
- Retry happens before caching
- Only successful results are cached
- Failed results never cached

**Fallback + on_error:**
- Both can be specified
- Fallback takes precedence for value
- on_error can add logging: `on_error: log, fallback: value`

**Throttle + Concurrency:**
- Independent controls (can combine)
- Throttle: rate limit (calls per time)
- Concurrency: parallel limit (simultaneous calls)
- Example: `throttle: 100/1min, concurrency: 10` = max 10 parallel, 100 per minute

**Lazy + Cache:**
- Lazy defers execution
- Cache applies when lazy value is forced
- Memoization happens at lazy level (before cache)

**Priority + Concurrency:**
- Priority orders queued tasks
- Concurrency limits available slots
- High-priority tasks scheduled first when slot opens

---

## Complete Examples

Real-world patterns with full option combinations.

### Resilient API Client

```constellation
in apiEndpoint: String
in requestData: Record

# Primary API call with full resilience
response = CallApi(apiEndpoint, requestData) with
    retry: 5,
    delay: 1s,
    backoff: exponential,
    timeout: 30s,
    throttle: 100/1min,
    cache: 5min,
    cache_backend: "memcached",
    on_error: log,
    fallback: { status: "unavailable", data: null }

out response
```

Features:
- 5 retries with exponential backoff (1s, 2s, 4s, 8s, 16s)
- 30s timeout per attempt
- Rate limited to 100 calls/min
- Results cached for 5 minutes in distributed cache
- Errors logged
- Fallback to degraded response on total failure

### Data Processing Pipeline

```constellation
in records: List[Record]

# Validate in parallel (fast)
validated = records.map(r =>
    Validate(r) with timeout: 1s, on_error: skip
)

# Transform with caching (expensive)
transformed = validated.map(v =>
    Transform(v) with
        cache: 30min,
        concurrency: 5,
        timeout: 1min,
        retry: 2
)

# Store with retry (critical)
stored = transformed.map(t =>
    Store(t) with
        retry: 10,
        delay: 2s,
        backoff: linear,
        timeout: 30s,
        priority: high,
        on_error: log
)

out stored
```

Features:
- Validation: fail fast, skip invalid records
- Transform: cached, limited concurrency, moderate retry
- Store: high retry, high priority, logged errors

### Multi-Tier Caching

```constellation
in userId: String

# L1: Fast in-memory cache (30s)
l1Data = GetFromMemCache(userId) with
    cache: 30s,
    cache_backend: "memory",
    timeout: 10ms,
    fallback: getL2(userId)

# L2: Distributed cache (5min)
getL2 = lazy GetFromMemcached(userId) with
    cache: 5min,
    cache_backend: "memcached",
    timeout: 100ms,
    fallback: getL3(userId)

# L3: Database (slow, authoritative)
getL3 = lazy LoadFromDatabase(userId) with
    retry: 3,
    timeout: 5s,
    concurrency: 10

out l1Data
```

Features:
- Three-tier caching strategy
- Fast L1 memory cache (30s)
- Distributed L2 Memcached (5min)
- Authoritative L3 database with retry
- Lazy evaluation (only fetch deeper tiers on miss)
- Progressive timeouts (faster tiers fail faster)

### Prioritized Job Queue

```constellation
in jobs: List[Job]

# Process jobs by priority
results = jobs.map(job =>
    when job.priority == "urgent" then
        ProcessJob(job) with
            priority: critical,
            retry: 2,
            timeout: 30s,
            concurrency: 5,
            on_error: propagate
    else when job.priority == "high" then
        ProcessJob(job) with
            priority: high,
            retry: 3,
            timeout: 1min,
            concurrency: 10,
            on_error: log
    else
        ProcessJob(job) with
            priority: background,
            retry: 10,
            delay: 5s,
            backoff: linear,
            timeout: 5min,
            on_error: log
)

out results
```

Features:
- Urgent: critical priority, fail fast, low concurrency (protect resources)
- High: high priority, moderate retry, logged errors
- Normal: background priority, high retry, long timeout, logged errors
- Scheduler automatically orders by priority

### Lazy Conditional Execution

```constellation
in useNewFeature: Boolean
in inputData: Record

# Define both paths (only one executes)
oldPath = OldAlgorithm(inputData) with
    lazy,
    cache: 10min,
    timeout: 2min

newPath = NewAlgorithm(inputData) with
    lazy,
    retry: 3,
    timeout: 1min,
    on_error: wrap

# Select path at runtime
result = when useNewFeature then newPath else oldPath

# Handle wrapped result from new path
output = when result.isRight
    then result.right
    else { error: result.left.message, usedOld: true }

out output
```

Features:
- Both algorithms defined but only one executes (lazy)
- Old path: cached, long timeout (stable)
- New path: retry, wrapped errors (experimental)
- Conditional selection at runtime
- Explicit error handling for new path

### Rate-Limited Batch Processing

```constellation
in items: List[Item]
in batchSize: Int

# Process in batches with rate limiting
batches = items.batch(batchSize)

results = batches.map(batch =>
    ProcessBatch(batch) with
        throttle: 10/1min,
        concurrency: 1,
        retry: 5,
        delay: 2s,
        backoff: exponential,
        timeout: 5min,
        priority: low,
        on_error: log
)

out results.flatten()
```

Features:
- Rate limited to 10 batches per minute
- Serialized processing (concurrency: 1)
- Retry with exponential backoff
- Low priority (background processing)
- Logged errors for monitoring
- Flattened results

---

## Warnings and Diagnostics

The compiler provides warnings and errors for option usage.

### Warnings

| Warning | Description | Fix |
|---------|-------------|-----|
| `delay without retry` | Delay has no effect without retry | Add `retry: N` |
| `backoff without delay` | Backoff requires a base delay | Add `delay: D` |
| `backoff without retry` | Backoff has no effect without retry | Add `retry: N` |
| `cache_backend without cache` | Backend requires cache option | Add `cache: D` |
| `High retry count` | More than 10 retries specified | Review retry count |

### Errors

| Error | Description | Fix |
|-------|-------------|-----|
| Negative retry | Retry count must be >= 0 | Use non-negative value |
| Zero concurrency | Concurrency must be positive | Use value >= 1 |
| Unknown option | Unrecognized option name | Check spelling |
| Duplicate option | Same option specified twice | Remove duplicate |
| Type mismatch | Fallback type doesn't match module return type | Fix fallback type |

---

## IDE Support

### Autocomplete

After `with`, the IDE suggests available option names.

After the colon (`:`)
- **Duration options** (`timeout`, `delay`, `cache`) → suggest units: `ms`, `s`, `min`, `h`, `d`
- **Strategy options** (`backoff`) → suggest values: `fixed`, `linear`, `exponential`
- **Error strategies** (`on_error`) → suggest values: `propagate`, `skip`, `log`, `wrap`
- **Priority levels** (`priority`) → suggest values: `critical`, `high`, `normal`, `low`, `background`

### Hover Information

Hover over any option name to see:
- Description of the option
- Type signature
- Usage examples
- Related options

### Real-time Diagnostics

Warnings appear for:
- Ineffective option combinations (e.g., `delay` without `retry`)
- High retry counts (> 10)
- Invalid values
- Type mismatches

---

## See Also

- **[Language Guide](/docs/language/)** - Full constellation-lang syntax
- **[Resilient Pipelines](/docs/language/resilient-pipelines)** - Real-world resilience patterns
- **[Orchestration Algebra](/docs/language/orchestration-algebra)** - Control flow operators
- **[ModuleBuilder API](/docs/api/module-builder)** - Defining modules in Scala
- **[Performance Guide](/docs/operations/performance)** - Optimization strategies
- **[Scheduler Configuration](/docs/operations/scheduler)** - Global priority scheduler setup
