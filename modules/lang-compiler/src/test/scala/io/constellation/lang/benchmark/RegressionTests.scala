package io.constellation.lang.benchmark

import io.constellation.lang._
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.semantic.{FunctionRegistry, TypeChecker}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

/** Performance regression test suite
  *
  * Run with: sbt "langCompiler/testOnly *RegressionTests"
  *
  * This suite enforces performance baselines to prevent regressions.
  * Tests will fail if performance degrades beyond the configured tolerance.
  *
  * To update baselines after performance improvements:
  * 1. Run benchmarks to get new times
  * 2. Update the baseline values below
  * 3. Add a comment explaining why the baseline changed
  */
class RegressionTests extends AnyFlatSpec with Matchers {

  // Benchmark configuration
  val WarmupIterations  = 5
  val MeasureIterations = 20

  // Baselines: operation_name -> (max_allowed_ms)
  // These are absolute maximums, not targets. Actual performance should be well below.
  // Tolerance is built into these values (typically 20-50% above expected).
  val baselines: Map[String, Double] = Map(
    // Parsing baselines (generous to account for JVM variance)
    "parse_small"  -> 50.0,   // Expected: <5ms, allowing up to 50ms
    "parse_medium" -> 100.0,  // Expected: <30ms, allowing up to 100ms
    "parse_large"  -> 300.0,  // Expected: <100ms, allowing up to 300ms

    // Full pipeline baselines
    "pipeline_small"  -> 100.0,  // Expected: <30ms
    "pipeline_medium" -> 200.0,  // Expected: <80ms
    "pipeline_large"  -> 500.0,  // Expected: <200ms

    // Cache performance baselines
    "cache_warm_small"  -> 10.0,  // Cache hits should be very fast
    "cache_warm_medium" -> 10.0,
    "cache_warm_large"  -> 15.0,

    // Type checking baselines
    "typecheck_small"  -> 50.0,
    "typecheck_medium" -> 100.0,
    "typecheck_large"  -> 300.0
  )

  // Minimum required cache speedup
  val MinCacheSpeedup = 5.0

  // Collect all results for summary
  private val allResults = ListBuffer[BenchmarkResult]()

  /** Check if result exceeds baseline and fail with clear message */
  private def checkRegression(result: BenchmarkResult): Unit = {
    baselines.get(result.name).foreach { maxAllowed =>
      withClue(
        s"\n" +
          s"PERFORMANCE REGRESSION DETECTED!\n" +
          s"Operation: ${result.name}\n" +
          s"Measured:  ${result.avgMs}ms (±${result.stdDevMs}ms)\n" +
          s"Baseline:  ${maxAllowed}ms\n" +
          s"Exceeded by: ${((result.avgMs - maxAllowed) / maxAllowed * 100).formatted("%.1f")}%\n"
      ) {
        result.avgMs should be <= maxAllowed
      }
    }
    allResults += result
  }

  // -----------------------------------------------------------------
  // Parse Regression Tests
  // -----------------------------------------------------------------

  "Parse performance" should "not regress for small programs" in {
    val source = TestFixtures.smallProgram

    val result = BenchmarkHarness.measureWithWarmup(
      name = "parse_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "parse",
      inputSize = "small"
    ) {
      ConstellationParser.parse(source)
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  it should "not regress for medium programs" in {
    val source = TestFixtures.mediumProgram

    val result = BenchmarkHarness.measureWithWarmup(
      name = "parse_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "parse",
      inputSize = "medium"
    ) {
      ConstellationParser.parse(source)
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  it should "not regress for large programs" in {
    val source = TestFixtures.largeProgram

    val result = BenchmarkHarness.measureWithWarmup(
      name = "parse_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "parse",
      inputSize = "large"
    ) {
      ConstellationParser.parse(source)
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  // -----------------------------------------------------------------
  // TypeCheck Regression Tests
  // -----------------------------------------------------------------

  "TypeCheck performance" should "not regress for small programs" in {
    val source = TestFixtures.smallProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val registry = FunctionRegistry.empty

    val result = BenchmarkHarness.measureWithWarmup(
      name = "typecheck_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "typecheck",
      inputSize = "small"
    ) {
      TypeChecker.check(parsed, registry)
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  it should "not regress for medium programs" in {
    val source = TestFixtures.mediumProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val registry = FunctionRegistry.empty

    val result = BenchmarkHarness.measureWithWarmup(
      name = "typecheck_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "typecheck",
      inputSize = "medium"
    ) {
      TypeChecker.check(parsed, registry)
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  it should "not regress for large programs" in {
    val source = TestFixtures.largeProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val registry = FunctionRegistry.empty

    val result = BenchmarkHarness.measureWithWarmup(
      name = "typecheck_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "typecheck",
      inputSize = "large"
    ) {
      TypeChecker.check(parsed, registry)
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  // -----------------------------------------------------------------
  // Full Pipeline Regression Tests
  // -----------------------------------------------------------------

  "Full pipeline performance" should "not regress for small programs" in {
    val source = TestFixtures.smallProgram
    val compiler = LangCompiler.empty

    val result = BenchmarkHarness.measureWithWarmup(
      name = "pipeline_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "pipeline",
      inputSize = "small"
    ) {
      compiler.compile(source, "regression-test")
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  it should "not regress for medium programs" in {
    val source = TestFixtures.mediumProgram
    val compiler = LangCompiler.empty

    val result = BenchmarkHarness.measureWithWarmup(
      name = "pipeline_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "pipeline",
      inputSize = "medium"
    ) {
      compiler.compile(source, "regression-test")
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  it should "not regress for large programs" in {
    val source = TestFixtures.largeProgram
    val compiler = LangCompiler.empty

    val result = BenchmarkHarness.measureWithWarmup(
      name = "pipeline_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "pipeline",
      inputSize = "large"
    ) {
      compiler.compile(source, "regression-test")
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  // -----------------------------------------------------------------
  // Cache Performance Regression Tests
  // -----------------------------------------------------------------

  "Cache performance" should "not regress for warm cache hits (small)" in {
    val source = TestFixtures.smallProgram
    val compiler = LangCompiler.builder.withCaching().build

    // Warm the cache
    compiler.compile(source, "cache-test")

    val result = BenchmarkHarness.measureWithWarmup(
      name = "cache_warm_small",
      warmupIterations = 0, // Already warmed
      measureIterations = MeasureIterations,
      phase = "cache",
      inputSize = "small"
    ) {
      compiler.compile(source, "cache-test")
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  it should "not regress for warm cache hits (medium)" in {
    val source = TestFixtures.mediumProgram
    val compiler = LangCompiler.builder.withCaching().build

    // Warm the cache
    compiler.compile(source, "cache-test")

    val result = BenchmarkHarness.measureWithWarmup(
      name = "cache_warm_medium",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "cache",
      inputSize = "medium"
    ) {
      compiler.compile(source, "cache-test")
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  it should "not regress for warm cache hits (large)" in {
    val source = TestFixtures.largeProgram
    val compiler = LangCompiler.builder.withCaching().build

    // Warm the cache
    compiler.compile(source, "cache-test")

    val result = BenchmarkHarness.measureWithWarmup(
      name = "cache_warm_large",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "cache",
      inputSize = "large"
    ) {
      compiler.compile(source, "cache-test")
    }

    println(result.toConsoleString)
    checkRegression(result)
  }

  it should "provide at least 5x speedup" in {
    val source = TestFixtures.mediumProgram

    // Measure cold compilation
    val coldResult = BenchmarkHarness.measureWithWarmup(
      name = "cache_cold_speedup",
      warmupIterations = WarmupIterations,
      measureIterations = 10,
      phase = "cache_speedup",
      inputSize = "cold"
    ) {
      val freshCompiler = LangCompiler.empty
      freshCompiler.compile(source, s"cold-${System.nanoTime()}")
    }

    // Create caching compiler and warm it
    val cachingCompiler = LangCompiler.builder.withCaching().build
    cachingCompiler.compile(source, "speedup-test")

    // Measure warm compilation
    val warmResult = BenchmarkHarness.measureWithWarmup(
      name = "cache_warm_speedup",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "cache_speedup",
      inputSize = "warm"
    ) {
      cachingCompiler.compile(source, "speedup-test")
    }

    val speedup = coldResult.avgMs / warmResult.avgMs

    println(f"\nCache Speedup Analysis:")
    println(f"  Cold compile: ${coldResult.avgMs}%.2fms")
    println(f"  Warm compile: ${warmResult.avgMs}%.2fms")
    println(f"  Speedup: ${speedup}%.1fx")

    allResults += coldResult
    allResults += warmResult

    withClue(
      s"\nCache speedup regression!\n" +
        s"Measured speedup: ${speedup}x\n" +
        s"Required minimum: ${MinCacheSpeedup}x\n"
    ) {
      speedup should be >= MinCacheSpeedup
    }
  }

  // -----------------------------------------------------------------
  // Summary Report
  // -----------------------------------------------------------------

  "Regression test summary" should "report all results" in {
    println("\n" + "=" * 80)
    println("REGRESSION TEST SUMMARY")
    println("=" * 80)

    BenchmarkReporter.printSummary(allResults.toList)

    // Count passes/failures
    val passed = allResults.count { r =>
      baselines.get(r.name).forall(r.avgMs <= _)
    }
    val total = allResults.size

    println()
    println(s"Results: $passed/$total within baseline")

    if (passed == total) {
      println("All regression tests PASSED")
    } else {
      println("WARNING: Some tests exceeded baselines (see failures above)")
    }

    println("=" * 80)

    // Write report
    val reportPath = s"target/regression-test-${System.currentTimeMillis()}.json"
    BenchmarkReporter.writeJsonReport(allResults.toList, reportPath)
    println(s"Report written to: $reportPath")

    allResults.size should be > 0
  }
}
