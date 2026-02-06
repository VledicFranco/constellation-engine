# Optional Types

> **Path**: `docs/features/type-safety/optionals.md`
> **Parent**: [type-safety/](./README.md)

Values that may or may not be present, with compile-time tracking of absence.

## Quick Example

```constellation
in user: { name: String, tier: String }
in threshold: Int

# Guard creates Optional - present only if condition is true
premium = user when user.tier == "premium"
# Type: Optional<{ name: String, tier: String }>

# Coalesce unwraps with fallback
result = premium ?? { name: "Guest", tier: "free" }
# Type: { name: String, tier: String } (guaranteed present)
```

## Syntax

### Type Declaration

```constellation
type MaybeUser = Optional<User>
type OptionalString = Optional<String>
type MaybeInt = Optional<Int>
```

### Guard Expression (`when`)

Create an Optional from a condition:

```constellation
# expr when condition -> Optional<TypeOfExpr>
filtered = value when condition
```

### Coalesce Operator (`??`)

Unwrap an Optional with a fallback value:

```constellation
# Optional<T> ?? T -> T
result = optional ?? fallback
```

## Creating Optionals

### Guard Expression

The `when` keyword creates an `Optional` from a condition:

```constellation
in user: User
in threshold: Int

# premium is Optional<User> - present only if condition is true
premium = user when user.tier == "premium"

# filtered is Optional<Order> - present only if amount > threshold
filtered = order when order.amount > threshold
```

**Semantics:**
- If condition is `true`: result is `Some(value)`
- If condition is `false`: result is `None`

### Module Output

Modules can return Optional types directly:

```constellation
# FindUser returns Optional<User> - user may not exist
maybeUser = FindUser(email)

# LookupCache returns Optional<Data> - may be cache miss
cached = LookupCache(key)
```

## Unwrapping Optionals

### Coalesce Operator

Use `??` to provide a default when absent:

```constellation
in maybeName: Optional<String>

name = maybeName ?? "Anonymous"
# Result: String (guaranteed present)
```

### With Records

```constellation
in maybeUser: Optional<User>

user = maybeUser ?? { id: "", name: "Guest", tier: "free" }
# Result: User (guaranteed present)
```

### Type Rules

```constellation
# Optional<T> ?? T -> T
result = optionalInt ?? 0            # Int

# Optional<T> ?? Optional<T> -> Optional<T>
result = maybeA ?? maybeB            # Optional<T>

# Error: fallback type must match inner type
result = optionalInt ?? "default"    # Compile error
```

## Optional Field Access

Accessing fields through Optional propagates the Optional:

```constellation
in maybeUser: Optional<{ name: String, age: Int }>

maybeName = maybeUser.name  # Optional<String>
maybeAge = maybeUser.age    # Optional<Int>
```

**Note:** The result is also Optional. To get a concrete value, use coalesce:

```constellation
name = maybeUser.name ?? "Unknown"  # String
```

## Combining Guards

Chain guards for multiple conditions:

```constellation
in order: Order

# First guard
highValue = order when order.amount > 1000
# Type: Optional<Order>

# Second guard (on Optional)
recent = highValue when highValue.date > cutoff
# Type: Optional<Order>

# Unwrap with fallback
result = recent ?? defaultOrder
# Type: Order
```

## Compile-Time Validation

The compiler validates Optional usage:

```constellation
in maybeUser: Optional<User>

# Error: cannot access field on Optional without coalesce
name = maybeUser.name  # This is Optional<String>, not String

# Correct: coalesce first
user = maybeUser ?? defaultUser
name = user.name  # String

# Or: coalesce the field access
name = maybeUser.name ?? "Unknown"  # String
```

### Coalesce Type Checking

```constellation
in maybeInt: Optional<Int>

# Error: fallback type doesn't match
result = maybeInt ?? "not a number"
# Expected: Int, Found: String

# Correct
result = maybeInt ?? 0  # Int
```

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `core` | Runtime Optional representation | `modules/core/src/main/scala/io/constellation/TypeSystem.scala` (`CType.COptional`, `CValue.CSome`, `CValue.CNone`) |
| `lang-parser` | Parse `when` and `??` syntax | `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala` |
| `lang-compiler` | Optional type checking | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/SemanticType.scala` (`SOptional`) |
| `lang-compiler` | Guard expression type checking | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala:643-659` |
| `lang-compiler` | Coalesce expression type checking | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/TypeChecker.scala:661-691` |
| `lang-compiler` | Optional subtyping (covariance) | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/Subtyping.scala:47-48` |

## Implementation Details

### CType.COptional

Runtime type representation:

```scala
final case class COptional(innerType: CType) extends CType
```

### CValue.CSome / CValue.CNone

Runtime value representation:

```scala
// Present value
final case class CSome(value: CValue, innerType: CType) extends CValue {
  override def ctype: CType = CType.COptional(innerType)
}

// Absent value
final case class CNone(innerType: CType) extends CValue {
  override def ctype: CType = CType.COptional(innerType)
}
```

### Guard Type Checking

From `TypeChecker.scala`:

```scala
case Expression.Guard(expr, condition) =>
  (checkExpression(expr), checkExpression(condition)).mapN { (typedExpr, typedCondition) =>
    if typedCondition.semanticType != SemanticType.SBoolean then
      CompileError.TypeMismatch("Boolean", typedCondition.semanticType.prettyPrint, ...).invalidNel
    else
      // Result is Optional<ExprType>
      TypedExpression.Guard(typedExpr, typedCondition, span).validNel
  }
```

The typed expression has `semanticType = SemanticType.SOptional(expr.semanticType)`.

### Coalesce Type Checking

From `TypeChecker.scala`:

```scala
case Expression.Coalesce(left, right) =>
  // Left must be Optional<T>
  typedLeft.semanticType match {
    case SemanticType.SOptional(innerType) =>
      val rightType = typedRight.semanticType
      // Check: right is T or Optional<T>
      if innerType == rightType then
        // Optional<T> ?? T -> T
        TypedExpression.Coalesce(typedLeft, typedRight, span, rightType).validNel
      else rightType match {
        case SemanticType.SOptional(rightInner) if innerType == rightInner =>
          // Optional<T> ?? Optional<T> -> Optional<T>
          ...
        case _ =>
          CompileError.TypeMismatch(innerType.prettyPrint, rightType.prettyPrint, ...).invalidNel
      }
    case other =>
      CompileError.TypeError("Left side of ?? must be Optional", ...).invalidNel
  }
```

### Optional Subtyping

Optionals are covariant: `Optional<Sub> <: Optional<Super>` if `Sub <: Super`.

```scala
case (SemanticType.SOptional(subInner), SemanticType.SOptional(supInner)) =>
  isSubtype(subInner, supInner)
```

## Use Cases

| Pattern | Example | Result Type |
|---------|---------|-------------|
| Conditional processing | `user when user.active` | `Optional<User>` |
| Default values | `maybeConfig ?? defaultConfig` | `Config` |
| Null safety | Module returns `Optional` instead of null | `Optional<T>` |
| Filter single value | `item when item.price < 100` | `Optional<Item>` |
| Chained conditions | `a when c1 when c2` | `Optional<A>` |

## Best Practices

1. **Use Optional for domain absence.** "User might not exist" is `Optional<User>`, not null.
2. **Coalesce early.** Don't propagate Optional through many expressions; unwrap when you have a sensible default.
3. **Guard for filtering.** Use `when` to create Optional from boolean conditions.
4. **Document fallback semantics.** Make clear what the default value means in context.

## Related

- [record-types.md](./record-types.md) - Optional field access on records
- [type-algebra.md](./type-algebra.md) - Merge and projection with Optionals
- [unions.md](./unions.md) - Union types for multiple possible types
- [docs/language/types/optionals.md](../../language/types/optionals.md) - Language reference
