package io.constellation.http

import java.nio.file.Paths

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DashboardConfigTest extends AnyFlatSpec with Matchers {

  // ============= Default Config =============

  "DashboardConfig" should "have correct defaults" in {
    val config = DashboardConfig()
    config.cstDirectory shouldBe None
    config.defaultSampleRate shouldBe 1.0
    config.maxStoredExecutions shouldBe 1000
    config.enableDashboard shouldBe true
  }

  "DashboardConfig.default" should "be equivalent to no-args constructor" in {
    DashboardConfig.default shouldBe DashboardConfig()
  }

  // ============= getCstDirectory =============

  "getCstDirectory" should "return configured directory" in {
    val config = DashboardConfig(cstDirectory = Some(Paths.get("/scripts")))
    config.getCstDirectory shouldBe Paths.get("/scripts")
  }

  it should "default to current working directory" in {
    val config = DashboardConfig(cstDirectory = None)
    config.getCstDirectory shouldBe Paths.get(".")
  }

  // ============= Validation =============

  "validate" should "accept valid config" in {
    val config = DashboardConfig(defaultSampleRate = 0.5, maxStoredExecutions = 100)
    config.validate shouldBe Right(config)
  }

  it should "reject negative sample rate" in {
    val config = DashboardConfig(defaultSampleRate = -0.1)
    config.validate shouldBe a[Left[?, ?]]
  }

  it should "reject sample rate above 1.0" in {
    val config = DashboardConfig(defaultSampleRate = 1.1)
    config.validate shouldBe a[Left[?, ?]]
  }

  it should "accept sample rate 0.0" in {
    val config = DashboardConfig(defaultSampleRate = 0.0)
    config.validate shouldBe Right(config)
  }

  it should "accept sample rate 1.0" in {
    val config = DashboardConfig(defaultSampleRate = 1.0)
    config.validate shouldBe Right(config)
  }

  it should "reject zero max executions" in {
    val config = DashboardConfig(maxStoredExecutions = 0)
    config.validate shouldBe a[Left[?, ?]]
  }

  it should "reject negative max executions" in {
    val config = DashboardConfig(maxStoredExecutions = -1)
    config.validate shouldBe a[Left[?, ?]]
  }

  // ============= Builder =============

  "DashboardConfig.defaultBuilder" should "create config with defaults" in {
    val result = DashboardConfig.defaultBuilder.build
    result shouldBe Right(DashboardConfig.default)
  }

  it should "allow overriding cst directory with Path" in {
    val result = DashboardConfig.defaultBuilder
      .withCstDirectory(Paths.get("/my/scripts"))
      .build
    result shouldBe a[Right[?, ?]]
    result.toOption.get.cstDirectory shouldBe Some(Paths.get("/my/scripts"))
  }

  it should "allow overriding cst directory with String" in {
    val result = DashboardConfig.defaultBuilder
      .withCstDirectory("/my/scripts")
      .build
    result shouldBe a[Right[?, ?]]
    result.toOption.get.cstDirectory shouldBe Some(Paths.get("/my/scripts"))
  }

  it should "allow overriding sample rate" in {
    val result = DashboardConfig.defaultBuilder
      .withSampleRate(0.5)
      .build
    result shouldBe a[Right[?, ?]]
    result.toOption.get.defaultSampleRate shouldBe 0.5
  }

  it should "allow overriding max executions" in {
    val result = DashboardConfig.defaultBuilder
      .withMaxExecutions(500)
      .build
    result shouldBe a[Right[?, ?]]
    result.toOption.get.maxStoredExecutions shouldBe 500
  }

  it should "allow disabling dashboard" in {
    val result = DashboardConfig.defaultBuilder
      .withDashboardEnabled(false)
      .build
    result shouldBe a[Right[?, ?]]
    result.toOption.get.enableDashboard shouldBe false
  }

  it should "reject invalid config on build" in {
    val result = DashboardConfig.defaultBuilder
      .withSampleRate(2.0)
      .build
    result shouldBe a[Left[?, ?]]
  }
}
