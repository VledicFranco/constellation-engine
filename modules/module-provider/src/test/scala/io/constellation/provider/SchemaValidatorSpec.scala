package io.constellation.provider

import io.constellation.lang.semantic.{FunctionRegistry, FunctionSignature, InMemoryFunctionRegistry, SemanticType}
import io.constellation.provider.v1.{provider => pb}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SchemaValidatorSpec extends AnyFlatSpec with Matchers {

  private val stringSchema = pb.TypeSchema(
    pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))
  )

  private def mkRequest(
      namespace: String,
      modules: Seq[pb.ModuleDeclaration],
      executorUrl: String = "localhost:9090"
  ): pb.RegisterRequest =
    pb.RegisterRequest(
      namespace = namespace,
      modules = modules,
      protocolVersion = 1,
      executorUrl = executorUrl
    )

  private def mkDecl(name: String): pb.ModuleDeclaration =
    pb.ModuleDeclaration(
      name = name,
      inputSchema = Some(stringSchema),
      outputSchema = Some(stringSchema),
      version = "1.0.0",
      description = s"Test module $name"
    )

  // ===== Namespace validation =====

  "SchemaValidator.validateNamespace" should "accept valid namespace" in {
    SchemaValidator.validateNamespace("ml.sentiment") shouldBe None
  }

  it should "accept single-part namespace" in {
    SchemaValidator.validateNamespace("myProvider") shouldBe None
  }

  it should "reject empty namespace" in {
    SchemaValidator.validateNamespace("").isDefined shouldBe true
  }

  it should "reject namespace with empty parts" in {
    SchemaValidator.validateNamespace("ml..sentiment").isDefined shouldBe true
  }

  it should "reject namespace part starting with digit" in {
    SchemaValidator.validateNamespace("ml.1sentiment").isDefined shouldBe true
  }

  // ===== Executor URL validation =====

  "SchemaValidator.validateExecutorUrl" should "accept valid host:port" in {
    SchemaValidator.validateExecutorUrl("localhost:9090") shouldBe None
  }

  it should "accept hostname only (port defaults)" in {
    SchemaValidator.validateExecutorUrl("myhost") shouldBe None
  }

  it should "accept hostname with valid port" in {
    SchemaValidator.validateExecutorUrl("example.com:8080") shouldBe None
  }

  it should "reject empty URL" in {
    SchemaValidator.validateExecutorUrl("") shouldBe defined
    SchemaValidator.validateExecutorUrl("   ") shouldBe defined
  }

  it should "reject URL with scheme prefix" in {
    val result = SchemaValidator.validateExecutorUrl("http://localhost:9090")
    result shouldBe defined
    result.get should include("scheme")
  }

  it should "reject URL with grpc scheme prefix" in {
    SchemaValidator.validateExecutorUrl("grpc://host:9090") shouldBe defined
  }

  it should "reject URL with invalid port" in {
    SchemaValidator.validateExecutorUrl("localhost:abc") shouldBe defined
  }

  it should "reject URL with port out of range" in {
    SchemaValidator.validateExecutorUrl("localhost:0") shouldBe defined
    SchemaValidator.validateExecutorUrl("localhost:70000") shouldBe defined
  }

  it should "reject all modules when executor_url is invalid" in {
    val results = SchemaValidator.validate(
      mkRequest("ml", Seq(mkDecl("a"), mkDecl("b")), executorUrl = ""),
      FunctionRegistry.empty,
      Map.empty,
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 2
    results.foreach(_ shouldBe a[ModuleValidationResult.Rejected])
  }

  // ===== Module name validation =====

  "SchemaValidator.validateModuleName" should "accept valid module name" in {
    SchemaValidator.validateModuleName("analyze") shouldBe None
    SchemaValidator.validateModuleName("myModule_v2") shouldBe None
    SchemaValidator.validateModuleName("A") shouldBe None
  }

  it should "reject empty name" in {
    SchemaValidator.validateModuleName("") shouldBe defined
  }

  it should "reject name starting with digit" in {
    SchemaValidator.validateModuleName("1analyze") shouldBe defined
  }

  it should "reject name with spaces" in {
    SchemaValidator.validateModuleName("analyze stuff") shouldBe defined
  }

  it should "reject name with dots" in {
    SchemaValidator.validateModuleName("analyze.v2") shouldBe defined
  }

  it should "reject name with special characters" in {
    SchemaValidator.validateModuleName("analyze!") shouldBe defined
    SchemaValidator.validateModuleName("analyze@home") shouldBe defined
    SchemaValidator.validateModuleName("analyze-v2") shouldBe defined
  }

  it should "reject module with invalid characters via full validate" in {
    val results = SchemaValidator.validate(
      mkRequest("ml", Seq(mkDecl("analyze-v2").copy(name = "analyze-v2"))),
      FunctionRegistry.empty,
      Map.empty,
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include("alphanumeric")
  }

  // ===== Module validation =====

  "SchemaValidator.validate" should "accept valid module" in {
    val results = SchemaValidator.validate(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze"))),
      FunctionRegistry.empty,
      Map.empty,
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Accepted]
  }

  it should "accept multiple modules" in {
    val results = SchemaValidator.validate(
      mkRequest("ml", Seq(mkDecl("analyze"), mkDecl("classify"))),
      FunctionRegistry.empty,
      Map.empty,
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 2
    results.foreach(_ shouldBe a[ModuleValidationResult.Accepted])
  }

  it should "reject reserved namespace" in {
    val results = SchemaValidator.validate(
      mkRequest("stdlib.math", Seq(mkDecl("add"))),
      FunctionRegistry.empty,
      Map.empty,
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include("reserved")
  }

  it should "reject exact reserved namespace" in {
    val results = SchemaValidator.validate(
      mkRequest("stdlib", Seq(mkDecl("add"))),
      FunctionRegistry.empty,
      Map.empty,
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
  }

  it should "reject namespace owned by another provider" in {
    val results = SchemaValidator.validate(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze"))),
      FunctionRegistry.empty,
      Map("ml.sentiment" -> "other-conn"),
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include("another provider")
  }

  it should "allow same provider to re-register (upgrade)" in {
    val registry = new InMemoryFunctionRegistry
    registry.register(FunctionSignature(
      name = "analyze",
      params = List("input" -> SemanticType.SString),
      returns = SemanticType.SString,
      moduleName = "ml.sentiment.analyze",
      namespace = Some("ml.sentiment")
    ))

    val results = SchemaValidator.validate(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze"))),
      registry,
      Map("ml.sentiment" -> "conn1"),
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Accepted]
  }

  it should "reject module with missing input schema" in {
    val declNoInput = pb.ModuleDeclaration(
      name = "broken",
      inputSchema = None,
      outputSchema = Some(stringSchema)
    )
    val results = SchemaValidator.validate(
      mkRequest("ml", Seq(declNoInput)),
      FunctionRegistry.empty,
      Map.empty,
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include("input schema")
  }

  it should "reject module with invalid input schema" in {
    val declBadInput = pb.ModuleDeclaration(
      name = "broken",
      inputSchema = Some(pb.TypeSchema()), // empty type
      outputSchema = Some(stringSchema)
    )
    val results = SchemaValidator.validate(
      mkRequest("ml", Seq(declBadInput)),
      FunctionRegistry.empty,
      Map.empty,
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
  }

  it should "reject all modules when namespace is invalid" in {
    val results = SchemaValidator.validate(
      mkRequest("", Seq(mkDecl("analyze"), mkDecl("classify"))),
      FunctionRegistry.empty,
      Map.empty,
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 2
    results.foreach(_ shouldBe a[ModuleValidationResult.Rejected])
  }

  // ===== Missing/invalid output schema =====

  it should "reject module with missing output schema" in {
    val declNoOutput = pb.ModuleDeclaration(
      name = "broken",
      inputSchema = Some(stringSchema),
      outputSchema = None
    )
    val results = SchemaValidator.validate(
      mkRequest("ml", Seq(declNoOutput)),
      FunctionRegistry.empty,
      Map.empty,
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include("output")
  }

  it should "reject module with invalid output schema" in {
    val declBadOutput = pb.ModuleDeclaration(
      name = "broken",
      inputSchema = Some(stringSchema),
      outputSchema = Some(pb.TypeSchema()) // empty type
    )
    val results = SchemaValidator.validate(
      mkRequest("ml", Seq(declBadOutput)),
      FunctionRegistry.empty,
      Map.empty,
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
  }

  // ===== Name conflict with no namespace owner =====

  it should "reject when module exists in registry with no namespace owner" in {
    val registry = new InMemoryFunctionRegistry
    registry.register(FunctionSignature(
      name = "analyze",
      params = List("input" -> SemanticType.SString),
      returns = SemanticType.SString,
      moduleName = "ml.sentiment.analyze",
      namespace = Some("ml.sentiment")
    ))

    val results = SchemaValidator.validate(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze"))),
      registry,
      Map.empty, // No namespace owners — triggers "already exists" path
      Map.empty,
      "conn1",
      Set("stdlib")
    )
    results should have size 1
    results.head shouldBe a[ModuleValidationResult.Rejected]
    results.head.asInstanceOf[ModuleValidationResult.Rejected].reason should include("already exists")
  }
}
