package io.constellation.stream.connector.sse

import java.net.URI

import scala.concurrent.duration.*

import cats.effect.{IO, Ref}

import io.constellation.CValue
import io.constellation.json.cvalueDecoder
import io.constellation.stream.connector.*

import fs2.Stream
import io.circe.parser.parse as parseJson
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.typelevel.ci.CIString

/** A source connector that reads from an HTTP Server-Sent Events endpoint.
  *
  * Makes a GET request with `Accept: text/event-stream`, parses the SSE protocol, and emits each
  * event's data as a CValue (JSON parsed if possible, otherwise CString). Tracks the last event ID
  * for reconnection with `Last-Event-ID` header.
  */
class HttpSseSourceConnector(connectorName: String) extends SourceConnector {

  override def name: String     = connectorName
  override def typeName: String = "http-sse"

  override def configSchema: ConnectorSchema = HttpSseSourceConnector.schema

  override def stream(config: ValidatedConnectorConfig): Stream[IO, CValue] = {
    val url         = config.getString("url").get
    val reconnect   = config.getStringOrDefault("reconnect", "true") == "true"
    val delay       = config.getDuration("reconnect_delay").getOrElse(3.seconds)
    val lastEventId = config.getString("last_event_id")
    val headerPairs = config.getString("headers").map(parseHeaders).getOrElse(Nil)

    val uri = Uri.unsafeFromString(url)

    Stream.eval(Ref.of[IO, Option[String]](lastEventId)).flatMap { lastIdRef =>
      def connectStream: Stream[IO, CValue] =
        Stream.resource(EmberClientBuilder.default[IO].build).flatMap { client =>
          Stream.eval(lastIdRef.get).flatMap { currentLastId =>
            val baseHeaders = Headers(
              Header.Raw(CIString("Accept"), "text/event-stream")
            )
            val withLastId = currentLastId.fold(baseHeaders) { id =>
              baseHeaders.put(Header.Raw(CIString("Last-Event-ID"), id))
            }
            val customHeaders = headerPairs.foldLeft(withLastId) { case (h, (k, v)) =>
              h.put(Header.Raw(CIString(k), v))
            }
            val request = Request[IO](Method.GET, uri, headers = customHeaders)

            Stream
              .eval(client.run(request).use { response =>
                response.body
                  .through(SseParser.parse)
                  .evalTap { event =>
                    event.id.fold(IO.unit)(id => lastIdRef.set(Some(id)))
                  }
                  .map(event => parseDataToCValue(event.data))
                  .compile
                  .toList
              })
              .flatMap(Stream.emits)
          }
        }

      if reconnect then withReconnect(connectStream, delay)
      else connectStream
    }
  }

  private def withReconnect(
      base: Stream[IO, CValue],
      delay: FiniteDuration
  ): Stream[IO, CValue] =
    base.handleErrorWith { _ =>
      Stream.exec(IO.sleep(delay)) ++ withReconnect(base, delay)
    }

  private def parseDataToCValue(data: String): CValue =
    parseJson(data).flatMap(_.as[CValue](cvalueDecoder)) match {
      case Right(cvalue) => cvalue
      case Left(_)       => CValue.CString(data)
    }

  private def parseHeaders(headerStr: String): List[(String, String)] =
    headerStr.split(',').toList.flatMap { pair =>
      pair.indexOf('=') match {
        case -1  => None
        case idx => Some((pair.substring(0, idx).trim, pair.substring(idx + 1).trim))
      }
    }
}

object HttpSseSourceConnector {
  val schema: ConnectorSchema = ConnectorSchema(
    required = Map(
      "url" -> PropertyType.StringProp()
    ),
    optional = Map(
      "reconnect"       -> PropertyType.EnumProp(Set("true", "false"), default = Some("true")),
      "reconnect_delay" -> PropertyType.DurationProp(default = Some(3.seconds)),
      "last_event_id"   -> PropertyType.StringProp(),
      "headers"         -> PropertyType.StringProp()
    ),
    description = "HTTP Server-Sent Events source connector using http4s-ember-client"
  )
}
