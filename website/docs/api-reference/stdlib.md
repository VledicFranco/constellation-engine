---
title: "Standard Library"
sidebar_position: 3
description: "Built-in functions available in every pipeline"
---

# Standard Library Reference

The constellation-lang standard library provides commonly-used functions for pipeline orchestration.

:::tip Most Commonly Used Functions
The functions you will use most often are: `add`, `concat`, `gt`/`lt` for comparisons, `and`/`or`/`not` for boolean logic, and `list-length`/`list-first` for collections. Start with these before exploring the full library.
:::

## Finding the Right Function

### By Task

| I want to... | Function | Example |
|--------------|----------|---------|
| Transform text | `Uppercase`, `Lowercase`, `Trim` | `Uppercase("hello")` → `"HELLO"` |
| Work with numbers | `Add`, `Multiply`, `Max`, `Min` | `Add(1, 2)` → `3` |
| Process lists | `Map`, `Filter`, `Reduce`, `Length` | `Length([1,2,3])` → `3` |
| Format strings | `Concat`, `Format` | `Concat("a", "b")` → `"ab"` |
| Handle nulls | `Coalesce`, `IsEmpty` | `Coalesce(null, "default")` |
| Compare values | `Equals`, `GreaterThan`, `Contains` | `Contains("abc", "b")` → `true` |

### By Category

The standard library is organized into categories:

- **String functions** — Text manipulation and formatting
- **Numeric functions** — Arithmetic and math operations
- **List functions** — Collection processing with higher-order functions
- **Logic functions** — Boolean operations and comparisons
- **Utility functions** — Type conversion and null handling

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

---

## Function Reference

:::note Type Signature Convention
Type signatures use arrow notation: `(param1: Type1, param2: Type2) -> ReturnType`. Generic types like `List<Int>` indicate the element type. All functions are pure unless noted otherwise.
:::

### Math Operations

| Function | Type Signature | Description |
|----------|----------------|-------------|
| `add` | `(a: Int, b: Int) -> Int` | Add two integers |
| `subtract` | `(a: Int, b: Int) -> Int` | Subtract b from a |
| `multiply` | `(a: Int, b: Int) -> Int` | Multiply two integers |
| `divide` | `(a: Int, b: Int) -> Int` | Integer division |
| `max` | `(a: Int, b: Int) -> Int` | Maximum of two integers |
| `min` | `(a: Int, b: Int) -> Int` | Minimum of two integers |
| `abs` | `(value: Int) -> Int` | Absolute value |
| `modulo` | `(a: Int, b: Int) -> Int` | Remainder after division |
| `round` | `(value: Float) -> Int` | Round float to nearest integer |
| `negate` | `(value: Int) -> Int` | Negate a number |

#### Error Behavior

| Function | Error Condition | Behavior |
|----------|-----------------|----------|
| `divide` | `b = 0` | **Raises `ArithmeticException`** with message "Division by zero in stdlib.divide" |
| `modulo` | `b = 0` | **Raises `ArithmeticException`** with message "Division by zero in stdlib.modulo" |

#### Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `add` | `add(0, 0)` | `0` |
| `add` | Large numbers near `Long.MaxValue` | May overflow (no bounds checking) |
| `subtract` | `subtract(-5, -3)` | `-2` |
| `multiply` | `multiply(x, 0)` | `0` |
| `multiply` | `multiply(-4, -3)` | `12` |
| `divide` | `divide(0, 5)` | `0` |
| `divide` | `divide(7, 3)` | `2` (truncates toward zero) |
| `divide` | `divide(-10, 3)` | `-3` (truncates toward zero) |
| `max` | `max(5, 5)` | `5` |
| `max` | `max(-10, -5)` | `-5` |
| `min` | `min(5, 5)` | `5` |
| `min` | `min(-10, -5)` | `-10` |
| `abs` | `abs(0)` | `0` |
| `abs` | `abs(-5)` | `5` |
| `abs` | `abs(Long.MinValue)` | Undefined (overflow) |
| `modulo` | `modulo(6, 3)` | `0` (exact divisibility) |
| `negate` | `negate(0)` | `0` |
| `negate` | `negate(-5)` | `5` |
| `round` | `round(3.5)` | `4` (rounds half up) |
| `round` | `round(-2.7)` | `-3` |

#### Performance Notes

All math operations are **O(1)** pure functions with no memory allocation beyond the result.

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

**Safe Division Pattern:**

```
in numerator: Int
in denominator: Int

# Check for zero before dividing to avoid runtime errors
isZero = eq-int(denominator, 0)
safeResult = if (isZero) 0 else divide(numerator, denominator)

out safeResult
```

---

### String Operations

| Function | Type Signature | Description |
|----------|----------------|-------------|
| `concat` | `(a: String, b: String) -> String` | Concatenate two strings |
| `string-length` | `(value: String) -> Int` | Get string length (character count) |
| `join` | `(list: List<String>, separator: String) -> String` | Join strings with delimiter |
| `split` | `(value: String, substring: String) -> List<String>` | Split string by delimiter |
| `contains` | `(value: String, substring: String) -> Boolean` | Check if string contains substring |
| `trim` | `(value: String) -> String` | Trim leading and trailing whitespace |
| `replace` | `(value: String, target: String, replacement: String) -> String` | Replace all occurrences |

#### Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `concat` | `concat("", "")` | `""` |
| `concat` | `concat("", "hello")` | `"hello"` |
| `concat` | Unicode strings | Fully supported |
| `string-length` | `string-length("")` | `0` |
| `string-length` | Unicode (e.g., Chinese) | Character count, not byte count |
| `string-length` | `string-length("hello world")` | `11` (includes space) |
| `join` | Empty list | `""` |
| `join` | Single element list | Element without separator |
| `split` | Empty delimiter | Returns original string in single-element list |
| `split` | Delimiter not found | Returns original string in single-element list |
| `split` | Consecutive delimiters | Includes empty strings in result |
| `contains` | `contains("hello", "")` | `true` (empty string is always contained) |
| `contains` | `contains("", "x")` | `false` |
| `trim` | `trim("")` | `""` |
| `trim` | `trim("   ")` | `""` (whitespace-only becomes empty) |
| `trim` | `trim("  hello  ")` | `"hello"` |
| `replace` | No match found | Returns original string unchanged |
| `replace` | `replace("aaa", "a", "b")` | `"bbb"` (replaces all occurrences) |

#### Error Guarantees

All string operations are **safe** and never raise exceptions. They handle empty strings and Unicode gracefully.

#### Performance Notes

- `concat`: O(n+m) where n and m are string lengths
- `string-length`: O(1)
- `join`: O(total length of all strings)
- `split`: O(n) where n is string length; uses regex quoting internally for safe literal matching
- `contains`: O(n*m) worst case
- `trim`: O(n)
- `replace`: O(n*m) worst case, creates new string

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

**Joining a List of Strings:**

```
in words: List<String>
in separator: String

sentence = join(words, separator)

out sentence
```

**Splitting a String:**

```
in csv: String

# Split CSV line by comma
fields = split(csv, ",")

out fields
```

---

### Boolean Operations

| Function | Type Signature | Description |
|----------|----------------|-------------|
| `and` | `(a: Boolean, b: Boolean) -> Boolean` | Logical AND |
| `or` | `(a: Boolean, b: Boolean) -> Boolean` | Logical OR |
| `not` | `(value: Boolean) -> Boolean` | Logical NOT |

#### Truth Tables

**`and(a, b)`:**
| a | b | Result |
|---|---|--------|
| `true` | `true` | `true` |
| `true` | `false` | `false` |
| `false` | `true` | `false` |
| `false` | `false` | `false` |

**`or(a, b)`:**
| a | b | Result |
|---|---|--------|
| `true` | `true` | `true` |
| `true` | `false` | `true` |
| `false` | `true` | `true` |
| `false` | `false` | `false` |

**`not(value)`:**
| value | Result |
|-------|--------|
| `true` | `false` |
| `false` | `true` |

#### Error Guarantees

Boolean operations are **always safe** and cannot fail.

#### Performance Notes

All boolean operations are **O(1)** constant time, pure functions.

**Examples:**

```
in isActive: Boolean
in isVerified: Boolean

canProceed = and(isActive, isVerified)
hasAccess = or(isActive, isVerified)
isInactive = not(isActive)

out canProceed
```

---

### Comparison Operations

| Function | Type Signature | Description |
|----------|----------------|-------------|
| `eq-int` | `(a: Int, b: Int) -> Boolean` | Check if integers are equal |
| `eq-string` | `(a: String, b: String) -> Boolean` | Check if strings are equal |
| `gt` | `(a: Int, b: Int) -> Boolean` | Check if a > b |
| `lt` | `(a: Int, b: Int) -> Boolean` | Check if a < b |
| `gte` | `(a: Int, b: Int) -> Boolean` | Check if a >= b |
| `lte` | `(a: Int, b: Int) -> Boolean` | Check if a <= b |

#### Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `eq-int` | `eq-int(0, 0)` | `true` |
| `eq-int` | `eq-int(-5, -5)` | `true` |
| `eq-string` | `eq-string("", "")` | `true` |
| `eq-string` | `eq-string("Hello", "hello")` | `false` (case-sensitive) |
| `eq-string` | Unicode strings | Fully supported |
| `gt` | `gt(5, 5)` | `false` |
| `gt` | `gt(-5, -10)` | `true` |
| `lt` | `lt(5, 5)` | `false` |
| `lt` | `lt(-10, -5)` | `true` |
| `gte` | `gte(5, 5)` | `true` |
| `lte` | `lte(5, 5)` | `true` |

#### Error Guarantees

Comparison operations are **always safe** and cannot fail.

#### Performance Notes

All comparison operations are **O(1)** for integers. String equality is **O(min(n,m))** where n and m are string lengths.

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

---

### List Operations

| Function | Type Signature | Description |
|----------|----------------|-------------|
| `list-length` | `(list: List<Int>) -> Int` | Get list length |
| `list-first` | `(list: List<Int>) -> Int` | Get first element |
| `list-last` | `(list: List<Int>) -> Int` | Get last element |
| `list-is-empty` | `(list: List<Int>) -> Boolean` | Check if list is empty |
| `list-sum` | `(list: List<Int>) -> Int` | Sum all elements |
| `list-concat` | `(a: List<Int>, b: List<Int>) -> List<Int>` | Concatenate two lists |
| `list-contains` | `(list: List<Int>, value: Int) -> Boolean` | Check if element exists |
| `list-reverse` | `(list: List<Int>) -> List<Int>` | Reverse list order |

#### Error Behavior

| Function | Error Condition | Behavior |
|----------|-----------------|----------|
| `list-first` | Empty list | **Raises `NoSuchElementException`** with message "stdlib.list-first: list is empty" |
| `list-last` | Empty list | **Raises `NoSuchElementException`** with message "stdlib.list-last: list is empty" |

#### Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `list-length` | Empty list | `0` |
| `list-length` | Single element | `1` |
| `list-is-empty` | Empty list | `true` |
| `list-is-empty` | Non-empty list | `false` |
| `list-sum` | Empty list | `0` (identity for addition) |
| `list-sum` | Negative numbers | Summed correctly |
| `list-contains` | Empty list | `false` |
| `list-reverse` | Empty list | `[]` (empty list) |
| `list-reverse` | Single element | Same list |
| `list-concat` | Both empty | `[]` |
| `list-concat` | First empty | Returns second list |
| `list-concat` | Second empty | Returns first list |

#### Safe List Access Pattern

```
in numbers: List<Int>

# Always check before accessing first/last
isEmpty = list-is-empty(numbers)
safeFirst = if (isEmpty) 0 else list-first(numbers)

out safeFirst
```

#### Performance Notes

| Function | Time Complexity | Space Complexity |
|----------|-----------------|------------------|
| `list-length` | O(1) | O(1) |
| `list-first` | O(1) | O(1) |
| `list-last` | O(1) | O(1) |
| `list-is-empty` | O(1) | O(1) |
| `list-sum` | O(n) | O(1) |
| `list-concat` | O(n+m) | O(n+m) |
| `list-contains` | O(n) | O(1) |
| `list-reverse` | O(n) | O(n) |

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

**Concatenating Lists:**

```
in listA: List<Int>
in listB: List<Int>

combined = list-concat(listA, listB)

out combined
```

---

### Type Conversion Operations

| Function | Type Signature | Description |
|----------|----------------|-------------|
| `to-string` | `(value: Int) -> String` | Convert integer to string |
| `to-int` | `(value: Float) -> Int` | Truncate float to integer |
| `to-float` | `(value: Int) -> Float` | Convert integer to float |

#### Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `to-string` | `to-string(0)` | `"0"` |
| `to-string` | `to-string(-7)` | `"-7"` |
| `to-int` | `to-int(3.7)` | `3` (truncates toward zero) |
| `to-int` | `to-int(-3.7)` | `-3` (truncates toward zero) |
| `to-int` | `to-int(0.0)` | `0` |
| `to-float` | `to-float(0)` | `0.0` |
| `to-float` | `to-float(-7)` | `-7.0` |

#### Error Guarantees

Type conversion operations are **always safe** and cannot fail.

**Note:** `to-int` truncates toward zero (not floor), so `-3.7` becomes `-3`, not `-4`.

#### Performance Notes

All type conversions are **O(1)** except `to-string` which is O(log n) where n is the magnitude of the integer.

**Examples:**

```
in count: Int
in measurement: Float

label = to-string(count)
truncated = to-int(measurement)
precise = to-float(count)

out label
```

---

### Higher-Order Functions

:::tip
Higher-order functions like `filter`, `map`, `all`, and `any` are processed specially by the compiler and use `InlineTransform` at runtime. They short-circuit when possible (e.g., `any` stops on first `true`).
:::

| Function | Type Signature | Description |
|----------|----------------|-------------|
| `filter` | `(List<Int>, (Int) => Boolean) -> List<Int>` | Keep elements matching predicate |
| `map` | `(List<Int>, (Int) => Int) -> List<Int>` | Transform each element |
| `all` | `(List<Int>, (Int) => Boolean) -> Boolean` | Check all elements match |
| `any` | `(List<Int>, (Int) => Boolean) -> Boolean` | Check any element matches |

#### Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `filter` | Empty list | `[]` |
| `filter` | No matches | `[]` |
| `map` | Empty list | `[]` |
| `all` | Empty list | `true` (vacuous truth) |
| `any` | Empty list | `false` |

#### Implementation Notes

Higher-order functions are processed specially by the compiler. They use `InlineTransform` at runtime (e.g., `FilterTransform`, `MapTransform`) rather than traditional Module implementations.

#### Performance Notes

| Function | Time Complexity | Space Complexity |
|----------|-----------------|------------------|
| `filter` | O(n) | O(k) where k = matching elements |
| `map` | O(n) | O(n) |
| `all` | O(n) worst case, short-circuits on first `false` | O(1) |
| `any` | O(n) worst case, short-circuits on first `true` | O(1) |

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

**Chaining Higher-Order Functions:**

```
use stdlib.collection
use stdlib.compare
use stdlib.math

in numbers: List<Int>

# Filter then map: get absolute values of negative numbers
negatives = filter(numbers, (x) => lt(x, 0))
absolutes = map(negatives, (x) => abs(x))

out absolutes
```

---

### Utility Operations

| Function | Type Signature | Description |
|----------|----------------|-------------|
| `identity` | `(value: String) -> String` | Pass-through (returns input unchanged) |
| `log` | `(message: String) -> String` | Log message and pass through |

#### Edge Cases

| Function | Edge Case | Result |
|----------|-----------|--------|
| `identity` | Empty string | `""` |
| `identity` | Unicode | Passed through unchanged |
| `log` | Empty message | Logs empty line, returns `""` |

#### Error Guarantees

Utility operations are **always safe** and cannot fail.

#### Implementation Notes

- `identity` is a pure function with no side effects
- `log` prints to stdout with prefix `[constellation-lang]` and returns the input unchanged

**Examples:**

```
in message: String

# Log for debugging
logged = log(message)

# Identity (useful for type coercion or pipeline passthrough)
same = identity(message)

out logged
```

---

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

### Safe Division

```
in numerator: Int
in denominator: Int

# Avoid division by zero
isZero = eq-int(denominator, 0)
safeQuotient = if (isZero) 0 else divide(numerator, denominator)

out safeQuotient
```

### Safe List Access

```
in numbers: List<Int>

# Get first element or default to 0
isEmpty = list-is-empty(numbers)
firstOrDefault = if (isEmpty) 0 else list-first(numbers)

out firstOrDefault
```

### Data Aggregation

```
in values: List<Int>

count = list-length(values)
total = list-sum(values)

# Note: For average, you'd need float division which requires
# converting total to float first
totalFloat = to-float(total)
countFloat = to-float(count)

out total
```

---

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

---

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

---

## Error Handling Summary

### Functions That Can Raise Errors

| Function | Exception Type | Condition | Message |
|----------|----------------|-----------|---------|
| `divide` | `ArithmeticException` | Divisor is zero | "Division by zero in stdlib.divide" |
| `modulo` | `ArithmeticException` | Divisor is zero | "Division by zero in stdlib.modulo" |
| `list-first` | `NoSuchElementException` | List is empty | "stdlib.list-first: list is empty" |
| `list-last` | `NoSuchElementException` | List is empty | "stdlib.list-last: list is empty" |

### Functions That Are Always Safe

All other functions are **guaranteed not to raise exceptions** when given valid typed inputs:

- All math functions except `divide` and `modulo`
- All string functions
- All boolean functions
- All comparison functions
- All list functions except `list-first` and `list-last`
- All type conversion functions
- All higher-order functions
- All utility functions

---

## Type System Notes

### Integer Type

Integers in constellation-lang use `Long` (64-bit signed) internally:
- Range: `-9,223,372,036,854,775,808` to `9,223,372,036,854,775,807`
- Overflow behavior: Standard JVM wraparound (no automatic detection)

### Float Type

Floats use `Double` (64-bit IEEE 754) internally:
- Full double-precision floating-point support
- Special values: `NaN`, `Infinity`, `-Infinity` are supported

### String Type

Strings are Java `String` objects:
- Full Unicode support (UTF-16 internally)
- `string-length` returns character count (Unicode code units), not byte count

### List Type

Lists use immutable `Vector[CValue]` internally:
- Efficient random access: O(log n)
- Efficient append/prepend: O(log n)
- Memory efficient for large lists

---

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

### Best Practices for Custom Functions

1. **Use descriptive names** - Function names should clearly indicate purpose
2. **Document edge cases** - Specify behavior for empty inputs, zeros, etc.
3. **Prefer pure implementations** - Use `implementationPure` when possible
4. **Handle errors gracefully** - Raise meaningful exceptions with clear messages
5. **Follow naming conventions** - Use lowercase-with-hyphens for function names
