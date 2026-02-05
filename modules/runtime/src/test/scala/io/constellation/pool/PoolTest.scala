package io.constellation.pool

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PoolTest extends AnyFlatSpec with Matchers {

  // ============================================================================
  // DeferredPool Tests
  // ============================================================================

  "DeferredPool" should "create with initial size" in {
    val test = for {
      pool <- DeferredPool.create(initialSize = 10, maxSize = 100)
      size <- pool.size
    } yield size

    test.unsafeRunSync() shouldBe 10
  }

  it should "acquire deferreds from pool" in {
    val test = for {
      pool      <- DeferredPool.create(initialSize = 5, maxSize = 100)
      d1        <- pool.acquire
      d2        <- pool.acquire
      sizeAfter <- pool.size
    } yield (sizeAfter, d1, d2)

    val (sizeAfter, d1, d2) = test.unsafeRunSync()
    sizeAfter shouldBe 3 // 5 - 2 = 3
    d1 should not be d2  // Different deferreds
  }

  it should "create new deferreds when pool is empty" in {
    val test = for {
      pool      <- DeferredPool.create(initialSize = 2, maxSize = 100)
      _         <- pool.acquire
      _         <- pool.acquire
      sizeEmpty <- pool.size
      // Pool is now empty, this should create a new one
      d3             <- pool.acquire
      sizeStillEmpty <- pool.size
    } yield (sizeEmpty, sizeStillEmpty, d3)

    val (sizeEmpty, sizeStillEmpty, d3) = test.unsafeRunSync()
    sizeEmpty shouldBe 0
    sizeStillEmpty shouldBe 0
    d3 should not be null
  }

  it should "acquire multiple deferreds efficiently" in {
    val test = for {
      pool      <- DeferredPool.create(initialSize = 10, maxSize = 100)
      deferreds <- pool.acquireN(5)
      sizeAfter <- pool.size
    } yield (deferreds.length, sizeAfter)

    val (count, sizeAfter) = test.unsafeRunSync()
    count shouldBe 5
    sizeAfter shouldBe 5 // 10 - 5 = 5
  }

  it should "acquire more than available and create new ones" in {
    val test = for {
      pool      <- DeferredPool.create(initialSize = 3, maxSize = 100)
      deferreds <- pool.acquireN(5) // Need 5 but only 3 available
      sizeAfter <- pool.size
    } yield (deferreds.length, sizeAfter)

    val (count, sizeAfter) = test.unsafeRunSync()
    count shouldBe 5
    sizeAfter shouldBe 0 // All 3 from pool used, 2 created fresh
  }

  it should "replenish pool after use" in {
    val test = for {
      pool            <- DeferredPool.create(initialSize = 5, maxSize = 100)
      _               <- pool.acquireN(5) // Deplete pool
      sizeDepleted    <- pool.size
      _               <- pool.replenish(5)
      sizeReplenished <- pool.size
    } yield (sizeDepleted, sizeReplenished)

    val (sizeDepleted, sizeReplenished) = test.unsafeRunSync()
    sizeDepleted shouldBe 0
    sizeReplenished shouldBe 5
  }

  it should "not exceed max size on replenish" in {
    val test = for {
      pool <- DeferredPool.create(initialSize = 5, maxSize = 10)
      _    <- pool.replenish(20) // Try to add 20 to pool of 5
      size <- pool.size
    } yield size

    test.unsafeRunSync() shouldBe 10 // Capped at max
  }

  it should "track metrics correctly" in {
    val test = for {
      pool <- DeferredPool.create(initialSize = 3, maxSize = 100)
      _    <- pool.acquire // Hit
      _    <- pool.acquire // Hit
      _    <- pool.acquire // Hit
      _    <- pool.acquire // Miss (pool empty)
    } yield pool.getMetrics

    val metrics = test.unsafeRunSync()
    metrics.hits shouldBe 3
    metrics.misses shouldBe 1
    metrics.totalAcquires shouldBe 4
    metrics.hitRate shouldBe 0.75 +- 0.01
  }

  it should "track metrics for batch acquires" in {
    val test = for {
      pool <- DeferredPool.create(initialSize = 5, maxSize = 100)
      _    <- pool.acquireN(7) // 5 hits, 2 misses
    } yield pool.getMetrics

    val metrics = test.unsafeRunSync()
    metrics.hits shouldBe 5
    metrics.misses shouldBe 2
    metrics.totalAcquires shouldBe 7
  }

  // ============================================================================
  // RuntimeStatePool Tests
  // ============================================================================

  "RuntimeStatePool" should "create with initial size" in {
    val test = for {
      pool <- RuntimeStatePool.create(initialSize = 5, maxSize = 20)
      size <- pool.size
    } yield size

    test.unsafeRunSync() shouldBe 5
  }

  it should "acquire state from pool" in {
    val test = for {
      pool      <- RuntimeStatePool.create(initialSize = 3, maxSize = 20)
      state     <- pool.acquire
      sizeAfter <- pool.size
    } yield (state.inUse, sizeAfter)

    val (inUse, sizeAfter) = test.unsafeRunSync()
    inUse shouldBe true
    sizeAfter shouldBe 2 // 3 - 1 = 2
  }

  it should "release state back to pool" in {
    val test = for {
      pool              <- RuntimeStatePool.create(initialSize = 2, maxSize = 20)
      state             <- pool.acquire
      sizeBeforeRelease <- pool.size
      _                 <- pool.release(state)
      sizeAfterRelease  <- pool.size
    } yield (sizeBeforeRelease, sizeAfterRelease, state.inUse)

    val (before, after, inUse) = test.unsafeRunSync()
    before shouldBe 1
    after shouldBe 2     // Released back
    inUse shouldBe false // Reset on release
  }

  it should "clear state on release" in {
    val test = for {
      pool  <- RuntimeStatePool.create(initialSize = 1, maxSize = 20)
      state <- pool.acquire
      // Modify state
      _ = state.moduleStatus.put(
        java.util.UUID.randomUUID(),
        cats.Eval.now(io.constellation.Module.Status.Unfired)
      )
      _ = state.data.put(
        java.util.UUID.randomUUID(),
        cats.Eval.now(io.constellation.CValue.CInt(42))
      )
      _ <- pool.release(state)
    } yield (state.moduleStatus.isEmpty, state.data.isEmpty)

    val (statusEmpty, dataEmpty) = test.unsafeRunSync()
    statusEmpty shouldBe true
    dataEmpty shouldBe true
  }

  it should "provide bracket-style resource management" in {
    val test = for {
      pool       <- RuntimeStatePool.create(initialSize = 2, maxSize = 20)
      sizeBefore <- pool.size
      result <- pool.use { state =>
        IO.pure(state.inUse)
      }
      sizeAfter <- pool.size
    } yield (sizeBefore, result, sizeAfter)

    val (before, inUseInside, after) = test.unsafeRunSync()
    before shouldBe 2
    inUseInside shouldBe true
    after shouldBe 2 // State released back
  }

  it should "track metrics correctly" in {
    val test = for {
      pool <- RuntimeStatePool.create(initialSize = 2, maxSize = 20)
      _    <- pool.acquire // Hit
      _    <- pool.acquire // Hit
      _    <- pool.acquire // Miss (pool empty)
    } yield pool.getMetrics

    val metrics = test.unsafeRunSync()
    metrics.hits shouldBe 2
    metrics.misses shouldBe 1
    metrics.totalAcquires shouldBe 3
  }

  // ============================================================================
  // RuntimePool Tests
  // ============================================================================

  "RuntimePool" should "create with default config" in {
    val test = for {
      pool  <- RuntimePool.create()
      sizes <- pool.sizes
    } yield sizes

    val (deferredSize, stateSize) = test.unsafeRunSync()
    deferredSize shouldBe 100 // Default initial size
    stateSize shouldBe 10     // Default initial size
  }

  it should "create with custom config" in {
    val config = RuntimePool.Config(
      deferredInitialSize = 50,
      deferredMaxSize = 500,
      stateInitialSize = 5,
      stateMaxSize = 25
    )
    val test = for {
      pool  <- RuntimePool.create(config)
      sizes <- pool.sizes
    } yield sizes

    val (deferredSize, stateSize) = test.unsafeRunSync()
    deferredSize shouldBe 50
    stateSize shouldBe 5
  }

  it should "create with high throughput config" in {
    val test = for {
      pool  <- RuntimePool.create(RuntimePool.Config.highThroughput)
      sizes <- pool.sizes
    } yield sizes

    val (deferredSize, stateSize) = test.unsafeRunSync()
    deferredSize shouldBe 500
    stateSize shouldBe 50
  }

  it should "provide combined metrics" in {
    val test = for {
      pool <- RuntimePool.create(
        RuntimePool.Config(
          deferredInitialSize = 5,
          deferredMaxSize = 100,
          stateInitialSize = 2,
          stateMaxSize = 20
        )
      )
      _ <- pool.deferredPool.acquireN(3)
      _ <- pool.statePool.acquire
    } yield pool.getMetrics

    val metrics = test.unsafeRunSync()
    metrics.deferred.hits shouldBe 3
    metrics.state.hits shouldBe 1
    metrics.overallHitRate shouldBe 1.0 +- 0.01 // All hits
  }
}
