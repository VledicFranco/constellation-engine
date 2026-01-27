# RFC-005: Delay

**Status:** Draft
**Priority:** 2 (Retry Configuration)
**Author:** Agent 1
**Created:** 2026-01-25

---

## Summary

Add a `delay` option to module calls that specifies a fixed wait time between retry attempts.

---

## Motivation

When retrying failed operations, immediate retries often fail for the same reason:
- Rate-limited APIs need time to reset
- Overloaded services need time to recover
- Network issues may need time to resolve

A delay between retries gives transient issues time to resolve before the next attempt.

---

## Syntax

```constellation
result = MyModule(input) with retry: 3, delay: 1s
```

### Duration Format

| Unit | Syntax | Example |
|------|--------|---------|
| Milliseconds | `ms` | `delay: 500ms` |
| Seconds | `s` | `delay: 1s` |
| Minutes | `min` | `delay: 1min` |

---

## Semantics

### Behavior

1. Execute module call
2. If failed and retries remain:
   a. Wait for the delay duration
   b. Execute next retry attempt
3. Repeat until success or retries exhausted

### Timing Diagram

```
Attempt 1 ──[fail]── delay ── Attempt 2 ──[fail]── delay ── Attempt 3
           │                              │
           └─── 1s ────┘                  └─── 1s ────┘
```

### Requires `retry`

The `delay` option only makes sense with `retry`:

```constellation
# OK: delay between retry attempts
result = MyModule(input) with retry: 3, delay: 1s

# WARNING: delay without retry has no effect
result = MyModule(input) with delay: 1s  # Compiler warning
```

### Interaction with Other Options

| Option | Interaction |
|--------|-------------|
| `retry` | Required. Delay inserted between retry attempts |
| `backoff` | Backoff modifies the delay between attempts |
| `timeout` | Timeout applies to each attempt, not including delay |

---

## Implementation Notes

### Parser Changes

Same as other duration options.

### AST Changes

```scala
case class ModuleCallOptions(
  retry: Option[Int] = None,
  timeout: Option[Duration] = None,
  delay: Option[Duration] = None,  // NEW
  // ...
)
```

### Runtime Changes

```scala
def executeWithRetryAndDelay[A](
  module: Module,
  inputs: Map[String, CValue],
  maxRetries: Int,
  delay: FiniteDuration
): IO[A] = {
  def attempt(remaining: Int): IO[A] = {
    module.run(inputs).handleErrorWith { error =>
      if (remaining > 0)
        IO.sleep(delay) >> attempt(remaining - 1)
      else
        IO.raiseError(error)
    }
  }
  attempt(maxRetries)
}
```

### Validation

Compiler should warn if `delay` is used without `retry`.

---

## Examples

### Basic Delay

```constellation
in request: Request

# Wait 2 seconds between retries
response = CallAPI(request) with retry: 3, delay: 2s

out response
```

### Delay with Timeout

```constellation
in data: Record

# Each attempt has 10s timeout, with 1s delay between
result = ProcessData(data) with timeout: 10s, retry: 3, delay: 1s

out result
```

### Rate-Limited API

```constellation
in query: String

# Respect rate limits with longer delay
result = RateLimitedAPI(query) with retry: 5, delay: 5s

out result
```

---

## Alternatives Considered

### 1. Delay as Part of Retry Syntax

```constellation
result = MyModule(input) with retry: { count: 3, delay: 1s }
```

Rejected: More complex syntax for simple case. Options can be combined at the same level.

### 2. Initial Delay Option

```constellation
result = MyModule(input) with initial_delay: 500ms, retry: 3, delay: 1s
```

Deferred: Can add later if needed. Most use cases don't need initial delay.

---

## Open Questions

1. Should there be a maximum allowed delay?
2. Should delay be skipped for the last retry attempt?
3. Should delay support jitter (small random variation)?

---

## References

- [RFC-001: Retry](./rfc-001-retry.md)
- [RFC-006: Backoff](./rfc-006-backoff.md)
