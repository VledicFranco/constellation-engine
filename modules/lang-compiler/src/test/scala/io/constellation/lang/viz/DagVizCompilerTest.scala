package io.constellation.lang.viz

import io.constellation.lang.compiler.{IRModuleCallOptions, IRNode, IRProgram}
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
    val fieldAccessId = UUID.randomUUID()

    val ir = IRProgram(
      nodes = Map(
        inputId -> IRNode.Input(inputId, "input", SemanticType.SString, None),
        moduleId -> IRNode.ModuleCall(
          moduleId,
          "Uppercase",
          "Uppercase",
          Map("text" -> inputId),
          SemanticType.SString,
          IRModuleCallOptions.empty,
          None
        ),
        fieldAccessId -> IRNode.FieldAccessNode(fieldAccessId, moduleId, "result", SemanticType.SString, None)
      ),
      inputs = List(inputId),
      declaredOutputs = List("result"),
      variableBindings = Map("input" -> inputId, "processed" -> moduleId, "result" -> fieldAccessId)
    )

    val vizIR = DagVizCompiler.compile(ir)

    // 4 nodes: Input, Operation, FieldAccess, and separate Output node
    vizIR.nodes should have length 4
    // 3 edges: input->module, module->fieldAccess, fieldAccess->output
    vizIR.edges should have length 3

    // Check node types
    val nodeKinds = vizIR.nodes.map(n => n.id -> n.kind).toMap
    nodeKinds(inputId.toString) shouldBe NodeKind.Input
    nodeKinds(moduleId.toString) shouldBe NodeKind.Operation
    nodeKinds(fieldAccessId.toString) shouldBe NodeKind.FieldAccess // Keeps original kind
    nodeKinds("output_result") shouldBe NodeKind.Output // Separate output node

    // Check edges exist
    val edgePairs = vizIR.edges.map(e => (e.source, e.target)).toSet
    edgePairs should contain((inputId.toString, moduleId.toString))
    edgePairs should contain((moduleId.toString, fieldAccessId.toString))
    edgePairs should contain((fieldAccessId.toString, "output_result")) // Edge to output node
  }

  test("operation node with declared output creates separate output node") {
    // sum = add(a, b); out sum
    // Should create: Input(a), Input(b), Operation(add), Output(sum)
    // With edges: a→add, b→add, add→sum
    val aId = UUID.randomUUID()
    val bId = UUID.randomUUID()
    val addId = UUID.randomUUID()

    val ir = IRProgram(
      nodes = Map(
        aId -> IRNode.Input(aId, "a", SemanticType.SInt, None),
        bId -> IRNode.Input(bId, "b", SemanticType.SInt, None),
        addId -> IRNode.ModuleCall(
          addId,
          "Add",
          "add",
          Map("left" -> aId, "right" -> bId),
          SemanticType.SInt,
          IRModuleCallOptions.empty,
          None
        )
      ),
      inputs = List(aId, bId),
      declaredOutputs = List("sum"),
      variableBindings = Map("a" -> aId, "b" -> bId, "sum" -> addId)
    )

    val vizIR = DagVizCompiler.compile(ir)

    // 4 nodes: Input(a), Input(b), Operation(add), Output(sum)
    vizIR.nodes should have length 4

    // Check node types and labels
    val nodeKinds = vizIR.nodes.map(n => n.id -> n.kind).toMap
    val nodeLabels = vizIR.nodes.map(n => n.id -> n.label).toMap

    nodeKinds(aId.toString) shouldBe NodeKind.Input
    nodeKinds(bId.toString) shouldBe NodeKind.Input
    nodeKinds(addId.toString) shouldBe NodeKind.Operation
    nodeLabels(addId.toString) shouldBe "add" // Operation label preserved!
    nodeKinds("output_sum") shouldBe NodeKind.Output
    nodeLabels("output_sum") shouldBe "sum"

    // 3 edges: a→add, b→add, add→sum
    vizIR.edges should have length 3

    val edgePairs = vizIR.edges.map(e => (e.source, e.target)).toSet
    edgePairs should contain((aId.toString, addId.toString))
    edgePairs should contain((bId.toString, addId.toString))
    edgePairs should contain((addId.toString, "output_sum"))

    // Check output edge has "value" label
    val outputEdge = vizIR.edges.find(_.target == "output_sum").get
    outputEdge.label shouldBe Some("value")
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
        bId -> IRNode.ModuleCall(bId, "ModuleB", "ModuleB", Map("x" -> aId), SemanticType.SString, IRModuleCallOptions.empty, None),
        cId -> IRNode.ModuleCall(cId, "ModuleC", "ModuleC", Map("x" -> aId), SemanticType.SString, IRModuleCallOptions.empty, None),
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
          IRModuleCallOptions.empty,
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
