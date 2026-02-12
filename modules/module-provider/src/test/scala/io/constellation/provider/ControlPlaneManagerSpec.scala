package io.constellation.provider

import scala.concurrent.duration.*

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global

import io.constellation.provider.v1.{provider => pb}

import io.grpc.stub.StreamObserver

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ControlPlaneManagerSpec extends AnyFlatSpec with Matchers {

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

  /** Thread-safe StreamObserver that records messages sent to it. */
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

  // ===== registerConnection =====

  "ControlPlaneManager.registerConnection" should "add connection in Registered state" in {
    val mgr = createManager()

    mgr.registerConnection("conn1", "ml.sentiment", "localhost:9999", "", Set("ml.sentiment.analyze"), 1)
      .unsafeRunSync()

    val conn = mgr.getConnection("conn1").unsafeRunSync()
    conn shouldBe defined
    conn.get.connectionId shouldBe "conn1"
    conn.get.namespace shouldBe "ml.sentiment"
    conn.get.executorUrl shouldBe "localhost:9999"
    conn.get.registeredModules shouldBe Set("ml.sentiment.analyze")
    conn.get.protocolVersion shouldBe 1
    conn.get.state shouldBe ConnectionState.Registered
    conn.get.controlPlaneEstablishedAt shouldBe None
    conn.get.lastHeartbeatAt shouldBe None
    conn.get.responseObserver shouldBe None
  }

  it should "set registeredAt timestamp" in {
    val mgr = createManager()

    val before = System.currentTimeMillis()
    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set.empty, 1).unsafeRunSync()
    val after = System.currentTimeMillis()

    val conn = mgr.getConnection("conn1").unsafeRunSync().get
    conn.registeredAt should be >= before
    conn.registeredAt should be <= after
  }

  // ===== activateControlPlane =====

  "ControlPlaneManager.activateControlPlane" should "transition from Registered to Active" in {
    val mgr      = createManager()
    val observer = new RecordingObserver

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set("ml.analyze"), 1).unsafeRunSync()

    val result = mgr.activateControlPlane("conn1", observer).unsafeRunSync()
    result shouldBe true

    val conn = mgr.getConnection("conn1").unsafeRunSync().get
    conn.state shouldBe ConnectionState.Active
    conn.controlPlaneEstablishedAt shouldBe defined
    conn.lastHeartbeatAt shouldBe defined
    conn.responseObserver shouldBe Some(observer)
  }

  it should "return false for unknown connection" in {
    val mgr      = createManager()
    val observer = new RecordingObserver

    val result = mgr.activateControlPlane("nonexistent", observer).unsafeRunSync()
    result shouldBe false
  }

  it should "not activate an already-Active connection" in {
    val mgr  = createManager()
    val obs1 = new RecordingObserver
    val obs2 = new RecordingObserver

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set.empty, 1).unsafeRunSync()
    mgr.activateControlPlane("conn1", obs1).unsafeRunSync() shouldBe true

    // Second activation should fail (already Active, not Registered)
    mgr.activateControlPlane("conn1", obs2).unsafeRunSync() shouldBe false

    // Original observer should still be stored
    val conn = mgr.getConnection("conn1").unsafeRunSync().get
    conn.responseObserver shouldBe Some(obs1)
  }

  // ===== recordHeartbeat =====

  "ControlPlaneManager.recordHeartbeat" should "update lastHeartbeatAt for Active connection" in {
    val mgr      = createManager()
    val observer = new RecordingObserver

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set.empty, 1).unsafeRunSync()
    mgr.activateControlPlane("conn1", observer).unsafeRunSync()

    val connBefore = mgr.getConnection("conn1").unsafeRunSync().get
    val hbBefore   = connBefore.lastHeartbeatAt.get

    Thread.sleep(10) // ensure time passes
    mgr.recordHeartbeat("conn1").unsafeRunSync()

    val connAfter = mgr.getConnection("conn1").unsafeRunSync().get
    connAfter.lastHeartbeatAt.get should be >= hbBefore
  }

  it should "be a no-op for non-Active connection" in {
    val mgr = createManager()

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set.empty, 1).unsafeRunSync()
    mgr.recordHeartbeat("conn1").unsafeRunSync() // Registered, not Active

    val conn = mgr.getConnection("conn1").unsafeRunSync().get
    conn.lastHeartbeatAt shouldBe None
  }

  // ===== deactivateConnection =====

  "ControlPlaneManager.deactivateConnection" should "transition to Disconnected" in {
    val mgr      = createManager()
    val observer = new RecordingObserver

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set("ml.analyze"), 1).unsafeRunSync()
    mgr.activateControlPlane("conn1", observer).unsafeRunSync()
    mgr.deactivateConnection("conn1").unsafeRunSync()

    val conn = mgr.getConnection("conn1").unsafeRunSync().get
    conn.state shouldBe ConnectionState.Disconnected
    conn.responseObserver shouldBe None
  }

  // ===== getConnectionByNamespace =====

  "ControlPlaneManager.getConnectionByNamespace" should "find connection by namespace" in {
    val mgr = createManager()

    mgr.registerConnection("conn1", "ml.sentiment", "localhost:9999", "", Set.empty, 1).unsafeRunSync()

    mgr.getConnectionByNamespace("ml.sentiment").unsafeRunSync() shouldBe defined
    mgr.getConnectionByNamespace("other").unsafeRunSync() shouldBe None
  }

  // ===== removeConnection =====

  "ControlPlaneManager.removeConnection" should "remove connection from state" in {
    val mgr = createManager()

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set.empty, 1).unsafeRunSync()
    mgr.removeConnection("conn1").unsafeRunSync()

    mgr.getConnection("conn1").unsafeRunSync() shouldBe None
  }

  // ===== updateModules =====

  "ControlPlaneManager.updateModules" should "update registered modules" in {
    val mgr = createManager()

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set("ml.a", "ml.b"), 1).unsafeRunSync()
    mgr.updateModules("conn1", Set("ml.a")).unsafeRunSync()

    val conn = mgr.getConnection("conn1").unsafeRunSync().get
    conn.registeredModules shouldBe Set("ml.a")
  }

  // ===== getAllConnections =====

  "ControlPlaneManager.getAllConnections" should "return all connections" in {
    val mgr = createManager()

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set.empty, 1).unsafeRunSync()
    mgr.registerConnection("conn2", "text", "localhost:8888", "", Set.empty, 1).unsafeRunSync()

    val conns = mgr.getAllConnections.unsafeRunSync()
    conns should have size 2
    conns.map(_.connectionId).toSet shouldBe Set("conn1", "conn2")
  }

  // ===== Multiple connections coexist independently =====

  "ControlPlaneManager" should "handle multiple connections independently" in {
    val mgr = createManager()
    val obs1 = new RecordingObserver
    val obs2 = new RecordingObserver

    mgr.registerConnection("conn1", "ml", "localhost:9999", "", Set("ml.a"), 1).unsafeRunSync()
    mgr.registerConnection("conn2", "text", "localhost:8888", "", Set("text.b"), 1).unsafeRunSync()

    mgr.activateControlPlane("conn1", obs1).unsafeRunSync()
    mgr.activateControlPlane("conn2", obs2).unsafeRunSync()

    // Deactivate conn1 — conn2 should be unaffected
    mgr.deactivateConnection("conn1").unsafeRunSync()

    val c1 = mgr.getConnection("conn1").unsafeRunSync().get
    val c2 = mgr.getConnection("conn2").unsafeRunSync().get
    c1.state shouldBe ConnectionState.Disconnected
    c2.state shouldBe ConnectionState.Active
    c2.responseObserver shouldBe Some(obs2)
  }

  // ===== Edge case: operations on non-existent connections =====

  "ControlPlaneManager.deactivateConnection" should "be a no-op for non-existent connection" in {
    val mgr = createManager()

    noException should be thrownBy mgr.deactivateConnection("nonexistent").unsafeRunSync()
    mgr.getConnection("nonexistent").unsafeRunSync() shouldBe None
  }

  "ControlPlaneManager.updateModules" should "be a no-op for non-existent connection" in {
    val mgr = createManager()

    noException should be thrownBy mgr.updateModules("nonexistent", Set("ml.a")).unsafeRunSync()
    mgr.getConnection("nonexistent").unsafeRunSync() shouldBe None
  }

  "ControlPlaneManager.recordHeartbeat" should "be a no-op for unknown connection" in {
    val mgr = createManager()

    noException should be thrownBy mgr.recordHeartbeat("nonexistent").unsafeRunSync()
    mgr.getConnection("nonexistent").unsafeRunSync() shouldBe None
  }
}
