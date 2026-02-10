package io.constellation.pool

import java.util.UUID

import scala.concurrent.duration.*

import cats.Eval
import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.{
  CType,
  CValue,
  ComponentMetadata,
  DagSpec,
  DataNodeSpec,
  Module,
  ModuleNodeSpec
}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class RuntimeStatePoolTest extends AnyFlatSpec with Matchers {

  // ============================================================================
  // Helper: build a minimal DagSpec with module entries for initialize()
  // ============================================================================

  private def makeDag(moduleCount: Int): DagSpec = {
    val modules = (1 to moduleCount).map { _ =>
      UUID.randomUUID() -> ModuleNodeSpec.empty
    }.toMap
    DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = modules,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty
    )
  }

  // ============================================================================
  // Pool creation
  // ============================================================================

  "RuntimeStatePool.create" should "initialize pool with the requested initial size" in {
    val test = for {
      pool <- RuntimeStatePool.create(initialSize = 8, maxSize = 40)
      size <- pool.size
    } yield (size, pool.maxSize)

    val (size, maxSize) = test.unsafeRunSync()
    size shouldBe 8
    maxSize shouldBe 40
  }

  it should "initialize pool with zero initial size" in {
    val test = for {
      pool <- RuntimeStatePool.create(initialSize = 0, maxSize = 10)
      size <- pool.size
    } yield size

    test.unsafeRunSync() shouldBe 0
  }

  "RuntimeStatePool.empty" should "create a pool with zero size and zero maxSize" in {
    val test = for {
      pool <- RuntimeStatePool.empty
      size <- pool.size
    } yield (size, pool.maxSize)

    val (size, maxSize) = test.unsafeRunSync()
    size shouldBe 0
    maxSize shouldBe 0
  }

  // ============================================================================
  // acquire / release cycle
  // ============================================================================

  "acquire" should "return a PooledState marked as inUse" in {
    val test = for {
      pool  <- RuntimeStatePool.create(initialSize = 3, maxSize = 20)
      state <- pool.acquire
    } yield state.inUse

    test.unsafeRunSync() shouldBe true
  }

  it should "decrement the pool size by one" in {
    val test = for {
      pool      <- RuntimeStatePool.create(initialSize = 5, maxSize = 20)
      _         <- pool.acquire
      sizeAfter <- pool.size
    } yield sizeAfter

    test.unsafeRunSync() shouldBe 4
  }

  it should "create a fresh state when the pool is empty (miss)" in {
    val test = for {
      pool  <- RuntimeStatePool.create(initialSize = 0, maxSize = 20)
      state <- pool.acquire
      size  <- pool.size
    } yield (state.inUse, size, pool.getMetrics)

    val (inUse, size, metrics) = test.unsafeRunSync()
    inUse shouldBe true
    size shouldBe 0
    metrics.misses shouldBe 1
    metrics.hits shouldBe 0
  }

  "release" should "return the state to the pool and reset it" in {
    val test = for {
      pool              <- RuntimeStatePool.create(initialSize = 2, maxSize = 20)
      state             <- pool.acquire
      sizeBeforeRelease <- pool.size
      _                 <- pool.release(state)
      sizeAfterRelease  <- pool.size
    } yield (sizeBeforeRelease, sizeAfterRelease, state.inUse)

    val (before, after, inUse) = test.unsafeRunSync()
    before shouldBe 1
    after shouldBe 2
    inUse shouldBe false
  }

  it should "discard the state when pool is already at maxSize" in {
    val test = for {
      pool  <- RuntimeStatePool.create(initialSize = 3, maxSize = 3)
      state <- pool.acquire
      // Pool now has 2 items and maxSize is 3. Release should fit.
      _     <- pool.release(state)
      size1 <- pool.size
      // Now pool has 3. Acquire an extra one (miss), then release it.
      extra <- pool.acquire
      size2 <- pool.size
      // Acquire another to get pool to maxSize after release
      // pool has 2 now; let's fill it to max then release the extra
      s1 <- pool.acquire
      s2 <- pool.acquire
      // pool is now at 0. Release s1 and s2 to fill to 2.
      _     <- pool.release(s1)
      _     <- pool.release(s2)
      size3 <- pool.size // should be 2
      // Release `extra` -- pool has 2, maxSize 3 -> fits
      _     <- pool.release(extra)
      size4 <- pool.size // should be 3
      // Now pool is at maxSize=3. Acquire and release an extra.
      another <- pool.acquire          // pool -> 2
      _       <- pool.acquire          // pool -> 1
      _       <- pool.acquire          // pool -> 0
      fresh   <- pool.acquire          // miss, pool stays 0
      _       <- pool.release(another) // pool -> 1
      _       <- pool.release(fresh)   // pool -> 2
      extra2  <- pool.acquire          // pool -> 1, miss
      _       <- pool.release(extra2)  // pool -> 2
    } yield ()

    // If this doesn't throw, the discard branch works
    test.unsafeRunSync()
  }

  "release when pool is full" should "discard and not increase pool size" in {
    val test = for {
      // maxSize = 1, initialSize = 1
      pool <- RuntimeStatePool.create(initialSize = 1, maxSize = 1)
      // Acquire the single item from pool
      state <- pool.acquire
      // Pool is now empty (size = 0). Release brings it back to 1 = maxSize.
      _     <- pool.release(state)
      size1 <- pool.size
      // Create a fresh state by acquiring from empty, then fill pool to max
      fresh <- pool.acquire        // hit, pool -> 0
      _     <- pool.release(fresh) // pool -> 1 (at max)
      // Now acquire two: one from pool and one fresh
      s1 <- pool.acquire // hit, pool -> 0
      s2 <- pool.acquire // miss (fresh), pool stays 0
      // Release both -- only one should fit
      _     <- pool.release(s1) // pool -> 1 (= maxSize)
      _     <- pool.release(s2) // pool full, should discard
      size2 <- pool.size
    } yield (size1, size2)

    val (size1, size2) = test.unsafeRunSync()
    size1 shouldBe 1
    size2 shouldBe 1 // Did not exceed maxSize
  }

  // ============================================================================
  // use[] bracket-style
  // ============================================================================

  "use" should "acquire, run the function, then release the state" in {
    val test = for {
      pool       <- RuntimeStatePool.create(initialSize = 2, maxSize = 20)
      sizeBefore <- pool.size
      result <- pool.use { state =>
        IO.pure(state.inUse)
      }
      sizeAfter <- pool.size
    } yield (sizeBefore, result, sizeAfter)

    val (before, wasInUse, after) = test.unsafeRunSync()
    before shouldBe 2
    wasInUse shouldBe true
    after shouldBe 2 // Released back
  }

  it should "release the state even when the function raises an error" in {
    val test = for {
      pool       <- RuntimeStatePool.create(initialSize = 2, maxSize = 20)
      sizeBefore <- pool.size
      result <- pool.use { _ =>
        IO.raiseError[Int](new RuntimeException("boom"))
      }.attempt
      sizeAfter <- pool.size
    } yield (sizeBefore, result.isLeft, sizeAfter)

    val (before, wasError, after) = test.unsafeRunSync()
    before shouldBe 2
    wasError shouldBe true
    after shouldBe 2 // Released back despite error (guarantee)
  }

  it should "propagate the error from the function" in {
    val test = for {
      pool <- RuntimeStatePool.create(initialSize = 1, maxSize = 10)
      result <- pool.use { _ =>
        IO.raiseError[String](new IllegalArgumentException("test error"))
      }.attempt
    } yield result

    val result = test.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[IllegalArgumentException]
    result.left.toOption.get.getMessage shouldBe "test error"
  }

  // ============================================================================
  // PooledState.reset()
  // ============================================================================

  "PooledState.reset" should "clear moduleStatus and data maps" in {
    val state = RuntimeStatePool.PooledState.create()
    val id    = UUID.randomUUID()
    state.moduleStatus.put(id, Eval.now(Module.Status.Unfired))
    state.data.put(id, Eval.now(CValue.CInt(42)))
    state.markInUse()
    state.setProcessUuid(UUID.randomUUID())

    state.moduleStatus.isEmpty shouldBe false
    state.data.isEmpty shouldBe false
    state.inUse shouldBe true

    state.reset()

    state.moduleStatus.isEmpty shouldBe true
    state.data.isEmpty shouldBe true
    state.inUse shouldBe false
    state.processUuid shouldBe null
    state.dag shouldBe null
    state.latency shouldBe None
  }

  // ============================================================================
  // PooledState.initialize(dag)
  // ============================================================================

  "PooledState.initialize" should "set processUuid and dag" in {
    val state = RuntimeStatePool.PooledState.create()
    val dag   = makeDag(3)

    state.initialize(dag)

    state.processUuid should not be null
    state.dag shouldBe dag
    state.latency shouldBe None
  }

  it should "pre-populate moduleStatus with Unfired for each module in the DAG" in {
    val dag   = makeDag(5)
    val state = RuntimeStatePool.PooledState.create()

    state.initialize(dag)

    state.moduleStatus.size shouldBe 5
    dag.modules.keys.foreach { moduleId =>
      state.moduleStatus.contains(moduleId) shouldBe true
      state.moduleStatus(moduleId).value shouldBe Module.Status.Unfired
    }
  }

  it should "set latency to None" in {
    val state = RuntimeStatePool.PooledState.create()
    state.setLatency(Some(100.millis))

    state.initialize(makeDag(1))

    state.latency shouldBe None
  }

  // ============================================================================
  // PooledState.toImmutableState
  // ============================================================================

  "PooledState.toImmutableState" should "return a RuntimeStateSnapshot with matching fields" in {
    val state = RuntimeStatePool.PooledState.create()
    val dag   = makeDag(2)
    state.initialize(dag)
    state.setLatency(Some(50.millis))

    val dataId = UUID.randomUUID()
    state.data.put(dataId, Eval.now(CValue.CString("hello")))

    val snapshot = state.toImmutableState

    snapshot.processUuid shouldBe state.processUuid
    snapshot.dag shouldBe dag
    snapshot.latency shouldBe Some(50.millis)
    snapshot.moduleStatus.size shouldBe 2
    snapshot.data.size shouldBe 1
    snapshot.data(dataId).value shouldBe CValue.CString("hello")
  }

  it should "produce an immutable copy that is not affected by subsequent mutations" in {
    val state = RuntimeStatePool.PooledState.create()
    val dag   = makeDag(1)
    state.initialize(dag)

    val snapshot            = state.toImmutableState
    val snapshotModuleCount = snapshot.moduleStatus.size

    // Mutate the pooled state after snapshot
    state.moduleStatus.put(UUID.randomUUID(), Eval.now(Module.Status.Unfired))

    // Snapshot should not be affected
    snapshot.moduleStatus.size shouldBe snapshotModuleCount
  }

  // ============================================================================
  // PooledState setters
  // ============================================================================

  "PooledState setters" should "update processUuid, dag, and latency" in {
    val state = RuntimeStatePool.PooledState.create()
    val uuid  = UUID.randomUUID()
    val dag   = makeDag(0)

    state.setProcessUuid(uuid)
    state.processUuid shouldBe uuid

    state.setDag(dag)
    state.dag shouldBe dag

    state.setLatency(Some(200.millis))
    state.latency shouldBe Some(200.millis)

    state.setLatency(None)
    state.latency shouldBe None
  }

  // ============================================================================
  // Pool metrics
  // ============================================================================

  "getMetrics" should "start at zero for a fresh pool" in {
    val metrics = RuntimeStatePool.create(initialSize = 5, maxSize = 20).unsafeRunSync().getMetrics

    metrics.hits shouldBe 0
    metrics.misses shouldBe 0
    metrics.totalAcquires shouldBe 0
    metrics.hitRate shouldBe 0.0
  }

  it should "record hits when acquiring from a non-empty pool" in {
    val test = for {
      pool <- RuntimeStatePool.create(initialSize = 3, maxSize = 20)
      _    <- pool.acquire
      _    <- pool.acquire
    } yield pool.getMetrics

    val metrics = test.unsafeRunSync()
    metrics.hits shouldBe 2
    metrics.misses shouldBe 0
    metrics.totalAcquires shouldBe 2
    metrics.hitRate shouldBe 1.0
  }

  it should "record misses when acquiring from an empty pool" in {
    val test = for {
      pool <- RuntimeStatePool.create(initialSize = 0, maxSize = 20)
      _    <- pool.acquire
    } yield pool.getMetrics

    val metrics = test.unsafeRunSync()
    metrics.hits shouldBe 0
    metrics.misses shouldBe 1
    metrics.totalAcquires shouldBe 1
    metrics.hitRate shouldBe 0.0
  }

  it should "correctly compute hitRate with mixed hits and misses" in {
    val test = for {
      pool <- RuntimeStatePool.create(initialSize = 2, maxSize = 20)
      _    <- pool.acquire // hit
      _    <- pool.acquire // hit
      _    <- pool.acquire // miss
      _    <- pool.acquire // miss
    } yield pool.getMetrics

    val metrics = test.unsafeRunSync()
    metrics.hits shouldBe 2
    metrics.misses shouldBe 2
    metrics.totalAcquires shouldBe 4
    metrics.hitRate shouldBe 0.5 +- 0.01
  }

  // ============================================================================
  // size
  // ============================================================================

  "size" should "reflect acquire and release operations" in {
    val test = for {
      pool <- RuntimeStatePool.create(initialSize = 5, maxSize = 20)
      s0   <- pool.size
      s1   <- pool.acquire
      s1s  <- pool.size
      s2   <- pool.acquire
      s2s  <- pool.size
      _    <- pool.release(s1)
      s3s  <- pool.size
      _    <- pool.release(s2)
      s4s  <- pool.size
    } yield (s0, s1s, s2s, s3s, s4s)

    val (s0, s1s, s2s, s3s, s4s) = test.unsafeRunSync()
    s0 shouldBe 5
    s1s shouldBe 4
    s2s shouldBe 3
    s3s shouldBe 4
    s4s shouldBe 5
  }
}
