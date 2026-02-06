# Extensibility

> **Path**: `docs/features/extensibility/`
> **Parent**: [features/](../README.md)

Service Provider Interfaces (SPI) for custom cache backends, metrics providers, execution listeners, and storage. Constellation Engine's extensibility model enables integration with your infrastructure without modifying core code.

## Ethos Summary

- Extension points are **interfaces**, not inheritance
- Implementations are **pluggable at startup**
- Core functionality **does not depend on extensions**
- Extensions **cannot break core invariants**
- New methods have **defaults** for backward compatibility

## Contents

| File | Description |
|------|-------------|
| [PHILOSOPHY.md](./PHILOSOPHY.md) | Why SPI over inheritance, design principles |
| [ETHOS.md](./ETHOS.md) | Constraints for LLMs working on SPI |
| [cache-backend.md](./cache-backend.md) | Custom cache storage (Redis, Caffeine, etc.) |
| [metrics-provider.md](./metrics-provider.md) | Custom metrics export (Prometheus, DataDog, etc.) |
| [execution-listener.md](./execution-listener.md) | Execution event hooks (Kafka, audit logs, etc.) |
| [execution-storage.md](./execution-storage.md) | Pipeline and suspension persistence |

## Quick Reference

```scala
import io.constellation.ConstellationImpl
import io.constellation.cache.InMemoryCacheBackend
import io.constellation.spi._

// Implement the interface
class RedisCacheBackend(client: RedisClient) extends CacheBackend {
  def get[A](key: String): IO[Option[CacheEntry[A]]] = ...
  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit] = ...
  def delete(key: String): IO[Boolean] = ...
  def clear: IO[Unit] = ...
  def stats: IO[CacheStats] = ...
}

// Register at startup
val constellation = ConstellationImpl.builder()
  .withCache(new RedisCacheBackend(client))
  .withMetrics(new PrometheusMetricsProvider(registry))
  .withListener(ExecutionListener.composite(kafkaListener, auditListener))
  .build()
```

## Extension Points

| Interface | Purpose | Default |
|-----------|---------|---------|
| `CacheBackend` | Custom cache storage (Redis, Memcached, etc.) | `InMemoryCacheBackend` |
| `MetricsProvider` | Custom metrics export (Prometheus, DataDog, etc.) | `MetricsProvider.noop` |
| `ExecutionListener` | Hooks for execution events (logging, tracing, etc.) | `ExecutionListener.noop` |
| `PipelineStore` | Compiled pipeline persistence | `PipelineStoreImpl` (in-memory) |
| `SuspensionStore` | Suspended execution persistence | `InMemorySuspensionStore` |

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `runtime/spi` | SPI trait definitions | `MetricsProvider.scala`, `ExecutionListener.scala`, `ConstellationBackends.scala` |
| `runtime/cache` | Cache interfaces | `CacheBackend.scala`, `CacheEntry.scala`, `CacheStats.scala` |
| `runtime/cache` | Default cache implementation | `InMemoryCacheBackend.scala` |
| `runtime` | Storage interfaces | `PipelineStore.scala`, `SuspensionStore.scala` |
| `runtime/impl` | Default storage implementations | `PipelineStoreImpl.scala`, `InMemorySuspensionStore.scala` |

## Design Principles

1. **Minimal interface surface** - Small, focused traits
2. **Effectful returns** - All methods return `IO[_]`
3. **Fire-and-forget for observability** - Metrics/listeners don't block execution
4. **Composable** - Multiple listeners via `ExecutionListener.composite`
5. **Backward compatible evolution** - New methods have defaults

See [PHILOSOPHY.md](./PHILOSOPHY.md) for the full rationale.

## For AI Agents

When modifying SPIs, follow the constraints in [ETHOS.md](./ETHOS.md):

- Never remove or change existing method signatures
- Add new methods with default implementations
- Run all tests after changes (`make test`)
- Update documentation in this directory
