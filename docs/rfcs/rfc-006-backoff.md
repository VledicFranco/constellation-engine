# RFC-006: Backoff

**Status:** Implemented
**Priority:** 2 (Retry Configuration)
**Author:** Agent 1
**Created:** 2026-01-25

---

## Summary

Add a `backoff` option to module calls that modifies how delay increases between retry attempts.

---

## Motivation

Fixed delays between retries are often suboptimal:
- Too short: Keeps hitting rate limits or overloaded services
- Too long: Wastes time when a brief retry would succeed

Backoff strategies progressively increase delay, giving services more time to recover while not wasting time on initial retries.

---

## Syntax

```constellation
result = MyModule(input) with retry: 5, delay: 1s, backoff: exponential
```

### Backoff Strategies

| Strategy | Description | Delay Pattern (1s base) |
|----------|-------------|------------------------|
| `fixed` | No change (default) | 1s, 1s, 1s, 1s |
| `linear` | Add base delay each time | 1s, 2s, 3s, 4s |
| `exponential` | Double delay each time | 1s, 2s, 4s, 8s |

### Examples

```constellation
# Exponential backoff starting at 500ms
result = CallAPI(request) with retry: 5, delay: 500ms, backoff: exponential

# Linear backoff: 1s, 2s, 3s...
result = CallAPI(request) with retry: 5, delay: 1s, backoff: linear

# Fixed delay (explicit, same as omitting backoff)
result = CallAPI(request) with retry: 5, delay: 1s, backoff: fixed
```

---

## Semantics

### Behavior

Given `delay: D` and `backoff: strategy`:

| Attempt | Fixed | Linear | Exponential |
|---------|-------|--------|-------------|
| 1→2 | D | D | D |
| 2→3 | D | 2D | 2D |
| 3→4 | D | 3D | 4D |
| 4→5 | D | 4D | 8D |
| N→N+1 | D | N×D | 2^(N-1)×D |

### Maximum Delay Cap

To prevent unbounded delays, exponential backoff is capped:

```constellation
# Exponential with max delay of 30s
result = CallAPI(request) with retry: 10, delay: 1s, backoff: exponential
# Delays: 1s, 2s, 4s, 8s, 16s, 30s, 30s, 30s, 30s (capped at 30s)
```

Default cap: 30 seconds (configurable at runtime)

### Requires `delay`

`backoff` modifies `delay`, so it requires `delay` to be specified:

```constellation
# OK
result = MyModule(input) with retry: 3, delay: 1s, backoff: exponential

# WARNING: backoff without delay has no effect
result = MyModule(input) with retry: 3, backoff: exponential  # Compiler warning
```

### Interaction with Other Options

| Option | Interaction |
|--------|-------------|
| `retry` | Required for backoff to make sense |
| `delay` | Required. Backoff modifies this base delay |
| `timeout` | Timeout is per-attempt, unaffected by backoff |

---

## Implementation Notes

### Parser Changes

Add backoff strategy as an identifier option:

```
BackoffStrategy ::= 'fixed' | 'linear' | 'exponential'
```

### AST Changes

```scala
enum BackoffStrategy:
  case Fixed
  case Linear
  case Exponential

case class ModuleCallOptions(
  retry: Option[Int] = None,
  delay: Option[Duration] = None,
  backoff: Option[BackoffStrategy] = None,  // NEW
  // ...
)
```

### Runtime Changes

```scala
def computeDelay(
  baseDelay: FiniteDuration,
  attempt: Int,
  strategy: BackoffStrategy,
  maxDelay: FiniteDuration = 30.seconds
): FiniteDuration = {
  val computed = strategy match {
    case BackoffStrategy.Fixed => baseDelay
    case BackoffStrategy.Linear => baseDelay * attempt
    case BackoffStrategy.Exponential => baseDelay * math.pow(2, attempt - 1).toLong
  }
  computed.min(maxDelay)
}

def executeWithBackoff[A](
  module: Module,
  inputs: Map[String, CValue],
  maxRetries: Int,
  baseDelay: FiniteDuration,
  backoff: BackoffStrategy
): IO[A] = {
  def attempt(remaining: Int, attemptNum: Int): IO[A] = {
    module.run(inputs).handleErrorWith { error =>
      if (remaining > 0) {
        val delay = computeDelay(baseDelay, attemptNum, backoff)
        IO.sleep(delay) >> attempt(remaining - 1, attemptNum + 1)
      } else {
        IO.raiseError(error)
      }
    }
  }
  attempt(maxRetries, 1)
}
```

---

## Examples

### Exponential Backoff for Rate Limits

```constellation
in query: String

# Start with 100ms, double each time (100ms, 200ms, 400ms, 800ms, 1600ms)
result = RateLimitedAPI(query) with retry: 5, delay: 100ms, backoff: exponential

out result
```

### Linear Backoff for Overloaded Service

```constellation
in request: Request

# Gradually increase delay (1s, 2s, 3s, 4s, 5s)
response = BusyService(request) with retry: 5, delay: 1s, backoff: linear

out response
```

### Combined with Timeout

```constellation
in data: Record

# Each attempt has 5s timeout, exponential backoff between attempts
result = ProcessData(data) with
    timeout: 5s,
    retry: 4,
    delay: 500ms,
    backoff: exponential

out result
```

---

## Alternatives Considered

### 1. Jitter Option

```constellation
result = MyModule(input) with retry: 5, delay: 1s, backoff: exponential, jitter: true
```

Deferred: Jitter is useful for preventing thundering herd but adds complexity. Can add as enhancement later.

### 2. Custom Backoff Function

```constellation
result = MyModule(input) with retry: 5, delay: 1s, backoff: (n) => n * n * 100ms
```

Rejected: Too complex for DSL. If needed, implement as a custom module wrapper.

### 3. Backoff Multiplier

```constellation
result = MyModule(input) with retry: 5, delay: 1s, backoff_factor: 1.5
```

Deferred: More flexible but less intuitive. Standard strategies cover most cases.

---

## Open Questions

1. Should we support custom max delay per call?
2. Should jitter be built into exponential by default?
3. Should we add a `decorrelated` jitter strategy (common in AWS)?

---

## References

- [RFC-001: Retry](./rfc-001-retry.md)
- [RFC-005: Delay](./rfc-005-delay.md)
- [Exponential Backoff and Jitter - AWS Architecture Blog](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)
