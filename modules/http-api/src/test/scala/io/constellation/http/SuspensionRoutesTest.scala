package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.circe.CirceEntityCodec.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.constellation.impl.{ConstellationImpl, InMemorySuspensionStore}
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.FunctionRegistry
import io.constellation.http.ApiModels.*
import io.circe.Json

class SuspensionRoutesTest extends AnyFlatSpec with Matchers {

  // Helper: create routes with a SuspensionStore configured
  private def routesWithStore: (HttpRoutes[IO], io.constellation.SuspensionStore) = {
    val (routes, store) = (for {
      suspensionStore <- InMemorySuspensionStore.init
      constellation <- ConstellationImpl
        .builder()
        .withSuspensionStore(suspensionStore)
        .build()
      compiler         = LangCompiler.empty
      functionRegistry = FunctionRegistry.empty
      routes = ConstellationRoutes(constellation, compiler, functionRegistry).routes
    } yield (routes, suspensionStore)).unsafeRunSync()
    (routes, store)
  }

  // Helper: create routes without a SuspensionStore
  private def routesWithoutStore: HttpRoutes[IO] = {
    val constellation    = ConstellationImpl.init.unsafeRunSync()
    val compiler         = LangCompiler.empty
    val functionRegistry = FunctionRegistry.empty
    ConstellationRoutes(constellation, compiler, functionRegistry).routes
  }

  // ---------------------------------------------------------------------------
  // GET /executions — List suspended executions
  // ---------------------------------------------------------------------------

  "GET /executions" should "return empty list when no suspensions exist" in {
    val (routes, _) = routesWithStore
    val request     = Request[IO](Method.GET, uri"/executions")
    val response    = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ExecutionListResponse].unsafeRunSync()
    body.executions shouldBe empty
  }

  it should "return empty list when no store is configured" in {
    val routes   = routesWithoutStore
    val request  = Request[IO](Method.GET, uri"/executions")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ExecutionListResponse].unsafeRunSync()
    body.executions shouldBe empty
  }

  it should "list suspended executions after /run with partial inputs" in {
    val (routes, _) = routesWithStore

    // Run a script with partial inputs to create a suspension
    val runRequest = RunRequest(
      source = """
        in x: Int
        in y: Int
        out x
      """,
      inputs = Map("x" -> Json.fromLong(5))
      // missing "y"
    )

    val runReq = Request[IO](Method.POST, uri"/run").withEntity(runRequest)
    val runResponse = routes.orNotFound.run(runReq).unsafeRunSync()
    runResponse.status shouldBe Status.Ok
    val runBody = runResponse.as[RunResponse].unsafeRunSync()
    runBody.status shouldBe Some("suspended")

    // Now list executions
    val listRequest  = Request[IO](Method.GET, uri"/executions")
    val listResponse = routes.orNotFound.run(listRequest).unsafeRunSync()

    listResponse.status shouldBe Status.Ok
    val listBody = listResponse.as[ExecutionListResponse].unsafeRunSync()
    listBody.executions should have size 1
    listBody.executions.head.missingInputs should contain key "y"
  }

  // ---------------------------------------------------------------------------
  // GET /executions/:id — Get execution detail
  // ---------------------------------------------------------------------------

  "GET /executions/:id" should "return 404 for unknown execution" in {
    val (routes, _) = routesWithStore
    val request  = Request[IO](Method.GET, uri"/executions/550e8400-e29b-41d4-a716-446655440000")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  it should "return 404 when no store is configured" in {
    val routes   = routesWithoutStore
    val request  = Request[IO](Method.GET, uri"/executions/550e8400-e29b-41d4-a716-446655440000")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
    val body = response.as[ErrorResponse].unsafeRunSync()
    body.message should include("no suspension store configured")
  }

  it should "return execution detail after suspension" in {
    val (routes, _) = routesWithStore

    // Create a suspension via /run
    val runRequest = RunRequest(
      source = """
        in x: Int
        in y: Int
        out x
      """,
      inputs = Map("x" -> Json.fromLong(5))
    )

    val runReq = Request[IO](Method.POST, uri"/run").withEntity(runRequest)
    val runResponse = routes.orNotFound.run(runReq).unsafeRunSync()
    val runBody = runResponse.as[RunResponse].unsafeRunSync()
    val executionId = runBody.executionId.get

    // Get detail by executionId
    val detailRequest  = Request[IO](Method.GET, Uri.unsafeFromString(s"/executions/$executionId"))
    val detailResponse = routes.orNotFound.run(detailRequest).unsafeRunSync()

    detailResponse.status shouldBe Status.Ok
    val detailBody = detailResponse.as[ExecutionSummary].unsafeRunSync()
    detailBody.executionId shouldBe executionId
    detailBody.missingInputs should contain key "y"
  }

  // ---------------------------------------------------------------------------
  // POST /executions/:id/resume — Resume suspended execution
  // ---------------------------------------------------------------------------

  "POST /executions/:id/resume" should "return 404 when no store is configured" in {
    val routes = routesWithoutStore
    val resumeReq = ResumeRequest(additionalInputs = Some(Map("y" -> Json.fromLong(10))))
    val request = Request[IO](Method.POST, uri"/executions/550e8400-e29b-41d4-a716-446655440000/resume")
      .withEntity(resumeReq)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
    val body = response.as[ErrorResponse].unsafeRunSync()
    body.message should include("no suspension store configured")
  }

  it should "return 404 for unknown execution ID" in {
    val (routes, _) = routesWithStore
    val resumeReq = ResumeRequest(additionalInputs = Some(Map("y" -> Json.fromLong(10))))
    val request = Request[IO](Method.POST, uri"/executions/550e8400-e29b-41d4-a716-446655440000/resume")
      .withEntity(resumeReq)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  it should "resume a suspended execution and return completed status" in {
    val (routes, _) = routesWithStore

    // Step 1: Run with partial inputs → suspended
    val runRequest = RunRequest(
      source = """
        in x: Int
        in y: Int
        out x
      """,
      inputs = Map("x" -> Json.fromLong(5))
    )

    val runReq = Request[IO](Method.POST, uri"/run").withEntity(runRequest)
    val runResponse = routes.orNotFound.run(runReq).unsafeRunSync()
    val runBody = runResponse.as[RunResponse].unsafeRunSync()
    runBody.status shouldBe Some("suspended")
    val executionId = runBody.executionId.get

    // Step 2: Resume with the missing input
    val resumeReq = ResumeRequest(additionalInputs = Some(Map("y" -> Json.fromLong(10))))
    val request = Request[IO](Method.POST, Uri.unsafeFromString(s"/executions/$executionId/resume"))
      .withEntity(resumeReq)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[ExecuteResponse].unsafeRunSync()
    body.success shouldBe true
    body.status shouldBe Some("completed")
    body.resumptionCount shouldBe Some(1)
    body.outputs.get("x") shouldBe Some(Json.fromLong(5))
  }

  it should "auto-delete completed execution from store" in {
    val (routes, _) = routesWithStore

    // Create a suspension
    val runRequest = RunRequest(
      source = """
        in x: Int
        in y: Int
        out x
      """,
      inputs = Map("x" -> Json.fromLong(5))
    )

    val runReq = Request[IO](Method.POST, uri"/run").withEntity(runRequest)
    val runResponse = routes.orNotFound.run(runReq).unsafeRunSync()
    val runBody = runResponse.as[RunResponse].unsafeRunSync()
    val executionId = runBody.executionId.get

    // Resume to completion
    val resumeReq = ResumeRequest(additionalInputs = Some(Map("y" -> Json.fromLong(10))))
    val request = Request[IO](Method.POST, Uri.unsafeFromString(s"/executions/$executionId/resume"))
      .withEntity(resumeReq)
    routes.orNotFound.run(request).unsafeRunSync()

    // Verify execution is removed from store
    val listRequest  = Request[IO](Method.GET, uri"/executions")
    val listResponse = routes.orNotFound.run(listRequest).unsafeRunSync()
    val listBody     = listResponse.as[ExecutionListResponse].unsafeRunSync()
    listBody.executions shouldBe empty
  }

  // ---------------------------------------------------------------------------
  // DELETE /executions/:id — Delete suspended execution
  // ---------------------------------------------------------------------------

  "DELETE /executions/:id" should "return 404 when no store is configured" in {
    val routes   = routesWithoutStore
    val request  = Request[IO](Method.DELETE, uri"/executions/550e8400-e29b-41d4-a716-446655440000")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  it should "return 404 for unknown execution" in {
    val (routes, _) = routesWithStore
    val request  = Request[IO](Method.DELETE, uri"/executions/550e8400-e29b-41d4-a716-446655440000")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  it should "delete a suspended execution" in {
    val (routes, _) = routesWithStore

    // Create a suspension
    val runRequest = RunRequest(
      source = """
        in x: Int
        in y: Int
        out x
      """,
      inputs = Map("x" -> Json.fromLong(5))
    )

    val runReq = Request[IO](Method.POST, uri"/run").withEntity(runRequest)
    val runResponse = routes.orNotFound.run(runReq).unsafeRunSync()
    val runBody = runResponse.as[RunResponse].unsafeRunSync()
    val executionId = runBody.executionId.get

    // Delete it
    val deleteRequest  = Request[IO](Method.DELETE, Uri.unsafeFromString(s"/executions/$executionId"))
    val deleteResponse = routes.orNotFound.run(deleteRequest).unsafeRunSync()

    deleteResponse.status shouldBe Status.Ok
    val deleteBody = deleteResponse.as[Json].unsafeRunSync()
    deleteBody.hcursor.downField("deleted").as[Boolean] shouldBe Right(true)

    // Verify it's gone
    val listRequest  = Request[IO](Method.GET, uri"/executions")
    val listResponse = routes.orNotFound.run(listRequest).unsafeRunSync()
    val listBody     = listResponse.as[ExecutionListResponse].unsafeRunSync()
    listBody.executions shouldBe empty
  }

  it should "return 404 when resuming a deleted execution" in {
    val (routes, _) = routesWithStore

    // Create a suspension
    val runRequest = RunRequest(
      source = """
        in x: Int
        in y: Int
        out x
      """,
      inputs = Map("x" -> Json.fromLong(5))
    )

    val runReq = Request[IO](Method.POST, uri"/run").withEntity(runRequest)
    val runResponse = routes.orNotFound.run(runReq).unsafeRunSync()
    val runBody = runResponse.as[RunResponse].unsafeRunSync()
    val executionId = runBody.executionId.get

    // Delete it
    val deleteRequest = Request[IO](Method.DELETE, Uri.unsafeFromString(s"/executions/$executionId"))
    routes.orNotFound.run(deleteRequest).unsafeRunSync()

    // Try to resume — should 404
    val resumeReq = ResumeRequest(additionalInputs = Some(Map("y" -> Json.fromLong(10))))
    val request = Request[IO](Method.POST, Uri.unsafeFromString(s"/executions/$executionId/resume"))
      .withEntity(resumeReq)
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  // ---------------------------------------------------------------------------
  // Auto-save: /execute with partial inputs should auto-save
  // ---------------------------------------------------------------------------

  "Auto-save" should "save suspension from /execute with partial inputs" in {
    val (routes, _) = routesWithStore

    // Compile first
    val compileRequest = CompileRequest(
      source = """
        in x: Int
        in y: Int
        out x
      """,
      dagName = Some("auto-save-test")
    )
    val compileReq = Request[IO](Method.POST, uri"/compile").withEntity(compileRequest)
    routes.orNotFound.run(compileReq).unsafeRunSync()

    // Execute with partial inputs
    val executeRequest = ExecuteRequest(
      dagName = Some("auto-save-test"),
      inputs = Map("x" -> Json.fromLong(42))
    )

    val execReq = Request[IO](Method.POST, uri"/execute").withEntity(executeRequest)
    val response = routes.orNotFound.run(execReq).unsafeRunSync()
    response.status shouldBe Status.Ok
    val body = response.as[ExecuteResponse].unsafeRunSync()
    body.status shouldBe Some("suspended")

    // Verify it was saved to the store
    val listRequest  = Request[IO](Method.GET, uri"/executions")
    val listResponse = routes.orNotFound.run(listRequest).unsafeRunSync()
    val listBody     = listResponse.as[ExecutionListResponse].unsafeRunSync()
    listBody.executions should have size 1
  }

  it should "not save completed executions to store" in {
    val (routes, _) = routesWithStore

    // Run with all inputs → completed
    val runRequest = RunRequest(
      source = """
        in x: Int
        out x
      """,
      inputs = Map("x" -> Json.fromLong(5))
    )

    val runReq = Request[IO](Method.POST, uri"/run").withEntity(runRequest)
    val response = routes.orNotFound.run(runReq).unsafeRunSync()
    val body = response.as[RunResponse].unsafeRunSync()
    body.status shouldBe Some("completed")

    // Should not have any suspensions
    val listRequest  = Request[IO](Method.GET, uri"/executions")
    val listResponse = routes.orNotFound.run(listRequest).unsafeRunSync()
    val listBody     = listResponse.as[ExecutionListResponse].unsafeRunSync()
    listBody.executions shouldBe empty
  }
}
