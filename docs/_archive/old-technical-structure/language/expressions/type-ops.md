# Type Operations

> **Path**: `docs/language/expressions/type-ops.md`
> **Parent**: [expressions/](./README.md)

Merge and projection operations on records.

## Merge (`+`)

Combine fields from two records:

```constellation
in user: { id: String, name: String }
in stats: { loginCount: Int, lastSeen: String }

combined = user + stats
# Result type: { id: String, name: String, loginCount: Int, lastSeen: String }
```

### Field Conflicts

When both records have the same field, the right-hand side wins:

```constellation
in base: { name: String, value: Int }
in override: { value: Int, extra: String }

result = base + override
# result.value comes from override
```

### Chaining Merges

```constellation
a = GetA(id)
b = GetB(id)
c = GetC(id)
combined = a + b + c  # left-to-right: (a + b) + c
```

### With Module Outputs

Common pattern for aggregating service calls:

```constellation
in productId: String
inventory = GetInventory(productId)
pricing = GetPricing(productId)
reviews = GetReviews(productId)
product = inventory + pricing + reviews
out product
```

## Projection (`[]`)

Select a subset of fields:

```constellation
in user: { id: String, name: String, email: String, password: String }

public = user[id, name, email]
# Result type: { id: String, name: String, email: String }
```

### Nested Fields

Projection flattens nested access:

```constellation
in data: { user: { id: String, name: String }, meta: { created: String } }

flat = data[user.id, user.name, meta.created]
# Result: { id: String, name: String, created: String }
```

### On Lists (Element-Wise)

Projection applied to list projects each element:

```constellation
in users: List<{ id: String, name: String, email: String, internal: Boolean }>

publicUsers = users[id, name, email]
# Result: List<{ id: String, name: String, email: String }>
```

### After Merge

```constellation
combined = user + profile + settings
output = combined[id, name, theme]  # select from merged result
```

## Combining Operations

```constellation
in orders: List<Order>

# Merge defaults, then project
enriched = orders + defaults
result = enriched[id, status, priority]

# Element-wise access after projection
ids = orders[id, status].id
```
