package io.constellation.provider

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global

import io.constellation.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.ast.CompileError
import io.constellation.lang.semantic.{FunctionRegistry, InMemoryFunctionRegistry, SemanticType}
import io.constellation.provider.v1.{provider => pb}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests verifying the SDK↔Server boundary.
  *
  * These tests exercise the full path from protobuf schema declarations (SDK types)
  * through server-side registration, type conversion, module creation, and serialization.
  */
class SdkServerIntegrationSpec extends AnyFlatSpec with Matchers {

  // ===== Helpers =====

  private val stringSchema = pb.TypeSchema(
    pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.STRING))
  )
  private val intSchema = pb.TypeSchema(
    pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.INT))
  )
  private val floatSchema = pb.TypeSchema(
    pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.FLOAT))
  )
  private val boolSchema = pb.TypeSchema(
    pb.TypeSchema.Type.Primitive(pb.PrimitiveType(pb.PrimitiveType.Kind.BOOL))
  )

  private def mkRecordSchema(fields: (String, pb.TypeSchema)*): pb.TypeSchema =
    pb.TypeSchema(pb.TypeSchema.Type.Record(pb.RecordType(fields.toMap)))

  private def mkListSchema(elem: pb.TypeSchema): pb.TypeSchema =
    pb.TypeSchema(pb.TypeSchema.Type.List(pb.ListType(Some(elem))))

  private def mkMapSchema(key: pb.TypeSchema, value: pb.TypeSchema): pb.TypeSchema =
    pb.TypeSchema(pb.TypeSchema.Type.Map(pb.MapType(Some(key), Some(value))))

  private def mkOptionalSchema(inner: pb.TypeSchema): pb.TypeSchema =
    pb.TypeSchema(pb.TypeSchema.Type.Option(pb.OptionType(Some(inner))))

  private def mkDecl(
      name: String,
      inputSchema: pb.TypeSchema,
      outputSchema: pb.TypeSchema
  ): pb.ModuleDeclaration =
    pb.ModuleDeclaration(
      name = name,
      inputSchema = Some(inputSchema),
      outputSchema = Some(outputSchema),
      version = "1.0.0",
      description = s"Test module $name"
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

  private def createTestManager(): (ModuleProviderManager, FunctionRegistry) = {
    val testFunctionRegistry = new InMemoryFunctionRegistry
    val constellation = ConstellationImpl.init.unsafeRunSync()

    val compiler = new LangCompiler {
      def compile(source: String, dagName: String) =
        Left(List(CompileError.UndefinedFunction("test", None)))
      def compileToIR(source: String, dagName: String) =
        Left(List(CompileError.UndefinedFunction("test", None)))
      def functionRegistry: FunctionRegistry = testFunctionRegistry
    }

    val config = ProviderManagerConfig(grpcPort = 0, reservedNamespaces = Set("stdlib"))
    val state = Ref.of[IO, Map[String, ProviderConnection]](Map.empty).unsafeRunSync()
    val cp = new ControlPlaneManager(state, config, _ => IO.unit)
    val cache = new GrpcChannelCache
    val manager = new ModuleProviderManager(constellation, compiler, config, cp, JsonCValueSerializer, cache)
    (manager, testFunctionRegistry)
  }

  // ===== TypeSchemaConverter → Registration Integration =====

  "Schema conversion integration" should "register module with Record input/output" in {
    val (manager, registry) = createTestManager()

    val decl = mkDecl(
      "transform",
      inputSchema = mkRecordSchema("name" -> stringSchema, "age" -> intSchema),
      outputSchema = mkRecordSchema("result" -> stringSchema, "valid" -> boolSchema)
    )

    val response = manager.handleRegister(mkRequest("data", Seq(decl)), "conn1").unsafeRunSync()

    response.success shouldBe true

    // Verify module was created with correct consume/produce specs
    val moduleOpt = manager.getModuleByName("data.transform").unsafeRunSync()
    moduleOpt shouldBe defined
    val spec = moduleOpt.get.spec
    spec.consumes should contain key "name"
    spec.consumes should contain key "age"
    spec.consumes("name") shouldBe CType.CString
    spec.consumes("age") shouldBe CType.CInt
    spec.produces should contain key "result"
    spec.produces should contain key "valid"
    spec.produces("result") shouldBe CType.CString
    spec.produces("valid") shouldBe CType.CBoolean

    // Verify function signature has correct parameter types
    val sig = registry.lookupQualified("data.transform")
    sig shouldBe defined
    sig.get.params.map(_._1).toSet shouldBe Set("name", "age")
    val paramMap = sig.get.params.toMap
    paramMap("name") shouldBe SemanticType.SString
    paramMap("age") shouldBe SemanticType.SInt
  }

  it should "register module with List type" in {
    val (manager, registry) = createTestManager()

    val decl = mkDecl(
      "process",
      inputSchema = mkRecordSchema("items" -> mkListSchema(stringSchema)),
      outputSchema = mkRecordSchema("count" -> intSchema)
    )

    val response = manager.handleRegister(mkRequest("ml", Seq(decl)), "conn1").unsafeRunSync()

    response.success shouldBe true

    val moduleOpt = manager.getModuleByName("ml.process").unsafeRunSync()
    moduleOpt shouldBe defined
    moduleOpt.get.spec.consumes("items") shouldBe CType.CList(CType.CString)
  }

  it should "register module with Map type" in {
    val (manager, registry) = createTestManager()

    val decl = mkDecl(
      "lookup",
      inputSchema = mkRecordSchema("data" -> mkMapSchema(stringSchema, intSchema)),
      outputSchema = mkRecordSchema("result" -> stringSchema)
    )

    val response = manager.handleRegister(mkRequest("ml", Seq(decl)), "conn1").unsafeRunSync()

    response.success shouldBe true

    val moduleOpt = manager.getModuleByName("ml.lookup").unsafeRunSync()
    moduleOpt shouldBe defined
    moduleOpt.get.spec.consumes("data") shouldBe CType.CMap(CType.CString, CType.CInt)
  }

  it should "register module with Optional type" in {
    val (manager, _) = createTestManager()

    val decl = mkDecl(
      "maybe",
      inputSchema = mkRecordSchema("value" -> mkOptionalSchema(stringSchema)),
      outputSchema = mkRecordSchema("result" -> boolSchema)
    )

    val response = manager.handleRegister(mkRequest("ml", Seq(decl)), "conn1").unsafeRunSync()

    response.success shouldBe true

    val moduleOpt = manager.getModuleByName("ml.maybe").unsafeRunSync()
    moduleOpt shouldBe defined
    moduleOpt.get.spec.consumes("value") shouldBe CType.COptional(CType.CString)
  }

  it should "register module with nested Record type" in {
    val (manager, _) = createTestManager()

    val nestedRecord = mkRecordSchema("street" -> stringSchema, "city" -> stringSchema)
    val decl = mkDecl(
      "geolocate",
      inputSchema = mkRecordSchema("address" -> nestedRecord),
      outputSchema = mkRecordSchema("lat" -> floatSchema, "lng" -> floatSchema)
    )

    val response = manager.handleRegister(mkRequest("geo", Seq(decl)), "conn1").unsafeRunSync()

    response.success shouldBe true

    val moduleOpt = manager.getModuleByName("geo.geolocate").unsafeRunSync()
    moduleOpt shouldBe defined
    val addressType = moduleOpt.get.spec.consumes("address")
    addressType shouldBe a[CType.CProduct]
    val product = addressType.asInstanceOf[CType.CProduct]
    product.structure should contain key "street"
    product.structure should contain key "city"
  }

  it should "register module with primitive (non-record) input" in {
    val (manager, _) = createTestManager()

    // Primitive input wraps as Map("input" -> type), primitive output as Map("output" -> type)
    val decl = mkDecl("uppercase", inputSchema = stringSchema, outputSchema = stringSchema)

    val response = manager.handleRegister(mkRequest("text", Seq(decl)), "conn1").unsafeRunSync()

    response.success shouldBe true

    val moduleOpt = manager.getModuleByName("text.uppercase").unsafeRunSync()
    moduleOpt shouldBe defined
    moduleOpt.get.spec.consumes should have size 1
    moduleOpt.get.spec.consumes("input") shouldBe CType.CString
    moduleOpt.get.spec.produces("output") shouldBe CType.CString
  }

  it should "reject module with invalid nested schema" in {
    val (manager, _) = createTestManager()

    // List with missing element type
    val badListSchema = pb.TypeSchema(pb.TypeSchema.Type.List(pb.ListType(None)))
    val decl = mkDecl(
      "bad",
      inputSchema = mkRecordSchema("items" -> badListSchema),
      outputSchema = stringSchema
    )

    val response = manager.handleRegister(mkRequest("ml", Seq(decl)), "conn1").unsafeRunSync()
    response.success shouldBe false
    response.results.head.accepted shouldBe false
    response.results.head.rejectionReason should include("Invalid input schema")
  }

  // ===== CValueSerializer Integration =====

  "CValueSerializer integration" should "serialize CProduct round-trip" in {
    val input = CValue.CProduct(
      Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30)),
      Map("name" -> CType.CString, "age" -> CType.CInt)
    )

    val bytes = JsonCValueSerializer.serialize(input)
    bytes shouldBe a[Right[_, _]]

    val output = JsonCValueSerializer.deserialize(bytes.toOption.get)
    output shouldBe a[Right[_, _]]
    output.toOption.get shouldBe a[CValue.CProduct]

    val product = output.toOption.get.asInstanceOf[CValue.CProduct]
    product.value("name") shouldBe CValue.CString("Alice")
    product.value("age") shouldBe CValue.CInt(30)
  }

  it should "serialize CList round-trip" in {
    val input = CValue.CList(Vector(CValue.CString("a"), CValue.CString("b")), CType.CString)

    val bytes = JsonCValueSerializer.serialize(input)
    bytes shouldBe a[Right[_, _]]

    val output = JsonCValueSerializer.deserialize(bytes.toOption.get)
    output shouldBe a[Right[_, _]]
    output.toOption.get shouldBe a[CValue.CList]

    val list = output.toOption.get.asInstanceOf[CValue.CList]
    list.value should have size 2
  }

  it should "serialize CMap round-trip" in {
    val input = CValue.CMap(
      Vector(CValue.CString("key1") -> CValue.CInt(1)),
      CType.CString,
      CType.CInt
    )

    val bytes = JsonCValueSerializer.serialize(input)
    bytes shouldBe a[Right[_, _]]

    val output = JsonCValueSerializer.deserialize(bytes.toOption.get)
    output shouldBe a[Right[_, _]]
  }

  it should "handle serialization error for invalid bytes" in {
    val result = JsonCValueSerializer.deserialize(Array[Byte](0, 1, 2, 3))
    result shouldBe a[Left[_, _]]
  }

  // ===== Full Registration + Type Conversion Pipeline =====

  "Full pipeline" should "register module and verify ExternalModule spec correctness" in {
    val (manager, registry) = createTestManager()

    // Register a module with complex types
    val decl = mkDecl(
      "analyze",
      inputSchema = mkRecordSchema(
        "text" -> stringSchema,
        "options" -> mkRecordSchema("maxLength" -> intSchema, "language" -> stringSchema)
      ),
      outputSchema = mkRecordSchema(
        "sentiment" -> floatSchema,
        "tokens" -> mkListSchema(stringSchema)
      )
    )

    val response = manager.handleRegister(mkRequest("ml.nlp", Seq(decl)), "conn1").unsafeRunSync()
    response.success shouldBe true

    // Verify module spec has deeply nested types
    val moduleOpt = manager.getModuleByName("ml.nlp.analyze").unsafeRunSync()
    moduleOpt shouldBe defined
    val spec = moduleOpt.get.spec

    // Input: text (String), options (Record)
    spec.consumes("text") shouldBe CType.CString
    val optionsType = spec.consumes("options").asInstanceOf[CType.CProduct]
    optionsType.structure("maxLength") shouldBe CType.CInt
    optionsType.structure("language") shouldBe CType.CString

    // Output: sentiment (Float), tokens (List[String])
    spec.produces("sentiment") shouldBe CType.CFloat
    spec.produces("tokens") shouldBe CType.CList(CType.CString)

    // Verify function registry has correct types
    val sig = registry.lookupQualified("ml.nlp.analyze")
    sig shouldBe defined
    val pipelineParamMap = sig.get.params.toMap
    pipelineParamMap("text") shouldBe SemanticType.SString
    pipelineParamMap("options") shouldBe a[SemanticType.SRecord]
  }

  it should "register and deregister preserving other modules" in {
    val (manager, registry) = createTestManager()

    // Register two modules from different namespaces
    val decl1 = mkDecl("analyze", inputSchema = mkRecordSchema("text" -> stringSchema), outputSchema = mkRecordSchema("score" -> floatSchema))
    val decl2 = mkDecl("transform", inputSchema = mkRecordSchema("input" -> stringSchema), outputSchema = mkRecordSchema("output" -> stringSchema))

    manager.handleRegister(mkRequest("ml", Seq(decl1)), "conn1").unsafeRunSync()
    manager.handleRegister(mkRequest("text", Seq(decl2)), "conn2").unsafeRunSync()

    // Verify both registered
    registry.lookupQualified("ml.analyze") shouldBe defined
    registry.lookupQualified("text.transform") shouldBe defined

    // Deregister first
    manager.deregisterAllForConnection("conn1").unsafeRunSync()

    // Only first should be gone
    registry.lookupQualified("ml.analyze") shouldBe None
    registry.lookupQualified("text.transform") shouldBe defined
    manager.getModuleByName("text.transform").unsafeRunSync() shouldBe defined
  }

  it should "handle multiple modules in single registration with mixed types" in {
    val (manager, registry) = createTestManager()

    val decls = Seq(
      mkDecl("tokenize", inputSchema = mkRecordSchema("text" -> stringSchema), outputSchema = mkRecordSchema("tokens" -> mkListSchema(stringSchema))),
      mkDecl("embed", inputSchema = mkRecordSchema("tokens" -> mkListSchema(stringSchema)), outputSchema = mkRecordSchema("vector" -> mkListSchema(floatSchema))),
      mkDecl("classify", inputSchema = mkRecordSchema("vector" -> mkListSchema(floatSchema)), outputSchema = mkRecordSchema("label" -> stringSchema, "confidence" -> floatSchema))
    )

    val response = manager.handleRegister(mkRequest("ml.nlp", decls), "conn1").unsafeRunSync()
    response.success shouldBe true
    response.results should have size 3
    response.results.forall(_.accepted) shouldBe true

    // Verify all three modules registered with correct types
    manager.getModuleByName("ml.nlp.tokenize").unsafeRunSync() shouldBe defined
    manager.getModuleByName("ml.nlp.embed").unsafeRunSync() shouldBe defined
    manager.getModuleByName("ml.nlp.classify").unsafeRunSync() shouldBe defined

    val classifySpec = manager.getModuleByName("ml.nlp.classify").unsafeRunSync().get.spec
    classifySpec.consumes("vector") shouldBe CType.CList(CType.CFloat)
    classifySpec.produces should contain key "label"
    classifySpec.produces should contain key "confidence"
  }

  // ===== Executor Pool Integration =====

  "Executor pool integration" should "track pool per namespace across registrations" in {
    val (manager, _) = createTestManager()

    val decl = mkDecl("analyze", inputSchema = mkRecordSchema("text" -> stringSchema), outputSchema = mkRecordSchema("result" -> stringSchema))

    // First registration creates pool
    manager.handleRegister(
      mkRequest("ml", Seq(decl), executorUrl = "host1:9090"),
      "conn1"
    ).unsafeRunSync()

    val pools = manager.executorPools.get.unsafeRunSync()
    pools should contain key "ml"
    pools("ml").size.unsafeRunSync() shouldBe 1

    // Second registration (same group) adds to pool without creating new module
    manager.handleRegister(
      pb.RegisterRequest(namespace = "ml", modules = Seq(decl), protocolVersion = 1, executorUrl = "host2:9090", groupId = "ml-group"),
      "conn2"
    ).unsafeRunSync()

    // Note: the first registration was solo (no groupId), so the second is rejected by SchemaValidator.
    // This verifies the integration: validator correctly prevents mixing solo + group.
    // Let's verify the pool still has size 1
    val poolMap = manager.executorPools.get.unsafeRunSync()
    poolMap("ml").size.unsafeRunSync() shouldBe 1
  }

  it should "clean up pool on deregister" in {
    val (manager, _) = createTestManager()

    val decl = mkDecl("analyze", inputSchema = mkRecordSchema("text" -> stringSchema), outputSchema = mkRecordSchema("result" -> stringSchema))

    manager.handleRegister(mkRequest("ml", Seq(decl)), "conn1").unsafeRunSync()
    manager.executorPools.get.unsafeRunSync() should contain key "ml"

    manager.deregisterAllForConnection("conn1").unsafeRunSync()
    manager.executorPools.get.unsafeRunSync() should not contain key("ml")
  }
}
