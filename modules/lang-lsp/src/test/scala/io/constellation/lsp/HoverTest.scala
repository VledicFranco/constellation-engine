package io.constellation.lsp

import io.constellation.lsp.protocol.LspTypes.*
import io.circe.syntax.*
import io.circe.parser.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HoverTest extends AnyFlatSpec with Matchers {

  "Hover" should "serialize to correct JSON format" in {
    val hover = Hover(
      contents = MarkupContent(
        kind = "markdown",
        value = "### `test`\n\nDescription"
      )
    )

    val json       = hover.asJson
    val jsonString = json.noSpaces

    // Verify JSON structure
    jsonString should include("\"kind\":\"markdown\"")
    jsonString should include("\"value\":\"### `test`\\n\\nDescription\"")
    jsonString should include("\"contents\":")
  }

  it should "deserialize from JSON correctly" in {
    val jsonStr = """{"contents":{"kind":"markdown","value":"test"},"range":null}"""
    val result  = decode[Hover](jsonStr)

    result should be(
      Right(
        Hover(
          contents = MarkupContent("markdown", "test"),
          range = None
        )
      )
    )
  }

  it should "handle hover with range" in {
    val hover = Hover(
      contents = MarkupContent("markdown", "test"),
      range = Some(
        Range(
          start = Position(0, 0),
          end = Position(0, 5)
        )
      )
    )

    val json   = hover.asJson
    val result = json.as[Hover]

    result should be(Right(hover))
  }

  "MarkupContent" should "serialize kind and value fields" in {
    val markup = MarkupContent(kind = "markdown", value = "**bold**")
    val json   = markup.asJson.noSpaces

    json shouldBe """{"kind":"markdown","value":"**bold**"}"""
  }

  it should "handle multiline markdown" in {
    val markdown = """### Title
                     |
                     |Content line 1
                     |Content line 2""".stripMargin

    val markup = MarkupContent(kind = "markdown", value = markdown)
    val json   = markup.asJson

    // Should be able to round-trip
    json.as[MarkupContent] should be(Right(markup))
  }

  it should "handle markdown with special characters" in {
    val markdown = "### `Signature(param: String) -> String`\n\n**Description**"
    val markup   = MarkupContent(kind = "markdown", value = markdown)

    val json   = markup.asJson
    val result = json.as[MarkupContent]

    result should be(Right(markup))
  }

  it should "preserve formatting in multiline strings" in {
    val markdown = """### `Function()`
                     |
                     |**Version**: 1.0
                     |
                     |Description text
                     |
                     |### Parameters
                     |- **param**: `String`
                     |
                     |### Returns
                     |`String`
                     |
                     |**Tags**: test, example""".stripMargin

    val markup = MarkupContent(kind = "markdown", value = markdown)
    val json   = markup.asJson

    // Round-trip should preserve content
    json.as[MarkupContent] match {
      case Right(parsed) =>
        parsed.value shouldBe markdown
        parsed.kind shouldBe "markdown"
      case Left(error) =>
        fail(s"Failed to parse: ${error.message}")
    }
  }
}
