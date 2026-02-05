package io.constellation.lang.benchmark

import io.constellation.lang.*
import io.constellation.lang.compiler.*
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.semantic.TypeChecker

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Benchmark for analyzing parallel compilation potential
  *
  * This benchmark measures:
  *   1. Current sequential compilation performance 2. IR structure analysis (layers, parallelism
  *      potential) 3. Overhead of layer computation
  *
  * The topological layers analysis shows how much potential parallelism exists in a program's
  * dependency graph.
  */
class ParallelCompilationBenchmark extends AnyFlatSpec with Matchers {

  val WarmupIterations  = 5
  val MeasureIterations = 20

  /** Compile to IR for analysis */
  private def compileToIR(source: String): Either[List[_], IRPipeline] =
    for {
      program <- ConstellationParser.parse(source).left.map(List(_))
      typed   <- TypeChecker.check(program, io.constellation.lang.semantic.FunctionRegistry.empty)
    } yield IRGenerator.generate(typed)

  "Topological layers" should "be computed efficiently" in {
    // First compile to IR
    val irResult = compileToIR(TestFixtures.largeProgram)
    irResult.isRight shouldBe true
    val ir = irResult.toOption.get

    // Measure layer computation
    val result = BenchmarkHarness.measureWithWarmup(
      name = "topological_layers_large",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "analysis",
      inputSize = "large"
    ) {
      ir.topologicalLayers
    }

    println(result.toConsoleString)
    println(s"  Nodes: ${ir.nodes.size}")
    println(s"  Layers: ${ir.criticalPathLength}")
    println(s"  Max parallelism: ${ir.maxParallelism}")

    // Layer computation should be fast (sub-millisecond)
    result.avgMs should be < 5.0
  }

  "Parallelism analysis" should "show potential for large programs" in {
    case class ParallelismStats(
        name: String,
        nodeCount: Int,
        layerCount: Int,
        maxParallelism: Int,
        avgLayerSize: Double,
        sequentialOverhead: Double // ratio of layers to nodes (lower = more parallel)
    )

    def analyzeProgram(name: String, source: String): Option[ParallelismStats] =
      compileToIR(source).toOption.map { ir =>
        val layers         = ir.topologicalLayers
        val nodeCount      = ir.nodes.size
        val layerCount     = layers.size
        val avgLayerSize   = if layerCount > 0 then nodeCount.toDouble / layerCount else 0.0
        val maxParallelism = ir.maxParallelism
        val seqOverhead    = if nodeCount > 0 then layerCount.toDouble / nodeCount else 1.0

        ParallelismStats(name, nodeCount, layerCount, maxParallelism, avgLayerSize, seqOverhead)
      }

    val stats = List(
      analyzeProgram("small", TestFixtures.smallProgram),
      analyzeProgram("medium", TestFixtures.mediumProgram),
      analyzeProgram("large", TestFixtures.largeProgram),
      analyzeProgram("stress_100", TestFixtures.stressProgram100),
      analyzeProgram("stress_200", TestFixtures.stressProgram200)
    ).flatten

    println("\n=== Parallelism Analysis ===")
    println(
      f"${"Pipeline"}%-15s ${"Nodes"}%8s ${"Layers"}%8s ${"MaxPar"}%8s ${"AvgLayer"}%10s ${"SeqRatio"}%10s"
    )
    println("-" * 70)

    stats.foreach { s =>
      println(
        f"${s.name}%-15s ${s.nodeCount}%8d ${s.layerCount}%8d ${s.maxParallelism}%8d ${s.avgLayerSize}%10.2f ${s.sequentialOverhead}%10.2f"
      )
    }

    // Verify we have meaningful parallelism potential
    val largeStats = stats.find(_.name == "large")
    largeStats shouldBe defined
    largeStats.get.maxParallelism should be > 1 // Some parallel potential
  }

  "Full pipeline compilation" should "be measured for baseline" in {
    val compiler = LangCompiler.empty

    // Measure full pipeline for each fixture size
    val results = TestFixtures.standardFixtures.map { fixture =>
      val result = BenchmarkHarness.measureWithWarmup(
        name = s"full_pipeline_${fixture.size}",
        warmupIterations = WarmupIterations,
        measureIterations = MeasureIterations,
        phase = "full_pipeline",
        inputSize = fixture.size
      ) {
        compiler.compile(fixture.source, "test-dag")
      }
      (fixture.size, result)
    }

    println("\n=== Full Pipeline Baseline ===")
    results.foreach { case (size, result) =>
      println(result.toConsoleString)
    }

    // Verify performance targets
    val smallResult = results.find(_._1 == "small").map(_._2)
    smallResult shouldBe defined
    smallResult.get.avgMs should be < 50.0 // Target: <50ms for small

    val mediumResult = results.find(_._1 == "medium").map(_._2)
    mediumResult shouldBe defined
    mediumResult.get.avgMs should be < 200.0 // Target: <200ms for medium
  }

  "DAG compilation phase" should "show layer-aware processing potential" in {
    // Parse and type-check first
    val irResult = compileToIR(TestFixtures.largeProgram)
    irResult.isRight shouldBe true
    val ir = irResult.toOption.get

    // Measure sequential topological order
    val seqResult = BenchmarkHarness.measureWithWarmup(
      name = "dag_compile_sequential",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "dag_compile",
      inputSize = "large"
    ) {
      DagCompiler.compile(ir, "test-dag", Map.empty)
    }

    // Measure just the layer computation overhead
    val layerResult = BenchmarkHarness.measureWithWarmup(
      name = "layer_computation",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "analysis",
      inputSize = "large"
    ) {
      ir.topologicalLayers
    }

    val overheadPercent = layerResult.avgMs / seqResult.avgMs * 100
    println("\n=== DAG Compilation Analysis ===")
    println(seqResult.toConsoleString)
    println(layerResult.toConsoleString)
    println(f"  Layer computation overhead: $overheadPercent%.1f%% of DAG compile time")

    // Layer computation should be fast in absolute terms
    // Note: Relative comparison is unreliable due to JVM warmup variance
    // when running as part of a larger test suite
    layerResult.avgMs should be < 10.0 // Target: <10ms for layer computation
    seqResult.avgMs should be < 10.0   // Target: <10ms for DAG compilation
  }

  "Stress test" should "maintain reasonable performance" in {
    val compiler = LangCompiler.empty

    // Test stress_200
    val result = BenchmarkHarness.measureWithWarmup(
      name = "stress_200_full_pipeline",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "full_pipeline",
      inputSize = "stress"
    ) {
      compiler.compile(TestFixtures.stressProgram200, "stress-dag")
    }

    println("\n=== Stress Test (200 ops) ===")
    println(result.toConsoleString)

    // Analyze parallelism
    compileToIR(TestFixtures.stressProgram200).foreach { ir =>
      println(s"  Nodes: ${ir.nodes.size}")
      println(s"  Layers: ${ir.criticalPathLength}")
      println(s"  Max parallelism: ${ir.maxParallelism}")
    }

    // Stress test should complete in reasonable time
    result.avgMs should be < 500.0 // Target: <500ms for 200 ops
  }
}
