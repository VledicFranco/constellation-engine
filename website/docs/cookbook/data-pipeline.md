---
title: "Data Pipeline"
sidebar_position: 14
description: "Multi-step data processing with filtering, transformation, and aggregation"
---

# Data Pipeline

A multi-step pipeline that filters, transforms, and aggregates numeric data.

## Use Case

You have a list of numbers and need to filter by a threshold, scale the results, and compute statistics.

## The Pipeline

```constellation
# data-pipeline.cst

@example([2, 4, 6])
in numbers: List<Int>

@example(5)
in threshold: Int

@example(2)
in multiplier: Int

# Step 1: Filter - keep only numbers above threshold
filtered = FilterGreaterThan(numbers, threshold)

# Step 2: Transform - multiply each filtered number
scaled = MultiplyEach(filtered, multiplier)

# Step 3: Aggregate - calculate statistics on the scaled data
total = SumList(scaled)
avg = Average(scaled)
highest = Max(scaled)
lowest = Min(scaled)

# Step 4: Format - make the total human-readable
formattedTotal = FormatNumber(total)

# Outputs - multiple results from the pipeline
out filtered
out scaled
out total
out avg
out highest
out lowest
out formattedTotal
```

## Explanation

| Step | Module | Purpose |
|---|---|---|
| 1 | `FilterGreaterThan` | Keep numbers above the threshold |
| 2 | `MultiplyEach` | Scale each remaining number |
| 3 | `SumList`, `Average`, `Max`, `Min` | Compute aggregate statistics (run in parallel) |
| 4 | `FormatNumber` | Format the total for display |

Steps 3's four aggregation calls are independent — they all depend on `scaled` but not on each other — so they run in parallel.

:::tip Automatic Parallelization
The runtime detects that `SumList`, `Average`, `Max`, and `Min` are independent and executes them concurrently. No explicit parallel constructs needed.
:::

## Running the Example

### Input
```json
{
  "numbers": [2, 4, 6],
  "threshold": 5,
  "multiplier": 2
}
```

### Expected Output
```json
{
  "filtered": [6],
  "scaled": [12],
  "total": 12,
  "avg": 12.0,
  "highest": 12,
  "lowest": 12,
  "formattedTotal": "12"
}
```

## Variations

### With larger dataset

```constellation
in values: List<Int>
in cutoff: Int

above = FilterGreaterThan(values, cutoff)
count = ListLength(above)
total = SumList(above)

out count
out total
```

:::note
Filtering early (`FilterGreaterThan` before `MultiplyEach`) reduces the number of items processed by downstream steps, improving performance.
:::

## Best Practices

1. **Filter early** — reduce the dataset before expensive operations
2. **Fan out aggregations** — independent statistics compute in parallel
3. **Parameterize thresholds** — use inputs instead of hardcoded values for reusability

## Related Examples

- [Lead Scoring](lead-scoring.md) — complex data processing with records
- [Lambdas and HOF](lambdas-and-hof.md) — filter/map with inline predicates
- [Candidates Batch](candidates-batch.md) — batch processing with type algebra
