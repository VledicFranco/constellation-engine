package io.constellation.cli

import io.constellation.cli.models.ApiModels.*

import io.circe.Json
import io.circe.parser.decode
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ApiModelsTest extends AnyFunSuite with Matchers:

  // ============= ModuleInfo Decoder Tests =============

  test("ModuleInfo: decodes from valid JSON"):
    val json   = """{
      "name": "Uppercase",
      "description": "Convert text to uppercase",
      "version": "1.0",
      "inputs": {"text": "String"},
      "outputs": {"result": "String"}
    }"""
    val result = decode[ModuleInfo](json)
    result shouldBe a[Right[?, ?]]
    val info = result.toOption.get
    info.name shouldBe "Uppercase"
    info.description shouldBe "Convert text to uppercase"
    info.version shouldBe "1.0"
    info.inputs shouldBe Map("text" -> "String")
    info.outputs shouldBe Map("result" -> "String")

  test("ModuleInfo: decodes with empty inputs/outputs"):
    val json   = """{
      "name": "NoOp",
      "description": "No operation",
      "version": "0.1",
      "inputs": {},
      "outputs": {}
    }"""
    val result = decode[ModuleInfo](json)
    result shouldBe a[Right[?, ?]]
    result.toOption.get.inputs shouldBe empty
    result.toOption.get.outputs shouldBe empty

  test("ModuleInfo: fails for missing required fields"):
    val json   = """{"name": "Test"}"""
    val result = decode[ModuleInfo](json)
    result shouldBe a[Left[?, ?]]

  // ============= PipelineDetailResponse Decoder Tests =============

  test("PipelineDetailResponse: decodes from valid JSON"):
    val json   = """{
      "structuralHash": "abc123",
      "syntacticHash": "def456",
      "aliases": ["my-pipeline"],
      "compiledAt": "2026-01-01T00:00:00Z",
      "modules": [{
        "name": "Uppercase",
        "description": "To upper",
        "version": "1.0",
        "inputs": {"text": "String"},
        "outputs": {"result": "String"}
      }],
      "declaredOutputs": ["result"],
      "inputSchema": {"text": "String"},
      "outputSchema": {"result": "String"}
    }"""
    val result = decode[PipelineDetailResponse](json)
    result shouldBe a[Right[?, ?]]
    val detail = result.toOption.get
    detail.structuralHash shouldBe "abc123"
    detail.modules should have size 1
    detail.declaredOutputs shouldBe List("result")

  test("PipelineDetailResponse: handles optional fields with defaults"):
    val json   = """{
      "structuralHash": "abc123",
      "modules": [],
      "inputSchema": {},
      "outputSchema": {}
    }"""
    val result = decode[PipelineDetailResponse](json)
    result shouldBe a[Right[?, ?]]
    val detail = result.toOption.get
    detail.syntacticHash shouldBe ""
    detail.aliases shouldBe Nil
    detail.compiledAt shouldBe ""
    detail.declaredOutputs shouldBe Nil

  // ============= PipelineSummary Decoder Tests =============

  test("PipelineSummary: decodes from valid JSON"):
    val json   = """{
      "structuralHash": "hash1",
      "syntacticHash": "hash2",
      "aliases": ["test"],
      "compiledAt": "2026-01-01T00:00:00Z",
      "moduleCount": 5,
      "declaredOutputs": ["out1", "out2"]
    }"""
    val result = decode[PipelineSummary](json)
    result shouldBe a[Right[?, ?]]
    val summary = result.toOption.get
    summary.structuralHash shouldBe "hash1"
    summary.moduleCount shouldBe 5
    summary.declaredOutputs shouldBe List("out1", "out2")

  test("PipelineSummary: handles missing optional fields"):
    val json   = """{
      "structuralHash": "hash1",
      "syntacticHash": "hash2",
      "compiledAt": "2026-01-01T00:00:00Z",
      "moduleCount": 0
    }"""
    val result = decode[PipelineSummary](json)
    result shouldBe a[Right[?, ?]]
    result.toOption.get.aliases shouldBe Nil
    result.toOption.get.declaredOutputs shouldBe Nil

  // ============= ExecutionSummary Decoder Tests =============

  test("ExecutionSummary: decodes from valid JSON"):
    val json   = """{
      "executionId": "exec-001",
      "structuralHash": "hash123",
      "resumptionCount": 2,
      "missingInputs": {"name": "CString"},
      "createdAt": "2026-01-01T00:00:00Z"
    }"""
    val result = decode[ExecutionSummary](json)
    result shouldBe a[Right[?, ?]]
    val exec = result.toOption.get
    exec.executionId shouldBe "exec-001"
    exec.resumptionCount shouldBe 2
    exec.missingInputs shouldBe Map("name" -> "CString")

  test("ExecutionSummary: decodes with empty missingInputs"):
    val json   = """{
      "executionId": "exec-002",
      "structuralHash": "hash456",
      "resumptionCount": 0,
      "missingInputs": {},
      "createdAt": "2026-02-01T00:00:00Z"
    }"""
    val result = decode[ExecutionSummary](json)
    result shouldBe a[Right[?, ?]]
    result.toOption.get.missingInputs shouldBe empty

  test("ExecutionSummary: fails for missing required fields"):
    val json   = """{"executionId": "exec-003"}"""
    val result = decode[ExecutionSummary](json)
    result shouldBe a[Left[?, ?]]
