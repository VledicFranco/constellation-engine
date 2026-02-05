package io.constellation.lang.semantic

import io.constellation.lang.ast.CompileError
import io.constellation.lang.parser.ConstellationParser
import io.constellation.lang.semantic.SemanticType.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Integration tests for row polymorphism with full type checking.
  *
  * These tests verify that row-polymorphic functions work correctly through the entire compilation
  * pipeline.
  */
class RowPolymorphismIntegrationTest extends AnyFlatSpec with Matchers {

  /** Create a FunctionRegistry with row-polymorphic test functions */
  def createTestRegistry(): FunctionRegistry = {
    val registry = new InMemoryFunctionRegistry()

    // GetName: ∀ρ. { name: String | ρ } -> String
    val nameRowVar = RowVar(1001)
    registry.register(
      FunctionSignature(
        name = "GetName",
        params = List(("record", SOpenRecord(Map("name" -> SString), nameRowVar))),
        returns = SString,
        moduleName = "test.get-name",
        namespace = Some("test.record"),
        rowVars = List(nameRowVar)
      )
    )

    // GetAge: ∀ρ. { age: Int | ρ } -> Int
    val ageRowVar = RowVar(1002)
    registry.register(
      FunctionSignature(
        name = "GetAge",
        params = List(("record", SOpenRecord(Map("age" -> SInt), ageRowVar))),
        returns = SInt,
        moduleName = "test.get-age",
        namespace = Some("test.record"),
        rowVars = List(ageRowVar)
      )
    )

    // GetId: ∀ρ. { id: Int | ρ } -> Int
    val idRowVar = RowVar(1003)
    registry.register(
      FunctionSignature(
        name = "GetId",
        params = List(("record", SOpenRecord(Map("id" -> SInt), idRowVar))),
        returns = SInt,
        moduleName = "test.get-id",
        namespace = Some("test.record"),
        rowVars = List(idRowVar)
      )
    )

    // GetValue: ∀ρ. { value: String | ρ } -> String
    val valueRowVar = RowVar(1004)
    registry.register(
      FunctionSignature(
        name = "GetValue",
        params = List(("record", SOpenRecord(Map("value" -> SString), valueRowVar))),
        returns = SString,
        moduleName = "test.get-value",
        namespace = Some("test.record"),
        rowVars = List(valueRowVar)
      )
    )

    // Also add a non-row-polymorphic function for comparison
    registry.register(
      FunctionSignature(
        name = "StringLength",
        params = List(("str", SString)),
        returns = SInt,
        moduleName = "test.string-length",
        namespace = Some("test.string")
      )
    )

    registry
  }

  private def parse(source: String) =
    ConstellationParser.parse(source).getOrElse(fail("Parse failed"))

  private def check(
      source: String,
      registry: FunctionRegistry = createTestRegistry()
  ): Either[List[CompileError], TypedPipeline] =
    TypeChecker.check(parse(source), registry)

  // ===========================================================================
  // Basic Row Polymorphism Tests
  // ===========================================================================

  "Row polymorphism" should "allow extra fields to pass through to GetName" in {
    val source = """
      |use test.record
      |
      |in user: { name: String, age: Int, email: String }
      |name = GetName(user)
      |out name
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true
  }

  it should "allow extra fields to pass through to GetAge" in {
    val source = """
      |use test.record
      |
      |in person: { name: String, age: Int, department: String, salary: Int }
      |age = GetAge(person)
      |out age
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true
  }

  it should "allow extra fields to pass through to GetId" in {
    val source = """
      |use test.record
      |
      |in entity: { id: Int, kind: String, data: String }
      |entityId = GetId(entity)
      |out entityId
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true
  }

  it should "allow exact fields (no extra)" in {
    val source = """
      |use test.record
      |
      |in minimal: { name: String }
      |name = GetName(minimal)
      |out name
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true
  }

  // ===========================================================================
  // Error Cases
  // ===========================================================================

  it should "reject records missing required fields" in {
    val source = """
      |use test.record
      |
      |in partial: { age: Int, email: String }
      |name = GetName(partial)
      |out name
    """.stripMargin

    val result = check(source)
    result.isLeft shouldBe true
    result.swap.toOption.get.head.message should include("name")
  }

  it should "reject records with wrong field type" in {
    val source = """
      |use test.record
      |
      |in wrong: { name: Int, age: Int }
      |name = GetName(wrong)
      |out name
    """.stripMargin

    val result = check(source)
    result.isLeft shouldBe true
    // Should mention type mismatch
    result.swap.toOption.get.head.message should (include("String") or include("Int"))
  }

  // ===========================================================================
  // Chaining Row-Polymorphic Functions
  // ===========================================================================

  it should "work with chained row-polymorphic function calls" in {
    val source = """
      |use test.record
      |
      |in data: { name: String, age: Int, value: String }
      |extractedName = GetName(data)
      |extractedAge = GetAge(data)
      |extractedValue = GetValue(data)
      |out extractedName
      |out extractedAge
      |out extractedValue
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true
  }

  // ===========================================================================
  // Record Literals with Row Polymorphism
  // ===========================================================================

  it should "work with input record having many fields" in {
    // Note: Record literals in expressions are not fully supported by the parser,
    // so we test with input declarations instead
    val source = """
      |use test.record
      |
      |in record: { name: String, age: Int, dept: String }
      |name = GetName(record)
      |out name
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true
  }

  // ===========================================================================
  // Nested Records with Row Polymorphism
  // ===========================================================================

  it should "work with nested records" in {
    val source = """
      |use test.record
      |
      |in person: { name: String, address: { city: String, zip: String } }
      |name = GetName(person)
      |out name
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true
  }

  // ===========================================================================
  // Multiple Row-Polymorphic Calls
  // ===========================================================================

  it should "handle multiple independent row-polymorphic calls" in {
    val source = """
      |use test.record
      |
      |in user1: { name: String, role: String }
      |in user2: { name: String, department: String }
      |name1 = GetName(user1)
      |name2 = GetName(user2)
      |out name1
      |out name2
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true
  }

  // ===========================================================================
  // Return Type Verification
  // ===========================================================================

  it should "produce correct return types" in {
    val source = """
      |use test.record
      |
      |in data: { name: String, age: Int, value: String }
      |name = GetName(data)
      |age = GetAge(data)
      |out name
      |out age
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true

    val typedPipeline = result.toOption.get
    val outputs       = typedPipeline.outputs

    // Find name output - should be String
    val nameOutput = outputs.find(_._1 == "name")
    nameOutput shouldBe defined
    nameOutput.get._2 shouldBe SString

    // Find age output - should be Int
    val ageOutput = outputs.find(_._1 == "age")
    ageOutput shouldBe defined
    ageOutput.get._2 shouldBe SInt
  }

  // ===========================================================================
  // Mixed Row-Polymorphic and Regular Functions
  // ===========================================================================

  it should "work with mix of row-polymorphic and regular functions" in {
    val source = """
      |use test.record
      |use test.string
      |
      |in data: { name: String, age: Int }
      |name = GetName(data)
      |nameLen = StringLength(name)
      |out nameLen
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true

    val typedPipeline = result.toOption.get
    val outputs       = typedPipeline.outputs

    // nameLen should be Int
    val nameLenOutput = outputs.find(_._1 == "nameLen")
    nameLenOutput shouldBe defined
    nameLenOutput.get._2 shouldBe SInt
  }

  // ===========================================================================
  // Edge Cases
  // ===========================================================================

  it should "handle records with many extra fields" in {
    val source = """
      |use test.record
      |
      |in big: { name: String, a: Int, b: Int, c: Int, d: Int, e: Int, f: Int }
      |name = GetName(big)
      |out name
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true
  }

  it should "handle same-named function calls on different records" in {
    val source = """
      |use test.record
      |
      |in user: { name: String, email: String }
      |in product: { name: String, price: Int }
      |userName = GetName(user)
      |productName = GetName(product)
      |out userName
      |out productName
    """.stripMargin

    val result = check(source)
    result.isRight shouldBe true
  }
}
