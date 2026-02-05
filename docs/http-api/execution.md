# Execution Endpoints

> **Path**: `docs/http-api/execution.md`
> **Parent**: [http-api/](./README.md)

Compile and execute constellation-lang pipelines via REST.

## Endpoint Summary

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/compile` | POST | Compile source, return DAG metadata |
| `/run` | POST | Compile and execute in one call |
| `/execute` | POST | Execute a stored pipeline by reference |

## POST /compile

Compile constellation-lang source code and store the resulting pipeline image.

### Request

```json
{
  "source": "in text: String\nresult = Uppercase(text)\nout result",
  "name": "my-pipeline"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `source` | string | Yes | Constellation-lang source code |
| `name` | string | No | Pipeline name (creates alias if provided) |

### Response (Success)

```json
{
  "success": true,
  "structuralHash": "a1b2c3d4e5f6...",
  "syntacticHash": "f6e5d4c3b2a1...",
  "name": "my-pipeline"
}
```

### Response (Error)

```json
{
  "success": false,
  "errors": ["Line 2: Unknown function 'Uppercas'"]
}
```

### curl Example

```bash
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in text: String\nresult = Uppercase(text)\nout result",
    "name": "text-processor"
  }'
```

## POST /run

Compile and execute a pipeline in a single request. The compiled image is stored for future reference.

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
| `inputs` | object | Yes | Input values (JSON) |

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

### Response (Suspended)

When inputs are missing, the pipeline suspends:

```json
{
  "success": true,
  "outputs": {},
  "structuralHash": "a1b2c3d4e5f6...",
  "status": "suspended",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "missingInputs": {
    "text": "CString"
  },
  "pendingOutputs": ["result"],
  "resumptionCount": 0
}
```

### Response (Failed)

```json
{
  "success": false,
  "error": "Module 'Uppercase' execution failed: ...",
  "status": "failed",
  "executionId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### curl Example

```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in x: Int\nin y: Int\nsum = Add(x, y)\nout sum",
    "inputs": { "x": 5, "y": 3 }
  }'
```

## POST /execute

Execute a previously compiled pipeline by reference (name or structural hash).

### Request

```json
{
  "ref": "my-pipeline",
  "inputs": {
    "text": "hello world"
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `ref` | string | Yes | Pipeline name or 64-char structural hash |
| `inputs` | object | Yes | Input values (JSON) |

### Reference Resolution

- If `ref` is exactly 64 hex characters, it is treated as a structural hash
- Otherwise, it is treated as a pipeline alias name

### Response

Same format as `/run` response (without `structuralHash` field).

### curl Examples

```bash
# By name
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{
    "ref": "text-processor",
    "inputs": { "text": "hello" }
  }'

# By structural hash
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -d '{
    "ref": "a1b2c3d4e5f6789012345678901234567890123456789012345678901234abcd",
    "inputs": { "text": "hello" }
  }'
```

## Error Responses

All endpoints return structured errors:

```json
{
  "error": "NotFound",
  "message": "Pipeline 'my-pipeline' not found",
  "requestId": "abc-123"
}
```

### HTTP Status Codes

| Code | Error Type | Description |
|------|------------|-------------|
| 400 | BadRequest | Invalid request body, compilation errors |
| 404 | NotFound | Pipeline reference not found |
| 413 | PayloadTooLarge | Request body exceeds 10MB limit |
| 429 | TooManyRequests | Rate limit exceeded or queue full |
| 503 | ServiceUnavailable | Server shutting down |
| 500 | InternalServerError | Unexpected execution error |

## Request Tracing

Include `X-Request-ID` header for request correlation:

```bash
curl -X POST http://localhost:8080/run \
  -H "X-Request-ID: my-trace-123" \
  -H "Content-Type: application/json" \
  -d '{"source": "...", "inputs": {}}'
```

The `requestId` appears in error responses and server logs.

## Timeouts

Compilation has a 30-second timeout. If exceeded:

```json
{
  "success": false,
  "error": "Compilation timed out after 30 seconds"
}
```

## See Also

- [suspension.md](./suspension.md) - Resume suspended executions
- [pipelines.md](./pipelines.md) - Pipeline management and versioning
- [security.md](./security.md) - Authentication for protected endpoints
