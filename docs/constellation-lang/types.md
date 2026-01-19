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

### Candidates<T>

`Candidates<T>` represents a batch of items for ML processing. Operations on Candidates are applied element-wise:

```
type Item = { id: String, features: List<Float> }
in items: Candidates<Item>

# Operations on Candidates apply to each element
processed = transform(items)  # Returns Candidates<ProcessedItem>
```

### List<T>

```
in tags: List<String>
in scores: List<Float>
```

### Map<K, V>

```
in metadata: Map<String, Int>
in lookup: Map<Int, String>
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
# A model that can return different output types
type ModelOutput = {
  prediction: Float,
  confidence: Float
} | {
  error: String,
  code: Int
}

in modelResult: ModelOutput
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
