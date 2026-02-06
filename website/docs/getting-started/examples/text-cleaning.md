---
title: "Text Cleaning"
sidebar_position: 2
description: "Normalize and clean text input by trimming whitespace, adjusting case, and replacing unwanted patterns."
---

# Text Cleaning Pipeline

A common requirement in ML pipelines is to normalize and clean text input before processing.

## Use Case

:::note When to Use
This pattern is essential as a preprocessing step before any text analysis, search indexing, or ML feature extraction.
:::

You receive user-generated text that may have:
- Leading/trailing whitespace
- Inconsistent casing
- Unwanted characters or patterns

This pipeline standardizes text into a consistent format.

## The Pipeline

```
# text-cleaning.cst
# Normalize and clean user input text

in rawText: String

# Step 1: Remove leading/trailing whitespace
trimmed = Trim(rawText)

# Step 2: Convert to lowercase for consistency
normalized = Lowercase(trimmed)

# Step 3: Replace common unwanted patterns
# (e.g., replace multiple spaces with single space)
cleaned = Replace(normalized, "  ", " ")

# Output the cleaned text
out cleaned
```

## Explanation

| Step | Function | Purpose |
|------|----------|---------|
| 1 | `Trim` | Removes leading and trailing whitespace |
| 2 | `Lowercase` | Converts to lowercase for consistent matching |
| 3 | `Replace` | Replaces double spaces with single space |

## Running the Example

### Input
```json
{
  "rawText": "  Hello   WORLD  "
}
```

### Expected Output
```json
{
  "cleaned": "hello world"
}
```

## Variations

### Uppercase Normalization

For systems that require uppercase:

```
in rawText: String

trimmed = Trim(rawText)
normalized = Uppercase(trimmed)

out normalized
```

### Multi-step Replacement

Chain multiple replacements for complex cleaning:

```
in rawText: String

step1 = Trim(rawText)
step2 = Lowercase(step1)
step3 = Replace(step2, "\t", " ")      # Replace tabs
step4 = Replace(step3, "  ", " ")       # Replace double spaces
step5 = Replace(step4, ".", "")         # Remove periods

out step5
```

### With Metrics

:::tip Debugging Tip
Output both original and cleaned metrics to verify your cleaning steps are working as expected.
:::

Output both cleaned text and original metrics:

```
in rawText: String

# Clean the text
trimmed = Trim(rawText)
cleaned = Lowercase(trimmed)

# Compute metrics on original
originalLength = TextLength(rawText)
cleanedLength = TextLength(cleaned)

out cleaned
out originalLength
out cleanedLength
```

## Best Practices

:::warning Order Matters
Always `Trim` before case conversion. Trimming after case conversion may behave unexpectedly with certain Unicode whitespace characters.
:::

1. **Order matters**: Trim before case conversion to avoid trimming issues
2. **Chain carefully**: Each step should have a single responsibility
3. **Preserve originals**: Keep the original text available if you need comparisons

## Related Examples

- [Content Analysis Pipeline](content-analysis.md) - Analyze cleaned text
- [Batch Enrichment](batch-enrichment.md) - Apply cleaning to batches
