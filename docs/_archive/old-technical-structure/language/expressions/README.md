# Expressions

> **Path**: `docs/language/expressions/`
> **Parent**: [language/](../README.md)

Operators and control flow in constellation-lang.

## Contents

| File | Description |
|------|-------------|
| [field-access.md](./field-access.md) | Dot notation, element-wise access |
| [type-ops.md](./type-ops.md) | Merge (+), projection ([]) |
| [control-flow.md](./control-flow.md) | Guards (when), coalesce (??), branch |
| [arithmetic.md](./arithmetic.md) | Math and comparison operators |

## Expression Categories

| Category | Operators | Example |
|----------|-----------|---------|
| Field access | `.` | `user.name` |
| Element-wise | `.` on list | `users.name` |
| Merge | `+` | `a + b` |
| Projection | `[]` | `user[id, name]` |
| Guard | `when` | `x when cond` |
| Coalesce | `??` | `opt ?? default` |
| Comparison | `==`, `>`, `<`, etc. | `a == b` |
| Logical | `and`, `or`, `not` | `a and b` |
| Arithmetic | `+`, `-`, `*`, `/` | `a + b` |

## Precedence (highest to lowest)

1. Field access (`.`)
2. Projection (`[]`)
3. Unary (`not`, `-`)
4. Multiplicative (`*`, `/`)
5. Additive (`+`, `-`) / Merge (`+`)
6. Comparison (`==`, `!=`, `<`, `>`, `<=`, `>=`)
7. Logical AND (`and`)
8. Logical OR (`or`)
9. Guard (`when`)
10. Coalesce (`??`)

## Examples

```constellation
# Chained field access
city = customer.address.city

# Element-wise then projection
ids = orders[id, status].id

# Guard with comparison
highValue = order when order.amount > 1000

# Coalesce with merge
result = (data ?? defaults) + overrides
```
