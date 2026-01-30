# Constellation HTTP API

HTTP server module for the Constellation Engine, providing a REST API for compiling constellation-lang programs, managing DAGs and modules, and executing computational pipelines.

## Features

- **Compilation API**: Compile constellation-lang source code to executable DAGs
- **Execution API**: Execute compiled DAGs with JSON inputs
- **DAG Management**: List, retrieve, and manage computational DAGs
- **Module Management**: List available modules and their specifications
- **Health Monitoring**: Liveness, readiness, and detail health check endpoints
- **API Authentication**: Static API key auth with role-based access control (opt-in)
- **CORS Support**: Configurable cross-origin request handling (opt-in)
- **Rate Limiting**: Per-IP token bucket rate limiting (opt-in)

## Getting Started

### Running the Demo Server

The module includes a demo server with the standard library pre-loaded:

```scala
import io.constellation.http.examples.DemoServer

// Run with sbt
sbt "httpApi/runMain io.constellation.http.examples.DemoServer"
```

The server will start on `http://localhost:8080`

### Creating a Custom Server

```scala
import cats.effect.{IO, IOApp}
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.http.ConstellationServer
import io.constellation.stdlib.StdLib

object MyServer extends IOApp.Simple {
  def run: IO[Unit] = {
    for {
      // Initialize the constellation engine
      constellation <- ConstellationImpl.init

      // Create a compiler (with or without stdlib)
      compiler = StdLib.compiler

      // Start the HTTP server
      _ <- ConstellationServer
        .builder(constellation, compiler)
        .withHost("0.0.0.0")
        .withPort(8080)
        .run
    } yield ()
  }
}
```

### Creating a Hardened Server

All hardening features are opt-in and disabled by default. When not configured, the server behaves identically to before with zero overhead.

```scala
import io.constellation.http._

ConstellationServer
  .builder(constellation, compiler)
  .withAuth(AuthConfig(apiKeys = Map(
    "admin-key" -> ApiRole.Admin,
    "exec-key"  -> ApiRole.Execute,
    "read-key"  -> ApiRole.ReadOnly
  )))
  .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  .withRateLimit(RateLimitConfig(requestsPerMinute = 100, burst = 20))
  .withHealthChecks(HealthCheckConfig(enableDetailEndpoint = true))
  .run
```

## API Endpoints

### Health Checks

```bash
# Liveness — always returns 200 if process is alive
GET /health/live
# Response: {"status": "alive"}

# Readiness — 200 if lifecycle Running + custom checks pass, 503 otherwise
GET /health/ready
# Response: {"status": "ready"} or {"status": "not_ready"}

# Detail diagnostics — opt-in, shows cache/scheduler/lifecycle details
GET /health/detail
# Response: {"timestamp": "...", "lifecycle": {...}, "cache": {...}, "scheduler": {...}}

# Legacy health check — unchanged from previous versions
GET /health
# Response: {"status": "ok"}
```

### Compile Program
```bash
POST /compile
Content-Type: application/json
```

Compile constellation-lang source code into a DAG.

**Request:**
```json
{
  "source": "in a: Int\nin b: Int\nresult = add(a, b)\nout result",
  "dagName": "addition-dag"
}
```

**Success Response (200):**
```json
{
  "success": true,
  "dagName": "addition-dag",
  "errors": []
}
```

**Error Response (400):**
```json
{
  "success": false,
  "dagName": null,
  "errors": ["Undefined variable: x"]
}
```

### Execute DAG
```bash
POST /execute
Content-Type: application/json
```

Execute a compiled DAG with inputs.

**Request:**
```json
{
  "dagName": "addition-dag",
  "inputs": {
    "a": 10,
    "b": 20
  }
}
```

**Response:**
```json
{
  "success": true,
  "outputs": {},
  "error": null
}
```

### List DAGs
```bash
GET /dags
```

List all available DAGs.

**Response:**
```json
{
  "dags": {
    "addition-dag": {
      "name": "addition-dag",
      "description": "",
      "tags": [],
      "majorVersion": 0,
      "minorVersion": 1
    }
  }
}
```

### Get DAG
```bash
GET /dags/:dagName
```

Get a specific DAG by name.

**Response (200):**
```json
{
  "name": "addition-dag",
  "metadata": {
    "name": "addition-dag",
    "description": "",
    "tags": [],
    "majorVersion": 0,
    "minorVersion": 1
  }
}
```

**Response (404):**
```json
{
  "error": "DagNotFound",
  "message": "DAG 'non-existent' not found"
}
```

### List Modules
```bash
GET /modules
```

List all available modules.

**Response:**
```json
{
  "modules": [
    {
      "name": "PlusOne",
      "description": "Adds one to the input",
      "version": "0.1",
      "inputs": {
        "n": "CInt"
      },
      "outputs": {
        "nPlusOne": "CInt"
      }
    }
  ]
}
```

## Security Configuration

All security features are **opt-in** and **disabled by default**. When not configured, the route stack is identical to an unconfigured server.

### API Authentication

Static API key authentication with role-based access control.

```scala
.withAuth(AuthConfig(apiKeys = Map(
  "key1" -> ApiRole.Admin,
  "key2" -> ApiRole.Execute,
  "key3" -> ApiRole.ReadOnly
)))
```

| Role | Allowed Methods |
|------|----------------|
| `Admin` | All (GET, POST, PUT, DELETE, etc.) |
| `Execute` | GET, POST |
| `ReadOnly` | GET |

Clients send the key via `Authorization: Bearer <api-key>` header.

**Error responses:**

| Status | When |
|--------|------|
| `401 Unauthorized` | Missing header, invalid token format, or unknown key |
| `403 Forbidden` | Valid key but insufficient role for the HTTP method |

**Public paths** (exempt from auth): `/health`, `/health/live`, `/health/ready`, `/metrics`.

**Environment variable:** `CONSTELLATION_API_KEYS=key1:Admin,key2:Execute`

### CORS

Cross-origin request configuration using the http4s built-in CORS middleware.

```scala
.withCors(CorsConfig(
  allowedOrigins = Set("https://app.example.com", "https://admin.example.com"),
  allowedMethods = Set("GET", "POST", "PUT", "DELETE", "OPTIONS"),
  allowedHeaders = Set("Content-Type", "Authorization"),
  allowCredentials = false,
  maxAge = 3600L
))
```

Use `Set("*")` for wildcard origins (development only). Cannot combine wildcard with `allowCredentials = true`.

**Environment variable:** `CONSTELLATION_CORS_ORIGINS=https://app.example.com,https://admin.example.com`

### Rate Limiting

Per-client-IP token bucket rate limiting. Returns `429 Too Many Requests` with `Retry-After` header.

```scala
.withRateLimit(RateLimitConfig(
  requestsPerMinute = 100,
  burst = 20,
  exemptPaths = Set("/health", "/health/live", "/health/ready", "/metrics")
))
```

**Environment variables:** `CONSTELLATION_RATE_LIMIT_RPM=100`, `CONSTELLATION_RATE_LIMIT_BURST=20`

IP extraction uses `X-Forwarded-For` header first, then falls back to remote address.

### Health Checks

```scala
.withHealthChecks(HealthCheckConfig(
  enableDetailEndpoint = true,
  detailRequiresAuth = true,
  customReadinessChecks = List(
    ReadinessCheck("database", dbCheckIO),
    ReadinessCheck("cache", cacheCheckIO)
  )
))
```

| Endpoint | Purpose | Auth required |
|----------|---------|--------------|
| `/health/live` | Kubernetes liveness probe | No |
| `/health/ready` | Kubernetes readiness probe | No |
| `/health/detail` | Full diagnostics | Yes (when auth enabled) |

### Middleware Layering

Middleware is applied in this order (inner to outer):

1. **Auth** (innermost) — checks credentials closest to route handlers
2. **Rate Limit** — limits after CORS preflight is handled
3. **CORS** (outermost) — handles preflight OPTIONS before auth/rate-limit

## Example Usage

### Using curl

```bash
# Health check
curl http://localhost:8080/health

# Liveness probe
curl http://localhost:8080/health/live

# Readiness probe
curl http://localhost:8080/health/ready

# Compile a program
curl -X POST http://localhost:8080/compile \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in a: Int\nin b: Int\nresult = add(a, b)\nout result",
    "dagName": "addition-dag"
  }'

# List DAGs
curl http://localhost:8080/dags

# Get a specific DAG
curl http://localhost:8080/dags/addition-dag

# List modules
curl http://localhost:8080/modules

# With authentication (when enabled):
curl http://localhost:8080/modules \
  -H "Authorization: Bearer admin-key"

# Execute (with auth):
curl -X POST http://localhost:8080/execute \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer exec-key" \
  -d '{
    "dagName": "addition-dag",
    "inputs": {"a": 10, "b": 20}
  }'
```

### Using httpie

```bash
# Compile a program
http POST localhost:8080/compile \
  source="in x: Int\nout x" \
  dagName="simple-dag"

# List DAGs
http GET localhost:8080/dags

# With auth:
http GET localhost:8080/modules "Authorization: Bearer admin-key"
```

## Configuration

The server can be configured using the builder pattern:

```scala
ConstellationServer
  .builder(constellation, compiler)
  .withHost("127.0.0.1")    // Default: "0.0.0.0"
  .withPort(9000)            // Default: 8080
  .withAuth(authConfig)      // Optional: API key authentication
  .withCors(corsConfig)      // Optional: CORS headers
  .withRateLimit(rlConfig)   // Optional: per-IP rate limiting
  .withHealthChecks(hcConfig) // Optional: health check tuning
  .build
```

## Dependencies

- **http4s-ember-server**: HTTP server
- **http4s-dsl**: DSL for defining routes
- **http4s-circe**: JSON encoding/decoding
- **circe**: JSON library
- **logback**: Logging

## Testing

Run the test suite:

```bash
sbt httpApi/test
```

The tests cover:
- Health check endpoints (liveness, readiness, detail)
- Program compilation (success and error cases)
- DAG listing and retrieval
- Module listing
- Authentication middleware (valid/invalid keys, roles, public paths)
- CORS middleware (allowed/disallowed origins, preflight, wildcard)
- Rate limiting (under/over limit, exempt paths, Retry-After header)
- Integration (middleware composition, backward compatibility)
- Error handling

## Future Enhancements

- JWT/OAuth authentication (swap without changing middleware interface)
- Streaming execution results
- Per-API-key rate limiting (in addition to per-IP)
- OpenAPI/Swagger documentation generation
