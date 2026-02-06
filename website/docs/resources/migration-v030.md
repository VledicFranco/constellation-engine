---
title: "Migration: v0.3.0"
sidebar_position: 2
description: "Upgrading from v0.2.x to v0.3.0"
---

# Migration Guide: v0.3.0

This guide covers upgrading from v0.2.x to v0.3.0. All new features are **opt-in** — existing code continues to work without changes.

:::tip Smooth Upgrade Path
v0.3.0 is fully backward-compatible. You can upgrade dependencies, verify tests pass, and adopt new features incrementally. No code changes are required for the upgrade itself.
:::

## Summary

v0.3.0 introduces:

- **SPI (Service Provider Interface)** — plug in metrics, tracing, event listeners, caching
- **Execution lifecycle** — cancellation, circuit breakers, graceful shutdown, backpressure
- **HTTP hardening** — authentication, CORS, rate limiting, deep health checks
- **Deployment artifacts** — Dockerfile, Docker Compose, Kubernetes manifests, fat JAR
- **Bounded scheduler** — priority-based scheduling with configurable concurrency
- **Documentation** — embedding guide, security model, performance tuning, error reference

## Non-Breaking Changes

:::note Zero-Overhead by Default
All new features are disabled by default, meaning your application has the exact same behavior and performance characteristics as v0.2.x until you explicitly opt in.
:::

All new features are disabled by default. Your existing code works identically:

| Feature | Default State | Impact When Disabled |
|---------|--------------|---------------------|
| `ConstellationBackends` | All fields no-op | Zero overhead |
| Circuit breakers | Not configured | No rejection |
| Authentication | Not configured | No auth checks |
| CORS | Not configured | No CORS headers |
| Rate limiting | Not configured | No rate limits |
| Bounded scheduler | Unbounded (default) | Same as before |
| Lifecycle management | Not configured | No graceful shutdown |
| Health endpoints | `/health` unchanged | Same response |

## API Additions

### ConstellationBackends

New bundle type for pluggable integrations:

```scala
import io.constellation.spi.ConstellationBackends

// Before (v0.2.x): no backends concept
val constellation = ConstellationImpl.init

// After (v0.3.0): optionally configure backends
val constellation = ConstellationImpl.builder()
  .withBackends(ConstellationBackends(
    metrics  = myMetrics,     // MetricsProvider
    tracer   = myTracer,      // TracerProvider
    listener = myListener     // ExecutionListener
  ))
  .build()
```

`ConstellationImpl.init` still works and uses `ConstellationBackends.defaults` (all no-op).

### ConstellationBuilder

New builder pattern for `ConstellationImpl`:

```scala
// Before: ConstellationImpl.init
val constellation = ConstellationImpl.init

// After: builder pattern (init still works)
val constellation = ConstellationImpl.builder()
  .withScheduler(scheduler)
  .withBackends(backends)
  .withDefaultTimeout(60.seconds)
  .withLifecycle(lifecycle)
  .build()
```

### CancellableExecution

:::warning API Changed in v1.0
The `runDagCancellable` method was removed in v1.0. Use `IO.timeout` for cancellation as shown below.
:::

New trait for cancelling running pipelines:

```scala
// Note: runDagCancellable was subsequently removed in v1.0.
// Use IO.timeout for cancellation:
constellation.run(compiled.pipeline, inputs)
  .timeout(30.seconds)
```

### CircuitBreaker

New module-level circuit breaker:

```scala
import io.constellation.execution.{CircuitBreaker, CircuitBreakerConfig}

val cb = CircuitBreaker.create("my-module", CircuitBreakerConfig(
  failureThreshold  = 5,
  resetDuration     = 30.seconds,
  halfOpenMaxProbes = 1
))
```

### ConstellationLifecycle

New lifecycle manager for graceful shutdown:

```scala
import io.constellation.execution.ConstellationLifecycle

for {
  lifecycle <- ConstellationLifecycle.create
  constellation <- ConstellationImpl.builder()
    .withLifecycle(lifecycle)
    .build()
  // ... application runs ...
  _ <- lifecycle.shutdown(drainTimeout = 30.seconds)
} yield ()
```

### GlobalScheduler Configuration

New bounded scheduler with priority ordering:

```scala
import io.constellation.execution.GlobalScheduler

// Before: only unbounded (implicit)
val constellation = ConstellationImpl.init

// After: optionally bounded
GlobalScheduler.bounded(
  maxConcurrency    = 16,
  maxQueueSize      = 1000,
  starvationTimeout = 30.seconds
).use { scheduler =>
  ConstellationImpl.builder()
    .withScheduler(scheduler)
    .build()
}
```

### ServerBuilder Methods

New builder methods on `ConstellationServer.builder()`:

```scala
ConstellationServer.builder(constellation, compiler)
  .withAuth(AuthConfig(...))                   // New
  .withCors(CorsConfig(...))                   // New
  .withRateLimit(RateLimitConfig(...))          // New
  .withHealthChecks(HealthCheckConfig(...))     // New
  .run
```

### New Health Endpoints

| Endpoint | Status | Response |
|----------|--------|----------|
| `GET /health` | Unchanged | `{"status": "ok"}` |
| `GET /health/live` | **New** | `{"status": "alive"}` (always 200) |
| `GET /health/ready` | **New** | `{"ready": true}` or 503 |
| `GET /health/detail` | **New** | Component diagnostics (opt-in) |

## How to Opt In

### SPI Backends

Implement one or more SPI traits and pass them to `ConstellationBackends`:

```scala
val backends = ConstellationBackends(
  metrics  = new MyPrometheusMetrics(),
  tracer   = new MyOtelTracer(),
  listener = new MyKafkaListener(),
  cache    = Some(new MyRedisCache())
)

val constellation = ConstellationImpl.builder()
  .withBackends(backends)
  .build()
```

See the [SPI Integration Guides](../integrations/metrics-provider.md) for implementation examples.

### Lifecycle Management

```scala
for {
  lifecycle <- ConstellationLifecycle.create
  constellation <- ConstellationImpl.builder()
    .withLifecycle(lifecycle)
    .build()
  // On shutdown signal:
  _ <- lifecycle.shutdown(drainTimeout = 30.seconds)
} yield ()
```

### HTTP Hardening

Each feature is independent — enable any combination:

```scala
ConstellationServer.builder(constellation, compiler)
  // Auth: API key + role-based access
  .withAuth(AuthConfig(apiKeys = Map("key1" -> ApiRole.Admin)))
  // CORS: specific origins
  .withCors(CorsConfig(allowedOrigins = Set("https://myapp.com")))
  // Rate limiting: per-IP token bucket
  .withRateLimit(RateLimitConfig(requestsPerMinute = 100))
  // Health: enable detail endpoint
  .withHealthChecks(HealthCheckConfig(enableDetailEndpoint = true))
  .run
```

### Circuit Breakers

```scala
import io.constellation.execution.CircuitBreakerConfig

val constellation = ConstellationImpl.builder()
  .withCircuitBreaker(CircuitBreakerConfig(
    failureThreshold = 5,
    resetDuration = 30.seconds
  ))
  .build()
```

## New Environment Variables

All environment variables are prefixed with `CONSTELLATION_`:

| Variable | Default | Description | Phase |
|----------|---------|-------------|-------|
| `CONSTELLATION_PORT` | `8080` | HTTP server port | Existing |
| `CONSTELLATION_SCHEDULER_ENABLED` | `false` | Enable bounded scheduler | Phase 5 |
| `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY` | `16` | Max concurrent tasks | Phase 5 |
| `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` | `30s` | Starvation prevention | Phase 5 |
| `CONSTELLATION_API_KEYS` | (none) | API keys as `key:Role,...` | Phase 3 |
| `CONSTELLATION_CORS_ORIGINS` | (none) | CORS allowed origins | Phase 3 |
| `CONSTELLATION_RATE_LIMIT_RPM` | `100` | Requests per minute | Phase 3 |
| `CONSTELLATION_RATE_LIMIT_BURST` | `20` | Rate limit burst size | Phase 3 |
| `CONSTELLATION_CST_DIR` | (cwd) | Dashboard script directory | Existing |
| `CONSTELLATION_SAMPLE_RATE` | `1.0` | Execution sampling rate | Existing |
| `CONSTELLATION_MAX_EXECUTIONS` | `1000` | Max stored executions | Existing |
| `CONSTELLATION_DEBUG` | `false` | Enable debug mode | Existing |

## Deployment Artifacts

v0.3.0 includes reference deployment artifacts:

| Artifact | Location | Purpose |
|----------|----------|---------|
| Dockerfile | `Dockerfile` | Multi-stage build (JDK 17 builder, JRE 17 runtime) |
| Docker Compose | `docker-compose.yml` | Dev stack with health checks |
| K8s manifests | `deploy/k8s/` | Namespace, Deployment, Service, ConfigMap |
| Fat JAR | `make assembly` | Single executable JAR |

Build and run:

```bash
make assembly       # Build fat JAR
make docker-build   # Build Docker image
make docker-run     # Run in Docker
```

## Related Documentation

- [Embedding Guide](../getting-started/embedding-guide.md) — complete setup for embedding in your JVM app
- [Security Model](../architecture/security-model.md) — trust boundaries and HTTP hardening details
- [Performance Tuning](../operations/performance-tuning.md) — scheduler, circuit breakers, caching
- [Error Reference](../api-reference/error-reference.md) — all error codes and resolutions
- [SPI Integration Guides](../integrations/metrics-provider.md) — implementations for popular libraries
