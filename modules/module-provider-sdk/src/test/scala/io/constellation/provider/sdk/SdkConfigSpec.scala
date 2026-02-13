package io.constellation.provider.sdk

import scala.concurrent.duration.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SdkConfigSpec extends AnyFlatSpec with Matchers {

  // ===== Defaults =====

  "SdkConfig" should "have sensible defaults" in {
    val config = SdkConfig()

    config.executorPort shouldBe 9091
    config.executorHost shouldBe "localhost"
    config.heartbeatInterval shouldBe 5.seconds
    config.reconnectBackoff shouldBe 1.second
    config.maxReconnectBackoff shouldBe 60.seconds
    config.maxReconnectAttempts shouldBe 10
  }

  // ===== CanaryConfig defaults =====

  "CanaryConfig" should "have sensible defaults" in {
    val canary = CanaryConfig()

    canary.observationWindow shouldBe 30.seconds
    canary.healthThreshold shouldBe 0.95
    canary.maxLatencyMs shouldBe 5000L
    canary.rollbackOnFailure shouldBe true
  }

  // ===== Custom values =====

  "SdkConfig" should "accept custom values" in {
    val config = SdkConfig(
      executorPort = 8888,
      executorHost = "my-provider",
      heartbeatInterval = 10.seconds,
      reconnectBackoff = 2.seconds,
      maxReconnectBackoff = 120.seconds,
      maxReconnectAttempts = 5,
      canary = CanaryConfig(
        observationWindow = 60.seconds,
        healthThreshold = 0.99,
        maxLatencyMs = 10000L,
        rollbackOnFailure = false
      )
    )

    config.executorPort shouldBe 8888
    config.executorHost shouldBe "my-provider"
    config.heartbeatInterval shouldBe 10.seconds
    config.maxReconnectAttempts shouldBe 5
    config.canary.observationWindow shouldBe 60.seconds
    config.canary.healthThreshold shouldBe 0.99
    config.canary.rollbackOnFailure shouldBe false
  }
}
