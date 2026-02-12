package io.constellation.provider

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Ref}

import io.constellation.lang.semantic.FunctionRegistry
import io.constellation.provider.v1.provider as pb

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
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include(
      "different provider group"
    )
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
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include(
      "another provider"
    )
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
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include(
      "another provider"
    )
  }

  // ===== ControlPlaneManager Group Membership Tests =====

  "ControlPlaneManager.isLastGroupMember" should "return true for solo provider" in {
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val mgr   = new ControlPlaneManager(state, ProviderManagerConfig(), _ => IO.unit)

    mgr.registerConnection("conn1", "ml", "localhost:9090", "", Set("ml.a"), 1).unsafeRunSync()

    mgr.isLastGroupMember("conn1").unsafeRunSync() shouldBe true
  }

  it should "return false when other group members exist" in {
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val mgr   = new ControlPlaneManager(state, ProviderManagerConfig(), _ => IO.unit)

    mgr.registerConnection("conn1", "ml", "host1:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()
    mgr.registerConnection("conn2", "ml", "host2:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()

    // Activate both so they're not just Registered
    val obs = new StreamObserver[pb.ControlMessage] {
      override def onNext(msg: pb.ControlMessage): Unit = ()
      override def onError(t: Throwable): Unit          = ()
      override def onCompleted(): Unit                  = ()
    }
    mgr.activateControlPlane("conn1", obs).unsafeRunSync()
    mgr.activateControlPlane("conn2", obs).unsafeRunSync()

    mgr.isLastGroupMember("conn1").unsafeRunSync() shouldBe false
    mgr.isLastGroupMember("conn2").unsafeRunSync() shouldBe false
  }

  it should "return true when only remaining group member" in {
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val mgr   = new ControlPlaneManager(state, ProviderManagerConfig(), _ => IO.unit)

    mgr.registerConnection("conn1", "ml", "host1:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()
    mgr.registerConnection("conn2", "ml", "host2:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()

    // Disconnect conn2
    mgr.deactivateConnection("conn2").unsafeRunSync()

    // conn1 is now the last active member
    mgr.isLastGroupMember("conn1").unsafeRunSync() shouldBe true
  }

  it should "return true for unknown connection" in {
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val mgr   = new ControlPlaneManager(state, ProviderManagerConfig(), _ => IO.unit)

    mgr.isLastGroupMember("nonexistent").unsafeRunSync() shouldBe true
  }

  "ControlPlaneManager.getConnectionsByNamespace" should "return all connections for a namespace" in {
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val mgr   = new ControlPlaneManager(state, ProviderManagerConfig(), _ => IO.unit)

    mgr.registerConnection("conn1", "ml", "host1:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()
    mgr.registerConnection("conn2", "ml", "host2:9090", "ml-group", Set("ml.a"), 1).unsafeRunSync()
    mgr.registerConnection("conn3", "text", "host3:9090", "", Set("text.b"), 1).unsafeRunSync()

    val mlConns = mgr.getConnectionsByNamespace("ml").unsafeRunSync()
    mlConns should have size 2
    mlConns.map(_.connectionId).toSet shouldBe Set("conn1", "conn2")
  }

}
