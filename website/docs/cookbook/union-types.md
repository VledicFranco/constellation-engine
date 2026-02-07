---
title: "Union Types"
sidebar_position: 13
description: "Variant data types with union type declarations"
---

# Union Types

Declare types that can be one of several variants using the `A | B` union syntax.

## Use Case

You need to model API responses that can be either a success or an error, or identifiers that can be a string or a number.

## The Pipeline

```constellation
# union-types.cst

# Result type: success or error
type Result = { value: Int, status: String } | { error: String, code: Int }

# Flexible identifier
type Identifier = String | Int

# API response variants
type ApiResponse = { data: String, count: Int } | { error: String }

# Processing state variants
type ProcessingState = { pending: Boolean } | { completed: Int } | { failed: String }

# Inputs with union types
in result: Result
in userId: Identifier
in response: ApiResponse
in state: ProcessingState

@example("Operation failed - using fallback")
in fallbackMessage: String

@example(42)
in defaultValue: Int

# Pass through union-typed values
outputResult = result
outputUserId = userId
outputResponse = response
outputState = state

# Regular logic alongside union data
isHighDefault = defaultValue > 50
statusMessage = if (isHighDefault) "High value mode" else "Standard mode"

out outputResult
out outputUserId
out outputResponse
out outputState
out statusMessage
out fallbackMessage
```

## Explanation

| Declaration | Meaning |
|---|---|
| `type Result = A \| B` | A value of type `Result` is either an `A` or a `B` |
| `String \| Int` | Primitive union — value is a string or an integer |
| `{ data: ... } \| { error: ... }` | Record union — value matches one record shape |
| Three-way union | `A \| B \| C` — value matches any of three shapes |

Union types are useful for modeling variant data from external APIs where the response shape depends on success or failure.

## Pattern Matching

Use `match` expressions to discriminate between union variants:

```constellation
type Result = { value: Int, status: String } | { error: String, code: Int }
in response: Result

message = match response {
  { value, status } -> "Success: ${value}",
  { error, code } -> "Error ${code}: ${error}"
}
out message
```

### How It Works

| Pattern | Matches When |
|---------|--------------|
| `{ field1, field2 }` | Record has exactly these fields |
| `_` | Any value (wildcard) |

The compiler verifies exhaustiveness at compile time. If any union variant is not covered, you get a compile error:

```
TypeError: Non-exhaustive match on type Result
  Missing patterns for: { error: String, code: Int }
```

### Field Binding

Pattern fields become variables in the match body:

```constellation
type User = { name: String, age: Int } | { error: String }
in user: User

greeting = match user {
  { name, age } -> "Hello ${name}, you are ${age} years old",
  { error } -> "Error: ${error}"
}
out greeting
```

### Wildcard Patterns

Use `_` to match any remaining variants:

```constellation
type State = { active: Boolean } | { pending: Int } | { banned: String }
in state: State

isActive = match state {
  { active } -> active,
  _ -> false
}
out isActive
```

:::warning Exhaustiveness Required
All union variants must be covered by patterns. The compiler will error if any variant is unmatched unless you include a wildcard pattern.
:::

## Running the Example

### Input
```json
{
  "result": { "value": 42, "status": "ok" },
  "userId": "user-123",
  "response": { "data": "hello", "count": 1 },
  "state": { "completed": 100 },
  "fallbackMessage": "Operation failed - using fallback",
  "defaultValue": 42
}
```

### Expected Output
```json
{
  "outputResult": { "value": 42, "status": "ok" },
  "outputUserId": "user-123",
  "outputResponse": { "data": "hello", "count": 1 },
  "outputState": { "completed": 100 },
  "statusMessage": "Standard mode",
  "fallbackMessage": "Operation failed - using fallback"
}
```

## Variations

### Scoring result union

```constellation
type ScoringResult = { score: Int, confidence: Float } | { reason: String }

in input: ScoringResult

out input
```

:::tip
Name your union types at the top of the file. `type Result = Success | Error` is much clearer than repeating `{ value: Int } | { error: String }` everywhere.
:::

## Best Practices

1. **Use unions for variant returns** — API responses, parsing results, and validation outcomes naturally have multiple shapes
2. **Name your union types** — `type Result = Success | Error` is clearer than inline unions everywhere
3. **Plan for pattern matching** — structure your union variants so they'll be easy to discriminate when pattern matching is available

## Related Examples

- [Record Types](record-types.md) — single record type definitions
- [Optional Types](optional-types.md) — optional values (a specific kind of variant)
