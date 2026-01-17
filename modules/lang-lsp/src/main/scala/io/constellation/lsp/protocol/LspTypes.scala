package io.constellation.lsp.protocol

import io.circe._
import io.circe.generic.semiauto._

/** Core LSP protocol types */
object LspTypes {

  /** Position in a text document (zero-based) */
  case class Position(
    line: Int,
    character: Int
  )

  /** Range in a text document */
  case class Range(
    start: Position,
    end: Position
  )

  /** Text document identifier */
  case class TextDocumentIdentifier(
    uri: String
  )

  /** Versioned text document identifier */
  case class VersionedTextDocumentIdentifier(
    uri: String,
    version: Int
  )

  /** Text document item (full content) */
  case class TextDocumentItem(
    uri: String,
    languageId: String,
    version: Int,
    text: String
  )

  /** Text document content change event */
  case class TextDocumentContentChangeEvent(
    range: Option[Range],
    rangeLength: Option[Int],
    text: String
  )

  /** Location in a text document */
  case class Location(
    uri: String,
    range: Range
  )

  /** Diagnostic severity */
  enum DiagnosticSeverity(val value: Int):
    case Error extends DiagnosticSeverity(1)
    case Warning extends DiagnosticSeverity(2)
    case Information extends DiagnosticSeverity(3)
    case Hint extends DiagnosticSeverity(4)

  /** Diagnostic (error, warning, etc.) */
  case class Diagnostic(
    range: Range,
    severity: Option[DiagnosticSeverity],
    code: Option[String],
    source: Option[String],
    message: String,
    relatedInformation: Option[List[DiagnosticRelatedInformation]] = None
  )

  /** Related information for a diagnostic */
  case class DiagnosticRelatedInformation(
    location: Location,
    message: String
  )

  /** Completion item kind */
  enum CompletionItemKind(val value: Int):
    case Text extends CompletionItemKind(1)
    case Method extends CompletionItemKind(2)
    case Function extends CompletionItemKind(3)
    case Constructor extends CompletionItemKind(4)
    case Field extends CompletionItemKind(5)
    case Variable extends CompletionItemKind(6)
    case Class extends CompletionItemKind(7)
    case Module extends CompletionItemKind(9)
    case Property extends CompletionItemKind(10)
    case Keyword extends CompletionItemKind(14)
    case Snippet extends CompletionItemKind(15)

  /** Completion item */
  case class CompletionItem(
    label: String,
    kind: Option[CompletionItemKind],
    detail: Option[String],
    documentation: Option[String],
    insertText: Option[String],
    filterText: Option[String],
    sortText: Option[String]
  )

  /** Completion list */
  case class CompletionList(
    isIncomplete: Boolean,
    items: List[CompletionItem]
  )

  /** Hover information */
  case class Hover(
    contents: MarkupContent,
    range: Option[Range] = None
  )

  /** Markup content (markdown or plaintext) */
  case class MarkupContent(
    kind: String, // "markdown" or "plaintext"
    value: String
  )

  // JSON encoders/decoders

  given Encoder[Position] = deriveEncoder
  given Decoder[Position] = deriveDecoder

  given Encoder[Range] = deriveEncoder
  given Decoder[Range] = deriveDecoder

  given Encoder[TextDocumentIdentifier] = deriveEncoder
  given Decoder[TextDocumentIdentifier] = deriveDecoder

  given Encoder[VersionedTextDocumentIdentifier] = deriveEncoder
  given Decoder[VersionedTextDocumentIdentifier] = deriveDecoder

  given Encoder[TextDocumentItem] = deriveEncoder
  given Decoder[TextDocumentItem] = deriveDecoder

  given Encoder[TextDocumentContentChangeEvent] = deriveEncoder
  given Decoder[TextDocumentContentChangeEvent] = deriveDecoder

  given Encoder[Location] = deriveEncoder
  given Decoder[Location] = deriveDecoder

  given Encoder[DiagnosticSeverity] = Encoder.encodeInt.contramap(_.value)
  given Decoder[DiagnosticSeverity] = Decoder.decodeInt.emap {
    case 1 => Right(DiagnosticSeverity.Error)
    case 2 => Right(DiagnosticSeverity.Warning)
    case 3 => Right(DiagnosticSeverity.Information)
    case 4 => Right(DiagnosticSeverity.Hint)
    case other => Left(s"Unknown diagnostic severity: $other")
  }

  given Encoder[Diagnostic] = deriveEncoder
  given Decoder[Diagnostic] = deriveDecoder

  given Encoder[DiagnosticRelatedInformation] = deriveEncoder
  given Decoder[DiagnosticRelatedInformation] = deriveDecoder

  given Encoder[CompletionItemKind] = Encoder.encodeInt.contramap(_.value)
  given Decoder[CompletionItemKind] = Decoder.decodeInt.emap {
    case 1 => Right(CompletionItemKind.Text)
    case 2 => Right(CompletionItemKind.Method)
    case 3 => Right(CompletionItemKind.Function)
    case 4 => Right(CompletionItemKind.Constructor)
    case 5 => Right(CompletionItemKind.Field)
    case 6 => Right(CompletionItemKind.Variable)
    case 7 => Right(CompletionItemKind.Class)
    case 9 => Right(CompletionItemKind.Module)
    case 10 => Right(CompletionItemKind.Property)
    case 14 => Right(CompletionItemKind.Keyword)
    case 15 => Right(CompletionItemKind.Snippet)
    case other => Left(s"Unknown completion item kind: $other")
  }

  given Encoder[CompletionItem] = deriveEncoder
  given Decoder[CompletionItem] = deriveDecoder

  given Encoder[CompletionList] = deriveEncoder
  given Decoder[CompletionList] = deriveDecoder

  given Encoder[MarkupContent] = deriveEncoder
  given Decoder[MarkupContent] = deriveDecoder

  given Encoder[Hover] = deriveEncoder
  given Decoder[Hover] = deriveDecoder
}
