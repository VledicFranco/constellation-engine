package io.constellation.http

import io.constellation.http.DashboardModels.*

import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DashboardModelsTest extends AnyFlatSpec with Matchers {

  // ============= FileType =============

  "FileType" should "encode to lowercase string" in {
    FileType.File.asJson.asString shouldBe Some("file")
    FileType.Directory.asJson.asString shouldBe Some("directory")
  }

  it should "decode from lowercase string" in {
    Json.fromString("file").as[FileType] shouldBe Right(FileType.File)
    Json.fromString("directory").as[FileType] shouldBe Right(FileType.Directory)
  }

  it should "fail to decode unknown values" in {
    Json.fromString("symlink").as[FileType] shouldBe a[Left[?, ?]]
  }

  // ============= FileNode =============

  "FileNode" should "encode and decode" in {
    val node = FileNode("test.cst", "/scripts/test.cst", FileType.File, Some(100), Some(1000L))
    val json = node.asJson
    json.as[FileNode] shouldBe Right(node)
  }

  it should "handle optional children" in {
    val dir = FileNode(
      "scripts",
      "/scripts",
      FileType.Directory,
      children = Some(
        List(
          FileNode("a.cst", "/scripts/a.cst", FileType.File)
        )
      )
    )
    val json = dir.asJson
    json.as[FileNode] shouldBe Right(dir)
  }

  // ============= FilesResponse =============

  "FilesResponse" should "encode and decode" in {
    val response = FilesResponse(
      "/root",
      List(
        FileNode("test.cst", "/root/test.cst", FileType.File)
      )
    )
    val json = response.asJson
    json.as[FilesResponse] shouldBe Right(response)
  }

  // ============= InputParam / OutputParam =============

  "InputParam" should "encode and decode" in {
    val param = InputParam("text", "String", required = true, defaultValue = None)
    val json  = param.asJson
    json.as[InputParam] shouldBe Right(param)
  }

  it should "handle optional default value" in {
    val param = InputParam("count", "Int", required = false, defaultValue = Some(Json.fromInt(10)))
    val json  = param.asJson
    json.as[InputParam] shouldBe Right(param)
  }

  "OutputParam" should "encode and decode" in {
    val param = OutputParam("result", "String")
    val json  = param.asJson
    json.as[OutputParam] shouldBe Right(param)
  }

  // ============= FileContentResponse =============

  "FileContentResponse" should "encode and decode" in {
    val response = FileContentResponse(
      path = "/test.cst",
      name = "test.cst",
      content = "in x: Int\nout x",
      inputs = List(InputParam("x", "Int")),
      outputs = List(OutputParam("x", "Int")),
      lastModified = Some(1000L)
    )
    val json = response.asJson
    json.as[FileContentResponse] shouldBe Right(response)
  }

  // ============= DashboardExecuteRequest =============

  "DashboardExecuteRequest" should "encode and decode" in {
    val req = DashboardExecuteRequest(
      scriptPath = "test.cst",
      inputs = Map("x" -> Json.fromInt(42)),
      sampleRate = Some(0.5),
      source = Some("dashboard")
    )
    val json = req.asJson
    json.as[DashboardExecuteRequest] shouldBe Right(req)
  }

  it should "handle minimal request" in {
    val req  = DashboardExecuteRequest("test.cst", Map.empty)
    val json = req.asJson
    json.as[DashboardExecuteRequest] shouldBe Right(req)
  }

  // ============= DashboardExecuteResponse =============

  "DashboardExecuteResponse" should "encode and decode successful response" in {
    val resp = DashboardExecuteResponse(
      success = true,
      executionId = "exec-123",
      outputs = Map("result" -> Json.fromString("hello")),
      durationMs = Some(42)
    )
    val json = resp.asJson
    json.as[DashboardExecuteResponse] shouldBe Right(resp)
  }

  it should "encode and decode error response" in {
    val resp = DashboardExecuteResponse(
      success = false,
      executionId = "exec-456",
      error = Some("Compilation failed")
    )
    val json = resp.asJson
    json.as[DashboardExecuteResponse] shouldBe Right(resp)
  }

  // ============= PreviewRequest / PreviewResponse =============

  "PreviewRequest" should "encode and decode" in {
    val req  = PreviewRequest("in x: Int\nout x")
    val json = req.asJson
    json.as[PreviewRequest] shouldBe Right(req)
  }

  "PreviewResponse" should "encode and decode success" in {
    val resp = PreviewResponse(success = true)
    val json = resp.asJson
    json.as[PreviewResponse] shouldBe Right(resp)
  }

  it should "encode and decode failure with errors" in {
    val resp = PreviewResponse(success = false, errors = List("Parse error at line 1"))
    val json = resp.asJson
    json.as[PreviewResponse] shouldBe Right(resp)
  }

  // ============= ExecutionStatus / ExecutionSource Codecs =============

  "ExecutionStatus" should "encode and decode all values" in {
    ExecutionStatus.values.foreach { status =>
      val json = status.asJson
      json.as[ExecutionStatus] shouldBe Right(status)
    }
  }

  it should "fail for unknown status" in {
    Json.fromString("Unknown").as[ExecutionStatus] shouldBe a[Left[?, ?]]
  }

  "ExecutionSource" should "encode and decode all values" in {
    ExecutionSource.values.foreach { source =>
      val json = source.asJson
      json.as[ExecutionSource] shouldBe Right(source)
    }
  }

  it should "fail for unknown source" in {
    Json.fromString("Unknown").as[ExecutionSource] shouldBe a[Left[?, ?]]
  }

  // ============= DashboardError =============

  "DashboardError" should "encode and decode" in {
    val err  = DashboardError("test_error", "Test message", Some("/path"))
    val json = err.asJson
    json.as[DashboardError] shouldBe Right(err)
  }

  "DashboardError.notFound" should "create not_found error" in {
    val err = DashboardError.notFound("Script", "/test.cst")
    err.error shouldBe "not_found"
    err.message should include("not found")
    err.path shouldBe Some("/test.cst")
  }

  "DashboardError.invalidRequest" should "create invalid_request error" in {
    val err = DashboardError.invalidRequest("Bad input")
    err.error shouldBe "invalid_request"
    err.message shouldBe "Bad input"
  }

  "DashboardError.executionFailed" should "create error with execution ID" in {
    val err = DashboardError.executionFailed("Timeout", "exec-789")
    err.error shouldBe "execution_failed"
    err.details shouldBe defined
  }

  "DashboardError.serverError" should "create server_error" in {
    val err = DashboardError.serverError("Internal error")
    err.error shouldBe "server_error"
    err.message shouldBe "Internal error"
  }

  // ============= StorageStats =============

  "StorageStats" should "encode and decode" in {
    val stats = StorageStats(10, 2, 7, 1, Some(1000L), Some(5000L), 100)
    val json  = stats.asJson
    json.as[StorageStats] shouldBe Right(stats)
  }
}
