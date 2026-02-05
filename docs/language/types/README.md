# Type System

> **Path**: `docs/language/types/`
> **Parent**: [language/](../README.md)

Constellation's type system for compile-time validation.

## Contents

| File | Description |
|------|-------------|
| [primitives.md](./primitives.md) | String, Int, Float, Boolean |
| [records.md](./records.md) | Structured data with named fields |
| [lists.md](./lists.md) | Ordered collections, element-wise operations |
| [optionals.md](./optionals.md) | Maybe-present values, guards, coalesce |
| [unions.md](./unions.md) | Tagged variants for sum types |

## Type Summary

| Type | Syntax | Example Value |
|------|--------|---------------|
| String | `String` | `"hello"` |
| Int | `Int` | `42` |
| Float | `Float` | `3.14` |
| Boolean | `Boolean` | `true` |
| List | `List<T>` | `[1, 2, 3]` |
| Record | `{ field: Type }` | `{ name: "Alice", age: 30 }` |
| Optional | `Optional<T>` | present or absent |
| Union | `A \| B` | tagged variant |
| Map | `Map<K, V>` | key-value pairs |

## Type Operations

| Operation | Operator | Types | Result |
|-----------|----------|-------|--------|
| Merge | `a + b` | Record + Record | Combined fields |
| Projection | `r[f1, f2]` | Record | Subset of fields |
| Element-wise | `list.field` | List<Record> | List<FieldType> |
| Guard | `e when c` | T when Boolean | Optional<T> |
| Coalesce | `o ?? d` | Optional<T> ?? T | T |

## Type Aliases

```constellation
type UserId = String
type User = { id: UserId, name: String, email: String }
type UserList = List<User>
```

Aliases are purely syntactic; they expand at compile time.
