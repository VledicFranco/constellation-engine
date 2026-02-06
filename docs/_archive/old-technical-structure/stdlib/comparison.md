# Comparison Functions

> **Path**: `docs/stdlib/comparison.md`
> **Parent**: [stdlib/](./README.md)

Equality and ordering functions for comparisons.

## Function Reference

| Function | Signature | Description |
|----------|-----------|-------------|
| `eq-int` | `(a: Int, b: Int) -> Boolean` | Integer equality |
| `eq-string` | `(a: String, b: String) -> Boolean` | String equality |
| `gt` | `(a: Int, b: Int) -> Boolean` | Greater than |
| `lt` | `(a: Int, b: Int) -> Boolean` | Less than |
| `gte` | `(a: Int, b: Int) -> Boolean` | Greater or equal |
| `lte` | `(a: Int, b: Int) -> Boolean` | Less or equal |

## Equality Functions

### eq-int

Check if two integers are equal.

```constellation
in a: Int
in b: Int
isEqual = eq-int(a, b)
out isEqual
```

### eq-string

Check if two strings are equal (case-sensitive).

```constellation
in expected: String
in actual: String
matches = eq-string(expected, actual)
out matches
```

Note: Comparison is case-sensitive. `eq-string("Hello", "hello")` returns `false`.

## Ordering Functions

### gt (greater than)

Check if the first integer is greater than the second.

```constellation
in score: Int
in threshold: Int
isAbove = gt(score, threshold)
out isAbove
```

### lt (less than)

Check if the first integer is less than the second.

```constellation
in value: Int
in limit: Int
isBelow = lt(value, limit)
out isBelow
```

### gte (greater or equal)

Check if the first integer is greater than or equal to the second.

```constellation
in age: Int
in minAge: Int
isOldEnough = gte(age, minAge)
out isOldEnough
```

### lte (less or equal)

Check if the first integer is less than or equal to the second.

```constellation
in count: Int
in maxCount: Int
isWithinLimit = lte(count, maxCount)
out isWithinLimit
```

## Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `eq-int` | `eq-int(0, 0)` | `true` |
| `eq-int` | `eq-int(-5, -5)` | `true` |
| `eq-string` | `eq-string("", "")` | `true` |
| `eq-string` | `eq-string("Hello", "hello")` | `false` |
| `eq-string` | Unicode strings | Fully supported |
| `gt` | `gt(5, 5)` | `false` |
| `gt` | `gt(-5, -10)` | `true` |
| `lt` | `lt(5, 5)` | `false` |
| `lt` | `lt(-10, -5)` | `true` |
| `gte` | `gte(5, 5)` | `true` |
| `lte` | `lte(5, 5)` | `true` |

## Common Patterns

### Conditional Logic

```constellation
in score: Int
in threshold: Int

isPass = gt(score, threshold)
result = if (isPass) "PASS" else "FAIL"
out result
```

### Range Checking

```constellation
in value: Int
in min: Int
in max: Int

aboveMin = gte(value, min)
belowMax = lte(value, max)
inRange = and(aboveMin, belowMax)
out inRange
```

### Safe Division Guard

```constellation
in numerator: Int
in denominator: Int

isZero = eq-int(denominator, 0)
safeResult = if (isZero) 0 else divide(numerator, denominator)
out safeResult
```

### String Matching

```constellation
in input: String
in expected: String

matches = eq-string(input, expected)
out matches
```

## Combining with Boolean Operations

```constellation
in age: Int
in hasPermission: Boolean

isAdult = gte(age, 18)
canAccess = and(isAdult, hasPermission)
out canAccess
```

## Performance

| Function | Time Complexity |
|----------|-----------------|
| `eq-int` | O(1) |
| `eq-string` | O(min(n,m)) |
| `gt` | O(1) |
| `lt` | O(1) |
| `gte` | O(1) |
| `lte` | O(1) |

## Error Guarantees

All comparison operations are safe and never raise exceptions.

## Namespace

All comparison functions are in the `stdlib.compare` namespace.
