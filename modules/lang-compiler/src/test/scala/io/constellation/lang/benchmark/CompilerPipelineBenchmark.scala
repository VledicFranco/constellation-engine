package io.constellation.lang.benchmark

import io.constellation.lang.*
import io.constellation.lang.ast.CompileError
import io.constellation.lang.compiler.{DagCompiler, IRGenerator, IRPipeline}
import io.constellation.lang.optimizer.{IROptimizer, OptimizationConfig}
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.semantic.{FunctionRegistry, TypeChecker}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

/** Benchmarks for compiler pipeline phases
  *
  * Run with: sbt "langCompiler/testOnly *CompilerPipelineBenchmark"
  *
  * Or run all benchmarks: sbt "langCompiler/testOnly *Benchmark"
  */
class CompilerPipelineBenchmark extends AnyFlatSpec with Matchers {

  // Benchmark configuration
  val WarmupIterations  = 5
  val MeasureIterations = 20
  val RunStressTests    = false // Set to true for comprehensive testing

  // Collect all results for final report
  private val allResults = ListBuffer[BenchmarkResult]()

  // Empty registry for basic tests (no stdlib functions)
  val emptyRegistry: FunctionRegistry = FunctionRegistry.empty

  // -----------------------------------------------------------------
  // Phase Benchmarks: Parse
  // -----------------------------------------------------------------

  "Parser benchmark" should "measure parsing time for small programs" in {
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
    allResults += result

    result.avgMs should be < 50.0 // Target: <50ms
  }

  it should "measure parsing time for medium programs" in {
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
    allResults += result

    result.avgMs should be < 100.0 // Target: <100ms
  }

  it should "measure parsing time for large programs" in {
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
    allResults += result

    result.avgMs should be < 300.0 // Target: <300ms
  }

  // -----------------------------------------------------------------
  // Phase Benchmarks: Type Check
  // -----------------------------------------------------------------

  "TypeChecker benchmark" should "measure type checking time for small programs" in {
    val source = TestFixtures.smallProgram
    val parsed = ConstellationParser.parse(source).toOption.get

    val result = BenchmarkHarness.measureWithWarmup(
      name = "typecheck_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "typecheck",
      inputSize = "small"
    ) {
      TypeChecker.check(parsed, emptyRegistry)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 50.0
  }

  it should "measure type checking time for medium programs" in {
    val source = TestFixtures.mediumProgram
    val parsed = ConstellationParser.parse(source).toOption.get

    val result = BenchmarkHarness.measureWithWarmup(
      name = "typecheck_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "typecheck",
      inputSize = "medium"
    ) {
      TypeChecker.check(parsed, emptyRegistry)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0
  }

  it should "measure type checking time for large programs" in {
    val source = TestFixtures.largeProgram
    val parsed = ConstellationParser.parse(source).toOption.get

    val result = BenchmarkHarness.measureWithWarmup(
      name = "typecheck_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "typecheck",
      inputSize = "large"
    ) {
      TypeChecker.check(parsed, emptyRegistry)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 300.0
  }

  // -----------------------------------------------------------------
  // Phase Benchmarks: IR Generation
  // -----------------------------------------------------------------

  "IRGenerator benchmark" should "measure IR generation time for small programs" in {
    val source = TestFixtures.smallProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val typed  = TypeChecker.check(parsed, emptyRegistry).toOption.get

    val result = BenchmarkHarness.measureWithWarmup(
      name = "irgen_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "irgen",
      inputSize = "small"
    ) {
      IRGenerator.generate(typed)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 20.0
  }

  it should "measure IR generation time for medium programs" in {
    val source = TestFixtures.mediumProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val typed  = TypeChecker.check(parsed, emptyRegistry).toOption.get

    val result = BenchmarkHarness.measureWithWarmup(
      name = "irgen_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "irgen",
      inputSize = "medium"
    ) {
      IRGenerator.generate(typed)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 50.0
  }

  it should "measure IR generation time for large programs" in {
    val source = TestFixtures.largeProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val typed  = TypeChecker.check(parsed, emptyRegistry).toOption.get

    val result = BenchmarkHarness.measureWithWarmup(
      name = "irgen_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "irgen",
      inputSize = "large"
    ) {
      IRGenerator.generate(typed)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0
  }

  // -----------------------------------------------------------------
  // Phase Benchmarks: IR Optimization
  // -----------------------------------------------------------------

  "IROptimizer benchmark" should "measure optimization time for small programs" in {
    val source = TestFixtures.smallProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val typed  = TypeChecker.check(parsed, emptyRegistry).toOption.get
    val ir     = IRGenerator.generate(typed)

    val result = BenchmarkHarness.measureWithWarmup(
      name = "optimize_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "optimize",
      inputSize = "small"
    ) {
      IROptimizer.optimizeIR(ir, OptimizationConfig.default)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 20.0
  }

  it should "measure optimization time for medium programs" in {
    val source = TestFixtures.mediumProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val typed  = TypeChecker.check(parsed, emptyRegistry).toOption.get
    val ir     = IRGenerator.generate(typed)

    val result = BenchmarkHarness.measureWithWarmup(
      name = "optimize_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "optimize",
      inputSize = "medium"
    ) {
      IROptimizer.optimizeIR(ir, OptimizationConfig.default)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 50.0
  }

  it should "measure optimization time for large programs" in {
    val source = TestFixtures.largeProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val typed  = TypeChecker.check(parsed, emptyRegistry).toOption.get
    val ir     = IRGenerator.generate(typed)

    val result = BenchmarkHarness.measureWithWarmup(
      name = "optimize_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "optimize",
      inputSize = "large"
    ) {
      IROptimizer.optimizeIR(ir, OptimizationConfig.default)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0
  }

  // -----------------------------------------------------------------
  // Phase Benchmarks: DAG Compilation
  // -----------------------------------------------------------------

  "DagCompiler benchmark" should "measure DAG compilation time for small programs" in {
    val source    = TestFixtures.smallProgram
    val parsed    = ConstellationParser.parse(source).toOption.get
    val typed     = TypeChecker.check(parsed, emptyRegistry).toOption.get
    val ir        = IRGenerator.generate(typed)
    val optimized = IROptimizer.optimizeIR(ir, OptimizationConfig.none)

    val result = BenchmarkHarness.measureWithWarmup(
      name = "dagcompile_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "dagcompile",
      inputSize = "small"
    ) {
      DagCompiler.compile(optimized, "benchmark-dag", Map.empty)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 50.0
  }

  it should "measure DAG compilation time for medium programs" in {
    val source    = TestFixtures.mediumProgram
    val parsed    = ConstellationParser.parse(source).toOption.get
    val typed     = TypeChecker.check(parsed, emptyRegistry).toOption.get
    val ir        = IRGenerator.generate(typed)
    val optimized = IROptimizer.optimizeIR(ir, OptimizationConfig.none)

    val result = BenchmarkHarness.measureWithWarmup(
      name = "dagcompile_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "dagcompile",
      inputSize = "medium"
    ) {
      DagCompiler.compile(optimized, "benchmark-dag", Map.empty)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0
  }

  it should "measure DAG compilation time for large programs" in {
    val source    = TestFixtures.largeProgram
    val parsed    = ConstellationParser.parse(source).toOption.get
    val typed     = TypeChecker.check(parsed, emptyRegistry).toOption.get
    val ir        = IRGenerator.generate(typed)
    val optimized = IROptimizer.optimizeIR(ir, OptimizationConfig.none)

    val result = BenchmarkHarness.measureWithWarmup(
      name = "dagcompile_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "dagcompile",
      inputSize = "large"
    ) {
      DagCompiler.compile(optimized, "benchmark-dag", Map.empty)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 200.0
  }

  // -----------------------------------------------------------------
  // Full Pipeline Benchmarks
  // -----------------------------------------------------------------

  "Full pipeline benchmark" should "measure end-to-end compilation time for small programs" in {
    val source   = TestFixtures.smallProgram
    val compiler = LangCompiler.empty

    val result = BenchmarkHarness.measureWithWarmup(
      name = "full_pipeline_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "full",
      inputSize = "small"
    ) {
      compiler.compile(source, "benchmark-dag")
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0
  }

  it should "measure end-to-end compilation time for medium programs" in {
    val source   = TestFixtures.mediumProgram
    val compiler = LangCompiler.empty

    val result = BenchmarkHarness.measureWithWarmup(
      name = "full_pipeline_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "full",
      inputSize = "medium"
    ) {
      compiler.compile(source, "benchmark-dag")
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 200.0
  }

  it should "measure end-to-end compilation time for large programs" in {
    val source   = TestFixtures.largeProgram
    val compiler = LangCompiler.empty

    val result = BenchmarkHarness.measureWithWarmup(
      name = "full_pipeline_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "full",
      inputSize = "large"
    ) {
      compiler.compile(source, "benchmark-dag")
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 500.0
  }

  // -----------------------------------------------------------------
  // Report Generation (runs last)
  // -----------------------------------------------------------------

  "Benchmark report" should "generate summary and JSON report" in {
    // Print summary
    BenchmarkReporter.printSummary(allResults.toList)

    // Identify bottlenecks by size
    List("small", "medium", "large").foreach { size =>
      val sizeResults = allResults.filter(_.inputSize == size).toList
      if sizeResults.nonEmpty then {
        BenchmarkReporter.printQuickStats(s"$size programs", sizeResults)
      }
    }

    // Write JSON report
    val reportPath = BenchmarkReporter.generateReportPath()
    BenchmarkReporter.writeJsonReport(allResults.toList, reportPath)
    println(s"\nJSON report written to: $reportPath")

    // Basic sanity check
    allResults.size should be > 0
  }
}
