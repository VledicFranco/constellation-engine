package io.constellation.execution

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import io.constellation.RetrySupport

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.tagobjects.Retryable

class TokenBucketRateLimiterCoverageTest extends AnyFlatSpec with Matchers with RetrySupport {

  // -------------------------------------------------------------------------
  // RateLimit construction and validation
  // -------------------------------------------------------------------------

  "RateLimit" should "construct with valid positive count and duration" in {
    val rate = RateLimit(10, 1.second)
    rate.count shouldBe 10
    rate.perDuration shouldBe 1.second
  }

  it should "reject zero count" in {
    an[IllegalArgumentException] should be thrownBy {
      RateLimit(0, 1.second)
    }
  }

  it should "reject negative count" in {
    an[IllegalArgumentException] should be thrownBy {
      RateLimit(-5, 1.second)
    }
  }

  it should "reject zero duration" in {
    an[IllegalArgumentException] should be thrownBy {
      RateLimit(10, Duration.Zero)
    }
  }

  it should "reject negative duration" in {
    an[IllegalArgumentException] should be thrownBy {
      RateLimit(10, -1.second)
    }
  }

  // -------------------------------------------------------------------------
  // RateLimit.tokensPerMs calculation
  // -------------------------------------------------------------------------

  "RateLimit.tokensPerMs" should "calculate correctly for 1000 per second" in {
    val rate = RateLimit(1000, 1.second)
    rate.tokensPerMs shouldBe 1.0
  }

  it should "calculate correctly for 60 per minute" in {
    val rate = RateLimit(60, 1.minute)
    rate.tokensPerMs shouldBe 0.001 +- 0.0001
  }

  it should "calculate correctly for 10 per 100ms" in {
    val rate = RateLimit(10, 100.millis)
    rate.tokensPerMs shouldBe 0.1
  }

  // -------------------------------------------------------------------------
  // RateLimit convenience constructors
  // -------------------------------------------------------------------------

  "RateLimit companion" should "create perSecond rate limit" in {
    RateLimit.perSecond(10) shouldBe RateLimit(10, 1.second)
  }

  it should "create perMinute rate limit" in {
    RateLimit.perMinute(60) shouldBe RateLimit(60, 1.minute)
  }

  it should "create perHour rate limit" in {
    RateLimit.perHour(100) shouldBe RateLimit(100, 1.hour)
  }

  // -------------------------------------------------------------------------
  // RateLimit.toString
  // -------------------------------------------------------------------------

  "RateLimit.toString" should "format as count/coarseDuration" in {
    val rate = RateLimit(10, 1.second)
    rate.toString shouldBe "10/1 second"
  }

  it should "use coarsest duration unit for minutes" in {
    val rate = RateLimit(60, 1.minute)
    rate.toString shouldBe "60/1 minute"
  }

  // -------------------------------------------------------------------------
  // TokenBucketRateLimiter.apply (creation)
  // -------------------------------------------------------------------------

  "TokenBucketRateLimiter.apply" should "create a limiter with full token bucket" taggedAs Retryable in {
    val tokens = (for {
      limiter <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      tokens  <- limiter.availableTokens
    } yield tokens).unsafeRunSync()

    tokens shouldBe 10.0 +- 0.1
  }

  "TokenBucketRateLimiter.withInitialTokens" should "create limiter with specified initial tokens" taggedAs Retryable in {
    val tokens = (for {
      limiter <- TokenBucketRateLimiter.withInitialTokens(RateLimit.perSecond(10), 3.0)
      tokens  <- limiter.availableTokens
    } yield tokens).unsafeRunSync()

    tokens shouldBe 3.0 +- 0.2
  }

  it should "cap initial tokens at max" taggedAs Retryable in {
    val tokens = (for {
      limiter <- TokenBucketRateLimiter.withInitialTokens(RateLimit.perSecond(5), 100.0)
      tokens  <- limiter.availableTokens
    } yield tokens).unsafeRunSync()

    tokens shouldBe 5.0 +- 0.2
  }

  // -------------------------------------------------------------------------
  // acquire (consumes token)
  // -------------------------------------------------------------------------

  "TokenBucketRateLimiter.acquire" should "consume a token on each call" taggedAs Retryable in {
    val (initial, afterThree) = (for {
      limiter    <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      initial    <- limiter.availableTokens
      _          <- limiter.acquire
      _          <- limiter.acquire
      _          <- limiter.acquire
      afterThree <- limiter.availableTokens
    } yield (initial, afterThree)).unsafeRunSync()

    initial shouldBe 10.0 +- 0.1
    afterThree shouldBe 7.0 +- 0.5
  }

  it should "allow multiple sequential acquires within budget" in {
    val result = (for {
      limiter <- TokenBucketRateLimiter(RateLimit.perSecond(100))
      _       <- limiter.acquire
      _       <- limiter.acquire
      _       <- limiter.acquire
      _       <- limiter.acquire
      _       <- limiter.acquire
    } yield true).unsafeRunSync()

    result shouldBe true
  }

  // -------------------------------------------------------------------------
  // tryAcquire (success and failure cases)
  // -------------------------------------------------------------------------

  "TokenBucketRateLimiter.tryAcquire" should "return true when tokens are available" in {
    val result = (for {
      limiter  <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      acquired <- limiter.tryAcquire
    } yield acquired).unsafeRunSync()

    result shouldBe true
  }

  it should "return false when no tokens are available" in {
    val result = (for {
      limiter  <- TokenBucketRateLimiter.withInitialTokens(RateLimit.perSecond(2), 0.0)
      acquired <- limiter.tryAcquire
    } yield acquired).unsafeRunSync()

    result shouldBe false
  }

  it should "return false after all tokens are consumed" taggedAs Retryable in {
    val result = (for {
      limiter <- TokenBucketRateLimiter.withInitialTokens(RateLimit.perSecond(2), 2.0)
      first   <- limiter.tryAcquire
      second  <- limiter.tryAcquire
      third   <- limiter.tryAcquire // Should fail - no tokens left
    } yield (first, second, third)).unsafeRunSync()

    result._1 shouldBe true
    result._2 shouldBe true
    result._3 shouldBe false
  }

  // -------------------------------------------------------------------------
  // Token refill over time
  // -------------------------------------------------------------------------

  "Token refill" should "replenish tokens after time elapses" taggedAs Retryable in {
    val (before, after) = (for {
      limiter <- TokenBucketRateLimiter(RateLimit(10, 100.millis))
      _       <- limiter.acquire
      _       <- limiter.acquire
      _       <- limiter.acquire
      before  <- limiter.availableTokens
      _       <- IO.sleep(50.millis)
      after   <- limiter.availableTokens
    } yield (before, after)).unsafeRunSync()

    before shouldBe 7.0 +- 0.5
    after should be > before
  }

  it should "not exceed max tokens on refill" taggedAs Retryable in {
    val tokens = (for {
      limiter <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      _       <- IO.sleep(200.millis)
      tokens  <- limiter.availableTokens
    } yield tokens).unsafeRunSync()

    tokens shouldBe 10.0 +- 0.1
  }

  // -------------------------------------------------------------------------
  // withRateLimit
  // -------------------------------------------------------------------------

  "TokenBucketRateLimiter.withRateLimit" should "execute the operation and return its result" in {
    val counter = new AtomicInteger(0)
    val result = (for {
      limiter <- TokenBucketRateLimiter(RateLimit.perSecond(100))
      result <- limiter.withRateLimit(IO {
        counter.incrementAndGet()
        42
      })
    } yield result).unsafeRunSync()

    result shouldBe 42
    counter.get() shouldBe 1
  }

  it should "propagate errors from the operation" in {
    val caught = intercept[RuntimeException] {
      (for {
        limiter <- TokenBucketRateLimiter(RateLimit.perSecond(100))
        _       <- limiter.withRateLimit(IO.raiseError[Int](new RuntimeException("op failed")))
      } yield ()).unsafeRunSync()
    }

    caught.getMessage shouldBe "op failed"
  }

  // -------------------------------------------------------------------------
  // Rate limiting under load
  // -------------------------------------------------------------------------

  "Rate limiting under load" should "enforce rate limits across many calls" taggedAs Retryable in {
    val rate      = RateLimit(5, 100.millis)
    val callCount = 10

    val elapsed = (for {
      limiter <- TokenBucketRateLimiter(rate)
      start   <- IO.realTime
      _       <- (1 to callCount).toList.traverse_(_ => limiter.acquire)
      end     <- IO.realTime
    } yield (end - start).toMillis).unsafeRunSync()

    // 10 calls at 5 per 100ms should take at least ~100ms
    elapsed should be >= 50L
  }

  // -------------------------------------------------------------------------
  // Stats reporting
  // -------------------------------------------------------------------------

  "TokenBucketRateLimiter.stats" should "report correct maxTokens" taggedAs Retryable in {
    val stats = (for {
      limiter <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      stats   <- limiter.stats
    } yield stats).unsafeRunSync()

    stats.maxTokens shouldBe 10.0
  }

  it should "report available tokens after consumption" taggedAs Retryable in {
    val stats = (for {
      limiter <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      _       <- limiter.acquire
      stats   <- limiter.stats
    } yield stats).unsafeRunSync()

    stats.availableTokens shouldBe 9.0 +- 0.2
    stats.rate shouldBe RateLimit.perSecond(10)
  }

  it should "compute fillRatio correctly" taggedAs Retryable in {
    val stats = (for {
      limiter <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      _       <- limiter.acquire
      stats   <- limiter.stats
    } yield stats).unsafeRunSync()

    stats.fillRatio shouldBe 0.9 +- 0.02
  }

  "RateLimiterStats.fillRatio" should "be 1.0 for a full bucket" in {
    val stats = RateLimiterStats(
      availableTokens = 10.0,
      maxTokens = 10.0,
      rate = RateLimit.perSecond(10)
    )
    stats.fillRatio shouldBe 1.0
  }

  it should "be 0.5 for a half-full bucket" in {
    val stats = RateLimiterStats(
      availableTokens = 5.0,
      maxTokens = 10.0,
      rate = RateLimit.perSecond(10)
    )
    stats.fillRatio shouldBe 0.5
  }

  it should "be 0.0 for an empty bucket" in {
    val stats = RateLimiterStats(
      availableTokens = 0.0,
      maxTokens = 10.0,
      rate = RateLimit.perSecond(10)
    )
    stats.fillRatio shouldBe 0.0
  }

  "RateLimiterStats.toString" should "format with available and max tokens" in {
    val stats = RateLimiterStats(
      availableTokens = 8.0,
      maxTokens = 10.0,
      rate = RateLimit.perSecond(10)
    )
    val str = stats.toString
    str should include("8.0")
    str should include("10")
    str should include("10/1 second")
  }
}
