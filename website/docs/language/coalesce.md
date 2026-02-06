---
title: "Coalesce Operator"
sidebar_position: 9
description: "Use the coalesce operator (??) to provide fallback values for optional expressions and unwrap Optional types."
---

# Coalesce Operator (`??`)

The coalesce operator provides fallback values for optional expressions. It unwraps `Optional<T>` values, returning the inner value if present or the fallback if the optional is `None`.

## Syntax

```
result = optional_expr ?? fallback_expr
```

- If `optional_expr` is `Some(v)`, the result is `v`
- If `optional_expr` is `None`, the result is `fallback_expr`

## Basic Usage

### Simple Fallback

Provide a default value when an optional might be `None`:

```
in score: Int
in threshold: Int

# Guard produces Optional<String>
highScoreMessage = "Excellent!" when score > threshold

# Coalesce provides fallback, result is String
finalMessage = highScoreMessage ?? "Below threshold"

out finalMessage
```

If `score > threshold`: `finalMessage` is `"Excellent!"`
If `score <= threshold`: `finalMessage` is `"Below threshold"`

### Unwrapping Optional Inputs

Handle optional input values with defaults:

```
in maybeValue: Optional<Int>

# Unwrap with default
value = maybeValue ?? 0

out value
```

## Chained Coalesce

Chain multiple coalesce operators to try fallbacks in order:

```
in score: Int
in enabled: Boolean
in defaultMessage: String

# Multiple optional sources
highScoreMessage = "Excellent!" when score > 80
processingResult = "Enabled" when enabled

# Chain of fallbacks - first non-None wins
result = highScoreMessage ?? processingResult ?? defaultMessage

out result
```

The chain evaluates left to right:
1. If `highScoreMessage` is `Some(v)`, result is `v`
2. Otherwise, if `processingResult` is `Some(v)`, result is `v`
3. Otherwise, result is `defaultMessage`

### Right Associativity

The coalesce operator is right-associative:

```
# These are equivalent:
a ?? b ?? c
a ?? (b ?? c)
```

This ensures the entire fallback chain is evaluated correctly.

## Type Semantics

### Type Unwrapping

Coalesce removes the `Optional` wrapper:

| Left Operand | Right Operand | Result |
|--------------|---------------|--------|
| `Optional<String>` | `String` | `String` |
| `Optional<Int>` | `Int` | `Int` |
| `Optional<{ x: Int }>` | `{ x: Int }` | `{ x: Int }` |

### Type Compatibility

The fallback must be compatible with the optional's inner type:

```
# Valid - both are String
message = optionalString ?? "default"

# Valid - both are Int
count = optionalCount ?? 0

# The fallback type must match the optional's inner type
```

## Practical Examples

### Conditional Bonuses with Defaults

Calculate bonuses where conditions may not be met:

```
in score: Int
in enabled: Boolean

# Conditional bonuses (may be None)
highScoreBonus = 25 when score > 80
enabledBonus = 15 when enabled

# Use coalesce to get 0 if condition not met
actualHighBonus = highScoreBonus ?? 0
actualEnabledBonus = enabledBonus ?? 0

# Total with all applicable bonuses
totalBonus = 10 + actualHighBonus + actualEnabledBonus

out totalBonus
```

### Cache Fallback Pattern

Try cache first, compute on miss:

```
in id: String
in data: String

# Try cached result
cachedResult = get-from-cache(id)

# Compute fresh if cache miss
freshResult = compute-expensive(data)

# Use cached if available, otherwise fresh
result = cachedResult ?? freshResult

out result
```

### Feature Flag Orchestration

Handle multiple feature variants with fallback:

```
in data: String
in flags: { useNewModel: Boolean, experimentGroup: String }

# Conditional model selection
newModelResult = new-model-v2(data) when flags.useNewModel
oldModelResult = legacy-model(data) when not flags.useNewModel

# Merge with fallback
modelOutput = newModelResult ?? oldModelResult

# Experiment branches
experimentA = variant-a(modelOutput) when flags.experimentGroup == "A"
experimentB = variant-b(modelOutput) when flags.experimentGroup == "B"
control = control-variant(modelOutput) when flags.experimentGroup == "control"

# First matching experiment wins
final = experimentA ?? experimentB ?? control

out final
```

### Multi-Source Data Aggregation

Aggregate data from multiple sources with priorities:

```
in userId: String

# Try multiple data sources in priority order
primaryData = fetch-primary-source(userId)
secondaryData = fetch-secondary-source(userId)
tertiaryData = fetch-tertiary-source(userId)
defaultData = generate-default(userId)

# Use first available source
userData = primaryData ?? secondaryData ?? tertiaryData ?? defaultData

out userData
```

### Conditional Processing with Fallback

Process data conditionally with safe fallback:

```
in text: String
in minLength: Int

# Only process sufficiently long text
processed = expensive-transform(text) when length(text) > minLength

# Fallback to simple processing
simple = simple-transform(text)

# Use expensive result if available, otherwise simple
result = processed ?? simple

out result
```

## Comparison with Conditionals

| Feature | Coalesce (`??`) | Conditional (`if/else`) |
|---------|-----------------|------------------------|
| Purpose | Unwrap optionals | Binary choice |
| Left operand | Must be optional | Boolean condition |
| Chaining | Natural (a ?? b ?? c) | Nested |
| Best for | Fallback values | Conditional logic |

```
in maybeValue: Optional<Int>
in flag: Boolean

# Coalesce - unwrap optional with fallback
unwrapped = maybeValue ?? 0

# Conditional - choose based on boolean
chosen = if (flag) 100 else 0
```

## Short-Circuit Evaluation

The coalesce operator uses short-circuit evaluation:

```
# If a is Some(v), b is never evaluated
result = a ?? expensive-computation()
```

This is important for performance when fallbacks involve expensive operations.

## Combining with Guards

Guards and coalesce work together naturally:

```
in value: Int
in threshold: Int

# Guard produces Optional
guarded = value * 2 when value > threshold

# Coalesce unwraps with fallback
result = guarded ?? 0

out result
```

This pattern is common for conditional computation with defaults.

## Best Practices

1. **Use for optional unwrapping**: Primary purpose is handling `Optional` values
2. **Provide sensible defaults**: Fallback values should be meaningful
3. **Chain for priority**: Use chained coalesce for multi-source data
4. **Leverage short-circuit**: Put expensive computations on the right
5. **Combine with guards**: Natural pairing for conditional execution with defaults
