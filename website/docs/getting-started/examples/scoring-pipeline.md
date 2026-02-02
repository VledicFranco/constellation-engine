---
title: "Scoring Pipeline"
sidebar_position: 7
---

# Scoring Pipeline

Build a multi-factor scoring system with conditional logic - essential for lead scoring, risk assessment, and ranking systems.

## Use Case

You need to score entities based on multiple factors:
- Combine numeric scores with weights
- Apply conditional adjustments
- Classify into tiers based on final score

This pattern is used in lead scoring, credit risk, recommendation ranking, and more.

## The Pipeline

```
# scoring-pipeline.cst
# Multi-factor scoring with conditional tier assignment

use stdlib.math
use stdlib.comparison

type Candidate = {
  id: String,
  engagementScore: Int,
  recencyScore: Int,
  frequencyScore: Int
}

type Weights = {
  engagementWeight: Int,
  recencyWeight: Int,
  frequencyWeight: Int,
  threshold: Int
}

in candidate: Candidate
in weights: Weights

# Calculate weighted component scores
engagementContrib = multiply(candidate.engagementScore, weights.engagementWeight)
recencyContrib = multiply(candidate.recencyScore, weights.recencyWeight)
frequencyContrib = multiply(candidate.frequencyScore, weights.frequencyWeight)

# Combine into total score
step1 = add(engagementContrib, recencyContrib)
totalScore = add(step1, frequencyContrib)

# Determine if above threshold
isQualified = gte(totalScore, weights.threshold)

out totalScore
out isQualified
```

## Explanation

| Component | Description |
|-----------|-------------|
| Weighted scores | Multiply raw scores by configurable weights |
| Score combination | Add weighted components together |
| Threshold check | Compare against configurable threshold |

## Running the Example

### Input
```json
{
  "candidate": {
    "id": "lead-123",
    "engagementScore": 8,
    "recencyScore": 6,
    "frequencyScore": 4
  },
  "weights": {
    "engagementWeight": 3,
    "recencyWeight": 2,
    "frequencyWeight": 1,
    "threshold": 40
  }
}
```

### Calculation
- Engagement: 8 * 3 = 24
- Recency: 6 * 2 = 12
- Frequency: 4 * 1 = 4
- Total: 24 + 12 + 4 = 40
- Qualified: 40 >= 40 = true

### Expected Output
```json
{
  "totalScore": 40,
  "isQualified": true
}
```

## Variations

### With Conditional Bonus

Apply conditional score adjustments:

```
use stdlib.math
use stdlib.comparison

in baseScore: Int
in isPremium: Boolean
in bonusAmount: Int

# Apply bonus only if premium
bonus = if (isPremium) bonusAmount else 0
finalScore = add(baseScore, bonus)

out finalScore
```

### Multi-tier Classification

Classify into multiple tiers:

```
use stdlib.comparison

in score: Int

# Tier thresholds
isHot = gte(score, 80)
isWarm = and(gte(score, 50), lt(score, 80))
isCold = lt(score, 50)

out isHot
out isWarm
out isCold
```

### Risk Scoring

Calculate risk with multiple factors:

```
use stdlib.math
use stdlib.comparison

type RiskFactors = {
  creditScore: Int,
  debtRatio: Int,
  paymentHistory: Int
}

in factors: RiskFactors
in maxRisk: Int

# Lower credit score = higher risk
creditRisk = subtract(100, factors.creditScore)

# Higher debt ratio = higher risk
debtRisk = factors.debtRatio

# Lower payment history = higher risk
historyRisk = subtract(100, factors.paymentHistory)

# Combine risks (simple average approximation)
combined = add(creditRisk, debtRisk)
totalRisk = add(combined, historyRisk)

# Check if exceeds threshold
isHighRisk = gt(totalRisk, maxRisk)

out totalRisk
out isHighRisk
```

### Batch Scoring with Enrichment

Score batched candidates with context:

```
use stdlib.math

type Item = {
  id: String,
  relevanceScore: Int,
  qualityScore: Int
}

type Context = {
  boostFactor: Int
}

in items: Candidates<Item>
in context: Context

# Enrich each item with context
enriched = items + context

# Select for output (scoring would happen in custom module)
output = enriched[id, relevanceScore, qualityScore, boostFactor]

out output
```

### Score Normalization

Normalize scores to a standard range:

```
use stdlib.math
use stdlib.comparison

in rawScore: Int
in minPossible: Int
in maxPossible: Int

# Calculate range
range = subtract(maxPossible, minPossible)

# Normalize to 0-100 scale
adjusted = subtract(rawScore, minPossible)
normalized = divide(multiply(adjusted, 100), range)

# Clamp to valid range
tooLow = lt(normalized, 0)
tooHigh = gt(normalized, 100)
clampedLow = if (tooLow) 0 else normalized
finalScore = if (tooHigh) 100 else clampedLow

out finalScore
```

## Scoring Patterns

### Linear Combination
```
score = w1*f1 + w2*f2 + w3*f3
```
Most common pattern. Each factor contributes proportionally.

### Threshold Gates
```
qualified = (score1 >= t1) AND (score2 >= t2)
```
All conditions must be met.

### Tiered Classification
```
tier = HOT if score >= 80
     = WARM if score >= 50
     = COLD otherwise
```
Mutually exclusive categories.

### Conditional Adjustments
```
finalScore = baseScore + (isPremium ? bonus : 0)
```
Apply adjustments based on flags.

## Real-World Applications

### Lead Scoring
- Engagement + recency + fit = lead quality
- Classify into sales-ready, nurture, discard

### Credit Risk
- Payment history + debt ratio + credit age = risk score
- Approve/review/decline decisions

### Search Ranking
- Relevance + freshness + authority = rank
- Apply personalization boosts

### Content Recommendations
- User affinity + item quality + freshness = recommendation score
- Filter by minimum threshold

## Best Practices

1. **Externalize weights**: Make weights configurable, not hardcoded
2. **Document thresholds**: Explain what each threshold means
3. **Test edge cases**: Zero scores, negative values, overflow
4. **Log intermediate scores**: For debugging and explainability

## Related Examples

- [Batch Enrichment](batch-enrichment.md) - Prepare data for scoring
- [Data Statistics Pipeline](data-statistics.md) - Calculate aggregate stats
- [List Processing Pipeline](list-processing.md) - Filter scored results
