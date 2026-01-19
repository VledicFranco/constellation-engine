package io.constellation.lsp.protocol

import io.circe.Json
import io.circe.parser.*
import io.circe.syntax.*
import io.constellation.lsp.protocol.LspTypes.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LspTypesTest extends AnyFlatSpec with Matchers {

  // ========== Position Tests ==========

  "Position" should "serialize to JSON" in {
    val position = Position(line = 10, character = 5)
    val json = position.asJson.noSpaces

    json shouldBe """{"line":10,"character":5}"""
  }

  it should "deserialize from JSON" in {
    val jsonStr = """{"line":42,"character":15}"""
    val parsed = decode[Position](jsonStr)

    parsed match {
      case Right(position) =>
        position.line shouldBe 42
        position.character shouldBe 15
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  it should "round-trip through JSON" in {
    val original = Position(100, 50)
    val json = original.asJson
    val parsed = json.as[Position]

    parsed shouldBe Right(original)
  }

  it should "handle zero-based positions" in {
    val position = Position(0, 0)
    val json = position.asJson
    val parsed = json.as[Position]

    parsed shouldBe Right(position)
  }

  // ========== Range Tests ==========

  "Range" should "serialize to JSON" in {
    val range = Range(
      start = Position(0, 0),
      end = Position(0, 10)
    )
    val json = range.asJson.noSpaces

    json should include("\"start\":{\"line\":0,\"character\":0}")
    json should include("\"end\":{\"line\":0,\"character\":10}")
  }

  it should "deserialize from JSON" in {
    val jsonStr = """{"start":{"line":5,"character":10},"end":{"line":5,"character":20}}"""
    val parsed = decode[Range](jsonStr)

    parsed match {
      case Right(range) =>
        range.start shouldBe Position(5, 10)
        range.end shouldBe Position(5, 20)
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  it should "round-trip through JSON" in {
    val original = Range(Position(10, 5), Position(15, 25))
    val json = original.asJson
    val parsed = json.as[Range]

    parsed shouldBe Right(original)
  }

  it should "handle multi-line ranges" in {
    val range = Range(Position(0, 0), Position(100, 0))
    val json = range.asJson
    val parsed = json.as[Range]

    parsed shouldBe Right(range)
  }

  // ========== TextDocumentIdentifier Tests ==========

  "TextDocumentIdentifier" should "serialize to JSON" in {
    val doc = TextDocumentIdentifier("file:///test.cst")
    val json = doc.asJson.noSpaces

    json shouldBe """{"uri":"file:///test.cst"}"""
  }

  it should "deserialize from JSON" in {
    val jsonStr = """{"uri":"file:///path/to/file.cst"}"""
    val parsed = decode[TextDocumentIdentifier](jsonStr)

    parsed shouldBe Right(TextDocumentIdentifier("file:///path/to/file.cst"))
  }

  // ========== VersionedTextDocumentIdentifier Tests ==========

  "VersionedTextDocumentIdentifier" should "serialize to JSON" in {
    val doc = VersionedTextDocumentIdentifier("file:///test.cst", version = 5)
    val json = doc.asJson.noSpaces

    json should include("\"uri\":\"file:///test.cst\"")
    json should include("\"version\":5")
  }

  it should "deserialize from JSON" in {
    val jsonStr = """{"uri":"file:///test.cst","version":10}"""
    val parsed = decode[VersionedTextDocumentIdentifier](jsonStr)

    parsed match {
      case Right(doc) =>
        doc.uri shouldBe "file:///test.cst"
        doc.version shouldBe 10
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== TextDocumentItem Tests ==========

  "TextDocumentItem" should "serialize to JSON" in {
    val doc = TextDocumentItem(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "in x: Int\nout x"
    )
    val json = doc.asJson.noSpaces

    json should include("\"uri\":\"file:///test.cst\"")
    json should include("\"languageId\":\"constellation\"")
    json should include("\"version\":1")
    json should include("\"text\":")
  }

  it should "deserialize from JSON" in {
    val jsonStr = """{"uri":"file:///test.cst","languageId":"constellation","version":2,"text":"in y: String"}"""
    val parsed = decode[TextDocumentItem](jsonStr)

    parsed match {
      case Right(doc) =>
        doc.uri shouldBe "file:///test.cst"
        doc.languageId shouldBe "constellation"
        doc.version shouldBe 2
        doc.text shouldBe "in y: String"
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== TextDocumentContentChangeEvent Tests ==========

  "TextDocumentContentChangeEvent" should "serialize full document change" in {
    val change = TextDocumentContentChangeEvent(
      range = None,
      rangeLength = None,
      text = "new content"
    )
    val json = change.asJson.noSpaces

    json should include("\"text\":\"new content\"")
  }

  it should "serialize incremental change with range" in {
    val change = TextDocumentContentChangeEvent(
      range = Some(Range(Position(5, 0), Position(5, 10))),
      rangeLength = Some(10),
      text = "replacement"
    )
    val json = change.asJson.noSpaces

    json should include("\"range\":")
    json should include("\"rangeLength\":10")
    json should include("\"text\":\"replacement\"")
  }

  it should "deserialize from JSON" in {
    val jsonStr = """{"text":"updated content"}"""
    val parsed = decode[TextDocumentContentChangeEvent](jsonStr)

    parsed match {
      case Right(change) =>
        change.text shouldBe "updated content"
        change.range shouldBe None
      case Left(error) =>
        fail(s"Failed to parse: ${error.getMessage}")
    }
  }

  // ========== Location Tests ==========

  "Location" should "serialize to JSON" in {
    val location = Location(
      uri = "file:///test.cst",
      range = Range(Position(10, 5), Position(10, 15))
    )
    val json = location.asJson.noSpaces

    json should include("\"uri\":\"file:///test.cst\"")
    json should include("\"range\":")
  }

  it should "round-trip through JSON" in {
    val original = Location(
      uri = "file:///another.cst",
      range = Range(Position(0, 0), Position(100, 50))
    )
    val json = original.asJson
    val parsed = json.as[Location]

    parsed shouldBe Right(original)
  }

  // ========== DiagnosticSeverity Tests ==========

  "DiagnosticSeverity" should "serialize to integer values" in {
    DiagnosticSeverity.Error.asJson shouldBe Json.fromInt(1)
    DiagnosticSeverity.Warning.asJson shouldBe Json.fromInt(2)
    DiagnosticSeverity.Information.asJson shouldBe Json.fromInt(3)
    DiagnosticSeverity.Hint.asJson shouldBe Json.fromInt(4)
  }

  it should "deserialize from integer values" in {
    Json.fromInt(1).as[DiagnosticSeverity] shouldBe Right(DiagnosticSeverity.Error)
    Json.fromInt(2).as[DiagnosticSeverity] shouldBe Right(DiagnosticSeverity.Warning)
    Json.fromInt(3).as[DiagnosticSeverity] shouldBe Right(DiagnosticSeverity.Information)
    Json.fromInt(4).as[DiagnosticSeverity] shouldBe Right(DiagnosticSeverity.Hint)
  }

  it should "fail on unknown severity value" in {
    val result = Json.fromInt(99).as[DiagnosticSeverity]
    result.isLeft shouldBe true
  }

  // ========== Diagnostic Tests ==========

  "Diagnostic" should "serialize with all fields" in {
    val diagnostic = Diagnostic(
      range = Range(Position(5, 0), Position(5, 10)),
      severity = Some(DiagnosticSeverity.Error),
      code = Some("E001"),
      source = Some("constellation-lang"),
      message = "Undefined variable: x"
    )
    val json = diagnostic.asJson.noSpaces

    json should include("\"range\":")
    json should include("\"severity\":1")
    json should include("\"code\":\"E001\"")
    json should include("\"source\":\"constellation-lang\"")
    json should include("\"message\":\"Undefined variable: x\"")
  }

  it should "serialize without optional fields" in {
    val diagnostic = Diagnostic(
      range = Range(Position(0, 0), Position(0, 5)),
      severity = None,
      code = None,
      source = None,
      message = "Error message"
    )
    val json = diagnostic.asJson.noSpaces

    json should include("\"message\":\"Error message\"")
    json should include("\"range\":")
  }

  it should "round-trip through JSON" in {
    val original = Diagnostic(
      range = Range(Position(10, 5), Position(10, 15)),
      severity = Some(DiagnosticSeverity.Warning),
      code = Some("W001"),
      source = Some("test"),
      message = "Test warning"
    )
    val json = original.asJson
    val parsed = json.as[Diagnostic]

    parsed shouldBe Right(original)
  }

  // ========== CompletionItemKind Tests ==========

  "CompletionItemKind" should "serialize to integer values" in {
    CompletionItemKind.Text.asJson shouldBe Json.fromInt(1)
    CompletionItemKind.Function.asJson shouldBe Json.fromInt(3)
    CompletionItemKind.Module.asJson shouldBe Json.fromInt(9)
    CompletionItemKind.Keyword.asJson shouldBe Json.fromInt(14)
  }

  it should "deserialize from integer values" in {
    Json.fromInt(1).as[CompletionItemKind] shouldBe Right(CompletionItemKind.Text)
    Json.fromInt(3).as[CompletionItemKind] shouldBe Right(CompletionItemKind.Function)
    Json.fromInt(9).as[CompletionItemKind] shouldBe Right(CompletionItemKind.Module)
    Json.fromInt(14).as[CompletionItemKind] shouldBe Right(CompletionItemKind.Keyword)
  }

  it should "fail on unknown kind value" in {
    val result = Json.fromInt(999).as[CompletionItemKind]
    result.isLeft shouldBe true
  }

  // ========== CompletionItem Tests ==========

  "CompletionItem" should "serialize with all fields" in {
    val item = CompletionItem(
      label = "Uppercase",
      kind = Some(CompletionItemKind.Function),
      detail = Some("Uppercase(text: String) -> String"),
      documentation = Some("Converts text to uppercase"),
      insertText = Some("Uppercase()"),
      filterText = Some("uppercase"),
      sortText = Some("0_Uppercase")
    )
    val json = item.asJson.noSpaces

    json should include("\"label\":\"Uppercase\"")
    json should include("\"kind\":3")
    json should include("\"detail\":")
    json should include("\"documentation\":")
    json should include("\"insertText\":\"Uppercase()\"")
  }

  it should "serialize with minimal fields" in {
    val item = CompletionItem(
      label = "in",
      kind = Some(CompletionItemKind.Keyword),
      detail = Some("Input declaration"),
      documentation = None,
      insertText = None,
      filterText = None,
      sortText = None
    )
    val json = item.asJson.noSpaces

    json should include("\"label\":\"in\"")
    json should include("\"kind\":14")
  }

  it should "round-trip through JSON" in {
    val original = CompletionItem(
      label = "TestFunc",
      kind = Some(CompletionItemKind.Function),
      detail = Some("Test function"),
      documentation = Some("Documentation"),
      insertText = Some("TestFunc()"),
      filterText = Some("test"),
      sortText = Some("test")
    )
    val json = original.asJson
    val parsed = json.as[CompletionItem]

    parsed shouldBe Right(original)
  }

  // ========== CompletionList Tests ==========

  "CompletionList" should "serialize with items" in {
    val list = CompletionList(
      isIncomplete = false,
      items = List(
        CompletionItem("in", Some(CompletionItemKind.Keyword), Some("Input"), None, None, None, None),
        CompletionItem("out", Some(CompletionItemKind.Keyword), Some("Output"), None, None, None, None)
      )
    )
    val json = list.asJson.noSpaces

    json should include("\"isIncomplete\":false")
    json should include("\"items\":[")
    json should include("\"label\":\"in\"")
    json should include("\"label\":\"out\"")
  }

  it should "serialize empty list" in {
    val list = CompletionList(
      isIncomplete = true,
      items = List.empty
    )
    val json = list.asJson.noSpaces

    json should include("\"isIncomplete\":true")
    json should include("\"items\":[]")
  }

  it should "round-trip through JSON" in {
    val original = CompletionList(
      isIncomplete = false,
      items = List(
        CompletionItem("test", None, None, None, None, None, None)
      )
    )
    val json = original.asJson
    val parsed = json.as[CompletionList]

    parsed shouldBe Right(original)
  }

  // ========== MarkupContent Tests ==========

  "MarkupContent" should "serialize markdown content" in {
    val content = MarkupContent(
      kind = "markdown",
      value = "### Title\n\nDescription"
    )
    val json = content.asJson.noSpaces

    json should include("\"kind\":\"markdown\"")
    json should include("\"value\":\"### Title")
  }

  it should "serialize plaintext content" in {
    val content = MarkupContent(
      kind = "plaintext",
      value = "Simple text"
    )
    val json = content.asJson.noSpaces

    json should include("\"kind\":\"plaintext\"")
    json should include("\"value\":\"Simple text\"")
  }

  it should "round-trip through JSON" in {
    val original = MarkupContent("markdown", "**bold** text")
    val json = original.asJson
    val parsed = json.as[MarkupContent]

    parsed shouldBe Right(original)
  }

  // ========== Hover Tests ==========

  "Hover" should "serialize with contents only" in {
    val hover = Hover(
      contents = MarkupContent("markdown", "### Function")
    )
    val json = hover.asJson.noSpaces

    json should include("\"contents\":")
    json should include("\"kind\":\"markdown\"")
  }

  it should "serialize with range" in {
    val hover = Hover(
      contents = MarkupContent("markdown", "### Function"),
      range = Some(Range(Position(5, 10), Position(5, 20)))
    )
    val json = hover.asJson.noSpaces

    json should include("\"contents\":")
    json should include("\"range\":")
  }

  it should "round-trip through JSON" in {
    val original = Hover(
      contents = MarkupContent("markdown", "Test hover"),
      range = Some(Range(Position(0, 0), Position(0, 10)))
    )
    val json = original.asJson
    val parsed = json.as[Hover]

    parsed shouldBe Right(original)
  }

  // ========== DiagnosticRelatedInformation Tests ==========

  "DiagnosticRelatedInformation" should "serialize correctly" in {
    val info = DiagnosticRelatedInformation(
      location = Location(
        uri = "file:///related.cst",
        range = Range(Position(10, 0), Position(10, 20))
      ),
      message = "Related information here"
    )
    val json = info.asJson.noSpaces

    json should include("\"location\":")
    json should include("\"message\":\"Related information here\"")
  }

  it should "round-trip through JSON" in {
    val original = DiagnosticRelatedInformation(
      location = Location("file:///test.cst", Range(Position(0, 0), Position(0, 5))),
      message = "Test message"
    )
    val json = original.asJson
    val parsed = json.as[DiagnosticRelatedInformation]

    parsed shouldBe Right(original)
  }

  "Diagnostic with relatedInformation" should "serialize correctly" in {
    val diagnostic = Diagnostic(
      range = Range(Position(5, 0), Position(5, 10)),
      severity = Some(DiagnosticSeverity.Error),
      code = None,
      source = Some("constellation-lang"),
      message = "Type error",
      relatedInformation = Some(List(
        DiagnosticRelatedInformation(
          location = Location("file:///other.cst", Range(Position(10, 0), Position(10, 5))),
          message = "Declared here"
        )
      ))
    )
    val json = diagnostic.asJson.noSpaces

    json should include("\"relatedInformation\":")
    json should include("\"Declared here\"")
  }
}
