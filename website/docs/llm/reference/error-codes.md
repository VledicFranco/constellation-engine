---
title: "Error Code Reference"
sidebar_position: 1
description: "Complete error code catalog with causes and solutions for quick lookup"
---

# Error Code Reference

Complete catalog of all error codes in Constellation Engine with causes and solutions. This is a quick lookup reference optimized for LLM consumption.

## Error Code Ranges

| Range | Category | Description |
|-------|----------|-------------|
| E001-E009 | Reference Errors | Undefined variables, functions, types, namespaces |
| E010-E019 | Type Errors | Type mismatches, incompatible operations |
| E020-E029 | Syntax Errors | Parse errors, unexpected tokens |
| E030-E039 | Semantic Errors | Duplicate definitions, circular dependencies |
| E900+ | Internal Errors | Compiler bugs (report these) |

## Severity Levels

| Level | Description | Impact |
|-------|-------------|--------|
| **Critical** | Compilation cannot proceed | No code generated |
| **Error** | Module execution fails | Pipeline stops |
| **Warning** | Potential issue | Execution continues |
| **Info** | Informational diagnostic | No impact |

---

## Compilation Error Codes

### Reference Errors (E001-E009)

#### E001: Undefined Variable

**Code:** `E001`
**Category:** Reference
**Severity:** Critical

**Cause:** Using a variable that hasn't been declared or assigned.

**Common Scenarios:**
- Typo in variable name: `textt` instead of `text`
- Using variable before declaring it
- Case mismatch: `userName` vs `username`
- Referencing non-existent field: `data.email` when field doesn't exist

**Solution:**
1. Check for typos (error includes "Did you mean?" suggestions)
2. Verify variable is declared before use
3. Add input declaration: `in variableName: Type`
4. Check case-sensitive spelling

**Example:**
```constellation
# WRONG
result = Uppercase(textt)  # 'textt' not defined

# CORRECT
in text: String
result = Uppercase(text)
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/declarations

---

#### E002: Undefined Function

**Code:** `E002`
**Category:** Reference
**Severity:** Critical

**Cause:** Calling a function that isn't registered with the compiler.

**Common Scenarios:**
- Typo in function name: `Upppercase` instead of `Uppercase`
- Function not registered in Scala code
- Case mismatch: `uppercase` vs `Uppercase`
- Missing namespace import

**Solution:**
1. Check for typos in function name
2. Register module in Scala:
   ```scala
   constellation.setModule(myModule)
   ```
3. Verify `ModuleBuilder.metadata()` name matches exactly:
   ```scala
   ModuleBuilder.metadata("Uppercase", ...)  // Must match usage
   ```
4. Add namespace import:
   ```constellation
   use stdlib.math
   result = math.add(x, y)
   ```

**Example:**
```constellation
# WRONG
result = Upppercase(text)  # Typo

# CORRECT
result = Uppercase(text)

# WRONG (missing namespace)
result = add(1, 2)

# CORRECT
use stdlib.math
result = math.add(1, 2)
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/functions

---

#### E003: Undefined Type

**Code:** `E003`
**Category:** Reference
**Severity:** Critical

**Cause:** Using a type name that isn't defined.

**Common Scenarios:**
- Typo in type name: `Integer` instead of `Int`
- Using undefined custom type
- Wrong bracket syntax: `List[T]` instead of `List<T>`

**Solution:**
1. Check built-in types: `String`, `Int`, `Float`, `Boolean`
2. Use correct collection syntax: `List<T>`, `Map<K, V>`, `Optional<T>`
3. Define custom types first:
   ```constellation
   type MyType = { id: Int, name: String }
   ```

**Example:**
```constellation
# WRONG
in count: Integer  # Should be 'Int'

# CORRECT
in count: Int

# WRONG
in items: List[String]  # Wrong brackets

# CORRECT
in items: List<String>
```

**Built-in Types:**
- Primitives: `String`, `Int`, `Float`, `Boolean`
- Collections: `List<T>`, `Map<K, V>`, `Optional<T>`
- Special: `Candidates<T>`, `Any`

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/types

---

#### E004: Undefined Namespace

**Code:** `E004`
**Category:** Reference
**Severity:** Critical

**Cause:** Using a namespace that isn't registered.

**Common Scenarios:**
- Typo in namespace: `stdlib.meth` instead of `stdlib.math`
- Namespace not loaded
- Custom namespace not registered

**Solution:**
1. Check spelling of namespace
2. Verify namespace is loaded with modules
3. Common namespaces: `stdlib`, `stdlib.math`, `stdlib.string`

**Example:**
```constellation
# WRONG
use stdlib.meth  # Typo

# CORRECT
use stdlib.math

# Usage
result = math.add(1, 2)
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/namespaces

---

#### E005: Ambiguous Function

**Code:** `E005`
**Category:** Reference
**Severity:** Critical

**Cause:** Multiple functions match the same name.

**Solution:**
1. Use fully qualified name: `stdlib.math.add`
2. Or use alias: `use stdlib.math as m`, then `m.add`

**Example:**
```constellation
# Ambiguous
result = add(x, y)  # Which 'add' function?

# Disambiguated
use stdlib.math
result = math.add(x, y)

# Or with alias
use stdlib.math as m
result = m.add(x, y)
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/namespaces

---

#### E006: Invalid Projection

**Code:** `E006`
**Category:** Reference
**Severity:** Critical

**Cause:** Trying to project a field that doesn't exist on a record type.

**Common Scenarios:**
- Typo in field name
- Field doesn't exist in record type
- Wrong record type

**Solution:**
1. Check available fields (listed in error message)
2. Verify record type definition
3. Use hover in VSCode to see available fields

**Example:**
```constellation
# WRONG
in data: { id: Int, name: String }
result = data[id, email]  # 'email' doesn't exist

# CORRECT
result = data[id, name]

# Or add field to type
in data: { id: Int, name: String, email: String }
result = data[id, email]
```

**Projection Syntax:** `record[field1, field2]` creates a new record with only specified fields.

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/expressions

---

#### E007: Invalid Field Access

**Code:** `E007`
**Category:** Reference
**Severity:** Critical

**Cause:** Trying to access a field that doesn't exist using dot notation.

**Common Scenarios:**
- Typo in field name
- Field not defined in record type
- Accessing field on non-record type

**Solution:**
1. Check available fields (listed in error message)
2. Verify upstream type definition
3. Add missing field to type definition

**Example:**
```constellation
# WRONG
in user: { id: Int, name: String }
email = user.email  # 'email' doesn't exist

# CORRECT
in user: { id: Int, name: String, email: String }
email = user.email
```

**Field Access Syntax:** `record.fieldName` accesses a single field.

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/expressions

---

### Type Errors (E010-E019)

#### E010: Type Mismatch

**Code:** `E010`
**Category:** Type
**Severity:** Critical

**Cause:** Actual type doesn't match expected type.

**Common Scenarios:**
- Passing wrong argument type: `Uppercase(42)` expects `String`, got `Int`
- Assigning incompatible value
- Returning wrong type from conditional
- Input JSON type doesn't match declaration

**Solution:**
1. Check function signature for expected types
2. Use type conversion functions:
   - `ToString(x)` — Convert to String
   - `ToInt(x)` — Convert Float to Int
   - Int auto-promotes to Float in arithmetic
3. Verify input declarations match data
4. Use hover in VSCode to see inferred types

**Example:**
```constellation
# WRONG
in count: Int
result = Uppercase(count)  # Expects String, got Int

# CORRECT
in count: Int
text = ToString(count)
result = Uppercase(text)
```

**Type Conversions:**
```constellation
# String conversions
message = ToString(count)  # Int -> String

# Numeric conversions
dollars = ToInt(price)  # Float -> Int (truncates)
total = quantity * 1.5  # Int -> Float (auto-promote)

# Optional handling
value = maybeValue ?? 0  # Extract with default
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/type-system

---

#### E011: Incompatible Operator

**Code:** `E011`
**Category:** Type
**Severity:** Critical

**Cause:** Operator cannot be applied to these types.

**Supported Operators:**
- Arithmetic (`+`, `-`, `*`, `/`): Int, Float
- Comparison (`==`, `!=`, `<`, `>`, `<=`, `>=`): Int, Float, String, Boolean
- Boolean (`and`, `or`, `not`): Boolean
- Merge (`+`): Records, Candidates

**Solution:**
1. Verify operand types support the operator
2. Use type conversion if needed
3. For arithmetic, use `stdlib.math` functions

**Example:**
```constellation
# WRONG
in age: String
isAdult = age > 18  # Can't compare String with Int

# CORRECT
in age: Int
isAdult = age >= 18
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/operators

---

#### E012: Incompatible Merge

**Code:** `E012`
**Category:** Type
**Severity:** Critical

**Cause:** Cannot merge these types with the `+` operator.

**Important:** In constellation-lang, `+` is for record/Candidates merge, NOT arithmetic.

**Valid Merge Operations:**
- Record + Record → Merged record
- Candidates<A> + Candidates<B> → Candidates<A + B> (element-wise)
- Candidates<A> + Record → Candidates (broadcast)
- Record + Candidates<A> → Candidates (broadcast)

**Solution:**
1. For numeric addition, use `stdlib.math.add(a, b)`
2. For record merge, ensure both are records
3. For Candidates merge, ensure compatible types

**Example:**
```constellation
# WRONG - Using + for arithmetic
in a: Int
in b: Int
sum = a + b  # + is for merging, not addition!

# CORRECT - Use stdlib.math
use stdlib.math
in a: Int
in b: Int
sum = math.add(a, b)

# CORRECT - Record merge
in data1: { x: Int }
in data2: { y: String }
merged = data1 + data2  # Creates { x: Int, y: String }
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/operators

---

#### E013: Unsupported Comparison

**Code:** `E013`
**Category:** Type
**Severity:** Critical

**Cause:** Comparison operator not supported for these types.

**Supported Comparisons:**
- `Int` with `Int`: All comparison operators
- `Float` with `Float`: All comparison operators
- `String` with `String`: All comparison operators (lexicographic)
- `Boolean` with `Boolean`: `==` and `!=` only

**Solution:**
1. Ensure both operands have compatible types
2. For complex types, compare specific fields

**Example:**
```constellation
# WRONG
in a: { x: Int }
in b: { y: String }
result = a == b  # Can't compare different record types

# CORRECT - Compare fields
match = (a.x == b.x) and (a.y == b.y)
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/operators

---

#### E014: Unsupported Arithmetic

**Code:** `E014`
**Category:** Type
**Severity:** Critical

**Cause:** Arithmetic operators not supported for these types.

**Supported Arithmetic:**
- `Int` and `Int` → `Int`
- `Float` and `Float` → `Float`
- `Int` and `Float` → `Float` (Int auto-promotes)

**Solution:**
1. Use numeric types (Int, Float) for arithmetic
2. Convert strings to numbers: `ToInt("42")`
3. Use `stdlib.math` functions for complex operations

**Example:**
```constellation
# WRONG
in text: String
result = text * 2  # Can't multiply String

# CORRECT
use stdlib.math
in count: Int
result = math.multiply(count, 2)
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/operators

---

#### E015: General Type Error

**Code:** `E015`
**Category:** Type
**Severity:** Critical

**Cause:** A type-related error occurred during compilation.

**Solution:**
1. Check all expressions have compatible types
2. Review error context for specific details
3. Break complex expressions into steps to isolate issue

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/type-system

---

#### E016: Invalid Option Value

**Code:** `E016`
**Category:** Type
**Severity:** Critical

**Cause:** Invalid value for a module call option.

**Option Constraints:**
- `retry`: must be >= 0
- `timeout`: must be > 0 (milliseconds)
- `delay`: must be > 0 (milliseconds)
- `cache`: must be > 0 (milliseconds)
- `concurrency`: must be > 0
- `throttle`: count must be > 0

**Solution:**
1. Check option value meets constraints
2. Use appropriate units (milliseconds for timeouts)

**Example:**
```constellation
# WRONG
result = Process(data) with retry: -1  # Negative retry

# CORRECT
result = Process(data) with retry: 3

# Common valid values
result = Api(data) with
  retry: 3,              # Retry 3 times
  timeout: 5000,         # 5 second timeout
  cache: 3600000,        # 1 hour cache
  fallback: default,     # Use default on failure
  concurrency: 10,       # Max 10 concurrent
  priority: 5            # Priority level
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/module-options

---

#### E017: Fallback Type Mismatch

**Code:** `E017`
**Category:** Type
**Severity:** Critical

**Cause:** Fallback value type doesn't match module's return type.

**Solution:**
1. Check module's return type (hover in VSCode)
2. Provide fallback value of matching type

**Example:**
```constellation
# WRONG
in id: Int
age = GetAge(id) with fallback: "Unknown"  # GetAge returns Int, not String

# CORRECT
age = GetAge(id) with fallback: 0

# Type-specific fallbacks
name = GetName(id) with fallback: "Unknown"     # String
flag = CheckStatus(id) with fallback: false     # Boolean
value = Fetch(id) with fallback: None           # Optional
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/module-options

---

### Syntax Errors (E020-E029)

#### E020: Parse Error

**Code:** `E020`
**Category:** Syntax
**Severity:** Critical

**Cause:** Invalid syntax that parser cannot understand.

**Common Causes:**
- Missing or extra parentheses
- Missing commas between arguments
- Typos in keywords
- Unclosed strings or brackets
- Invalid identifiers (starting with digit)

**Solution:**
1. Check indicated line and column
2. Look at previous line for unclosed delimiters
3. Verify matching parentheses/brackets/braces
4. Check keyword spelling: `in`, `out`, `type`, `use`

**Example:**
```constellation
# WRONG - Missing parenthesis
result = Uppercase(text
out result

# CORRECT
result = Uppercase(text)
out result

# WRONG - Missing comma
result = Add(x y)

# CORRECT
result = Add(x, y)

# WRONG - Unclosed string
message = "Hello
out message

# CORRECT
message = "Hello"
out message

# WRONG - Invalid identifier
in 123value: Int

# CORRECT
in value123: Int
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/syntax

---

#### E021: Unexpected Token

**Code:** `E021`
**Category:** Syntax
**Severity:** Critical

**Cause:** Parser found a token it didn't expect at this position.

**Solution:**
1. Remove invalid characters
2. Check for reserved operators in wrong contexts
3. Verify identifier naming rules (alphanumeric + underscore, can't start with digit)

**Example:**
```constellation
# WRONG
out @ result

# CORRECT
out result
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/syntax

---

### Semantic Errors (E030-E039)

#### E030: Duplicate Definition

**Code:** `E030`
**Category:** Semantic
**Severity:** Critical

**Cause:** Same name defined multiple times in same scope.

**Common Scenarios:**
- Duplicate input declaration
- Duplicate variable assignment
- Duplicate type definition

**Solution:**
1. Rename conflicting identifiers
2. Use different names for different variables

**Example:**
```constellation
# WRONG - Duplicate input
in x: Int
in x: String

# CORRECT
in x: Int
in y: String

# WRONG - Duplicate assignment
result = Step1(data)
result = Step2(data)

# CORRECT
intermediate = Step1(data)
result = Step2(intermediate)
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/declarations

---

#### E031: Circular Dependency

**Code:** `E031`
**Category:** Semantic
**Severity:** Critical

**Cause:** Variable depends on itself directly or transitively.

**Solution:**
1. Identify cycle in error message
2. Break cycle by restructuring computation
3. Remember: Constellation is a DAG (no cycles allowed)

**Example:**
```constellation
# WRONG - Circular dependency
in x: Int
a = b + 1
b = a + 1
out a

# CORRECT - Break the cycle
in x: Int
a = x + 1
b = a + 1
out b
```

**Documentation:** https://constellation-engine.dev/docs/constellation-lang/dag

---

### Internal Errors (E900+)

#### E900: Internal Compiler Error

**Code:** `E900`
**Category:** Internal
**Severity:** Critical

**Cause:** Unexpected error in the compiler (this is a bug).

**Solution:**
1. Report bug at https://github.com/VledicFranco/constellation-engine/issues
2. Include full source code that triggered error
3. Simplify script to find minimal reproduction case
4. Include Constellation Engine version

**This is not your fault** — it's a compiler bug that should be fixed.

---

## Runtime Error Codes

### Core Runtime Errors

#### TYPE_MISMATCH

**Code:** `TYPE_MISMATCH`
**Category:** type
**Severity:** Error

**Cause:** Value's type doesn't match expected type at runtime.

**Common Scenarios:**
- JSON input type doesn't match declaration
- Module returns unexpected type
- Type conversion failed

**Solution:**
1. Verify JSON input types match constellation-lang declarations:
   ```json
   // For: in count: Int
   { "count": 42 }  // Not "42"
   ```
2. Check module return types
3. Use appropriate CValue constructors in Scala

**JSON Type Mapping:**
- `String` → JSON string
- `Int` → JSON number (integer)
- `Float` → JSON number (decimal)
- `Boolean` → JSON boolean
- `List<T>` → JSON array
- Record → JSON object

---

#### TYPE_CONVERSION

**Code:** `TYPE_CONVERSION`
**Category:** type
**Severity:** Error

**Cause:** Failed to convert value from one type to another.

**Solution:**
1. Verify JSON inputs match declared types
2. Check list elements are homogeneous
3. Ensure struct fields match expected schema

---

#### NODE_NOT_FOUND

**Code:** `NODE_NOT_FOUND`
**Category:** compiler
**Severity:** Error

**Cause:** Internal compiler reference to non-existent node (likely a bug).

**Solution:**
1. Report as bug with source that triggered it
2. Simplify script to isolate which construct triggers error

---

#### UNDEFINED_VARIABLE

**Code:** `UNDEFINED_VARIABLE`
**Category:** compiler
**Severity:** Error

**Cause:** Script references undefined variable name.

**Solution:**
1. Check spelling (case-sensitive)
2. Ensure variable declared with `in`, assigned via `=`, or is output of prior function
3. Use "did you mean?" suggestions from compiler

---

#### CYCLE_DETECTED

**Code:** `CYCLE_DETECTED`
**Category:** compiler
**Severity:** Error

**Cause:** Circular dependency in DAG.

**Solution:**
1. Review data flow for circular references
2. Break cycle by introducing intermediate variable
3. Restructure pipeline to be acyclic

---

#### UNSUPPORTED_OPERATION

**Code:** `UNSUPPORTED_OPERATION`
**Category:** compiler
**Severity:** Error

**Cause:** Script uses unsupported language construct.

**Examples:**
- Merging incompatible types
- Projecting from non-record type
- Using unsupported operator

**Solution:**
1. Check constellation-lang documentation for supported operations
2. Verify operand types using hover in VSCode

---

#### MODULE_NOT_FOUND

**Code:** `MODULE_NOT_FOUND`
**Category:** runtime
**Severity:** Error

**Cause:** DAG references unregistered module.

**Solution:**
1. Register module before execution:
   ```scala
   constellation.setModule(myModule)
   ```
2. Verify module name matches exactly (case-sensitive):
   ```scala
   ModuleBuilder.metadata("Uppercase", ...)  // Must match usage
   ```
3. Register standard library:
   ```scala
   StdLib.allModules.values.toList.traverse(constellation.setModule)
   ```

---

#### MODULE_EXECUTION

**Code:** `MODULE_EXECUTION`
**Category:** runtime
**Severity:** Error

**Cause:** Module threw exception during execution.

**Solution:**
1. Check error message for underlying cause
2. Verify input data matches module expectations
3. For IO-based modules, ensure external services are reachable
4. Use resilience options:
   ```constellation
   result = RiskyModule(data) with fallback: defaultValue
   result = UnreliableModule(data) with retry: 3
   ```

---

#### INPUT_VALIDATION

**Code:** `INPUT_VALIDATION`
**Category:** runtime
**Severity:** Error

**Cause:** Input value failed validation.

**Common Causes:**
- Missing required input
- Value type doesn't match declared type
- Value outside acceptable range

**Solution:**
1. Provide all required inputs
2. Ensure JSON values match declared types
3. Check value constraints

**Input Type Mapping:**
```constellation
in count: Int       // Expects JSON number
in text: String     // Expects JSON string
in flag: Boolean    // Expects JSON boolean
in items: List<String>  // Expects JSON array
```

---

#### DATA_NOT_FOUND

**Code:** `DATA_NOT_FOUND`
**Category:** runtime
**Severity:** Error

**Cause:** Runtime attempted to read unpopulated data node.

**Solution:**
1. Check upstream module statuses in `Runtime.State.moduleStatuses`
2. Verify DAG compiled without warnings
3. May indicate compiler bug — report with source

---

#### RUNTIME_NOT_INITIALIZED

**Code:** `RUNTIME_NOT_INITIALIZED`
**Category:** runtime
**Severity:** Error

**Cause:** Operation attempted before runtime initialization.

**Solution:**
1. Ensure `ConstellationImpl.init` or `.builder().build()` completed
2. In `IOApp`, use `for`/`yield` or `flatMap` to sequence initialization

**Example:**
```scala
for {
  constellation <- ConstellationImpl.builder().build()
  result <- constellation.execute(source, inputs)
} yield result
```

---

#### VALIDATION_ERROR

**Code:** `VALIDATION_ERROR`
**Category:** runtime
**Severity:** Error

**Cause:** Multiple validation errors occurred.

**Solution:**
1. Address each error in the list individually
2. Start with first error (later errors may cascade)
3. Re-compile after each fix

---

## Lifecycle Exception Types

These are thrown as exceptions during execution lifecycle operations.

### CircuitOpenException

**Class:** `CircuitOpenException`
**Message:** `Circuit breaker open for module: {moduleName}`

**Cause:** Circuit breaker is in Open state due to repeated failures.

**Solution:**
1. Wait for circuit breaker's reset duration (default: 30 seconds)
2. Check underlying module for persistent failures:
   - External service down
   - Network connectivity issues
   - Configuration error
3. Monitor circuit state:
   ```scala
   val stats = circuitBreaker.stats
   println(s"State: ${stats.state}")  // Open, HalfOpen, or Closed
   ```
4. Adjust circuit breaker configuration:
   ```scala
   CircuitBreakerConfig(
     failureThreshold = 5,      // Open after 5 failures
     resetDuration = 60.seconds, // Wait 60s before retry
     halfOpenMaxProbes = 3       // Test with 3 requests
   )
   ```

**Circuit States:**
- **Closed:** Normal operation, tracking failures
- **Open:** Rejecting calls, waiting for reset duration
- **HalfOpen:** Testing with limited probe requests

---

### QueueFullException

**Class:** `QueueFullException`
**Message:** `Scheduler queue is full ({currentSize}/{maxSize})`

**Cause:** Bounded scheduler's queue at maximum capacity.

**Solution:**
1. Implement backpressure in application:
   ```scala
   execution.attempt.flatMap {
     case Right(result) => handleResult(result)
     case Left(_: QueueFullException) =>
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

---

### ShutdownRejectedException

**Class:** `ShutdownRejectedException`
**Message:** `Execution rejected: system is shutting down`

**Cause:** New execution submitted during graceful shutdown.

**Solution:**
1. This is expected during shutdown
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
       getCachedResult.orElse(IO.pure(ErrorResponse))
     case Left(err) => handleError(err)
   }
   ```

**Lifecycle States:**
- **Running:** Accepting new executions
- **Draining:** Completing in-flight, rejecting new
- **Stopped:** All executions complete, system stopped

---

## HTTP Error Codes

### 401 Unauthorized

**Response:**
```json
{
  "error": "Unauthorized",
  "message": "Missing or invalid Authorization header"
}
```

**Cause:** Authentication enabled but request lacks valid API key.

**Solution:**
1. Include `Authorization: Bearer <your-api-key>` header
2. Verify API key configured in `AuthConfig`
3. Check for whitespace or encoding issues

---

### 403 Forbidden

**Response:**
```json
{
  "error": "Forbidden",
  "message": "Insufficient permissions for POST"
}
```

**Cause:** API key valid but role doesn't permit HTTP method.

**Solution:**
1. Use key with appropriate role:
   - `ReadOnly`: GET only
   - `Execute`: GET and POST
   - `Admin`: All methods

---

### 429 Too Many Requests

**Headers:**
```
HTTP/1.1 429 Too Many Requests
Retry-After: 12
```

**Cause:** Client IP exceeded rate limit.

**Solution:**
1. Wait for duration indicated by `Retry-After` header
2. Reduce request frequency
3. Increase `requestsPerMinute` and `burst` in `RateLimitConfig` if legitimate

**Configuration:**
```scala
RateLimitConfig(
  requestsPerMinute = 100,  // Requests per minute per IP
  burst = 20                // Burst size
)
```

---

### 503 Service Unavailable

**Response:**
```json
{
  "ready": false,
  "reason": "System is draining"
}
```

**Cause:** Readiness probe returned not-ready.

**When This Happens:**
- System in `Draining` or `Stopped` lifecycle state
- Custom readiness check failed

**Solution:**
1. If during shutdown, this is expected
2. Check lifecycle state via `/health/detail`
3. Verify custom readiness checks

---

## Error Handling Strategies

### Module Call Options

Use these options to handle errors gracefully in constellation-lang:

#### retry

**Syntax:** `with retry: N`
**Purpose:** Retry failed module calls

**Example:**
```constellation
result = UnreliableAPI(data) with retry: 3
```

**When to use:** Transient failures (network issues, temporary service unavailability)

---

#### fallback

**Syntax:** `with fallback: defaultValue`
**Purpose:** Use default value on failure

**Example:**
```constellation
name = GetName(id) with fallback: "Unknown"
age = GetAge(id) with fallback: 0
```

**When to use:** Critical data where meaningful default exists

---

#### on_error

**Syntax:** `with on_error: strategy`
**Strategies:** `fail` (default), `log`, `skip`

**Examples:**
```constellation
# Fail (default) - propagate error, stop pipeline
critical = CriticalService(data) with on_error: fail

# Log - log error, continue with zero/empty value
optional = OptionalService(data) with on_error: log

# Skip - silently return zero/empty value
enrichment = EnrichmentService(data) with on_error: skip
```

**When to use:**
- `fail`: Critical operations where failure means result is invalid
- `log`: Optional enrichment where you want visibility into failures
- `skip`: Truly optional calls where failure is expected and uninteresting

---

#### timeout

**Syntax:** `with timeout: N` (milliseconds)
**Purpose:** Limit execution time

**Example:**
```constellation
result = SlowAPI(data) with timeout: 5000  # 5 seconds
```

**When to use:** Prevent hanging on slow operations

---

#### Combined Options

**Example:**
```constellation
result = RiskyAPI(data) with
  retry: 3,
  timeout: 5000,
  fallback: defaultValue,
  on_error: log
```

**Execution order:**
1. Try operation
2. If timeout, retry (up to retry count)
3. If all retries fail:
   - If fallback provided, use fallback value
   - Otherwise, apply on_error strategy

---

## Common Error Scenarios

### Scenario 1: Type Mismatch in Function Call

**Error:** E010 Type Mismatch

**Example:**
```constellation
in count: Int
result = Uppercase(count)  # Expects String, got Int
```

**Quick Fix:**
```constellation
in count: Int
text = ToString(count)
result = Uppercase(text)
```

---

### Scenario 2: Module Not Found

**Error:** MODULE_NOT_FOUND

**Cause:** Module not registered in Scala

**Quick Fix:**
```scala
// Register module
constellation.setModule(myModule)

// Or register all stdlib modules
StdLib.allModules.values.toList.traverse(constellation.setModule)
```

---

### Scenario 3: Undefined Variable

**Error:** E001 Undefined Variable

**Example:**
```constellation
result = Uppercase(textt)  # Typo: 'textt' instead of 'text'
```

**Quick Fix:**
1. Check "Did you mean?" suggestion in error
2. Fix typo: `Uppercase(text)`
3. Or declare variable: `in text: String`

---

### Scenario 4: Circular Dependency

**Error:** E031 Circular Dependency

**Example:**
```constellation
a = b + 1
b = a + 1  # Circular!
```

**Quick Fix:**
```constellation
# Break cycle with input
in x: Int
a = x + 1
b = a + 1
```

---

### Scenario 5: Wrong Merge Operator

**Error:** E012 Incompatible Merge

**Example:**
```constellation
in a: Int
in b: Int
sum = a + b  # + is for merging, not arithmetic!
```

**Quick Fix:**
```constellation
use stdlib.math
in a: Int
in b: Int
sum = math.add(a, b)
```

---

### Scenario 6: Invalid JSON Input

**Error:** INPUT_VALIDATION

**Example:**
```json
// For: in count: Int
{ "count": "42" }  // Wrong: string instead of number
```

**Quick Fix:**
```json
{ "count": 42 }  // Correct: number
```

---

### Scenario 7: Circuit Breaker Open

**Error:** CircuitOpenException

**Cause:** Too many consecutive failures

**Quick Fix:**
1. Wait for reset duration (default 30s)
2. Check underlying service is available
3. Monitor circuit state:
   ```scala
   circuitBreaker.stats.state  // Open, HalfOpen, or Closed
   ```

---

### Scenario 8: Missing Required Input

**Error:** INPUT_VALIDATION

**Example:**
```constellation
in text: String
in count: Int
```

```json
// Missing 'count'
{ "text": "hello" }
```

**Quick Fix:**
```json
// Provide all required inputs
{ "text": "hello", "count": 42 }
```

---

## Error Recovery Decision Tree

```
Error Occurred
├── Compilation Failed?
│   ├── E001-E009 (Reference)
│   │   ├── Check spelling (use "Did you mean?")
│   │   ├── Verify declaration order
│   │   └── Check module registration
│   ├── E010-E019 (Type)
│   │   ├── Check expected vs actual types
│   │   ├── Use type conversion functions
│   │   └── Break complex expressions into steps
│   ├── E020-E029 (Syntax)
│   │   ├── Check for unclosed delimiters
│   │   ├── Verify keyword spelling
│   │   └── Check identifier naming rules
│   └── E030-E039 (Semantic)
│       ├── Rename duplicate identifiers
│       └── Break circular dependencies
│
└── Execution Failed?
    ├── MODULE_NOT_FOUND
    │   ├── Register: constellation.setModule()
    │   └── Check name matches exactly
    ├── MODULE_EXECUTION
    │   ├── Check error message for cause
    │   ├── Verify input data
    │   └── Use fallback or retry options
    ├── INPUT_VALIDATION
    │   ├── Verify JSON types match declarations
    │   ├── Provide all required inputs
    │   └── Check value constraints
    ├── CircuitOpenException
    │   ├── Wait for reset duration
    │   ├── Check underlying service
    │   └── Adjust circuit breaker config
    └── QueueFullException
        ├── Implement backpressure
        ├── Increase concurrency/queue size
        └── Monitor queue depth
```

---

## Quick Troubleshooting Guide

### Problem: "Undefined variable"

**Check:**
1. Spelling (case-sensitive)
2. Variable declared before use
3. "Did you mean?" suggestion in error

---

### Problem: "Undefined function"

**Check:**
1. Function name spelling
2. Module registered in Scala: `constellation.setModule(myModule)`
3. Module name matches: `ModuleBuilder.metadata("Name", ...)`

---

### Problem: "Type mismatch"

**Check:**
1. Function signature (hover in VSCode)
2. Use type conversion: `ToString()`, `ToInt()`
3. JSON input types match declarations

---

### Problem: Module execution fails

**Check:**
1. Error message for underlying cause
2. Input data validity
3. External service availability
4. Add resilience: `with retry: 3` or `with fallback: default`

---

### Problem: Pipeline stops unexpectedly

**Check:**
1. Use `on_error: log` for optional operations
2. Add fallback values for critical data
3. Check circuit breaker state
4. Monitor queue depth

---

### Problem: Circuit breaker open

**Check:**
1. Wait for reset duration
2. Check underlying service health
3. Review failure patterns
4. Adjust thresholds if transient failures expected

---

## Related Documentation

- [Language Error Messages](../../language/error-messages.md) — User-facing error documentation
- [Error Reference](../../api-reference/error-reference.md) — Complete error catalog with API details
- [Error Handling Patterns](../patterns/error-handling.md) — Comprehensive error handling guide
- [Type System](../../language/types.md) — Understanding constellation-lang types
- [Module Options](../../language/module-options.md) — Error handling with retry, fallback, etc.

---

## Appendix: Error Code Summary Table

| Code | Name | Category | Quick Fix |
|------|------|----------|-----------|
| E001 | Undefined Variable | Reference | Check spelling, declare variable |
| E002 | Undefined Function | Reference | Register module, check name |
| E003 | Undefined Type | Reference | Use built-in types, define custom |
| E004 | Undefined Namespace | Reference | Check namespace spelling |
| E005 | Ambiguous Function | Reference | Use fully qualified name |
| E006 | Invalid Projection | Reference | Check available fields |
| E007 | Invalid Field Access | Reference | Verify field exists in type |
| E010 | Type Mismatch | Type | Use type conversion |
| E011 | Incompatible Operator | Type | Check operator support for types |
| E012 | Incompatible Merge | Type | Use stdlib.math for arithmetic |
| E013 | Unsupported Comparison | Type | Ensure compatible types |
| E014 | Unsupported Arithmetic | Type | Use numeric types |
| E015 | General Type Error | Type | Break down complex expressions |
| E016 | Invalid Option Value | Type | Check option constraints |
| E017 | Fallback Type Mismatch | Type | Match fallback to return type |
| E020 | Parse Error | Syntax | Check delimiters, keywords |
| E021 | Unexpected Token | Syntax | Remove invalid characters |
| E030 | Duplicate Definition | Semantic | Rename duplicate identifiers |
| E031 | Circular Dependency | Semantic | Break cycle in data flow |
| E900 | Internal Compiler Error | Internal | Report bug with source |

---

**Document Version:** 1.0
**Last Updated:** 2026-02-09
**For Constellation Engine:** Latest
