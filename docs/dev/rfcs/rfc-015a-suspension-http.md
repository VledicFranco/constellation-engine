# RFC-015a: Suspension-Aware HTTP Endpoints

**Status:** Draft
**Priority:** P1 (Core HTTP Module)
**Author:** Human + Claude
**Created:** 2026-02-02
**Parent:** [RFC-015: Pipeline Lifecycle Management](./rfc-015-pipeline-lifecycle.md)
**Depends On:** RFC-015 (Phase 0: terminology rename)

---

## Summary

Expose the runtime's suspend/resume semantics (from RFC-014) through the HTTP API. This RFC adds suspension-aware fields to existing response models and introduces a `POST /executions/:id/resume` endpoint for multi-step workflows over HTTP.

**Phases covered:**
- Phase 1: Suspension-aware HTTP responses
- Phase 2: Resume endpoint

---

## Motivation

RFC-014 implemented full suspend/resume semantics at the library level. The `DataSignature` returned by `constellation.run()` includes `status`, `missingInputs`, `pendingOutputs`, and `suspendedState`. But the HTTP API discards all of this — `POST /execute` and `POST /run` return only `{ success, outputs, error }`.

HTTP consumers cannot use multi-step workflows at all. The suspension feature — one of the most architecturally significant capabilities — is invisible to HTTP clients.

---

## Phase 1: Suspension-Aware HTTP Responses

### Extended Response Models

Add optional fields to existing responses (backward compatible):

```scala
// Extended ExecuteResponse
case class ExecuteResponse(
  success: Boolean,
  outputs: Option[Map[String, Json]] = None,
  error: Option[String] = None,
  // --- New fields (Phase 1) ---
  status: Option[String] = None,           // "completed" | "suspended" | "failed"
  executionId: Option[String] = None,      // UUID for resume reference
  missingInputs: Option[Map[String, String]] = None,  // name → type
  pendingOutputs: Option[List[String]] = None,
  resumptionCount: Option[Int] = None
)

// Extended RunResponse (same additions)
case class RunResponse(
  success: Boolean,
  outputs: Option[Map[String, Json]] = None,
  structuralHash: Option[String] = None,
  compilationErrors: Option[List[String]] = None,
  error: Option[String] = None,
  // --- New fields (Phase 1) ---
  status: Option[String] = None,
  executionId: Option[String] = None,
  missingInputs: Option[Map[String, String]] = None,
  pendingOutputs: Option[List[String]] = None,
  resumptionCount: Option[Int] = None
)
```

### Behavior Change

When `constellation.run()` returns a `DataSignature` with `status = Suspended`:

**Before (current):**
```json
{
  "success": true,
  "outputs": {}
}
```

**After:**
```json
{
  "success": true,
  "status": "suspended",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "outputs": { "partialResult": 42 },
  "missingInputs": { "approval": "Bool", "managerNotes": "String" },
  "pendingOutputs": ["finalScore", "recommendation"],
  "resumptionCount": 0
}
```

Existing clients that only read `success` and `outputs` continue to work unchanged. Clients aware of suspension can check `status` and proceed accordingly.

### What Changes

| Component | Change |
|-----------|--------|
| `ApiModels.scala` | Add optional fields to `ExecuteResponse`, `RunResponse` |
| `ConstellationRoutes.scala` | Map `DataSignature` fields to response, populate new fields |

### What Doesn't Change

| Component | Reason |
|-----------|--------|
| `DataSignature` | Already has all the information |
| `ConstellationImpl` | Already returns `DataSignature` |
| `SuspendableExecution` | Already works |
| `PipelineStore` | Not affected |

### Tests

- Existing `/run` and `/execute` tests pass unchanged (new fields are optional/`None`)
- New test: `/run` with partial inputs returns `status: "suspended"` and `missingInputs`
- New test: `/execute` with partial inputs returns `status: "suspended"`
- New test: Completed execution returns `status: "completed"` with `missingInputs: {}`

---

## Phase 2: Resume Endpoint

### New Endpoint

```
POST /executions/:id/resume
Content-Type: application/json

{
  "additionalInputs": {
    "approval": true,
    "managerNotes": "Looks good"
  }
}
```

**Response:** Same shape as `ExecuteResponse` (may suspend again if more inputs needed).

### Suspension Storage

The server needs to store `SuspendedExecution` state between requests. This uses the existing `SuspensionStore` trait from the runtime module:

```scala
// Already defined in runtime module
trait SuspensionStore[F[_]] {
  def save(state: SuspendedExecution): F[Unit]
  def load(executionId: UUID): F[Option[SuspendedExecution]]
  def delete(executionId: UUID): F[Unit]
  def list: F[List[SuspendedExecution]]
}
```

**Default:** In-memory `SuspensionStore` (sufficient for single-instance deployments).

### Server Builder Extension

```scala
ConstellationServer.builder(constellation, compiler)
  .withSuspensionStore(InMemorySuspensionStore.create)  // opt-in
  .run
```

When no suspension store is configured, resume returns `404 Not Found` (suspension state is discarded).

### Additional Endpoints

```
GET /executions                    → List suspended executions
GET /executions/:id                → Get suspension details
DELETE /executions/:id             → Abandon/delete a suspended execution
```

### Request/Response Models

```scala
case class ResumeRequest(
  additionalInputs: Option[Map[String, Json]] = None,
  resolvedNodes: Option[Map[String, Json]] = None  // Manual healing
)

case class ExecutionSummary(
  executionId: String,
  structuralHash: String,
  pipelineName: Option[String],
  resumptionCount: Int,
  missingInputs: Map[String, String],
  createdAt: String  // ISO-8601
)

case class ExecutionListResponse(
  executions: List[ExecutionSummary]
)
```

### Execution Flow

```
Client                          Server
  │                               │
  │  POST /run (partial inputs)   │
  │──────────────────────────────>│
  │                               │ execute → suspend → store in SuspensionStore
  │  { status: "suspended",       │
  │    executionId: "abc-123",    │
  │    missingInputs: {...} }     │
  │<──────────────────────────────│
  │                               │
  │  ... (hours/days pass) ...    │
  │                               │
  │  POST /executions/abc-123/resume
  │  { additionalInputs: {...} }  │
  │──────────────────────────────>│
  │                               │ load from SuspensionStore → resume → complete
  │  { status: "completed",       │
  │    outputs: { ... } }         │
  │<──────────────────────────────│
```

### What Changes

| Component | Change |
|-----------|--------|
| `ConstellationRoutes.scala` | Add `/executions/*` routes |
| `ConstellationServer.scala` | Add `withSuspensionStore()` builder method |
| `ApiModels.scala` | Add `ResumeRequest`, `ExecutionSummary`, `ExecutionListResponse` |

### What Doesn't Change

| Component | Reason |
|-----------|--------|
| `SuspendableExecution` | Resume logic already works |
| `SuspensionStore` trait | Already defined |
| `DataSignature` | Already captures suspension state |

### Tests

- Resume a suspended execution, verify outputs
- Resume with wrong execution ID → 404
- Resume without suspension store configured → 404 with clear error
- Resume with type-mismatched inputs → 400
- Concurrent resume attempts → 409 Conflict
- List suspended executions
- Delete a suspended execution, verify resume returns 404

---

## Design Decisions

**D1: Additive responses over new endpoints.** We extend existing `/execute` and `/run` responses with optional suspension fields rather than creating separate `/execute-with-suspend` endpoints. This keeps the API surface small and avoids forcing clients to choose between endpoint variants.

**D2: SuspensionStore is opt-in.** Without a configured store, suspended state is returned in the response but not persisted server-side. Clients can store it themselves (same as the library API pattern). The store is only needed for the resume endpoint.
