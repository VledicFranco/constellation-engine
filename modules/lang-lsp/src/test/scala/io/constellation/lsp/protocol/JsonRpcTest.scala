package io.constellation.lsp.protocol

import io.circe.Json
import io.circe.parser.*
import io.circe.syntax.*
import io.constellation.lsp.protocol.JsonRpc.*
import io.constellation.lsp.protocol.JsonRpc.RequestId.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JsonRpcTest extends AnyFlatSpec with Matchers {

  // ========== RequestId Tests ==========

  "RequestId.StringId" should "serialize to JSON string" in {
    val id = StringId("test-123")
    id.asJson shouldBe Json.fromString("test-123")
  }

  it should "deserialize from JSON string" in {
    val json = Json.fromString("my-request")
    json.as[RequestId] shouldBe Right(StringId("my-request"))
  }

  "RequestId.NumberId" should "serialize to JSON number" in {
    val id = NumberId(42)
    id.asJson shouldBe Json.fromInt(42)
  }

  it should "deserialize from JSON number" in {
    val json = Json.fromInt(100)
    json.as[RequestId] shouldBe Right(NumberId(100))
  }

  "RequestId" should "round-trip through JSON for StringId" in {
    val original = StringId("abc-xyz")
    val json = original.asJson
    val parsed = json.as[RequestId]
    parsed shouldBe Right(original)
  }

  it should "round-trip through JSON for NumberId" in {
    val original = NumberId(999)
    val json = original.asJson
    val parsed = json.as[RequestId]
    parsed shouldBe Right(original)
  }

  "RequestId.NullId" should "serialize to JSON null" in {
    val id = NullId
    id.asJson shouldBe Json.Null
  }

  it should "deserialize from JSON null" in {
    val json = Json.Null
    json.as[RequestId] shouldBe Right(NullId)
  }

  it should "round-trip through JSON" in {
    val original: RequestId = NullId
    val json = original.asJson
    val parsed = json.as[RequestId]
    parsed shouldBe Right(original)
  }

  "RequestId decoding" should "fail for boolean values" in {
    val json = Json.fromBoolean(true)
    val result = json.as[RequestId]
    result.isLeft shouldBe true
    result.left.toOption.get.message should include("must be string, number, or null")
  }

  it should "fail for object values" in {
    val json = Json.obj("id" -> Json.fromInt(1))
    val result = json.as[RequestId]
    result.isLeft shouldBe true
  }

  it should "fail for array values" in {
    val json = Json.arr(Json.fromInt(1), Json.fromInt(2))
    val result = json.as[RequestId]
    result.isLeft shouldBe true
  }

  it should "handle negative number IDs" in {
    val json = Json.fromInt(-42)
    json.as[RequestId] shouldBe Right(NumberId(-42))
  }

  it should "handle empty string IDs" in {
    val json = Json.fromString("")
    json.as[RequestId] shouldBe Right(StringId(""))
  }

  it should "handle very long string IDs" in {
    val longId = "x" * 10000
    val json = Json.fromString(longId)
    json.as[RequestId] shouldBe Right(StringId(longId))
  }

  it should "handle large number IDs" in {
    val largeNumber = Long.MaxValue
    val json = Json.fromLong(largeNumber)
    json.as[RequestId] shouldBe Right(NumberId(largeNumber))
  }

  // ========== Request Tests ==========

  "Request" should "serialize with all fields" in {
    val request = Request(
      id = StringId("1"),
      method = "textDocument/completion",
      params = Some(Json.obj("uri" -> Json.fromString("file:///test.cst")))
    )

    val json = request.asJson.noSpaces

    json should include("\"jsonrpc\":\"2.0\"")
    json should include("\"id\":\"1\"")
    json should include("\"method\":\"textDocument/completion\"")
    json should include("\"params\":")
  }

  it should "serialize without params" in {
    val request = Request(
      id = StringId("1"),
      method = "shutdown",
      params = None
    )

    val json = request.asJson.noSpaces

    json should include("\"jsonrpc\":\"2.0\"")
    json should include("\"method\":\"shutdown\"")
    // Circe serializes None as null
    json should include("\"params\":null")
  }

  it should "deserialize from JSON" in {
    val jsonStr = """{"jsonrpc":"2.0","id":"test","method":"initialize","params":{"rootUri":"file:///project"}}"""
    val parsed = decode[Request](jsonStr)

    parsed match {
      case Right(request) =>
        request.id shouldBe StringId("test")
        request.method shouldBe "initialize"
        request.params shouldBe defined
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  it should "round-trip through JSON" in {
    val original = Request(
      id = NumberId(42),
      method = "textDocument/hover",
      params = Some(Json.obj(
        "textDocument" -> Json.obj("uri" -> Json.fromString("file:///test.cst")),
        "position" -> Json.obj("line" -> Json.fromInt(10), "character" -> Json.fromInt(5))
      ))
    )

    val json = original.asJson
    val parsed = json.as[Request]

    parsed match {
      case Right(request) =>
        request.id shouldBe original.id
        request.method shouldBe original.method
        request.params shouldBe original.params
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== Response Tests ==========

  "Response" should "serialize with result" in {
    val response = Response(
      id = StringId("1"),
      result = Some(Json.obj("capabilities" -> Json.obj()))
    )

    val json = response.asJson.noSpaces

    json should include("\"jsonrpc\":\"2.0\"")
    json should include("\"id\":\"1\"")
    json should include("\"result\":")
    json should not include "\"error\":"
  }

  it should "serialize with error" in {
    val response = Response(
      id = StringId("1"),
      error = Some(ResponseError(
        code = ErrorCodes.InvalidParams,
        message = "Missing required parameter"
      ))
    )

    val json = response.asJson.noSpaces

    json should include("\"error\":")
    json should include(s"\"code\":${ErrorCodes.InvalidParams}")
    json should include("\"message\":\"Missing required parameter\"")
    json should not include "\"result\":"
  }

  it should "serialize with null result" in {
    val response = Response(
      id = StringId("1"),
      result = None
    )

    val json = response.asJson.noSpaces

    json should include("\"result\":null")
  }

  it should "deserialize from JSON with result" in {
    val jsonStr = """{"jsonrpc":"2.0","id":"test","result":{"success":true}}"""
    val parsed = decode[Response](jsonStr)

    parsed match {
      case Right(response) =>
        response.id shouldBe StringId("test")
        response.result shouldBe defined
        response.error shouldBe None
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  it should "deserialize from JSON with error" in {
    val jsonStr = """{"jsonrpc":"2.0","id":"test","error":{"code":-32600,"message":"Invalid Request"}}"""
    val parsed = decode[Response](jsonStr)

    parsed match {
      case Right(response) =>
        response.id shouldBe StringId("test")
        response.result shouldBe None
        response.error shouldBe defined
        response.error.get.code shouldBe ErrorCodes.InvalidRequest
        response.error.get.message shouldBe "Invalid Request"
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== Notification Tests ==========

  "Notification" should "serialize with params" in {
    val notification = Notification(
      method = "textDocument/didOpen",
      params = Some(Json.obj(
        "textDocument" -> Json.obj(
          "uri" -> Json.fromString("file:///test.cst"),
          "text" -> Json.fromString("in x: Int")
        )
      ))
    )

    val json = notification.asJson.noSpaces

    json should include("\"jsonrpc\":\"2.0\"")
    json should include("\"method\":\"textDocument/didOpen\"")
    json should include("\"params\":")
    json should not include "\"id\":"
  }

  it should "serialize without params" in {
    val notification = Notification(
      method = "initialized",
      params = None
    )

    val json = notification.asJson.noSpaces

    json should include("\"jsonrpc\":\"2.0\"")
    json should include("\"method\":\"initialized\"")
    // Circe serializes None as null
    json should include("\"params\":null")
  }

  it should "deserialize from JSON" in {
    val jsonStr = """{"jsonrpc":"2.0","method":"exit"}"""
    val parsed = decode[Notification](jsonStr)

    parsed match {
      case Right(notification) =>
        notification.method shouldBe "exit"
        notification.params shouldBe None
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  it should "round-trip through JSON" in {
    val original = Notification(
      method = "textDocument/didChange",
      params = Some(Json.obj("uri" -> Json.fromString("file:///test.cst")))
    )

    val json = original.asJson
    val parsed = json.as[Notification]

    parsed match {
      case Right(notification) =>
        notification.method shouldBe original.method
        notification.params shouldBe original.params
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== ResponseError Tests ==========

  "ResponseError" should "serialize with code and message" in {
    val error = ResponseError(
      code = ErrorCodes.MethodNotFound,
      message = "Unknown method"
    )

    val json = error.asJson.noSpaces

    json should include(s"\"code\":${ErrorCodes.MethodNotFound}")
    json should include("\"message\":\"Unknown method\"")
  }

  it should "serialize with data" in {
    val error = ResponseError(
      code = ErrorCodes.InternalError,
      message = "Server error",
      data = Some(Json.obj("details" -> Json.fromString("Stack trace here")))
    )

    val json = error.asJson.noSpaces

    json should include("\"data\":")
    json should include("\"details\"")
  }

  it should "serialize without data" in {
    val error = ResponseError(
      code = ErrorCodes.ParseError,
      message = "Parse error"
    )

    val json = error.asJson.noSpaces

    // Circe serializes None as null
    json should include("\"data\":null")
  }

  // ========== ErrorCodes Tests ==========

  "ErrorCodes" should "have correct values" in {
    ErrorCodes.ParseError shouldBe -32700
    ErrorCodes.InvalidRequest shouldBe -32600
    ErrorCodes.MethodNotFound shouldBe -32601
    ErrorCodes.InvalidParams shouldBe -32602
    ErrorCodes.InternalError shouldBe -32603
  }

  // ========== Edge Cases ==========

  "Request with numeric ID 0" should "serialize correctly" in {
    val request = Request(
      id = NumberId(0),
      method = "test",
      params = None
    )

    val json = request.asJson.noSpaces

    json should include("\"id\":0")
  }

  "Request with empty method" should "serialize correctly" in {
    val request = Request(
      id = StringId("1"),
      method = "",
      params = None
    )

    val json = request.asJson.noSpaces

    json should include("\"method\":\"\"")
  }

  "Response with null ID" should "serialize correctly" in {
    val response = Response(
      id = StringId(""),
      result = Some(Json.Null)
    )

    val json = response.asJson.noSpaces

    json should include("\"id\":\"\"")
    json should include("\"result\":null")
  }

  "Notification with complex params" should "serialize correctly" in {
    val notification = Notification(
      method = "textDocument/publishDiagnostics",
      params = Some(Json.obj(
        "uri" -> Json.fromString("file:///test.cst"),
        "diagnostics" -> Json.arr(
          Json.obj(
            "range" -> Json.obj(
              "start" -> Json.obj("line" -> Json.fromInt(0), "character" -> Json.fromInt(0)),
              "end" -> Json.obj("line" -> Json.fromInt(0), "character" -> Json.fromInt(10))
            ),
            "message" -> Json.fromString("Error message"),
            "severity" -> Json.fromInt(1)
          )
        )
      ))
    )

    val json = notification.asJson.noSpaces

    json should include("\"diagnostics\":")
    json should include("\"range\":")
    json should include("\"message\":\"Error message\"")
  }
}
