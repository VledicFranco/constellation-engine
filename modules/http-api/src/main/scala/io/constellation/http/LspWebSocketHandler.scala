package io.constellation.http

import cats.effect.{IO, Ref}
import cats.effect.std.Queue
import cats.implicits._
import fs2.{Pipe, Stream}
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import io.circe.parser.*
import io.circe.syntax.*
import io.constellation.Constellation
import io.constellation.lang.LangCompiler
import io.constellation.lsp.ConstellationLanguageServer
import io.constellation.lsp.protocol.JsonRpc.*
import io.constellation.lsp.protocol.LspMessages.*
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** WebSocket handler for Language Server Protocol support
  *
  * Provides a WebSocket endpoint at /lsp that implements the Language Server Protocol for
  * constellation-lang, enabling IDE integration with features like:
  *   - Autocomplete (module names, keywords)
  *   - Diagnostics (compilation errors)
  *   - Hover information (module documentation)
  *   - Custom commands (execute pipelines)
  */
class LspWebSocketHandler(
    constellation: Constellation,
    compiler: LangCompiler
) {
  private val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](classOf[LspWebSocketHandler])

  def routes(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "lsp" =>
      // Queue for complete LSP messages (after buffering)
      // Use bounded queues to prevent memory issues if one side can't keep up
      Queue.bounded[IO, String](100).flatMap { clientMessages =>
        Queue.bounded[IO, String](100).flatMap { serverMessages =>
          // Buffer for accumulating partial LSP messages from WebSocket frames
          Ref.of[IO, String]("").flatMap { bufferRef =>
            ConstellationLanguageServer
              .create(
                constellation,
                compiler,
                publishDiagnostics = { params =>
                  val notification = Notification(
                    method = "textDocument/publishDiagnostics",
                    params = Some(params.asJson)
                  )
                  val message = formatLspMessage(notification.asJson.noSpaces)
                  serverMessages.offer(message)
                }
              )
              .flatMap { languageServer =>
                // Process incoming messages from client
                val processClientMessages: IO[Unit] =
                  Stream
                    .fromQueueUnterminated(clientMessages)
                    .filter(msg =>
                      msg.trim.nonEmpty
                    ) // Silently ignore empty messages (keep-alive pings)
                    .evalMap { message =>
                      parseMessage(message)
                        .flatMap {
                          case Left(request) =>
                            // Handle request, send response
                            for {
                              _ <- logger.debug(s"Processing request method: ${request.method}")
                              response <- languageServer.handleRequest(request)
                              json = response.asJson.noSpaces
                              _ <- logger.debug(s"Sending response: ${json
                                  .take(200)}${if json.length > 200 then "..." else ""}")
                              message = formatLspMessage(json)
                              _ <- serverMessages.offer(message)
                            } yield ()
                          case Right(notification) =>
                            // Handle notification, no response
                            for {
                              _ <- logger
                                .debug(s"Processing notification method: ${notification.method}")
                              _ <- languageServer.handleNotification(notification)
                            } yield ()
                        }
                        .handleErrorWith { error =>
                          logger.error(s"Error processing message: ${error.getMessage}")
                        }
                    }
                    .compile
                    .drain

                // Start processing in background
                processClientMessages.start.flatMap { _ =>
                  // WebSocket message handlers
                  val toClient: Stream[IO, WebSocketFrame] =
                    Stream
                      .fromQueueUnterminated(serverMessages)
                      .map(msg => WebSocketFrame.Text(msg))

                  val fromClient: Pipe[IO, WebSocketFrame, Unit] = { stream =>
                    stream.evalMap {
                      case WebSocketFrame.Text(data, _) =>
                        // Buffer incoming data and extract complete LSP messages
                        bufferAndExtractMessages(bufferRef, data).flatMap { messages =>
                          messages.traverse_(msg => clientMessages.offer(msg))
                        }
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
  }

  /** Buffer incoming WebSocket data and extract complete LSP messages.
    * LSP messages have format: "Content-Length: XXX\r\n\r\n{json}"
    * WebSocket frames may contain partial messages or multiple messages.
    */
  private def bufferAndExtractMessages(bufferRef: Ref[IO, String], newData: String): IO[List[String]] = {
    bufferRef.modify { buffer =>
      val combined = buffer + newData
      extractCompleteLspMessages(combined, List.empty)
    }
  }

  /** Recursively extract complete LSP messages from buffer.
    * Returns (remaining buffer, list of complete messages).
    */
  @scala.annotation.tailrec
  private def extractCompleteLspMessages(buffer: String, acc: List[String]): (String, List[String]) = {
    if (buffer.isEmpty || !buffer.startsWith("Content-Length:")) {
      // If buffer doesn't start with Content-Length, it might be plain JSON (legacy)
      // or incomplete. For plain JSON, just return it as-is if it looks complete.
      if (buffer.trim.startsWith("{") && buffer.trim.endsWith("}")) {
        ("", acc :+ buffer)
      } else if (buffer.trim.isEmpty) {
        ("", acc)
      } else if (!buffer.startsWith("Content-Length:") && buffer.contains("{")) {
        // Partial message or garbage - keep buffering
        (buffer, acc)
      } else {
        (buffer, acc)
      }
    } else {
      // Find the header/body separator
      val headerEnd = buffer.indexOf("\r\n\r\n")
      if (headerEnd < 0) {
        // Header not complete yet
        (buffer, acc)
      } else {
        // Parse Content-Length
        val header = buffer.substring(0, headerEnd)
        val contentLengthMatch = """Content-Length:\s*(\d+)""".r.findFirstMatchIn(header)
        contentLengthMatch match {
          case Some(m) =>
            val contentLength = m.group(1).toInt
            val bodyStart = headerEnd + 4
            val bodyEnd = bodyStart + contentLength
            if (buffer.length >= bodyEnd) {
              // Complete message - extract it and continue
              val completeMessage = buffer.substring(0, bodyEnd)
              val remaining = buffer.substring(bodyEnd)
              extractCompleteLspMessages(remaining, acc :+ completeMessage)
            } else {
              // Body not complete yet
              (buffer, acc)
            }
          case None =>
            // Malformed header, skip to next potential message
            val nextStart = buffer.indexOf("Content-Length:", 1)
            if (nextStart > 0) {
              extractCompleteLspMessages(buffer.substring(nextStart), acc)
            } else {
              // Discard malformed buffer
              ("", acc)
            }
        }
      }
    }
  }

  /** Format outgoing message with LSP Content-Length header */
  private def formatLspMessage(json: String): String = {
    val contentLength = json.getBytes("UTF-8").length
    s"Content-Length: $contentLength\r\n\r\n$json"
  }

  /** Extract JSON content from LSP message (strips Content-Length header if present) */
  private def extractJsonFromLspMessage(message: String): String =
    // LSP messages may have format: "Content-Length: XXX\r\n\r\n{json}"
    // or just plain JSON
    if message.startsWith("Content-Length:") then {
      // Find the double newline that separates headers from content
      val headerEnd = message.indexOf("\r\n\r\n")
      if headerEnd > 0 then {
        message.substring(headerEnd + 4) // Skip past "\r\n\r\n"
      } else {
        message
      }
    } else {
      message
    }

  /** Parse incoming message as either Request or Notification */
  private def parseMessage(message: String): IO[Either[Request, Notification]] = {
    val jsonContent = extractJsonFromLspMessage(message)

    // Debug: log message preview
    val preview = jsonContent.take(100).replace("\n", "\\n").replace("\r", "\\r")
    logger.debug(s"Parsing message (${jsonContent.length} chars): $preview...") *>
    IO.fromEither(
      parse(jsonContent).left.map { err =>
        // Log parse failures with more context
        val msgPreview = message.take(200).replace("\n", "\\n").replace("\r", "\\r")
        new Exception(s"${err.getMessage} | Raw message preview: $msgPreview")
      }.flatMap { json =>
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
