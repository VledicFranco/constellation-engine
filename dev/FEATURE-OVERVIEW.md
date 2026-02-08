# Constellation Engine - Feature Overview

> **Purpose:** Canonical reference for all features. Use this to understand what Constellation does, verify documentation completeness, and provide LLM context.

---

## What Is Constellation?

**Type-safe pipeline orchestration for Scala 3.** Define processing pipelines in a declarative DSL (`.cst` files), implement modules in Scala, execute with automatic parallelization and resilience.

### Core Value Propositions

| Value | How |
|-------|-----|
| **Type safety** | Compile-time validation of field access, type operations, module signatures |
| **Separation of concerns** | Pipeline logic (`.cst`) separate from implementation (Scala) |
| **Automatic parallelization** | Independent modules execute concurrently without manual coordination |
| **Built-in resilience** | Retry, timeout, fallback, caching, rate limiting as DSL options |
| **Hot reload** | Update pipelines without restarting the server |
| **Observability** | Execution tracing, metrics hooks, DAG visualization |

### When to Use / Not Use

| Good Fit | Poor Fit |
|----------|----------|
| API composition (BFF pattern) | Simple CRUD (use ORM) |
| Data enrichment pipelines | Stream processing (use Kafka/Flink) |
| ML inference orchestration | ETL at scale (use Spark/dbt) |
| Multi-service aggregation | Single-service calls |
| Type-safety-critical workflows | Prototype/throwaway scripts |

---

## Architecture at a Glance

```
┌─────────────────────────────────────────────────────────────────┐
│                        User Code                                 │
│  .cst files (DSL)              Scala modules (implementation)   │
└──────────────┬─────────────────────────────┬────────────────────┘
               │                             │
               ▼                             ▼
┌──────────────────────────┐    ┌─────────────────────────────────┐
│  Parser → Compiler → DAG │    │  ModuleBuilder → Module.Runnable│
│  (lang-parser, compiler) │    │  (runtime)                      │
└──────────────┬───────────┘    └──────────────┬──────────────────┘
               │                               │
               └───────────┬───────────────────┘
                           ▼
               ┌───────────────────────┐
               │   Execution Engine    │
               │   - Parallel layers   │
               │   - Priority scheduler│
               │   - Suspend/resume    │
               └───────────┬───────────┘
                           ▼
               ┌───────────────────────┐
               │   HTTP API / LSP      │
               │   Dashboard / Tooling │
               └───────────────────────┘
```

**Layers:**
- `core` - Type system (CType/CValue), DAG specs
- `runtime` - Execution engine, ModuleBuilder, scheduling
- `lang-*` - Parser, compiler, stdlib, LSP
- `http-api` - REST endpoints, dashboard, WebSocket LSP

---

## Feature Matrix

### Complexity Levels

| Level | Target User | Examples |
|-------|-------------|----------|
| **Basic** | First-time users | Primitives, simple pipelines, string ops |
| **Intermediate** | Regular users | Records, module options, caching, guards |
| **Advanced** | Power users | Suspend/resume, canary, custom SPIs, unions |

### Core Features

| Feature | Level | Description |
|---------|-------|-------------|
| Pipeline compilation | Basic | `.cst` → validated DAG |
| Module execution | Basic | Call Scala functions from DSL |
| Automatic parallelization | Basic | Independent nodes run concurrently |
| Type checking | Intermediate | Field access, type ops validated at compile |
| Content-addressed storage | Intermediate | Pipelines stored by structural hash |
| Hot reload | Intermediate | Update without restart |
| Suspend/resume | Advanced | Partial execution, resume with missing inputs |
| Canary deployments | Advanced | Traffic splitting, auto-rollback |

### Type System

| Type | Level | Description |
|------|-------|-------------|
| `String`, `Int`, `Float`, `Boolean` | Basic | Primitives |
| `List<T>` | Basic | Ordered collections |
| `Record { field: Type }` | Intermediate | Structured data with named fields |
| `Optional<T>` | Intermediate | Maybe-present values |
| `Map<K,V>` | Intermediate | Key-value mappings |
| `Union A \| B` | Advanced | Tagged variants |

### Type Operations

| Operation | Level | Syntax | Description |
|-----------|-------|--------|-------------|
| Field access | Basic | `record.field` | Get field value |
| Element-wise | Basic | `list.field` | Extract field from each element |
| Merge | Intermediate | `a + b` | Combine record fields |
| Projection | Intermediate | `record[f1, f2]` | Select subset of fields |
| Guard | Intermediate | `expr when cond` | Conditional → Optional |
| Coalesce | Intermediate | `opt ?? default` | Unwrap with fallback |

### Module Options (Orchestration)

| Option | Level | Syntax | Purpose |
|--------|-------|--------|---------|
| `retry` | Basic | `with retry: 3` | Retry N times on failure |
| `timeout` | Basic | `with timeout: 5s` | Max execution time |
| `fallback` | Basic | `with fallback: value` | Default on failure |
| `delay` | Intermediate | `with delay: 500ms` | Wait between retries |
| `backoff` | Intermediate | `with backoff: exponential` | Delay growth strategy |
| `cache` | Intermediate | `with cache: 15min` | Result caching with TTL |
| `cache_backend` | Intermediate | `with cache_backend: "redis"` | Named cache storage |
| `throttle` | Intermediate | `with throttle: 100/1min` | Rate limiting |
| `concurrency` | Intermediate | `with concurrency: 5` | Max parallel executions |
| `on_error` | Advanced | `with on_error: skip` | Error handling strategy |
| `lazy` | Advanced | `with lazy: true` | Deferred execution |
| `priority` | Advanced | `with priority: high` | Scheduling hint |

**Option Interactions:**
- `retry + timeout` → timeout per attempt
- `retry + delay + backoff` → increasing delays
- `cache + retry` → cache after success
- `fallback + on_error` → fallback takes precedence

### Standard Library (44+ functions)

| Category | Functions | Level |
|----------|-----------|-------|
| Math | add, subtract, multiply, divide, max, min, abs, modulo, round, negate | Basic |
| String | concat, length, join, split, contains, trim, replace | Basic |
| Boolean | and, or, not | Basic |
| Comparison | eq-int, eq-string, gt, lt, gte, lte | Basic |
| List | length, first, last, is-empty, sum, concat, contains, reverse | Intermediate |
| Convert | to-string, to-int, to-float | Intermediate |
| Higher-order | filter, map, all, any | Intermediate |
| Utility | identity, log | Basic |

### HTTP API Endpoints

| Category | Key Endpoints | Level |
|----------|---------------|-------|
| Execution | `POST /run`, `POST /execute` | Basic |
| Compilation | `POST /compile` | Basic |
| Pipelines | `GET/DELETE /pipelines/{ref}`, `PUT /alias`, `POST /reload` | Intermediate |
| Suspension | `GET /executions`, `POST /resume` | Advanced |
| Canary | `GET/POST /canary/*` | Advanced |
| Discovery | `GET /modules`, `GET /namespaces` | Basic |
| Health | `/health`, `/health/live`, `/health/ready` | Basic |
| Metrics | `GET /metrics` | Intermediate |

### Security (All Opt-In)

| Feature | Level | Configuration |
|---------|-------|---------------|
| API key auth | Intermediate | `CONSTELLATION_API_KEYS` |
| Role-based access | Intermediate | Admin, Execute, ReadOnly roles |
| CORS | Basic | `CONSTELLATION_CORS_ORIGINS` |
| Rate limiting | Intermediate | `CONSTELLATION_RATE_LIMIT_RPM` |

### Tooling

| Tool | Level | Purpose |
|------|-------|---------|
| Web Dashboard | Basic | Browse, edit, run, visualize pipelines |
| VSCode Extension | Intermediate | Autocomplete, diagnostics, run, DAG view |
| LSP Protocol | Advanced | IDE integration via WebSocket |
| E2E Tests | Advanced | Playwright-based dashboard testing |

### Extensibility (SPIs)

| SPI | Level | Purpose |
|-----|-------|---------|
| `CacheBackend` | Advanced | Custom cache storage (Redis, Caffeine) |
| `ExecutionListener` | Advanced | Audit logs, event streaming |
| `MetricsProvider` | Advanced | Prometheus, Datadog, CloudWatch |
| `PipelineStore` | Advanced | Custom pipeline persistence |
| `SuspensionStore` | Advanced | Custom suspend/resume storage |

---

## Execution Patterns

### Hot Pipeline (Development)
```
Client → POST /run { source, inputs } → Compile → Execute → Response
```
- Compile + execute in one request
- ~50-100ms total
- Best for: ad-hoc queries, development, dynamic pipelines

### Cold Pipeline (Production)
```
1. POST /compile { source, name } → Store PipelineImage
2. POST /execute { ref: "name", inputs } → Execute (no compile)
```
- ~1ms execution (pre-compiled)
- Best for: production APIs, high throughput

### Suspended Pipeline (Long-running)
```
1. POST /run { source, partial_inputs } → Suspended { id, missingInputs }
2. POST /executions/{id}/resume { missing_inputs } → Complete
```
- State persisted between calls
- Best for: multi-step workflows, human-in-the-loop

---

## Configuration Reference

| Variable | Default | Purpose |
|----------|---------|---------|
| `CONSTELLATION_PORT` | 8080 | Server port |
| `CONSTELLATION_CST_DIR` | cwd | Script directory |
| `CONSTELLATION_SCHEDULER_ENABLED` | false | Enable priority scheduler |
| `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY` | 16 | Max concurrent tasks |
| `CONSTELLATION_API_KEYS` | (none) | `key:Role` pairs |
| `CONSTELLATION_CORS_ORIGINS` | (none) | Allowed origins |
| `CONSTELLATION_RATE_LIMIT_RPM` | 100 | Requests/minute |
| `CONSTELLATION_DASHBOARD_ENABLED` | true | Enable dashboard |
| `CONSTELLATION_SAMPLE_RATE` | 1.0 | Execution sampling |
| `CONSTELLATION_MAX_EXECUTIONS` | 1000 | Max stored executions |

---

## Quick Examples

### Basic Pipeline
```constellation
in name: String
greeting = concat("Hello, ", name)
out greeting
```

### With Module Options
```constellation
in userId: String
user = GetUser(userId) with retry: 3, timeout: 5s, cache: 15min
profile = EnrichProfile(user) with fallback: { tier: "basic" }
out profile
```

### Type Operations
```constellation
in orders: List<Order>
totals = orders.amount                    # element-wise: List<Float>
summary = orders[id, status]              # projection: List<{id, status}>
highValue = orders when orders.amount > 1000  # guard: Optional
result = highValue ?? []                  # coalesce: List<Order>
out result
```

### Parallel Fan-Out
```constellation
in productId: String
inventory = GetInventory(productId)    # \
pricing = GetPricing(productId)        #  > execute in parallel
reviews = GetReviews(productId)        # /
combined = inventory + pricing + reviews  # merge after all complete
out combined
```

---

## Documentation Map

| Need | Document |
|------|----------|
| First-time setup | `website/docs/getting-started/tutorial.md` |
| Language syntax | `website/docs/language/*.md` |
| Module options | `website/docs/language/options/*.md` |
| Stdlib functions | `website/docs/api-reference/stdlib.md` |
| HTTP API | `website/docs/api-reference/http-api-overview.md` |
| Embedding in Scala | `website/docs/api-reference/programmatic-api.md` |
| Custom backends | `website/docs/integrations/*.md` |
| Operations | `website/docs/operations/*.md` |
| Troubleshooting | `website/docs/tooling/troubleshooting.md` |

---

## Version History

| Version | Highlights |
|---------|------------|
| v0.4.0 | Canary deployments, pipeline versioning, rollback |
| v0.3.0 | Suspend/resume, priority scheduler, SPI interfaces |
| v0.2.0 | Module options, stdlib, caching |
| v0.1.0 | Core execution, type system, basic HTTP API |

---

*Last updated: 2026-02-05 | Constellation Engine v0.4.0*
