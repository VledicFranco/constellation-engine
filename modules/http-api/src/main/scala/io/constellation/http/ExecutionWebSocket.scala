package io.constellation.http

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import scala.jdk.CollectionConverters.*

import cats.effect.std.Queue
import cats.effect.{IO, Ref}
import cats.implicits.*

import io.constellation.spi.ExecutionListener

import fs2.{Pipe, Stream}
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import io.circe.{Encoder, Json}
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/** Execution event types sent over WebSocket */
sealed trait ExecutionEvent {
  def eventType: String
  def executionId: String
  def timestamp: Long
}

object ExecutionEvent {
  case class ExecutionStarted(
      executionId: String,
      dagName: String,
      timestamp: Long
  ) extends ExecutionEvent {
    val eventType = "execution:start"
  }

  case class ModuleStarted(
      executionId: String,
      moduleId: String,
      moduleName: String,
      timestamp: Long
  ) extends ExecutionEvent {
    val eventType = "module:start"
  }

  case class ModuleCompleted(
      executionId: String,
      moduleId: String,
      moduleName: String,
      durationMs: Long,
      timestamp: Long
  ) extends ExecutionEvent {
    val eventType = "module:complete"
  }

  case class ModuleFailed(
      executionId: String,
      moduleId: String,
      moduleName: String,
      error: String,
      timestamp: Long
  ) extends ExecutionEvent {
    val eventType = "module:failed"
  }

  case class ExecutionCompleted(
      executionId: String,
      dagName: String,
      succeeded: Boolean,
      durationMs: Long,
      timestamp: Long
  ) extends ExecutionEvent {
    val eventType = "execution:complete"
  }

  case class ExecutionCancelled(
      executionId: String,
      dagName: String,
      timestamp: Long
  ) extends ExecutionEvent {
    val eventType = "execution:cancelled"
  }

  // JSON encoders
  given Encoder[ExecutionStarted]   = deriveEncoder
  given Encoder[ModuleStarted]      = deriveEncoder
  given Encoder[ModuleCompleted]    = deriveEncoder
  given Encoder[ModuleFailed]       = deriveEncoder
  given Encoder[ExecutionCompleted] = deriveEncoder
  given Encoder[ExecutionCancelled] = deriveEncoder

  given Encoder[ExecutionEvent] = Encoder.instance {
    case e: ExecutionStarted   => e.asJson.deepMerge(Json.obj("type" -> Json.fromString(e.eventType)))
    case e: ModuleStarted      => e.asJson.deepMerge(Json.obj("type" -> Json.fromString(e.eventType)))
    case e: ModuleCompleted    => e.asJson.deepMerge(Json.obj("type" -> Json.fromString(e.eventType)))
    case e: ModuleFailed       => e.asJson.deepMerge(Json.obj("type" -> Json.fromString(e.eventType)))
    case e: ExecutionCompleted => e.asJson.deepMerge(Json.obj("type" -> Json.fromString(e.eventType)))
    case e: ExecutionCancelled => e.asJson.deepMerge(Json.obj("type" -> Json.fromString(e.eventType)))
  }
}

/** Subscription info for a connected client */
case class Subscription(
    clientId: String,
    executionId: Option[String], // None = subscribe to all executions
    queue: Queue[IO, ExecutionEvent]
)

/** WebSocket handler for streaming execution events to dashboard clients.
  *
  * Implements ExecutionListener to receive events from the runtime and broadcasts them to all
  * connected WebSocket clients that are subscribed to the relevant execution.
  *
  * Usage:
  *   1. Create an instance and register it as an ExecutionListener with Constellation
  *   2. Add routes(wsb) to your http4s server
  *   3. Clients connect to /api/v1/executions/events?executionId=xxx (or omit for all)
  */
class ExecutionWebSocket {
  private val logger: Logger[IO] = Slf4jLogger.getLoggerFromClass[IO](classOf[ExecutionWebSocket])

  // Thread-safe map of client subscriptions
  private val subscriptions: ConcurrentHashMap[String, Subscription] = new ConcurrentHashMap()

  /** Maximum queue size per client before messages are dropped */
  private val maxQueueSize = 100

  /** Get the ExecutionListener to register with Constellation */
  val listener: ExecutionListener = new ExecutionListener {
    def onExecutionStart(executionId: UUID, dagName: String): IO[Unit] =
      publish(
        executionId.toString,
        ExecutionEvent.ExecutionStarted(
          executionId = executionId.toString,
          dagName = dagName,
          timestamp = System.currentTimeMillis()
        )
      )

    def onModuleStart(executionId: UUID, moduleId: UUID, moduleName: String): IO[Unit] =
      publish(
        executionId.toString,
        ExecutionEvent.ModuleStarted(
          executionId = executionId.toString,
          moduleId = moduleId.toString,
          moduleName = moduleName,
          timestamp = System.currentTimeMillis()
        )
      )

    def onModuleComplete(
        executionId: UUID,
        moduleId: UUID,
        moduleName: String,
        durationMs: Long
    ): IO[Unit] =
      publish(
        executionId.toString,
        ExecutionEvent.ModuleCompleted(
          executionId = executionId.toString,
          moduleId = moduleId.toString,
          moduleName = moduleName,
          durationMs = durationMs,
          timestamp = System.currentTimeMillis()
        )
      )

    def onModuleFailed(
        executionId: UUID,
        moduleId: UUID,
        moduleName: String,
        error: Throwable
    ): IO[Unit] =
      publish(
        executionId.toString,
        ExecutionEvent.ModuleFailed(
          executionId = executionId.toString,
          moduleId = moduleId.toString,
          moduleName = moduleName,
          error = Option(error.getMessage).getOrElse(error.getClass.getSimpleName),
          timestamp = System.currentTimeMillis()
        )
      )

    def onExecutionComplete(
        executionId: UUID,
        dagName: String,
        succeeded: Boolean,
        durationMs: Long
    ): IO[Unit] =
      publish(
        executionId.toString,
        ExecutionEvent.ExecutionCompleted(
          executionId = executionId.toString,
          dagName = dagName,
          succeeded = succeeded,
          durationMs = durationMs,
          timestamp = System.currentTimeMillis()
        )
      )

    override def onExecutionCancelled(executionId: UUID, dagName: String): IO[Unit] =
      publish(
        executionId.toString,
        ExecutionEvent.ExecutionCancelled(
          executionId = executionId.toString,
          dagName = dagName,
          timestamp = System.currentTimeMillis()
        )
      )
  }

  /** Broadcast an event to all subscribed clients */
  def publish(executionId: String, event: ExecutionEvent): IO[Unit] = {
    val subscribers = subscriptions.values().asScala.toList.filter { sub =>
      sub.executionId.isEmpty || sub.executionId.contains(executionId)
    }

    subscribers.traverse_ { sub =>
      sub.queue.tryOffer(event).flatMap {
        case true  => IO.unit
        case false => logger.warn(s"Dropped event for client ${sub.clientId} (queue full)")
      }
    }
  }

  /** Subscribe a new client */
  private def subscribe(clientId: String, executionId: Option[String]): IO[Queue[IO, ExecutionEvent]] =
    for {
      queue <- Queue.bounded[IO, ExecutionEvent](maxQueueSize)
      sub    = Subscription(clientId, executionId, queue)
      _     <- IO(subscriptions.put(clientId, sub))
      _     <- logger.debug(s"Client $clientId subscribed to execution: ${executionId.getOrElse("all")}")
    } yield queue

  /** Unsubscribe a client */
  private def unsubscribe(clientId: String): IO[Unit] =
    for {
      _ <- IO(subscriptions.remove(clientId))
      _ <- logger.debug(s"Client $clientId unsubscribed")
    } yield ()

  /** Get count of active subscriptions (for testing/monitoring) */
  def subscriptionCount: IO[Int] = IO(subscriptions.size())

  /** Object for query parameter extraction */
  private object ExecutionIdParam extends OptionalQueryParamDecoderMatcher[String]("executionId")

  /** HTTP routes for WebSocket connections */
  def routes(wsb: WebSocketBuilder2[IO]): HttpRoutes[IO] = HttpRoutes.of[IO] {
    // Subscribe to execution events
    // GET /api/v1/executions/events?executionId=xxx (optional filter)
    case GET -> Root / "api" / "v1" / "executions" / "events" :? ExecutionIdParam(executionIdOpt) =>
      val clientId = UUID.randomUUID().toString

      for {
        queue <- subscribe(clientId, executionIdOpt)

        // Stream events from queue to client
        toClient: Stream[IO, WebSocketFrame] = Stream
          .fromQueueUnterminated(queue)
          .map(event => WebSocketFrame.Text(event.asJson.noSpaces))

        // Handle incoming messages (just keep-alive pings for now)
        fromClient: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
          case WebSocketFrame.Text("ping", _) => IO.unit
          case WebSocketFrame.Close(_)        => unsubscribe(clientId)
          case _                              => IO.unit
        }

        // Build WebSocket with cleanup on disconnect
        response <- wsb
          .withOnClose(unsubscribe(clientId))
          .build(toClient, fromClient)
      } yield response
  }
}

object ExecutionWebSocket {
  def apply(): ExecutionWebSocket = new ExecutionWebSocket()
}
