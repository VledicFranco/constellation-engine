---
title: "Runbook"
sidebar_position: 3
---

# Operational Runbook

Common operational procedures and troubleshooting for Constellation Engine.

## Health Checks

### Verify Server is Running

```bash
curl http://localhost:8080/health
# Expected: {"status":"ok"}
```

### Check Readiness

```bash
curl http://localhost:8080/health/ready
# Expected: {"ready":true}
# During shutdown: {"ready":false,"reason":"System is draining"}
```

### Detailed Diagnostics

```bash
curl -H "Authorization: Bearer YOUR_API_KEY" http://localhost:8080/health/detail
```

Returns scheduler stats, lifecycle state, and custom check results.
Requires `enableDetailEndpoint = true` in HealthCheckConfig.

## Common Issues

### High Compilation Latency

**Symptoms:** `/compile` or `/run` requests are slow.

**Check cache hit rate:**
```bash
curl http://localhost:8080/metrics | jq .cache
```

- `hitRate` < 0.5 — sources are changing frequently or cache is too small
- `hitRate` > 0.8 — cache is working; latency may be from large programs

**Actions:**
- Ensure clients reuse compiled programs via `/execute` instead of recompiling
- Check program complexity (large DAGs take longer)
- Compilation has a 30-second timeout; if hit, simplify the program

### Rate Limit Errors (429)

**Symptoms:** Clients receive `429 Too Many Requests`.

**Diagnose:**
- Check `Retry-After` header in the 429 response
- Identify if it's per-IP or per-API-key limiting

**Actions:**
- Increase `CONSTELLATION_RATE_LIMIT_RPM` for higher throughput
- Increase `CONSTELLATION_RATE_LIMIT_BURST` for spiky traffic
- Distribute clients across multiple IPs if one IP is a bottleneck
- Use different API keys for different services to get separate rate budgets

### Request Body Too Large (413)

**Symptoms:** `413 PayloadTooLarge` response.

**Cause:** Request body exceeds 10MB limit on `/compile`, `/execute`, or `/run`.

**Actions:**
- Reduce source code size (split large programs)
- Reduce input data size
- The 10MB limit is a server-side safety measure and is not configurable via environment variable

### Scheduler Queue Full

**Symptoms:** Tasks are rejected or queued for a long time.

**Check scheduler stats:**
```bash
curl http://localhost:8080/metrics | jq .scheduler
```

- `queuedCount` consistently high — more work than the scheduler can handle
- `starvationPromotions` increasing — low-priority tasks waiting too long

**Actions:**
- Increase `CONSTELLATION_SCHEDULER_MAX_CONCURRENCY`
- Scale out (add more instances)
- Reduce task concurrency from clients
- Increase `CONSTELLATION_SCHEDULER_STARVATION_TIMEOUT` if low-priority delays are acceptable

### Memory Issues

**Symptoms:** `OutOfMemoryError`, slow GC pauses, process killed by OOM killer.

**Actions:**
- Increase `JAVA_OPTS` heap size: `-Xmx2g` or higher
- Check compilation cache size (entries in `/metrics`)
- Check for large programs with many modules
- Enable G1GC for better latency: `-XX:+UseG1GC`
- Monitor heap usage with JMX or `jstat`

### Authentication Failures (401/403)

**Symptoms:** Clients receive `401 Unauthorized` or `403 Forbidden`.

**Diagnose:**
- `401` — Missing or malformed `Authorization: Bearer <key>` header
- `403` — Key is valid but role doesn't permit the HTTP method

**Actions:**
- Verify the API key format: `Authorization: Bearer <key>`
- Check role permissions (ReadOnly can't POST, Execute can't DELETE)
- Verify the key was loaded at startup (check logs for "Loaded N API key(s)")

### WebSocket (LSP) Issues

**Symptoms:** LSP connection drops, messages lost, high latency.

**Possible causes:**
- Bounded queue full (100 messages) — client or server can't keep up
- Network interruption
- Proxy/load balancer closing idle WebSocket connections

**Actions:**
- Check server logs for "queue full, message dropped" warnings
- Configure proxy WebSocket timeout (increase from default)
- Ensure load balancer supports WebSocket upgrades
- Client should implement reconnection logic

## Operational Procedures

### Rolling Restart (Kubernetes)

```bash
kubectl rollout restart deployment constellation -n constellation
```

This performs a zero-downtime restart:
1. New pods start and become ready
2. Old pods are drained (readiness probe returns 503)
3. Old pods shut down after grace period

### Scale Up/Down

```bash
# Scale to 4 replicas
kubectl scale deployment constellation --replicas=4 -n constellation

# Check rollout status
kubectl rollout status deployment constellation -n constellation
```

### View Logs

```bash
# All pods
kubectl logs -l app=constellation -n constellation --tail=100

# Specific pod
kubectl logs constellation-xyz123 -n constellation -f
```

### Check Resource Usage

```bash
kubectl top pods -l app=constellation -n constellation
```

### Emergency: Force Restart

If a pod is unresponsive and liveness probe hasn't killed it yet:

```bash
kubectl delete pod constellation-xyz123 -n constellation
```

The deployment controller will create a replacement.

## Performance Tuning

### Compilation Cache

The compilation cache avoids redundant parsing and type checking. Key metrics:
- **hitRate** > 0.8 is healthy for production workloads
- Cache is per-instance (not shared across replicas)
- Cache evicts on TTL expiry or when entries exceed limits

### Scheduler Concurrency

For CPU-bound pipelines:
- Set `MAX_CONCURRENCY` to number of CPU cores
- Leave burst room for I/O-bound tasks

For I/O-bound pipelines (external API calls):
- Set `MAX_CONCURRENCY` higher (2-4x CPU cores)
- Monitor queue depth to avoid excessive queuing

### JVM Tuning

```bash
JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

- **Heap sizing:** Start with 1-2GB, increase if cache misses cause frequent compilation
- **GC:** G1GC recommended for production (good latency/throughput balance)
- **GC pause target:** 200ms is a reasonable starting point

## Alerting Thresholds

| Metric | Warning | Critical |
|--------|---------|----------|
| `health/ready` | N/A | Returns `false` |
| `health/live` | N/A | No response / 5xx |
| `cache.hitRate` | < 0.5 | < 0.2 |
| `scheduler.queuedCount` | > 50% of maxConcurrency | > 100% of maxConcurrency |
| Response latency (P99) | > 5s | > 30s |
| Error rate (5xx) | > 1% | > 5% |
| Memory usage | > 80% of limit | > 95% of limit |
