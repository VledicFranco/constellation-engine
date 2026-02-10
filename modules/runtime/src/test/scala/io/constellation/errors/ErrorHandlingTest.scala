package io.constellation.errors

import java.util.concurrent.atomic.AtomicReference

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ErrorHandlingTest extends AnyFlatSpec with Matchers {

  // ============= liftIO =============

  "liftIO" should "lift successful IO to Right" in {
    val result = ErrorHandling.liftIO(IO.pure(42))(_.getMessage).value.unsafeRunSync()
    result shouldBe Right(42)
  }

  it should "lift failed IO to Left with mapped error" in {
    val result = ErrorHandling
      .liftIO(IO.raiseError[Int](new RuntimeException("boom")))(_.getMessage)
      .value
      .unsafeRunSync()
    result shouldBe Left("boom")
  }

  // ============= fromEither =============

  "fromEither" should "lift Right" in {
    val result = ErrorHandling.fromEither[String, Int](Right(42)).value.unsafeRunSync()
    result shouldBe Right(42)
  }

  it should "lift Left" in {
    val result = ErrorHandling.fromEither[String, Int](Left("error")).value.unsafeRunSync()
    result shouldBe Left("error")
  }

  // ============= fromOption =============

  "fromOption" should "lift Some to Right" in {
    val result = ErrorHandling.fromOption(Some(42), "missing").value.unsafeRunSync()
    result shouldBe Right(42)
  }

  it should "lift None to Left" in {
    val result = ErrorHandling.fromOption(None, "missing").value.unsafeRunSync()
    result shouldBe Left("missing")
  }

  // ============= pure =============

  "pure" should "lift value to Right" in {
    val result = ErrorHandling.pure[String, Int](42).value.unsafeRunSync()
    result shouldBe Right(42)
  }

  // ============= handleNotification =============

  "handleNotification" should "complete successfully for successful IO" in {
    val logged                     = new AtomicReference[String]("")
    val logger: String => IO[Unit] = msg => IO(logged.set(msg))

    ErrorHandling.handleNotification("test", logger)(IO.unit).unsafeRunSync()
    logged.get() shouldBe ""
  }

  it should "log error and complete for failed IO" in {
    val logged                     = new AtomicReference[String]("")
    val logger: String => IO[Unit] = msg => IO(logged.set(msg))

    ErrorHandling
      .handleNotification("test-op", logger)(IO.raiseError(new RuntimeException("fail")))
      .unsafeRunSync()

    logged.get() should include("Error in test-op")
    logged.get() should include("fail")
  }

  // ============= handleRequestWithLogging =============

  "handleRequestWithLogging" should "return Some for successful IO" in {
    val logger: String => IO[Unit] = _ => IO.unit

    val result = ErrorHandling.handleRequestWithLogging("test", logger)(IO.pure(42)).unsafeRunSync()
    result shouldBe Some(42)
  }

  it should "return None and log for failed IO" in {
    val logged                     = new AtomicReference[String]("")
    val logger: String => IO[Unit] = msg => IO(logged.set(msg))

    val result = ErrorHandling
      .handleRequestWithLogging[Int]("test-op", logger)(
        IO.raiseError(new RuntimeException("boom"))
      )
      .unsafeRunSync()

    result shouldBe None
    logged.get() should include("Error in test-op")
    logged.get() should include("boom")
  }

  // ============= executeWithResult =============

  "executeWithResult" should "use onSuccess for successful IO" in {
    val result = ErrorHandling
      .executeWithResult(IO.pure(42))(
        onSuccess = v => s"ok: $v",
        onError = e => s"err: ${e.getMessage}"
      )
      .unsafeRunSync()

    result shouldBe "ok: 42"
  }

  it should "use onError for failed IO" in {
    val result = ErrorHandling
      .executeWithResult(IO.raiseError[Int](new RuntimeException("fail")))(
        onSuccess = v => s"ok: $v",
        onError = e => s"err: ${e.getMessage}"
      )
      .unsafeRunSync()

    result shouldBe "err: fail"
  }

  // ============= ApiError subtypes =============

  "ApiError.InputError" should "have correct message" in {
    val err = ApiError.InputError("bad input")
    err.message shouldBe "bad input"
  }

  "ApiError.ExecutionError" should "have correct message" in {
    val err = ApiError.ExecutionError("exec failed")
    err.message shouldBe "exec failed"
  }

  "ApiError.OutputError" should "have correct message" in {
    val err = ApiError.OutputError("output error")
    err.message shouldBe "output error"
  }

  "ApiError.NotFoundError" should "format resource and name in message" in {
    val err = ApiError.NotFoundError("Pipeline", "my-pipeline")
    err.message shouldBe "Pipeline 'my-pipeline' not found"
    err.resource shouldBe "Pipeline"
    err.name shouldBe "my-pipeline"
  }

  "ApiError.CompilationError" should "join errors with semicolons" in {
    val err = ApiError.CompilationError(List("Error 1", "Error 2", "Error 3"))
    err.message shouldBe "Error 1; Error 2; Error 3"
  }

  it should "handle single error" in {
    val err = ApiError.CompilationError(List("Only error"))
    err.message shouldBe "Only error"
  }

  it should "handle empty errors list" in {
    val err = ApiError.CompilationError(List.empty)
    err.message shouldBe ""
  }

  "ApiError.fromThrowable" should "create ExecutionError with prefix" in {
    val err = ApiError.fromThrowable("prefix")(new RuntimeException("detail"))
    err shouldBe a[ApiError.ExecutionError]
    err.message shouldBe "prefix: detail"
  }
}
