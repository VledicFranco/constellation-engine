package io.constellation.http

import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}

/** API request and response models for the streaming pipeline endpoints */
object StreamApiModels {

  // ===== Request Models =====

  /** Request to deploy a new streaming pipeline */
  case class StreamDeployRequest(
      name: String,
      pipelineRef: String,
      sourceBindings: Map[String, SourceBindingRequest] = Map.empty,
      sinkBindings: Map[String, SinkBindingRequest] = Map.empty,
      options: Option[StreamOptionsRequest] = None
  )

  object StreamDeployRequest {
    given Encoder[StreamDeployRequest] = deriveEncoder
    given Decoder[StreamDeployRequest] = deriveDecoder
  }

  /** Source connector binding in a deploy request */
  case class SourceBindingRequest(
      connectorType: String,
      properties: Map[String, String] = Map.empty
  )

  object SourceBindingRequest {
    given Encoder[SourceBindingRequest] = deriveEncoder
    given Decoder[SourceBindingRequest] = deriveDecoder
  }

  /** Sink connector binding in a deploy request */
  case class SinkBindingRequest(
      connectorType: String,
      properties: Map[String, String] = Map.empty
  )

  object SinkBindingRequest {
    given Encoder[SinkBindingRequest] = deriveEncoder
    given Decoder[SinkBindingRequest] = deriveDecoder
  }

  /** Optional streaming options in a deploy request */
  case class StreamOptionsRequest(
      errorStrategy: Option[String] = None,
      joinStrategy: Option[String] = None,
      parallelism: Option[Int] = None,
      metricsEnabled: Option[Boolean] = None
  )

  object StreamOptionsRequest {
    given Encoder[StreamOptionsRequest] = deriveEncoder
    given Decoder[StreamOptionsRequest] = deriveDecoder
  }

  // ===== Response Models =====

  /** Response for a single stream's info */
  case class StreamInfoResponse(
      id: String,
      name: String,
      status: String,
      startedAt: String,
      metrics: Option[StreamMetricsSummary] = None
  )

  object StreamInfoResponse {
    given Encoder[StreamInfoResponse] = deriveEncoder
    given Decoder[StreamInfoResponse] = deriveDecoder
  }

  /** Aggregated metrics for a stream */
  case class StreamMetricsSummary(
      totalElements: Long,
      totalErrors: Long,
      totalDlq: Long,
      perModule: Map[String, ModuleMetrics] = Map.empty
  )

  object StreamMetricsSummary {
    given Encoder[StreamMetricsSummary] = deriveEncoder
    given Decoder[StreamMetricsSummary] = deriveDecoder
  }

  /** Per-module metrics */
  case class ModuleMetrics(
      elements: Long,
      errors: Long,
      dlq: Long
  )

  object ModuleMetrics {
    given Encoder[ModuleMetrics] = deriveEncoder
    given Decoder[ModuleMetrics] = deriveDecoder
  }

  /** Response listing all streams */
  case class StreamListResponse(
      streams: List[StreamInfoResponse]
  )

  object StreamListResponse {
    given Encoder[StreamListResponse] = deriveEncoder
    given Decoder[StreamListResponse] = deriveDecoder
  }

  /** Information about an available connector */
  case class ConnectorInfoResponse(
      name: String,
      typeName: String,
      kind: String,
      schema: Option[ConnectorSchemaResponse] = None
  )

  object ConnectorInfoResponse {
    given Encoder[ConnectorInfoResponse] = deriveEncoder
    given Decoder[ConnectorInfoResponse] = deriveDecoder
  }

  /** Schema for a connector's configuration */
  case class ConnectorSchemaResponse(
      required: Map[String, String] = Map.empty,
      optional: Map[String, String] = Map.empty
  )

  object ConnectorSchemaResponse {
    given Encoder[ConnectorSchemaResponse] = deriveEncoder
    given Decoder[ConnectorSchemaResponse] = deriveDecoder
  }

  /** Response listing all available connectors */
  case class ConnectorListResponse(
      connectors: List[ConnectorInfoResponse]
  )

  object ConnectorListResponse {
    given Encoder[ConnectorListResponse] = deriveEncoder
    given Decoder[ConnectorListResponse] = deriveDecoder
  }
}
