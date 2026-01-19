package io.constellation.lsp.protocol

import io.circe.Json
import io.circe.parser.*
import io.circe.syntax.*
import io.constellation.lsp.protocol.LspMessages.*
import io.constellation.lsp.protocol.LspTypes.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LspMessagesTest extends AnyFlatSpec with Matchers {

  // ========== ErrorCategory Tests ==========

  "ErrorCategory" should "serialize Syntax to 'syntax'" in {
    ErrorCategory.Syntax.asJson shouldBe Json.fromString("syntax")
  }

  it should "serialize Type to 'type'" in {
    ErrorCategory.Type.asJson shouldBe Json.fromString("type")
  }

  it should "serialize Reference to 'reference'" in {
    ErrorCategory.Reference.asJson shouldBe Json.fromString("reference")
  }

  it should "serialize Runtime to 'runtime'" in {
    ErrorCategory.Runtime.asJson shouldBe Json.fromString("runtime")
  }

  it should "deserialize from 'syntax'" in {
    Json.fromString("syntax").as[ErrorCategory] shouldBe Right(ErrorCategory.Syntax)
  }

  it should "deserialize from 'type'" in {
    Json.fromString("type").as[ErrorCategory] shouldBe Right(ErrorCategory.Type)
  }

  it should "deserialize from 'reference'" in {
    Json.fromString("reference").as[ErrorCategory] shouldBe Right(ErrorCategory.Reference)
  }

  it should "deserialize from 'runtime'" in {
    Json.fromString("runtime").as[ErrorCategory] shouldBe Right(ErrorCategory.Runtime)
  }

  it should "fail for unknown category" in {
    val result = Json.fromString("unknown").as[ErrorCategory]
    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("Unknown error category")
  }

  it should "round-trip through JSON" in {
    ErrorCategory.values.foreach { category =>
      val json = category.asJson
      val parsed = json.as[ErrorCategory]
      parsed shouldBe Right(category)
    }
  }

  // ========== ExecutionState Tests ==========

  "ExecutionState" should "serialize Pending to 'pending'" in {
    ExecutionState.Pending.asJson shouldBe Json.fromString("pending")
  }

  it should "serialize Running to 'running'" in {
    ExecutionState.Running.asJson shouldBe Json.fromString("running")
  }

  it should "serialize Completed to 'completed'" in {
    ExecutionState.Completed.asJson shouldBe Json.fromString("completed")
  }

  it should "serialize Failed to 'failed'" in {
    ExecutionState.Failed.asJson shouldBe Json.fromString("failed")
  }

  it should "deserialize from 'pending'" in {
    Json.fromString("pending").as[ExecutionState] shouldBe Right(ExecutionState.Pending)
  }

  it should "deserialize from 'running'" in {
    Json.fromString("running").as[ExecutionState] shouldBe Right(ExecutionState.Running)
  }

  it should "deserialize from 'completed'" in {
    Json.fromString("completed").as[ExecutionState] shouldBe Right(ExecutionState.Completed)
  }

  it should "deserialize from 'failed'" in {
    Json.fromString("failed").as[ExecutionState] shouldBe Right(ExecutionState.Failed)
  }

  it should "fail for unknown state" in {
    val result = Json.fromString("unknown").as[ExecutionState]
    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("Unknown execution state")
  }

  it should "round-trip through JSON" in {
    ExecutionState.values.foreach { state =>
      val json = state.asJson
      val parsed = json.as[ExecutionState]
      parsed shouldBe Right(state)
    }
  }

  // ========== TypeDescriptor Tests ==========

  "TypeDescriptor.PrimitiveType" should "serialize correctly" in {
    val td: TypeDescriptor = TypeDescriptor.PrimitiveType("String")
    val json = td.asJson.noSpaces

    json should include("\"kind\":\"primitive\"")
    json should include("\"name\":\"String\"")
  }

  it should "deserialize correctly" in {
    val jsonStr = """{"kind":"primitive","name":"Int"}"""
    val parsed = decode[TypeDescriptor](jsonStr)

    parsed shouldBe Right(TypeDescriptor.PrimitiveType("Int"))
  }

  it should "round-trip through JSON" in {
    val original: TypeDescriptor = TypeDescriptor.PrimitiveType("Boolean")
    val json = original.asJson
    val parsed = json.as[TypeDescriptor]

    parsed shouldBe Right(original)
  }

  "TypeDescriptor.ListType" should "serialize correctly" in {
    val td: TypeDescriptor = TypeDescriptor.ListType(TypeDescriptor.PrimitiveType("String"))
    val json = td.asJson.noSpaces

    json should include("\"kind\":\"list\"")
    json should include("\"elementType\":")
  }

  it should "deserialize correctly" in {
    val jsonStr = """{"kind":"list","elementType":{"kind":"primitive","name":"Int"}}"""
    val parsed = decode[TypeDescriptor](jsonStr)

    parsed shouldBe Right(TypeDescriptor.ListType(TypeDescriptor.PrimitiveType("Int")))
  }

  it should "round-trip through JSON" in {
    val original: TypeDescriptor = TypeDescriptor.ListType(TypeDescriptor.PrimitiveType("Float"))
    val json = original.asJson
    val parsed = json.as[TypeDescriptor]

    parsed shouldBe Right(original)
  }

  "TypeDescriptor.RecordType" should "serialize correctly" in {
    val td: TypeDescriptor = TypeDescriptor.RecordType(List(
      RecordField("name", TypeDescriptor.PrimitiveType("String")),
      RecordField("age", TypeDescriptor.PrimitiveType("Int"))
    ))
    val json = td.asJson.noSpaces

    json should include("\"kind\":\"record\"")
    json should include("\"fields\":")
    json should include("\"name\"")
    json should include("\"age\"")
  }

  it should "deserialize correctly" in {
    val jsonStr = """{"kind":"record","fields":[{"name":"value","type":{"kind":"primitive","name":"String"}}]}"""
    val parsed = decode[TypeDescriptor](jsonStr)

    parsed match {
      case Right(TypeDescriptor.RecordType(fields)) =>
        fields.length shouldBe 1
        fields.head.name shouldBe "value"
      case other =>
        fail(s"Unexpected result: $other")
    }
  }

  it should "round-trip through JSON" in {
    val original: TypeDescriptor = TypeDescriptor.RecordType(List(
      RecordField("x", TypeDescriptor.PrimitiveType("Int")),
      RecordField("y", TypeDescriptor.PrimitiveType("Int"))
    ))
    val json = original.asJson
    val parsed = json.as[TypeDescriptor]

    parsed shouldBe Right(original)
  }

  it should "handle empty record" in {
    val original: TypeDescriptor = TypeDescriptor.RecordType(List.empty)
    val json = original.asJson
    val parsed = json.as[TypeDescriptor]

    parsed shouldBe Right(original)
  }

  "TypeDescriptor.MapType" should "serialize correctly" in {
    val td: TypeDescriptor = TypeDescriptor.MapType(
      TypeDescriptor.PrimitiveType("String"),
      TypeDescriptor.PrimitiveType("Int")
    )
    val json = td.asJson.noSpaces

    json should include("\"kind\":\"map\"")
    json should include("\"keyType\":")
    json should include("\"valueType\":")
  }

  it should "deserialize correctly" in {
    val jsonStr = """{"kind":"map","keyType":{"kind":"primitive","name":"String"},"valueType":{"kind":"primitive","name":"Int"}}"""
    val parsed = decode[TypeDescriptor](jsonStr)

    parsed shouldBe Right(TypeDescriptor.MapType(
      TypeDescriptor.PrimitiveType("String"),
      TypeDescriptor.PrimitiveType("Int")
    ))
  }

  it should "round-trip through JSON" in {
    val original: TypeDescriptor = TypeDescriptor.MapType(
      TypeDescriptor.PrimitiveType("String"),
      TypeDescriptor.ListType(TypeDescriptor.PrimitiveType("Int"))
    )
    val json = original.asJson
    val parsed = json.as[TypeDescriptor]

    parsed shouldBe Right(original)
  }

  "TypeDescriptor.ParameterizedType" should "serialize correctly" in {
    val td: TypeDescriptor = TypeDescriptor.ParameterizedType("Optional", List(
      TypeDescriptor.PrimitiveType("String")
    ))
    val json = td.asJson.noSpaces

    json should include("\"kind\":\"parameterized\"")
    json should include("\"name\":\"Optional\"")
    json should include("\"params\":")
  }

  it should "deserialize correctly" in {
    val jsonStr = """{"kind":"parameterized","name":"Future","params":[{"kind":"primitive","name":"Int"}]}"""
    val parsed = decode[TypeDescriptor](jsonStr)

    parsed shouldBe Right(TypeDescriptor.ParameterizedType("Future", List(
      TypeDescriptor.PrimitiveType("Int")
    )))
  }

  it should "round-trip through JSON" in {
    val original: TypeDescriptor = TypeDescriptor.ParameterizedType("Either", List(
      TypeDescriptor.PrimitiveType("String"),
      TypeDescriptor.PrimitiveType("Int")
    ))
    val json = original.asJson
    val parsed = json.as[TypeDescriptor]

    parsed shouldBe Right(original)
  }

  it should "handle empty params" in {
    val original: TypeDescriptor = TypeDescriptor.ParameterizedType("Unit", List.empty)
    val json = original.asJson
    val parsed = json.as[TypeDescriptor]

    parsed shouldBe Right(original)
  }

  "TypeDescriptor.RefType" should "serialize correctly" in {
    val td: TypeDescriptor = TypeDescriptor.RefType("UserType")
    val json = td.asJson.noSpaces

    json should include("\"kind\":\"ref\"")
    json should include("\"name\":\"UserType\"")
  }

  it should "deserialize correctly" in {
    val jsonStr = """{"kind":"ref","name":"CustomType"}"""
    val parsed = decode[TypeDescriptor](jsonStr)

    parsed shouldBe Right(TypeDescriptor.RefType("CustomType"))
  }

  it should "round-trip through JSON" in {
    val original: TypeDescriptor = TypeDescriptor.RefType("MyRecord")
    val json = original.asJson
    val parsed = json.as[TypeDescriptor]

    parsed shouldBe Right(original)
  }

  "TypeDescriptor.UnionType" should "serialize correctly" in {
    val td: TypeDescriptor = TypeDescriptor.UnionType(List(
      TypeDescriptor.PrimitiveType("String"),
      TypeDescriptor.PrimitiveType("Int")
    ))
    val json = td.asJson.noSpaces

    json should include("\"kind\":\"union\"")
    json should include("\"members\":")
  }

  it should "deserialize correctly" in {
    val jsonStr = """{"kind":"union","members":[{"kind":"primitive","name":"String"},{"kind":"primitive","name":"Int"}]}"""
    val parsed = decode[TypeDescriptor](jsonStr)

    parsed shouldBe Right(TypeDescriptor.UnionType(List(
      TypeDescriptor.PrimitiveType("String"),
      TypeDescriptor.PrimitiveType("Int")
    )))
  }

  it should "round-trip through JSON" in {
    val original: TypeDescriptor = TypeDescriptor.UnionType(List(
      TypeDescriptor.PrimitiveType("String"),
      TypeDescriptor.RefType("Custom")
    ))
    val json = original.asJson
    val parsed = json.as[TypeDescriptor]

    parsed shouldBe Right(original)
  }

  it should "handle empty union" in {
    val original: TypeDescriptor = TypeDescriptor.UnionType(List.empty)
    val json = original.asJson
    val parsed = json.as[TypeDescriptor]

    parsed shouldBe Right(original)
  }

  "TypeDescriptor decoding" should "fail for unknown kind" in {
    val jsonStr = """{"kind":"unknown","name":"Test"}"""
    val result = decode[TypeDescriptor](jsonStr)

    result.isLeft shouldBe true
    result.left.toOption.get.getMessage should include("Unknown TypeDescriptor kind")
  }

  "TypeDescriptor" should "handle deeply nested types" in {
    val original: TypeDescriptor = TypeDescriptor.ListType(
      TypeDescriptor.MapType(
        TypeDescriptor.PrimitiveType("String"),
        TypeDescriptor.RecordType(List(
          RecordField("value", TypeDescriptor.UnionType(List(
            TypeDescriptor.PrimitiveType("Int"),
            TypeDescriptor.PrimitiveType("String")
          )))
        ))
      )
    )
    val json = original.asJson
    val parsed = json.as[TypeDescriptor]

    parsed shouldBe Right(original)
  }

  // ========== ErrorInfo Tests ==========

  "ErrorInfo" should "serialize with all fields" in {
    val info = ErrorInfo(
      category = ErrorCategory.Type,
      message = "Type mismatch",
      line = Some(10),
      column = Some(5),
      endLine = Some(10),
      endColumn = Some(15),
      codeContext = Some("val x: Int = \"hello\""),
      suggestion = Some("Use a String type instead")
    )
    val json = info.asJson.noSpaces

    json should include("\"category\":\"type\"")
    json should include("\"message\":\"Type mismatch\"")
    json should include("\"line\":10")
    json should include("\"column\":5")
    json should include("\"endLine\":10")
    json should include("\"endColumn\":15")
    json should include("\"codeContext\":")
    json should include("\"suggestion\":")
  }

  it should "serialize with minimal fields" in {
    val info = ErrorInfo(
      category = ErrorCategory.Syntax,
      message = "Parse error"
    )
    val json = info.asJson.noSpaces

    json should include("\"category\":\"syntax\"")
    json should include("\"message\":\"Parse error\"")
  }

  it should "round-trip through JSON" in {
    val original = ErrorInfo(
      category = ErrorCategory.Reference,
      message = "Undefined variable",
      line = Some(5),
      column = Some(10)
    )
    val json = original.asJson
    val parsed = json.as[ErrorInfo]

    parsed shouldBe Right(original)
  }

  // ========== InputField Tests ==========

  "InputField" should "serialize correctly" in {
    val field = InputField(
      name = "input",
      `type` = TypeDescriptor.PrimitiveType("String"),
      line = 1
    )
    val json = field.asJson.noSpaces

    json should include("\"name\":\"input\"")
    json should include("\"type\":")
    json should include("\"line\":1")
  }

  it should "round-trip through JSON" in {
    val original = InputField(
      name = "count",
      `type` = TypeDescriptor.PrimitiveType("Int"),
      line = 5
    )
    val json = original.asJson
    val parsed = json.as[InputField]

    parsed shouldBe Right(original)
  }

  // ========== RecordField Tests ==========

  "RecordField" should "serialize correctly" in {
    val field = RecordField("name", TypeDescriptor.PrimitiveType("String"))
    val json = field.asJson.noSpaces

    json should include("\"name\":\"name\"")
    json should include("\"type\":")
  }

  it should "round-trip through JSON" in {
    val original = RecordField("age", TypeDescriptor.PrimitiveType("Int"))
    val json = original.asJson
    val parsed = json.as[RecordField]

    parsed shouldBe Right(original)
  }

  // ========== DagExecutionUpdateParams Tests ==========

  "DagExecutionUpdateParams" should "serialize with all fields" in {
    val params = DagExecutionUpdateParams(
      uri = "file:///test.cst",
      nodeId = "node-1",
      state = ExecutionState.Completed,
      valuePreview = Some("42"),
      valueFull = Some(Json.fromInt(42)),
      durationMs = Some(100L),
      error = None
    )
    val json = params.asJson.noSpaces

    json should include("\"uri\":\"file:///test.cst\"")
    json should include("\"nodeId\":\"node-1\"")
    json should include("\"state\":\"completed\"")
    json should include("\"valuePreview\":\"42\"")
    json should include("\"durationMs\":100")
  }

  it should "serialize with error" in {
    val params = DagExecutionUpdateParams(
      uri = "file:///test.cst",
      nodeId = "node-2",
      state = ExecutionState.Failed,
      error = Some("Module execution failed")
    )
    val json = params.asJson.noSpaces

    json should include("\"state\":\"failed\"")
    json should include("\"error\":\"Module execution failed\"")
  }

  it should "round-trip through JSON" in {
    val original = DagExecutionUpdateParams(
      uri = "file:///test.cst",
      nodeId = "node-3",
      state = ExecutionState.Running
    )
    val json = original.asJson
    val parsed = json.as[DagExecutionUpdateParams]

    parsed shouldBe Right(original)
  }

  // ========== DagExecutionBatchUpdateParams Tests ==========

  "DagExecutionBatchUpdateParams" should "serialize correctly" in {
    val params = DagExecutionBatchUpdateParams(
      uri = "file:///test.cst",
      updates = List(
        DagExecutionUpdateParams("file:///test.cst", "node-1", ExecutionState.Completed),
        DagExecutionUpdateParams("file:///test.cst", "node-2", ExecutionState.Running)
      )
    )
    val json = params.asJson.noSpaces

    json should include("\"uri\":\"file:///test.cst\"")
    json should include("\"updates\":[")
  }

  it should "round-trip through JSON" in {
    val original = DagExecutionBatchUpdateParams(
      uri = "file:///test.cst",
      updates = List(
        DagExecutionUpdateParams("file:///test.cst", "node-1", ExecutionState.Pending)
      )
    )
    val json = original.asJson
    val parsed = json.as[DagExecutionBatchUpdateParams]

    parsed shouldBe Right(original)
  }

  it should "handle empty updates list" in {
    val original = DagExecutionBatchUpdateParams(
      uri = "file:///test.cst",
      updates = List.empty
    )
    val json = original.asJson
    val parsed = json.as[DagExecutionBatchUpdateParams]

    parsed shouldBe Right(original)
  }

  // ========== StepState Tests ==========

  "StepState" should "serialize correctly" in {
    val state = StepState(
      currentBatch = 1,
      totalBatches = 3,
      batchNodes = List("node-1", "node-2"),
      completedNodes = List(
        CompletedNode("node-0", "Input", "data", "hello", Some(10L))
      ),
      pendingNodes = List("node-3", "node-4")
    )
    val json = state.asJson.noSpaces

    json should include("\"currentBatch\":1")
    json should include("\"totalBatches\":3")
    json should include("\"batchNodes\":")
    json should include("\"completedNodes\":")
    json should include("\"pendingNodes\":")
  }

  it should "round-trip through JSON" in {
    val original = StepState(
      currentBatch = 0,
      totalBatches = 2,
      batchNodes = List("node-1"),
      completedNodes = List.empty,
      pendingNodes = List("node-2")
    )
    val json = original.asJson
    val parsed = json.as[StepState]

    parsed shouldBe Right(original)
  }

  // ========== CompletedNode Tests ==========

  "CompletedNode" should "serialize correctly" in {
    val node = CompletedNode(
      nodeId = "node-1",
      nodeName = "Uppercase",
      nodeType = "module",
      valuePreview = "HELLO",
      durationMs = Some(50L)
    )
    val json = node.asJson.noSpaces

    json should include("\"nodeId\":\"node-1\"")
    json should include("\"nodeName\":\"Uppercase\"")
    json should include("\"nodeType\":\"module\"")
    json should include("\"valuePreview\":\"HELLO\"")
    json should include("\"durationMs\":50")
  }

  it should "round-trip through JSON" in {
    val original = CompletedNode(
      nodeId = "data-1",
      nodeName = "text",
      nodeType = "data",
      valuePreview = "input value",
      durationMs = None
    )
    val json = original.asJson
    val parsed = json.as[CompletedNode]

    parsed shouldBe Right(original)
  }

  // ========== Step Request/Result Tests ==========

  "StepStartParams" should "round-trip through JSON" in {
    val original = StepStartParams(
      uri = "file:///test.cst",
      inputs = Map("text" -> Json.fromString("hello"))
    )
    val json = original.asJson
    val parsed = json.as[StepStartParams]

    parsed shouldBe Right(original)
  }

  "StepStartResult" should "round-trip through JSON" in {
    val original = StepStartResult(
      success = true,
      sessionId = Some("session-123"),
      totalBatches = Some(3),
      initialState = Some(StepState(0, 3, List("node-1"), List.empty, List("node-2", "node-3"))),
      error = None
    )
    val json = original.asJson
    val parsed = json.as[StepStartResult]

    parsed shouldBe Right(original)
  }

  "StepNextParams" should "round-trip through JSON" in {
    val original = StepNextParams("session-456")
    val json = original.asJson
    val parsed = json.as[StepNextParams]

    parsed shouldBe Right(original)
  }

  "StepNextResult" should "round-trip through JSON" in {
    val original = StepNextResult(
      success = true,
      state = Some(StepState(1, 3, List("node-2"), List.empty, List("node-3"))),
      isComplete = false,
      error = None
    )
    val json = original.asJson
    val parsed = json.as[StepNextResult]

    parsed shouldBe Right(original)
  }

  "StepContinueParams" should "round-trip through JSON" in {
    val original = StepContinueParams("session-789")
    val json = original.asJson
    val parsed = json.as[StepContinueParams]

    parsed shouldBe Right(original)
  }

  "StepContinueResult" should "round-trip through JSON" in {
    val original = StepContinueResult(
      success = true,
      state = Some(StepState(3, 3, List.empty, List.empty, List.empty)),
      outputs = Some(Map("result" -> Json.fromString("HELLO"))),
      executionTimeMs = Some(150L),
      error = None
    )
    val json = original.asJson
    val parsed = json.as[StepContinueResult]

    parsed shouldBe Right(original)
  }

  "StepStopParams" should "round-trip through JSON" in {
    val original = StepStopParams("session-abc")
    val json = original.asJson
    val parsed = json.as[StepStopParams]

    parsed shouldBe Right(original)
  }

  "StepStopResult" should "round-trip through JSON" in {
    val original = StepStopResult(success = true)
    val json = original.asJson
    val parsed = json.as[StepStopResult]

    parsed shouldBe Right(original)
  }

  // ========== Initialize Messages Tests ==========

  "InitializeParams" should "round-trip through JSON" in {
    val original = InitializeParams(
      processId = Some(1234),
      rootUri = Some("file:///project"),
      capabilities = ClientCapabilities(
        textDocument = Some(TextDocumentClientCapabilities(
          completion = Some(CompletionClientCapabilities(dynamicRegistration = Some(true))),
          hover = Some(HoverClientCapabilities(dynamicRegistration = Some(false)))
        ))
      )
    )
    val json = original.asJson
    val parsed = json.as[InitializeParams]

    parsed shouldBe Right(original)
  }

  "InitializeResult" should "round-trip through JSON" in {
    val original = InitializeResult(
      capabilities = ServerCapabilities(
        textDocumentSync = Some(1),
        completionProvider = Some(CompletionOptions(
          triggerCharacters = Some(List(".", "("))
        )),
        hoverProvider = Some(true),
        executeCommandProvider = Some(ExecuteCommandOptions(
          commands = List("constellation.run", "constellation.debug")
        ))
      )
    )
    val json = original.asJson
    val parsed = json.as[InitializeResult]

    parsed shouldBe Right(original)
  }

  // ========== Execute Pipeline Messages Tests ==========

  "ExecutePipelineParams" should "round-trip through JSON" in {
    val original = ExecutePipelineParams(
      uri = "file:///test.cst",
      inputs = Map(
        "text" -> Json.fromString("hello"),
        "count" -> Json.fromInt(5)
      )
    )
    val json = original.asJson
    val parsed = json.as[ExecutePipelineParams]

    parsed shouldBe Right(original)
  }

  "ExecutePipelineResult" should "round-trip with success" in {
    val original = ExecutePipelineResult(
      success = true,
      outputs = Some(Map("result" -> Json.fromString("HELLO"))),
      error = None,
      errors = None,
      executionTimeMs = Some(100L)
    )
    val json = original.asJson
    val parsed = json.as[ExecutePipelineResult]

    parsed shouldBe Right(original)
  }

  it should "round-trip with errors" in {
    val original = ExecutePipelineResult(
      success = false,
      outputs = None,
      error = Some("Execution failed"),
      errors = Some(List(
        ErrorInfo(ErrorCategory.Runtime, "Module threw exception", Some(5), Some(10))
      )),
      executionTimeMs = None
    )
    val json = original.asJson
    val parsed = json.as[ExecutePipelineResult]

    parsed shouldBe Right(original)
  }

  // ========== Get Input Schema Messages Tests ==========

  "GetInputSchemaParams" should "round-trip through JSON" in {
    val original = GetInputSchemaParams("file:///test.cst")
    val json = original.asJson
    val parsed = json.as[GetInputSchemaParams]

    parsed shouldBe Right(original)
  }

  "GetInputSchemaResult" should "round-trip through JSON" in {
    val original = GetInputSchemaResult(
      success = true,
      inputs = Some(List(
        InputField("text", TypeDescriptor.PrimitiveType("String"), 1),
        InputField("count", TypeDescriptor.PrimitiveType("Int"), 2)
      )),
      error = None,
      errors = None
    )
    val json = original.asJson
    val parsed = json.as[GetInputSchemaResult]

    parsed shouldBe Right(original)
  }

  // ========== Get DAG Structure Messages Tests ==========

  "GetDagStructureParams" should "round-trip through JSON" in {
    val original = GetDagStructureParams("file:///test.cst")
    val json = original.asJson
    val parsed = json.as[GetDagStructureParams]

    parsed shouldBe Right(original)
  }

  "GetDagStructureResult" should "round-trip through JSON" in {
    val original = GetDagStructureResult(
      success = true,
      dag = Some(DagStructure(
        modules = Map("mod-1" -> ModuleNode("Uppercase", Map("text" -> "String"), Map("result" -> "String"))),
        data = Map("data-1" -> DataNode("text", "String"), "data-2" -> DataNode("result", "String")),
        inEdges = List(("data-1", "mod-1")),
        outEdges = List(("mod-1", "data-2")),
        declaredOutputs = List("result")
      )),
      error = None
    )
    val json = original.asJson
    val parsed = json.as[GetDagStructureResult]

    parsed shouldBe Right(original)
  }

  // ========== Publish Diagnostics Tests ==========

  "PublishDiagnosticsParams" should "round-trip through JSON" in {
    val original = PublishDiagnosticsParams(
      uri = "file:///test.cst",
      diagnostics = List(
        Diagnostic(
          range = Range(Position(0, 0), Position(0, 10)),
          severity = Some(DiagnosticSeverity.Error),
          code = Some("E001"),
          source = Some("constellation"),
          message = "Undefined variable"
        )
      )
    )
    val json = original.asJson
    val parsed = json.as[PublishDiagnosticsParams]

    parsed shouldBe Right(original)
  }

  it should "handle empty diagnostics list" in {
    val original = PublishDiagnosticsParams(
      uri = "file:///test.cst",
      diagnostics = List.empty
    )
    val json = original.asJson
    val parsed = json.as[PublishDiagnosticsParams]

    parsed shouldBe Right(original)
  }

  // ========== Completion Context Tests ==========

  "CompletionContext" should "round-trip through JSON" in {
    val original = CompletionContext(
      triggerKind = 1,
      triggerCharacter = Some(".")
    )
    val json = original.asJson
    val parsed = json.as[CompletionContext]

    parsed shouldBe Right(original)
  }

  "CompletionParams" should "round-trip with context" in {
    val original = CompletionParams(
      textDocument = TextDocumentIdentifier("file:///test.cst"),
      position = Position(5, 10),
      context = Some(CompletionContext(2, Some("(")))
    )
    val json = original.asJson
    val parsed = json.as[CompletionParams]

    parsed shouldBe Right(original)
  }

  // ========== Text Document Sync Messages Tests ==========

  "DidOpenTextDocumentParams" should "round-trip through JSON" in {
    val original = DidOpenTextDocumentParams(
      TextDocumentItem("file:///test.cst", "constellation", 1, "in x: Int\nout x")
    )
    val json = original.asJson
    val parsed = json.as[DidOpenTextDocumentParams]

    parsed shouldBe Right(original)
  }

  "DidChangeTextDocumentParams" should "round-trip through JSON" in {
    val original = DidChangeTextDocumentParams(
      textDocument = VersionedTextDocumentIdentifier("file:///test.cst", 2),
      contentChanges = List(
        TextDocumentContentChangeEvent(
          range = Some(Range(Position(0, 0), Position(0, 5))),
          rangeLength = Some(5),
          text = "new text"
        )
      )
    )
    val json = original.asJson
    val parsed = json.as[DidChangeTextDocumentParams]

    parsed shouldBe Right(original)
  }

  "DidCloseTextDocumentParams" should "round-trip through JSON" in {
    val original = DidCloseTextDocumentParams(
      TextDocumentIdentifier("file:///test.cst")
    )
    val json = original.asJson
    val parsed = json.as[DidCloseTextDocumentParams]

    parsed shouldBe Right(original)
  }

  // ========== Hover Params Tests ==========

  "HoverParams" should "round-trip through JSON" in {
    val original = HoverParams(
      textDocument = TextDocumentIdentifier("file:///test.cst"),
      position = Position(10, 15)
    )
    val json = original.asJson
    val parsed = json.as[HoverParams]

    parsed shouldBe Right(original)
  }

  // ========== Additional Coverage: ClientCapabilities Tests ==========

  "ClientCapabilities" should "serialize with empty textDocument" in {
    val caps = ClientCapabilities(textDocument = None)
    val json = caps.asJson.noSpaces

    json should include("\"textDocument\"")
  }

  it should "serialize with full textDocument capabilities" in {
    val caps = ClientCapabilities(
      textDocument = Some(TextDocumentClientCapabilities(
        completion = Some(CompletionClientCapabilities(dynamicRegistration = Some(true))),
        hover = Some(HoverClientCapabilities(dynamicRegistration = Some(false)))
      ))
    )
    val json = caps.asJson.noSpaces

    json should include("\"textDocument\":")
    json should include("\"completion\":")
    json should include("\"hover\":")
  }

  it should "deserialize from JSON with empty textDocument" in {
    val jsonStr = """{"textDocument":null}"""
    val parsed = decode[ClientCapabilities](jsonStr)

    parsed shouldBe Right(ClientCapabilities(textDocument = None))
  }

  it should "round-trip minimal capabilities" in {
    val original = ClientCapabilities(textDocument = None)
    val json = original.asJson
    val parsed = json.as[ClientCapabilities]

    parsed shouldBe Right(original)
  }

  // ========== TextDocumentClientCapabilities Tests ==========

  "TextDocumentClientCapabilities" should "serialize with no capabilities" in {
    val caps = TextDocumentClientCapabilities(completion = None, hover = None)
    val json = caps.asJson.noSpaces

    // Should still produce valid JSON
    json should include("\"completion\"")
  }

  it should "serialize with only completion" in {
    val caps = TextDocumentClientCapabilities(
      completion = Some(CompletionClientCapabilities(dynamicRegistration = Some(true))),
      hover = None
    )
    val json = caps.asJson.noSpaces

    json should include("\"completion\":")
    json should include("\"dynamicRegistration\":true")
  }

  it should "serialize with only hover" in {
    val caps = TextDocumentClientCapabilities(
      completion = None,
      hover = Some(HoverClientCapabilities(dynamicRegistration = Some(false)))
    )
    val json = caps.asJson.noSpaces

    json should include("\"hover\":")
    json should include("\"dynamicRegistration\":false")
  }

  it should "round-trip through JSON" in {
    val original = TextDocumentClientCapabilities(
      completion = Some(CompletionClientCapabilities(Some(true))),
      hover = Some(HoverClientCapabilities(Some(true)))
    )
    val json = original.asJson
    val parsed = json.as[TextDocumentClientCapabilities]

    parsed shouldBe Right(original)
  }

  // ========== CompletionClientCapabilities Tests ==========

  "CompletionClientCapabilities" should "serialize with dynamicRegistration true" in {
    val caps = CompletionClientCapabilities(dynamicRegistration = Some(true))
    val json = caps.asJson.noSpaces

    json should include("\"dynamicRegistration\":true")
  }

  it should "serialize with dynamicRegistration false" in {
    val caps = CompletionClientCapabilities(dynamicRegistration = Some(false))
    val json = caps.asJson.noSpaces

    json should include("\"dynamicRegistration\":false")
  }

  it should "serialize with no dynamicRegistration" in {
    val caps = CompletionClientCapabilities(dynamicRegistration = None)
    val json = caps.asJson.noSpaces

    json should include("\"dynamicRegistration\"")
  }

  it should "round-trip through JSON" in {
    val original = CompletionClientCapabilities(dynamicRegistration = Some(true))
    val json = original.asJson
    val parsed = json.as[CompletionClientCapabilities]

    parsed shouldBe Right(original)
  }

  // ========== HoverClientCapabilities Tests ==========

  "HoverClientCapabilities" should "serialize with dynamicRegistration true" in {
    val caps = HoverClientCapabilities(dynamicRegistration = Some(true))
    val json = caps.asJson.noSpaces

    json should include("\"dynamicRegistration\":true")
  }

  it should "serialize with dynamicRegistration false" in {
    val caps = HoverClientCapabilities(dynamicRegistration = Some(false))
    val json = caps.asJson.noSpaces

    json should include("\"dynamicRegistration\":false")
  }

  it should "serialize with no dynamicRegistration" in {
    val caps = HoverClientCapabilities(dynamicRegistration = None)
    val json = caps.asJson.noSpaces

    json should include("\"dynamicRegistration\"")
  }

  it should "round-trip through JSON" in {
    val original = HoverClientCapabilities(dynamicRegistration = Some(false))
    val json = original.asJson
    val parsed = json.as[HoverClientCapabilities]

    parsed shouldBe Right(original)
  }

  // ========== ServerCapabilities Tests ==========

  "ServerCapabilities" should "serialize with default values" in {
    val caps = ServerCapabilities()
    val json = caps.asJson.noSpaces

    json should include("\"textDocumentSync\":1")
    json should include("\"hoverProvider\":true")
  }

  it should "serialize with custom completionProvider" in {
    val caps = ServerCapabilities(
      completionProvider = Some(CompletionOptions(
        triggerCharacters = Some(List(".", "(", "["))
      ))
    )
    val json = caps.asJson.noSpaces

    json should include("\"completionProvider\":")
    json should include("\"triggerCharacters\":")
  }

  it should "serialize with executeCommandProvider" in {
    val caps = ServerCapabilities(
      executeCommandProvider = Some(ExecuteCommandOptions(
        commands = List("command1", "command2")
      ))
    )
    val json = caps.asJson.noSpaces

    json should include("\"executeCommandProvider\":")
    json should include("\"commands\":")
  }

  it should "serialize with all None values" in {
    val caps = ServerCapabilities(
      textDocumentSync = None,
      completionProvider = None,
      hoverProvider = None,
      executeCommandProvider = None
    )
    val json = caps.asJson.noSpaces

    // Should still produce valid JSON
    json should startWith("{")
  }

  it should "round-trip through JSON" in {
    val original = ServerCapabilities(
      textDocumentSync = Some(2),
      completionProvider = Some(CompletionOptions(Some(List(".")))),
      hoverProvider = Some(false),
      executeCommandProvider = Some(ExecuteCommandOptions(List("test")))
    )
    val json = original.asJson
    val parsed = json.as[ServerCapabilities]

    parsed shouldBe Right(original)
  }

  // ========== CompletionOptions Tests ==========

  "CompletionOptions" should "serialize with no trigger characters" in {
    val opts = CompletionOptions(triggerCharacters = None)
    val json = opts.asJson.noSpaces

    json should include("\"triggerCharacters\"")
  }

  it should "serialize with empty trigger characters list" in {
    val opts = CompletionOptions(triggerCharacters = Some(List.empty))
    val json = opts.asJson.noSpaces

    json should include("\"triggerCharacters\":[]")
  }

  it should "serialize with multiple trigger characters" in {
    val opts = CompletionOptions(triggerCharacters = Some(List(".", "(", "<")))
    val json = opts.asJson.noSpaces

    json should include("\".\"")
    json should include("\"(\"")
    json should include("\"<\"")
  }

  it should "round-trip through JSON" in {
    val original = CompletionOptions(triggerCharacters = Some(List("@", "#")))
    val json = original.asJson
    val parsed = json.as[CompletionOptions]

    parsed shouldBe Right(original)
  }

  // ========== ExecuteCommandOptions Tests ==========

  "ExecuteCommandOptions" should "serialize with commands" in {
    val opts = ExecuteCommandOptions(commands = List("cmd1", "cmd2", "cmd3"))
    val json = opts.asJson.noSpaces

    json should include("\"commands\":[")
    json should include("\"cmd1\"")
    json should include("\"cmd2\"")
    json should include("\"cmd3\"")
  }

  it should "serialize with empty commands list" in {
    val opts = ExecuteCommandOptions(commands = List.empty)
    val json = opts.asJson.noSpaces

    json should include("\"commands\":[]")
  }

  it should "round-trip through JSON" in {
    val original = ExecuteCommandOptions(commands = List("execute", "debug", "stop"))
    val json = original.asJson
    val parsed = json.as[ExecuteCommandOptions]

    parsed shouldBe Right(original)
  }

  // ========== ExecutePipelineResult Additional Tests ==========

  "ExecutePipelineResult" should "serialize success with all fields" in {
    val result = ExecutePipelineResult(
      success = true,
      outputs = Some(Map("x" -> Json.fromInt(42), "y" -> Json.fromString("hello"))),
      error = None,
      errors = None,
      executionTimeMs = Some(250L)
    )
    val json = result.asJson.noSpaces

    json should include("\"success\":true")
    json should include("\"outputs\":")
    json should include("\"executionTimeMs\":250")
  }

  it should "serialize failure with simple error" in {
    val result = ExecutePipelineResult(
      success = false,
      outputs = None,
      error = Some("Simple error message"),
      errors = None,
      executionTimeMs = None
    )
    val json = result.asJson.noSpaces

    json should include("\"success\":false")
    json should include("\"error\":\"Simple error message\"")
  }

  it should "serialize failure with structured errors" in {
    val result = ExecutePipelineResult(
      success = false,
      outputs = None,
      error = Some("Multiple errors"),
      errors = Some(List(
        ErrorInfo(ErrorCategory.Syntax, "Parse error", Some(1), Some(1)),
        ErrorInfo(ErrorCategory.Type, "Type mismatch", Some(5), Some(10))
      )),
      executionTimeMs = None
    )
    val json = result.asJson.noSpaces

    json should include("\"errors\":[")
    json should include("\"syntax\"")
    json should include("\"type\"")
  }

  // ========== GetInputSchemaResult Additional Tests ==========

  "GetInputSchemaResult" should "serialize success with inputs" in {
    val result = GetInputSchemaResult(
      success = true,
      inputs = Some(List(
        InputField("x", TypeDescriptor.PrimitiveType("Int"), 1),
        InputField("name", TypeDescriptor.PrimitiveType("String"), 2)
      )),
      error = None,
      errors = None
    )
    val json = result.asJson.noSpaces

    json should include("\"success\":true")
    json should include("\"inputs\":[")
  }

  it should "serialize failure with errors" in {
    val result = GetInputSchemaResult(
      success = false,
      inputs = None,
      error = Some("Failed to parse"),
      errors = Some(List(
        ErrorInfo(ErrorCategory.Syntax, "Unexpected token", Some(1), Some(5))
      ))
    )
    val json = result.asJson.noSpaces

    json should include("\"success\":false")
    json should include("\"error\":\"Failed to parse\"")
  }

  it should "serialize with empty inputs list" in {
    val result = GetInputSchemaResult(
      success = true,
      inputs = Some(List.empty),
      error = None,
      errors = None
    )
    val json = result.asJson.noSpaces

    json should include("\"inputs\":[]")
  }

  // ========== ModuleNode and DataNode Tests ==========

  "ModuleNode" should "serialize correctly" in {
    val node = ModuleNode(
      name = "Uppercase",
      consumes = Map("text" -> "String"),
      produces = Map("result" -> "String")
    )
    val json = node.asJson.noSpaces

    json should include("\"name\":\"Uppercase\"")
    json should include("\"consumes\":")
    json should include("\"produces\":")
  }

  it should "round-trip through JSON" in {
    val original = ModuleNode(
      name = "Process",
      consumes = Map("input1" -> "Int", "input2" -> "String"),
      produces = Map("output" -> "Boolean")
    )
    val json = original.asJson
    val parsed = json.as[ModuleNode]

    parsed shouldBe Right(original)
  }

  "DataNode" should "serialize correctly" in {
    val node = DataNode(name = "data1", cType = "String")
    val json = node.asJson.noSpaces

    json should include("\"name\":\"data1\"")
    json should include("\"cType\":\"String\"")
  }

  it should "round-trip through JSON" in {
    val original = DataNode(name = "count", cType = "Int")
    val json = original.asJson
    val parsed = json.as[DataNode]

    parsed shouldBe Right(original)
  }

  // ========== DagStructure Tests ==========

  "DagStructure" should "serialize with all fields" in {
    val dag = DagStructure(
      modules = Map("m1" -> ModuleNode("Mod1", Map("in" -> "String"), Map("out" -> "String"))),
      data = Map("d1" -> DataNode("data1", "String")),
      inEdges = List(("d1", "m1")),
      outEdges = List(("m1", "d1")),
      declaredOutputs = List("out")
    )
    val json = dag.asJson.noSpaces

    json should include("\"modules\":")
    json should include("\"data\":")
    json should include("\"inEdges\":")
    json should include("\"outEdges\":")
    json should include("\"declaredOutputs\":")
  }

  it should "serialize empty dag" in {
    val dag = DagStructure(
      modules = Map.empty,
      data = Map.empty,
      inEdges = List.empty,
      outEdges = List.empty,
      declaredOutputs = List.empty
    )
    val json = dag.asJson.noSpaces

    json should include("\"modules\":{}")
    json should include("\"data\":{}")
    json should include("\"inEdges\":[]")
  }

  it should "round-trip through JSON" in {
    val original = DagStructure(
      modules = Map(
        "m1" -> ModuleNode("A", Map("x" -> "Int"), Map("y" -> "Int")),
        "m2" -> ModuleNode("B", Map("y" -> "Int"), Map("z" -> "Int"))
      ),
      data = Map(
        "d1" -> DataNode("x", "Int"),
        "d2" -> DataNode("y", "Int"),
        "d3" -> DataNode("z", "Int")
      ),
      inEdges = List(("d1", "m1"), ("d2", "m2")),
      outEdges = List(("m1", "d2"), ("m2", "d3")),
      declaredOutputs = List("z")
    )
    val json = original.asJson
    val parsed = json.as[DagStructure]

    parsed shouldBe Right(original)
  }

  // ========== CompletionContext Additional Tests ==========

  "CompletionContext" should "serialize with triggerKind only" in {
    val ctx = CompletionContext(triggerKind = 1, triggerCharacter = None)
    val json = ctx.asJson.noSpaces

    json should include("\"triggerKind\":1")
  }

  it should "serialize with invoked trigger kind" in {
    val ctx = CompletionContext(triggerKind = 1, triggerCharacter = None)
    val json = ctx.asJson.noSpaces

    json should include("\"triggerKind\":1")
  }

  it should "serialize with trigger character trigger kind" in {
    val ctx = CompletionContext(triggerKind = 2, triggerCharacter = Some("."))
    val json = ctx.asJson.noSpaces

    json should include("\"triggerKind\":2")
    json should include("\"triggerCharacter\":\".\"")
  }

  it should "serialize with incomplete trigger kind" in {
    val ctx = CompletionContext(triggerKind = 3, triggerCharacter = None)
    val json = ctx.asJson.noSpaces

    json should include("\"triggerKind\":3")
  }

  // ========== Step Result Error Cases ==========

  "StepStartResult" should "serialize failure" in {
    val result = StepStartResult(
      success = false,
      sessionId = None,
      totalBatches = None,
      initialState = None,
      error = Some("Failed to start session")
    )
    val json = result.asJson.noSpaces

    json should include("\"success\":false")
    json should include("\"error\":\"Failed to start session\"")
  }

  "StepNextResult" should "serialize failure" in {
    val result = StepNextResult(
      success = false,
      state = None,
      isComplete = false,
      error = Some("Session not found")
    )
    val json = result.asJson.noSpaces

    json should include("\"success\":false")
    json should include("\"error\":\"Session not found\"")
  }

  it should "serialize completion" in {
    val result = StepNextResult(
      success = true,
      state = Some(StepState(3, 3, List.empty, List.empty, List.empty)),
      isComplete = true,
      error = None
    )
    val json = result.asJson.noSpaces

    json should include("\"isComplete\":true")
  }

  "StepContinueResult" should "serialize failure" in {
    val result = StepContinueResult(
      success = false,
      state = None,
      outputs = None,
      executionTimeMs = None,
      error = Some("Execution failed")
    )
    val json = result.asJson.noSpaces

    json should include("\"success\":false")
    json should include("\"error\":\"Execution failed\"")
  }

  "StepStopResult" should "serialize failure" in {
    val result = StepStopResult(success = false)
    val json = result.asJson.noSpaces

    json should include("\"success\":false")
  }

  // ========== GetDagStructureResult Additional Tests ==========

  "GetDagStructureResult" should "serialize failure" in {
    val result = GetDagStructureResult(
      success = false,
      dag = None,
      error = Some("Compilation failed")
    )
    val json = result.asJson.noSpaces

    json should include("\"success\":false")
    json should include("\"error\":\"Compilation failed\"")
  }

  it should "serialize with empty dag" in {
    val result = GetDagStructureResult(
      success = true,
      dag = Some(DagStructure(Map.empty, Map.empty, List.empty, List.empty, List.empty)),
      error = None
    )
    val json = result.asJson.noSpaces

    json should include("\"success\":true")
    json should include("\"dag\":")
  }
}
