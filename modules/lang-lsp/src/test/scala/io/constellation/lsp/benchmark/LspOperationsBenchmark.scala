package io.constellation.lsp.benchmark

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.syntax.*
import io.constellation.*
import io.constellation.impl.ConstellationImpl
import io.constellation.lang.LangCompiler
import io.constellation.lang.semantic.{FunctionSignature, SemanticType}
import io.constellation.lsp.ConstellationLanguageServer
import io.constellation.lsp.protocol.JsonRpc.*
import io.constellation.lsp.protocol.JsonRpc.RequestId.*
import io.constellation.lsp.protocol.LspMessages.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

/** Benchmarks for LSP operations
  *
  * Run with: sbt "langLsp/testOnly *LspOperationsBenchmark"
  *
  * Tests:
  *   - Document validation (didOpen, didChange notifications)
  *   - Code completion requests
  *   - DAG visualization requests
  */
class LspOperationsBenchmark extends AnyFlatSpec with Matchers {

  // Benchmark configuration
  val WarmupIterations  = 3
  val MeasureIterations = 15

  // Collect results
  private val allResults = ListBuffer[BenchmarkResult]()

  // Test fixtures
  val smallSource: String =
    """in text: String
      |result = Uppercase(text)
      |out result
      |""".stripMargin

  val mediumSource: String =
    """type TextInput = {
      |  content: String,
      |  maxLength: Int,
      |  trim: Boolean
      |}
      |
      |in input: TextInput
      |in prefix: String
      |
      |rawContent = input.content
      |trimmed = Trim(rawContent)
      |processed = if (input.trim) trimmed else rawContent
      |
      |wordCount = WordCount(processed)
      |length = TextLength(processed)
      |
      |isLong = length > input.maxLength
      |upper = Uppercase(processed)
      |lower = Lowercase(processed)
      |
      |finalText = if (isLong) lower else upper
      |withPrefix = Concat(prefix, finalText)
      |
      |out finalText
      |out wordCount
      |out withPrefix
      |""".stripMargin

  val largeSource: String =
    """type CompanyInfo = {
      |  name: String,
      |  industry: String,
      |  employeeCount: Int,
      |  annualRevenue: Int
      |}
      |
      |type EngagementData = {
      |  websiteVisits: Int,
      |  emailOpens: Int,
      |  contentDownloads: Int,
      |  description: String
      |}
      |
      |in company: CompanyInfo
      |in engagement: EngagementData
      |in industryKeywords: String
      |in minScoreThreshold: Int
      |
      |descriptionText = Trim(engagement.description)
      |normalizedDesc = Lowercase(descriptionText)
      |descWordCount = WordCount(normalizedDesc)
      |descLength = TextLength(normalizedDesc)
      |keywordsLower = Lowercase(industryKeywords)
      |hasIndustryMatch = Contains(normalizedDesc, keywordsLower)
      |
      |isLargeCompany = company.employeeCount > 500
      |isMediumCompany = company.employeeCount >= 50 and company.employeeCount <= 500
      |isSmallCompany = company.employeeCount < 50
      |
      |companySizeScore = if (isLargeCompany) 100 else if (isMediumCompany) 70 else 30
      |
      |revenueBase = company.annualRevenue / 10000
      |revenueScore = if (revenueBase > 100) 100 else revenueBase
      |
      |totalEngagement = engagement.websiteVisits + engagement.emailOpens + engagement.contentDownloads
      |hasHighEngagement = totalEngagement > 10
      |hasMediumEngagement = totalEngagement >= 5 and totalEngagement <= 10
      |engagementScore = if (hasHighEngagement) 100 else if (hasMediumEngagement) 60 else 20
      |
      |hasDetailedDescription = descWordCount > 50 and descLength > 200
      |hasModerateDescription = descWordCount >= 20 and descWordCount <= 50
      |textQualityScore = if (hasDetailedDescription) 100 else if (hasModerateDescription) 60 else 25
      |
      |meetsRevenueThreshold = revenueScore >= 50
      |meetsEngagementThreshold = engagementScore >= 60
      |isQualified = meetsRevenueThreshold and meetsEngagementThreshold
      |isHighPriority = isLargeCompany or (hasIndustryMatch and hasHighEngagement)
      |isDisqualified = isSmallCompany and not hasHighEngagement and not hasMediumEngagement
      |
      |weightedCompanyScore = companySizeScore / 4
      |weightedRevenueScore = revenueScore * 3 / 10
      |weightedEngagementScore = engagementScore / 4
      |weightedTextScore = textQualityScore / 5
      |
      |rawTotalScore = weightedCompanyScore + weightedRevenueScore + weightedEngagementScore + weightedTextScore
      |industryBonus = if (hasIndustryMatch) 15 else 0
      |adjustedScore = rawTotalScore + industryBonus
      |finalScore = if (adjustedScore > 100) 100 else adjustedScore
      |
      |isHotLead = finalScore >= 80
      |isWarmLead = finalScore >= 50 and finalScore < 80
      |isColdLead = finalScore < 50
      |
      |meetsMinimum = finalScore >= minScoreThreshold
      |scoreDifference = finalScore - minScoreThreshold
      |
      |displayName = Uppercase(company.name)
      |normalizedIndustry = Uppercase(company.industry)
      |
      |conditionalBonus = industryBonus when hasIndustryMatch
      |adjustmentFactor = conditionalBonus ?? 0
      |
      |out displayName
      |out normalizedIndustry
      |out companySizeScore
      |out revenueScore
      |out engagementScore
      |out textQualityScore
      |out isQualified
      |out isHighPriority
      |out finalScore
      |out isHotLead
      |out meetsMinimum
      |""".stripMargin

  // Test module definitions
  case class TestInput(text: String)
  case class TestOutput(result: String)

  private def createTestServer(): IO[ConstellationLanguageServer] = {
    val uppercaseModule = ModuleBuilder
      .metadata("Uppercase", "Converts text to uppercase", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.text.toUpperCase))
      .build

    val lowercaseModule = ModuleBuilder
      .metadata("Lowercase", "Converts text to lowercase", 1, 0)
      .implementationPure[TestInput, TestOutput](in => TestOutput(in.text.toLowerCase))
      .build

    for {
      constellation <- ConstellationImpl.init
      _             <- constellation.setModule(uppercaseModule)
      _             <- constellation.setModule(lowercaseModule)

      compiler = LangCompiler.builder
        .withModule(
          "Uppercase",
          uppercaseModule,
          List("text" -> SemanticType.SString),
          SemanticType.SString
        )
        .withModule(
          "Lowercase",
          lowercaseModule,
          List("text" -> SemanticType.SString),
          SemanticType.SString
        )
        .withCaching() // Enable caching for realistic LSP performance
        .build

      server <- ConstellationLanguageServer.create(
        constellation,
        compiler,
        _ => IO.unit // No-op diagnostics publisher
      )
    } yield server
  }

  // -----------------------------------------------------------------
  // Document Open (didOpen) Benchmarks
  // -----------------------------------------------------------------

  "Document open benchmark" should "measure didOpen notification handling for small documents" in {
    val server = createTestServer().unsafeRunSync()
    val uri    = "file:///test/small.cst"

    val result = measureLspOperation(
      name = "didOpen_small",
      phase = "didOpen",
      inputSize = "small"
    ) {
      server
        .handleNotification(
          Notification(
            method = "textDocument/didOpen",
            params = Some(
              Json.obj(
                "textDocument" -> Json.obj(
                  "uri"        -> Json.fromString(uri),
                  "languageId" -> Json.fromString("constellation"),
                  "version"    -> Json.fromInt(1),
                  "text"       -> Json.fromString(smallSource)
                )
              )
            )
          )
        )
        .unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0 // Target: <100ms
  }

  it should "measure didOpen notification handling for medium documents" in {
    val server = createTestServer().unsafeRunSync()
    val uri    = "file:///test/medium.cst"

    val result = measureLspOperation(
      name = "didOpen_medium",
      phase = "didOpen",
      inputSize = "medium"
    ) {
      server
        .handleNotification(
          Notification(
            method = "textDocument/didOpen",
            params = Some(
              Json.obj(
                "textDocument" -> Json.obj(
                  "uri"        -> Json.fromString(uri),
                  "languageId" -> Json.fromString("constellation"),
                  "version"    -> Json.fromInt(1),
                  "text"       -> Json.fromString(mediumSource)
                )
              )
            )
          )
        )
        .unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 200.0
  }

  it should "measure didOpen notification handling for large documents" in {
    val server = createTestServer().unsafeRunSync()
    val uri    = "file:///test/large.cst"

    val result = measureLspOperation(
      name = "didOpen_large",
      phase = "didOpen",
      inputSize = "large"
    ) {
      server
        .handleNotification(
          Notification(
            method = "textDocument/didOpen",
            params = Some(
              Json.obj(
                "textDocument" -> Json.obj(
                  "uri"        -> Json.fromString(uri),
                  "languageId" -> Json.fromString("constellation"),
                  "version"    -> Json.fromInt(1),
                  "text"       -> Json.fromString(largeSource)
                )
              )
            )
          )
        )
        .unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 500.0
  }

  // -----------------------------------------------------------------
  // Document Change (didChange) Benchmarks
  // -----------------------------------------------------------------

  "Document change benchmark" should "measure didChange notification for incremental edits" in {
    val server = createTestServer().unsafeRunSync()
    val uri    = "file:///test/edit.cst"

    // Open document first
    server
      .handleNotification(
        Notification(
          method = "textDocument/didOpen",
          params = Some(
            Json.obj(
              "textDocument" -> Json.obj(
                "uri"        -> Json.fromString(uri),
                "languageId" -> Json.fromString("constellation"),
                "version"    -> Json.fromInt(1),
                "text"       -> Json.fromString(mediumSource)
              )
            )
          )
        )
      )
      .unsafeRunSync()

    var version = 1
    val result = measureLspOperation(
      name = "didChange_incremental",
      phase = "didChange",
      inputSize = "medium"
    ) {
      version += 1
      // Simulate typing - add a comment line
      val modifiedSource = mediumSource + s"\n# edit $version"
      server
        .handleNotification(
          Notification(
            method = "textDocument/didChange",
            params = Some(
              Json.obj(
                "textDocument" -> Json.obj(
                  "uri"     -> Json.fromString(uri),
                  "version" -> Json.fromInt(version)
                ),
                "contentChanges" -> Json.arr(
                  Json.obj("text" -> Json.fromString(modifiedSource))
                )
              )
            )
          )
        )
        .unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    // This is critical for responsive typing
    result.avgMs should be < 100.0 // Target: <100ms per keystroke
  }

  // -----------------------------------------------------------------
  // Completion Request Benchmarks
  // -----------------------------------------------------------------

  "Completion benchmark" should "measure completion response time for small documents" in {
    val server = createTestServer().unsafeRunSync()
    val uri    = "file:///test/complete.cst"

    // Open document
    server
      .handleNotification(
        Notification(
          method = "textDocument/didOpen",
          params = Some(
            Json.obj(
              "textDocument" -> Json.obj(
                "uri"        -> Json.fromString(uri),
                "languageId" -> Json.fromString("constellation"),
                "version"    -> Json.fromInt(1),
                "text"       -> Json.fromString(smallSource)
              )
            )
          )
        )
      )
      .unsafeRunSync()

    val result = measureLspOperation(
      name = "completion_small",
      phase = "completion",
      inputSize = "small"
    ) {
      server
        .handleRequest(
          Request(
            id = StringId("comp"),
            method = "textDocument/completion",
            params = Some(
              Json.obj(
                "textDocument" -> Json.obj("uri" -> Json.fromString(uri)),
                "position" -> Json.obj("line" -> Json.fromInt(1), "character" -> Json.fromInt(10))
              )
            )
          )
        )
        .unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    // Completion must be fast for responsive typing
    result.avgMs should be < 50.0 // Target: <50ms
  }

  it should "measure completion response time for large documents" in {
    val server = createTestServer().unsafeRunSync()
    val uri    = "file:///test/complete-large.cst"

    // Open document
    server
      .handleNotification(
        Notification(
          method = "textDocument/didOpen",
          params = Some(
            Json.obj(
              "textDocument" -> Json.obj(
                "uri"        -> Json.fromString(uri),
                "languageId" -> Json.fromString("constellation"),
                "version"    -> Json.fromInt(1),
                "text"       -> Json.fromString(largeSource)
              )
            )
          )
        )
      )
      .unsafeRunSync()

    val result = measureLspOperation(
      name = "completion_large",
      phase = "completion",
      inputSize = "large"
    ) {
      server
        .handleRequest(
          Request(
            id = StringId("comp"),
            method = "textDocument/completion",
            params = Some(
              Json.obj(
                "textDocument" -> Json.obj("uri" -> Json.fromString(uri)),
                "position" -> Json.obj("line" -> Json.fromInt(30), "character" -> Json.fromInt(15))
              )
            )
          )
        )
        .unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0
  }

  // -----------------------------------------------------------------
  // DAG Visualization Request Benchmarks
  // -----------------------------------------------------------------

  "DAG visualization benchmark" should "measure visualization request for small documents" in {
    val server = createTestServer().unsafeRunSync()
    val uri    = "file:///test/viz-small.cst"

    // Open document
    server
      .handleNotification(
        Notification(
          method = "textDocument/didOpen",
          params = Some(
            Json.obj(
              "textDocument" -> Json.obj(
                "uri"        -> Json.fromString(uri),
                "languageId" -> Json.fromString("constellation"),
                "version"    -> Json.fromInt(1),
                "text"       -> Json.fromString(smallSource)
              )
            )
          )
        )
      )
      .unsafeRunSync()

    val result = measureLspOperation(
      name = "dagviz_small",
      phase = "dagviz",
      inputSize = "small"
    ) {
      server
        .handleRequest(
          Request(
            id = StringId("viz"),
            method = "constellation/getDagVisualization",
            params = Some(
              Json.obj(
                "textDocument" -> Json.obj("uri" -> Json.fromString(uri))
              )
            )
          )
        )
        .unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0
  }

  it should "measure visualization request for large documents" in {
    val server = createTestServer().unsafeRunSync()
    val uri    = "file:///test/viz-large.cst"

    // Open document
    server
      .handleNotification(
        Notification(
          method = "textDocument/didOpen",
          params = Some(
            Json.obj(
              "textDocument" -> Json.obj(
                "uri"        -> Json.fromString(uri),
                "languageId" -> Json.fromString("constellation"),
                "version"    -> Json.fromInt(1),
                "text"       -> Json.fromString(largeSource)
              )
            )
          )
        )
      )
      .unsafeRunSync()

    val result = measureLspOperation(
      name = "dagviz_large",
      phase = "dagviz",
      inputSize = "large"
    ) {
      server
        .handleRequest(
          Request(
            id = StringId("viz"),
            method = "constellation/getDagVisualization",
            params = Some(
              Json.obj(
                "textDocument" -> Json.obj("uri" -> Json.fromString(uri))
              )
            )
          )
        )
        .unsafeRunSync()
    }

    println(result.toConsoleString)
    allResults += result

    // DAG visualization is less time-critical but should still be responsive
    result.avgMs should be < 500.0 // Target: <500ms for panel refresh
  }

  // -----------------------------------------------------------------
  // Report Generation
  // -----------------------------------------------------------------

  "LSP benchmark report" should "generate summary" in {
    println("\n" + "=" * 80)
    println("LSP OPERATIONS BENCHMARK RESULTS")
    println("=" * 80)

    // Group by phase
    val byPhase = allResults.groupBy(_.phase)
    List("didOpen", "didChange", "completion", "dagviz").foreach { phase =>
      byPhase.get(phase).foreach { results =>
        println(s"\n--- $phase ---")
        results.foreach(r => println(r.toConsoleString))
      }
    }

    // Performance targets summary
    println("\n" + "=" * 80)
    println("PERFORMANCE TARGETS")
    println("=" * 80)
    println("""
      |Operation              Target    Status
      |--------------------------------------------
      |didOpen (small)        <100ms    Check results above
      |didChange (keystroke)  <100ms    CRITICAL for responsiveness
      |completion             <50ms     CRITICAL for typing
      |dagviz                 <500ms    Less critical
      |""".stripMargin)

    // Write JSON report
    val reportPath = s"target/benchmark-lsp-${System.currentTimeMillis()}.json"
    writeJsonReport(reportPath)
    println(s"\nLSP benchmark report written to: $reportPath")

    allResults.size should be > 0
  }

  // -----------------------------------------------------------------
  // Helper Methods
  // -----------------------------------------------------------------

  private def measureLspOperation(
      name: String,
      phase: String,
      inputSize: String
  )(op: => Unit): BenchmarkResult = {
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

    BenchmarkResult(
      name = name,
      phase = phase,
      inputSize = inputSize,
      avgMs = avg,
      minMs = min,
      maxMs = max,
      stdDevMs = stdDev,
      throughputOpsPerSec = if avg > 0 then 1000.0 / avg else 0.0,
      iterations = MeasureIterations
    )
  }

  private def writeJsonReport(path: String): Unit = {
    import java.io.{File, PrintWriter}
    import scala.util.Using

    val json = new StringBuilder
    json.append("{\n")
    json.append(s"""  "timestamp": "${java.time.Instant.now()}",\n""")
    json.append(s"""  "type": "lsp-benchmark",\n""")
    json.append("  \"results\": [\n")

    allResults.toList.zipWithIndex.foreach { case (r, idx) =>
      json.append(s"""    {"name":"${r.name}","phase":"${r.phase}","inputSize":"${r.inputSize}",""")
      json.append(s""""avgMs":${f"${r.avgMs}%.4f"},"minMs":${f"${r.minMs}%.4f"},""")
      json.append(s""""maxMs":${f"${r.maxMs}%.4f"},"stdDevMs":${f"${r.stdDevMs}%.4f"},""")
      json.append(s""""throughputOpsPerSec":${f"${r.throughputOpsPerSec}%.4f"}}""")
      if idx < allResults.length - 1 then json.append(",")
      json.append("\n")
    }

    json.append("  ]\n")
    json.append("}\n")

    Using(new PrintWriter(new File(path)))(_.write(json.toString)).getOrElse(())
  }
}

// Inline BenchmarkResult for LSP module (avoids cross-module dependency)
case class BenchmarkResult(
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
    val nameWidth  = 30
    val paddedName = name.padTo(nameWidth, ' ').take(nameWidth)
    f"$paddedName : $avgMs%8.2fms (±$stdDevMs%.2f) [$minMs%.2f - $maxMs%.2f] $throughputOpsPerSec%.1f ops/s"
  }
}
