---
title: "List Processing"
sidebar_position: 5
description: "Filter and transform lists with higher-order functions like map, filter, all, and any using lambda syntax."
---

# List Processing Pipeline

Use higher-order functions to filter and transform lists - the foundation of functional data processing.

## Use Case

You have a list of items and need to:
- Filter items matching criteria
- Transform each item
- Check conditions across all items

This is essential for data preprocessing, validation, and transformation.

## The Pipeline

```
# list-processing.cst
# Filter and transform lists with lambda functions

use stdlib.collection

in numbers: List<Int>
in threshold: Int

# Filter: keep only numbers greater than threshold
filtered = filter(numbers, (x) => gt(x, threshold))

# Map: double each number
doubled = map(numbers, (x) => multiply(x, 2))

# All: check if all numbers are positive
allPositive = all(numbers, (x) => gt(x, 0))

# Any: check if any number exceeds 100
anyLarge = any(numbers, (x) => gt(x, 100))

out filtered
out doubled
out allPositive
out anyLarge
```

## Explanation

| Function | Description | Signature |
|----------|-------------|-----------|
| `filter` | Keep items matching predicate | `(List<T>, (T) => Boolean) => List<T>` |
| `map` | Transform each item | `(List<T>, (T) => U) => List<U>` |
| `all` | True if all items match | `(List<T>, (T) => Boolean) => Boolean` |
| `any` | True if any item matches | `(List<T>, (T) => Boolean) => Boolean` |

## Running the Example

### Input
```json
{
  "numbers": [5, 25, 15, 150, 10],
  "threshold": 10
}
```

### Expected Output
```json
{
  "filtered": [25, 15, 150],
  "doubled": [10, 50, 30, 300, 20],
  "allPositive": true,
  "anyLarge": true
}
```

## Variations

### Filter and Compute

Filter then calculate statistics:

```
use stdlib.collection

in scores: List<Int>
in passingScore: Int

# Get only passing scores
passing = filter(scores, (s) => gte(s, passingScore))

# Calculate stats on passing scores
passingCount = list-length(passing)
passingAvg = Average(passing)

out passingCount
out passingAvg
```

### Chain Transformations

Apply multiple transformations in sequence:

```
use stdlib.collection

in numbers: List<Int>

# First double all numbers
step1 = map(numbers, (x) => multiply(x, 2))

# Then filter to keep only those > 50
step2 = filter(step1, (x) => gt(x, 50))

# Finally check if any exceed 200
hasVeryLarge = any(step2, (x) => gt(x, 200))

out step2
out hasVeryLarge
```

### Validation Pipeline

Validate data meets criteria:

```
use stdlib.collection

in values: List<Int>
in minValue: Int
in maxValue: Int

# Check all values are within range
allAboveMin = all(values, (v) => gte(v, minValue))
allBelowMax = all(values, (v) => lte(v, maxValue))

# Check if any values are exactly at boundaries
anyAtMin = any(values, (v) => eq-int(v, minValue))
anyAtMax = any(values, (v) => eq-int(v, maxValue))

out allAboveMin
out allBelowMax
out anyAtMin
out anyAtMax
```

### Score Normalization

Transform scores to a different scale:

```
use stdlib.collection

in rawScores: List<Int>
in multiplier: Int

# Scale all scores
scaled = map(rawScores, (score) => multiply(score, multiplier))

# Filter to valid range (0-100 after scaling)
valid = filter(scaled, (s) => and(gte(s, 0), lte(s, 100)))

out scaled
out valid
```

## Lambda Syntax

Lambdas in constellation-lang use arrow syntax:

```
# Single parameter
(x) => expression

# With comparison
(x) => gt(x, 10)

# With arithmetic
(x) => multiply(x, 2)

# With boolean logic
(x) => and(gt(x, 0), lt(x, 100))
```

## Real-World Applications

### Data Cleaning
- Filter out invalid entries
- Transform values to standard format

### Validation
- Check all items meet criteria
- Find items that violate rules

### Feature Engineering
- Scale features for ML models
- Create derived features

### Business Logic
- Filter products by criteria
- Apply bulk transformations

## Performance Considerations

1. **Filter early**: Reduce list size before expensive operations
2. **Avoid redundant passes**: Combine conditions when possible
3. **Use short-circuit evaluation**: `any` stops at first match, `all` stops at first failure

## Best Practices

1. **Keep lambdas simple**: Complex logic should be in separate steps
2. **Name intermediate results**: Makes pipeline readable and debuggable
3. **Validate inputs**: Check list isn't empty before operations like `list-first`

## Related Examples

- [Data Statistics Pipeline](data-statistics.md) - Compute stats on filtered lists
- [Scoring Pipeline](scoring-pipeline.md) - Use filtering in scoring logic
