---
title: "Standard Library"
sidebar_position: 3
description: "Built-in functions available in every pipeline"
---

# Standard Library Reference

The constellation-lang standard library provides commonly-used functions for pipeline orchestration.

## Using the Standard Library

### Quick Start

```scala
import io.constellation.lang.stdlib.StdLib

// Create a compiler with all stdlib functions registered
val compiler = StdLib.compiler

val source = """
  in a: Int
  in b: Int
  sum = add(a, b)
  isGreater = gt(sum, b)
  out isGreater
"""

compiler.compile(source, "my-dag")
```

### Selective Registration

Register only specific functions:

```scala
import io.constellation.lang.LangCompiler
import io.constellation.lang.stdlib._

val compiler = LangCompilerBuilder()
  .withFunction(MathOps.addSignature)
  .withFunction(MathOps.subtractSignature)
  .withFunction(CompareOps.gtSignature)
  .build
```

## Function Reference

### Math Operations

| Function | Signature | Description |
|----------|-----------|-------------|
| `add` | `(a: Int, b: Int) -> Int` | Add two integers |
| `subtract` | `(a: Int, b: Int) -> Int` | Subtract b from a |
| `multiply` | `(a: Int, b: Int) -> Int` | Multiply two integers |
| `divide` | `(a: Int, b: Int) -> Int` | Integer division (returns 0 if b is 0) |
| `max` | `(a: Int, b: Int) -> Int` | Maximum of two integers |
| `min` | `(a: Int, b: Int) -> Int` | Minimum of two integers |
| `abs` | `(value: Int) -> Int` | Absolute value |
| `modulo` | `(a: Int, b: Int) -> Int` | Remainder after division (returns 0 if b is 0) |
| `round` | `(value: Float) -> Int` | Round float to nearest integer |
| `negate` | `(value: Int) -> Int` | Negate a number |

**Examples:**

```
in x: Int
in y: Int

sum = add(x, y)
diff = subtract(x, y)
product = multiply(x, y)
quotient = divide(x, y)
maximum = max(x, y)
minimum = min(x, y)
absolute = abs(x)
remainder = modulo(x, y)
negated = negate(x)

out sum
```

### String Operations

| Function | Signature | Description |
|----------|-----------|-------------|
| `concat` | `(a: String, b: String) -> String` | Concatenate two strings |
| `string-length` | `(value: String) -> Int` | Get string length |
| `join` | `(list: List<String>, separator: String) -> String` | Join strings with delimiter |
| `split` | `(value: String, substring: String) -> List<String>` | Split string by delimiter |
| `contains` | `(value: String, substring: String) -> Boolean` | Check if string contains substring |
| `trim` | `(value: String) -> String` | Trim whitespace |
| `replace` | `(value: String, target: String, replacement: String) -> String` | Replace occurrences |

**Examples:**

```
in firstName: String
in lastName: String

fullName = concat(firstName, lastName)
nameLength = string-length(fullName)
trimmed = trim(fullName)
hasSpace = contains(fullName, " ")
cleaned = replace(fullName, " ", "-")

out fullName
```

### Boolean Operations

| Function | Signature | Description |
|----------|-----------|-------------|
| `and` | `(a: Boolean, b: Boolean) -> Boolean` | Logical AND |
| `or` | `(a: Boolean, b: Boolean) -> Boolean` | Logical OR |
| `not` | `(value: Boolean) -> Boolean` | Logical NOT |

**Examples:**

```
in isActive: Boolean
in isVerified: Boolean

canProceed = and(isActive, isVerified)
hasAccess = or(isActive, isVerified)
isInactive = not(isActive)

out canProceed
```

### Comparison Operations

| Function | Signature | Description |
|----------|-----------|-------------|
| `eq-int` | `(a: Int, b: Int) -> Boolean` | Check if integers are equal |
| `eq-string` | `(a: String, b: String) -> Boolean` | Check if strings are equal |
| `gt` | `(a: Int, b: Int) -> Boolean` | Check if a > b |
| `lt` | `(a: Int, b: Int) -> Boolean` | Check if a < b |
| `gte` | `(a: Int, b: Int) -> Boolean` | Check if a >= b |
| `lte` | `(a: Int, b: Int) -> Boolean` | Check if a <= b |

**Examples:**

```
in score: Int
in threshold: Int
in expected: String
in actual: String

isAbove = gt(score, threshold)
isBelow = lt(score, threshold)
isEqual = eq-int(score, threshold)
matches = eq-string(expected, actual)

# Use with conditional
result = if (isAbove) score else threshold

out result
```

### List Operations

| Function | Signature | Description |
|----------|-----------|-------------|
| `list-length` | `(list: List<Int>) -> Int` | Get list length |
| `list-first` | `(list: List<Int>) -> Int` | Get first element (0 if empty) |
| `list-last` | `(list: List<Int>) -> Int` | Get last element (0 if empty) |
| `list-is-empty` | `(list: List<Int>) -> Boolean` | Check if list is empty |
| `list-sum` | `(list: List<Int>) -> Int` | Sum all elements |
| `list-concat` | `(a: List<Int>, b: List<Int>) -> List<Int>` | Concatenate two lists |
| `list-contains` | `(list: List<Int>, value: Int) -> Boolean` | Check if element exists |
| `list-reverse` | `(list: List<Int>) -> List<Int>` | Reverse list order |

**Examples:**

```
in numbers: List<Int>

count = list-length(numbers)
first = list-first(numbers)
last = list-last(numbers)
isEmpty = list-is-empty(numbers)
total = list-sum(numbers)
reversed = list-reverse(numbers)

out count
```

### Type Conversion Operations

| Function | Signature | Description |
|----------|-----------|-------------|
| `to-string` | `(value: Int) -> String` | Convert integer to string |
| `to-int` | `(value: Float) -> Int` | Truncate float to integer |
| `to-float` | `(value: Int) -> Float` | Convert integer to float |

**Examples:**

```
in count: Int
in measurement: Float

label = to-string(count)
truncated = to-int(measurement)
precise = to-float(count)

out label
```

### Higher-Order Functions

| Function | Signature | Description |
|----------|-----------|-------------|
| `filter` | `(List<Int>, (Int) => Boolean) -> List<Int>` | Keep elements matching predicate |
| `map` | `(List<Int>, (Int) => Int) -> List<Int>` | Transform each element |
| `all` | `(List<Int>, (Int) => Boolean) -> Boolean` | Check all elements match |
| `any` | `(List<Int>, (Int) => Boolean) -> Boolean` | Check any element matches |

**Examples:**

```
use stdlib.collection
use stdlib.compare
use stdlib.math

in numbers: List<Int>

positives = filter(numbers, (x) => gt(x, 0))
doubled = map(numbers, (x) => multiply(x, 2))
allPositive = all(numbers, (x) => gt(x, 0))
anyNegative = any(numbers, (x) => lt(x, 0))

out positives
```

### Utility Operations

| Function | Signature | Description |
|----------|-----------|-------------|
| `identity` | `(value: String) -> String` | Pass-through (returns input unchanged) |
| `log` | `(message: String) -> String` | Log message and pass through |

**Examples:**

```
in message: String

# Log for debugging
logged = log(message)

# Identity (useful for type coercion)
same = identity(message)

out logged
```

## Common Patterns

### Conditional Logic with Comparisons

```
in score: Int

threshold = 50
isPass = gt(score, threshold)
result = if (isPass) score else threshold

out result
```

### Chaining Operations

```
in a: Int
in b: Int
in c: Int

# Compute (a + b) * c
sum = add(a, b)
product = multiply(sum, c)

out product
```

### String Processing

```
in firstName: String
in lastName: String

# Create "lastName, firstName"
separator = ", "
temp = concat(lastName, separator)
fullName = concat(temp, firstName)
trimmed = trim(fullName)

out trimmed
```

### Boolean Gates

```
in isAuthenticated: Boolean
in hasPermission: Boolean
in isAdmin: Boolean

# Check: (authenticated AND permission) OR admin
hasAccess = and(isAuthenticated, hasPermission)
canProceed = or(hasAccess, isAdmin)

out canProceed
```

## Namespaces

All stdlib functions are organized into namespaces:

| Namespace | Functions |
|-----------|-----------|
| `stdlib.math` | add, subtract, multiply, divide, max, min, abs, modulo, round, negate |
| `stdlib.string` | concat, string-length, join, split, contains, trim, replace |
| `stdlib.compare` | eq-int, eq-string, gt, lt, gte, lte |
| `stdlib.bool` | and, or, not |
| `stdlib.list` | list-length, list-first, list-last, list-is-empty, list-sum, list-concat, list-contains, list-reverse |
| `stdlib.collection` | filter, map, all, any |
| `stdlib.convert` | to-string, to-int, to-float |
| `stdlib` | identity |
| `stdlib.debug` | log |

## Module Details

Each stdlib function is implemented as a Constellation module. The modules are registered with these names:

| Function | Module Name |
|----------|-------------|
| `add` | `stdlib.add` |
| `subtract` | `stdlib.subtract` |
| `multiply` | `stdlib.multiply` |
| `divide` | `stdlib.divide` |
| `max` | `stdlib.max` |
| `min` | `stdlib.min` |
| `abs` | `stdlib.abs` |
| `modulo` | `stdlib.modulo` |
| `round` | `stdlib.round` |
| `negate` | `stdlib.negate` |
| `concat` | `stdlib.concat` |
| `string-length` | `stdlib.string-length` |
| `join` | `stdlib.join` |
| `split` | `stdlib.split` |
| `contains` | `stdlib.contains` |
| `trim` | `stdlib.trim` |
| `replace` | `stdlib.replace` |
| `and` | `stdlib.and` |
| `or` | `stdlib.or` |
| `not` | `stdlib.not` |
| `eq-int` | `stdlib.eq-int` |
| `eq-string` | `stdlib.eq-string` |
| `gt` | `stdlib.gt` |
| `lt` | `stdlib.lt` |
| `gte` | `stdlib.gte` |
| `lte` | `stdlib.lte` |
| `list-length` | `stdlib.list-length` |
| `list-first` | `stdlib.list-first` |
| `list-last` | `stdlib.list-last` |
| `list-is-empty` | `stdlib.list-is-empty` |
| `list-sum` | `stdlib.list-sum` |
| `list-concat` | `stdlib.list-concat` |
| `list-contains` | `stdlib.list-contains` |
| `list-reverse` | `stdlib.list-reverse` |
| `to-string` | `stdlib.to-string` |
| `to-int` | `stdlib.to-int` |
| `to-float` | `stdlib.to-float` |
| `identity` | `stdlib.identity` |
| `log` | `stdlib.log` |

## Extending the Standard Library

Add custom functions following the same pattern:

```scala
import io.constellation.ModuleBuilder
import io.constellation.lang.semantic._

object MyModule {
  case class In(value: Int)
  case class Out(result: Int)

  val module = ModuleBuilder
    .metadata("my-namespace.my-function", "Description", 1, 0)
    .tags("custom")
    .implementationPure[In, Out](in => Out(in.value * 2))
    .build

  val signature = FunctionSignature(
    name = "my-function",
    params = List("value" -> SemanticType.SInt),
    returns = SemanticType.SInt,
    moduleName = "my-namespace.my-function"
  )
}
```

Register with the compiler:

```scala
val compiler = StdLib.registerAll(LangCompilerBuilder())
  .withFunction(MyModule.signature)
  .build
```
