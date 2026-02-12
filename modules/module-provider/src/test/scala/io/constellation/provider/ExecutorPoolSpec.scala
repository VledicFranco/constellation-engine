package io.constellation.provider

import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ExecutorPoolSpec extends AnyFlatSpec with Matchers {

  private def ep(id: String, url: String = "host:9090") =
    ExecutorEndpoint(connectionId = id, executorUrl = url)

  // ===== Basic Pool Operations =====

  "RoundRobinExecutorPool" should "start empty with size 0" in {
    val pool = RoundRobinExecutorPool.create.unsafeRunSync()
    pool.size.unsafeRunSync() shouldBe 0
  }

  it should "add an endpoint and report size 1" in {
    val pool = RoundRobinExecutorPool.create.unsafeRunSync()
    pool.add(ep("a")).unsafeRunSync()
    pool.size.unsafeRunSync() shouldBe 1
  }

  it should "create with initial endpoint" in {
    val pool = RoundRobinExecutorPool.withEndpoint(ep("a")).unsafeRunSync()
    pool.size.unsafeRunSync() shouldBe 1
  }

  // ===== Round-Robin Selection =====

  it should "return the single endpoint for pool of size 1" in {
    val pool = RoundRobinExecutorPool.withEndpoint(ep("a", "host-a:9090")).unsafeRunSync()
    pool.next.unsafeRunSync().connectionId shouldBe "a"
    pool.next.unsafeRunSync().connectionId shouldBe "a"
  }

  it should "cycle through endpoints in round-robin order" in {
    val pool = RoundRobinExecutorPool.create.unsafeRunSync()
    pool.add(ep("a")).unsafeRunSync()
    pool.add(ep("b")).unsafeRunSync()
    pool.add(ep("c")).unsafeRunSync()

    val results = (1 to 6).map(_ => pool.next.unsafeRunSync().connectionId).toList
    results shouldBe List("a", "b", "c", "a", "b", "c")
  }

  // ===== Error on Empty Pool =====

  it should "raise error when pool is empty" in {
    val pool = RoundRobinExecutorPool.create.unsafeRunSync()
    val error = intercept[NoSuchElementException] {
      pool.next.unsafeRunSync()
    }
    error.getMessage should include("No healthy executors")
  }

  // ===== Remove =====

  it should "remove an endpoint and return false when pool is not empty" in {
    val pool = RoundRobinExecutorPool.create.unsafeRunSync()
    pool.add(ep("a")).unsafeRunSync()
    pool.add(ep("b")).unsafeRunSync()
    val isEmpty = pool.remove("a").unsafeRunSync()
    isEmpty shouldBe false
    pool.size.unsafeRunSync() shouldBe 1
  }

  it should "remove the last endpoint and return true" in {
    val pool = RoundRobinExecutorPool.withEndpoint(ep("a")).unsafeRunSync()
    val isEmpty = pool.remove("a").unsafeRunSync()
    isEmpty shouldBe true
    pool.size.unsafeRunSync() shouldBe 0
  }

  it should "return false when removing non-existent endpoint" in {
    val pool = RoundRobinExecutorPool.withEndpoint(ep("a")).unsafeRunSync()
    val isEmpty = pool.remove("nonexistent").unsafeRunSync()
    isEmpty shouldBe false
    pool.size.unsafeRunSync() shouldBe 1
  }

  // ===== Idempotent Add =====

  it should "replace endpoint with same connectionId on add" in {
    val pool = RoundRobinExecutorPool.create.unsafeRunSync()
    pool.add(ep("a", "host-old:9090")).unsafeRunSync()
    pool.add(ep("a", "host-new:9090")).unsafeRunSync()
    pool.size.unsafeRunSync() shouldBe 1
    pool.next.unsafeRunSync().executorUrl shouldBe "host-new:9090"
  }

  // ===== Endpoints Listing =====

  it should "return all endpoints" in {
    val pool = RoundRobinExecutorPool.create.unsafeRunSync()
    pool.add(ep("a")).unsafeRunSync()
    pool.add(ep("b")).unsafeRunSync()
    val eps = pool.endpoints.unsafeRunSync()
    eps.map(_.connectionId).toSet shouldBe Set("a", "b")
  }

  // ===== Round-Robin After Remove =====

  it should "continue round-robin after removing a member" in {
    val pool = RoundRobinExecutorPool.create.unsafeRunSync()
    pool.add(ep("a")).unsafeRunSync()
    pool.add(ep("b")).unsafeRunSync()
    pool.add(ep("c")).unsafeRunSync()

    // Advance to "b"
    pool.next.unsafeRunSync().connectionId shouldBe "a"
    pool.next.unsafeRunSync().connectionId shouldBe "b"

    // Remove "b"
    pool.remove("b").unsafeRunSync()

    // Pool is now [a, c] — next should continue cycling
    val results = (1 to 4).map(_ => pool.next.unsafeRunSync().connectionId).toList
    // After remove, index may land anywhere — just verify it cycles through a and c
    results.toSet shouldBe Set("a", "c")
    results.count(_ == "a") shouldBe 2
    results.count(_ == "c") shouldBe 2
  }
}
