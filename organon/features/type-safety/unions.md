# Union Types

> **Path**: `organon/features/type-safety/unions.md`
> **Parent**: [type-safety/](./README.md)

Tagged variants for sum types (discriminated unions).

## Quick Example

```constellation
# Define union type
type Result = { value: Int, status: String } | { error: String, code: Int }

in response: Result

# Pattern match on union variants
output = match response {
  { value, status } -> "Success: ${value}",
  { error, code } -> "Error ${code}: ${error}"
}
```

## Syntax

### Simple Unions

Union of type names:

```constellation
type Result = Success | Error
type PaymentStatus = Pending | Completed | Failed | Refunded
type Permission = Read | Write | Admin
```

### Unions with Data

Variants can carry associated data:

```constellation
type ApiResponse =
  | Success { data: UserData }
  | Error { code: Int, message: String }

type ValidationResult =
  | Valid { value: String }
  | Invalid { errors: List<String> }
```

### Inline Unions

Unions can be used without type alias:

```constellation
in result: Success | Error
in status: Pending | Processing | Complete
```

## Creating Union Values

Modules return union types to express multiple possible outcomes:

```constellation
# ProcessPayment returns PaymentResult = Success | Failure
result = ProcessPayment(order)

# ValidateInput returns Valid { value } | Invalid { errors }
validation = ValidateInput(data)
```

**Note:** Union values are created by modules, not by literals in the DSL. The DSL handles union values returned from modules.

## Pattern Matching with Match

Use `match` expression to discriminate between union variants:

### Basic Match

```constellation
type Result = { value: Int } | { error: String }
in result: Result

output = match result {
  { value } -> "Got value: ${value}",
  { error } -> "Error: ${error}"
}
# Type: String
```

### Field Binding

Pattern fields become variables in the match body:

```constellation
type User = { name: String, age: Int } | { error: String, code: Int }
in user: User

message = match user {
  { name, age } -> "Hello ${name}, you are ${age}",
  { error, code } -> "Error ${code}: ${error}"
}
# Type: String
```

### Wildcard Pattern

Use `_` to match any remaining variants:

```constellation
type State = { active: Boolean } | { pending: Int } | { banned: String }
in state: State

isActive = match state {
  { active } -> active,
  _ -> false
}
# Type: Boolean
```

## Exhaustiveness Checking

The compiler ensures all union variants are covered:

```constellation
type State = { active: Boolean } | { pending: Int } | { banned: String }
in state: State

# Compile error: missing { pending } and { banned }
output = match state {
  { active } -> "active"
  # Error: Non-exhaustive match. Missing: { pending: Int }, { banned: String }
}
```

To fix, either:
1. Add patterns for all variants
2. Use `_` wildcard for a catch-all

```constellation
# Option 1: Handle all cases
output = match state {
  { active } -> if (active) "active" else "inactive",
  { pending } -> "pending",
  { banned } -> "banned"
}

# Option 2: Use wildcard
output = match state {
  { active } -> if (active) "active" else "inactive",
  _ -> "not active"
}
```

## Union Type Operations

### Combining with Optional

```constellation
type MaybeResult = Optional<Success | Error>

# Guard can produce Optional of a union
validated = result when isValid
# Type: Optional<Success | Error>
```

### Union in Records

Records can contain union-typed fields:

```constellation
type Order = {
  id: String,
  status: Pending | Shipped | Delivered,
  payment: Paid | Unpaid
}
```

### Unions in Lists

```constellation
type Event = Created | Updated | Deleted
in events: List<Event>

# Element-wise branch (future feature)
# For now, use a module to map over list
```

## Type Inference

Union types are inferred when branches have different types:

```constellation
in condition: Boolean

# Branches have different types -> union
result = if condition then
  { status: "success" }
else
  { error: "failed" }
# Type: { status: String } | { error: String }
```

The compiler uses LUB (Least Upper Bound) to find the common type:
- If one is subtype of other, use the supertype
- Otherwise, create a union

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `core` | Runtime union representation | `modules/core/src/main/scala/io/constellation/TypeSystem.scala` (`CType.CUnion`, `CValue.CUnion`) |
| `lang-ast` | Match expression AST | `modules/lang-ast/src/main/scala/io/constellation/lang/ast/AST.scala` (`Expression.Match`, `Pattern`) |
| `lang-parser` | Parse union type syntax and match expressions | `modules/lang-parser/src/main/scala/io/constellation/lang/parser/ConstellationParser.scala` |
| `lang-compiler` | Union type resolution | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/SemanticType.scala` (`SUnion`) |
| `lang-compiler` | Match expression type checking & exhaustiveness | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/BidirectionalTypeChecker.scala` |
| `lang-compiler` | Match IR generation | `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/IR.scala` (`MatchNode`) |
| `lang-compiler` | Match DAG compilation | `modules/lang-compiler/src/main/scala/io/constellation/lang/compiler/DagCompiler.scala` |
| `lang-compiler` | Union subtyping | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/Subtyping.scala:79-84` |
| `lang-compiler` | LUB computation for unions | `modules/lang-compiler/src/main/scala/io/constellation/lang/semantic/Subtyping.scala:114-131` |

## Implementation Details

### CType.CUnion

Runtime type representation:

```scala
final case class CUnion(structure: Map[String, CType]) extends CType
```

The `structure` maps variant tags to their associated data types.

### CValue.CUnion

Runtime value representation:

```scala
final case class CUnion(value: CValue, structure: Map[String, CType], tag: String) extends CValue {
  override def ctype: CType = CType.CUnion(structure)
}
```

The `tag` identifies which variant this value is.

### SemanticType.SUnion

Compile-time representation:

```scala
final case class SUnion(members: Set[SemanticType]) extends SemanticType {
  def prettyPrint: String = members.map(_.prettyPrint).toList.sorted.mkString(" | ")
}
```

### Union Subtyping

From `Subtyping.scala`:

```scala
// Union as supertype: sub is subtype if it's subtype of any member
case (_, SemanticType.SUnion(supMembers)) =>
  supMembers.exists(m => isSubtype(sub, m))

// Union as subtype: all members must be subtypes of sup
case (SemanticType.SUnion(subMembers), _) =>
  subMembers.forall(m => isSubtype(m, sup))
```

### LUB for Unions

When no direct subtype relationship exists, LUB creates a union:

```scala
def lub(a: SemanticType, b: SemanticType): SemanticType =
  if isSubtype(a, b) then b
  else if isSubtype(b, a) then a
  else {
    // Flatten existing unions and combine
    val aMembers = a match {
      case SemanticType.SUnion(members) => members
      case other => Set(other)
    }
    val bMembers = b match {
      case SemanticType.SUnion(members) => members
      case other => Set(other)
    }
    SemanticType.SUnion(aMembers ++ bMembers)
  }
```

## Use Cases

| Pattern | Example | Description |
|---------|---------|-------------|
| Error handling | `Success { data } \| Error { message }` | Explicit success/failure |
| State machines | `Pending \| Processing \| Complete` | Workflow states |
| Polymorphic returns | `User \| Guest \| Admin` | Role-based types |
| Validation | `Valid { value } \| Invalid { errors }` | Validation results |
| Parse results | `Parsed { ast } \| ParseError { line, message }` | Parser output |

## Comparison with Optional

| Feature | Optional | Union |
|---------|----------|-------|
| Purpose | Value might be absent | Value is one of N types |
| Variants | 2 (Some, None) | N (user-defined) |
| Data | Single inner type | Different data per variant |
| Pattern match | `??` coalesce | `match` expression |
| Use case | Nullable values | Sum types, ADTs |

```constellation
# Optional: value or nothing
maybeUser: Optional<User>

# Union: different kinds of values
result: Success { data: User } | NotFound | Forbidden
```

## Best Practices

1. **Use unions for domain variants.** Payment statuses, order states, response types.
2. **Prefer exhaustive matching.** Handle all cases explicitly rather than using `_` wildcard.
3. **Use `match` for union discrimination.** The compiler checks exhaustiveness.
4. **Keep variant count small.** Large unions (10+ variants) suggest refactoring.
5. **Document variant semantics.** What does each variant mean in the domain?
6. **Use Optional for simple absence.** Don't create `Present | Absent` when `Optional` suffices.

## Limitations

Current limitations of union types:

1. **No variant constructors in DSL.** Union values come from modules, not literals.
2. **No nested pattern matching.** Can't match on nested union structure in one pattern.
3. **No type narrowing.** After match, bound variables have the field type from the matched pattern.
4. **No generic unions.** Can't parameterize union types (e.g., `Result<T, E>`).
5. **No type test patterns for primitives.** `is String` syntax is parsed but not yet fully implemented.

These may be addressed in future RFCs.

## Related

- [optionals.md](./optionals.md) - Optional types for simple absence
- [record-types.md](./record-types.md) - Records as union variant data
- [website/docs/language/types/unions.md](../../language/types/unions.md) - Language reference
