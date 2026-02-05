# Control Flow

> **Path**: `docs/language/expressions/control-flow.md`
> **Parent**: [expressions/](./README.md)

Conditional execution and value handling.

## Guard Expression (`when`)

Creates an `Optional<T>` based on a condition:

```constellation
in order: Order

# highValue is Optional<Order>
highValue = order when order.amount > 1000
```

When condition is:
- `true` → value is present
- `false` → value is absent

### Chained Guards

```constellation
validated = data when data.isValid
filtered = validated when validated.score > threshold
result = filtered ?? defaultValue
```

### With Module Calls

```constellation
# Only call expensive module if condition met
premium = GetPremiumData(user) when user.tier == "premium"
```

## Coalesce Operator (`??`)

Unwrap Optional with a default:

```constellation
in maybeName: Optional<String>
name = maybeName ?? "Anonymous"  # Result: String
```

### With Guards

```constellation
premium = user when user.tier == "premium"
result = premium ?? guestUser
```

### Chained Coalesce

```constellation
# Try multiple sources
value = primary ?? secondary ?? default
```

## Branch Expression

Pattern match on union types:

```constellation
in result: Success | Error

output = branch result {
  Success => result.data
  Error => defaultData
}
```

### With Data Extraction

```constellation
in response: Ok { data: User } | Err { message: String }

user = branch response {
  Ok => response.data
  Err => guestUser
}
```

## If-Else Expression

Simple conditional:

```constellation
in score: Int

tier = if (score > 90) "gold" else if (score > 70) "silver" else "bronze"
```

## Lambda Expressions

For higher-order functions:

```constellation
in users: List<User>

# Filter with lambda
active = filter(users, (u) => u.active)

# Map with lambda
names = map(users, (u) => u.name)

# Predicate checks
allValid = all(users, (u) => u.verified)
anyAdmin = any(users, (u) => u.role == "admin")
```

## Combining Patterns

```constellation
in orders: List<Order>
in threshold: Int

# Filter, guard, coalesce
highValue = filter(orders, (o) => o.amount > threshold)
recent = highValue when list-length(highValue) > 0
result = recent ?? []
out result
```
