package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.constellation.http.ApiModels.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.FunctionRegistry

import io.circe.Json
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for content-addressed HTTP API endpoints (RFC-014 Phase 5). */
class ContentAddressedRoutesTest extends AnyFlatSpec with Matchers {

  // Fresh constellation + compiler per test class
  private val constellation    = ConstellationImpl.init.unsafeRunSync()
  private val compiler         = LangCompiler.empty
  private val functionRegistry = FunctionRegistry.empty
  private val routes = ConstellationRoutes(constellation, compiler, functionRegistry).routes

  // ---------------------------------------------------------------------------
  // POST /compile — returns structuralHash + syntacticHash
  // ---------------------------------------------------------------------------

  "POST /compile" should "return structuralHash and syntacticHash on success" in {
    val compileRequest = CompileRequest(
      source = "in x: Int\nout x",
      name = Some("hash-test")
    )

    val request  = Request[IO](Method.POST, uri"/compile").withEntity(compileRequest)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[CompileResponse].unsafeRunSync()
    body.success shouldBe true
    body.structuralHash shouldBe defined
    body.syntacticHash shouldBe defined
    body.name shouldBe Some("hash-test")
    body.dagName shouldBe Some("hash-test")
  }

  it should "store image in PipelineStore accessible by name" in {
    val compileRequest = CompileRequest(
      source = "in y: String\nout y",
      name = Some("store-test")
    )

    val request = Request[IO](Method.POST, uri"/compile").withEntity(compileRequest)
    routes.orNotFound.run(request).unsafeRunSync()

    // The pipeline should be retrievable by name from PipelineStore
    val image = constellation.PipelineStore.getByName("store-test").unsafeRunSync()
    image shouldBe defined
    image.get.dagSpec.declaredOutputs should contain("y")
  }

  it should "store image in PipelineStore accessible by hash" in {
    val compileRequest = CompileRequest(
      source = "in z: Boolean\nout z",
      name = Some("hash-lookup-test")
    )

    val request = Request[IO](Method.POST, uri"/compile").withEntity(compileRequest)
    val resp    = routes.orNotFound.run(request).unsafeRunSync()
    val body    = resp.as[CompileResponse].unsafeRunSync()

    val hash  = body.structuralHash.get
    val image = constellation.PipelineStore.get(hash).unsafeRunSync()
    image shouldBe defined
    image.get.structuralHash shouldBe hash
  }

  // ---------------------------------------------------------------------------
  // POST /execute — accept ref (name or hash)
  // ---------------------------------------------------------------------------

  "POST /execute" should "execute by name (ref field)" in {
    // Compile first
    val compileReq =
      CompileRequest(source = "in text: String\nout text", name = Some("exec-by-name"))
    routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/compile").withEntity(compileReq)
      )
      .unsafeRunSync()

    // Execute by name
    val execReq = ExecuteRequest(
      ref = Some("exec-by-name"),
      inputs = Map("text" -> Json.fromString("hello"))
    )
    val response = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/execute").withEntity(execReq)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ExecuteResponse].unsafeRunSync()
    body.success shouldBe true
    body.outputs.get("text") shouldBe Some(Json.fromString("hello"))
  }

  it should "execute by structural hash (sha256: prefix)" in {
    // Compile first
    val compileReq = CompileRequest(source = "in n: Int\nout n", name = Some("exec-by-hash"))
    val compileResp = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/compile").withEntity(compileReq)
      )
      .unsafeRunSync()
    val hash = compileResp.as[CompileResponse].unsafeRunSync().structuralHash.get

    // Execute by hash
    val execReq = ExecuteRequest(
      ref = Some(s"sha256:$hash"),
      inputs = Map("n" -> Json.fromLong(42))
    )
    val response = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/execute").withEntity(execReq)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ExecuteResponse].unsafeRunSync()
    body.success shouldBe true
    body.outputs.get("n") shouldBe Some(Json.fromLong(42))
  }

  it should "fall back to legacy dagName for backward compat" in {
    // Compile with legacy dagName
    val compileReq = CompileRequest(source = "in v: String\nout v", dagName = Some("legacy-dag"))
    routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/compile").withEntity(compileReq)
      )
      .unsafeRunSync()

    // Execute with legacy dagName
    val execReq = ExecuteRequest(
      dagName = Some("legacy-dag"),
      inputs = Map("v" -> Json.fromString("legacy"))
    )
    val response = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/execute").withEntity(execReq)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ExecuteResponse].unsafeRunSync()
    body.success shouldBe true
    body.outputs.get("v") shouldBe Some(Json.fromString("legacy"))
  }

  it should "return 404 for unknown ref" in {
    val execReq = ExecuteRequest(
      ref = Some("nonexistent-program"),
      inputs = Map("x" -> Json.fromLong(1))
    )
    val response = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/execute").withEntity(execReq)
      )
      .unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  // ---------------------------------------------------------------------------
  // POST /run — stores image and returns structuralHash
  // ---------------------------------------------------------------------------

  "POST /run" should "return structuralHash in response" in {
    val runReq = RunRequest(
      source = "in x: Int\nout x",
      inputs = Map("x" -> Json.fromLong(99))
    )
    val response = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/run").withEntity(runReq)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[RunResponse].unsafeRunSync()
    body.success shouldBe true
    body.structuralHash shouldBe defined
    body.outputs.get("x") shouldBe Some(Json.fromLong(99))
  }

  it should "produce same structuralHash for same source (dedup)" in {
    val source = "in a: String\nout a"

    val resp1 = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/run").withEntity(
          RunRequest(source, Map("a" -> Json.fromString("first")))
        )
      )
      .unsafeRunSync()
      .as[RunResponse]
      .unsafeRunSync()

    val resp2 = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/run").withEntity(
          RunRequest(source, Map("a" -> Json.fromString("second")))
        )
      )
      .unsafeRunSync()
      .as[RunResponse]
      .unsafeRunSync()

    resp1.structuralHash shouldBe resp2.structuralHash
  }

  it should "store image accessible by hash after /run" in {
    val runReq = RunRequest(
      source = "in flag: Boolean\nout flag",
      inputs = Map("flag" -> Json.fromBoolean(true))
    )
    val body = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/run").withEntity(runReq)
      )
      .unsafeRunSync()
      .as[RunResponse]
      .unsafeRunSync()

    val hash  = body.structuralHash.get
    val image = constellation.PipelineStore.get(hash).unsafeRunSync()
    image shouldBe defined
  }

  // ---------------------------------------------------------------------------
  // GET /pipelines — list stored pipelines
  // ---------------------------------------------------------------------------

  "GET /pipelines" should "list stored pipelines" in {
    // Compile a program with a name
    val compileReq = CompileRequest(
      source = "in w: Int\nout w",
      name = Some("list-prog-test")
    )
    routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/compile").withEntity(compileReq)
      )
      .unsafeRunSync()

    val response = routes.orNotFound
      .run(
        Request[IO](Method.GET, uri"/pipelines")
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[PipelineListResponse].unsafeRunSync()
    body.pipelines should not be empty
    // At least one pipeline should have our alias
    val hasAlias = body.pipelines.exists(_.aliases.contains("list-prog-test"))
    hasAlias shouldBe true
  }

  // ---------------------------------------------------------------------------
  // GET /pipelines/:ref — pipeline metadata
  // ---------------------------------------------------------------------------

  "GET /pipelines/:ref" should "return pipeline details by name" in {
    val compileReq = CompileRequest(
      source = "in q: String\nout q",
      name = Some("detail-test")
    )
    routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/compile").withEntity(compileReq)
      )
      .unsafeRunSync()

    val response = routes.orNotFound
      .run(
        Request[IO](Method.GET, uri"/pipelines/detail-test")
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[PipelineDetailResponse].unsafeRunSync()
    body.aliases should contain("detail-test")
    body.declaredOutputs should contain("q")
    body.inputSchema should contain key "q"
  }

  it should "return 404 for unknown pipeline" in {
    val response = routes.orNotFound
      .run(
        Request[IO](Method.GET, uri"/pipelines/nonexistent")
      )
      .unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  // ---------------------------------------------------------------------------
  // DELETE /pipelines/:ref — remove pipeline
  // ---------------------------------------------------------------------------

  "DELETE /pipelines/:ref" should "delete a pipeline without aliases" in {
    // Compile without a name (no alias)
    val runReq = RunRequest(
      source = "in del: Int\nout del",
      inputs = Map("del" -> Json.fromLong(1))
    )
    val runBody = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/run").withEntity(runReq)
      )
      .unsafeRunSync()
      .as[RunResponse]
      .unsafeRunSync()

    val hash = runBody.structuralHash.get

    // Delete by hash
    val response = routes.orNotFound
      .run(
        Request[IO](Method.DELETE, Uri.unsafeFromString(s"/pipelines/sha256:$hash"))
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok

    // Verify it's gone
    val image = constellation.PipelineStore.get(hash).unsafeRunSync()
    image shouldBe None
  }

  it should "fail to delete pipeline with active aliases" in {
    val compileReq = CompileRequest(
      source = "in keep: String\nout keep",
      name = Some("cannot-delete-alias")
    )
    val compileBody = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/compile").withEntity(compileReq)
      )
      .unsafeRunSync()
      .as[CompileResponse]
      .unsafeRunSync()

    val hash = compileBody.structuralHash.get

    val response = routes.orNotFound
      .run(
        Request[IO](Method.DELETE, Uri.unsafeFromString(s"/pipelines/sha256:$hash"))
      )
      .unsafeRunSync()

    response.status shouldBe Status.Conflict
    val body = response.as[ErrorResponse].unsafeRunSync()
    body.error shouldBe "AliasConflict"
  }

  it should "return 404 for deleting unknown pipeline" in {
    val response = routes.orNotFound
      .run(
        Request[IO](Method.DELETE, uri"/pipelines/sha256:nonexistent")
      )
      .unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  // ---------------------------------------------------------------------------
  // PUT /pipelines/:name/alias — repoint alias
  // ---------------------------------------------------------------------------

  "PUT /pipelines/:name/alias" should "repoint alias to different hash" in {
    // Compile v1
    val v1 = CompileRequest(source = "in x: Int\nout x", name = Some("aliased-prog"))
    val v1Body = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/compile").withEntity(v1)
      )
      .unsafeRunSync()
      .as[CompileResponse]
      .unsafeRunSync()
    val hash1 = v1Body.structuralHash.get

    // Compile v2 (different source = different hash)
    val v2Run = RunRequest(
      source = "in x: Int\nin y: Int\nout x",
      inputs = Map("x" -> Json.fromLong(1), "y" -> Json.fromLong(2))
    )
    val v2Body = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/run").withEntity(v2Run)
      )
      .unsafeRunSync()
      .as[RunResponse]
      .unsafeRunSync()
    val hash2 = v2Body.structuralHash.get

    hash1 should not equal hash2

    // Repoint alias to v2
    val aliasReq = AliasRequest(structuralHash = hash2)
    val response = routes.orNotFound
      .run(
        Request[IO](Method.PUT, uri"/pipelines/aliased-prog/alias").withEntity(aliasReq)
      )
      .unsafeRunSync()

    response.status shouldBe Status.Ok

    // Verify alias now points to v2
    val resolved = constellation.PipelineStore.resolve("aliased-prog").unsafeRunSync()
    resolved shouldBe Some(hash2)
  }

  it should "return 404 when target hash doesn't exist" in {
    val aliasReq = AliasRequest(structuralHash = "nonexistent-hash")
    val response = routes.orNotFound
      .run(
        Request[IO](Method.PUT, uri"/pipelines/some-name/alias").withEntity(aliasReq)
      )
      .unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  // ---------------------------------------------------------------------------
  // Hot-load (alias repoint + execute)
  // ---------------------------------------------------------------------------

  "Hot-load scenario" should "execute new version after alias repoint" in {
    // Compile v1: passthrough x
    val v1 = CompileRequest(source = "in x: Int\nout x", name = Some("hotload-prog"))
    routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/compile").withEntity(v1)
      )
      .unsafeRunSync()

    // Execute v1
    val exec1 = ExecuteRequest(ref = Some("hotload-prog"), inputs = Map("x" -> Json.fromLong(10)))
    val resp1 = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/execute").withEntity(exec1)
      )
      .unsafeRunSync()
      .as[ExecuteResponse]
      .unsafeRunSync()
    resp1.success shouldBe true
    resp1.outputs.get("x") shouldBe Some(Json.fromLong(10))

    // Compile v2: passthrough y
    val v2Run = RunRequest(
      source = "in y: String\nout y",
      inputs = Map("y" -> Json.fromString("v2"))
    )
    val v2Body = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/run").withEntity(v2Run)
      )
      .unsafeRunSync()
      .as[RunResponse]
      .unsafeRunSync()
    val hash2 = v2Body.structuralHash.get

    // Repoint alias to v2
    routes.orNotFound
      .run(
        Request[IO](Method.PUT, uri"/pipelines/hotload-prog/alias")
          .withEntity(AliasRequest(hash2))
      )
      .unsafeRunSync()

    // Execute should now use v2 (expects "y" input)
    val exec2 =
      ExecuteRequest(ref = Some("hotload-prog"), inputs = Map("y" -> Json.fromString("updated")))
    val resp2 = routes.orNotFound
      .run(
        Request[IO](Method.POST, uri"/execute").withEntity(exec2)
      )
      .unsafeRunSync()
      .as[ExecuteResponse]
      .unsafeRunSync()

    resp2.success shouldBe true
    resp2.outputs.get("y") shouldBe Some(Json.fromString("updated"))
  }
}
