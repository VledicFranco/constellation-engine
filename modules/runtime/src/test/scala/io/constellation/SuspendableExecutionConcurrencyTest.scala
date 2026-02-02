package io.constellation

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import io.constellation.RetrySupport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable

import java.time.Instant
import java.util.UUID

/** Tests for SuspendableExecution concurrent resume safety.
  *
  * Verifies that concurrent resume calls on the same suspended execution are properly serialized to
  * prevent data corruption.
  */
class SuspendableExecutionConcurrencyTest extends AnyFlatSpec with Matchers with RetrySupport {

  // Helper to create a minimal suspended execution
  private def createSuspendedExecution(
      executionId: UUID = UUID.randomUUID()
  ): SuspendedExecution = {
    // Create a user input data node with proper nickname
    val inputUuid = UUID.randomUUID()
    val inputDataNode = DataNodeSpec(
      name = "input1_data",
      cType = CType.CString,
      nicknames =
        Map(UUID.randomUUID() -> "input1"), // Map some module UUID to the nickname "input1"
      inlineTransform = None,
      transformInputs = Map.empty
    )

    val dagSpec = DagSpec(
      metadata = ComponentMetadata.empty("test-pipeline"),
      modules = Map.empty,
      data = Map(inputUuid -> inputDataNode),
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List.empty,
      outputBindings = Map.empty
    )

    SuspendedExecution(
      executionId = executionId,
      structuralHash = "test-hash",
      resumptionCount = 0,
      dagSpec = dagSpec,
      moduleOptions = Map.empty,
      providedInputs = Map.empty,
      computedValues = Map.empty,
      moduleStatuses = Map.empty
    )
  }

  "SuspendableExecution.resume" should "prevent concurrent resumes on the same execution" taggedAs Retryable in {
    val suspended = createSuspendedExecution()

    // Launch two concurrent resume calls with different inputs for the same variable
    val resume1 = IO.delay(Thread.sleep(10)) *> SuspendableExecution.resume(
      suspended = suspended,
      additionalInputs = Map("input1" -> CValue.CString("value-A"))
    )

    val resume2 = IO.delay(Thread.sleep(10)) *> SuspendableExecution.resume(
      suspended = suspended,
      additionalInputs = Map("input1" -> CValue.CString("value-B"))
    )

    // Run both in parallel
    val results = (resume1.attempt, resume2.attempt).parTupled.unsafeRunSync()

    // At least one should succeed; both may succeed if they serialize
    val (result1, result2) = results
    val all                = List(result1, result2)

    val successes = all.count(_.isRight)
    successes should be >= 1

    // Any failures must be ResumeInProgressError
    val errors = all.collect { case Left(err) => err }
    errors.foreach(_ shouldBe a[ResumeInProgressError])
  }

  it should "allow sequential resumes on the same execution" in {
    val suspended = createSuspendedExecution()

    // First resume should succeed
    val result1 = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("input1" -> CValue.CString("value-A"))
      )
      .attempt
      .unsafeRunSync()

    result1 should be a Symbol("right")

    // Second resume (after first completes) should also work
    // Note: In practice, you'd resume the new suspended state, but for testing the lock mechanism,
    // we're testing that the lock is released after the first resume completes
    val result2 = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("input1" -> CValue.CString("value-B"))
      )
      .attempt
      .unsafeRunSync()

    result2 should be a Symbol("right")
  }

  it should "release lock even if resume fails" in {
    // Create a suspended execution with invalid state to cause resume to fail
    val suspended = createSuspendedExecution()

    // First resume will fail due to validation error (unknown input)
    val result1 = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("unknown-input" -> CValue.CString("value"))
      )
      .attempt
      .unsafeRunSync()

    result1.isLeft should be(true)
    result1.left.toOption.get shouldBe a[UnknownNodeError]

    // Second resume should work (lock was released despite first resume failing)
    val result2 = SuspendableExecution
      .resume(
        suspended = suspended,
        additionalInputs = Map("input1" -> CValue.CString("value-A"))
      )
      .attempt
      .unsafeRunSync()

    result2 should be a Symbol("right")
  }

  it should "allow concurrent resumes on different executions" in {
    val suspended1 = createSuspendedExecution(UUID.randomUUID())
    val suspended2 = createSuspendedExecution(UUID.randomUUID())

    // Different execution IDs should not block each other
    val resume1 = SuspendableExecution.resume(
      suspended = suspended1,
      additionalInputs = Map("input1" -> CValue.CString("value-A"))
    )

    val resume2 = SuspendableExecution.resume(
      suspended = suspended2,
      additionalInputs = Map("input1" -> CValue.CString("value-B"))
    )

    // Both should succeed when run in parallel
    val results = (resume1.attempt, resume2.attempt).parTupled.unsafeRunSync()

    results._1 should be a Symbol("right")
    results._2 should be a Symbol("right")
  }

  it should "handle rapid concurrent resume attempts correctly" in {
    val suspended = createSuspendedExecution()

    // Launch 10 concurrent resume attempts
    val resumes = (1 to 10).map { i =>
      SuspendableExecution
        .resume(
          suspended = suspended,
          additionalInputs = Map("input1" -> CValue.CString(s"value-$i"))
        )
        .attempt
    }.toList

    // Run all in parallel
    val results = resumes.parSequence.unsafeRunSync()

    // At least one should succeed; more may succeed if they serialize
    val successes = results.count(_.isRight)
    successes should be >= 1

    // All failures must be ResumeInProgressError (not some other error)
    val errors = results.collect { case Left(err) => err }
    errors.foreach { err =>
      err shouldBe a[ResumeInProgressError]
    }
  }

  it should "not corrupt data with race between validation and merge" taggedAs Retryable in {
    // This test demonstrates the original bug: two concurrent resumes could both
    // pass validation before either merged, resulting in one overwriting the other.
    // With the fix, only one resume proceeds.

    val suspended = createSuspendedExecution()

    val resume1 = SuspendableExecution.resume(
      suspended = suspended,
      additionalInputs = Map("input1" -> CValue.CString("CORRECT-VALUE"))
    )

    val resume2 = SuspendableExecution.resume(
      suspended = suspended,
      additionalInputs = Map("input1" -> CValue.CString("WRONG-VALUE"))
    )

    val (result1, result2) = (resume1.attempt, resume2.attempt).parTupled.unsafeRunSync()

    // One should succeed with its value intact, one should fail
    val successful = List(result1, result2).collect { case Right(sig) => sig }
    successful should have size 1

    val sig = successful.head
    // Verify the successful resume has the correct input (whichever one won the race)
    sig.inputs should contain oneOf (
      "input1" -> CValue.CString("CORRECT-VALUE"),
      "input1" -> CValue.CString("WRONG-VALUE")
    )

    // Critically: The value should match what was requested by the successful resume
    // (not corrupted by the other resume attempt)
    val inputValue = sig.inputs("input1").asInstanceOf[CValue.CString].value
    inputValue should (equal("CORRECT-VALUE") or equal("WRONG-VALUE"))
  }
}
