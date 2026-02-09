package io.constellation.execution

import scala.concurrent.duration.*

import cats.effect.{Deferred, IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.implicits.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class LazyValueExtendedTest extends AnyFlatSpec with Matchers {

  // -------------------------------------------------------------------------
  // LazyValue.apply
  // -------------------------------------------------------------------------

  "LazyValue.apply" should "create a lazy value in Pending state" in {
    val result = (for {
      lv         <- LazyValue(IO.pure(42))
      computed   <- lv.isComputed
      computing  <- lv.isComputing
      peeked     <- lv.peek
    } yield (computed, computing, peeked)).unsafeRunSync()

    result._1 shouldBe false
    result._2 shouldBe false
    result._3 shouldBe None
  }

  // -------------------------------------------------------------------------
  // force
  // -------------------------------------------------------------------------

  "force" should "compute value on first call and return cached on subsequent calls" in {
    val result = (for {
      lv     <- LazyValue(IO.pure("hello"))
      first  <- lv.force
      second <- lv.force
      third  <- lv.force
    } yield (first, second, third)).unsafeRunSync()

    result._1 shouldBe "hello"
    result._2 shouldBe "hello"
    result._3 shouldBe "hello"
  }

  it should "only run computation once even when forced multiple times" in {
    val result = (for {
      counter <- Ref.of[IO, Int](0)
      lv      <- LazyValue(counter.update(_ + 1) >> IO.pure("value"))
      _       <- lv.force
      _       <- lv.force
      _       <- lv.force
      count   <- counter.get
    } yield count).unsafeRunSync()

    result shouldBe 1
  }

  it should "reset to Pending and re-raise error on computation failure" in {
    val error = new RuntimeException("boom")
    val result = (for {
      lv        <- LazyValue(IO.raiseError[String](error))
      attempt   <- lv.force.attempt
      computed  <- lv.isComputed
      computing <- lv.isComputing
    } yield (attempt, computed, computing)).unsafeRunSync()

    result._1.isLeft shouldBe true
    result._1.left.toOption.get.getMessage shouldBe "boom"
    result._2 shouldBe false
    result._3 shouldBe false
  }

  it should "allow retry after error and recompute value" in {
    val result = (for {
      counter <- Ref.of[IO, Int](0)
      lv <- LazyValue {
        counter.updateAndGet(_ + 1).flatMap { n =>
          if (n == 1) IO.raiseError[String](new RuntimeException("first call fails"))
          else IO.pure(s"ok-$n")
        }
      }
      first  <- lv.force.attempt
      second <- lv.force
    } yield (first, second)).unsafeRunSync()

    result._1.isLeft shouldBe true
    result._2 shouldBe "ok-2"
  }

  // -------------------------------------------------------------------------
  // isComputed
  // -------------------------------------------------------------------------

  "isComputed" should "be false before force and true after force" in {
    val result = (for {
      lv     <- LazyValue(IO.pure(99))
      before <- lv.isComputed
      _      <- lv.force
      after  <- lv.isComputed
    } yield (before, after)).unsafeRunSync()

    result._1 shouldBe false
    result._2 shouldBe true
  }

  // -------------------------------------------------------------------------
  // isComputing
  // -------------------------------------------------------------------------

  "isComputing" should "be true while computation is in progress" in {
    val result = (for {
      gate   <- Deferred[IO, Unit]
      lv     <- LazyValue(gate.get >> IO.pure("done"))
      fiber  <- lv.force.start
      _      <- IO.sleep(50.millis)
      during <- lv.isComputing
      _      <- gate.complete(())
      value  <- fiber.joinWithNever
      after  <- lv.isComputing
    } yield (during, after, value)).unsafeRunSync()

    result._1 shouldBe true
    result._2 shouldBe false
    result._3 shouldBe "done"
  }

  // -------------------------------------------------------------------------
  // peek
  // -------------------------------------------------------------------------

  "peek" should "return None before force and Some(value) after force" in {
    val result = (for {
      lv     <- LazyValue(IO.pure("peeked"))
      before <- lv.peek
      _      <- lv.force
      after  <- lv.peek
    } yield (before, after)).unsafeRunSync()

    result._1 shouldBe None
    result._2 shouldBe Some("peeked")
  }

  // -------------------------------------------------------------------------
  // reset
  // -------------------------------------------------------------------------

  "reset" should "revert to Pending after force and force recomputation" in {
    val result = (for {
      counter <- Ref.of[IO, Int](0)
      lv      <- LazyValue(counter.updateAndGet(_ + 1))
      first   <- lv.force
      _       <- lv.reset
      peeked  <- lv.peek
      computed <- lv.isComputed
      second  <- lv.force
      count   <- counter.get
    } yield (first, peeked, computed, second, count)).unsafeRunSync()

    result._1 shouldBe 1
    result._2 shouldBe None
    result._3 shouldBe false
    result._4 shouldBe 2
    result._5 shouldBe 2
  }

  it should "wait for in-progress computation then reset" in {
    val result = (for {
      gate    <- Deferred[IO, Unit]
      counter <- Ref.of[IO, Int](0)
      lv      <- LazyValue(gate.get >> counter.updateAndGet(_ + 1))
      fiber   <- lv.force.start
      _       <- IO.sleep(50.millis)
      _       <- gate.complete(())
      _       <- lv.reset
      peeked  <- lv.peek
      second  <- lv.force
      count   <- counter.get
    } yield (peeked, second, count)).unsafeRunSync()

    result._1 shouldBe None
    result._2 shouldBe 2
    result._3 shouldBe 2
  }

  // -------------------------------------------------------------------------
  // map
  // -------------------------------------------------------------------------

  "map" should "transform the value lazily" in {
    val result = (for {
      lv      <- LazyValue(IO.pure(10))
      mapped  <- lv.map(_ * 2)
      before  <- mapped.isComputed
      value   <- mapped.force
      after   <- mapped.isComputed
    } yield (before, value, after)).unsafeRunSync()

    result._1 shouldBe false
    result._2 shouldBe 20
    result._3 shouldBe true
  }

  // -------------------------------------------------------------------------
  // flatMap
  // -------------------------------------------------------------------------

  "flatMap" should "transform the value with IO" in {
    val result = (for {
      lv         <- LazyValue(IO.pure(5))
      flatMapped <- lv.flatMap(n => IO.pure(n.toString + "!"))
      before     <- flatMapped.isComputed
      value      <- flatMapped.force
      after      <- flatMapped.isComputed
    } yield (before, value, after)).unsafeRunSync()

    result._1 shouldBe false
    result._2 shouldBe "5!"
    result._3 shouldBe true
  }

  // -------------------------------------------------------------------------
  // LazyValue.pure
  // -------------------------------------------------------------------------

  "LazyValue.pure" should "create an already-computed value" in {
    val result = (for {
      lv    <- LazyValue.pure("already")
      value <- lv.force
    } yield value).unsafeRunSync()

    result shouldBe "already"
  }

  it should "have isComputed true immediately and force returns value without delay" in {
    val result = (for {
      lv       <- LazyValue.pure(42)
      computed <- lv.isComputed
      peeked   <- lv.peek
      value    <- lv.force
    } yield (computed, peeked, value)).unsafeRunSync()

    result._1 shouldBe true
    result._2 shouldBe Some(42)
    result._3 shouldBe 42
  }

  // -------------------------------------------------------------------------
  // LazyValue.raiseError
  // -------------------------------------------------------------------------

  "LazyValue.raiseError" should "create a value that fails on force" in {
    val error = new RuntimeException("error value")
    val result = (for {
      lv      <- LazyValue.raiseError[String](error)
      attempt <- lv.force.attempt
    } yield attempt).unsafeRunSync()

    result.isLeft shouldBe true
    result.left.toOption.get.getMessage shouldBe "error value"
  }

  // -------------------------------------------------------------------------
  // LazyValue.sequence
  // -------------------------------------------------------------------------

  "LazyValue.sequence" should "force all values and return list" in {
    val result = (for {
      lv1    <- LazyValue(IO.pure(1))
      lv2    <- LazyValue(IO.pure(2))
      lv3    <- LazyValue(IO.pure(3))
      values <- LazyValue.sequence(List(lv1, lv2, lv3))
    } yield values).unsafeRunSync()

    result shouldBe List(1, 2, 3)
  }

  it should "return empty list for empty input" in {
    val result = LazyValue.sequence(List.empty).unsafeRunSync()
    result shouldBe List.empty
  }

  // -------------------------------------------------------------------------
  // LazyValue.fromList
  // -------------------------------------------------------------------------

  "LazyValue.fromList" should "create lazy values from IO list with none forced yet" in {
    val result = (for {
      counter <- Ref.of[IO, Int](0)
      computations = List(
        counter.update(_ + 1) >> IO.pure("a"),
        counter.update(_ + 1) >> IO.pure("b"),
        counter.update(_ + 1) >> IO.pure("c")
      )
      lazies     <- LazyValue.fromList(computations)
      countAfter <- counter.get
      allPending <- lazies.traverse(_.isComputed)
    } yield (lazies.size, countAfter, allPending)).unsafeRunSync()

    result._1 shouldBe 3
    result._2 shouldBe 0
    result._3 shouldBe List(false, false, false)
  }

  // -------------------------------------------------------------------------
  // Concurrent access
  // -------------------------------------------------------------------------

  "concurrent access" should "only run computation once even with multiple fibers" in {
    val result = (for {
      counter <- Ref.of[IO, Int](0)
      lv      <- LazyValue(IO.sleep(50.millis) >> counter.updateAndGet(_ + 1).map(_.toString))
      fibers  <- (1 to 20).toList.traverse(_ => lv.force.start)
      results <- fibers.traverse(_.joinWithNever)
      count   <- counter.get
    } yield (results, count)).unsafeRunSync()

    result._1.distinct.size shouldBe 1
    result._1.head shouldBe "1"
    result._2 shouldBe 1
  }
}
