package io.constellation.lang.integration

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.CType
import io.constellation.lang.LangCompiler
import io.constellation.lang.compiler.{
  CompilationOutput,
  DagCompiler,
  IRModuleCallOptions,
  ModuleOptionsExecutor
}
import io.constellation.lang.semantic.{FunctionSignature, SemanticType}
import io.constellation.lang.RetrySupport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration.*

/** End-to-end integration tests for module call options. These tests verify that options specified
  * in constellation-lang source code are correctly parsed, compiled, and executed at runtime.
  */
class OptionsRuntimeIntegrationTest extends AnyFlatSpec with Matchers with RetrySupport {

  // Create a compiler with a test function registered
  private def compilerWithFunction(name: String): LangCompiler =
    LangCompiler.builder
      .withFunction(
        FunctionSignature(
          name = name,
          params = List("x" -> SemanticType.SInt),
          returns = SemanticType.SInt,
          moduleName = name
        )
      )
      .build

  /** Compile source and extract IRModuleCallOptions from the underlying DagCompileOutput. Uses
    * compileToIR + DagCompiler to get the raw IR-level options needed by ModuleOptionsExecutor.
    */
  private def compileAndGetOptions(
      source: String,
      moduleName: String
  ): Either[Any, IRModuleCallOptions] = {
    val compiler = compilerWithFunction(moduleName)
    for {
      ir     <- compiler.compileToIR(source, "test-dag")
      result <- DagCompiler.compile(ir, "test-dag", Map.empty).left.map(e => List(e))
    } yield result.moduleOptions.values.headOption.getOrElse(IRModuleCallOptions())
  }

  // ============================================================================
  // E2E Tests: Source -> Compile -> Execute with Options
  // ============================================================================

  "E2E with retry option" should "retry failed operations" in {
    val source = """
      in x: Int
      result = TestModule(x) with retry: 3
      out result
    """

    val compiled = compileAndGetOptions(source, "TestModule")
    compiled.isRight shouldBe true

    val options = compiled.toOption.get
    options.retry shouldBe Some(3)

    // Simulate execution with retry
    val counter = new AtomicInteger(0)
    val operation = IO {
      val count = counter.incrementAndGet()
      if count < 3 then throw new RuntimeException(s"Attempt $count failed")
      "success"
    }

    val result = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor.executeWithOptions(
        operation = operation,
        moduleId = UUID.randomUUID(),
        moduleName = "TestModule",
        options = options,
        outputType = CType.CString
      )
    } yield result).unsafeRunSync()

    result shouldBe "success"
    counter.get() shouldBe 3
  }

  "E2E with timeout option" should "timeout slow operations" in {
    val source = """
      in x: Int
      result = TestModule(x) with timeout: 100ms
      out result
    """

    val compiled = compileAndGetOptions(source, "TestModule")
    compiled.isRight shouldBe true

    val options = compiled.toOption.get
    options.timeoutMs shouldBe Some(100L)

    // Simulate slow execution
    val result = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor
        .executeWithOptions(
          operation = IO.sleep(5.seconds) >> IO.pure("too slow"),
          moduleId = UUID.randomUUID(),
          moduleName = "TestModule",
          options = options,
          outputType = CType.CString
        )
        .attempt
    } yield result).unsafeRunSync()

    result.isLeft shouldBe true
    result.left.getOrElse(throw new RuntimeException()).getMessage should include("timed out")
  }

  "E2E with cache option" should "cache results" in {
    val source = """
      in x: Int
      result = TestModule(x) with cache: 5min
      out result
    """

    val compiled = compileAndGetOptions(source, "TestModule")
    compiled.isRight shouldBe true

    val options = compiled.toOption.get
    options.cacheMs shouldBe Some(300000L) // 5min in ms

    // Simulate cacheable execution
    val counter  = new AtomicInteger(0)
    val moduleId = UUID.randomUUID()

    val result = (for {
      executor <- ModuleOptionsExecutor.create
      // First call
      result1 <- executor.executeWithOptions(
        operation = IO(counter.incrementAndGet()),
        moduleId = moduleId,
        moduleName = "TestModule",
        options = options,
        outputType = CType.CInt
      )
      // Second call should use cache
      result2 <- executor.executeWithOptions(
        operation = IO(counter.incrementAndGet()),
        moduleId = moduleId,
        moduleName = "TestModule",
        options = options,
        outputType = CType.CInt
      )
    } yield (result1, result2, counter.get())).unsafeRunSync()

    result._1 shouldBe 1
    result._2 shouldBe 1 // Cached
    result._3 shouldBe 1 // Only executed once
  }

  "E2E with delay and backoff" should "apply backoff delays" taggedAs Retryable in {
    val source = """
      in x: Int
      result = TestModule(x) with retry: 3, delay: 50ms, backoff: exponential
      out result
    """

    val compiled = compileAndGetOptions(source, "TestModule")
    compiled.isRight shouldBe true

    val options = compiled.toOption.get
    options.retry shouldBe Some(3)
    options.delayMs shouldBe Some(50L)
    options.backoff shouldBe Some(io.constellation.lang.ast.BackoffStrategy.Exponential)

    // Simulate failing operation that succeeds on attempt 3
    val counter    = new AtomicInteger(0)
    val timestamps = new java.util.concurrent.ConcurrentLinkedQueue[Long]()

    val operation = IO {
      timestamps.add(System.currentTimeMillis())
      val count = counter.incrementAndGet()
      if count < 3 then throw new RuntimeException(s"Attempt $count failed")
      "success"
    }

    val result = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor.executeWithOptions(
        operation = operation,
        moduleId = UUID.randomUUID(),
        moduleName = "TestModule",
        options = options,
        outputType = CType.CString
      )
    } yield result).unsafeRunSync()

    result shouldBe "success"
    counter.get() shouldBe 3

    // Verify exponential backoff was applied
    val times = timestamps.toArray.map(_.asInstanceOf[Long])
    if times.length >= 3 then {
      val gap1 = times(1) - times(0)
      val gap2 = times(2) - times(1)
      // Gap2 should be >= gap1 for exponential backoff
      gap2 should be >= gap1
    }
  }

  "E2E with throttle option" should "rate limit operations" taggedAs Retryable in {
    val source = """
      in x: Int
      result = TestModule(x) with throttle: 2/100ms
      out result
    """

    val compiled = compileAndGetOptions(source, "TestModule")
    compiled.isRight shouldBe true

    val options = compiled.toOption.get
    options.throttleCount shouldBe Some(2)
    options.throttlePerMs shouldBe Some(100L)

    val counter = new AtomicInteger(0)

    val result = (for {
      executor <- ModuleOptionsExecutor.create
      start    <- IO.realTime
      // Execute 4 operations with throttle of 2 per 100ms
      _ <- (1 to 4).toList.foldLeft(IO.unit) { (prev, _) =>
        prev >> executor
          .executeWithOptions(
            operation = IO(counter.incrementAndGet()),
            moduleId = UUID.randomUUID(),
            moduleName = "TestModule",
            options = options,
            outputType = CType.CInt
          )
          .void
      }
      end <- IO.realTime
    } yield (counter.get(), (end - start).toMillis)).unsafeRunSync()

    result._1 shouldBe 4       // All executed
    result._2 should be >= 50L // Should take some time due to throttle
  }

  "E2E with concurrency option" should "limit concurrent executions" in {
    val source = """
      in x: Int
      result = TestModule(x) with concurrency: 2
      out result
    """

    val compiled = compileAndGetOptions(source, "TestModule")
    compiled.isRight shouldBe true

    val options = compiled.toOption.get
    options.concurrency shouldBe Some(2)

    val maxActive     = new AtomicInteger(0)
    val currentActive = new AtomicInteger(0)

    import cats.implicits.*
    val result = (for {
      executor <- ModuleOptionsExecutor.create
      _ <- (1 to 5).toList.parTraverse_ { _ =>
        executor.executeWithOptions(
          operation = IO {
            val active = currentActive.incrementAndGet()
            maxActive.updateAndGet(m => math.max(m, active))
          } >> IO.sleep(30.millis) >> IO(currentActive.decrementAndGet()),
          moduleId = UUID.randomUUID(),
          moduleName = "TestModule",
          options = options,
          outputType = CType.CInt
        )
      }
    } yield maxActive.get()).unsafeRunSync()

    result shouldBe 2 // Max 2 concurrent
  }

  "E2E with on_error option" should "skip errors" in {
    val source = """
      in x: Int
      result = TestModule(x) with on_error: skip
      out result
    """

    val compiled = compileAndGetOptions(source, "TestModule")
    compiled.isRight shouldBe true

    val options = compiled.toOption.get
    options.onError shouldBe Some(io.constellation.lang.ast.ErrorStrategy.Skip)

    val result = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor.executeWithOptions(
        operation = IO.raiseError[Int](new RuntimeException("fail")),
        moduleId = UUID.randomUUID(),
        moduleName = "TestModule",
        options = options,
        outputType = CType.CInt
      )
    } yield result).unsafeRunSync()

    // Skip strategy returns zero value (CInt(0) wrapper)
    result shouldBe io.constellation.CValue.CInt(0)
  }

  "E2E with priority option" should "set execution priority" in {
    val source = """
      in x: Int
      result = TestModule(x) with priority: high
      out result
    """

    val compiled = compileAndGetOptions(source, "TestModule")
    compiled.isRight shouldBe true

    val options = compiled.toOption.get
    // high priority is converted to 80 by IRGenerator
    options.priority shouldBe defined
    options.priority.get shouldBe 80
  }

  "E2E with lazy option" should "defer evaluation" in {
    val source = """
      in x: Int
      result = TestModule(x) with lazy: true
      out result
    """

    val compiled = compileAndGetOptions(source, "TestModule")
    compiled.isRight shouldBe true

    val options = compiled.toOption.get
    options.lazyEval shouldBe Some(true)

    val counter = new AtomicInteger(0)

    val (result, count) = (for {
      executor <- ModuleOptionsExecutor.create
      result <- executor.executeWithOptions(
        operation = IO { counter.incrementAndGet(); "computed" },
        moduleId = UUID.randomUUID(),
        moduleName = "TestModule",
        options = options,
        outputType = CType.CString
      )
    } yield (result, counter.get())).unsafeRunSync()

    result shouldBe "computed"
    count shouldBe 1
  }

  // ============================================================================
  // Combined Options E2E Tests
  // ============================================================================

  "E2E with all resilience options" should "combine retry, timeout, delay, backoff, and fallback" in {
    val source = """
      in x: Int
      in fallbackValue: Int
      result = TestModule(x) with
          retry: 2,
          timeout: 1s,
          delay: 50ms,
          backoff: exponential,
          fallback: fallbackValue
      out result
    """

    val compiler = LangCompiler.builder
      .withFunction(
        FunctionSignature(
          name = "TestModule",
          params = List("x" -> SemanticType.SInt),
          returns = SemanticType.SInt,
          moduleName = "TestModule"
        )
      )
      .build

    val ir = compiler.compileToIR(source, "test-dag")
    ir.isRight shouldBe true

    val dagResult = DagCompiler.compile(ir.toOption.get, "test-dag", Map.empty)
    dagResult.isRight shouldBe true

    val options =
      dagResult.toOption.get.moduleOptions.values.headOption.getOrElse(IRModuleCallOptions())

    options.retry shouldBe Some(2)
    options.timeoutMs shouldBe Some(1000L)
    options.delayMs shouldBe Some(50L)
    options.backoff shouldBe Some(io.constellation.lang.ast.BackoffStrategy.Exponential)
    options.fallback shouldBe defined // Should be a UUID reference
  }

  "E2E with rate control options" should "combine throttle and concurrency" in {
    val source = """
      in x: Int
      result = TestModule(x) with throttle: 10/1s, concurrency: 3
      out result
    """

    val compiled = compileAndGetOptions(source, "TestModule")
    compiled.isRight shouldBe true

    val options = compiled.toOption.get
    options.throttleCount shouldBe Some(10)
    options.throttlePerMs shouldBe Some(1000L)
    options.concurrency shouldBe Some(3)
  }

  "E2E with cache and cache_backend" should "configure caching" in {
    val source = """
      in x: Int
      result = TestModule(x) with cache: 10min, cache_backend: "redis"
      out result
    """

    val compiled = compileAndGetOptions(source, "TestModule")
    compiled.isRight shouldBe true

    val options = compiled.toOption.get
    options.cacheMs shouldBe Some(600000L) // 10min in ms
    options.cacheBackend shouldBe Some("redis")
  }
}
