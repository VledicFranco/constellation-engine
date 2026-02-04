package io.constellation.lang.semantic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.compiler.DagCompiler
import io.constellation.lang.ast.CompileError

/** Tests for bidirectional type inference.
  *
  * These tests verify that bidirectional type checking enables:
  *   - Lambda parameter inference from function context
  *   - Empty list typing from expected context
  *   - Enhanced error messages with context
  */
class BidirectionalTypeCheckerSpec extends AnyFlatSpec with Matchers {

  // Create a registry with higher-order functions for testing
  // Mirrors the setup from LangCompilerTest's hofCompiler
  private def createRegistry: FunctionRegistry = {
    val registry = FunctionRegistry.empty

    // filter: (List<Int>, (Int) => Boolean) => List<Int>
    registry.register(
      FunctionSignature(
        name = "filter",
        params = List(
          "items"     -> SemanticType.SList(SemanticType.SInt),
          "predicate" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SBoolean)
        ),
        returns = SemanticType.SList(SemanticType.SInt),
        moduleName = "stdlib.hof.filter-int",
        namespace = Some("stdlib.collection")
      )
    )

    // filter-records: For testing record lambdas
    registry.register(
      FunctionSignature(
        name = "filter-users",
        params = List(
          "users" -> SemanticType.SList(
            SemanticType.SRecord(
              Map(
                "name"   -> SemanticType.SString,
                "active" -> SemanticType.SBoolean
              )
            )
          ),
          "predicate" -> SemanticType.SFunction(
            List(
              SemanticType.SRecord(
                Map(
                  "name"   -> SemanticType.SString,
                  "active" -> SemanticType.SBoolean
                )
              )
            ),
            SemanticType.SBoolean
          )
        ),
        returns = SemanticType.SList(
          SemanticType.SRecord(
            Map(
              "name"   -> SemanticType.SString,
              "active" -> SemanticType.SBoolean
            )
          )
        ),
        moduleName = "stdlib.hof.filter-users"
      )
    )

    // map: (List<Int>, (Int) => String) => List<String>
    registry.register(
      FunctionSignature(
        name = "map-to-string",
        params = List(
          "items"     -> SemanticType.SList(SemanticType.SInt),
          "transform" -> SemanticType.SFunction(List(SemanticType.SInt), SemanticType.SString)
        ),
        returns = SemanticType.SList(SemanticType.SString),
        moduleName = "stdlib.hof.map-to-string"
      )
    )

    // Comparison functions
    registry.register(
      FunctionSignature(
        name = "gt",
        params = List("left" -> SemanticType.SInt, "right" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.gt"
      )
    )

    registry.register(
      FunctionSignature(
        name = "lt",
        params = List("left" -> SemanticType.SInt, "right" -> SemanticType.SInt),
        returns = SemanticType.SBoolean,
        moduleName = "stdlib.lt"
      )
    )

    // Arithmetic
    registry.register(
      FunctionSignature(
        name = "multiply",
        params = List("left" -> SemanticType.SInt, "right" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "stdlib.multiply"
      )
    )

    // Simple identity for testing
    registry.register(
      FunctionSignature(
        name = "Identity",
        params = List("value" -> SemanticType.SInt),
        returns = SemanticType.SInt,
        moduleName = "Identity"
      )
    )

    registry
  }

  private def parse(source: String) = ConstellationParser.parse(source)

  private def typeCheck(source: String): Either[List[CompileError], TypedPipeline] =
    parse(source) match {
      case Left(err)      => Left(List(err))
      case Right(program) => TypeChecker.check(program, createRegistry)
    }

  // ============================================================================
  // Lambda Parameter Inference Tests
  // ============================================================================

  "Bidirectional type inference" should "infer lambda parameter types from filter context" in {
    // Note: Lambda syntax requires parentheses around parameters: (x) => ...
    val source = """
      |in users: List<{ name: String, active: Boolean }>
      |active = filter-users(users, (u) => u.active)
      |out active
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  it should "infer lambda parameter type for Int filter" in {
    val source = """
      |in numbers: List<Int>
      |positives = filter(numbers, (n) => n > 0)
      |out positives
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  it should "infer lambda parameter type in map-to-string" in {
    val source = """
      |in numbers: List<Int>
      |strings = map-to-string(numbers, (n) => "${n * 2}")
      |out strings
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  it should "allow explicit annotations that match expected type" in {
    val source = """
      |in numbers: List<Int>
      |filtered = filter(numbers, (n: Int) => n > 5)
      |out filtered
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  it should "reject lambda with wrong number of parameters" in {
    val source = """
      |in numbers: List<Int>
      |filtered = filter(numbers, (a, b) => a > b)
      |out filtered
    """.stripMargin

    val result = typeCheck(source)
    result.isLeft shouldBe true
    result.swap.toOption.get.head.message should include("parameter")
  }

  it should "still require annotations when context unavailable" in {
    // Lambda without function context needs explicit types
    val source = """
      |transform = ((x: Int) => x)
      |out transform
    """.stripMargin

    // This should compile because we have explicit annotation
    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  it should "fail for lambda without annotations when no context" in {
    // This tests the parse error case - lambdas need parentheses
    // Standalone lambda assignment without type annotation will fail
    val source = """
      |in x: Int
      |double = ((y) => y)
      |out double
    """.stripMargin

    val result = typeCheck(source)
    result.isLeft shouldBe true
    // Should mention type annotation
    result.swap.toOption.get.head.message should include("type annotation")
  }

  it should "work with nested field access in lambda body" in {
    val source = """
      |in users: List<{ name: String, active: Boolean }>
      |active = filter-users(users, (user) => user.active)
      |out active
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  // ============================================================================
  // Empty List Typing Tests
  // ============================================================================

  it should "type empty list in inference mode as List<Nothing>" in {
    val source = """
      |empty = []
      |out empty
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
    // The type should be List<Nothing> which is subtype of any List<T>
  }

  it should "allow empty list as function argument matching List type" in {
    // This tests that List<Nothing> is subtype of List<Int>
    val source = """
      |filtered = filter([], (n) => n > 0)
      |out filtered
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  // ============================================================================
  // Subsumption Rule Tests
  // ============================================================================

  it should "apply subsumption rule for compatible types" in {
    // SNothing is subtype of any type, so List<Nothing> is subtype of List<Int>
    val source = """
      |empty = []
      |combined = filter(empty, (n) => n > 0)
      |out combined
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  // ============================================================================
  // Error Message Tests
  // ============================================================================

  it should "provide helpful error message for lambda without context" in {
    val source = """
      |f = ((x) => x)
      |out f
    """.stripMargin

    val result = typeCheck(source)
    result.isLeft shouldBe true
    val error = result.swap.toOption.get.head.message
    error should include("type annotation")
  }

  it should "report type mismatch errors clearly" in {
    val source = """
      |in text: String
      |result = Identity(text)
      |out result
    """.stripMargin

    val result = typeCheck(source)
    result.isLeft shouldBe true
    val error = result.swap.toOption.get.head.message
    error should (include("Int") and include("String"))
  }

  // ============================================================================
  // Complex Scenario Tests
  // ============================================================================

  it should "handle lambda with comparison in body" in {
    val source = """
      |in numbers: List<Int>
      |big = filter(numbers, (x) => x > 100)
      |out big
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  it should "handle lambda with arithmetic in body" in {
    val source = """
      |in numbers: List<Int>
      |strings = map-to-string(numbers, (n) => "${n * 2}")
      |out strings
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  // ============================================================================
  // Backward Compatibility Tests
  // ============================================================================

  it should "still work with explicit type annotations everywhere" in {
    val source = """
      |in numbers: List<Int>
      |filtered = filter(numbers, (n: Int) => n > 0)
      |out filtered
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  it should "accept non-empty lists with correct element types" in {
    val source = """
      |filtered = filter([1, 2, 3], (n) => n > 0)
      |out filtered
    """.stripMargin

    val result = typeCheck(source)
    result.isRight shouldBe true
  }

  it should "reject non-empty lists with wrong element types" in {
    val source = """
      |filtered = filter(["a", "b"], (n) => n > 0)
      |out filtered
    """.stripMargin

    val result = typeCheck(source)
    result.isLeft shouldBe true
    val error = result.swap.toOption.get.head.message
    error should include("Int")
    error should include("String")
  }
}

/** Tests for the Mode types */
class ModeSpec extends AnyFlatSpec with Matchers {

  import Mode.*
  import Mode.TypeContext.*

  "Mode.Infer" should "describe itself correctly" in {
    Infer.describe shouldBe "inference mode"
  }

  "Mode.Check" should "describe itself with expected type" in {
    val mode = Check(SemanticType.SInt)
    mode.describe should include("Int")
    mode.describe should include("checking mode")
  }

  "TypeContext.FunctionArgument" should "describe argument position" in {
    val ctx = FunctionArgument("Filter", 1, "predicate")
    ctx.describe should include("argument 2")
    ctx.describe should include("predicate")
    ctx.describe should include("Filter")
  }

  "TypeContext.LambdaBody" should "describe expected return type" in {
    val ctx = LambdaBody(SemanticType.SBoolean)
    ctx.describe should include("Boolean")
    ctx.describe should include("lambda body")
  }

  "TypeContext.ListElement" should "describe element position" in {
    val ctx = ListElement(2, SemanticType.SString)
    ctx.describe should include("element 2")
    ctx.describe should include("String")
  }

  "TypeContext.RecordField" should "describe field name" in {
    val ctx = RecordField("name", SemanticType.SString)
    ctx.describe should include("name")
    ctx.describe should include("String")
  }
}
