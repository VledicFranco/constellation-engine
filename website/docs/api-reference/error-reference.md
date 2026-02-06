---
title: "Error Reference"
sidebar_position: 4
description: "Structured error codes and resolution guides"
---

# Error Reference

This document catalogs every error type in Constellation Engine with error codes, causes, and resolution steps.

## Error Structure

All Constellation errors extend the `ConstellationError` trait:

```scala
sealed trait ConstellationError extends Exception {
  def message: String
  def errorCode: String
  def context: Map[String, String]
  def category: String
  def toJson: Json
}
```

### JSON Format

Errors serialize to a consistent JSON shape:

```json
{
  "error": "TYPE_MISMATCH",
  "category": "type",
  "message": "Expected String but got Int",
  "context": {
    "expected": "String",
    "actual": "Int",
    "location": "argument 1 of Uppercase"
  }
}
```

### Error Categories

| Category | Prefix | When |
|----------|--------|------|
| `type` | `TYPE_` | Type checking and conversion failures |
| `compiler` | Various | Compilation-phase errors |
| `runtime` | Various | Execution-phase errors |
| `execution` | Exceptions | Lifecycle and scheduling errors |
| `http` | HTTP status codes | HTTP API errors |

---

## Quick Debugging Guide

### Most Common Errors

| Error | Likely Cause | Quick Fix |
|-------|--------------|-----------|
| "field not found" | Typo in field name | Check spelling, use autocomplete |
| "type mismatch" | Wrong type in operation | Check expected vs actual types |
| "module not found" | Missing module registration | Verify module is registered in Scala |
| "cannot unify types" | Incompatible merge/projection | Use explicit type annotation |

### Debugging Steps

1. **Read the error location** — Line and column point to the exact problem
2. **Check the expected type** — Error shows what was expected vs found
3. **Hover in VSCode** — See inferred types at any expression
4. **Simplify the expression** — Break complex expressions into steps

### Error Code Ranges

| Range | Category | Example |
|-------|----------|---------|
| E001-E099 | Parse errors | Syntax issues, missing tokens |
| E100-E199 | Type errors | Type mismatches, field access |
| E200-E299 | Semantic errors | Undefined variables, cycles |
| E300-E399 | Runtime errors | Module failures, timeouts |

---

## Type Errors

Category: `type`

### TYPE_MISMATCH

| Field | Value |
|-------|-------|
| **Class** | `TypeMismatchError` |
| **Code** | `TYPE_MISMATCH` |
| **Message** | `Expected {expected} but got {actual}` |
| **Context** | `expected`, `actual` |

**Cause:** A value's type does not match what was expected. Common scenarios:
- Passing a `String` where an `Int` is required
- Function return type does not match declared output type
- Merge operands have incompatible types

**Resolution:**
- Check the function signature to verify parameter types
- Ensure input declarations in your `.cst` script match the data you provide
- Use the compiler error position to locate the mismatch

### TYPE_CONVERSION

| Field | Value |
|-------|-------|
| **Class** | `TypeConversionError` |
| **Code** | `TYPE_CONVERSION` |
| **Message** | `Cannot convert {from} to {to}: {reason}` |
| **Context** | `from`, `to`, `reason` |

**Cause:** The runtime failed to convert a value from one type to another. Typically occurs when JSON input values cannot be coerced to the expected `CType`.

**Resolution:**
- Verify your JSON inputs match the declared types (e.g., send `42` not `"42"` for `Int`)
- Check that list elements are homogeneous
- Ensure struct fields match the expected schema

---

## Compiler Errors

Category: `compiler`

### NODE_NOT_FOUND

| Field | Value |
|-------|-------|
| **Class** | `NodeNotFoundError` |
| **Code** | `NODE_NOT_FOUND` |
| **Message** | `Node {nodeId} of type {nodeType} not found` |
| **Context** | `nodeId`, `nodeType` |

**Cause:** An internal compiler reference points to a node that does not exist in the DAG. This is typically an internal compiler error rather than a user error.

**Resolution:**
- Report this as a bug with the constellation-lang source that triggered it
- Simplify the script to isolate which construct triggers the error

### UNDEFINED_VARIABLE

| Field | Value |
|-------|-------|
| **Class** | `UndefinedVariableError` |
| **Code** | `UNDEFINED_VARIABLE` |
| **Message** | `Undefined variable: {variableName}` |
| **Context** | `variableName` |

**Cause:** The script references a variable name that has not been declared. Common scenarios:
- Typo in a variable name
- Using a variable before declaring it
- Referencing a variable from a different scope

**Resolution:**
- Check spelling — the compiler may suggest "did you mean?" alternatives
- Ensure the variable is declared with `in`, assigned via `=`, or is an output of a prior function call
- Variable names are case-sensitive

### CYCLE_DETECTED

| Field | Value |
|-------|-------|
| **Class** | `CycleDetectedError` |
| **Code** | `CYCLE_DETECTED` |
| **Message** | `Cycle detected involving nodes: {nodeIds}` |
| **Context** | `nodeIds` (comma-separated UUIDs) |

**Cause:** The compiled DAG contains a circular dependency. A module's output feeds back (directly or transitively) into its own input.

**Resolution:**
- Review the data flow in your script for circular references
- Break the cycle by introducing an intermediate variable or restructuring the pipeline

### UNSUPPORTED_OPERATION

| Field | Value |
|-------|-------|
| **Class** | `UnsupportedOperationError` |
| **Code** | `UNSUPPORTED_OPERATION` |
| **Message** | `Unsupported operation: {operation} — {reason}` |
| **Context** | `operation`, `reason` |

**Cause:** The script uses a language construct that is not supported in the current context. Examples:
- Merging incompatible types (e.g., `Int + String`)
- Projecting fields from a non-record type
- Using an unsupported operator

**Resolution:**
- Check the [constellation-lang documentation](../language/index.md) for supported operations
- Verify the types of operands using hover info in the VSCode extension

---

## Runtime Errors

Category: `runtime`

### MODULE_NOT_FOUND

| Field | Value |
|-------|-------|
| **Class** | `ModuleNotFoundError` |
| **Code** | `MODULE_NOT_FOUND` |
| **Message** | `Module not found: {moduleName}` |
| **Context** | `moduleName` |

**Cause:** The DAG references a module that has not been registered with the `Constellation` instance. The script compiled successfully (the function signature was known), but the runtime module implementation is missing.

**Resolution:**
- Register the module before execution: `constellation.setModule(myModule)`
- Verify the module name in `ModuleBuilder.metadata()` matches exactly (case-sensitive)
- Ensure all standard library modules are registered: `StdLib.allModules.values.toList.traverse(constellation.setModule)`

### MODULE_EXECUTION

| Field | Value |
|-------|-------|
| **Class** | `ModuleExecutionError` |
| **Code** | `MODULE_EXECUTION` |
| **Message** | `Module '{moduleName}' execution failed` |
| **Context** | `moduleName`, plus optional `cause` |

**Cause:** A module threw an exception during execution. The `cause` field (if present) contains the original exception.

**Resolution:**
- Check the module implementation for unhandled exceptions
- Verify the input data matches what the module expects
- For IO-based modules, ensure external services are reachable
- Check the `cause` context field for the underlying error message

### INPUT_VALIDATION

| Field | Value |
|-------|-------|
| **Class** | `InputValidationError` |
| **Code** | `INPUT_VALIDATION` |
| **Message** | `Input validation failed for '{inputName}': {reason}` |
| **Context** | `inputName`, `reason` |

**Cause:** An input value provided to the DAG failed validation. Common scenarios:
- Missing required input
- Value type does not match the declared input type
- Value is outside acceptable range (if module validates)

**Resolution:**
- Provide all inputs declared in the script's `in` declarations
- Ensure JSON values match declared types:
  - `String` → JSON string
  - `Int` → JSON number (integer)
  - `Boolean` → JSON boolean
  - `List<T>` → JSON array

### DATA_NOT_FOUND

| Field | Value |
|-------|-------|
| **Class** | `DataNotFoundError` |
| **Code** | `DATA_NOT_FOUND` |
| **Message** | `Data not found: {dataId} ({dataType})` |
| **Context** | `dataId`, `dataType` |

**Cause:** The runtime attempted to read a data node that has not been populated. This can happen if an upstream module failed silently or if the DAG structure is inconsistent.

**Resolution:**
- Check upstream module statuses in `Runtime.State.moduleStatuses`
- Verify the DAG compiled without warnings
- This may indicate a compiler bug — report with the source script

### RUNTIME_NOT_INITIALIZED

| Field | Value |
|-------|-------|
| **Class** | `RuntimeNotInitializedError` |
| **Code** | `RUNTIME_NOT_INITIALIZED` |
| **Message** | `Runtime not initialized: {reason}` |
| **Context** | `reason` |

**Cause:** An operation was attempted before the runtime was fully initialized.

**Resolution:**
- Ensure `ConstellationImpl.init` or `.builder().build()` has completed before calling execution methods
- In `IOApp`, use `for`/`yield` or `flatMap` to sequence initialization before execution

### VALIDATION_ERROR

| Field | Value |
|-------|-------|
| **Class** | `ValidationError` |
| **Code** | `VALIDATION_ERROR` |
| **Message** | `Validation failed: {errors}` (newline-separated) |
| **Context** | `errors` |

**Cause:** Multiple validation errors occurred. The `errors` list contains individual error messages.

**Resolution:**
- Address each error in the list individually
- This often occurs when multiple inputs are invalid simultaneously

---

## Execution Lifecycle Errors

These are thrown as exceptions (not `ConstellationError` subtypes) during execution lifecycle operations.

### CircuitOpenException

| Field | Value |
|-------|-------|
| **Class** | `CircuitOpenException` |
| **Message** | `Circuit breaker is open for module: {moduleName}` |

**Cause:** The circuit breaker for a module is in the Open state, meaning the module has exceeded its failure threshold and calls are being rejected to prevent cascading failures.

**Resolution:**
- Wait for the circuit breaker's `resetDuration` to elapse (default: 30 seconds)
- Check the underlying module for persistent failures (external service down, configuration error)
- Monitor circuit state via `circuitBreaker.stats`
- Adjust `CircuitBreakerConfig.failureThreshold` if the module has transient failures

### QueueFullException

| Field | Value |
|-------|-------|
| **Class** | `QueueFullException` |
| **Message** | `Scheduler queue is full (max: {maxQueueSize})` |

**Cause:** The bounded scheduler's queue has reached its maximum capacity. New tasks cannot be accepted.

**Resolution:**
- Increase `maxQueueSize` in the scheduler configuration
- Increase `maxConcurrency` to process tasks faster
- Implement backpressure in your application (reject or queue requests upstream)
- Monitor queue depth via `scheduler.stats.queuedCount`

### ShutdownRejectedException

| Field | Value |
|-------|-------|
| **Class** | `ShutdownRejectedException` |
| **Message** | `Execution rejected: system is shutting down` |

**Cause:** A new execution was submitted after `ConstellationLifecycle.shutdown()` was called. The system is draining in-flight executions and rejecting new ones.

**Resolution:**
- This is expected behavior during graceful shutdown
- Ensure your application stops submitting new work after initiating shutdown
- Check `lifecycle.state` before submitting — only submit when state is `Running`

---

## HTTP Error Responses

These are HTTP status codes returned by the API server, not `ConstellationError` types.

### 401 Unauthorized

```json
{
  "error": "Unauthorized",
  "message": "Missing or invalid Authorization header"
}
```

**Cause:** Authentication is enabled and the request either lacks an `Authorization: Bearer <key>` header or the provided key is not in the configured key map.

**Resolution:**
- Include `Authorization: Bearer <your-api-key>` header
- Verify the API key is correctly configured in `AuthConfig`
- Check for whitespace or encoding issues in the header value

### 403 Forbidden

```json
{
  "error": "Forbidden",
  "message": "Insufficient permissions for POST"
}
```

**Cause:** The API key is valid but the associated role does not permit the requested HTTP method.

**Resolution:**
- Use a key with the appropriate role (see [Security Model](../architecture/security-model.md) for role permissions)
- `ReadOnly` keys can only `GET`; `Execute` keys can `GET` and `POST`; `Admin` keys can use all methods

### 429 Too Many Requests

```
HTTP/1.1 429 Too Many Requests
Retry-After: 12
```

**Cause:** The client IP has exceeded the configured rate limit.

**Resolution:**
- Wait for the duration indicated by the `Retry-After` header
- Reduce request frequency
- If legitimate traffic, increase `requestsPerMinute` and `burst` in `RateLimitConfig`

### 503 Service Unavailable

```json
{
  "ready": false,
  "reason": "System is draining"
}
```

**Cause:** The readiness probe (`/health/ready`) returned not-ready. This occurs when:
- The system is in `Draining` or `Stopped` lifecycle state
- A custom readiness check failed

**Resolution:**
- If during shutdown, this is expected — the load balancer should route traffic elsewhere
- If unexpected, check custom readiness checks and lifecycle state via `/health/detail`

---

## constellation-lang Parse and Compile Errors

Parse and compile errors from the constellation-lang compiler use a separate error hierarchy (`CompileError`) with source position information. These are documented in detail in the [Error Messages Guide](../language/error-messages.md).

Common categories:

| Error | Example |
|-------|---------|
| `ParseError` | `Unexpected token at line 3, column 5` |
| `UndefinedVariable` | `Undefined variable: textt` (with "did you mean: text?") |
| `UndefinedFunction` | `Undefined function: Upppercase` (with suggestion) |
| `TypeMismatch` | `Expected Int in argument 1 of Add, got String` |
| `InvalidProjection` | `Field 'score' does not exist on record {name: String}` |
| `IncompatibleMerge` | `Cannot merge Int with String` |

All compile errors include:
- **Position** — line and column number
- **Error code** — E001-E900 (see `ErrorCode.scala`)
- **Suggestions** — "did you mean?" based on Levenshtein distance
- **Formatted output** — code snippet with caret marker pointing to the error location

## Related

- [HTTP API Overview](./http-api-overview.md) — REST endpoints and response formats
- [Programmatic API](./programmatic-api.md) — Scala API error handling patterns
- [Error Messages Guide](../language/error-messages.md) — constellation-lang compile-time errors
- [Security Model](../architecture/security-model.md) — HTTP error responses and access control
