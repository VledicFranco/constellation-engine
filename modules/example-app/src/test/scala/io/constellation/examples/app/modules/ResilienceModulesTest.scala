package io.constellation.examples.app.modules

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ResilienceModulesTest extends AnyFlatSpec with Matchers {

  // Module spec tests

  "SlowQuery" should "have correct spec name" in {
    ResilienceModules.slowQuery.spec.name shouldBe "SlowQuery"
  }

  it should "have description mentioning 500ms latency" in {
    ResilienceModules.slowQuery.spec.description should include("500ms")
  }

  it should "have resilience and cache-demo tags" in {
    ResilienceModules.slowQuery.spec.tags should contain allOf ("resilience", "slow", "cache-demo")
  }

  "SlowApiCall" should "have correct spec name" in {
    ResilienceModules.slowApiCall.spec.name shouldBe "SlowApiCall"
  }

  it should "have description mentioning 1 second latency" in {
    ResilienceModules.slowApiCall.spec.description should include("1 second")
  }

  it should "have timeout-demo tag" in {
    ResilienceModules.slowApiCall.spec.tags should contain("timeout-demo")
  }

  "ExpensiveCompute" should "have correct spec name" in {
    ResilienceModules.expensiveCompute.spec.name shouldBe "ExpensiveCompute"
  }

  it should "have description mentioning 2 second processing" in {
    ResilienceModules.expensiveCompute.spec.description should include("2 second")
  }

  it should "have lazy-demo tag" in {
    ResilienceModules.expensiveCompute.spec.tags should contain("lazy-demo")
  }

  "FlakyService" should "have correct spec name" in {
    ResilienceModules.flakyService.spec.name shouldBe "FlakyService"
  }

  it should "have description mentioning retry" in {
    ResilienceModules.flakyService.spec.description should include("retry")
  }

  it should "have retry-demo tag" in {
    ResilienceModules.flakyService.spec.tags should contain("retry-demo")
  }

  "TimeoutProneService" should "have correct spec name" in {
    ResilienceModules.timeoutProneService.spec.name shouldBe "TimeoutProneService"
  }

  it should "have timeout-demo and retry-demo tags" in {
    ResilienceModules.timeoutProneService.spec.tags should contain allOf ("timeout-demo", "retry-demo")
  }

  "RateLimitedApi" should "have correct spec name" in {
    ResilienceModules.rateLimitedApi.spec.name shouldBe "RateLimitedApi"
  }

  it should "have throttle-demo tag" in {
    ResilienceModules.rateLimitedApi.spec.tags should contain("throttle-demo")
  }

  "ResourceIntensiveTask" should "have correct spec name" in {
    ResilienceModules.resourceIntensiveTask.spec.name shouldBe "ResourceIntensiveTask"
  }

  it should "have concurrency-demo tag" in {
    ResilienceModules.resourceIntensiveTask.spec.tags should contain("concurrency-demo")
  }

  "QuickCheck" should "have correct spec name" in {
    ResilienceModules.quickCheck.spec.name shouldBe "QuickCheck"
  }

  it should "have lazy-demo and priority-demo tags" in {
    ResilienceModules.quickCheck.spec.tags should contain allOf ("lazy-demo", "priority-demo")
  }

  "DeepAnalysis" should "have correct spec name" in {
    ResilienceModules.deepAnalysis.spec.name shouldBe "DeepAnalysis"
  }

  it should "have slow and lazy-demo tags" in {
    ResilienceModules.deepAnalysis.spec.tags should contain allOf ("slow", "lazy-demo")
  }

  "AlwaysFailsService" should "have correct spec name" in {
    ResilienceModules.alwaysFailsService.spec.name shouldBe "AlwaysFailsService"
  }

  it should "have error-demo tag" in {
    ResilienceModules.alwaysFailsService.spec.tags should contain("error-demo")
  }

  // Collection tests

  "ResilienceModules.all" should "contain exactly 10 modules" in {
    ResilienceModules.all should have size 10
  }

  it should "contain all resilience modules" in {
    val names = ResilienceModules.all.map(_.spec.name)
    names should contain allOf (
      "SlowQuery", "SlowApiCall", "ExpensiveCompute",
      "FlakyService", "TimeoutProneService", "RateLimitedApi",
      "ResourceIntensiveTask", "QuickCheck", "DeepAnalysis",
      "AlwaysFailsService"
    )
  }

  it should "have unique module names" in {
    val names = ResilienceModules.all.map(_.spec.name)
    names.distinct should have size names.size
  }

  // Counter reset tests

  "resetFlakyCounter" should "reset the flaky service call count" in {
    ResilienceModules.resetFlakyCounter()
    // Just verify no exception; counter is private
    succeed
  }

  "resetTimeoutCounter" should "reset the timeout service call count" in {
    ResilienceModules.resetTimeoutCounter()
    succeed
  }

  // All modules should have resilience tag
  "all resilience modules" should "have the resilience tag" in {
    ResilienceModules.all.foreach { module =>
      module.spec.tags should contain("resilience")
    }
  }

  // Version checks
  "all resilience modules" should "be version 1.0" in {
    ResilienceModules.all.foreach { module =>
      module.spec.majorVersion shouldBe 1
      module.spec.minorVersion shouldBe 0
    }
  }
}
