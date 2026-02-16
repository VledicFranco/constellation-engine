# RFC-030: Lambda Closure Support

- **Status:** Draft
- **Created:** 2026-02-15
- **Author:** Claude (discovered during RFC-029 demo QA)

## Summary

Add support for closures in lambda expressions, allowing lambdas to capture
variables from their enclosing scope.

## Motivation

Currently, lambda expressions in constellation-lang can only reference their
own parameters. This is a significant limitation that prevents common patterns
like filtering with a user-provided threshold:

```constellation
use stdlib.collection
use stdlib.compare

in numbers: List<Int>
in threshold: Int

# This FAILS with "Undefined variable in IR generation: threshold"
above = filter(numbers, (x) => gt(x, threshold))
```

### Discovery Context

This limitation was discovered during RFC-029 (Demo QA Gold Standard) when
creating `lambda-filter.cst`. The pipeline had to be rewritten to avoid
closures, using only literal values:

```constellation
# Workaround: only use literals, no captured variables
positives = filter(numbers, (x) => gt(x, 0))
```

## Current Behavior

The `IRGenerator.generateLambdaIR` method only resolves the lambda's declared
parameter(s) when generating IR for the lambda body. Any reference to an
outer-scope variable throws:

```
java.lang.IllegalStateException: Undefined variable in IR generation: <varname>
```

**Crash severity:** This is an unrecoverable crash that terminates the
PipelineLoader and crashes the server if the pipeline is auto-loaded from
the `CST_DIR`. Even though PipelineLoader logs compilation warnings for
other errors, an `IllegalStateException` during IR generation bypasses
the error handling.

## Proposed Solution

### Phase 1: Closure Variable Capture

Modify `IRGenerator.generateLambdaIR` to:

1. Build a "capture set" of variables referenced in the lambda body that are
   not lambda parameters
2. Resolve these variables from the enclosing scope at the call site
3. Pass captured values as additional context to the `InlineTransform` node

### Phase 2: Graceful Error Handling

Regardless of closure support, `PipelineLoader.processFile` should catch
`IllegalStateException` from IR generation and log a warning instead of
crashing the server.

## Key Files

- `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/IRGenerator.scala`
  - `generateLambdaIR` - lambda IR generation
  - `generateHigherOrderCall` - higher-order function call handling
- `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/InlineTransform.scala`
  - Runtime execution of inline-expanded lambdas
- `modules/http-api/src/main/scala/io/constellation/http/PipelineLoader.scala`
  - Pipeline auto-loading (crash site for the error handling issue)

## Test Cases

```constellation
# Basic closure
in threshold: Int
in numbers: List<Int>
above = filter(numbers, (x) => gt(x, threshold))

# Closure over computed value
in numbers: List<Int>
avg = Average(numbers)
aboveAvg = filter(numbers, (x) => gt(x, avg))

# Nested closures (stretch goal)
in matrix: List<List<Int>>
in minVal: Int
filtered = map(matrix, (row) => filter(row, (x) => gt(x, minVal)))
```

## Alternatives Considered

1. **Partial application**: `filter(numbers, gt(_, threshold))` - requires
   new syntax and currying support
2. **Let-in bindings**: Inline variable substitution at compile time -
   simpler but less general
3. **Do nothing**: Require users to restructure pipelines to avoid closures -
   current workaround but limits expressiveness

## Priority

P1 - This is a fundamental expressiveness limitation that users will commonly
encounter when writing real-world pipelines.
