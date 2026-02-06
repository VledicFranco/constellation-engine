# Extensibility

> **Path**: `docs/features/extensibility/`
> **Parent**: [features/](../README.md)

SPI for custom cache backends, metrics providers, execution listeners, and storage.

## Ethos Summary

- Extension points are **interfaces**, not inheritance
- Implementations are **pluggable at startup**
- Core functionality **does not depend on extensions**
- Extensions **cannot break core invariants**

## Contents

| File | Description |
|------|-------------|
| PHILOSOPHY.md | Why SPI over inheritance |
| ETHOS.md | Constraints for LLMs working on SPI |
| cache-backend.md | Custom cache storage |
| metrics-provider.md | Custom metrics export |
| execution-listener.md | Execution event hooks |
| execution-storage.md | Execution state persistence |

## Quick Reference

```scala
// Implement the interface
class RedisCacheBackend extends CacheBackend {
  def get(key: String): IO[Option[CValue]] = ...
  def set(key: String, value: CValue, ttl: FiniteDuration): IO[Unit] = ...
}

// Register at startup
constellation.withCacheBackend("redis", new RedisCacheBackend(client))
```

## Extension Points

| Interface | Purpose |
|-----------|---------|
| `CacheBackend` | Custom cache storage (Redis, Memcached, etc.) |
| `MetricsProvider` | Custom metrics export (Prometheus, DataDog, etc.) |
| `ExecutionListener` | Hooks for execution events (logging, tracing, etc.) |
| `ExecutionStorage` | Persist execution state for suspension/resume |

## Components Involved

| Component | Role | Key Files |
|-----------|------|-----------|
| `runtime` | SPI interfaces | `spi/*.scala` |
| `runtime` | Default implementations | `InMemoryCacheBackend.scala`, etc. |
