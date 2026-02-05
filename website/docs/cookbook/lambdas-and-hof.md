---
title: "Lambdas and Higher-Order Functions"
sidebar_position: 9
description: "List processing with filter, map, all, and any"
---

# Lambdas and Higher-Order Functions

Process lists declaratively using lambda expressions and higher-order functions: `filter`, `map`, `all`, `any`.

## Use Case

You have a list of numbers and need to filter, transform, and validate them without writing explicit loops.

## The Pipeline

```constellation
# lambdas-and-hof.cst

use stdlib.collection
use stdlib.compare
use stdlib.math

in numbers: List<Int>

# Filter: keep elements matching predicate
positives = filter(numbers, (x) => gt(x, 0))
above10 = filter(numbers, (x) => gt(x, 10))

# Map: transform each element
doubled = map(numbers, (x) => multiply(x, 2))
plus10 = map(numbers, (x) => add(x, 10))

# All: check if all elements satisfy predicate
allPositive = all(numbers, (x) => gt(x, 0))
allBelow100 = all(numbers, (x) => lt(x, 100))

# Any: check if any element satisfies predicate
hasNegative = any(numbers, (x) => lt(x, 0))
hasAbove100 = any(numbers, (x) => gt(x, 100))

# Chaining: filter then transform
positivesDoubled = map(positives, (x) => multiply(x, 2))

out positives
out above10
out doubled
out plus10
out allPositive
out allBelow100
out hasNegative
out hasAbove100
out positivesDoubled
```

## Explanation

| Function | Signature | Returns | Purpose |
|---|---|---|---|
| `filter` | `filter(list, predicate)` | `List<T>` | Keep elements where predicate is true |
| `map` | `map(list, transform)` | `List<U>` | Apply transform to each element |
| `all` | `all(list, predicate)` | `Boolean` | True if predicate holds for all elements |
| `any` | `any(list, predicate)` | `Boolean` | True if predicate holds for at least one element |

Lambda syntax: `(x) => expression`. The lambda body can call any function available in scope (imported via `use` or fully qualified).

:::note
Lambda bodies currently support the lambda parameter and literals. Closure capture (referencing outer variables from the enclosing scope) is not yet implemented.
:::

## Running the Example

### Input
```json
{
  "numbers": [5, -3, 15, 8, -1, 42]
}
```

### Expected Output
```json
{
  "positives": [5, 15, 8, 42],
  "above10": [15, 42],
  "doubled": [10, -6, 30, 16, -2, 84],
  "plus10": [15, 7, 25, 18, 9, 52],
  "allPositive": false,
  "allBelow100": true,
  "hasNegative": true,
  "hasAbove100": false,
  "positivesDoubled": [10, 30, 16, 84]
}
```

## Variations

### Filter then check

```constellation
use stdlib.collection
use stdlib.compare

in scores: List<Int>

passing = filter(scores, (x) => gte(x, 60))
allPassing = all(scores, (x) => gte(x, 60))

out passing
out allPassing
```

## Best Practices

1. **Import the right namespaces** — `stdlib.collection` for HOF, `stdlib.compare` for comparison functions, `stdlib.math` for arithmetic
2. **Chain operations** — filter first, then map, to avoid transforming elements you'll discard
3. **Use `all`/`any` for validation** — check batch constraints without manual iteration

## Related Examples

- [Namespaces](namespaces.md) — importing functions
- [Data Pipeline](data-pipeline.md) — filtering and aggregation with modules
