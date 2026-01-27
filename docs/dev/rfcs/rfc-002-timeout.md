# RFC-002: Timeout

**Status:** Draft
**Priority:** 1 (Core Resilience)
**Author:** Agent 1
**Created:** 2026-01-25

---

## Summary

Add a `timeout` option to module calls that sets a maximum execution time for each invocation.

---

## Motivation

Module calls may hang indefinitely due to:
- Unresponsive external services
- Network issues
- Expensive computations without bounds
- Deadlocks in underlying implementations

Currently, there's no way to bound execution time at the language level. This RFC proposes a `timeout` option that guarantees module calls complete (or fail) within a specified duration.

---

## Syntax

```constellation
result = MyModule(input) with timeout: 30s
```

### Duration Format

| Unit | Syntax | Example |
|------|--------|---------|
| Milliseconds | `ms` | `timeout: 500ms` |
| Seconds | `s` | `timeout: 30s` |
| Minutes | `min` | `timeout: 5min` |

### Examples

```constellation
# 30 second timeout
response = CallAPI(request) with timeout: 30s

# Combined with retry (each attempt has its own timeout)
response = CallAPI(request) with timeout: 30s, retry: 3

# Short timeout for cached lookups
cached = LookupCache(key) with timeout: 100ms
```

---

## Semantics

### Behavior

1. Start a timer when module execution begins
2. If the module completes before timeout, return the result
3. If the timer expires, cancel the execution and raise a `TimeoutError`
4. The `TimeoutError` can be caught by `fallback` or trigger `retry`

### Cancellation

When a timeout occurs:
- The module execution is interrupted (if possible)
- Any acquired resources should be released
- The error includes the timeout duration for debugging

### Interaction with Other Options

| Option | Interaction |
|--------|-------------|
| `retry` | Each retry attempt has its own timeout |
| `fallback` | Timeout triggers fallback evaluation |
| `delay` | Delay happens after timeout, before next retry |
| `cache` | Timeouts are not cached |

---

## Implementation Notes

### Parser Changes

Add duration parsing to the option value grammar:

```
OptionValue    ::= Integer | Duration | Expression | Identifier
Duration       ::= Integer DurationUnit
DurationUnit   ::= 'ms' | 's' | 'min'
```

### AST Changes

```scala
case class ModuleCallOptions(
  retry: Option[Int] = None,
  timeout: Option[Duration] = None,  // NEW
  // ...
)
```

### Runtime Changes

Wrap module execution with timeout logic:

```scala
def executeWithTimeout[A](
  module: Module,
  inputs: Map[String, CValue],
  timeout: FiniteDuration
): IO[A] = {
  module.run(inputs).timeout(timeout).adaptError {
    case _: TimeoutException =>
      TimeoutError(s"Module execution exceeded ${timeout.toSeconds}s")
  }
}
```

### Error Reporting

```
TimeoutError: GetEmbedding timed out after 30s
  Module: GetEmbedding
  Timeout: 30s
  Hint: Consider increasing timeout or using fallback
```

---

## Examples

### API Call with Timeout

```constellation
in query: String

# Timeout after 10 seconds
result = SearchAPI(query) with timeout: 10s

out result
```

### Timeout with Fallback

```constellation
in userId: String

# Try fast path, fall back to slow query
user = GetUserFromCache(userId) with timeout: 100ms, fallback: GetUserFromDB(userId)

out user
```

### Timeout with Retry

```constellation
in data: Record

# Each attempt has 5s to complete, retry up to 3 times
result = ProcessData(data) with timeout: 5s, retry: 3

out result
```

---

## Alternatives Considered

### 1. Global Timeout

Configure timeout at the DAG level instead of per-call.

Rejected: Different modules have different performance characteristics. Per-call configuration is more flexible.

### 2. Soft vs Hard Timeout

Distinguish between "warn but continue" and "cancel execution".

Deferred: Can be added later if needed. Start with hard timeout only.

---

## Open Questions

1. Should there be a maximum allowed timeout value?
2. How to handle modules that cannot be interrupted?
3. Should timeouts include queue/scheduling time or just execution time?

---

## References

- [RFC-001: Retry](./rfc-001-retry.md)
- [RFC-003: Fallback](./rfc-003-fallback.md)
