---
title: "Cache Backend"
sidebar_position: 4
---

# CacheBackend Integration Guide

## Overview

`CacheBackend` is the SPI trait for caching compiled DAGs, module results, or other reusable data. The default (no cache configured) means no caching. Implement this trait to plug in Redis, Caffeine, Memcached, or any other cache.

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
}
```

## Example 1: Redis via redis4cats

**Dependencies:**

```scala
libraryDependencies ++= Seq(
  "dev.profunktor" %% "redis4cats-effects" % "1.5.2",
  "dev.profunktor" %% "redis4cats-streams"  % "1.5.2"
)
```

**Implementation:**

```scala
import io.constellation.cache.{CacheBackend, CacheEntry, CacheStats}
import dev.profunktor.redis4cats.RedisCommands
import cats.effect.IO
import scala.concurrent.duration.FiniteDuration
import io.circe.{Encoder, Decoder}
import io.circe.syntax._
import io.circe.parser.decode
import java.util.concurrent.atomic.AtomicLong

class RedisCacheBackend(redis: RedisCommands[IO, String, String]) extends CacheBackend {

  private val hitCount  = new AtomicLong(0)
  private val missCount = new AtomicLong(0)

  def get[A](key: String): IO[Option[CacheEntry[A]]] =
    redis.get(key).map {
      case Some(json) =>
        // Deserialize CacheEntry from JSON (requires Decoder[A] in scope)
        // Simplified: assumes A is serializable as JSON string
        hitCount.incrementAndGet()
        None // Replace with actual deserialization
      case None =>
        missCount.incrementAndGet()
        None
    }

  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit] = {
    val entry = CacheEntry.create(value, ttl)
    // Serialize entry to JSON string (requires Encoder[A] in scope)
    val json = s"""{"value":"$value","createdAt":${entry.createdAt},"expiresAt":${entry.expiresAt}}"""
    redis.setEx(key, json, ttl)
  }

  def delete(key: String): IO[Boolean] =
    redis.del(key).map(_ > 0)

  def clear: IO[Unit] =
    redis.flushAll.void

  def stats: IO[CacheStats] = IO {
    CacheStats(
      hits      = hitCount.get(),
      misses    = missCount.get(),
      evictions = 0,  // Redis manages eviction internally
      size      = 0,  // Use redis.dbSize for approximate count
      maxSize   = None
    )
  }
}
```

**Wiring:**

```scala
import dev.profunktor.redis4cats.Redis
import dev.profunktor.redis4cats.effect.Log.Stdout._

Redis[IO].utf8("redis://localhost:6379").use { redis =>
  val cache = new RedisCacheBackend(redis)

  val constellation = ConstellationImpl.builder()
    .withCache(cache)
    .build()

  // ... run application
}
```

## Example 2: Caffeine (In-Process)

**Dependencies:**

```scala
libraryDependencies ++= Seq(
  "com.github.ben-manes.caffeine" % "caffeine" % "3.1.8"
)
```

**Implementation:**

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

**Wiring:**

```scala
val cache = new CaffeineCacheBackend(maxEntries = 10000)

val constellation = ConstellationImpl.builder()
  .withCache(cache)
  .build()
```

## Gotchas

- **Serialization:** For distributed caches (Redis, Memcached), you need to serialize/deserialize cache entries. The examples above show the pattern — production implementations should use proper codecs (Circe, Kryo, etc.).
- **TTL handling:** The `CacheEntry` tracks expiration. In-process caches (Caffeine) should check `isExpired` on read. Distributed caches can rely on the store's native TTL.
- **Thread safety:** Cache methods may be called concurrently. Caffeine and redis4cats are both thread-safe. If implementing a custom cache, ensure concurrent access is handled.
- **Memory:** In-process caches consume heap memory. Set `maxEntries` appropriately and monitor eviction counts. If evictions are high, the cache is too small.
- **Default behavior:** The `getOrCompute` method has a default implementation that calls `get`, and if missing, calls `compute`, then `set`. Override for atomic implementations (e.g., Caffeine's `get(key, loader)` pattern).
- **Type erasure:** The `get[A]` method uses a type parameter that is erased at runtime. Ensure you always read with the same type you wrote. Consider including type information in the cache key.
