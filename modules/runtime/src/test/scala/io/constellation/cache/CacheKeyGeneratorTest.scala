package io.constellation.cache

import io.constellation.{CType, CValue}

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CacheKeyGeneratorExtendedTest extends AnyFlatSpec with Matchers {

  // -------------------------------------------------------------------------
  // Helper values
  // -------------------------------------------------------------------------

  private val simpleInputs: Map[String, CValue] = Map(
    "text" -> CValue.CString("hello"),
    "count" -> CValue.CInt(42)
  )

  // -------------------------------------------------------------------------
  // generateKey - determinism and uniqueness
  // -------------------------------------------------------------------------

  "generateKey" should "produce deterministic keys for the same inputs" in {
    val key1 = CacheKeyGenerator.generateKey("MyModule", simpleInputs)
    val key2 = CacheKeyGenerator.generateKey("MyModule", simpleInputs)

    key1 shouldBe key2
  }

  it should "produce different keys for different module names" in {
    val key1 = CacheKeyGenerator.generateKey("ModuleA", simpleInputs)
    val key2 = CacheKeyGenerator.generateKey("ModuleB", simpleInputs)

    key1 should not be key2
  }

  it should "produce different keys for different input values" in {
    val inputs1 = Map("x" -> CValue.CInt(1): (String, CValue))
    val inputs2 = Map("x" -> CValue.CInt(2): (String, CValue))

    val key1 = CacheKeyGenerator.generateKey("Mod", inputs1)
    val key2 = CacheKeyGenerator.generateKey("Mod", inputs2)

    key1 should not be key2
  }

  it should "produce different keys for different input names" in {
    val inputs1 = Map("a" -> CValue.CInt(1): (String, CValue))
    val inputs2 = Map("b" -> CValue.CInt(1): (String, CValue))

    val key1 = CacheKeyGenerator.generateKey("Mod", inputs1)
    val key2 = CacheKeyGenerator.generateKey("Mod", inputs2)

    key1 should not be key2
  }

  it should "handle empty inputs" in {
    val key = CacheKeyGenerator.generateKey("Mod", Map.empty)

    key should not be empty
  }

  it should "produce different keys with and without version" in {
    val keyNoVersion = CacheKeyGenerator.generateKey("Mod", simpleInputs, None)
    val keyWithVersion = CacheKeyGenerator.generateKey("Mod", simpleInputs, Some("1.0"))

    keyNoVersion should not be keyWithVersion
  }

  it should "produce different keys for different versions" in {
    val key1 = CacheKeyGenerator.generateKey("Mod", simpleInputs, Some("1.0"))
    val key2 = CacheKeyGenerator.generateKey("Mod", simpleInputs, Some("2.0"))

    key1 should not be key2
  }

  it should "produce the same key with version specified identically" in {
    val key1 = CacheKeyGenerator.generateKey("Mod", simpleInputs, Some("1.0"))
    val key2 = CacheKeyGenerator.generateKey("Mod", simpleInputs, Some("1.0"))

    key1 shouldBe key2
  }

  it should "default version to None" in {
    val keyDefault = CacheKeyGenerator.generateKey("Mod", simpleInputs)
    val keyExplicitNone = CacheKeyGenerator.generateKey("Mod", simpleInputs, None)

    keyDefault shouldBe keyExplicitNone
  }

  // -------------------------------------------------------------------------
  // generateKey - all CValue types
  // -------------------------------------------------------------------------

  "generateKey with CString" should "produce a valid key" in {
    val inputs = Map("s" -> CValue.CString("world"): (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  "generateKey with CInt" should "produce a valid key" in {
    val inputs = Map("i" -> CValue.CInt(99L): (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  "generateKey with CFloat" should "produce a valid key" in {
    val inputs = Map("f" -> CValue.CFloat(3.14): (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  "generateKey with CBoolean" should "produce a valid key" in {
    val inputsTrue = Map("b" -> CValue.CBoolean(true): (String, CValue))
    val inputsFalse = Map("b" -> CValue.CBoolean(false): (String, CValue))

    val keyTrue = CacheKeyGenerator.generateKey("Mod", inputsTrue)
    val keyFalse = CacheKeyGenerator.generateKey("Mod", inputsFalse)

    keyTrue should not be empty
    keyFalse should not be empty
    keyTrue should not be keyFalse
  }

  "generateKey with CList" should "produce a valid key" in {
    val list = CValue.CList(
      Vector(CValue.CInt(1), CValue.CInt(2), CValue.CInt(3)),
      CType.CInt
    )
    val inputs = Map("lst" -> list: (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "produce different keys for different list contents" in {
    val list1 = CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2)), CType.CInt)
    val list2 = CValue.CList(Vector(CValue.CInt(3), CValue.CInt(4)), CType.CInt)

    val key1 = CacheKeyGenerator.generateKey("Mod", Map("lst" -> list1))
    val key2 = CacheKeyGenerator.generateKey("Mod", Map("lst" -> list2))

    key1 should not be key2
  }

  it should "produce different keys for lists with different order" in {
    val list1 = CValue.CList(Vector(CValue.CInt(1), CValue.CInt(2)), CType.CInt)
    val list2 = CValue.CList(Vector(CValue.CInt(2), CValue.CInt(1)), CType.CInt)

    val key1 = CacheKeyGenerator.generateKey("Mod", Map("lst" -> list1))
    val key2 = CacheKeyGenerator.generateKey("Mod", Map("lst" -> list2))

    key1 should not be key2
  }

  "generateKey with CMap" should "produce a valid key" in {
    val cmap = CValue.CMap(
      Vector(
        (CValue.CString("a"), CValue.CInt(1)),
        (CValue.CString("b"), CValue.CInt(2))
      ),
      CType.CString,
      CType.CInt
    )
    val inputs = Map("m" -> cmap: (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "produce the same key regardless of CMap pair ordering" in {
    val cmap1 = CValue.CMap(
      Vector(
        (CValue.CString("a"), CValue.CInt(1)),
        (CValue.CString("b"), CValue.CInt(2))
      ),
      CType.CString,
      CType.CInt
    )
    val cmap2 = CValue.CMap(
      Vector(
        (CValue.CString("b"), CValue.CInt(2)),
        (CValue.CString("a"), CValue.CInt(1))
      ),
      CType.CString,
      CType.CInt
    )

    val key1 = CacheKeyGenerator.generateKey("Mod", Map("m" -> cmap1))
    val key2 = CacheKeyGenerator.generateKey("Mod", Map("m" -> cmap2))

    key1 shouldBe key2
  }

  "generateKey with CProduct" should "produce a valid key" in {
    val product = CValue.CProduct(
      Map("name" -> CValue.CString("Alice"), "age" -> CValue.CInt(30)),
      Map("name" -> CType.CString, "age" -> CType.CInt)
    )
    val inputs = Map("p" -> product: (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "produce the same key regardless of CProduct field ordering" in {
    val product1 = CValue.CProduct(
      Map("x" -> CValue.CInt(1), "y" -> CValue.CInt(2)),
      Map("x" -> CType.CInt, "y" -> CType.CInt)
    )
    val product2 = CValue.CProduct(
      Map("y" -> CValue.CInt(2), "x" -> CValue.CInt(1)),
      Map("y" -> CType.CInt, "x" -> CType.CInt)
    )

    val key1 = CacheKeyGenerator.generateKey("Mod", Map("p" -> product1))
    val key2 = CacheKeyGenerator.generateKey("Mod", Map("p" -> product2))

    key1 shouldBe key2
  }

  "generateKey with CUnion" should "produce a valid key" in {
    val union = CValue.CUnion(
      CValue.CString("hello"),
      Map("Text" -> CType.CString, "Number" -> CType.CInt),
      "Text"
    )
    val inputs = Map("u" -> union: (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "produce different keys for different union tags" in {
    val structure = Map("Text" -> CType.CString, "Number" -> CType.CInt)
    val union1 = CValue.CUnion(CValue.CString("hello"), structure, "Text")
    val union2 = CValue.CUnion(CValue.CInt(42), structure, "Number")

    val key1 = CacheKeyGenerator.generateKey("Mod", Map("u" -> union1))
    val key2 = CacheKeyGenerator.generateKey("Mod", Map("u" -> union2))

    key1 should not be key2
  }

  "generateKey with CSome" should "produce a valid key" in {
    val some = CValue.CSome(CValue.CString("present"), CType.CString)
    val inputs = Map("opt" -> some: (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  "generateKey with CNone" should "produce a valid key" in {
    val none = CValue.CNone(CType.CString)
    val inputs = Map("opt" -> none: (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  "generateKey with CSome vs CNone" should "produce different keys" in {
    val some = CValue.CSome(CValue.CString("value"), CType.CString)
    val none = CValue.CNone(CType.CString)

    val key1 = CacheKeyGenerator.generateKey("Mod", Map("opt" -> some))
    val key2 = CacheKeyGenerator.generateKey("Mod", Map("opt" -> none))

    key1 should not be key2
  }

  // -------------------------------------------------------------------------
  // generateShortKey
  // -------------------------------------------------------------------------

  "generateShortKey" should "produce a key of default length 8" in {
    val shortKey = CacheKeyGenerator.generateShortKey("Mod", simpleInputs)

    shortKey should have length 8
  }

  it should "produce a key of the specified custom length" in {
    val shortKey = CacheKeyGenerator.generateShortKey("Mod", simpleInputs, length = 12)

    shortKey should have length 12
  }

  it should "produce a prefix of the full key" in {
    val fullKey = CacheKeyGenerator.generateKey("Mod", simpleInputs)
    val shortKey = CacheKeyGenerator.generateShortKey("Mod", simpleInputs)

    fullKey should startWith(shortKey)
  }

  it should "be deterministic" in {
    val key1 = CacheKeyGenerator.generateShortKey("Mod", simpleInputs)
    val key2 = CacheKeyGenerator.generateShortKey("Mod", simpleInputs)

    key1 shouldBe key2
  }

  it should "handle length of 1" in {
    val shortKey = CacheKeyGenerator.generateShortKey("Mod", simpleInputs, length = 1)

    shortKey should have length 1
  }

  it should "handle length greater than full key length by returning full key" in {
    val fullKey = CacheKeyGenerator.generateKey("Mod", simpleInputs)
    val shortKey = CacheKeyGenerator.generateShortKey("Mod", simpleInputs, length = 1000)

    shortKey shouldBe fullKey
  }

  // -------------------------------------------------------------------------
  // hashBytes
  // -------------------------------------------------------------------------

  "hashBytes" should "produce deterministic hashes" in {
    val bytes = "test data".getBytes("UTF-8")

    val hash1 = CacheKeyGenerator.hashBytes(bytes)
    val hash2 = CacheKeyGenerator.hashBytes(bytes)

    hash1 shouldBe hash2
  }

  it should "produce different hashes for different byte arrays" in {
    val bytes1 = "data1".getBytes("UTF-8")
    val bytes2 = "data2".getBytes("UTF-8")

    val hash1 = CacheKeyGenerator.hashBytes(bytes1)
    val hash2 = CacheKeyGenerator.hashBytes(bytes2)

    hash1 should not be hash2
  }

  it should "produce a non-empty hash" in {
    val hash = CacheKeyGenerator.hashBytes(Array.emptyByteArray)

    hash should not be empty
  }

  it should "produce a valid URL-safe Base64 string" in {
    val hash = CacheKeyGenerator.hashBytes("anything".getBytes("UTF-8"))

    // URL-safe Base64 without padding uses only [A-Za-z0-9_-]
    hash should fullyMatch regex "[A-Za-z0-9_-]+"
  }

  // -------------------------------------------------------------------------
  // Key stability - Map ordering independence
  // -------------------------------------------------------------------------

  "generateKey (key stability)" should "produce the same key regardless of input Map insertion order" in {
    val inputs1 = Map(
      "alpha" -> CValue.CString("a"),
      "beta" -> CValue.CInt(2),
      "gamma" -> CValue.CBoolean(true)
    ): Map[String, CValue]

    // Construct the same map with reversed insertion order
    val inputs2 = Map(
      "gamma" -> CValue.CBoolean(true),
      "beta" -> CValue.CInt(2),
      "alpha" -> CValue.CString("a")
    ): Map[String, CValue]

    val key1 = CacheKeyGenerator.generateKey("Mod", inputs1)
    val key2 = CacheKeyGenerator.generateKey("Mod", inputs2)

    key1 shouldBe key2
  }

  it should "produce the same key for large maps regardless of ordering" in {
    val entries = (1 to 50).map(i => s"key$i" -> (CValue.CInt(i.toLong): CValue))

    val inputs1: Map[String, CValue] = entries.toMap
    val inputs2: Map[String, CValue] = entries.reverse.toMap

    val key1 = CacheKeyGenerator.generateKey("Mod", inputs1)
    val key2 = CacheKeyGenerator.generateKey("Mod", inputs2)

    key1 shouldBe key2
  }

  // -------------------------------------------------------------------------
  // Escaping - special characters in strings
  // -------------------------------------------------------------------------

  "generateKey (escaping)" should "handle strings containing colons" in {
    val inputs = Map("s" -> CValue.CString("key:value"): (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "handle strings containing commas" in {
    val inputs = Map("s" -> CValue.CString("a,b,c"): (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "handle strings containing brackets" in {
    val inputs = Map("s" -> CValue.CString("[data]"): (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "handle strings containing braces" in {
    val inputs = Map("s" -> CValue.CString("{data}"): (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "handle strings containing parentheses" in {
    val inputs = Map("s" -> CValue.CString("(data)"): (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "handle strings containing backslashes" in {
    val inputs = Map("s" -> CValue.CString("path\\to\\file"): (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "distinguish strings that differ only in special characters" in {
    val inputs1 = Map("s" -> CValue.CString("a:b"): (String, CValue))
    val inputs2 = Map("s" -> CValue.CString("a,b"): (String, CValue))

    val key1 = CacheKeyGenerator.generateKey("Mod", inputs1)
    val key2 = CacheKeyGenerator.generateKey("Mod", inputs2)

    key1 should not be key2
  }

  it should "distinguish strings that differ only by escaped vs unescaped characters" in {
    val inputs1 = Map("s" -> CValue.CString("a\\:b"): (String, CValue))
    val inputs2 = Map("s" -> CValue.CString("a:b"): (String, CValue))

    val key1 = CacheKeyGenerator.generateKey("Mod", inputs1)
    val key2 = CacheKeyGenerator.generateKey("Mod", inputs2)

    key1 should not be key2
  }

  it should "handle strings with all special characters combined" in {
    val inputs = Map(
      "s" -> CValue.CString("a:b,c[d]e{f}g(h)i\\j"): (String, CValue)
    )
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "handle empty strings" in {
    val inputs = Map("s" -> CValue.CString(""): (String, CValue))
    val key = CacheKeyGenerator.generateKey("Mod", inputs)

    key should not be empty
  }

  it should "distinguish empty string from missing key" in {
    val inputsWithEmpty = Map("s" -> CValue.CString(""): (String, CValue))
    val inputsEmpty: Map[String, CValue] = Map.empty

    val key1 = CacheKeyGenerator.generateKey("Mod", inputsWithEmpty)
    val key2 = CacheKeyGenerator.generateKey("Mod", inputsEmpty)

    key1 should not be key2
  }
}
