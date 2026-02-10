package io.constellation.cli.commands

import java.nio.file.{Files, Path}

import cats.effect.unsafe.implicits.global
import cats.effect.{IO, Resource}
import cats.implicits.*

import io.constellation.cli.{CliApp, OutputFormat}

import io.circe.Json
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.{EntityDecoder, EntityEncoder, HttpApp, Response, Status, Uri}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests for CLI commands with mocked HTTP server.
  *
  * These tests verify that CLI commands correctly:
  *   - Make HTTP requests to the server
  *   - Parse successful responses
  *   - Handle various error responses (401, 403, 404, etc.)
  *   - Handle connection errors
  *   - Handle malformed JSON responses
  *
  * Run with: sbt "langCli/testOnly *CliHttpCommandsTest"
  */
class CliHttpCommandsTest extends AnyFlatSpec with Matchers {

  /** Create a mock HTTP client from an HttpApp. */
  private def mockClient(app: HttpApp[IO]): Client[IO] =
    Client.fromHttpApp(app)

  /** Create a temporary file with given content. */
  private def tempFile(content: String): Path = {
    val path = Files.createTempFile("test", ".cst")
    Files.writeString(path, content)
    path.toFile.deleteOnExit()
    path
  }

  // ===== CompileCommand Tests =====

  "CompileCommand with successful response" should "return success exit code" in {
    val mockApp = HttpApp[IO] { case req @ POST -> Root / "compile" =>
      Ok(
        Json.obj(
          "success"        -> Json.True,
          "structuralHash" -> Json.fromString("sha256:abc123")
        )
      )
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = CompileCommand(sourceFile)

    val result = CompileCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.Success
  }

  it should "handle compilation errors" in {
    val mockApp = HttpApp[IO] { case req @ POST -> Root / "compile" =>
      Ok(
        Json.obj(
          "success" -> Json.False,
          "errors"  -> Json.arr(Json.fromString("Type error on line 1"))
        )
      )
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("invalid syntax")
    val cmd        = CompileCommand(sourceFile)

    val result = CompileCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.CompileError
  }

  it should "handle 401 authentication errors" in {
    val mockApp = HttpApp[IO] { case POST -> Root / "compile" =>
      Response[IO](Status.Unauthorized).pure[IO]
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = CompileCommand(sourceFile)

    val result = CompileCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.AuthError
  }

  it should "handle 403 access denied errors" in {
    val mockApp = HttpApp[IO] { case POST -> Root / "compile" =>
      Response[IO](Status.Forbidden).pure[IO]
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = CompileCommand(sourceFile)

    val result = CompileCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.AuthError
  }

  it should "handle malformed JSON responses" in {
    val mockApp = HttpApp[IO] { case POST -> Root / "compile" =>
      Ok("not valid json")
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = CompileCommand(sourceFile)

    val result = CompileCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.RuntimeError
  }

  it should "handle connection errors" in {
    // Create a client that always fails
    val failingClient = Client.fromHttpApp[IO](HttpApp[IO] { _ =>
      IO.raiseError(new java.net.ConnectException("Connection refused"))
    })

    given Client[IO] = failingClient

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = CompileCommand(sourceFile)

    val result = CompileCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.ConnectionError
  }

  // ===== RunCommand Tests =====

  "RunCommand with successful execution" should "return success exit code" in {
    val mockApp = HttpApp[IO] { case req @ POST -> Root / "run" =>
      Ok(
        Json.obj(
          "success" -> Json.True,
          "outputs" -> Json.obj(
            "result" -> Json.fromInt(42)
          )
        )
      )
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = RunCommand(sourceFile, List("x" -> "42"))

    val result = RunCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.Success
  }

  it should "handle runtime errors" in {
    val mockApp = HttpApp[IO] { case req @ POST -> Root / "run" =>
      Ok(
        Json.obj(
          "success" -> Json.False,
          "error"   -> Json.fromString("Division by zero")
        )
      )
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = RunCommand(sourceFile, List("x" -> "0"))

    val result = RunCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.RuntimeError
  }

  it should "handle compilation errors during run" in {
    val mockApp = HttpApp[IO] { case req @ POST -> Root / "run" =>
      Ok(
        Json.obj(
          "success"           -> Json.False,
          "compilationErrors" -> Json.arr(Json.fromString("Type mismatch"))
        )
      )
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("invalid")
    val cmd        = RunCommand(sourceFile)

    val result = RunCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.CompileError
  }

  it should "handle suspended execution (missing inputs)" in {
    val mockApp = HttpApp[IO] { case req @ POST -> Root / "run" =>
      Ok(
        Json.obj(
          "success"       -> Json.True,
          "status"        -> Json.fromString("suspended"),
          "executionId"   -> Json.fromString("exec-123"),
          "missingInputs" -> Json.obj("name" -> Json.fromString("String"))
        )
      )
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nin name: String\nout x")
    val cmd        = RunCommand(sourceFile, List("x" -> "42"))

    val result = RunCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.Success
  }

  it should "handle 401 authentication errors" in {
    val mockApp = HttpApp[IO] { case POST -> Root / "run" =>
      Response[IO](Status.Unauthorized).pure[IO]
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = RunCommand(sourceFile, List("x" -> "42"))

    val result = RunCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.AuthError
  }

  it should "handle 403 access denied errors" in {
    val mockApp = HttpApp[IO] { case POST -> Root / "run" =>
      Response[IO](Status.Forbidden).pure[IO]
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = RunCommand(sourceFile, List("x" -> "42"))

    val result = RunCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.AuthError
  }

  // ===== VizCommand Tests =====

  "VizCommand with successful visualization" should "return success exit code" in {
    val mockApp = HttpApp[IO] {
      case POST -> Root / "compile" =>
        Ok(
          Json.obj(
            "success"        -> Json.True,
            "structuralHash" -> Json.fromString("abc123")
          )
        )
      case GET -> Root / "pipelines" / hash =>
        Ok(
          Json.obj(
            "structuralHash"  -> Json.fromString("abc123"),
            "modules"         -> Json.arr(),
            "declaredOutputs" -> Json.arr(Json.fromString("result")),
            "inputSchema"     -> Json.obj("x" -> Json.fromString("Int")),
            "outputSchema"    -> Json.obj("result" -> Json.fromString("Int"))
          )
        )
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout result: x")
    val cmd        = VizCommand(sourceFile, VizFormat.Dot)

    val result = VizCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.Success
  }

  it should "handle compilation errors" in {
    val mockApp = HttpApp[IO] { case POST -> Root / "compile" =>
      Ok(
        Json.obj(
          "success" -> Json.False,
          "errors"  -> Json.arr(Json.fromString("Parse error"))
        )
      )
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("invalid")
    val cmd        = VizCommand(sourceFile, VizFormat.Dot)

    val result = VizCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.CompileError
  }

  it should "handle missing structural hash" in {
    val mockApp = HttpApp[IO] { case POST -> Root / "compile" =>
      Ok(
        Json.obj(
          "success" -> Json.True
          // No structuralHash field
        )
      )
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = VizCommand(sourceFile, VizFormat.Dot)

    val result = VizCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.RuntimeError
  }

  it should "handle 404 pipeline not found" in {
    val mockApp = HttpApp[IO] {
      case POST -> Root / "compile" =>
        Ok(
          Json.obj(
            "success"        -> Json.True,
            "structuralHash" -> Json.fromString("abc123")
          )
        )
      case GET -> Root / "pipelines" / hash =>
        Response[IO](Status.NotFound).pure[IO]
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = VizCommand(sourceFile, VizFormat.Dot)

    val result = VizCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.NotFound
  }

  it should "handle 401 authentication errors" in {
    val mockApp = HttpApp[IO] { case POST -> Root / "compile" =>
      Response[IO](Status.Unauthorized).pure[IO]
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = VizCommand(sourceFile, VizFormat.Dot)

    val result = VizCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.AuthError
  }

  // ===== Edge Cases =====

  "CLI commands" should "handle non-existent files" in {
    val mockApp      = HttpApp[IO] { case _ => Ok() }
    given Client[IO] = mockClient(mockApp)

    val nonExistentFile = Path.of("/nonexistent/file.cst")
    val cmd             = CompileCommand(nonExistentFile)

    val result = CompileCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.UsageError
  }

  it should "handle server errors (500)" in {
    val mockApp = HttpApp[IO] { case POST -> Root / "compile" =>
      InternalServerError(Json.obj("error" -> Json.fromString("Internal error")))
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = CompileCommand(sourceFile)

    val result = CompileCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.CompileError
  }

  it should "handle rate limiting (429)" in {
    val mockApp = HttpApp[IO] { case POST -> Root / "compile" =>
      Response[IO](Status.TooManyRequests).pure[IO]
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = CompileCommand(sourceFile)

    val result = CompileCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.CompileError
  }

  it should "handle service unavailable (503)" in {
    val mockApp = HttpApp[IO] { case POST -> Root / "run" =>
      Response[IO](Status.ServiceUnavailable).pure[IO]
    }

    given Client[IO] = mockClient(mockApp)

    val sourceFile = tempFile("in x: Int\nout x")
    val cmd        = RunCommand(sourceFile, List("x" -> "42"))

    val result = RunCommand
      .execute(cmd, uri"http://localhost:8080", None, OutputFormat.Json, quiet = true)
      .unsafeRunSync()

    result shouldBe CliApp.ExitCodes.RuntimeError
  }
}
