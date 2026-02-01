package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.circe.CirceEntityCodec.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.FunctionRegistry
import io.constellation.http.ApiModels.*
import io.circe.Json

class ConstellationRoutesTest extends AnyFlatSpec with Matchers {

  // Create test constellation and compiler instances
  val constellation    = ConstellationImpl.init.unsafeRunSync()
  val compiler         = LangCompiler.empty
  val functionRegistry = FunctionRegistry.empty
  val routes           = ConstellationRoutes(constellation, compiler, functionRegistry).routes

  "ConstellationRoutes" should "respond to health check" in {
    val request  = Request[IO](Method.GET, uri"/health")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    body.hcursor.downField("status").as[String] shouldBe Right("ok")
  }

  it should "compile valid constellation-lang programs" in {
    val compileRequest = CompileRequest(
      source = """
        in x: Int
        in y: Int
        out x
      """,
      dagName = Some("test-dag")
    )

    val request = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)

    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[CompileResponse].unsafeRunSync()
    body.success shouldBe true
    body.dagName shouldBe Some("test-dag")
    body.errors shouldBe List.empty
  }

  it should "return errors for invalid constellation-lang programs" in {
    val compileRequest = CompileRequest(
      source = """
        in x: Int
        out undefined_variable
      """,
      dagName = Some("invalid-dag")
    )

    val request = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)

    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.BadRequest
    val body = response.as[CompileResponse].unsafeRunSync()
    body.success shouldBe false
    body.errors should not be empty
  }

  it should "list available DAGs" in {
    // First compile a DAG
    val compileRequest = CompileRequest(
      source = "in x: Int\nout x",
      dagName = Some("list-test-dag")
    )

    val compileReq = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)
    routes.orNotFound.run(compileReq).unsafeRunSync()

    // Then list DAGs
    val listRequest = Request[IO](Method.GET, uri"/dags")
    val response    = routes.orNotFound.run(listRequest).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[DagListResponse].unsafeRunSync()
    body.dags.keySet should contain("list-test-dag")
  }

  it should "get a specific DAG by name" in {
    // First compile a DAG
    val compileRequest = CompileRequest(
      source = "in x: Int\nout x",
      dagName = Some("get-test-dag")
    )

    val compileReq = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)
    routes.orNotFound.run(compileReq).unsafeRunSync()

    // Then get the DAG
    val getRequest = Request[IO](Method.GET, uri"/dags/get-test-dag")
    val response   = routes.orNotFound.run(getRequest).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[DagResponse].unsafeRunSync()
    body.name shouldBe "get-test-dag"
  }

  it should "return 404 for non-existent DAG" in {
    val request  = Request[IO](Method.GET, uri"/dags/non-existent")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
    val body = response.as[ErrorResponse].unsafeRunSync()
    body.error shouldBe "DagNotFound"
  }

  it should "list available modules" in {
    val request  = Request[IO](Method.GET, uri"/modules")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ModuleListResponse].unsafeRunSync()
    body.modules shouldBe a[List[_]]
  }

  // Execute endpoint tests

  it should "execute a simple DAG with valid inputs" in {
    // First compile a simple passthrough DAG
    val compileRequest = CompileRequest(
      source = """
        in text: String
        out text
      """,
      dagName = Some("passthrough-pipeline")
    )

    val compileReq = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)
    routes.orNotFound.run(compileReq).unsafeRunSync()

    // Execute the DAG
    val executeRequest = ExecuteRequest(
      dagName = Some("passthrough-pipeline"),
      inputs = Map("text" -> Json.fromString("HELLO WORLD"))
    )

    val execReq = Request[IO](Method.POST, uri"/execute")
      .withEntity(executeRequest)
    val response = routes.orNotFound.run(execReq).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ExecuteResponse].unsafeRunSync()
    body.success shouldBe true
    body.error shouldBe None
    body.outputs should not be empty
    body.outputs.get("text") shouldBe Some(Json.fromString("HELLO WORLD"))
  }

  it should "return 404 for executing non-existent DAG" in {
    val executeRequest = ExecuteRequest(
      dagName = Some("non-existent-dag"),
      inputs = Map("input" -> Json.fromString("test"))
    )

    val request = Request[IO](Method.POST, uri"/execute")
      .withEntity(executeRequest)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
    val body = response.as[ErrorResponse].unsafeRunSync()
    body.error shouldBe "NotFound"
  }

  it should "return 400 for missing required input" in {
    // First compile a DAG with two inputs (both are top-level data nodes)
    val compileRequest = CompileRequest(
      source = """
        in x: Int
        in y: Int
        out x
      """,
      dagName = Some("two-input-pipeline")
    )

    val compileReq = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)
    routes.orNotFound.run(compileReq).unsafeRunSync()

    // Try to execute with only one input (missing y)
    val executeRequest = ExecuteRequest(
      dagName = Some("two-input-pipeline"),
      inputs = Map("x" -> Json.fromLong(5))
      // missing "y"
    )

    val execReq = Request[IO](Method.POST, uri"/execute")
      .withEntity(executeRequest)
    val response = routes.orNotFound.run(execReq).unsafeRunSync()

    response.status shouldBe Status.BadRequest
    val body = response.as[ExecuteResponse].unsafeRunSync()
    body.success shouldBe false
    body.error.get should include("Missing required input")
  }

  it should "return 400 for input type mismatch" in {
    // First compile a DAG
    val compileRequest = CompileRequest(
      source = """
        in x: Int
        out x
      """,
      dagName = Some("passthrough-int")
    )

    val compileReq = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)
    routes.orNotFound.run(compileReq).unsafeRunSync()

    // Try to execute with wrong type (string instead of int)
    val executeRequest = ExecuteRequest(
      dagName = Some("passthrough-int"),
      inputs = Map("x" -> Json.fromString("not a number"))
    )

    val execReq = Request[IO](Method.POST, uri"/execute")
      .withEntity(executeRequest)
    val response = routes.orNotFound.run(execReq).unsafeRunSync()

    response.status shouldBe Status.BadRequest
    val body = response.as[ExecuteResponse].unsafeRunSync()
    body.success shouldBe false
    body.error.get should include("Input error")
  }

  it should "execute DAG with complex types (lists)" in {
    // Compile a DAG that works with lists (use angle brackets for type params)
    val compileRequest = CompileRequest(
      source = """
        in items: List<String>
        out items
      """,
      dagName = Some("list-passthrough")
    )

    val compileReq = Request[IO](Method.POST, uri"/compile")
      .withEntity(compileRequest)
    routes.orNotFound.run(compileReq).unsafeRunSync()

    // Execute with a list input
    val executeRequest = ExecuteRequest(
      dagName = Some("list-passthrough"),
      inputs = Map(
        "items" -> Json.fromValues(
          List(
            Json.fromString("apple"),
            Json.fromString("banana"),
            Json.fromString("cherry")
          )
        )
      )
    )

    val execReq = Request[IO](Method.POST, uri"/execute")
      .withEntity(executeRequest)
    val response = routes.orNotFound.run(execReq).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ExecuteResponse].unsafeRunSync()
    body.success shouldBe true
    body.error shouldBe None
    body.outputs.get("items") shouldBe Some(
      Json.fromValues(
        List(
          Json.fromString("apple"),
          Json.fromString("banana"),
          Json.fromString("cherry")
        )
      )
    )
  }

  // Run endpoint tests (compile + execute in one step)

  it should "run a simple script with valid inputs" in {
    val runRequest = RunRequest(
      source = """
        in text: String
        out text
      """,
      inputs = Map("text" -> Json.fromString("HELLO WORLD"))
    )

    val request = Request[IO](Method.POST, uri"/run")
      .withEntity(runRequest)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[RunResponse].unsafeRunSync()
    body.success shouldBe true
    body.error shouldBe None
    body.compilationErrors shouldBe empty
    body.outputs.get("text") shouldBe Some(Json.fromString("HELLO WORLD"))
  }

  it should "return compilation errors for invalid script in /run" in {
    val runRequest = RunRequest(
      source = """
        in x: Int
        out undefined_variable
      """,
      inputs = Map("x" -> Json.fromLong(42))
    )

    val request = Request[IO](Method.POST, uri"/run")
      .withEntity(runRequest)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.BadRequest
    val body = response.as[RunResponse].unsafeRunSync()
    body.success shouldBe false
    body.compilationErrors should not be empty
  }

  it should "return 400 for missing required input in /run" in {
    val runRequest = RunRequest(
      source = """
        in x: Int
        in y: Int
        out x
      """,
      inputs = Map("x" -> Json.fromLong(5))
      // missing "y"
    )

    val request = Request[IO](Method.POST, uri"/run")
      .withEntity(runRequest)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.BadRequest
    val body = response.as[RunResponse].unsafeRunSync()
    body.success shouldBe false
    body.error.get should include("Missing required input")
  }

  it should "return 400 for input type mismatch in /run" in {
    val runRequest = RunRequest(
      source = """
        in x: Int
        out x
      """,
      inputs = Map("x" -> Json.fromString("not a number"))
    )

    val request = Request[IO](Method.POST, uri"/run")
      .withEntity(runRequest)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.BadRequest
    val body = response.as[RunResponse].unsafeRunSync()
    body.success shouldBe false
    body.error.get should include("Input error")
  }

  it should "run script with complex types (records)" in {
    val runRequest = RunRequest(
      source = """
        in user: { name: String, age: Int }
        out user
      """,
      inputs = Map(
        "user" -> Json.obj(
          "name" -> Json.fromString("Alice"),
          "age"  -> Json.fromLong(30)
        )
      )
    )

    val request = Request[IO](Method.POST, uri"/run")
      .withEntity(runRequest)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[RunResponse].unsafeRunSync()
    body.success shouldBe true
    body.error shouldBe None
    body.outputs.get("user") shouldBe Some(
      Json.obj(
        "name" -> Json.fromString("Alice"),
        "age"  -> Json.fromLong(30)
      )
    )
  }

  it should "run script with multiple inputs where all are required" in {
    // All inputs declared are top-level data nodes, so all must be provided
    val runRequest = RunRequest(
      source = """
        in a: Int
        in b: String
        in c: Boolean
        out a
      """,
      inputs = Map(
        "a" -> Json.fromLong(42),
        "b" -> Json.fromString("hello"),
        "c" -> Json.fromBoolean(true)
      )
    )

    val request = Request[IO](Method.POST, uri"/run")
      .withEntity(runRequest)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[RunResponse].unsafeRunSync()
    body.success shouldBe true
    body.outputs.get("a") shouldBe Some(Json.fromLong(42))
  }

  // Namespace endpoint tests

  "Namespace endpoints" should "list all namespaces" in {
    // Create routes with a populated function registry
    import io.constellation.lang.semantic.{FunctionSignature, SemanticType}
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "add",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.add",
        namespace = Some("stdlib.math")
      )
    )
    registry.register(
      FunctionSignature(
        name = "upper",
        params = List("value" -> SemanticType.SString),
        returns = SemanticType.SString,
        moduleName = "stdlib.upper",
        namespace = Some("stdlib.string")
      )
    )

    val routesWithNs = ConstellationRoutes(constellation, compiler, registry).routes

    val request  = Request[IO](Method.GET, uri"/namespaces")
    val response = routesWithNs.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[NamespaceListResponse].unsafeRunSync()
    body.namespaces should contain allOf ("stdlib.math", "stdlib.string")
  }

  it should "list functions in a specific namespace" in {
    import io.constellation.lang.semantic.{FunctionSignature, SemanticType}
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        name = "add",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.add",
        namespace = Some("stdlib.math")
      )
    )
    registry.register(
      FunctionSignature(
        name = "multiply",
        params = List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.multiply",
        namespace = Some("stdlib.math")
      )
    )

    val routesWithNs = ConstellationRoutes(constellation, compiler, registry).routes

    val request  = Request[IO](Method.GET, uri"/namespaces/stdlib.math")
    val response = routesWithNs.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[NamespaceFunctionsResponse].unsafeRunSync()
    body.namespace shouldBe "stdlib.math"
    body.functions.map(_.name) should contain allOf ("add", "multiply")
  }

  it should "return 404 for non-existent namespace" in {
    val request  = Request[IO](Method.GET, uri"/namespaces/nonexistent.namespace")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
    val body = response.as[ErrorResponse].unsafeRunSync()
    body.error shouldBe "NamespaceNotFound"
  }

  it should "return empty namespace list when no functions registered" in {
    val request  = Request[IO](Method.GET, uri"/namespaces")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[NamespaceListResponse].unsafeRunSync()
    // The default empty registry has no namespaces
    body.namespaces shouldBe empty
  }

  // Metrics endpoint tests

  "Metrics endpoint" should "return server statistics without caching compiler" in {
    val request  = Request[IO](Method.GET, uri"/metrics")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()

    // Check timestamp is present
    body.hcursor.downField("timestamp").as[String] should be a Symbol("right")

    // Check cache is null when not using CachingLangCompiler
    body.hcursor.downField("cache").focus shouldBe Some(Json.Null)

    // Check server stats are present
    body.hcursor.downField("server").downField("uptime_seconds").as[Long] should be a Symbol("right")
    body.hcursor.downField("server").downField("requests_total").as[Long] should be a Symbol("right")
  }

  it should "return cache statistics with caching compiler" in {
    import io.constellation.lang.CachingLangCompiler

    // Create routes with a caching compiler
    val cachingCompiler = LangCompiler.builder.withCaching().build
    val cachingRoutes   = ConstellationRoutes(constellation, cachingCompiler, functionRegistry).routes

    // First make a compile request to populate cache stats
    val compileRequest = CompileRequest(
      source = "in x: Int\nout x",
      dagName = Some("cache-test")
    )
    val compileReq = Request[IO](Method.POST, uri"/compile").withEntity(compileRequest)
    cachingRoutes.orNotFound.run(compileReq).unsafeRunSync()

    // Now get metrics
    val request  = Request[IO](Method.GET, uri"/metrics")
    val response = cachingRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()

    // Check cache stats are present
    val cacheCursor = body.hcursor.downField("cache")
    cacheCursor.downField("hits").as[Long] should be a Symbol("right")
    cacheCursor.downField("misses").as[Long] should be a Symbol("right")
    cacheCursor.downField("hitRate").as[Double] should be a Symbol("right")
    cacheCursor.downField("evictions").as[Long] should be a Symbol("right")
    cacheCursor.downField("entries").as[Int] should be a Symbol("right")
  }

  it should "increment request count on each call" in {
    val request1 = Request[IO](Method.GET, uri"/metrics")
    val response1 = routes.orNotFound.run(request1).unsafeRunSync()
    val count1 = response1.as[Json].unsafeRunSync()
      .hcursor.downField("server").downField("requests_total").as[Long].toOption.get

    val request2 = Request[IO](Method.GET, uri"/metrics")
    val response2 = routes.orNotFound.run(request2).unsafeRunSync()
    val count2 = response2.as[Json].unsafeRunSync()
      .hcursor.downField("server").downField("requests_total").as[Long].toOption.get

    count2 should be > count1
  }
}
