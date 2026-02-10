package io.constellation.pool

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DeferredPoolTest extends AnyFlatSpec with Matchers {

  // ============================================================================
  // create / empty
  // ============================================================================

  "DeferredPool.create" should "initialize pool with correct size" in {
    val test = for {
      pool <- DeferredPool.create(initialSize = 10, maxSize = 100)
      size <- pool.size
    } yield (size, pool.initialSize, pool.maxSize)

    val (size, init, max) = test.unsafeRunSync()
    size shouldBe 10
    init shouldBe 10
    max shouldBe 100
  }

  it should "pre-allocate initial Deferreds" in {
    val test = for {
      pool      <- DeferredPool.create(initialSize = 20, maxSize = 200)
      size      <- pool.size
      deferreds <- pool.acquireN(20)
    } yield (size, deferreds.length)

    val (sizeBeforeAcquire, acquiredCount) = test.unsafeRunSync()
    sizeBeforeAcquire shouldBe 20
    acquiredCount shouldBe 20
  }

  "DeferredPool.empty" should "create pool with size 0 and maxSize 0" in {
    val test = for {
      pool <- DeferredPool.empty
      size <- pool.size
    } yield (size, pool.initialSize, pool.maxSize)

    val (size, init, max) = test.unsafeRunSync()
    size shouldBe 0
    init shouldBe 0
    max shouldBe 0
  }

  // ============================================================================
  // acquire
  // ============================================================================

  "acquire" should "decrement pool size and record a hit" in {
    val test = for {
      pool      <- DeferredPool.create(initialSize = 5, maxSize = 100)
      _         <- pool.acquire
      sizeAfter <- pool.size
    } yield (sizeAfter, pool.getMetrics)

    val (sizeAfter, metrics) = test.unsafeRunSync()
    sizeAfter shouldBe 4
    metrics.hits shouldBe 1
    metrics.misses shouldBe 0
    metrics.totalAcquires shouldBe 1
  }

  it should "create a fresh Deferred and record a miss when pool is empty" in {
    val test = for {
      pool      <- DeferredPool.empty
      d         <- pool.acquire
      sizeAfter <- pool.size
    } yield (d, sizeAfter, pool.getMetrics)

    val (d, sizeAfter, metrics) = test.unsafeRunSync()
    d should not be null
    sizeAfter shouldBe 0
    metrics.hits shouldBe 0
    metrics.misses shouldBe 1
    metrics.totalAcquires shouldBe 1
  }

  // ============================================================================
  // acquireN
  // ============================================================================

  "acquireN" should "return empty list for n = 0" in {
    val test = for {
      pool   <- DeferredPool.create(initialSize = 5, maxSize = 100)
      result <- pool.acquireN(0)
      size   <- pool.size
    } yield (result, size, pool.getMetrics)

    val (result, size, metrics) = test.unsafeRunSync()
    result shouldBe empty
    size shouldBe 5
    metrics.totalAcquires shouldBe 0
  }

  it should "take all from pool when n < pool size (all hits)" in {
    val test = for {
      pool      <- DeferredPool.create(initialSize = 10, maxSize = 100)
      deferreds <- pool.acquireN(3)
      sizeAfter <- pool.size
    } yield (deferreds.length, sizeAfter, pool.getMetrics)

    val (count, sizeAfter, metrics) = test.unsafeRunSync()
    count shouldBe 3
    sizeAfter shouldBe 7
    metrics.hits shouldBe 3
    metrics.misses shouldBe 0
    metrics.totalAcquires shouldBe 3
  }

  it should "mix pool and fresh Deferreds when n > pool size" in {
    val test = for {
      pool      <- DeferredPool.create(initialSize = 3, maxSize = 100)
      deferreds <- pool.acquireN(7)
      sizeAfter <- pool.size
    } yield (deferreds.length, sizeAfter, pool.getMetrics)

    val (count, sizeAfter, metrics) = test.unsafeRunSync()
    count shouldBe 7
    sizeAfter shouldBe 0
    metrics.hits shouldBe 3
    metrics.misses shouldBe 4
    metrics.totalAcquires shouldBe 7
  }

  it should "create all fresh Deferreds from empty pool (all misses)" in {
    val test = for {
      pool      <- DeferredPool.empty
      deferreds <- pool.acquireN(5)
      sizeAfter <- pool.size
    } yield (deferreds.length, sizeAfter, pool.getMetrics)

    val (count, sizeAfter, metrics) = test.unsafeRunSync()
    count shouldBe 5
    sizeAfter shouldBe 0
    metrics.hits shouldBe 0
    metrics.misses shouldBe 5
    metrics.totalAcquires shouldBe 5
  }

  // ============================================================================
  // replenish
  // ============================================================================

  "replenish" should "add Deferreds up to maxSize" in {
    val test = for {
      pool            <- DeferredPool.create(initialSize = 0, maxSize = 50)
      sizeBefore      <- pool.size
      _               <- pool.replenish(10)
      sizeReplenished <- pool.size
    } yield (sizeBefore, sizeReplenished)

    val (before, after) = test.unsafeRunSync()
    before shouldBe 0
    after shouldBe 10
  }

  it should "not exceed maxSize" in {
    val test = for {
      pool <- DeferredPool.create(initialSize = 5, maxSize = 10)
      _    <- pool.replenish(20)
      size <- pool.size
    } yield size

    test.unsafeRunSync() shouldBe 10
  }

  it should "do nothing when count is 0 and pool is at maxSize" in {
    val test = for {
      pool       <- DeferredPool.create(initialSize = 10, maxSize = 10)
      sizeBefore <- pool.size
      _          <- pool.replenish(0)
      sizeAfter  <- pool.size
    } yield (sizeBefore, sizeAfter)

    val (before, after) = test.unsafeRunSync()
    before shouldBe 10
    after shouldBe 10
  }

  // ============================================================================
  // size
  // ============================================================================

  "size" should "return the correct count after mixed operations" in {
    val test = for {
      pool <- DeferredPool.create(initialSize = 8, maxSize = 100)
      s1   <- pool.size
      _    <- pool.acquire
      s2   <- pool.size
      _    <- pool.acquireN(3)
      s3   <- pool.size
      _    <- pool.replenish(2)
      s4   <- pool.size
    } yield (s1, s2, s3, s4)

    val (s1, s2, s3, s4) = test.unsafeRunSync()
    s1 shouldBe 8
    s2 shouldBe 7
    s3 shouldBe 4
    s4 shouldBe 6
  }

  // ============================================================================
  // getMetrics / hitRate
  // ============================================================================

  "getMetrics.hitRate" should "return 0.0 when no acquires have been made" in {
    val test = for {
      pool <- DeferredPool.create(initialSize = 5, maxSize = 100)
    } yield pool.getMetrics

    val metrics = test.unsafeRunSync()
    metrics.hitRate shouldBe 0.0
    metrics.hits shouldBe 0
    metrics.misses shouldBe 0
    metrics.totalAcquires shouldBe 0
  }

  it should "return correct ratio after mixed hits and misses" in {
    val test = for {
      pool <- DeferredPool.create(initialSize = 3, maxSize = 100)
      _    <- pool.acquire // hit
      _    <- pool.acquire // hit
      _    <- pool.acquire // hit
      _    <- pool.acquire // miss (pool now empty)
    } yield pool.getMetrics

    val metrics = test.unsafeRunSync()
    metrics.hits shouldBe 3
    metrics.misses shouldBe 1
    metrics.totalAcquires shouldBe 4
    metrics.hitRate shouldBe 0.75 +- 0.01
  }

  // ============================================================================
  // Full lifecycle
  // ============================================================================

  "DeferredPool" should "support a full lifecycle of create, acquire, replenish, acquire" in {
    val test = for {
      pool <- DeferredPool.create(initialSize = 5, maxSize = 20)
      s0   <- pool.size

      // Phase 1: Deplete the pool
      _ <- pool.acquireN(5)
      s1 <- pool.size
      m1 = pool.getMetrics

      // Phase 2: Replenish
      _ <- pool.replenish(10)
      s2 <- pool.size

      // Phase 3: Acquire again from the replenished pool
      _ <- pool.acquireN(4)
      s3 <- pool.size
      m3 = pool.getMetrics
    } yield (s0, s1, m1, s2, s3, m3)

    val (s0, s1, m1, s2, s3, m3) = test.unsafeRunSync()

    // Initial state
    s0 shouldBe 5

    // After depleting
    s1 shouldBe 0
    m1.hits shouldBe 5
    m1.misses shouldBe 0

    // After replenish
    s2 shouldBe 10

    // After second round of acquires
    s3 shouldBe 6
    m3.hits shouldBe 9  // 5 from first batch + 4 from second batch
    m3.misses shouldBe 0
    m3.totalAcquires shouldBe 9
    m3.hitRate shouldBe 1.0 +- 0.01
  }

  // ============================================================================
  // initialSize and maxSize fields
  // ============================================================================

  "initialSize and maxSize" should "be accessible on the pool instance" in {
    val test = for {
      pool <- DeferredPool.create(initialSize = 42, maxSize = 999)
    } yield (pool.initialSize, pool.maxSize)

    val (init, max) = test.unsafeRunSync()
    init shouldBe 42
    max shouldBe 999
  }
}
