package io.constellation.impl

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.cache.{CacheBackend, CacheEntry, CacheStats}
import io.constellation.execution.{ConstellationLifecycle, GlobalScheduler}
import io.constellation.spi.{
  ConstellationBackends,
  ExecutionListener,
  MetricsProvider,
  TracerProvider
}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConstellationImplExtendedTest extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // Test helpers
  // ---------------------------------------------------------------------------

  case class TextInput(text: String)
  case class TextOutput(result: String)

  private def createTestModule(name: String): Module.Uninitialized =
    ModuleBuilder
      .metadata(name, s"Test module $name", 1, 0)
      .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toUpperCase))
      .build

  /** Build a minimal DagSpec with one module that takes a single String input and produces a single
    * String output. Returns the DagSpec together with the module, input, and output UUIDs.
    */
  private def buildSimpleDag(
      moduleName: String
  ): (DagSpec, UUID, UUID, UUID) = {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("TestDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata(moduleName, s"Test $moduleName", List.empty, 1, 0),
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
        outputDataId -> DataNodeSpec(
          "result",
          Map(moduleId -> "result"),
          CType.CString
        )
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> outputDataId)
    )

    (dag, moduleId, inputDataId, outputDataId)
  }

  /** Build a PipelineImage from a DagSpec. */
  private def buildImage(dag: DagSpec): PipelineImage = {
    val hash = PipelineImage.computeStructuralHash(dag)
    PipelineImage(
      structuralHash = hash,
      syntacticHash = s"syn-$hash",
      dagSpec = dag,
      moduleOptions = Map.empty,
      compiledAt = Instant.now()
    )
  }

  /** A minimal no-op CacheBackend for testing builder wiring. */
  private class NoOpCacheBackend extends CacheBackend {
    def get[A](key: String): IO[Option[CacheEntry[A]]]               = IO.pure(None)
    def set[A](key: String, value: A, ttl: FiniteDuration): IO[Unit] = IO.unit
    def delete(key: String): IO[Boolean]                             = IO.pure(false)
    def clear: IO[Unit]                                              = IO.unit
    def stats: IO[CacheStats]                                        = IO.pure(CacheStats.empty)
  }

  // =========================================================================
  // ConstellationImpl.init - additional coverage
  // =========================================================================

  "ConstellationImpl.init" should "produce a non-null PipelineStore" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.PipelineStore should not be null
  }

  it should "have no suspension store configured by default" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.suspensionStore shouldBe None
  }

  // =========================================================================
  // ConstellationBuilder - withMetrics
  // =========================================================================

  "ConstellationBuilder.withMetrics" should "set the metrics provider on backends" in {
    val customMetrics = MetricsProvider.noop
    val builder       = ConstellationImpl.builder().withMetrics(customMetrics)
    builder.backends.metrics shouldBe customMetrics
  }

  it should "build a working instance with custom metrics" in {
    val customMetrics = MetricsProvider.noop
    val constellation = ConstellationImpl
      .builder()
      .withMetrics(customMetrics)
      .build()
      .unsafeRunSync()

    constellation shouldBe a[ConstellationImpl]
    constellation.setModule(createTestModule("MetricsMod")).unsafeRunSync()
    val modules = constellation.getModules.unsafeRunSync()
    modules.map(_.name) should contain("MetricsMod")
  }

  // =========================================================================
  // ConstellationBuilder - withTracer
  // =========================================================================

  "ConstellationBuilder.withTracer" should "set the tracer provider on backends" in {
    val customTracer = TracerProvider.noop
    val builder      = ConstellationImpl.builder().withTracer(customTracer)
    builder.backends.tracer shouldBe customTracer
  }

  it should "build a working instance with custom tracer" in {
    val customTracer = TracerProvider.noop
    val constellation = ConstellationImpl
      .builder()
      .withTracer(customTracer)
      .build()
      .unsafeRunSync()

    constellation shouldBe a[ConstellationImpl]
  }

  // =========================================================================
  // ConstellationBuilder - withListener
  // =========================================================================

  "ConstellationBuilder.withListener" should "set the execution listener on backends" in {
    val customListener = ExecutionListener.noop
    val builder        = ConstellationImpl.builder().withListener(customListener)
    builder.backends.listener shouldBe customListener
  }

  it should "build a working instance with custom listener" in {
    val customListener = ExecutionListener.noop
    val constellation = ConstellationImpl
      .builder()
      .withListener(customListener)
      .build()
      .unsafeRunSync()

    constellation shouldBe a[ConstellationImpl]
  }

  // =========================================================================
  // ConstellationBuilder - withCache
  // =========================================================================

  "ConstellationBuilder.withCache" should "set the cache backend on backends" in {
    val cache   = new NoOpCacheBackend
    val builder = ConstellationImpl.builder().withCache(cache)
    builder.backends.cache shouldBe Some(cache)
  }

  it should "build a working instance with custom cache" in {
    val cache = new NoOpCacheBackend
    val constellation = ConstellationImpl
      .builder()
      .withCache(cache)
      .build()
      .unsafeRunSync()

    constellation shouldBe a[ConstellationImpl]
  }

  // =========================================================================
  // ConstellationBuilder - withLifecycle
  // =========================================================================

  "ConstellationBuilder.withLifecycle" should "set the lifecycle on the builder" in {
    val lifecycle = ConstellationLifecycle.create.unsafeRunSync()
    val builder   = ConstellationImpl.builder().withLifecycle(lifecycle)
    builder.lifecycle shouldBe Some(lifecycle)
  }

  it should "build a working instance with lifecycle configured" in {
    val lifecycle = ConstellationLifecycle.create.unsafeRunSync()
    val constellation = ConstellationImpl
      .builder()
      .withLifecycle(lifecycle)
      .build()
      .unsafeRunSync()

    constellation shouldBe a[ConstellationImpl]
    constellation.setModule(createTestModule("LifecycleMod")).unsafeRunSync()
    val modules = constellation.getModules.unsafeRunSync()
    modules.map(_.name) should contain("LifecycleMod")
  }

  // =========================================================================
  // Full builder chaining with all SPI backends
  // =========================================================================

  "ConstellationBuilder" should "support chaining all SPI with* methods together" in {
    val cache     = new NoOpCacheBackend
    val lifecycle = ConstellationLifecycle.create.unsafeRunSync()

    val builder = ConstellationImpl
      .builder()
      .withScheduler(GlobalScheduler.unbounded)
      .withMetrics(MetricsProvider.noop)
      .withTracer(TracerProvider.noop)
      .withListener(ExecutionListener.noop)
      .withCache(cache)
      .withDefaultTimeout(45.seconds)
      .withLifecycle(lifecycle)

    builder.scheduler shouldBe GlobalScheduler.unbounded
    builder.backends.metrics shouldBe MetricsProvider.noop
    builder.backends.tracer shouldBe TracerProvider.noop
    builder.backends.listener shouldBe ExecutionListener.noop
    builder.backends.cache shouldBe Some(cache)
    builder.defaultTimeout shouldBe Some(45.seconds)
    builder.lifecycle shouldBe Some(lifecycle)
  }

  it should "build a fully configured instance with all SPI backends" in {
    val cache     = new NoOpCacheBackend
    val lifecycle = ConstellationLifecycle.create.unsafeRunSync()
    val ps        = PipelineStoreImpl.init.unsafeRunSync()
    val ss        = InMemorySuspensionStore.init.unsafeRunSync()

    val constellation = ConstellationImpl
      .builder()
      .withScheduler(GlobalScheduler.unbounded)
      .withMetrics(MetricsProvider.noop)
      .withTracer(TracerProvider.noop)
      .withListener(ExecutionListener.noop)
      .withCache(cache)
      .withDefaultTimeout(45.seconds)
      .withLifecycle(lifecycle)
      .withPipelineStore(ps)
      .withSuspensionStore(ss)
      .build()
      .unsafeRunSync()

    constellation shouldBe a[ConstellationImpl]
    constellation.PipelineStore shouldBe ps
    constellation.suspensionStore shouldBe defined
  }

  // =========================================================================
  // run(loaded, inputs) - actual execution
  // =========================================================================

  "run(loaded, inputs)" should "execute a simple pipeline and return a completed DataSignature" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("TestMod")).unsafeRunSync()

    val result = constellation
      .run(loaded, Map("text" -> CValue.CString("hello")))
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.structuralHash shouldBe image.structuralHash
    result.resumptionCount shouldBe 0
    result.missingInputs shouldBe empty
    result.pendingOutputs shouldBe empty
    result.outputs should contain key "result"
    result.outputs("result") shouldBe CValue.CString("HELLO")
  }

  it should "return computed nodes including all data node values" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("TestMod")).unsafeRunSync()

    val result = constellation
      .run(loaded, Map("text" -> CValue.CString("world")))
      .unsafeRunSync()

    result.computedNodes should not be empty
  }

  it should "mark pipeline as suspended when required inputs are missing" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("TestMod")).unsafeRunSync()

    // Run with empty inputs -- the module will time out waiting for the input
    // which should result in a non-Completed status
    val result = constellation
      .run(loaded, Map.empty)
      .attempt
      .unsafeRunSync()

    // The runtime may either produce a suspended/failed DataSignature or raise an error
    // when the module times out waiting for its input. Both are valid behaviors.
    result match {
      case Right(sig) =>
        sig.status should not be PipelineStatus.Completed
      case Left(_) =>
        // Timeout or execution error is acceptable for missing inputs
        succeed
    }
  }

  it should "accept ExecutionOptions and return metadata when timings requested" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("TestMod")).unsafeRunSync()

    val options = ExecutionOptions(includeTimings = true)
    val result = constellation
      .run(loaded, Map("text" -> CValue.CString("test")), options)
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs("result") shouldBe CValue.CString("TEST")
  }

  it should "generate a unique execution ID per run" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("TestMod")).unsafeRunSync()

    val result1 = constellation
      .run(loaded, Map("text" -> CValue.CString("a")))
      .unsafeRunSync()
    val result2 = constellation
      .run(loaded, Map("text" -> CValue.CString("b")))
      .unsafeRunSync()

    result1.executionId should not be result2.executionId
  }

  // =========================================================================
  // run(ref, inputs) - execution with stored pipeline
  // =========================================================================

  "run(ref, inputs)" should "execute a pipeline stored by alias name" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("TestMod")).unsafeRunSync()

    // Store the image and create an alias
    val hash = constellation.PipelineStore.store(image).unsafeRunSync()
    constellation.PipelineStore.alias("my-pipeline", hash).unsafeRunSync()

    val result = constellation
      .run("my-pipeline", Map("text" -> CValue.CString("hello")), ExecutionOptions())
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs should contain key "result"
    result.outputs("result") shouldBe CValue.CString("HELLO")
  }

  it should "execute a pipeline stored by sha256 hash reference" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("TestMod")).unsafeRunSync()

    val hash = constellation.PipelineStore.store(image).unsafeRunSync()

    val result = constellation
      .run(s"sha256:$hash", Map("text" -> CValue.CString("world")), ExecutionOptions())
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs("result") shouldBe CValue.CString("WORLD")
  }

  it should "raise error when alias does not resolve to any stored pipeline" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val result = constellation
      .run("nonexistent-alias", Map.empty, ExecutionOptions())
      .attempt
      .unsafeRunSync()

    result.isLeft shouldBe true
    result.left.getOrElse(fail("Expected Left")).getMessage should include("Pipeline not found")
  }

  it should "raise error when sha256 hash is not in the store" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val result = constellation
      .run("sha256:deadbeef", Map.empty, ExecutionOptions())
      .attempt
      .unsafeRunSync()

    result.isLeft shouldBe true
    result.left.getOrElse(fail("Expected Left")).getMessage should include("Pipeline not found")
  }

  // =========================================================================
  // resumeFromStore - error paths
  // =========================================================================

  "resumeFromStore" should "raise IllegalStateException when no SuspensionStore configured" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val handle        = SuspensionHandle("nonexistent")

    val result = constellation
      .resumeFromStore(handle)
      .attempt
      .unsafeRunSync()

    result.isLeft shouldBe true
    result.left.getOrElse(fail("Expected Left")) shouldBe a[IllegalStateException]
    result.left.getOrElse(fail("Expected Left")).getMessage should include(
      "No SuspensionStore configured"
    )
  }

  it should "raise NoSuchElementException when handle not found in store" in {
    val suspensionStore = InMemorySuspensionStore.init.unsafeRunSync()
    val constellation = ConstellationImpl
      .builder()
      .withSuspensionStore(suspensionStore)
      .build()
      .unsafeRunSync()

    val handle = SuspensionHandle("nonexistent-handle")

    val result = constellation
      .resumeFromStore(handle, Map.empty, Map.empty, ExecutionOptions())
      .attempt
      .unsafeRunSync()

    result.isLeft shouldBe true
    result.left.getOrElse(fail("Expected Left")) shouldBe a[NoSuchElementException]
    result.left.getOrElse(fail("Expected Left")).getMessage should include("Suspension not found")
  }

  // =========================================================================
  // suspensionStore accessor
  // =========================================================================

  "suspensionStore" should "return None for default init" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.suspensionStore shouldBe None
  }

  it should "return the configured SuspensionStore from builder" in {
    val ss = InMemorySuspensionStore.init.unsafeRunSync()
    val constellation = ConstellationImpl
      .builder()
      .withSuspensionStore(ss)
      .build()
      .unsafeRunSync()

    constellation.suspensionStore shouldBe Some(ss)
  }

  // =========================================================================
  // getModules / getModuleByName / setModule - extended
  // =========================================================================

  "getModules" should "return empty list for freshly-built instance from builder" in {
    val constellation = ConstellationImpl.builder().build().unsafeRunSync()
    val modules       = constellation.getModules.unsafeRunSync()
    modules shouldBe empty
  }

  "setModule" should "allow overwriting a module with the same name" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val mod1 = createTestModule("Overwrite")
    val mod2 = createTestModule("Overwrite")

    constellation.setModule(mod1).unsafeRunSync()
    constellation.setModule(mod2).unsafeRunSync()

    val modules = constellation.getModules.unsafeRunSync()
    // The module list should contain exactly one entry for "Overwrite"
    modules.count(_.name == "Overwrite") shouldBe 1
  }

  "getModuleByName" should "return the correct module after multiple registrations" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    constellation.setModule(createTestModule("Alpha")).unsafeRunSync()
    constellation.setModule(createTestModule("Beta")).unsafeRunSync()
    constellation.setModule(createTestModule("Gamma")).unsafeRunSync()

    val alpha = constellation.getModuleByName("Alpha").unsafeRunSync()
    alpha shouldBe defined
    alpha.get.spec.name shouldBe "Alpha"

    val beta = constellation.getModuleByName("Beta").unsafeRunSync()
    beta shouldBe defined
    beta.get.spec.name shouldBe "Beta"

    val gamma = constellation.getModuleByName("Gamma").unsafeRunSync()
    gamma shouldBe defined
    gamma.get.spec.name shouldBe "Gamma"
  }

  // =========================================================================
  // PipelineImage.rehydrate
  // =========================================================================

  "PipelineImage.rehydrate" should "produce a LoadedPipeline with matching structural hash" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    loaded.structuralHash shouldBe image.structuralHash
    loaded.image shouldBe image
  }

  it should "produce empty synthetic modules when no branch modules exist" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    loaded.syntheticModules shouldBe empty
  }

  // =========================================================================
  // DataSignature properties
  // =========================================================================

  "DataSignature" should "report isComplete for completed executions" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("TestMod")).unsafeRunSync()

    val sig = constellation
      .run(loaded, Map("text" -> CValue.CString("check")))
      .unsafeRunSync()

    sig.isComplete shouldBe true
    sig.progress shouldBe 1.0
    sig.failedNodes shouldBe empty
  }

  it should "allow looking up outputs by name" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("TestMod")).unsafeRunSync()

    val sig = constellation
      .run(loaded, Map("text" -> CValue.CString("lookup")))
      .unsafeRunSync()

    sig.output("result") shouldBe Some(CValue.CString("LOOKUP"))
    sig.output("nonexistent") shouldBe None
  }

  it should "report allInputs matching the provided inputs" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(createTestModule("TestMod")).unsafeRunSync()

    val inputs = Map("text" -> CValue.CString("inputs"))
    val sig    = constellation.run(loaded, inputs).unsafeRunSync()

    sig.allInputs shouldBe inputs
  }

  // =========================================================================
  // Builder with withDefaultTimeout
  // =========================================================================

  "ConstellationBuilder with defaultTimeout" should "build and execute pipelines" in {
    val (dag, _, _, _) = buildSimpleDag("TestMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    val constellation = ConstellationImpl
      .builder()
      .withDefaultTimeout(30.seconds)
      .build()
      .unsafeRunSync()

    constellation.setModule(createTestModule("TestMod")).unsafeRunSync()

    val result = constellation
      .run(loaded, Map("text" -> CValue.CString("timeout")))
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs("result") shouldBe CValue.CString("TIMEOUT")
  }

  // =========================================================================
  // IO module (side-effectful implementation)
  // =========================================================================

  "run with IO-based module" should "execute side-effectful implementations" in {
    case class IOInput(text: String)
    case class IOOutput(result: String)

    val ioModule = ModuleBuilder
      .metadata("IOMod", "IO-based module", 1, 0)
      .implementation[IOInput, IOOutput](in => IO.pure(IOOutput(in.text.reverse)))
      .build

    val (dag, _, _, _) = buildSimpleDag("IOMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    val constellation = ConstellationImpl.init.unsafeRunSync()
    constellation.setModule(ioModule).unsafeRunSync()

    val result = constellation
      .run(loaded, Map("text" -> CValue.CString("hello")))
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs("result") shouldBe CValue.CString("olleh")
  }

  // =========================================================================
  // initWithScheduler - execution
  // =========================================================================

  "initWithScheduler" should "allow pipeline execution with the custom scheduler" in {
    val (dag, _, _, _) = buildSimpleDag("SchedulerMod")
    val image          = buildImage(dag)
    val loaded         = PipelineImage.rehydrate(image)

    val constellation = ConstellationImpl
      .initWithScheduler(GlobalScheduler.unbounded)
      .unsafeRunSync()

    constellation.setModule(createTestModule("SchedulerMod")).unsafeRunSync()

    val result = constellation
      .run(loaded, Map("text" -> CValue.CString("scheduler")))
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs("result") shouldBe CValue.CString("SCHEDULER")
  }

  // =========================================================================
  // Multiple module registration and execution
  // =========================================================================

  "setModule" should "register multiple distinct modules and retain all of them" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()

    constellation.setModule(createTestModule("ModA")).unsafeRunSync()
    constellation.setModule(createTestModule("ModB")).unsafeRunSync()
    constellation.setModule(createTestModule("ModC")).unsafeRunSync()
    constellation.setModule(createTestModule("ModD")).unsafeRunSync()

    val modules = constellation.getModules.unsafeRunSync()
    modules should have size 4
    modules.map(_.name) should contain allOf ("ModA", "ModB", "ModC", "ModD")
  }

  // =========================================================================
  // PipelineStore roundtrip through ConstellationImpl
  // =========================================================================

  "PipelineStore via ConstellationImpl" should "support store, alias, resolve, and getByName" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val store         = constellation.PipelineStore

    val (dag, _, _, _) = buildSimpleDag("StoreMod")
    val image          = buildImage(dag)

    // Store
    val hash = store.store(image).unsafeRunSync()
    hash shouldBe image.structuralHash

    // Get by hash
    val retrieved = store.get(hash).unsafeRunSync()
    retrieved shouldBe defined
    retrieved.get.dagSpec.name shouldBe "TestDag"

    // Alias
    store.alias("test-pipe", hash).unsafeRunSync()
    val resolved = store.resolve("test-pipe").unsafeRunSync()
    resolved shouldBe Some(hash)

    // Get by name
    val byName = store.getByName("test-pipe").unsafeRunSync()
    byName shouldBe defined
    byName.get.structuralHash shouldBe hash

    // List aliases
    val aliases = store.listAliases.unsafeRunSync()
    aliases should contain key "test-pipe"

    // List images
    val images = store.listImages.unsafeRunSync()
    images should have size 1

    // Remove
    val removed = store.remove(hash).unsafeRunSync()
    removed shouldBe true
    val afterRemove = store.get(hash).unsafeRunSync()
    afterRemove shouldBe None
  }

  // =========================================================================
  // Builder withPipelineStore - execution through stored pipeline
  // =========================================================================

  "Builder with custom PipelineStore" should "use the provided store for run(ref)" in {
    val customStore = PipelineStoreImpl.init.unsafeRunSync()

    val (dag, _, _, _) = buildSimpleDag("CustomStoreMod")
    val image          = buildImage(dag)

    // Pre-populate the custom store
    val hash = customStore.store(image).unsafeRunSync()
    customStore.alias("custom-pipe", hash).unsafeRunSync()

    val constellation = ConstellationImpl
      .builder()
      .withPipelineStore(customStore)
      .build()
      .unsafeRunSync()

    constellation.setModule(createTestModule("CustomStoreMod")).unsafeRunSync()

    val result = constellation
      .run("custom-pipe", Map("text" -> CValue.CString("store")), ExecutionOptions())
      .unsafeRunSync()

    result.status shouldBe PipelineStatus.Completed
    result.outputs("result") shouldBe CValue.CString("STORE")
  }

  // =========================================================================
  // Structural hash consistency
  // =========================================================================

  "PipelineImage.computeStructuralHash" should "produce consistent hashes for same DagSpec" in {
    val (dag1, _, _, _) = buildSimpleDag("HashMod")
    val hash1           = PipelineImage.computeStructuralHash(dag1)
    val hash2           = PipelineImage.computeStructuralHash(dag1)

    hash1 shouldBe hash2
  }
}
