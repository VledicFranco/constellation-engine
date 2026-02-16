# Lambda Closures Philosophy

> Why lambdas need to capture variables from their enclosing scope.

---

## The Problem

Without closures, lambda expressions can only reference their own parameters and literals. This prevents common patterns like filtering with a user-provided threshold:

```constellation
in numbers: List<Int>
in threshold: Int

# Without closures: FAILS — threshold is not a lambda parameter
above = filter(numbers, (x) => gt(x, threshold))
```

Users must work around this by restructuring pipelines to avoid referencing outer variables, which is unnatural and limits expressiveness.

---

## The Bet

Closures are a **fundamental expectation** of any language with lambda expressions. Users coming from Python, JavaScript, Scala, or any modern language expect lambdas to "see" variables in scope. Not supporting this creates friction and confusion.

The bet: the implementation complexity of closures (copy strategy, additional data node wiring) is worth the expressiveness gain.

---

## Design Decisions

### 1. Copy Strategy (Self-Contained Sub-DAGs)

Captured variables become additional `IRNode.Input` nodes copied into the lambda's `bodyNodes` map. The lambda sub-DAG is fully self-contained — no runtime merging of execution contexts.

**Why:** The DAG execution model evaluates nodes by resolving their inputs. By making captured values regular Input nodes in the sub-DAG, the existing mini-interpreter (`evaluateLambdaBodyUnsafe`) handles them without structural changes. The captured values are resolved from `paramBindings` alongside lambda parameters.

### 2. By-Value Capture

Captured variables are resolved from the DAG at execution time, not at lambda definition time. Since DAG node outputs are immutable values, this is equivalent to by-value capture.

**Why:** The DAG model has no concept of mutable references. Each node produces exactly one value. Capturing the node's output is the natural and only option.

### 3. Free Variable Analysis at IR Generation Time

The `collectFreeVars` function pre-scans the lambda body to identify which outer-scope variables are referenced. Only those variables get Input nodes in the sub-DAG.

**Why:** Eager capture (creating Input nodes for all outer variables) would waste resources. Lazy capture (on-demand during expression generation) would require mutable state or a two-pass approach. Pre-scanning is clean, deterministic, and minimal.

### 4. Separate Closure Transform Variants

Closure lambdas use `ClosureFilterTransform`, `ClosureMapTransform`, etc., instead of the plain variants. The closure variants receive `(element, capturedValues)` instead of just `element`.

**Why:** Keeping the non-closure path unchanged preserves backwards compatibility and avoids adding overhead (map lookup for captured values) to lambdas that don't need it.

---

## Trade-Offs

| Decision | Benefit | Cost |
|----------|---------|------|
| Copy strategy | Self-contained sub-DAGs, simple runtime | Captured nodes duplicated in IR |
| By-value capture | Matches DAG immutability model | No mutable capture (intentional) |
| Pre-scan for free vars | Minimal capture, deterministic | Recursive AST walk |
| Separate transform variants | No overhead for non-closure lambdas | More InlineTransform subtypes |

---

## What This Does Not Cover

- **Nested closures** (Phase 3): Capturing through multiple lambda boundaries requires transitive capture propagation. Deferred.
- **Mutable capture**: DAG nodes are immutable. No mutable variable capture by design.
- **First-class functions**: Lambdas can only be passed to HOF stdlib functions (filter, map, all, any). They are not general-purpose values.
