---
title: "Branch Expressions"
sidebar_position: 10
description: "Multi-way conditionals with branch { } syntax"
---

# Branch Expressions

Use `branch { }` for clean multi-way conditionals, replacing nested `if/else` chains.

## Use Case

You need to classify a value into one of several categories based on conditions.

## The Pipeline

```constellation
# branch-expressions.cst

@example("high")
in priority: String

@example(85)
in score: Int

@example("technical")
in category: String

# Basic branch: string matching
processingTier = branch {
  priority == "high" -> "fast-path",
  priority == "medium" -> "standard",
  otherwise -> "batch"
}

# Numeric ranges (first match wins)
grade = branch {
  score >= 90 -> "A",
  score >= 80 -> "B",
  score >= 70 -> "C",
  score >= 60 -> "D",
  otherwise -> "F"
}

# Complex boolean conditions
isUrgent = priority == "high"
isHighScore = score >= 75

actionPlan = branch {
  isUrgent and isHighScore -> "immediate-review",
  isUrgent and not isHighScore -> "escalate",
  isHighScore -> "queue-review",
  otherwise -> "standard-process"
}

# Category routing
department = branch {
  category == "sales" -> "Sales Team",
  category == "support" -> "Customer Service",
  category == "technical" -> "Engineering",
  category == "billing" -> "Finance",
  otherwise -> "General Inquiries"
}

out processingTier
out grade
out actionPlan
out department
```

## Explanation

| Part | Syntax | Purpose |
|---|---|---|
| Branch keyword | `branch { ... }` | Opens a multi-way conditional |
| Arm | `condition -> result` | Evaluated in order; first true condition wins |
| Otherwise | `otherwise -> default` | Required fallback if no condition matches |

Branch expressions are expressions — they produce a value that can be assigned to a variable, interpolated into a string, or passed to a module.

## Running the Example

### Input
```json
{
  "priority": "high",
  "score": 85,
  "category": "technical"
}
```

### Expected Output
```json
{
  "processingTier": "fast-path",
  "grade": "B",
  "actionPlan": "immediate-review",
  "department": "Engineering"
}
```

## Variations

### Branch vs if/else

```constellation
in score: Int

# Branch (preferred for 3+ cases)
grade_branch = branch {
  score >= 90 -> "A",
  score >= 80 -> "B",
  otherwise -> "C or below"
}

# If/else (fine for 2 cases)
pass_fail = if (score >= 60) "Pass" else "Fail"

out grade_branch
out pass_fail
```

## Best Practices

1. **Use `branch` for 3+ conditions** — `if/else` is fine for binary choices
2. **Order conditions from most specific to least** — conditions are evaluated top to bottom
3. **Always include `otherwise`** — the compiler requires a fallback case

## Related Examples

- [Guard and Coalesce](guard-and-coalesce.md) — conditional values with `when`
- [Conditional Branching](conditional-branching.md) — combining branch, guard, and coalesce
- [String Interpolation](string-interpolation.md) — using branch results in strings
