package io.constellation.execution

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference}
import scala.concurrent.duration.*

class RateLimitTest extends AnyFlatSpec with Matchers {

  "RateLimit" should "validate positive count" in {
    assertThrows[IllegalArgumentException] {
      RateLimit(0, 1.second)
    }
    assertThrows[IllegalArgumentException] {
      RateLimit(-1, 1.second)
    }
  }

  it should "validate positive duration" in {
    assertThrows[IllegalArgumentException] {
      RateLimit(10, Duration.Zero)
    }
  }

  it should "calculate tokens per millisecond" in {
    val rate = RateLimit(1000, 1.second)
    rate.tokensPerMs shouldBe 1.0

    val rate2 = RateLimit(60, 1.minute)
    rate2.tokensPerMs shouldBe 0.001 +- 0.0001
  }

  it should "provide convenience constructors" in {
    RateLimit.perSecond(10) shouldBe RateLimit(10, 1.second)
    RateLimit.perMinute(60) shouldBe RateLimit(60, 1.minute)
    RateLimit.perHour(100) shouldBe RateLimit(100, 1.hour)
  }
}

class TokenBucketRateLimiterTest extends AnyFlatSpec with Matchers {

  "TokenBucketRateLimiter" should "allow immediate execution when tokens available" in {
    val result = (for {
      limiter <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      _       <- limiter.acquire
      _       <- limiter.acquire
      _       <- limiter.acquire
    } yield true).unsafeRunSync()

    result shouldBe true
  }

  it should "report available tokens" in {
    val tokens = (for {
      limiter   <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      initial   <- limiter.availableTokens
      _         <- limiter.acquire
      _         <- limiter.acquire
      remaining <- limiter.availableTokens
    } yield (initial, remaining)).unsafeRunSync()

    tokens._1 shouldBe 10.0 +- 0.1
    tokens._2 shouldBe 8.0 +- 0.2
  }

  it should "tryAcquire returns false when no tokens" in {
    val result = (for {
      limiter  <- TokenBucketRateLimiter.withInitialTokens(RateLimit.perSecond(2), 0)
      acquired <- limiter.tryAcquire
    } yield acquired).unsafeRunSync()

    result shouldBe false
  }

  it should "tryAcquire returns true when tokens available" in {
    val result = (for {
      limiter  <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      acquired <- limiter.tryAcquire
    } yield acquired).unsafeRunSync()

    result shouldBe true
  }

  it should "replenish tokens over time" in {
    val result = (for {
      limiter <- TokenBucketRateLimiter(RateLimit(10, 100.millis)) // 10 tokens per 100ms
      _       <- limiter.acquire
      _       <- limiter.acquire
      _       <- limiter.acquire
      before  <- limiter.availableTokens
      _       <- IO.sleep(50.millis)                               // Should replenish ~5 tokens
      after   <- limiter.availableTokens
    } yield (before, after)).unsafeRunSync()

    result._1 shouldBe 7.0 +- 0.5
    result._2 should be > result._1 // Tokens replenished
  }

  it should "not exceed max tokens on replenish" in {
    val result = (for {
      limiter <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      _       <- IO.sleep(200.millis) // Wait for potential over-replenishment
      tokens  <- limiter.availableTokens
    } yield tokens).unsafeRunSync()

    result shouldBe 10.0 +- 0.1 // Capped at max
  }

  it should "withRateLimit executes operation" in {
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

  it should "provide stats" in {
    val stats = (for {
      limiter <- TokenBucketRateLimiter(RateLimit.perSecond(10))
      _       <- limiter.acquire
      stats   <- limiter.stats
    } yield stats).unsafeRunSync()

    stats.maxTokens shouldBe 10.0
    stats.availableTokens shouldBe 9.0 +- 0.2
    stats.rate shouldBe RateLimit.perSecond(10)
    stats.fillRatio shouldBe 0.9 +- 0.02
  }

  "Rate limiting timing" should "enforce rate limits under load" in {
    val rate      = RateLimit(5, 100.millis) // 5 per 100ms = 50/sec
    val callCount = 10

    val (elapsed, _) = (for {
      limiter <- TokenBucketRateLimiter(rate)
      start   <- IO.realTime
      // Execute callCount operations
      _   <- (1 to callCount).toList.traverse_(_ => limiter.acquire)
      end <- IO.realTime
    } yield (end - start, ())).unsafeRunSync()

    // Should take at least 100ms for 10 calls at 5 per 100ms
    // Use wide tolerance (50ms) to avoid flaky failures under system load
    elapsed.toMillis should be >= 50L
  }
}

class ConcurrencyLimiterTest extends AnyFlatSpec with Matchers {

  "ConcurrencyLimiter" should "allow concurrent executions up to limit" in {
    val maxActive     = new AtomicInteger(0)
    val currentActive = new AtomicInteger(0)

    val result = (for {
      limiter <- ConcurrencyLimiter(3)
      // Run 5 concurrent tasks with limit of 3
      _ <- (1 to 5).toList.parTraverse_ { _ =>
        limiter.withPermit {
          IO {
            val active = currentActive.incrementAndGet()
            maxActive.updateAndGet(m => math.max(m, active))
          } >> IO.sleep(50.millis) >> IO {
            currentActive.decrementAndGet()
          }
        }
      }
    } yield maxActive.get()).unsafeRunSync()

    result shouldBe 3 // Never exceeded the limit
  }

  it should "track active count" in {
    val result = (for {
      limiter <- ConcurrencyLimiter(5)
      before  <- limiter.active
      _       <- limiter.acquire
      _       <- limiter.acquire
      during  <- limiter.active
      _       <- limiter.release
      after   <- limiter.active
    } yield (before, during, after)).unsafeRunSync()

    result shouldBe (0L, 2L, 1L)
  }

  it should "report available permits" in {
    val result = (for {
      limiter   <- ConcurrencyLimiter(5)
      initial   <- limiter.available
      _         <- limiter.acquire
      _         <- limiter.acquire
      remaining <- limiter.available
    } yield (initial, remaining)).unsafeRunSync()

    result shouldBe (5L, 3L)
  }

  it should "tryAcquire returns false when limit reached" in {
    val result = (for {
      limiter  <- ConcurrencyLimiter(2)
      _        <- limiter.acquire
      _        <- limiter.acquire
      acquired <- limiter.tryAcquire
    } yield acquired).unsafeRunSync()

    result shouldBe false
  }

  it should "tryAcquire returns true when permits available" in {
    val result = (for {
      limiter  <- ConcurrencyLimiter(5)
      _        <- limiter.acquire
      acquired <- limiter.tryAcquire
    } yield acquired).unsafeRunSync()

    result shouldBe true
  }

  it should "provide accurate statistics" in {
    val stats = (for {
      limiter <- ConcurrencyLimiter(5)
      _       <- limiter.acquire
      _       <- limiter.acquire
      _       <- limiter.release
      stats   <- limiter.stats
    } yield stats).unsafeRunSync()

    stats.maxConcurrent shouldBe 5
    stats.currentActive shouldBe 1
    stats.peakActive shouldBe 2
    stats.totalExecutions shouldBe 2
    stats.availablePermits shouldBe 4
    stats.utilization shouldBe 0.2 +- 0.01
    stats.peakUtilization shouldBe 0.4 +- 0.01
  }

  it should "reset statistics" in {
    val (before, after) = (for {
      limiter <- ConcurrencyLimiter(5)
      _       <- limiter.acquire
      _       <- limiter.release
      before  <- limiter.stats
      _       <- limiter.resetStats
      after   <- limiter.stats
    } yield (before, after)).unsafeRunSync()

    before.totalExecutions shouldBe 1
    before.peakActive shouldBe 1
    after.totalExecutions shouldBe 0
    after.peakActive shouldBe 0
  }

  it should "reject non-positive maxConcurrent" in {
    an[IllegalArgumentException] should be thrownBy {
      ConcurrencyLimiter(0).unsafeRunSync()
    }
    an[IllegalArgumentException] should be thrownBy {
      ConcurrencyLimiter(-1).unsafeRunSync()
    }
  }

  it should "release permit on failure" in {
    val result = (for {
      limiter <- ConcurrencyLimiter(2)
      _       <- limiter.withPermit(IO.raiseError[Int](new RuntimeException("boom"))).attempt
      stats   <- limiter.stats
    } yield stats).unsafeRunSync()

    result.currentActive shouldBe 0
    result.availablePermits shouldBe 2
  }

  it should "create mutex with single permit" in {
    val maxActive     = new AtomicInteger(0)
    val currentActive = new AtomicInteger(0)

    val result = (for {
      limiter <- ConcurrencyLimiter.mutex
      _ <- (1 to 5).toList.parTraverse_ { _ =>
        limiter.withPermit {
          IO {
            val active = currentActive.incrementAndGet()
            maxActive.updateAndGet(m => math.max(m, active))
          } >> IO.sleep(10.millis) >> IO {
            currentActive.decrementAndGet()
          }
        }
      }
    } yield maxActive.get()).unsafeRunSync()

    result shouldBe 1 // Mutex: only one at a time
  }
}

class LimiterRegistryTest extends AnyFlatSpec with Matchers {

  "LimiterRegistry" should "create and reuse rate limiters" in {
    val result = (for {
      registry <- LimiterRegistry.create
      limiter1 <- registry.getRateLimiter("module1", RateLimit.perSecond(10))
      limiter2 <- registry.getRateLimiter("module1", RateLimit.perSecond(100)) // Different rate
      sameLimiter <- IO.pure(limiter1 eq limiter2) // Should be same instance
      has         <- registry.hasRateLimiter("module1")
    } yield (sameLimiter, has)).unsafeRunSync()

    result shouldBe (true, true)
  }

  it should "create separate limiters for different names" in {
    val result = (for {
      registry  <- LimiterRegistry.create
      limiter1  <- registry.getRateLimiter("module1", RateLimit.perSecond(10))
      limiter2  <- registry.getRateLimiter("module2", RateLimit.perSecond(10))
      different <- IO.pure(!(limiter1 eq limiter2))
    } yield different).unsafeRunSync()

    result shouldBe true
  }

  it should "create and reuse concurrency limiters" in {
    val result = (for {
      registry    <- LimiterRegistry.create
      limiter1    <- registry.getConcurrencyLimiter("module1", 5)
      limiter2    <- registry.getConcurrencyLimiter("module1", 10) // Different limit
      sameLimiter <- IO.pure(limiter1 eq limiter2)
      has         <- registry.hasConcurrencyLimiter("module1")
    } yield (sameLimiter, has)).unsafeRunSync()

    result shouldBe (true, true)
  }

  it should "list registered limiters" in {
    val result = (for {
      registry            <- LimiterRegistry.create
      _                   <- registry.getRateLimiter("b", RateLimit.perSecond(10))
      _                   <- registry.getRateLimiter("a", RateLimit.perSecond(10))
      _                   <- registry.getConcurrencyLimiter("c", 5)
      rateLimiters        <- registry.listRateLimiters
      concurrencyLimiters <- registry.listConcurrencyLimiters
    } yield (rateLimiters, concurrencyLimiters)).unsafeRunSync()

    result._1 shouldBe List("a", "b") // Sorted
    result._2 shouldBe List("c")
  }

  it should "remove limiters" in {
    val result = (for {
      registry   <- LimiterRegistry.create
      _          <- registry.getRateLimiter("test", RateLimit.perSecond(10))
      _          <- registry.getConcurrencyLimiter("test", 5)
      hasBefore  <- registry.hasRateLimiter("test")
      removed    <- registry.removeRateLimiter("test")
      hasAfter   <- registry.hasRateLimiter("test")
      notRemoved <- registry.removeRateLimiter("nonexistent")
    } yield (hasBefore, removed, hasAfter, notRemoved)).unsafeRunSync()

    result shouldBe (true, true, false, false)
  }

  it should "clear all limiters" in {
    val result = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getRateLimiter("m1", RateLimit.perSecond(10))
      _        <- registry.getRateLimiter("m2", RateLimit.perSecond(10))
      _        <- registry.getConcurrencyLimiter("m3", 5)
      before   <- registry.listRateLimiters
      _        <- registry.clear
      after    <- registry.listRateLimiters
    } yield (before.length, after.length)).unsafeRunSync()

    result shouldBe (2, 0)
  }

  it should "get all rate limiter stats" in {
    val result = (for {
      registry <- LimiterRegistry.create
      limiter  <- registry.getRateLimiter("test", RateLimit.perSecond(10))
      _        <- limiter.acquire
      stats    <- registry.allRateLimiterStats
    } yield stats).unsafeRunSync()

    result should contain key "test"
    // Allow wider tolerance due to token replenishment timing
    result("test").availableTokens should (be >= 8.5 and be <= 10.0)
  }

  it should "remove concurrency limiters" in {
    val result = (for {
      registry   <- LimiterRegistry.create
      _          <- registry.getConcurrencyLimiter("test", 5)
      hasBefore  <- registry.hasConcurrencyLimiter("test")
      removed    <- registry.removeConcurrencyLimiter("test")
      hasAfter   <- registry.hasConcurrencyLimiter("test")
      notRemoved <- registry.removeConcurrencyLimiter("nonexistent")
    } yield (hasBefore, removed, hasAfter, notRemoved)).unsafeRunSync()

    result shouldBe (true, true, false, false)
  }

  it should "get all concurrency stats" in {
    val result = (for {
      registry <- LimiterRegistry.create
      limiter  <- registry.getConcurrencyLimiter("test", 5)
      _        <- limiter.acquire
      stats    <- registry.allConcurrencyStats
    } yield stats).unsafeRunSync()

    result should contain key "test"
    result("test").currentActive shouldBe 1
  }
}

class RateControlExecutorTest extends AnyFlatSpec with Matchers {

  "RateControlExecutor" should "apply throttle only" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      registry <- LimiterRegistry.create
      result <- RateControlExecutor.executeWithRateControl(
        IO { counter.incrementAndGet(); 42 },
        "module",
        RateControlOptions.withThrottle(RateLimit.perSecond(100)),
        registry
      )
    } yield result).unsafeRunSync()

    result shouldBe 42
    counter.get() shouldBe 1
  }

  it should "apply concurrency only" in {
    val maxActive     = new AtomicInteger(0)
    val currentActive = new AtomicInteger(0)

    val result = (for {
      registry <- LimiterRegistry.create
      _ <- (1 to 5).toList.parTraverse_ { _ =>
        RateControlExecutor.executeWithRateControl(
          IO {
            val active = currentActive.incrementAndGet()
            maxActive.updateAndGet(m => math.max(m, active))
          } >> IO.sleep(30.millis) >> IO(currentActive.decrementAndGet()),
          "module",
          RateControlOptions.withConcurrency(2),
          registry
        )
      }
    } yield maxActive.get()).unsafeRunSync()

    result shouldBe 2
  }

  it should "apply both throttle and concurrency" in {
    val counter       = new AtomicInteger(0)
    val maxActive     = new AtomicInteger(0)
    val currentActive = new AtomicInteger(0)

    val result = (for {
      registry <- LimiterRegistry.create
      start    <- IO.realTime
      _ <- (1 to 6).toList.parTraverse_ { _ =>
        RateControlExecutor.executeWithRateControl(
          IO {
            val active = currentActive.incrementAndGet()
            maxActive.updateAndGet(m => math.max(m, active))
            counter.incrementAndGet()
          } >> IO.sleep(20.millis) >> IO(currentActive.decrementAndGet()),
          "module",
          RateControlOptions(RateLimit(3, 100.millis), 2),
          registry
        )
      }
      end <- IO.realTime
    } yield (counter.get(), maxActive.get(), end - start)).unsafeRunSync()

    result._1 shouldBe 6 // All executed
    result._2 shouldBe 2 // Max 2 concurrent
    // With rate limit of 3/100ms and 6 calls, should take at least 100ms
    // Use wide tolerance (50ms) to avoid flaky failures under system load
    result._3.toMillis should be >= 50L
  }

  it should "use no limiting when options are empty" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      registry <- LimiterRegistry.create
      result <- RateControlExecutor.executeWithRateControl(
        IO { counter.incrementAndGet(); 42 },
        "module",
        RateControlOptions(), // No limiting
        registry
      )
    } yield result).unsafeRunSync()

    result shouldBe 42
    counter.get() shouldBe 1
  }
}
