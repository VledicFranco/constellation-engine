# RFC-032: Lambda Body Infix Operators

- **Status:** Implemented
- **Created:** 2026-02-21
- **Author:** Francisco (discovered during #220 investigation)

## Summary

Infix operators (`+`, `-`, `*`, `/`, `>`, `<`, `>=`, `<=`, `==`, `!=`, `and`, `or`, `not`) already work in lambda bodies for integer types — the TypeChecker desugars them to the same stdlib `FunctionCall` nodes the lambda evaluator supports. This is untested and undocumented. This RFC proposes verifying the existing behavior, fixing two edge-case gaps (`!=` and Float arithmetic), adding comprehensive tests, and updating documentation to show infix syntax as the idiomatic way to write lambda predicates.

## Motivation

### Current friction

Lambda bodies require prefix function-call syntax for all operations:

```constellation
use stdlib.collection
use stdlib.compare

in numbers: List<Int>
in threshold: Int

# What you must write today
above = filter(numbers, (x) => gt(x, threshold))
doubled = map(numbers, (x) => multiply(x, 2))

# What both humans and LLMs naturally expect to write
above = filter(numbers, (x) => x > threshold)
doubled = map(numbers, (x) => x * 2)
```

This is unnatural for both human developers and LLM code generators. Every developer has `>` in muscle memory; nobody reaches for `gt` first. LLM code generation models overwhelmingly produce infix operators for arithmetic and comparison.

### Discovery

Investigation of issue #220 ("Only 14 stdlib functions supported in lambda bodies") revealed that the compilation pipeline already handles infix operators in lambda bodies through an existing desugaring path:

1. **Parser:** Lambda body is `P.defer(expression)` — full expression grammar, including infix operators
2. **TypeChecker:** `desugarComparison` and `desugarArithmetic` convert infix AST nodes to `TypedExpression.FunctionCall` (e.g., `x > 5` → `FunctionCall("gt", sig, [x, 5])`)
3. **IRGenerator:** `generateExpression` compiles `FunctionCall` to `IRNode.ModuleCall` inside the lambda body context
4. **DagCompiler:** `evaluateLambdaNodeUnsafe` handles `ModuleCall` and delegates to `evaluateBuiltinFunctionUnsafe`, which already supports `gt`, `lt`, `gte`, `lte`, `eq-int`, `eq-string`, `add`, `subtract`, `multiply`, `divide`

The boolean operators `and`, `or` (keywords) produce `BoolBinary` → `AndNode`/`OrNode`, which the evaluator also handles. The `not` keyword produces `Not` → `NotNode`, also handled.

### What already works (untested)

| Operator | Desugars to | Evaluator support |
|----------|-------------|-------------------|
| `+` (Int) | `FunctionCall("add")` → `ModuleCall` | `evaluateBuiltinFunctionUnsafe("add")` |
| `-` (Int) | `FunctionCall("subtract")` → `ModuleCall` | `evaluateBuiltinFunctionUnsafe("subtract")` |
| `*` (Int) | `FunctionCall("multiply")` → `ModuleCall` | `evaluateBuiltinFunctionUnsafe("multiply")` |
| `/` (Int) | `FunctionCall("divide")` → `ModuleCall` | `evaluateBuiltinFunctionUnsafe("divide")` |
| `>` | `FunctionCall("gt")` → `ModuleCall` | `evaluateBuiltinFunctionUnsafe("gt")` |
| `<` | `FunctionCall("lt")` → `ModuleCall` | `evaluateBuiltinFunctionUnsafe("lt")` |
| `>=` | `FunctionCall("gte")` → `ModuleCall` | `evaluateBuiltinFunctionUnsafe("gte")` |
| `<=` | `FunctionCall("lte")` → `ModuleCall` | `evaluateBuiltinFunctionUnsafe("lte")` |
| `==` (Int) | `FunctionCall("eq-int")` → `ModuleCall` | `evaluateBuiltinFunctionUnsafe("eq-int")` |
| `==` (String) | `FunctionCall("eq-string")` → `ModuleCall` | `evaluateBuiltinFunctionUnsafe("eq-string")` |
| `and` | `BoolBinary` → `AndNode` | `evaluateLambdaNodeUnsafe` handles `AndNode` |
| `or` | `BoolBinary` → `OrNode` | `evaluateLambdaNodeUnsafe` handles `OrNode` |
| `not` (keyword) | `Not` → `NotNode` | `evaluateLambdaNodeUnsafe` handles `NotNode` |

### What doesn't work

| Operator | Issue | Root cause |
|----------|-------|------------|
| `!=` | `validateBuiltinFunction` rejects `not` | TypeChecker desugars `!=` to `FunctionCall("not", [FunctionCall("eq-int")])`. IRGenerator compiles this as `ModuleCall("not")`, but `not` is not in the `validateBuiltinFunction` supported set. The keyword `not` works (produces `NotNode`), but the function call `not()` doesn't. |
| `+` (Float) | `ClassCastException` at runtime | `evaluateBuiltinFunctionUnsafe` does `.asInstanceOf[Long]` for all arithmetic. No Float path exists. |
| `-` (Float) | Same | Same |
| `*` (Float) | Same | Same |
| `/` (Float) | Same | Same |
| `>` (Float) | `ClassCastException` | Comparison evaluator also casts to `Long` |
| `<` (Float) | Same | Same |
| `>=` (Float) | Same | Same |
| `<=` (Float) | Same | Same |

## Proposed Solution

### Fix 1: `!=` support

**Option A (preferred):** Add `not` to `validateBuiltinFunction` and `evaluateBuiltinFunctionUnsafe`:

```scala
// In validateBuiltinFunction — add "not" to supported set
val supported = Set(
  "add", "add-int", "subtract", "sub-int",
  "multiply", "mul-int", "divide", "div-int",
  "gt", "lt", "gte", "lte", "eq-int", "eq-string",
  "not"  // NEW: supports != desugaring
)

// In evaluateBuiltinFunctionUnsafe — add not case
case "not" =>
  !inputs("a").asInstanceOf[Boolean]
```

**Option B:** Change the TypeChecker to desugar `!=` to `TypedExpression.Not(FunctionCall("eq-int", ...))` instead of `FunctionCall("not", [FunctionCall("eq-int", ...)])`. This would produce `NotNode` → `ModuleCall` instead of `ModuleCall("not")` → `ModuleCall`. More invasive, no additional benefit.

### Fix 2: Float arithmetic and comparison

Add type-aware evaluation in `evaluateBuiltinFunctionUnsafe`:

```scala
case "add" | "add-int" =>
  (inputs("a"), inputs("b")) match {
    case (a: Long, b: Long)     => a + b
    case (a: Double, b: Double) => a + b
    case (a: Long, b: Double)   => a.toDouble + b
    case (a: Double, b: Long)   => a + b.toDouble
    case (a, b) => throw DagCompilerError.toException(
      DagCompilerError.UnsupportedFunction(moduleName, s"$funcName(${a.getClass}, ${b.getClass})")
    )
  }

// Same pattern for subtract, multiply, divide, gt, lt, gte, lte
```

This follows the existing evaluator style (runtime type dispatch) and handles mixed Int/Float arithmetic naturally.

### Fix 3: Tests

Add test cases in `ClosureTest.scala` verifying infix operators in lambda bodies:

```scala
"compile filter with infix comparison operator" in {
  // filter(numbers, (x) => x > 5)
}

"compile map with infix arithmetic operator" in {
  // map(numbers, (x) => x * 2 + 1)
}

"compile filter with != operator" in {
  // filter(strings, (s) => s != "exclude")
}

"compile filter with infix and boolean operators" in {
  // filter(numbers, (x) => x > 0 and x < 100)
}

"compile map with float arithmetic" in {
  // map(numbers, (x) => x * 1.5)
}

"compile filter with closure and infix operator" in {
  // filter(numbers, (x) => x > threshold)
  // where threshold is a captured variable
}
```

### Fix 4: Documentation

Update language documentation and examples to show infix syntax as the **idiomatic** way to write lambda bodies. Function-call syntax (`gt`, `add`, etc.) should still work but is not the recommended style.

## Interaction with #220

This RFC **partially supersedes** issue #220 ("Only 14 stdlib functions supported in lambda bodies").

The original concern was that only 14 hardcoded functions work in the lambda evaluator. With infix operator support verified, the practical impact shrinks significantly:

- **Arithmetic** (`+`, `-`, `*`, `/`) covers the vast majority of lambda math via infix
- **Comparison** (`>`, `<`, `>=`, `<=`, `==`, `!=`) covers all predicate patterns via infix
- **Boolean** (`and`, `or`, `not`) already works via keywords

The remaining functions not callable in lambda bodies (e.g., `abs`, `mod`, `concat`, `length`, `contains`, `toUpper`, `toLower`) represent genuinely uncommon lambda-body operations. A filter like `filter(items, (x) => length(x.name) > 10)` is rare enough that keeping the explicit function-call style is acceptable, or it can be handled by computing the intermediate value outside the lambda:

```constellation
# Instead of: filter(items, (x) => length(x.name) > 10)
# Write:
namedItems = map(items, (x) => {name: x.name, nameLen: length(x.name)})
longNames = filter(namedItems, (x) => x.nameLen > 10)
```

**Recommendation:** Close #220 after this RFC ships. File a low-priority follow-up if users report needing specific functions in lambda bodies.

## Key Files

| File | Changes |
|------|---------|
| `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala` | Fix 1 (`not` in supported set), Fix 2 (Float evaluation) |
| `modules/lang-compiler/src/test/scala/io/constellation/lang/compiler/ClosureTest.scala` | Fix 3 (infix operator tests) |
| `website/docs/language/` | Fix 4 (documentation) |

**Read-only references (no changes needed):**

| File | Relevance |
|------|-----------|
| `modules/lang-parser/.../ConstellationParser.scala` lines 327-345, 405-419 | Parser already handles infix in lambda bodies |
| `modules/lang-compiler/.../TypeChecker.scala` lines 714-727, 962-1132 | Desugaring logic |
| `modules/lang-compiler/.../IRGenerator.scala` lines 99-131, 406-458 | Lambda IR generation |

## Alternatives Considered

### A. Add dedicated Arithmetic/Compare IR nodes for lambda bodies

Instead of relying on `ModuleCall` desugaring, add `ArithmeticNode` and `CompareNode` IR types that the lambda evaluator handles directly.

**Rejected:** Adds IR complexity for zero benefit. The existing `ModuleCall` path works; the evaluator already dispatches on function name. Adding new IR nodes would require changes across IRGenerator, DagCompiler, and any future IR analysis passes.

### B. Replace function-call syntax entirely — only allow infix in lambda bodies

**Rejected:** Breaking change with no migration path. Existing `.cst` files and tests use `gt(x, 5)` style. Both should work.

### C. Do nothing — just document the existing behavior

**Rejected:** The `!=` and Float gaps are genuine bugs that users will hit. The desugaring path exists but is broken for these cases.

## Implementation Phases

### Phase 1: Verify and fix (this RFC)

1. Add `not` to `validateBuiltinFunction` and `evaluateBuiltinFunctionUnsafe`
2. Add Float paths to `evaluateBuiltinFunctionUnsafe`
3. Add infix operator tests to `ClosureTest.scala`
4. Update language docs

### Phase 2: Deferred (future RFC if needed)

- Expand `evaluateBuiltinFunctionUnsafe` to support additional stdlib functions (`abs`, `mod`, `length`, etc.)
- Only if user demand justifies it

## Performance Considerations

None. The desugaring happens at compile time (TypeChecker). The runtime evaluation path is identical whether the user writes `gt(x, 5)` or `x > 5` — both produce the same `ModuleCall` IR node.

## Trade-offs

| Aspect | Benefit | Cost |
|--------|---------|------|
| Infix verification | Confirms existing capability, enables natural syntax | None — already works |
| `!=` fix | Completes operator set | 5 lines in DagCompiler |
| Float support | Enables `x * 1.5` patterns | ~40 lines of type dispatch in evaluator |
| Tests | Prevents regression, documents behavior | ~100 lines of test code |

## Related RFCs

- **RFC-030 (Lambda Closures):** Established the lambda body evaluator infrastructure. This RFC builds on it.
- **RFC-019 (Higher-Order Functions):** Defined `filter`, `map`, `all`, `any` as HOFs. The operators documented here work inside these HOF lambda arguments.

## Priority

**P1** — Ergonomic improvement that affects every user writing lambda expressions. Low implementation risk (targeted fixes to existing code). Should ship in 0.8.2.
