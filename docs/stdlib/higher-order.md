# Higher-Order Functions

> **Path**: `docs/stdlib/higher-order.md`
> **Parent**: [stdlib/](./README.md)

Functions that accept lambda expressions for list processing.

## Function Reference

| Function | Signature | Description |
|----------|-----------|-------------|
| `filter` | `(List<Int>, (Int) => Boolean) -> List<Int>` | Keep matching elements |
| `map` | `(List<Int>, (Int) => Int) -> List<Int>` | Transform each element |
| `all` | `(List<Int>, (Int) => Boolean) -> Boolean` | Check all match |
| `any` | `(List<Int>, (Int) => Boolean) -> Boolean` | Check any match |

## Lambda Syntax

Lambdas use arrow syntax: `(param) => expression`

```constellation
# Single parameter
(x) => gt(x, 0)

# Expression can use any stdlib function
(n) => multiply(n, 2)
```

## filter

Keep only elements that satisfy the predicate.

```constellation
in numbers: List<Int>

# Keep positive numbers
positives = filter(numbers, (x) => gt(x, 0))
out positives
```

Example: `filter([1, -2, 3, -4], (x) => gt(x, 0))` returns `[1, 3]`.

## map

Transform each element using a function.

```constellation
in numbers: List<Int>

# Double each number
doubled = map(numbers, (x) => multiply(x, 2))
out doubled
```

Example: `map([1, 2, 3], (x) => multiply(x, 2))` returns `[2, 4, 6]`.

## all

Check if all elements satisfy the predicate.

```constellation
in numbers: List<Int>

# Are all numbers positive?
allPositive = all(numbers, (x) => gt(x, 0))
out allPositive
```

Example: `all([1, 2, 3], (x) => gt(x, 0))` returns `true`.

## any

Check if any element satisfies the predicate.

```constellation
in numbers: List<Int>

# Is any number negative?
hasNegative = any(numbers, (x) => lt(x, 0))
out hasNegative
```

Example: `any([1, -2, 3], (x) => lt(x, 0))` returns `true`.

## Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `filter` | Empty list | `[]` |
| `filter` | No matches | `[]` |
| `map` | Empty list | `[]` |
| `all` | Empty list | `true` (vacuous truth) |
| `any` | Empty list | `false` |

## Common Patterns

### Filter Then Transform

```constellation
in numbers: List<Int>

# Get absolute values of negative numbers
negatives = filter(numbers, (x) => lt(x, 0))
absolutes = map(negatives, (x) => abs(x))
out absolutes
```

### Validation

```constellation
in scores: List<Int>

# Check all scores are within valid range
aboveMin = all(scores, (x) => gte(x, 0))
belowMax = all(scores, (x) => lte(x, 100))
allValid = and(aboveMin, belowMax)
out allValid
```

### Finding Outliers

```constellation
in values: List<Int>
in threshold: Int

# Check if any value exceeds threshold
hasOutlier = any(values, (x) => gt(x, threshold))
out hasOutlier
```

### Data Transformation

```constellation
in rawScores: List<Int>

# Scale scores by 10 and filter out zeros
scaled = map(rawScores, (x) => multiply(x, 10))
nonZero = filter(scaled, (x) => gt(x, 0))
out nonZero
```

## Lambda Expression Examples

```constellation
# Comparison predicates
(x) => gt(x, 0)      # positive check
(x) => lt(x, 0)      # negative check
(x) => eq-int(x, 5)  # equality check
(x) => gte(x, 10)    # minimum threshold

# Math transforms
(x) => multiply(x, 2)  # double
(x) => add(x, 1)       # increment
(x) => abs(x)          # absolute value
(x) => negate(x)       # negation
```

## Implementation Note

Higher-order functions are processed specially by the compiler. They use `InlineTransform` at runtime (FilterTransform, MapTransform, etc.) rather than traditional Module implementations.

## Performance

| Function | Time | Space |
|----------|------|-------|
| `filter` | O(n) | O(k) matching |
| `map` | O(n) | O(n) |
| `all` | O(n) | O(1) |
| `any` | O(n) | O(1) |

`all` and `any` short-circuit: `all` stops on first `false`, `any` stops on first `true`.

## Error Guarantees

All higher-order functions are safe and never raise exceptions.

## Namespace

All higher-order functions are in the `stdlib.collection` namespace.
