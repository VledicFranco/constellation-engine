package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.Json
import org.typelevel.ci.CIString

class RateLimitMiddlewareTest extends AnyFlatSpec with Matchers {

  private val testRoutes = HttpRoutes.of[IO] {
    case GET -> Root / "data"    => Ok(Json.obj("value" -> Json.fromString("hello")))
    case GET -> Root / "health"  => Ok(Json.obj("status" -> Json.fromString("ok")))
    case GET -> Root / "metrics" => Ok(Json.obj("uptime" -> Json.fromInt(100)))
  }

  "RateLimitMiddleware" should "allow requests under the limit" in {
    val config = RateLimitConfig(requestsPerMinute = 100, burst = 10)
    val rateLimitedRoutes = RateLimitMiddleware(config)(testRoutes).unsafeRunSync()

    val request = Request[IO](Method.GET, uri"/data")
    val response = rateLimitedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  it should "return 429 when rate limit exceeded" in {
    // Very low limit: 1 request per minute, burst of 1
    val config = RateLimitConfig(requestsPerMinute = 1, burst = 1)
    val rateLimitedRoutes = RateLimitMiddleware(config)(testRoutes).unsafeRunSync()

    val request = Request[IO](Method.GET, uri"/data")

    // First request should succeed
    val response1 = rateLimitedRoutes.orNotFound.run(request).unsafeRunSync()
    response1.status shouldBe Status.Ok

    // Second request should be rate limited
    val response2 = rateLimitedRoutes.orNotFound.run(request).unsafeRunSync()
    response2.status shouldBe Status.TooManyRequests
  }

  it should "include Retry-After header in 429 response" in {
    val config = RateLimitConfig(requestsPerMinute = 1, burst = 1)
    val rateLimitedRoutes = RateLimitMiddleware(config)(testRoutes).unsafeRunSync()

    val request = Request[IO](Method.GET, uri"/data")

    // Exhaust the limit
    rateLimitedRoutes.orNotFound.run(request).unsafeRunSync()

    // Check 429 response
    val response = rateLimitedRoutes.orNotFound.run(request).unsafeRunSync()
    response.status shouldBe Status.TooManyRequests
    response.headers.get(CIString("Retry-After")) should not be empty
  }

  it should "exempt health check paths" in {
    val config = RateLimitConfig(requestsPerMinute = 1, burst = 1)
    val rateLimitedRoutes = RateLimitMiddleware(config)(testRoutes).unsafeRunSync()

    // Exhaust limit with /data
    val dataReq = Request[IO](Method.GET, uri"/data")
    rateLimitedRoutes.orNotFound.run(dataReq).unsafeRunSync()

    // /health should still work (exempt)
    val healthReq = Request[IO](Method.GET, uri"/health")
    val response = rateLimitedRoutes.orNotFound.run(healthReq).unsafeRunSync()
    response.status shouldBe Status.Ok
  }

  it should "exempt /metrics path" in {
    val config = RateLimitConfig(requestsPerMinute = 1, burst = 1)
    val rateLimitedRoutes = RateLimitMiddleware(config)(testRoutes).unsafeRunSync()

    // Exhaust limit
    val dataReq = Request[IO](Method.GET, uri"/data")
    rateLimitedRoutes.orNotFound.run(dataReq).unsafeRunSync()

    // /metrics should still work (exempt)
    val metricsReq = Request[IO](Method.GET, uri"/metrics")
    val response = rateLimitedRoutes.orNotFound.run(metricsReq).unsafeRunSync()
    response.status shouldBe Status.Ok
  }

  // --- Config validation ---

  "RateLimitConfig" should "reject zero requestsPerMinute" in {
    val config = RateLimitConfig(requestsPerMinute = 0)
    config.validate shouldBe a[Left[_, _]]
  }

  it should "reject negative burst" in {
    val config = RateLimitConfig(burst = -1)
    config.validate shouldBe a[Left[_, _]]
  }

  it should "accept valid configuration" in {
    val config = RateLimitConfig(requestsPerMinute = 100, burst = 20)
    config.validate shouldBe a[Right[_, _]]
  }
}
