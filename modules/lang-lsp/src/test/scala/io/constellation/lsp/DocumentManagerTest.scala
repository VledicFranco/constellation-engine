package io.constellation.lsp

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
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

  // ========== Additional DocumentState Edge Cases ==========

  "DocumentState.getWordAtPosition" should "return empty string for empty line" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "line1\n\nline3"
    )

    doc.getWordAtPosition(Position(1, 0)) shouldBe Some("")
  }

  it should "handle position at line start" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "  hello world"
    )

    // Position at leading space
    doc.getWordAtPosition(Position(0, 0)) shouldBe Some("")
    doc.getWordAtPosition(Position(0, 1)) shouldBe Some("")
    // Position at word start
    doc.getWordAtPosition(Position(0, 2)) shouldBe Some("hello")
  }

  it should "handle position at line end" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "hello world"
    )

    // Position at end of last word
    doc.getWordAtPosition(Position(0, 10)) shouldBe Some("world")
    // Position past end of last word
    doc.getWordAtPosition(Position(0, 11)) shouldBe Some("world")
  }

  it should "handle single character word" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "a b c"
    )

    doc.getWordAtPosition(Position(0, 0)) shouldBe Some("a")
    doc.getWordAtPosition(Position(0, 2)) shouldBe Some("b")
    doc.getWordAtPosition(Position(0, 4)) shouldBe Some("c")
  }

  it should "handle only whitespace" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "   "
    )

    doc.getWordAtPosition(Position(0, 0)) shouldBe Some("")
    doc.getWordAtPosition(Position(0, 1)) shouldBe Some("")
    doc.getWordAtPosition(Position(0, 2)) shouldBe Some("")
  }

  "DocumentState.getCharAt" should "handle position at newline" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "ab\ncd"
    )

    // Position after 'b' but before newline
    doc.getCharAt(Position(0, 0)) shouldBe Some('a')
    doc.getCharAt(Position(0, 1)) shouldBe Some('b')
    // Position past end of line should return None
    doc.getCharAt(Position(0, 2)) shouldBe None
  }

  it should "handle empty line" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "line1\n\nline3"
    )

    // Empty line has no characters
    doc.getCharAt(Position(1, 0)) shouldBe None
  }

  it should "handle negative character position" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "hello"
    )

    doc.getCharAt(Position(0, -1)) shouldBe None
  }

  "DocumentState.getLine" should "handle only newlines" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "\n\n\n"
    )

    // Java/Scala's split removes trailing empty strings by default
    // "\n\n\n".split("\n") produces empty array since all parts are empty
    // This is expected JVM behavior - no content means no lines
    doc.getLines.length shouldBe 0
  }

  it should "handle newlines with content before" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "content\n\n\n"
    )

    // "content\n\n\n".split("\n") preserves lines up to but not after last content
    doc.getLine(0) shouldBe Some("content")
    doc.getLines.length shouldBe 1
  }

  it should "handle multiple empty lines between content" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "first\n\n\nlast"
    )

    doc.getLine(0) shouldBe Some("first")
    doc.getLine(1) shouldBe Some("")
    doc.getLine(2) shouldBe Some("")
    doc.getLine(3) shouldBe Some("last")
    doc.getLines.length shouldBe 4
  }

  "DocumentState.getLines" should "handle single line without newline" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "single line no newline"
    )

    doc.getLines shouldBe List("single line no newline")
  }

  "DocumentState with unicode" should "handle unicode in getLine" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "émoji = \"🎉\"\nresult = Uppercase(text)"
    )

    // Lines are preserved correctly with unicode
    doc.getLine(0) shouldBe Some("émoji = \"🎉\"")
    doc.getLine(1) shouldBe Some("result = Uppercase(text)")
  }

  it should "handle ASCII word extraction around unicode" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "abc émoji xyz"
    )

    // The regex [a-zA-Z0-9_] only matches ASCII word characters
    // So unicode characters like 'é' break word boundaries
    doc.getWordAtPosition(Position(0, 0)) shouldBe Some("abc")
    doc.getWordAtPosition(Position(0, 10)) shouldBe Some("xyz")
  }

  it should "handle multi-byte characters in getCharAt" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "café"
    )

    doc.getCharAt(Position(0, 0)) shouldBe Some('c')
    doc.getCharAt(Position(0, 3)) shouldBe Some('é')
  }

  "DocumentState with long lines" should "handle correctly" in {
    val longLine = "x" * 10000
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = longLine
    )

    doc.getLine(0) shouldBe Some(longLine)
    doc.getCharAt(Position(0, 9999)) shouldBe Some('x')
    doc.getCharAt(Position(0, 10000)) shouldBe None
  }

  "DocumentState.sourceFile" should "have correct name and content" in {
    val doc = DocumentState(
      uri = "file:///test.cst",
      languageId = "constellation",
      version = 1,
      text = "in x: Int\nout x"
    )

    doc.sourceFile.name shouldBe "file:///test.cst"
    doc.sourceFile.content shouldBe "in x: Int\nout x"
  }

  // ========== Additional DocumentManager Edge Cases ==========

  "DocumentManager" should "handle rapid open/close cycles" in {
    val result = for {
      manager <- DocumentManager.create
      _ <- (1 to 100).toList.traverse_ { i =>
        for {
          _ <- manager.openDocument(s"file:///test$i.cst", "constellation", 1, s"content $i")
          _ <- manager.closeDocument(s"file:///test$i.cst")
        } yield ()
      }
      docs <- manager.getAllDocuments
    } yield docs

    val docs = result.unsafeRunSync()
    docs shouldBe empty
  }

  it should "handle concurrent open and close" in {
    val result = for {
      manager <- DocumentManager.create
      _ <- IO.parSequenceN(4)(List(
        manager.openDocument("file:///a.cst", "constellation", 1, "a"),
        manager.openDocument("file:///b.cst", "constellation", 1, "b"),
        manager.closeDocument("file:///c.cst"), // Non-existent
        manager.openDocument("file:///d.cst", "constellation", 1, "d")
      ))
      docs <- manager.getAllDocuments
    } yield docs

    val docs = result.unsafeRunSync()
    docs.size shouldBe 3
  }

  it should "handle very large documents" in {
    val largeContent = "in x: Int\n" * 10000
    val result = for {
      manager <- DocumentManager.create
      _ <- manager.openDocument("file:///large.cst", "constellation", 1, largeContent)
      doc <- manager.getDocument("file:///large.cst")
    } yield doc

    val doc = result.unsafeRunSync()
    doc shouldBe defined
    doc.get.text shouldBe largeContent
  }

  it should "preserve document state through multiple updates" in {
    val result = for {
      manager <- DocumentManager.create
      _ <- manager.openDocument("file:///test.cst", "constellation", 1, "v1")
      _ <- manager.updateDocument("file:///test.cst", 2, "v2")
      _ <- manager.updateDocument("file:///test.cst", 3, "v3")
      _ <- manager.updateDocument("file:///test.cst", 4, "v4")
      doc <- manager.getDocument("file:///test.cst")
    } yield doc

    val doc = result.unsafeRunSync()
    doc shouldBe defined
    doc.get.version shouldBe 4
    doc.get.text shouldBe "v4"
  }
}
