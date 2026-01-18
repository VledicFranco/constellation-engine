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
