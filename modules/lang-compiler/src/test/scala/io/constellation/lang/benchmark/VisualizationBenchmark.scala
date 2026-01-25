package io.constellation.lang.benchmark

import io.constellation.lang._
import io.constellation.lang.compiler.{IRGenerator, IRProgram}
import io.constellation.lang.optimizer.{IROptimizer, OptimizationConfig}
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.semantic.{FunctionRegistry, TypeChecker}
import io.constellation.lang.viz.{DagVizCompiler, DagVizIR, LayoutConfig, SugiyamaLayout}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

/** Benchmarks for DAG visualization pipeline
  *
  * Run with: sbt "langCompiler/testOnly *VisualizationBenchmark"
  *
  * Tests:
  * - DagVizCompiler: Converting IR to visualization nodes/edges
  * - SugiyamaLayout: Full graph layout algorithm
  * - Combined pipeline: IR to positioned visualization
  */
class VisualizationBenchmark extends AnyFlatSpec with Matchers {

  // Benchmark configuration
  val WarmupIterations  = 5
  val MeasureIterations = 20

  // Collect results
  private val allResults = ListBuffer[BenchmarkResult]()

  // Empty registry for basic tests
  val emptyRegistry: FunctionRegistry = FunctionRegistry.empty

  // Pre-compile IR for each fixture size to isolate visualization benchmarks
  lazy val smallIR: IRProgram  = compileToIR(TestFixtures.smallProgram)
  lazy val mediumIR: IRProgram = compileToIR(TestFixtures.mediumProgram)
  lazy val largeIR: IRProgram  = compileToIR(TestFixtures.largeProgram)
  lazy val stress100IR: IRProgram  = compileToIR(TestFixtures.stressProgram100)
  lazy val stress500IR: IRProgram  = compileToIR(TestFixtures.stressProgram500)
  lazy val stress1000IR: IRProgram = compileToIR(TestFixtures.stressProgram1000)

  private def compileToIR(source: String): IRProgram = {
    val parsed  = ConstellationParser.parse(source).toOption.get
    val typed   = TypeChecker.check(parsed, emptyRegistry).toOption.get
    IRGenerator.generate(typed)
  }

  // -----------------------------------------------------------------
  // DagVizCompiler Benchmarks
  // -----------------------------------------------------------------

  "DagVizCompiler benchmark" should "measure IR to VizIR conversion for small programs" in {
    val result = BenchmarkHarness.measureWithWarmup(
      name = "vizcompile_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "vizcompile",
      inputSize = "small"
    ) {
      DagVizCompiler.compile(smallIR, Some("benchmark"))
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 10.0 // Very fast operation
  }

  it should "measure IR to VizIR conversion for medium programs" in {
    val result = BenchmarkHarness.measureWithWarmup(
      name = "vizcompile_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "vizcompile",
      inputSize = "medium"
    ) {
      DagVizCompiler.compile(mediumIR, Some("benchmark"))
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 20.0
  }

  it should "measure IR to VizIR conversion for large programs" in {
    val result = BenchmarkHarness.measureWithWarmup(
      name = "vizcompile_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "vizcompile",
      inputSize = "large"
    ) {
      DagVizCompiler.compile(largeIR, Some("benchmark"))
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 50.0
  }

  it should "measure IR to VizIR conversion for stress programs" in {
    val result = BenchmarkHarness.measureWithWarmup(
      name = "vizcompile_stress100",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "vizcompile",
      inputSize = "stress"
    ) {
      DagVizCompiler.compile(stress100IR, Some("benchmark"))
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0
  }

  // -----------------------------------------------------------------
  // SugiyamaLayout Benchmarks
  // -----------------------------------------------------------------

  "SugiyamaLayout benchmark" should "measure full layout for small programs" in {
    val vizIR  = DagVizCompiler.compile(smallIR, Some("benchmark"))
    val config = LayoutConfig()

    val result = BenchmarkHarness.measureWithWarmup(
      name = "layout_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "layout",
      inputSize = "small"
    ) {
      SugiyamaLayout.layout(vizIR, config)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 10.0
  }

  it should "measure full layout for medium programs" in {
    val vizIR  = DagVizCompiler.compile(mediumIR, Some("benchmark"))
    val config = LayoutConfig()

    val result = BenchmarkHarness.measureWithWarmup(
      name = "layout_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "layout",
      inputSize = "medium"
    ) {
      SugiyamaLayout.layout(vizIR, config)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 30.0
  }

  it should "measure full layout for large programs" in {
    val vizIR  = DagVizCompiler.compile(largeIR, Some("benchmark"))
    val config = LayoutConfig()

    val result = BenchmarkHarness.measureWithWarmup(
      name = "layout_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "layout",
      inputSize = "large"
    ) {
      SugiyamaLayout.layout(vizIR, config)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0
  }

  it should "measure full layout for stress programs" in {
    val vizIR  = DagVizCompiler.compile(stress100IR, Some("benchmark"))
    val config = LayoutConfig()

    // Print size info
    println(s"  Stress test: ${vizIR.nodes.size} nodes, ${vizIR.edges.size} edges")

    val result = BenchmarkHarness.measureWithWarmup(
      name = "layout_stress100",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "layout",
      inputSize = "stress"
    ) {
      SugiyamaLayout.layout(vizIR, config)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 500.0
  }

  it should "measure full layout for 500 node stress programs" in {
    val vizIR  = DagVizCompiler.compile(stress500IR, Some("benchmark"))
    val config = LayoutConfig()

    // Print size info
    println(s"  Stress 500 test: ${vizIR.nodes.size} nodes, ${vizIR.edges.size} edges")

    val result = BenchmarkHarness.measureWithWarmup(
      name = "layout_stress500",
      warmupIterations = 3, // Fewer warmups for slower tests
      measureIterations = 10, // Fewer iterations for slower tests
      phase = "layout",
      inputSize = "stress500"
    ) {
      SugiyamaLayout.layout(vizIR, config)
    }

    println(result.toConsoleString)
    allResults += result

    // 500 nodes should complete in reasonable time
    result.avgMs should be < 5000.0
  }

  it should "measure full layout for 1000 node stress programs" in {
    val vizIR  = DagVizCompiler.compile(stress1000IR, Some("benchmark"))
    val config = LayoutConfig()

    // Print size info
    println(s"  Stress 1000 test: ${vizIR.nodes.size} nodes, ${vizIR.edges.size} edges")

    val result = BenchmarkHarness.measureWithWarmup(
      name = "layout_stress1000",
      warmupIterations = 2, // Minimal warmups for very slow tests
      measureIterations = 5, // Very few iterations for slow test
      phase = "layout",
      inputSize = "stress1000"
    ) {
      SugiyamaLayout.layout(vizIR, config)
    }

    println(result.toConsoleString)
    allResults += result

    // 1000 nodes establishes upper bound - may be slow
    // We're measuring, not enforcing a strict target here
    result.avgMs should be < 30000.0
  }

  // -----------------------------------------------------------------
  // Combined Visualization Pipeline Benchmarks
  // -----------------------------------------------------------------

  "Full visualization pipeline" should "measure combined IR -> positioned DAG for small programs" in {
    val config = LayoutConfig()

    val result = BenchmarkHarness.measureWithWarmup(
      name = "fullviz_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "fullviz",
      inputSize = "small"
    ) {
      val vizIR = DagVizCompiler.compile(smallIR, Some("benchmark"))
      SugiyamaLayout.layout(vizIR, config)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 20.0
  }

  it should "measure combined IR -> positioned DAG for medium programs" in {
    val config = LayoutConfig()

    val result = BenchmarkHarness.measureWithWarmup(
      name = "fullviz_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "fullviz",
      inputSize = "medium"
    ) {
      val vizIR = DagVizCompiler.compile(mediumIR, Some("benchmark"))
      SugiyamaLayout.layout(vizIR, config)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 50.0
  }

  it should "measure combined IR -> positioned DAG for large programs" in {
    val config = LayoutConfig()

    val result = BenchmarkHarness.measureWithWarmup(
      name = "fullviz_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "fullviz",
      inputSize = "large"
    ) {
      val vizIR = DagVizCompiler.compile(largeIR, Some("benchmark"))
      SugiyamaLayout.layout(vizIR, config)
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 150.0
  }

  // -----------------------------------------------------------------
  // End-to-End: Source -> Positioned Visualization
  // -----------------------------------------------------------------

  "End-to-end visualization" should "measure source -> positioned DAG for large programs" in {
    val source = TestFixtures.largeProgram
    val config = LayoutConfig()

    val result = BenchmarkHarness.measureWithWarmup(
      name = "e2e_viz_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "e2e_viz",
      inputSize = "large"
    ) {
      // Full pipeline: parse -> typecheck -> IR -> vizIR -> layout
      val parsed    = ConstellationParser.parse(source).toOption.get
      val typed     = TypeChecker.check(parsed, emptyRegistry).toOption.get
      val ir        = IRGenerator.generate(typed)
      val vizIR     = DagVizCompiler.compile(ir, Some("benchmark"))
      SugiyamaLayout.layout(vizIR, config)
    }

    println(result.toConsoleString)
    allResults += result

    // This is the critical path for "Show DAG Visualization" command
    // Target: <200ms for responsive UX
    result.avgMs should be < 500.0
  }

  // -----------------------------------------------------------------
  // Report Generation
  // -----------------------------------------------------------------

  "Visualization benchmark report" should "generate summary" in {
    BenchmarkReporter.printSummary(allResults.toList)

    // Compare vizcompile vs layout phases
    val vizcompileResults = allResults.filter(_.phase == "vizcompile").toList
    val layoutResults     = allResults.filter(_.phase == "layout").toList

    if (vizcompileResults.nonEmpty && layoutResults.nonEmpty) {
      BenchmarkReporter.printQuickStats("VizCompile phase", vizcompileResults)
      BenchmarkReporter.printQuickStats("Layout phase", layoutResults)
    }

    // Write JSON report
    val reportPath = s"target/benchmark-visualization-${System.currentTimeMillis()}.json"
    BenchmarkReporter.writeJsonReport(allResults.toList, reportPath)
    println(s"\nVisualization benchmark report written to: $reportPath")

    allResults.size should be > 0
  }

  it should "include scaling analysis for stress tests" in {
    println("\n" + "=" * 80)
    println("LAYOUT SCALING ANALYSIS")
    println("=" * 80)

    val stress100  = allResults.find(_.name == "layout_stress100")
    val stress500  = allResults.find(_.name == "layout_stress500")
    val stress1000 = allResults.find(_.name == "layout_stress1000")

    // Calculate and print scaling factors
    (stress100, stress500) match {
      case (Some(r100), Some(r500)) =>
        val scalingFactor = r500.avgMs / r100.avgMs
        // For O(n² log n), going from 100 to 500 nodes:
        // Expected factor ≈ (500² × log(500)) / (100² × log(100)) ≈ 25 × 1.21 ≈ 30x
        println(f"\n100 -> 500 nodes:")
        println(f"  100 nodes: ${r100.avgMs}%.2fms")
        println(f"  500 nodes: ${r500.avgMs}%.2fms")
        println(f"  Scaling factor: ${scalingFactor}%.2fx")
        if (scalingFactor < 30) {
          println(s"  Analysis: Better than O(n² log n) expected (~30x)")
        } else {
          println(s"  Analysis: As expected for O(n² log n) complexity")
        }
      case _ =>
        println("\n100 -> 500: Incomplete data")
    }

    (stress500, stress1000) match {
      case (Some(r500), Some(r1000)) =>
        val scalingFactor = r1000.avgMs / r500.avgMs
        // For O(n² log n), going from 500 to 1000 nodes:
        // Expected factor ≈ (1000² × log(1000)) / (500² × log(500)) ≈ 4 × 1.11 ≈ 4.4x
        println(f"\n500 -> 1000 nodes:")
        println(f"  500 nodes: ${r500.avgMs}%.2fms")
        println(f"  1000 nodes: ${r1000.avgMs}%.2fms")
        println(f"  Scaling factor: ${scalingFactor}%.2fx")
        if (scalingFactor < 5) {
          println(s"  Analysis: Better than O(n² log n) expected (~4.4x)")
        } else {
          println(s"  Analysis: As expected for O(n² log n) complexity")
        }
      case _ =>
        println("\n500 -> 1000: Incomplete data")
    }

    // Overall scaling from 100 to 1000
    (stress100, stress1000) match {
      case (Some(r100), Some(r1000)) =>
        val scalingFactor = r1000.avgMs / r100.avgMs
        // For O(n² log n), going from 100 to 1000 nodes:
        // Expected factor ≈ (1000² × log(1000)) / (100² × log(100)) ≈ 100 × 1.5 ≈ 150x
        println(f"\n100 -> 1000 nodes (overall):")
        println(f"  100 nodes: ${r100.avgMs}%.2fms")
        println(f"  1000 nodes: ${r1000.avgMs}%.2fms")
        println(f"  Scaling factor: ${scalingFactor}%.2fx")
      case _ =>
        println("\n100 -> 1000: Incomplete data")
    }

    // Warning threshold recommendations
    println("\n" + "-" * 60)
    println("WARNING THRESHOLD RECOMMENDATIONS:")
    println("-" * 60)
    stress500.foreach { r =>
      if (r.avgMs > 1000) {
        println(s"  500 nodes: ${r.avgMs.toLong}ms - Recommend warning at 500 nodes")
      } else if (r.avgMs > 500) {
        println(s"  500 nodes: ${r.avgMs.toLong}ms - Recommend warning at 1000 nodes")
      } else {
        println(s"  500 nodes: ${r.avgMs.toLong}ms - Performance acceptable")
      }
    }
    stress1000.foreach { r =>
      if (r.avgMs > 5000) {
        println(s"  1000 nodes: ${r.avgMs.toLong}ms - CRITICAL: Consider limiting DAG size")
      } else if (r.avgMs > 2000) {
        println(s"  1000 nodes: ${r.avgMs.toLong}ms - Recommend warning at 1000 nodes")
      } else {
        println(s"  1000 nodes: ${r.avgMs.toLong}ms - Performance acceptable")
      }
    }

    println()
    allResults.size should be > 0
  }
}
