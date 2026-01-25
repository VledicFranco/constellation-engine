package io.constellation.examples.app.modules

import io.constellation.*
import cats.effect.IO

import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicInteger

/** Resilience demonstration modules for showcasing module call options.
  *
  * These modules simulate real-world scenarios like slow API calls, unreliable services, and
  * expensive computations. They are designed to demonstrate the effectiveness of options like
  * retry, timeout, cache, throttle, etc.
  *
  * All modules use simple String inputs/outputs for compatibility with the lang compiler's
  * SemanticType system.
  */
object ResilienceModules {

  // ========== Slow Operations (for cache/timeout demos) ==========

  /** Simulates a slow database query (500ms delay) */
  case class SlowQueryInput(query: String)
  case class SlowQueryOutput(result: String)

  val slowQuery: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "SlowQuery",
      description =
        "Simulates a slow database query with 500ms latency. Use with `cache` to see dramatic speedup on repeated calls.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("resilience", "slow", "cache-demo")
    .implementation[SlowQueryInput, SlowQueryOutput] { input =>
      IO.sleep(500.millis) >> IO {
        SlowQueryOutput(s"Result for: ${input.query}")
      }
    }
    .build

  /** Simulates a slow API call (1 second delay) */
  case class SlowApiInput(endpoint: String)
  case class SlowApiOutput(data: String)

  val slowApiCall: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "SlowApiCall",
      description =
        "Simulates a slow external API call with 1 second latency. Perfect for demonstrating timeout and cache options.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("resilience", "slow", "cache-demo", "timeout-demo")
    .implementation[SlowApiInput, SlowApiOutput] { input =>
      IO.sleep(1.second) >> IO {
        SlowApiOutput(s"API response from: ${input.endpoint}")
      }
    }
    .build

  /** Simulates an expensive computation (2 second delay) */
  case class ExpensiveComputeInput(data: String)
  case class ExpensiveComputeOutput(result: String)

  val expensiveCompute: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "ExpensiveCompute",
      description =
        "Simulates an expensive computation with 2 second processing time. Ideal for cache and lazy evaluation demos.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("resilience", "slow", "cache-demo", "lazy-demo")
    .implementation[ExpensiveComputeInput, ExpensiveComputeOutput] { input =>
      IO.sleep(2.seconds) >> IO {
        ExpensiveComputeOutput(s"Computed: ${input.data.toUpperCase}")
      }
    }
    .build

  // ========== Unreliable Operations (for retry/fallback demos) ==========

  // Track call counts for flaky service
  private val flakyCallCount = new AtomicInteger(0)

  /** Simulates an unreliable service that fails 2 out of 3 times */
  case class FlakyServiceInput(request: String)
  case class FlakyServiceOutput(response: String)

  val flakyService: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "FlakyService",
      description =
        "Simulates an unreliable service that fails 2 out of 3 attempts. Use with `retry: 3` to see successful recovery.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("resilience", "unreliable", "retry-demo")
    .implementation[FlakyServiceInput, FlakyServiceOutput] { input =>
      IO {
        val attempt = flakyCallCount.incrementAndGet()
        if (attempt % 3 != 0) {
          throw new RuntimeException(s"Service temporarily unavailable (attempt $attempt)")
        }
        FlakyServiceOutput(s"Success for: ${input.request}")
      }
    }
    .build

  // Reset flaky service counter (for testing)
  def resetFlakyCounter(): Unit = flakyCallCount.set(0)

  /** Simulates a service that times out occasionally */
  case class TimeoutProneInput(request: String)
  case class TimeoutProneOutput(response: String)

  private val timeoutCallCount = new AtomicInteger(0)

  val timeoutProneService: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "TimeoutProneService",
      description =
        "Simulates a service with variable latency (100ms-3s). First call is slow (3s), subsequent calls are fast (100ms). Use with timeout option.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("resilience", "timeout-demo", "retry-demo")
    .implementation[TimeoutProneInput, TimeoutProneOutput] { input =>
      val count = timeoutCallCount.incrementAndGet()
      // First call is slow, subsequent calls are fast
      val delay = if (count == 1) 3.seconds else 100.millis
      IO.sleep(delay) >> IO {
        TimeoutProneOutput(s"Response for: ${input.request}")
      }
    }
    .build

  def resetTimeoutCounter(): Unit = timeoutCallCount.set(0)

  // ========== Rate-Limited Operations (for throttle/concurrency demos) ==========

  /** Simulates a rate-limited API endpoint */
  case class RateLimitedInput(request: String)
  case class RateLimitedOutput(response: String)

  val rateLimitedApi: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "RateLimitedApi",
      description =
        "Simulates an API with rate limits. Use with `throttle` to stay within limits. Each call takes 50ms.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("resilience", "throttle-demo")
    .implementation[RateLimitedInput, RateLimitedOutput] { input =>
      IO.sleep(50.millis) >> IO {
        RateLimitedOutput(s"Rate-limited response for: ${input.request}")
      }
    }
    .build

  /** Simulates a resource-intensive operation that shouldn't run too concurrently */
  case class ResourceIntensiveInput(task: String)
  case class ResourceIntensiveOutput(result: String)

  val resourceIntensiveTask: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "ResourceIntensiveTask",
      description =
        "Simulates a resource-intensive task (200ms). Use with `concurrency` to limit parallel executions.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("resilience", "concurrency-demo")
    .implementation[ResourceIntensiveInput, ResourceIntensiveOutput] { input =>
      IO.sleep(200.millis) >> IO {
        ResourceIntensiveOutput(s"Completed: ${input.task}")
      }
    }
    .build

  // ========== Priority Operations (for priority/lazy demos) ==========

  /** Simulates a quick operation for priority demos */
  case class QuickCheckInput(data: String)
  case class QuickCheckOutput(result: String)

  val quickCheck: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "QuickCheck",
      description =
        "Fast validation (10ms). Returns 'valid' or 'needs-deep-analysis'. Use with lazy evaluation.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("resilience", "lazy-demo", "priority-demo")
    .implementation[QuickCheckInput, QuickCheckOutput] { input =>
      IO.sleep(10.millis) >> IO {
        // Simulate: data with "complex" needs deep analysis
        val needsDeep = input.data.toLowerCase.contains("complex")
        val result = if (needsDeep) "needs-deep-analysis" else "valid"
        QuickCheckOutput(result)
      }
    }
    .build

  /** Simulates a deep analysis operation (expensive, 1.5s) */
  case class DeepAnalysisInput(data: String)
  case class DeepAnalysisOutput(analysis: String)

  val deepAnalysis: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "DeepAnalysis",
      description =
        "Expensive deep analysis (1.5s). Should only run when needed. Perfect for lazy evaluation demo.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("resilience", "slow", "lazy-demo")
    .implementation[DeepAnalysisInput, DeepAnalysisOutput] { input =>
      IO.sleep(1500.millis) >> IO {
        DeepAnalysisOutput(s"Deep analysis of: ${input.data}")
      }
    }
    .build

  // ========== Error Handling Demos ==========

  /** Simulates a service that always fails (for on_error demos) */
  case class AlwaysFailsInput(request: String)
  case class AlwaysFailsOutput(response: String)

  val alwaysFailsService: Module.Uninitialized = ModuleBuilder
    .metadata(
      name = "AlwaysFailsService",
      description =
        "A service that always fails. Use with `on_error` to see different error handling strategies.",
      majorVersion = 1,
      minorVersion = 0
    )
    .tags("resilience", "error-demo")
    .implementation[AlwaysFailsInput, AlwaysFailsOutput] { input =>
      IO.raiseError(new RuntimeException(s"Service failed for: ${input.request}"))
    }
    .build

  // ========== All Modules ==========

  /** All resilience demonstration modules */
  val all: List[Module.Uninitialized] = List(
    slowQuery,
    slowApiCall,
    expensiveCompute,
    flakyService,
    timeoutProneService,
    rateLimitedApi,
    resourceIntensiveTask,
    quickCheck,
    deepAnalysis,
    alwaysFailsService
  )
}
