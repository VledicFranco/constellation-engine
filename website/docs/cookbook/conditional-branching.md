---
title: "Conditional Branching"
sidebar_position: 17
description: "Route execution through different paths with guards, coalesce, and branch"
---

# Conditional Branching

Combine `when` guards, `??` coalesce, and `branch {}` expressions to route execution through different paths.

## Use Case

You need to classify users into tiers, generate conditional offers, and route requests based on multiple criteria.

## The Pipeline

```constellation
# conditional-branching.cst

@example(85)
in score: Int

@example("premium")
in tier: String

@example("Welcome aboard!")
in defaultMessage: String

# Branch expression: multi-way routing
category = branch {
  score >= 90 -> "platinum",
  score >= 70 -> "gold",
  score >= 50 -> "silver",
  otherwise -> "bronze"
}

# Guard: value exists only when condition is true (Optional<String>)
premiumOffer = "Free upgrade available!" when tier == "premium"
highScoreBonus = "Bonus: 500 points" when score > 80

# Coalesce: unwrap Optional with fallback
offerMessage = premiumOffer ?? "Standard service"
bonusMessage = highScoreBonus ?? "No bonus this time"

# Chained coalesce: priority fallback chain
vipMessage = "VIP: exclusive access" when score >= 95
goldMessage = "Gold: priority support" when score >= 75
finalMessage = vipMessage ?? goldMessage ?? defaultMessage

# Combine branch and guard for routing
route = branch {
  tier == "premium" and score >= 80 -> "fast-track",
  tier == "premium" -> "priority-queue",
  score >= 90 -> "express",
  otherwise -> "standard"
}

out category
out offerMessage
out bonusMessage
out finalMessage
out route
```

## Explanation

| Pattern | When to use |
|---|---|
| `branch { ... }` | Classify into one of N categories — every input gets exactly one result |
| `expr when condition` | Compute a value only if a condition holds — result is `Optional<T>` |
| `optional ?? fallback` | Provide a default when the optional value is absent |
| `a ?? b ?? c` | Priority chain — first non-None wins |

These three patterns compose naturally:

- Use `branch` when you need exactly one result from several options
- Use `when` + `??` when you want to try conditional values with fallbacks
- Use `branch` for routing keys, `when` + `??` for conditional messages/bonuses

:::tip Choosing the Right Pattern
Use `branch` for exhaustive classification (every input maps to exactly one output). Use `when` + `??` for optional enrichment (a value that might not exist).
:::

## Running the Example

### Input
```json
{
  "score": 85,
  "tier": "premium",
  "defaultMessage": "Welcome aboard!"
}
```

### Expected Output
```json
{
  "category": "gold",
  "offerMessage": "Free upgrade available!",
  "bonusMessage": "Bonus: 500 points",
  "finalMessage": "Gold: priority support",
  "route": "fast-track"
}
```

## Variations

### Error-level routing

```constellation
@example(500)
in statusCode: Int

severity = branch {
  statusCode >= 500 -> "critical",
  statusCode >= 400 -> "warning",
  statusCode >= 300 -> "info",
  otherwise -> "ok"
}

alert = "Page ops team" when statusCode >= 500
notification = alert ?? "No action required"

out severity
out notification
```

:::note
All three patterns produce values (they are expressions, not statements). You can assign the result to a variable, interpolate it, or pass it to a module.
:::

## Best Practices

1. **Use `branch` for exhaustive classification** — the `otherwise` clause ensures every input is handled
2. **Use guards for conditional enrichment** — values that only make sense under certain conditions
3. **Keep coalesce chains short** — if you have more than 3-4 levels, consider `branch` instead
4. **Name intermediate booleans** — `isHighScore = score > 80` is clearer than inline conditions in complex expressions

## Related Examples

- [Branch Expressions](branch-expressions.md) — branch syntax reference
- [Guard and Coalesce](guard-and-coalesce.md) — guard and coalesce patterns
- [Lead Scoring](lead-scoring.md) — conditional logic in a real pipeline
