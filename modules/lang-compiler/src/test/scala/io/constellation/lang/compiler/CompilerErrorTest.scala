package io.constellation.lang.compiler

import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CompilerErrorTest extends AnyFlatSpec with Matchers {

  // ========== NodeNotFound Message Tests ==========

  "NodeNotFound" should "produce a message containing the node ID and context" in {
    val id    = UUID.fromString("00000000-0000-0000-0000-000000000001")
    val error = CompilerError.NodeNotFound(id, "dependency graph")

    error.message shouldBe "Node 00000000-0000-0000-0000-000000000001 not found in dependency graph"
  }

  it should "work with a random UUID" in {
    val id    = UUID.randomUUID()
    val error = CompilerError.NodeNotFound(id, "lookup table")

    error.message shouldBe s"Node $id not found in lookup table"
  }

  // ========== LambdaParameterNotBound Message Tests ==========

  "LambdaParameterNotBound" should "produce a message containing the parameter name" in {
    val error = CompilerError.LambdaParameterNotBound("x")

    error.message shouldBe "Lambda parameter 'x' not bound"
  }

  it should "handle multi-word parameter names" in {
    val error = CompilerError.LambdaParameterNotBound("myParam")

    error.message shouldBe "Lambda parameter 'myParam' not bound"
  }

  it should "handle empty parameter name" in {
    val error = CompilerError.LambdaParameterNotBound("")

    error.message shouldBe "Lambda parameter '' not bound"
  }

  // ========== UnsupportedOperation Message Tests ==========

  "UnsupportedOperation" should "produce a message containing the operation name" in {
    val error = CompilerError.UnsupportedOperation("pattern matching")

    error.message shouldBe "pattern matching is not yet implemented"
  }

  it should "handle descriptive operation strings" in {
    val error = CompilerError.UnsupportedOperation("recursive lambda compilation")

    error.message shouldBe "recursive lambda compilation is not yet implemented"
  }

  // ========== InvalidFieldAccess Message Tests ==========

  "InvalidFieldAccess" should "produce a message containing the field and actual type" in {
    val error = CompilerError.InvalidFieldAccess("name", "Int")

    error.message shouldBe "Cannot access field 'name' on Int"
  }

  it should "handle complex type descriptions" in {
    val error = CompilerError.InvalidFieldAccess("length", "List[String]")

    error.message shouldBe "Cannot access field 'length' on List[String]"
  }

  // ========== UnsupportedNodeType Message Tests ==========

  "UnsupportedNodeType" should "produce a message containing the node type and context" in {
    val error = CompilerError.UnsupportedNodeType("ConditionalNode", "lambda body")

    error.message shouldBe "Unsupported node type 'ConditionalNode' in lambda body"
  }

  it should "handle various context strings" in {
    val error = CompilerError.UnsupportedNodeType("LoopNode", "top-level compilation")

    error.message shouldBe "Unsupported node type 'LoopNode' in top-level compilation"
  }

  // ========== UnsupportedFunction Message Tests ==========

  "UnsupportedFunction" should "produce a message containing the module and function name" in {
    val error = CompilerError.UnsupportedFunction("MathModule", "factorial")

    error.message shouldBe "Unsupported function in lambda body: MathModule (funcName=factorial)"
  }

  it should "handle names with special characters" in {
    val error = CompilerError.UnsupportedFunction("Std.Math", "add_numbers")

    error.message shouldBe "Unsupported function in lambda body: Std.Math (funcName=add_numbers)"
  }

  // ========== toException Tests ==========

  "CompilerError.toException" should "produce an IllegalStateException for NodeNotFound" in {
    val id    = UUID.fromString("00000000-0000-0000-0000-000000000042")
    val error = CompilerError.NodeNotFound(id, "IR graph")
    val ex    = CompilerError.toException(error)

    ex shouldBe a[IllegalStateException]
    ex.getMessage shouldBe "Node 00000000-0000-0000-0000-000000000042 not found in IR graph"
  }

  it should "produce an IllegalStateException for LambdaParameterNotBound" in {
    val error = CompilerError.LambdaParameterNotBound("y")
    val ex    = CompilerError.toException(error)

    ex shouldBe a[IllegalStateException]
    ex.getMessage shouldBe "Lambda parameter 'y' not bound"
  }

  it should "produce an IllegalStateException for UnsupportedOperation" in {
    val error = CompilerError.UnsupportedOperation("tail call optimization")
    val ex    = CompilerError.toException(error)

    ex shouldBe a[IllegalStateException]
    ex.getMessage shouldBe "tail call optimization is not yet implemented"
  }

  it should "produce an IllegalStateException for InvalidFieldAccess" in {
    val error = CompilerError.InvalidFieldAccess("age", "String")
    val ex    = CompilerError.toException(error)

    ex shouldBe a[IllegalStateException]
    ex.getMessage shouldBe "Cannot access field 'age' on String"
  }

  it should "produce an IllegalStateException for UnsupportedNodeType" in {
    val error = CompilerError.UnsupportedNodeType("WhileNode", "DAG compilation")
    val ex    = CompilerError.toException(error)

    ex shouldBe a[IllegalStateException]
    ex.getMessage shouldBe "Unsupported node type 'WhileNode' in DAG compilation"
  }

  it should "produce an IllegalStateException for UnsupportedFunction" in {
    val error = CompilerError.UnsupportedFunction("IOModule", "readFile")
    val ex    = CompilerError.toException(error)

    ex shouldBe a[IllegalStateException]
    ex.getMessage shouldBe "Unsupported function in lambda body: IOModule (funcName=readFile)"
  }

  // ========== Sealed Trait / Pattern Matching Tests ==========

  "CompilerError sealed trait" should "support exhaustive pattern matching over all variants" in {
    val errors: List[CompilerError] = List(
      CompilerError.NodeNotFound(UUID.randomUUID(), "ctx"),
      CompilerError.LambdaParameterNotBound("p"),
      CompilerError.UnsupportedOperation("op"),
      CompilerError.InvalidFieldAccess("f", "T"),
      CompilerError.UnsupportedNodeType("N", "ctx"),
      CompilerError.UnsupportedFunction("M", "fn")
    )

    // Exhaustive match -- this would fail to compile if a variant were missing
    errors.foreach { error =>
      val description: String = error match {
        case CompilerError.NodeNotFound(_, _)          => "node-not-found"
        case CompilerError.LambdaParameterNotBound(_)  => "lambda-param"
        case CompilerError.UnsupportedOperation(_)     => "unsupported-op"
        case CompilerError.InvalidFieldAccess(_, _)    => "invalid-field"
        case CompilerError.UnsupportedNodeType(_, _)   => "unsupported-node"
        case CompilerError.UnsupportedFunction(_, _)   => "unsupported-func"
      }
      description should not be empty
    }
  }

  it should "allow polymorphic access to the message method" in {
    val error: CompilerError = CompilerError.UnsupportedOperation("closures")

    // Access via the trait interface, not the concrete type
    error.message shouldBe "closures is not yet implemented"
  }

  it should "distinguish different variants via isInstanceOf" in {
    val error: CompilerError = CompilerError.InvalidFieldAccess("x", "Boolean")

    error shouldBe a[CompilerError.InvalidFieldAccess]
    error should not be a[CompilerError.NodeNotFound]
    error should not be a[CompilerError.LambdaParameterNotBound]
  }

  // ========== Case Class Equality Tests ==========

  "CompilerError case classes" should "support structural equality" in {
    val id = UUID.fromString("11111111-1111-1111-1111-111111111111")

    CompilerError.NodeNotFound(id, "a") shouldBe CompilerError.NodeNotFound(id, "a")
    CompilerError.NodeNotFound(id, "a") should not be CompilerError.NodeNotFound(id, "b")

    CompilerError.LambdaParameterNotBound("x") shouldBe CompilerError.LambdaParameterNotBound("x")
    CompilerError.LambdaParameterNotBound("x") should not be CompilerError.LambdaParameterNotBound("y")

    CompilerError.UnsupportedOperation("a") shouldBe CompilerError.UnsupportedOperation("a")
    CompilerError.UnsupportedOperation("a") should not be CompilerError.UnsupportedOperation("b")

    CompilerError.InvalidFieldAccess("f", "T") shouldBe CompilerError.InvalidFieldAccess("f", "T")
    CompilerError.InvalidFieldAccess("f", "T") should not be CompilerError.InvalidFieldAccess("g", "T")

    CompilerError.UnsupportedNodeType("N", "c") shouldBe CompilerError.UnsupportedNodeType("N", "c")
    CompilerError.UnsupportedNodeType("N", "c") should not be CompilerError.UnsupportedNodeType("N", "d")

    CompilerError.UnsupportedFunction("M", "f") shouldBe CompilerError.UnsupportedFunction("M", "f")
    CompilerError.UnsupportedFunction("M", "f") should not be CompilerError.UnsupportedFunction("M", "g")
  }
}
