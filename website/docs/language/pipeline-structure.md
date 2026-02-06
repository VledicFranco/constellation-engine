---
title: "Pipeline Structure"
sidebar_position: 2
description: "Understand pipeline anatomy: declarations, dependency analysis, DAG construction, and parallel execution patterns."
---

# Pipeline Structure

A constellation-lang pipeline is a declarative specification of a data transformation workflow. The compiler analyzes your pipeline and constructs a Directed Acyclic Graph (DAG) that enables optimal parallel execution while respecting data dependencies.

## Anatomy of a Pipeline File

A complete pipeline consists of five types of declarations, typically organized in this order:

```constellation
# 1. Use declarations (namespace imports) - optional
use stdlib.math
use stdlib.text as t

# 2. Type definitions - optional
type MyRecord = { field1: String, field2: Int }

# 3. Input declarations - at least one required for parameterized pipelines
in inputName: TypeExpression

# 4. Variable assignments - the transformation logic
intermediate = Transform(inputName)
result = Process(intermediate)

# 5. Output declarations - at least one required
out result
```

### Declaration Order

While declarations can technically appear in any order, the recommended organization improves readability:

1. **Use declarations** at the top establish namespace imports
2. **Type definitions** define reusable type structures
3. **Input declarations** specify the pipeline's API contract
4. **Variable assignments** contain the transformation logic
5. **Output declarations** define what the pipeline produces

:::tip
The compiler does not require a specific order, but consistent organization makes pipelines easier to maintain.
:::

## Use Declarations

Use declarations import namespaced modules for shorter function references:

```constellation
# Import a namespace
use stdlib.math

# Import with an alias
use stdlib.text as t

# Now you can use shorter references
result = math.add(x, y)
trimmed = t.trim(text)
```

Without use declarations, you must use fully qualified names:

```constellation
result = stdlib.math.add(x, y)
trimmed = stdlib.text.trim(text)
```

## Type Definitions

Type definitions create named types for reuse throughout the pipeline:

```constellation
# Simple record type
type User = {
  id: String,
  name: String,
  email: String
}

# Nested record type
type Order = {
  orderId: String,
  customer: User,
  items: List<{ sku: String, quantity: Int }>
}

# Type alias for collections
type UserList = List<User>

# Type merge (combining record types)
type AuditedUser = User + { createdAt: String, updatedAt: String }

# Union type (one of several types)
type Result = { value: Int } | { error: String }
```

## Input Declarations

Inputs define the external data required to execute the pipeline:

```constellation
# Basic inputs
in userId: String
in count: Int
in enabled: Boolean

# Record input
in user: { name: String, age: Int }

# Collection input
in items: List<{ id: String, score: Float }>

# Using a defined type
in customer: User

# With example annotation (for tooling)
@example("user-12345")
in userId: String
```

## Variable Assignments

Assignments bind expression results to variable names:

```constellation
# Module calls
cleaned = Trim(rawText)
wordCount = WordCount(cleaned)

# Chained transformations
step1 = FirstModule(input)
step2 = SecondModule(step1)
step3 = ThirdModule(step2)

# Arithmetic
total = price * quantity
discounted = total - discount

# Type algebra (merge)
enriched = baseData + additionalFields

# Field projection
selected = record[field1, field2]

# Conditionals
status = if (score > 0.5) "pass" else "fail"

# Module options
cached = SlowOperation(data) with cache: 5min, retry: 3
```

## Output Declarations

Outputs specify the pipeline's results. Every pipeline must have at least one output:

```constellation
# Single output
out result

# Multiple outputs
out processedData
out metadata
out statistics
```

---

## Execution Order and Dependencies

The compiler analyzes your pipeline and automatically determines the execution order based on data dependencies. You don't need to worry about sequencing - just declare what you want to compute.

### Dependency Analysis

The compiler builds a dependency graph by tracing which variables each expression depends on:

```constellation
in x: Int
in y: Int

# 'a' depends on 'x' only
a = Transform(x)

# 'b' depends on 'y' only
b = Transform(y)

# 'c' depends on both 'a' and 'b'
c = Combine(a, b)

out c
```

This creates the dependency graph:

```
x ──► Transform ──► a ──┐
                        ├──► Combine ──► c
y ──► Transform ──► b ──┘
```

### Parallel Execution

Independent computations execute in parallel automatically:

```constellation
in userId: String
in productId: String

# These have no shared dependencies - execute in parallel
userProfile = FetchUser(userId)
productData = FetchProduct(productId)
inventory = CheckInventory(productId)

# This waits for all three to complete
combined = userProfile + productData + inventory

out combined
```

Execution timeline:

```
Time ──────────────────────────────────────────►

FetchUser(userId)      ████████████░░░░░░░░░░░░░
FetchProduct(productId) ███████░░░░░░░░░░░░░░░░░░
CheckInventory(productId) █████████████░░░░░░░░░░

                                     ▼ all complete
combined = ...                       █████
```

### Sequential Dependencies

When one computation depends on another's result, they execute sequentially:

```constellation
in text: String

# These must execute in order
cleaned = Trim(text)           # Step 1
normalized = Lowercase(cleaned) # Step 2 - depends on step 1
tokenized = Tokenize(normalized) # Step 3 - depends on step 2

out tokenized
```

---

## How the DAG is Constructed

The compiler performs several passes to build the execution DAG:

### 1. Parse Phase

The parser reads your `.cst` file and produces an Abstract Syntax Tree (AST) representing the pipeline structure.

### 2. Type Checking Phase

The type checker validates that all expressions are well-typed:
- Input types are verified
- Module calls match registered signatures
- Operators are applied to compatible types
- Output expressions are valid

### 3. Dependency Resolution Phase

The compiler traces dependencies for each variable:

```constellation
in a: Int
in b: Int
c = Add(a, b)      # depends on: {a, b}
d = Multiply(c, 2)  # depends on: {c} → transitively {a, b}
e = Square(b)       # depends on: {b}
f = Combine(d, e)   # depends on: {d, e} → transitively {a, b}
out f
```

### 4. DAG Construction Phase

The compiler builds nodes for each computation and edges for dependencies:

```
     a ─────┐
             ├──► Add ──► c ──► Multiply ──► d ──┐
     b ──┬──┘                                     ├──► Combine ──► f
         │                                        │
         └───────────► Square ──────────► e ──────┘
```

### 5. Optimization Phase

The DAG is optimized for execution:
- **Constant folding**: Compile-time evaluation of constant expressions
- **Dead code elimination**: Removing unused computations
- **Common subexpression elimination**: Reusing identical computations
- **Execution planning**: Scheduling for optimal parallelism

### 6. Execution Phase

The runtime executes the DAG:
1. Start with nodes that have no dependencies (inputs)
2. As each node completes, check if any dependent nodes are now ready
3. Execute ready nodes in parallel (up to configured concurrency limits)
4. Continue until all output nodes complete

---

## Multiple Outputs Pattern

Pipelines can produce multiple outputs. Each output represents a separate result that consumers can access:

```constellation
in document: String

# Process the document
cleaned = Trim(document)
normalized = Lowercase(cleaned)

# Generate multiple analysis results
wordCount = WordCount(normalized)
charCount = TextLength(normalized)
lineCount = CountLines(cleaned)
sentiment = AnalyzeSentiment(normalized)

# Export all results
out wordCount
out charCount
out lineCount
out sentiment
```

### Selective Output Consumption

When executing a pipeline, consumers can request specific outputs:

```scala
// Request only specific outputs
val result = pipeline.execute(
  inputs = Map("document" -> "Hello World"),
  outputs = Set("wordCount", "sentiment")
)
// Only wordCount and sentiment are computed; lineCount is skipped
```

### Output Grouping with Records

For related outputs, consider grouping them into a record:

```constellation
in document: String

# Individual computations
words = WordCount(document)
chars = TextLength(document)
lines = CountLines(document)

# Group into a single output record
type Metrics = { words: Int, chars: Int, lines: Int }
metrics = { words: words, chars: chars, lines: lines }

out metrics
```

---

## Modular Pipeline Organization

As pipelines grow, organization becomes important. Here are patterns for maintainable pipelines.

### Logical Sections with Comments

Use comment headers to delineate sections:

```constellation
# =============================================================================
# CONFIGURATION & TYPES
# =============================================================================

type InputRecord = { id: String, value: Float }
type OutputRecord = { id: String, score: Float, category: String }

# =============================================================================
# INPUTS
# =============================================================================

@example([])
in records: List<InputRecord>

@example(0.5)
in threshold: Float

# =============================================================================
# PREPROCESSING
# =============================================================================

cleaned = CleanRecords(records)
normalized = NormalizeValues(cleaned)

# =============================================================================
# SCORING
# =============================================================================

scores = ComputeScores(normalized)
categories = Categorize(scores, threshold)

# =============================================================================
# OUTPUT ASSEMBLY
# =============================================================================

result = normalized[id] + scores + categories

out result
```

### Feature Extraction Pattern

Separate feature computation from model inference:

```constellation
# =============================================================================
# FEATURE EXTRACTION
# =============================================================================

# Text features
textLength = TextLength(document)
wordCount = WordCount(document)
avgWordLength = textLength / wordCount

# Numerical features
normalizedValue = value / maxValue
scaledScore = score * multiplier

# Combine features into feature vector
features = {
  textLength: textLength,
  wordCount: wordCount,
  avgWordLength: avgWordLength,
  normalizedValue: normalizedValue,
  scaledScore: scaledScore
}

# =============================================================================
# MODEL INFERENCE
# =============================================================================

prediction = MLModel(features) with cache: 10min, timeout: 5s

out prediction
```

### Fan-Out / Fan-In Pattern

Process data through multiple independent paths, then combine:

```constellation
in userId: String
in productId: String

# =============================================================================
# FAN-OUT: Independent parallel fetches
# =============================================================================

userProfile = FetchUserProfile(userId) with timeout: 3s
userHistory = FetchPurchaseHistory(userId) with timeout: 5s
productDetails = FetchProduct(productId) with timeout: 2s
inventory = CheckInventory(productId) with timeout: 1s
reviews = FetchReviews(productId) with timeout: 4s

# =============================================================================
# FAN-IN: Combine results
# =============================================================================

# Merge all data into comprehensive view
fullContext = userProfile + userHistory + productDetails + inventory + reviews

# Generate recommendation based on combined data
recommendation = RecommendationModel(fullContext)

out recommendation
```

### Conditional Processing Pattern

Route data through different paths based on conditions:

```constellation
in request: { type: String, payload: String }

# =============================================================================
# ROUTING LOGIC
# =============================================================================

# Determine processing path based on request type
processedResult = branch {
  request.type == "urgent" -> FastProcessor(request.payload) with priority: high,
  request.type == "batch" -> BatchProcessor(request.payload) with priority: low,
  request.type == "standard" -> StandardProcessor(request.payload),
  otherwise -> DefaultProcessor(request.payload)
}

out processedResult
```

### Error Handling Pattern

Build resilient pipelines with fallbacks:

```constellation
in query: String

# =============================================================================
# PRIMARY PATH (with resilience)
# =============================================================================

# Try primary service with retries
primaryResult = PrimaryService(query) with
    retry: 3,
    timeout: 5s,
    backoff: exponential

# =============================================================================
# FALLBACK PATH
# =============================================================================

# Fallback to secondary service if primary fails
@example("default-response")
in defaultResponse: String

# Use guard + coalesce for graceful degradation
safeResult = primaryResult ?? SecondaryService(query) ?? defaultResponse

out safeResult
```

---

## Pipeline File Conventions

### File Extension

Use `.cst` (constellation) for pipeline files:
- `text-analysis.cst`
- `customer-scoring.cst`
- `data-enrichment.cst`

### Naming Conventions

- **File names**: lowercase with hyphens (`lead-scoring-pipeline.cst`)
- **Type names**: PascalCase (`UserProfile`, `OrderItem`)
- **Variable names**: camelCase (`userProfile`, `orderTotal`)
- **Module names**: PascalCase (`FetchUser`, `ComputeScore`)

### File Header

Start each file with a descriptive header:

```constellation
# =============================================================================
# Pipeline: Customer Segmentation
# Description: Analyzes customer data for marketing campaign targeting
# Author: Data Team
# Version: 2.1.0
# Last Updated: 2024-01-15
# =============================================================================
```

---

## See Also

- [Declarations](./declarations.md) - Detailed declaration syntax
- [Expressions](./expressions.md) - Expression types and operators
- [Module Options](./module-options.md) - Execution options (`with` clause)
- [Examples](./examples.md) - Complete pipeline examples
