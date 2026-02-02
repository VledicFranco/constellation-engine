---
title: "Examples"
sidebar_position: 1
description: "Example pipelines demonstrating Constellation features"
---

# Pipeline Examples

This directory contains real-world examples demonstrating constellation-lang patterns and best practices.

## Examples by Category

### Text Processing
- [Text Cleaning Pipeline](text-cleaning.md) - Normalize and clean user input
- [Content Analysis Pipeline](content-analysis.md) - Analyze text for metrics and keywords

### Data Processing
- [Data Statistics Pipeline](data-statistics.md) - Calculate summary statistics on numeric data
- [List Processing Pipeline](list-processing.md) - Filter and transform lists with lambdas

### ML Pipelines
- [Batch Data Enrichment](batch-enrichment.md) - Enrich Candidates with context data
- [Scoring Pipeline](scoring-pipeline.md) - Multi-factor scoring with conditional logic

## Running Examples

### In VSCode
1. Copy the code block to a `.cst` file
2. Press `Ctrl+Shift+R` to run
3. Enter inputs when prompted

### Via HTTP API
```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "<paste source code here>",
    "inputs": { ... }
  }'
```

## Prerequisites

Make sure the server is running:
```bash
make server
```

## Example Complexity Guide

| Example | Difficulty | Concepts Covered |
|---------|------------|------------------|
| Text Cleaning | Beginner | Basic functions, chaining |
| Data Statistics | Beginner | List functions, multiple outputs |
| Content Analysis | Intermediate | Record types, field access |
| List Processing | Intermediate | Higher-order functions, lambdas |
| Batch Enrichment | Intermediate | Candidates, merge, projection |
| Scoring Pipeline | Advanced | Conditionals, type algebra, custom types |

## Next Steps

After working through these examples:
- Check the [Standard Library Reference](../../api-reference/stdlib.md) for all available functions
- Read the [constellation-lang Reference](../../language/index.md) for complete syntax
- Build your own custom modules following the [Getting Started Guide](../tutorial.md)
