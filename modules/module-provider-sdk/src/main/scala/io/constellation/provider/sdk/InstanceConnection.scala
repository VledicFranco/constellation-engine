package io.constellation.provider.sdk

import cats.effect.{IO, Ref}
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
              connIdRef.set(Some(response.connectionId)) >>
                state.set(InstanceConnectionState.Active)
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
}
