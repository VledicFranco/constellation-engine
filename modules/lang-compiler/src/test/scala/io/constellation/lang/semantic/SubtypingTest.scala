package io.constellation.lang.semantic

import io.constellation.lang.semantic.SemanticType.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SubtypingTest extends AnyFlatSpec with Matchers {

  // ===========================================================================
  // Reflexivity Tests
  // ===========================================================================

  "Subtyping" should "handle reflexivity for primitive types" in {
    Subtyping.isSubtype(SInt, SInt) shouldBe true
    Subtyping.isSubtype(SString, SString) shouldBe true
    Subtyping.isSubtype(SFloat, SFloat) shouldBe true
    Subtyping.isSubtype(SBoolean, SBoolean) shouldBe true
    Subtyping.isSubtype(SNothing, SNothing) shouldBe true
  }

  it should "handle reflexivity for complex types" in {
    val record = SRecord(Map("name" -> SString, "age" -> SInt))
    Subtyping.isSubtype(record, record) shouldBe true

    val list = SList(SInt)
    Subtyping.isSubtype(list, list) shouldBe true

    val union = SUnion(Set(SInt, SString))
    Subtyping.isSubtype(union, union) shouldBe true
  }

  // ===========================================================================
  // Bottom Type (SNothing) Tests
  // ===========================================================================

  it should "handle SNothing as bottom type - subtype of all types" in {
    Subtyping.isSubtype(SNothing, SInt) shouldBe true
    Subtyping.isSubtype(SNothing, SString) shouldBe true
    Subtyping.isSubtype(SNothing, SBoolean) shouldBe true
    Subtyping.isSubtype(SNothing, SFloat) shouldBe true
  }

  it should "handle SNothing as subtype of complex types" in {
    Subtyping.isSubtype(SNothing, SList(SInt)) shouldBe true
    Subtyping.isSubtype(SNothing, SRecord(Map("name" -> SString))) shouldBe true
    Subtyping.isSubtype(SNothing, SOptional(SInt)) shouldBe true
    Subtyping.isSubtype(SNothing, SUnion(Set(SInt, SString))) shouldBe true
    // Note: Candidates is now a legacy alias for List
    Subtyping.isSubtype(SNothing, SList(SInt)) shouldBe true
    Subtyping.isSubtype(SNothing, SMap(SString, SInt)) shouldBe true
  }

  it should "not allow other types to be subtypes of SNothing" in {
    Subtyping.isSubtype(SInt, SNothing) shouldBe false
    Subtyping.isSubtype(SString, SNothing) shouldBe false
    Subtyping.isSubtype(SList(SNothing), SNothing) shouldBe false
  }

  // ===========================================================================
  // Covariant Collections Tests
  // ===========================================================================

  it should "handle covariant lists" in {
    // List<Nothing> <: List<T> for any T
    Subtyping.isSubtype(SList(SNothing), SList(SInt)) shouldBe true
    Subtyping.isSubtype(SList(SNothing), SList(SString)) shouldBe true

    // But List<T> is not <: List<Nothing>
    Subtyping.isSubtype(SList(SInt), SList(SNothing)) shouldBe false

    // List<T> is not <: List<U> when T != U and no subtype relation
    Subtyping.isSubtype(SList(SInt), SList(SString)) shouldBe false
  }

  // Note: Candidates is now a legacy alias for List, so these tests use SList
  it should "handle covariant Lists (formerly Candidates)" in {
    Subtyping.isSubtype(SList(SNothing), SList(SInt)) shouldBe true
    Subtyping.isSubtype(SList(SNothing), SList(SString)) shouldBe true
    Subtyping.isSubtype(SList(SInt), SList(SNothing)) shouldBe false
  }

  it should "handle covariant Optional" in {
    Subtyping.isSubtype(SOptional(SNothing), SOptional(SInt)) shouldBe true
    Subtyping.isSubtype(SOptional(SNothing), SOptional(SString)) shouldBe true
    Subtyping.isSubtype(SOptional(SInt), SOptional(SNothing)) shouldBe false
  }

  it should "handle Map with invariant keys and covariant values" in {
    // Values are covariant
    Subtyping.isSubtype(SMap(SString, SNothing), SMap(SString, SInt)) shouldBe true

    // Keys are invariant
    Subtyping.isSubtype(SMap(SString, SInt), SMap(SInt, SInt)) shouldBe false
    Subtyping.isSubtype(SMap(SNothing, SInt), SMap(SString, SInt)) shouldBe false
  }

  // ===========================================================================
  // Record Subtyping Tests
  // ===========================================================================

  it should "handle record width subtyping" in {
    val narrow = SRecord(Map("name" -> SString))
    val wide   = SRecord(Map("name" -> SString, "age" -> SInt))

    // Wide record is subtype of narrow (has all required fields plus extra)
    Subtyping.isSubtype(wide, narrow) shouldBe true

    // Narrow is NOT subtype of wide (missing 'age' field)
    Subtyping.isSubtype(narrow, wide) shouldBe false
  }

  it should "handle record depth subtyping" in {
    val sub = SRecord(Map("value" -> SNothing))
    val sup = SRecord(Map("value" -> SInt))

    // Field type SNothing <: SInt, so record is subtype
    Subtyping.isSubtype(sub, sup) shouldBe true

    // Reverse doesn't hold
    Subtyping.isSubtype(sup, sub) shouldBe false
  }

  it should "handle combined width + depth record subtyping" in {
    val sub = SRecord(
      Map(
        "name" -> SString,
        "age"  -> SInt,
        "data" -> SList(SNothing)
      )
    )
    val sup = SRecord(
      Map(
        "name" -> SString,
        "data" -> SList(SInt)
      )
    )

    // sub has extra field (age) and compatible data field (List<Nothing> <: List<Int>)
    Subtyping.isSubtype(sub, sup) shouldBe true
  }

  it should "reject records with incompatible field types" in {
    val r1 = SRecord(Map("name" -> SString))
    val r2 = SRecord(Map("name" -> SInt))

    Subtyping.isSubtype(r1, r2) shouldBe false
    Subtyping.isSubtype(r2, r1) shouldBe false
  }

  it should "handle nested records" in {
    val inner1 = SRecord(Map("x" -> SInt, "y" -> SInt))
    val inner2 = SRecord(Map("x" -> SInt))

    val outer1 = SRecord(Map("point" -> inner1))
    val outer2 = SRecord(Map("point" -> inner2))

    // inner1 <: inner2 (width subtyping), so outer1 <: outer2
    Subtyping.isSubtype(outer1, outer2) shouldBe true
    Subtyping.isSubtype(outer2, outer1) shouldBe false
  }

  // ===========================================================================
  // Union Subtyping Tests
  // ===========================================================================

  it should "handle union as supertype (upper bound)" in {
    val union = SUnion(Set(SInt, SString))

    // Each member is subtype of the union
    Subtyping.isSubtype(SInt, union) shouldBe true
    Subtyping.isSubtype(SString, union) shouldBe true

    // Non-members are not subtypes
    Subtyping.isSubtype(SBoolean, union) shouldBe false
    Subtyping.isSubtype(SFloat, union) shouldBe false
  }

  it should "handle union as subtype (lower bound)" in {
    val union = SUnion(Set(SInt, SString))

    // Union is subtype of T only if ALL members are subtypes of T
    // Since Int is not <: String and String is not <: Int, this is false
    Subtyping.isSubtype(union, SInt) shouldBe false
    Subtyping.isSubtype(union, SString) shouldBe false

    // A single-member union is subtype of its member
    Subtyping.isSubtype(SUnion(Set(SInt)), SInt) shouldBe true
  }

  it should "handle subtype assignable to union" in {
    val union = SUnion(Set(SInt, SList(SInt)))

    // SNothing (bottom) is subtype of any union member, so subtype of union
    Subtyping.isSubtype(SNothing, union) shouldBe true

    // List<Nothing> is subtype of List<Int>, so subtype of union
    Subtyping.isSubtype(SList(SNothing), union) shouldBe true
  }

  it should "handle union subtyping between unions" in {
    val union1 = SUnion(Set(SInt))
    val union2 = SUnion(Set(SInt, SString))

    // union1's members (just Int) are all subtypes of members in union2
    Subtyping.isSubtype(union1, union2) shouldBe true

    // union2 has String which is not in union1
    Subtyping.isSubtype(union2, union1) shouldBe false
  }

  // ===========================================================================
  // Function Subtyping Tests
  // ===========================================================================

  it should "handle function contravariance in parameters" in {
    // (Int) => String
    val f1 = SFunction(List(SInt), SString)
    // (Nothing) => String - accepts more specific input
    val f2 = SFunction(List(SNothing), SString)

    // f1 can be used where f2 is expected (Int <: Int)
    Subtyping.isSubtype(f1, f1) shouldBe true

    // f2 cannot be used where f1 is expected
    // Because f1 expects Int, but f2 only accepts Nothing
    // Contravariance: sup param <: sub param
    Subtyping.isSubtype(f2, f1) shouldBe false
  }

  it should "handle function covariance in return type" in {
    // () => Nothing
    val f1 = SFunction(List(), SNothing)
    // () => Int
    val f2 = SFunction(List(), SInt)
    // () => String
    val f3 = SFunction(List(), SString)

    // f1 <: f2 because Nothing <: Int
    Subtyping.isSubtype(f1, f2) shouldBe true

    // f2 is not <: f1 because Int is not <: Nothing
    Subtyping.isSubtype(f2, f1) shouldBe false

    // f2 is not <: f3 because Int is not <: String
    Subtyping.isSubtype(f2, f3) shouldBe false
  }

  it should "reject functions with different parameter counts" in {
    val f1 = SFunction(List(SInt), SString)
    val f2 = SFunction(List(SInt, SInt), SString)

    Subtyping.isSubtype(f1, f2) shouldBe false
    Subtyping.isSubtype(f2, f1) shouldBe false
  }

  // ===========================================================================
  // LUB (Least Upper Bound) Tests
  // ===========================================================================

  it should "compute LUB when one type is subtype of other" in {
    Subtyping.lub(SNothing, SInt) shouldBe SInt
    Subtyping.lub(SInt, SNothing) shouldBe SInt
  }

  it should "compute LUB as union for unrelated types" in {
    val result = Subtyping.lub(SInt, SString)
    result shouldBe SUnion(Set(SInt, SString))
  }

  it should "flatten unions in LUB computation" in {
    val union1 = SUnion(Set(SInt, SString))
    val result = Subtyping.lub(union1, SBoolean)

    result shouldBe SUnion(Set(SInt, SString, SBoolean))
  }

  it should "handle LUB of identical types" in {
    Subtyping.lub(SInt, SInt) shouldBe SInt
    Subtyping.lub(SString, SString) shouldBe SString
  }

  // ===========================================================================
  // GLB (Greatest Lower Bound) Tests
  // ===========================================================================

  it should "compute GLB when one type is subtype of other" in {
    Subtyping.glb(SNothing, SInt) shouldBe SNothing
    Subtyping.glb(SInt, SNothing) shouldBe SNothing
  }

  it should "compute GLB as Nothing for unrelated types" in {
    Subtyping.glb(SInt, SString) shouldBe SNothing
  }

  it should "handle GLB of identical types" in {
    Subtyping.glb(SInt, SInt) shouldBe SInt
    Subtyping.glb(SString, SString) shouldBe SString
  }

  // ===========================================================================
  // commonType Tests
  // ===========================================================================

  it should "compute common type for list of same types" in {
    Subtyping.commonType(List(SInt, SInt, SInt)) shouldBe SInt
  }

  it should "compute common type as union for different types" in {
    val result = Subtyping.commonType(List(SInt, SString, SBoolean))
    result shouldBe SUnion(Set(SInt, SString, SBoolean))
  }

  it should "compute common type with bottom types" in {
    Subtyping.commonType(List(SNothing, SInt)) shouldBe SInt
    Subtyping.commonType(List(SInt, SNothing, SString)) shouldBe SUnion(Set(SInt, SString))
  }

  // ===========================================================================
  // isAssignable Tests
  // ===========================================================================

  it should "correctly delegate isAssignable to isSubtype" in {
    Subtyping.isAssignable(SNothing, SInt) shouldBe true
    Subtyping.isAssignable(SInt, SNothing) shouldBe false
    Subtyping.isAssignable(SInt, SInt) shouldBe true
    Subtyping.isAssignable(SInt, SString) shouldBe false
  }

  // ===========================================================================
  // explainFailure Tests
  // ===========================================================================

  it should "return None for valid subtype relationships" in {
    Subtyping.explainFailure(SNothing, SInt) shouldBe None
    Subtyping.explainFailure(SInt, SInt) shouldBe None
  }

  it should "explain missing record fields" in {
    val narrow = SRecord(Map("name" -> SString))
    val wide   = SRecord(Map("name" -> SString, "age" -> SInt))

    val explanation = Subtyping.explainFailure(narrow, wide)
    explanation shouldBe defined
    explanation.get should include("missing")
    explanation.get should include("age")
  }

  it should "explain incompatible field types" in {
    val r1 = SRecord(Map("value" -> SString))
    val r2 = SRecord(Map("value" -> SInt))

    val explanation = Subtyping.explainFailure(r1, r2)
    explanation shouldBe defined
    explanation.get should include("value")
  }

  it should "explain union membership failures" in {
    val union       = SUnion(Set(SInt, SString))
    val explanation = Subtyping.explainFailure(SBoolean, union)

    explanation shouldBe defined
    explanation.get should include("Boolean")
    explanation.get should include("union")
  }

  it should "explain function parameter count mismatch" in {
    val f1 = SFunction(List(SInt), SString)
    val f2 = SFunction(List(SInt, SInt), SString)

    val explanation = Subtyping.explainFailure(f1, f2)
    explanation shouldBe defined
    explanation.get should include("1")
    explanation.get should include("2")
  }

  // ===========================================================================
  // Edge Cases
  // ===========================================================================

  it should "handle deeply nested list subtyping" in {
    // List<List<Nothing>> <: List<List<Int>>
    Subtyping.isSubtype(
      SList(SList(SNothing)),
      SList(SList(SInt))
    ) shouldBe true
  }

  it should "handle empty records" in {
    val empty    = SRecord(Map.empty)
    val nonEmpty = SRecord(Map("x" -> SInt))

    // Any record is subtype of empty record
    Subtyping.isSubtype(nonEmpty, empty) shouldBe true
    Subtyping.isSubtype(empty, empty) shouldBe true

    // Empty record is not subtype of non-empty (missing field)
    Subtyping.isSubtype(empty, nonEmpty) shouldBe false
  }

  it should "handle single-member unions" in {
    val singleUnion = SUnion(Set(SInt))

    Subtyping.isSubtype(SInt, singleUnion) shouldBe true
    Subtyping.isSubtype(singleUnion, SInt) shouldBe true
  }

  it should "handle complex nested types" in {
    // Record with list of records
    val inner1 = SRecord(Map("id" -> SInt, "name" -> SString))
    val inner2 = SRecord(Map("id" -> SInt))
    val outer1 = SRecord(Map("items" -> SList(inner1)))
    val outer2 = SRecord(Map("items" -> SList(inner2)))

    // outer1 <: outer2 because inner1 <: inner2
    Subtyping.isSubtype(outer1, outer2) shouldBe true
    Subtyping.isSubtype(outer2, outer1) shouldBe false
  }
}
