package io.constellation.impl

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.execution.GlobalScheduler
import io.constellation.spi.ConstellationBackends

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConstellationImplTest extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // Test helpers
  // ---------------------------------------------------------------------------

  case class TestInput(text: String)
  case class TestOutput(result: String)

  private def createTestModule(name: String): Module.Uninitialized =
    ModuleBuilder
      .metadata(name, s"Test module $name", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.text.toUpperCase))
      .build

  /** Simple in-memory mock of SuspensionStore for tests that only need the interface. */
  private class MockSuspensionStore extends SuspensionStore {
    override def save(suspended: SuspendedExecution): IO[SuspensionHandle] =
      IO.pure(SuspensionHandle(suspended.executionId.toString))

    override def load(handle: SuspensionHandle): IO[Option[SuspendedExecution]] =
      IO.pure(None)

    override def delete(handle: SuspensionHandle): IO[Boolean] =
      IO.pure(true)

    override def list(filter: SuspensionFilter): IO[List[SuspensionSummary]] =
      IO.pure(List.empty)
  }

  // =========================================================================
  // ConstellationImpl.init
  // =========================================================================

  "ConstellationImpl.init" should "create an instance successfully" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation shouldBe a[ConstellationImpl]
  }

  it should "return empty module list initially" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val modules       = constellation.getModules.unsafeRunSync()
    modules shouldBe empty
  }

  it should "return None for getModuleByName on unknown module" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val result        = constellation.getModuleByName("UnknownModule").unsafeRunSync()
    result shouldBe None
  }

  // =========================================================================
  // setModule / getModules / getModuleByName
  // =========================================================================

  "setModule" should "register a module successfully" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val module        = createTestModule("Alpha")

    constellation.setModule(module).unsafeRunSync()

    val modules = constellation.getModules.unsafeRunSync()
    modules.map(_.name) should contain("Alpha")
  }

  "getModules" should "list all registered modules" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    constellation.setModule(createTestModule("ModuleA")).unsafeRunSync()
    constellation.setModule(createTestModule("ModuleB")).unsafeRunSync()
    constellation.setModule(createTestModule("ModuleC")).unsafeRunSync()

    val modules = constellation.getModules.unsafeRunSync()
    modules should have size 3
    modules.map(_.name) should contain allOf ("ModuleA", "ModuleB", "ModuleC")
  }

  "getModuleByName" should "return Some for a registered module" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("FindMe")).unsafeRunSync()

    val result = constellation.getModuleByName("FindMe").unsafeRunSync()
    result shouldBe defined
    result.get.spec.name shouldBe "FindMe"
  }

  it should "return None for an unregistered module name" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("Exists")).unsafeRunSync()

    val result = constellation.getModuleByName("DoesNotExist").unsafeRunSync()
    result shouldBe None
  }

  // =========================================================================
  // PipelineStore accessor
  // =========================================================================

  "PipelineStore" should "be accessible from a ConstellationImpl instance" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val store         = constellation.PipelineStore
    store should not be null
  }

  it should "allow storing and retrieving pipeline images" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val store         = constellation.PipelineStore

    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("StoreDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("TestMod", "Test", List.empty, 1, 0),
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

    val image = PipelineImage(
      structuralHash = "test-hash-123",
      syntacticHash = "syn-hash-456",
      dagSpec = dag,
      moduleOptions = Map.empty,
      compiledAt = Instant.now()
    )

    val hash      = store.store(image).unsafeRunSync()
    val retrieved = store.get(hash).unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.structuralHash shouldBe "test-hash-123"
  }

  // =========================================================================
  // ConstellationImpl.initWithScheduler
  // =========================================================================

  "ConstellationImpl.initWithScheduler" should "create an instance with a custom scheduler" in {
    val scheduler     = GlobalScheduler.unbounded
    val constellation = ConstellationImpl.initWithScheduler(scheduler).unsafeRunSync()
    constellation shouldBe a[ConstellationImpl]
  }

  it should "be functional with the custom scheduler" in {
    val scheduler     = GlobalScheduler.unbounded
    val constellation = ConstellationImpl.initWithScheduler(scheduler).unsafeRunSync()

    constellation.setModule(createTestModule("SchedulerTest")).unsafeRunSync()

    val modules = constellation.getModules.unsafeRunSync()
    modules.map(_.name) should contain("SchedulerTest")
  }

  // =========================================================================
  // ConstellationBuilder
  // =========================================================================

  "ConstellationImpl.builder()" should "return a ConstellationBuilder" in {
    val builder = ConstellationImpl.builder()
    builder shouldBe a[ConstellationImpl.ConstellationBuilder]
  }

  "ConstellationBuilder.withScheduler" should "set the scheduler" in {
    val scheduler = GlobalScheduler.unbounded
    val builder   = ConstellationImpl.builder().withScheduler(scheduler)
    builder.scheduler shouldBe scheduler
  }

  "ConstellationBuilder.withBackends" should "set the backends" in {
    val backends = ConstellationBackends.defaults
    val builder  = ConstellationImpl.builder().withBackends(backends)
    builder.backends shouldBe backends
  }

  "ConstellationBuilder.withDefaultTimeout" should "set the default timeout" in {
    val builder = ConstellationImpl.builder().withDefaultTimeout(30.seconds)
    builder.defaultTimeout shouldBe Some(30.seconds)
  }

  "ConstellationBuilder.withPipelineStore" should "set the pipeline store" in {
    val ps      = PipelineStoreImpl.init.unsafeRunSync()
    val builder = ConstellationImpl.builder().withPipelineStore(ps)
    builder.PipelineStoreOpt shouldBe Some(ps)
  }

  "ConstellationBuilder.withSuspensionStore" should "set the suspension store" in {
    val ss      = new MockSuspensionStore
    val builder = ConstellationImpl.builder().withSuspensionStore(ss)
    builder.suspensionStoreOpt shouldBe Some(ss)
  }

  "ConstellationBuilder.build" should "create a working ConstellationImpl instance" in {
    val constellation = ConstellationImpl.builder().build().unsafeRunSync()
    constellation shouldBe a[ConstellationImpl]
  }

  it should "create an instance that can register and list modules" in {
    val constellation = ConstellationImpl.builder().build().unsafeRunSync()

    constellation.setModule(createTestModule("BuilderMod1")).unsafeRunSync()
    constellation.setModule(createTestModule("BuilderMod2")).unsafeRunSync()

    val modules = constellation.getModules.unsafeRunSync()
    modules should have size 2
    modules.map(_.name) should contain allOf ("BuilderMod1", "BuilderMod2")
  }

  it should "use the provided PipelineStore when one is configured" in {
    val customStore = PipelineStoreImpl.init.unsafeRunSync()
    val constellation =
      ConstellationImpl.builder().withPipelineStore(customStore).build().unsafeRunSync()

    constellation.PipelineStore shouldBe customStore
  }

  it should "create a default PipelineStore when none is configured" in {
    val constellation = ConstellationImpl.builder().build().unsafeRunSync()
    constellation.PipelineStore should not be null
  }

  // =========================================================================
  // suspensionStore accessor
  // =========================================================================

  "suspensionStore" should "return None when not configured" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.suspensionStore shouldBe None
  }

  it should "return Some when configured via builder" in {
    val ss            = new MockSuspensionStore
    val constellation = ConstellationImpl.builder().withSuspensionStore(ss).build().unsafeRunSync()
    constellation.suspensionStore shouldBe defined
    constellation.suspensionStore.get shouldBe ss
  }

  // =========================================================================
  // resumeFromStore error paths
  // =========================================================================

  "resumeFromStore" should "raise IllegalStateException when no suspension store is configured" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val handle        = SuspensionHandle("test-id")
    val result =
      constellation
        .resumeFromStore(handle, Map.empty, Map.empty, ExecutionOptions())
        .attempt
        .unsafeRunSync()
    result.isLeft shouldBe true
    result.left.getOrElse(fail("Expected Left")) shouldBe a[IllegalStateException]
    result.left.getOrElse(fail("Expected Left")).getMessage should include(
      "No SuspensionStore configured"
    )
  }

  it should "raise NoSuchElementException when suspension is not found in store" in {
    val result = (for {
      suspensionStore <- InMemorySuspensionStore.init
      constellation <- ConstellationImpl
        .builder()
        .withSuspensionStore(suspensionStore)
        .build()
      sig <- constellation
        .resumeFromStore(SuspensionHandle("nonexistent"), Map.empty, Map.empty, ExecutionOptions())
        .attempt
    } yield sig).unsafeRunSync()

    result.isLeft shouldBe true
    result.left.getOrElse(fail("Expected Left")) shouldBe a[NoSuchElementException]
    result.left.getOrElse(fail("Expected Left")).getMessage should include("Suspension not found")
  }

  // =========================================================================
  // run(ref: String, ...) error paths
  // =========================================================================

  "run with string ref" should "raise error when pipeline not found by name" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val result =
      constellation.run("nonexistent", Map.empty, ExecutionOptions()).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.getOrElse(fail("Expected Left")).getMessage should include("Pipeline not found")
  }

  it should "raise error when pipeline not found by hash" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val result =
      constellation.run("sha256:abc123", Map.empty, ExecutionOptions()).attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.left.getOrElse(fail("Expected Left")).getMessage should include("Pipeline not found")
  }

  // =========================================================================
  // Builder chaining
  // =========================================================================

  "ConstellationBuilder" should "support fluent chaining of all with* methods" in {
    val ss = new MockSuspensionStore
    val ps = PipelineStoreImpl.init.unsafeRunSync()

    val builder = ConstellationImpl
      .builder()
      .withScheduler(GlobalScheduler.unbounded)
      .withBackends(ConstellationBackends.defaults)
      .withDefaultTimeout(60.seconds)
      .withPipelineStore(ps)
      .withSuspensionStore(ss)

    builder.scheduler shouldBe GlobalScheduler.unbounded
    builder.backends shouldBe ConstellationBackends.defaults
    builder.defaultTimeout shouldBe Some(60.seconds)
    builder.PipelineStoreOpt shouldBe Some(ps)
    builder.suspensionStoreOpt shouldBe Some(ss)
  }

  it should "build a fully configured instance that works correctly" in {
    val ss = new MockSuspensionStore
    val ps = PipelineStoreImpl.init.unsafeRunSync()

    val constellation = ConstellationImpl
      .builder()
      .withScheduler(GlobalScheduler.unbounded)
      .withBackends(ConstellationBackends.defaults)
      .withDefaultTimeout(60.seconds)
      .withPipelineStore(ps)
      .withSuspensionStore(ss)
      .build()
      .unsafeRunSync()

    constellation shouldBe a[ConstellationImpl]
    constellation.PipelineStore shouldBe ps
    constellation.suspensionStore shouldBe defined

    // Verify it can register and list modules
    constellation.setModule(createTestModule("FullyConfigured")).unsafeRunSync()
    val modules = constellation.getModules.unsafeRunSync()
    modules.map(_.name) should contain("FullyConfigured")
  }
}
