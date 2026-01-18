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

## Arithmetic Expressions

Perform arithmetic operations on numeric values:

```
in a: Int
in b: Int

sum = a + b        # Addition
diff = a - b       # Subtraction
product = a * b    # Multiplication
quotient = a / b   # Division
```

Arithmetic operators work with `Int` and `Float` types:

```
in x: Float
in y: Float

result = x * y + 1.5
```

**Operator Precedence:**
- `*` and `/` have higher precedence than `+` and `-`
- Use parentheses to control evaluation order: `(a + b) * c`

## Comparison Expressions

Compare values and produce Boolean results:

```
in a: Int
in b: Int

isEqual = a == b       # Equality
isNotEqual = a != b    # Inequality
isGreater = a > b      # Greater than
isLess = a < b         # Less than
isGte = a >= b         # Greater than or equal
isLte = a <= b         # Less than or equal
```

Comparisons work with numeric types and return `Boolean`:

```
in score: Int
in threshold: Int

passed = score >= threshold
result = if (passed) score else 0
```

## Field Access Expressions

Access individual fields from a record using dot notation:

```
in user: { id: Int, name: String, email: String }

userId = user.id        # Type: Int
userName = user.name    # Type: String
```

Field access works on `Candidates` element-wise:

```
in items: Candidates<{ id: String, score: Float }>

ids = items.id          # Type: Candidates<String>
scores = items.score    # Type: Candidates<Float>
```

**Note:** Field access (`.field`) extracts a single field's value, while projection (`[field1, field2]`) creates a new record with selected fields.

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
