---
title: "Text Analysis"
sidebar_position: 6
description: "Multi-step text analysis with multiple outputs"
---

# Text Analysis

A multi-step pipeline that cleans text and computes several metrics in parallel.

## Use Case

You receive a document and need to normalize it, count words, measure length, and split into lines — all from a single input.

## The Pipeline

```constellation
# text-analysis.cst
# Analyzes text input and produces multiple metrics

@example("The quick brown fox jumps over the lazy dog.\nThis is a sample document for analysis.")
in document: String

# Clean the input
cleaned = Trim(document)
normalized = Lowercase(cleaned)

# Analyze the text
words = WordCount(normalized)
chars = TextLength(normalized)

# Split into lines for further processing
lines = SplitLines(cleaned)

out cleaned
out normalized
out words
out chars
out lines
```

## Explanation

| Step | Module | Purpose |
|---|---|---|
| 1 | `Trim` | Remove leading/trailing whitespace |
| 2 | `Lowercase` | Normalize to lowercase |
| 3 | `WordCount` | Count words in the normalized text |
| 4 | `TextLength` | Count characters |
| 5 | `SplitLines` | Split into a list of lines |

Steps 3, 4, and 5 are independent of each other (they each depend only on `normalized` or `cleaned`). The runtime executes them in parallel automatically.

## Running the Example

### Input
```json
{
  "document": "The quick brown fox jumps over the lazy dog.\nThis is a sample document for analysis."
}
```

### Expected Output
```json
{
  "cleaned": "The quick brown fox jumps over the lazy dog.\nThis is a sample document for analysis.",
  "normalized": "the quick brown fox jumps over the lazy dog.\nthis is a sample document for analysis.",
  "words": 18,
  "chars": 83,
  "lines": ["The quick brown fox jumps over the lazy dog.", "This is a sample document for analysis."]
}
```

## Variations

### With uppercase comparison

```constellation
in document: String

cleaned = Trim(document)
lower = Lowercase(cleaned)
upper = Uppercase(cleaned)
length = TextLength(cleaned)

out lower
out upper
out length
```

## Best Practices

1. **Clean first, analyze second** — normalize input before computing metrics
2. **Fan out from a common base** — multiple analyses from one cleaned input run in parallel
3. **Output intermediate results** — expose `cleaned` alongside metrics for debugging

## Related Examples

- [Simple Transform](simple-transform.md) — single transformation
- [Hello World](hello-world.md) — basic string operations
- [Data Pipeline](data-pipeline.md) — numeric data analysis
