package io.constellation.cache

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.duration.*

class CacheBackendTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  var cache: InMemoryCacheBackend = _

  override def beforeEach(): Unit =
    cache = InMemoryCacheBackend()

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

  it should "handle concurrent writes without race conditions" in {
    import cats.syntax.all.*

    val limitedCache = InMemoryCacheBackend.withMaxSize(10)

    // Launch 100 concurrent set operations
    val writes = (1 to 100).toList.map { i =>
      IO {
        limitedCache.set(s"key-$i", s"value-$i", 1.minute).unsafeRunSync()
      }
    }

    // All writes should succeed without ConcurrentModificationException
    val result = writes.parSequence.attempt.unsafeRunSync()
    result.isRight shouldBe true

    val stats = limitedCache.stats.unsafeRunSync()
    // Cache size should not exceed maxSize
    stats.size should be <= 10
  }

  it should "cache stats results for performance (5 second TTL)" in {
    cache.set("key1", "value1", 1.minute).unsafeRunSync()
    cache.set("key2", "value2", 1.minute).unsafeRunSync()

    // First stats call - triggers cleanup and computation
    val stats1 = cache.stats.unsafeRunSync()
    stats1.size shouldBe 2

    // Add more entries
    cache.set("key3", "value3", 1.minute).unsafeRunSync()

    // Second stats call within TTL - should return cached stats (size still 2)
    val stats2 = cache.stats.unsafeRunSync()
    stats2.size shouldBe 2 // Cached value, doesn't reflect key3 yet

    // Wait for cache to expire (TTL is 5 seconds)
    Thread.sleep(5100)

    // Third stats call after TTL - should recompute (size now 3)
    val stats3 = cache.stats.unsafeRunSync()
    stats3.size shouldBe 3
  }

  it should "invalidate cached stats when cache is cleared" in {
    cache.set("key1", "value1", 1.minute).unsafeRunSync()
    val stats1 = cache.stats.unsafeRunSync()
    stats1.size shouldBe 1

    // Clear cache
    cache.clear.unsafeRunSync()

    // Stats should immediately reflect cleared cache (not cached)
    val stats2 = cache.stats.unsafeRunSync()
    stats2.size shouldBe 0
  }

  // -------------------------------------------------------------------------
  // getOrCompute
  // -------------------------------------------------------------------------

  "getOrCompute" should "return cached value if present" in {
    cache.set("key", "cached", 1.minute).unsafeRunSync()

    var computeCalled = false
    val result = cache
      .getOrCompute("key", 1.minute) {
        IO {
          computeCalled = true
          "computed"
        }
      }
      .unsafeRunSync()

    result shouldBe "cached"
    computeCalled shouldBe false
  }

  it should "compute and cache value if missing" in {
    var computeCount = 0
    val result = cache
      .getOrCompute("key", 1.minute) {
        IO {
          computeCount += 1
          s"computed-$computeCount"
        }
      }
      .unsafeRunSync()

    result shouldBe "computed-1"

    // Second call should use cached value
    val result2 = cache
      .getOrCompute("key", 1.minute) {
        IO {
          computeCount += 1
          s"computed-$computeCount"
        }
      }
      .unsafeRunSync()

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
      "text"  -> CValue.CString("hello"),
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

  it should "handle CFloat values" in {
    val inputs = Map("num" -> CValue.CFloat(3.14))
    val key    = CacheKeyGenerator.generateKey("Mod", inputs)
    key.nonEmpty shouldBe true
  }

  it should "handle CBoolean values" in {
    val inputs = Map("flag" -> CValue.CBoolean(true))
    val key    = CacheKeyGenerator.generateKey("Mod", inputs)
    key.nonEmpty shouldBe true
  }

  it should "handle CMap values" in {
    val inputs = Map(
      "data" -> CValue.CMap(
        Vector(
          (CValue.CString("a"), CValue.CInt(1)),
          (CValue.CString("b"), CValue.CInt(2))
        ),
        CType.CString,
        CType.CInt
      )
    )
    val key = CacheKeyGenerator.generateKey("Mod", inputs)
    key.nonEmpty shouldBe true
  }

  it should "handle CUnion values" in {
    val inputs = Map(
      "either" -> CValue.CUnion(
        CValue.CString("value"),
        Map("left" -> CType.CString, "right" -> CType.CInt),
        "left"
      )
    )
    val key = CacheKeyGenerator.generateKey("Mod", inputs)
    key.nonEmpty shouldBe true
  }

  it should "handle CSome values" in {
    val inputs = Map("opt" -> CValue.CSome(CValue.CInt(42), CType.CInt))
    val key    = CacheKeyGenerator.generateKey("Mod", inputs)
    key.nonEmpty shouldBe true
  }

  it should "handle CNone values" in {
    val inputs = Map("opt" -> CValue.CNone(CType.CInt))
    val key    = CacheKeyGenerator.generateKey("Mod", inputs)
    key.nonEmpty shouldBe true
  }

  it should "produce deterministic keys for CMap regardless of insertion order" in {
    val map1 = CValue.CMap(
      Vector(
        (CValue.CString("b"), CValue.CInt(2)),
        (CValue.CString("a"), CValue.CInt(1))
      ),
      CType.CString,
      CType.CInt
    )
    val map2 = CValue.CMap(
      Vector(
        (CValue.CString("a"), CValue.CInt(1)),
        (CValue.CString("b"), CValue.CInt(2))
      ),
      CType.CString,
      CType.CInt
    )

    val key1 = CacheKeyGenerator.generateKey("Mod", Map("data" -> map1))
    val key2 = CacheKeyGenerator.generateKey("Mod", Map("data" -> map2))

    key1 shouldBe key2
  }

  it should "generate short keys for display" in {
    val inputs = Map("text" -> CValue.CString("hello"))

    val shortKey = CacheKeyGenerator.generateShortKey("MyModule", inputs, 8)

    shortKey.length shouldBe 8
  }

  it should "generate short keys with default length of 8" in {
    val inputs = Map("text" -> CValue.CString("hello"))

    val shortKey = CacheKeyGenerator.generateShortKey("MyModule", inputs)

    shortKey.length shouldBe 8
  }

  it should "generate short key as prefix of full key" in {
    val inputs = Map("text" -> CValue.CString("hello"))

    val fullKey  = CacheKeyGenerator.generateKey("MyModule", inputs)
    val shortKey = CacheKeyGenerator.generateShortKey("MyModule", inputs)

    fullKey should startWith(shortKey)
  }

  "hashBytes" should "produce consistent hash for same bytes" in {
    val bytes = "hello world".getBytes("UTF-8")

    val hash1 = CacheKeyGenerator.hashBytes(bytes)
    val hash2 = CacheKeyGenerator.hashBytes(bytes)

    hash1 shouldBe hash2
  }

  it should "produce different hashes for different bytes" in {
    val hash1 = CacheKeyGenerator.hashBytes("hello".getBytes("UTF-8"))
    val hash2 = CacheKeyGenerator.hashBytes("world".getBytes("UTF-8"))

    hash1 should not be hash2
  }
}

class CacheRegistryTest extends AnyFlatSpec with Matchers {

  "CacheRegistry" should "register and retrieve backends" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend  = InMemoryCacheBackend()

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
    val registry = CacheRegistry
      .withBackends(
        "cache1" -> InMemoryCacheBackend(),
        "cache2" -> InMemoryCacheBackend()
      )
      .unsafeRunSync()

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
    val registry = CacheRegistry
      .withBackends(
        "cache1" -> InMemoryCacheBackend(),
        "cache2" -> InMemoryCacheBackend()
      )
      .unsafeRunSync()

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

  it should "return false when setting unknown default" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    registry.setDefault("nonexistent").unsafeRunSync() shouldBe false
  }

  it should "unregister backends" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    registry.register("memory", InMemoryCacheBackend()).unsafeRunSync()
    registry.unregister("memory").unsafeRunSync() shouldBe true
    registry.get("memory").unsafeRunSync() shouldBe None
  }

  it should "return false when unregistering unknown backend" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    registry.unregister("nonexistent").unsafeRunSync() shouldBe false
  }

  "CacheRegistry.withMemory" should "create working registry with memory backend" in {
    val registry = CacheRegistry.withMemory.unsafeRunSync()

    registry.list.unsafeRunSync() shouldBe List("memory")

    val defaultBackend = registry.default.unsafeRunSync()
    defaultBackend.isInstanceOf[InMemoryCacheBackend] shouldBe true

    // Verify it works
    defaultBackend.set("key", "value", 1.minute).unsafeRunSync()
    val entry = defaultBackend.get[String]("key").unsafeRunSync()
    entry shouldBe defined
    entry.get.value shouldBe "value"
  }

  "CacheRegistry.withBackends" should "register multiple backends" in {
    val registry = CacheRegistry
      .withBackends(
        "backend1" -> InMemoryCacheBackend(),
        "backend2" -> InMemoryCacheBackend()
      )
      .unsafeRunSync()

    registry.list.unsafeRunSync() shouldBe List("backend1", "backend2")
  }
}
