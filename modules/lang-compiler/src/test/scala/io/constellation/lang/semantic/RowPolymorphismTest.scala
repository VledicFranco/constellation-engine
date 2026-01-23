package io.constellation.lang.semantic

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.constellation.lang.semantic.SemanticType._

/** Tests for row polymorphism implementation.
  *
  * Row polymorphism allows functions to accept records with "at least" certain fields.
  * This is essential for flexible data transformation pipelines where extra fields
  * should pass through without explicit projection.
  */
class RowPolymorphismTest extends AnyFlatSpec with Matchers {

  // ===========================================================================
  // RowVar Tests
  // ===========================================================================

  "RowVar" should "have correct prettyPrint representation" in {
    RowVar(1).prettyPrint shouldBe "ρ1"
    RowVar(42).prettyPrint shouldBe "ρ42"
    RowVar(1001).prettyPrint shouldBe "ρ1001"
  }

  // ===========================================================================
  // SOpenRecord Tests
  // ===========================================================================

  "SOpenRecord" should "have correct prettyPrint for single field" in {
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))
    open.prettyPrint shouldBe "{ name: String | ρ1 }"
  }

  it should "have correct prettyPrint for multiple fields" in {
    val open = SOpenRecord(Map("name" -> SString, "age" -> SInt), RowVar(2))
    // Note: field order in Map may vary
    open.prettyPrint should (include("name: String") and include("age: Int") and include("| ρ2"))
  }

  it should "have correct prettyPrint for empty field set" in {
    val open = SOpenRecord(Map.empty, RowVar(3))
    open.prettyPrint shouldBe "{ | ρ3 }"
  }

  // ===========================================================================
  // Subtyping Tests for Row Polymorphism
  // ===========================================================================

  "Subtyping with SOpenRecord" should "allow closed record with exact fields" in {
    val closed = SRecord(Map("name" -> SString))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    Subtyping.isSubtype(closed, open) shouldBe true
  }

  it should "allow closed record with extra fields" in {
    val closed = SRecord(Map("name" -> SString, "age" -> SInt, "email" -> SString))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    Subtyping.isSubtype(closed, open) shouldBe true
  }

  it should "reject closed record missing required fields" in {
    val closed = SRecord(Map("age" -> SInt))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    Subtyping.isSubtype(closed, open) shouldBe false
  }

  it should "reject closed record with incompatible field type" in {
    val closed = SRecord(Map("name" -> SInt))  // Wrong type!
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    Subtyping.isSubtype(closed, open) shouldBe false
  }

  it should "handle multiple required fields" in {
    val open = SOpenRecord(Map("name" -> SString, "age" -> SInt), RowVar(1))

    // Has both fields plus extra
    val valid = SRecord(Map("name" -> SString, "age" -> SInt, "email" -> SString))
    Subtyping.isSubtype(valid, open) shouldBe true

    // Missing age
    val missingAge = SRecord(Map("name" -> SString, "email" -> SString))
    Subtyping.isSubtype(missingAge, open) shouldBe false

    // Missing name
    val missingName = SRecord(Map("age" -> SInt, "email" -> SString))
    Subtyping.isSubtype(missingName, open) shouldBe false
  }

  it should "allow open record as subtype of open record" in {
    val sub = SOpenRecord(Map("name" -> SString, "age" -> SInt), RowVar(1))
    val sup = SOpenRecord(Map("name" -> SString), RowVar(2))

    Subtyping.isSubtype(sub, sup) shouldBe true
  }

  it should "reject open record as subtype of closed record" in {
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))
    val closed = SRecord(Map("name" -> SString))

    Subtyping.isSubtype(open, closed) shouldBe false
  }

  // ===========================================================================
  // Subtyping.explainFailure Tests
  // ===========================================================================

  "Subtyping.explainFailure" should "explain missing fields for open records" in {
    val closed = SRecord(Map("age" -> SInt))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    val explanation = Subtyping.explainFailure(closed, open)
    explanation shouldBe defined
    explanation.get should include("name")
  }

  it should "explain incompatible field types for open records" in {
    val closed = SRecord(Map("name" -> SInt))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    val explanation = Subtyping.explainFailure(closed, open)
    explanation shouldBe defined
    explanation.get should include("name")
  }

  // ===========================================================================
  // Row Unification Tests
  // ===========================================================================

  "RowUnification.unifyClosedWithOpen" should "capture extra fields in row variable" in {
    val closed = SRecord(Map("name" -> SString, "age" -> SInt, "active" -> SBoolean))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    val result = RowUnification.unifyClosedWithOpen(closed, open)
    result.isRight shouldBe true

    val subst = result.toOption.get
    subst(RowVar(1)) shouldBe Some(Map("age" -> SInt, "active" -> SBoolean))
  }

  it should "produce empty row for exact match" in {
    val closed = SRecord(Map("name" -> SString))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    val result = RowUnification.unifyClosedWithOpen(closed, open)
    result.isRight shouldBe true

    val subst = result.toOption.get
    subst(RowVar(1)) shouldBe Some(Map.empty)
  }

  it should "fail for missing required fields" in {
    val closed = SRecord(Map("age" -> SInt))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    val result = RowUnification.unifyClosedWithOpen(closed, open)
    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[RowUnification.MissingFields]
  }

  it should "fail for incompatible field types" in {
    val closed = SRecord(Map("name" -> SInt))
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))

    val result = RowUnification.unifyClosedWithOpen(closed, open)
    result.isLeft shouldBe true
    result.left.toOption.get shouldBe a[RowUnification.FieldTypeMismatch]
  }

  // ===========================================================================
  // Substitution Application Tests
  // ===========================================================================

  "RowUnification.applySubstitution" should "close open record when row var is bound" in {
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))
    val subst = RowUnification.Substitution.empty.withRowVar(RowVar(1), Map("age" -> SInt))

    val result = RowUnification.applySubstitution(open, subst)
    result shouldBe SRecord(Map("name" -> SString, "age" -> SInt))
  }

  it should "leave open record unchanged when row var is not bound" in {
    val open = SOpenRecord(Map("name" -> SString), RowVar(1))
    val subst = RowUnification.Substitution.empty

    val result = RowUnification.applySubstitution(open, subst)
    result shouldBe open
  }

  it should "handle nested types" in {
    val open = SList(SOpenRecord(Map("id" -> SInt), RowVar(1)))
    val subst = RowUnification.Substitution.empty.withRowVar(RowVar(1), Map("name" -> SString))

    val result = RowUnification.applySubstitution(open, subst)
    result shouldBe SList(SRecord(Map("id" -> SInt, "name" -> SString)))
  }

  // ===========================================================================
  // FunctionSignature Row Polymorphism Tests
  // ===========================================================================

  "FunctionSignature" should "detect row-polymorphic signatures" in {
    val rv = RowVar(1)
    val rowPolySig = FunctionSignature(
      name = "GetName",
      params = List(("record", SOpenRecord(Map("name" -> SString), rv))),
      returns = SString,
      moduleName = "test.get-name",
      rowVars = List(rv)
    )
    val normalSig = FunctionSignature(
      name = "Length",
      params = List(("str", SString)),
      returns = SInt,
      moduleName = "test.length"
    )

    rowPolySig.isRowPolymorphic shouldBe true
    normalSig.isRowPolymorphic shouldBe false
  }

  it should "instantiate fresh row variables" in {
    val rv = RowVar(1)
    val sig = FunctionSignature(
      name = "GetName",
      params = List(("record", SOpenRecord(Map("name" -> SString), rv))),
      returns = SString,
      moduleName = "test.get-name",
      rowVars = List(rv)
    )

    var counter = 100
    val freshGen = () => { counter += 1; RowVar(counter) }

    val instantiated = sig.instantiate(freshGen)

    instantiated.rowVars should have size 1
    instantiated.rowVars.head shouldNot be(rv)
    instantiated.rowVars.head.id shouldBe 101

    // Check the param type was also updated
    instantiated.params.head._2 match {
      case SOpenRecord(_, newRv) => newRv.id shouldBe 101
      case _ => fail("Expected SOpenRecord")
    }
  }

  // ===========================================================================
  // Substitution Merge Tests
  // ===========================================================================

  "Substitution.merge" should "combine substitutions" in {
    val s1 = RowUnification.Substitution.empty.withRowVar(RowVar(1), Map("a" -> SInt))
    val s2 = RowUnification.Substitution.empty.withRowVar(RowVar(2), Map("b" -> SString))

    val merged = s1.merge(s2)
    merged(RowVar(1)) shouldBe Some(Map("a" -> SInt))
    merged(RowVar(2)) shouldBe Some(Map("b" -> SString))
  }

  it should "let second substitution win on conflicts" in {
    val s1 = RowUnification.Substitution.empty.withRowVar(RowVar(1), Map("a" -> SInt))
    val s2 = RowUnification.Substitution.empty.withRowVar(RowVar(1), Map("b" -> SString))

    val merged = s1.merge(s2)
    merged(RowVar(1)) shouldBe Some(Map("b" -> SString))
  }

  // ===========================================================================
  // Edge Cases
  // ===========================================================================

  "Row polymorphism" should "handle nested record fields" in {
    val inner = SRecord(Map("x" -> SInt, "y" -> SInt))
    val closed = SRecord(Map("point" -> inner, "color" -> SString))
    val open = SOpenRecord(Map("point" -> inner), RowVar(1))

    Subtyping.isSubtype(closed, open) shouldBe true

    val result = RowUnification.unifyClosedWithOpen(closed, open)
    result.isRight shouldBe true
    result.toOption.get(RowVar(1)) shouldBe Some(Map("color" -> SString))
  }

  it should "handle list field types" in {
    val closed = SRecord(Map("items" -> SList(SInt), "count" -> SInt))
    val open = SOpenRecord(Map("items" -> SList(SInt)), RowVar(1))

    Subtyping.isSubtype(closed, open) shouldBe true

    val result = RowUnification.unifyClosedWithOpen(closed, open)
    result.isRight shouldBe true
    result.toOption.get(RowVar(1)) shouldBe Some(Map("count" -> SInt))
  }

  it should "use subtyping for field type checking" in {
    // List<Nothing> is subtype of List<Int>
    val closed = SRecord(Map("items" -> SList(SNothing)))
    val open = SOpenRecord(Map("items" -> SList(SInt)), RowVar(1))

    Subtyping.isSubtype(closed, open) shouldBe true

    val result = RowUnification.unifyClosedWithOpen(closed, open)
    result.isRight shouldBe true
  }

  it should "handle empty open record (accepts any record)" in {
    val closed = SRecord(Map("a" -> SInt, "b" -> SString))
    val open = SOpenRecord(Map.empty, RowVar(1))

    Subtyping.isSubtype(closed, open) shouldBe true

    val result = RowUnification.unifyClosedWithOpen(closed, open)
    result.isRight shouldBe true
    result.toOption.get(RowVar(1)) shouldBe Some(Map("a" -> SInt, "b" -> SString))
  }
}
