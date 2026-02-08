# Suspended Execution

> **Path**: `organon/features/execution/suspension.md`
> **Parent**: [execution/](./README.md)

Pause on missing inputs, resume with additional data, manage workflow state.

## Overview

Suspended execution enables multi-step workflows. When a pipeline encounters missing inputs, it suspends rather than failing. The execution state is persisted and can be resumed later when the missing inputs become available.

## When to Use Suspension

| Use Case | Why Suspension |
|----------|----------------|
| Human-in-the-loop approval | Wait for manual decision |
| Multi-day workflows | State survives across sessions |
| Event-driven pipelines | Resume when external event arrives |
| Incremental data collection | Add inputs as they become available |
| Async external calls | Resume when callback arrives |
| Staged processing | Pause between stages for validation |

## Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                    INITIAL REQUEST                           │
├─────────────────────────────────────────────────────────────┤
│  POST /run                                                   │
│    source: "in userId: String\n                             │
│             in approval: Boolean\n                          │
│             user = GetUser(userId)\n                        │
│             out user when approval"                          │
│    inputs: { userId: "user-123" }  ← approval missing       │
│                                                              │
│  Response:                                                   │
│    status: "suspended"                                       │
│    executionId: "exec-789"                                   │
│    missingInputs: { approval: "CBool" }                     │
│    computedNodes: { user: { id: "user-123", name: "Alice" }}│
└─────────────────────────────────────────────────────────────┘
                              ↓
                    [Time passes...]
                    [Human reviews user data]
                    [Human approves]
                              ↓
┌─────────────────────────────────────────────────────────────┐
│                    RESUME REQUEST                            │
├─────────────────────────────────────────────────────────────┤
│  POST /executions/exec-789/resume                            │
│    additionalInputs: { approval: true }                      │
│                                                              │
│  Response:                                                   │
│    status: "completed"                                       │
│    outputs: { user: { id: "user-123", name: "Alice" } }     │
└─────────────────────────────────────────────────────────────┘
```

## API

### Initial Execution (Creates Suspension)

Suspension is triggered automatically when inputs are missing:

```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in userId: String\nin approval: Boolean\nuser = GetUser(userId)\nout user when approval",
    "inputs": { "userId": "user-123" }
  }'
```

Response:

```json
{
  "success": true,
  "outputs": {},
  "structuralHash": "a1b2c3d4...",
  "status": "suspended",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "missingInputs": {
    "approval": "CBool"
  },
  "pendingOutputs": ["user"],
  "resumptionCount": 0
}
```

| Field | Description |
|-------|-------------|
| `status` | `"suspended"` indicates more inputs needed |
| `executionId` | UUID for resumption |
| `missingInputs` | Map of missing input names to their types |
| `pendingOutputs` | Outputs that cannot be computed yet |
| `resumptionCount` | Number of resume operations (0 initially) |

### List Suspended Executions

```bash
curl http://localhost:8080/executions
```

Response:

```json
{
  "executions": [
    {
      "executionId": "550e8400-e29b-41d4-a716-446655440000",
      "structuralHash": "a1b2c3d4...",
      "resumptionCount": 0,
      "missingInputs": {
        "approval": "CBool"
      },
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ]
}
```

### Get Suspension Details

```bash
curl http://localhost:8080/executions/550e8400-e29b-41d4-a716-446655440000
```

Returns same format as list entry.

### Resume Execution

```bash
curl -X POST http://localhost:8080/executions/550e8400-e29b-41d4-a716-446655440000/resume \
  -H "Content-Type: application/json" \
  -d '{
    "additionalInputs": {
      "approval": true
    }
  }'
```

#### Request Fields

| Field | Type | Description |
|-------|------|-------------|
| `additionalInputs` | object | Input values by variable name |
| `resolvedNodes` | object | Directly resolve intermediate nodes (advanced) |

#### Response (Completed)

```json
{
  "success": true,
  "outputs": {
    "user": { "id": "user-123", "name": "Alice" }
  },
  "status": "completed",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "resumptionCount": 1
}
```

#### Response (Still Suspended)

If resume provides some but not all inputs:

```json
{
  "success": true,
  "outputs": {},
  "status": "suspended",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "missingInputs": {
    "finalApproval": "CBool"
  },
  "pendingOutputs": ["result"],
  "resumptionCount": 1
}
```

### Delete Suspension

Discard saved state without resuming:

```bash
curl -X DELETE http://localhost:8080/executions/550e8400-e29b-41d4-a716-446655440000
```

Response: `{"deleted": true}`

## State Management

### What is Persisted

When execution suspends, the following is saved:

| State | Description |
|-------|-------------|
| Execution ID | UUID for reference |
| Pipeline reference | Structural hash of pipeline |
| Provided inputs | All inputs given so far |
| Computed nodes | Intermediate values already calculated |
| Missing inputs | What still needs to be provided |
| Pending outputs | Outputs that cannot be computed |
| Resumption count | How many resumes have occurred |

### Auto-Save and Auto-Delete

| Event | Action |
|-------|--------|
| Execution suspends | State saved automatically |
| Execution completes after resume | State deleted automatically |
| Execution fails after resume | State deleted automatically |
| Re-suspension after resume | Old state replaced with new |

### State Lifetime

By default, suspended executions persist until:
- Resumed to completion or failure
- Explicitly deleted via `DELETE /executions/{id}`
- Server restart (in-memory store)

For persistent storage:

```scala
ConstellationServer.builder(constellation, compiler)
  .withSuspensionStore(FileSystemSuspensionStore(Path.of("/suspensions")))
  .run
```

## Advanced Usage

### Resolving Intermediate Nodes

You can provide values for computed nodes, not just inputs:

```bash
curl -X POST http://localhost:8080/executions/{id}/resume \
  -H "Content-Type: application/json" \
  -d '{
    "resolvedNodes": {
      "preprocessedData": {"items": [1, 2, 3], "count": 3}
    }
  }'
```

Use cases:
- External system already computed part of the pipeline
- Inject cached results
- Testing specific pipeline branches
- Bypass expensive computations with known results

### Multi-Stage Suspension

Pipelines can suspend multiple times:

```
Execute with input A → Suspend (needs B)
Resume with B → Suspend (needs C)
Resume with C → Complete
```

Each resume increments `resumptionCount`.

### Combining with Cold Execution

Cold execution supports suspension:

```bash
# Compile
curl -X POST http://localhost:8080/compile \
  -d '{"source": "...", "name": "approval-workflow"}'

# Execute (suspends)
curl -X POST http://localhost:8080/execute \
  -d '{"ref": "approval-workflow", "inputs": {"userId": "123"}}'

# Resume (same endpoint, uses execution ID)
curl -X POST http://localhost:8080/executions/{id}/resume \
  -d '{"additionalInputs": {"approval": true}}'
```

## Error Handling

### Input Type Mismatch

```json
{
  "success": false,
  "error": "Input error: Type mismatch for 'approval': expected CBool, got string"
}
```

### Input Already Provided

```json
{
  "success": false,
  "error": "Input error: Input 'userId' was already provided"
}
```

### Unknown Input

```json
{
  "success": false,
  "error": "Input error: Unknown input 'approvalCode'"
}
```

### Node Already Resolved

```json
{
  "success": false,
  "error": "Node error: Node 'user' was already computed"
}
```

### Concurrent Resume

```json
{
  "error": "ResumeInProgress",
  "message": "A resume operation is already in progress for execution '550e8400-...'"
}
```

HTTP status: `409 Conflict`

### Execution Not Found

```json
{
  "error": "NotFound",
  "message": "Execution '550e8400-...' not found"
}
```

HTTP status: `404 Not Found`

### Pipeline Changed

```json
{
  "success": false,
  "error": "Pipeline error: Pipeline was reloaded; suspension is no longer valid"
}
```

This occurs if the pipeline was hot-reloaded with a different structural hash.

## HTTP Status Codes

| Code | Meaning |
|------|---------|
| 200 | Success (completed, suspended, or failed) |
| 400 | Invalid inputs (type mismatch, already provided) |
| 404 | Execution or pipeline not found |
| 409 | Concurrent resume in progress |
| 500 | Internal server error |

## Components Involved

| Component | Module | File | Role |
|-----------|--------|------|------|
| HTTP Endpoints | `http-api` | `ConstellationRoutes.scala` | Route handling |
| Execution Helper | `http-api` | `ExecutionHelper.scala` | Resume orchestration |
| Execution Storage | `http-api` | `ExecutionStorage.scala` | HTTP-layer state management |
| Suspendable Execution | `runtime` | `SuspendableExecution.scala` | Core suspension logic |
| Suspended Execution | `runtime` | `SuspendedExecution.scala` | Suspension state model |
| Suspension Store | `runtime` | `SuspensionStore.scala` | State persistence interface |
| In-Memory Store | `runtime` | `InMemorySuspensionStore.scala` | Default implementation |
| Suspension Codec | `runtime` | `SuspensionCodec.scala` | State serialization |
| Circe Codec | `runtime` | `CirceJsonSuspensionCodec.scala` | JSON serialization |

## Concurrency and Safety

### Per-Execution Locking

Resume operations acquire exclusive access:

```
Client A: POST /executions/123/resume
  → Acquires lock on 123
  → Processes resume
  → Releases lock

Client B: POST /executions/123/resume (during A's processing)
  → Lock acquisition fails
  → Returns 409 Conflict
```

### Idempotency Considerations

Resume is **not** idempotent. Each resume:
- Modifies execution state
- Increments resumption count
- May complete or re-suspend

For idempotent workflows, implement at the application layer.

## Patterns and Best Practices

### 1. Check Before Resume

```bash
# Get current state
STATE=$(curl http://localhost:8080/executions/{id})
MISSING=$(echo $STATE | jq -r '.missingInputs | keys[]')

# Verify you have what's needed
if [[ $MISSING == "approval" ]]; then
  curl -X POST .../resume -d '{"additionalInputs": {"approval": true}}'
fi
```

### 2. Timeout Handling

Implement application-level timeouts:

```bash
# Check if suspension is stale
CREATED=$(curl .../executions/{id} | jq -r '.createdAt')
if [[ $(date_diff_hours "$CREATED") -gt 24 ]]; then
  curl -X DELETE .../executions/{id}
  # Handle timeout
fi
```

### 3. Resumption Count Limits

Prevent infinite resume loops:

```bash
COUNT=$(curl .../executions/{id} | jq -r '.resumptionCount')
if [[ $COUNT -gt 10 ]]; then
  curl -X DELETE .../executions/{id}
  # Handle runaway execution
fi
```

## Related Documentation

- [PHILOSOPHY.md](./PHILOSOPHY.md) - Why suspension exists
- [ETHOS.md](./ETHOS.md) - Invariants for suspension code
- [hot-execution.md](./hot-execution.md) - Initial execution mode
- [cold-execution.md](./cold-execution.md) - Pre-compiled execution
- [website/docs/api-reference/suspension.md](../../http-api/suspension.md) - Full API reference
