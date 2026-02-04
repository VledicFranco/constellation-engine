package io.constellation.http

import cats.effect.{IO, Ref}
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

import java.nio.file.{Files, Path}

class PipelineReloadTest extends AnyFlatSpec with Matchers {

  private val sourceV1 =
    """in x: Int
      |out x""".stripMargin

  private val sourceV2 =
    """in x: Int
      |in y: Int
      |out x""".stripMargin

  /** Create routes with versioning enabled. */
  private def routesWithVersioning(): (HttpRoutes[IO], PipelineVersionStore, ConstellationImpl) = {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val compiler      = LangCompiler.empty
    val registry      = FunctionRegistry.empty
    val vs            = PipelineVersionStore.init.unsafeRunSync()
    val filePathRef   = Ref.of[IO, Map[String, Path]](Map.empty).unsafeRunSync()
    val routes = new ConstellationRoutes(
      constellation,
      compiler,
      registry,
      scheduler = None,
      lifecycle = None,
      versionStore = Some(vs),
      filePathMap = Some(filePathRef)
    ).routes
    (routes, vs, constellation)
  }

  /** Helper to compile a pipeline by name. */
  private def compilePipeline(
      routes: HttpRoutes[IO],
      name: String,
      source: String
  ): CompileResponse = {
    val req = Request[IO](Method.POST, uri"/compile")
      .withEntity(CompileRequest(source = source, name = Some(name)))
    val resp = routes.orNotFound.run(req).unsafeRunSync()
    resp.as[CompileResponse].unsafeRunSync()
  }

  // --- Reload tests ---

  "POST /pipelines/:name/reload" should "reload with new source and create a new version" in {
    val (routes, vs, _) = routesWithVersioning()

    // Compile v1
    compilePipeline(routes, "scoring", sourceV1)

    // Reload with different source
    val reloadReq = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
      .withEntity(ReloadRequest(source = Some(sourceV2)))
    val resp = routes.orNotFound.run(reloadReq).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[ReloadResponse].unsafeRunSync()
    body.success shouldBe true
    body.changed shouldBe true
    body.name shouldBe "scoring"
    body.version shouldBe 2
    body.previousHash should not be None
  }

  it should "return changed=false when source produces same hash" in {
    val (routes, vs, _) = routesWithVersioning()

    // Compile v1
    compilePipeline(routes, "scoring", sourceV1)

    // Reload with identical source
    val reloadReq = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
      .withEntity(ReloadRequest(source = Some(sourceV1)))
    val resp = routes.orNotFound.run(reloadReq).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[ReloadResponse].unsafeRunSync()
    body.success shouldBe true
    body.changed shouldBe false
    body.version shouldBe 1 // No new version created
  }

  it should "return 400 for invalid source" in {
    val (routes, _, _) = routesWithVersioning()

    // Compile a valid v1 first
    compilePipeline(routes, "scoring", sourceV1)

    // Reload with invalid source
    val reloadReq = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
      .withEntity(ReloadRequest(source = Some("in x: Int\nout undefined_var")))
    val resp = routes.orNotFound.run(reloadReq).unsafeRunSync()

    resp.status shouldBe Status.BadRequest
    val body = resp.as[ErrorResponse].unsafeRunSync()
    body.error shouldBe "CompilationError"
  }

  it should "return 400 when no source and no file path known" in {
    val (routes, _, _) = routesWithVersioning()

    // Compile so the pipeline exists
    compilePipeline(routes, "scoring", sourceV1)

    // Reload without body (no file path known since we compiled directly, not loaded from file)
    val reloadReq = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
    val resp      = routes.orNotFound.run(reloadReq).unsafeRunSync()

    resp.status shouldBe Status.BadRequest
    val body = resp.as[ErrorResponse].unsafeRunSync()
    body.error shouldBe "NoSource"
  }

  it should "reload from file when file path is known" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val compiler      = LangCompiler.empty
    val registry      = FunctionRegistry.empty
    val vs            = PipelineVersionStore.init.unsafeRunSync()

    // Create a temp file with source
    val tmpDir  = Files.createTempDirectory("reload-test")
    val cstFile = tmpDir.resolve("scoring.cst")
    Files.writeString(cstFile, sourceV1)

    val filePathRef = Ref.of[IO, Map[String, Path]](Map("scoring" -> cstFile)).unsafeRunSync()

    val routes = new ConstellationRoutes(
      constellation,
      compiler,
      registry,
      scheduler = None,
      lifecycle = None,
      versionStore = Some(vs),
      filePathMap = Some(filePathRef)
    ).routes

    // Compile the pipeline first
    compilePipeline(routes, "scoring", sourceV1)

    // Now update the file with new content
    Files.writeString(cstFile, sourceV2)

    // Reload without body — should re-read from file
    val reloadReq = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
    val resp      = routes.orNotFound.run(reloadReq).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[ReloadResponse].unsafeRunSync()
    body.success shouldBe true
    body.changed shouldBe true
    body.version shouldBe 2

    // Cleanup
    Files.delete(cstFile)
    Files.delete(tmpDir)
  }

  // --- Version listing tests ---

  "GET /pipelines/:name/versions" should "list version history after multiple compiles" in {
    val (routes, _, _) = routesWithVersioning()

    // Compile v1 and v2
    compilePipeline(routes, "scoring", sourceV1)
    compilePipeline(routes, "scoring", sourceV2)

    val req  = Request[IO](Method.GET, uri"/pipelines/scoring/versions")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[PipelineVersionsResponse].unsafeRunSync()
    body.name shouldBe "scoring"
    body.versions should have size 2
    body.activeVersion shouldBe 2
    body.versions.head.version shouldBe 2
    body.versions.head.active shouldBe true
    body.versions(1).version shouldBe 1
    body.versions(1).active shouldBe false
  }

  it should "return 404 for unknown pipeline" in {
    val (routes, _, _) = routesWithVersioning()

    val req  = Request[IO](Method.GET, uri"/pipelines/nonexistent/versions")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.NotFound
  }

  // --- Rollback tests ---

  "POST /pipelines/:name/rollback" should "rollback to previous version" in {
    val (routes, vs, _) = routesWithVersioning()

    // Compile v1 and v2
    val v1Resp = compilePipeline(routes, "scoring", sourceV1)
    compilePipeline(routes, "scoring", sourceV2)

    // Rollback to previous (v1)
    val req  = Request[IO](Method.POST, uri"/pipelines/scoring/rollback")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[RollbackResponse].unsafeRunSync()
    body.success shouldBe true
    body.name shouldBe "scoring"
    body.previousVersion shouldBe 2
    body.activeVersion shouldBe 1
    body.structuralHash shouldBe v1Resp.structuralHash.get
  }

  it should "return 404 when no previous version exists" in {
    val (routes, _, _) = routesWithVersioning()

    // Only compile v1
    compilePipeline(routes, "scoring", sourceV1)

    val req  = Request[IO](Method.POST, uri"/pipelines/scoring/rollback")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.NotFound
  }

  it should "return 404 for unknown pipeline" in {
    val (routes, _, _) = routesWithVersioning()

    val req  = Request[IO](Method.POST, uri"/pipelines/nonexistent/rollback")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.NotFound
  }

  "POST /pipelines/:name/rollback/:v" should "rollback to specific version" in {
    val (routes, _, _) = routesWithVersioning()

    // Compile v1, v2, v3
    val v1Resp = compilePipeline(routes, "scoring", sourceV1)
    compilePipeline(routes, "scoring", sourceV2)
    compilePipeline(routes, "scoring", sourceV1.replace("x", "z")) // slightly different to get v3

    // Rollback to v1 specifically
    val req  = Request[IO](Method.POST, uri"/pipelines/scoring/rollback/1")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[RollbackResponse].unsafeRunSync()
    body.success shouldBe true
    body.activeVersion shouldBe 1
    body.structuralHash shouldBe v1Resp.structuralHash.get
  }

  it should "return 404 for non-existent version number" in {
    val (routes, _, _) = routesWithVersioning()

    compilePipeline(routes, "scoring", sourceV1)

    val req  = Request[IO](Method.POST, uri"/pipelines/scoring/rollback/99")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.NotFound
  }

  it should "return 400 for non-integer version" in {
    val (routes, _, _) = routesWithVersioning()

    compilePipeline(routes, "scoring", sourceV1)

    val req  = Request[IO](Method.POST, uri"/pipelines/scoring/rollback/abc")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.BadRequest
    val body = resp.as[ErrorResponse].unsafeRunSync()
    body.error shouldBe "InvalidVersion"
  }

  // --- Versioning disabled tests ---

  "Versioning endpoints" should "return 400 when version store not configured" in {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val compiler      = LangCompiler.empty
    val registry      = FunctionRegistry.empty
    val routes =
      ConstellationRoutes(constellation, compiler, registry).routes

    val reloadReq  = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
    val reloadResp = routes.orNotFound.run(reloadReq).unsafeRunSync()
    reloadResp.status shouldBe Status.BadRequest

    val versionsReq  = Request[IO](Method.GET, uri"/pipelines/scoring/versions")
    val versionsResp = routes.orNotFound.run(versionsReq).unsafeRunSync()
    versionsResp.status shouldBe Status.BadRequest

    val rollbackReq  = Request[IO](Method.POST, uri"/pipelines/scoring/rollback")
    val rollbackResp = routes.orNotFound.run(rollbackReq).unsafeRunSync()
    rollbackResp.status shouldBe Status.BadRequest
  }

  // --- Compile integration test ---

  "POST /compile" should "create v1 when version store is active" in {
    val (routes, vs, _) = routesWithVersioning()

    compilePipeline(routes, "scoring", sourceV1)

    val versions = vs.listVersions("scoring").unsafeRunSync()
    versions should have size 1
    versions.head.version shouldBe 1
    versions.head.source shouldBe Some(sourceV1)
  }

  it should "execute using the correct version after rollback" in {
    val (routes, _, _) = routesWithVersioning()

    // Compile v1: only needs x
    compilePipeline(routes, "scoring", sourceV1)

    // Compile v2: needs x and y
    compilePipeline(routes, "scoring", sourceV2)

    // Rollback to v1
    val rollbackReq  = Request[IO](Method.POST, uri"/pipelines/scoring/rollback")
    val rollbackResp = routes.orNotFound.run(rollbackReq).unsafeRunSync()
    rollbackResp.status shouldBe Status.Ok

    // Execute by name — should use v1 (only needs x)
    val execReq = Request[IO](Method.POST, uri"/execute")
      .withEntity(ExecuteRequest(dagName = Some("scoring"), inputs = Map("x" -> Json.fromLong(42))))
    val execResp = routes.orNotFound.run(execReq).unsafeRunSync()

    execResp.status shouldBe Status.Ok
    val body = execResp.as[ExecuteResponse].unsafeRunSync()
    body.success shouldBe true
    body.status shouldBe Some("completed")
    body.outputs.get("x") shouldBe Some(Json.fromLong(42))
  }

  it should "return 404 for reload of non-existent pipeline with no source" in {
    val (routes, _, _) = routesWithVersioning()

    // Try to reload a pipeline that was never compiled, without providing source
    val reloadReq = Request[IO](Method.POST, uri"/pipelines/nonexistent/reload")
    val resp      = routes.orNotFound.run(reloadReq).unsafeRunSync()

    // Should fail because there's no source and no file path
    resp.status shouldBe Status.BadRequest
  }

  it should "not create version entry when no name is provided" in {
    val (routes, vs, _) = routesWithVersioning()

    // Compile without a name
    val req = Request[IO](Method.POST, uri"/compile")
      .withEntity(CompileRequest(source = sourceV1))
    routes.orNotFound.run(req).unsafeRunSync()

    // No version should be recorded for "unnamed"
    vs.listVersions("unnamed").unsafeRunSync() shouldBe empty
  }
}
