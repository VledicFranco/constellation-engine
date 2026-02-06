# Type Derivation with Scala 3 Mirrors

This document describes the automatic type derivation API added to Constellation Engine using Scala 3 Mirrors.

## Overview

Constellation Engine now supports automatic derivation of `CType` from Scala case classes. This eliminates the need for manual type specification when working with the type system.

## API

### `deriveType[T]`

The primary user-facing function for type derivation:

```scala
import io.constellation.deriveType

case class Person(name: String, age: Long)

val personType: CType = deriveType[Person]
// Returns: CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))
```

### `CTypeTag.productTag`

The underlying given instance that enables automatic derivation for case classes:

```scala
import io.constellation.CTypeTag

case class Address(street: String, city: String)

// Summon directly
val tag: CTypeTag[Address] = summon[CTypeTag[Address]]
val addressType: CType = tag.cType
```

## Supported Types

### Primitive Types

```scala
deriveType[String]   // => CType.CString
deriveType[Long]     // => CType.CInt
deriveType[Double]   // => CType.CFloat
deriveType[Boolean]  // => CType.CBoolean
```

### Collections

```scala
deriveType[List[String]]         // => CType.CList(CType.CString)
deriveType[Vector[Long]]         // => CType.CList(CType.CInt)
deriveType[Map[String, Long]]    // => CType.CMap(CType.CString, CType.CInt)
```

### Optional Types

```scala
deriveType[Option[String]]       // => CType.COptional(CType.CString)
```

### Case Classes

```scala
case class SimpleRecord(name: String, age: Long)
deriveType[SimpleRecord]
// => CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))
```

### Nested Case Classes

```scala
case class Inner(value: String)
case class Outer(id: Long, inner: Inner)

deriveType[Outer]
// => CType.CProduct(Map(
//      "id" -> CType.CInt,
//      "inner" -> CType.CProduct(Map("value" -> CType.CString))
//    ))
```

### Case Classes with Collections

```scala
case class WithCollections(items: List[Long], mapping: Map[String, String])

deriveType[WithCollections]
// => CType.CProduct(Map(
//      "items" -> CType.CList(CType.CInt),
//      "mapping" -> CType.CMap(CType.CString, CType.CString)
//    ))
```

## How It Works

The implementation uses Scala 3's `Mirror.ProductOf[T]` to introspect case class structure at compile time:

1. **Field Names**: Extracted from `MirroredElemLabels` tuple using `constValue`
2. **Field Types**: Derived by recursively summoning `CTypeTag` for each element in `MirroredElemTypes`
3. **Structure**: Combined into a `CType.CProduct` with field name to type mapping

The `productTag` given is defined with a constraint `T <: Product`, ensuring it only applies to case classes. Primitive type tags (String, Long, etc.) have higher priority, preventing ambiguity.

## Usage in ModuleBuilder

The ModuleBuilder API already uses Scala 3 Mirrors internally for automatic type inference:

```scala
case class TextInput(text: String)
case class TextOutput(result: String)

val uppercaseModule = ModuleBuilder
  .metadata("Uppercase", "Converts text to uppercase", 1, 0)
  .implementationPure[TextInput, TextOutput] { input =>
    TextOutput(input.text.toUpperCase)
  }
  .build
```

The input/output types are automatically derived from `TextInput` and `TextOutput`.

## Limitations

1. **Product Types Only**: Only case classes (Product types) are supported. Sum types (sealed traits, enums) are not automatically derived.

2. **Recursive Types**: Directly recursive types may cause compilation issues:
   ```scala
   // NOT SUPPORTED
   case class Node(value: String, children: List[Node])
   ```

3. **Type Parameters**: Generic case classes require explicit type arguments:
   ```scala
   case class Container[A](value: A)

   // Must specify concrete type
   deriveType[Container[String]]  // Works
   // deriveType[Container]       // Doesn't work
   ```

## Migration

If you were previously specifying types manually in module definitions, you can now rely on automatic derivation:

**Before (manual):**
```scala
val spec = ModuleNodeSpec(
  name = "MyModule",
  consumes = Map("text" -> CType.CString, "count" -> CType.CInt),
  produces = Map("result" -> CType.CString),
  ...
)
```

**After (automatic):**
```scala
case class MyInput(text: String, count: Long)
case class MyOutput(result: String)

val module = ModuleBuilder
  .metadata("MyModule", "Description", 1, 0)
  .implementationPure[MyInput, MyOutput] { input => ... }
  .build
// Types are automatically derived from case class fields
```

## Related

- `TypeSystem.scala` - Core type system definitions
- `Runtime.scala` - ModuleBuilder and runtime type derivation
- `ModuleBuilder` - Module definition API
