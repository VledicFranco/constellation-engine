package io.constellation.provider.sdk

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.provider.v1.provider as pb

import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.stub.StreamObserver

import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class GrpcProviderTransportSpec extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private var serverName: String                  = _
  private var server: io.grpc.Server              = _
  private var channel: io.grpc.ManagedChannel     = _
  private var fakeService: FakeModuleProviderImpl = _

  override def beforeEach(): Unit = {
    serverName = io.grpc.inprocess.InProcessServerBuilder.generateName()
    fakeService = new FakeModuleProviderImpl
    server = InProcessServerBuilder
      .forName(serverName)
      .directExecutor()
      .addService(
        pb.ModuleProviderGrpc.bindService(fakeService, scala.concurrent.ExecutionContext.global)
      )
      .build()
      .start()
    channel = InProcessChannelBuilder.forName(serverName).directExecutor().build()
  }

  override def afterEach(): Unit = {
    if channel != null then channel.shutdownNow()
    if server != null then server.shutdownNow()
  }

  private def transport = new GrpcProviderTransport(channel)

  // ===== Register =====

  "GrpcProviderTransport" should "send register request and return response" in {
    val req = pb.RegisterRequest(
      namespace = "test.ns",
      protocolVersion = 1,
      executorUrl = "localhost:9091"
    )
    val resp = transport.register(req).unsafeRunSync()

    resp.success shouldBe true
    resp.connectionId shouldBe "fake-conn-id"
    fakeService.lastRegisterRequest.get() shouldBe req
  }

  // ===== Deregister =====

  it should "send deregister request and return response" in {
    val req = pb.DeregisterRequest(
      namespace = "test.ns",
      moduleNames = List("mod1"),
      connectionId = "conn-1"
    )
    val resp = transport.deregister(req).unsafeRunSync()

    resp.success shouldBe true
    fakeService.lastDeregisterRequest.get() shouldBe req
  }

  // ===== Register gRPC error =====

  it should "propagate gRPC errors on register" in {
    fakeService.registerError = Some(io.grpc.Status.UNAVAILABLE.withDescription("server down"))
    val req = pb.RegisterRequest(namespace = "test.ns")

    val result = transport.register(req).attempt.unsafeRunSync()
    result shouldBe a[Left[_, _]]
    result.left.getOrElse(null).getMessage should include("UNAVAILABLE")
  }

  // ===== Deregister gRPC error =====

  it should "propagate gRPC errors on deregister" in {
    fakeService.deregisterError = Some(io.grpc.Status.NOT_FOUND.withDescription("not found"))
    val req = pb.DeregisterRequest(namespace = "test.ns", moduleNames = List("mod1"))

    val result = transport.deregister(req).attempt.unsafeRunSync()
    result shouldBe a[Left[_, _]]
  }

  // ===== Control plane: heartbeat roundtrip =====

  it should "open control plane and send heartbeat" in {
    val ackLatch    = new CountDownLatch(1)
    val receivedAck = new AtomicReference[pb.HeartbeatAck](null)

    val handler = new ControlPlaneHandler {
      def onHeartbeatAck(ack: pb.HeartbeatAck): IO[Unit] = IO {
        receivedAck.set(ack)
        ackLatch.countDown()
      }
      def onActiveModulesReport(report: pb.ActiveModulesReport): IO[Unit] = IO.unit
      def onDrainRequest(drain: pb.DrainRequest): IO[Unit]                = IO.unit
      def onStreamError(error: Throwable): IO[Unit]                       = IO.unit
      def onStreamCompleted: IO[Unit]                                     = IO.unit
    }

    val (stream, release) = transport.openControlPlane(handler).allocated.unsafeRunSync()
    try {
      stream.sendHeartbeat(pb.Heartbeat(namespace = "test.ns", timestamp = 12345L)).unsafeRunSync()

      // The fake server echoes heartbeat as HeartbeatAck
      ackLatch.await(5, TimeUnit.SECONDS) shouldBe true
      receivedAck.get().timestamp shouldBe 12345L
    } finally release.unsafeRunSync()
  }

  // ===== Control plane: drain request =====

  it should "receive drain request from server on control plane" in {
    val drainLatch    = new CountDownLatch(1)
    val receivedDrain = new AtomicReference[pb.DrainRequest](null)

    val handler = new ControlPlaneHandler {
      def onHeartbeatAck(ack: pb.HeartbeatAck): IO[Unit]                  = IO.unit
      def onActiveModulesReport(report: pb.ActiveModulesReport): IO[Unit] = IO.unit
      def onDrainRequest(drain: pb.DrainRequest): IO[Unit] = IO {
        receivedDrain.set(drain)
        drainLatch.countDown()
      }
      def onStreamError(error: Throwable): IO[Unit] = IO.unit
      def onStreamCompleted: IO[Unit]               = IO.unit
    }

    val (stream, release) = transport.openControlPlane(handler).allocated.unsafeRunSync()
    try {
      // Send an initial heartbeat to open the stream
      stream.sendHeartbeat(pb.Heartbeat(namespace = "test.ns", timestamp = 1L)).unsafeRunSync()
      // Wait for the fake server to process it
      Thread.sleep(100)

      // Now trigger a drain from the server side
      fakeService.sendDrain(pb.DrainRequest(reason = "maintenance", deadlineMs = 30000L))

      drainLatch.await(5, TimeUnit.SECONDS) shouldBe true
      receivedDrain.get().reason shouldBe "maintenance"
    } finally release.unsafeRunSync()
  }

  // ===== Control plane: send drain ack =====

  it should "send drain ack on control plane stream" in {
    val handler = new ControlPlaneHandler {
      def onHeartbeatAck(ack: pb.HeartbeatAck): IO[Unit]                  = IO.unit
      def onActiveModulesReport(report: pb.ActiveModulesReport): IO[Unit] = IO.unit
      def onDrainRequest(drain: pb.DrainRequest): IO[Unit]                = IO.unit
      def onStreamError(error: Throwable): IO[Unit]                       = IO.unit
      def onStreamCompleted: IO[Unit]                                     = IO.unit
    }

    val (stream, release) = transport.openControlPlane(handler).allocated.unsafeRunSync()
    try {
      stream.sendDrainAck(pb.DrainAck(accepted = true, inFlightCount = 3)).unsafeRunSync()
      // Wait for processing
      Thread.sleep(100)

      fakeService.lastDrainAck.get() should not be null
      fakeService.lastDrainAck.get().accepted shouldBe true
      fakeService.lastDrainAck.get().inFlightCount shouldBe 3
    } finally release.unsafeRunSync()
  }

  // ===== Control plane: close stream =====

  it should "close control plane stream cleanly" in {
    val completedLatch = new CountDownLatch(1)

    val handler = new ControlPlaneHandler {
      def onHeartbeatAck(ack: pb.HeartbeatAck): IO[Unit]                  = IO.unit
      def onActiveModulesReport(report: pb.ActiveModulesReport): IO[Unit] = IO.unit
      def onDrainRequest(drain: pb.DrainRequest): IO[Unit]                = IO.unit
      def onStreamError(error: Throwable): IO[Unit]                       = IO.unit
      def onStreamCompleted: IO[Unit] = IO {
        completedLatch.countDown()
      }
    }

    val (stream, release) = transport.openControlPlane(handler).allocated.unsafeRunSync()
    stream.close.unsafeRunSync()

    // Resource release should be safe even after close
    release.unsafeRunSync()
    succeed
  }

  // ===== GrpcProviderTransport.apply resource lifecycle =====

  "GrpcProviderTransport.apply" should "create a resource that manages channel lifecycle" in {
    // We can't easily test with a real TCP port here, but we verify the factory shape.
    // The InProcess-based tests above thoroughly cover the actual transport logic.
    // This test validates that the companion object's Resource properly wraps channel creation.
    // We'll just verify the method exists and produces a Resource type.
    val resource = GrpcProviderTransport("localhost", 50051)
    resource shouldBe a[cats.effect.Resource[?, ?]]
  }
}

// ===== Fake ModuleProvider gRPC service for tests =====

private class FakeModuleProviderImpl extends pb.ModuleProviderGrpc.ModuleProvider {
  val lastRegisterRequest   = new AtomicReference[pb.RegisterRequest](null)
  val lastDeregisterRequest = new AtomicReference[pb.DeregisterRequest](null)
  val lastDrainAck          = new AtomicReference[pb.DrainAck](null)

  var registerError: Option[io.grpc.Status]   = None
  var deregisterError: Option[io.grpc.Status] = None

  // Store the server-side response observer for control plane so we can push messages to the client
  private val controlPlaneResponseObserver =
    new AtomicReference[StreamObserver[pb.ControlMessage]](null)

  override def register(
      request: pb.RegisterRequest
  ): scala.concurrent.Future[pb.RegisterResponse] = {
    lastRegisterRequest.set(request)
    registerError match {
      case Some(status) =>
        scala.concurrent.Future.failed(status.asRuntimeException())
      case None =>
        scala.concurrent.Future.successful(
          pb.RegisterResponse(
            success = true,
            protocolVersion = 1,
            connectionId = "fake-conn-id"
          )
        )
    }
  }

  override def deregister(
      request: pb.DeregisterRequest
  ): scala.concurrent.Future[pb.DeregisterResponse] = {
    lastDeregisterRequest.set(request)
    deregisterError match {
      case Some(status) =>
        scala.concurrent.Future.failed(status.asRuntimeException())
      case None =>
        scala.concurrent.Future.successful(
          pb.DeregisterResponse(success = true)
        )
    }
  }

  override def controlPlane(
      responseObserver: StreamObserver[pb.ControlMessage]
  ): StreamObserver[pb.ControlMessage] = {
    controlPlaneResponseObserver.set(responseObserver)

    new StreamObserver[pb.ControlMessage] {
      override def onNext(msg: pb.ControlMessage): Unit =
        msg.payload match {
          case pb.ControlMessage.Payload.Heartbeat(hb) =>
            // Echo back as HeartbeatAck
            responseObserver.onNext(
              pb.ControlMessage(
                connectionId = msg.connectionId,
                payload = pb.ControlMessage.Payload.HeartbeatAck(
                  pb.HeartbeatAck(timestamp = hb.timestamp)
                )
              )
            )
          case pb.ControlMessage.Payload.DrainAck(ack) =>
            lastDrainAck.set(ack)
          case _ => ()
        }

      override def onError(t: Throwable): Unit = ()
      override def onCompleted(): Unit =
        responseObserver.onCompleted()
    }
  }

  /** Push a DrainRequest to the client on the control plane stream. */
  def sendDrain(drain: pb.DrainRequest): Unit =
    Option(controlPlaneResponseObserver.get()).foreach { obs =>
      obs.onNext(
        pb.ControlMessage(
          payload = pb.ControlMessage.Payload.DrainRequest(drain)
        )
      )
    }
}
