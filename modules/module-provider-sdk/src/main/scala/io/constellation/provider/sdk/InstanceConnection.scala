package io.constellation.provider.sdk

import scala.concurrent.duration.*

import cats.effect.{Fiber, IO, Ref}
import cats.implicits.*

import io.constellation.provider.CValueSerializer
import io.constellation.provider.v1.provider as pb

// ===== Connection State =====

enum InstanceConnectionState:
  case Disconnected
  case Registering
  case Active
  case Draining
  case Reconnecting

// ===== InstanceConnection =====

/** Manages a single connection to a Constellation instance.
  *
  * Handles registration, deregistration, and state tracking. Control plane (heartbeats,
  * reconnection) is managed externally by ConstellationProvider.
  */
class InstanceConnection(
    val instanceAddress: String,
    val namespace: String,
    transport: ProviderTransport,
    config: SdkConfig,
    private[sdk] val modulesRef: Ref[IO, List[ModuleDefinition]],
    serializer: CValueSerializer
) {

  private val state = Ref.unsafe[IO, InstanceConnectionState](InstanceConnectionState.Disconnected)
  private val connIdRef = Ref.unsafe[IO, Option[String]](None)
  private val controlPlaneRef =
    Ref.unsafe[IO, Option[(ControlPlaneStream, IO[Unit])]](None)
  private val heartbeatFiberRef =
    Ref.unsafe[IO, Option[Fiber[IO, Throwable, Nothing]]](None)

  /** Get the current connection state. */
  def currentState: IO[InstanceConnectionState] = state.get

  /** Get the connection ID assigned by the server. */
  def connectionId: IO[Option[String]] = connIdRef.get

  /** Check if this connection is healthy (Active). */
  def isHealthy: IO[Boolean] = state.get.map(_ == InstanceConnectionState.Active)

  /** Connect to the Constellation instance by registering modules.
    *
    * Idempotent: if already Active, this is a no-op.
    */
  def connect: IO[Unit] =
    state.get.flatMap {
      case InstanceConnectionState.Active => IO.unit // Already connected
      case _ =>
        for {
          _       <- state.set(InstanceConnectionState.Registering)
          modules <- modulesRef.get
          request = pb.RegisterRequest(
            namespace = namespace,
            modules = modules.map(_.toDeclaration),
            protocolVersion = 1,
            executorUrl = s"${config.executorHost}:${config.executorPort}",
            groupId = config.groupId.getOrElse("")
          )
          response <- transport.register(request).handleErrorWith { error =>
            state.set(InstanceConnectionState.Disconnected) >>
              IO.raiseError(error)
          }
          _ <-
            if response.success then
              for {
                _ <- connIdRef.set(Some(response.connectionId))
                // Open control plane stream and start heartbeats
                _ <- startControlPlane(response.connectionId)
                _ <- state.set(InstanceConnectionState.Active)
              } yield ()
            else
              state.set(InstanceConnectionState.Disconnected) >>
                IO.raiseError(
                  new RuntimeException(
                    s"Registration failed: ${response.results.filterNot(_.accepted).map(r => s"${r.moduleName}: ${r.rejectionReason}").mkString(", ")}"
                  )
                )
        } yield ()
    }

  /** Gracefully disconnect from the Constellation instance.
    *
    * Sends deregister, clears connection state. No-op if already disconnected.
    */
  def disconnect: IO[Unit] =
    state.get.flatMap {
      case InstanceConnectionState.Disconnected => IO.unit
      case _ =>
        for {
          // Stop heartbeats and close control plane
          _       <- stopControlPlane
          modules <- modulesRef.get
          connId  <- connIdRef.get
          _ <- connId.traverse_ { cid =>
            transport
              .deregister(
                pb.DeregisterRequest(
                  namespace = namespace,
                  moduleNames = modules.map(_.name),
                  connectionId = cid
                )
              )
              .void
              .handleErrorWith(_ => IO.unit)
          }
          _ <- connIdRef.set(None)
          _ <- state.set(InstanceConnectionState.Disconnected)
        } yield ()
    }

  /** Replace the module list (for canary rollout). */
  def replaceModules(newModules: List[ModuleDefinition]): IO[Unit] =
    modulesRef.set(newModules)

  /** Simulate a drain state transition (for testing). */
  private[sdk] def simulateDrain: IO[Unit] =
    state.set(InstanceConnectionState.Draining)

  // ===== Control Plane =====

  private val heartbeatInterval: FiniteDuration = config.heartbeatInterval

  /** Open the control plane stream and start the heartbeat loop. */
  private def startControlPlane(connectionId: String): IO[Unit] = {
    val handler = new ControlPlaneHandler {
      def onHeartbeatAck(ack: pb.HeartbeatAck): IO[Unit]                  = IO.unit
      def onActiveModulesReport(report: pb.ActiveModulesReport): IO[Unit] = IO.unit
      def onDrainRequest(drain: pb.DrainRequest): IO[Unit] =
        state.set(InstanceConnectionState.Draining)
      def onStreamError(error: Throwable): IO[Unit] = IO.unit
      def onStreamCompleted: IO[Unit]               = IO.unit
    }

    for {
      allocated <- transport.openControlPlane(handler).allocated
      (stream, cleanup) = allocated
      _ <- controlPlaneRef.set(Some((stream, cleanup)))
      // Send initial heartbeat with connectionId to activate the control plane on server
      _ <- stream.sendHeartbeat(pb.Heartbeat(timestamp = System.currentTimeMillis()), connectionId)
      // Start periodic heartbeat loop
      fiber <- heartbeatLoop(stream, connectionId).start
      _     <- heartbeatFiberRef.set(Some(fiber))
    } yield ()
  }

  /** Stop the heartbeat loop and close the control plane stream. */
  private def stopControlPlane: IO[Unit] =
    for {
      fiberOpt <- heartbeatFiberRef.getAndSet(None)
      _        <- fiberOpt.traverse_(_.cancel)
      cpOpt    <- controlPlaneRef.getAndSet(None)
      _        <- cpOpt.traverse_ { case (_, cleanup) => cleanup.handleErrorWith(_ => IO.unit) }
    } yield ()

  /** Periodically send heartbeats on the control plane stream. */
  private def heartbeatLoop(
      stream: ControlPlaneStream,
      connectionId: String
  ): IO[Nothing] =
    (IO.sleep(heartbeatInterval) >>
      stream
        .sendHeartbeat(pb.Heartbeat(timestamp = System.currentTimeMillis()), connectionId)
        .handleErrorWith(_ => IO.unit)).foreverM
}
