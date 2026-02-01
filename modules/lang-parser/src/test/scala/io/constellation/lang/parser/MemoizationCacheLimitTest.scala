package io.constellation.lang.parser

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for parser memoization cache LRU eviction and memory limits. */
class MemoizationCacheLimitTest extends AnyFlatSpec with Matchers {

  // Create a test class that exposes MemoizationSupport for testing
  private class TestMemoizationParser extends MemoizationSupport {
    def testClearCache(): Unit = clearMemoCache()
    def testGetCacheSize: Int = getCacheSize
    def testCacheResult(offset: Int, parserId: Int, result: Either[cats.parse.Parser.Error, String]): Unit =
      cacheResult("test", offset, parserId, result)
    def testCheckCache[A](offset: Int, parserId: Int): Option[Either[cats.parse.Parser.Error, A]] =
      checkCache[A]("test", offset, parserId)
  }

  "MemoizationSupport cache" should "enforce max size limit of 1000 entries" in {
    val parser = new TestMemoizationParser()

    // Add 1500 entries (exceeds MaxCacheSize of 1000)
    (0 until 1500).foreach { i =>
      parser.testCacheResult(i, 0, Right(s"result-$i"))
    }

    // Cache should be limited to 1000 entries
    val cacheSize = parser.testGetCacheSize
    cacheSize should be <= 1000
  }

  it should "evict least recently used entries when full" in {
    val parser = new TestMemoizationParser()

    // Fill cache to max size
    (0 until 1000).foreach { i =>
      parser.testCacheResult(i, 0, Right(s"result-$i"))
    }

    // Access first 100 entries to mark them as recently used
    (0 until 100).foreach { i =>
      parser.testCheckCache[String](i, 0)
    }

    // Add 200 more entries (should evict entries 100-299, not 0-99)
    (1000 until 1200).foreach { i =>
      parser.testCacheResult(i, 0, Right(s"result-$i"))
    }

    // First 100 entries should still be in cache (recently accessed)
    (0 until 100).foreach { i =>
      val cached = parser.testCheckCache[String](i, 0)
      cached shouldBe defined
    }

    // Cache size should still be at limit
    parser.testGetCacheSize should be <= 1000
  }

  it should "allow manual cache clearing" in {
    val parser = new TestMemoizationParser()

    // Add some entries
    (0 until 100).foreach { i =>
      parser.testCacheResult(i, 0, Right(s"result-$i"))
    }

    parser.testGetCacheSize should be > 0

    // Clear cache
    parser.testClearCache()

    // Cache should be empty
    parser.testGetCacheSize shouldBe 0
  }

  it should "remain stable over many parses (memory leak test)" in {
    val parser = new TestMemoizationParser()

    // Simulate 100 parses with clearing between each
    (1 to 100).foreach { parseNum =>
      parser.testClearCache()

      // Each parse adds some entries
      (0 until 50).foreach { i =>
        parser.testCacheResult(i, parseNum, Right(s"parse-$parseNum-result-$i"))
      }

      // After clearing, cache should be small
      parser.testGetCacheSize should be <= 50
    }

    // Final cache should be small (not accumulated from all parses)
    parser.testGetCacheSize should be <= 50
  }

  it should "remain stable without manual clearing (LRU protects against leak)" in {
    val parser = new TestMemoizationParser()

    // Simulate 100 parses WITHOUT clearing (simulates leak scenario)
    (1 to 100).foreach { parseNum =>
      // Each parse adds 50 entries
      (0 until 50).foreach { i =>
        // Use offset + parseNum to create unique keys
        val offset = (parseNum * 1000) + i
        parser.testCacheResult(offset, 0, Right(s"parse-$parseNum-result-$i"))
      }
    }

    // Cache should be limited to max size despite 5000 total insertions
    val finalSize = parser.testGetCacheSize
    finalSize should be <= 1000

    // Should contain only the most recent entries
    // (last parse used offsets 99000-99049)
    val recentEntry = parser.testCheckCache[String](99040, 0)
    recentEntry shouldBe defined
  }
}
