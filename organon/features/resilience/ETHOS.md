# Resilience Ethos

> Behavioral constraints for LLMs working on resilience features.

---

## Core Invariants

1. **Resilience is declarative.** Options are language syntax (`with retry: 3`), not module calls or library functions.

2. **Options are compile-time validated.** Invalid option values must be caught by the compiler, not at runtime.

3. **Defaults are safe.** No retry, no cache, no timeout, fail-fast. Unsafe behavior requires explicit opt-in.

4. **Options compose orthogonally.** Each option works independently. Adding `cache` doesn't change how `retry` works.

5. **Execution order is fixed.** Cache → Execution → Timeout → Retry → Fallback. This order is not configurable.

---

## Design Constraints

### When Adding a New Option

- **Must have an RFC.** New options require a design document before implementation.
- **Must be orthogonal.** The option should not change the behavior of existing options.
- **Must have a safe default.** The default must be "do nothing" or the most conservative choice.
- **Must validate at compile time.** Invalid values are compile errors, not runtime errors.
- **Must document in three places:** RFC, feature docs, language reference.

### When Modifying Option Behavior

- **Check backward compatibility.** Existing pipelines must continue to work.
- **Update the RFC.** Design changes are documented in the original RFC.
- **Update all documentation.** Feature docs, language reference, and examples.

### When Implementing in Runtime

- **Respect execution order.** Cache check happens before execution; fallback happens after all retries.
- **Use the SPI.** Cache backend, metrics, and execution listeners are pluggable.
- **Propagate errors correctly.** Failed attempts surface via execution listeners; only final failure uses fallback.

---

## Decision Heuristics

### "Should this be an option or a module?"

**Option** if:
- It modifies *how* a module executes (retry, timeout, cache)
- It's reusable across many modules
- It has a simple configuration (a few parameters)

**Module** if:
- It performs computation or transformation
- It has complex, domain-specific logic
- It needs access to external systems beyond the wrapped module

### "Should this option have a default?"

All options have defaults. The default should be:
- **Safe:** Does not change behavior from "no option specified"
- **Conservative:** Prefers failing fast over silent degradation
- **Documented:** The default is explicit in docs and error messages

### "How do I handle option conflicts?"

Options don't conflict—they compose. If a user specifies incompatible values, that's a validation error:

```constellation
# Error: fallback type {name: String} doesn't match module output {id: Int, name: String}
result = GetUser(id) with fallback: {name: "Unknown"}
```

---

## Component Boundaries

| Component | Resilience Responsibility |
|-----------|---------------------------|
| `lang-parser` | Parse `with` clause syntax |
| `lang-compiler` | Validate option types and values, attach to DAG nodes |
| `runtime` | Execute options in correct order |
| `runtime` (SPI) | Delegate to pluggable backends (cache, metrics) |

**Never:**
- Put resilience logic in modules (modules are pure business logic)
- Put option parsing in runtime (parsing is compiler's job)
- Put execution logic in compiler (compiler produces DAG, runtime executes)

---

## What Is Out of Scope

Do not add:

- **Custom retry predicates.** (Retry on specific errors only) — Requires RFC.
- **Dynamic option values.** (Read retry count from config at runtime) — Options are static.
- **Circuit breakers.** (Fail-fast after N failures) — Requires RFC.
- **Bulkheads.** (Isolate failure domains) — Requires RFC.
- **Per-attempt hooks.** (Log each retry attempt) — Use execution listeners.

These are potential future features, not current scope.

---

## Testing Requirements

When modifying resilience:

1. **Unit tests** for option validation in compiler
2. **Unit tests** for execution behavior in runtime
3. **Integration tests** for end-to-end option behavior
4. **Benchmark tests** for performance-sensitive paths (caching, throttling)

All resilience options have dedicated test files in both `lang-compiler` and `runtime` modules.
