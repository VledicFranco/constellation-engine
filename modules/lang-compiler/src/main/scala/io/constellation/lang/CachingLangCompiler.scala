package io.constellation.lang

import cats.effect.unsafe.implicits.global
import io.constellation.lang.ast.CompileError
import io.constellation.lang.compiler.{CompileResult, IRProgram}
import io.constellation.lang.semantic.FunctionRegistry

/** A LangCompiler wrapper that caches compilation results.
  *
  * Provides transparent caching of compilation results to avoid redundant
  * parsing, type checking, and IR generation when the same source is
  * compiled multiple times.
  *
  * Cache invalidation occurs when:
  * - Source code changes (different sourceHash)
  * - Function registry changes (different registryHash)
  * - TTL expires (configurable)
  *
  * @param underlying the underlying compiler to delegate to on cache miss
  * @param cache the compilation cache
  */
class CachingLangCompiler(
    underlying: LangCompiler,
    cache: CompilationCache
) extends LangCompiler {

  def functionRegistry: FunctionRegistry = underlying.functionRegistry

  def compile(source: String, dagName: String): Either[List[CompileError], CompileResult] = {
    val sourceHash   = source.hashCode
    val registryHash = functionRegistry.all.hashCode

    // Note: Using unsafeRunSync() because LangCompiler.compile returns Either,
    // not IO. For a fully IO-based interface, the trait signature would need
    // to change to: def compile(...): IO[Either[...]]
    cache.get(dagName, sourceHash, registryHash).unsafeRunSync() match {
      case Some(cached) =>
        // Cache hit - return cached result
        Right(cached)
      case None =>
        // Cache miss - compile and cache the result
        val result = underlying.compile(source, dagName)
        result.foreach { r =>
          cache.put(dagName, sourceHash, registryHash, r).unsafeRunSync()
        }
        result
    }
  }

  def compileToIR(source: String, dagName: String): Either[List[CompileError], IRProgram] =
    // Delegate to underlying compiler (no caching for IR - it's used for visualization)
    underlying.compileToIR(source, dagName)

  /** Get cache statistics */
  def cacheStats: CacheStats =
    cache.stats.unsafeRunSync()

  /** Invalidate a specific cached compilation */
  def invalidate(dagName: String): Unit =
    cache.invalidate(dagName).unsafeRunSync()

  /** Invalidate all cached compilations */
  def invalidateAll(): Unit =
    cache.invalidateAll().unsafeRunSync()
}

object CachingLangCompiler {

  /** Create a CachingLangCompiler wrapping the given compiler */
  def apply(underlying: LangCompiler, cache: CompilationCache): CachingLangCompiler =
    new CachingLangCompiler(underlying, cache)

  /** Create a CachingLangCompiler with default cache configuration */
  def withDefaults(underlying: LangCompiler): CachingLangCompiler = {
    val cache = CompilationCache.createUnsafe()
    new CachingLangCompiler(underlying, cache)
  }

  /** Create a CachingLangCompiler with custom cache configuration */
  def withConfig(underlying: LangCompiler, config: CompilationCache.Config): CachingLangCompiler = {
    val cache = CompilationCache.createUnsafe(config)
    new CachingLangCompiler(underlying, cache)
  }
}
