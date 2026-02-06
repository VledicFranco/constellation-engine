---
title: "Lambda Expressions"
sidebar_position: 10
description: "Define inline functions with lambda expressions for filter, map, all, and any operations on lists."
---

# Lambda Expressions

Lambda expressions define inline functions for use with higher-order functions. They enable functional-style list processing with operations like `filter`, `map`, `all`, and `any`.

## Syntax

```
(parameter) => expression
(param1, param2) => expression
```

Parameters can optionally include type annotations:

```
(x: Int) => x + 1
(a: Int, b: Int) => a + b
```

## Basic Usage

### Single Parameter

Define a simple transformation or predicate:

```
# Transformation lambda
(x) => x * 2

# Predicate lambda (returns Boolean)
(x) => x > 0
```

### Multiple Parameters

Lambdas can accept multiple parameters:

```
# Two-parameter lambda
(a, b) => a + b

# Comparison lambda
(x, y) => x > y
```

:::note Import Required
Remember to import the required stdlib namespaces (`stdlib.collection`, `stdlib.compare`, `stdlib.math`) before using higher-order functions and comparison operations in lambdas.
:::

## Higher-Order Functions

Lambda expressions are primarily used with higher-order functions from the standard library.

### filter

Keep elements that match a predicate:

```
use stdlib.collection

in numbers: List<Int>

# Keep only positive numbers
positives = filter(numbers, (x) => gt(x, 0))

# Keep numbers above 10
above10 = filter(numbers, (x) => gt(x, 10))

# Keep numbers at most 50
atMost50 = filter(numbers, (x) => lte(x, 50))

out positives
```

The predicate lambda must return `Boolean`. Elements where the predicate returns `true` are kept.

### map

Transform each element:

```
use stdlib.collection
use stdlib.math

in numbers: List<Int>

# Double each number
doubled = map(numbers, (x) => multiply(x, 2))

# Add 10 to each number
plus10 = map(numbers, (x) => add(x, 10))

# Triple each number
tripled = map(numbers, (x) => multiply(x, 3))

out doubled
```

The transformation lambda can return any type. The result is `List<T>` where `T` is the return type.

### all

Check if all elements satisfy a predicate:

```
use stdlib.collection

in numbers: List<Int>

# Check if all numbers are positive
allPositive = all(numbers, (x) => gt(x, 0))

# Check if all numbers are below 100
allBelow100 = all(numbers, (x) => lt(x, 100))

# Check if all numbers are non-negative
allNonNegative = all(numbers, (x) => gte(x, 0))

out allPositive
```

Returns `true` if the predicate is true for every element. Returns `true` for empty lists (vacuous truth).

### any

Check if any element satisfies a predicate:

```
use stdlib.collection

in numbers: List<Int>

# Check if any number is negative
hasNegative = any(numbers, (x) => lt(x, 0))

# Check if any number exceeds 100
hasAbove100 = any(numbers, (x) => gt(x, 100))

# Check if any number equals 42
has42 = any(numbers, (x) => eq-int(x, 42))

out hasNegative
```

Returns `true` if the predicate is true for at least one element. Returns `false` for empty lists.

## Chaining Operations

Combine higher-order functions for complex processing:

```
use stdlib.collection
use stdlib.math

in numbers: List<Int>

# Filter positive, then double them
positives = filter(numbers, (x) => gt(x, 0))
positivesDoubled = map(positives, (x) => multiply(x, 2))

# Check if all positives are below 50
positivesAllBelow50 = all(positives, (x) => lt(x, 50))

# Check if any doubled value exceeds 100
anyDoubledLarge = any(positivesDoubled, (x) => gt(x, 100))

out positivesDoubled
out positivesAllBelow50
out anyDoubledLarge
```

## Lambda with Comparison Functions

Since lambda bodies currently use function calls for comparisons, import comparison functions:

```
use stdlib.compare

# Comparison functions available:
# gt(a, b)  - greater than (a > b)
# lt(a, b)  - less than (a < b)
# gte(a, b) - greater than or equal (a >= b)
# lte(a, b) - less than or equal (a <= b)
# eq-int(a, b) - equality for integers
```

### Available Comparison Functions

| Function | Description | Example |
|----------|-------------|---------|
| `gt(a, b)` | Greater than | `(x) => gt(x, 0)` |
| `lt(a, b)` | Less than | `(x) => lt(x, 100)` |
| `gte(a, b)` | Greater than or equal | `(x) => gte(x, 5)` |
| `lte(a, b)` | Less than or equal | `(x) => lte(x, 50)` |
| `eq-int(a, b)` | Integer equality | `(x) => eq-int(x, 42)` |

## Lambda with Math Functions

Use math functions for transformations:

```
use stdlib.math

# Math functions available:
# add(a, b)      - addition
# subtract(a, b) - subtraction
# multiply(a, b) - multiplication
# divide(a, b)   - division
```

### Available Math Functions

| Function | Description | Example |
|----------|-------------|---------|
| `add(a, b)` | Addition | `(x) => add(x, 10)` |
| `subtract(a, b)` | Subtraction | `(x) => subtract(x, 5)` |
| `multiply(a, b)` | Multiplication | `(x) => multiply(x, 2)` |
| `divide(a, b)` | Division | `(x) => divide(x, 2)` |

## Practical Examples

### Data Filtering Pipeline

Filter and transform a list of items:

```
use stdlib.collection
use stdlib.compare
use stdlib.math

in scores: List<Int>
in threshold: Int

# Filter scores above threshold
passing = filter(scores, (x) => gte(x, threshold))

# Normalize passing scores (divide by 100)
normalized = map(passing, (x) => divide(x, 100))

# Check if all passing scores are valid (positive)
allValid = all(passing, (x) => gt(x, 0))

out passing
out normalized
out allValid
```

### Validation Checks

Validate list data with predicates:

```
use stdlib.collection
use stdlib.compare

in values: List<Int>

# Validation checks
noNegatives = all(values, (x) => gte(x, 0))
allInRange = all(values, (x) => lte(x, 100))
hasData = any(values, (x) => gt(x, 0))

# Combined validation
isValid = noNegatives and allInRange and hasData

out isValid
```

### Conditional Processing with Guards

Combine lambdas with guard expressions:

```
use stdlib.collection
use stdlib.compare

in items: List<Int>
in minCount: Int

# Only process if we have enough items
count = length(items)
filtered = filter(items, (x) => gt(x, 0)) when count > minCount

# Process filtered items only if filtering happened
result = filtered ?? items

out result
```

## Type Inference

Lambda parameter types are inferred from context:

```
use stdlib.collection

in numbers: List<Int>

# x is inferred as Int from List<Int>
doubled = map(numbers, (x) => multiply(x, 2))
```

Explicit type annotations are optional but can improve clarity:

```
# Explicit type annotation
doubled = map(numbers, (x: Int) => multiply(x, 2))
```

:::warning Current Limitations
Lambda expressions do not yet support closure capture (referencing variables from outer scope). Use literals directly in lambda bodies, or restructure your pipeline to pass values through function parameters.
:::

## Current Limitations

Lambda expressions have the following current limitations:

1. **Closure capture**: Lambda bodies currently support only the lambda parameter and literals. Referencing outer variables (closure capture) is not yet implemented.

```
in multiplier: Int
in numbers: List<Int>

# NOT YET SUPPORTED: capturing 'multiplier' in lambda
# scaled = map(numbers, (x) => multiply(x, multiplier))

# WORKAROUND: Use function calls that accept the value
scaled = map(numbers, (x) => multiply(x, 2))  # Use literal instead
```

2. **Function calls in predicates**: Comparisons in lambda bodies currently use function calls (`gt`, `lt`, etc.) rather than operators (`>`, `<`).

:::tip Keep It Simple
If your lambda logic becomes complex (nested calls, multiple operations), consider extracting it to a named module instead. Lambdas work best for simple predicates and transformations.
:::

## Best Practices

1. **Use meaningful parameter names**: `(item) => ...` is clearer than `(x) => ...` for complex operations
2. **Keep lambdas simple**: If logic is complex, consider a named module
3. **Chain operations**: Build pipelines by chaining filter, map, all, any
4. **Import required functions**: Remember to `use` stdlib namespaces for comparison and math functions
5. **Validate early**: Use `all` and `any` for early validation before expensive processing

## Related

- [Types](./types.md) — Understanding `List<T>` and collection types
- [Expressions](./expressions.md) — Expression syntax reference
- [Guards](./guards.md) — Combining lambdas with conditional execution
- [Orchestration Algebra](./orchestration-algebra.md) — Higher-order functions in pipelines