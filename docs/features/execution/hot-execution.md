# Hot Execution

> **Path**: `docs/features/execution/hot-execution.md`
> **Parent**: [execution/](./README.md)

Compile and execute pipelines in a single request.

## Overview

Hot execution is the default mode for development. Source code is sent directly to the server, compiled on the fly, and executed immediately. The result returns in one response.

## When to Use Hot Execution

| Use Case | Why Hot Execution |
|----------|-------------------|
| Interactive development | Fast iteration without pipeline management |
| Ad-hoc queries | One-time transformations without registration |
| Testing and debugging | Immediate feedback on source changes |
| Dynamic pipeline generation | Pipelines constructed at runtime |
| Prototyping | Rapid experimentation |

## API

### Endpoint

```
POST /run
```

### Request

```json
{
  "source": "in text: String\nresult = Uppercase(text)\nout result",
  "inputs": {
    "text": "hello world"
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `source` | string | Yes | Constellation-lang source code |
| `inputs` | object | Yes | Input values keyed by variable name |

### Response (Completed)

```json
{
  "success": true,
  "outputs": {
    "result": "HELLO WORLD"
  },
  "structuralHash": "a1b2c3d4e5f6...",
  "status": "completed",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "resumptionCount": 0
}
```

| Field | Description |
|-------|-------------|
| `outputs` | Computed output values |
| `structuralHash` | Hash of compiled pipeline (for caching reference) |
| `status` | Always `completed` for successful hot execution |
| `executionId` | Unique identifier for this execution |
| `resumptionCount` | Always `0` for initial execution |

### Response (Suspended)

If inputs are missing, the pipeline suspends:

```json
{
  "success": true,
  "outputs": {},
  "structuralHash": "a1b2c3d4e5f6...",
  "status": "suspended",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "missingInputs": {
    "approvalCode": "CString"
  },
  "pendingOutputs": ["result"],
  "resumptionCount": 0
}
```

See [suspension.md](./suspension.md) for resumption details.

### Response (Failed)

```json
{
  "success": false,
  "error": "Compilation failed: Unknown function 'Uppercas'",
  "status": "failed"
}
```

Or for runtime errors:

```json
{
  "success": false,
  "error": "Module 'Uppercase' execution failed: null input",
  "status": "failed",
  "executionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

## Examples

### Basic Text Processing

```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in text: String\ncleaned = Trim(text)\nupper = Uppercase(cleaned)\nout upper",
    "inputs": { "text": "  hello world  " }
  }'
```

Response:
```json
{
  "success": true,
  "outputs": { "upper": "HELLO WORLD" },
  "status": "completed"
}
```

### Numeric Computation

```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in x: Int\nin y: Int\nsum = Add(x, y)\nproduct = Multiply(x, y)\nout sum\nout product",
    "inputs": { "x": 5, "y": 3 }
  }'
```

Response:
```json
{
  "success": true,
  "outputs": {
    "sum": 8,
    "product": 15
  },
  "status": "completed"
}
```

### Pipeline with Object Types

```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in userId: String\nuser = GetUser(userId)\nout user",
    "inputs": { "userId": "user-123" }
  }'
```

Response:
```json
{
  "success": true,
  "outputs": {
    "user": {
      "id": "user-123",
      "name": "Alice",
      "email": "alice@example.com"
    }
  },
  "status": "completed"
}
```

## Caching Behavior

Hot execution caches the compiled pipeline by structural hash:

1. Source is parsed and compiled to intermediate representation
2. Structural hash is computed from the IR (ignoring comments and formatting)
3. If hash exists in cache, cached pipeline is used
4. If hash is new, pipeline is stored for future reference

This means:
- **Repeated identical source** compiles once, executes many times
- **Formatting changes** do not trigger recompilation
- **Semantic changes** always trigger recompilation

The `structuralHash` in the response can be used with `/execute` for cold execution.

## Performance Characteristics

| Aspect | Typical Value | Notes |
|--------|---------------|-------|
| Compile time | 10-100ms | Depends on source complexity |
| Execute time | 1-1000ms | Depends on module operations |
| Total latency | 50-200ms | For typical pipelines |
| Cache hit | ~5ms overhead | Hash lookup only |

For production workloads requiring consistent low latency, use [cold execution](./cold-execution.md).

## Error Handling

### Compilation Errors

```json
{
  "success": false,
  "errors": [
    "Line 2: Unknown function 'Uppercas'",
    "Line 3: Type mismatch: expected Int, got String"
  ]
}
```

HTTP status: `400 Bad Request`

### Runtime Errors

```json
{
  "success": false,
  "error": "Module 'Divide' execution failed: division by zero",
  "status": "failed",
  "executionId": "..."
}
```

HTTP status: `200 OK` (the request succeeded; the execution failed)

### Timeout

```json
{
  "success": false,
  "error": "Compilation timed out after 30 seconds"
}
```

HTTP status: `200 OK`

## Request Tracing

Include `X-Request-ID` header for correlation:

```bash
curl -X POST http://localhost:8080/run \
  -H "X-Request-ID: my-trace-123" \
  -H "Content-Type: application/json" \
  -d '{"source": "...", "inputs": {}}'
```

The request ID appears in:
- Error responses (`requestId` field)
- Server logs
- Metrics (if tracing enabled)

## Components Involved

| Component | Module | File | Role |
|-----------|--------|------|------|
| HTTP Endpoint | `http-api` | `ConstellationRoutes.scala` | Route handling and JSON parsing |
| Execution Helper | `http-api` | `ExecutionHelper.scala` | Orchestrates compile + execute |
| Compiler | `lang-compiler` | `LangCompiler.scala` | Source to PipelineImage |
| Runtime | `runtime` | `Runtime.scala` | Module execution engine |
| Constellation | `runtime` | `Constellation.scala` | Top-level execution facade |
| Pipeline Store | `runtime` | `PipelineStore.scala` | Caches compiled pipelines |
| Suspension Store | `runtime` | `SuspensionStore.scala` | Persists suspended state |

## Related Documentation

- [PHILOSOPHY.md](./PHILOSOPHY.md) - Why hot execution exists
- [cold-execution.md](./cold-execution.md) - Pre-compiled execution for production
- [suspension.md](./suspension.md) - Resuming suspended executions
- [docs/http-api/execution.md](../../http-api/execution.md) - Full API reference
