package io.constellation.lang.optimizer

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OptimizationConfigTest extends AnyFlatSpec with Matchers {

  // ── default preset ──────────────────────────────────────────────────

  "OptimizationConfig.default" should "have all three optimizations enabled" in {
    val cfg = OptimizationConfig.default
    cfg.enableDCE shouldBe true
    cfg.enableConstantFolding shouldBe true
    cfg.enableCSE shouldBe true
  }

  it should "have maxIterations set to 3" in {
    OptimizationConfig.default.maxIterations shouldBe 3
  }

  it should "list all three pass names in enabledPassNames" in {
    val names = OptimizationConfig.default.enabledPassNames
    names should have size 3
    names should contain("dead-code-elimination")
    names should contain("constant-folding")
    names should contain("common-subexpression-elimination")
  }

  it should "report hasOptimizationsEnabled as true" in {
    OptimizationConfig.default.hasOptimizationsEnabled shouldBe true
  }

  // ── none preset ─────────────────────────────────────────────────────

  "OptimizationConfig.none" should "have all three optimizations disabled" in {
    val cfg = OptimizationConfig.none
    cfg.enableDCE shouldBe false
    cfg.enableConstantFolding shouldBe false
    cfg.enableCSE shouldBe false
  }

  it should "return an empty enabledPassNames list" in {
    OptimizationConfig.none.enabledPassNames shouldBe empty
  }

  it should "report hasOptimizationsEnabled as false" in {
    OptimizationConfig.none.hasOptimizationsEnabled shouldBe false
  }

  // ── aggressive preset ───────────────────────────────────────────────

  "OptimizationConfig.aggressive" should "have all optimizations enabled" in {
    val cfg = OptimizationConfig.aggressive
    cfg.enableDCE shouldBe true
    cfg.enableConstantFolding shouldBe true
    cfg.enableCSE shouldBe true
  }

  it should "have maxIterations set to 10" in {
    OptimizationConfig.aggressive.maxIterations shouldBe 10
  }

  it should "report hasOptimizationsEnabled as true" in {
    OptimizationConfig.aggressive.hasOptimizationsEnabled shouldBe true
  }

  // ── dceOnly preset ──────────────────────────────────────────────────

  "OptimizationConfig.dceOnly" should "enable only DCE" in {
    val cfg = OptimizationConfig.dceOnly
    cfg.enableDCE shouldBe true
    cfg.enableConstantFolding shouldBe false
    cfg.enableCSE shouldBe false
  }

  it should "list only dead-code-elimination in enabledPassNames" in {
    OptimizationConfig.dceOnly.enabledPassNames shouldBe List("dead-code-elimination")
  }

  it should "report hasOptimizationsEnabled as true" in {
    OptimizationConfig.dceOnly.hasOptimizationsEnabled shouldBe true
  }

  // ── constantFoldingOnly preset ──────────────────────────────────────

  "OptimizationConfig.constantFoldingOnly" should "enable only constant folding" in {
    val cfg = OptimizationConfig.constantFoldingOnly
    cfg.enableDCE shouldBe false
    cfg.enableConstantFolding shouldBe true
    cfg.enableCSE shouldBe false
  }

  it should "list only constant-folding in enabledPassNames" in {
    OptimizationConfig.constantFoldingOnly.enabledPassNames shouldBe List("constant-folding")
  }

  it should "report hasOptimizationsEnabled as true" in {
    OptimizationConfig.constantFoldingOnly.hasOptimizationsEnabled shouldBe true
  }

  // ── cseOnly preset ──────────────────────────────────────────────────

  "OptimizationConfig.cseOnly" should "enable only CSE" in {
    val cfg = OptimizationConfig.cseOnly
    cfg.enableDCE shouldBe false
    cfg.enableConstantFolding shouldBe false
    cfg.enableCSE shouldBe true
  }

  it should "list only common-subexpression-elimination in enabledPassNames" in {
    OptimizationConfig.cseOnly.enabledPassNames shouldBe List("common-subexpression-elimination")
  }

  it should "report hasOptimizationsEnabled as true" in {
    OptimizationConfig.cseOnly.hasOptimizationsEnabled shouldBe true
  }

  // ── custom config with 2 passes ─────────────────────────────────────

  "A custom OptimizationConfig with two passes" should "list exactly the two enabled pass names" in {
    val cfg = OptimizationConfig(
      enableDCE = true,
      enableConstantFolding = false,
      enableCSE = true,
      maxIterations = 5
    )
    cfg.enabledPassNames should have size 2
    cfg.enabledPassNames should contain("dead-code-elimination")
    cfg.enabledPassNames should contain("common-subexpression-elimination")
    cfg.enabledPassNames should not contain "constant-folding"
  }

  it should "report hasOptimizationsEnabled as true" in {
    val cfg = OptimizationConfig(
      enableDCE = true,
      enableConstantFolding = false,
      enableCSE = true
    )
    cfg.hasOptimizationsEnabled shouldBe true
  }

  // ── default constructor matches default preset ──────────────────────

  "The default constructor (no args)" should "produce the same config as OptimizationConfig.default" in {
    val fromConstructor = OptimizationConfig()
    val fromPreset      = OptimizationConfig.default
    fromConstructor shouldBe fromPreset
  }

  it should "have the same enabledPassNames as default" in {
    OptimizationConfig().enabledPassNames shouldBe OptimizationConfig.default.enabledPassNames
  }

  it should "have the same hasOptimizationsEnabled as default" in {
    OptimizationConfig().hasOptimizationsEnabled shouldBe OptimizationConfig.default.hasOptimizationsEnabled
  }

  it should "have the same maxIterations as default" in {
    OptimizationConfig().maxIterations shouldBe OptimizationConfig.default.maxIterations
  }
}
