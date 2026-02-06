---
title: "Troubleshooting"
sidebar_position: 3
description: "Common issues and solutions"
---

# Troubleshooting Guide

This guide covers common issues encountered when using Constellation Engine and how to resolve them.

## Table of Contents

- [Compilation Errors](#compilation-errors)
- [Runtime Errors](#runtime-errors)
- [VSCode Extension Issues](#vscode-extension-issues)
- [HTTP API Issues](#http-api-issues)
- [Debug Mode](#debug-mode)
- [Getting Help](#getting-help)

---

## Compilation Errors

:::warning Case Sensitivity
Constellation module names are **case-sensitive**. `Uppercase`, `uppercase`, and `UPPERCASE` are three different names. Always use exact PascalCase for module names as defined in your Scala code.
:::

### "Module 'X' not found"

**Error Code:** `MODULE_NOT_FOUND`

**Error Message:**
```
Module 'Uppercas' not found in namespace
```

**Cause:** The module is not registered in the Constellation instance. This typically happens when:
- The module name is misspelled (case-sensitive!)
- The module wasn't added to the runtime
- The function signature wasn't registered with the compiler

**Solution:**
1. Check the module name spelling - names are case-sensitive
2. Ensure the module is registered:
   ```scala
   import io.constellation._

   val uppercase = ModuleBuilder
     .metadata("Uppercase", "Converts text to uppercase", 1, 0)
     .implementationPure[TextInput, TextOutput] { input =>
       TextOutput(input.text.toUpperCase)
     }
     .build

   // Register the module
   constellation.setModule(uppercase)
   ```
3. If using the compiler, register the function signature:
   ```scala
   val compiler = LangCompiler.builder
     .withFunction(FunctionSignature(
       name = "Uppercase",
       params = List("text" -> SemanticType.SString),
       returns = SemanticType.SString,
       moduleName = "Uppercase"
     ))
     .build
   ```

---

### "Type mismatch: expected X, got Y"

**Error Code:** `TYPE_MISMATCH`

**Error Message:**
```
[TYPE_MISMATCH] Expected String, but got Int [field=name, module=Uppercase]
```

**Cause:** Input or output types don't match between modules in your pipeline.

**Solution:**
1. Check that your module's input types match the output types of upstream modules
2. Verify your field names match exactly (case-sensitive)
3. Use type declarations to explicitly specify types:
   ```
   type UserData = {
     name: String,
     age: Int
   }

   in userData: UserData
   result = ProcessUser(userData)
   ```

---

### "Undefined variable: X"

**Error Code:** `UNDEFINED_VARIABLE`

**Error Message:**
```
Undefined variable: resut
```

**Cause:** A variable is referenced before it's defined, or there's a typo in the variable name.

**Solution:**
1. Check for typos in variable names
2. Ensure variables are defined before use
3. Verify the variable is in scope (not defined inside a branch/conditional only)

Example fix:
```
# Wrong - typo in variable name
text = "hello"
result = Uppercase(txt)  # Error: txt not defined

# Correct
text = "hello"
result = Uppercase(text)
```

---

### "Cycle detected in DAG"

**Error Code:** `CYCLE_DETECTED`

**Error Message:**
```
Cycle detected in DAG involving nodes: node-1, node-2, node-3
```

**Cause:** Circular dependency between modules - module A depends on module B, which depends on module A.

**Solution:**
1. Review your pipeline for circular references
2. Use the DAG visualizer to identify the cycle
3. Restructure the pipeline to break the cycle:

```
# Wrong - circular dependency
a = ProcessA(b)
b = ProcessB(a)  # Error: cycle

# Correct - break the cycle
initial = GetInitial()
a = ProcessA(initial)
b = ProcessB(a)
```

---

### "Parse error at line X, column Y"

**Error Message:**
```
Parse error at line 3, column 15: Expected '=' but found '('
```

**Cause:** Syntax error in your constellation-lang code.

**Common causes:**
- Missing `=` in assignments
- Using reserved keywords as variable names (`and`, `or`, `not`, `when`, `if`, `else`)
- Unclosed brackets or parentheses
- Invalid characters in identifiers

**Solution:**
```
# Wrong - missing equals
result Uppercase(text)

# Correct
result = Uppercase(text)

# Wrong - reserved keyword as variable
and = true  # Error: 'and' is reserved

# Correct
andResult = true
```

---

### "Cannot merge records with incompatible fields"

**Error Message:**
```
Cannot merge Candidates with different lengths: left has 5 elements, right has 3 elements
```

**Cause:** Attempting to merge two Candidates lists with different sizes.

**Solution:**
1. Ensure both Candidates have the same length before merging
2. Use filtering or transformation to align sizes:

```
# Wrong - different sized lists
candidates1 = GetCandidates(5)
candidates2 = GetOtherCandidates(3)
merged = candidates1 + candidates2  # Error

# Correct - use Record + Candidates broadcast instead
record = GetSingleRecord()
merged = candidates1 + record  # Broadcasts record to all candidates
```

---

## Runtime Errors

### "Failed to find data node in init data"

**Error Code:** `DATA_NOT_FOUND`

**Error Message:**
```
Data with ID abc-123 not found
```

**Cause:** A data node was referenced but not properly initialized. This can happen with:
- Inline transforms (string interpolation, lambdas)
- Conditional branches not being executed

**Solution:**
1. Ensure all required inputs are provided
2. Check that inline transforms have their dependencies satisfied
3. Verify the DAG was compiled correctly

---

### "Module execution failed"

**Error Code:** `MODULE_EXECUTION`

**Error Message:**
```
Module 'ProcessData' execution failed: NullPointerException
```

**Cause:** An exception occurred within a module's implementation.

**Solution:**
1. Check your module's implementation for null handling
2. Validate inputs before processing:
   ```scala
   val myModule = ModuleBuilder
     .metadata("MyModule", "Description", 1, 0)
     .implementationPure[Input, Output] { input =>
       require(input.text != null, "text cannot be null")
       // ... processing
     }
     .build
   ```
3. Use Option types for nullable values
4. Check server logs for full stack trace

---

### "Input validation failed"

**Error Code:** `INPUT_VALIDATION`

**Error Message:**
```
Input 'userId' validation failed: must be positive integer
```

**Cause:** The provided input doesn't meet the validation requirements.

**Solution:**
1. Check the expected input format in the API documentation
2. Ensure JSON types match (strings vs numbers vs booleans)
3. Validate inputs before sending:
   ```json
   {
     "inputs": {
       "userId": 123,          // Correct: integer
       "userId": "123"         // Wrong: string
     }
   }
   ```

---

### "Runtime not initialized"

**Error Code:** `RUNTIME_NOT_INITIALIZED`

**Error Message:**
```
Runtime not initialized: modules not loaded
```

**Cause:** Attempting to execute a DAG before the runtime is fully initialized.

**Solution:**
1. Ensure modules are registered before compiling
2. Wait for server startup to complete
3. Check initialization order:
   ```scala
   // Correct order
   val constellation = ConstellationImpl.init
   modules.foreach(m => constellation.setModule(m))
   val compiled = compiler.compile(source, "my-dag")
   constellation.run(compiled.pipeline, inputs)  // Run after setup
   ```

---

### "Type conversion failed"

**Error Code:** `TYPE_CONVERSION`

**Error Message:**
```
Cannot convert from JSON Array to CProduct: expected object
```

**Cause:** JSON input doesn't match the expected Constellation type structure.

**Solution:**
1. Check input JSON matches expected type structure
2. Arrays should use `Candidates<T>` or `List<T>` types
3. Objects should use record types `{ field: Type }`

```json
// For type: { name: String, age: Int }
{
  "name": "Alice",
  "age": 30
}

// For type: Candidates<{ name: String }>
[
  { "name": "Alice" },
  { "name": "Bob" }
]
```

---

## VSCode Extension Issues

### Extension not connecting to server

**Symptoms:**
- "Connection refused" errors
- No autocomplete or diagnostics
- LSP features not working

**Cause:** Server not running or using wrong port.

**Solution:**
1. Start the server:
   ```bash
   make server       # Or: ./scripts/dev.ps1 -ServerOnly
   ```
2. Check the port configuration in VS Code settings:
   - Default: `http://localhost:8080`
   - Check `constellation.lspPort` setting
3. Verify server is responding:
   ```bash
   curl http://localhost:8080/health
   ```
4. Check for port conflicts:
   ```bash
   # Windows
   netstat -ano | findstr :8080

   # macOS/Linux
   lsof -i :8080
   ```

---

### DAG visualization not updating

**Symptoms:**
- DAG panel shows stale data
- Changes in script not reflected in visualization

**Cause:** WebSocket connection lost or compilation errors.

**Solution:**
1. Check for compilation errors in the Problems panel
2. Save the file (Ctrl+S) to trigger recompilation
3. Reload the DAG visualizer:
   - Press `Ctrl+Shift+D` to refresh
   - Or close and reopen the DAG panel
4. Check WebSocket connection in Developer Tools (Help > Toggle Developer Tools)

---

### Autocomplete not working

**Symptoms:**
- No suggestions appear when typing
- Function signatures not shown

**Cause:** LSP server not initialized or file not recognized.

**Solution:**
1. Ensure file has `.cst` extension
2. Check that LSP is connected (status bar shows "Constellation")
3. Restart the LSP:
   - Command Palette (`Ctrl+Shift+P`)
   - Type "Restart Extension Host"
4. Verify modules are registered:
   ```bash
   curl http://localhost:8080/modules
   ```

---

### Step-through debugging not working

**Symptoms:**
- Step buttons don't respond
- Execution state not highlighted

**Cause:** Debugging session not started or DAG not compiled.

**Solution:**
1. Ensure script compiles without errors
2. Start debugging session:
   - Click "Start Debug" in Script Runner panel
   - Or use keyboard shortcut
3. Wait for initial compilation before stepping
4. Check that the server supports step-through execution

---

## HTTP API Issues

### 400 Bad Request on /execute

**Error Response:**
```json
{
  "error": "INPUT_VALIDATION",
  "message": "Input 'text' validation failed: missing required field"
}
```

**Cause:** Request body doesn't match expected format.

**Solution:**
1. Check required inputs in the DAG specification
2. Ensure JSON structure is correct:
   ```bash
   curl -X POST http://localhost:8080/execute \
     -H "Content-Type: application/json" \
     -d '{
       "ref": "my-pipeline",
       "inputs": {
         "text": "hello world",
         "count": 5
       }
     }'
   ```
3. Verify input types match (strings, numbers, booleans)

---

### 404 Not Found for Pipeline

**Error Response:**
```json
{
  "error": "PIPELINE_NOT_FOUND",
  "message": "Pipeline 'my-pipelin' not found"
}
```

**Cause:** Pipeline name misspelled or not compiled.

**Solution:**
1. Check pipeline name spelling (case-sensitive)
2. List available pipelines:
   ```bash
   curl http://localhost:8080/pipelines
   ```
3. Compile the pipeline before executing

---

### 500 Internal Server Error

**Error Response:**
```json
{
  "error": "INTERNAL_ERROR",
  "message": "An unexpected error occurred"
}
```

**Cause:** Server-side exception during execution.

**Solution:**
1. Check server logs for full error details
2. Enable debug mode for more information (see below)
3. Verify all modules are correctly implemented
4. Check for resource issues (memory, connections)

---

### WebSocket connection closes unexpectedly

**Symptoms:**
- LSP connection drops frequently
- "WebSocket disconnected" messages

**Cause:** Network issues or idle timeout.

**Solution:**
1. Check network stability
2. The extension automatically reconnects
3. If persistent, check server logs for errors
4. Verify no firewall is blocking WebSocket connections

---

## Debug Mode

:::tip Enable Debug Mode First
When investigating any issue, enabling debug mode should be your first step. It provides detailed type validation errors and diagnostic logging that can quickly pinpoint the root cause.
:::

Constellation Engine includes a debug mode that provides additional runtime validation and logging.

### Enabling Debug Mode

Set the `CONSTELLATION_DEBUG` environment variable:

```bash
# Linux/macOS
export CONSTELLATION_DEBUG=true
make server

# Windows PowerShell
$env:CONSTELLATION_DEBUG = "true"
.\scripts\dev.ps1 -ServerOnly

# Windows CMD
set CONSTELLATION_DEBUG=true
make server
```

### Debug Mode Features

When enabled, debug mode provides:

1. **Runtime type validation**: `safeCast` operations throw detailed errors instead of ClassCastException
2. **Debug assertions**: `debugAssert` checks are evaluated
3. **Debug logging**: Additional diagnostic messages via `debugLog`

### Debug Output

Debug messages are written to `stderr` with the prefix `[CONSTELLATION_DEBUG]`:

```
[CONSTELLATION_DEBUG] Processing module: Uppercase
[CONSTELLATION_DEBUG] Input type validated: String
[CONSTELLATION_DEBUG] Execution complete
```

### TypeMismatchError in Debug Mode

When debug mode catches a type error:

```
[TYPE_MISMATCH] Expected Long, but got String [location=HOF lambda argument extraction, value=hello world]
```

### Inspecting Pipeline State

Use the HTTP API to inspect pipeline state:

```bash
# Get pipeline metadata
curl http://localhost:8080/pipelines/my-pipeline

# List all stored pipelines
curl http://localhost:8080/pipelines
```

### Structured Logging

Constellation uses log4cats for structured logging. Configure log levels in `application.conf`:

```hocon
logging {
  level = "DEBUG"  # DEBUG, INFO, WARN, ERROR
  format = "json"  # json or text
}
```

---

## Getting Help

If you can't resolve an issue using this guide:

### GitHub Issues

:::note Before Opening an Issue
Search existing issues first - your problem may already have a solution. Include the exact error message, your constellation-lang code, and reproduction steps to get faster help.
:::

Report bugs and request features:
- **Repository:** [VledicFranco/constellation-engine](https://github.com/VledicFranco/constellation-engine)
- **Issues:** [GitHub Issues](https://github.com/VledicFranco/constellation-engine/issues)

When reporting an issue, include:
1. Error message (exact text)
2. Constellation-lang code (if applicable)
3. Environment details (OS, Java version, Scala version)
4. Steps to reproduce

### Documentation

- [Getting Started Tutorial](../getting-started/tutorial.md) - Step-by-step introduction
- [Language Reference](../language/index.md) - Full language documentation
- [API Guide](../api-reference/programmatic-api.md) - Programmatic usage
- [Architecture](../architecture/technical-architecture.md) - Technical details

### Community

- Check existing [GitHub Issues](https://github.com/VledicFranco/constellation-engine/issues) for similar problems
- Review [closed issues](https://github.com/VledicFranco/constellation-engine/issues?q=is%3Aclosed) for solutions to common problems
