---
title: "Candidates Batch"
sidebar_position: 4
description: "Batch processing with Candidates<T> type"
---

# Candidates Batch

Process batches of records using `Candidates<T>` — Constellation's batch type that applies operations to each item automatically.

## Use Case

You have a batch of items and need to enrich each with context data, then select specific fields.

## The Pipeline

```constellation
# candidates-demo.cst - Working with batched data

# Define types
type Item = {
  id: String,
  value: Int
}

type Context = {
  userId: Int,
  source: String
}

in items: Candidates<Item>
in context: Context

# Merge adds context to EACH item in the batch
# Result: Candidates<{ id: String, value: Int, userId: Int, source: String }>
enriched = items + context

# Project selects fields from EACH item
# Result: Candidates<{ id: String, userId: Int }>
ids_with_user = enriched[id, userId]

out enriched
out ids_with_user
```

## Explanation

| Step | Expression | Result Type | Purpose |
|---|---|---|---|
| 1 | `items + context` | `Candidates<{ id, value, userId, source }>` | Merge context into **each** item in the batch |
| 2 | `enriched[id, userId]` | `Candidates<{ id, userId }>` | Project fields from **each** item |

When you merge a `Candidates<T>` with a record, the record is merged into every item in the batch. Similarly, projection applies to every item. This eliminates the need for manual `.traverse` or `.map` calls.

:::tip
`Candidates<T>` automatically broadcasts single records to each item in the batch. No explicit loops required.
:::

## Running the Example

### Input
```json
{
  "items": [
    { "id": "item-1", "value": 100 },
    { "id": "item-2", "value": 200 },
    { "id": "item-3", "value": 300 }
  ],
  "context": {
    "userId": 42,
    "source": "api"
  }
}
```

### Expected Output
```json
{
  "enriched": [
    { "id": "item-1", "value": 100, "userId": 42, "source": "api" },
    { "id": "item-2", "value": 200, "userId": 42, "source": "api" },
    { "id": "item-3", "value": 300, "userId": 42, "source": "api" }
  ],
  "ids_with_user": [
    { "id": "item-1", "userId": 42 },
    { "id": "item-2", "userId": 42 },
    { "id": "item-3", "userId": 42 }
  ]
}
```

## Variations

### Multi-source enrichment

```constellation
type Item = { id: String }
type UserCtx = { userId: Int }
type SessionCtx = { sessionId: String }

in items: Candidates<Item>
in user: UserCtx
in session: SessionCtx

enriched = items + user + session

out enriched
```

### Enrich then project

```constellation
type Product = { sku: String, price: Int }
type Discount = { discount: Float, campaign: String }

in products: Candidates<Product>
in discount: Discount

with_discount = products + discount
pricing = with_discount[sku, price, discount]

out pricing
```

:::note
Projection on a batch (`items[field1, field2]`) creates a new batch with only the specified fields in each item, reducing memory and serialization overhead.
:::

## Best Practices

1. **Use `Candidates<T>` for batch inputs** — the type system tracks the shape of each item through all operations
2. **Merge context once** — `items + context` is more concise than enriching each item individually
3. **Project early** — narrow the batch to only the fields downstream steps need

## Related Examples

- [Type Algebra](type-algebra.md) — merge and projection on single records
- [Lead Scoring](lead-scoring.md) — complex data processing
- [Data Pipeline](data-pipeline.md) — multi-step transformations
