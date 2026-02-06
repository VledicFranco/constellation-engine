---
title: "Hello World"
sidebar_position: 1
description: "Your first Constellation pipeline"
---

# Hello World

The simplest Constellation pipeline: take an input, call a module, and produce an output.

## Use Case

You want to greet a user by name, using string concatenation and trimming.

## The Pipeline

```constellation
# hello.cst - My first Constellation pipeline

@example("Alice")
in name: String

greeting = concat("Hello, ", name)
trimmed_greeting = trim(greeting)

out trimmed_greeting
```

## Explanation

| Step | Expression | Purpose |
|---|---|---|
| 1 | `in name: String` | Declares a required string input |
| 2 | `concat("Hello, ", name)` | Calls the `concat` stdlib function to build a greeting |
| 3 | `trim(greeting)` | Removes any leading/trailing whitespace |
| 4 | `out trimmed_greeting` | Declares the pipeline output |

The `@example("Alice")` annotation provides a default value for testing — the dashboard and API use it when no input is supplied.

:::tip
The `@example` annotation is not just for documentation. The dashboard pre-fills input forms with these values, enabling one-click testing.
:::

## Running the Example

### Input
```json
{
  "name": "Alice"
}
```

### Expected Output
```json
{
  "trimmed_greeting": "Hello, Alice"
}
```

## Variations

### Multiple greetings

```constellation
@example("World")
in name: String

hello = concat("Hello, ", name)
hi = concat("Hi, ", name)
hey = concat("Hey, ", name)

out hello
out hi
out hey
```

### Using string interpolation

```constellation
@example("Alice")
in name: String

greeting = "Hello, ${name}! Welcome aboard."

out greeting
```

:::note
String interpolation with `${...}` is preferred over `concat()` for readability. See [String Interpolation](string-interpolation.md) for the full syntax.
:::

## Best Practices

1. **Always declare types** — `in name: String` gives the compiler type information for downstream validation
2. **Use `@example` annotations** — they serve as documentation and enable one-click testing in the dashboard
3. **Name outputs meaningfully** — output names become JSON keys in the API response

## Related Examples

- [Simple Transform](simple-transform.md) — single module call pattern
- [Text Analysis](text-analysis.md) — multi-step text processing
- [String Interpolation](string-interpolation.md) — `${expression}` syntax
