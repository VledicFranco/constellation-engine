package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.headers.Accept
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.FunctionRegistry
import io.circe.Json

/** Tests for the /metrics endpoint including JSON and Prometheus content negotiation (RFC-017 Phase 3).
  *
  * Run with: sbt "httpApi/testOnly *MetricsRouteTest"
  */
class MetricsRouteTest extends AnyFlatSpec with Matchers {

  private val constellation    = ConstellationImpl.init.unsafeRunSync()
  private val compiler         = LangCompiler.empty
  private val functionRegistry = FunctionRegistry.empty
  private val routes =
    ConstellationRoutes(constellation, compiler, functionRegistry).routes

  // =========================================================================
  // JSON response (default)
  // =========================================================================

  "GET /metrics" should "return JSON by default" in {
    val request  = Request[IO](Method.GET, uri"/metrics")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()

    // Verify JSON structure
    body.hcursor.downField("timestamp").as[String].isRight shouldBe true
    body.hcursor.downField("server").downField("uptime_seconds").as[Long].isRight shouldBe true
    body.hcursor.downField("server").downField("requests_total").as[Long].isRight shouldBe true
  }

  it should "include server metrics with uptime and request count" in {
    val request  = Request[IO](Method.GET, uri"/metrics")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()

    val uptime = body.hcursor.downField("server").downField("uptime_seconds").as[Long]
    uptime.isRight shouldBe true
    uptime.toOption.get should be >= 0L

    val requests = body.hcursor.downField("server").downField("requests_total").as[Long]
    requests.isRight shouldBe true
    requests.toOption.get should be >= 1L
  }

  it should "include scheduler field in JSON response" in {
    val request  = Request[IO](Method.GET, uri"/metrics")
    val response = routes.orNotFound.run(request).unsafeRunSync()
    val body     = response.as[Json].unsafeRunSync()

    // Scheduler field should be present (even if disabled)
    body.hcursor.downField("scheduler").succeeded shouldBe true
  }

  it should "increment request count across multiple calls" in {
    val request = Request[IO](Method.GET, uri"/metrics")

    val response1 = routes.orNotFound.run(request).unsafeRunSync()
    val body1     = response1.as[Json].unsafeRunSync()
    val count1    = body1.hcursor.downField("server").downField("requests_total").as[Long].toOption.get

    val response2 = routes.orNotFound.run(request).unsafeRunSync()
    val body2     = response2.as[Json].unsafeRunSync()
    val count2    = body2.hcursor.downField("server").downField("requests_total").as[Long].toOption.get

    count2 should be > count1
  }

  // =========================================================================
  // Prometheus format (Accept: text/plain)
  // =========================================================================

  it should "return Prometheus text format when Accept: text/plain is set" in {
    val request = Request[IO](Method.GET, uri"/metrics")
      .putHeaders(Accept(MediaType.text.plain))
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok

    val body = response.as[String].unsafeRunSync()

    // Should contain Prometheus-format metrics
    body should include("constellation_server_uptime_seconds")
    body should include("constellation_server_requests_total")
    body should include("# TYPE")
    body should include("# HELP")
  }

  it should "set Content-Type to text/plain for Prometheus format" in {
    val request = Request[IO](Method.GET, uri"/metrics")
      .putHeaders(Accept(MediaType.text.plain))
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.contentType.map(_.mediaType) shouldBe Some(MediaType.text.plain)
  }

  it should "include TYPE and HELP annotations in Prometheus format" in {
    val request = Request[IO](Method.GET, uri"/metrics")
      .putHeaders(Accept(MediaType.text.plain))
    val response = routes.orNotFound.run(request).unsafeRunSync()
    val body     = response.as[String].unsafeRunSync()

    body should include("# HELP constellation_server_uptime_seconds")
    body should include("# TYPE constellation_server_uptime_seconds gauge")
    body should include("# HELP constellation_server_requests_total")
    body should include("# TYPE constellation_server_requests_total counter")
  }

  // =========================================================================
  // Content negotiation
  // =========================================================================

  it should "return JSON when Accept: application/json is set" in {
    val request = Request[IO](Method.GET, uri"/metrics")
      .putHeaders(Accept(MediaType.application.json))
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    // Should parse as JSON
    val body = response.as[Json].unsafeRunSync()
    body.hcursor.downField("timestamp").as[String].isRight shouldBe true
  }

  it should "return JSON when no Accept header is set" in {
    val request  = Request[IO](Method.GET, uri"/metrics")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    body.hcursor.downField("server").succeeded shouldBe true
  }
}
