---
title: "Namespaces"
sidebar_position: 8
description: "Organizing code with namespace imports and aliases"
---

# Namespaces

Import standard library functions with `use` declarations, aliases, and fully qualified calls.

## Use Case

You want to use math, string, and comparison functions from the standard library without verbose fully qualified names.

## The Pipeline

```constellation
# namespaces.cst

@example(10)
in a: Int

@example(5)
in b: Int

# Method 1: Use stdlib.math namespace
use stdlib.math
result1 = add(a, b)

# Method 2: Fully qualified name (always works)
result2 = stdlib.math.multiply(a, b)

# Method 3: Use namespace with alias
use stdlib.string as str

@example("Hello from namespace!")
in greeting: String
trimmed_greeting = str.trim(greeting)

# Outputs
out result1
out result2
out trimmed_greeting
```

## Explanation

| Import Style | Syntax | Call Syntax |
|---|---|---|
| Direct import | `use stdlib.math` | `add(a, b)` |
| Fully qualified | (none needed) | `stdlib.math.multiply(a, b)` |
| Aliased import | `use stdlib.string as str` | `str.trim(greeting)` |

After `use stdlib.math`, all functions in that namespace are available directly. Aliases with `as` provide short prefixes to avoid name collisions.

## Running the Example

### Input
```json
{
  "a": 10,
  "b": 5,
  "greeting": "Hello from namespace!"
}
```

### Expected Output
```json
{
  "result1": 15,
  "result2": 50,
  "trimmed_greeting": "Hello from namespace!"
}
```

## Variations

### Multiple namespace imports

```constellation
use stdlib.math
use stdlib.compare
use stdlib.string as str

in value: Int
in text: String

doubled = multiply(value, 2)
isLarge = gt(doubled, 100)
trimmed = str.trim(text)

out doubled
out isLarge
out trimmed
```

## Best Practices

1. **Import at the top** — place `use` declarations near the top of the file, after type definitions
2. **Use aliases to avoid ambiguity** — if two namespaces might share function names, alias at least one
3. **Fully qualified for one-off calls** — if you only call a namespace function once, the fully qualified form avoids an import

## Related Examples

- [Lambdas and HOF](lambdas-and-hof.md) — namespace imports for collection and compare functions
- [Lead Scoring](lead-scoring.md) — multiple namespace imports in a complex pipeline
