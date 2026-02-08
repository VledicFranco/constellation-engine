# Standard Library Ethos

> Normative constraints for the built-in function library.

---

## Identity

- **IS:** Built-in function library providing common operations for constellation-lang pipelines
- **IS NOT:** A runtime executor, parser, type checker, or user-defined module registry

---

## Semantic Mapping

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `StdLib` | Central aggregator mixing in all category traits and exposing unified accessors |
| `FunctionSignature` | Type contract for a function: name, parameter types, return type, and namespace |
| `MathFunctions` | Arithmetic operations: add, subtract, multiply, divide, max, min, abs, modulo, round, negate |
| `StringFunctions` | String manipulation: concat, string-length, join, split, contains, trim, replace |
| `ListFunctions` | List operations: list-length, list-first, list-last, list-is-empty, list-sum, list-concat, list-contains, list-reverse |
| `BooleanFunctions` | Logical operations: and, or, not |
| `ComparisonFunctions` | Equality and ordering: eq-int, eq-string, gt, lt, gte, lte |
| `UtilityFunctions` | General utilities: identity, log |
| `HigherOrderFunctions` | Lambda-based operations: filter, map, all, any (executed via InlineTransform) |
| `TypeConversionFunctions` | Type conversions: to-string, to-int, to-float |
| `StdLib.compiler` | Pre-configured LangCompiler with all stdlib functions registered |
| `StdLib.allModules` | Map of module names to Module.Uninitialized implementations |
| `StdLib.allSignatures` | List of all function signatures for type checking |
| `StdLib.registerAll` | Builder method to register all stdlib functions with a custom compiler |

For complete type signatures, see:
- [io.constellation.stdlib](/organon/generated/io.constellation.stdlib.md)
- [io.constellation.stdlib.categories](/organon/generated/io.constellation.stdlib.categories.md)

---

## Invariants

### 1. Every function has both a signature and a module

Function signatures (for type checking) and module implementations (for execution) are defined in pairs. A function cannot exist for compilation without a corresponding runtime implementation.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/categories/MathFunctions.scala#mathSignatures` |
| Test | `modules/lang-stdlib/src/test/scala/io/constellation/stdlib/StdLibTest.scala#contain all modules` |

**Exception:** Higher-order functions (filter, map, all, any) have signatures but no module implementations because they are executed via InlineTransform at runtime.

### 2. Function signatures match module I/O types

The parameter types and return type declared in a FunctionSignature must align with the input/output case classes of the corresponding Module.Uninitialized. Mismatches cause type errors at pipeline compilation.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/categories/MathFunctions.scala#addSignature` |
| Test | `modules/lang-stdlib/src/test/scala/io/constellation/stdlib/StdLibTest.scala#have correct types` |

### 3. Functions are namespaced consistently

All stdlib functions belong to a namespace following the pattern `stdlib.<category>`. Function lookup supports both unqualified names (with `use` imports) and fully qualified names.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/StdLib.scala#registerAll` |
| Test | `modules/lang-stdlib/src/test/scala/io/constellation/stdlib/StdLibTest.scala#expose function registry with namespaces` |

### 4. Modules are pure or explicitly effectful

Pure functions use `implementationPure` and produce no side effects. Functions with side effects (error raising, logging) use `implementation` with explicit `IO`. There is no implicit state mutation.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/categories/MathFunctions.scala#divideModule` |
| Test | `modules/lang-stdlib/src/test/scala/io/constellation/stdlib/StdLibEdgeCasesTest.scala#raise error for division by zero` |

### 5. Edge cases raise errors rather than returning sentinel values

Operations that can fail (division by zero, empty list access) raise explicit errors rather than returning null, NaN, or other sentinel values. This ensures errors are visible and traceable.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/categories/MathFunctions.scala#moduloModule` |
| Test | `modules/lang-stdlib/src/test/scala/io/constellation/stdlib/StdLibEdgeCasesTest.scala#raise error for empty list` |

---

## Principles (Prioritized)

1. **Completeness over minimalism** - Provide a rich set of common operations so users rarely need custom modules for basic tasks
2. **Predictable semantics** - Functions behave like their mathematical or programming counterparts (e.g., integer division truncates toward zero)
3. **Type safety first** - Every function has a precise type signature; no dynamic typing or implicit conversions
4. **Fail fast** - Invalid operations (divide by zero, empty list access) fail immediately with clear errors
5. **Namespace hygiene** - Functions are organized into logical categories to avoid name collisions

---

## Decision Heuristics

- When adding a new function, choose the most specific category; create a new category trait only if the function doesn't fit existing ones
- When choosing between pure and effectful implementation, prefer pure unless the operation can fail or has side effects
- When naming functions, use lowercase-hyphenated names (e.g., `list-length`, `eq-string`) for consistency
- When a function operates on a specific type, include the type in the name to avoid overloading ambiguity (e.g., `eq-int` vs `eq-string`)
- When implementing higher-order functions, prefer InlineTransform to avoid module creation overhead per element
- When in doubt about error handling, raise an explicit error rather than returning a default value

---

## Out of Scope

- DAG compilation (see [compiler/](../compiler/))
- Module execution and runtime (see [runtime/](../runtime/))
- Type system definitions (see [core/](../core/))
- HTTP API and WebSocket handling (see [http-api/](../http-api/))
- User-defined modules (see [example-app/](../example-app/))

---

## Implements Features

| Feature | Artifacts |
|---------|-----------|
| [Type Safety](../../features/type-safety/) | Type conversion functions, type-specific operations (eq-int, eq-string, etc.) |

Note: StdLib provides utility functions used across all features but is not the primary implementation of any single feature.
