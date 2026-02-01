package io.constellation.http

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*
import io.constellation.{CValue, ComponentMetadata}
import io.constellation.json.given

/** API request and response models for the HTTP server */
object ApiModels {

  // Explicit encoders/decoders for ComponentMetadata
  given Encoder[ComponentMetadata] = deriveEncoder
  given Decoder[ComponentMetadata] = deriveDecoder

  /** Request to compile constellation-lang source code.
    *
    * Accepts either `name` (new API) or `dagName` (legacy, deprecated).
    * If both are present, `name` takes precedence. At least one must be provided.
    */
  case class CompileRequest(
      source: String,
      name: Option[String] = None,
      dagName: Option[String] = None
  ) {
    /** Resolve the effective program name (new `name` field takes priority). */
    def effectiveName: Option[String] = name.orElse(dagName)
  }

  object CompileRequest {
    given Encoder[CompileRequest] = deriveEncoder
    given Decoder[CompileRequest] = Decoder.instance { c =>
      for {
        source  <- c.downField("source").as[String]
        name    <- c.downField("name").as[Option[String]]
        dagName <- c.downField("dagName").as[Option[String]]
      } yield CompileRequest(source, name, dagName)
    }
  }

  /** Response from compilation.
    *
    * Now includes `structuralHash` and `syntacticHash` for content-addressed lookups.
    * The `dagName` field is retained for backward compatibility.
    */
  case class CompileResponse(
      success: Boolean,
      structuralHash: Option[String] = None,
      syntacticHash: Option[String] = None,
      dagName: Option[String] = None,
      name: Option[String] = None,
      errors: List[String] = List.empty
  )

  object CompileResponse {
    given Encoder[CompileResponse] = deriveEncoder
    given Decoder[CompileResponse] = deriveDecoder
  }

  /** Request to execute a program.
    *
    * Accepts `ref` (new API: name or "sha256:<hash>") or `dagName` (legacy).
    * If both are present, `ref` takes precedence.
    */
  case class ExecuteRequest(
      ref: Option[String] = None,
      dagName: Option[String] = None,
      inputs: Map[String, Json] = Map.empty
  ) {
    /** Resolve the effective reference (new `ref` field takes priority). */
    def effectiveRef: Option[String] = ref.orElse(dagName)
  }

  object ExecuteRequest {
    given Encoder[ExecuteRequest] = deriveEncoder
    given Decoder[ExecuteRequest] = Decoder.instance { c =>
      for {
        ref     <- c.downField("ref").as[Option[String]]
        dagName <- c.downField("dagName").as[Option[String]]
        inputs  <- c.downField("inputs").as[Map[String, Json]]
      } yield ExecuteRequest(ref, dagName, inputs)
    }
  }

  /** Response from DAG execution */
  case class ExecuteResponse(
      success: Boolean,
      outputs: Map[String, Json] = Map.empty,
      error: Option[String] = None
  )

  object ExecuteResponse {
    given Encoder[ExecuteResponse] = deriveEncoder
    given Decoder[ExecuteResponse] = deriveDecoder
  }

  /** Request to compile and run a script in one step */
  case class RunRequest(
      source: String,
      inputs: Map[String, Json]
  )

  object RunRequest {
    given Encoder[RunRequest] = deriveEncoder
    given Decoder[RunRequest] = deriveDecoder
  }

  /** Response from compile-and-run.
    *
    * Now includes `structuralHash` so callers can reference the program by hash.
    */
  case class RunResponse(
      success: Boolean,
      outputs: Map[String, Json] = Map.empty,
      structuralHash: Option[String] = None,
      compilationErrors: List[String] = List.empty,
      error: Option[String] = None
  )

  object RunResponse {
    given Encoder[RunResponse] = deriveEncoder
    given Decoder[RunResponse] = deriveDecoder
  }

  /** Response listing available DAGs */
  case class DagListResponse(
      dags: Map[String, ComponentMetadata]
  )

  object DagListResponse {
    given Encoder[DagListResponse] = deriveEncoder
    given Decoder[DagListResponse] = deriveDecoder
  }

  /** Response with a single DAG specification */
  case class DagResponse(
      name: String,
      metadata: ComponentMetadata
  )

  object DagResponse {
    given Encoder[DagResponse] = deriveEncoder
    given Decoder[DagResponse] = deriveDecoder
  }

  /** Response listing available modules */
  case class ModuleListResponse(
      modules: List[ModuleInfo]
  )

  object ModuleListResponse {
    given Encoder[ModuleListResponse] = deriveEncoder
    given Decoder[ModuleListResponse] = deriveDecoder
  }

  /** Module information */
  case class ModuleInfo(
      name: String,
      description: String,
      version: String,
      inputs: Map[String, String],
      outputs: Map[String, String]
  )

  object ModuleInfo {
    given Encoder[ModuleInfo] = deriveEncoder
    given Decoder[ModuleInfo] = deriveDecoder
  }

  /** Error response */
  case class ErrorResponse(
      error: String,
      message: String
  )

  object ErrorResponse {
    given Encoder[ErrorResponse] = deriveEncoder
    given Decoder[ErrorResponse] = deriveDecoder
  }

  /** Response listing available namespaces */
  case class NamespaceListResponse(
      namespaces: List[String]
  )

  object NamespaceListResponse {
    given Encoder[NamespaceListResponse] = deriveEncoder
    given Decoder[NamespaceListResponse] = deriveDecoder
  }

  /** Function information for namespace listing */
  case class FunctionInfo(
      name: String,
      qualifiedName: String,
      params: List[String],
      returns: String
  )

  object FunctionInfo {
    given Encoder[FunctionInfo] = deriveEncoder
    given Decoder[FunctionInfo] = deriveDecoder
  }

  /** Response listing functions in a namespace */
  case class NamespaceFunctionsResponse(
      namespace: String,
      functions: List[FunctionInfo]
  )

  object NamespaceFunctionsResponse {
    given Encoder[NamespaceFunctionsResponse] = deriveEncoder
    given Decoder[NamespaceFunctionsResponse] = deriveDecoder
  }

  // ---------------------------------------------------------------------------
  // Program management models (Phase 5)
  // ---------------------------------------------------------------------------

  /** Summary of a stored program image */
  case class ProgramSummary(
      structuralHash: String,
      syntacticHash: String,
      aliases: List[String],
      compiledAt: String,
      moduleCount: Int,
      declaredOutputs: List[String]
  )

  object ProgramSummary {
    given Encoder[ProgramSummary] = deriveEncoder
    given Decoder[ProgramSummary] = deriveDecoder
  }

  /** Response listing stored programs */
  case class ProgramListResponse(
      programs: List[ProgramSummary]
  )

  object ProgramListResponse {
    given Encoder[ProgramListResponse] = deriveEncoder
    given Decoder[ProgramListResponse] = deriveDecoder
  }

  /** Detailed program metadata response */
  case class ProgramDetailResponse(
      structuralHash: String,
      syntacticHash: String,
      aliases: List[String],
      compiledAt: String,
      modules: List[ModuleInfo],
      declaredOutputs: List[String],
      inputSchema: Map[String, String],
      outputSchema: Map[String, String]
  )

  object ProgramDetailResponse {
    given Encoder[ProgramDetailResponse] = deriveEncoder
    given Decoder[ProgramDetailResponse] = deriveDecoder
  }

  /** Request to update an alias target */
  case class AliasRequest(
      structuralHash: String
  )

  object AliasRequest {
    given Encoder[AliasRequest] = deriveEncoder
    given Decoder[AliasRequest] = deriveDecoder
  }
}
