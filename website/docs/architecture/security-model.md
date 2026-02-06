---
title: "Security Model"
sidebar_position: 2
description: "Trust boundaries, sandboxing, HTTP hardening"
---

# Security Model

This document describes the trust model, sandboxing properties, and security controls of Constellation Engine. It is intended for embedders evaluating the library for production use.

:::danger Security Warning
**Authentication, CORS, and rate limiting are all disabled by default.** An unconfigured Constellation server is open to any HTTP client. Always configure security for production deployments.
:::

## Production Security Checklist

Before deploying to production, ensure:

- [ ] **API Keys configured** — Set `CONSTELLATION_API_KEYS` environment variable
- [ ] **CORS restricted** — Set `CONSTELLATION_CORS_ORIGINS` to your domains (not `*`)
- [ ] **Rate limiting enabled** — Configure `CONSTELLATION_RATE_LIMIT_RPM`
- [ ] **Health detail disabled** — Don't expose `/health/detail` publicly
- [ ] **TLS enabled** — Use HTTPS via reverse proxy or load balancer
- [ ] **Module code reviewed** — All registered modules have been audited

**Quick secure startup:**

```bash
export CONSTELLATION_API_KEYS="admin-key-$(openssl rand -hex 16):Admin"
export CONSTELLATION_CORS_ORIGINS="https://your-app.example.com"
export CONSTELLATION_RATE_LIMIT_RPM=100
make server
```

## Trust Model

Constellation operates with the following trust boundaries:

| Component | Trusted? | Why |
|-----------|----------|-----|
| Module code (Scala) | **Yes** | Modules run with full JVM permissions. You deploy them. |
| constellation-lang scripts | **Partially** | Scripts can only call registered modules — they cannot execute arbitrary code. However, if a registered module performs dangerous operations, the script can invoke it. |
| JVM runtime | **Yes** | Standard JVM security model applies. |
| DAG structure | **Validated** | The compiler validates types, dependencies, and acyclicity at compile time. |
| User inputs (JSON) | **Validated** | Runtime coerces JSON to typed `CValue` values, rejecting invalid shapes. |
| HTTP clients | **Untrusted** | All HTTP hardening features exist to protect against untrusted network clients. |

**Key principle:** Constellation trusts the embedder (you), validates user inputs and scripts, and treats HTTP clients as untrusted.

## constellation-lang Sandboxing

Scripts written in constellation-lang have limited capabilities by design:

### What Scripts Can Do

- Declare typed inputs and outputs
- Call registered modules by name
- Merge records (`+`), project fields (`[f1, f2]`), use conditionals
- Use higher-order functions (`filter`, `map`, `all`, `any`)

### What Scripts Cannot Do

- Execute arbitrary Scala/Java code
- Access the filesystem, network, or environment variables
- Import libraries or load classes
- Perform reflection or class loading
- Access memory outside their DAG execution scope

### Module Permissions

Modules themselves run with full JVM permissions. A module implementation can do anything the JVM process can do — make HTTP calls, read files, execute system commands. **Review all module code before registering it.** The sandboxing boundary is between the script language and module implementations.

```
constellation-lang script (sandboxed)
    │
    ▼  calls by name only
Module implementation (full JVM permissions)
```

## HTTP Security

:::caution
All HTTP security features are **opt-in and disabled by default**. When not configured, they add zero overhead — but your server is completely open. See the [Production Security Checklist](#production-security-checklist) above.
:::

### Authentication

API key authentication with role-based access control:

```scala
import io.constellation.http._

ConstellationServer.builder(constellation, compiler)
  .withAuth(AuthConfig(apiKeys = Map(
    "admin-key-abc123" -> ApiRole.Admin,
    "app-key-def456"   -> ApiRole.Execute,
    "read-key-ghi789"  -> ApiRole.ReadOnly
  )))
```

**Roles and permissions:**

| Role | GET | POST | PUT | DELETE |
|------|-----|------|-----|--------|
| `Admin` | Yes | Yes | Yes | Yes |
| `Execute` | Yes | Yes | No | No |
| `ReadOnly` | Yes | No | No | No |

## API Roles

| Role | Permissions | Use Case |
|------|-------------|----------|
| **Admin** | All operations | Internal tooling, CI/CD pipelines, administrative dashboards |
| **Execute** | Run pipelines, read data | Application backends, integration services |
| **ReadOnly** | Health checks, metrics, introspection | Monitoring systems, load balancers, observability tools |

**Best practices for role assignment:**

- **Admin keys**: Use only in trusted environments (internal networks, CI/CD). Rotate frequently.
- **Execute keys**: Issue to application backends that need to run pipelines. One key per service.
- **ReadOnly keys**: Issue to monitoring systems. These keys can be more widely distributed.

**Authentication flow:**

1. Client sends `Authorization: Bearer <api-key>` header
2. Middleware looks up the key in the configured map
3. If found, checks role permissions against the HTTP method
4. Returns `401 Unauthorized` if key is missing/invalid, `403 Forbidden` if role lacks permission

**Public paths** bypass authentication entirely:
- `/health`
- `/health/live`
- `/health/ready`
- `/metrics`

**Environment variable configuration:**

```bash
CONSTELLATION_API_KEYS=admin-key-abc123:Admin,app-key-def456:Execute
```

### CORS

Cross-Origin Resource Sharing controls which browser origins can call the API:

```scala
.withCors(CorsConfig(
  allowedOrigins   = Set("https://app.example.com", "https://admin.example.com"),
  allowedMethods   = Set("GET", "POST", "PUT", "DELETE", "OPTIONS"),
  allowedHeaders   = Set("Content-Type", "Authorization"),
  allowCredentials = false,
  maxAge           = 3600L
))
```

**Recommendations:**
- Never use wildcard (`*`) in production — specify exact origins
- Only allow methods your application actually uses
- Set `allowCredentials = true` only if you need cookie-based auth alongside API keys

**Environment variable:**

```bash
CONSTELLATION_CORS_ORIGINS=https://app.example.com,https://admin.example.com
```

### Rate Limiting

Per-IP token bucket rate limiting protects against abuse:

```scala
.withRateLimit(RateLimitConfig(
  requestsPerMinute = 100,
  burst             = 20,
  exemptPaths       = Set("/health", "/health/live", "/health/ready", "/metrics")
))
```

When a client exceeds the limit, the server returns `429 Too Many Requests` with a `Retry-After` header indicating when to retry.

**Client IP extraction:** The middleware reads `X-Forwarded-For` first (for reverse proxy setups), then falls back to the remote address.

**Environment variables:**

```bash
CONSTELLATION_RATE_LIMIT_RPM=100
CONSTELLATION_RATE_LIMIT_BURST=20
```

### Middleware Stack Order

When multiple security features are enabled, they apply in this order (outermost to innermost):

```
Client Request
  → CORS (preflight handling)
    → Rate Limit (per-IP throttling)
      → Auth (key + role validation)
        → Application Routes
```

## Input Validation

### Compile-Time

The compiler validates all constellation-lang scripts before execution:

- **Type checking** — ensures function arguments match expected types
- **Undefined references** — catches misspelled variable and function names
- **Cycle detection** — rejects circular dependencies in the DAG
- **Field validation** — verifies projected fields exist on the source record

### Runtime

When executing a compiled DAG, the runtime validates inputs:

- JSON values are coerced to typed `CValue` instances
- Missing required inputs produce `InputValidationError`
- Type mismatches between provided JSON and expected `CType` produce `TypeConversionError`

## Error Information Disclosure

Constellation is conservative about exposing internal details to HTTP clients:

| Endpoint | Information Exposed | Access Control |
|----------|-------------------|----------------|
| `/health` | `{"status": "ok"}` | Public |
| `/health/live` | `{"status": "alive"}` | Public |
| `/health/ready` | `{"ready": true/false}` | Public |
| `/health/detail` | Component diagnostics, scheduler stats, lifecycle state | **Opt-in**, auth-gated by default |
| Error responses | Error code + message, no stack traces | Role-based |

**`/health/detail`** is disabled by default. Enable it explicitly:

```scala
.withHealthChecks(HealthCheckConfig(
  enableDetailEndpoint = true,
  detailRequiresAuth   = true  // default: requires auth
))
```

**Error responses** include structured error codes and messages but never expose Java stack traces, internal file paths, or implementation details to HTTP clients.

## Dependency Audit

Constellation's runtime dependencies are from the Typelevel ecosystem:

| Dependency | Version | Purpose |
|------------|---------|---------|
| cats-core | 2.10.0 | Functional abstractions |
| cats-effect | 3.5.2 | IO monad, concurrency |
| cats-parse | 1.0.0 | Parser combinators |
| circe | 0.14.6 | JSON encoding/decoding |
| http4s | 0.23.25 | HTTP server (only in `http-api` module) |
| log4cats | 2.6.0 | Structured logging |

**Notable properties:**
- No native (JNI) dependencies
- No network calls at startup
- No telemetry or phone-home behavior
- All dependencies are open-source with permissive licenses (MIT/Apache 2.0)

## Recommendations

### Network-Exposed Deployments

:::tip
If your Constellation server is accessible over a network, all five of these are required:
:::

1. **Enable authentication** — configure API keys with appropriate roles
2. **Set specific CORS origins** — never use wildcard in production
3. **Enable rate limiting** — protect against abuse and accidental load spikes
4. **Use a reverse proxy** — place nginx/envoy in front for TLS termination
5. **Keep `/health/detail` auth-gated** — it exposes internal component state

### Module Code Review

Since modules run with full JVM permissions:

1. **Review all module code** before registering it in production
2. **Limit module capabilities** — prefer pure modules over IO-based ones where possible
3. **Avoid shell execution** in modules — never pass user input to `Runtime.exec` or similar
4. **Validate external inputs** within module implementations — don't trust data from external APIs

### Resource Management

1. **Use bounded scheduler** in production — unbounded scheduling can exhaust system resources
2. **Set execution timeouts** — prevent runaway pipelines with `withDefaultTimeout`
3. **Enable lifecycle management** — graceful shutdown prevents data loss on deployment
4. **Monitor queue depth** — growing queues indicate backpressure problems

### Configuration Security

1. **API keys** — use strong random strings (32+ characters), rotate regularly
2. **Environment variables** — prefer env vars over hardcoded keys in source
3. **Secrets management** — in Kubernetes, use Secrets rather than ConfigMaps for API keys
