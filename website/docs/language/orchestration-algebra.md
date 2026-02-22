---
title: "Orchestration Algebra"
sidebar_position: 7
description: "Learn boolean-based control flow with guards, coalesce, and branch expressions for conditional pipeline execution."
---

# Boolean Algebra for Orchestration

This document describes the orchestration control flow system in constellation-lang, based on boolean algebra principles. These features enable conditional execution, branching, and composition of pipeline fragments while maintaining the declarative, DAG-based nature of the language.

## Implementation Status

| Feature | Status | Issue |
|---------|--------|-------|
| Guard expressions (`when`) | ✅ Implemented | #15 |
| Coalesce operator (`??`) | ✅ Implemented | #17 |
| Branch expressions | ✅ Implemented | #22 |
| Boolean operators (`and`, `or`, `not`) | ✅ Implemented | #10 |
| Comparison operators | ✅ Implemented | #9 |
| Lambda expressions (for `all`, `any`, etc.) | ✅ Implemented | #23 |
| Lambda infix operators (`>`, `*`, etc. in bodies) | ✅ Implemented | RFC-032 |
| Implicit `it` parameter + infix HOF syntax | ✅ Implemented | RFC-033 |
| Optional chaining (`?.`) | 🔮 Future | - |
| Unwrap with default (`?:`) | 🔮 Future | - |

## Theoretical Foundation

### Orchestration as Boolean Lattice

Pipeline orchestration can be modeled as a boolean lattice where:

- **⊤ (top)** = Always execute (unconditional)
- **⊥ (bottom)** = Never execute (dead branch)
- **∧ (meet)** = Both conditions must hold (AND)
- **∨ (join)** = Either condition suffices (OR)
- **¬ (complement)** = Negation (NOT)

This forms a complete boolean algebra with the standard laws:
- Idempotent: `a ∧ a = a`, `a ∨ a = a`
- Commutative: `a ∧ b = b ∧ a`, `a ∨ b = b ∨ a`
- Associative: `(a ∧ b) ∧ c = a ∧ (b ∧ c)`
- Absorption: `a ∧ (a ∨ b) = a`, `a ∨ (a ∧ b) = a`
- Distributive: `a ∧ (b ∨ c) = (a ∧ b) ∨ (a ∧ c)`
- De Morgan: `¬(a ∧ b) = ¬a ∨ ¬b`, `¬(a ∨ b) = ¬a ∧ ¬b`

### Execution Semantics

Each node in the DAG carries an implicit **guard predicate** `G(n)` that determines whether it executes:

```
execute(n) = G(n) ∧ ∀p ∈ parents(n): execute(p)
```

A node executes if its guard is true AND all parent nodes executed. This ensures:
1. Data dependencies are respected
2. Conditional branches propagate correctly
3. Dead branches are pruned at compile time when possible

### Type-Level Guards

Guards interact with the type system through an `Optional<T>` wrapper:

- Unconditional node: `T`
- Conditional node: `Optional<T>`
- Merging conditional branches: `Optional<T> ⊕ Optional<T> → Optional<T>`

## Syntax Reference

:::tip Guards vs Conditionals
Use guard expressions (`when`) for skip-or-execute decisions where you may want `None`. Use `branch` expressions when you need to choose between multiple concrete values. Guards compose well with `??` for fallback chains.
:::

### 1. Guard Expressions (`when`) ✅

> **Status:** Implemented in #15

Attach a boolean guard to any expression:

```
result = expression when condition
```

**Semantics:** `result` has type `Optional<T>` where `T` is the type of `expression`. If `condition` is false, `result` is `None`.

**Examples:**

```
# Only compute embeddings for long text
embeddings = compute-embeddings(text) when length(text) > 100

# Conditional feature extraction
premium_features = extract-premium(data) when user.tier == "premium"
```

### 2. Boolean Operators ✅

> **Status:** Implemented in #10

Standard boolean operators with short-circuit evaluation:

| Operator | Syntax | Description |
|----------|--------|-------------|
| AND | `a and b` | True if both true |
| OR | `a or b` | True if either true |
| NOT | `not a` | Negation |

**Examples:**

```
# Complex guard conditions
result = expensive-op(data) when (flag and not disabled) or override

# Compound conditions
filtered = process(items) when hasFeature and itemCount > 0
```

### 3. Comparison Operators ✅

> **Status:** Implemented in #9

For constructing boolean predicates:

| Operator | Syntax | Description |
|----------|--------|-------------|
| Equal | `a == b` | Equality test |
| Not equal | `a != b` | Inequality test |
| Less than | `a < b` | Ordering |
| Greater than | `a > b` | Ordering |
| Less or equal | `a <= b` | Ordering |
| Greater or equal | `a >= b` | Ordering |

### 4. Coalesce Operator (`??`) ✅

> **Status:** Implemented in #17

Provide fallback for optional values:

```
result = optional_expr ?? fallback_expr
```

**Semantics:** If `optional_expr` is `Some(v)`, result is `v`. Otherwise, result is `fallback_expr`.

**Examples:**

```
# Fallback to default embeddings
embeddings = cached-embeddings(id) ?? compute-embeddings(text)

# Chain of fallbacks
value = primary() ?? secondary() ?? default
```

### 5. Unwrap with Default (`?:`) 🔮

> **Status:** Not yet implemented (future work)

Similar to coalesce but with explicit None handling:

```
result = optional_expr ?: default_value
```

### 6. Branch Expression (`branch`) ✅

> **Status:** Implemented in #22

Multi-way conditional with exhaustive matching:

```
result = branch {
  condition1 -> expression1,
  condition2 -> expression2,
  otherwise -> default_expression
}
```

**Semantics:** Evaluates conditions in order, returns first matching branch. The `otherwise` clause is required for exhaustiveness.

**Examples:**

```
# Tiered processing
processed = branch {
  priority == "high" -> fast-path(data),
  priority == "medium" -> standard-path(data),
  otherwise -> batch-path(data)
}

# Model selection based on input characteristics
model_output = branch {
  length(text) > 1000 -> large-model(text),
  length(text) > 100 -> medium-model(text),
  otherwise -> small-model(text)
}
```

### 7. Parallel Guards (`all`, `any`) ✅

> **Status:** Implemented in #23 (via lambda expressions), enhanced by RFC-033 (infix + `it`)

Aggregate boolean conditions over collections:

```
all(collection, predicate)  # True if predicate holds for all elements
any(collection, predicate)  # True if predicate holds for any element
```

**Examples:**

```
# Infix with implicit it (RFC-033)
validated = process(items) when items all it.isValid
special = special-process(items) when items any it.needsSpecial

# Prefix with implicit it
validated = process(items) when all(items, it.isValid)

# Explicit lambda (always works)
validated = process(items) when all(items, (item) => item.isValid)
```

### 8. Optional Chaining (`?.`) 🔮

> **Status:** Not yet implemented (future work)

Safe field access on optional values:

```
result = optional_value?.field
```

**Semantics:** If `optional_value` is `Some(v)`, returns `Some(v.field)`. Otherwise, returns `None`.

**Examples:**

```
# Safe nested access
name = user?.profile?.displayName ?? "Anonymous"
```

## Composite Examples

### Conditional Pipeline Branch

```
type Input = { text: String, priority: String, cached: Boolean }

in request: Input

# Try cache first for non-priority requests
cached_result = lookup-cache(request.text) when request.cached

# Compute fresh result if needed
fresh_result = compute-result(request.text) when not request.cached or request.priority == "high"

# Merge with fallback
result = cached_result ?? fresh_result

out result
```

### Feature Flag Orchestration

```
type FeatureFlags = {
  useNewModel: Boolean,
  enableCaching: Boolean,
  experimentGroup: String
}

in data: Candidates<Item>
in flags: FeatureFlags

# Conditional model selection
new_scores = new-model-v2(data) when flags.useNewModel
old_scores = legacy-model(data) when not flags.useNewModel
scores = new_scores ?? old_scores

# Optional caching layer
cached = cache-results(scores) when flags.enableCaching

# A/B experiment branches
experiment_a = variant-a-process(scores) when flags.experimentGroup == "A"
experiment_b = variant-b-process(scores) when flags.experimentGroup == "B"
control = control-process(scores) when flags.experimentGroup == "control"

final = experiment_a ?? experiment_b ?? control

out final
```

### Early Termination Pattern

```
in items: Candidates<Item>

# Validation gate
validated = validate(items)
valid_items = validated when validated.allValid

# Processing only happens if validation passed
processed = expensive-process(valid_items) when valid_items != None

# Fallback for invalid input
error_result = error-response("Validation failed") when valid_items == None

out processed ?? error_result
```

:::note Optional Types
When a guard is applied, the result type becomes `Optional<T>`. Use the coalesce operator (`??`) to unwrap optionals with a fallback value, or chain multiple guarded expressions for a priority-based selection.
:::

## Compile-Time Optimizations

The boolean algebra enables several optimizations:

1. **Dead Branch Elimination**: If a guard is statically `false`, prune the subgraph
2. **Guard Propagation**: Push guards down to minimize computation
3. **Guard Merging**: Combine adjacent guards: `(a when g1) when g2` → `a when (g1 and g2)`
4. **Short-Circuit Fusion**: `a ?? b ?? c` compiles to single conditional chain
5. **Constant Folding**: Evaluate static boolean expressions at compile time

## Relationship to Existing Constructs

| Existing | New | Relationship |
|----------|-----|--------------|
| `if (c) a else b` | `branch { c -> a, otherwise -> b }` | Equivalent, branch generalizes |
| `a + b` (merge) | `a + b` | Unchanged, merge is orthogonal |
| `a[fields]` | `a[fields]` | Unchanged, projection is orthogonal |

The `when` guard and `??` coalesce are new primitives that compose with existing operations.

## Future Extensions

Potential extensions to the algebra:

1. **Temporal Guards**: `when available(dependency)` for async orchestration
2. **Resource Guards**: `when gpu_available` for hardware-aware scheduling
3. **Quota Guards**: `when within_budget(cost)` for cost-aware execution
4. **Retry Semantics**: `expression retry 3 when transient_error`

## Summary

| Construct | Syntax | Purpose | Status |
|-----------|--------|---------|--------|
| Guard | `expr when cond` | Conditional execution | ✅ #15 |
| Coalesce | `a ?? b` | Fallback for optionals | ✅ #17 |
| Branch | `branch { c -> e, ... }` | Multi-way conditional | ✅ #22 |
| Boolean ops | `and`, `or`, `not` | Predicate composition | ✅ #10 |
| Comparisons | `==`, `!=`, `<`, `>`, `<=`, `>=` | Predicate construction | ✅ #9 |
| Lambda | `(x) => expr` | Inline functions | ✅ #23 |
| Implicit `it` | `filter(xs, it > 0)` | Lambda shorthand | ✅ RFC-033 |
| Infix HOF | `xs filter it > 0` | Left-to-right pipelines | ✅ RFC-033 |
| Aggregates | `all(xs, p)`, `any(xs, p)` | Collection predicates | ✅ #23 |
| Optional chain | `a?.field` | Safe access | 🔮 Future |
| Unwrap default | `a ?: b` | Explicit None handling | 🔮 Future |

This set of primitives, grounded in boolean algebra, provides expressive orchestration control while preserving the declarative DAG semantics of constellation-lang.

## Related

- [Guards](./guards.md) — Detailed guide to `when` expressions
- [Coalesce](./coalesce.md) — Detailed guide to `??` operator
- [Lambdas](./lambdas.md) — Lambda expressions for `all`, `any`, `filter`, `map`
- [Expressions](./expressions.md) — Complete expression syntax reference