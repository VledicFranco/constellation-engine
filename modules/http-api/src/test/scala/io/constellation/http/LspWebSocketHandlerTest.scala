package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lsp.protocol.JsonRpc.RequestId.*
import io.constellation.lsp.protocol.JsonRpc.*

import io.circe.Json
import io.circe.parser.*
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LspWebSocketHandlerTest extends AnyFlatSpec with Matchers {

  // Create test instances
  val constellation = ConstellationImpl.init.unsafeRunSync()
  val compiler      = LangCompiler.empty
  val handler       = LspWebSocketHandler(constellation, compiler)

  // ========== Route Existence Tests ==========

  "LspWebSocketHandler" should "create routes without error" in {
    // The handler should be instantiable
    handler should not be null
  }

  // ========== LSP Message Format Tests ==========

  // Testing via the JSON-RPC protocol types directly

  "LSP Request format" should "serialize correctly" in {
    val request = Request(
      id = StringId("1"),
      method = "initialize",
      params = Some(Json.obj("rootUri" -> Json.fromString("file:///test")))
    )

    val json = request.asJson.noSpaces

    json should include("\"jsonrpc\":\"2.0\"")
    json should include("\"id\":\"1\"")
    json should include("\"method\":\"initialize\"")
    json should include("\"params\":")
  }

  it should "serialize without params" in {
    val request = Request(
      id = NumberId(1),
      method = "shutdown",
      params = None
    )

    val json = request.asJson.noSpaces

    json should include("\"method\":\"shutdown\"")
  }

  "LSP Notification format" should "serialize correctly" in {
    val notification = Notification(
      method = "textDocument/didOpen",
      params = Some(
        Json.obj(
          "textDocument" -> Json.obj(
            "uri"        -> Json.fromString("file:///test.cst"),
            "languageId" -> Json.fromString("constellation"),
            "version"    -> Json.fromInt(1),
            "text"       -> Json.fromString("in x: Int")
          )
        )
      )
    )

    val json = notification.asJson.noSpaces

    json should include("\"jsonrpc\":\"2.0\"")
    json should include("\"method\":\"textDocument/didOpen\"")
    json should not include "\"id\":"
  }

  "LSP Response format" should "serialize success correctly" in {
    val response = Response(
      id = StringId("1"),
      result = Some(Json.obj("capabilities" -> Json.obj()))
    )

    val json = response.asJson.noSpaces

    json should include("\"jsonrpc\":\"2.0\"")
    json should include("\"id\":\"1\"")
    json should include("\"result\":")
  }

  it should "serialize error correctly" in {
    val response = Response(
      id = StringId("1"),
      error = Some(
        ResponseError(
          code = ErrorCodes.MethodNotFound,
          message = "Unknown method"
        )
      )
    )

    val json = response.asJson.noSpaces

    json should include("\"error\":")
    json should include(s"\"code\":${ErrorCodes.MethodNotFound}")
    json should include("\"message\":\"Unknown method\"")
  }

  // ========== Message Parsing Tests ==========

  "Request/Notification distinction" should "identify request by presence of id field" in {
    val requestJson = """{"jsonrpc":"2.0","id":"test","method":"initialize","params":{}}"""
    val parsed      = parse(requestJson).flatMap(_.as[Request])

    parsed match {
      case Right(request) =>
        request.id shouldBe StringId("test")
        request.method shouldBe "initialize"
      case Left(error) =>
        fail(s"Should parse as Request: ${error.getMessage}")
    }
  }

  it should "identify notification by absence of id field" in {
    val notificationJson = """{"jsonrpc":"2.0","method":"exit"}"""
    val parsed           = parse(notificationJson).flatMap(_.as[Notification])

    parsed match {
      case Right(notification) =>
        notification.method shouldBe "exit"
      case Left(error) =>
        fail(s"Should parse as Notification: ${error.getMessage}")
    }
  }

  // ========== Content-Length Header Tests ==========

  "LSP message with Content-Length header" should "be valid format" in {
    val json          = """{"jsonrpc":"2.0","id":"1","method":"test"}"""
    val contentLength = json.getBytes("UTF-8").length
    val message       = s"Content-Length: $contentLength\r\n\r\n$json"

    // Verify the format
    message should startWith("Content-Length:")
    message should include("\r\n\r\n")
    message should endWith(json)
  }

  it should "have correct byte length calculation" in {
    // Unicode characters affect byte length
    val json       = """{"text":"héllo wörld"}"""
    val byteLength = json.getBytes("UTF-8").length

    // "héllo wörld" has multi-byte characters
    byteLength should be > json.length
  }

  // ========== Initialize Request Tests ==========

  "Initialize request" should "be parseable" in {
    val initRequest =
      """{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"processId":1234,"rootUri":"file:///workspace","capabilities":{}}}"""
    val parsed = parse(initRequest).flatMap(_.as[Request])

    parsed match {
      case Right(request) =>
        request.method shouldBe "initialize"
        request.params shouldBe defined
        request.params.get.hcursor.downField("processId").as[Int] shouldBe Right(1234)
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== TextDocument Notifications ==========

  "textDocument/didOpen notification" should "be parseable" in {
    val didOpen = """{
      "jsonrpc": "2.0",
      "method": "textDocument/didOpen",
      "params": {
        "textDocument": {
          "uri": "file:///test.cst",
          "languageId": "constellation",
          "version": 1,
          "text": "in x: Int\\nout x"
        }
      }
    }"""

    val parsed = parse(didOpen).flatMap(_.as[Notification])

    parsed match {
      case Right(notification) =>
        notification.method shouldBe "textDocument/didOpen"
        notification.params shouldBe defined
        val params = notification.params.get
        params.hcursor.downField("textDocument").downField("uri").as[String] shouldBe Right(
          "file:///test.cst"
        )
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  "textDocument/didChange notification" should "be parseable" in {
    val didChange = """{
      "jsonrpc": "2.0",
      "method": "textDocument/didChange",
      "params": {
        "textDocument": {
          "uri": "file:///test.cst",
          "version": 2
        },
        "contentChanges": [
          {"text": "in y: String\\nout y"}
        ]
      }
    }"""

    val parsed = parse(didChange).flatMap(_.as[Notification])

    parsed match {
      case Right(notification) =>
        notification.method shouldBe "textDocument/didChange"
        notification.params shouldBe defined
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  "textDocument/didClose notification" should "be parseable" in {
    val didClose = """{
      "jsonrpc": "2.0",
      "method": "textDocument/didClose",
      "params": {
        "textDocument": {
          "uri": "file:///test.cst"
        }
      }
    }"""

    val parsed = parse(didClose).flatMap(_.as[Notification])

    parsed match {
      case Right(notification) =>
        notification.method shouldBe "textDocument/didClose"
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== Completion Request ==========

  "textDocument/completion request" should "be parseable" in {
    val completion = """{
      "jsonrpc": "2.0",
      "id": "2",
      "method": "textDocument/completion",
      "params": {
        "textDocument": {"uri": "file:///test.cst"},
        "position": {"line": 5, "character": 10}
      }
    }"""

    val parsed = parse(completion).flatMap(_.as[Request])

    parsed match {
      case Right(request) =>
        request.method shouldBe "textDocument/completion"
        val pos = request.params.get.hcursor.downField("position")
        pos.downField("line").as[Int] shouldBe Right(5)
        pos.downField("character").as[Int] shouldBe Right(10)
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== Hover Request ==========

  "textDocument/hover request" should "be parseable" in {
    val hover = """{
      "jsonrpc": "2.0",
      "id": "3",
      "method": "textDocument/hover",
      "params": {
        "textDocument": {"uri": "file:///test.cst"},
        "position": {"line": 2, "character": 5}
      }
    }"""

    val parsed = parse(hover).flatMap(_.as[Request])

    parsed match {
      case Right(request) =>
        request.method shouldBe "textDocument/hover"
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== Custom Commands ==========

  "constellation/executePipeline request" should "be parseable" in {
    val execute = """{
      "jsonrpc": "2.0",
      "id": "4",
      "method": "constellation/executePipeline",
      "params": {
        "uri": "file:///test.cst",
        "inputs": {"x": 42}
      }
    }"""

    val parsed = parse(execute).flatMap(_.as[Request])

    parsed match {
      case Right(request) =>
        request.method shouldBe "constellation/executePipeline"
        request.params.get.hcursor.downField("inputs").downField("x").as[Int] shouldBe Right(42)
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== Error Codes ==========

  "ErrorCodes" should "have standard JSON-RPC error codes" in {
    ErrorCodes.ParseError shouldBe -32700
    ErrorCodes.InvalidRequest shouldBe -32600
    ErrorCodes.MethodNotFound shouldBe -32601
    ErrorCodes.InvalidParams shouldBe -32602
    ErrorCodes.InternalError shouldBe -32603
  }

  // ========== RequestId Types ==========

  "RequestId" should "support string IDs" in {
    val id = StringId("abc-123")
    id.asJson shouldBe Json.fromString("abc-123")
  }

  it should "support numeric IDs" in {
    val id = NumberId(42)
    id.asJson shouldBe Json.fromInt(42)
  }

  it should "round-trip string ID through JSON" in {
    val original = StringId("test-id")
    val json     = original.asJson
    val parsed   = json.as[RequestId]

    parsed shouldBe Right(original)
  }

  it should "round-trip numeric ID through JSON" in {
    val original = NumberId(999)
    val json     = original.asJson
    val parsed   = json.as[RequestId]

    parsed shouldBe Right(original)
  }

  // ========== Edge Cases ==========

  "Empty params" should "be handled in request" in {
    val requestWithNullParams = """{"jsonrpc":"2.0","id":"1","method":"test","params":null}"""
    val parsed                = parse(requestWithNullParams).flatMap(_.as[Request])

    parsed match {
      case Right(request) =>
        request.method shouldBe "test"
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  "Missing optional fields" should "parse correctly" in {
    // Request without params
    val minimalRequest = """{"jsonrpc":"2.0","id":"1","method":"shutdown"}"""
    val parsed         = parse(minimalRequest).flatMap(_.as[Request])

    parsed match {
      case Right(request) =>
        request.method shouldBe "shutdown"
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  "Response without result or error" should "be valid" in {
    // Null result is valid
    val response = Response(
      id = StringId("1"),
      result = None
    )

    val json = response.asJson.noSpaces

    json should include("\"id\":\"1\"")
  }
}
