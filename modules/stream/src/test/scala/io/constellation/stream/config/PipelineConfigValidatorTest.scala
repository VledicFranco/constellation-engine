package io.constellation.stream.config

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.stream.connector.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PipelineConfigValidatorTest extends AnyFlatSpec with Matchers {

  private def makeRegistry: (
      ConnectorRegistry,
      cats.effect.std.Queue[IO, Option[CValue]],
      cats.effect.std.Queue[IO, CValue]
  ) = {
    val srcQ = cats.effect.std.Queue.bounded[IO, Option[CValue]](10).unsafeRunSync()
    val snkQ = cats.effect.std.Queue.bounded[IO, CValue](10).unsafeRunSync()
    val registry = ConnectorRegistry.builder
      .source("input", MemoryConnector.source("input", srcQ))
      .sink("output", MemoryConnector.sink("output", snkQ))
      .build
    (registry, srcQ, snkQ)
  }

  "PipelineConfigValidator" should "fail when a source binding is missing" in {
    val (registry, _, _) = makeRegistry
    val config           = StreamPipelineConfig()

    val result = PipelineConfigValidator.validate(
      config,
      sourceNames = Set("input"),
      sinkNames = Set.empty,
      registry = registry
    )

    result.isLeft shouldBe true
    result.left.toOption.get
      .exists(_.isInstanceOf[ConfigValidationError.UnboundSource]) shouldBe true
  }

  it should "fail when a sink binding is missing" in {
    val (registry, _, _) = makeRegistry
    val config           = StreamPipelineConfig()

    val result = PipelineConfigValidator.validate(
      config,
      sourceNames = Set.empty,
      sinkNames = Set("output"),
      registry = registry
    )

    result.isLeft shouldBe true
    result.left.toOption.get.exists(_.isInstanceOf[ConfigValidationError.UnboundSink]) shouldBe true
  }

  it should "fail when connector type is unknown" in {
    val config = StreamPipelineConfig(
      sourceBindings = Map("input" -> SourceBinding("nonexistent"))
    )

    val result = PipelineConfigValidator.validate(
      config,
      sourceNames = Set("input"),
      sinkNames = Set.empty,
      registry = ConnectorRegistry.empty
    )

    result.isLeft shouldBe true
    result.left.toOption.get
      .exists(_.isInstanceOf[ConfigValidationError.UnknownConnectorType]) shouldBe true
  }

  it should "fail when connector config validation fails" in {
    // Create a source connector with required config
    val strictSrc = new SourceConnector {
      def name: String     = "strict"
      def typeName: String = "strict"
      override def configSchema: ConnectorSchema = ConnectorSchema(
        required = Map("url" -> PropertyType.StringProp())
      )
      def stream(config: ValidatedConnectorConfig) = fs2.Stream.empty
    }

    val registry = ConnectorRegistry.builder
      .source("strict", strictSrc)
      .build

    val config = StreamPipelineConfig(
      sourceBindings = Map("input" -> SourceBinding("strict", Map.empty)) // missing 'url'
    )

    val result = PipelineConfigValidator.validate(
      config,
      sourceNames = Set("input"),
      sinkNames = Set.empty,
      registry = registry
    )

    result.isLeft shouldBe true
    result.left.toOption.get
      .exists(_.isInstanceOf[ConfigValidationError.ConnectorConfigErrors]) shouldBe true
  }

  it should "succeed with valid bindings" in {
    val (registry, _, _) = makeRegistry
    val config = StreamPipelineConfig(
      sourceBindings = Map("input" -> SourceBinding("input")),
      sinkBindings = Map("output" -> SinkBinding("output"))
    )

    val result = PipelineConfigValidator.validate(
      config,
      sourceNames = Set("input"),
      sinkNames = Set("output"),
      registry = registry
    )

    result.isRight shouldBe true
    result.toOption.get.resolvedSources should contain key "input"
    result.toOption.get.resolvedSinks should contain key "output"
  }

  it should "resolve DLQ binding" in {
    val (registry, _, _) = makeRegistry
    val config = StreamPipelineConfig(
      sourceBindings = Map("input" -> SourceBinding("input")),
      sinkBindings = Map("output" -> SinkBinding("output")),
      dlq = Some(SinkBinding("output"))
    )

    val result = PipelineConfigValidator.validate(
      config,
      sourceNames = Set("input"),
      sinkNames = Set("output"),
      registry = registry
    )

    result.isRight shouldBe true
    result.toOption.get.resolvedDlq shouldBe defined
  }

  it should "fail with unknown DLQ connector type" in {
    val config = StreamPipelineConfig(
      dlq = Some(SinkBinding("nonexistent"))
    )

    val result = PipelineConfigValidator.validate(
      config,
      sourceNames = Set.empty,
      sinkNames = Set.empty,
      registry = ConnectorRegistry.empty
    )

    result.isLeft shouldBe true
    result.left.toOption.get
      .exists(_.isInstanceOf[ConfigValidationError.UnknownConnectorType]) shouldBe true
  }

  it should "pass with empty source and sink names" in {
    val config = StreamPipelineConfig()
    val result = PipelineConfigValidator.validate(
      config,
      sourceNames = Set.empty,
      sinkNames = Set.empty,
      registry = ConnectorRegistry.empty
    )

    result.isRight shouldBe true
    result.toOption.get.resolvedSources shouldBe empty
    result.toOption.get.resolvedSinks shouldBe empty
    result.toOption.get.resolvedDlq shouldBe None
  }

  "StreamCompiler.wire with config" should "round-trip through config-based wiring" in {
    val (dagSpec, _, moduleId, _) = linearDag("input", "Upper", "output")

    val result = (for {
      srcQ <- cats.effect.std.Queue.bounded[IO, Option[CValue]](10)
      snkQ <- cats.effect.std.Queue.bounded[IO, CValue](10)
      registry = ConnectorRegistry.builder
        .source("input", MemoryConnector.source("input", srcQ))
        .sink("output", MemoryConnector.sink("output", snkQ))
        .build
      config = StreamPipelineConfig(
        sourceBindings = Map("input" -> SourceBinding("input")),
        sinkBindings = Map("output" -> SinkBinding("output"))
      )
      uppercaseFn = (v: CValue) =>
        v match {
          case CValue.CString(s) => IO.pure(CValue.CString(s.toUpperCase))
          case other             => IO.pure(other)
        }
      graph <- io.constellation.stream.StreamCompiler.wireWithConfig(
        dagSpec,
        config,
        registry,
        Map(moduleId -> uppercaseFn)
      )
      _     <- srcQ.offer(Some(CValue.CString("hello")))
      _     <- srcQ.offer(None)
      _     <- graph.stream.compile.drain
      items <- snkQ.tryTakeN(None)
    } yield items).unsafeRunSync()

    result should have size 1
    result(0) shouldBe CValue.CString("HELLO")
  }

  private def linearDag(
      sourceName: String,
      moduleName: String,
      sinkName: String
  ): (DagSpec, java.util.UUID, java.util.UUID, java.util.UUID) = {
    val sourceId = java.util.UUID.randomUUID()
    val moduleId = java.util.UUID.randomUUID()
    val sinkId   = java.util.UUID.randomUUID()

    val dagSpec = DagSpec(
      metadata = ComponentMetadata("linear", "linear pipeline", Nil, 1, 0),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata(moduleName, "transform", Nil, 1, 0),
          consumes = Map("input" -> CType.CString),
          produces = Map("output" -> CType.CString)
        )
      ),
      data = Map(
        sourceId -> DataNodeSpec(
          sourceName,
          Map(sourceId -> sourceName),
          CType.CString,
          None,
          Map.empty
        ),
        sinkId -> DataNodeSpec(sinkName, Map(sinkId -> sinkName), CType.CString, None, Map.empty)
      ),
      inEdges = Set(sourceId -> moduleId),
      outEdges = Set(moduleId -> sinkId),
      declaredOutputs = List(sinkName),
      outputBindings = Map(sinkName -> sinkId)
    )

    (dagSpec, sourceId, moduleId, sinkId)
  }

  // ===== Error Messages =====

  "ConfigValidationError" should "produce readable messages" in {
    ConfigValidationError.UnboundSource("src").message should include("src")
    ConfigValidationError.UnboundSink("snk").message should include("snk")
    ConfigValidationError.UnknownConnectorType("binding", "kafka").message should include("kafka")

    val configErrors = List(ConnectorConfigError.MissingRequired("url"))
    ConfigValidationError.ConnectorConfigErrors("binding", configErrors).message should include(
      "url"
    )
  }
}
