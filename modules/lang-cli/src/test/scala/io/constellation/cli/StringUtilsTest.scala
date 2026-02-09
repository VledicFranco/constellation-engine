package io.constellation.cli

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class StringUtilsTest extends AnyFunSuite with Matchers:

  // ============= truncate =============

  test("truncate: empty string with positive limit"):
    StringUtils.truncate("", 10) shouldBe ""

  test("truncate: string shorter than limit"):
    StringUtils.truncate("hi", 10) shouldBe "hi"

  test("truncate: string exactly at limit"):
    StringUtils.truncate("hello", 5) shouldBe "hello"

  test("truncate: string one over limit"):
    StringUtils.truncate("hello!", 5) shouldBe "he..."

  test("truncate: string longer than limit adds ellipsis"):
    StringUtils.truncate("hello world", 8) shouldBe "hello..."

  test("truncate: maxLen = 0 returns empty"):
    StringUtils.truncate("hello", 0) shouldBe ""

  test("truncate: negative maxLen returns empty"):
    StringUtils.truncate("hello", -5) shouldBe ""

  test("truncate: maxLen = 1 returns first char"):
    StringUtils.truncate("hello", 1) shouldBe "h"

  test("truncate: maxLen = 2 returns first 2 chars"):
    StringUtils.truncate("hello", 2) shouldBe "he"

  test("truncate: maxLen = 3 returns first 3 chars"):
    StringUtils.truncate("hello", 3) shouldBe "hel"

  test("truncate: maxLen = 4 adds ellipsis"):
    StringUtils.truncate("hello world", 4) shouldBe "h..."

  test("truncate: single character string with limit 1"):
    StringUtils.truncate("x", 1) shouldBe "x"

  // ============= hashPreview =============

  test("hashPreview: short hash returned as-is"):
    StringUtils.hashPreview("abc123") shouldBe "abc123"

  test("hashPreview: hash exactly at HashPreviewLength"):
    val exactHash = "a" * StringUtils.Display.HashPreviewLength
    StringUtils.hashPreview(exactHash) shouldBe exactHash

  test("hashPreview: long hash truncated with ellipsis"):
    val longHash = "7a3b8c9d1234567890abcdef"
    StringUtils.hashPreview(longHash) shouldBe "7a3b8c9d1234..."

  test("hashPreview: empty string"):
    StringUtils.hashPreview("") shouldBe ""

  // ============= idPreview =============

  test("idPreview: short id returned as-is"):
    StringUtils.idPreview("abc") shouldBe "abc"

  test("idPreview: id exactly at IdPreviewLength"):
    val exactId = "a" * StringUtils.Display.IdPreviewLength
    StringUtils.idPreview(exactId) shouldBe exactId

  test("idPreview: UUID truncated with ellipsis"):
    val uuid = "550e8400-e29b-41d4-a716-446655440000"
    StringUtils.idPreview(uuid) shouldBe "550e8400..."

  test("idPreview: empty string"):
    StringUtils.idPreview("") shouldBe ""

  // ============= timestampPreview =============

  test("timestampPreview: full ISO timestamp truncated"):
    val ts = "2026-02-08T10:30:00.123Z"
    StringUtils.timestampPreview(ts) shouldBe "2026-02-08T10:30:00"

  test("timestampPreview: short string returned as-is"):
    StringUtils.timestampPreview("2026") shouldBe "2026"

  test("timestampPreview: exact length string"):
    val exact = "2026-02-08T10:30:00"
    StringUtils.timestampPreview(exact) shouldBe exact

  test("timestampPreview: empty string"):
    StringUtils.timestampPreview("") shouldBe ""

  // ============= sanitizeError =============

  test("sanitizeError: redacts Bearer tokens"):
    val msg = "Failed: Bearer sk-abc123xyz"
    val result = StringUtils.sanitizeError(msg)
    result should include("Bearer [REDACTED]")
    result should not include "sk-abc123xyz"

  test("sanitizeError: redacts sk- API keys"):
    val msg = "API key: sk-1234567890"
    val result = StringUtils.sanitizeError(msg)
    result should include("[REDACTED]")
    result should not include "sk-1234567890"

  test("sanitizeError: redacts Authorization headers"):
    val msg = "Authorization: secret-token-value"
    val result = StringUtils.sanitizeError(msg)
    result should include("Authorization: [REDACTED]")
    result should not include "secret-token-value"

  test("sanitizeError: redacts password patterns"):
    val msg = "password=mysecret123"
    val result = StringUtils.sanitizeError(msg)
    result should include("[REDACTED]")
    result should not include "mysecret123"

  test("sanitizeError: redacts token patterns"):
    val msg = "token=abc123def456"
    val result = StringUtils.sanitizeError(msg)
    result should include("[REDACTED]")
    result should not include "abc123def456"

  test("sanitizeError: preserves non-sensitive content"):
    val msg = "Connection refused to localhost:8080"
    StringUtils.sanitizeError(msg) shouldBe msg

  test("sanitizeError: handles multiple sensitive patterns"):
    val msg = "Bearer abc123 and Authorization: xyz789"
    val sanitized = StringUtils.sanitizeError(msg)
    sanitized should include("Bearer [REDACTED]")
    sanitized should include("Authorization: [REDACTED]")

  test("sanitizeError: empty string"):
    StringUtils.sanitizeError("") shouldBe ""

  test("sanitizeError: case-insensitive Bearer"):
    val msg = "bearer some-token-here"
    val result = StringUtils.sanitizeError(msg)
    result should include("[REDACTED]")
    result should not include "some-token-here"

  // ============= Display constants =============

  test("Display constants have expected values"):
    StringUtils.Display.HashPreviewLength shouldBe 12
    StringUtils.Display.IdPreviewLength shouldBe 8
    StringUtils.Display.TimestampPreviewLength shouldBe 19
