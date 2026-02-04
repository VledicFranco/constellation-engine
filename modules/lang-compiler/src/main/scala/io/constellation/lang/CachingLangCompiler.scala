package io.constellation.lang

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.constellation.ContentHash
import io.constellation.cache.CacheStats
import io.constellation.lang.ast.CompileError
import io.constellation.lang.compiler.{CompilationOutput, IRPipeline}
import io.constellation.lang.semantic.FunctionRegistry

import java.util.concurrent.ConcurrentHashMap

/** A LangCompiler wrapper that caches compilation results.
  *
  * Provides transparent caching of compilation results to avoid redundant parsing, type checking,
  * and IR generation when the same source is compiled multiple times.
  *
  * Cache invalidation occurs when:
  *   - Source code changes (different sourceHash)
  *   - Function registry changes (different registryHash)
  *   - TTL expires (configurable)
  *
  * @param underlying
  *   the underlying compiler to delegate to on cache miss
  * @param cache
  *   the compilation cache
  */
class CachingLangCompiler(
    underlying: LangCompiler,
    cache: CompilationCache
) extends LangCompiler {

  // Simple in-memory cache for IR results (separate from CompilationOutput cache)
  // Key: (sourceHash, registryHash), Value: IRPipeline
  private val irCache = new ConcurrentHashMap[(String, String), IRPipeline]()

  def functionRegistry: FunctionRegistry = underlying.functionRegistry

  def compile(source: String, dagName: String): Either[List[CompileError], CompilationOutput] =
    compileIO(source, dagName).unsafeRunSync()

  override def compileIO(
      source: String,
      dagName: String
  ): IO[Either[List[CompileError], CompilationOutput]] = {
    val sourceHash = ContentHash.computeSHA256(source.getBytes("UTF-8"))
    val registryHash = ContentHash.computeSHA256(
      functionRegistry.all.map(_.toString).sorted.mkString(",").getBytes("UTF-8")
    )

    cache.get(dagName, sourceHash, registryHash).flatMap {
      case Some(cached) =>
        // Cache hit
        IO.pure(Right(cached))
      case None =>
        // Cache miss - compile and cache the result
        IO {
          underlying.compile(source, dagName)
        }.flatMap { result =>
          result match {
            case Right(r) =>
              cache.put(dagName, sourceHash, registryHash, r).as(result)
            case Left(_) =>
              IO.pure(result)
          }
        }
    }
  }

  def compileToIR(source: String, dagName: String): Either[List[CompileError], IRPipeline] = {
    val sourceHash = ContentHash.computeSHA256(source.getBytes("UTF-8"))
    val registryHash = ContentHash.computeSHA256(
      functionRegistry.all.map(_.toString).sorted.mkString(",").getBytes("UTF-8")
    )
    val cacheKey = (sourceHash, registryHash)

    // Check IR cache first
    Option(irCache.get(cacheKey)) match {
      case Some(cachedIR) =>
        // IR cache hit
        Right(cachedIR)
      case None =>
        // Cache miss - compile and cache the IR
        val result = underlying.compileToIR(source, dagName)
        result.foreach { ir =>
          // Limit IR cache size to prevent unbounded growth
          if irCache.size() > 100 then {
            irCache.clear() // Simple eviction: clear all when full
          }
          irCache.put(cacheKey, ir)
        }
        result
    }
  }

  /** Get cache statistics (IO-based, preferred). */
  def cacheStatsIO: IO[CacheStats] = cache.stats

  /** Get cache statistics (blocking, for backward compatibility). */
  def cacheStats: CacheStats =
    cache.stats.unsafeRunSync()

  /** Invalidate a specific cached compilation (IO-based). */
  def invalidateIO(dagName: String): IO[Unit] =
    cache.invalidate(dagName)

  /** Invalidate a specific cached compilation (blocking). */
  def invalidate(dagName: String): Unit =
    cache.invalidate(dagName).unsafeRunSync()

  /** Invalidate all cached compilations (IO-based). */
  def invalidateAllIO: IO[Unit] =
    cache.invalidateAll().map(_ => irCache.clear())

  /** Invalidate all cached compilations (blocking). */
  def invalidateAll(): Unit = {
    cache.invalidateAll().unsafeRunSync()
    irCache.clear()
  }
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
