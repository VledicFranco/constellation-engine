package io.constellation.lsp

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.lsp.protocol.LspTypes.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DocumentManagerTest extends AnyFlatSpec with Matchers {

  // ========== DocumentManager Tests ==========

  "DocumentManager" should "create empty manager" in {
    val result = for {
      manager <- DocumentManager.create
      docs <- manager.getAllDocuments
    } yield docs

    val docs = result.unsafeRunSync()
    docs shouldBe empty
  }

  it should "open a document" in {
    val result = for {
      manager <- DocumentManager.create
      _ <- manager.openDocument(
        uri = "file:///test.cst",
        languageId = "constellation",
        version = 1,
        text = "in x: Int"
      )
      doc <- manager.getDocument("file:///test.cst")
    } yield doc

    val doc = result.unsafeRunSync()
    doc shouldBe defined
    doc.get.uri shouldBe "file:///test.cst"
    doc.get.languageId shouldBe "constellation"
    doc.get.version shouldBe 1
    doc.get.text shouldBe "in x: Int"
  }

  it should "return None for non-existent document" in {
    val result = for {
      manager <- DocumentManager.create
      doc <- manager.getDocument("file:///nonexistent.cst")
    } yield doc

    val doc = result.unsafeRunSync()
    doc shouldBe None
  }

  it should "update document content" in {
    val result = for {
      manager <- DocumentManager.create
      _ <- manager.openDocument("file:///test.cst", "constellation", 1, "in x: Int")
      _ <- manager.updateDocument("file:///test.cst", 2, "in y: String")
      doc <- manager.getDocument("file:///test.cst")
    } yield doc

    val doc = result.unsafeRunSync()
    doc shouldBe defined
    doc.get.version shouldBe 2
    doc.get.text shouldBe "in y: String"
  }

  it should "ignore update for non-existent document" in {
    val result = for {
      manager <- DocumentManager.create
      _ <- manager.updateDocument("file:///nonexistent.cst", 1, "text")
      doc <- manager.getDocument("file:///nonexistent.cst")
    } yield doc

    val doc = result.unsafeRunSync()
    doc shouldBe None
  }

  it should "close a document" in {
    val result = for {
      manager <- DocumentManager.create
      _ <- manager.openDocument("file:///test.cst", "constellation", 1, "in x: Int")
      _ <- manager.closeDocument("file:///test.cst")
      doc <- manager.getDocument("file:///test.cst")
    } yield doc

    val doc = result.unsafeRunSync()
    doc shouldBe None
  }

  it should "handle closing non-existent document" in {
    val result = for {
      manager <- DocumentManager.create
      _ <- manager.closeDocument("file:///nonexistent.cst")
      docs <- manager.getAllDocuments
    } yield docs

    val docs = result.unsafeRunSync()
    docs shouldBe empty
  }

  it should "manage multiple documents" in {
    val result = for {
      manager <- DocumentManager.create
      _ <- manager.openDocument("file:///a.cst", "constellation", 1, "in a: Int")
      _ <- manager.openDocument("file:///b.cst", "constellation", 1, "in b: String")
      _ <- manager.openDocument("file:///c.cst", "constellation", 1, "in c: Float")
      docs <- manager.getAllDocuments
    } yield docs

    val docs = result.unsafeRunSync()
    docs.size shouldBe 3
    docs.keys should contain allOf ("file:///a.cst", "file:///b.cst", "file:///c.cst")
  }

  it should "overwrite document when opened with same URI" in {
    val result = for {
      manager <- DocumentManager.create
      _ <- manager.openDocument("file:///test.cst", "constellation", 1, "first")
      _ <- manager.openDocument("file:///test.cst", "constellation", 2, "second")
      doc <- manager.getDocument("file:///test.cst")
    } yield doc

    val doc = result.unsafeRunSync()
    doc shouldBe defined
    doc.get.text shouldBe "second"
    doc.get.version shouldBe 2
  }

  // ========== DocumentState Tests ==========

  "DocumentState" should "get line by index" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "line0\nline1\nline2"
    )

    doc.getLine(0) shouldBe Some("line0")
    doc.getLine(1) shouldBe Some("line1")
    doc.getLine(2) shouldBe Some("line2")
    doc.getLine(3) shouldBe None
    doc.getLine(-1) shouldBe None
  }

  it should "handle empty document" in {
    val doc = DocumentState(
      uri = "file:///empty.cst",
      languageId = "constellation",
      version = 1,
      text = ""
    )

    doc.getLine(0) shouldBe Some("")
    doc.getLine(1) shouldBe None
  }

  it should "handle single line document" in {
    val doc = DocumentState(
      uri = "file:///single.cst",
      languageId = "constellation",
      version = 1,
      text = "single line content"
    )

    doc.getLine(0) shouldBe Some("single line content")
    doc.getLine(1) shouldBe None
  }

  it should "get all lines" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "first\nsecond\nthird"
    )

    doc.getLines shouldBe List("first", "second", "third")
  }

  it should "get character at position" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "hello\nworld"
    )

    doc.getCharAt(Position(0, 0)) shouldBe Some('h')
    doc.getCharAt(Position(0, 4)) shouldBe Some('o')
    doc.getCharAt(Position(1, 0)) shouldBe Some('w')
    doc.getCharAt(Position(1, 4)) shouldBe Some('d')
  }

  it should "return None for invalid character position" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "hello"
    )

    doc.getCharAt(Position(0, 10)) shouldBe None
    doc.getCharAt(Position(0, -1)) shouldBe None
    doc.getCharAt(Position(1, 0)) shouldBe None
    doc.getCharAt(Position(-1, 0)) shouldBe None
  }

  it should "get word at position" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "result = Uppercase(text)"
    )

    // Position at start of "result"
    doc.getWordAtPosition(Position(0, 0)) shouldBe Some("result")
    doc.getWordAtPosition(Position(0, 3)) shouldBe Some("result")

    // Position at "Uppercase"
    doc.getWordAtPosition(Position(0, 9)) shouldBe Some("Uppercase")
    doc.getWordAtPosition(Position(0, 14)) shouldBe Some("Uppercase")

    // Position at "text"
    doc.getWordAtPosition(Position(0, 19)) shouldBe Some("text")
  }

  it should "get word at position for multi-line document" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "in text: String\nresult = Uppercase(text)\nout result"
    )

    doc.getWordAtPosition(Position(0, 3)) shouldBe Some("text")
    doc.getWordAtPosition(Position(1, 10)) shouldBe Some("Uppercase")
    doc.getWordAtPosition(Position(2, 4)) shouldBe Some("result")
  }

  it should "return empty string for word at non-word position" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "result = Uppercase(text)"
    )

    // Position at "=" (character 7) - between spaces, should return empty
    doc.getWordAtPosition(Position(0, 7)) shouldBe Some("")

    // Position at "(" (character 18) - right after "Uppercase", algorithm scans backwards
    // and finds the preceding word, which is the expected behavior for LSP hover
    doc.getWordAtPosition(Position(0, 18)) shouldBe Some("Uppercase")
  }

  it should "handle word with underscores" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "my_variable = some_function(another_var)"
    )

    doc.getWordAtPosition(Position(0, 5)) shouldBe Some("my_variable")
    doc.getWordAtPosition(Position(0, 18)) shouldBe Some("some_function")
    doc.getWordAtPosition(Position(0, 32)) shouldBe Some("another_var")
  }

  it should "handle word with numbers" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "var1 = func2(data3)"
    )

    doc.getWordAtPosition(Position(0, 2)) shouldBe Some("var1")
    doc.getWordAtPosition(Position(0, 8)) shouldBe Some("func2")
    doc.getWordAtPosition(Position(0, 15)) shouldBe Some("data3")
  }

  it should "return None for word at invalid line" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "single line"
    )

    doc.getWordAtPosition(Position(1, 0)) shouldBe None
    doc.getWordAtPosition(Position(-1, 0)) shouldBe None
  }

  it should "have lazy sourceFile" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "in x: Int\nout x"
    )

    // Accessing sourceFile should work
    doc.sourceFile should not be null
    doc.sourceFile.name shouldBe "file:///test.cst"
    doc.sourceFile.content shouldBe "in x: Int\nout x"
  }

  // ========== Edge Cases ==========

  "DocumentState with empty lines" should "handle correctly" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "line1\n\nline3\n\nline5"
    )

    doc.getLine(0) shouldBe Some("line1")
    doc.getLine(1) shouldBe Some("")
    doc.getLine(2) shouldBe Some("line3")
    doc.getLine(3) shouldBe Some("")
    doc.getLine(4) shouldBe Some("line5")
    doc.getLines.length shouldBe 5
  }

  "DocumentState with trailing newline" should "handle correctly" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "line1\nline2\n"
    )

    doc.getLines.length shouldBe 2
  }

  "DocumentState with special characters" should "handle correctly" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "text = \"hello, world!\"\nresult = Concat(a, b)"
    )

    doc.getWordAtPosition(Position(0, 0)) shouldBe Some("text")
    doc.getWordAtPosition(Position(1, 9)) shouldBe Some("Concat")
  }

  "DocumentManager concurrency" should "handle concurrent updates" in {
    val result = for {
      manager <- DocumentManager.create
      _ <- manager.openDocument("file:///test.cst", "constellation", 1, "initial")
      // Simulate concurrent updates
      _ <- IO.parSequenceN(4)(List(
        manager.updateDocument("file:///test.cst", 2, "update1"),
        manager.updateDocument("file:///test.cst", 3, "update2"),
        manager.updateDocument("file:///test.cst", 4, "update3"),
        manager.updateDocument("file:///test.cst", 5, "update4")
      ))
      doc <- manager.getDocument("file:///test.cst")
    } yield doc

    // Just verify it completes without error
    val doc = result.unsafeRunSync()
    doc shouldBe defined
  }
}
