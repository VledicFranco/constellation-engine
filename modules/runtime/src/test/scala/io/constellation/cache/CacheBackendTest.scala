package io.constellation.cache

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.duration._

class CacheBackendTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  var cache: InMemoryCacheBackend = _

  override def beforeEach(): Unit = {
    cache = InMemoryCacheBackend()
  }

  // -------------------------------------------------------------------------
  // Basic Operations
  // -------------------------------------------------------------------------

  "InMemoryCacheBackend" should "store and retrieve values" in {
    cache.set("key1", "value1", 1.minute).unsafeRunSync()

    val result = cache.get[String]("key1").unsafeRunSync()

    result shouldBe defined
    result.get.value shouldBe "value1"
  }

  it should "return None for missing keys" in {
    val result = cache.get[String]("nonexistent").unsafeRunSync()
    result shouldBe None
  }

  it should "delete existing keys" in {
    cache.set("key1", "value1", 1.minute).unsafeRunSync()

    val deleted = cache.delete("key1").unsafeRunSync()

    deleted shouldBe true
    cache.get[String]("key1").unsafeRunSync() shouldBe None
  }

  it should "return false when deleting non-existent key" in {
    val deleted = cache.delete("nonexistent").unsafeRunSync()
    deleted shouldBe false
  }

  it should "clear all entries" in {
    cache.set("key1", "value1", 1.minute).unsafeRunSync()
    cache.set("key2", "value2", 1.minute).unsafeRunSync()

    cache.clear.unsafeRunSync()

    cache.get[String]("key1").unsafeRunSync() shouldBe None
    cache.get[String]("key2").unsafeRunSync() shouldBe None
  }

  // -------------------------------------------------------------------------
  // TTL and Expiration
  // -------------------------------------------------------------------------

  it should "expire entries after TTL" in {
    cache.set("short-lived", "value", 50.millis).unsafeRunSync()

    // Should exist immediately
    cache.get[String]("short-lived").unsafeRunSync() shouldBe defined

    // Wait for expiration
    Thread.sleep(100)

    // Should be expired now
    cache.get[String]("short-lived").unsafeRunSync() shouldBe None
  }

  it should "provide remaining TTL in entry" in {
    cache.set("key", "value", 1.second).unsafeRunSync()

    val entry = cache.get[String]("key").unsafeRunSync().get

    entry.remainingTtlMs should be > 0L
    entry.remainingTtlMs should be <= 1000L
  }

  // -------------------------------------------------------------------------
  // Statistics
  // -------------------------------------------------------------------------

  it should "track hits and misses" in {
    cache.set("key1", "value1", 1.minute).unsafeRunSync()

    // Generate hits
    cache.get[String]("key1").unsafeRunSync()
    cache.get[String]("key1").unsafeRunSync()

    // Generate misses
    cache.get[String]("missing").unsafeRunSync()
    cache.get[String]("also-missing").unsafeRunSync()

    val stats = cache.stats.unsafeRunSync()

    stats.hits shouldBe 2
    stats.misses shouldBe 2
    stats.hitRatio shouldBe 0.5
  }

  it should "track size" in {
    cache.set("key1", "value1", 1.minute).unsafeRunSync()
    cache.set("key2", "value2", 1.minute).unsafeRunSync()
    cache.set("key3", "value3", 1.minute).unsafeRunSync()

    val stats = cache.stats.unsafeRunSync()
    stats.size shouldBe 3
  }

  // -------------------------------------------------------------------------
  // Max Size and Eviction
  // -------------------------------------------------------------------------

  "InMemoryCacheBackend with maxSize" should "evict entries when full" in {
    val limitedCache = InMemoryCacheBackend.withMaxSize(3)

    limitedCache.set("key1", "value1", 1.minute).unsafeRunSync()
    limitedCache.set("key2", "value2", 1.minute).unsafeRunSync()
    limitedCache.set("key3", "value3", 1.minute).unsafeRunSync()

    // Cache is full, adding one more should evict oldest
    limitedCache.set("key4", "value4", 1.minute).unsafeRunSync()

    val stats = limitedCache.stats.unsafeRunSync()
    stats.size shouldBe 3
    stats.evictions should be >= 1L
  }

  it should "evict least recently used entries" in {
    val limitedCache = InMemoryCacheBackend.withMaxSize(3)

    limitedCache.set("key1", "value1", 1.minute).unsafeRunSync()
    Thread.sleep(10)
    limitedCache.set("key2", "value2", 1.minute).unsafeRunSync()
    Thread.sleep(10)
    limitedCache.set("key3", "value3", 1.minute).unsafeRunSync()

    // Access key1 to make it recently used
    limitedCache.get[String]("key1").unsafeRunSync()

    // Add new entry - should evict key2 (LRU)
    limitedCache.set("key4", "value4", 1.minute).unsafeRunSync()

    // key1 should still exist (was accessed recently)
    limitedCache.get[String]("key1").unsafeRunSync() shouldBe defined
    // key4 should exist (just added)
    limitedCache.get[String]("key4").unsafeRunSync() shouldBe defined
  }

  // -------------------------------------------------------------------------
  // getOrCompute
  // -------------------------------------------------------------------------

  "getOrCompute" should "return cached value if present" in {
    cache.set("key", "cached", 1.minute).unsafeRunSync()

    var computeCalled = false
    val result = cache.getOrCompute("key", 1.minute) {
      IO {
        computeCalled = true
        "computed"
      }
    }.unsafeRunSync()

    result shouldBe "cached"
    computeCalled shouldBe false
  }

  it should "compute and cache value if missing" in {
    var computeCount = 0
    val result = cache.getOrCompute("key", 1.minute) {
      IO {
        computeCount += 1
        s"computed-$computeCount"
      }
    }.unsafeRunSync()

    result shouldBe "computed-1"

    // Second call should use cached value
    val result2 = cache.getOrCompute("key", 1.minute) {
      IO {
        computeCount += 1
        s"computed-$computeCount"
      }
    }.unsafeRunSync()

    result2 shouldBe "computed-1"
    computeCount shouldBe 1
  }

  // -------------------------------------------------------------------------
  // Type Safety
  // -------------------------------------------------------------------------

  it should "handle different types" in {
    cache.set("string", "hello", 1.minute).unsafeRunSync()
    cache.set("int", 42, 1.minute).unsafeRunSync()
    cache.set("list", List(1, 2, 3), 1.minute).unsafeRunSync()
    cache.set("map", Map("a" -> 1), 1.minute).unsafeRunSync()

    cache.get[String]("string").unsafeRunSync().get.value shouldBe "hello"
    cache.get[Int]("int").unsafeRunSync().get.value shouldBe 42
    cache.get[List[Int]]("list").unsafeRunSync().get.value shouldBe List(1, 2, 3)
    cache.get[Map[String, Int]]("map").unsafeRunSync().get.value shouldBe Map("a" -> 1)
  }
}

class CacheKeyGeneratorTest extends AnyFlatSpec with Matchers {

  "CacheKeyGenerator" should "generate deterministic keys" in {
    val inputs = Map(
      "text" -> CValue.CString("hello"),
      "count" -> CValue.CInt(42)
    )

    val key1 = CacheKeyGenerator.generateKey("MyModule", inputs)
    val key2 = CacheKeyGenerator.generateKey("MyModule", inputs)

    key1 shouldBe key2
  }

  it should "generate different keys for different modules" in {
    val inputs = Map("text" -> CValue.CString("hello"))

    val key1 = CacheKeyGenerator.generateKey("Module1", inputs)
    val key2 = CacheKeyGenerator.generateKey("Module2", inputs)

    key1 should not be key2
  }

  it should "generate different keys for different inputs" in {
    val inputs1 = Map("text" -> CValue.CString("hello"))
    val inputs2 = Map("text" -> CValue.CString("world"))

    val key1 = CacheKeyGenerator.generateKey("MyModule", inputs1)
    val key2 = CacheKeyGenerator.generateKey("MyModule", inputs2)

    key1 should not be key2
  }

  it should "handle map ordering consistently" in {
    val inputs1 = Map(
      "a" -> CValue.CInt(1),
      "b" -> CValue.CInt(2),
      "c" -> CValue.CInt(3)
    )
    val inputs2 = Map(
      "c" -> CValue.CInt(3),
      "a" -> CValue.CInt(1),
      "b" -> CValue.CInt(2)
    )

    val key1 = CacheKeyGenerator.generateKey("MyModule", inputs1)
    val key2 = CacheKeyGenerator.generateKey("MyModule", inputs2)

    key1 shouldBe key2
  }

  it should "include version in key if provided" in {
    val inputs = Map("text" -> CValue.CString("hello"))

    val key1 = CacheKeyGenerator.generateKey("MyModule", inputs, version = Some("v1"))
    val key2 = CacheKeyGenerator.generateKey("MyModule", inputs, version = Some("v2"))

    key1 should not be key2
  }

  it should "handle complex nested values" in {
    val inputs = Map(
      "product" -> CValue.CProduct(
        Map(
          "name" -> CValue.CString("test"),
          "values" -> CValue.CList(
            Vector(CValue.CInt(1), CValue.CInt(2)),
            CType.CInt
          )
        ),
        Map("name" -> CType.CString, "values" -> CType.CList(CType.CInt))
      )
    )

    val key = CacheKeyGenerator.generateKey("MyModule", inputs)
    key.nonEmpty shouldBe true
    key.length should be > 20 // SHA-256 base64 is 43 chars
  }

  it should "generate short keys for display" in {
    val inputs = Map("text" -> CValue.CString("hello"))

    val shortKey = CacheKeyGenerator.generateShortKey("MyModule", inputs, 8)

    shortKey.length shouldBe 8
  }
}

class CacheRegistryTest extends AnyFlatSpec with Matchers {

  "CacheRegistry" should "register and retrieve backends" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend = InMemoryCacheBackend()

    registry.register("test", backend).unsafeRunSync()

    registry.get("test").unsafeRunSync() shouldBe Some(backend)
  }

  it should "return None for unregistered backends" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    registry.get("nonexistent").unsafeRunSync() shouldBe None
  }

  it should "use first registered backend as default" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend1 = InMemoryCacheBackend()
    val backend2 = InMemoryCacheBackend()

    registry.register("first", backend1).unsafeRunSync()
    registry.register("second", backend2).unsafeRunSync()

    registry.default.unsafeRunSync() shouldBe backend1
  }

  it should "allow setting default backend" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend1 = InMemoryCacheBackend()
    val backend2 = InMemoryCacheBackend()

    registry.register("first", backend1).unsafeRunSync()
    registry.register("second", backend2).unsafeRunSync()

    registry.setDefault("second").unsafeRunSync() shouldBe true
    registry.default.unsafeRunSync() shouldBe backend2
  }

  it should "list all registered backends" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    registry.register("memory", InMemoryCacheBackend()).unsafeRunSync()
    registry.register("backup", InMemoryCacheBackend()).unsafeRunSync()

    registry.list.unsafeRunSync() shouldBe List("backup", "memory") // sorted
  }

  it should "clear all backends" in {
    val registry = CacheRegistry.withBackends(
      "cache1" -> InMemoryCacheBackend(),
      "cache2" -> InMemoryCacheBackend()
    ).unsafeRunSync()

    // Add some data
    registry.get("cache1").unsafeRunSync().get.set("key", "value", 1.minute).unsafeRunSync()
    registry.get("cache2").unsafeRunSync().get.set("key", "value", 1.minute).unsafeRunSync()

    // Clear all
    registry.clearAll.unsafeRunSync()

    // Verify cleared
    registry.get("cache1").unsafeRunSync().get.get[String]("key").unsafeRunSync() shouldBe None
    registry.get("cache2").unsafeRunSync().get.get[String]("key").unsafeRunSync() shouldBe None
  }

  it should "get stats from all backends" in {
    val registry = CacheRegistry.withBackends(
      "cache1" -> InMemoryCacheBackend(),
      "cache2" -> InMemoryCacheBackend()
    ).unsafeRunSync()

    // Generate some activity
    val cache1 = registry.get("cache1").unsafeRunSync().get
    cache1.set("key", "value", 1.minute).unsafeRunSync()
    cache1.get[String]("key").unsafeRunSync()
    cache1.get[String]("missing").unsafeRunSync()

    val allStats = registry.allStats.unsafeRunSync()

    allStats.contains("cache1") shouldBe true
    allStats.contains("cache2") shouldBe true
    allStats("cache1").hits shouldBe 1
    allStats("cache1").misses shouldBe 1
  }

  it should "provide fallback backend when none registered" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    // Should return fallback InMemoryBackend
    val fallback = registry.default.unsafeRunSync()
    fallback should not be null

    // Should work
    fallback.set("key", "value", 1.minute).unsafeRunSync()
    fallback.get[String]("key").unsafeRunSync() shouldBe defined
  }
}
