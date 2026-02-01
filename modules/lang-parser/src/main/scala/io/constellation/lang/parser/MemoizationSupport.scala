package io.constellation.lang.parser

import cats.parse.{Parser => P, Parser0}

import scala.collection.mutable

/** Memoization support for the Constellation parser.
  *
  * Provides caching utilities for expensive parsing operations.
  * Uses ThreadLocal storage for thread-safety in concurrent parsing scenarios.
  *
  * Note: cats-parse Parser is sealed and cannot be extended directly.
  * This trait provides memoization at the source-string level for
  * repeated parsing of the same expressions.
  */
trait MemoizationSupport {

  /** Thread-local cache for expression parsing results.
    * Key: (offset, parserId)
    * Value: Parsing result
    *
    * Note: Cache is scoped to a single parse invocation (cleared before each parse),
    * so using offset alone is safe - different parses use different cache instances.
    */
  private val expressionCache =
    new ThreadLocal[mutable.Map[(Int, Int), Either[P.Error, Any]]] {
      override def initialValue(): mutable.Map[(Int, Int), Either[P.Error, Any]] =
        mutable.Map.empty
    }

  /** Thread-local hit/miss counters for cache statistics */
  private val cacheHits   = new ThreadLocal[Int] { override def initialValue() = 0 }
  private val cacheMisses = new ThreadLocal[Int] { override def initialValue() = 0 }

  /** Clear the memoization cache.
    * Should be called before each top-level parse to prevent stale entries
    * and unbounded memory growth.
    */
  protected def clearMemoCache(): Unit = {
    expressionCache.get().clear()
    cacheHits.set(0)
    cacheMisses.set(0)
  }

  /** Get cache statistics for debugging/benchmarking.
    * @return (hits, misses)
    */
  protected def getCacheStats: (Int, Int) = {
    (cacheHits.get(), cacheMisses.get())
  }

  /** Cache size for monitoring memory usage */
  protected def getCacheSize: Int = expressionCache.get().size

  /** Check cache for a previously computed result.
    *
    * @param input The input string being parsed (unused - kept for API compatibility)
    * @param offset The current position in the input
    * @param parserId Unique identifier for the parser type
    * @return Cached result if available
    */
  protected def checkCache[A](input: String, offset: Int, parserId: Int): Option[Either[P.Error, A]] = {
    val key    = (offset, parserId)
    val cached = expressionCache.get().get(key)
    cached match {
      case Some(result) =>
        cacheHits.set(cacheHits.get() + 1)
        Some(result.asInstanceOf[Either[P.Error, A]])
      case None =>
        cacheMisses.set(cacheMisses.get() + 1)
        None
    }
  }

  /** Store a parsing result in the cache.
    *
    * @param input The input string being parsed (unused - kept for API compatibility)
    * @param offset The position where parsing started
    * @param parserId Unique identifier for the parser type
    * @param result The parsing result to cache
    */
  protected def cacheResult[A](
      input: String,
      offset: Int,
      parserId: Int,
      result: Either[P.Error, A]
  ): Unit = {
    val key = (offset, parserId)
    expressionCache.get().put(key, result)
  }

  /** Wrapper for creating cached parser execution.
    * Creates a function that checks cache before parsing and caches results.
    *
    * @param parserId Unique identifier for this parser
    * @param parser The underlying parser to memoize
    * @return A function that performs cached parsing
    *
    * Note: Uses offset 0 as key since this caches full-input parses.
    * Cache is cleared before each parse, so offset 0 uniquely identifies the input.
    */
  protected def cachedParse[A](parserId: Int, parser: P[A]): String => Either[P.Error, (String, A)] = {
    input =>
      val key = (0, parserId) // offset 0 for full-input parsing
      expressionCache.get().get(key) match {
        case Some(Right((remaining: String, value))) =>
          cacheHits.set(cacheHits.get() + 1)
          Right((remaining, value.asInstanceOf[A]))
        case Some(Left(error: P.Error)) =>
          cacheHits.set(cacheHits.get() + 1)
          Left(error)
        case _ =>
          cacheMisses.set(cacheMisses.get() + 1)
          val result = parser.parse(input)
          expressionCache.get().put(key, result)
          result
      }
  }
}

/** Parser optimization utilities for cats-parse.
  *
  * Note: cats-parse has a sealed Parser type with Parser vs Parser0 distinction.
  * The most effective optimizations are:
  * 1. Use P.oneOf instead of chained | for alternatives
  * 2. Use .backtrack only where needed
  * 3. Order alternatives by likelihood (most common first)
  * 4. Use P.charIn/P.stringIn for simple character-based choices
  */
object ParserOptimizations {
  import cats.parse.{Parser => P}

  /** Create an optimized choice from a list of alternatives.
    * Uses P.oneOf which is more efficient than chained |.
    *
    * @param alternatives List of parser alternatives to try
    * @return Combined parser
    */
  def oneOfOptimized[A](alternatives: List[P[A]]): P[A] =
    P.oneOf(alternatives)

  /** Create an optimized choice with backtracking on all alternatives.
    * Use when any alternative might partially consume input before failing.
    *
    * @param alternatives List of parser alternatives to try
    * @return Combined parser with backtracking
    */
  def oneOfWithBacktrack[A](alternatives: List[P[A]]): P[A] =
    P.oneOf(alternatives.map(_.backtrack))
}
