---
title: "Tutorial"
sidebar_position: 2
description: "Step-by-step guide to building your first Constellation pipeline"
---

# Getting Started with Constellation Engine

This tutorial will guide you through building your first ML pipeline with Constellation Engine. By the end, you'll understand the core concepts and be able to create your own type-safe pipelines.

**Estimated time:** 2 hours

## Table of Contents

1. [Introduction](#1-introduction)
2. [Installation](#2-installation)
3. [Hello World](#3-hello-world)
4. [Building a Simple Pipeline](#4-building-a-simple-pipeline)
5. [Creating Custom Modules](#5-creating-custom-modules)
6. [Real-World Example](#6-real-world-example)
7. [Next Steps](#7-next-steps)

---

## 1. Introduction

### What is Constellation Engine?

Constellation Engine is a Scala 3 framework for building type-safe ML pipeline DAGs (Directed Acyclic Graphs). It lets you:

- **Define pipelines declaratively** using constellation-lang, a domain-specific language
- **Compose modules** into complex workflows with automatic dependency resolution
- **Ensure type safety** at compile time, catching errors before runtime
- **Visualize execution** with the VSCode extension's DAG viewer

### Why DAG-based ML Pipelines?

Traditional ML code often becomes tangled spaghetti:

```python
# Hard to test, maintain, and parallelize
def process(data):
    cleaned = clean(data)
    features = extract_features(cleaned)
    embeddings = model.embed(features)
    scores = ranker.score(embeddings, context)
    return filter_results(scores)
```

With Constellation, the same pipeline becomes:

```
in data: Candidates<RawData>
in context: Context

cleaned = Clean(data)
features = ExtractFeatures(cleaned)
embeddings = Embed(features)
scores = Rank(embeddings, context)
result = Filter(scores)

out result
```

**Benefits:**
- Each step is independently testable
- Dependencies are explicit and visible
- Parallel execution is automatic
- Type mismatches are caught at compile time

:::tip Testing Individual Modules
Each module can be unit tested in isolation with standard Scala testing frameworks. The pipeline only tests the composition of modules.
:::

### Key Concepts

| Concept | Description |
|---------|-------------|
| **Module** | A reusable processing unit with typed inputs and outputs |
| **DAG** | A directed acyclic graph of modules and data flow |
| **constellation-lang** | The DSL for declaring pipelines |
| **Candidates<T>** | A batch type for ML operations on multiple items |

---

## 2. Installation

### Prerequisites

- **JDK 17+** (we recommend [Temurin](https://adoptium.net/))
- **SBT 1.10+** ([download](https://www.scala-sbt.org/download.html))
- **VSCode** with the Constellation extension (optional but recommended)

### Add Dependencies

Add Constellation Engine to your `build.sbt`:

```scala
val constellationVersion = "0.3.1"

libraryDependencies ++= Seq(
  "io.github.vledicfranco" %% "constellation-core"          % constellationVersion,
  "io.github.vledicfranco" %% "constellation-runtime"       % constellationVersion,
  "io.github.vledicfranco" %% "constellation-lang-compiler" % constellationVersion,
  "io.github.vledicfranco" %% "constellation-lang-stdlib"   % constellationVersion
)
```

Add the HTTP server module if you want the REST API:

```scala
libraryDependencies += "io.github.vledicfranco" %% "constellation-http-api" % constellationVersion
```

### Alternative: Clone and Run the Example App

If you'd like to explore the example application and included pipelines:

```bash
git clone https://github.com/VledicFranco/constellation-engine.git
cd constellation-engine
make compile
make test
```

If `make` is not available (Windows without WSL):

```powershell
sbt compile
sbt test
```

### Alternative: Run with Docker

If you have Docker installed, you can skip the JDK/SBT prerequisites and run the server directly:

```bash
# Build and start the server
make docker-build
make docker-run

# Or using docker compose
docker compose up
```

The server will be available at `http://localhost:8080`. All configuration is via environment variables (see `docker-compose.yml` for defaults).

### Install VSCode Extension

1. Open VSCode
2. Go to Extensions (Ctrl+Shift+X)
3. Search for "Constellation Engine"
4. Click Install

Or install from the command line:

```bash
cd vscode-extension
npm install
npm run compile
```

Then press F5 in VSCode to launch a development instance with the extension loaded.

### Start the Development Server

The server provides the HTTP API and LSP (Language Server Protocol) support:

```bash
make server
```

You should see:

```
Starting Constellation server on http://localhost:8080...
LSP WebSocket: ws://localhost:8080/lsp
```

### Verify Installation

:::tip Quick Health Check
Always verify the server is running before trying to execute pipelines.
:::

Test the server is running:

```bash
curl http://localhost:8080/health
```

Expected response:

```json
{"status":"healthy","modulesLoaded":true,"dagsLoaded":true}
```

**Troubleshooting:**

| Issue | Solution |
|-------|----------|
| `sbt: command not found` | Install SBT from https://www.scala-sbt.org/download.html |
| `java: command not found` | Install JDK 17+ from https://adoptium.net/ |
| Port 8080 in use | Set `CONSTELLATION_PORT=8081` before running |
| Tests fail | Run `make clean` then `make compile` |

---

## 3. Hello World

Let's create your first Constellation pipeline.

### Create a Script File

Create a file named `hello.cst` in the `modules/example-app/examples/` directory:

```
# hello.cst - My first Constellation pipeline

in name: String

greeting = concat("Hello, ", name)
trimmed_greeting = trim(greeting)

out trimmed_greeting
```

### Understanding the Code

| Line | Explanation |
|------|-------------|
| `# hello.cst...` | Comment (starts with `#`) |
| `in name: String` | Declare an input named `name` of type `String` |
| `greeting = concat(...)` | Call the `concat` function, assign result to `greeting` |
| `trimmed_greeting = trim(...)` | Call `trim` function on the greeting |
| `out trimmed_greeting` | Declare the output of the pipeline |

**Tip:** You can add example values to inputs using the `@example` annotation:

```
@example("World")
in name: String
```

The example value pre-populates the run widget in VSCode. See [Input Annotations](../language/declarations.md#input-annotations) for details.

### Run in VSCode

1. Open `hello.cst` in VSCode (with the Constellation extension installed)
2. Press `Ctrl+Shift+R` to run the script
3. Enter `"World"` when prompted for the `name` input
4. See the result: `"Hello, World"`

### View the DAG

Press `Ctrl+Shift+D` to visualize the pipeline:

```
    [name]
       |
       v
   [concat] <-- "Hello, "
       |
       v
    [trim]
       |
       v
[trimmed_greeting]
```

### Run via HTTP API

You can also execute pipelines via the REST API:

```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "in name: String\ngreeting = concat(\"Hello, \", name)\nresult = trim(greeting)\nout result",
    "inputs": {"name": "World"}
  }'
```

Response:

```json
{
  "success": true,
  "outputs": {
    "result": "Hello, World"
  },
  "executionTimeMs": 12
}
```

### Try It Yourself

Modify `hello.cst` to:

1. Add a second input `title: String`
2. Concatenate the title before the name
3. Output both the greeting and its length

<details>
<summary>Solution</summary>

```
in name: String
in title: String

full_name = concat(title, name)
greeting = concat("Hello, ", full_name)
trimmed_greeting = trim(greeting)
length = string-length(trimmed_greeting)

out trimmed_greeting
out length
```

</details>

---

## 4. Building a Simple Pipeline

Now let's build a more realistic pipeline using record types and type algebra.

### Define Custom Types

Create `simple-pipeline.cst`:

```
# Define a record type for user data
type User = {
  id: Int,
  name: String,
  email: String,
  score: Int
}

# Input: a single user record
in user: User

# Extract and process fields
user_name = trim(user.name)
is_high_score = gt(user.score, 100)

# Create output with selected fields plus computed values
out user_name
out is_high_score
```

### Record Types

Records are structured data with named fields:

```
type Person = {
  name: String,
  age: Int,
  active: Boolean
}
```

Access fields with dot notation: `person.name`, `person.age`

### Type Algebra: Merging Records

Use the `+` operator to merge records (right side wins on conflicts):

```
type Base = { id: Int, name: String }
type Extra = { name: String, score: Float }

in base: Base
in extra: Extra

# merged has type { id: Int, name: String, score: Float }
# The 'name' from extra overwrites the one from base
merged = base + extra

out merged
```

### Projections: Selecting Fields

Use `[field1, field2]` to select specific fields:

```
type User = { id: Int, name: String, email: String, score: Int }

in user: User

# Select only id and name
summary = user[id, name]  # Type: { id: Int, name: String }

out summary
```

### Working with Candidates

`Candidates<T>` represents a batch of items - essential for ML operations:

```
type Item = { id: String, features: List<Float> }
type Context = { userId: Int }

in items: Candidates<Item>
in context: Context

# Merge adds context to EACH item in the batch
enriched = items + context
# Type: Candidates<{ id: String, features: List<Float>, userId: Int }>

# Project selects fields from EACH item
ids_only = items[id]
# Type: Candidates<{ id: String }>

out enriched
```

### Conditional Expressions

Use `if/else` for conditional logic:

```
in score: Int
in threshold: Int

is_above = gt(score, threshold)
result = if (is_above) score else threshold

out result
```

### Complete Example

Create `data-pipeline.cst`:

```
# A pipeline that processes user data

type UserInput = {
  id: Int,
  name: String,
  email: String,
  score: Int
}

type Settings = {
  threshold: Int,
  prefix: String
}

in user: UserInput
in settings: Settings

# Check if user meets threshold
qualifies = gte(user.score, settings.threshold)

# Create display name with prefix
prefixed_name = concat(settings.prefix, user.name)
display_name = trim(prefixed_name)

# Select output fields
user_summary = user[id, name, score]

out display_name
out qualifies
out user_summary
```

### Try It Yourself

Create a pipeline that:

1. Takes a `Candidates<Product>` where Product has `{id: String, price: Int, category: String}`
2. Takes a `discount: Int` input
3. Merges the discount into each product
4. Projects only `id` and `price` fields
5. Outputs the result

<details>
<summary>Solution</summary>

```
type Product = {
  id: String,
  price: Int,
  category: String
}

type Discount = {
  discount: Int
}

in products: Candidates<Product>
in discount_info: Discount

# Add discount to each product
with_discount = products + discount_info

# Select only id and price (discount is also included from merge)
result = with_discount[id, price, discount]

out result
```

</details>

---

## 5. Creating Custom Modules

The standard library covers basic operations, but real ML pipelines need custom modules.

### Module Basics

A module is defined in Scala using `ModuleBuilder`:

```scala
import io.constellation.ModuleBuilder

// Define input/output types as case classes
case class TextInput(text: String)
case class TextOutput(result: String, wordCount: Int)

// Build the module
val textProcessor = ModuleBuilder
  .metadata("TextProcessor", "Processes text and counts words", 1, 0)
  .tags("text", "nlp")
  .implementationPure[TextInput, TextOutput] { input =>
    val words = input.text.split("\\s+").filter(_.nonEmpty)
    TextOutput(
      result = words.map(_.capitalize).mkString(" "),
      wordCount = words.length
    )
  }
  .build
```

### Key Points

1. **Case classes for I/O**: Field names must match what constellation-lang expects
2. **Metadata**: Name, description, version (major, minor)
3. **Tags**: For categorization and discovery
4. **Implementation**: Pure (no side effects) or IO-based

:::warning Field Name Matching
The field names in your case classes must exactly match the parameter names used in constellation-lang. A mismatch like `userName` vs `username` will cause a runtime error.
:::

### Create Your First Module

Create `modules/example-app/src/main/scala/io/constellation/examples/app/modules/TutorialModules.scala`:

```scala
package io.constellation.examples.app.modules

import io.constellation.ModuleBuilder
import io.constellation.lang.semantic.{FunctionSignature, SemanticType}

object TutorialModules {

  // --- Sentiment Analyzer ---

  case class SentimentInput(text: String)
  case class SentimentOutput(sentiment: String, confidence: Float)

  val sentimentAnalyzer = ModuleBuilder
    .metadata("SentimentAnalyzer", "Analyzes text sentiment", 1, 0)
    .tags("nlp", "sentiment")
    .implementationPure[SentimentInput, SentimentOutput] { input =>
      // Simple mock implementation
      val text = input.text.toLowerCase
      val (sentiment, confidence) =
        if (text.contains("great") || text.contains("love"))
          ("positive", 0.85f)
        else if (text.contains("bad") || text.contains("hate"))
          ("negative", 0.80f)
        else
          ("neutral", 0.60f)
      SentimentOutput(sentiment, confidence)
    }
    .build

  val sentimentSignature = FunctionSignature(
    name = "AnalyzeSentiment",
    params = List("text" -> SemanticType.SString),
    returns = SemanticType.SRecord(Map(
      "sentiment" -> SemanticType.SString,
      "confidence" -> SemanticType.SFloat
    )),
    moduleName = "SentimentAnalyzer",
    description = Some("Analyzes text sentiment")
  )

  // --- Score Calculator ---

  case class ScoreInput(value: Int, multiplier: Int)
  case class ScoreOutput(score: Int, isHigh: Boolean)

  val scoreCalculator = ModuleBuilder
    .metadata("ScoreCalculator", "Calculates and evaluates scores", 1, 0)
    .tags("math", "scoring")
    .implementationPure[ScoreInput, ScoreOutput] { input =>
      val score = input.value * input.multiplier
      ScoreOutput(score, score > 100)
    }
    .build

  val scoreSignature = FunctionSignature(
    name = "CalculateScore",
    params = List(
      "value" -> SemanticType.SInt,
      "multiplier" -> SemanticType.SInt
    ),
    returns = SemanticType.SRecord(Map(
      "score" -> SemanticType.SInt,
      "isHigh" -> SemanticType.SBoolean
    )),
    moduleName = "ScoreCalculator",
    description = Some("Calculates score and checks if high")
  )

  // --- All modules and signatures ---

  val allModules = List(sentimentAnalyzer, scoreCalculator)
  val allSignatures = List(sentimentSignature, scoreSignature)
}
```

### Register the Module

:::note Server Restart Required
After registering a new module, you must restart the server (`make server`) for it to be available in constellation-lang scripts.
:::

Add to `modules/example-app/src/main/scala/io/constellation/examples/app/ExampleLib.scala`:

```scala
// In the imports section
import io.constellation.examples.app.modules.TutorialModules

// In allSignatures
val allSignatures: List[FunctionSignature] =
  TextModules.allSignatures ++
  DataModules.allSignatures ++
  TutorialModules.allSignatures  // Add this line

// In allModules
val allModules: List[Module.Uninitialized] =
  TextModules.allModules ++
  DataModules.allModules ++
  TutorialModules.allModules  // Add this line
```

### Use in constellation-lang

Now restart the server (`make server`) and create `sentiment-demo.cst`:

```
in review: String
in rating: Int
in multiplier: Int

# Use our custom modules
sentiment = AnalyzeSentiment(review)
score_result = CalculateScore(rating, multiplier)

# Access result fields
final_sentiment = sentiment.sentiment
final_score = score_result.score

out final_sentiment
out final_score
```

### IO-Based Modules

For modules that need side effects (HTTP calls, database access):

```scala
import cats.effect.IO

case class ApiInput(query: String)
case class ApiOutput(result: String)

val apiModule = ModuleBuilder
  .metadata("ApiCall", "Calls external API", 1, 0)
  .implementation[ApiInput, ApiOutput] { input =>
    IO {
      // Simulated API call
      ApiOutput(s"Response for: ${input.query}")
    }
  }
  .build
```

### Try It Yourself

Create a module called `TextStats` that:

1. Takes a `text: String` input
2. Returns `{ charCount: Int, wordCount: Int, avgWordLength: Float }`
3. Register it and use it in a pipeline

<details>
<summary>Solution</summary>

```scala
case class TextStatsInput(text: String)
case class TextStatsOutput(charCount: Int, wordCount: Int, avgWordLength: Float)

val textStats = ModuleBuilder
  .metadata("TextStats", "Computes text statistics", 1, 0)
  .tags("text", "stats")
  .implementationPure[TextStatsInput, TextStatsOutput] { input =>
    val words = input.text.split("\\s+").filter(_.nonEmpty)
    val wordCount = words.length
    val charCount = input.text.length
    val avgWordLength = if (wordCount > 0)
      words.map(_.length).sum.toFloat / wordCount
    else 0f
    TextStatsOutput(charCount, wordCount, avgWordLength)
  }
  .build

val textStatsSignature = FunctionSignature(
  name = "TextStats",
  params = List("text" -> SemanticType.SString),
  returns = SemanticType.SRecord(Map(
    "charCount" -> SemanticType.SInt,
    "wordCount" -> SemanticType.SInt,
    "avgWordLength" -> SemanticType.SFloat
  )),
  moduleName = "TextStats"
)
```

Pipeline:

```
in text: String

stats = TextStats(text)
out stats
```

</details>

---

## 6. Real-World Example

Let's build a complete lead scoring pipeline that demonstrates all concepts together.

### The Scenario

You're building a lead scoring system that:

1. Takes candidate leads with their activity data
2. Enriches them with company context
3. Scores them based on multiple factors
4. Filters to high-quality leads

### Define Types

Create `lead-scoring-pipeline.cst`:

```
# Lead Scoring Pipeline
# Processes candidate leads and scores them for sales prioritization

# --- Type Definitions ---

type Lead = {
  id: String,
  name: String,
  email: String,
  company: String,
  pageViews: Int,
  emailOpens: Int,
  daysActive: Int
}

type CompanyContext = {
  industry: String,
  companySize: Int,
  targetScore: Int
}

type ScoringWeights = {
  pageViewWeight: Int,
  emailWeight: Int,
  activityWeight: Int
}

# --- Inputs ---

in leads: Candidates<Lead>
in company: CompanyContext
in weights: ScoringWeights

# --- Processing Pipeline ---

# Step 1: Enrich leads with company context
enriched = leads + company

# Step 2: Calculate engagement score for each lead
# (In a real system, this would be a custom scoring module)
# For now, we'll project the data we need for scoring

# Step 3: Select fields for output
scored_leads = enriched[id, name, email, industry, pageViews, emailOpens]

# --- Output ---

out scored_leads
```

### Add Custom Scoring Module

For a more complete example, add a scoring module:

```scala
// In TutorialModules.scala

case class LeadScoreInput(
  pageViews: Int,
  emailOpens: Int,
  daysActive: Int,
  pageViewWeight: Int,
  emailWeight: Int,
  activityWeight: Int
)

case class LeadScoreOutput(
  engagementScore: Int,
  activityScore: Int,
  totalScore: Int,
  tier: String
)

val leadScorer = ModuleBuilder
  .metadata("LeadScorer", "Calculates lead scores", 1, 0)
  .tags("scoring", "leads", "ml")
  .implementationPure[LeadScoreInput, LeadScoreOutput] { input =>
    val engagementScore =
      input.pageViews * input.pageViewWeight +
      input.emailOpens * input.emailWeight
    val activityScore = input.daysActive * input.activityWeight
    val totalScore = engagementScore + activityScore

    val tier = totalScore match {
      case s if s >= 100 => "hot"
      case s if s >= 50 => "warm"
      case _ => "cold"
    }

    LeadScoreOutput(engagementScore, activityScore, totalScore, tier)
  }
  .build
```

### Run the Pipeline

With the server running:

```bash
curl -X POST http://localhost:8080/run \
  -H "Content-Type: application/json" \
  -d '{
    "source": "type Lead = { id: String, name: String, pageViews: Int }\ntype Context = { source: String }\nin leads: Candidates<Lead>\nin ctx: Context\nenriched = leads + ctx\nout enriched",
    "inputs": {
      "leads": [
        {"id": "1", "name": "Alice", "pageViews": 50},
        {"id": "2", "name": "Bob", "pageViews": 25}
      ],
      "ctx": {"source": "marketing"}
    }
  }'
```

### Step-Through Execution

The VSCode extension supports step-through debugging:

1. Open your `.cst` file
2. Click "Step" instead of "Run"
3. Watch the DAG visualizer highlight each batch as it executes
4. Inspect intermediate values at each step
5. Click "Continue" to finish or "Stop" to abort

This is invaluable for debugging complex pipelines.

---

## 7. Next Steps

Congratulations! You've learned the fundamentals of Constellation Engine.

### What You've Learned

- Installing and setting up Constellation Engine
- Writing constellation-lang pipelines
- Using record types, projections, and merges
- Working with `Candidates<T>` for batch operations
- Creating custom modules in Scala
- Building real-world ML pipelines

### Continue Learning

| Resource | Description |
|----------|-------------|
| [Pipeline Examples](./examples/index.md) | Real-world pipeline examples with explanations |
| [constellation-lang Reference](../language/index.md) | Complete language syntax and semantics |
| [Standard Library](../api-reference/stdlib.md) | All built-in functions |
| [Architecture Guide](../architecture/technical-architecture.md) | Deep dive into internals |
| [API Guide](../api-reference/programmatic-api.md) | Programmatic usage and advanced patterns |
| [LSP Integration](../tooling/lsp-integration.md) | IDE features and configuration |

### Example Projects

Explore the example code in:

- `docs/examples/` - Real-world pipeline examples with detailed explanations
- `modules/example-app/examples/` - Example pipeline files
- `modules/example-app/src/` - Example module implementations
- `modules/lang-stdlib/` - Standard library implementation

### Get Help

- **Issues:** [GitHub Issues](https://github.com/VledicFranco/constellation-engine/issues)
- **Discussions:** Open a discussion for questions

### What's Next for You?

1. **Build a real pipeline** - Take a workflow from your domain and model it
2. **Create custom modules** - Wrap your ML models as Constellation modules
3. **Integrate with your stack** - Use the HTTP API in your services
4. **Contribute** - Found a bug? Have an idea? PRs welcome!

---

*Happy pipeline building!*
