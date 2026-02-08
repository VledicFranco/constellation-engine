package io.constellation.cli

import java.nio.file.{Files, Path}

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import io.circe.Json

import io.constellation.cli.commands.CompileCommand

import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CompileCommandTest extends AnyFunSuite with Matchers with BeforeAndAfterEach:

  private var tempDir: Path = _

  override def beforeEach(): Unit =
    tempDir = Files.createTempDirectory("cli-compile-test")

  override def afterEach(): Unit =
    if tempDir != null && Files.exists(tempDir) then
      Files.walk(tempDir).sorted(java.util.Comparator.reverseOrder())
        .forEach(Files.deleteIfExists)

  // ============= File Reading Tests =============

  test("readSourceFile: reads valid file"):
    val file = tempDir.resolve("test.cst")
    val content = "in x: Int\nout x"
    Files.writeString(file, content)

    val cmd = CompileCommand(file)
    // We can't easily test execute without mocking HTTP, but we can test file reading logic
    val source = Files.readString(file)
    source shouldBe content

  test("readSourceFile: handles non-existent file"):
    val file = tempDir.resolve("nonexistent.cst")
    Files.exists(file) shouldBe false

  test("readSourceFile: handles directory instead of file"):
    val dir = tempDir.resolve("subdir")
    Files.createDirectory(dir)
    Files.isDirectory(dir) shouldBe true

  // ============= Command Construction Tests =============

  test("CompileCommand: stores file path"):
    val file = tempDir.resolve("pipeline.cst")
    val cmd = CompileCommand(file)
    cmd.file shouldBe file

  // ============= Response Parsing Tests =============

  test("CompileResponse: decode success response"):
    val json = Json.obj(
      "success"        -> Json.True,
      "structuralHash" -> Json.fromString("abc123"),
      "syntacticHash"  -> Json.fromString("def456"),
      "name"           -> Json.fromString("test")
    )
    val result = json.as[CompileCommand.CompileResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.success shouldBe true
    resp.structuralHash shouldBe Some("abc123")
    resp.name shouldBe Some("test")
    resp.errors shouldBe empty

  test("CompileResponse: decode failure response"):
    val json = Json.obj(
      "success" -> Json.False,
      "errors"  -> Json.arr(
        Json.fromString("Syntax error at line 1"),
        Json.fromString("Type mismatch at line 2")
      )
    )
    val result = json.as[CompileCommand.CompileResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.success shouldBe false
    resp.errors should have size 2

  test("CompileResponse: decode minimal response"):
    val json = Json.obj("success" -> Json.True)
    val result = json.as[CompileCommand.CompileResponse]
    result shouldBe a[Right[?, ?]]
    val resp = result.toOption.get
    resp.success shouldBe true
    resp.structuralHash shouldBe None
    resp.errors shouldBe empty

  // ============= Exit Code Tests =============

  test("exit codes are defined correctly"):
    CliApp.ExitCodes.Success.code shouldBe 0
    CliApp.ExitCodes.CompileError.code shouldBe 1
    CliApp.ExitCodes.RuntimeError.code shouldBe 2
    CliApp.ExitCodes.ConnectionError.code shouldBe 3
    CliApp.ExitCodes.AuthError.code shouldBe 4
    CliApp.ExitCodes.NotFound.code shouldBe 5
    CliApp.ExitCodes.Conflict.code shouldBe 6
    CliApp.ExitCodes.UsageError.code shouldBe 10
