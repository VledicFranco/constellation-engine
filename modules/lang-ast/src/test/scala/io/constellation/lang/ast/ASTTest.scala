package io.constellation.lang.ast

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ASTTest extends AnyFlatSpec with Matchers {

  // ============================================================
  // Span Tests
  // ============================================================

  "Span" should "correctly calculate point as start position" in {
    val span = Span(10, 20)
    span.point shouldBe 10
  }

  it should "correctly calculate length" in {
    Span(0, 10).length shouldBe 10
    Span(5, 5).length shouldBe 0
    Span(100, 150).length shouldBe 50
  }

  it should "correctly check if offset is contained" in {
    val span = Span(10, 20)
    span.contains(10) shouldBe true
    span.contains(15) shouldBe true
    span.contains(19) shouldBe true
    span.contains(9) shouldBe false
    span.contains(20) shouldBe false  // end is exclusive
  }

  it should "correctly detect empty spans" in {
    Span(5, 5).isEmpty shouldBe true
    Span(0, 0).isEmpty shouldBe true
    Span(0, 1).isEmpty shouldBe false
  }

  it should "format toString correctly" in {
    Span(10, 20).toString shouldBe "[10..20)"
    Span(0, 0).toString shouldBe "[0..0)"
  }

  "Span.zero" should "be an empty span at position 0" in {
    Span.zero shouldBe Span(0, 0)
    Span.zero.isEmpty shouldBe true
  }

  "Span.point" should "create a single-character span" in {
    val span = Span.point(5)
    span shouldBe Span(5, 6)
    span.length shouldBe 1
  }

  // ============================================================
  // LineCol Tests
  // ============================================================

  "LineCol" should "store line and column" in {
    val lc = LineCol(5, 10)
    lc.line shouldBe 5
    lc.col shouldBe 10
  }

  it should "format toString correctly" in {
    LineCol(1, 1).toString shouldBe "1:1"
    LineCol(42, 7).toString shouldBe "42:7"
  }

  // ============================================================
  // LineMap Tests
  // ============================================================

  "LineMap.fromSource" should "handle empty source" in {
    val lm = LineMap.fromSource("")
    lm.lineCount shouldBe 1
    lm.offsetToLineCol(0) shouldBe LineCol(1, 1)
  }

  it should "handle single line without newline" in {
    val lm = LineMap.fromSource("hello")
    lm.lineCount shouldBe 1
    lm.offsetToLineCol(0) shouldBe LineCol(1, 1)
    lm.offsetToLineCol(4) shouldBe LineCol(1, 5)
  }

  it should "handle multiple lines" in {
    val lm = LineMap.fromSource("hello\nworld\n")
    lm.lineCount shouldBe 3
    lm.offsetToLineCol(0) shouldBe LineCol(1, 1)   // 'h' in hello
    lm.offsetToLineCol(5) shouldBe LineCol(1, 6)   // newline after hello
    lm.offsetToLineCol(6) shouldBe LineCol(2, 1)   // 'w' in world
    lm.offsetToLineCol(11) shouldBe LineCol(2, 6)  // newline after world
  }

  it should "correctly handle offsets in the middle of lines" in {
    val lm = LineMap.fromSource("abc\ndefgh\nij")
    lm.offsetToLineCol(1) shouldBe LineCol(1, 2)   // 'b'
    lm.offsetToLineCol(5) shouldBe LineCol(2, 2)   // 'e'
    lm.offsetToLineCol(10) shouldBe LineCol(3, 1)  // 'i'
    lm.offsetToLineCol(11) shouldBe LineCol(3, 2)  // 'j'
  }

  // ============================================================
  // SourceFile Tests
  // ============================================================

  "SourceFile" should "convert span to line/column positions" in {
    val sf = SourceFile("test.cst", "hello\nworld")
    val (start, end) = sf.spanToLineCol(Span(0, 5))
    start shouldBe LineCol(1, 1)
    end shouldBe LineCol(1, 6)
  }

  it should "extract a line by line number" in {
    val sf = SourceFile("test.cst", "first\nsecond\nthird")
    sf.extractLine(1) shouldBe "first"
    sf.extractLine(2) shouldBe "second"
    sf.extractLine(3) shouldBe "third"
  }

  it should "extract a code snippet with line number and pointer" in {
    val sf = SourceFile("test.cst", "let x = 42")
    val snippet = sf.extractSnippet(Span(4, 5))
    snippet should include("1")       // line number
    snippet should include("let x = 42")
    snippet should include("^")       // pointer
  }

  // ============================================================
  // Located Tests
  // ============================================================

  "Located" should "store value and span" in {
    val loc = Located("hello", Span(0, 5))
    loc.value shouldBe "hello"
    loc.span shouldBe Span(0, 5)
  }

  it should "map over the value while preserving span" in {
    val loc = Located(10, Span(5, 10))
    val mapped = loc.map(_ * 2)
    mapped.value shouldBe 20
    mapped.span shouldBe Span(5, 10)
  }

  // ============================================================
  // QualifiedName Tests
  // ============================================================

  "QualifiedName" should "detect simple names" in {
    QualifiedName.simple("foo").isSimple shouldBe true
    QualifiedName(List("foo")).isSimple shouldBe true
    QualifiedName(List("foo", "bar")).isSimple shouldBe false
  }

  it should "extract namespace correctly" in {
    QualifiedName.simple("foo").namespace shouldBe None
    QualifiedName(List("stdlib", "math")).namespace shouldBe Some("stdlib")
    QualifiedName(List("a", "b", "c")).namespace shouldBe Some("a.b")
  }

  it should "extract local name correctly" in {
    QualifiedName.simple("foo").localName shouldBe "foo"
    QualifiedName(List("stdlib", "math")).localName shouldBe "math"
    QualifiedName(List("a", "b", "c")).localName shouldBe "c"
  }

  it should "produce full name correctly" in {
    QualifiedName.simple("foo").fullName shouldBe "foo"
    QualifiedName(List("stdlib", "math")).fullName shouldBe "stdlib.math"
    QualifiedName(List("a", "b", "c")).fullName shouldBe "a.b.c"
  }

  it should "format toString as full name" in {
    QualifiedName(List("stdlib", "math", "add")).toString shouldBe "stdlib.math.add"
  }

  "QualifiedName.simple" should "create a single-part qualified name" in {
    val qn = QualifiedName.simple("myFunc")
    qn.parts shouldBe List("myFunc")
    qn.isSimple shouldBe true
  }

  "QualifiedName.fromString" should "parse dotted names" in {
    QualifiedName.fromString("stdlib.math.add").parts shouldBe List("stdlib", "math", "add")
    QualifiedName.fromString("simple").parts shouldBe List("simple")
  }

  // ============================================================
  // Program Tests
  // ============================================================

  "Program" should "hold declarations and outputs" in {
    val decls = List(
      Declaration.InputDecl(
        Located("x", Span(0, 1)),
        Located(TypeExpr.Primitive("String"), Span(3, 9))
      )
    )
    val outputs = List(Located("result", Span(20, 26)))
    val program = Program(decls, outputs)

    program.declarations shouldBe decls
    program.outputs shouldBe outputs
  }

  // ============================================================
  // Declaration Tests
  // ============================================================

  "Declaration.TypeDef" should "hold name and type definition" in {
    val td = Declaration.TypeDef(
      Located("MyType", Span(5, 11)),
      Located(TypeExpr.Record(List(("id", TypeExpr.Primitive("String")))), Span(14, 30))
    )
    td.name.value shouldBe "MyType"
    td.definition.value shouldBe a[TypeExpr.Record]
  }

  "Declaration.InputDecl" should "hold name and type expression" in {
    val input = Declaration.InputDecl(
      Located("data", Span(3, 7)),
      Located(TypeExpr.Parameterized("List", List(TypeExpr.Primitive("Int"))), Span(9, 18))
    )
    input.name.value shouldBe "data"
    input.typeExpr.value shouldBe a[TypeExpr.Parameterized]
  }

  "Declaration.Assignment" should "hold target and value expression" in {
    val assign = Declaration.Assignment(
      Located("result", Span(0, 6)),
      Located(Expression.FunctionCall(QualifiedName.simple("process"), List()), Span(9, 18))
    )
    assign.target.value shouldBe "result"
    assign.value.value shouldBe a[Expression.FunctionCall]
  }

  "Declaration.OutputDecl" should "hold output variable name" in {
    val out = Declaration.OutputDecl(Located("output", Span(4, 10)))
    out.name.value shouldBe "output"
  }

  "Declaration.UseDecl" should "hold path and optional alias" in {
    val useWithAlias = Declaration.UseDecl(
      Located(QualifiedName(List("stdlib", "math")), Span(4, 16)),
      Some(Located("m", Span(20, 21)))
    )
    useWithAlias.path.value.fullName shouldBe "stdlib.math"
    useWithAlias.alias.map(_.value) shouldBe Some("m")

    val useWithoutAlias = Declaration.UseDecl(
      Located(QualifiedName(List("stdlib", "io")), Span(4, 13)),
      None
    )
    useWithoutAlias.alias shouldBe None
  }

  // ============================================================
  // TypeExpr Tests
  // ============================================================

  "TypeExpr.Primitive" should "hold primitive type name" in {
    TypeExpr.Primitive("String").name shouldBe "String"
    TypeExpr.Primitive("Int").name shouldBe "Int"
    TypeExpr.Primitive("Float").name shouldBe "Float"
    TypeExpr.Primitive("Boolean").name shouldBe "Boolean"
  }

  "TypeExpr.Record" should "hold field definitions" in {
    val record = TypeExpr.Record(List(
      ("id", TypeExpr.Primitive("String")),
      ("count", TypeExpr.Primitive("Int"))
    ))
    record.fields.size shouldBe 2
    record.fields.head shouldBe ("id", TypeExpr.Primitive("String"))
    record.fields(1) shouldBe ("count", TypeExpr.Primitive("Int"))
  }

  "TypeExpr.Parameterized" should "hold type name and parameters" in {
    val listType = TypeExpr.Parameterized("List", List(TypeExpr.Primitive("String")))
    listType.name shouldBe "List"
    listType.params shouldBe List(TypeExpr.Primitive("String"))

    val mapType = TypeExpr.Parameterized("Map", List(
      TypeExpr.Primitive("String"),
      TypeExpr.Primitive("Int")
    ))
    mapType.params.size shouldBe 2
  }

  "TypeExpr.TypeRef" should "hold referenced type name" in {
    val ref = TypeExpr.TypeRef("MyCustomType")
    ref.name shouldBe "MyCustomType"
  }

  "TypeExpr.TypeMerge" should "hold left and right types" in {
    val merge = TypeExpr.TypeMerge(
      TypeExpr.Primitive("String"),
      TypeExpr.Primitive("Int")
    )
    merge.left shouldBe TypeExpr.Primitive("String")
    merge.right shouldBe TypeExpr.Primitive("Int")
  }

  // ============================================================
  // Expression Tests
  // ============================================================

  "Expression.VarRef" should "hold variable name" in {
    Expression.VarRef("myVar").name shouldBe "myVar"
  }

  "Expression.FunctionCall" should "hold qualified name and arguments" in {
    val call = Expression.FunctionCall(
      QualifiedName(List("stdlib", "math", "add")),
      List(
        Located(Expression.VarRef("x"), Span(0, 1)),
        Located(Expression.IntLit(5), Span(3, 4))
      )
    )
    call.name.fullName shouldBe "stdlib.math.add"
    call.args.size shouldBe 2
  }

  "Expression.Merge" should "hold left and right expressions" in {
    val merge = Expression.Merge(
      Located(Expression.VarRef("a"), Span(0, 1)),
      Located(Expression.VarRef("b"), Span(4, 5))
    )
    merge.left.value shouldBe Expression.VarRef("a")
    merge.right.value shouldBe Expression.VarRef("b")
  }

  "Expression.Projection" should "hold source and field names" in {
    val proj = Expression.Projection(
      Located(Expression.VarRef("record"), Span(0, 6)),
      List("id", "name")
    )
    proj.source.value shouldBe Expression.VarRef("record")
    proj.fields shouldBe List("id", "name")
  }

  "Expression.Conditional" should "hold condition and branches" in {
    val cond = Expression.Conditional(
      Located(Expression.BoolLit(true), Span(4, 8)),
      Located(Expression.IntLit(1), Span(10, 11)),
      Located(Expression.IntLit(0), Span(17, 18))
    )
    cond.condition.value shouldBe Expression.BoolLit(true)
    cond.thenBranch.value shouldBe Expression.IntLit(1)
    cond.elseBranch.value shouldBe Expression.IntLit(0)
  }

  "Expression literals" should "hold correct values" in {
    Expression.StringLit("hello").value shouldBe "hello"
    Expression.IntLit(42L).value shouldBe 42L
    Expression.FloatLit(3.14).value shouldBe 3.14
    Expression.BoolLit(true).value shouldBe true
    Expression.BoolLit(false).value shouldBe false
  }

  // ============================================================
  // CompileError Tests
  // ============================================================

  "CompileError.ParseError" should "format message correctly" in {
    val err = CompileError.ParseError("Unexpected token", Some(Span(10, 15)))
    err.message shouldBe "Unexpected token"
    err.span shouldBe Some(Span(10, 15))
    err.format shouldBe "Error at [10..15): Unexpected token"
  }

  it should "handle missing span" in {
    val err = CompileError.ParseError("General error", None)
    err.format shouldBe "Error: General error"
  }

  "CompileError.TypeError" should "format message correctly" in {
    val err = CompileError.TypeError("Invalid type", Some(Span(5, 10)))
    err.message shouldBe "Invalid type"
    err.format should include("[5..10)")
  }

  "CompileError.UndefinedVariable" should "generate descriptive message" in {
    val err = CompileError.UndefinedVariable("foo", Some(Span(0, 3)))
    err.message shouldBe "Undefined variable: foo"
  }

  "CompileError.UndefinedType" should "generate descriptive message" in {
    val err = CompileError.UndefinedType("MyType", None)
    err.message shouldBe "Undefined type: MyType"
  }

  "CompileError.UndefinedFunction" should "generate descriptive message" in {
    val err = CompileError.UndefinedFunction("compute", Some(Span(10, 17)))
    err.message shouldBe "Undefined function: compute"
  }

  "CompileError.TypeMismatch" should "generate descriptive message" in {
    val err = CompileError.TypeMismatch("String", "Int", Some(Span(0, 5)))
    err.message shouldBe "Type mismatch: expected String, got Int"
  }

  "CompileError.InvalidProjection" should "generate descriptive message" in {
    val err = CompileError.InvalidProjection("foo", List("bar", "baz"), Some(Span(0, 5)))
    err.message shouldBe "Invalid projection: field 'foo' not found. Available: bar, baz"
  }

  "CompileError.IncompatibleMerge" should "generate descriptive message" in {
    val err = CompileError.IncompatibleMerge("String", "Int", Some(Span(0, 5)))
    err.message shouldBe "Cannot merge types: String + Int"
  }

  "CompileError.UndefinedNamespace" should "generate descriptive message" in {
    val err = CompileError.UndefinedNamespace("unknown.module", Some(Span(0, 14)))
    err.message shouldBe "Undefined namespace: unknown.module"
  }

  "CompileError.AmbiguousFunction" should "generate descriptive message" in {
    val err = CompileError.AmbiguousFunction("add", List("math.add", "custom.add"), Some(Span(0, 3)))
    err.message shouldBe "Ambiguous function 'add'. Candidates: math.add, custom.add"
  }

  "CompileError.formatWithSource" should "include source context" in {
    val sf = SourceFile("test.cst", "let x = undefined")
    val err = CompileError.UndefinedVariable("undefined", Some(Span(8, 17)))
    val formatted = err.formatWithSource(sf)

    formatted should include("test.cst")
    formatted should include("1:")        // line number
    formatted should include("undefined") // the error context
  }

  // ============================================================
  // Equality Tests
  // ============================================================

  "Span" should "have correct equality semantics" in {
    Span(0, 10) shouldBe Span(0, 10)
    Span(0, 10) should not be Span(0, 11)
    Span(0, 10) should not be Span(1, 10)
  }

  "LineCol" should "have correct equality semantics" in {
    LineCol(1, 5) shouldBe LineCol(1, 5)
    LineCol(1, 5) should not be LineCol(1, 6)
    LineCol(1, 5) should not be LineCol(2, 5)
  }

  "Located" should "have correct equality semantics" in {
    Located("test", Span(0, 4)) shouldBe Located("test", Span(0, 4))
    Located("test", Span(0, 4)) should not be Located("other", Span(0, 4))
    Located("test", Span(0, 4)) should not be Located("test", Span(0, 5))
  }

  "QualifiedName" should "have correct equality semantics" in {
    QualifiedName(List("a", "b")) shouldBe QualifiedName(List("a", "b"))
    QualifiedName(List("a", "b")) should not be QualifiedName(List("a", "c"))
    QualifiedName(List("a", "b")) should not be QualifiedName(List("a"))
  }

  "TypeExpr" should "have correct equality semantics" in {
    TypeExpr.Primitive("String") shouldBe TypeExpr.Primitive("String")
    TypeExpr.Primitive("String") should not be TypeExpr.Primitive("Int")

    val record1 = TypeExpr.Record(List(("id", TypeExpr.Primitive("String"))))
    val record2 = TypeExpr.Record(List(("id", TypeExpr.Primitive("String"))))
    val record3 = TypeExpr.Record(List(("id", TypeExpr.Primitive("Int"))))
    record1 shouldBe record2
    record1 should not be record3
  }

  "Expression" should "have correct equality semantics" in {
    Expression.VarRef("x") shouldBe Expression.VarRef("x")
    Expression.VarRef("x") should not be Expression.VarRef("y")

    Expression.IntLit(42) shouldBe Expression.IntLit(42)
    Expression.IntLit(42) should not be Expression.IntLit(43)
  }

  // ============================================================
  // Hash Code Tests
  // ============================================================

  "Span" should "have consistent hashCode" in {
    Span(10, 20).hashCode shouldBe Span(10, 20).hashCode
    // Equal objects must have equal hash codes
    val s1 = Span(5, 15)
    val s2 = Span(5, 15)
    s1.hashCode shouldBe s2.hashCode
  }

  "QualifiedName" should "have consistent hashCode" in {
    val qn1 = QualifiedName(List("a", "b", "c"))
    val qn2 = QualifiedName(List("a", "b", "c"))
    qn1.hashCode shouldBe qn2.hashCode
  }

  "Expression literals" should "have consistent hashCode" in {
    Expression.StringLit("test").hashCode shouldBe Expression.StringLit("test").hashCode
    Expression.IntLit(42).hashCode shouldBe Expression.IntLit(42).hashCode
    Expression.FloatLit(3.14).hashCode shouldBe Expression.FloatLit(3.14).hashCode
    Expression.BoolLit(true).hashCode shouldBe Expression.BoolLit(true).hashCode
  }

  // ============================================================
  // Edge Cases
  // ============================================================

  "QualifiedName with empty parts" should "handle edge case gracefully" in {
    // This tests the data structure behavior, not necessarily expected usage
    val qn = QualifiedName(List())
    qn.isSimple shouldBe false
    qn.fullName shouldBe ""
  }

  "TypeExpr.Record with empty fields" should "be valid" in {
    val emptyRecord = TypeExpr.Record(List())
    emptyRecord.fields shouldBe empty
  }

  "Expression.FunctionCall with no arguments" should "be valid" in {
    val call = Expression.FunctionCall(QualifiedName.simple("noArgs"), List())
    call.args shouldBe empty
  }

  "Expression.Projection with single field" should "be valid" in {
    val proj = Expression.Projection(
      Located(Expression.VarRef("obj"), Span(0, 3)),
      List("single")
    )
    proj.fields shouldBe List("single")
  }

  "Nested type expressions" should "be constructible" in {
    val nested = TypeExpr.Parameterized(
      "Map",
      List(
        TypeExpr.Primitive("String"),
        TypeExpr.Parameterized("List", List(
          TypeExpr.Record(List(
            ("id", TypeExpr.Primitive("Int")),
            ("data", TypeExpr.TypeRef("CustomType"))
          ))
        ))
      )
    )
    nested.name shouldBe "Map"
    nested.params.size shouldBe 2
  }

  "Deeply nested expressions" should "be constructible" in {
    val nested = Expression.Merge(
      Located(Expression.Merge(
        Located(Expression.VarRef("a"), Span(0, 1)),
        Located(Expression.VarRef("b"), Span(4, 5))
      ), Span(0, 5)),
      Located(Expression.VarRef("c"), Span(8, 9))
    )
    nested.left.value shouldBe a[Expression.Merge]
    nested.right.value shouldBe Expression.VarRef("c")
  }
}
