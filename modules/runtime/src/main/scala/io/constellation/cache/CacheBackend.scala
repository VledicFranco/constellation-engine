package io.constellation.cache

import cats.effect.IO

import scala.concurrent.duration.FiniteDuration

/** Pluggable cache backend interface.
  *
  * Implementations must be thread-safe and support concurrent access. All operations return IO to
  * allow for async backends (e.g., Redis).
  *
  * ==Usage==
  *
  * {{{
  * val cache: CacheBackend = InMemoryCacheBackend()
  *
  * // Store with TTL
  * cache.set("key", "value", ttl = 5.minutes)
  *
  * // Retrieve
  * val result = cache.get("key")  // IO[Option[CacheEntry[String]]]
  *
  * // Get statistics
  * val stats = cache.stats  // IO[CacheStats]
  * }}}
  */
trait CacheBackend {

  /** Get a value from the cache.
    *
    * @param key
    *   The cache key
    * @return
    *   Some(entry) if found and not expired, None otherwise
    */
  def get[A](key: String): IO[Option[CacheEntry[A]]]

  /** Store a value in the cache with TTL.
    *
    * @param key
    *   The cache key
    * @param value
    *   The value to store
    * @param ttl
    *   Time-to-live for the entry
    */
  def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit]

  /** Delete a specific key from the cache.
    *
    * @param key
    *   The cache key to delete
    * @return
    *   true if key existed and was deleted, false otherwise
    */
  def delete(key: String): IO[Boolean]

  /** Clear all entries from the cache. */
  def clear: IO[Unit]

  /** Get cache statistics. */
  def stats: IO[CacheStats]

  /** Check if a key exists and is not expired.
    *
    * @param key
    *   The cache key
    * @return
    *   true if key exists and is valid
    */
  def contains(key: String): IO[Boolean] =
    get[Any](key).map(_.isDefined)

  /** Get or compute a value.
    *
    * If the key exists and is not expired, return cached value. Otherwise, compute the value, store
    * it, and return it.
    *
    * @param key
    *   The cache key
    * @param ttl
    *   Time-to-live for newly computed values
    * @param compute
    *   The computation to run on cache miss
    * @return
    *   The cached or computed value
    */
  def getOrCompute[A](key: String, ttl: FiniteDuration)(compute: => IO[A]): IO[A] =
    get[A](key).flatMap {
      case Some(entry) => IO.pure(entry.value)
      case None =>
        compute.flatTap(value => set(key, value, ttl))
    }
}

/** A cached entry with metadata. */
final case class CacheEntry[A](
    value: A,
    createdAt: Long,
    expiresAt: Long
) {

  /** Check if this entry has expired. */
  def isExpired: Boolean = System.currentTimeMillis() > expiresAt

  /** Remaining TTL in milliseconds, or 0 if expired. */
  def remainingTtlMs: Long = math.max(0, expiresAt - System.currentTimeMillis())
}

object CacheEntry {
  def create[A](value: A, ttl: FiniteDuration): CacheEntry[A] = {
    val now = System.currentTimeMillis()
    CacheEntry(
      value = value,
      createdAt = now,
      expiresAt = now + ttl.toMillis
    )
  }
}

/** Cache statistics for monitoring and debugging.
  *
  * This is the canonical cache statistics type used across the entire codebase, including both
  * runtime module caching and compilation caching.
  */
final case class CacheStats(
    hits: Long,
    misses: Long,
    evictions: Long,
    size: Int,
    maxSize: Option[Int]
) {

  /** Cache hit ratio (0.0 to 1.0). */
  def hitRatio: Double = {
    val total = hits + misses
    if total == 0 then 0.0 else hits.toDouble / total
  }

  /** Alias for [[hitRatio]], used by compilation cache consumers. */
  def hitRate: Double = hitRatio

  /** Alias for [[size]], used by compilation cache consumers. */
  def entries: Int = size

  override def toString: String =
    f"CacheStats(hits=$hits, misses=$misses, hitRatio=${hitRatio * 100}%.1f%%, size=$size, evictions=$evictions)"
}

object CacheStats {
  val empty: CacheStats = CacheStats(0, 0, 0, 0, None)
}
