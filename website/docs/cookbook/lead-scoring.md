---
title: "Lead Scoring"
sidebar_position: 15
description: "Comprehensive B2B lead scoring with record types, arithmetic, and conditionals"
---

# Lead Scoring

A realistic B2B lead scoring pipeline that demonstrates record types, field access, arithmetic, conditionals, namespace imports, guards, and coalesce — most constellation-lang features in a single pipeline.

## Use Case

Score business leads based on company size, revenue, engagement signals, and text analysis. Classify each lead as hot, warm, or cold.

## The Pipeline

```constellation
# lead-scoring-pipeline.cst

# Type definitions
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

# Namespace imports
use stdlib.math
use stdlib.string as str
use stdlib.compare

# Inputs
in company: CompanyInfo
in engagement: EngagementData

@example("technology,software,AI")
in industryKeywords: String

@example(60)
in minScoreThreshold: Int

@example(2)
in scoreMultiplier: Int

# Feature extraction - text analysis
descriptionText = Trim(engagement.description)
normalizedDesc = Lowercase(descriptionText)
descWordCount = WordCount(normalizedDesc)
hasIndustryMatch = Contains(normalizedDesc, str.trim(industryKeywords))

# Company size scoring (conditional)
isLargeCompany = company.employeeCount > 500
isMediumCompany = company.employeeCount >= 50 and company.employeeCount <= 500
companySizeScore = if (isLargeCompany) 100 else if (isMediumCompany) 70 else 30

# Revenue scoring
revenueBase = company.annualRevenue / 10000
revenueScore = if (revenueBase > 100) 100 else revenueBase

# Engagement scoring
totalEngagement = engagement.websiteVisits + engagement.emailOpens + engagement.contentDownloads
hasHighEngagement = totalEngagement > 10
engagementScore = if (hasHighEngagement) 100 else if (totalEngagement >= 5) 60 else 20

# Text quality scoring
hasDetailedDescription = descWordCount > 50
textQualityScore = if (hasDetailedDescription) 100 else if (descWordCount >= 20) 60 else 25

# Qualification logic (boolean operators)
isQualified = revenueScore >= 50 and engagementScore >= 60
isHighPriority = isLargeCompany or (hasIndustryMatch and hasHighEngagement)

# Weighted final score
rawTotalScore = companySizeScore / 4 + revenueScore * 3 / 10 + engagementScore / 4 + textQualityScore / 5
industryBonus = if (hasIndustryMatch) 15 else 0
adjustedScore = rawTotalScore + industryBonus
finalScore = if (adjustedScore > 100) 100 else adjustedScore

# Classification
isHotLead = finalScore >= 80
isWarmLead = finalScore >= 50 and finalScore < 80
meetsMinimum = finalScore >= minScoreThreshold

# Guard and coalesce for tiered bonuses
tier1Bonus = 50 when finalScore >= 90
tier2Bonus = 30 when finalScore >= 70
tier3Bonus = 10 when finalScore >= 50
tieredBonus = tier1Bonus ?? tier2Bonus ?? tier3Bonus ?? 0

out finalScore
out isHotLead
out isWarmLead
out isQualified
out isHighPriority
out meetsMinimum
out tieredBonus
```

## Explanation

| Section | Features Used |
|---|---|
| Type definitions | `type X = { field: Type }` |
| Namespace imports | `use stdlib.math`, `use stdlib.string as str` |
| Field access | `company.name`, `engagement.description` |
| Module calls | `Trim(...)`, `Lowercase(...)`, `WordCount(...)`, `Contains(...)` |
| Arithmetic | `+`, `-`, `*`, `/` |
| Comparisons | `>`, `>=`, `<=`, `==` |
| Boolean logic | `and`, `or`, `not` |
| Conditionals | `if (cond) x else y` with nesting |
| Guards | `expr when condition` |
| Coalesce | `a ?? b ?? c ?? default` |

## Running the Example

### Input
```json
{
  "company": {
    "name": "Acme Corp",
    "industry": "technology",
    "employeeCount": 250,
    "annualRevenue": 5000000
  },
  "engagement": {
    "websiteVisits": 8,
    "emailOpens": 5,
    "contentDownloads": 3,
    "description": "Acme Corp is a technology company focused on AI and machine learning solutions."
  },
  "industryKeywords": "technology,software,AI",
  "minScoreThreshold": 60,
  "scoreMultiplier": 2
}
```

## Best Practices

1. **Extract features first** — normalize text and compute metrics before scoring
2. **Use intermediate variables** — `isLargeCompany`, `hasHighEngagement` make the scoring logic readable
3. **Cap scores** — use `if (score > 100) 100 else score` to prevent unbounded values
4. **Use guards for tiered logic** — `expr when condition` with chained `??` is cleaner than deeply nested `if/else`

## Related Examples

- [Record Types](record-types.md) — defining and using record types
- [Branch Expressions](branch-expressions.md) — alternative conditional syntax
- [Guard and Coalesce](guard-and-coalesce.md) — optional value patterns
