# RFCs

> **Path**: `docs/rfcs/`
> **Parent**: [docs/](../README.md)

Request for Comments (RFC) documents for proposed features and infrastructure.

## Status Legend

| Status | Description |
|--------|-------------|
| Draft | Initial proposal, open for discussion |
| Accepted | Approved for implementation |
| Implemented | Feature has been implemented |
| Rejected | Proposal was rejected |

---

## RFC Index

### Module Execution Options (RFC-001 to RFC-011)

These RFCs introduce a `with` clause for configuring module call behavior:

```constellation
result = MyModule(input) with retry: 3, timeout: 30s, cache: 5min
```

#### Priority 1: Core Resilience

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-001](./rfc-001-retry.md) | `retry` | Implemented | Retry failed module calls N times |
| [RFC-002](./rfc-002-timeout.md) | `timeout` | Implemented | Maximum execution time for module calls |
| [RFC-003](./rfc-003-fallback.md) | `fallback` | Implemented | Default value when module call fails |
| [RFC-004](./rfc-004-cache.md) | `cache` | Implemented | Cache results with TTL and pluggable backends |

#### Priority 2: Retry Configuration

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-005](./rfc-005-delay.md) | `delay` | Implemented | Delay between retry attempts |
| [RFC-006](./rfc-006-backoff.md) | `backoff` | Implemented | Retry backoff strategy (exponential, linear, fixed) |

#### Priority 3: Rate Control

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-007](./rfc-007-throttle.md) | `throttle` | Implemented | Rate limiting for API calls |
| [RFC-008](./rfc-008-concurrency.md) | `concurrency` | Implemented | Limit parallel executions |

#### Priority 4: Advanced Control

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-009](./rfc-009-on-error.md) | `on_error` | Implemented | Error handling strategy |
| [RFC-010](./rfc-010-lazy.md) | `lazy` | Implemented | Lazy evaluation |
| [RFC-011](./rfc-011-priority.md) | `priority` | Implemented | Execution priority hints |

---

### Quality Infrastructure

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-012](./rfc-012-dashboard-e2e-tests.md) | Dashboard E2E Tests | Implemented | Playwright-based end-to-end test suite for the web dashboard |

---

### Production Readiness

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-013](./rfc-013-production-readiness.md) | Production Readiness | Implemented | HTTP hardening (auth, CORS, rate limiting), health checks, Docker, K8s |

---

### Runtime Features

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-014](./rfc-014-suspendable-execution.md) | Suspendable Execution | Draft | Suspend/resume execution at runtime when inputs are missing or nodes fail |

---

### Pipeline Lifecycle (RFC-015 family)

RFC-015 is a **structured RFC** with a master document defining shared terminology and architecture, and child RFCs covering specific capabilities.

| RFC | Feature | Status | Depends On | Description |
|-----|---------|--------|------------|-------------|
| [RFC-015](./rfc-015-pipeline-lifecycle.md) | Pipeline Lifecycle (master) | Draft | — | Terminology standardization ("program" → "pipeline"), hot/cold/warm definitions, caching architecture, rename inventory |
| [RFC-015a](./rfc-015a-suspension-http.md) | Suspension-Aware HTTP | Draft | RFC-015 | Expose suspend/resume over HTTP: extended response models, `POST /executions/:id/resume`, SuspensionStore wiring |
| [RFC-015b](./rfc-015b-pipeline-loader-reload.md) | Pipeline Loader & Reload | Draft | RFC-015 | Startup `.cst` loader, hot-reload endpoint, pipeline versioning with rollback |
| [RFC-015c](./rfc-015c-canary-releases.md) | Canary Releases | Draft | RFC-015b | Traffic splitting between pipeline versions, per-version metrics, auto-promote/rollback |
| [RFC-015d](./rfc-015d-persistent-pipeline-store.md) | Persistent PipelineStore | Draft | RFC-015 | Filesystem-backed PipelineStore for pipelines that survive restarts |
| [RFC-015e](./rfc-015e-dashboard-integration.md) | Dashboard Integration | Draft | RFC-015a, 015b, 015c | Pipelines panel, suspend/resume UI, canary visualization, file browser bridge |

#### Implementation Order

```
RFC-015 (terminology rename)
  ├── RFC-015a (suspension HTTP)     ← can start after 015
  │     └── Phase 1: responses
  │     └── Phase 2: resume endpoint
  ├── RFC-015b (loader + reload)     ← can start after 015
  │     └── Phase 3: startup loader
  │     └── Phase 4a: versioning + hot-reload
  │           └── RFC-015c (canary)  ← requires 015b
  ├── RFC-015d (persistent store)    ← can start after 015
  └── RFC-015e (dashboard)           ← requires 015a + 015b
```

#### QA Iterations

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-015f](./rfc-015f-qa-iteration-1.md) | QA Iteration 1 | Implemented | First QA pass fixes |
| [RFC-015g](./rfc-015g-qa-iteration-2.md) | QA Iteration 2 | Implemented | Second QA pass fixes |

---

### Project Quality

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-016](./rfc-016-project-qa-audit.md) | Project QA Audit | Implemented | Comprehensive quality audit across all modules |

---

### v1.0 Readiness

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-017](./rfc-017-v1-readiness.md) | v1.0 Readiness | In Progress | Gap analysis and roadmap for v1.0 release |

---

### Future Features

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-018](./rfc-018-mcp-server.md) | MCP Server | Draft | Model Context Protocol server for LLM integration |

---

## Syntax Overview

The `with` clause (RFC-001 through RFC-011) follows a module call and contains comma-separated options:

```constellation
# Single option
result = Module(input) with retry: 3

# Multiple options
result = Module(input) with retry: 3, timeout: 30s

# Multi-line for readability
result = Module(input) with
    retry: 3,
    delay: 1s,
    backoff: exponential,
    timeout: 30s,
    fallback: "default",
    cache: 5min
```
