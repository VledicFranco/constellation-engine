# Benchmark Results

This document contains measured performance baselines for the Constellation Engine. For framework usage, see [benchmark-framework.md](./benchmark-framework.md).

---

## Quick Reference

| Benchmark | Command | Key Result |
|-----------|---------|------------|
| Incremental Compile | `sbt "langCompiler/testOnly *IncrementalCompileBenchmark"` | 1-3ms per change |
| Visualization (1000 nodes) | `sbt "langCompiler/testOnly *VisualizationBenchmark"` | 33ms layout |
| Semantic Tokens | `sbt "langLsp/testOnly *SemanticTokenBenchmark"` | <3ms for large files |
| Cache Performance | `sbt "langCompiler/testOnly *CacheBenchmark"` | 7-90x speedup |
| Compiler Pipeline | `sbt "langCompiler/testOnly *CompilerPipelineBenchmark"` | <10ms full pipeline |
| Memory Profiling | `sbt "langCompiler/testOnly *MemoryBenchmark"` | <50MB per phase |
| Execution Performance | `sbt "runtime/testOnly *ExecutionBenchmark"` | ~0.2ms per node |

---

## Incremental Compilation Benchmarks

**File:** `modules/lang-compiler/src/test/scala/io/constellation/lang/benchmark/IncrementalCompileBenchmark.scala`

**Purpose:** Measures recompilation time for incremental source changes, establishing editor responsiveness baselines.

### Results

| Scenario | Average | Target | Status | Speedup vs Cold |
|----------|---------|--------|--------|-----------------|
| Cold baseline | 4.50ms | - | baseline | 1.0x |
| Comment addition | 1.23ms | <50ms | PASS | 3.7x |
| Whitespace changes | 1.15ms | <50ms | PASS | 3.9x |
| Variable rename | 2.10ms | <100ms | PASS | 2.1x |
| New output | 1.45ms | <100ms | PASS | 3.1x |
| New variable | 1.52ms | <100ms | PASS | 3.0x |
| Type definition | 2.85ms | <200ms | PASS | 1.6x |
| Expression change | 1.35ms | <100ms | PASS | 3.3x |

### Analysis

- **Average incremental time:** 1.23ms
- **Average speedup:** 2-4x vs cold compilation
- **Cache effectiveness:** Even though source hash changes invalidate the compilation cache, the IR cache provides significant speedup for unchanged code patterns.

### Interpretation

All incremental scenarios complete well under their targets. The editor will feel responsive even during rapid typing. The CachingLangCompiler's IR cache is working effectively.

---

## Visualization Benchmarks (Extended)

**File:** `modules/lang-compiler/src/test/scala/io/constellation/lang/benchmark/VisualizationBenchmark.scala`

**Purpose:** Measures DAG visualization performance including Sugiyama layout algorithm for graphs up to 1000 nodes.

### Layout Performance by Size

| Node Count | Nodes | Edges | Layout Time | Scaling Factor |
|------------|-------|-------|-------------|----------------|
| Small | 14 | 18 | 0.84ms | - |
| Medium | 47 | 62 | 1.33ms | - |
| Large | 95 | 127 | 2.89ms | - |
| Stress 100 | 106 | 205 | 5.11ms | baseline |
| Stress 500 | 510 | 1014 | 15.20ms | 2.98x |
| Stress 1000 | 1010 | 2014 | 33.40ms | 6.54x overall |

### Scaling Analysis

```
100 -> 500 nodes:   2.98x scaling (expected ~30x for O(n² log n))
500 -> 1000 nodes:  2.20x scaling (expected ~4.4x for O(n² log n))
100 -> 1000 nodes:  6.54x overall scaling
```

**Finding:** The Sugiyama layout implementation scales approximately O(n), much better than the theoretical O(n² log n) worst case. This is likely due to:
- Sparse graph structure (edges ≈ 2n)
- Early termination in crossing minimization
- Efficient layer assignment

### Warning Thresholds

Based on measured performance:
- **No warning needed** for graphs under 1000 nodes (33ms is acceptable)
- Consider warning at **2000+ nodes** if layout exceeds 100ms
- Current implementation handles large graphs well

---

## Semantic Token Benchmarks

**File:** `modules/lang-lsp/src/test/scala/io/constellation/lsp/benchmark/SemanticTokenBenchmark.scala`

**Purpose:** Measures semantic token generation for VS Code syntax highlighting.

### Results

| File Size | Lines | Tokens | Average | Target | Status |
|-----------|-------|--------|---------|--------|--------|
| Small | 15 | 23 | 0.49ms | <20ms | PASS |
| Medium | 61 | 85 | 0.68ms | <50ms | PASS |
| Large | 188 | 331 | 2.53ms | <100ms | PASS |

### Caching Analysis

| Measurement | Average | Std Dev |
|-------------|---------|---------|
| Cold calls (fresh provider) | 1.32ms | ±0.05ms |
| Warm calls (same provider) | 1.16ms | ±0.07ms |
| Speedup | 1.14x | - |

**Finding:** No significant caching opportunity. The 1.14x speedup indicates the current implementation is already efficient and doesn't benefit from result caching. The SemanticTokenProvider parses and tokenizes quickly enough that adding a cache would add complexity without meaningful benefit.

### Tokens per Line

- Small: 1.53 tokens/line
- Medium: 1.39 tokens/line
- Large: 1.76 tokens/line

---

## Cache Performance Benchmarks

**File:** `modules/lang-compiler/src/test/scala/io/constellation/lang/benchmark/CacheBenchmark.scala`

**Purpose:** Measures compilation cache effectiveness (cold vs warm).

### Results

| Size | Cold Cache | Warm Cache | Speedup |
|------|------------|------------|---------|
| Small | 3.21ms | 0.41ms | 7.9x |
| Medium | 5.45ms | 0.12ms | 45.4x |
| Large | 9.12ms | 0.10ms | 91.2x |

### Analysis

- Cache hits are extremely fast (<1ms)
- Larger programs benefit more from caching (higher speedup)
- The CachingLangCompiler uses source hash + registry hash as cache key

---

## Full Compilation Pipeline

**File:** `modules/lang-compiler/src/test/scala/io/constellation/lang/benchmark/CompilerPipelineBenchmark.scala`

**Purpose:** Measures each phase of the compilation pipeline.

### Pipeline Breakdown (Medium Program)

| Phase | Average | % of Total |
|-------|---------|------------|
| Parse | 1.23ms | 18% |
| TypeCheck | 2.15ms | 31% |
| IR Generation | 0.89ms | 13% |
| Optimization | 0.45ms | 7% |
| DAG Compile | 2.12ms | 31% |
| **Total** | **6.84ms** | 100% |

### Bottleneck Analysis

- **TypeCheck** and **DAG Compile** are the slowest phases
- Parse is efficient due to cats-parse performance
- Optimization is fast (few passes currently enabled)

---

## Performance Targets Summary

Based on measured results, these are the validated performance targets:

| Operation | Target | Measured | Margin |
|-----------|--------|----------|--------|
| Incremental compile (any change) | <100ms | <5ms | 20x |
| DAG visualization (100 nodes) | <500ms | 5ms | 100x |
| DAG visualization (1000 nodes) | <5000ms | 33ms | 150x |
| Semantic tokens (large file) | <100ms | 2.5ms | 40x |
| Cache hit | <20ms | <1ms | 20x |
| Full compile (medium) | <150ms | 7ms | 21x |

All operations have significant margin under their targets, indicating excellent performance.

---

## Running Benchmarks

### All Benchmarks
```bash
# Compiler benchmarks
sbt "langCompiler/testOnly *Benchmark"

# LSP benchmarks
sbt "langLsp/testOnly *Benchmark"

# Everything
sbt test
```

### Individual Benchmarks
```bash
sbt "langCompiler/testOnly *IncrementalCompileBenchmark"
sbt "langCompiler/testOnly *VisualizationBenchmark"
sbt "langCompiler/testOnly *CacheBenchmark"
sbt "langCompiler/testOnly *CompilerPipelineBenchmark"
sbt "langLsp/testOnly *SemanticTokenBenchmark"
sbt "langLsp/testOnly *LspOperationsBenchmark"
```

### With More Heap (for stress tests)
```bash
sbt -J-Xmx4G "langCompiler/testOnly *VisualizationBenchmark"
```

---

## Troubleshooting Slow Performance

### Compilation Taking >100ms

1. **Check cache effectiveness:**
   ```bash
   sbt "langCompiler/testOnly *CacheBenchmark"
   ```
   If cache speedup is low, the cache may be invalidating unexpectedly.

2. **Profile the pipeline:**
   ```bash
   sbt "langCompiler/testOnly *CompilerPipelineBenchmark"
   ```
   Identify which phase is slow.

3. **Check for large type definitions:** Complex nested types slow TypeChecker.

### DAG Visualization Slow

1. **Check node count:** Run with verbose output to see node/edge counts
2. **Consider simplifying:** Break large programs into smaller modules
3. **Profile layout:** The scaling analysis test shows expected times per node count

### Semantic Tokens Lagging

1. **Check parse errors:** Failed parses return empty tokens (graceful degradation)
2. **Profile with large file:** The benchmark uses a 188-line file as "large"
3. **Verify LSP connection:** Network latency may dominate for remote LSP

---

## Memory Benchmarks

**File:** `modules/lang-compiler/src/test/scala/io/constellation/lang/benchmark/MemoryBenchmark.scala`

**Purpose:** Measures heap memory consumption per compilation phase to establish baselines and identify memory-intensive operations.

### Running Memory Benchmarks

```bash
sbt "langCompiler/testOnly *MemoryBenchmark"
```

### Phase Memory Usage

| Phase | Small | Medium | Large | Target |
|-------|-------|--------|-------|--------|
| Parse | ~24MB | ~2MB | ~3MB | <50MB |
| TypeCheck | ~2MB | ~0.4MB | ~8MB | <50MB |
| IR Generation | ~4MB | ~1.5MB | ~0.7MB | <50MB |
| Optimization | - | - | ~5MB | <50MB |
| DAG Compile | - | - | ~11MB | <50MB |
| **Full Pipeline** | ~0.3MB | ~0.1MB | ~0.1MB | <50MB |

**Note:** JVM memory measurement is inherently noisy due to GC timing. Values are averages over 10 samples. The "Full Pipeline" memory is lower than individual phases because it measures retained memory after the entire operation, while phase measurements include intermediate objects.

### Stress Test Results

| Node Count | Heap Delta | MB per Node |
|------------|-----------|-------------|
| 100 nodes | ~0.1MB | ~0.001 MB/node |
| 200 nodes | ~0.14MB | ~0.0007 MB/node |

### Scaling Analysis

- **Scaling characteristic:** Sub-linear to linear (~0.5-0.9 ratio)
- Memory usage does not grow proportionally with program size
- JVM GC effectively manages intermediate objects

### JVM Heap Recommendations

Based on benchmark results:

| Use Case | Recommended Heap |
|----------|-----------------|
| Minimum for development | `-Xms40m` |
| Large workloads | `-Xmx100m` |
| Stress testing | `-Xmx200m` |

### Warning Thresholds (for LSP/Extension)

| Level | Per Compilation | Action |
|-------|----------------|--------|
| Normal | < 50 MB | No action needed |
| Warning | 50-100 MB | Monitor, consider splitting large files |
| Critical | > 200 MB | Investigate for memory leaks |

---

## Execution Benchmarks

**File:** `modules/runtime/src/test/scala/io/constellation/benchmark/ExecutionBenchmark.scala`

**Purpose:** Measures DAG execution performance for both full execution and step-through debugging modes.

### Running Execution Benchmarks

```bash
sbt "runtime/testOnly *ExecutionBenchmark"
```

### Full Execution Performance

| DAG Size | Nodes | Average | Target | Throughput |
|----------|-------|---------|--------|------------|
| Simple | 1 | ~1.8ms | <50ms | ~560 ops/s |
| Medium | 3 | ~2.1ms | <100ms | ~480 ops/s |
| Large | 10 | ~4.8ms | <200ms | ~210 ops/s |
| Stress 50 | 50 | ~12ms | <500ms | ~85 ops/s |
| Stress 100 | 100 | ~21ms | <1000ms | ~47 ops/s |

### Step-through Execution Performance

| DAG Size | Average per Step | Target | Throughput |
|----------|-----------------|--------|------------|
| Simple | ~0.3ms | <100ms | ~3300 steps/s |
| Medium | ~0.3ms | <100ms | ~3300 steps/s |

### Scaling Analysis

```
Simple -> Large:     2.7x time increase (10x node increase)
Stress50 -> Stress100: 1.8x time increase (2x node increase)
Scaling characteristic: Linear (~0.9 ratio)
```

**Finding:** Execution scales approximately linearly with node count. Each node adds ~0.21ms of execution time.

### Performance Targets Summary

| Operation | Target | Status |
|-----------|--------|--------|
| Simple DAG execution | <50ms | PASS (~2ms) |
| Medium DAG execution | <100ms | PASS (~2ms) |
| Large DAG execution | <200ms | PASS (~5ms) |
| Stress test (100 nodes) | <1000ms | PASS (~21ms) |
| Step-through per batch | <100ms | PASS (~0.3ms) |

All execution benchmarks pass with significant margin, ensuring interactive responsiveness.

---

## VS Code Extension Memory Management

### Memory Limits in Extension

The VS Code extension implements memory management to prevent unbounded growth during long sessions:

| Component | Behavior |
|-----------|----------|
| Step-through session state | Cleaned via `_cleanupStepSession()` on stop/complete |
| Panel dispose | Clears all accumulated state and static references |
| WebView state | Reset on new execution via `setAllNodesState('pending')` |

### Memory Management Implementation

**ScriptRunnerPanel:**
- `_cleanupStepSession()`: Clears `_steppingSessionId` and `_isStepping` flag
- Called on: stop, continue, error, and dispose
- Prevents step-through state accumulation

**DagVisualizerPanel:**
- Execution states stored in WebView (cleared on panel close)
- `resetExecutionStates()` clears state on demand
- Static `currentPanel` reference cleared on dispose

### Testing Memory Management

Tests in `vscode-extension/src/test/suite/e2e/memory-management.test.ts` verify:
- Step-through session cleanup on stop/complete/error
- Panel dispose clearing all state
- Rapid open/close cycles not leaking memory
- Multiple executions not causing memory growth

### Troubleshooting Extension Memory Issues

1. **Check extension host memory** in Task Manager/Activity Monitor
2. **If memory is growing:**
   - Check for panel leaks (open/close panels 10 times)
   - Verify step-through sessions are being stopped properly
3. **Long step-through sessions:**
   - Stop and restart periodically
   - Memory is bounded by panel lifecycle
4. **Large files:**
   - Consider splitting into smaller modules
   - Each module compiles independently

---

*Measured on: 2026-01-24*
*Environment: JVM 17, Scala 3.3.4*
