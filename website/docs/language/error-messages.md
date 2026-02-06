---
title: "Error Messages"
sidebar_position: 15
description: "Understand constellation-lang compiler errors with precise line and column information for quick debugging."
---

# Error Messages

constellation-lang provides precise error messages with line and column information.

:::tip Debugging Strategy
When you encounter an error, check the line and column numbers first. The error location points to where the problem was detected, which is often (but not always) where the fix should be applied. For type errors, trace back to where the variable was defined.
:::

## Undefined Variable

```
out undefined_var
```
```
Error at 1:5: Undefined variable: undefined_var
```

## Undefined Type

```
in x: NonExistent
```
```
Error at 1:6: Undefined type: NonExistent
```

## Undefined Function

```
result = unknown_func(x)
```
```
Error at 1:10: Undefined function: unknown_func
```

## Type Mismatch

```
# If function expects Int but receives String
result = expects_int(stringValue)
```
```
Error at 1:22: Type mismatch: expected Int, got String
```

## Invalid Projection

```
in data: { id: Int, name: String }
result = data[id, nonexistent]
```
```
Error at 2:10: Invalid projection: field 'nonexistent' not found. Available: id, name
```

## Incompatible Merge

```
in a: Int
in b: String
result = a + b
```
```
Error at 3:10: Cannot merge types: Int + String
```

:::note Common Merge Confusion
The `+` operator in constellation-lang is for merging record types, not numeric addition. For arithmetic, use function calls like `add(a, b)` from stdlib.math. Merge only works between compatible record types.
:::

## Parse Errors

```
in x: Int
out @invalid
```
```
Error at 2:5: Parse error: expected identifier
```

:::warning Check Syntax Carefully
Parse errors often indicate a syntax issue on the previous line, such as a missing comma or closing bracket. If the indicated line looks correct, check the line above it.
:::

## Related

- [Types](./types.md) — Type system reference and common type errors
- [Expressions](./expressions.md) — Valid expression syntax
- [Type Algebra](./type-algebra.md) — Record merge and projection rules