---
title: "lazy"
sidebar_position: 11
description: "Defer module execution until the result is actually needed, with automatic memoization on first access."
---

# lazy

Defer module execution until the result is actually needed.

## Syntax

```constellation
result = Module(args) with lazy
# or
result = Module(args) with lazy: true
```

**Type:** Boolean (flag or explicit true/false)

## Description

The `lazy` option defers the execution of a module until its result is actually used. The deferred computation is wrapped in a `LazyValue` that executes on first access and memoizes the result for subsequent accesses.

This is useful for expensive computations that may not always be needed, or for breaking circular dependencies.

## Examples

### Defer Expensive Computation

```constellation
in shouldProcess: Boolean
in data: Record

# Only computed if shouldProcess is true
expensive = HeavyComputation(data) with lazy

output = when shouldProcess then expensive else defaultValue
out output
```

The `HeavyComputation` only runs if `shouldProcess` is true.

### Explicit Boolean Value

```constellation
# Enable lazy evaluation
deferred = Compute(x) with lazy: true

# Disable (execute immediately - default behavior)
immediate = Compute(x) with lazy: false
```

### Multiple Access (Memoization)

```constellation
cached = ExpensiveLoad(id) with lazy

# First access triggers execution
first = process(cached)

# Second access uses memoized result
second = transform(cached)

out { first: first, second: second }
```

The module only executes once, and both uses get the same result.

### Conditional Branching

```constellation
in useNewAlgorithm: Boolean

# Both computations are defined but only one runs
oldResult = OldAlgorithm(data) with lazy
newResult = NewAlgorithm(data) with lazy

output = when useNewAlgorithm then newResult else oldResult
out output
```

## Behavior

1. When a lazy module call is encountered:
   - Create a `LazyValue` wrapper containing the computation
   - Return the wrapper immediately (no execution)
2. When the lazy value is first accessed:
   - Execute the wrapped computation
   - Store the result (memoize)
   - Return the result
3. On subsequent accesses:
   - Return the memoized result (no re-execution)

### Thread Safety

Lazy evaluation is thread-safe:
- Only one thread executes the computation
- Other threads wait for the result
- All threads see the same memoized value

## Related Options

- **[timeout](./timeout.md)** - Applied when lazy value is forced
- **[retry](./retry.md)** - Applied when lazy value is forced

## When to Use Lazy

**Good use cases:**
- Expensive computations that may not be needed
- Conditional branches where only one path executes
- Breaking circular dependencies
- Deferring I/O until necessary

**Avoid when:**
- The value is always needed (no benefit)
- Timing of execution matters (unpredictable)
- You need explicit error handling at definition time

## Best Practices

- Use lazy for expensive operations in conditional paths
- Remember that errors occur when the value is forced, not defined
- Combine with timeout to handle slow lazy evaluations
- Consider the memoization behavior when side effects matter
