package io.constellation.execution

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// ---------------------------------------------------------------------------
// Race conditions in getRateLimiter
// ---------------------------------------------------------------------------

class LimiterRegistryRateLimiterRaceTest extends AnyFlatSpec with Matchers {

  "getRateLimiter" should "return the same limiter when two concurrent calls race for the same name" in {
    val results = (for {
      registry <- LimiterRegistry.create
      // Fire many concurrent requests for the same name to trigger the
      // modify-branch where an existing limiter is discovered after creation.
      limiters <- (1 to 50).toList.parTraverse { _ =>
        registry.getRateLimiter("raceModule", RateLimit.perSecond(10))
      }
    } yield limiters).unsafeRunSync()

    // All returned limiters must be the exact same instance
    results.distinct.size shouldBe 1
    results.forall(_ eq results.head) shouldBe true
  }

  it should "only register one limiter despite concurrent creation attempts" in {
    val count = (for {
      registry <- LimiterRegistry.create
      _ <- (1 to 20).toList.parTraverse_ { _ =>
        registry.getRateLimiter("singleModule", RateLimit.perSecond(5))
      }
      names <- registry.listRateLimiters
    } yield names.size).unsafeRunSync()

    count shouldBe 1
  }

  it should "handle concurrent creation of many distinct limiters" in {
    val names = (for {
      registry <- LimiterRegistry.create
      _ <- (1 to 30).toList.parTraverse_ { i =>
        registry.getRateLimiter(s"module$i", RateLimit.perSecond(10))
      }
      names <- registry.listRateLimiters
    } yield names).unsafeRunSync()

    names.size shouldBe 30
  }
}

// ---------------------------------------------------------------------------
// Race conditions in getConcurrencyLimiter
// ---------------------------------------------------------------------------

class LimiterRegistryConcurrencyLimiterRaceTest extends AnyFlatSpec with Matchers {

  "getConcurrencyLimiter" should "return the same limiter when two concurrent calls race for the same name" in {
    val results = (for {
      registry <- LimiterRegistry.create
      limiters <- (1 to 50).toList.parTraverse { _ =>
        registry.getConcurrencyLimiter("raceModule", 5)
      }
    } yield limiters).unsafeRunSync()

    results.distinct.size shouldBe 1
    results.forall(_ eq results.head) shouldBe true
  }

  it should "only register one limiter despite concurrent creation attempts" in {
    val count = (for {
      registry <- LimiterRegistry.create
      _ <- (1 to 20).toList.parTraverse_ { _ =>
        registry.getConcurrencyLimiter("singleModule", 3)
      }
      names <- registry.listConcurrencyLimiters
    } yield names.size).unsafeRunSync()

    count shouldBe 1
  }

  it should "handle concurrent creation of many distinct limiters" in {
    val names = (for {
      registry <- LimiterRegistry.create
      _ <- (1 to 30).toList.parTraverse_ { i =>
        registry.getConcurrencyLimiter(s"module$i", 5)
      }
      names <- registry.listConcurrencyLimiters
    } yield names).unsafeRunSync()

    names.size shouldBe 30
  }
}

// ---------------------------------------------------------------------------
// listRateLimiters extended scenarios
// ---------------------------------------------------------------------------

class LimiterRegistryListRateLimitersExtendedTest extends AnyFlatSpec with Matchers {

  "listRateLimiters" should "not include removed limiters" in {
    val names = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getRateLimiter("a", RateLimit.perSecond(10))
      _        <- registry.getRateLimiter("b", RateLimit.perSecond(10))
      _        <- registry.getRateLimiter("c", RateLimit.perSecond(10))
      _        <- registry.removeRateLimiter("b")
      names    <- registry.listRateLimiters
    } yield names).unsafeRunSync()

    names shouldBe List("a", "c")
  }

  it should "return empty list after clear" in {
    val names = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getRateLimiter("x", RateLimit.perSecond(10))
      _        <- registry.clear
      names    <- registry.listRateLimiters
    } yield names).unsafeRunSync()

    names shouldBe empty
  }
}

// ---------------------------------------------------------------------------
// listConcurrencyLimiters extended scenarios
// ---------------------------------------------------------------------------

class LimiterRegistryListConcurrencyLimitersExtendedTest extends AnyFlatSpec with Matchers {

  "listConcurrencyLimiters" should "not include removed limiters" in {
    val names = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getConcurrencyLimiter("a", 5)
      _        <- registry.getConcurrencyLimiter("b", 5)
      _        <- registry.getConcurrencyLimiter("c", 5)
      _        <- registry.removeConcurrencyLimiter("b")
      names    <- registry.listConcurrencyLimiters
    } yield names).unsafeRunSync()

    names shouldBe List("a", "c")
  }

  it should "return empty list after clear" in {
    val names = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getConcurrencyLimiter("x", 5)
      _        <- registry.clear
      names    <- registry.listConcurrencyLimiters
    } yield names).unsafeRunSync()

    names shouldBe empty
  }
}

// ---------------------------------------------------------------------------
// allRateLimiterStats extended scenarios
// ---------------------------------------------------------------------------

class LimiterRegistryAllRateLimiterStatsExtendedTest extends AnyFlatSpec with Matchers {

  "allRateLimiterStats" should "report correct maxTokens and rate for registered limiters" in {
    val stats = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getRateLimiter("module1", RateLimit.perSecond(10))
      _        <- registry.getRateLimiter("module2", RateLimit.perSecond(25))
      stats    <- registry.allRateLimiterStats
    } yield stats).unsafeRunSync()

    stats.size shouldBe 2
    stats("module1").maxTokens shouldBe 10.0
    stats("module1").rate shouldBe RateLimit.perSecond(10)
    stats("module2").maxTokens shouldBe 25.0
    stats("module2").rate shouldBe RateLimit.perSecond(25)
  }

  it should "not include stats for removed limiters" in {
    val stats = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getRateLimiter("keep", RateLimit.perSecond(10))
      _        <- registry.getRateLimiter("remove", RateLimit.perSecond(20))
      _        <- registry.removeRateLimiter("remove")
      stats    <- registry.allRateLimiterStats
    } yield stats).unsafeRunSync()

    stats.size shouldBe 1
    stats should contain key "keep"
    stats should not contain key("remove")
  }
}

// ---------------------------------------------------------------------------
// allConcurrencyStats extended scenarios
// ---------------------------------------------------------------------------

class LimiterRegistryAllConcurrencyStatsExtendedTest extends AnyFlatSpec with Matchers {

  "allConcurrencyStats" should "not include stats for removed limiters" in {
    val stats = (for {
      registry <- LimiterRegistry.create
      _        <- registry.getConcurrencyLimiter("keep", 5)
      _        <- registry.getConcurrencyLimiter("remove", 10)
      _        <- registry.removeConcurrencyLimiter("remove")
      stats    <- registry.allConcurrencyStats
    } yield stats).unsafeRunSync()

    stats.size shouldBe 1
    stats should contain key "keep"
    stats should not contain key("remove")
  }

  it should "reflect peak and total stats after executions" in {
    val stats = (for {
      registry <- LimiterRegistry.create
      limiter  <- registry.getConcurrencyLimiter("module1", 5)
      _        <- limiter.withPermit(IO.unit)
      _        <- limiter.withPermit(IO.unit)
      stats    <- registry.allConcurrencyStats
    } yield stats).unsafeRunSync()

    stats("module1").totalExecutions shouldBe 2
    stats("module1").currentActive shouldBe 0
  }
}

// ---------------------------------------------------------------------------
// removeRateLimiter / removeConcurrencyLimiter extended
// ---------------------------------------------------------------------------

class LimiterRegistryRemoveExtendedTest extends AnyFlatSpec with Matchers {

  "removeRateLimiter" should "allow re-creating a limiter after removal" in {
    val (hasBefore, hasMiddle, hasAfter) = (for {
      registry  <- LimiterRegistry.create
      _         <- registry.getRateLimiter("mod", RateLimit.perSecond(10))
      hasBefore <- registry.hasRateLimiter("mod")
      _         <- registry.removeRateLimiter("mod")
      hasMiddle <- registry.hasRateLimiter("mod")
      _         <- registry.getRateLimiter("mod", RateLimit.perSecond(50))
      hasAfter  <- registry.hasRateLimiter("mod")
    } yield (hasBefore, hasMiddle, hasAfter)).unsafeRunSync()

    hasBefore shouldBe true
    hasMiddle shouldBe false
    hasAfter shouldBe true
  }

  "removeConcurrencyLimiter" should "allow re-creating a limiter after removal" in {
    val (hasBefore, hasMiddle, hasAfter) = (for {
      registry  <- LimiterRegistry.create
      _         <- registry.getConcurrencyLimiter("mod", 5)
      hasBefore <- registry.hasConcurrencyLimiter("mod")
      _         <- registry.removeConcurrencyLimiter("mod")
      hasMiddle <- registry.hasConcurrencyLimiter("mod")
      _         <- registry.getConcurrencyLimiter("mod", 10)
      hasAfter  <- registry.hasConcurrencyLimiter("mod")
    } yield (hasBefore, hasMiddle, hasAfter)).unsafeRunSync()

    hasBefore shouldBe true
    hasMiddle shouldBe false
    hasAfter shouldBe true
  }
}

// ---------------------------------------------------------------------------
// clear extended
// ---------------------------------------------------------------------------

class LimiterRegistryClearExtendedTest extends AnyFlatSpec with Matchers {

  "clear" should "allow adding new limiters after clear" in {
    val (namesAfterClear, namesAfterAdd) = (for {
      registry       <- LimiterRegistry.create
      _              <- registry.getRateLimiter("r1", RateLimit.perSecond(10))
      _              <- registry.getConcurrencyLimiter("c1", 5)
      _              <- registry.clear
      namesAfterClear <- registry.listRateLimiters
      _              <- registry.getRateLimiter("r2", RateLimit.perSecond(20))
      namesAfterAdd  <- registry.listRateLimiters
    } yield (namesAfterClear, namesAfterAdd)).unsafeRunSync()

    namesAfterClear shouldBe empty
    namesAfterAdd shouldBe List("r2")
  }

  it should "be idempotent on an empty registry" in {
    val (rateBefore, concBefore) = (for {
      registry   <- LimiterRegistry.create
      _          <- registry.clear
      _          <- registry.clear
      rateBefore <- registry.listRateLimiters
      concBefore <- registry.listConcurrencyLimiters
    } yield (rateBefore, concBefore)).unsafeRunSync()

    rateBefore shouldBe empty
    concBefore shouldBe empty
  }
}

// ---------------------------------------------------------------------------
// RateControlOptions extended
// ---------------------------------------------------------------------------

class RateControlOptionsExtendedTest extends AnyFlatSpec with Matchers {

  "RateControlOptions.apply(throttle, concurrency)" should "expose both fields correctly" in {
    val rate    = RateLimit.perMinute(60)
    val options = RateControlOptions(rate, 10)

    options.throttle shouldBe Some(rate)
    options.concurrency shouldBe Some(10)
    options.throttle.get.count shouldBe 60
    options.concurrency.get shouldBe 10
  }

  "RateControlOptions case class copy" should "allow overriding individual fields" in {
    val base    = RateControlOptions.withThrottle(RateLimit.perSecond(10))
    val updated = base.copy(concurrency = Some(3))

    updated.throttle shouldBe Some(RateLimit.perSecond(10))
    updated.concurrency shouldBe Some(3)
  }
}

// ---------------------------------------------------------------------------
// RateControlExecutor extended
// ---------------------------------------------------------------------------

class RateControlExecutorPipelineTest extends AnyFlatSpec with Matchers {

  "RateControlExecutor.executeWithRateControl" should "propagate errors from the operation" in {
    val error = new RuntimeException("operation failed")

    val result = (for {
      registry <- LimiterRegistry.create
      attempt <- RateControlExecutor
        .executeWithRateControl(
          IO.raiseError[Int](error),
          "failModule",
          RateControlOptions(RateLimit.perSecond(100), 5),
          registry
        )
        .attempt
    } yield attempt).unsafeRunSync()

    result.isLeft shouldBe true
    result.left.toOption.get.getMessage shouldBe "operation failed"
  }

  it should "reuse the same limiters across multiple executeWithRateControl calls" in {
    val (names, concNames) = (for {
      registry <- LimiterRegistry.create
      _ <- RateControlExecutor.executeWithRateControl(
        IO.pure(1),
        "sharedModule",
        RateControlOptions(RateLimit.perSecond(100), 5),
        registry
      )
      _ <- RateControlExecutor.executeWithRateControl(
        IO.pure(2),
        "sharedModule",
        RateControlOptions(RateLimit.perSecond(100), 5),
        registry
      )
      names     <- registry.listRateLimiters
      concNames <- registry.listConcurrencyLimiters
    } yield (names, concNames)).unsafeRunSync()

    names shouldBe List("sharedModule")
    concNames shouldBe List("sharedModule")
  }

  it should "create separate limiters for different module names" in {
    val (rateNames, concNames) = (for {
      registry <- LimiterRegistry.create
      _ <- RateControlExecutor.executeWithRateControl(
        IO.pure(1),
        "moduleA",
        RateControlOptions.withThrottle(RateLimit.perSecond(100)),
        registry
      )
      _ <- RateControlExecutor.executeWithRateControl(
        IO.pure(2),
        "moduleB",
        RateControlOptions.withConcurrency(3),
        registry
      )
      rateNames <- registry.listRateLimiters
      concNames <- registry.listConcurrencyLimiters
    } yield (rateNames, concNames)).unsafeRunSync()

    rateNames shouldBe List("moduleA")
    concNames shouldBe List("moduleB")
  }

  it should "enforce concurrency limit under parallel load through executeWithRateControl" in {
    val maxActive     = new AtomicInteger(0)
    val currentActive = new AtomicInteger(0)
    val maxConcurrent = 3

    val peak = (for {
      registry <- LimiterRegistry.create
      _ <- (1 to 20).toList.parTraverse_ { _ =>
        RateControlExecutor.executeWithRateControl(
          IO {
            val active = currentActive.incrementAndGet()
            maxActive.updateAndGet(m => math.max(m, active))
          } >> IO.sleep(10.millis) >> IO(currentActive.decrementAndGet()),
          "limitedModule",
          RateControlOptions.withConcurrency(maxConcurrent),
          registry
        )
      }
    } yield maxActive.get()).unsafeRunSync()

    peak should be <= maxConcurrent
  }
}
