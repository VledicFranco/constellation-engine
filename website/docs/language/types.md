---
title: "Types"
sidebar_position: 3
---

# Types

## Primitive Types

| Type | Description |
|------|-------------|
| `String` | Text values |
| `Int` | Integer numbers |
| `Float` | Floating-point numbers |
| `Boolean` | True/false values |

## Record Types

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

## Parameterized Types

### List<T>

Lists are ordered collections that support element-wise operations when containing records:

```
in tags: List<String>
in scores: List<Float>
```

**Element-wise operations on `List<Record>`:**

When a list contains record elements, merge, projection, and field access operations apply to each element automatically:

```
type Item = { id: String, price: Float }
in items: List<Item>
in context: { currency: String }

# Merge adds context to EACH item
enriched = items + context  # Type: List<{ id: String, price: Float, currency: String }>

# Projection selects fields from EACH item
selected = items[id]  # Type: List<{ id: String }>

# Field access extracts field from EACH item
ids = items.id  # Type: List<String>
```

### Map<K, V>

```
in metadata: Map<String, Int>
in lookup: Map<Int, String>
```

### Candidates<T> (Legacy Alias)

`Candidates<T>` is a legacy alias for `List<T>`. It is fully supported for backwards compatibility but new code should use `List<T>` directly:

```
# These are equivalent:
in items: Candidates<{ id: String }>
in items: List<{ id: String }>
```

## Union Types

Union types represent values that can be one of several types. Use the `|` operator to create union types:

```
type Result = { value: Int } | { error: String }

in response: Result
```

Union types are useful for:

- **Variant returns**: Functions that can return different result types
- **Error handling**: Representing success/failure cases
- **Discriminated unions**: Type-safe handling of multiple cases

```
# A service call that can return different output types
type ServiceResponse = {
  data: String,
  status: Int
} | {
  error: String,
  code: Int
}

in apiResult: ServiceResponse
```

Union types can combine primitive types:

```
type StringOrInt = String | Int
in flexible: StringOrInt
```

## Optional Type

`Optional<T>` represents values that may or may not be present. Use with guard expressions and coalesce operators:

```
in maybeValue: Optional<Int>

# Coalesce to provide fallback
result = maybeValue ?? 0

# Guard expressions produce Optional types
guarded = expensiveOp(data) when condition  # Type: Optional<Result>
```

Optional interacts with the orchestration algebra:
- Guard expressions (`when`) produce `Optional<T>` results
- Coalesce (`??`) unwraps optionals with fallbacks
- Branch expressions can handle optional cases

## Type References

Previously defined types can be referenced by name:

```
type Base = { id: String }
type Extended = Base + { name: String }  # Type algebra

in data: Extended  # References the Extended type
```
