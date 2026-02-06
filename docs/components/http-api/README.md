# HTTP API Component

> **Path**: `docs/components/http-api/`
> **Parent**: [components/](../README.md)
> **Module**: `modules/http-api/`

REST endpoints, WebSocket LSP, and web dashboard serving.

## Key Files

| File | Purpose |
|------|---------|
| `ConstellationServer.scala` | Server builder and configuration |
| `ConstellationRoutes.scala` | Core API routes |
| `DashboardRoutes.scala` | Dashboard and file browser routes |
| `HealthCheckRoutes.scala` | Health, liveness, readiness endpoints |
| `LspWebSocketHandler.scala` | WebSocket LSP bridge |
| `ExecutionHelper.scala` | Pipeline execution utilities |
| `ExecutionStorage.scala` | In-memory execution history |
| **Middleware** | |
| `AuthMiddleware.scala` | API key authentication |
| `CorsMiddleware.scala` | Cross-origin resource sharing |
| `RateLimitMiddleware.scala` | Token bucket rate limiting |
| **Pipeline Management** | |
| `PipelineLoader.scala` | Startup directory loader |
| `PipelineVersionStore.scala` | Version tracking for reload |
| `FileSystemPipelineStore.scala` | Persistent pipeline storage |
| `CanaryRouter.scala` | Traffic splitting for canary releases |
| **Models** | |
| `ApiModels.scala` | Request/response types |
| `DashboardModels.scala` | Dashboard-specific types |
| `AuthConfig.scala`, `CorsConfig.scala`, etc. | Configuration types |

## Role in the System

The HTTP API is the primary interface for external clients:

```
                    ┌─────────────┐
                    │    core     │
                    └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  │
   [runtime]         [lang-compiler]          │
        │                  │                  │
        │      ┌───────────┼───────────┐      │
        │      │           │           │      │
        │      ▼           ▼           ▼      │
        │ [lang-stdlib] [lang-lsp]     │      │
        │      │           │           │      │
        └──────┼───────────┼───────────┼──────┘
               │           │           │
               ▼           ▼           ▼
             [http-api] ◄──────────────┘
                  │
                  ▼
            [example-app]
```

The HTTP API:
1. Exposes REST endpoints for compilation, execution, pipeline management
2. Bridges WebSocket to LSP for IDE integration
3. Serves the web dashboard for visual pipeline editing/execution
4. Provides health checks for orchestration platforms

## Server Builder

### Basic Usage

```scala
ConstellationServer.builder(constellation, compiler)
  .withPort(8080)
  .withDashboard
  .run
```

### Full Configuration

```scala
ConstellationServer.builder(constellation, compiler)
  .withHost("0.0.0.0")
  .withPort(8080)
  .withFunctionRegistry(registry)
  .withDashboard(DashboardConfig(...))
  .withAuth(AuthConfig(apiKeys = Map("key1" -> ApiRole.Admin)))
  .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  .withRateLimit(RateLimitConfig(requestsPerMinute = 100, burst = 20))
  .withHealthChecks(HealthCheckConfig(enableDetailEndpoint = true))
  .withPipelineLoader(PipelineLoaderConfig(directory = Paths.get("pipelines")))
  .withPersistentPipelineStore(Paths.get(".constellation-store"))
  .run
```

## Endpoint Categories

### Execution Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST /run` | Hot execution | Compile + execute in one call |
| `POST /compile` | Compile only | Returns pipeline reference |
| `POST /execute` | Cold execution | Execute pre-compiled pipeline |

### Pipeline Management

| Method | Path | Description |
|--------|------|-------------|
| `GET /pipelines/{ref}` | Get pipeline | Returns DAG structure |
| `DELETE /pipelines/{ref}` | Delete pipeline | Remove from store |
| `PUT /alias` | Create alias | Map name to hash |
| `POST /reload` | Reload pipeline | Re-compile from source file |
| `GET /versions/{alias}` | List versions | Version history |
| `POST /canary` | Set canary | Configure traffic split |

### Discovery

| Method | Path | Description |
|--------|------|-------------|
| `GET /modules` | List modules | Registered module specs |
| `GET /namespaces` | List namespaces | Available function namespaces |

### Health & Metrics

| Method | Path | Description |
|--------|------|-------------|
| `GET /health` | Health check | Basic status |
| `GET /health/live` | Liveness probe | Is process alive |
| `GET /health/ready` | Readiness probe | Is ready for traffic |
| `GET /health/detail` | Detailed health | Auth-gated diagnostics |
| `GET /metrics` | Metrics | Prometheus format stats |

### Suspension (Async Execution)

| Method | Path | Description |
|--------|------|-------------|
| `GET /executions` | List executions | Suspended executions |
| `GET /executions/{id}` | Get execution | Execution state |
| `POST /executions/{id}/resume` | Resume execution | Provide pending inputs |

### Dashboard

| Method | Path | Description |
|--------|------|-------------|
| `GET /dashboard` | Dashboard UI | Serves index.html |
| `GET /dashboard/*` | Static assets | JS, CSS, etc. |
| `GET /dashboard/api/files` | File browser | List .cst files |
| `GET /dashboard/api/file` | Read file | Get file contents |
| `POST /dashboard/api/file` | Save file | Write file contents |
| `GET /dashboard/api/history` | Execution history | Recent executions |
| `GET /dashboard/api/history/{id}` | Execution detail | Single execution |

### WebSocket

| Path | Description |
|------|-------------|
| `WS /lsp` | Language Server Protocol | JSON-RPC over WebSocket |

## Middleware Stack

Middleware is applied inner to outer:

```
Request → CORS → RateLimit → Auth → Routes → Response
```

### Authentication

```scala
AuthConfig(
  apiKeys = Map(
    "key1" -> ApiRole.Admin,    // All methods
    "key2" -> ApiRole.Execute,  // GET + POST
    "key3" -> ApiRole.ReadOnly  // GET only
  )
)
```

API key is passed via `X-API-Key` header or `Authorization: Bearer <key>`.

Public paths exempt from auth: `/health`, `/health/live`, `/health/ready`, `/metrics`

### CORS

```scala
CorsConfig(
  allowedOrigins = Set("https://app.example.com"),
  allowedMethods = Set("GET", "POST", "PUT", "DELETE"),
  allowedHeaders = Set("Content-Type", "Authorization", "X-API-Key"),
  maxAge = 86400.seconds
)
```

Use `allowedOrigins = Set("*")` for wildcard (development only).

### Rate Limiting

```scala
RateLimitConfig(
  requestsPerMinute = 100,
  burst = 20
)
```

Uses token bucket algorithm per client IP. Public paths exempt.

## Pipeline Lifecycle

### Hot Execution (`POST /run`)

```
Request { source, inputs }
          │
          ▼
    ┌─────────────┐
    │  Compile    │
    └──────┬──────┘
           │
    ┌──────▼──────┐
    │  Execute    │
    └──────┬──────┘
           │
           ▼
Response { outputs }
```

### Cold Execution (`POST /execute`)

```
POST /compile { source }
          │
          ▼
Response { ref: "sha256:..." }

POST /execute { ref, inputs }
          │
          ▼
    ┌─────────────────┐
    │  Lookup by ref  │
    └────────┬────────┘
             │
    ┌────────▼────────┐
    │    Execute      │
    └────────┬────────┘
             │
             ▼
Response { outputs }
```

### Pipeline Versioning

```
POST /reload { alias: "my-pipeline" }
          │
          ▼
    ┌─────────────────────┐
    │ Re-compile from file │
    └──────────┬──────────┘
               │
    ┌──────────▼──────────┐
    │ Store new version    │
    │ v2 → new hash        │
    │ v1 → old hash        │
    └──────────┬──────────┘
               │
               ▼
Response { version: 2, hash: "sha256:..." }
```

### Canary Releases

```scala
POST /canary {
  alias: "my-pipeline",
  canaryVersion: 2,
  canaryWeight: 0.1  // 10% traffic to v2
}
```

Traffic routing:
- 10% requests → version 2 (canary)
- 90% requests → version 1 (stable)

## Dashboard

### File Browser

The dashboard can browse and edit `.cst` files:

```scala
DashboardConfig(
  enableDashboard = true,
  cstDirectory = Some(Paths.get("pipelines"))
)
```

API:
- `GET /dashboard/api/files` - List `.cst` files in directory
- `GET /dashboard/api/file?path=...` - Read file contents
- `POST /dashboard/api/file` - Save file contents

### Execution History

In-memory execution history (configurable limit):

```scala
ExecutionStorage(maxEntries = 100)
```

Tracks:
- Execution ID, pipeline name, status
- Inputs, outputs, execution time
- Error messages (if failed)

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_PORT` | 8080 | Server port |
| `CONSTELLATION_API_KEYS` | (none) | `key:Role` pairs |
| `CONSTELLATION_CORS_ORIGINS` | (none) | Allowed origins |
| `CONSTELLATION_RATE_LIMIT_RPM` | 100 | Requests per minute |
| `CONSTELLATION_RATE_LIMIT_BURST` | 20 | Burst size |
| `CONSTELLATION_SCHEDULER_ENABLED` | false | Enable bounded scheduler |
| `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY` | 16 | Max concurrent tasks |
| `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` | 30s | Priority boost timeout |

## Persistent Pipeline Store

File-based pipeline storage that survives restarts:

```scala
.withPersistentPipelineStore(Paths.get(".constellation-store"))
```

Directory structure:
```
.constellation-store/
├── pipelines/           # PipelineImage by hash
│   └── sha256_xxx.json
├── aliases.json         # alias → hash mapping
└── syntactic-index.json # source hash → structural hash
```

## Dependencies

- **Depends on:** `core`, `runtime`, `lang-compiler`, `lang-lsp`, `lang-stdlib`
- **Depended on by:** `example-app`

## Features Using This Component

| Feature | HTTP API Role |
|---------|---------------|
| [REST API](../../http-api/) | All endpoints |
| [Dashboard](../../features/dashboard/) | Dashboard routes |
| [Security](../../http-api/security.md) | Auth, CORS, rate limiting |
| [Pipeline versioning](../../http-api/pipelines.md) | Version store, reload |
| [Canary releases](../../http-api/pipelines.md) | Canary router |
| [Suspension](../../http-api/suspension.md) | Execution storage |
