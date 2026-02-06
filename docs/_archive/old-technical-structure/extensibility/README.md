# Extensibility

> **Path**: `docs/extensibility/`
> **Parent**: [docs/](../README.md)

SPI interfaces for custom backends.

## Contents

| File | Description |
|------|-------------|
| [cache-backend.md](./cache-backend.md) | Custom cache storage (Redis, Caffeine) |
| [execution-listener.md](./execution-listener.md) | Audit logs, event streaming |
| [metrics-provider.md](./metrics-provider.md) | Prometheus, Datadog, CloudWatch |
| [stores.md](./stores.md) | PipelineStore, SuspensionStore |

## SPI Overview

Constellation defines interfaces (SPIs) for extension:

| Interface | Purpose | Default |
|-----------|---------|---------|
| `CacheBackend` | Result caching | In-memory LRU |
| `ExecutionListener` | Lifecycle events | No-op |
| `MetricsProvider` | Metrics emission | No-op |
| `PipelineStore` | Pipeline persistence | In-memory |
| `SuspensionStore` | Execution snapshots | In-memory |

## Configuration

```scala
ConstellationBackends(
  cache = RedisCacheBackend(redisClient),
  listener = KafkaExecutionListener(producer),
  metrics = PrometheusMetricsProvider(registry),
  storage = PostgresPipelineStore(transactor)
)
```

## Extension Pattern

1. Implement the trait
2. Register via `ConstellationBackends`
3. Use in production deployment

```scala
// 1. Implement
class MyCacheBackend extends CacheBackend {
  def get[A](key: String): IO[Option[CacheEntry[A]]] = ...
  def set[A](key: String, value: A, ttl: Duration): IO[Unit] = ...
  // ...
}

// 2. Register
val backends = ConstellationBackends(
  cache = new MyCacheBackend()
)

// 3. Use
val constellation = Constellation.create(backends)
```

## Fire-and-Forget

`ExecutionListener` and `MetricsProvider` methods are fire-and-forget:
- Errors are logged but don't fail the pipeline
- Non-blocking execution

## Built-in Metrics

When `MetricsProvider` is registered, these are emitted automatically:

| Metric | Type | Tags |
|--------|------|------|
| `execution.started` | counter | dag_name |
| `execution.completed` | counter | dag_name, success |
| `execution.duration_ms` | histogram | dag_name |
| `module.started` | counter | module_name |
| `module.completed` | counter | module_name |
| `module.duration_ms` | histogram | module_name |
| `module.failed` | counter | module_name |
