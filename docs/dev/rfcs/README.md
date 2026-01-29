# Constellation Engine RFCs

This directory contains Request for Comments (RFC) documents for proposed features and infrastructure.

## Status Legend

| Status | Description |
|--------|-------------|
| Draft | Initial proposal, open for discussion |
| Accepted | Approved for implementation |
| Implemented | Feature has been implemented |
| Rejected | Proposal was rejected |

---

## Module Execution Options

These RFCs introduce a `with` clause for configuring module call behavior:

```constellation
result = MyModule(input) with retry: 3, timeout: 30s, cache: 5min
```

### Priority 1: Core Resilience

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-001](./rfc-001-retry.md) | `retry` | Draft | Retry failed module calls N times |
| [RFC-002](./rfc-002-timeout.md) | `timeout` | Draft | Maximum execution time for module calls |
| [RFC-003](./rfc-003-fallback.md) | `fallback` | Draft | Default value when module call fails |
| [RFC-004](./rfc-004-cache.md) | `cache` | Draft | Cache results with TTL and pluggable backends |

### Priority 2: Retry Configuration

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-005](./rfc-005-delay.md) | `delay` | Draft | Delay between retry attempts |
| [RFC-006](./rfc-006-backoff.md) | `backoff` | Draft | Retry backoff strategy (exponential, linear, fixed) |

### Priority 3: Rate Control

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-007](./rfc-007-throttle.md) | `throttle` | Draft | Rate limiting for API calls |
| [RFC-008](./rfc-008-concurrency.md) | `concurrency` | Draft | Limit parallel executions |

### Priority 4: Advanced Control

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-009](./rfc-009-on-error.md) | `on_error` | Draft | Error handling strategy |
| [RFC-010](./rfc-010-lazy.md) | `lazy` | Draft | Lazy evaluation |
| [RFC-011](./rfc-011-priority.md) | `priority` | Draft | Execution priority hints |

---

## Syntax Overview

The `with` clause follows a module call and contains comma-separated options:

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

---

## Implementation Phases

### Phase 1: Core Resilience (MVP)
- RFC-001: retry
- RFC-002: timeout
- RFC-003: fallback
- RFC-004: cache

### Phase 2: Retry Configuration
- RFC-005: delay
- RFC-006: backoff

### Phase 3: Rate Control
- RFC-007: throttle
- RFC-008: concurrency

### Phase 4: Advanced
- RFC-009: on_error
- RFC-010: lazy
- RFC-011: priority

---

## Quality Infrastructure

| RFC | Feature | Status | Description |
|-----|---------|--------|-------------|
| [RFC-012](./rfc-012-dashboard-e2e-tests.md) | Dashboard E2E Tests | Implemented | Playwright-based end-to-end test suite for the web dashboard |
