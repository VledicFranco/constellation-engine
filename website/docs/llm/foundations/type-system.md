---
title: "Type System Foundations"
sidebar_position: 1
description: "Deep dive into Constellation's runtime and semantic type system: CType, CValue, type checking, subtyping, and type-driven development patterns."
---

# Type System Foundations

This guide provides a comprehensive reference for the Constellation type system, covering both runtime types (`CType`/`CValue`) and compile-time semantic types (`SemanticType`). Understanding this system is crucial for implementing modules, extending the compiler, and debugging type-related issues.

## Table of Contents

1. [Overview and Mental Model](#overview-and-mental-model)
2. [Runtime Type System (CType/CValue)](#runtime-type-system-ctypecvalue)
3. [Semantic Type System](#semantic-type-system)
4. [Type Compatibility and Subtyping](#type-compatibility-and-subtyping)
5. [Type Tags, Injectors, and Extractors](#type-tags-injectors-and-extractors)
6. [Type Inference](#type-inference)
7. [Working Examples](#working-examples)
8. [Edge Cases and Common Mistakes](#edge-cases-and-common-mistakes)
9. [Type-Driven Development Patterns](#type-driven-development-patterns)

---

## Overview and Mental Model

Constellation has **two parallel type systems** that work together:

```
┌──────────────────────────────────────────────────────────────┐
│                    CONSTELLATION TYPE SYSTEM                  │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────────────┐         ┌─────────────────────┐   │
│  │  COMPILE TIME       │         │  RUNTIME            │   │
│  │                     │         │                     │   │
│  │  SemanticType       │  maps   │  CType / CValue     │   │
│  │  (type checking)    │ ─────>  │  (execution)        │   │
│  │                     │   to    │                     │   │
│  └─────────────────────┘         └─────────────────────┘   │
│                                                               │
│  - Type inference                 - Runtime values           │
│  - Subtyping checks               - DAG execution            │
│  - Error messages                 - Module I/O               │
│  - Union/Optional                 - Type safety              │
│  - Row polymorphism                                          │
│                                                               │
└──────────────────────────────────────────────────────────────┘
```

### Why Two Type Systems?

**`CType` and `CValue` (Runtime):**
- Represent **actual data** flowing through DAG pipelines
- Used by modules at execution time
- Simple, efficient, concrete types
- Defined in `modules/core/src/main/scala/io/constellation/TypeSystem.scala`

**`SemanticType` (Compile-time):**
- Used during **type checking and compilation**
- Supports advanced features like unions, optionals, row polymorphism
- Enables better error messages and type inference
- Defined in `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/SemanticType.scala`

```
Constellation Source (.cst)
         ↓
    [PARSER] → AST
         ↓
  [TYPE CHECKER] → Uses SemanticType
         ↓
    [COMPILER] → Converts SemanticType → CType
         ↓
    [RUNTIME] → Uses CType/CValue
         ↓
      Result
```

---

## Runtime Type System (CType/CValue)

### CType Hierarchy

`CType` represents the **type** of a value at runtime. Every `CValue` has a corresponding `CType`.

```
CType (sealed trait)
├── CString                      // String type
├── CInt                         // 64-bit signed integer (Long)
├── CFloat                       // 64-bit double precision (Double)
├── CBoolean                     // Boolean type
├── CList(valuesType: CType)     // Homogeneous list
├── CMap(keysType, valuesType)   // Key-value map
├── CProduct(structure: Map)     // Record with named fields
├── CUnion(structure: Map)       // Tagged union (discriminated)
└── COptional(innerType: CType)  // Optional value
```

**ASCII Diagram:**

```
                     CType
                       │
        ┌──────────────┼──────────────┐
        │              │              │
    Primitives    Collections     Structured
        │              │              │
    ┌───┴───┐      ┌───┴───┐      ┌──┴───┐
    │       │      │       │      │      │
CString  CInt   CList   CMap   CProduct CUnion
      CFloat              └──────────┘
    CBoolean              COptional
```

### CType Details

#### Primitives

| CType | Scala Type | Description | Example |
|-------|-----------|-------------|---------|
| `CType.CString` | `String` | UTF-8 text | `"hello"` |
| `CType.CInt` | `Long` | 64-bit signed integer | `42L` |
| `CType.CFloat` | `Double` | 64-bit double precision | `3.14` |
| `CType.CBoolean` | `Boolean` | True/false | `true` |

```scala
// Primitive type examples
val stringType: CType = CType.CString
val intType: CType    = CType.CInt
val floatType: CType  = CType.CFloat
val boolType: CType   = CType.CBoolean
```

#### Collections

**CList:**
```scala
final case class CList(valuesType: CType) extends CType
```
- Homogeneous lists (all elements same type)
- Element type is explicit: `CList(CType.CInt)` means "list of integers"
- Empty list uses `CList(CType.CString)` with empty vector (by convention)

**CMap:**
```scala
final case class CMap(keysType: CType, valuesType: CType) extends CType
```
- Keys must all be the same type
- Values must all be the same type
- Keys and values can be different types

```scala
// Collection type examples
val listOfInts: CType = CType.CList(CType.CInt)
val listOfStrings: CType = CType.CList(CType.CString)
val stringToIntMap: CType = CType.CMap(CType.CString, CType.CInt)
```

#### Structured Types

**CProduct (Records):**
```scala
final case class CProduct(structure: Map[String, CType]) extends CType
```
- Named fields with typed values
- Structural typing: compatible by field names and types
- Used for records, case classes, structured data

```scala
// Record type example
val personType: CType = CType.CProduct(Map(
  "name" -> CType.CString,
  "age" -> CType.CInt,
  "email" -> CType.CString
))
```

**CUnion (Tagged Unions):**
```scala
final case class CUnion(structure: Map[String, CType]) extends CType
```
- Discriminated union with tag-to-type mapping
- Each tag identifies a variant with its type
- Used for sum types, result types, error handling

```scala
// Union type example
val resultType: CType = CType.CUnion(Map(
  "Success" -> CType.CProduct(Map("data" -> CType.CString)),
  "Error" -> CType.CProduct(Map("message" -> CType.CString))
))
```

**COptional:**
```scala
final case class COptional(innerType: CType) extends CType
```
- Represents values that may or may not exist
- Used for nullable fields, optional parameters, guard expressions
- Runtime values: `CSome(value, type)` or `CNone(type)`

```scala
// Optional type example
val maybeInt: CType = CType.COptional(CType.CInt)
val maybeString: CType = CType.COptional(CType.CString)
```

---

### CValue Hierarchy

`CValue` represents **actual runtime values** flowing through the DAG. Every `CValue` can report its `CType` via the `.ctype` method.

```
CValue (sealed trait)
├── CString(value: String)
├── CInt(value: Long)
├── CFloat(value: Double)
├── CBoolean(value: Boolean)
├── CList(value: Vector[CValue], subtype: CType)
├── CMap(value: Vector[(CValue, CValue)], keysType, valuesType)
├── CProduct(value: Map[String, CValue], structure: Map[String, CType])
├── CUnion(value: CValue, structure: Map[String, CType], tag: String)
├── CSome(value: CValue, innerType: CType)
└── CNone(innerType: CType)
```

### CValue Details

#### Primitive Values

```scala
// String value
val hello: CValue = CValue.CString("hello")
hello.ctype  // => CType.CString

// Integer value
val answer: CValue = CValue.CInt(42L)
answer.ctype  // => CType.CInt

// Float value
val pi: CValue = CValue.CFloat(3.14159)
pi.ctype  // => CType.CFloat

// Boolean value
val yes: CValue = CValue.CBoolean(true)
yes.ctype  // => CType.CBoolean
```

#### Collection Values

**CList:**
```scala
final case class CList(
  value: Vector[CValue],
  subtype: CType
) extends CValue {
  override def ctype: CType = CType.CList(subtype)
}
```

```scala
// List of integers
val numbers: CValue = CValue.CList(
  Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
  CType.CInt
)
numbers.ctype  // => CType.CList(CType.CInt)

// Empty list
val empty: CValue = CValue.CList(Vector.empty, CType.CString)
empty.ctype  // => CType.CList(CType.CString)
```

**CMap:**
```scala
final case class CMap(
  value: Vector[(CValue, CValue)],
  keysType: CType,
  valuesType: CType
) extends CValue {
  override def ctype: CType = CType.CMap(keysType, valuesType)
}
```

```scala
// Map from strings to integers
val counts: CValue = CValue.CMap(
  Vector(
    (CValue.CString("apples"), CValue.CInt(5)),
    (CValue.CString("oranges"), CValue.CInt(3))
  ),
  CType.CString,
  CType.CInt
)
counts.ctype  // => CType.CMap(CType.CString, CType.CInt)
```

#### Structured Values

**CProduct:**
```scala
final case class CProduct(
  value: Map[String, CValue],
  structure: Map[String, CType]
) extends CValue {
  override def ctype: CType = CType.CProduct(structure)
}
```

```scala
// Person record
val person: CValue = CValue.CProduct(
  Map(
    "name" -> CValue.CString("Alice"),
    "age" -> CValue.CInt(30),
    "email" -> CValue.CString("alice@example.com")
  ),
  Map(
    "name" -> CType.CString,
    "age" -> CType.CInt,
    "email" -> CType.CString
  )
)
person.ctype  // => CType.CProduct(...)
```

**CUnion:**
```scala
final case class CUnion(
  value: CValue,
  structure: Map[String, CType],
  tag: String
) extends CValue {
  override def ctype: CType = CType.CUnion(structure)
}
```

```scala
// Success variant
val success: CValue = CValue.CUnion(
  CValue.CProduct(
    Map("data" -> CValue.CString("result")),
    Map("data" -> CType.CString)
  ),
  Map(
    "Success" -> CType.CProduct(Map("data" -> CType.CString)),
    "Error" -> CType.CProduct(Map("message" -> CType.CString))
  ),
  "Success"
)
success.ctype  // => CType.CUnion(...)
```

**Optional Values:**
```scala
// CSome - present value
final case class CSome(value: CValue, innerType: CType) extends CValue {
  override def ctype: CType = CType.COptional(innerType)
}

// CNone - absent value
final case class CNone(innerType: CType) extends CValue {
  override def ctype: CType = CType.COptional(innerType)
}
```

```scala
// Present value
val some: CValue = CValue.CSome(CValue.CInt(42), CType.CInt)
some.ctype  // => CType.COptional(CType.CInt)

// Absent value
val none: CValue = CValue.CNone(CType.CInt)
none.ctype  // => CType.COptional(CType.CInt)
```

---

## Semantic Type System

`SemanticType` is used during compilation for type checking, inference, and error reporting. It has more expressive power than `CType`.

### SemanticType Hierarchy

```
SemanticType (sealed trait)
├── SString                           // String type
├── SInt                              // Integer type
├── SFloat                            // Float type
├── SBoolean                          // Boolean type
├── SNothing                          // Bottom type (subtype of all)
├── SRecord(fields: Map)              // Record with named fields
├── SList(element: SemanticType)      // List type
├── SMap(key, value)                  // Map type
├── SOptional(inner)                  // Optional type
├── SFunction(params, return)         // Function type (compile-time only)
├── SUnion(members: Set)              // Union type (A | B | C)
├── RowVar(id: Int)                   // Row variable for polymorphism
└── SOpenRecord(fields, rowVar)       // Open record type
```

### SemanticType vs CType Mapping

| SemanticType | CType | Notes |
|--------------|-------|-------|
| `SString` | `CType.CString` | Direct mapping |
| `SInt` | `CType.CInt` | Direct mapping |
| `SFloat` | `CType.CFloat` | Direct mapping |
| `SBoolean` | `CType.CBoolean` | Direct mapping |
| `SNothing` | `CType.CString` (by convention) | Bottom type, no runtime representation |
| `SRecord(fields)` | `CType.CProduct(fields)` | Records map to products |
| `SList(elem)` | `CType.CList(elem)` | Direct mapping |
| `SMap(k, v)` | `CType.CMap(k, v)` | Direct mapping |
| `SOptional(inner)` | `CType.COptional(inner)` | Direct mapping |
| `SFunction(...)` | **No mapping** | Functions exist only at compile-time |
| `SUnion(members)` | `CType.CUnion(tagMap)` | Union types with tags |
| `RowVar(id)` | **No mapping** | Must be resolved during type checking |
| `SOpenRecord(...)` | **No mapping** | Must be closed during type checking |

### Semantic Type Details

#### SNothing (Bottom Type)

```scala
case object SNothing extends SemanticType {
  def prettyPrint: String = "Nothing"
}
```

**Properties:**
- Subtype of **all types** (bottom of the type lattice)
- Used for empty lists: `[]` has type `List<Nothing>`
- Used for type error recovery
- Cannot be explicitly written in constellation-lang
- Has no runtime representation

**Why it's useful:**
```scala
// Empty list is compatible with any list type
val emptyList: SemanticType = SList(SNothing)
Subtyping.isSubtype(emptyList, SList(SInt))     // => true
Subtyping.isSubtype(emptyList, SList(SString))  // => true
```

#### SRecord (Closed Records)

```scala
final case class SRecord(fields: Map[String, SemanticType]) extends SemanticType {
  def prettyPrint: String = {
    val fieldStrs = fields.map { case (n, t) => s"$n: ${t.prettyPrint}" }.mkString(", ")
    s"{ $fieldStrs }"
  }
}
```

**Properties:**
- Fixed set of fields (closed world)
- Structural subtyping: width + depth
- Maps to `CType.CProduct` at runtime

```scala
val person: SemanticType = SRecord(Map(
  "name" -> SString,
  "age" -> SInt,
  "email" -> SString
))
// Pretty prints as: { name: String, age: Int, email: String }
```

#### SList

```scala
final case class SList(element: SemanticType) extends SemanticType {
  def prettyPrint: String = s"List<${element.prettyPrint}>"
}
```

**Properties:**
- Covariant: `List<A>` is a subtype of `List<B>` if `A` is a subtype of `B`
- Element-wise operations on records (merge, projection, field access)
- Legacy alias: "Candidates" resolves to `SList`

#### SOptional

```scala
final case class SOptional(inner: SemanticType) extends SemanticType {
  def prettyPrint: String = s"Optional<${inner.prettyPrint}>"
}
```

**Properties:**
- Represents nullable/missing values
- Covariant: `Optional<A> <: Optional<B>` if `A <: B`
- Used with guard expressions (`when`) and coalesce (`??`)

#### SFunction (Compile-time Only)

```scala
final case class SFunction(
  paramTypes: List[SemanticType],
  returnType: SemanticType
) extends SemanticType {
  def prettyPrint: String = {
    val params = paramTypes.map(_.prettyPrint).mkString(", ")
    s"($params) => ${returnType.prettyPrint}"
  }
}
```

**Properties:**
- Exists **only during type checking**
- Used for lambda expressions and higher-order functions
- **Cannot be converted to CType** (runtime error if attempted)
- Contravariant in parameters, covariant in return type

```scala
// Lambda type: (String, Int) => Boolean
val lambdaType = SFunction(
  List(SString, SInt),
  SBoolean
)
// Pretty prints as: (String, Int) => Boolean
```

#### SUnion (Union Types)

```scala
final case class SUnion(members: Set[SemanticType]) extends SemanticType {
  def prettyPrint: String = members.map(_.prettyPrint).toList.sorted.mkString(" | ")
}
```

**Properties:**
- Represents "one of" multiple types
- Automatically flattened: `(A | B) | C` becomes `A | B | C`
- Used for variant returns, error handling, discriminated unions

```scala
val result: SemanticType = SUnion(Set(
  SRecord(Map("value" -> SInt)),
  SRecord(Map("error" -> SString))
))
// Pretty prints as: { error: String } | { value: Int }
```

#### RowVar and SOpenRecord (Row Polymorphism)

```scala
final case class RowVar(id: Int) extends SemanticType {
  def prettyPrint: String = s"ρ$id"
}

final case class SOpenRecord(
  fields: Map[String, SemanticType],
  rowVar: RowVar
) extends SemanticType {
  def prettyPrint: String = {
    val fieldStr = fields.map { case (k, v) => s"$k: ${v.prettyPrint}" }.mkString(", ")
    if fieldStr.isEmpty then s"{ | ${rowVar.prettyPrint} }"
    else s"{ $fieldStr | ${rowVar.prettyPrint} }"
  }
}
```

**Properties:**
- Open records have "at least" the specified fields
- Row variable represents "rest" of the fields
- Used for row-polymorphic functions
- **Must be resolved before runtime** (cannot convert to CType)

```scala
// Function that accepts any record with at least "name" field
val openType = SOpenRecord(
  Map("name" -> SString),
  RowVar(1)
)
// Pretty prints as: { name: String | ρ1 }

// This matches:
// - { name: String }
// - { name: String, age: Int }
// - { name: String, age: Int, email: String }
```

---

## Type Compatibility and Subtyping

Constellation uses **structural subtyping** to determine type compatibility. The subtyping rules are defined in `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/Subtyping.scala`.

### Subtyping Rules

```scala
// Reflexivity: every type is a subtype of itself
S <: S

// Transitivity: if S <: T and T <: U, then S <: U (implicit)
S <: T ∧ T <: U ⟹ S <: U

// Bottom type: Nothing is a subtype of all types
SNothing <: T
```

### Primitive Type Subtyping

**No subtyping between primitives:**
- `Int` is not a subtype of `Float`
- `String` is not a subtype of `Int`
- Each primitive is only a subtype of itself

```scala
Subtyping.isSubtype(SInt, SInt)      // => true
Subtyping.isSubtype(SInt, SFloat)    // => false
Subtyping.isSubtype(SInt, SString)   // => false
```

### Collection Subtyping

#### Lists (Covariant)

```
SList(S) <: SList(T)  ⟸  S <: T
```

Lists are **covariant** in their element type: if `A` is a subtype of `B`, then `List<A>` is a subtype of `List<B>`.

```scala
// List<Nothing> <: List<Int>
Subtyping.isSubtype(
  SList(SNothing),
  SList(SInt)
)  // => true

// List<{name: String, age: Int}> <: List<{name: String}>  (width subtyping)
Subtyping.isSubtype(
  SList(SRecord(Map("name" -> SString, "age" -> SInt))),
  SList(SRecord(Map("name" -> SString)))
)  // => true
```

#### Maps (Invariant Keys, Covariant Values)

```
SMap(K, S) <: SMap(K, T)  ⟸  S <: T  (keys must be exactly equal)
```

Map keys are **invariant** (must match exactly), but values are **covariant**.

```scala
// Map<String, Nothing> <: Map<String, Int>  (values covariant)
Subtyping.isSubtype(
  SMap(SString, SNothing),
  SMap(SString, SInt)
)  // => true

// Map<Int, String> is NOT <: Map<String, String>  (keys invariant)
Subtyping.isSubtype(
  SMap(SInt, SString),
  SMap(SString, SString)
)  // => false
```

#### Optional (Covariant)

```
SOptional(S) <: SOptional(T)  ⟸  S <: T
```

Optional types are **covariant** in their inner type.

```scala
// Optional<Nothing> <: Optional<Int>
Subtyping.isSubtype(
  SOptional(SNothing),
  SOptional(SInt)
)  // => true
```

### Record Subtyping (Width + Depth)

```
SRecord(F₁) <: SRecord(F₂)  ⟸  ∀f∈F₂. f∈F₁ ∧ F₁(f) <: F₂(f)
```

**Width subtyping:** A record with **more fields** is a subtype of a record with fewer fields.

**Depth subtyping:** Field types must be subtypes.

```scala
// Wide record <: narrow record
val wide = SRecord(Map(
  "name" -> SString,
  "age" -> SInt,
  "email" -> SString
))
val narrow = SRecord(Map(
  "name" -> SString
))
Subtyping.isSubtype(wide, narrow)  // => true
Subtyping.isSubtype(narrow, wide)  // => false (missing fields)

// Depth subtyping example
val sub = SRecord(Map("value" -> SNothing))
val sup = SRecord(Map("value" -> SInt))
Subtyping.isSubtype(sub, sup)  // => true (SNothing <: SInt)
```

**Practical implications:**

```constellation
type PersonBase = { name: String }
type PersonWithAge = { name: String, age: Int }

in person: PersonWithAge

# This is valid: PersonWithAge <: PersonBase
result = GetName(person)  // GetName expects { name: String }
```

### Union Subtyping

```
S <: T₁ | T₂  ⟸  S <: T₁ ∨ S <: T₂    (Union as supertype)
T₁ | T₂ <: S  ⟸  T₁ <: S ∧ T₂ <: S    (Union as subtype)
```

**Union as supertype:** A type is a subtype of a union if it's a subtype of **any** member.

**Union as subtype:** A union is a subtype of a type if **all** members are subtypes.

```scala
val union = SUnion(Set(SInt, SString))

// Each member is subtype of union
Subtyping.isSubtype(SInt, union)     // => true
Subtyping.isSubtype(SString, union)  // => true

// Non-member is not subtype
Subtyping.isSubtype(SBoolean, union) // => false

// Union is subtype only if all members are subtypes
Subtyping.isSubtype(union, SInt)     // => false (String is not <: Int)

// But a single-member union is subtype of its member
Subtyping.isSubtype(SUnion(Set(SInt)), SInt)  // => true
```

### Function Subtyping (Contravariant/Covariant)

```
SFunction(P₁, R₁) <: SFunction(P₂, R₂)  ⟸
  |P₁| = |P₂| ∧ (∀i. P₂ᵢ <: P₁ᵢ) ∧ R₁ <: R₂
```

Functions are:
- **Contravariant** in parameter types (reversed!)
- **Covariant** in return type

```scala
// (String) => String  <:  (Nothing) => String  (contravariant params)
Subtyping.isSubtype(
  SFunction(List(SString), SString),
  SFunction(List(SNothing), SString)
)  // => true (more general parameter type)

// (String) => Nothing  <:  (String) => Int  (covariant return)
Subtyping.isSubtype(
  SFunction(List(SString), SNothing),
  SFunction(List(SString), SInt)
)  // => true (more specific return type)
```

### Row Polymorphism (Open Records)

Closed records can be subtypes of open records if they have all required fields:

```scala
val closedRecord = SRecord(Map(
  "name" -> SString,
  "age" -> SInt
))
val openRecord = SOpenRecord(
  Map("name" -> SString),
  RowVar(1)
)

// Closed record matches open record (has required "name" field)
Subtyping.isSubtype(closedRecord, openRecord)  // => true

// But open record is NOT subtype of closed (may have extra fields)
Subtyping.isSubtype(openRecord, closedRecord)  // => false
```

### Subtyping Lattice Visualization

```
                    ⊤ (no top type)
                    │
         ┌──────────┼──────────┐
         │          │          │
     String       Int       Float
         │          │          │
         └──────────┼──────────┘
                    │
                 Nothing (⊥)


For Records:
  { name: String, age: Int, email: String }
                    │
                    │  (width subtyping: drop fields)
                    ↓
        { name: String, age: Int }
                    │
                    ↓
            { name: String }
```

---

## Type Tags, Injectors, and Extractors

Constellation provides type classes for converting between Scala types and Constellation types. This enables seamless interop with Scala code.

### CTypeTag: Scala Type → CType

`CTypeTag[A]` maps a Scala type `A` to its corresponding `CType` at compile time.

**Given instances:**

```scala
given CTypeTag[String]      // => CType.CString
given CTypeTag[Long]        // => CType.CInt
given CTypeTag[Double]      // => CType.CFloat
given CTypeTag[Boolean]     // => CType.CBoolean
given CTypeTag[List[A]]     // => CType.CList(A's CType)
given CTypeTag[Vector[A]]   // => CType.CList(A's CType)
given CTypeTag[Map[A, B]]   // => CType.CMap(A's CType, B's CType)
given CTypeTag[Option[A]]   // => CType.COptional(A's CType)
```

**Case class derivation (via Scala 3 Mirrors):**

```scala
case class Person(name: String, age: Long, email: String)

// Automatically derived:
// CTypeTag[Person] => CType.CProduct(Map(
//   "name" -> CType.CString,
//   "age" -> CType.CInt,
//   "email" -> CType.CString
// ))
```

**Usage:**

```scala
import io.constellation.deriveType

// Primitives
deriveType[String]   // => CType.CString
deriveType[Long]     // => CType.CInt
deriveType[Double]   // => CType.CFloat
deriveType[Boolean]  // => CType.CBoolean

// Collections
deriveType[List[String]]        // => CType.CList(CType.CString)
deriveType[Map[String, Long]]   // => CType.CMap(CType.CString, CType.CInt)
deriveType[Option[Long]]        // => CType.COptional(CType.CInt)

// Case classes
case class Point(x: Long, y: Long)
deriveType[Point]  // => CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CInt))

// Nested structures
case class Team(leader: Person, size: Long)
deriveType[Team]  // => CType.CProduct(Map(
                  //      "leader" -> CType.CProduct(...),
                  //      "size" -> CType.CInt
                  //    ))
```

### CValueInjector: Scala Value → CValue

`CValueInjector[A]` converts Scala values to `CValue` representations.

**Given instances:**

```scala
given CValueInjector[String]
given CValueInjector[Long]
given CValueInjector[Double]
given CValueInjector[Boolean]
given CValueInjector[Vector[A]]
given CValueInjector[List[A]]
given CValueInjector[Map[A, B]]
given CValueInjector[Option[A]]
```

**Usage:**

```scala
// Primitives
CValueInjector[String].inject("hello")  // => CValue.CString("hello")
CValueInjector[Long].inject(42L)        // => CValue.CInt(42)
CValueInjector[Double].inject(3.14)     // => CValue.CFloat(3.14)
CValueInjector[Boolean].inject(true)    // => CValue.CBoolean(true)

// Collections
CValueInjector[List[Long]].inject(List(1, 2, 3))
// => CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)), CType.CInt)

CValueInjector[Map[String, Long]].inject(Map("a" -> 1, "b" -> 2))
// => CValue.CMap(Vector(...), CType.CString, CType.CInt)

CValueInjector[Option[Long]].inject(Some(42))
// => CValue.CSome(CValue.CInt(42), CType.CInt)

CValueInjector[Option[Long]].inject(None)
// => CValue.CNone(CType.CInt)
```

### CValueExtractor: CValue → Scala Value

`CValueExtractor[A]` converts `CValue` back to Scala types. Extraction is effectful (`IO`) because type mismatches raise errors.

**Given instances:**

```scala
given CValueExtractor[String]
given CValueExtractor[Long]
given CValueExtractor[Double]
given CValueExtractor[Boolean]
given CValueExtractor[Vector[A]]
given CValueExtractor[List[A]]
given CValueExtractor[Map[A, B]]
given CValueExtractor[Option[A]]
```

**Usage:**

```scala
import cats.effect.IO
import cats.effect.unsafe.implicits.global

// Primitives
CValueExtractor[String].extract(CValue.CString("hello")).unsafeRunSync()
// => "hello"

CValueExtractor[Long].extract(CValue.CInt(42)).unsafeRunSync()
// => 42L

// Collections
val listValue = CValue.CList(
  Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
  CType.CInt
)
CValueExtractor[List[Long]].extract(listValue).unsafeRunSync()
// => List(1L, 2L, 3L)

// Optional
CValueExtractor[Option[Long]].extract(CValue.CSome(CValue.CInt(42), CType.CInt)).unsafeRunSync()
// => Some(42L)

CValueExtractor[Option[Long]].extract(CValue.CNone(CType.CInt)).unsafeRunSync()
// => None

// Type mismatch raises error
CValueExtractor[String].extract(CValue.CInt(42)).attempt.unsafeRunSync()
// => Left(RuntimeException("Expected CValue.CString, but got CInt(42)"))
```

### Using Type Classes in Modules

Type classes enable seamless conversion in module definitions:

```scala
import io.constellation.*
import io.constellation.ModuleBuilder

case class Input(text: String, count: Long)
case class Output(result: String)

val myModule = ModuleBuilder
  .metadata("MyModule", "Example module", 1, 0)
  .implementationPure[Input, Output] { input =>
    // input: Input (Scala case class)
    // Automatically converted from CValue using extractors

    val repeated = input.text * input.count.toInt

    // Output is automatically converted to CValue using injectors
    Output(result = repeated)
  }
  .build

// Usage:
// - Input CValue.CProduct is extracted to Input case class
// - Output case class is injected to CValue.CProduct
// - Type safety guaranteed by CType checks
```

---

## Type Inference

Constellation uses **bidirectional type inference**, where types flow both bottom-up (from expressions) and top-down (from context).

### Inference Mode (Bottom-Up)

Types are **synthesized** from expression structure without context.

| Expression | Inferred Type |
|------------|---------------|
| `"hello"` | `String` |
| `42` | `Int` |
| `3.14` | `Float` |
| `true`, `false` | `Boolean` |
| `[1, 2, 3]` | `List<Int>` |
| `[]` | `List<Nothing>` (compatible with any `List<T>`) |
| `{ name: "Alice", age: 30 }` | `{ name: String, age: Int }` |
| `a + b` (records) | Merged record type |
| `record.field` | Type of the field |
| `record[field1, field2]` | Record with projected fields |
| `expr when cond` | `Optional<T>` where `T` is type of `expr` |
| `opt ?? fallback` | Type of `fallback` |

**Examples:**

```constellation
# Literal inference
x = 42                # Type: Int
name = "Alice"        # Type: String
ratio = 3.14          # Type: Float
active = true         # Type: Boolean

# Record literal inference
person = {
  name: "Alice",
  age: 30
}                     # Type: { name: String, age: Int }

# List literal inference
numbers = [1, 2, 3]   # Type: List<Int>
empty = []            # Type: List<Nothing>

# Record operations
in a: { x: Int }
in b: { y: String }
merged = a + b        # Type: { x: Int, y: String }

in record: { name: String, age: Int, email: String }
subset = record[name, age]  # Type: { name: String, age: Int }
justName = record.name      # Type: String

# Guard and coalesce
in data: String
result = Process(data) when shouldProcess
                      # Type: Optional<Result>
final = result ?? defaultValue
                      # Type: Result (unwrapped)
```

### Checking Mode (Top-Down)

Expected types **propagate** into expressions, enabling context-sensitive inference.

**Lambda parameter inference:**

```constellation
in items: List<{ id: String, active: Boolean }>

# Lambda parameter 'item' is inferred from list element type
filtered = Filter(items, item => item.active)
#                        ^^^^
#                        Type: { id: String, active: Boolean }
```

**Empty list typing:**

```constellation
in numbers: List<Int>

# Empty list inferred as List<Int> from expected type
result = if condition
  then numbers
  else []  # Type: List<Int> (from context)
```

**Function parameter checking:**

```scala
// Function signature: Uppercase(text: String) -> String
in value: String
result = Uppercase(value)  # Type: String (from signature)
```

### Conditional Branch Typing (LUB)

Conditional expressions compute the **least upper bound (LUB)** of all branches:

```constellation
# Both branches have compatible record types
result = if condition
  then { name: "Alice", age: 30 }
  else { name: "Bob", age: 25 }
# Type: { name: String, age: Int } (common type)

# Branches with different structures create a union
result = if condition
  then { success: true, data: "ok" }
  else { success: false, error: "failed" }
# Type: { success: Boolean, data: String } | { success: Boolean, error: String }
```

### Type Inference for Complex Expressions

```constellation
type Person = { name: String, age: Int, email: String }
in people: List<Person>

# Chained operations with inference
adults = Filter(people, p => p.age >= 18)
         # Filter inferred: (List<Person>, (Person) => Boolean) => List<Person>
         # Lambda param 'p' inferred as Person

names = adults.name
        # Field access: List<Person> -> List<String>

# Branch with list comprehension
results = branch
  when peopleCount > 10  => Process(adults)
  when peopleCount > 0   => ProcessSmall(adults)
  otherwise              => []
# Type: List<Result> (LUB of all branches)
```

---

## Working Examples

### Example 1: Building a Module with Records

```scala
import io.constellation.*
import io.constellation.ModuleBuilder
import cats.effect.IO

// Define input/output case classes
case class PersonInput(name: String, age: Long)
case class PersonOutput(greeting: String, isAdult: Boolean)

// Module that greets a person
val greetPerson = ModuleBuilder
  .metadata("GreetPerson", "Greets a person and checks if adult", 1, 0)
  .implementationPure[PersonInput, PersonOutput] { input =>
    PersonOutput(
      greeting = s"Hello, ${input.name}!",
      isAdult = input.age >= 18
    )
  }
  .build

// Usage in constellation-lang:
/*
type Person = { name: String, age: Int }
in person: Person

result = GreetPerson(person)
# result: { greeting: String, isAdult: Boolean }

out result.greeting
out result.isAdult
*/
```

### Example 2: Module with List Processing

```scala
case class FilterInput(items: List[String], pattern: String)
case class FilterOutput(filtered: List[String], count: Long)

val filterStrings = ModuleBuilder
  .metadata("FilterStrings", "Filters strings by pattern", 1, 0)
  .implementationPure[FilterInput, FilterOutput] { input =>
    val filtered = input.items.filter(_.contains(input.pattern))
    FilterOutput(filtered, filtered.size.toLong)
  }
  .build

// Usage:
/*
in items: List<String>
in pattern: String

result = FilterStrings({ items: items, pattern: pattern })
out result.filtered
out result.count
*/
```

### Example 3: Working with Optional Values

```scala
case class LookupInput(key: String)
case class LookupOutput(value: Option[String])

val lookup = ModuleBuilder
  .metadata("Lookup", "Looks up a value by key", 1, 0)
  .implementation[LookupInput, LookupOutput] { input => IO {
    val cache = Map("foo" -> "bar", "hello" -> "world")
    LookupOutput(value = cache.get(input.key))
  }}
  .build

// Usage:
/*
in key: String

result = Lookup({ key: key })
# result: { value: Optional<String> }

value = result.value ?? "default"
# value: String (unwrapped with default)

out value
*/
```

### Example 4: Runtime CValue Construction

```scala
import io.constellation.{CType, CValue}

// Create a person record manually
val personValue: CValue = CValue.CProduct(
  Map(
    "name" -> CValue.CString("Alice"),
    "age" -> CValue.CInt(30),
    "email" -> CValue.CString("alice@example.com")
  ),
  Map(
    "name" -> CType.CString,
    "age" -> CType.CInt,
    "email" -> CType.CString
  )
)

// Create a list of integers
val numbersValue: CValue = CValue.CList(
  Vector(
    CValue.CInt(1),
    CValue.CInt(2),
    CValue.CInt(3)
  ),
  CType.CInt
)

// Create a map
val countMap: CValue = CValue.CMap(
  Vector(
    (CValue.CString("apples"), CValue.CInt(5)),
    (CValue.CString("oranges"), CValue.CInt(3))
  ),
  CType.CString,
  CType.CInt
)

// Create an optional value
val maybeName: CValue = CValue.CSome(
  CValue.CString("Alice"),
  CType.CString
)

val noValue: CValue = CValue.CNone(CType.CString)
```

### Example 5: Type-Safe Extraction

```scala
import cats.effect.IO
import cats.effect.unsafe.implicits.global

// Extract from CValue to Scala types
val personValue: CValue = CValue.CProduct(
  Map(
    "name" -> CValue.CString("Bob"),
    "age" -> CValue.CInt(25)
  ),
  Map(
    "name" -> CType.CString,
    "age" -> CType.CInt
  )
)

// Manual extraction (for illustration)
val extractName: IO[String] = personValue match {
  case CValue.CProduct(fields, _) =>
    fields.get("name") match {
      case Some(CValue.CString(name)) => IO.pure(name)
      case _ => IO.raiseError(new RuntimeException("Invalid name field"))
    }
  case _ => IO.raiseError(new RuntimeException("Not a product"))
}

val name: String = extractName.unsafeRunSync()  // => "Bob"

// Using extractors (preferred)
case class Person(name: String, age: Long)
// Note: automatic extraction requires deriving Product encoder/decoder
// This is handled automatically by ModuleBuilder for module I/O
```

### Example 6: Union Types

```scala
// Define a union type for results
val resultUnionType: CType = CType.CUnion(Map(
  "Success" -> CType.CProduct(Map("data" -> CType.CString)),
  "Error" -> CType.CProduct(Map("message" -> CType.CString))
))

// Success variant
val success: CValue = CValue.CUnion(
  CValue.CProduct(
    Map("data" -> CValue.CString("operation completed")),
    Map("data" -> CType.CString)
  ),
  resultUnionType.asInstanceOf[CType.CUnion].structure,
  "Success"
)

// Error variant
val error: CValue = CValue.CUnion(
  CValue.CProduct(
    Map("message" -> CValue.CString("operation failed")),
    Map("message" -> CType.CString)
  ),
  resultUnionType.asInstanceOf[CType.CUnion].structure,
  "Error"
)
```

---

## Edge Cases and Common Mistakes

### 1. Empty Lists and Type Ambiguity

**Problem:** Empty lists have type `List<Nothing>` which can match any list type.

```constellation
# This is valid but may cause confusion
in numbers: List<Int>
in strings: List<String>

result = if condition
  then numbers
  else []  # Type: List<Nothing>, compatible with List<Int>

# Both branches are valid:
result2 = if condition
  then []  # List<Nothing>
  else strings  # List<String>
```

**Recommendation:** Use explicit type annotations when the context isn't clear.

### 2. Record Field Name Mismatches

**Problem:** Field names in case classes must match constellation-lang variable names **exactly**.

```scala
// WRONG - field name mismatch
case class BadInput(personName: String)  // Field: "personName"

/*
# In constellation-lang:
type Person = { name: String }  # Field: "name"
in person: Person
result = Process(person)  # ERROR: "name" != "personName"
*/

// CORRECT - field names match
case class GoodInput(name: String)  // Field: "name"
```

### 3. Module Name Case Sensitivity

**Problem:** Module names must match exactly (case-sensitive) between Scala and constellation-lang.

```scala
// Scala
ModuleBuilder.metadata("Uppercase", ...).build

// constellation-lang - CORRECT
result = Uppercase(text)

// WRONG - case mismatch
result = uppercase(text)  # ERROR: Function 'uppercase' not found
result = UPPERCASE(text)  # ERROR: Function 'UPPERCASE' not found
```

### 4. Optional vs Null

**Problem:** Constellation doesn't have `null`. Use `Optional<T>` instead.

```scala
// WRONG - returning null
.implementationPure[Input, Option[String]] { _ =>
  null  // Will crash at runtime
}

// CORRECT - use None
.implementationPure[Input, Option[String]] { _ =>
  None  // => CValue.CNone(CType.CString)
}
```

### 5. Record Subtyping Direction

**Problem:** Forgetting that wider records are subtypes of narrower records (not vice versa).

```constellation
type PersonBase = { name: String }
type PersonWithAge = { name: String, age: Int }

in detailed: PersonWithAge

# CORRECT: wider type is subtype of narrower type
result = ProcessBase(detailed)  # ProcessBase expects { name: String }

# WRONG: can't pass narrower type when wider is expected
in base: PersonBase
result = ProcessDetailed(base)  # ERROR: missing 'age' field
```

### 6. Union Type Ordering

**Problem:** Union members have no ordering in type representation, but pretty-printing sorts them.

```scala
val union1 = SUnion(Set(SInt, SString))
val union2 = SUnion(Set(SString, SInt))

union1 == union2  // => true (Set equality)
union1.prettyPrint  // => "Int | String" (sorted)
union2.prettyPrint  // => "Int | String" (sorted)
```

### 7. Function Types Cannot Be Runtime Values

**Problem:** `SFunction` exists only at compile time and cannot be converted to `CType`.

```scala
val funcType = SemanticType.SFunction(List(SString), SInt)

// WRONG - will throw exception
val ctype = SemanticType.toCType(funcType)
// => IllegalArgumentException: Function types cannot be converted to CType

// CORRECT - functions are only for type checking
// They don't exist at runtime in constellation-lang
```

### 8. Open Records Must Be Closed

**Problem:** `SOpenRecord` and `RowVar` must be resolved during type checking before runtime.

```scala
val openType = SOpenRecord(Map("name" -> SString), RowVar(1))

// WRONG - cannot convert to runtime type
val ctype = SemanticType.toCType(openType)
// => IllegalArgumentException: Open record types cannot be converted to CType

// CORRECT - row polymorphic functions must instantiate with closed types
// before execution
```

### 9. Map Key Invariance

**Problem:** Map keys are invariant, so maps with different key types are incompatible even if keys are subtypes.

```scala
// WRONG assumption
val mapIntToString = SMap(SInt, SString)
val mapNothingToString = SMap(SNothing, SString)

Subtyping.isSubtype(mapNothingToString, mapIntToString)  // => false
// Keys are invariant!

// CORRECT
val mapStringToInt = SMap(SString, SInt)
val mapStringToNothing = SMap(SString, SNothing)

Subtyping.isSubtype(mapStringToNothing, mapStringToInt)  // => true
// Values are covariant
```

### 10. Forgetting to Import cats.implicits._

**Problem:** Missing import causes `.traverse` to fail.

```scala
import cats.effect.IO
// WRONG - missing cats.implicits._
val modules = List(module1, module2, module3)
modules.traverse(constellation.setModule)  // ERROR: value traverse is not a member

// CORRECT
import cats.implicits._
modules.traverse(constellation.setModule)  // Works!
```

---

## Type-Driven Development Patterns

### Pattern 1: Start with Types, Then Implementation

Define types first to clarify your module's contract:

```scala
// 1. Define types
case class TextInput(content: String, maxLength: Long)
case class TextOutput(truncated: String, wasTruncated: Boolean)

// 2. Define module metadata
val truncateText = ModuleBuilder
  .metadata("TruncateText", "Truncates text to max length", 1, 0)
  // 3. Implementation follows naturally from types
  .implementationPure[TextInput, TextOutput] { input =>
    val truncated =
      if input.content.length > input.maxLength.toInt
      then input.content.take(input.maxLength.toInt)
      else input.content

    TextOutput(
      truncated = truncated,
      wasTruncated = input.content.length > input.maxLength.toInt
    )
  }
  .build
```

### Pattern 2: Use Structural Subtyping for Flexibility

Design functions to accept records with "at least" certain fields:

```scala
// Flexible: accepts any record with "id" field
case class WithId(id: String)

val getId = ModuleBuilder
  .metadata("GetId", "Extracts ID from any record with id field", 1, 0)
  .implementationPure[WithId, String](_.id)
  .build

// Works with:
// - { id: String }
// - { id: String, name: String }
// - { id: String, name: String, age: Int }
```

### Pattern 3: Optional for Fallible Operations

Use `Option[T]` for operations that might fail:

```scala
case class ParseInput(text: String)
case class ParseOutput(value: Option[Long])

val tryParseInt = ModuleBuilder
  .metadata("TryParseInt", "Attempts to parse string as integer", 1, 0)
  .implementationPure[ParseInput, ParseOutput] { input =>
    ParseOutput(value = input.text.toLongOption)
  }
  .build

// Usage:
/*
in text: String
result = TryParseInt({ text: text })
# result: { value: Optional<Int> }

parsed = result.value ?? 0
# parsed: Int (with default)
*/
```

### Pattern 4: Tagged Results for Error Handling

Use record variants to represent success/failure:

```scala
case class OperationInput(data: String)

// Success variant
case class Success(result: String)
// Error variant
case class Failure(error: String, code: Long)

// Constellation will see this as a union type
// { result: String } | { error: String, code: Int }
```

### Pattern 5: Compose Types with Records

Build complex types from simpler building blocks:

```scala
// Basic types
case class Address(street: String, city: String, country: String)
case class Contact(email: String, phone: String)
case class Metadata(createdAt: Long, updatedAt: Long)

// Composed type
case class User(
  name: String,
  address: Address,
  contact: Contact,
  metadata: Metadata
)

// Constellation type:
// {
//   name: String,
//   address: { street: String, city: String, country: String },
//   contact: { email: String, phone: String },
//   metadata: { createdAt: Int, updatedAt: Int }
// }
```

### Pattern 6: List Processing Patterns

Leverage element-wise operations for list processing:

```constellation
type Item = { id: String, price: Float, quantity: Int }
in items: List<Item>

# Add context to each item (element-wise merge)
in currency: { currency: String }
enriched = items + currency
# Type: List<{ id: String, price: Float, quantity: Int, currency: String }>

# Project fields from each item
summary = items[id, price]
# Type: List<{ id: String, price: Float }>

# Extract single field from each item
ids = items.id
# Type: List<String>
```

### Pattern 7: Guard for Conditional Execution

Use guards to conditionally execute expensive operations:

```scala
case class ExpensiveInput(data: String, config: Map[String, String])
case class ExpensiveOutput(result: String)

val expensiveOp = ModuleBuilder
  .metadata("ExpensiveOp", "Expensive operation", 1, 0)
  .implementation[ExpensiveInput, ExpensiveOutput] { input => IO {
    // Simulate expensive work
    Thread.sleep(1000)
    ExpensiveOutput(s"Processed: ${input.data}")
  }}
  .build

// Usage with guard:
/*
in shouldRun: Boolean
in data: String

result = ExpensiveOp({ data: data, config: {} }) when shouldRun
# result: Optional<{ result: String }>
# Only runs ExpensiveOp if shouldRun is true

final = result ?? { result: "skipped" }
out final.result
*/
```

### Pattern 8: Type-Safe Configuration

Use record types for strongly-typed configuration:

```scala
case class ServerConfig(
  host: String,
  port: Long,
  timeout: Long,
  retries: Long
)

case class AppInput(config: ServerConfig, data: String)
case class AppOutput(status: String)

val runApp = ModuleBuilder
  .metadata("RunApp", "Runs app with config", 1, 0)
  .implementation[AppInput, AppOutput] { input => IO {
    // Type-safe access to config
    val url = s"http://${input.config.host}:${input.config.port}"
    // ... use config ...
    AppOutput(status = "success")
  }}
  .build
```

### Pattern 9: Batch Operations with Lists

Process batches of items with type safety:

```scala
case class BatchInput(items: List[String], batchSize: Long)
case class BatchOutput(batches: List[List[String]])

val batchItems = ModuleBuilder
  .metadata("BatchItems", "Groups items into batches", 1, 0)
  .implementationPure[BatchInput, BatchOutput] { input =>
    val grouped = input.items.grouped(input.batchSize.toInt).toList
    BatchOutput(batches = grouped)
  }
  .build

// Type: BatchInput -> BatchOutput
// Where: BatchOutput = { batches: List<List<String>> }
```

### Pattern 10: Validation with Boolean Returns

Use boolean fields in output for validation results:

```scala
case class ValidationInput(email: String)
case class ValidationOutput(isValid: Boolean, reason: String)

val validateEmail = ModuleBuilder
  .metadata("ValidateEmail", "Validates email format", 1, 0)
  .implementationPure[ValidationInput, ValidationOutput] { input =>
    val isValid = input.email.contains("@") && input.email.contains(".")
    ValidationOutput(
      isValid = isValid,
      reason = if (isValid) "valid" else "missing @ or ."
    )
  }
  .build

// Usage:
/*
in email: String
validation = ValidateEmail({ email: email })

result = branch
  when validation.isValid => Process(email)
  otherwise => { error: validation.reason }
*/
```

---

## Summary

The Constellation type system is built on a solid foundation of:

1. **Runtime types** (`CType`/`CValue`) for execution
2. **Semantic types** (`SemanticType`) for type checking
3. **Structural subtyping** with width and depth rules
4. **Type classes** for Scala interop (`CTypeTag`, `CValueInjector`, `CValueExtractor`)
5. **Bidirectional type inference** for ergonomic programming
6. **Advanced features** like unions, optionals, and row polymorphism

**Key takeaways:**

- **CType and CValue** are runtime representations used during DAG execution
- **SemanticType** is compile-time only, used for type checking and inference
- **Subtyping** is structural: wider records are subtypes of narrower records
- **Collections** are covariant in element types (except Map keys, which are invariant)
- **Type classes** enable seamless conversion between Scala and Constellation types
- **Type inference** works bidirectionally: bottom-up (synthesis) and top-down (checking)
- **Type-driven development** starts with types to clarify contracts before implementation

For more information:

- **Language types**: See `website/docs/language/types.md`
- **Type system implementation**: See `modules/core/src/main/scala/io/constellation/TypeSystem.scala`
- **Semantic types**: See `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/SemanticType.scala`
- **Subtyping rules**: See `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/Subtyping.scala`
- **Module building**: See `modules/runtime/src/main/scala/io/constellation/ModuleBuilder.scala`
