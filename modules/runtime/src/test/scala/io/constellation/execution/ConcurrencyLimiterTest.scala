package io.constellation.execution

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.duration.*

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// ---------------------------------------------------------------------------
// ConcurrencyLimiter creation
// ---------------------------------------------------------------------------

class ConcurrencyLimiterCreationTest extends AnyFlatSpec with Matchers {

  "ConcurrencyLimiter.apply" should "create limiter with correct available permits" in {
    val available = (for {
      limiter   <- ConcurrencyLimiter(5)
      available <- limiter.available
    } yield available).unsafeRunSync()

    available shouldBe 5L
  }

  it should "throw IllegalArgumentException when maxConcurrent is 0" in {
    an[IllegalArgumentException] should be thrownBy {
      ConcurrencyLimiter(0).unsafeRunSync()
    }
  }

  it should "throw IllegalArgumentException when maxConcurrent is negative" in {
    an[IllegalArgumentException] should be thrownBy {
      ConcurrencyLimiter(-1).unsafeRunSync()
    }
  }

  "ConcurrencyLimiter.mutex" should "create limiter with 1 permit" in {
    val available = (for {
      limiter   <- ConcurrencyLimiter.mutex
      available <- limiter.available
    } yield available).unsafeRunSync()

    available shouldBe 1L
  }
}

// ---------------------------------------------------------------------------
// withPermit
// ---------------------------------------------------------------------------

class ConcurrencyLimiterWithPermitTest extends AnyFlatSpec with Matchers {

  "withPermit" should "execute operation and return result" in {
    val result = (for {
      limiter <- ConcurrencyLimiter(3)
      result  <- limiter.withPermit(IO.pure(42))
    } yield result).unsafeRunSync()

    result shouldBe 42
  }

  it should "release permit even on error" in {
    val stats = (for {
      limiter <- ConcurrencyLimiter(2)
      _       <- limiter.withPermit(IO.raiseError[Int](new RuntimeException("boom"))).attempt
      stats   <- limiter.stats
    } yield stats).unsafeRunSync()

    stats.currentActive shouldBe 0
    stats.availablePermits shouldBe 2
  }

  it should "limit concurrency to maxConcurrent" in {
    val maxActive     = new AtomicInteger(0)
    val currentActive = new AtomicInteger(0)

    val peak = (for {
      limiter <- ConcurrencyLimiter(2)
      _ <- (1 to 10).toList.parTraverse_ { _ =>
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

    peak should be <= 2
  }
}

// ---------------------------------------------------------------------------
// acquire / release
// ---------------------------------------------------------------------------

class ConcurrencyLimiterAcquireReleaseTest extends AnyFlatSpec with Matchers {

  "acquire" should "decrement available permits" in {
    val (before, after) = (for {
      limiter <- ConcurrencyLimiter(3)
      before  <- limiter.available
      _       <- limiter.acquire
      after   <- limiter.available
    } yield (before, after)).unsafeRunSync()

    before shouldBe 3L
    after shouldBe 2L
  }

  "release" should "increment available permits" in {
    val (during, after) = (for {
      limiter <- ConcurrencyLimiter(3)
      _       <- limiter.acquire
      during  <- limiter.available
      _       <- limiter.release
      after   <- limiter.available
    } yield (during, after)).unsafeRunSync()

    during shouldBe 2L
    after shouldBe 3L
  }

  "active" should "reflect current active count" in {
    val (before, during, after) = (for {
      limiter <- ConcurrencyLimiter(5)
      before  <- limiter.active
      _       <- limiter.acquire
      _       <- limiter.acquire
      during  <- limiter.active
      _       <- limiter.release
      after   <- limiter.active
    } yield (before, during, after)).unsafeRunSync()

    before shouldBe 0L
    during shouldBe 2L
    after shouldBe 1L
  }
}

// ---------------------------------------------------------------------------
// tryAcquire
// ---------------------------------------------------------------------------

class ConcurrencyLimiterTryAcquireTest extends AnyFlatSpec with Matchers {

  "tryAcquire" should "succeed when permits are available" in {
    val acquired = (for {
      limiter  <- ConcurrencyLimiter(3)
      acquired <- limiter.tryAcquire
    } yield acquired).unsafeRunSync()

    acquired shouldBe true
  }

  it should "fail when no permits are available" in {
    val acquired = (for {
      limiter  <- ConcurrencyLimiter(2)
      _        <- limiter.acquire
      _        <- limiter.acquire
      acquired <- limiter.tryAcquire
    } yield acquired).unsafeRunSync()

    acquired shouldBe false
  }

  it should "allow the acquired permit to be released" in {
    val (acquired, availableAfter) = (for {
      limiter        <- ConcurrencyLimiter(1)
      acquired       <- limiter.tryAcquire
      _              <- limiter.release
      availableAfter <- limiter.available
    } yield (acquired, availableAfter)).unsafeRunSync()

    acquired shouldBe true
    availableAfter shouldBe 1L
  }
}

// ---------------------------------------------------------------------------
// stats
// ---------------------------------------------------------------------------

class ConcurrencyLimiterStatsTest extends AnyFlatSpec with Matchers {

  "stats" should "report correct initial values" in {
    val stats = (for {
      limiter <- ConcurrencyLimiter(4)
      stats   <- limiter.stats
    } yield stats).unsafeRunSync()

    stats.maxConcurrent shouldBe 4
    stats.currentActive shouldBe 0
    stats.peakActive shouldBe 0
    stats.totalExecutions shouldBe 0
    stats.availablePermits shouldBe 4
  }

  it should "track active count during execution" in {
    val activeDuring = (for {
      limiter <- ConcurrencyLimiter(5)
      _       <- limiter.acquire
      _       <- limiter.acquire
      stats   <- limiter.stats
    } yield stats.currentActive).unsafeRunSync()

    activeDuring shouldBe 2L
  }

  it should "track peak active across multiple executions" in {
    val stats = (for {
      limiter <- ConcurrencyLimiter(5)
      _       <- limiter.acquire
      _       <- limiter.acquire
      _       <- limiter.acquire // peak = 3
      _       <- limiter.release
      _       <- limiter.release // active = 1, peak still 3
      stats   <- limiter.stats
    } yield stats).unsafeRunSync()

    stats.currentActive shouldBe 1
    stats.peakActive shouldBe 3
  }

  it should "track total executions" in {
    val stats = (for {
      limiter <- ConcurrencyLimiter(5)
      _       <- limiter.acquire
      _       <- limiter.release
      _       <- limiter.acquire
      _       <- limiter.release
      _       <- limiter.acquire
      _       <- limiter.release
      stats   <- limiter.stats
    } yield stats).unsafeRunSync()

    stats.totalExecutions shouldBe 3
  }

  "resetStats" should "clear peak and total but not active" in {
    val (before, after) = (for {
      limiter <- ConcurrencyLimiter(5)
      _       <- limiter.acquire // active=1, total=1, peak=1
      _       <- limiter.acquire // active=2, total=2, peak=2
      _       <- limiter.release // active=1, total=2, peak=2
      before  <- limiter.stats
      _       <- limiter.resetStats
      after   <- limiter.stats
    } yield (before, after)).unsafeRunSync()

    before.peakActive shouldBe 2
    before.totalExecutions shouldBe 2

    after.peakActive shouldBe 0
    after.totalExecutions shouldBe 0
    after.currentActive shouldBe 1 // active is not reset
  }
}

// ---------------------------------------------------------------------------
// ConcurrencyStats
// ---------------------------------------------------------------------------

class ConcurrencyStatsValueTest extends AnyFlatSpec with Matchers {

  "utilization" should "return correct ratio" in {
    val stats = ConcurrencyStats(
      maxConcurrent = 10,
      currentActive = 3,
      peakActive = 7,
      totalExecutions = 100,
      currentWaiting = 0,
      availablePermits = 7
    )

    stats.utilization shouldBe 0.3 +- 0.001
  }

  it should "be 0 when no active executions" in {
    val stats = ConcurrencyStats(
      maxConcurrent = 10,
      currentActive = 0,
      peakActive = 0,
      totalExecutions = 0,
      currentWaiting = 0,
      availablePermits = 10
    )

    stats.utilization shouldBe 0.0
  }

  "peakUtilization" should "return correct ratio" in {
    val stats = ConcurrencyStats(
      maxConcurrent = 10,
      currentActive = 1,
      peakActive = 8,
      totalExecutions = 50,
      currentWaiting = 0,
      availablePermits = 9
    )

    stats.peakUtilization shouldBe 0.8 +- 0.001
  }

  "toString" should "contain key info" in {
    val stats = ConcurrencyStats(
      maxConcurrent = 4,
      currentActive = 2,
      peakActive = 3,
      totalExecutions = 10,
      currentWaiting = 1,
      availablePermits = 2
    )

    val str = stats.toString
    str should include("active=2/4")
    str should include("peak=3")
    str should include("total=10")
    str should include("waiting=1")
  }
}
