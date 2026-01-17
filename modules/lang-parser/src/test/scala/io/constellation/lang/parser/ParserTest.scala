package io.constellation.lang.parser

import io.constellation.lang.ast.*
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ParserTest extends AnyFlatSpec with Matchers {

  "ConstellationParser" should "parse simple type definitions" in {
    val source = """
      type Message = {
        id: String,
        content: String
      }
      out x
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.declarations should have size 1
    program.declarations.head shouldBe a[Declaration.TypeDef]

    val typeDef = program.declarations.head.asInstanceOf[Declaration.TypeDef]
    typeDef.name.value shouldBe "Message"
    typeDef.definition.value shouldBe a[TypeExpr.Record]

    val record = typeDef.definition.value.asInstanceOf[TypeExpr.Record]
    record.fields.map(_._1) shouldBe List("id", "content")
  }

  it should "parse parameterized types" in {
    val source = """
      type Item = { name: String }
      in items: Candidates<Item>
      out items
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.declarations should have size 2
    val inputDecl = program.declarations(1).asInstanceOf[Declaration.InputDecl]
    inputDecl.typeExpr.value shouldBe a[TypeExpr.Parameterized]

    val paramType = inputDecl.typeExpr.value.asInstanceOf[TypeExpr.Parameterized]
    paramType.name shouldBe "Candidates"
    paramType.params should have size 1
    paramType.params.head shouldBe TypeExpr.TypeRef("Item")
  }

  it should "parse nested parameterized types" in {
    val source = """
      in data: List<Map<String, Int>>
      out data
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
  }

  it should "parse input declarations" in {
    val source = """
      in userId: Int
      in name: String
      out userId
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.declarations should have size 2
    program.declarations.foreach(_ shouldBe a[Declaration.InputDecl])
  }

  it should "parse function calls" in {
    val source = """
      in x: Int
      result = compute-something(x)
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.declarations should have size 2
    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.FunctionCall]

    val funcCall = assignment.value.value.asInstanceOf[Expression.FunctionCall]
    funcCall.name shouldBe "compute-something"
    funcCall.args should have size 1
  }

  it should "parse function calls with multiple arguments" in {
    val source = """
      in x: Int
      in y: Int
      result = add(x, y)
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val funcCall = assignment.value.value.asInstanceOf[Expression.FunctionCall]
    funcCall.args should have size 2
  }

  it should "parse merge expressions" in {
    val source = """
      in a: { x: Int }
      in b: { y: Int }
      result = a + b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.Merge]
  }

  it should "parse projection expressions" in {
    val source = """
      in data: { id: Int, name: String, email: String }
      result = data[id, name]
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.Projection]

    val projection = assignment.value.value.asInstanceOf[Expression.Projection]
    projection.fields shouldBe List("id", "name")
  }

  it should "parse complex expressions with merge and projection" in {
    val source = """
      in a: { x: Int, y: Int }
      in b: { z: Int }
      result = a[x] + b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
  }

  it should "parse conditional expressions" in {
    val source = """
      in flag: Boolean
      in a: Int
      in b: Int
      result = if (flag) a else b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.Conditional]
  }

  it should "parse string literals" in {
    val source = """
      x = "hello world"
      out x
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations.head.asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe Expression.StringLit("hello world")
  }

  it should "parse integer literals" in {
    val source = """
      x = 42
      out x
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations.head.asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe Expression.IntLit(42)
  }

  it should "parse float literals" in {
    val source = """
      x = 3.14
      out x
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations.head.asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe Expression.FloatLit(3.14)
  }

  it should "parse boolean literals" in {
    val source = """
      x = true
      y = false
      out x
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
  }

  it should "parse comments" in {
    val source = """
      # This is a comment
      in x: Int
      # Another comment
      out x
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
  }

  it should "parse the example program from design doc" in {
    val source = """
      type Communication = {
        communicationId: String,
        contentBlocks: List<String>,
        channel: String
      }

      in communications: Candidates<Communication>
      in mappedUserId: Int

      embeddings = ide-ranker-v2-candidate-embed(communications)
      scores = ide-ranker-v2-precomputed-embeddings(embeddings + communications, mappedUserId)
      result = communications[communicationId, channel] + scores[score]

      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    // 1 type def + 2 inputs + 3 assignments = 6 declarations
    program.declarations should have size 6
  }

  it should "fail on undefined keywords used as identifiers" in {
    val source = """
      in type: Int
      out type
    """
    val result = ConstellationParser.parse(source)
    result.isLeft shouldBe true
  }

  it should "provide error position on parse failure" in {
    val source = """
      in x: Int
      out @invalid
    """
    val result = ConstellationParser.parse(source)
    result.isLeft shouldBe true
    val error = result.left.toOption.get
    error.span.isDefined shouldBe true
  }

  it should "parse type merge expressions" in {
    val source = """
      type A = { x: Int }
      type B = { y: Int }
      type C = A + B
      in c: C
      out c
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val typeDef = program.declarations(2).asInstanceOf[Declaration.TypeDef]
    typeDef.definition.value shouldBe a[TypeExpr.TypeMerge]
  }

  it should "parse empty record types" in {
    val source = """
      type Empty = {}
      in e: Empty
      out e
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
  }

  it should "parse primitive type references" in {
    val source = """
      in s: String
      in i: Int
      in f: Float
      in b: Boolean
      out s
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val stringInput = program.declarations.head.asInstanceOf[Declaration.InputDecl]
    stringInput.typeExpr.value shouldBe TypeExpr.Primitive("String")
  }
}
