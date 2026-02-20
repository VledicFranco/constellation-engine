package io.constellation.stream.connector.websocket

import java.net.URI

import scala.concurrent.duration.*

import cats.effect.IO

import io.constellation.CValue
import io.constellation.json.cvalueDecoder
import io.constellation.stream.connector.*

import fs2.Stream
import io.circe.parser.parse as parseJson

/** A source connector that reads from a WebSocket endpoint.
  *
  * Connects as a WebSocket client, receives text frames, and parses them as JSON CValues (falling
  * back to CString for non-JSON text). Supports configurable reconnection on disconnect.
  */
class WebSocketSourceConnector(connectorName: String) extends SourceConnector {

  override def name: String     = connectorName
  override def typeName: String = "websocket"

  override def configSchema: ConnectorSchema = WebSocketSourceConnector.schema

  override def stream(config: ValidatedConnectorConfig): Stream[IO, CValue] = {
    val uri           = URI.create(config.getString("uri").get)
    val reconnect     = config.getStringOrDefault("reconnect", "true") == "true"
    val delay         = config.getDuration("reconnect_delay").getOrElse(1.second)
    val maxReconnects = config.getInt("max_reconnects").getOrElse(10)

    def connectStream: Stream[IO, CValue] =
      Stream.resource(JdkWebSocketWrapper.connect(uri)).flatMap { case (incoming, _) =>
        incoming.map(parseTextToCValue)
      }

    if reconnect then withReconnect(connectStream, delay, maxReconnects)
    else connectStream
  }

  private def withReconnect(
      base: Stream[IO, CValue],
      delay: FiniteDuration,
      maxReconnects: Int
  ): Stream[IO, CValue] = {
    def go(remaining: Int): Stream[IO, CValue] =
      base.handleErrorWith { _ =>
        if remaining <= 0 then Stream.empty
        else Stream.exec(IO.sleep(delay)) ++ go(remaining - 1)
      }
    go(maxReconnects)
  }

  private def parseTextToCValue(text: String): CValue =
    parseJson(text).flatMap(_.as[CValue](cvalueDecoder)) match {
      case Right(cvalue) => cvalue
      case Left(_)       => CValue.CString(text)
    }
}

object WebSocketSourceConnector {
  val schema: ConnectorSchema = ConnectorSchema(
    required = Map(
      "uri" -> PropertyType.StringProp()
    ),
    optional = Map(
      "reconnect"       -> PropertyType.EnumProp(Set("true", "false"), default = Some("true")),
      "reconnect_delay" -> PropertyType.DurationProp(default = Some(1.second)),
      "max_reconnects"  -> PropertyType.IntProp(default = Some(10), min = 0, max = 1000)
    ),
    description = "WebSocket source connector using JDK 11 native WebSocket client"
  )
}
