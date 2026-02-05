package io.constellation.lang.optimizer

import java.util.UUID

import io.constellation.lang.compiler.{IRModuleCallOptions, IRNode, IRPipeline}
import io.constellation.lang.semantic.SemanticType

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class IROptimizerTest extends AnyFlatSpec with Matchers {

  private def uuid(name: String): UUID = UUID.nameUUIDFromBytes(name.getBytes)

  "IROptimizer" should "apply all enabled passes" in {
    // Pipeline with dead code, constants, and duplicates
    val ir = IRPipeline(
      nodes = Map(
        uuid("x") -> IRNode.Input(uuid("x"), "x", SemanticType.SInt, None),
        // Constant folding target
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 2, SemanticType.SInt, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), 3, SemanticType.SInt, None),
        uuid("const") -> IRNode.ModuleCall(
          uuid("const"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("a"), "b" -> uuid("b")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        ),
        // Dead code
        uuid("dead") -> IRNode.LiteralNode(uuid("dead"), 42, SemanticType.SInt, None),
        // Output
        uuid("result") -> IRNode.ModuleCall(
          uuid("result"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("x"), "b" -> uuid("const")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List(uuid("x")),
      declaredOutputs = List("result"),
      variableBindings = Map(
        "x"      -> uuid("x"),
        "const"  -> uuid("const"),
        "dead"   -> uuid("dead"),
        "result" -> uuid("result")
      )
    )

    val result = IROptimizer.optimize(ir, OptimizationConfig.default)

    // Dead code should be eliminated
    result.optimizedIR.nodes should not contain key(uuid("dead"))

    // Constant should be folded
    result.optimizedIR.nodes(uuid("const")) shouldBe a[IRNode.LiteralNode]

    // Stats should show optimization
    result.stats.nodesEliminated should be > 0
  }

  it should "respect disabled passes" in {
    val ir = IRPipeline(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 2, SemanticType.SInt, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), 3, SemanticType.SInt, None),
        uuid("add") -> IRNode.ModuleCall(
          uuid("add"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("a"), "b" -> uuid("b")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("add"))
    )

    // Disable constant folding
    val config = OptimizationConfig(
      enableDCE = true,
      enableConstantFolding = false,
      enableCSE = true
    )

    val result = IROptimizer.optimize(ir, config)

    // add should remain a ModuleCall (not folded)
    result.optimizedIR.nodes(uuid("add")) shouldBe a[IRNode.ModuleCall]
  }

  it should "return unchanged IR when no optimizations enabled" in {
    val ir = IRPipeline(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 2, SemanticType.SInt, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), 3, SemanticType.SInt, None),
        uuid("add") -> IRNode.ModuleCall(
          uuid("add"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("a"), "b" -> uuid("b")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("add"))
    )

    val result = IROptimizer.optimize(ir, OptimizationConfig.none)

    result.optimizedIR.nodes shouldBe ir.nodes
    result.stats.nodesEliminated shouldBe 0
    result.iterations shouldBe 0
  }

  it should "iterate until fixpoint" in {
    // Create a program that needs multiple passes to fully optimize
    // First pass: fold constant -> Second pass: eliminate now-dead literal dependencies
    val ir = IRPipeline(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 2, SemanticType.SInt, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), 3, SemanticType.SInt, None),
        uuid("add") -> IRNode.ModuleCall(
          uuid("add"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("a"), "b" -> uuid("b")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("add"))
    )

    val result = IROptimizer.optimize(ir, OptimizationConfig.default)

    // Constant folding should have folded add to a literal
    result.optimizedIR.nodes(uuid("add")) shouldBe a[IRNode.LiteralNode]

    // DCE should have removed the now-unused literal nodes a and b
    result.optimizedIR.nodes should not contain key(uuid("a"))
    result.optimizedIR.nodes should not contain key(uuid("b"))

    // Multiple iterations should have occurred
    result.iterations should be >= 1
  }

  it should "respect maxIterations" in {
    val ir = IRPipeline(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 1, SemanticType.SInt, None)
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("a"))
    )

    val config = OptimizationConfig.default.copy(maxIterations = 1)
    val result = IROptimizer.optimize(ir, config)

    result.iterations should be <= 1
  }

  it should "provide accurate statistics" in {
    val ir = IRPipeline(
      nodes = Map(
        uuid("x")     -> IRNode.Input(uuid("x"), "x", SemanticType.SInt, None),
        uuid("dead1") -> IRNode.LiteralNode(uuid("dead1"), 1, SemanticType.SInt, None),
        uuid("dead2") -> IRNode.LiteralNode(uuid("dead2"), 2, SemanticType.SInt, None),
        uuid("used")  -> IRNode.LiteralNode(uuid("used"), 42, SemanticType.SInt, None)
      ),
      inputs = List(uuid("x")),
      declaredOutputs = List("result"),
      variableBindings = Map(
        "x"      -> uuid("x"),
        "dead1"  -> uuid("dead1"),
        "dead2"  -> uuid("dead2"),
        "result" -> uuid("used")
      )
    )

    val result = IROptimizer.optimize(ir, OptimizationConfig.dceOnly)

    result.stats.nodesBefore shouldBe 4
    result.stats.nodesAfter shouldBe 1 // only 'used' should remain
    result.stats.nodesEliminated shouldBe 3
    result.stats.eliminationPercentage shouldBe 75.0
  }

  "OptimizationConfig" should "have sensible defaults" in {
    val default = OptimizationConfig.default
    default.enableDCE shouldBe true
    default.enableConstantFolding shouldBe true
    default.enableCSE shouldBe true
    default.maxIterations shouldBe 3
  }

  it should "have a none configuration" in {
    val none = OptimizationConfig.none
    none.enableDCE shouldBe false
    none.enableConstantFolding shouldBe false
    none.enableCSE shouldBe false
    none.hasOptimizationsEnabled shouldBe false
  }

  it should "have specialized configurations" in {
    OptimizationConfig.dceOnly.enableDCE shouldBe true
    OptimizationConfig.dceOnly.enableConstantFolding shouldBe false
    OptimizationConfig.dceOnly.enableCSE shouldBe false

    OptimizationConfig.constantFoldingOnly.enableDCE shouldBe false
    OptimizationConfig.constantFoldingOnly.enableConstantFolding shouldBe true
    OptimizationConfig.constantFoldingOnly.enableCSE shouldBe false

    OptimizationConfig.cseOnly.enableDCE shouldBe false
    OptimizationConfig.cseOnly.enableConstantFolding shouldBe false
    OptimizationConfig.cseOnly.enableCSE shouldBe true
  }

  "IROptimizer.analyze" should "correctly count node types" in {
    val ir = IRPipeline(
      nodes = Map(
        uuid("input") -> IRNode.Input(uuid("input"), "x", SemanticType.SInt, None),
        uuid("lit")   -> IRNode.LiteralNode(uuid("lit"), 5, SemanticType.SInt, None),
        uuid("call") -> IRNode.ModuleCall(
          uuid("call"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("input"), "b" -> uuid("lit")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List(uuid("input")),
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("call"))
    )

    val analysis = IROptimizer.analyze(ir)

    analysis.totalNodes shouldBe 3
    analysis.inputNodes shouldBe 1
    analysis.literalNodes shouldBe 1
    analysis.moduleCallNodes shouldBe 1
  }
}
