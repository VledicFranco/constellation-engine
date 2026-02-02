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

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check (liveness) |
| `/health/live` | GET | Liveness probe |
| `/health/ready` | GET | Readiness probe |
| `/health/detail` | GET | Detailed diagnostics (auth-gated, opt-in) |
| `/modules` | GET | List registered modules with type signatures |
| `/compile` | POST | Compile constellation-lang source, return DAG |
| `/run` | POST | Compile and execute in one call |
| `/programs` | GET | List stored programs |
| `/programs/{ref}` | GET | Get program metadata |
| `/execute` | POST | Execute a previously stored program |
| `/metrics` | GET | Runtime metrics (cache stats, execution counts) |
| `/lsp` | WebSocket | Language Server Protocol for IDE support |

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
