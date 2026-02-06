# RFC-001: Retry

**Status:** Implemented
**Priority:** 1 (Core Resilience)
**Author:** Agent 1
**Created:** 2026-01-25

---

## Summary

Add a `retry` option to module calls that automatically retries failed executions up to N times.

---

## Motivation

ML pipelines often call external services (APIs, databases, remote models) that can fail transiently due to:
- Network timeouts
- Rate limiting (429 responses)
- Temporary service unavailability
- Connection resets

Currently, users must handle retries in their module implementations. This RFC proposes language-level retry support for cleaner, declarative error handling.

---

## Syntax

```constellation
result = MyModule(input) with retry: N
```

Where `N` is a positive integer specifying the maximum number of retry attempts (not including the initial attempt).

### Examples

```constellation
# Retry up to 3 times (4 total attempts)
embedding = GetEmbedding(text) with retry: 3

# Combined with other options
response = CallAPI(request) with retry: 3, timeout: 30s
```

---

## Semantics

### Behavior

1. Execute the module call
2. If successful, return the result
3. If failed, retry up to N times
4. If all retries exhausted, propagate the error (or use `fallback` if specified)

### What Constitutes Failure

A module call fails when:
- The module throws an exception
- The module returns an error type (if using `Either` or similar)
- A timeout is exceeded (if `timeout` is specified)

### Retry Count

- `retry: 0` - No retries, same as no `retry` option
- `retry: 1` - One retry (2 total attempts)
- `retry: 3` - Three retries (4 total attempts)

### Interaction with Other Options

| Option | Interaction |
|--------|-------------|
| `timeout` | Each attempt has its own timeout |
| `delay` | Wait between retry attempts |
| `backoff` | Modify delay between attempts |
| `fallback` | Used if all retries fail |
| `cache` | Only successful results are cached |

---

## Implementation Notes

### Parser Changes

Extend the parser to recognize the `with` clause after module calls:

```
ModuleCall     ::= Identifier '(' Arguments ')' WithClause?
WithClause     ::= 'with' OptionList
OptionList     ::= Option (',' Option)*
Option         ::= Identifier ':' OptionValue
OptionValue    ::= Integer | Duration | Expression | Identifier
```

### AST Changes

```scala
case class ModuleCallOptions(
  retry: Option[Int] = None,
  timeout: Option[Duration] = None,
  delay: Option[Duration] = None,
  backoff: Option[BackoffStrategy] = None,
  fallback: Option[Expression] = None,
  cache: Option[Duration] = None
)

case class ModuleCall(
  name: String,
  args: List[Expression],
  options: ModuleCallOptions,  // NEW
  span: Span
)
```

### Runtime Changes

Wrap module execution with retry logic:

```scala
def executeWithRetry[A](
  module: Module,
  inputs: Map[String, CValue],
  maxRetries: Int
): IO[A] = {
  def attempt(remaining: Int): IO[A] = {
    module.run(inputs).handleErrorWith { error =>
      if (remaining > 0) attempt(remaining - 1)
      else IO.raiseError(error)
    }
  }
  attempt(maxRetries)
}
```

### Error Reporting

When all retries fail, include retry information in the error:

```
ModuleExecutionError: GetEmbedding failed after 4 attempts
  Attempt 1: ConnectionTimeout after 30s
  Attempt 2: ConnectionTimeout after 30s
  Attempt 3: RateLimited (429)
  Attempt 4: RateLimited (429)
```

---

## Examples

### Basic Retry

```constellation
in text: String

# Retry embedding API up to 3 times
embedding = GetEmbedding(text) with retry: 3

out embedding
```

### Retry with Fallback

```constellation
in query: String

# Try API, fall back to cached result
result = SearchAPI(query) with retry: 2, fallback: getCachedResult(query)

out result
```

### Retry with Delay

```constellation
in data: Record

# Retry with 1 second delay between attempts
enriched = EnrichData(data) with retry: 3, delay: 1s

out enriched
```

---

## Alternatives Considered

### 1. Annotation Style

```constellation
@retry(3)
embedding = GetEmbedding(text)
```

Rejected: Separates configuration from call, less readable for multiple options.

### 2. Method Chaining

```constellation
embedding = GetEmbedding(text).retry(3).timeout(30s)
```

Rejected: Implies transformation rather than configuration; inconsistent with language style.

---

## Open Questions

1. Should there be a global default retry count configurable at the DAG level?
2. Should certain error types be non-retryable (e.g., validation errors)?
3. Should we support retry predicates (`retry: 3 when isTransient`)?

---

## References

- [RFC-002: Timeout](./rfc-002-timeout.md)
- [RFC-003: Fallback](./rfc-003-fallback.md)
- [RFC-005: Delay](./rfc-005-delay.md)
- [RFC-006: Backoff](./rfc-006-backoff.md)
