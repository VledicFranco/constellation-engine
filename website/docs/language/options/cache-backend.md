---
title: "cache_backend"
sidebar_position: 5
description: "Select a specific cache storage backend by name, such as in-memory, Memcached, or Redis."
---

# cache_backend

Specify a named cache storage backend.

## Syntax

```constellation
result = Module(args) with cache: <duration>, cache_backend: "<name>"
```

**Type:** String (quoted backend name)

## Description

The `cache_backend` option selects a specific cache storage backend by name. This allows different modules to use different caching strategies (in-memory, Memcached, Redis, etc.) based on their requirements.

This option requires `cache` to be specified. Without `cache`, specifying a backend generates a compiler warning.

## Examples

### Memcached Backend

```constellation
session = LoadSession(token) with cache: 1h, cache_backend: "memcached"
```

Use a distributed Memcached cache for session data.

### In-Memory Backend (Explicit)

```constellation
config = GetConfig(key) with cache: 5min, cache_backend: "memory"
```

Explicitly use the default in-memory cache.

### Redis Backend

```constellation
lookup = ExpensiveLookup(id) with cache: 30min, cache_backend: "redis"
```

Use a Redis backend for shared caching across instances.

## Available Backends

| Name | Source | Description | Use Case |
|------|--------|-------------|----------|
| `memory` | Built-in | In-memory with TTL + LRU eviction (default) | Development, single instance |
| `memcached` | [Optional module](/docs/modules/cache-memcached) | Distributed Memcached via spymemcached | High-throughput distributed caching |
| `redis` | [Custom SPI](/docs/integrations/cache-backend) | Distributed Redis (implement yourself) | Multi-instance with rich data structures |
| `caffeine` | [Custom SPI](/docs/integrations/cache-backend) | High-performance local cache (implement yourself) | Production single instance |

The `memory` backend ships with the core runtime. The `memcached` backend is available as a first-party [optional module](/docs/modules/cache-memcached). For Redis and Caffeine, implement the `CacheBackend` SPI — see the [integration guide](/docs/integrations/cache-backend) for complete examples.

## Backend Configuration

Backends are registered at application startup via `CacheRegistry`:

```scala
import io.constellation.cache.{CacheRegistry, InMemoryCacheBackend}
import io.constellation.cache.memcached.{MemcachedCacheBackend, MemcachedConfig}

MemcachedCacheBackend.resource(MemcachedConfig.single()).use { memcached =>
  for {
    registry <- CacheRegistry.withBackends(
      "memory"    -> InMemoryCacheBackend(),
      "memcached" -> memcached
    )
    // constellation-lang programs can now use:
    //   cache_backend: "memory"
    //   cache_backend: "memcached"
  } yield ()
}
```

You can also set a global default via `ConstellationBuilder.withCache()`:

```scala
ConstellationImpl.builder()
  .withCache(memcachedBackend)  // All modules use Memcached by default
  .build()
```

## Behavior

1. Look up the named backend in the cache registry
2. If found, use that backend for cache operations
3. If not found, create a new `InMemoryCacheBackend` as fallback
4. Proceed with normal cache behavior (check, store, return)

## Related Options

- **[cache](./cache.md)** — Required to enable caching with TTL

## Related Pages

- **[CacheBackend SPI](/docs/integrations/cache-backend)** — Implement a custom backend
- **[Memcached Module](/docs/modules/cache-memcached)** — First-party Memcached backend
- **[Optional Modules](/docs/modules/)** — All available first-party modules

## Diagnostics

| Warning | Cause |
|---------|-------|
| `cache_backend` without `cache` | Backend requires the `cache` option to be set |

## Best Practices

- Use `memory` for local, single-instance caches during development
- Use `memcached` or a custom Redis backend for distributed production deployments
- Configure backends at application startup before running any pipelines
- Match backend choice to data consistency and latency requirements
- Use `keyPrefix` (Memcached) or key namespacing to isolate tenants sharing a cache cluster
