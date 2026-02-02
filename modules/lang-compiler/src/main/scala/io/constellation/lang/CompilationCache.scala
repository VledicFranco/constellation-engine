package io.constellation.lang

import cats.effect.{IO, Ref}
import io.constellation.cache.{CacheBackend, CacheStats, InMemoryCacheBackend}
import io.constellation.lang.compiler.CompilationOutput

import scala.concurrent.duration.*

/** Thread-safe cache for compilation results.
  *
  * Delegates storage to a `CacheBackend` (defaulting to `InMemoryCacheBackend`) for consistency
  * with the runtime cache SPI. The compilation cache adds hash-based validation on top: a cached
  * result is only returned if both the source hash and registry hash match the stored entry.
  *
  * Statistics are tracked at this layer (not delegated to the backend) to ensure accurate
  * hit/miss counts regardless of backend caching behavior.
  *
  * '''Note:''' `CompilationOutput` contains closures (`Module.Uninitialized`) that cannot be
  * serialized, so this cache must use an in-memory backend. The `CacheBackend` abstraction is used
  * for API consistency, not for enabling distributed caching of compilation results.
  *
  * @param backend
  *   the underlying cache backend for storage
  * @param state
  *   thread-safe reference holding the key index and access timestamps
  * @param statsRef
  *   the thread-safe reference to cache statistics
  * @param config
  *   cache configuration
  */
class CompilationCache private (
    backend: CacheBackend,
    state: Ref[IO, CompilationCache.State],
    statsRef: Ref[IO, CacheStats],
    config: CompilationCache.Config
) {

  /** Construct the composite cache key used in the backend. */
  private def cacheKey(dagName: String, sourceHash: String, registryHash: String): String =
    s"compilation:$dagName:$sourceHash:$registryHash"

  /** Look up a cached compilation result.
    *
    * Returns Some(result) if:
    *   - Entry exists for the dagName with matching source hash and registry hash
    *   - Entry has not expired (TTL managed by the backend)
    *
    * A cache hit with a different source or registry hash is treated as a miss (the stale entry is
    * removed).
    */
  def get(
      dagName: String,
      sourceHash: String,
      registryHash: String
  ): IO[Option[CompilationOutput]] = {
    val key = cacheKey(dagName, sourceHash, registryHash)
    for {
      now <- IO(System.currentTimeMillis())
      st  <- state.get
      result <- st.keyIndex.get(dagName) match {
        case Some(indexedKey) if indexedKey == key =>
          // Key matches — check backend
          backend.get[CompilationOutput](key).flatMap {
            case Some(cached) =>
              state.update(s => s.copy(accessTimes = s.accessTimes.updated(dagName, now))) *>
                statsRef.update(s => s.copy(hits = s.hits + 1)).as(Some(cached.value))
            case None =>
              // Entry expired in backend
              state.update(s =>
                s.copy(
                  keyIndex = s.keyIndex - dagName,
                  accessTimes = s.accessTimes - dagName
                )
              ) *>
                statsRef.update(s => s.copy(misses = s.misses + 1)).as(None)
          }
        case Some(staleKey) =>
          // Different hash — stale entry, clean up and miss
          backend.delete(staleKey) *>
            state.update(s =>
              s.copy(
                keyIndex = s.keyIndex - dagName,
                accessTimes = s.accessTimes - dagName
              )
            ) *>
            statsRef.update(s => s.copy(misses = s.misses + 1)).as(None)
        case None =>
          // No entry at all — miss
          statsRef.update(s => s.copy(misses = s.misses + 1)).as(None)
      }
    } yield result
  }

  /** Store a compilation result in the cache.
    *
    * If the cache is at maximum capacity, evicts the least recently used entry.
    */
  def put(
      dagName: String,
      sourceHash: String,
      registryHash: String,
      result: CompilationOutput
  ): IO[Unit] = {
    val key = cacheKey(dagName, sourceHash, registryHash)
    for {
      now <- IO(System.currentTimeMillis())
      evicted <- state.modify { st =>
        // Remove stale key for this dagName if different
        val cleaned = st.keyIndex.get(dagName) match {
          case Some(old) if old != key =>
            st.copy(
              keyIndex = st.keyIndex - dagName,
              accessTimes = st.accessTimes - dagName
            )
          case _ => st
        }

        // Evict LRU if at capacity (and this dagName isn't already in the index)
        val needsEviction = !cleaned.keyIndex.contains(dagName) &&
          cleaned.keyIndex.size >= config.maxEntries

        val (afterEviction, evictedKey) = if needsEviction then {
          cleaned.accessTimes.minByOption(_._2).map(_._1) match {
            case Some(lruDag) =>
              val lruKey = cleaned.keyIndex(lruDag)
              (
                cleaned.copy(
                  keyIndex = cleaned.keyIndex - lruDag,
                  accessTimes = cleaned.accessTimes - lruDag
                ),
                Some(lruKey)
              )
            case None =>
              // accessTimes is empty despite keyIndex being full — defensive fallback
              (cleaned, None)
          }
        } else (cleaned, None)

        // Add the new entry
        val updated = afterEviction.copy(
          keyIndex = afterEviction.keyIndex.updated(dagName, key),
          accessTimes = afterEviction.accessTimes.updated(dagName, now)
        )

        (updated, evictedKey)
      }
      // Delete evicted entry from backend and update stats
      _ <- evicted match {
        case Some(evictedKey) =>
          backend.delete(evictedKey) *>
            statsRef.update(s => s.copy(evictions = s.evictions + 1))
        case None => IO.unit
      }
      // Store the new entry in the backend
      _ <- backend.set(key, result, config.maxAge)
    } yield ()
  }

  /** Invalidate a specific cache entry by dagName. */
  def invalidate(dagName: String): IO[Unit] =
    for {
      removedKey <- state.modify { st =>
        val key = st.keyIndex.get(dagName)
        (
          st.copy(
            keyIndex = st.keyIndex - dagName,
            accessTimes = st.accessTimes - dagName
          ),
          key
        )
      }
      _ <- removedKey match {
        case Some(key) => backend.delete(key).void
        case None      => IO.unit
      }
    } yield ()

  /** Invalidate all cache entries. */
  def invalidateAll(): IO[Unit] =
    state.set(CompilationCache.State.empty) *> backend.clear

  /** Get current cache statistics.
    *
    * Combines locally-tracked hit/miss/eviction counters with the entry count from state.
    */
  def stats: IO[CacheStats] =
    for {
      s  <- statsRef.get
      st <- state.get
    } yield s.copy(size = st.keyIndex.size)

  /** Get current number of entries in the cache. */
  def size: IO[Int] =
    state.get.map(_.keyIndex.size)

  /** Get the underlying cache backend. */
  def getBackend: CacheBackend = backend
}

object CompilationCache {

  /** Internal state for key tracking and LRU ordering. */
  private[lang] case class State(
      keyIndex: Map[String, String],
      accessTimes: Map[String, Long]
  )
  private[lang] object State {
    val empty: State = State(Map.empty, Map.empty)
  }

  /** Cache configuration */
  case class Config(
      maxEntries: Int = 100,
      maxAge: FiniteDuration = 1.hour
  )

  /** Create a new CompilationCache with the default in-memory backend. */
  def create(config: Config = Config()): IO[CompilationCache] =
    createWithBackend(new InMemoryCacheBackend(maxSize = Some(config.maxEntries)), config)

  /** Create a CompilationCache with a specific cache backend.
    *
    * '''Note:''' The backend must be in-memory since `CompilationOutput` contains non-serializable
    * closures. This method exists for API consistency and testing, not for distributed caching.
    */
  def createWithBackend(backend: CacheBackend, config: Config = Config()): IO[CompilationCache] =
    for {
      st    <- Ref.of[IO, State](State.empty)
      stats <- Ref.of[IO, CacheStats](CacheStats(0, 0, 0, 0, Some(config.maxEntries)))
    } yield new CompilationCache(backend, st, stats, config)

  /** Create a CompilationCache synchronously (for use in non-IO contexts). Should only be used
    * during initialization.
    */
  def createUnsafe(config: Config = Config()): CompilationCache = {
    import cats.effect.unsafe.implicits.global
    create(config).unsafeRunSync()
  }

  /** Create a CompilationCache synchronously with a specific cache backend. */
  def createUnsafeWithBackend(backend: CacheBackend, config: Config = Config()): CompilationCache = {
    import cats.effect.unsafe.implicits.global
    createWithBackend(backend, config).unsafeRunSync()
  }
}
