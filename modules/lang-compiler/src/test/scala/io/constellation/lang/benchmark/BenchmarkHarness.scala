package io.constellation.lang.benchmark

import scala.collection.mutable

/** Timing utilities with warmup and measurement for performance benchmarks */
object BenchmarkHarness {

  /** Run warmup iterations to trigger JIT compilation */
  def warmup[A](iterations: Int)(op: => A): Unit = {
    var i = 0
    while (i < iterations) {
      op
      i += 1
    }
  }

  /** Measure operation performance
    *
    * @param name Benchmark name for reporting
    * @param iterations Number of measurement iterations
    * @param op Operation to measure
    * @return BenchmarkResult with timing statistics
    */
  def measure[A](name: String, iterations: Int, phase: String = "", inputSize: String = "")(
      op: => A
  ): BenchmarkResult = {
    val timings = mutable.ArrayBuffer[Double]()

    // Collect measurements
    var i = 0
    while (i < iterations) {
      val start = System.nanoTime()
      op
      val end = System.nanoTime()
      timings += (end - start) / 1e6 // Convert to milliseconds
      i += 1
    }

    // Calculate statistics
    val sorted = timings.sorted
    val avg    = timings.sum / timings.length
    val min    = sorted.head
    val max    = sorted.last

    // Standard deviation
    val variance = timings.map(t => math.pow(t - avg, 2)).sum / timings.length
    val stdDev   = math.sqrt(variance)

    // Throughput (operations per second)
    val throughput = if (avg > 0) 1000.0 / avg else 0.0

    BenchmarkResult(
      name = name,
      phase = phase,
      inputSize = inputSize,
      avgMs = avg,
      minMs = min,
      maxMs = max,
      stdDevMs = stdDev,
      throughputOpsPerSec = throughput,
      iterations = iterations,
      rawTimingsMs = timings.toList
    )
  }

  /** Measure with automatic warmup
    *
    * @param name Benchmark name
    * @param warmupIterations Warmup iterations before measuring
    * @param measureIterations Measurement iterations
    * @param phase Phase name (e.g., "parse", "typecheck")
    * @param inputSize Size category (e.g., "small", "medium", "large")
    * @param op Operation to measure
    */
  def measureWithWarmup[A](
      name: String,
      warmupIterations: Int = 5,
      measureIterations: Int = 20,
      phase: String = "",
      inputSize: String = ""
  )(op: => A): BenchmarkResult = {
    warmup(warmupIterations)(op)
    measure(name, measureIterations, phase, inputSize)(op)
  }

  /** Run a benchmark suite with multiple phases
    *
    * @param suiteName Name of the benchmark suite
    * @param warmup Warmup iterations
    * @param iterations Measurement iterations
    * @param phases List of (phaseName, operation) pairs
    */
  def runSuite(
      suiteName: String,
      warmup: Int = 5,
      iterations: Int = 20,
      inputSize: String = ""
  )(phases: List[(String, () => Any)]): List[BenchmarkResult] = {
    phases.map { case (phaseName, op) =>
      measureWithWarmup(
        name = s"${suiteName}_$phaseName",
        warmupIterations = warmup,
        measureIterations = iterations,
        phase = phaseName,
        inputSize = inputSize
      )(op())
    }
  }
}

/** Result of a benchmark measurement */
case class BenchmarkResult(
    name: String,
    phase: String,
    inputSize: String,
    avgMs: Double,
    minMs: Double,
    maxMs: Double,
    stdDevMs: Double,
    throughputOpsPerSec: Double,
    iterations: Int,
    rawTimingsMs: List[Double] = List.empty
) {

  /** Format result for console output */
  def toConsoleString: String = {
    val nameWidth = 40
    val paddedName = name.padTo(nameWidth, ' ').take(nameWidth)
    f"$paddedName : $avgMs%8.2fms (±$stdDevMs%.2f) [$minMs%.2f - $maxMs%.2f] $throughputOpsPerSec%.1f ops/s"
  }

  /** Convert to JSON-compatible map */
  def toMap: Map[String, Any] = Map(
    "name"                -> name,
    "phase"               -> phase,
    "inputSize"           -> inputSize,
    "avgMs"               -> avgMs,
    "minMs"               -> minMs,
    "maxMs"               -> maxMs,
    "stdDevMs"            -> stdDevMs,
    "throughputOpsPerSec" -> throughputOpsPerSec,
    "iterations"          -> iterations
  )
}
