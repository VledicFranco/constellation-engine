package io.constellation.provider.sdk

import java.util.concurrent.TimeUnit

import cats.effect.{IO, Resource}

import io.constellation.provider.v1.provider as pb

import io.grpc.stub.StreamObserver
import io.grpc.{ManagedChannel, ManagedChannelBuilder}

/** Production ProviderTransport backed by gRPC stubs.
  *
  * Wraps ScalaPB-generated blocking and async stubs to communicate with a Constellation server.
  */
class GrpcProviderTransport(private[sdk] val channel: ManagedChannel) extends ProviderTransport {

  private val blockingStub = pb.ModuleProviderGrpc.blockingStub(channel)

  def register(request: pb.RegisterRequest): IO[pb.RegisterResponse] =
    IO(blockingStub.register(request))

  def deregister(request: pb.DeregisterRequest): IO[pb.DeregisterResponse] =
    IO(blockingStub.deregister(request))

  def openControlPlane(handler: ControlPlaneHandler): Resource[IO, ControlPlaneStream] = {
    val asyncStub = pb.ModuleProviderGrpc.stub(channel)

    Resource.make(
      IO {
        val responseObserver = new StreamObserver[pb.ControlMessage] {
          import cats.effect.unsafe.implicits.global

          override def onNext(msg: pb.ControlMessage): Unit =
            (msg.payload match {
              case pb.ControlMessage.Payload.HeartbeatAck(ack) => handler.onHeartbeatAck(ack)
              case pb.ControlMessage.Payload.ActiveModulesReport(report) =>
                handler.onActiveModulesReport(report)
              case pb.ControlMessage.Payload.DrainRequest(drain) => handler.onDrainRequest(drain)
              case _                                             => IO.unit
            }).unsafeRunAndForget()

          override def onError(t: Throwable): Unit =
            handler.onStreamError(t).unsafeRunAndForget()

          override def onCompleted(): Unit =
            handler.onStreamCompleted.unsafeRunAndForget()
        }

        val requestObserver = asyncStub.controlPlane(responseObserver)

        new GrpcControlPlaneStream(requestObserver)
      }
    )(stream => stream.close)
  }
}

object GrpcProviderTransport {

  /** Create a GrpcProviderTransport as a Resource that manages the channel lifecycle. */
  def apply(host: String, port: Int): Resource[IO, GrpcProviderTransport] =
    Resource.make(
      IO {
        val channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()
        new GrpcProviderTransport(channel)
      }
    )(transport =>
      IO {
        transport.channel.shutdown()
        try transport.channel.awaitTermination(5, TimeUnit.SECONDS)
        catch { case _: InterruptedException => () }
      }
    )
}

/** Production ControlPlaneStream backed by a gRPC StreamObserver. */
private class GrpcControlPlaneStream(
    requestObserver: StreamObserver[pb.ControlMessage]
) extends ControlPlaneStream {

  def sendHeartbeat(hb: pb.Heartbeat): IO[Unit] = sendHeartbeat(hb, "")

  override def sendHeartbeat(hb: pb.Heartbeat, connectionId: String): IO[Unit] = IO {
    requestObserver.onNext(
      pb.ControlMessage(
        connectionId = connectionId,
        protocolVersion = 1,
        payload = pb.ControlMessage.Payload.Heartbeat(hb)
      )
    )
  }

  def sendDrainAck(ack: pb.DrainAck): IO[Unit] = IO {
    requestObserver.onNext(
      pb.ControlMessage(
        payload = pb.ControlMessage.Payload.DrainAck(ack)
      )
    )
  }

  def close: IO[Unit] = IO {
    try requestObserver.onCompleted()
    catch { case _: Exception => () }
  }
}
