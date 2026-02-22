---
title: "Lambda Expressions"
sidebar_position: 10
description: "Define inline functions with lambda expressions for filter, map, all, and any operations on lists."
---

# Lambda Expressions

Lambda expressions define inline functions for use with higher-order functions. They enable functional-style list processing with operations like `filter`, `map`, `all`, and `any`.

## Three Levels of Ceremony

Constellation offers three equivalent ways to write lambda arguments, from most concise to most explicit:

| Form | Example | When to use |
|------|---------|-------------|
| **Infix + `it`** | `numbers filter it > 0` | Simple single-param predicates and transforms |
| **Prefix + `it`** | `filter(numbers, it > 0)` | Single-param lambdas in prefix call style |
| **Explicit lambda** | `filter(numbers, (x) => x > 0)` | Multi-param lambdas, nested HOFs, or when naming the parameter aids clarity |

All three compile to the same IR. Choose based on readability.

## Syntax

### Explicit Lambda

```
(parameter) => expression
(param1, param2) => expression
```

Parameters can optionally include type annotations:

```
(x: Int) => x + 1
(a: Int, b: Int) => a + b
```

### Implicit `it` Parameter

When a higher-order function expects a single-parameter lambda, you can write the body expression directly using `it` as the parameter name:

```
filter(numbers, it > 0)        # equivalent to: filter(numbers, (it) => it > 0)
map(numbers, it * 2)           # equivalent to: map(numbers, (it) => it * 2)
```

The type checker auto-wraps the expression in a lambda when:
1. The expected argument type is a single-parameter function
2. The expression contains a free reference to `it`
3. `it` is not already bound as a variable in the enclosing scope

### Infix HOF Syntax

`filter`, `map`, `all`, and `any` can be used as infix operators between a collection and a predicate/transform:

```
numbers filter it > 0          # equivalent to: filter(numbers, it > 0)
numbers map it * 2             # equivalent to: map(numbers, it * 2)
numbers all it >= 0            # equivalent to: all(numbers, it >= 0)
numbers any it < 0             # equivalent to: any(numbers, it < 0)
```

Infix HOF calls are **left-associative** and can be chained:

```
numbers filter it > 0 map it * 2
# parses as: map(filter(numbers, it > 0), it * 2)
```

:::note Soft Keywords
`filter`, `map`, `all`, and `any` are soft keywords — they are only recognized as infix operators at word boundaries. Names like `filterBy` or `mapping` remain valid identifiers.
:::

## Higher-Order Functions

Lambda expressions are primarily used with higher-order functions from the standard library.

### filter

Keep elements that match a predicate:

```constellation
use stdlib.collection

in numbers: List<Int>

# Infix style (recommended)
positives = numbers filter it > 0

# Prefix with implicit it
above10 = filter(numbers, it > 10)

# Explicit lambda (always works)
atMost50 = filter(numbers, (x) => x <= 50)

out positives
```

The predicate must return `Boolean`. Elements where the predicate returns `true` are kept.

### map

Transform each element:

```constellation
use stdlib.collection

in numbers: List<Int>

# Infix style
doubled = numbers map it * 2

# Prefix with implicit it
plus10 = map(numbers, it + 10)

# Explicit lambda
tripled = map(numbers, (x) => x * 3)

out doubled
```

The transformation lambda can return any type. The result is `List<T>` where `T` is the return type.

### all

Check if all elements satisfy a predicate:

```constellation
use stdlib.collection

in numbers: List<Int>

allPositive = numbers all it > 0
allBelow100 = numbers all it < 100

out allPositive
```

Returns `true` if the predicate is true for every element. Returns `true` for empty lists (vacuous truth).

### any

Check if any element satisfies a predicate:

```constellation
use stdlib.collection

in numbers: List<Int>

hasNegative = numbers any it < 0
hasAbove100 = numbers any it > 100

out hasNegative
```

Returns `true` if the predicate is true for at least one element. Returns `false` for empty lists.

## Chaining Operations

Infix HOF syntax enables natural left-to-right pipelines:

```constellation
use stdlib.collection

in numbers: List<Int>

# Filter positive numbers, then double them — reads left to right
doubledPositives = numbers filter it > 0 map it * 2

# Equivalent nested prefix form
doubledPositives2 = map(filter(numbers, it > 0), it * 2)

out doubledPositives
```

For multi-step pipelines, intermediate variables can improve readability:

```constellation
use stdlib.collection

in numbers: List<Int>
in threshold: Int

positives = numbers filter it > 0
aboveThreshold = filter(positives, it > threshold)
scaled = aboveThreshold map it * 10

out scaled
```

## Closure Capture

Lambdas can reference variables from the enclosing scope. Captured variables are snapshotted by value at execution time:

```constellation
use stdlib.collection

in numbers: List<Int>
in threshold: Int
in factor: Int

# `threshold` is captured from outer scope
aboveThreshold = numbers filter it > threshold

# `factor` is captured from outer scope
scaled = numbers map it * factor

out aboveThreshold
out scaled
```

Capture works with all three lambda forms:

```constellation
# Infix + it
result1 = numbers filter it > threshold

# Prefix + it
result2 = filter(numbers, it > threshold)

# Explicit lambda
result3 = filter(numbers, (x) => x > threshold)
```

## Lambda with Operators

Lambda bodies support the full expression grammar — arithmetic, comparison, boolean, and field access operators:

### Arithmetic Operators

| Operator | Example |
|----------|---------|
| `+` | `numbers map it + 10` |
| `-` | `numbers map it - 5` |
| `*` | `numbers map it * 2` |
| `/` | `numbers map it / 2` |

### Comparison Operators

| Operator | Example |
|----------|---------|
| `>` | `numbers filter it > 0` |
| `<` | `numbers filter it < 100` |
| `>=` | `numbers filter it >= 5` |
| `<=` | `numbers filter it <= 50` |
| `==` | `numbers filter it == 42` |
| `!=` | `numbers filter it != 0` |

### Stdlib Comparison Functions

The function-call comparison style (`gt`, `lt`, etc.) still works and is available for backwards compatibility:

```constellation
use stdlib.compare

# These are equivalent:
positives1 = filter(numbers, it > 0)
positives2 = filter(numbers, (x) => gt(x, 0))
```

## Practical Examples

### Data Filtering Pipeline

```constellation
use stdlib.collection

in scores: List<Int>
in threshold: Int

passing = scores filter it >= threshold
normalized = passing map it / 100
allValid = passing all it > 0

out passing
out normalized
out allValid
```

### Validation Checks

```constellation
use stdlib.collection

in values: List<Int>

noNegatives = values all it >= 0
allInRange = values all it <= 100
hasData = values any it > 0
isValid = noNegatives and allInRange and hasData

out isValid
```

### Conditional Processing with Guards

```constellation
use stdlib.collection

in items: List<Int>
in minCount: Int

count = list-length(items)
filtered = (items filter it > 0) when count > minCount
result = filtered ?? items

out result
```

## Type Inference

Lambda parameter types are inferred from context:

```constellation
use stdlib.collection

in numbers: List<Int>

# x is inferred as Int from List<Int>
doubled = numbers map it * 2

# Explicit type annotation is optional
doubled2 = map(numbers, (x: Int) => x * 2)
```

## Edge Cases

### `it` as a Regular Variable

If `it` is already bound in scope (e.g., as an input), the implicit lifting does **not** apply — `it` is treated as a normal variable reference:

```constellation
in it: Int
in numbers: List<Int>

# Here `it` refers to the input variable, NOT the implicit parameter.
# This would need an explicit lambda:
filtered = filter(numbers, (x) => x > it)
```

### Nested HOFs

In nested HOF calls, each `it` binds to its immediately enclosing HOF:

```constellation
# The inner `it` belongs to the inner `any` call.
# The outer `filter` needs an explicit lambda:
result = filter(numbers, (x) => any(items, it == x))
```

## Best Practices

1. **Use infix + `it` for simple operations** — `numbers filter it > 0` reads naturally
2. **Use explicit lambdas for complex logic** — if the body needs multiple operations or named parameters for clarity
3. **Chain left-to-right** — `numbers filter it > 0 map it * 2` reads better than nested calls
4. **Capture freely** — closures are well-supported; reference outer variables without ceremony
5. **Validate early** — use `all` and `any` for validation before expensive processing

## Related

- [Types](./types.md) — Understanding `List<T>` and collection types
- [Expressions](./expressions.md) — Expression syntax reference
- [Guards](./guards.md) — Combining lambdas with conditional execution
- [Orchestration Algebra](./orchestration-algebra.md) — Higher-order functions in pipelines
