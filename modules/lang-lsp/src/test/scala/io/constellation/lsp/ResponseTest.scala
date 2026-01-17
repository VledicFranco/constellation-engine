package io.constellation.lsp

import io.constellation.lsp.protocol.JsonRpc._
import io.constellation.lsp.protocol.LspTypes._
import io.circe.syntax._
import io.circe.Json
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ResponseTest extends AnyFlatSpec with Matchers {

  "Response with Hover result" should "serialize correctly" in {
    val hover = Hover(
      contents = MarkupContent("markdown", "Test")
    )

    val response = Response(
      id = StringId("1"),
      result = Some(hover.asJson)
    )

    val json = response.asJson.noSpaces

    json should include("\"jsonrpc\":\"2.0\"")
    json should include("\"id\":\"1\"")
    json should include("\"result\":")
    json should include("\"contents\":")
  }

  it should "include markdown content" in {
    val hover = Hover(
      contents = MarkupContent("markdown", "### `Function()`\n\nDescription")
    )

    val response = Response(
      id = StringId("test-id"),
      result = Some(hover.asJson)
    )

    val json = response.asJson.noSpaces

    json should include("\"kind\":\"markdown\"")
    json should include("### `Function()`")
  }

  "Response with None result" should "serialize as null" in {
    val response = Response(
      id = StringId("1"),
      result = None
    )

    val json = response.asJson

    // Result should be null in JSON
    (json \\ "result").headOption shouldBe Some(Json.Null)
  }

  it should "be valid JSON-RPC" in {
    val response = Response(
      id = StringId("1"),
      result = None
    )

    val json = response.asJson.noSpaces

    json should include("\"jsonrpc\":\"2.0\"")
    json should include("\"id\":\"1\"")
    json should include("\"result\":null")
  }

  "Response with error" should "not include result field" in {
    val response = Response(
      id = StringId("1"),
      error = Some(ResponseError(
        code = ErrorCodes.InvalidParams,
        message = "Invalid params"
      ))
    )

    val json = response.asJson.noSpaces

    json should include("\"error\":")
    json should not include ("\"result\":")
  }

  it should "include error code and message" in {
    val response = Response(
      id = StringId("1"),
      error = Some(ResponseError(
        code = ErrorCodes.MethodNotFound,
        message = "Method not found: test"
      ))
    )

    val json = response.asJson.noSpaces

    json should include(s"\"code\":${ErrorCodes.MethodNotFound}")
    json should include("\"message\":\"Method not found: test\"")
  }

  "Response with numeric ID" should "serialize correctly" in {
    val hover = Hover(
      contents = MarkupContent("markdown", "Test")
    )

    val response = Response(
      id = NumericId(42),
      result = Some(hover.asJson)
    )

    val json = response.asJson.noSpaces

    json should include("\"id\":42")
  }

  "Response" should "round-trip through JSON" in {
    val hover = Hover(
      contents = MarkupContent("markdown", "### Test\n\nDescription")
    )

    val originalResponse = Response(
      id = StringId("test"),
      result = Some(hover.asJson)
    )

    val json = originalResponse.asJson
    val parsed = json.as[Response]

    parsed match {
      case Right(response) =>
        response.id shouldBe originalResponse.id
        response.result.isDefined shouldBe true
        response.error shouldBe None
      case Left(error) =>
        fail(s"Failed to parse response: ${error.message}")
    }
  }

  it should "handle hover with range in response" in {
    val hover = Hover(
      contents = MarkupContent("markdown", "Test"),
      range = Some(Range(Position(0, 0), Position(0, 10)))
    )

    val response = Response(
      id = StringId("1"),
      result = Some(hover.asJson)
    )

    val json = response.asJson
    val parsed = json.as[Response]

    parsed should be (Symbol("right"))
  }
}
