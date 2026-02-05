# CacheBackend

> **Path**: `docs/extensibility/cache-backend.md`
> **Parent**: [extensibility/](./README.md)

Interface for pluggable cache backends. Implementations store module execution results and compiled DAGs with TTL-based expiration.

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

## Wiring

Register via `ConstellationBuilder`:

```scala
val cache = new CaffeineCacheBackend(maxEntries = 10000)

ConstellationImpl.builder()
  .withCache(cache)
  .build()
```

## Gotchas

- **Thread safety**: Cache methods may be called concurrently. Both `InMemoryCacheBackend` and Caffeine are thread-safe.
- **TTL handling**: `CacheEntry` tracks expiration. Check `isExpired` on read for in-process caches.
- **Memory**: In-process caches consume heap. Monitor eviction counts to size appropriately.
- **Type erasure**: `get[A]` erases at runtime. Implementations store via `asInstanceOf`.

## See Also

- [metrics-provider.md](./metrics-provider.md) - Monitor cache hit rates
- [stores.md](./stores.md) - Pipeline persistence (different from result caching)
