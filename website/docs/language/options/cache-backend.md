---
title: "cache-backend"
sidebar_position: 5
---

# cache_backend

Specify a named cache storage backend.

## Syntax

```constellation
result = Module(args) with cache: <duration>, cache_backend: "<name>"
```

**Type:** String (quoted backend name)

## Description

The `cache_backend` option selects a specific cache storage backend by name. This allows different modules to use different caching strategies (in-memory, Redis, Memcached, etc.) based on their requirements.

This option requires `cache` to be specified. Without `cache`, specifying a backend generates a compiler warning.

## Examples

### Redis Backend

```constellation
session = LoadSession(token) with cache: 1h, cache_backend: "redis"
```

Use a distributed Redis cache for session data.

### In-Memory Backend (Explicit)

```constellation
config = GetConfig(key) with cache: 5min, cache_backend: "memory"
```

Explicitly use the default in-memory cache.

### Caffeine Backend

```constellation
lookup = ExpensiveLookup(id) with cache: 30min, cache_backend: "caffeine"
```

Use Caffeine for high-performance local caching.

## Available Backends

| Name | Description | Use Case |
|------|-------------|----------|
| `memory` | In-memory with TTL (default) | Development, single instance |
| `caffeine` | High-performance local cache | Production single instance |
| `redis` | Distributed Redis cache | Multi-instance deployments |
| `memcached` | Distributed Memcached | High-throughput caching |

## Backend Configuration

Backends are configured in the runtime environment. Example configuration:

```scala
// Register custom backends
cacheRegistry.register("redis", new RedisCacheBackend(redisClient))
cacheRegistry.register("caffeine", new CaffeineCacheBackend(maxSize = 10000))
```

## Behavior

1. Look up the named backend in the cache registry
2. If found, use that backend for cache operations
3. If not found, fall back to the default backend (memory)
4. Proceed with normal cache behavior (check, store, return)

## Related Options

- **[cache](./cache.md)** - Required to enable caching with TTL

## Diagnostics

| Warning | Cause |
|---------|-------|
| cache_backend without cache | Backend requires cache option |

## Best Practices

- Use `memory` or `caffeine` for local, single-instance caches
- Use `redis` or `memcached` for distributed caching
- Configure backends at application startup
- Match backend choice to data consistency requirements
- Consider cache eviction policies for each backend
