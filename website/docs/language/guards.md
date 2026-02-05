---
title: "Guard Expressions"
sidebar_position: 8
---

# Guard Expressions (`when`)

Guard expressions provide conditional execution by attaching a boolean condition to any expression. When the condition is false, the guarded expression produces `None` instead of executing.

## Syntax

```
result = expression when condition
```

The result type is `Optional<T>` where `T` is the type of `expression`.

## Basic Usage

### Simple Guards

Guard any expression with a boolean condition:

```
in score: Int
in threshold: Int

# Only compute when score exceeds threshold
highScoreMessage = "Excellent!" when score > threshold
```

If `score > threshold` is true, `highScoreMessage` is `Some("Excellent!")`.
If `score > threshold` is false, `highScoreMessage` is `None`.

### Boolean Guards

Guard based on boolean input values:

```
in enabled: Boolean
in data: String

# Only process when enabled flag is true
processingResult = transform(data) when enabled
```

### Comparison Guards

Use comparison operators to build guard conditions:

```
in count: Int
in value: Float

# Various comparison guards
large = process-large(data) when count > 100
small = process-small(data) when count <= 10
exact = process-exact(data) when count == 50
different = process-alt(data) when value != 0.0
```

## Complex Guard Conditions

### Boolean Operators

Combine conditions using `and`, `or`, and `not`:

```
in score: Int
in enabled: Boolean
in override: Boolean

# AND - both conditions must be true
complexGuard = "Both met" when score > 50 and enabled

# OR - at least one condition must be true
anyCondition = process(data) when enabled or override

# NOT - negates the condition
whenDisabled = fallback(data) when not enabled

# Combined operators
fullCondition = expensive-op(data) when (enabled and score > 80) or override
```

### Operator Precedence

Boolean operators follow standard precedence (lowest to highest):
1. `or` - Logical OR
2. `and` - Logical AND
3. `not` - Logical NOT
4. Comparison operators (`==`, `!=`, `<`, `>`, `<=`, `>=`)

Use parentheses to control evaluation order:

```
# Without parentheses: flag or (isHigh and enabled)
result1 = data when flag or isHigh and enabled

# With parentheses: (flag or isHigh) and enabled
result2 = data when (flag or isHigh) and enabled
```

## Guards with Computed Values

Guard expressions can use computed intermediate values:

```
in score: Int
in threshold: Int

# Compute intermediate value
doubleScore = score * 2
adjustedThreshold = threshold + 10

# Use computed values in guard
doubleGuard = "High double" when doubleScore > 100
adjustedGuard = "Above adjusted" when score > adjustedThreshold
```

## Practical Examples

### Conditional Bonuses

Calculate bonuses that only apply under certain conditions:

```
in score: Int
in enabled: Boolean
in isPremium: Boolean

# Base bonus always applies
baseBonus = 10

# Conditional bonuses (may be None)
highScoreBonus = 25 when score > 80
enabledBonus = 15 when enabled
premiumBonus = 50 when isPremium

# Use coalesce to get actual values (0 if condition not met)
actualHighBonus = highScoreBonus ?? 0
actualEnabledBonus = enabledBonus ?? 0
actualPremiumBonus = premiumBonus ?? 0

# Total with all applicable bonuses
totalBonus = baseBonus + actualHighBonus + actualEnabledBonus + actualPremiumBonus

out totalBonus
```

### Conditional Feature Extraction

Extract features only for qualifying data:

```
in user: { tier: String, score: Float }

# Premium features only for premium users
premiumFeatures = get-premium-data(user) when user.tier == "premium"

# High-engagement analysis only for engaged users
engagementAnalysis = analyze-engagement(user) when user.score > 0.8

out premiumFeatures
out engagementAnalysis
```

### Conditional Processing Pipeline

Process data conditionally based on characteristics:

```
in text: String
in minLength: Int

# Only compute embeddings for sufficiently long text
embeddings = compute-embeddings(text) when length(text) > minLength

# Only apply expensive model for long inputs
advancedAnalysis = advanced-model(text) when length(text) > 1000

out embeddings
out advancedAnalysis
```

## Type Semantics

### Optional Type Result

Every guarded expression produces `Optional<T>`:

| Expression Type | Guard Result Type |
|----------------|-------------------|
| `String` | `Optional<String>` |
| `Int` | `Optional<Int>` |
| `{ name: String }` | `Optional<{ name: String }>` |
| `List<Int>` | `Optional<List<Int>>` |

### Unwrapping with Coalesce

Use the `??` operator to unwrap optional values with a fallback:

```
in score: Int

# Guarded expression produces Optional<String>
message = "High score!" when score > 100

# Unwrap with fallback produces String
finalMessage = message ?? "Keep trying"

out finalMessage
```

See [Coalesce Operator](./coalesce.md) for more details on unwrapping optionals.

## Comparison with Conditionals

Guards differ from `if/else` expressions:

| Feature | Guard (`when`) | Conditional (`if/else`) |
|---------|---------------|------------------------|
| Result type | `Optional<T>` | `T` |
| Else branch | Implicit `None` | Required |
| Best for | Optional execution | Binary choice |

```
in flag: Boolean
in value: Int

# Guard - result may be None
guarded = value * 2 when flag        # Type: Optional<Int>

# Conditional - always produces a value
conditional = if (flag) value * 2 else 0  # Type: Int
```

## Best Practices

1. **Use guards for optional computation**: When you may not need a value
2. **Combine with coalesce**: Provide fallbacks for guarded expressions
3. **Keep conditions simple**: Complex conditions hurt readability
4. **Consider short-circuit evaluation**: Boolean operators short-circuit
