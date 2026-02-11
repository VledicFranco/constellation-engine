package io.constellation.provider

import scala.concurrent.duration.*

import cats.effect.{IO, Ref, Resource}
import cats.implicits.*

import io.constellation.provider.v1.{provider => pb}

import io.grpc.stub.StreamObserver

// ===== Connection State =====

enum ConnectionState:
  case Registered   // Register RPC succeeded, awaiting control plane stream
  case Active       // Control plane stream established, heartbeats flowing
  case Disconnected // Stream broke or timed out — cleanup pending

/** Tracks a provider's active connection state. */
final case class ProviderConnection(
    connectionId: String,
    namespace: String,
    executorUrl: String,
    registeredModules: Set[String],
    protocolVersion: Int,
    state: ConnectionState,
    registeredAt: Long,
    controlPlaneEstablishedAt: Option[Long],
    lastHeartbeatAt: Option[Long],
    responseObserver: Option[StreamObserver[pb.ControlMessage]]
)

// ===== ControlPlaneManager =====

/** Manages provider connection lifecycle, liveness monitoring, and periodic ActiveModulesReport.
  *
  * Connection lifecycle: Registered → Active → Disconnected
  *   - Registered: Register RPC succeeded, provider must open ControlPlane stream within timeout
  *   - Active: ControlPlane stream established, heartbeats expected within timeout
  *   - Disconnected: Stream broke or timed out, modules being cleaned up
  */
class ControlPlaneManager(
    providerState: Ref[IO, Map[String, ProviderConnection]],
    config: ProviderManagerConfig,
    onConnectionDead: String => IO[Unit]
) {

  /** Register a new connection after successful Register RPC. */
  def registerConnection(
      connectionId: String,
      namespace: String,
      executorUrl: String,
      modules: Set[String],
      protocolVersion: Int
  ): IO[Unit] =
    IO.realTime.flatMap { now =>
      providerState.update(_ + (connectionId -> ProviderConnection(
        connectionId = connectionId,
        namespace = namespace,
        executorUrl = executorUrl,
        registeredModules = modules,
        protocolVersion = protocolVersion,
        state = ConnectionState.Registered,
        registeredAt = now.toMillis,
        controlPlaneEstablishedAt = None,
        lastHeartbeatAt = None,
        responseObserver = None
      )))
    }

  /** Transition a connection to Active when its control plane stream opens. Returns false if connection not found. */
  def activateControlPlane(
      connectionId: String,
      observer: StreamObserver[pb.ControlMessage]
  ): IO[Boolean] =
    IO.realTime.flatMap { now =>
      providerState.modify { state =>
        state.get(connectionId) match {
          case Some(conn) if conn.state == ConnectionState.Registered =>
            val updated = conn.copy(
              state = ConnectionState.Active,
              controlPlaneEstablishedAt = Some(now.toMillis),
              lastHeartbeatAt = Some(now.toMillis),
              responseObserver = Some(observer)
            )
            (state + (connectionId -> updated), true)
          case _ =>
            (state, false)
        }
      }
    }

  /** Record a heartbeat from a provider. */
  def recordHeartbeat(connectionId: String): IO[Unit] =
    IO.realTime.flatMap { now =>
      providerState.update { state =>
        state.get(connectionId) match {
          case Some(conn) if conn.state == ConnectionState.Active =>
            state + (connectionId -> conn.copy(lastHeartbeatAt = Some(now.toMillis)))
          case _ => state
        }
      }
    }

  /** Transition a connection to Disconnected. */
  def deactivateConnection(connectionId: String): IO[Unit] =
    providerState.update { state =>
      state.get(connectionId) match {
        case Some(conn) =>
          state + (connectionId -> conn.copy(
            state = ConnectionState.Disconnected,
            responseObserver = None
          ))
        case None => state
      }
    }

  /** Look up a connection by ID. */
  def getConnection(connectionId: String): IO[Option[ProviderConnection]] =
    providerState.get.map(_.get(connectionId))

  /** Look up a connection by namespace. */
  def getConnectionByNamespace(namespace: String): IO[Option[ProviderConnection]] =
    providerState.get.map(_.values.find(_.namespace == namespace))

  /** Remove a connection from state entirely. */
  def removeConnection(connectionId: String): IO[Unit] =
    providerState.update(_ - connectionId)

  /** Get all active connections. */
  def getAllConnections: IO[List[ProviderConnection]] =
    providerState.get.map(_.values.toList)

  /** Update registered modules for a connection. */
  def updateModules(connectionId: String, modules: Set[String]): IO[Unit] =
    providerState.update { state =>
      state.get(connectionId) match {
        case Some(conn) => state + (connectionId -> conn.copy(registeredModules = modules))
        case None       => state
      }
    }

  // ===== Background Fibers =====

  /** Start the liveness monitor as a managed Resource (fiber is canceled on release). */
  def startLivenessMonitor: Resource[IO, Unit] = {
    val loop = (checkLiveness >> IO.sleep(1.second)).foreverM
    Resource.make(loop.start)(_.cancel).void
  }

  /** Start the ActiveModulesReport sender as a managed Resource (fiber is canceled on release). */
  def startActiveModulesReporter: Resource[IO, Unit] = {
    val loop = (IO.sleep(config.activeModulesReportInterval) >> sendActiveModulesReports).foreverM
    Resource.make(loop.start)(_.cancel).void
  }

  private def checkLiveness: IO[Unit] =
    for {
      now <- IO.realTime.map(_.toMillis)
      // Atomically identify dead connections and mark them Disconnected, clearing observers
      deadConnections <- providerState.modify { state =>
        val (dead, alive) = state.partition { case (_, conn) =>
          conn.state match {
            case ConnectionState.Registered =>
              now - conn.registeredAt > config.controlPlaneRequiredTimeout.toMillis
            case ConnectionState.Active =>
              conn.lastHeartbeatAt.exists(last => now - last > config.heartbeatTimeout.toMillis)
            case ConnectionState.Disconnected =>
              false
          }
        }
        val disconnected = dead.view.mapValues(_.copy(
          state = ConnectionState.Disconnected,
          responseObserver = None
        )).toMap
        (alive ++ disconnected, dead.values.toList)
      }
      _ <- deadConnections.traverse_ { conn =>
        // Close the observer stream if present (using the atomically-claimed snapshot)
        IO(conn.responseObserver.foreach(obs =>
          try obs.onCompleted()
          catch { case _: Exception => () }
        )) >>
          onConnectionDead(conn.connectionId)
      }
    } yield ()

  private def sendActiveModulesReports: IO[Unit] =
    for {
      state <- providerState.get
      activeConnections = state.values.filter(c =>
        c.state == ConnectionState.Active && c.responseObserver.isDefined
      ).toList
      _ <- activeConnections.traverse_ { conn =>
        val moduleNames = conn.registeredModules.map { qualifiedName =>
          // Strip namespace prefix to get short name
          val prefix = conn.namespace + "."
          if qualifiedName.startsWith(prefix) then qualifiedName.drop(prefix.length)
          else qualifiedName
        }.toSeq
        val report = pb.ControlMessage(
          protocolVersion = conn.protocolVersion,
          connectionId = conn.connectionId,
          payload = pb.ControlMessage.Payload.ActiveModulesReport(
            pb.ActiveModulesReport(activeModules = moduleNames)
          )
        )
        IO(conn.responseObserver.foreach(_.onNext(report))).handleErrorWith(_ => IO.unit)
      }
    } yield ()
}
