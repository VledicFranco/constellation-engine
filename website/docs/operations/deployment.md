---
title: "Deployment"
sidebar_position: 2
description: "Deploy Constellation Engine with Docker, Kubernetes, load balancing, and autoscaling."
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

If you use the [Module Provider Protocol](../integrations/module-provider.md), also expose the gRPC port:

```bash
docker run -d \
  -p 8080:8080 \
  -p 9090:9090 \
  -e CONSTELLATION_PORT=8080 \
  -e CONSTELLATION_PROVIDER_PORT=9090 \
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
| `service.yaml` | ClusterIP service on port 8080 (add port 9090 for module providers) |

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

:::warning
The LSP WebSocket endpoint (`/lsp`) requires session affinity. Without sticky sessions, WebSocket connections will be randomly distributed across pods and connection state will be lost on each request. See [Session Affinity Requirements](#session-affinity-requirements) below.
:::

## Graceful Shutdown

The server supports graceful shutdown:

1. Pod receives `SIGTERM`
2. Server enters **Draining** state
3. Readiness probe returns 503 (removed from service)
4. In-flight requests complete (up to 30s grace period)
5. Remaining requests are cancelled
6. Process exits

The K8s deployment sets `terminationGracePeriodSeconds: 30` to match.

:::tip
Set `terminationGracePeriodSeconds` to at least your drain timeout plus 15 seconds buffer. If Kubernetes kills the pod before the drain completes, in-flight requests will be terminated abruptly.
:::

## Hardened Production Example

```bash
docker run -d \
  -p 8080:8080 \
  -p 9090:9090 \
  -e CONSTELLATION_PORT=8080 \
  -e CONSTELLATION_PROVIDER_PORT=9090 \
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

:::note
Use `/health/live` for liveness probes (should the pod be restarted?) and `/health/ready` for readiness probes (should the pod receive traffic?). Never use `/health/ready` for liveness probes as it returns 503 during graceful shutdown, which would cause unnecessary pod restarts.
:::

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

## Multi-Instance Deployments

Constellation Engine supports horizontal scaling with multiple instances. Each instance is stateless at the HTTP layer, making it straightforward to run behind a load balancer.

### Running Multiple Instances

#### Docker Compose

```yaml
services:
  constellation-1:
    build: .
    ports:
      - "8080:8080"
    environment:
      CONSTELLATION_PORT: "8080"
      CONSTELLATION_SCHEDULER_ENABLED: "true"
      JAVA_OPTS: "-Xms512m -Xmx1g"

  constellation-2:
    build: .
    ports:
      - "8081:8080"
    environment:
      CONSTELLATION_PORT: "8080"
      CONSTELLATION_SCHEDULER_ENABLED: "true"
      JAVA_OPTS: "-Xms512m -Xmx1g"

  nginx:
    image: nginx:latest
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf
    depends_on:
      - constellation-1
      - constellation-2
```

#### Kubernetes

Increase replicas in the deployment:

```bash
kubectl scale deployment constellation-engine --replicas=4 -n constellation
```

Or set replicas in the manifest:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: constellation-engine
  namespace: constellation
spec:
  replicas: 4  # Multiple instances
  # ...
```

### Shared State Considerations

Each Constellation instance maintains its own in-memory state:

| State Type | Scope | Sharing Strategy |
|------------|-------|------------------|
| Compilation cache | Per-instance | Each instance has independent cache; consider distributed cache backend |
| Execution history | Per-instance | Use external storage (PostgreSQL, Redis) via `ExecutionStorage` SPI |
| Module registry | Per-instance | Modules are registered at startup; ensure identical configuration |
| Scheduler queue | Per-instance | Each instance schedules independently |
| Circuit breaker state | Per-instance | Each instance tracks failures independently |

For workloads requiring shared state across instances:

1. **Shared compilation cache** — Use a distributed `CacheBackend` like Memcached or Redis:
   ```scala
   import io.constellation.cache.memcached.{MemcachedCacheBackend, MemcachedConfig}

   MemcachedCacheBackend.resource(MemcachedConfig.cluster(
     "memcached-1:11211,memcached-2:11211"
   )).use { cache =>
     val compiler = LangCompilerBuilder()
       .withCacheBackend(cache)
       .build()
     // All instances share the same compilation cache
   }
   ```

2. **Shared execution history** — Implement `ExecutionStorage` backed by a database:
   ```scala
   val storage = new PostgresExecutionStorage(dataSource)
   ConstellationImpl.builder()
     .withBackends(ConstellationBackends(storage = Some(storage)))
     .build()
   ```

3. **Distributed circuit breakers** — For coordinated failure handling across instances, implement a custom circuit breaker backed by Redis or a coordination service.

### Load Balancing Strategies

#### Round-Robin (Default)

Suitable for stateless workloads where any instance can handle any request:

```nginx
upstream constellation {
    server constellation-1:8080;
    server constellation-2:8080;
    server constellation-3:8080;
}
```

Kubernetes Services use round-robin by default.

#### Least Connections

Better for variable-duration requests (long-running pipelines):

```nginx
upstream constellation {
    least_conn;
    server constellation-1:8080;
    server constellation-2:8080;
    server constellation-3:8080;
}
```

#### Weighted Distribution

For heterogeneous instances (different CPU/memory):

```nginx
upstream constellation {
    server constellation-1:8080 weight=3;  # More powerful
    server constellation-2:8080 weight=1;
}
```

### Session Affinity Requirements

Session affinity (sticky sessions) is **required** for:

| Feature | Reason | Affinity Type |
|---------|--------|---------------|
| LSP WebSocket | Connection state is per-instance | IP hash or cookie |
| Step-through debugging | Execution state is per-instance | IP hash or cookie |
| Suspendable executions | Resume must hit same instance | Execution ID routing |

#### Enabling Session Affinity in Kubernetes

```yaml
apiVersion: v1
kind: Service
metadata:
  name: constellation-engine
  namespace: constellation
spec:
  type: ClusterIP
  sessionAffinity: ClientIP
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 10800  # 3 hours
  selector:
    app.kubernetes.io/name: constellation-engine
  ports:
    - name: http
      port: 8080
      targetPort: 8080
    # Add this if using Module Provider Protocol:
    # - name: grpc-provider
    #   port: 9090
    #   targetPort: 9090
```

#### Nginx IP Hash

```nginx
upstream constellation {
    ip_hash;
    server constellation-1:8080;
    server constellation-2:8080;
}
```

#### Ingress Controller (nginx-ingress)

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: constellation
  namespace: constellation
  annotations:
    nginx.ingress.kubernetes.io/affinity: "cookie"
    nginx.ingress.kubernetes.io/session-cookie-name: "CONSTELLATION_AFFINITY"
    nginx.ingress.kubernetes.io/session-cookie-max-age: "10800"
spec:
  rules:
    - host: constellation.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: constellation-engine
                port:
                  number: 8080
```

### WebSocket Load Balancing

The LSP endpoint (`/lsp`) uses WebSocket. Ensure your load balancer:

1. **Supports WebSocket upgrades** — Most modern load balancers do
2. **Has appropriate timeouts** — WebSocket connections are long-lived
3. **Uses session affinity** — WebSocket state is per-connection

#### Nginx WebSocket Configuration

```nginx
upstream constellation {
    ip_hash;  # Required for WebSocket affinity
    server constellation-1:8080;
    server constellation-2:8080;
}

server {
    listen 80;

    location / {
        proxy_pass http://constellation;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;  # 1 hour for long-lived connections
    }
}
```

#### AWS ALB

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: constellation
  annotations:
    alb.ingress.kubernetes.io/target-type: ip
    alb.ingress.kubernetes.io/target-group-attributes: stickiness.enabled=true,stickiness.lb_cookie.duration_seconds=10800
spec:
  ingressClassName: alb
  rules:
    - host: constellation.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: constellation-engine
                port:
                  number: 8080
```

### Health Check Configuration for Load Balancers

Configure your load balancer to use the appropriate health endpoints:

| Endpoint | Use For | Expected Response |
|----------|---------|-------------------|
| `/health/live` | Liveness — is the process running? | 200 always (unless process crashed) |
| `/health/ready` | Readiness — should it receive traffic? | 200 when ready, 503 when draining |

```nginx
upstream constellation {
    server constellation-1:8080;
    server constellation-2:8080;

    # Health check (nginx plus / commercial)
    health_check uri=/health/ready interval=5s fails=2 passes=1;
}
```

For open-source nginx, use a sidecar or external health checker.

### Autoscaling

#### Kubernetes Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: constellation-hpa
  namespace: constellation
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: constellation-engine
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

#### Custom Metrics Scaling

For more sophisticated scaling based on scheduler queue depth or request latency, expose Prometheus metrics and use the Prometheus Adapter:

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: constellation-hpa-custom
  namespace: constellation
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: constellation-engine
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Pods
      pods:
        metric:
          name: constellation_scheduler_queued_count
        target:
          type: AverageValue
          averageValue: "50"  # Scale up when avg queue > 50
```

## Next Steps

- [Configuration](./configuration.md) — Environment variables, auth, CORS, and rate limiting
- [Graceful Shutdown](./graceful-shutdown.md) — Drain behavior and Kubernetes integration
- [Clustering](./clustering.md) — Distributed deployment with shared state
- [JSON Logging](./json-logging.md) — Structured logging for log aggregation
- [Runbook](./runbook.md) — Operational procedures and troubleshooting
