# constellation-lang Reference

constellation-lang is a domain-specific language for defining ML pipeline DAGs. It provides a declarative syntax with strong typing, type algebra, and field projections.

## Table of Contents

- [Program Structure](#program-structure)
- [Types](#types)
- [Declarations](#declarations)
- [Expressions](#expressions)
- [Type Algebra](#type-algebra)
- [Comments](#comments)
- [Complete Example](#complete-example)
- [Error Messages](#error-messages)

## Program Structure

A constellation-lang program consists of:

1. **Type definitions** (optional) - Define custom record types
2. **Input declarations** - Declare external inputs to the pipeline
3. **Assignments** - Compute intermediate values
4. **Output declaration** - Declare the pipeline output (exactly one required)

```
# Type definitions
type MyType = { field1: String, field2: Int }

# Input declarations
in inputName: TypeExpr

# Assignments
variable = expression

# Output (required, exactly one)
out expression
```

## Types

### Primitive Types

| Type | Description |
|------|-------------|
| `String` | Text values |
| `Int` | Integer numbers |
| `Float` | Floating-point numbers |
| `Boolean` | True/false values |

### Record Types

Record types define structured data with named fields:

```
type Person = {
  name: String,
  age: Int,
  email: String
}
```

Records can be nested:

```
type Order = {
  id: String,
  customer: { name: String, address: String },
  total: Float
}
```

### Parameterized Types

#### Candidates<T>

`Candidates<T>` represents a batch of items for ML processing. Operations on Candidates are applied element-wise:

```
type Item = { id: String, features: List<Float> }
in items: Candidates<Item>

# Operations on Candidates apply to each element
processed = transform(items)  # Returns Candidates<ProcessedItem>
```

#### List<T>

```
in tags: List<String>
in scores: List<Float>
```

#### Map<K, V>

```
in metadata: Map<String, Int>
in lookup: Map<Int, String>
```

### Type References

Previously defined types can be referenced by name:

```
type Base = { id: String }
type Extended = Base + { name: String }  # Type algebra

in data: Extended  # References the Extended type
```

## Declarations

### Type Definitions

```
type TypeName = TypeExpression
```

Examples:

```
type User = { id: Int, name: String }
type UserList = List<User>
type Merged = TypeA + TypeB
```

### Input Declarations

```
in variableName: TypeExpression
```

Examples:

```
in userId: Int
in query: String
in items: Candidates<{ id: String, score: Float }>
```

### Assignments

```
variableName = expression
```

Examples:

```
result = process(input)
merged = a + b
projected = data[field1, field2]
```

### Output Declaration

Every program must have exactly one output:

```
out expression
```

Examples:

```
out result
out items[id, score] + computed[rank]
```

## Expressions

### Variable References

Reference a previously declared variable:

```
in x: Int
y = x        # Reference x
out y
```

### Function Calls

Call registered functions (ML models, transformations):

```
result = function-name(arg1, arg2, ...)
```

Function names can include hyphens:

```
embeddings = ide-ranker-v2-embed(communications)
scores = compute-relevance-score(query, documents)
```

### Merge Expressions

Combine record types using `+`:

```
in a: { x: Int }
in b: { y: String }
merged = a + b  # Type: { x: Int, y: String }
```

See [Type Algebra](#type-algebra) for details.

### Projection Expressions

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

### Conditional Expressions

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

### Literals

```
stringVal = "hello world"
intVal = 42
floatVal = 3.14
boolVal = true
```

### Parentheses

Group expressions for clarity:

```
result = (a + b)[field1, field2]
```

## Type Algebra

The `+` operator merges record types. This is the core mechanism for combining data from different sources.

### Record + Record

Fields are merged; right-hand side wins on conflicts:

```
type A = { x: Int, y: Int }
type B = { y: String, z: String }
type C = A + B  # { x: Int, y: String, z: String }
```

### Candidates + Candidates

Element-wise merge of inner record types:

```
in items: Candidates<{ id: String }>
in scores: Candidates<{ score: Float }>
result = items + scores  # Candidates<{ id: String, score: Float }>
```

### Candidates + Record

Record is merged into each element:

```
in items: Candidates<{ id: String }>
in context: { userId: Int }
result = items + context  # Candidates<{ id: String, userId: Int }>
```

### Record + Candidates

Same as above; record fields are added to each element:

```
in context: { userId: Int }
in items: Candidates<{ id: String }>
result = context + items  # Candidates<{ userId: Int, id: String }>
```

### Incompatible Merges

Merging incompatible types produces a compile error:

```
in a: Int
in b: String
result = a + b  # Error: Cannot merge types: Int + String
```

## Comments

Line comments start with `#`:

```
# This is a comment
type User = { name: String }  # Inline comment

# Multi-line comments use multiple #
# Like this
in user: User
```

## Complete Example

```
# Communication ranking pipeline
# Ranks communications for a specific user

type Communication = {
  communicationId: String,
  contentBlocks: List<String>,
  channel: String
}

type EmbeddingResult = {
  embedding: List<Float>
}

type ScoreResult = {
  score: Float,
  rank: Int
}

# Pipeline inputs
in communications: Candidates<Communication>
in mappedUserId: Int

# Step 1: Generate embeddings for each communication
embeddings = ide-ranker-v2-candidate-embed(communications)

# Step 2: Compute relevance scores using embeddings and user context
scores = ide-ranker-v2-score(embeddings + communications, mappedUserId)

# Step 3: Select output fields and merge with scores
result = communications[communicationId, channel] + scores[score, rank]

# Pipeline output
out result
```

Output type: `Candidates<{ communicationId: String, channel: String, score: Float, rank: Int }>`

## Error Messages

constellation-lang provides precise error messages with line and column information.

### Undefined Variable

```
out undefined_var
```
```
Error at 1:5: Undefined variable: undefined_var
```

### Undefined Type

```
in x: NonExistent
```
```
Error at 1:6: Undefined type: NonExistent
```

### Undefined Function

```
result = unknown_func(x)
```
```
Error at 1:10: Undefined function: unknown_func
```

### Type Mismatch

```
# If function expects Int but receives String
result = expects_int(stringValue)
```
```
Error at 1:22: Type mismatch: expected Int, got String
```

### Invalid Projection

```
in data: { id: Int, name: String }
result = data[id, nonexistent]
```
```
Error at 2:10: Invalid projection: field 'nonexistent' not found. Available: id, name
```

### Incompatible Merge

```
in a: Int
in b: String
result = a + b
```
```
Error at 3:10: Cannot merge types: Int + String
```

### Parse Errors

```
in x: Int
out @invalid
```
```
Error at 2:5: Parse error: expected identifier
```
