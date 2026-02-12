package io.constellation.provider

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global

import io.constellation.lang.semantic.{FunctionRegistry, InMemoryFunctionRegistry, FunctionSignature, SemanticType}
import io.constellation.provider.v1.{provider => pb}

import io.grpc.stub.StreamObserver

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ProviderGroupSpec extends AnyFlatSpec with Matchers {

  private val stringSchema = pb.TypeSchema(
    pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))
  )

  private def mkDecl(name: String): pb.ModuleDeclaration =
    pb.ModuleDeclaration(
      name = name,
      inputSchema = Some(stringSchema),
      outputSchema = Some(stringSchema),
      version = "1.0.0",
      description = s"Test module $name"
    )

  private def mkRequest(
      namespace: String,
      modules: Seq[pb.ModuleDeclaration],
      executorUrl: String = "localhost:9090",
      groupId: String = ""
  ): pb.RegisterRequest =
    pb.RegisterRequest(
      namespace = namespace,
      modules = modules,
      protocolVersion = 1,
      executorUrl = executorUrl,
      groupId = groupId
    )

  // ===== SchemaValidator Group Tests =====

  "SchemaValidator" should "allow same namespace from same group_id" in {
    val results = SchemaValidator.validate(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze")), groupId = "ml-group"),
      FunctionRegistry.empty,
      Map("ml.sentiment" -> "conn1"),
      Map("ml.sentiment" -> "ml-group"),
      "conn2",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Accepted]
  }

  it should "reject same namespace with different group_id" in {
    val results = SchemaValidator.validate(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze")), groupId = "other-group"),
      FunctionRegistry.empty,
      Map("ml.sentiment" -> "conn1"),
      Map("ml.sentiment" -> "ml-group"),
      "conn2",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include("different provider group")
  }

  it should "reject solo provider joining existing namespace" in {
    val results = SchemaValidator.validate(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze")), groupId = ""),
      FunctionRegistry.empty,
      Map("ml.sentiment" -> "conn1"),
      Map.empty, // Existing is solo (empty groupId)
      "conn2",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include("another provider")
  }

  it should "reject group provider joining solo namespace" in {
    val results = SchemaValidator.validate(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze")), groupId = "ml-group"),
      FunctionRegistry.empty,
      Map("ml.sentiment" -> "conn1"),
      Map.empty, // Existing is solo (no groupId in map)
      "conn2",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include("another provider")
  }

  // ===== ControlPlaneManager Group Membership Tests =====

  "ControlPlaneManager.isLastGroupMember" should "return true for solo provider" in {
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val mgr = new ControlPlaneManager(state, ProviderManagerConfig(), _ => IO.unit)

    mgr.registerConnection("conn1", "ml", "localhost:9090", "", Set("ml.a"), 1).unsafeRunSync()

    mgr.isLastGroupMember("conn1").unsafeRunSync() shouldBe true
  }

  it should "return false when other group members exist" in {
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val mgr = new ControlPlaneManager(state, ProviderManagerConfig(), _ => IO.unit)

    mgr.registerConnection("conn1", "ml", "host1:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()
    mgr.registerConnection("conn2", "ml", "host2:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()

    // Activate both so they're not just Registered
    val obs = new StreamObserver[pb.ControlMessage] {
      override def onNext(msg: pb.ControlMessage): Unit = ()
      override def onError(t: Throwable): Unit = ()
      override def onCompleted(): Unit = ()
    }
    mgr.activateControlPlane("conn1", obs).unsafeRunSync()
    mgr.activateControlPlane("conn2", obs).unsafeRunSync()

    mgr.isLastGroupMember("conn1").unsafeRunSync() shouldBe false
    mgr.isLastGroupMember("conn2").unsafeRunSync() shouldBe false
  }

  it should "return true when only remaining group member" in {
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val mgr = new ControlPlaneManager(state, ProviderManagerConfig(), _ => IO.unit)

    mgr.registerConnection("conn1", "ml", "host1:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()
    mgr.registerConnection("conn2", "ml", "host2:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()

    // Disconnect conn2
    mgr.deactivateConnection("conn2").unsafeRunSync()

    // conn1 is now the last active member
    mgr.isLastGroupMember("conn1").unsafeRunSync() shouldBe true
  }

  it should "return true for unknown connection" in {
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val mgr = new ControlPlaneManager(state, ProviderManagerConfig(), _ => IO.unit)

    mgr.isLastGroupMember("nonexistent").unsafeRunSync() shouldBe true
  }

  "ControlPlaneManager.getConnectionsByNamespace" should "return all connections for a namespace" in {
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val mgr = new ControlPlaneManager(state, ProviderManagerConfig(), _ => IO.unit)

    mgr.registerConnection("conn1", "ml", "host1:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()
    mgr.registerConnection("conn2", "ml", "host2:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()
    mgr.registerConnection("conn3", "text", "host3:9090", "", Set("text.b"), 1).unsafeRunSync()

    val mlConns = mgr.getConnectionsByNamespace("ml").unsafeRunSync()
    mlConns should have size 2
    mlConns.map(_.connectionId).toSet shouldBe Set("conn1", "conn2")
  }

  // ===== SDK Group ID Tests =====

  "SdkConfig" should "have groupId default to None" in {
    import io.constellation.provider.sdk.SdkConfig
    val config = SdkConfig()
    config.groupId shouldBe None
  }

  it should "accept a groupId" in {
    import io.constellation.provider.sdk.SdkConfig
    val config = SdkConfig(groupId = Some("my-group"))
    config.groupId shouldBe Some("my-group")
  }

  "InstanceConnection" should "include groupId in RegisterRequest" in {
    import io.constellation.provider.sdk.*
    import io.constellation.{CType, CValue}

    val config = SdkConfig(executorPort = 9091, groupId = Some("test-group"))
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
      def openControlPlane(handler: ControlPlaneHandler): cats.effect.Resource[IO, ControlPlaneStream] =
        cats.effect.Resource.pure(new ControlPlaneStream {
          def sendHeartbeat(hb: pb.Heartbeat): IO[Unit] = IO.unit
          def sendDrainAck(ack: pb.DrainAck): IO[Unit] = IO.unit
          def close: IO[Unit] = IO.unit
        })
    }

    val modulesRef = cats.effect.Ref.unsafe[IO, List[ModuleDefinition]](List(
      ModuleDefinition("echo", CType.CString, CType.CString, "1.0.0", "Echo", v => IO.pure(v))
    ))

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
    import io.constellation.provider.sdk.*
    import io.constellation.{CType, CValue}

    val config = SdkConfig(executorPort = 9091)
    val serializer: CValueSerializer = JsonCValueSerializer

    var capturedRequest: Option[pb.RegisterRequest] = None
    val transport = new ProviderTransport {
      def register(request: pb.RegisterRequest): IO[pb.RegisterResponse] = {
        capturedRequest = Some(request)
        IO.pure(pb.RegisterResponse(success = true, connectionId = "conn1"))
      }
      def deregister(request: pb.DeregisterRequest): IO[pb.DeregisterResponse] =
        IO.pure(pb.DeregisterResponse(success = true))
      def openControlPlane(handler: ControlPlaneHandler): cats.effect.Resource[IO, ControlPlaneStream] =
        cats.effect.Resource.pure(new ControlPlaneStream {
          def sendHeartbeat(hb: pb.Heartbeat): IO[Unit] = IO.unit
          def sendDrainAck(ack: pb.DrainAck): IO[Unit] = IO.unit
          def close: IO[Unit] = IO.unit
        })
    }

    val modulesRef = cats.effect.Ref.unsafe[IO, List[ModuleDefinition]](List(
      ModuleDefinition("echo", CType.CString, CType.CString, "1.0.0", "Echo", v => IO.pure(v))
    ))

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
