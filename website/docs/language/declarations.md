---
title: "Declarations"
sidebar_position: 4
---

# Declarations

## Type Definitions

```
type TypeName = TypeExpression
```

Examples:

```
type User = { id: Int, name: String }
type UserList = List<User>
type Merged = TypeA + TypeB
```

## Input Declarations

```
in variableName: TypeExpression
```

Examples:

```
in userId: Int
in query: String
in items: Candidates<{ id: String, score: Float }>
```

### Input Annotations

Annotations provide metadata for input declarations. They appear on the line(s) before the `in` declaration.

#### @example

The `@example` annotation specifies an example value for an input. This value is:
- Used by the VSCode extension to pre-populate the run widget
- Type-checked against the declared input type at compile time
- Included in the LSP `getInputSchema` response for tooling integration

**Syntax:**

```
@example(value)
in variableName: Type
```

**Supported Example Values:**

Currently, `@example` supports literal values only:

| Type | Example Syntax |
|------|----------------|
| String | `@example("hello world")` |
| Int | `@example(42)` or `@example(-100)` |
| Float | `@example(3.14)` |
| Boolean | `@example(true)` or `@example(false)` |

**Examples:**

```
# String input with example
@example("hello world")
in text: String

# Integer input with example
@example(42)
in count: Int

# Negative integer
@example(-100)
in offset: Int

# Float input with example
@example(3.14)
in ratio: Float

# Boolean input with example
@example(true)
in enabled: Boolean
```

**Multiple Inputs with Examples:**

```
@example("Alice")
in name: String

@example(30)
in age: Int

@example(true)
in active: Boolean

out name
```

**Inputs Without Examples:**

The `@example` annotation is optional. Inputs without examples still work normally:

```
# Some inputs have examples, some don't
@example("hello")
in greeting: String

in count: Int          # No example - user must provide value

@example(true)
in verbose: Boolean

out greeting
```

**Type Checking:**

The example value is type-checked against the declared input type. Mismatches produce compile errors:

```
# Error: @example type mismatch: expected String, got Int
@example(42)
in text: String
```

```
# Error: @example type mismatch: expected Int, got String
@example("hello")
in count: Int
```

**Limitations:**

- Only literal values (strings, integers, floats, booleans) are supported
- Variable references and function calls in `@example` are parsed but not converted to JSON for the LSP
- Record literals (`{ key: value }`) and list literals (`[a, b, c]`) are not supported in expressions

For complex types (records, lists, Candidates), the VSCode extension provides a form-based input UI instead of using `@example`.

## Assignments

```
variableName = expression
```

Examples:

```
result = process(input)
merged = a + b
projected = data[field1, field2]
```

## Output Declaration

Every pipeline must have exactly one output:

```
out expression
```

Examples:

```
out result
out items[id, score] + computed[rank]
```
