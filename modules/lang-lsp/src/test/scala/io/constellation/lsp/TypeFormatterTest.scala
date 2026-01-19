package io.constellation.lsp

import io.constellation.CType
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class TypeFormatterTest extends AnyFlatSpec with Matchers {

  "TypeFormatter.formatCType" should "format primitive types" in {
    TypeFormatter.formatCType(CType.CString) shouldBe "String"
    TypeFormatter.formatCType(CType.CInt) shouldBe "Int"
    TypeFormatter.formatCType(CType.CFloat) shouldBe "Float"
    TypeFormatter.formatCType(CType.CBoolean) shouldBe "Boolean"
  }

  it should "format collection types" in {
    TypeFormatter.formatCType(CType.CList(CType.CString)) shouldBe "List<String>"
    TypeFormatter.formatCType(
      CType.CMap(CType.CString, CType.CInt)
    ) shouldBe "Map<String, Int>"
  }

  it should "format product types" in {
    val product = CType.CProduct(
      Map(
        "name" -> CType.CString,
        "age"  -> CType.CInt
      )
    )
    TypeFormatter.formatCType(product) shouldBe "{ age: Int, name: String }"
  }

  it should "format union types" in {
    val union = CType.CUnion(
      Map(
        "String" -> CType.CString,
        "Int"    -> CType.CInt
      )
    )
    TypeFormatter.formatCType(union) shouldBe "(Int: Int | String: String)"
  }

  it should "format nested types" in {
    val nested = CType.CList(CType.CMap(CType.CString, CType.CInt))
    TypeFormatter.formatCType(nested) shouldBe "List<Map<String, Int>>"
  }

  it should "format optional types" in {
    TypeFormatter.formatCType(CType.COptional(CType.CString)) shouldBe "Optional<String>"
    TypeFormatter.formatCType(CType.COptional(CType.CInt)) shouldBe "Optional<Int>"
  }

  it should "format nested optional types" in {
    val nested = CType.COptional(CType.CList(CType.CString))
    TypeFormatter.formatCType(nested) shouldBe "Optional<List<String>>"
  }

  it should "format deeply nested types" in {
    val deep = CType.CList(CType.COptional(CType.CMap(CType.CString, CType.CList(CType.CInt))))
    TypeFormatter.formatCType(deep) shouldBe "List<Optional<Map<String, List<Int>>>>"
  }

  it should "format empty product type" in {
    val emptyProduct = CType.CProduct(Map.empty)
    TypeFormatter.formatCType(emptyProduct) shouldBe "{  }"
  }

  it should "format empty union type" in {
    val emptyUnion = CType.CUnion(Map.empty)
    TypeFormatter.formatCType(emptyUnion) shouldBe "()"
  }

  "TypeFormatter.formatSignature" should "format simple function signature" in {
    val sig = TypeFormatter.formatSignature(
      "Uppercase",
      Map("text" -> CType.CString),
      Map("out"  -> CType.CString)
    )
    sig shouldBe "Uppercase(text: String) -> String"
  }

  it should "format function with multiple parameters" in {
    val sig = TypeFormatter.formatSignature(
      "Add",
      Map("a"   -> CType.CInt, "b" -> CType.CInt),
      Map("out" -> CType.CInt)
    )
    sig shouldBe "Add(a: Int, b: Int) -> Int"
  }

  it should "format function with no parameters" in {
    val sig = TypeFormatter.formatSignature(
      "GetTime",
      Map.empty,
      Map("out" -> CType.CString)
    )
    sig shouldBe "GetTime() -> String"
  }

  it should "format function with record return type" in {
    val sig = TypeFormatter.formatSignature(
      "Split",
      Map("text"  -> CType.CString, "delimiter"          -> CType.CString),
      Map("parts" -> CType.CList(CType.CString), "count" -> CType.CInt)
    )
    sig shouldBe "Split(delimiter: String, text: String) -> { count: Int, parts: List<String> }"
  }

  "TypeFormatter.formatParameters" should "format parameter list" in {
    val params = TypeFormatter.formatParameters(
      Map(
        "text"  -> CType.CString,
        "count" -> CType.CInt
      )
    )
    params shouldBe "- **count**: `Int`\n- **text**: `String`"
  }

  it should "handle empty parameters" in {
    val params = TypeFormatter.formatParameters(Map.empty)
    params shouldBe "No parameters"
  }

  it should "format single parameter" in {
    val params = TypeFormatter.formatParameters(Map("value" -> CType.CFloat))
    params shouldBe "- **value**: `Float`"
  }

  "TypeFormatter.formatReturns" should "format single return value" in {
    val returns = TypeFormatter.formatReturns(Map("out" -> CType.CString))
    returns shouldBe "`String`"
  }

  it should "format multiple return values as record" in {
    val returns = TypeFormatter.formatReturns(
      Map(
        "result"  -> CType.CString,
        "success" -> CType.CBoolean
      )
    )
    returns shouldBe "- **result**: `String`\n- **success**: `Boolean`"
  }

  it should "format complex return types" in {
    val returns = TypeFormatter.formatReturns(
      Map(
        "out" -> CType.CList(CType.CMap(CType.CString, CType.CInt))
      )
    )
    returns shouldBe "`List<Map<String, Int>>`"
  }
}
