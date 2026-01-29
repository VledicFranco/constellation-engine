package io.constellation.execution

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.implicits._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicInteger

class CircuitBreakerTest extends AnyFlatSpec with Matchers {

  // -------------------------------------------------------------------------
  // CircuitBreaker - Closed State
  // -------------------------------------------------------------------------

  "CircuitBreaker" should "execute operations normally in Closed state" in {
    val cb = CircuitBreaker.create("TestModule", CircuitBreakerConfig(failureThreshold = 3)).unsafeRunSync()

    val result = cb.protect(IO.pure(42)).unsafeRunSync()
    result shouldBe 42

    cb.state.unsafeRunSync() shouldBe CircuitState.Closed
    val stats = cb.stats.unsafeRunSync()
    stats.totalSuccesses shouldBe 1
    stats.totalFailures shouldBe 0
    stats.consecutiveFailures shouldBe 0
  }

  it should "track multiple successes" in {
    val cb = CircuitBreaker.create("TestModule", CircuitBreakerConfig(failureThreshold = 3)).unsafeRunSync()

    (1 to 5).foreach { i =>
      cb.protect(IO.pure(i)).unsafeRunSync() shouldBe i
    }

    val stats = cb.stats.unsafeRunSync()
    stats.totalSuccesses shouldBe 5
    stats.consecutiveFailures shouldBe 0
  }

  it should "track failures without opening if below threshold" in {
    val cb = CircuitBreaker.create("TestModule", CircuitBreakerConfig(failureThreshold = 3)).unsafeRunSync()

    // 2 failures (threshold is 3)
    (1 to 2).foreach { _ =>
      intercept[RuntimeException] {
        cb.protect(IO.raiseError[Int](new RuntimeException("fail"))).unsafeRunSync()
      }
    }

    cb.state.unsafeRunSync() shouldBe CircuitState.Closed
    val stats = cb.stats.unsafeRunSync()
    stats.consecutiveFailures shouldBe 2
    stats.totalFailures shouldBe 2
  }

  // -------------------------------------------------------------------------
  // CircuitBreaker - Open State
  // -------------------------------------------------------------------------

  it should "open after N consecutive failures" in {
    val cb = CircuitBreaker.create("TestModule", CircuitBreakerConfig(failureThreshold = 3)).unsafeRunSync()

    // 3 consecutive failures
    (1 to 3).foreach { _ =>
      intercept[RuntimeException] {
        cb.protect(IO.raiseError[Int](new RuntimeException("fail"))).unsafeRunSync()
      }
    }

    cb.state.unsafeRunSync() shouldBe CircuitState.Open
  }

  it should "reject immediately when Open" in {
    val cb = CircuitBreaker.create("TestModule", CircuitBreakerConfig(
      failureThreshold = 2,
      resetDuration = 10.seconds // long duration to stay open
    )).unsafeRunSync()

    // Open the circuit
    (1 to 2).foreach { _ =>
      intercept[RuntimeException] {
        cb.protect(IO.raiseError[Int](new RuntimeException("fail"))).unsafeRunSync()
      }
    }

    cb.state.unsafeRunSync() shouldBe CircuitState.Open

    // The operation should never execute
    val callCount = new AtomicInteger(0)
    val error = intercept[CircuitOpenException] {
      cb.protect(IO { callCount.incrementAndGet(); 42 }).unsafeRunSync()
    }

    error.moduleName shouldBe "TestModule"
    callCount.get() shouldBe 0

    val stats = cb.stats.unsafeRunSync()
    stats.totalRejected shouldBe 1
  }

  it should "reset consecutive failure count on success" in {
    val cb = CircuitBreaker.create("TestModule", CircuitBreakerConfig(failureThreshold = 3)).unsafeRunSync()

    // 2 failures
    (1 to 2).foreach { _ =>
      intercept[RuntimeException] {
        cb.protect(IO.raiseError[Int](new RuntimeException("fail"))).unsafeRunSync()
      }
    }

    // 1 success resets the counter
    cb.protect(IO.pure(42)).unsafeRunSync()

    val stats = cb.stats.unsafeRunSync()
    stats.consecutiveFailures shouldBe 0

    // 2 more failures should NOT open (counter was reset)
    (1 to 2).foreach { _ =>
      intercept[RuntimeException] {
        cb.protect(IO.raiseError[Int](new RuntimeException("fail"))).unsafeRunSync()
      }
    }

    cb.state.unsafeRunSync() shouldBe CircuitState.Closed
  }

  // -------------------------------------------------------------------------
  // CircuitBreaker - HalfOpen State
  // -------------------------------------------------------------------------

  it should "transition to HalfOpen after resetDuration" in {
    val cb = CircuitBreaker.create("TestModule", CircuitBreakerConfig(
      failureThreshold = 2,
      resetDuration = 200.millis
    )).unsafeRunSync()

    // Open the circuit
    (1 to 2).foreach { _ =>
      intercept[RuntimeException] {
        cb.protect(IO.raiseError[Int](new RuntimeException("fail"))).unsafeRunSync()
      }
    }
    cb.state.unsafeRunSync() shouldBe CircuitState.Open

    // Wait for reset duration
    IO.sleep(300.millis).unsafeRunSync()

    // Next call should go through (HalfOpen probe)
    val result = cb.protect(IO.pure(42)).unsafeRunSync()
    result shouldBe 42

    // Success in HalfOpen → Closed
    cb.state.unsafeRunSync() shouldBe CircuitState.Closed
  }

  it should "revert to Open if probe fails in HalfOpen" in {
    val cb = CircuitBreaker.create("TestModule", CircuitBreakerConfig(
      failureThreshold = 2,
      resetDuration = 200.millis
    )).unsafeRunSync()

    // Open the circuit
    (1 to 2).foreach { _ =>
      intercept[RuntimeException] {
        cb.protect(IO.raiseError[Int](new RuntimeException("fail"))).unsafeRunSync()
      }
    }

    // Wait for reset
    IO.sleep(300.millis).unsafeRunSync()

    // Probe fails → back to Open
    intercept[RuntimeException] {
      cb.protect(IO.raiseError[Int](new RuntimeException("still failing"))).unsafeRunSync()
    }

    cb.state.unsafeRunSync() shouldBe CircuitState.Open
  }

  // -------------------------------------------------------------------------
  // CircuitBreaker - Manual Reset
  // -------------------------------------------------------------------------

  it should "reset manually" in {
    val cb = CircuitBreaker.create("TestModule", CircuitBreakerConfig(
      failureThreshold = 2,
      resetDuration = 10.seconds
    )).unsafeRunSync()

    // Open the circuit
    (1 to 2).foreach { _ =>
      intercept[RuntimeException] {
        cb.protect(IO.raiseError[Int](new RuntimeException("fail"))).unsafeRunSync()
      }
    }
    cb.state.unsafeRunSync() shouldBe CircuitState.Open

    // Manual reset
    cb.reset.unsafeRunSync()
    cb.state.unsafeRunSync() shouldBe CircuitState.Closed

    // Should work normally again
    val result = cb.protect(IO.pure(42)).unsafeRunSync()
    result shouldBe 42
  }

  // -------------------------------------------------------------------------
  // CircuitBreakerRegistry
  // -------------------------------------------------------------------------

  "CircuitBreakerRegistry" should "return same instance for same module name" in {
    val registry = CircuitBreakerRegistry.create(CircuitBreakerConfig()).unsafeRunSync()

    val cb1 = registry.getOrCreate("ModuleA").unsafeRunSync()
    val cb2 = registry.getOrCreate("ModuleA").unsafeRunSync()

    // Should be the same instance
    (cb1 eq cb2) shouldBe true
  }

  it should "return different instances for different module names" in {
    val registry = CircuitBreakerRegistry.create(CircuitBreakerConfig()).unsafeRunSync()

    val cbA = registry.getOrCreate("ModuleA").unsafeRunSync()
    val cbB = registry.getOrCreate("ModuleB").unsafeRunSync()

    (cbA eq cbB) shouldBe false
  }

  it should "return None for non-existent modules via get" in {
    val registry = CircuitBreakerRegistry.create(CircuitBreakerConfig()).unsafeRunSync()

    registry.get("NoSuchModule").unsafeRunSync() shouldBe None
  }

  it should "return Some for existing modules via get" in {
    val registry = CircuitBreakerRegistry.create(CircuitBreakerConfig()).unsafeRunSync()

    registry.getOrCreate("ModuleA").unsafeRunSync()
    registry.get("ModuleA").unsafeRunSync().isDefined shouldBe true
  }

  it should "aggregate stats from all breakers" in {
    val registry = CircuitBreakerRegistry.create(CircuitBreakerConfig(failureThreshold = 5)).unsafeRunSync()

    val cbA = registry.getOrCreate("ModuleA").unsafeRunSync()
    val cbB = registry.getOrCreate("ModuleB").unsafeRunSync()

    cbA.protect(IO.pure(1)).unsafeRunSync()
    cbB.protect(IO.pure(2)).unsafeRunSync()
    cbB.protect(IO.pure(3)).unsafeRunSync()

    val allStats = registry.allStats.unsafeRunSync()
    allStats.size shouldBe 2
    allStats("ModuleA").totalSuccesses shouldBe 1
    allStats("ModuleB").totalSuccesses shouldBe 2
  }

  it should "handle concurrent getOrCreate for same name" in {
    val registry = CircuitBreakerRegistry.create(CircuitBreakerConfig()).unsafeRunSync()

    // Launch 10 concurrent getOrCreate for the same name
    val cbs = (1 to 10).toList.parTraverse { _ =>
      registry.getOrCreate("ModuleA")
    }.unsafeRunSync()

    // All should reference the same logical breaker
    val first = cbs.head
    cbs.forall(_ eq first) shouldBe true
  }

  // -------------------------------------------------------------------------
  // CircuitBreaker - Shared across executions
  // -------------------------------------------------------------------------

  "CircuitBreaker" should "share state across DAG executions" in {
    val registry = CircuitBreakerRegistry.create(CircuitBreakerConfig(failureThreshold = 3)).unsafeRunSync()

    // Simulate "execution 1" — 2 failures
    val cb1 = registry.getOrCreate("FailModule").unsafeRunSync()
    (1 to 2).foreach { _ =>
      intercept[RuntimeException] {
        cb1.protect(IO.raiseError[Int](new RuntimeException("fail"))).unsafeRunSync()
      }
    }

    // Simulate "execution 2" — 1 more failure should open (shared state)
    val cb2 = registry.getOrCreate("FailModule").unsafeRunSync()
    intercept[RuntimeException] {
      cb2.protect(IO.raiseError[Int](new RuntimeException("fail"))).unsafeRunSync()
    }

    // Should be open now (3 failures total across "executions")
    cb2.state.unsafeRunSync() shouldBe CircuitState.Open
  }
}
