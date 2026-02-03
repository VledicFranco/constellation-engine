# RFC-015e: Dashboard Integration

**Status:** Draft
**Priority:** P2 (UI Enhancement)
**Author:** Human + Claude
**Created:** 2026-02-02
**Parent:** [RFC-015: Pipeline Lifecycle Management](./rfc-015-pipeline-lifecycle.md)
**Depends On:** [RFC-015a](./rfc-015a-suspension-http.md), [RFC-015b](./rfc-015b-pipeline-loader-reload.md)

---

## Summary

Integrate the pipeline lifecycle features from RFC-015a through RFC-015d into the web dashboard. This adds a pipelines panel, suspend/resume UI, canary status visualization, and a bridge between the file browser and `PipelineStore`.

---

## Motivation

The dashboard currently operates entirely in hot mode — users browse files, edit source in the editor, and click "Run" which sends source + inputs to `POST /run`. There is no visibility into:

- What pipelines are loaded in `PipelineStore`
- Pipeline version history or rollback
- Canary deployment status and metrics
- Suspended executions waiting for input

These features exist at the HTTP level (RFC-015a through RFC-015d) but are invisible to dashboard users.

---

## Bridge File Browser → PipelineStore

The dashboard file browser currently reads `.cst` files from disk via `/api/v1/files` and sends source to `/api/v1/execute` (hot path). After [RFC-015b](./rfc-015b-pipeline-loader-reload.md), files loaded at startup are already in `PipelineStore`.

Add a **"Load"** action in the file browser that:
1. Compiles the selected file via `POST /compile`
2. Aliases it by filename
3. Shows it in a "Loaded Pipelines" panel alongside file browser

This connects two currently disconnected parts of the dashboard: browsing files and managing pipelines.

---

## Pipelines Panel

New dashboard panel showing loaded pipelines:

| Pipeline | Hash | Version | Inputs | Outputs | Status | Actions |
|----------|------|---------|--------|---------|--------|---------|
| scoring | abc1... | v3 | x: Int, y: Float | score: Float | Active | Execute, Reload, Versions, Delete |
| enrichment | def4... | v1 | text: String | entities: List | Active | Execute, Reload, Versions, Delete |
| scoring (canary) | ghi7... | v4 | x: Int, y: Float | score: Float | Canary (25%) | Promote, Rollback |

### Panel Features

- **Real-time refresh** — polls `GET /pipelines` periodically or uses server-sent events
- **Canary indicator** — when a canary is active, shows both versions with weight percentage and live metrics
- **Version history** — expandable row showing version history with rollback buttons
- **Search/filter** — filter pipelines by name

---

## Execute from Pipelines Panel

Clicking "Execute" on a loaded pipeline:
1. Shows input form (populated from `inputSchema` in pipeline metadata)
2. Submits via `POST /execute { ref: "scoring", inputs: {...} }`
3. Shows outputs (or suspension state with missing inputs)

This is the cold execution path made accessible from the dashboard.

---

## Suspend/Resume in Dashboard

When an execution returns `status: "suspended"`:

1. Show partially computed outputs in the output panel
2. Highlight missing inputs in the input form (with type hints from `missingInputs`)
3. "Resume" button submits `POST /executions/:id/resume` with filled-in missing inputs
4. If the resumed execution suspends again, repeat the cycle

### Suspended Executions List

New panel or tab showing all suspended executions:

| Execution ID | Pipeline | Resumptions | Missing Inputs | Created | Actions |
|-------------|----------|-------------|----------------|---------|---------|
| abc-123 | scoring | 0 | approval: Bool | 2m ago | Resume, Delete |
| def-456 | enrichment | 1 | reviewNotes: String | 1h ago | Resume, Delete |

Clicking "Resume" opens the input form pre-populated with known inputs and highlighting the missing ones.

---

## Canary Status Visualization

When a canary is active for a pipeline, show in the pipelines panel:

### Canary Progress Bar

```
scoring v3 → v4: ████████░░░░░░░░░░░░ 25% (step 2/4)
```

### Canary Metrics Comparison

| Metric | v3 (old) | v4 (new) | Threshold |
|--------|----------|----------|-----------|
| Error rate | 0.2% | 0.8% | < 5% |
| Avg latency | 12ms | 14ms | — |
| P99 latency | 45ms | 48ms | < 100ms |
| Requests | 750 | 50 | min 10 |

### Canary Actions

- **Promote** button → `POST /pipelines/:name/canary/promote`
- **Rollback** button → `POST /pipelines/:name/canary/rollback`
- Auto-refresh metrics while canary is active

---

## Incremental Delivery

This phase is the largest in scope (frontend work) and can be delivered incrementally:

1. **Pipelines panel** — list loaded pipelines, execute by reference (depends on RFC-015b)
2. **Suspend/resume UI** — suspension status in output, resume form (depends on RFC-015a)
3. **Version history and rollback** — version list with rollback buttons (depends on RFC-015b)
4. **Canary visualization** — progress bar, metrics comparison, promote/rollback (depends on RFC-015c)
5. **File browser → Load bridge** — "Load" action connecting file browser to PipelineStore

Each increment is independently useful and testable.

---

## What Changes

| Component | Change |
|-----------|--------|
| `dashboard/src/` | New pipelines panel component |
| `dashboard/src/` | Suspend/resume UI in execution output |
| `dashboard/src/` | Canary status visualization |
| `dashboard/src/types.d.ts` | TypeScript types for pipeline, version, canary models |
| `dashboard-tests/` | E2E tests for new panels |

---

## Tests

### E2E Tests (Playwright)

- Pipelines panel loads and shows pipeline list
- Execute from pipelines panel → outputs displayed
- Suspended execution shows missing inputs → resume → completed
- Version history panel shows versions, rollback works
- Canary status shows progress bar and metrics
- Canary promote/rollback buttons work
- File browser "Load" action adds pipeline to panel
- Pipeline search/filter works

### Smoke Tests

- Pipelines panel renders without JS errors
- Panel handles empty pipeline list gracefully
- Panel handles server unavailable gracefully
