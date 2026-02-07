---
title: "Declarations"
sidebar_position: 4
description: "Define types, inputs, variables, outputs, and imports in constellation-lang pipelines with declaration syntax."
---

# Declarations

## Quick Reference

| Declaration | Syntax | Purpose |
|-------------|--------|---------|
| Input | `in name: Type` | Declare pipeline input |
| Output | `out expr` | Declare pipeline output |
| Variable | `name = expr` | Bind expression to name |
| Type alias | `type Name = Type` | Create type shorthand |
| Import | `use namespace.path` | Import module namespace |
| Annotation | `@example(value)` | Attach metadata to input |

Constellation-lang pipelines consist of five types of declarations: type definitions, input declarations, variable assignments, output declarations, and use declarations. Each pipeline must have at least one output declaration.

## Declaration Order

Declarations can appear in any order within a pipeline, with one constraint: all pipelines must have at least one output declaration. The typical ordering is:

```constellation
# 1. Use declarations (imports)
use stdlib.math as m

# 2. Type definitions
type MyType = { field: String }

# 3. Input declarations
in data: MyType

# 4. Variable assignments
processed = Transform(data)

# 5. Output declaration (required)
out processed
```

---

## Type Definitions

Type definitions create named types that can be referenced elsewhere in the pipeline. They help improve readability and enable type reuse.

### Syntax

```
type TypeName = TypeExpression
```

### Examples

**Record type:**
```constellation
type User = { id: String, name: String, email: String }
```

**Nested record type:**
```constellation
type Order = {
  id: String,
  customer: { name: String, address: String },
  items: List<{ sku: String, quantity: Int }>
}
```

**Parameterized type alias:**
```constellation
type UserList = List<User>
type Candidates = List<{ id: String, score: Float }>
```

**Type merge (type algebra):**
```constellation
type Base = { id: String, createdAt: String }
type UserData = { name: String, email: String }
type User = Base + UserData  # { id: String, createdAt: String, name: String, email: String }
```

**Union type:**
```constellation
type Result = { value: Int } | { error: String }
type StringOrInt = String | Int
```

### Type Expression Reference

| Type Expression | Description | Example |
|-----------------|-------------|---------|
| `String` | Text value | `in name: String` |
| `Int` | Integer number | `in count: Int` |
| `Float` | Floating-point number | `in price: Float` |
| `Boolean` | True/false value | `in active: Boolean` |
| `{ field: Type, ... }` | Record with named fields | `{ id: String, score: Float }` |
| `List<T>` | Ordered collection | `List<String>` |
| `Map<K, V>` | Key-value mapping | `Map<String, Int>` |
| `Optional<T>` | Value that may be absent | `Optional<String>` |
| `Candidates<T>` | Legacy alias for `List<T>` | `Candidates<{ id: String }>` |
| `A + B` | Type merge (record combination) | `Base + Extended` |
| `A \| B` | Union type | `Success \| Error` |
| `TypeRef` | Reference to defined type | `User` |

---

## Input Declarations

Input declarations define the external data that must be provided when executing the pipeline. Inputs are the entry points for data into the pipeline.

### Syntax

```
in variableName: TypeExpression
```

With annotations:
```
@annotation(value)
in variableName: TypeExpression
```

### Examples

**Basic inputs:**
```constellation
in userId: Int
in query: String
in enabled: Boolean
in price: Float
```

**Record inputs:**
```constellation
in user: { name: String, age: Int }
in config: { timeout: Int, retries: Int, debug: Boolean }
```

**Collection inputs:**
```constellation
in items: List<{ id: String, score: Float }>
in tags: List<String>
in metadata: Map<String, String>
```

**Type reference inputs:**
```constellation
type Communication = { id: String, channel: String, content: String }
in communications: List<Communication>
```

---

## Input Annotations

Annotations provide metadata for input declarations. They appear on the line(s) before the `in` declaration and affect tooling behavior without changing runtime semantics.

### @example Annotation

The `@example` annotation specifies an example value for an input. This value is:
- Used by the VSCode extension to pre-populate the run widget
- Type-checked against the declared input type at compile time
- Included in the LSP `getInputSchema` response for tooling integration

**Syntax:**
```
@example(expression)
in variableName: Type
```

**Supported Example Values:**

| Type | Example Syntax |
|------|----------------|
| String | `@example("hello world")` |
| Int | `@example(42)` or `@example(-100)` |
| Float | `@example(3.14)` |
| Boolean | `@example(true)` or `@example(false)` |
| List | `@example([1, 2, 3])` or `@example(["a", "b"])` |
| Empty List | `@example([])` |

**Literal examples:**
```constellation
# String input with example
@example("hello world")
in text: String

# Integer input with example
@example(42)
in count: Int

# Negative integer
@example(-100)
in offset: Int

# Float input with example
@example(3.14)
in ratio: Float

# Boolean input with example
@example(true)
in enabled: Boolean
```

**List literal examples:**
```constellation
@example([1, 2, 3])
in numbers: List<Int>

@example(["Alice", "Bob", "Charlie"])
in names: List<String>

@example([true, false, true])
in flags: List<Boolean>

@example([])
in items: List<String>
```

**Expression examples:**

The `@example` annotation supports any valid expression, including variable references and function calls:

```constellation
# Variable reference
@example(defaultValue)
in data: Int

# Qualified variable reference (field access)
@example(config.defaultTimeout)
in timeout: Int

# Function call
@example(createDefault(5))
in data: Int

# Qualified function call
@example(stdlib.examples.createUser())
in user: { name: String }

# Arithmetic expression
@example(1 + 2)
in value: Int

# Conditional expression
@example(if (true) 1 else 2)
in value: Int

# String interpolation
@example("Hello, ${name}!")
in greeting: String
```

**Multiple annotations:**

An input can have multiple `@example` annotations, useful for documenting various valid input values:

```constellation
@example(10)
@example(20)
@example(30)
in value: Int
```

**Multiple inputs with examples:**
```constellation
@example("Alice")
in name: String

@example(30)
in age: Int

@example(true)
in active: Boolean

out name
```

**Inputs without examples:**

The `@example` annotation is optional. Inputs without examples still work normally:

```constellation
# Some inputs have examples, some don't
@example("hello")
in greeting: String

in count: Int          # No example - user must provide value

@example(true)
in verbose: Boolean

out greeting
```

**Type checking:**

The example value is type-checked against the declared input type. Mismatches produce compile errors:

```constellation
# Error: @example type mismatch: expected String, got Int
@example(42)
in text: String
```

```constellation
# Error: @example type mismatch: expected Int, got String
@example("hello")
in count: Int
```

**Record literal examples:**

Record literals enable `@example` annotations for record and union type inputs:

```constellation
# Record input with example
@example({ name: "Alice", age: 30 })
in user: { name: String, age: Int }

# Union type input with example (uses one variant)
type ApiResult = { value: Int, status: String } | { error: String, code: Int }

@example({ value: 42, status: "ok" })
in response: ApiResult
```

**Limitations:**

- Variable references and function calls in `@example` are parsed but not converted to JSON for the LSP

---

## Variable Assignments

Assignments bind the result of an expression to a variable name. Variables can then be used in subsequent expressions.

### Syntax

```
variableName = expression
```

### Examples

**Simple assignments:**
```constellation
result = process(input)
upper = Uppercase(text)
count = WordCount(document)
```

**Chained assignments:**
```constellation
in text: String
cleaned = Trim(text)
upper = Uppercase(cleaned)
result = WordCount(upper)
out result
```

**Expression assignments:**

Assignments can use any valid expression:

```constellation
# Function calls
embeddings = EmbedModel(communications)

# Arithmetic
total = price * quantity
discounted = total - discount

# Merge (type algebra)
enriched = items + context

# Projection
selected = data[id, name, score]

# Field access
userId = user.id

# Conditional
status = if (score > 0.5) "pass" else "fail"

# Branch expression
grade = branch {
  score >= 90 -> "A",
  score >= 80 -> "B",
  score >= 70 -> "C",
  otherwise -> "F"
}

# Guard expression
filtered = expensiveOp(data) when condition

# Coalesce
value = maybeValue ?? defaultValue
```

**Module call with options:**
```constellation
result = FetchData(url) with retry: 3, timeout: 10s
cached = LoadProfile(userId) with cache: 15min
```

See [Module Options](./module-options.md) for full details on `with` clause options.

---

## Output Declarations

Output declarations specify which variable or expression represents the pipeline's final result. Every pipeline must have at least one output declaration.

### Syntax

```
out variableName
```

### Examples

**Simple output:**
```constellation
in text: String
result = Uppercase(text)
out result
```

**Expression as output:**

The output must reference a variable name (not an arbitrary expression):

```constellation
in items: List<{ id: String, score: Float }>
projected = items[id, score]
out projected
```

**Multiple outputs:**

Pipelines can have multiple output declarations:

```constellation
in text: String
upper = Uppercase(text)
lower = Lowercase(text)
count = WordCount(text)

out upper
out lower
out count
```

---

## Use Declarations (Namespace Imports)

Use declarations import modules from namespaces, enabling shorter references to functions defined in those namespaces.

### Syntax

```
use qualified.namespace.path
use qualified.namespace.path as alias
```

### Examples

**Basic import:**
```constellation
use stdlib.math
result = math.add(a, b)
```

**Import with alias:**
```constellation
use stdlib.math as m
result = m.add(a, b)
```

**Multiple imports:**
```constellation
use stdlib.math as m
use stdlib.text as t

in text: String
in x: Int
in y: Int

trimmed = t.trim(text)
sum = m.add(x, y)

out trimmed
```

**Qualified function calls without import:**

You can also call namespaced functions directly without a `use` declaration:

```constellation
result = stdlib.math.add(x, y)
```

---

## Module Call Options (`with` Clause)

When calling modules, you can specify execution options using the `with` clause. These options control retry behavior, caching, timeouts, and other execution policies.

### Syntax

```
result = ModuleName(args) with option1: value1, option2: value2
```

### Quick Reference

| Option | Type | Description |
|--------|------|-------------|
| `retry` | Integer | Maximum retry attempts on failure |
| `timeout` | Duration | Maximum execution time per attempt |
| `delay` | Duration | Base delay between retries |
| `backoff` | Strategy | How delay increases between retries |
| `fallback` | Expression | Value to use if all retries fail |
| `cache` | Duration | TTL for caching results |
| `cache_backend` | String | Named cache backend to use |
| `throttle` | Rate | Maximum call rate limit |
| `concurrency` | Integer | Maximum parallel executions |
| `on_error` | Strategy | Error handling behavior |
| `lazy` | Boolean | Defer execution until needed |
| `priority` | Level | Scheduling priority hint |

### Duration Values

Time values with unit suffix:

| Unit | Suffix | Example |
|------|--------|---------|
| Milliseconds | `ms` | `500ms` |
| Seconds | `s` | `30s` |
| Minutes | `min` | `5min` |
| Hours | `h` | `1h` |
| Days | `d` | `1d` |

### Rate Values

Request rate in format `count/duration`:

```constellation
throttle: 100/1min    # 100 calls per minute
throttle: 10/1s       # 10 calls per second
```

### Strategy Values

**Backoff strategies:**
- `fixed` - Constant delay between retries
- `linear` - Delay increases linearly (N x base delay)
- `exponential` - Delay doubles each retry (capped at 30s)

**Error strategies:**
- `propagate` - Re-throw the error (default)
- `skip` - Return zero value for the type
- `log` - Log error and return zero value
- `wrap` - Wrap error in result type

**Priority levels:**
- `critical` - Highest priority
- `high` - Above normal
- `normal` - Default priority
- `low` - Below normal
- `background` - Lowest priority

### Examples

**Retry with timeout:**
```constellation
response = HttpGet(url) with retry: 3, timeout: 10s
```

**Exponential backoff:**
```constellation
result = FetchData(endpoint) with
    retry: 5,
    delay: 1s,
    backoff: exponential,
    timeout: 30s
```

**Caching:**
```constellation
profile = LoadUserProfile(userId) with cache: 15min
```

**Fallback value:**
```constellation
price = GetStockPrice(symbol) with
    retry: 2,
    timeout: 5s,
    fallback: 0.0
```

**Rate limiting:**
```constellation
result = ProcessRequest(req) with throttle: 100/1min
```

**Priority scheduling:**
```constellation
critical = ProcessUrgent(data) with priority: critical
background = GenerateReport(data) with priority: background
```

**Lazy evaluation:**
```constellation
deferred = ExpensiveOperation(data) with lazy
```

See [Module Options](./module-options.md) for comprehensive documentation of all options.

---

## Complete Pipeline Example

Here is a complete example demonstrating all declaration types:

```constellation
# Use declarations
use stdlib.text as t

# Type definitions
type Communication = {
  id: String,
  content: String,
  channel: String
}

type ScoredItem = {
  id: String,
  score: Float
}

# Input declarations with annotations
@example([])
in communications: List<Communication>

@example(12345)
in userId: Int

@example("email")
in filterChannel: String

# Variable assignments
cleaned = communications.map(c => t.trim(c.content))
filtered = communications.filter(c => c.channel == filterChannel)
embeddings = EmbedModel(filtered) with cache: 10min
scores = RankingModel(embeddings, userId) with retry: 3, timeout: 30s

# Combine data using type algebra
result = filtered[id, channel] + scores

# Output declaration
out result
```

---

## See Also

- [Types](./types.md) - Type system reference
- [Expressions](./expressions.md) - Expression syntax
- [Type Algebra](./type-algebra.md) - Merge operators
- [Module Options](./module-options.md) - `with` clause options
- [Pipeline Structure](./pipeline-structure.md) - Pipeline overview
