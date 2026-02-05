package io.constellation.lsp

import io.constellation.{CType, ComponentMetadata, ModuleNodeSpec}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class HoverContentTest extends AnyFlatSpec with Matchers {

  "Hover content" should "include type signature" in {
    val module = ModuleNodeSpec(
      metadata = ComponentMetadata("Lowercase", "Convert to lowercase", List("text"), 1, 0),
      consumes = Map("text" -> CType.CString),
      produces = Map("result" -> CType.CString)
    )

    val signature = TypeFormatter.formatSignature(
      module.name,
      module.consumes,
      module.produces
    )

    signature shouldBe "Lowercase(text: String) -> String"
  }

  it should "format parameters section correctly" in {
    val consumes = Map(
      "text"      -> CType.CString,
      "delimiter" -> CType.CString
    )

    val params = TypeFormatter.formatParameters(consumes)

    params should include("- **delimiter**: `String`")
    params should include("- **text**: `String`")
  }

  it should "format returns section correctly" in {
    val produces = Map("result" -> CType.CList(CType.CString))

    val returns = TypeFormatter.formatReturns(produces)

    returns shouldBe "`List<String>`"
  }

  it should "handle modules with no parameters" in {
    val module = ModuleNodeSpec(
      metadata = ComponentMetadata("GetTime", "Get current time", List.empty, 1, 0),
      consumes = Map.empty,
      produces = Map("time" -> CType.CString)
    )

    val signature = TypeFormatter.formatSignature(
      module.name,
      module.consumes,
      module.produces
    )

    signature shouldBe "GetTime() -> String"
  }

  it should "handle modules with multiple return values" in {
    val module = ModuleNodeSpec(
      metadata = ComponentMetadata("Split", "Split text", List.empty, 1, 0),
      consumes = Map("text" -> CType.CString, "delimiter" -> CType.CString),
      produces = Map(
        "parts" -> CType.CList(CType.CString),
        "count" -> CType.CInt
      )
    )

    val returns = TypeFormatter.formatReturns(module.produces)

    returns should include("- **count**: `Int`")
    returns should include("- **parts**: `List<String>`")
  }

  it should "handle modules with empty tags" in {
    val module = ModuleNodeSpec(
      metadata = ComponentMetadata("TestModule", "Test description", List.empty, 1, 0),
      consumes = Map("input" -> CType.CString),
      produces = Map("output" -> CType.CString)
    )

    // Tags list is empty, should handle gracefully
    module.tags shouldBe List.empty
  }

  it should "format signature with complex types" in {
    val module = ModuleNodeSpec(
      metadata = ComponentMetadata("ComplexFunc", "Complex function", List("transform"), 1, 0),
      consumes = Map(
        "items"  -> CType.CList(CType.CString),
        "config" -> CType.CMap(CType.CString, CType.CInt)
      ),
      produces = Map(
        "result" -> CType.CProduct(
          Map(
            "success" -> CType.CBoolean,
            "data"    -> CType.CList(CType.CString)
          )
        )
      )
    )

    val signature = TypeFormatter.formatSignature(
      module.name,
      module.consumes,
      module.produces
    )

    signature should include("ComplexFunc")
    signature should include("List<String>")
    signature should include("Map<String, Int>")
  }

  "Module metadata" should "provide description via helper method" in {
    val module = ModuleNodeSpec(
      metadata = ComponentMetadata("Test", "Test description", List.empty, 1, 0),
      consumes = Map.empty,
      produces = Map("out" -> CType.CString)
    )

    // Both should return the same value
    module.description shouldBe module.metadata.description
    module.description shouldBe "Test description"
  }

  it should "provide version information" in {
    val module = ModuleNodeSpec(
      metadata = ComponentMetadata("Test", "Test description", List("test"), 2, 5),
      consumes = Map.empty,
      produces = Map("out" -> CType.CString)
    )

    module.majorVersion shouldBe 2
    module.minorVersion shouldBe 5
  }

  it should "provide tags via helper method" in {
    val module = ModuleNodeSpec(
      metadata =
        ComponentMetadata("Test", "Test description", List("text", "transform", "utility"), 1, 0),
      consumes = Map.empty,
      produces = Map("out" -> CType.CString)
    )

    module.tags shouldBe List("text", "transform", "utility")
    module.tags.mkString(", ") shouldBe "text, transform, utility"
  }
}
