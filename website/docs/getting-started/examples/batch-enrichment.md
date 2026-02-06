---
title: "Batch Enrichment"
sidebar_position: 6
description: "Enrich batched candidates with context data using merge and projection operators for ML ranking pipelines."
---

# Batch Data Enrichment Pipeline

Enrich batched ML candidates with context data - a core pattern for recommendation systems and ranking pipelines.

## Use Case

You have a batch of candidate items (e.g., products, recommendations, search results) and need to:
- Add contextual information to each item
- Select specific fields for downstream processing
- Combine data from multiple sources

This is fundamental to ML ranking and personalization systems.

## The Pipeline

```
# batch-enrichment.cst
# Enrich candidate items with user context

type Item = {
  id: String,
  title: String,
  score: Float,
  category: String
}

type UserContext = {
  userId: Int,
  region: String,
  tier: String
}

in items: Candidates<Item>
in context: UserContext

# Merge: adds context fields to EACH item
enriched = items + context

# Project: select only needed fields from each item
output = enriched[id, title, score, userId, region]

out output
```

## Explanation

### Merge Operator (`+`)

When you merge `Candidates<T>` with a record, the record's fields are added to **each** item:

```
items: Candidates<{id: String, score: Float}>
context: {userId: Int}

result = items + context
# Result type: Candidates<{id: String, score: Float, userId: Int}>
```

### Projection Operator (`[fields]`)

When you project `Candidates<T>`, it selects fields from **each** item:

```
items: Candidates<{id: String, score: Float, extra: String}>

result = items[id, score]
# Result type: Candidates<{id: String, score: Float}>
```

## Running the Example

### Input
```json
{
  "items": [
    {"id": "p1", "title": "Widget A", "score": 0.95, "category": "tools"},
    {"id": "p2", "title": "Widget B", "score": 0.87, "category": "tools"},
    {"id": "p3", "title": "Gadget X", "score": 0.92, "category": "electronics"}
  ],
  "context": {
    "userId": 12345,
    "region": "us-west",
    "tier": "premium"
  }
}
```

### Expected Output
```json
{
  "output": [
    {"id": "p1", "title": "Widget A", "score": 0.95, "userId": 12345, "region": "us-west"},
    {"id": "p2", "title": "Widget B", "score": 0.87, "userId": 12345, "region": "us-west"},
    {"id": "p3", "title": "Gadget X", "score": 0.92, "userId": 12345, "region": "us-west"}
  ]
}
```

## Variations

### Multi-source Enrichment

Combine data from multiple context sources:

```
type Product = { id: String, price: Int }
type UserPrefs = { discountTier: String }
type SessionData = { sessionId: String, deviceType: String }

in products: Candidates<Product>
in userPrefs: UserPrefs
in session: SessionData

# Chain merges to add all context
step1 = products + userPrefs
enriched = step1 + session

out enriched
```

### Selective Enrichment

Add context then select specific combinations:

```
type SearchResult = {
  docId: String,
  title: String,
  snippet: String,
  relevanceScore: Float
}

type QueryContext = {
  query: String,
  timestamp: Int,
  userId: Int
}

in results: Candidates<SearchResult>
in queryCtx: QueryContext

# Enrich with query context
enriched = results + queryCtx

# Select fields for logging (subset)
loggingData = enriched[docId, relevanceScore, query, userId]

# Select fields for display (different subset)
displayData = enriched[title, snippet, relevanceScore]

out loggingData
out displayData
```

### Pipeline with Scoring

Enrich candidates then pass through scoring:

```
type Lead = {
  id: String,
  name: String,
  email: String,
  engagementScore: Int
}

type Campaign = {
  campaignId: String,
  priority: Int
}

in leads: Candidates<Lead>
in campaign: Campaign

# Add campaign context to each lead
enriched = leads + campaign

# Select fields needed for downstream scoring
forScoring = enriched[id, engagementScore, priority]

out forScoring
```

### Field Overwriting

When merging, right-side fields overwrite left-side on conflicts:

```
type Original = { id: String, value: Int, source: String }
type Override = { value: Int, timestamp: Int }

in items: Candidates<Original>
in override: Override

# 'value' from override replaces 'value' from items
merged = items + override
# Type: Candidates<{id: String, value: Int, source: String, timestamp: Int}>

out merged
```

## Type Algebra Deep Dive

### Merge Rules

```
{a: T1, b: T2} + {b: T3, c: T4} = {a: T1, b: T3, c: T4}
```

- All fields from left are included
- All fields from right are included
- Right overwrites left on conflicts

### Projection Rules

```
{a: T1, b: T2, c: T3}[a, c] = {a: T1, c: T3}
```

- Only selected fields are kept
- Order is preserved as declared

### Candidates Propagation

Operations on `Candidates<T>` apply element-wise:

```
Candidates<{a: Int}> + {b: Int} = Candidates<{a: Int, b: Int}>
Candidates<{a: Int, b: Int}>[a] = Candidates<{a: Int}>
```

## Real-World Applications

### Recommendation Systems
- Add user preferences to item candidates
- Include session context for personalization

### Search Ranking
- Enrich search results with query features
- Add user context for personalized ranking

### Ad Serving
- Add targeting context to ad candidates
- Include budget/bid information

### Content Delivery
- Add device context for format selection
- Include region data for localization

## Best Practices

1. **Project early**: Remove unused fields to reduce data size
2. **Document field conflicts**: Be explicit about which source "wins"
3. **Type defensively**: Verify merged types match expectations
4. **Name meaningful outputs**: Use clear variable names for intermediate steps

## Related Examples

- [Scoring Pipeline](scoring-pipeline.md) - Score enriched candidates
- [Content Analysis Pipeline](content-analysis.md) - Analyze enriched content
