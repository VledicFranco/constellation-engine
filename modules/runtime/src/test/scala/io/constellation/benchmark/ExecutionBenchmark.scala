package io.constellation.benchmark

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation._
import io.constellation.impl.ConstellationImpl
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.collection.mutable.ListBuffer

/** Result of a benchmark measurement */
case class ExecutionBenchmarkResult(
    name: String,
    phase: String,
    inputSize: String,
    avgMs: Double,
    minMs: Double,
    maxMs: Double,
    stdDevMs: Double,
    throughputOpsPerSec: Double,
    iterations: Int
) {
  def toConsoleString: String = {
    val nameWidth  = 40
    val paddedName = name.padTo(nameWidth, ' ').take(nameWidth)
    f"$paddedName : $avgMs%8.2fms (±$stdDevMs%.2f) [$minMs%.2f - $maxMs%.2f] $throughputOpsPerSec%.1f ops/s"
  }
}

/** Benchmarks for DAG execution performance
  *
  * Measures runtime execution time to complement compilation benchmarks.
  * Tests both full execution and step-through execution modes.
  *
  * Run with: sbt "runtime/testOnly *ExecutionBenchmark"
  */
class ExecutionBenchmark extends AnyFlatSpec with Matchers {

  // Configuration
  val WarmupIterations  = 5
  val MeasureIterations = 20

  // Collect results for report
  private val allResults = ListBuffer[ExecutionBenchmarkResult]()

  // -----------------------------------------------------------------
  // Test Modules
  // -----------------------------------------------------------------

  case class TextInput(text: String)
  case class TextOutput(result: String)

  case class IntInput(x: Long)
  case class IntOutput(result: Long)

  case class BoolInput(flag: Boolean)
  case class BoolOutput(result: Boolean)

  private def createUppercaseModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Uppercase", "Converts text to uppercase", 1, 0)
      .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toUpperCase))
      .build

  private def createLowercaseModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Lowercase", "Converts text to lowercase", 1, 0)
      .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.toLowerCase))
      .build

  private def createTrimModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Trim", "Trims whitespace", 1, 0)
      .implementationPure[TextInput, TextOutput](in => TextOutput(in.text.trim))
      .build

  private def createDoubleModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Double", "Doubles a number", 1, 0)
      .implementationPure[IntInput, IntOutput](in => IntOutput(in.x * 2))
      .build

  private def createNotModule(): Module.Uninitialized =
    ModuleBuilder
      .metadata("Not", "Negates a boolean", 1, 0)
      .implementationPure[BoolInput, BoolOutput](in => BoolOutput(!in.flag))
      .build

  // -----------------------------------------------------------------
  // DAG Builders
  // -----------------------------------------------------------------

  /** Create a simple linear DAG: input -> module -> output */
  private def createSimpleDag(): (DagSpec, Map[UUID, Module.Uninitialized]) = {
    val moduleId     = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("SimpleDag"),
      modules = Map(
        moduleId -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "Test", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId  -> DataNodeSpec("input", Map(inputDataId -> "input", moduleId -> "text"), CType.CString),
        outputDataId -> DataNodeSpec("output", Map(moduleId -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId)),
      outEdges = Set((moduleId, outputDataId)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputDataId)
    )

    (dag, Map(moduleId -> createUppercaseModule()))
  }

  /** Create a medium-complexity DAG: input -> M1 -> M2 -> M3 -> output */
  private def createMediumDag(): (DagSpec, Map[UUID, Module.Uninitialized]) = {
    val moduleId1    = UUID.randomUUID()
    val moduleId2    = UUID.randomUUID()
    val moduleId3    = UUID.randomUUID()
    val inputDataId  = UUID.randomUUID()
    val midDataId1   = UUID.randomUUID()
    val midDataId2   = UUID.randomUUID()
    val outputDataId = UUID.randomUUID()

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("MediumDag"),
      modules = Map(
        moduleId1 -> ModuleNodeSpec(
          metadata = ComponentMetadata("Uppercase", "M1", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        ),
        moduleId2 -> ModuleNodeSpec(
          metadata = ComponentMetadata("Trim", "M2", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        ),
        moduleId3 -> ModuleNodeSpec(
          metadata = ComponentMetadata("Lowercase", "M3", List.empty, 1, 0),
          consumes = Map("text" -> CType.CString),
          produces = Map("result" -> CType.CString)
        )
      ),
      data = Map(
        inputDataId  -> DataNodeSpec("input", Map(inputDataId -> "input", moduleId1 -> "text"), CType.CString),
        midDataId1   -> DataNodeSpec("mid1", Map(moduleId1 -> "result", moduleId2 -> "text"), CType.CString),
        midDataId2   -> DataNodeSpec("mid2", Map(moduleId2 -> "result", moduleId3 -> "text"), CType.CString),
        outputDataId -> DataNodeSpec("output", Map(moduleId3 -> "result"), CType.CString)
      ),
      inEdges = Set((inputDataId, moduleId1), (midDataId1, moduleId2), (midDataId2, moduleId3)),
      outEdges = Set((moduleId1, midDataId1), (moduleId2, midDataId2), (moduleId3, outputDataId)),
      declaredOutputs = List("output"),
      outputBindings = Map("output" -> outputDataId)
    )

    val modules = Map(
      moduleId1 -> createUppercaseModule(),
      moduleId2 -> createTrimModule(),
      moduleId3 -> createLowercaseModule()
    )

    (dag, modules)
  }

  /** Create a large DAG with parallel and sequential paths */
  private def createLargeDag(): (DagSpec, Map[UUID, Module.Uninitialized]) = {
    val moduleIds = (1 to 10).map(_ => UUID.randomUUID()).toList
    val dataIds   = (1 to 11).map(_ => UUID.randomUUID()).toList

    // Create modules: alternating Uppercase and Lowercase for variety
    val moduleSpecs = moduleIds.zipWithIndex.map { case (id, idx) =>
      val name = if (idx % 2 == 0) "Uppercase" else "Lowercase"
      id -> ModuleNodeSpec(
        metadata = ComponentMetadata(name, s"M$idx", List.empty, 1, 0),
        consumes = Map("text" -> CType.CString),
        produces = Map("result" -> CType.CString)
      )
    }.toMap

    // Create data nodes connecting modules sequentially
    val dataSpecs = dataIds.zipWithIndex.map { case (id, idx) =>
      val portMap = if (idx == 0) {
        // First data node is input, connects to first module
        Map(id -> "input", moduleIds.head -> "text")
      } else if (idx < moduleIds.length) {
        // Middle nodes connect previous module output to next module input
        Map(moduleIds(idx - 1) -> "result", moduleIds(idx) -> "text")
      } else {
        // Last node is just output from last module
        Map(moduleIds.last -> "result")
      }
      id -> DataNodeSpec(s"data$idx", portMap, CType.CString)
    }.toMap

    // Build edges
    val inEdges = dataIds.init.zipWithIndex.map { case (dataId, idx) =>
      (dataId, moduleIds(idx))
    }.toSet

    val outEdges = moduleIds.zipWithIndex.map { case (moduleId, idx) =>
      (moduleId, dataIds(idx + 1))
    }.toSet

    val dag = DagSpec(
      metadata = ComponentMetadata.empty("LargeDag"),
      modules = moduleSpecs,
      data = dataSpecs,
      inEdges = inEdges,
      outEdges = outEdges,
      declaredOutputs = List("data10"),
      outputBindings = Map("data10" -> dataIds.last)
    )

    val modules = moduleIds.zipWithIndex.map { case (id, idx) =>
      if (idx % 2 == 0) id -> createUppercaseModule()
      else id -> createLowercaseModule()
    }.toMap

    (dag, modules)
  }

  /** Create a stress test DAG with N sequential modules */
  private def createStressDag(nodeCount: Int): (DagSpec, Map[UUID, Module.Uninitialized]) = {
    val moduleIds = (1 to nodeCount).map(_ => UUID.randomUUID()).toList
    val dataIds   = (1 to (nodeCount + 1)).map(_ => UUID.randomUUID()).toList

    val moduleSpecs = moduleIds.zipWithIndex.map { case (id, idx) =>
      val name = if (idx % 2 == 0) "Uppercase" else "Lowercase"
      id -> ModuleNodeSpec(
        metadata = ComponentMetadata(name, s"M$idx", List.empty, 1, 0),
        consumes = Map("text" -> CType.CString),
        produces = Map("result" -> CType.CString)
      )
    }.toMap

    val dataSpecs = dataIds.zipWithIndex.map { case (id, idx) =>
      val portMap = if (idx == 0) {
        Map(id -> "input", moduleIds.head -> "text")
      } else if (idx < moduleIds.length) {
        Map(moduleIds(idx - 1) -> "result", moduleIds(idx) -> "text")
      } else {
        Map(moduleIds.last -> "result")
      }
      id -> DataNodeSpec(s"data$idx", portMap, CType.CString)
    }.toMap

    val inEdges = dataIds.init.zipWithIndex.map { case (dataId, idx) =>
      (dataId, moduleIds(idx))
    }.toSet

    val outEdges = moduleIds.zipWithIndex.map { case (moduleId, idx) =>
      (moduleId, dataIds(idx + 1))
    }.toSet

    val dag = DagSpec(
      metadata = ComponentMetadata.empty(s"StressDag$nodeCount"),
      modules = moduleSpecs,
      data = dataSpecs,
      inEdges = inEdges,
      outEdges = outEdges,
      declaredOutputs = List(s"data$nodeCount"),
      outputBindings = Map(s"data$nodeCount" -> dataIds.last)
    )

    val modules = moduleIds.zipWithIndex.map { case (id, idx) =>
      if (idx % 2 == 0) id -> createUppercaseModule()
      else id -> createLowercaseModule()
    }.toMap

    (dag, modules)
  }

  // -----------------------------------------------------------------
  // Measurement Utilities
  // -----------------------------------------------------------------

  /** Measure operation with warmup */
  private def measureWithWarmup(
      name: String,
      phase: String,
      inputSize: String
  )(op: => Unit): ExecutionBenchmarkResult = {
    // Warmup
    (0 until WarmupIterations).foreach(_ => op)

    // Measure
    val timings = (0 until MeasureIterations).map { _ =>
      val start = System.nanoTime()
      op
      val end = System.nanoTime()
      (end - start) / 1e6 // ms
    }

    val avg      = timings.sum / timings.length
    val sorted   = timings.sorted
    val min      = sorted.head
    val max      = sorted.last
    val variance = timings.map(t => math.pow(t - avg, 2)).sum / timings.length
    val stdDev   = math.sqrt(variance)

    ExecutionBenchmarkResult(
      name = name,
      phase = phase,
      inputSize = inputSize,
      avgMs = avg,
      minMs = min,
      maxMs = max,
      stdDevMs = stdDev,
      throughputOpsPerSec = if (avg > 0) 1000.0 / avg else 0.0,
      iterations = MeasureIterations
    )
  }

  // -----------------------------------------------------------------
  // Full Execution Benchmarks
  // -----------------------------------------------------------------

  "Full execution benchmark" should "measure execution for simple DAGs" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("input" -> CValue.CString("hello world"))

    val result = measureWithWarmup("execute_simple", "execute", "simple") {
      val session = SteppedExecution
        .createSession("bench", dag, Map.empty, modules, inputs)
        .unsafeRunSync()
      val initialized = SteppedExecution.initializeRuntime(session).unsafeRunSync()
      SteppedExecution.executeToCompletion(initialized).unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 50.0 // Target: <50ms for simple DAG
  }

  it should "measure execution for medium DAGs" in {
    val (dag, modules) = createMediumDag()
    val inputs         = Map("input" -> CValue.CString("  Hello World  "))

    val result = measureWithWarmup("execute_medium", "execute", "medium") {
      val session = SteppedExecution
        .createSession("bench", dag, Map.empty, modules, inputs)
        .unsafeRunSync()
      val initialized = SteppedExecution.initializeRuntime(session).unsafeRunSync()
      SteppedExecution.executeToCompletion(initialized).unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0 // Target: <100ms for medium DAG
  }

  it should "measure execution for large DAGs" in {
    val (dag, modules) = createLargeDag()
    val inputs         = Map("input" -> CValue.CString("test input string"))

    val result = measureWithWarmup("execute_large", "execute", "large") {
      val session = SteppedExecution
        .createSession("bench", dag, Map.empty, modules, inputs)
        .unsafeRunSync()
      val initialized = SteppedExecution.initializeRuntime(session).unsafeRunSync()
      SteppedExecution.executeToCompletion(initialized).unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 200.0 // Target: <200ms for large DAG
  }

  // -----------------------------------------------------------------
  // Step-through Execution Benchmarks
  // -----------------------------------------------------------------

  "Step-through benchmark" should "measure per-step latency for simple DAGs" in {
    val (dag, modules) = createSimpleDag()
    val inputs         = Map("input" -> CValue.CString("hello"))

    var stepTimings = ListBuffer[Double]()

    (0 until MeasureIterations).foreach { _ =>
      val session = SteppedExecution
        .createSession("bench", dag, Map.empty, modules, inputs)
        .unsafeRunSync()

      val initializedSession = SteppedExecution.initializeRuntime(session).unsafeRunSync()

      var currentSession = initializedSession
      var done           = false
      while (!done) {
        val start                    = System.nanoTime()
        val (newSession, isComplete) = SteppedExecution.executeNextBatch(currentSession).unsafeRunSync()
        val end                      = System.nanoTime()

        stepTimings += (end - start) / 1e6
        currentSession = newSession
        done = isComplete
      }
    }

    val avgStep     = stepTimings.sum / stepTimings.size
    val sortedSteps = stepTimings.sorted
    val minStep     = sortedSteps.head
    val maxStep     = sortedSteps.last
    val variance    = stepTimings.map(t => math.pow(t - avgStep, 2)).sum / stepTimings.size
    val stdDev      = math.sqrt(variance)

    val result = ExecutionBenchmarkResult(
      name = "step_simple",
      phase = "step",
      inputSize = "simple",
      avgMs = avgStep,
      minMs = minStep,
      maxMs = maxStep,
      stdDevMs = stdDev,
      throughputOpsPerSec = if (avgStep > 0) 1000.0 / avgStep else 0.0,
      iterations = stepTimings.size
    )

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0 // Target: <100ms per step
  }

  it should "measure per-step latency for medium DAGs" in {
    val (dag, modules) = createMediumDag()
    val inputs         = Map("input" -> CValue.CString("  Hello  "))

    var stepTimings = ListBuffer[Double]()

    (0 until MeasureIterations).foreach { _ =>
      val session = SteppedExecution
        .createSession("bench", dag, Map.empty, modules, inputs)
        .unsafeRunSync()

      val initializedSession = SteppedExecution.initializeRuntime(session).unsafeRunSync()

      var currentSession = initializedSession
      var done           = false
      while (!done) {
        val start                    = System.nanoTime()
        val (newSession, isComplete) = SteppedExecution.executeNextBatch(currentSession).unsafeRunSync()
        val end                      = System.nanoTime()

        stepTimings += (end - start) / 1e6
        currentSession = newSession
        done = isComplete
      }
    }

    val avgStep     = stepTimings.sum / stepTimings.size
    val sortedSteps = stepTimings.sorted

    val result = ExecutionBenchmarkResult(
      name = "step_medium",
      phase = "step",
      inputSize = "medium",
      avgMs = avgStep,
      minMs = sortedSteps.head,
      maxMs = sortedSteps.last,
      stdDevMs = math.sqrt(stepTimings.map(t => math.pow(t - avgStep, 2)).sum / stepTimings.size),
      throughputOpsPerSec = if (avgStep > 0) 1000.0 / avgStep else 0.0,
      iterations = stepTimings.size
    )

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0 // Target: <100ms per step
  }

  // -----------------------------------------------------------------
  // Stress Test Execution
  // -----------------------------------------------------------------

  "Stress execution benchmark" should "measure execution scaling for 50 nodes" in {
    val (dag, modules) = createStressDag(50)
    val inputs         = Map("input" -> CValue.CString("stress test"))

    val result = measureWithWarmup("execute_stress50", "execute", "stress50") {
      val session = SteppedExecution
        .createSession("bench", dag, Map.empty, modules, inputs)
        .unsafeRunSync()
      val initialized = SteppedExecution.initializeRuntime(session).unsafeRunSync()
      SteppedExecution.executeToCompletion(initialized).unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 500.0 // Target: <500ms for 50 nodes
  }

  it should "measure execution scaling for 100 nodes" in {
    val (dag, modules) = createStressDag(100)
    val inputs         = Map("input" -> CValue.CString("stress test"))

    val result = measureWithWarmup("execute_stress100", "execute", "stress100") {
      val session = SteppedExecution
        .createSession("bench", dag, Map.empty, modules, inputs)
        .unsafeRunSync()
      val initialized = SteppedExecution.initializeRuntime(session).unsafeRunSync()
      SteppedExecution.executeToCompletion(initialized).unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    // Calculate ms per node
    val msPerNode = result.avgMs / 100
    println(f"  Time per node: $msPerNode%.4f ms")

    result.avgMs should be < 1000.0 // Target: <1s for 100 nodes
  }

  // -----------------------------------------------------------------
  // Report
  // -----------------------------------------------------------------

  "Execution benchmark report" should "summarize findings" in {
    println("\n" + "=" * 80)
    println("EXECUTION BENCHMARK SUMMARY")
    println("=" * 80)

    // Group by phase
    val byPhase = allResults.groupBy(_.phase)
    List("execute", "step").foreach { phase =>
      byPhase.get(phase).foreach { results =>
        println(s"\n--- $phase ---")
        results.foreach(r => println(r.toConsoleString))
      }
    }

    // Scaling analysis
    println("\n" + "=" * 80)
    println("SCALING ANALYSIS")
    println("=" * 80)

    val executeResults = allResults.filter(_.phase == "execute").toList
    val simple         = executeResults.find(_.inputSize == "simple").map(_.avgMs)
    val large          = executeResults.find(_.inputSize == "large").map(_.avgMs)
    val stress50       = executeResults.find(_.inputSize == "stress50").map(_.avgMs)
    val stress100      = executeResults.find(_.inputSize == "stress100").map(_.avgMs)

    (simple, large) match {
      case (Some(s), Some(l)) =>
        println(f"\nFull Execution Scaling:")
        println(f"  Simple -> Large: ${l / s}%.1fx time increase")
      case _ => ()
    }

    (stress50, stress100) match {
      case (Some(s50), Some(s100)) =>
        println(f"  Stress50 -> Stress100: ${s100 / s50}%.1fx time increase (2x nodes)")
        val scaling = (s100 / s50) / 2.0
        val scalingType =
          if (scaling < 0.6) "sub-linear"
          else if (scaling < 1.1) "linear"
          else if (scaling < 1.5) "slightly super-linear"
          else "super-linear (warning!)"
        println(s"  Scaling characteristic: $scalingType ($scaling ratio)")
      case _ => ()
    }

    // Time per node
    println("\nTime per Node Estimates:")
    stress50.foreach { ms =>
      println(f"  From 50-node stress test: ${ms / 50.0}%.4f ms/node")
    }
    stress100.foreach { ms =>
      println(f"  From 100-node stress test: ${ms / 100.0}%.4f ms/node")
    }

    // Performance targets summary
    println("\n" + "=" * 80)
    println("PERFORMANCE TARGETS")
    println("=" * 80)
    println(
      """
      |Operation           Target     Note
      |--------------------------------------------------
      |execute_simple      <50ms      Interactive responsiveness
      |execute_medium      <100ms     Acceptable latency
      |execute_large       <200ms     Complex pipelines (10 nodes)
      |execute_stress50    <500ms     Stress test limit
      |execute_stress100   <1000ms    Stress test limit
      |step (per batch)    <100ms     Debugging responsiveness
      |""".stripMargin
    )

    allResults.size should be > 0
  }
}
