# Security

> **Path**: `docs/http-api/security.md`
> **Parent**: [http-api/](./README.md)

Authentication, CORS, and rate limiting for the HTTP API.

## Overview

All security features are **opt-in and disabled by default**. When not configured, they add zero overhead.

## Authentication

API key authentication with role-based access control.

### Configuration

**Environment variable:**

```bash
CONSTELLATION_API_KEYS=admin-key-abc123:Admin,app-key-def456:Execute,read-key-ghi789:ReadOnly
```

**Programmatic:**

```scala
import io.constellation.http._

ConstellationServer.builder(constellation, compiler)
  .withAuth(AuthConfig(hashedKeys = List(
    HashedApiKey("admin-key-abc123", ApiRole.Admin),
    HashedApiKey("app-key-def456", ApiRole.Execute),
    HashedApiKey("read-key-ghi789", ApiRole.ReadOnly)
  )))
```

### Roles and Permissions

| Role | GET | POST | PUT | DELETE |
|------|-----|------|-----|--------|
| `Admin` | Yes | Yes | Yes | Yes |
| `Execute` | Yes | Yes | No | No |
| `ReadOnly` | Yes | No | No | No |

### Making Authenticated Requests

```bash
curl -H "Authorization: Bearer admin-key-abc123" http://localhost:8080/pipelines
```

### Public Paths

These paths bypass authentication entirely:

- `/health`
- `/health/live`
- `/health/ready`
- `/metrics`

### Error Responses

**Missing or invalid header (401):**

```json
{
  "error": "Unauthorized",
  "message": "Missing or invalid Authorization header. Expected: Bearer <api-key>"
}
```

**Invalid API key (401):**

```json
{
  "error": "Unauthorized",
  "message": "Invalid API key"
}
```

**Insufficient permissions (403):**

```json
{
  "error": "Forbidden",
  "message": "Role 'ReadOnly' does not permit POST requests"
}
```

### Key Requirements

- Minimum 24 characters
- No control characters
- Keys are hashed with SHA-256 at startup (never stored in plaintext)
- Constant-time comparison prevents timing attacks

## CORS

Cross-Origin Resource Sharing controls which browser origins can call the API.

### Configuration

**Environment variable:**

```bash
CONSTELLATION_CORS_ORIGINS=https://app.example.com,https://admin.example.com
```

Use `*` for wildcard (development only):

```bash
CONSTELLATION_CORS_ORIGINS=*
```

**Programmatic:**

```scala
.withCors(CorsConfig(
  allowedOrigins = Set("https://app.example.com", "https://admin.example.com"),
  allowedMethods = Set("GET", "POST", "PUT", "DELETE", "OPTIONS"),
  allowedHeaders = Set("Content-Type", "Authorization"),
  allowCredentials = false,
  maxAge = 3600L
))
```

### Configuration Options

| Field | Default | Description |
|-------|---------|-------------|
| `allowedOrigins` | (empty) | Origin URLs allowed to call API |
| `allowedMethods` | GET, POST, PUT, DELETE, OPTIONS | Allowed HTTP methods |
| `allowedHeaders` | Content-Type, Authorization | Headers client may send |
| `allowCredentials` | false | Allow cookies/auth headers |
| `maxAge` | 3600 | Preflight cache duration (seconds) |

### Origin Validation

- HTTPS required for non-localhost origins
- HTTP allowed for `localhost`, `127.0.0.1`, `[::1]`
- Invalid origins are logged and skipped
- Cannot use `allowCredentials=true` with wildcard origin

### Recommendations

- Never use wildcard (`*`) in production
- Only allow methods your application uses
- Set `allowCredentials=true` only if needed for cookie-based auth

## Rate Limiting

Per-IP and per-API-key token bucket rate limiting.

### Configuration

**Environment variables:**

```bash
CONSTELLATION_RATE_LIMIT_RPM=100
CONSTELLATION_RATE_LIMIT_BURST=20
```

**Programmatic:**

```scala
.withRateLimit(RateLimitConfig(
  requestsPerMinute = 100,
  burst = 20,
  keyRequestsPerMinute = 200,
  keyBurst = 40,
  exemptPaths = Set("/health", "/health/live", "/health/ready", "/metrics")
))
```

### Configuration Options

| Field | Default | Description |
|-------|---------|-------------|
| `requestsPerMinute` | 100 | Sustained rate per client IP |
| `burst` | 20 | Maximum burst size (bucket capacity) |
| `keyRequestsPerMinute` | 200 | Sustained rate per API key |
| `keyBurst` | 40 | Burst size per API key |
| `exemptPaths` | /health, /metrics | Paths that bypass rate limiting |

### Two-Layer Rate Limiting

Requests must pass both checks:

1. **Per-IP limit:** Every client is rate-limited by source IP
2. **Per-API-key limit:** Authenticated clients are also rate-limited by their key

This prevents a single API key from monopolizing an IP's budget and vice versa.

### Rate Limit Response

When limit is exceeded (429):

```json
{
  "error": "RateLimitExceeded",
  "message": "Too many requests, please try again later"
}
```

Includes `Retry-After` header with seconds until next token.

### Client IP Extraction

Uses `remoteAddr` from the request. For reverse proxy setups, configure your proxy to set trusted headers and adjust the server accordingly.

## Middleware Stack Order

When multiple security features are enabled:

```
Client Request
  -> CORS (preflight handling)
    -> Rate Limit (per-IP/key throttling)
      -> Auth (key + role validation)
        -> Application Routes
```

## Full Example

```scala
import io.constellation.http._

ConstellationServer.builder(constellation, compiler)
  .withPort(8080)
  .withAuth(AuthConfig(hashedKeys = List(
    HashedApiKey("strong-admin-key-32chars-min", ApiRole.Admin),
    HashedApiKey("app-service-key-for-execution", ApiRole.Execute)
  )))
  .withCors(CorsConfig(
    allowedOrigins = Set("https://app.example.com"),
    allowedMethods = Set("GET", "POST"),
    allowCredentials = false
  ))
  .withRateLimit(RateLimitConfig(
    requestsPerMinute = 60,
    burst = 10
  ))
  .withHealthChecks(HealthCheckConfig(
    enableDetailEndpoint = true,
    detailRequiresAuth = true
  ))
  .run
```

## Best Practices

### Production Deployments

1. **Enable authentication** with strong random API keys (32+ characters)
2. **Specify exact CORS origins** (never use wildcard)
3. **Enable rate limiting** to protect against abuse
4. **Use a reverse proxy** (nginx, envoy) for TLS termination
5. **Keep `/health/detail` auth-gated** (exposes internal state)

### API Key Management

1. Use strong random strings (32+ characters)
2. Rotate keys regularly
3. Use environment variables, not hardcoded keys
4. In Kubernetes, use Secrets (not ConfigMaps) for API keys

### Error Information

Constellation limits information disclosure:

| Endpoint | Information Exposed |
|----------|---------------------|
| `/health` | `{"status": "ok"}` only |
| `/health/detail` | Full diagnostics (auth-gated) |
| Error responses | Error code + message, no stack traces |

## See Also

- [execution.md](./execution.md) - Execute protected endpoints
- [pipelines.md](./pipelines.md) - Manage protected resources
- [../architecture/](../README.md) - Security model overview
