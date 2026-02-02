package io.constellation.cache.memcached

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.cache.CacheSerde
import io.constellation.{CType, CValue}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

import scala.concurrent.duration.*

/** Integration tests for MemcachedCacheBackend using a real Memcached server via Testcontainers.
  *
  * '''Requires Docker.''' These tests are excluded from the default `sbt test` run. Run manually:
  * {{{
  * sbt "cacheMemcached/testOnly *IntegrationTest"
  * }}}
  *
  * Tests exercise the full round-trip: CacheSerde serialization -> spymemcached client ->
  * Memcached wire protocol -> Memcached server -> wire protocol -> client -> CacheSerde
  * deserialization.
  */
class MemcachedIntegrationTest
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterAll
    with BeforeAndAfterEach {

  private var container: GenericContainer[?] = _
  private var backend: MemcachedCacheBackend = _
  private var config: MemcachedConfig        = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    container = new GenericContainer(DockerImageName.parse("memcached:1.6-alpine"))
    container.withExposedPorts(11211)
    container.start()

    config = MemcachedConfig.single(
      s"${container.getHost}:${container.getMappedPort(11211)}"
    )
    backend = MemcachedCacheBackend.create(config).unsafeRunSync()
  }

  override def afterEach(): Unit = {
    // Flush between tests for isolation
    backend.clear.unsafeRunSync()
    super.afterEach()
  }

  override def afterAll(): Unit = {
    if backend != null then backend.shutdown().unsafeRunSync()
    if container != null then container.stop()
    super.afterAll()
  }

  // -------------------------------------------------------------------------
  // Basic Round-Trip
  // -------------------------------------------------------------------------

  "MemcachedCacheBackend (integration)" should "round-trip a String through CacheSerde" in {
    backend.set("key1", "hello world", 1.minute).unsafeRunSync()

    val result = backend.get[String]("key1").unsafeRunSync()
    result shouldBe defined
    result.get.value shouldBe "hello world"
  }

  it should "round-trip a CValue.CString" in {
    val value = CValue.CString("constellation")

    backend.set("cv-string", value, 1.minute).unsafeRunSync()

    val result = backend.get[CValue]("cv-string").unsafeRunSync()
    result shouldBe defined
    result.get.value shouldBe value
  }

  it should "round-trip a CValue.CInt" in {
    val value = CValue.CInt(42L)

    backend.set("cv-int", value, 1.minute).unsafeRunSync()

    val result = backend.get[CValue]("cv-int").unsafeRunSync()
    result shouldBe defined
    result.get.value shouldBe value
  }

  it should "round-trip a CValue.CProduct" in {
    val value = CValue.CProduct(
      Map("name" -> CValue.CString("test"), "count" -> CValue.CInt(5)),
      Map("name" -> CType.CString, "count" -> CType.CInt)
    )

    backend.set("cv-product", value, 1.minute).unsafeRunSync()

    val result = backend.get[CValue]("cv-product").unsafeRunSync()
    result shouldBe defined
    result.get.value shouldBe value
  }

  it should "round-trip a CValue.CList" in {
    val value = CValue.CList(
      Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
      CType.CInt
    )

    backend.set("cv-list", value, 1.minute).unsafeRunSync()

    val result = backend.get[CValue]("cv-list").unsafeRunSync()
    result shouldBe defined
    result.get.value shouldBe value
  }

  it should "round-trip a Map[String, CValue]" in {
    val value: Map[String, CValue] = Map(
      "text"  -> CValue.CString("hello"),
      "count" -> CValue.CInt(42)
    )

    backend.set("map-cv", value, 1.minute).unsafeRunSync()

    val result = backend.get[Map[String, CValue]]("map-cv").unsafeRunSync()
    result shouldBe defined
    result.get.value shouldBe value
  }

  // -------------------------------------------------------------------------
  // Cache Miss
  // -------------------------------------------------------------------------

  it should "return None for missing keys" in {
    val result = backend.get[String]("nonexistent").unsafeRunSync()
    result shouldBe None
  }

  // -------------------------------------------------------------------------
  // Delete
  // -------------------------------------------------------------------------

  it should "delete an existing key" in {
    backend.set("del-me", "value", 1.minute).unsafeRunSync()

    val deleted = backend.delete("del-me").unsafeRunSync()
    deleted shouldBe true

    val result = backend.get[String]("del-me").unsafeRunSync()
    result shouldBe None
  }

  it should "return false when deleting a non-existent key" in {
    val deleted = backend.delete("never-existed").unsafeRunSync()
    deleted shouldBe false
  }

  // -------------------------------------------------------------------------
  // Clear
  // -------------------------------------------------------------------------

  it should "clear all entries" in {
    backend.set("a", "1", 1.minute).unsafeRunSync()
    backend.set("b", "2", 1.minute).unsafeRunSync()

    backend.clear.unsafeRunSync()

    // Give Memcached a moment to process the flush
    Thread.sleep(100)

    backend.get[String]("a").unsafeRunSync() shouldBe None
    backend.get[String]("b").unsafeRunSync() shouldBe None
  }

  // -------------------------------------------------------------------------
  // TTL Expiration (server-side)
  // -------------------------------------------------------------------------

  it should "expire entries after TTL (server-side)" in {
    // Use 1-second TTL (minimum for Memcached)
    backend.set("short-lived", "value", 1.second).unsafeRunSync()

    // Should be present immediately
    backend.get[String]("short-lived").unsafeRunSync() shouldBe defined

    // Wait for expiration (Memcached TTL is in seconds, so 2s should be enough)
    Thread.sleep(2500)

    backend.get[String]("short-lived").unsafeRunSync() shouldBe None
  }

  // -------------------------------------------------------------------------
  // Stats
  // -------------------------------------------------------------------------

  it should "track hits and misses" in {
    backend.set("stats-key", "value", 1.minute).unsafeRunSync()

    backend.get[String]("stats-key").unsafeRunSync()  // hit
    backend.get[String]("stats-key").unsafeRunSync()  // hit
    backend.get[String]("no-such-key").unsafeRunSync() // miss

    val stats = backend.stats.unsafeRunSync()
    stats.hits shouldBe 2
    stats.misses shouldBe 1
  }

  // -------------------------------------------------------------------------
  // getOrCompute
  // -------------------------------------------------------------------------

  it should "support getOrCompute" in {
    var computeCount = 0
    val result1 = backend.getOrCompute[String]("compute-key", 1.minute)(IO {
      computeCount += 1
      s"computed-$computeCount"
    }).unsafeRunSync()

    result1 shouldBe "computed-1"
    computeCount shouldBe 1

    val result2 = backend.getOrCompute[String]("compute-key", 1.minute)(IO {
      computeCount += 1
      s"computed-$computeCount"
    }).unsafeRunSync()

    result2 shouldBe "computed-1" // cached
    computeCount shouldBe 1
  }

  // -------------------------------------------------------------------------
  // Key Prefixing (end-to-end)
  // -------------------------------------------------------------------------

  it should "isolate entries with different key prefixes" in {
    val configA = config.copy(keyPrefix = "app-a")
    val configB = config.copy(keyPrefix = "app-b")

    val bA = MemcachedCacheBackend.create(configA).unsafeRunSync()
    val bB = MemcachedCacheBackend.create(configB).unsafeRunSync()

    try {
      bA.set("shared-key", "value-a", 1.minute).unsafeRunSync()
      bB.set("shared-key", "value-b", 1.minute).unsafeRunSync()

      val resultA = bA.get[String]("shared-key").unsafeRunSync()
      val resultB = bB.get[String]("shared-key").unsafeRunSync()

      resultA shouldBe defined
      resultA.get.value shouldBe "value-a"

      resultB shouldBe defined
      resultB.get.value shouldBe "value-b"
    } finally {
      bA.shutdown().unsafeRunSync()
      bB.shutdown().unsafeRunSync()
    }
  }

  // -------------------------------------------------------------------------
  // Resource lifecycle
  // -------------------------------------------------------------------------

  it should "work with Resource for lifecycle management" in {
    val result = MemcachedCacheBackend
      .resource(config)
      .use { b =>
        b.set("resource-key", "resource-value", 1.minute) *>
          b.get[String]("resource-key")
      }
      .unsafeRunSync()

    result shouldBe defined
    result.get.value shouldBe "resource-value"
  }
}
