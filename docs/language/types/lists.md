# List Types

> **Path**: `docs/language/types/lists.md`
> **Parent**: [types/](./README.md)

Ordered, homogeneous collections.

## Syntax

```constellation
type UserList = List<User>
type Numbers = List<Int>
type Matrix = List<List<Float>>
```

## Element-Wise Field Access

Access a field from each element:

```constellation
in users: List<{ id: String, name: String, age: Int }>

ids = users.id      # List<String> - extracts id from each user
names = users.name  # List<String>
ages = users.age    # List<Int>
```

## Element-Wise Projection

Project multiple fields from each element:

```constellation
in users: List<{ id: String, name: String, email: String, password: String }>

public = users[id, name]
# Result: List<{ id: String, name: String }>
```

## Higher-Order Functions

See [stdlib/higher-order.md](../../stdlib/higher-order.md) for details.

```constellation
# Filter elements
active = filter(users, (u) => u.active)

# Transform elements
names = map(users, (u) => u.name)

# Check predicates
allActive = all(users, (u) => u.active)
anyAdmin = any(users, (u) => u.role == "admin")
```

## List Operations

See [stdlib/list.md](../../stdlib/list.md) for details.

| Function | Description |
|----------|-------------|
| `list-length(list)` | Number of elements |
| `list-first(list)` | First element |
| `list-last(list)` | Last element |
| `list-is-empty(list)` | True if empty |
| `list-sum(list)` | Sum of numeric list |
| `list-concat(a, b)` | Concatenate lists |
| `list-reverse(list)` | Reverse order |

## Candidates (Legacy Alias)

`Candidates<T>` is an alias for `List<T>`, preserved for backwards compatibility:

```constellation
in candidates: Candidates<Recommendation>
# Equivalent to:
in candidates: List<Recommendation>
```
