---
title: "Debugging"
sidebar_position: 25
description: "Comprehensive guide to debugging Constellation pipelines"
---

# Debugging

A comprehensive guide to finding and fixing issues in Constellation pipelines.

## Overview

Constellation provides multiple debugging tools:

| Tool | Use Case |
|------|----------|
| **Dashboard** | Visual DAG inspection, node status, execution history |
| **Step-through Execution** | Batch-by-batch execution with state inspection |
| **Execution Tracker** | Per-node timing and value capture |
| **Debug Mode** | Runtime type validation with configurable levels |
| **API Endpoints** | Programmatic access to execution state |
| **LSP Diagnostics** | Real-time editor feedback |

---

:::tip Start with the Dashboard
The dashboard at `http://localhost:8080/dashboard` is the fastest way to debug. Run your script, see which nodes fail (red border), and click them to inspect values and errors.
:::

## 1. Common Debugging Patterns

### Pattern: Isolate the Failing Step

When a pipeline fails, narrow down which step is causing the issue.

```constellation
# debug-isolate.cst
# Comment out later steps to find where failure occurs

@example("test-input")
in data: String

step1 = ProcessA(data)
# step2 = ProcessB(step1)  # Commented out
# step3 = ProcessC(step2)  # Commented out

out step1  # Test each step individually
```

### Pattern: Add Intermediate Outputs

Expose intermediate values to understand data flow.

```constellation
# debug-intermediate.cst

@example("raw data with issues")
in rawData: String

cleaned = Trim(rawData)
normalized = Lowercase(cleaned)
processed = ParseJson(normalized)

# Expose intermediate values for debugging
out cleaned      # See what Trim produces
out normalized   # See what Lowercase produces
out processed    # Final output
```

### Pattern: Type Inspection

When type errors occur, explicitly annotate types to catch mismatches early.

```constellation
# debug-types.cst

@example(42)
in value: Int

# Explicit type annotation helps catch errors
result: String = IntToString(value)

out result
```

---

## 2. Step-Through Execution

Constellation supports batch-by-batch execution for debugging, where modules at the same dependency level execute together.

### How Batches Work

```
Batch 0: Input data nodes (provided values)
Batch 1: Modules with no dependencies
Batch 2: Modules depending on Batch 1 outputs
Batch 3: Modules depending on Batch 2 outputs
...
```

### Viewing Batch Structure

The DAG visualization in the dashboard shows the execution hierarchy. Nodes at the same horizontal level execute in the same batch.

### Node States During Execution

| State | Description | Visual Indicator |
|-------|-------------|------------------|
| `Pending` | Not yet executed | Gray border |
| `Running` | Currently executing | Blue dashed border, pulsing |
| `Completed` | Successfully finished | Green solid border |
| `Failed` | Execution error | Red border |

### Using the Dashboard for Step-Through

1. **Open the Dashboard** at `http://localhost:8080/dashboard`
2. **Select a script** from the file browser
3. **View the DAG** to understand execution order
4. **Run the script** and observe node states update
5. **Click nodes** to see computed values

---

## 3. Inspecting Intermediate Values

### Via the Dashboard

When you run a script through the dashboard:

1. Each node shows its execution status with a colored border
2. **Hover over any node** to see a tooltip with:
   - Node kind (Input, Operation, Output, etc.)
   - Type signature
   - Computed value (truncated if large)
   - Execution duration
3. **Click a node** to open the details panel with full information

### Via the API

Get detailed execution information programmatically:

```bash
# Execute and get outputs
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in x: Int\nresult = Add(x, 10)\nout result",
    "inputs": {"x": 5}
  }'
```

Response includes execution status:

```json
{
  "success": true,
  "outputs": {"result": 15},
  "status": "completed",
  "executionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Via Execution History

```bash
# List recent executions
curl http://localhost:8080/api/v1/executions?limit=10

# Get details for a specific execution
curl http://localhost:8080/api/v1/executions/{executionId}

# Get DAG visualization with execution state
curl http://localhost:8080/api/v1/executions/{executionId}/dag
```

---

## 4. Using the Dashboard for Debugging

### Scripts View

The main debugging interface:

```
+------------------+----------------------------------------+-------------+
|  File Browser    |  Inputs Panel  |  Outputs Panel        | Node Details|
|                  +-----------------+----------------------+             |
|  docs/           |  name: [Alice]  |  {"greeting": ...}  |  Kind: Op   |
|    example.cst   |  count: [5]     |                      |  Type: Str  |
|    debug.cst     |                 |                      |  Value: ... |
|                  |     [Run]       |                      |             |
|                  +-----------------+----------------------+             |
|                  |       Code Editor       |    DAG       |             |
|                  |  in name: String        | (visual)     |             |
|                  |  greeting = Hello(name) |   [O]        |             |
|                  |  out greeting           |    |         |             |
|                  |                         |   [O]        |             |
+------------------+-------------------------+--------------+-------------+
```

### Key Dashboard Features

| Feature | How to Use | Debugging Value |
|---------|-----------|-----------------|
| **File Browser** | Navigate to `.cst` files | Find the script to debug |
| **Inputs Panel** | Fill in test values | Test with different inputs |
| **Outputs Panel** | View execution results | See final output or error |
| **Code Editor** | Edit script in place | Make quick fixes |
| **DAG Visualization** | View pipeline structure | Understand data flow |
| **Node Details** | Click any node | Inspect intermediate values |
| **Execution History** | Switch to History view | Compare past executions |

### Live Preview

As you type in the code editor:
- The DAG updates in real-time
- Compilation errors appear in the error banner
- Input forms update to match declared inputs

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl+Enter` | Execute the current script |
| `+` / `-` | Zoom DAG in / out |
| `Fit` | Fit DAG to viewport |
| `TB` / `LR` | Toggle layout direction |

---

## 5. Troubleshooting Failed Pipelines

### Execution Status Values

| Status | Meaning | Action |
|--------|---------|--------|
| `completed` | All outputs computed | Success |
| `suspended` | Waiting for more inputs | Provide missing inputs |
| `failed` | Error during execution | Check error message |

### Handling Suspended Executions

When a pipeline suspends due to missing inputs:

```bash
# List suspended executions
curl http://localhost:8080/executions

# Resume with additional inputs
curl -X POST http://localhost:8080/executions/{id}/resume \
  -H "Content-Type: application/json" \
  -d '{"additionalInputs": {"missingInput": "value"}}'
```

### Common Failure Patterns

| Symptom | Likely Cause | Solution |
|---------|--------------|----------|
| `Missing required input` | Input not provided | Add to inputs JSON |
| `Input type mismatch` | Wrong JSON type | Match expected type |
| `Module not found` | Typo in module name | Check case-sensitive spelling |
| `Output not found` | Output name mismatch | Verify `out` declaration |
| `Compilation failed` | Syntax error | Check LSP diagnostics |

---

## 6. Reading Error Messages

### Compilation Errors

Compilation errors include source location:

```
Line 3, Column 15: Type mismatch
  Expected: Int
  Found: String

  result = Add(name, 10)
               ^^^^
```

**Interpretation:**
- **Line/Column**: Exact location in source
- **Expected/Found**: The type conflict
- **Context**: The offending expression

### Runtime Errors

Runtime errors show the execution context:

```json
{
  "success": false,
  "error": "Module 'FetchUser' failed: Connection refused to api.example.com",
  "status": "failed"
}
```

**Interpretation:**
- **Module name**: Which step failed
- **Error message**: Root cause from the module

### API Error Responses

HTTP errors include structured information:

```json
{
  "error": "InputError",
  "message": "Input 'count' expected Int, got String",
  "requestId": "abc-123"
}
```

Use `requestId` for correlating with server logs.

---

## 7. Debugging Type Errors

### Common Type Error Patterns

#### Mismatched Parameter Types

```constellation
# ERROR: Add expects Int, got String
in text: String
result = Add(text, 10)  # Type error!
```

**Fix:** Use type conversion:

```constellation
in text: String
num = ParseInt(text)
result = Add(num, 10)
out result
```

#### Record Field Access Errors

```constellation
# ERROR: Field 'email' not found
in user: { name: String }
email = user.email  # Error: field doesn't exist
```

**Fix:** Check the record type definition:

```constellation
in user: { name: String, email: String }
email = user.email
out email
```

#### Optional Type Handling

```constellation
# ERROR: Cannot use Optional<String> where String expected
in maybeValue: Optional<String>
result = Uppercase(maybeValue)  # Error!
```

**Fix:** Use coalesce to provide default:

```constellation
in maybeValue: Optional<String>
value = maybeValue ?? "default"
result = Uppercase(value)
out result
```

:::warning
Always coalesce `Optional<T>` before passing to modules. `Uppercase(maybeValue)` will fail; use `Uppercase(maybeValue ?? "default")` instead.
:::

### Using Debug Mode for Type Validation

Set the `CONSTELLATION_DEBUG` environment variable:

| Value | Behavior |
|-------|----------|
| `off` | No validation (zero overhead) |
| `errors` | Log violations, continue execution (default) |
| `full` | Throw on violations (development) |

```bash
# Development mode - strict type checking
export CONSTELLATION_DEBUG=full
make server

# Production mode - log only
export CONSTELLATION_DEBUG=errors
make server
```

---

## 8. Performance Debugging

### Identifying Slow Modules

After execution, check the `/metrics` endpoint:

```bash
curl http://localhost:8080/metrics
```

Response includes timing data:

```json
{
  "cache": {
    "hits": 150,
    "misses": 12,
    "hitRate": 0.926,
    "evictions": 0
  },
  "server": {
    "uptime_seconds": 3600,
    "requests_total": 1000
  }
}
```

### Dashboard Node Timing

In the dashboard:
1. Run your pipeline
2. Click on any node
3. View `durationMs` in the details panel
4. Identify nodes taking longer than expected

### Execution History Analysis

Use the History view to compare execution times:

```bash
# Get execution with timing
curl http://localhost:8080/api/v1/executions/{id}
```

Response includes:

```json
{
  "startTime": 1704067200000,
  "endTime": 1704067201234,
  "nodeCount": 15
}
```

### Common Performance Issues

| Issue | Symptom | Solution |
|-------|---------|----------|
| No caching | Same requests repeated | Add `cache: 60s` option |
| Serial execution | Long total time | Pipeline parallelizes automatically |
| Large payloads | Slow serialization | Reduce data size, use projection |
| External API slow | High latency on one node | Add `timeout`, `retry` options |

### Adding Caching for Performance

```constellation
# Cache expensive operations
result = ExpensiveService(input) with cache: 300s
```

---

## 9. Logging and Tracing

### Server Logs

The server logs important events to stderr:

```bash
# Run with visible logs
make server 2>&1 | tee server.log
```

Log levels include:
- **INFO**: Normal operations
- **WARN**: Potential issues (type cast violations in ErrorsOnly mode)
- **ERROR**: Failures

### Request Tracing

Add request IDs for correlation:

```bash
curl -X POST http://localhost:8080/run \
  -H "X-Request-ID: my-trace-id-123" \
  -H "Content-Type: application/json" \
  -d '{"source": "...", "inputs": {}}'
```

Error responses include the request ID:

```json
{
  "error": "ExecutionError",
  "message": "...",
  "requestId": "my-trace-id-123"
}
```

### Execution Tracker

The runtime includes an execution tracker that captures per-node data:

```scala
// Programmatic access to execution traces
val tracker = ExecutionTracker.create
val execId = tracker.startExecution("my-dag")
// ... execution happens ...
val trace = tracker.getTrace(execId)
// trace.nodeResults contains per-node status, values, timing
```

Traces include:
- **Node status**: Pending, Running, Completed, Failed
- **Computed values**: JSON-serialized (truncated if large)
- **Timing**: Per-node `durationMs`
- **Errors**: Error messages for failed nodes

---

## 10. LSP Diagnostics

The Language Server Protocol integration provides real-time feedback in editors.

### VSCode Extension

Install the Constellation VSCode extension for:

- **Syntax highlighting** for `.cst` files
- **Real-time error diagnostics** as you type
- **Autocomplete** for module names and stdlib functions
- **Hover documentation** for types and functions
- **Run shortcut** (`Ctrl+Shift+R`) to execute scripts

### Diagnostic Categories

| Severity | Icon | Meaning |
|----------|------|---------|
| Error | Red | Prevents compilation |
| Warning | Yellow | May cause issues |
| Info | Blue | Suggestions |

### Common Diagnostics

| Message | Cause | Fix |
|---------|-------|-----|
| `Unknown module 'X'` | Module not registered | Check spelling, import namespace |
| `Type mismatch` | Incompatible types | Add conversion or fix declaration |
| `Unused variable` | Declared but never used | Remove or use in output |
| `Missing output` | No `out` declaration | Add `out variableName` |

---

## Quick Reference

### Debug Checklist

When a pipeline fails:

1. **Check the error message** for the failing module and cause
2. **Open the dashboard** and load the script
3. **Run the pipeline** and observe which nodes fail (red border)
4. **Click the failing node** to see the error details
5. **Check the inputs** - are they the right type?
6. **Isolate the step** by commenting out later operations
7. **Add intermediate outputs** to see values at each step
8. **Check the logs** for additional context

### Environment Variables

| Variable | Purpose | Values |
|----------|---------|--------|
| `CONSTELLATION_DEBUG` | Type validation level | `off`, `errors`, `full` |
| `CONSTELLATION_PORT` | Server port | Default: `8080` |

### Useful API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/run` | POST | Execute and debug pipelines |
| `/executions` | GET | List suspended executions |
| `/executions/{id}` | GET | Get execution details |
| `/executions/{id}/dag` | GET | Get DAG with execution state |
| `/metrics` | GET | Performance metrics |
| `/health/detail` | GET | Detailed diagnostics |

---

## Related Examples

- [Error Handling](error-handling.md) - `on_error` strategies
- [Retry and Fallback](retry-and-fallback.md) - Handling transient failures
- [Resilient Pipeline](resilient-pipeline.md) - Combining all resilience options
