# Type Algebra

> **Path**: `organon/features/type-safety/type-algebra.md`
> **Parent**: [type-safety/](./README.md)

Merge, projection, and element-wise operations for composing record types.

## Quick Example

```constellation
in user: { id: String, name: String }
in stats: { loginCount: Int, lastSeen: String }

# Merge: combine fields
combined = user + stats
# Result: { id: String, name: String, loginCount: Int, lastSeen: String }

# Projection: select fields
public = combined[id, name]
# Result: { id: String, name: String }

# Element-wise: operate on list elements
in users: List<{ id: String, name: String }>
names = users.name
# Result: List<String>
```

## Merge Operation (`+`)

Combine fields from two records into a single record.

### Basic Merge

```constellation
in user: { id: String, name: String }
in profile: { bio: String, avatar: String }

combined = user + profile
# Result: { id: String, name: String, bio: String, avatar: String }
```

### Field Conflicts

When both records have the same field, the right-hand side wins:

```constellation
in base: { name: String, value: Int }
in override: { value: Int, extra: String }

result = base + override
# result.value comes from override (right side wins)
# Result: { name: String, value: Int, extra: String }
```

### Chaining Merges

```constellation
a = GetA(id)  # { a1, a2 }
b = GetB(id)  # { b1, b2 }
c = GetC(id)  # { c1, c2 }

combined = a + b + c  # left-to-right: (a + b) + c
# Result: { a1, a2, b1, b2, c1, c2 }
```

### List + Record Merge

Merge a single record into each element of a list:

```constellation
in users: List<{ id: String, name: String }>
in defaults: { role: String, active: Boolean }

enriched = users + defaults
# Result: List<{ id: String, name: String, role: String, active: Boolean }>
```

### List + List Merge

Merge two lists of records element-wise (by position):

```constellation
in users: List<{ id: String, name: String }>
in profiles: List<{ bio: String }>

combined = users + profiles
# Result: List<{ id: String, name: String, bio: String }>
# Note: Lists must have same length at runtime
```

## Projection Operation (`[]`)

Select a subset of fields from a record.

### Basic Projection

```constellation
in user: { id: String, name: String, email: String, password: String }

public = user[id, name, email]
# Result: { id: String, name: String, email: String }
# 'password' is excluded
```

### Nested Field Projection

Projection flattens nested access:

```constellation
in data: {
  user: { id: String, name: String },
  meta: { created: String, updated: String }
}

flat = data[user.id, user.name, meta.created]
# Result: { id: String, name: String, created: String }
```

### Projection on Lists

Applied to a list, projection selects fields from each element:

```constellation
in users: List<{ id: String, name: String, email: String, internal: Boolean }>

publicUsers = users[id, name, email]
# Result: List<{ id: String, name: String, email: String }>
```

### After Merge

```constellation
combined = user + profile + settings
output = combined[id, name, theme]
# Select specific fields from merged result
```

## Element-Wise Field Access

When accessing a field on a list of records, the access is applied to each element.

### Basic Element-Wise

```constellation
in users: List<{ id: String, name: String, age: Int }>

ids = users.id      # List<String>
names = users.name  # List<String>
ages = users.age    # List<Int>
```

### Nested Element-Wise

```constellation
in orders: List<{
  id: String,
  customer: { name: String, email: String }
}>

customerNames = orders.customer.name  # List<String>
customerEmails = orders.customer.email  # List<String>
```

### After Projection

```constellation
in users: List<{ id: String, name: String, email: String }>

# Project then access
subset = users[id, name]
ids = subset.id  # List<String>

# Or chain directly
ids = users[id, name].id  # List<String>
```

## Type Inference

The compiler infers result types for all operations:

```constellation
# Merge inference
combined = a + b  # Type is union of a and b's fields

# Projection inference
subset = record[f1, f2]  # Type contains exactly f1, f2

# Element-wise inference
values = list.field  # Type is List<FieldType>
```

## Compile-Time Validation

Invalid operations are caught at compile time:

```constellation
in user: { id: String, name: String }

# Error: field 'email' not found in projection source
invalid = user[id, email]

# Error: cannot merge String + Record
invalid = user.name + user

# Error: field 'age' not found in { id: String, name: String }
invalid = user.age
```

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `core` | Runtime record representation | `modules/core/src/main/scala/io/constellation/TypeSystem.scala` |
| `lang-parser` | Parse merge (`+`) and projection (`[]`) syntax | `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala` |
| `lang-compiler` | Merge type computation | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala:374-398` (`mergeTypes`) |
| `lang-compiler` | Projection type checking | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala:470-502` |
| `lang-compiler` | Element-wise field access | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala:517-534` |
| `lang-compiler` | IR generation for operations | `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/IRGenerator.scala` |

## Implementation Details

### Merge Type Computation

From `TypeChecker.scala`:

```scala
def mergeTypes(left: SemanticType, right: SemanticType, span: Span): TypeResult[SemanticType] =
  (left, right) match {
    case (SemanticType.SRecord(lFields), SemanticType.SRecord(rFields)) =>
      // Right-hand side wins on conflicts
      SemanticType.SRecord(lFields ++ rFields).validNel

    case (SemanticType.SList(lElem), rRec: SemanticType.SRecord) =>
      // List + Record: add fields to each element
      mergeTypes(lElem, rRec, span).map(SemanticType.SList(_))

    // ... other cases
  }
```

### Projection Validation

From `TypeChecker.scala`:

```scala
private def checkProjection(
    requested: List[String],
    available: Map[String, SemanticType],
    span: Span
): TypeResult[Map[String, SemanticType]] =
  requested.traverse { field =>
    available.get(field)
      .toValidNel(CompileError.InvalidProjection(field, available.keys.toList, Some(span)))
      .map(field -> _)
  }.map(_.toMap)
```

### Element-Wise Inference

Field access on `List<Record>` returns `List<FieldType>`:

```scala
case SemanticType.SList(SemanticType.SRecord(availableFields)) =>
  availableFields.get(field.value) match {
    case Some(fieldType) =>
      TypedExpression.FieldAccess(
        typedSource, field.value,
        SemanticType.SList(fieldType),  // Wrapped in List
        span
      ).validNel
    // ...
  }
```

## Common Patterns

### Aggregating Service Calls

```constellation
in productId: String

inventory = GetInventory(productId)  # { stock, warehouse }
pricing = GetPricing(productId)      # { price, currency }
reviews = GetReviews(productId)      # { rating, count }

product = inventory + pricing + reviews
out product
# Output: { stock, warehouse, price, currency, rating, count }
```

### Filtering Fields for API Response

```constellation
in user: { id, name, email, passwordHash, createdAt, lastLogin, internal }

publicUser = user[id, name, email, createdAt]
out publicUser
# Only expose safe fields
```

### Extracting from Lists

```constellation
in orders: List<{ id, customer, items, total }>

orderIds = orders.id          # For batch lookup
totals = orders.total         # For aggregation
summary = orders[id, total]   # For lightweight response
```

## Best Practices

1. **Use merge for aggregation.** Combine multiple service calls into one response.
2. **Use projection for security.** Exclude sensitive fields before output.
3. **Prefer field access over projection for single fields.** `list.id` is cleaner than `list[id].id`.
4. **Document merge conflict resolution.** If both sides have same field, note which wins.

## Related

- [record-types.md](./record-types.md) - Record syntax and field access
- [optionals.md](./optionals.md) - Optional types in merge/projection
- [website/docs/language/expressions/type-ops.md](../../language/expressions/type-ops.md) - Language reference
