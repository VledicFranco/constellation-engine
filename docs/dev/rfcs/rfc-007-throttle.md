# RFC-007: Throttle

**Status:** Draft
**Priority:** 3 (Rate Control)
**Author:** Agent 1
**Created:** 2026-01-25

---

## Summary

Add a `throttle` option to module calls that limits the rate at which calls are made.

---

## Motivation

External APIs often have rate limits:
- "100 requests per minute"
- "10 requests per second"
- "1000 requests per hour"

Exceeding these limits results in 429 errors and potential account suspension. The `throttle` option allows declaring rate limits at the call site, ensuring the pipeline respects API constraints without manual tracking.

---

## Syntax

```constellation
result = MyModule(input) with throttle: 100/1min
```

### Rate Format

```
throttle: <count> / <duration>
```

| Example | Meaning |
|---------|---------|
| `throttle: 10/1s` | Max 10 calls per second |
| `throttle: 100/1min` | Max 100 calls per minute |
| `throttle: 1000/1h` | Max 1000 calls per hour |

---

## Semantics

### Behavior

1. Before executing a module call, check the rate limiter
2. If under the limit, execute immediately
3. If at the limit, wait until a slot becomes available
4. Execute the module call
5. Record the call for rate tracking

### Token Bucket Algorithm

Implementation uses a token bucket:
- Bucket has capacity = rate count
- Tokens are added at rate = count/duration
- Each call consumes one token
- If no tokens available, wait for next token

### Scope

Rate limits are tracked per module name (not per call site):

```constellation
# Both calls share the same rate limit for APIModule
result1 = APIModule(input1) with throttle: 100/1min
result2 = APIModule(input2) with throttle: 100/1min
```

### Interaction with Other Options

| Option | Interaction |
|--------|-------------|
| `retry` | Each retry attempt is rate-limited |
| `timeout` | Timeout includes wait time for rate limit |
| `concurrency` | Throttle limits rate, concurrency limits parallelism |

### Difference from Concurrency

| Aspect | Throttle | Concurrency |
|--------|----------|-------------|
| What it limits | Calls per time period | Simultaneous executions |
| Example | Max 100 calls/minute | Max 5 parallel calls |
| Use case | API rate limits | Resource constraints |

---

## Implementation Notes

### Parser Changes

Add rate expression parsing:

```
RateExpr       ::= Integer '/' Duration
```

### AST Changes

```scala
case class RateLimit(count: Int, duration: FiniteDuration)

case class ModuleCallOptions(
  // ...
  throttle: Option[RateLimit] = None,  // NEW
)
```

### Runtime Changes

```scala
class TokenBucketRateLimiter(rate: RateLimit) {
  private val bucket = new AtomicLong(rate.count)
  private val refillTask = scheduleRefill(rate)

  def acquire: IO[Unit] = {
    IO.defer {
      if (bucket.decrementAndGet() >= 0) IO.unit
      else {
        bucket.incrementAndGet() // Restore
        IO.sleep(rate.duration / rate.count) >> acquire
      }
    }
  }
}

def executeWithThrottle[A](
  module: Module,
  inputs: Map[String, CValue],
  rateLimiter: TokenBucketRateLimiter
): IO[A] = {
  rateLimiter.acquire >> module.run(inputs)
}
```

### Shared Rate Limiters

Rate limiters are shared per module name across all call sites:

```scala
class RateLimiterRegistry {
  private val limiters = ConcurrentHashMap[String, TokenBucketRateLimiter]()

  def getOrCreate(moduleName: String, rate: RateLimit): TokenBucketRateLimiter = {
    limiters.computeIfAbsent(moduleName, _ => new TokenBucketRateLimiter(rate))
  }
}
```

---

## Examples

### API Rate Limit

```constellation
in queries: List<String>

# Respect API rate limit of 100 requests per minute
results = queries | map(q => SearchAPI(q) with throttle: 100/1min)

out results
```

### Multiple Rate Limits

```constellation
in data: Record

# Different APIs have different limits
enriched = EnrichAPI(data) with throttle: 10/1s
scored = ScoreAPI(enriched) with throttle: 1000/1h

out scored
```

### Throttle with Retry

```constellation
in request: Request

# Rate limit applies to retries too
response = CallAPI(request) with throttle: 100/1min, retry: 3

out response
```

---

## Alternatives Considered

### 1. Global Rate Limit Configuration

```scala
val dagConfig = DagConfig(
  rateLimits = Map("SearchAPI" -> RateLimit(100, 1.minute))
)
```

Rejected: Rate limits are best declared at call site for visibility.

### 2. Backpressure Instead of Waiting

Return an error or skip when rate limited instead of waiting.

Rejected: Waiting is more intuitive for pipelines. Can add `throttle_mode: skip` later if needed.

### 3. Burst Support

```constellation
result = MyModule(input) with throttle: 100/1min, burst: 20
```

Deferred: Adds complexity. Standard token bucket provides some burst tolerance. Can add explicit burst configuration later.

---

## Open Questions

1. How to handle rate limits across distributed instances?
2. Should throttle wait time count against `timeout`?
3. Should we support different throttle modes (wait, skip, error)?
4. How to expose rate limit metrics?

---

## References

- [RFC-008: Concurrency](./rfc-008-concurrency.md)
- [Token Bucket Algorithm - Wikipedia](https://en.wikipedia.org/wiki/Token_bucket)
