package io.constellation.lsp

import cats.effect.{IO, Ref}
import io.constellation.lsp.protocol.LspTypes._

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
        case None => docs // Document not open
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
  /** Get line at position (zero-based) */
  def getLine(line: Int): Option[String] = {
    val lines = text.split("\n")
    if (line >= 0 && line < lines.length) Some(lines(line))
    else None
  }

  /** Get character at position */
  def getCharAt(position: Position): Option[Char] = {
    getLine(position.line).flatMap { line =>
      if (position.character >= 0 && position.character < line.length)
        Some(line.charAt(position.character))
      else None
    }
  }

  /** Get word at position (for completion context) */
  def getWordAtPosition(position: Position): Option[String] = {
    getLine(position.line).map { line =>
      val beforeCursor = line.take(position.character)
      val wordPattern = "[a-zA-Z0-9_]+$".r
      wordPattern.findFirstIn(beforeCursor).getOrElse("")
    }
  }

  /** Get all lines */
  def getLines: List[String] = text.split("\n").toList
}
