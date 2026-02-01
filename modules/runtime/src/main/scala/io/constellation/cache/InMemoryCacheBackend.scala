package io.constellation.cache

import cats.effect.{IO, Ref}

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

/** In-memory cache backend using ConcurrentHashMap.
  *
  * Suitable for single-instance deployments and development.
  * For distributed systems, use Redis or Memcached backends.
  *
  * ==Features==
  *
  * - Thread-safe concurrent access
  * - TTL-based expiration (lazy cleanup)
  * - Optional max size with LRU eviction
  * - Statistics tracking
  *
  * ==Usage==
  *
  * {{{
  * // Basic usage
  * val cache = InMemoryCacheBackend()
  *
  * // With max size (LRU eviction)
  * val cache = InMemoryCacheBackend(maxSize = Some(1000))
  *
  * // Store and retrieve
  * cache.set("key", myValue, ttl = 5.minutes).unsafeRunSync()
  * val result = cache.get[MyType]("key").unsafeRunSync()
  * }}}
  */
class InMemoryCacheBackend(
    maxSize: Option[Int] = None
) extends CacheBackend {

  // Internal storage with Any to support generic types
  private val storage = new ConcurrentHashMap[String, CacheEntry[Any]]()

  // Statistics counters
  private val hitCount = new AtomicLong(0)
  private val missCount = new AtomicLong(0)
  private val evictionCount = new AtomicLong(0)

  // Access timestamps for LRU eviction
  private val accessTimes = new ConcurrentHashMap[String, Long]()

  // Cached stats with TTL (5 seconds) to avoid O(n) cleanup on every stats() call
  @volatile private var cachedStats: Option[(CacheStats, Long)] = None
  private val statsCacheTTL: Long = 5000 // milliseconds

  override def get[A](key: String): IO[Option[CacheEntry[A]]] = IO {
    Option(storage.get(key)) match {
      case Some(entry) if !entry.isExpired =>
        hitCount.incrementAndGet()
        accessTimes.put(key, System.currentTimeMillis())
        Some(entry.asInstanceOf[CacheEntry[A]])

      case Some(_) =>
        // Expired - remove and count as miss
        storage.remove(key)
        accessTimes.remove(key)
        missCount.incrementAndGet()
        None

      case None =>
        missCount.incrementAndGet()
        None
    }
  }

  override def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit] = IO {
    // Check max size and evict if necessary
    // Use synchronized block to prevent race conditions between size check and eviction
    maxSize.foreach { max =>
      this.synchronized {
        while (storage.size() >= max) {
          evictLRU()
        }
      }
    }

    val entry = CacheEntry.create(value, ttl)
    storage.put(key, entry.asInstanceOf[CacheEntry[Any]])
    accessTimes.put(key, System.currentTimeMillis())
  }

  override def delete(key: String): IO[Boolean] = IO {
    val existed = storage.remove(key) != null
    accessTimes.remove(key)
    existed
  }

  override def clear: IO[Unit] = IO {
    storage.clear()
    accessTimes.clear()
    cachedStats = None // Invalidate cached stats
    // Don't reset stats on clear - they track lifetime metrics
  }

  override def stats: IO[CacheStats] = IO {
    val now = System.currentTimeMillis()

    // Check if cached stats are still fresh
    cachedStats match {
      case Some((stats, timestamp)) if (now - timestamp) < statsCacheTTL =>
        // Return cached stats (O(1), no cleanup needed)
        stats

      case _ =>
        // Cache expired or missing - recompute
        // Clean up expired entries for accurate size
        cleanupExpired()

        val newStats = CacheStats(
          hits = hitCount.get(),
          misses = missCount.get(),
          evictions = evictionCount.get(),
          size = storage.size(),
          maxSize = maxSize
        )

        // Cache for future calls
        cachedStats = Some((newStats, now))
        newStats
    }
  }

  /** Evict the least recently used entry.
    * Must be called from within a synchronized block to prevent races.
    */
  private def evictLRU(): Unit = {
    // Convert to list first to avoid ConcurrentModificationException
    val oldest = accessTimes.entrySet().asScala.toList
      .minByOption(_.getValue)
      .map(_.getKey)

    oldest.foreach { key =>
      storage.remove(key)
      accessTimes.remove(key)
      evictionCount.incrementAndGet()
    }
  }

  /** Remove all expired entries. */
  private def cleanupExpired(): Unit = {
    val now = System.currentTimeMillis()
    // Convert to list first to avoid ConcurrentModificationException
    storage.entrySet().asScala.toList.foreach { entry =>
      if (entry.getValue.expiresAt < now) {
        storage.remove(entry.getKey)
        accessTimes.remove(entry.getKey)
      }
    }
  }

  /** Force cleanup of all expired entries.
    * Useful for testing or manual maintenance.
    */
  def forceCleanup: IO[Int] = IO {
    val sizeBefore = storage.size()
    cleanupExpired()
    cachedStats = None // Invalidate cached stats after cleanup
    sizeBefore - storage.size()
  }

  /** Reset all statistics counters.
    * Useful for testing or metrics reset.
    */
  def resetStats: IO[Unit] = IO {
    hitCount.set(0)
    missCount.set(0)
    evictionCount.set(0)
  }
}

object InMemoryCacheBackend {

  /** Create a new in-memory cache with no size limit. */
  def apply(): InMemoryCacheBackend = new InMemoryCacheBackend()

  /** Create a new in-memory cache with max size (LRU eviction). */
  def withMaxSize(maxSize: Int): InMemoryCacheBackend =
    new InMemoryCacheBackend(maxSize = Some(maxSize))

  /** Default global cache instance.
    * Use with caution - prefer dependency injection.
    */
  lazy val default: InMemoryCacheBackend = apply()
}
