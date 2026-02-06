# Type Algebra

The `+` operator merges record types. This is the core mechanism for combining data from different sources.

## Record + Record

Fields are merged; right-hand side wins on conflicts:

```
type A = { x: Int, y: Int }
type B = { y: String, z: String }
type C = A + B  # { x: Int, y: String, z: String }
```

## Candidates + Candidates

Element-wise merge of inner record types:

```
in items: Candidates<{ id: String }>
in scores: Candidates<{ score: Float }>
result = items + scores  # Candidates<{ id: String, score: Float }>
```

## Candidates + Record

Record is merged into each element:

```
in items: Candidates<{ id: String }>
in context: { userId: Int }
result = items + context  # Candidates<{ id: String, userId: Int }>
```

## Record + Candidates

Same as above; record fields are added to each element:

```
in context: { userId: Int }
in items: Candidates<{ id: String }>
result = context + items  # Candidates<{ userId: Int, id: String }>
```

## Record Projection

Select a subset of fields from a record using `{}` syntax:

```
record = {a: Int, b: Int, c: Int}
record{a,c}  # {a: Int, c: Int}
```

## Incompatible Merges

Merging incompatible types produces a compile error:

```
in a: Int
in b: String
result = a + b  # Error: Cannot merge types: Int + String
```
