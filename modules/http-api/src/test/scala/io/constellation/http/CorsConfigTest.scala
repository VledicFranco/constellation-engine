package io.constellation.http

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for CorsConfig validation logic. */
class CorsConfigTest extends AnyFlatSpec with Matchers {

  "CorsConfig.validateOrigin" should "accept valid HTTPS origins" in {
    CorsConfig.validateOrigin("https://app.example.com") shouldBe Right("https://app.example.com")
    CorsConfig.validateOrigin("https://admin.example.com") shouldBe Right(
      "https://admin.example.com"
    )
  }

  it should "accept wildcard origin" in {
    CorsConfig.validateOrigin("*") shouldBe Right("*")
  }

  it should "accept HTTP for localhost" in {
    CorsConfig.validateOrigin("http://localhost") shouldBe Right("http://localhost")
    CorsConfig.validateOrigin("http://localhost:3000") shouldBe Right("http://localhost:3000")
  }

  it should "accept HTTP for 127.0.0.1" in {
    CorsConfig.validateOrigin("http://127.0.0.1") shouldBe Right("http://127.0.0.1")
    CorsConfig.validateOrigin("http://127.0.0.1:8080") shouldBe Right("http://127.0.0.1:8080")
  }

  it should "accept HTTP for IPv6 localhost [::1]" in {
    CorsConfig.validateOrigin("http://[::1]") shouldBe Right("http://[::1]")
    CorsConfig.validateOrigin("http://[::1]:5173") shouldBe Right("http://[::1]:5173")
  }

  it should "reject HTTP for non-localhost origins" in {
    CorsConfig.validateOrigin("http://app.example.com") match {
      case Left(error) =>
        error should include("HTTP not allowed for non-localhost origins")
        error should include("use HTTPS")
      case Right(_) =>
        fail("Should have rejected HTTP for non-localhost")
    }
  }

  it should "reject malformed URLs" in {
    CorsConfig.validateOrigin("not-a-url") match {
      case Left(error) =>
        error should include("Malformed URL")
      case Right(_) =>
        fail("Should have rejected malformed URL")
    }
  }

  it should "reject invalid schemes" in {
    CorsConfig.validateOrigin("ftp://example.com") match {
      case Left(error) =>
        error should include("Invalid scheme 'ftp'")
        error should include("expected http/https")
      case Right(_) =>
        fail("Should have rejected FTP scheme")
    }
  }

  it should "reject file:// URLs" in {
    CorsConfig.validateOrigin("file:///path/to/file") match {
      case Left(error) =>
        error should include("Invalid scheme 'file'")
      case Right(_) =>
        fail("Should have rejected file:// URL")
    }
  }

  "CorsConfig.validate" should "pass validation for valid configs" in {
    val config = CorsConfig(
      allowedOrigins = Set("https://example.com")
    )

    config.validate shouldBe Right(config)
  }

  it should "reject wildcard with allowCredentials=true" in {
    val config = CorsConfig(
      allowedOrigins = Set("*"),
      allowCredentials = true
    )

    config.validate match {
      case Left(error) =>
        error should include("Cannot combine allowCredentials=true with wildcard origin")
      case Right(_) =>
        fail("Should have failed validation")
    }
  }

  it should "allow wildcard when allowCredentials=false" in {
    val config = CorsConfig(
      allowedOrigins = Set("*"),
      allowCredentials = false
    )

    config.validate shouldBe Right(config)
  }

  it should "reject negative maxAge" in {
    val config = CorsConfig(
      allowedOrigins = Set("https://example.com"),
      maxAge = -1L
    )

    config.validate match {
      case Left(error) =>
        error should include("maxAge must be non-negative")
      case Right(_) =>
        fail("Should have failed validation")
    }
  }

  it should "accept zero maxAge" in {
    val config = CorsConfig(
      allowedOrigins = Set("https://example.com"),
      maxAge = 0L
    )

    config.validate shouldBe Right(config)
  }

  "CorsConfig.isEnabled" should "return true when origins are configured" in {
    val config = CorsConfig(allowedOrigins = Set("https://example.com"))
    config.isEnabled shouldBe true
  }

  it should "return false when no origins are configured" in {
    val config = CorsConfig(allowedOrigins = Set.empty)
    config.isEnabled shouldBe false
  }

  it should "return true for wildcard origin" in {
    val config = CorsConfig(allowedOrigins = Set("*"))
    config.isEnabled shouldBe true
  }

  "CorsConfig.isWildcard" should "return true for wildcard origin" in {
    val config = CorsConfig(allowedOrigins = Set("*"))
    config.isWildcard shouldBe true
  }

  it should "return false for specific origins" in {
    val config = CorsConfig(allowedOrigins = Set("https://example.com"))
    config.isWildcard shouldBe false
  }

  it should "return false when no origins configured" in {
    val config = CorsConfig(allowedOrigins = Set.empty)
    config.isWildcard shouldBe false
  }

  "CorsConfig.default" should "have no origins and standard settings" in {
    val config = CorsConfig.default

    config.allowedOrigins shouldBe empty
    config.isEnabled shouldBe false
    config.isWildcard shouldBe false
    config.allowedMethods should contain allOf ("GET", "POST", "PUT", "DELETE", "OPTIONS")
    config.allowedHeaders should contain allOf ("Content-Type", "Authorization")
    config.allowCredentials shouldBe false
    config.maxAge shouldBe 3600L
  }
}
