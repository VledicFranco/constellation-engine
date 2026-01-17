package io.constellation.http

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._
import io.constellation.{ComponentMetadata, CValue}
import io.constellation.json.given

/** API request and response models for the HTTP server */
object ApiModels {

  // Explicit encoders/decoders for ComponentMetadata
  given Encoder[ComponentMetadata] = deriveEncoder
  given Decoder[ComponentMetadata] = deriveDecoder

  /** Request to compile constellation-lang source code */
  case class CompileRequest(
    source: String,
    dagName: String
  )

  object CompileRequest {
    given Encoder[CompileRequest] = deriveEncoder
    given Decoder[CompileRequest] = deriveDecoder
  }

  /** Response from compilation */
  case class CompileResponse(
    success: Boolean,
    dagName: Option[String] = None,
    errors: List[String] = List.empty
  )

  object CompileResponse {
    given Encoder[CompileResponse] = deriveEncoder
    given Decoder[CompileResponse] = deriveDecoder
  }

  /** Request to execute a DAG */
  case class ExecuteRequest(
    dagName: String,
    inputs: Map[String, Json]
  )

  object ExecuteRequest {
    given Encoder[ExecuteRequest] = deriveEncoder
    given Decoder[ExecuteRequest] = deriveDecoder
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

  /** Response from compile-and-run */
  case class RunResponse(
    success: Boolean,
    outputs: Map[String, Json] = Map.empty,
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
}
