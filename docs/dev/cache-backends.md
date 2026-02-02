# Cache Backends Architecture

This document explains the cache SPI (Service Provider Interface) in Constellation Engine, how to implement custom backends, and how the caching layers interact.

## Overview

Constellation Engine has two caching layers:

1. **Module execution cache** — Caches module results during DAG execution. Managed by `CacheRegistry` and `ModuleOptionsExecutor`. Configurable via `ConstellationBuilder.withCache()`.

2. **Compilation cache** — Caches parsed/compiled `CompilationOutput` to avoid redundant compilation. Managed by `CompilationCache` and `CachingLangCompiler`. Must remain in-memory (contains closures).

Both layers use the unified `CacheBackend` interface and `CacheStats` type.

## CacheBackend Interface

```scala
trait CacheBackend {
  def get[A](key: String): IO[Option[CacheEntry[A]]]
  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit]
  def delete(key: String): IO[Boolean]
  def clear: IO[Unit]
  def stats: IO[CacheStats]
  def contains(key: String): IO[Boolean]
  def getOrCompute[A](key: String, ttl: FiniteDuration)(compute: => IO[A]): IO[A]
}
```

All operations return `IO` to support async backends. Implementations must be thread-safe.

## Built-in Backends

### InMemoryCacheBackend

Located in `modules/runtime/.../cache/InMemoryCacheBackend.scala`.

- Thread-safe via `ConcurrentHashMap`
- Optional max size with LRU eviction
- TTL-based expiration (lazy cleanup)
- 5-second stats cache for performance

```scala
val cache = InMemoryCacheBackend()                // No size limit
val cache = InMemoryCacheBackend.withMaxSize(1000) // LRU eviction at 1000 entries
```

### MemcachedCacheBackend

Located in `modules/cache-memcached/`. Optional module — add as a dependency only if needed.

```scala
libraryDependencies += "io.github.vledicfranco" %% "constellation-cache-memcached" % version
```

Usage:

```scala
MemcachedCacheBackend.resource(MemcachedConfig.single()).use { backend =>
  ConstellationImpl.builder()
    .withCache(backend)
    .build()
    .flatMap { constellation => ... }
}
```

## Implementing a Custom Backend

### For In-Memory Backends

Extend `CacheBackend` directly:

```scala
class MyCacheBackend extends CacheBackend {
  def get[A](key: String): IO[Option[CacheEntry[A]]] = ...
  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit] = ...
  def delete(key: String): IO[Boolean] = ...
  def clear: IO[Unit] = ...
  def stats: IO[CacheStats] = ...
}
```

### For Distributed Backends (Redis, Memcached, etc.)

Extend `DistributedCacheBackend` which handles serialization:

```scala
class RedisCacheBackend(client: RedisClient, serde: CacheSerde[Any])
  extends DistributedCacheBackend(serde) {

  protected def getBytes(key: String): IO[Option[(Array[Byte], Long, Long)]] = ...
  protected def setBytes(key: String, bytes: Array[Byte], ttl: FiniteDuration): IO[Unit] = ...
  protected def deleteKey(key: String): IO[Boolean] = ...
  protected def clearAll: IO[Unit] = ...
  protected def getStats: IO[CacheStats] = ...
}
```

`DistributedCacheBackend` provides the `CacheBackend` implementation that serializes/deserializes values through `CacheSerde[Any]`.

## Serialization (CacheSerde)

The `CacheSerde[A]` type class handles value serialization for distributed backends:

```scala
trait CacheSerde[A] {
  def serialize(value: A): Array[Byte]
  def deserialize(bytes: Array[Byte]): A
}
```

Built-in instances:

| Serde | Strategy | Use Case |
|-------|----------|----------|
| `CacheSerde.cvalueSerde` | JSON (Circe) | `CValue` types |
| `CacheSerde.mapCValueSerde` | JSON (Circe) | `Map[String, CValue]` |
| `CacheSerde.javaSerde[A]` | Java ObjectStream | Any `Serializable` |
| `CacheSerde.anySerde` | JSON for CValue, Java fallback | Default for `DistributedCacheBackend` |

## CacheStats

A single unified `CacheStats` type is used across the entire codebase:

```scala
final case class CacheStats(
    hits: Long,
    misses: Long,
    evictions: Long,
    size: Int,
    maxSize: Option[Int]
) {
  def hitRatio: Double = ...  // hits / (hits + misses)
  def hitRate: Double = ...   // alias for hitRatio
  def entries: Int = ...      // alias for size
}
```

The `hitRate` and `entries` aliases exist for backward compatibility with code that previously used the compilation-specific `CacheStats` type.

## CacheRegistry

`CacheRegistry` manages multiple named backends:

```scala
for {
  registry <- CacheRegistry.withBackends(
    "default" -> InMemoryCacheBackend(),
    "redis"   -> redisCacheBackend
  )
  _ = registry.default       // Returns the "default" backend
  _ = registry.get("redis")  // Returns the "redis" backend
  allStats <- registry.allStats      // Stats from all backends
} yield ()
```

## Wiring ConstellationBackends.cache

When a user configures `.withCache(backend)` on `ConstellationBuilder`, the backend is stored in `ConstellationBackends.cache`. To flow this into `ModuleOptionsExecutor`:

```scala
ModuleOptionsExecutor.createWithCacheBackend(
  cacheBackend = backends.cache,
  scheduler = scheduler
)
```

This registers the provided backend as the `"default"` in the `CacheRegistry`.

## Module Dependency Graph

```
core
  ↓
runtime (CacheBackend, CacheSerde, DistributedCacheBackend, CacheRegistry)
  ↓          ↓
  ↓     cache-memcached (MemcachedCacheBackend) ← optional module
  ↓
lang-compiler (CompilationCache delegates to CacheBackend)
```
