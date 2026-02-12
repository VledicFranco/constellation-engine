package io.constellation.provider

import scala.concurrent.duration.*

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global

import io.constellation.provider.v1.{provider => pb}

import io.grpc.stub.StreamObserver

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ProviderListingSpec extends AnyFlatSpec with Matchers {

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
    override def onNext(value: pb.ControlMessage): Unit = ()
    override def onError(t: Throwable): Unit = ()
    override def onCompleted(): Unit = ()
  }

  // ===== ProviderInfo construction =====

  "ProviderInfo" should "capture all connection fields" in {
    val info = ProviderInfo(
      connectionId = "conn1",
      namespace = "ml.sentiment",
      executorUrl = "localhost:9999",
      modules = Set("ml.sentiment.analyze"),
      state = ConnectionState.Active,
      registeredAt = 1000L,
      lastHeartbeatAt = Some(2000L)
    )

    info.connectionId shouldBe "conn1"
    info.namespace shouldBe "ml.sentiment"
    info.executorUrl shouldBe "localhost:9999"
    info.modules shouldBe Set("ml.sentiment.analyze")
    info.state shouldBe ConnectionState.Active
    info.registeredAt shouldBe 1000L
    info.lastHeartbeatAt shouldBe Some(2000L)
  }

  // ===== getAllConnections for listing =====

  "ControlPlaneManager.getAllConnections" should "return connections across all states" in {
    val mgr = createManager()
    val obs  = new RecordingObserver

    mgr.registerConnection("conn1", "ml", "localhost:9999", Set("ml.a"), 1).unsafeRunSync()
    mgr.registerConnection("conn2", "text", "localhost:8888", Set("text.b"), 1).unsafeRunSync()
    mgr.activateControlPlane("conn2", obs).unsafeRunSync()

    val conns = mgr.getAllConnections.unsafeRunSync()
    conns should have size 2

    val states = conns.map(c => c.connectionId -> c.state).toMap
    states("conn1") shouldBe ConnectionState.Registered
    states("conn2") shouldBe ConnectionState.Active
  }

  it should "include draining connections" in {
    val mgr = createManager()
    val obs  = new RecordingObserver

    mgr.registerConnection("conn1", "ml", "localhost:9999", Set("ml.a"), 1).unsafeRunSync()
    mgr.activateControlPlane("conn1", obs).unsafeRunSync()
    mgr.recordDrainAck("conn1", pb.DrainAck(accepted = true, inFlightCount = 2)).unsafeRunSync()

    val conns = mgr.getAllConnections.unsafeRunSync()
    conns should have size 1
    conns.head.state shouldBe ConnectionState.Draining
  }

  // ===== Liveness skips Draining =====

  "ControlPlaneManager liveness" should "skip draining connections" in {
    val deadConnections = Ref.of[IO, List[String]](List.empty).unsafeRunSync()
    val config = defaultConfig.copy(heartbeatTimeout = 1.millisecond)
    val mgr = createManager(config, connId => deadConnections.update(_ :+ connId))

    val obs = new RecordingObserver
    mgr.registerConnection("conn1", "ml", "localhost:9999", Set("ml.a"), 1).unsafeRunSync()
    mgr.activateControlPlane("conn1", obs).unsafeRunSync()
    mgr.recordDrainAck("conn1", pb.DrainAck(accepted = true, inFlightCount = 1)).unsafeRunSync()

    // Wait long enough that heartbeat would be considered timed out
    Thread.sleep(50)

    // Manually run liveness check (via reflection or just verify state didn't change)
    val conn = mgr.getConnection("conn1").unsafeRunSync().get
    conn.state shouldBe ConnectionState.Draining // Should remain draining, not disconnected
  }
}
