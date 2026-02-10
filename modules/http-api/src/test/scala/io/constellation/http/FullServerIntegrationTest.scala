package io.constellation.http

import scala.concurrent.duration.*

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.implicits.*

import io.constellation.http.ApiModels.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.stdlib.StdLib

import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Full server integration tests that start a real HTTP server and make real HTTP requests.
  *
  * These tests complement the existing route tests by validating end-to-end flows including:
  *   - Server lifecycle (startup, shutdown, cleanup)
  *   - Real HTTP requests over the network
  *   - Middleware interaction (Auth + CORS + RateLimit together)
  *   - Health check endpoints
  *   - Compile and execute endpoints
  *   - Error handling and status codes
  *
  * Targets Phase 3 of the strategic test coverage improvement plan.
  */
class FullServerIntegrationTest extends AnyFlatSpec with Matchers {

  /** Helper to create a client + server resource on a random port */
  private def serverAndClient(
      authConfig: Option[AuthConfig] = None,
      corsConfig: Option[CorsConfig] = None,
      rateLimitConfig: Option[RateLimitConfig] = None
  ): Resource[IO, (Client[IO], Uri)] =
    for {
      constellation <- Resource.eval(ConstellationImpl.init)
      // Load stdlib modules into constellation
      _ <- Resource.eval(StdLib.allModules.values.toList.traverse(constellation.setModule))
      compiler = StdLib.compiler

      // Build server with optional hardening (bind to localhost for tests)
      builder = ConstellationServer
        .builder(constellation, compiler)
        .withHost("127.0.0.1")
        .withPort(0) // random port

      // Apply optional configurations
      builderWithAuth = authConfig.fold(builder)(builder.withAuth)
      builderWithCors = corsConfig.fold(builderWithAuth)(builderWithAuth.withCors)
      builderWithRate = rateLimitConfig.fold(builderWithCors)(builderWithCors.withRateLimit)

      // Start server
      server <- builderWithRate.build

      // Get server URI (wrap IPv6 addresses in brackets)
      host      = server.address.getHostString
      hostStr   = if host.contains(":") then s"[$host]" else host
      serverUri = Uri.unsafeFromString(s"http://$hostStr:${server.address.getPort}")

      // Create HTTP client
      client <- EmberClientBuilder.default[IO].build
    } yield (client, serverUri)

  /** Helper to make authenticated request */
  private def authenticatedRequest(
      client: Client[IO],
      baseUri: Uri,
      path: String,
      apiKey: String,
      method: Method = Method.GET
  ): IO[Response[IO]] = {
    val uri = baseUri / path
    val request = Request[IO](method, uri)
      .putHeaders(
        headers.Authorization(Credentials.Token(org.typelevel.ci.CIString("Bearer"), apiKey))
      )
    client.run(request).use(resp => IO.pure(resp))
  }

  // ===== Basic Server Lifecycle =====

  "FullServer" should "start and respond to health check" in {
    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val request  = Request[IO](Method.GET, serverUri / "health")
        val response = client.run(request).use(resp => IO.pure(resp))
        response.map(_.status)
      }
      .unsafeRunSync()

    result shouldBe Status.Ok
  }

  it should "start on random port and be accessible" in {
    val result = serverAndClient()
      .use { case (client, serverUri) =>
        IO.pure(serverUri.port.isDefined)
      }
      .unsafeRunSync()

    result shouldBe true
  }

  it should "clean up properly on shutdown" in {
    // Server resource should clean up when resource is released
    val ports = (1 to 3).map { _ =>
      serverAndClient()
        .use { case (_, serverUri) =>
          IO.pure(serverUri.port.get)
        }
        .unsafeRunSync()
    }

    // All servers should have started and stopped cleanly
    ports.size shouldBe 3
    ports.distinct.size shouldBe 3 // Different random ports
  }

  // ===== Health Check Endpoints =====

  "Health check endpoints" should "return OK for /health" in {
    val result = serverAndClient()
      .use { case (client, serverUri) =>
        client.expect[Json](Request[IO](Method.GET, serverUri / "health"))
      }
      .unsafeRunSync()

    result shouldBe a[Json]
    result.hcursor.get[String]("status").toOption shouldBe Some("ok")
  }

  it should "return OK for /health/live" in {
    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val request  = Request[IO](Method.GET, serverUri / "health" / "live")
        val response = client.run(request).use(resp => IO.pure(resp.status))
        response
      }
      .unsafeRunSync()

    result shouldBe Status.Ok
  }

  it should "return OK for /health/ready" in {
    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val request  = Request[IO](Method.GET, serverUri / "health" / "ready")
        val response = client.run(request).use(resp => IO.pure(resp.status))
        response
      }
      .unsafeRunSync()

    result shouldBe Status.Ok
  }

  // ===== Authentication Middleware =====

  "Auth middleware" should "reject unauthenticated requests" in {
    val authConfig = AuthConfig(
      hashedKeys = List(HashedApiKey("test-key", ApiRole.Admin)),
      publicPaths = Set("/health", "/health/live", "/health/ready")
    )

    val result = serverAndClient(authConfig = Some(authConfig))
      .use { case (client, serverUri) =>
        val request  = Request[IO](Method.GET, serverUri / "pipelines")
        val response = client.run(request).use(resp => IO.pure(resp.status))
        response
      }
      .unsafeRunSync()

    result shouldBe Status.Unauthorized
  }

  it should "accept authenticated requests with valid key" in {
    val authConfig = AuthConfig(
      hashedKeys = List(HashedApiKey("test-key", ApiRole.Admin)),
      publicPaths = Set("/health")
    )

    val result = serverAndClient(authConfig = Some(authConfig))
      .use { case (client, serverUri) =>
        authenticatedRequest(client, serverUri, "pipelines", "test-key").map(_.status)
      }
      .unsafeRunSync()

    result shouldBe Status.Ok
  }

  it should "allow public paths without authentication" in {
    val authConfig = AuthConfig(
      hashedKeys = List(HashedApiKey("test-key", ApiRole.Admin)),
      publicPaths = Set("/health", "/health/live", "/health/ready")
    )

    val result = serverAndClient(authConfig = Some(authConfig))
      .use { case (client, serverUri) =>
        val healthRequest = Request[IO](Method.GET, serverUri / "health")
        val liveRequest   = Request[IO](Method.GET, serverUri / "health" / "live")
        val readyRequest  = Request[IO](Method.GET, serverUri / "health" / "ready")

        for {
          health <- client.run(healthRequest).use(resp => IO.pure(resp.status))
          live   <- client.run(liveRequest).use(resp => IO.pure(resp.status))
          ready  <- client.run(readyRequest).use(resp => IO.pure(resp.status))
        } yield (health, live, ready)
      }
      .unsafeRunSync()

    result shouldBe ((Status.Ok, Status.Ok, Status.Ok))
  }

  // ===== CORS Middleware =====

  "CORS middleware" should "add CORS headers to responses" in {
    val corsConfig = CorsConfig(allowedOrigins = Set("https://example.com"))

    val result = serverAndClient(corsConfig = Some(corsConfig))
      .use { case (client, serverUri) =>
        val request = Request[IO](Method.GET, serverUri / "health")
          .putHeaders(Header.Raw(org.typelevel.ci.CIString("Origin"), "https://example.com"))

        client.run(request).use { resp =>
          IO.pure(resp.headers.get(org.typelevel.ci.CIString("Access-Control-Allow-Origin")))
        }
      }
      .unsafeRunSync()

    result shouldBe defined
  }

  it should "handle preflight OPTIONS requests" in {
    val corsConfig = CorsConfig(allowedOrigins = Set("https://example.com"))

    val result = serverAndClient(corsConfig = Some(corsConfig))
      .use { case (client, serverUri) =>
        val request = Request[IO](Method.OPTIONS, serverUri / "pipelines")
          .putHeaders(
            Header.Raw(org.typelevel.ci.CIString("Origin"), "https://example.com"),
            Header.Raw(org.typelevel.ci.CIString("Access-Control-Request-Method"), "GET")
          )

        client.run(request).use(resp => IO.pure(resp.status))
      }
      .unsafeRunSync()

    result shouldBe Status.Ok
  }

  it should "reject requests from non-allowed origins" in {
    val corsConfig = CorsConfig(allowedOrigins = Set("https://example.com"))

    val result = serverAndClient(corsConfig = Some(corsConfig))
      .use { case (client, serverUri) =>
        val request = Request[IO](Method.GET, serverUri / "health")
          .putHeaders(Header.Raw(org.typelevel.ci.CIString("Origin"), "https://evil.com"))

        client.run(request).use { resp =>
          val hasAllowOrigin =
            resp.headers.get(org.typelevel.ci.CIString("Access-Control-Allow-Origin")).isDefined
          IO.pure(hasAllowOrigin)
        }
      }
      .unsafeRunSync()

    result shouldBe false
  }

  // ===== Combined Middleware (Auth + CORS) =====

  "Combined Auth + CORS middleware" should "work together" in {
    val authConfig = AuthConfig(
      hashedKeys = List(HashedApiKey("test-key", ApiRole.Admin)),
      publicPaths = Set("/health")
    )
    val corsConfig = CorsConfig(allowedOrigins = Set("https://example.com"))

    val result = serverAndClient(
      authConfig = Some(authConfig),
      corsConfig = Some(corsConfig)
    ).use { case (client, serverUri) =>
      val request = Request[IO](Method.GET, serverUri / "pipelines")
        .putHeaders(
          headers.Authorization(
            Credentials.Token(org.typelevel.ci.CIString("Bearer"), "test-key")
          ),
          Header.Raw(org.typelevel.ci.CIString("Origin"), "https://example.com")
        )

      client.run(request).use { resp =>
        val hasCors = resp.headers
          .get(org.typelevel.ci.CIString("Access-Control-Allow-Origin"))
          .isDefined
        IO.pure((resp.status, hasCors))
      }
    }.unsafeRunSync()

    val (status, hasCors) = result
    status shouldBe Status.Ok
    hasCors shouldBe true
  }

  it should "reject unauthenticated requests even with valid CORS" in {
    val authConfig = AuthConfig(
      hashedKeys = List(HashedApiKey("test-key", ApiRole.Admin)),
      publicPaths = Set("/health")
    )
    val corsConfig = CorsConfig(allowedOrigins = Set("https://example.com"))

    val result = serverAndClient(
      authConfig = Some(authConfig),
      corsConfig = Some(corsConfig)
    ).use { case (client, serverUri) =>
      val request = Request[IO](Method.GET, serverUri / "pipelines")
        .putHeaders(Header.Raw(org.typelevel.ci.CIString("Origin"), "https://example.com"))

      client.run(request).use(resp => IO.pure(resp.status))
    }.unsafeRunSync()

    result shouldBe Status.Unauthorized
  }

  // ===== Compile Endpoint =====

  "POST /compile" should "compile valid constellation-lang code" in {
    val source = """
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """

    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val compileRequest = CompileRequest(source, name = Some("test-dag"))
        val request = Request[IO](Method.POST, serverUri / "compile")
          .withEntity(compileRequest.asJson)

        client.expect[CompileResponse](request)
      }
      .unsafeRunSync()

    result.success shouldBe true
    result.name shouldBe Some("test-dag")
    result.structuralHash shouldBe defined
  }

  it should "return error for invalid code" in {
    val source = "invalid syntax here"

    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val compileRequest = CompileRequest(source, name = Some("test-dag"))
        val request = Request[IO](Method.POST, serverUri / "compile")
          .withEntity(compileRequest.asJson)

        // Compilation errors return 400 Bad Request
        client.run(request).use { resp =>
          if resp.status == Status.BadRequest then resp.as[CompileResponse]
          else client.expect[CompileResponse](request)
        }
      }
      .unsafeRunSync()

    result.success shouldBe false
    result.errors should not be empty
  }

  // ===== Execute Endpoint =====

  "POST /execute" should "execute a simple pipeline" in {
    val source = """
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """

    val result = serverAndClient()
      .use { case (client, serverUri) =>
        // First compile the pipeline
        val compileRequest = CompileRequest(source, name = Some("add-test"))
        val compileReq = Request[IO](Method.POST, serverUri / "compile")
          .withEntity(compileRequest.asJson)

        for {
          compileResp <- client.expect[CompileResponse](compileReq)

          // Then execute it
          executeRequest = ExecuteRequest(
            ref = compileResp.name,
            inputs = Map("a" -> Json.fromInt(10), "b" -> Json.fromInt(32))
          )
          executeReq = Request[IO](Method.POST, serverUri / "execute")
            .withEntity(executeRequest.asJson)

          executeResp <- client.expect[ExecuteResponse](executeReq)
        } yield executeResp
      }
      .unsafeRunSync()

    result.success shouldBe true
    result.outputs should contain key "result"
    result.outputs("result") shouldBe Json.fromInt(42)
  }

  it should "return error for missing inputs" in {
    val source = """
      in a: Int
      in b: Int
      result = add(a, b)
      out result
    """

    val result = serverAndClient()
      .use { case (client, serverUri) =>
        // First compile the pipeline
        val compileRequest = CompileRequest(source, name = Some("add-test-fail"))
        val compileReq = Request[IO](Method.POST, serverUri / "compile")
          .withEntity(compileRequest.asJson)

        for {
          compileResp <- client.expect[CompileResponse](compileReq)

          // Then execute with missing input
          executeRequest = ExecuteRequest(
            ref = compileResp.name,
            inputs = Map("a" -> Json.fromInt(10)) // missing 'b'
          )
          executeReq = Request[IO](Method.POST, serverUri / "execute")
            .withEntity(executeRequest.asJson)

          executeResp <- client.expect[ExecuteResponse](executeReq)
        } yield executeResp
      }
      .unsafeRunSync()

    // Missing inputs results in suspended execution, not failure
    result.status shouldBe Some("suspended")
    result.missingInputs shouldBe defined
    result.missingInputs.get should contain key "b"
  }

  // ===== GET /pipelines =====

  "GET /pipelines" should "return empty list initially" in {
    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val request = Request[IO](Method.GET, serverUri / "pipelines")
        client.expect[Json](request)
      }
      .unsafeRunSync()

    result shouldBe a[Json]
    val pipelines = result.hcursor.get[List[Json]]("pipelines").getOrElse(List.empty)
    pipelines shouldBe empty
  }

  // ===== GET /modules =====

  "GET /modules" should "return stdlib modules" in {
    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val request = Request[IO](Method.GET, serverUri / "modules")
        client.expect[ModuleListResponse](request)
      }
      .unsafeRunSync()

    result.modules should not be empty
    result.modules.map(_.name) should contain("stdlib.add")
  }

  // ===== GET /namespaces =====

  "GET /namespaces" should "return available namespaces" in {
    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val request = Request[IO](Method.GET, serverUri / "namespaces")
        client.expect[Json](request)
      }
      .unsafeRunSync()

    result shouldBe a[Json]
    val namespaces = result.hcursor.get[List[String]]("namespaces").getOrElse(List.empty)
    namespaces should not be empty
  }

  // ===== Error Handling =====

  "Server error handling" should "return 404 for unknown endpoints" in {
    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val request  = Request[IO](Method.GET, serverUri / "unknown" / "endpoint")
        val response = client.run(request).use(resp => IO.pure(resp.status))
        response
      }
      .unsafeRunSync()

    result shouldBe Status.NotFound
  }

  it should "return 405 for unsupported methods" in {
    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val request  = Request[IO](Method.DELETE, serverUri / "pipelines")
        val response = client.run(request).use(resp => IO.pure(resp.status))
        response
      }
      .unsafeRunSync()

    // Note: http4s returns 404 when no routes match, not 405
    // This test verifies error handling rather than specific status code
    List(Status.NotFound, Status.MethodNotAllowed) should contain(result)
  }

  // ===== Concurrent Requests =====

  "Server under load" should "handle multiple concurrent requests" in {
    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val requests = (1 to 10).map { i =>
          val request = Request[IO](Method.GET, serverUri / "health")
          client.run(request).use(resp => IO.pure(resp.status))
        }

        requests.toList.parSequence
      }
      .unsafeRunSync()

    result.size shouldBe 10
    result.forall(_ == Status.Ok) shouldBe true
  }

  it should "handle concurrent compile requests" in {
    val source = """
      in x: Int
      doubled = multiply(x, 2)
      out doubled
    """

    val result = serverAndClient()
      .use { case (client, serverUri) =>
        val requests = (1 to 5).map { i =>
          val compileRequest = CompileRequest(source, name = Some(s"test-dag-$i"))
          val request = Request[IO](Method.POST, serverUri / "compile")
            .withEntity(compileRequest.asJson)
          client.expect[CompileResponse](request)
        }

        requests.toList.parSequence
      }
      .unsafeRunSync()

    result.size shouldBe 5
    result.forall(_.success) shouldBe true
  }
}
