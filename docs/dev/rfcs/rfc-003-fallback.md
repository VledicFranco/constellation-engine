# RFC-003: Fallback

**Status:** Draft
**Priority:** 1 (Core Resilience)
**Author:** Agent 1
**Created:** 2026-01-25

---

## Summary

Add a `fallback` option to module calls that provides a default value when the module fails.

---

## Motivation

When module calls fail, pipelines often need graceful degradation rather than complete failure:
- Return cached data when live API is unavailable
- Use a default value when optional enrichment fails
- Switch to a backup service when primary fails

Currently, error handling requires wrapping module calls in try/catch at the Scala level. This RFC proposes language-level fallback support for declarative error handling.

---

## Syntax

```constellation
result = MyModule(input) with fallback: defaultValue
```

### Examples

```constellation
# Static fallback value
greeting = GetGreeting(user) with fallback: "Hello, Guest!"

# Fallback to another module call
data = PrimaryAPI(query) with fallback: BackupAPI(query)

# Fallback with other options
result = Process(input) with retry: 3, fallback: "error"
```

---

## Semantics

### Behavior

1. Execute the module call
2. If successful, return the result
3. If failed (after all retries exhausted), evaluate and return the fallback expression
4. The fallback expression is only evaluated if needed (lazy evaluation)

### Type Constraints

The fallback expression must have a type compatible with the module's return type:

```constellation
# OK: both return String
result = GetName(id) with fallback: "Unknown"

# OK: fallback can be narrower type
result = GetUser(id) with fallback: defaultUser

# ERROR: type mismatch
result = GetCount(id) with fallback: "not a number"  # Int vs String
```

### Interaction with Other Options

| Option | Interaction |
|--------|-------------|
| `retry` | Fallback used only after all retries fail |
| `timeout` | Timeout triggers fallback |
| `cache` | Fallback results are NOT cached |

### Evaluation Order

```
Module Call
    ↓ (fail)
Retry (if specified)
    ↓ (all retries fail)
Fallback Expression
    ↓
Result
```

---

## Implementation Notes

### Parser Changes

Allow expressions as option values:

```
OptionValue    ::= Integer | Duration | Expression | Identifier
```

### AST Changes

```scala
case class ModuleCallOptions(
  retry: Option[Int] = None,
  timeout: Option[Duration] = None,
  fallback: Option[Expression] = None,  // NEW
  // ...
)
```

### Type Checker Changes

Validate that fallback type is compatible with module return type:

```scala
def checkFallback(moduleType: SemanticType, fallbackExpr: Expression): Either[TypeError, Unit] = {
  val fallbackType = typeCheck(fallbackExpr)
  if (fallbackType.isAssignableTo(moduleType)) Right(())
  else Left(TypeMismatch(moduleType, fallbackType))
}
```

### Runtime Changes

```scala
def executeWithFallback[A](
  module: Module,
  inputs: Map[String, CValue],
  fallback: => A
): IO[A] = {
  module.run(inputs).handleError(_ => fallback)
}
```

### Error Reporting

When fallback is used, optionally log the original error:

```
[WARN] GetEmbedding failed, using fallback
  Original error: ConnectionTimeout
  Fallback value: <default embedding>
```

---

## Examples

### Static Fallback

```constellation
in text: String

# Use empty string if translation fails
translated = Translate(text) with fallback: ""

out translated
```

### Module Fallback

```constellation
in query: String

# Try primary, fall back to backup
result = PrimarySearch(query) with fallback: BackupSearch(query)

out result
```

### Chained Fallbacks

```constellation
in userId: String

# Multiple fallback levels (using nested calls)
userData = GetUserLive(userId) with fallback: (
  GetUserCache(userId) with fallback: defaultUser
)

out userData
```

### Fallback with Retry

```constellation
in request: Request

# Try 3 times, then use fallback
response = CallService(request) with retry: 3, fallback: errorResponse

out response
```

---

## Alternatives Considered

### 1. Default Parameter on Module

```constellation
result = MyModule(input, default: "fallback")
```

Rejected: Mixes error handling with normal parameters; not all modules support defaults.

### 2. Separate Fallback Statement

```constellation
result = MyModule(input)
fallback result: "default"
```

Rejected: Separates related logic; harder to read.

### 3. Pattern Matching on Result

```constellation
result = match MyModule(input) {
  Success(v) => v
  Failure(_) => "default"
}
```

Deferred: More powerful but more complex. Can add later for advanced use cases.

---

## Open Questions

1. Should fallback expressions have access to the error that triggered them?
2. Should we support multiple fallback levels in a single option?
3. Should fallback results be distinguishable from normal results?

---

## References

- [RFC-001: Retry](./rfc-001-retry.md)
- [RFC-002: Timeout](./rfc-002-timeout.md)
