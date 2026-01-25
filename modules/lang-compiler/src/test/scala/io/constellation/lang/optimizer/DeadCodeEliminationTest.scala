package io.constellation.lang.optimizer

import io.constellation.lang.compiler.{IRModuleCallOptions, IRNode, IRProgram}
import io.constellation.lang.semantic.SemanticType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class DeadCodeEliminationTest extends AnyFlatSpec with Matchers {

  private def uuid(name: String): UUID = UUID.nameUUIDFromBytes(name.getBytes)

  "DeadCodeElimination" should "remove unused nodes" in {
    // Program: x + 1 (unused), x * 2 (used as output)
    val ir = IRProgram(
      nodes = Map(
        uuid("input") -> IRNode.Input(uuid("input"), "x", SemanticType.SInt, None),
        uuid("literal1") -> IRNode.LiteralNode(uuid("literal1"), 1, SemanticType.SInt, None),
        uuid("literal2") -> IRNode.LiteralNode(uuid("literal2"), 2, SemanticType.SInt, None),
        uuid("unused") -> IRNode.ModuleCall(
          uuid("unused"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("input"), "b" -> uuid("literal1")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        ),
        uuid("used") -> IRNode.ModuleCall(
          uuid("used"),
          "stdlib.math.multiply",
          "multiply",
          Map("a" -> uuid("input"), "b" -> uuid("literal2")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List(uuid("input")),
      declaredOutputs = List("result"),
      variableBindings = Map(
        "x" -> uuid("input"),
        "unused" -> uuid("unused"),
        "result" -> uuid("used")
      )
    )

    val optimized = DeadCodeElimination.run(ir)

    // Should keep: input, literal2, used (and their dependencies)
    optimized.nodes should contain key uuid("input")
    optimized.nodes should contain key uuid("literal2")
    optimized.nodes should contain key uuid("used")

    // Should remove: literal1, unused (not reachable from output)
    optimized.nodes should not contain key (uuid("unused"))
    optimized.nodes should not contain key (uuid("literal1"))
  }

  it should "preserve all nodes when all are reachable" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("input") -> IRNode.Input(uuid("input"), "x", SemanticType.SInt, None),
        uuid("literal") -> IRNode.LiteralNode(uuid("literal"), 1, SemanticType.SInt, None),
        uuid("add") -> IRNode.ModuleCall(
          uuid("add"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("input"), "b" -> uuid("literal")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List(uuid("input")),
      declaredOutputs = List("result"),
      variableBindings = Map(
        "x" -> uuid("input"),
        "result" -> uuid("add")
      )
    )

    val optimized = DeadCodeElimination.run(ir)

    // All nodes should be preserved
    optimized.nodes.size shouldBe 3
    optimized.nodes should contain key uuid("input")
    optimized.nodes should contain key uuid("literal")
    optimized.nodes should contain key uuid("add")
  }

  it should "handle empty programs" in {
    val ir = IRProgram(
      nodes = Map.empty,
      inputs = List.empty,
      declaredOutputs = List.empty,
      variableBindings = Map.empty
    )

    val optimized = DeadCodeElimination.run(ir)

    optimized.nodes shouldBe empty
  }

  it should "handle programs with no outputs" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("input") -> IRNode.Input(uuid("input"), "x", SemanticType.SInt, None),
        uuid("add") -> IRNode.ModuleCall(
          uuid("add"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("input"), "b" -> uuid("input")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List(uuid("input")),
      declaredOutputs = List.empty, // No outputs declared
      variableBindings = Map("x" -> uuid("input"), "result" -> uuid("add"))
    )

    val optimized = DeadCodeElimination.run(ir)

    // With no outputs, nothing is reachable - return original
    optimized.nodes.size shouldBe 2
  }

  it should "remove dead code in a chain" in {
    // Program: a -> b -> c (unused), d (used as output)
    val ir = IRProgram(
      nodes = Map(
        uuid("input") -> IRNode.Input(uuid("input"), "x", SemanticType.SInt, None),
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 1, SemanticType.SInt, None),
        uuid("b") -> IRNode.ModuleCall(
          uuid("b"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("input"), "b" -> uuid("a")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        ),
        uuid("c") -> IRNode.ModuleCall(
          uuid("c"),
          "stdlib.math.multiply",
          "multiply",
          Map("a" -> uuid("b"), "b" -> uuid("a")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        ),
        uuid("d") -> IRNode.LiteralNode(uuid("d"), 42, SemanticType.SInt, None)
      ),
      inputs = List(uuid("input")),
      declaredOutputs = List("result"),
      variableBindings = Map(
        "x" -> uuid("input"),
        "a" -> uuid("a"),
        "b" -> uuid("b"),
        "c" -> uuid("c"),
        "result" -> uuid("d")
      )
    )

    val optimized = DeadCodeElimination.run(ir)

    // Only d should remain (it's the output)
    optimized.nodes should contain key uuid("d")
    optimized.nodes should not contain key (uuid("a"))
    optimized.nodes should not contain key (uuid("b"))
    optimized.nodes should not contain key (uuid("c"))
  }

  it should "handle conditionals correctly" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("cond") -> IRNode.LiteralNode(uuid("cond"), true, SemanticType.SBoolean, None),
        uuid("then") -> IRNode.LiteralNode(uuid("then"), 1, SemanticType.SInt, None),
        uuid("else") -> IRNode.LiteralNode(uuid("else"), 2, SemanticType.SInt, None),
        uuid("if") -> IRNode.ConditionalNode(
          uuid("if"),
          uuid("cond"),
          uuid("then"),
          uuid("else"),
          SemanticType.SInt,
          None
        )
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("if"))
    )

    val optimized = DeadCodeElimination.run(ir)

    // All nodes should be preserved (all reachable from conditional)
    optimized.nodes.size shouldBe 4
  }

  it should "handle boolean expressions" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), true, SemanticType.SBoolean, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), false, SemanticType.SBoolean, None),
        uuid("and") -> IRNode.AndNode(uuid("and"), uuid("a"), uuid("b"), None)
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("and"))
    )

    val optimized = DeadCodeElimination.run(ir)

    optimized.nodes.size shouldBe 3
  }
}
