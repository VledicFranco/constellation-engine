package io.constellation.lang.optimizer

import io.constellation.lang.compiler.{IRModuleCallOptions, IRNode, IRProgram}
import io.constellation.lang.semantic.SemanticType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class ConstantFoldingTest extends AnyFlatSpec with Matchers {

  private def uuid(name: String): UUID = UUID.nameUUIDFromBytes(name.getBytes)

  "ConstantFolding" should "fold constant addition" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 5, SemanticType.SInt, None),
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

    val optimized = ConstantFolding.run(ir)

    // The add node should be replaced with a literal
    optimized.nodes(uuid("add")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe 8
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "fold constant subtraction" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 10, SemanticType.SInt, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), 4, SemanticType.SInt, None),
        uuid("sub") -> IRNode.ModuleCall(
          uuid("sub"),
          "stdlib.math.subtract",
          "subtract",
          Map("a" -> uuid("a"), "b" -> uuid("b")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("sub"))
    )

    val optimized = ConstantFolding.run(ir)

    optimized.nodes(uuid("sub")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe 6
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "fold constant multiplication" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 6, SemanticType.SInt, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), 7, SemanticType.SInt, None),
        uuid("mul") -> IRNode.ModuleCall(
          uuid("mul"),
          "stdlib.math.multiply",
          "multiply",
          Map("a" -> uuid("a"), "b" -> uuid("b")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("mul"))
    )

    val optimized = ConstantFolding.run(ir)

    optimized.nodes(uuid("mul")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe 42
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "fold constant division" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 20, SemanticType.SInt, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), 4, SemanticType.SInt, None),
        uuid("div") -> IRNode.ModuleCall(
          uuid("div"),
          "stdlib.math.divide",
          "divide",
          Map("a" -> uuid("a"), "b" -> uuid("b")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("div"))
    )

    val optimized = ConstantFolding.run(ir)

    optimized.nodes(uuid("div")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe 5
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "not fold division by zero" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 10, SemanticType.SInt, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), 0, SemanticType.SInt, None),
        uuid("div") -> IRNode.ModuleCall(
          uuid("div"),
          "stdlib.math.divide",
          "divide",
          Map("a" -> uuid("a"), "b" -> uuid("b")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("div"))
    )

    val optimized = ConstantFolding.run(ir)

    // Should remain a ModuleCall (not folded)
    optimized.nodes(uuid("div")) shouldBe a[IRNode.ModuleCall]
  }

  it should "fold boolean AND" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a")   -> IRNode.LiteralNode(uuid("a"), true, SemanticType.SBoolean, None),
        uuid("b")   -> IRNode.LiteralNode(uuid("b"), false, SemanticType.SBoolean, None),
        uuid("and") -> IRNode.AndNode(uuid("and"), uuid("a"), uuid("b"), None)
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("and"))
    )

    val optimized = ConstantFolding.run(ir)

    optimized.nodes(uuid("and")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe false
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "fold boolean OR" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a")  -> IRNode.LiteralNode(uuid("a"), false, SemanticType.SBoolean, None),
        uuid("b")  -> IRNode.LiteralNode(uuid("b"), true, SemanticType.SBoolean, None),
        uuid("or") -> IRNode.OrNode(uuid("or"), uuid("a"), uuid("b"), None)
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("or"))
    )

    val optimized = ConstantFolding.run(ir)

    optimized.nodes(uuid("or")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe true
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "fold boolean NOT" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a")   -> IRNode.LiteralNode(uuid("a"), true, SemanticType.SBoolean, None),
        uuid("not") -> IRNode.NotNode(uuid("not"), uuid("a"), None)
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("not"))
    )

    val optimized = ConstantFolding.run(ir)

    optimized.nodes(uuid("not")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe false
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "fold string concatenation" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), "Hello, ", SemanticType.SString, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), "World!", SemanticType.SString, None),
        uuid("concat") -> IRNode.ModuleCall(
          uuid("concat"),
          "stdlib.string.concat",
          "concat",
          Map("a" -> uuid("a"), "b" -> uuid("b")),
          SemanticType.SString,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("concat"))
    )

    val optimized = ConstantFolding.run(ir)

    optimized.nodes(uuid("concat")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe "Hello, World!"
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "fold string interpolation with constants" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("name") -> IRNode.LiteralNode(uuid("name"), "Alice", SemanticType.SString, None),
        uuid("interp") -> IRNode.StringInterpolationNode(
          uuid("interp"),
          List("Hello, ", "!"),
          List(uuid("name")),
          None
        )
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("interp"))
    )

    val optimized = ConstantFolding.run(ir)

    optimized.nodes(uuid("interp")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe "Hello, Alice!"
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "fold conditional with constant condition (true)" in {
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

    val optimized = ConstantFolding.run(ir)

    optimized.nodes(uuid("if")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe 1
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "fold conditional with constant condition (false)" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("cond") -> IRNode.LiteralNode(uuid("cond"), false, SemanticType.SBoolean, None),
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

    val optimized = ConstantFolding.run(ir)

    optimized.nodes(uuid("if")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe 2
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "not fold when inputs are non-constant" in {
    val ir = IRProgram(
      nodes = Map(
        uuid("input")   -> IRNode.Input(uuid("input"), "x", SemanticType.SInt, None),
        uuid("literal") -> IRNode.LiteralNode(uuid("literal"), 5, SemanticType.SInt, None),
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
      variableBindings = Map("x" -> uuid("input"), "result" -> uuid("add"))
    )

    val optimized = ConstantFolding.run(ir)

    // Should remain a ModuleCall
    optimized.nodes(uuid("add")) shouldBe a[IRNode.ModuleCall]
  }

  it should "fold chained constant operations" in {
    // (2 + 3) * 4 = 20
    val ir = IRProgram(
      nodes = Map(
        uuid("a") -> IRNode.LiteralNode(uuid("a"), 2, SemanticType.SInt, None),
        uuid("b") -> IRNode.LiteralNode(uuid("b"), 3, SemanticType.SInt, None),
        uuid("c") -> IRNode.LiteralNode(uuid("c"), 4, SemanticType.SInt, None),
        uuid("add") -> IRNode.ModuleCall(
          uuid("add"),
          "stdlib.math.add",
          "add",
          Map("a" -> uuid("a"), "b" -> uuid("b")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        ),
        uuid("mul") -> IRNode.ModuleCall(
          uuid("mul"),
          "stdlib.math.multiply",
          "multiply",
          Map("a" -> uuid("add"), "b" -> uuid("c")),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List.empty,
      declaredOutputs = List("result"),
      variableBindings = Map("result" -> uuid("mul"))
    )

    val optimized = ConstantFolding.run(ir)

    // add should be folded to 5
    optimized.nodes(uuid("add")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe 5
      case other                              => fail(s"Expected LiteralNode but got $other")
    }

    // mul should be folded to 20
    optimized.nodes(uuid("mul")) match {
      case IRNode.LiteralNode(_, value, _, _) => value shouldBe 20
      case other                              => fail(s"Expected LiteralNode but got $other")
    }
  }

  it should "handle empty programs" in {
    val ir = IRProgram(
      nodes = Map.empty,
      inputs = List.empty,
      declaredOutputs = List.empty,
      variableBindings = Map.empty
    )

    val optimized = ConstantFolding.run(ir)

    optimized.nodes shouldBe empty
  }
}
