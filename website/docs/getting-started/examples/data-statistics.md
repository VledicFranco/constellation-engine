---
title: "Data Statistics"
sidebar_position: 4
---

# Data Statistics Pipeline

Calculate summary statistics on numeric data - a fundamental building block for data analysis and ML feature engineering.

## Use Case

You have a list of numbers and need to compute:
- Total sum
- Average value
- Maximum and minimum values

This is useful for feature normalization, outlier detection, or reporting.

## The Pipeline

```
# data-statistics.cst
# Calculate summary statistics on a list of numbers

in numbers: List<Int>

# Calculate statistics
total = SumList(numbers)
average = Average(numbers)
maximum = Max(numbers)
minimum = Min(numbers)

# Output all statistics
out total
out average
out maximum
out minimum
```

## Explanation

| Function | Description | Return Type |
|----------|-------------|-------------|
| `SumList` | Adds all numbers in the list | `Int` |
| `Average` | Computes arithmetic mean | `Float` |
| `Max` | Finds the largest value | `Int` |
| `Min` | Finds the smallest value | `Int` |

## Running the Example

### Input
```json
{
  "numbers": [10, 25, 15, 30, 20]
}
```

### Expected Output
```json
{
  "total": 100,
  "average": 20.0,
  "maximum": 30,
  "minimum": 10
}
```

## Variations

### With Range Calculation

Add the range (max - min) using stdlib:

```
use stdlib.math

in numbers: List<Int>

maximum = Max(numbers)
minimum = Min(numbers)
range = subtract(maximum, minimum)

out maximum
out minimum
out range
```

### Formatted Output

Format numbers for display:

```
in numbers: List<Int>

total = SumList(numbers)
average = Average(numbers)

# Format with 2 decimal places
formattedAvg = FormatNumber(average, 2)

out total
out formattedAvg
```

### With Filtering

Calculate statistics on filtered subsets:

```
in numbers: List<Int>
in threshold: Int

# Filter to numbers above threshold
filtered = FilterGreaterThan(numbers, threshold)

# Calculate stats on filtered list
filteredTotal = SumList(filtered)
filteredAvg = Average(filtered)

out filteredTotal
out filteredAvg
```

### Comparison Statistics

Compare two datasets:

```
in dataset1: List<Int>
in dataset2: List<Int>

avg1 = Average(dataset1)
avg2 = Average(dataset2)

max1 = Max(dataset1)
max2 = Max(dataset2)

out avg1
out avg2
out max1
out max2
```

## Real-World Applications

### Feature Engineering
Calculate statistics as features for ML models:
- Use min/max for normalization bounds
- Use average as a baseline comparison
- Use sum for aggregation features

### Data Validation
Check data quality:
- Compare max/min to expected ranges
- Verify averages are within expected bounds

### Reporting
Generate summary reports:
- Total values for financial reports
- Averages for performance metrics

## Best Practices

1. **Handle empty lists**: Some functions may behave unexpectedly with empty lists
2. **Consider data types**: `Average` returns `Float` even for `Int` lists
3. **Chain efficiently**: Compute expensive operations once and reuse results

## Related Examples

- [List Processing Pipeline](list-processing.md) - Filter before computing stats
- [Scoring Pipeline](scoring-pipeline.md) - Use statistics in scoring logic
