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
}
