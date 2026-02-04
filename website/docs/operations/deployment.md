---
title: "Deployment"
sidebar_position: 2
---

# Deployment Guide

How to deploy Constellation Engine in production environments.

## Docker

### Build the Image

```bash
# Build fat JAR first
make assembly

# Build Docker image
make docker-build
```

The Dockerfile uses a multi-stage build:
1. **Build stage** — sbt assembly in a JDK image
2. **Runtime stage** — JRE-only image with non-root user, health check built in

### Run with Docker

```bash
docker run -d \
  -p 8080:8080 \
  -e CONSTELLATION_PORT=8080 \
  -e JAVA_OPTS="-Xms256m -Xmx1g" \
  --name constellation \
  constellation-engine:latest
```

### Docker Compose

```bash
docker-compose up -d
```

The `docker-compose.yml` includes health check configuration and environment variable defaults. Edit it to enable scheduler, rate limiting, or authentication.

## Kubernetes

Manifests are in `deploy/k8s/`.

### Apply Manifests

```bash
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/configmap.yaml
kubectl apply -f deploy/k8s/deployment.yaml
kubectl apply -f deploy/k8s/service.yaml
```

### What's Included

| Manifest | Description |
|----------|-------------|
| `namespace.yaml` | `constellation` namespace |
| `configmap.yaml` | Scheduler, rate limit, JVM settings |
| `deployment.yaml` | 2 replicas, probes, resource limits, security context |
| `service.yaml` | ClusterIP service on port 8080 |

### Health Probes

The deployment configures:

- **Liveness**: `GET /health/live` — restarts the pod if the process is unresponsive
  - Initial delay: 30s, period: 10s, timeout: 5s, failure threshold: 3
- **Readiness**: `GET /health/ready` — removes pod from service during draining/startup
  - Initial delay: 10s, period: 5s, timeout: 3s, failure threshold: 3

### Resource Limits

Default limits (adjust for your workload):

```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

### Scaling

Constellation Engine is stateless at the HTTP layer. Scale horizontally by increasing replicas:

```bash
kubectl scale deployment constellation --replicas=4 -n constellation
```

**Considerations:**
- Each instance has its own in-memory compilation cache (no shared cache)
- Pipeline state (compiled DAGs) is per-instance
- For shared state, use a load balancer with session affinity or an external store

### Ingress / Load Balancer

Expose the service via your preferred ingress controller:

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: constellation
  namespace: constellation
spec:
  rules:
    - host: constellation.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: constellation
                port:
                  number: 8080
```

For WebSocket (LSP) support, ensure your ingress supports WebSocket upgrades.

## Graceful Shutdown

The server supports graceful shutdown:

1. Pod receives `SIGTERM`
2. Server enters **Draining** state
3. Readiness probe returns 503 (removed from service)
4. In-flight requests complete (up to 30s grace period)
5. Remaining requests are cancelled
6. Process exits

The K8s deployment sets `terminationGracePeriodSeconds: 30` to match.

## Hardened Production Example

```bash
docker run -d \
  -p 8080:8080 \
  -e CONSTELLATION_PORT=8080 \
  -e CONSTELLATION_API_KEYS="sk-prod-your-admin-key-here-24chars:Admin" \
  -e CONSTELLATION_CORS_ORIGINS="https://your-app.example.com" \
  -e CONSTELLATION_RATE_LIMIT_RPM=200 \
  -e CONSTELLATION_RATE_LIMIT_BURST=40 \
  -e CONSTELLATION_SCHEDULER_ENABLED=true \
  -e CONSTELLATION_SCHEDULER_MAX_CONCURRENCY=8 \
  -e JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC" \
  --name constellation \
  constellation-engine:latest
```

## Monitoring

### Metrics Endpoint

`GET /metrics` returns JSON with:
- **server** — uptime, total request count
- **cache** — compilation cache hit rate, entries, evictions
- **scheduler** — active tasks, queue depth, starvation promotions

### Health Endpoints

| Endpoint | Purpose | Use For |
|----------|---------|---------|
| `GET /health` | Basic check | Simple uptime monitoring |
| `GET /health/live` | Liveness | K8s liveness probe |
| `GET /health/ready` | Readiness | K8s readiness probe, load balancer |
| `GET /health/detail` | Diagnostics | Deep health inspection (auth-gated) |

### Key Metrics to Monitor

| Metric | Healthy Range | Action if Outside |
|--------|---------------|-------------------|
| `cache.hitRate` | > 0.8 | Check for cache invalidation issues |
| `scheduler.queuedCount` | < maxConcurrency | Increase concurrency or scale out |
| `scheduler.starvationPromotions` | Low, stable | Increase starvation timeout or concurrency |
| `server.requests_total` | Increasing | If flat, check connectivity |

### Log Output

All logging uses SLF4J/Logback. Configure via `logback.xml` in the classpath.
Default log format includes timestamps, log level, logger name, and message.

Request IDs are included in error logs via the `X-Request-ID` header (auto-generated if not provided by client).
