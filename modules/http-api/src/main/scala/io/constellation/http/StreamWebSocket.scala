package io.constellation.http

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import scala.jdk.CollectionConverters.*

import cats.effect.IO
import cats.effect.std.Queue
import cats.implicits.*

import fs2.{Pipe, Stream}
import io.circe.syntax.*
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** Subscription info for a connected stream event client */
case class StreamSubscription(
    clientId: String,
    streamId: Option[String],
    queue: Queue[IO, StreamEvent]
)

/** WebSocket handler for streaming pipeline lifecycle events.
  *
  * Follows the same pattern as `ExecutionWebSocket` — publishes stream events to connected clients
  * that subscribe via `/api/v1/streams/events`.
  */
class StreamWebSocket {
  private val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](classOf[StreamWebSocket])

  private val subscriptions: ConcurrentHashMap[String, StreamSubscription] =
    new ConcurrentHashMap()

  private val maxQueueSize = 100

  /** Publish a stream event to all matching subscribers */
  def publish(event: StreamEvent): IO[Unit] = {
    val subscribers = subscriptions.values().asScala.toList.filter { sub =>
      sub.streamId.isEmpty || sub.streamId.contains(event.streamId)
    }

    subscribers.traverse_ { sub =>
      sub.queue.tryOffer(event).flatMap {
        case true  => IO.unit
        case false => logger.warn(s"Dropped stream event for client ${sub.clientId} (queue full)")
      }
    }
  }

  /** Subscribe a new client */
  private def subscribe(
      clientId: String,
      streamId: Option[String]
  ): IO[Queue[IO, StreamEvent]] =
    for {
      queue <- Queue.bounded[IO, StreamEvent](maxQueueSize)
      sub = StreamSubscription(clientId, streamId, queue)
      _ <- IO(subscriptions.put(clientId, sub))
      _ <- logger.debug(
        s"Stream client $clientId subscribed to stream: ${streamId.getOrElse("all")}"
      )
    } yield queue

  /** Unsubscribe a client */
  private def unsubscribe(clientId: String): IO[Unit] =
    for {
      _ <- IO(subscriptions.remove(clientId))
      _ <- logger.debug(s"Stream client $clientId unsubscribed")
    } yield ()

  /** Get count of active subscriptions (for testing/monitoring) */
  def subscriptionCount: IO[Int] = IO(subscriptions.size())

  private object StreamIdParam extends OptionalQueryParamDecoderMatcher[String]("streamId")

  /** HTTP routes for WebSocket connections */
  def routes(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "api" / "v1" / "streams" / "events" :? StreamIdParam(streamIdOpt) =>
      val clientId = UUID.randomUUID().toString

      for {
        queue <- subscribe(clientId, streamIdOpt)

        toClient: Stream[IO, WebSocketFrame] = Stream
          .fromQueueUnterminated(queue)
          .map(event => WebSocketFrame.Text(event.asJson.noSpaces))

        fromClient: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
          case WebSocketFrame.Text("ping", _) => IO.unit
          case WebSocketFrame.Close(_)        => unsubscribe(clientId)
          case _                              => IO.unit
        }

        response <- wsb
          .withOnClose(unsubscribe(clientId))
          .build(toClient, fromClient)
      } yield response
  }
}

object StreamWebSocket {
  def apply(): StreamWebSocket = new StreamWebSocket()
}
