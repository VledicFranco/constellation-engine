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

  // ===== Module validation =====

  "SchemaValidator.validate" should "accept valid module" in {
    val results = SchemaValidator.validate(
      mkRequest("ml.sentiment", Seq(mkDecl("analyze"))),
      FunctionRegistry.empty,
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
      "conn1",
      Set("stdlib")
    )
    results should have size 2
    results.foreach(_ shouldBe a[ModuleValidationResult.Rejected])
  }
}
