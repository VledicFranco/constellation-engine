---
title: "Configuration"
sidebar_position: 1
description: "Complete reference for environment variables, auth, CORS, rate limiting, and health checks."
---

# Configuration Reference

Complete reference for all Constellation Engine environment variables, defaults, and configuration options.

## Environment Variables

### Server

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_PORT` | `8080` | HTTP server listen port |

### Scheduler

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_SCHEDULER_ENABLED` | `false` | Enable bounded priority scheduler |
| `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY` | `16` | Maximum concurrent tasks |
| `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` | `30s` | Time before low-priority tasks get priority boost |

When the scheduler is disabled (default), tasks execute immediately with no concurrency limit. Enable it when you need to bound resource usage or prioritize workloads.

### Authentication

:::danger
Authentication is **disabled by default**. In production, always set `CONSTELLATION_API_KEYS` to prevent unauthorized access to your pipelines and execution endpoints.
:::

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_API_KEYS` | *(none)* | Comma-separated `key:Role` pairs |

**Roles:**

| Role | Allowed Methods |
|------|-----------------|
| `Admin` | All HTTP methods |
| `Execute` | GET + POST |
| `ReadOnly` | GET only |

**Key requirements:**
- Minimum 24 characters
- Only printable ASCII (no whitespace, no control characters)
- No `=` or `,` characters (used as delimiters)

**Example:**
```bash
CONSTELLATION_API_KEYS="sk-prod-abc123def456ghi789jkl0:Admin,sk-readonly-xyz987wvu654tsr321:ReadOnly"
```

**Public paths** exempt from authentication: `/health`, `/health/live`, `/health/ready`, `/metrics`.

### CORS

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_CORS_ORIGINS` | *(none)* | Comma-separated allowed origin URLs |

:::warning
Using `*` (wildcard) for CORS origins allows any website to make requests to your API. This is acceptable for local development, but in production always specify exact origin URLs.
:::

When empty, CORS middleware is not applied.

**Example:**
```bash
CONSTELLATION_CORS_ORIGINS="https://app.example.com,https://admin.example.com"
```

### Rate Limiting

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_RATE_LIMIT_RPM` | `100` | Requests per minute per client IP |
| `CONSTELLATION_RATE_LIMIT_BURST` | `20` | Token bucket burst capacity |

:::tip
Rate limiting is only active when explicitly enabled via the server builder (`.withRateLimit()`). The defaults (100 RPM, 20 burst) are conservative starting points. Adjust based on your expected traffic patterns and pipeline execution times.
::: Two layers are applied:
1. **Per-IP** — every client is rate-limited by source IP address
2. **Per-API-key** — authenticated clients are also rate-limited by their key (200 RPM, burst 40)

Both checks must pass. Exempt paths: `/health`, `/health/live`, `/health/ready`, `/metrics`.

When a client exceeds the limit, a `429 Too Many Requests` response is returned with a `Retry-After` header.

### Module Provider (gRPC)

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_PROVIDER_PORT` | `9090` | gRPC port for module provider registrations |
| `CONSTELLATION_PROVIDER_HEARTBEAT_TIMEOUT` | `15s` | Auto-deregister providers after this heartbeat lapse |
| `CONSTELLATION_PROVIDER_CONTROL_PLANE_TIMEOUT` | `30s` | Deadline for providers to establish control plane stream |
| `CONSTELLATION_PROVIDER_RESERVED_NS` | `stdlib` | Comma-separated namespace prefixes that providers cannot use |

These variables only apply when using `ModuleProviderManager` to accept external module registrations via gRPC. The gRPC port is separate from the HTTP port (`CONSTELLATION_PORT`). See the [Module Provider Integration Guide](../integrations/module-provider.md) for setup instructions.

### Dashboard

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_DASHBOARD_ENABLED` | `true` | Enable the web dashboard |
| `CONSTELLATION_CST_DIR` | *(current working directory)* | Directory to scan for `.cst` files |
| `CONSTELLATION_SAMPLE_RATE` | `1.0` | Execution storage sampling rate (0.0–1.0) |
| `CONSTELLATION_MAX_EXECUTIONS` | `1000` | Maximum stored execution records |

### JVM (Docker/Kubernetes)

| Variable | Default | Description |
|----------|---------|-------------|
| `JAVA_OPTS` | `-Xms256m -Xmx1g` | JVM memory and GC options |

## Programmatic Configuration

Environment variables configure the server at startup. For programmatic control, use the builder API:

```scala
ConstellationServer.builder(constellation, compiler)
  .withAuth(AuthConfig(apiKeys = Map("key" -> ApiRole.Admin)))
  .withCors(CorsConfig(allowedOrigins = Set("https://app.example.com")))
  .withRateLimit(RateLimitConfig(requestsPerMinute = 100, burst = 20))
  .withHealthChecks(HealthCheckConfig(enableDetailEndpoint = true))
  .run
```

## Validation

All configuration is validated at server startup:
- Invalid auth keys are rejected with a warning (server still starts with valid keys)
- Invalid CORS origins are rejected with a warning
- Invalid rate limit values (non-positive RPM or burst) fail server startup
- Configuration summary is logged at startup showing which features are enabled

## Health Check Configuration

Constellation provides multiple health endpoints with configurable behavior for different deployment scenarios.

### Health Endpoints Overview

| Endpoint | Purpose | Default Behavior |
|----------|---------|------------------|
| `GET /health` | Basic health check | Always returns `{"status":"ok"}` |
| `GET /health/live` | Liveness probe | Returns 200 if process is alive |
| `GET /health/ready` | Readiness probe | Returns 200 if ready, 503 if draining |
| `GET /health/detail` | Deep diagnostics | Disabled by default (opt-in) |

### Enabling the Detail Endpoint

The `/health/detail` endpoint provides comprehensive diagnostics but is disabled by default for security. Enable it via `HealthCheckConfig`:

```scala
ConstellationServer.builder(constellation, compiler)
  .withHealthChecks(HealthCheckConfig(
    enableDetailEndpoint = true
  ))
  .run
```

When enabled, `/health/detail` returns:

```json
{
  "status": "ok",
  "lifecycle": "Running",
  "scheduler": {
    "activeCount": 5,
    "queuedCount": 12,
    "totalSubmitted": 1542
  },
  "cache": {
    "hitRate": 0.85,
    "entries": 234,
    "evictions": 12
  },
  "checks": {
    "database": { "status": "ok", "latencyMs": 2 },
    "redis": { "status": "ok", "latencyMs": 1 }
  }
}
```

### Custom Health Checks

Add custom health checks to verify external dependencies:

```scala
import io.constellation.http.health.{HealthCheck, HealthCheckResult}
import cats.effect.IO
import scala.concurrent.duration._

// Simple check
val dbCheck = HealthCheck("database") {
  IO(dataSource.getConnection())
    .flatMap(conn => IO(conn.close()))
    .as(HealthCheckResult.ok("Connected"))
    .timeout(5.seconds)
    .handleError(e => HealthCheckResult.unhealthy(e.getMessage))
}

// Check with latency measurement
val redisCheck = HealthCheck.timed("redis") {
  redisClient.ping.as(HealthCheckResult.ok("PONG"))
}

ConstellationServer.builder(constellation, compiler)
  .withHealthChecks(HealthCheckConfig(
    enableDetailEndpoint = true,
    customChecks = List(dbCheck, redisCheck)
  ))
  .run
```

### Liveness vs Readiness Configuration

**Liveness probe** (`/health/live`):
- Always returns 200 if the JVM process is running
- Used by Kubernetes to restart crashed pods
- Should NOT check external dependencies (database, cache, etc.)
- Failure triggers pod restart

**Readiness probe** (`/health/ready`):
- Returns 200 when the instance can serve traffic
- Returns 503 during:
  - Startup (before initialization completes)
  - Graceful shutdown (draining state)
- Used by Kubernetes to route/stop traffic
- Failure removes pod from Service endpoints

#### Kubernetes Configuration

```yaml
spec:
  containers:
    - name: constellation
      livenessProbe:
        httpGet:
          path: /health/live
          port: 8080
        initialDelaySeconds: 30    # Wait for JVM startup
        periodSeconds: 10          # Check every 10s
        timeoutSeconds: 5          # Fail if no response in 5s
        failureThreshold: 3        # Restart after 3 consecutive failures

      readinessProbe:
        httpGet:
          path: /health/ready
          port: 8080
        initialDelaySeconds: 10    # Shorter than liveness
        periodSeconds: 5           # Check more frequently
        timeoutSeconds: 3          # Fail faster
        failureThreshold: 1        # Remove from service immediately
```

#### Why Different Configurations?

| Setting | Liveness | Readiness | Rationale |
|---------|----------|-----------|-----------|
| `initialDelaySeconds` | 30s | 10s | Liveness allows more startup time |
| `periodSeconds` | 10s | 5s | Readiness needs faster detection |
| `failureThreshold` | 3 | 1 | Liveness allows transient failures; readiness is strict |

### Startup Probe (Slow Initialization)

For applications with slow startup (large module registry, cold cache), use a startup probe to avoid liveness probe false positives:

```yaml
spec:
  containers:
    - name: constellation
      startupProbe:
        httpGet:
          path: /health/ready
          port: 8080
        initialDelaySeconds: 10
        periodSeconds: 5
        failureThreshold: 30      # 30 * 5s = 150s max startup time

      livenessProbe:
        httpGet:
          path: /health/live
          port: 8080
        periodSeconds: 10
        failureThreshold: 3
        # No initialDelaySeconds - startup probe handles this
```

The startup probe runs until success, then liveness and readiness probes take over.

### Health Check Timeouts

Health endpoints have internal timeouts to prevent hanging:

| Check Type | Timeout | Configurable |
|------------|---------|--------------|
| Basic `/health` | None | No |
| Liveness `/health/live` | None | No |
| Readiness `/health/ready` | 5s | No |
| Detail `/health/detail` | 10s total | No |
| Custom checks | Per-check | Yes (in `HealthCheck`) |

If a custom check exceeds its timeout, the detail endpoint marks it as unhealthy but still returns a response.

### Health Check Security

The `/health/detail` endpoint can expose sensitive information (queue depths, cache sizes, check results). Protect it:

1. **Require authentication** — When auth is enabled, `/health/detail` requires a valid API key:
   ```bash
   curl -H "Authorization: Bearer YOUR_API_KEY" http://localhost:8080/health/detail
   ```

2. **Network isolation** — Only expose detail endpoint on internal network:
   ```yaml
   # Kubernetes: Internal service for monitoring
   apiVersion: v1
   kind: Service
   metadata:
     name: constellation-internal
     namespace: constellation
   spec:
     type: ClusterIP
     selector:
       app.kubernetes.io/name: constellation-engine
     ports:
       - name: http-internal
         port: 8080
         targetPort: 8080
   ```

3. **Disable in production** — Keep `enableDetailEndpoint = false` (default) if not needed.

## Security Considerations

:::danger
The `/health/detail` endpoint exposes internal server state including queue depths, cache statistics, and custom health check results. In production, either keep it disabled (default) or ensure authentication is enabled.
:::

- API keys are never logged — only counts and role distributions appear in logs
- CORS origin URLs are never logged — only counts appear
- API keys are stored as SHA-256 hashes in memory (not plaintext)
- Bearer tokens in `Authorization` headers are hashed before use as rate limit bucket keys
- The `/health/detail` endpoint exposes internal state and requires authentication by default

## Next Steps

- [Deployment](./deployment.md) — Docker, Kubernetes, and production deployment
- [Scheduler](./scheduler.md) — Priority-based task scheduling with bounded concurrency
- [JSON Logging](./json-logging.md) — Structured logging for production environments
- [Runbook](./runbook.md) — Operational procedures and troubleshooting
