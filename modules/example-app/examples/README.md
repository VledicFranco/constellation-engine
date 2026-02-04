# Constellation-Lang Examples

This directory contains example pipelines demonstrating all supported constellation-lang features. The examples are organized in a progressive learning path, from basic concepts to advanced features.

## Quick Start

Run any example using the HTTP API:

```bash
# Start the server
make server

# Execute an example
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{"source": "...", "dagName": "my-dag", "inputs": {...}}'
```

Or use the VSCode extension (Ctrl+Shift+R to run the active file).

## Learning Path

### Beginner

| # | File | Features Demonstrated | Description |
|---|------|----------------------|-------------|
| 1 | [simple-test.cst](simple-test.cst) | Basic I/O, module calls | Minimal example showing inputs, outputs, and function calls |
| 2 | [text-analysis.cst](text-analysis.cst) | Module chaining, text processing | Chain multiple text processing modules together |
| 3 | [namespaces.cst](namespaces.cst) | `use` declarations, namespace imports | Import stdlib functions with aliases |

### Intermediate

| # | File | Features Demonstrated | Description |
|---|------|----------------------|-------------|
| 4 | [string-interpolation.cst](string-interpolation.cst) | String templates `"Hello ${name}"` | Dynamic string construction with embedded expressions |
| 5 | [branch-expressions.cst](branch-expressions.cst) | `branch { }`, `otherwise` | Multi-way conditionals cleaner than nested if/else |
| 6 | [data-pipeline.cst](data-pipeline.cst) | Numeric processing, arithmetic | Data transformation pipeline with calculations |
| 7 | [guard-and-coalesce.cst](guard-and-coalesce.cst) | `when` guards, `??` coalesce | Safe optional handling without explicit null checks |
| 8 | [optional-types.cst](optional-types.cst) | `Optional<T>`, fallback patterns | Explicit optional type inputs and coalesce chains |

### Advanced

| # | File | Features Demonstrated | Description |
|---|------|----------------------|-------------|
| 9 | [lambdas-and-hof.cst](lambdas-and-hof.cst) | `(x) => expr`, filter/map/all/any | Higher-order functions with lambda expressions |
| 10 | [union-types.cst](union-types.cst) | `A \| B` type syntax | Union types for variant data modeling |
| 11 | [lead-scoring-pipeline.cst](lead-scoring-pipeline.cst) | Complete feature showcase | Real-world B2B lead scoring with all features |

## Feature Reference

### Basic Syntax

```constellation
# Comments start with #
in name: String           # Input declaration
out result                 # Output declaration
value = ModuleCall(input)  # Variable assignment
```

### Types

```constellation
# Primitive types
in text: String
in count: Int
in rate: Float
in enabled: Boolean

# Collection types
in numbers: List<Int>
in lookup: Map<String, Int>

# Optional type
in maybeValue: Optional<Int>

# Record types
type Person = { name: String, age: Int }

# Union types
type Result = { value: Int } | { error: String }
```

### Operators

```constellation
# Arithmetic: +, -, *, /
sum = a + b
product = x * y

# Comparison: >, <, >=, <=, ==, !=
isGreater = score > threshold

# Boolean: and, or, not
qualified = hasExperience and passedTest
```

### Conditionals

```constellation
# If/else expression
result = if (condition) valueA else valueB

# Branch expression (multi-way)
grade = branch {
  score >= 90 -> "A",
  score >= 80 -> "B",
  otherwise -> "C"
}
```

### Guards and Optionals

```constellation
# Guard: produces Optional<T>
bonus = 100 when score > 90

# Coalesce: unwrap optional with fallback
safeBonus = bonus ?? 0

# Chained coalesce
final = first ?? second ?? default
```

### Namespaces

```constellation
# Wildcard import
use stdlib.math

# Aliased import
use stdlib.string as str

# Usage
result = add(a, b)           # From wildcard
trimmed = str.trim(text)     # Using alias
fqn = stdlib.math.max(x, y)  # Fully qualified
```

### Higher-Order Functions

```constellation
use stdlib.collection
use stdlib.compare

# Filter: keep matching elements
positives = filter(numbers, (x) => gt(x, 0))

# Map: transform each element
doubled = map(numbers, (x) => multiply(x, 2))

# All: check if all match
allPositive = all(numbers, (x) => gt(x, 0))

# Any: check if any matches
hasNegative = any(numbers, (x) => lt(x, 0))
```

## Running Examples

### Via HTTP API

```bash
# Example: running simple-test.cst
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in text: String\nresult = Uppercase(text)\nout result",
    "dagName": "simple-dag",
    "inputs": {"text": "hello world"}
  }'
```

### Via VSCode Extension

1. Open any `.cst` file
2. Press `Ctrl+Shift+R` to run
3. Enter inputs when prompted
4. View results in the Script Runner panel

### Via LSP/DAG Visualization

1. Open any `.cst` file
2. Press `Ctrl+Shift+D` to view DAG
3. See the execution graph with type information

## Standard Library Reference

The stdlib provides common functions across these namespaces:

| Namespace | Functions |
|-----------|-----------|
| `stdlib.math` | add, subtract, multiply, divide, max, min |
| `stdlib.string` | concat, trim, string-length |
| `stdlib.compare` | gt, lt, gte, lte, eq-int, eq-string |
| `stdlib.bool` | and, or, not |
| `stdlib.list` | list-length, list-first, list-last, list-is-empty |
| `stdlib.collection` | filter, map, all, any |
| `stdlib.util` | identity, log |

## Troubleshooting

### Lambda bodies need explicit function imports

Lambda expressions need functions imported for comparisons and arithmetic:

```constellation
# Wrong - operators not directly available in lambda
filter(nums, (x) => x > 0)  # Error: gt not found

# Correct - import and use functions
use stdlib.compare
filter(nums, (x) => gt(x, 0))
```

### Lambda closures are not supported

Lambda bodies can only reference the lambda parameter and literals. Capturing outer variables (closures) is not yet implemented:

```constellation
# Wrong - capturing outer variable 'threshold'
in threshold: Int
filter(nums, (x) => gt(x, threshold))  # Error: undefined variable

# Correct - use literal values
filter(nums, (x) => gt(x, 10))
```

### Union types are declaration-only

Union types can be declared and passed through pipelines, but runtime pattern matching is not yet implemented.
