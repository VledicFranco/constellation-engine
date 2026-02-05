# Field Access

> **Path**: `docs/language/expressions/field-access.md`
> **Parent**: [expressions/](./README.md)

## Record Field Access

Access a field from a record with dot notation:

```constellation
in user: { id: String, name: String, email: String }

userId = user.id      # String
userName = user.name  # String
```

## Nested Access

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

city = customer.address.city      # String
country = customer.address.country  # String
```

## Element-Wise Access

When applied to a list, `.field` extracts that field from each element:

```constellation
in users: List<{ id: String, name: String, age: Int }>

ids = users.id      # List<String>
names = users.name  # List<String>
ages = users.age    # List<Int>
```

## Chaining with List

```constellation
in orders: List<{
  id: String,
  customer: { name: String, email: String }
}>

# Element-wise access through nested structure
customerNames = orders.customer.name  # List<String>
```

## After Merge

Access fields after merging records:

```constellation
user = GetUser(id)
profile = GetProfile(id)
combined = user + profile
name = combined.name  # from user
bio = combined.bio    # from profile
```

## After Projection

Access fields after projection:

```constellation
subset = user[id, name]
id = subset.id    # valid
name = subset.name  # valid
# email = subset.email  # compile error: email not in projection
```

## Type Errors

The compiler catches invalid field access:

```constellation
in user: { id: String, name: String }
age = user.age  # Compile error: field 'age' not found in type
```
