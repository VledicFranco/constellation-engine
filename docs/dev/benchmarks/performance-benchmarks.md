# Performance Benchmarks & Monitoring Tools

This document is the definitive guide to all performance tooling in Constellation Engine. It covers benchmarks, monitoring endpoints, client-side tracking, and troubleshooting.

## Quick Stats

- **15 Scala benchmark/test suites** - Compiler, cache, visualization, memory, incremental, LSP, concurrency, execution, semantic tokens, regression, large DAG stress, sustained load, type system properties, compilation properties, adversarial fuzzing
- **1 TypeScript performance tracker** - Client-side latency measurement
- **2 performance endpoints** - HTTP /metrics, LSP getCacheStats
- **Automated regression testing** - Baseline enforcement in CI
- **Orchestration overhead** - ~0.15 ms/node at scale (pure engine cost, no module implementation time)

---

## Quick Start

### Run All Benchmarks
```bash
# Compiler benchmarks
sbt "langCompiler/testOnly *Benchmark"

# LSP benchmarks
sbt "langLsp/testOnly *Benchmark"

# Runtime benchmarks
sbt "runtime/testOnly *Benchmark"

# Regression tests (fail on performance degradation)
sbt "langCompiler/testOnly *RegressionTests"
```

### Check Cache Effectiveness
```bash
curl http://localhost:8080/metrics | jq .cache
```

### Monitor Extension Performance
Check VS Code Output panel for `[PERF]` entries when operations exceed 500ms.

---

## Benchmark Suites

### 1. CompilerPipelineBenchmark

**Location:** `modules/lang-compiler/.../benchmark/CompilerPipelineBenchmark.scala`

**Purpose:** Measure each compilation phase (parse, typecheck, IR gen, optimize, DAG compile).

**Command:**
```bash
sbt "langCompiler/testOnly *CompilerPipelineBenchmark"
```

**Expected Results:**

| Phase | Small | Medium | Large | Target |
|-------|-------|--------|-------|--------|
| Parse | <5ms | <50ms | <200ms | Pass |
| TypeCheck | <5ms | <50ms | <200ms | Pass |
| IR Gen | <5ms | <20ms | <50ms | Pass |
| Optimize | <5ms | <20ms | <50ms | Pass |
| DAG Compile | <10ms | <50ms | <150ms | Pass |
| Full Pipeline | <30ms | <100ms | <300ms | Pass |

---

### 2. CacheBenchmark

**Location:** `modules/lang-compiler/.../benchmark/CacheBenchmark.scala`

**Purpose:** Validate caching effectiveness (cold vs warm compilation).

**Command:**
```bash
sbt "langCompiler/testOnly *CacheBenchmark"
```

**Expected Results:**
- Cold compile (no cache): Varies by size
- Warm cache hit: <5ms for all sizes
- Speedup factor: 10-90x typical, minimum 5x required
- Cache hit rate: ~100% for unchanged sources

**Scenarios Tested:**
- Cold cache compilation
- Warm cache hits
- IR cache performance
- Cache invalidation
- Source change detection

---

### 3. VisualizationBenchmark

**Location:** `modules/lang-compiler/.../benchmark/VisualizationBenchmark.scala`

**Purpose:** Measure DAG visualization and layout algorithm performance at scale.

**Command:**
```bash
sbt "langCompiler/testOnly *VisualizationBenchmark"
```

**Expected Results:**

| Size | VizCompile | Layout | Full Viz |
|------|------------|--------|----------|
| Small (10 nodes) | <5ms | <10ms | <20ms |
| Medium (50 nodes) | <20ms | <30ms | <50ms |
| Large (100 nodes) | <50ms | <100ms | <150ms |
| Stress (500 nodes) | <100ms | <2000ms | <2100ms |

**Note:** Layout uses O(n) Sugiyama algorithm. 500+ node graphs may be slow; consider warning users.

---

### 4. IncrementalCompileBenchmark

**Location:** `modules/lang-compiler/.../benchmark/IncrementalCompileBenchmark.scala`

**Purpose:** Measure recompilation speed for small edits (editor responsiveness).

**Command:**
```bash
sbt "langCompiler/testOnly *IncrementalCompileBenchmark"
```

**Expected Results:**

| Change Type | Target | Notes |
|-------------|--------|-------|
| Comment addition | <50ms | Whitespace-only change |
| Variable rename | <100ms | Identifier change |
| New output | <100ms | Semantic change |
| Type modification | <200ms | Structural change |

---

### 5. MemoryBenchmark

**Location:** `modules/lang-compiler/.../benchmark/MemoryBenchmark.scala`

**Purpose:** Track heap usage during compilation phases.

**Command:**
```bash
sbt "langCompiler/testOnly *MemoryBenchmark"
```

**Expected Results:**

| Size | Memory Target | Notes |
|------|---------------|-------|
| Small | <5MB | Single small program |
| Medium | <20MB | Typical development file |
| Large | <50MB | Complex pipeline |
| Stress 500 | <200MB | Stress test limit |

**Note:** JVM memory measurement is noisy. Results show averages with min/max ranges.

---

### 6. LspOperationsBenchmark

**Location:** `modules/lang-lsp/.../benchmark/LspOperationsBenchmark.scala`

**Purpose:** Measure LSP request latency for editor operations.

**Command:**
```bash
sbt "langLsp/testOnly *LspOperationsBenchmark"
```

**Expected Results:**

| Operation | Target | Priority |
|-----------|--------|----------|
| textDocument/didOpen | <100ms | High |
| textDocument/didChange | <50ms | CRITICAL |
| textDocument/completion | <50ms | CRITICAL |
| textDocument/hover | <30ms | Medium |
| constellation/getDAG | <200ms | Medium |

---

### 7. ConcurrencyBenchmark

**Location:** `modules/lang-lsp/.../benchmark/ConcurrencyBenchmark.scala`

**Purpose:** Test LSP server under concurrent request load.

**Command:**
```bash
sbt "langLsp/testOnly *ConcurrencyBenchmark"
```

**Expected Results:**

| Scenario | Target |
|----------|--------|
| 10 parallel completions | <500ms total |
| Mixed requests | <200ms |
| 100 rapid requests (stress) | No deadlock, <30s |

**Verifies:**
- No deadlocks under parallel requests
- Reasonable throughput with concurrent load
- Thread safety of shared state

---

### 8. SemanticTokenBenchmark

**Location:** `modules/lang-lsp/.../benchmark/SemanticTokenBenchmark.scala`

**Purpose:** Measure semantic token generation for syntax highlighting.

**Command:**
```bash
sbt "langLsp/testOnly *SemanticTokenBenchmark"
```

**Expected Results:**
- Small files: <20ms
- Medium files: <50ms
- Large files: <100ms

---

### 9. ExecutionBenchmark

**Location:** `modules/runtime/.../benchmark/ExecutionBenchmark.scala`

**Purpose:** Measure DAG execution performance and pure orchestration overhead.

**Command:**
```bash
sbt "runtime/testOnly *ExecutionBenchmark"
```

**Methodology:** All test modules use trivial implementations (`toUpperCase` / `toLowerCase`) that complete in nanoseconds. The measured latency is therefore pure orchestration cost: fiber scheduling, data flow, module dispatch, and result collection.

**Expected Results (warmed, 20 iterations):**

| Test | Modules | Avg Latency | Per-Node | Target |
|------|---------|-------------|----------|--------|
| execute_simple | 1 | ~1 ms | 1.06 ms | <50 ms |
| execute_medium | 3 | ~1.7 ms | 0.56 ms | <50 ms |
| execute_large | 10 | ~3 ms | 0.30 ms | <100 ms |
| execute_stress50 | 50 | ~7.4 ms | 0.15 ms | <500 ms |
| execute_stress100 | 100 | ~14.7 ms | 0.15 ms | <1000 ms |

**Key Insight:** Per-node overhead converges to ~0.15 ms at scale, demonstrating that the engine's scheduling cost is amortized across larger pipelines.

---

### 10. LargeDagStressTest (RFC-013 Phase 5.1)

**Location:** `modules/runtime/.../benchmark/LargeDagStressTest.scala`

**Purpose:** Validate engine correctness and performance with large DAG topologies (100–1000 nodes), memory bounds, determinism, and high-concurrency scheduling.

**Command:**
```bash
sbt "runtime/testOnly *LargeDagStressTest"
```

**Tests:**

| Test | Description | Target |
|------|-------------|--------|
| 100-node chain | Sequential chain of 100 modules | < 5 s |
| 500-node chain | Sequential chain of 500 modules | < 30 s |
| Wide DAG 50x10 | 50 parallel branches, 10 modules deep (500 total) | < 30 s |
| Memory bounded | 10 runs of 200-node DAGs, heap growth < 200 MB | Pass |
| Deterministic results | 5 identical runs produce identical output | Pass |
| 1000 concurrent submissions | Scheduler handles 1000 parallel `IO` tasks | Pass |
| 1000-node construction | Build a 1000-node DagSpec in < 1 s | Pass |

---

### 11. SustainedLoadTest (RFC-013 Phase 5.3)

**Location:** `modules/runtime/.../benchmark/SustainedLoadTest.scala`

**Purpose:** Run 10,000+ executions and verify no OOM, bounded heap growth, stable p99 latency, and correct concurrent execution.

**Command:**
```bash
sbt "runtime/testOnly *SustainedLoadTest"
```

**Expected Results:**

| Test | Metric | Target |
|------|--------|--------|
| 10K executions | All complete without OOM | Pass |
| Heap growth | Max – first sample < 200 MB across 5 batches | Pass |
| p99 stability | Last-batch p99 / first-batch p99 < 3x | Pass |
| Concurrent load | 1000 parallel executions complete | Pass |

**Observed Results (single-module DAG, warmed):**

| Metric | Value |
|--------|-------|
| p50 latency | 0.06 ms |
| p99 latency | 0.49 ms |
| Heap after 10K runs | Stable (~95 MB, no monotonic growth) |
| p99 ratio (last/first) | 0.94 (no degradation) |

---

### 12. TypeSystemPropertyTest (RFC-013 Phase 5.2)

**Location:** `modules/core/.../property/TypeSystemPropertyTest.scala`

**Purpose:** Property-based tests verifying structural invariants of CType and CValue using ScalaCheck generators.

**Command:**
```bash
sbt "core/testOnly *TypeSystemPropertyTest"
```

**Properties Verified:**
- CValue.ctype always matches construction type
- Generator produces all primitive and composite types
- List elements match declared subtype
- Product fields match declared structure
- Map keys/values match declared types
- CType equality is reflexive and deterministic

---

### 13. CompilationPropertyTest (RFC-013 Phase 5.2)

**Location:** `modules/lang-compiler/.../property/CompilationPropertyTest.scala`

**Purpose:** Verify parsing and compilation determinism — same source always produces the same AST and DagSpec.

**Command:**
```bash
sbt "langCompiler/testOnly *CompilationPropertyTest"
```

**Properties Verified:**
- Parser determinism (identical ASTs across 10 runs)
- Compilation determinism (identical DagSpec structure across 5 runs)
- Parse error consistency (same error message for same invalid input)
- Compilation error consistency (same error count for same invalid source)
- Output binding consistency

---

### 14. AdversarialFuzzingTest (RFC-013 Phase 5.4)

**Location:** `modules/lang-parser/.../parser/AdversarialFuzzingTest.scala`

**Purpose:** Feed adversarial inputs to the parser and verify all failures produce structured errors with no unhandled exceptions or stack overflows.

**Command:**
```bash
sbt "langParser/testOnly *AdversarialFuzzingTest"
```

**Tests:**

| Test | Description | Target |
|------|-------------|--------|
| 10K random inputs | Deterministic seed, mixed strategies | < 100 unexpected exceptions |
| ScalaCheck arbitrary strings | Property-based random strings | No exceptions |
| 100-level nested booleans | `(((flag and flag) and flag)...)` | No crash |
| 100-level nested if-else | `if (flag) if (flag) ... else y` | No crash |
| 200-level coalesce chains | `v0 ?? v1 ?? ... ?? fallback` | No crash |
| 500-level nested booleans | Stack overflow guard | No StackOverflowError |
| 1000 variable assignments | Linear program size | Parses successfully |
| 500 output declarations | Large output list | Parses successfully |
| Malformed types | `in x: {`, `in x: List<>` | Structured ParseError |
| Token-level adversarial | `=====`, `(((((`, control chars | No crash |
| Unicode input | BOM, emoji, CJK, zero-width space | No crash |

---

### 10. RegressionTests

**Location:** `modules/lang-compiler/.../benchmark/RegressionTests.scala`

**Purpose:** Automated baseline enforcement that fails CI on performance degradation.

**Command:**
```bash
sbt "langCompiler/testOnly *RegressionTests"
```

**Baselines Enforced:**

| Operation | Baseline | Tolerance |
|-----------|----------|-----------|
| parse_small | 50ms | Built-in |
| parse_medium | 100ms | Built-in |
| parse_large | 300ms | Built-in |
| pipeline_small | 100ms | Built-in |
| pipeline_medium | 200ms | Built-in |
| pipeline_large | 500ms | Built-in |
| cache_warm_* | 10-15ms | Built-in |
| Cache speedup | 5x minimum | Required |

**Updating Baselines:**
When performance improves, update values in `RegressionTests.scala` to lock in gains.

---

## Performance Endpoints

### HTTP GET /metrics

**Purpose:** Returns server-side performance statistics.

**Request:**
```bash
curl http://localhost:8080/metrics
```

**Response:**
```json
{
  "timestamp": "2026-01-24T12:00:00Z",
  "cache": {
    "hits": 150,
    "misses": 23,
    "hitRate": 0.867,
    "evictions": 5,
    "entries": 12
  },
  "server": {
    "uptime_seconds": 3600,
    "requests_total": 500
  }
}
```

### LSP constellation/getCacheStats

**Purpose:** Query cache statistics via LSP protocol.

**Request (JSON-RPC):**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "constellation/getCacheStats"
}
```

**Response:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "success": true,
    "cachingEnabled": true,
    "stats": {
      "hits": 150,
      "misses": 23,
      "hitRate": 0.867
    }
  }
}
```

---

## Client-Side Performance Tracking

### PerformanceTracker (VS Code Extension)

**Location:** `vscode-extension/src/utils/performanceTracker.ts`

**Purpose:** Measures client-side latency for LSP operations, panel rendering, and user interactions.

**Usage:**
```typescript
import { PerformanceTracker, Operations } from './utils/performanceTracker';

const tracker = PerformanceTracker.getInstance();

// Start timing
const timer = tracker.startOperation(Operations.DAG_REFRESH);

// ... perform operation ...

// End timing (logs if >500ms)
const elapsed = timer.end();

// Get all statistics
const stats = tracker.getAllStats();
```

**Console Output (for slow operations):**
```
[PERF] Slow operation: dag.refresh took 723.4ms
```

**Available Operations:**
- `LSP_COMPLETION` - Autocomplete requests
- `LSP_HOVER` - Hover information
- `DAG_REFRESH` - DAG visualization update
- `PANEL_RENDER` - Panel rendering
- `FILE_OPEN` - File open handling

---

## Orchestration Overhead

The engine's orchestration overhead is the latency added by DAG scheduling, fiber management, and data flow — independent of module implementation time.

All measurements use trivial modules (`toUpperCase` / `toLowerCase`) that complete in nanoseconds. The full measured latency is therefore pure engine cost.

### Per-Node Cost

| Pipeline Size | Avg Latency | Per-Node Overhead |
|---------------|-------------|-------------------|
| 1 module | 1.06 ms | 1.06 ms |
| 3 modules | 1.67 ms | 0.56 ms |
| 10 modules | 3.01 ms | 0.30 ms |
| 50 modules | 7.40 ms | 0.15 ms |
| 100 modules | 14.67 ms | 0.15 ms |

Per-node cost converges to **~0.15 ms** as pipeline size grows. The fixed overhead (~1 ms) covers fiber pool initialization, DAG topology resolution, and result collection.

### Sustained Load Profile

Over 10,000 consecutive single-module executions (post-warmup):

| Metric | Value |
|--------|-------|
| p50 latency | 0.06 ms |
| p99 latency | 0.49 ms |
| Heap after 10K runs | Stable (~95 MB, no monotonic growth) |
| p99 ratio (last/first batch) | 0.94 (no degradation) |

### Large DAG Topologies

| Topology | Modules | Target |
|----------|---------|--------|
| 100-node sequential chain | 100 | < 5 s |
| 500-node sequential chain | 500 | < 30 s |
| 50 branches x 10 depth (parallel) | 500 | < 30 s |
| 1000-node DAG construction | 1000 | < 1 s |
| 1000 concurrent scheduler submissions | — | Bounded |

### Interpretation

For real-world pipelines where each module makes an HTTP call (~50–200 ms), the engine overhead is negligible:

| Scenario | Module Time | Engine Overhead | Total | Overhead % |
|----------|-------------|-----------------|-------|------------|
| 10-module pipeline, 50 ms/module | 500 ms | 3 ms | 503 ms | 0.6% |
| 10-module pipeline, 200 ms/module | 2000 ms | 3 ms | 2003 ms | 0.15% |
| 100-module pipeline, 50 ms/module | 5000 ms | 15 ms | 5015 ms | 0.3% |

With automatic parallelization of independent branches, actual wall-clock time is often much lower than the sequential sum.

---

## Performance Targets Summary

| Category | Operation | Good | Acceptable | Poor |
|----------|-----------|------|------------|------|
| **Latency** | File Open (small) | <30ms | <100ms | >200ms |
| | File Open (large) | <100ms | <300ms | >500ms |
| | Autocomplete | <30ms | <50ms | >100ms |
| | DAG Visualization | <100ms | <200ms | >500ms |
| | Cache Hit | <5ms | <10ms | >20ms |
| **Orchestration** | Per-node overhead | <0.2ms | <0.5ms | >1ms |
| | Sustained p99 | <1ms | <5ms | >10ms |
| | 10K runs heap growth | <50MB | <200MB | >500MB |
| **Memory** | Extension (idle) | <50MB | <100MB | >200MB |
| | Extension (active) | <100MB | <200MB | >500MB |
| **Throughput** | Cache Speedup | >10x | >5x | <2x |
| **Concurrency** | Parallel requests | No issues | Minor delays | Deadlocks |

---

## E2E Test Fixtures

**Location:** `vscode-extension/src/test/fixtures/`

Standardized `.cst` files for consistent performance testing:

| File | Lines | Purpose | Target |
|------|-------|---------|--------|
| `perf-small.cst` | ~15 | Quick sanity checks | <50ms |
| `perf-medium.cst` | ~80 | Realistic small file | <150ms |
| `perf-large.cst` | ~200 | Complex pipeline | <400ms |
| `perf-stress-100.cst` | ~110 | Scalability test | Stress |

**Usage in TypeScript:**
```typescript
const fixturesPath = path.join(__dirname, '../../fixtures');
const testFile = vscode.Uri.file(path.join(fixturesPath, 'perf-small.cst'));
```

---

## CI Integration

**Workflow:** `.github/workflows/benchmark.yml`

Benchmarks run automatically on:
- Every push to master
- Every pull request

**Features:**
- Runs all compiler and LSP benchmarks
- Archives results as GitHub artifacts (30-day retention)
- Fails on benchmark test failures (regression detection)
- Generates job summary with performance targets

---

## Troubleshooting

### Slow Compilation

1. Check cache hit rate:
   ```bash
   curl localhost:8080/metrics | jq .cache.hitRate
   ```
2. If low (<80%), source may be changing unexpectedly
3. Run `CacheBenchmark` to verify caching works
4. Check for incremental compilation regressions

### Extension Memory Growth

1. Open/close DAG panel 10 times
2. Memory should be stable (not growing)
3. If growing, check static reference cleanup in panels
4. Limit step-through history (50 entries max)

### LSP Unresponsive

1. Check server logs for slow requests
2. Run `ConcurrencyBenchmark` to test parallel handling
3. Verify WebSocket buffer not full
4. Check CPU usage for compilation bottlenecks

### CI Benchmark Failures

1. Check if baseline was exceeded (regression)
2. Compare with recent master results
3. If legitimate slowdown, investigate cause
4. If baseline too aggressive, update with justification

### High Variance in Benchmark Results

1. Increase warmup iterations (minimum 5)
2. Close other applications (resource contention)
3. Run benchmarks in isolation
4. Consider GC pauses (use `-XX:+UseG1GC`)

---

## Adding New Benchmarks

See `docs/dev/benchmark-framework.md` for complete framework guide.

### Quick Steps

1. Create test class in `modules/lang-*/src/test/scala/.../benchmark/`
2. Use `BenchmarkHarness.measureWithWarmup()` for measurements
3. Use `TestFixtures` for consistent input
4. Add assertions for performance targets
5. Add baseline to `RegressionTests.scala` if critical
6. Document expected results in this file

### Template

```scala
package io.constellation.lang.benchmark

import io.constellation.lang._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MyNewBenchmark extends AnyFlatSpec with Matchers {
  val WarmupIterations  = 5
  val MeasureIterations = 20

  "MyOperation" should "complete within target" in {
    val result = BenchmarkHarness.measureWithWarmup(
      name = "my_operation",
      warmupIterations = WarmupIterations,
      measureIterations = MeasureIterations,
      phase = "my_phase",
      inputSize = "medium"
    ) {
      // Code to benchmark
    }

    println(result.toConsoleString)
    result.avgMs should be < 100.0
  }
}
```

---

## Command Reference

| Command | Purpose |
|---------|---------|
| `sbt "langCompiler/testOnly *Benchmark"` | All compiler benchmarks |
| `sbt "langCompiler/testOnly *CacheBenchmark"` | Cache only |
| `sbt "langCompiler/testOnly *VisualizationBenchmark"` | DAG viz only |
| `sbt "langCompiler/testOnly *IncrementalCompileBenchmark"` | Incremental only |
| `sbt "langCompiler/testOnly *MemoryBenchmark"` | Memory only |
| `sbt "langLsp/testOnly *Benchmark"` | All LSP benchmarks |
| `sbt "langLsp/testOnly *LspOperationsBenchmark"` | LSP operations |
| `sbt "langLsp/testOnly *ConcurrencyBenchmark"` | Concurrency only |
| `sbt "langLsp/testOnly *SemanticTokenBenchmark"` | Semantic tokens |
| `sbt "runtime/testOnly *ExecutionBenchmark"` | Runtime execution overhead |
| `sbt "runtime/testOnly *LargeDagStressTest"` | Large DAG stress (100–1000 nodes) |
| `sbt "runtime/testOnly *SustainedLoadTest"` | 10K sustained load + heap stability |
| `sbt "core/testOnly *TypeSystemPropertyTest"` | Type system property tests |
| `sbt "langCompiler/testOnly *CompilationPropertyTest"` | Compilation determinism |
| `sbt "langParser/testOnly *AdversarialFuzzingTest"` | Parser adversarial fuzzing |
| `sbt "langCompiler/testOnly *RegressionTests"` | Regression suite |
| `curl localhost:8080/metrics` | Server metrics |

---

## Related Documentation

- **Benchmark Framework Guide:** `docs/dev/benchmark-framework.md`
- **E2E Fixture README:** `vscode-extension/src/test/fixtures/PERF-README.md`
- **CI Workflow:** `.github/workflows/benchmark.yml`

---

*Last updated: 2026-01-29*
