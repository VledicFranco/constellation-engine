---
title: "Types"
sidebar_position: 3
description: "Master Constellation's structural type system: primitives, records, lists, unions, optionals, and subtyping rules."
---

# Types

Constellation uses a structural type system with support for primitives, records, collections, unions, and optional values. This page covers all type constructs and how they interact.

## Primitive Types

| Type | Description | Runtime Representation |
|------|-------------|------------------------|
| `String` | Text values | `CType.CString` |
| `Int` | Integer numbers (64-bit signed) | `CType.CInt` |
| `Float` | Floating-point numbers (64-bit double) | `CType.CFloat` |
| `Boolean` | True/false values | `CType.CBoolean` |

```
in text: String
in count: Int
in ratio: Float
in enabled: Boolean
```

## Record Types

Record types define structured data with named fields:

```
type Person = {
  name: String,
  age: Int,
  email: String
}
```

Records can be nested:

```
type Order = {
  id: String,
  customer: { name: String, address: String },
  total: Float
}
```

### Record Subtyping (Width and Depth)

Constellation uses **structural subtyping** for records. A record type `A` is a subtype of record type `B` if:

1. **Width subtyping**: `A` has all the fields required by `B` (and may have extra fields)
2. **Depth subtyping**: For each field in `B`, the corresponding field in `A` is a subtype of `B`'s field type

```
# Width subtyping: more fields is a subtype
type PersonWithAge = { name: String, age: Int }
type PersonBase = { name: String }

# PersonWithAge is a subtype of PersonBase because it has all required fields

# Depth subtyping: field types must also be subtypes
type Employee = {
  info: { name: String, department: String },
  salary: Float
}
type Worker = {
  info: { name: String },  # Less specific info type
  salary: Float
}

# Employee is a subtype of Worker (info field has extra field)
```

## Parameterized Types

### List<T>

Lists are ordered collections that support element-wise operations when containing records:

```
in tags: List<String>
in scores: List<Float>
```

**Element-wise operations on `List<Record>`:**

When a list contains record elements, merge, projection, and field access operations apply to each element automatically:

```
type Item = { id: String, price: Float }
in items: List<Item>
in context: { currency: String }

# Merge adds context to EACH item
enriched = items + context  # Type: List<{ id: String, price: Float, currency: String }>

# Projection selects fields from EACH item
selected = items[id]  # Type: List<{ id: String }>

# Field access extracts field from EACH item
ids = items.id  # Type: List<String>
```

**List covariance:**

Lists are **covariant** in their element type. If `A` is a subtype of `B`, then `List<A>` is a subtype of `List<B>`:

```
type DetailedPerson = { name: String, age: Int, email: String }
type BasicPerson = { name: String }

# List<DetailedPerson> is a subtype of List<BasicPerson>
```

### Map<K, V>

Maps associate keys with values:

```
in metadata: Map<String, Int>
in lookup: Map<Int, String>
```

**Map variance:**
- Keys are **invariant**: `Map<A, V>` and `Map<B, V>` are unrelated even if `A` is a subtype of `B`
- Values are **covariant**: If `A` is a subtype of `B`, then `Map<K, A>` is a subtype of `Map<K, B>`

### Candidates<T> (Legacy Alias)

`Candidates<T>` is a legacy alias for `List<T>`. It is fully supported for backwards compatibility but new code should use `List<T>` directly:

```
# These are equivalent:
in items: Candidates<{ id: String }>
in items: List<{ id: String }>
```

## Union Types

Union types represent values that can be one of several types. Use the `|` operator to create union types:

```
type Result = { value: Int } | { error: String }

in response: Result
```

Union types are useful for:

- **Variant returns**: Functions that can return different result types
- **Error handling**: Representing success/failure cases
- **Discriminated unions**: Type-safe handling of multiple cases

```
# A service call that can return different output types
type ServiceResponse = {
  data: String,
  status: Int
} | {
  error: String,
  code: Int
}

in apiResult: ServiceResponse
```

Union types can combine primitive types:

```
type StringOrInt = String | Int
in flexible: StringOrInt
```

### Union Subtyping

A type `T` is a subtype of a union `A | B | C` if `T` is a subtype of **any** member of the union:

```
type Success = { data: String }
type Failure = { error: String }
type Response = Success | Failure

# Success is a subtype of Response
# Failure is a subtype of Response
```

A union `A | B` is a subtype of type `T` if **all** members of the union are subtypes of `T`:

```
type BasicRecord = { id: String }
type RecordA = { id: String, name: String }
type RecordB = { id: String, count: Int }
type UnionAB = RecordA | RecordB

# UnionAB is a subtype of BasicRecord (both members have 'id' field)
```

### Union Flattening

Nested unions are automatically flattened:

```
type A = String | Int
type B = Float | Boolean
type C = A | B  # Equivalent to: String | Int | Float | Boolean
```

## Optional Type

`Optional<T>` represents values that may or may not be present. Use with guard expressions and coalesce operators:

```
in maybeValue: Optional<Int>

# Coalesce to provide fallback
result = maybeValue ?? 0

# Guard expressions produce Optional types
guarded = expensiveOp(data) when condition  # Type: Optional<Result>
```

Optional interacts with the orchestration algebra:
- Guard expressions (`when`) produce `Optional<T>` results
- Coalesce (`??`) unwraps optionals with fallbacks
- Branch expressions can handle optional cases

**Optional covariance:**

Optional types are covariant. If `A` is a subtype of `B`, then `Optional<A>` is a subtype of `Optional<B>`.

## Type References

Previously defined types can be referenced by name:

```
type Base = { id: String }
type Extended = Base + { name: String }  # Type algebra

in data: Extended  # References the Extended type
```

## Type Inference Rules

Constellation uses **bidirectional type inference**, where types flow both bottom-up (from expressions) and top-down (from context). This enables powerful inference without requiring explicit type annotations everywhere.

### Inference Mode (Bottom-Up)

Types are synthesized from expression structure:

| Expression | Inferred Type |
|------------|---------------|
| `"hello"` | `String` |
| `42` | `Int` |
| `3.14` | `Float` |
| `true`, `false` | `Boolean` |
| `[1, 2, 3]` | `List<Int>` |
| `[]` (empty list) | `List<Nothing>` (compatible with any `List<T>`) |
| `a + b` (records) | Merged record type |
| `record.field` | Type of the field |
| `record[field1, field2]` | Record with only projected fields |
| `expr when cond` | `Optional<T>` where `T` is the type of `expr` |
| `opt ?? fallback` | Type of `fallback` (unwrapped from Optional) |

### Checking Mode (Top-Down)

Expected types propagate into expressions, enabling:

**Lambda parameter inference:**
```
# Filter expects (T) => Boolean, so 'item' is inferred as the element type
filtered = Filter(items, item => item.active)
```

**Empty list typing:**
```
# Expected type provides element type
in numbers: List<Int>
empty = []  # Inferred as List<Int> from context
```

### Conditional Branch Typing

Conditional expressions (`if`/`else` and `branch`) compute the **least upper bound (LUB)** of all branches:

```
# Both branches return compatible record types
result = if condition
  then { name: "Alice", age: 30 }
  else { name: "Bob", age: 25 }
# Type: { name: String, age: Int }

# Branches with different record structures create a union
result = if condition
  then { success: true, data: "ok" }
  else { success: false, error: "failed" }
# Type: { success: Boolean, data: String } | { success: Boolean, error: String }
```

### Function Return Type Inference

Function return types are determined by the function signature in the registry. The type checker validates that arguments match expected parameter types and propagates the declared return type.

## Type Compatibility Matrix

This matrix shows which types can be assigned to which (rows can be assigned to columns):

| From \ To | String | Int | Float | Boolean | List<T> | Map<K,V> | Record | Optional<T> | Union |
|-----------|--------|-----|-------|---------|---------|----------|--------|-------------|-------|
| **String** | Yes | - | - | - | - | - | - | - | If member |
| **Int** | - | Yes | - | - | - | - | - | - | If member |
| **Float** | - | - | Yes | - | - | - | - | - | If member |
| **Boolean** | - | - | - | Yes | - | - | - | - | If member |
| **List<A>** | - | - | - | - | If A <: T | - | - | - | If member |
| **Map<K,A>** | - | - | - | - | - | If K=K, A <: V | - | - | If member |
| **Record** | - | - | - | - | - | - | Width+Depth | - | If member |
| **Optional<A>** | - | - | - | - | - | - | - | If A <: T | If member |
| **Union** | - | - | - | - | - | - | - | - | All members |
| **Nothing** | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes | Yes |

**Key:**
- "Yes" = Always compatible
- "-" = Never compatible
- "If A <: T" = Compatible if element type is a subtype
- "Width+Depth" = Compatible via structural subtyping
- "If member" = Compatible if the type is a member of the union
- "All members" = Compatible if all union members are subtypes of target

## Complex Nested Types Examples

### List of Records with Nested Types

```
type Address = {
  street: String,
  city: String,
  country: String
}

type Person = {
  name: String,
  age: Int,
  address: Address,
  tags: List<String>
}

in people: List<Person>

# Extract all cities
cities = people.address.city  # Type: List<String>

# Project subset of fields from each person
summary = people[name, age]  # Type: List<{ name: String, age: Int }>
```

### Deeply Nested Record Types

```
type Organization = {
  name: String,
  departments: List<{
    name: String,
    manager: { name: String, email: String },
    employees: List<{
      name: String,
      role: String,
      skills: List<String>
    }>
  }>
}

in org: Organization

# Access nested list
depts = org.departments  # Type: List<{name: String, manager: {...}, employees: [...]}>

# Deep field access through lists
managerNames = org.departments.manager.name  # Type: List<String>
```

### Optional with Complex Inner Types

```
type SearchResult = {
  query: String,
  results: List<{
    id: String,
    score: Float,
    metadata: { source: String, timestamp: Int }
  }>
}

in cachedResult: Optional<SearchResult>

# Coalesce with default
result = cachedResult ?? {
  query: "",
  results: []
}

# Guard expression creates Optional
expensiveResult = Search(query) when shouldSearch
# Type: Optional<SearchResult>
```

### Union with Record Variants

```
type APIResponse = {
  status: Int,
  data: {
    items: List<{ id: String, name: String }>,
    total: Int,
    page: Int
  }
} | {
  status: Int,
  error: {
    code: String,
    message: String,
    details: List<String>
  }
}

in response: APIResponse
```

### Map with Complex Value Types

```
type UserSession = {
  userId: String,
  loginTime: Int,
  permissions: List<String>,
  metadata: { device: String, location: String }
}

in sessions: Map<String, UserSession>
```

## Common Type Errors and How to Fix Them

### TypeMismatch

**Error:** `Type mismatch: expected Int, got String`

**Cause:** Assigning or passing a value of the wrong type.

```
# Wrong
in count: Int
result = Uppercase(count)  # Uppercase expects String, not Int

# Fix: Use the correct type
in text: String
result = Uppercase(text)
```

### UndefinedVariable

**Error:** `Undefined variable: foo`

**Cause:** Referencing a variable that hasn't been declared.

```
# Wrong
result = Process(foo)  # 'foo' was never defined

# Fix: Declare the variable first
in foo: String
result = Process(foo)
```

### UndefinedType

**Error:** `Undefined type: Person`

**Cause:** Using a type name that hasn't been defined.

```
# Wrong
in data: Person  # 'Person' type doesn't exist

# Fix: Define the type first
type Person = { name: String, age: Int }
in data: Person
```

### InvalidFieldAccess

**Error:** `Invalid field access: field 'email' not found. Available: name, age`

**Cause:** Accessing a field that doesn't exist on the record.

```
# Wrong
type Person = { name: String, age: Int }
in person: Person
email = person.email  # 'email' doesn't exist

# Fix: Use an existing field or update the type
type Person = { name: String, age: Int, email: String }
in person: Person
email = person.email  # Now valid
```

### InvalidProjection

**Error:** `Invalid projection: field 'email' not found. Available: name, age`

**Cause:** Projecting a field that doesn't exist.

```
# Wrong
in person: { name: String, age: Int }
subset = person[name, email]  # 'email' doesn't exist

# Fix: Only project existing fields
subset = person[name, age]
```

### IncompatibleMerge

**Error:** `Cannot merge types: String + Int`

**Cause:** Using `+` with non-record types.

```
# Wrong
in a: String
in b: Int
result = a + b  # Can't merge primitives

# Fix: Merge is for records
in a: { name: String }
in b: { age: Int }
result = a + b  # Type: { name: String, age: Int }
```

### TypeError for Projections on Non-Records

**Error:** `Projection requires a record type, got String`

**Cause:** Using projection syntax on a non-record type.

```
# Wrong
in text: String
result = text[name]  # Can't project from String

# Fix: Only project from records
in person: { name: String, age: Int }
result = person[name]
```

### TypeError for Field Access on Non-Records

**Error:** `Field access requires a record type, got List<String>`

**Cause:** Using dot notation on a non-record type (that isn't a `List<Record>`).

```
# Wrong
in tags: List<String>
first = tags.name  # List<String> has no 'name' field

# Fix: Field access works on List<Record>
in items: List<{ name: String }>
names = items.name  # Type: List<String>
```

### Lambda Parameter Type Inference Failure

**Error:** `Lambda parameter 'x' requires a type annotation`

**Cause:** Using a lambda outside of a context where parameter types can be inferred.

```
# Wrong (standalone lambda without context)
fn = x => x + 1

# Fix: Use in a context that provides type information
result = Map(numbers, x => x + 1)  # x inferred from List element type

# Or add explicit annotation
fn = (x: Int) => x + 1
```

### Coalesce Type Mismatch

**Error:** `Left side of ?? must be Optional, got Int`

**Cause:** Using `??` with a non-optional left operand.

```
# Wrong
in value: Int
result = value ?? 0  # 'value' is not Optional

# Fix: Use Optional type
in value: Optional<Int>
result = value ?? 0  # Correct
```

### Fallback Type Mismatch

**Error:** `Fallback type mismatch: module returns { data: String } but fallback is String`

**Cause:** The fallback value type doesn't match the module's return type.

```
# Wrong
result = FetchData(url) with { fallback: "error" }  # String doesn't match return type

# Fix: Match the return type
result = FetchData(url) with { fallback: { data: "default" } }
```

### Guard Condition Type Error

**Error:** `Type mismatch: expected Boolean, got String`

**Cause:** Using a non-boolean expression as a guard condition.

```
# Wrong
result = Compute(data) when status  # 'status' must be Boolean

# Fix: Use a boolean condition
result = Compute(data) when (status == "active")
```

## Advanced: The Nothing Type

Constellation has a **bottom type** called `Nothing` that is a subtype of all other types. This is used internally for:

- **Empty lists**: `[]` has type `List<Nothing>`, making it compatible with any `List<T>`
- **Type error recovery**: When type checking fails in one branch, other branches can still be checked

You cannot explicitly write `Nothing` in Constellation code, but understanding it helps explain why empty lists work with any expected list type.
