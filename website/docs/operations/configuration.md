---
title: "Configuration"
sidebar_position: 1
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

Use `*` for wildcard (development only). When empty, CORS middleware is not applied.

**Example:**
```bash
CONSTELLATION_CORS_ORIGINS="https://app.example.com,https://admin.example.com"
```

### Rate Limiting

| Variable | Default | Description |
|----------|---------|-------------|
| `CONSTELLATION_RATE_LIMIT_RPM` | `100` | Requests per minute per client IP |
| `CONSTELLATION_RATE_LIMIT_BURST` | `20` | Token bucket burst capacity |

Rate limiting is only active when explicitly enabled via the server builder (`.withRateLimit()`). Two layers are applied:
1. **Per-IP** — every client is rate-limited by source IP address
2. **Per-API-key** — authenticated clients are also rate-limited by their key (200 RPM, burst 40)

Both checks must pass. Exempt paths: `/health`, `/health/live`, `/health/ready`, `/metrics`.

When a client exceeds the limit, a `429 Too Many Requests` response is returned with a `Retry-After` header.

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

## Security Considerations

- API keys are never logged — only counts and role distributions appear in logs
- CORS origin URLs are never logged — only counts appear
- API keys are stored as SHA-256 hashes in memory (not plaintext)
- Bearer tokens in `Authorization` headers are hashed before use as rate limit bucket keys
- The `/health/detail` endpoint exposes internal state and requires authentication by default
