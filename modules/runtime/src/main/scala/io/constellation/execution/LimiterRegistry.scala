package io.constellation.execution

import cats.effect.{IO, Ref}
import cats.implicits._

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

/** Registry for managing rate limiters and concurrency limiters.
  *
  * Limiters are shared per-name (typically module name) to ensure
  * consistent rate limiting across all calls to the same module.
  *
  * ==Usage==
  *
  * {{{
  * val registry = LimiterRegistry.create.unsafeRunSync()
  *
  * // Get or create rate limiter for a module
  * val rateLimiter = registry.getRateLimiter("MyModule", RateLimit.perSecond(10))
  *
  * // Get or create concurrency limiter
  * val concurrencyLimiter = registry.getConcurrencyLimiter("MyModule", 5)
  *
  * // Execute with both limiters
  * for {
  *   rl <- rateLimiter
  *   cl <- concurrencyLimiter
  *   result <- rl.withRateLimit(cl.withPermit(myOperation))
  * } yield result
  * }}}
  */
class LimiterRegistry private (
    rateLimitersRef: Ref[IO, Map[String, TokenBucketRateLimiter]],
    concurrencyLimitersRef: Ref[IO, Map[String, ConcurrencyLimiter]]
) {

  /** Get or create a rate limiter for the given name.
    *
    * If a limiter already exists for this name, it is returned.
    * Otherwise, a new limiter is created with the specified rate.
    *
    * Note: If a limiter exists but with a different rate, the existing
    * limiter is returned (first-registered rate wins).
    *
    * @param name The limiter name (typically module name)
    * @param rate The rate limit to apply
    * @return The rate limiter for this name
    */
  def getRateLimiter(name: String, rate: RateLimit): IO[TokenBucketRateLimiter] = {
    rateLimitersRef.get.flatMap { limiters =>
      limiters.get(name) match {
        case Some(limiter) => IO.pure(limiter)
        case None =>
          TokenBucketRateLimiter(rate).flatMap { newLimiter =>
            rateLimitersRef.modify { current =>
              current.get(name) match {
                case Some(existing) => (current, existing)
                case None => (current + (name -> newLimiter), newLimiter)
              }
            }
          }
      }
    }
  }

  /** Get or create a concurrency limiter for the given name.
    *
    * If a limiter already exists for this name, it is returned.
    * Otherwise, a new limiter is created with the specified max concurrency.
    *
    * @param name The limiter name (typically module name)
    * @param maxConcurrent Maximum concurrent executions
    * @return The concurrency limiter for this name
    */
  def getConcurrencyLimiter(name: String, maxConcurrent: Int): IO[ConcurrencyLimiter] = {
    concurrencyLimitersRef.get.flatMap { limiters =>
      limiters.get(name) match {
        case Some(limiter) => IO.pure(limiter)
        case None =>
          ConcurrencyLimiter(maxConcurrent).flatMap { newLimiter =>
            concurrencyLimitersRef.modify { current =>
              current.get(name) match {
                case Some(existing) => (current, existing)
                case None => (current + (name -> newLimiter), newLimiter)
              }
            }
          }
      }
    }
  }

  /** Check if a rate limiter exists for the given name. */
  def hasRateLimiter(name: String): IO[Boolean] =
    rateLimitersRef.get.map(_.contains(name))

  /** Check if a concurrency limiter exists for the given name. */
  def hasConcurrencyLimiter(name: String): IO[Boolean] =
    concurrencyLimitersRef.get.map(_.contains(name))

  /** List all registered rate limiter names. */
  def listRateLimiters: IO[List[String]] =
    rateLimitersRef.get.map(_.keys.toList.sorted)

  /** List all registered concurrency limiter names. */
  def listConcurrencyLimiters: IO[List[String]] =
    concurrencyLimitersRef.get.map(_.keys.toList.sorted)

  /** Get statistics for all rate limiters. */
  def allRateLimiterStats: IO[Map[String, RateLimiterStats]] = {
    for {
      limiters <- rateLimitersRef.get
      stats <- limiters.toList.traverse { case (name, limiter) =>
        limiter.stats.map(name -> _)
      }
    } yield stats.toMap
  }

  /** Get statistics for all concurrency limiters. */
  def allConcurrencyStats: IO[Map[String, ConcurrencyStats]] = {
    for {
      limiters <- concurrencyLimitersRef.get
      stats <- limiters.toList.traverse { case (name, limiter) =>
        limiter.stats.map(name -> _)
      }
    } yield stats.toMap
  }

  /** Remove a rate limiter by name.
    *
    * @return true if a limiter was removed, false if not found
    */
  def removeRateLimiter(name: String): IO[Boolean] = {
    rateLimitersRef.modify { limiters =>
      val exists = limiters.contains(name)
      (limiters - name, exists)
    }
  }

  /** Remove a concurrency limiter by name.
    *
    * @return true if a limiter was removed, false if not found
    */
  def removeConcurrencyLimiter(name: String): IO[Boolean] = {
    concurrencyLimitersRef.modify { limiters =>
      val exists = limiters.contains(name)
      (limiters - name, exists)
    }
  }

  /** Clear all limiters.
    *
    * Use with caution - this will reset all rate limiting state.
    */
  def clear: IO[Unit] = {
    rateLimitersRef.set(Map.empty) >> concurrencyLimitersRef.set(Map.empty)
  }
}

object LimiterRegistry {

  /** Create an empty limiter registry. */
  def create: IO[LimiterRegistry] = {
    for {
      rateLimitersRef <- Ref.of[IO, Map[String, TokenBucketRateLimiter]](Map.empty)
      concurrencyLimitersRef <- Ref.of[IO, Map[String, ConcurrencyLimiter]](Map.empty)
    } yield new LimiterRegistry(rateLimitersRef, concurrencyLimitersRef)
  }
}

/** Combined rate control options.
  *
  * @param throttle Rate limit to apply (operations per time period)
  * @param concurrency Maximum concurrent executions
  */
final case class RateControlOptions(
    throttle: Option[RateLimit] = None,
    concurrency: Option[Int] = None
)

object RateControlOptions {
  def withThrottle(rate: RateLimit): RateControlOptions =
    RateControlOptions(throttle = Some(rate))

  def withConcurrency(max: Int): RateControlOptions =
    RateControlOptions(concurrency = Some(max))

  def apply(throttle: RateLimit, concurrency: Int): RateControlOptions =
    RateControlOptions(Some(throttle), Some(concurrency))
}

/** Extension methods for executing with rate control. */
object RateControlExecutor {

  /** Execute an operation with rate control.
    *
    * Applies throttle (rate limiting) and concurrency limiting in order:
    * 1. Wait for rate limit permit
    * 2. Wait for concurrency permit
    * 3. Execute operation
    *
    * @param operation The operation to execute
    * @param moduleName Name for limiter lookup
    * @param options Rate control options
    * @param registry The limiter registry
    * @return The result of the operation
    */
  def executeWithRateControl[A](
      operation: IO[A],
      moduleName: String,
      options: RateControlOptions,
      registry: LimiterRegistry
  ): IO[A] = {
    // Build execution pipeline
    val withConcurrency: IO[A] = options.concurrency match {
      case Some(max) =>
        registry.getConcurrencyLimiter(moduleName, max).flatMap(_.withPermit(operation))
      case None =>
        operation
    }

    val withThrottle: IO[A] = options.throttle match {
      case Some(rate) =>
        registry.getRateLimiter(moduleName, rate).flatMap(_.withRateLimit(withConcurrency))
      case None =>
        withConcurrency
    }

    withThrottle
  }
}
