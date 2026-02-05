package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.Json
import org.http4s.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.typelevel.ci.CIString

class CorsMiddlewareTest extends AnyFlatSpec with Matchers {

  private val testRoutes = HttpRoutes.of[IO] {
    case GET -> Root / "data"  => Ok(Json.obj("value" -> Json.fromString("hello")))
    case POST -> Root / "data" => Ok(Json.obj("created" -> Json.fromBoolean(true)))
  }

  private val corsConfig = CorsConfig(
    allowedOrigins = Set("https://app.example.com", "https://admin.example.com"),
    allowedMethods = Set("GET", "POST"),
    allowedHeaders = Set("Content-Type", "Authorization"),
    allowCredentials = false,
    maxAge = 3600L
  )

  private val corsRoutes = CorsMiddleware(corsConfig)(testRoutes)

  "CorsMiddleware" should "add CORS headers for allowed origin" in {
    val request = Request[IO](Method.GET, uri"/data")
      .putHeaders(Header.Raw(CIString("Origin"), "https://app.example.com"))
    val response = corsRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    response.headers.get(CIString("Access-Control-Allow-Origin")) should not be empty
  }

  it should "handle preflight OPTIONS request" in {
    val request = Request[IO](Method.OPTIONS, uri"/data")
      .putHeaders(
        Header.Raw(CIString("Origin"), "https://app.example.com"),
        Header.Raw(CIString("Access-Control-Request-Method"), "POST")
      )
    val response = corsRoutes.orNotFound.run(request).unsafeRunSync()

    // Preflight should return 200 or 204
    response.status.code should be <= 204
  }

  it should "not add CORS headers for disallowed origin" in {
    val request = Request[IO](Method.GET, uri"/data")
      .putHeaders(Header.Raw(CIString("Origin"), "https://evil.example.com"))
    val response = corsRoutes.orNotFound.run(request).unsafeRunSync()

    // The response should not have the allow-origin for the evil origin
    val allowOrigin = response.headers.get(CIString("Access-Control-Allow-Origin"))
    allowOrigin.foreach { nel =>
      nel.head.value should not be "https://evil.example.com"
    }
  }

  // --- Wildcard ---

  it should "allow all origins with wildcard config" in {
    val wildcardConfig = CorsConfig(allowedOrigins = Set("*"))
    val wildcardRoutes = CorsMiddleware(wildcardConfig)(testRoutes)

    val request = Request[IO](Method.GET, uri"/data")
      .putHeaders(Header.Raw(CIString("Origin"), "https://any.example.com"))
    val response = wildcardRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    response.headers.get(CIString("Access-Control-Allow-Origin")) should not be empty
  }

  // --- Disabled ---

  it should "pass through without CORS headers when disabled" in {
    val disabledConfig = CorsConfig(allowedOrigins = Set.empty)
    val disabledRoutes = CorsMiddleware(disabledConfig)(testRoutes)

    val request = Request[IO](Method.GET, uri"/data")
      .putHeaders(Header.Raw(CIString("Origin"), "https://app.example.com"))
    val response = disabledRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
    // When disabled, no CORS middleware at all — no Access-Control headers
    response.headers.get(CIString("Access-Control-Allow-Origin")) shouldBe None
  }

  // --- Config validation ---

  "CorsConfig" should "reject negative maxAge" in {
    val config = CorsConfig(allowedOrigins = Set("https://app.example.com"), maxAge = -1)
    config.validate shouldBe a[Left[_, _]]
  }

  it should "reject credentials with wildcard origin" in {
    val config = CorsConfig(allowedOrigins = Set("*"), allowCredentials = true)
    config.validate shouldBe a[Left[_, _]]
  }

  it should "accept valid configuration" in {
    corsConfig.validate shouldBe a[Right[_, _]]
  }

  it should "report disabled when no origins" in {
    CorsConfig.default.isEnabled shouldBe false
  }

  it should "report enabled when origins set" in {
    corsConfig.isEnabled shouldBe true
  }
}
