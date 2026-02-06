---
title: "Comments"
sidebar_position: 11
description: "Learn how to write single-line and multi-line comments to document your constellation-lang pipelines."
---

# Comments

Comments in constellation-lang help document your pipelines, explain complex logic, and make your code more maintainable. Well-commented pipelines are easier to understand, debug, and collaborate on.

## Single-Line Comments

Line comments start with the `#` character. Everything from `#` to the end of the line is ignored by the compiler.

```constellation
# This is a full-line comment
in text: String

# Comments can explain why something is done
upper = Uppercase(text)  # Inline comment after code

out upper
```

## Multi-Line Comments

For longer explanations, use multiple consecutive `#` comments:

```constellation
# =============================================================================
# Text Processing Pipeline
# =============================================================================
# This pipeline performs multi-step text analysis:
# 1. Cleans and normalizes the input
# 2. Extracts key metrics (word count, character count)
# 3. Identifies the primary language
#
# Author: Data Team
# Version: 1.2.0
# =============================================================================
```

## Documentation Comments for Inputs

Document your pipeline inputs to explain their purpose, expected format, and any constraints:

```constellation
# User identifier for personalization
# Format: UUID string (e.g., "550e8400-e29b-41d4-a716-446655440000")
# Required: Yes
@example("550e8400-e29b-41d4-a716-446655440000")
in userId: String

# Search query from the user
# Can contain multiple terms separated by spaces
# Empty strings will return no results
@example("machine learning tutorial")
in query: String

# Maximum number of results to return
# Must be between 1 and 100
# Default recommendation: 10
@example(10)
in limit: Int
```

## Documentation Comments for Outputs

Explain what each output represents and how it should be interpreted:

```constellation
# Processing results
result = ProcessData(input)

# Output: processed result
# Type: { score: Float, category: String }
# score: confidence score between 0.0 and 1.0
# category: one of "high", "medium", "low"
out result
```

## Documenting Type Definitions

Complex types benefit from documentation explaining their structure and purpose:

```constellation
# Represents a customer order in the e-commerce system
# Used for order processing, fulfillment, and analytics
type Order = {
  id: String,           # Unique order identifier (UUID)
  customerId: String,   # Reference to the customer record
  items: List<OrderItem>,  # Line items in the order
  total: Float,         # Total amount in USD
  status: String        # Order status: pending, confirmed, shipped, delivered
}

# Individual line item within an order
type OrderItem = {
  sku: String,          # Product SKU
  quantity: Int,        # Number of units ordered
  unitPrice: Float      # Price per unit in USD
}
```

## Section Headers

Use visual separators to organize larger pipelines into logical sections:

```constellation
# =============================================================================
# CONFIGURATION
# =============================================================================

type Config = { timeout: Int, retries: Int }

# =============================================================================
# INPUTS
# =============================================================================

@example("user-123")
in userId: String

@example(30)
in maxResults: Int

# =============================================================================
# DATA FETCHING
# =============================================================================

# Fetch user profile with retry logic
userData = FetchUser(userId) with retry: 3, timeout: 5s

# =============================================================================
# PROCESSING
# =============================================================================

# Transform and enrich the data
enriched = EnrichData(userData)

# =============================================================================
# OUTPUT
# =============================================================================

out enriched
```

## Explaining Module Options

Document why specific options are used:

```constellation
# External API call with resilience options
# - retry: 3 attempts for transient failures (network issues, 503s)
# - timeout: 5s to prevent blocking on slow responses
# - backoff: exponential to avoid overwhelming a struggling service
# - fallback: cached data to maintain service availability
apiResult = ExternalApi(request) with
    retry: 3,
    timeout: 5s,
    backoff: exponential,
    fallback: cachedDefault
```

## Documenting Complex Logic

When using advanced features like guards, coalesce, or branches, explain the business logic:

```constellation
# =============================================================================
# Lead Scoring Logic
# =============================================================================
# Tiered bonus system based on final score:
# - Tier 1 (score >= 90): Premium lead, gets 50 point bonus
# - Tier 2 (score >= 70): Qualified lead, gets 30 point bonus
# - Tier 3 (score >= 50): Potential lead, gets 10 point bonus
# - Below 50: No bonus
#
# Guards produce Optional<Int>, coalesce chain picks first applicable tier
tier1Bonus = 50 when finalScore >= 90
tier2Bonus = 30 when finalScore >= 70
tier3Bonus = 10 when finalScore >= 50
tieredBonus = tier1Bonus ?? tier2Bonus ?? tier3Bonus ?? 0
```

## Best Practices

### Do: Explain Why, Not What

```constellation
# GOOD: Explains the business reasoning
# Cache for 5 minutes because user preferences rarely change within a session
prefs = LoadPreferences(userId) with cache: 5min

# BAD: Just restates the code
# Load preferences with 5 minute cache
prefs = LoadPreferences(userId) with cache: 5min
```

### Do: Document Assumptions and Constraints

```constellation
# GOOD: Documents important assumption
# Assumes scores are normalized to 0-100 range by upstream module
normalizedScore = score / 100.0

# GOOD: Documents constraint
# Must not exceed 1000 items due to downstream API limits
@example(100)
in batchSize: Int
```

### Do: Keep Comments Up to Date

When you change code, update the corresponding comments. Outdated comments are worse than no comments.

### Do: Use Consistent Formatting

Pick a style and stick with it throughout your pipeline:

```constellation
# === Section Headers Use Triple Equals ===
# - List items use dashes
# Regular explanations use plain text
# TODO: Mark incomplete items clearly
# FIXME: Mark known issues
# NOTE: Important callouts
```

### Don't: Over-Comment Simple Code

```constellation
# BAD: Too much commenting for obvious code
# Declare input named 'text' of type String
in text: String

# Convert text to uppercase using Uppercase module
upper = Uppercase(text)

# Output the uppercase result
out upper
```

```constellation
# GOOD: Let clear code speak for itself
in text: String
upper = Uppercase(text)
out upper
```

### Don't: Leave Commented-Out Code

```constellation
# BAD: Commented-out code clutters the pipeline
# oldResult = OldModule(data)
# deprecatedStep = DeprecatedModule(oldResult)
result = NewModule(data)

# GOOD: Remove unused code, use version control for history
result = NewModule(data)
```

## Complete Example: Well-Commented Pipeline

```constellation
# =============================================================================
# Customer Segmentation Pipeline
# =============================================================================
# Analyzes customer data to determine marketing segment and personalization
# strategy. Used by the marketing automation system for campaign targeting.
#
# Inputs:
#   - customer: Customer profile record
#   - purchaseHistory: Recent purchases (last 90 days)
#
# Outputs:
#   - segment: Customer segment classification
#   - recommendations: Personalized product suggestions
#   - engagementScore: Predicted engagement likelihood (0-100)
#
# Dependencies:
#   - CustomerAnalytics module (v2.1+)
#   - RecommendationEngine module
# =============================================================================

# -----------------------------------------------------------------------------
# Type Definitions
# -----------------------------------------------------------------------------

# Core customer data from CRM system
type Customer = {
  id: String,           # CRM customer ID
  tier: String,         # Loyalty tier: bronze, silver, gold, platinum
  joinDate: String,     # ISO 8601 date when customer joined
  totalSpend: Float     # Lifetime spend in USD
}

# Purchase record from order management system
type Purchase = {
  orderId: String,
  amount: Float,
  category: String,     # Product category code
  timestamp: String     # ISO 8601 timestamp
}

# -----------------------------------------------------------------------------
# Inputs
# -----------------------------------------------------------------------------

# Customer profile - required for segmentation
@example({ id: "cust-12345", tier: "gold", joinDate: "2023-01-15", totalSpend: 2500.00 })
in customer: Customer

# Recent purchase history - empty list OK for new customers
@example([])
in purchaseHistory: List<Purchase>

# -----------------------------------------------------------------------------
# Feature Extraction
# -----------------------------------------------------------------------------

# Calculate purchase frequency and recency metrics
# These are key predictors of future engagement
purchaseMetrics = AnalyzePurchases(purchaseHistory)

# Combine customer profile with behavioral data
# Type: { ...Customer, avgOrderValue: Float, purchaseFrequency: Int }
enrichedProfile = customer + purchaseMetrics

# -----------------------------------------------------------------------------
# Segmentation Logic
# -----------------------------------------------------------------------------

# Determine segment using ML model
# Cache for 1 hour - segment rarely changes within a day
# Retry on failure since this is a critical step
segment = SegmentationModel(enrichedProfile) with
    cache: 1h,
    retry: 3,
    timeout: 5s

# Generate recommendations based on segment and history
# Lower priority since it's enhancement, not critical path
recommendations = RecommendationEngine(segment, purchaseHistory) with
    priority: low,
    timeout: 10s,
    on_error: skip  # OK to proceed without recommendations

# Calculate engagement score for campaign targeting
# Score of 0-100 indicating likelihood of response
engagementScore = PredictEngagement(enrichedProfile, segment)

# -----------------------------------------------------------------------------
# Outputs
# -----------------------------------------------------------------------------

out segment           # String: segment name (e.g., "high-value-frequent")
out recommendations   # List<String>: product SKUs to recommend
out engagementScore   # Int: predicted engagement likelihood 0-100
```

---

## See Also

- [Pipeline Structure](./pipeline-structure.md) - Overall pipeline organization
- [Declarations](./declarations.md) - Input and output declarations
- [Examples](./examples.md) - Complete pipeline examples
