package io.constellation.lsp

import io.constellation.lsp.protocol.LspTypes.{CompletionItem, CompletionItemKind}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CompletionTrieTest extends AnyFlatSpec with Matchers {

  private def item(label: String, kind: CompletionItemKind = CompletionItemKind.Function): CompletionItem = {
    CompletionItem(
      label = label,
      kind = Some(kind),
      detail = Some(s"$label function"),
      documentation = None,
      insertText = Some(s"$label()"),
      filterText = Some(label),
      sortText = Some(label)
    )
  }

  // ========== Basic Operations ==========

  "CompletionTrie" should "insert and find a single item" in {
    val trie = CompletionTrie.empty
    trie.insert(item("Uppercase"))

    val results = trie.findByPrefix("Upper")
    results should have length 1
    results.head.label shouldBe "Uppercase"
  }

  it should "find items by exact prefix" in {
    val trie = CompletionTrie(List(
      item("Uppercase"),
      item("Lowercase"),
      item("UpperLimit")
    ))

    val results = trie.findByPrefix("Upper")
    results.map(_.label) should contain theSameElementsAs List("Uppercase", "UpperLimit")
  }

  it should "be case-insensitive" in {
    val trie = CompletionTrie(List(item("Uppercase"), item("lowercase")))

    trie.findByPrefix("UP").map(_.label) should contain("Uppercase")
    trie.findByPrefix("up").map(_.label) should contain("Uppercase")
    trie.findByPrefix("LOW").map(_.label) should contain("lowercase")
    trie.findByPrefix("low").map(_.label) should contain("lowercase")
  }

  it should "return empty list for non-matching prefix" in {
    val trie = CompletionTrie(List(item("Uppercase")))
    trie.findByPrefix("xyz") shouldBe empty
  }

  it should "return all items for empty prefix" in {
    val items = List(item("Alpha"), item("Beta"), item("Gamma"))
    val trie = CompletionTrie(items)

    trie.findByPrefix("").map(_.label) should contain theSameElementsAs List("Alpha", "Beta", "Gamma")
  }

  // ========== Single Character Prefix ==========

  it should "handle single character prefix" in {
    val trie = CompletionTrie(List(
      item("Add"),
      item("Average"),
      item("Abs"),
      item("Multiply")
    ))

    val results = trie.findByPrefix("A")
    results.map(_.label) should contain theSameElementsAs List("Add", "Average", "Abs")
  }

  it should "handle single character items" in {
    val trie = CompletionTrie(List(
      item("A"),
      item("B"),
      item("AB")
    ))

    trie.findByPrefix("A").map(_.label) should contain theSameElementsAs List("A", "AB")
    trie.findByPrefix("B").map(_.label) should contain only "B"
  }

  // ========== Common Prefixes ==========

  it should "handle items with common prefixes" in {
    val trie = CompletionTrie(List(
      item("List"),
      item("ListLength"),
      item("ListFirst"),
      item("ListLast")
    ))

    trie.findByPrefix("List").size shouldBe 4
    trie.findByPrefix("ListL").size shouldBe 2
    trie.findByPrefix("ListLength").size shouldBe 1
  }

  it should "handle items where one is prefix of another" in {
    val trie = CompletionTrie(List(
      item("Get"),
      item("GetAll"),
      item("GetAllUsers"),
      item("GetUser")
    ))

    trie.findByPrefix("Get").size shouldBe 4
    trie.findByPrefix("GetA").size shouldBe 2
    trie.findByPrefix("GetAll").size shouldBe 2
    trie.findByPrefix("GetAllU").size shouldBe 1
  }

  // ========== Size Tracking ==========

  it should "track size correctly" in {
    val trie = CompletionTrie.empty
    trie.size shouldBe 0
    trie.isEmpty shouldBe true

    trie.insert(item("A"))
    trie.size shouldBe 1
    trie.isEmpty shouldBe false
    trie.nonEmpty shouldBe true

    trie.insertAll(List(item("B"), item("C")))
    trie.size shouldBe 3
  }

  it should "track size after clear" in {
    val trie = CompletionTrie(List(item("A"), item("B"), item("C")))
    trie.size shouldBe 3

    trie.clear()
    trie.size shouldBe 0
    trie.isEmpty shouldBe true
    trie.findByPrefix("") shouldBe empty
  }

  // ========== Special Characters ==========

  it should "handle special characters in labels" in {
    val trie = CompletionTrie(List(
      item("eq-int"),
      item("eq-string"),
      item("list-length")
    ))

    trie.findByPrefix("eq-").map(_.label) should contain theSameElementsAs
      List("eq-int", "eq-string")
  }

  it should "handle underscores in labels" in {
    val trie = CompletionTrie(List(
      item("get_user"),
      item("get_all_users"),
      item("set_user")
    ))

    trie.findByPrefix("get_").map(_.label) should contain theSameElementsAs
      List("get_user", "get_all_users")
  }

  it should "handle numbers in labels" in {
    val trie = CompletionTrie(List(
      item("int32"),
      item("int64"),
      item("float32")
    ))

    trie.findByPrefix("int").map(_.label) should contain theSameElementsAs
      List("int32", "int64")
    trie.findByPrefix("int3").map(_.label) should contain only "int32"
  }

  // ========== hasPrefix ==========

  it should "check hasPrefix correctly" in {
    val trie = CompletionTrie(List(item("Uppercase"), item("Lowercase")))

    trie.hasPrefix("U") shouldBe true
    trie.hasPrefix("Upper") shouldBe true
    trie.hasPrefix("Uppercase") shouldBe true
    trie.hasPrefix("L") shouldBe true
    trie.hasPrefix("xyz") shouldBe false
    trie.hasPrefix("") shouldBe true
  }

  // ========== Edge Cases ==========

  it should "handle empty trie" in {
    val trie = CompletionTrie.empty

    trie.findByPrefix("") shouldBe empty
    trie.findByPrefix("anything") shouldBe empty
    trie.hasPrefix("") shouldBe false
    trie.hasPrefix("x") shouldBe false
  }

  it should "handle duplicate insertions" in {
    val trie = CompletionTrie.empty
    trie.insert(item("Test"))
    trie.insert(item("Test"))

    trie.size shouldBe 2
    trie.findByPrefix("Test") should have length 2
  }

  it should "handle very long labels" in {
    val longLabel = "A" * 1000
    val trie = CompletionTrie(List(item(longLabel)))

    trie.findByPrefix("A" * 500) should have length 1
    trie.findByPrefix("A" * 1000) should have length 1
    trie.findByPrefix("A" * 1001) shouldBe empty
  }

  it should "handle unicode characters" in {
    val trie = CompletionTrie(List(
      item("Hello"),
      item("Helloworld")
    ))

    trie.findByPrefix("Hello").map(_.label) should contain theSameElementsAs
      List("Hello", "Helloworld")
  }

  // ========== CompletionItem Preservation ==========

  it should "preserve all CompletionItem fields" in {
    val original = CompletionItem(
      label = "TestFunc",
      kind = Some(CompletionItemKind.Function),
      detail = Some("Test function detail"),
      documentation = Some("Test documentation"),
      insertText = Some("TestFunc($1)"),
      filterText = Some("testfunc"),
      sortText = Some("001_testfunc")
    )

    val trie = CompletionTrie.empty
    trie.insert(original)

    val results = trie.findByPrefix("Test")
    results should have length 1

    val retrieved = results.head
    retrieved.label shouldBe original.label
    retrieved.kind shouldBe original.kind
    retrieved.detail shouldBe original.detail
    retrieved.documentation shouldBe original.documentation
    retrieved.insertText shouldBe original.insertText
    retrieved.filterText shouldBe original.filterText
    retrieved.sortText shouldBe original.sortText
  }

  // ========== Factory Methods ==========

  "CompletionTrie.apply" should "create a populated trie" in {
    val items = List(item("A"), item("B"), item("C"))
    val trie = CompletionTrie(items)

    trie.size shouldBe 3
    trie.findByPrefix("").size shouldBe 3
  }

  "CompletionTrie.empty" should "create an empty trie" in {
    val trie = CompletionTrie.empty

    trie.size shouldBe 0
    trie.isEmpty shouldBe true
  }
}
