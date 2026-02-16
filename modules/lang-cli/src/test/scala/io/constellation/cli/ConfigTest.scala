package io.constellation.cli

import java.nio.file.{Files, Path}

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.parser.decode
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ConfigTest extends AnyFunSuite with Matchers with BeforeAndAfterEach:

  private var tempDir: Path = _

  override def beforeEach(): Unit =
    tempDir = Files.createTempDirectory("cli-config-test")

  override def afterEach(): Unit =
    // Clean up temp directory
    if tempDir != null && Files.exists(tempDir) then
      Files
        .walk(tempDir)
        .sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.deleteIfExists)

  // ============= CliConfig Tests =============

  test("CliConfig: default values"):
    val config = CliConfig()
    config.server.url shouldBe "http://localhost:8080"
    config.server.token shouldBe None
    config.defaults.output shouldBe OutputFormat.Human
    config.defaults.vizFormat shouldBe "dot"

  test("CliConfig: serverUri parses valid URL"):
    val config = CliConfig(server = ServerConfig(url = "http://example.com:9090"))
    config.serverUri shouldBe a[Right[?, ?]]
    config.serverUri.toOption.get.host.map(_.value) shouldBe Some("example.com")

  test("CliConfig: serverUri fails for invalid URL"):
    val config = CliConfig(server = ServerConfig(url = "not a url"))
    config.serverUri shouldBe a[Left[?, ?]]

  test("CliConfig: effectiveOutput respects JSON flag"):
    val config = CliConfig()
    config.effectiveOutput(jsonFlag = false) shouldBe OutputFormat.Human
    config.effectiveOutput(jsonFlag = true) shouldBe OutputFormat.Json

  test("CliConfig: effectiveOutput respects config default"):
    val config = CliConfig(defaults = DefaultsConfig(output = OutputFormat.Json))
    config.effectiveOutput(jsonFlag = false) shouldBe OutputFormat.Json

  // ============= JSON Encoding/Decoding Tests =============

  test("ServerConfig: encode and decode"):
    val config = ServerConfig(url = "http://test.com", token = Some("secret"))
    val json   = io.circe.syntax.EncoderOps(config).asJson
    val result = json.as[ServerConfig]
    result shouldBe Right(config)

  test("ServerConfig: decode with missing fields"):
    val json   = io.circe.Json.obj()
    val result = json.as[ServerConfig]
    result shouldBe a[Right[?, ?]]
    result.toOption.get.url shouldBe "http://localhost:8080"
    result.toOption.get.token shouldBe None

  test("CliConfig: encode and decode"):
    val config = CliConfig(
      server = ServerConfig(url = "http://example.com", token = Some("tok")),
      defaults = DefaultsConfig(output = OutputFormat.Json, vizFormat = "mermaid")
    )
    val json   = io.circe.syntax.EncoderOps(config).asJson
    val result = json.as[CliConfig]
    result shouldBe Right(config)

  test("CliConfig: decode empty JSON"):
    val json   = io.circe.Json.obj()
    val result = json.as[CliConfig]
    result shouldBe a[Right[?, ?]]
    result.toOption.get shouldBe CliConfig()

  // ============= Path Resolution Tests =============

  test("resolvePath: simple key"):
    val json = io.circe.Json.obj(
      "key" -> io.circe.Json.fromString("value")
    )
    val result = resolvePath(json, List("key"))
    result shouldBe Some("value")

  test("resolvePath: nested key"):
    val json = io.circe.Json.obj(
      "server" -> io.circe.Json.obj(
        "url" -> io.circe.Json.fromString("http://test.com")
      )
    )
    val result = resolvePath(json, List("server", "url"))
    result shouldBe Some("http://test.com")

  test("resolvePath: missing key"):
    val json   = io.circe.Json.obj()
    val result = resolvePath(json, List("missing"))
    result shouldBe None

  test("resolvePath: number value"):
    val json = io.circe.Json.obj(
      "count" -> io.circe.Json.fromInt(42)
    )
    val result = resolvePath(json, List("count"))
    result shouldBe Some("42")

  // ============= Path Update Tests =============

  test("updatePath: simple key"):
    val json   = io.circe.Json.obj()
    val result = updatePath(json, List("key"), "value")
    result.hcursor.downField("key").as[String] shouldBe Right("value")

  test("updatePath: nested key"):
    val json   = io.circe.Json.obj()
    val result = updatePath(json, List("server", "url"), "http://new.com")
    result.hcursor.downField("server").downField("url").as[String] shouldBe Right("http://new.com")

  test("updatePath: overwrite existing"):
    val json = io.circe.Json.obj(
      "key" -> io.circe.Json.fromString("old")
    )
    val result = updatePath(json, List("key"), "new")
    result.hcursor.downField("key").as[String] shouldBe Right("new")

  // ============= Empty Key Validation Tests =============

  test("CliConfig.setValue: rejects empty path"):
    val result = CliConfig.setValue("", "value").attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.swap.toOption.get.getMessage should include("empty segments")

  test("CliConfig.setValue: rejects path with empty segment"):
    val result = CliConfig.setValue("server..url", "value").attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.swap.toOption.get.getMessage should include("empty segments")

  test("CliConfig.setValue: rejects path starting with dot"):
    val result = CliConfig.setValue(".server.url", "value").attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.swap.toOption.get.getMessage should include("empty segments")

  test("CliConfig.setValue: rejects path ending with dot"):
    val result = CliConfig.setValue("server.url.", "value").attempt.unsafeRunSync()
    result.isLeft shouldBe true
    result.swap.toOption.get.getMessage should include("empty segments")

  // ============= Helper Methods =============

  private def resolvePath(json: io.circe.Json, path: List[String]): Option[String] =
    path match
      case Nil => json.asString.orElse(json.asNumber.map(_.toString))
      case head :: tail =>
        json.asObject.flatMap(_.apply(head)).flatMap(resolvePath(_, tail))

  private def updatePath(json: io.circe.Json, path: List[String], value: String): io.circe.Json =
    path match
      case Nil => io.circe.Json.fromString(value)
      case head :: tail =>
        val obj     = json.asObject.getOrElse(io.circe.JsonObject.empty)
        val current = obj(head).getOrElse(io.circe.Json.Null)
        io.circe.Json.fromJsonObject(obj.add(head, updatePath(current, tail, value)))
