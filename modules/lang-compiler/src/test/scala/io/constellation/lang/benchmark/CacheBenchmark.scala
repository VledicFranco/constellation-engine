package io.constellation.lang.benchmark

import cats.effect.unsafe.implicits.global
import io.constellation.lang.*
import io.constellation.lang.compiler.CompilationOutput
import io.constellation.lang.semantic.FunctionRegistry
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

/** Benchmarks for compilation cache performance
  *
  * Run with: sbt "langCompiler/testOnly *CacheBenchmark"
  *
  * Tests:
  *   - Cold cache (first compile, no cache hit)
  *   - Warm cache (subsequent compiles, cache hits)
  *   - Cache speedup factor (warm / cold ratio)
  */
class CacheBenchmark extends AnyFlatSpec with Matchers {

  // Benchmark configuration
  val WarmupIterations  = 3
  val MeasureIterations = 20

  // Collect results
  private val allResults = ListBuffer[BenchmarkResult]()

  // -----------------------------------------------------------------
  // Cold Cache Benchmarks (Cache Miss)
  // -----------------------------------------------------------------

  "Cold cache benchmark" should "measure compilation time without cache (small)" in {
    val source = TestFixtures.smallProgram

    val result = BenchmarkHarness.measureWithWarmup(
      name = "cold_cache_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "cold_cache",
      inputSize = "small"
    ) {
      // Create fresh compiler each time (no caching)
      val compiler = LangCompiler.empty
      compiler.compile(source, s"dag-${System.nanoTime()}")
    }

    println(result.toConsoleString)
    allResults += result
  }

  it should "measure compilation time without cache (medium)" in {
    val source = TestFixtures.mediumProgram

    val result = BenchmarkHarness.measureWithWarmup(
      name = "cold_cache_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "cold_cache",
      inputSize = "medium"
    ) {
      val compiler = LangCompiler.empty
      compiler.compile(source, s"dag-${System.nanoTime()}")
    }

    println(result.toConsoleString)
    allResults += result
  }

  it should "measure compilation time without cache (large)" in {
    val source = TestFixtures.largeProgram

    val result = BenchmarkHarness.measureWithWarmup(
      name = "cold_cache_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "cold_cache",
      inputSize = "large"
    ) {
      val compiler = LangCompiler.empty
      compiler.compile(source, s"dag-${System.nanoTime()}")
    }

    println(result.toConsoleString)
    allResults += result
  }

  // -----------------------------------------------------------------
  // Warm Cache Benchmarks (Cache Hit)
  // -----------------------------------------------------------------

  "Warm cache benchmark" should "measure compilation time with cache hits (small)" in {
    val source = TestFixtures.smallProgram

    // Create caching compiler
    val compiler = LangCompiler.builder.withCaching().build

    // Warmup: first compile populates cache
    compiler.compile(source, "cached-dag")

    val result = BenchmarkHarness.measureWithWarmup(
      name = "warm_cache_small",
      warmupIterations = 0, // Already warmed by initial compile
      measureIterations = MeasureIterations,
      phase = "warm_cache",
      inputSize = "small"
    ) {
      compiler.compile(source, "cached-dag")
    }

    println(result.toConsoleString)
    allResults += result

    // Cache hits should be very fast
    result.avgMs should be < 5.0
  }

  it should "measure compilation time with cache hits (medium)" in {
    val source   = TestFixtures.mediumProgram
    val compiler = LangCompiler.builder.withCaching().build

    // Populate cache
    compiler.compile(source, "cached-dag")

    val result = BenchmarkHarness.measureWithWarmup(
      name = "warm_cache_medium",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "warm_cache",
      inputSize = "medium"
    ) {
      compiler.compile(source, "cached-dag")
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 5.0
  }

  it should "measure compilation time with cache hits (large)" in {
    val source   = TestFixtures.largeProgram
    val compiler = LangCompiler.builder.withCaching().build

    // Populate cache
    compiler.compile(source, "cached-dag")

    val result = BenchmarkHarness.measureWithWarmup(
      name = "warm_cache_large",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "warm_cache",
      inputSize = "large"
    ) {
      compiler.compile(source, "cached-dag")
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 5.0
  }

  // -----------------------------------------------------------------
  // IR Cache Benchmarks (compileToIR caching)
  // -----------------------------------------------------------------

  "IR cache benchmark" should "measure IR compilation with cache (large)" in {
    val source   = TestFixtures.largeProgram
    val compiler = LangCompiler.builder.withCaching().build.asInstanceOf[CachingLangCompiler]

    // Cold compile first
    compiler.compileToIR(source, "ir-dag")

    val result = BenchmarkHarness.measureWithWarmup(
      name = "ir_cache_large",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "ir_cache",
      inputSize = "large"
    ) {
      compiler.compileToIR(source, "ir-dag")
    }

    println(result.toConsoleString)
    allResults += result

    // IR cache hits should also be fast
    result.avgMs should be < 5.0
  }

  // -----------------------------------------------------------------
  // Cache Invalidation Benchmarks
  // -----------------------------------------------------------------

  "Cache invalidation benchmark" should "measure recompilation after invalidation" in {
    val source   = TestFixtures.mediumProgram
    val compiler = LangCompiler.builder.withCaching().build.asInstanceOf[CachingLangCompiler]

    // Populate cache
    compiler.compile(source, "test-dag")

    val result = BenchmarkHarness.measureWithWarmup(
      name = "invalidate_recompile",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "invalidate",
      inputSize = "medium"
    ) {
      compiler.invalidate("test-dag")
      compiler.compile(source, "test-dag")
    }

    println(result.toConsoleString)
    allResults += result
  }

  // -----------------------------------------------------------------
  // Source Change Detection Benchmarks
  // -----------------------------------------------------------------

  "Source change benchmark" should "measure recompilation on source change" in {
    val baseSource = TestFixtures.mediumProgram
    val compiler   = LangCompiler.builder.withCaching().build

    // Populate cache with original
    compiler.compile(baseSource, "change-dag")

    var iteration = 0
    val result = BenchmarkHarness.measureWithWarmup(
      name = "source_change_recompile",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "source_change",
      inputSize = "medium"
    ) {
      iteration += 1
      // Slightly modify source each time (simulates editing)
      val modifiedSource = baseSource + s"\n# iteration $iteration"
      compiler.compile(modifiedSource, "change-dag")
    }

    println(result.toConsoleString)
    allResults += result
  }

  // -----------------------------------------------------------------
  // Speedup Analysis
  // -----------------------------------------------------------------

  "Cache speedup analysis" should "calculate and report speedup factors" in {
    println("\n" + "=" * 80)
    println("CACHE SPEEDUP ANALYSIS")
    println("=" * 80)

    val sizes = List("small", "medium", "large")

    sizes.foreach { size =>
      val coldResult = allResults.find(r => r.phase == "cold_cache" && r.inputSize == size)
      val warmResult = allResults.find(r => r.phase == "warm_cache" && r.inputSize == size)

      (coldResult, warmResult) match {
        case (Some(cold), Some(warm)) =>
          val speedup = cold.avgMs / warm.avgMs
          println(
            f"$size%-10s: cold=${cold.avgMs}%7.2fms  warm=${warm.avgMs}%7.2fms  speedup=${speedup}%6.1fx"
          )
        case _ =>
          println(s"$size: incomplete data")
      }
    }

    println()

    // Overall stats
    val coldResults = allResults.filter(_.phase == "cold_cache").toList
    val warmResults = allResults.filter(_.phase == "warm_cache").toList

    if coldResults.nonEmpty && warmResults.nonEmpty then {
      val avgCold    = coldResults.map(_.avgMs).sum / coldResults.size
      val avgWarm    = warmResults.map(_.avgMs).sum / warmResults.size
      val avgSpeedup = avgCold / avgWarm

      println(f"Average cold cache time: $avgCold%.2f ms")
      println(f"Average warm cache time: $avgWarm%.2f ms")
      println(f"Average speedup factor:  ${avgSpeedup}%.1fx")

      // Expected: >10x speedup
      avgSpeedup should be > 5.0
    }

    allResults.size should be > 0
  }

  // -----------------------------------------------------------------
  // Report Generation
  // -----------------------------------------------------------------

  "Cache benchmark report" should "generate summary and JSON report" in {
    BenchmarkReporter.printSummary(allResults.toList)

    // Compare cold vs warm
    val coldResults = allResults.filter(_.phase == "cold_cache").toList
    val warmResults = allResults.filter(_.phase == "warm_cache").toList

    BenchmarkReporter.printComparison(
      "warm_cache",
      warmResults,
      "cold_cache",
      coldResults
    )

    // Write JSON report
    val reportPath = s"target/benchmark-cache-${System.currentTimeMillis()}.json"
    BenchmarkReporter.writeJsonReport(allResults.toList, reportPath)
    println(s"\nCache benchmark report written to: $reportPath")

    allResults.size should be > 0
  }
}
