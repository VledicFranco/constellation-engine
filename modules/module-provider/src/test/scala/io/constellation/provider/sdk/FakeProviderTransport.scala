package io.constellation.provider.sdk

import java.util.concurrent.ConcurrentLinkedQueue

import cats.effect.{IO, Ref, Resource}
import cats.effect.std.Queue
import cats.implicits.*

import io.constellation.provider.v1.{provider => pb}

/** In-memory ProviderTransport for testing. Records calls and returns configurable responses. */
class FakeProviderTransport(
    registerResponse: Ref[IO, pb.RegisterResponse],
    deregisterResponse: Ref[IO, pb.DeregisterResponse],
    controlMessages: Queue[IO, pb.ControlMessage]
) extends ProviderTransport {

  /** Recorded register calls. */
  val registerCalls: ConcurrentLinkedQueue[pb.RegisterRequest] = new ConcurrentLinkedQueue()

  /** Recorded deregister calls. */
  val deregisterCalls: ConcurrentLinkedQueue[pb.DeregisterRequest] = new ConcurrentLinkedQueue()

  /** Whether the control plane has been opened. */
  @volatile var controlPlaneOpened: Boolean = false

  /** Whether the control plane has been closed. */
  @volatile var controlPlaneClosed: Boolean = false

  def register(request: pb.RegisterRequest): IO[pb.RegisterResponse] =
    IO(registerCalls.add(request)) >> registerResponse.get

  def deregister(request: pb.DeregisterRequest): IO[pb.DeregisterResponse] =
    IO(deregisterCalls.add(request)) >> deregisterResponse.get

  def openControlPlane(handler: ControlPlaneHandler): Resource[IO, ControlPlaneStream] = {
    val acquire = IO {
      controlPlaneOpened = true
      new FakeControlPlaneStream(handler, controlMessages)
    }
    val release = (stream: FakeControlPlaneStream) => IO { controlPlaneClosed = true }
    Resource.make(acquire)(release).widen[ControlPlaneStream]
  }

  /** Configure the response for future register calls. */
  def setRegisterResponse(resp: pb.RegisterResponse): IO[Unit] =
    registerResponse.set(resp)

  /** Configure the response for future deregister calls. */
  def setDeregisterResponse(resp: pb.DeregisterResponse): IO[Unit] =
    deregisterResponse.set(resp)

  /** Enqueue a server-originated control message to be delivered to the handler. */
  def enqueueControlMessage(msg: pb.ControlMessage): IO[Unit] =
    controlMessages.offer(msg)
}

object FakeProviderTransport {

  /** Create a FakeProviderTransport with default success responses. */
  def create: IO[FakeProviderTransport] =
    for {
      regResp <- Ref.of[IO, pb.RegisterResponse](
        pb.RegisterResponse(success = true, connectionId = "fake-conn-id", protocolVersion = 1)
      )
      deregResp <- Ref.of[IO, pb.DeregisterResponse](
        pb.DeregisterResponse(success = true)
      )
      queue <- Queue.unbounded[IO, pb.ControlMessage]
    } yield new FakeProviderTransport(regResp, deregResp, queue)
}

/** In-memory control plane stream for testing. */
class FakeControlPlaneStream(
    handler: ControlPlaneHandler,
    controlMessages: Queue[IO, pb.ControlMessage]
) extends ControlPlaneStream {

  val sentHeartbeats: ConcurrentLinkedQueue[pb.Heartbeat] = new ConcurrentLinkedQueue()
  val sentDrainAcks: ConcurrentLinkedQueue[pb.DrainAck] = new ConcurrentLinkedQueue()
  @volatile var closed: Boolean = false

  def sendHeartbeat(hb: pb.Heartbeat): IO[Unit] =
    IO(sentHeartbeats.add(hb)).void

  def sendDrainAck(ack: pb.DrainAck): IO[Unit] =
    IO(sentDrainAcks.add(ack)).void

  def close: IO[Unit] =
    IO { closed = true }

  /** Process one pending server message by dispatching to the handler. */
  def processOneMessage: IO[Boolean] =
    controlMessages.tryTake.flatMap {
      case Some(msg) =>
        dispatchMessage(msg).as(true)
      case None =>
        IO.pure(false)
    }

  private def dispatchMessage(msg: pb.ControlMessage): IO[Unit] =
    msg.payload match {
      case pb.ControlMessage.Payload.HeartbeatAck(ack) =>
        handler.onHeartbeatAck(ack)
      case pb.ControlMessage.Payload.ActiveModulesReport(report) =>
        handler.onActiveModulesReport(report)
      case pb.ControlMessage.Payload.DrainRequest(drain) =>
        handler.onDrainRequest(drain)
      case _ =>
        IO.unit
    }
}

/** In-memory ExecutorServerFactory for testing. */
class FakeExecutorServerFactory extends ExecutorServerFactory {
  @volatile var handler: Option[pb.ExecuteRequest => IO[pb.ExecuteResponse]] = None
  @volatile var started: Boolean = false
  @volatile var stopped: Boolean = false

  def create(
      h: pb.ExecuteRequest => IO[pb.ExecuteResponse],
      port: Int
  ): Resource[IO, Int] =
    Resource.make(
      IO {
        handler = Some(h)
        started = true
        port
      }
    )(_ => IO { stopped = true })

  /** Execute a request through the registered handler (for test assertions). */
  def execute(request: pb.ExecuteRequest): IO[pb.ExecuteResponse] =
    handler match {
      case Some(h) => h(request)
      case None    => IO.raiseError(new RuntimeException("No handler registered"))
    }
}
