package io.constellation.http

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.dsl.io.*
import org.http4s.headers.Authorization
import org.http4s.circe.CirceEntityCodec.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.Json
import io.constellation.http.ApiModels.ErrorResponse

class AuthMiddlewareTest extends AnyFlatSpec with Matchers {

  // Simple test routes
  private val testRoutes = HttpRoutes.of[IO] {
    case GET -> Root / "data"    => Ok(Json.obj("value" -> Json.fromString("secret")))
    case POST -> Root / "data"   => Ok(Json.obj("created" -> Json.fromBoolean(true)))
    case DELETE -> Root / "data"  => Ok(Json.obj("deleted" -> Json.fromBoolean(true)))
    case GET -> Root / "health"  => Ok(Json.obj("status" -> Json.fromString("ok")))
    case GET -> Root / "health" / "live" => Ok(Json.obj("status" -> Json.fromString("alive")))
    case GET -> Root / "metrics" => Ok(Json.obj("uptime" -> Json.fromInt(100)))
  }

  private val authConfig = AuthConfig(
    apiKeys = Map(
      "admin-key" -> ApiRole.Admin,
      "exec-key" -> ApiRole.Execute,
      "read-key" -> ApiRole.ReadOnly
    )
  )

  private val protectedRoutes = AuthMiddleware(authConfig)(testRoutes)

  // --- Missing header ---

  "AuthMiddleware" should "return 401 when no Authorization header is present" in {
    val request = Request[IO](Method.GET, uri"/data")
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Unauthorized
    val body = response.as[ErrorResponse].unsafeRunSync()
    body.error shouldBe "Unauthorized"
  }

  // --- Invalid key ---

  it should "return 401 for an invalid API key" in {
    val request = Request[IO](Method.GET, uri"/data")
      .putHeaders(Authorization(Credentials.Token(org.typelevel.ci.CIString("Bearer"), "wrong-key")))
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Unauthorized
    val body = response.as[ErrorResponse].unsafeRunSync()
    body.error shouldBe "Unauthorized"
    body.message should include("Invalid API key")
  }

  // --- Valid Admin key ---

  it should "allow GET with Admin key" in {
    val request = Request[IO](Method.GET, uri"/data")
      .putHeaders(Authorization(Credentials.Token(org.typelevel.ci.CIString("Bearer"), "admin-key")))
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  it should "allow POST with Admin key" in {
    val request = Request[IO](Method.POST, uri"/data")
      .putHeaders(Authorization(Credentials.Token(org.typelevel.ci.CIString("Bearer"), "admin-key")))
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  it should "allow DELETE with Admin key" in {
    val request = Request[IO](Method.DELETE, uri"/data")
      .putHeaders(Authorization(Credentials.Token(org.typelevel.ci.CIString("Bearer"), "admin-key")))
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  // --- Execute role ---

  it should "allow GET with Execute key" in {
    val request = Request[IO](Method.GET, uri"/data")
      .putHeaders(Authorization(Credentials.Token(org.typelevel.ci.CIString("Bearer"), "exec-key")))
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  it should "allow POST with Execute key" in {
    val request = Request[IO](Method.POST, uri"/data")
      .putHeaders(Authorization(Credentials.Token(org.typelevel.ci.CIString("Bearer"), "exec-key")))
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  it should "return 403 for DELETE with Execute key" in {
    val request = Request[IO](Method.DELETE, uri"/data")
      .putHeaders(Authorization(Credentials.Token(org.typelevel.ci.CIString("Bearer"), "exec-key")))
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Forbidden
    val body = response.as[ErrorResponse].unsafeRunSync()
    body.error shouldBe "Forbidden"
  }

  // --- ReadOnly role ---

  it should "allow GET with ReadOnly key" in {
    val request = Request[IO](Method.GET, uri"/data")
      .putHeaders(Authorization(Credentials.Token(org.typelevel.ci.CIString("Bearer"), "read-key")))
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  it should "return 403 for POST with ReadOnly key" in {
    val request = Request[IO](Method.POST, uri"/data")
      .putHeaders(Authorization(Credentials.Token(org.typelevel.ci.CIString("Bearer"), "read-key")))
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Forbidden
  }

  // --- Public paths ---

  it should "allow access to public paths without auth" in {
    val request = Request[IO](Method.GET, uri"/health")
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  it should "allow access to public path sub-routes without auth" in {
    val request = Request[IO](Method.GET, uri"/health/live")
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  it should "allow access to /metrics without auth" in {
    val request = Request[IO](Method.GET, uri"/metrics")
    val response = protectedRoutes.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  // --- Disabled ---

  it should "pass through all requests when disabled (no keys configured)" in {
    val disabledConfig = AuthConfig(apiKeys = Map.empty)
    val unprotected = AuthMiddleware(disabledConfig)(testRoutes)

    val request = Request[IO](Method.GET, uri"/data")
    val response = unprotected.orNotFound.run(request).unsafeRunSync()

    response.status shouldBe Status.Ok
  }

  // --- Config validation ---

  "AuthConfig" should "reject blank API keys" in {
    val config = AuthConfig(apiKeys = Map("" -> ApiRole.Admin))
    config.validate shouldBe a[Left[_, _]]
  }

  it should "accept valid configuration" in {
    val config = AuthConfig(apiKeys = Map("valid-key" -> ApiRole.Admin))
    config.validate shouldBe a[Right[_, _]]
  }

  // --- ApiRole ---

  "ApiRole" should "permit correct methods for Admin" in {
    ApiRole.Admin.permits("GET") shouldBe true
    ApiRole.Admin.permits("POST") shouldBe true
    ApiRole.Admin.permits("DELETE") shouldBe true
    ApiRole.Admin.permits("PUT") shouldBe true
  }

  it should "permit correct methods for Execute" in {
    ApiRole.Execute.permits("GET") shouldBe true
    ApiRole.Execute.permits("POST") shouldBe true
    ApiRole.Execute.permits("DELETE") shouldBe false
    ApiRole.Execute.permits("PUT") shouldBe false
  }

  it should "permit correct methods for ReadOnly" in {
    ApiRole.ReadOnly.permits("GET") shouldBe true
    ApiRole.ReadOnly.permits("POST") shouldBe false
    ApiRole.ReadOnly.permits("DELETE") shouldBe false
  }
}
