package io.constellation.lang.viz

import io.constellation.lang.compiler.{IRNode, IRProgram}
import io.constellation.lang.semantic.SemanticType
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.util.UUID

class DagVizCompilerTest extends AnyFunSuite with Matchers {

  test("compile simple input node") {
    val inputId = UUID.randomUUID()
    val ir = IRProgram(
      nodes = Map(
        inputId -> IRNode.Input(inputId, "data", SemanticType.SString, None)
      ),
      inputs = List(inputId),
      declaredOutputs = List.empty,
      variableBindings = Map("data" -> inputId)
    )

    val vizIR = DagVizCompiler.compile(ir)

    vizIR.nodes should have length 1
    vizIR.nodes.head.kind shouldBe NodeKind.Input
    vizIR.nodes.head.label shouldBe "data"
    vizIR.nodes.head.typeSignature shouldBe "String"
    vizIR.edges should be(empty)
  }

  test("compile linear pipeline A -> B -> C") {
    val inputId = UUID.randomUUID()
    val moduleId = UUID.randomUUID()
    val outputId = UUID.randomUUID()

    val ir = IRProgram(
      nodes = Map(
        inputId -> IRNode.Input(inputId, "input", SemanticType.SString, None),
        moduleId -> IRNode.ModuleCall(
          moduleId,
          "Uppercase",
          "Uppercase",
          Map("text" -> inputId),
          SemanticType.SString,
          None
        ),
        outputId -> IRNode.FieldAccessNode(outputId, moduleId, "result", SemanticType.SString, None)
      ),
      inputs = List(inputId),
      declaredOutputs = List("result"),
      variableBindings = Map("input" -> inputId, "processed" -> moduleId, "result" -> outputId)
    )

    val vizIR = DagVizCompiler.compile(ir)

    vizIR.nodes should have length 3
    vizIR.edges should have length 2

    // Check node types
    val nodeKinds = vizIR.nodes.map(n => n.id -> n.kind).toMap
    nodeKinds(inputId.toString) shouldBe NodeKind.Input
    nodeKinds(moduleId.toString) shouldBe NodeKind.Operation
    nodeKinds(outputId.toString) shouldBe NodeKind.Output // Marked as output because it's in declaredOutputs

    // Check edges exist
    val edgePairs = vizIR.edges.map(e => (e.source, e.target)).toSet
    edgePairs should contain((inputId.toString, moduleId.toString))
    edgePairs should contain((moduleId.toString, outputId.toString))
  }

  test("compile diamond pattern") {
    // A -> B, A -> C, B -> D, C -> D
    val aId = UUID.randomUUID()
    val bId = UUID.randomUUID()
    val cId = UUID.randomUUID()
    val dId = UUID.randomUUID()

    val ir = IRProgram(
      nodes = Map(
        aId -> IRNode.Input(aId, "a", SemanticType.SString, None),
        bId -> IRNode.ModuleCall(bId, "ModuleB", "ModuleB", Map("x" -> aId), SemanticType.SString, None),
        cId -> IRNode.ModuleCall(cId, "ModuleC", "ModuleC", Map("x" -> aId), SemanticType.SString, None),
        dId -> IRNode.MergeNode(dId, bId, cId, SemanticType.SRecord(Map("b" -> SemanticType.SString, "c" -> SemanticType.SString)), None)
      ),
      inputs = List(aId),
      declaredOutputs = List.empty,
      variableBindings = Map("a" -> aId, "b" -> bId, "c" -> cId, "d" -> dId)
    )

    val vizIR = DagVizCompiler.compile(ir)

    vizIR.nodes should have length 4
    vizIR.edges should have length 4 // a->b, a->c, b->d, c->d

    val nodeKinds = vizIR.nodes.map(n => n.id -> n.kind).toMap
    nodeKinds(aId.toString) shouldBe NodeKind.Input
    nodeKinds(bId.toString) shouldBe NodeKind.Operation
    nodeKinds(cId.toString) shouldBe NodeKind.Operation
    nodeKinds(dId.toString) shouldBe NodeKind.Merge
  }

  test("compile with literal node") {
    val literalId = UUID.randomUUID()
    val ir = IRProgram(
      nodes = Map(
        literalId -> IRNode.LiteralNode(literalId, 42, SemanticType.SInt, None)
      ),
      inputs = List.empty,
      declaredOutputs = List.empty,
      variableBindings = Map("x" -> literalId)
    )

    val vizIR = DagVizCompiler.compile(ir)

    vizIR.nodes should have length 1
    vizIR.nodes.head.kind shouldBe NodeKind.Literal
    vizIR.nodes.head.label shouldBe "42"
  }

  test("compile with guard node") {
    val inputId = UUID.randomUUID()
    val condId = UUID.randomUUID()
    val guardId = UUID.randomUUID()

    val ir = IRProgram(
      nodes = Map(
        inputId -> IRNode.Input(inputId, "value", SemanticType.SInt, None),
        condId -> IRNode.LiteralNode(condId, true, SemanticType.SBoolean, None),
        guardId -> IRNode.GuardNode(guardId, inputId, condId, SemanticType.SInt, None)
      ),
      inputs = List(inputId),
      declaredOutputs = List.empty,
      variableBindings = Map("value" -> inputId, "guarded" -> guardId)
    )

    val vizIR = DagVizCompiler.compile(ir)

    vizIR.nodes should have length 3
    val guardNode = vizIR.nodes.find(_.id == guardId.toString).get
    guardNode.kind shouldBe NodeKind.Guard
    guardNode.typeSignature shouldBe "Optional<Int>"
  }

  test("compile with record type abbreviation") {
    val inputId = UUID.randomUUID()
    val recordType = SemanticType.SRecord(Map(
      "field1" -> SemanticType.SString,
      "field2" -> SemanticType.SInt,
      "field3" -> SemanticType.SFloat,
      "field4" -> SemanticType.SBoolean,
      "field5" -> SemanticType.SString
    ))

    val ir = IRProgram(
      nodes = Map(
        inputId -> IRNode.Input(inputId, "data", recordType, None)
      ),
      inputs = List(inputId),
      declaredOutputs = List.empty,
      variableBindings = Map("data" -> inputId)
    )

    val vizIR = DagVizCompiler.compile(ir)

    // Should abbreviate to show 3 fields + count
    vizIR.nodes.head.typeSignature should include("...")
    vizIR.nodes.head.typeSignature should include("+2")
  }

  test("edge labels for module parameters") {
    val input1Id = UUID.randomUUID()
    val input2Id = UUID.randomUUID()
    val moduleId = UUID.randomUUID()

    val ir = IRProgram(
      nodes = Map(
        input1Id -> IRNode.Input(input1Id, "a", SemanticType.SInt, None),
        input2Id -> IRNode.Input(input2Id, "b", SemanticType.SInt, None),
        moduleId -> IRNode.ModuleCall(
          moduleId,
          "Add",
          "add",
          Map("left" -> input1Id, "right" -> input2Id),
          SemanticType.SInt,
          None
        )
      ),
      inputs = List(input1Id, input2Id),
      declaredOutputs = List.empty,
      variableBindings = Map("a" -> input1Id, "b" -> input2Id, "sum" -> moduleId)
    )

    val vizIR = DagVizCompiler.compile(ir)

    // Find edges going to the module
    val moduleEdges = vizIR.edges.filter(_.target == moduleId.toString)
    moduleEdges should have length 2

    val labels = moduleEdges.flatMap(_.label).toSet
    labels should contain("left")
    labels should contain("right")
  }
}
