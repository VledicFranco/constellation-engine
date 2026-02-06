---
title: "Optional Types"
sidebar_position: 12
description: "Handling optional inputs with Optional<T> and coalesce"
---

# Optional Types

Declare inputs that may or may not be provided, with safe default handling via `Optional<T>` and `??`.

## Use Case

Your pipeline accepts optional configuration parameters — callers may omit them, and you provide sensible defaults.

## The Pipeline

```constellation
# optional-types.cst

# Optional inputs - may be None (missing) at runtime
in maybeValue: Optional<Int>
in maybeName: Optional<String>
in maybeMultiplier: Optional<Int>

# Required inputs
@example(100)
in defaultValue: Int

@example("Anonymous")
in defaultName: String

@example(85)
in score: Int

# Unwrap optionals with defaults
value = maybeValue ?? defaultValue
name = maybeName ?? defaultName
multiplier = maybeMultiplier ?? 1

# Guards create optionals from conditions
positiveScore = score when score > 0
safeScore = positiveScore ?? 0

# Chained fallbacks
bonusValue = 25 when score > 90
standardValue = 10 when score > 50
finalValue = bonusValue ?? standardValue ?? 5

# Arithmetic after unwrapping
scaledValue = value * multiplier

# Configuration with defaults pattern
in maybeTimeout: Optional<Int>
in maybeRetries: Optional<Int>
timeout = maybeTimeout ?? 30
retries = maybeRetries ?? 3
totalWaitTime = timeout * retries

out value
out name
out multiplier
out safeScore
out finalValue
out scaledValue
out timeout
out retries
out totalWaitTime
```

## Explanation

| Pattern | Syntax | Purpose |
|---|---|---|
| Optional input | `in x: Optional<Int>` | Input that may be omitted by the caller |
| Default value | `x ?? defaultValue` | Unwrap with fallback |
| Guard to optional | `expr when condition` | Produce `Optional<T>` from a condition |
| Config pattern | `in x: Optional<T>` + `x ?? default` | Optional parameter with sensible default |

Optional inputs appear as `null` or are absent from the JSON input. The `??` operator provides the fallback value.

:::tip
Define defaults immediately after declaring optional inputs. This keeps the pipeline logic simple and avoids passing `Optional<T>` through multiple steps.
:::

## Running the Example

### Input (all optionals omitted)
```json
{
  "defaultValue": 100,
  "defaultName": "Anonymous",
  "score": 85
}
```

### Expected Output
```json
{
  "value": 100,
  "name": "Anonymous",
  "multiplier": 1,
  "safeScore": 85,
  "finalValue": 10,
  "scaledValue": 100,
  "timeout": 30,
  "retries": 3,
  "totalWaitTime": 90
}
```

### Input (with optionals provided)
```json
{
  "maybeValue": 200,
  "maybeName": "Alice",
  "maybeMultiplier": 3,
  "defaultValue": 100,
  "defaultName": "Anonymous",
  "score": 95,
  "maybeTimeout": 60,
  "maybeRetries": 5
}
```

### Expected Output
```json
{
  "value": 200,
  "name": "Alice",
  "multiplier": 3,
  "safeScore": 95,
  "finalValue": 25,
  "scaledValue": 600,
  "timeout": 60,
  "retries": 5,
  "totalWaitTime": 300
}
```

:::warning
Don't overuse `Optional<T>`. Required inputs catch missing data at the API boundary. Only use optional for truly optional parameters like configuration overrides.
:::

## Best Practices

1. **Use `Optional<T>` for truly optional inputs** — don't make everything optional; required inputs catch missing data early
2. **Provide defaults immediately** — coalesce right after the optional declaration to avoid passing `Optional<T>` through the pipeline
3. **Document defaults in comments** — make it clear what value is used when the input is omitted

## Related Examples

- [Guard and Coalesce](guard-and-coalesce.md) — guards that produce optionals
- [Conditional Branching](conditional-branching.md) — routing with optionals
