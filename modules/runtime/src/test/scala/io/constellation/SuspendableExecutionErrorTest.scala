package io.constellation

import java.util.UUID

import scala.concurrent.duration.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.constellation.execution.{ModuleTimeoutException, RetryExhaustedException}

class SuspendableExecutionErrorTest extends AnyFlatSpec with Matchers {

  // ---------------------------------------------------------------------------
  // InputTypeMismatchError
  // ---------------------------------------------------------------------------

  "InputTypeMismatchError" should "contain the input name in the message" in {
    val error = InputTypeMismatchError("myInput", CType.CString, CType.CInt)
    error.getMessage should include("myInput")
  }

  it should "contain the expected and actual types in the message" in {
    val error = InputTypeMismatchError("myInput", CType.CString, CType.CInt)
    error.getMessage should include(CType.CString.toString)
    error.getMessage should include(CType.CInt.toString)
  }

  it should "be a RuntimeException" in {
    val error = InputTypeMismatchError("x", CType.CString, CType.CInt)
    error shouldBe a[RuntimeException]
  }

  it should "preserve its fields via case class accessors" in {
    val error = InputTypeMismatchError("field", CType.CFloat, CType.CBoolean)
    error.name shouldBe "field"
    error.expected shouldBe CType.CFloat
    error.actual shouldBe CType.CBoolean
  }

  // ---------------------------------------------------------------------------
  // InputAlreadyProvidedError
  // ---------------------------------------------------------------------------

  "InputAlreadyProvidedError" should "contain the input name in the message" in {
    val error = InputAlreadyProvidedError("duplicateInput")
    error.getMessage should include("duplicateInput")
  }

  it should "be a RuntimeException" in {
    val error = InputAlreadyProvidedError("x")
    error shouldBe a[RuntimeException]
  }

  it should "preserve the name field" in {
    val error = InputAlreadyProvidedError("myField")
    error.name shouldBe "myField"
  }

  // ---------------------------------------------------------------------------
  // UnknownNodeError
  // ---------------------------------------------------------------------------

  "UnknownNodeError" should "contain the node name in the message" in {
    val error = UnknownNodeError("missingNode")
    error.getMessage should include("missingNode")
  }

  it should "be a RuntimeException" in {
    val error = UnknownNodeError("x")
    error shouldBe a[RuntimeException]
  }

  it should "preserve the name field" in {
    val error = UnknownNodeError("nodeName")
    error.name shouldBe "nodeName"
  }

  // ---------------------------------------------------------------------------
  // NodeTypeMismatchError
  // ---------------------------------------------------------------------------

  "NodeTypeMismatchError" should "contain the node name in the message" in {
    val error = NodeTypeMismatchError("myNode", CType.CInt, CType.CString)
    error.getMessage should include("myNode")
  }

  it should "contain the expected and actual types in the message" in {
    val error = NodeTypeMismatchError("myNode", CType.CInt, CType.CString)
    error.getMessage should include(CType.CInt.toString)
    error.getMessage should include(CType.CString.toString)
  }

  it should "be a RuntimeException" in {
    val error = NodeTypeMismatchError("n", CType.CInt, CType.CFloat)
    error shouldBe a[RuntimeException]
  }

  it should "preserve its fields via case class accessors" in {
    val error = NodeTypeMismatchError("node1", CType.CBoolean, CType.CFloat)
    error.name shouldBe "node1"
    error.expected shouldBe CType.CBoolean
    error.actual shouldBe CType.CFloat
  }

  // ---------------------------------------------------------------------------
  // NodeAlreadyResolvedError
  // ---------------------------------------------------------------------------

  "NodeAlreadyResolvedError" should "contain the node name in the message" in {
    val error = NodeAlreadyResolvedError("resolvedNode")
    error.getMessage should include("resolvedNode")
  }

  it should "be a RuntimeException" in {
    val error = NodeAlreadyResolvedError("x")
    error shouldBe a[RuntimeException]
  }

  it should "preserve the name field" in {
    val error = NodeAlreadyResolvedError("computedNode")
    error.name shouldBe "computedNode"
  }

  // ---------------------------------------------------------------------------
  // PipelineChangedError
  // ---------------------------------------------------------------------------

  "PipelineChangedError" should "contain the expected hash in the message" in {
    val error = PipelineChangedError("abc123", "def456")
    error.getMessage should include("abc123")
  }

  it should "contain the actual hash in the message" in {
    val error = PipelineChangedError("abc123", "def456")
    error.getMessage should include("def456")
  }

  it should "be a RuntimeException" in {
    val error = PipelineChangedError("x", "y")
    error shouldBe a[RuntimeException]
  }

  it should "preserve its fields via case class accessors" in {
    val error = PipelineChangedError("hash1", "hash2")
    error.expected shouldBe "hash1"
    error.actual shouldBe "hash2"
  }

  // ---------------------------------------------------------------------------
  // PipelineNotFoundError
  // ---------------------------------------------------------------------------

  "PipelineNotFoundError" should "contain the pipeline ref in the message" in {
    val error = PipelineNotFoundError("my-pipeline-ref")
    error.getMessage should include("my-pipeline-ref")
  }

  it should "be a RuntimeException" in {
    val error = PipelineNotFoundError("ref")
    error shouldBe a[RuntimeException]
  }

  it should "preserve the ref field" in {
    val error = PipelineNotFoundError("pipeline-42")
    error.ref shouldBe "pipeline-42"
  }

  // ---------------------------------------------------------------------------
  // ResumeInProgressError
  // ---------------------------------------------------------------------------

  "ResumeInProgressError" should "contain the execution ID in the message" in {
    val id    = UUID.randomUUID()
    val error = ResumeInProgressError(id)
    error.getMessage should include(id.toString)
  }

  it should "be a RuntimeException" in {
    val error = ResumeInProgressError(UUID.randomUUID())
    error shouldBe a[RuntimeException]
  }

  it should "preserve the executionId field" in {
    val id    = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
    val error = ResumeInProgressError(id)
    error.executionId shouldBe id
  }

  // ---------------------------------------------------------------------------
  // All error types extend RuntimeException
  // ---------------------------------------------------------------------------

  "All suspendable execution error types" should "be subclasses of RuntimeException" in {
    val uuid = UUID.randomUUID()
    val errors: List[Throwable] = List(
      InputTypeMismatchError("a", CType.CString, CType.CInt),
      InputAlreadyProvidedError("b"),
      UnknownNodeError("c"),
      NodeTypeMismatchError("d", CType.CInt, CType.CFloat),
      NodeAlreadyResolvedError("e"),
      PipelineChangedError("f", "g"),
      PipelineNotFoundError("h"),
      ResumeInProgressError(uuid)
    )

    errors.foreach { err =>
      err shouldBe a[RuntimeException]
    }
  }

  // ---------------------------------------------------------------------------
  // RetryExhaustedException
  // ---------------------------------------------------------------------------

  "RetryExhaustedException" should "be a RuntimeException" in {
    val error = RetryExhaustedException("Retries exhausted", 3, List.empty)
    error shouldBe a[RuntimeException]
  }

  it should "store the message, totalAttempts, and errors fields" in {
    val cause1 = new RuntimeException("timeout")
    val cause2 = new IllegalArgumentException("bad input")
    val error  = RetryExhaustedException("All retries failed", 2, List(cause1, cause2))

    error.getMessage shouldBe "All retries failed"
    error.totalAttempts shouldBe 2
    error.errors shouldBe List(cause1, cause2)
  }

  it should "produce a detailedMessage with attempt numbers and error info" in {
    val cause1 = new RuntimeException("connection refused")
    val cause2 = new IllegalStateException("server down")
    val error =
      RetryExhaustedException("Operation failed after 2 attempts", 2, List(cause1, cause2))

    val detail = error.detailedMessage
    detail should include("Operation failed after 2 attempts")
    detail should include("Attempt 1")
    detail should include("RuntimeException")
    detail should include("connection refused")
    detail should include("Attempt 2")
    detail should include("IllegalStateException")
    detail should include("server down")
  }

  it should "use detailedMessage for toString" in {
    val cause = new RuntimeException("fail")
    val error = RetryExhaustedException("Exhausted", 1, List(cause))

    error.toString shouldBe error.detailedMessage
  }

  it should "handle an empty errors list in detailedMessage" in {
    val error  = RetryExhaustedException("No attempts logged", 0, List.empty)
    val detail = error.detailedMessage
    detail should include("No attempts logged")
  }

  // ---------------------------------------------------------------------------
  // ModuleTimeoutException
  // ---------------------------------------------------------------------------

  "ModuleTimeoutException" should "be a RuntimeException" in {
    val error = ModuleTimeoutException("timed out", 5.seconds)
    error shouldBe a[RuntimeException]
  }

  it should "store the message and timeout fields" in {
    val error = ModuleTimeoutException("Module X exceeded timeout", 10.seconds)
    error.getMessage shouldBe "Module X exceeded timeout"
    error.timeout shouldBe 10.seconds
  }

  it should "produce a toString that includes the message and timeout in millis" in {
    val error = ModuleTimeoutException("Slow module", 3.seconds)
    val str   = error.toString
    str should include("ModuleTimeoutException")
    str should include("Slow module")
    str should include("3000ms")
  }

  it should "correctly convert sub-second timeouts to millis in toString" in {
    val error = ModuleTimeoutException("Quick timeout", 250.milliseconds)
    error.toString should include("250ms")
  }

  it should "preserve the timeout duration accurately" in {
    val duration = 42.seconds
    val error    = ModuleTimeoutException("test", duration)
    error.timeout shouldBe duration
    error.timeout.toMillis shouldBe 42000L
  }
}
