---
title: "Migration: v0.4.0"
sidebar_position: 3
description: "Upgrading from v0.3.x to v0.4.0"
---

# Migration Guide: v0.4.0

This guide covers upgrading from v0.3.x to v0.4.0. The release focuses on distributed caching infrastructure with minimal breaking changes.

:::tip Before You Start
Back up your `build.sbt` and take note of any custom `CacheStats` imports. The only breaking change is a moved type, which is easy to fix with a find-and-replace.
:::

## Summary

v0.4.0 introduces:

- **Pluggable cache backends** — Memcached support out of the box, extensible for Redis/Caffeine
- **CacheSerde abstraction** — Serialization layer for distributed cache backends
- **DistributedCacheBackend base class** — Simplifies implementing network-backed caches
- **Unified CacheStats** — Single statistics type across compilation and runtime caching
- **LangCompilerBuilder.withCacheBackend** — Custom cache backends for compilation results

## Breaking Changes

:::danger Import Path Changed
The `CacheStats` type has moved to a new package. If you import it directly, update the import path or your code will fail to compile.
:::

### CacheStats Type Location Changed

**Impact:** Low. Affects code that imports `CacheStats` by full path.

The `CacheStats` type has moved from `io.constellation.lang.CacheStats` to `io.constellation.cache.CacheStats`.

```scala
// Before (v0.3.x)
import io.constellation.lang.CacheStats

// After (v0.4.0)
import io.constellation.cache.CacheStats
```

**Backward compatibility:** The old type is removed, but the new `CacheStats` includes `.hitRate` and `.entries` aliases that match the old API. If you only use these fields, your code works without changes after updating the import.

```scala
val stats: CacheStats = cache.stats.unsafeRunSync()
stats.hitRate   // Works (alias for hitRatio)
stats.entries   // Works (alias for size)
stats.hitRatio  // Also works (canonical name)
stats.size      // Also works (canonical name)
```

### CompilationCache Internal Changes

**Impact:** None for external users. The `CompilationCache` internal implementation changed from a local `CacheEntry` case class to using the runtime `CacheEntry[A]` through the `CacheBackend` interface. This is an internal refactoring with no API changes.

## New Features

### Pluggable Cache Backends

v0.4.0 introduces a cache backend SPI that allows plugging in distributed caches for both compilation results and module execution caching.

#### Using the Built-in Memcached Backend

Add the optional dependency:

```scala
// build.sbt
libraryDependencies += "io.constellation" %% "constellation-cache-memcached" % "0.4.0"
```

Configure and use:

```scala
import io.constellation.cache.memcached.{MemcachedCacheBackend, MemcachedConfig}

// Single server
MemcachedCacheBackend.resource(MemcachedConfig.single()).use { cache =>
  // Use for compilation caching
  val compiler = LangCompilerBuilder()
    .withCacheBackend(cache)
    .build()

  // Use for runtime caching
  val constellation = ConstellationImpl.builder()
    .withBackends(ConstellationBackends(cache = Some(cache)))
    .build()

  // ... run application
}

// Cluster configuration
MemcachedCacheBackend.resource(MemcachedConfig.cluster(
  servers = "mc1:11211,mc2:11211,mc3:11211"
)).use { cache =>
  // ...
}
```

See the [Cache Backend Integration Guide](/docs/integrations/cache-backend) for Redis and Caffeine implementations.

#### Implementing a Custom Backend

For in-process caches (Caffeine, Guava):

```scala
import io.constellation.cache.{CacheBackend, CacheEntry, CacheStats}

class CaffeineCacheBackend(underlying: Cache[String, CacheEntry[Any]]) extends CacheBackend {
  def get[A](key: String): IO[Option[CacheEntry[A]]] = IO {
    Option(underlying.getIfPresent(key))
      .filter(!_.isExpired)
      .map(_.asInstanceOf[CacheEntry[A]])
  }

  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit] = IO {
    underlying.put(key, CacheEntry.create(value, ttl).asInstanceOf[CacheEntry[Any]])
  }

  // ... implement remaining methods
}
```

For network-backed caches (Redis, DynamoDB), extend `DistributedCacheBackend`:

```scala
import io.constellation.cache.{CacheSerde, DistributedCacheBackend}

class RedisCacheBackend(redis: RedisCommands[IO, String, Array[Byte]], serde: CacheSerde[Any])
    extends DistributedCacheBackend(serde) {

  override protected def getBytes(key: String): IO[Option[(Array[Byte], Long, Long)]] =
    redis.get(key).map(_.map(bytes => (bytes, 0L, Long.MaxValue)))

  override protected def setBytes(key: String, bytes: Array[Byte], ttl: FiniteDuration): IO[Unit] =
    redis.setEx(key, ttl, bytes)

  override protected def deleteKey(key: String): IO[Boolean] =
    redis.del(key).map(_ > 0)

  override protected def clearAll: IO[Unit] =
    redis.flushDb

  override protected def getStats: IO[CacheStats] =
    IO.pure(CacheStats.empty)
}
```

### CacheSerde for Serialization

Distributed cache backends need to serialize values to bytes. The `CacheSerde[A]` type class handles this:

```scala
trait CacheSerde[A] {
  def serialize(value: A): Array[Byte]
  def deserialize(bytes: Array[Byte]): A
}
```

Built-in implementations:

| Serde | Use Case |
|-------|----------|
| `CacheSerde.cvalueSerde` | Constellation `CValue` types (JSON) |
| `CacheSerde.mapCValueSerde` | `Map[String, CValue]` inputs/outputs |
| `CacheSerde.javaSerde[A]` | Any `java.io.Serializable` |
| `CacheSerde.anySerde` | Default: JSON for CValue, Java fallback |

Custom serde example:

```scala
import io.constellation.cache.CacheSerde

given mySerde: CacheSerde[MyType] = new CacheSerde[MyType] {
  def serialize(value: MyType): Array[Byte] = value.toProto.toByteArray
  def deserialize(bytes: Array[Byte]): MyType = MyProto.parseFrom(bytes).toMyType
}
```

### LangCompilerBuilder.withCacheBackend

Configure custom cache backends for compilation results:

```scala
import io.constellation.lang.LangCompilerBuilder

// Before (v0.3.x) - only in-memory caching
val compiler = LangCompilerBuilder()
  .withCaching(maxEntries = 1000, ttl = 1.hour)
  .build()

// After (v0.4.0) - pluggable backend
val compiler = LangCompilerBuilder()
  .withCacheBackend(myRedisCache)
  .build()
```

This enables sharing compilation caches across multiple Constellation instances.

### ModuleOptionsExecutor.createWithCacheBackend

Wire a cache backend into the module execution layer:

```scala
import io.constellation.lang.compiler.ModuleOptionsExecutor

for {
  executor <- ModuleOptionsExecutor.createWithCacheBackend(
    cacheBackend = Some(myDistributedCache),
    scheduler = myScheduler
  )
  // Module results cached in distributed backend
} yield ()
```

## Upgrade Steps

### Step 1: Update Dependencies

```scala
// build.sbt
libraryDependencies ++= Seq(
  "io.constellation" %% "constellation-core" % "0.4.0",
  "io.constellation" %% "constellation-runtime" % "0.4.0",
  "io.constellation" %% "constellation-lang-compiler" % "0.4.0",
  // ... other modules
)
```

### Step 2: Fix CacheStats Import (If Applicable)

If you import `CacheStats` directly:

```scala
// Change this:
import io.constellation.lang.CacheStats

// To this:
import io.constellation.cache.CacheStats
```

### Step 3: (Optional) Add Distributed Caching

If you want to share caches across instances:

```scala
// Add optional dependency
libraryDependencies += "io.constellation" %% "constellation-cache-memcached" % "0.4.0"

// Configure in your application
import io.constellation.cache.memcached.{MemcachedCacheBackend, MemcachedConfig}

MemcachedCacheBackend.resource(MemcachedConfig.fromEnv()).use { cache =>
  val compiler = LangCompilerBuilder()
    .withCacheBackend(cache)
    .build()

  val constellation = ConstellationImpl.builder()
    .withBackends(ConstellationBackends(cache = Some(cache)))
    .build()

  // ... run application
}
```

### Step 4: Verify Tests Pass

```bash
make test
```

### Step 5: Monitor Cache Metrics

After deploying v0.4.0 with distributed caching:

```bash
# Check cache hit rate
curl http://localhost:8080/metrics | jq '.cache'

# Expected output:
{
  "hits": 1234,
  "misses": 56,
  "evictions": 0,
  "size": 89,
  "hitRate": 0.956,
  "entries": 89
}
```

## Rollback Procedure

:::note Rollback Is Safe
Rolling back to v0.3.x only requires reverting dependency versions and removing v0.4.0-specific API calls. No data migration is needed.
:::

If you need to rollback to v0.3.x:

### Step 1: Revert Dependencies

```scala
// build.sbt
libraryDependencies ++= Seq(
  "io.constellation" %% "constellation-core" % "0.3.0",
  // ... other modules at 0.3.0
)
```

### Step 2: Remove v0.4.0 Features

Remove any usage of new v0.4.0 APIs:

```scala
// Remove distributed cache backend configuration
// val compiler = LangCompilerBuilder().withCacheBackend(cache).build()

// Revert to in-memory caching
val compiler = LangCompilerBuilder()
  .withCaching(maxEntries = 1000, ttl = 1.hour)
  .build()
```

### Step 3: Fix CacheStats Import

```scala
// Revert to:
import io.constellation.lang.CacheStats
```

### Step 4: Rebuild and Deploy

```bash
make clean
make compile
make test
make assembly
```

## API Compatibility Matrix

| API | v0.3.x | v0.4.0 | Notes |
|-----|--------|--------|-------|
| `CacheBackend` trait | Available | Available | No changes |
| `InMemoryCacheBackend` | Available | Available | No changes |
| `CachingLangCompiler` | Available | Available | No changes |
| `CacheStats` | `io.constellation.lang` | `io.constellation.cache` | **Moved** |
| `CacheSerde` | N/A | **New** | Serialization abstraction |
| `DistributedCacheBackend` | N/A | **New** | Base for network caches |
| `MemcachedCacheBackend` | N/A | **New** | Optional module |
| `LangCompilerBuilder.withCacheBackend` | N/A | **New** | Custom compilation cache |
| `ModuleOptionsExecutor.createWithCacheBackend` | N/A | **New** | Custom execution cache |

## Related Documentation

- [Cache Backend Integration Guide](/docs/integrations/cache-backend) — Implementation examples for Redis, Caffeine
- [Memcached Module](/docs/modules/cache-memcached) — First-party Memcached backend
- [Clustering Guide](/docs/operations/clustering) — Shared cache configuration for clusters
- [Performance Tuning](/docs/operations/performance-tuning) — Cache sizing and monitoring