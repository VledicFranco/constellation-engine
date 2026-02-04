package io.constellation.lang.semantic

import io.constellation.lang.ast.*
import io.constellation.lang.parser.ConstellationParser
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Comprehensive tests for @example annotation type checking.
  *
  * Tests cover:
  *   - Valid type combinations (String, Int, Float, Bool)
  *   - Type mismatches with clear error messages
  *   - Expression-based examples (variable refs, function calls)
  *   - Error message quality (source location, context)
  *
  * Note: The parser supports any valid expression as @example value. Record and list literals are
  * not supported in expressions, so examples must use literals, variable references, or function
  * calls.
  *
  * See Issue #109: https://github.com/VledicFranco/constellation-engine/issues/109
  */
class ExampleTypeCheckSpec extends AnyFlatSpec with Matchers {

  private def parse(source: String): Pipeline =
    ConstellationParser.parse(source).getOrElse(fail("Parse failed"))

  private def check(
      source: String,
      registry: FunctionRegistry = FunctionRegistry.empty
  ): Either[List[CompileError], TypedPipeline] =
    TypeChecker.check(parse(source), registry)

  // ==========================================================================
  // Valid Type Combinations - Primitive Types
  // ==========================================================================

  "TypeChecker @example validation" should "accept String example for String input" in {
    val source = """
      @example("hello")
      in text: String
      out text
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "accept Int example for Int input" in {
    val source = """
      @example(42)
      in count: Int
      out count
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "accept negative Int example for Int input" in {
    val source = """
      @example(-100)
      in offset: Int
      out offset
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "accept Float example for Float input" in {
    val source = """
      @example(3.14)
      in ratio: Float
      out ratio
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "accept Boolean true example for Boolean input" in {
    val source = """
      @example(true)
      in enabled: Boolean
      out enabled
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "accept Boolean false example for Boolean input" in {
    val source = """
      @example(false)
      in disabled: Boolean
      out disabled
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  // ==========================================================================
  // Type Mismatches - Primitive Types
  // ==========================================================================

  it should "reject Int example for String input" in {
    val source = """
      @example(42)
      in text: String
      out text
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.get.head.message should include("String")
    result.left.get.head.message should include("Int")
  }

  it should "reject String example for Int input" in {
    val source = """
      @example("hello")
      in count: Int
      out count
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.get.head.message should include("Int")
    result.left.get.head.message should include("String")
  }

  it should "reject Boolean example for String input" in {
    val source = """
      @example(true)
      in text: String
      out text
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  it should "reject Boolean example for Int input" in {
    val source = """
      @example(false)
      in count: Int
      out count
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  it should "reject String example for Boolean input" in {
    val source = """
      @example("true")
      in enabled: Boolean
      out enabled
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  it should "reject Int example for Boolean input" in {
    val source = """
      @example(1)
      in enabled: Boolean
      out enabled
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  it should "reject Float example for Int input" in {
    val source = """
      @example(3.14)
      in count: Int
      out count
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  it should "reject String example for Float input" in {
    val source = """
      @example("3.14")
      in ratio: Float
      out ratio
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  // ==========================================================================
  // Variable Reference Examples
  // ==========================================================================

  it should "accept variable reference with matching type" in {
    val source = """
      defaultValue = "hello"
      @example(defaultValue)
      in text: String
      out text
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "reject variable reference with mismatched type" in {
    val source = """
      defaultValue = 42
      @example(defaultValue)
      in text: String
      out text
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  it should "reject undefined variable in example" in {
    val source = """
      @example(undefinedVar)
      in text: String
      out text
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  // ==========================================================================
  // Function Call Examples
  // ==========================================================================

  it should "accept function call example with matching return type" in {
    // Create a registry with a function that returns String
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        "createDefault",
        List(),
        SemanticType.SString,
        "createDefault"
      )
    )

    val source = """
      @example(createDefault())
      in text: String
      out text
    """
    val result = check(source, registry)
    result.isRight shouldBe true
  }

  it should "reject function call example with mismatched return type" in {
    // Create a registry with a function that returns Int
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        "getCount",
        List(),
        SemanticType.SInt,
        "getCount"
      )
    )

    val source = """
      @example(getCount())
      in text: String
      out text
    """
    val result = check(source, registry)
    result.isLeft shouldBe true
  }

  // ==========================================================================
  // Multiple Annotations
  // ==========================================================================

  it should "accept multiple valid @example annotations" in {
    val source = """
      @example(10)
      @example(20)
      @example(30)
      in count: Int
      out count
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "reject if any @example annotation has mismatched type" in {
    val source = """
      @example(10)
      @example("invalid")
      @example(30)
      in count: Int
      out count
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  // ==========================================================================
  // Multiple Inputs with Mixed Annotations
  // ==========================================================================

  it should "validate each input's @example independently" in {
    val source = """
      @example("hello")
      in text: String
      @example(42)
      in count: Int
      out text
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "report error only for input with mismatched example" in {
    val source = """
      @example("hello")
      in text: String
      @example("invalid")
      in count: Int
      out text
    """
    val result = check(source)
    result.isLeft shouldBe true
    // Should have exactly one error (for the count input)
    result.left.get should have size 1
  }

  // ==========================================================================
  // Inputs Without Annotations (Backward Compatibility)
  // ==========================================================================

  it should "accept inputs without @example annotations" in {
    val source = """
      in text: String
      in count: Int
      out text
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "accept mixed inputs with and without annotations" in {
    val source = """
      @example("hello")
      in text: String
      in count: Int
      @example(true)
      in enabled: Boolean
      out text
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  // ==========================================================================
  // Error Message Quality
  // ==========================================================================

  it should "include source location in example type error" in {
    val source = """
      @example(42)
      in text: String
      out text
    """
    val result = check(source)
    result.isLeft shouldBe true
    val error = result.left.get.head
    error.span shouldBe defined
  }

  it should "include expected and actual types in error message" in {
    val source = """
      @example(42)
      in text: String
      out text
    """
    val result = check(source)
    result.isLeft shouldBe true
    val errorMsg = result.left.get.head.message
    errorMsg should include("String")
    errorMsg should include("Int")
  }

  // ==========================================================================
  // Complex Type Inputs
  // ==========================================================================

  it should "reject String example for Optional<String> input (strict type equality)" in {
    // Note: TypeChecker uses strict equality in isAssignable, so String != Optional<String>
    val source = """
      @example("hello")
      in text: Optional<String>
      out text
    """
    val result = check(source)
    // Strict type equality means String is NOT assignable to Optional<String>
    result.isLeft shouldBe true
  }

  it should "accept Optional<String> example for Optional<String> input via function" in {
    // Create a registry with a function that returns Optional<String>
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        "createOptional",
        List(),
        SemanticType.SOptional(SemanticType.SString),
        "createOptional"
      )
    )

    val source = """
      @example(createOptional())
      in text: Optional<String>
      out text
    """
    val result = check(source, registry)
    result.isRight shouldBe true
  }

  it should "accept String example for user-defined type alias" in {
    val source = """
      type Name = String
      @example("Alice")
      in name: Name
      out name
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  // ==========================================================================
  // Expression Composition in Examples
  // ==========================================================================

  it should "accept arithmetic expression with correct result type when stdlib registered" in {
    // Arithmetic expressions require stdlib functions (add, subtract, etc.)
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        "add",
        List("a" -> SemanticType.SInt, "b" -> SemanticType.SInt),
        SemanticType.SInt,
        "add"
      )
    )

    val source = """
      @example(1 + 2)
      in count: Int
      out count
    """
    val result = check(source, registry)
    result.isRight shouldBe true
  }

  it should "reject arithmetic expression when stdlib not registered" in {
    // Without add function registered, arithmetic fails
    val source = """
      @example(1 + 2)
      in count: Int
      out count
    """
    val result = check(source)
    // Fails because 'add' function not found
    result.isLeft shouldBe true
  }

  it should "accept conditional expression with matching type" in {
    val source = """
      @example(if (true) 1 else 2)
      in count: Int
      out count
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  // Note: Field access on record variables requires record literal syntax in assignments,
  // which is not supported by the parser. Use function calls to test field access.
  it should "accept field access on function call result with matching type" in {
    val recordType = SemanticType.SRecord(Map("name" -> SemanticType.SString))
    val registry   = FunctionRegistry.empty
    registry.register(
      FunctionSignature("createConfig", List(), recordType, "createConfig")
    )

    val source = """
      @example(createConfig().name)
      in text: String
      out text
    """
    val result = check(source, registry)
    result.isRight shouldBe true
  }

  it should "reject field access with mismatched type" in {
    val recordType = SemanticType.SRecord(Map("count" -> SemanticType.SInt))
    val registry   = FunctionRegistry.empty
    registry.register(
      FunctionSignature("createConfig", List(), recordType, "createConfig")
    )

    val source = """
      @example(createConfig().count)
      in text: String
      out text
    """
    val result = check(source, registry)
    result.isLeft shouldBe true
  }

  // ==========================================================================
  // Record Type Inputs
  // ==========================================================================

  // Note: Parser doesn't support record literals in variable assignments.
  // We use function calls to test record type validation.

  it should "accept function call returning record type for record input" in {
    val userType =
      SemanticType.SRecord(Map("name" -> SemanticType.SString, "age" -> SemanticType.SInt))
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature("createUser", List(), userType, "createUser")
    )

    val source = """
      type User = { name: String, age: Int }
      @example(createUser())
      in user: User
      out user
    """
    val result = check(source, registry)
    result.isRight shouldBe true
  }

  it should "reject function returning wrong record type for record input" in {
    val wrongType = SemanticType.SRecord(Map("x" -> SemanticType.SInt, "y" -> SemanticType.SInt))
    val registry  = FunctionRegistry.empty
    registry.register(
      FunctionSignature("createPoint", List(), wrongType, "createPoint")
    )

    val source = """
      type User = { name: String, age: Int }
      @example(createPoint())
      in user: User
      out user
    """
    val result = check(source, registry)
    result.isLeft shouldBe true
  }

  // ==========================================================================
  // List Type Inputs
  // ==========================================================================

  // Note: Parser doesn't support list literals in variable assignments.
  // We use function calls to test list type validation.

  it should "accept function call returning List type for List input" in {
    val listType = SemanticType.SList(SemanticType.SInt)
    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature("createNumbers", List(), listType, "createNumbers")
    )

    val source = """
      @example(createNumbers())
      in data: List<Int>
      out data
    """
    val result = check(source, registry)
    result.isRight shouldBe true
  }

  it should "reject function returning wrong List element type" in {
    val wrongListType = SemanticType.SList(SemanticType.SString)
    val registry      = FunctionRegistry.empty
    registry.register(
      FunctionSignature("createStrings", List(), wrongListType, "createStrings")
    )

    val source = """
      @example(createStrings())
      in data: List<Int>
      out data
    """
    val result = check(source, registry)
    result.isLeft shouldBe true
  }

  // ==========================================================================
  // Candidates Type Inputs (Candidates is a legacy alias for List)
  // ==========================================================================

  it should "accept function call for Candidates type input" in {
    val itemType = SemanticType.SRecord(Map("id" -> SemanticType.SString))
    val listType = SemanticType.SList(itemType)

    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        "createItems",
        List(),
        listType,
        "createItems"
      )
    )

    val source = """
      type Item = { id: String }
      @example(createItems())
      in items: Candidates<Item>
      out items
    """
    val result = check(source, registry)
    result.isRight shouldBe true
  }

  it should "reject function returning wrong Candidates element type" in {
    val wrongItemType = SemanticType.SRecord(Map("wrong" -> SemanticType.SInt))
    val wrongListType = SemanticType.SList(wrongItemType)

    val registry = FunctionRegistry.empty
    registry.register(
      FunctionSignature(
        "createWrongItems",
        List(),
        wrongListType,
        "createWrongItems"
      )
    )

    val source = """
      type Item = { id: String }
      @example(createWrongItems())
      in items: Candidates<Item>
      out items
    """
    val result = check(source, registry)
    result.isLeft shouldBe true
  }

  // ==========================================================================
  // List Literal Examples
  // ==========================================================================

  it should "accept list literal example for List<Int> input" in {
    val source = """
      @example([1, 2, 3])
      in numbers: List<Int>
      out numbers
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "accept list literal example for List<String> input" in {
    val source = """
      @example(["a", "b", "c"])
      in names: List<String>
      out names
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "accept list literal example for List<Boolean> input" in {
    val source = """
      @example([true, false, true])
      in flags: List<Boolean>
      out flags
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "accept list literal example for List<Float> input" in {
    val source = """
      @example([1.5, 2.5, 3.5])
      in values: List<Float>
      out values
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "accept empty list literal for any List type" in {
    val source = """
      @example([])
      in items: List<Int>
      out items
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "accept single element list literal" in {
    val source = """
      @example([42])
      in numbers: List<Int>
      out numbers
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "reject list literal with wrong element type" in {
    val source = """
      @example(["a", "b", "c"])
      in numbers: List<Int>
      out numbers
    """
    val result = check(source)
    result.isLeft shouldBe true
    result.left.get.head.message should include("List<Int>")
    result.left.get.head.message should include("List<String>")
  }

  it should "reject list literal with mixed element types as input example" in {
    val source = """
      @example([1, "two", 3])
      in items: List<Int>
      out items
    """
    val result = check(source)
    result.isLeft shouldBe true
    // With subtyping, mixed types produce a union: List<Int | String>
    // This fails because List<Int | String> is not assignable to List<Int>
    result.left.get.head.message should include("List<Int>")
  }

  it should "reject list literal for non-list input type" in {
    val source = """
      @example([1, 2, 3])
      in count: Int
      out count
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  it should "reject Int literal for List<Int> input" in {
    val source = """
      @example(42)
      in numbers: List<Int>
      out numbers
    """
    val result = check(source)
    result.isLeft shouldBe true
  }

  it should "accept list literal with negative integers" in {
    val source = """
      @example([-1, -2, -3])
      in offsets: List<Int>
      out offsets
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "validate multiple list literal examples" in {
    val source = """
      @example([1, 2, 3])
      @example([4, 5, 6])
      in numbers: List<Int>
      out numbers
    """
    val result = check(source)
    result.isRight shouldBe true
  }

  it should "reject if any list literal example has wrong element type" in {
    val source = """
      @example([1, 2, 3])
      @example(["a", "b"])
      in numbers: List<Int>
      out numbers
    """
    val result = check(source)
    result.isLeft shouldBe true
  }
}
