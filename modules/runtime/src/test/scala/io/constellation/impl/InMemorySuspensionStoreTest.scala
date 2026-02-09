package io.constellation.impl

import java.util.UUID

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InMemorySuspensionStoreTest extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // Test helpers
  // ---------------------------------------------------------------------------

  private val inputDataId  = UUID.randomUUID()
  private val moduleId     = UUID.randomUUID()
  private val outputDataId = UUID.randomUUID()

  private def mkSimpleDag: DagSpec = DagSpec(
    metadata = ComponentMetadata.empty("TestDag"),
    modules = Map(
      moduleId -> ModuleNodeSpec(
        metadata = ComponentMetadata("TestModule", "Test", List.empty, 1, 0),
        consumes = Map("text" -> CType.CString),
        produces = Map("result" -> CType.CString)
      )
    ),
    data = Map(
      inputDataId  -> DataNodeSpec("text", Map(moduleId -> "text"), CType.CString),
      outputDataId -> DataNodeSpec("result", Map.empty, CType.CString)
    ),
    inEdges = Set((inputDataId, moduleId)),
    outEdges = Set((moduleId, outputDataId)),
    declaredOutputs = List("result")
  )

  private def mkSuspended(
      hash: String = "hash",
      resumptionCount: Int = 0,
      inputs: Map[String, CValue] = Map.empty,
      computed: Map[UUID, CValue] = Map.empty,
      statuses: Map[UUID, String] = Map.empty
  ): SuspendedExecution = SuspendedExecution(
    executionId = UUID.randomUUID(),
    structuralHash = hash,
    resumptionCount = resumptionCount,
    dagSpec = mkSimpleDag,
    moduleOptions = Map.empty,
    providedInputs = inputs,
    computedValues = computed,
    moduleStatuses = statuses
  )

  // ---------------------------------------------------------------------------
  // 1. InMemorySuspensionStore.init creates an empty store
  // ---------------------------------------------------------------------------

  "InMemorySuspensionStore.init" should "create an empty store" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()

    val all = store.list().unsafeRunSync()
    all shouldBe empty
  }

  // ---------------------------------------------------------------------------
  // 2. save and load - stores and retrieves suspended execution
  // ---------------------------------------------------------------------------

  "save and load" should "store and retrieve a suspended execution by handle" in {
    val store     = InMemorySuspensionStore.init.unsafeRunSync()
    val suspended = mkSuspended()

    val result = (for {
      handle <- store.save(suspended)
      loaded <- store.load(handle)
    } yield loaded).unsafeRunSync()

    result shouldBe defined
    result.get.executionId shouldBe suspended.executionId
    result.get.structuralHash shouldBe suspended.structuralHash
    result.get.resumptionCount shouldBe suspended.resumptionCount
    result.get.dagSpec.name shouldBe "TestDag"
    result.get.moduleOptions shouldBe Map.empty
    result.get.providedInputs shouldBe suspended.providedInputs
    result.get.computedValues shouldBe suspended.computedValues
    result.get.moduleStatuses shouldBe suspended.moduleStatuses
  }

  it should "preserve all fields of the suspended execution" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()
    val computedId = UUID.randomUUID()
    val suspended = mkSuspended(
      hash = "specific-hash",
      resumptionCount = 3,
      inputs = Map("text" -> CValue.CString("hello")),
      computed = Map(computedId -> CValue.CString("computed-value")),
      statuses = Map(moduleId -> "completed")
    )

    val result = (for {
      handle <- store.save(suspended)
      loaded <- store.load(handle)
    } yield loaded).unsafeRunSync()

    result shouldBe defined
    val loaded = result.get
    loaded.structuralHash shouldBe "specific-hash"
    loaded.resumptionCount shouldBe 3
    loaded.providedInputs shouldBe Map("text" -> CValue.CString("hello"))
    loaded.computedValues shouldBe Map(computedId -> CValue.CString("computed-value"))
    loaded.moduleStatuses shouldBe Map(moduleId -> "completed")
  }

  // ---------------------------------------------------------------------------
  // 3. save and list - lists all stored executions
  // ---------------------------------------------------------------------------

  "save and list" should "list all stored executions" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()
    val s1    = mkSuspended(hash = "hash-1")
    val s2    = mkSuspended(hash = "hash-2")
    val s3    = mkSuspended(hash = "hash-3")

    val result = (for {
      _ <- store.save(s1)
      _ <- store.save(s2)
      _ <- store.save(s3)
      all <- store.list()
    } yield all).unsafeRunSync()

    result should have size 3
    result.map(_.structuralHash).toSet shouldBe Set("hash-1", "hash-2", "hash-3")
  }

  it should "return summaries with correct execution IDs" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()
    val s1    = mkSuspended()
    val s2    = mkSuspended()

    val result = (for {
      _ <- store.save(s1)
      _ <- store.save(s2)
      all <- store.list()
    } yield all).unsafeRunSync()

    result.map(_.executionId).toSet shouldBe Set(s1.executionId, s2.executionId)
  }

  // ---------------------------------------------------------------------------
  // 4. save and delete - deletes by handle
  // ---------------------------------------------------------------------------

  "save and delete" should "delete a stored execution by handle" in {
    val store     = InMemorySuspensionStore.init.unsafeRunSync()
    val suspended = mkSuspended()

    val result = (for {
      handle  <- store.save(suspended)
      deleted <- store.delete(handle)
      loaded  <- store.load(handle)
      all     <- store.list()
    } yield (deleted, loaded, all)).unsafeRunSync()

    result._1 shouldBe true
    result._2 shouldBe None
    result._3 shouldBe empty
  }

  it should "only delete the specified entry, leaving others intact" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()
    val s1    = mkSuspended(hash = "keep-me")
    val s2    = mkSuspended(hash = "delete-me")

    val result = (for {
      h1      <- store.save(s1)
      h2      <- store.save(s2)
      deleted <- store.delete(h2)
      remaining <- store.list()
      kept    <- store.load(h1)
      gone    <- store.load(h2)
    } yield (deleted, remaining, kept, gone)).unsafeRunSync()

    result._1 shouldBe true
    result._2 should have size 1
    result._2.head.structuralHash shouldBe "keep-me"
    result._3 shouldBe defined
    result._4 shouldBe None
  }

  // ---------------------------------------------------------------------------
  // 5. load returns None for unknown handle
  // ---------------------------------------------------------------------------

  "load" should "return None for unknown handle" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()

    val result = store.load(SuspensionHandle("nonexistent-id")).unsafeRunSync()
    result shouldBe None
  }

  it should "return None for a UUID-like handle that was never saved" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()

    val result = store.load(SuspensionHandle(UUID.randomUUID().toString)).unsafeRunSync()
    result shouldBe None
  }

  // ---------------------------------------------------------------------------
  // 6. delete returns false for unknown handle
  // ---------------------------------------------------------------------------

  "delete" should "return false for unknown handle" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()

    val deleted = store.delete(SuspensionHandle("nonexistent-id")).unsafeRunSync()
    deleted shouldBe false
  }

  it should "return false for an already-deleted handle" in {
    val store     = InMemorySuspensionStore.init.unsafeRunSync()
    val suspended = mkSuspended()

    val result = (for {
      handle       <- store.save(suspended)
      firstDelete  <- store.delete(handle)
      secondDelete <- store.delete(handle)
    } yield (firstDelete, secondDelete)).unsafeRunSync()

    result._1 shouldBe true
    result._2 shouldBe false
  }

  // ---------------------------------------------------------------------------
  // 7. save replaces existing execution with same handle
  //    (Note: InMemorySuspensionStore generates a new handle per save,
  //     so each save creates a new entry. Two saves of the same
  //     SuspendedExecution produce two distinct handles.)
  // ---------------------------------------------------------------------------

  "save" should "produce unique handles for each save call" in {
    val store     = InMemorySuspensionStore.init.unsafeRunSync()
    val suspended = mkSuspended()

    val (h1, h2) = (for {
      handle1 <- store.save(suspended)
      handle2 <- store.save(suspended)
    } yield (handle1, handle2)).unsafeRunSync()

    h1.id should not be h2.id
  }

  it should "store multiple entries when saving the same execution twice" in {
    val store     = InMemorySuspensionStore.init.unsafeRunSync()
    val suspended = mkSuspended()

    val result = (for {
      _ <- store.save(suspended)
      _ <- store.save(suspended)
      all <- store.list()
    } yield all).unsafeRunSync()

    result should have size 2
    result.map(_.executionId).toSet should have size 1
  }

  // ---------------------------------------------------------------------------
  // 8. list returns empty list when empty
  // ---------------------------------------------------------------------------

  "list" should "return empty list when store is empty" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()

    val result = store.list().unsafeRunSync()
    result shouldBe empty
  }

  it should "return empty list when no entries match the filter" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()

    val result = (for {
      _        <- store.save(mkSuspended(hash = "hash-A"))
      filtered <- store.list(SuspensionFilter(structuralHash = Some("hash-B")))
    } yield filtered).unsafeRunSync()

    result shouldBe empty
  }

  // ---------------------------------------------------------------------------
  // 9. TTL-based eviction (closest equivalent to "clear")
  // ---------------------------------------------------------------------------

  "initWithTTL" should "evict entries older than the TTL on save" in {
    // Use a very short TTL so entries expire quickly
    val store = InMemorySuspensionStore.initWithTTL(1.millisecond).unsafeRunSync()

    val result = (for {
      _   <- store.save(mkSuspended(hash = "old-entry"))
      // Sleep to let the entry expire
      _   <- IO.sleep(50.milliseconds)
      // Saving triggers eviction
      _   <- store.save(mkSuspended(hash = "new-entry"))
      all <- store.list()
    } yield all).unsafeRunSync()

    // The old entry should have been evicted; only the new entry should remain
    // (The new entry may also be evicted depending on timing, so we check that
    //  old entry is gone)
    result.map(_.structuralHash) should not contain "old-entry"
  }

  it should "evict entries older than the TTL on load" in {
    val store = InMemorySuspensionStore.initWithTTL(1.millisecond).unsafeRunSync()

    val result = (for {
      handle <- store.save(mkSuspended())
      _      <- IO.sleep(50.milliseconds)
      loaded <- store.load(handle)
    } yield loaded).unsafeRunSync()

    result shouldBe None
  }

  it should "evict entries older than the TTL on list" in {
    val store = InMemorySuspensionStore.initWithTTL(1.millisecond).unsafeRunSync()

    val result = (for {
      _ <- store.save(mkSuspended())
      _ <- store.save(mkSuspended())
      _ <- IO.sleep(50.milliseconds)
      all <- store.list()
    } yield all).unsafeRunSync()

    result shouldBe empty
  }

  it should "keep entries within the TTL" in {
    val store = InMemorySuspensionStore.initWithTTL(10.seconds).unsafeRunSync()

    val result = (for {
      handle <- store.save(mkSuspended(hash = "still-alive"))
      loaded <- store.load(handle)
      all    <- store.list()
    } yield (loaded, all)).unsafeRunSync()

    result._1 shouldBe defined
    result._2 should have size 1
  }

  // ---------------------------------------------------------------------------
  // 10. count via list size (no dedicated count method)
  // ---------------------------------------------------------------------------

  "list size" should "reflect the correct count of stored entries" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()

    val counts = (for {
      count0 <- store.list().map(_.size)
      _      <- store.save(mkSuspended())
      count1 <- store.list().map(_.size)
      _      <- store.save(mkSuspended())
      count2 <- store.list().map(_.size)
      _      <- store.save(mkSuspended())
      count3 <- store.list().map(_.size)
    } yield (count0, count1, count2, count3)).unsafeRunSync()

    counts shouldBe (0, 1, 2, 3)
  }

  it should "decrease after delete" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()

    val counts = (for {
      h1     <- store.save(mkSuspended())
      h2     <- store.save(mkSuspended())
      _      <- store.save(mkSuspended())
      count3 <- store.list().map(_.size)
      _      <- store.delete(h1)
      count2 <- store.list().map(_.size)
      _      <- store.delete(h2)
      count1 <- store.list().map(_.size)
    } yield (count3, count2, count1)).unsafeRunSync()

    counts shouldBe (3, 2, 1)
  }

  // ---------------------------------------------------------------------------
  // 11. Multiple stored executions
  // ---------------------------------------------------------------------------

  "multiple stored executions" should "be independently loadable by their handles" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()
    val s1    = mkSuspended(hash = "hash-1")
    val s2    = mkSuspended(hash = "hash-2")
    val s3    = mkSuspended(hash = "hash-3")

    val result = (for {
      h1 <- store.save(s1)
      h2 <- store.save(s2)
      h3 <- store.save(s3)
      l1 <- store.load(h1)
      l2 <- store.load(h2)
      l3 <- store.load(h3)
    } yield (l1, l2, l3)).unsafeRunSync()

    result._1.get.structuralHash shouldBe "hash-1"
    result._2.get.structuralHash shouldBe "hash-2"
    result._3.get.structuralHash shouldBe "hash-3"
  }

  it should "be independently deletable" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()
    val s1    = mkSuspended(hash = "hash-1")
    val s2    = mkSuspended(hash = "hash-2")
    val s3    = mkSuspended(hash = "hash-3")

    val result = (for {
      h1 <- store.save(s1)
      h2 <- store.save(s2)
      h3 <- store.save(s3)
      _  <- store.delete(h2)
      l1 <- store.load(h1)
      l2 <- store.load(h2)
      l3 <- store.load(h3)
      all <- store.list()
    } yield (l1, l2, l3, all)).unsafeRunSync()

    result._1 shouldBe defined
    result._2 shouldBe None
    result._3 shouldBe defined
    result._4 should have size 2
    result._4.map(_.structuralHash).toSet shouldBe Set("hash-1", "hash-3")
  }

  it should "support filtering across multiple entries" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()

    val result = (for {
      _ <- store.save(mkSuspended(hash = "pipeline-A", resumptionCount = 0))
      _ <- store.save(mkSuspended(hash = "pipeline-A", resumptionCount = 1))
      _ <- store.save(mkSuspended(hash = "pipeline-B", resumptionCount = 0))
      _ <- store.save(mkSuspended(hash = "pipeline-B", resumptionCount = 2))
      _ <- store.save(mkSuspended(hash = "pipeline-C", resumptionCount = 5))
      allA <- store.list(SuspensionFilter(structuralHash = Some("pipeline-A")))
      allB <- store.list(SuspensionFilter(structuralHash = Some("pipeline-B")))
      highResumption <- store.list(SuspensionFilter(minResumptionCount = Some(2)))
      combined <- store.list(SuspensionFilter(
        structuralHash = Some("pipeline-B"),
        maxResumptionCount = Some(1)
      ))
    } yield (allA, allB, highResumption, combined)).unsafeRunSync()

    result._1 should have size 2
    result._2 should have size 2
    result._3 should have size 2 // resumption counts 2 and 5
    result._4 should have size 1 // pipeline-B with count 0
  }

  // ---------------------------------------------------------------------------
  // SuspensionSummary fields
  // ---------------------------------------------------------------------------

  "SuspensionSummary" should "contain correct handle, executionId, and structuralHash" in {
    val store     = InMemorySuspensionStore.init.unsafeRunSync()
    val suspended = mkSuspended(hash = "summary-hash", resumptionCount = 7)

    val result = (for {
      handle  <- store.save(suspended)
      summaries <- store.list()
    } yield (handle, summaries)).unsafeRunSync()

    val (handle, summaries) = result
    summaries should have size 1
    val summary = summaries.head
    summary.handle shouldBe handle
    summary.executionId shouldBe suspended.executionId
    summary.structuralHash shouldBe "summary-hash"
    summary.resumptionCount shouldBe 7
  }

  it should "include createdAt timestamp" in {
    val store     = InMemorySuspensionStore.init.unsafeRunSync()
    val suspended = mkSuspended()

    val summaries = (for {
      _ <- store.save(suspended)
      all <- store.list()
    } yield all).unsafeRunSync()

    summaries.head.createdAt should not be null
  }

  // ---------------------------------------------------------------------------
  // Codec validation store
  // ---------------------------------------------------------------------------

  "initWithCodecValidation" should "create a store that validates on save" in {
    val store = InMemorySuspensionStore
      .initWithCodecValidation(CirceJsonSuspensionCodec)
      .unsafeRunSync()
    val suspended = mkSuspended(
      inputs = Map("text" -> CValue.CString("validated")),
      computed = Map(UUID.randomUUID() -> CValue.CInt(42))
    )

    val result = (for {
      handle <- store.save(suspended)
      loaded <- store.load(handle)
    } yield loaded).unsafeRunSync()

    result shouldBe defined
    result.get.executionId shouldBe suspended.executionId
    result.get.providedInputs shouldBe Map("text" -> CValue.CString("validated"))
  }

  // ---------------------------------------------------------------------------
  // Concurrent operations
  // ---------------------------------------------------------------------------

  "concurrent operations" should "handle concurrent saves without data loss" in {
    val store       = InMemorySuspensionStore.init.unsafeRunSync()
    val suspensions = (1 to 50).map(i => mkSuspended(hash = s"concurrent-$i")).toList

    val result = (for {
      _   <- suspensions.parTraverse(store.save)
      all <- store.list()
    } yield all).unsafeRunSync()

    result should have size 50
  }

  it should "handle concurrent deletes correctly" in {
    val store       = InMemorySuspensionStore.init.unsafeRunSync()
    val suspensions = (1 to 20).map(i => mkSuspended(hash = s"del-$i")).toList

    val result = (for {
      handles <- suspensions.traverse(store.save)
      results <- handles.parTraverse(store.delete)
      all     <- store.list()
    } yield (results, all)).unsafeRunSync()

    result._1 should have size 20
    result._1.forall(_ == true) shouldBe true
    result._2 shouldBe empty
  }

  it should "handle interleaved saves and loads" in {
    val store = InMemorySuspensionStore.init.unsafeRunSync()

    val result = (for {
      h1 <- store.save(mkSuspended(hash = "interleaved-1"))
      l1 <- store.load(h1)
      h2 <- store.save(mkSuspended(hash = "interleaved-2"))
      l2 <- store.load(h2)
      l1Again <- store.load(h1)
      all <- store.list()
    } yield (l1, l2, l1Again, all)).unsafeRunSync()

    result._1.get.structuralHash shouldBe "interleaved-1"
    result._2.get.structuralHash shouldBe "interleaved-2"
    result._3.get.structuralHash shouldBe "interleaved-1"
    result._4 should have size 2
  }
}
