package io.constellation

import java.time.Instant
import java.util.UUID

import scala.concurrent.duration.*

import cats.Eval

import io.constellation.json.given

import io.circe.Json
import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CustomJsonCodecsExtendedTest extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // CValue.CProduct codec -- nested products
  // ---------------------------------------------------------------------------

  "CValue.CProduct codec" should "round-trip a product with nested product values" in {
    val inner = CValue.CProduct(
      Map("x" -> CValue.CInt(1), "y" -> CValue.CInt(2)),
      Map("x" -> CType.CInt, "y" -> CType.CInt)
    )
    val original: CValue = CValue.CProduct(
      Map("point" -> inner, "label" -> CValue.CString("origin")),
      Map("point" -> CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CInt)), "label" -> CType.CString)
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip an empty product" in {
    val original: CValue = CValue.CProduct(Map.empty, Map.empty)
    val decoded          = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip a product containing a list field" in {
    val original: CValue = CValue.CProduct(
      Map(
        "name"   -> CValue.CString("Alice"),
        "scores" -> CValue.CList(Vector(CValue.CInt(90), CValue.CInt(85)), CType.CInt)
      ),
      Map("name" -> CType.CString, "scores" -> CType.CList(CType.CInt))
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip a product containing an optional field" in {
    val original: CValue = CValue.CProduct(
      Map(
        "required" -> CValue.CString("present"),
        "optional" -> CValue.CSome(CValue.CInt(42), CType.CInt)
      ),
      Map("required" -> CType.CString, "optional" -> CType.COptional(CType.CInt))
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // CValue.CUnion codec
  // ---------------------------------------------------------------------------

  "CValue.CUnion codec" should "round-trip a union with an int variant" in {
    val original: CValue = CValue.CUnion(
      CValue.CInt(42),
      Map("text" -> CType.CString, "number" -> CType.CInt),
      "number"
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip a union with a nested product variant" in {
    val productValue = CValue.CProduct(
      Map("id" -> CValue.CInt(1), "name" -> CValue.CString("test")),
      Map("id" -> CType.CInt, "name" -> CType.CString)
    )
    val original: CValue = CValue.CUnion(
      productValue,
      Map("record" -> CType.CProduct(Map("id" -> CType.CInt, "name" -> CType.CString)), "error" -> CType.CString),
      "record"
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip a union with a boolean variant" in {
    val original: CValue = CValue.CUnion(
      CValue.CBoolean(false),
      Map("flag" -> CType.CBoolean, "count" -> CType.CInt, "label" -> CType.CString),
      "flag"
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "preserve the unionTag field distinct from the JSON tag field" in {
    val original: CValue = CValue.CUnion(
      CValue.CString("hello"),
      Map("a" -> CType.CString),
      "a"
    )
    val json = original.asJson
    // The JSON should have "tag" -> "CUnion" and "unionTag" -> "a"
    json.hcursor.downField("tag").as[String] shouldBe Right("CUnion")
    json.hcursor.downField("unionTag").as[String] shouldBe Right("a")
  }

  // ---------------------------------------------------------------------------
  // CValue.CMap codec -- various key/value types
  // ---------------------------------------------------------------------------

  "CValue.CMap codec" should "round-trip an empty map" in {
    val original: CValue = CValue.CMap(Vector.empty, CType.CString, CType.CInt)
    val decoded          = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip a map with int keys and string values" in {
    val original: CValue = CValue.CMap(
      Vector(
        (CValue.CInt(1), CValue.CString("one")),
        (CValue.CInt(2), CValue.CString("two")),
        (CValue.CInt(3), CValue.CString("three"))
      ),
      CType.CInt,
      CType.CString
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip a map with boolean keys and float values" in {
    val original: CValue = CValue.CMap(
      Vector(
        (CValue.CBoolean(true), CValue.CFloat(1.0)),
        (CValue.CBoolean(false), CValue.CFloat(0.0))
      ),
      CType.CBoolean,
      CType.CFloat
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip a map with nested list values" in {
    val original: CValue = CValue.CMap(
      Vector(
        (
          CValue.CString("nums"),
          CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2)), CType.CInt)
        )
      ),
      CType.CString,
      CType.CList(CType.CInt)
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip a map with product values" in {
    val productVal = CValue.CProduct(
      Map("x" -> CValue.CFloat(1.5)),
      Map("x" -> CType.CFloat)
    )
    val original: CValue = CValue.CMap(
      Vector((CValue.CString("point"), productVal)),
      CType.CString,
      CType.CProduct(Map("x" -> CType.CFloat))
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // CValue.CSome / CValue.CNone codec
  // ---------------------------------------------------------------------------

  "CValue.CSome codec" should "round-trip CSome wrapping a string" in {
    val original: CValue = CValue.CSome(CValue.CString("present"), CType.CString)
    val decoded          = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip CSome wrapping a nested list" in {
    val listVal          = CValue.CList(Vector(CValue.CInt(10), CValue.CInt(20)), CType.CInt)
    val original: CValue = CValue.CSome(listVal, CType.CList(CType.CInt))
    val decoded          = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip CSome wrapping a product" in {
    val product = CValue.CProduct(
      Map("name" -> CValue.CString("test")),
      Map("name" -> CType.CString)
    )
    val original: CValue = CValue.CSome(product, CType.CProduct(Map("name" -> CType.CString)))
    val decoded          = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip CSome wrapping a boolean" in {
    val original: CValue = CValue.CSome(CValue.CBoolean(false), CType.CBoolean)
    val decoded          = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  "CValue.CNone codec" should "round-trip CNone with int inner type" in {
    val original: CValue = CValue.CNone(CType.CInt)
    val decoded          = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip CNone with list inner type" in {
    val original: CValue = CValue.CNone(CType.CList(CType.CString))
    val decoded          = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip CNone with product inner type" in {
    val original: CValue = CValue.CNone(CType.CProduct(Map("a" -> CType.CInt, "b" -> CType.CFloat)))
    val decoded          = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip CNone with optional inner type" in {
    val original: CValue = CValue.CNone(CType.COptional(CType.CString))
    val decoded          = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // Module.Status codec -- all variants
  // ---------------------------------------------------------------------------

  "Module.Status codec" should "round-trip Unfired" in {
    val original: Module.Status = Module.Status.Unfired
    val decoded                 = original.asJson.as[Module.Status]
    decoded shouldBe Right(original)
  }

  it should "round-trip Fired with context containing nested JSON" in {
    val context = Some(
      Map(
        "nested" -> Json.obj("inner" -> Json.fromString("value"), "count" -> Json.fromInt(5)),
        "array"  -> Json.arr(Json.fromInt(1), Json.fromInt(2))
      )
    )
    val original: Module.Status = Module.Status.Fired(250.millis, context)
    val decoded                 = original.asJson.as[Module.Status]
    decoded shouldBe Right(original)
  }

  it should "round-trip Fired with no context (None)" in {
    val original: Module.Status = Module.Status.Fired(500.millis, None)
    val decoded                 = original.asJson.as[Module.Status]
    decoded shouldBe Right(original)
  }

  it should "round-trip Fired with empty context map" in {
    val original: Module.Status = Module.Status.Fired(10.millis, Some(Map.empty))
    val decoded                 = original.asJson.as[Module.Status]
    decoded shouldBe Right(original)
  }

  it should "round-trip Failed and preserve error message" in {
    val original: Module.Status = Module.Status.Failed(new RuntimeException("execution failed"))
    val json                    = original.asJson
    val decoded                 = json.as[Module.Status]
    decoded.isRight shouldBe true
    decoded.toOption.get match {
      case Module.Status.Failed(err) => err.getMessage shouldBe "execution failed"
      case other                     => fail(s"Expected Failed, got $other")
    }
  }

  it should "round-trip Timed with zero latency" in {
    val original: Module.Status = Module.Status.Timed(0.millis)
    val decoded                 = original.asJson.as[Module.Status]
    decoded shouldBe Right(original)
  }

  it should "round-trip Timed with large latency" in {
    val original: Module.Status = Module.Status.Timed(999999.millis)
    val decoded                 = original.asJson.as[Module.Status]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // ModuleCallOptions codec -- partial options
  // ---------------------------------------------------------------------------

  "ModuleCallOptions codec" should "round-trip with only retry and timeout set" in {
    val original = ModuleCallOptions(retry = Some(5), timeoutMs = Some(10000L))
    val decoded  = original.asJson.as[ModuleCallOptions]
    decoded shouldBe Right(original)
  }

  it should "round-trip with only caching options set" in {
    val original = ModuleCallOptions(cacheMs = Some(30000L), cacheBackend = Some("memory"))
    val decoded  = original.asJson.as[ModuleCallOptions]
    decoded shouldBe Right(original)
  }

  it should "round-trip with only throttle options set" in {
    val original = ModuleCallOptions(throttleCount = Some(100), throttlePerMs = Some(60000L))
    val decoded  = original.asJson.as[ModuleCallOptions]
    decoded shouldBe Right(original)
  }

  it should "round-trip with only concurrency and priority set" in {
    val original = ModuleCallOptions(concurrency = Some(8), priority = Some(10))
    val decoded  = original.asJson.as[ModuleCallOptions]
    decoded shouldBe Right(original)
  }

  it should "round-trip with only error handling options set" in {
    val original = ModuleCallOptions(
      onError = Some("retry"),
      backoff = Some("linear"),
      delayMs = Some(500L)
    )
    val decoded = original.asJson.as[ModuleCallOptions]
    decoded shouldBe Right(original)
  }

  it should "round-trip with lazyEval false" in {
    val original = ModuleCallOptions(lazyEval = Some(false))
    val decoded  = original.asJson.as[ModuleCallOptions]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // ComponentMetadata codec
  // ---------------------------------------------------------------------------

  "ComponentMetadata codec" should "round-trip with empty tags" in {
    val original = ComponentMetadata("EmptyTags", "A module with no tags", List.empty, 1, 0)
    val decoded  = original.asJson.as[ComponentMetadata]
    decoded shouldBe Right(original)
  }

  it should "round-trip with many tags" in {
    val original = ComponentMetadata(
      "TaggedModule",
      "A heavily tagged module",
      List("text", "processing", "nlp", "production", "v2"),
      3,
      14
    )
    val decoded = original.asJson.as[ComponentMetadata]
    decoded shouldBe Right(original)
  }

  it should "round-trip with special characters in name and description" in {
    val original = ComponentMetadata(
      "My-Module_v2.0",
      "Handles \"special\" chars & <xml> stuff",
      List("special"),
      1,
      0
    )
    val decoded = original.asJson.as[ComponentMetadata]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // ModuleConfig codec
  // ---------------------------------------------------------------------------

  "ModuleConfig codec" should "round-trip default config" in {
    val original = ModuleConfig.default
    val decoded  = original.asJson.as[ModuleConfig]
    decoded shouldBe Right(original)
  }

  it should "round-trip with very short timeouts" in {
    val original = ModuleConfig(1.millis, 1.millis)
    val decoded  = original.asJson.as[ModuleConfig]
    decoded shouldBe Right(original)
  }

  it should "round-trip with large timeouts" in {
    val original = ModuleConfig(60.seconds, 120.seconds)
    val decoded  = original.asJson.as[ModuleConfig]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // DataNodeSpec codec -- with and without inlineTransform
  // ---------------------------------------------------------------------------

  "DataNodeSpec codec" should "round-trip with empty nicknames and transformInputs" in {
    val original = DataNodeSpec(
      name = "simpleData",
      nicknames = Map.empty,
      cType = CType.CInt,
      inlineTransform = None,
      transformInputs = Map.empty
    )
    val decoded = original.asJson.as[DataNodeSpec]
    decoded shouldBe Right(original)
  }

  it should "round-trip with multiple nicknames" in {
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    val original = DataNodeSpec(
      name = "multiNick",
      nicknames = Map(id1 -> "alias1", id2 -> "alias2"),
      cType = CType.CString,
      inlineTransform = None,
      transformInputs = Map.empty
    )
    val decoded = original.asJson.as[DataNodeSpec]
    decoded shouldBe Right(original)
  }

  it should "round-trip with multiple transformInputs" in {
    val inputId1 = UUID.randomUUID()
    val inputId2 = UUID.randomUUID()
    val original = DataNodeSpec(
      name = "transformed",
      nicknames = Map.empty,
      cType = CType.CProduct(Map("a" -> CType.CString, "b" -> CType.CInt)),
      inlineTransform = None,
      transformInputs = Map("left" -> inputId1, "right" -> inputId2)
    )
    val decoded = original.asJson.as[DataNodeSpec]
    decoded shouldBe Right(original)
  }

  it should "serialize inlineTransform type name and deserialize as None" in {
    // InlineTransform closures cannot be deserialized; the encoder writes the type name,
    // and the decoder always returns None for inlineTransform.
    val transform = InlineTransform.MergeTransform(CType.CString, CType.CString)
    val original = DataNodeSpec(
      name = "withTransform",
      nicknames = Map.empty,
      cType = CType.CString,
      inlineTransform = Some(transform),
      transformInputs = Map.empty
    )
    val json    = original.asJson
    val decoded = json.as[DataNodeSpec]
    decoded.isRight shouldBe true
    val result = decoded.toOption.get
    result.name shouldBe "withTransform"
    result.inlineTransform shouldBe None // closures are not deserializable
    // Verify the JSON does contain the transform type name
    json.hcursor.downField("inlineTransformType").as[Option[String]] shouldBe Right(Some("MergeTransform"))
  }

  it should "round-trip with complex CType" in {
    val original = DataNodeSpec(
      name = "complexType",
      nicknames = Map.empty,
      cType = CType.CMap(CType.CString, CType.CList(CType.COptional(CType.CInt))),
      inlineTransform = None,
      transformInputs = Map.empty
    )
    val decoded = original.asJson.as[DataNodeSpec]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // ModuleNodeSpec codec -- with consumes/produces
  // ---------------------------------------------------------------------------

  "ModuleNodeSpec codec" should "round-trip with empty consumes and produces" in {
    val original = ModuleNodeSpec(
      metadata = ComponentMetadata("NoIO", "No inputs or outputs", List.empty, 1, 0),
      consumes = Map.empty,
      produces = Map.empty,
      config = ModuleConfig.default,
      definitionContext = None
    )
    val decoded = original.asJson.as[ModuleNodeSpec]
    decoded shouldBe Right(original)
  }

  it should "round-trip with multiple consumes and produces" in {
    val original = ModuleNodeSpec(
      metadata = ComponentMetadata("MultiIO", "Multiple inputs and outputs", List("io"), 2, 3),
      consumes = Map(
        "text"  -> CType.CString,
        "count" -> CType.CInt,
        "flag"  -> CType.CBoolean
      ),
      produces = Map(
        "result" -> CType.CString,
        "stats"  -> CType.CProduct(Map("total" -> CType.CInt, "avg" -> CType.CFloat))
      ),
      config = ModuleConfig(10.seconds, 5.seconds),
      definitionContext = Some(Map(
        "source"  -> Json.fromString("example"),
        "version" -> Json.fromInt(2)
      ))
    )
    val decoded = original.asJson.as[ModuleNodeSpec]
    decoded shouldBe Right(original)
  }

  it should "round-trip with complex type signatures" in {
    val original = ModuleNodeSpec(
      metadata = ComponentMetadata("ComplexTypes", "Uses complex types", List.empty, 1, 0),
      consumes = Map(
        "data" -> CType.CMap(CType.CString, CType.CList(CType.COptional(CType.CInt)))
      ),
      produces = Map(
        "output" -> CType.CUnion(Map("ok" -> CType.CString, "err" -> CType.CInt))
      ),
      config = ModuleConfig.default,
      definitionContext = None
    )
    val decoded = original.asJson.as[ModuleNodeSpec]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // DagSpec codec -- full round-trip with all fields
  // ---------------------------------------------------------------------------

  "DagSpec codec" should "round-trip an empty DAG" in {
    val original = DagSpec(
      metadata = ComponentMetadata("EmptyDag", "No modules or data", List.empty, 1, 0),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List.empty,
      outputBindings = Map.empty
    )
    val decoded = original.asJson.as[DagSpec]
    decoded shouldBe Right(original)
  }

  it should "round-trip a DAG with multiple modules and data nodes" in {
    val mod1Id  = UUID.randomUUID()
    val mod2Id  = UUID.randomUUID()
    val data1Id = UUID.randomUUID()
    val data2Id = UUID.randomUUID()
    val data3Id = UUID.randomUUID()

    val mod1 = ModuleNodeSpec(
      metadata = ComponentMetadata("Trim", "Trim whitespace", List("text"), 1, 0),
      consumes = Map("text" -> CType.CString),
      produces = Map("result" -> CType.CString),
      config = ModuleConfig.default,
      definitionContext = None
    )
    val mod2 = ModuleNodeSpec(
      metadata = ComponentMetadata("Uppercase", "To uppercase", List("text"), 1, 0),
      consumes = Map("text" -> CType.CString),
      produces = Map("result" -> CType.CString),
      config = ModuleConfig.default,
      definitionContext = None
    )
    val d1 = DataNodeSpec("input", Map.empty, CType.CString, None, Map.empty)
    val d2 = DataNodeSpec("trimmed", Map.empty, CType.CString, None, Map.empty)
    val d3 = DataNodeSpec("output", Map.empty, CType.CString, None, Map.empty)

    val original = DagSpec(
      metadata = ComponentMetadata("Pipeline", "Two-step pipeline", List("text"), 1, 0),
      modules = Map(mod1Id -> mod1, mod2Id -> mod2),
      data = Map(data1Id -> d1, data2Id -> d2, data3Id -> d3),
      inEdges = Set((data1Id, mod1Id), (data2Id, mod2Id)),
      outEdges = Set((mod1Id, data2Id), (mod2Id, data3Id)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> data3Id)
    )
    val decoded = original.asJson.as[DagSpec]
    decoded shouldBe Right(original)
  }

  it should "round-trip a DAG with multiple output bindings" in {
    val modId   = UUID.randomUUID()
    val dataIn  = UUID.randomUUID()
    val dataOut1 = UUID.randomUUID()
    val dataOut2 = UUID.randomUUID()

    val mod = ModuleNodeSpec(
      metadata = ComponentMetadata("Split", "Splits text", List.empty, 1, 0),
      consumes = Map("text" -> CType.CString),
      produces = Map("first" -> CType.CString, "rest" -> CType.CString),
      config = ModuleConfig.default,
      definitionContext = None
    )

    val original = DagSpec(
      metadata = ComponentMetadata("SplitPipeline", "Pipeline with two outputs", List.empty, 1, 0),
      modules = Map(modId -> mod),
      data = Map(
        dataIn   -> DataNodeSpec("input", Map.empty, CType.CString, None, Map.empty),
        dataOut1 -> DataNodeSpec("first", Map.empty, CType.CString, None, Map.empty),
        dataOut2 -> DataNodeSpec("rest", Map.empty, CType.CString, None, Map.empty)
      ),
      inEdges = Set((dataIn, modId)),
      outEdges = Set((modId, dataOut1), (modId, dataOut2)),
      declaredOutputs = List("first", "rest"),
      outputBindings = Map("first" -> dataOut1, "rest" -> dataOut2)
    )
    val decoded = original.asJson.as[DagSpec]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // Instant codec (java.time.Instant round-trip)
  // ---------------------------------------------------------------------------

  "Instant codec" should "round-trip epoch instant" in {
    val original = Instant.EPOCH
    val decoded  = original.asJson.as[Instant]
    decoded shouldBe Right(original)
  }

  it should "round-trip instant with nanosecond precision" in {
    val original = Instant.parse("2026-06-15T14:30:00.123456789Z")
    val decoded  = original.asJson.as[Instant]
    decoded shouldBe Right(original)
  }

  it should "round-trip a distant future instant" in {
    val original = Instant.parse("2099-12-31T23:59:59Z")
    val decoded  = original.asJson.as[Instant]
    decoded shouldBe Right(original)
  }

  it should "encode as ISO-8601 string" in {
    val original = Instant.parse("2026-01-15T10:30:00Z")
    val json     = original.asJson
    json.isString shouldBe true
    json.asString shouldBe Some("2026-01-15T10:30:00Z")
  }

  // ---------------------------------------------------------------------------
  // PipelineImage codec
  // ---------------------------------------------------------------------------

  "PipelineImage codec" should "round-trip with no sourceHash" in {
    val modId  = UUID.randomUUID()
    val dataId = UUID.randomUUID()
    val dagSpec = DagSpec(
      metadata = ComponentMetadata("Pipe", "Test", List.empty, 1, 0),
      modules = Map(modId -> ModuleNodeSpec(
        ComponentMetadata("M", "Mod", List.empty, 1, 0),
        Map("in" -> CType.CString),
        Map("out" -> CType.CString),
        ModuleConfig.default,
        None
      )),
      data = Map(dataId -> DataNodeSpec("d", Map.empty, CType.CString, None, Map.empty)),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List.empty,
      outputBindings = Map.empty
    )
    val original = PipelineImage(
      structuralHash = "struct-hash-1",
      syntacticHash = "syntax-hash-1",
      dagSpec = dagSpec,
      moduleOptions = Map.empty,
      compiledAt = Instant.parse("2026-02-01T00:00:00Z"),
      sourceHash = None
    )
    val decoded = original.asJson.as[PipelineImage]
    decoded shouldBe Right(original)
  }

  it should "round-trip with multiple module options" in {
    val modId1 = UUID.randomUUID()
    val modId2 = UUID.randomUUID()
    val dagSpec = DagSpec(
      metadata = ComponentMetadata("MultiMod", "Multi module pipeline", List.empty, 1, 0),
      modules = Map(
        modId1 -> ModuleNodeSpec(
          ComponentMetadata("A", "Module A", List.empty, 1, 0),
          Map("in" -> CType.CInt),
          Map("out" -> CType.CInt),
          ModuleConfig.default,
          None
        ),
        modId2 -> ModuleNodeSpec(
          ComponentMetadata("B", "Module B", List.empty, 1, 0),
          Map("in" -> CType.CInt),
          Map("out" -> CType.CString),
          ModuleConfig(3.seconds, 2.seconds),
          None
        )
      ),
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List.empty,
      outputBindings = Map.empty
    )
    val original = PipelineImage(
      structuralHash = "hash-a",
      syntacticHash = "hash-b",
      dagSpec = dagSpec,
      moduleOptions = Map(
        modId1 -> ModuleCallOptions(retry = Some(3), timeoutMs = Some(5000L)),
        modId2 -> ModuleCallOptions(cacheMs = Some(60000L), priority = Some(10))
      ),
      compiledAt = Instant.parse("2026-03-15T12:00:00Z"),
      sourceHash = Some("sha256-abcdef123")
    )
    val decoded = original.asJson.as[PipelineImage]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // CType codec -- additional edge cases
  // ---------------------------------------------------------------------------

  "CType codec" should "round-trip CInt" in {
    val original: CType = CType.CInt
    val decoded         = original.asJson.as[CType]
    decoded shouldBe Right(original)
  }

  it should "round-trip CBoolean" in {
    val original: CType = CType.CBoolean
    val decoded         = original.asJson.as[CType]
    decoded shouldBe Right(original)
  }

  it should "round-trip CProduct with multiple fields" in {
    val original: CType = CType.CProduct(Map(
      "name"   -> CType.CString,
      "age"    -> CType.CInt,
      "active" -> CType.CBoolean,
      "score"  -> CType.CFloat
    ))
    val decoded = original.asJson.as[CType]
    decoded shouldBe Right(original)
  }

  it should "round-trip CProduct with empty structure" in {
    val original: CType = CType.CProduct(Map.empty)
    val decoded         = original.asJson.as[CType]
    decoded shouldBe Right(original)
  }

  it should "round-trip CUnion with multiple variants" in {
    val original: CType = CType.CUnion(Map(
      "text"   -> CType.CString,
      "number" -> CType.CInt,
      "flag"   -> CType.CBoolean
    ))
    val decoded = original.asJson.as[CType]
    decoded shouldBe Right(original)
  }

  it should "round-trip COptional wrapping CString" in {
    val original: CType = CType.COptional(CType.CString)
    val decoded         = original.asJson.as[CType]
    decoded shouldBe Right(original)
  }

  it should "round-trip COptional wrapping CList" in {
    val original: CType = CType.COptional(CType.CList(CType.CInt))
    val decoded         = original.asJson.as[CType]
    decoded shouldBe Right(original)
  }

  it should "round-trip deeply nested CType" in {
    val original: CType = CType.CMap(
      CType.CString,
      CType.CList(CType.COptional(CType.CProduct(Map(
        "inner" -> CType.CUnion(Map("a" -> CType.CInt, "b" -> CType.CFloat))
      ))))
    )
    val decoded = original.asJson.as[CType]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // FiniteDuration codec -- edge cases
  // ---------------------------------------------------------------------------

  "FiniteDuration codec" should "round-trip zero duration" in {
    val original = 0.millis
    val decoded  = original.asJson.as[FiniteDuration]
    decoded shouldBe Right(original)
  }

  it should "encode as milliseconds long" in {
    val original = 2.seconds
    val json     = original.asJson
    json.asNumber.flatMap(_.toLong) shouldBe Some(2000L)
  }

  // ---------------------------------------------------------------------------
  // Throwable codec -- edge cases
  // ---------------------------------------------------------------------------

  "Throwable codec" should "round-trip with empty message" in {
    val original: Throwable = new RuntimeException("")
    val decoded             = original.asJson.as[Throwable]
    decoded.isRight shouldBe true
    decoded.toOption.get.getMessage shouldBe ""
  }

  it should "encode null message without throwing during encoding" in {
    val original: Throwable = new RuntimeException(null: String)
    // Encoder[String].apply(null) produces a JString wrapping null internally.
    // This is a known circe edge case. We verify encoding does not throw.
    noException should be thrownBy {
      original.asJson
    }
  }

  // ---------------------------------------------------------------------------
  // UUID Map codec -- edge cases
  // ---------------------------------------------------------------------------

  "UUID Map codec" should "round-trip empty map" in {
    val original = Map.empty[UUID, String]
    val decoded  = original.asJson.as[Map[UUID, String]]
    decoded shouldBe Right(original)
  }

  it should "round-trip map with CValue values" in {
    val id1      = UUID.randomUUID()
    val original: Map[UUID, CValue] = Map(id1 -> CValue.CInt(42L))
    val decoded  = original.asJson.as[Map[UUID, CValue]]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // Eval codec -- edge cases
  // ---------------------------------------------------------------------------

  "Eval codec" should "round-trip Eval.later" in {
    var evaluated = false
    val original  = Eval.later { evaluated = true; 99 }
    val json      = original.asJson
    evaluated shouldBe true // asJson forces evaluation
    val decoded = json.as[Eval[Int]]
    decoded.isRight shouldBe true
    decoded.toOption.get.value shouldBe 99
  }

  it should "round-trip Eval with string value" in {
    val original = Eval.now("hello world")
    val decoded  = original.asJson.as[Eval[String]]
    decoded.isRight shouldBe true
    decoded.toOption.get.value shouldBe "hello world"
  }

  // ---------------------------------------------------------------------------
  // Error decoder paths (malformed JSON)
  // ---------------------------------------------------------------------------

  "CType decoder" should "fail on missing tag field" in {
    val json = Json.obj("notTag" -> Json.fromString("CString"))
    json.as[CType].isLeft shouldBe true
  }

  it should "fail on non-string tag" in {
    val json = Json.obj("tag" -> Json.fromInt(42))
    json.as[CType].isLeft shouldBe true
  }

  it should "fail when CList is missing valuesType" in {
    val json = Json.obj("tag" -> Json.fromString("CList"))
    json.as[CType].isLeft shouldBe true
  }

  it should "fail when CMap is missing keysType" in {
    val json = Json.obj("tag" -> Json.fromString("CMap"), "valuesType" -> Json.obj("tag" -> Json.fromString("CInt")))
    json.as[CType].isLeft shouldBe true
  }

  it should "fail when CMap is missing valuesType" in {
    val json = Json.obj("tag" -> Json.fromString("CMap"), "keysType" -> Json.obj("tag" -> Json.fromString("CString")))
    json.as[CType].isLeft shouldBe true
  }

  it should "fail when CProduct structure is not a map" in {
    val json = Json.obj("tag" -> Json.fromString("CProduct"), "structure" -> Json.fromString("invalid"))
    json.as[CType].isLeft shouldBe true
  }

  it should "fail when CUnion structure is not a map" in {
    val json = Json.obj("tag" -> Json.fromString("CUnion"), "structure" -> Json.arr())
    json.as[CType].isLeft shouldBe true
  }

  it should "fail when COptional is missing innerType" in {
    val json = Json.obj("tag" -> Json.fromString("COptional"))
    json.as[CType].isLeft shouldBe true
  }

  "CValue decoder" should "fail on missing tag field" in {
    val json = Json.obj("value" -> Json.fromString("hello"))
    json.as[CValue].isLeft shouldBe true
  }

  it should "fail on CString with wrong value type" in {
    val json = Json.obj("tag" -> Json.fromString("CString"), "value" -> Json.fromInt(42))
    json.as[CValue].isLeft shouldBe true
  }

  it should "fail on CInt with wrong value type" in {
    val json = Json.obj("tag" -> Json.fromString("CInt"), "value" -> Json.fromString("notanint"))
    json.as[CValue].isLeft shouldBe true
  }

  it should "fail on CFloat with wrong value type" in {
    val json = Json.obj("tag" -> Json.fromString("CFloat"), "value" -> Json.fromString("notafloat"))
    json.as[CValue].isLeft shouldBe true
  }

  it should "fail on CBoolean with wrong value type" in {
    val json = Json.obj("tag" -> Json.fromString("CBoolean"), "value" -> Json.fromString("notabool"))
    json.as[CValue].isLeft shouldBe true
  }

  it should "fail on CList missing subtype" in {
    val json = Json.obj(
      "tag"   -> Json.fromString("CList"),
      "value" -> Json.arr()
    )
    json.as[CValue].isLeft shouldBe true
  }

  it should "fail on CMap with non-array value" in {
    val json = Json.obj(
      "tag"        -> Json.fromString("CMap"),
      "value"      -> Json.fromString("bad"),
      "keysType"   -> Json.obj("tag" -> Json.fromString("CString")),
      "valuesType" -> Json.obj("tag" -> Json.fromString("CInt"))
    )
    json.as[CValue].isLeft shouldBe true
  }

  it should "fail on CProduct missing structure" in {
    val json = Json.obj(
      "tag"   -> Json.fromString("CProduct"),
      "value" -> Json.obj()
    )
    json.as[CValue].isLeft shouldBe true
  }

  it should "fail on CUnion missing unionTag" in {
    val json = Json.obj(
      "tag"       -> Json.fromString("CUnion"),
      "value"     -> Json.obj("tag" -> Json.fromString("CString"), "value" -> Json.fromString("hi")),
      "structure" -> Json.obj()
    )
    json.as[CValue].isLeft shouldBe true
  }

  it should "fail on CSome missing innerType" in {
    val json = Json.obj(
      "tag"   -> Json.fromString("CSome"),
      "value" -> Json.obj("tag" -> Json.fromString("CInt"), "value" -> Json.fromInt(1))
    )
    json.as[CValue].isLeft shouldBe true
  }

  it should "fail on CNone missing innerType" in {
    val json = Json.obj("tag" -> Json.fromString("CNone"))
    json.as[CValue].isLeft shouldBe true
  }

  "Module.Status decoder" should "fail on missing tag field" in {
    val json = Json.obj("latency" -> Json.fromLong(100))
    json.as[Module.Status].isLeft shouldBe true
  }

  it should "fail on Fired missing latency" in {
    val json = Json.obj("tag" -> Json.fromString("Fired"))
    json.as[Module.Status].isLeft shouldBe true
  }

  it should "fail on Timed missing latency" in {
    val json = Json.obj("tag" -> Json.fromString("Timed"))
    json.as[Module.Status].isLeft shouldBe true
  }

  it should "fail on Failed missing error" in {
    val json = Json.obj("tag" -> Json.fromString("Failed"))
    json.as[Module.Status].isLeft shouldBe true
  }

  it should "fail on Fired with non-numeric latency" in {
    val json = Json.obj("tag" -> Json.fromString("Fired"), "latency" -> Json.fromString("fast"))
    json.as[Module.Status].isLeft shouldBe true
  }

  "Instant decoder" should "throw on non-ISO string (Instant.parse propagates exception)" in {
    val json = Json.fromString("not-a-date")
    // The decoder uses .map(Instant.parse) which throws DateTimeParseException
    // rather than wrapping it in a DecodingFailure
    assertThrows[java.time.format.DateTimeParseException] {
      json.as[Instant]
    }
  }

  it should "fail on numeric input" in {
    val json = Json.fromLong(1234567890L)
    json.as[Instant].isLeft shouldBe true
  }

  "ComponentMetadata decoder" should "fail on missing name" in {
    val json = Json.obj(
      "description"  -> Json.fromString("desc"),
      "tags"         -> Json.arr(),
      "majorVersion" -> Json.fromInt(1),
      "minorVersion" -> Json.fromInt(0)
    )
    json.as[ComponentMetadata].isLeft shouldBe true
  }

  it should "fail on wrong type for tags" in {
    val json = Json.obj(
      "name"         -> Json.fromString("mod"),
      "description"  -> Json.fromString("desc"),
      "tags"         -> Json.fromString("notalist"),
      "majorVersion" -> Json.fromInt(1),
      "minorVersion" -> Json.fromInt(0)
    )
    json.as[ComponentMetadata].isLeft shouldBe true
  }

  "ModuleConfig decoder" should "fail on missing inputsTimeout" in {
    val json = Json.obj("moduleTimeout" -> Json.fromLong(3000))
    json.as[ModuleConfig].isLeft shouldBe true
  }

  it should "fail on non-numeric timeout" in {
    val json = Json.obj(
      "inputsTimeout" -> Json.fromString("slow"),
      "moduleTimeout" -> Json.fromLong(3000)
    )
    json.as[ModuleConfig].isLeft shouldBe true
  }

  "DataNodeSpec decoder" should "fail on missing name" in {
    val json = Json.obj(
      "nicknames"       -> Json.obj(),
      "cType"           -> Json.obj("tag" -> Json.fromString("CString")),
      "transformInputs" -> Json.obj()
    )
    json.as[DataNodeSpec].isLeft shouldBe true
  }

  "DagSpec decoder" should "fail on missing metadata" in {
    val json = Json.obj(
      "modules"         -> Json.obj(),
      "data"            -> Json.obj(),
      "inEdges"         -> Json.arr(),
      "outEdges"        -> Json.arr(),
      "declaredOutputs" -> Json.arr(),
      "outputBindings"  -> Json.obj()
    )
    json.as[DagSpec].isLeft shouldBe true
  }

  "PipelineImage decoder" should "fail on missing structuralHash" in {
    val json = Json.obj(
      "syntacticHash" -> Json.fromString("h"),
      "dagSpec"       -> Json.obj(),
      "moduleOptions" -> Json.obj(),
      "compiledAt"    -> Json.fromString("2026-01-01T00:00:00Z")
    )
    json.as[PipelineImage].isLeft shouldBe true
  }

  it should "throw on invalid compiledAt (Instant.parse propagates exception)" in {
    val dagJson = DagSpec(
      ComponentMetadata("P", "P", List.empty, 1, 0),
      Map.empty, Map.empty, Set.empty, Set.empty, List.empty, Map.empty
    ).asJson
    val json = Json.obj(
      "structuralHash" -> Json.fromString("h1"),
      "syntacticHash"  -> Json.fromString("h2"),
      "dagSpec"        -> dagJson,
      "moduleOptions"  -> Json.obj(),
      "compiledAt"     -> Json.fromString("bad-date"),
      "sourceHash"     -> Json.Null
    )
    // The Instant decoder uses .map(Instant.parse) which throws DateTimeParseException
    assertThrows[java.time.format.DateTimeParseException] {
      json.as[PipelineImage]
    }
  }

  // ---------------------------------------------------------------------------
  // CValue.CList -- additional nesting edge cases
  // ---------------------------------------------------------------------------

  "CValue.CList codec" should "round-trip empty list" in {
    val original: CValue = CValue.CList(Vector.empty, CType.CString)
    val decoded          = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip list of lists" in {
    val inner1 = CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2)), CType.CInt)
    val inner2 = CValue.CList(Vector(CValue.CInt(3)), CType.CInt)
    val original: CValue = CValue.CList(
      Vector(inner1, inner2),
      CType.CList(CType.CInt)
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  it should "round-trip list of optionals" in {
    val original: CValue = CValue.CList(
      Vector(
        CValue.CSome(CValue.CString("a"), CType.CString),
        CValue.CNone(CType.CString),
        CValue.CSome(CValue.CString("b"), CType.CString)
      ),
      CType.COptional(CType.CString)
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }

  // ---------------------------------------------------------------------------
  // CValue.CMap with duplicate-style entries (multiple same-type pairs)
  // ---------------------------------------------------------------------------

  "CValue.CMap codec" should "round-trip a single-entry map" in {
    val original: CValue = CValue.CMap(
      Vector((CValue.CString("only"), CValue.CFloat(3.14))),
      CType.CString,
      CType.CFloat
    )
    val decoded = original.asJson.as[CValue]
    decoded shouldBe Right(original)
  }
}
