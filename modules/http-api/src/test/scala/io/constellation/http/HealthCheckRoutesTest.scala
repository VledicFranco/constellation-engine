package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.circe.CirceEntityCodec.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.Json

class HealthCheckRoutesTest extends AnyFlatSpec with Matchers {

  // --- Liveness ---

  "HealthCheckRoutes /health/live" should "always return 200 with alive status" in {
    val routes = HealthCheckRoutes.routes(HealthCheckConfig.default)

    val request = Request[IO](Method.GET, uri"/health/live")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    body.hcursor.downField("status").as[String] shouldBe Right("alive")
  }

  // --- Readiness without lifecycle ---

  "/health/ready" should "return 200 when no lifecycle is configured" in {
    val routes = HealthCheckRoutes.routes(HealthCheckConfig.default)

    val request = Request[IO](Method.GET, uri"/health/ready")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    body.hcursor.downField("status").as[String] shouldBe Right("ready")
  }

  // --- Readiness with custom checks ---

  it should "return 200 when all custom checks pass" in {
    val config = HealthCheckConfig(
      customReadinessChecks = List(
        ReadinessCheck("db", IO.pure(true)),
        ReadinessCheck("cache", IO.pure(true))
      )
    )
    val routes = HealthCheckRoutes.routes(config)

    val request = Request[IO](Method.GET, uri"/health/ready")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  it should "return 503 when a custom check fails" in {
    val config = HealthCheckConfig(
      customReadinessChecks = List(
        ReadinessCheck("db", IO.pure(true)),
        ReadinessCheck("cache", IO.pure(false))
      )
    )
    val routes = HealthCheckRoutes.routes(config)

    val request = Request[IO](Method.GET, uri"/health/ready")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.ServiceUnavailable
  }

  // --- Detail endpoint disabled ---

  "/health/detail" should "return 404 when detail endpoint is disabled" in {
    val routes = HealthCheckRoutes.routes(HealthCheckConfig(enableDetailEndpoint = false))

    val request = Request[IO](Method.GET, uri"/health/detail")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.NotFound
  }

  // --- Detail endpoint enabled ---

  it should "return 200 with diagnostics when enabled" in {
    val config = HealthCheckConfig(enableDetailEndpoint = true)
    val routes = HealthCheckRoutes.routes(config)

    val request = Request[IO](Method.GET, uri"/health/detail")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    body.hcursor.downField("timestamp").as[String] should be a Symbol("right")
    body.hcursor.downField("lifecycle").downField("state").as[String] shouldBe Right("unknown")
  }

  it should "include custom check results in detail" in {
    val config = HealthCheckConfig(
      enableDetailEndpoint = true,
      customReadinessChecks = List(
        ReadinessCheck("db", IO.pure(true)),
        ReadinessCheck("cache", IO.pure(false))
      )
    )
    val routes = HealthCheckRoutes.routes(config)

    val request = Request[IO](Method.GET, uri"/health/detail")
    val response = routes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    val body = response.as[Json].unsafeRunSync()
    body.hcursor.downField("readinessChecks").downField("db").as[Boolean] shouldBe Right(true)
    body.hcursor.downField("readinessChecks").downField("cache").as[Boolean] shouldBe Right(false)
  }

  // --- HealthCheckConfig ---

  "HealthCheckConfig" should "have sensible defaults" in {
    val config = HealthCheckConfig.default
    config.enableDetailEndpoint shouldBe false
    config.detailRequiresAuth shouldBe true
    config.customReadinessChecks shouldBe empty
  }
}
