package io.constellation.lang

import cats.effect.{IO, Ref}
import io.constellation.lang.compiler.CompileResult

import scala.concurrent.duration._

/** Statistics about cache performance */
case class CacheStats(
    hits: Long,
    misses: Long,
    evictions: Long,
    entries: Int
) {
  def hitRate: Double = if (hits + misses == 0) 0.0 else hits.toDouble / (hits + misses)
}

/** A single entry in the compilation cache */
case class CacheEntry(
    sourceHash: Int,
    registryHash: Int,
    result: CompileResult,
    createdAt: Long,
    lastAccessed: Long
)

/** Thread-safe cache for compilation results.
  *
  * Provides LRU eviction when the cache reaches maximum size and TTL-based
  * expiration to prevent stale results. Uses cats-effect Ref for thread safety.
  *
  * @param cache the thread-safe reference to cache entries
  * @param statsRef the thread-safe reference to cache statistics
  * @param config cache configuration
  */
class CompilationCache private (
    cache: Ref[IO, Map[String, CacheEntry]],
    statsRef: Ref[IO, CacheStats],
    config: CompilationCache.Config
) {

  /** Look up a cached compilation result.
    *
    * Returns Some(result) if:
    * - Entry exists for the dagName
    * - Source hash matches
    * - Registry hash matches
    * - Entry has not expired (TTL)
    *
    * Updates lastAccessed time on cache hit for LRU tracking.
    */
  def get(dagName: String, sourceHash: Int, registryHash: Int): IO[Option[CompileResult]] = {
    val now = System.currentTimeMillis()
    cache.modify { entries =>
      entries.get(dagName) match {
        case Some(entry) if isValid(entry, sourceHash, registryHash, now) =>
          // Cache hit - update lastAccessed and return result
          val updated = entries.updated(dagName, entry.copy(lastAccessed = now))
          (updated, Some(entry.result))
        case Some(_) =>
          // Invalid entry (hash mismatch or expired) - remove it
          (entries - dagName, None)
        case None =>
          // Cache miss
          (entries, None)
      }
    }.flatTap {
      case Some(_) => statsRef.update(s => s.copy(hits = s.hits + 1))
      case None    => statsRef.update(s => s.copy(misses = s.misses + 1))
    }
  }

  /** Store a compilation result in the cache.
    *
    * If the cache is at maximum capacity, evicts the least recently used entry.
    */
  def put(dagName: String, sourceHash: Int, registryHash: Int, result: CompileResult): IO[Unit] = {
    val now   = System.currentTimeMillis()
    val entry = CacheEntry(sourceHash, registryHash, result, now, now)

    cache.modify { entries =>
      val evicted = if (entries.size >= config.maxEntries) {
        // LRU eviction - remove the entry with oldest lastAccessed
        val oldest = entries.minBy(_._2.lastAccessed)._1
        entries - oldest
      } else entries

      val wasEvicted = evicted.size < entries.size
      (evicted.updated(dagName, entry), wasEvicted)
    }.flatMap { wasEvicted =>
      if (wasEvicted) statsRef.update(s => s.copy(evictions = s.evictions + 1))
      else IO.unit
    }
  }

  /** Invalidate a specific cache entry */
  def invalidate(dagName: String): IO[Unit] =
    cache.update(_ - dagName)

  /** Invalidate all cache entries */
  def invalidateAll(): IO[Unit] =
    cache.set(Map.empty)

  /** Get current cache statistics */
  def stats: IO[CacheStats] =
    for {
      s       <- statsRef.get
      entries <- cache.get
    } yield s.copy(entries = entries.size)

  /** Get current number of entries in the cache */
  def size: IO[Int] =
    cache.get.map(_.size)

  private def isValid(entry: CacheEntry, sourceHash: Int, registryHash: Int, now: Long): Boolean =
    entry.sourceHash == sourceHash &&
      entry.registryHash == registryHash &&
      (now - entry.createdAt) < config.maxAge.toMillis
}

object CompilationCache {

  /** Cache configuration */
  case class Config(
      maxEntries: Int = 100,
      maxAge: FiniteDuration = 1.hour
  )

  /** Create a new CompilationCache with the given configuration */
  def create(config: Config = Config()): IO[CompilationCache] =
    for {
      cache <- Ref.of[IO, Map[String, CacheEntry]](Map.empty)
      stats <- Ref.of[IO, CacheStats](CacheStats(0, 0, 0, 0))
    } yield new CompilationCache(cache, stats, config)

  /** Create a CompilationCache synchronously (for use in non-IO contexts).
    * Should only be used during initialization.
    */
  def createUnsafe(config: Config = Config()): CompilationCache = {
    import cats.effect.unsafe.implicits.global
    create(config).unsafeRunSync()
  }
}
