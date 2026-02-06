# Standard Library Component

> **Path**: `docs/components/stdlib/`
> **Parent**: [components/](../README.md)
> **Module**: `modules/lang-stdlib/`

Built-in functions available in all constellation-lang pipelines.

## Key Files

| File | Purpose |
|------|---------|
| `StdLib.scala` | Main entry point, aggregates all categories |
| `categories/MathFunctions.scala` | Arithmetic operations |
| `categories/StringFunctions.scala` | String manipulation |
| `categories/ListFunctions.scala` | List operations |
| `categories/BooleanFunctions.scala` | Logical operations |
| `categories/ComparisonFunctions.scala` | Equality and ordering |
| `categories/UtilityFunctions.scala` | General utilities |
| `categories/HigherOrderFunctions.scala` | Lambda-based operations |
| `categories/TypeConversionFunctions.scala` | Type conversions |

## Role in the System

The stdlib provides the foundational function library:

```
                    ┌─────────────┐
                    │    core     │
                    └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  │
   [runtime]         [lang-compiler]          │
        │                  │                  │
        │                  ▼                  │
        │           [lang-stdlib] ◄───────────┘
        │                  │
        └──────────────────┼──────────────────┐
                           │                  │
                           ▼                  ▼
                      [http-api]        [example-app]
```

The stdlib:
1. Registers `FunctionSignature`s with the compiler for type checking
2. Provides `Module.Uninitialized` implementations for runtime execution
3. Is automatically included in `LangCompiler` via `StdLib.compiler`

## Architecture

### Module Pattern

Each stdlib function follows this pattern:

```scala
// 1. Define input/output case classes
case class TwoInts(a: Long, b: Long)
case class MathIntOut(out: Long)

// 2. Create Module.Uninitialized using ModuleBuilder
val addModule: Module.Uninitialized = ModuleBuilder
  .metadata("stdlib.add", "Add two integers", 1, 0)
  .tags("stdlib", "math")
  .implementationPure[TwoInts, MathIntOut](in => MathIntOut(in.a + in.b))
  .build

// 3. Define FunctionSignature for type checking
val addSignature: FunctionSignature = FunctionSignature(
  name = "add",
  params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
  returns = SemanticType.SInt,
  moduleName = "stdlib.add",
  namespace = Some("stdlib.math")
)
```

### Category Traits

Each category is a trait that provides:
- Module definitions (`xxxModule`)
- Function signatures (`xxxSignature`)
- Collection accessors (`xxxModules`, `xxxSignatures`)

```scala
trait MathFunctions {
  val addModule: Module.Uninitialized
  val addSignature: FunctionSignature
  def mathModules: Map[String, Module.Uninitialized]
  def mathSignatures: List[FunctionSignature]
}
```

### StdLib Object

The main `StdLib` object mixes in all category traits:

```scala
object StdLib
    extends MathFunctions
    with StringFunctions
    with ListFunctions
    with BooleanFunctions
    with ComparisonFunctions
    with UtilityFunctions
    with HigherOrderFunctions
    with TypeConversionFunctions {

  def allModules: Map[String, Module.Uninitialized]
  def allSignatures: List[FunctionSignature]
  def registerAll(builder: LangCompilerBuilder): LangCompilerBuilder
  def compiler: LangCompiler  // Pre-configured compiler with all stdlib
}
```

## Function Categories

### Math (10 functions)

| Function | Signature | Implementation |
|----------|-----------|----------------|
| `add` | `(Int, Int) -> Int` | `a + b` |
| `subtract` | `(Int, Int) -> Int` | `a - b` |
| `multiply` | `(Int, Int) -> Int` | `a * b` |
| `divide` | `(Int, Int) -> Int` | `a / b` (error on zero) |
| `max` | `(Int, Int) -> Int` | `Math.max(a, b)` |
| `min` | `(Int, Int) -> Int` | `Math.min(a, b)` |
| `abs` | `(Int) -> Int` | `Math.abs(value)` |
| `modulo` | `(Int, Int) -> Int` | `a % b` (error on zero) |
| `round` | `(Float) -> Int` | `Math.round(value)` |
| `negate` | `(Int) -> Int` | `-value` |

### String (7 functions)

| Function | Signature | Implementation |
|----------|-----------|----------------|
| `concat` | `(String, String) -> String` | `a + b` |
| `string-length` | `(String) -> Int` | `s.length` |
| `join` | `(List<String>, String) -> String` | `items.mkString(delimiter)` |
| `split` | `(String, String) -> List<String>` | `s.split(delimiter)` |
| `contains` | `(String, String) -> Boolean` | `s.contains(sub)` |
| `trim` | `(String) -> String` | `s.trim` |
| `replace` | `(String, String, String) -> String` | `s.replace(old, new)` |

### List (8 functions)

| Function | Signature | Implementation |
|----------|-----------|----------------|
| `list-length` | `(List<A>) -> Int` | `list.size` |
| `list-first` | `(List<A>) -> A` | `list.head` (error if empty) |
| `list-last` | `(List<A>) -> A` | `list.last` (error if empty) |
| `list-is-empty` | `(List<A>) -> Boolean` | `list.isEmpty` |
| `list-sum` | `(List<Int>) -> Int` | `list.sum` |
| `list-concat` | `(List<A>, List<A>) -> List<A>` | `a ++ b` |
| `list-contains` | `(List<A>, A) -> Boolean` | `list.contains(elem)` |
| `list-reverse` | `(List<A>) -> List<A>` | `list.reverse` |

### Boolean (3 functions)

| Function | Signature | Implementation |
|----------|-----------|----------------|
| `and` | `(Boolean, Boolean) -> Boolean` | `a && b` |
| `or` | `(Boolean, Boolean) -> Boolean` | `a \|\| b` |
| `not` | `(Boolean) -> Boolean` | `!value` |

### Comparison (6 functions)

| Function | Signature | Implementation |
|----------|-----------|----------------|
| `eq-int` | `(Int, Int) -> Boolean` | `a == b` |
| `eq-string` | `(String, String) -> Boolean` | `a == b` |
| `gt` | `(Int, Int) -> Boolean` | `a > b` |
| `lt` | `(Int, Int) -> Boolean` | `a < b` |
| `gte` | `(Int, Int) -> Boolean` | `a >= b` |
| `lte` | `(Int, Int) -> Boolean` | `a <= b` |

### Higher-Order (4 functions)

| Function | Signature | Implementation |
|----------|-----------|----------------|
| `filter` | `(List<A>, A -> Boolean) -> List<A>` | Keep elements where predicate is true |
| `map` | `(List<A>, A -> B) -> List<B>` | Transform each element |
| `all` | `(List<A>, A -> Boolean) -> Boolean` | True if all match |
| `any` | `(List<A>, A -> Boolean) -> Boolean` | True if any match |

Higher-order functions are implemented via `InlineTransform` in the compiler:
- Lambda bodies are compiled to inline evaluators
- Supports arithmetic, comparison, and field access in lambdas
- No module creation overhead per element

### Utility (2 functions)

| Function | Signature | Implementation |
|----------|-----------|----------------|
| `identity` | `(A) -> A` | Pass through unchanged |
| `log` | `(A) -> A` | Log value and pass through |

### Type Conversion (3 functions)

| Function | Signature | Implementation |
|----------|-----------|----------------|
| `to-string` | `(A) -> String` | Convert to string representation |
| `to-int` | `(String) -> Int` | Parse integer (error on invalid) |
| `to-float` | `(String) -> Float` | Parse float (error on invalid) |

## Namespace Organization

Functions are organized into namespaces:

```
stdlib
├── math      (add, subtract, multiply, ...)
├── string    (concat, join, split, ...)
├── list      (list-length, list-first, ...)
├── boolean   (and, or, not)
├── compare   (eq-int, eq-string, gt, lt, ...)
└── util      (identity, log)
```

Usage in constellation-lang:

```constellation
use stdlib.math

result = add(1, 2)           # Via wildcard import
result = stdlib.math.add(1, 2)  # Fully qualified
```

## Adding New Functions

1. **Choose/create a category file** in `categories/`

2. **Define I/O case classes**:
```scala
case class MyInput(value: String)
case class MyOutput(result: String)
```

3. **Create the module**:
```scala
val myModule: Module.Uninitialized = ModuleBuilder
  .metadata("stdlib.my-func", "Description", 1, 0)
  .tags("stdlib", "category")
  .implementationPure[MyInput, MyOutput](in => MyOutput(in.value.toUpperCase))
  .build
```

4. **Create the signature**:
```scala
val mySignature: FunctionSignature = FunctionSignature(
  "my-func",
  List("value" -> SemanticType.SString),
  SemanticType.SString,
  "stdlib.my-func",
  Some("stdlib.category")
)
```

5. **Add to category collections**:
```scala
def categorySignatures: List[FunctionSignature] = List(..., mySignature)
def categoryModules: Map[String, Module.Uninitialized] = Map(..., myModule.spec.name -> myModule)
```

6. **Update StdLib.scala** if creating a new category

## Dependencies

- **Depends on:** `core` (CType), `runtime` (ModuleBuilder), `lang-compiler` (SemanticType, FunctionSignature)
- **Depended on by:** `http-api`, `example-app`

## Features Using This Component

| Feature | Stdlib Role |
|---------|-------------|
| [Arithmetic](../../stdlib/math.md) | Math functions |
| [String ops](../../stdlib/string.md) | String functions |
| [List ops](../../stdlib/list.md) | List functions |
| [HOF](../../stdlib/higher-order.md) | filter, map, all, any |
| [Type checking](../compiler/) | Function signatures for type inference |
