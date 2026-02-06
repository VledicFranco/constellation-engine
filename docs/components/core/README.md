# Core Component

> **Path**: `docs/components/core/`
> **Parent**: [components/](../README.md)
> **Module**: `modules/core/`

The type system foundation for Constellation Engine.

## Key Files

| File | Purpose |
|------|---------|
| `TypeSystem.scala` | CType, CValue, type tags, injectors, extractors |
| `ConstellationError.scala` | Structured error hierarchy |
| `Spec.scala` | DagSpec, ModuleNodeSpec, DataNodeSpec definitions |
| `ContentHash.scala` | SHA-256 hashing for cache keys |
| `RawValue.scala` | Dynamic value wrapper for module I/O |
| `InlineTransform.scala` | Inline transform definitions for optimized execution |
| `ModuleCallOptions.scala` | Runtime module call option representation |

## Role in the System

The core module provides the foundational types that all other modules depend on:

```
                    ┌─────────────┐
                    │    core     │
                    └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
   [runtime]         [lang-parser]     [lang-compiler]
```

**Core defines:**
- Runtime type representations (CType, CValue)
- Type-safe data conversion (injectors, extractors)
- DAG specification structures (DagSpec, ModuleNodeSpec)
- Error types for the entire system

## Type System

### CType Hierarchy

Runtime type representation for pipeline data:

```scala
CType (sealed trait)
├── CString       // String type
├── CInt          // Long integer type
├── CFloat        // Double precision float
├── CBoolean      // Boolean type
├── CList(T)      // Homogeneous list
├── CMap(K, V)    // Key-value map
├── CProduct      // Named fields (records)
├── CUnion        // Tagged union
└── COptional(T)  // Optional value
```

### CValue Hierarchy

Runtime value representation:

```scala
CValue (sealed trait)
├── CString(value: String)
├── CInt(value: Long)
├── CFloat(value: Double)
├── CBoolean(value: Boolean)
├── CList(values: Vector[CValue], subtype: CType)
├── CMap(pairs: Vector[(CValue, CValue)], keysType, valuesType)
├── CProduct(fields: Map[String, CValue], structure)
├── CUnion(value: CValue, structure, tag: String)
├── CSome(value: CValue, innerType)
└── CNone(innerType)
```

### Type Derivation

Compile-time type mapping from Scala to CType:

```scala
// CTypeTag type class
deriveType[String]  // => CType.CString
deriveType[Long]    // => CType.CInt
deriveType[List[String]]  // => CType.CList(CType.CString)

// Case class derivation
case class Person(name: String, age: Long)
deriveType[Person]  // => CType.CProduct(Map("name" -> CString, "age" -> CInt))
```

### Injectors and Extractors

Type-safe conversion between Scala and CValue:

```scala
// Injection: Scala → CValue
CValueInjector[String].inject("hello")  // => CValue.CString("hello")
CValueInjector[List[Long]].inject(List(1, 2))  // => CValue.CList(...)

// Extraction: CValue → Scala (effectful)
CValueExtractor[String].extract(cvalue): IO[String]
CValueExtractor[List[Long]].extract(cvalue): IO[List[Long]]
```

## Error Hierarchy

Structured exceptions for domain-specific errors:

```
ConstellationError (base trait)
├── TypeError
│   ├── TypeMismatchError
│   └── TypeConversionError
├── CompilerError
│   ├── NodeNotFoundError
│   ├── UndefinedVariableError
│   ├── CycleDetectedError
│   └── UnsupportedOperationError
└── RuntimeError
    ├── ModuleNotFoundError
    ├── ModuleExecutionError
    ├── InputValidationError
    └── DataNotFoundError
```

All errors include:
- `errorCode`: Programmatic identifier (e.g., `TYPE_MISMATCH`)
- `message`: Human-readable description
- `context`: Map of debugging information
- `toJson`: JSON serialization for API responses

## Specification Types

### DagSpec

Complete specification of a compiled DAG:

```scala
case class DagSpec(
  metadata: ComponentMetadata,
  modules: Map[UUID, ModuleNodeSpec],
  data: Map[UUID, DataNodeSpec],
  inEdges: Set[(UUID, UUID)],   // data → module
  outEdges: Set[(UUID, UUID)],  // module → data
  declaredOutputs: List[String],
  outputBindings: Map[String, UUID]
)
```

### ModuleNodeSpec

Specification for a single module in the DAG:

```scala
case class ModuleNodeSpec(
  metadata: ComponentMetadata,
  consumes: Map[String, CType],  // input parameters
  produces: Map[String, CType]   // output values
)
```

### DataNodeSpec

Specification for a data node:

```scala
case class DataNodeSpec(
  name: String,
  nicknames: Map[UUID, String],  // module-specific aliases
  cType: CType,
  inlineTransform: Option[InlineTransform],
  transformInputs: Map[String, UUID]
)
```

## Inline Transforms

Lightweight operations that execute directly on data nodes (no synthetic module):

| Transform | Purpose |
|-----------|---------|
| `MergeTransform` | Combine two records |
| `ProjectTransform` | Select fields from record |
| `FieldAccessTransform` | Extract single field |
| `ConditionalTransform` | If-then-else selection |
| `LiteralTransform` | Constant value injection |
| `AndTransform`, `OrTransform`, `NotTransform` | Boolean operations |
| `GuardTransform` | Conditional optional wrapper |
| `CoalesceTransform` | Optional unwrapping with default |
| `StringInterpolationTransform` | Template string building |
| `FilterTransform`, `MapTransform`, `AllTransform`, `AnyTransform` | HOF operations |

## Dependencies

- **Depends on:** None (foundation module)
- **Depended on by:** `runtime`, `lang-parser`, `lang-compiler`

## Features Using This Component

| Feature | Core Role |
|---------|-----------|
| [Type safety](../../features/type-safety/) | CType/CValue system |
| [Module execution](../../runtime/module-builder.md) | Type derivation for I/O |
| [Error handling](../../reference/errors.md) | ConstellationError hierarchy |
