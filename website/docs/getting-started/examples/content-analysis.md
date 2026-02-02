---
title: "Content Analysis"
sidebar_position: 3
---

# Content Analysis Pipeline

Analyze text content to extract metrics and detect patterns - essential for content moderation, SEO analysis, or feature extraction.

## Use Case

Given a piece of text content, you want to:
- Count words and characters
- Check for specific keywords
- Split into processable lines

## The Pipeline

```
# content-analysis.cst
# Analyze text content for metrics and keywords

in content: String
in keyword: String

# Basic metrics
wordCount = WordCount(content)
charCount = TextLength(content)

# Keyword detection
containsKeyword = Contains(content, keyword)

# Normalize for analysis
normalized = Lowercase(content)

# Output analysis results
out wordCount
out charCount
out containsKeyword
out normalized
```

## Explanation

| Function | Description | Return Type |
|----------|-------------|-------------|
| `WordCount` | Counts words (space-separated tokens) | `Int` |
| `TextLength` | Counts characters in string | `Int` |
| `Contains` | Checks if substring exists | `Boolean` |
| `Lowercase` | Converts to lowercase | `String` |

## Running the Example

### Input
```json
{
  "content": "Hello World! This is a Test Document.",
  "keyword": "Test"
}
```

### Expected Output
```json
{
  "wordCount": 7,
  "charCount": 38,
  "containsKeyword": true,
  "normalized": "hello world! this is a test document."
}
```

## Variations

### Multi-line Content Analysis

Process content that spans multiple lines:

```
in content: String

# Split into lines
lines = SplitLines(content)

# Get line count
lineCount = list-length(lines)

# Get first line for preview
firstLine = list-first(lines)

out lineCount
out firstLine
```

### CSV-like Data Parsing

Parse delimited data:

```
in row: String
in delimiter: String

# Split by delimiter
fields = Split(row, delimiter)

# Get field count
fieldCount = list-length(fields)

out fields
out fieldCount
```

Input:
```json
{
  "row": "John,Doe,john@example.com,42",
  "delimiter": ","
}
```

### Content Quality Score

Combine metrics into a quality assessment:

```
use stdlib.math
use stdlib.comparison

in content: String

wordCount = WordCount(content)
charCount = TextLength(content)

# Check minimum thresholds
hasMinWords = gte(wordCount, 10)
hasMinChars = gte(charCount, 50)

out wordCount
out charCount
out hasMinWords
out hasMinChars
```

### Keyword Density Analysis

Analyze keyword presence in content:

```
in content: String
in keyword: String

# Get content metrics
contentWords = WordCount(content)
contentLength = TextLength(content)

# Check keyword presence
hasKeyword = Contains(content, keyword)

# Normalize for case-insensitive check
normalizedContent = Lowercase(content)
normalizedKeyword = Lowercase(keyword)
hasKeywordNormalized = Contains(normalizedContent, normalizedKeyword)

out hasKeyword
out hasKeywordNormalized
out contentWords
```

## Real-World Applications

### Content Moderation
- Check for banned keywords
- Validate minimum content length
- Extract content for review

### SEO Analysis
- Count keyword occurrences
- Measure content length
- Analyze content structure

### Data Extraction
- Parse structured text (CSV, logs)
- Split multi-line content
- Extract specific fields

### Feature Engineering
- Generate text features for ML models
- Normalize text for comparison
- Create content fingerprints

## Best Practices

1. **Normalize before comparing**: Use `Lowercase` for case-insensitive matching
2. **Trim inputs**: Clean whitespace before analysis with `Trim`
3. **Handle edge cases**: Empty strings, single words, etc.

## Related Examples

- [Text Cleaning Pipeline](text-cleaning.md) - Clean content before analysis
- [Batch Enrichment](batch-enrichment.md) - Analyze content in batches
