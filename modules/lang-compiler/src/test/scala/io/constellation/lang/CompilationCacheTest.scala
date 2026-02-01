package io.constellation.lang

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.*
import io.constellation.lang.compiler.CompilationOutput
import io.constellation.lang.semantic.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration._

class CompilationCacheTest extends AnyFlatSpec with Matchers {

  /** Create a mock CompilationOutput for testing */
  private def mockCompileResult(name: String = "test-dag"): CompilationOutput = {
    val dagSpec = DagSpec(
      metadata = ComponentMetadata.empty(name),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List.empty,
      outputBindings = Map.empty
    )
    val image = ProgramImage(
      structuralHash = ContentHash.computeStructuralHash(dagSpec),
      syntacticHash = "",
      dagSpec = dagSpec,
      moduleOptions = Map.empty,
      compiledAt = Instant.now(),
      sourceHash = None
    )
    CompilationOutput(LoadedProgram(image, Map.empty), Nil)
  }

  // ============================================
  // CompilationCache Unit Tests
  // ============================================

  "CompilationCache" should "return cached result on cache hit" in {
    val cache  = CompilationCache.createUnsafe()
    val result = mockCompileResult()

    cache.put("test", "hash123", "hash456", result).unsafeRunSync()
    val retrieved = cache.get("test", "hash123", "hash456").unsafeRunSync()

    retrieved shouldBe Some(result)
  }

  it should "return None on cache miss" in {
    val cache = CompilationCache.createUnsafe()
    val retrieved = cache.get("test", "hash123", "hash456").unsafeRunSync()

    retrieved shouldBe None
  }

  it should "return None when source hash changes" in {
    val cache  = CompilationCache.createUnsafe()
    val result = mockCompileResult()

    cache.put("test", "hash123", "hash456", result).unsafeRunSync()
    val retrieved = cache.get("test", "hash999", "hash456").unsafeRunSync() // Different source hash

    retrieved shouldBe None
  }

  it should "return None when registry hash changes" in {
    val cache  = CompilationCache.createUnsafe()
    val result = mockCompileResult()

    cache.put("test", "hash123", "hash456", result).unsafeRunSync()
    val retrieved = cache.get("test", "hash123", "hash999").unsafeRunSync() // Different registry hash

    retrieved shouldBe None
  }

  it should "evict LRU entries when cache is full" in {
    val config = CompilationCache.Config(maxEntries = 2)
    val cache  = CompilationCache.createUnsafe(config)

    cache.put("a", "hash1", "hash1", mockCompileResult("a")).unsafeRunSync()
    Thread.sleep(5) // Ensure different timestamps
    cache.put("b", "hash2", "hash2", mockCompileResult("b")).unsafeRunSync()
    Thread.sleep(5) // Ensure different timestamps
    cache.get("a", "hash1", "hash1").unsafeRunSync() // Access 'a' to make it more recent than 'b'
    Thread.sleep(5) // Ensure different timestamps
    cache.put("c", "hash3", "hash3", mockCompileResult("c")).unsafeRunSync() // Should evict 'b'

    cache.get("a", "hash1", "hash1").unsafeRunSync() should not be empty
    cache.get("b", "hash2", "hash2").unsafeRunSync() shouldBe None // Evicted
    cache.get("c", "hash3", "hash3").unsafeRunSync() should not be empty
  }

  it should "expire entries after TTL" in {
    val config = CompilationCache.Config(maxAge = 50.millis)
    val cache  = CompilationCache.createUnsafe(config)

    cache.put("test", "hash123", "hash456", mockCompileResult()).unsafeRunSync()

    // Should be present immediately
    cache.get("test", "hash123", "hash456").unsafeRunSync() should not be empty

    // Wait for TTL to expire
    Thread.sleep(100)

    // Should be expired now
    cache.get("test", "hash123", "hash456").unsafeRunSync() shouldBe None
  }

  it should "track cache statistics correctly" in {
    val cache = CompilationCache.createUnsafe()

    // Cache miss
    cache.get("test", "hash123", "hash456").unsafeRunSync()
    var stats = cache.stats.unsafeRunSync()
    stats.misses shouldBe 1
    stats.hits shouldBe 0

    // Put and cache hit
    cache.put("test", "hash123", "hash456", mockCompileResult()).unsafeRunSync()
    cache.get("test", "hash123", "hash456").unsafeRunSync()
    stats = cache.stats.unsafeRunSync()
    stats.hits shouldBe 1
    stats.misses shouldBe 1
    stats.entries shouldBe 1
  }

  it should "track eviction statistics" in {
    val config = CompilationCache.Config(maxEntries = 1)
    val cache  = CompilationCache.createUnsafe(config)

    cache.put("a", "hash1", "hash1", mockCompileResult()).unsafeRunSync()
    cache.put("b", "hash2", "hash2", mockCompileResult()).unsafeRunSync() // Should evict 'a'

    val stats = cache.stats.unsafeRunSync()
    stats.evictions shouldBe 1
  }

  it should "invalidate specific entries" in {
    val cache = CompilationCache.createUnsafe()

    cache.put("a", "hash1", "hash1", mockCompileResult("a")).unsafeRunSync()
    cache.put("b", "hash2", "hash2", mockCompileResult("b")).unsafeRunSync()

    cache.invalidate("a").unsafeRunSync()

    cache.get("a", "hash1", "hash1").unsafeRunSync() shouldBe None
    cache.get("b", "hash2", "hash2").unsafeRunSync() should not be empty
  }

  it should "invalidate all entries" in {
    val cache = CompilationCache.createUnsafe()

    cache.put("a", "hash1", "hash1", mockCompileResult("a")).unsafeRunSync()
    cache.put("b", "hash2", "hash2", mockCompileResult("b")).unsafeRunSync()

    cache.invalidateAll().unsafeRunSync()

    cache.get("a", "hash1", "hash1").unsafeRunSync() shouldBe None
    cache.get("b", "hash2", "hash2").unsafeRunSync() shouldBe None
    cache.size.unsafeRunSync() shouldBe 0
  }

  it should "calculate hit rate correctly" in {
    val cache = CompilationCache.createUnsafe()

    // 2 misses, then 2 hits
    cache.get("test", "hash123", "hash456").unsafeRunSync() // miss
    cache.get("test", "hash123", "hash456").unsafeRunSync() // miss
    cache.put("test", "hash123", "hash456", mockCompileResult()).unsafeRunSync()
    cache.get("test", "hash123", "hash456").unsafeRunSync() // hit
    cache.get("test", "hash123", "hash456").unsafeRunSync() // hit

    val stats = cache.stats.unsafeRunSync()
    stats.hits shouldBe 2
    stats.misses shouldBe 2
    stats.hitRate shouldBe 0.5 +- 0.001
  }

  it should "return 0.0 hit rate when no operations" in {
    val cache = CompilationCache.createUnsafe()
    val stats = cache.stats.unsafeRunSync()
    stats.hitRate shouldBe 0.0
  }

  // ============================================
  // CachingLangCompiler Tests
  // ============================================

  "CachingLangCompiler" should "compile only once for same source" in {
    var compileCount = 0

    val underlying = new LangCompiler {
      def functionRegistry: FunctionRegistry = FunctionRegistry.empty
      def compile(source: String, dagName: String): Either[List[io.constellation.lang.ast.CompileError], CompilationOutput] = {
        compileCount += 1
        Right(mockCompileResult(dagName))
      }
      def compileToIR(source: String, dagName: String) = Left(List.empty)
    }

    val cache    = CompilationCache.createUnsafe()
    val compiler = new CachingLangCompiler(underlying, cache)

    compiler.compile("in x: Int\nout x", "test")
    compiler.compile("in x: Int\nout x", "test")
    compiler.compile("in x: Int\nout x", "test")

    compileCount shouldBe 1
  }

  it should "recompile when source changes" in {
    var compileCount = 0

    val underlying = new LangCompiler {
      def functionRegistry: FunctionRegistry = FunctionRegistry.empty
      def compile(source: String, dagName: String): Either[List[io.constellation.lang.ast.CompileError], CompilationOutput] = {
        compileCount += 1
        Right(mockCompileResult(dagName))
      }
      def compileToIR(source: String, dagName: String) = Left(List.empty)
    }

    val cache    = CompilationCache.createUnsafe()
    val compiler = new CachingLangCompiler(underlying, cache)

    compiler.compile("in x: Int\nout x", "test")
    compiler.compile("in y: String\nout y", "test") // Different source

    compileCount shouldBe 2
  }

  it should "compile different dags separately" in {
    var compileCount = 0

    val underlying = new LangCompiler {
      def functionRegistry: FunctionRegistry = FunctionRegistry.empty
      def compile(source: String, dagName: String): Either[List[io.constellation.lang.ast.CompileError], CompilationOutput] = {
        compileCount += 1
        Right(mockCompileResult(dagName))
      }
      def compileToIR(source: String, dagName: String) = Left(List.empty)
    }

    val cache    = CompilationCache.createUnsafe()
    val compiler = new CachingLangCompiler(underlying, cache)

    compiler.compile("in x: Int\nout x", "dag1")
    compiler.compile("in x: Int\nout x", "dag2") // Same source, different dag

    compileCount shouldBe 2
  }

  it should "not cache compilation errors" in {
    var compileCount = 0

    val underlying = new LangCompiler {
      def functionRegistry: FunctionRegistry = FunctionRegistry.empty
      def compile(source: String, dagName: String): Either[List[io.constellation.lang.ast.CompileError], CompilationOutput] = {
        compileCount += 1
        Left(List(io.constellation.lang.ast.CompileError.InternalError("test error")))
      }
      def compileToIR(source: String, dagName: String) = Left(List.empty)
    }

    val cache    = CompilationCache.createUnsafe()
    val compiler = new CachingLangCompiler(underlying, cache)

    compiler.compile("invalid", "test")
    compiler.compile("invalid", "test")

    // Errors are not cached, so compile is called each time
    compileCount shouldBe 2
  }

  it should "expose cache statistics" in {
    var compileCount = 0

    val underlying = new LangCompiler {
      def functionRegistry: FunctionRegistry = FunctionRegistry.empty
      def compile(source: String, dagName: String): Either[List[io.constellation.lang.ast.CompileError], CompilationOutput] = {
        compileCount += 1
        Right(mockCompileResult(dagName))
      }
      def compileToIR(source: String, dagName: String) = Left(List.empty)
    }

    val cache    = CompilationCache.createUnsafe()
    val compiler = new CachingLangCompiler(underlying, cache)

    compiler.compile("in x: Int\nout x", "test") // miss + compile
    compiler.compile("in x: Int\nout x", "test") // hit
    compiler.compile("in x: Int\nout x", "test") // hit

    val stats = compiler.cacheStats
    stats.hits shouldBe 2
    stats.misses shouldBe 1
    compileCount shouldBe 1
  }

  it should "allow manual invalidation" in {
    var compileCount = 0

    val underlying = new LangCompiler {
      def functionRegistry: FunctionRegistry = FunctionRegistry.empty
      def compile(source: String, dagName: String): Either[List[io.constellation.lang.ast.CompileError], CompilationOutput] = {
        compileCount += 1
        Right(mockCompileResult(dagName))
      }
      def compileToIR(source: String, dagName: String) = Left(List.empty)
    }

    val cache    = CompilationCache.createUnsafe()
    val compiler = new CachingLangCompiler(underlying, cache)

    compiler.compile("in x: Int\nout x", "test")
    compiler.invalidate("test")
    compiler.compile("in x: Int\nout x", "test")

    compileCount shouldBe 2
  }

  // ============================================
  // LangCompilerBuilder Cache Integration Tests
  // ============================================

  "LangCompilerBuilder with caching" should "enable caching via withCaching" in {
    val compiler = LangCompiler.builder
      .withCaching()
      .build

    compiler shouldBe a[CachingLangCompiler]
  }

  it should "use custom cache config" in {
    val config = CompilationCache.Config(maxEntries = 10, maxAge = 30.seconds)
    val compiler = LangCompiler.builder
      .withCaching(config)
      .build

    compiler shouldBe a[CachingLangCompiler]
  }

  it should "disable caching via withoutCaching" in {
    val compiler = LangCompiler.builder
      .withCaching()
      .withoutCaching
      .build

    compiler should not be a[CachingLangCompiler]
  }

  it should "not use caching by default" in {
    val compiler = LangCompiler.builder.build

    compiler should not be a[CachingLangCompiler]
  }

  // ============================================
  // Thread Safety Tests
  // ============================================

  "CompilationCache" should "be thread-safe under concurrent access" in {
    val cache = CompilationCache.createUnsafe()

    // Run many concurrent operations
    val operations = (0 until 100).map { i =>
      IO {
        cache.put(s"dag$i", s"hash$i", s"hash$i", mockCompileResult(s"dag$i")).unsafeRunSync()
        cache.get(s"dag$i", s"hash$i", s"hash$i").unsafeRunSync()
      }
    }

    import cats.syntax.all._
    operations.toList.parSequence.unsafeRunSync()

    // Should have completed without errors
    cache.size.unsafeRunSync() should be <= 100
  }
}
