---
title: "DAG Composition Patterns"
sidebar_position: 1
description: "Comprehensive guide to writing effective pipelines with parallel, sequential, fan-out, conditional, and list processing patterns"
---

# DAG Composition Patterns

This guide covers proven patterns for composing effective Constellation pipelines. Understanding these patterns helps you write pipelines that are clear, maintainable, and performant.

:::tip Key Insight
Constellation automatically detects parallelism from your pipeline structure. When operations don't depend on each other, they run concurrently. Focus on expressing your logic clearly — the DAG compiler handles optimization.
:::

## Table of Contents

1. [Parallel vs Sequential Patterns](#parallel-vs-sequential-patterns)
2. [Fan-Out and Fan-In Patterns](#fan-out-and-fan-in-patterns)
3. [Conditional Branching Patterns](#conditional-branching-patterns)
4. [List Processing Patterns](#list-processing-patterns)
5. [Pipeline Organization Best Practices](#pipeline-organization-best-practices)
6. [Variable Naming Conventions](#variable-naming-conventions)
7. [Performance Optimization Strategies](#performance-optimization-strategies)
8. [Anti-Patterns to Avoid](#anti-patterns-to-avoid)

---

## Parallel vs Sequential Patterns

### Sequential Pattern

Operations that depend on each other form a sequential chain. Each step waits for the previous step to complete.

```constellation
# Sequential pipeline - each step depends on the previous
in text: String

# Step 1: Clean the text
trimmed = Trim(text)

# Step 2: Convert to lowercase (depends on trimmed)
normalized = Lowercase(trimmed)

# Step 3: Count words (depends on normalized)
wordCount = WordCount(normalized)

out wordCount
```

**DAG Visualization:**
```
text
  ↓
Trim → trimmed
  ↓
Lowercase → normalized
  ↓
WordCount → wordCount
```

**When to use:**
- Each operation transforms the output of the previous operation
- The order of operations matters for correctness
- Later operations depend on the results of earlier ones

---

### Parallel Pattern

Operations that use the same inputs but don't depend on each other execute concurrently.

```constellation
# Parallel pipeline - independent operations on same input
in text: String

# All three execute in parallel (no dependencies between them)
wordCount = WordCount(text)
charCount = CharCount(text)
lineCount = LineCount(text)

out wordCount
out charCount
out lineCount
```

**DAG Visualization:**
```
        text
    ┌────┼────┐
    ↓    ↓    ↓
WordCount CharCount LineCount
    ↓    ↓    ↓
wordCount charCount lineCount
```

**When to use:**
- Multiple independent analyses or transformations on the same data
- No operation needs the output of another
- Want to minimize total latency (parallel execution is faster than sequential)

---

### Mixed Pattern

Real pipelines often combine sequential and parallel patterns.

```constellation
# Mixed pattern - combination of sequential and parallel
in rawText: String

# Sequential preprocessing
cleaned = Trim(rawText)
normalized = Lowercase(cleaned)

# Parallel analysis on the normalized text
wordCount = WordCount(normalized)
sentenceCount = SentenceCount(normalized)
avgWordLength = AverageWordLength(normalized)

# Sequential aggregation (depends on all parallel results)
summary = FormatSummary(wordCount, sentenceCount, avgWordLength)

out summary
```

**DAG Visualization:**
```
rawText
  ↓
Trim → cleaned
  ↓
Lowercase → normalized
    ┌─────┼─────┐
    ↓     ↓     ↓
WordCount SentenceCount AverageWordLength
    ↓     ↓     ↓
wordCount sentenceCount avgWordLength
    └─────┼─────┘
          ↓
    FormatSummary → summary
```

**Best practice:**
- Preprocess sequentially to prepare data
- Fan out to parallel analysis
- Aggregate results sequentially

---

## Fan-Out and Fan-In Patterns

### Basic Fan-Out

Fan-out distributes work across multiple independent operations. The DAG compiler automatically parallelizes.

```constellation
# Basic fan-out pattern
in userId: String
in productId: String
in sessionId: String

# Fan-out: three independent service calls execute in parallel
userProfile = FetchUserProfile(userId)
productDetails = FetchProduct(productId)
sessionData = FetchSession(sessionId)

# All three outputs available concurrently
out userProfile
out productDetails
out sessionData
```

**DAG Visualization:**
```
userId  productId  sessionId
  ↓        ↓          ↓
FetchUserProfile FetchProduct FetchSession
  ↓        ↓          ↓
userProfile productDetails sessionData
```

**Performance characteristic:** Total latency is `max(operation1, operation2, operation3)`, not the sum.

---

### Fan-Out with Fan-In

Fan-in merges the results from multiple parallel operations.

```constellation
# Fan-out/fan-in pattern
type Profile = { name: String, email: String }
type Activity = { score: Int, lastSeen: String }
type Preferences = { theme: String, language: String }

in userId: String

# Fan-out: parallel data fetching
profile = FetchProfile(userId)
activity = FetchActivity(userId)
prefs = FetchPreferences(userId)

# Fan-in: merge all data into one record
combined = profile + activity + prefs

# Project specific fields
summary = combined[name, score, theme]

out summary
```

**DAG Visualization:**
```
        userId
    ┌────┼────┐
    ↓    ↓    ↓
FetchProfile FetchActivity FetchPreferences
    ↓    ↓    ↓
profile activity prefs
    └────┼────┘
         ↓
    merge (+) → combined
         ↓
    project ([]) → summary
```

**When to use:**
- Need data from multiple independent sources
- Want to combine results into a single output
- Each source can be queried in parallel

---

### Selective Fan-In

Not all parallel branches need to merge. Select what you need.

```constellation
# Selective fan-in
in requestId: String

# Fan-out to multiple services
profile = SlowService(requestId)
metadata = FastService(requestId)
analytics = OptionalService(requestId)

# Fan-in: use only some results
response = profile + metadata

out response
out analytics  # Separate output, not merged
```

**DAG Visualization:**
```
          requestId
      ┌──────┼──────┐
      ↓      ↓      ↓
SlowService FastService OptionalService
      ↓      ↓      ↓
  profile metadata analytics
      └──────┘      ↓
         ↓       (separate output)
    merge (+) → response
```

---

### Resilient Fan-Out

Add resilience to individual branches with fallbacks, retries, and timeouts.

```constellation
# Resilient fan-out pattern
in userId: String
in fallbackProfile: String

# Each branch has its own resilience strategy
profile = FlakyService(userId) with
    retry: 3,
    timeout: 2s,
    fallback: fallbackProfile

activity = SlowApiCall(userId) with
    cache: 5min,
    timeout: 3s

preferences = ReliableService(userId) with
    cache: 1h

# Fan-in with partial results
result = profile + activity + preferences

out result
```

**When to use:**
- Working with unreliable external services
- Different services have different SLAs
- Want partial results when some services fail

:::note Error Handling
By default, if one branch fails, sibling branches are cancelled and the error propagates. Use `fallback` or `on_error: log` on individual operations to allow partial results.
:::

---

## Conditional Branching Patterns

### Guard Pattern

Use guards to conditionally compute values. Guards produce `Optional<T>` types.

```constellation
# Guard pattern - conditional computation
in score: Int
in tier: String

# Guarded values (Optional<String>)
premiumOffer = "Free upgrade!" when tier == "premium"
highScoreBonus = "Bonus: 500 points" when score > 80

# Unwrap optionals with fallback
offerMessage = premiumOffer ?? "Standard service"
bonusMessage = highScoreBonus ?? "No bonus"

out offerMessage
out bonusMessage
```

**DAG Visualization:**
```
tier    score
  ↓       ↓
  └───┬───┘
      ↓
  guard (when) → premiumOffer (Optional<String>)
      ↓
  coalesce (??) → offerMessage

      score
        ↓
    guard (when) → highScoreBonus (Optional<String>)
        ↓
    coalesce (??) → bonusMessage
```

**When to use:**
- Value only makes sense under certain conditions
- Want explicit handling of "no value" cases
- Building priority-based fallback chains

---

### Branch Pattern

Use `branch` for exhaustive classification where every input maps to exactly one output.

```constellation
# Branch pattern - multi-way classification
in score: Int

# Classify into exactly one tier
tier = branch {
  score >= 90 -> "platinum",
  score >= 70 -> "gold",
  score >= 50 -> "silver",
  otherwise -> "bronze"
}

# Route based on multiple conditions
route = branch {
  score >= 95 -> "vip-lane",
  score >= 80 -> "express-lane",
  score >= 50 -> "standard-lane",
  otherwise -> "bulk-lane"
}

out tier
out route
```

**DAG Visualization:**
```
     score
  ┌────┼────┐
  ↓         ↓
branch    branch
(tier)    (route)
  ↓         ↓
tier      route
```

**When to use:**
- Classify input into one of N categories
- Need exactly one result (not optional)
- Decision tree / routing logic

:::tip Choosing Between Guard and Branch
Use `branch` for exhaustive classification (always get a result). Use `guard` + `??` for optional enrichment (might not get a value).
:::

---

### Combined Guard and Branch

Guards and branches compose naturally for complex conditional logic.

```constellation
# Combined conditional pattern
in score: Int
in tier: String
in subscriptionActive: Boolean

# Branch for base routing
baseRoute = branch {
  tier == "enterprise" -> "enterprise-queue",
  tier == "premium" -> "premium-queue",
  otherwise -> "standard-queue"
}

# Guard for conditional upgrades
fastTrack = "fast-track-enabled" when score > 90 and subscriptionActive
priority = "priority-upgrade" when score > 75 and tier == "premium"

# Priority chain: try fast-track, then priority, then base route
finalRoute = fastTrack ?? priority ?? baseRoute

out finalRoute
```

**DAG Visualization:**
```
tier  score  subscriptionActive
  ↓     ↓           ↓
  └─────┼───────────┘
        ↓
    branch → baseRoute
        ↓
    guard → fastTrack (Optional)
        ↓
    guard → priority (Optional)
        ↓
    coalesce chain (??) → finalRoute
```

---

## List Processing Patterns

### Filter Pattern

Filter lists to keep only elements matching a predicate.

```constellation
# Filter pattern
use stdlib.collection
use stdlib.compare

in numbers: List<Int>
in threshold: Int

# Keep only numbers above threshold
above = filter(numbers, (x) => gt(x, threshold))

# Chain multiple filters
positives = filter(numbers, (x) => gt(x, 0))
large = filter(positives, (x) => gt(x, 100))

out above
out large
```

**DAG Visualization:**
```
numbers  threshold
   ↓        ↓
   └────────┘
        ↓
    filter (above threshold) → above

numbers
   ↓
filter (positives) → positives
   ↓
filter (large) → large
```

**Best practice:** Filter early to reduce the dataset before expensive operations.

---

### Map Pattern

Transform each element in a list.

```constellation
# Map pattern
use stdlib.collection
use stdlib.math

in values: List<Int>
in multiplier: Int

# Transform each element
doubled = map(values, (x) => multiply(x, 2))
scaled = map(values, (x) => multiply(x, multiplier))

out doubled
out scaled
```

**When to use:**
- Apply same transformation to all elements
- Convert list of one type to list of another type

---

### Filter-Map Pattern

Combine filtering and transformation for efficient processing.

```constellation
# Filter-map pattern - filter first, then transform
use stdlib.collection
use stdlib.compare
use stdlib.math

in numbers: List<Int>
in threshold: Int
in multiplier: Int

# Step 1: Filter (reduce dataset size)
filtered = filter(numbers, (x) => gt(x, threshold))

# Step 2: Map (transform only remaining elements)
scaled = map(filtered, (x) => multiply(x, multiplier))

out scaled
```

**DAG Visualization:**
```
numbers  threshold
   ↓        ↓
   └────────┘
        ↓
    filter → filtered
        ↓
multiplier
        ↓
    map → scaled
```

**Performance tip:** `map(filter(list, pred), fn)` is faster than `filter(map(list, fn), pred)` because fewer elements are transformed.

---

### Reduce Pattern

Aggregate a list into a single value.

```constellation
# Reduce pattern - aggregating lists
in numbers: List<Int>

# Multiple independent aggregations (run in parallel)
total = SumList(numbers)
average = Average(numbers)
maximum = Max(numbers)
minimum = Min(numbers)
count = ListLength(numbers)

out total
out average
out maximum
out minimum
out count
```

**DAG Visualization:**
```
         numbers
    ┌──┬──┼──┬──┐
    ↓  ↓  ↓  ↓  ↓
SumList Average Max Min ListLength
    ↓  ↓  ↓  ↓  ↓
total avg max min count
```

**When to use:**
- Need summary statistics
- Want multiple aggregations in parallel
- Converting collection to scalar values

---

### All/Any Pattern

Check if all or any elements satisfy a condition.

```constellation
# All/Any pattern - validation
use stdlib.collection
use stdlib.compare

in scores: List<Int>
in minScore: Int

# Check predicates over the entire list
allPassing = all(scores, (x) => gte(x, minScore))
anyFailing = any(scores, (x) => lt(x, minScore))
allPositive = all(scores, (x) => gt(x, 0))

# Conditional processing based on validation
summary = ProcessScores(scores) when allPassing

out allPassing
out anyFailing
out summary
```

**When to use:**
- Validate batch constraints before processing
- Gate expensive operations on collection properties
- Implement business rules on collections

---

### Complete List Processing Pipeline

Combine filter, map, and reduce for comprehensive data processing.

```constellation
# Complete list processing pipeline
use stdlib.collection
use stdlib.compare
use stdlib.math

in numbers: List<Int>
in threshold: Int
in multiplier: Int

# Step 1: Filter early
filtered = filter(numbers, (x) => gt(x, threshold))

# Step 2: Transform
scaled = map(filtered, (x) => multiply(x, multiplier))

# Step 3: Aggregate (multiple operations in parallel)
total = SumList(scaled)
avg = Average(scaled)
count = ListLength(scaled)

# Step 4: Format results
formattedTotal = FormatNumber(total)

out total
out avg
out count
out formattedTotal
```

**DAG Visualization:**
```
numbers  threshold
   ↓        ↓
   └────────┘
        ↓
    filter → filtered
        ↓
   multiplier
        ↓
    map → scaled
    ┌───┼───┐
    ↓   ↓   ↓
SumList Average ListLength
    ↓   ↓   ↓
total avg count
    ↓
FormatNumber → formattedTotal
```

---

## Pipeline Organization Best Practices

### 1. Organize by Concern

Group related operations into logical sections with comments.

```constellation
# Well-organized pipeline
in rawData: String

# ===== Input Validation =====
trimmed = Trim(rawData)
isValid = ValidateFormat(trimmed)
validated = trimmed when isValid

# ===== Data Extraction =====
normalized = Lowercase(validated ?? "")
tokens = Tokenize(normalized)
entities = ExtractEntities(normalized)

# ===== Feature Engineering =====
tokenCount = ListLength(tokens)
entityCount = ListLength(entities)
avgTokenLength = AverageTokenLength(tokens)

# ===== Scoring =====
qualityScore = ComputeQuality(tokenCount, entityCount)
finalScore = qualityScore * avgTokenLength

out finalScore
out entities
```

**Benefits:**
- Easy to understand pipeline flow
- Clear responsibilities for each section
- Easier to maintain and extend

---

### 2. Use Descriptive Variable Names

Variables should describe the data they contain, not the operation that created them.

```constellation
# Good: descriptive names
cleanedText = Trim(rawText)
normalizedText = Lowercase(cleanedText)
wordCount = WordCount(normalizedText)

# Bad: operation-focused names
trimResult = Trim(rawText)
lowercaseResult = Lowercase(trimResult)
countResult = WordCount(lowercaseResult)
```

---

### 3. Extract Intermediate Values

Don't nest deep expressions. Extract intermediate values for clarity.

```constellation
# Good: clear intermediate steps
in text: String

trimmed = Trim(text)
normalized = Lowercase(trimmed)
wordCount = WordCount(normalized)
isLong = wordCount > 100
embeddings = ComputeEmbeddings(normalized) when isLong

out embeddings

# Bad: deeply nested
embeddings = ComputeEmbeddings(Lowercase(Trim(text))) when WordCount(Lowercase(Trim(text))) > 100
```

**Benefits:**
- Easier to debug (can inspect intermediate values)
- Clearer data dependencies for the DAG compiler
- More readable and maintainable

---

### 4. Declare Types for Complex Records

Define record types at the top of your pipeline for complex data structures.

```constellation
# Good: explicit type declarations
type UserProfile = {
  id: String,
  name: String,
  email: String,
  score: Int
}

type Enrichment = {
  timestamp: String,
  source: String,
  campaign: String
}

in user: UserProfile
in enrichment: Enrichment

enrichedUser = user + enrichment

out enrichedUser

# Acceptable for simple cases
in simpleRecord: { id: String, value: Int }
```

---

### 5. Separate Data Flow from Control Flow

Keep data transformations and conditional logic separate for clarity.

```constellation
# Good: separation of concerns
in rawScore: Int
in tier: String

# Data transformations
normalizedScore = rawScore / 100
cappedScore = if (normalizedScore > 1.0) 1.0 else normalizedScore

# Control flow decisions
isPremium = tier == "premium"
isHighScore = cappedScore > 0.8

# Conditional outputs
bonus = "Premium bonus applied" when isPremium and isHighScore
finalScore = cappedScore + 0.1 when bonus != None else cappedScore

out finalScore
out bonus
```

---

## Variable Naming Conventions

### Naming Patterns by Type

| Data Type | Naming Pattern | Examples |
|-----------|---------------|----------|
| Boolean | `is`, `has`, `should`, `can` prefix | `isValid`, `hasError`, `shouldRetry`, `canProcess` |
| Count/Length | `count`, `length`, `size`, `num` suffix | `wordCount`, `listLength`, `batchSize`, `numItems` |
| Transformed Data | Descriptive adjective + noun | `cleanedText`, `normalizedScore`, `enrichedUser` |
| Aggregates | Operation + noun | `totalRevenue`, `averageScore`, `maxValue`, `minThreshold` |
| Collections | Plural nouns | `users`, `scores`, `items`, `results` |
| Records | Singular nouns | `user`, `profile`, `request`, `response` |

---

### Good Naming Examples

```constellation
# Boolean variables
isAuthenticated = ValidateToken(token)
hasPermission = CheckPermission(userId, resource)
shouldCache = responseTime > 100
canRetry = attemptCount < 3

# Counts and sizes
wordCount = WordCount(text)
itemCount = ListLength(items)
batchSize = 100

# Transformed data
cleanedText = Trim(rawText)
normalizedEmail = Lowercase(email)
enrichedProfile = profile + metadata
scaledScores = map(scores, (x) => multiply(x, 2))

# Aggregates
totalRevenue = SumList(revenues)
averageScore = Average(scores)
maxValue = Max(values)
highestRated = First(SortDescending(items))

# Collections vs. Records
users = FetchUsers(query)           # Collection (plural)
user = SelectFirst(users)           # Single record (singular)
activeUsers = filter(users, (u) => u.active)  # Collection
```

---

### Avoid These Naming Patterns

```constellation
# ❌ Avoid: Generic names
result1 = Process(data)
result2 = Transform(result1)
temp = Filter(result2)

# ✅ Better: Descriptive names
validatedData = Process(data)
normalizedData = Transform(validatedData)
filteredData = Filter(normalizedData)

# ❌ Avoid: Abbreviations
usrProf = FetchUser(id)
procRes = ProcessRequest(req)

# ✅ Better: Full words
userProfile = FetchUser(id)
processedResponse = ProcessRequest(request)

# ❌ Avoid: Type information in names
stringName = GetName(user)
intScore = GetScore(user)

# ✅ Better: Semantic meaning
userName = GetName(user)
userScore = GetScore(user)
```

---

## Performance Optimization Strategies

### 1. Filter Early, Project Late

Reduce dataset size as early as possible, but keep fields you might need.

```constellation
# Optimized: filter early
in items: List<Item>
in threshold: Int

# Step 1: Filter to reduce dataset
aboveThreshold = filter(items, (x) => gt(x.value, threshold))

# Step 2: Expensive operations on smaller dataset
enriched = EnrichItems(aboveThreshold)
scored = ScoreItems(enriched)

# Step 3: Project at the end (once you know what you need)
final = scored[id, score, category]

out final
```

**Why:** Processing fewer items through expensive operations is faster.

---

### 2. Leverage Automatic Parallelism

Structure independent operations to run in parallel.

```constellation
# Optimized: parallel independent operations
in userId: String

# These run in parallel automatically
profile = FetchProfile(userId)
orders = FetchOrders(userId)
preferences = FetchPreferences(userId)
recommendations = ComputeRecommendations(userId)

# Fan-in happens after all parallel work completes
summary = profile + orders + preferences

out summary
out recommendations
```

**Why:** Total latency = `max(operations)`, not `sum(operations)`.

---

### 3. Cache Expensive Operations

Add caching to reduce redundant computation.

```constellation
# Optimized: strategic caching
in query: String

# Cache expensive lookups
embeddings = ComputeEmbeddings(query) with cache: 1h
similarDocs = FindSimilar(embeddings) with cache: 30min

# Don't cache fast operations
formatted = FormatResults(similarDocs)

out formatted
```

**When to cache:**
- Expensive computations (ML models, embeddings)
- External API calls
- Operations with deterministic results

**When NOT to cache:**
- Fast operations (string manipulation)
- Non-deterministic operations
- Real-time data that changes frequently

---

### 4. Use Resilience Patterns Strategically

Add retries and timeouts where they matter most.

```constellation
# Optimized: selective resilience
in requestId: String

# Critical path: aggressive resilience
criticalData = FetchCritical(requestId) with
    retry: 3,
    timeout: 1s,
    fallback: defaultCritical

# Non-critical path: lighter resilience
enrichmentData = FetchEnrichment(requestId) with
    timeout: 500ms,
    on_error: log

# No resilience needed for fast, reliable operations
formatted = FormatResponse(criticalData)

out formatted
out enrichmentData
```

---

### 5. Batch Operations When Possible

Process collections in batches instead of individual items.

```constellation
# Optimized: batch processing
type Item = { id: String, value: Int }

in items: Candidates<Item>
in context: Context

# Single batch operation (efficient)
enriched = items + context

# Single batch projection
final = enriched[id, value, userId]

out final

# ❌ Avoid: processing items individually in loops
# Use Candidates<T> and let the runtime batch automatically
```

---

### 6. Conditional Execution for Expensive Operations

Use guards to avoid unnecessary computation.

```constellation
# Optimized: conditional expensive operations
in text: String
in mode: String

# Only compute embeddings when needed
needsEmbeddings = mode == "semantic"
embeddings = ComputeExpensiveEmbeddings(text) when needsEmbeddings

# Fallback to cheaper alternative
keywords = ExtractKeywords(text)

# Use embeddings if available, otherwise keywords
searchTerms = embeddings ?? keywords

out searchTerms
```

---

## Anti-Patterns to Avoid

### ❌ Anti-Pattern 1: Over-Parallelization

**Problem:** Creating too many tiny parallel operations that add coordination overhead.

```constellation
# ❌ Bad: over-parallelization
in text: String

# Each operation is very fast - parallelism adds overhead
char1 = GetChar(text, 0)
char2 = GetChar(text, 1)
char3 = GetChar(text, 2)
char4 = GetChar(text, 3)

combined = char1 + char2 + char3 + char4
```

**Solution:** Group related fast operations sequentially.

```constellation
# ✅ Good: appropriate granularity
in text: String

substring = Substring(text, 0, 4)
out substring
```

**Rule of thumb:** Parallelize operations that take >10ms. For faster operations, sequential execution may be more efficient.

---

### ❌ Anti-Pattern 2: Unnecessary Dependencies

**Problem:** Creating artificial dependencies that prevent parallelism.

```constellation
# ❌ Bad: unnecessary sequential execution
in userId: String

profile = FetchProfile(userId)
_ = LogFetch("profile")  # Artificial dependency
orders = FetchOrders(userId)
_ = LogFetch("orders")   # Artificial dependency
prefs = FetchPreferences(userId)
```

**Solution:** Remove unnecessary dependencies.

```constellation
# ✅ Good: independent operations
in userId: String

# All three fetch in parallel
profile = FetchProfile(userId)
orders = FetchOrders(userId)
prefs = FetchPreferences(userId)

# Log after all complete (if needed)
_ = LogCompletion("all-fetched")
```

---

### ❌ Anti-Pattern 3: Premature Optimization

**Problem:** Adding complexity for unproven performance gains.

```constellation
# ❌ Bad: premature caching and complexity
in query: String

# Caching everything "just in case"
tokens = Tokenize(query) with cache: 1h
normalized = Normalize(tokens) with cache: 1h
cleaned = Clean(normalized) with cache: 1h
formatted = Format(cleaned) with cache: 1h

out formatted
```

**Solution:** Start simple, measure, then optimize.

```constellation
# ✅ Good: simple first
in query: String

tokens = Tokenize(query)
normalized = Normalize(tokens)
cleaned = Clean(normalized)
formatted = Format(cleaned)

out formatted

# Add caching later if profiling shows bottlenecks
```

**Best practice:** Write clear code first. Profile to find actual bottlenecks. Optimize only where measurements show benefit.

---

### ❌ Anti-Pattern 4: Deep Expression Nesting

**Problem:** Deeply nested expressions are hard to read and debug.

```constellation
# ❌ Bad: deep nesting
out FormatOutput(
    Aggregate(
        Transform(
            Filter(
                Normalize(
                    Clean(rawData)
                )
            )
        )
    )
)
```

**Solution:** Extract intermediate values with descriptive names.

```constellation
# ✅ Good: clear intermediate steps
cleaned = Clean(rawData)
normalized = Normalize(cleaned)
filtered = Filter(normalized)
transformed = Transform(filtered)
aggregated = Aggregate(transformed)
output = FormatOutput(aggregated)

out output
```

---

### ❌ Anti-Pattern 5: Magic Numbers and Strings

**Problem:** Hardcoded values make pipelines inflexible and hard to understand.

```constellation
# ❌ Bad: magic numbers
in score: Int

tier = branch {
  score >= 90 -> "platinum",
  score >= 70 -> "gold",
  score >= 50 -> "silver",
  otherwise -> "bronze"
}

bonus = 100 when score > 85
```

**Solution:** Use named inputs for thresholds and configuration.

```constellation
# ✅ Good: parameterized
@example(90)
in platinumThreshold: Int

@example(70)
in goldThreshold: Int

@example(50)
in silverThreshold: Int

@example(85)
in bonusThreshold: Int

@example(100)
in bonusAmount: Int

in score: Int

tier = branch {
  score >= platinumThreshold -> "platinum",
  score >= goldThreshold -> "gold",
  score >= silverThreshold -> "silver",
  otherwise -> "bronze"
}

bonus = bonusAmount when score > bonusThreshold
```

---

### ❌ Anti-Pattern 6: Ignoring Type Safety

**Problem:** Using stringly-typed data instead of structured records.

```constellation
# ❌ Bad: stringly-typed
in userData: String  # JSON string "{"id": "123", "name": "Alice"}"

# Parsing and extracting manually
userId = ExtractUserId(userData)
userName = ExtractUserName(userData)
```

**Solution:** Use structured record types.

```constellation
# ✅ Good: type-safe
type User = {
  id: String,
  name: String,
  email: String
}

in user: User

# Direct field access (compile-time validated)
userId = user.id
userName = user.name

out userId
out userName
```

---

### ❌ Anti-Pattern 7: Duplicate Logic

**Problem:** Repeating the same computation multiple times.

```constellation
# ❌ Bad: duplicate computation
in text: String

wordCount1 = WordCount(Lowercase(Trim(text)))
wordCount2 = WordCount(Lowercase(Trim(text)))
isLong = WordCount(Lowercase(Trim(text))) > 100
```

**Solution:** Compute once, reuse the result.

```constellation
# ✅ Good: compute once
in text: String

trimmed = Trim(text)
normalized = Lowercase(trimmed)
wordCount = WordCount(normalized)
isLong = wordCount > 100

out wordCount
out isLong
```

---

### ❌ Anti-Pattern 8: Overly Complex Conditionals

**Problem:** Nested conditionals become hard to reason about.

```constellation
# ❌ Bad: complex nested conditionals
result = if (a) (if (b) (if (c) x else y) else z) else w
```

**Solution:** Use branch expressions or extract intermediate booleans.

```constellation
# ✅ Good: branch expression
result = branch {
  a and b and c -> x,
  a and b -> y,
  a -> z,
  otherwise -> w
}

# ✅ Also good: named intermediates
isCase1 = a and b and c
isCase2 = a and b
isCase3 = a

result = branch {
  isCase1 -> x,
  isCase2 -> y,
  isCase3 -> z,
  otherwise -> w
}
```

---

## Visual DAG Examples

### Example 1: E-Commerce Order Processing

```constellation
# E-commerce order processing pipeline
type Order = { orderId: String, userId: String, total: Int }
type User = { userId: String, tier: String }

in order: Order
in user: User

# Parallel validation and enrichment
orderValid = ValidateOrder(order)
userValid = ValidateUser(user)
inventory = CheckInventory(order.orderId)

# Sequential processing after validation
allValid = orderValid and userValid
processOrder = ProcessPayment(order) when allValid

# Parallel post-processing
invoice = GenerateInvoice(order) when processOrder != None
notification = SendNotification(user) when processOrder != None
analytics = LogAnalytics(order, user)

out processOrder
out invoice
out notification
```

**DAG Visualization:**
```
order  user
  ↓     ↓
  ├─────┼─────┐
  ↓     ↓     ↓
ValidateOrder ValidateUser CheckInventory
  ↓     ↓     ↓
orderValid userValid inventory
  └──┬──┘
     ↓
  allValid (and)
     ↓
ProcessPayment (when allValid) → processOrder
     ↓
  ┌──┼──┐
  ↓  ↓  ↓
GenerateInvoice SendNotification LogAnalytics
  ↓  ↓  ↓
invoice notification analytics
```

---

### Example 2: Content Recommendation Pipeline

```constellation
# Content recommendation pipeline
in userId: String
in contextTags: List<String>

# Fan-out: parallel data gathering
userHistory = FetchHistory(userId) with cache: 5min
userPrefs = FetchPreferences(userId) with cache: 1h
trendingContent = FetchTrending() with cache: 10min

# Parallel analysis
historyEmbeddings = ComputeEmbeddings(userHistory)
contextEmbeddings = ComputeEmbeddings(contextTags)

# Similarity search (depends on embeddings)
historySimilar = FindSimilar(historyEmbeddings)
contextSimilar = FindSimilar(contextEmbeddings)

# Merge and rank
allCandidates = historySimilar + contextSimilar
rankedResults = RankByRelevance(allCandidates, userPrefs)

# Filter and format
topResults = TakeTop(rankedResults, 10)
formatted = FormatRecommendations(topResults)

out formatted
```

**DAG Visualization:**
```
userId  contextTags
  ↓         ↓
  ├───┬─────┼───┐
  ↓   ↓     ↓   ↓
FetchHistory FetchPreferences FetchTrending ComputeEmbeddings
  ↓   ↓     ↓         ↓               (contextTags)
  │   │     │    ComputeEmbeddings        ↓
  │   │     │    (userHistory)      contextEmbeddings
  │   │     │         ↓                   ↓
  │   │     │    historyEmbeddings   FindSimilar
  │   │     │         ↓                   ↓
  │   │     │    FindSimilar         contextSimilar
  │   │     │         ↓                   │
  │   │     │    historySimilar           │
  │   │     │         └───────┬───────────┘
  │   │     │                 ↓
  │   │     │           allCandidates (+)
  │   │     └─────────────────┼───────────┐
  │   │                       ↓           │
  │   └──────────────────RankByRelevance  │
  │                           ↓           │
  │                      rankedResults    │
  │                           ↓           │
  │                        TakeTop        │
  │                           ↓           │
  │                       topResults      │
  │                           ↓           │
  └────────────────────FormatRecommendations
                              ↓
                          formatted
```

---

## Summary

### Key Takeaways

1. **Automatic Parallelism**: Focus on expressing logic clearly. The DAG compiler optimizes execution.

2. **Pattern Selection**:
   - Use **sequential** for data transformations where order matters
   - Use **parallel** for independent operations
   - Use **fan-out/fan-in** for aggregating data from multiple sources
   - Use **guards** for optional values with fallbacks
   - Use **branches** for exhaustive classification

3. **Organization**:
   - Group related operations
   - Use descriptive variable names
   - Extract intermediate values
   - Separate data flow from control flow

4. **Performance**:
   - Filter early, project late
   - Leverage automatic parallelism
   - Cache strategically
   - Use resilience where it matters

5. **Avoid Anti-Patterns**:
   - Don't over-parallelize tiny operations
   - Don't create artificial dependencies
   - Don't prematurely optimize
   - Don't nest expressions deeply
   - Don't hardcode magic numbers
   - Don't ignore type safety

### Further Reading

- [Fan-Out/Fan-In Cookbook](/docs/cookbook/fan-out-fan-in) — Practical fan-out patterns
- [Conditional Branching Cookbook](/docs/cookbook/conditional-branching) — Guards, coalesce, and branches
- [Data Pipeline Cookbook](/docs/cookbook/data-pipeline) — Multi-step data processing
- [Orchestration Algebra](/docs/language/orchestration-algebra) — Boolean-based control flow
- [Lambdas and HOF Cookbook](/docs/cookbook/lambdas-and-hof) — List processing patterns

---

**Remember:** Well-structured pipelines are clear, maintainable, and performant. Let the DAG compiler handle optimization while you focus on expressing your domain logic clearly.
