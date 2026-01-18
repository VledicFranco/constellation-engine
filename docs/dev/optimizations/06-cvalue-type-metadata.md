# Optimization 06: CValue Type Metadata Stripping

**Priority:** 6 (Medium Impact)
**Expected Gain:** 20-40% memory reduction
**Complexity:** Medium
**Status:** Not Implemented

---

## Problem Statement

The `CValue` type carries type metadata at every level of the data structure:

```scala
// TypeSystem.scala:98-109

case class CListValue(values: List[CValue], subType: CType) extends CValue
case class CMapValue(values: Map[CValue, CValue], keysType: CType, valuesType: CType) extends CValue
```

For a list of 10,000 integers:
- **Useful data:** 10,000 integers = ~40KB
- **Type metadata:** `subType: CInt` repeated conceptually, plus wrapper overhead
- **Actual overhead:** Each `CIntValue` is an object with header (~16 bytes) + value (4 bytes padded to 8)

### Memory Breakdown

```scala
// List of 10,000 integers
CListValue(
  values = List(
    CIntValue(1),   // 24 bytes (object header + int + padding)
    CIntValue(2),   // 24 bytes
    // ... 10,000 times
  ),
  subType = CInt    // 16 bytes (object reference)
)
// Total: ~240KB for 40KB of actual data (6x overhead)
```

---

## Proposed Solution

Separate type information from values, storing types once per data node rather than embedded in every value.

### Strategy

```
Current:
  DataNode → CValue (with embedded types at every level)

Optimized:
  DataNode → (CType, RawValue)
  where RawValue is unboxed Scala types
```

### Implementation

#### Step 1: Define RawValue

```scala
// New: RawValue.scala

/**
 * Unboxed value representation without type metadata.
 * Type information is stored separately in the DataNodeSpec.
 */
sealed trait RawValue

object RawValue {
  // Primitives - unboxed
  case class RString(value: String) extends RawValue
  case class RInt(value: Int) extends RawValue with AnyVal  // Value class!
  case class RFloat(value: Double) extends RawValue with AnyVal
  case class RBool(value: Boolean) extends RawValue with AnyVal

  // Collections - homogeneous, no per-element type info
  case class RList(values: Array[RawValue]) extends RawValue
  case class RMap(values: java.util.HashMap[RawValue, RawValue]) extends RawValue

  // Records - field names known from type
  case class RProduct(values: Array[RawValue]) extends RawValue  // Indexed by field order
}
```

#### Step 2: Specialized Primitive Lists

For ML workloads, primitive arrays are critical:

```scala
// Specialized list representations
sealed trait RawList extends RawValue {
  def length: Int
}

// Unboxed primitive arrays - HUGE memory savings
case class RIntList(values: Array[Int]) extends RawList {
  def length: Int = values.length
}

case class RFloatList(values: Array[Double]) extends RawList {
  def length: Int = values.length
}

case class RStringList(values: Array[String]) extends RawList {
  def length: Int = values.length
}

// Generic list for nested types
case class RGenericList(values: Array[RawValue]) extends RawList {
  def length: Int = values.length
}
```

#### Step 3: Type-Aware Value Access

```scala
// TypedValueAccessor.scala

class TypedValueAccessor(cType: CType) {

  def getInt(raw: RawValue): Int = raw match {
    case RawValue.RInt(v) => v
    case _ => throw new TypeMismatchException(s"Expected Int, got $raw")
  }

  def getList(raw: RawValue): RawList = raw match {
    case list: RawList => list
    case _ => throw new TypeMismatchException(s"Expected List, got $raw")
  }

  def getField(raw: RawValue, fieldName: String): RawValue = {
    cType match {
      case CProduct(fields) =>
        val fieldIndex = fields.keys.toList.sorted.indexOf(fieldName)
        raw.asInstanceOf[RawValue.RProduct].values(fieldIndex)
      case _ =>
        throw new TypeMismatchException(s"Cannot get field from $cType")
    }
  }

  // Convert back to CValue when needed (e.g., for JSON output)
  def toCValue(raw: RawValue): CValue = (raw, cType) match {
    case (RawValue.RInt(v), CInt) => CIntValue(v)
    case (RawValue.RFloat(v), CFloat) => CFloatValue(v)
    case (RawValue.RString(v), CString) => CStringValue(v)
    case (RawValue.RBool(v), CBoolean) => CBooleanValue(v)

    case (list: RIntList, CList(CInt)) =>
      CListValue(list.values.map(CIntValue(_)).toList, CInt)

    case (list: RFloatList, CList(CFloat)) =>
      CListValue(list.values.map(CFloatValue(_)).toList, CFloat)

    case (RawValue.RProduct(values), CProduct(fields)) =>
      val fieldList = fields.toList.sortBy(_._1)
      val converted = fieldList.zip(values).map { case ((name, fieldType), value) =>
        name -> TypedValueAccessor(fieldType).toCValue(value)
      }.toMap
      CProductValue(converted)

    case _ =>
      throw new TypeMismatchException(s"Cannot convert $raw to $cType")
  }
}
```

#### Step 4: Update Runtime Data Table

```scala
// Runtime modifications

// Change data table to store raw values with separate type info
type MutableDataTable = Map[UUID, Deferred[IO, RawValue]]

// Type information comes from DagSpec
def getTypeForNode(dagSpec: DagSpec, nodeId: UUID): CType = {
  dagSpec.dataNodes.find(_.id == nodeId).map(_.cType)
    .getOrElse(throw new RuntimeException(s"Unknown node: $nodeId"))
}
```

---

## Memory Comparison

### Test Case: Feature Vector (10,000 floats)

| Representation | Memory Usage | Overhead |
|----------------|--------------|----------|
| `CListValue[CFloatValue]` | ~240KB | 6x |
| `RFloatList(Array[Double])` | ~80KB | 1x |

### Test Case: Batch Embeddings (1000 x 1536 floats)

| Representation | Memory Usage | Overhead |
|----------------|--------------|----------|
| Nested `CListValue` | ~37MB | 6x |
| `Array[RFloatList]` | ~12MB | 1x |

### Test Case: User Records (10,000 records with 5 fields)

| Representation | Memory Usage | Notes |
|----------------|--------------|-------|
| `CListValue[CProductValue]` | ~4MB | Per-field type info |
| `RGenericList[RProduct]` | ~2MB | Indexed field access |

---

## Conversion Points

Type metadata is still needed at system boundaries:

```
┌─────────────────────────────────────────────────────────────┐
│                     INTERNAL RUNTIME                         │
│                   (RawValue + CType)                         │
├──────────────┬────────────────────────────┬─────────────────┤
│              │                            │                  │
│   JSON Input │     Module Execution       │   JSON Output    │
│   ──────────►│     (RawValue only)       │◄──────────       │
│   (convert)  │                            │   (convert)      │
│              │                            │                  │
└──────────────┴────────────────────────────┴─────────────────┘
```

**Convert to RawValue:** At JSON input parsing
**Use RawValue:** During all internal execution
**Convert to CValue:** Only for JSON output (or keep as RawValue with streaming JSON)

---

## Module Interface Changes

Modules currently work with typed Scala values, not CValue:

```scala
// This stays the same - modules see Scala types
case class EmbeddingInput(text: String)
case class EmbeddingOutput(embedding: List[Double])

val embeddingModule = ModuleBuilder
  .metadata("Embedding", "Generate embeddings", 1, 0)
  .implementationPure[EmbeddingInput, EmbeddingOutput] { input =>
    EmbeddingOutput(generateEmbedding(input.text))
  }
  .build
```

The conversion happens in the runtime layer:

```scala
// Runtime converts RawValue → Scala type → RawValue
def executeModule[I, O](
  module: Module[I, O],
  input: RawValue,
  inputType: CType,
  outputType: CType
): RawValue = {
  val scalaInput = rawValueToScala[I](input, inputType)
  val scalaOutput = module.run(scalaInput)
  scalaToRawValue(scalaOutput, outputType)
}
```

---

## Gradual Migration Path

### Phase 1: Internal Only

1. Add `RawValue` types alongside `CValue`
2. Use `RawValue` in data table internally
3. Convert at boundaries (JSON ↔ RawValue)
4. Keep `CValue` for external API compatibility

### Phase 2: Optimize Hot Paths

1. Identify modules that process large arrays
2. Add specialized `RawValue` support for those modules
3. Benchmark and iterate

### Phase 3: Full Migration

1. Deprecate `CValue` for internal use
2. Update all modules to use `RawValue`
3. Remove `CValue` from hot paths

---

## Implementation Checklist

- [ ] Define `RawValue` sealed trait and implementations
- [ ] Add specialized primitive array types (`RIntList`, `RFloatList`)
- [ ] Create `TypedValueAccessor` for type-aware operations
- [ ] Update `JsonCValueConverter` to produce `RawValue`
- [ ] Modify runtime data table to use `RawValue`
- [ ] Update module execution to convert at boundaries
- [ ] Benchmark memory usage with ML workloads
- [ ] Profile CPU impact of conversions

---

## Files to Modify

| File | Changes |
|------|---------|
| New: `modules/core/.../RawValue.scala` | Raw value types |
| New: `modules/core/.../TypedValueAccessor.scala` | Type-aware access |
| `modules/runtime/.../JsonCValueConverter.scala` | Add RawValue conversion |
| `modules/runtime/.../Runtime.scala` | Use RawValue in data table |
| `modules/core/.../TypeSystem.scala` | Add RawValue ↔ CValue conversion |

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Type safety regression | Keep CType alongside RawValue, validate at boundaries |
| Module compatibility | Conversion layer maintains Scala type interface |
| Debugging difficulty | Add `toDebugString` that includes type info |
| Increased complexity | Gradual migration, keep CValue for simple cases |

---

## Related Optimizations

- [JSON Conversion Optimization](./03-json-conversion-optimization.md) - Produces RawValue directly from JSON
- [Module Initialization Pooling](./02-module-initialization-pooling.md) - Pool RawValue containers
