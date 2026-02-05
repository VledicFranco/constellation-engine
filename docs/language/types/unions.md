# Union Types

> **Path**: `docs/language/types/unions.md`
> **Parent**: [types/](./README.md)

Tagged variants for sum types (discriminated unions).

## Syntax

```constellation
type Result = Success | Error
type PaymentStatus = Pending | Completed | Failed | Refunded
```

## With Data

Union variants can carry data:

```constellation
type ApiResponse =
  | Success { data: UserData }
  | Error { code: Int, message: String }
```

## Creating Union Values

Modules return union types:

```constellation
# ProcessPayment returns PaymentResult (Success | Failure)
result = ProcessPayment(order)
```

## Pattern Matching

Use `branch` expression to handle variants:

```constellation
in result: Success | Error

output = branch result {
  Success => result.data
  Error => defaultData
}
```

## Use Cases

| Pattern | Example |
|---------|---------|
| Error handling | `Success { data } \| Error { message }` |
| State machines | `Pending \| Processing \| Complete` |
| Polymorphic returns | `User \| Guest \| Admin` |
| Validation | `Valid { value } \| Invalid { errors }` |

## Combining with Optional

```constellation
type MaybeResult = Optional<Success | Error>

# Guard can produce Optional of a union
validated = result when result.isValid
```

## Type Safety

The compiler ensures all variants are handled:

```constellation
# Compile error if a variant is missing from branch
output = branch status {
  Pending => "waiting"
  Completed => "done"
  # Error: missing Failed, Refunded
}
```
