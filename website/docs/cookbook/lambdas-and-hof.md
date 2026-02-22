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

in numbers: List<Int>

# Filter: keep elements matching predicate
positives = numbers filter it > 0
above10 = numbers filter it > 10

# Map: transform each element
doubled = numbers map it * 2
plus10 = numbers map it + 10

# All: check if all elements satisfy predicate
allPositive = numbers all it > 0
allBelow100 = numbers all it < 100

# Any: check if any element satisfies predicate
hasNegative = numbers any it < 0
hasAbove100 = numbers any it > 100

# Chaining: filter then transform (left-to-right)
positivesDoubled = numbers filter it > 0 map it * 2

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

### Three equivalent forms

All higher-order functions support three calling styles:

```constellation
# Infix + implicit it (most concise)
positives = numbers filter it > 0

# Prefix + implicit it
positives = filter(numbers, it > 0)

# Explicit lambda (most explicit)
positives = filter(numbers, (x) => x > 0)
```

Use infix for simple predicates and transforms. Use explicit lambdas when the body is complex or when naming the parameter aids clarity.

:::tip
Lambda bodies support closure capture — you can reference variables from the enclosing scope:
```constellation
in threshold: Int
above = numbers filter it > threshold   # captures `threshold`
```
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

in scores: List<Int>

passing = scores filter it >= 60
allPassing = scores all it >= 60

out passing
out allPassing
```

### Chaining pipelines

```constellation
use stdlib.collection

in numbers: List<Int>

# Left-to-right pipeline: filter, then transform
result = numbers filter it > 0 map it * 10

# Equivalent nested form
result2 = map(filter(numbers, it > 0), it * 10)

out result
```

:::tip
Filter before map. Running `numbers filter pred map transform` is more efficient than mapping first and filtering after, because fewer elements are transformed.
:::

## Best Practices

1. **Use infix for readability** — `numbers filter it > 0` reads naturally left-to-right
2. **Chain operations** — filter first, then map, to avoid transforming elements you'll discard
3. **Use `all`/`any` for validation** — check batch constraints without manual iteration
4. **Capture outer variables** — closures work with all three calling forms

## Related Examples

- [Namespaces](namespaces.md) — importing functions
- [Data Pipeline](data-pipeline.md) — filtering and aggregation with modules
