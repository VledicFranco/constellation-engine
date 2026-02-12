package io.constellation.provider

import scala.concurrent.duration.*

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}

import io.constellation.provider.v1.provider as pb

import io.grpc.stub.StreamObserver
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DrainSpec extends AnyFlatSpec with Matchers {

  private val defaultConfig = ProviderManagerConfig(
    grpcPort = 0,
    heartbeatInterval = 5.seconds,
    heartbeatTimeout = 15.seconds,
    controlPlaneRequiredTimeout = 30.seconds,
    activeModulesReportInterval = 30.seconds
  )

  private def createManager(
      config: ProviderManagerConfig = defaultConfig,
      onDead: String => IO[Unit] = _ => IO.unit
  ): ControlPlaneManager = {
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    new ControlPlaneManager(state, config, onDead)
  }

  private class RecordingObserver extends StreamObserver[pb.ControlMessage] {
    private val _messages = new java.util.concurrent.ConcurrentLinkedQueue[pb.ControlMessage]()
    @volatile var completed: Boolean = false

    def messages: List[pb.ControlMessage] = {
      import scala.jdk.CollectionConverters.*
      _messages.asScala.toList
    }

    override def onNext(value: pb.ControlMessage): Unit =
      _messages.add(value)
    override def onError(t: Throwable): Unit = ()
    override def onCompleted(): Unit =
      completed = true
  }

  // ===== drainConnection =====

  "ControlPlaneManager.drainConnection" should "send DrainRequest to active connection" in {
    val mgr      = createManager()
    val observer = new RecordingObserver

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set("ml.a"), 1).unsafeRunSync()
    mgr.activateControlPlane("conn1", observer).unsafeRunSync()

    val result = mgr.drainConnection("conn1", "rolling update", 30000L).unsafeRunSync()
    result shouldBe true

    val drainMsgs = observer.messages.filter(_.payload.isDrainRequest)
    drainMsgs should have size 1
    drainMsgs.head.payload.drainRequest.get.reason shouldBe "rolling update"
    drainMsgs.head.payload.drainRequest.get.deadlineMs shouldBe 30000L
  }

  it should "return false for unknown connection" in {
    val mgr = createManager()

    val result = mgr.drainConnection("nonexistent", "test", 30000L).unsafeRunSync()
    result shouldBe false
  }

  it should "return false for non-active connection" in {
    val mgr = createManager()

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set.empty, 1).unsafeRunSync()

    val result = mgr.drainConnection("conn1", "test", 30000L).unsafeRunSync()
    result shouldBe false
  }

  // ===== recordDrainAck =====

  "ControlPlaneManager.recordDrainAck" should "transition Active to Draining" in {
    val mgr      = createManager()
    val observer = new RecordingObserver

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set.empty, 1).unsafeRunSync()
    mgr.activateControlPlane("conn1", observer).unsafeRunSync()

    mgr.recordDrainAck("conn1", pb.DrainAck(accepted = true, inFlightCount = 3)).unsafeRunSync()

    val conn = mgr.getConnection("conn1").unsafeRunSync().get
    conn.state shouldBe ConnectionState.Draining
  }

  it should "be a no-op for non-active connection" in {
    val mgr = createManager()

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set.empty, 1).unsafeRunSync()

    mgr.recordDrainAck("conn1", pb.DrainAck(accepted = true, inFlightCount = 0)).unsafeRunSync()

    val conn = mgr.getConnection("conn1").unsafeRunSync().get
    conn.state shouldBe ConnectionState.Registered // unchanged
  }
}
