package io.constellation

import io.constellation.InlineTransform.*

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class InlineTransformTest extends AnyFlatSpec with Matchers {

  // ========== MergeTransform Tests ==========

  "MergeTransform" should "merge two records (Map + Map)" in {
    val transform = MergeTransform(
      CType.CProduct(Map("a" -> CType.CInt)),
      CType.CProduct(Map("b" -> CType.CString))
    )

    val result = transform(
      Map(
        "left"  -> Map("a" -> 1),
        "right" -> Map("b" -> "hello")
      )
    )

    result shouldBe Map("a" -> 1, "b" -> "hello")
  }

  it should "merge records with overlapping fields (right wins)" in {
    val transform = MergeTransform(
      CType.CProduct(Map("x" -> CType.CInt)),
      CType.CProduct(Map("x" -> CType.CInt))
    )

    val result = transform(
      Map(
        "left"  -> Map("x" -> 1, "y" -> 2),
        "right" -> Map("x" -> 10, "z" -> 3)
      )
    )

    result shouldBe Map("x" -> 10, "y" -> 2, "z" -> 3)
  }

  it should "merge Candidates element-wise (List + List)" in {
    val transform = MergeTransform(
      CType.CList(CType.CProduct(Map("a" -> CType.CInt))),
      CType.CList(CType.CProduct(Map("b" -> CType.CString)))
    )

    val result = transform(
      Map(
        "left"  -> List(Map("a" -> 1), Map("a" -> 2)),
        "right" -> List(Map("b" -> "x"), Map("b" -> "y"))
      )
    )

    result shouldBe List(
      Map("a" -> 1, "b" -> "x"),
      Map("a" -> 2, "b" -> "y")
    )
  }

  it should "throw when merging Candidates with different lengths" in {
    val transform = MergeTransform(
      CType.CList(CType.CProduct(Map("a" -> CType.CInt))),
      CType.CList(CType.CProduct(Map("b" -> CType.CInt)))
    )

    val exception = intercept[IllegalArgumentException] {
      transform(
        Map(
          "left"  -> List(Map("a" -> 1), Map("a" -> 2)),
          "right" -> List(Map("b" -> 10)) // Different length
        )
      )
    }
    exception.getMessage should include("different lengths")
  }

  it should "broadcast Record to Candidates (List + Map)" in {
    val transform = MergeTransform(
      CType.CList(CType.CProduct(Map("a" -> CType.CInt))),
      CType.CProduct(Map("b" -> CType.CString))
    )

    val result = transform(
      Map(
        "left"  -> List(Map("a" -> 1), Map("a" -> 2), Map("a" -> 3)),
        "right" -> Map("b" -> "shared")
      )
    )

    result shouldBe List(
      Map("a" -> 1, "b" -> "shared"),
      Map("a" -> 2, "b" -> "shared"),
      Map("a" -> 3, "b" -> "shared")
    )
  }

  it should "broadcast Record to Candidates (Map + List)" in {
    val transform = MergeTransform(
      CType.CProduct(Map("prefix" -> CType.CString)),
      CType.CList(CType.CProduct(Map("value" -> CType.CInt)))
    )

    val result = transform(
      Map(
        "left"  -> Map("prefix" -> "item"),
        "right" -> List(Map("value" -> 1), Map("value" -> 2))
      )
    )

    result shouldBe List(
      Map("prefix" -> "item", "value" -> 1),
      Map("prefix" -> "item", "value" -> 2)
    )
  }

  it should "return right for incompatible types (fallback)" in {
    val transform = MergeTransform(CType.CInt, CType.CString)

    val result = transform(
      Map(
        "left"  -> 42,
        "right" -> "hello"
      )
    )

    result shouldBe "hello"
  }

  // ========== ProjectTransform Tests ==========

  "ProjectTransform" should "project fields from a record" in {
    val transform = ProjectTransform(
      List("name", "age"),
      CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt, "email" -> CType.CString))
    )

    val result = transform(
      Map(
        "source" -> Map("name" -> "Alice", "age" -> 30, "email" -> "alice@test.com")
      )
    )

    result shouldBe Map("name" -> "Alice", "age" -> 30)
  }

  it should "ignore non-existent fields" in {
    val transform = ProjectTransform(
      List("name", "missing"),
      CType.CProduct(Map("name" -> CType.CString))
    )

    val result = transform(
      Map(
        "source" -> Map("name" -> "Bob")
      )
    )

    result shouldBe Map("name" -> "Bob")
  }

  it should "project fields from Candidates (list of records)" in {
    val transform = ProjectTransform(
      List("x"),
      CType.CList(CType.CProduct(Map("x" -> CType.CInt, "y" -> CType.CInt)))
    )

    val result = transform(
      Map(
        "source" -> List(Map("x" -> 1, "y" -> 2), Map("x" -> 10, "y" -> 20))
      )
    )

    result shouldBe List(Map("x" -> 1), Map("x" -> 10))
  }

  it should "return value unchanged for non-product types" in {
    val transform = ProjectTransform(List("x"), CType.CInt)

    val result = transform(Map("source" -> 42))

    result shouldBe 42
  }

  // ========== FieldAccessTransform Tests ==========

  "FieldAccessTransform" should "extract single field from record" in {
    val transform = FieldAccessTransform(
      "name",
      CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))
    )

    val result = transform(
      Map(
        "source" -> Map("name" -> "Charlie", "age" -> 25)
      )
    )

    result shouldBe "Charlie"
  }

  it should "return MatchBindingMissing for non-existent field" in {
    val transform = FieldAccessTransform(
      "missing",
      CType.CProduct(Map("name" -> CType.CString))
    )

    val result = transform(Map("source" -> Map("name" -> "Test")))
    result shouldBe MatchBindingMissing
  }

  it should "extract field from Candidates (list of records)" in {
    val transform = FieldAccessTransform(
      "score",
      CType.CList(CType.CProduct(Map("score" -> CType.CInt)))
    )

    val result = transform(
      Map(
        "source" -> List(Map("score" -> 100), Map("score" -> 85), Map("score" -> 92))
      )
    )

    result shouldBe List(100, 85, 92)
  }

  it should "return MatchBindingMissing for non-record type" in {
    val transform = FieldAccessTransform("field", CType.CInt)

    val result = transform(Map("source" -> 42))
    result shouldBe MatchBindingMissing
  }

  // ========== ConditionalTransform Tests ==========

  "ConditionalTransform" should "return thenBr when condition is true" in {
    val result = ConditionalTransform(
      Map(
        "cond"   -> true,
        "thenBr" -> "yes",
        "elseBr" -> "no"
      )
    )

    result shouldBe "yes"
  }

  it should "return elseBr when condition is false" in {
    val result = ConditionalTransform(
      Map(
        "cond"   -> false,
        "thenBr" -> 42,
        "elseBr" -> 0
      )
    )

    result shouldBe 0
  }

  it should "work with complex branch values" in {
    val result = ConditionalTransform(
      Map(
        "cond"   -> true,
        "thenBr" -> Map("a" -> 1, "b" -> 2),
        "elseBr" -> Map("x" -> 10)
      )
    )

    result shouldBe Map("a" -> 1, "b" -> 2)
  }

  // ========== GuardTransform Tests ==========

  "GuardTransform" should "return Some(expr) when condition is true" in {
    val result = GuardTransform(
      Map(
        "cond" -> true,
        "expr" -> "value"
      )
    )

    result shouldBe Some("value")
  }

  it should "return None when condition is false" in {
    val result = GuardTransform(
      Map(
        "cond" -> false,
        "expr" -> "value"
      )
    )

    result shouldBe None
  }

  // ========== CoalesceTransform Tests ==========

  "CoalesceTransform" should "unwrap Some value" in {
    val result = CoalesceTransform(
      Map(
        "left"  -> Some("inner"),
        "right" -> "fallback"
      )
    )

    result shouldBe "inner"
  }

  it should "return fallback for None" in {
    val result = CoalesceTransform(
      Map(
        "left"  -> None,
        "right" -> "fallback"
      )
    )

    result shouldBe "fallback"
  }

  it should "work with complex types" in {
    val result = CoalesceTransform(
      Map(
        "left"  -> Some(List(1, 2, 3)),
        "right" -> List.empty
      )
    )

    result shouldBe List(1, 2, 3)
  }

  // ========== AndTransform Tests ==========

  "AndTransform" should "return true when both operands are true" in {
    val result = AndTransform(Map("left" -> true, "right" -> true))
    result shouldBe true
  }

  it should "return false when left is false (short-circuit)" in {
    // Right operand is not evaluated when left is false
    val result = AndTransform(Map("left" -> false, "right" -> true))
    result shouldBe false
  }

  it should "return false when right is false" in {
    val result = AndTransform(Map("left" -> true, "right" -> false))
    result shouldBe false
  }

  it should "return false when both are false" in {
    val result = AndTransform(Map("left" -> false, "right" -> false))
    result shouldBe false
  }

  // ========== OrTransform Tests ==========

  "OrTransform" should "return true when left is true (short-circuit)" in {
    val result = OrTransform(Map("left" -> true, "right" -> false))
    result shouldBe true
  }

  it should "return true when right is true" in {
    val result = OrTransform(Map("left" -> false, "right" -> true))
    result shouldBe true
  }

  it should "return false when both are false" in {
    val result = OrTransform(Map("left" -> false, "right" -> false))
    result shouldBe false
  }

  it should "return true when both are true" in {
    val result = OrTransform(Map("left" -> true, "right" -> true))
    result shouldBe true
  }

  // ========== NotTransform Tests ==========

  "NotTransform" should "negate true to false" in {
    val result = NotTransform(Map("operand" -> true))
    result shouldBe false
  }

  it should "negate false to true" in {
    val result = NotTransform(Map("operand" -> false))
    result shouldBe true
  }

  // ========== LiteralTransform Tests ==========

  "LiteralTransform" should "produce constant string value" in {
    val transform = LiteralTransform("constant")
    val result    = transform(Map.empty) // Ignores inputs

    result shouldBe "constant"
  }

  it should "produce constant numeric value" in {
    val transform = LiteralTransform(42)
    val result    = transform(Map("ignored" -> "input"))

    result shouldBe 42
  }

  it should "produce constant complex value" in {
    val transform = LiteralTransform(List(1, 2, 3))
    val result    = transform(Map.empty)

    result shouldBe List(1, 2, 3)
  }

  it should "produce null value" in {
    val transform = LiteralTransform(null)
    val result    = transform(Map.empty)

    (result == null) shouldBe true
  }

  // ========== StringInterpolationTransform Tests ==========

  "StringInterpolationTransform" should "interpolate single expression" in {
    val transform = StringInterpolationTransform(List("Hello, ", "!"))
    val result    = transform(Map("expr0" -> "World"))

    result shouldBe "Hello, World!"
  }

  it should "interpolate multiple expressions" in {
    val transform = StringInterpolationTransform(List("", " + ", " = ", ""))
    val result = transform(
      Map(
        "expr0" -> 2,
        "expr1" -> 3,
        "expr2" -> 5
      )
    )

    result shouldBe "2 + 3 = 5"
  }

  it should "handle empty parts" in {
    val transform = StringInterpolationTransform(List("", "", ""))
    val result    = transform(Map("expr0" -> "A", "expr1" -> "B"))

    result shouldBe "AB"
  }

  it should "stringify numbers" in {
    val transform = StringInterpolationTransform(List("Value: ", ""))
    val result    = transform(Map("expr0" -> 3.14))

    result shouldBe "Value: 3.14"
  }

  it should "stringify booleans" in {
    val transform = StringInterpolationTransform(List("Is active: ", ""))
    val result    = transform(Map("expr0" -> true))

    result shouldBe "Is active: true"
  }

  it should "stringify None as empty string" in {
    val transform = StringInterpolationTransform(List("Value: ", " end"))
    val result    = transform(Map("expr0" -> None))

    result shouldBe "Value:  end"
  }

  it should "stringify Some by unwrapping" in {
    val transform = StringInterpolationTransform(List("Value: ", ""))
    val result    = transform(Map("expr0" -> Some("inner")))

    result shouldBe "Value: inner"
  }

  it should "stringify lists" in {
    val transform = StringInterpolationTransform(List("Items: ", ""))
    val result    = transform(Map("expr0" -> List(1, 2, 3)))

    result shouldBe "Items: [1, 2, 3]"
  }

  it should "stringify maps" in {
    val transform = StringInterpolationTransform(List("Data: ", ""))
    val result    = transform(Map("expr0" -> Map("a" -> 1)))

    result shouldBe "Data: {a: 1}"
  }

  // ========== FilterTransform Tests ==========

  "FilterTransform" should "filter list with predicate" in {
    val transform = FilterTransform((elem: Any) => elem.asInstanceOf[Int] > 5)

    val result = transform(Map("source" -> List(1, 10, 3, 8, 2, 7)))

    result shouldBe List(10, 8, 7)
  }

  it should "return empty list when no elements match" in {
    val transform = FilterTransform((elem: Any) => elem.asInstanceOf[Int] < 0)

    val result = transform(Map("source" -> List(1, 2, 3)))

    result shouldBe List.empty
  }

  it should "return all elements when all match" in {
    val transform = FilterTransform((elem: Any) => elem.asInstanceOf[Int] > 0)

    val result = transform(Map("source" -> List(1, 2, 3)))

    result shouldBe List(1, 2, 3)
  }

  it should "handle empty source list" in {
    val transform = FilterTransform((elem: Any) => true)

    val result = transform(Map("source" -> List.empty))

    result shouldBe List.empty
  }

  // ========== MapTransform Tests ==========

  "MapTransform" should "transform each element" in {
    val transform = MapTransform((elem: Any) => elem.asInstanceOf[Int] * 2)

    val result = transform(Map("source" -> List(1, 2, 3)))

    result shouldBe List(2, 4, 6)
  }

  it should "handle type conversion" in {
    val transform = MapTransform((elem: Any) => elem.toString)

    val result = transform(Map("source" -> List(1, 2, 3)))

    result shouldBe List("1", "2", "3")
  }

  it should "handle empty source list" in {
    val transform = MapTransform((elem: Any) => elem.asInstanceOf[Int] * 2)

    val result = transform(Map("source" -> List.empty))

    result shouldBe List.empty
  }

  it should "support complex transformations" in {
    val transform = MapTransform { (elem: Any) =>
      val m = elem.asInstanceOf[Map[String, Any]]
      m("value").asInstanceOf[Int] + 10
    }

    val result = transform(
      Map(
        "source" -> List(Map("value" -> 1), Map("value" -> 2))
      )
    )

    result shouldBe List(11, 12)
  }

  // ========== AllTransform Tests ==========

  "AllTransform" should "return true when all elements match" in {
    val transform = AllTransform((elem: Any) => elem.asInstanceOf[Int] > 0)

    val result = transform(Map("source" -> List(1, 2, 3)))

    result shouldBe true
  }

  it should "return false when any element doesn't match" in {
    val transform = AllTransform((elem: Any) => elem.asInstanceOf[Int] > 0)

    val result = transform(Map("source" -> List(1, -1, 3)))

    result shouldBe false
  }

  it should "return true for empty list" in {
    val transform = AllTransform((elem: Any) => false)

    val result = transform(Map("source" -> List.empty))

    result shouldBe true // vacuously true
  }

  // ========== AnyTransform Tests ==========

  "AnyTransform" should "return true when any element matches" in {
    val transform = AnyTransform((elem: Any) => elem.asInstanceOf[Int] > 10)

    val result = transform(Map("source" -> List(1, 2, 15, 3)))

    result shouldBe true
  }

  it should "return false when no element matches" in {
    val transform = AnyTransform((elem: Any) => elem.asInstanceOf[Int] > 100)

    val result = transform(Map("source" -> List(1, 2, 3)))

    result shouldBe false
  }

  it should "return false for empty list" in {
    val transform = AnyTransform((elem: Any) => true)

    val result = transform(Map("source" -> List.empty))

    result shouldBe false
  }

  // ========== Integration / Edge Cases ==========

  "InlineTransform" should "work with deeply nested data in MergeTransform" in {
    val transform = MergeTransform(
      CType.CList(CType.CList(CType.CProduct(Map("x" -> CType.CInt)))),
      CType.CList(CType.CList(CType.CProduct(Map("y" -> CType.CInt))))
    )

    val result = transform(
      Map(
        "left"  -> List(List(Map("x" -> 1)), List(Map("x" -> 2))),
        "right" -> List(List(Map("y" -> 10)), List(Map("y" -> 20)))
      )
    )

    result shouldBe List(
      List(Map("x" -> 1, "y" -> 10)),
      List(Map("x" -> 2, "y" -> 20))
    )
  }

  it should "handle chained field access through nested lists" in {
    val innerType = CType.CProduct(Map("data" -> CType.CProduct(Map("value" -> CType.CInt))))
    val transform = FieldAccessTransform(
      "data",
      CType.CList(innerType)
    )

    val result = transform(
      Map(
        "source" -> List(
          Map("data" -> Map("value" -> 1)),
          Map("data" -> Map("value" -> 2))
        )
      )
    )

    result shouldBe List(Map("value" -> 1), Map("value" -> 2))
  }

  // ========== MatchTransform Tests ==========

  "MatchTransform" should "match first matching pattern" in {
    val matchers = List(
      (v: Any) => v == "a",
      (v: Any) => v == "b",
      (v: Any) => true // wildcard
    )
    val bodies = List(
      (v: Any) => "matched-a",
      (v: Any) => "matched-b",
      (v: Any) => "matched-wildcard"
    )
    val transform = MatchTransform(matchers, bodies, CType.CString)

    transform(Map("scrutinee" -> "a")) shouldBe "matched-a"
    transform(Map("scrutinee" -> "b")) shouldBe "matched-b"
    transform(Map("scrutinee" -> "c")) shouldBe "matched-wildcard"
  }

  it should "throw MatchError when no pattern matches" in {
    val matchers = List((v: Any) => v == "x")
    val bodies   = List((v: Any) => "matched")
    val transform = MatchTransform(matchers, bodies, CType.CString)

    a[MatchError] should be thrownBy {
      transform(Map("scrutinee" -> "y"))
    }
  }

  it should "unwrap union values for matching" in {
    val matchers = List(
      (v: Any) => true // matches the inner value
    )
    val bodies = List(
      (v: Any) => s"matched: $v"
    )
    val variants = Map("Int" -> CType.CInt, "String" -> CType.CString)
    val transform = MatchTransform(matchers, bodies, CType.CUnion(variants))

    // Union value is represented as (tag, innerValue) tuple
    val result = transform(Map("scrutinee" -> ("Int", 42)))
    result shouldBe "matched: (Int,42)"
  }

  it should "handle non-union scrutinee without unwrapping" in {
    val matchers = List((v: Any) => v.asInstanceOf[Int] > 10)
    val bodies   = List((v: Any) => s"big: $v")
    val transform = MatchTransform(matchers, bodies, CType.CInt)

    transform(Map("scrutinee" -> 42)) shouldBe "big: 42"
  }

  // ========== RecordBuildTransform Tests ==========

  "RecordBuildTransform" should "build a record from named inputs" in {
    val transform = RecordBuildTransform(List("name", "age"))

    val result = transform(Map("name" -> "Alice", "age" -> 30))

    result shouldBe Map("name" -> "Alice", "age" -> 30)
  }

  it should "build a single-field record" in {
    val transform = RecordBuildTransform(List("value"))

    val result = transform(Map("value" -> 42))

    result shouldBe Map("value" -> 42)
  }

  it should "build an empty record" in {
    val transform = RecordBuildTransform(List.empty)

    val result = transform(Map.empty)

    result shouldBe Map.empty
  }

  it should "handle complex field values" in {
    val transform = RecordBuildTransform(List("items", "meta"))

    val result = transform(Map(
      "items" -> List(1, 2, 3),
      "meta"  -> Map("source" -> "test")
    ))

    result shouldBe Map(
      "items" -> List(1, 2, 3),
      "meta"  -> Map("source" -> "test")
    )
  }

  // ========== ListLiteralTransform Tests ==========

  "ListLiteralTransform" should "assemble elements into a list" in {
    val transform = ListLiteralTransform(3)

    val result = transform(Map("elem0" -> 1, "elem1" -> 2, "elem2" -> 3))

    result shouldBe List(1, 2, 3)
  }

  it should "build single-element list" in {
    val transform = ListLiteralTransform(1)

    val result = transform(Map("elem0" -> "only"))

    result shouldBe List("only")
  }

  it should "build empty list" in {
    val transform = ListLiteralTransform(0)

    val result = transform(Map.empty)

    result shouldBe List.empty
  }

  // ========== FieldAccessTransform with Union Tests ==========

  "FieldAccessTransform with union" should "access field from union inner record" in {
    val recordType = CType.CProduct(Map("name" -> CType.CString, "age" -> CType.CInt))
    val unionType  = CType.CUnion(Map("Person" -> recordType, "Error" -> CType.CString))
    val transform  = FieldAccessTransform("name", unionType)

    val result = transform(Map("source" -> ("Person", Map("name" -> "Alice", "age" -> 30))))

    result shouldBe "Alice"
  }

  it should "return MatchBindingMissing for unknown union tag" in {
    val unionType = CType.CUnion(Map("A" -> CType.CInt))
    val transform = FieldAccessTransform("field", unionType)

    val result = transform(Map("source" -> ("Unknown", 42)))

    result shouldBe MatchBindingMissing
  }
}
