package io.constellation

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.impl.{ConstellationImpl, InMemorySuspensionStore}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

/** Integration tests for the full suspend -> store -> resumeFromStore lifecycle (Phase 4). */
class ResumeFromStoreTest extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // Shared fixture: text -> Uppercase -> result
  // (using same name "text" for both input name and module parameter)
  // ---------------------------------------------------------------------------

  case class TextIn(text: String)
  case class TextOut(result: String)

  private val uppercaseModule: Module.Uninitialized = ModuleBuilder
    .metadata("Uppercase", "Uppercase", 1, 0)
    .implementationPure[TextIn, TextOut](in => TextOut(in.text.toUpperCase))
    .build

  private val moduleId     = UUID.randomUUID()
  private val inputDataId  = UUID.randomUUID()
  private val outputDataId = UUID.randomUUID()

  private val dag = DagSpec(
    metadata = ComponentMetadata.empty("TestDag"),
    modules = Map(
      moduleId -> ModuleNodeSpec(
        metadata = ComponentMetadata("Uppercase", "Uppercase", List.empty, 1, 0),
        consumes = Map("text" -> CType.CString),
        produces = Map("result" -> CType.CString)
      )
    ),
    data = Map(
      inputDataId -> DataNodeSpec(
        "text",
        Map(inputDataId -> "text", moduleId -> "text"),
        CType.CString
      ),
      outputDataId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CString)
    ),
    inEdges = Set((inputDataId, moduleId)),
    outEdges = Set((moduleId, outputDataId)),
    declaredOutputs = List("result"),
    outputBindings = Map("result" -> outputDataId)
  )

  private val image = PipelineImage(
    structuralHash = "test-hash-abc",
    syntacticHash = "test-syntactic",
    dagSpec = dag,
    moduleOptions = Map.empty,
    compiledAt = java.time.Instant.now()
  )

  /** Create a suspended execution snapshot (simulating partial execution). */
  private def mkSuspended: SuspendedExecution = SuspendedExecution(
    executionId = UUID.randomUUID(),
    structuralHash = image.structuralHash,
    resumptionCount = 0,
    dagSpec = dag,
    moduleOptions = Map.empty,
    providedInputs = Map.empty,
    computedValues = Map.empty,
    moduleStatuses = Map.empty
  )

  // ---------------------------------------------------------------------------
  // Full lifecycle: save suspended -> resumeFromStore with input -> Completed
  // ---------------------------------------------------------------------------

  "resumeFromStore" should "complete a suspended pipeline after saving and reloading" in {
    val result = (for {
      suspensionStore <- InMemorySuspensionStore.init
      constellation <- ConstellationImpl
        .builder()
        .withSuspensionStore(suspensionStore)
        .build()
      _ <- constellation.setModule(uppercaseModule)

      // Save a suspended execution to the store
      handle <- suspensionStore.save(mkSuspended)

      // Resume from store with the missing input
      sig <- constellation.resumeFromStore(
        handle = handle,
        additionalInputs = Map("text" -> CValue.CString("hello"))
      )
    } yield sig).unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs should contain key "result"
    result.outputs("result") shouldBe CValue.CString("HELLO")
    result.resumptionCount shouldBe 1
  }

  // ---------------------------------------------------------------------------
  // Error: no SuspensionStore configured
  // ---------------------------------------------------------------------------

  it should "fail with IllegalStateException when no SuspensionStore is configured" in {
    val error = intercept[IllegalStateException] {
      (for {
        constellation <- ConstellationImpl.init
        _             <- constellation.resumeFromStore(SuspensionHandle("any"))
      } yield ()).unsafeRunSync()
    }

    error.getMessage should include("No SuspensionStore configured")
  }

  // ---------------------------------------------------------------------------
  // Error: unknown handle
  // ---------------------------------------------------------------------------

  it should "fail with NoSuchElementException for unknown handle" in {
    val error = intercept[NoSuchElementException] {
      (for {
        suspensionStore <- InMemorySuspensionStore.init
        constellation <- ConstellationImpl
          .builder()
          .withSuspensionStore(suspensionStore)
          .build()
        _ <- constellation.resumeFromStore(SuspensionHandle("nonexistent"))
      } yield ()).unsafeRunSync()
    }

    error.getMessage should include("Suspension not found")
  }

  // ---------------------------------------------------------------------------
  // Verify suspensionStore accessor
  // ---------------------------------------------------------------------------

  it should "expose suspensionStore via the trait accessor" in {
    val (withStore, withoutStore) = (for {
      ss <- InMemorySuspensionStore.init
      c1 <- ConstellationImpl.builder().withSuspensionStore(ss).build()
      c2 <- ConstellationImpl.init
    } yield (c1.suspensionStore, c2.suspensionStore)).unsafeRunSync()

    withStore shouldBe defined
    withoutStore shouldBe None
  }

  // ---------------------------------------------------------------------------
  // Verify SuspensionStore.list after save
  // ---------------------------------------------------------------------------

  it should "list the saved suspension with correct summary" in {
    val result = (for {
      suspensionStore <- InMemorySuspensionStore.init
      _               <- suspensionStore.save(mkSuspended)
      summaries       <- suspensionStore.list()
    } yield summaries).unsafeRunSync()

    result should have size 1
    val summary = result.head
    summary.structuralHash shouldBe "test-hash-abc"
    summary.missingInputs should contain key "text"
    summary.missingInputs("text") shouldBe CType.CString
  }

  // ---------------------------------------------------------------------------
  // Resume with metadata flags
  // ---------------------------------------------------------------------------

  it should "include metadata when flags are set during resumeFromStore" in {
    val result = (for {
      suspensionStore <- InMemorySuspensionStore.init
      constellation <- ConstellationImpl
        .builder()
        .withSuspensionStore(suspensionStore)
        .build()
      _ <- constellation.setModule(uppercaseModule)

      handle <- suspensionStore.save(mkSuspended)

      sig <- constellation.resumeFromStore(
        handle = handle,
        additionalInputs = Map("text" -> CValue.CString("world")),
        options = ExecutionOptions(includeTimings = true, includeProvenance = true)
      )
    } yield sig).unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.metadata.nodeTimings shouldBe defined
    result.metadata.provenance shouldBe defined
    result.metadata.provenance.get("result") shouldBe "Uppercase"
  }
}
