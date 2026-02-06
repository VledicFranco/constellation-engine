# Type System

> **Path**: `docs/runtime/type-system.md`
> **Parent**: [runtime/](./README.md)

Runtime type representations, values, and automatic type derivation.

## Overview

| Component | Purpose |
|-----------|---------|
| `CType` | Runtime type representation |
| `CValue` | Runtime value with type tag |
| `CTypeTag` | Compile-time Scala-to-CType mapping |
| `CValueInjector` | Convert Scala values to CValue |
| `CValueExtractor` | Convert CValue to Scala types |

## CType Hierarchy

```
CType
├── CString      - String type
├── CInt         - Integer type (Long)
├── CFloat       - Floating point (Double)
├── CBoolean     - Boolean type
├── CList        - Homogeneous list
├── CMap         - Key-value map
├── CProduct     - Record/struct (named fields)
├── CUnion       - Tagged union
└── COptional    - Optional/nullable
```

### Type Definitions

```scala
sealed trait CType

object CType {
  case object CString extends CType
  case object CInt extends CType
  case object CFloat extends CType
  case object CBoolean extends CType
  final case class CList(valuesType: CType) extends CType
  final case class CMap(keysType: CType, valuesType: CType) extends CType
  final case class CProduct(structure: Map[String, CType]) extends CType
  final case class COptional(innerType: CType) extends CType
}
```

## Scala-to-CType Mapping

| Scala Type | CType |
|------------|-------|
| `String` | `CType.CString` |
| `Long` | `CType.CInt` |
| `Double` | `CType.CFloat` |
| `Boolean` | `CType.CBoolean` |
| `List[A]` / `Vector[A]` | `CType.CList(A's type)` |
| `Map[K, V]` | `CType.CMap(K, V)` |
| `Option[A]` | `CType.COptional(A)` |
| Case class | `CType.CProduct(fields)` |

## CTypeTag

Compile-time type mapping from Scala types to `CType`:

```scala
trait CTypeTag[A] {
  def cType: CType
}
```

### Deriving Types

```scala
import io.constellation.deriveType

deriveType[String]  // => CType.CString
deriveType[Long]    // => CType.CInt
deriveType[List[String]]  // => CType.CList(CType.CString)

case class Person(name: String, age: Long)
deriveType[Person]
// => CType.CProduct(Map("name" -> CString, "age" -> CInt))
```

## CValue

Runtime values carry their type:

```scala
val strVal = CValue.CString("hello")
strVal.ctype  // => CType.CString

val listVal = CValue.CList(Vector(CValue.CInt(1)), CType.CInt)
listVal.ctype  // => CType.CList(CType.CInt)

val product = CValue.CProduct(
  Map("name" -> CValue.CString("Alice")),
  Map("name" -> CType.CString)
)
```

### Optional Values

```scala
val some = CValue.CSome(CValue.CString("hello"), CType.CString)
val none = CValue.CNone(CType.CString)
// Both have ctype => CType.COptional(CType.CString)
```

## CValueInjector

Converts Scala values to `CValue`:

```scala
trait CValueInjector[A] {
  def inject(value: A): CValue
}

CValueInjector[String].inject("hello")  // => CValue.CString("hello")
CValueInjector[Long].inject(42)         // => CValue.CInt(42)
CValueInjector[List[Long]].inject(List(1, 2))
// => CValue.CList(Vector(CInt(1), CInt(2)), CType.CInt)
```

## CValueExtractor

Converts `CValue` to Scala types (may fail):

```scala
trait CValueExtractor[A] {
  def extract(data: CValue): IO[A]
}

CValueExtractor[String].extract(CValue.CString("hi"))
// => IO.pure("hi")

CValueExtractor[String].extract(CValue.CInt(42))
// => IO.raiseError(RuntimeException("Expected CString, got CInt"))
```

## Complete Example

```scala
import io.constellation._
import cats.effect.IO

case class OrderInput(orderId: String, quantity: Long)

// Type derivation
val inputType: CType = deriveType[OrderInput]
// => CType.CProduct(Map("orderId" -> CString, "quantity" -> CInt))

// Value injection
val input = CValue.CProduct(
  Map(
    "orderId" -> CValue.CString("ORD-123"),
    "quantity" -> CValue.CInt(5)
  ),
  Map("orderId" -> CType.CString, "quantity" -> CType.CInt)
)

// Value extraction
val extraction: IO[(String, Long)] = for {
  orderId <- CValueExtractor[String].extract(input.value("orderId"))
  qty     <- CValueExtractor[Long].extract(input.value("quantity"))
} yield (orderId, qty)
```

## Related Files

| File | Purpose |
|------|---------|
| `modules/core/.../TypeSystem.scala` | Core implementation |
| [module-builder.md](./module-builder.md) | Types in modules |
| `modules/lang-compiler/.../SemanticType.scala` | Compiler types |
