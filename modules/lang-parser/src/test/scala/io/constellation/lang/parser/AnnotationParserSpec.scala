package io.constellation.lang.parser

import io.constellation.lang.ast.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

/** Comprehensive tests for @example annotation parsing.
  *
  * Tests cover:
  * - Basic annotation parsing (string, int, float, boolean literals)
  * - Expression-based examples (variable refs, function calls)
  * - Edge cases (inputs without annotations, multiple inputs, source locations)
  * - Error cases (malformed annotations, unclosed annotations, unknown annotations)
  *
  * Note: @example supports any valid expression. The parser does not currently
  * support record literals or list literals as expressions, so examples must
  * use literals, variable references, or function calls.
  *
  * See Issue #108: https://github.com/VledicFranco/constellation-engine/issues/108
  */
class AnnotationParserSpec extends AnyFlatSpec with Matchers {

  // ==========================================================================
  // Basic Annotation Parsing - Primitive Types
  // ==========================================================================

  "ConstellationParser" should "parse @example with string literal" in {
    val source = """
      @example("hello world")
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.name.value shouldBe "text"
    inputDecl.annotations should have size 1

    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.StringLit("hello world")
  }

  it should "parse @example with integer literal" in {
    val source = """
      @example(42)
      in count: Int
      out count
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.annotations should have size 1

    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.IntLit(42)
  }

  it should "parse @example with negative integer literal" in {
    val source = """
      @example(-100)
      in offset: Int
      out offset
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.annotations should have size 1

    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.IntLit(-100)
  }

  it should "parse @example with float literal" in {
    val source = """
      @example(3.14159)
      in pi: Float
      out pi
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.annotations should have size 1

    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.FloatLit(3.14159)
  }

  it should "parse @example with negative float via arithmetic" in {
    // Note: The parser supports signed integers but not signed floats directly.
    // To express negative floats, use arithmetic: 0 - 2.5 or 0.0 - 2.5
    val source = """
      @example(0.0 - 2.5)
      in temperature: Float
      out temperature
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.annotations should have size 1

    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.Arithmetic]
  }

  it should "parse @example with boolean true literal" in {
    val source = """
      @example(true)
      in enabled: Boolean
      out enabled
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.annotations should have size 1

    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.BoolLit(true)
  }

  it should "parse @example with boolean false literal" in {
    val source = """
      @example(false)
      in disabled: Boolean
      out disabled
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.annotations should have size 1

    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.BoolLit(false)
  }

  // ==========================================================================
  // Variable Reference and Function Call Examples
  // ==========================================================================

  it should "parse @example with variable reference expression" in {
    val source = """
      @example(someVar)
      in data: Int
      out data
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.VarRef]

    val variable = annotation.value.value.asInstanceOf[Expression.VarRef]
    variable.name shouldBe "someVar"
  }

  it should "parse @example with qualified variable reference" in {
    val source = """
      @example(config.defaultValue)
      in data: Int
      out data
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.FieldAccess]
  }

  it should "parse @example with function call expression" in {
    val source = """
      @example(createDefault(5))
      in data: Int
      out data
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.FunctionCall]

    val funcCall = annotation.value.value.asInstanceOf[Expression.FunctionCall]
    funcCall.name shouldBe QualifiedName.simple("createDefault")
    funcCall.args should have size 1
  }

  it should "parse @example with function call with multiple arguments" in {
    val source = """
      @example(makeRecord("Alice", 30))
      in user: { name: String, age: Int }
      out user
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.FunctionCall]

    val funcCall = annotation.value.value.asInstanceOf[Expression.FunctionCall]
    funcCall.name shouldBe QualifiedName.simple("makeRecord")
    funcCall.args should have size 2
  }

  it should "parse @example with qualified function call" in {
    val source = """
      @example(stdlib.examples.createUser())
      in user: { name: String }
      out user
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.FunctionCall]

    val funcCall = annotation.value.value.asInstanceOf[Expression.FunctionCall]
    funcCall.name shouldBe QualifiedName(List("stdlib", "examples", "createUser"))
  }

  it should "parse @example with nested function calls" in {
    val source = """
      @example(outer(inner(42)))
      in data: Int
      out data
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.FunctionCall]

    val outerCall = annotation.value.value.asInstanceOf[Expression.FunctionCall]
    outerCall.name shouldBe QualifiedName.simple("outer")
    outerCall.args should have size 1

    outerCall.args.head.value shouldBe a[Expression.FunctionCall]
    val innerCall = outerCall.args.head.value.asInstanceOf[Expression.FunctionCall]
    innerCall.name shouldBe QualifiedName.simple("inner")
  }

  // ==========================================================================
  // Edge Cases - Multiple Annotations, Multiple Inputs, No Annotations
  // ==========================================================================

  it should "parse multiple @example annotations on single input" in {
    val source = """
      @example(10)
      @example(20)
      @example(30)
      in value: Int
      out value
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.annotations should have size 3

    val values = inputDecl.annotations.map { ann =>
      ann.asInstanceOf[Annotation.Example].value.value.asInstanceOf[Expression.IntLit].value
    }
    values shouldBe List(10, 20, 30)
  }

  it should "parse input without annotation (backward compatibility)" in {
    val source = """
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.name.value shouldBe "text"
    inputDecl.annotations shouldBe empty
  }

  it should "parse multiple inputs with mixed annotation presence" in {
    val source = """
      @example("hello")
      in text: String
      in count: Int
      @example(100)
      in data: Int
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.declarations should have size 4

    val input1 = program.declarations(0).asInstanceOf[Declaration.InputDecl]
    input1.name.value shouldBe "text"
    input1.annotations should have size 1

    val input2 = program.declarations(1).asInstanceOf[Declaration.InputDecl]
    input2.name.value shouldBe "count"
    input2.annotations shouldBe empty

    val input3 = program.declarations(2).asInstanceOf[Declaration.InputDecl]
    input3.name.value shouldBe "data"
    input3.annotations should have size 1
  }

  it should "parse annotations with different expression types across inputs" in {
    val source = """
      @example("hello")
      in text: String
      @example(42)
      in count: Int
      @example(3.14)
      in ratio: Float
      @example(true)
      in enabled: Boolean
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.declarations should have size 5

    val textDecl    = program.declarations(0).asInstanceOf[Declaration.InputDecl]
    val textAnnot   = textDecl.annotations.head.asInstanceOf[Annotation.Example]
    textAnnot.value.value shouldBe Expression.StringLit("hello")

    val countDecl   = program.declarations(1).asInstanceOf[Declaration.InputDecl]
    val countAnnot  = countDecl.annotations.head.asInstanceOf[Annotation.Example]
    countAnnot.value.value shouldBe Expression.IntLit(42)

    val ratioDecl   = program.declarations(2).asInstanceOf[Declaration.InputDecl]
    val ratioAnnot  = ratioDecl.annotations.head.asInstanceOf[Annotation.Example]
    ratioAnnot.value.value shouldBe Expression.FloatLit(3.14)

    val enabledDecl = program.declarations(3).asInstanceOf[Declaration.InputDecl]
    val enabledAnn  = enabledDecl.annotations.head.asInstanceOf[Annotation.Example]
    enabledAnn.value.value shouldBe Expression.BoolLit(true)
  }

  // ==========================================================================
  // Source Location Tracking
  // ==========================================================================

  it should "track source location of annotation value" in {
    val source = "@example(42)\nin x: Int\nout x"
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]

    // The Located wrapper should have a valid span
    annotation.value.span.start should be >= 0
    annotation.value.span.end should be > annotation.value.span.start
  }

  it should "track source location for function call annotation expressions" in {
    val source = "@example(createUser(\"Alice\"))\nin x: { name: String }\nout x"
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]

    annotation.value.span.start should be >= 0
    annotation.value.span.end should be > annotation.value.span.start
  }

  // ==========================================================================
  // Integration with Type Definitions
  // ==========================================================================

  it should "parse @example with type reference" in {
    val source = """
      type User = { name: String, age: Int }
      @example(createUser("test", 25))
      in user: User
      out user
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.declarations should have size 3

    val typeDef = program.declarations.head.asInstanceOf[Declaration.TypeDef]
    typeDef.name.value shouldBe "User"

    val inputDecl = program.declarations(1).asInstanceOf[Declaration.InputDecl]
    inputDecl.typeExpr.value shouldBe TypeExpr.TypeRef("User")
    inputDecl.annotations should have size 1
  }

  it should "parse @example with Candidates type" in {
    val source = """
      type Item = { id: String, score: Float }
      @example(createItems())
      in candidates: Candidates<Item>
      out candidates
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations(1).asInstanceOf[Declaration.InputDecl]
    inputDecl.typeExpr.value shouldBe a[TypeExpr.Parameterized]

    val paramType = inputDecl.typeExpr.value.asInstanceOf[TypeExpr.Parameterized]
    paramType.name shouldBe "Candidates"

    inputDecl.annotations should have size 1
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.FunctionCall]
  }

  // ==========================================================================
  // Full Program Integration
  // ==========================================================================

  it should "parse complex program with multiple annotated inputs" in {
    val source = """
      type Communication = {
        id: String,
        content: String,
        channel: String
      }

      @example(createCommunications())
      in communications: Candidates<Communication>

      @example(12345)
      in userId: Int

      embeddings = embed-model(communications)
      scores = ranking-model(embeddings, userId)
      result = communications[id, channel] + scores

      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    // 1 type + 2 inputs + 3 assignments + 1 output = 7 declarations
    program.declarations should have size 7

    val commInput = program.declarations(1).asInstanceOf[Declaration.InputDecl]
    commInput.name.value shouldBe "communications"
    commInput.annotations should have size 1

    val userInput = program.declarations(2).asInstanceOf[Declaration.InputDecl]
    userInput.name.value shouldBe "userId"
    userInput.annotations should have size 1
  }

  it should "parse program with use declarations and annotated inputs" in {
    val source = """
      use stdlib.examples as ex
      @example(ex.defaultUser())
      in user: { name: String }
      out user
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.declarations should have size 3

    val useDecl = program.declarations.head.asInstanceOf[Declaration.UseDecl]
    useDecl.path.value shouldBe QualifiedName(List("stdlib", "examples"))

    val inputDecl = program.declarations(1).asInstanceOf[Declaration.InputDecl]
    inputDecl.annotations should have size 1
  }

  // ==========================================================================
  // Error Cases
  // ==========================================================================

  it should "fail on malformed annotation (missing closing paren)" in {
    val source = """
      @example("unclosed
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isLeft shouldBe true
  }

  it should "fail on annotation with missing expression" in {
    val source = """
      @example()
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isLeft shouldBe true
  }

  it should "fail on annotation with multiple expressions (no comma support)" in {
    val source = """
      @example("a", "b")
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isLeft shouldBe true
  }

  it should "fail on unknown annotation type" in {
    val source = """
      @unknown(42)
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    // Parser should fail because only @example is supported
    result.isLeft shouldBe true
  }

  it should "fail on annotation without parentheses" in {
    val source = """
      @example
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isLeft shouldBe true
  }

  it should "fail on annotation with unclosed parenthesis" in {
    val source = """
      @example(42
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isLeft shouldBe true
  }

  it should "provide error position for malformed annotation" in {
    val source = "@example()\nin x: Int\nout x"
    val result = ConstellationParser.parse(source)
    result.isLeft shouldBe true
    val error = result.left.toOption.get
    error.span.isDefined shouldBe true
  }

  // ==========================================================================
  // Whitespace and Formatting
  // ==========================================================================

  it should "parse annotation with extra whitespace" in {
    val source = """
      @example(   "spaced"   )
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.StringLit("spaced")
  }

  it should "parse annotation on same line as input" in {
    val source = """@example(42) in count: Int
out count"""
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.annotations should have size 1
  }

  it should "parse multiple annotations on same line" in {
    val source = """@example(1) @example(2) @example(3) in value: Int
out value"""
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.annotations should have size 3
  }

  it should "parse annotation with comment before input" in {
    val source = """
      @example("test")
      # This is a comment
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    inputDecl.annotations should have size 1
  }

  // ==========================================================================
  // String Edge Cases
  // ==========================================================================

  it should "parse @example with escaped characters in string" in {
    val source = """
      @example("hello\nworld")
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.StringLit]
  }

  it should "parse @example with empty string" in {
    val source = """
      @example("")
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.StringLit("")
  }

  it should "parse @example with string containing parentheses" in {
    val source = """
      @example("(nested) parens")
      in text: String
      out text
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.StringLit("(nested) parens")
  }

  it should "parse @example with string containing special characters" in {
    val source = """
      @example("hello@world.com")
      in email: String
      out email
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.StringLit("hello@world.com")
  }

  // ==========================================================================
  // Numeric Edge Cases
  // ==========================================================================

  it should "parse @example with zero" in {
    val source = """
      @example(0)
      in value: Int
      out value
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.IntLit(0)
  }

  it should "parse @example with large integer" in {
    val source = """
      @example(9223372036854775807)
      in value: Int
      out value
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.IntLit(Long.MaxValue)
  }

  it should "parse @example with float zero" in {
    val source = """
      @example(0.0)
      in value: Float
      out value
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.FloatLit(0.0)
  }

  it should "parse @example with scientific notation style float" in {
    val source = """
      @example(1.5)
      in value: Float
      out value
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe Expression.FloatLit(1.5)
  }

  // ==========================================================================
  // Expression Composition in Annotations
  // ==========================================================================

  it should "parse @example with arithmetic expression" in {
    val source = """
      @example(1 + 2)
      in value: Int
      out value
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.Arithmetic]
  }

  it should "parse @example with conditional expression" in {
    val source = """
      @example(if (true) 1 else 2)
      in value: Int
      out value
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.Conditional]
  }

  it should "parse @example with string interpolation" in {
    val source = """
      @example("Hello, ${name}!")
      in greeting: String
      out greeting
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.StringInterpolation]
  }

  // ==========================================================================
  // List Literal Examples
  // ==========================================================================

  it should "parse @example with empty list literal" in {
    val source = """
      @example([])
      in items: List<Int>
      out items
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.ListLit]

    val listLit = annotation.value.value.asInstanceOf[Expression.ListLit]
    listLit.elements shouldBe empty
  }

  it should "parse @example with list of integers" in {
    val source = """
      @example([1, 2, 3])
      in numbers: List<Int>
      out numbers
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.ListLit]

    val listLit = annotation.value.value.asInstanceOf[Expression.ListLit]
    listLit.elements should have size 3
    listLit.elements.map(_.value) shouldBe List(
      Expression.IntLit(1),
      Expression.IntLit(2),
      Expression.IntLit(3)
    )
  }

  it should "parse @example with list of strings" in {
    val source = """
      @example(["Alice", "Bob", "Charlie"])
      in names: List<String>
      out names
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.ListLit]

    val listLit = annotation.value.value.asInstanceOf[Expression.ListLit]
    listLit.elements should have size 3
    listLit.elements.map(_.value) shouldBe List(
      Expression.StringLit("Alice"),
      Expression.StringLit("Bob"),
      Expression.StringLit("Charlie")
    )
  }

  it should "parse @example with list of booleans" in {
    val source = """
      @example([true, false, true])
      in flags: List<Boolean>
      out flags
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.ListLit]

    val listLit = annotation.value.value.asInstanceOf[Expression.ListLit]
    listLit.elements should have size 3
  }

  it should "parse @example with list of floats" in {
    val source = """
      @example([1.5, 2.7, 3.14])
      in values: List<Float>
      out values
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.ListLit]

    val listLit = annotation.value.value.asInstanceOf[Expression.ListLit]
    listLit.elements should have size 3
  }

  it should "parse @example with single element list" in {
    val source = """
      @example([42])
      in data: List<Int>
      out data
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.ListLit]

    val listLit = annotation.value.value.asInstanceOf[Expression.ListLit]
    listLit.elements should have size 1
    listLit.elements.head.value shouldBe Expression.IntLit(42)
  }

  it should "parse @example with list containing negative integers" in {
    val source = """
      @example([-1, -2, -3])
      in offsets: List<Int>
      out offsets
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.ListLit]

    val listLit = annotation.value.value.asInstanceOf[Expression.ListLit]
    listLit.elements.map(_.value) shouldBe List(
      Expression.IntLit(-1),
      Expression.IntLit(-2),
      Expression.IntLit(-3)
    )
  }

  it should "parse @example with list with extra whitespace" in {
    val source = """
      @example([  1  ,  2  ,  3  ])
      in numbers: List<Int>
      out numbers
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]
    annotation.value.value shouldBe a[Expression.ListLit]
  }

  it should "track source location of list literal annotation" in {
    val source = "@example([1, 2, 3])\nin nums: List<Int>\nout nums"
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val inputDecl  = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    val annotation = inputDecl.annotations.head.asInstanceOf[Annotation.Example]

    annotation.value.span.start should be >= 0
    annotation.value.span.end should be > annotation.value.span.start
  }
}
