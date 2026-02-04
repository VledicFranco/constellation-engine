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

import java.nio.file.Path

class CanaryIntegrationTest extends AnyFlatSpec with Matchers {

  private val sourceV1 =
    """in x: Int
      |out x""".stripMargin

  private val sourceV2 =
    """in x: Int
      |in y: Int
      |out x""".stripMargin

  /** Create routes with versioning and canary routing enabled. */
  private def routesWithCanary(): (HttpRoutes[IO], PipelineVersionStore, CanaryRouter, ConstellationImpl) = {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val compiler      = LangCompiler.empty
    val registry      = FunctionRegistry.empty
    val vs            = PipelineVersionStore.init.unsafeRunSync()
    val cr            = CanaryRouter.init.unsafeRunSync()
    val filePathRef   = Ref.of[IO, Map[String, Path]](Map.empty).unsafeRunSync()
    val routes = new ConstellationRoutes(
      constellation,
      compiler,
      registry,
      scheduler = None,
      lifecycle = None,
      versionStore = Some(vs),
      filePathMap = Some(filePathRef),
      canaryRouter = Some(cr)
    ).routes
    (routes, vs, cr, constellation)
  }

  /** Create routes without canary router. */
  private def routesWithoutCanary(): HttpRoutes[IO] = {
    val constellation = ConstellationImpl.init.unsafeRunSync()
    val compiler      = LangCompiler.empty
    val registry      = FunctionRegistry.empty
    val vs            = PipelineVersionStore.init.unsafeRunSync()
    val filePathRef   = Ref.of[IO, Map[String, Path]](Map.empty).unsafeRunSync()
    new ConstellationRoutes(
      constellation,
      compiler,
      registry,
      scheduler = None,
      lifecycle = None,
      versionStore = Some(vs),
      filePathMap = Some(filePathRef),
      canaryRouter = None
    ).routes
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

  // --- Reload with canary ---

  "POST /pipelines/:name/reload with canary" should "start canary deployment and include canary state in response" in {
    val (routes, _, cr, _) = routesWithCanary()

    // Compile v1
    compilePipeline(routes, "scoring", sourceV1)

    // Reload with canary config
    val reloadReq = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
      .withEntity(
        ReloadRequest(
          source = Some(sourceV2),
          canary = Some(CanaryConfigRequest(initialWeight = Some(0.1)))
        )
      )
    val resp = routes.orNotFound.run(reloadReq).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[ReloadResponse].unsafeRunSync()
    body.success shouldBe true
    body.changed shouldBe true
    body.canary shouldBe defined
    body.canary.get.status shouldBe "observing"
    body.canary.get.currentWeight shouldBe 0.1

    // Verify canary state in router
    val state = cr.getState("scoring").unsafeRunSync()
    state shouldBe defined
    state.get.status shouldBe CanaryStatus.Observing
  }

  it should "return 409 when canary already active for pipeline" in {
    val (routes, _, _, _) = routesWithCanary()

    // Compile v1
    compilePipeline(routes, "scoring", sourceV1)

    // Start first canary
    val reload1 = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
      .withEntity(
        ReloadRequest(
          source = Some(sourceV2),
          canary = Some(CanaryConfigRequest())
        )
      )
    val resp1 = routes.orNotFound.run(reload1).unsafeRunSync()
    resp1.status shouldBe Status.Ok

    // Try second canary — different source so hash changes
    val sourceV3 =
      """in x: Int
        |in z: String
        |out x""".stripMargin
    val reload2 = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
      .withEntity(
        ReloadRequest(
          source = Some(sourceV3),
          canary = Some(CanaryConfigRequest())
        )
      )
    val resp2 = routes.orNotFound.run(reload2).unsafeRunSync()

    resp2.status shouldBe Status.Conflict
  }

  it should "not start canary when pipeline didn't change" in {
    val (routes, _, cr, _) = routesWithCanary()

    compilePipeline(routes, "scoring", sourceV1)

    // Reload with same source (no change) but canary config
    val reloadReq = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
      .withEntity(
        ReloadRequest(
          source = Some(sourceV1),
          canary = Some(CanaryConfigRequest())
        )
      )
    val resp = routes.orNotFound.run(reloadReq).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[ReloadResponse].unsafeRunSync()
    body.changed shouldBe false
    body.canary shouldBe None

    cr.getState("scoring").unsafeRunSync() shouldBe None
  }

  // --- Canary status endpoint ---

  "GET /pipelines/:name/canary" should "return canary status with metrics" in {
    val (routes, _, _, _) = routesWithCanary()

    compilePipeline(routes, "scoring", sourceV1)

    // Start canary
    val reloadReq = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
      .withEntity(
        ReloadRequest(
          source = Some(sourceV2),
          canary = Some(CanaryConfigRequest(initialWeight = Some(0.2)))
        )
      )
    routes.orNotFound.run(reloadReq).unsafeRunSync()

    // Get status
    val req  = Request[IO](Method.GET, uri"/pipelines/scoring/canary")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[CanaryStateResponse].unsafeRunSync()
    body.pipelineName shouldBe "scoring"
    body.currentWeight shouldBe 0.2
    body.status shouldBe "observing"
    body.metrics.oldVersion.requests shouldBe 0
    body.metrics.newVersion.requests shouldBe 0
  }

  it should "return 404 when no canary active" in {
    val (routes, _, _, _) = routesWithCanary()

    val req  = Request[IO](Method.GET, uri"/pipelines/scoring/canary")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.NotFound
  }

  // --- Canary promote endpoint ---

  "POST /pipelines/:name/canary/promote" should "advance canary step" in {
    val (routes, _, _, _) = routesWithCanary()

    compilePipeline(routes, "scoring", sourceV1)

    val reloadReq = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
      .withEntity(
        ReloadRequest(
          source = Some(sourceV2),
          canary = Some(CanaryConfigRequest(
            initialWeight = Some(0.1),
            promotionSteps = Some(List(0.25, 0.50, 1.0))
          ))
        )
      )
    routes.orNotFound.run(reloadReq).unsafeRunSync()

    // Promote
    val req  = Request[IO](Method.POST, uri"/pipelines/scoring/canary/promote")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[CanaryStateResponse].unsafeRunSync()
    body.currentStep shouldBe 1
    body.currentWeight shouldBe 0.50
    body.status shouldBe "observing"
  }

  it should "return 404 when no canary active" in {
    val (routes, _, _, _) = routesWithCanary()

    val req  = Request[IO](Method.POST, uri"/pipelines/scoring/canary/promote")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.NotFound
  }

  // --- Canary rollback endpoint ---

  "POST /pipelines/:name/canary/rollback" should "revert canary" in {
    val (routes, _, _, _) = routesWithCanary()

    compilePipeline(routes, "scoring", sourceV1)

    val reloadReq = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
      .withEntity(
        ReloadRequest(
          source = Some(sourceV2),
          canary = Some(CanaryConfigRequest())
        )
      )
    routes.orNotFound.run(reloadReq).unsafeRunSync()

    val req  = Request[IO](Method.POST, uri"/pipelines/scoring/canary/rollback")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[CanaryStateResponse].unsafeRunSync()
    body.status shouldBe "rolled_back"
  }

  it should "return 404 when no canary active" in {
    val (routes, _, _, _) = routesWithCanary()

    val req  = Request[IO](Method.POST, uri"/pipelines/scoring/canary/rollback")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.NotFound
  }

  // --- Canary abort endpoint ---

  "DELETE /pipelines/:name/canary" should "abort canary" in {
    val (routes, _, _, _) = routesWithCanary()

    compilePipeline(routes, "scoring", sourceV1)

    val reloadReq = Request[IO](Method.POST, uri"/pipelines/scoring/reload")
      .withEntity(
        ReloadRequest(
          source = Some(sourceV2),
          canary = Some(CanaryConfigRequest())
        )
      )
    routes.orNotFound.run(reloadReq).unsafeRunSync()

    val req  = Request[IO](Method.DELETE, uri"/pipelines/scoring/canary")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.Ok
    val body = resp.as[CanaryStateResponse].unsafeRunSync()
    body.status shouldBe "rolled_back"
  }

  it should "return 404 when no canary active" in {
    val (routes, _, _, _) = routesWithCanary()

    val req  = Request[IO](Method.DELETE, uri"/pipelines/scoring/canary")
    val resp = routes.orNotFound.run(req).unsafeRunSync()

    resp.status shouldBe Status.NotFound
  }

  // --- Canary endpoints when router not configured ---

  "Canary endpoints" should "return 400 when canary router not configured" in {
    val routes = routesWithoutCanary()

    val getReq  = Request[IO](Method.GET, uri"/pipelines/scoring/canary")
    val getResp = routes.orNotFound.run(getReq).unsafeRunSync()
    getResp.status shouldBe Status.BadRequest

    val promoteReq  = Request[IO](Method.POST, uri"/pipelines/scoring/canary/promote")
    val promoteResp = routes.orNotFound.run(promoteReq).unsafeRunSync()
    promoteResp.status shouldBe Status.BadRequest

    val rollbackReq  = Request[IO](Method.POST, uri"/pipelines/scoring/canary/rollback")
    val rollbackResp = routes.orNotFound.run(rollbackReq).unsafeRunSync()
    rollbackResp.status shouldBe Status.BadRequest

    val deleteReq  = Request[IO](Method.DELETE, uri"/pipelines/scoring/canary")
    val deleteResp = routes.orNotFound.run(deleteReq).unsafeRunSync()
    deleteResp.status shouldBe Status.BadRequest
  }
}
