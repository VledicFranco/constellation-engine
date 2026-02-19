package io.constellation.http

import java.time.Instant

import io.circe.generic.semiauto.*
import io.circe.syntax.*
import io.circe.{Encoder, Json}

/** Stream lifecycle events sent over WebSocket */
sealed trait StreamEvent {
  def eventType: String
  def streamId: String
  def timestamp: Long
}

object StreamEvent {

  case class StreamDeployed(
      streamId: String,
      streamName: String,
      timestamp: Long = System.currentTimeMillis()
  ) extends StreamEvent {
    val eventType = "stream:deployed"
  }

  case class StreamStopped(
      streamId: String,
      streamName: String,
      timestamp: Long = System.currentTimeMillis()
  ) extends StreamEvent {
    val eventType = "stream:stopped"
  }

  case class StreamFailed(
      streamId: String,
      streamName: String,
      error: String,
      timestamp: Long = System.currentTimeMillis()
  ) extends StreamEvent {
    val eventType = "stream:failed"
  }

  case class StreamMetricsUpdate(
      streamId: String,
      totalElements: Long,
      totalErrors: Long,
      totalDlq: Long,
      timestamp: Long = System.currentTimeMillis()
  ) extends StreamEvent {
    val eventType = "stream:metrics"
  }

  // JSON encoders
  given Encoder[StreamDeployed]      = deriveEncoder
  given Encoder[StreamStopped]       = deriveEncoder
  given Encoder[StreamFailed]        = deriveEncoder
  given Encoder[StreamMetricsUpdate] = deriveEncoder

  given Encoder[StreamEvent] = Encoder.instance {
    case e: StreamDeployed =>
      e.asJson.deepMerge(Json.obj("type" -> Json.fromString(e.eventType)))
    case e: StreamStopped =>
      e.asJson.deepMerge(Json.obj("type" -> Json.fromString(e.eventType)))
    case e: StreamFailed =>
      e.asJson.deepMerge(Json.obj("type" -> Json.fromString(e.eventType)))
    case e: StreamMetricsUpdate =>
      e.asJson.deepMerge(Json.obj("type" -> Json.fromString(e.eventType)))
  }
}
