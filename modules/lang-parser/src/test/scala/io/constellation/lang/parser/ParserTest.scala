package io.constellation.lang.parser

import io.constellation.lang.ast.*
import io.constellation.lang.ast.CompareOp
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

    // 1 TypeDef + 1 OutputDecl = 2 declarations
    program.declarations should have size 2
    program.declarations.head shouldBe a[Declaration.TypeDef]
    program.declarations.last shouldBe a[Declaration.OutputDecl]

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

    // 1 TypeDef + 1 InputDecl + 1 OutputDecl = 3 declarations
    program.declarations should have size 3
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

    // 2 InputDecl + 1 OutputDecl = 3 declarations
    program.declarations should have size 3
    program.declarations.take(2).foreach(_ shouldBe a[Declaration.InputDecl])
    program.declarations.last shouldBe a[Declaration.OutputDecl]
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

    // 1 InputDecl + 1 Assignment + 1 OutputDecl = 3 declarations
    program.declarations should have size 3
    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.FunctionCall]

    val funcCall = assignment.value.value.asInstanceOf[Expression.FunctionCall]
    funcCall.name shouldBe QualifiedName.simple("compute-something")
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

    // 1 type def + 2 inputs + 3 assignments + 1 output = 7 declarations
    program.declarations should have size 7
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

  // Multiple output declaration tests

  it should "parse multiple output declarations" in {
    val source = """
      in x: Int
      in y: Int
      z = x
      out x
      out z
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    // Should have 2 inputs + 1 assignment + 2 outputs = 5 declarations
    program.declarations should have size 5

    // Should have 2 outputs in the outputs list
    program.outputs should have size 2
    program.outputs.map(_.value) should contain allOf ("x", "z")
  }

  it should "collect output declarations in order" in {
    val source = """
      in a: Int
      in b: Int
      in c: Int
      out c
      out a
      out b
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    // Outputs should be in declaration order
    program.outputs.map(_.value) shouldBe List("c", "a", "b")
  }

  it should "parse output declarations interspersed with other declarations" in {
    val source = """
      in x: Int
      out x
      y = x
      out y
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.outputs should have size 2
    program.outputs.map(_.value) shouldBe List("x", "y")
  }

  it should "require at least one output declaration" in {
    val source = """
      in x: Int
      y = x
    """
    val result = ConstellationParser.parse(source)
    // Should fail because no output is declared
    result.isLeft shouldBe true
  }

  // Namespace / Qualified Name tests

  it should "parse qualified function names" in {
    val source = """
      in a: Int
      in b: Int
      result = stdlib.math.add(a, b)
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val funcCall = assignment.value.value.asInstanceOf[Expression.FunctionCall]
    funcCall.name shouldBe QualifiedName(List("stdlib", "math", "add"))
    funcCall.name.namespace shouldBe Some("stdlib.math")
    funcCall.name.localName shouldBe "add"
  }

  it should "parse use declarations without alias" in {
    val source = """
      use stdlib.math
      in a: Int
      result = add(a, a)
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.declarations.head shouldBe a[Declaration.UseDecl]
    val useDecl = program.declarations.head.asInstanceOf[Declaration.UseDecl]
    useDecl.path.value shouldBe QualifiedName(List("stdlib", "math"))
    useDecl.alias shouldBe None
  }

  it should "parse use declarations with alias" in {
    val source = """
      use stdlib.math as m
      in a: Int
      result = m.add(a, a)
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.declarations.head shouldBe a[Declaration.UseDecl]
    val useDecl = program.declarations.head.asInstanceOf[Declaration.UseDecl]
    useDecl.path.value shouldBe QualifiedName(List("stdlib", "math"))
    useDecl.alias.map(_.value) shouldBe Some("m")
  }

  it should "parse multiple use declarations" in {
    val source = """
      use stdlib.math
      use stdlib.string as str
      in x: Int
      out x
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    program.declarations.take(2).foreach(_ shouldBe a[Declaration.UseDecl])
    val useDecl1 = program.declarations(0).asInstanceOf[Declaration.UseDecl]
    val useDecl2 = program.declarations(1).asInstanceOf[Declaration.UseDecl]
    useDecl1.alias shouldBe None
    useDecl2.alias.map(_.value) shouldBe Some("str")
  }

  it should "parse aliased function calls" in {
    val source = """
      use stdlib.math as m
      in a: Int
      result = m.add(a, a)
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val funcCall = assignment.value.value.asInstanceOf[Expression.FunctionCall]
    funcCall.name shouldBe QualifiedName(List("m", "add"))
  }

  // Field access tests

  it should "parse simple field access" in {
    val source = """
      in user: { name: String, age: Int }
      result = user.name
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.FieldAccess]

    val fieldAccess = assignment.value.value.asInstanceOf[Expression.FieldAccess]
    fieldAccess.source.value shouldBe Expression.VarRef("user")
    fieldAccess.field.value shouldBe "name"
  }

  it should "parse chained field access" in {
    val source = """
      in person: { address: { city: String } }
      result = person.address.city
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.FieldAccess]

    // person.address.city should be FieldAccess(FieldAccess(person, address), city)
    val outerAccess = assignment.value.value.asInstanceOf[Expression.FieldAccess]
    outerAccess.field.value shouldBe "city"
    outerAccess.source.value shouldBe a[Expression.FieldAccess]

    val innerAccess = outerAccess.source.value.asInstanceOf[Expression.FieldAccess]
    innerAccess.field.value shouldBe "address"
    innerAccess.source.value shouldBe Expression.VarRef("person")
  }

  it should "parse field access combined with merge" in {
    val source = """
      in a: { x: Int }
      in b: { y: Int }
      result = a.x + b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.Merge]
  }

  it should "parse field access after function call" in {
    val source = """
      in x: Int
      result = compute(x).value
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.FieldAccess]

    val fieldAccess = assignment.value.value.asInstanceOf[Expression.FieldAccess]
    fieldAccess.field.value shouldBe "value"
    fieldAccess.source.value shouldBe a[Expression.FunctionCall]
  }

  it should "parse field access combined with projection" in {
    val source = """
      in data: { records: { id: Int, name: String } }
      result = data.records[id]
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    // The result should be Projection(FieldAccess(data, records), [id])
    assignment.value.value shouldBe a[Expression.Projection]

    val projection = assignment.value.value.asInstanceOf[Expression.Projection]
    projection.fields shouldBe List("id")
    projection.source.value shouldBe a[Expression.FieldAccess]
  }

  it should "parse field access on parenthesized expression" in {
    val source = """
      in a: { x: Int }
      in b: { x: Int }
      result = (if (true) a else b).x
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.FieldAccess]

    val fieldAccess = assignment.value.value.asInstanceOf[Expression.FieldAccess]
    fieldAccess.field.value shouldBe "x"
    fieldAccess.source.value shouldBe a[Expression.Conditional]
  }

  // Comparison operator tests

  it should "parse equality comparison (==)" in {
    val source = """
      in a: Int
      in b: Int
      result = a == b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.Compare]

    val compare = assignment.value.value.asInstanceOf[Expression.Compare]
    compare.op shouldBe CompareOp.Eq
  }

  it should "parse inequality comparison (!=)" in {
    val source = """
      in a: Int
      in b: Int
      result = a != b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val compare = assignment.value.value.asInstanceOf[Expression.Compare]
    compare.op shouldBe CompareOp.NotEq
  }

  it should "parse less than comparison (<)" in {
    val source = """
      in a: Int
      in b: Int
      result = a < b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val compare = assignment.value.value.asInstanceOf[Expression.Compare]
    compare.op shouldBe CompareOp.Lt
  }

  it should "parse greater than comparison (>)" in {
    val source = """
      in a: Int
      in b: Int
      result = a > b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val compare = assignment.value.value.asInstanceOf[Expression.Compare]
    compare.op shouldBe CompareOp.Gt
  }

  it should "parse less than or equal comparison (<=)" in {
    val source = """
      in a: Int
      in b: Int
      result = a <= b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val compare = assignment.value.value.asInstanceOf[Expression.Compare]
    compare.op shouldBe CompareOp.LtEq
  }

  it should "parse greater than or equal comparison (>=)" in {
    val source = """
      in a: Int
      in b: Int
      result = a >= b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val compare = assignment.value.value.asInstanceOf[Expression.Compare]
    compare.op shouldBe CompareOp.GtEq
  }

  it should "parse comparison with merge operator respecting precedence" in {
    // a + b == c + d should parse as (a + b) == (c + d)
    val source = """
      in a: { x: Int }
      in b: { y: Int }
      in c: { x: Int }
      in d: { y: Int }
      result = a + b == c + d
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(4).asInstanceOf[Declaration.Assignment]
    val compare = assignment.value.value.asInstanceOf[Expression.Compare]
    compare.op shouldBe CompareOp.Eq
    compare.left.value shouldBe a[Expression.Merge]
    compare.right.value shouldBe a[Expression.Merge]
  }

  it should "parse comparison with literals" in {
    val source = """
      in x: Int
      result = x == 42
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    val compare = assignment.value.value.asInstanceOf[Expression.Compare]
    compare.op shouldBe CompareOp.Eq
    compare.left.value shouldBe a[Expression.VarRef]
    compare.right.value shouldBe Expression.IntLit(42)
  }
}
