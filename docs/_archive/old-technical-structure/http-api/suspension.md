# Suspension Management

> **Path**: `docs/http-api/suspension.md`
> **Parent**: [http-api/](./README.md)

Manage suspended pipeline executions and resume them with additional inputs.

## Overview

When a pipeline encounters missing inputs, it **suspends** instead of failing. The execution state is persisted and can be resumed later when the missing inputs become available.

## Endpoint Summary

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/executions` | GET | List suspended executions |
| `/executions/{id}` | GET | Get suspension detail |
| `/executions/{id}/resume` | POST | Resume with additional inputs |
| `/executions/{id}` | DELETE | Delete a suspended execution |

## Suspension Flow

```
1. POST /run with partial inputs
   -> Response: status="suspended", executionId="abc-123", missingInputs={...}

2. GET /executions/abc-123
   -> View what inputs are still needed

3. POST /executions/abc-123/resume with additional inputs
   -> Response: status="completed" (or "suspended" again if more inputs needed)
```

## GET /executions

List all suspended executions.

```bash
curl http://localhost:8080/executions
```

**Response:**

```json
{
  "executions": [
    {
      "executionId": "550e8400-e29b-41d4-a716-446655440000",
      "structuralHash": "a1b2c3d4e5f6...",
      "resumptionCount": 0,
      "missingInputs": {
        "userId": "CString",
        "amount": "CInt"
      },
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ]
}
```

| Field | Description |
|-------|-------------|
| `executionId` | UUID for this execution |
| `structuralHash` | Pipeline version that was executed |
| `resumptionCount` | How many times this has been resumed |
| `missingInputs` | Map of missing input names to their types |
| `createdAt` | When the suspension was created |

## GET /executions/{id}

Get details of a specific suspended execution.

```bash
curl http://localhost:8080/executions/550e8400-e29b-41d4-a716-446655440000
```

**Response:** Same format as list entries.

## POST /executions/{id}/resume

Resume a suspended execution with additional inputs.

### Request

```json
{
  "additionalInputs": {
    "userId": "user-123",
    "amount": 500
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `additionalInputs` | object | Input values to provide (by variable name) |
| `resolvedNodes` | object | Directly resolve intermediate nodes (advanced) |

### Response (Completed)

```json
{
  "success": true,
  "outputs": {
    "result": "Transaction processed"
  },
  "status": "completed",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "resumptionCount": 1
}
```

### Response (Still Suspended)

If the resumed execution needs more inputs:

```json
{
  "success": true,
  "outputs": {},
  "status": "suspended",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "missingInputs": {
    "approvalCode": "CString"
  },
  "pendingOutputs": ["result"],
  "resumptionCount": 1
}
```

### curl Example

```bash
curl -X POST http://localhost:8080/executions/550e8400-e29b-41d4-a716-446655440000/resume \
  -H "Content-Type: application/json" \
  -d '{
    "additionalInputs": {
      "userId": "user-123",
      "amount": 500
    }
  }'
```

## DELETE /executions/{id}

Delete a suspended execution (discard the saved state).

```bash
curl -X DELETE http://localhost:8080/executions/550e8400-e29b-41d4-a716-446655440000
```

**Response:** `{"deleted": true}`

## Auto-Save and Auto-Delete

- **Auto-save:** When an execution suspends (via `/run`, `/execute`, or `/resume`), it is automatically saved to the suspension store.
- **Auto-delete:** When an execution completes after a resume, the suspended state is automatically deleted.
- **Re-suspension:** If a resumed execution suspends again, the old entry is deleted and a new one is saved with the updated state.

## Error Responses

### Input Errors

```json
{
  "success": false,
  "error": "Input error: Type mismatch for 'amount': expected CInt, got string"
}
```

| Error Type | Description |
|------------|-------------|
| `InputTypeMismatchError` | Provided value doesn't match expected type |
| `InputAlreadyProvidedError` | Attempting to provide an input that was already given |
| `UnknownNodeError` | Input name not found in pipeline |
| `NodeTypeMismatchError` | Resolved node value has wrong type |
| `NodeAlreadyResolvedError` | Node was already computed |

### Concurrency Errors

```json
{
  "error": "ResumeInProgress",
  "message": "A resume operation is already in progress for execution '550e8400-...'"
}
```

This prevents race conditions when multiple clients try to resume the same execution.

### Pipeline Changed

```json
{
  "success": false,
  "error": "Pipeline error: Pipeline was reloaded; suspension is no longer valid"
}
```

If the underlying pipeline was hot-reloaded with a different structural hash, the suspended state may be invalid.

## Advanced: Resolving Nodes Directly

The `resolvedNodes` field allows you to provide values for intermediate computed nodes, not just declared inputs:

```json
{
  "resolvedNodes": {
    "preprocessedData": {"items": [1, 2, 3], "count": 3}
  }
}
```

This is useful when:
- An external system has already computed part of the pipeline
- You want to inject cached results
- Testing specific pipeline branches

## HTTP Status Codes

| Code | Error | Description |
|------|-------|-------------|
| 404 | NotFound | Execution ID not found |
| 400 | BadRequest | Invalid input types or already provided |
| 409 | Conflict | Resume already in progress |
| 500 | InternalServerError | Unexpected error |

## See Also

- [execution.md](./execution.md) - Initial pipeline execution
- [pipelines.md](./pipelines.md) - Pipeline management
