---
title: "Cache Backend"
sidebar_position: 4
description: "SPI guide for implementing custom cache backends to store module results and compiled DAGs."
---

# CacheBackend Integration Guide

## Overview

`CacheBackend` is the SPI trait for caching module execution results, compiled DAGs, or other reusable data. The default (no cache configured) means no caching. Implement this trait to plug in Redis, Caffeine, or any other cache store.

:::tip First-Party Module
For Memcached, a ready-made implementation is available as an [optional module](/docs/modules/cache-memcached) — no need to implement the SPI yourself.
:::

## Trait API

```scala
package io.constellation.cache

import cats.effect.IO
import scala.concurrent.duration.FiniteDuration

trait CacheBackend {
  /** Get a cached entry by key. Returns None if missing or expired. */
  def get[A](key: String): IO[Option[CacheEntry[A]]]

  /** Store a value with a TTL. */
  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit]

  /** Delete a specific key. Returns true if the key existed. */
  def delete(key: String): IO[Boolean]

  /** Remove all entries. */
  def clear: IO[Unit]

  /** Return cache statistics. */
  def stats: IO[CacheStats]

  /** Check if a key exists (default: delegates to get). */
  def contains(key: String): IO[Boolean]

  /** Get or compute and cache. */
  def getOrCompute[A](key: String, ttl: FiniteDuration)(compute: => IO[A]): IO[A]
}
```

### Supporting Types

```scala
final case class CacheEntry[A](
  value: A,
  createdAt: Long,    // epoch millis
  expiresAt: Long     // epoch millis
) {
  def isExpired: Boolean
  def remainingTtlMs: Long
}

final case class CacheStats(
  hits: Long,
  misses: Long,
  evictions: Long,
  size: Int,
  maxSize: Option[Int]
) {
  def hitRatio: Double  // 0.0 to 1.0
  def hitRate: Double   // alias for hitRatio
  def entries: Int      // alias for size
}
```

`CacheStats` is the unified statistics type used across the entire codebase — both for module execution caching and compilation caching. The `hitRate` and `entries` aliases exist so that all consumers (HTTP `/metrics`, LSP, health checks) can use either naming convention.

## Built-in Implementations

### InMemoryCacheBackend

Ships with `constellation-runtime`. No extra dependencies.

```scala
import io.constellation.cache.InMemoryCacheBackend

// No size limit
val cache = InMemoryCacheBackend()

// With LRU eviction at 1000 entries
val cache = InMemoryCacheBackend.withMaxSize(1000)
```

### MemcachedCacheBackend

Ships as the optional [`constellation-cache-memcached`](/docs/modules/cache-memcached) module. Requires a running Memcached server.

```scala
import io.constellation.cache.memcached.{MemcachedCacheBackend, MemcachedConfig}

MemcachedCacheBackend.resource(MemcachedConfig.single()).use { cache =>
  // use cache...
}
```

## Implementing a Custom Backend

There are two approaches depending on whether your backend stores values in-process or over the network.

### Approach 1: Implement CacheBackend Directly

Best for in-process caches (Caffeine, Guava, etc.) where values stay on the JVM heap.

```scala
import io.constellation.cache.{CacheBackend, CacheEntry, CacheStats}
import com.github.benmanes.caffeine.cache.{Caffeine, Cache}
import cats.effect.IO
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

class CaffeineCacheBackend(maxEntries: Int) extends CacheBackend {

  private val cache: Cache[String, CacheEntry[Any]] = Caffeine.newBuilder()
    .maximumSize(maxEntries)
    .recordStats()
    .build()

  def get[A](key: String): IO[Option[CacheEntry[A]]] = IO {
    Option(cache.getIfPresent(key))
      .filter(!_.isExpired)
      .map(_.asInstanceOf[CacheEntry[A]])
  }

  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit] = IO {
    val entry = CacheEntry.create(value, ttl)
    cache.put(key, entry.asInstanceOf[CacheEntry[Any]])
  }

  def delete(key: String): IO[Boolean] = IO {
    val existed = cache.getIfPresent(key) != null
    cache.invalidate(key)
    existed
  }

  def clear: IO[Unit] = IO(cache.invalidateAll())

  def stats: IO[CacheStats] = IO {
    val s = cache.stats()
    CacheStats(
      hits      = s.hitCount(),
      misses    = s.missCount(),
      evictions = s.evictionCount(),
      size      = cache.estimatedSize().toInt,
      maxSize   = Some(maxEntries)
    )
  }
}
```

### Approach 2: Extend DistributedCacheBackend

Best for network-backed caches (Redis, Memcached, DynamoDB, etc.) where values must be serialized to bytes for transport. `DistributedCacheBackend` handles serialization through `CacheSerde` and provides the full `CacheBackend` implementation — you only implement five byte-level methods.

```scala
import io.constellation.cache.{CacheSerde, CacheStats, DistributedCacheBackend}
import cats.effect.IO
import scala.concurrent.duration.FiniteDuration

class RedisCacheBackend(client: RedisClient, serde: CacheSerde[Any])
    extends DistributedCacheBackend(serde) {

  override protected def getBytes(key: String): IO[Option[(Array[Byte], Long, Long)]] =
    // Return Some((bytes, createdAt, expiresAt)) or None
    client.get(key).map(_.map(bytes => (bytes, 0L, Long.MaxValue)))

  override protected def setBytes(key: String, bytes: Array[Byte], ttl: FiniteDuration): IO[Unit] =
    client.setEx(key, bytes, ttl)

  override protected def deleteKey(key: String): IO[Boolean] =
    client.del(key).map(_ > 0)

  override protected def clearAll: IO[Unit] =
    client.flushDb

  override protected def getStats: IO[CacheStats] = IO {
    CacheStats(hits = 0, misses = 0, evictions = 0, size = 0, maxSize = None)
  }
}
```

`DistributedCacheBackend` takes care of:
- Calling `serde.serialize(value)` in `set` before passing bytes to `setBytes`
- Calling `serde.deserialize(bytes)` in `get` after reading from `getBytes`
- Handling deserialization failures as cache misses (corrupt entries are deleted automatically)

## Serialization (CacheSerde) {#serialization-cacheserde}

Distributed backends need to convert values to/from `Array[Byte]`. The `CacheSerde[A]` type class handles this:

```scala
trait CacheSerde[A] {
  def serialize(value: A): Array[Byte]
  def deserialize(bytes: Array[Byte]): A
}
```

### Built-in Serdes

| Serde | Strategy | Use Case |
|-------|----------|----------|
| `CacheSerde.cvalueSerde` | JSON via Circe | `CValue` constellation types |
| `CacheSerde.mapCValueSerde` | JSON via Circe | `Map[String, CValue]` |
| `CacheSerde.javaSerde[A]` | Java `ObjectOutputStream` | Any `java.io.Serializable` |
| `CacheSerde.anySerde` | JSON for `CValue`, Java fallback | Default for `DistributedCacheBackend` |

### Custom Serde

```scala
import io.constellation.cache.CacheSerde

given mySerde: CacheSerde[MyType] = new CacheSerde[MyType] {
  def serialize(value: MyType): Array[Byte] =
    value.toProto.toByteArray  // e.g., Protocol Buffers

  def deserialize(bytes: Array[Byte]): MyType =
    MyProto.parseFrom(bytes).toMyType
}
```

Pass your serde when constructing a distributed backend:

```scala
class MyBackend(serde: CacheSerde[Any]) extends DistributedCacheBackend(serde) {
  // ...
}
```

## Wiring

### As Default Cache

Use `ConstellationBuilder.withCache` to set the default backend for all module execution caching:

```scala
val cache = new CaffeineCacheBackend(maxEntries = 10000)

for {
  constellation <- ConstellationImpl.builder()
    .withCache(cache)
    .build()
  // ... run pipelines
} yield ()
```

### With ModuleOptionsExecutor

For programmatic use with the module options system:

```scala
import io.constellation.lang.compiler.ModuleOptionsExecutor

for {
  executor <- ModuleOptionsExecutor.createWithCacheBackend(
    cacheBackend = Some(myCacheBackend),
    scheduler = myScheduler
  )
  // ... use executor
} yield ()
```

### In CacheRegistry

Register multiple named backends so constellation-lang programs can select per-module:

```scala
import io.constellation.cache.{CacheRegistry, InMemoryCacheBackend}

for {
  registry <- CacheRegistry.withBackends(
    "memory" -> InMemoryCacheBackend(),
    "redis"  -> myRedisBackend
  )
  // In constellation-lang programs:
  // fast = QuickLookup(id) with cache: 30s, cache_backend: "memory"
  // slow = ExpensiveCall(id) with cache: 1h, cache_backend: "redis"
} yield ()
```

In constellation-lang:

```constellation
fast = QuickLookup(id) with cache: 30s, cache_backend: "memory"
slow = ExpensiveCall(id) with cache: 1h, cache_backend: "redis"
```

## Gotchas

- **Serialization:** For distributed caches, you need to serialize/deserialize cache entries. Extend `DistributedCacheBackend` and use `CacheSerde` instead of writing serialization logic by hand.
- **TTL handling:** The `CacheEntry` tracks expiration. In-process caches should check `isExpired` on read. Distributed caches can rely on the store's native TTL.
- **Thread safety:** Cache methods may be called concurrently from different fibers. Caffeine and redis4cats are both thread-safe. Ensure your implementation handles concurrent access.
- **Memory:** In-process caches consume heap memory. Set `maxEntries` appropriately and monitor eviction counts. If evictions are high, the cache is too small.
- **Default `getOrCompute`:** The default implementation calls `get`, then `compute` + `set` on miss. Override for atomic implementations (e.g., Caffeine's `get(key, loader)` pattern).
- **Type erasure:** `get[A]` uses a type parameter erased at runtime. `InMemoryCacheBackend` stores `Any` via `asInstanceOf`. Distributed backends serialize through `CacheSerde[Any]`, which handles type routing.
