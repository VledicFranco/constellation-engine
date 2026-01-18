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

## Type References

Previously defined types can be referenced by name:

```
type Base = { id: String }
type Extended = Base + { name: String }  # Type algebra

in data: Extended  # References the Extended type
```
