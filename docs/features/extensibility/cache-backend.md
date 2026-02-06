# CacheBackend

> **Path**: `docs/features/extensibility/cache-backend.md`
> **Parent**: [extensibility/](./README.md)

Interface for pluggable cache backends. Implementations store module execution results and compiled DAGs with TTL-based expiration.

## Components Involved

| Component | Role | File Path |
|-----------|------|-----------|
| `CacheBackend` | SPI trait definition | `modules/runtime/src/main/scala/io/constellation/cache/CacheBackend.scala` |
| `CacheEntry` | Cached value with metadata | `modules/runtime/src/main/scala/io/constellation/cache/CacheBackend.scala` |
| `CacheStats` | Statistics for monitoring | `modules/runtime/src/main/scala/io/constellation/cache/CacheBackend.scala` |
| `InMemoryCacheBackend` | Default in-process implementation | `modules/runtime/src/main/scala/io/constellation/cache/InMemoryCacheBackend.scala` |
| `CacheRegistry` | Multi-backend management | `modules/runtime/src/main/scala/io/constellation/cache/CacheRegistry.scala` |
| `CacheKeyGenerator` | Key generation utilities | `modules/runtime/src/main/scala/io/constellation/cache/CacheKeyGenerator.scala` |
| `CacheSerde` | Serialization for distributed caches | `modules/runtime/src/main/scala/io/constellation/cache/CacheSerde.scala` |
| `DistributedCacheBackend` | Base for remote caches | `modules/runtime/src/main/scala/io/constellation/cache/DistributedCacheBackend.scala` |
| `ConstellationBackends` | Backend bundle configuration | `modules/runtime/src/main/scala/io/constellation/spi/ConstellationBackends.scala` |

## Trait API

```scala
package io.constellation.cache

import cats.effect.IO
import scala.concurrent.duration.FiniteDuration

trait CacheBackend {
  /** Get a value from the cache. Returns None if missing or expired. */
  def get[A](key: String): IO[Option[CacheEntry[A]]]

  /** Store a value with TTL. */
  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit]

  /** Delete a specific key. Returns true if key existed. */
  def delete(key: String): IO[Boolean]

  /** Remove all entries. */
  def clear: IO[Unit]

  /** Return cache statistics. */
  def stats: IO[CacheStats]

  /** Check if a key exists (default: delegates to get). */
  def contains(key: String): IO[Boolean]

  /** Get or compute and cache on miss. */
  def getOrCompute[A](key: String, ttl: FiniteDuration)(compute: => IO[A]): IO[A]
}
```

## Supporting Types

```scala
final case class CacheEntry[A](
  value: A,
  createdAt: Long,    // epoch millis
  expiresAt: Long     // epoch millis
) {
  def isExpired: Boolean
  def remainingTtlMs: Long
}

object CacheEntry {
  def create[A](value: A, ttl: FiniteDuration): CacheEntry[A]
}

final case class CacheStats(
  hits: Long,
  misses: Long,
  evictions: Long,
  size: Int,
  maxSize: Option[Int]
) {
  def hitRatio: Double   // 0.0 to 1.0
  def hitRate: Double    // alias for hitRatio
  def entries: Int       // alias for size
}

object CacheStats {
  val empty: CacheStats
}
```

## Method Reference

| Method | Description |
|--------|-------------|
| `get[A](key)` | Retrieve entry if present and not expired |
| `set[A](key, value, ttl)` | Store value with time-to-live |
| `delete(key)` | Remove single key, returns true if existed |
| `clear` | Remove all entries |
| `stats` | Return hit/miss/eviction statistics |
| `contains(key)` | Check existence without retrieving value |
| `getOrCompute[A](key, ttl)(compute)` | Return cached value or compute and cache |

## Built-in Implementation

`InMemoryCacheBackend` ships with `constellation-runtime`:

```scala
import io.constellation.cache.InMemoryCacheBackend

// No size limit
val cache = InMemoryCacheBackend()

// With LRU eviction at 1000 entries
val cache = InMemoryCacheBackend.withMaxSize(1000)
```

### Features

- **Thread-safe**: Uses `ConcurrentHashMap` for concurrent access
- **TTL-based expiration**: Lazy cleanup on read, periodic cleanup on stats
- **LRU eviction**: Optional max size with least-recently-used eviction
- **Statistics tracking**: Hits, misses, evictions for monitoring

## Example: Caffeine Backend

In-process cache using Caffeine with LRU eviction:

```scala
import io.constellation.cache.{CacheBackend, CacheEntry, CacheStats}
import com.github.benmanes.caffeine.cache.{Caffeine, Cache}
import cats.effect.IO
import scala.concurrent.duration.FiniteDuration

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

## Example: Redis Backend

Distributed cache using Redis for multi-instance deployments:

```scala
import io.constellation.cache.{CacheBackend, CacheEntry, CacheStats}
import io.lettuce.core.api.async.RedisAsyncCommands
import cats.effect.IO
import scala.concurrent.duration.FiniteDuration
import io.circe.{Encoder, Decoder}
import io.circe.syntax._
import io.circe.parser

class RedisCacheBackend[F[_]](
  commands: RedisAsyncCommands[String, String],
  keyPrefix: String = "constellation:"
)(implicit encoder: Encoder[Any], decoder: Decoder[Any]) extends CacheBackend {

  private def prefixedKey(key: String): String = s"$keyPrefix$key"

  def get[A](key: String): IO[Option[CacheEntry[A]]] = IO.async_ { cb =>
    val future = commands.get(prefixedKey(key))
    future.whenComplete { (value, error) =>
      if (error != null) cb(Left(error))
      else cb(Right(Option(value).flatMap { json =>
        parser.decode[CacheEntry[A]](json).toOption
      }))
    }
  }

  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit] = IO.async_ { cb =>
    val entry = CacheEntry.create(value, ttl)
    val json = entry.asJson.noSpaces
    val future = commands.setex(prefixedKey(key), ttl.toSeconds, json)
    future.whenComplete { (_, error) =>
      if (error != null) cb(Left(error))
      else cb(Right(()))
    }
  }

  def delete(key: String): IO[Boolean] = IO.async_ { cb =>
    val future = commands.del(prefixedKey(key))
    future.whenComplete { (count, error) =>
      if (error != null) cb(Left(error))
      else cb(Right(count > 0))
    }
  }

  def clear: IO[Unit] = IO.async_ { cb =>
    // WARNING: Use with caution - deletes all keys with prefix
    val future = commands.keys(s"$keyPrefix*")
    future.whenComplete { (keys, error) =>
      if (error != null) cb(Left(error))
      else if (keys.isEmpty) cb(Right(()))
      else {
        val delFuture = commands.del(keys.toArray(new Array[String](0)): _*)
        delFuture.whenComplete { (_, delError) =>
          if (delError != null) cb(Left(delError))
          else cb(Right(()))
        }
      }
    }
  }

  def stats: IO[CacheStats] = IO.async_ { cb =>
    // Redis doesn't track stats per-prefix, return minimal info
    val future = commands.dbsize()
    future.whenComplete { (size, error) =>
      if (error != null) cb(Left(error))
      else cb(Right(CacheStats(
        hits = 0,      // Redis doesn't expose per-prefix stats
        misses = 0,
        evictions = 0,
        size = size.toInt,
        maxSize = None
      )))
    }
  }
}
```

## Wiring

Register via `ConstellationImpl.builder()`:

```scala
import io.constellation.ConstellationImpl
import io.constellation.cache.InMemoryCacheBackend

// In-memory cache
val cache = InMemoryCacheBackend.withMaxSize(10000)

val constellation = ConstellationImpl.builder()
  .withCache(cache)
  .build()
```

Or via `ConstellationBackends`:

```scala
import io.constellation.spi.ConstellationBackends

val backends = ConstellationBackends(
  cache = Some(cache),
  metrics = myMetrics,
  listener = myListener
)

val constellation = ConstellationImpl.builder()
  .withBackends(backends)
  .build()
```

## Cache Key Design

Keys should be deterministic and collision-free:

```scala
// Module result caching
val key = s"module:$moduleName:${inputHash}"

// Compiled DAG caching
val key = s"dag:${structuralHash}"

// Use CacheKeyGenerator for consistent hashing
import io.constellation.cache.CacheKeyGenerator

val key = CacheKeyGenerator.forModuleResult(moduleName, inputs)
val key = CacheKeyGenerator.forCompiledDag(source, registryHash)
```

## Monitoring Cache Effectiveness

Use the `/metrics` endpoint to track cache performance:

```bash
curl http://localhost:8080/metrics | jq .cache
```

Expected output:
```json
{
  "hits": 1523,
  "misses": 234,
  "hitRatio": 0.867,
  "size": 456,
  "evictions": 12
}
```

**Target metrics:**
- `hitRatio > 0.8` for module result cache
- `hitRatio > 0.95` for compiled DAG cache (rarely changes)

## Gotchas

| Issue | Mitigation |
|-------|------------|
| **Thread safety** | Cache methods may be called concurrently. Both `InMemoryCacheBackend` and Caffeine are thread-safe. |
| **TTL handling** | `CacheEntry` tracks expiration. Check `isExpired` on read for in-process caches. |
| **Memory pressure** | In-process caches consume heap. Monitor eviction counts and size appropriately. |
| **Type erasure** | `get[A]` erases at runtime. Implementations store via `asInstanceOf`. Callers must ensure type consistency. |
| **Serialization** | Distributed caches require serialization. Use `CacheSerde` or Circe codecs. |
| **Clock skew** | Distributed caches with TTL may behave unexpectedly with clock skew. Use server-side TTL (Redis `SETEX`). |

## See Also

- [PHILOSOPHY.md](./PHILOSOPHY.md) - Why SPI over inheritance
- [ETHOS.md](./ETHOS.md) - Constraints for modifying SPIs
- [metrics-provider.md](./metrics-provider.md) - Monitor cache hit rates
- [execution-storage.md](./execution-storage.md) - Pipeline persistence (different from result caching)
