# HTTP API Ethos

> Normative constraints for the HTTP API layer.

---

## Identity

- **IS:** HTTP interface layer exposing REST endpoints, WebSocket LSP bridge, and web dashboard
- **IS NOT:** An execution engine, compiler, type system, or business logic implementation

---

## Semantic Mapping

| Scala Artifact | Domain Meaning |
|----------------|----------------|
| `ConstellationServer` | Entry point for building and running the HTTP server with configurable middleware |
| `ServerBuilder` | Fluent API for assembling server configuration with auth, CORS, rate limiting, health checks |
| `ConstellationRoutes` | Core REST routes: compile, execute, run, modules, namespaces, metrics |
| `DashboardRoutes` | Dashboard UI serving and file browser API for `.cst` files |
| `HealthCheckRoutes` | Kubernetes-style probes: `/health/live`, `/health/ready`, `/health/detail` |
| `LspWebSocketHandler` | WebSocket-to-LSP bridge for IDE integration via JSON-RPC |
| `AuthMiddleware` | API key authentication with role-based access control (Admin, Execute, ReadOnly) |
| `CorsMiddleware` | Cross-origin resource sharing policy enforcement |
| `RateLimitMiddleware` | Token bucket rate limiting per client IP with exempt paths |
| `ExecutionHelper` | JSON-to-CValue conversion using DAG input schemas |
| `ExecutionStorage` | In-memory execution history with configurable retention |
| `PipelineLoader` | Startup loader for `.cst` files from configured directory |
| `PipelineVersionStore` | Version history tracking for pipeline hot-reload |
| `FileSystemPipelineStore` | Persistent pipeline storage surviving server restarts |
| `CanaryRouter` | Traffic splitting for canary releases between pipeline versions |
| `ApiModels` | Request/response types: CompileRequest, ExecuteRequest, RunRequest, etc. |
| `HashedApiKey` | SHA-256 hashed API key with constant-time verification |
| `ApiRole` | Authorization level: Admin (all), Execute (GET+POST), ReadOnly (GET) |
| `ReadinessCheck` | Custom readiness probe for `/health/ready` endpoint |
| `PrometheusFormatter` | Metrics serialization to Prometheus exposition format |

For complete type signatures, see:
- [io.constellation.http](/docs/generated/io.constellation.http.md)

---

## Invariants

### 1. Public paths bypass authentication and rate limiting

Health checks (`/health`, `/health/live`, `/health/ready`) and metrics (`/metrics`) are always accessible regardless of authentication or rate limit state. This ensures orchestration platforms can probe liveness without credentials.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/http-api/src/main/scala/io/constellation/http/AuthMiddleware.scala#publicPaths` |
| Test | `modules/http-api/src/test/scala/io/constellation/http/AuthMiddlewareTest.scala#allow access to public paths without auth` |

### 2. API keys are stored as SHA-256 hashes with constant-time comparison

Plaintext API keys are never stored; only their SHA-256 hashes. Verification uses constant-time comparison to prevent timing attacks.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/http-api/src/main/scala/io/constellation/http/AuthConfig.scala#HashedApiKey` |
| Test | `modules/http-api/src/test/scala/io/constellation/http/AuthMiddlewareTest.scala#use constant-time comparison` |

### 3. Role-based authorization enforces method restrictions

`ApiRole.Admin` permits all HTTP methods, `Execute` permits GET and POST only, `ReadOnly` permits GET only. Violations return 403 Forbidden.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/http-api/src/main/scala/io/constellation/http/AuthMiddleware.scala#permits` |
| Test | `modules/http-api/src/test/scala/io/constellation/http/AuthMiddlewareTest.scala#return 403 for DELETE with Execute key` |

### 4. Rate limiting uses token bucket per client IP

Each client IP has an independent token bucket refilling at `requestsPerMinute / 60` tokens per second with configurable burst. Exceeded limits return 429 with `Retry-After` header.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/http-api/src/main/scala/io/constellation/http/RateLimitMiddleware.scala#tokenBuckets` |
| Test | `modules/http-api/src/test/scala/io/constellation/http/RateLimitMiddlewareTest.scala#return 429 when rate limit exceeded` |

### 5. CORS middleware is disabled when no origins configured

Empty `allowedOrigins` results in zero middleware overhead. Wildcard (`*`) with credentials is rejected at config validation time.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/http-api/src/main/scala/io/constellation/http/CorsMiddleware.scala#apply` |
| Test | `modules/http-api/src/test/scala/io/constellation/http/CorsMiddlewareTest.scala#pass through without CORS headers when disabled` |

### 6. Liveness probe always returns 200

`/health/live` returns `{"status":"alive"}` with HTTP 200 unconditionally. It indicates process is running, not application readiness.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/http-api/src/main/scala/io/constellation/http/HealthCheckRoutes.scala#liveness` |
| Test | `modules/http-api/src/test/scala/io/constellation/http/HealthCheckRoutesTest.scala#always return 200 with alive status` |

### 7. Readiness probe aggregates custom checks

`/health/ready` returns 200 only when lifecycle is Running and all custom `ReadinessCheck` instances pass. Any failure returns 503 Service Unavailable.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/http-api/src/main/scala/io/constellation/http/HealthCheckRoutes.scala#readiness` |
| Test | `modules/http-api/src/test/scala/io/constellation/http/HealthCheckRoutesTest.scala#return 503 when a custom check fails` |

### 8. Middleware applies inner to outer

Middleware composition order is: `Request -> CORS -> RateLimit -> Auth -> Routes -> Response`. Auth is innermost (checked last), CORS is outermost (applied first).

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/http-api/src/main/scala/io/constellation/http/ConstellationServer.scala#buildRoutes` |
| Test | `modules/http-api/src/test/scala/io/constellation/http/ServerBuilderIntegrationTest.scala#AuthMiddleware + CorsMiddleware composition` |

### 9. Missing inputs suspend execution rather than fail

When `/execute` or `/run` receives incomplete inputs, the response is 200 OK with `status: "suspended"`, `missingInputs` map, and `executionId` for resumption.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/http-api/src/main/scala/io/constellation/http/ConstellationRoutes.scala#handleSuspension` |
| Test | `modules/http-api/src/test/scala/io/constellation/http/ConstellationRoutesTest.scala#return 200 with suspended status for missing input` |

### 10. LSP WebSocket uses JSON-RPC 2.0 protocol

WebSocket messages at `/lsp` follow JSON-RPC 2.0 with `jsonrpc`, `method`, `params`, `id` (for requests), `result`/`error` (for responses). Standard error codes (ParseError, MethodNotFound, etc.) apply.

| Aspect | Reference |
|--------|-----------|
| Implementation | `modules/http-api/src/main/scala/io/constellation/http/LspWebSocketHandler.scala` |
| Test | `modules/http-api/src/test/scala/io/constellation/http/LspWebSocketHandlerTest.scala#serialize correctly` |

---

## Principles (Prioritized)

1. **Secure by default, opt-in hardening** — No authentication required out of the box, but all security features are available via builder methods
2. **Fail-safe for infrastructure** — Health probes never block on application logic; liveness always succeeds
3. **Composable middleware** — Each concern (auth, CORS, rate limit) is independent and can be enabled/disabled individually
4. **Consistent error responses** — All errors return structured JSON with `error` and `message` fields

---

## Decision Heuristics

- When adding a new endpoint, use `ConstellationRoutes` for core API functionality, `DashboardRoutes` for UI-related features
- When implementing authentication features, store only hashes and use constant-time comparison
- When adding middleware, maintain the inner-to-outer composition order and preserve public path exemptions
- When uncertain about HTTP status codes, prefer 200 with structured error body over 4xx/5xx for client-recoverable conditions
- When adding health checks, make custom checks implement `ReadinessCheck` rather than modifying core health routes

---

## Out of Scope

- DAG execution logic (see [runtime/](../runtime/))
- Compilation and type checking (see [compiler/](../compiler/))
- Type system definitions (see [core/](../core/))
- LSP protocol implementation details (see [lsp/](../lsp/))
- Standard library functions (see [stdlib/](../stdlib/))
