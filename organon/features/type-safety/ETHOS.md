# Type Safety Ethos

> Behavioral constraints for LLMs working on the type system.

---

## Core Invariants

1. **Types are structural.** Two types with the same structure are equivalent. Type names are aliases, not distinct types.

2. **Type errors never reach runtime.** Any program that compiles will not fail due to type mismatches, missing fields, or invalid operations.

3. **Type inference is decidable.** Type checking always terminates in polynomial time. No infinite loops, no exponential blowup.

4. **Field access is validated.** Every `.field` expression is checked against the known type at that point in the program.

5. **Type operations are closed.** Merge, projection, and element-wise operations produce types that are themselves valid and checkable.

---

## Design Constraints

### When Modifying CType or CValue (core)

- **Preserve the sealed hierarchy.** All types must be representable as `CType` variants.
- **Match CType and CValue.** Every `CType` has corresponding `CValue` constructors.
- **Keep JSON serialization bidirectional.** Any `CValue` can round-trip through JSON.
- **Update TypeSystem.scala scaladoc.** The type hierarchy diagram must stay accurate.

### When Modifying SemanticType (lang-compiler)

- **SemanticType is for type checking only.** It has features (row variables, open records) that don't exist at runtime.
- **Implement toCType for new types.** Any type that reaches runtime must convert to `CType`.
- **Update prettyPrint.** Type error messages use this; it must be readable.
- **Consider subtyping.** New types need rules in `Subtyping.scala`.

### When Modifying TypeChecker

- **Accumulate errors, don't fail fast.** Use `ValidatedNel` to report all type errors in one pass.
- **Preserve source spans.** Every error must include location information.
- **Check before transform.** Validate types before constructing typed AST nodes.
- **Keep bidirectional inference.** Lambda parameters infer from context; don't require annotations everywhere.

### When Modifying Subtyping

- **Maintain transitivity.** If A <: B and B <: C, then A <: C.
- **Maintain reflexivity.** A <: A for all types.
- **SNothing is bottom.** SNothing <: T for all T.
- **Document variance.** Collections are covariant; function parameters are contravariant.

---

## Decision Heuristics

### "Should this be a type or a module?"

**Type** if:
- It describes the shape of data (fields, structure)
- It's used for validation, not computation
- It composes with other types (merge, union)

**Module** if:
- It performs computation or transformation
- It interacts with external systems
- It has side effects

### "Should this type check at compile time or runtime?"

**Compile time** if:
- The structure is known from source code (literals, type annotations)
- The operation is syntactic (field access, projection)
- We can give a precise error message

**Runtime** (via module) if:
- The structure comes from external data (JSON parsing)
- The check depends on values, not just types
- We need dynamic behavior (schema validation)

### "How do I handle a new type operation?"

1. Add the case to `TypeChecker.checkExpression`
2. Define the result type computation
3. Add the case to `TypedExpression` sealed trait
4. Implement in `IRGenerator` if it reaches runtime
5. Add tests for valid and invalid uses

### "How do I add a new subtyping rule?"

1. Define the rule mathematically (S <: T when...)
2. Add the case to `Subtyping.isSubtype`
3. Add explanation to `Subtyping.explainFailure`
4. Consider interactions with LUB/GLB computation
5. Add property tests for transitivity and reflexivity

---

## Component Boundaries

| Component | Type System Responsibility |
|-----------|---------------------------|
| `core` | Runtime type representation (`CType`, `CValue`) |
| `lang-parser` | Parse type expressions (`TypeExpr` AST) |
| `lang-compiler` | Type checking, inference (`SemanticType`, `TypeChecker`) |
| `lang-compiler` | Subtyping, type algebra (`Subtyping.scala`) |
| `lang-compiler` | Type-to-runtime conversion (`SemanticType.toCType`) |
| `runtime` | Type-safe value extraction (`CValueExtractor`) |

**Never:**
- Put type inference in `core` (core is runtime-only)
- Put runtime checks in `lang-compiler` (compiler produces typed DAG)
- Put parsing in `TypeChecker` (parser produces untyped AST)
- Return `Any` or dynamic types from type checking (all types must be concrete)

---

## What Is Out of Scope

Do not add:

- **Dependent types.** Types that depend on values (e.g., `Vector[n]` where n is a variable).
- **Higher-kinded types.** Type constructors as type parameters (e.g., `F[_]`).
- **Type-level computation.** Types that compute other types at compile time.
- **Gradual typing.** `Any` type or dynamic escape hatches.
- **Implicit conversions.** Int-to-Float, String-to-Int must be explicit modules.
- **Type classes.** Ad-hoc polymorphism via type class dictionaries.

These features add complexity without proportional benefit for data pipelines. Constellation's type system is intentionally simple.

---

## Error Message Standards

Type errors must include:

1. **What was expected.** The type the context required.
2. **What was found.** The type that was actually present.
3. **Where it happened.** Source location (line, column, span).
4. **Why it's wrong.** For complex types, explain the mismatch (missing field, wrong field type).

Example of a good error:

```
Error: Type mismatch at line 5, column 10
  Expected: { id: String, name: String, email: String }
  Found:    { id: String, name: String }
  Reason:   Record is missing required field 'email'
```

Example of a bad error:

```
Error: Type mismatch
  { id: String, name: String } is not { id: String, name: String, email: String }
```

---

## Testing Requirements

When modifying the type system:

1. **Unit tests** for each type checking rule in `TypeChecker`
2. **Unit tests** for each subtyping rule in `Subtyping`
3. **Property tests** for subtyping invariants (transitivity, reflexivity)
4. **Integration tests** for end-to-end pipeline type checking
5. **Error message tests** for all error paths (verify message quality)

Key test files:
- `modules/lang-compiler/src/test/scala/io/constellation/lang/semantic/TypeCheckerTest.scala`
- `modules/lang-compiler/src/test/scala/io/constellation/lang/semantic/SubtypingTest.scala`
- `modules/lang-compiler/src/test/scala/io/constellation/lang/semantic/BidirectionalTypeCheckerSpec.scala`

---

## Row Polymorphism (Advanced)

Constellation supports row polymorphism for functions that accept "records with at least these fields":

```scala
// Signature: forall rho. { name: String | rho } -> { greeting: String | rho }
// Accepts any record with 'name', preserves other fields
val greet = FunctionSignature(
  name = "greet",
  params = List("person" -> SOpenRecord(Map("name" -> SString), RowVar(1))),
  returns = SOpenRecord(Map("greeting" -> SString), RowVar(1)),
  rowVars = List(RowVar(1))
)
```

**Constraints for row polymorphism:**

1. **Row variables are scoped to signatures.** They don't escape into the type environment.
2. **Open records close at runtime.** `SOpenRecord` must resolve to `SRecord` before `toCType`.
3. **Unification fills row variables.** Call sites provide concrete extra fields.
4. **Fresh variables per call.** Each call to a row-polymorphic function gets fresh row variables.

See `RowUnification.scala` for implementation details.
