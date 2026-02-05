package io.constellation.http

import java.time.Instant

import scala.concurrent.duration.*

import cats.effect.unsafe.implicits.global

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CanaryRouterTest extends AnyFlatSpec with Matchers {

  private def freshRouter: CanaryRouter =
    CanaryRouter.init.unsafeRunSync()

  private val oldVersion = PipelineVersion(1, "hash-old", Instant.now())
  private val newVersion = PipelineVersion(2, "hash-new", Instant.now())

  private val defaultConfig = CanaryConfig(
    initialWeight = 0.5,
    promotionSteps = List(0.5, 1.0),
    observationWindow = 0.seconds,
    errorThreshold = 0.05,
    minRequests = 2,
    autoPromote = true
  )

  "CanaryRouter.startCanary" should "create canary with initial weight" in {
    val router = freshRouter
    val result =
      router.startCanary("scoring", oldVersion, newVersion, defaultConfig).unsafeRunSync()

    result.isRight shouldBe true
    val state = result.toOption.get
    state.pipelineName shouldBe "scoring"
    state.currentWeight shouldBe 0.5
    state.currentStep shouldBe 0
    state.status shouldBe CanaryStatus.Observing
    state.oldVersion shouldBe oldVersion
    state.newVersion shouldBe newVersion
  }

  it should "return error if canary already active for same pipeline" in {
    val router = freshRouter
    router.startCanary("scoring", oldVersion, newVersion, defaultConfig).unsafeRunSync()

    val result =
      router.startCanary("scoring", oldVersion, newVersion, defaultConfig).unsafeRunSync()
    result.isLeft shouldBe true
    result.left.toOption.get should include("already active")
  }

  it should "allow canary for different pipelines" in {
    val router = freshRouter
    router
      .startCanary("scoring", oldVersion, newVersion, defaultConfig)
      .unsafeRunSync()
      .isRight shouldBe true
    router
      .startCanary("text", oldVersion, newVersion, defaultConfig)
      .unsafeRunSync()
      .isRight shouldBe true
  }

  it should "allow new canary after previous one was rolled back" in {
    val router = freshRouter
    router.startCanary("scoring", oldVersion, newVersion, defaultConfig).unsafeRunSync()
    router.rollback("scoring").unsafeRunSync()

    val result =
      router.startCanary("scoring", oldVersion, newVersion, defaultConfig).unsafeRunSync()
    result.isRight shouldBe true
  }

  "CanaryRouter.selectVersion" should "return None when no canary active" in {
    val router = freshRouter
    router.selectVersion("scoring").unsafeRunSync() shouldBe None
  }

  it should "return one of old or new hash when canary active" in {
    val router = freshRouter
    router.startCanary("scoring", oldVersion, newVersion, defaultConfig).unsafeRunSync()

    // With 50% weight, should get both hashes over many attempts
    val results = (1 to 100).map(_ => router.selectVersion("scoring").unsafeRunSync())
    results.forall(_.isDefined) shouldBe true
    val hashes = results.map(_.get).toSet
    hashes should contain(oldVersion.structuralHash)
    hashes should contain(newVersion.structuralHash)
  }

  it should "return None after rollback" in {
    val router = freshRouter
    router.startCanary("scoring", oldVersion, newVersion, defaultConfig).unsafeRunSync()
    router.rollback("scoring").unsafeRunSync()

    router.selectVersion("scoring").unsafeRunSync() shouldBe None
  }

  it should "return None after canary completed" in {
    val router = freshRouter
    val config =
      defaultConfig.copy(promotionSteps = List(1.0), minRequests = 1, observationWindow = 0.seconds)
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    // Record enough successful requests to trigger promotion through all steps
    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()

    router.selectVersion("scoring").unsafeRunSync() shouldBe None
  }

  "CanaryRouter.recordResult" should "increment metrics correctly" in {
    val router = freshRouter
    val config = defaultConfig.copy(autoPromote = false)
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()
    router.recordResult("scoring", newVersion.structuralHash, success = false, 20.0).unsafeRunSync()
    router.recordResult("scoring", oldVersion.structuralHash, success = true, 5.0).unsafeRunSync()

    val state = router.getState("scoring").unsafeRunSync().get
    state.metrics.newVersion.requests shouldBe 2
    state.metrics.newVersion.successes shouldBe 1
    state.metrics.newVersion.failures shouldBe 1
    state.metrics.oldVersion.requests shouldBe 1
    state.metrics.oldVersion.successes shouldBe 1
  }

  it should "return None for unknown pipeline" in {
    val router = freshRouter
    router.recordResult("nonexistent", "hash", success = true, 10.0).unsafeRunSync() shouldBe None
  }

  it should "trigger auto-rollback when error threshold exceeded" in {
    val router = freshRouter
    val config = defaultConfig.copy(
      errorThreshold = 0.05,
      minRequests = 2,
      observationWindow = 0.seconds,
      autoPromote = true
    )
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    // Record failures exceeding threshold
    router.recordResult("scoring", newVersion.structuralHash, success = false, 10.0).unsafeRunSync()
    val result = router
      .recordResult("scoring", newVersion.structuralHash, success = false, 10.0)
      .unsafeRunSync()

    result.get.status shouldBe CanaryStatus.RolledBack
  }

  it should "trigger auto-promote when healthy and thresholds met" in {
    val router = freshRouter
    val config = defaultConfig.copy(
      promotionSteps = List(0.5, 1.0),
      minRequests = 2,
      observationWindow = 0.seconds,
      autoPromote = true
    )
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    // Record enough successes
    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()
    val result = router
      .recordResult("scoring", newVersion.structuralHash, success = true, 10.0)
      .unsafeRunSync()

    // Should advance to step 1 (weight 0.5 -> next step)
    result.get.currentStep shouldBe 1
    result.get.status shouldBe CanaryStatus.Observing
  }

  it should "not promote before minRequests met" in {
    val router = freshRouter
    val config = defaultConfig.copy(
      minRequests = 5,
      observationWindow = 0.seconds,
      autoPromote = true
    )
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    // Record fewer than minRequests
    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()
    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()

    val state = router.getState("scoring").unsafeRunSync().get
    state.currentStep shouldBe 0 // Not advanced
    state.status shouldBe CanaryStatus.Observing
  }

  it should "not promote before observationWindow elapsed" in {
    val router = freshRouter
    val config = defaultConfig.copy(
      minRequests = 1,
      observationWindow = 1.hour, // Very long — won't elapse during test
      autoPromote = true
    )
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()
    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()
    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()

    val state = router.getState("scoring").unsafeRunSync().get
    state.currentStep shouldBe 0 // Not advanced — window hasn't elapsed
    state.status shouldBe CanaryStatus.Observing
  }

  it should "reset newVersion metrics on step advancement" in {
    val router = freshRouter
    val config = defaultConfig.copy(
      promotionSteps = List(0.5, 1.0),
      minRequests = 1,
      observationWindow = 0.seconds,
      autoPromote = true
    )
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    // Record to trigger first promotion
    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()

    val state = router.getState("scoring").unsafeRunSync().get
    state.currentStep shouldBe 1
    state.metrics.newVersion.requests shouldBe 0 // Reset on step advance
  }

  it should "mark Complete when final step reached" in {
    val router = freshRouter
    val config = defaultConfig.copy(
      promotionSteps = List(1.0),
      minRequests = 1,
      observationWindow = 0.seconds,
      autoPromote = true
    )
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    val result = router
      .recordResult("scoring", newVersion.structuralHash, success = true, 10.0)
      .unsafeRunSync()
    result.get.status shouldBe CanaryStatus.Complete
    result.get.currentWeight shouldBe 1.0
  }

  it should "trigger rollback on p99 latency threshold exceeded" in {
    val router = freshRouter
    val config = defaultConfig.copy(
      latencyThresholdMs = Some(50),
      minRequests = 2,
      observationWindow = 0.seconds,
      autoPromote = true
    )
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    // Record high latency
    router.recordResult("scoring", newVersion.structuralHash, success = true, 100.0).unsafeRunSync()
    val result = router
      .recordResult("scoring", newVersion.structuralHash, success = true, 100.0)
      .unsafeRunSync()

    result.get.status shouldBe CanaryStatus.RolledBack
  }

  it should "not auto-advance when autoPromote is false" in {
    val router = freshRouter
    val config = defaultConfig.copy(
      minRequests = 1,
      observationWindow = 0.seconds,
      autoPromote = false
    )
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()
    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()
    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()

    val state = router.getState("scoring").unsafeRunSync().get
    state.currentStep shouldBe 0
    state.status shouldBe CanaryStatus.Observing
  }

  "CanaryRouter.getState" should "return current state" in {
    val router = freshRouter
    router.startCanary("scoring", oldVersion, newVersion, defaultConfig).unsafeRunSync()

    val state = router.getState("scoring").unsafeRunSync()
    state shouldBe defined
    state.get.pipelineName shouldBe "scoring"
  }

  it should "return None for unknown pipeline" in {
    val router = freshRouter
    router.getState("nonexistent").unsafeRunSync() shouldBe None
  }

  "CanaryRouter.promote" should "manually advance step" in {
    val router = freshRouter
    val config = defaultConfig.copy(
      promotionSteps = List(0.25, 0.50, 1.0),
      autoPromote = false
    )
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    val result = router.promote("scoring").unsafeRunSync()
    result.get.currentStep shouldBe 1
    result.get.currentWeight shouldBe 0.50
    result.get.status shouldBe CanaryStatus.Observing
  }

  it should "mark Complete when promoting past final step" in {
    val router = freshRouter
    val config = defaultConfig.copy(
      promotionSteps = List(1.0),
      autoPromote = false
    )
    router.startCanary("scoring", oldVersion, newVersion, config).unsafeRunSync()

    val result = router.promote("scoring").unsafeRunSync()
    result.get.status shouldBe CanaryStatus.Complete
    result.get.currentWeight shouldBe 1.0
  }

  it should "return None for unknown pipeline" in {
    val router = freshRouter
    router.promote("nonexistent").unsafeRunSync() shouldBe None
  }

  "CanaryRouter.rollback" should "revert to old version" in {
    val router = freshRouter
    router.startCanary("scoring", oldVersion, newVersion, defaultConfig).unsafeRunSync()

    val result = router.rollback("scoring").unsafeRunSync()
    result.get.status shouldBe CanaryStatus.RolledBack
  }

  it should "return None for unknown pipeline" in {
    val router = freshRouter
    router.rollback("nonexistent").unsafeRunSync() shouldBe None
  }

  "CanaryRouter.abort" should "be the same as rollback" in {
    val router = freshRouter
    router.startCanary("scoring", oldVersion, newVersion, defaultConfig).unsafeRunSync()

    val result = router.abort("scoring").unsafeRunSync()
    result.get.status shouldBe CanaryStatus.RolledBack
  }

  "Multiple pipelines" should "have independent canaries" in {
    val router   = freshRouter
    val otherOld = PipelineVersion(1, "hash-other-old", Instant.now())
    val otherNew = PipelineVersion(2, "hash-other-new", Instant.now())

    router.startCanary("scoring", oldVersion, newVersion, defaultConfig).unsafeRunSync()
    router.startCanary("text", otherOld, otherNew, defaultConfig).unsafeRunSync()

    router.recordResult("scoring", newVersion.structuralHash, success = true, 10.0).unsafeRunSync()

    val scoringState = router.getState("scoring").unsafeRunSync().get
    val textState    = router.getState("text").unsafeRunSync().get

    scoringState.metrics.newVersion.requests shouldBe 1
    textState.metrics.newVersion.requests shouldBe 0
  }

  "VersionMetrics" should "calculate error rate correctly" in {
    val vm = VersionMetrics(requests = 10, successes = 8, failures = 2)
    vm.errorRate shouldBe 0.2 +- 0.001
  }

  it should "calculate p99 latency correctly" in {
    val latencies = (1 to 100).map(_.toDouble).toVector
    val vm        = VersionMetrics(requests = 100, latencies = latencies)
    // ceil(100 * 0.99) - 1 = 98 → value at index 98 is 99.0
    vm.p99LatencyMs shouldBe 99.0 +- 0.1
  }

  it should "handle empty latencies" in {
    val vm = VersionMetrics()
    vm.p99LatencyMs shouldBe 0.0
    vm.avgLatencyMs shouldBe 0.0
    vm.errorRate shouldBe 0.0
  }
}
