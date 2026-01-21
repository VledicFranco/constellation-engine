package io.constellation.lsp.protocol

import io.circe.*
import io.circe.generic.semiauto.*
import LspTypes.*

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

  // ========== Structured Error Information ==========

  /** Error category for classification */
  enum ErrorCategory:
    case Syntax    // Parser failures
    case Type      // Type mismatches, unknown types
    case Reference // Undefined variables, unknown modules
    case Runtime   // Execution failures, module exceptions
    case Internal  // Internal compiler errors (DAG compilation failures)

  /** Structured error information with source location and context */
  case class ErrorInfo(
      category: ErrorCategory,
      message: String,
      line: Option[Int] = None,           // 1-based line number
      column: Option[Int] = None,         // 1-based column number
      endLine: Option[Int] = None,        // End line for range highlighting
      endColumn: Option[Int] = None,      // End column for range highlighting
      codeContext: Option[String] = None, // Code snippet showing error location
      suggestion: Option[String] = None   // Suggested fix (e.g., "Did you mean: Uppercase?")
  )

  // ========== Custom: Execute Pipeline ==========

  case class ExecutePipelineParams(
      uri: String,
      inputs: Map[String, Json]
  )

  case class ExecutePipelineResult(
      success: Boolean,
      outputs: Option[Map[String, Json]],
      error: Option[String],                  // Simple error message (kept for backwards compat)
      errors: Option[List[ErrorInfo]] = None, // Structured errors with context
      executionTimeMs: Option[Long] = None
  )

  // ========== Custom: Get Input Schema ==========

  case class GetInputSchemaParams(
      uri: String
  )

  case class GetInputSchemaResult(
      success: Boolean,
      inputs: Option[List[InputField]],
      error: Option[String],                 // Simple error message (kept for backwards compat)
      errors: Option[List[ErrorInfo]] = None // Structured errors with context
  )

  // ========== Custom: Get DAG Structure ==========

  case class GetDagStructureParams(
      uri: String
  )

  case class GetDagStructureResult(
      success: Boolean,
      dag: Option[DagStructure],
      error: Option[String]
  )

  case class DagStructure(
      modules: Map[String, ModuleNode],
      data: Map[String, DataNode],
      inEdges: List[(String, String)],
      outEdges: List[(String, String)],
      declaredOutputs: List[String]
  )

  case class ModuleNode(
      name: String,
      consumes: Map[String, String],
      produces: Map[String, String]
  )

  case class DataNode(
      name: String,
      cType: String
  )

  case class InputField(
      name: String,
      `type`: TypeDescriptor,
      line: Int,
      example: Option[Json] = None // Example value from @example annotation
  )

  // ========== Custom: DAG Execution Updates ==========

  /** Execution state for a node in the DAG */
  enum ExecutionState:
    case Pending   // Not yet executed
    case Running   // Currently executing
    case Completed // Successfully executed
    case Failed    // Execution failed

  /** Update about a node's execution state */
  case class DagExecutionUpdateParams(
      uri: String,                         // Document URI
      nodeId: String,                      // Node UUID
      state: ExecutionState,               // Current state
      valuePreview: Option[String] = None, // Short preview of output value
      valueFull: Option[Json] = None,      // Full output value as JSON
      durationMs: Option[Long] = None,     // Execution time if completed
      error: Option[String] = None         // Error message if failed
  )

  /** Batch update for all nodes (e.g., when execution starts or completes) */
  case class DagExecutionBatchUpdateParams(
      uri: String,
      updates: List[DagExecutionUpdateParams]
  )

  sealed trait TypeDescriptor

  object TypeDescriptor {
    case class PrimitiveType(name: String)                                   extends TypeDescriptor
    case class ListType(elementType: TypeDescriptor)                         extends TypeDescriptor
    case class RecordType(fields: List[RecordField])                         extends TypeDescriptor
    case class MapType(keyType: TypeDescriptor, valueType: TypeDescriptor)   extends TypeDescriptor
    case class ParameterizedType(name: String, params: List[TypeDescriptor]) extends TypeDescriptor
    case class RefType(name: String)                                         extends TypeDescriptor
    case class UnionType(members: List[TypeDescriptor])                      extends TypeDescriptor
  }

  case class RecordField(name: String, `type`: TypeDescriptor)

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

  given Encoder[ErrorCategory] = Encoder.encodeString.contramap {
    case ErrorCategory.Syntax    => "syntax"
    case ErrorCategory.Type      => "type"
    case ErrorCategory.Reference => "reference"
    case ErrorCategory.Runtime   => "runtime"
    case ErrorCategory.Internal  => "internal"
  }

  given Decoder[ErrorCategory] = Decoder.decodeString.emap {
    case "syntax"    => Right(ErrorCategory.Syntax)
    case "type"      => Right(ErrorCategory.Type)
    case "reference" => Right(ErrorCategory.Reference)
    case "runtime"   => Right(ErrorCategory.Runtime)
    case "internal"  => Right(ErrorCategory.Internal)
    case other       => Left(s"Unknown error category: $other")
  }

  given Encoder[ErrorInfo] = deriveEncoder
  given Decoder[ErrorInfo] = deriveDecoder

  given Encoder[ExecutePipelineParams] = deriveEncoder
  given Decoder[ExecutePipelineParams] = deriveDecoder

  given Encoder[ExecutePipelineResult] = deriveEncoder
  given Decoder[ExecutePipelineResult] = deriveDecoder

  given Encoder[GetInputSchemaParams] = deriveEncoder
  given Decoder[GetInputSchemaParams] = deriveDecoder

  given Encoder[GetInputSchemaResult] = deriveEncoder
  given Decoder[GetInputSchemaResult] = deriveDecoder

  given Encoder[GetDagStructureParams] = deriveEncoder
  given Decoder[GetDagStructureParams] = deriveDecoder

  given Encoder[GetDagStructureResult] = deriveEncoder
  given Decoder[GetDagStructureResult] = deriveDecoder

  given Encoder[DagStructure] = deriveEncoder
  given Decoder[DagStructure] = deriveDecoder

  given Encoder[ModuleNode] = deriveEncoder
  given Decoder[ModuleNode] = deriveDecoder

  given Encoder[DataNode] = deriveEncoder
  given Decoder[DataNode] = deriveDecoder

  given Encoder[RecordField] = deriveEncoder
  given Decoder[RecordField] = deriveDecoder

  // TypeDescriptor requires manual encoder/decoder for discriminated union
  given Encoder[TypeDescriptor] = Encoder.instance {
    case TypeDescriptor.PrimitiveType(name) =>
      Json.obj("kind" -> Json.fromString("primitive"), "name" -> Json.fromString(name))
    case TypeDescriptor.ListType(elementType) =>
      Json.obj(
        "kind"        -> Json.fromString("list"),
        "elementType" -> Encoder[TypeDescriptor].apply(elementType)
      )
    case TypeDescriptor.RecordType(fields) =>
      Json.obj(
        "kind"   -> Json.fromString("record"),
        "fields" -> Encoder[List[RecordField]].apply(fields)
      )
    case TypeDescriptor.MapType(keyType, valueType) =>
      Json.obj(
        "kind"      -> Json.fromString("map"),
        "keyType"   -> Encoder[TypeDescriptor].apply(keyType),
        "valueType" -> Encoder[TypeDescriptor].apply(valueType)
      )
    case TypeDescriptor.ParameterizedType(name, params) =>
      Json.obj(
        "kind"   -> Json.fromString("parameterized"),
        "name"   -> Json.fromString(name),
        "params" -> Encoder[List[TypeDescriptor]].apply(params)
      )
    case TypeDescriptor.RefType(name) =>
      Json.obj("kind" -> Json.fromString("ref"), "name" -> Json.fromString(name))
    case TypeDescriptor.UnionType(members) =>
      Json.obj(
        "kind"    -> Json.fromString("union"),
        "members" -> Encoder[List[TypeDescriptor]].apply(members)
      )
  }

  given Decoder[TypeDescriptor] = Decoder.instance { cursor =>
    cursor.downField("kind").as[String].flatMap {
      case "primitive" =>
        cursor.downField("name").as[String].map(TypeDescriptor.PrimitiveType(_))
      case "list" =>
        cursor.downField("elementType").as[TypeDescriptor].map(TypeDescriptor.ListType(_))
      case "record" =>
        cursor.downField("fields").as[List[RecordField]].map(TypeDescriptor.RecordType(_))
      case "map" =>
        for {
          keyType   <- cursor.downField("keyType").as[TypeDescriptor]
          valueType <- cursor.downField("valueType").as[TypeDescriptor]
        } yield TypeDescriptor.MapType(keyType, valueType)
      case "parameterized" =>
        for {
          name   <- cursor.downField("name").as[String]
          params <- cursor.downField("params").as[List[TypeDescriptor]]
        } yield TypeDescriptor.ParameterizedType(name, params)
      case "ref" =>
        cursor.downField("name").as[String].map(TypeDescriptor.RefType(_))
      case "union" =>
        cursor.downField("members").as[List[TypeDescriptor]].map(TypeDescriptor.UnionType(_))
      case other =>
        Left(DecodingFailure(s"Unknown TypeDescriptor kind: $other", cursor.history))
    }
  }

  given Encoder[InputField] = deriveEncoder
  given Decoder[InputField] = deriveDecoder

  given Encoder[ExecutionState] = Encoder.encodeString.contramap {
    case ExecutionState.Pending   => "pending"
    case ExecutionState.Running   => "running"
    case ExecutionState.Completed => "completed"
    case ExecutionState.Failed    => "failed"
  }

  given Decoder[ExecutionState] = Decoder.decodeString.emap {
    case "pending"   => Right(ExecutionState.Pending)
    case "running"   => Right(ExecutionState.Running)
    case "completed" => Right(ExecutionState.Completed)
    case "failed"    => Right(ExecutionState.Failed)
    case other       => Left(s"Unknown execution state: $other")
  }

  given Encoder[DagExecutionUpdateParams] = deriveEncoder
  given Decoder[DagExecutionUpdateParams] = deriveDecoder

  given Encoder[DagExecutionBatchUpdateParams] = deriveEncoder
  given Decoder[DagExecutionBatchUpdateParams] = deriveDecoder

  // ========== Custom: Step-through Execution ==========

  /** Start step-through execution - returns session ID and initial state */
  case class StepStartParams(
      uri: String,
      inputs: Map[String, Json]
  )

  case class StepStartResult(
      success: Boolean,
      sessionId: Option[String],
      totalBatches: Option[Int],
      initialState: Option[StepState],
      error: Option[String]
  )

  /** Execute next batch */
  case class StepNextParams(
      sessionId: String
  )

  case class StepNextResult(
      success: Boolean,
      state: Option[StepState],
      isComplete: Boolean,
      error: Option[String]
  )

  /** Continue to completion */
  case class StepContinueParams(
      sessionId: String
  )

  case class StepContinueResult(
      success: Boolean,
      state: Option[StepState],
      outputs: Option[Map[String, Json]],
      executionTimeMs: Option[Long],
      error: Option[String]
  )

  /** Abort execution */
  case class StepStopParams(
      sessionId: String
  )

  case class StepStopResult(
      success: Boolean
  )

  /** State after each step */
  case class StepState(
      currentBatch: Int,
      totalBatches: Int,
      batchNodes: List[String], // Node IDs in current batch
      completedNodes: List[CompletedNode],
      pendingNodes: List[String] // Node IDs still pending
  )

  /** Information about a completed node */
  case class CompletedNode(
      nodeId: String,
      nodeName: String,
      nodeType: String, // "module" or "data"
      valuePreview: String,
      durationMs: Option[Long]
  )

  // JSON encoders/decoders for step-through execution

  given Encoder[StepStartParams] = deriveEncoder
  given Decoder[StepStartParams] = deriveDecoder

  given Encoder[StepStartResult] = deriveEncoder
  given Decoder[StepStartResult] = deriveDecoder

  given Encoder[StepNextParams] = deriveEncoder
  given Decoder[StepNextParams] = deriveDecoder

  given Encoder[StepNextResult] = deriveEncoder
  given Decoder[StepNextResult] = deriveDecoder

  given Encoder[StepContinueParams] = deriveEncoder
  given Decoder[StepContinueParams] = deriveDecoder

  given Encoder[StepContinueResult] = deriveEncoder
  given Decoder[StepContinueResult] = deriveDecoder

  given Encoder[StepStopParams] = deriveEncoder
  given Decoder[StepStopParams] = deriveDecoder

  given Encoder[StepStopResult] = deriveEncoder
  given Decoder[StepStopResult] = deriveDecoder

  given Encoder[StepState] = deriveEncoder
  given Decoder[StepState] = deriveDecoder

  given Encoder[CompletedNode] = deriveEncoder
  given Decoder[CompletedNode] = deriveDecoder
}
