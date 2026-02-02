package io.constellation.cache

import cats.effect.IO
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.duration.FiniteDuration

/** Abstract base class for distributed (network-backed) cache backends.
  *
  * Provides the bridge between the type-erased `CacheBackend` interface and byte-level network
  * operations. Subclasses implement `getBytes`/`setBytes`/`deleteKey`/`clearAll`/`getStats` to
  * communicate with the remote cache server.
  *
  * Serialization is handled by a `CacheSerde[Any]` instance, which by default tries JSON for
  * `CValue` instances and falls back to Java serialization for other types.
  *
  * ==Implementing a new backend==
  *
  * {{{
  * class RedisCacheBackend(client: RedisClient, serde: CacheSerde[Any])
  *   extends DistributedCacheBackend(serde) {
  *
  *   override protected def getBytes(key: String) = ...
  *   override protected def setBytes(key: String, bytes: Array[Byte], ttl: FiniteDuration) = ...
  *   override protected def deleteKey(key: String) = ...
  *   override protected def clearAll = ...
  *   override protected def getStats = ...
  * }
  * }}}
  *
  * @param serde
  *   Serialization strategy for cache values
  */
abstract class DistributedCacheBackend(serde: CacheSerde[Any]) extends CacheBackend {

  private val logger = Slf4jLogger.getLoggerFromClass[IO](classOf[DistributedCacheBackend])

  /** Get raw bytes and metadata from the remote cache.
    *
    * @param key
    *   The cache key
    * @return
    *   Some((bytes, createdAt, expiresAt)) if key exists and is valid, None otherwise
    */
  protected def getBytes(key: String): IO[Option[(Array[Byte], Long, Long)]]

  /** Store raw bytes in the remote cache.
    *
    * @param key
    *   The cache key
    * @param bytes
    *   The serialized value bytes
    * @param ttl
    *   Time-to-live for the entry
    */
  protected def setBytes(key: String, bytes: Array[Byte], ttl: FiniteDuration): IO[Unit]

  /** Delete a key from the remote cache.
    *
    * @return
    *   true if the key existed and was deleted
    */
  protected def deleteKey(key: String): IO[Boolean]

  /** Clear all entries from the remote cache. */
  protected def clearAll: IO[Unit]

  /** Get cache statistics from the remote server. */
  protected def getStats: IO[CacheStats]

  override def get[A](key: String): IO[Option[CacheEntry[A]]] =
    getBytes(key).flatMap {
      case Some((bytes, createdAt, expiresAt)) =>
        IO {
          val value = serde.deserialize(bytes).asInstanceOf[A]
          Some(CacheEntry(value, createdAt, expiresAt))
        }.handleErrorWith { e =>
          // Deserialization failure — log, delete corrupt entry, treat as cache miss
          logger.warn(e)(s"Cache deserialization failed for key '$key', deleting corrupt entry") *>
            deleteKey(key).as(None)
        }
      case None =>
        IO.pure(None)
    }

  override def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit] =
    IO(serde.serialize(value.asInstanceOf[Any])).flatMap { bytes =>
      setBytes(key, bytes, ttl)
    }

  override def delete(key: String): IO[Boolean] =
    deleteKey(key)

  override def clear: IO[Unit] =
    clearAll

  override def stats: IO[CacheStats] =
    getStats
}
