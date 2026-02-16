# RFC-030: Lambda Closure Support

- **Status:** Implemented
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

**Crash severity:** ~~This is an unrecoverable crash that terminates the
PipelineLoader and crashes the server.~~ Fixed in PR #223 — PipelineLoader now
catches unexpected exceptions via `.handleErrorWith` and reports them as
compilation failures. The underlying `IRGenerator` limitation remains: closures
are not supported and produce a compile-time error.

## Proposed Solution

### Phase 1: Graceful Error Handling — SHIPPED

Merged in PR #223 (`fix/pipeline-loader-crash`). PipelineLoader now catches
exceptions from `compileIO` via `.handleErrorWith` in both `compileAndStore`
and `processFilesBatched`, converting them to graceful `FileResult.Failed` /
`CompileResult` errors with WARN-level logging. Closure `.cst` files are
reported as compilation failures instead of crashing the server.

### Phase 2: Closure Variable Capture

Closures are implemented as **inline modules with extra data node inputs**.
The lambda compiles into a self-contained sub-DAG (`TypedLambda.bodyNodes`).
Today the only inputs are the lambda parameters. Captured variables become
additional nodes copied into the sub-DAG from the outer scope.

Implementation:

1. `generateLambdaIR` accepts the enclosing `GenContext` (currently uses
   `GenContext.empty`)
2. When `VarRef` resolves a name not in the lambda parameters, it looks up
   the node ID in the enclosing context
3. The referenced outer node (and its transitive dependencies) are **copied**
   into the lambda's `bodyNodes` map, making the sub-DAG self-contained
4. At runtime, `HigherOrderNode` executes the lambda body as before — the
   captured values are just regular nodes in `bodyNodes`, no special handling

This is the **copy** strategy: the lambda sub-DAG owns all the nodes it
needs. No runtime merging of execution contexts required.

## Scoping Semantics

Closures use **by-value capture** (snapshot at lambda creation time). Captured
variables are resolved from the enclosing scope at the call site and their
nodes are copied into the lambda's sub-DAG. This is the natural fit for the
DAG execution model where node outputs are immutable values.

## Type Checker Interaction

The type checker **already accepts** lambdas with captured variables — it
resolves them correctly from the enclosing scope. The crash only occurs later
during IR generation, where `generateLambdaIR` uses `GenContext.empty` and
loses the enclosing scope. No type checker changes are needed.

## Key Files

- `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/IRGenerator.scala`
  - `generateLambdaIR` — uses `GenContext.empty` (root cause)
  - `generateHigherOrderCall` — has outer `ctx` that should be passed through
- `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/InlineTransform.scala`
  - Runtime execution of inline-expanded lambdas
- `modules/lang-compiler/src/main/scala/io/constellation/lang/LangCompiler.scala`
  - `compileIO` wraps `compile` in `IO(...)` — exception becomes failed IO
- `modules/http-api/src/main/scala/io/constellation/http/PipelineLoader.scala`
  - Pipeline auto-loading (crash fix already shipped in PR #223)

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

# Shadowing: lambda param shadows outer variable
in x: Int
in numbers: List<Int>
result = filter(numbers, (x) => gt(x, 0))
# Expected: lambda param `x` shadows outer `x`, no ambiguity
```

### Nested Closures (Phase 3 — separate follow-up)

Nested closures require capturing variables across **two lambda boundaries**
(e.g., `minVal` captured through both the outer `map` lambda and the inner
`filter` lambda). This is a significant implementation complexity jump and
should be deferred to a follow-up:

```constellation
# Nested closures — requires multi-level capture
in matrix: List<List<Int>>
in minVal: Int
filtered = map(matrix, (row) => filter(row, (x) => gt(x, minVal)))
```

## Performance Considerations

Closures add captured values as additional node references in the lambda's
`bodyNodes` map. Since captured values are just existing DAG node IDs (UUIDs),
the overhead is minimal — a few extra entries per closure. No serialization
impact expected for typical use cases.

## Alternatives Considered

1. **Partial application**: `filter(numbers, gt(_, threshold))` - requires
   new syntax and currying support
2. **Let-in bindings**: Inline variable substitution at compile time -
   simpler but less general
3. **Do nothing**: Require users to restructure pipelines to avoid closures -
   current workaround but limits expressiveness

## Priority

- **Phase 1 (crash fix):** P0 — SHIPPED (PR #223)
- **Phase 2 (closures):** P1 — fundamental expressiveness limitation that
  users will commonly encounter when writing real-world pipelines
- **Phase 3 (nested closures):** P2 — advanced feature, deferred

## Related RFCs

- **RFC-031:** IRGenerator error handling refactor (return `Either` instead of
  throwing) — spun out from this RFC's review as an independent improvement
