# Arithmetic & Comparison

> **Path**: `docs/language/expressions/arithmetic.md`
> **Parent**: [expressions/](./README.md)

Numeric and logical operations.

## Arithmetic Operators

| Operator | Operation | Example |
|----------|-----------|---------|
| `+` | Addition | `a + b` |
| `-` | Subtraction | `a - b` |
| `*` | Multiplication | `a * b` |
| `/` | Division | `a / b` |

```constellation
in x: Int
in y: Int

sum = x + y
diff = x - y
product = x * y
quotient = x / y
```

**Note:** `+` on records means merge, not addition. Use `add(a, b)` for explicit numeric addition if ambiguous.

## Comparison Operators

| Operator | Meaning | Result |
|----------|---------|--------|
| `==` | Equal | Boolean |
| `!=` | Not equal | Boolean |
| `>` | Greater than | Boolean |
| `<` | Less than | Boolean |
| `>=` | Greater or equal | Boolean |
| `<=` | Less or equal | Boolean |

```constellation
in score: Int

isHigh = score > 90
isLow = score < 50
isPassing = score >= 60
```

## Logical Operators

| Operator | Meaning | Example |
|----------|---------|---------|
| `and` | Logical AND | `a and b` |
| `or` | Logical OR | `a or b` |
| `not` | Logical NOT | `not a` |

```constellation
in user: User

canAccess = user.active and user.verified
needsReview = user.flagged or not user.verified
```

### Short-Circuit Evaluation

`and` and `or` short-circuit:
- `false and x` → `false` (x not evaluated)
- `true or x` → `true` (x not evaluated)

## Precedence

From highest to lowest:
1. `not`, unary `-`
2. `*`, `/`
3. `+`, `-`
4. `==`, `!=`, `<`, `>`, `<=`, `>=`
5. `and`
6. `or`

Use parentheses for clarity:

```constellation
result = (a > 0 and b > 0) or (c == 0)
```

## Stdlib Functions

For complex operations, use stdlib:

| Function | Description |
|----------|-------------|
| `add(a, b)` | Explicit addition |
| `max(a, b)` | Maximum value |
| `min(a, b)` | Minimum value |
| `abs(a)` | Absolute value |
| `modulo(a, b)` | Remainder |

See [stdlib/math.md](../../stdlib/math.md) for details.
