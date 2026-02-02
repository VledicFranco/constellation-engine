package io.constellation.cache.memcached

import cats.effect.{IO, Resource}
import io.constellation.cache.{CacheSerde, CacheStats, DistributedCacheBackend}
import net.spy.memcached.{AddrUtil, ConnectionFactoryBuilder, MemcachedClient}

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

/** Memcached-backed distributed cache backend.
  *
  * Uses spymemcached client for non-blocking I/O to Memcached servers. Values are serialized using
  * the provided `CacheSerde[Any]` (defaults to JSON for CValue, Java serialization fallback).
  *
  * Spymemcached's `SerializingTranscoder` natively recognizes `byte[]` as a special type and stores
  * it raw (via the `SPECIAL_BYTEARRAY` flag) without applying Java serialization on top. This means
  * our pre-serialized bytes from `CacheSerde` are stored as-is — no double serialization occurs.
  *
  * ==Lifecycle management==
  *
  * Always use `MemcachedCacheBackend.resource` for proper lifecycle management:
  *
  * {{{
  * MemcachedCacheBackend.resource(MemcachedConfig.single()).use { backend =>
  *   ConstellationImpl.builder()
  *     .withCache(backend)
  *     .build()
  *     .flatMap { constellation => ... }
  * }
  * }}}
  *
  * @param client
  *   The spymemcached client instance
  * @param config
  *   Memcached connection configuration
  * @param serde
  *   Serialization strategy for cache values
  */
class MemcachedCacheBackend(
    client: MemcachedClient,
    config: MemcachedConfig,
    serde: CacheSerde[Any] = CacheSerde.anySerde
) extends DistributedCacheBackend(serde) {

  // Client-side stats tracking (Memcached protocol stats are server-global, not per-client)
  private val hitCount      = new AtomicLong(0)
  private val missCount     = new AtomicLong(0)
  private val evictionCount = new AtomicLong(0)

  private def prefixKey(key: String): String =
    if config.keyPrefix.isEmpty then key else s"${config.keyPrefix}:$key"

  override protected def getBytes(key: String): IO[Option[(Array[Byte], Long, Long)]] =
    IO.blocking {
      val prefixed = prefixKey(key)
      Option(client.get(prefixed)) match {
        case Some(raw: Array[Byte] @unchecked) =>
          hitCount.incrementAndGet()
          val now = System.currentTimeMillis()
          // Memcached handles TTL expiration server-side; we don't have exact timestamps.
          // Use current time as createdAt approximation and a far-future expiresAt.
          Some((raw, now, now + 365L * 24 * 60 * 60 * 1000)) // 1 year
        case Some(_) =>
          // Unexpected type stored — treat as miss
          missCount.incrementAndGet()
          None
        case None =>
          missCount.incrementAndGet()
          None
      }
    }

  override protected def setBytes(
      key: String,
      bytes: Array[Byte],
      ttl: FiniteDuration
  ): IO[Unit] =
    IO.blocking {
      val prefixed   = prefixKey(key)
      val ttlSeconds = math.max(1, ttl.toSeconds.toInt)
      // byte[] is recognized as SPECIAL_BYTEARRAY by SerializingTranscoder — stored raw
      val future = client.set(prefixed, ttlSeconds, bytes)
      // Block until the set completes or times out, so errors are not silently lost.
      // The operationTimeout configured on the ConnectionFactory bounds this wait.
      future.get()
      ()
    }

  override protected def deleteKey(key: String): IO[Boolean] =
    IO.blocking {
      val prefixed = prefixKey(key)
      val future   = client.delete(prefixed)
      val result: Boolean = Try(future.get()).getOrElse(java.lang.Boolean.FALSE)
      result
    }

  override protected def clearAll: IO[Unit] =
    IO.blocking {
      client.flush()
      ()
    }

  override protected def getStats: IO[CacheStats] = IO {
    CacheStats(
      hits = hitCount.get(),
      misses = missCount.get(),
      evictions = evictionCount.get(),
      size = 0, // Memcached doesn't expose per-client item count
      maxSize = None
    )
  }

  /** Shutdown the Memcached client connection. */
  def shutdown(): IO[Unit] = IO.blocking(client.shutdown())
}

object MemcachedCacheBackend {

  /** Create a MemcachedCacheBackend as a cats-effect Resource for proper lifecycle management.
    *
    * The client connection is established on resource acquisition and shut down on release.
    *
    * {{{
    * MemcachedCacheBackend.resource(MemcachedConfig.single()).use { backend =>
    *   // use backend...
    * }
    * }}}
    */
  def resource(
      config: MemcachedConfig,
      serde: CacheSerde[Any] = CacheSerde.anySerde
  ): Resource[IO, MemcachedCacheBackend] =
    Resource.make(create(config, serde))(_.shutdown())

  /** Create a MemcachedCacheBackend without lifecycle management.
    *
    * The caller is responsible for calling `shutdown()` when done. Prefer `resource` instead.
    *
    * Returns a failed IO if the server addresses cannot be resolved.
    */
  def create(
      config: MemcachedConfig,
      serde: CacheSerde[Any] = CacheSerde.anySerde
  ): IO[MemcachedCacheBackend] = IO.blocking {
    val addresses = AddrUtil.getAddresses(config.addresses.mkString(" "))
    // Note: spymemcached's ConnectionFactoryBuilder does not expose a dedicated
    // connection timeout setting. The operationTimeout governs blocking behavior.
    // connectionTimeout is retained in MemcachedConfig for forward compatibility.
    val factory = new ConnectionFactoryBuilder()
      .setOpTimeout(config.operationTimeout.toMillis)
      .setMaxReconnectDelay(config.maxReconnectDelay.toSeconds)
      .setDaemon(true)
      .build()
    val client = new MemcachedClient(factory, addresses)
    new MemcachedCacheBackend(client, config, serde)
  }
}
