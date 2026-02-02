package io.constellation.cache.memcached

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.cache.CacheSerde
import net.spy.memcached.MemcachedClient
import net.spy.memcached.internal.OperationFuture
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration.*

/** Unit tests that exercise the real MemcachedCacheBackend class with a mocked spymemcached client.
  *
  * This covers: key prefixing, IO.blocking wrapping, future.get() error handling, stats tracking,
  * and integration between the serde layer and the client calls.
  *
  * For integration tests with a real Memcached server, see [[MemcachedIntegrationTest]].
  */
class MemcachedCacheBackendMockTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private var mockClient: MemcachedClient = _
  private var mockSetFuture: OperationFuture[java.lang.Boolean] = _
  private var mockDeleteFuture: OperationFuture[java.lang.Boolean] = _
  private var mockFlushFuture: OperationFuture[java.lang.Boolean] = _

  override def beforeEach(): Unit = {
    mockClient = mock(classOf[MemcachedClient])
    mockSetFuture = mock(classOf[OperationFuture[java.lang.Boolean]])
    mockDeleteFuture = mock(classOf[OperationFuture[java.lang.Boolean]])
    mockFlushFuture = mock(classOf[OperationFuture[java.lang.Boolean]])

    when(mockSetFuture.get()).thenReturn(java.lang.Boolean.TRUE)
    when(mockDeleteFuture.get()).thenReturn(java.lang.Boolean.TRUE)
    when(mockFlushFuture.get()).thenReturn(java.lang.Boolean.TRUE)

    when(mockClient.set(anyString(), anyInt(), any())).thenReturn(mockSetFuture)
    when(mockClient.delete(anyString())).thenReturn(mockDeleteFuture)
    when(mockClient.flush()).thenReturn(mockFlushFuture)
  }

  private def createBackend(
      config: MemcachedConfig = MemcachedConfig()
  ): MemcachedCacheBackend =
    new MemcachedCacheBackend(mockClient, config)

  // -------------------------------------------------------------------------
  // Key Prefixing
  // -------------------------------------------------------------------------

  "MemcachedCacheBackend" should "prefix keys when keyPrefix is set" in {
    val backend = createBackend(MemcachedConfig(keyPrefix = "myapp"))

    // Set up mock to return serialized bytes
    val testValue = "hello"
    val serialized = CacheSerde.anySerde.serialize(testValue)
    when(mockClient.get("myapp:testkey")).thenReturn(serialized)

    backend.set("testkey", testValue, 1.minute).unsafeRunSync()

    // Verify set was called with prefixed key
    verify(mockClient).set(org.mockito.ArgumentMatchers.eq("myapp:testkey"), anyInt(), any())
  }

  it should "not prefix keys when keyPrefix is empty" in {
    val backend = createBackend(MemcachedConfig(keyPrefix = ""))

    backend.set("testkey", "value", 1.minute).unsafeRunSync()

    verify(mockClient).set(org.mockito.ArgumentMatchers.eq("testkey"), anyInt(), any())
  }

  // -------------------------------------------------------------------------
  // Set Operations
  // -------------------------------------------------------------------------

  it should "convert TTL to seconds for set" in {
    val backend = createBackend()

    backend.set("key", "value", 5.minutes).unsafeRunSync()

    verify(mockClient).set(anyString(), org.mockito.ArgumentMatchers.eq(300), any())
  }

  it should "enforce minimum 1 second TTL" in {
    val backend = createBackend()

    backend.set("key", "value", 100.millis).unsafeRunSync()

    verify(mockClient).set(anyString(), org.mockito.ArgumentMatchers.eq(1), any())
  }

  it should "await set future to detect errors" in {
    val backend = createBackend()

    backend.set("key", "value", 1.minute).unsafeRunSync()

    verify(mockSetFuture).get()
  }

  it should "serialize values through CacheSerde before storing" in {
    val backend = createBackend()
    val testValue = "hello"

    backend.set("key", testValue, 1.minute).unsafeRunSync()

    // The value passed to client.set should be a byte array (CacheSerde output)
    val captor = org.mockito.ArgumentCaptor.forClass(classOf[AnyRef])
    verify(mockClient).set(anyString(), anyInt(), captor.capture())
    captor.getValue shouldBe a[Array[Byte]]

    // Verify the bytes round-trip through serde
    val stored = captor.getValue.asInstanceOf[Array[Byte]]
    CacheSerde.anySerde.deserialize(stored) shouldBe testValue
  }

  // -------------------------------------------------------------------------
  // Get Operations
  // -------------------------------------------------------------------------

  it should "return deserialized value on cache hit" in {
    val backend = createBackend()
    val testValue = "hello"
    val serialized = CacheSerde.anySerde.serialize(testValue)

    when(mockClient.get("key")).thenReturn(serialized)

    val result = backend.get[String]("key").unsafeRunSync()
    result shouldBe defined
    result.get.value shouldBe testValue
  }

  it should "return None on cache miss" in {
    val backend = createBackend()

    when(mockClient.get("key")).thenReturn(null)

    val result = backend.get[String]("key").unsafeRunSync()
    result shouldBe None
  }

  it should "return None for unexpected (non-byte-array) stored type" in {
    val backend = createBackend()

    // Simulate a non-byte-array object being returned (e.g., from a different client)
    when(mockClient.get("key")).thenReturn(java.lang.Integer.valueOf(42))

    val result = backend.get[String]("key").unsafeRunSync()
    result shouldBe None
  }

  it should "get with prefixed key" in {
    val backend = createBackend(MemcachedConfig(keyPrefix = "app"))
    val serialized = CacheSerde.anySerde.serialize("value")

    when(mockClient.get("app:key")).thenReturn(serialized)

    val result = backend.get[String]("key").unsafeRunSync()
    result shouldBe defined
    result.get.value shouldBe "value"

    verify(mockClient).get("app:key")
  }

  // -------------------------------------------------------------------------
  // Delete Operations
  // -------------------------------------------------------------------------

  it should "delete with prefixed key and return true" in {
    val backend = createBackend(MemcachedConfig(keyPrefix = "app"))

    when(mockDeleteFuture.get()).thenReturn(java.lang.Boolean.TRUE)

    val result = backend.delete("key").unsafeRunSync()
    result shouldBe true

    verify(mockClient).delete("app:key")
  }

  it should "return false when delete future returns false" in {
    val backend = createBackend()

    when(mockDeleteFuture.get()).thenReturn(java.lang.Boolean.FALSE)

    val result = backend.delete("key").unsafeRunSync()
    result shouldBe false
  }

  it should "return false when delete future throws" in {
    val backend = createBackend()

    when(mockDeleteFuture.get()).thenThrow(new RuntimeException("timeout"))

    val result = backend.delete("key").unsafeRunSync()
    result shouldBe false
  }

  // -------------------------------------------------------------------------
  // Clear
  // -------------------------------------------------------------------------

  it should "call flush on clear" in {
    val backend = createBackend()

    backend.clear.unsafeRunSync()

    verify(mockClient).flush()
  }

  // -------------------------------------------------------------------------
  // Stats Tracking
  // -------------------------------------------------------------------------

  it should "track hits on successful get" in {
    val backend = createBackend()
    val serialized = CacheSerde.anySerde.serialize("value")

    when(mockClient.get("key")).thenReturn(serialized)

    backend.get[String]("key").unsafeRunSync()
    backend.get[String]("key").unsafeRunSync()

    val stats = backend.stats.unsafeRunSync()
    stats.hits shouldBe 2
    stats.misses shouldBe 0
  }

  it should "track misses on null return" in {
    val backend = createBackend()

    when(mockClient.get("key")).thenReturn(null)

    backend.get[String]("key").unsafeRunSync()

    val stats = backend.stats.unsafeRunSync()
    stats.hits shouldBe 0
    stats.misses shouldBe 1
  }

  it should "track misses on unexpected type" in {
    val backend = createBackend()

    when(mockClient.get("key")).thenReturn("not bytes")

    backend.get[String]("key").unsafeRunSync()

    val stats = backend.stats.unsafeRunSync()
    stats.hits shouldBe 0
    stats.misses shouldBe 1
  }

  it should "report size=0 and maxSize=None in stats" in {
    val backend = createBackend()

    val stats = backend.stats.unsafeRunSync()
    stats.size shouldBe 0
    stats.maxSize shouldBe None
  }

  // -------------------------------------------------------------------------
  // Shutdown
  // -------------------------------------------------------------------------

  it should "call client.shutdown on shutdown" in {
    val backend = createBackend()

    backend.shutdown().unsafeRunSync()

    verify(mockClient).shutdown()
  }
}
