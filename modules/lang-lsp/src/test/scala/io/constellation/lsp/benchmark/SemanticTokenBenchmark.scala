package io.constellation.lsp.benchmark

import io.constellation.lsp.SemanticTokenProvider
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

/** Benchmarks for semantic token generation performance
  *
  * Run with: sbt "langLsp/testOnly *SemanticTokenBenchmark"
  *
  * Tests:
  * - Small file semantic token generation
  * - Medium file semantic token generation
  * - Large file semantic token generation
  * - Repeated call timing (to identify caching opportunities)
  *
  * Target metrics:
  * - Small files: <20ms
  * - Medium files: <50ms
  * - Large files: <100ms
  */
class SemanticTokenBenchmark extends AnyFlatSpec with Matchers {

  // Benchmark configuration
  val WarmupIterations  = 5
  val MeasureIterations = 20

  // Collect results
  private val allResults = ListBuffer[SemanticBenchmarkResult]()

  // Create provider instance
  val provider = SemanticTokenProvider()

  // Test fixtures - constellation-lang source files
  val smallSource: String =
    """# Small program
      |type Data = { value: Int, flag: Boolean }
      |
      |in data: Data
      |in fallback: Int
      |
      |value = data.value
      |flag = data.flag
      |
      |result = if (flag) value else fallback
      |guarded = value when flag
      |final = guarded ?? fallback
      |
      |out result
      |out final
      |""".stripMargin

  val mediumSource: String =
    """# Medium program - Flag Processing
      |
      |type Config = {
      |  enabled: Boolean,
      |  active: Boolean,
      |  premium: Boolean,
      |  verified: Boolean
      |}
      |
      |type UserData = {
      |  score: Int,
      |  level: Int,
      |  isAdmin: Boolean,
      |  isActive: Boolean
      |}
      |
      |in config: Config
      |in user: UserData
      |in defaultScore: Int
      |in defaultLevel: Int
      |
      |# Extract fields
      |enabled = config.enabled
      |active = config.active
      |premium = config.premium
      |verified = config.verified
      |
      |score = user.score
      |level = user.level
      |isAdmin = user.isAdmin
      |isActive = user.isActive
      |
      |# Compound boolean conditions
      |isFullyEnabled = enabled and active
      |isPremiumUser = premium and verified
      |canAccess = isFullyEnabled or isAdmin
      |isRestricted = not enabled or not active
      |
      |# Nested conditions
      |hasAccess = canAccess and isActive
      |isPowerUser = isPremiumUser and isAdmin
      |needsUpgrade = not premium and isActive
      |
      |# Guard expressions
      |premiumScore = score when isPremiumUser
      |adminLevel = level when isAdmin
      |activeScore = score when isActive
      |
      |# Coalesce chains
      |effectiveScore = premiumScore ?? activeScore ?? defaultScore
      |effectiveLevel = adminLevel ?? defaultLevel
      |
      |# Complex conditionals
      |accessLevel = if (isPowerUser) level else if (hasAccess) defaultLevel else defaultScore
      |
      |out isFullyEnabled
      |out isPremiumUser
      |out canAccess
      |out hasAccess
      |out effectiveScore
      |out accessLevel
      |""".stripMargin

  val largeSource: String =
    """# Large program - Complex Flag Processing Pipeline
      |
      |type SystemConfig = {
      |  mainEnabled: Boolean,
      |  backupEnabled: Boolean,
      |  debugMode: Boolean,
      |  maintenanceMode: Boolean
      |}
      |
      |type FeatureFlags = {
      |  featureA: Boolean,
      |  featureB: Boolean,
      |  featureC: Boolean,
      |  featureD: Boolean,
      |  featureE: Boolean,
      |  featureF: Boolean
      |}
      |
      |type UserProfile = {
      |  isAdmin: Boolean,
      |  isPremium: Boolean,
      |  isVerified: Boolean,
      |  isBetaTester: Boolean,
      |  isInternal: Boolean,
      |  level: Int,
      |  score: Int
      |}
      |
      |type AccessRules = {
      |  requiresAuth: Boolean,
      |  requiresPremium: Boolean,
      |  requiresAdmin: Boolean,
      |  allowGuests: Boolean
      |}
      |
      |in system: SystemConfig
      |in features: FeatureFlags
      |in user: UserProfile
      |in access: AccessRules
      |in defaultLevel: Int
      |in defaultScore: Int
      |
      |# System state extraction
      |mainOn = system.mainEnabled
      |backupOn = system.backupEnabled
      |debugOn = system.debugMode
      |maintOn = system.maintenanceMode
      |
      |# Feature flag extraction
      |fA = features.featureA
      |fB = features.featureB
      |fC = features.featureC
      |fD = features.featureD
      |fE = features.featureE
      |fF = features.featureF
      |
      |# User profile extraction
      |isAdmin = user.isAdmin
      |isPremium = user.isPremium
      |isVerified = user.isVerified
      |isBetaTester = user.isBetaTester
      |isInternal = user.isInternal
      |userLevel = user.level
      |userScore = user.score
      |
      |# Access rules extraction
      |needsAuth = access.requiresAuth
      |needsPremium = access.requiresPremium
      |needsAdmin = access.requiresAdmin
      |allowsGuests = access.allowGuests
      |
      |# System availability logic
      |systemAvailable = mainOn or backupOn
      |systemOperational = systemAvailable and not maintOn
      |canDebug = debugOn and isAdmin
      |emergencyMode = not mainOn and backupOn
      |
      |# Feature access logic
      |coreFeatures = fA and fB
      |advancedFeatures = fC and fD
      |experimentalFeatures = fE and fF
      |anyFeatureEnabled = fA or fB or fC or fD or fE or fF
      |allCoreEnabled = fA and fB and fC
      |
      |# User tier logic
      |isPrivileged = isAdmin or isInternal
      |canAccessBeta = isBetaTester or isInternal
      |isPowerUser = isPremium and isVerified
      |isRegularUser = not isAdmin and not isPremium
      |needsUpgrade = isRegularUser and not isVerified
      |
      |# Access control logic
      |passesAuthCheck = not needsAuth or isVerified
      |passesPremiumCheck = not needsPremium or isPremium
      |passesAdminCheck = not needsAdmin or isAdmin
      |passesGuestCheck = allowsGuests or isVerified
      |passesAllChecks = passesAuthCheck and passesPremiumCheck and passesAdminCheck
      |
      |# Complex access decisions
      |canAccessSystem = systemOperational and passesAllChecks
      |canAccessCore = canAccessSystem and coreFeatures
      |canAccessAdvanced = canAccessCore and advancedFeatures and isPowerUser
      |canAccessExperimental = canAccessAdvanced and experimentalFeatures and canAccessBeta
      |
      |# Guard expressions for conditional values
      |adminLevel = userLevel when isAdmin
      |premiumLevel = userLevel when isPremium
      |verifiedLevel = userLevel when isVerified
      |betaLevel = userLevel when isBetaTester
      |
      |adminScore = userScore when isAdmin
      |premiumScore = userScore when isPremium
      |verifiedScore = userScore when isVerified
      |internalScore = userScore when isInternal
      |
      |# Coalesce chains for fallback resolution
      |effectiveLevel = adminLevel ?? premiumLevel ?? verifiedLevel ?? betaLevel ?? defaultLevel
      |effectiveScore = adminScore ?? premiumScore ?? verifiedScore ?? internalScore ?? defaultScore
      |
      |# More complex conditionals
      |accessTier = if (isAdmin) userLevel else if (isPremium) defaultLevel else defaultScore
      |featureTier = if (canAccessExperimental) userLevel else if (canAccessAdvanced) defaultLevel else defaultScore
      |
      |# Additional boolean combinations
      |fullAccess = canAccessExperimental and isPrivileged
      |limitedAccess = canAccessCore and not canAccessAdvanced
      |noAccess = not canAccessSystem
      |
      |# Status flags
      |isHealthy = systemOperational and anyFeatureEnabled
      |needsMaintenance = maintOn or (not mainOn and not backupOn)
      |isInGoodState = isHealthy and not needsMaintenance
      |
      |# Mixed logic chains
      |complexCondition1 = (isAdmin and fA) or (isPremium and fB) or (isVerified and fC)
      |complexCondition2 = (mainOn and not debugOn) or (backupOn and debugOn)
      |complexCondition3 = (needsAuth and passesAuthCheck) or (not needsAuth and allowsGuests)
      |
      |# Final tier guards
      |tier1Value = userScore when fullAccess
      |tier2Value = userLevel when limitedAccess
      |tier3Value = defaultScore when noAccess
      |
      |# Final coalesce resolution
      |finalTierValue = tier1Value ?? tier2Value ?? tier3Value ?? defaultLevel
      |
      |# More conditional outputs
      |systemStatus = if (isHealthy) userScore else defaultScore
      |userStatus = if (isPrivileged) userLevel else effectiveLevel
      |accessStatus = if (passesAllChecks) userScore else effectiveScore
      |
      |# Outputs
      |out systemAvailable
      |out systemOperational
      |out canDebug
      |out emergencyMode
      |out coreFeatures
      |out advancedFeatures
      |out experimentalFeatures
      |out anyFeatureEnabled
      |out allCoreEnabled
      |out isPrivileged
      |out canAccessBeta
      |out isPowerUser
      |out isRegularUser
      |out needsUpgrade
      |out passesAllChecks
      |out canAccessSystem
      |out canAccessCore
      |out canAccessAdvanced
      |out canAccessExperimental
      |out effectiveLevel
      |out effectiveScore
      |out accessTier
      |out featureTier
      |out fullAccess
      |out limitedAccess
      |out noAccess
      |out isHealthy
      |out needsMaintenance
      |out isInGoodState
      |out complexCondition1
      |out complexCondition2
      |out complexCondition3
      |out finalTierValue
      |out systemStatus
      |out userStatus
      |out accessStatus
      |""".stripMargin

  // -----------------------------------------------------------------
  // Semantic Token Generation Benchmarks
  // -----------------------------------------------------------------

  "Semantic token generation" should "be fast for small files" in {
    val result = measureSemanticTokens(
      name = "semantic_tokens_small",
      phase = "semantic_tokens",
      inputSize = "small"
    ) {
      provider.computeTokens(smallSource)
    }

    println(result.toConsoleString)
    allResults += result

    // Target: <20ms for small files
    result.avgMs should be < 20.0
  }

  it should "be fast for medium files" in {
    val result = measureSemanticTokens(
      name = "semantic_tokens_medium",
      phase = "semantic_tokens",
      inputSize = "medium"
    ) {
      provider.computeTokens(mediumSource)
    }

    println(result.toConsoleString)
    allResults += result

    // Target: <50ms for medium files
    result.avgMs should be < 50.0
  }

  it should "be fast for large files" in {
    val result = measureSemanticTokens(
      name = "semantic_tokens_large",
      phase = "semantic_tokens",
      inputSize = "large"
    ) {
      provider.computeTokens(largeSource)
    }

    println(result.toConsoleString)
    allResults += result

    // Target: <100ms for large files
    result.avgMs should be < 100.0
  }

  // -----------------------------------------------------------------
  // Repeated Call Timing (Caching Opportunity Analysis)
  // -----------------------------------------------------------------

  "Semantic token repeated calls" should "identify caching opportunities" in {
    println("\n" + "=" * 80)
    println("SEMANTIC TOKEN CACHING OPPORTUNITY ANALYSIS")
    println("=" * 80)

    // Measure first call vs subsequent calls
    val source = largeSource

    // First call (cold)
    val coldTimings = (1 to 5).map { _ =>
      val freshProvider = SemanticTokenProvider()
      val start = System.nanoTime()
      freshProvider.computeTokens(source)
      (System.nanoTime() - start) / 1e6
    }

    // Subsequent calls (same provider)
    val warmProvider = SemanticTokenProvider()
    warmProvider.computeTokens(source) // Warmup

    val warmTimings = (1 to 20).map { _ =>
      val start = System.nanoTime()
      warmProvider.computeTokens(source)
      (System.nanoTime() - start) / 1e6
    }

    val coldAvg  = coldTimings.sum / coldTimings.length
    val warmAvg  = warmTimings.sum / warmTimings.length
    val coldVar  = coldTimings.map(t => math.pow(t - coldAvg, 2)).sum / coldTimings.length
    val warmVar  = warmTimings.map(t => math.pow(t - warmAvg, 2)).sum / warmTimings.length
    val coldStd  = math.sqrt(coldVar)
    val warmStd  = math.sqrt(warmVar)

    println(f"\nCold calls (fresh provider): ${coldAvg}%.2fms (±${coldStd}%.2fms)")
    println(f"Warm calls (same provider):  ${warmAvg}%.2fms (±${warmStd}%.2fms)")

    val speedup = coldAvg / warmAvg
    println(f"\nSpeedup (warm vs cold): ${speedup}%.2fx")

    if (speedup > 1.5) {
      println("Analysis: Significant speedup detected - internal caching IS providing benefit")
    } else if (warmStd < coldStd * 0.5) {
      println("Analysis: More consistent timing suggests stable performance")
    } else {
      println("Analysis: No significant caching benefit - consider adding AST caching")
    }

    // Also record as result
    allResults += SemanticBenchmarkResult(
      name = "semantic_tokens_cold_avg",
      phase = "caching_analysis",
      inputSize = "large",
      avgMs = coldAvg,
      minMs = coldTimings.min,
      maxMs = coldTimings.max,
      stdDevMs = coldStd,
      throughputOpsPerSec = if (coldAvg > 0) 1000.0 / coldAvg else 0.0,
      iterations = coldTimings.length
    )

    allResults += SemanticBenchmarkResult(
      name = "semantic_tokens_warm_avg",
      phase = "caching_analysis",
      inputSize = "large",
      avgMs = warmAvg,
      minMs = warmTimings.min,
      maxMs = warmTimings.max,
      stdDevMs = warmStd,
      throughputOpsPerSec = if (warmAvg > 0) 1000.0 / warmAvg else 0.0,
      iterations = warmTimings.length
    )

    // Always pass - this is for measurement
    speedup should be >= 0.0
  }

  // -----------------------------------------------------------------
  // Token Count Analysis
  // -----------------------------------------------------------------

  it should "report token generation statistics" in {
    println("\n" + "=" * 80)
    println("SEMANTIC TOKEN STATISTICS")
    println("=" * 80)

    List(
      ("small", smallSource),
      ("medium", mediumSource),
      ("large", largeSource)
    ).foreach { case (size, source) =>
      val tokens   = provider.computeTokens(source)
      val numLines = source.linesIterator.length
      val numChars = source.length
      val numTokens = tokens.length / 5 // Each token is 5 integers

      println(f"\n$size%s file:")
      println(f"  Lines: $numLines%d")
      println(f"  Characters: $numChars%d")
      println(f"  Semantic tokens: $numTokens%d")
      println(f"  Tokens/line: ${numTokens.toDouble / numLines}%.2f")
    }

    println()
    allResults.nonEmpty shouldBe true
  }

  // -----------------------------------------------------------------
  // Report Generation
  // -----------------------------------------------------------------

  "Semantic token benchmark report" should "generate summary" in {
    println("\n" + "=" * 80)
    println("SEMANTIC TOKEN BENCHMARK SUMMARY")
    println("=" * 80)

    val tokenResults = allResults.filter(_.phase == "semantic_tokens").toList

    if (tokenResults.nonEmpty) {
      println("\n--- Semantic Token Generation Times ---")
      tokenResults.foreach(r => println(s"  ${r.toConsoleString}"))

      val avgTime = tokenResults.map(_.avgMs).sum / tokenResults.size
      println(f"\nAverage time across all sizes: $avgTime%.2fms")
    }

    // Target verification
    println("\n" + "-" * 60)
    println("TARGET VERIFICATION:")
    println("-" * 60)

    val targets = Map(
      "small"  -> 20.0,
      "medium" -> 50.0,
      "large"  -> 100.0
    )

    targets.foreach { case (size, target) =>
      allResults.find(r => r.phase == "semantic_tokens" && r.inputSize == size) match {
        case Some(result) =>
          val status = if (result.avgMs <= target) "PASS" else "FAIL"
          println(f"  $size%-10s: ${result.avgMs}%7.2fms / ${target}%.0fms target  $status")
        case None =>
          println(f"  $size%-10s: (no data)")
      }
    }

    println()
    allResults.size should be > 0
  }

  // -----------------------------------------------------------------
  // Helper Methods
  // -----------------------------------------------------------------

  private def measureSemanticTokens[A](
      name: String,
      phase: String,
      inputSize: String
  )(op: => A): SemanticBenchmarkResult = {
    // Warmup
    (0 until WarmupIterations).foreach(_ => op)

    // Measure
    val timings = (0 until MeasureIterations).map { _ =>
      val start = System.nanoTime()
      op
      val end = System.nanoTime()
      (end - start) / 1e6 // ms
    }

    val avg    = timings.sum / timings.length
    val sorted = timings.sorted
    val min    = sorted.head
    val max    = sorted.last
    val variance = timings.map(t => math.pow(t - avg, 2)).sum / timings.length
    val stdDev   = math.sqrt(variance)

    SemanticBenchmarkResult(
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
}

// Inline BenchmarkResult for LSP module (avoids cross-module dependency)
case class SemanticBenchmarkResult(
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
