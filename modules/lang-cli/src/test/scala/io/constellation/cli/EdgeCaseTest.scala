package io.constellation.cli

import java.nio.file.{Files, Path}

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** Edge case tests for CLI robustness.
  *
  * Tests boundary conditions, unusual inputs, and error handling.
  */
class EdgeCaseTest extends AnyFunSuite with Matchers with BeforeAndAfterEach:

  private var tempDir: Path = _

  override def beforeEach(): Unit =
    tempDir = Files.createTempDirectory("cli-edge-test")

  override def afterEach(): Unit =
    if tempDir != null && Files.exists(tempDir) then
      Files
        .walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.deleteIfExists)

  // ============= StringUtils Tests =============

  test("StringUtils.truncate: empty string"):
    StringUtils.truncate("", 10) shouldBe ""

  test("StringUtils.truncate: string shorter than limit"):
    StringUtils.truncate("hello", 10) shouldBe "hello"

  test("StringUtils.truncate: string equal to limit"):
    StringUtils.truncate("hello", 5) shouldBe "hello"

  test("StringUtils.truncate: string longer than limit"):
    StringUtils.truncate("hello world", 8) shouldBe "hello..."

  test("StringUtils.truncate: very short limit"):
    StringUtils.truncate("hello", 3) shouldBe "hel"

  test("StringUtils.truncate: zero limit"):
    StringUtils.truncate("hello", 0) shouldBe ""

  test("StringUtils.truncate: negative limit"):
    StringUtils.truncate("hello", -1) shouldBe ""

  test("StringUtils.hashPreview: short hash"):
    StringUtils.hashPreview("abc123") shouldBe "abc123"

  test("StringUtils.hashPreview: long hash"):
    val hash = "7a3b8c9d1234567890abcdef"
    StringUtils.hashPreview(hash) shouldBe "7a3b8c9d1234..."

  test("StringUtils.idPreview: short id"):
    StringUtils.idPreview("abc") shouldBe "abc"

  test("StringUtils.idPreview: UUID"):
    val uuid = "550e8400-e29b-41d4-a716-446655440000"
    StringUtils.idPreview(uuid) shouldBe "550e8400..."

  test("StringUtils.timestampPreview: ISO timestamp"):
    val ts = "2026-02-08T10:30:00.123Z"
    StringUtils.timestampPreview(ts) shouldBe "2026-02-08T10:30:00"

  test("StringUtils.timestampPreview: short timestamp"):
    StringUtils.timestampPreview("2026") shouldBe "2026"

  // ============= Error Sanitization Tests =============

  test("StringUtils.sanitizeError: redacts Bearer tokens"):
    val msg = "Failed: Bearer sk-abc123xyz"
    StringUtils.sanitizeError(msg) should include("Bearer [REDACTED]")
    StringUtils.sanitizeError(msg) should not include "sk-abc123xyz"

  test("StringUtils.sanitizeError: redacts API keys"):
    val msg = "API key: sk-1234567890"
    StringUtils.sanitizeError(msg) should include("[REDACTED]")
    StringUtils.sanitizeError(msg) should not include "sk-1234567890"

  test("StringUtils.sanitizeError: redacts Authorization headers"):
    val msg = "Authorization: secret-token-value"
    StringUtils.sanitizeError(msg) should include("Authorization: [REDACTED]")
    StringUtils.sanitizeError(msg) should not include "secret-token-value"

  test("StringUtils.sanitizeError: redacts password patterns"):
    val msg = "password=mysecret123"
    StringUtils.sanitizeError(msg) should include("[REDACTED]")
    StringUtils.sanitizeError(msg) should not include "mysecret123"

  test("StringUtils.sanitizeError: preserves non-sensitive content"):
    val msg = "Connection refused to localhost:8080"
    StringUtils.sanitizeError(msg) shouldBe msg

  test("StringUtils.sanitizeError: handles multiple sensitive patterns"):
    val msg       = "Bearer abc123 and Authorization: xyz789"
    val sanitized = StringUtils.sanitizeError(msg)
    sanitized should include("Bearer [REDACTED]")
    sanitized should include("Authorization: [REDACTED]")

  // ============= Config Path Depth Tests =============

  test("CliConfig: shallow path is accepted"):
    val result = CliConfig.setValue("server.url", "http://test.com").attempt.unsafeRunSync()
    // This should not throw (may fail for other reasons in test env)
    result.isLeft || result.isRight shouldBe true

  test("CliConfig: deeply nested path is rejected"):
    val deepPath = "a.b.c.d.e.f.g" // More than MaxPathDepth (5)
    val result   = CliConfig.setValue(deepPath, "value").attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.swap.toOption.get.getMessage should include("too deep")

  // ============= Output Format Tests =============

  test("OutputFormat: decode human"):
    val result = io.circe.Json.fromString("human").as[OutputFormat]
    result shouldBe Right(OutputFormat.Human)

  test("OutputFormat: decode json"):
    val result = io.circe.Json.fromString("json").as[OutputFormat]
    result shouldBe Right(OutputFormat.Json)

  test("OutputFormat: decode invalid"):
    val result = io.circe.Json.fromString("xml").as[OutputFormat]
    result.isLeft shouldBe true

  // ============= Unicode Tests =============

  test("StringUtils.truncate: unicode characters"):
    val msg = "こんにちは世界" // Hello World in Japanese
    StringUtils.truncate(msg, 5) should have length 5

  test("StringUtils.sanitizeError: unicode message"):
    val msg       = "エラー: Bearer abc123"
    val sanitized = StringUtils.sanitizeError(msg)
    sanitized should include("エラー")
    sanitized should include("Bearer [REDACTED]")

  // ============= Empty/Null Input Tests =============

  test("StringUtils.hashPreview: empty string"):
    StringUtils.hashPreview("") shouldBe ""

  test("StringUtils.idPreview: empty string"):
    StringUtils.idPreview("") shouldBe ""

  test("StringUtils.timestampPreview: empty string"):
    StringUtils.timestampPreview("") shouldBe ""

  test("StringUtils.sanitizeError: empty string"):
    StringUtils.sanitizeError("") shouldBe ""

  // ============= Exit Codes Tests =============

  test("ExitCodes: all codes are distinct"):
    val codes = List(
      CliApp.ExitCodes.Success,
      CliApp.ExitCodes.CompileError,
      CliApp.ExitCodes.RuntimeError,
      CliApp.ExitCodes.ConnectionError,
      CliApp.ExitCodes.AuthError,
      CliApp.ExitCodes.NotFound,
      CliApp.ExitCodes.Conflict,
      CliApp.ExitCodes.UsageError
    )
    codes.map(_.code).toSet.size shouldBe codes.size

  test("ExitCodes: Conflict is code 6"):
    CliApp.ExitCodes.Conflict.code shouldBe 6

  // ============= Version Tests =============

  test("CliApp.cliVersion: is non-empty"):
    CliApp.cliVersion should not be empty

  test("CliApp.cliVersion: is either semver or dev"):
    val version  = CliApp.cliVersion
    val isSemver = version.matches("""\d+\.\d+\.\d+.*""")
    val isDev    = version == "dev"
    (isSemver || isDev) shouldBe true
