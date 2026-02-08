# Optimization 01: Compilation Caching

**Priority:** 1 (Highest Impact)
**Expected Gain:** 50-200ms per request
**Complexity:** Low
**Status:** Not Implemented

---

## Problem Statement

Currently, every request to execute a Constellation pipeline triggers the full compilation process:

```
Source Code → Parser → Type Checker → IR Generator → DAG Compiler → DagSpec
```

This happens even when the exact same pipeline was compiled moments ago. For ML workflows that repeatedly execute the same transformation graph, this represents significant wasted computation.

### Current Flow

```scala
// LangCompiler.scala:86-99
def compile(source: String, modules: Map[String, FunctionSignature]): Either[CompileError, DagSpec] = {
  for {
    ast      <- parser.parse(source)           // ~10-30ms
    typed    <- typeChecker.check(ast, modules) // ~20-50ms
    ir       <- irGenerator.generate(typed)     // ~5-10ms
    dagSpec  <- dagCompiler.compile(ir, modules) // ~20-80ms
  } yield dagSpec
}
```

**Total compilation time: 55-170ms per request**

---

## Proposed Solution

Implement a source-hash-based compilation cache that stores compiled `DagSpec` objects.

### Implementation

#### Step 1: Add Cache Data Structure

```scala
// In LangCompiler.scala or a new CompilationCache.scala

import java.util.concurrent.ConcurrentHashMap
import scala.collection.concurrent.TrieMap

class CompilationCache(maxSize: Int = 1000) {
  // Thread-safe cache with LRU eviction
  private val cache = new ConcurrentHashMap[Int, CachedCompilation]()
  private val accessOrder = new java.util.LinkedHashMap[Int, Long](maxSize, 0.75f, true)

  case class CachedCompilation(
    dagSpec: DagSpec,
    compiledAt: Long,
    moduleSignatureHash: Int  // Invalidate if modules change
  )

  def get(sourceHash: Int, moduleHash: Int): Option[DagSpec] = {
    Option(cache.get(sourceHash)).filter(_.moduleSignatureHash == moduleHash).map { cached =>
      accessOrder.put(sourceHash, System.currentTimeMillis())
      cached.dagSpec
    }
  }

  def put(sourceHash: Int, moduleHash: Int, dagSpec: DagSpec): Unit = {
    evictIfNeeded()
    cache.put(sourceHash, CachedCompilation(dagSpec, System.currentTimeMillis(), moduleHash))
    accessOrder.put(sourceHash, System.currentTimeMillis())
  }

  private def evictIfNeeded(): Unit = {
    if (cache.size() >= maxSize) {
      // Remove oldest 10% of entries
      val toRemove = accessOrder.keySet().iterator()
      var removed = 0
      while (removed < maxSize / 10 && toRemove.hasNext) {
        val key = toRemove.next()
        cache.remove(key)
        toRemove.remove()
        removed += 1
      }
    }
  }
}
```

#### Step 2: Integrate with LangCompiler

```scala
// LangCompiler.scala

class LangCompiler(
  parser: ConstellationParser,
  typeChecker: TypeChecker,
  irGenerator: IRGenerator,
  dagCompiler: DagCompiler,
  cache: CompilationCache = new CompilationCache()
) {

  def compile(
    source: String,
    modules: Map[String, FunctionSignature]
  ): Either[CompileError, DagSpec] = {
    val sourceHash = source.hashCode
    val moduleHash = modules.hashCode

    // Check cache first
    cache.get(sourceHash, moduleHash) match {
      case Some(cached) => Right(cached)
      case None =>
        // Full compilation
        val result = for {
          ast     <- parser.parse(source)
          typed   <- typeChecker.check(ast, modules)
          ir      <- irGenerator.generate(typed)
          dagSpec <- dagCompiler.compile(ir, modules)
        } yield dagSpec

        // Cache successful compilations
        result.foreach(dagSpec => cache.put(sourceHash, moduleHash, dagSpec))
        result
    }
  }
}
```

#### Step 3: Add Cache Metrics

```scala
// For observability
case class CacheStats(
  hits: Long,
  misses: Long,
  evictions: Long,
  size: Int
) {
  def hitRate: Double = if (hits + misses == 0) 0.0 else hits.toDouble / (hits + misses)
}

class CompilationCache(maxSize: Int = 1000) {
  private val hits = new java.util.concurrent.atomic.AtomicLong(0)
  private val misses = new java.util.concurrent.atomic.AtomicLong(0)
  private val evictions = new java.util.concurrent.atomic.AtomicLong(0)

  def stats: CacheStats = CacheStats(
    hits.get(), misses.get(), evictions.get(), cache.size()
  )

  // ... rest of implementation
}
```

---

## Cache Key Considerations

### Why Source Hash?

Using `source.hashCode` is simple but has limitations:

| Approach | Pros | Cons |
|----------|------|------|
| `source.hashCode` | Simple, fast | Hash collisions possible (rare) |
| SHA-256 of source | No collisions | Slower to compute |
| Normalized AST hash | Ignores whitespace/comments | Requires parsing first |

**Recommendation:** Start with `source.hashCode` for simplicity. Hash collisions are extremely rare for pipeline-sized strings and the worst case is just recompilation.

### Module Signature Invalidation

The cache must invalidate when available modules change:

```scala
// If user adds a new module, old cache entries may be invalid
val moduleHash = modules.keys.toSeq.sorted.hashCode
```

---

## Memory Considerations

### DagSpec Size Estimation

A typical `DagSpec` contains:
- Data nodes: ~500 bytes each
- Module nodes: ~1KB each
- Edges: ~100 bytes each

**Estimate:** A 20-node DAG ≈ 30KB in memory

### Cache Sizing

| Cache Size | Memory Usage | Suitable For |
|------------|--------------|--------------|
| 100 entries | ~3MB | Development |
| 1000 entries | ~30MB | Production (small) |
| 10000 entries | ~300MB | Production (large) |

---

## Alternative: File-Based Cache

For persistence across restarts:

```scala
import java.nio.file.{Files, Path, Paths}
import io.circe.syntax._
import io.circe.parser._

class PersistentCompilationCache(cacheDir: Path) {
  def get(sourceHash: Int, moduleHash: Int): Option[DagSpec] = {
    val file = cacheDir.resolve(s"$sourceHash-$moduleHash.json")
    if (Files.exists(file)) {
      val json = Files.readString(file)
      decode[DagSpec](json).toOption
    } else None
  }

  def put(sourceHash: Int, moduleHash: Int, dagSpec: DagSpec): Unit = {
    Files.createDirectories(cacheDir)
    val file = cacheDir.resolve(s"$sourceHash-$moduleHash.json")
    Files.writeString(file, dagSpec.asJson.noSpaces)
  }
}
```

---

## Benchmarking

### Test Scenario

```scala
val source = """
  in text: String
  out upper: String
  upper = Uppercase(text)
"""

// Benchmark: 1000 compilations
val withoutCache = benchmark { compiler.compile(source, modules) }
val withCache    = benchmark { cachedCompiler.compile(source, modules) }
```

### Expected Results

| Metric | Without Cache | With Cache (hit) | Improvement |
|--------|---------------|------------------|-------------|
| Latency | 80ms | <1ms | 99%+ |
| CPU usage | High | Minimal | ~99% |
| Memory | Temp allocations | Cached object | Slight increase |

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Memory growth | LRU eviction policy |
| Stale cache entries | Module hash invalidation |
| Hash collisions | Use SHA-256 if critical |
| Cache stampede | Add mutex per source hash |

---

## Implementation Checklist

- [ ] Create `CompilationCache` class
- [ ] Integrate with `LangCompiler`
- [ ] Add cache stats endpoint to HTTP API
- [ ] Add configuration for cache size
- [ ] Write unit tests for cache behavior
- [ ] Benchmark before/after
- [ ] Document cache invalidation rules

---

## Files to Modify

| File | Changes |
|------|---------|
| `modules/lang-compiler/.../LangCompiler.scala` | Add cache integration |
| `modules/http-api/.../ConstellationServer.scala` | Add cache stats endpoint |
| New: `modules/lang-compiler/.../CompilationCache.scala` | Cache implementation |

---

## Related Optimizations

- [Module Initialization Pooling](./02-module-initialization-pooling.md) - Complements caching by reusing runtime objects
- [Quick Wins](./12-quick-wins.md) - JIT warmup improves cache effectiveness
