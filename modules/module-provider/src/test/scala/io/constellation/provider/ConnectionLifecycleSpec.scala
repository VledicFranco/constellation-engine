package io.constellation.provider

import scala.concurrent.duration.*

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}
import cats.implicits.*

import io.constellation.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.{FunctionRegistry, InMemoryFunctionRegistry}
import io.constellation.provider.v1.provider as pb

import io.grpc.stub.StreamObserver
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConnectionLifecycleSpec extends AnyFlatSpec with Matchers {

  private val stringSchema = pb.TypeSchema(
    pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))
  )

  private def mkDecl(name: String): pb.ModuleDeclaration =
    pb.ModuleDeclaration(
      name = name,
      inputSchema = Some(
        pb.TypeSchema(
          pb.TypeSchema.Type.Record(
            pb.RecordType(
              Map(
                "text" -> stringSchema
              )
            )
          )
        )
      ),
      outputSchema = Some(
        pb.TypeSchema(
          pb.TypeSchema.Type.Record(
            pb.RecordType(
              Map(
                "result" -> stringSchema
              )
            )
          )
        )
      ),
      version = "1.0.0",
      description = s"Test module $name"
    )

  private def mkRequest(namespace: String, modules: Seq[pb.ModuleDeclaration]): pb.RegisterRequest =
    pb.RegisterRequest(
      namespace = namespace,
      modules = modules,
      protocolVersion = 1,
      executorUrl = "localhost:9999"
    )

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

  /** Create a full ModuleProviderManager (without gRPC server) with short timeouts for testing. */
  private def createTestSetup(
      controlPlaneTimeout: FiniteDuration = 200.millis,
      heartbeatTimeout: FiniteDuration = 200.millis,
      reportInterval: FiniteDuration = 100.millis
  ): (ModuleProviderManager, ControlPlaneManager, FunctionRegistry) = {
    val testFunctionRegistry = new InMemoryFunctionRegistry
    val constellation        = ConstellationImpl.init.unsafeRunSync()

    val compiler = new LangCompiler {
      def compile(source: String, dagName: String) =
        Left(List(io.constellation.lang.ast.CompileError.UndefinedFunction("test", None)))
      def compileToIR(source: String, dagName: String) =
        Left(List(io.constellation.lang.ast.CompileError.UndefinedFunction("test", None)))
      def functionRegistry: FunctionRegistry = testFunctionRegistry
    }

    val config = ProviderManagerConfig(
      grpcPort = 0,
      heartbeatTimeout = heartbeatTimeout,
      controlPlaneRequiredTimeout = controlPlaneTimeout,
      activeModulesReportInterval = reportInterval,
      reservedNamespaces = Set("stdlib")
    )

    val state    = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val deregRef = Ref.of[IO, String => IO[Unit]](_ => IO.unit).unsafeRunSync()
    val cp       = new ControlPlaneManager(state, config, connId => deregRef.get.flatMap(_(connId)))
    val cache    = new GrpcChannelCache
    val manager =
      new ModuleProviderManager(constellation, compiler, config, cp, JsonCValueSerializer, cache)
    deregRef.set(connId => manager.deregisterAllForConnection(connId)).unsafeRunSync()

    (manager, cp, testFunctionRegistry)
  }

  // ===== Register → no control plane within timeout → auto-deregistered =====

  "Connection lifecycle" should "auto-deregister when control plane is not established within timeout" in {
    val (manager, cp, registry) = createTestSetup(controlPlaneTimeout = 150.millis)

    // Register a module
    val response = manager
      .handleRegister(
        mkRequest("ml.sentiment", Seq(mkDecl("analyze"))),
        "conn1"
      )
      .unsafeRunSync()
    response.success shouldBe true

    // Verify module is registered
    registry.lookupQualified("ml.sentiment.analyze") shouldBe defined

    // Start liveness monitor; wait >1s so the second check fires (first check at t≈0 sees fresh connection)
    val fiber = cp.startLivenessMonitor.use(_ => IO.sleep(1500.millis)).start.unsafeRunSync()
    fiber.joinWithNever.unsafeRunSync()

    // Module should be deregistered
    registry.lookupQualified("ml.sentiment.analyze") shouldBe None
    cp.getConnection("conn1").unsafeRunSync() shouldBe None
  }

  // ===== Register → control plane activated → heartbeat stops → auto-deregistered =====

  it should "auto-deregister when heartbeat stops" in {
    val (manager, cp, registry) = createTestSetup(
      controlPlaneTimeout = 5.seconds,
      heartbeatTimeout = 150.millis
    )
    val observer = new RecordingObserver

    // Register and activate control plane
    manager.handleRegister(mkRequest("ml", Seq(mkDecl("analyze"))), "conn1").unsafeRunSync()
    cp.activateControlPlane("conn1", observer).unsafeRunSync() shouldBe true

    // Verify module is registered
    registry.lookupQualified("ml.analyze") shouldBe defined

    // Start liveness monitor; wait >1s so the second check fires after heartbeat timeout
    val fiber = cp.startLivenessMonitor.use(_ => IO.sleep(1500.millis)).start.unsafeRunSync()
    fiber.joinWithNever.unsafeRunSync()

    // Module should be deregistered
    registry.lookupQualified("ml.analyze") shouldBe None
    cp.getConnection("conn1").unsafeRunSync() shouldBe None
  }

  // ===== Register → control plane activated → heartbeat continues → stays alive =====

  it should "keep connection alive while heartbeats continue" in {
    val (manager, cp, registry) = createTestSetup(
      controlPlaneTimeout = 5.seconds,
      heartbeatTimeout = 200.millis
    )
    val observer = new RecordingObserver

    // Register and activate
    manager.handleRegister(mkRequest("ml", Seq(mkDecl("analyze"))), "conn1").unsafeRunSync()
    cp.activateControlPlane("conn1", observer).unsafeRunSync()

    // Start liveness monitor + keep heartbeating through multiple check cycles
    val monitorFiber = cp.startLivenessMonitor.use(_ => IO.sleep(2500.millis)).start.unsafeRunSync()

    // Send heartbeats every 50ms for 2500ms
    val heartbeatFiber =
      (IO.sleep(50.millis) >> cp.recordHeartbeat("conn1")).replicateA(50).start.unsafeRunSync()

    monitorFiber.joinWithNever.unsafeRunSync()
    heartbeatFiber.joinWithNever.unsafeRunSync()

    // Connection should still be alive
    registry.lookupQualified("ml.analyze") shouldBe defined
    val conn = cp.getConnection("conn1").unsafeRunSync()
    conn shouldBe defined
    conn.get.state shouldBe ConnectionState.Active
  }

  // ===== Connection break → all modules cleaned up =====

  it should "clean up all modules when connection is deregistered" in {
    val (manager, cp, registry) = createTestSetup()

    // Register multiple modules
    manager
      .handleRegister(
        mkRequest("ml", Seq(mkDecl("analyze"), mkDecl("classify"))),
        "conn1"
      )
      .unsafeRunSync()

    registry.lookupQualified("ml.analyze") shouldBe defined
    registry.lookupQualified("ml.classify") shouldBe defined

    // Simulate connection break
    manager.deregisterAllForConnection("conn1").unsafeRunSync()

    registry.lookupQualified("ml.analyze") shouldBe None
    registry.lookupQualified("ml.classify") shouldBe None
    cp.getConnection("conn1").unsafeRunSync() shouldBe None
  }

  // ===== Two providers → one dies → other unaffected =====

  it should "not affect other providers when one dies" in {
    val (manager, cp, registry) = createTestSetup(
      controlPlaneTimeout = 5.seconds,
      heartbeatTimeout = 150.millis
    )
    val obs1 = new RecordingObserver
    val obs2 = new RecordingObserver

    // Register two providers in different namespaces
    manager.handleRegister(mkRequest("ml", Seq(mkDecl("analyze"))), "conn1").unsafeRunSync()
    manager.handleRegister(mkRequest("text", Seq(mkDecl("uppercase"))), "conn2").unsafeRunSync()

    // Both activate control planes
    cp.activateControlPlane("conn1", obs1).unsafeRunSync()
    cp.activateControlPlane("conn2", obs2).unsafeRunSync()

    // conn1 stops heartbeating, conn2 keeps going; wait >1s for second liveness check
    val monitorFiber = cp.startLivenessMonitor.use(_ => IO.sleep(1500.millis)).start.unsafeRunSync()
    val hbFiber =
      (IO.sleep(50.millis) >> cp.recordHeartbeat("conn2")).replicateA(30).start.unsafeRunSync()

    monitorFiber.joinWithNever.unsafeRunSync()
    hbFiber.joinWithNever.unsafeRunSync()

    // conn1's modules should be gone
    registry.lookupQualified("ml.analyze") shouldBe None
    cp.getConnection("conn1").unsafeRunSync() shouldBe None

    // conn2 should be fine
    registry.lookupQualified("text.uppercase") shouldBe defined
    val c2 = cp.getConnection("conn2").unsafeRunSync()
    c2 shouldBe defined
    c2.get.state shouldBe ConnectionState.Active
  }

  // ===== ActiveModulesReport sent periodically =====

  it should "send ActiveModulesReport to connected providers" in {
    val (manager, cp, _) = createTestSetup(
      controlPlaneTimeout = 5.seconds,
      heartbeatTimeout = 5.seconds,
      reportInterval = 100.millis
    )
    val observer = new RecordingObserver

    // Register and activate
    manager.handleRegister(mkRequest("ml", Seq(mkDecl("analyze"))), "conn1").unsafeRunSync()
    cp.activateControlPlane("conn1", observer).unsafeRunSync()

    // Start reporter for a short time
    val fiber = cp.startActiveModulesReporter.use(_ => IO.sleep(350.millis)).start.unsafeRunSync()
    fiber.joinWithNever.unsafeRunSync()

    // Should have received at least 2 reports
    val reports = observer.messages.filter(_.payload.isActiveModulesReport)
    reports.size should be >= 2

    // Reports should contain the module name (short name, not qualified)
    val firstReport = reports.head.payload.activeModulesReport.get
    firstReport.activeModules should contain("analyze")

    // Reports should include connection_id
    reports.head.connectionId shouldBe "conn1"
  }

  // ===== Register returns connection_id =====

  it should "return connection_id in RegisterResponse" in {
    val (manager, _, _) = createTestSetup()

    val response = manager
      .handleRegister(
        mkRequest("ml", Seq(mkDecl("test"))),
        "my-conn-id"
      )
      .unsafeRunSync()

    response.connectionId shouldBe "my-conn-id"
  }

  // ===== Deregister with connection_id =====

  it should "accept deregister with correct connection_id" in {
    val (manager, _, registry) = createTestSetup()

    manager.handleRegister(mkRequest("ml", Seq(mkDecl("analyze"))), "conn1").unsafeRunSync()

    val response = manager
      .handleDeregister(
        pb.DeregisterRequest(
          namespace = "ml",
          moduleNames = Seq("analyze"),
          connectionId = "conn1"
        ),
        "conn1"
      )
      .unsafeRunSync()

    response.success shouldBe true
    registry.lookupQualified("ml.analyze") shouldBe None
  }

  it should "reject deregister with wrong connection_id" in {
    val (manager, _, registry) = createTestSetup()

    manager.handleRegister(mkRequest("ml", Seq(mkDecl("analyze"))), "conn1").unsafeRunSync()

    val response = manager
      .handleDeregister(
        pb.DeregisterRequest(
          namespace = "ml",
          moduleNames = Seq("analyze"),
          connectionId = "wrong"
        ),
        "wrong"
      )
      .unsafeRunSync()

    response.success shouldBe false
    // Module should still be registered
    registry.lookupQualified("ml.analyze") shouldBe defined
  }
}
