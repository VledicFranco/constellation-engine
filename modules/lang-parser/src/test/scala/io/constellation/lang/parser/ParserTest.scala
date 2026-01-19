package io.constellation.lang.parser

import io.constellation.lang.ast.*
import io.constellation.lang.ast.CompareOp
import io.constellation.lang.ast.ArithOp
import io.constellation.lang.ast.BoolOp
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

  it should "parse addition expressions (+ operator)" in {
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
    // Parser now produces Arithmetic(Add, ...) for +
    // TypeChecker will convert to Merge for records or function call for numerics
    assignment.value.value shouldBe a[Expression.Arithmetic]
    val arith = assignment.value.value.asInstanceOf[Expression.Arithmetic]
    arith.op shouldBe ArithOp.Add
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

  it should "parse projection expressions with curly brace syntax" in {
    val source = """
      in data: { id: Int, name: String, email: String }
      result = data{id, name}
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

  it should "parse single field projection with curly braces" in {
    val source = """
      in data: { id: Int, name: String }
      result = data{id}
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true

    val program = result.toOption.get
    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    val projection = assignment.value.value.asInstanceOf[Expression.Projection]
    projection.fields shouldBe List("id")
  }

  it should "parse curly brace projection combined with field access" in {
    val source = """
      in data: { records: { id: Int, name: String } }
      result = data.records{id}
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    // The result should be Projection(FieldAccess(data, records), {id})
    assignment.value.value shouldBe a[Expression.Projection]

    val projection = assignment.value.value.asInstanceOf[Expression.Projection]
    projection.fields shouldBe List("id")
    projection.source.value shouldBe a[Expression.FieldAccess]
  }

  it should "parse complex expressions with merge and curly brace projection" in {
    val source = """
      in a: { x: Int, y: Int }
      in b: { z: Int }
      result = a{x} + b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
  }

  it should "parse both bracket and brace projection in same program" in {
    val source = """
      in data: { id: Int, name: String, email: String }
      r1 = data[id, name]
      r2 = data{name, email}
      out r1
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment1 = program.declarations(1).asInstanceOf[Declaration.Assignment]
    val projection1 = assignment1.value.value.asInstanceOf[Expression.Projection]
    projection1.fields shouldBe List("id", "name")

    val assignment2 = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val projection2 = assignment2.value.value.asInstanceOf[Expression.Projection]
    projection2.fields shouldBe List("name", "email")
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

  it should "parse field access combined with addition" in {
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
    assignment.value.value shouldBe a[Expression.Arithmetic]
    val arith = assignment.value.value.asInstanceOf[Expression.Arithmetic]
    arith.op shouldBe ArithOp.Add
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

  it should "parse comparison with addition operator respecting precedence" in {
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
    compare.left.value shouldBe a[Expression.Arithmetic]
    compare.right.value shouldBe a[Expression.Arithmetic]
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

  // Arithmetic operator tests

  it should "parse subtraction (-)" in {
    val source = """
      in a: Int
      in b: Int
      result = a - b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.Arithmetic]
    val arith = assignment.value.value.asInstanceOf[Expression.Arithmetic]
    arith.op shouldBe ArithOp.Sub
  }

  it should "parse multiplication (*)" in {
    val source = """
      in a: Int
      in b: Int
      result = a * b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.Arithmetic]
    val arith = assignment.value.value.asInstanceOf[Expression.Arithmetic]
    arith.op shouldBe ArithOp.Mul
  }

  it should "parse division (/)" in {
    val source = """
      in a: Int
      in b: Int
      result = a / b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.Arithmetic]
    val arith = assignment.value.value.asInstanceOf[Expression.Arithmetic]
    arith.op shouldBe ArithOp.Div
  }

  it should "parse arithmetic with correct precedence (* before +)" in {
    // a + b * c should parse as a + (b * c)
    val source = """
      in a: Int
      in b: Int
      in c: Int
      result = a + b * c
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    val arith = assignment.value.value.asInstanceOf[Expression.Arithmetic]
    arith.op shouldBe ArithOp.Add
    arith.left.value shouldBe a[Expression.VarRef]  // a
    arith.right.value shouldBe a[Expression.Arithmetic]  // b * c
    val mulExpr = arith.right.value.asInstanceOf[Expression.Arithmetic]
    mulExpr.op shouldBe ArithOp.Mul
  }

  it should "parse arithmetic with correct precedence (/ before -)" in {
    // a - b / c should parse as a - (b / c)
    val source = """
      in a: Int
      in b: Int
      in c: Int
      result = a - b / c
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    val arith = assignment.value.value.asInstanceOf[Expression.Arithmetic]
    arith.op shouldBe ArithOp.Sub
    arith.left.value shouldBe a[Expression.VarRef]  // a
    arith.right.value shouldBe a[Expression.Arithmetic]  // b / c
    val divExpr = arith.right.value.asInstanceOf[Expression.Arithmetic]
    divExpr.op shouldBe ArithOp.Div
  }

  it should "parse chained multiplication with left associativity" in {
    // a * b * c should parse as (a * b) * c
    val source = """
      in a: Int
      in b: Int
      in c: Int
      result = a * b * c
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    val arith = assignment.value.value.asInstanceOf[Expression.Arithmetic]
    arith.op shouldBe ArithOp.Mul
    arith.left.value shouldBe a[Expression.Arithmetic]  // a * b
    arith.right.value shouldBe a[Expression.VarRef]  // c
    val leftMul = arith.left.value.asInstanceOf[Expression.Arithmetic]
    leftMul.op shouldBe ArithOp.Mul
  }

  it should "parse complex arithmetic expression" in {
    // a + b * c - d / e should parse as ((a + (b * c)) - (d / e))
    val source = """
      in a: Int
      in b: Int
      in c: Int
      in d: Int
      in e: Int
      result = a + b * c - d / e
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(5).asInstanceOf[Declaration.Assignment]
    // The top-level should be subtraction
    val arith = assignment.value.value.asInstanceOf[Expression.Arithmetic]
    arith.op shouldBe ArithOp.Sub
    // Left side should be a + (b * c)
    arith.left.value shouldBe a[Expression.Arithmetic]
    val leftAdd = arith.left.value.asInstanceOf[Expression.Arithmetic]
    leftAdd.op shouldBe ArithOp.Add
    // Right side should be d / e
    arith.right.value shouldBe a[Expression.Arithmetic]
    val rightDiv = arith.right.value.asInstanceOf[Expression.Arithmetic]
    rightDiv.op shouldBe ArithOp.Div
  }

  // Boolean operator tests

  it should "parse 'and' operator" in {
    val source = """
      in a: Boolean
      in b: Boolean
      result = a and b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.BoolBinary]

    val boolBinary = assignment.value.value.asInstanceOf[Expression.BoolBinary]
    boolBinary.op shouldBe BoolOp.And
  }

  it should "parse 'or' operator" in {
    val source = """
      in a: Boolean
      in b: Boolean
      result = a or b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.BoolBinary]

    val boolBinary = assignment.value.value.asInstanceOf[Expression.BoolBinary]
    boolBinary.op shouldBe BoolOp.Or
  }

  it should "parse 'not' operator" in {
    val source = """
      in a: Boolean
      result = not a
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.Not]
  }

  it should "parse chained 'and' operators (left-associative)" in {
    val source = """
      in a: Boolean
      in b: Boolean
      in c: Boolean
      result = a and b and c
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    // (a and b) and c
    val outer = assignment.value.value.asInstanceOf[Expression.BoolBinary]
    outer.op shouldBe BoolOp.And
    outer.left.value shouldBe a[Expression.BoolBinary]
  }

  it should "parse chained 'or' operators (left-associative)" in {
    val source = """
      in a: Boolean
      in b: Boolean
      in c: Boolean
      result = a or b or c
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    // (a or b) or c
    val outer = assignment.value.value.asInstanceOf[Expression.BoolBinary]
    outer.op shouldBe BoolOp.Or
    outer.left.value shouldBe a[Expression.BoolBinary]
  }

  it should "parse 'or' with lower precedence than 'and'" in {
    val source = """
      in a: Boolean
      in b: Boolean
      in c: Boolean
      result = a or b and c
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    // a or (b and c)
    val outer = assignment.value.value.asInstanceOf[Expression.BoolBinary]
    outer.op shouldBe BoolOp.Or
    outer.right.value shouldBe a[Expression.BoolBinary]
    val inner = outer.right.value.asInstanceOf[Expression.BoolBinary]
    inner.op shouldBe BoolOp.And
  }

  it should "parse 'not' with higher precedence than 'and'" in {
    val source = """
      in a: Boolean
      in b: Boolean
      result = not a and b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    // (not a) and b
    val boolBinary = assignment.value.value.asInstanceOf[Expression.BoolBinary]
    boolBinary.op shouldBe BoolOp.And
    boolBinary.left.value shouldBe a[Expression.Not]
  }

  it should "parse double negation 'not not'" in {
    val source = """
      in a: Boolean
      result = not not a
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    val outer = assignment.value.value.asInstanceOf[Expression.Not]
    outer.operand.value shouldBe a[Expression.Not]
  }

  it should "parse boolean operators with comparison operators" in {
    val source = """
      in x: Int
      in y: Int
      in z: Int
      result = x < y and y < z
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    // (x < y) and (y < z)
    val boolBinary = assignment.value.value.asInstanceOf[Expression.BoolBinary]
    boolBinary.op shouldBe BoolOp.And
    boolBinary.left.value shouldBe a[Expression.Compare]
    boolBinary.right.value shouldBe a[Expression.Compare]
  }

  it should "parse complex boolean expression with parentheses" in {
    val source = """
      in a: Boolean
      in b: Boolean
      in c: Boolean
      result = (a or b) and c
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    val boolBinary = assignment.value.value.asInstanceOf[Expression.BoolBinary]
    boolBinary.op shouldBe BoolOp.And
    boolBinary.left.value shouldBe a[Expression.BoolBinary]
    val inner = boolBinary.left.value.asInstanceOf[Expression.BoolBinary]
    inner.op shouldBe BoolOp.Or
  }

  it should "parse not with comparison" in {
    val source = """
      in x: Int
      in y: Int
      result = not x == y
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    // not (x == y)
    val notExpr = assignment.value.value.asInstanceOf[Expression.Not]
    notExpr.operand.value shouldBe a[Expression.Compare]
  }

  // Guard expression tests

  it should "parse guard expression with 'when' keyword" in {
    val source = """
      in value: Int
      in isActive: Boolean
      result = value when isActive
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.Guard]

    val guard = assignment.value.value.asInstanceOf[Expression.Guard]
    guard.expr.value shouldBe a[Expression.VarRef]
    guard.condition.value shouldBe a[Expression.VarRef]
  }

  it should "parse guard expression with comparison condition" in {
    val source = """
      in score: Int
      in data: String
      result = data when score > 90
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val guard = assignment.value.value.asInstanceOf[Expression.Guard]
    guard.expr.value shouldBe a[Expression.VarRef]
    guard.condition.value shouldBe a[Expression.Compare]
  }

  it should "parse guard expression with boolean operators in condition" in {
    val source = """
      in value: Int
      in a: Boolean
      in b: Boolean
      result = value when a and b
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    val guard = assignment.value.value.asInstanceOf[Expression.Guard]
    guard.condition.value shouldBe a[Expression.BoolBinary]
  }

  it should "parse guard expression with function call as expression" in {
    val source = """
      in x: Int
      in isEnabled: Boolean
      result = process(x) when isEnabled
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val guard = assignment.value.value.asInstanceOf[Expression.Guard]
    guard.expr.value shouldBe a[Expression.FunctionCall]
    guard.condition.value shouldBe a[Expression.VarRef]
  }

  it should "parse guard with lowest precedence (below or)" in {
    // a or b when c should parse as (a or b) when c
    val source = """
      in a: Boolean
      in b: Boolean
      in c: Boolean
      result = a or b when c
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    val guard = assignment.value.value.asInstanceOf[Expression.Guard]
    guard.expr.value shouldBe a[Expression.BoolBinary]
    val boolBinary = guard.expr.value.asInstanceOf[Expression.BoolBinary]
    boolBinary.op shouldBe BoolOp.Or
  }

  it should "not allow 'when' as an identifier" in {
    val source = """
      in when: Int
      out when
    """
    val result = ConstellationParser.parse(source)
    result.isLeft shouldBe true
  }

  it should "parse guard expression with arithmetic expression" in {
    val source = """
      in a: Int
      in b: Int
      in flag: Boolean
      result = a + b when flag
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    val guard = assignment.value.value.asInstanceOf[Expression.Guard]
    guard.expr.value shouldBe a[Expression.Arithmetic]
  }

  it should "parse guard expression with literal expression" in {
    val source = """
      in flag: Boolean
      result = 42 when flag
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    val guard = assignment.value.value.asInstanceOf[Expression.Guard]
    guard.expr.value shouldBe Expression.IntLit(42)
  }

  // Coalesce operator tests

  it should "parse basic coalesce expression with ??" in {
    val source = """
      in maybeValue: Optional<Int>
      in fallback: Int
      result = maybeValue ?? fallback
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    assignment.value.value shouldBe a[Expression.Coalesce]

    val coalesce = assignment.value.value.asInstanceOf[Expression.Coalesce]
    coalesce.left.value shouldBe a[Expression.VarRef]
    coalesce.right.value shouldBe a[Expression.VarRef]
  }

  it should "parse coalesce with right associativity" in {
    // a ?? b ?? c should parse as a ?? (b ?? c)
    val source = """
      in a: Optional<Int>
      in b: Optional<Int>
      in c: Int
      result = a ?? b ?? c
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    val coalesce = assignment.value.value.asInstanceOf[Expression.Coalesce]
    coalesce.left.value shouldBe a[Expression.VarRef]
    // right should be another coalesce (b ?? c)
    coalesce.right.value shouldBe a[Expression.Coalesce]
    val innerCoalesce = coalesce.right.value.asInstanceOf[Expression.Coalesce]
    innerCoalesce.left.value shouldBe a[Expression.VarRef]
    innerCoalesce.right.value shouldBe a[Expression.VarRef]
  }

  it should "parse coalesce with lower precedence than guard" in {
    // a when b ?? c should parse as (a when b) ?? c
    val source = """
      in a: Int
      in b: Boolean
      in c: Int
      result = a when b ?? c
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    val coalesce = assignment.value.value.asInstanceOf[Expression.Coalesce]
    // left side should be a guard expression
    coalesce.left.value shouldBe a[Expression.Guard]
    val guard = coalesce.left.value.asInstanceOf[Expression.Guard]
    guard.expr.value shouldBe a[Expression.VarRef]
    guard.condition.value shouldBe a[Expression.VarRef]
    // right side should be the fallback value
    coalesce.right.value shouldBe a[Expression.VarRef]
  }

  it should "parse coalesce with function call on left side" in {
    val source = """
      in x: Int
      in fallback: Int
      result = compute(x) ?? fallback
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val coalesce = assignment.value.value.asInstanceOf[Expression.Coalesce]
    coalesce.left.value shouldBe a[Expression.FunctionCall]
    coalesce.right.value shouldBe a[Expression.VarRef]
  }

  it should "parse coalesce with literal fallback" in {
    val source = """
      in maybeValue: Optional<Int>
      result = maybeValue ?? 0
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    val coalesce = assignment.value.value.asInstanceOf[Expression.Coalesce]
    coalesce.left.value shouldBe a[Expression.VarRef]
    coalesce.right.value shouldBe Expression.IntLit(0)
  }

  it should "parse coalesce with string literal fallback" in {
    val source = """
      in maybeName: Optional<String>
      result = maybeName ?? "default"
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(1).asInstanceOf[Declaration.Assignment]
    val coalesce = assignment.value.value.asInstanceOf[Expression.Coalesce]
    coalesce.right.value shouldBe Expression.StringLit("default")
  }

  it should "parse coalesce with field access" in {
    val source = """
      in record: { maybeValue: Optional<Int> }
      in fallback: Int
      result = record.maybeValue ?? fallback
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(2).asInstanceOf[Declaration.Assignment]
    val coalesce = assignment.value.value.asInstanceOf[Expression.Coalesce]
    coalesce.left.value shouldBe a[Expression.FieldAccess]
    coalesce.right.value shouldBe a[Expression.VarRef]
  }

  it should "parse coalesce in conditional expression" in {
    val source = """
      in flag: Boolean
      in a: Optional<Int>
      in b: Optional<Int>
      in fallback: Int
      result = if (flag) a ?? fallback else b ?? fallback
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(4).asInstanceOf[Declaration.Assignment]
    val conditional = assignment.value.value.asInstanceOf[Expression.Conditional]
    conditional.thenBranch.value shouldBe a[Expression.Coalesce]
    conditional.elseBranch.value shouldBe a[Expression.Coalesce]
  }

  it should "parse parenthesized coalesce expression" in {
    val source = """
      in a: Optional<Int>
      in b: Int
      in c: Int
      result = (a ?? b) + c
      out result
    """
    val result = ConstellationParser.parse(source)
    result.isRight shouldBe true
    val program = result.toOption.get

    val assignment = program.declarations(3).asInstanceOf[Declaration.Assignment]
    // Should be Arithmetic(Coalesce(...), +, c)
    val arith = assignment.value.value.asInstanceOf[Expression.Arithmetic]
    arith.op shouldBe ArithOp.Add
    arith.left.value shouldBe a[Expression.Coalesce]
    arith.right.value shouldBe a[Expression.VarRef]
  }
}
