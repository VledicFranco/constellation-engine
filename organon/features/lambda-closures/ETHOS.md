# Lambda Closures Ethos

> Behavioral constraints for LLMs working on closure support.

---

## Core Invariants

1. **Closures are self-contained.** A lambda's `bodyNodes` map contains all nodes needed to evaluate it — parameter inputs, captured variable inputs, and body computation nodes. No runtime context merging.

2. **Capture is by-value.** Captured variables snapshot the DAG node output at execution time. There is no mutable reference capture. DAG nodes produce exactly one immutable value.

3. **Lambda parameters shadow outer variables.** If a lambda parameter has the same name as an outer variable, the parameter wins. The outer variable is NOT captured.

4. **No capture means no closure overhead.** Lambdas without free variables use plain transforms (`FilterTransform`, `MapTransform`). Closure transforms (`ClosureFilterTransform`, etc.) are only used when `capturedBindings` is non-empty.

5. **Free variable analysis is exhaustive.** `collectFreeVars` must handle every `TypedExpression` variant. A missing case means a captured variable could be silently dropped, producing wrong results at runtime.

---

## Design Constraints

### When Modifying IR.scala (TypedLambda, HigherOrderNode)

- **Keep `capturedBindings` defaulted to `Map.empty`.** Non-closure lambdas must work without specifying this field.
- **Keep `capturedInputs` defaulted to `Map.empty`.** Same reason — backwards compatibility.
- **`capturedBindings` maps to bodyNodes UUIDs.** The values are Input node IDs *inside* the lambda's `bodyNodes`, not outer context IDs.
- **`capturedInputs` maps to outer context UUIDs.** The values are node IDs in the *enclosing* IR pipeline, used by DagCompiler to wire data dependencies.
- **Update `dependencies` when changing capture.** `IRPipeline.dependencies` must include `capturedInputs` values so topological sort resolves captured nodes before the HOF node.

### When Modifying IRGenerator.scala (generateLambdaIR, collectFreeVars)

- **Pass the outer `GenContext` to `generateLambdaIR`.** The outer context provides variable bindings and node types for captured variables.
- **Create fresh `IRNode.Input` for each captured variable.** Do not reuse the outer node ID inside the lambda body — the lambda must be self-contained.
- **Only capture variables that exist in outer context.** If a name is not in outer context, it's not a free variable (it will cause an error through normal name resolution).
- **Exhaustive match in `collectFreeVars`.** Every `TypedExpression` case must be handled. Use `case _ =>` only as a safety net, never as the primary handler for expression variants.
- **Recurse into sub-expressions.** `collectFreeVars` must recurse into function arguments, conditional branches, binary operators, and all nested structures.

### When Modifying InlineTransform.scala (Closure*Transform)

- **Closure transforms extract captured values from `inputs`.** The `inputs` map contains both `"source"` (the list) and captured variable values keyed by name.
- **Keep non-closure transforms unchanged.** Plain `FilterTransform`, `MapTransform`, etc. must not be modified — they handle the common case without map lookups.
- **Evaluator signature is `(Any, Map[String, Any]) => ReturnType`.** Element first, captured values second. This matches how `evaluateLambdaBodyUnsafe` receives bindings.

### When Modifying DagCompiler.scala (processHigherOrderNode)

- **Resolve captured variable UUIDs to data node IDs.** `capturedInputs` contains IR node UUIDs; `transformInputs` needs DagSpec data node UUIDs. Use `getNodeOutput` to resolve.
- **Add captured data IDs to `transformInputs`.** The transform needs both `"source"` and all captured variable data to execute.
- **Dispatch to closure evaluator when `capturedBindings.nonEmpty`.** Check at transform creation time, not at runtime.
- **`evaluateLambdaBodyUnsafe` bindings include both params and captures.** The `allBindings` map merges lambda parameter bindings with captured value bindings. Lambda params take precedence (shadowing).

---

## Decision Heuristics

### "Should this variable be captured?"

**Yes** if:
- It appears in the lambda body as a `VarRef`
- It is NOT one of the lambda's own parameter names
- It resolves to a binding in the outer `GenContext`

**No** if:
- It is a lambda parameter (shadowed)
- It is a literal value (no capture needed)
- It is a function name (resolved separately via `FunctionCall`)

### "Should this use a plain or closure transform?"

**Plain** (`FilterTransform`, `MapTransform`, etc.) if:
- `capturedBindings` is empty
- The lambda only references its own parameters and literals

**Closure** (`ClosureFilterTransform`, etc.) if:
- `capturedBindings` is non-empty
- The lambda references at least one outer-scope variable

### "How do I add a new HOF operation with closure support?"

1. Add the `HigherOrderOp` variant (e.g., `SortBy`)
2. Add a plain transform in `InlineTransform` (e.g., `SortByTransform`)
3. Add a closure variant in `InlineTransform` (e.g., `ClosureSortByTransform`)
4. Handle both variants in `createHigherOrderTransform` in `DagCompiler`
5. Add test cases for both plain and closure paths
6. Update `DagVizCompiler` pattern matches if needed

---

## Component Boundaries

| Component | Closure Responsibility |
|-----------|----------------------|
| `core` | Closure transform variants (`InlineTransform.scala`) |
| `lang-compiler` | Free variable analysis, IR generation (`IRGenerator.scala`) |
| `lang-compiler` | IR data structures (`IR.scala` — `TypedLambda`, `HigherOrderNode`) |
| `lang-compiler` | DAG wiring of captured dependencies (`DagCompiler.scala`) |
| `lang-compiler` | Visualization of HOF nodes (`DagVizCompiler.scala`) |

**Never:**
- Put free variable analysis in `core` (core is runtime-only, no AST knowledge)
- Put capture resolution in `lang-parser` (parser produces untyped AST, no scope info)
- Share node UUIDs between lambda body and outer context (self-containment invariant)
- Add mutable capture semantics (DAG nodes are immutable by design)

---

## What Is Out of Scope

Do not add:

- **Nested closures.** Capturing through multiple lambda boundaries (lambda inside lambda). Deferred to Phase 3.
- **Mutable capture.** DAG nodes produce immutable values. No mutable variable capture.
- **First-class functions.** Lambdas are only valid as HOF arguments (filter, map, all, any). They are not general-purpose values.
- **Closure serialization.** Closures exist only at compile/execution time. No need to serialize lambda bodies.
- **Dynamic capture.** All captured variables are determined statically by `collectFreeVars`. No runtime discovery.

---

## Testing Requirements

When modifying closure support:

1. **IR-level tests** for `TypedLambda.capturedBindings` and `HigherOrderNode.capturedInputs`
2. **DAG-level tests** for `transformInputs` containing captured variable data IDs
3. **Backwards compatibility tests** confirming non-closure lambdas still use plain transforms
4. **Shadowing tests** confirming lambda params hide outer variables of the same name
5. **Multi-capture tests** confirming multiple outer variables can be captured simultaneously

Key test file:
- `modules/lang-compiler/src/test/scala/io/constellation/lang/compiler/ClosureTest.scala`
