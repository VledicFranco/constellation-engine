---
title: "Type Algebra"
sidebar_position: 3
description: "Record merging and projection with type algebra operators"
---

# Type Algebra

Merge records with `+` and select fields with `[]` — Constellation's type algebra operators.

## Use Case

You have data from two sources with overlapping fields and need to combine them, then extract a subset.

## The Pipeline

```constellation
# type-algebra.cst - Record merging and projections

# Define record types
type Base = { id: Int, name: String }
type Extra = { name: String, score: Float }

in base: Base
in extra: Extra

# Merge records (right side wins on conflicts)
# Result type: { id: Int, name: String, score: Float }
merged = base + extra

# Project specific fields
# Result type: { id: Int, score: Float }
summary = merged[id, score]

out merged
out summary
```

## Explanation

| Step | Expression | Result Type | Purpose |
|---|---|---|---|
| 1 | `base + extra` | `{ id: Int, name: String, score: Float }` | Merge two records — `extra.name` overwrites `base.name` |
| 2 | `merged[id, score]` | `{ id: Int, score: Float }` | Select only `id` and `score` fields |

The `+` operator performs a right-biased merge: when both records have a field with the same name, the right operand's value wins. The `[]` operator projects (selects) specific fields — the compiler verifies that every listed field exists.

## Running the Example

### Input
```json
{
  "base": { "id": 1, "name": "Alice" },
  "extra": { "name": "Alice Smith", "score": 95.5 }
}
```

### Expected Output
```json
{
  "merged": { "id": 1, "name": "Alice Smith", "score": 95.5 },
  "summary": { "id": 1, "score": 95.5 }
}
```

## Variations

### Multi-source merge

```constellation
type A = { id: Int, name: String }
type B = { email: String }
type C = { role: String, active: Boolean }

in a: A
in b: B
in c: C

full = a + b + c
contact = full[name, email]

out full
out contact
```

### Merge then process

```constellation
type Profile = { name: String, age: Int }
type Stats = { score: Int, rank: Int }

in profile: Profile
in stats: Stats

combined = profile + stats
display_name = Uppercase(combined.name)
is_top = gt(combined.rank, 0)

out display_name
out is_top
```

## Best Practices

1. **Be aware of right-bias** — when merging `a + b`, fields from `b` overwrite same-named fields from `a`
2. **Use projection to narrow types** — projecting after a merge keeps your output shape explicit
3. **Chain merges for multiple sources** — `a + b + c` merges left to right

## Related Examples

- [Record Types](record-types.md) — field access basics
- [Candidates Batch](candidates-batch.md) — type algebra on batch data
- [Fan-Out / Fan-In](fan-out-fan-in.md) — merge API responses
