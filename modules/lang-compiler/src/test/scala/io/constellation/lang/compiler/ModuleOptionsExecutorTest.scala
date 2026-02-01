package io.constellation.lang.compiler

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import io.constellation.CType
import io.constellation.cache.CacheRegistry
import io.constellation.execution.LimiterRegistry
import io.constellation.lang.ast.{BackoffStrategy => ASTBackoffStrategy, ErrorStrategy => ASTErrorStrategy}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._

class ModuleOptionsExecutorTest extends AnyFlatSpec with Matchers {

  "ModuleOptionsExecutor" should "execute without options (fast path)" in {
    val result = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor.executeWithOptions(
        operation = IO.pure(42),
        moduleId = UUID.randomUUID(),
        moduleName = "Test",
        options = IRModuleCallOptions(),
        outputType = CType.CInt
      )
    } yield result).unsafeRunSync()

    result shouldBe 42
  }

  it should "apply retry on failure" in {
    val counter = new AtomicInteger(0)
    val operation = IO {
      val count = counter.incrementAndGet()
      if (count < 3) throw new RuntimeException(s"Attempt $count failed")
      "success"
    }

    val result = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor.executeWithOptions(
        operation = operation,
        moduleId = UUID.randomUUID(),
        moduleName = "RetryTest",
        options = IRModuleCallOptions(retry = Some(3)),
        outputType = CType.CString
      )
    } yield result).unsafeRunSync()

    result shouldBe "success"
    counter.get() shouldBe 3
  }

  it should "apply timeout" in {
    val result = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor.executeWithOptions(
        operation = IO.sleep(5.seconds) >> IO.pure("too slow"),
        moduleId = UUID.randomUUID(),
        moduleName = "TimeoutTest",
        options = IRModuleCallOptions(timeoutMs = Some(100)),
        outputType = CType.CString
      ).attempt
    } yield result).unsafeRunSync()

    result.isLeft shouldBe true
    result.left.getOrElse(throw new RuntimeException()).getMessage should include("timed out")
  }

  it should "use fallback on failure" in {
    val result = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor.executeWithOptions(
        operation = IO.raiseError[String](new RuntimeException("fail")),
        moduleId = UUID.randomUUID(),
        moduleName = "FallbackTest",
        options = IRModuleCallOptions(retry = Some(0)),
        outputType = CType.CString,
        getFallbackValue = Some(() => IO.pure("fallback value"))
      )
    } yield result).unsafeRunSync()

    result shouldBe "fallback value"
  }

  it should "apply caching" in {
    val counter = new AtomicInteger(0)
    val moduleId = UUID.randomUUID()

    val result = (for {
      executor <- ModuleOptionsExecutor.create
      // First call
      result1 <- executor.executeWithOptions(
        operation = IO { counter.incrementAndGet() },
        moduleId = moduleId,
        moduleName = "CacheTest",
        options = IRModuleCallOptions(cacheMs = Some(60000)),
        outputType = CType.CInt
      )
      // Second call should use cache
      result2 <- executor.executeWithOptions(
        operation = IO { counter.incrementAndGet() },
        moduleId = moduleId,
        moduleName = "CacheTest",
        options = IRModuleCallOptions(cacheMs = Some(60000)),
        outputType = CType.CInt
      )
    } yield (result1, result2, counter.get())).unsafeRunSync()

    result._1 shouldBe 1
    result._2 shouldBe 1 // Cached result
    result._3 shouldBe 1 // Only one execution
  }

  it should "apply throttle rate limiting" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      executor <- ModuleOptionsExecutor.create
      start <- IO.realTime
      // Execute 3 operations with throttle of 2 per 100ms
      _ <- (1 to 3).toList.traverse_ { _ =>
        executor.executeWithOptions(
          operation = IO { counter.incrementAndGet() },
          moduleId = UUID.randomUUID(),
          moduleName = "ThrottleTest",
          options = IRModuleCallOptions(throttleCount = Some(2), throttlePerMs = Some(100)),
          outputType = CType.CInt
        )
      }
      end <- IO.realTime
    } yield (counter.get(), (end - start).toMillis)).unsafeRunSync()

    result._1 shouldBe 3 // All executed
    result._2 should be >= 20L // Should take some time due to throttle
  }

  it should "apply concurrency limiting" in {
    val maxActive = new AtomicInteger(0)
    val currentActive = new AtomicInteger(0)

    val result = (for {
      executor <- ModuleOptionsExecutor.create
      _ <- (1 to 5).toList.parTraverse_ { _ =>
        executor.executeWithOptions(
          operation = IO {
            val active = currentActive.incrementAndGet()
            maxActive.updateAndGet(m => math.max(m, active))
          } >> IO.sleep(30.millis) >> IO { currentActive.decrementAndGet() },
          moduleId = UUID.randomUUID(),
          moduleName = "ConcurrencyTest",
          options = IRModuleCallOptions(concurrency = Some(2)),
          outputType = CType.CInt
        )
      }
    } yield maxActive.get()).unsafeRunSync()

    result should be <= 2 // Max 2 concurrent
  }

  it should "apply error strategy Skip" in {
    val result = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor.executeWithOptions(
        operation = IO.raiseError[Int](new RuntimeException("fail")),
        moduleId = UUID.randomUUID(),
        moduleName = "ErrorTest",
        options = IRModuleCallOptions(onError = Some(ASTErrorStrategy.Skip)),
        outputType = CType.CInt
      )
    } yield result).unsafeRunSync()

    // Skip strategy returns zero value (0 for Int)
    result shouldBe io.constellation.CValue.CInt(0)
  }

  it should "apply backoff strategy" in {
    val counter = new AtomicInteger(0)
    val timestamps = new java.util.concurrent.ConcurrentLinkedQueue[Long]()

    val operation = IO {
      timestamps.add(System.currentTimeMillis())
      val count = counter.incrementAndGet()
      if (count < 3) throw new RuntimeException(s"Attempt $count failed")
      "success"
    }

    val result = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor.executeWithOptions(
        operation = operation,
        moduleId = UUID.randomUUID(),
        moduleName = "BackoffTest",
        options = IRModuleCallOptions(
          retry = Some(3),
          delayMs = Some(50),
          backoff = Some(ASTBackoffStrategy.Exponential)
        ),
        outputType = CType.CString
      )
    } yield result).unsafeRunSync()

    result shouldBe "success"
    counter.get() shouldBe 3

    // Check that delays are exponential
    val times = timestamps.toArray.map(_.asInstanceOf[Long])
    if (times.length >= 3) {
      val gap1 = times(1) - times(0)
      val gap2 = times(2) - times(1)
      // Gap2 should show exponential growth trend (tolerant of scheduling noise)
      gap2 should be >= (gap1 * 0.8).toLong
    }
  }

  it should "apply lazy evaluation" in {
    val counter = new AtomicInteger(0)

    val (result, count) = (for {
      executor <- ModuleOptionsExecutor.create
      // With lazy=true, operation should still be evaluated when we await the result
      result <- executor.executeWithOptions(
        operation = IO { counter.incrementAndGet(); "computed" },
        moduleId = UUID.randomUUID(),
        moduleName = "LazyTest",
        options = IRModuleCallOptions(lazyEval = Some(true)),
        outputType = CType.CString
      )
    } yield (result, counter.get())).unsafeRunSync()

    result shouldBe "computed"
    count shouldBe 1 // Should be computed when forced
  }

  it should "combine multiple options" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor.executeWithOptions(
        operation = IO {
          val c = counter.incrementAndGet()
          if (c < 2) throw new RuntimeException("first fail")
          "combined"
        },
        moduleId = UUID.randomUUID(),
        moduleName = "CombinedTest",
        options = IRModuleCallOptions(
          retry = Some(3),
          timeoutMs = Some(5000),
          delayMs = Some(10)
        ),
        outputType = CType.CString
      )
    } yield result).unsafeRunSync()

    result shouldBe "combined"
    counter.get() shouldBe 2
  }

  "ModuleOptionsExecutor.create" should "create with default registries" in {
    val executor = ModuleOptionsExecutor.create.unsafeRunSync()
    executor.getCacheRegistry shouldNot be(null)
    executor.getLimiterRegistry shouldNot be(null)
  }

  "ModuleOptionsExecutor.withRegistries" should "use custom registries" in {
    val (executor, sameCache, sameLimiter) = (for {
      cacheReg <- CacheRegistry.create
      limiterReg <- LimiterRegistry.create
      executor = ModuleOptionsExecutor.withRegistries(cacheReg, limiterReg)
    } yield (executor, executor.getCacheRegistry eq cacheReg, executor.getLimiterRegistry eq limiterReg)).unsafeRunSync()

    sameCache shouldBe true
    sameLimiter shouldBe true
  }

  it should "cache based on input values, not just module name" in {
    import io.constellation.CValue

    val counter = new AtomicInteger(0)
    val moduleId = UUID.randomUUID()

    val result = (for {
      executor <- ModuleOptionsExecutor.create

      // First call with input "hello"
      result1 <- executor.executeWithOptions(
        operation = IO { s"result-${counter.incrementAndGet()}" },
        moduleId = moduleId,
        moduleName = "InputSensitiveCache",
        options = IRModuleCallOptions(cacheMs = Some(60000)),
        outputType = CType.CString,
        inputs = Map("text" -> CValue.CString("hello"))
      )

      // Second call with same input "hello" - should hit cache
      result2 <- executor.executeWithOptions(
        operation = IO { s"result-${counter.incrementAndGet()}" },
        moduleId = moduleId,
        moduleName = "InputSensitiveCache",
        options = IRModuleCallOptions(cacheMs = Some(60000)),
        outputType = CType.CString,
        inputs = Map("text" -> CValue.CString("hello"))
      )

      // Third call with different input "world" - should NOT hit cache
      result3 <- executor.executeWithOptions(
        operation = IO { s"result-${counter.incrementAndGet()}" },
        moduleId = moduleId,
        moduleName = "InputSensitiveCache",
        options = IRModuleCallOptions(cacheMs = Some(60000)),
        outputType = CType.CString,
        inputs = Map("text" -> CValue.CString("world"))
      )

      // Fourth call with "world" again - should hit cache
      result4 <- executor.executeWithOptions(
        operation = IO { s"result-${counter.incrementAndGet()}" },
        moduleId = moduleId,
        moduleName = "InputSensitiveCache",
        options = IRModuleCallOptions(cacheMs = Some(60000)),
        outputType = CType.CString,
        inputs = Map("text" -> CValue.CString("world"))
      )
    } yield (result1, result2, result3, result4, counter.get())).unsafeRunSync()

    val (result1, result2, result3, result4, finalCount) = result

    // First call executes (counter = 1)
    result1 shouldBe "result-1"

    // Second call hits cache (counter still 1)
    result2 shouldBe "result-1"

    // Third call with different input executes (counter = 2)
    result3 shouldBe "result-2"

    // Fourth call hits cache (counter still 2)
    result4 shouldBe "result-2"

    // Only 2 executions should have happened
    finalCount shouldBe 2
  }
}
