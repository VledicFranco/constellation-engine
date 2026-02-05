package io.constellation.lsp

import cats.effect.{IO, Ref}

import io.constellation.lang.ast.SourceFile
import io.constellation.lsp.protocol.LspTypes.*

/** Manages open text documents and their state */
class DocumentManager private (
    documents: Ref[IO, Map[String, DocumentState]]
) {

  /** Get document by URI */
  def getDocument(uri: String): IO[Option[DocumentState]] =
    documents.get.map(_.get(uri))

  /** Open a new document */
  def openDocument(uri: String, languageId: String, version: Int, text: String): IO[Unit] =
    documents.update { docs =>
      docs + (uri -> DocumentState(uri, languageId, version, text))
    }

  /** Update document content */
  def updateDocument(uri: String, version: Int, text: String): IO[Unit] =
    documents.update { docs =>
      docs.get(uri) match {
        case Some(doc) => docs + (uri -> doc.copy(version = version, text = text))
        case None      => docs // Document not open
      }
    }

  /** Close a document */
  def closeDocument(uri: String): IO[Unit] =
    documents.update(_ - uri)

  /** Get all open documents */
  def getAllDocuments: IO[Map[String, DocumentState]] =
    documents.get
}

object DocumentManager {
  def create: IO[DocumentManager] =
    Ref.of[IO, Map[String, DocumentState]](Map.empty).map(new DocumentManager(_))
}

/** State of an open text document */
case class DocumentState(
    uri: String,
    languageId: String,
    version: Int,
    text: String
) {

  /** Lazy source file for efficient span to line/col conversion */
  lazy val sourceFile: SourceFile = SourceFile(uri, text)

  /** Get line at position (zero-based) */
  def getLine(line: Int): Option[String] = {
    val lines = text.split("\n")
    if line >= 0 && line < lines.length then Some(lines(line))
    else None
  }

  /** Get character at position */
  def getCharAt(position: Position): Option[Char] =
    getLine(position.line).flatMap { line =>
      if position.character >= 0 && position.character < line.length then
        Some(line.charAt(position.character))
      else None
    }

  /** Get word at position (for completion and hover) */
  def getWordAtPosition(position: Position): Option[String] =
    getLine(position.line).map { line =>
      val char = position.character

      // Find the start of the word (scan backwards)
      var start = char
      while start > 0 && line.charAt(start - 1).toString.matches("[a-zA-Z0-9_]") do start -= 1

      // Find the end of the word (scan forwards)
      var end = char
      while end < line.length && line.charAt(end).toString.matches("[a-zA-Z0-9_]") do end += 1

      // Extract the word
      if start < end then {
        line.substring(start, end)
      } else {
        ""
      }
    }

  /** Get all lines */
  def getLines: List[String] = text.split("\n").toList
}
