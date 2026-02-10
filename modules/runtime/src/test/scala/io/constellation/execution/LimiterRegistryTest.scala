package io.constellation.execution

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// ---------------------------------------------------------------------------
// LimiterRegistry.create
// ---------------------------------------------------------------------------

class LimiterRegistryCreateTest extends AnyFlatSpec with Matchers {

  "LimiterRegistry.create" should "create an empty registry with no rate limiters" in {
    val names = (for {
      registry <- LimiterRegistry.create
      names    <- registry.listRateLimiters
    } yield names).unsafeRunSync()

    names shouldBe empty
  }

  it should "create an empty registry with no concurrency limiters" in {
    val names = (for {
      registry <- LimiterRegistry.create
      names    <- registry.listConcurrencyLimiters
    } yield names).unsafeRunSync()

    names shouldBe empty
  }
}

// ---------------------------------------------------------------------------
// getRateLimiter
// ---------------------------------------------------------------------------

class LimiterRegistryGetRateLimiterTest extends AnyFlatSpec with Matchers {

  "getRateLimiter" should "create a new rate limiter on first access" in {
    val has = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getRateLimiter("module1", RateLimit.perSecond(10))
      has      <- registry.hasRateLimiter("module1")
    } yield has).unsafeRunSync()

    has shouldBe true
  }

  it should "return the same instance on second call (first-registered-wins)" in {
    val same = (for {
      registry <- LimiterRegistry.create
      limiter1 <- registry.getRateLimiter("module1", RateLimit.perSecond(10))
      limiter2 <- registry.getRateLimiter("module1", RateLimit.perSecond(100))
    } yield limiter1 eq limiter2).unsafeRunSync()

    same shouldBe true
  }

  it should "create separate limiters for different names" in {
    val different = (for {
      registry <- LimiterRegistry.create
      limiter1 <- registry.getRateLimiter("module1", RateLimit.perSecond(10))
      limiter2 <- registry.getRateLimiter("module2", RateLimit.perSecond(10))
    } yield !(limiter1 eq limiter2)).unsafeRunSync()

    different shouldBe true
  }
}

// ---------------------------------------------------------------------------
// getConcurrencyLimiter
// ---------------------------------------------------------------------------

class LimiterRegistryGetConcurrencyLimiterTest extends AnyFlatSpec with Matchers {

  "getConcurrencyLimiter" should "create a new concurrency limiter on first access" in {
    val has = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getConcurrencyLimiter("module1", 5)
      has      <- registry.hasConcurrencyLimiter("module1")
    } yield has).unsafeRunSync()

    has shouldBe true
  }

  it should "return the same instance on second call (first-registered-wins)" in {
    val same = (for {
      registry <- LimiterRegistry.create
      limiter1 <- registry.getConcurrencyLimiter("module1", 5)
      limiter2 <- registry.getConcurrencyLimiter("module1", 10)
    } yield limiter1 eq limiter2).unsafeRunSync()

    same shouldBe true
  }

  it should "create separate limiters for different names" in {
    val different = (for {
      registry <- LimiterRegistry.create
      limiter1 <- registry.getConcurrencyLimiter("module1", 5)
      limiter2 <- registry.getConcurrencyLimiter("module2", 5)
    } yield !(limiter1 eq limiter2)).unsafeRunSync()

    different shouldBe true
  }
}

// ---------------------------------------------------------------------------
// hasRateLimiter
// ---------------------------------------------------------------------------

class LimiterRegistryHasRateLimiterTest extends AnyFlatSpec with Matchers {

  "hasRateLimiter" should "return false for non-existing limiter" in {
    val has = (for {
      registry <- LimiterRegistry.create
      has      <- registry.hasRateLimiter("nonexistent")
    } yield has).unsafeRunSync()

    has shouldBe false
  }

  it should "return true for existing limiter" in {
    val has = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getRateLimiter("module1", RateLimit.perSecond(10))
      has      <- registry.hasRateLimiter("module1")
    } yield has).unsafeRunSync()

    has shouldBe true
  }
}

// ---------------------------------------------------------------------------
// hasConcurrencyLimiter
// ---------------------------------------------------------------------------

class LimiterRegistryHasConcurrencyLimiterTest extends AnyFlatSpec with Matchers {

  "hasConcurrencyLimiter" should "return false for non-existing limiter" in {
    val has = (for {
      registry <- LimiterRegistry.create
      has      <- registry.hasConcurrencyLimiter("nonexistent")
    } yield has).unsafeRunSync()

    has shouldBe false
  }

  it should "return true for existing limiter" in {
    val has = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getConcurrencyLimiter("module1", 5)
      has      <- registry.hasConcurrencyLimiter("module1")
    } yield has).unsafeRunSync()

    has shouldBe true
  }
}

// ---------------------------------------------------------------------------
// listRateLimiters
// ---------------------------------------------------------------------------

class LimiterRegistryListRateLimitersTest extends AnyFlatSpec with Matchers {

  "listRateLimiters" should "return empty list when no limiters registered" in {
    val names = (for {
      registry <- LimiterRegistry.create
      names    <- registry.listRateLimiters
    } yield names).unsafeRunSync()

    names shouldBe empty
  }

  it should "return sorted list after additions" in {
    val names = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getRateLimiter("charlie", RateLimit.perSecond(10))
      _        <- registry.getRateLimiter("alpha", RateLimit.perSecond(10))
      _        <- registry.getRateLimiter("bravo", RateLimit.perSecond(10))
      names    <- registry.listRateLimiters
    } yield names).unsafeRunSync()

    names shouldBe List("alpha", "bravo", "charlie")
  }
}

// ---------------------------------------------------------------------------
// listConcurrencyLimiters
// ---------------------------------------------------------------------------

class LimiterRegistryListConcurrencyLimitersTest extends AnyFlatSpec with Matchers {

  "listConcurrencyLimiters" should "return empty list when no limiters registered" in {
    val names = (for {
      registry <- LimiterRegistry.create
      names    <- registry.listConcurrencyLimiters
    } yield names).unsafeRunSync()

    names shouldBe empty
  }

  it should "return sorted list after additions" in {
    val names = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getConcurrencyLimiter("zulu", 5)
      _        <- registry.getConcurrencyLimiter("alpha", 3)
      _        <- registry.getConcurrencyLimiter("mike", 10)
      names    <- registry.listConcurrencyLimiters
    } yield names).unsafeRunSync()

    names shouldBe List("alpha", "mike", "zulu")
  }
}

// ---------------------------------------------------------------------------
// allRateLimiterStats
// ---------------------------------------------------------------------------

class LimiterRegistryAllRateLimiterStatsTest extends AnyFlatSpec with Matchers {

  "allRateLimiterStats" should "return empty map when no limiters registered" in {
    val stats = (for {
      registry <- LimiterRegistry.create
      stats    <- registry.allRateLimiterStats
    } yield stats).unsafeRunSync()

    stats shouldBe empty
  }

  it should "return stats for all registered rate limiters" in {
    val stats = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getRateLimiter("module1", RateLimit.perSecond(10))
      _        <- registry.getRateLimiter("module2", RateLimit.perSecond(20))
      stats    <- registry.allRateLimiterStats
    } yield stats).unsafeRunSync()

    stats.size shouldBe 2
    stats should contain key "module1"
    stats should contain key "module2"
    stats("module1").maxTokens shouldBe 10.0
    stats("module2").maxTokens shouldBe 20.0
  }
}

// ---------------------------------------------------------------------------
// allConcurrencyStats
// ---------------------------------------------------------------------------

class LimiterRegistryAllConcurrencyStatsTest extends AnyFlatSpec with Matchers {

  "allConcurrencyStats" should "return empty map when no limiters registered" in {
    val stats = (for {
      registry <- LimiterRegistry.create
      stats    <- registry.allConcurrencyStats
    } yield stats).unsafeRunSync()

    stats shouldBe empty
  }

  it should "return stats for all registered concurrency limiters" in {
    val stats = (for {
      registry <- LimiterRegistry.create
      limiter1 <- registry.getConcurrencyLimiter("module1", 5)
      _        <- limiter1.acquire
      _        <- registry.getConcurrencyLimiter("module2", 10)
      stats    <- registry.allConcurrencyStats
    } yield stats).unsafeRunSync()

    stats.size shouldBe 2
    stats should contain key "module1"
    stats should contain key "module2"
    stats("module1").currentActive shouldBe 1
    stats("module1").maxConcurrent shouldBe 5
    stats("module2").currentActive shouldBe 0
    stats("module2").maxConcurrent shouldBe 10
  }
}

// ---------------------------------------------------------------------------
// removeRateLimiter
// ---------------------------------------------------------------------------

class LimiterRegistryRemoveRateLimiterTest extends AnyFlatSpec with Matchers {

  "removeRateLimiter" should "return true when removing an existing limiter" in {
    val (removed, hasAfter) = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getRateLimiter("module1", RateLimit.perSecond(10))
      removed  <- registry.removeRateLimiter("module1")
      hasAfter <- registry.hasRateLimiter("module1")
    } yield (removed, hasAfter)).unsafeRunSync()

    removed shouldBe true
    hasAfter shouldBe false
  }

  it should "return false when removing a non-existing limiter" in {
    val removed = (for {
      registry <- LimiterRegistry.create
      removed  <- registry.removeRateLimiter("nonexistent")
    } yield removed).unsafeRunSync()

    removed shouldBe false
  }
}

// ---------------------------------------------------------------------------
// removeConcurrencyLimiter
// ---------------------------------------------------------------------------

class LimiterRegistryRemoveConcurrencyLimiterTest extends AnyFlatSpec with Matchers {

  "removeConcurrencyLimiter" should "return true when removing an existing limiter" in {
    val (removed, hasAfter) = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getConcurrencyLimiter("module1", 5)
      removed  <- registry.removeConcurrencyLimiter("module1")
      hasAfter <- registry.hasConcurrencyLimiter("module1")
    } yield (removed, hasAfter)).unsafeRunSync()

    removed shouldBe true
    hasAfter shouldBe false
  }

  it should "return false when removing a non-existing limiter" in {
    val removed = (for {
      registry <- LimiterRegistry.create
      removed  <- registry.removeConcurrencyLimiter("nonexistent")
    } yield removed).unsafeRunSync()

    removed shouldBe false
  }
}

// ---------------------------------------------------------------------------
// clear
// ---------------------------------------------------------------------------

class LimiterRegistryClearTest extends AnyFlatSpec with Matchers {

  "clear" should "remove all rate limiters and concurrency limiters" in {
    val (rateBefore, concBefore, rateAfter, concAfter) = (for {
      registry   <- LimiterRegistry.create
      _          <- registry.getRateLimiter("r1", RateLimit.perSecond(10))
      _          <- registry.getRateLimiter("r2", RateLimit.perSecond(20))
      _          <- registry.getConcurrencyLimiter("c1", 5)
      _          <- registry.getConcurrencyLimiter("c2", 10)
      rateBefore <- registry.listRateLimiters
      concBefore <- registry.listConcurrencyLimiters
      _          <- registry.clear
      rateAfter  <- registry.listRateLimiters
      concAfter  <- registry.listConcurrencyLimiters
    } yield (rateBefore, concBefore, rateAfter, concAfter)).unsafeRunSync()

    rateBefore.size shouldBe 2
    concBefore.size shouldBe 2
    rateAfter shouldBe empty
    concAfter shouldBe empty
  }
}

// ---------------------------------------------------------------------------
// RateControlOptions
// ---------------------------------------------------------------------------

class RateControlOptionsTest extends AnyFlatSpec with Matchers {

  "RateControlOptions.withThrottle" should "set throttle and leave concurrency as None" in {
    val rate    = RateLimit.perSecond(10)
    val options = RateControlOptions.withThrottle(rate)

    options.throttle shouldBe Some(rate)
    options.concurrency shouldBe None
  }

  "RateControlOptions.withConcurrency" should "set concurrency and leave throttle as None" in {
    val options = RateControlOptions.withConcurrency(5)

    options.throttle shouldBe None
    options.concurrency shouldBe Some(5)
  }

  "RateControlOptions.apply(throttle, concurrency)" should "set both throttle and concurrency" in {
    val rate    = RateLimit.perSecond(10)
    val options = RateControlOptions(rate, 5)

    options.throttle shouldBe Some(rate)
    options.concurrency shouldBe Some(5)
  }

  "RateControlOptions()" should "default to no throttle and no concurrency" in {
    val options = RateControlOptions()

    options.throttle shouldBe None
    options.concurrency shouldBe None
  }
}

// ---------------------------------------------------------------------------
// RateControlExecutor
// ---------------------------------------------------------------------------

class RateControlExecutorExtendedTest extends AnyFlatSpec with Matchers {

  "RateControlExecutor.executeWithRateControl" should "execute without limiting when options are empty" in {
    val counter = new AtomicInteger(0)

    val result = (for {
      registry <- LimiterRegistry.create
      result <- RateControlExecutor.executeWithRateControl(
        IO { counter.incrementAndGet(); 42 },
        "module",
        RateControlOptions(),
        registry
      )
    } yield result).unsafeRunSync()

    result shouldBe 42
    counter.get() shouldBe 1
  }

  it should "apply concurrency limiting only" in {
    val maxActive     = new AtomicInteger(0)
    val currentActive = new AtomicInteger(0)

    val peak = (for {
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

    peak should be <= 2
  }

  it should "apply throttle limiting only" in {
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

  it should "apply both throttle and concurrency limiting" in {
    val counter       = new AtomicInteger(0)
    val maxActive     = new AtomicInteger(0)
    val currentActive = new AtomicInteger(0)

    val (total, peak) = (for {
      registry <- LimiterRegistry.create
      _ <- (1 to 4).toList.parTraverse_ { _ =>
        RateControlExecutor.executeWithRateControl(
          IO {
            val active = currentActive.incrementAndGet()
            maxActive.updateAndGet(m => math.max(m, active))
            counter.incrementAndGet()
          } >> IO.sleep(20.millis) >> IO(currentActive.decrementAndGet()),
          "module",
          RateControlOptions(RateLimit.perSecond(100), 2),
          registry
        )
      }
    } yield (counter.get(), maxActive.get())).unsafeRunSync()

    total shouldBe 4
    peak should be <= 2
  }
}
