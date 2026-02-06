---
title: "Examples"
sidebar_position: 14
description: "Complete constellation-lang examples from hello world to production pipelines with API composition and ML workflows."
---

# Examples

This page provides progressively complex examples of constellation-lang pipelines, from simple transformations to production-ready systems.

:::note Running the Examples
To run these examples, save the code to a `.cst` file and execute using the constellation runtime or dashboard. The `@example` annotations provide sample input values for testing.
:::

## Example 1: Hello World

The simplest possible pipeline - take a name and create a greeting.

```constellation
# hello-world.cst
# A minimal pipeline demonstrating basic structure

@example("Alice")
in name: String

greeting = Concat("Hello, ", name)
message = Concat(greeting, "!")

out message
```

**Output:** `"Hello, Alice!"`

**Concepts demonstrated:**
- Input declaration with `@example` annotation
- Variable assignment with module calls
- Single output

---

## Example 2: Text Transformation

A multi-step text processing pipeline that cleans and analyzes input text.

```constellation
# text-analysis.cst
# Clean, normalize, and analyze text input

@example("  The Quick Brown Fox Jumps Over The Lazy Dog.  ")
in rawText: String

# Step 1: Clean the input
trimmed = Trim(rawText)

# Step 2: Normalize to lowercase
normalized = Lowercase(trimmed)

# Step 3: Analyze the text
wordCount = WordCount(normalized)
charCount = TextLength(normalized)

# Step 4: Extract first word
words = SplitWords(normalized)
firstWord = Head(words)

# Multiple outputs
out trimmed
out normalized
out wordCount
out charCount
out firstWord
```

**Sample outputs:**
- `trimmed`: `"The Quick Brown Fox Jumps Over The Lazy Dog."`
- `normalized`: `"the quick brown fox jumps over the lazy dog."`
- `wordCount`: `9`
- `charCount`: `44`
- `firstWord`: `"the"`

**Concepts demonstrated:**
- Chained transformations
- Multiple outputs
- Basic text processing modules

---

## Example 3: Data Transformation with Records

Working with structured data - filtering, transforming, and aggregating records.

```constellation
# data-pipeline.cst
# Process a list of numbers with filtering and aggregation

@example([2, 4, 6, 8, 10, 12, 15, 20])
in numbers: List<Int>

@example(5)
in threshold: Int

@example(2)
in multiplier: Int

# Step 1: Filter - keep only numbers above threshold
filtered = FilterGreaterThan(numbers, threshold)

# Step 2: Transform - multiply each filtered number
scaled = MultiplyEach(filtered, multiplier)

# Step 3: Aggregate - calculate statistics
total = SumList(scaled)
average = Average(scaled)
maximum = Max(scaled)
minimum = Min(scaled)
count = Length(scaled)

# Step 4: Format for display
formattedTotal = FormatNumber(total)
summary = Concat("Processed ", FormatNumber(count))

# All outputs
out filtered      # [6, 8, 10, 12, 15, 20]
out scaled        # [12, 16, 20, 24, 30, 40]
out total         # 142
out average       # 23.67
out maximum       # 40
out minimum       # 12
out formattedTotal
out summary
```

**Concepts demonstrated:**
- List processing
- Multiple input parameters
- Aggregation functions
- Formatting for output

---

:::tip Learning Progression
Work through the examples in order. Each builds on concepts from the previous ones. After Example 3, you will understand the fundamentals. Examples 4-7 introduce production patterns like resilience and ML workflows.
:::

## Example 4: API Composition with Resilience

A realistic pipeline that fetches data from multiple sources with error handling and caching.

```constellation
# user-dashboard.cst
# Compose data from multiple APIs with resilience patterns

# =============================================================================
# Type Definitions
# =============================================================================

type UserProfile = {
  id: String,
  name: String,
  email: String,
  tier: String
}

type Activity = {
  action: String,
  timestamp: String
}

type Preferences = {
  theme: String,
  notifications: Boolean
}

# =============================================================================
# Inputs
# =============================================================================

@example("user-12345")
in userId: String

# Fallback values for resilience
@example({ theme: "light", notifications: true })
in defaultPreferences: Preferences

# =============================================================================
# Data Fetching (Fan-Out Pattern)
# =============================================================================

# Fetch user profile - critical, retry aggressively
profile = FetchUserProfile(userId) with
    cache: 5min,
    retry: 3,
    delay: 100ms,
    backoff: exponential,
    timeout: 3s,
    priority: high

# Fetch recent activity - less critical, log errors and continue
activity = FetchUserActivity(userId) with
    cache: 1min,
    retry: 2,
    timeout: 5s,
    on_error: log

# Fetch preferences - use fallback on failure
preferences = FetchUserPreferences(userId) with
    cache: 10min,
    retry: 1,
    timeout: 2s,
    fallback: defaultPreferences

# =============================================================================
# Data Processing
# =============================================================================

# Extract display name
displayName = Uppercase(profile.name)

# Check if premium user
isPremium = profile.tier == "premium" or profile.tier == "enterprise"

# Count recent activities
activityCount = Length(activity)

# Determine greeting based on time and tier
greeting = branch {
  isPremium -> Concat("Welcome back, valued customer ", displayName),
  activityCount > 10 -> Concat("Welcome, active user ", displayName),
  otherwise -> Concat("Hello, ", displayName)
}

# =============================================================================
# Outputs
# =============================================================================

out profile
out activity
out preferences
out displayName
out isPremium
out greeting
```

**Concepts demonstrated:**
- Type definitions for structured data
- Multiple API calls with fan-out pattern
- Module options: `cache`, `retry`, `timeout`, `fallback`, `on_error`
- Branch expressions for conditional logic
- Comparison and boolean operators

---

## Example 5: Error Handling Patterns

Demonstrating various error handling strategies for robust pipelines.

```constellation
# error-handling.cst
# Comprehensive error handling patterns

@example("request-123")
in requestId: String

@example("safe-default-response")
in defaultResponse: String

# =============================================================================
# Strategy 1: Retry with Exponential Backoff
# =============================================================================

# For transient failures (network issues, rate limits)
# Retry up to 3 times with increasing delays: 100ms, 200ms, 400ms
retriedResult = FlakyService(requestId) with
    retry: 3,
    delay: 100ms,
    backoff: exponential,
    timeout: 5s

# =============================================================================
# Strategy 2: Fallback Values
# =============================================================================

# When you have a safe default to use on failure
# The fallback is used if all retries fail
withFallback = UnreliableService(requestId) with
    retry: 2,
    timeout: 3s,
    fallback: defaultResponse

# =============================================================================
# Strategy 3: Skip on Error
# =============================================================================

# For non-critical enrichment data
# Returns zero/empty value on failure, pipeline continues
skipped = OptionalEnrichment(requestId) with
    timeout: 2s,
    on_error: skip

# =============================================================================
# Strategy 4: Log and Continue
# =============================================================================

# Log errors for debugging but don't fail the pipeline
# Useful during development or for monitoring
logged = DebugService(requestId) with
    timeout: 1s,
    on_error: log

# =============================================================================
# Strategy 5: Guard + Coalesce Pattern
# =============================================================================

# Conditionally attempt expensive operation
# Only try if we have sufficient data
@example(100)
in dataSize: Int

# Guard: only compute if data is large enough
expensiveResult = ExpensiveComputation(requestId) when dataSize > 50

# Coalesce: use fallback if guard failed or computation failed
safeResult = expensiveResult ?? defaultResponse

# =============================================================================
# Strategy 6: Tiered Fallback Chain
# =============================================================================

# Try primary, then secondary, then tertiary, then default
primaryResult = PrimaryService(requestId) with retry: 2, on_error: skip
secondaryResult = SecondaryService(requestId) when primaryResult == "" with on_error: skip
tertiaryResult = TertiaryService(requestId) when secondaryResult == "" with on_error: skip

# Coalesce chain picks first non-empty result
finalResult = primaryResult ?? secondaryResult ?? tertiaryResult ?? defaultResponse

# =============================================================================
# Outputs
# =============================================================================

out retriedResult
out withFallback
out skipped
out logged
out safeResult
out finalResult
```

**Concepts demonstrated:**
- `retry` with `backoff` strategies
- `fallback` values
- `on_error: skip` for non-critical operations
- `on_error: log` for debugging
- Guard expressions with `when`
- Coalesce operator `??` for fallback chains

---

## Example 6: Complex Multi-Step Pipeline

A comprehensive lead scoring pipeline demonstrating all major language features.

```constellation
# lead-scoring.cst
# B2B Lead Scoring Pipeline
# Analyzes company and engagement data to score and classify leads

# =============================================================================
# Type Definitions
# =============================================================================

type CompanyInfo = {
  name: String,
  industry: String,
  employeeCount: Int,
  annualRevenue: Int
}

type EngagementData = {
  websiteVisits: Int,
  emailOpens: Int,
  contentDownloads: Int,
  description: String
}

type ScoringResult = {
  finalScore: Int,
  category: String,
  qualified: Boolean,
  priority: String
}

# =============================================================================
# Namespace Imports
# =============================================================================

use stdlib.math as m
use stdlib.text as t

# =============================================================================
# Inputs
# =============================================================================

@example({ name: "Acme Corp", industry: "technology", employeeCount: 250, annualRevenue: 5000000 })
in company: CompanyInfo

@example({ websiteVisits: 15, emailOpens: 8, contentDownloads: 3, description: "Looking for AI solutions for customer service automation" })
in engagement: EngagementData

@example("technology,software,AI,machine learning")
in targetKeywords: String

@example(60)
in qualificationThreshold: Int

# =============================================================================
# Feature Extraction
# =============================================================================

# Text analysis on engagement description
descriptionCleaned = t.trim(engagement.description)
descriptionLower = Lowercase(descriptionCleaned)
descriptionWordCount = WordCount(descriptionLower)
hasKeywordMatch = Contains(descriptionLower, targetKeywords)

# =============================================================================
# Company Scoring (25% weight)
# =============================================================================

# Score based on company size
isLargeCompany = company.employeeCount > 500
isMediumCompany = company.employeeCount >= 50 and company.employeeCount <= 500
companySizeScore = if (isLargeCompany) 100 else if (isMediumCompany) 70 else 30

# =============================================================================
# Revenue Scoring (30% weight)
# =============================================================================

# Normalize revenue to 0-100 scale (assuming max $10M)
revenueNormalized = company.annualRevenue / 100000
revenueScore = if (revenueNormalized > 100) 100 else revenueNormalized

# =============================================================================
# Engagement Scoring (25% weight)
# =============================================================================

# Total engagement activities
totalEngagement = engagement.websiteVisits + engagement.emailOpens + engagement.contentDownloads

# Score based on engagement level
highEngagement = totalEngagement > 15
mediumEngagement = totalEngagement >= 5 and totalEngagement <= 15
engagementScore = if (highEngagement) 100 else if (mediumEngagement) 60 else 20

# =============================================================================
# Content Quality Scoring (20% weight)
# =============================================================================

# Score based on description quality
hasDetailedDescription = descriptionWordCount > 10
hasBasicDescription = descriptionWordCount >= 5 and descriptionWordCount <= 10
contentScore = if (hasDetailedDescription) 100 else if (hasBasicDescription) 50 else 10

# =============================================================================
# Final Score Calculation
# =============================================================================

# Weighted combination: company 25%, revenue 30%, engagement 25%, content 20%
weightedCompany = companySizeScore / 4
weightedRevenue = revenueScore * 3 / 10
weightedEngagement = engagementScore / 4
weightedContent = contentScore / 5

# Base score
baseScore = weightedCompany + weightedRevenue + weightedEngagement + weightedContent

# Bonus for keyword match
keywordBonus = if (hasKeywordMatch) 15 else 0

# Final score (capped at 100)
rawFinalScore = baseScore + keywordBonus
finalScore = if (rawFinalScore > 100) 100 else rawFinalScore

# =============================================================================
# Classification
# =============================================================================

# Lead category based on score
category = branch {
  finalScore >= 80 -> "hot",
  finalScore >= 60 -> "warm",
  finalScore >= 40 -> "cool",
  otherwise -> "cold"
}

# Qualification status
qualified = finalScore >= qualificationThreshold

# Priority assignment
priority = branch {
  finalScore >= 90 and hasKeywordMatch -> "urgent",
  finalScore >= 80 -> "high",
  finalScore >= 60 -> "medium",
  otherwise -> "low"
}

# =============================================================================
# Advanced: Tiered Bonus with Guards
# =============================================================================

# Guard expressions for tiered bonuses
tier1Bonus = 50 when finalScore >= 90
tier2Bonus = 30 when finalScore >= 70 and finalScore < 90
tier3Bonus = 10 when finalScore >= 50 and finalScore < 70

# Coalesce picks first applicable tier
tieredBonus = tier1Bonus ?? tier2Bonus ?? tier3Bonus ?? 0
adjustedScore = finalScore + tieredBonus

# =============================================================================
# Output Assembly
# =============================================================================

# Format company name for display
displayName = Uppercase(company.name)

# Construct result summary
resultSummary = Concat(displayName, Concat(": ", category))

# =============================================================================
# Outputs
# =============================================================================

# Lead identification
out displayName
out company

# Component scores
out companySizeScore
out revenueScore
out engagementScore
out contentScore

# Intermediate values
out totalEngagement
out hasKeywordMatch
out keywordBonus

# Final results
out finalScore
out category
out qualified
out priority

# Advanced calculations
out tieredBonus
out adjustedScore
out resultSummary
```

**Concepts demonstrated:**
- Complete type definitions
- Namespace imports with aliases
- Arithmetic operators (`+`, `-`, `*`, `/`)
- Comparison operators (`>`, `>=`, `<`, `<=`, `==`)
- Boolean operators (`and`, `or`)
- Conditional expressions (`if/else`)
- Branch expressions for multi-way routing
- Guard expressions (`when`)
- Coalesce operator (`??`)
- Field access on records
- Multiple coordinated outputs

---

## Example 7: Real-Time Recommendation System

A production-style pipeline for generating personalized recommendations.

```constellation
# recommendations.cst
# Real-Time Personalized Recommendation Pipeline

# =============================================================================
# Types
# =============================================================================

type User = {
  id: String,
  segment: String,
  preferences: List<String>
}

type Item = {
  id: String,
  category: String,
  score: Float
}

type Context = {
  deviceType: String,
  location: String,
  timeOfDay: String
}

# =============================================================================
# Inputs
# =============================================================================

@example({ id: "user-123", segment: "power-user", preferences: ["technology", "gaming"] })
in user: User

@example([])
in candidateItems: List<Item>

@example({ deviceType: "mobile", location: "US", timeOfDay: "evening" })
in context: Context

@example(10)
in maxResults: Int

# =============================================================================
# Feature Engineering
# =============================================================================

# User features
userEmbedding = EmbedUser(user) with cache: 10min, timeout: 2s

# Item features (parallel processing)
itemEmbeddings = EmbedItems(candidateItems) with
    cache: 5min,
    timeout: 5s,
    concurrency: 4

# Context features
contextVector = EncodeContext(context)

# =============================================================================
# Scoring Pipeline
# =============================================================================

# Compute relevance scores
relevanceScores = ComputeRelevance(userEmbedding, itemEmbeddings) with
    timeout: 3s,
    priority: high

# Apply context adjustments
contextAdjusted = ApplyContextBoost(relevanceScores, contextVector) with
    timeout: 1s

# Apply business rules (promotions, inventory)
businessRulesApplied = ApplyBusinessRules(contextAdjusted, user.segment) with
    timeout: 2s,
    on_error: skip  # Continue without business rules if service is down

# =============================================================================
# Post-Processing
# =============================================================================

# Sort by final score
sorted = SortByScore(businessRulesApplied)

# Take top N results
topN = Take(sorted, maxResults)

# Filter out items user has already seen
filtered = FilterSeen(topN, user.id) with cache: 1min

# Add explanations for each recommendation
withExplanations = GenerateExplanations(filtered, user) with
    lazy: true,  # Only compute if client requests explanations
    timeout: 5s,
    on_error: skip

# =============================================================================
# Metrics & Logging
# =============================================================================

# Track recommendation quality metrics
metrics = ComputeMetrics(filtered, user) with
    priority: background,
    on_error: log

# =============================================================================
# Outputs
# =============================================================================

out filtered           # Final recommendations
out withExplanations   # Recommendations with explanations (lazy)
out metrics            # Quality metrics (background)
```

**Concepts demonstrated:**
- Complex type hierarchies
- Caching strategies at multiple levels
- Concurrency control for batch operations
- Priority levels (`high`, `background`)
- Lazy evaluation for optional computations
- Error handling for non-critical components
- Pipeline for ML inference workflow

---

## Original Example: Communication Ranking

The original example showing type algebra and projections for ranking communications.

```constellation
# Communication ranking pipeline
# Ranks communications for a specific user

type Communication = {
  communicationId: String,
  contentBlocks: List<String>,
  channel: String
}

type EmbeddingResult = {
  embedding: List<Float>
}

type ScoreResult = {
  score: Float,
  rank: Int
}

# Pipeline inputs
in communications: Candidates<Communication>
in mappedUserId: Int

# Step 1: Generate embeddings for each communication
embeddings = ide-ranker-v2-candidate-embed(communications)

# Step 2: Compute relevance scores using embeddings and user context
scores = ide-ranker-v2-score(embeddings + communications, mappedUserId)

# Step 3: Select output fields and merge with scores
result = communications[communicationId, channel] + scores[score, rank]

# Pipeline output
out result
```

Output type: `Candidates<{ communicationId: String, channel: String, score: Float, rank: Int }>`

**Concepts demonstrated:**
- Type algebra with the `+` operator
- Field projection with `[field1, field2]`
- Working with `Candidates<T>` (list-like container)

---

## Quick Reference: Pattern Summary

| Pattern | Use Case | Key Features |
|---------|----------|--------------|
| Hello World | Learning basics | Single input, single output |
| Text Transformation | Data cleaning | Chained operations, multiple outputs |
| Data Pipeline | List processing | Filtering, aggregation, formatting |
| API Composition | Service orchestration | Fan-out, caching, resilience |
| Error Handling | Fault tolerance | Retry, fallback, guards, coalesce |
| Complex Pipeline | Business logic | All features combined |
| Recommendation | ML/AI workloads | Embeddings, scoring, lazy eval |
| Communication Ranking | Type algebra | Merge, projection |

---

## See Also

- [Pipeline Structure](./pipeline-structure.md) - Pipeline organization
- [Declarations](./declarations.md) - Input/output syntax
- [Expressions](./expressions.md) - Expression reference
- [Module Options](./module-options.md) - Resilience and caching options
- [Resilient Pipelines](./resilient-pipelines.md) - Production patterns
