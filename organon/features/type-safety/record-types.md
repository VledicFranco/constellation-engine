# Record Types

> **Path**: `organon/features/type-safety/record-types.md`
> **Parent**: [type-safety/](./README.md)

Structured data with named fields, validated at compile time.

## Quick Example

```constellation
# Define a record type
type User = {
  id: String,
  name: String,
  age: Int,
  email: String
}

# Use in declarations
in user: User

# Access fields (compile-time validated)
userName = user.name         # String
userAge = user.age           # Int
invalid = user.address       # Compile error: field 'address' not found
```

## Syntax

### Type Definitions

```constellation
# Named type alias
type User = { id: String, name: String, age: Int }

# Nested records
type Address = { street: String, city: String, country: String }
type Customer = {
  id: String,
  name: String,
  address: Address
}
```

### Anonymous Records

Records can be used inline without a type alias:

```constellation
in data: { x: Int, y: Int, label: String }
result = Process(data)
```

### Record Literals

```constellation
# Fallback values use record literals
result = GetUser(id) with fallback: { id: "", name: "Unknown", age: 0 }
```

## Field Access

### Basic Access

```constellation
in user: { id: String, name: String, email: String }

userId = user.id      # String
userName = user.name  # String
```

### Nested Access

Chain dots for nested records:

```constellation
in customer: {
  id: String,
  address: {
    street: String,
    city: String,
    country: String
  }
}

city = customer.address.city        # String
country = customer.address.country  # String
```

### Element-Wise Access

When applied to a list, `.field` extracts that field from each element:

```constellation
in users: List<{ id: String, name: String, age: Int }>

ids = users.id      # List<String>
names = users.name  # List<String>
ages = users.age    # List<Int>
```

## Compile-Time Validation

The compiler catches invalid field access:

```constellation
in user: { id: String, name: String }

# Compile error: field 'email' not found in type { id: String, name: String }
email = user.email

# Compile error: field 'city' not found in type { id: String, name: String }
city = user.address.city
```

Error messages include:
- The field name that was requested
- The type that was accessed
- Available fields (for autocomplete suggestions)

## Width Subtyping

A record with more fields is a subtype of a record with fewer fields:

```constellation
type Full = { id: String, name: String, email: String, role: String }
type Basic = { id: String, name: String }

in user: Full

# Valid: Full has all fields Basic requires
result = ProcessBasic(user)  # Accepts Basic, receives Full
```

This means:
- You can pass richer records to functions expecting fewer fields
- Extra fields are carried through but not accessed
- No explicit projection required for "downcast"

## From Module Output

Modules return records that can be accessed and merged:

```constellation
user = GetUser(id)           # returns { id, name, email }
profile = GetProfile(id)     # returns { bio, avatar }

# Access individual fields
userName = user.name
userBio = profile.bio

# Merge for combined record
combined = user + profile    # { id, name, email, bio, avatar }

# Project subset
public = combined[id, name, bio]  # { id, name, bio }
```

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `core` | Runtime record representation | `modules/core/src/main/scala/io/constellation/TypeSystem.scala` (`CType.CProduct`, `CValue.CProduct`) |
| `lang-parser` | Parse record type syntax | `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala` |
| `lang-compiler` | Record type checking | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/SemanticType.scala` (`SRecord`) |
| `lang-compiler` | Field access validation | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala:504-544` |
| `lang-compiler` | Width subtyping | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/Subtyping.scala:54-59` |

## Implementation Details

### CType.CProduct

Runtime representation of record types:

```scala
final case class CProduct(structure: Map[String, CType]) extends CType
```

The `structure` map contains field names to their types.

### CValue.CProduct

Runtime representation of record values:

```scala
final case class CProduct(value: Map[String, CValue], structure: Map[String, CType])
    extends CValue
```

Both the values and the type structure are carried at runtime.

### SemanticType.SRecord

Compile-time representation:

```scala
final case class SRecord(fields: Map[String, SemanticType]) extends SemanticType
```

### Type Checking Field Access

From `TypeChecker.scala`:

```scala
case Expression.FieldAccess(source, field) =>
  checkExpression(source.value, source.span, env).andThen { typedSource =>
    typedSource.semanticType match {
      case SemanticType.SRecord(availableFields) =>
        availableFields.get(field.value) match {
          case Some(fieldType) =>
            TypedExpression.FieldAccess(typedSource, field.value, fieldType, span).validNel
          case None =>
            CompileError.InvalidFieldAccess(field.value, availableFields.keys.toList, Some(field.span)).invalidNel
        }
      // ... List<Record> element-wise case
    }
  }
```

## Best Practices

1. **Use type aliases for reusable structures.** Define `type User = {...}` rather than repeating inline.
2. **Keep records flat when possible.** Deep nesting makes access verbose.
3. **Let subtyping work for you.** Don't project just to match a function's expected type.
4. **Document field semantics.** Type says "String", but meaning (UserId? Email?) needs comments.

## Related

- [type-algebra.md](./type-algebra.md) - Merge and projection operations
- [optionals.md](./optionals.md) - Optional field access
- [website/docs/language/types/records.md](../../language/types/records.md) - Language reference
