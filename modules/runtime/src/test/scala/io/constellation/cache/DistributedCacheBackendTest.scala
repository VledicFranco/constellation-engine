package io.constellation.cache

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.RetrySupport

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable

/** Tests for DistributedCacheBackend using a fake in-memory byte store.
  *
  * This verifies the abstract contract: serialization/deserialization through the serde layer,
  * delegation to byte-level operations, and error handling.
  */
class DistributedCacheBackendTest extends AnyFlatSpec with Matchers with RetrySupport {

  /** A fake distributed backend that stores bytes in a ConcurrentHashMap. */
  class FakeDistributedBackend(serde: CacheSerde[Any] = CacheSerde.anySerde)
      extends DistributedCacheBackend(serde) {

    val storage   = new ConcurrentHashMap[String, (Array[Byte], Long, Long)]()
    val hitCount  = new AtomicLong(0)
    val missCount = new AtomicLong(0)

    override protected def getBytes(key: String): IO[Option[(Array[Byte], Long, Long)]] = IO {
      Option(storage.get(key)) match {
        case Some(entry) if entry._3 > System.currentTimeMillis() =>
          hitCount.incrementAndGet()
          Some(entry)
        case Some(_) =>
          // Expired
          storage.remove(key)
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
    ): IO[Unit] = IO {
      val now = System.currentTimeMillis()
      storage.put(key, (bytes, now, now + ttl.toMillis))
      ()
    }

    override protected def deleteKey(key: String): IO[Boolean] = IO {
      storage.remove(key) != null
    }

    override protected def clearAll: IO[Unit] = IO {
      storage.clear()
    }

    override protected def getStats: IO[CacheStats] = IO {
      CacheStats(
        hits = hitCount.get(),
        misses = missCount.get(),
        evictions = 0,
        size = storage.size(),
        maxSize = None
      )
    }
  }

  // -------------------------------------------------------------------------
  // Basic Operations
  // -------------------------------------------------------------------------

  "DistributedCacheBackend" should "set and get values through serde" in {
    val backend = new FakeDistributedBackend()

    backend.set("key1", "value1", 1.minute).unsafeRunSync()
    val result = backend.get[String]("key1").unsafeRunSync()

    result shouldBe defined
    result.get.value shouldBe "value1"
  }

  it should "return None for missing keys" in {
    val backend = new FakeDistributedBackend()
    val result  = backend.get[String]("nonexistent").unsafeRunSync()
    result shouldBe None
  }

  it should "delete keys" in {
    val backend = new FakeDistributedBackend()

    backend.set("key1", "value1", 1.minute).unsafeRunSync()
    backend.delete("key1").unsafeRunSync() shouldBe true
    backend.get[String]("key1").unsafeRunSync() shouldBe None
  }

  it should "return false when deleting non-existent key" in {
    val backend = new FakeDistributedBackend()
    backend.delete("nonexistent").unsafeRunSync() shouldBe false
  }

  it should "clear all entries" in {
    val backend = new FakeDistributedBackend()

    backend.set("key1", "value1", 1.minute).unsafeRunSync()
    backend.set("key2", "value2", 1.minute).unsafeRunSync()
    backend.clear.unsafeRunSync()

    backend.get[String]("key1").unsafeRunSync() shouldBe None
    backend.get[String]("key2").unsafeRunSync() shouldBe None
  }

  // -------------------------------------------------------------------------
  // Statistics
  // -------------------------------------------------------------------------

  it should "track hits and misses" in {
    val backend = new FakeDistributedBackend()

    backend.set("key1", "value1", 1.minute).unsafeRunSync()
    backend.get[String]("key1").unsafeRunSync()    // hit
    backend.get[String]("missing").unsafeRunSync() // miss

    val stats = backend.stats.unsafeRunSync()
    stats.hits shouldBe 1
    stats.misses shouldBe 1
  }

  // -------------------------------------------------------------------------
  // TTL Expiration
  // -------------------------------------------------------------------------

  it should "expire entries after TTL" taggedAs Retryable in {
    val backend = new FakeDistributedBackend()

    backend.set("short", "value", 50.millis).unsafeRunSync()
    backend.get[String]("short").unsafeRunSync() shouldBe defined

    Thread.sleep(100)

    backend.get[String]("short").unsafeRunSync() shouldBe None
  }

  // -------------------------------------------------------------------------
  // getOrCompute
  // -------------------------------------------------------------------------

  it should "support getOrCompute" in {
    val backend = new FakeDistributedBackend()

    var computeCount = 0
    val result1 = backend
      .getOrCompute("key", 1.minute)(IO {
        computeCount += 1
        s"computed-$computeCount"
      })
      .unsafeRunSync()

    result1 shouldBe "computed-1"

    val result2 = backend
      .getOrCompute("key", 1.minute)(IO {
        computeCount += 1
        s"computed-$computeCount"
      })
      .unsafeRunSync()

    result2 shouldBe "computed-1" // Cached
    computeCount shouldBe 1
  }

  // -------------------------------------------------------------------------
  // Error Handling
  // -------------------------------------------------------------------------

  it should "treat deserialization failures as cache misses" in {
    val backend = new FakeDistributedBackend()

    // Store corrupt bytes directly
    val now = System.currentTimeMillis()
    backend.storage.put("corrupt", ("not valid".getBytes("UTF-8"), now, now + 60000))

    // Should return None (deserialization fails, entry is deleted)
    val result = backend.get[String]("corrupt").unsafeRunSync()
    result shouldBe None

    // Corrupt entry should be cleaned up
    backend.storage.containsKey("corrupt") shouldBe false
  }
}
