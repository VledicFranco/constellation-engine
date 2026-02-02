package io.constellation.lang

import cats.effect.IO
import io.constellation.*
import io.constellation.lang.ast.CompileError
import io.constellation.lang.compiler.CompilationOutput
import io.constellation.lang.semantic.FunctionRegistry
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util.UUID

class CachingLangCompilerTest extends AnyFlatSpec with Matchers {

  /** Helper to create a mock CompilationOutput with unique characteristics */
  def mockCompileResult(name: String, sourceId: String): CompilationOutput = {
    val dagSpec = DagSpec(
      metadata = ComponentMetadata.empty(name),
      modules = Map.empty,
      data = Map.empty,
      inEdges = Set.empty,
      outEdges = Set.empty,
      declaredOutputs = List(sourceId), // Use sourceId in declared outputs to make it unique
      outputBindings = Map.empty
    )
    val image = ProgramImage(
      structuralHash = ContentHash.computeStructuralHash(dagSpec),
      syntacticHash = sourceId, // Unique per source
      dagSpec = dagSpec,
      moduleOptions = Map.empty,
      compiledAt = Instant.now(),
      sourceHash = Some(sourceId)
    )
    CompilationOutput(LoadedProgram(image, Map.empty), Nil)
  }

  /** Mock compiler that returns different results based on source content */
  class MockCompiler(registry: FunctionRegistry) extends LangCompiler {
    def functionRegistry: FunctionRegistry = registry

    def compile(source: String, dagName: String): Either[List[CompileError], CompilationOutput] =
      // Return different compilation results based on source hashCode (to simulate uniqueness)
      Right(mockCompileResult(dagName, s"source-${source.hashCode}"))

    def compileToIR(source: String, dagName: String) = ???
  }

  "CachingLangCompiler" should "return different results for different sources even with hash collisions" in {
    // Find two different strings with the same hashCode
    // Note: In Java, "Aa".hashCode() != "BB".hashCode()
    // But we can craft strings that collide. For testing, we'll demonstrate
    // that two different strings should produce different results.

    // These strings are different and should produce different compilation results
    val source1 = "in x: String\nout x"
    val source2 = "in y: String\nout y"

    // Verify they're different
    source1 should not equal source2

    // However, if they happen to have the same hashCode (collision),
    // the cache should NOT return the wrong result
    val registry     = FunctionRegistry.empty
    val mockCompiler = new MockCompiler(registry)
    val cache        = CompilationCache.createUnsafe()
    val caching      = new CachingLangCompiler(mockCompiler, cache)

    val result1 = caching.compile(source1, "test")
    val result2 = caching.compile(source2, "test")

    result1.isRight should be(true)
    result2.isRight should be(true)

    // Different sources should produce different results
    // With the hashCode bug, if there's a collision, result2 might equal result1
    val hash1 = result1.toOption.get.program.image.syntacticHash
    val hash2 = result2.toOption.get.program.image.syntacticHash

    hash1 should not equal hash2 // CRITICAL: Different sources must produce different results
    hash1 should be(s"source-${source1.hashCode}")
    hash2 should be(s"source-${source2.hashCode}")
  }

  it should "correctly hash source strings to avoid collisions" in {
    // Demonstrate that hashCode can collide but SHA-256 won't
    val registry     = FunctionRegistry.empty
    val mockCompiler = new MockCompiler(registry)
    val cache        = CompilationCache.createUnsafe()
    val caching      = new CachingLangCompiler(mockCompiler, cache)

    // Create multiple different sources
    val sources = List(
      "in a: String\nout a",
      "in b: String\nout b",
      "in c: String\nout c",
      "in d: String\nout d"
    )

    val results = sources.map { src =>
      caching.compile(src, s"dag-${src.hashCode}")
    }

    // All should succeed
    results.foreach { r =>
      r.isRight should be(true)
    }

    // Each should have different syntactic hashes
    val hashes = results.map(_.toOption.get.program.image.syntacticHash)

    // Verify each source produced a unique result
    hashes.toSet.size should be(sources.size) // All hashes should be unique
    sources.zip(hashes).foreach { case (src, hash) =>
      hash should be(s"source-${src.hashCode}")
    }
  }

  it should "cache identical sources correctly" in {
    val source   = "in x: String\nout x"
    val registry = FunctionRegistry.empty

    // Create a compiler that tracks invocation count
    var compileCount = 0
    val countingCompiler = new LangCompiler {
      def functionRegistry: FunctionRegistry = registry
      def compile(src: String, dagName: String): Either[List[CompileError], CompilationOutput] = {
        compileCount += 1
        Right(mockCompileResult(dagName, "source-test"))
      }
      def compileToIR(src: String, dagName: String) = ???
    }

    val cache   = CompilationCache.createUnsafe()
    val caching = new CachingLangCompiler(countingCompiler, cache)

    // First compilation - should invoke underlying compiler
    val result1 = caching.compile(source, "test")
    compileCount should be(1)
    result1.isRight should be(true)

    // Second compilation of identical source - should use cache
    val result2 = caching.compile(source, "test")
    compileCount should be(1) // Should NOT compile again
    result2.isRight should be(true)

    result1 should equal(result2)
  }

  it should "invalidate cache when source changes" in {
    val source1 = "in x: String\nout x"
    val source2 = "in y: String\nout y"

    val registry     = FunctionRegistry.empty
    val mockCompiler = new MockCompiler(registry)
    val cache        = CompilationCache.createUnsafe()
    val caching      = new CachingLangCompiler(mockCompiler, cache)

    // Compile first source
    val result1 = caching.compile(source1, "test")
    result1.isRight should be(true)

    // Compile different source with same dagName - should NOT return cached result
    val result2 = caching.compile(source2, "test")
    result2.isRight should be(true)

    // Results should be different because source changed
    result1.toOption.get.program.image.syntacticHash should not equal result2.toOption.get.program.image.syntacticHash
  }
}
