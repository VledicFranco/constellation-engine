package io.constellation.lang.benchmark

import io.circe.*
import io.circe.syntax.*

import java.io.{File, PrintWriter}
import java.time.Instant
import scala.util.{Try, Using}

/** Reporter for benchmark results with JSON and console output */
object BenchmarkReporter {

  /** Metadata about the benchmark run */
  case class BenchmarkMetadata(
      timestamp: String,
      javaVersion: String,
      jvmName: String,
      scalaVersion: String,
      gitCommit: Option[String],
      hostname: String
  )

  /** Complete benchmark report */
  case class BenchmarkReport(
      metadata: BenchmarkMetadata,
      results: List[BenchmarkResult]
  )

  /** Gather current environment metadata */
  def gatherMetadata(): BenchmarkMetadata = {
    val gitCommit = Try {
      import scala.sys.process.*
      "git rev-parse --short HEAD".!!.trim
    }.toOption

    BenchmarkMetadata(
      timestamp = Instant.now().toString,
      javaVersion = System.getProperty("java.version"),
      jvmName = System.getProperty("java.vm.name"),
      scalaVersion = scala.util.Properties.versionNumberString,
      gitCommit = gitCommit,
      hostname = Try(java.net.InetAddress.getLocalHost.getHostName).getOrElse("unknown")
    )
  }

  /** Print console summary of benchmark results */
  def printSummary(results: List[BenchmarkResult]): Unit = {
    println()
    println("=" * 80)
    println("BENCHMARK RESULTS SUMMARY")
    println("=" * 80)

    // Group by input size
    val bySize = results.groupBy(_.inputSize)
    val sizes  = List("small", "medium", "large", "stress").filter(bySize.contains)

    sizes.foreach { size =>
      println()
      println(s"--- $size programs ---")
      bySize(size).foreach(r => println(r.toConsoleString))
    }

    // If no size groups, just print all
    if sizes.isEmpty && results.nonEmpty then {
      results.foreach(r => println(r.toConsoleString))
    }

    println()
    println("=" * 80)
  }

  /** Write JSON report to file
    *
    * @param results
    *   Benchmark results
    * @param filePath
    *   Output file path
    */
  def writeJsonReport(results: List[BenchmarkResult], filePath: String): Unit = {
    val metadata = gatherMetadata()
    val report   = BenchmarkReport(metadata, results)

    // Build JSON manually to ensure proper formatting
    val json = buildJson(report)

    Using(new PrintWriter(new File(filePath))) { writer =>
      writer.write(json)
    }.recover { case e =>
      System.err.println(s"Failed to write benchmark report: ${e.getMessage}")
    }
  }

  /** Build JSON string from report */
  private def buildJson(report: BenchmarkReport): String = {
    val sb = new StringBuilder

    sb.append("{\n")
    sb.append("  \"metadata\": {\n")
    sb.append(s"""    "timestamp": "${report.metadata.timestamp}",\n""")
    sb.append(s"""    "javaVersion": "${report.metadata.javaVersion}",\n""")
    sb.append(s"""    "jvmName": "${escapeJson(report.metadata.jvmName)}",\n""")
    sb.append(s"""    "scalaVersion": "${report.metadata.scalaVersion}",\n""")
    sb.append(s"""    "gitCommit": ${report.metadata.gitCommit
        .map(s => s""""$s"""")
        .getOrElse("null")},\n""")
    sb.append(s"""    "hostname": "${report.metadata.hostname}"\n""")
    sb.append("  },\n")
    sb.append("  \"results\": [\n")

    report.results.zipWithIndex.foreach { case (r, idx) =>
      sb.append("    {\n")
      sb.append(s"""      "name": "${r.name}",\n""")
      sb.append(s"""      "phase": "${r.phase}",\n""")
      sb.append(s"""      "inputSize": "${r.inputSize}",\n""")
      sb.append(s"""      "avgMs": ${formatDouble(r.avgMs)},\n""")
      sb.append(s"""      "minMs": ${formatDouble(r.minMs)},\n""")
      sb.append(s"""      "maxMs": ${formatDouble(r.maxMs)},\n""")
      sb.append(s"""      "stdDevMs": ${formatDouble(r.stdDevMs)},\n""")
      sb.append(s"""      "throughputOpsPerSec": ${formatDouble(r.throughputOpsPerSec)},\n""")
      sb.append(s"""      "iterations": ${r.iterations}\n""")
      sb.append("    }")
      if idx < report.results.length - 1 then sb.append(",")
      sb.append("\n")
    }

    sb.append("  ]\n")
    sb.append("}\n")

    sb.toString
  }

  private def formatDouble(d: Double): String = f"$d%.4f"

  private def escapeJson(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

  /** Generate timestamped report filename */
  def generateReportPath(basePath: String = "target"): String = {
    val timestamp = java.time.LocalDateTime
      .now()
      .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
    s"$basePath/benchmark-results-$timestamp.json"
  }

  /** Print quick summary statistics for a group of results */
  def printQuickStats(label: String, results: List[BenchmarkResult]): Unit = {
    if results.isEmpty then return

    val avgTotal = results.map(_.avgMs).sum
    val slowest  = results.maxBy(_.avgMs)
    val fastest  = results.minBy(_.avgMs)

    println()
    println(s"=== $label ===")
    println(f"Total pipeline time: $avgTotal%.2f ms")
    println(f"Slowest phase: ${slowest.phase} (${slowest.avgMs}%.2f ms)")
    println(f"Fastest phase: ${fastest.phase} (${fastest.avgMs}%.2f ms)")
  }

  /** Compare two sets of results (e.g., cache hit vs miss) */
  def printComparison(
      label1: String,
      results1: List[BenchmarkResult],
      label2: String,
      results2: List[BenchmarkResult]
  ): Unit = {
    println()
    println(s"=== Comparison: $label1 vs $label2 ===")

    // Find matching benchmarks by name
    results1.foreach { r1 =>
      results2.find(_.name == r1.name).foreach { r2 =>
        val speedup = r2.avgMs / r1.avgMs
        val pctDiff = ((r1.avgMs - r2.avgMs) / r2.avgMs) * 100
        println(
          f"${r1.name}%-40s : $label1=${r1.avgMs}%.2fms  $label2=${r2.avgMs}%.2fms  speedup=${speedup}%.1fx"
        )
      }
    }
  }
}
