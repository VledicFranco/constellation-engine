---
title: "Type Syntax Reference"
sidebar_position: 1
description: "Complete reference for all CType variants, CValue representations, and constellation-lang type syntax. Quick lookup guide for type system implementation."
---

# Type Syntax Reference

This is a comprehensive quick reference for the Constellation type system. Use this as a lookup guide when implementing modules, debugging type errors, or working with the compiler.

## Quick Type Lookup Table

| Type Category | Constellation Syntax | CType | Scala Type | Example Value |
|---------------|---------------------|-------|------------|---------------|
| **Primitives** | | | | |
| String | `String` | `CType.CString` | `String` | `"hello"` |
| Integer | `Int` | `CType.CInt` | `Long` | `42` |
| Float | `Float` | `CType.CFloat` | `Double` | `3.14` |
| Boolean | `Boolean` | `CType.CBoolean` | `Boolean` | `true` |
| **Collections** | | | | |
| List | `List<T>` | `CType.CList(T)` | `List[T]` | `[1, 2, 3]` |
| Map | `Map<K, V>` | `CType.CMap(K, V)` | `Map[K, V]` | `{"a": 1}` |
| **Structured** | | | | |
| Record | `{ field: Type }` | `CType.CProduct(Map)` | Case class | `{ name: "Alice" }` |
| Union | `A \| B` | `CType.CUnion(Map)` | - | Tagged variant |
| Optional | `Optional<T>` | `CType.COptional(T)` | `Option[T]` | `Some(42)` or `None` |
| **Special** | | | | |
| Nothing | (implicit) | `CType.CString` | - | `[]` (empty list) |

---

## Primitive Types

### String

**Constellation Syntax:**
```constellation
in text: String
in name: String
```

**Runtime Type:**
```scala
CType.CString
```

**Runtime Value:**
```scala
CValue.CString(value: String)
```

**Scala Mapping:**
- Scala type: `String`
- Type tag: `given CTypeTag[String] => CType.CString`
- Injector: `CValueInjector[String].inject("hello") => CValue.CString("hello")`
- Extractor: `CValueExtractor[String].extract(CValue.CString("hello")) => IO("hello")`

**Examples:**
```constellation
# Input declaration
in message: String

# String literal
greeting = "Hello, World!"

# Module with String parameter
uppercase = Uppercase(message)
```

**Common Operations:**
- Concatenation (via modules like `Concat`)
- Transformation (via modules like `Uppercase`, `Lowercase`, `Trim`)
- Pattern matching (via modules like `Match`, `Replace`)

---

### Int

**Constellation Syntax:**
```constellation
in count: Int
in age: Int
```

**Runtime Type:**
```scala
CType.CInt
```

**Runtime Value:**
```scala
CValue.CInt(value: Long)  // 64-bit signed integer
```

**Scala Mapping:**
- Scala type: `Long`
- Type tag: `given CTypeTag[Long] => CType.CInt`
- Injector: `CValueInjector[Long].inject(42L) => CValue.CInt(42)`
- Extractor: `CValueExtractor[Long].extract(CValue.CInt(42)) => IO(42L)`

**Examples:**
```constellation
# Input declaration
in count: Int

# Integer literal
answer = 42

# Module with Int parameter
doubled = Double(count)
```

**Important Notes:**
- **No implicit conversion** between `Int` and `Float`
- **No arithmetic operators** in constellation-lang (use modules)
- **Literals are inferred** as `Int` unless decimal point is present

---

### Float

**Constellation Syntax:**
```constellation
in ratio: Float
in temperature: Float
```

**Runtime Type:**
```scala
CType.CFloat
```

**Runtime Value:**
```scala
CValue.CFloat(value: Double)  // 64-bit double precision
```

**Scala Mapping:**
- Scala type: `Double`
- Type tag: `given CTypeTag[Double] => CType.CFloat`
- Injector: `CValueInjector[Double].inject(3.14) => CValue.CFloat(3.14)`
- Extractor: `CValueExtractor[Double].extract(CValue.CFloat(3.14)) => IO(3.14)`

**Examples:**
```constellation
# Input declaration
in ratio: Float

# Float literal (must have decimal point)
pi = 3.14159

# Module with Float parameter
rounded = Round(pi)
```

**Important Notes:**
- **Decimal point required** for float literals: `3.14` (not `3`)
- **No implicit conversion** from `Int` to `Float`
- **Use modules for math operations** (no built-in operators)

---

### Boolean

**Constellation Syntax:**
```constellation
in enabled: Boolean
in active: Boolean
```

**Runtime Type:**
```scala
CType.CBoolean
```

**Runtime Value:**
```scala
CValue.CBoolean(value: Boolean)
```

**Scala Mapping:**
- Scala type: `Boolean`
- Type tag: `given CTypeTag[Boolean] => CType.CBoolean`
- Injector: `CValueInjector[Boolean].inject(true) => CValue.CBoolean(true)`
- Extractor: `CValueExtractor[Boolean].extract(CValue.CBoolean(true)) => IO(true)`

**Examples:**
```constellation
# Input declaration
in enabled: Boolean

# Boolean literals
isActive = true
isDisabled = false

# Used in conditional expressions
result = if enabled
  then Process(data)
  else Skip()

# Used with guards
guarded = ExpensiveOp(data) when enabled
```

**Important Notes:**
- **Two literals only**: `true` and `false`
- **Used in conditionals** (`if`/`else`, `branch`, `when`)
- **No boolean operators** in constellation-lang (use modules like `And`, `Or`, `Not`)

---

## Collection Types

### List<T>

**Constellation Syntax:**
```constellation
in items: List<String>
in numbers: List<Int>
in people: List<{ name: String, age: Int }>
```

**Runtime Type:**
```scala
CType.CList(valuesType: CType)
```

**Runtime Value:**
```scala
CValue.CList(
  value: Vector[CValue],
  subtype: CType
)
```

**Scala Mapping:**
- Scala type: `List[A]` or `Vector[A]`
- Type tag: `given CTypeTag[List[A]] => CType.CList(A's CType)`
- Injector: `CValueInjector[List[A]].inject(List(1, 2, 3))`
- Extractor: `CValueExtractor[List[A]].extract(listValue) => IO(List(...))`

**Examples:**
```constellation
# Input declaration
in tags: List<String>

# List literal
numbers = [1, 2, 3, 4, 5]

# Empty list (type inferred from context)
empty = []

# List of records
type Person = { name: String, age: Int }
in people: List<Person>
```

**Element-wise Operations on List<Record>:**

When a list contains record elements, special operations apply to **each element**:

```constellation
type Item = { id: String, price: Float }
in items: List<Item>
in context: { currency: String }

# Merge adds context to EACH item
enriched = items + context
# Type: List<{ id: String, price: Float, currency: String }>

# Projection selects fields from EACH item
selected = items[id]
# Type: List<{ id: String }>

# Field access extracts field from EACH item
ids = items.id
# Type: List<String>
```

**Type Properties:**
- **Homogeneous**: All elements must have the same type
- **Covariant**: If `A <: B`, then `List<A> <: List<B>`
- **Empty list**: `[]` has type `List<Nothing>` (compatible with any `List<T>`)

**Common Patterns:**
```constellation
# Filter list
filtered = Filter(items, x => x.active)

# Map over list
transformed = Map(items, x => x.name)

# Extract field from list of records
names = people.name  # Type: List<String>

# Flatten nested lists
flat = Flatten(nestedList)
```

---

### Map<K, V>

**Constellation Syntax:**
```constellation
in cache: Map<String, Int>
in lookup: Map<Int, String>
```

**Runtime Type:**
```scala
CType.CMap(
  keysType: CType,
  valuesType: CType
)
```

**Runtime Value:**
```scala
CValue.CMap(
  value: Vector[(CValue, CValue)],
  keysType: CType,
  valuesType: CType
)
```

**Scala Mapping:**
- Scala type: `Map[K, V]`
- Type tag: `given CTypeTag[Map[K, V]] => CType.CMap(K's CType, V's CType)`
- Injector: `CValueInjector[Map[K, V]].inject(Map("a" -> 1))`
- Extractor: `CValueExtractor[Map[K, V]].extract(mapValue) => IO(Map(...))`

**Examples:**
```constellation
# Input declaration
in metadata: Map<String, Int>

# Map construction (via modules)
cache = BuildMap(keys, values)

# Map lookup
value = MapGet(cache, "key")
```

**Type Properties:**
- **Keys are invariant**: `Map<A, V>` and `Map<B, V>` are unrelated even if `A <: B`
- **Values are covariant**: If `A <: B`, then `Map<K, A> <: Map<K, B>`
- **Homogeneous**: All keys same type, all values same type

**Important Notes:**
- **No map literals** in constellation-lang (construct via modules)
- **Key and value types explicit** in runtime representation
- **Use modules** for operations like `MapGet`, `MapPut`, `MapKeys`, `MapValues`

---

### Candidates<T> (Legacy Alias)

**Constellation Syntax:**
```constellation
in items: Candidates<{ id: String }>
```

**Modern Equivalent:**
```constellation
in items: List<{ id: String }>
```

**Important Notes:**
- **Legacy alias**: `Candidates<T>` is fully equivalent to `List<T>`
- **Use `List<T>` in new code** for clarity
- **Backwards compatible**: Existing code using `Candidates` will continue to work
- **Same runtime type**: Both map to `CType.CList`

---

## Structured Types

### Record Types (Product Types)

**Constellation Syntax:**
```constellation
# Inline record type
in person: { name: String, age: Int }

# Type alias
type Person = {
  name: String,
  age: Int,
  email: String
}
in person: Person
```

**Runtime Type:**
```scala
CType.CProduct(structure: Map[String, CType])
```

**Runtime Value:**
```scala
CValue.CProduct(
  value: Map[String, CValue],
  structure: Map[String, CType]
)
```

**Scala Mapping:**
- Scala type: Case class (via Scala 3 Mirrors)
- Type tag: Automatically derived for case classes
- Injector: Automatically derived for case classes
- Extractor: Automatically derived for case classes

**Examples:**
```constellation
# Type definition
type Person = {
  name: String,
  age: Int,
  email: String
}

# Input with record type
in person: Person

# Record literal
alice = {
  name: "Alice",
  age: 30,
  email: "alice@example.com"
}

# Field access
name = person.name        # Type: String
age = person.age          # Type: Int

# Projection (select subset of fields)
basic = person[name, age] # Type: { name: String, age: Int }
```

**Nested Records:**
```constellation
type Address = {
  street: String,
  city: String,
  country: String
}

type Person = {
  name: String,
  address: Address
}

in person: Person

# Nested field access
city = person.address.city  # Type: String
```

**Scala Case Class Mapping:**
```scala
// Define case class
case class Person(name: String, age: Long, email: String)

// CTypeTag automatically derived
// CType.CProduct(Map(
//   "name" -> CType.CString,
//   "age" -> CType.CInt,
//   "email" -> CType.CString
// ))

// Use in module
val module = ModuleBuilder
  .metadata("ProcessPerson", "Processes a person", 1, 0)
  .implementationPure[Person, String] { person =>
    s"${person.name} is ${person.age} years old"
  }
  .build
```

**Subtyping (Width + Depth):**

Records use **structural subtyping**:

```constellation
type PersonBase = { name: String }
type PersonWithAge = { name: String, age: Int }
type PersonFull = { name: String, age: Int, email: String }

# Wider records are subtypes of narrower records
# PersonFull <: PersonWithAge <: PersonBase

in detailed: PersonFull

# Valid: pass wider type where narrower expected
result = ProcessBase(detailed)  # ProcessBase expects { name: String }
```

**Width subtyping**: A record with **more fields** is a subtype of one with fewer fields.

**Depth subtyping**: Field types must also be subtypes.

---

### Union Types

**Constellation Syntax:**
```constellation
# Simple union
type Result = String | Int

# Record variant union
type APIResponse = {
  status: Int,
  data: String
} | {
  status: Int,
  error: String
}

# Multi-way union
type Value = String | Int | Float | Boolean
```

**Semantic Type:**
```scala
SemanticType.SUnion(members: Set[SemanticType])
```

**Runtime Type:**
```scala
CType.CUnion(structure: Map[String, CType])
```

**Runtime Value:**
```scala
CValue.CUnion(
  value: CValue,
  structure: Map[String, CType],
  tag: String  // Discriminator
)
```

**Examples:**
```constellation
# Define union type
type Result = {
  success: Boolean,
  data: String
} | {
  success: Boolean,
  error: String
}

# Input with union type
in response: Result

# Branch on union members (pattern matching)
output = branch
  when HasField(response, "data") => response.data
  when HasField(response, "error") => response.error
  otherwise => "unknown"
```

**Union Flattening:**

Nested unions are automatically flattened:

```constellation
type A = String | Int
type B = Float | Boolean
type C = A | B

# C is equivalent to: String | Int | Float | Boolean
```

**Subtyping:**

```constellation
type Success = { data: String }
type Failure = { error: String }
type Response = Success | Failure

# Each member is a subtype of the union
# Success <: Response
# Failure <: Response

# Union is subtype if ALL members are subtypes
type BaseRecord = { id: String }
type RecordA = { id: String, name: String }
type RecordB = { id: String, count: Int }
type UnionAB = RecordA | RecordB

# UnionAB <: BaseRecord (both members have 'id' field)
```

**Important Notes:**
- **Union members unordered**: Order doesn't matter, `A | B` equals `B | A`
- **Automatically flattened**: `(A | B) | C` becomes `A | B | C`
- **Used for variant returns**: Success/failure, different result types
- **Runtime requires tags**: Each variant has a discriminator tag

---

### Optional<T>

**Constellation Syntax:**
```constellation
in maybeValue: Optional<Int>
in optionalName: Optional<String>
```

**Semantic Type:**
```scala
SemanticType.SOptional(inner: SemanticType)
```

**Runtime Type:**
```scala
CType.COptional(innerType: CType)
```

**Runtime Value:**
```scala
// Present value
CValue.CSome(value: CValue, innerType: CType)

// Absent value
CValue.CNone(innerType: CType)
```

**Scala Mapping:**
- Scala type: `Option[T]`
- Type tag: `given CTypeTag[Option[T]] => CType.COptional(T's CType)`
- Injector: `Some(42) => CValue.CSome(...)`, `None => CValue.CNone(...)`
- Extractor: `CValue.CSome(...) => Some(...)`, `CValue.CNone(...) => None`

**Examples:**
```constellation
# Input declaration
in maybeCount: Optional<Int>

# Coalesce operator (??) provides fallback
count = maybeCount ?? 0
# If maybeCount is Some(42), count = 42
# If maybeCount is None, count = 0

# Guard expression produces Optional
in shouldProcess: Boolean
in data: String
result = Process(data) when shouldProcess
# Type: Optional<Result>
# Only runs Process if shouldProcess is true

# Chain optional operations
final = result ?? defaultValue
```

**Working with Optional in Scala Modules:**
```scala
case class LookupInput(key: String)
case class LookupOutput(value: Option[String])

val lookup = ModuleBuilder
  .metadata("Lookup", "Looks up a value", 1, 0)
  .implementation[LookupInput, LookupOutput] { input => IO {
    val cache = Map("foo" -> "bar")
    LookupOutput(value = cache.get(input.key))
  }}
  .build

// Usage in constellation-lang:
/*
in key: String
result = Lookup({ key: key })
# result: { value: Optional<String> }

value = result.value ?? "not found"
# value: String (unwrapped with default)
*/
```

**Type Properties:**
- **Covariant**: If `A <: B`, then `Optional<A> <: Optional<B>`
- **Two runtime variants**: `CSome` (present) and `CNone` (absent)
- **Used with guards**: `expr when condition` produces `Optional<T>`
- **Unwrapped with `??`**: `opt ?? fallback` extracts value or uses fallback

---

## Type Syntax in Declarations

### Input Declarations

```constellation
# Primitive inputs
in text: String
in count: Int
in ratio: Float
in enabled: Boolean

# Collection inputs
in tags: List<String>
in cache: Map<String, Int>

# Record inputs
in person: { name: String, age: Int }

# Union inputs
in result: { success: Boolean, data: String } | { success: Boolean, error: String }

# Optional inputs
in maybeValue: Optional<Int>

# Type alias
type Person = { name: String, age: Int }
in person: Person
```

### Output Declarations

```constellation
# Simple output
out result

# Multiple outputs
out name
out age
out email

# Output with projection
out person.name
out person.age

# Output with optional coalesce
out maybeValue ?? 0
```

### Type Aliases

```constellation
# Simple alias
type UserId = String

# Record alias
type Person = {
  name: String,
  age: Int,
  email: String
}

# Union alias
type Result = {
  success: Boolean,
  data: String
} | {
  success: Boolean,
  error: String
}

# Nested type alias
type Address = { street: String, city: String }
type Person = {
  name: String,
  address: Address
}

# Using aliases
in user: Person
result = ProcessPerson(user)
```

### Function Parameters

```constellation
# Function with primitive parameters
result = Uppercase(text: String) -> String

# Function with record parameters
result = GreetPerson(person: { name: String, age: Int }) -> { greeting: String }

# Function with multiple parameters
result = Concat(a: String, b: String) -> String

# Function with optional parameters
result = Lookup(key: String) -> { value: Optional<String> }
```

---

## Type Literals and Constructors

### Literal Expressions

```constellation
# String literal
text = "hello"            # Type: String

# Integer literal
count = 42                # Type: Int

# Float literal (requires decimal point)
ratio = 3.14              # Type: Float

# Boolean literals
yes = true                # Type: Boolean
no = false                # Type: Boolean

# List literal
numbers = [1, 2, 3]       # Type: List<Int>
names = ["Alice", "Bob"]  # Type: List<String>
empty = []                # Type: List<Nothing>

# Record literal
person = {
  name: "Alice",
  age: 30,
  email: "alice@example.com"
}
# Type: { name: String, age: Int, email: String }

# Nested record literal
user = {
  name: "Alice",
  address: {
    street: "123 Main St",
    city: "Springfield"
  }
}
# Type: { name: String, address: { street: String, city: String } }
```

### Type Inference from Literals

```constellation
# Type inferred from literal
x = 42                    # Inferred: Int
name = "Alice"            # Inferred: String
ratio = 3.14              # Inferred: Float

# Type inferred from list elements
numbers = [1, 2, 3]       # Inferred: List<Int>
mixed = [1, 2.5]          # ERROR: inconsistent types

# Empty list needs context
in numbers: List<Int>
result = if condition
  then numbers
  else []                 # Inferred as List<Int> from context
```

---

## Type Compatibility Quick Reference

### Assignment Compatibility Matrix

Can I assign type X to type Y?

| From \ To | String | Int | Float | Boolean | List<T> | Map<K,V> | Record | Optional<T> | Union |
|-----------|--------|-----|-------|---------|---------|----------|--------|-------------|-------|
| **String** | Yes | No | No | No | No | No | No | No | If member |
| **Int** | No | Yes | No | No | No | No | No | No | If member |
| **Float** | No | No | Yes | No | No | No | No | No | If member |
| **Boolean** | No | No | No | Yes | No | No | No | No | If member |
| **List<A>** | No | No | No | No | If A <: T | No | No | No | If member |
| **Map<K,A>** | No | No | No | No | No | If K=K, A <: V | No | No | If member |
| **Record** | No | No | No | No | No | No | Width+Depth | No | If member |
| **Optional<A>** | No | No | No | No | No | No | No | If A <: T | If member |
| **Union** | No | No | No | No | No | No | No | No | All members |
| **Nothing** | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes |

**Legend:**
- **Yes**: Always compatible
- **No**: Never compatible
- **If A <: T**: Compatible if element/inner type is a subtype
- **Width+Depth**: Compatible via structural subtyping (width and depth rules)
- **If member**: Compatible if type is a member of the union
- **All members**: Compatible if all union members are subtypes of target

### Subtyping Rules Summary

```
Primitives:
  - No subtyping between different primitives
  - String, Int, Float, Boolean only subtypes of themselves

Collections:
  - List<A> <: List<B>  if  A <: B  (covariant)
  - Map<K, A> <: Map<K, B>  if  A <: B  (values covariant, keys invariant)
  - Optional<A> <: Optional<B>  if  A <: B  (covariant)

Records:
  - Width: { a: A, b: B } <: { a: A }  (more fields is subtype)
  - Depth: Field types must be subtypes

Unions:
  - A <: (A | B)  (member is subtype of union)
  - (A | B) <: C  if  A <: C and B <: C  (union is subtype if all members are)

Nothing:
  - Nothing <: T  for all T  (bottom type)
```

---

## Type Conversion: SemanticType ↔ CType

### SemanticType to CType (Compile-time → Runtime)

```scala
SemanticType.toCType(semanticType: SemanticType): CType
```

**Mapping Table:**

| SemanticType | CType | Notes |
|--------------|-------|-------|
| `SString` | `CType.CString` | Direct mapping |
| `SInt` | `CType.CInt` | Direct mapping |
| `SFloat` | `CType.CFloat` | Direct mapping |
| `SBoolean` | `CType.CBoolean` | Direct mapping |
| `SNothing` | `CType.CString` | Bottom type, no runtime repr (String by convention) |
| `SRecord(fields)` | `CType.CProduct(fields)` | Maps to product type |
| `SList(elem)` | `CType.CList(elem)` | Direct mapping |
| `SMap(k, v)` | `CType.CMap(k, v)` | Direct mapping |
| `SOptional(inner)` | `CType.COptional(inner)` | Direct mapping |
| `SFunction(...)` | **ERROR** | Functions don't exist at runtime |
| `SUnion(members)` | `CType.CUnion(tagMap)` | Union with tags |
| `RowVar(id)` | **ERROR** | Must be resolved during type checking |
| `SOpenRecord(...)` | **ERROR** | Must be closed during type checking |

**Example:**
```scala
import io.constellation.lang.semantic.SemanticType.*

// Convert semantic type to runtime type
val semanticType = SRecord(Map(
  "name" -> SString,
  "age" -> SInt
))

val runtimeType = SemanticType.toCType(semanticType)
// => CType.CProduct(Map(
//      "name" -> CType.CString,
//      "age" -> CType.CInt
//    ))
```

**Types that CANNOT be converted:**
- `SFunction` - Functions are compile-time only
- `RowVar` - Row variables must be resolved first
- `SOpenRecord` - Open records must be closed first

### CType to SemanticType (Runtime → Compile-time)

```scala
SemanticType.fromCType(cType: CType): SemanticType
```

**Mapping Table:**

| CType | SemanticType | Notes |
|-------|--------------|-------|
| `CType.CString` | `SString` | Direct mapping |
| `CType.CInt` | `SInt` | Direct mapping |
| `CType.CFloat` | `SFloat` | Direct mapping |
| `CType.CBoolean` | `SBoolean` | Direct mapping |
| `CType.CList(elem)` | `SList(elem)` | Direct mapping |
| `CType.CMap(k, v)` | `SMap(k, v)` | Direct mapping |
| `CType.CProduct(fields)` | `SRecord(fields)` | Maps to record type |
| `CType.CUnion(fields)` | `SUnion(members)` | Union type |
| `CType.COptional(inner)` | `SOptional(inner)` | Direct mapping |

**Example:**
```scala
import io.constellation.{CType, lang.semantic.SemanticType}

// Convert runtime type to semantic type
val runtimeType = CType.CProduct(Map(
  "name" -> CType.CString,
  "age" -> CType.CInt
))

val semanticType = SemanticType.fromCType(runtimeType)
// => SRecord(Map(
//      "name" -> SString,
//      "age" -> SInt
//    ))
```

---

## Scala Interop: Type Classes

### CTypeTag - Compile-time Type Derivation

```scala
trait CTypeTag[A] {
  def cType: CType
}
```

**Given Instances:**

```scala
// Primitives
given CTypeTag[String]   => CType.CString
given CTypeTag[Long]     => CType.CInt
given CTypeTag[Double]   => CType.CFloat
given CTypeTag[Boolean]  => CType.CBoolean

// Collections
given CTypeTag[List[A]]    => CType.CList(A's CType)
given CTypeTag[Vector[A]]  => CType.CList(A's CType)
given CTypeTag[Map[K, V]]  => CType.CMap(K's CType, V's CType)
given CTypeTag[Option[A]]  => CType.COptional(A's CType)

// Case classes (automatically derived via Scala 3 Mirrors)
case class Person(name: String, age: Long)
// CTypeTag[Person] => CType.CProduct(Map(
//   "name" -> CType.CString,
//   "age" -> CType.CInt
// ))
```

**Usage:**
```scala
import io.constellation.deriveType

// Derive CType from Scala type
val stringType = deriveType[String]        // => CType.CString
val listType = deriveType[List[String]]    // => CType.CList(CType.CString)

case class Point(x: Long, y: Long)
val pointType = deriveType[Point]          // => CType.CProduct(...)
```

### CValueInjector - Scala Value → CValue

```scala
trait CValueInjector[A] {
  def inject(value: A): CValue
}
```

**Given Instances:**

```scala
// Primitives
CValueInjector[String].inject("hello")  => CValue.CString("hello")
CValueInjector[Long].inject(42L)        => CValue.CInt(42)
CValueInjector[Double].inject(3.14)     => CValue.CFloat(3.14)
CValueInjector[Boolean].inject(true)    => CValue.CBoolean(true)

// Collections
CValueInjector[List[Long]].inject(List(1, 2, 3))
  => CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)), CType.CInt)

CValueInjector[Map[String, Long]].inject(Map("a" -> 1))
  => CValue.CMap(Vector((CValue.CString("a"), CValue.CInt(1))), CType.CString, CType.CInt)

// Optional
CValueInjector[Option[Long]].inject(Some(42))
  => CValue.CSome(CValue.CInt(42), CType.CInt)

CValueInjector[Option[Long]].inject(None)
  => CValue.CNone(CType.CInt)
```

**Example Usage in Modules:**
```scala
case class Input(text: String, count: Long)
case class Output(result: String)

val module = ModuleBuilder
  .metadata("MyModule", "Example", 1, 0)
  .implementationPure[Input, Output] { input =>
    // Input automatically extracted from CValue
    // Output automatically injected to CValue
    Output(result = input.text * input.count.toInt)
  }
  .build
```

### CValueExtractor - CValue → Scala Value

```scala
trait CValueExtractor[A] {
  def extract(data: CValue): IO[A]
}
```

**Given Instances:**

```scala
// Primitives
CValueExtractor[String].extract(CValue.CString("hello"))
  => IO("hello")

CValueExtractor[Long].extract(CValue.CInt(42))
  => IO(42L)

// Collections
CValueExtractor[List[Long]].extract(
  CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2)), CType.CInt)
) => IO(List(1L, 2L))

// Optional
CValueExtractor[Option[Long]].extract(CValue.CSome(CValue.CInt(42), CType.CInt))
  => IO(Some(42L))

CValueExtractor[Option[Long]].extract(CValue.CNone(CType.CInt))
  => IO(None)

// Type mismatch raises error
CValueExtractor[String].extract(CValue.CInt(42))
  => IO.raiseError(RuntimeException("Expected CValue.CString, but got CInt(42)"))
```

---

## Common Type Patterns

### Pattern 1: Simple Module with Primitives

```scala
// Scala
case class UppercaseInput(text: String)
case class UppercaseOutput(result: String)

val uppercase = ModuleBuilder
  .metadata("Uppercase", "Converts text to uppercase", 1, 0)
  .implementationPure[UppercaseInput, UppercaseOutput] { input =>
    UppercaseOutput(result = input.text.toUpperCase)
  }
  .build
```

```constellation
# constellation-lang
in text: String
result = Uppercase({ text: text })
out result.result
```

### Pattern 2: List Processing

```scala
// Scala
case class FilterInput(items: List[String], pattern: String)
case class FilterOutput(filtered: List[String], count: Long)

val filterStrings = ModuleBuilder
  .metadata("FilterStrings", "Filters strings by pattern", 1, 0)
  .implementationPure[FilterInput, FilterOutput] { input =>
    val filtered = input.items.filter(_.contains(input.pattern))
    FilterOutput(filtered, filtered.size.toLong)
  }
  .build
```

```constellation
# constellation-lang
in items: List<String>
in pattern: String

result = FilterStrings({ items: items, pattern: pattern })
out result.filtered
out result.count
```

### Pattern 3: Optional Values

```scala
// Scala
case class LookupInput(key: String)
case class LookupOutput(value: Option[String])

val lookup = ModuleBuilder
  .metadata("Lookup", "Looks up a value", 1, 0)
  .implementation[LookupInput, LookupOutput] { input => IO {
    val cache = Map("foo" -> "bar")
    LookupOutput(value = cache.get(input.key))
  }}
  .build
```

```constellation
# constellation-lang
in key: String
result = Lookup({ key: key })
value = result.value ?? "not found"
out value
```

### Pattern 4: Nested Records

```scala
// Scala
case class Address(street: String, city: String, country: String)
case class Person(name: String, age: Long, address: Address)
case class Output(city: String)

val extractCity = ModuleBuilder
  .metadata("ExtractCity", "Extracts city from person", 1, 0)
  .implementationPure[Person, Output] { person =>
    Output(city = person.address.city)
  }
  .build
```

```constellation
# constellation-lang
type Address = {
  street: String,
  city: String,
  country: String
}

type Person = {
  name: String,
  age: Int,
  address: Address
}

in person: Person
result = ExtractCity(person)
out result.city
```

### Pattern 5: Record with Structural Subtyping

```scala
// Scala - accepts any record with "name" field
case class WithName(name: String)
case class Output(greeting: String)

val greet = ModuleBuilder
  .metadata("Greet", "Greets by name", 1, 0)
  .implementationPure[WithName, Output] { input =>
    Output(greeting = s"Hello, ${input.name}!")
  }
  .build
```

```constellation
# constellation-lang
type PersonBase = { name: String }
type PersonFull = { name: String, age: Int, email: String }

in person: PersonFull

# Works because PersonFull <: PersonBase
result = Greet(person)  # Greet expects { name: String }
out result.greeting
```

### Pattern 6: List<Record> Element-wise Operations

```constellation
type Item = { id: String, price: Float }
in items: List<Item>

# Add currency to each item
in currency: { currency: String }
enriched = items + currency
# Type: List<{ id: String, price: Float, currency: String }>

# Project id from each item
ids = items[id]
# Type: List<{ id: String }>

# Extract price from each item
prices = items.price
# Type: List<Float>
```

### Pattern 7: Union for Result Types

```scala
// Scala - using separate case classes for union variants
case class Success(data: String)
case class Failure(error: String, code: Long)

// Module returns union via Either or ADT
// (union handling at constellation level, not Scala module level)
```

```constellation
# constellation-lang
type Result = {
  success: Boolean,
  data: String
} | {
  success: Boolean,
  error: String
}

in response: Result

# Pattern match on union
output = branch
  when HasField(response, "data") => response.data
  when HasField(response, "error") => response.error
  otherwise => "unknown"
```

### Pattern 8: Guard for Conditional Execution

```constellation
in shouldProcess: Boolean
in data: String

# Only run expensive operation if condition is true
result = ExpensiveOp(data) when shouldProcess
# Type: Optional<Result>

# Unwrap with fallback
final = result ?? { result: "skipped" }
out final.result
```

---

## Type Error Quick Reference

### Common Type Errors and Solutions

#### 1. Type Mismatch

**Error:**
```
Type mismatch: expected Int, got String
```

**Cause:** Passing wrong type to module parameter.

**Solution:**
```constellation
# Wrong
in count: String
result = Double(count)  # Double expects Int

# Correct
in count: Int
result = Double(count)
```

#### 2. Undefined Variable

**Error:**
```
Undefined variable: foo
```

**Cause:** Using variable before declaration.

**Solution:**
```constellation
# Wrong
result = Process(foo)

# Correct
in foo: String
result = Process(foo)
```

#### 3. Undefined Type

**Error:**
```
Undefined type: Person
```

**Cause:** Using type before definition.

**Solution:**
```constellation
# Wrong
in person: Person

# Correct
type Person = { name: String, age: Int }
in person: Person
```

#### 4. Invalid Field Access

**Error:**
```
Invalid field access: field 'email' not found. Available: name, age
```

**Cause:** Accessing non-existent field.

**Solution:**
```constellation
# Wrong
type Person = { name: String, age: Int }
in person: Person
email = person.email

# Correct (add field to type)
type Person = { name: String, age: Int, email: String }
in person: Person
email = person.email
```

#### 5. Invalid Projection

**Error:**
```
Invalid projection: field 'email' not found. Available: name, age
```

**Cause:** Projecting non-existent fields.

**Solution:**
```constellation
# Wrong
in person: { name: String, age: Int }
subset = person[name, email]

# Correct
subset = person[name, age]
```

#### 6. Incompatible Merge

**Error:**
```
Cannot merge types: String + Int
```

**Cause:** Using `+` with non-record types.

**Solution:**
```constellation
# Wrong
in a: String
in b: Int
result = a + b

# Correct (merge is for records)
in a: { name: String }
in b: { age: Int }
result = a + b  # Type: { name: String, age: Int }
```

#### 7. Projection on Non-Record

**Error:**
```
Projection requires a record type, got String
```

**Cause:** Using projection syntax on non-record.

**Solution:**
```constellation
# Wrong
in text: String
result = text[name]

# Correct
in person: { name: String, age: Int }
result = person[name]
```

#### 8. Field Access on Non-Record

**Error:**
```
Field access requires a record type, got List<String>
```

**Cause:** Using dot notation on wrong type.

**Solution:**
```constellation
# Wrong
in tags: List<String>
first = tags.name

# Correct (field access works on List<Record>)
in items: List<{ name: String }>
names = items.name  # Type: List<String>
```

#### 9. Coalesce on Non-Optional

**Error:**
```
Left side of ?? must be Optional, got Int
```

**Cause:** Using `??` with non-optional value.

**Solution:**
```constellation
# Wrong
in value: Int
result = value ?? 0

# Correct
in value: Optional<Int>
result = value ?? 0
```

#### 10. Module Name Case Mismatch

**Error:**
```
Function 'uppercase' not found. Did you mean 'Uppercase'?
```

**Cause:** Case-sensitive module name mismatch.

**Solution:**
```scala
// Scala
ModuleBuilder.metadata("Uppercase", ...).build
```

```constellation
# Wrong
result = uppercase(text)

# Correct (exact case match)
result = Uppercase(text)
```

---

## Advanced Type Features

### Nothing Type (Bottom Type)

**Properties:**
- Subtype of **all types**
- Used for empty lists: `[]` has type `List<Nothing>`
- Cannot be explicitly written
- Has no runtime representation (uses `CType.CString` by convention)

**Examples:**
```constellation
# Empty list compatible with any list type
in numbers: List<Int>

result = if condition
  then numbers
  else []  # Type: List<Nothing>, compatible with List<Int>

# Empty list can be assigned to any list variable
in strings: List<String>
empty = []  # Type: List<Nothing>, compatible with List<String>
```

### Function Types (Compile-time Only)

**Semantic Type:**
```scala
SemanticType.SFunction(
  paramTypes: List[SemanticType],
  returnType: SemanticType
)
```

**Properties:**
- Exists **only during type checking**
- Used for lambda expressions
- **Cannot be converted to CType** (runtime error if attempted)
- Contravariant in parameters, covariant in return

**Example:**
```constellation
# Lambda in Filter function
in items: List<{ id: String, active: Boolean }>

# Lambda parameter type inferred from context
filtered = Filter(items, item => item.active)
#                        ^^^^
#                        Type: (item: { id: String, active: Boolean }) => Boolean
```

### Row Polymorphism (Open Records)

**Semantic Types:**
```scala
// Row variable
SemanticType.RowVar(id: Int)

// Open record
SemanticType.SOpenRecord(
  fields: Map[String, SemanticType],
  rowVar: RowVar
)
```

**Properties:**
- Open records have "at least" specified fields
- Row variable represents "rest" of fields
- Used for row-polymorphic functions
- **Must be resolved before runtime** (cannot convert to CType)

**Example:**
```scala
// Function accepting any record with "name" field
val openType = SOpenRecord(
  Map("name" -> SString),
  RowVar(1)
)
// Pretty prints as: { name: String | ρ1 }

// Matches:
// - { name: String }
// - { name: String, age: Int }
// - { name: String, age: Int, email: String }
```

---

## Summary

### Key Takeaways

1. **Two Type Systems:**
   - **CType/CValue**: Runtime types and values (execution)
   - **SemanticType**: Compile-time types (type checking)

2. **Primitive Types:**
   - `String`, `Int`, `Float`, `Boolean`
   - No implicit conversions between primitives
   - No subtyping between different primitives

3. **Collection Types:**
   - `List<T>`: Homogeneous, covariant
   - `Map<K, V>`: Keys invariant, values covariant
   - Element-wise operations on `List<Record>`

4. **Structured Types:**
   - **Records**: Structural subtyping (width + depth)
   - **Unions**: Represent multiple possible types
   - **Optional**: Nullable values, covariant

5. **Type Inference:**
   - Bidirectional: bottom-up (synthesis) and top-down (checking)
   - Empty lists type as `List<Nothing>`
   - Lambda parameters inferred from context

6. **Subtyping:**
   - Records: wider <: narrower (more fields is subtype)
   - Collections: covariant in element types (except Map keys)
   - Unions: member is subtype of union
   - Nothing: bottom type, subtype of all

7. **Scala Interop:**
   - `CTypeTag`: Compile-time type derivation
   - `CValueInjector`: Scala value → CValue
   - `CValueExtractor`: CValue → Scala value
   - Automatic derivation for case classes

8. **Special Types:**
   - **Nothing**: Bottom type, subtype of all
   - **Function types**: Compile-time only, no runtime representation
   - **Open records**: Row polymorphism, must be closed before runtime

---

## Related Documentation

- **Language types**: `website/docs/language/types.md`
- **Type system foundations**: `website/docs/llm/foundations/type-system.md`
- **Type algebra**: `website/docs/language/type-algebra.md`
- **Module development**: `website/docs/llm/patterns/module-development.md`

---

## Quick Reference Cheat Sheet

```
PRIMITIVES:
  String, Int, Float, Boolean
  No implicit conversions, no subtyping

COLLECTIONS:
  List<T>     - covariant
  Map<K, V>   - keys invariant, values covariant

RECORDS:
  { field: Type }
  Structural subtyping: wider <: narrower

UNIONS:
  A | B | C
  Member is subtype of union

OPTIONAL:
  Optional<T>
  Covariant, use with ?? operator

TYPE INFERENCE:
  Bottom-up: literals → types
  Top-down: context → expected types
  Empty list: List<Nothing>

SCALA INTEROP:
  deriveType[T]              → CType
  CValueInjector[T].inject   → CValue
  CValueExtractor[T].extract → IO[T]

SUBTYPING:
  Nothing <: T               (all types)
  { a: A, b: B } <: { a: A } (width)
  List<A> <: List<B>         (if A <: B)
  A <: (A | B)               (union membership)
```
