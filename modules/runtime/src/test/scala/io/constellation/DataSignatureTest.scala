package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class DataSignatureTest extends AnyFlatSpec with Matchers {

  private val completedSig = DataSignature(
    executionId = UUID.randomUUID(),
    structuralHash = "abc123",
    resumptionCount = 0,
    status = PipelineStatus.Completed,
    inputs = Map("text" -> CValue.CString("hello")),
    computedNodes = Map("text" -> CValue.CString("hello"), "result" -> CValue.CString("HELLO")),
    outputs = Map("result" -> CValue.CString("HELLO")),
    missingInputs = Nil,
    pendingOutputs = Nil
  )

  private val suspendedSig = DataSignature(
    executionId = UUID.randomUUID(),
    structuralHash = "def456",
    resumptionCount = 0,
    status = PipelineStatus.Suspended,
    inputs = Map("a" -> CValue.CInt(1)),
    computedNodes = Map("a" -> CValue.CInt(1)),
    outputs = Map.empty,
    missingInputs = List("b"),
    pendingOutputs = List("result")
  )

  private val failedSig = DataSignature(
    executionId = UUID.randomUUID(),
    structuralHash = "ghi789",
    resumptionCount = 1,
    status = PipelineStatus.Failed(
      List(
        ExecutionError("ModuleA", "ModuleA", "boom")
      )
    ),
    inputs = Map("x" -> CValue.CString("test")),
    computedNodes = Map("x" -> CValue.CString("test")),
    outputs = Map.empty,
    missingInputs = Nil,
    pendingOutputs = List("out")
  )

  "isComplete" should "return true for Completed status" in {
    completedSig.isComplete shouldBe true
  }

  it should "return false for Suspended status" in {
    suspendedSig.isComplete shouldBe false
  }

  it should "return false for Failed status" in {
    failedSig.isComplete shouldBe false
  }

  "output" should "return the value for a known output" in {
    completedSig.output("result") shouldBe Some(CValue.CString("HELLO"))
  }

  it should "return None for unknown output" in {
    completedSig.output("nonexistent") shouldBe None
  }

  "node" should "return any computed node" in {
    completedSig.node("text") shouldBe Some(CValue.CString("hello"))
    completedSig.node("result") shouldBe Some(CValue.CString("HELLO"))
  }

  "allInputs" should "return the provided inputs" in {
    completedSig.allInputs shouldBe Map("text" -> CValue.CString("hello"))
  }

  "progress" should "be 1.0 when complete" in {
    completedSig.progress shouldBe 1.0
  }

  it should "be 0.0 when no outputs computed" in {
    suspendedSig.progress shouldBe 0.0
  }

  it should "handle empty outputs gracefully" in {
    val empty = completedSig.copy(outputs = Map.empty, pendingOutputs = Nil)
    empty.progress shouldBe 1.0
  }

  "failedNodes" should "return empty for completed" in {
    completedSig.failedNodes shouldBe empty
  }

  it should "return failed node names for Failed status" in {
    failedSig.failedNodes shouldBe List("ModuleA")
  }
}
