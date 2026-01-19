package io.constellation.lsp.protocol

import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.syntax.*

/** JSON-RPC 2.0 protocol types for LSP communication */
object JsonRpc {

  /** JSON-RPC request from client to server */
  case class Request(
      jsonrpc: String = "2.0",
      id: RequestId,
      method: String,
      params: Option[Json]
  )

  /** JSON-RPC response from server to client */
  case class Response(
      jsonrpc: String = "2.0",
      id: RequestId,
      result: Option[Json] = None,
      error: Option[ResponseError] = None
  )

  /** JSON-RPC notification (no response expected) */
  case class Notification(
      jsonrpc: String = "2.0",
      method: String,
      params: Option[Json]
  )

  /** Request identifier (can be string, number, or null) */
  enum RequestId:
    case StringId(value: String)
    case NumberId(value: Long)
    case NullId

  /** Error object for responses */
  case class ResponseError(
      code: Int,
      message: String,
      data: Option[Json] = None
  )

  /** Standard JSON-RPC error codes */
  object ErrorCodes {
    val ParseError     = -32700
    val InvalidRequest = -32600
    val MethodNotFound = -32601
    val InvalidParams  = -32602
    val InternalError  = -32603

    // LSP-specific error codes
    val ServerNotInitialized = -32002
    val UnknownErrorCode     = -32001
    val RequestCancelled     = -32800
    val ContentModified      = -32801
  }

  // JSON encoders/decoders

  given Encoder[RequestId] = {
    case RequestId.StringId(value) => Json.fromString(value)
    case RequestId.NumberId(value) => Json.fromLong(value)
    case RequestId.NullId          => Json.Null
  }

  given Decoder[RequestId] = Decoder.instance { cursor =>
    cursor.value match {
      case json if json.isString => cursor.as[String].map(RequestId.StringId.apply)
      case json if json.isNumber => cursor.as[Long].map(RequestId.NumberId.apply)
      case json if json.isNull   => Right(RequestId.NullId)
      case _ => Left(DecodingFailure("RequestId must be string, number, or null", cursor.history))
    }
  }

  given Encoder[ResponseError] = deriveEncoder
  given Decoder[ResponseError] = deriveDecoder

  given Encoder[Request] = deriveEncoder
  given Decoder[Request] = deriveDecoder

  /** Custom encoder for Response that follows JSON-RPC 2.0 spec:
    *   - On success: include result, omit error
    *   - On error: include error, omit result
    */
  given Encoder[Response] = Encoder.instance { response =>
    val baseFields = List(
      "jsonrpc" -> Json.fromString(response.jsonrpc),
      "id"      -> response.id.asJson
    )

    val responseFields = response.error match {
      case Some(err) =>
        // Error response: include error, omit result
        baseFields :+ ("error" -> err.asJson)
      case None =>
        // Success response: include result (even if null), omit error
        baseFields :+ ("result" -> response.result.getOrElse(Json.Null))
    }

    Json.obj(responseFields*)
  }

  given Decoder[Response] = deriveDecoder

  given Encoder[Notification] = deriveEncoder
  given Decoder[Notification] = deriveDecoder
}
