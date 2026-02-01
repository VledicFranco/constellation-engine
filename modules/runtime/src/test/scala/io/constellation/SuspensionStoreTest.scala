package io.constellation

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import io.constellation.impl.InMemorySuspensionStore
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class SuspensionStoreTest extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // Test helpers
  // ---------------------------------------------------------------------------

  private def mkStore: SuspensionStore =
    InMemorySuspensionStore.init.unsafeRunSync()

  private def mkStoreWithCodec: SuspensionStore =
    InMemorySuspensionStore.initWithCodecValidation(CirceJsonSuspensionCodec).unsafeRunSync()

  private def mkSuspended(
      hash: String = "test-hash-123",
      inputs: Map[String, CValue] = Map("text" -> CValue.CString("hello")),
      resumptionCount: Int = 0
  ): SuspendedExecution = SuspendedExecution(
    executionId = UUID.randomUUID(),
    structuralHash = hash,
    resumptionCount = resumptionCount,
    dagSpec = mkSimpleDag,
    moduleOptions = Map.empty,
    providedInputs = inputs,
    computedValues = Map.empty,
    moduleStatuses = Map.empty
  )

  private val inputDataId = UUID.randomUUID()
  private val moduleId = UUID.randomUUID()
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
      inputDataId -> DataNodeSpec("text", Map(moduleId -> "text"), CType.CString),
      outputDataId -> DataNodeSpec("result", Map.empty, CType.CString)
    ),
    inEdges = Set((inputDataId, moduleId)),
    outEdges = Set((moduleId, outputDataId)),
    declaredOutputs = List("result")
  )

  // ---------------------------------------------------------------------------
  // save / load round-trip
  // ---------------------------------------------------------------------------

  "SuspensionStore" should "save and load a suspended execution" in {
    val store = mkStore
    val suspended = mkSuspended()

    val result = (for {
      handle <- store.save(suspended)
      loaded <- store.load(handle)
    } yield loaded).unsafeRunSync()

    result shouldBe defined
    result.get.executionId shouldBe suspended.executionId
    result.get.structuralHash shouldBe suspended.structuralHash
    result.get.providedInputs shouldBe suspended.providedInputs
  }

  // ---------------------------------------------------------------------------
  // unknown handle
  // ---------------------------------------------------------------------------

  it should "return None for unknown handle" in {
    val store = mkStore

    val result = store.load(SuspensionHandle("nonexistent")).unsafeRunSync()
    result shouldBe None
  }

  // ---------------------------------------------------------------------------
  // delete
  // ---------------------------------------------------------------------------

  it should "delete a stored suspension" in {
    val store = mkStore
    val suspended = mkSuspended()

    val result = (for {
      handle  <- store.save(suspended)
      deleted <- store.delete(handle)
      loaded  <- store.load(handle)
    } yield (deleted, loaded)).unsafeRunSync()

    result._1 shouldBe true
    result._2 shouldBe None
  }

  it should "return false when deleting unknown handle" in {
    val store = mkStore

    val deleted = store.delete(SuspensionHandle("nonexistent")).unsafeRunSync()
    deleted shouldBe false
  }

  // ---------------------------------------------------------------------------
  // list all
  // ---------------------------------------------------------------------------

  it should "list all stored suspensions" in {
    val store = mkStore
    val s1 = mkSuspended(hash = "hash-1")
    val s2 = mkSuspended(hash = "hash-2")

    val result = (for {
      _ <- store.save(s1)
      _ <- store.save(s2)
      all <- store.list()
    } yield all).unsafeRunSync()

    result should have size 2
    result.map(_.structuralHash).toSet shouldBe Set("hash-1", "hash-2")
  }

  // ---------------------------------------------------------------------------
  // filter by structuralHash
  // ---------------------------------------------------------------------------

  it should "filter by structural hash" in {
    val store = mkStore

    val result = (for {
      _ <- store.save(mkSuspended(hash = "hash-A"))
      _ <- store.save(mkSuspended(hash = "hash-B"))
      _ <- store.save(mkSuspended(hash = "hash-A"))
      filtered <- store.list(SuspensionFilter(structuralHash = Some("hash-A")))
    } yield filtered).unsafeRunSync()

    result should have size 2
    result.foreach(_.structuralHash shouldBe "hash-A")
  }

  // ---------------------------------------------------------------------------
  // filter by executionId
  // ---------------------------------------------------------------------------

  it should "filter by execution ID" in {
    val store = mkStore
    val s1 = mkSuspended()
    val s2 = mkSuspended()

    val result = (for {
      _ <- store.save(s1)
      _ <- store.save(s2)
      filtered <- store.list(SuspensionFilter(executionId = Some(s1.executionId)))
    } yield filtered).unsafeRunSync()

    result should have size 1
    result.head.executionId shouldBe s1.executionId
  }

  // ---------------------------------------------------------------------------
  // filter by resumptionCount
  // ---------------------------------------------------------------------------

  it should "filter by min/max resumption count" in {
    val store = mkStore

    val result = (for {
      _ <- store.save(mkSuspended(resumptionCount = 0))
      _ <- store.save(mkSuspended(resumptionCount = 2))
      _ <- store.save(mkSuspended(resumptionCount = 5))
      min2 <- store.list(SuspensionFilter(minResumptionCount = Some(2)))
      max2 <- store.list(SuspensionFilter(maxResumptionCount = Some(2)))
      range <- store.list(SuspensionFilter(minResumptionCount = Some(1), maxResumptionCount = Some(3)))
    } yield (min2, max2, range)).unsafeRunSync()

    result._1 should have size 2 // count 2 and 5
    result._2 should have size 2 // count 0 and 2
    result._3 should have size 1 // count 2 only
  }

  // ---------------------------------------------------------------------------
  // SuspensionSummary.missingInputs
  // ---------------------------------------------------------------------------

  it should "compute missingInputs in SuspensionSummary" in {
    val store = mkStore
    // Save a suspension with no inputs provided
    val suspended = mkSuspended(inputs = Map.empty)

    val result = (for {
      _ <- store.save(suspended)
      summaries <- store.list()
    } yield summaries).unsafeRunSync()

    result should have size 1
    val summary = result.head
    summary.missingInputs should contain key "text"
    summary.missingInputs("text") shouldBe CType.CString
  }

  it should "report empty missingInputs when all inputs are provided" in {
    val store = mkStore
    val suspended = mkSuspended(inputs = Map("text" -> CValue.CString("hello")))

    val result = (for {
      _ <- store.save(suspended)
      summaries <- store.list()
    } yield summaries).unsafeRunSync()

    result.head.missingInputs shouldBe empty
  }

  // ---------------------------------------------------------------------------
  // codec validation
  // ---------------------------------------------------------------------------

  it should "round-trip through codec when validation is enabled" in {
    val store = mkStoreWithCodec
    val suspended = mkSuspended()

    val result = (for {
      handle <- store.save(suspended)
      loaded <- store.load(handle)
    } yield loaded).unsafeRunSync()

    result shouldBe defined
    result.get.executionId shouldBe suspended.executionId
  }

  // ---------------------------------------------------------------------------
  // concurrent saves
  // ---------------------------------------------------------------------------

  it should "handle concurrent saves without data loss" in {
    val store = mkStore
    val suspensions = (1 to 20).map(i => mkSuspended(hash = s"hash-$i")).toList

    val result = (for {
      // Save all concurrently
      _ <- suspensions.parTraverse(store.save)
      all <- store.list()
    } yield all).unsafeRunSync()

    result should have size 20
  }
}
