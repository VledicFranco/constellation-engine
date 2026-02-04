# RFC-015c: Canary Releases

**Status:** Implemented
**Priority:** P1 (Core HTTP Module)
**Author:** Human + Claude
**Created:** 2026-02-02
**Parent:** [RFC-015: Pipeline Lifecycle Management](./rfc-015-pipeline-lifecycle.md)
**Depends On:** [RFC-015b: Pipeline Loader, Hot-Reload, and Versioning](./rfc-015b-pipeline-loader-reload.md)

---

## Summary

Add canary release support to pipeline hot-reload. When reloading a pipeline, operators can opt into a canary strategy that splits traffic between the old and new versions, collects per-version metrics, and automatically promotes or rolls back based on configurable error and latency thresholds.

---

## Motivation

Immediate cutover on reload (the default from RFC-015b) is fine for development but risky for production. A new pipeline version may have subtle bugs that only surface under real traffic — schema changes pass compilation but produce incorrect outputs, performance degrades under load, or edge cases trigger failures.

Canary releases mitigate this risk: the new version serves a small fraction of traffic while metrics are collected. If the error rate exceeds a threshold, the system automatically rolls back. If the new version is healthy through progressive weight increases, it becomes the active version.

This is standard practice for service deployments (Kubernetes canary, Istio traffic splitting) but not typically available for data pipeline updates. Since Constellation pipelines are the unit of computation, canary releases at the pipeline level provide the same safety guarantees.

---

## Canary Configuration

```scala
case class CanaryConfig(
  initialWeight: Double = 0.05,          // 5% traffic to new version
  promotionSteps: List[Double] = List(0.10, 0.25, 0.50, 1.0),
  observationWindow: FiniteDuration = 5.minutes,  // per step
  errorThreshold: Double = 0.05,         // >5% error rate triggers rollback
  latencyThresholdMs: Option[Long] = None,  // p99 latency ceiling
  minRequests: Int = 10,                 // minimum requests before evaluating a step
  autoPromote: Boolean = true            // false = manual promotion only
)
```

### Configuration Fields

| Field | Default | Description |
|-------|---------|-------------|
| `initialWeight` | `0.05` | Fraction of traffic routed to the new version at the start of the canary (0.0 to 1.0). |
| `promotionSteps` | `[0.10, 0.25, 0.50, 1.0]` | Sequence of weight values the canary progresses through. Each step is evaluated independently. The final step (1.0) means full promotion. |
| `observationWindow` | `5m` | Minimum time to observe at each step before evaluating promotion. Prevents premature decisions on insufficient data. |
| `errorThreshold` | `0.05` | Maximum acceptable error rate (failures / total requests) for the new version. Exceeding this triggers automatic rollback. |
| `latencyThresholdMs` | `None` | Optional p99 latency ceiling in milliseconds. If the new version's p99 latency exceeds this, rollback is triggered. |
| `minRequests` | `10` | Minimum number of requests the new version must serve before evaluation. Prevents statistical noise from small samples. |
| `autoPromote` | `true` | When `true`, the canary automatically advances through steps when thresholds are met. When `false`, each step requires manual promotion via `POST /pipelines/:name/canary/promote`. |

---

## Canary State

```scala
case class CanaryState(
  pipelineName: String,
  oldVersion: PipelineVersion,
  newVersion: PipelineVersion,
  currentWeight: Double,
  currentStep: Int,
  status: CanaryStatus,       // Observing | Promoting | RolledBack | Complete
  startedAt: Instant,
  metrics: CanaryMetrics
)

enum CanaryStatus {
  case Observing    // collecting metrics at current weight
  case Promoting    // advancing to next weight step
  case RolledBack   // error threshold exceeded, reverted to old version
  case Complete     // reached 100%, new version is now active
}

case class CanaryMetrics(
  oldVersion: VersionMetrics,
  newVersion: VersionMetrics
)

case class VersionMetrics(
  requests: Long,
  successes: Long,
  failures: Long,
  avgLatencyMs: Double,
  p99LatencyMs: Double
)
```

---

## Triggering a Canary

Include a `canary` configuration in the reload request:

```json
POST /pipelines/scoring/reload
{
  "source": "in x: Int\nresult = Double(x)\nout result",
  "canary": {
    "initialWeight": 0.05,
    "promotionSteps": [0.10, 0.25, 0.50, 1.0],
    "observationWindow": "5m",
    "errorThreshold": 0.05,
    "minRequests": 10,
    "autoPromote": true
  }
}
```

**Response:**

```json
{
  "success": true,
  "previousHash": "abc123...",
  "newHash": "def456...",
  "name": "scoring",
  "changed": true,
  "version": 3,
  "canary": {
    "status": "observing",
    "currentWeight": 0.05,
    "oldVersion": 2,
    "newVersion": 3
  }
}
```

Without the `canary` field, reload works as before — immediate cutover, no canary.

---

## Execution Flow

When a canary is active for pipeline `"scoring"`:

```
Client                          Server
  │                               │
  │  POST /execute                │
  │  { ref: "scoring",            │
  │    inputs: { x: 42 } }       │
  │──────────────────────────────>│
  │                               │ canary active for "scoring"
  │                               │ weighted random: 5% → v3, 95% → v2
  │                               │ selected: v2 (old version)
  │                               │ execute v2, record metrics
  │  { success: true,             │
  │    outputs: { result: 84 } }  │
  │<──────────────────────────────│
```

Step-by-step:

1. `POST /execute { ref: "scoring" }` arrives
2. Router detects active canary for `"scoring"`
3. Weighted random: 5% → new version, 95% → old version
4. Execute selected version, record metrics (success/failure, latency)
5. After `observationWindow` and `minRequests` reached, evaluate:
   - New version error rate ≤ `errorThreshold` and latency ≤ threshold → promote to next step
   - New version error rate > `errorThreshold` → automatic rollback
6. Repeat steps until weight reaches 1.0 (complete) or rollback

### Metrics Reset Between Steps

Metrics are **reset at each promotion step**. When the canary advances from 10% to 25%, the counters for both versions are zeroed. This ensures each step is evaluated on its own merit — a step that was healthy at 10% traffic might show different behavior at 25% (e.g., contention effects). Cumulative metrics would mask per-step regressions.

The `observationWindow` and `minRequests` thresholds apply per step. Each step must independently pass the evaluation criteria.

---

## Canary Management Endpoints

```
GET  /pipelines/:name/canary              → current canary status + metrics
POST /pipelines/:name/canary/promote      → manually advance to next step
POST /pipelines/:name/canary/rollback     → manually rollback to old version
DELETE /pipelines/:name/canary            → abort canary (rollback to old)
```

### Get Canary Status

```
GET /pipelines/scoring/canary
```

```json
{
  "pipelineName": "scoring",
  "oldVersion": { "version": 2, "structuralHash": "abc123..." },
  "newVersion": { "version": 3, "structuralHash": "def456..." },
  "currentWeight": 0.25,
  "currentStep": 2,
  "status": "observing",
  "startedAt": "2026-02-02T14:30:00Z",
  "metrics": {
    "oldVersion": {
      "requests": 950,
      "successes": 948,
      "failures": 2,
      "avgLatencyMs": 12.3,
      "p99LatencyMs": 45.0
    },
    "newVersion": {
      "requests": 50,
      "successes": 49,
      "failures": 1,
      "avgLatencyMs": 11.8,
      "p99LatencyMs": 42.0
    }
  }
}
```

### Manual Promote

```
POST /pipelines/scoring/canary/promote
```

Advances to the next weight step. Returns updated `CanaryState`. Returns `404` if there's no active canary. Works regardless of `autoPromote` setting — manual override is always available, even when automatic promotion is enabled.

### Manual Rollback

```
POST /pipelines/scoring/canary/rollback
```

Immediately reverts to the old version. The canary state transitions to `RolledBack`. The new version remains in version history but the alias points to the old version.

### Abort Canary

```
DELETE /pipelines/scoring/canary
```

Same effect as rollback — reverts to old version and clears canary state.

---

## Observability

- **Metrics endpoint:** `GET /metrics` extended with per-pipeline per-version metrics when a canary is active
- **Canary status endpoint:** `GET /pipelines/:name/canary` returns live metrics for both versions
- **Logging:** All state transitions are logged:
  ```
  INFO  - Canary 'scoring' v2→v3: started at 5% weight
  INFO  - Canary 'scoring' v2→v3: promoted to 25% (error rate 0.8%, 142 requests)
  INFO  - Canary 'scoring' v2→v3: promoted to 50% (error rate 1.2%, 380 requests)
  INFO  - Canary 'scoring' v2→v3: complete at 100% (error rate 0.9%, 1204 requests)
  ```
- **Rollback logging:**
  ```
  WARN  - Canary 'scoring' v2→v3: rolled back at 10% (error rate 12.3% > threshold 5.0%, 87 requests)
  ```
- **Dashboard integration:** Canary status and metrics shown in the pipelines panel (see [RFC-015e](./rfc-015e-dashboard-integration.md))

---

## Constraints

- **One canary per pipeline** — starting a new canary while one is active requires aborting the current one first. Attempting to start a second canary returns `409 Conflict`.
- **Per-pipeline, not global** — canaries on different pipelines are independent. You can canary `"scoring"` while `"enrichment"` runs normally.
- **In-memory state** — canary state lives in memory (does not survive restarts). If the server restarts during a canary, the new version becomes immediately active (the reload already stored it as the latest version). Persistent canary state is future work.
- **`autoPromote: false` for manual control** — in high-stakes deployments, operators can disable automatic promotion and manually approve each step via `POST /pipelines/:name/canary/promote` after reviewing metrics.

---

## What Changes

| Component | Change |
|-----------|--------|
| `ConstellationRoutes.scala` | Add `/pipelines/:name/canary/*` routes |
| `ApiModels.scala` | Add `CanaryConfig`, `CanaryState`, `CanaryMetrics`, `VersionMetrics` |
| New: `CanaryRouter.scala` | Weighted routing logic, metric collection, step evaluation, auto-promote/rollback |
| `ConstellationServer.scala` | Wire canary router into execution path |

---

## Tests

- Canary routes traffic by weight (statistical test over N requests)
- Canary promotes through steps when error rate is below threshold
- Canary auto-rollback when error rate exceeds threshold
- Canary with `autoPromote: false` stays at current step until manual promote
- Manual promote advances to next step
- Manual rollback reverts to old version
- Abort (DELETE) reverts to old version
- Get canary status returns metrics for both versions
- Start canary while one is active → 409 Conflict
- Canary on non-existent pipeline → 404
- Canary with `minRequests` — no evaluation until threshold met
- Canary with `latencyThresholdMs` — rollback on p99 latency exceeded
- Metrics reset on promotion — counters zeroed when advancing to next step
- Concurrent executions during canary → metrics are thread-safe

---

## Design Decisions

**D1: Canary is opt-in per reload, not a global deployment strategy.** Canary behavior is triggered by including a `canary` configuration in the reload request. Without it, reload is an immediate cutover (the simple case). This keeps the default behavior simple and predictable while making progressive rollouts available when needed. The canary config is per-reload, not per-pipeline — the same pipeline can be reloaded with or without canary at different times depending on the operator's confidence in the change.

**D2: Canary evaluation is request-driven, not timer-driven.** The canary evaluates promotion/rollback criteria after each request (when `minRequests` is met and `observationWindow` has elapsed), rather than running a background timer. This avoids complexity around background fibers and ensures evaluation happens naturally with traffic. For low-traffic pipelines, this means promotion may be slower — but that's appropriate, since low traffic means less statistical confidence in the canary's health.
