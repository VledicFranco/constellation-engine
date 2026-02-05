package io.constellation.lsp

import scala.collection.mutable

import io.constellation.lsp.protocol.LspTypes.CompletionItem

/** A trie (prefix tree) for efficient completion lookups.
  *
  * Provides O(k) lookup where k is the prefix length, compared to O(n) for linear filtering where n
  * is the number of items.
  *
  * This implementation is NOT thread-safe. Create a new trie for concurrent access or synchronize
  * externally.
  */
class CompletionTrie {

  private class TrieNode {
    val children: mutable.Map[Char, TrieNode]     = mutable.Map.empty
    val items: mutable.ListBuffer[CompletionItem] = mutable.ListBuffer.empty
  }

  private val root       = new TrieNode()
  private var _size: Int = 0

  /** Number of items in the trie */
  def size: Int = _size

  /** Check if the trie is empty */
  def isEmpty: Boolean = _size == 0

  /** Check if the trie is non-empty */
  def nonEmpty: Boolean = _size > 0

  /** Insert a completion item into the trie.
    *
    * @param item
    *   The completion item to insert
    */
  def insert(item: CompletionItem): Unit = {
    val key  = item.label.toLowerCase
    var node = root

    for char <- key do node = node.children.getOrElseUpdate(char, new TrieNode())

    node.items += item
    _size += 1
  }

  /** Insert multiple completion items into the trie.
    *
    * @param items
    *   The completion items to insert
    */
  def insertAll(items: Iterable[CompletionItem]): Unit =
    items.foreach(insert)

  /** Find all items matching the given prefix (case-insensitive).
    *
    * @param prefix
    *   The prefix to search for. Empty string returns all items.
    * @return
    *   List of matching completion items
    */
  def findByPrefix(prefix: String): List[CompletionItem] = {
    val lowerPrefix = prefix.toLowerCase

    // Navigate to the prefix node
    navigateToPrefix(lowerPrefix) match {
      case Some(node) => collectAll(node)
      case None       => List.empty
    }
  }

  private def navigateToPrefix(prefix: String): Option[TrieNode] = {
    var node  = root
    var i     = 0
    var found = true
    while i < prefix.length && found do
      node.children.get(prefix.charAt(i)) match {
        case Some(child) =>
          node = child
          i += 1
        case None =>
          found = false
      }
    if found then Some(node) else None
  }

  /** Check if any items match the given prefix.
    *
    * @param prefix
    *   The prefix to check
    * @return
    *   true if any items match, false otherwise
    */
  def hasPrefix(prefix: String): Boolean =
    navigateToPrefix(prefix.toLowerCase) match {
      case Some(node) => node.items.nonEmpty || node.children.nonEmpty
      case None       => false
    }

  /** Clear all items from the trie.
    */
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
    for (_, child) <- node.children do collectAllRecursive(child, result)
  }
}

object CompletionTrie {

  /** Create a trie from a list of completion items.
    *
    * @param items
    *   The items to populate the trie with
    * @return
    *   A new trie containing all items
    */
  def apply(items: Iterable[CompletionItem]): CompletionTrie = {
    val trie = new CompletionTrie()
    trie.insertAll(items)
    trie
  }

  /** Create an empty trie.
    *
    * @return
    *   A new empty trie
    */
  def empty: CompletionTrie = new CompletionTrie()
}
