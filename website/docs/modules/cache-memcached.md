---
title: "Memcached Cache"
sidebar_position: 2
description: "Distributed cache backend using Memcached with spymemcached client for non-blocking I/O."
---

# Memcached Cache Backend

A first-party distributed cache backend backed by [Memcached](https://memcached.org/), using the [spymemcached](https://github.com/couchbase/spymemcached) client for non-blocking I/O.

## Installation

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "io.github.vledicfranco" %% "constellation-cache-memcached" % "0.7.0"
```

This module depends on `constellation-runtime` and brings in `net.spy:spymemcached:2.12.3`.

## Quick Start

```scala
import io.constellation.cache.memcached.{MemcachedCacheBackend, MemcachedConfig}
import io.constellation.impl.ConstellationImpl

MemcachedCacheBackend.resource(MemcachedConfig.single()).use { backend =>
  for {
    constellation <- ConstellationImpl.builder()
      .withCache(backend)
      .build()
    // ... run pipelines
  } yield ()
}
```

The `resource` factory manages the client lifecycle — the Memcached connection is established on acquisition and shut down on release.

## Configuration

`MemcachedConfig` controls connection behavior:

```scala
final case class MemcachedConfig(
  addresses: List[String]           = List("localhost:11211"),
  operationTimeout: FiniteDuration  = 2500.millis,
  connectionTimeout: FiniteDuration = 5.seconds,
  maxReconnectDelay: FiniteDuration = 30.seconds,
  keyPrefix: String                 = ""
)
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `addresses` | `localhost:11211` | Memcached server addresses in `host:port` format |
| `operationTimeout` | `2500ms` | Timeout for individual cache operations |
| `connectionTimeout` | `5s` | Timeout for establishing connections |
| `maxReconnectDelay` | `30s` | Maximum delay between reconnection attempts |
| `keyPrefix` | `""` | Optional prefix for all cache keys (multi-tenant isolation) |

### Factory Methods

```scala
// Single server
MemcachedConfig.single("cache.example.com:11211")

// Cluster
MemcachedConfig.cluster("host1:11211", "host2:11211", "host3:11211")
```

## Usage Patterns

### With ConstellationBuilder

The most common pattern — set Memcached as the default cache for all module execution:

```scala
MemcachedCacheBackend.resource(MemcachedConfig.single()).use { backend =>
  for {
    constellation <- ConstellationImpl.builder()
      .withCache(backend)
      .build()
    // All `cache: <duration>` options in constellation-lang now use Memcached
  } yield ()
}
```

### With CacheRegistry (Multiple Backends)

Register Memcached alongside other backends for per-module backend selection via `cache_backend`:

```scala
import io.constellation.cache.{CacheRegistry, InMemoryCacheBackend}

MemcachedCacheBackend.resource(config).use { memcached =>
  for {
    registry <- CacheRegistry.withBackends(
      "memory"    -> InMemoryCacheBackend(),
      "memcached" -> memcached
    )
    // In constellation-lang:
    // result = Slow(x) with cache: 10min, cache_backend: "memcached"
  } yield ()
}
```

### With LangCompilerBuilder

Provide a custom cache backend to the compilation cache (must be in-memory — `CompilationOutput` contains non-serializable closures):

```scala
import io.constellation.cache.InMemoryCacheBackend

val compiler = LangCompiler.builder
  .withCaching()
  .withCacheBackend(InMemoryCacheBackend.withMaxSize(500))
  .build
```

:::note
The compilation cache must remain in-memory because `CompilationOutput` contains Scala closures that cannot be serialized. Use `withCacheBackend` only to customize the in-memory backend (e.g., max size), not to point at a distributed store.
:::

## Serialization

Memcached stores byte arrays. The backend uses `CacheSerde[Any]` to serialize values before storing them:

| Value Type | Serialization Strategy |
|------------|----------------------|
| `CValue` (constellation types) | JSON via Circe (human-readable, uses existing codecs) |
| `java.io.Serializable` | Java ObjectOutputStream (binary) |
| Other | Throws `CacheSerdeException` |

You can provide a custom serde:

```scala
MemcachedCacheBackend.resource(config, serde = mySerde).use { backend =>
  // ...
}
```

See the [CacheBackend SPI guide](/docs/integrations/cache-backend#serialization-cacheserde) for details on implementing custom serdes.

## Statistics

The backend tracks client-side statistics:

```scala
val stats = backend.stats.unsafeRunSync()
// CacheStats(hits=150, misses=30, evictions=0, size=0, maxSize=None)
```

| Field | Description |
|-------|-------------|
| `hits` | Number of successful cache retrievals |
| `misses` | Number of cache misses (key absent or expired) |
| `evictions` | Always 0 (Memcached manages eviction server-side) |
| `size` | Always 0 (Memcached doesn't expose per-client item count) |
| `maxSize` | `None` |

For server-level statistics, query Memcached directly:

```bash
echo "stats" | nc localhost 11211
```

## Key Prefixing

Use `keyPrefix` to isolate cache entries when multiple applications share a Memcached cluster:

```scala
// Application A
MemcachedConfig(keyPrefix = "app-a")
// Key "module:abc" becomes "app-a:module:abc"

// Application B
MemcachedConfig(keyPrefix = "app-b")
// Key "module:abc" becomes "app-b:module:abc"
```

## Error Handling

- **Connection failure:** `MemcachedCacheBackend.create` returns a failed `IO` if it cannot resolve the server addresses. Use `.resource` with error handling around the `use` block.
- **Operation timeout:** Individual `get`/`set` operations respect `operationTimeout`. Timed-out operations surface as cache misses (for `get`) or as failed `IO` effects (for `set`).
- **Deserialization failure:** If stored bytes cannot be deserialized, the entry is treated as a miss and the corrupt key is deleted automatically.

## Limitations

- **No atomic getOrCompute:** Memcached doesn't support atomic check-and-set for arbitrary values. The default `getOrCompute` implementation uses separate `get` + `set` calls, which means a brief window where two callers may both compute the value.
- **Key length limit:** Memcached keys are limited to 250 bytes. The cache key generator produces SHA-256 hashes (43 characters) so this is not a concern for module caching.
- **Value size limit:** Memcached has a default 1MB value limit. Large module results may exceed this. Increase the server's `-I` flag if needed.

## Related

- [CacheBackend SPI Guide](/docs/integrations/cache-backend) — implement your own backend
- [`cache` option](/docs/language/options/cache) — enable caching in constellation-lang
- [`cache_backend` option](/docs/language/options/cache-backend) — select a named backend
