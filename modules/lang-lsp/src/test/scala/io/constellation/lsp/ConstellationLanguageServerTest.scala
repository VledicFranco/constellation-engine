package io.constellation.lsp

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.syntax.*
import io.constellation.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.{FunctionSignature, SemanticType}
import io.constellation.lsp.protocol.JsonRpc.*
import io.constellation.lsp.protocol.JsonRpc.RequestId.*
import io.constellation.lsp.protocol.LspMessages.*
import io.constellation.lsp.protocol.LspTypes.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConstellationLanguageServerTest extends AnyFlatSpec with Matchers {

  case class TestInput(text: String)
  case class TestOutput(result: String)

  private def createUppercaseModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Uppercase", "Converts text to uppercase", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.text.toUpperCase))
      .build

  private def createTestServer(): IO[ConstellationLanguageServer] = {
    val uppercaseModule = createUppercaseModule()

    for {
      constellation <- ConstellationImpl.init
      _ <- constellation.setModule(uppercaseModule)

      // Create compiler with Uppercase function registered
      compiler = LangCompiler.builder
        .withModule(
          "Uppercase",
          uppercaseModule,
          List("text" -> SemanticType.SString),
          SemanticType.SString
        )
        .build

      // Create server with no-op diagnostics publisher
      server <- ConstellationLanguageServer.create(
        constellation,
        compiler,
        _ => IO.unit
      )
    } yield server
  }

  // ========== Initialize Request Tests ==========

  "ConstellationLanguageServer" should "handle initialize request" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "initialize",
        params = Some(Json.obj("rootUri" -> Json.fromString("file:///project")))
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe StringId("1")
    response.result shouldBe defined
    response.error shouldBe None

    // Verify capabilities are returned
    val resultJson = response.result.get
    (resultJson \\ "capabilities").nonEmpty shouldBe true
  }

  it should "handle shutdown request" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "shutdown",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe StringId("1")
    response.result shouldBe Some(Json.Null)
    response.error shouldBe None
  }

  // ========== Method Not Found Tests ==========

  it should "return MethodNotFound for unknown request" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "unknown/method",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.MethodNotFound
    response.error.get.message should include("unknown/method")
  }

  // ========== Completion Tests ==========

  it should "handle completion request with valid params" in {
    val result = for {
      server <- createTestServer()
      docManager <- DocumentManager.create
      _ <- docManager.openDocument("file:///test.cst", "constellation", 1, "in text: String\nUpper")

      // Need to open document in the server's document manager
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "in text: String\nUpper")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "textDocument/completion",
        params = Some(CompletionParams(
          textDocument = TextDocumentIdentifier("file:///test.cst"),
          position = Position(1, 5)
        ).asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe StringId("1")
    response.result shouldBe defined
    response.error shouldBe None
  }

  it should "return InvalidParams for completion without params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "textDocument/completion",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "return empty completion for non-existent document" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "textDocument/completion",
        params = Some(CompletionParams(
          textDocument = TextDocumentIdentifier("file:///nonexistent.cst"),
          position = Position(0, 0)
        ).asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
    // Should return empty completion list
    val resultJson = response.result.get
    (resultJson \\ "items").headOption.flatMap(_.asArray).map(_.isEmpty) shouldBe Some(true)
  }

  // ========== Hover Tests ==========

  it should "handle hover request with valid params" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "result = Uppercase(text)")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "textDocument/hover",
        params = Some(HoverParams(
          textDocument = TextDocumentIdentifier("file:///test.cst"),
          position = Position(0, 12) // Position at "Uppercase"
        ).asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe StringId("1")
    response.error shouldBe None
  }

  it should "return InvalidParams for hover without params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "textDocument/hover",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "return None for hover on non-existent document" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "textDocument/hover",
        params = Some(HoverParams(
          textDocument = TextDocumentIdentifier("file:///nonexistent.cst"),
          position = Position(0, 0)
        ).asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe None
    response.error shouldBe None
  }

  // ========== Get Input Schema Tests ==========

  it should "handle getInputSchema request" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "in text: String\nout text")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = Some(GetInputSchemaParams("file:///test.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe StringId("1")
    response.result shouldBe defined
  }

  it should "return InvalidParams for getInputSchema without params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  // ========== Get DAG Structure Tests ==========

  it should "handle getDagStructure request" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "in text: String\nresult = Uppercase(text)\nout result")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getDagStructure",
        params = Some(GetDagStructureParams("file:///test.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe StringId("1")
    response.result shouldBe defined
  }

  it should "return error for getDagStructure on non-existent document" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getDagStructure",
        params = Some(GetDagStructureParams("file:///nonexistent.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
    val resultJson = response.result.get
    (resultJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(false)
  }

  // ========== Execute Pipeline Tests ==========

  it should "handle executePipeline request" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "in text: String\nresult = Uppercase(text)\nout result")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/executePipeline",
        params = Some(ExecutePipelineParams(
          uri = "file:///test.cst",
          inputs = Map("text" -> Json.fromString("hello"))
        ).asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe StringId("1")
    response.result shouldBe defined
  }

  it should "return error for executePipeline with invalid params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/executePipeline",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  // ========== Step Execution Tests ==========

  it should "handle stepStart request" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "in text: String\nresult = Uppercase(text)\nout result")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepStart",
        params = Some(StepStartParams(
          uri = "file:///test.cst",
          inputs = Map("text" -> Json.fromString("hello"))
        ).asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe StringId("1")
    response.result shouldBe defined
  }

  it should "return error for stepNext with invalid session" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepNext",
        params = Some(StepNextParams("invalid-session-id").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
    val resultJson = response.result.get
    (resultJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(false)
  }

  it should "handle stepStop request" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepStop",
        params = Some(StepStopParams("some-session-id").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe StringId("1")
    response.result shouldBe defined
  }

  // ========== Notification Tests ==========

  it should "handle initialized notification" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "initialized",
        params = None
      ))
    } yield ()

    // Should complete without error
    result.unsafeRunSync()
  }

  it should "handle exit notification" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "exit",
        params = None
      ))
    } yield ()

    // Should complete without error
    result.unsafeRunSync()
  }

  it should "handle didOpen notification" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "in x: Int")
        ).asJson)
      ))
    } yield ()

    // Should complete without error
    result.unsafeRunSync()
  }

  it should "handle didChange notification" in {
    val result = for {
      server <- createTestServer()
      // First open the document
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "in x: Int")
        ).asJson)
      ))
      // Then change it
      _ <- server.handleNotification(Notification(
        method = "textDocument/didChange",
        params = Some(DidChangeTextDocumentParams(
          textDocument = VersionedTextDocumentIdentifier("file:///test.cst", 2),
          contentChanges = List(TextDocumentContentChangeEvent(None, None, "in y: String"))
        ).asJson)
      ))
    } yield ()

    // Should complete without error
    result.unsafeRunSync()
  }

  it should "handle didClose notification" in {
    val result = for {
      server <- createTestServer()
      // First open the document
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "in x: Int")
        ).asJson)
      ))
      // Then close it
      _ <- server.handleNotification(Notification(
        method = "textDocument/didClose",
        params = Some(DidCloseTextDocumentParams(
          TextDocumentIdentifier("file:///test.cst")
        ).asJson)
      ))
    } yield ()

    // Should complete without error
    result.unsafeRunSync()
  }

  it should "ignore unknown notifications" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "unknown/notification",
        params = None
      ))
    } yield ()

    // Should complete without error
    result.unsafeRunSync()
  }

  // ========== Error Handling Tests ==========

  it should "handle malformed completion params gracefully" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "textDocument/completion",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "handle malformed hover params gracefully" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "textDocument/hover",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  // ========== Full Workflow Tests ==========

  it should "handle complete edit-compile-execute workflow" in {
    val result = for {
      server <- createTestServer()

      // 1. Open document
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///workflow.cst", "constellation", 1, "in text: String\nresult = Uppercase(text)\nout result")
        ).asJson)
      ))

      // 2. Get input schema
      schemaResponse <- server.handleRequest(Request(
        id = StringId("2"),
        method = "constellation/getInputSchema",
        params = Some(GetInputSchemaParams("file:///workflow.cst").asJson)
      ))

      // 3. Get DAG structure
      dagResponse <- server.handleRequest(Request(
        id = StringId("3"),
        method = "constellation/getDagStructure",
        params = Some(GetDagStructureParams("file:///workflow.cst").asJson)
      ))

      // 4. Execute pipeline
      execResponse <- server.handleRequest(Request(
        id = StringId("4"),
        method = "constellation/executePipeline",
        params = Some(ExecutePipelineParams(
          uri = "file:///workflow.cst",
          inputs = Map("text" -> Json.fromString("hello world"))
        ).asJson)
      ))

      // 5. Close document
      _ <- server.handleNotification(Notification(
        method = "textDocument/didClose",
        params = Some(DidCloseTextDocumentParams(
          TextDocumentIdentifier("file:///workflow.cst")
        ).asJson)
      ))

    } yield (schemaResponse, dagResponse, execResponse)

    val (schemaResponse, dagResponse, execResponse) = result.unsafeRunSync()

    schemaResponse.result shouldBe defined
    dagResponse.result shouldBe defined
    execResponse.result shouldBe defined
  }

  // ========== Additional Coverage Tests ==========

  it should "handle completion at various positions" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "in text: String\nresult = ")
        ).asJson)
      ))

      // Completion at beginning of line
      response1 <- server.handleRequest(Request(
        id = StringId("1"),
        method = "textDocument/completion",
        params = Some(CompletionParams(
          textDocument = TextDocumentIdentifier("file:///test.cst"),
          position = Position(1, 0)
        ).asJson)
      ))

      // Completion at end of line
      response2 <- server.handleRequest(Request(
        id = StringId("2"),
        method = "textDocument/completion",
        params = Some(CompletionParams(
          textDocument = TextDocumentIdentifier("file:///test.cst"),
          position = Position(1, 9)
        ).asJson)
      ))
    } yield (response1, response2)

    val (response1, response2) = result.unsafeRunSync()
    response1.error shouldBe None
    response2.error shouldBe None
  }

  it should "handle hover at variable position" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "in myVariable: String\nresult = Uppercase(myVariable)\nout result")
        ).asJson)
      ))

      // Hover over variable
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "textDocument/hover",
        params = Some(HoverParams(
          textDocument = TextDocumentIdentifier("file:///test.cst"),
          position = Position(1, 22) // Position at myVariable
        ).asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe None
  }

  it should "handle getInputSchema with syntax error" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///error.cst", "constellation", 1, "in text: String\nresult = InvalidSyntax(((\nout result")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = Some(GetInputSchemaParams("file:///error.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
  }

  it should "handle executePipeline with missing input" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///test.cst", "constellation", 1, "in text: String\nresult = Uppercase(text)\nout result")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/executePipeline",
        params = Some(ExecutePipelineParams(
          uri = "file:///test.cst",
          inputs = Map.empty // Missing required input
        ).asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
    // Should indicate an error about missing input
  }

  it should "handle step-through execution workflow" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///step.cst", "constellation", 1, "in text: String\nresult = Uppercase(text)\nout result")
        ).asJson)
      ))

      // Start step execution
      startResponse <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepStart",
        params = Some(StepStartParams(
          uri = "file:///step.cst",
          inputs = Map("text" -> Json.fromString("test"))
        ).asJson)
      ))

      // Extract session ID
      sessionIdOpt = for {
        result <- startResponse.result
        obj <- result.asObject
        sessionId <- obj("sessionId").flatMap(_.asString)
      } yield sessionId

      // Step next (if we have a session)
      nextResponse <- sessionIdOpt match {
        case Some(sessionId) =>
          server.handleRequest(Request(
            id = StringId("2"),
            method = "constellation/stepNext",
            params = Some(StepNextParams(sessionId).asJson)
          ))
        case None =>
          IO.pure(Response(id = StringId("2"), result = None))
      }

      // Continue to end (if we have a session)
      continueResponse <- sessionIdOpt match {
        case Some(sessionId) =>
          server.handleRequest(Request(
            id = StringId("3"),
            method = "constellation/stepContinue",
            params = Some(StepContinueParams(sessionId).asJson)
          ))
        case None =>
          IO.pure(Response(id = StringId("3"), result = None))
      }

      // Stop session (if we have one)
      stopResponse <- sessionIdOpt match {
        case Some(sessionId) =>
          server.handleRequest(Request(
            id = StringId("4"),
            method = "constellation/stepStop",
            params = Some(StepStopParams(sessionId).asJson)
          ))
        case None =>
          IO.pure(Response(id = StringId("4"), result = None))
      }
    } yield (startResponse, nextResponse, continueResponse, stopResponse)

    val (startResponse, nextResponse, continueResponse, stopResponse) = result.unsafeRunSync()
    startResponse.result shouldBe defined
  }

  it should "handle didChange with invalid params" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didChange",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield ()

    // Should complete without throwing
    result.unsafeRunSync()
  }

  it should "handle getDagStructure with malformed params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getDagStructure",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "handle executePipeline with malformed params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/executePipeline",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "handle stepStart with malformed params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepStart",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "handle stepContinue with invalid session" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepContinue",
        params = Some(StepContinueParams("non-existent-session").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
    val resultJson = response.result.get
    (resultJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(false)
  }

  it should "handle request with numeric ID" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = NumberId(12345),
        method = "shutdown",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe NumberId(12345)
    response.result shouldBe Some(Json.Null)
  }

  // ========== Input Schema with Various Types ==========

  it should "handle getInputSchema with Int type" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///types.cst", "constellation", 1, "in count: Int\nout count")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = Some(GetInputSchemaParams("file:///types.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
    (response.result.get \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)
  }

  it should "handle getInputSchema with Float type" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///types.cst", "constellation", 1, "in value: Float\nout value")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = Some(GetInputSchemaParams("file:///types.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
  }

  it should "handle getInputSchema with Boolean type" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///types.cst", "constellation", 1, "in flag: Boolean\nout flag")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = Some(GetInputSchemaParams("file:///types.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
  }

  it should "handle getInputSchema with List type" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///types.cst", "constellation", 1, "in items: List<String>\nout items")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = Some(GetInputSchemaParams("file:///types.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
  }

  it should "handle getInputSchema with Map type" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///types.cst", "constellation", 1, "in mapping: Map<String, Int>\nout mapping")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = Some(GetInputSchemaParams("file:///types.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
  }

  it should "handle getInputSchema with record type" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///types.cst", "constellation", 1, "in person: { name: String, age: Int }\nout person")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = Some(GetInputSchemaParams("file:///types.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
  }

  it should "handle getInputSchema for document not found" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = Some(GetInputSchemaParams("file:///notfound.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
    (response.result.get \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(false)
    (response.result.get \\ "error").headOption.flatMap(_.asString) should contain("Document not found")
  }

  it should "handle getInputSchema with malformed params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  // ========== Execute Pipeline Error Cases ==========

  it should "handle executePipeline for document not found" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/executePipeline",
        params = Some(ExecutePipelineParams(
          uri = "file:///notfound.cst",
          inputs = Map("text" -> Json.fromString("test"))
        ).asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
    (response.result.get \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(false)
    (response.result.get \\ "error").headOption.flatMap(_.asString) should contain("Document not found")
  }

  // ========== getDagStructure Error Cases ==========

  it should "handle getDagStructure with missing params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getDagStructure",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "handle getDagStructure with compile error" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///error.cst", "constellation", 1, "in text: String\nresult = UnknownModule(text)\nout result")
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getDagStructure",
        params = Some(GetDagStructureParams("file:///error.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
    (response.result.get \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(false)
  }

  // ========== Step Execution Error Cases ==========

  it should "handle stepStart with missing params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepStart",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "handle stepStart for document not found" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepStart",
        params = Some(StepStartParams(
          uri = "file:///notfound.cst",
          inputs = Map("text" -> Json.fromString("test"))
        ).asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
    (response.result.get \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(false)
  }

  it should "handle stepNext with missing params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepNext",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "handle stepNext with malformed params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepNext",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "handle stepContinue with missing params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepContinue",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "handle stepContinue with malformed params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepContinue",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "handle stepStop with missing params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepStop",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  it should "handle stepStop with malformed params" in {
    val result = for {
      server <- createTestServer()
      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/stepStop",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.error shouldBe defined
    response.error.get.code shouldBe ErrorCodes.InvalidParams
  }

  // ========== Notification Edge Cases ==========

  it should "handle didOpen with malformed params" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield ()

    // Should complete without throwing
    result.unsafeRunSync()
  }

  it should "handle didClose with malformed params" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didClose",
        params = Some(Json.obj("invalid" -> Json.fromString("params")))
      ))
    } yield ()

    // Should complete without throwing
    result.unsafeRunSync()
  }

  it should "handle didOpen with missing params" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = None
      ))
    } yield ()

    // Should complete without throwing
    result.unsafeRunSync()
  }

  it should "handle didChange with missing params" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didChange",
        params = None
      ))
    } yield ()

    // Should complete without throwing
    result.unsafeRunSync()
  }

  it should "handle didClose with missing params" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didClose",
        params = None
      ))
    } yield ()

    // Should complete without throwing
    result.unsafeRunSync()
  }

  // ========== Multiple Input Types ==========

  it should "handle getInputSchema with multiple inputs of different types" in {
    val result = for {
      server <- createTestServer()
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///multi.cst", "constellation", 1,
            """in name: String
              |in count: Int
              |in factor: Float
              |in enabled: Boolean
              |out name""".stripMargin)
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("1"),
        method = "constellation/getInputSchema",
        params = Some(GetInputSchemaParams("file:///multi.cst").asJson)
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.result shouldBe defined
    (response.result.get \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)
  }

  // ========== Cache Stats Tests ==========

  it should "return cachingEnabled=false for non-caching compiler" in {
    val result = for {
      server <- createTestServer() // Uses non-caching compiler
      response <- server.handleRequest(Request(
        id = StringId("cache-1"),
        method = "constellation/getCacheStats",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe StringId("cache-1")
    response.result shouldBe defined
    response.error shouldBe None

    val resultJson = response.result.get
    (resultJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)
    (resultJson \\ "cachingEnabled").headOption.flatMap(_.asBoolean) shouldBe Some(false)
    (resultJson \\ "stats").headOption.flatMap(_.asNull) shouldBe defined
  }

  it should "return cache statistics for caching compiler" in {
    import io.constellation.lang.{CachingLangCompiler, CompilationCache}

    val uppercaseModule = createUppercaseModule()

    val result = for {
      constellation <- ConstellationImpl.init
      _ <- constellation.setModule(uppercaseModule)

      // Create a caching compiler
      baseCompiler = LangCompiler.builder
        .withModule(
          "Uppercase",
          uppercaseModule,
          List("text" -> SemanticType.SString),
          SemanticType.SString
        )
        .build
      cachingCompiler = CachingLangCompiler.withDefaults(baseCompiler)

      // Create server with caching compiler
      server <- ConstellationLanguageServer.create(
        constellation,
        cachingCompiler,
        _ => IO.unit
      )

      // Do a compilation to populate cache stats
      _ <- server.handleNotification(Notification(
        method = "textDocument/didOpen",
        params = Some(DidOpenTextDocumentParams(
          TextDocumentItem("file:///cache.cst", "constellation", 1,
            """in text: String
              |result = Uppercase(text)
              |out result""".stripMargin)
        ).asJson)
      ))

      response <- server.handleRequest(Request(
        id = StringId("cache-2"),
        method = "constellation/getCacheStats",
        params = None
      ))
    } yield response

    val response = result.unsafeRunSync()
    response.id shouldBe StringId("cache-2")
    response.result shouldBe defined
    response.error shouldBe None

    val resultJson = response.result.get
    (resultJson \\ "success").headOption.flatMap(_.asBoolean) shouldBe Some(true)
    (resultJson \\ "cachingEnabled").headOption.flatMap(_.asBoolean) shouldBe Some(true)

    // Stats should be an object with hits, misses, etc.
    val stats = (resultJson \\ "stats").headOption
    stats shouldBe defined
    stats.get.isObject shouldBe true
    (stats.get \\ "hits").headOption.flatMap(_.asNumber) shouldBe defined
    (stats.get \\ "misses").headOption.flatMap(_.asNumber) shouldBe defined
    (stats.get \\ "hitRate").headOption.flatMap(_.asNumber) shouldBe defined
    (stats.get \\ "evictions").headOption.flatMap(_.asNumber) shouldBe defined
    (stats.get \\ "entries").headOption.flatMap(_.asNumber) shouldBe defined
  }
}
