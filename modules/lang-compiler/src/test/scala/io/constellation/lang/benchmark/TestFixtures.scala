package io.constellation.lang.benchmark

import scala.io.Source
import scala.util.Using

/** Test fixtures for benchmark programs
  *
  * NOTE: These programs use ONLY built-in language features that work with LangCompiler.empty (no
  * external module calls, no arithmetic, no comparisons).
  *
  * Supported features:
  *   - Boolean operators: and, or, not
  *   - Record types and field access
  *   - Conditionals with boolean conditions (if/else)
  *   - Guard expressions (when)
  *   - Coalesce (??)
  */
object TestFixtures {

  /** Small program: ~10 lines, basic inputs and outputs Uses: inputs, outputs, field access,
    * boolean ops, guard, coalesce Expected to parse and compile in <5ms
    */
  val smallProgram: String =
    """# Small benchmark program
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

  /** Medium program: ~50 lines, multiple inputs, conditionals, field access Uses: record types,
    * field access, conditionals, boolean ops, guards, coalesce Expected to parse and compile in
    * <50ms
    */
  val mediumProgram: String =
    """# Medium benchmark program - Flag Processing
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
      |tierLevel = if (isPremiumUser) level else defaultLevel
      |
      |# More boolean combinations
      |allEnabled = enabled and active and premium and verified
      |anyEnabled = enabled or active or premium or verified
      |mixedState = (enabled and not active) or (not enabled and active)
      |
      |# Final computations
      |finalAccess = if (allEnabled) level else effectiveLevel
      |fallbackResult = if (anyEnabled) score else defaultScore
      |
      |out isFullyEnabled
      |out isPremiumUser
      |out canAccess
      |out hasAccess
      |out isPowerUser
      |out needsUpgrade
      |out effectiveScore
      |out effectiveLevel
      |out accessLevel
      |out tierLevel
      |out allEnabled
      |out anyEnabled
      |out mixedState
      |out finalAccess
      |out fallbackResult
      |""".stripMargin

  /** Large program: Complex Flag Processing Pipeline (no external modules) Uses: record types,
    * field access, boolean ops, conditionals, guards, coalesce Expected to parse and compile in
    * <200ms
    */
  val largeProgram: String =
    """# Large Benchmark - Complex Flag Processing Pipeline
      |# Tests core constellation-lang features (no external modules, no arithmetic)
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

  /** Generate stress test program with chained boolean ops (no external modules)
    *
    * @param chainLength
    *   Number of chained operations
    * @return
    *   Generated program source
    */
  def generateStressProgram(chainLength: Int): String = {
    val sb = new StringBuilder

    sb.append("# Stress test program - chained boolean operations\n")
    sb.append("type Input = { flag: Boolean, value: Int }\n")
    sb.append("in input: Input\n")
    sb.append("in fallback: Int\n\n")

    sb.append("baseFlag = input.flag\n")
    sb.append("baseValue = input.value\n\n")

    // Generate chain of boolean operations
    sb.append("b0 = baseFlag\n")
    for i <- 1 to chainLength do {
      val prev = s"b${i - 1}"
      val op = i % 4 match {
        case 0 => s"$prev and baseFlag"
        case 1 => s"$prev or baseFlag"
        case 2 => s"not $prev"
        case 3 => s"($prev and baseFlag) or not baseFlag"
      }
      sb.append(s"b$i = $op\n")
    }

    // Add guard/coalesce chain
    sb.append(s"\n# Guards and coalesce\n")
    val last = s"b$chainLength"
    sb.append(s"guarded1 = baseValue when $last\n")
    sb.append(s"guarded2 = fallback when not $last\n")
    sb.append(s"result = guarded1 ?? guarded2 ?? fallback\n")

    // Add conditional
    sb.append(s"final = if ($last) baseValue else fallback\n")

    sb.append(s"\nout result\n")
    sb.append(s"out final\n")
    sb.append(s"out $last\n")

    sb.toString
  }

  /** Stress program with 100 chained operations */
  lazy val stressProgram100: String = generateStressProgram(100)

  /** Stress program with 200 chained operations */
  lazy val stressProgram200: String = generateStressProgram(200)

  /** Stress program with 500 chained operations */
  lazy val stressProgram500: String = generateStressProgram(500)

  /** Stress program with 1000 chained operations */
  lazy val stressProgram1000: String = generateStressProgram(1000)

  /** Load the actual lead-scoring-pipeline.cst from examples NOTE: This requires a full
    * FunctionRegistry, not LangCompiler.empty
    */
  def loadLeadScoringPipeline: Option[String] = {
    val path = "modules/example-app/examples/lead-scoring-pipeline.cst"
    Using(Source.fromFile(path))(_.mkString).toOption
  }

  /** All fixture sizes for iteration */
  case class Fixture(name: String, size: String, source: String)

  /** Get standard test fixtures */
  def standardFixtures: List[Fixture] = List(
    Fixture("small_flags", "small", smallProgram),
    Fixture("medium_flags", "medium", mediumProgram),
    Fixture("large_flags", "large", largeProgram)
  )

  /** Get stress test fixtures */
  def stressFixtures: List[Fixture] = List(
    Fixture("stress_100", "stress", stressProgram100),
    Fixture("stress_200", "stress", stressProgram200)
    // Uncomment for longer tests:
    // Fixture("stress_500", "stress", stressProgram500)
  )

  /** Get all fixtures including stress tests */
  def allFixtures: List[Fixture] = standardFixtures ++ stressFixtures
}
