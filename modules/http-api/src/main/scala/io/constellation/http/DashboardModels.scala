package io.constellation.http

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto._

/** API request and response models for the Dashboard endpoints */
object DashboardModels {

  // ============================================
  // File Browser Models
  // ============================================

  /** Type of file node */
  enum FileType:
    case File
    case Directory

  object FileType:
    given Encoder[FileType] = Encoder.encodeString.contramap(_.toString.toLowerCase)
    given Decoder[FileType] = Decoder.decodeString.emap { s =>
      s.toLowerCase match {
        case "file"      => Right(FileType.File)
        case "directory" => Right(FileType.Directory)
        case other       => Left(s"Unknown FileType: $other")
      }
    }

  /** A node in the file tree */
  case class FileNode(
      name: String,
      path: String,
      fileType: FileType,
      size: Option[Long] = None,
      modifiedTime: Option[Long] = None,
      children: Option[List[FileNode]] = None
  )

  object FileNode:
    given Encoder[FileNode] = deriveEncoder
    given Decoder[FileNode] = deriveDecoder

  /** Response listing available files */
  case class FilesResponse(
      root: String,
      files: List[FileNode]
  )

  object FilesResponse:
    given Encoder[FilesResponse] = deriveEncoder
    given Decoder[FilesResponse] = deriveDecoder

  /** Input parameter information parsed from script */
  case class InputParam(
      name: String,
      paramType: String,
      required: Boolean = true,
      defaultValue: Option[Json] = None
  )

  object InputParam:
    given Encoder[InputParam] = deriveEncoder
    given Decoder[InputParam] = deriveDecoder

  /** Output parameter information parsed from script */
  case class OutputParam(
      name: String,
      paramType: String
  )

  object OutputParam:
    given Encoder[OutputParam] = deriveEncoder
    given Decoder[OutputParam] = deriveDecoder

  /** Response with file content and metadata */
  case class FileContentResponse(
      path: String,
      name: String,
      content: String,
      inputs: List[InputParam],
      outputs: List[OutputParam],
      lastModified: Option[Long] = None
  )

  object FileContentResponse:
    given Encoder[FileContentResponse] = deriveEncoder
    given Decoder[FileContentResponse] = deriveDecoder

  // ============================================
  // Execution Models
  // ============================================

  /** Request to execute a script from the dashboard */
  case class DashboardExecuteRequest(
      scriptPath: String,
      inputs: Map[String, Json],
      sampleRate: Option[Double] = None,
      source: Option[String] = None
  )

  object DashboardExecuteRequest:
    given Encoder[DashboardExecuteRequest] = deriveEncoder
    given Decoder[DashboardExecuteRequest] = deriveDecoder

  /** Response from dashboard execution */
  case class DashboardExecuteResponse(
      success: Boolean,
      executionId: String,
      outputs: Map[String, Json] = Map.empty,
      error: Option[String] = None,
      dashboardUrl: Option[String] = None,
      durationMs: Option[Long] = None
  )

  object DashboardExecuteResponse:
    given Encoder[DashboardExecuteResponse] = deriveEncoder
    given Decoder[DashboardExecuteResponse] = deriveDecoder

  /** Request to preview/compile a script without executing */
  case class PreviewRequest(
      source: String
  )

  object PreviewRequest:
    given Encoder[PreviewRequest] = deriveEncoder
    given Decoder[PreviewRequest] = deriveDecoder

  /** Response from preview compilation */
  case class PreviewResponse(
      success: Boolean,
      dagVizIR: Option[io.constellation.lang.viz.DagVizIR] = None,
      errors: List[String] = List.empty
  )

  object PreviewResponse:
    import io.constellation.lang.viz.DagVizIR
    given Encoder[PreviewResponse] = deriveEncoder
    given Decoder[PreviewResponse] = deriveDecoder

  /** Response listing executions */
  case class ExecutionListResponse(
      executions: List[ExecutionSummary],
      total: Int,
      limit: Int,
      offset: Int
  )

  object ExecutionListResponse:
    given Encoder[ExecutionListResponse] = deriveEncoder
    given Decoder[ExecutionListResponse] = deriveDecoder

  // ============================================
  // Dashboard Status Models
  // ============================================

  /** Dashboard status information */
  case class DashboardStatus(
      enabled: Boolean,
      cstDirectory: String,
      sampleRate: Double,
      maxExecutions: Int,
      storageStats: StorageStats
  )

  object DashboardStatus:
    given Encoder[DashboardStatus] = deriveEncoder
    given Decoder[DashboardStatus] = deriveDecoder

  // ============================================
  // Error Models
  // ============================================

  /** Dashboard-specific error response */
  case class DashboardError(
      error: String,
      message: String,
      path: Option[String] = None,
      details: Option[Json] = None
  )

  object DashboardError:
    given Encoder[DashboardError] = deriveEncoder
    given Decoder[DashboardError] = deriveDecoder

    def notFound(resource: String, path: String): DashboardError =
      DashboardError("not_found", s"$resource not found", Some(path))

    def invalidRequest(message: String): DashboardError =
      DashboardError("invalid_request", message)

    def executionFailed(message: String, executionId: String): DashboardError =
      DashboardError("execution_failed", message, details = Some(Json.obj("executionId" -> Json.fromString(executionId))))

    def serverError(message: String): DashboardError =
      DashboardError("server_error", message)
}
