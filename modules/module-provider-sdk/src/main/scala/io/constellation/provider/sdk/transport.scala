package io.constellation.provider.sdk

import cats.effect.{IO, Resource}

import io.constellation.provider.v1.provider as pb

// ===== Provider Transport =====

/** Abstracts the gRPC client-side transport for registration and control plane.
  *
  * Production: wraps ScalaPB stubs. Test: in-memory fake.
  */
trait ProviderTransport {
  def register(request: pb.RegisterRequest): IO[pb.RegisterResponse]
  def deregister(request: pb.DeregisterRequest): IO[pb.DeregisterResponse]
  def openControlPlane(handler: ControlPlaneHandler): Resource[IO, ControlPlaneStream]
}

// ===== Control Plane Handler =====

/** Callbacks for server-originated control plane messages. */
trait ControlPlaneHandler {
  def onHeartbeatAck(ack: pb.HeartbeatAck): IO[Unit]
  def onActiveModulesReport(report: pb.ActiveModulesReport): IO[Unit]
  def onDrainRequest(drain: pb.DrainRequest): IO[Unit]
  def onStreamError(error: Throwable): IO[Unit]
  def onStreamCompleted: IO[Unit]
}

// ===== Control Plane Stream =====

/** Client-side control plane stream handle for sending messages. */
trait ControlPlaneStream {
  def sendHeartbeat(hb: pb.Heartbeat): IO[Unit]
  def sendHeartbeat(hb: pb.Heartbeat, connectionId: String): IO[Unit] = sendHeartbeat(hb)
  def sendDrainAck(ack: pb.DrainAck): IO[Unit]
  def close: IO[Unit]
}

// ===== Executor Server Factory =====

/** Abstracts hosting the ModuleExecutor gRPC service.
  *
  * Production: wraps io.grpc.ServerBuilder. Test: in-memory dispatch.
  */
trait ExecutorServerFactory {

  /** Create and start an executor server. Returns the actual bound port. */
  def create(handler: pb.ExecuteRequest => IO[pb.ExecuteResponse], port: Int): Resource[IO, Int]
}
