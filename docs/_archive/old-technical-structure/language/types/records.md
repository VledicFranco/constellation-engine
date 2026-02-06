# Record Types

> **Path**: `docs/language/types/records.md`
> **Parent**: [types/](./README.md)

Structured data with named fields.

## Syntax

```constellation
type User = {
  id: String,
  name: String,
  age: Int,
  email: String
}
```

## Nested Records

```constellation
type Address = {
  street: String,
  city: String
}

type Customer = {
  id: String,
  name: String,
  address: Address
}
```

## Field Access

```constellation
in customer: Customer
name = customer.name           # String
city = customer.address.city   # String (nested)
```

## Merge Operation

Combine fields from two records with `+`:

```constellation
type UserInfo = { id: String, name: String }
type UserStats = { loginCount: Int, lastSeen: String }

in info: UserInfo
in stats: UserStats
combined = info + stats
# Result: { id: String, name: String, loginCount: Int, lastSeen: String }
```

**Field conflict**: If both records have the same field, right-hand side wins.

## Projection

Select subset of fields with `[field1, field2]`:

```constellation
in user: { id: String, name: String, email: String, password: String }
public = user[id, name, email]
# Result: { id: String, name: String, email: String }
```

## Anonymous Records

Records can be used inline without type alias:

```constellation
in data: { x: Int, y: Int }
result = Process(data)
```

## From Module Output

Modules return records that can be accessed and merged:

```constellation
user = GetUser(id)           # returns { id, name, email }
profile = GetProfile(id)     # returns { bio, avatar }
combined = user + profile    # merge both
out combined[id, name, bio]  # project subset
```
