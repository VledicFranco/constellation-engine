# Expressions

## Variable References

Reference a previously declared variable:

```
in x: Int
y = x        # Reference x
out y
```

## Function Calls

Call registered functions (ML models, transformations):

```
result = function-name(arg1, arg2, ...)
```

Function names can include hyphens:

```
embeddings = ide-ranker-v2-embed(communications)
scores = compute-relevance-score(query, documents)
```

## Merge Expressions

Combine record types using `+`:

```
in a: { x: Int }
in b: { y: String }
merged = a + b  # Type: { x: Int, y: String }
```

See [Type Algebra](./type-algebra.md) for details.

## Projection Expressions

Select specific fields from a record:

```
in data: { id: Int, name: String, email: String, extra: String }
result = data[id, name, email]  # Type: { id: Int, name: String, email: String }
```

Projections work on Candidates element-wise:

```
in items: Candidates<{ id: String, score: Float, metadata: String }>
result = items[id, score]  # Type: Candidates<{ id: String, score: Float }>
```

## Comparison Expressions

Compare values using comparison operators:

```
in x: Int
in y: Int
isEqual = x == y     # Equality
isNotEqual = x != y  # Inequality
isLess = x < y       # Less than
isGreater = x > y    # Greater than
isLessEq = x <= y    # Less than or equal
isGreaterEq = x >= y # Greater than or equal
out isEqual
```

All comparison operators return `Boolean`.

**Supported comparisons:**

| Operator | Types | Description |
|----------|-------|-------------|
| `==` | Int, String | Equality |
| `!=` | Int, String | Inequality |
| `<` | Int | Less than |
| `>` | Int | Greater than |
| `<=` | Int | Less than or equal |
| `>=` | Int | Greater than or equal |

## Boolean Expressions

Combine boolean values using logical operators:

```
in isActive: Boolean
in hasPermission: Boolean
in score: Float

# Logical AND - both must be true
canAccess = isActive and hasPermission

# Logical OR - at least one must be true
isEligible = hasPermission or score >= 0.9

# Logical NOT - negates the value
isBlocked = not isActive

out canAccess
```

**Operator precedence (lowest to highest):**

1. `or` - Logical OR
2. `and` - Logical AND
3. `not` - Logical NOT
4. Comparison operators (`==`, `!=`, `<`, `>`, `<=`, `>=`)
5. Merge operator (`+`)

**Short-circuit evaluation:**

Boolean operators use short-circuit evaluation:
- `a and b`: If `a` is false, `b` is not evaluated
- `a or b`: If `a` is true, `b` is not evaluated

This is important when `b` involves expensive operations:

```
# If hasCache is true, expensiveComputation is never called
result = hasCache or expensiveComputation(data)
```

**Complex expressions:**

Use parentheses to control evaluation order:

```
# Without parentheses: a or (b and c)
result1 = a or b and c

# With parentheses: (a or b) and c
result2 = (a or b) and c
```

## Conditional Expressions

```
result = if (condition) thenExpr else elseExpr
```

Both branches must have the same type:

```
in flag: Boolean
in a: Int
in b: Int
result = if (flag) a else b  # Type: Int
```

## Literals

```
stringVal = "hello world"
intVal = 42
floatVal = 3.14
boolVal = true
```

## Parentheses

Group expressions for clarity:

```
result = (a + b)[field1, field2]
```
