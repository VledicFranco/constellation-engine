package io.constellation.http

import cats.effect.{IO, Ref}
import cats.effect.std.Queue
import fs2.{Pipe, Stream}
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import io.circe.parser._
import io.circe.syntax._
import io.constellation.Constellation
import io.constellation.lang.LangCompiler
import io.constellation.lsp.ConstellationLanguageServer
import io.constellation.lsp.protocol.JsonRpc._
import io.constellation.lsp.protocol.LspMessages._

/** WebSocket handler for Language Server Protocol support
  *
  * Provides a WebSocket endpoint at /lsp that implements the Language Server Protocol
  * for constellation-lang, enabling IDE integration with features like:
  * - Autocomplete (module names, keywords)
  * - Diagnostics (compilation errors)
  * - Hover information (module documentation)
  * - Custom commands (execute pipelines)
  */
class LspWebSocketHandler(
  constellation: Constellation,
  compiler: LangCompiler
) {

  def routes(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "lsp" =>
      Queue.unbounded[IO, String].flatMap { clientMessages =>
        Queue.unbounded[IO, String].flatMap { serverMessages =>
          ConstellationLanguageServer.create(
            constellation,
            compiler,
            publishDiagnostics = { params =>
              val notification = Notification(
                method = "textDocument/publishDiagnostics",
                params = Some(params.asJson)
              )
              serverMessages.offer(notification.asJson.noSpaces)
            }
          ).flatMap { languageServer =>
            // Process incoming messages from client
            val processClientMessages: IO[Unit] =
              Stream.fromQueueUnterminated(clientMessages)
                .evalMap { message =>
                  parseMessage(message).flatMap {
                    case Left(request) =>
                      // Handle request, send response
                      languageServer.handleRequest(request).flatMap { response =>
                        serverMessages.offer(response.asJson.noSpaces)
                      }
                    case Right(notification) =>
                      // Handle notification, no response
                      languageServer.handleNotification(notification)
                  }.handleErrorWith { error =>
                    // Log error and continue
                    IO.println(s"Error processing message: ${error.getMessage}")
                  }
                }
                .compile
                .drain

            // Start processing in background
            processClientMessages.start.flatMap { _ =>
              // WebSocket message handlers
              val toClient: Stream[IO, WebSocketFrame] =
                Stream.fromQueueUnterminated(serverMessages)
                  .map(msg => WebSocketFrame.Text(msg))

              val fromClient: Pipe[IO, WebSocketFrame, Unit] = { stream =>
                stream.evalMap {
                  case WebSocketFrame.Text(message, _) =>
                    clientMessages.offer(message)
                  case WebSocketFrame.Close(_) =>
                    IO.unit
                  case _ =>
                    IO.unit
                }
              }

              wsb.build(toClient, fromClient)
            }
          }
        }
      }
  }

  /** Parse incoming message as either Request or Notification */
  private def parseMessage(message: String): IO[Either[Request, Notification]] = {
    IO.fromEither(
      parse(message).flatMap { json =>
        // Try to parse as Request first (has "id" field)
        json.hcursor.downField("id").as[String] match {
          case Right(_) | Left(_) if json.hcursor.downField("id").succeeded =>
            // Has "id" field, it's a request
            json.as[Request].map(Left.apply)
          case _ =>
            // No "id" field, it's a notification
            json.as[Notification].map(Right.apply)
        }
      }
    )
  }
}

object LspWebSocketHandler {
  def apply(constellation: Constellation, compiler: LangCompiler): LspWebSocketHandler =
    new LspWebSocketHandler(constellation, compiler)
}
