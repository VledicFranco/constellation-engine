package io.constellation.spi

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import io.constellation._
import io.constellation.execution.GlobalScheduler

class BackendsIntegrationTest extends AnyFlatSpec with Matchers {

  // Simple module input/output types
  case class TextInput(text: String)
  case class TextOutput(result: String)

  private def createUppercaseModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Uppercase", "Converts text to uppercase", 1, 0)
      .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toUpperCase))
      .build

  /** Build a simple DAG: input -> Uppercase -> output */
  private def buildSimpleDag(): (DagSpec, Map[UUID, Module.Uninitialized]) = {
    val moduleId = UUID.randomUUID()
    val inputDataId = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val uppercaseModule = createUppercaseModule()

    val dag = DagSpec(
      metadata = ComponentMetadata("TestDag", "Test DAG for SPI", List.empty, 1, 0),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Converts text to uppercase", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId -> DataNodeSpec(
          "input",
          Map(inputDataId -> "input", moduleId -> "text"),
          CType.CString
        ),
        outputDataId -> DataNodeSpec(
          "output",
          Map(moduleId -> "result"),
          CType.CString
        )
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputDataId)
    )

    val modules = Map(moduleId -> uppercaseModule)
    (dag, modules)
  }

  // Recording implementations for testing

  private case class MetricRecord(name: String, value: Option[Double], tags: Map[String, String])
  private case class SpanRecord(name: String, attributes: Map[String, String])
  private case class ListenerEvent(eventType: String, args: Map[String, String])

  private def recordingMetrics(ref: Ref[IO, List[MetricRecord]]): MetricsProvider = new MetricsProvider {
    def counter(name: String, tags: Map[String, String]): IO[Unit] =
      ref.update(MetricRecord(name, None, tags) :: _)
    def histogram(name: String, value: Double, tags: Map[String, String]): IO[Unit] =
      ref.update(MetricRecord(name, Some(value), tags) :: _)
    def gauge(name: String, value: Double, tags: Map[String, String]): IO[Unit] =
      ref.update(MetricRecord(name, Some(value), tags) :: _)
  }

  private def recordingTracer(ref: Ref[IO, List[SpanRecord]]): TracerProvider = new TracerProvider {
    def span[A](name: String, attributes: Map[String, String])(body: IO[A]): IO[A] =
      ref.update(SpanRecord(name, attributes) :: _) *> body
  }

  private def recordingListener(ref: Ref[IO, List[ListenerEvent]]): ExecutionListener = new ExecutionListener {
    def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
      ref.update(ListenerEvent("executionStart", Map("dagName" -> dagName)) :: _)
    def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
      ref.update(ListenerEvent("moduleStart", Map("moduleName" -> moduleName)) :: _)
    def onModuleComplete(executionId: UUID, moduleId: UUID, moduleName: String, durationMs: Long): IO[Unit] =
      ref.update(ListenerEvent("moduleComplete", Map("moduleName" -> moduleName, "durationMs" -> durationMs.toString)) :: _)
    def onModuleFailed(executionId: UUID, moduleId: UUID, moduleName: String, error: Throwable): IO[Unit] =
      ref.update(ListenerEvent("moduleFailed", Map("moduleName" -> moduleName, "error" -> error.getMessage)) :: _)
    def onExecutionComplete(executionId: UUID, dagName: String, succeeded: Boolean, durationMs: Long): IO[Unit] =
      ref.update(ListenerEvent("executionComplete", Map("dagName" -> dagName, "succeeded" -> succeeded.toString)) :: _)
  }

  "runWithBackends" should "produce correct results with noop backends (same as runWithScheduler)" in {
    val (dag, modules) = buildSimpleDag()
    val inputs = Map("input" -> CValue.CString("hello"))

    val noopState = Runtime.runWithBackends(
      dag, inputs, modules, Map.empty, GlobalScheduler.unbounded, ConstellationBackends.defaults
    ).unsafeRunSync()

    val schedulerState = Runtime.runWithScheduler(
      dag, inputs, modules, Map.empty, GlobalScheduler.unbounded
    ).unsafeRunSync()

    // Both should produce the same output data
    val noopOutput = noopState.data.values.map(_.value).collect { case CValue.CString(s) => s }.toSet
    val schedulerOutput = schedulerState.data.values.map(_.value).collect { case CValue.CString(s) => s }.toSet

    noopOutput should contain("HELLO")
    schedulerOutput should contain("HELLO")
    noopOutput shouldBe schedulerOutput
  }

  it should "fire listener onExecutionStart with correct dagName" in {
    val (dag, modules) = buildSimpleDag()
    val inputs = Map("input" -> CValue.CString("hello"))

    val result = (for {
      listenerEvents <- Ref.of[IO, List[ListenerEvent]](Nil)
      backends = ConstellationBackends(listener = recordingListener(listenerEvents))
      _ <- Runtime.runWithBackends(dag, inputs, modules, Map.empty, GlobalScheduler.unbounded, backends)
      // Small delay for fire-and-forget fibers to complete
      _ <- IO.sleep(scala.concurrent.duration.Duration(50, "ms"))
      events <- listenerEvents.get
    } yield events).unsafeRunSync()

    val startEvents = result.filter(_.eventType == "executionStart")
    startEvents should not be empty
    startEvents.head.args("dagName") shouldBe "TestDag"
  }

  it should "fire listener onModuleStart and onModuleComplete for each module" in {
    val (dag, modules) = buildSimpleDag()
    val inputs = Map("input" -> CValue.CString("hello"))

    val result = (for {
      listenerEvents <- Ref.of[IO, List[ListenerEvent]](Nil)
      backends = ConstellationBackends(listener = recordingListener(listenerEvents))
      _ <- Runtime.runWithBackends(dag, inputs, modules, Map.empty, GlobalScheduler.unbounded, backends)
      _ <- IO.sleep(scala.concurrent.duration.Duration(50, "ms"))
      events <- listenerEvents.get
    } yield events).unsafeRunSync()

    val moduleStarts = result.filter(_.eventType == "moduleStart")
    moduleStarts should not be empty
    moduleStarts.head.args("moduleName") shouldBe "Uppercase"

    val moduleCompletes = result.filter(_.eventType == "moduleComplete")
    moduleCompletes should not be empty
    moduleCompletes.head.args("moduleName") shouldBe "Uppercase"
  }

  it should "fire listener onExecutionComplete with correct succeeded flag" in {
    val (dag, modules) = buildSimpleDag()
    val inputs = Map("input" -> CValue.CString("hello"))

    val result = (for {
      listenerEvents <- Ref.of[IO, List[ListenerEvent]](Nil)
      backends = ConstellationBackends(listener = recordingListener(listenerEvents))
      _ <- Runtime.runWithBackends(dag, inputs, modules, Map.empty, GlobalScheduler.unbounded, backends)
      _ <- IO.sleep(scala.concurrent.duration.Duration(50, "ms"))
      events <- listenerEvents.get
    } yield events).unsafeRunSync()

    val completeEvents = result.filter(_.eventType == "executionComplete")
    completeEvents should not be empty
    completeEvents.head.args("dagName") shouldBe "TestDag"
    completeEvents.head.args("succeeded") shouldBe "true"
  }

  it should "record constellation.execution.total counter" in {
    val (dag, modules) = buildSimpleDag()
    val inputs = Map("input" -> CValue.CString("hello"))

    val result = (for {
      metricRecords <- Ref.of[IO, List[MetricRecord]](Nil)
      backends = ConstellationBackends(metrics = recordingMetrics(metricRecords))
      _ <- Runtime.runWithBackends(dag, inputs, modules, Map.empty, GlobalScheduler.unbounded, backends)
      _ <- IO.sleep(scala.concurrent.duration.Duration(50, "ms"))
      metrics <- metricRecords.get
    } yield metrics).unsafeRunSync()

    val executionCounters = result.filter(_.name == "constellation.execution.total")
    executionCounters should not be empty
    executionCounters.head.tags("dag.name") shouldBe "TestDag"
    executionCounters.head.tags("status") shouldBe "success"
  }

  it should "record constellation.execution.duration_ms histogram" in {
    val (dag, modules) = buildSimpleDag()
    val inputs = Map("input" -> CValue.CString("hello"))

    val result = (for {
      metricRecords <- Ref.of[IO, List[MetricRecord]](Nil)
      backends = ConstellationBackends(metrics = recordingMetrics(metricRecords))
      _ <- Runtime.runWithBackends(dag, inputs, modules, Map.empty, GlobalScheduler.unbounded, backends)
      _ <- IO.sleep(scala.concurrent.duration.Duration(50, "ms"))
      metrics <- metricRecords.get
    } yield metrics).unsafeRunSync()

    val durationHistograms = result.filter(_.name == "constellation.execution.duration_ms")
    durationHistograms should not be empty
    durationHistograms.head.value shouldBe defined
    durationHistograms.head.value.get should be >= 0.0
    durationHistograms.head.tags("dag.name") shouldBe "TestDag"
  }

  it should "record constellation.module.duration_ms histogram per module" in {
    val (dag, modules) = buildSimpleDag()
    val inputs = Map("input" -> CValue.CString("hello"))

    val result = (for {
      metricRecords <- Ref.of[IO, List[MetricRecord]](Nil)
      backends = ConstellationBackends(metrics = recordingMetrics(metricRecords))
      _ <- Runtime.runWithBackends(dag, inputs, modules, Map.empty, GlobalScheduler.unbounded, backends)
      _ <- IO.sleep(scala.concurrent.duration.Duration(50, "ms"))
      metrics <- metricRecords.get
    } yield metrics).unsafeRunSync()

    val moduleDurations = result.filter(_.name == "constellation.module.duration_ms")
    moduleDurations should not be empty
    moduleDurations.head.tags("module.name") shouldBe "Uppercase"
    moduleDurations.head.tags("status") shouldBe "success"
    moduleDurations.head.value shouldBe defined
    moduleDurations.head.value.get should be >= 0.0
  }

  it should "create tracer spans for execution and each module" in {
    val (dag, modules) = buildSimpleDag()
    val inputs = Map("input" -> CValue.CString("hello"))

    val result = (for {
      spanRecords <- Ref.of[IO, List[SpanRecord]](Nil)
      backends = ConstellationBackends(tracer = recordingTracer(spanRecords))
      _ <- Runtime.runWithBackends(dag, inputs, modules, Map.empty, GlobalScheduler.unbounded, backends)
      _ <- IO.sleep(scala.concurrent.duration.Duration(50, "ms"))
      spans <- spanRecords.get
    } yield spans).unsafeRunSync()

    val executionSpans = result.filter(_.name.startsWith("execute("))
    executionSpans should not be empty
    executionSpans.head.name shouldBe "execute(TestDag)"

    val moduleSpans = result.filter(_.name.startsWith("module("))
    moduleSpans should not be empty
    moduleSpans.head.name shouldBe "module(Uppercase)"
  }

  it should "work with ConstellationBuilder API" in {
    val result = (for {
      listenerEvents <- Ref.of[IO, List[ListenerEvent]](Nil)
      metricRecords  <- Ref.of[IO, List[MetricRecord]](Nil)
      spanRecords    <- Ref.of[IO, List[SpanRecord]](Nil)

      constellation <- impl.ConstellationImpl.builder()
        .withMetrics(recordingMetrics(metricRecords))
        .withTracer(recordingTracer(spanRecords))
        .withListener(recordingListener(listenerEvents))
        .build()

      // Register module and DAG
      uppercaseModule = createUppercaseModule()
      _ <- constellation.setModule(uppercaseModule)

      moduleId = UUID.randomUUID()
      inputDataId = UUID.randomUUID()
      outputDataId = UUID.randomUUID()

      dag = DagSpec(
        metadata = ComponentMetadata("BuilderTestDag", "Test DAG", List.empty, 1, 0),
        modules = Map(
          moduleId -> ModuleNodeSpec(
            metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
            consumes = Map("text" -> CType.CString),
            produces = Map("result" -> CType.CString)
          )
        ),
        data = Map(
          inputDataId -> DataNodeSpec("input", Map(inputDataId -> "input", moduleId -> "text"), CType.CString),
          outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
        ),
        inEdges = Set((inputDataId, moduleId)),
        outEdges = Set((moduleId, outputDataId)),
        declaredOutputs = List("output"),
        outputBindings = Map("output" -> outputDataId)
      )

      structuralHash = ProgramImage.computeStructuralHash(dag)
      image = ProgramImage(
        structuralHash = structuralHash,
        syntacticHash = "",
        dagSpec = dag,
        moduleOptions = Map.empty,
        compiledAt = java.time.Instant.now()
      )
      loaded = LoadedProgram(image, Map.empty)
      sig <- constellation.run(loaded, Map("input" -> CValue.CString("world")))

      _ <- IO.sleep(scala.concurrent.duration.Duration(50, "ms"))

      events  <- listenerEvents.get
      metrics <- metricRecords.get
      spans   <- spanRecords.get
    } yield (sig, events, metrics, spans)).unsafeRunSync()

    val (sig, events, metrics, spans) = result

    // Verify execution produced correct result
    sig.outputs.get("output") shouldBe Some(CValue.CString("WORLD"))

    // Verify listener events
    events.map(_.eventType) should contain("executionStart")
    events.map(_.eventType) should contain("executionComplete")

    // Verify metrics
    metrics.map(_.name) should contain("constellation.execution.total")
    metrics.map(_.name) should contain("constellation.execution.duration_ms")

    // Verify spans
    spans.map(_.name) should contain("execute(BuilderTestDag)")
  }
}
