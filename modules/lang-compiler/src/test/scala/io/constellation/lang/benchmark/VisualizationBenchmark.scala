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
  lazy val stress100IR: IRProgram = compileToIR(TestFixtures.stressProgram100)

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
}
