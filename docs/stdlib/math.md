# Math Functions

> **Path**: `docs/stdlib/math.md`
> **Parent**: [stdlib/](./README.md)

Arithmetic operations for integer and float values.

## Function Reference

| Function | Signature | Description |
|----------|-----------|-------------|
| `add` | `(a: Int, b: Int) -> Int` | Add two integers |
| `subtract` | `(a: Int, b: Int) -> Int` | Subtract b from a |
| `multiply` | `(a: Int, b: Int) -> Int` | Multiply two integers |
| `divide` | `(a: Int, b: Int) -> Int` | Integer division |
| `max` | `(a: Int, b: Int) -> Int` | Maximum of two values |
| `min` | `(a: Int, b: Int) -> Int` | Minimum of two values |
| `abs` | `(value: Int) -> Int` | Absolute value |
| `modulo` | `(a: Int, b: Int) -> Int` | Remainder after division |
| `round` | `(value: Float) -> Int` | Round float to integer |
| `negate` | `(value: Int) -> Int` | Negate a number |

## Basic Arithmetic

### add

Add two integers together.

```constellation
in x: Int
in y: Int
result = add(x, y)
out result
```

### subtract

Subtract the second integer from the first.

```constellation
in a: Int
in b: Int
diff = subtract(a, b)  # a - b
out diff
```

### multiply

Multiply two integers.

```constellation
in x: Int
in y: Int
product = multiply(x, y)
out product
```

### divide

Integer division (truncates toward zero).

```constellation
in numerator: Int
in denominator: Int
quotient = divide(numerator, denominator)
out quotient
```

**Error**: Raises `ArithmeticException` if denominator is zero.

## Min/Max Operations

### max

Return the larger of two integers.

```constellation
in a: Int
in b: Int
larger = max(a, b)
out larger
```

### min

Return the smaller of two integers.

```constellation
in a: Int
in b: Int
smaller = min(a, b)
out smaller
```

## Unary Operations

### abs

Return the absolute value.

```constellation
in value: Int
positive = abs(value)
out positive
```

### negate

Return the negation of a number.

```constellation
in value: Int
negated = negate(value)  # -value
out negated
```

### modulo

Return the remainder after division.

```constellation
in a: Int
in b: Int
remainder = modulo(a, b)
out remainder
```

**Error**: Raises `ArithmeticException` if b is zero.

### round

Round a float to the nearest integer.

```constellation
in measurement: Float
rounded = round(measurement)
out rounded
```

## Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `divide` | `divide(7, 3)` | `2` (truncates) |
| `divide` | `divide(-10, 3)` | `-3` (toward zero) |
| `modulo` | `modulo(6, 3)` | `0` |
| `abs` | `abs(-5)` | `5` |
| `abs` | `abs(0)` | `0` |
| `max` | `max(-10, -5)` | `-5` |
| `min` | `min(-10, -5)` | `-10` |
| `round` | `round(3.5)` | `4` |
| `round` | `round(-2.7)` | `-3` |

## Common Patterns

### Chained Operations

```constellation
in a: Int
in b: Int
in c: Int

# Compute (a + b) * c
sum = add(a, b)
result = multiply(sum, c)
out result
```

### Safe Division

```constellation
in numerator: Int
in denominator: Int

# Check for zero before dividing
isZero = eq-int(denominator, 0)
safeResult = if (isZero) 0 else divide(numerator, denominator)
out safeResult
```

## Performance

All math operations are O(1) pure functions with no memory allocation beyond the result.

## Namespace

All math functions are in the `stdlib.math` namespace.
