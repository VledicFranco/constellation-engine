# Lambda Closures

> **Path**: `organon/features/lambda-closures/`
> **Parent**: [features/](../README.md)

Lambda expressions that capture variables from their enclosing scope.

## Ethos Summary

- Closures use **by-value capture** (snapshot at lambda creation time)
- Captured variables become **additional Input nodes** in the lambda sub-DAG (copy strategy)
- Lambda parameters **shadow** outer variables of the same name
- **Nested closures** (multi-level capture) are out of scope (deferred to Phase 3)

## Contents

| File | Description |
|------|-------------|
| [PHILOSOPHY.md](./PHILOSOPHY.md) | Why closures exist, design reasoning |
| [ETHOS.md](./ETHOS.md) | Constraints for LLMs working on closures |

## Quick Reference

```constellation
use stdlib.collection
use stdlib.compare

in numbers: List<Int>
in threshold: Int

# Lambda captures `threshold` from outer scope
above = filter(numbers, (x) => gt(x, threshold))
out above
```

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `lang-compiler` | IR generation with closure capture | `IRGenerator.scala` — `generateLambdaIR`, `collectFreeVars` |
| `lang-compiler` | IR data structures | `IR.scala` — `TypedLambda.capturedBindings`, `HigherOrderNode.capturedInputs` |
| `lang-compiler` | DAG compilation with closure wiring | `DagCompiler.scala` — `processHigherOrderNode`, `createClosureEvaluator` |
| `core` | Closure-aware inline transforms | `InlineTransform.scala` — `ClosureFilterTransform`, `ClosureMapTransform`, etc. |

## See Also

- [rfcs/rfc-030-lambda-closures.md](../../../rfcs/rfc-030-lambda-closures.md) - RFC specification
- [type-safety/](../type-safety/) - Type checking (already supports closures)
