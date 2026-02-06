---
title: "Guard and Coalesce"
sidebar_position: 11
description: "Conditional values with when guards and ?? coalesce"
---

# Guard and Coalesce

Create conditional values with `when` guards and safely unwrap them with the `??` coalesce operator.

## Use Case

You want to compute values that only exist under certain conditions, with fallback defaults.

## The Pipeline

```constellation
# guard-and-coalesce.cst

@example(75)
in score: Int

@example(50)
in threshold: Int

@example(true)
in enabled: Boolean

@example("Default fallback message")
in defaultMessage: String

# Guard: "expr when condition" produces Optional<T>
# If condition is true → Some(expr); if false → None
highScoreMessage = "Excellent!" when score > threshold
processingResult = "Enabled" when enabled
complexGuard = "Both conditions met" when score > 50 and enabled

# Coalesce: "optional ?? fallback" unwraps with a default
finalMessage = highScoreMessage ?? "Below threshold"

# Chained coalesce: first non-None value wins
result = highScoreMessage ?? processingResult ?? defaultMessage

# Practical: conditional bonuses with arithmetic
baseBonus = 10
highScoreBonus = 25 when score > 80
enabledBonus = 15 when enabled

actualHighBonus = highScoreBonus ?? 0
actualEnabledBonus = enabledBonus ?? 0
totalBonus = baseBonus + actualHighBonus + actualEnabledBonus

out highScoreMessage
out processingResult
out complexGuard
out finalMessage
out result
out actualHighBonus
out actualEnabledBonus
out totalBonus
```

## Explanation

| Operator | Syntax | Result | Purpose |
|---|---|---|---|
| Guard | `expr when condition` | `Optional<T>` | `Some(expr)` if true, `None` if false |
| Coalesce | `optional ?? fallback` | `T` | Unwrap with fallback |
| Chain | `a ?? b ?? c` | `T` | Try each in order, use first non-None |

Guards produce `Optional<T>` values. Coalesce unwraps them. Together they replace explicit `if/else` patterns for conditional values with defaults.

:::warning
Guards produce `Optional<T>`, not `T`. You must coalesce (`??`) before using the value in arithmetic or passing it to a module that expects a non-optional type.
:::

## Running the Example

### Input
```json
{
  "score": 75,
  "threshold": 50,
  "enabled": true,
  "defaultMessage": "Default fallback message"
}
```

### Expected Output
```json
{
  "highScoreMessage": "Excellent!",
  "processingResult": "Enabled",
  "complexGuard": "Both conditions met",
  "finalMessage": "Excellent!",
  "result": "Excellent!",
  "actualHighBonus": 0,
  "actualEnabledBonus": 15,
  "totalBonus": 25
}
```

## Variations

### Tiered bonuses

```constellation
in score: Int

tier1 = 50 when score >= 90
tier2 = 30 when score >= 70
tier3 = 10 when score >= 50

bonus = tier1 ?? tier2 ?? tier3 ?? 0

out bonus
```

:::tip
Chained coalesce (`a ?? b ?? c ?? default`) reads like a priority list. The first non-None value wins. Use this pattern for tiered defaults.
:::

## Best Practices

1. **Guard for conditional existence** — use `when` when a value should only exist under certain conditions
2. **Always coalesce before arithmetic** — unwrap `Optional<T>` with `??` before using the value in operations
3. **Chain for priority fallbacks** — `a ?? b ?? c` naturally expresses "try A, then B, then C"

## Related Examples

- [Optional Types](optional-types.md) — explicit `Optional<T>` inputs
- [Branch Expressions](branch-expressions.md) — multi-way conditionals
- [Conditional Branching](conditional-branching.md) — combining all conditional patterns
