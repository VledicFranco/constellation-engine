---
title: "fallback"
sidebar_position: 3
---

# fallback

Provide a default value to use when a module call fails.

## Syntax

```constellation
result = Module(args) with fallback: <expression>
```

**Type:** Expression (must match module's return type)

## Description

The `fallback` option specifies a value to return when a module call fails, instead of propagating the error. When combined with `retry`, the fallback is used only after all retry attempts are exhausted.

The fallback expression must have the same type as the module's return type. This is validated at compile time.

## Examples

### Simple Fallback Value

```constellation
config = GetConfig(key) with fallback: "default"
```

If `GetConfig` fails, return `"default"` instead.

### Fallback with Retry

```constellation
price = GetPrice(symbol) with retry: 3, fallback: 0.0
```

Try up to 4 times, then return `0.0` if all attempts fail.

### Record Fallback

```constellation
user = LoadUser(id) with fallback: { name: "Unknown", id: 0 }
```

Return a default user record on failure.

### Fallback with Another Call

```constellation
primary = PrimaryService(req) with fallback: BackupService(req)
```

Fall back to a secondary service on failure.

### Conditional Fallback

```constellation
in useCache: Boolean

data = FetchData(id) with fallback: when useCache then cachedData else emptyData
```

Use conditional logic in fallback expressions.

## Behavior

1. Execute the module (with retries if configured)
2. If successful, return the result
3. If all attempts fail:
   - Evaluate the fallback expression
   - Return the fallback value

Note: The fallback expression is only evaluated if needed (lazy evaluation).

## Type Checking

The fallback expression is type-checked against the module's return type:

```constellation
# Valid - fallback matches return type (Int)
count = CountItems(list) with fallback: 0

# Invalid - type mismatch (String vs Int)
count = CountItems(list) with fallback: "none"  # Compile error!
```

## Related Options

- **[retry](./retry.md)** - Retry before using fallback
- **[timeout](./timeout.md)** - Timeout before using fallback
- **[on_error](./on-error.md)** - Alternative error handling

## Best Practices

- Provide meaningful default values
- Consider the downstream impact of fallback values
- Use fallback for graceful degradation, not error suppression
- Log errors separately when using fallback (consider `on_error: log`)
