# HTTP API

> **Path**: `docs/http-api/`
> **Parent**: [docs/](../README.md)

REST endpoints, security, and WebSocket LSP.

## Contents

| File | Description |
|------|-------------|
| [execution.md](./execution.md) | /run, /compile, /execute endpoints |
| [pipelines.md](./pipelines.md) | Pipeline CRUD, versioning, canary |
| [suspension.md](./suspension.md) | /executions, resume, list |
| [discovery.md](./discovery.md) | /modules, /namespaces |
| [security.md](./security.md) | Auth, CORS, rate limiting |

## Endpoint Overview

| Category | Endpoints |
|----------|-----------|
| Execution | `POST /run`, `POST /compile`, `POST /execute` |
| Pipelines | `GET/DELETE /pipelines/{ref}`, `PUT /alias`, `POST /reload` |
| Suspension | `GET /executions`, `POST /executions/{id}/resume` |
| Discovery | `GET /modules`, `GET /namespaces` |
| Health | `GET /health`, `/health/live`, `/health/ready` |
| Metrics | `GET /metrics` |
| LSP | `WS /lsp` |
| Dashboard | `GET /dashboard/*` |

## Quick Reference

### Run Pipeline (Hot)
```http
POST /run
Content-Type: application/json

{
  "source": "in x: String\nout x",
  "inputs": { "x": "hello" }
}
```

### Execute Pipeline (Cold)
```http
POST /execute
Content-Type: application/json

{
  "ref": "my-pipeline",
  "inputs": { "x": "hello" }
}
```

### List Modules
```http
GET /modules
```

### Health Check
```http
GET /health
```

## Server Configuration

| Variable | Default | Purpose |
|----------|---------|---------|
| `CONSTELLATION_PORT` | 8080 | Server port |
| `CONSTELLATION_API_KEYS` | (none) | Auth keys |
| `CONSTELLATION_CORS_ORIGINS` | (none) | CORS origins |
| `CONSTELLATION_RATE_LIMIT_RPM` | 100 | Rate limit |

## Server Builder

```scala
ConstellationServer.builder(constellation, compiler)
  .withAuth(AuthConfig(...))
  .withCors(CorsConfig(...))
  .withRateLimit(RateLimitConfig(...))
  .withHealthChecks(HealthCheckConfig(...))
  .run
```

See [security.md](./security.md) for details.
