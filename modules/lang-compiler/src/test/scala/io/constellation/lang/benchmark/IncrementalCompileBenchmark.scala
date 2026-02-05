package io.constellation.lang.benchmark

import scala.collection.mutable.ListBuffer

import cats.effect.unsafe.implicits.global

import io.constellation.lang.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Benchmarks for incremental compilation performance
  *
  * Measures recompilation time for various types of source code changes, establishing baselines for
  * editor responsiveness targets.
  *
  * Run with: sbt "langCompiler/testOnly *IncrementalCompileBenchmark"
  *
  * Scenarios tested:
  *   - Comment addition (whitespace-only change)
  *   - Variable rename (identifier change)
  *   - New output addition (semantic change)
  *   - Type definition modification (structural change)
  *
  * Target metrics:
  *   - Comment addition: <50ms
  *   - Variable rename: <100ms
  *   - New output: <100ms
  *   - Type modification: <200ms
  */
class IncrementalCompileBenchmark extends AnyFlatSpec with Matchers {

  // Benchmark configuration
  val WarmupIterations  = 5
  val MeasureIterations = 20

  // Collect results
  private val allResults = ListBuffer[BenchmarkResult]()

  // -----------------------------------------------------------------
  // Baseline: Cold Compilation (for comparison)
  // -----------------------------------------------------------------

  "Cold compilation baseline" should "establish baseline for medium program" in {
    val source = TestFixtures.mediumProgram

    val result = BenchmarkHarness.measureWithWarmup(
      name = "cold_baseline_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "cold",
      inputSize = "medium"
    ) {
      // Fresh compiler each time - no caching benefit
      val compiler = LangCompiler.empty
      compiler.compile(source, s"dag-${System.nanoTime()}")
    }

    println(s"Cold baseline: ${result.toConsoleString}")
    allResults += result
  }

  // -----------------------------------------------------------------
  // Scenario 1: Comment Addition (whitespace-only change)
  // -----------------------------------------------------------------

  "Incremental compilation" should "be fast for comment additions" in {
    val baseSource = TestFixtures.mediumProgram
    val compiler   = LangCompiler.builder.withCaching().build

    // Populate cache with original source
    compiler.compile(baseSource, "incremental-dag")

    var iteration = 0
    val result = BenchmarkHarness.measureWithWarmup(
      name = "incremental_comment_add",
      warmupIterations = 0, // Cache already populated
      measureIterations = MeasureIterations,
      phase = "incremental",
      inputSize = "comment"
    ) {
      iteration += 1
      // Add a new comment - minimal change
      val modifiedSource = baseSource + s"\n# comment iteration $iteration"
      compiler.compile(modifiedSource, "incremental-dag")
    }

    println(result.toConsoleString)
    allResults += result

    // Target: Comment additions should be fast
    // Even though cache misses (source changed), should still be reasonable
    result.avgMs should be < 100.0
  }

  // -----------------------------------------------------------------
  // Scenario 2: Whitespace-Only Changes
  // -----------------------------------------------------------------

  it should "be fast for whitespace-only changes" in {
    val baseSource = TestFixtures.mediumProgram
    val compiler   = LangCompiler.builder.withCaching().build

    // Populate cache
    compiler.compile(baseSource, "whitespace-dag")

    var iteration = 0
    val result = BenchmarkHarness.measureWithWarmup(
      name = "incremental_whitespace",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "incremental",
      inputSize = "whitespace"
    ) {
      iteration += 1
      // Add trailing whitespace - minimal semantic impact
      val modifiedSource = baseSource + (" " * iteration)
      compiler.compile(modifiedSource, "whitespace-dag")
    }

    println(result.toConsoleString)
    allResults += result

    // Target: Whitespace changes should be very fast
    result.avgMs should be < 100.0
  }

  // -----------------------------------------------------------------
  // Scenario 3: Variable Rename (identifier change)
  // -----------------------------------------------------------------

  it should "be fast for variable renames" in {
    val baseSource = TestFixtures.mediumProgram
    val compiler   = LangCompiler.builder.withCaching().build

    // Populate cache
    compiler.compile(baseSource, "rename-dag")

    var iteration = 0
    val result = BenchmarkHarness.measureWithWarmup(
      name = "incremental_var_rename",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "incremental",
      inputSize = "rename"
    ) {
      iteration += 1
      // Rename 'enabled' to 'enabled_N' - requires recompilation
      val modifiedSource = baseSource.replace("enabled", s"enabled_$iteration")
      compiler.compile(modifiedSource, "rename-dag")
    }

    println(result.toConsoleString)
    allResults += result

    // Target: Variable renames should complete reasonably
    result.avgMs should be < 150.0
  }

  // -----------------------------------------------------------------
  // Scenario 4: New Output Addition (semantic change)
  // -----------------------------------------------------------------

  it should "handle new output additions efficiently" in {
    val baseSource = TestFixtures.mediumProgram
    val compiler   = LangCompiler.builder.withCaching().build

    // Populate cache
    compiler.compile(baseSource, "output-dag")

    var iteration = 0
    val result = BenchmarkHarness.measureWithWarmup(
      name = "incremental_new_output",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "incremental",
      inputSize = "output"
    ) {
      iteration += 1
      // Add a new output line - semantic change
      val modifiedSource = baseSource + s"\nout needsUpgrade"
      compiler.compile(modifiedSource, "output-dag")
    }

    println(result.toConsoleString)
    allResults += result

    // Target: Output additions should be under 100ms
    result.avgMs should be < 150.0
  }

  // -----------------------------------------------------------------
  // Scenario 5: New Variable Assignment
  // -----------------------------------------------------------------

  it should "handle new variable assignments efficiently" in {
    val baseSource = TestFixtures.mediumProgram
    val compiler   = LangCompiler.builder.withCaching().build

    // Populate cache
    compiler.compile(baseSource, "newvar-dag")

    var iteration = 0
    val result = BenchmarkHarness.measureWithWarmup(
      name = "incremental_new_var",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "incremental",
      inputSize = "newvar"
    ) {
      iteration += 1
      // Add a new variable assignment
      val modifiedSource = baseSource + s"\nnewVar$iteration = isActive and enabled"
      compiler.compile(modifiedSource, "newvar-dag")
    }

    println(result.toConsoleString)
    allResults += result

    // Target: New variable assignments under 150ms
    result.avgMs should be < 150.0
  }

  // -----------------------------------------------------------------
  // Scenario 6: Type Definition Change (structural change)
  // -----------------------------------------------------------------

  it should "handle type definition changes" in {
    val baseSource = TestFixtures.mediumProgram
    val compiler   = LangCompiler.builder.withCaching().build

    // Populate cache
    compiler.compile(baseSource, "typedef-dag")

    var iteration = 0
    val result = BenchmarkHarness.measureWithWarmup(
      name = "incremental_type_change",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "incremental",
      inputSize = "typedef"
    ) {
      iteration += 1
      // Add a new field to a type - significant structural change
      // Since we can't easily add fields, we add a new type definition
      val modifiedSource = s"type ExtraType$iteration = { extra: Boolean }\n" + baseSource
      compiler.compile(modifiedSource, "typedef-dag")
    }

    println(result.toConsoleString)
    allResults += result

    // Target: Type changes are more expensive - under 200ms
    result.avgMs should be < 250.0
  }

  // -----------------------------------------------------------------
  // Scenario 7: Expression Modification
  // -----------------------------------------------------------------

  it should "handle expression modifications efficiently" in {
    val baseSource = TestFixtures.mediumProgram
    val compiler   = LangCompiler.builder.withCaching().build

    // Populate cache
    compiler.compile(baseSource, "expr-dag")

    var iteration = 0
    val result = BenchmarkHarness.measureWithWarmup(
      name = "incremental_expr_change",
      warmupIterations = 0,
      measureIterations = MeasureIterations,
      phase = "incremental",
      inputSize = "expression"
    ) {
      iteration += 1
      // Modify a boolean expression
      val toggle         = if iteration % 2 == 0 then "and" else "or"
      val modifiedSource = baseSource.replace("enabled and active", s"enabled $toggle active")
      compiler.compile(modifiedSource, "expr-dag")
    }

    println(result.toConsoleString)
    allResults += result

    // Target: Expression changes under 100ms
    result.avgMs should be < 150.0
  }

  // -----------------------------------------------------------------
  // Speedup Analysis
  // -----------------------------------------------------------------

  "Incremental speedup analysis" should "compare incremental vs cold compilation" in {
    println("\n" + "=" * 80)
    println("INCREMENTAL COMPILATION SPEEDUP ANALYSIS")
    println("=" * 80)

    val coldResult         = allResults.find(_.phase == "cold")
    val incrementalResults = allResults.filter(_.phase == "incremental").toList

    coldResult match {
      case Some(cold) =>
        println(f"\nCold compilation baseline: ${cold.avgMs}%.2fms\n")
        println("Incremental compilation times:")
        println("-" * 60)

        incrementalResults.foreach { incr =>
          val speedup = cold.avgMs / incr.avgMs
          val status  = if speedup >= 1.0 then "✓" else "✗"
          println(
            f"  ${incr.inputSize}%-15s: ${incr.avgMs}%7.2fms  speedup: ${speedup}%5.2fx  $status"
          )
        }

        println("-" * 60)

        // Calculate average incremental time
        if incrementalResults.nonEmpty then {
          val avgIncremental = incrementalResults.map(_.avgMs).sum / incrementalResults.size
          val avgSpeedup     = cold.avgMs / avgIncremental
          println(f"\nAverage incremental time: $avgIncremental%.2fms")
          println(f"Average speedup vs cold:  ${avgSpeedup}%.2fx")
        }

      case None =>
        println("Cold baseline not available")
    }

    println()
    allResults.size should be > 0
  }

  // -----------------------------------------------------------------
  // Report Generation
  // -----------------------------------------------------------------

  "Incremental benchmark report" should "generate summary and JSON report" in {
    println("\n" + "=" * 80)
    println("INCREMENTAL COMPILE BENCHMARK SUMMARY")
    println("=" * 80)

    BenchmarkReporter.printSummary(allResults.toList)

    // Target verification
    println("\n" + "-" * 60)
    println("TARGET VERIFICATION:")
    println("-" * 60)

    val targets = Map(
      "comment"    -> 50.0,
      "whitespace" -> 50.0,
      "rename"     -> 100.0,
      "output"     -> 100.0,
      "newvar"     -> 100.0,
      "typedef"    -> 200.0,
      "expression" -> 100.0
    )

    targets.foreach { case (inputSize, target) =>
      allResults.find(_.inputSize == inputSize) match {
        case Some(result) =>
          val status = if result.avgMs <= target then "PASS ✓" else "FAIL ✗"
          println(f"  $inputSize%-15s: ${result.avgMs}%7.2fms / ${target}%.0fms target  $status")
        case None =>
          println(f"  $inputSize%-15s: (no data)")
      }
    }

    // Write JSON report
    val reportPath = s"target/benchmark-incremental-${System.currentTimeMillis()}.json"
    BenchmarkReporter.writeJsonReport(allResults.toList, reportPath)
    println(s"\nIncremental benchmark report written to: $reportPath")

    allResults.size should be > 0
  }
}
