# Core Ethos

> Normative constraints for the type system foundation.

---

## Identity

- **IS:** Type system foundation providing runtime type/value representations for all pipeline data
- **IS NOT:** An execution engine, compiler, parser, or module implementation

---

## Semantic Mapping

### Type Representations

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `CType` | Sealed trait representing runtime types that can flow through a DAG |
| `CType.CString` | String type representation |
| `CType.CInt` | Integer type (Long) representation |
| `CType.CFloat` | Floating point type (Double) representation |
| `CType.CBoolean` | Boolean type representation |
| `CType.CList(T)` | Homogeneous list type with element type T |
| `CType.CMap(K, V)` | Key-value map type with key type K and value type V |
| `CType.CProduct(structure)` | Record/struct type with named fields |
| `CType.CUnion(structure)` | Tagged union type with variant options |
| `CType.COptional(T)` | Optional type wrapping inner type T |

### Value Representations

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `CValue` | Sealed trait representing runtime values with self-describing types |
| `CValue.CString(value)` | String value carrying a `String` |
| `CValue.CInt(value)` | Integer value carrying a `Long` |
| `CValue.CFloat(value)` | Float value carrying a `Double` |
| `CValue.CBoolean(value)` | Boolean value carrying a `Boolean` |
| `CValue.CList(values, subtype)` | List value with elements and declared element type |
| `CValue.CMap(pairs, keysType, valuesType)` | Map value with key-value pairs and declared types |
| `CValue.CProduct(fields, structure)` | Record value with named fields and declared structure |
| `CValue.CUnion(value, structure, tag)` | Tagged union value with variant tag |
| `CValue.CSome(value, innerType)` | Present optional value |
| `CValue.CNone(innerType)` | Absent optional value |

### Type Derivation

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `CTypeTag[A]` | Type class mapping Scala types to CType at compile time |
| `deriveType[T]` | Convenience function to derive CType from Scala type T |
| `CValueInjector[A]` | Type class for converting Scala values to CValue (pure) |
| `CValueExtractor[A]` | Type class for converting CValue to Scala values (effectful) |

### Error Hierarchy

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `ConstellationError` | Base trait for all domain errors with code, message, context |
| `TypeError` | Type system and conversion errors (category: "type") |
| `TypeMismatchError` | Value type does not match expected type |
| `TypeConversionError` | Conversion between formats failed |
| `CompilerError` | Compilation and IR generation errors (category: "compiler") |
| `RuntimeError` | Execution and runtime errors (category: "runtime") |

### Specification Types

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `DagSpec` | Complete specification of a compiled DAG |
| `ModuleNodeSpec` | Specification for a single module node (consumes/produces) |
| `DataNodeSpec` | Specification for a data node with type and inline transform |
| `InlineTransform` | Lightweight operation executed directly on data nodes |

For complete type signatures, see:
- [io.constellation](/docs/generated/io.constellation.md)

---

## Invariants

### 1. Every CValue reports its correct CType

Every `CValue` instance correctly reports its type via the `ctype` method. The type is determined by the value's structure, not external context.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/core/src/main/scala/io/constellation/TypeSystem.scala#CValue.ctype` |
| Test | `modules/core/src/test/scala/io/constellation/TypeSystemTest.scala#CValue should report correct ctype` |
| Property Test | `modules/core/src/test/scala/io/constellation/property/TypeSystemPropertyTest.scala#CValue should always have a ctype matching its construction type` |

### 2. Type derivation is deterministic and consistent

`CTypeTag` derivation for the same Scala type always produces the same `CType`. Primitive types take priority over product derivation to avoid ambiguity.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/core/src/main/scala/io/constellation/TypeSystem.scala#CTypeTag` |
| Test | `modules/core/src/test/scala/io/constellation/TypeSystemTest.scala#CTypeTag should not interfere with primitive type tags` |

### 3. Injection and extraction are inverses (roundtrip)

For supported types, `extract(inject(value)) == value`. Injecting a Scala value to CValue and extracting back yields the original value.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/core/src/main/scala/io/constellation/TypeSystem.scala#CValueInjector, CValueExtractor` |
| Test | `modules/core/src/test/scala/io/constellation/TypeSystemTest.scala#Roundtrip should inject and extract values correctly` |

### 4. Extraction fails with descriptive errors on type mismatch

When extracting a CValue to an incompatible Scala type, extraction raises an `IO.raiseError` with a message indicating the expected and actual types.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/core/src/main/scala/io/constellation/TypeSystem.scala#CValueExtractor.stringExtractor` |
| Test | `modules/core/src/test/scala/io/constellation/TypeSystemTest.scala#CValueExtractor should fail with descriptive error on type mismatch` |

### 5. Composite CValues have structurally consistent children

List elements match the declared subtype. Product fields match the declared structure. Map keys and values match their declared types.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/core/src/main/scala/io/constellation/TypeSystem.scala#CValue.CList, CValue.CProduct, CValue.CMap` |
| Test | `modules/core/src/test/scala/io/constellation/property/TypeSystemPropertyTest.scala#CValue structural invariant` |

### 6. Errors are serializable and machine-readable

All `ConstellationError` instances provide `errorCode`, `message`, `context`, and `toJson` for programmatic handling and API responses.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/core/src/main/scala/io/constellation/ConstellationError.scala#ConstellationError` |
| Test | `modules/core/src/test/scala/io/constellation/ConstellationErrorTest.scala` |

---

## Principles (Prioritized)

1. **Type safety over convenience** — Never allow implicit type coercion; all conversions are explicit
2. **Self-describing values** — Every CValue carries its type; no external context needed to interpret data
3. **Scala 3 native** — Use given instances, inline derivation, and Mirrors; avoid macros or runtime reflection
4. **Composition over inheritance** — Types compose (CList of CProduct) rather than extending specialized variants

---

## Decision Heuristics

- When adding a new primitive type, add entries to CType, CValue, CTypeTag, CValueInjector, and CValueExtractor
- When uncertain about type representation, prefer structural types (CProduct) over nominal types
- When adding error types, extend the appropriate sealed trait (TypeError, CompilerError, RuntimeError)
- When optimizing, preserve roundtrip semantics and type consistency

---

## Out of Scope

- Module execution and runtime (see [runtime/](../runtime/))
- Parsing and AST (see [parser/](../parser/) or [lang-ast/](../lang-ast/))
- Compilation and IR generation (see [compiler/](../compiler/))
- HTTP server and endpoints (see [http-api/](../http-api/))
- Standard library functions (see [stdlib/](../stdlib/))

---

## Implements Features

| Feature | Artifacts |
|---------|-----------|
| [Type Safety](../../features/type-safety/) | CType, CValue, CTypeTag, CValueInjector, CValueExtractor, TypeSystem |
