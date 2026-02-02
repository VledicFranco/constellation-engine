---
title: "Standard Library"
sidebar_position: 3
description: "Built-in functions available in every pipeline"
---

# Standard Library Reference

The constellation-lang standard library provides commonly-used functions for ML pipeline orchestration.

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

out sum
```

### String Operations

| Function | Signature | Description |
|----------|-----------|-------------|
| `concat` | `(a: String, b: String) -> String` | Concatenate two strings |
| `upper` | `(value: String) -> String` | Convert to uppercase |
| `lower` | `(value: String) -> String` | Convert to lowercase |
| `string-length` | `(value: String) -> Int` | Get string length |

**Examples:**

```
in firstName: String
in lastName: String

fullName = concat(firstName, lastName)
upperName = upper(fullName)
lowerName = lower(fullName)
nameLength = string-length(fullName)

out upperName
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

**Examples:**

```
in numbers: List<Int>

count = list-length(numbers)
first = list-first(numbers)
last = list-last(numbers)
isEmpty = list-is-empty(numbers)

out count
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

### Constant Functions

| Function | Signature | Description |
|----------|-----------|-------------|
| `const-int` | `(value: Int) -> Int` | Return constant integer |
| `const-float` | `(value: Float) -> Float` | Return constant float |
| `const-string` | `(value: String) -> String` | Return constant string |
| `const-bool` | `(value: Boolean) -> Boolean` | Return constant boolean |

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

# Create "LASTNAME, Firstname"
upper_last = upper(lastName)
separator = ", "
temp = concat(upper_last, separator)
fullName = concat(temp, firstName)

out fullName
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
| `concat` | `stdlib.concat` |
| `upper` | `stdlib.upper` |
| `lower` | `stdlib.lower` |
| `string-length` | `stdlib.string-length` |
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
| `identity` | `stdlib.identity` |
| `log` | `stdlib.log` |
| `const-int` | `stdlib.const-int` |
| `const-float` | `stdlib.const-float` |
| `const-string` | `stdlib.const-string` |
| `const-bool` | `stdlib.const-bool` |

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
