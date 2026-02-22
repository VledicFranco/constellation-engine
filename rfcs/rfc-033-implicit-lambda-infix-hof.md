# RFC-033: Implicit Lambda Parameter (`it`) and Infix HOF Syntax

- **Status:** Accepted
- **Created:** 2026-02-22
- **Author:** Francisco
- **Depends on:** RFC-032 (Lambda Infix Operators), RFC-030 (Lambda Closures)

## Summary

Two syntactic sugar features that reduce ceremony in higher-order function expressions:

1. **Implicit `it` parameter** — In HOF argument position, bare expressions containing free variable `it` are auto-wrapped in a single-parameter lambda by the type checker.
2. **Infix HOF syntax** — HOF names (`filter`, `map`, `all`, `any`) work as infix operators: `collection filter predicate` desugars to `filter(collection, predicate)`.

Both desugar to existing lambda + HOF infrastructure. No runtime changes.

## Motivation

### The ceremony problem

Every HOF call requires explicit lambda syntax and prefix function-call form, even for trivial predicates:

```constellation
use stdlib.collection
use stdlib.compare
use stdlib.math

in numbers: List<Int>
in threshold: Int

# Current: prefix call + explicit lambda
positive = filter(numbers, (x) => x > 0)
doubled = map(numbers, (x) => x * 2)
above = filter(numbers, (x) => x > threshold)
allPos = all(numbers, (x) => x > 0)
```

The `(x) =>` prefix is pure ceremony — it declares a binding that is used exactly once, immediately, in a context where the binding's type is already known from the function signature. And the prefix `filter(collection, ...)` buries the subject (collection) inside a function call instead of leading with it.

### What this RFC enables

```constellation
# Infix + implicit it — reads as English
positive = numbers filter it > 0
doubled = numbers map it * 2
above = numbers filter it > threshold
allPos = numbers all it > 0

# Pipeline chaining — left-to-right data flow
result = numbers filter it > 0 map it * 2

# Prefix + implicit it also works
positive = filter(numbers, it > 0)

# Explicit lambda always works (escape hatch)
positive = filter(numbers, (x) => x > 0)
```

### Three levels of ceremony, all equivalent

```constellation
# Level 1: Full explicit (current, always works)
result = filter(numbers, (x) => x > 0)

# Level 2: Implicit it (less ceremony)
result = filter(numbers, it > 0)

# Level 3: Infix + implicit it (reads as English)
result = numbers filter it > 0
```

Each level desugars to the one above. Users choose their preferred style.

### Precedent within the language

Constellation already has infix operators that desugar to stdlib function calls:

- `x > 5` desugars to `gt(x, 5)` (RFC-032)
- `x + y` on records desugars to merge
- `a and b` desugars to `BoolBinary(a, And, b)`
- `expr when condition` desugars to guard

HOF infix follows the same pattern: `collection filter predicate` desugars to `filter(collection, predicate)`. The compilation pipeline already handles infix → function call transformations at every level.

### LLM code generation

LLM code generators produce `numbers filter it > 0` far more reliably than `filter(numbers, (x) => x > 0)`. The infix form:
1. Leads with the data (the collection) — matches how humans describe operations
2. Uses the function name directly (no invented parameter name)
3. Requires no special syntax (`=>`, parenthesized params)

## Design

### Feature 1: Implicit `it` parameter

#### Semantics

When the type checker encounters a function call where:
1. A parameter expects `SFunction(List(T), R)` (single-parameter function type)
2. The argument is **not** an explicit `Lambda` AST node
3. `it` is **not bound** in the current `TypeEnvironment` (no `in it: ...` declaration, no enclosing lambda with parameter `it`)
4. The argument expression tree **contains** `VarRef("it")` (syntactic check)

The type checker wraps the argument in `Lambda(List(LambdaParam("it")), argument)` before type-checking the lambda body.

The environment check (condition 3) is critical for nested HOF calls. When an outer HOF lifts an expression, the lifted lambda binds `it` in the environment. Inner HOF calls then see `it` as bound and do **not** lift again — the inner `it` resolves to the outer lambda parameter. Users who need independent `it` bindings in nested HOFs must use explicit lambdas for at least one level:

```constellation
# Nested: outer explicit, inner implicit
result = filter(numbers, (n) => any(related, it > n))
```

#### `it` is not reserved

`it` is a regular identifier, not a keyword. If a user defines `in it: Int`, that binding takes precedence — `it` is bound in scope, so no lambda-lifting occurs. This follows Kotlin's exact semantics for `it`.

The type checker should emit a **warning** (not error) when a user-defined binding shadows the implicit lambda parameter:

```
WARNING: Variable 'it' shadows the implicit lambda parameter.
         Consider using a different name if you intend to use implicit lambdas.
```

#### Rules

| Condition | Behavior |
|-----------|----------|
| `filter(nums, (x) => x > 0)` | Explicit lambda — no transformation |
| `filter(nums, it > 0)` | `it` is free → lambda-lift to `(it) => it > 0` |
| `filter(nums, it > threshold)` | `it` is free, `threshold` captured as closure |
| `filter(nums, x > 0)` with `in x: Int` | `x` is bound, not `it` → type error (Boolean, not function) |
| `filter(nums, it > 0)` with `in it: Int` | `it` is bound → no lifting, type error |
| `map(nums, it * 2 + 1)` | Compound expression, `it` free → lifts entire expression |
| `filter(nums, it > 0 and it < 100)` | Boolean operators, `it` free → lifts entire expression |
| `filter(nums, any(items, it > 0))` | Nested HOF: inner `any` is type-checked first in a scope where `it` is unbound → inner lift produces `any(items, (it) => it > 0)` returning Boolean. Outer `filter` then sees Boolean argument with no free `it` → type error (expected function). Use explicit lambda for outer: `filter(nums, (n) => any(items, it > n))` |

#### Examples (prefix form with implicit `it`)

```constellation
use stdlib.collection
use stdlib.compare
use stdlib.math

in numbers: List<Int>
in threshold: Int

# Filter with comparison
positive = filter(numbers, it > 0)

# Filter with closure capture
above = filter(numbers, it > threshold)

# Map with arithmetic
doubled = map(numbers, it * 2)

# Boolean aggregate
allPositive = all(numbers, it > 0)

# Compound predicate
inRange = filter(numbers, it > 0 and it < 100)
```

### Feature 2: Infix HOF syntax

#### Syntax

The four HOF names become soft-keywords in infix position:

```
exprHofInfix = exprGuard (hofInfixOp exprGuard)*
hofInfixOp   = 'filter' | 'map' | 'all' | 'any'
```

Left-associative, sitting between `coalesce (??)` and `guard (when)` in precedence:

```
Precedence (low to high):
  lambda → coalesce (??) → HOF INFIX → guard (when) → or → and → not
    → compare → addSub → mulDiv → postfix → primary
```

#### Desugaring

The type checker (or parser) desugars infix HOF to prefix function call:

```
collection filter predicate  →  filter(collection, predicate)
collection map transform     →  map(collection, transform)
collection all predicate     →  all(collection, predicate)
collection any predicate     →  any(collection, predicate)
```

Combined with Feature 1 (implicit `it`), the full desugaring chain is:

```
numbers filter it > 0
→ (infix desugar) filter(numbers, it > 0)
→ (implicit it)   filter(numbers, (it) => it > 0)
→ (existing)      FilterTransform with lambda evaluator
```

#### Soft-keyword behavior

`filter`, `map`, `all`, `any` are **soft keywords** — they only act as infix operators when they appear between two expressions in the `exprHofInfix` production. In all other contexts, they remain regular identifiers:

| Context | Behavior |
|---------|----------|
| `numbers filter it > 0` | Infix HOF operator |
| `filter(numbers, lambda)` | Regular function call (unchanged) |
| `result = filter` | Variable reference (if `filter` is in scope as a variable) |
| `in filter: Int` | Input declaration (identifier) |

Unlike `and`/`or`/`when` (which are in the `reserved` set and cannot be used as identifiers), the HOF names are **not reserved**. They can still be used as variable names, input names, and function call names. The infix behavior only activates in the specific `exprHofInfix` parser production — between two `exprGuard` subexpressions.

#### Chaining

Multiple infix HOFs chain left-to-right, forming a natural pipeline:

```constellation
result = numbers filter it > 0 map it * 2
```

Parses as:
```
(numbers filter it > 0) map (it * 2)
```

Desugars to:
```
map(filter(numbers, (it) => it > 0), (it) => it * 2)
```

The left-associative `(hofInfixOp exprGuard)*` repetition handles arbitrary chain length:

```constellation
result = numbers filter it > 0 filter it < 100 map it * 2
# → map(filter(filter(numbers, ...), ...), ...)
```

#### Interaction with `when` (guard)

HOF infix sits above `when` in precedence. The two features operate on different domains and don't interact in practice:

```constellation
positive = numbers filter it > 0     # collection filter → List<Int>
value = x when condition              # scalar guard → Optional<T>
```

If `when` appears inside a predicate body, it is consumed by `exprGuard` as part of the right operand — not by the infix HOF. For example, `numbers filter it > 0 when flag` parses as `numbers filter ((it > 0) when flag)`, which is a type error: the guard produces `Optional<Boolean>`, but `filter` expects `Boolean`. This is the correct behavior — mixing guards and predicates requires explicit handling.

#### Examples

```constellation
use stdlib.collection
use stdlib.compare
use stdlib.math

in numbers: List<Int>
in threshold: Int
in users: List<{name: String, age: Int, email: String}>

# Basic filter
positive = numbers filter it > 0

# With closure capture
above = numbers filter it > threshold

# Map
doubled = numbers map it * 2
adjusted = numbers map it * 2 + 1

# Boolean aggregates
allPositive = numbers all it > 0
anyNegative = numbers any it < 0

# Compound predicate
inRange = numbers filter it > 0 and it < 100

# Field access on element
adults = users filter it.age >= 18

# Chained pipeline
result = numbers filter it > 0 map it * 2

# Combined with projection (filter, then select fields)
adultEmails = (users filter it.age >= 18)[email]

# Explicit lambda — use prefix form or parenthesize
positive2 = filter(numbers, (x) => x > 0)
doubled2 = map(numbers, (x) => x * 2)
```

**Note on explicit lambdas with infix syntax:** The infix right operand is parsed as `exprGuard`, not as a full `expression`. Since lambda syntax (`(x) => ...`) is only available at the top-level `expression` production, bare lambdas cannot appear as infix operands. This is by design — infix HOF syntax is meant for the `it` shorthand. For explicit lambdas, use the prefix form or parenthesize: `numbers map ((x) => x * 2)`.

## Implementation

### Phase 1: Implicit `it` parameter

#### Parser changes: None

`it` is a regular identifier. `it > 0` parses as `Expression.Compare(VarRef("it"), Gt, IntLiteral(0))`. No parser modifications needed.

#### TypeChecker changes

**File:** `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala`

1. Add `containsVarRef(expr: Expression, name: String): Boolean` — purely syntactic walk of the expression tree, returns `true` if the tree contains `VarRef(name)` anywhere (including inside nested function call arguments). Does NOT check scoping — just AST presence. Mirrors `collectFreeVars` in `IRGenerator.scala` (lines 463-519) but operates on `Expression` (pre-typecheck) rather than `TypedExpression`.

2. In the function call argument checking logic, when a parameter expects `SFunction(List(T), R)` and the argument is not a `Lambda`:

```scala
// When checking function call arguments:
(paramType, argExpr) match {
  case (sf: SemanticType.SFunction, expr) if sf.params.size == 1
    && !expr.isInstanceOf[Expression.Lambda]
    && !env.resolveVariable("it").isDefined  // not bound in scope
    && containsVarRef(expr, "it") =>         // expression mentions "it"
    // Lambda-lift: wrap in implicit lambda
    val implicitLambda = Expression.Lambda(
      List(Expression.LambdaParam(Located("it", span), None)),
      Located(expr, span)
    )
    checkExpression(implicitLambda, span, env)

  // ... existing lambda handling
}
```

The two-part check is essential: `env.resolveVariable("it")` prevents lifting when `it` is already bound (by user declaration or outer lambda), while `containsVarRef` confirms the expression actually references `it`.

#### IRGenerator changes: None

After type checking, the implicit lambda is identical to an explicit one.

#### DagCompiler changes: None

Same `InlineTransform` infrastructure.

### Phase 2: Infix HOF syntax

#### Parser changes

**File:** `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala`

1. Add HOF infix operator parser using the existing `keyword()` word-boundary pattern:

```scala
// Word-boundary helper (matches parser's existing keyword() pattern)
private def hofKeyword(s: String): P[String] =
  (P.string(s) <* P.not(alpha | digit | P.charIn("-_"))).as(s)

private val hofInfixOp: P[String] =
  token(
    hofKeyword("filter").backtrack |
    hofKeyword("map").backtrack |
    hofKeyword("all").backtrack |
    hofKeyword("any").backtrack
  )
```

The word-boundary check (`P.not(alpha | digit | P.charIn("-_"))`) prevents matching prefixes of longer identifiers — `filterBy` and `mapping` won't trigger infix parsing. This is the same pattern used by `keyword()` for `and`, `or`, `when`, etc.

**Important:** `filter`, `map`, `all`, `any` are **not** added to the `reserved` set. They remain valid identifiers in all non-infix contexts (input declarations, variable names, function call position). The `identifier` parser continues to accept them.

Note: prefix function calls like `filter(numbers, ...)` are safe because `exprPrimary` matches `functionCall.backtrack` (qualified name followed by `(`) before `hofInfixOp` ever runs. The infix check only fires after the first `exprGuard` is fully consumed.

2. Insert `exprHofInfix` in the precedence chain between `exprCoalesce` and `exprGuard`:

```scala
// Before:
private lazy val exprCoalesce: P[Expression] =
  (withSpan(exprGuard) ~ (coalesceOp *> withSpan(P.defer(exprCoalesce))).?).map { ... }

// After:
private lazy val exprCoalesce: P[Expression] =
  (withSpan(exprHofInfix) ~ (coalesceOp *> withSpan(P.defer(exprCoalesce))).?).map { ... }

private lazy val exprHofInfix: P[Expression] =
  (withSpan(exprGuard) ~ (hofInfixOp ~ withSpan(exprGuard)).rep0).map {
    case (first, Nil) => first.value
    case (first, rest) =>
      rest.foldLeft((first.value, first.span)) { case ((left, leftSpan), (op, right)) =>
        val call = Expression.FunctionCall(
          Located(QualifiedName.simple(op), Span(leftSpan.start, right.span.end)),
          List(Located(left, leftSpan), right),
          ModuleCallOptions.empty,
          None
        )
        (call, Span(leftSpan.start, right.span.end))
      }._1
  }
```

The infix form directly produces `Expression.FunctionCall` — the same AST node as `filter(numbers, expr)`. No new AST types needed.

#### AST changes: None

Infix HOF desugars to `Expression.FunctionCall` at parse time. No new AST nodes.

#### TypeChecker changes: None beyond Phase 1

The type checker sees a regular `FunctionCall("filter", List(collection, predicate))`. The implicit `it` lifting from Phase 1 handles the predicate argument.

#### IRGenerator changes: None

#### DagCompiler changes: None

### Summary of changes per phase

| File | Phase 1 | Phase 2 |
|------|---------|---------|
| `ConstellationParser.scala` | — | `hofKeyword`, `hofInfixOp`, `exprHofInfix`, precedence rewiring |
| `TypeChecker.scala` | `containsVarRef` helper, env-aware lambda-lifting in HOF args | — |
| `ClosureTest.scala` | Tests for `it` in all HOF contexts | Tests for infix syntax |
| `TypeCheckerTest.scala` | Tests for shadowing, non-HOF rejection | Tests for infix type errors |

**Read-only references (no changes):**

| File | Relevance |
|------|-----------|
| `Expression.scala` (AST) | No new nodes — infix produces `FunctionCall` |
| `IRGenerator.scala` | Lambda IR generation (unchanged) |
| `DagCompiler.scala` | Lambda body evaluation (unchanged) |
| `InlineTransform.scala` | FilterTransform, MapTransform, etc. (unchanged) |
| `HigherOrderFunctions.scala` | HOF signatures (unchanged) |

## Alternatives Considered

### A. `where` keyword for filter

```constellation
numbers where it > 0
```

**Superseded by infix HOF.** A dedicated `where` keyword only covers filter. Infix HOF syntax covers all four operations (`filter`, `map`, `all`, `any`) with one mechanism. `numbers filter it > 0` reads just as naturally as `numbers where it > 0` — and the verb matches the function name, so there's no additional concept to learn.

### B. Array-language pervasion: `numbers * 2` maps element-wise

The APL/J/K family makes arithmetic operations pervasive — applying a scalar operation to a collection automatically maps over elements.

**Rejected:** Ambiguous with existing merge semantics. `records + record` already means field merge on `List<Record>`. Adding element-wise arithmetic introduces type-dependent operator overloading. `numbers map it * 2` is explicit and equally concise.

### C. Method-chain syntax: `numbers.filter(it > 0)`

Familiar from Kotlin, Scala, JavaScript.

**Rejected:** `.` already means field access in constellation-lang. `numbers.filter` would parse as accessing field `filter` on a `List<Int>`.

### D. Pipe operator: `numbers |> filter(it > 0)`

Elixir/F#-style left-to-right piping.

**Rejected:** `|` already means union type. Infix HOF achieves the same left-to-right data flow without a new operator.

### E. Kotlin-style brace lambdas: `filter(numbers) { it > 0 }`

Trailing lambda syntax where the last argument is a `{}` block.

**Rejected:** `{}` already means record literal and brace projection. `users { name }` is a valid projection.

### F. General infix function calls (any function)

Allow any function to be used infix: `a func b` → `func(a, b)`.

**Rejected for now:** Too broad. `x y z` becoming `y(x, z)` for arbitrary identifiers creates parsing ambiguity. The HOF set (`filter`, `map`, `all`, `any`) is closed and compiler-known, making soft-keyword treatment safe. Adding a new HOF (e.g., `sortBy`) requires adding one entry to `hofInfixOp` in the parser — a deliberate cost that keeps the set curated. A general infix mechanism could be a future RFC with appropriate syntax markers (e.g., backtick syntax).

### G. SQL-style implicit context: `numbers filter > 0` (no `it`)

Drop the element binding entirely — make the collection's elements implicitly available.

**Rejected:** Works for records (`users filter age > 18`) but not for primitive collections (`numbers filter ??? > 0` — what's the implicit name for a bare integer?). The `it` binding handles both uniformly and is explicit about what's being referenced.

## Interaction with Existing Features

### Projection

```constellation
# Projection: select fields (implicit map)
users[name, email]

# Infix filter: select rows
users filter it.age > 18

# Composed: filter then project
(users filter it.age >= 18)[name, email]

# Pipeline: filter, project
adultNames = users filter it.age >= 18 map it.name
```

Complementary operations. Infix HOFs handle row-level operations (filter, transform), Projection handles column-level operations (field selection).

### Guard (`when`) and Coalesce (`??`)

```constellation
x when condition              # Optional<T> — scalar conditional
numbers filter it > 0         # List<T> — collection filter
```

Different domains, different result types. Precedence ordering (`?? > hofInfix > when`) prevents ambiguity.

### Closures (RFC-030)

`it > threshold` captures `threshold` from enclosing scope, identically to `(x) => x > threshold`. The lambda-lifting produces the same `Lambda` AST node; `collectFreeVars` in IRGenerator finds the same captured variables.

### Infix operators (RFC-032)

Essential prerequisite. `it > 0`, `it * 2`, `it != "exclude"` all use the infix operators that RFC-032 verified and fixed. Without RFC-032, predicates would need prefix syntax: `filter(numbers, gt(it, 0))` — defeating the purpose.

### Prefix function calls

Infix does NOT replace prefix. Both work:

```constellation
# Prefix (always works)
result = filter(numbers, (x) => x > 0)
result = filter(numbers, it > 0)

# Infix (sugar)
result = numbers filter it > 0
```

Prefix calls are safe because `exprPrimary` matches `functionCall.backtrack` (identifier + `(`) during the first `exprGuard` parse, before `hofInfixOp` is ever attempted.

## Trade-offs

| Aspect | Benefit | Cost |
|--------|---------|------|
| `it` implicit parameter | Eliminates lambda boilerplate for all single-param HOFs | Soft-reserves `it` as identifier name; single-param only |
| Infix HOF syntax | Natural English reading, pipeline chaining, uniform across all HOFs | Soft-reserves `filter`/`map`/`all`/`any` in infix position |
| No new keywords | `filter`/`map`/`all`/`any` are existing function names, not new concepts | Parser must recognize stdlib names (acceptable — closed set) |
| Desugaring to `FunctionCall` | No new AST nodes, no runtime changes | Type checker depends on Phase 1 for implicit `it` |
| Chaining | Left-to-right pipeline reads naturally | Deep chains may reduce readability (user discretion) |

## Error Messages

### Implicit `it` errors

| Scenario | Error |
|----------|-------|
| `filter(nums, x > 0)` with `x` undefined | `Undefined variable 'x'` |
| `filter(nums, x > 0)` with `in x: Int` | `Type mismatch: expected (Int) => Boolean, got Boolean` |
| `filter(nums, it > 0)` with `in it: Int` | Same — `it` is bound, no lifting |
| `map(nums, it > 0)` | `Type mismatch in lambda: expected Int return, got Boolean` |

### Infix HOF errors

| Scenario | Error |
|----------|-------|
| `numbers filter it > 0` without `use stdlib.collection` | `Unknown function 'filter'` |
| `42 filter it > 0` | `Type mismatch: filter expects List or Seq as first argument, got Int` |
| `numbers filter 42` | `Type mismatch: filter predicate must be (Int) => Boolean, got Int` |

## Implementation Phases

### Phase 1: Implicit `it` parameter (target: 0.8.3)

1. Add `containsVarRef(expr, name)` syntactic helper to TypeChecker
2. Add lambda-lifting logic: check `!env.resolveVariable("it")` + `containsVarRef(expr, "it")`
3. Tests: `it` in filter, map, all, any contexts (prefix calls)
4. Tests: `it` with closure capture (`it > threshold`)
5. Tests: `it` shadowed by user binding (`in it: Int`) → no lifting, type error
6. Tests: `it` in non-HOF context → normal variable reference
7. Tests: nested HOFs — inner `it` lifted, outer gets type error without explicit lambda
8. Update language docs: show `it` as idiomatic shorthand

### Phase 2: Infix HOF syntax (target: 0.8.3)

1. Add `hofKeyword` helper and `hofInfixOp` parser with word-boundary guard
2. Add `exprHofInfix` to expression precedence chain
3. Produce `Expression.FunctionCall` from infix form (no new AST nodes)
4. Tests: infix filter, map, all, any
5. Tests: chaining (filter then map, filter then filter)
6. Tests: infix + projection composition
7. Tests: infix without `use stdlib.collection` → clear error
8. Tests: `filter(...)` prefix still works (not captured by infix)
9. Update language docs: show infix as idiomatic style

### Phase 3: Deferred (future RFC if needed)

- User-defined infix functions (with syntax marker)
- `sortBy` infix when sort support is added
- Only if user demand justifies

## Performance Considerations

None. Both features are pure compile-time sugar. The implicit `it` lambda-lifting and infix→prefix desugaring happen during parsing/type-checking. The resulting IR, DAG, and runtime execution are identical to the explicit prefix + explicit lambda form.

## Related RFCs

- **RFC-030 (Lambda Closures):** Established closure infrastructure that `it` builds on.
- **RFC-032 (Lambda Infix Operators):** Enables `it > 0`, `it * 2` in lambda bodies — essential prerequisite.
- **RFC-019 (Higher-Order Functions):** Defined filter/map/all/any. This RFC adds sugar on top.

## Priority

**P2** — Ergonomic improvement that reduces ceremony for the most common HOF patterns. Low implementation risk (pure sugar, no runtime changes). Depends on RFC-032 (merged in PR #236).
