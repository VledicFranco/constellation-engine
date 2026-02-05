package io.constellation.execution

import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.duration.*

import cats.effect.{IO, Ref}

/** Rate limit specification.
  *
  * @param count
  *   Maximum number of operations allowed
  * @param perDuration
  *   Time window for the rate limit
  */
final case class RateLimit(count: Int, perDuration: FiniteDuration) {
  require(count > 0, "Rate limit count must be positive")
  require(perDuration > Duration.Zero, "Rate limit duration must be positive")

  /** Tokens replenished per millisecond. */
  def tokensPerMs: Double = count.toDouble / perDuration.toMillis

  override def toString: String = s"$count/${perDuration.toCoarsest}"
}

object RateLimit {

  /** Create a rate limit of N operations per second. */
  def perSecond(n: Int): RateLimit = RateLimit(n, 1.second)

  /** Create a rate limit of N operations per minute. */
  def perMinute(n: Int): RateLimit = RateLimit(n, 1.minute)

  /** Create a rate limit of N operations per hour. */
  def perHour(n: Int): RateLimit = RateLimit(n, 1.hour)
}

/** Token bucket rate limiter.
  *
  * Implements the token bucket algorithm for smooth rate limiting:
  *   - Tokens are added at a fixed rate (rate.count / rate.perDuration)
  *   - Each operation consumes one token
  *   - If no tokens available, caller waits until token is available
  *
  * ==Usage==
  *
  * {{{
  * val limiter = TokenBucketRateLimiter(RateLimit.perSecond(10))
  *
  * // Acquire before executing rate-limited operation
  * limiter.acquire >> myOperation
  *
  * // Or use withRateLimit for automatic handling
  * limiter.withRateLimit(myOperation)
  * }}}
  *
  * ==Thread Safety==
  *
  * This implementation is thread-safe and suitable for concurrent access. Multiple fibers can
  * safely call acquire concurrently.
  */
class TokenBucketRateLimiter private (
    rate: RateLimit,
    tokensRef: Ref[IO, Double],
    lastRefillRef: Ref[IO, Long]
) {

  private val maxTokens: Double   = rate.count.toDouble
  private val tokensPerMs: Double = rate.tokensPerMs

  /** Acquire a token, waiting if necessary.
    *
    * This method will:
    *   1. Refill tokens based on elapsed time 2. If tokens available, consume one and return
    *      immediately 3. If no tokens, wait for the next token and retry
    *
    * @return
    *   IO that completes when a token is acquired
    */
  def acquire: IO[Unit] =
    for {
      now      <- IO.realTime.map(_.toMillis)
      _        <- refillTokens(now)
      acquired <- tryAcquire
      _        <- if acquired then IO.unit else waitAndRetry
    } yield ()

  /** Try to acquire a token without waiting.
    *
    * @return
    *   true if token was acquired, false if no tokens available
    */
  def tryAcquire: IO[Boolean] =
    tokensRef.modify { tokens =>
      if tokens >= 1.0 then {
        (tokens - 1.0, true)
      } else {
        (tokens, false)
      }
    }

  /** Execute an operation with rate limiting.
    *
    * Acquires a token before executing, ensuring the operation respects the rate limit.
    *
    * @param operation
    *   The operation to rate limit
    * @return
    *   The result of the operation
    */
  def withRateLimit[A](operation: IO[A]): IO[A] = acquire >> operation

  /** Get current number of available tokens.
    *
    * Useful for monitoring and debugging.
    */
  def availableTokens: IO[Double] =
    for {
      now    <- IO.realTime.map(_.toMillis)
      _      <- refillTokens(now)
      tokens <- tokensRef.get
    } yield tokens

  /** Get rate limiter statistics. */
  def stats: IO[RateLimiterStats] =
    for {
      tokens <- availableTokens
    } yield RateLimiterStats(
      availableTokens = tokens,
      maxTokens = maxTokens,
      rate = rate
    )

  private def refillTokens(now: Long): IO[Unit] =
    lastRefillRef
      .modify { lastRefill =>
        val elapsed = now - lastRefill
        if elapsed > 0 then {
          val newTokens = elapsed * tokensPerMs
          (now, newTokens)
        } else {
          (lastRefill, 0.0)
        }
      }
      .flatMap { newTokens =>
        if newTokens > 0 then {
          tokensRef.update(tokens => math.min(maxTokens, tokens + newTokens))
        } else {
          IO.unit
        }
      }

  private def waitAndRetry: IO[Unit] = {
    // Wait for approximately one token interval
    val waitTime = (1.0 / tokensPerMs).toLong.max(1L).millis
    IO.sleep(waitTime) >> acquire
  }
}

object TokenBucketRateLimiter {

  /** Create a new rate limiter.
    *
    * @param rate
    *   The rate limit to enforce
    * @return
    *   A new token bucket rate limiter
    */
  def apply(rate: RateLimit): IO[TokenBucketRateLimiter] =
    for {
      tokensRef     <- Ref.of[IO, Double](rate.count.toDouble)
      now           <- IO.realTime.map(_.toMillis)
      lastRefillRef <- Ref.of[IO, Long](now)
    } yield new TokenBucketRateLimiter(rate, tokensRef, lastRefillRef)

  /** Create a rate limiter with initial token count.
    *
    * Useful for testing or when you want to start with fewer tokens.
    *
    * @param rate
    *   The rate limit to enforce
    * @param initialTokens
    *   Initial number of tokens (default: full bucket)
    */
  def withInitialTokens(rate: RateLimit, initialTokens: Double): IO[TokenBucketRateLimiter] =
    for {
      tokensRef     <- Ref.of[IO, Double](initialTokens.min(rate.count.toDouble))
      now           <- IO.realTime.map(_.toMillis)
      lastRefillRef <- Ref.of[IO, Long](now)
    } yield new TokenBucketRateLimiter(rate, tokensRef, lastRefillRef)
}

/** Statistics for a rate limiter. */
final case class RateLimiterStats(
    availableTokens: Double,
    maxTokens: Double,
    rate: RateLimit
) {

  /** Current fill percentage (0.0 to 1.0). */
  def fillRatio: Double = availableTokens / maxTokens

  override def toString: String =
    f"RateLimiterStats(tokens=$availableTokens%.1f/$maxTokens%.0f, rate=$rate)"
}
