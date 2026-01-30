package io.constellation.execution

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class CircuitBreakerRegistryTest extends AnyFlatSpec with Matchers {

  private val defaultConfig = CircuitBreakerConfig(
    failureThreshold = 5,
    resetDuration = 30.seconds,
    halfOpenMaxProbes = 1
  )

  "CircuitBreakerRegistry.create" should "start with empty registry" in {
    val registry = CircuitBreakerRegistry.create(defaultConfig).unsafeRunSync()

    registry.get("Unknown").unsafeRunSync() shouldBe None
    registry.allStats.unsafeRunSync() shouldBe empty
  }

  "getOrCreate" should "create new circuit breaker on first access" in {
    val registry = CircuitBreakerRegistry.create(defaultConfig).unsafeRunSync()

    val cb = registry.getOrCreate("ModuleA").unsafeRunSync()
    cb should not be null

    registry.get("ModuleA").unsafeRunSync() shouldBe defined
  }

  it should "return same instance on repeated access" in {
    val registry = CircuitBreakerRegistry.create(defaultConfig).unsafeRunSync()

    val cb1 = registry.getOrCreate("ModuleA").unsafeRunSync()
    val cb2 = registry.getOrCreate("ModuleA").unsafeRunSync()

    (cb1 eq cb2) shouldBe true
  }

  it should "create different breakers for different modules" in {
    val registry = CircuitBreakerRegistry.create(defaultConfig).unsafeRunSync()

    val cbA = registry.getOrCreate("ModuleA").unsafeRunSync()
    val cbB = registry.getOrCreate("ModuleB").unsafeRunSync()

    (cbA eq cbB) shouldBe false
  }

  "get" should "return None for unknown module" in {
    val registry = CircuitBreakerRegistry.create(defaultConfig).unsafeRunSync()

    registry.get("NonExistent").unsafeRunSync() shouldBe None
  }

  it should "return Some for known module" in {
    val registry = CircuitBreakerRegistry.create(defaultConfig).unsafeRunSync()

    registry.getOrCreate("ModuleA").unsafeRunSync()
    val result = registry.get("ModuleA").unsafeRunSync()

    result shouldBe defined
  }

  "allStats" should "aggregate stats across all registered breakers" in {
    val registry = CircuitBreakerRegistry.create(defaultConfig).unsafeRunSync()

    // Create breakers and run some operations
    val cbA = registry.getOrCreate("ModuleA").unsafeRunSync()
    val cbB = registry.getOrCreate("ModuleB").unsafeRunSync()

    cbA.protect(IO.pure(1)).unsafeRunSync()
    cbB.protect(IO.pure(2)).unsafeRunSync()
    cbB.protect(IO.pure(3)).unsafeRunSync()

    val stats = registry.allStats.unsafeRunSync()
    stats.size shouldBe 2
    stats.contains("ModuleA") shouldBe true
    stats.contains("ModuleB") shouldBe true

    stats("ModuleA").totalSuccesses shouldBe 1
    stats("ModuleB").totalSuccesses shouldBe 2
  }

  it should "return empty map when no breakers registered" in {
    val registry = CircuitBreakerRegistry.create(defaultConfig).unsafeRunSync()
    registry.allStats.unsafeRunSync() shouldBe empty
  }
}
