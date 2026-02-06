# Standard Library

> **Path**: `docs/stdlib/`
> **Parent**: [docs/](../README.md)

Built-in functions for common operations.

## Contents

| File | Description |
|------|-------------|
| [math.md](./math.md) | Arithmetic operations |
| [string.md](./string.md) | String manipulation |
| [list.md](./list.md) | List operations |
| [comparison.md](./comparison.md) | Equality and ordering |
| [higher-order.md](./higher-order.md) | filter, map, all, any |

## Function Summary

### Math (10 functions)
| Function | Signature | Description |
|----------|-----------|-------------|
| `add` | `(Int, Int) → Int` | Addition |
| `subtract` | `(Int, Int) → Int` | Subtraction |
| `multiply` | `(Int, Int) → Int` | Multiplication |
| `divide` | `(Int, Int) → Int` | Division |
| `max` | `(Int, Int) → Int` | Maximum |
| `min` | `(Int, Int) → Int` | Minimum |
| `abs` | `(Int) → Int` | Absolute value |
| `modulo` | `(Int, Int) → Int` | Remainder |
| `round` | `(Float) → Int` | Round to integer |
| `negate` | `(Int) → Int` | Negation |

### String (7 functions)
| Function | Signature | Description |
|----------|-----------|-------------|
| `concat` | `(String, String) → String` | Concatenate |
| `string-length` | `(String) → Int` | Length |
| `join` | `(List<String>, String) → String` | Join with delimiter |
| `split` | `(String, String) → List<String>` | Split by delimiter |
| `contains` | `(String, String) → Boolean` | Substring check |
| `trim` | `(String) → String` | Remove whitespace |
| `replace` | `(String, String, String) → String` | Replace substring |

### List (8 functions)
| Function | Signature | Description |
|----------|-----------|-------------|
| `list-length` | `(List<A>) → Int` | Length |
| `list-first` | `(List<A>) → A` | First element |
| `list-last` | `(List<A>) → A` | Last element |
| `list-is-empty` | `(List<A>) → Boolean` | Empty check |
| `list-sum` | `(List<Int>) → Int` | Sum elements |
| `list-concat` | `(List<A>, List<A>) → List<A>` | Concatenate |
| `list-contains` | `(List<A>, A) → Boolean` | Membership |
| `list-reverse` | `(List<A>) → List<A>` | Reverse |

### Comparison (6 functions)
| Function | Signature | Description |
|----------|-----------|-------------|
| `eq-int` | `(Int, Int) → Boolean` | Integer equality |
| `eq-string` | `(String, String) → Boolean` | String equality |
| `gt` | `(Int, Int) → Boolean` | Greater than |
| `lt` | `(Int, Int) → Boolean` | Less than |
| `gte` | `(Int, Int) → Boolean` | Greater or equal |
| `lte` | `(Int, Int) → Boolean` | Less or equal |

### Higher-Order (4 functions)
| Function | Signature | Description |
|----------|-----------|-------------|
| `filter` | `(List<A>, A → Boolean) → List<A>` | Keep matching |
| `map` | `(List<A>, A → B) → List<B>` | Transform each |
| `all` | `(List<A>, A → Boolean) → Boolean` | All match |
| `any` | `(List<A>, A → Boolean) → Boolean` | Any match |

### Utility (2 functions)
| Function | Signature | Description |
|----------|-----------|-------------|
| `identity` | `(A) → A` | Pass through |
| `log` | `(A) → A` | Log and pass through |

### Conversion (3 functions)
| Function | Signature | Description |
|----------|-----------|-------------|
| `to-string` | `(A) → String` | Convert to string |
| `to-int` | `(String) → Int` | Parse integer |
| `to-float` | `(String) → Float` | Parse float |

## Usage

```constellation
in numbers: List<Int>
in text: String

# Math
total = list-sum(numbers)
doubled = multiply(total, 2)

# String
upper = Uppercase(text)  # custom module
length = string-length(text)

# List
first = list-first(numbers)
reversed = list-reverse(numbers)

# Higher-order
positive = filter(numbers, (n) => gt(n, 0))
```
