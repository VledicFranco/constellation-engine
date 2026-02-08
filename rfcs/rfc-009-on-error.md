# RFC-009: On Error

**Status:** Implemented
**Priority:** 4 (Advanced Control)
**Author:** Agent 1
**Created:** 2026-01-25

---

## Summary

Add an `on_error` option to module calls that specifies an error handling strategy beyond simple fallback.

---

## Motivation

While `fallback` provides a default value on failure, sometimes more sophisticated error handling is needed:
- Log and continue with null/empty
- Skip the failed item in a collection
- Propagate specific error types while handling others
- Transform errors before propagating

The `on_error` option provides declarative error handling strategies.

---

## Syntax

```constellation
result = MyModule(input) with on_error: skip
```

### Error Strategies

| Strategy | Description |
|----------|-------------|
| `propagate` | Re-throw the error (default) |
| `skip` | Return empty/null, continue pipeline |
| `log` | Log error and skip |
| `wrap` | Wrap in error type for downstream handling |

---

## Semantics

### Strategy Behaviors

#### `propagate` (default)
Re-throws the error, stopping the pipeline.

```constellation
result = MyModule(input) with on_error: propagate
# Equivalent to no error handling
```

#### `skip`
Returns a "zero" value appropriate for the type and continues:
- String → ""
- Int → 0
- List → []
- Optional → None
- Record → record with null fields

```constellation
result = MyModule(input) with on_error: skip
```

#### `log`
Logs the error with context and then skips:

```constellation
result = MyModule(input) with on_error: log
# Logs: "[WARN] MyModule failed: <error>. Skipping."
```

#### `wrap`
Wraps the result in an Either or Result type:

```constellation
result = MyModule(input) with on_error: wrap
# result: Either<Error, T> instead of T
```

### Interaction with Other Options

| Option | Interaction |
|--------|-------------|
| `retry` | on_error applies after all retries fail |
| `fallback` | fallback takes precedence over on_error |
| `timeout` | Timeout errors are handled by on_error |

### Priority Order

```
Module Call
    ↓ (fail)
Retry (if specified)
    ↓ (all retries fail)
Fallback (if specified)
    ↓ (fallback not specified)
on_error strategy
```

---

## Implementation Notes

### Parser Changes

```
ErrorStrategy ::= 'propagate' | 'skip' | 'log' | 'wrap'
```

### AST Changes

```scala
enum ErrorStrategy:
  case Propagate
  case Skip
  case Log
  case Wrap

case class ModuleCallOptions(
  // ...
  onError: Option[ErrorStrategy] = None,  // NEW
)
```

### Runtime Changes

```scala
def executeWithErrorHandling[A](
  module: Module,
  inputs: Map[String, CValue],
  strategy: ErrorStrategy,
  outputType: SemanticType
): IO[A] = {
  module.run(inputs).handleErrorWith { error =>
    strategy match {
      case ErrorStrategy.Propagate => IO.raiseError(error)
      case ErrorStrategy.Skip => IO.pure(zeroValue(outputType))
      case ErrorStrategy.Log =>
        IO(logger.warn(s"${module.name} failed: $error. Skipping.")) >>
        IO.pure(zeroValue(outputType))
      case ErrorStrategy.Wrap =>
        IO.pure(Left(error))  // Returns Either[Error, A]
    }
  }
}

def zeroValue(t: SemanticType): CValue = t match {
  case SString => CValue.CString("")
  case SInt => CValue.CInt(0)
  case SList(_) => CValue.CList(Nil)
  case SOptional(_) => CValue.CNone
  // etc.
}
```

---

## Examples

### Skip Failed Items

```constellation
in items: List<Item>

# Skip items that fail processing
processed = items | map(item =>
    Process(item) with on_error: skip
) | filter(x => x != null)

out processed
```

### Log and Continue

```constellation
in requests: List<Request>

# Log failures but continue processing
responses = requests | map(req =>
    CallAPI(req) with on_error: log
)

out responses
```

### Wrap for Downstream Handling

```constellation
in data: Record

# Wrap result for explicit error handling downstream
result = ProcessData(data) with on_error: wrap

# result is Either<Error, ProcessedData>
finalResult = match result {
    Right(processed) => processed
    Left(error) => handleError(error)
}

out finalResult
```

### Combined with Retry

```constellation
in query: String

# Retry first, then skip if still failing
result = SearchAPI(query) with retry: 3, on_error: skip

out result
```

---

## Alternatives Considered

### 1. Error Predicate

```constellation
result = MyModule(input) with on_error: skip when isTransient
```

Deferred: Error classification adds complexity. Can add later.

### 2. Error Callback

```constellation
result = MyModule(input) with on_error: (e) => logAndNotify(e)
```

Rejected: Too complex for DSL. Use module wrappers for custom logic.

### 3. Multiple Error Handlers

```constellation
result = MyModule(input) with
    on_timeout: skip,
    on_rate_limit: retry,
    on_other: propagate
```

Deferred: Interesting but complex. Start with single strategy.

---

## Open Questions

1. Should `skip` return Optional<T> instead of zero value?
2. How should `wrap` interact with type inference?
3. Should there be error categories (transient, permanent, etc.)?
4. Should `log` be configurable (log level, format)?

---

## References

- [RFC-001: Retry](./rfc-001-retry.md)
- [RFC-003: Fallback](./rfc-003-fallback.md)
