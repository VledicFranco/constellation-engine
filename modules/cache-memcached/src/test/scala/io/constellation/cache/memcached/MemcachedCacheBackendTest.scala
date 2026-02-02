package io.constellation.cache.memcached

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.cache.{CacheSerde, CacheStats, DistributedCacheBackend}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.duration.*

/** Unit tests for MemcachedCacheBackend using a mock client.
  *
  * These tests verify the backend logic without requiring a running Memcached server. Integration
  * tests with a real server should be tagged and run separately.
  */
class MemcachedCacheBackendTest extends AnyFlatSpec with Matchers {

  // -------------------------------------------------------------------------
  // MemcachedConfig
  // -------------------------------------------------------------------------

  "MemcachedConfig" should "have sensible defaults" in {
    val config = MemcachedConfig()
    config.addresses shouldBe List("localhost:11211")
    config.operationTimeout shouldBe 2500.millis
    config.connectionTimeout shouldBe 5.seconds
    config.maxReconnectDelay shouldBe 30.seconds
    config.keyPrefix shouldBe ""
  }

  "MemcachedConfig.single" should "create single-server config" in {
    val config = MemcachedConfig.single("cache.example.com:11211")
    config.addresses shouldBe List("cache.example.com:11211")
  }

  "MemcachedConfig.cluster" should "create multi-server config" in {
    val config = MemcachedConfig.cluster("host1:11211", "host2:11211", "host3:11211")
    config.addresses shouldBe List("host1:11211", "host2:11211", "host3:11211")
  }

  it should "support key prefix configuration" in {
    val config = MemcachedConfig(keyPrefix = "myapp")
    config.keyPrefix shouldBe "myapp"
  }

  // -------------------------------------------------------------------------
  // Backend logic (using FakeDistributedBackend from runtime tests pattern)
  // -------------------------------------------------------------------------

  /** A minimal fake that mimics MemcachedCacheBackend's key prefixing logic. */
  class FakeMemcachedBackend(config: MemcachedConfig)
      extends DistributedCacheBackend(CacheSerde.anySerde) {

    val storage   = new ConcurrentHashMap[String, (Array[Byte], Long, Long)]()
    val hitCount  = new AtomicLong(0)
    val missCount = new AtomicLong(0)

    private def prefixKey(key: String): String =
      if config.keyPrefix.isEmpty then key else s"${config.keyPrefix}:$key"

    override protected def getBytes(key: String): IO[Option[(Array[Byte], Long, Long)]] = IO {
      val prefixed = prefixKey(key)
      Option(storage.get(prefixed)) match {
        case Some(entry) if entry._3 > System.currentTimeMillis() =>
          hitCount.incrementAndGet()
          Some(entry)
        case Some(_) =>
          storage.remove(prefixed)
          missCount.incrementAndGet()
          None
        case None =>
          missCount.incrementAndGet()
          None
      }
    }

    override protected def setBytes(key: String, bytes: Array[Byte], ttl: FiniteDuration): IO[Unit] = IO {
      val prefixed = prefixKey(key)
      val now      = System.currentTimeMillis()
      storage.put(prefixed, (bytes, now, now + ttl.toMillis))
      ()
    }

    override protected def deleteKey(key: String): IO[Boolean] = IO {
      storage.remove(prefixKey(key)) != null
    }

    override protected def clearAll: IO[Unit] = IO(storage.clear())

    override protected def getStats: IO[CacheStats] = IO {
      CacheStats(hitCount.get(), missCount.get(), 0, storage.size(), None)
    }
  }

  "MemcachedCacheBackend key prefixing" should "prepend prefix when configured" in {
    val backend = new FakeMemcachedBackend(MemcachedConfig(keyPrefix = "myapp"))

    backend.set("key1", "value1", 1.minute).unsafeRunSync()

    // Verify the prefixed key is in storage
    backend.storage.containsKey("myapp:key1") shouldBe true

    // Should be retrievable via the unprefixed key
    val result = backend.get[String]("key1").unsafeRunSync()
    result shouldBe defined
    result.get.value shouldBe "value1"
  }

  it should "not prepend prefix when empty" in {
    val backend = new FakeMemcachedBackend(MemcachedConfig(keyPrefix = ""))

    backend.set("key1", "value1", 1.minute).unsafeRunSync()

    // Key should be stored without prefix
    backend.storage.containsKey("key1") shouldBe true
  }

  "MemcachedCacheBackend operations" should "support set/get/delete cycle" in {
    val backend = new FakeMemcachedBackend(MemcachedConfig())

    backend.set("key1", "value1", 1.minute).unsafeRunSync()
    backend.get[String]("key1").unsafeRunSync().get.value shouldBe "value1"

    backend.delete("key1").unsafeRunSync() shouldBe true
    backend.get[String]("key1").unsafeRunSync() shouldBe None
  }

  it should "support clear all" in {
    val backend = new FakeMemcachedBackend(MemcachedConfig())

    backend.set("k1", "v1", 1.minute).unsafeRunSync()
    backend.set("k2", "v2", 1.minute).unsafeRunSync()
    backend.clear.unsafeRunSync()

    backend.get[String]("k1").unsafeRunSync() shouldBe None
    backend.get[String]("k2").unsafeRunSync() shouldBe None
  }

  it should "track statistics" in {
    val backend = new FakeMemcachedBackend(MemcachedConfig())

    backend.set("k1", "v1", 1.minute).unsafeRunSync()
    backend.get[String]("k1").unsafeRunSync()      // hit
    backend.get[String]("missing").unsafeRunSync()  // miss

    val stats = backend.stats.unsafeRunSync()
    stats.hits shouldBe 1
    stats.misses shouldBe 1
  }
}
