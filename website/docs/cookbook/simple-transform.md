---
title: "Simple Transform"
sidebar_position: 5
description: "Basic single-module transformation"
---

# Simple Transform

The minimal pipeline pattern: one input, one module call, one output.

## Use Case

You need to transform a string to uppercase.

## The Pipeline

```constellation
# simple-test.cst
# Demonstrates basic module usage with a single transformation

@example("Hello, World!")
in message: String

# Transform the message to uppercase
result = Uppercase(message)

out result
```

## Explanation

| Step | Expression | Purpose |
|---|---|---|
| 1 | `@example("Hello, World!")` | Provides a default test value |
| 2 | `in message: String` | Declares the input |
| 3 | `Uppercase(message)` | Calls the `Uppercase` module (PascalCase = module call) |
| 4 | `out result` | Declares the output |

Module names use PascalCase (`Uppercase`, `WordCount`, `Trim`). The name must exactly match the module registered in the Scala runtime.

## Running the Example

### Input
```json
{
  "message": "Hello, World!"
}
```

### Expected Output
```json
{
  "result": "HELLO, WORLD!"
}
```

## Variations

### Chain two transforms

```constellation
@example("  Hello, World!  ")
in message: String

trimmed = Trim(message)
upper = Uppercase(trimmed)

out upper
```

### Multiple outputs from one input

```constellation
@example("Hello, World!")
in message: String

upper = Uppercase(message)
lower = Lowercase(message)
length = TextLength(message)

out upper
out lower
out length
```

## Best Practices

1. **One responsibility per step** — each variable assignment should do one thing
2. **Use `@example` for every input** — enables quick testing from the dashboard
3. **Module names are case-sensitive** — `Uppercase` works, `uppercase` does not (unless it's a stdlib function)

## Related Examples

- [Hello World](hello-world.md) — string concatenation
- [Text Analysis](text-analysis.md) — multi-step text processing
