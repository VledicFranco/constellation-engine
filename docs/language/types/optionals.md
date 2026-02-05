# Optional Types

> **Path**: `docs/language/types/optionals.md`
> **Parent**: [types/](./README.md)

Values that may or may not be present.

## Syntax

```constellation
type MaybeUser = Optional<User>
type OptionalString = Optional<String>
```

## Creating Optionals

### Guard Expression

The `when` keyword creates an Optional from a condition:

```constellation
in user: User
in threshold: Int

# premium is Optional<User> - present only if condition is true
premium = user when user.tier == "premium"

# filtered is Optional<Order> - present only if amount > threshold
filtered = order when order.amount > threshold
```

### Module Output

Modules can return Optional types:

```constellation
# FindUser returns Optional<User>
maybeUser = FindUser(email)
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
premium = user when user.tier == "premium"
result = premium ?? { id: "", name: "Guest", tier: "free" }
```

## Optional Field Access

Access fields through Optional (result is also Optional):

```constellation
in maybeUser: Optional<User>
maybeName = maybeUser.name  # Optional<String>
```

## Combining Guards

Chain guards for multiple conditions:

```constellation
in order: Order
highValue = order when order.amount > 1000
recent = highValue when highValue.date > cutoff
result = recent ?? defaultOrder
```

## Use Cases

| Pattern | Example |
|---------|---------|
| Conditional processing | `premium when user.tier == "premium"` |
| Default values | `config ?? defaultConfig` |
| Null safety | Module returns Optional instead of null |
| Filter single value | `item when item.active` |
