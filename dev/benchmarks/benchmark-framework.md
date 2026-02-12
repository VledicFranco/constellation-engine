# Benchmark Framework Guide

This document provides comprehensive documentation for the Constellation benchmark framework. All benchmark authors should read this guide before creating new benchmarks.

## Overview

The Constellation benchmark framework provides utilities for measuring and reporting performance of compiler and LSP operations. It includes:

- **BenchmarkHarness** - Core timing utilities with warmup and measurement
- **BenchmarkReporter** - Console and JSON reporting
- **TestFixtures** - Standardized test programs for consistent benchmarking

**Location:** `modules/lang-compiler/src/test/scala/io/constellation/lang/benchmark/`

---

## Core Components

### BenchmarkHarness

The primary interface for performance measurements. Handles JIT warmup and statistical analysis.

**Location:** `modules/lang-compiler/.../benchmark/BenchmarkHarness.scala`

#### Basic Measurement

```scala
import io.constellation.lang.benchmark._

// Measure without warmup (for one-off measurements)
val result = BenchmarkHarness.measure(
  name = "my_operation",
  iterations = 20,
  phase = "parse",
  inputSize = "small"
) {
  myOperation()
}

println(result.toConsoleString)
// Output: my_operation                             :    5.23ms (±0.45) [4.12 - 7.89] 191.2 ops/s
```

#### Measurement with Warmup (Recommended)

```scala
// Always use this for accurate JVM benchmarks
val result = BenchmarkHarness.measureWithWarmup(
  name = "parse_small",
  warmupIterations = 5,    // JIT warmup iterations (not measured)
  measureIterations = 20,  // Actual measurement iterations
  phase = "parse",
  inputSize = "small"
) {
  ConstellationParser.parse(source)
}

println(result.toConsoleString)
result.avgMs should be < 50.0  // Assertion for regression testing
```

#### Running a Suite of Phases

```scala
// Measure multiple phases in sequence
val results = BenchmarkHarness.runSuite(
  suiteName = "compilation",
  warmup = 5,
  iterations = 20,
  inputSize = "medium"
)(List(
  ("parse", () => parser.parse(source)),
  ("typecheck", () => TypeChecker.check(parsed, registry)),
  ("irgen", () => IRGenerator.generate(typed)),
  ("dagcompile", () => DagCompiler.compile(ir, "dag", Map.empty))
))

results.foreach(r => println(r.toConsoleString))
```

#### Standalone Warmup

```scala
// Warm up JIT before custom measurement logic
BenchmarkHarness.warmup(iterations = 10) {
  expensiveOperation()
}
```

---

### BenchmarkResult

The result type returned by all measurement methods.

**Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `name` | String | Benchmark identifier |
| `phase` | String | Category (parse, typecheck, irgen, etc.) |
| `inputSize` | String | Size category (small, medium, large, stress) |
| `avgMs` | Double | Average execution time in milliseconds |
| `minMs` | Double | Minimum execution time |
| `maxMs` | Double | Maximum execution time |
| `stdDevMs` | Double | Standard deviation |
| `throughputOpsPerSec` | Double | Operations per second (1000/avgMs) |
| `iterations` | Int | Number of measurement iterations |
| `rawTimingsMs` | List[Double] | All individual timings |

**Methods:**

```scala
// Formatted console output
result.toConsoleString
// "parse_small                              :    2.45ms (±0.12) [2.10 - 3.01] 408.2 ops/s"

// Convert to map for JSON serialization
result.toMap
// Map("name" -> "parse_small", "avgMs" -> 2.45, ...)
```

---

### BenchmarkReporter

Utilities for generating reports and comparing results.

**Location:** `modules/lang-compiler/.../benchmark/BenchmarkReporter.scala`

#### Console Summary

```scala
val results: List[BenchmarkResult] = /* ... */

// Print formatted summary grouped by input size
BenchmarkReporter.printSummary(results)

// Output:
// ================================================================================
// BENCHMARK RESULTS SUMMARY
// ================================================================================
//
// --- small programs ---
// parse_small                              :    2.45ms (±0.12) [2.10 - 3.01] 408.2 ops/s
// typecheck_small                          :    1.23ms (±0.08) [1.05 - 1.45] 813.0 ops/s
// ...
```

#### Quick Statistics

```scala
BenchmarkReporter.printQuickStats("Parse Phase", parseResults)

// Output:
// === Parse Phase ===
// Total pipeline time: 45.67 ms
// Slowest phase: parse_large (32.15 ms)
// Fastest phase: parse_small (2.45 ms)
```

#### Comparing Results

```scala
BenchmarkReporter.printComparison(
  "cache_miss", coldResults,
  "cache_hit", warmResults
)

// Output:
// === Comparison: cache_miss vs cache_hit ===
// compile_medium                           : cache_miss=45.23ms  cache_hit=3.21ms  speedup=14.1x
```

#### JSON Reports

```scala
// Write JSON report with environment metadata
BenchmarkReporter.writeJsonReport(results, "target/benchmark-results.json")

// Generate timestamped filename
val path = BenchmarkReporter.generateReportPath("target")
// Returns: "target/benchmark-results-20260124-143052.json"
```

**JSON Report Format:**
```json
{
  "metadata": {
    "timestamp": "2026-01-24T14:30:52.123Z",
    "javaVersion": "17.0.2",
    "jvmName": "OpenJDK 64-Bit Server VM",
    "scalaVersion": "3.3.4",
    "gitCommit": "a1b2c3d",
    "hostname": "dev-machine"
  },
  "results": [
    {
      "name": "parse_small",
      "phase": "parse",
      "inputSize": "small",
      "avgMs": 2.4532,
      "minMs": 2.1045,
      "maxMs": 3.0123,
      "stdDevMs": 0.1234,
      "throughputOpsPerSec": 407.6543,
      "iterations": 20
    }
  ]
}
```

---

## Test Fixtures

Standardized test programs for consistent benchmarking across all tests.

**Location:** `modules/lang-compiler/.../benchmark/TestFixtures.scala`

### Available Fixtures

| Fixture | Lines | Description | Expected Time |
|---------|-------|-------------|---------------|
| `smallProgram` | ~15 | Basic inputs, outputs, field access | <5ms parse |
| `mediumProgram` | ~80 | Multiple types, boolean ops, guards | <50ms parse |
| `largeProgram` | ~200 | Complex pipeline with many operations | <200ms parse |
| `stressProgram100` | ~110 | 100 chained boolean operations | Stress test |
| `stressProgram200` | ~210 | 200 chained boolean operations | Stress test |
| `stressProgram500` | ~510 | 500 chained boolean operations | Stress test |

### Usage

```scala
import io.constellation.lang.benchmark.TestFixtures

// Use predefined fixtures
val source = TestFixtures.smallProgram
val result = compiler.compile(source, "test-dag")

// Generate custom stress test
val customStress = TestFixtures.generateStressProgram(chainLength = 300)

// Iterate over standard fixtures
TestFixtures.standardFixtures.foreach { fixture =>
  println(s"Testing ${fixture.name} (${fixture.size})")
  val result = compiler.compile(fixture.source, "benchmark")
}

// Include stress tests
TestFixtures.allFixtures.foreach { fixture =>
  // Runs small, medium, large, stress_100, stress_200
}
```

### Fixture Content Overview

**Small Pipeline:** Basic variable assignment, conditionals, guards, coalesce
```
type Data = { value: Int, flag: Boolean }
in data: Data
result = if (flag) value else fallback
guarded = value when flag
out result
```

**Medium Pipeline:** Multiple record types, compound boolean logic, chains
```
type Config = { enabled: Boolean, active: Boolean, ... }
isFullyEnabled = enabled and active
effectiveScore = premiumScore ?? activeScore ?? defaultScore
out effectiveScore
```

**Large Pipeline:** Complex access control system with many derived values
```
# System, feature flags, user profile, access rules
canAccessExperimental = canAccessAdvanced and experimentalFeatures and canAccessBeta
effectiveLevel = adminLevel ?? premiumLevel ?? verifiedLevel ?? betaLevel ?? defaultLevel
```

---

## Best Practices

### 1. Always Warm Up JIT

JVM performance varies significantly before JIT compilation kicks in. Always use warmup:

```scala
warmupIterations = 5,   // Minimum 5 iterations
measureIterations = 20, // At least 20 for statistical significance
```

### 2. Categorize Results Consistently

Set `phase` and `inputSize` for proper grouping and comparison:

```scala
phase = "parse"      // or "typecheck", "irgen", "dagcompile", "optimize"
inputSize = "small"  // or "medium", "large", "stress_100", etc.
```

### 3. Set Performance Targets

Add assertions to catch regressions:

```scala
result.avgMs should be < 100.0  // Fail if avg exceeds 100ms
```

### 4. Generate Reports for CI

Write JSON for trend analysis:

```scala
val reportPath = BenchmarkReporter.generateReportPath("target")
BenchmarkReporter.writeJsonReport(allResults.toList, reportPath)
```

### 5. Isolate What You Measure

Don't include setup time in measurements:

```scala
// BAD - includes compiler creation time
val result = BenchmarkHarness.measureWithWarmup(...) {
  val compiler = LangCompiler.empty
  compiler.compile(source, "dag")
}

// GOOD - only measures compilation
val compiler = LangCompiler.empty
val result = BenchmarkHarness.measureWithWarmup(...) {
  compiler.compile(source, "dag")
}
```

### 6. Use Consistent Configuration

Define constants at class level for maintainability:

```scala
class MyBenchmark extends AnyFlatSpec with Matchers {
  val WarmupIterations  = 5
  val MeasureIterations = 20

  // All tests use these constants
}
```

---

## Running Benchmarks

### All Compiler Benchmarks
```bash
sbt "langCompiler/testOnly *Benchmark"
```

### Specific Benchmark
```bash
sbt "langCompiler/testOnly *CacheBenchmark"
sbt "langCompiler/testOnly *CompilerPipelineBenchmark"
sbt "langCompiler/testOnly *VisualizationBenchmark"
```

### With Verbose Output
```bash
sbt "langCompiler/testOnly *Benchmark -- -oDF"
```

### LSP Benchmarks
```bash
sbt "langLsp/testOnly *Benchmark"
sbt "langLsp/testOnly *LspOperationsBenchmark"
```

### Using Make Commands
```bash
make test           # All tests including benchmarks
make test-compiler  # Compiler module tests
make test-lsp       # LSP module tests
```

---

## Creating New Benchmarks

### Step-by-Step Guide

1. **Create test class** in appropriate benchmark directory:
   - Compiler: `modules/lang-compiler/src/test/scala/io/constellation/lang/benchmark/`
   - LSP: `modules/lang-lsp/src/test/scala/io/constellation/lsp/benchmark/`

2. **Use the framework** for all measurements

3. **Use TestFixtures** for consistent input

4. **Set appropriate targets** with assertions

5. **Generate reports** for tracking

6. **Document expected results** in comments

### Template

```scala
package io.constellation.lang.benchmark

import io.constellation.lang._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.mutable.ListBuffer

/**
 * Benchmarks for [component name]
 *
 * Run with: sbt "langCompiler/testOnly *MyNewBenchmark"
 */
class MyNewBenchmark extends AnyFlatSpec with Matchers {

  val WarmupIterations  = 5
  val MeasureIterations = 20

  // Collect results for final report
  private val allResults = ListBuffer[BenchmarkResult]()

  "MyOperation" should "complete in under 50ms for small input" in {
    val compiler = LangCompiler.empty

    val result = BenchmarkHarness.measureWithWarmup(
      name = "my_operation_small",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "my_phase",
      inputSize = "small"
    ) {
      compiler.compile(TestFixtures.smallProgram, "test-dag")
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 50.0
  }

  it should "complete in under 100ms for medium input" in {
    val compiler = LangCompiler.empty

    val result = BenchmarkHarness.measureWithWarmup(
      name = "my_operation_medium",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "my_phase",
      inputSize = "medium"
    ) {
      compiler.compile(TestFixtures.mediumProgram, "test-dag")
    }

    println(result.toConsoleString)
    allResults += result

    result.avgMs should be < 100.0
  }

  "Benchmark report" should "generate summary" in {
    BenchmarkReporter.printSummary(allResults.toList)

    val reportPath = BenchmarkReporter.generateReportPath()
    BenchmarkReporter.writeJsonReport(allResults.toList, reportPath)
    println(s"\nJSON report written to: $reportPath")

    allResults.size should be > 0
  }
}
```

---

## Performance Targets Reference

Standard targets for compiler operations (based on developer experience requirements):

| Operation | Small | Medium | Large | Notes |
|-----------|-------|--------|-------|-------|
| Parse | <5ms | <50ms | <200ms | Parser performance |
| TypeCheck | <5ms | <50ms | <200ms | Type inference |
| IR Generation | <5ms | <20ms | <50ms | Usually fast |
| Optimization | <5ms | <20ms | <50ms | Depends on passes |
| DAG Compile | <10ms | <50ms | <150ms | Graph construction |
| Full Pipeline | <50ms | <150ms | <400ms | End-to-end |
| Cache Hit | <5ms | <10ms | <20ms | Should be very fast |

---

## Troubleshooting

### High Variance in Results

If `stdDevMs` is high relative to `avgMs`:
- Increase `warmupIterations` (JIT not fully warmed)
- Close other applications (resource contention)
- Run benchmarks in isolation
- Consider GC pauses (use `-XX:+UseG1GC`)

### Inconsistent Results Between Runs

- Ensure machine is not throttling (check power settings)
- Run multiple times and compare
- Check for background processes

### OutOfMemoryError During Benchmarks

- Increase heap size in sbt: `sbt -J-Xmx4G`
- Check for memory leaks in benchmarked code
- Reduce `measureIterations` for stress tests

---

*Last updated: 2026-01-24*
