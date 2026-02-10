---
title: "HTTP API Reference"
sidebar_position: 1
description: "Complete HTTP API reference for Constellation Engine"
---

# HTTP API Reference

Complete reference for the Constellation Engine REST API, including all endpoints, request/response schemas, authentication, security, and configuration.

## Base URL and Configuration

```
http://localhost:8080
```

The server port is configurable via the `CONSTELLATION_PORT` environment variable (default: 8080).

### Quick Start

```scala
import io.constellation.http.ConstellationServer

// Minimal server (no hardening)
ConstellationServer
  .builder(constellation, compiler)
  .withPort(8080)
  .run

// Production server with full hardening
ConstellationServer
  .builder(constellation, compiler)
  .withAuth(AuthConfig(apiKeys = Map("key1" -> ApiRole.Admin)))
  .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  .withRateLimit(RateLimitConfig(requestsPerMinute = 100, burst = 20))
  .withHealthChecks(HealthCheckConfig(enableDetailEndpoint = true))
  .run
```

## API Endpoints Summary

### Core Operations

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/compile` | POST | Compile constellation-lang source code | Yes |
| `/execute` | POST | Execute a stored pipeline by reference | Yes |
| `/run` | POST | Compile and execute in one request | Yes |
| `/modules` | GET | List all registered modules | Yes |
| `/namespaces` | GET | List all function namespaces | Yes |
| `/namespaces/{namespace}` | GET | List functions in a namespace | Yes |

### Health and Monitoring

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/health` | GET | Basic health check (compatibility) | No |
| `/health/live` | GET | Liveness probe (always 200) | No |
| `/health/ready` | GET | Readiness probe (checks lifecycle) | No |
| `/health/detail` | GET | Detailed diagnostics (opt-in) | Configurable |
| `/metrics` | GET | Runtime metrics (cache, scheduler, uptime) | No |

### Pipeline Management

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/pipelines` | GET | List all stored pipelines | Yes |
| `/pipelines/{ref}` | GET | Get pipeline metadata by name or hash | Yes |
| `/pipelines/{ref}` | DELETE | Delete a stored pipeline | Yes |
| `/pipelines/{name}/alias` | PUT | Repoint an alias to a different hash | Yes |

### Suspension Management

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/executions` | GET | List suspended executions | Yes |
| `/executions/{id}` | GET | Get suspension detail | Yes |
| `/executions/{id}/resume` | POST | Resume with additional inputs | Yes |
| `/executions/{id}` | DELETE | Delete a suspended execution | Yes |

### Pipeline Versioning

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/pipelines/{name}/reload` | POST | Hot-reload a pipeline from new source | Yes |
| `/pipelines/{name}/versions` | GET | List version history | Yes |
| `/pipelines/{name}/rollback` | POST | Rollback to previous version | Yes |
| `/pipelines/{name}/rollback/{version}` | POST | Rollback to specific version | Yes |

### Canary Releases

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/pipelines/{name}/canary` | GET | Get canary deployment status and metrics | Yes |
| `/pipelines/{name}/canary/promote` | POST | Manually promote canary to next step | Yes |
| `/pipelines/{name}/canary/rollback` | POST | Rollback canary deployment | Yes |
| `/pipelines/{name}/canary` | DELETE | Abort canary deployment | Yes |

### WebSocket

| Endpoint | Protocol | Description | Auth Required |
|----------|----------|-------------|---------------|
| `/lsp` | WebSocket | Language Server Protocol for IDE integration | No |

---

## Core Operations

### POST /compile

Compile constellation-lang source code into an executable pipeline without running it. Returns structural and syntactic hashes for content-addressed storage.

#### Request

**Content-Type:** `application/json`

```json
{
  "source": "in text: String\nresult = Uppercase(text)\nout result",
  "name": "my-pipeline"
}
```

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `source` | String | Yes | Constellation-lang source code |
| `name` | String | No | Pipeline name (creates an alias) |
| `dagName` | String | No | Deprecated, use `name` instead |

#### Response

**Success (200 OK):**

```json
{
  "success": true,
  "structuralHash": "a1b2c3d4e5f6...",
  "syntacticHash": "f6e5d4c3b2a1...",
  "name": "my-pipeline"
}
```

**Compilation Error (400 Bad Request):**

```json
{
  "success": false,
  "errors": [
    "Line 3: Unknown module 'InvalidModule'",
    "Line 5: Type mismatch: expected String, got Int"
  ]
}
```

**Timeout (500 Internal Server Error):**

Compilation times out after 30 seconds. Consider breaking large pipelines into smaller modules.

#### Example

```bash
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "source": "in x: Int\nin y: Int\nresult = Add(x, y)\nout result",
    "name": "add-pipeline"
  }'
```

---

### POST /execute

Execute a previously compiled pipeline by reference (name or structural hash). Pipelines are retrieved from the content-addressed PipelineStore.

#### Request

**Content-Type:** `application/json`

```json
{
  "ref": "my-pipeline",
  "inputs": {
    "text": "hello world"
  }
}
```

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `ref` | String | Yes | Pipeline name, structural hash, or `sha256:<hash>` |
| `inputs` | Object | No | Input values keyed by variable name |
| `dagName` | String | No | Deprecated, use `ref` instead |

**Pipeline References:**

- **Name:** `"my-pipeline"` — resolves via alias
- **Hash (64 hex chars):** `"a1b2c3d4..."` — treated as structural hash
- **SHA-256 prefix:** `"sha256:a1b2c3..."` — explicit hash reference

#### Response

**Success (200 OK):**

```json
{
  "success": true,
  "status": "completed",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "outputs": {
    "result": "HELLO WORLD"
  },
  "resumptionCount": 0
}
```

**Suspended (200 OK):**

When inputs are incomplete, execution suspends instead of failing:

```json
{
  "success": true,
  "status": "suspended",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "outputs": {},
  "missingInputs": {
    "count": "CInt",
    "threshold": "CFloat"
  },
  "pendingOutputs": ["result", "summary"],
  "resumptionCount": 0
}
```

Resume the execution using `POST /executions/{executionId}/resume`.

**Pipeline Not Found (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "Pipeline 'my-pipeline' not found",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Input Error (400 Bad Request):**

```json
{
  "success": false,
  "error": "Input error: Type mismatch for 'count': expected Int, got String"
}
```

**Execution Failed (200 OK):**

```json
{
  "success": false,
  "status": "failed",
  "error": "Module 'Divide' failed: Division by zero",
  "outputs": {}
}
```

**Overloaded Server (429 Too Many Requests):**

```json
{
  "error": "QueueFull",
  "message": "Server is overloaded, try again later",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Shutting Down (503 Service Unavailable):**

```json
{
  "error": "ShuttingDown",
  "message": "Server is shutting down",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Example

```bash
# Execute by name
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "ref": "add-pipeline",
    "inputs": {"x": 10, "y": 32}
  }'

# Execute by hash
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "ref": "a1b2c3d4e5f6...",
    "inputs": {"x": 10, "y": 32}
  }'
```

---

### POST /run

Compile and execute constellation-lang source in a single request. Combines `/compile` and `/execute` for ad-hoc execution.

**Use when:** You have the source code and inputs ready, and don't need to store the pipeline for reuse.

**Don't use when:** You're executing the same pipeline repeatedly with different inputs (use `/compile` once, then `/execute` multiple times for better performance).

#### Request

**Content-Type:** `application/json`

```json
{
  "source": "in text: String\nresult = Uppercase(text)\nout result",
  "inputs": {
    "text": "hello world"
  }
}
```

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `source` | String | Yes | Constellation-lang source code |
| `inputs` | Object | Yes | Input values keyed by variable name |

#### Response

**Success (200 OK):**

```json
{
  "success": true,
  "status": "completed",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "structuralHash": "a1b2c3d4e5f6...",
  "outputs": {
    "result": "HELLO WORLD"
  },
  "resumptionCount": 0
}
```

**Compilation Error (400 Bad Request):**

```json
{
  "success": false,
  "compilationErrors": [
    "Line 2: Unknown module 'InvalidModule'"
  ]
}
```

**Suspended (200 OK):**

```json
{
  "success": true,
  "status": "suspended",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "structuralHash": "a1b2c3d4e5f6...",
  "outputs": {},
  "missingInputs": {
    "threshold": "CFloat"
  },
  "pendingOutputs": ["result"],
  "resumptionCount": 0
}
```

#### Example

```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "source": "in x: Int\nin y: Int\nsum = Add(x, y)\nout sum",
    "inputs": {"x": 10, "y": 32}
  }'
```

---

### GET /modules

List all registered modules with their type signatures and descriptions.

#### Response

**Success (200 OK):**

```json
{
  "modules": [
    {
      "name": "Uppercase",
      "description": "Convert text to uppercase",
      "version": "1.0",
      "inputs": {
        "text": "CString"
      },
      "outputs": {
        "result": "CString"
      }
    },
    {
      "name": "Add",
      "description": "Add two integers",
      "version": "1.0",
      "inputs": {
        "a": "CInt",
        "b": "CInt"
      },
      "outputs": {
        "result": "CInt"
      }
    }
  ]
}
```

#### Example

```bash
curl http://localhost:8080/modules \
  -H "Authorization: Bearer your-api-key"
```

---

### GET /namespaces

List all available function namespaces in the registry.

#### Response

**Success (200 OK):**

```json
{
  "namespaces": [
    "text",
    "data",
    "math",
    "stdlib"
  ]
}
```

#### Example

```bash
curl http://localhost:8080/namespaces \
  -H "Authorization: Bearer your-api-key"
```

---

### GET /namespaces/{namespace}

List all functions in a specific namespace.

#### Response

**Success (200 OK):**

```json
{
  "namespace": "text",
  "functions": [
    {
      "name": "Uppercase",
      "qualifiedName": "text.Uppercase",
      "params": ["text: CString"],
      "returns": "CString"
    },
    {
      "name": "Trim",
      "qualifiedName": "text.Trim",
      "params": ["text: CString"],
      "returns": "CString"
    }
  ]
}
```

**Namespace Not Found (404 Not Found):**

```json
{
  "error": "NamespaceNotFound",
  "message": "Namespace 'invalid' not found or has no functions"
}
```

#### Example

```bash
curl http://localhost:8080/namespaces/text \
  -H "Authorization: Bearer your-api-key"
```

---

## Health and Monitoring

### GET /health/live

Liveness probe for Kubernetes and load balancers. Always returns 200 OK if the server process is running.

#### Response

**Success (200 OK):**

```json
{
  "status": "alive"
}
```

#### Example

```bash
curl http://localhost:8080/health/live
```

---

### GET /health/ready

Readiness probe for Kubernetes and load balancers. Returns 200 OK when the server is ready to accept traffic, 503 Service Unavailable during graceful shutdown or startup.

#### Response

**Ready (200 OK):**

```json
{
  "status": "ready"
}
```

**Not Ready (503 Service Unavailable):**

```json
{
  "status": "not_ready"
}
```

Returns not ready when:
- Server is in graceful shutdown (draining)
- Custom readiness checks fail
- Lifecycle state is not Running

#### Example

```bash
curl http://localhost:8080/health/ready
```

---

### GET /health/detail

Detailed health diagnostics including lifecycle state, cache statistics, scheduler metrics, and custom readiness checks.

**Opt-in:** Must be enabled via `HealthCheckConfig(enableDetailEndpoint = true)`.

**Auth-gated by default:** Set `detailRequiresAuth = false` to make this endpoint public.

#### Response

**Success (200 OK):**

```json
{
  "timestamp": "2026-02-09T12:34:56.789Z",
  "lifecycle": {
    "state": "Running"
  },
  "cache": {
    "hits": 12543,
    "misses": 437,
    "hitRate": 0.9663,
    "evictions": 12,
    "entries": 128
  },
  "scheduler": {
    "activeCount": 3,
    "queuedCount": 15,
    "totalSubmitted": 9876,
    "totalCompleted": 9858
  },
  "readinessChecks": {
    "database": true,
    "external-api": true
  }
}
```

**Field Descriptions:**

| Field | Description |
|-------|-------------|
| `timestamp` | ISO-8601 timestamp of the health check |
| `lifecycle.state` | Server lifecycle state: `Running`, `Draining`, or `Stopped` |
| `cache.hits` | Total cache hits since startup |
| `cache.misses` | Total cache misses since startup |
| `cache.hitRate` | Hit rate as a fraction (0.0 to 1.0) |
| `cache.evictions` | Number of cache evictions |
| `cache.entries` | Current number of cached entries |
| `scheduler.activeCount` | Currently executing tasks |
| `scheduler.queuedCount` | Tasks waiting in queue |
| `scheduler.totalSubmitted` | Total tasks submitted since startup |
| `scheduler.totalCompleted` | Total tasks completed since startup |
| `readinessChecks` | Results of custom readiness checks (name → boolean) |

#### Example

```bash
curl http://localhost:8080/health/detail \
  -H "Authorization: Bearer your-api-key"
```

---

### GET /metrics

Runtime metrics for monitoring and observability. Supports content negotiation:

- **JSON:** Default format (or `Accept: application/json`)
- **Prometheus:** Use `Accept: text/plain` for Prometheus-compatible format

#### Response (JSON)

**Success (200 OK):**

```json
{
  "timestamp": "2026-02-09T12:34:56.789Z",
  "server": {
    "uptime_seconds": 86400,
    "requests_total": 123456
  },
  "cache": {
    "hits": 12543,
    "misses": 437,
    "hitRate": 0.9663,
    "evictions": 12,
    "entries": 128
  },
  "scheduler": {
    "enabled": true,
    "activeCount": 3,
    "queuedCount": 15,
    "totalSubmitted": 9876,
    "totalCompleted": 9858,
    "highPriorityCompleted": 5432,
    "lowPriorityCompleted": 4426,
    "starvationPromotions": 18
  }
}
```

#### Response (Prometheus)

**Success (200 OK), Content-Type: text/plain:**

```
# HELP constellation_server_uptime_seconds Server uptime in seconds
# TYPE constellation_server_uptime_seconds counter
constellation_server_uptime_seconds 86400

# HELP constellation_requests_total Total number of requests handled
# TYPE constellation_requests_total counter
constellation_requests_total 123456

# HELP constellation_cache_hits_total Total cache hits
# TYPE constellation_cache_hits_total counter
constellation_cache_hits_total 12543

# HELP constellation_cache_misses_total Total cache misses
# TYPE constellation_cache_misses_total counter
constellation_cache_misses_total 437

# HELP constellation_cache_hit_rate Cache hit rate
# TYPE constellation_cache_hit_rate gauge
constellation_cache_hit_rate 0.9663

# HELP constellation_cache_entries Current number of cached entries
# TYPE constellation_cache_entries gauge
constellation_cache_entries 128
```

#### Example

```bash
# JSON format
curl http://localhost:8080/metrics

# Prometheus format
curl http://localhost:8080/metrics \
  -H "Accept: text/plain"
```

---

## Pipeline Management

### GET /pipelines

List all stored pipeline images with their metadata.

#### Response

**Success (200 OK):**

```json
{
  "pipelines": [
    {
      "structuralHash": "a1b2c3d4e5f6...",
      "syntacticHash": "f6e5d4c3b2a1...",
      "aliases": ["my-pipeline", "prod-pipeline"],
      "compiledAt": "2026-02-09T10:15:30.456Z",
      "moduleCount": 5,
      "declaredOutputs": ["result", "summary"]
    }
  ]
}
```

#### Example

```bash
curl http://localhost:8080/pipelines \
  -H "Authorization: Bearer your-api-key"
```

---

### GET /pipelines/{ref}

Get detailed metadata for a specific pipeline by name or structural hash.

#### Response

**Success (200 OK):**

```json
{
  "structuralHash": "a1b2c3d4e5f6...",
  "syntacticHash": "f6e5d4c3b2a1...",
  "aliases": ["my-pipeline"],
  "compiledAt": "2026-02-09T10:15:30.456Z",
  "declaredOutputs": ["result"],
  "inputSchema": {
    "text": "CString",
    "count": "CInt"
  },
  "outputSchema": {
    "result": "CString"
  },
  "modules": [
    {
      "name": "Uppercase",
      "description": "Convert text to uppercase",
      "version": "1.0",
      "inputs": {"text": "CString"},
      "outputs": {"result": "CString"}
    }
  ]
}
```

**Pipeline Not Found (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "Pipeline 'my-pipeline' not found"
}
```

#### Example

```bash
# By name
curl http://localhost:8080/pipelines/my-pipeline \
  -H "Authorization: Bearer your-api-key"

# By hash
curl http://localhost:8080/pipelines/a1b2c3d4e5f6... \
  -H "Authorization: Bearer your-api-key"
```

---

### DELETE /pipelines/{ref}

Delete a stored pipeline by name or structural hash. Fails if other aliases still point to the same structural hash.

#### Response

**Success (200 OK):**

```json
{
  "deleted": true
}
```

**Alias Conflict (409 Conflict):**

```json
{
  "error": "AliasConflict",
  "message": "Cannot delete pipeline: aliases [prod-pipeline, backup-pipeline] point to it"
}
```

**Pipeline Not Found (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "Pipeline 'my-pipeline' not found"
}
```

#### Example

```bash
curl -X DELETE http://localhost:8080/pipelines/my-pipeline \
  -H "Authorization: Bearer your-api-key"
```

---

### PUT /pipelines/{name}/alias

Repoint an alias to a different structural hash. Creates the alias if it doesn't exist.

#### Request

**Content-Type:** `application/json`

```json
{
  "structuralHash": "a1b2c3d4e5f6..."
}
```

#### Response

**Success (200 OK):**

```json
{
  "name": "my-pipeline",
  "structuralHash": "a1b2c3d4e5f6..."
}
```

**Pipeline Not Found (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "Pipeline with hash 'a1b2c3d4e5f6...' not found"
}
```

#### Example

```bash
curl -X PUT http://localhost:8080/pipelines/my-pipeline/alias \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{"structuralHash": "a1b2c3d4e5f6..."}'
```

---

## Suspension Management

### GET /executions

List all suspended executions. Requires suspension store to be configured.

#### Response

**Success (200 OK):**

```json
{
  "executions": [
    {
      "executionId": "550e8400-e29b-41d4-a716-446655440000",
      "structuralHash": "a1b2c3d4e5f6...",
      "resumptionCount": 2,
      "missingInputs": {
        "threshold": "CFloat",
        "maxRetries": "CInt"
      },
      "createdAt": "2026-02-09T10:15:30.456Z"
    }
  ]
}
```

**No Suspension Store (200 OK):**

```json
{
  "executions": []
}
```

#### Example

```bash
curl http://localhost:8080/executions \
  -H "Authorization: Bearer your-api-key"
```

---

### GET /executions/{id}

Get detailed information about a suspended execution.

#### Response

**Success (200 OK):**

```json
{
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "structuralHash": "a1b2c3d4e5f6...",
  "resumptionCount": 2,
  "missingInputs": {
    "threshold": "CFloat",
    "maxRetries": "CInt"
  },
  "createdAt": "2026-02-09T10:15:30.456Z"
}
```

**Execution Not Found (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "Execution '550e8400-e29b-41d4-a716-446655440000' not found"
}
```

#### Example

```bash
curl http://localhost:8080/executions/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer your-api-key"
```

---

### POST /executions/{id}/resume

Resume a suspended execution by providing missing inputs or manually resolved node values.

#### Request

**Content-Type:** `application/json`

```json
{
  "additionalInputs": {
    "threshold": 0.95,
    "maxRetries": 3
  },
  "resolvedNodes": {
    "computed_value": "override"
  }
}
```

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `additionalInputs` | Object | No | New input values keyed by variable name |
| `resolvedNodes` | Object | No | Manually-resolved data node values keyed by variable name |

#### Response

**Success (200 OK):**

Same format as `/execute` response. May complete or suspend again.

```json
{
  "success": true,
  "status": "completed",
  "executionId": "550e8400-e29b-41d4-a716-446655440000",
  "outputs": {
    "result": "final output"
  },
  "resumptionCount": 3
}
```

**Resume In Progress (409 Conflict):**

```json
{
  "error": "ResumeInProgress",
  "message": "A resume operation is already in progress for execution '550e8400-e29b-41d4-a716-446655440000'",
  "requestId": "550e8400-e29b-41d4-a716-446655440001"
}
```

**Input Error (400 Bad Request):**

```json
{
  "success": false,
  "error": "Input error: Type mismatch for 'threshold': expected Float, got String"
}
```

**Execution Not Found (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "Execution '550e8400-e29b-41d4-a716-446655440000' not found",
  "requestId": "550e8400-e29b-41d4-a716-446655440001"
}
```

#### Example

```bash
curl -X POST http://localhost:8080/executions/550e8400-e29b-41d4-a716-446655440000/resume \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "additionalInputs": {
      "threshold": 0.95,
      "maxRetries": 3
    }
  }'
```

---

### DELETE /executions/{id}

Delete a suspended execution from the suspension store.

#### Response

**Success (200 OK):**

```json
{
  "deleted": true
}
```

**Execution Not Found (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "Execution '550e8400-e29b-41d4-a716-446655440000' not found"
}
```

#### Example

```bash
curl -X DELETE http://localhost:8080/executions/550e8400-e29b-41d4-a716-446655440000 \
  -H "Authorization: Bearer your-api-key"
```

---

## Pipeline Versioning

Pipeline versioning must be enabled on the server (via `PipelineVersionStore`). These endpoints manage the version history of named pipelines.

### POST /pipelines/{name}/reload

Hot-reload a named pipeline from new source code. Compiles the source, stores the new image, and atomically updates the alias (unless starting a canary deployment).

#### Request

**Content-Type:** `application/json`

```json
{
  "source": "in text: String\nresult = Lowercase(text)\nout result"
}
```

**With Canary:**

```json
{
  "source": "in text: String\nresult = Lowercase(text)\nout result",
  "canary": {
    "initialWeight": 0.05,
    "promotionSteps": [0.10, 0.25, 0.50, 1.0],
    "observationWindow": "5m",
    "errorThreshold": 0.05,
    "autoPromote": true
  }
}
```

**Fields:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `source` | String | No | New source code. If omitted, re-reads from file (if path known) |
| `canary` | Object | No | Canary deployment configuration |
| `canary.initialWeight` | Float | No | Initial traffic percentage (0.0 to 1.0), default: 0.05 |
| `canary.promotionSteps` | Array[Float] | No | Traffic percentages for each promotion step |
| `canary.observationWindow` | String | No | Duration to observe metrics before auto-promotion (e.g. "5m") |
| `canary.errorThreshold` | Float | No | Max error rate before rollback (0.0 to 1.0) |
| `canary.latencyThresholdMs` | Long | No | Max p99 latency before rollback (milliseconds) |
| `canary.minRequests` | Int | No | Minimum requests before promotion |
| `canary.autoPromote` | Boolean | No | Enable automatic promotion, default: false |

#### Response

**Success (200 OK):**

```json
{
  "success": true,
  "previousHash": "f6e5d4c3b2a1...",
  "newHash": "a1b2c3d4e5f6...",
  "name": "my-pipeline",
  "changed": true,
  "version": 3
}
```

**Unchanged (200 OK):**

If the structural hash didn't change (no semantic changes):

```json
{
  "success": true,
  "previousHash": "a1b2c3d4e5f6...",
  "newHash": "a1b2c3d4e5f6...",
  "name": "my-pipeline",
  "changed": false,
  "version": 2
}
```

**With Canary Started (200 OK):**

```json
{
  "success": true,
  "previousHash": "f6e5d4c3b2a1...",
  "newHash": "a1b2c3d4e5f6...",
  "name": "my-pipeline",
  "changed": true,
  "version": 3,
  "canary": {
    "pipelineName": "my-pipeline",
    "oldVersion": {"version": 2, "structuralHash": "f6e5d4c3b2a1..."},
    "newVersion": {"version": 3, "structuralHash": "a1b2c3d4e5f6..."},
    "currentWeight": 0.05,
    "currentStep": 0,
    "status": "observing",
    "startedAt": "2026-02-09T12:00:00.000Z",
    "metrics": {
      "oldVersion": {"requests": 0, "successes": 0, "failures": 0, "avgLatencyMs": 0.0, "p99LatencyMs": 0.0},
      "newVersion": {"requests": 0, "successes": 0, "failures": 0, "avgLatencyMs": 0.0, "p99LatencyMs": 0.0}
    }
  }
}
```

**Compilation Error (400 Bad Request):**

```json
{
  "error": "CompilationError",
  "message": "Line 2: Unknown module 'InvalidModule'"
}
```

**No Source (400 Bad Request):**

```json
{
  "error": "NoSource",
  "message": "No source provided and no file path known for this pipeline"
}
```

**Canary Conflict (409 Conflict):**

```json
{
  "error": "CanaryConflict",
  "message": "No previous version exists for canary deployment"
}
```

#### Example

```bash
# Simple reload
curl -X POST http://localhost:8080/pipelines/my-pipeline/reload \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "source": "in text: String\nresult = Lowercase(text)\nout result"
  }'

# Reload with canary
curl -X POST http://localhost:8080/pipelines/my-pipeline/reload \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer your-api-key" \
  -d '{
    "source": "in text: String\nresult = Lowercase(text)\nout result",
    "canary": {
      "initialWeight": 0.05,
      "promotionSteps": [0.10, 0.25, 0.50, 1.0],
      "observationWindow": "5m",
      "errorThreshold": 0.05,
      "autoPromote": true
    }
  }'
```

---

### GET /pipelines/{name}/versions

List version history for a named pipeline.

#### Response

**Success (200 OK):**

```json
{
  "name": "my-pipeline",
  "activeVersion": 3,
  "versions": [
    {
      "version": 1,
      "structuralHash": "f6e5d4c3b2a1...",
      "createdAt": "2026-02-08T10:00:00.000Z",
      "active": false
    },
    {
      "version": 2,
      "structuralHash": "a1b2c3d4e5f6...",
      "createdAt": "2026-02-08T14:30:00.000Z",
      "active": false
    },
    {
      "version": 3,
      "structuralHash": "b2c3d4e5f6a1...",
      "createdAt": "2026-02-09T09:15:00.000Z",
      "active": true
    }
  ]
}
```

**Pipeline Not Found (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "Pipeline 'my-pipeline' not found"
}
```

**Versioning Not Enabled (400 Bad Request):**

```json
{
  "error": "VersioningNotEnabled",
  "message": "Versioning not enabled"
}
```

#### Example

```bash
curl http://localhost:8080/pipelines/my-pipeline/versions \
  -H "Authorization: Bearer your-api-key"
```

---

### POST /pipelines/{name}/rollback

Rollback to the previous version of a pipeline. Atomically updates the alias to point to the previous structural hash.

#### Response

**Success (200 OK):**

```json
{
  "success": true,
  "name": "my-pipeline",
  "previousVersion": 3,
  "activeVersion": 2,
  "structuralHash": "a1b2c3d4e5f6..."
}
```

**No Previous Version (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "No previous version exists for pipeline 'my-pipeline'"
}
```

**Pipeline Not Found (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "Pipeline 'my-pipeline' not found"
}
```

**Versioning Not Enabled (400 Bad Request):**

```json
{
  "error": "VersioningNotEnabled",
  "message": "Versioning not enabled"
}
```

#### Example

```bash
curl -X POST http://localhost:8080/pipelines/my-pipeline/rollback \
  -H "Authorization: Bearer your-api-key"
```

---

### POST /pipelines/{name}/rollback/{version}

Rollback to a specific version of a pipeline.

#### Response

Same as `/rollback` endpoint.

#### Example

```bash
curl -X POST http://localhost:8080/pipelines/my-pipeline/rollback/2 \
  -H "Authorization: Bearer your-api-key"
```

---

## Canary Releases

Canary deployments split traffic between an old and new pipeline version, gradually promoting the new version if metrics are healthy.

### GET /pipelines/{name}/canary

Get the current canary deployment status and metrics for a pipeline.

#### Response

**Success (200 OK):**

```json
{
  "pipelineName": "my-pipeline",
  "oldVersion": {
    "version": 2,
    "structuralHash": "f6e5d4c3b2a1..."
  },
  "newVersion": {
    "version": 3,
    "structuralHash": "a1b2c3d4e5f6..."
  },
  "currentWeight": 0.25,
  "currentStep": 2,
  "status": "observing",
  "startedAt": "2026-02-09T12:00:00.000Z",
  "metrics": {
    "oldVersion": {
      "requests": 750,
      "successes": 745,
      "failures": 5,
      "avgLatencyMs": 123.4,
      "p99LatencyMs": 456.7
    },
    "newVersion": {
      "requests": 250,
      "successes": 248,
      "failures": 2,
      "avgLatencyMs": 115.2,
      "p99LatencyMs": 432.1
    }
  }
}
```

**Canary Status Values:**

| Status | Description |
|--------|-------------|
| `observing` | Collecting metrics during observation window |
| `promoting` | Promotion in progress (transitional) |
| `rolled_back` | Canary rolled back due to failed health checks |
| `complete` | Canary completed successfully, 100% on new version |

**No Canary (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "No canary deployment active for pipeline 'my-pipeline'"
}
```

**Canary Not Enabled (400 Bad Request):**

```json
{
  "error": "CanaryNotEnabled",
  "message": "Canary routing not enabled"
}
```

#### Example

```bash
curl http://localhost:8080/pipelines/my-pipeline/canary \
  -H "Authorization: Bearer your-api-key"
```

---

### POST /pipelines/{name}/canary/promote

Manually advance the canary deployment to the next promotion step.

#### Response

**Success (200 OK):**

Returns updated canary state (same format as `GET /canary`).

**No Canary (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "No canary deployment active for pipeline 'my-pipeline'"
}
```

**Canary Not Enabled (400 Bad Request):**

```json
{
  "error": "CanaryNotEnabled",
  "message": "Canary routing not enabled"
}
```

#### Example

```bash
curl -X POST http://localhost:8080/pipelines/my-pipeline/canary/promote \
  -H "Authorization: Bearer your-api-key"
```

---

### POST /pipelines/{name}/canary/rollback

Rollback the canary deployment, routing all traffic back to the old version.

#### Response

**Success (200 OK):**

Returns updated canary state with `status: "rolled_back"`.

**No Canary (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "No canary deployment active for pipeline 'my-pipeline'"
}
```

#### Example

```bash
curl -X POST http://localhost:8080/pipelines/my-pipeline/canary/rollback \
  -H "Authorization: Bearer your-api-key"
```

---

### DELETE /pipelines/{name}/canary

Abort the canary deployment and route all traffic back to the old version.

#### Response

**Success (200 OK):**

Returns updated canary state with `status: "rolled_back"`.

**No Canary (404 Not Found):**

```json
{
  "error": "NotFound",
  "message": "No canary deployment active for pipeline 'my-pipeline'"
}
```

#### Example

```bash
curl -X DELETE http://localhost:8080/pipelines/my-pipeline/canary \
  -H "Authorization: Bearer your-api-key"
```

---

## Authentication

Authentication is opt-in via `AuthConfig`. When enabled, all requests except public paths must include an API key in the `Authorization` header.

### Configuring Authentication

**Environment Variable:**

```bash
export CONSTELLATION_API_KEYS="admin-key-123:Admin,exec-key-456:Execute,read-key-789:ReadOnly"
```

**Programmatic:**

```scala
val authConfig = AuthConfig(apiKeys = Map(
  "admin-key-123" -> ApiRole.Admin,
  "exec-key-456" -> ApiRole.Execute,
  "read-key-789" -> ApiRole.ReadOnly
))

ConstellationServer
  .builder(constellation, compiler)
  .withAuth(authConfig)
  .run
```

### API Key Requirements

- **Minimum length:** 24 characters
- **No control characters:** Must be printable ASCII/UTF-8
- **Format:** `key:Role` where Role is `Admin`, `Execute`, or `ReadOnly` (case-insensitive)

### Roles and Permissions

| Role | HTTP Methods | Use Case |
|------|--------------|----------|
| `Admin` | GET, POST, PUT, DELETE, PATCH | Full access to all endpoints |
| `Execute` | GET, POST | Execute pipelines, read metadata (no deletion) |
| `ReadOnly` | GET | Read-only access (monitoring, inspection) |

### Public Paths

These paths are always accessible without authentication:

- `/health`
- `/health/live`
- `/health/ready`
- `/metrics`

### Using API Keys

**Request Header:**

```
Authorization: Bearer your-api-key
```

**Example:**

```bash
curl http://localhost:8080/modules \
  -H "Authorization: Bearer admin-key-123"
```

### Error Responses

**Missing Authorization (401 Unauthorized):**

```json
{
  "error": "Unauthorized",
  "message": "Missing or invalid Authorization header. Expected: Bearer <api-key>"
}
```

**Invalid API Key (401 Unauthorized):**

```json
{
  "error": "Unauthorized",
  "message": "Invalid API key"
}
```

**Insufficient Permissions (403 Forbidden):**

```json
{
  "error": "Forbidden",
  "message": "Role 'ReadOnly' does not permit POST requests"
}
```

### Security Best Practices

1. **Use strong keys:** Generate random keys with at least 32 characters
2. **Rotate keys regularly:** Update keys periodically via environment variables
3. **Use HTTPS in production:** API keys are transmitted in headers (use TLS)
4. **Don't commit keys:** Store keys in secrets management systems (Vault, AWS Secrets Manager)
5. **Log access:** Monitor authentication failures for suspicious activity

---

## CORS Configuration

Cross-Origin Resource Sharing (CORS) is opt-in via `CorsConfig`. When enabled, the server sends appropriate CORS headers to allow browser-based clients.

### Configuring CORS

**Environment Variable:**

```bash
export CONSTELLATION_CORS_ORIGINS="https://app.example.com,https://admin.example.com"
```

**Programmatic:**

```scala
val corsConfig = CorsConfig(
  allowedOrigins = Set("https://app.example.com", "https://admin.example.com"),
  allowedMethods = Set("GET", "POST", "PUT", "DELETE", "OPTIONS"),
  allowedHeaders = Set("Content-Type", "Authorization"),
  allowCredentials = false,
  maxAge = 3600
)

ConstellationServer
  .builder(constellation, compiler)
  .withCors(corsConfig)
  .run
```

### CORS Options

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `allowedOrigins` | Set[String] | Empty | Allowed origin URLs (e.g. `https://app.example.com`) |
| `allowedMethods` | Set[String] | GET, POST, PUT, DELETE, OPTIONS | HTTP methods allowed in CORS requests |
| `allowedHeaders` | Set[String] | Content-Type, Authorization | Headers the client may send |
| `allowCredentials` | Boolean | false | Whether to include credentials (cookies, auth headers) |
| `maxAge` | Long | 3600 | Preflight cache duration in seconds |

### Wildcard Origins

**Development only:**

```bash
export CONSTELLATION_CORS_ORIGINS="*"
```

**Note:** Cannot combine `allowCredentials = true` with wildcard origins.

### Origin Validation

- **HTTPS required:** Non-localhost origins must use HTTPS
- **Localhost exception:** `http://localhost`, `http://127.0.0.1`, `http://[::1]` are allowed
- **Malformed URLs rejected:** Invalid origin formats are logged and skipped

### CORS Headers

When CORS is enabled, the server sends:

- `Access-Control-Allow-Origin: <origin>`
- `Access-Control-Allow-Methods: GET, POST, ...`
- `Access-Control-Allow-Headers: Content-Type, Authorization, ...`
- `Access-Control-Max-Age: 3600`
- `Access-Control-Allow-Credentials: true` (if enabled)

---

## Rate Limiting

Rate limiting is opt-in via `RateLimitConfig`. When enabled, the server enforces per-IP and per-API-key rate limits using token bucket algorithm.

### Configuring Rate Limiting

**Environment Variables:**

```bash
export CONSTELLATION_RATE_LIMIT_RPM=100      # Requests per minute per IP
export CONSTELLATION_RATE_LIMIT_BURST=20     # Burst size (token bucket capacity)
```

**Programmatic:**

```scala
val rateLimitConfig = RateLimitConfig(
  requestsPerMinute = 100,
  burst = 20,
  keyRequestsPerMinute = 200,
  keyBurst = 40
)

ConstellationServer
  .builder(constellation, compiler)
  .withRateLimit(rateLimitConfig)
  .run
```

### Rate Limit Options

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `requestsPerMinute` | Int | 100 | Sustained requests per minute per IP |
| `burst` | Int | 20 | Maximum burst size (token bucket capacity) |
| `keyRequestsPerMinute` | Int | 200 | Sustained requests per minute per API key |
| `keyBurst` | Int | 40 | Maximum burst size for API keys |
| `exemptPaths` | Set[String] | `/health`, `/health/live`, `/health/ready`, `/metrics` | Paths exempt from rate limiting |

### Two-Layer Rate Limiting

Rate limiting is applied in two layers:

1. **Per-IP:** Every client IP is rate-limited independently
2. **Per-API-key:** Authenticated clients are also rate-limited by their API key

A request must pass **both** checks. This prevents:
- A single API key from monopolizing an IP's budget
- Multiple keys on the same IP from bypassing the IP limit

### Exempt Paths

Public monitoring endpoints bypass rate limiting (prefix match):

- `/health`
- `/health/live`
- `/health/ready`
- `/metrics`

### Error Response

**Rate Limit Exceeded (429 Too Many Requests):**

```json
{
  "error": "RateLimitExceeded",
  "message": "Too many requests, please try again later"
}
```

**Headers:**

```
Retry-After: 60
```

### Token Bucket Algorithm

- **Tokens:** Each request consumes 1 token
- **Refill rate:** Tokens refill at `requestsPerMinute / 60` per second
- **Capacity:** Token bucket holds up to `burst` tokens
- **Non-blocking:** Uses `tryAcquire` (no waiting, immediate 429 response)

### Best Practices

1. **Set burst > RPM/60:** Allow short bursts above sustained rate
2. **Monitor 429 responses:** Track rate limit rejections in metrics
3. **Use exponential backoff:** Clients should back off when hitting limits
4. **Adjust per workload:** Higher limits for batch processing, lower for interactive

---

## Error Responses

All error responses follow a consistent JSON format:

```json
{
  "error": "ErrorCode",
  "message": "Human-readable error description",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Common Error Codes

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 400 Bad Request | `InvalidRequest` | Malformed request body or parameters |
| 400 Bad Request | `CompilationError` | Source code failed to compile |
| 400 Bad Request | `InputError` | Input values don't match expected types |
| 400 Bad Request | `VersioningNotEnabled` | Versioning endpoints require server config |
| 400 Bad Request | `CanaryNotEnabled` | Canary endpoints require server config |
| 401 Unauthorized | `Unauthorized` | Missing or invalid API key |
| 403 Forbidden | `Forbidden` | API key role doesn't permit this operation |
| 404 Not Found | `NotFound` | Resource (pipeline, execution, etc.) not found |
| 404 Not Found | `NamespaceNotFound` | Function namespace doesn't exist |
| 409 Conflict | `AliasConflict` | Cannot delete pipeline with active aliases |
| 409 Conflict | `ResumeInProgress` | Concurrent resume operation detected |
| 409 Conflict | `CanaryConflict` | Canary deployment precondition failed |
| 413 Payload Too Large | `PayloadTooLarge` | Request body exceeds 10MB limit |
| 429 Too Many Requests | `RateLimitExceeded` | Rate limit exceeded, retry later |
| 429 Too Many Requests | `QueueFull` | Server overloaded, queue full |
| 500 Internal Server Error | `InternalError` | Unexpected server error |
| 503 Service Unavailable | `ShuttingDown` | Server in graceful shutdown |

### Request ID

All error responses include a `requestId` field for tracing. The ID is either:
- Extracted from `X-Request-ID` request header (if provided)
- Generated as a random UUID (if not provided)

Use request IDs to correlate logs and troubleshoot issues.

---

## Configuration Reference

### Environment Variables

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `CONSTELLATION_PORT` | Int | 8080 | HTTP server port |
| `CONSTELLATION_API_KEYS` | String | (none) | Comma-separated `key:Role` pairs for authentication |
| `CONSTELLATION_CORS_ORIGINS` | String | (none) | Comma-separated origin URLs for CORS |
| `CONSTELLATION_RATE_LIMIT_RPM` | Int | 100 | Requests per minute per client IP |
| `CONSTELLATION_RATE_LIMIT_BURST` | Int | 20 | Burst size for rate limiter token bucket |
| `CONSTELLATION_SCHEDULER_ENABLED` | Boolean | false | Enable bounded scheduler with priority ordering |
| `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY` | Int | 16 | Maximum concurrent tasks when scheduler enabled |
| `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` | Duration | 30s | Time before low-priority tasks get priority boost |

### Programmatic Configuration

**Minimal Server:**

```scala
import io.constellation.http.ConstellationServer

ConstellationServer
  .builder(constellation, compiler)
  .withPort(8080)
  .run
```

**Production Server:**

```scala
import io.constellation.http.*
import java.nio.file.Paths

ConstellationServer
  .builder(constellation, compiler)
  .withHost("0.0.0.0")
  .withPort(8080)
  .withAuth(AuthConfig(apiKeys = Map(
    "admin-key-123" -> ApiRole.Admin,
    "exec-key-456" -> ApiRole.Execute
  )))
  .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  .withRateLimit(RateLimitConfig(requestsPerMinute = 100, burst = 20))
  .withHealthChecks(HealthCheckConfig(
    enableDetailEndpoint = true,
    detailRequiresAuth = true
  ))
  .withPipelineLoader(PipelineLoaderConfig(
    directory = Paths.get("pipelines"),
    watchForChanges = true
  ))
  .withPersistentPipelineStore(Paths.get(".constellation-store"))
  .run
```

### Builder Methods

| Method | Parameters | Description |
|--------|------------|-------------|
| `.withHost(host)` | String | Server bind address (default: "0.0.0.0") |
| `.withPort(port)` | Int | Server port (default: 8080) |
| `.withAuth(config)` | AuthConfig | Enable API key authentication |
| `.withCors(config)` | CorsConfig | Enable CORS headers |
| `.withRateLimit(config)` | RateLimitConfig | Enable per-IP and per-key rate limiting |
| `.withHealthChecks(config)` | HealthCheckConfig | Configure health check endpoints |
| `.withDashboard` | None | Enable dashboard with default config |
| `.withDashboard(config)` | DashboardConfig | Enable dashboard with custom config |
| `.withDashboard(dir)` | Path | Enable dashboard with CST directory |
| `.withPipelineLoader(config)` | PipelineLoaderConfig | Pre-load pipelines from directory on startup |
| `.withPersistentPipelineStore(dir)` | Path | Enable filesystem-backed pipeline store |
| `.withExecutionWebSocket(ws)` | ExecutionWebSocket | Enable WebSocket for live execution events |

---

## WebSocket Endpoints

### LSP WebSocket (WS /lsp)

Language Server Protocol endpoint for IDE integration. Provides real-time diagnostics, autocomplete, and hover information.

**Protocol:** JSON-RPC 2.0 over WebSocket

See [LSP WebSocket Documentation](./lsp-websocket.md) for full protocol details.

**Example (JavaScript):**

```javascript
const ws = new WebSocket('ws://localhost:8080/lsp');

ws.onopen = () => {
  // Initialize LSP
  ws.send(JSON.stringify({
    jsonrpc: "2.0",
    id: 1,
    method: "initialize",
    params: {
      capabilities: {}
    }
  }));
};

ws.onmessage = (event) => {
  const response = JSON.parse(event.data);
  console.log('LSP Response:', response);
};
```

---

## Performance Considerations

### Request Body Size Limit

Maximum request body size is **10MB** (10,485,760 bytes). Requests exceeding this limit receive:

**413 Payload Too Large:**

```json
{
  "error": "PayloadTooLarge",
  "message": "Request body too large: 12345678 bytes (max 10485760)"
}
```

**Mitigation:**
- Break large pipelines into smaller modules
- Stream large inputs via suspension/resume
- Compress payloads before sending (gzip)

### Compilation Timeout

Compilation operations timeout after **30 seconds**. If compilation takes longer:

**500 Internal Server Error:**

```json
{
  "success": false,
  "error": "Unexpected error: Compilation timed out after 30 seconds"
}
```

**Mitigation:**
- Simplify complex pipelines
- Use incremental compilation (pre-compile modules)
- Increase timeout in server configuration (advanced)

### Caching

The server uses `CachingLangCompiler` for compilation caching. Cache statistics are available via `/metrics`.

**Target hit rate:** > 80% for stable codebases

**Cache key:** SHA-256 hash of source code (content-addressed)

### Idle Timeout

WebSocket connections timeout after **10 minutes** of inactivity. Send periodic pings to keep connections alive.

---

## Examples

### Complete Workflow

```bash
# 1. Check server health
curl http://localhost:8080/health/ready

# 2. List available modules
curl http://localhost:8080/modules \
  -H "Authorization: Bearer admin-key-123"

# 3. Compile a pipeline
PIPELINE_HASH=$(curl -s -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer admin-key-123" \
  -d '{
    "source": "in text: String\ncleaned = Trim(text)\nresult = Uppercase(cleaned)\nout result",
    "name": "text-pipeline"
  }' | jq -r '.structuralHash')

# 4. Execute the pipeline
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer admin-key-123" \
  -d "{
    \"ref\": \"$PIPELINE_HASH\",
    \"inputs\": {\"text\": \"  hello world  \"}
  }"

# 5. Check metrics
curl http://localhost:8080/metrics \
  -H "Accept: text/plain"
```

### Handling Suspensions

```bash
# 1. Execute with incomplete inputs
RESPONSE=$(curl -s -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer admin-key-123" \
  -d '{
    "ref": "my-pipeline",
    "inputs": {"text": "hello"}
  }')

# 2. Check if suspended
STATUS=$(echo $RESPONSE | jq -r '.status')
if [ "$STATUS" = "suspended" ]; then
  # 3. Extract execution ID and missing inputs
  EXEC_ID=$(echo $RESPONSE | jq -r '.executionId')
  echo "Execution suspended: $EXEC_ID"
  echo "Missing inputs:" $(echo $RESPONSE | jq -r '.missingInputs')

  # 4. Resume with additional inputs
  curl -X POST "http://localhost:8080/executions/$EXEC_ID/resume" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer admin-key-123" \
    -d '{
      "additionalInputs": {
        "count": 42,
        "threshold": 0.95
      }
    }'
fi
```

### Hot-Reload with Canary

```bash
# 1. Reload pipeline with canary deployment
curl -X POST http://localhost:8080/pipelines/my-pipeline/reload \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer admin-key-123" \
  -d '{
    "source": "in text: String\nresult = Lowercase(text)\nout result",
    "canary": {
      "initialWeight": 0.05,
      "promotionSteps": [0.10, 0.25, 0.50, 1.0],
      "observationWindow": "5m",
      "errorThreshold": 0.05,
      "autoPromote": true
    }
  }'

# 2. Monitor canary metrics
watch -n 10 'curl -s http://localhost:8080/pipelines/my-pipeline/canary \
  -H "Authorization: Bearer admin-key-123" | jq ".metrics"'

# 3. Manually promote if healthy
curl -X POST http://localhost:8080/pipelines/my-pipeline/canary/promote \
  -H "Authorization: Bearer admin-key-123"

# 4. Or rollback if unhealthy
curl -X POST http://localhost:8080/pipelines/my-pipeline/canary/rollback \
  -H "Authorization: Bearer admin-key-123"
```

---

## See Also

- [CLI Reference](./cli-reference.md) — Command-line interface for local development
- [Key Concepts](../key-concepts.md) — Core concepts (pipelines, modules, DAGs)
- [Getting Started](../getting-started.md) — Quick start guide
- [Pipeline Lifecycle](../foundations/pipeline-lifecycle.md) — Compilation, execution, suspension
- [Error Handling Patterns](../patterns/error-handling.md) — Resilience and error recovery
