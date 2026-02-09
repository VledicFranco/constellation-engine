package io.constellation.execution

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

// ============================================================================
// PriorityLevel - Comprehensive Tests
// ============================================================================

class PriorityLevelValueSpec extends AnyFlatSpec with Matchers {

  "PriorityLevel.Critical" should "have value 100" in {
    PriorityLevel.Critical.value shouldBe 100
  }

  "PriorityLevel.High" should "have value 75" in {
    PriorityLevel.High.value shouldBe 75
  }

  "PriorityLevel.Normal" should "have value 50" in {
    PriorityLevel.Normal.value shouldBe 50
  }

  "PriorityLevel.Low" should "have value 25" in {
    PriorityLevel.Low.value shouldBe 25
  }

  "PriorityLevel.Background" should "have value 0" in {
    PriorityLevel.Background.value shouldBe 0
  }

  "PriorityLevel.Custom" should "have the provided value" in {
    PriorityLevel.Custom(42).value shouldBe 42
  }
}

class PriorityLevelFromStringSpec extends AnyFlatSpec with Matchers {

  "PriorityLevel.fromString" should "parse 'critical'" in {
    PriorityLevel.fromString("critical") shouldBe Some(PriorityLevel.Critical)
  }

  it should "parse 'high'" in {
    PriorityLevel.fromString("high") shouldBe Some(PriorityLevel.High)
  }

  it should "parse 'normal'" in {
    PriorityLevel.fromString("normal") shouldBe Some(PriorityLevel.Normal)
  }

  it should "parse 'low'" in {
    PriorityLevel.fromString("low") shouldBe Some(PriorityLevel.Low)
  }

  it should "parse 'background'" in {
    PriorityLevel.fromString("background") shouldBe Some(PriorityLevel.Background)
  }

  it should "parse an integer string as Custom" in {
    PriorityLevel.fromString("42") shouldBe Some(PriorityLevel.Custom(42))
  }

  it should "return None for invalid strings" in {
    PriorityLevel.fromString("invalid") shouldBe None
  }

  it should "be case insensitive for named levels" in {
    PriorityLevel.fromString("CRITICAL") shouldBe Some(PriorityLevel.Critical)
    PriorityLevel.fromString("High") shouldBe Some(PriorityLevel.High)
    PriorityLevel.fromString("NORMAL") shouldBe Some(PriorityLevel.Normal)
    PriorityLevel.fromString("LOW") shouldBe Some(PriorityLevel.Low)
    PriorityLevel.fromString("BACKGROUND") shouldBe Some(PriorityLevel.Background)
  }
}

class PriorityLevelOrderingSpec extends AnyFlatSpec with Matchers {

  "PriorityLevel ordering" should "rank Critical > High > Normal > Low > Background" in {
    val levels = List(
      PriorityLevel.Background,
      PriorityLevel.Normal,
      PriorityLevel.Critical,
      PriorityLevel.Low,
      PriorityLevel.High
    )

    val sorted = levels.sorted(PriorityLevel.ordering)

    sorted shouldBe List(
      PriorityLevel.Critical,
      PriorityLevel.High,
      PriorityLevel.Normal,
      PriorityLevel.Low,
      PriorityLevel.Background
    )
  }
}

// ============================================================================
// PrioritizedTask - Ordering Tests
// ============================================================================

class PrioritizedTaskOrderingSpec extends AnyFlatSpec with Matchers {

  "PrioritizedTask ordering" should "sort higher priority tasks first" in {
    val highTask = PrioritizedTask(id = 1, priority = 80, submittedAt = 100L, task = IO.pure(1))
    val lowTask  = PrioritizedTask(id = 2, priority = 20, submittedAt = 100L, task = IO.pure(2))

    val sorted = List(lowTask, highTask).sorted

    sorted.map(_.id) shouldBe List(1, 2) // highTask (80) before lowTask (20)
  }

  it should "sort earlier submitted tasks first when priority is equal" in {
    val earlier = PrioritizedTask(id = 1, priority = 50, submittedAt = 100L, task = IO.pure(1))
    val later   = PrioritizedTask(id = 2, priority = 50, submittedAt = 200L, task = IO.pure(2))

    val sorted = List(later, earlier).sorted

    sorted.map(_.id) shouldBe List(1, 2) // earlier (100L) before later (200L)
  }
}

// ============================================================================
// PriorityStats - Comprehensive Tests
// ============================================================================

class PriorityStatsSpec extends AnyFlatSpec with Matchers {

  "PriorityStats.empty" should "have all zeros" in {
    val empty = PriorityStats.empty

    empty.submitted shouldBe 0
    empty.completed shouldBe 0
    empty.totalDurationMs shouldBe 0
  }

  "PriorityStats.recordSubmission" should "increment submitted count" in {
    val stats = PriorityStats.empty.recordSubmission

    stats.submitted shouldBe 1
    stats.completed shouldBe 0
    stats.totalDurationMs shouldBe 0
  }

  "PriorityStats.recordCompletion" should "increment completed and accumulate duration" in {
    val stats = PriorityStats.empty
      .recordSubmission
      .recordCompletion(150L)

    stats.submitted shouldBe 1
    stats.completed shouldBe 1
    stats.totalDurationMs shouldBe 150L
  }

  "PriorityStats.avgDurationMs" should "return 0 when no completions" in {
    PriorityStats.empty.avgDurationMs shouldBe 0
  }

  it should "return the correct average duration" in {
    val stats = PriorityStats.empty
      .recordSubmission
      .recordCompletion(100L)
      .recordSubmission
      .recordCompletion(200L)

    stats.avgDurationMs shouldBe 150L
  }

  "PriorityStats.pendingCount" should "return submitted minus completed" in {
    val stats = PriorityStats(submitted = 5, completed = 3, totalDurationMs = 0)

    stats.pendingCount shouldBe 2
  }
}

// ============================================================================
// PrioritySchedulerStats - Comprehensive Tests
// ============================================================================

class PrioritySchedulerStatsSpec extends AnyFlatSpec with Matchers {

  "PrioritySchedulerStats.empty" should "have all zeros and empty map" in {
    val empty = PrioritySchedulerStats.empty

    empty.totalSubmitted shouldBe 0
    empty.totalCompleted shouldBe 0
    empty.byPriority shouldBe Map.empty
  }

  "PrioritySchedulerStats.recordSubmission" should "update total and per-priority counts" in {
    val stats = PrioritySchedulerStats.empty
      .recordSubmission(PriorityLevel.Normal)

    stats.totalSubmitted shouldBe 1
    stats.totalCompleted shouldBe 0
    stats.byPriority.get(50) shouldBe defined
    stats.byPriority(50).submitted shouldBe 1
  }

  "PrioritySchedulerStats.recordCompletion" should "update total and per-priority counts" in {
    val stats = PrioritySchedulerStats.empty
      .recordSubmission(PriorityLevel.High)
      .recordCompletion(PriorityLevel.High, 100L)

    stats.totalSubmitted shouldBe 1
    stats.totalCompleted shouldBe 1
    stats.byPriority(75).completed shouldBe 1
    stats.byPriority(75).totalDurationMs shouldBe 100L
  }

  "PrioritySchedulerStats.forPriority" should "return None for unused priority" in {
    val stats = PrioritySchedulerStats.empty

    stats.forPriority(PriorityLevel.Critical) shouldBe None
  }

  it should "return Some for a used priority" in {
    val stats = PrioritySchedulerStats.empty
      .recordSubmission(PriorityLevel.Low)

    stats.forPriority(PriorityLevel.Low) shouldBe defined
    stats.forPriority(PriorityLevel.Low).get.submitted shouldBe 1
  }

  "PrioritySchedulerStats.completionRate" should "return 1.0 when no tasks submitted" in {
    PrioritySchedulerStats.empty.completionRate shouldBe 1.0
  }

  it should "return correct ratio of completed to submitted" in {
    val stats = PrioritySchedulerStats(
      totalSubmitted = 10,
      totalCompleted = 7,
      byPriority = Map.empty
    )

    stats.completionRate shouldBe 0.7
  }

  "PrioritySchedulerStats.toString" should "contain formatted output" in {
    val stats = PrioritySchedulerStats.empty
      .recordSubmission(PriorityLevel.Normal)
      .recordCompletion(PriorityLevel.Normal, 42L)

    val output = stats.toString

    output should include("submitted=1")
    output should include("completed=1")
    output should include("priority=50")
  }
}

// ============================================================================
// PriorityScheduler - Comprehensive Tests
// ============================================================================

class PrioritySchedulerSpec extends AnyFlatSpec with Matchers {

  "PriorityScheduler.create" should "return a scheduler" in {
    val scheduler = PriorityScheduler.create.unsafeRunSync()

    scheduler should not be null
  }

  "PriorityScheduler.submit (default)" should "execute a task with Normal priority" in {
    val result = (for {
      scheduler <- PriorityScheduler.create
      result    <- scheduler.submit(IO.pure(42))
    } yield result).unsafeRunSync()

    result shouldBe 42
  }

  "PriorityScheduler.submit (explicit)" should "execute a task with explicit priority" in {
    val result = (for {
      scheduler <- PriorityScheduler.create
      result    <- scheduler.submit(IO.pure("hello"), PriorityLevel.Critical)
    } yield result).unsafeRunSync()

    result shouldBe "hello"
  }

  "PriorityScheduler stats tracking" should "record stats correctly" in {
    val stats = (for {
      scheduler <- PriorityScheduler.create
      _         <- scheduler.submit(IO.pure(1))
      _         <- scheduler.submit(IO.pure(2))
      _         <- scheduler.submit(IO.pure(3))
      stats     <- scheduler.stats
    } yield stats).unsafeRunSync()

    stats.totalSubmitted shouldBe 3
    stats.totalCompleted shouldBe 3
    stats.completionRate shouldBe 1.0
  }

  it should "record per-priority stats for different priorities" in {
    val stats = (for {
      scheduler <- PriorityScheduler.create
      _         <- scheduler.submit(IO.pure(1), PriorityLevel.Critical)
      _         <- scheduler.submit(IO.pure(2), PriorityLevel.Low)
      _         <- scheduler.submit(IO.pure(3), PriorityLevel.Background)
      stats     <- scheduler.stats
    } yield stats).unsafeRunSync()

    stats.totalSubmitted shouldBe 3
    stats.totalCompleted shouldBe 3
    stats.forPriority(PriorityLevel.Critical) shouldBe defined
    stats.forPriority(PriorityLevel.Critical).get.submitted shouldBe 1
    stats.forPriority(PriorityLevel.Critical).get.completed shouldBe 1
    stats.forPriority(PriorityLevel.Low) shouldBe defined
    stats.forPriority(PriorityLevel.Low).get.submitted shouldBe 1
    stats.forPriority(PriorityLevel.Background) shouldBe defined
    stats.forPriority(PriorityLevel.Background).get.submitted shouldBe 1
    stats.forPriority(PriorityLevel.Normal) shouldBe None
  }

  "PriorityScheduler.resetStats" should "clear all stats" in {
    val stats = (for {
      scheduler <- PriorityScheduler.create
      _         <- scheduler.submit(IO.pure(1), PriorityLevel.High)
      _         <- scheduler.submit(IO.pure(2), PriorityLevel.Low)
      _         <- scheduler.resetStats
      stats     <- scheduler.stats
    } yield stats).unsafeRunSync()

    stats.totalSubmitted shouldBe 0
    stats.totalCompleted shouldBe 0
    stats.byPriority shouldBe Map.empty
  }

  "PriorityScheduler error handling" should "propagate task errors" in {
    val error = intercept[RuntimeException] {
      (for {
        scheduler <- PriorityScheduler.create
        _         <- scheduler.submit(IO.raiseError[Int](new RuntimeException("boom")))
      } yield ()).unsafeRunSync()
    }

    error.getMessage shouldBe "boom"
  }

  it should "still record completion stats when a task fails" in {
    val stats = (for {
      scheduler <- PriorityScheduler.create
      _ <- scheduler
        .submit(IO.raiseError[Int](new RuntimeException("fail")), PriorityLevel.Normal)
        .attempt
      stats <- scheduler.stats
    } yield stats).unsafeRunSync()

    stats.totalSubmitted shouldBe 1
    stats.totalCompleted shouldBe 1
  }

  "PriorityScheduler default priority" should "use Normal priority for the default submit overload" in {
    val stats = (for {
      scheduler <- PriorityScheduler.create
      _         <- scheduler.submit(IO.pure("test"))
      stats     <- scheduler.stats
    } yield stats).unsafeRunSync()

    stats.forPriority(PriorityLevel.Normal) shouldBe defined
    stats.forPriority(PriorityLevel.Normal).get.submitted shouldBe 1
  }
}
