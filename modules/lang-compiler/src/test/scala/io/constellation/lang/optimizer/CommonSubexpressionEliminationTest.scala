package io.constellation.lang.optimizer

import io.constellation.lang.compiler.{IRNode, IRProgram}
import io.constellation.lang.semantic.SemanticType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class CommonSubexpressionEliminationTest extends AnyFlatSpec with Matchers {

  private def uuid(name: String): UUID = UUID.nameUUIDFromBytes(name.getBytes)

  "CommonSubexpressionElimination" should "deduplicate identical module calls" in {
    // Two identical add(x, y) calls should be deduplicated
    val ir = IRProgram(
      nodes = Map(
        uuid("x") -> IRNode.Input(uuid("x"), "x", SemanticType.SInt, None),
        uuid("y") -> IRNode.Input(uuid("y"), "y", SemanticType.SInt, None),
        uuid("add1") -> IRNode.ModuleCall(
          uuid("add1"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("x"), "b" -> uuid("y")),
          SemanticType.SInt,
          None
        ),
        uuid("add2") -> IRNode.ModuleCall(
          uuid("add2"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("x"), "b" -> uuid("y")),
          SemanticType.SInt,
          None
        ),
        uuid("result") -> IRNode.ModuleCall(
          uuid("result"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("add1"), "b" -> uuid("add2")),
          SemanticType.SInt,
          None
        )
      ),
      inputs = List(uuid("x"), uuid("y")),
      declaredOutputs = List("result"),
      variableBindings = Map(
        "x" -> uuid("x"),
        "y" -> uuid("y"),
        "add1" -> uuid("add1"),
        "add2" -> uuid("add2"),
        "result" -> uuid("result")
      )
    )

    val optimized = CommonSubexpressionElimination.run(ir)

    // One of add1 or add2 should be removed
    val addNodes = optimized.nodes.values.collect {
      case n @ IRNode.ModuleCall(_, "stdlib.math.add", "add", inputs, _, _)
          if inputs == Map("a" -> uuid("x"), "b" -> uuid("y")) =>
        n
    }.toList

    addNodes.size shouldBe 1

    // Total nodes should be reduced by 1
    optimized.nodes.size shouldBe ir.nodes.size - 1
  }

  it should "not deduplicate different module calls" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("x") -> IRNode.Input(uuid("x"), "x", SemanticType.SInt, None),
        uuid("y") -> IRNode.Input(uuid("y"), "y", SemanticType.SInt, None),
        uuid("add") -> IRNode.ModuleCall(
          uuid("add"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("x"), "b" -> uuid("y")),
          SemanticType.SInt,
          None
        ),
        uuid("mul") -> IRNode.ModuleCall(
          uuid("mul"),
          "stdlib.math.multiply",
          "multiply",
          Map("a" -> uuid("x"), "b" -> uuid("y")),
          SemanticType.SInt,
          None
        )
      ),
      inputs = List(uuid("x"), uuid("y")),
      declaredOutputs = List("add", "mul"),
      variableBindings = Map(
        "x" -> uuid("x"),
        "y" -> uuid("y"),
        "add" -> uuid("add"),
        "mul" -> uuid("mul")
      )
    )

    val optimized = CommonSubexpressionElimination.run(ir)

    // No nodes should be removed
    optimized.nodes.size shouldBe ir.nodes.size
  }

  it should "not deduplicate input nodes" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("x1") -> IRNode.Input(uuid("x1"), "x", SemanticType.SInt, None),
        uuid("x2") -> IRNode.Input(uuid("x2"), "x", SemanticType.SInt, None)
      ),
      inputs = List(uuid("x1"), uuid("x2")),
      declaredOutputs = List("x1", "x2"),
      variableBindings = Map("x1" -> uuid("x1"), "x2" -> uuid("x2"))
    )

    val optimized = CommonSubexpressionElimination.run(ir)

    // Both inputs should be preserved
    optimized.nodes.size shouldBe 2
  }

  it should "not deduplicate literal nodes" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 5, SemanticType.SInt, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), 5, SemanticType.SInt, None)
      ),
      inputs = List.empty,
      declaredOutputs = List("a", "b"),
      variableBindings = Map("a" -> uuid("a"), "b" -> uuid("b"))
    )

    val optimized = CommonSubexpressionElimination.run(ir)

    // Both literals should be preserved
    optimized.nodes.size shouldBe 2
  }

  it should "deduplicate identical field access nodes" in {
    val recordType = SemanticType.SRecord(Map("x" -> SemanticType.SInt, "y" -> SemanticType.SInt))

    val ir = IRProgram(
      nodes = Map(
        uuid("record") -> IRNode.Input(uuid("record"), "r", recordType, None),
        uuid("field1") -> IRNode.FieldAccessNode(
          uuid("field1"),
          uuid("record"),
          "x",
          SemanticType.SInt,
          None
        ),
        uuid("field2") -> IRNode.FieldAccessNode(
          uuid("field2"),
          uuid("record"),
          "x",
          SemanticType.SInt,
          None
        ),
        uuid("add") -> IRNode.ModuleCall(
          uuid("add"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("field1"), "b" -> uuid("field2")),
          SemanticType.SInt,
          None
        )
      ),
      inputs = List(uuid("record")),
      declaredOutputs = List("result"),
      variableBindings = Map(
        "r" -> uuid("record"),
        "field1" -> uuid("field1"),
        "field2" -> uuid("field2"),
        "result" -> uuid("add")
      )
    )

    val optimized = CommonSubexpressionElimination.run(ir)

    // One of the field access nodes should be eliminated
    val fieldNodes = optimized.nodes.values.collect {
      case n: IRNode.FieldAccessNode => n
    }.toList

    fieldNodes.size shouldBe 1
  }

  it should "handle empty programs" in {
    val ir = IRProgram(
      nodes = Map.empty,
      inputs = List.empty,
      declaredOutputs = List.empty,
      variableBindings = Map.empty
    )

    val optimized = CommonSubexpressionElimination.run(ir)

    optimized.nodes shouldBe empty
  }

  it should "deduplicate boolean operations" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a") -> IRNode.Input(uuid("a"), "a", SemanticType.SBoolean, None),
        uuid("b") -> IRNode.Input(uuid("b"), "b", SemanticType.SBoolean, None),
        uuid("and1") -> IRNode.AndNode(uuid("and1"), uuid("a"), uuid("b"), None),
        uuid("and2") -> IRNode.AndNode(uuid("and2"), uuid("a"), uuid("b"), None)
      ),
      inputs = List(uuid("a"), uuid("b")),
      declaredOutputs = List("and1", "and2"),
      variableBindings = Map(
        "a" -> uuid("a"),
        "b" -> uuid("b"),
        "and1" -> uuid("and1"),
        "and2" -> uuid("and2")
      )
    )

    val optimized = CommonSubexpressionElimination.run(ir)

    // One AND should be eliminated
    val andNodes = optimized.nodes.values.collect {
      case n: IRNode.AndNode => n
    }.toList

    andNodes.size shouldBe 1
  }

  it should "update references when deduplicating" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("x") -> IRNode.Input(uuid("x"), "x", SemanticType.SInt, None),
        uuid("y") -> IRNode.Input(uuid("y"), "y", SemanticType.SInt, None),
        uuid("add1") -> IRNode.ModuleCall(
          uuid("add1"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("x"), "b" -> uuid("y")),
          SemanticType.SInt,
          None
        ),
        uuid("add2") -> IRNode.ModuleCall(
          uuid("add2"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("x"), "b" -> uuid("y")),
          SemanticType.SInt,
          None
        ),
        uuid("mul") -> IRNode.ModuleCall(
          uuid("mul"),
          "stdlib.math.multiply",
          "multiply",
          Map("a" -> uuid("add1"), "b" -> uuid("add2")),
          SemanticType.SInt,
          None
        )
      ),
      inputs = List(uuid("x"), uuid("y")),
      declaredOutputs = List("result"),
      variableBindings = Map(
        "x" -> uuid("x"),
        "y" -> uuid("y"),
        "add1" -> uuid("add1"),
        "add2" -> uuid("add2"),
        "result" -> uuid("mul")
      )
    )

    val optimized = CommonSubexpressionElimination.run(ir)

    // The mul node should have both inputs pointing to the same add node
    val mulNode = optimized.nodes(uuid("mul")).asInstanceOf[IRNode.ModuleCall]
    mulNode.inputs("a") shouldBe mulNode.inputs("b")
  }
}
