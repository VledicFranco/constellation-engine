package io.constellation.lang.benchmark

import io.constellation.lang._
import io.constellation.lang.compiler.{DagCompiler, IRGenerator}
import io.constellation.lang.optimizer.{IROptimizer, OptimizationConfig}
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.semantic.{FunctionRegistry, TypeChecker}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

/** Result of a memory measurement */
case class MemoryResult(
    name: String,
    phase: String,
    inputSize: String,
    heapDeltaBytes: Long,
    heapDeltaMB: Double,
    samples: Int,
    minBytes: Long,
    maxBytes: Long
) {

  /** Format result for console output */
  def toConsoleString: String = {
    val nameWidth  = 40
    val paddedName = name.padTo(nameWidth, ' ').take(nameWidth)
    f"$paddedName : $heapDeltaMB%8.2f MB (min: ${minBytes / 1024.0 / 1024.0}%.2f, max: ${maxBytes / 1024.0 / 1024.0}%.2f MB)"
  }
}

/** Memory profiling benchmarks for compilation phases
  *
  * Measures heap memory consumption to establish baselines and identify
  * memory-intensive operations.
  *
  * Run with: sbt "langCompiler/testOnly *MemoryBenchmark"
  *
  * Note: JVM memory measurement is inherently noisy. Results show averages
  * over multiple samples with min/max ranges.
  */
class MemoryBenchmark extends AnyFlatSpec with Matchers {

  // Configuration
  val Samples        = 10 // Number of measurement samples per test
  val GcWaitMs       = 100 // Time to wait for GC to complete
  val MemoryLimitMB  = 200.0 // Fail if any single operation exceeds this

  // Collect all results for final report
  private val allResults = ListBuffer[MemoryResult]()

  // Empty registry for basic tests (no stdlib functions)
  val emptyRegistry: FunctionRegistry = FunctionRegistry.empty

  /** Measure heap memory delta for an operation
    *
    * Forces GC before and after to get accurate retained memory measurement.
    * Returns the difference in used heap memory.
    */
  def measureHeapDelta[A](op: => A): Long = {
    // Force GC to get accurate baseline
    System.gc()
    Thread.sleep(GcWaitMs)

    val runtime = Runtime.getRuntime
    val before  = runtime.totalMemory - runtime.freeMemory

    // Execute operation
    op

    // Force GC to measure actual retained memory
    System.gc()
    Thread.sleep(GcWaitMs)

    val after = runtime.totalMemory - runtime.freeMemory

    // Avoid negative values due to GC timing variations
    math.max(0, after - before)
  }

  /** Measure memory with multiple samples and statistics */
  def measureMemory(name: String, phase: String, inputSize: String)(
      op: => Any
  ): MemoryResult = {
    // Collect samples
    val samples = (1 to Samples).map(_ => measureHeapDelta(op))

    // Calculate statistics
    val avg = samples.sum / samples.size
    val min = samples.min
    val max = samples.max

    val result = MemoryResult(
      name = name,
      phase = phase,
      inputSize = inputSize,
      heapDeltaBytes = avg,
      heapDeltaMB = avg / (1024.0 * 1024.0),
      samples = Samples,
      minBytes = min,
      maxBytes = max
    )

    println(result.toConsoleString)
    result
  }

  // -----------------------------------------------------------------
  // Phase Memory: Parse
  // -----------------------------------------------------------------

  "Parse memory" should "be measured for small programs" in {
    val source = TestFixtures.smallProgram
    val result = measureMemory("parse_small", "parse", "small") {
      ConstellationParser.parse(source)
    }
    allResults += result
    // Note: JVM memory measurement is noisy due to class loading and GC timing
    // Thresholds are set high enough to avoid flaky failures while still catching regressions
    result.heapDeltaMB should be < 50.0
  }

  it should "be measured for medium programs" in {
    val source = TestFixtures.mediumProgram
    val result = measureMemory("parse_medium", "parse", "medium") {
      ConstellationParser.parse(source)
    }
    allResults += result
    result.heapDeltaMB should be < 50.0
  }

  it should "be measured for large programs" in {
    val source = TestFixtures.largeProgram
    val result = measureMemory("parse_large", "parse", "large") {
      ConstellationParser.parse(source)
    }
    allResults += result
    result.heapDeltaMB should be < 50.0
  }

  // -----------------------------------------------------------------
  // Phase Memory: TypeCheck
  // -----------------------------------------------------------------

  "TypeCheck memory" should "be measured for small programs" in {
    val source = TestFixtures.smallProgram
    val parsed = ConstellationParser.parse(source).toOption.get

    val result = measureMemory("typecheck_small", "typecheck", "small") {
      TypeChecker.check(parsed, emptyRegistry)
    }
    allResults += result
    result.heapDeltaMB should be < 50.0
  }

  it should "be measured for medium programs" in {
    val source = TestFixtures.mediumProgram
    val parsed = ConstellationParser.parse(source).toOption.get

    val result = measureMemory("typecheck_medium", "typecheck", "medium") {
      TypeChecker.check(parsed, emptyRegistry)
    }
    allResults += result
    result.heapDeltaMB should be < 50.0
  }

  it should "be measured for large programs" in {
    val source = TestFixtures.largeProgram
    val parsed = ConstellationParser.parse(source).toOption.get

    val result = measureMemory("typecheck_large", "typecheck", "large") {
      TypeChecker.check(parsed, emptyRegistry)
    }
    allResults += result
    result.heapDeltaMB should be < 50.0
  }

  // -----------------------------------------------------------------
  // Phase Memory: IR Generation
  // -----------------------------------------------------------------

  "IR generation memory" should "be measured for small programs" in {
    val source = TestFixtures.smallProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val typed  = TypeChecker.check(parsed, emptyRegistry).toOption.get

    val result = measureMemory("irgen_small", "irgen", "small") {
      IRGenerator.generate(typed)
    }
    allResults += result
    result.heapDeltaMB should be < 50.0
  }

  it should "be measured for medium programs" in {
    val source = TestFixtures.mediumProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val typed  = TypeChecker.check(parsed, emptyRegistry).toOption.get

    val result = measureMemory("irgen_medium", "irgen", "medium") {
      IRGenerator.generate(typed)
    }
    allResults += result
    result.heapDeltaMB should be < 50.0
  }

  it should "be measured for large programs" in {
    val source = TestFixtures.largeProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val typed  = TypeChecker.check(parsed, emptyRegistry).toOption.get

    val result = measureMemory("irgen_large", "irgen", "large") {
      IRGenerator.generate(typed)
    }
    allResults += result
    result.heapDeltaMB should be < 50.0
  }

  // -----------------------------------------------------------------
  // Phase Memory: Optimization
  // -----------------------------------------------------------------

  "Optimization memory" should "be measured for large programs" in {
    val source = TestFixtures.largeProgram
    val parsed = ConstellationParser.parse(source).toOption.get
    val typed  = TypeChecker.check(parsed, emptyRegistry).toOption.get
    val ir     = IRGenerator.generate(typed)

    val result = measureMemory("optimize_large", "optimize", "large") {
      IROptimizer.optimizeIR(ir, OptimizationConfig.default)
    }
    allResults += result
    result.heapDeltaMB should be < 50.0
  }

  // -----------------------------------------------------------------
  // Phase Memory: DAG Compilation
  // -----------------------------------------------------------------

  "DAG compilation memory" should "be measured for large programs" in {
    val source    = TestFixtures.largeProgram
    val parsed    = ConstellationParser.parse(source).toOption.get
    val typed     = TypeChecker.check(parsed, emptyRegistry).toOption.get
    val ir        = IRGenerator.generate(typed)
    val optimized = IROptimizer.optimizeIR(ir, OptimizationConfig.none)

    val result = measureMemory("dagcompile_large", "dagcompile", "large") {
      DagCompiler.compile(optimized, "memory-test", Map.empty)
    }
    allResults += result
    result.heapDeltaMB should be < 50.0
  }

  // -----------------------------------------------------------------
  // Full Pipeline Memory
  // -----------------------------------------------------------------

  "Full pipeline memory" should "be measured for all sizes" in {
    // All sizes use 50MB threshold (warning level)
    // Memory measurement is noisy - these are regression detection thresholds
    List(
      ("small", TestFixtures.smallProgram),
      ("medium", TestFixtures.mediumProgram),
      ("large", TestFixtures.largeProgram)
    ).foreach { case (size, source) =>
      val compiler = LangCompiler.empty
      val result = measureMemory(s"full_pipeline_$size", "full", size) {
        compiler.compile(source, "memory-test")
      }
      allResults += result
      result.heapDeltaMB should be < 50.0
    }
  }

  // -----------------------------------------------------------------
  // Stress Test Memory
  // -----------------------------------------------------------------

  "Stress test memory" should "establish scaling characteristics for 100 nodes" in {
    val source   = TestFixtures.stressProgram100
    val compiler = LangCompiler.empty

    val result = measureMemory("full_pipeline_stress100", "full", "stress100") {
      compiler.compile(source, "stress-test")
    }
    allResults += result

    // Calculate MB per node (approximate node count from chain length)
    val nodes     = 100
    val mbPerNode = result.heapDeltaMB / nodes
    println(f"  Memory per node (100): ${mbPerNode}%.4f MB")

    result.heapDeltaMB should be < 100.0 // 100MB limit for stress tests
  }

  it should "establish scaling characteristics for 200 nodes" in {
    val source   = TestFixtures.stressProgram200
    val compiler = LangCompiler.empty

    val result = measureMemory("full_pipeline_stress200", "full", "stress200") {
      compiler.compile(source, "stress-test")
    }
    allResults += result

    val nodes     = 200
    val mbPerNode = result.heapDeltaMB / nodes
    println(f"  Memory per node (200): ${mbPerNode}%.4f MB")

    result.heapDeltaMB should be < 150.0 // 150MB limit for larger stress tests
  }

  // -----------------------------------------------------------------
  // Memory Scaling Analysis & Report
  // -----------------------------------------------------------------

  "Memory benchmark report" should "summarize findings and calculate scaling" in {
    println("\n" + "=" * 80)
    println("MEMORY BENCHMARK SUMMARY")
    println("=" * 80)

    // Group by phase
    val byPhase = allResults.groupBy(_.phase)
    byPhase.toList.sortBy(_._1).foreach { case (phase, results) =>
      println(s"\n--- $phase ---")
      results
        .sortBy(r => (r.inputSize, r.name))
        .foreach(r => println(f"  ${r.name}%-35s: ${r.heapDeltaMB}%8.2f MB"))
    }

    // Scaling analysis
    println("\n" + "=" * 80)
    println("SCALING ANALYSIS")
    println("=" * 80)

    // Full pipeline scaling
    val fullResults = allResults.filter(_.phase == "full").toList
    val small       = fullResults.find(_.inputSize == "small").map(_.heapDeltaMB)
    val medium      = fullResults.find(_.inputSize == "medium").map(_.heapDeltaMB)
    val large       = fullResults.find(_.inputSize == "large").map(_.heapDeltaMB)
    val stress100   = fullResults.find(_.inputSize == "stress100").map(_.heapDeltaMB)
    val stress200   = fullResults.find(_.inputSize == "stress200").map(_.heapDeltaMB)

    println("\nFull Pipeline Memory Scaling:")
    (small, large) match {
      case (Some(s), Some(l)) =>
        println(f"  Small  -> Large:  ${l / s}%.1fx memory increase")
      case _ => ()
    }
    (stress100, stress200) match {
      case (Some(s100), Some(s200)) =>
        println(f"  Stress100 -> Stress200: ${s200 / s100}%.1fx memory increase (2x nodes)")
        val scaling = (s200 / s100) / 2.0
        val scalingType =
          if (scaling < 0.6) "sub-linear"
          else if (scaling < 1.1) "linear"
          else if (scaling < 1.5) "slightly super-linear"
          else "super-linear (warning!)"
        println(s"  Scaling characteristic: $scalingType ($scaling ratio)")
      case _ => ()
    }

    // Memory per node calculation
    println("\nMemory per Node Estimates:")
    stress100.foreach { mem =>
      println(f"  From 100-node stress test: ${mem / 100.0}%.4f MB/node")
    }
    stress200.foreach { mem =>
      println(f"  From 200-node stress test: ${mem / 200.0}%.4f MB/node")
    }

    // Recommendations
    println("\n" + "=" * 80)
    println("RECOMMENDATIONS")
    println("=" * 80)

    val maxObserved = allResults.map(_.heapDeltaMB).max
    println(f"\nMaximum observed memory: $maxObserved%.2f MB")

    // Calculate warning thresholds
    val recommendedHeapMin = math.ceil(maxObserved * 2 / 10) * 10 // Round up to nearest 10
    val recommendedHeapMax = math.ceil(maxObserved * 4 / 100) * 100 // Round up to nearest 100

    println(s"\nJVM Heap Recommendations:")
    println(s"  Minimum recommended:  -Xms${recommendedHeapMin.toInt}m")
    println(s"  For large workloads:  -Xmx${recommendedHeapMax.toInt}m")

    println("\nWarning Thresholds (for LSP/extension):")
    println(s"  Info:     > 50 MB per compilation")
    println(s"  Warning:  > 100 MB per compilation")
    println(s"  Critical: > 200 MB per compilation")

    // Sanity check
    allResults.size should be > 0
  }
}
