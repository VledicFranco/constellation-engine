package io.constellation

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import cats.Eval

import io.constellation.json.given

import io.circe.Json
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CustomJsonCodecsTest extends AnyFlatSpec with Matchers {

  "CustomJsonCodecs" should "encode/decode Module Status generically" in {
    val status: Module.Status  = Module.Status.Timed(100.millis)
    val status2: Module.Status = Module.Status.Fired(200.millis, Some(Map("key" -> "value".asJson)))
    val status4: Module.Status = Module.Status.Unfired

    val encoded1 = status.asJson
    val encoded2 = status2.asJson
    val encoded4 = status4.asJson

    val decoded1 = encoded1.as[Module.Status]
    val decoded2 = encoded2.as[Module.Status]
    val decoded4 = encoded4.as[Module.Status]

    decoded1 shouldBe Right(status)
    decoded2 shouldBe Right(status2)
    decoded4 shouldBe Right(status4)
  }

  it should "encode/decode CTypes generically" in {
    val cType1: CType = CType.CString
    val cType2: CType = CType.CFloat
    val cType3: CType = CType.CMap(CType.CInt, CType.CString)
    val cType4: CType = CType.CList(CType.CBoolean)

    val encoded1 = cType1.asJson
    val encoded2 = cType2.asJson
    val encoded3 = cType3.asJson
    val encoded4 = cType4.asJson

    val decoded1 = encoded1.as[CType]
    val decoded2 = encoded2.as[CType]
    val decoded3 = encoded3.as[CType]
    val decoded4 = encoded4.as[CType]

    decoded1 shouldBe Right(cType1)
    decoded2 shouldBe Right(cType2)
    decoded3 shouldBe Right(cType3)
    decoded4 shouldBe Right(cType4)
  }

  it should "encode/decode FiniteDuration as millis" in {
    val original = 1500.millis
    val encoded  = original.asJson
    val decoded  = encoded.as[FiniteDuration]
    decoded shouldBe Right(original)
  }

  it should "encode/decode Throwable as message string" in {
    val original: Throwable = new RuntimeException("something went wrong")
    val encoded             = original.asJson
    val decoded             = encoded.as[Throwable]
    decoded.isRight shouldBe true
    decoded.toOption.get.getMessage shouldBe "something went wrong"
  }

  it should "encode/decode UUID Map" in {
    val id1      = UUID.randomUUID()
    val id2      = UUID.randomUUID()
    val original = Map(id1 -> "alpha", id2 -> "beta")
    val encoded  = original.asJson
    val decoded  = encoded.as[Map[UUID, String]]
    decoded shouldBe Right(original)
  }

  it should "encode/decode Eval" in {
    val original = Eval.now(42)
    val encoded  = original.asJson
    val decoded  = encoded.as[Eval[Int]]
    decoded.isRight shouldBe true
    decoded.toOption.get.value shouldBe 42
  }

  it should "encode/decode CValue.CString" in {
    val original: CValue = CValue.CString("hello")
    val encoded          = original.asJson
    val decoded          = encoded.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "encode/decode CValue.CInt" in {
    val original: CValue = CValue.CInt(42L)
    val encoded          = original.asJson
    val decoded          = encoded.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "encode/decode CValue.CFloat" in {
    val original: CValue = CValue.CFloat(3.14)
    val encoded          = original.asJson
    val decoded          = encoded.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "encode/decode CValue.CBoolean" in {
    val original: CValue = CValue.CBoolean(true)
    val encoded          = original.asJson
    val decoded          = encoded.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "encode/decode CValue.CList with nested values" in {
    val original: CValue = CValue.CList(
      Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
      CType.CInt
    )
    val encoded = original.asJson
    val decoded = encoded.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "encode/decode CValue.CMap with key-value pairs" in {
    val original: CValue = CValue.CMap(
      Vector(
        (CValue.CString("a"), CValue.CInt(1)),
        (CValue.CString("b"), CValue.CInt(2))
      ),
      CType.CString,
      CType.CInt
    )
    val encoded = original.asJson
    val decoded = encoded.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "encode/decode CValue.CProduct with structure" in {
    val original: CValue = CValue.CProduct(
      Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30)),
      Map("name" -> CType.CString, "age"           -> CType.CInt)
    )
    val encoded = original.asJson
    val decoded = encoded.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "encode/decode CValue.CUnion with tag and structure" in {
    val original: CValue = CValue.CUnion(
      CValue.CString("hello"),
      Map("text" -> CType.CString, "number" -> CType.CInt),
      "text"
    )
    val encoded = original.asJson
    val decoded = encoded.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "encode/decode CValue.CSome" in {
    val original: CValue = CValue.CSome(CValue.CInt(99), CType.CInt)
    val encoded          = original.asJson
    val decoded          = encoded.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "encode/decode CValue.CNone" in {
    val original: CValue = CValue.CNone(CType.CString)
    val encoded          = original.asJson
    val decoded          = encoded.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "encode/decode ModuleCallOptions with various options set" in {
    val original = ModuleCallOptions(
      retry = Some(3),
      timeoutMs = Some(5000L),
      delayMs = Some(100L),
      backoff = Some("exponential"),
      cacheMs = Some(60000L),
      cacheBackend = Some("redis"),
      throttleCount = Some(10),
      throttlePerMs = Some(1000L),
      concurrency = Some(4),
      onError = Some("skip"),
      lazyEval = Some(true),
      priority = Some(50)
    )
    val encoded = original.asJson
    val decoded = encoded.as[ModuleCallOptions]
    decoded shouldBe Right(original)
  }

  it should "encode/decode ModuleCallOptions.empty" in {
    val original = ModuleCallOptions.empty
    val encoded  = original.asJson
    val decoded  = encoded.as[ModuleCallOptions]
    decoded shouldBe Right(original)
  }

  it should "encode/decode ComponentMetadata" in {
    val original = ComponentMetadata("TestModule", "A test module", List("test", "example"), 2, 1)
    val encoded  = original.asJson
    val decoded  = encoded.as[ComponentMetadata]
    decoded shouldBe Right(original)
  }

  it should "encode/decode ModuleConfig" in {
    val original = ModuleConfig(5.seconds, 3.seconds)
    val encoded  = original.asJson
    val decoded  = encoded.as[ModuleConfig]
    decoded shouldBe Right(original)
  }

  it should "encode/decode DataNodeSpec" in {
    val nickId  = UUID.randomUUID()
    val inputId = UUID.randomUUID()
    val original = DataNodeSpec(
      name = "inputText",
      nicknames = Map(nickId -> "text"),
      cType = CType.CString,
      inlineTransform = None,
      transformInputs = Map("source" -> inputId)
    )
    val encoded = original.asJson
    val decoded = encoded.as[DataNodeSpec]
    decoded shouldBe Right(original)
  }

  it should "encode/decode ModuleNodeSpec" in {
    val metadata = ComponentMetadata("Uppercase", "Converts text to uppercase", List("text"), 1, 0)
    val original = ModuleNodeSpec(
      metadata = metadata,
      consumes = Map("text" -> CType.CString),
      produces = Map("result" -> CType.CString),
      config = ModuleConfig(6.seconds, 3.seconds),
      definitionContext = Some(Map("source" -> "example".asJson))
    )
    val encoded = original.asJson
    val decoded = encoded.as[ModuleNodeSpec]
    decoded shouldBe Right(original)
  }

  it should "encode/decode DagSpec with modules, data nodes, edges, and outputs" in {
    val moduleId = UUID.randomUUID()
    val dataId1  = UUID.randomUUID()
    val dataId2  = UUID.randomUUID()

    val moduleSpec = ModuleNodeSpec(
      metadata = ComponentMetadata("Uppercase", "Uppercases text", List("text"), 1, 0),
      consumes = Map("text" -> CType.CString),
      produces = Map("result" -> CType.CString),
      config = ModuleConfig.default,
      definitionContext = None
    )
    val dataSpec1 = DataNodeSpec(
      name = "inputText",
      nicknames = Map.empty,
      cType = CType.CString,
      inlineTransform = None,
      transformInputs = Map.empty
    )
    val dataSpec2 = DataNodeSpec(
      name = "outputText",
      nicknames = Map.empty,
      cType = CType.CString,
      inlineTransform = None,
      transformInputs = Map.empty
    )

    val original = DagSpec(
      metadata = ComponentMetadata("TestPipeline", "A test pipeline", List("test"), 1, 0),
      modules = Map(moduleId -> moduleSpec),
      data = Map(dataId1 -> dataSpec1, dataId2 -> dataSpec2),
      inEdges = Set((dataId1, moduleId)),
      outEdges = Set((moduleId, dataId2)),
      declaredOutputs = List("outputText"),
      outputBindings = Map("outputText" -> dataId2)
    )
    val encoded = original.asJson
    val decoded = encoded.as[DagSpec]
    decoded shouldBe Right(original)
  }

  it should "encode/decode PipelineImage" in {
    val moduleId = UUID.randomUUID()
    val dataId   = UUID.randomUUID()

    val dagSpec = DagSpec(
      metadata = ComponentMetadata("Pipeline", "Test pipeline", List.empty, 1, 0),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Mod", "A module", List.empty, 1, 0),
          consumes = Map("in" -> CType.CString),
          produces = Map("out" -> CType.CString),
          config = ModuleConfig.default,
          definitionContext = None
        )
      ),
      data = Map(
        dataId -> DataNodeSpec(
          name = "data",
          nicknames = Map.empty,
          cType = CType.CString,
          inlineTransform = None,
          transformInputs = Map.empty
        )
      ),
      inEdges = Set((dataId, moduleId)),
      outEdges = Set.empty,
      declaredOutputs = List.empty,
      outputBindings = Map.empty
    )

    val original = PipelineImage(
      structuralHash = "abc123",
      syntacticHash = "def456",
      dagSpec = dagSpec,
      moduleOptions = Map(moduleId -> ModuleCallOptions(retry = Some(2))),
      compiledAt = Instant.parse("2026-01-15T10:30:00Z"),
      sourceHash = Some("sha256-abcdef")
    )
    val encoded = original.asJson
    val decoded = encoded.as[PipelineImage]
    decoded shouldBe Right(original)
  }

  it should "encode/decode Instant" in {
    val original = Instant.parse("2026-02-09T12:00:00Z")
    val encoded  = original.asJson
    val decoded  = encoded.as[Instant]
    decoded shouldBe Right(original)
  }

  it should "fail decoding unknown CType tag" in {
    val json = Json.obj("tag" -> "Unknown".asJson)
    json.as[CType].isLeft shouldBe true
  }

  it should "fail decoding unknown CValue tag" in {
    val json = Json.obj("tag" -> "Unknown".asJson)
    json.as[CValue].isLeft shouldBe true
  }

  it should "fail decoding unknown Module.Status tag" in {
    val json = Json.obj("tag" -> "Unknown".asJson)
    json.as[Module.Status].isLeft shouldBe true
  }
}
