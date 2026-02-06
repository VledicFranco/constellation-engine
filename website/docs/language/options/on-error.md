---
title: "on-error"
sidebar_position: 10
description: "Control error handling strategy using propagate, skip, log, or wrap to determine failure behavior."
---

# on_error

Control how errors are handled during module execution.

## Syntax

```constellation
result = Module(args) with on_error: <strategy>
```

**Type:** Strategy enum (`propagate`, `skip`, `log`, `wrap`)

## Description

The `on_error` option determines what happens when a module call fails. Unlike `fallback` which provides a specific value, `on_error` defines a general error handling strategy.

:::warning skip uses type-specific zero values
With `skip`, failures return the zero value for the return type (0 for Int, "" for String, etc.). Ensure downstream code can handle these sentinel values without misinterpreting them as valid results.
:::

:::tip Combine on_error: log with fallback
Use `on_error: log, fallback: value` to get both visibility (logged errors) and graceful degradation (specific fallback value). The log strategy alone only provides zero values.
:::

## Strategies

### propagate (default)

Re-throw the error to the caller. This is the default behavior when no error handling is specified.

```constellation
result = RiskyOperation(data) with on_error: propagate
```

Errors bubble up to be handled by the calling context.

### skip

Return a zero/default value for the type and continue execution.

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

### log

Log the error and return a zero value. Combines logging with graceful degradation.

```constellation
data = ProcessData(input) with on_error: log
```

Logs: `[ProcessData] failed: <error message>. Skipping.`

### wrap

Wrap the result in an `Either` type for downstream handling.

```constellation
result = UncertainOperation(input) with on_error: wrap
# Returns Either[Error, Value]
```

Downstream code can then handle the error explicitly:
```constellation
processed = when result.isRight then process(result.right) else handleError(result.left)
```

## Examples

### Skip Failing Items in a Pipeline

```constellation
in items: List[Record]

# Process each item, skipping failures
processed = items.map(item => Transform(item) with on_error: skip)

out processed
```

### Log and Continue

```constellation
# Log any failures but continue with default values
enriched = EnrichData(data) with on_error: log, retry: 2
```

### Wrap for Explicit Handling

```constellation
result = ExternalService(request) with on_error: wrap

# Handle the wrapped result
output = when result.isRight
    then result.right
    else { error: result.left.message, fallback: true }

out output
```

## on_error vs fallback

| Aspect | on_error | fallback |
|--------|----------|----------|
| Value | Zero/default for type | Specific expression |
| Flexibility | Fixed strategies | Any expression |
| Type | Must match return type | Must match return type |
| Use case | General handling | Specific default value |

They can be combined, but `fallback` takes precedence:

```constellation
# fallback is used for the value, on_error: log adds logging
result = Operation(x) with on_error: log, fallback: defaultValue
```

## Related Options

- **[fallback](./fallback.md)** - Specific fallback value
- **[retry](./retry.md)** - Retry before error handling

## Best Practices

- Use `propagate` when errors must be handled upstream
- Use `skip` for non-critical operations in pipelines
- Use `log` when you need visibility into failures
- Use `wrap` for explicit error handling in code
- Combine with `retry` to attempt recovery first
