# RFC-010: Lazy

**Status:** Implemented
**Priority:** 4 (Advanced Control)
**Author:** Agent 1
**Created:** 2026-01-25

---

## Summary

Add a `lazy` option to module calls that defers execution until the result is actually needed.

---

## Motivation

Some computations are expensive and may not always be needed:
- Fallback values that are rarely used
- Optional enrichments based on runtime conditions
- Branches that may not be taken

Currently, all module calls are executed eagerly during DAG traversal. The `lazy` option allows deferring execution, improving performance when values aren't always needed.

---

## Syntax

```constellation
result = MyModule(input) with lazy: true
```

Or shorthand:

```constellation
result = MyModule(input) with lazy
```

---

## Semantics

### Behavior

1. Instead of executing immediately, create a lazy thunk
2. The thunk captures the module call and its inputs
3. When the result is accessed, execute the module call
4. Cache the result for subsequent accesses (memoization)

### When to Use

```constellation
# Good: Expensive fallback that's rarely needed
primary = PrimaryAPI(query) with fallback: (
    ExpensiveFallback(query) with lazy
)

# Good: Conditional computation
expensive = ExpensiveModule(data) with lazy
result = if needsExpensive then expensive else simpleDefault

# Not useful: Value that's always used
result = AlwaysNeeded(input) with lazy  # No benefit
```

### Interaction with Other Options

| Option | Interaction |
|--------|-------------|
| `timeout` | Timeout applies when lazy value is forced |
| `cache` | Cache applies after first force |
| `retry` | Retry applies when forcing |

### Type Implications

Lazy values have the same type as non-lazy:

```constellation
# Both are type String
eager = GetName(id)
lazy_val = GetName(id) with lazy

# Can be used interchangeably
result = if condition then eager else lazy_val
```

---

## Implementation Notes

### Parser Changes

```
Option ::= 'lazy' (':' Boolean)?
```

`lazy` without value defaults to `true`.

### AST Changes

```scala
case class ModuleCallOptions(
  // ...
  lazy: Option[Boolean] = None,  // NEW
)
```

### IR Changes

```scala
final case class LazyNode(
  id: UUID,
  wrappedCall: ModuleCall,
  outputType: SemanticType,
  debugSpan: Option[Span] = None
) extends IRNode
```

### Runtime Changes

```scala
class LazyValue[A](compute: IO[A]) {
  private val cached: Ref[IO, Option[A]] = Ref.unsafe(None)

  def force: IO[A] = {
    cached.get.flatMap {
      case Some(value) => IO.pure(value)
      case None => compute.flatTap(v => cached.set(Some(v)))
    }
  }
}

def createLazy[A](module: Module, inputs: Map[String, CValue]): LazyValue[A] = {
  new LazyValue(module.run(inputs))
}
```

### Forcing Rules

Lazy values are automatically forced when:
- Used as input to another module
- Used in output declarations
- Used in conditionals (the branch that's taken)
- Accessed in expressions

---

## Examples

### Lazy Fallback

```constellation
in query: String

# Expensive fallback only computed if primary fails
result = FastAPI(query) with fallback: (
    SlowBackupAPI(query) with lazy
)

out result
```

### Conditional Computation

```constellation
in data: Record
in needsEnrichment: Boolean

# Enrichment only computed if needed
enriched = EnrichmentAPI(data) with lazy
result = if needsEnrichment then enriched else data

out result
```

### Multiple Optional Computations

```constellation
in request: Request

# All computed lazily, only force what's needed
optionA = ComputeA(request) with lazy
optionB = ComputeB(request) with lazy
optionC = ComputeC(request) with lazy

# Only one will be forced
result = branch {
    request.type == "A" => optionA
    request.type == "B" => optionB
    otherwise => optionC
}

out result
```

---

## Alternatives Considered

### 1. Implicit Laziness

Make all unused computations lazy automatically.

Rejected:
- Hard to reason about when things execute
- May delay errors unexpectedly
- Explicit is better than implicit

### 2. Lazy Keyword Instead of Option

```constellation
lazy result = ExpensiveModule(input)
```

Rejected: Inconsistent with other options that use `with` clause.

### 3. Force Keyword

```constellation
lazyVal = ExpensiveModule(input) with lazy
result = force lazyVal  # Explicit forcing
```

Deferred: Automatic forcing is more ergonomic. Can add explicit force later if needed.

---

## Open Questions

1. Should lazy values be serializable for distributed execution?
2. How do lazy values interact with DAG visualization?
3. Should there be a `strict` option to force evaluation?
4. How should errors in lazy values be reported?

---

## References

- [Lazy Evaluation - Wikipedia](https://en.wikipedia.org/wiki/Lazy_evaluation)
- [Cats Effect IO.defer](https://typelevel.org/cats-effect/docs/typeclasses/sync#defer)
