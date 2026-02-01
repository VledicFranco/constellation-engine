package io.constellation.http

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Tests for AuthConfig validation logic. */
class AuthConfigTest extends AnyFlatSpec with Matchers {

  "AuthConfig.validateApiKey" should "accept valid API keys" in {
    val validKey = "a" * 24 // Exactly 24 chars

    AuthConfig.validateApiKey(validKey) shouldBe Right(validKey)
  }

  it should "accept API keys longer than minimum" in {
    val longKey = "a" * 100

    AuthConfig.validateApiKey(longKey) shouldBe Right(longKey)
  }

  it should "reject API keys shorter than 24 characters" in {
    val shortKey = "short"

    AuthConfig.validateApiKey(shortKey) match {
      case Left(error) =>
        error should include("API key too short")
        error should include("5 chars")
      case Right(_) =>
        fail("Should have rejected short key")
    }
  }

  it should "reject blank API keys" in {
    AuthConfig.validateApiKey("") match {
      case Left(error) =>
        error should include("blank or whitespace-only")
      case Right(_) =>
        fail("Should have rejected blank key")
    }
  }

  it should "reject whitespace-only API keys" in {
    AuthConfig.validateApiKey("   ") match {
      case Left(error) =>
        error should include("blank or whitespace-only")
      case Right(_) =>
        fail("Should have rejected whitespace key")
    }
  }

  it should "reject API keys with control characters" in {
    val keyWithNull = "a" * 24 + "\u0000"

    AuthConfig.validateApiKey(keyWithNull) match {
      case Left(error) =>
        error should include("control characters")
      case Right(_) =>
        fail("Should have rejected key with control chars")
    }
  }

  it should "reject API keys with newline" in {
    val keyWithNewline = "a" * 24 + "\n"

    AuthConfig.validateApiKey(keyWithNewline) match {
      case Left(error) =>
        error should include("control characters")
      case Right(_) =>
        fail("Should have rejected key with newline")
    }
  }

  "AuthConfig.parseRole" should "parse Admin case-insensitively" in {
    AuthConfig.parseRole("admin") shouldBe Some(ApiRole.Admin)
    AuthConfig.parseRole("Admin") shouldBe Some(ApiRole.Admin)
    AuthConfig.parseRole("ADMIN") shouldBe Some(ApiRole.Admin)
  }

  it should "parse Execute case-insensitively" in {
    AuthConfig.parseRole("execute") shouldBe Some(ApiRole.Execute)
    AuthConfig.parseRole("Execute") shouldBe Some(ApiRole.Execute)
    AuthConfig.parseRole("EXECUTE") shouldBe Some(ApiRole.Execute)
  }

  it should "parse ReadOnly case-insensitively" in {
    AuthConfig.parseRole("readonly") shouldBe Some(ApiRole.ReadOnly)
    AuthConfig.parseRole("ReadOnly") shouldBe Some(ApiRole.ReadOnly)
    AuthConfig.parseRole("READONLY") shouldBe Some(ApiRole.ReadOnly)
  }

  it should "return None for invalid roles" in {
    AuthConfig.parseRole("InvalidRole") shouldBe None
    AuthConfig.parseRole("") shouldBe None
    AuthConfig.parseRole("SuperUser") shouldBe None
  }

  it should "trim whitespace before parsing" in {
    AuthConfig.parseRole("  admin  ") shouldBe Some(ApiRole.Admin)
    AuthConfig.parseRole("  execute  ") shouldBe Some(ApiRole.Execute)
  }

  "AuthConfig.validate" should "pass validation for valid configs" in {
    val config = AuthConfig(
      hashedKeys = List(HashedApiKey("test-key-24-chars-long!", ApiRole.Admin))
    )

    config.validate shouldBe Right(config)
  }

  it should "pass validation for empty config" in {
    val config = AuthConfig(hashedKeys = List.empty)
    config.validate shouldBe Right(config)
  }

  "AuthConfig.isEnabled" should "return true when keys are configured" in {
    val config = AuthConfig(hashedKeys = List(HashedApiKey("key", ApiRole.Admin)))
    config.isEnabled shouldBe true
  }

  it should "return false when no keys are configured" in {
    val config = AuthConfig(hashedKeys = List.empty)
    config.isEnabled shouldBe false
  }

  "AuthConfig.verifyKey" should "find and verify valid keys" in {
    val plainKey = "test-key-24-chars-long!"
    val config = AuthConfig(
      hashedKeys = List(HashedApiKey(plainKey, ApiRole.Admin))
    )

    config.verifyKey(plainKey) shouldBe Some(ApiRole.Admin)
  }

  it should "return None for invalid keys" in {
    val config = AuthConfig(
      hashedKeys = List(HashedApiKey("correct-key-24-chars!", ApiRole.Admin))
    )

    config.verifyKey("wrong-key") shouldBe None
  }

  it should "support multiple keys with different roles" in {
    val adminKey = "admin-key-24-chars-long"
    val execKey = "exec-key-24-chars-longg"
    val readKey = "read-key-24-chars-longg"

    val config = AuthConfig(
      hashedKeys = List(
        HashedApiKey(adminKey, ApiRole.Admin),
        HashedApiKey(execKey, ApiRole.Execute),
        HashedApiKey(readKey, ApiRole.ReadOnly)
      )
    )

    config.verifyKey(adminKey) shouldBe Some(ApiRole.Admin)
    config.verifyKey(execKey) shouldBe Some(ApiRole.Execute)
    config.verifyKey(readKey) shouldBe Some(ApiRole.ReadOnly)
  }

  "HashedApiKey.verify" should "use constant-time comparison" in {
    val key = "test-key-24-chars-long!"
    val hashed = HashedApiKey(key, ApiRole.Admin)

    // Should verify correct key
    hashed.verify(key) shouldBe true

    // Should reject wrong key
    hashed.verify("wrong-key-24-chars-long") shouldBe false

    // Should reject key with different length
    hashed.verify("short") shouldBe false
  }

  it should "hash keys deterministically" in {
    val key = "test-key-24-chars-long!"
    val hashed1 = HashedApiKey(key, ApiRole.Admin)
    val hashed2 = HashedApiKey(key, ApiRole.Admin)

    // Both should verify the same plaintext
    hashed1.verify(key) shouldBe true
    hashed2.verify(key) shouldBe true
  }

  "AuthConfig.default" should "have no keys and standard public paths" in {
    val config = AuthConfig.default

    config.hashedKeys shouldBe empty
    config.isEnabled shouldBe false
    config.publicPaths should contain allOf ("/health", "/health/live", "/health/ready", "/metrics")
  }
}
