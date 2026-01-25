package io.constellation.execution

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import io.constellation.{CType, CValue}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._

// ============================================================================
// ErrorStrategy Tests
// ============================================================================

class ErrorStrategyTest extends AnyFlatSpec with Matchers {

  "ErrorStrategy.fromString" should "parse propagate" in {
    ErrorStrategy.fromString("propagate") shouldBe Some(ErrorStrategy.Propagate)
    ErrorStrategy.fromString("PROPAGATE") shouldBe Some(ErrorStrategy.Propagate)
  }

  it should "parse skip" in {
    ErrorStrategy.fromString("skip") shouldBe Some(ErrorStrategy.Skip)
  }

  it should "parse log" in {
    ErrorStrategy.fromString("log") shouldBe Some(ErrorStrategy.Log)
  }

  it should "parse wrap" in {
    ErrorStrategy.fromString("wrap") shouldBe Some(ErrorStrategy.Wrap)
  }

  it should "return None for invalid" in {
    ErrorStrategy.fromString("invalid") shouldBe None
    ErrorStrategy.fromString("") shouldBe None
  }
}

class ErrorStrategyExecutorTest extends AnyFlatSpec with Matchers {

  val successOperation: IO[CValue] = IO.pure(CValue.CInt(42))
  val failingOperation: IO[CValue] = IO.raiseError(new RuntimeException("test error"))

  "ErrorStrategyExecutor with Propagate" should "return success value" in {
    val result = ErrorStrategyExecutor.execute(
      successOperation,
      ErrorStrategy.Propagate,
      CType.CInt,
      "TestModule"
    ).unsafeRunSync()

    result shouldBe CValue.CInt(42)
  }

  it should "propagate errors" in {
    val result = ErrorStrategyExecutor.execute(
      failingOperation,
      ErrorStrategy.Propagate,
      CType.CInt,
      "TestModule"
    ).attempt.unsafeRunSync()

    result.isLeft shouldBe true
    result.left.toOption.get.getMessage shouldBe "test error"
  }

  "ErrorStrategyExecutor with Skip" should "return success value" in {
    val result = ErrorStrategyExecutor.execute(
      successOperation,
      ErrorStrategy.Skip,
      CType.CInt,
      "TestModule"
    ).unsafeRunSync()

    result shouldBe CValue.CInt(42)
  }

  it should "return zero value on error" in {
    val result = ErrorStrategyExecutor.execute(
      failingOperation,
      ErrorStrategy.Skip,
      CType.CInt,
      "TestModule"
    ).unsafeRunSync()

    result shouldBe CValue.CInt(0)
  }

  "ErrorStrategyExecutor with Log" should "return success value" in {
    val result = ErrorStrategyExecutor.execute(
      successOperation,
      ErrorStrategy.Log,
      CType.CInt,
      "TestModule"
    ).unsafeRunSync()

    result shouldBe CValue.CInt(42)
  }

  it should "log and return zero value on error" in {
    // Note: This test doesn't verify logging, just behavior
    val result = ErrorStrategyExecutor.execute(
      failingOperation,
      ErrorStrategy.Log,
      CType.CInt,
      "TestModule"
    ).unsafeRunSync()

    result shouldBe CValue.CInt(0)
  }

  "ErrorStrategyExecutor with Wrap" should "wrap success in Right" in {
    val result = ErrorStrategyExecutor.execute(
      successOperation,
      ErrorStrategy.Wrap,
      CType.CInt,
      "TestModule"
    ).unsafeRunSync()

    result shouldBe Right(CValue.CInt(42))
  }

  it should "wrap error in Left(ModuleError)" in {
    val result = ErrorStrategyExecutor.execute(
      failingOperation,
      ErrorStrategy.Wrap,
      CType.CInt,
      "TestModule"
    ).unsafeRunSync()

    result shouldBe a[Left[_, _]]
    val Left(error) = result: @unchecked
    error shouldBe a[ModuleError]
    val moduleError = error.asInstanceOf[ModuleError]
    moduleError.moduleName shouldBe "TestModule"
    moduleError.message shouldBe "test error"
  }

  "zeroValue" should "return empty string for CString" in {
    ErrorStrategyExecutor.zeroValue(CType.CString) shouldBe CValue.CString("")
  }

  it should "return 0 for CInt" in {
    ErrorStrategyExecutor.zeroValue(CType.CInt) shouldBe CValue.CInt(0)
  }

  it should "return 0.0 for CFloat" in {
    ErrorStrategyExecutor.zeroValue(CType.CFloat) shouldBe CValue.CFloat(0.0)
  }

  it should "return false for CBoolean" in {
    ErrorStrategyExecutor.zeroValue(CType.CBoolean) shouldBe CValue.CBoolean(false)
  }

  it should "return empty list for CList" in {
    val listType = CType.CList(CType.CInt)
    ErrorStrategyExecutor.zeroValue(listType) shouldBe CValue.CList(Vector.empty, CType.CInt)
  }

  it should "return empty map for CMap" in {
    val mapType = CType.CMap(CType.CString, CType.CInt)
    ErrorStrategyExecutor.zeroValue(mapType) shouldBe CValue.CMap(Vector.empty, CType.CString, CType.CInt)
  }

  it should "return None for COptional" in {
    val optType = CType.COptional(CType.CInt)
    val result = ErrorStrategyExecutor.zeroValue(optType)
    result shouldBe a[CValue.CNone]
  }

  it should "return product with zero fields for CProduct" in {
    val productType = CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CString))
    val result = ErrorStrategyExecutor.zeroValue(productType)
    result shouldBe a[CValue.CProduct]
    val product = result.asInstanceOf[CValue.CProduct]
    product.value("x") shouldBe CValue.CInt(0)
    product.value("y") shouldBe CValue.CString("")
  }

  "hasZeroValue" should "return true for primitive types" in {
    ErrorStrategyExecutor.hasZeroValue(CType.CString) shouldBe true
    ErrorStrategyExecutor.hasZeroValue(CType.CInt) shouldBe true
    ErrorStrategyExecutor.hasZeroValue(CType.CFloat) shouldBe true
    ErrorStrategyExecutor.hasZeroValue(CType.CBoolean) shouldBe true
  }

  it should "return true for collections" in {
    ErrorStrategyExecutor.hasZeroValue(CType.CList(CType.CInt)) shouldBe true
    ErrorStrategyExecutor.hasZeroValue(CType.COptional(CType.CInt)) shouldBe true
  }
}

// ============================================================================
// LazyValue Tests
// ============================================================================

class LazyValueTest extends AnyFlatSpec with Matchers {

  "LazyValue" should "defer computation until forced" in {
    var executed = false

    val (lazyVal, wasExecuted) = (for {
      lv <- LazyValue(IO { executed = true; 42 })
      before = executed
      _ <- lv.force
      after = executed
    } yield (lv, (before, after))).unsafeRunSync()

    wasExecuted shouldBe (false, true)
  }

  it should "memoize result" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      lv <- LazyValue(IO { counter.incrementAndGet() })
      r1 <- lv.force
      r2 <- lv.force
      r3 <- lv.force
    } yield (r1, r2, r3, counter.get())).unsafeRunSync()

    result shouldBe (1, 1, 1, 1) // All same value, computed once
  }

  it should "handle concurrent force calls" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      lv <- LazyValue(IO.sleep(50.millis) >> IO { counter.incrementAndGet() })
      // Force from multiple fibers concurrently
      results <- List.fill(5)(lv.force).parSequence
    } yield (results, counter.get())).unsafeRunSync()

    result._1.distinct.length shouldBe 1 // All got same value
    result._2 shouldBe 1 // Computed only once
  }

  it should "report isComputed correctly" in {
    val result = (for {
      lv <- LazyValue(IO.pure(42))
      before <- lv.isComputed
      _ <- lv.force
      after <- lv.isComputed
    } yield (before, after)).unsafeRunSync()

    result shouldBe (false, true)
  }

  it should "peek without forcing" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      lv <- LazyValue(IO { counter.incrementAndGet() })
      peek1 <- lv.peek
      _ <- lv.force
      peek2 <- lv.peek
    } yield (peek1, peek2, counter.get())).unsafeRunSync()

    result shouldBe (None, Some(1), 1)
  }

  it should "allow reset" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      lv <- LazyValue(IO { counter.incrementAndGet() })
      r1 <- lv.force
      _ <- lv.reset
      computed <- lv.isComputed
      r2 <- lv.force
    } yield (r1, computed, r2, counter.get())).unsafeRunSync()

    result shouldBe (1, false, 2, 2) // Computed twice after reset
  }

  it should "allow retry after error" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      lv <- LazyValue(IO {
        val count = counter.incrementAndGet()
        if (count == 1) throw new RuntimeException("first try fails")
        count
      })
      r1 <- lv.force.attempt
      r2 <- lv.force.attempt // Should retry since error wasn't cached
    } yield (r1.isLeft, r2, counter.get())).unsafeRunSync()

    result shouldBe (true, Right(2), 2)
  }

  "LazyValue.pure" should "create already-computed value" in {
    val result = (for {
      lv <- LazyValue.pure(42)
      computed <- lv.isComputed
      value <- lv.force
    } yield (computed, value)).unsafeRunSync()

    result shouldBe (true, 42)
  }

  "LazyValue.sequence" should "force all values" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      lvs <- LazyValue.fromList(List(
        IO { counter.incrementAndGet() },
        IO { counter.incrementAndGet() },
        IO { counter.incrementAndGet() }
      ))
      before = counter.get()
      results <- LazyValue.sequence(lvs)
      after = counter.get()
    } yield (before, results, after)).unsafeRunSync()

    result._1 shouldBe 0 // None computed yet
    result._2 shouldBe List(1, 2, 3)
    result._3 shouldBe 3
  }

  "LazyValue.map" should "create new lazy value" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      lv <- LazyValue(IO { counter.incrementAndGet() })
      mapped <- lv.map(_ * 10)
      before = counter.get()
      value <- mapped.force
      after = counter.get()
    } yield (before, value, after)).unsafeRunSync()

    result shouldBe (0, 10, 1)
  }
}

// ============================================================================
// PriorityScheduler Tests
// ============================================================================

class PriorityLevelTest extends AnyFlatSpec with Matchers {

  "PriorityLevel" should "have correct values" in {
    PriorityLevel.Critical.value shouldBe 100
    PriorityLevel.High.value shouldBe 75
    PriorityLevel.Normal.value shouldBe 50
    PriorityLevel.Low.value shouldBe 25
    PriorityLevel.Background.value shouldBe 0
  }

  it should "support custom values" in {
    PriorityLevel.Custom(42).value shouldBe 42
    PriorityLevel.Custom(-10).value shouldBe -10
  }

  "PriorityLevel.fromString" should "parse standard levels" in {
    PriorityLevel.fromString("critical") shouldBe Some(PriorityLevel.Critical)
    PriorityLevel.fromString("HIGH") shouldBe Some(PriorityLevel.High)
    PriorityLevel.fromString("Normal") shouldBe Some(PriorityLevel.Normal)
    PriorityLevel.fromString("low") shouldBe Some(PriorityLevel.Low)
    PriorityLevel.fromString("background") shouldBe Some(PriorityLevel.Background)
  }

  it should "parse numeric values as Custom" in {
    PriorityLevel.fromString("42") shouldBe Some(PriorityLevel.Custom(42))
    PriorityLevel.fromString("0") shouldBe Some(PriorityLevel.Custom(0))
    PriorityLevel.fromString("-5") shouldBe Some(PriorityLevel.Custom(-5))
  }

  it should "return None for invalid" in {
    PriorityLevel.fromString("invalid") shouldBe None
    PriorityLevel.fromString("") shouldBe None
  }

  "PriorityLevel ordering" should "order higher values first" in {
    val levels = List(
      PriorityLevel.Low,
      PriorityLevel.Critical,
      PriorityLevel.Normal,
      PriorityLevel.High
    )

    val sorted = levels.sorted(PriorityLevel.ordering)

    sorted shouldBe List(
      PriorityLevel.Critical,
      PriorityLevel.High,
      PriorityLevel.Normal,
      PriorityLevel.Low
    )
  }
}

class PrioritySchedulerTest extends AnyFlatSpec with Matchers {

  "PriorityScheduler" should "execute submitted tasks" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      scheduler <- PriorityScheduler.create
      r1 <- scheduler.submit(IO { counter.incrementAndGet() }, PriorityLevel.Normal)
      r2 <- scheduler.submit(IO { counter.incrementAndGet() }, PriorityLevel.High)
    } yield (r1, r2, counter.get())).unsafeRunSync()

    result shouldBe (1, 2, 2)
  }

  it should "use default Normal priority" in {
    val result = (for {
      scheduler <- PriorityScheduler.create
      _ <- scheduler.submit(IO.pure(42))
      stats <- scheduler.stats
    } yield stats.forPriority(PriorityLevel.Normal)).unsafeRunSync()

    result shouldBe defined
    result.get.submitted shouldBe 1
  }

  it should "track statistics by priority" in {
    val result = (for {
      scheduler <- PriorityScheduler.create
      _ <- scheduler.submit(IO.sleep(10.millis), PriorityLevel.Critical)
      _ <- scheduler.submit(IO.sleep(10.millis), PriorityLevel.Critical)
      _ <- scheduler.submit(IO.sleep(10.millis), PriorityLevel.Normal)
      _ <- scheduler.submit(IO.sleep(10.millis), PriorityLevel.Low)
      stats <- scheduler.stats
    } yield stats).unsafeRunSync()

    result.totalSubmitted shouldBe 4
    result.totalCompleted shouldBe 4
    result.forPriority(PriorityLevel.Critical).get.submitted shouldBe 2
    result.forPriority(PriorityLevel.Normal).get.submitted shouldBe 1
    result.forPriority(PriorityLevel.Low).get.submitted shouldBe 1
  }

  it should "track duration by priority" in {
    val result = (for {
      scheduler <- PriorityScheduler.create
      _ <- scheduler.submit(IO.sleep(50.millis), PriorityLevel.High)
      stats <- scheduler.stats
    } yield stats.forPriority(PriorityLevel.High).get).unsafeRunSync()

    result.completed shouldBe 1
    result.avgDurationMs should be >= 40L
  }

  it should "handle errors without affecting stats" in {
    val result = (for {
      scheduler <- PriorityScheduler.create
      _ <- scheduler.submit(IO.raiseError[Int](new RuntimeException("fail")), PriorityLevel.Normal).attempt
      stats <- scheduler.stats
    } yield stats).unsafeRunSync()

    result.totalSubmitted shouldBe 1
    result.totalCompleted shouldBe 1 // Still counts as completed (with error)
  }

  it should "reset statistics" in {
    val result = (for {
      scheduler <- PriorityScheduler.create
      _ <- scheduler.submit(IO.pure(1), PriorityLevel.High)
      _ <- scheduler.submit(IO.pure(2), PriorityLevel.Low)
      before <- scheduler.stats
      _ <- scheduler.resetStats
      after <- scheduler.stats
    } yield (before.totalSubmitted, after.totalSubmitted)).unsafeRunSync()

    result shouldBe (2, 0)
  }

  it should "calculate completion rate" in {
    val result = (for {
      scheduler <- PriorityScheduler.create
      _ <- scheduler.submit(IO.pure(1))
      _ <- scheduler.submit(IO.pure(2))
      stats <- scheduler.stats
    } yield stats.completionRate).unsafeRunSync()

    result shouldBe 1.0 // All completed
  }
}

class PriorityStatsTest extends AnyFlatSpec with Matchers {

  "PriorityStats" should "record submissions" in {
    val stats = PriorityStats.empty.recordSubmission
    stats.submitted shouldBe 1
    stats.completed shouldBe 0
  }

  it should "record completions with duration" in {
    val stats = PriorityStats.empty
      .recordSubmission
      .recordCompletion(100)

    stats.submitted shouldBe 1
    stats.completed shouldBe 1
    stats.totalDurationMs shouldBe 100
    stats.avgDurationMs shouldBe 100
  }

  it should "calculate average duration" in {
    val stats = PriorityStats.empty
      .recordSubmission.recordCompletion(100)
      .recordSubmission.recordCompletion(200)
      .recordSubmission.recordCompletion(300)

    stats.avgDurationMs shouldBe 200
  }

  it should "track pending count" in {
    val stats = PriorityStats.empty
      .recordSubmission
      .recordSubmission
      .recordSubmission
      .recordCompletion(10)

    stats.pendingCount shouldBe 2
  }
}
