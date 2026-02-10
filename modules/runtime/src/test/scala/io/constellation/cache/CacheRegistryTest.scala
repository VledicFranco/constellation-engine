package io.constellation.cache

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CacheRegistryExtendedTest extends AnyFlatSpec with Matchers {

  // -------------------------------------------------------------------------
  // CacheRegistry.create - empty registry
  // -------------------------------------------------------------------------

  "CacheRegistry.create" should "produce an empty registry with no backends listed" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    registry.list.unsafeRunSync() shouldBe empty
  }

  it should "return None when getting a backend from an empty registry" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    registry.get("anything").unsafeRunSync() shouldBe None
  }

  // -------------------------------------------------------------------------
  // register
  // -------------------------------------------------------------------------

  "register" should "add a backend that can be retrieved by name" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend  = InMemoryCacheBackend()

    registry.register("test", backend).unsafeRunSync()

    registry.get("test").unsafeRunSync() shouldBe Some(backend)
  }

  it should "set the first registered backend as the default" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend1 = InMemoryCacheBackend()

    registry.register("first", backend1).unsafeRunSync()

    registry.default.unsafeRunSync() shouldBe backend1
  }

  it should "not change the default when a second backend is registered" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend1 = InMemoryCacheBackend()
    val backend2 = InMemoryCacheBackend()

    registry.register("first", backend1).unsafeRunSync()
    registry.register("second", backend2).unsafeRunSync()

    registry.default.unsafeRunSync() shouldBe backend1
  }

  // -------------------------------------------------------------------------
  // get
  // -------------------------------------------------------------------------

  "get" should "return Some for a registered backend" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend  = InMemoryCacheBackend()

    registry.register("existing", backend).unsafeRunSync()

    registry.get("existing").unsafeRunSync() shouldBe Some(backend)
  }

  it should "return None for an unregistered name" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend  = InMemoryCacheBackend()

    registry.register("existing", backend).unsafeRunSync()

    registry.get("nonexistent").unsafeRunSync() shouldBe None
  }

  // -------------------------------------------------------------------------
  // default
  // -------------------------------------------------------------------------

  "default" should "return a fallback InMemory backend when no backends are registered" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    val fallback = registry.default.unsafeRunSync()

    fallback should not be null
    // Verify the fallback is functional
    fallback.set("key", "value", 1.minute).unsafeRunSync()
    fallback.get[String]("key").unsafeRunSync() shouldBe defined
  }

  it should "return the first registered backend as the default" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend1 = InMemoryCacheBackend()
    val backend2 = InMemoryCacheBackend()

    registry.register("alpha", backend1).unsafeRunSync()
    registry.register("beta", backend2).unsafeRunSync()

    registry.default.unsafeRunSync() shouldBe backend1
  }

  // -------------------------------------------------------------------------
  // setDefault
  // -------------------------------------------------------------------------

  "setDefault" should "change the default when the backend exists and return true" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend1 = InMemoryCacheBackend()
    val backend2 = InMemoryCacheBackend()

    registry.register("first", backend1).unsafeRunSync()
    registry.register("second", backend2).unsafeRunSync()

    val result = registry.setDefault("second").unsafeRunSync()

    result shouldBe true
    registry.default.unsafeRunSync() shouldBe backend2
  }

  it should "return false when the backend does not exist and not change the default" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend  = InMemoryCacheBackend()

    registry.register("existing", backend).unsafeRunSync()

    val result = registry.setDefault("nonexistent").unsafeRunSync()

    result shouldBe false
    registry.default.unsafeRunSync() shouldBe backend
  }

  // -------------------------------------------------------------------------
  // list
  // -------------------------------------------------------------------------

  "list" should "return an empty list when no backends are registered" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    registry.list.unsafeRunSync() shouldBe empty
  }

  it should "return registered backend names in sorted order" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    registry.register("zebra", InMemoryCacheBackend()).unsafeRunSync()
    registry.register("alpha", InMemoryCacheBackend()).unsafeRunSync()
    registry.register("middle", InMemoryCacheBackend()).unsafeRunSync()

    registry.list.unsafeRunSync() shouldBe List("alpha", "middle", "zebra")
  }

  // -------------------------------------------------------------------------
  // allStats
  // -------------------------------------------------------------------------

  "allStats" should "return an empty map when no backends are registered" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    registry.allStats.unsafeRunSync() shouldBe empty
  }

  it should "return stats for all registered backends" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend1 = InMemoryCacheBackend()
    val backend2 = InMemoryCacheBackend()

    registry.register("cache1", backend1).unsafeRunSync()
    registry.register("cache2", backend2).unsafeRunSync()

    // Generate some activity on cache1
    backend1.set("key", "value", 1.minute).unsafeRunSync()
    backend1.get[String]("key").unsafeRunSync() // hit
    backend1.get[String]("missing").unsafeRunSync() // miss

    val stats = registry.allStats.unsafeRunSync()

    stats.size shouldBe 2
    stats.contains("cache1") shouldBe true
    stats.contains("cache2") shouldBe true
    stats("cache1").hits shouldBe 1
    stats("cache1").misses shouldBe 1
    stats("cache2").hits shouldBe 0
  }

  // -------------------------------------------------------------------------
  // clearAll
  // -------------------------------------------------------------------------

  "clearAll" should "clear all registered backends" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend1 = InMemoryCacheBackend()
    val backend2 = InMemoryCacheBackend()

    registry.register("cache1", backend1).unsafeRunSync()
    registry.register("cache2", backend2).unsafeRunSync()

    // Add data to both backends
    backend1.set("key1", "value1", 1.minute).unsafeRunSync()
    backend2.set("key2", "value2", 1.minute).unsafeRunSync()

    // Verify data exists
    backend1.get[String]("key1").unsafeRunSync() shouldBe defined
    backend2.get[String]("key2").unsafeRunSync() shouldBe defined

    // Clear all
    registry.clearAll.unsafeRunSync()

    // Verify both backends are cleared
    backend1.get[String]("key1").unsafeRunSync() shouldBe None
    backend2.get[String]("key2").unsafeRunSync() shouldBe None
  }

  // -------------------------------------------------------------------------
  // unregister
  // -------------------------------------------------------------------------

  "unregister" should "remove an existing backend and return true" in {
    val registry = CacheRegistry.create.unsafeRunSync()
    val backend  = InMemoryCacheBackend()

    registry.register("target", backend).unsafeRunSync()

    val result = registry.unregister("target").unsafeRunSync()

    result shouldBe true
    registry.get("target").unsafeRunSync() shouldBe None
  }

  it should "return false when unregistering a non-existing backend" in {
    val registry = CacheRegistry.create.unsafeRunSync()

    val result = registry.unregister("nonexistent").unsafeRunSync()

    result shouldBe false
  }

  // -------------------------------------------------------------------------
  // CacheRegistry.withBackends
  // -------------------------------------------------------------------------

  "CacheRegistry.withBackends" should "pre-configure multiple backends" in {
    val backend1 = InMemoryCacheBackend()
    val backend2 = InMemoryCacheBackend()

    val registry = CacheRegistry
      .withBackends(
        "backend1" -> backend1,
        "backend2" -> backend2
      )
      .unsafeRunSync()

    registry.list.unsafeRunSync() shouldBe List("backend1", "backend2")
    registry.get("backend1").unsafeRunSync() shouldBe Some(backend1)
    registry.get("backend2").unsafeRunSync() shouldBe Some(backend2)
  }

  it should "set the first provided backend as the default" in {
    val backend1 = InMemoryCacheBackend()
    val backend2 = InMemoryCacheBackend()

    val registry = CacheRegistry
      .withBackends(
        "first"  -> backend1,
        "second" -> backend2
      )
      .unsafeRunSync()

    registry.default.unsafeRunSync() shouldBe backend1
  }

  // -------------------------------------------------------------------------
  // CacheRegistry.withMemory
  // -------------------------------------------------------------------------

  "CacheRegistry.withMemory" should "create a registry with a memory backend named 'memory'" in {
    val registry = CacheRegistry.withMemory.unsafeRunSync()

    registry.list.unsafeRunSync() shouldBe List("memory")
  }

  it should "use the memory backend as the default" in {
    val registry = CacheRegistry.withMemory.unsafeRunSync()

    val defaultBackend = registry.default.unsafeRunSync()
    defaultBackend.isInstanceOf[InMemoryCacheBackend] shouldBe true
  }

  it should "provide a functional backend" in {
    val registry = CacheRegistry.withMemory.unsafeRunSync()

    val backend = registry.default.unsafeRunSync()
    backend.set("key", "value", 1.minute).unsafeRunSync()

    val entry = backend.get[String]("key").unsafeRunSync()
    entry shouldBe defined
    entry.get.value shouldBe "value"
  }

  // -------------------------------------------------------------------------
  // CacheRegistry.withMemory(maxSize)
  // -------------------------------------------------------------------------

  "CacheRegistry.withMemory(maxSize)" should "create a registry with a size-limited memory backend" in {
    val registry = CacheRegistry.withMemory(100).unsafeRunSync()

    registry.list.unsafeRunSync() shouldBe List("memory")
  }

  it should "use a backend that respects the max size" in {
    val registry = CacheRegistry.withMemory(3).unsafeRunSync()
    val backend  = registry.default.unsafeRunSync()

    // Fill beyond capacity
    backend.set("key1", "value1", 1.minute).unsafeRunSync()
    backend.set("key2", "value2", 1.minute).unsafeRunSync()
    backend.set("key3", "value3", 1.minute).unsafeRunSync()
    backend.set("key4", "value4", 1.minute).unsafeRunSync()

    val stats = backend.stats.unsafeRunSync()
    stats.size shouldBe 3
    stats.evictions should be >= 1L
  }
}
