---
title: "Record Types"
sidebar_position: 2
description: "Working with structured record types and field access"
---

# Record Types

Define structured data types and access their fields with compile-time validation.

## Use Case

You receive structured user data and need to extract and process individual fields.

## The Pipeline

```constellation
# simple-pipeline.cst - Working with record types

# Define a record type for user data
type User = {
  id: Int,
  name: String,
  email: String,
  score: Int
}

# Input: a single user record
in user: User

# Extract and process fields
user_name = trim(user.name)
is_high_score = gt(user.score, 100)

# Output the results
out user_name
out is_high_score
```

## Explanation

| Step | Expression | Purpose |
|---|---|---|
| 1 | `type User = { ... }` | Defines a record type with four typed fields |
| 2 | `in user: User` | Declares input using the custom type |
| 3 | `user.name` | Accesses the `name` field — compiler verifies it exists |
| 4 | `gt(user.score, 100)` | Compares `score` to a threshold |

Field access is validated at compile time. Writing `user.nme` would produce a compile error: `field 'nme' not found. Did you mean 'name'?`

## Running the Example

### Input
```json
{
  "user": {
    "id": 1,
    "name": "  Alice  ",
    "email": "alice@example.com",
    "score": 150
  }
}
```

### Expected Output
```json
{
  "user_name": "Alice",
  "is_high_score": true
}
```

## Variations

### Nested processing

```constellation
type User = { name: String, email: String, score: Int }

in user: User

upper_name = Uppercase(user.name)
name_length = TextLength(user.name)

out upper_name
out name_length
```

### Multiple record types

```constellation
type User = { name: String, role: String }
type Config = { maxRetries: Int, timeout: Int }

in user: User
in config: Config

greeting = concat("Welcome, ", user.name)
totalWait = config.maxRetries * config.timeout

out greeting
out totalWait
```

## Best Practices

1. **Define types at the top** — type definitions should appear before they are used
2. **Use descriptive field names** — field names become part of the compile-time contract
3. **Prefer record types over loose inputs** — grouping related fields into a record makes pipelines more maintainable

## Related Examples

- [Type Algebra](type-algebra.md) — merge and project records
- [Candidates Batch](candidates-batch.md) — records in batch processing
- [Lead Scoring](lead-scoring.md) — complex record processing
