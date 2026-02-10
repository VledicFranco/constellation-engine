package io.constellation

import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CirceJsonSuspensionCodecExtendedTest extends AnyFlatSpec with Matchers {

  private val codec = CirceJsonSuspensionCodec

  private def mkSuspended(
      inputs: Map[String, CValue] = Map("text" -> CValue.CString("hello")),
      computed: Map[UUID, CValue] = Map.empty,
      statuses: Map[UUID, String] = Map.empty,
      moduleOptions: Map[UUID, ModuleCallOptions] = Map.empty,
      resumptionCount: Int = 0
  ): SuspendedExecution = SuspendedExecution(
    executionId = UUID.randomUUID(),
    structuralHash = "test-hash-extended",
    resumptionCount = resumptionCount,
    dagSpec = DagSpec.empty("test"),
    moduleOptions = moduleOptions,
    providedInputs = inputs,
    computedValues = computed,
    moduleStatuses = statuses
  )

  // ===== Complex round-trip tests =====

  "CirceJsonSuspensionCodecExtended" should "round-trip a complex SuspendedExecution with nested CValues, module options, and multiple inputs" in {
    val modId1  = UUID.randomUUID()
    val modId2  = UUID.randomUUID()
    val nodeId1 = UUID.randomUUID()
    val nodeId2 = UUID.randomUUID()

    val complexInputs = Map(
      "text"  -> CValue.CString("hello world"),
      "count" -> CValue.CInt(42L),
      "ratio" -> CValue.CFloat(3.14),
      "flag"  -> CValue.CBoolean(false),
      "items" -> CValue.CList(
        Vector(CValue.CString("a"), CValue.CString("b"), CValue.CString("c")),
        CType.CString
      )
    )

    val computedValues = Map(
      nodeId1 -> CValue.CString("result-1"),
      nodeId2 -> CValue.CInt(100L)
    )

    val moduleStatuses = Map(
      modId1 -> "Fired",
      modId2 -> "Unfired"
    )

    val moduleOptions = Map(
      modId1 -> ModuleCallOptions(retry = Some(3), timeoutMs = Some(5000L), cacheMs = Some(60000L)),
      modId2 -> ModuleCallOptions(backoff = Some("exponential"), priority = Some(80))
    )

    val original = mkSuspended(
      inputs = complexInputs,
      computed = computedValues,
      statuses = moduleStatuses,
      moduleOptions = moduleOptions,
      resumptionCount = 5
    )

    val encoded = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true

    val result = decoded.toOption.get
    result.executionId shouldBe original.executionId
    result.structuralHash shouldBe original.structuralHash
    result.resumptionCount shouldBe 5
    result.providedInputs shouldBe complexInputs
    result.computedValues shouldBe computedValues
    result.moduleStatuses shouldBe moduleStatuses
    result.moduleOptions shouldBe moduleOptions
  }

  // ===== CProduct round-trip =====

  it should "round-trip with nested CProduct values" in {
    val innerProduct = CValue.CProduct(
      Map("street" -> CValue.CString("123 Main St"), "zip" -> CValue.CInt(90210L)),
      Map("street" -> CType.CString, "zip"                 -> CType.CInt)
    )
    val outerProduct = CValue.CProduct(
      Map(
        "name"    -> CValue.CString("Alice"),
        "age"     -> CValue.CInt(30L),
        "address" -> innerProduct
      ),
      Map(
        "name"    -> CType.CString,
        "age"     -> CType.CInt,
        "address" -> CType.CProduct(Map("street" -> CType.CString, "zip" -> CType.CInt))
      )
    )
    val inputs = Map("person" -> outerProduct)

    val original = mkSuspended(inputs = inputs)
    val encoded  = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true
    decoded.toOption.get.providedInputs shouldBe inputs
  }

  // ===== CUnion round-trip =====

  it should "round-trip with CUnion values" in {
    val unionValue = CValue.CUnion(
      CValue.CString("hello"),
      Map("text" -> CType.CString, "number" -> CType.CInt),
      "text"
    )
    val inputs = Map("tagged" -> unionValue)

    val original = mkSuspended(inputs = inputs)
    val encoded  = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true
    decoded.toOption.get.providedInputs shouldBe inputs
  }

  // ===== CMap round-trip =====

  it should "round-trip with CMap values" in {
    val mapValue = CValue.CMap(
      Vector(
        (CValue.CString("key1"), CValue.CInt(1L)),
        (CValue.CString("key2"), CValue.CInt(2L)),
        (CValue.CString("key3"), CValue.CInt(3L))
      ),
      CType.CString,
      CType.CInt
    )
    val inputs = Map("lookup" -> mapValue)

    val original = mkSuspended(inputs = inputs)
    val encoded  = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true
    decoded.toOption.get.providedInputs shouldBe inputs
  }

  // ===== CSome and CNone round-trip =====

  it should "round-trip with nested CSome containing CProduct" in {
    val product = CValue.CProduct(
      Map("x" -> CValue.CInt(10L), "y" -> CValue.CInt(20L)),
      Map("x" -> CType.CInt, "y"       -> CType.CInt)
    )
    val someProduct =
      CValue.CSome(product, CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CInt)))
    val inputs = Map("maybePoint" -> someProduct)

    val original = mkSuspended(inputs = inputs)
    val decoded  = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.providedInputs shouldBe inputs
  }

  it should "round-trip with CNone of complex inner type" in {
    val noneList = CValue.CNone(CType.CList(CType.CString))
    val inputs   = Map("maybeItems" -> noneList)

    val original = mkSuspended(inputs = inputs)
    val decoded  = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.providedInputs shouldBe inputs
  }

  // ===== Non-empty moduleStatuses =====

  it should "round-trip with multiple module statuses" in {
    val id1 = UUID.randomUUID()
    val id2 = UUID.randomUUID()
    val id3 = UUID.randomUUID()
    val statuses = Map(
      id1 -> "Fired",
      id2 -> "Failed",
      id3 -> "Timed"
    )

    val original = mkSuspended(statuses = statuses)
    val encoded  = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true
    decoded.toOption.get.moduleStatuses shouldBe statuses
  }

  // ===== Non-empty moduleOptions with all fields =====

  it should "round-trip with module options containing all fields" in {
    val modId = UUID.randomUUID()
    val fullOptions = ModuleCallOptions(
      retry = Some(5),
      timeoutMs = Some(10000L),
      delayMs = Some(500L),
      backoff = Some("exponential"),
      cacheMs = Some(30000L),
      cacheBackend = Some("redis"),
      throttleCount = Some(10),
      throttlePerMs = Some(1000L),
      concurrency = Some(4),
      onError = Some("skip"),
      lazyEval = Some(true),
      priority = Some(90)
    )
    val options = Map(modId -> fullOptions)

    val original = mkSuspended(moduleOptions = options)
    val decoded  = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.moduleOptions shouldBe options
  }

  it should "round-trip with empty module options (ModuleCallOptions.empty)" in {
    val modId   = UUID.randomUUID()
    val options = Map(modId -> ModuleCallOptions.empty)

    val original = mkSuspended(moduleOptions = options)
    val decoded  = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.moduleOptions shouldBe options
  }

  // ===== Error cases =====

  it should "return Left with CodecError for completely malformed JSON" in {
    val result = codec.decode("{{{{not valid json at all".getBytes("UTF-8"))
    result.isLeft shouldBe true
    val error = result.swap.toOption.get
    error.message should include("Failed to parse JSON")
    error.cause shouldBe defined
  }

  it should "return Left with CodecError for empty byte array" in {
    val result = codec.decode(Array.empty[Byte])
    result.isLeft shouldBe true
  }

  it should "return Left with CodecError for JSON with missing executionId field" in {
    val jsonMissingExecutionId =
      """{
        |  "structuralHash": "abc",
        |  "resumptionCount": 0,
        |  "dagSpec": {},
        |  "moduleOptions": {},
        |  "providedInputs": {},
        |  "computedValues": {},
        |  "moduleStatuses": {}
        |}""".stripMargin

    val result = codec.decode(jsonMissingExecutionId.getBytes("UTF-8"))
    result.isLeft shouldBe true
    val error = result.swap.toOption.get
    error.message should include("Failed to decode SuspendedExecution")
  }

  it should "return Left with CodecError for JSON with missing structuralHash field" in {
    val jsonMissingHash =
      s"""{
         |  "executionId": "${UUID.randomUUID()}",
         |  "resumptionCount": 0,
         |  "dagSpec": {},
         |  "moduleOptions": {},
         |  "providedInputs": {},
         |  "computedValues": {},
         |  "moduleStatuses": {}
         |}""".stripMargin

    val result = codec.decode(jsonMissingHash.getBytes("UTF-8"))
    result.isLeft shouldBe true
  }

  it should "throw on JSON with invalid UUID in computedValues keys" in {
    val jsonBadUuid =
      s"""{
         |  "executionId": "${UUID.randomUUID()}",
         |  "structuralHash": "hash",
         |  "resumptionCount": 0,
         |  "dagSpec": {"metadata":{"name":"t","description":"","tags":[],"majorVersion":0,"minorVersion":1},"modules":{},"data":{},"inEdges":[],"outEdges":[],"declaredOutputs":[],"outputBindings":{}},
         |  "moduleOptions": {},
         |  "providedInputs": {},
         |  "computedValues": {"not-a-uuid": {"tag":"CInt","value":42}},
         |  "moduleStatuses": {}
         |}""".stripMargin

    // The decoder parses keys as Strings then calls UUID.fromString in the yield block,
    // which throws IllegalArgumentException (not caught by the circe decoder layer)
    an[IllegalArgumentException] should be thrownBy {
      codec.decode(jsonBadUuid.getBytes("UTF-8"))
    }
  }

  it should "return Left for JSON with wrong type for resumptionCount" in {
    val jsonBadType =
      s"""{
         |  "executionId": "${UUID.randomUUID()}",
         |  "structuralHash": "hash",
         |  "resumptionCount": "not-a-number",
         |  "dagSpec": {},
         |  "moduleOptions": {},
         |  "providedInputs": {},
         |  "computedValues": {},
         |  "moduleStatuses": {}
         |}""".stripMargin

    val result = codec.decode(jsonBadType.getBytes("UTF-8"))
    result.isLeft shouldBe true
  }

  // ===== Computed values with various CValue types =====

  it should "round-trip with computed values containing CProduct, CList, and CBoolean" in {
    val nodeId1 = UUID.randomUUID()
    val nodeId2 = UUID.randomUUID()
    val nodeId3 = UUID.randomUUID()

    val computed = Map(
      nodeId1 -> CValue.CProduct(
        Map("a" -> CValue.CInt(1L), "b" -> CValue.CString("x")),
        Map("a" -> CType.CInt, "b"      -> CType.CString)
      ),
      nodeId2 -> CValue.CList(
        Vector(CValue.CFloat(1.1), CValue.CFloat(2.2)),
        CType.CFloat
      ),
      nodeId3 -> CValue.CBoolean(true)
    )

    val original = mkSuspended(computed = computed)
    val decoded  = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.computedValues shouldBe computed
  }

  // ===== Mixed CValues in inputs =====

  it should "round-trip with all CValue types simultaneously in inputs" in {
    val inputs = Map(
      "str"  -> CValue.CString("hello"),
      "num"  -> CValue.CInt(99L),
      "dec"  -> CValue.CFloat(2.718),
      "bool" -> CValue.CBoolean(true),
      "list" -> CValue.CList(Vector(CValue.CInt(1L), CValue.CInt(2L)), CType.CInt),
      "map" -> CValue.CMap(
        Vector((CValue.CString("a"), CValue.CFloat(1.0))),
        CType.CString,
        CType.CFloat
      ),
      "product" -> CValue.CProduct(
        Map("f1" -> CValue.CBoolean(false)),
        Map("f1" -> CType.CBoolean)
      ),
      "union" -> CValue.CUnion(
        CValue.CInt(42L),
        Map("num" -> CType.CInt, "str" -> CType.CString),
        "num"
      ),
      "some" -> CValue.CSome(CValue.CString("present"), CType.CString),
      "none" -> CValue.CNone(CType.CInt)
    )

    val original = mkSuspended(inputs = inputs)
    val encoded  = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true
    decoded.toOption.get.providedInputs shouldBe inputs
  }

  // ===== Empty collections =====

  it should "round-trip with empty CList" in {
    val inputs = Map("empty" -> CValue.CList(Vector.empty, CType.CString))

    val original = mkSuspended(inputs = inputs)
    val decoded  = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.providedInputs shouldBe inputs
  }

  it should "round-trip with empty CMap" in {
    val inputs = Map("empty" -> CValue.CMap(Vector.empty, CType.CString, CType.CInt))

    val original = mkSuspended(inputs = inputs)
    val decoded  = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.providedInputs shouldBe inputs
  }

  it should "round-trip with empty CProduct" in {
    val inputs = Map("empty" -> CValue.CProduct(Map.empty, Map.empty))

    val original = mkSuspended(inputs = inputs)
    val decoded  = codec.decode(codec.encode(original).toOption.get).toOption.get
    decoded.providedInputs shouldBe inputs
  }

  // ===== DagSpec with non-empty structure =====

  it should "round-trip with a non-empty DagSpec" in {
    val moduleId = UUID.randomUUID()
    val dataId   = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata("TestPipeline", "A test pipeline", List("test", "demo"), 1, 2),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Uppercases text", List("text"), 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        dataId   -> DataNodeSpec("text", Map(moduleId -> "text"), CType.CString),
        outputId -> DataNodeSpec("result", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((dataId, moduleId)),
      outEdges = Set((moduleId, outputId)),
      declaredOutputs = List("result"),
      outputBindings = Map("result" -> outputId)
    )

    val original = SuspendedExecution(
      executionId = UUID.randomUUID(),
      structuralHash = "dag-hash-42",
      resumptionCount = 1,
      dagSpec = dag,
      moduleOptions = Map.empty,
      providedInputs = Map("text" -> CValue.CString("hello")),
      computedValues = Map.empty,
      moduleStatuses = Map.empty
    )

    val encoded = codec.encode(original)
    encoded.isRight shouldBe true

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true

    val result = decoded.toOption.get
    result.dagSpec.metadata.name shouldBe "TestPipeline"
    result.dagSpec.modules should have size 1
    result.dagSpec.data should have size 2
    result.dagSpec.inEdges should have size 1
    result.dagSpec.outEdges should have size 1
    result.dagSpec.declaredOutputs shouldBe List("result")
    result.dagSpec.outputBindings should contain key "result"
  }

  // ===== Encode produces valid UTF-8 bytes =====

  it should "produce valid UTF-8 JSON bytes that can be parsed back" in {
    val original =
      mkSuspended(inputs = Map("unicode" -> CValue.CString("hello \u00e9\u00e0\u00fc\u4e16\u754c")))
    val encoded = codec.encode(original)
    encoded.isRight shouldBe true

    val jsonStr = new String(encoded.toOption.get, "UTF-8")
    jsonStr should include("hello")

    val decoded = codec.decode(encoded.toOption.get)
    decoded.isRight shouldBe true
    decoded.toOption.get.providedInputs("unicode") shouldBe CValue.CString(
      "hello \u00e9\u00e0\u00fc\u4e16\u754c"
    )
  }
}
