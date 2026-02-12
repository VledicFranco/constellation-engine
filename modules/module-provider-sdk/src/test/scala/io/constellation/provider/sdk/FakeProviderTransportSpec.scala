package io.constellation.provider.sdk

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.provider.v1.provider as pb

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class FakeProviderTransportSpec extends AnyFlatSpec with Matchers {

  // ===== Register =====

  "FakeProviderTransport" should "record register calls and return configured response" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()

    val request = pb.RegisterRequest(namespace = "ml", executorUrl = "localhost:9999")
    val resp    = transport.register(request).unsafeRunSync()

    resp.success shouldBe true
    resp.connectionId shouldBe "fake-conn-id"

    import scala.jdk.CollectionConverters.*
    transport.registerCalls.asScala.toList shouldBe List(request)
  }

  it should "return updated register response after setRegisterResponse" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()

    val failResp = pb.RegisterResponse(success = false, connectionId = "")
    transport.setRegisterResponse(failResp).unsafeRunSync()

    val resp = transport.register(pb.RegisterRequest(namespace = "ml")).unsafeRunSync()
    resp.success shouldBe false
  }

  // ===== Deregister =====

  it should "record deregister calls" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()

    val request = pb.DeregisterRequest(namespace = "ml", moduleNames = Seq("analyze"))
    transport.deregister(request).unsafeRunSync()

    import scala.jdk.CollectionConverters.*
    transport.deregisterCalls.asScala.toList shouldBe List(request)
  }

  // ===== Control Plane =====

  it should "open and close control plane" in {
    val transport = FakeProviderTransport.create.unsafeRunSync()

    val handler = new ControlPlaneHandler {
      def onHeartbeatAck(ack: pb.HeartbeatAck): IO[Unit]                  = IO.unit
      def onActiveModulesReport(report: pb.ActiveModulesReport): IO[Unit] = IO.unit
      def onDrainRequest(drain: pb.DrainRequest): IO[Unit]                = IO.unit
      def onStreamError(error: Throwable): IO[Unit]                       = IO.unit
      def onStreamCompleted: IO[Unit]                                     = IO.unit
    }

    transport.controlPlaneOpened shouldBe false

    transport
      .openControlPlane(handler)
      .use { stream =>
        IO {
          transport.controlPlaneOpened shouldBe true
          transport.controlPlaneClosed shouldBe false
        }
      }
      .unsafeRunSync()

    transport.controlPlaneClosed shouldBe true
  }

  // ===== FakeExecutorServerFactory =====

  "FakeExecutorServerFactory" should "register handler and track lifecycle" in {
    val factory = new FakeExecutorServerFactory

    factory.started shouldBe false

    val resp = factory
      .create(
        req =>
          IO.pure(
            pb.ExecuteResponse(result =
              pb.ExecuteResponse.Result.OutputData(
                com.google.protobuf.ByteString.copyFromUtf8("ok")
              )
            )
          ),
        9091
      )
      .use { port =>
        IO {
          port shouldBe 9091
          factory.started shouldBe true
          factory.stopped shouldBe false
        } >> factory.execute(pb.ExecuteRequest(moduleName = "test")).map { resp =>
          resp.result.isOutputData shouldBe true
        }
      }
      .unsafeRunSync()

    factory.stopped shouldBe true
  }
}
