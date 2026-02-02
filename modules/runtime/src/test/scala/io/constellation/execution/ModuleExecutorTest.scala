package io.constellation.execution

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.RetrySupport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable

import scala.concurrent.duration.*
import java.util.concurrent.atomic.AtomicInteger

class ModuleExecutorTest extends AnyFlatSpec with Matchers with RetrySupport {

  // -------------------------------------------------------------------------
  // Retry Tests
  // -------------------------------------------------------------------------

  "executeWithRetry" should "succeed on first attempt if no error" in {
    val result = ModuleExecutor
      .executeWithRetry(
        IO.pure(42),
        maxRetries = 3
      )
      .unsafeRunSync()

    result shouldBe 42
  }

  it should "retry on failure and succeed" in {
    val counter = new AtomicInteger(0)

    val operation = IO {
      val attempt = counter.incrementAndGet()
      if attempt < 3 then throw new RuntimeException(s"Attempt $attempt failed")
      else "success"
    }

    val result = ModuleExecutor
      .executeWithRetry(
        operation,
        maxRetries = 3
      )
      .unsafeRunSync()

    result shouldBe "success"
    counter.get() shouldBe 3
  }

  it should "throw RetryExhaustedException when all retries fail" in {
    val counter = new AtomicInteger(0)

    val operation = IO {
      counter.incrementAndGet()
      throw new RuntimeException("Always fails")
    }

    val error = intercept[RetryExhaustedException] {
      ModuleExecutor
        .executeWithRetry(
          operation,
          maxRetries = 2
        )
        .unsafeRunSync()
    }

    error.totalAttempts shouldBe 3 // 1 initial + 2 retries
    error.errors.size shouldBe 3
    counter.get() shouldBe 3
  }

  it should "apply delay between retries" taggedAs Retryable in {
    val counter = new AtomicInteger(0)

    val operation = IO {
      val attempt = counter.incrementAndGet()
      if attempt < 2 then throw new RuntimeException("Retry")
      else "done"
    }

    val (duration, result) = ModuleExecutor
      .executeWithRetry(
        operation,
        maxRetries = 2,
        delay = Some(100.millis)
      )
      .timed
      .unsafeRunSync()

    result shouldBe "done"
    duration.toMillis should be >= 100L // At least one delay
  }

  it should "call onRetry callback before each retry" in {
    val retryLog = scala.collection.mutable.ListBuffer[(Int, String)]()
    val counter  = new AtomicInteger(0)

    val operation = IO {
      val attempt = counter.incrementAndGet()
      if attempt < 3 then throw new RuntimeException(s"Error $attempt")
      else "ok"
    }

    val result = ModuleExecutor
      .executeWithRetry(
        operation,
        maxRetries = 3,
        onRetry = Some((attempt, error) =>
          IO {
            retryLog += ((attempt, error.getMessage))
          }
        )
      )
      .unsafeRunSync()

    result shouldBe "ok"
    retryLog.toList shouldBe List(
      (1, "Error 1"),
      (2, "Error 2")
    )
  }

  it should "not retry when maxRetries is 0" in {
    val counter = new AtomicInteger(0)

    val operation = IO {
      counter.incrementAndGet()
      throw new RuntimeException("Fail")
    }

    intercept[RuntimeException] {
      ModuleExecutor
        .executeWithRetry(
          operation,
          maxRetries = 0
        )
        .unsafeRunSync()
    }

    counter.get() shouldBe 1
  }

  // -------------------------------------------------------------------------
  // Timeout Tests
  // -------------------------------------------------------------------------

  "executeWithTimeout" should "succeed if operation completes in time" in {
    val result = ModuleExecutor
      .executeWithTimeout(
        IO.pure(42),
        timeout = 1.second
      )
      .unsafeRunSync()

    result shouldBe 42
  }

  it should "throw ModuleTimeoutException if operation exceeds timeout" in {
    val error = intercept[ModuleTimeoutException] {
      ModuleExecutor
        .executeWithTimeout(
          IO.sleep(500.millis) *> IO.pure(42),
          timeout = 100.millis
        )
        .unsafeRunSync()
    }

    error.timeout shouldBe 100.millis
    error.message should include("100ms")
  }

  it should "cancel the operation on timeout" in {
    val completed = new AtomicInteger(0)

    val operation = IO.sleep(500.millis) *> IO {
      completed.incrementAndGet()
      42
    }

    intercept[ModuleTimeoutException] {
      ModuleExecutor
        .executeWithTimeout(
          operation,
          timeout = 50.millis
        )
        .unsafeRunSync()
    }

    // Give some time for any potential completion
    Thread.sleep(600)
    completed.get() shouldBe 0 // Should not have completed
  }

  // -------------------------------------------------------------------------
  // Fallback Tests
  // -------------------------------------------------------------------------

  "executeWithFallback" should "return result if operation succeeds" in {
    val result = ModuleExecutor
      .executeWithFallback(
        IO.pure(42),
        fallback = IO.pure(0)
      )
      .unsafeRunSync()

    result shouldBe 42
  }

  it should "return fallback if operation fails" in {
    val result = ModuleExecutor
      .executeWithFallback(
        IO.raiseError[Int](new RuntimeException("Failed")),
        fallback = IO.pure(0)
      )
      .unsafeRunSync()

    result shouldBe 0
  }

  it should "evaluate fallback lazily" in {
    val fallbackCalled = new AtomicInteger(0)

    val result = ModuleExecutor
      .executeWithFallback(
        IO.pure(42),
        fallback = IO {
          fallbackCalled.incrementAndGet()
          0
        }
      )
      .unsafeRunSync()

    result shouldBe 42
    fallbackCalled.get() shouldBe 0 // Fallback never evaluated
  }

  it should "call onFallback callback when fallback is used" in {
    var capturedError: Option[Throwable] = None

    val result = ModuleExecutor
      .executeWithFallback(
        IO.raiseError[Int](new RuntimeException("Original error")),
        fallback = IO.pure(0),
        onFallback = Some(error =>
          IO {
            capturedError = Some(error)
          }
        )
      )
      .unsafeRunSync()

    result shouldBe 0
    capturedError shouldBe defined
    capturedError.get.getMessage shouldBe "Original error"
  }

  // -------------------------------------------------------------------------
  // Combined Execution Tests
  // -------------------------------------------------------------------------

  "execute with combined options" should "apply timeout per retry attempt" in {
    val counter = new AtomicInteger(0)

    val operation = IO {
      val attempt = counter.incrementAndGet()
      if attempt < 2 then {
        Thread.sleep(200) // First attempt times out
        "slow"
      } else {
        "fast"
      }
    }

    val result = ModuleExecutor
      .execute(
        operation,
        ExecutionOptions(
          retry = Some(2),
          timeout = Some(100.millis)
        )
      )
      .unsafeRunSync()

    result shouldBe "fast"
    counter.get() shouldBe 2
  }

  it should "use fallback after retries are exhausted" in {
    val counter = new AtomicInteger(0)

    val operation = IO {
      counter.incrementAndGet()
      throw new RuntimeException("Always fails")
    }

    val result = ModuleExecutor
      .execute[String](
        operation,
        ExecutionOptions(
          retry = Some(2),
          fallback = Some(IO.pure("fallback"))
        )
      )
      .unsafeRunSync()

    result shouldBe "fallback"
    counter.get() shouldBe 3 // 1 initial + 2 retries
  }

  it should "use fallback after timeout with no retries" in {
    val result = ModuleExecutor
      .execute[String](
        IO.sleep(500.millis) *> IO.pure("slow"),
        ExecutionOptions(
          timeout = Some(50.millis),
          fallback = Some(IO.pure("fast fallback"))
        )
      )
      .unsafeRunSync()

    result shouldBe "fast fallback"
  }

  // -------------------------------------------------------------------------
  // Convenience Methods Tests
  // -------------------------------------------------------------------------

  "retryWithDelay" should "retry with specified delay" taggedAs Retryable in {
    val counter = new AtomicInteger(0)

    val (duration, result) = ModuleExecutor
      .retryWithDelay(
        IO {
          if counter.incrementAndGet() < 2 then throw new RuntimeException("Retry")
          else "done"
        },
        maxRetries = 2,
        delay = 50.millis
      )
      .timed
      .unsafeRunSync()

    result shouldBe "done"
    duration.toMillis should be >= 50L
  }

  "timeoutOrElse" should "return fallback on timeout" in {
    val result = ModuleExecutor
      .timeoutOrElse(
        IO.sleep(500.millis) *> IO.pure(42),
        timeout = 50.millis,
        fallback = 0
      )
      .unsafeRunSync()

    result shouldBe 0
  }

  it should "return result if no timeout" in {
    val result = ModuleExecutor
      .timeoutOrElse(
        IO.pure(42),
        timeout = 1.second,
        fallback = 0
      )
      .unsafeRunSync()

    result shouldBe 42
  }

  // -------------------------------------------------------------------------
  // Error Details Tests
  // -------------------------------------------------------------------------

  "RetryExhaustedException" should "provide detailed error message" in {
    val errors = List(
      new RuntimeException("Network error"),
      new RuntimeException("Timeout"),
      new RuntimeException("Service unavailable")
    )

    val exception = RetryExhaustedException(
      "Operation failed after 3 attempts",
      3,
      errors
    )

    val detailed = exception.detailedMessage
    detailed should include("Attempt 1: RuntimeException: Network error")
    detailed should include("Attempt 2: RuntimeException: Timeout")
    detailed should include("Attempt 3: RuntimeException: Service unavailable")
  }

  "ModuleTimeoutException" should "include timeout duration in message" in {
    val exception = ModuleTimeoutException("Operation timed out", 30.seconds)

    exception.toString should include("30000ms")
  }

  // -------------------------------------------------------------------------
  // Backoff Strategy Tests
  // -------------------------------------------------------------------------

  "BackoffStrategy.computeDelay" should "return constant delay for Fixed" in {
    val base = 100.millis

    BackoffStrategy.computeDelay(base, 1, BackoffStrategy.Fixed) shouldBe 100.millis
    BackoffStrategy.computeDelay(base, 2, BackoffStrategy.Fixed) shouldBe 100.millis
    BackoffStrategy.computeDelay(base, 5, BackoffStrategy.Fixed) shouldBe 100.millis
  }

  it should "return linear delay for Linear" in {
    val base = 100.millis

    BackoffStrategy.computeDelay(base, 1, BackoffStrategy.Linear) shouldBe 100.millis
    BackoffStrategy.computeDelay(base, 2, BackoffStrategy.Linear) shouldBe 200.millis
    BackoffStrategy.computeDelay(base, 3, BackoffStrategy.Linear) shouldBe 300.millis
    BackoffStrategy.computeDelay(base, 5, BackoffStrategy.Linear) shouldBe 500.millis
  }

  it should "return exponential delay for Exponential" in {
    val base = 100.millis

    BackoffStrategy.computeDelay(base, 1, BackoffStrategy.Exponential) shouldBe 100.millis
    BackoffStrategy.computeDelay(base, 2, BackoffStrategy.Exponential) shouldBe 200.millis
    BackoffStrategy.computeDelay(base, 3, BackoffStrategy.Exponential) shouldBe 400.millis
    BackoffStrategy.computeDelay(base, 4, BackoffStrategy.Exponential) shouldBe 800.millis
    BackoffStrategy.computeDelay(base, 5, BackoffStrategy.Exponential) shouldBe 1600.millis
  }

  it should "cap delay at maxDelay" in {
    val base     = 1.second
    val maxDelay = 5.seconds

    // Exponential: 1s, 2s, 4s, 8s -> capped at 5s
    BackoffStrategy.computeDelay(base, 1, BackoffStrategy.Exponential, maxDelay) shouldBe 1.second
    BackoffStrategy.computeDelay(base, 2, BackoffStrategy.Exponential, maxDelay) shouldBe 2.seconds
    BackoffStrategy.computeDelay(base, 3, BackoffStrategy.Exponential, maxDelay) shouldBe 4.seconds
    BackoffStrategy.computeDelay(
      base,
      4,
      BackoffStrategy.Exponential,
      maxDelay
    ) shouldBe 5.seconds // capped
    BackoffStrategy.computeDelay(
      base,
      5,
      BackoffStrategy.Exponential,
      maxDelay
    ) shouldBe 5.seconds // capped
  }

  "executeWithRetry with backoff" should "apply exponential backoff delays" taggedAs Retryable in {
    val counter   = new AtomicInteger(0)
    val startTime = System.nanoTime()

    val operation = IO {
      val attempt = counter.incrementAndGet()
      if attempt < 4 then throw new RuntimeException(s"Attempt $attempt")
      else "success"
    }

    val result = ModuleExecutor
      .executeWithRetry(
        operation,
        maxRetries = 3,
        delay = Some(50.millis),
        backoff = BackoffStrategy.Exponential
      )
      .unsafeRunSync()

    val totalTime = (System.nanoTime() - startTime) / 1e6

    result shouldBe "success"
    counter.get() shouldBe 4

    // Expected delays: 50ms (attempt 1), 100ms (attempt 2), 200ms (attempt 3) = 350ms total
    // Allow some margin for execution time
    totalTime should be >= 300.0
  }

  it should "apply linear backoff delays" taggedAs Retryable in {
    val counter   = new AtomicInteger(0)
    val startTime = System.nanoTime()

    val operation = IO {
      val attempt = counter.incrementAndGet()
      if attempt < 4 then throw new RuntimeException(s"Attempt $attempt")
      else "success"
    }

    val result = ModuleExecutor
      .executeWithRetry(
        operation,
        maxRetries = 3,
        delay = Some(50.millis),
        backoff = BackoffStrategy.Linear
      )
      .unsafeRunSync()

    val totalTime = (System.nanoTime() - startTime) / 1e6

    result shouldBe "success"
    counter.get() shouldBe 4

    // Expected delays: 50ms (attempt 1), 100ms (attempt 2), 150ms (attempt 3) = 300ms total
    totalTime should be >= 250.0
  }

  it should "respect maxDelay cap" taggedAs Retryable in {
    val counter   = new AtomicInteger(0)
    val startTime = System.nanoTime()

    val operation = IO {
      val attempt = counter.incrementAndGet()
      if attempt < 4 then throw new RuntimeException(s"Attempt $attempt")
      else "success"
    }

    val result = ModuleExecutor
      .executeWithRetry(
        operation,
        maxRetries = 3,
        delay = Some(100.millis),
        backoff = BackoffStrategy.Exponential,
        maxDelay = 150.millis // Cap at 150ms
      )
      .unsafeRunSync()

    val totalTime = (System.nanoTime() - startTime) / 1e6

    result shouldBe "success"

    // Expected delays: 100ms (attempt 1), 150ms (capped from 200ms), 150ms (capped from 400ms) = 400ms total
    // Should be less than if uncapped: 100 + 200 + 400 = 700ms
    totalTime should be < 600.0
  }
}
