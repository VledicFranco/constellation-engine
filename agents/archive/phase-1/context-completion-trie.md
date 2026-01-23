# Task 1.3: Completion Trie

**Phase:** 1 - Quick Wins
**Effort:** Low (1-2 days)
**Impact:** Low-Medium (Faster completions for large registries)
**Dependencies:** None
**Blocks:** None

---

## Objective

Replace the linear module filtering in completion handling with a prefix trie for O(prefix length) lookups instead of O(n) where n is the number of modules.

---

## Background

### Current Behavior

Completions filter all modules linearly:

```scala
// ConstellationLanguageServer.scala - lines 937-969
private def getModuleCompletions(prefix: String): List[CompletionItem] = {
  val lowerPrefix = prefix.toLowerCase

  // O(n) iteration over all modules
  modules.toList.filter { module =>
    module.name.toLowerCase.startsWith(lowerPrefix)
  }.map { module =>
    CompletionItem(
      label = module.name,
      // ...
    )
  }
}
```

**Problem:** For large module registries (100+ modules), each completion request scans all modules.

### Performance Impact

| Registry Size | Current (O(n)) | With Trie (O(k)) |
|---------------|----------------|------------------|
| 50 modules | ~50 comparisons | ~3-5 comparisons |
| 200 modules | ~200 comparisons | ~3-5 comparisons |
| 1000 modules | ~1000 comparisons | ~3-5 comparisons |

Where k = prefix length (typically 3-5 characters)

---

## Technical Design

### Trie Data Structure

```
Root
 ├─ U ─ p ─ p ─ e ─ r ─ c ─ a ─ s ─ e [Uppercase]
 │   └─ r ─ l ─ D ─ e ─ c ─ o ─ d ─ e [UrlDecode]
 ├─ L ─ o ─ w ─ e ─ r ─ c ─ a ─ s ─ e [Lowercase]
 │   └─ i ─ s ─ t ─ L ─ e ─ n ─ g ─ t ─ h [ListLength]
 └─ T ─ r ─ i ─ m [Trim]
     └─ e ─ x ─ t ─ L ─ e ─ n ─ g ─ t ─ h [TextLength]
```

Searching for "Up" traverses: Root → U → p → [collect all items below]

### Implementation

```scala
class CompletionTrie {
  private case class TrieNode(
    children: mutable.Map[Char, TrieNode] = mutable.Map.empty,
    items: mutable.Set[CompletionItem] = mutable.Set.empty
  )

  private val root = TrieNode()

  def insert(item: CompletionItem): Unit = {
    val key = item.label.toLowerCase
    var node = root
    for (char <- key) {
      node = node.children.getOrElseUpdate(char, TrieNode())
    }
    node.items += item
  }

  def findByPrefix(prefix: String): List[CompletionItem] = {
    val lowerPrefix = prefix.toLowerCase
    var node = root

    // Navigate to prefix node
    for (char <- lowerPrefix) {
      node.children.get(char) match {
        case Some(child) => node = child
        case None => return List.empty
      }
    }

    // Collect all items below this node
    collectAll(node)
  }

  private def collectAll(node: TrieNode): List[CompletionItem] = {
    val result = mutable.ListBuffer[CompletionItem]()
    result ++= node.items

    for ((_, child) <- node.children) {
      result ++= collectAll(child)
    }

    result.toList
  }
}
```

---

## Deliverables

### Required

- [ ] **`CompletionTrie.scala`** - Trie data structure:
  - Case-insensitive prefix matching
  - Efficient insertion and lookup
  - Thread-safe for concurrent access (or immutable variant)

- [ ] **Integration with LSP**:
  - Populate trie from module registry
  - Replace linear filtering with trie lookup
  - Handle trie refresh when modules change

- [ ] **Unit Tests**:
  - Insert and find operations
  - Case-insensitive matching
  - Empty prefix returns all
  - Non-matching prefix returns empty
  - Duplicate handling

### Optional Enhancements

- [ ] Fuzzy matching (edit distance)
- [ ] Ranked results (most used first)
- [ ] Keyword completion (not just prefix)

---

## Files to Modify

| File | Change Type | Description |
|------|-------------|-------------|
| `modules/lang-lsp/src/main/scala/io/constellation/lsp/CompletionTrie.scala` | **New** | Trie implementation |
| `modules/lang-lsp/src/main/scala/io/constellation/lsp/ConstellationLanguageServer.scala` | Modify | Use trie for completions |
| `modules/lang-lsp/src/test/scala/io/constellation/lsp/CompletionTrieTest.scala` | **New** | Tests |

---

## Implementation Guide

> **Overview:** 4 steps | ~4 new files | Estimated 1-2 days

### Step 1: Create Trie Data Structure

```scala
// CompletionTrie.scala
package io.constellation.lsp

import org.eclipse.lsp4j.CompletionItem
import scala.collection.mutable

/**
 * A trie (prefix tree) for efficient completion lookups.
 *
 * Provides O(k) lookup where k is the prefix length, compared to
 * O(n) for linear filtering where n is the number of items.
 *
 * This implementation is NOT thread-safe. Use `ImmutableCompletionTrie`
 * for concurrent access or synchronize externally.
 */
class CompletionTrie {

  private class TrieNode {
    val children: mutable.Map[Char, TrieNode] = mutable.Map.empty
    val items: mutable.ListBuffer[CompletionItem] = mutable.ListBuffer.empty
  }

  private val root = new TrieNode()
  private var _size: Int = 0

  /** Number of items in the trie */
  def size: Int = _size

  /** Insert a completion item into the trie */
  def insert(item: CompletionItem): Unit = {
    val key = item.getLabel.toLowerCase
    var node = root

    for (char <- key) {
      node = node.children.getOrElseUpdate(char, new TrieNode())
    }

    node.items += item
    _size += 1
  }

  /** Insert multiple items */
  def insertAll(items: Iterable[CompletionItem]): Unit = {
    items.foreach(insert)
  }

  /**
   * Find all items matching the given prefix (case-insensitive).
   *
   * @param prefix The prefix to search for. Empty string returns all items.
   * @return List of matching completion items
   */
  def findByPrefix(prefix: String): List[CompletionItem] = {
    val lowerPrefix = prefix.toLowerCase

    // Navigate to the prefix node
    var node = root
    for (char <- lowerPrefix) {
      node.children.get(char) match {
        case Some(child) => node = child
        case None => return List.empty
      }
    }

    // Collect all items at and below this node
    collectAll(node)
  }

  /**
   * Check if any items match the given prefix.
   */
  def hasPrefix(prefix: String): Boolean = {
    val lowerPrefix = prefix.toLowerCase
    var node = root

    for (char <- lowerPrefix) {
      node.children.get(char) match {
        case Some(child) => node = child
        case None => return false
      }
    }

    node.items.nonEmpty || node.children.nonEmpty
  }

  /** Clear all items from the trie */
  def clear(): Unit = {
    root.children.clear()
    root.items.clear()
    _size = 0
  }

  private def collectAll(node: TrieNode): List[CompletionItem] = {
    val result = mutable.ListBuffer[CompletionItem]()
    collectAllRecursive(node, result)
    result.toList
  }

  private def collectAllRecursive(
    node: TrieNode,
    result: mutable.ListBuffer[CompletionItem]
  ): Unit = {
    result ++= node.items
    for ((_, child) <- node.children) {
      collectAllRecursive(child, result)
    }
  }
}

object CompletionTrie {
  /** Create a trie from a list of completion items */
  def apply(items: Iterable[CompletionItem]): CompletionTrie = {
    val trie = new CompletionTrie()
    trie.insertAll(items)
    trie
  }

  /** Create an empty trie */
  def empty: CompletionTrie = new CompletionTrie()
}
```

### Step 2: Create Immutable Variant (Optional but Recommended)

```scala
/**
 * Thread-safe immutable completion trie.
 * Each modification creates a new trie.
 */
class ImmutableCompletionTrie private (
  private val items: Map[String, CompletionItem]
) {
  // Lazy trie built on first query
  private lazy val trie: CompletionTrie = {
    val t = new CompletionTrie()
    t.insertAll(items.values)
    t
  }

  def insert(item: CompletionItem): ImmutableCompletionTrie = {
    new ImmutableCompletionTrie(items + (item.getLabel -> item))
  }

  def insertAll(newItems: Iterable[CompletionItem]): ImmutableCompletionTrie = {
    new ImmutableCompletionTrie(
      items ++ newItems.map(i => i.getLabel -> i)
    )
  }

  def findByPrefix(prefix: String): List[CompletionItem] = {
    trie.findByPrefix(prefix)
  }

  def size: Int = items.size
}

object ImmutableCompletionTrie {
  val empty: ImmutableCompletionTrie = new ImmutableCompletionTrie(Map.empty)

  def apply(items: Iterable[CompletionItem]): ImmutableCompletionTrie = {
    new ImmutableCompletionTrie(items.map(i => i.getLabel -> i).toMap)
  }
}
```

### Step 3: Integrate with LSP Server

```scala
// In ConstellationLanguageServer.scala

class ConstellationLanguageServer(...) {

  // Add trie for module completions
  private var moduleCompletionTrie: CompletionTrie = _

  // Initialize trie when modules are registered
  private def initializeCompletionTrie(): Unit = {
    moduleCompletionTrie = CompletionTrie.empty

    // Add module completions
    constellation.getModules.unsafeRunSync().foreach { module =>
      moduleCompletionTrie.insert(createModuleCompletionItem(module))
    }

    // Add function completions from registry
    compiler.functionRegistry.signatures.foreach { sig =>
      moduleCompletionTrie.insert(createFunctionCompletionItem(sig))
    }

    // Add keyword completions
    Keywords.all.foreach { keyword =>
      moduleCompletionTrie.insert(createKeywordCompletionItem(keyword))
    }
  }

  // Replace linear filtering with trie lookup
  private def getCompletions(prefix: String): List[CompletionItem] = {
    moduleCompletionTrie.findByPrefix(prefix)
  }

  // In handleCompletion method
  def handleCompletion(params: CompletionParams): IO[CompletionList] = {
    // ... get prefix from document position ...

    val items = if (prefix.isEmpty) {
      // Empty prefix: return popular/recent items instead of all
      moduleCompletionTrie.findByPrefix("").take(50)
    } else {
      moduleCompletionTrie.findByPrefix(prefix)
    }

    IO.pure(CompletionList(isIncomplete = false, items.asJava))
  }
}
```

### Step 4: Handle Registry Updates

```scala
// When modules change, rebuild the trie
def handleModuleRegistration(module: Module.Uninitialized): IO[Unit] = {
  // ... existing registration logic ...

  // Rebuild trie (or use immutable variant for incremental updates)
  IO(initializeCompletionTrie())
}
```

---

## Testing Strategy

### Unit Tests

```scala
class CompletionTrieTest extends AnyFlatSpec with Matchers {

  private def item(label: String): CompletionItem = {
    val item = new CompletionItem(label)
    item.setKind(CompletionItemKind.Function)
    item
  }

  "CompletionTrie" should "find items by exact prefix" in {
    val trie = CompletionTrie(List(
      item("Uppercase"),
      item("Lowercase"),
      item("UpperLimit")
    ))

    val results = trie.findByPrefix("Upper")
    results.map(_.getLabel) should contain theSameElementsAs List("Uppercase", "UpperLimit")
  }

  it should "be case-insensitive" in {
    val trie = CompletionTrie(List(item("Uppercase"), item("lowercase")))

    trie.findByPrefix("UP").map(_.getLabel) should contain("Uppercase")
    trie.findByPrefix("up").map(_.getLabel) should contain("Uppercase")
    trie.findByPrefix("LOW").map(_.getLabel) should contain("lowercase")
    trie.findByPrefix("low").map(_.getLabel) should contain("lowercase")
  }

  it should "return empty list for non-matching prefix" in {
    val trie = CompletionTrie(List(item("Uppercase")))
    trie.findByPrefix("xyz") shouldBe empty
  }

  it should "return all items for empty prefix" in {
    val items = List(item("A"), item("B"), item("C"))
    val trie = CompletionTrie(items)

    trie.findByPrefix("").map(_.getLabel) should contain theSameElementsAs List("A", "B", "C")
  }

  it should "handle single character prefix" in {
    val trie = CompletionTrie(List(
      item("Add"),
      item("Average"),
      item("Abs"),
      item("Multiply")
    ))

    val results = trie.findByPrefix("A")
    results.map(_.getLabel) should contain theSameElementsAs List("Add", "Average", "Abs")
  }

  it should "handle items with common prefixes" in {
    val trie = CompletionTrie(List(
      item("List"),
      item("ListLength"),
      item("ListFirst"),
      item("ListLast")
    ))

    trie.findByPrefix("List").size shouldBe 4
    trie.findByPrefix("ListL").size shouldBe 2
  }

  it should "track size correctly" in {
    val trie = CompletionTrie.empty
    trie.size shouldBe 0

    trie.insert(item("A"))
    trie.size shouldBe 1

    trie.insertAll(List(item("B"), item("C")))
    trie.size shouldBe 3
  }

  it should "handle special characters in labels" in {
    val trie = CompletionTrie(List(
      item("eq-int"),
      item("eq-string"),
      item("list-length")
    ))

    trie.findByPrefix("eq-").map(_.getLabel) should contain theSameElementsAs
      List("eq-int", "eq-string")
  }
}
```

### Performance Tests

```scala
class CompletionTriePerformanceTest extends AnyFlatSpec with Matchers {

  "CompletionTrie" should "be faster than linear search for large registries" in {
    val items = (1 to 1000).map(i => item(s"Module$i")).toList
    val trie = CompletionTrie(items)

    // Trie lookup
    val trieStart = System.nanoTime()
    (1 to 1000).foreach(_ => trie.findByPrefix("Module1"))
    val trieTime = System.nanoTime() - trieStart

    // Linear search
    val linearStart = System.nanoTime()
    (1 to 1000).foreach { _ =>
      items.filter(_.getLabel.toLowerCase.startsWith("module1"))
    }
    val linearTime = System.nanoTime() - linearStart

    // Trie should be significantly faster
    trieTime should be < (linearTime / 2)
  }
}
```

---

## Web Resources

### Trie Data Structure
- [Wikipedia: Trie](https://en.wikipedia.org/wiki/Trie) - Conceptual overview
- [Visualgo Trie Visualization](https://visualgo.net/en/suffixtrie) - Interactive visualization
- [Tries in Scala](https://www.scala-lang.org/api/current/scala/collection/concurrent/TrieMap.html) - Scala TrieMap (different purpose but related)

### Completion Implementations
- [VSCode Completion Provider](https://code.visualstudio.com/api/references/vscode-api#CompletionItemProvider) - How VSCode handles completions
- [IntelliJ Completion](https://plugins.jetbrains.com/docs/intellij/code-completion.html) - JetBrains approach
- [rust-analyzer Completions](https://github.com/rust-lang/rust-analyzer/tree/master/crates/ide-completion) - Rust implementation

### Performance Optimization
- [Efficient String Matching](https://www.cs.cmu.edu/~avrim/451f11/lectures/lect1004.pdf) - Algorithms for string matching
- [Aho-Corasick Algorithm](https://en.wikipedia.org/wiki/Aho%E2%80%93Corasick_algorithm) - Multi-pattern matching

---

## Acceptance Criteria

1. **Functional Requirements**
   - [ ] Prefix search returns correct results
   - [ ] Case-insensitive matching works
   - [ ] Empty prefix returns all items
   - [ ] Non-matching prefix returns empty list
   - [ ] Handles special characters in labels

2. **Performance Requirements**
   - [ ] Lookup time O(k) where k = prefix length
   - [ ] Faster than linear search for 100+ modules
   - [ ] Memory overhead < 2x item storage

3. **Quality Requirements**
   - [ ] Unit test coverage > 90%
   - [ ] No test regressions
   - [ ] Documented public API

---

## Notes for Implementer

1. **Case sensitivity matters** - Module names are case-sensitive in constellation-lang, but completion should be case-insensitive for discoverability.

2. **Consider memory** - For very large registries, consider a more memory-efficient trie variant (radix tree/patricia trie).

3. **Thread safety** - If the trie can be accessed concurrently, either use the immutable variant or add synchronization.

4. **Empty prefix handling** - Returning all items for empty prefix could be slow for large registries. Consider limiting results or showing popular items.

5. **Incremental updates** - If modules can be added/removed at runtime, consider how to update the trie efficiently.
