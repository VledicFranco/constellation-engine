---
title: "HTTP API Overview"
sidebar_position: 1
description: "REST API endpoints for compiling and executing constellation-lang pipelines"
---

# HTTP API Overview

Constellation Engine exposes a REST API for compiling and executing pipelines, plus a WebSocket endpoint for IDE integration via the Language Server Protocol.

## Base URL

```
http://localhost:8080
```

The port is configurable via the `CONSTELLATION_PORT` environment variable.

## Endpoints

### Core

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check (liveness) |
| `/health/live` | GET | Liveness probe |
| `/health/ready` | GET | Readiness probe |
| `/health/detail` | GET | Detailed diagnostics (auth-gated, opt-in) |
| `/modules` | GET | List registered modules with type signatures |
| `/compile` | POST | Compile constellation-lang source, return DAG |
| `/run` | POST | Compile and execute in one call |
| `/execute` | POST | Execute a previously stored pipeline |
| `/metrics` | GET | Runtime metrics (cache stats, execution counts) |
| `/lsp` | WebSocket | Language Server Protocol for IDE support |

### Pipeline Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/pipelines` | GET | List stored pipelines |
| `/pipelines/{ref}` | GET | Get pipeline metadata by name or hash |
| `/pipelines/{ref}` | DELETE | Delete a stored pipeline |
| `/pipelines/{name}/alias` | PUT | Repoint a pipeline alias to a different hash |

### Suspension Management

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/executions` | GET | List suspended executions |
| `/executions/{id}` | GET | Get suspension detail |
| `/executions/{id}/resume` | POST | Resume with additional inputs |
| `/executions/{id}` | DELETE | Delete a suspended execution |

### Pipeline Versioning

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/pipelines/{name}/reload` | POST | Hot-reload a pipeline from new source |
| `/pipelines/{name}/versions` | GET | List version history |
| `/pipelines/{name}/rollback` | POST | Rollback to previous version |
| `/pipelines/{name}/rollback/{version}` | POST | Rollback to a specific version |

### Canary Releases

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/pipelines/{name}/canary` | GET | Get canary deployment status and metrics |
| `/pipelines/{name}/canary/promote` | POST | Manually promote canary to next step |
| `/pipelines/{name}/canary/rollback` | POST | Rollback canary deployment |
| `/pipelines/{name}/canary` | DELETE | Abort canary deployment |

---

## Compile

Compile constellation-lang source and return the execution DAG without running it.

```bash
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in text: String\nresult = Uppercase(text)\nout result"
  }'
```

## Run

Compile and execute a pipeline in a single request.

```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in text: String\nresult = Uppercase(text)\nout result",
    "inputs": { "text": "hello world" }
  }'
```

## Health Checks

```bash
# Basic health check
curl http://localhost:8080/health

# Kubernetes liveness probe
curl http://localhost:8080/health/live

# Kubernetes readiness probe
curl http://localhost:8080/health/ready
```

---

## Suspension Management

Pipelines that encounter missing inputs suspend instead of failing. Use these endpoints to manage suspended executions.

### List Suspended Executions

```bash
curl http://localhost:8080/executions
```

Returns a list of execution summaries with `executionId`, `structuralHash`, `resumptionCount`, `missingInputs`, and `createdAt`.

### Get Suspension Detail

```bash
curl http://localhost:8080/executions/{id}
```

Returns the full detail of a single suspended execution, including all missing inputs and their expected types.

### Resume a Suspended Execution

```bash
curl -X POST http://localhost:8080/executions/{id}/resume \
  -H "Content-Type: application/json" \
  -d '{
    "additionalInputs": { "missingField": "value" }
  }'
```

The request body accepts:
- `additionalInputs` -- map of input name to JSON value for missing inputs
- `resolvedNodes` -- map of node name to JSON value for directly resolving nodes

Returns the same response format as `/run` and `/execute`. The pipeline may complete or suspend again if further inputs are still missing.

### Delete a Suspended Execution

```bash
curl -X DELETE http://localhost:8080/executions/{id}
```

---

## Pipeline Management

### List Stored Pipelines

```bash
curl http://localhost:8080/pipelines
```

Returns pipeline summaries with `structuralHash`, `syntacticHash`, `aliases`, `compiledAt`, `moduleCount`, and `declaredOutputs`.

### Get Pipeline Detail

```bash
# By alias name
curl http://localhost:8080/pipelines/my-pipeline

# By structural hash
curl http://localhost:8080/pipelines/sha256abcdef0123456789...
```

Pipeline references are resolved as follows: if the ref is exactly 64 hex characters, it's treated as a structural hash; otherwise it's treated as an alias name.

### Delete a Pipeline

```bash
curl -X DELETE http://localhost:8080/pipelines/my-pipeline
```

Returns `409 Conflict` if other aliases still point to this pipeline.

### Repoint an Alias

```bash
curl -X PUT http://localhost:8080/pipelines/my-pipeline/alias \
  -H "Content-Type: application/json" \
  -d '{ "structuralHash": "abc123..." }'
```

Points the alias `my-pipeline` to a different structural hash.

---

## Pipeline Versioning

Versioning must be enabled on the server. These endpoints manage the version history of named pipelines.

### Hot-Reload a Pipeline

```bash
curl -X POST http://localhost:8080/pipelines/my-pipeline/reload \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in text: String\nresult = Uppercase(text)\nout result"
  }'
```

Compiles the new source and atomically replaces the pipeline. The response includes `previousHash`, `newHash`, `changed` (whether the hash actually changed), and `version` (the new version number).

Optionally start a canary deployment on reload:

```bash
curl -X POST http://localhost:8080/pipelines/my-pipeline/reload \
  -H "Content-Type: application/json" \
  -d '{
    "source": "...",
    "canary": {
      "initialWeight": 0.05,
      "promotionSteps": [0.10, 0.25, 0.50, 1.0],
      "observationWindow": "5m",
      "errorThreshold": 0.05,
      "autoPromote": true
    }
  }'
```

### List Version History

```bash
curl http://localhost:8080/pipelines/my-pipeline/versions
```

Returns the pipeline name, list of versions (each with `version`, `structuralHash`, `createdAt`, `active`), and `activeVersion`.

### Rollback to Previous Version

```bash
curl -X POST http://localhost:8080/pipelines/my-pipeline/rollback
```

Returns `previousVersion`, `activeVersion`, and the `structuralHash` of the now-active version.

### Rollback to Specific Version

```bash
curl -X POST http://localhost:8080/pipelines/my-pipeline/rollback/2
```

---

## Canary Releases

Canary deployments split traffic between an old and new pipeline version, gradually promoting the new version if metrics are healthy.

### Get Canary Status

```bash
curl http://localhost:8080/pipelines/my-pipeline/canary
```

Returns the canary state including `currentWeight` (traffic fraction to new version), `currentStep`, `status` (`observing`, `promoting`, `rolled_back`, `complete`), `startedAt`, and per-version metrics (request counts, error rates, latencies).

### Promote Canary

```bash
curl -X POST http://localhost:8080/pipelines/my-pipeline/canary/promote
```

Manually advances to the next promotion step.

### Rollback Canary

```bash
curl -X POST http://localhost:8080/pipelines/my-pipeline/canary/rollback
```

Routes all traffic back to the old version.

### Abort Canary

```bash
curl -X DELETE http://localhost:8080/pipelines/my-pipeline/canary
```

Aborts the canary deployment and routes all traffic to the old version.

---

## Authentication

Authentication is opt-in. When enabled, pass the API key in the `Authorization` header:

```bash
curl -H "Authorization: Bearer your-api-key" http://localhost:8080/modules
```

Roles control access levels:

| Role | Permissions |
|------|------------|
| `Admin` | All HTTP methods |
| `Execute` | GET + POST only |
| `ReadOnly` | GET only |

Public paths exempt from auth: `/health`, `/health/live`, `/health/ready`, `/metrics`.

## Server Configuration

Configure via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_PORT` | `8080` | Server port |
| `CONSTELLATION_API_KEYS` | (none) | Comma-separated `key:Role` pairs |
| `CONSTELLATION_CORS_ORIGINS` | (none) | Allowed CORS origins |
| `CONSTELLATION_RATE_LIMIT_RPM` | `100` | Requests per minute per client IP |
| `CONSTELLATION_RATE_LIMIT_BURST` | `20` | Burst size for rate limiter |

## Programmatic Server Setup

```scala
import io.constellation.http.ConstellationServer

ConstellationServer.builder(constellation, compiler)
  .withPort(8080)
  .withAuth(AuthConfig(apiKeys = Map("key1" -> ApiRole.Admin)))
  .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  .withRateLimit(RateLimitConfig(requestsPerMinute = 100, burst = 20))
  .withHealthChecks(HealthCheckConfig(enableDetailEndpoint = true))
  .run
```

See the [Programmatic API](./programmatic-api) guide for full details on setting up the runtime and server.

## WebSocket (LSP)

The `/lsp` endpoint provides Language Server Protocol support for IDEs. See the [LSP WebSocket](./lsp-websocket) documentation for the protocol details.
