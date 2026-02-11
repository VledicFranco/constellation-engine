package io.constellation.provider

import scala.concurrent.duration.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ProviderManagerConfigSpec extends AnyFlatSpec with Matchers {

  // ===== Default values =====

  "ProviderManagerConfig" should "have correct default values" in {
    val config = ProviderManagerConfig()

    config.grpcPort shouldBe 9090
    config.heartbeatInterval shouldBe 5.seconds
    config.heartbeatTimeout shouldBe 15.seconds
    config.controlPlaneRequiredTimeout shouldBe 30.seconds
    config.activeModulesReportInterval shouldBe 30.seconds
    config.reservedNamespaces shouldBe Set("stdlib")
  }

  it should "accept custom values via constructor" in {
    val config = ProviderManagerConfig(
      grpcPort = 8080,
      heartbeatInterval = 10.seconds,
      heartbeatTimeout = 20.seconds,
      controlPlaneRequiredTimeout = 60.seconds,
      activeModulesReportInterval = 45.seconds,
      reservedNamespaces = Set("stdlib", "system")
    )

    config.grpcPort shouldBe 8080
    config.heartbeatInterval shouldBe 10.seconds
    config.heartbeatTimeout shouldBe 20.seconds
    config.controlPlaneRequiredTimeout shouldBe 60.seconds
    config.activeModulesReportInterval shouldBe 45.seconds
    config.reservedNamespaces shouldBe Set("stdlib", "system")
  }

  // ===== fromEnv =====

  "ProviderManagerConfig.fromEnv" should "return defaults when no env vars set" in {
    val config = ProviderManagerConfig.fromEnv

    config.grpcPort shouldBe 9090
    config.heartbeatInterval shouldBe 5.seconds
    config.heartbeatTimeout shouldBe 15.seconds
    config.controlPlaneRequiredTimeout shouldBe 30.seconds
    config.activeModulesReportInterval shouldBe 30.seconds
    config.reservedNamespaces shouldBe Set("stdlib")
  }

  it should "produce consistent values with default constructor" in {
    val fromConstructor = ProviderManagerConfig()
    val fromEnv = ProviderManagerConfig.fromEnv

    fromConstructor.grpcPort shouldBe fromEnv.grpcPort
    fromConstructor.heartbeatInterval shouldBe fromEnv.heartbeatInterval
    fromConstructor.heartbeatTimeout shouldBe fromEnv.heartbeatTimeout
    fromConstructor.controlPlaneRequiredTimeout shouldBe fromEnv.controlPlaneRequiredTimeout
    fromConstructor.activeModulesReportInterval shouldBe fromEnv.activeModulesReportInterval
  }
}
