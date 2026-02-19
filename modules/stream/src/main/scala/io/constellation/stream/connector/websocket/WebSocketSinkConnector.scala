package io.constellation.stream.connector.websocket

import java.net.URI

import cats.effect.IO

import fs2.Pipe

import io.circe.syntax.*

import io.constellation.CValue
import io.constellation.json.cvalueEncoder
import io.constellation.stream.connector.*

/** A sink connector that writes CValues as JSON text frames to a WebSocket endpoint.
  *
  * Connects as a WebSocket client and serializes each CValue to JSON before sending.
  */
class WebSocketSinkConnector(connectorName: String) extends SinkConnector {

  override def name: String     = connectorName
  override def typeName: String = "websocket"

  override def configSchema: ConnectorSchema = WebSocketSinkConnector.schema

  override def pipe(config: ValidatedConnectorConfig): Pipe[IO, CValue, Unit] = { input =>
    val uri = URI.create(config.getString("uri").get)

    fs2.Stream.resource(JdkWebSocketWrapper.connect(uri)).flatMap { case (_, send) =>
      input.evalMap { value =>
        val json = value.asJson(cvalueEncoder).noSpaces
        send(json)
      }
    }
  }
}

object WebSocketSinkConnector {
  val schema: ConnectorSchema = ConnectorSchema(
    required = Map(
      "uri" -> PropertyType.StringProp()
    ),
    description = "WebSocket sink connector using JDK 11 native WebSocket client"
  )
}
