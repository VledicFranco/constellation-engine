# Task 1.1: Compilation Caching

**Phase:** 1 - Quick Wins
**Effort:** Low (2-3 days)
**Impact:** High (50-200ms saved per request)
**Dependencies:** None
**Blocks:** Task 2.1 (IR Optimization)

---

## Objective

Implement a caching layer for compilation results to avoid redundant parsing, type checking, and IR generation when the same source is compiled multiple times.

---

## Background

### Current Behavior

Every call to `compiler.compile(source, dagName)` performs the full compilation pipeline:

```scala
// LangCompiler.scala - current implementation
def compile(source: String, dagName: String): Either[List[CompileError], CompileResult] = {
  for {
    program <- ConstellationParser.parse(source)           // ~5-20ms
    typedProgram <- TypeChecker.check(program, registry)   // ~10-50ms
    irProgram = IRGenerator.generate(typedProgram)         // ~5-10ms
    result <- DagCompiler.compile(irProgram, dagName, modules)  // ~20-100ms
  } yield result
}
```

**Problem:** When the same pipeline is compiled multiple times (e.g., LSP validation on every keystroke, repeated API calls), all this work is duplicated.

### Typical Usage Patterns

1. **LSP Validation:** Same file recompiled on every save (~100x/hour)
2. **HTTP API:** Same pipeline compiled on every request
3. **Development:** Recompilation during hot-reload cycles

---

## Technical Design

### Cache Structure

```scala
case class CacheEntry(
  sourceHash: Int,           // Hash of source code
  parseResult: Program,      // Cached AST
  typedResult: TypedProgram, // Cached typed AST
  irResult: IRProgram,       // Cached IR
  dagResult: CompileResult,  // Final result
  timestamp: Long,           // For LRU eviction
  registryHash: Int          // Hash of function registry (invalidate if modules change)
)

class CompilationCache(
  maxEntries: Int = 100,
  maxAge: FiniteDuration = 1.hour
) {
  private val cache: Ref[IO, Map[String, CacheEntry]]

  def get(dagName: String, sourceHash: Int, registryHash: Int): IO[Option[CompileResult]]
  def put(dagName: String, entry: CacheEntry): IO[Unit]
  def invalidate(dagName: String): IO[Unit]
  def invalidateAll(): IO[Unit]
  def stats: IO[CacheStats]
}
```

### Cache Key Strategy

The cache key should incorporate:
1. **Source hash:** Content of the source code
2. **DAG name:** Identifier for the pipeline
3. **Registry hash:** Hash of registered functions/modules (cache invalidates if modules change)

```scala
def cacheKey(dagName: String, source: String, registry: FunctionRegistry): Int = {
  (dagName, source.hashCode, registry.signatures.hashCode).hashCode
}
```

### Integration Points

```scala
// Updated LangCompiler
class CachingLangCompiler(
  underlying: LangCompiler,
  cache: CompilationCache
) extends LangCompiler {

  def compile(source: String, dagName: String): Either[List[CompileError], CompileResult] = {
    val sourceHash = source.hashCode
    val registryHash = underlying.functionRegistry.signatures.hashCode

    cache.get(dagName, sourceHash, registryHash).flatMap {
      case Some(cached) =>
        IO.pure(Right(cached))
      case None =>
        underlying.compile(source, dagName).flatMap { result =>
          result.foreach(r => cache.put(dagName, CacheEntry(...)))
          IO.pure(result)
        }
    }
  }
}
```

---

## Deliverables

### Required

- [ ] **`CompilationCache.scala`** - Cache implementation with:
  - Thread-safe storage using `Ref[IO, Map[...]]`
  - LRU eviction policy
  - TTL-based expiration
  - Cache statistics (hits, misses, evictions)

- [ ] **`CachingLangCompiler.scala`** - Wrapper that adds caching:
  - Transparent caching of compilation results
  - Proper cache invalidation when registry changes
  - Configurable cache size and TTL

- [ ] **Integration with `LangCompiler`**:
  - Update `LangCompiler.empty` and `StdLib.compiler` to use caching
  - Add configuration options

- [ ] **Unit Tests**:
  - Cache hit returns same result
  - Cache miss triggers compilation
  - Registry change invalidates cache
  - TTL expiration works
  - LRU eviction works
  - Thread safety tests

- [ ] **Benchmark**:
  - Before/after comparison of repeated compilations
  - Memory usage measurement

### Optional Enhancements

- [ ] Partial caching (cache AST separately from typed AST)
- [ ] Persistence to disk for cross-session caching
- [ ] Cache warming on startup

---

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `modules/lang-compiler/src/main/scala/io/constellation/lang/CompilationCache.scala` | **New** | Cache implementation |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/CachingLangCompiler.scala` | **New** | Caching wrapper |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/LangCompiler.scala` | Modify | Add cache integration |
| `modules/lang-compiler/src/main/scala/io/constellation/lang/LangCompilerBuilder.scala` | Modify | Add cache configuration |
| `modules/lang-stdlib/src/main/scala/io/constellation/stdlib/StdLib.scala` | Modify | Use caching compiler |
| `modules/lang-compiler/src/test/scala/io/constellation/lang/CompilationCacheTest.scala` | **New** | Tests |

---

## Implementation Guide

> **Overview:** 4 steps | ~6 new files | Estimated 2-3 days

### Step 1: Create Cache Data Structures

```scala
// CompilationCache.scala
package io.constellation.lang

import cats.effect.{IO, Ref}
import scala.concurrent.duration._

case class CacheStats(
  hits: Long,
  misses: Long,
  evictions: Long,
  entries: Int
) {
  def hitRate: Double = if (hits + misses == 0) 0.0 else hits.toDouble / (hits + misses)
}

case class CacheEntry(
  sourceHash: Int,
  registryHash: Int,
  result: CompileResult,
  createdAt: Long,
  lastAccessed: Long
)

class CompilationCache private (
  cache: Ref[IO, Map[String, CacheEntry]],
  stats: Ref[IO, CacheStats],
  config: CompilationCache.Config
) {
  // Implementation...
}

object CompilationCache {
  case class Config(
    maxEntries: Int = 100,
    maxAge: FiniteDuration = 1.hour
  )

  def create(config: Config = Config()): IO[CompilationCache] = {
    for {
      cache <- Ref.of[IO, Map[String, CacheEntry]](Map.empty)
      stats <- Ref.of[IO, CacheStats](CacheStats(0, 0, 0, 0))
    } yield new CompilationCache(cache, stats, config)
  }
}
```

### Step 2: Implement Cache Operations

```scala
// In CompilationCache class
def get(dagName: String, sourceHash: Int, registryHash: Int): IO[Option[CompileResult]] = {
  val now = System.currentTimeMillis()
  cache.modify { entries =>
    entries.get(dagName) match {
      case Some(entry) if isValid(entry, sourceHash, registryHash, now) =>
        val updated = entries.updated(dagName, entry.copy(lastAccessed = now))
        (updated, Some(entry.result))
      case Some(_) =>
        // Invalid entry (hash mismatch or expired)
        (entries - dagName, None)
      case None =>
        (entries, None)
    }
  }.flatTap {
    case Some(_) => stats.update(s => s.copy(hits = s.hits + 1))
    case None => stats.update(s => s.copy(misses = s.misses + 1))
  }
}

def put(dagName: String, sourceHash: Int, registryHash: Int, result: CompileResult): IO[Unit] = {
  val now = System.currentTimeMillis()
  val entry = CacheEntry(sourceHash, registryHash, result, now, now)

  cache.modify { entries =>
    val evicted = if (entries.size >= config.maxEntries) {
      // LRU eviction
      val oldest = entries.minBy(_._2.lastAccessed)._1
      entries - oldest
    } else entries

    (evicted.updated(dagName, entry), evicted.size < entries.size)
  }.flatMap { wasEvicted =>
    if (wasEvicted) stats.update(s => s.copy(evictions = s.evictions + 1))
    else IO.unit
  }
}

private def isValid(entry: CacheEntry, sourceHash: Int, registryHash: Int, now: Long): Boolean = {
  entry.sourceHash == sourceHash &&
  entry.registryHash == registryHash &&
  (now - entry.createdAt) < config.maxAge.toMillis
}
```

### Step 3: Create Caching Wrapper

```scala
// CachingLangCompiler.scala
package io.constellation.lang

class CachingLangCompiler(
  underlying: LangCompiler,
  cache: CompilationCache
) extends LangCompiler {

  def functionRegistry: FunctionRegistry = underlying.functionRegistry

  def compile(source: String, dagName: String): Either[List[CompileError], CompileResult] = {
    val sourceHash = source.hashCode
    val registryHash = functionRegistry.signatures.hashCode

    // Note: This is a simplified sync version. For full IO integration,
    // the LangCompiler trait would need to return IO[Either[...]]
    cache.get(dagName, sourceHash, registryHash).unsafeRunSync() match {
      case Some(cached) => Right(cached)
      case None =>
        val result = underlying.compile(source, dagName)
        result.foreach { r =>
          cache.put(dagName, sourceHash, registryHash, r).unsafeRunSync()
        }
        result
    }
  }
}
```

### Step 4: Integration

```scala
// In LangCompilerBuilder
case class LangCompilerBuilder(
  // ... existing fields
  cacheConfig: Option[CompilationCache.Config] = Some(CompilationCache.Config())
) {
  def withCaching(config: CompilationCache.Config): LangCompilerBuilder =
    copy(cacheConfig = Some(config))

  def withoutCaching: LangCompilerBuilder =
    copy(cacheConfig = None)

  def build: LangCompiler = {
    val base = // ... existing build logic
    cacheConfig match {
      case Some(config) =>
        val cache = CompilationCache.create(config).unsafeRunSync()
        new CachingLangCompiler(base, cache)
      case None =>
        base
    }
  }
}
```

---

## Testing Strategy

### Unit Tests

```scala
class CompilationCacheTest extends AnyFlatSpec with Matchers {

  "CompilationCache" should "return cached result on cache hit" in {
    val cache = CompilationCache.create().unsafeRunSync()
    val result = mockCompileResult()

    cache.put("test", 123, 456, result).unsafeRunSync()
    cache.get("test", 123, 456).unsafeRunSync() shouldBe Some(result)
  }

  it should "return None on cache miss" in {
    val cache = CompilationCache.create().unsafeRunSync()
    cache.get("test", 123, 456).unsafeRunSync() shouldBe None
  }

  it should "invalidate on source hash change" in {
    val cache = CompilationCache.create().unsafeRunSync()
    val result = mockCompileResult()

    cache.put("test", 123, 456, result).unsafeRunSync()
    cache.get("test", 999, 456).unsafeRunSync() shouldBe None  // Different source hash
  }

  it should "invalidate on registry hash change" in {
    val cache = CompilationCache.create().unsafeRunSync()
    val result = mockCompileResult()

    cache.put("test", 123, 456, result).unsafeRunSync()
    cache.get("test", 123, 999).unsafeRunSync() shouldBe None  // Different registry hash
  }

  it should "evict LRU entries when full" in {
    val config = CompilationCache.Config(maxEntries = 2)
    val cache = CompilationCache.create(config).unsafeRunSync()

    cache.put("a", 1, 1, mockCompileResult()).unsafeRunSync()
    cache.put("b", 2, 2, mockCompileResult()).unsafeRunSync()
    cache.get("a", 1, 1).unsafeRunSync()  // Access 'a' to make it recent
    cache.put("c", 3, 3, mockCompileResult()).unsafeRunSync()  // Should evict 'b'

    cache.get("a", 1, 1).unsafeRunSync() should not be empty
    cache.get("b", 2, 2).unsafeRunSync() shouldBe None  // Evicted
    cache.get("c", 3, 3).unsafeRunSync() should not be empty
  }

  it should "expire entries after TTL" in {
    val config = CompilationCache.Config(maxAge = 100.millis)
    val cache = CompilationCache.create(config).unsafeRunSync()

    cache.put("test", 123, 456, mockCompileResult()).unsafeRunSync()
    Thread.sleep(150)
    cache.get("test", 123, 456).unsafeRunSync() shouldBe None
  }
}
```

### Integration Tests

```scala
class CachingLangCompilerTest extends AnyFlatSpec with Matchers {

  "CachingLangCompiler" should "compile only once for same source" in {
    var compileCount = 0
    val underlying = new LangCompiler {
      def functionRegistry = FunctionRegistry.empty
      def compile(source: String, dagName: String) = {
        compileCount += 1
        Right(mockCompileResult())
      }
    }

    val cache = CompilationCache.create().unsafeRunSync()
    val compiler = new CachingLangCompiler(underlying, cache)

    compiler.compile("in x: Int\nout x", "test")
    compiler.compile("in x: Int\nout x", "test")
    compiler.compile("in x: Int\nout x", "test")

    compileCount shouldBe 1
  }
}
```

### Benchmark

```scala
object CompilationCacheBenchmark extends App {
  val source = """
    in text: String
    cleaned = Trim(text)
    upper = Uppercase(cleaned)
    words = WordCount(upper)
    out upper
    out words
  """

  // Without caching
  val uncached = LangCompilerBuilder().withoutCaching.build
  val uncachedTime = benchmark(100) {
    uncached.compile(source, "test")
  }

  // With caching
  val cached = LangCompilerBuilder().withCaching(CompilationCache.Config()).build
  val cachedTime = benchmark(100) {
    cached.compile(source, "test")
  }

  println(s"Uncached: ${uncachedTime}ms avg")
  println(s"Cached: ${cachedTime}ms avg")
  println(s"Speedup: ${uncachedTime / cachedTime}x")
}
```

---

## Web Resources

### Caching Patterns
- [Caffeine Cache (Java)](https://github.com/ben-manes/caffeine) - High-performance caching library with LRU, TTL
- [Scalacache](https://cb372.github.io/scalacache/) - Scala caching facade supporting multiple backends
- [Cats Effect Ref](https://typelevel.org/cats-effect/docs/std/ref) - Thread-safe mutable reference for IO

### Compiler Caching Examples
- [Rust Incremental Compilation](https://blog.rust-lang.org/2016/09/08/incremental.html) - How Rust caches compilation
- [TypeScript Build Watch Mode](https://www.typescriptlang.org/docs/handbook/configuring-watch.html) - File watching and caching
- [Babel Cache](https://babeljs.io/docs/en/config-files#apicache) - JavaScript transpiler caching

### Performance Measurement
- [JMH - Java Microbenchmark Harness](https://github.com/openjdk/jmh) - Standard benchmarking tool
- [sbt-jmh](https://github.com/sbt/sbt-jmh) - SBT plugin for JMH

---

## Acceptance Criteria

1. **Functional Requirements**
   - [ ] Cache returns same result for identical source + registry
   - [ ] Cache invalidates on source change
   - [ ] Cache invalidates on registry change
   - [ ] LRU eviction keeps cache bounded
   - [ ] TTL expiration prevents stale results

2. **Performance Requirements**
   - [ ] Cache lookup < 1ms
   - [ ] Repeated compilation with cache > 10x faster than without
   - [ ] Memory overhead < 10MB for 100 entries

3. **Quality Requirements**
   - [ ] Unit test coverage > 80%
   - [ ] No test regressions
   - [ ] Thread-safe implementation

---

## Notes for Implementer

1. **Start with synchronous implementation** - The current `LangCompiler.compile` returns `Either`, not `IO`. You may need to use `unsafeRunSync()` internally or propose changing the trait signature.

2. **Consider cache granularity** - You could cache at finer granularity (AST, typed AST separately) but start simple with full result caching.

3. **Watch for memory leaks** - Ensure eviction and invalidation actually free memory.

4. **Thread safety is critical** - Multiple LSP clients or API requests may compile concurrently.

5. **Background reading** - `docs/dev/optimizations/01-compilation-caching.md` contains early design exploration. **Use for background context only** - this context file is the canonical implementation spec. Key differences:
   - This file uses `Ref[IO, Map]` (cats-effect); the doc uses `ConcurrentHashMap`
   - This file uses dagName-based keys; the doc uses sourceHash-based keys
   - **Follow this context file when there are conflicts**
