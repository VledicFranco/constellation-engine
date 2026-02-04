package io.constellation.http

import io.circe.{parser, Json}
import io.circe.syntax.*
import io.constellation.ComponentMetadata
import io.constellation.http.ApiModels.*
import io.constellation.http.ApiModels.given
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ApiModelsTest extends AnyFlatSpec with Matchers {

  // ========== CompileRequest Tests ==========

  "CompileRequest" should "serialize to JSON" in {
    val request = CompileRequest(
      source = "in x: Int\nout x",
      dagName = Some("test-dag")
    )

    val json = request.asJson.noSpaces

    json should include("\"source\":")
    json should include("\"dagName\":\"test-dag\"")
  }

  it should "deserialize from JSON with dagName (legacy)" in {
    val jsonStr = """{"source":"in x: Int","dagName":"my-dag"}"""
    val parsed  = parser.decode[CompileRequest](jsonStr)

    parsed match {
      case Right(request) =>
        request.source shouldBe "in x: Int"
        request.dagName shouldBe Some("my-dag")
        request.effectiveName shouldBe Some("my-dag")
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  it should "deserialize from JSON with name (new API)" in {
    val jsonStr = """{"source":"in x: Int","name":"my-program"}"""
    val parsed  = parser.decode[CompileRequest](jsonStr)

    parsed match {
      case Right(request) =>
        request.source shouldBe "in x: Int"
        request.name shouldBe Some("my-program")
        request.effectiveName shouldBe Some("my-program")
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  it should "prefer name over dagName when both present" in {
    val jsonStr = """{"source":"in x: Int","name":"new-name","dagName":"old-name"}"""
    val parsed  = parser.decode[CompileRequest](jsonStr)

    parsed match {
      case Right(request) =>
        request.effectiveName shouldBe Some("new-name")
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  it should "round-trip through JSON" in {
    val original = CompileRequest("in y: String\nout y", dagName = Some("roundtrip-dag"))
    val json     = original.asJson
    val parsed   = json.as[CompileRequest]

    parsed shouldBe Right(original)
  }

  // ========== CompileResponse Tests ==========

  "CompileResponse" should "serialize success response" in {
    val response = CompileResponse(
      success = true,
      dagName = Some("compiled-dag"),
      errors = List.empty
    )

    val json = response.asJson.noSpaces

    json should include("\"success\":true")
    json should include("\"dagName\":\"compiled-dag\"")
    json should include("\"errors\":[]")
  }

  it should "serialize success response with hashes" in {
    val response = CompileResponse(
      success = true,
      structuralHash = Some("abc123"),
      syntacticHash = Some("def456"),
      name = Some("my-program")
    )

    val json = response.asJson.noSpaces

    json should include("\"structuralHash\":\"abc123\"")
    json should include("\"syntacticHash\":\"def456\"")
    json should include("\"name\":\"my-program\"")
  }

  it should "serialize error response" in {
    val response = CompileResponse(
      success = false,
      dagName = None,
      errors = List("Line 1: Undefined variable", "Line 2: Type mismatch")
    )

    val json = response.asJson.noSpaces

    json should include("\"success\":false")
    json should include("\"errors\":[")
    json should include("Undefined variable")
    json should include("Type mismatch")
  }

  it should "round-trip through JSON" in {
    val original = CompileResponse(true, dagName = Some("dag"), errors = List("warning"))
    val json     = original.asJson
    val parsed   = json.as[CompileResponse]

    parsed shouldBe Right(original)
  }

  // ========== ExecuteRequest Tests ==========

  "ExecuteRequest" should "serialize with JSON inputs (new ref API)" in {
    val request = ExecuteRequest(
      ref = Some("my-program"),
      inputs = Map(
        "x"    -> Json.fromInt(42),
        "name" -> Json.fromString("Alice")
      )
    )

    val json = request.asJson.noSpaces

    json should include("\"ref\":\"my-program\"")
    json should include("\"x\":42")
    json should include("\"name\":\"Alice\"")
  }

  it should "deserialize with dagName (legacy)" in {
    val jsonStr = """{"dagName":"complex","inputs":{"list":[1,2,3],"nested":{"a":"b"}}}"""
    val parsed  = parser.decode[ExecuteRequest](jsonStr)

    parsed match {
      case Right(request) =>
        request.dagName shouldBe Some("complex")
        request.effectiveRef shouldBe Some("complex")
        request.inputs.keySet should contain allOf ("list", "nested")
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  it should "deserialize with ref (new API)" in {
    val jsonStr = """{"ref":"sha256:abc123","inputs":{"x":1}}"""
    val parsed  = parser.decode[ExecuteRequest](jsonStr)

    parsed match {
      case Right(request) =>
        request.ref shouldBe Some("sha256:abc123")
        request.effectiveRef shouldBe Some("sha256:abc123")
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  it should "handle empty inputs" in {
    val request = ExecuteRequest(dagName = Some("empty-inputs"), inputs = Map.empty)
    val json    = request.asJson
    val parsed  = json.as[ExecuteRequest]

    parsed shouldBe Right(request)
  }

  // ========== ExecuteResponse Tests ==========

  "ExecuteResponse" should "serialize success response" in {
    val response = ExecuteResponse(
      success = true,
      outputs = Map("result" -> Json.fromInt(100)),
      error = None
    )

    val json = response.asJson.noSpaces

    json should include("\"success\":true")
    json should include("\"result\":100")
  }

  it should "serialize error response" in {
    val response = ExecuteResponse(
      success = false,
      outputs = Map.empty,
      error = Some("Execution failed")
    )

    val json = response.asJson.noSpaces

    json should include("\"success\":false")
    json should include("\"error\":\"Execution failed\"")
  }

  it should "round-trip through JSON" in {
    val original = ExecuteResponse(true, Map("x" -> Json.fromBoolean(true)), None)
    val json     = original.asJson
    val parsed   = json.as[ExecuteResponse]

    parsed shouldBe Right(original)
  }

  it should "round-trip with suspension fields" in {
    val original = ExecuteResponse(
      success = true,
      outputs = Map("x" -> Json.fromInt(1)),
      status = Some("suspended"),
      executionId = Some("550e8400-e29b-41d4-a716-446655440000"),
      missingInputs = Some(Map("y" -> "CString")),
      pendingOutputs = Some(List("result")),
      resumptionCount = Some(0)
    )
    val json   = original.asJson
    val parsed = json.as[ExecuteResponse]

    parsed shouldBe Right(original)
  }

  it should "decode JSON without suspension fields as all None (backward compat)" in {
    val jsonStr = """{"success":true,"outputs":{"x":1}}"""
    val parsed  = parser.decode[ExecuteResponse](jsonStr)

    parsed match {
      case Right(response) =>
        response.success shouldBe true
        response.status shouldBe None
        response.executionId shouldBe None
        response.missingInputs shouldBe None
        response.pendingOutputs shouldBe None
        response.resumptionCount shouldBe None
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== RunRequest Tests ==========

  "RunRequest" should "serialize with source and inputs" in {
    val request = RunRequest(
      source = "in x: Int\nout x",
      inputs = Map("x" -> Json.fromInt(5))
    )

    val json = request.asJson.noSpaces

    json should include("\"source\":")
    json should include("\"inputs\":")
  }

  it should "round-trip through JSON" in {
    val original = RunRequest("in y: String", Map("y" -> Json.fromString("test")))
    val json     = original.asJson
    val parsed   = json.as[RunRequest]

    parsed shouldBe Right(original)
  }

  // ========== RunResponse Tests ==========

  "RunResponse" should "serialize success response" in {
    val response = RunResponse(
      success = true,
      outputs = Map("result" -> Json.fromString("output")),
      compilationErrors = List.empty,
      error = None
    )

    val json = response.asJson.noSpaces

    json should include("\"success\":true")
    json should include("\"compilationErrors\":[]")
  }

  it should "serialize success response with structuralHash" in {
    val response = RunResponse(
      success = true,
      outputs = Map("result" -> Json.fromString("output")),
      structuralHash = Some("abc123")
    )

    val json = response.asJson.noSpaces

    json should include("\"structuralHash\":\"abc123\"")
  }

  it should "serialize with compilation errors" in {
    val response = RunResponse(
      success = false,
      outputs = Map.empty,
      compilationErrors = List("Error 1", "Error 2"),
      error = None
    )

    val json = response.asJson.noSpaces

    json should include("\"success\":false")
    json should include("\"compilationErrors\":[")
    json should include("Error 1")
  }

  it should "serialize with runtime error" in {
    val response = RunResponse(
      success = false,
      outputs = Map.empty,
      compilationErrors = List.empty,
      error = Some("Runtime error occurred")
    )

    val json = response.asJson.noSpaces

    json should include("\"error\":\"Runtime error occurred\"")
  }

  it should "round-trip through JSON" in {
    val original = RunResponse(true, Map("a" -> Json.fromInt(1)), error = None)
    val json     = original.asJson
    val parsed   = json.as[RunResponse]

    parsed shouldBe Right(original)
  }

  it should "round-trip with suspension fields" in {
    val original = RunResponse(
      success = true,
      outputs = Map("x" -> Json.fromInt(1)),
      structuralHash = Some("abc123"),
      status = Some("suspended"),
      executionId = Some("550e8400-e29b-41d4-a716-446655440000"),
      missingInputs = Some(Map("y" -> "CInt")),
      pendingOutputs = Some(List("result")),
      resumptionCount = Some(1)
    )
    val json   = original.asJson
    val parsed = json.as[RunResponse]

    parsed shouldBe Right(original)
  }

  it should "decode JSON without suspension fields as all None (backward compat)" in {
    val jsonStr = """{"success":true,"outputs":{"x":1},"compilationErrors":[]}"""
    val parsed  = parser.decode[RunResponse](jsonStr)

    parsed match {
      case Right(response) =>
        response.success shouldBe true
        response.status shouldBe None
        response.executionId shouldBe None
        response.missingInputs shouldBe None
        response.pendingOutputs shouldBe None
        response.resumptionCount shouldBe None
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== PipelineSummary Tests ==========

  "PipelineSummary" should "serialize all fields" in {
    val summary = PipelineSummary(
      structuralHash = "abc123",
      syntacticHash = "def456",
      aliases = List("my-program", "prod"),
      compiledAt = "2026-01-15T10:30:00Z",
      moduleCount = 3,
      declaredOutputs = List("result", "status")
    )

    val json = summary.asJson.noSpaces

    json should include("\"structuralHash\":\"abc123\"")
    json should include("\"aliases\":[\"my-program\",\"prod\"]")
    json should include("\"moduleCount\":3")
  }

  it should "round-trip through JSON" in {
    val original = PipelineSummary("h1", "h2", List("a"), "2026-01-01T00:00:00Z", 1, List("out"))
    val json     = original.asJson
    val parsed   = json.as[PipelineSummary]

    parsed shouldBe Right(original)
  }

  // ========== AliasRequest Tests ==========

  "AliasRequest" should "round-trip through JSON" in {
    val original = AliasRequest("abc123")
    val json     = original.asJson
    val parsed   = json.as[AliasRequest]

    parsed shouldBe Right(original)
  }

  // ========== ModuleListResponse Tests ==========

  "ModuleListResponse" should "serialize module list" in {
    val response = ModuleListResponse(
      modules = List(
        ModuleInfo(
          "Uppercase",
          "Converts to uppercase",
          "1.0",
          Map("text"   -> "String"),
          Map("result" -> "String")
        ),
        ModuleInfo(
          "Add",
          "Adds numbers",
          "1.0",
          Map("a"   -> "Int", "b" -> "Int"),
          Map("sum" -> "Int")
        )
      )
    )

    val json = response.asJson.noSpaces

    json should include("\"Uppercase\"")
    json should include("\"Add\"")
    json should include("\"inputs\":")
    json should include("\"outputs\":")
  }

  it should "serialize empty module list" in {
    val response = ModuleListResponse(List.empty)
    val json     = response.asJson.noSpaces

    json should include("\"modules\":[]")
  }

  it should "round-trip through JSON" in {
    val original = ModuleListResponse(
      List(
        ModuleInfo("Test", "Test module", "1.0", Map.empty, Map.empty)
      )
    )
    val json   = original.asJson
    val parsed = json.as[ModuleListResponse]

    parsed shouldBe Right(original)
  }

  // ========== ModuleInfo Tests ==========

  "ModuleInfo" should "serialize all fields" in {
    val info = ModuleInfo(
      name = "TestModule",
      description = "A test module",
      version = "2.1",
      inputs = Map("in1" -> "String", "in2" -> "Int"),
      outputs = Map("out" -> "Boolean")
    )

    val json = info.asJson.noSpaces

    json should include("\"name\":\"TestModule\"")
    json should include("\"description\":\"A test module\"")
    json should include("\"version\":\"2.1\"")
    json should include("\"in1\":\"String\"")
    json should include("\"out\":\"Boolean\"")
  }

  it should "round-trip through JSON" in {
    val original = ModuleInfo("M", "desc", "1.0", Map("x" -> "Int"), Map("y" -> "Int"))
    val json     = original.asJson
    val parsed   = json.as[ModuleInfo]

    parsed shouldBe Right(original)
  }

  // ========== ErrorResponse Tests ==========

  "ErrorResponse" should "serialize error info" in {
    val response = ErrorResponse(
      error = "NotFound",
      message = "The requested resource was not found"
    )

    val json = response.asJson.noSpaces

    json should include("\"error\":\"NotFound\"")
    json should include("\"message\":\"The requested resource was not found\"")
  }

  it should "round-trip through JSON" in {
    val original = ErrorResponse("TestError", "Test message")
    val json     = original.asJson
    val parsed   = json.as[ErrorResponse]

    parsed shouldBe Right(original)
  }

  // ========== NamespaceListResponse Tests ==========

  "NamespaceListResponse" should "serialize namespace list" in {
    val response = NamespaceListResponse(
      namespaces = List("stdlib.math", "stdlib.string", "custom.utils")
    )

    val json = response.asJson.noSpaces

    json should include("\"namespaces\":[")
    json should include("stdlib.math")
    json should include("stdlib.string")
    json should include("custom.utils")
  }

  it should "serialize empty namespace list" in {
    val response = NamespaceListResponse(List.empty)
    val json     = response.asJson.noSpaces

    json should include("\"namespaces\":[]")
  }

  it should "round-trip through JSON" in {
    val original = NamespaceListResponse(List("ns1", "ns2"))
    val json     = original.asJson
    val parsed   = json.as[NamespaceListResponse]

    parsed shouldBe Right(original)
  }

  // ========== FunctionInfo Tests ==========

  "FunctionInfo" should "serialize function info" in {
    val info = FunctionInfo(
      name = "add",
      qualifiedName = "stdlib.math.add",
      params = List("a: Int", "b: Int"),
      returns = "Int"
    )

    val json = info.asJson.noSpaces

    json should include("\"name\":\"add\"")
    json should include("\"qualifiedName\":\"stdlib.math.add\"")
    json should include("\"params\":[")
    json should include("\"returns\":\"Int\"")
  }

  it should "round-trip through JSON" in {
    val original = FunctionInfo("func", "ns.func", List("x: String"), "String")
    val json     = original.asJson
    val parsed   = json.as[FunctionInfo]

    parsed shouldBe Right(original)
  }

  // ========== NamespaceFunctionsResponse Tests ==========

  "NamespaceFunctionsResponse" should "serialize with functions" in {
    val response = NamespaceFunctionsResponse(
      namespace = "stdlib.math",
      functions = List(
        FunctionInfo("add", "stdlib.math.add", List("a: Int", "b: Int"), "Int"),
        FunctionInfo("multiply", "stdlib.math.multiply", List("a: Int", "b: Int"), "Int")
      )
    )

    val json = response.asJson.noSpaces

    json should include("\"namespace\":\"stdlib.math\"")
    json should include("\"functions\":[")
    json should include("\"add\"")
    json should include("\"multiply\"")
  }

  it should "serialize with empty function list" in {
    val response = NamespaceFunctionsResponse("empty.namespace", List.empty)
    val json     = response.asJson.noSpaces

    json should include("\"namespace\":\"empty.namespace\"")
    json should include("\"functions\":[]")
  }

  it should "round-trip through JSON" in {
    val original = NamespaceFunctionsResponse(
      "ns",
      List(
        FunctionInfo("f", "ns.f", List.empty, "Unit")
      )
    )
    val json   = original.asJson
    val parsed = json.as[NamespaceFunctionsResponse]

    parsed shouldBe Right(original)
  }

  // ========== ResumeRequest Tests ==========

  "ResumeRequest" should "round-trip through JSON" in {
    val original = ResumeRequest(
      additionalInputs = Some(Map("x" -> Json.fromInt(42))),
      resolvedNodes = Some(Map("computed" -> Json.fromString("value")))
    )
    val json   = original.asJson
    val parsed = json.as[ResumeRequest]

    parsed shouldBe Right(original)
  }

  it should "round-trip with empty fields" in {
    val original = ResumeRequest()
    val json     = original.asJson
    val parsed   = json.as[ResumeRequest]

    parsed shouldBe Right(original)
  }

  it should "round-trip with only additionalInputs" in {
    val original = ResumeRequest(additionalInputs = Some(Map("y" -> Json.fromString("hello"))))
    val json     = original.asJson
    val parsed   = json.as[ResumeRequest]

    parsed shouldBe Right(original)
  }

  it should "deserialize from minimal JSON" in {
    val jsonStr = """{}"""
    val parsed  = parser.decode[ResumeRequest](jsonStr)

    parsed match {
      case Right(request) =>
        request.additionalInputs shouldBe None
        request.resolvedNodes shouldBe None
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== ExecutionSummary Tests ==========

  "ExecutionSummary" should "round-trip through JSON" in {
    val original = ExecutionSummary(
      executionId = "550e8400-e29b-41d4-a716-446655440000",
      structuralHash = "abc123def456",
      resumptionCount = 2,
      missingInputs = Map("y" -> "CInt", "z" -> "CString"),
      createdAt = "2026-01-15T10:30:00Z"
    )
    val json   = original.asJson
    val parsed = json.as[ExecutionSummary]

    parsed shouldBe Right(original)
  }

  it should "serialize all fields" in {
    val summary = ExecutionSummary(
      executionId = "test-uuid",
      structuralHash = "hash123",
      resumptionCount = 0,
      missingInputs = Map("x" -> "CInt"),
      createdAt = "2026-02-01T00:00:00Z"
    )

    val json = summary.asJson.noSpaces

    json should include("\"executionId\":\"test-uuid\"")
    json should include("\"structuralHash\":\"hash123\"")
    json should include("\"resumptionCount\":0")
    json should include("\"missingInputs\":")
    json should include("\"createdAt\":\"2026-02-01T00:00:00Z\"")
  }

  // ========== ExecutionListResponse Tests ==========

  "ExecutionListResponse" should "round-trip through JSON" in {
    val original = ExecutionListResponse(
      executions = List(
        ExecutionSummary("id1", "hash1", 0, Map("x" -> "CInt"), "2026-01-01T00:00:00Z"),
        ExecutionSummary("id2", "hash2", 1, Map.empty, "2026-01-02T00:00:00Z")
      )
    )
    val json   = original.asJson
    val parsed = json.as[ExecutionListResponse]

    parsed shouldBe Right(original)
  }

  it should "serialize empty list" in {
    val response = ExecutionListResponse(List.empty)
    val json     = response.asJson.noSpaces

    json should include("\"executions\":[]")
  }

  // ========== ComponentMetadata Codec Tests ==========

  "ComponentMetadata codec" should "serialize correctly" in {
    val metadata = ComponentMetadata(
      name = "TestComponent",
      description = "A test component",
      tags = List("test", "example"),
      majorVersion = 2,
      minorVersion = 5
    )

    val json = metadata.asJson.noSpaces

    json should include("\"name\":\"TestComponent\"")
    json should include("\"description\":\"A test component\"")
    json should include("\"tags\":[\"test\",\"example\"]")
    json should include("\"majorVersion\":2")
    json should include("\"minorVersion\":5")
  }

  it should "round-trip through JSON" in {
    val original = ComponentMetadata("Test", "Desc", List("tag"), 1, 0)
    val json     = original.asJson
    val parsed   = json.as[ComponentMetadata]

    parsed shouldBe Right(original)
  }
}
