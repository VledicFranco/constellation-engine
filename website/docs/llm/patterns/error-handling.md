---
title: "Error Handling Patterns"
sidebar_position: 3
description: "Comprehensive guide to compiler diagnostics, runtime errors, and recovery strategies for Constellation Engine"
---

# Error Handling Patterns

This guide provides comprehensive coverage of error handling in Constellation Engine, including compiler diagnostics, runtime errors, recovery strategies, and testing patterns for LLM-assisted development.

## Overview

Constellation Engine has a sophisticated error handling system that spans three main phases:

1. **Parse-time errors** — Syntax and structure validation
2. **Compile-time errors** — Type checking, semantic analysis, and DAG construction
3. **Runtime errors** — Module execution, lifecycle, and resource management

Each phase produces structured, actionable error messages with precise location information, suggestions, and documentation links.

## Error Hierarchy

### Compile-Time Errors (`CompileError`)

All compile-time errors extend the `CompileError` sealed trait from the AST module:

```scala
sealed trait CompileError {
  def message: String
  def span: Option[Span]  // Source location (start/end position)
}
```

**Error Categories:**

| Category | Error Codes | Description |
|----------|-------------|-------------|
| **Reference** | E001-E009 | Undefined variables, functions, types, namespaces |
| **Type** | E010-E019 | Type mismatches, incompatible operations |
| **Syntax** | E020-E029 | Parse errors, unexpected tokens |
| **Semantic** | E030-E039 | Duplicate definitions, circular dependencies |
| **Internal** | E900+ | Compiler bugs (should be reported) |

### Runtime Errors

Runtime errors use different hierarchies depending on the subsystem:

```scala
// Execution errors (from errors package)
sealed trait ApiError {
  def message: String
}

// Lifecycle exceptions
class CircuitOpenException(moduleName: String)
class QueueFullException(maxSize: Int)
class ShutdownRejectedException()

// Suspendable execution errors
case class InputTypeMismatchError(name: String, expected: CType, actual: CType)
case class InputAlreadyProvidedError(name: String)
case class UnknownNodeError(name: String)
```

### DAG Compilation Errors (`CompilerError`)

Lower-level IR compilation errors:

```scala
sealed trait CompilerError {
  def message: String
}

// Common variants:
case class NodeNotFound(nodeId: UUID, context: String)
case class LambdaParameterNotBound(paramName: String)
case class UnsupportedOperation(operation: String)
case class InvalidFieldAccess(field: String, actualType: String)
```

## Common Error Types

### 1. Parse Errors (E020-E029)

Parse errors occur when the source code has invalid syntax.

#### E020: Syntax Error

**Cause:** Invalid syntax that the parser cannot understand.

**Examples:**

```constellation
# Missing closing parenthesis
result = Uppercase(text
out result
```

**Error:**
```
Error E020: Syntax error
  --> line 1, column 22

  1 │ result = Uppercase(text
    │                         ^
  2 │ out result

  Parse error: expected ')' but got end of line

  The parser encountered invalid syntax.

  Check for:
    - Missing or extra parentheses
    - Missing commas between arguments
    - Typos in keywords
    - Unclosed strings or brackets

  → Check for unclosed parentheses or brackets
  → Verify all function calls have matching '(' and ')'
```

**Recovery Strategy:**
1. Check the indicated line and column
2. Look at the previous line for unclosed delimiters
3. Verify matching parentheses/brackets/braces
4. Check for typos in keywords (`in`, `out`, `type`, `use`)

**Common Mistakes:**

```constellation
# WRONG: Missing type annotation
in x
out x

# CORRECT:
in x: String
out x

# WRONG: Missing comma in function args
result = Add(x y)

# CORRECT:
result = Add(x, y)

# WRONG: Unclosed string literal
message = "Hello
out message

# CORRECT:
message = "Hello"
out message

# WRONG: Invalid identifier (starts with digit)
in 123value: Int

# CORRECT:
in value123: Int
```

#### E021: Unexpected Token

**Cause:** Parser found a token it didn't expect at this position.

**Example:**

```constellation
in x: Int
out @ result
```

**Error:**
```
Error E021: Unexpected token
  --> line 2, column 5

  2 │ out @ result
    │     ^

  The parser found a token it didn't expect at this position.

  This usually indicates a syntax error nearby.

  → Remove the unexpected '@' character
  → Valid output syntax: out identifier
```

**Recovery Strategy:**
1. Remove invalid characters
2. Check if you're using reserved operators in wrong contexts
3. Verify identifier naming rules (alphanumeric + underscore, can't start with digit)

### 2. Reference Errors (E001-E009)

Reference errors occur when you use a name that doesn't exist.

#### E001: Undefined Variable

**Cause:** Using a variable that hasn't been declared or assigned.

**Example:**

```constellation
in text: String
result = Uppercase(textt)  # Typo: 'textt' instead of 'text'
out result
```

**Error:**
```
Error E001: Undefined variable
  --> line 2, column 20

  1 │ in text: String
  2 │ result = Uppercase(textt)
    │                    ^^^^^
  3 │ out result

  Undefined variable: textt

  The variable you're trying to use has not been declared.

  Variables must be declared before use:
    - As an input: in variableName: Type
    - As an assignment: variableName = SomeModule(...)

  → Did you mean 'text'?

  See: https://constellation-engine.dev/docs/constellation-lang/declarations
```

**Recovery Strategy:**
1. Check for typos — the error includes "Did you mean?" suggestions
2. Verify the variable is declared before use (order matters)
3. Check variable name is case-sensitive
4. If truly undefined, add an input declaration: `in variableName: Type`

**Common Mistakes:**

```constellation
# WRONG: Using before declaring
out result
result = Compute(x)

# CORRECT: Declare/assign before use
result = Compute(x)
out result

# WRONG: Case mismatch
in userName: String
result = Process(username)  # 'username' != 'userName'

# CORRECT:
in userName: String
result = Process(userName)

# WRONG: Referencing non-existent field
in data: { id: Int, name: String }
email = data.email  # 'email' field doesn't exist

# CORRECT:
in data: { id: Int, name: String, email: String }
email = data.email
```

#### E002: Undefined Function

**Cause:** Calling a function that isn't registered with the compiler.

**Example:**

```constellation
in text: String
result = Upppercase(text)  # Typo: 'Upppercase' instead of 'Uppercase'
out result
```

**Error:**
```
Error E002: Undefined function
  --> line 2, column 10

  2 │ result = Upppercase(text)
    │          ^^^^^^^^^^

  Undefined function: Upppercase

  The function you're trying to call is not registered.

  Make sure the function is:
    - Spelled correctly (function names are case-sensitive)
    - Registered with the compiler via StdLib or custom modules
    - Imported if it's from a namespace: use stdlib.math

  → Did you mean 'Uppercase'?

  See: https://constellation-engine.dev/docs/constellation-lang/functions
```

**Recovery Strategy:**
1. Check for typos in function name
2. Verify the module is registered in your Scala code:
   ```scala
   constellation.setModule(myModule)
   ```
3. Check the `ModuleBuilder.metadata()` name matches exactly:
   ```scala
   ModuleBuilder.metadata("Uppercase", ...) // Must match usage
   ```
4. For namespaced functions, add `use` declaration:
   ```constellation
   use stdlib.math
   result = math.add(x, y)
   ```

**Common Mistakes:**

```constellation
# WRONG: Function not registered
result = CustomFunction(data)
# Need to register in Scala: constellation.setModule(customFunction)

# WRONG: Case mismatch
result = uppercase(text)  # Should be 'Uppercase'

# CORRECT:
result = Uppercase(text)

# WRONG: Missing namespace
result = add(1, 2)  # 'add' is in stdlib.math

# CORRECT:
use stdlib.math
result = math.add(1, 2)
```

#### E003: Undefined Type

**Cause:** Using a type name that isn't defined.

**Example:**

```constellation
in data: MyCustomType  # MyCustomType not defined
out data
```

**Error:**
```
Error E003: Undefined type
  --> line 1, column 10

  1 │ in data: MyCustomType
    │          ^^^^^^^^^^^^

  Undefined type: MyCustomType

  The type you specified is not defined.

  Built-in types: String, Int, Float, Boolean
  Collections: List<T>, Map<K, V>, Optional<T>
  Custom types must be declared: type MyType = { field: Type }

  → Define the type first: type MyCustomType = { ... }

  See: https://constellation-engine.dev/docs/constellation-lang/types
```

**Recovery Strategy:**
1. Check for typos in type name
2. Verify built-in types are spelled correctly (case-sensitive)
3. For custom types, add a type definition:
   ```constellation
   type MyCustomType = { id: Int, name: String }
   ```
4. For parameterized types, use correct syntax: `List<T>`, not `List[T]`

**Common Mistakes:**

```constellation
# WRONG: Typo in built-in type
in count: Integer  # Should be 'Int'

# CORRECT:
in count: Int

# WRONG: Using undefined custom type
in user: User

# CORRECT: Define type first
type User = { id: Int, name: String }
in user: User

# WRONG: Wrong bracket syntax
in items: List[String]  # Should use '<>'

# CORRECT:
in items: List<String>
```

#### E006: Invalid Projection

**Cause:** Trying to project a field that doesn't exist on a record type.

**Example:**

```constellation
in data: { id: Int, name: String }
result = data[id, email]  # 'email' doesn't exist
out result
```

**Error:**
```
Error E006: Invalid projection
  --> line 2, column 19

  2 │ result = data[id, email]
    │                   ^^^^^

  Invalid projection: field 'email' not found

  The field you're trying to project doesn't exist on this type.

  Projection syntax: record[field1, field2]
  This creates a new record with only the specified fields.

  → Did you mean 'name'?
  → Available fields: id, name

  See: https://constellation-engine.dev/docs/constellation-lang/expressions
```

**Recovery Strategy:**
1. Check field name spelling
2. Verify the field exists in the record type definition
3. Use hover info in VSCode to see available fields
4. Consider adding the field to the type definition if needed

#### E007: Invalid Field Access

**Cause:** Trying to access a field that doesn't exist using dot notation.

**Example:**

```constellation
in user: { id: Int, name: String }
email = user.email  # 'email' field doesn't exist
out email
```

**Error:**
```
Error E007: Invalid field access
  --> line 2, column 14

  2 │ email = user.email
    │              ^^^^^

  Invalid field access: field 'email' not found

  The field you're trying to access doesn't exist on this type.

  Field access syntax: record.fieldName
  The source expression must be a record type with that field.

  → Available fields: id, name

  See: https://constellation-engine.dev/docs/constellation-lang/expressions
```

**Recovery Strategy:**
1. Check available fields (listed in error message)
2. Verify the upstream type definition
3. Use projection to select subset of fields: `user[id, name]`

### 3. Type Errors (E010-E019)

Type errors occur when types don't match expectations.

#### E010: Type Mismatch

**Cause:** The actual type doesn't match the expected type.

**Example:**

```constellation
in count: Int
result = Uppercase(count)  # Uppercase expects String, got Int
out result
```

**Error:**
```
Error E010: Type mismatch
  --> line 2, column 20

  2 │ result = Uppercase(count)
    │                    ^^^^^

  Type mismatch: expected String, got Int

  The actual type does not match the expected type.

  This often happens when:
    - Passing wrong argument type to a function
    - Assigning incompatible value to a variable
    - Returning wrong type from a conditional

  → Use ToString(count) to convert to String

  See: https://constellation-engine.dev/docs/constellation-lang/type-system
```

**Recovery Strategy:**
1. Check function signature to see what types are expected
2. Use type conversion functions:
   - `ToString(x)` — Convert any value to String
   - `ToInt(x)` — Convert Float to Int (truncates)
   - Int automatically promotes to Float in arithmetic
3. Verify your input declarations match the data you're providing
4. Use hover in VSCode to see inferred types

**Common Type Conversions:**

```constellation
# String conversions
in count: Int
message = ToString(count)  # Int -> String

# Numeric conversions
in price: Float
dollars = ToInt(price)  # Float -> Int (truncates)

in quantity: Int
total = quantity * 1.5  # Int -> Float (automatic promotion)

# Optional handling
in maybeValue: Optional<Int>
value = maybeValue ?? 0  # Extract with default

# Boolean from comparison
in age: Int
isAdult = age >= 18  # Int -> Boolean via comparison
```

#### E012: Incompatible Merge

**Cause:** Trying to merge types that can't be merged with the `+` operator.

**Example:**

```constellation
in count: Int
in text: String
result = count + text  # Can't merge Int and String
out result
```

**Error:**
```
Error E012: Incompatible types for merge
  --> line 3, column 10

  3 │ result = count + text
    │          ^^^^^^^^^^^^

  Cannot merge types: Int + String

  Cannot merge these types with the + operator.

  The merge operator requires compatible types:
    - Two records (fields are merged)
    - Two Candidates (element-wise merge)
    - Candidates + Record (broadcast)
    - Record + Candidates (broadcast)

  → For numeric addition, use stdlib.math.add(count, ...)
  → The + operator is for merging records, not arithmetic

  See: https://constellation-engine.dev/docs/constellation-lang/operators
```

**Recovery Strategy:**
1. **Important:** In constellation-lang, `+` is for record/candidates merge, NOT arithmetic
2. For numeric addition, use `stdlib.math.add(a, b)`
3. For record merge, both sides must be records:
   ```constellation
   in a: { x: Int }
   in b: { y: String }
   merged = a + b  # Valid: creates { x: Int, y: String }
   ```
4. For candidates merge, both must be Candidates or one can be a record:
   ```constellation
   type A = { x: Int }
   type B = { y: String }
   in items1: Candidates<A>
   in items2: Candidates<B>
   merged = items1 + items2  # Element-wise merge
   ```

**Common Mistakes:**

```constellation
# WRONG: Using + for arithmetic
in a: Int
in b: Int
sum = a + b  # + is for merging, not addition!

# CORRECT: Use stdlib.math
use stdlib.math
in a: Int
in b: Int
sum = math.add(a, b)

# WRONG: Merging incompatible types
in count: Int
in data: { name: String }
result = count + data  # Int can't merge with record

# CORRECT: Merge compatible records
in data1: { x: Int }
in data2: { y: String }
result = data1 + data2  # Both are records
```

#### E013: Unsupported Comparison

**Cause:** Using a comparison operator with unsupported types.

**Example:**

```constellation
in a: { x: Int }
in b: { y: String }
result = a == b  # Can't compare different record types
out result
```

**Recovery Strategy:**
1. Ensure both operands have compatible types
2. Comparison operators work with:
   - Int and Int
   - Float and Float
   - String and String (lexicographic)
   - Boolean and Boolean (== and != only)
3. For complex types, compare specific fields:
   ```constellation
   match = (a.x == b.x) and (a.y == b.y)
   ```

#### E016: Invalid Option Value

**Cause:** Providing an invalid value for a module call option.

**Example:**

```constellation
in data: String
result = Process(data) with retry: -1  # Negative retry count
out result
```

**Error:**
```
Error E016: Invalid option value
  --> line 2, column 36

  2 │ result = Process(data) with retry: -1
    │                                    ^^

  Invalid option value: retry must be >= 0

  The value provided for a module call option is invalid.

  Option value constraints:
    - retry: must be >= 0
    - timeout, delay, cache: must be > 0
    - concurrency: must be > 0
    - throttle count: must be > 0

  → Use retry: 3 for three retry attempts
  → Use retry: 0 for no retries

  See: https://constellation-engine.dev/docs/constellation-lang/module-options
```

**Recovery Strategy:**
1. Check option value constraints in error message
2. Common valid values:
   - `retry: 3` — Retry up to 3 times
   - `timeout: 5000` — 5 second timeout (milliseconds)
   - `cache: 3600000` — 1 hour cache (milliseconds)
   - `fallback: defaultValue` — Use default on failure
   - `concurrency: 10` — Max 10 concurrent executions
   - `priority: 5` — Priority level (higher = more urgent)

#### E017: Fallback Type Mismatch

**Cause:** The fallback value type doesn't match the module's return type.

**Example:**

```constellation
in id: Int
# GetAge returns Int, but fallback is String
age = GetAge(id) with fallback: "Unknown"
out age
```

**Error:**
```
Error E017: Fallback type mismatch
  --> line 2, column 38

  2 │ age = GetAge(id) with fallback: "Unknown"
    │                                 ^^^^^^^^^

  Fallback type mismatch: expected Int, got String

  The fallback expression type doesn't match the module return type.

  The fallback option provides a default value when the module fails.
  Its type must be compatible with what the module returns.

  Example:
    result = GetName(id) with fallback: "Unknown"
    If GetName returns String, "Unknown" is valid.
    If GetName returns Int, "Unknown" would be invalid.

  → Use fallback: 0 for Int return type
  → Or use fallback: -1 to indicate unknown age

  See: https://constellation-engine.dev/docs/constellation-lang/module-options
```

**Recovery Strategy:**
1. Check the module's return type (hover in VSCode or check signature)
2. Provide a fallback value of matching type:
   ```constellation
   # For Int return type
   count = GetCount(id) with fallback: 0

   # For String return type
   name = GetName(id) with fallback: "Unknown"

   # For Boolean return type
   flag = CheckStatus(id) with fallback: false

   # For Optional return type
   value = Fetch(id) with fallback: None
   ```

### 4. Semantic Errors (E030-E039)

Semantic errors occur when code is syntactically valid but has logical issues.

#### E030: Duplicate Definition

**Cause:** Defining the same name multiple times in the same scope.

**Example:**

```constellation
in data: String
in data: Int  # Duplicate input name
out data
```

**Error:**
```
Error E030: Duplicate definition
  --> line 2, column 4

  1 │ in data: String
  2 │ in data: Int
    │    ^^^^

  Duplicate definition: 'data' is already defined

  This name is already defined in the current scope.

  Each variable, type, and input must have a unique name.

  → Rename one of the 'data' variables
  → Use data1 and data2 for different inputs
```

**Recovery Strategy:**
1. Rename one of the conflicting identifiers
2. If you meant to reassign, use assignment instead:
   ```constellation
   in data: String
   data2 = Transform(data)  # New variable
   ```

**Common Mistakes:**

```constellation
# WRONG: Duplicate input
in x: Int
in x: String

# CORRECT: Different names
in x: Int
in y: String

# WRONG: Duplicate assignment
result = Step1(data)
result = Step2(data)

# CORRECT: Chain or use different names
intermediate = Step1(data)
result = Step2(intermediate)

# WRONG: Duplicate type definition
type User = { id: Int }
type User = { name: String }

# CORRECT: Different type names or merge fields
type User = { id: Int, name: String }
```

#### E031: Circular Dependency

**Cause:** A variable depends on itself directly or transitively.

**Example:**

```constellation
in x: Int
a = b + 1
b = a + 1
out a
```

**Error:**
```
Error E031: Circular dependency
  --> line 2, column 1

  2 │ a = b + 1
    │ ^
  3 │ b = a + 1

  Circular dependency detected: a -> b -> a

  A circular dependency was detected in the DAG.

  Variables cannot depend on themselves, directly or indirectly.

  → Break the cycle by removing one of the dependencies
  → Restructure the computation to be acyclic

  See: https://constellation-engine.dev/docs/constellation-lang/dag
```

**Recovery Strategy:**
1. Identify the cycle in the error message
2. Break the cycle by:
   - Using different input values
   - Restructuring the computation
   - Removing circular reference
3. Remember: Constellation is a DAG (Directed Acyclic Graph) — cycles are not allowed

### 5. Internal Errors (E900+)

Internal compiler errors indicate bugs in the compiler itself.

#### E900: Internal Compiler Error

**Cause:** An unexpected error in the compiler.

**Error:**
```
Error E900: Internal compiler error
  --> line 3, column 10

  An unexpected error occurred in the compiler.

  This is a bug in the compiler. Please report it at:
  https://github.com/VledicFranco/constellation-engine/issues

  → Include the constellation-lang source that triggered this error
  → Simplify the script to find the minimal reproduction case
```

**Recovery Strategy:**
1. This is a compiler bug, not your fault
2. Try simplifying your script to isolate the issue
3. Report the bug with:
   - The full source code that triggered the error
   - Any error messages or stack traces
   - Constellation Engine version (`sbt version`)

## Runtime Errors

### Execution API Errors

From the `ApiError` hierarchy:

#### InputError

**Cause:** Invalid input provided to the pipeline.

**Example:**

```scala
// Providing wrong type
val inputs = Map("count" -> Json.fromString("not a number"))
// Pipeline expects: in count: Int
```

**Error:**
```json
{
  "error": "INPUT_ERROR",
  "message": "Input validation failed for 'count': expected Int, got String"
}
```

**Recovery Strategy:**
1. Verify JSON input types match constellation-lang declarations:
   ```constellation
   in count: Int  // Expects JSON number
   in text: String  // Expects JSON string
   in flag: Boolean  // Expects JSON boolean
   in items: List<String>  // Expects JSON array
   ```
2. Check for typos in input names (case-sensitive)
3. Ensure all required inputs are provided

#### ExecutionError

**Cause:** A module threw an exception during execution.

**Example:**

```scala
// Module implementation
.implementationPure[Input, Output] { input =>
  if (input.value < 0) throw new IllegalArgumentException("Value must be positive")
  Output(input.value * 2)
}
```

**Error:**
```json
{
  "error": "EXECUTION_ERROR",
  "message": "Module 'ValidateInput' execution failed: Value must be positive"
}
```

**Recovery Strategy:**
1. Check the error message for the underlying cause
2. Validate input constraints in your module implementation
3. Use `Try` or `Either` for safer error handling:
   ```scala
   .implementation[Input, Output] { input =>
     IO {
       if (input.value < 0) {
         throw new IllegalArgumentException("Value must be positive")
       }
       Output(input.value * 2)
     }.handleErrorWith { err =>
       IO.raiseError(new RuntimeException(s"Validation failed: ${err.getMessage}"))
     }
   }
   ```
4. Use module options for resilience:
   ```constellation
   result = RiskyModule(data) with fallback: defaultValue
   result = UnreliableModule(data) with retry: 3
   ```

#### CompilationError

**Cause:** Compilation failed with multiple errors.

**Error:**
```json
{
  "error": "COMPILATION_ERROR",
  "message": "Compilation failed",
  "errors": [
    "Undefined variable: textt at line 2",
    "Type mismatch: expected String, got Int at line 5"
  ]
}
```

**Recovery Strategy:**
1. Fix each error in the list individually
2. Start with the first error (later errors may cascade from earlier ones)
3. Re-compile after each fix to see remaining errors

#### NotFoundError

**Cause:** Referenced resource doesn't exist.

**Example:**

```scala
constellation.getModule("NonExistent")
```

**Error:**
```json
{
  "error": "NOT_FOUND",
  "message": "Module not found: NonExistent"
}
```

**Recovery Strategy:**
1. Verify the module is registered:
   ```scala
   constellation.setModule(myModule)
   ```
2. Check the module name matches exactly (case-sensitive)
3. For standard library, ensure StdLib is registered:
   ```scala
   StdLib.allModules.values.toList.traverse(constellation.setModule)
   ```

### Lifecycle Errors

#### CircuitOpenException

**Cause:** Circuit breaker is open due to repeated module failures.

**Error:**
```
CircuitOpenException: Circuit breaker is open for module: ExternalAPI
```

**Recovery Strategy:**
1. Wait for the circuit breaker's reset duration (default: 30 seconds)
2. Check the underlying module for persistent failures:
   - External service down
   - Network connectivity issues
   - Configuration error
3. Monitor circuit breaker state:
   ```scala
   val stats = circuitBreaker.stats
   println(s"State: ${stats.state}")  // Open, HalfOpen, or Closed
   ```
4. Adjust circuit breaker configuration if needed:
   ```scala
   CircuitBreakerConfig(
     failureThreshold = 5,    // Open after 5 failures
     resetDuration = 60.seconds,  // Wait 60s before retry
     halfOpenRequests = 3     // Test with 3 requests
   )
   ```

#### QueueFullException

**Cause:** Scheduler queue is full (too many pending tasks).

**Error:**
```
QueueFullException: Scheduler queue is full (max: 100)
```

**Recovery Strategy:**
1. Implement backpressure in your application:
   ```scala
   execution.attempt.flatMap {
     case Right(result) => handleResult(result)
     case Left(_: QueueFullException) =>
       // Wait and retry, or reject request
       IO.sleep(100.millis) *> retryExecution
     case Left(err) => IO.raiseError(err)
   }
   ```
2. Increase queue size:
   ```bash
   CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=32 sbt run
   ```
3. Monitor queue depth:
   ```scala
   val stats = scheduler.stats
   println(s"Queue: ${stats.queuedCount}/${stats.maxQueueSize}")
   ```

#### ShutdownRejectedException

**Cause:** New execution submitted during graceful shutdown.

**Error:**
```
ShutdownRejectedException: Execution rejected: system is shutting down
```

**Recovery Strategy:**
1. This is expected during shutdown — don't submit new work
2. Check lifecycle state before submitting:
   ```scala
   if (lifecycle.state == LifecycleState.Running) {
     constellation.execute(source, inputs)
   } else {
     IO.raiseError(new IllegalStateException("System not ready"))
   }
   ```
3. Implement graceful degradation:
   ```scala
   execution.attempt.flatMap {
     case Right(result) => handleSuccess(result)
     case Left(_: ShutdownRejectedException) =>
       // Return cached result or error response
       getCachedResult.orElse(IO.pure(ErrorResponse))
     case Left(err) => handleError(err)
   }
   ```

### Suspendable Execution Errors

#### InputTypeMismatchError

**Cause:** Resumed execution received input with wrong type.

**Example:**

```scala
// Initial execution declares: in count: Int
val suspended = execution.suspend()

// Later resume with wrong type
val result = execution.resume(
  suspended.executionId,
  Map("count" -> CValue.CString("not a number"))  // Should be CInt
)
```

**Recovery Strategy:**
1. Verify input types match the pipeline declaration
2. Use the correct CValue constructor:
   ```scala
   CValue.CInt(42)              // For Int
   CValue.CString("text")       // For String
   CValue.CBoolean(true)        // For Boolean
   CValue.CList(List(...))      // For List<T>
   ```

#### PipelineChangedError

**Cause:** Pipeline was recompiled between suspend and resume.

**Recovery Strategy:**
1. This is expected if you recompile during execution
2. Options:
   - Accept that in-flight executions will fail
   - Version your pipelines and store version with suspended state
   - Drain in-flight executions before recompiling:
     ```scala
     // Before recompiling
     lifecycle.drain(30.seconds)
     // Now safe to recompile
     ```

## Error Formatting and Presentation

### ErrorFormatter

The `ErrorFormatter` class provides rich error messages:

```scala
import io.constellation.lang.compiler.{ErrorFormatter, SuggestionContext}

val source = """
  in text: String
  result = Uppercase(textt)
  out result
"""

val formatter = ErrorFormatter(source)
val context = SuggestionContext(
  definedVariables = List("text", "result"),
  availableFunctions = List("Uppercase", "Lowercase", "Trim")
)

// Format error with context
val formatted = formatter.format(error, context)

// Output formats
println(formatted.toPlainText)  // Terminal output
println(formatted.toMarkdown)   // IDE hover tooltip
println(formatted.toOneLine)    // Log format
```

### FormattedError Structure

```scala
case class FormattedError(
  code: String,              // "E001"
  title: String,             // "Undefined variable"
  category: ErrorCategory,   // Reference, Type, Syntax, etc.
  location: String,          // "line 2, column 20"
  snippet: String,           // Code with underline
  explanation: String,       // Detailed explanation
  suggestions: List[String], // "Did you mean?" suggestions
  docUrl: Option[String],    // Documentation link
  rawMessage: String         // Original error message
)
```

### Suggestion System

The suggestion system uses Levenshtein distance for "Did you mean?" suggestions:

```scala
import io.constellation.lang.compiler.Suggestions

// Find similar strings
val similar = Suggestions.findSimilar(
  target = "textt",
  candidates = List("text", "count", "result"),
  maxDistance = 2,      // Max edit distance
  maxSuggestions = 3    // Max suggestions to return
)
// Returns: List("text")

// Context-aware suggestions
val suggestions = Suggestions.forError(error, context)
// Returns suggestions based on error type
```

**Levenshtein Distance Examples:**

| Target | Candidate | Distance | Match? |
|--------|-----------|----------|--------|
| "textt" | "text" | 1 | ✓ (extra 't') |
| "Upppercase" | "Uppercase" | 2 | ✓ (extra 'p's) |
| "usr" | "user" | 2 | ✓ (missing 'e') |
| "count" | "amount" | 3 | ✗ (too far) |

## Testing Error Scenarios

### Parser Error Recovery Tests

```scala
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.ast.CompileError

// Test that invalid syntax produces ParseError
val source = "result = \nout result"  // Missing expression
val result = ConstellationParser.parse(source)

result.isLeft shouldBe true
result.left.toOption.get shouldBe a[CompileError.ParseError]
```

### Type Error Tests

```scala
import io.constellation.lang.compiler.{ErrorFormatter, ErrorCodes}
import io.constellation.lang.semantic.TypeChecker

// Test type mismatch detection
val source = """
  in count: Int
  result = Uppercase(count)  # Expects String
  out result
"""

val pipeline = ConstellationParser.parse(source).toOption.get
val typeResult = TypeChecker.check(pipeline, registry)

typeResult.isLeft shouldBe true
val errors = typeResult.left.toOption.get
errors.head shouldBe a[CompileError.TypeMismatch]

// Test formatted error
val formatter = ErrorFormatter(source)
val formatted = formatter.format(errors.head)

formatted.code shouldBe "E010"
formatted.category shouldBe ErrorCategory.Type
formatted.suggestions should not be empty
```

### Runtime Error Tests

```scala
import cats.effect.IO
import io.constellation.ModuleBuilder

// Test module execution error handling
val failingModule = ModuleBuilder
  .metadata("FailingModule", "Always fails", 1, 0)
  .implementation[Input, Output] { _ =>
    IO.raiseError(new RuntimeException("Simulated failure"))
  }
  .build

// Test with fallback
val source = """
  in data: String
  result = FailingModule(data) with fallback: "default"
  out result
"""

// Should use fallback instead of failing
val result = constellation.execute(source, inputs).unsafeRunSync()
result shouldBe Right(Map("result" -> CValue.CString("default")))

// Test without fallback (should fail)
val sourceNoFallback = """
  in data: String
  result = FailingModule(data)
  out result
"""

val result2 = constellation.execute(sourceNoFallback, inputs).attempt.unsafeRunSync()
result2.isLeft shouldBe true
```

### Circuit Breaker Tests

```scala
import io.constellation.execution.CircuitBreakerConfig

// Test circuit breaker opening after failures
val config = CircuitBreakerConfig(
  failureThreshold = 3,
  resetDuration = 1.second
)

// Simulate multiple failures
(1 to 3).foreach { _ =>
  module.execute(input).attempt.unsafeRunSync()
}

// Circuit should now be open
val stats = circuitBreaker.stats
stats.state shouldBe CircuitState.Open

// Further calls should be rejected
val result = module.execute(input).attempt.unsafeRunSync()
result.isLeft shouldBe true
result.left.toOption.get shouldBe a[CircuitOpenException]

// Wait for reset
Thread.sleep(1100)

// Circuit should be half-open
val stats2 = circuitBreaker.stats
stats2.state shouldBe CircuitState.HalfOpen
```

## Error Reporting Best Practices

### 1. Always Provide Location Information

```scala
// GOOD: Include span for precise error location
CompileError.UndefinedVariable("textt", Some(Span(45, 50)))

// BAD: No location information
CompileError.UndefinedVariable("textt", None)
```

### 2. Include Context in Error Messages

```scala
// GOOD: Specific context
s"Type mismatch in argument 1 of Uppercase: expected String, got Int"

// BAD: Vague error
"Type mismatch"
```

### 3. Provide Actionable Suggestions

```scala
// GOOD: Tell user how to fix it
"Did you mean 'text'? Variable names are case-sensitive."

// BAD: Just state the problem
"Variable not found"
```

### 4. Use Structured Error Types

```scala
// GOOD: Use specific error types
case class TypeMismatchError(
  expected: CType,
  actual: CType,
  location: Option[Span]
) extends CompileError

// BAD: Generic errors
case class GenericError(message: String) extends CompileError
```

### 5. Test Error Paths

```scala
// GOOD: Dedicated tests for error cases
"TypeChecker" should "reject type mismatch in function arguments" in {
  val result = check("result = Uppercase(42)")
  result.isLeft shouldBe true
  result.left.toOption.get.head shouldBe a[CompileError.TypeMismatch]
}

// GOOD: Test error recovery
"Parser" should "recover from unclosed parenthesis" in {
  val result = parse("result = func(\nout result")
  result.isLeft shouldBe true
  result.left.toOption.get shouldBe a[CompileError.ParseError]
}
```

### 6. Format Errors Consistently

```scala
// Use ErrorFormatter for all user-facing errors
val formatted = ErrorFormatter(source).format(error, context)

// Terminal output
println(formatted.toPlainText)

// IDE integration
sendDiagnostic(formatted.toMarkdown)

// Logging
logger.error(formatted.toOneLine)
```

### 7. Aggregate Related Errors

```scala
// GOOD: Return all errors at once
def check(pipeline: Pipeline): Either[List[CompileError], TypedPipeline]

// BAD: Fail on first error (makes debugging slower)
def check(pipeline: Pipeline): Either[CompileError, TypedPipeline]
```

## Common Debugging Workflows

### Workflow 1: Fixing Parse Errors

1. **Read the error location** — Line and column point to problem
2. **Check for common syntax mistakes:**
   - Missing parentheses: `func(x` → `func(x)`
   - Missing commas: `func(x y)` → `func(x, y)`
   - Unclosed strings: `"text` → `"text"`
   - Invalid identifiers: `123var` → `var123`
3. **Look at the previous line** — Parse errors often point to line after the mistake
4. **Use IDE syntax highlighting** — Mismatched delimiters show up visually

### Workflow 2: Fixing Type Errors

1. **Read expected vs actual types** — Error shows what was expected and what was found
2. **Hover in VSCode to see inferred types** — Verify upstream types are correct
3. **Check function signatures:**
   ```scala
   // In Scala: see ModuleBuilder.metadata()
   FunctionSignature(
     "Uppercase",
     List(FunctionParameter("text", CType.CString)),
     CType.CString
   )
   ```
4. **Use type conversions:**
   - `ToString(value)` for String
   - `ToInt(value)` for Int
   - Int auto-promotes to Float
5. **Break complex expressions into steps:**
   ```constellation
   # Instead of:
   result = Complex(Transform(Validate(input)))

   # Break down:
   validated = Validate(input)
   transformed = Transform(validated)
   result = Complex(transformed)
   # Now you can see exactly where the type mismatch occurs
   ```

### Workflow 3: Fixing Runtime Errors

1. **Check the error message for underlying cause**
2. **Verify module registration:**
   ```scala
   constellation.setModule(myModule)
   ```
3. **Check input types match declarations:**
   ```json
   // For: in count: Int
   { "count": 42 }  // Not "42"
   ```
4. **Use fallback for unreliable modules:**
   ```constellation
   result = RiskyModule(data) with fallback: defaultValue
   ```
5. **Add retry for transient failures:**
   ```constellation
   result = UnreliableAPI(data) with retry: 3
   ```
6. **Check circuit breaker state:**
   ```scala
   circuitBreaker.stats.state  // Open, HalfOpen, Closed
   ```

### Workflow 4: Debugging DAG Issues

1. **Visualize the DAG:**
   ```scala
   val dag = compiler.compile(source).toOption.get
   println(DagRenderer.render(dag))  // ASCII visualization
   ```
2. **Check for cycles:**
   - Error will show cycle path: `a -> b -> c -> a`
   - Break the cycle by restructuring
3. **Verify execution order:**
   - DAG determines execution order based on dependencies
   - Use `Runtime.State.moduleStatuses` to see execution status
4. **Check data flow:**
   - Each node must have all inputs available before execution
   - Missing data indicates upstream failure

## Advanced Error Handling Patterns

### Pattern 1: Cascading Error Recovery

```constellation
# Try primary source, fallback to secondary, then default
primary = FetchPrimary(id) with fallback: None
secondary = FetchSecondary(id) with fallback: None
data = primary ?? secondary ?? defaultData
out data
```

### Pattern 2: Conditional Error Handling

```scala
// In module implementation
.implementation[Input, Output] { input =>
  IO {
    if (input.value < 0) {
      throw ValidationException("Value must be positive")
    }
    if (input.value > 1000) {
      throw ValidationException("Value too large")
    }
    Output(input.value)
  }.handleErrorWith {
    case e: ValidationException =>
      // Return default for validation errors
      IO.pure(Output(0))
    case e: IOException =>
      // Retry for IO errors
      IO.sleep(1.second) *> IO.raiseError(e)
    case e =>
      // Fail for unexpected errors
      IO.raiseError(e)
  }
}
```

### Pattern 3: Error Aggregation

```scala
// Collect multiple validation errors
def validateInputs(inputs: Map[String, CValue]): Either[List[ValidationError], Unit] = {
  val errors = List.newBuilder[ValidationError]

  inputs.get("count") match {
    case Some(CValue.CInt(n)) if n < 0 =>
      errors += ValidationError("count must be positive")
    case None =>
      errors += ValidationError("count is required")
    case _ => ()
  }

  inputs.get("name") match {
    case Some(CValue.CString(s)) if s.isEmpty =>
      errors += ValidationError("name cannot be empty")
    case None =>
      errors += ValidationError("name is required")
    case _ => ()
  }

  val allErrors = errors.result()
  if (allErrors.isEmpty) Right(()) else Left(allErrors)
}
```

### Pattern 4: Graceful Degradation

```constellation
# Try enhanced version, fall back to basic version
enhanced = EnhancedProcessor(data) with
  timeout: 5000
  fallback: None

basic = BasicProcessor(data) with fallback: None

result = enhanced ?? basic ?? minimalResult
out result
```

## Error Recovery Decision Tree

```
┌─────────────────────────┐
│   Compilation Failed    │
└────────────┬────────────┘
             │
             ├── Parse Error (E020-E029)
             │   ├── Check syntax near error line
             │   ├── Look for unclosed delimiters
             │   └── Verify keyword spelling
             │
             ├── Reference Error (E001-E009)
             │   ├── Check for typos ("Did you mean?")
             │   ├── Verify declaration order
             │   └── Check module registration
             │
             ├── Type Error (E010-E019)
             │   ├── Check expected vs actual types
             │   ├── Use type conversion functions
             │   └── Break complex expressions into steps
             │
             └── Semantic Error (E030-E039)
                 ├── Rename duplicate identifiers
                 └── Break circular dependencies

┌─────────────────────────┐
│   Execution Failed      │
└────────────┬────────────┘
             │
             ├── Module Not Found
             │   ├── Register module: constellation.setModule()
             │   └── Check name matches exactly
             │
             ├── Module Execution Error
             │   ├── Check error message for cause
             │   ├── Verify input data
             │   └── Use fallback or retry options
             │
             ├── Input Validation Error
             │   ├── Verify JSON types match declarations
             │   ├── Check all required inputs provided
             │   └── Validate input constraints
             │
             ├── Circuit Open
             │   ├── Wait for reset duration
             │   ├── Check underlying service
             │   └── Adjust circuit breaker config
             │
             └── Queue Full
                 ├── Implement backpressure
                 ├── Increase concurrency/queue size
                 └── Monitor queue depth
```

## Related

- [Language Error Messages](../../language/error-messages.md) — User-facing error documentation
- [Error Reference](../../api-reference/error-reference.md) — Complete error catalog
- [Type System](../../language/types.md) — Understanding constellation-lang types
- [Module Options](../../language/module-options.md) — Error handling with options (retry, fallback, etc.)
- [Testing Strategies](./testing.md) — Testing error scenarios
