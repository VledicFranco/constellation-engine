package io.constellation.lsp

import cats.effect.IO
import cats.effect.Ref
import cats.effect.unsafe.implicits.global
import cats.syntax.all._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class DebouncerTest extends AnyFlatSpec with Matchers {

  "Debouncer" should "delay execution by the configured duration" in {
    val result = (for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      beforeDelay <- counter.get
      _ <- IO.sleep(150.millis)
      afterDelay <- counter.get
    } yield {
      beforeDelay shouldBe 0 // Not executed yet
      afterDelay shouldBe 1  // Executed after delay
    }).unsafeRunSync()
  }

  it should "only execute once for rapid calls with the same key" in {
    val result = (for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      // Rapid calls - each should cancel the previous
      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      _ <- IO.sleep(30.millis)
      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      _ <- IO.sleep(30.millis)
      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      _ <- IO.sleep(30.millis)
      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      _ <- IO.sleep(30.millis)
      _ <- debouncer.debounce("key")(counter.update(_ + 1))

      // Wait for final execution
      _ <- IO.sleep(150.millis)
      count <- counter.get
    } yield {
      count shouldBe 1 // Only the last call should have executed
    }).unsafeRunSync()
  }

  it should "debounce different keys independently" in {
    val result = (for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      // Different keys should execute independently
      _ <- debouncer.debounce("key1")(counter.update(_ + 1))
      _ <- debouncer.debounce("key2")(counter.update(_ + 10))
      _ <- debouncer.debounce("key3")(counter.update(_ + 100))

      // Wait for all to execute
      _ <- IO.sleep(150.millis)
      count <- counter.get
    } yield {
      count shouldBe 111 // All three should execute (1 + 10 + 100)
    }).unsafeRunSync()
  }

  it should "cancel pending for a key when rapid calls and new call for different key" in {
    val result = (for {
      debouncer <- Debouncer.create[String](100.millis)
      counter1 <- Ref.of[IO, Int](0)
      counter2 <- Ref.of[IO, Int](0)

      // Call for key1, then rapidly call for key1 again (canceling first)
      _ <- debouncer.debounce("key1")(counter1.update(_ + 1))
      _ <- IO.sleep(50.millis)
      _ <- debouncer.debounce("key1")(counter1.update(_ + 1))

      // Call for key2 - should not affect key1
      _ <- debouncer.debounce("key2")(counter2.update(_ + 1))

      // Wait for both to complete
      _ <- IO.sleep(150.millis)
      count1 <- counter1.get
      count2 <- counter2.get
    } yield {
      count1 shouldBe 1 // key1 should only execute once (second call)
      count2 shouldBe 1 // key2 should execute independently
    }).unsafeRunSync()
  }

  it should "execute immediately when using immediate()" in {
    val result = (for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      // Schedule a debounced action
      _ <- debouncer.debounce("key")(counter.update(_ + 1))

      // Before the delay, execute immediately with a different action
      _ <- IO.sleep(50.millis)
      _ <- debouncer.immediate("key")(counter.update(_ + 10))

      // Check immediately after immediate()
      immediateResult <- counter.get

      // Wait to ensure the debounced action was cancelled
      _ <- IO.sleep(150.millis)
      finalResult <- counter.get
    } yield {
      immediateResult shouldBe 10  // Immediate action executed
      finalResult shouldBe 10      // Debounced action was cancelled
    }).unsafeRunSync()
  }

  it should "cancel pending action with cancel()" in {
    val result = (for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      // Schedule a debounced action
      _ <- debouncer.debounce("key")(counter.update(_ + 1))

      // Cancel before it executes
      _ <- IO.sleep(50.millis)
      _ <- debouncer.cancel("key")

      // Wait past when it would have executed
      _ <- IO.sleep(100.millis)
      count <- counter.get
    } yield {
      count shouldBe 0 // Action should never have executed
    }).unsafeRunSync()
  }

  it should "cancel all pending actions with cancelAll()" in {
    val result = (for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      // Schedule multiple debounced actions
      _ <- debouncer.debounce("key1")(counter.update(_ + 1))
      _ <- debouncer.debounce("key2")(counter.update(_ + 10))
      _ <- debouncer.debounce("key3")(counter.update(_ + 100))

      // Cancel all before any execute
      _ <- IO.sleep(50.millis)
      _ <- debouncer.cancelAll

      // Wait past when they would have executed
      _ <- IO.sleep(100.millis)
      count <- counter.get
    } yield {
      count shouldBe 0 // No actions should have executed
    }).unsafeRunSync()
  }

  it should "track pending count correctly" in {
    val result = (for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      initialCount <- debouncer.pendingCount

      _ <- debouncer.debounce("key1")(counter.update(_ + 1))
      _ <- debouncer.debounce("key2")(counter.update(_ + 1))
      countAfterScheduling <- debouncer.pendingCount

      _ <- IO.sleep(150.millis)
      countAfterExecution <- debouncer.pendingCount
    } yield {
      initialCount shouldBe 0
      countAfterScheduling shouldBe 2
      countAfterExecution shouldBe 0 // Cleaned up after execution
    }).unsafeRunSync()
  }

  it should "report hasPending correctly" in {
    val result = (for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      hasPendingBefore <- debouncer.hasPending("key")

      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      hasPendingDuring <- debouncer.hasPending("key")
      hasPendingOther <- debouncer.hasPending("other-key")

      _ <- IO.sleep(150.millis)
      hasPendingAfter <- debouncer.hasPending("key")
    } yield {
      hasPendingBefore shouldBe false
      hasPendingDuring shouldBe true
      hasPendingOther shouldBe false
      hasPendingAfter shouldBe false
    }).unsafeRunSync()
  }

  it should "use default delay when not specified" in {
    val result = (for {
      debouncer <- Debouncer.create[String]()
    } yield {
      debouncer.delay shouldBe Debouncer.DefaultDelay
      debouncer.delay shouldBe 200.millis
    }).unsafeRunSync()
  }

  it should "handle very short delays" in {
    val result = (for {
      debouncer <- Debouncer.create[String](10.millis)
      counter <- Ref.of[IO, Int](0)

      _ <- debouncer.debounce("key")(counter.update(_ + 1))
      _ <- IO.sleep(50.millis)
      count <- counter.get
    } yield {
      count shouldBe 1
    }).unsafeRunSync()
  }

  it should "handle action errors gracefully" in {
    val result = (for {
      debouncer <- Debouncer.create[String](50.millis)
      counter <- Ref.of[IO, Int](0)

      // Schedule an action that fails
      _ <- debouncer.debounce("key1")(IO.raiseError(new RuntimeException("test error")))

      // Schedule a successful action on different key
      _ <- debouncer.debounce("key2")(counter.update(_ + 1))

      // Wait for both
      _ <- IO.sleep(100.millis)
      count <- counter.get
    } yield {
      // key2 should still execute even if key1 failed
      count shouldBe 1
    }).unsafeRunSync()
  }

  it should "handle many rapid calls efficiently" in {
    val result = (for {
      debouncer <- Debouncer.create[String](100.millis)
      counter <- Ref.of[IO, Int](0)

      // Simulate 100 rapid calls (like fast typing)
      _ <- (1 to 100).toList.traverse_ { _ =>
        debouncer.debounce("key")(counter.update(_ + 1)) *> IO.sleep(5.millis)
      }

      // Wait for the final debounced action
      _ <- IO.sleep(150.millis)
      count <- counter.get
    } yield {
      // Should only execute once despite 100 calls
      count shouldBe 1
    }).unsafeRunSync()
  }
}
