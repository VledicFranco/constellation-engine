package io.constellation

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ModuleCallOptionsTest extends AnyFlatSpec with Matchers {

  "ModuleCallOptions.empty" should "have all fields as None" in {
    val opts = ModuleCallOptions.empty
    opts.retry shouldBe None
    opts.timeoutMs shouldBe None
    opts.delayMs shouldBe None
    opts.backoff shouldBe None
    opts.cacheMs shouldBe None
    opts.cacheBackend shouldBe None
    opts.throttleCount shouldBe None
    opts.throttlePerMs shouldBe None
    opts.concurrency shouldBe None
    opts.onError shouldBe None
    opts.lazyEval shouldBe None
    opts.priority shouldBe None
  }

  "isEmpty" should "return true for empty options" in {
    ModuleCallOptions.empty.isEmpty shouldBe true
    ModuleCallOptions().isEmpty shouldBe true
  }

  it should "return false when retry is set" in {
    ModuleCallOptions(retry = Some(3)).isEmpty shouldBe false
  }

  it should "return false when timeoutMs is set" in {
    ModuleCallOptions(timeoutMs = Some(5000)).isEmpty shouldBe false
  }

  it should "return false when delayMs is set" in {
    ModuleCallOptions(delayMs = Some(100)).isEmpty shouldBe false
  }

  it should "return false when backoff is set" in {
    ModuleCallOptions(backoff = Some("exponential")).isEmpty shouldBe false
  }

  it should "return false when cacheMs is set" in {
    ModuleCallOptions(cacheMs = Some(60000)).isEmpty shouldBe false
  }

  it should "return false when cacheBackend is set" in {
    ModuleCallOptions(cacheBackend = Some("local")).isEmpty shouldBe false
  }

  it should "return false when throttleCount is set" in {
    ModuleCallOptions(throttleCount = Some(10)).isEmpty shouldBe false
  }

  it should "return false when throttlePerMs is set" in {
    ModuleCallOptions(throttlePerMs = Some(1000)).isEmpty shouldBe false
  }

  it should "return false when concurrency is set" in {
    ModuleCallOptions(concurrency = Some(4)).isEmpty shouldBe false
  }

  it should "return false when onError is set" in {
    ModuleCallOptions(onError = Some("skip")).isEmpty shouldBe false
  }

  it should "return false when lazyEval is set" in {
    ModuleCallOptions(lazyEval = Some(true)).isEmpty shouldBe false
  }

  it should "return false when priority is set" in {
    ModuleCallOptions(priority = Some(80)).isEmpty shouldBe false
  }

  "ModuleCallOptions" should "support multiple options" in {
    val opts = ModuleCallOptions(
      retry = Some(3),
      timeoutMs = Some(5000),
      backoff = Some("exponential"),
      priority = Some(80)
    )
    opts.isEmpty shouldBe false
    opts.retry shouldBe Some(3)
    opts.timeoutMs shouldBe Some(5000)
    opts.backoff shouldBe Some("exponential")
    opts.priority shouldBe Some(80)
  }
}
