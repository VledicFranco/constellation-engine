# Primitive Types

> **Path**: `docs/language/types/primitives.md`
> **Parent**: [types/](./README.md)

## Types

| Type | Description | Example Literals |
|------|-------------|------------------|
| `String` | Unicode text | `"hello"`, `"user-123"` |
| `Int` | 64-bit signed integer | `42`, `-1`, `0` |
| `Float` | 64-bit floating point | `3.14`, `-0.5`, `1.0` |
| `Boolean` | True or false | `true`, `false` |

## Usage

```constellation
in name: String
in count: Int
in price: Float
in active: Boolean
```

## Input Annotations

Use `@example` to provide sample values for IDE pre-population:

```constellation
in userId: String @example("user-123")
in quantity: Int @example(5)
in rate: Float @example(0.15)
in enabled: Boolean @example(true)
```

## Operations

### String
- Concatenation via `concat(a, b)` stdlib function
- Length via `string-length(s)`
- See [stdlib/string.md](../../stdlib/string.md)

### Numeric (Int, Float)
- Arithmetic: `add`, `subtract`, `multiply`, `divide`
- Comparison: `gt`, `lt`, `gte`, `lte`
- See [stdlib/math.md](../../stdlib/math.md)

### Boolean
- Logical: `and`, `or`, `not`
- Used in guard expressions: `expr when condition`
