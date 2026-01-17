package io.constellation.lsp.protocol

import io.circe._
import io.circe.generic.semiauto._
import LspTypes._

/** LSP message types for requests, responses, and notifications */
object LspMessages {

  // ========== Initialize ==========

  case class InitializeParams(
    processId: Option[Int],
    rootUri: Option[String],
    capabilities: ClientCapabilities
  )

  case class ClientCapabilities(
    textDocument: Option[TextDocumentClientCapabilities] = None
  )

  case class TextDocumentClientCapabilities(
    completion: Option[CompletionClientCapabilities] = None,
    hover: Option[HoverClientCapabilities] = None
  )

  case class CompletionClientCapabilities(
    dynamicRegistration: Option[Boolean] = None
  )

  case class HoverClientCapabilities(
    dynamicRegistration: Option[Boolean] = None
  )

  case class InitializeResult(
    capabilities: ServerCapabilities
  )

  case class ServerCapabilities(
    textDocumentSync: Option[Int] = Some(1), // 1 = Full sync
    completionProvider: Option[CompletionOptions] = Some(CompletionOptions()),
    hoverProvider: Option[Boolean] = Some(true),
    executeCommandProvider: Option[ExecuteCommandOptions] = None
  )

  case class CompletionOptions(
    triggerCharacters: Option[List[String]] = None
  )

  case class ExecuteCommandOptions(
    commands: List[String]
  )

  // ========== Text Document Sync ==========

  case class DidOpenTextDocumentParams(
    textDocument: TextDocumentItem
  )

  case class DidChangeTextDocumentParams(
    textDocument: VersionedTextDocumentIdentifier,
    contentChanges: List[TextDocumentContentChangeEvent]
  )

  case class DidCloseTextDocumentParams(
    textDocument: TextDocumentIdentifier
  )

  case class PublishDiagnosticsParams(
    uri: String,
    diagnostics: List[Diagnostic]
  )

  // ========== Completion ==========

  case class CompletionParams(
    textDocument: TextDocumentIdentifier,
    position: Position,
    context: Option[CompletionContext] = None
  )

  case class CompletionContext(
    triggerKind: Int,
    triggerCharacter: Option[String] = None
  )

  // ========== Hover ==========

  case class HoverParams(
    textDocument: TextDocumentIdentifier,
    position: Position
  )

  // ========== Custom: Execute Pipeline ==========

  case class ExecutePipelineParams(
    uri: String,
    inputs: Map[String, Json]
  )

  case class ExecutePipelineResult(
    success: Boolean,
    outputs: Option[Map[String, Json]],
    error: Option[String]
  )

  // JSON encoders/decoders

  given Encoder[InitializeParams] = deriveEncoder
  given Decoder[InitializeParams] = deriveDecoder

  given Encoder[ClientCapabilities] = deriveEncoder
  given Decoder[ClientCapabilities] = deriveDecoder

  given Encoder[TextDocumentClientCapabilities] = deriveEncoder
  given Decoder[TextDocumentClientCapabilities] = deriveDecoder

  given Encoder[CompletionClientCapabilities] = deriveEncoder
  given Decoder[CompletionClientCapabilities] = deriveDecoder

  given Encoder[HoverClientCapabilities] = deriveEncoder
  given Decoder[HoverClientCapabilities] = deriveDecoder

  given Encoder[InitializeResult] = deriveEncoder
  given Decoder[InitializeResult] = deriveDecoder

  given Encoder[ServerCapabilities] = deriveEncoder
  given Decoder[ServerCapabilities] = deriveDecoder

  given Encoder[CompletionOptions] = deriveEncoder
  given Decoder[CompletionOptions] = deriveDecoder

  given Encoder[ExecuteCommandOptions] = deriveEncoder
  given Decoder[ExecuteCommandOptions] = deriveDecoder

  given Encoder[DidOpenTextDocumentParams] = deriveEncoder
  given Decoder[DidOpenTextDocumentParams] = deriveDecoder

  given Encoder[DidChangeTextDocumentParams] = deriveEncoder
  given Decoder[DidChangeTextDocumentParams] = deriveDecoder

  given Encoder[DidCloseTextDocumentParams] = deriveEncoder
  given Decoder[DidCloseTextDocumentParams] = deriveDecoder

  given Encoder[PublishDiagnosticsParams] = deriveEncoder
  given Decoder[PublishDiagnosticsParams] = deriveDecoder

  given Encoder[CompletionParams] = deriveEncoder
  given Decoder[CompletionParams] = deriveDecoder

  given Encoder[CompletionContext] = deriveEncoder
  given Decoder[CompletionContext] = deriveDecoder

  given Encoder[HoverParams] = deriveEncoder
  given Decoder[HoverParams] = deriveDecoder

  given Encoder[ExecutePipelineParams] = deriveEncoder
  given Decoder[ExecutePipelineParams] = deriveDecoder

  given Encoder[ExecutePipelineResult] = deriveEncoder
  given Decoder[ExecutePipelineResult] = deriveDecoder
}
