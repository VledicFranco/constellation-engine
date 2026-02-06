---
title: "Type Algebra"
sidebar_position: 6
description: "Use the + operator to merge record types and combine data from multiple sources in your pipelines."
---

# Type Algebra

:::note
Type algebra is an advanced topic. Make sure you understand [Types](./types.md) first, especially record types and structural subtyping.
:::

The `+` operator merges record types. This is the core mechanism for combining data from different sources.

## Quick Reference

| Operator | Syntax | What it Does |
|----------|--------|--------------|
| Merge | `A + B` | Combine all fields from both records |
| Projection | `record{field1, field2}` | Select specific fields from a record |

## Common Patterns

### Enriching Data

Combine data from multiple sources:

```constellation
user = GetUser(id)
profile = GetProfile(id)
enriched = user + profile  # Has all fields from both
```

### Selecting Fields for Output

Pick only the fields you need:

```constellation
out enriched{name, email, tier}  # Only these 3 fields
```

### Combining Both

Merge then project for clean API responses:

```constellation
combined = order + customer + shipping
out combined{orderId, customerName, estimatedDelivery}
```

### Adding Context to Collections

Broadcast a record into every element of a collection:

```constellation
in items: Candidates<{ id: String, name: String }>
in context: { requestId: String, timestamp: Int }
result = items + context  # Each item now has requestId and timestamp
```

---

## Detailed Reference

## Record + Record

:::warning
When merging records, the right-hand side wins on field conflicts. In `A + B`, if both have a `y` field, `B`'s type for `y` will be used. This is intentional but can be surprising.
:::

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

:::tip
Use merge (`+`) when you need to **add** fields to a record. Use projection (`{}`) when you need to **select** specific fields. Both are essential tools for reshaping data in pipelines.
:::

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
