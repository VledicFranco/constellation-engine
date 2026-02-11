package io.constellation.provider

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.{FunctionRegistry, InMemoryFunctionRegistry}
import io.constellation.provider.v1.{provider => pb}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModuleProviderManagerSpec extends AnyFlatSpec with Matchers {

  private val stringSchema = pb.TypeSchema(
    pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))
  )

  private def mkDecl(name: String): pb.ModuleDeclaration =
    pb.ModuleDeclaration(
      name = name,
      inputSchema = Some(pb.TypeSchema(pb.TypeSchema.Type.Record(pb.RecordType(Map(
        "text" -> stringSchema
      ))))),
      outputSchema = Some(pb.TypeSchema(pb.TypeSchema.Type.Record(pb.RecordType(Map(
        "result" -> stringSchema
      ))))),
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

  /** Create a manager without starting the gRPC server (for unit testing). */
  private def createTestManager(): (ModuleProviderManager, FunctionRegistry) = {
    val testFunctionRegistry = new InMemoryFunctionRegistry
    val constellation        = ConstellationImpl.init.unsafeRunSync()

    // Create a minimal LangCompiler stub that exposes the function registry
    val compiler = new LangCompiler {
      def compile(source: String, dagName: String) =
        Left(List(io.constellation.lang.ast.CompileError.UndefinedFunction("test", None)))
      def compileToIR(source: String, dagName: String) =
        Left(List(io.constellation.lang.ast.CompileError.UndefinedFunction("test", None)))
      def functionRegistry: FunctionRegistry = testFunctionRegistry
    }

    val config = ProviderManagerConfig(
      grpcPort = 0, // not starting gRPC server in these tests
      reservedNamespaces = Set("stdlib")
    )

    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val cp    = new ControlPlaneManager(state, config, _ => IO.unit)
    val cache = new GrpcChannelCache
    val manager = new ModuleProviderManager(constellation, compiler, config, cp, JsonCValueSerializer, cache)

    (manager, testFunctionRegistry)
  }

  // ===== Register =====

  "ModuleProviderManager.handleRegister" should "register modules successfully" in {
    val (manager, registry) = createTestManager()

    val response = manager.handleRegister(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze"))),
      "conn1"
    ).unsafeRunSync()

    response.success shouldBe true
    response.results should have size 1
    response.results.head.accepted shouldBe true
    response.results.head.moduleName shouldBe "analyze"

    // Verify module is in ModuleRegistry (via Constellation.getModules)
    val modules = manager.getModules.unsafeRunSync()
    modules.map(_.name) should contain("ml.sentiment.analyze")

    // Verify function signature is in FunctionRegistry
    registry.lookupQualified("ml.sentiment.analyze") shouldBe defined
  }

  it should "register multiple modules" in {
    val (manager, registry) = createTestManager()

    val response = manager.handleRegister(
      mkRequest("ml", Seq(mkDecl("analyze"), mkDecl("classify"))),
      "conn1"
    ).unsafeRunSync()

    response.success shouldBe true
    response.results should have size 2
    response.results.forall(_.accepted) shouldBe true

    registry.lookupQualified("ml.analyze") shouldBe defined
    registry.lookupQualified("ml.classify") shouldBe defined
  }

  it should "reject modules in reserved namespace" in {
    val (manager, _) = createTestManager()

    val response = manager.handleRegister(
      mkRequest("stdlib.math", Seq(mkDecl("add"))),
      "conn1"
    ).unsafeRunSync()

    response.success shouldBe false
    response.results.head.accepted shouldBe false
  }

  it should "return protocol version" in {
    val (manager, _) = createTestManager()

    val response = manager.handleRegister(
      mkRequest("ml", Seq(mkDecl("test"))),
      "conn1"
    ).unsafeRunSync()

    response.protocolVersion shouldBe 1
  }

  it should "return connection_id in response" in {
    val (manager, _) = createTestManager()

    val response = manager.handleRegister(
      mkRequest("ml", Seq(mkDecl("test"))),
      "my-connection-id"
    ).unsafeRunSync()

    response.connectionId shouldBe "my-connection-id"
  }

  it should "track connection in ControlPlaneManager" in {
    val (manager, _) = createTestManager()

    manager.handleRegister(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze"))),
      "conn1"
    ).unsafeRunSync()

    val conn = manager.controlPlane.getConnection("conn1").unsafeRunSync()
    conn shouldBe defined
    conn.get.namespace shouldBe "ml.sentiment"
    conn.get.state shouldBe ConnectionState.Registered
    conn.get.registeredModules shouldBe Set("ml.sentiment.analyze")
  }

  it should "reject modules with invalid executor_url" in {
    val (manager, _) = createTestManager()

    val badRequest = pb.RegisterRequest(
      namespace = "ml",
      modules = Seq(mkDecl("analyze")),
      protocolVersion = 1,
      executorUrl = ""
    )

    val response = manager.handleRegister(badRequest, "conn1").unsafeRunSync()

    response.success shouldBe false
    response.results.head.accepted shouldBe false
    response.results.head.rejectionReason should include("executor_url")
  }

  it should "reject modules with scheme-prefixed executor_url" in {
    val (manager, _) = createTestManager()

    val badRequest = pb.RegisterRequest(
      namespace = "ml",
      modules = Seq(mkDecl("analyze")),
      protocolVersion = 1,
      executorUrl = "http://localhost:9090"
    )

    val response = manager.handleRegister(badRequest, "conn1").unsafeRunSync()

    response.success shouldBe false
    response.results.head.accepted shouldBe false
  }

  it should "reject modules with invalid module name characters" in {
    val (manager, _) = createTestManager()

    val badDecl = pb.ModuleDeclaration(
      name = "analyze stuff",
      inputSchema = Some(stringSchema),
      outputSchema = Some(stringSchema),
      version = "1.0.0",
      description = "Bad name"
    )

    val response = manager.handleRegister(
      mkRequest("ml", Seq(badDecl)),
      "conn1"
    ).unsafeRunSync()

    response.success shouldBe false
    response.results.head.accepted shouldBe false
    response.results.head.rejectionReason should include("alphanumeric")
  }

  // ===== Deregister =====

  "ModuleProviderManager.handleDeregister" should "deregister modules" in {
    val (manager, registry) = createTestManager()

    // Register first
    manager.handleRegister(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze"))),
      "conn1"
    ).unsafeRunSync()

    // Deregister
    val response = manager.handleDeregister(
      pb.DeregisterRequest(namespace = "ml.sentiment", moduleNames = Seq("analyze"), connectionId = "conn1"),
      "conn1"
    ).unsafeRunSync()

    response.success shouldBe true
    response.results should have size 1
    response.results.head.removed shouldBe true

    // Verify module is removed from FunctionRegistry
    registry.lookupQualified("ml.sentiment.analyze") shouldBe None
  }

  it should "reject deregister from wrong owner" in {
    val (manager, _) = createTestManager()

    manager.handleRegister(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze"))),
      "conn1"
    ).unsafeRunSync()

    val response = manager.handleDeregister(
      pb.DeregisterRequest(namespace = "ml.sentiment", moduleNames = Seq("analyze"), connectionId = "other-conn"),
      "other-conn"
    ).unsafeRunSync()

    response.success shouldBe false
    response.results.head.removed shouldBe false
  }

  it should "report not found for non-existent module" in {
    val (manager, _) = createTestManager()

    val response = manager.handleDeregister(
      pb.DeregisterRequest(namespace = "ml", moduleNames = Seq("nonexistent"), connectionId = "conn1"),
      "conn1"
    ).unsafeRunSync()

    response.success shouldBe false
    response.results.head.removed shouldBe false
    response.results.head.error should include("not found")
  }

  it should "remove connection from ControlPlaneManager when all modules deregistered" in {
    val (manager, _) = createTestManager()

    manager.handleRegister(
      mkRequest("ml", Seq(mkDecl("analyze"))),
      "conn1"
    ).unsafeRunSync()

    manager.handleDeregister(
      pb.DeregisterRequest(namespace = "ml", moduleNames = Seq("analyze"), connectionId = "conn1"),
      "conn1"
    ).unsafeRunSync()

    manager.controlPlane.getConnection("conn1").unsafeRunSync() shouldBe None
  }

  // ===== DeregisterAll =====

  "ModuleProviderManager.deregisterAllForConnection" should "clean up all modules for a connection" in {
    val (manager, registry) = createTestManager()

    manager.handleRegister(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze"), mkDecl("classify"))),
      "conn1"
    ).unsafeRunSync()

    manager.deregisterAllForConnection("conn1").unsafeRunSync()

    registry.lookupQualified("ml.sentiment.analyze") shouldBe None
    registry.lookupQualified("ml.sentiment.classify") shouldBe None
  }

  it should "not affect other connections" in {
    val (manager, registry) = createTestManager()

    manager.handleRegister(
      mkRequest("ml", Seq(mkDecl("analyze"))),
      "conn1"
    ).unsafeRunSync()

    manager.handleRegister(
      mkRequest("text", Seq(mkDecl("uppercase"))),
      "conn2"
    ).unsafeRunSync()

    manager.deregisterAllForConnection("conn1").unsafeRunSync()

    registry.lookupQualified("ml.analyze") shouldBe None
    registry.lookupQualified("text.uppercase") shouldBe defined
  }

  // ===== Constellation delegation =====

  "ModuleProviderManager" should "delegate Constellation methods" in {
    val (manager, _) = createTestManager()

    // getModules should work
    val modules = manager.getModules.unsafeRunSync()
    modules shouldBe empty

    // PipelineStore should be accessible
    manager.PipelineStore should not be null
  }
}
