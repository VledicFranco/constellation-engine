package io.constellation.provider.sdk

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.CType
import io.constellation.provider.v1.provider as pb
import io.constellation.provider.{CValueSerializer, JsonCValueSerializer}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SdkGroupIdSpec extends AnyFlatSpec with Matchers {

  "SdkConfig" should "have groupId default to None" in {
    val config = SdkConfig()
    config.groupId shouldBe None
  }

  it should "accept a groupId" in {
    val config = SdkConfig(groupId = Some("my-group"))
    config.groupId shouldBe Some("my-group")
  }

  "InstanceConnection" should "include groupId in RegisterRequest" in {
    val config                       = SdkConfig(executorPort = 9091, groupId = Some("test-group"))
    val serializer: CValueSerializer = JsonCValueSerializer

    // Use a recording transport to capture the RegisterRequest
    var capturedRequest: Option[pb.RegisterRequest] = None
    val transport = new ProviderTransport {
      def register(request: pb.RegisterRequest): IO[pb.RegisterResponse] = {
        capturedRequest = Some(request)
        IO.pure(pb.RegisterResponse(success = true, connectionId = "conn1"))
      }
      def deregister(request: pb.DeregisterRequest): IO[pb.DeregisterResponse] =
        IO.pure(pb.DeregisterResponse(success = true))
      def openControlPlane(
          handler: ControlPlaneHandler
      ): cats.effect.Resource[IO, ControlPlaneStream] =
        cats.effect.Resource.pure(new ControlPlaneStream {
          def sendHeartbeat(hb: pb.Heartbeat): IO[Unit] = IO.unit
          def sendDrainAck(ack: pb.DrainAck): IO[Unit]  = IO.unit
          def close: IO[Unit]                           = IO.unit
        })
    }

    val modulesRef = cats.effect.Ref.unsafe[IO, List[ModuleDefinition]](
      List(
        ModuleDefinition("echo", CType.CString, CType.CString, "1.0.0", "Echo", v => IO.pure(v))
      )
    )

    val conn = new InstanceConnection(
      instanceAddress = "localhost",
      namespace = "test",
      transport = transport,
      config = config,
      modulesRef = modulesRef,
      serializer = serializer
    )

    conn.connect.unsafeRunSync()

    capturedRequest shouldBe defined
    capturedRequest.get.groupId shouldBe "test-group"
  }

  it should "send empty groupId when not configured" in {
    val config                       = SdkConfig(executorPort = 9091)
    val serializer: CValueSerializer = JsonCValueSerializer

    var capturedRequest: Option[pb.RegisterRequest] = None
    val transport = new ProviderTransport {
      def register(request: pb.RegisterRequest): IO[pb.RegisterResponse] = {
        capturedRequest = Some(request)
        IO.pure(pb.RegisterResponse(success = true, connectionId = "conn1"))
      }
      def deregister(request: pb.DeregisterRequest): IO[pb.DeregisterResponse] =
        IO.pure(pb.DeregisterResponse(success = true))
      def openControlPlane(
          handler: ControlPlaneHandler
      ): cats.effect.Resource[IO, ControlPlaneStream] =
        cats.effect.Resource.pure(new ControlPlaneStream {
          def sendHeartbeat(hb: pb.Heartbeat): IO[Unit] = IO.unit
          def sendDrainAck(ack: pb.DrainAck): IO[Unit]  = IO.unit
          def close: IO[Unit]                           = IO.unit
        })
    }

    val modulesRef = cats.effect.Ref.unsafe[IO, List[ModuleDefinition]](
      List(
        ModuleDefinition("echo", CType.CString, CType.CString, "1.0.0", "Echo", v => IO.pure(v))
      )
    )

    val conn = new InstanceConnection(
      instanceAddress = "localhost",
      namespace = "test",
      transport = transport,
      config = config,
      modulesRef = modulesRef,
      serializer = serializer
    )

    conn.connect.unsafeRunSync()

    capturedRequest shouldBe defined
    capturedRequest.get.groupId shouldBe ""
  }
}
