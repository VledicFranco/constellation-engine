package io.constellation.stream.connector.websocket

import java.net.URI
import java.net.http.{HttpClient, WebSocket}
import java.util.concurrent.CompletionStage

import cats.effect.{IO, Resource}
import cats.effect.std.Queue

import fs2.Stream

/** Internal wrapper around the JDK 11 WebSocket API, bridging to cats-effect IO and fs2 Stream.
  *
  * Provides a Resource that connects a WebSocket and returns:
  *   - A `Stream[IO, String]` of incoming text messages
  *   - A `String => IO[Unit]` function to send text messages
  *
  * The WebSocket is closed when the Resource is released.
  */
private[websocket] object JdkWebSocketWrapper {

  /** Connect to a WebSocket endpoint.
    *
    * @param uri
    *   The WebSocket URI to connect to
    * @param bufferSize
    *   Size of the internal message queue
    * @return
    *   A Resource yielding (incoming messages stream, send function)
    */
  def connect(
      uri: URI,
      bufferSize: Int = 256
  ): Resource[IO, (Stream[IO, String], String => IO[Unit])] =
    Resource.make(
      for {
        queue <- Queue.bounded[IO, Option[String]](bufferSize)
        ws    <- openWebSocket(uri, queue)
      } yield (queue, ws)
    ) { case (queue, ws) =>
      IO.fromCompletableFuture(
        IO.delay(ws.sendClose(WebSocket.NORMAL_CLOSURE, "closing").toCompletableFuture)
      ).void.handleError(_ => ()) *> queue.offer(None)
    }.map { case (queue, ws) =>
      val incoming = Stream.fromQueueNoneTerminated(queue)
      val send: String => IO[Unit] = msg =>
        IO.fromCompletableFuture(
          IO.delay(ws.sendText(msg, true).toCompletableFuture)
        ).void
      (incoming, send)
    }

  private def openWebSocket(
      uri: URI,
      queue: Queue[IO, Option[String]]
  ): IO[WebSocket] = {
    val client = HttpClient.newHttpClient()
    val listener = new WebSocket.Listener {
      private val buffer = new StringBuilder

      override def onText(
          webSocket: WebSocket,
          data: CharSequence,
          last: Boolean
      ): CompletionStage[?] = {
        buffer.append(data)
        if (last) {
          val message = buffer.toString()
          buffer.clear()
          // Offer message to queue, request next message after enqueue
          queue.offer(Some(message)).unsafeRunAndForget()(
            cats.effect.unsafe.IORuntime.global
          )
        }
        webSocket.request(1)
        null // null means "I'll handle backpressure myself"
      }

      override def onClose(
          webSocket: WebSocket,
          statusCode: Int,
          reason: String
      ): CompletionStage[?] = {
        queue.offer(None).unsafeRunAndForget()(
          cats.effect.unsafe.IORuntime.global
        )
        null
      }

      override def onError(webSocket: WebSocket, error: Throwable): Unit =
        queue.offer(None).unsafeRunAndForget()(
          cats.effect.unsafe.IORuntime.global
        )
    }

    IO.fromCompletableFuture(
      IO.delay(
        client.newWebSocketBuilder().buildAsync(uri, listener).toCompletableFuture
      )
    )
  }
}
